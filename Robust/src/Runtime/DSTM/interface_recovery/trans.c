#include "dstm.h"
#include "ip.h"
#include "machinepile.h"
#include "altmlookup.h"
#include "llookup.h"
#include "plookup.h"
#include "altprelookup.h"
#include "threadnotify.h"
#include "queue.h"
#include "addUdpEnhance.h"
#include "addPrefetchEnhance.h"
#include "gCollect.h"
#include "dsmlock.h"
#include "prefetch.h"
#ifdef COMPILER
#include "thread.h"
#endif
#ifdef ABORTREADERS
#include "abortreaders.h"
#endif
#include "trans.h"

#ifdef RECOVERY
#include <unistd.h>
#include <signal.h>
#include <sys/select.h>
#include "tlookup.h"
#include "translist.h"

#define CPU_FREQ 3056842
#endif

#define NUM_THREADS 1
#define CONFIG_FILENAME "dstm.conf"

/* Thread transaction variables */

__thread objstr_t *t_cache;
__thread struct ___Object___ *revertlist;
__thread struct timespec exponential_backoff;
__thread int count_exponential_backoff;
__thread const int max_exponential_backoff = 1000; // safety limit
#ifdef ABORTREADERS
__thread int t_abort;
__thread jmp_buf aborttrans;
#endif

/* Global Variables */
extern int classsize[];
pfcstats_t *evalPrefetch;
extern int numprefetchsites; //Global variable containing number of prefetch sites
extern pthread_mutex_t mainobjstore_mutex; // Mutex to lock main Object store
pthread_mutex_t prefetchcache_mutex; // Mutex to lock Prefetch Cache
pthread_mutexattr_t prefetchcache_mutex_attr; /* Attribute for lock to make it a recursive lock */
pthread_t wthreads[NUM_THREADS]; //Worker threads for working on the prefetch queue
pthread_t tPrefetch;            /* Primary Prefetch thread that processes the prefetch queue */
extern objstr_t *mainobjstore;
unsigned int myIpAddr;
unsigned int *hostIpAddrs;
int sizeOfHostArray;
int numHostsInSystem;
int myIndexInHostArray;
unsigned int oidsPerBlock;
unsigned int oidMin;
unsigned int oidMax;

sockPoolHashTable_t *transReadSockPool;
sockPoolHashTable_t *transPrefetchSockPool;
sockPoolHashTable_t *transRequestSockPool;
pthread_mutex_t notifymutex;
pthread_mutex_t atomicObjLock;

/***********************************
 * Global Variables for statistics
 **********************************/
int numTransCommit = 0;
int numTransAbort = 0;
int nchashSearch = 0;
int nmhashSearch = 0;
int nprehashSearch = 0;
int ndirtyCacheObj = 0;
int nRemoteSend = 0;
int nSoftAbort = 0;
int bytesSent = 0;
int bytesRecv = 0;
int totalObjSize = 0;
int sendRemoteReq = 0;
int getResponse = 0;

#ifdef RECOVERY


#define INCREASE_EPOCH(x,y,z) ((x/y+1)*y + z)
/***********************************
 * Global variables for Duplication
 ***********************************/
int *liveHosts;
int numLiveHostsInSystem;	
unsigned int *locateObjHosts;

unsigned int numWaitMachine;
extern int okCommit;

/* variables to clear dead threads */
int waitThreadMid;            
unsigned int waitThreadID; 

__thread int transRetryFlag;
unsigned int transIDMin;
unsigned int transIDMax;

char ip[16];      // for debugging purpose

extern tlist_t* transList;
extern pthread_mutex_t translist_mutex;
extern pthread_mutex_t clearNotifyList_mutex;

unsigned int currentEpoch;
unsigned int currentBackupMachine;

#ifdef RECOVERYSTATS
  int numRecovery = 0;
  recovery_stat_t* recoverStat;
#endif


#endif

void printhex(unsigned char *, int);
plistnode_t *createPiles();
plistnode_t *sortPiles(plistnode_t *pileptr);

#ifdef LOGEVENTS
char bigarray[16*1024*1024];
int bigindex=0;
#define LOGEVENT(x) { \
    int tmp=bigindex++;         \
    bigarray[tmp]=x;          \
  }
#else
#define LOGEVENT(x)
#endif

/*******************************
* Send and Recv function calls
*******************************/
int send_data(int fd, void *buf, int buflen) {
  char *buffer = (char *)(buf);
  int size = buflen;
  int numbytes;

  while (size > 0) {
#ifdef GDBDEBUG
GDBSEND1:
#endif
    numbytes = send(fd, buffer, size, 0);

    if( numbytes > 0) {
      bytesSent += numbytes;
      size -= numbytes;
    }
#ifdef RECOVERY
    else if( numbytes < 0) {    
      // Receive returned an error.
      // Analyze underlying cause
      if(errno == ECONNRESET || errno == EAGAIN || errno == EWOULDBLOCK) {
        // machine has failed
        //
        // if we see EAGAIN w/o failures, we should record the time
        // when we start send and finish send see if it is longer
        // than our threshold
        //
        return -1;
      } else {
#ifdef GDBDEBUG
        if(errno == 4)
          goto GDBSEND1;    
#endif

        return -2;
      }
    }
    else{
      // Case : numbytes == 0
      // // machine has failed -- this case probably doesn't occur in reality
#ifdef DEBUG
      printf("%s -> SHOULD NOT BE HERE\n",__func__);
#endif
      return -1;
    }
#else
    if(numbytes == -1) {
      perror("send");
      exit(0);
    }
#endif
  } // close while loop
  return 0; // completed sending data
}

void send_buf(int fd, struct writestruct * sendbuffer, void *buffer, int buflen) {
  if (buflen+sendbuffer->offset>WMAXBUF) {
    send_data(fd, sendbuffer->buf, sendbuffer->offset);
    sendbuffer->offset=0;
    send_data(fd, buffer, buflen);
    return;
  }
  memcpy(&sendbuffer->buf[sendbuffer->offset], buffer, buflen);
  sendbuffer->offset+=buflen;
  if (sendbuffer->offset>WTOP) {
    send_data(fd, sendbuffer->buf, sendbuffer->offset);
    sendbuffer->offset=0;
  }
}

void forcesend_buf(int fd, struct writestruct * sendbuffer, void *buffer, int buflen) {
  if (buflen+sendbuffer->offset>WMAXBUF) {
    send_data(fd, sendbuffer->buf, sendbuffer->offset);
    sendbuffer->offset=0;
    send_data(fd, buffer, buflen);
    return;
  }
  memcpy(&sendbuffer->buf[sendbuffer->offset], buffer, buflen);
  sendbuffer->offset+=buflen;
  send_data(fd, sendbuffer->buf, sendbuffer->offset);
  sendbuffer->offset=0;
}

//Returns negative value if receive cannot be completed because of
//timeout or machine failure

int recv_data(int fd, void *buf, int buflen) {
  char *buffer = (char *)(buf);
  int size = buflen;
  int numbytes;
  int trycounter = 0;
  
  while (size > 0) {
#ifdef GDBDEBUG
GDBRECV1:
#endif
    numbytes = recv(fd, buffer, size, 0);
    bytesRecv += numbytes;
    
    
    if (numbytes>0) {
      buffer += numbytes;
      size -= numbytes;
    }
#ifdef RECOVERY
    else if (numbytes<0){ 
      //Receive returned an error.
      //Analyze underlying cause
      if(errno == ECONNRESET || errno == EAGAIN || errno == EWOULDBLOCK) {
        //machine has failed
        //if we see EAGAIN w/o failures, we should record the time
      	//when we start read and finish read and see if it is longer
      	//than our threshold
        if(errno == EAGAIN) {
          if(trycounter < 5) {
            trycounter++;
            continue;
          }
          else
            return -1;
        }
        return -1;
      } else {
#ifdef GDBDEBUG
        if(errno == 4)
          goto GDBRECV1;
#endif

#ifdef DEBUG
        printf("%s -> Unexpected ERROR!\n",__func__);
        printf("%s-> errno = %d %s\n", __func__, errno, strerror(errno));
#endif
      	return -2;
      }
    } else {
//      printf("%s -> Here?\n",__func__);
//      printf("%s-> errno = %d %s\n", __func__, errno, strerror(errno));
      //Case: numbytes==0
      //machine has failed -- this case probably doesn't occur in reality
      //
#ifdef DEBUG
      printf("%s -> SHOULD NOT BE HERE\n",__func__);
#endif
      return -1;
    }
#else
    if( numbytes == -1) {
      perror("recv");
      exit(0);
    }
#endif
  } //close while loop
  return 0; // got all the data
}

int recvw(int fd, void *buf, int len, int flags) {
  return recv(fd, buf, len, flags);
}

void recv_data_buf(int fd, struct readstruct * readbuffer, void *buffer, int buflen) {
  char *buf=(char *)buffer;
  int numbytes=readbuffer->head-readbuffer->tail;
  if (numbytes>buflen)
    numbytes=buflen;
  if (numbytes>0) {
    memcpy(buf, &readbuffer->buf[readbuffer->tail], numbytes);
    readbuffer->tail+=numbytes;
    buflen-=numbytes;
    buf+=numbytes;
  }
  if (buflen==0) {
    return;
  }
  if (buflen>=MAXBUF) {
    recv_data(fd, buf, buflen);
    return;
  }
  
  int maxbuf=MAXBUF;
  int obufflen=buflen;
  readbuffer->head=0;
  
  while (buflen > 0) {
    int numbytes = recvw(fd, &readbuffer->buf[readbuffer->head], maxbuf, 0);
    if (numbytes == -1) {
      perror("recv");
      exit(0);
    }
    bytesRecv+=numbytes;
    buflen-=numbytes;
    readbuffer->head+=numbytes;
    maxbuf-=numbytes;
  }
  memcpy(buf,readbuffer->buf,obufflen);
  readbuffer->tail=obufflen;
}

int recv_data_errorcode(int fd, void *buf, int buflen) {
  char *buffer = (char *)(buf);
  int size = buflen;
  int numbytes;
  while (size > 0) {
    numbytes = recv(fd, buffer, size, 0);
    if (numbytes==0)
      return 0;
    else if (numbytes == -1) {
//      printf("%s -> ERROR NUMBER = %d %s\n",__func__,errno,strerror(errno));
//      perror("recv_data_errorcode");
      return -1;
    }
    bytesRecv += numbytes;
    buffer += numbytes;
    size -= numbytes;
  }
  return 1;
}

void printhex(unsigned char *ptr, int numBytes) {
  int i;
  for (i = 0; i < numBytes; i++) {
    if (ptr[i] < 16)
      printf("0%x ", ptr[i]);
    else
      printf("%x ", ptr[i]);
  }
  printf("\n");
  return;
}

inline int arrayLength(int *array) {
  int i;
  for(i=0 ; array[i] != -1; i++)
    ;
  return i;
}

inline int findmax(int *array, int arraylength) {
  int max, i;
  max = array[0];
  for(i = 0; i < arraylength; i++) {
    if(array[i] > max) {
      max = array[i];
    }
  }
  return max;
}

#ifdef RECOVERY
char* midtoIPString(unsigned int mid){
		midtoIP(mid, ip);
		return ip;
}
#endif

#define INLINEPREFETCH
#define PREFTHRESHOLD 0

/* This function is a prefetch call generated by the compiler that
 * populates the shared primary prefetch queue*/
void prefetch(int siteid, int ntuples, unsigned int *oids, unsigned short *endoffsets, short *arrayfields) {
  /* Allocate for the queue node*/
  int qnodesize = 2*sizeof(int) + ntuples * (sizeof(unsigned short) + sizeof(unsigned int)) + endoffsets[ntuples - 1] * sizeof(short);
  int len;
#ifdef INLINEPREFETCH
  int attempted=0;
  char *node;
  do {
  node=getmemory(qnodesize);
  if (node==NULL&&attempted)
    break;
  if (node!=NULL) {
#else
  char *node=getmemory(qnodesize);
#endif
  int top=endoffsets[ntuples-1];

  if (node==NULL) {
    LOGEVENT('D');
    return;
  }
  /* Set queue node values */

  /* TODO: Remove this after testing */
  evalPrefetch[siteid].callcount++;

  *((int *)(node))=siteid;
  *((int *)(node + sizeof(int))) = ntuples;
  len = 2*sizeof(int);
  memcpy(node+len, oids, ntuples*sizeof(unsigned int));
  memcpy(node+len+ntuples*sizeof(unsigned int), endoffsets, ntuples*sizeof(unsigned short));
  memcpy(node+len+ntuples*(sizeof(unsigned int)+sizeof(short)), arrayfields, top*sizeof(short));

#ifdef INLINEPREFETCH
  movehead(qnodesize);
  }
  int numpref=numavailable();
  attempted=1;

  if (node==NULL && numpref!=0 || numpref>=PREFTHRESHOLD) {
    node=gettail();
    prefetchpile_t *pilehead = foundLocal(node,numpref);
    if (pilehead!=NULL) {
      // Get sock from shared pool
      
      /* Send  Prefetch Request */
      prefetchpile_t *ptr = pilehead;
      while(ptr != NULL) {
        //int sd = getSock2(transPrefetchSockPool, ptr->mid);
        int sd;
        if((sd = getSockWithLock(transPrefetchSockPool, ptr->mid)) < 0) {
          printf("%s() Socket Create Error at %s, %d\n", __func__, __FILE__, __LINE__);
          return;
        }
        sendPrefetchReq(ptr, sd);
        freeSockWithLock(transPrefetchSockPool, ptr->mid, sd);
        ptr = ptr->next;
      }
      
      mcdealloc(pilehead);
    }
    resetqueue();
  }//end do prefetch if condition
  } while(node==NULL);
#else
  /* Lock and insert into primary prefetch queue */
  movehead(qnodesize);
#endif
}

/* This function starts up the transaction runtime. */
int dstmStartup(const char * option) {
  pthread_t thread_Listen, udp_thread_Listen;
  pthread_attr_t attr;
  int master=option!=NULL && strcmp(option, "master")==0;
  int fd;
  int udpfd;

  if (processConfigFile() != 0)
    return 0; //TODO: return error value, cause main program to exit
#ifdef COMPILER
  if (!master)
    threadcount--;
#endif

#ifdef TRANSSTATS
  printf("Trans stats is on\n");
  fflush(stdout);
#endif
#ifdef ABORTREADERS
  initreaderlist();
#endif

  //Initialize socket pool
  transReadSockPool = createSockPool(transReadSockPool, DEFAULTSOCKPOOLSIZE);
  transPrefetchSockPool = createSockPool(transPrefetchSockPool, DEFAULTSOCKPOOLSIZE);
  transRequestSockPool = createSockPool(transRequestSockPool, DEFAULTSOCKPOOLSIZE);

  dstmInit();
  transInit();

  fd=startlistening();
  pthread_attr_init(&attr);
  pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);
#ifdef CACHE
  udpfd = udpInit();
  pthread_create(&udp_thread_Listen, &attr, udpListenBroadcast, (void*)udpfd);
#endif
  if (master) {
		pthread_create(&thread_Listen, &attr, dstmListen, (void*)fd);
#ifdef RECOVERY
		updateLiveHosts();
		setLocateObjHosts();
		updateLiveHostsCommit();
		if(!allHostsLive()) {
			printf("Not all hosts live. Exiting.\n");
			exit(-1);
		}
#endif
		return 1;
	} else {
		dstmListen((void *)fd);
		return 0;
  }
}

//TODO Use this later
void *pCacheAlloc(objstr_t *store, unsigned int size) {
  void *tmp;
  objstr_t *ptr;
  ptr = store;
  int success = 0;

  while(ptr->next != NULL) {
    /* check if store is empty */
    if(((unsigned int)ptr->top - (unsigned int)ptr - sizeof(objstr_t) + size) <= ptr->size) {
      tmp = ptr->top;
      ptr->top += size;
      success = 1;
      return tmp;
    } else {
      ptr = ptr->next;
    }
  }

  if(success == 0) {
    return NULL;
  }
}

/* This function initiates the prefetch thread A queue is shared
 * between the main thread of execution and the prefetch thread to
 * process the prefetch call Call from compiler populates the shared
 * queue with prefetch requests while prefetch thread processes the
 * prefetch requests */

void transInit() {
  //Create and initialize prefetch cache structure
#ifdef CACHE
  initializePCache();
  if((evalPrefetch = initPrefetchStats()) == NULL) {
    printf("%s() Error allocating memory at %s, %d\n", __func__, __FILE__, __LINE__);
    exit(0);
  }
#endif

  /* Initialize attributes for mutex */
  pthread_mutexattr_init(&prefetchcache_mutex_attr);
  pthread_mutexattr_settype(&prefetchcache_mutex_attr, PTHREAD_MUTEX_RECURSIVE_NP);

  pthread_mutex_init(&prefetchcache_mutex, &prefetchcache_mutex_attr);
  pthread_mutex_init(&notifymutex, NULL);
  pthread_mutex_init(&atomicObjLock, NULL);
#ifdef CACHE
  //Create prefetch cache lookup table
  if(prehashCreate(PHASH_SIZE, PLOADFACTOR)) {
    printf("ERROR\n");
    return; //Failure
  }

  //Initialize primary shared queue
  queueInit();
  //Initialize machine pile w/prefetch oids and offsets shared queue
  mcpileqInit();

  //Create the primary prefetch thread
  int retval;
#ifdef RANGEPREFETCH
  do {
    retval=pthread_create(&tPrefetch, NULL, transPrefetchNew, NULL);
  } while(retval!=0);
#else
#ifndef INLINEPREFETCH
  do {
    retval=pthread_create(&tPrefetch, NULL, transPrefetch, NULL);
  } while(retval!=0);
#endif
#endif
#ifndef INLINEPREFETCH
  pthread_detach(tPrefetch);
#endif
#endif
}

/* This function stops the threads spawned */
void transExit() {
#ifdef CACHE
  int t;
  pthread_cancel(tPrefetch);
  for(t = 0; t < NUM_THREADS; t++)
    pthread_cancel(wthreads[t]);
#endif

  return;
}

/* This functions inserts randowm wait delays in the order of msec
 * Mostly used when transaction commits retry*/
void randomdelay() {
  struct timespec req;
  time_t t;

  t = time(NULL);
  req.tv_sec = 0;
  req.tv_nsec = (long)(1000 + (t%10000)); //1-11 microsec
  nanosleep(&req, NULL);
  return;
}

/* This functions inserts exponential backoff delays in the order of msec
 * Mostly used when transaction commits retry*/
void exponentialdelay() {
  exponential_backoff.tv_nsec = exponential_backoff.tv_nsec * 2;
  nanosleep(&exponential_backoff, NULL);
  ++count_exponential_backoff;
  if (count_exponential_backoff >= max_exponential_backoff) {
    printf(" reached max_exponential_backoff at %s, %s(), %d\n", __FILE__, __func__, __LINE__);
    exit(-1);
  }
  return;
}

/* This function initializes things required in the transaction start*/
void transStart() {
  t_cache = objstrCreate(1048576);
  t_chashCreate(CHASH_SIZE, CLOADFACTOR);
  revertlist=NULL;
#ifdef ABORTREADERS
  t_abort=0;
#endif
}

/*#define INLINE    inline __attribute__((always_inline))

INLINE void * chashSearchI(chashtable_t *table, unsigned int key) {
  //REMOVE HASH FUNCTION CALL TO MAKE SURE IT IS INLINED HERE                                                          
  chashlistnode_t *node = &table->table[(key & table->mask)>>1];

  do {
    if(node->key == key) {
      return node->val;
    }
    node = node->next;
  } while(node != NULL);

  return NULL;
  }*/




/* This function finds the location of the objects involved in a transaction
 * and returns the pointer to the object if found in a remote location */
__attribute__((pure)) objheader_t *transRead(unsigned int oid) {
  unsigned int machinenumber;
  objheader_t *tmp, *objheader;
  objheader_t *objcopy;
  int size;
  void *buf;
  chashlistnode_t *node;
	
	if(oid == 0) {
    return NULL;
  }

  node= &c_table[(oid & c_mask)>>1];
  do {
    if(node->key == oid) {
#ifdef TRANSSTATS
    nchashSearch++;
#endif
#ifdef COMPILER
    return &((objheader_t*)node->val)[1];
#else
    return node->val;
#endif
    }
    node = node->next;
  } while(node != NULL);
  

  /*  
  if((objheader = chashSearchI(record->lookupTable, oid)) != NULL) {
#ifdef TRANSSTATS
    nchashSearch++;
#endif
#ifdef COMPILER
    return &objheader[1];
#else
    return objheader;
#endif
  } else 
  */

#ifdef ABORTREADERS
  if (t_abort) {
    //abort this transaction
    //printf("ABORTING\n");
    removetransactionhash();
    objstrDelete(t_cache);
    t_chashDelete();
    _longjmp(aborttrans,1);
  } else
    addtransaction(oid);
#endif

  if ((objheader = (objheader_t *) mhashSearch(oid)) != NULL) {
#ifdef TRANSSTATS
    nmhashSearch++;
#endif
    /* Look up in machine lookup table  and copy  into cache*/
    GETSIZE(size, objheader);
    size += sizeof(objheader_t);
    objcopy = (objheader_t *) objstrAlloc(&t_cache, size);
    memcpy(objcopy, objheader, size);
    /* Insert into cache's lookup table */
    STATUS(objcopy)=0;
    t_chashInsert(OID(objheader), objcopy);
#ifdef COMPILER
    return &objcopy[1];
#else
    return objcopy;
#endif
  } else {
#ifdef CACHE
    if((tmp = (objheader_t *) prehashSearch(oid)) != NULL) {
      if(STATUS(tmp) & DIRTY) {
#ifdef TRANSSTATS
        ndirtyCacheObj++;
#endif
        goto remoteread;
      }
#ifdef TRANSSTATS
      nprehashSearch++;
#endif
      /* Look up in prefetch cache */
      GETSIZE(size, tmp);
      size+=sizeof(objheader_t);
      objcopy = (objheader_t *) objstrAlloc(&t_cache, size);
      memcpy(objcopy, tmp, size);
      /* Insert into cache's lookup table */
      t_chashInsert(OID(tmp), objcopy);
#ifdef COMPILER
      return &objcopy[1];
#else
      return objcopy;
#endif
    }
remoteread:
#endif
    /* Get the object from the remote location */
    if((machinenumber = lhashSearch(oid)) == 0) {
      printf("Error: %s() No machine found for oid =% %s,%dx\n",__func__, machinenumber, __FILE__, __LINE__);
      return NULL;
    }
    objcopy = getRemoteObj(machinenumber, oid);

    if(objcopy == NULL) {
      printf("Error: Object not found in Remote location %s, %d\n", __FILE__, __LINE__);
      return NULL;
    } else {
#ifdef TRANSSTATS
      nRemoteSend++;
#endif
#ifdef COMPILER
#ifdef CACHE
      //Copy object to prefetch cache
      pthread_mutex_lock(&prefetchcache_mutex);
      objheader_t *headerObj;
      int size;
      GETSIZE(size, objcopy);
      if((headerObj = prefetchobjstrAlloc(size + sizeof(objheader_t))) == NULL) {
        printf("%s(): Error in getting memory from prefetch cache at %s, %d\n", __func__,
            __FILE__, __LINE__);
        pthread_mutex_unlock(&prefetchcache_mutex);
        return NULL;
      }
      pthread_mutex_unlock(&prefetchcache_mutex);
      memcpy(headerObj, objcopy, size+sizeof(objheader_t));
      //make an entry in prefetch lookup hashtable
      prehashInsert(oid, headerObj);
      LOGEVENT('B');
#endif
      return &objcopy[1];
#else
      return objcopy;
#endif
    }
  }
}


/* This function finds the location of the objects involved in a transaction
 * and returns the pointer to the object if found in a remote location */
__attribute__((pure)) objheader_t *transRead2(unsigned int oid) {
  unsigned int machinenumber;
  objheader_t *tmp, *objheader;
  objheader_t *objcopy;
  int size;


#ifdef DEBUG
	printf("%s-> Start, oid:%u\n", __func__, oid);
#endif

#ifdef ABORTREADERS
  if (t_abort) {
    //abort this transaction
    //printf("ABORTING\n");
    removetransactionhash();
    objstrDelete(t_cache);
    t_chashDelete();
    _longjmp(aborttrans,1);
  } else
    addtransaction(oid);
#endif

    if ((objheader = (objheader_t *) mhashSearch(oid)) != NULL) {
#ifdef DEBUG
		  printf("%s-> Grab from this machine\n", __func__);
#endif
#ifdef TRANSSTATS
      nmhashSearch++;
#endif
      /* Look up in machine lookup table  and copy  into cache*/
      GETSIZE(size, objheader);
      size += sizeof(objheader_t);
      if((objcopy = (objheader_t *) objstrAlloc(&t_cache, size)) == NULL) {
        printf("DEBUG: %s() mlookup objcopy= %x\n", __func__, objcopy);
        exit(-1);
      }
      memcpy(objcopy, objheader, size);
      /* Insert into cache's lookup table */
      STATUS(objcopy)=0;
      t_chashInsert(OID(objheader), objcopy);
#ifdef DEBUG
      printf("%s -> obj type = %d\n",__func__,getObjType(oid));
      printf("%s -> obj grabbed\n",__func__);
#endif
#ifdef COMPILER
      return &objcopy[1];
#else
      return objcopy;
#endif
    } else {
#ifdef CACHE
      if((tmp = (objheader_t *) prehashSearch(oid)) != NULL) {
        if(STATUS(tmp) & DIRTY) {
#ifdef TRANSSTATS
          ndirtyCacheObj++;
#endif
          goto remoteread;
        }
#ifdef TRANSSTATS
      LOGEVENT('P')
      nprehashSearch++;
#endif
      /* Look up in prefetch cache */
      GETSIZE(size, tmp);
      size+=sizeof(objheader_t);
      if((objcopy = (objheader_t *) objstrAlloc(&t_cache, size)) == NULL) {
        printf("DEBUG: %s() prefetch cache objcopy= %x\n", __func__, objcopy);
        exit(-1);
      }
      memcpy(objcopy, tmp, size);
      /* Insert into cache's lookup table */
      t_chashInsert(OID(tmp), objcopy);
#ifdef COMPILER
      return &objcopy[1];
#else
			return objcopy;
#endif
  	} 
remoteread:
#endif
      /* Get the object from the remote location */
    if((machinenumber = lhashSearch(oid)) == 0) {
      printf("Error: %s() No machine found for oid =% %s,%dx\n",__func__, machinenumber, __FILE__, __LINE__);
	    return NULL;
   	}
#ifdef DEBUG
  	printf("%s-> Grab from remote machine\n", __func__);
#endif
#ifdef RECOVERY
    transRetryFlag = 0;

    static int flipBit = 0;        // Used to distribute requests between primary and backup evenly
    // either primary or backup machine
    machinenumber = (flipBit)?getPrimaryMachine(lhashSearch(oid)):getBackupMachine(lhashSearch(oid));
    flipBit ^= 1;

#ifdef DEBUG
    printf("mindex:%d, oid:%d, machinenumber:%s\n", machinenumber, oid, midtoIPString(machinenumber));
#endif
#endif

    objcopy = getRemoteObj(machinenumber, oid);

#ifdef RECOVERY
    if(transRetryFlag) {
      notifyLeaderDeadMachine(machinenumber);
      return transRead2(oid);
    }
#endif

  if(objcopy == NULL) {
	  printf("Error: Object not found in Remote location %s, %d\n", __FILE__, __LINE__);
		return NULL;
	} else {
#ifdef TRANSSTATS
    LOGEVENT('R');
    nRemoteSend++;
#endif

    if(objcopy!=NULL) {
#ifdef CACHE
      //Copy object to prefetch cache
      pthread_mutex_lock(&prefetchcache_mutex);
      objheader_t *headerObj;
      int size;
      GETSIZE(size, objcopy);
      if((headerObj = prefetchobjstrAlloc(size+sizeof(objheader_t))) == NULL) {
        printf("%s(): Error in getting memory from prefetch cache at %s, %d\n", __func__,
            __FILE__, __LINE__);
        pthread_mutex_unlock(&prefetchcache_mutex);
        return NULL;
      }
      pthread_mutex_unlock(&prefetchcache_mutex);
      memcpy(headerObj, objcopy, size+sizeof(objheader_t));
      //printf("%s() DEBUG: type=%d\n",__func__, TYPE(headerObj));
      //make an entry in prefetch lookup hashtable
      prehashInsert(oid, headerObj);
      LOGEVENT('B');
#endif
    }

    if(objcopy == NULL) {
	    printf("Error: Object not found in Remote location %s, %d\n", __FILE__, __LINE__);
  		return NULL;
	  } else {
#ifdef COMPILER
		  return &objcopy[1];
#else
  		return objcopy;
#endif
	  }
  }
  }
#ifdef DEBUG
  printf("%s -> Finished!!\n",__func__);
#endif
}

/* This function creates objects in the transaction record */
objheader_t *transCreateObj(unsigned int size) {
  objheader_t *tmp = (objheader_t *) objstrAlloc(&t_cache, (sizeof(objheader_t) + size));
  OID(tmp) = getNewOID();
  tmp->notifylist = NULL;
  tmp->version = 1;
  tmp->isBackup = 0;
  STATUS(tmp) = NEW;
  t_chashInsert(OID(tmp), tmp);
#ifdef COMPILER
  return &tmp[1]; //want space after object header
#else
  return tmp;
#endif
}


#if 1
/* This function creates machine piles based on all machines involved in a
 * transaction commit request */
plistnode_t *createPiles() {

#ifdef DEBUG
  printf("%s -> Entering\n",__func__);
#endif
  int i;
	unsigned int oid;
  plistnode_t *pile = NULL;
  unsigned int machinenum;
	unsigned int destMachine[2];
  objheader_t *headeraddr;
  chashlistnode_t * ptr = c_table;
  /* Represents number of bins in the chash table */
  unsigned int size = c_size;
#ifdef RECOVERY
  int phostindex[numHostsInSystem];
  int k;
  for(k=0; k<numHostsInSystem; k++)
    phostindex[k] = -1;
  int hostIndex = 0;
#endif
	for(i = 0; i < size ; i++) {
    chashlistnode_t * curr = &ptr[i];
		/* Inner loop to traverse the linked list of the cache lookupTable */
    while(curr != NULL) {
      //if the first bin in hash table is empty
      if(curr->key == 0)
        break;
      headeraddr=(objheader_t *) curr->val;

#ifdef RECOVERY

      oid = OID(headeraddr);

			  int makedirty = 0;
        unsigned int mid;

        // if the obj is dirty or new
   			if(STATUS(headeraddr) & DIRTY || STATUS(headeraddr) & NEW) {
          // set flag for backup machine
	  			makedirty = 1;
		    }

        // if the obj is new or local, destination will be my Ip
        if((mid=lhashSearch(oid)) == 0) {
            mid = myIpAddr;
        }

        int selectMid=0;
        if(mid == myIpAddr) {
          pile = pInsert(pile, headeraddr, getPrimaryMachine(mid), c_numelements);
          if(!checkIndex(findHost(myIpAddr), phostindex)) {
            phostindex[hostIndex++] = findHost(myIpAddr);
          }
        } else {
          int pindex = findHost(mid);//primary copy's index
          int bindex = (findHost(mid) + 1) % numHostsInSystem;//backup copy's index
          if(checkIndex(pindex, phostindex)) {
            pile = pInsert(pile, headeraddr, getPrimaryMachine(mid), c_numelements);
            selectMid = 1;
          } else if(checkIndex(bindex, phostindex)) {
            pile = pInsert(pile, headeraddr, getBackupMachine(mid), c_numelements);
          } else {
            //check if any indexes present in the phostindex arry is odd or even
            int chktype;
            if((chktype = typeIndex(phostindex)) != -1) {
              if(chktype == 1) { //odd indexed machines
                //pick up either backup or primary copy based on the type of previous indexes
                if((pindex%2) == 0) { 
                  pile = pInsert(pile, headeraddr, getBackupMachine(mid), c_numelements);
                  phostindex[hostIndex++] = bindex;
                } else {
                  pile = pInsert(pile, headeraddr, getPrimaryMachine(mid), c_numelements);
                  selectMid = 1;
                  phostindex[hostIndex++] = pindex;
                }
              } else { //even indexed machines
                if((pindex%2) == 0) {
                  pile = pInsert(pile, headeraddr, getPrimaryMachine(mid), c_numelements);
                  selectMid = 1;
                  phostindex[hostIndex++] = pindex;
                } else {
                  pile = pInsert(pile, headeraddr, getBackupMachine(mid), c_numelements);
                  phostindex[hostIndex++] = bindex;
                }
              }
            } else {
              if(((myIpAddr%2 == 0) && ((mid%2) == 0)) || ((myIpAddr%2 == 0) && ((mid%2) == 0))) {
                pile = pInsert(pile, headeraddr, getPrimaryMachine(mid), c_numelements);
                selectMid = 1;
                phostindex[hostIndex++] = pindex;
              } else {
                pile = pInsert(pile, headeraddr, getBackupMachine(mid), c_numelements);
                phostindex[hostIndex++] = bindex;
              }
            }
          }
        }

        if(numLiveHostsInSystem > 1) {
          if(makedirty) { 
            STATUS(headeraddr) = DIRTY;
            if(mid == myIpAddr) {
              pile = pInsert(pile, headeraddr, getBackupMachine(mid), c_numelements);
            } else {
              if(selectMid) {
                pile = pInsert(pile, headeraddr, getBackupMachine(mid), c_numelements);
              } else {
                pile = pInsert(pile, headeraddr, getPrimaryMachine(mid), c_numelements);
              }
            }
          }
        }
#else
    		// Get machine location for object id (and whether local or not)
		    if (STATUS(headeraddr) & NEW || (mhashSearch(curr->key) != NULL)) {
				  machinenum = myIpAddr;
        } else if ((machinenum = lhashSearch(curr->key)) == 0) {
          printf("Error: No such machine %s, %d\n", __FILE__, __LINE__);
          return NULL;
        }
        //Make machine groups
        pile = pInsert(pile, headeraddr, machinenum, c_numelements);        
#endif
      curr = curr->next;
    }
  }

	return pile;
}
#else
/* This function creates machine piles based on all machines involved in a
 * transaction commit request */
plistnode_t *createPiles() {
  int i;
  plistnode_t *pile = NULL;
  unsigned int machinenum;
	unsigned int destMachine[2];
  objheader_t *headeraddr;
  struct chashentry * ptr = c_table;
  /* Represents number of bins in the chash table */
  unsigned int size = c_size;

  for(i = 0; i < size ; i++) {
    struct chashentry * curr = & ptr[i];
    /* Inner loop to traverse the linked list of the cache lookupTable */
    // if the first bin in hash table is empty
    if(curr->key == 0)
      continue;
    headeraddr=(objheader_t *) curr->ptr;

    //Get machine location for object id (and whether local or not)
    if (STATUS(headeraddr) & NEW || (mhashSearch(curr->key) != NULL)) {
      machinenum = myIpAddr;
    } else if ((machinenum = lhashSearch(curr->key)) == 0) {
      printf("Error: No such machine %s, %d\n", __FILE__, __LINE__);
      return NULL;
    }

    //Make machine groups
    pile = pInsert(pile, headeraddr, machinenum, c_numelements);
  }
  return pile;
}
#endif

/**
 *  This function return 0 if indexes present are even
 *  returns 1 if indexes present are odd
 *  return -1 if indexes present are -1
**/
int typeIndex(int *phostindex) {
  if(phostindex[0] == -1)
    return -1;
  if((phostindex[0]%2) == 0)
    return 0;
  else 
    return 1;
}

/**
 * This function returns 1 is pindex is found 
 * in the phostindex array else
 * returns 0
 **/
int checkIndex(int pindex, int *phostindex) {
  int i;
  for(i=0; i<numHostsInSystem; i++) {
    if(phostindex[i] == pindex)
      return 1;
  }
  return 0;
}

/* This function initiates the transaction commit process
 * Spawns threads for each of the new connections with Participants
 * and creates new piles by calling the createPiles(),
 * Sends a transrequest() to each remote machines for objects found remotely
 * and calls handleLocalReq() to process objects found locally */
int transCommit() {
  unsigned int tot_bytes_mod, *listmid;
  plistnode_t *pile, *pile_ptr;
  char treplyretry; /* keeps track of the common response that needs to be sent */
  int firsttime=1;
  trans_commit_data_t transinfo; /* keeps track of objs locked during transaction */
  char finalResponse;
  struct writestruct writebuffer;
  writebuffer.offset=0;
#ifdef RECOVERY
  int deadsd = -1;
  int deadmid = -1;
  unsigned int transID = getNewTransID();
  unsigned int epoch_num;
  tlist_node_t* tNode;
#endif

#ifdef DEBUG
  printf("%s -> Starts transCommit\n",__func__);
#endif

#ifdef ABORTREADERS
  if (t_abort) {
    //abort this transaction
    /* Debug
     * printf("ABORTING TRANSACTION AT COMMIT\n");
     */
    removetransactionhash();
    objstrDelete(t_cache);
    t_chashDelete();
#ifndef DEBUG
	  printf("%s-> End, line:%d\n\n", __func__, __LINE__);
#endif
    return 1;
  }
#endif

#ifdef RECOVERY
  while(okCommit != TRANS_OK) {
//    printf("%s -> new Transaction is waiting\n",__func__);
 //   sleep(1);
    randomdelay();
  }
#endif

  pthread_mutex_lock(&recovery_mutex);
  epoch_num = currentEpoch;
  pthread_mutex_unlock(&recovery_mutex);
    
  int treplyretryCount = 0;
  /* Initialize timeout for exponential delay */
  exponential_backoff.tv_sec = 0;
  exponential_backoff.tv_nsec = (long)(12000);//12 microsec
  count_exponential_backoff = 0;
  do {
    treplyretry = 0;

    /* Look through all the objects in the transaction record and make piles
     * for each machine involved in the transaction*/
    if (firsttime) {
      pile_ptr = pile = createPiles();
      pile_ptr = pile = sortPiles(pile);
    } else {
      pile = pile_ptr;
    }
    firsttime = 0;
    /* Create the packet to be sent in TRANS_REQUEST */

    /* Count the number of participants */
    int pilecount;
    pilecount = pCount(pile);

    /* Create a list of machine ids(Participants) involved in transaction   */
    listmid = calloc(pilecount, sizeof(unsigned int));
    pListMid(pile, listmid);

    /* Create a socket and getReplyCtrl array, initialize */
    int socklist[pilecount];
    unsigned int midlist[pilecount];
    char getReplyCtrl[pilecount];
    int loopcount;
    for(loopcount = 0 ; loopcount < pilecount; loopcount++) {
      socklist[loopcount] = 0;
      getReplyCtrl[loopcount] = 0;
    }

    /* Process each machine pile */
    int sockindex = 0;
    int localReqsock = -1;
    trans_req_data_t *tosend;
    tosend = calloc(pilecount, sizeof(trans_req_data_t));

//    printf("%s -> transID : %u Start!\n",__func__,transID);

    while(pile != NULL) {
#ifdef DEBUG
			printf("%s-> New pile:[%s],", __func__, midtoIPString(pile->mid));
			printf(" myIp:[%s]\n", midtoIPString(myIpAddr));
#endif
      tosend[sockindex].f.control = TRANS_REQUEST;
      tosend[sockindex].f.transid = transID;
			tosend[sockindex].f.mcount = pilecount;
			tosend[sockindex].f.numread = pile->numread;
			tosend[sockindex].f.nummod = pile->nummod;
			tosend[sockindex].f.numcreated = pile->numcreated;
			tosend[sockindex].f.sum_bytes = pile->sum_bytes;
      tosend[sockindex].f.epoch_num = epoch_num;
			tosend[sockindex].listmid = listmid;
			tosend[sockindex].objread = pile->objread;
			tosend[sockindex].oidmod = pile->oidmod;
			tosend[sockindex].oidcreated = pile->oidcreated;


      midlist[sockindex] = pile->mid; // debugging purpose

      int sd = 0;
			if(pile->mid != myIpAddr) {
				if((sd = getSockWithLock(transRequestSockPool, pile->mid)) < 0) {
					printf("\ntransRequest(): socket create error\n");
					free(listmid);
					free(tosend);
#ifndef DEBUG
					printf("%s-> End, line:%d\n\n", __func__, __LINE__);
#endif
					return 1;
				}
				socklist[sockindex] = sd;
				/* Send bytes of data with TRANS_REQUEST control message */
				send_data(sd, &(tosend[sockindex].f), sizeof(fixed_data_t));
                //send_buf(sd, &writebuffer, &(tosend[sockindex].f), sizeof(fixed_data_t));

				/* Send list of machines involved in the transaction */
				{
					int size=sizeof(unsigned int)*(tosend[sockindex].f.mcount);
					send_data(sd, tosend[sockindex].listmid, size);
                    //send_buf(sd, &writebuffer, tosend[sockindex].listmid, size);
				}

				/* Send oids and version number tuples for objects that are read */
				{
					int size=(sizeof(unsigned int)+sizeof(unsigned short))*(tosend[sockindex].f.numread);
					send_data(sd, tosend[sockindex].objread, size);
                    //send_buf(sd, &writebuffer, tosend[sockindex].objread, size);
				}

				/* Send objects that are modified */
				void *modptr;
				if((modptr = calloc(1, tosend[sockindex].f.sum_bytes)) == NULL) {
					printf("Calloc error for modified objects %s, %d\n", __FILE__, __LINE__);
					free(listmid);
					free(tosend);
					return 1;
				}
				int offset = 0;
				int i;
				for(i = 0; i < tosend[sockindex].f.nummod ; i++) {
					int size;
					objheader_t *headeraddr;
					if((headeraddr = t_chashSearch(tosend[sockindex].oidmod[i])) == NULL) {
						printf("%s() Error: No such oid %s, %d\n", __func__, __FILE__, __LINE__);
						free(modptr);
						free(listmid);
						free(tosend);
						return 1;
					}
					GETSIZE(size,headeraddr);
					size+=sizeof(objheader_t);
					memcpy(modptr+offset, headeraddr, size);
					offset+=size;
				}
				send_data(sd, modptr, tosend[sockindex].f.sum_bytes);
                //forcesend_buf(sd, &writebuffer, modptr, tosend[sockindex].f.sum_bytes);


				free(modptr);
			} else { //handle request locally
        handleLocalReq(&tosend[sockindex], &transinfo, &getReplyCtrl[sockindex]);
			}
			sockindex++;
			pile = pile->next;
		} //end of pile processing
   
		/* Recv Ctrl msgs from all machines */
#ifdef DEBUG
		printf("%s-> Finished sending transaction read/mod objects transID = %u\n",__func__,transID);
#endif
		int i;

		for(i = 0; i < pilecount; i++) {
			int sd = socklist[i]; 
			if(sd != 0) {
				char control;
        int timeout;            // a variable to check if the connection is still alive. if it is -1, then need to transcommit again
//        printf("%s -> Waiting for mid : %s transID = %u\n",__func__,midtoIPString(midlist[i]),transID);
        timeout = recv_data(sd, &control, sizeof(char));

//        printf("%s -> Received mid : %s control %d timeout = %d\n",__func__,midtoIPString(midlist[i]),control,timeout);
				//Update common data structure with new ctrl msg
				getReplyCtrl[i] = control;
				/* Recv Objects if participant sends TRANS_DISAGREE */
#ifdef CACHE
				if(control == TRANS_DISAGREE) {
					int length;
					timeout = recv_data(sd, &length, sizeof(int));
					void *newAddr;
					pthread_mutex_lock(&prefetchcache_mutex);
					if ((newAddr = prefetchobjstrAlloc((unsigned int)length)) == NULL) {
						printf("Error: %s() objstrAlloc error for copying into prefetch cache %s, %d\n", __func__, __FILE__, __LINE__);
						free(tosend);
						free(listmid);
						pthread_mutex_unlock(&prefetchcache_mutex);
						return 1;
					}
					pthread_mutex_unlock(&prefetchcache_mutex);
					timeout = recv_data(sd, newAddr, length);
					int offset = 0;
					while(length != 0) {
						unsigned int oidToPrefetch;
						objheader_t * header;
						header = (objheader_t *)(((char *)newAddr) + offset);
						oidToPrefetch = OID(header);
						STATUS(header)=0;
						int size = 0;
						GETSIZE(size, header);
						size += sizeof(objheader_t);
						//make an entry in prefetch hash table
            prehashInsert(oidToPrefetch, header);
						length = length - size;
						offset += size;
					}
				} //end of receiving objs
#endif
        
//        printf("%s -> Pass this point2\n",__func__);
#ifdef RECOVERY
        if(timeout < 0) {
          deadmid = listmid[i];
          deadsd = sd;
          getReplyCtrl[i] = TRANS_DISAGREE;
        }
#endif
			}
		}
        /* Decide the final response */
		if((finalResponse = decideResponse(getReplyCtrl, &treplyretry, pilecount)) == 0) {
			printf("Error: %s() in updating prefetch cache %s, %d\n", __func__, __FILE__, __LINE__);
			free(tosend);
			free(listmid);
			return 1;
		}

#ifdef RECOVERY
// wait until leader fix the system
    if(finalResponse == RESPOND_HIGHER_EPOCH) {
      printf("%s -> Received Higher epoch\n",__func__);
      finalResponse = TRANS_ABORT;
      treplyretry = 0;
    }
#endif
//    printf("%s -> transID = %u Passed this point\n",__func__,transID);
    pthread_mutex_lock(&translist_mutex);
    transList = tlistInsertNode(transList,transID,-3,TRYING_TO_COMMIT,epoch_num);
    tNode = tlistSearch(transList,transID);
    pthread_mutex_unlock(&translist_mutex);

#ifdef CACHE
    if (finalResponse == TRANS_COMMIT) {
      /* Invalidate objects in other machine cache */
      int retval;
      if((retval = invalidateObj(tosend, pilecount,finalResponse,socklist)) != 0) {
	printf("Error: %s() in invalidating Objects %s, %d\n", __func__, __FILE__, __LINE__);
	free(tosend);
	free(listmid);
	return 1;
      }
    }
#endif

  if(finalResponse == TRANS_COMMIT) {
    tNode->decision = finalResponse;
    tNode->status = TRANS_INPROGRESS;
    if(okCommit == TRANS_OK && inspectEpoch(epoch_num,"TRANS_COMMIT") > 0)
    {
      finalResponse = tNode->decision;
      thashInsert(transID,tNode->decision);
      commitMessages(epoch_num,socklist,deadsd,pilecount,tosend,tNode->decision,treplyretry,transinfo);
      tNode->status = TRANS_AFTER;
    }
    else { 
      tNode->status = TRYING_TO_COMMIT;
      if(inspectEpoch(epoch_num,"TRANS_COMMIT2") > 0) {
        treplyretry = 1; 
      }
      finalResponse = TRANS_ABORT;
      commitMessages(epoch_num,socklist,deadsd,pilecount,tosend,finalResponse,treplyretry,transinfo);
    }
  }
  else {
    commitMessages(epoch_num,socklist,deadsd,pilecount,tosend,finalResponse,treplyretry,transinfo);
  }
 
  //===========  after transaction point
  pthread_mutex_lock(&translist_mutex);
  transList = tlistRemove(transList,transID);
  pthread_mutex_unlock(&translist_mutex);

  for(i = 0; i< pilecount; i++) {
     if(socklist[i] > 0) {
       freeSockWithLock(transRequestSockPool,listmid[i], socklist[i]);
     }
   }
		/* Free resources */
  free(tosend);
  free(listmid);
  if (!treplyretry)
    pDelete(pile_ptr);
  /* wait a random amount of time before retrying to commit transaction*/
  if(treplyretry) {
      randomdelay();
#ifdef TRANSSTATS
			nSoftAbort++;
#endif
		}
	} while (treplyretry && deadmid != -1);

	if(finalResponse == TRANS_ABORT) {
#ifdef TRANSSTATS
		numTransAbort++;
#endif
    /* Free Resources */
    objstrDelete(t_cache);
    t_chashDelete();
#ifdef RECOVERY
    if(deadmid != -1) { /* if deadmid is greater than or equal to 0, then there is dead machine. */
      notifyLeaderDeadMachine(deadmid);
    }
#endif
    return TRANS_ABORT;
  } else if(finalResponse == TRANS_COMMIT) {
#ifdef TRANSSTATS
		numTransCommit++;
#endif
    /* Free Resources */
    objstrDelete(t_cache);
    t_chashDelete();
    return 0;
  } else {
    //TODO Add other cases
    printf("Error: in %s() THIS SHOULD NOT HAPPEN.....EXIT PROGRAM\n", __func__);
    exit(-1);
  }
#ifdef DEBUG
	printf("%s-> End, line:%d\n\n", __func__, __LINE__);
#endif
  return 0;
}

void commitMessages(unsigned int epoch_num,int* socklist,unsigned int deadsd,int pilecount,trans_req_data_t* tosend,char finalResponse,char treplyretry,trans_commit_data_t transinfo ) {
  int i;
  /* Send responses to all machines */
	for(i = 0; i < pilecount; i++) {
	  int sd = socklist[i];
#ifdef RECOVERY
    if(sd != deadsd) {
#endif
    if(sd != 0) {
#ifdef CACHE
	    if(finalResponse == TRANS_COMMIT) {
		    int retval;
			  /* Update prefetch cache */
			  if((retval = updatePrefetchCache(&(tosend[i]))) != 0) {
			    printf("Error: %s() in updating prefetch cache %s, %d\n", __func__, __FILE__, __LINE__);
//				  free(tosend);
  //			  free(listmid);
          exit(0);
//		  	  return 1;
			  }
#ifdef ABORTREADERS
		  	removetransaction(tosend[i].oidmod,tosend[i].f.nummod);
			  removethisreadtransaction(tosend[i].objread, tosend[i].f.numread);
#endif
      }
#ifdef ABORTREADERS
      else if (!treplyretry) {
	      removethistransaction(tosend[i].oidmod,tosend[i].f.nummod);
	      removethisreadtransaction(tosend[i].objread,tosend[i].f.numread);
	    }
#endif
#endif
      send_data(sd,&finalResponse,sizeof(char));
      send_data(sd,&epoch_num,sizeof(unsigned int));
     } else {
     /* Complete local processing */
     finalResponse = doLocalProcess(finalResponse, &(tosend[i]), &transinfo);
#ifdef ABORTREADERS
      if(finalResponse == TRANS_COMMIT) {
			  removetransaction(tosend[i].oidmod,tosend[i].f.nummod);
  		  removethisreadtransaction(tosend[i].objread,tosend[i].f.numread);
	  	} else if (!treplyretry) {
		    removethistransaction(tosend[i].oidmod,tosend[i].f.nummod);
			  removethisreadtransaction(tosend[i].objread,tosend[i].f.numread);
			}
#endif
  	}
#ifdef RECOVERY
    } 
#endif    
  }
}

/* This function handles the local objects involved in a transaction
 * commiting process.  It also makes a decision if this local machine
 * sends AGREE or DISAGREE or SOFT_ABORT to coordinator */
void handleLocalReq(trans_req_data_t *tdata, trans_commit_data_t *transinfo, char *getReplyCtrl) {
  unsigned int *oidnotfound = NULL, *oidlocked = NULL;
  int numoidnotfound = 0, numoidlocked = 0;
  int v_nomatch = 0, v_matchlock = 0, v_matchnolock = 0;
  int numread, i;
  unsigned int oid;
  unsigned short version;

  /* Counters and arrays to formulate decision on control message to be sent */
  oidnotfound = (unsigned int *) calloc((tdata->f.numread + tdata->f.nummod), sizeof(unsigned int));
	oidlocked = (unsigned int *) calloc((tdata->f.numread + tdata->f.nummod +1), sizeof(unsigned int)); // calloc additional 1 byte for
	//setting a divider between read and write locks
	numread = tdata->f.numread;
	/* Process each oid in the machine pile/ group per thread */
	for (i = 0; i < tdata->f.numread + tdata->f.nummod; i++) {
		if (i < tdata->f.numread) {
			int incr = sizeof(unsigned int) + sizeof(unsigned short); // Offset that points to next position in the objread array
			incr *= i;
			oid = *((unsigned int *)(((char *)tdata->objread) + incr));
			version = *((unsigned short *)(((char *)tdata->objread) + incr + sizeof(unsigned int)));
			commitCountForObjRead(getReplyCtrl, oidnotfound, oidlocked, &numoidnotfound, &numoidlocked, &v_nomatch, &v_matchlock, &v_matchnolock, oid, version);
		} else { // Objects Modified
			if(i == tdata->f.numread) {
				oidlocked[numoidlocked++] = -1;
			}
			int tmpsize;
			objheader_t *headptr;
			headptr = (objheader_t *) t_chashSearch(tdata->oidmod[i-numread]);
			if (headptr == NULL) {
				printf("Error: handleLocalReq() returning NULL, no such oid %s, %d\n", __FILE__, __LINE__);
				return;
			}
			oid = OID(headptr);
			version = headptr->version;
			commitCountForObjMod(getReplyCtrl, oidnotfound, oidlocked, &numoidnotfound, &numoidlocked, &v_nomatch, &v_matchlock, &v_matchnolock, oid, version);
		}
  }

	/* Fill out the trans_commit_data_t data structure. This is required for a trans commit process
	 * if Participant receives a TRANS_COMMIT */
	transinfo->objlocked = oidlocked;
	transinfo->objnotfound = oidnotfound;
	transinfo->modptr = NULL;
	transinfo->numlocked = numoidlocked;
	transinfo->numnotfound = numoidnotfound;

  /* Condition to send TRANS_AGREE */
  if(v_matchnolock == tdata->f.numread + tdata->f.nummod) {
    *getReplyCtrl = TRANS_AGREE;
  }
  /* Condition to send TRANS_SOFT_ABORT */
  if((v_matchlock > 0 && v_nomatch == 0) || (numoidnotfound > 0 && v_nomatch == 0)) {
    *getReplyCtrl = TRANS_SOFT_ABORT;
  }
}

char doLocalProcess(char finalResponse, trans_req_data_t *tdata, trans_commit_data_t *transinfo) {
  if(finalResponse == TRANS_ABORT) {
    if(transAbortProcess(transinfo) != 0) {
      printf("Error in transAbortProcess() %s,%d\n", __FILE__, __LINE__);
      fflush(stdout);
      return;
    }
  } else if(finalResponse == TRANS_COMMIT) {
    if(transComProcess(tdata, transinfo) != 0) {
      printf("Error in transComProcess() %s,%d\n", __FILE__, __LINE__);
      fflush(stdout);
      return;
    }
  } else {
    printf("%s -> ERROR...No Decision transID = %u finalResponse = %d\a\n",__func__,tdata->f.transid,finalResponse);
  }


  /* Free memory */
  if (transinfo->objlocked != NULL) {
    free(transinfo->objlocked);
  }
  if (transinfo->objnotfound != NULL) {
    free(transinfo->objnotfound);
  }

  return finalResponse;
}

/* This function decides the reponse that needs to be sent to
 * all Participant machines after the TRANS_REQUEST protocol */
char decideResponse(char *getReplyCtrl, char *treplyretry, int pilecount) {
  int i, transagree = 0, transdisagree = 0, transsoftabort = 0; /* Counters to formulate decision of what
								   message to send */

  int higher_epoch_num=0;
  for (i = 0 ; i < pilecount; i++) {
    char control;
    control = getReplyCtrl[i];
    switch(control) {
    default:
#ifndef DEBUG
      printf("%s-> Participant sent unknown message, i:%d, Control: %d\n", __func__, i, (int)control);
#endif

      /* treat as disagree, pass thru */
    case TRANS_DISAGREE:
      transdisagree++;
#ifdef DEBUG
      printf("%s-> Participant sent TRANS_DISAGREE, i:%d, Control: %d\n", __func__, i, (int)control);
#endif
      break;

    case TRANS_AGREE:
      transagree++;
#ifdef DEBUG
      printf("%s-> Participant sent TRANS_AGREE, i:%d, Control: %d\n", __func__, i, (int)control);
#endif
      break;

    case TRANS_SOFT_ABORT:
      transsoftabort++;
#ifdef DEBUG
      printf("%s-> Participant sent TRANS_SOFT_ABORT, i:%d, Control: %d\n", __func__, i, (int)control);
#endif
      break;
    case RESPOND_HIGHER_EPOCH:
      higher_epoch_num++;
#ifdef DEBUG                                                                                              
      printf("%s-> Participant sent TRANS_DISAGREE, i:%d, Control: %d\n", __func__, i, (int)control);     
#endif 
      break;
    }
  }

  if(higher_epoch_num > 0)
    return RESPOND_HIGHER_EPOCH;


  if(transdisagree > 0) {
    /* Send Abort */
    *treplyretry = 0;
    return TRANS_ABORT;
#ifdef CACHE
    /* clear objects from prefetch cache */
    //cleanPCache();
#endif
  } else if(transagree == pilecount) {
    /* Send Commit */
    *treplyretry = 0;
    return TRANS_COMMIT;
  } else {
    /* Send Abort in soft abort case followed by retry commiting transaction again*/
    *treplyretry = 1;
    return TRANS_ABORT;
  }
  return 0;
}

/* This function opens a connection, places an object read request to
 * the remote machine, reads the control message and object if
 * available and copies the object and its header to the local
 * cache. */

void *getRemoteObj(unsigned int mnum, unsigned int oid) {
#ifdef DEBUG
  printf("%s -> entering\n",__func__);
#endif
  int size, val;
  struct sockaddr_in serv_addr;
  char control = 0;
  objheader_t *h;
  void *objcopy = NULL;

  //int sd = getSock2(transReadSockPool, mnum);
  int sd;
  if((sd = getSockWithLock(transReadSockPool, mnum)) < 0) {
    printf("%s() Socket Create Error at %s, %d\n", __func__, __FILE__, __LINE__);
    return NULL;
  }
  char readrequest[sizeof(char)+sizeof(unsigned int)];
  readrequest[0] = READ_REQUEST;
  *((unsigned int *)(&readrequest[1])) = oid;
  send_data(sd, readrequest, sizeof(readrequest));
  
  /* Read response from the Participant */
  if(recv_data(sd, &control, sizeof(char)) < 0) {
    transRetryFlag = 1;
    return NULL;
  }

  if (control==OBJECT_NOT_FOUND) {
    objcopy = NULL;
  } else if(control==OBJECT_FOUND) {
  
  /* Read object if found into local cache */

  if(recv_data(sd, &size, sizeof(int)) < 0) {
    transRetryFlag = 1;
    return NULL;
  }

  objcopy = objstrAlloc(&t_cache, size);

  if(recv_data(sd, objcopy, size) < 0) {
    transRetryFlag = 1;
    return NULL;
  }
  STATUS(objcopy)=0;
  
  /* Insert into cache's lookup table */
  t_chashInsert(oid, objcopy);
#ifdef TRANSSTATS
  totalObjSize += size;
#endif
	}
  freeSockWithLock(transReadSockPool, mnum, sd);
	return objcopy;
}

#ifdef RECOVERY
void notifyLeaderDeadMachine(unsigned int deadHost) {

  unsigned int epoch_num;

	if(!liveHosts[findHost(deadHost)]) {  // if it is already fixed
//    printf("%s -> already fixed\n",__func__);
		sleep(WAIT_TIME);
		return;
	}
  
  pthread_mutex_lock(&liveHosts_mutex);
  liveHosts[findHost(deadHost)] = 0;
  numLiveHostsInSystem--;
  pthread_mutex_unlock(&liveHosts_mutex);

  if(numLiveHostsInSystem == 1)
    return;

  // increase epoch number by number machines in the system
  pthread_mutex_lock(&recovery_mutex);
  epoch_num = currentEpoch = INCREASE_EPOCH(currentEpoch,numHostsInSystem,myIndexInHostArray);
  okCommit = TRANS_BEFORE;
  pthread_mutex_unlock(&recovery_mutex);

  // notify all machines that this machien will act as leader.
  // if return -1, then a machine that higher epoch_num started restoration
  restoreDuplicationState(deadHost,epoch_num);
}

/* Leader's role */ 
void restoreDuplicationState(unsigned int deadHost,unsigned int epoch_num)
{
  int* sdlist;
  tlist_t* tList;
  int flag = 0;

#ifdef RECOVERYSTATS
//  printf("Recovery Start\n");
  long long st;
  long long fi;
  unsigned int dupeSize = 0;  // to calculate the size of backed up data

  st = myrdtsc(); // to get clock
#endif
  // update leader's live host list and object locations
  
  do {
    do {
      sdlist = getSocketLists();
  
      printf("%s -> I'm currently leader num : %d ping machines\n\n",__func__,epoch_num);
      if((flag = pingMachines(epoch_num,sdlist,&tList)) < 0) break;

      pthread_mutex_lock(&translist_mutex);
//      tlistPrint(tList);
      pthread_mutex_unlock(&translist_mutex);
//      getchar();
      printf("%s -> I'm currently leader num : %d releaseing new lists\n\n",__func__,epoch_num);
      if((flag = releaseNewLists(epoch_num,sdlist,tList)) < 0) break;
  //    getchar();
      printf("%s -> I'm currently leader num : %d duplicate objects\n\n",__func__,epoch_num);
      // transfer lost objects
      if((flag= duplicateLostObjects(epoch_num,sdlist)) < 0) break;

      // restart transactions
      restartTransactions(epoch_num,sdlist);
    }while(0);

    freeSocketLists(sdlist);

    // falg == 0  - fixed
    //      == -1 - higher epoch
    //      == -2 - found another failure, redo everything
    if(flag > -2)
      break;

//    printf("%s -> Retry \n",__func__);
  }while(0);

  if(flag < 0) {
    printf("%s -> higher epoch\n",__func__);
    while(okCommit != TRANS_OK) {
//      sleep(3);
      randomdelay();
    }
    
  }else { 
    printf("%s -> I was leader! num : %d\n",__func__,epoch_num);
#ifdef RECOVERYSTATS
  fi = myrdtsc();
  recoverStat[numRecovery].elapsedTime = (fi-st)/CPU_FREQ;
  recoverStat[numRecovery].recoveredData = flag;
  numRecovery++;
  printRecoveryStat();
#endif
  }
}

int* getSocketLists()
{
  struct sockaddr_in remoteAddr[numHostsInSystem];
  int* sdlist;
  char request = RESPOND_LIVE;
  char response;
  int i;
  int sd;

  if((sdlist = calloc(numHostsInSystem,sizeof(int))) == NULL) 
  {
    printf("%s -> calloc error\n",__func__);
    exit(0);
  }

  // open sockets to all live machines
  for(i = 0 ; i < numHostsInSystem; i++) {
    if(liveHosts[i] == 1) {
      if((sd = socket(AF_INET , SOCK_STREAM, 0 )) < 0) 
      {
        sdlist[i] = -1;
        liveHosts[i] = 0;
      }
      else {
        bzero(&remoteAddr[i], sizeof(remoteAddr[i]));
        remoteAddr[i].sin_family = AF_INET;
        remoteAddr[i].sin_port = htons(LISTEN_PORT);
        remoteAddr[i].sin_addr.s_addr = htonl(hostIpAddrs[i]);
//        printf("%s -> open sd : %d to %s\n",__func__,sd,midtoIPString(hostIpAddrs[i]));

        if(connect(sd, (struct sockaddr *)&remoteAddr[i], sizeof(remoteAddr[i])) < 0) {
          sdlist[i] = -1;
          liveHosts[i] = 0;
        }
        else {
          send_data(sd,&request,sizeof(char));

          recv_data(sd,&response,sizeof(char));
          
          if(response == LIVE) {
            sdlist[i] = sd;
            liveHosts[i] = 1;
          }        
        }
      }
    }
    else {
      liveHosts[i] = 0;
      sdlist[i] = -1;
    }
  }
  
  return sdlist;
}

void freeSocketLists(int* sdlist)
{
  int i;
  for(i = 0 ; i < numHostsInSystem; i++) {
   if(sdlist[i] != -1) {
     close(sdlist[i]);
   }
  }

  free(sdlist);
}

// stop transactions, receive translists, live machine lists.
int pingMachines(unsigned int epoch_num,int* sdlist,tlist_t** tList)
{

//  printf("%s -> Enter\n",__func__);
  int i;
  char request;
  char response;
  tlist_t* currentTransactionList;
   
  if(inspectEpoch(epoch_num,__func__) < 0) {
    printf("%s -> Higher Epoch\n",__func__);
    return -1;
  }

  currentTransactionList = tlistCreate();

  // request remote amchines to stop all transactions
  for(i = 0; i < numHostsInSystem; i++)
  {
    if(sdlist[i] == -1 || hostIpAddrs[i] == myIpAddr)
      continue;
  
//    printf("%s -> sending request_trans_wait to %s\n",__func__,midtoIPString(hostIpAddrs[i]));
    request = REQUEST_TRANS_WAIT;
    send_data(sdlist[i],&request, sizeof(char));
    send_data(sdlist[i],&epoch_num,sizeof(unsigned int));
    send_data(sdlist[i],&myIndexInHostArray,sizeof(unsigned int));
  }

//  printf("%s -> Stop transaction\n",__func__);
  /* stop all local transactions */
  if(stopTransactions(TRANS_BEFORE,epoch_num) < 0)
    return -1;

//  printf("After Stop transaction\n");

  // grab leader's transaction list first
  tlist_node_t* walker = transList->head;
 
  while(walker) {
    pthread_mutex_lock(&translist_mutex);
    currentTransactionList = tlistInsertNode2(currentTransactionList,walker,epoch_num);
    pthread_mutex_unlock(&translist_mutex);
    walker = walker->next;
  }

//  printf("%s -> Local Transactions\n",__func__);
//  tlistPrint(currentTransactionList);

  for(i = 0; i < numHostsInSystem; i++)
  {
    if(sdlist[i] == -1 || hostIpAddrs[i] == myIpAddr)
      continue;

//    printf("%s -> receving from %s\n",__func__,midtoIPString(hostIpAddrs[i]));
    if(recv_data(sdlist[i],&response,sizeof(char)) < 0)
    {
      printf("Here\n");
      pthread_mutex_lock(&translist_mutex);
      tlistDestroy(currentTransactionList);
      pthread_mutex_unlock(&translist_mutex);
      return -2;
    }

    printf("recevied response = %d\n",response);
    if(response == RESPOND_TRANS_WAIT) 
    {
//      printf("%s -> RESPOND_TRANS_WAIT\n",__func__);
      int timeout1 = computeLiveHosts(sdlist[i]);
//      printf("%s -> received host list\n",__func__);
      int timeout2 = makeTransactionLists(&currentTransactionList,sdlist[i],epoch_num);
//      printf("%s -> received transaction list\n",__func__);
      // receive live host list       // receive transaction list
      if(timeout1 < 0 || timeout2 < 0) {
        pthread_mutex_lock(&translist_mutex);
        tlistDestroy(currentTransactionList);
        pthread_mutex_unlock(&translist_mutex);
        return -2;
      }
 //     tlistPrint(currentTransactionList);
    }
    else if(response == RESPOND_HIGHER_EPOCH)
    {
      printf("%s -> RESPOND_HIGHER_EPOCH\n",__func__);
      pthread_mutex_lock(&translist_mutex);
      tlistDestroy(currentTransactionList);
      pthread_mutex_unlock(&translist_mutex);
      return -1;
    }
    else {
      printf("%s -> no response mid : %s\n",__func__,midtoIPString(hostIpAddrs[i]));
      liveHosts[i] = 0;
      sdlist[i] = -1;
    }
  }
  
  walker = currentTransactionList->head;

  while(walker) {
    if(walker->decision == DECISION_LOST) {
      printf("%s -> No one knows decision for transID : %u\n",__func__,walker->transid);
      walker->decision = TRANS_ABORT;
    }
    walker = walker->next;
  }
  *tList = currentTransactionList;

  printf("%s -> Exit\n",__func__);
  return 0;
}

int computeLiveHosts(int sd)
{
  int receivedHost[numHostsInSystem];
  int i;
  
  if(recv_data(sd,receivedHost,sizeof(int)*numHostsInSystem) < 0)
    return -2;

  for(i = 0 ; i < numHostsInSystem;i ++)
  {
    if(liveHosts[i] == 1 && receivedHost[i] == 1)
    {
      liveHosts[i] = 1;
    }
    else 
      liveHosts[i] = 0;
  }
  
  return 0;
}

int releaseNewLists(unsigned int epoch_num,int* sdlist,tlist_t* tlist)
{
  printf("%s -> Enter\n",__func__);
  int i;
  char response = RELEASE_NEW_LIST;
  int size;
  int flag;
  tlist_node_t* tArray;
  
  
  if(inspectEpoch(epoch_num,__func__) < 0) return -1;  
  
  tArray = tlistToArray(tlist,&size);

  for(i = 0; i < numHostsInSystem; i++)
  {
    if(sdlist[i] != -1 && hostIpAddrs[i] != myIpAddr)
    {
      send_data(sdlist[i],&response,sizeof(char));
      send_data(sdlist[i],&epoch_num,sizeof(unsigned int));

      // new host list
      pthread_mutex_lock(&liveHosts_mutex);
      send_data(sdlist[i],liveHosts,sizeof(int) * numHostsInSystem);
      pthread_mutex_unlock(&liveHosts_mutex);

      if(size == 0) {
        size = -1;
        send_data(sdlist[i],&size,sizeof(int));
      }
      else {
        send_data(sdlist[i],&size,sizeof(int));
        send_data(sdlist[i],tArray,sizeof(tlist_node_t) * size);
      }
    }
    else if(hostIpAddrs[i] == myIpAddr) {
      setLocateObjHosts();
//      printHostsStatus();
      flag = combineTransactionList(tArray,size);

      if(flag == 0) {
        printf("%s -> problem\n",__func__);
        exit(0);
      }
      if(stopTransactions(TRANS_AFTER,epoch_num) < 0)
        return -1;
    }
  }

  printf("%s -> After sending msg\n",__func__);
  if(size > 0)
    free(tArray);

  for(i = 0; i < numHostsInSystem; i ++) {
    if(sdlist[i] != -1 && hostIpAddrs[i] != myIpAddr)
    {
 //     printf("%s -> Waiting for %s\n",__func__,midtoIPString(hostIpAddrs[i]));
      if(recv_data(sdlist[i], &response, sizeof(char)) < 0) {
        tlistDestroy(tlist);  
        return -2;
      }
      if(response != TRANS_OK && response != RESPOND_HIGHER_EPOCH)
      {
        printf("%s -> response : %d Need to fix\n",__func__,response);
      }
      else if(response == RESPOND_HIGHER_EPOCH)
      {
        printf("%s -> Higher epoch!\n",__func__);
        return -1;
      }
    }
  }
  tlistDestroy(tlist);  
  printf("%s -> End\n",__func__);
  return 0;
}

// after this fuction
// leader knows all the on-going transaction list and their decisions
int makeTransactionLists(tlist_t** tlist,int sd,unsigned int epoch_num)
{
  tlist_node_t* transArray;
  tlist_node_t* tmp;
  tlist_node_t* walker;
  int j;
  int i;
  int size;

  // receive all on-going transaction list
  if(recv_data(sd, &size, sizeof(int)) < 0) 
    return -2;

  if((transArray = calloc(size, sizeof(tlist_node_t))) == NULL) {
    printf("%s -> calloc error\n",__func__);
    exit(0);
  }
      
  if(recv_data(sd,transArray, sizeof(tlist_node_t) * size) < 0) {
    free(transArray);
    return -2;
  }

  printf("%s -> Received TransArray\n",__func__);
  for(i = 0; i< size; i++) {
    printf("ID : %u  Decision : %d  status : %d\n",transArray[i].transid,transArray[i].decision,transArray[i].status);
  }
  printf("%s -> End transArray\n",__func__);

  // add into currentTransactionList
  for(j = 0 ; j < size; j ++) {
    tmp = tlistSearch(*tlist,transArray[j].transid);

    if(tmp == NULL) {
      tlist_node_t* tNode = &transArray[j];
      tNode->status = TRANS_OK;

      printf("%s -> transid = %u decision = %d\n",__func__,transArray[j].transid,transArray[j].decision);
      *tlist = tlistInsertNode2(*tlist,&(transArray[j]),epoch_num);
    }
    else {
      if((tmp->decision != TRANS_ABORT && tmp->decision != TRANS_COMMIT) && (transArray[j].decision == TRANS_COMMIT || transArray[j].decision == TRANS_ABORT))
      {
        tmp->decision = transArray[j].decision;
      }
    }
  }  // j loop
  
  free(transArray);

  // current transaction list is completed
  // now see if any transaction is still missing
  walker = (*tlist)->head;
  char request = REQUEST_TRANS_CHECK;
  char respond;

  while(walker) {
    send_data(sd, &request, sizeof(char));
    send_data(sd, &(walker->transid), sizeof(unsigned int));

    if(recv_data(sd, &respond, sizeof(char)) < 0)
      return -2;

    if(respond  > 0)
    {
      walker->decision = respond;
      break;
    } 
    walker = walker->next;
  } // while loop

  request = REQUEST_TRANS_COMPLETE;
  send_data(sd, &request,sizeof(char));    

  return 0;
}

void restartTransactions(unsigned int epoch_num,int* sdlist)
{
  int i;
  int sd;
  char request;
  char response;

  for(i = 0; i < numHostsInSystem; i++) {
    if(sdlist[i] == -1) 
      continue;

    printf("%s -> request to %s\n",__func__,midtoIPString(hostIpAddrs[i]));
    request = REQUEST_TRANS_RESTART;
    send_data(sdlist[i], &request, sizeof(char));
    send_data(sdlist[i], &epoch_num,sizeof(char));
  }
}

int inspectEpoch(unsigned int epoch_num,const char* f)
{
  int flag = 1;

//  printf("%s -> current epoch %u epoch num = %u\n",__func__,currentEpoch,epoch_num);
  pthread_mutex_lock(&recovery_mutex);
  if(epoch_num < currentEpoch) {
    flag = -1;
  }/*
  else if(epoch_num > currentEpoch) {
//    printf("%s -> current epoch %u is changed to epoch num = %u\n",f,currentEpoch,epoch_num);
//    currentEpoch = epoch_num;
  }*/
  pthread_mutex_unlock(&recovery_mutex);

  return flag;
}

#endif


/*  Commit info for objects modified */
void commitCountForObjMod(char *getReplyCtrl, unsigned int *oidnotfound, unsigned int *oidlocked, int *numoidnotfound,
                          int *numoidlocked, int *v_nomatch, int *v_matchlock, int *v_matchnolock, unsigned int oid, unsigned short version) {
  void *mobj;
  /* Check if object is still present in the machine since the beginning of TRANS_REQUEST */
  /* Save the oids not found and number of oids not found for later use */
  if ((mobj = mhashSearch(oid)) == NULL) { /* Obj not found */
    /* Save the oids not found and number of oids not found for later use */
    oidnotfound[*numoidnotfound] = oid;
    (*numoidnotfound)++;
  } else { /* If Obj found in machine (i.e. has not moved) */
    /* Check if Obj is locked by any previous transaction */
    if (write_trylock(STATUSPTR(mobj))) { // Can acquire write lock
      if (version == ((objheader_t *)mobj)->version) {      /* match versions */
	(*v_matchnolock)++;
	//Keep track of what is locked
	oidlocked[(*numoidlocked)++] = OID(((objheader_t *)mobj));
      } else { /* If versions don't match ...HARD ABORT */
	(*v_nomatch)++;
	/* Send TRANS_DISAGREE to Coordinator */
	*getReplyCtrl = TRANS_DISAGREE;

	//Keep track of what is locked
	oidlocked[(*numoidlocked)++] = OID(((objheader_t *)mobj));
	return;
      }
    } else { //A lock is acquired some place else
		      if (version == ((objheader_t *)mobj)->version) { /* Check if versions match */
	(*v_matchlock)++;
      } else { /* If versions don't match ...HARD ABORT */
	(*v_nomatch)++;
	/* Send TRANS_DISAGREE to Coordinator */
	*getReplyCtrl = TRANS_DISAGREE;
	return;
      }
    }
  }
}

/*  Commit info for objects modified */
void commitCountForObjRead(char *getReplyCtrl, unsigned int *oidnotfound, unsigned int *oidlocked, int *numoidnotfound,
                           int *numoidlocked, int *v_nomatch, int *v_matchlock, int *v_matchnolock, unsigned int oid, unsigned short version) {
  void *mobj;
  /* Check if object is still present in the machine since the beginning of TRANS_REQUEST */
  /* Save the oids not found and number of oids not found for later use */
  if ((mobj = mhashSearch(oid)) == NULL) { /* Obj not found */
		/* Save the oids not found and number of oids not found for later use */
		oidnotfound[*numoidnotfound] = oid;
		(*numoidnotfound)++;
	} else { /* If Obj found in machine (i.e. has not moved) */
		/* Check if Obj is locked by any previous transaction */
		if (read_trylock(STATUSPTR(mobj))) { // Can further acquire read locks
			if (version == ((objheader_t *)mobj)->version) {      /* If locked then match versions */
				(*v_matchnolock)++;
				//Keep track of what is locked
				oidlocked[(*numoidlocked)++] = OID(((objheader_t *)mobj));
			} else { /* If versions don't match ...HARD ABORT */
				(*v_nomatch)++;
				/* Send TRANS_DISAGREE to Coordinator */
				*getReplyCtrl = TRANS_DISAGREE;
				//Keep track of what is locked
				oidlocked[(*numoidlocked)++] = OID(((objheader_t *)mobj));
				return;
			}
		} else { //Has reached max number of readers or some other transaction
			//has acquired a lock on this object
			if (version == ((objheader_t *)mobj)->version) { /* Check if versions match */
				(*v_matchlock)++;
			} else { /* If versions don't match ...HARD ABORT */
				(*v_nomatch)++;
				/* Send TRANS_DISAGREE to Coordinator */
				*getReplyCtrl = TRANS_DISAGREE;
				return;
			}
    }
  }
}

/* This function completes the ABORT process if the transaction is aborting */
int transAbortProcess(trans_commit_data_t *transinfo) {
  int i, numlocked;
  unsigned int *objlocked;
  void *header;

  numlocked = transinfo->numlocked;
  objlocked = transinfo->objlocked;

  int useWriteUnlock = 0;
  for (i = 0; i < numlocked; i++) {
    if(objlocked[i] == -1) {
      useWriteUnlock = 1;
      continue;
    }
    if((header = mhashSearch(objlocked[i])) == NULL) {
      printf("mhashsearch returns NULL at %s, %d\n", __FILE__, __LINE__);
      return 1;
    }
    if(!useWriteUnlock) {
      read_unlock(STATUSPTR(header));
    } else {
      write_unlock(STATUSPTR(header));
    }
  }

  return 0;
}

/*This function completes the COMMIT process if the transaction is commiting*/
int transComProcess(trans_req_data_t *tdata, trans_commit_data_t *transinfo) {
  objheader_t *header, *tcptr;
  int i, nummod, tmpsize, numcreated, numlocked;
  unsigned int *oidmod, *oidcreated, *oidlocked;
  void *ptrcreate;
#ifdef DEBUG
	printf("%s-> Entering transComProcess, trans.c\n", __func__);
#endif

  nummod = tdata->f.nummod;
  oidmod = tdata->oidmod;
  numcreated = tdata->f.numcreated;
  oidcreated = tdata->oidcreated;
  numlocked = transinfo->numlocked;
  oidlocked = transinfo->objlocked;

  for (i = 0; i < nummod; i++) {
    if((header = (objheader_t *) mhashSearch(oidmod[i])) == NULL) {
      printf("Error: transComProcess() mhashsearch returns NULL at %s, %d\n", __FILE__, __LINE__);
      return 1;
    }
    /* Copy from transaction cache -> main object store */
    if ((tcptr = ((objheader_t *) t_chashSearch(oidmod[i]))) == NULL) {
      printf("Error: transComProcess() chashSearch returned NULL at %s, %d\n", __FILE__, __LINE__);
      return 1;
    }
    GETSIZE(tmpsize, header);
    char *tmptcptr = (char *) tcptr;
    {
      struct ___Object___ *dst=(struct ___Object___*)((char*)header+sizeof(objheader_t));
      struct ___Object___ *src=(struct ___Object___*)((char*)tmptcptr+sizeof(objheader_t));
      dst->___cachedCode___=src->___cachedCode___;
      dst->___cachedHash___=src->___cachedHash___;

      memcpy(&dst[1], &src[1], tmpsize-sizeof(struct ___Object___));
    }

    header->version += 1;
    if(header->notifylist != NULL) {
#ifdef RECOVERY
      //printf("%s -> to notifyAll\n",__func__);
      if(header->isBackup == 0) {  // if it is primary obj, notify 
        //printf("%s -> Called notifyAll\n",__func__);
        notifyAll(&header->notifylist, OID(header), header->version);
      }
      else                        // if not, just clear the notification list
        clearNotifyList(OID(header));
#else  
      printf("no way!\n");
      notifyAll(&header->notifylist, OID(header), header->version);
#endif
    }
  }
  /* If object is newly created inside transaction then commit it */
  for (i = 0; i < numcreated; i++) {
    if ((header = ((objheader_t *) t_chashSearch(oidcreated[i]))) == NULL) {
      printf("Error: transComProcess() chashSearch returned NULL for oid = %x at %s, %d\n", oidcreated[i], __FILE__, __LINE__);
      return 1;
    }
    header->version += 1;
    GETSIZE(tmpsize, header);
    tmpsize += sizeof(objheader_t);
    pthread_mutex_lock(&mainobjstore_mutex);
    if ((ptrcreate = objstrAlloc(&mainobjstore, tmpsize)) == NULL) {
      printf("Error: transComProcess() failed objstrAlloc %s, %d\n", __FILE__, __LINE__);
      pthread_mutex_unlock(&mainobjstore_mutex);
      return 1;
    }
    pthread_mutex_unlock(&mainobjstore_mutex);
    /* Initialize read and write locks */
    initdsmlocks(STATUSPTR(header));
    memcpy(ptrcreate, header, tmpsize);
    mhashInsert(oidcreated[i], ptrcreate);
    lhashInsert(oidcreated[i], myIpAddr);
  }
  /* Unlock locked objects */
  int useWriteUnlock = 0;
  for(i = 0; i < numlocked; i++) {
    if(oidlocked[i] == -1) {
      useWriteUnlock = 1;
      continue;
    }
    if((header = (objheader_t *) mhashSearch(oidlocked[i])) == NULL) {
      printf("mhashsearch returns NULL at %s, %d\n", __FILE__, __LINE__);
      return 1;
    }
    if(!useWriteUnlock) {
      read_unlock(STATUSPTR(header));
    } else {
      write_unlock(STATUSPTR(header));
    }
  }
  return 0;
}

prefetchpile_t *foundLocal(char *ptr, int numprefetches) {
  int i;
  int j;
  prefetchpile_t * head=NULL;

  for(j=0;j<numprefetches;j++) {
    int siteid = *(GET_SITEID(ptr));
    int ntuples = *(GET_NTUPLES(ptr));
    unsigned int * oidarray = GET_PTR_OID(ptr);
    unsigned short * endoffsets = GET_PTR_EOFF(ptr, ntuples);
    short * arryfields = GET_PTR_ARRYFLD(ptr, ntuples);
    int numLocal = 0;
    
    for(i=0; i<ntuples; i++) {
      unsigned short baseindex=(i==0) ? 0 : endoffsets[i-1];
      unsigned short endindex=endoffsets[i];
      unsigned int oid=oidarray[i];
      int newbase;
      int machinenum;
      int countInvalidObj=0;

      if (oid==0) {
	numLocal++;
	continue;
      }
      //Look up fields locally
      int isLastOffset=0;
      if(endindex==0)
          isLastOffset=1;
      for(newbase=baseindex; newbase<endindex; newbase++) {
        if(newbase==(endindex-1))
          isLastOffset=1;
	if (!lookupObject(&oid,arryfields[newbase],&countInvalidObj)) {
	  break;
	}
	//Ended in a null pointer...
	if (oid==0) {
	  numLocal++;
	  goto tuple;
	}
      }

      //Entire prefetch is local
      if (newbase==endindex&&checkoid(oid,isLastOffset)) {
	numLocal++;
	goto tuple;
      }

      //Add to remote requests
      machinenum=lhashSearch(oid);
#ifdef RECOVERY
    static int flipBit = 0;// Used to distribute requests between primary and backup evenly
                           // either primary or backup machine
    machinenum = (flipBit)?getPrimaryMachine(lhashSearch(oid)):getBackupMachine(lhashSearch(oid));
    flipBit ^= 1;
#ifdef DEBUG
//    printf("mindex:%d, oid:%d, machinenumber:%s\n", machinenumber, oid, midtoIPString(machinenumber));
#endif
#endif
      insertPile(machinenum, oid, endindex-newbase, &arryfields[newbase], &head);
    tuple:
      ;
    }
    
    /* handle dynamic prefetching */
    handleDynPrefetching(numLocal, ntuples, siteid);
    ptr=((char *)&arryfields[endoffsets[ntuples-1]])+sizeof(int);
  }

  return head;
}

/*
prefetchpile_t *foundLocal(char *ptr) {
	int siteid = *(GET_SITEID(ptr));
	int ntuples = *(GET_NTUPLES(ptr));
	unsigned int * oidarray = GET_PTR_OID(ptr);
	unsigned short * endoffsets = GET_PTR_EOFF(ptr, ntuples);
	short * arryfields = GET_PTR_ARRYFLD(ptr, ntuples);
	prefetchpile_t * head=NULL;
	int numLocal = 0;

	int i;
	for(i=0; i<ntuples; i++) {
		unsigned short baseindex=(i==0) ? 0 : endoffsets[i-1];
		unsigned short endindex=endoffsets[i];
		unsigned int oid=oidarray[i];
		int newbase;
		int machinenum;
		if (oid==0)
			continue;
		//Look up fields locally
		for(newbase=baseindex; newbase<endindex; newbase++) {
			if (!lookupObject(&oid, arryfields[newbase]))
				break;
			//Ended in a null pointer...
			if (oid==0)
				goto tuple;
		}
		//Entire prefetch is local
		if (newbase==endindex&&checkoid(oid)) {
			numLocal++;
			goto tuple;
		}
		//Add to remote requests
		machinenum=lhashSearch(oid);
		insertPile(machinenum, oid, endindex-newbase, &arryfields[newbase], &head);
tuple:
		;
	}

	// handle dynamic prefetching
	handleDynPrefetching(numLocal, ntuples, siteid);
	return head;
}
*/

int checkoid(unsigned int oid, int isLastOffset) {
  objheader_t *header;
  if ((header=mhashSearch(oid))!=NULL) {
    //Found on machine
    return 1;
  } else if ((header=prehashSearch(oid))!=NULL) {
    //if the last offset then prefetch object
    if((STATUS(header) & DIRTY) && isLastOffset) {
      return 0;
    }
    //Found in cache
    return 1;
  } else {
    return 0;
  }
}

#if 0
int checkoid(unsigned int oid) {
	objheader_t *header;
	if ((header=mhashSearch(oid))!=NULL) {
		//Found on machine
		return 1;
	} else if ((header=prehashSearch(oid))!=NULL) {
		//Found in cache
		return 1;
	} else {
		return 0;
	}
}
#endif

int lookupObject(unsigned int * oid, short offset, int *countInvalidObj) {
  objheader_t *header;
  if ((header=mhashSearch(*oid))!=NULL) {
    //Found on machine
    ;
  } else if ((header=prehashSearch(*oid))!=NULL) {
    //Found in cache
    if(STATUS(header) & DIRTY) {//Read an oid that is an old entry in the cache;
      //only once because later old entries may still cause unnecessary roundtrips during prefetching
      (*countInvalidObj)+=1;
      if(*countInvalidObj > 1) {
        return 0;
      }
    }
  } else {
    return 0;
  }

  if(TYPE(header) >= NUMCLASSES) {
    int elementsize = classsize[TYPE(header)];
    struct ArrayObject *ao = (struct ArrayObject *) (((char *)header) + sizeof(objheader_t));
    int length = ao->___length___;
    /* Check if array out of bounds */
    if(offset < 0 || offset >= length) {
      //if yes treat the object as found
      (*oid)=0;
      return 1;
    }
    (*oid) = *((unsigned int *)(((char *)ao) + sizeof(struct ArrayObject) + (elementsize*offset)));
    return 1;
  } else {
    (*oid) = *((unsigned int *)(((char *)header) + sizeof(objheader_t) + offset));
    return 1;
  }
}

#if 0
int lookupObject(unsigned int * oid, short offset) {
  objheader_t *header;
  if ((header=mhashSearch(*oid))!=NULL) {
    //Found on machine
    ;
  } else if ((header=prehashSearch(*oid))!=NULL) {
    //Found in cache
    ;
  } else {
    return 0;
  }

  if(TYPE(header) >= NUMCLASSES) {
    int elementsize = classsize[TYPE(header)];
    struct ArrayObject *ao = (struct ArrayObject *) (((char *)header) + sizeof(objheader_t));
    int length = ao->___length___;
    /* Check if array out of bounds */
    if(offset < 0 || offset >= length) {
      //if yes treat the object as found
      (*oid)=0;
      return 1;
    }
    (*oid) = *((unsigned int *)(((char *)ao) + sizeof(struct ArrayObject) + (elementsize*offset)));
    return 1;
  } else {
    (*oid) = *((unsigned int *)(((char *)header) + sizeof(objheader_t) + offset));
    return 1;
  }
}
#endif

/* This function is called by the thread calling transPrefetch */
void *transPrefetch(void *t) {
  while(1) {
    /* read from prefetch queue */
    void *node=gettail();
    /* Check if the tuples are found locally, if yes then reduce them further*/
    /* and group requests by remote machine ids by calling the makePreGroups() */
    int count=numavailable();
    prefetchpile_t *pilehead = foundLocal(node, count);

    if (pilehead!=NULL) {
      // Get sock from shared pool

      /* Send  Prefetch Request */
      prefetchpile_t *ptr = pilehead;
      while(ptr != NULL) {
        int sd = getSock2(transPrefetchSockPool, ptr->mid);
        sendPrefetchReq(ptr, sd);
        ptr = ptr->next;
      }

      /* Release socket */
      //	freeSock(transPrefetchSockPool, pilehead->mid, sd);

      /* Deallocated pilehead */
      mcdealloc(pilehead);
    }
    // Deallocate the prefetch queue pile node
    incmulttail(count);
  }
}

/* This function is called by the thread calling transPrefetch */
#if 0
void *transPrefetch(void *t) {
  while(1) {
    /* read from prefetch queue */
    void *node=gettail();
    /* Check if the tuples are found locally, if yes then reduce them further*/
    /* and group requests by remote machine ids by calling the makePreGroups() */
    prefetchpile_t *pilehead = foundLocal(node);

    if (pilehead!=NULL) {
      // Get sock from shared pool

      /* Send  Prefetch Request */
      prefetchpile_t *ptr = pilehead;
      while(ptr != NULL) {
	int sd = getSock2(transPrefetchSockPool, ptr->mid);
	sendPrefetchReq(ptr, sd);
	ptr = ptr->next;
      }

      /* Release socket */
      //	freeSock(transPrefetchSockPool, pilehead->mid, sd);

      /* Deallocated pilehead */
      mcdealloc(pilehead);
    }
    // Deallocate the prefetch queue pile node
    inctail();
  }
}
#endif

void sendPrefetchReqnew(prefetchpile_t *mcpilenode, int sd) {
  objpile_t *tmp;

  int size=sizeof(char)+sizeof(int);
  for(tmp=mcpilenode->objpiles; tmp!=NULL; tmp=tmp->next) {
    size += sizeof(int) + sizeof(unsigned int) + sizeof(unsigned int) + ((tmp->numoffset) * sizeof(short));
  }

  char buft[size];
  char *buf=buft;
  *buf=TRANS_PREFETCH;
  buf+=sizeof(char);

  for(tmp=mcpilenode->objpiles; tmp!=NULL; tmp=tmp->next) {
    int len = sizeof(int) + sizeof(unsigned int) + sizeof(unsigned int) + ((tmp->numoffset) * sizeof(short));
    *((int*)buf)=len;
    buf+=sizeof(int);
    *((unsigned int *)buf)=tmp->oid;
    buf+=sizeof(unsigned int);
    *((unsigned int *)(buf)) = myIpAddr;
    buf+=sizeof(unsigned int);
    memcpy(buf, tmp->offset, tmp->numoffset*sizeof(short));
    buf+=tmp->numoffset*sizeof(short);
  }
  *((int *)buf)=-1;
  send_data(sd, buft, size);
  return;
}

void sendPrefetchReq(prefetchpile_t *mcpilenode, int sd) {
  int len, endpair;
  char control;
  objpile_t *tmp;

  /* Send TRANS_PREFETCH control message */
  control = TRANS_PREFETCH;
  send_data(sd, &control, sizeof(char));

  /* Send Oids and offsets in pairs */
  tmp = mcpilenode->objpiles;
  while(tmp != NULL) {
    len = sizeof(int) + sizeof(unsigned int) + sizeof(unsigned int) + ((tmp->numoffset) * sizeof(short));
    char oidnoffset[len];
    char *buf=oidnoffset;
    *((int*)buf) = tmp->numoffset;
    buf+=sizeof(int);
    *((unsigned int *)buf) = tmp->oid;
#ifdef TRANSSTATS
    sendRemoteReq++;
#endif
    buf+=sizeof(unsigned int);
    *((unsigned int *)buf) = myIpAddr;
    buf += sizeof(unsigned int);
    memcpy(buf, tmp->offset, (tmp->numoffset)*sizeof(short));
    send_data(sd, oidnoffset, len);
    tmp = tmp->next;
  }

  /* Send a special char -1 to represent the end of sending oids + offset pair to remote machine */
  endpair = -1;
  send_data(sd, &endpair, sizeof(int));
  return;
}

int getPrefetchResponse(int sd) {
  int length = 0, size = 0;
  char control;
  unsigned int oid;
  void *modptr, *oldptr;

  recv_data((int)sd, &length, sizeof(int));
  size = length - sizeof(int);
  char recvbuffer[size];
#ifdef TRANSSTATS
  getResponse++;
#endif
  recv_data((int)sd, recvbuffer, size);
  control = *((char *) recvbuffer);
  if(control == OBJECT_FOUND) {
    oid = *((unsigned int *)(recvbuffer + sizeof(char)));
    size = size - (sizeof(char) + sizeof(unsigned int));
    pthread_mutex_lock(&prefetchcache_mutex);
    if ((modptr = prefetchobjstrAlloc(size)) == NULL) {
      printf("Error: objstrAlloc error for copying into prefetch cache %s, %d\n", __FILE__, __LINE__);
      pthread_mutex_unlock(&prefetchcache_mutex);
      return -1;
    }
    pthread_mutex_unlock(&prefetchcache_mutex);
    memcpy(modptr, recvbuffer + sizeof(char) + sizeof(unsigned int), size);
    STATUS(modptr)=0;

    /* Insert the oid and its address into the prefetch hash lookup table */
    /* Do a version comparison if the oid exists */
    if((oldptr = prehashSearch(oid)) != NULL) {
      /* If older version then update with new object ptr */
      if(((objheader_t *)oldptr)->version < ((objheader_t *)modptr)->version) {
	prehashInsert(oid, modptr);
      }
    } else { /* Else add the object ptr to hash table*/
      prehashInsert(oid, modptr);
    }
#if 0
    /* Lock the Prefetch Cache look up table*/
    pthread_mutex_lock(&pflookup.lock);
    /* Broadcast signal on prefetch cache condition variable */
    pthread_cond_broadcast(&pflookup.cond);
    /* Unlock the Prefetch Cache look up table*/
    pthread_mutex_unlock(&pflookup.lock);
#endif
  } else if(control == OBJECT_NOT_FOUND) {
    oid = *((unsigned int *)(recvbuffer + sizeof(char)));
    /* TODO: For each object not found query DHT for new location and retrieve the object */
    /* Throw an error */
    //printf("OBJECT %x NOT FOUND.... THIS SHOULD NOT HAPPEN...TERMINATE PROGRAM\n", oid);
    //    exit(-1);
  } else {
    printf("Error: in decoding the control value %d, %s, %d\n",control, __FILE__, __LINE__);
  }

  return 0;
}

unsigned short getObjType(unsigned int oid) {
  objheader_t *objheader;
  unsigned short numoffset[] ={0};
  short fieldoffset[] ={};
  int sd=0;

  if ((objheader = (objheader_t *) mhashSearch(oid)) == NULL) {
#ifdef CACHE
    if ((objheader = (objheader_t *) prehashSearch(oid)) == NULL) {
#endif

#ifdef RECOVERY
    unsigned int mid = lhashSearch(oid);
    unsigned int machineID;
    static int flipBit = 0;
    machineID = (flipBit)?(getPrimaryMachine(mid)):(getBackupMachine(mid));
    //int sd = getSock2(transReadSockPool, machineID);
    if((sd = getSockWithLock(transReadSockPool, machineID)) < 0) {
      printf("%s() Socket Create Error at %s, %d\n", __func__, __FILE__, __LINE__);
      return 0;
    }
#else
    unsigned int mid = lhashSearch(oid);
    //int sd = getSock2(transReadSockPool, mid);
    if((sd = getSockWithLock(transReadSockPool, mid)) < 0) {
      printf("%s() Socket Create Error at %s, %d\n", __func__, __FILE__, __LINE__);
      return 0;
    }
#endif
    char remotereadrequest[sizeof(char)+sizeof(unsigned int)];
    remotereadrequest[0] = READ_REQUEST;
    *((unsigned int *)(&remotereadrequest[1])) = oid;
    send_data(sd, remotereadrequest, sizeof(remotereadrequest));

    /* Read response from the Participant */
    char control;
    recv_data(sd, &control, sizeof(char));
    if (control==OBJECT_NOT_FOUND) {
      printf("Error: in %s() THIS SHOULD NOT HAPPEN.....EXIT PROGRAM\n", __func__);
      fflush(stdout);
      exit(-1);
    } else {
      /* Read object if found into local cache */
      int size;
      recv_data(sd, &size, sizeof(int));
#ifdef CACHE
      pthread_mutex_lock(&prefetchcache_mutex);
      if ((objheader = prefetchobjstrAlloc(size)) == NULL) {
	printf("Error: %s() objstrAlloc error for copying into prefetch cache %s, %d\n", __func__, __FILE__, __LINE__);
	pthread_exit(NULL);
      }
      pthread_mutex_unlock(&prefetchcache_mutex);
      recv_data(sd, objheader, size);
      prehashInsert(oid, objheader);
#ifdef RECOVERY
      freeSockWithLock(transReadSockPool, machineID, sd);
#else
      freeSockWithLock(transReadSockPool, mid, sd);
      return TYPE(objheader);
#endif
#else
      char *buffer;
      if((buffer = calloc(1, size)) == NULL) {
	printf("%s() Calloc Error %s at line %d\n", __func__, __FILE__, __LINE__);
	fflush(stdout);
#ifdef RECOVERY
    freeSockWithLock(transReadSockPool, machineID, sd);
#else
	freeSockWithLock(transReadSockPool, mid, sd);
#endif
	return 0;
      }
      recv_data(sd, buffer, size);
      objheader = (objheader_t *)buffer;
      unsigned short type = TYPE(objheader);
      free(buffer);
#ifdef RECOVERY
    freeSockWithLock(transReadSockPool, machineID, sd);
#else
	freeSockWithLock(transReadSockPool, mid, sd);
#endif

      return type;
#endif
    }
#ifdef CACHE
  }
#endif
  }
  return TYPE(objheader);
}

int startRemoteThread(unsigned int oid, unsigned int mid) {
  int sock;
  struct sockaddr_in remoteAddr;
  char msg[1 + sizeof(unsigned int)];
  int bytesSent;
  int status;

  if ((sock = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
    perror("startRemoteThread():socket()");
    return -1;
  }

  bzero(&remoteAddr, sizeof(remoteAddr));
  remoteAddr.sin_family = AF_INET;
  remoteAddr.sin_port = htons(LISTEN_PORT);
  remoteAddr.sin_addr.s_addr = htonl(mid);

  if (connect(sock, (struct sockaddr *)&remoteAddr, sizeof(remoteAddr)) < 0) {
    printf("startRemoteThread():error %d connecting to %s:%d\n", errno,
           inet_ntoa(remoteAddr.sin_addr), LISTEN_PORT);
    status = -1;
  } else
  {
    msg[0] = START_REMOTE_THREAD;
    *((unsigned int *) &msg[1]) = oid;

    send_data(sock, msg, 1 + sizeof(unsigned int));
  }

  close(sock);
  return status;
}

//TODO: when reusing oids, make sure they are not already in use!
static unsigned int id = 0xFFFFFFFF;
unsigned int getNewOID(void) {
  id += 2;
  if (id > oidMax || id < oidMin) {
    id = (oidMin | 1);
  }
  return id;
}

#ifdef RECOVERY
static unsigned int tid = 0xFFFFFFFF;
unsigned int getNewTransID(void) {
  tid++;
  if (tid > transIDMax || tid < transIDMin) {
    tid = (transIDMin | 1);
  }
  return tid;
}
#endif

int processConfigFile() {
  FILE *configFile;
  const int maxLineLength = 200;
  char lineBuffer[maxLineLength];
  char *token;
  const char *delimiters = " \t\n";
  char *commentBegin;
  unsigned int i;
  in_addr_t tmpAddr;

  configFile = fopen(CONFIG_FILENAME, "r");
  if (configFile == NULL) {
    printf("error opening %s:\n", CONFIG_FILENAME);
    perror("");
    return -1;
  }

  numHostsInSystem = 0;
  sizeOfHostArray = 8;
  hostIpAddrs = calloc(sizeOfHostArray, sizeof(unsigned int));
#ifdef RECOVERY	
	liveHosts = calloc(sizeOfHostArray, sizeof(unsigned int));
	locateObjHosts = calloc(sizeOfHostArray*2, sizeof(unsigned int));
#endif

	while(fgets(lineBuffer, maxLineLength, configFile) != NULL) {
		commentBegin = strchr(lineBuffer, '#');
		if (commentBegin != NULL)
			*commentBegin = '\0';
		token = strtok(lineBuffer, delimiters);
		while (token != NULL) {
			tmpAddr = inet_addr(token);
			if ((int)tmpAddr == -1) {
				printf("error in %s: bad token:%s\n", CONFIG_FILENAME, token);
				fclose(configFile);
				return -1;
			} else
				addHost(htonl(tmpAddr));
			token = strtok(NULL, delimiters);
		}
	}

	fclose(configFile);

  if (numHostsInSystem < 1) {
    printf("error in %s: no IP Adresses found\n", CONFIG_FILENAME);
    return -1;
  }
#ifdef MAC
  myIpAddr = getMyIpAddr("en1");
#else
  myIpAddr = getMyIpAddr("eth0");
#endif
  myIndexInHostArray = findHost(myIpAddr);
#ifdef RECOVERY
	liveHosts[myIndexInHostArray] = 1;
  currentEpoch = 1;

#ifdef RECOVERYSTATS
  numRecovery = 0;
  if((recoverStat = (recovery_stat_t*) calloc(numHostsInSystem, sizeof(recovery_stat_t))) == NULL) {
    printf("%s -> Calloc error!\n",__func__);
    exit(0);
  }
#endif

#endif  
	if (myIndexInHostArray == -1) {
    printf("error in %s: IP Address of eth0 not found\n", CONFIG_FILENAME);
    return -1;
  }
  oidsPerBlock = (0xFFFFFFFF / numHostsInSystem) + 1;
  oidMin = oidsPerBlock * myIndexInHostArray;
  if (myIndexInHostArray == numHostsInSystem - 1)
    oidMax = 0xFFFFFFFF;
  else
    oidMax = oidsPerBlock * (myIndexInHostArray + 1) - 1;

	transIDMin = oidMin;
	transIDMax = oidMax;

  waitThreadID = -1;
  waitThreadMid = -1;

  return 0;
}

#ifdef RECOVERY
unsigned int getDuplicatedPrimaryMachine(unsigned int mid) {
	int i;
	for(i = 0; i < numHostsInSystem; i++) {
		if(mid == locateObjHosts[(i*2)+1]) {
			return locateObjHosts[i*2];
		}
	}
	return -1;
}

unsigned int getPrimaryMachine(unsigned int mid) {
	unsigned int pmid;
	int pmidindex = 2*findHost(mid);

  if(pmidindex < 0)
    printf("What!!!\n");

	pthread_mutex_lock(&liveHosts_mutex);
	pmid = locateObjHosts[pmidindex];
	pthread_mutex_unlock(&liveHosts_mutex);
	return pmid;
}

unsigned int getBackupMachine(unsigned int mid) {
	unsigned int bmid;
	int bmidindex = 2*findHost(mid)+1;

  if(bmidindex < 0)
    printf("damn!!\n");

	pthread_mutex_lock(&liveHosts_mutex);
	bmid = locateObjHosts[bmidindex];
	pthread_mutex_unlock(&liveHosts_mutex);
	return bmid;
}

int getStatus(int mid) {
#ifdef DEBUG
  printf("%s -> mid = %d\n",__func__,mid);
  printf("%s -> host %s : %s\n",__func__,midtoIPString(hostIpAddrs[mid]),(liveHosts[mid] == 1)?"LIVE":"DEAD");
#endif
  return liveHosts[mid];
}
#endif

#ifdef RECOVERY
// updates the leader's liveHostArray and locateObj
unsigned int updateLiveHosts() {
#ifdef DEBUG
    printf("%s-> Entering updateLiveHosts\n", __func__);	
#endif
	// update everyone's list
	
  //foreach in hostipaddrs, ping -> update list of livemachines	
  //socket connection?

  int deadhost = -1;
	//liveHosts lock here
	int sd = 0, i, j, tmpNumLiveHosts = 0;
	for(i = 0; i < numHostsInSystem; i++) {
    if(i == myIndexInHostArray) 
		{
      liveHosts[i] = 1;
			tmpNumLiveHosts++;
			continue;
		}
		if((sd = getSockWithLock(transPrefetchSockPool, hostIpAddrs[i])) < 0) {
			usleep(1000);
    
	   	if(liveHosts[i]) {
        liveHosts[i] = 0;
        deadhost = i;
      }
      continue;
		}
      
    char liverequest;
		liverequest = RESPOND_LIVE;
	
		send_data(sd, &liverequest, sizeof(char));
      
		char response = 0;
		int timeout = recv_data(sd, &response, sizeof(char));
			
		//try to send msg
		//if timeout, dead host
		if(response == LIVE) {
    	liveHosts[i] = 1;
	  	tmpNumLiveHosts++;
		}
		else {
      if(liveHosts[i]) {
        liveHosts[i] = 0;
        deadhost = i;
      }
		}
    freeSockWithLock(transPrefetchSockPool,hostIpAddrs[i],sd);
	}
  
  numLiveHostsInSystem = tmpNumLiveHosts;
#ifdef DEBUG
	printf("numLiveHostsInSystem:%d\n", numLiveHostsInSystem);
#endif
	//have updated list of live machines
#ifdef DEBUG	
	printHostsStatus();
  printf("%s-> Exiting updateLiveHosts\n", __func__);	
#endif

  return deadhost;
}

int getNumLiveHostsInSystem() {
	int count = 0, i = 0;
	for(; i<numHostsInSystem; i++) {
		if(liveHosts[i])
			count++;
	}
	return count;
}

int updateLiveHostsCommit() {
#ifdef DEBUG      
  printf("%s -> Enter\n",__func__);
#endif
	int sd = 0, i;
	
	char updaterequest[sizeof(char)+sizeof(int)*numHostsInSystem+sizeof(unsigned int)*(numHostsInSystem*2)];

  updaterequest[0] = UPDATE_LIVE_HOSTS;
	for(i = 0; i < numHostsInSystem; i++) {
		*((int *)(&updaterequest[i*4+1])) = liveHosts[i];  // clean this up later
	}

	for(i = 0; i < numHostsInSystem*2; i++) {
		*((unsigned int *)(&updaterequest[i*4+(numHostsInSystem*4)+1])) = locateObjHosts[i];	//ditto
	}

	//for each machine send data
	for(i = 0; i < numHostsInSystem; i++) { 	// hard define num of retries
		if(hostIpAddrs[i] == myIpAddr) 
			continue;
		if(liveHosts[i] == 1) {
			if((sd = getSockWithLock(transPrefetchSockPool, hostIpAddrs[i])) < 0) {
	  		printf("%s -> socket create error, attempt %d\n",__func__, i);
				return -1;
			}
			send_data(sd, updaterequest, sizeof(updaterequest));
      freeSockWithLock(transPrefetchSockPool,hostIpAddrs[i],sd);
		}
	}

  pthread_mutex_lock(&recovery_mutex);
  currentBackupMachine = getBackupMachine(myIpAddr);
  pthread_mutex_unlock(&recovery_mutex);

#ifdef DEBUG
	printHostsStatus();
  printf("%s -> Finish\n",__func__);
#endif

	return 0;
}
#endif

#ifdef RECOVERY
void setLocateObjHosts() {
	int i,validIndex;

	//check num hosts even valid first
	for(i = 0,validIndex = numHostsInSystem;i < numHostsInSystem; i++,validIndex = numHostsInSystem) {
		while(liveHosts[(i+validIndex)%numHostsInSystem] == 0) {
			validIndex--;
		}
		locateObjHosts[i*2] = hostIpAddrs[(i+validIndex)%numHostsInSystem];
		
    validIndex = numHostsInSystem + 1;
		while(liveHosts[(i+validIndex)%numHostsInSystem] == 0) {
			validIndex++;
		}
		
    locateObjHosts[(i*2)+1] = hostIpAddrs[(i+validIndex)%numHostsInSystem];
	}
}

//debug function
void printHostsStatus() {
	int i;
	printf("%s-> *printing live machines and backups*\n", __func__);
	for(i = 0; i < numHostsInSystem; i++) {
		if(liveHosts[i]) {
			printf("%s-> [%s]: LIVE\n", __func__, midtoIPString(hostIpAddrs[i])); 
		}
		else {
			printf("%s-> [%s]: DEAD\n", __func__, midtoIPString(hostIpAddrs[i]));
		}
			printf("%s-> original:\t[%s]\n", __func__, midtoIPString(locateObjHosts[i*2]));
			printf("%s-> backup:\t[%s]\n", __func__, midtoIPString(locateObjHosts[i*2+1]));
	}
}

int allHostsLive() {
	int i;
	for(i = 0; i < numHostsInSystem; i++) {
		if(!liveHosts[i])
			return 0;
	}
	return 1;
}
#endif

#ifdef RECOVERY
// request all machines to check their objects
int duplicateLostObjects(unsigned int epoch_num,int *sdlist){
  int i;
  char response;
  unsigned int dupeSize = 0;
  unsigned int tempSize;
  printf("%s -> Enter\n",__func__);

  /* duplicateLostObject example
   * Before M24 die,
   * MID        21      24      26
   * Primary    21      24      26
   * Backup     26      21      24
   * After M24 die,
   * MID        21      26
   * Primary   21,24    26
   * Backup     26      21,24
   */

  if(inspectEpoch(epoch_num,__func__) < 0) return -1;

  response = REQUEST_DUPLICATE;

  for(i = 0 ; i < numHostsInSystem; i ++) {
    if(sdlist[i] == -1)
      continue;
    send_data(sdlist[i],&response,sizeof(char));
    send_data(sdlist[i],&epoch_num,sizeof(unsigned int));
  }
 
  for(i = 0 ; i < numHostsInSystem; i ++) {
    if(sdlist[i] == -1)
      continue;

    if(recv_data(sdlist[i],&response,sizeof(char)))    return -2;

    if(response != DUPLICATION_COMPLETE) return -2; 

    if(recv_data(sdlist[i],&tempSize,sizeof(unsigned int)) < 0) return -2;

    dupeSize += tempSize;
    
  }

#ifndef DEBUG
	printf("%s-> End\n", __func__);  
#endif
  return dupeSize; 
}
#endif
void addHost(unsigned int hostIp) {
  unsigned int *tmpArray;
  int *tmpliveHostsArray;	
	unsigned int *tmplocateObjHostsArray;

  if (findHost(hostIp) != -1)
    return;

  if (numHostsInSystem == sizeOfHostArray) {
    tmpArray = calloc(sizeOfHostArray * 2, sizeof(unsigned int));
    memcpy(tmpArray, hostIpAddrs, sizeof(unsigned int) * numHostsInSystem);
    free(hostIpAddrs);
    hostIpAddrs = tmpArray;

#ifdef RECOVERY
		tmpliveHostsArray = calloc(sizeOfHostArray * 2, sizeof(unsigned int));
		memcpy(tmpliveHostsArray, liveHosts, sizeof(unsigned int) * numHostsInSystem);
    free(liveHosts);
    liveHosts = tmpliveHostsArray;
		
		tmplocateObjHostsArray = calloc(sizeOfHostArray * 2 * 2, sizeof(unsigned int));
		memcpy(tmplocateObjHostsArray, locateObjHosts, sizeof(unsigned int) * numHostsInSystem);
    free(locateObjHosts);
    locateObjHosts = tmplocateObjHostsArray;
#endif
		sizeOfHostArray *= 2;
  }

  hostIpAddrs[numHostsInSystem] = hostIp;

#ifdef RECOVERY
  liveHosts[numHostsInSystem] = 0;
  locateObjHosts[numHostsInSystem*2] = hostIp;
#endif

	numHostsInSystem++;
  return;
}

int findHost(unsigned int hostIp) {
  int i;
  for (i = 0; i < numHostsInSystem; i++)
    if (hostIpAddrs[i] == hostIp)
      return i;

  //not found
  return -1;
}

/* This function sends notification request per thread waiting on object(s) whose version
 * changes */
#ifdef RECOVERY
int reqNotify(unsigned int *oidarry, unsigned short *versionarry, unsigned int numoid, int waitmid) {
#else
int reqNotify(unsigned int *oidarry, unsigned short *versionarry, unsigned int numoid) {
#endif
  int psock,i;
  objheader_t *objheader;
  struct sockaddr_in premoteAddr;
  char msg[1 + numoid * (sizeof(unsigned short) + sizeof(unsigned int)) +  3 * sizeof(unsigned int)];
  char *ptr;
  int status, size;
  unsigned short version;
  unsigned int oid,mid;
  static unsigned int threadid = 0;
  pthread_mutex_t threadnotify = PTHREAD_MUTEX_INITIALIZER; //Lock and condition var for threadjoin and notification
  pthread_cond_t threadcond = PTHREAD_COND_INITIALIZER;
  notifydata_t *ndata;

#ifdef RECOVERY
  int bsock;
  struct sockaddr_in bremoteAddr;
#endif

  oid = oidarry[0];

  if((mid = lhashSearch(oid)) == 0) {
    printf("Error: %s() No such machine found for oid =%x\n",__func__, oid);
    return;
  }
#ifdef RECOVERY
  int pmid = getPrimaryMachine(mid);
  int bmid = getBackupMachine(mid);
#else
  int pmid = mid;
#endif

#ifdef RECOVERY
  if ((psock = socket(AF_INET, SOCK_STREAM, 0)) < 0 ||
      (bsock = socket(AF_INET, SOCK_STREAM, 0)) < 0 ) {
#else
  if ((psock = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
#endif
    perror("reqNotify():socket()");
    return -1;
  }

  /* for primary machine */
  bzero(&premoteAddr, sizeof(premoteAddr));
  premoteAddr.sin_family = AF_INET;
  premoteAddr.sin_port = htons(LISTEN_PORT);
  premoteAddr.sin_addr.s_addr = htonl(pmid);

#ifdef RECOVERY

  if(numLiveHostsInSystem > 1) {
    /* for backup machine */
    bzero(&bremoteAddr, sizeof(bremoteAddr));
    bremoteAddr.sin_family = AF_INET;
    bremoteAddr.sin_port = htons(LISTEN_PORT);
    bremoteAddr.sin_addr.s_addr = htonl(bmid);
  }
#endif
  /* Generate unique threadid */
  threadid++;
  
  /* Save threadid, numoid, oidarray, versionarray, pthread_cond_variable for later processing */
  if((ndata = calloc(1, sizeof(notifydata_t))) == NULL) {
    printf("Calloc Error %s, %d\n", __FILE__, __LINE__);
    return -1;
  }
  ndata->numoid = numoid;
//  printf("%s -> ndata = %d numoid = %d\n",__func__,ndata,numoid);
  ndata->threadid = threadid;
  ndata->oidarry = oidarry;
  ndata->versionarry = versionarry;
  ndata->threadcond = threadcond;
  ndata->threadnotify = threadnotify;
  if((status = notifyhashInsert(threadid, ndata)) != 0) {
    printf("reqNotify(): Insert into notify hash table not successful %s, %d\n", __FILE__, __LINE__);
    free(ndata);
    return -1;
  }

  /* Send  number of oids, oidarry, version array, machine id and threadid */
#ifdef RECOVERY
  // need to handle the single machine case
  int first = 0;
  int second = 0;

  first = connect(psock, (struct sockaddr *)&premoteAddr, sizeof(premoteAddr));
  // if it is running in single machine, it doesn't need to connect to backup machine
  if(numLiveHostsInSystem > 1)
    second = connect(bsock, (struct sockaddr *)&premoteAddr, sizeof(bremoteAddr));

  //  primary         backup
  if ((first < 0) || (second < 0 )) {
#else
  if ((connect(psock, (struct sockaddr *)&premoteAddr, sizeof(premoteAddr))< 0)) {
#endif
    printf("reqNotify():error %d connecting to %s:%d\n", errno,
    inet_ntoa(premoteAddr.sin_addr), LISTEN_PORT);
    free(ndata);
    return -1;
  } else {

#ifdef DEBUG
    printf("%s -> Pmid = %s\n",__func__,midtoIPString(pmid));
#ifdef RECOVERY
    printf("%s -> Bmid = %s\n",__func__,midtoIPString(bmid));
#endif
#endif

    msg[0] = THREAD_NOTIFY_REQUEST;

    *((unsigned int *)(&msg[1])) = numoid;
    /* Send array of oids  */
    size = sizeof(unsigned int);

    for(i = 0;i < numoid; i++) {
      oid = oidarry[i];
#ifdef DEBUG
      printf("%s -> oid[%d] = %d\n",__func__,i,oidarry[i]);
#endif
      *((unsigned int *)(&msg[1] + size)) = oid;
      size += sizeof(unsigned int);
    }

    /* Send array of version  */
    for(i = 0;i < numoid; i++) {
      version = versionarry[i];
      *((unsigned short *)(&msg[1] + size)) = version;
      size += sizeof(unsigned short);
    }

    *((unsigned int *)(&msg[1] + size)) = myIpAddr; 
    size += sizeof(unsigned int);
    *((unsigned int *)(&msg[1] + size)) = threadid;
#ifdef RECOVERY
    waitThreadMid = waitmid;
    waitThreadID = threadid;
#ifdef DEBUG
    printf("%s -> This Thread is waiting for %s\n",__func__,midtoIPString(waitmid));
#endif
#endif

    pthread_mutex_lock(&(ndata->threadnotify));
    size = 1 + numoid * (sizeof(unsigned int) + sizeof(unsigned short)) + 3 * sizeof(unsigned int);
    send_data(psock, msg, size);
#ifdef RECOVERY
    if(numLiveHostsInSystem > 1)
      send_data(bsock, msg, size);
#endif
    pthread_cond_wait(&(ndata->threadcond), &(ndata->threadnotify));
    pthread_mutex_unlock(&(ndata->threadnotify));
  }

  pthread_cond_destroy(&threadcond);
  pthread_mutex_destroy(&threadnotify);
  free(ndata);
  close(psock);

#ifdef RECOVERY
  close(bsock);
#endif

  return status;
}

void threadNotify(unsigned int oid, unsigned short version, unsigned int tid) {
  notifydata_t *ndata;
  int objIsFound = 0, index = -1;
  unsigned int i;
  void *ptr;
#ifdef DEBUG
  printf("%s -> oid = %d   vesion = %d    tid = %d\n",__func__,oid,version,tid);
#endif

  //Look up the tid and call the corresponding pthread_cond_signal
  if((ndata = notifyhashSearch(tid)) == NULL) {
    printf("threadnotify(): No such threadid is present %s, %d\n", __FILE__, __LINE__);
    return;
  } else  {
//    printf("%s -> Get to this point1\n",__func__);
//    printf("%s -> ndata : %d\n",__func__,ndata);
//    printf("%s -> ndata->numoid : %d\n",__func__,ndata->numoid);
    for(i = 0; i < (ndata->numoid); i++) {
      if(ndata->oidarry[i] == oid) {
        objIsFound = 1;
        index = i;
      }
    }
//    printf("%s -> Get to this point2\n",__func__);
    if(objIsFound == 0) {
      printf("threadNotify(): Oid not found %s, %d\n", __FILE__, __LINE__);
      return;
    } 
    else {
 //   printf("%s -> Get to this point3\n",__func__);
      if(version <= ndata->versionarry[index]) {
	      printf("threadNotify(): New version %d has not changed since last version for oid = %d, %s, %d\n", version, oid, __FILE__, __LINE__);
    	return;
      } else {
#ifdef CACHE
	/* Clear from prefetch cache and free thread related data structure */
      	if((ptr = prehashSearch(oid)) != NULL) {
      	  prehashRemove(oid);
      	}
#endif

//    printf("%s -> Get to this point4\n",__func__);
    	pthread_mutex_lock(&(ndata->threadnotify));
    	pthread_cond_signal(&(ndata->threadcond));
    	pthread_mutex_unlock(&(ndata->threadnotify));
      }
    }
  }

#ifdef DEBUG
  printf("%s -> Finished\n",__func__);
#endif
  return;
}

int notifyAll(threadlist_t **head, unsigned int oid, unsigned int version) {
  threadlist_t *ptr;
  unsigned int mid;
  struct sockaddr_in remoteAddr;
  char msg[1 + sizeof(unsigned short) + 2*sizeof(unsigned int)];
  int sock, status, size, bytesSent;
#ifdef DEBUG
  printf("%s -> Entering \n",__func__);
#endif

  while(*head != NULL) {
    ptr = *head;
    mid = ptr->mid;

    //create a socket connection to that machine
    if ((sock = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
      perror("notifyAll():socket()");
      return -1;
    }

    bzero(&remoteAddr, sizeof(remoteAddr));
    remoteAddr.sin_family = AF_INET;
    remoteAddr.sin_port = htons(LISTEN_PORT);
    remoteAddr.sin_addr.s_addr = htonl(mid);
    //send Thread Notify response and threadid to that machine
    if (connect(sock, (struct sockaddr *)&remoteAddr, sizeof(remoteAddr)) < 0) {
      printf("notifyAll():error %d connecting to %s:%d\n", errno,
             inet_ntoa(remoteAddr.sin_addr), LISTEN_PORT);
      fflush(stdout);
      status = -1;
    } else {
      bzero(msg, (1+sizeof(unsigned short) + 2*sizeof(unsigned int)));
//      printf("%s -> Calling THREAD_NOTIFY_RESPONSE\n",__func__);
      msg[0] = THREAD_NOTIFY_RESPONSE;
      *((unsigned int *)&msg[1]) = oid;
      size = sizeof(unsigned int);
      *((unsigned short *)(&msg[1]+ size)) = version;
      size+= sizeof(unsigned short);
      *((unsigned int *)(&msg[1]+ size)) = ptr->threadid;

      size = 1 + 2*sizeof(unsigned int) + sizeof(unsigned short);
      send_data(sock, msg, size);
    }
    //close socket
    close(sock);
    // Update head
    *head = ptr->next;
    free(ptr);
  }
  return status;
}


void transAbort() {
#ifdef ABORTREADERS
  removetransactionhash();
#endif
  objstrDelete(t_cache);
  t_chashDelete();
}

/* This function inserts necessary information into
 * a machine pile data structure */
plistnode_t *pInsert(plistnode_t *pile, objheader_t *headeraddr, unsigned int mid, int num_objs) {
  plistnode_t *ptr, *tmp;
  int found = 0, offset = 0;
  tmp = pile;

  //Add oid into a machine that is already present in the pile linked list structure
  while(tmp != NULL) {
    if (tmp->mid == mid) {
      int tmpsize;

			if (STATUS(headeraddr) & NEW) {
				tmp->oidcreated[tmp->numcreated] = OID(headeraddr);
				tmp->numcreated++;
				GETSIZE(tmpsize, headeraddr);
				tmp->sum_bytes += sizeof(objheader_t) + tmpsize;
			} else if (STATUS(headeraddr) & DIRTY) {
				tmp->oidmod[tmp->nummod] = OID(headeraddr);
				tmp->nummod++;
				GETSIZE(tmpsize, headeraddr);
				tmp->sum_bytes += sizeof(objheader_t) + tmpsize;
				/*	midtoIP(tmp->mid, ip);
					printf("pp; Redo? pile->mid: %s, oid: %d, header version: %d\n", ip, OID(headeraddr), headeraddr->version);*/
			} else {
				offset = (sizeof(unsigned int) + sizeof(short)) * tmp->numread;
				*((unsigned int *)(((char *)tmp->objread) + offset))=OID(headeraddr);
				offset += sizeof(unsigned int);
				*((short *)(((char *)tmp->objread) + offset)) = headeraddr->version;
				tmp->numread++;
      }
      found = 1;
      break;
    }
    tmp = tmp->next;
  }
  //Add oid for any new machine
  if (!found) {
    int tmpsize;
    if((ptr = pCreate(num_objs)) == NULL) {
      printf("pCreate Error\n");
      return NULL;
    }

    ptr->mid = mid;
    if (STATUS(headeraddr) & NEW) {
      ptr->oidcreated[ptr->numcreated] = OID(headeraddr);
      ptr->numcreated++;
      GETSIZE(tmpsize, headeraddr);
      ptr->sum_bytes += sizeof(objheader_t) + tmpsize;
	  } else if (STATUS(headeraddr) & DIRTY) {
      ptr->oidmod[ptr->nummod] = OID(headeraddr);
      ptr->nummod++;
      GETSIZE(tmpsize, headeraddr);
      ptr->sum_bytes += sizeof(objheader_t) + tmpsize;
    } else {
      *((unsigned int *)ptr->objread)=OID(headeraddr);
      offset = sizeof(unsigned int);
      *((short *)(((char *)ptr->objread) + offset)) = headeraddr->version;
      ptr->numread++;
    }

    ptr->next = pile;
    pile = ptr;
  }

  /* Clear Flags */
  STATUS(headeraddr) = 0;

  return pile;
}

// relocate the position of myIp pile to end of list
plistnode_t *sortPiles(plistnode_t *pileptr) {
	plistnode_t *ptr, *tail;
	tail = pileptr;
  ptr = NULL;
	/* Get tail pointer and myIp pile ptr */
  if(pileptr == NULL)
    return pileptr;

	while(tail->next != NULL) {
    if(tail->mid == myIpAddr)
      ptr = tail;
		tail = tail->next;    
	}

  // if ptr is null, then myIp pile is already at tail
  if(ptr != NULL) {
  	/* Arrange local machine processing at the end of the pile list */
    tail->next = pileptr;
    pileptr = ptr->next;
    ptr->next = NULL;
    return pileptr;
  }

  /* get too this point iff myIpAddr pile is at tail */
  return pileptr;
}

#ifdef RECOVERY
void clearDeadThreadsNotification() 
{
// clear all the threadnotify request first
  
  if(waitThreadID != -1) {
    int waitThreadIndex = findHost(waitThreadMid);
    int i;
    notifydata_t *ndata;

    if(liveHosts[waitThreadIndex] == 0) // the thread waiting for is dead
    {
      if((ndata = (notifydata_t*)notifyhashSearch(waitThreadID)) == NULL) {
        return;
      }
   
      for(i =0 ; i < ndata->numoid; i++) {
        clearNotifyList(ndata->oidarry[i]);  // clear thread object's notifylist
      }

      pthread_mutex_lock(&(ndata->threadnotify));
      pthread_cond_signal(&(ndata->threadcond));
      pthread_mutex_unlock(&(ndata->threadnotify));

      waitThreadMid = -1;
      waitThreadID = -1;
    }
  }

}

/* request the primary and the backup machines to clear
   thread obj's notify list */
void reqClearNotifyList(unsigned int oid)
{
  int psock,bsock,i;
  int mid,pmid,bmid;
  objheader_t *objheader;
  struct sockaddr_in premoteAddr, bremoteAddr;
  char msg[1 + sizeof(unsigned int)];

  if((mid = lhashSearch(oid)) == 0) {
    printf("%s -> No such machine found for oid %x\n",__func__,oid);
    return;
  }

  pmid = getPrimaryMachine(mid);
  bmid = getBackupMachine(mid);

  if((psock = socket(AF_INET, SOCK_STREAM, 0)) < 0 ||
     (bsock = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
        perror("clearNotifyList() : socket()");
    return ;
  }

  /* for primary machine */
  bzero(&premoteAddr, sizeof(premoteAddr));
  premoteAddr.sin_family = AF_INET;
  premoteAddr.sin_port = htons(LISTEN_PORT);
  premoteAddr.sin_addr.s_addr = htonl(pmid);

  /* for backup machine */
  bzero(&bremoteAddr, sizeof(bremoteAddr));
  bremoteAddr.sin_family = AF_INET;
  bremoteAddr.sin_port = htons(LISTEN_PORT);
  bremoteAddr.sin_addr.s_addr = htonl(bmid);

  /* send message to both the primary and the backup */
  if((connect(psock, (struct sockaddr *)&premoteAddr, sizeof(premoteAddr)) < 0) ||
     (connect(bsock, (struct sockaddr *)&bremoteAddr, sizeof(bremoteAddr)) < 0)) {
      printf("%s -> error in connecting\n",__func__);
      return;
  }
  else {
//    printf("%s -> Pmid = %s\n",__func__,midtoIPString(pmid));
//    printf("%s -> Bmid = %s\n",__func__,midtoIPString(bmid));
    
    msg[0] = CLEAR_NOTIFY_LIST;
    *((unsigned int *)(&msg[1])) = oid;
    
    send_data(psock, &msg, sizeof(char) + sizeof(unsigned int));
    send_data(bsock, &msg, sizeof(char) + sizeof(unsigned int)); 
  }
  
  close(psock);
  close(bsock);
  
}
   

int checkiftheMachineDead(unsigned int mid) {
  int mIndex = findHost(mid);
  return getStatus(mIndex);
}
#endif

void printRecoveryStat() {
#ifdef RECOVERYSTATS
  printf("\n***** Recovery Stats *****\n");
  printf("numRecovery = %d\n",numRecovery);
  int i;
  for(i=0; i < numRecovery;i++) {
//    printf("Dead Machine = %s\n",midtoIPString(recoverStat[i].deadMachine));
    printf("Recovery Time(ms) = %ld\n",recoverStat[i].elapsedTime);
    printf("Recovery Byte     = %u\n",recoverStat[i].recoveredData);
  }
  printf("**************************\n\n");
  fflush(stdout);
#else
  printf("No stat\n");
#endif
}
