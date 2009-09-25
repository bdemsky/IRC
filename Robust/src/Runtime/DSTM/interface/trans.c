#include "dstm.h"
#include "ip.h"
#include "machinepile.h"
#include "mlookup.h"
#include "llookup.h"
#include "plookup.h"
#include "prelookup.h"
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

#define NUM_THREADS 1
#define CONFIG_FILENAME "dstm.conf"

/* Thread transaction variables */

__thread objstr_t *t_cache;
__thread struct ___Object___ *revertlist;
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
extern prehashtable_t pflookup; //Global Prefetch cache's lookup table
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
int nRemoteSend = 0;
int nSoftAbort = 0;
int bytesSent = 0;
int bytesRecv = 0;
int totalObjSize = 0;
int sendRemoteReq = 0;
int getResponse = 0;

void printhex(unsigned char *, int);
plistnode_t *createPiles();
plistnode_t *sortPiles(plistnode_t *pileptr);

#ifdef LOGEVENTS
char bigarray[16*1024*1024];
int bigindex=0;
#define LOGEVENT(x) { \
    int tmp=bigindex++;				\
    bigarray[tmp]=x;				\
  }
#else
#define LOGEVENT(x)
#endif

/*******************************
* Send and Recv function calls
*******************************/
void send_data(int fd, void *buf, int buflen) {
  char *buffer = (char *)(buf);
  int size = buflen;
  int numbytes;
  while (size > 0) {
    numbytes = send(fd, buffer, size, MSG_NOSIGNAL);
    bytesSent = bytesSent + numbytes;
    if (numbytes == -1) {
      perror("send");
      exit(0);
    }
    buffer += numbytes;
    size -= numbytes;
  }
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
    buflen-=numbytes;
    readbuffer->head+=numbytes;
    maxbuf-=numbytes;
  }
  memcpy(buf,readbuffer->buf,obufflen);
  readbuffer->tail=obufflen;
}

int recv_data_errorcode_buf(int fd, struct readstruct * readbuffer, void *buffer, int buflen) {
  char *buf=(char *)buffer;
  //now tail<=head
  int numbytes=readbuffer->head-readbuffer->tail;
  if (numbytes>buflen)
    numbytes=buflen;
  if (numbytes>0) {
    memcpy(buf, &readbuffer->buf[readbuffer->tail], numbytes);
    readbuffer->tail+=numbytes;
    buflen-=numbytes;
    buf+=numbytes;
  }
  if (buflen==0)
    return 1;

  if (buflen>=MAXBUF) {
    return recv_data_errorcode(fd, buf, buflen);
  }

  int maxbuf=MAXBUF;
  int obufflen=buflen;
  readbuffer->head=0;
  
  while (buflen > 0) {
    int numbytes = recvw(fd, &readbuffer->buf[readbuffer->head], maxbuf, 0);
    if (numbytes ==0) {
      return 0;
    }
    if (numbytes==-1) {
      perror("recvbuf");
      return -1;
    }
    buflen-=numbytes;
    readbuffer->head+=numbytes;
    maxbuf-=numbytes;
  }
  memcpy(buf,readbuffer->buf,obufflen);
  readbuffer->tail=obufflen;
  return 1;
}


void recv_data(int fd, void *buf, int buflen) {
  char *buffer = (char *)(buf);
  int size = buflen;
  int numbytes;
  while (size > 0) {
    numbytes = recvw(fd, buffer, size, 0);
    bytesRecv = bytesRecv + numbytes;
    if (numbytes == -1) {
      perror("recv");
      exit(0);
    }
    buffer += numbytes;
    size -= numbytes;
  }
}

int recv_data_errorcode(int fd, void *buf, int buflen) {
  char *buffer = (char *)(buf);
  int size = buflen;
  int numbytes;
  while (size > 0) {
    numbytes = recvw(fd, buffer, size, 0);
    if (numbytes==0)
      return 0;
    if (numbytes == -1) {
      perror("recv");
      return -1;
    }
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

//#define INLINEPREFETCH
#define PREFTHRESHOLD 4

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

  if (node==NULL && numpref!=0 || numpref==PREFTHRESHOLD) {
    node=gettail();
    prefetchpile_t *pilehead = foundLocal(node,numpref);
    if (pilehead!=NULL) {
      // Get sock from shared pool
      
      /* Send  Prefetch Request */
      prefetchpile_t *ptr = pilehead;
      while(ptr != NULL) {
	int sd = getSock2(transPrefetchSockPool, ptr->mid);
	sendPrefetchReq(ptr, sd);
	ptr = ptr->next;
      }
      
      mcdealloc(pilehead);
      resetqueue();
    }
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

/* This function initializes things required in the transaction start*/
void transStart() {
  t_cache = objstrCreate(1048576);
  t_chashCreate(CHASH_SIZE, CLOADFACTOR);
  revertlist=NULL;
#ifdef ABORTREADERS
  t_abort=0;
#endif
}

// Search for an address for a given oid                                                                               
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
      void *oldptr;
      if((oldptr = prehashSearch(oid)) != NULL) {
        prehashRemove(oid);
        prehashInsert(oid, headerObj);
      } else {
        prehashInsert(oid, headerObj);
      }
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
#ifdef TRANSSTATS
      LOGEVENT('P')
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

      LOGEVENT('R');
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
      void *oldptr;
      if((oldptr = prehashSearch(oid)) != NULL) {
        prehashRemove(oid);
        prehashInsert(oid, headerObj);
      } else {
        prehashInsert(oid, headerObj);
      }
#endif

      return &objcopy[1];
#else
      return objcopy;
#endif
    }
  }
}

/* This function creates objects in the transaction record */
objheader_t *transCreateObj(unsigned int size) {
  objheader_t *tmp = (objheader_t *) objstrAlloc(&t_cache, (sizeof(objheader_t) + size));
  OID(tmp) = getNewOID();
  tmp->version = 1;
  tmp->rcount = 1;
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
  int i;
  plistnode_t *pile = NULL;
  unsigned int machinenum;
  objheader_t *headeraddr;
  chashlistnode_t * ptr = c_table;
  /* Represents number of bins in the chash table */
  unsigned int size = c_size;

  for(i = 0; i < size ; i++) {
    chashlistnode_t * curr = &ptr[i];
    /* Inner loop to traverse the linked list of the cache lookupTable */
    while(curr != NULL) {
      //if the first bin in hash table is empty
      if(curr->key == 0)
	break;
      headeraddr=(objheader_t *) curr->val;

      //Get machine location for object id (and whether local or not)
      if (STATUS(headeraddr) & NEW || (mhashSearch(curr->key) != NULL)) {
	machinenum = myIpAddr;
      } else if ((machinenum = lhashSearch(curr->key)) == 0) {
	printf("Error: No such machine %s, %d\n", __FILE__, __LINE__);
	return NULL;
      }

      //Make machine groups
      pile = pInsert(pile, headeraddr, machinenum, c_numelements);
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
  objheader_t *headeraddr;
  struct chashentry * ptr = c_table;
  /* Represents number of bins in the chash table */
  unsigned int size = c_size;

  for(i = 0; i < size ; i++) {
    struct chashentry * curr = & ptr[i];
    /* Inner loop to traverse the linked list of the cache lookupTable */
    //if the first bin in hash table is empty
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

#ifdef LOGEVENTS
  int iii;
  for(iii=0;iii<bigindex;iii++) {
    printf("%c", bigarray[iii]);
  }
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
    return 1;
  }
#endif


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
    int loopcount;
    for(loopcount = 0 ; loopcount < pilecount; loopcount++)
      socklist[loopcount] = 0;
    char getReplyCtrl[pilecount];
    for(loopcount = 0 ; loopcount < pilecount; loopcount++)
      getReplyCtrl[loopcount] = 0;

    /* Process each machine pile */
    int sockindex = 0;
    trans_req_data_t *tosend;
    tosend = calloc(pilecount, sizeof(trans_req_data_t));
    while(pile != NULL) {
      tosend[sockindex].f.control = TRANS_REQUEST;
      tosend[sockindex].f.mcount = pilecount;
      tosend[sockindex].f.numread = pile->numread;
      tosend[sockindex].f.nummod = pile->nummod;
      tosend[sockindex].f.numcreated = pile->numcreated;
      tosend[sockindex].f.sum_bytes = pile->sum_bytes;
      tosend[sockindex].listmid = listmid;
      tosend[sockindex].objread = pile->objread;
      tosend[sockindex].oidmod = pile->oidmod;
      tosend[sockindex].oidcreated = pile->oidcreated;
      int sd = 0;
      if(pile->mid != myIpAddr) {
	if((sd = getSock2WithLock(transRequestSockPool, pile->mid)) < 0) {
	  printf("transRequest(): socket create error\n");
	  free(listmid);
	  free(tosend);
	  return 1;
	}
	socklist[sockindex] = sd;
	/* Send bytes of data with TRANS_REQUEST control message */
	send_data(sd, &(tosend[sockindex].f), sizeof(fixed_data_t));

	/* Send list of machines involved in the transaction */
	{
	  int size=sizeof(unsigned int)*(tosend[sockindex].f.mcount);
	  send_data(sd, tosend[sockindex].listmid, size);
	}

	/* Send oids and version number tuples for objects that are read */
	{
	  int size=(sizeof(unsigned int)+sizeof(unsigned short))*(tosend[sockindex].f.numread);
	  send_data(sd, tosend[sockindex].objread, size);
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
	free(modptr);
      } else { //handle request locally
	handleLocalReq(&tosend[sockindex], &transinfo, &getReplyCtrl[sockindex]);
      }
      sockindex++;
      pile = pile->next;
    } //end of pile processing
      /* Recv Ctrl msgs from all machines */
    int i;
    for(i = 0; i < pilecount; i++) {
      int sd = socklist[i];
      if(sd != 0) {
	char control;
	recv_data(sd, &control, sizeof(char));
	//Update common data structure with new ctrl msg
	getReplyCtrl[i] = control;
	/* Recv Objects if participant sends TRANS_DISAGREE */
#ifdef CACHE
	if(control == TRANS_DISAGREE) {
	  int length;
	  recv_data(sd, &length, sizeof(int));
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
	  recv_data(sd, newAddr, length);
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
	    void *oldptr;
	    if((oldptr = prehashSearch(oidToPrefetch)) != NULL) {
	      prehashRemove(oidToPrefetch);
	      prehashInsert(oidToPrefetch, header);
	    } else {
	      prehashInsert(oidToPrefetch, header);
	    }
	    length = length - size;
	    offset += size;
	  }
	} //end of receiving objs
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

    /* Send responses to all machines */
    for(i = 0; i < pilecount; i++) {
      int sd = socklist[i];
      if(sd != 0) {
#ifdef CACHE
	if(finalResponse == TRANS_COMMIT) {
	  int retval;
	  /* Update prefetch cache */
	  if((retval = updatePrefetchCache(&(tosend[i]))) != 0) {
	    printf("Error: %s() in updating prefetch cache %s, %d\n", __func__, __FILE__, __LINE__);
	    free(tosend);
	    free(listmid);
	    return 1;
	  }


	  /* Invalidate objects in other machine cache */
	  if(tosend[i].f.nummod > 0) {
	    if((retval = invalidateObj(&(tosend[i]))) != 0) {
	      printf("Error: %s() in invalidating Objects %s, %d\n", __func__, __FILE__, __LINE__);
	      free(tosend);
	      free(listmid);
	      return 1;
	    }
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
	send_data(sd, &finalResponse, sizeof(char));
      } else {
	/* Complete local processing */
	doLocalProcess(finalResponse, &(tosend[i]), &transinfo);
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
    /* Retry trans commit procedure during soft_abort case */
  } while (treplyretry);

  if(finalResponse == TRANS_ABORT) {
    //printf("Aborting trans\n");
#ifdef TRANSSTATS
    numTransAbort++;
#endif
    /* Free Resources */
    objstrDelete(t_cache);
    t_chashDelete();
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
  return 0;
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

void doLocalProcess(char finalResponse, trans_req_data_t *tdata, trans_commit_data_t *transinfo) {
  if(finalResponse == TRANS_ABORT) {
    if(transAbortProcess(transinfo) != 0) {
      printf("Error in transAbortProcess() %s,%d\n", __FILE__, __LINE__);
      fflush(stdout);
      return;
    }
  } else if(finalResponse == TRANS_COMMIT) {
#ifdef CACHE
    /* Invalidate objects in other machine cache */
    if(tdata->f.nummod > 0) {
      int retval;
      if((retval = invalidateObj(tdata)) != 0) {
	printf("Error: %s() in invalidating Objects %s, %d\n", __func__, __FILE__, __LINE__);
	return;
      }
    }
#endif
    if(transComProcess(tdata, transinfo) != 0) {
      printf("Error in transComProcess() %s,%d\n", __FILE__, __LINE__);
      fflush(stdout);
      return;
    }
  } else {
    printf("ERROR...No Decision\n");
  }

  /* Free memory */
  if (transinfo->objlocked != NULL) {
    free(transinfo->objlocked);
  }
  if (transinfo->objnotfound != NULL) {
    free(transinfo->objnotfound);
  }
}

/* This function decides the reponse that needs to be sent to
 * all Participant machines after the TRANS_REQUEST protocol */
char decideResponse(char *getReplyCtrl, char *treplyretry, int pilecount) {
  int i, transagree = 0, transdisagree = 0, transsoftabort = 0; /* Counters to formulate decision of what
								   message to send */
  for (i = 0 ; i < pilecount; i++) {
    char control;
    control = getReplyCtrl[i];
    switch(control) {
    default:
      printf("Participant sent unknown message in %s, %d\n", __FILE__, __LINE__);

      /* treat as disagree, pass thru */
    case TRANS_DISAGREE:
      transdisagree++;
      break;

    case TRANS_AGREE:
      transagree++;
      break;

    case TRANS_SOFT_ABORT:
      transsoftabort++;
      break;
    }
  }

  if(transdisagree > 0) {
    /* Send Abort */
    *treplyretry = 0;
    return TRANS_ABORT;
#ifdef CACHE
    /* clear objects from prefetch cache */
    cleanPCache();
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
  int size, val;
  struct sockaddr_in serv_addr;
  char machineip[16];
  char control;
  objheader_t *h;
  void *objcopy = NULL;

  int sd = getSock2(transReadSockPool, mnum);
  char readrequest[sizeof(char)+sizeof(unsigned int)];
  readrequest[0] = READ_REQUEST;
  *((unsigned int *)(&readrequest[1])) = oid;
  send_data(sd, readrequest, sizeof(readrequest));

  /* Read response from the Participant */
  recv_data(sd, &control, sizeof(char));

  if (control==OBJECT_NOT_FOUND) {
    objcopy = NULL;
  } else {
    /* Read object if found into local cache */
    recv_data(sd, &size, sizeof(int));
    objcopy = objstrAlloc(&t_cache, size);
    recv_data(sd, objcopy, size);
    STATUS(objcopy)=0;
    /* Insert into cache's lookup table */
    t_chashInsert(oid, objcopy);
#ifdef TRANSSTATS
    totalObjSize += size;
#endif
  }

  return objcopy;
}

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
	//printf("%s() oid = %d, type = %d\t", __func__, OID(mobj), TYPE((objheader_t *)mobj));
	return;
      }
    } else { //A lock is acquired some place else
      if (version == ((objheader_t *)mobj)->version) { /* Check if versions match */
	(*v_matchlock)++;
      } else { /* If versions don't match ...HARD ABORT */
	(*v_nomatch)++;
	/* Send TRANS_DISAGREE to Coordinator */
	*getReplyCtrl = TRANS_DISAGREE;
	//printf("%s() oid = %d, type = %d\t", __func__, OID(mobj), TYPE((objheader_t *)mobj));
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
	//printf("%s() oid = %d, type = %d\t", __func__, OID(mobj), TYPE((objheader_t *)mobj));
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
	//printf("%s() oid = %d, type = %d\t", __func__, OID(mobj), TYPE((objheader_t *)mobj));
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
      notifyAll(&header->notifylist, OID(header), header->version);
    }
  }
  /* If object is newly created inside transaction then commit it */
  for (i = 0; i < numcreated; i++) {
    if ((header = ((objheader_t *) t_chashSearch(oidcreated[i]))) == NULL) {
      printf("Error: transComProcess() chashSearch returned NULL for oid = %x at %s, %d\n", oidcreated[i], __FILE__, __LINE__);
      return 1;
    }
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
    
    /* handle dynamic prefetching */
    handleDynPrefetching(numLocal, ntuples, siteid);
    ptr=((char *)&arryfields[endoffsets[ntuples-1]])+sizeof(int);
  }

  return head;
}

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
  struct writestruct writebuffer;
  writebuffer.offset=0;

  /* Send TRANS_PREFETCH control message */
  int first=1;

  /* Send Oids and offsets in pairs */
  tmp = mcpilenode->objpiles;
  while(tmp != NULL) {
    len = sizeof(int) + sizeof(unsigned int) + sizeof(unsigned int) + ((tmp->numoffset) * sizeof(short));
    char oidnoffset[len+5];
    char *buf=oidnoffset;
    if (first) {
      *buf=TRANS_PREFETCH;
      buf++;len++;
      first=0;
    }
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
    tmp = tmp->next;
    if (tmp==NULL) {
      *((int *)(&oidnoffset[len]))=-1;
      len+=sizeof(int);
    }
    if (tmp!=NULL)
      send_buf(sd, & writebuffer, oidnoffset, len);
    else
      forcesend_buf(sd, & writebuffer, oidnoffset, len);
  }

  LOGEVENT('S');
  return;
}

int getPrefetchResponse(int sd, struct readstruct *readbuffer) {
  int length = 0, size = 0;
  char control;
  unsigned int oid;
  void *modptr, *oldptr;

  recv_data_buf(sd, readbuffer, &length, sizeof(int));
  size = length - sizeof(int);
  char recvbuffer[size];
#ifdef TRANSSTATS
    getResponse++;
    LOGEVENT('Z');
#endif
    recv_data_buf(sd, readbuffer, recvbuffer, size);
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
      if(((objheader_t *)oldptr)->version <= ((objheader_t *)modptr)->version) {
	prehashRemove(oid);
	prehashInsert(oid, modptr);
      }
    } else { /* Else add the object ptr to hash table*/
      prehashInsert(oid, modptr);
    }
    /* Lock the Prefetch Cache look up table*/
    pthread_mutex_lock(&pflookup.lock);
    /* Broadcast signal on prefetch cache condition variable */
    pthread_cond_broadcast(&pflookup.cond);
    /* Unlock the Prefetch Cache look up table*/
    pthread_mutex_unlock(&pflookup.lock);
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

  if ((objheader = (objheader_t *) mhashSearch(oid)) == NULL) {
#ifdef CACHE
    if ((objheader = (objheader_t *) prehashSearch(oid)) == NULL) {
#endif
    unsigned int mid = lhashSearch(oid);
    int sd = getSock2(transReadSockPool, mid);
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
      return TYPE(objheader);
#else
      char *buffer;
      if((buffer = calloc(1, size)) == NULL) {
	printf("%s() Calloc Error %s at line %d\n", __func__, __FILE__, __LINE__);
	fflush(stdout);
	return 0;
      }
      recv_data(sd, buffer, size);
      objheader = (objheader_t *)buffer;
      unsigned short type = TYPE(objheader);
      free(buffer);
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

int processConfigFile() {
  FILE *configFile;
  const int maxLineLength = 200;
  char lineBuffer[maxLineLength];
  char *token;
  const char *delimiters = " \t\n";
  char *commentBegin;
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

  return 0;
}

void addHost(unsigned int hostIp) {
  unsigned int *tmpArray;

  if (findHost(hostIp) != -1)
    return;

  if (numHostsInSystem == sizeOfHostArray) {
    tmpArray = calloc(sizeOfHostArray * 2, sizeof(unsigned int));
    memcpy(tmpArray, hostIpAddrs, sizeof(unsigned int) * numHostsInSystem);
    free(hostIpAddrs);
    hostIpAddrs = tmpArray;
  }

  hostIpAddrs[numHostsInSystem++] = hostIp;

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
int reqNotify(unsigned int *oidarry, unsigned short *versionarry, unsigned int numoid) {
  int sock,i;
  objheader_t *objheader;
  struct sockaddr_in remoteAddr;
  char msg[1 + numoid * (sizeof(unsigned short) + sizeof(unsigned int)) +  3 * sizeof(unsigned int)];
  char *ptr;
  int bytesSent;
  int status, size;
  unsigned short version;
  unsigned int oid,mid;
  static unsigned int threadid = 0;
  pthread_mutex_t threadnotify = PTHREAD_MUTEX_INITIALIZER; //Lock and condition var for threadjoin and notification
  pthread_cond_t threadcond = PTHREAD_COND_INITIALIZER;
  notifydata_t *ndata;

  oid = oidarry[0];
  if((mid = lhashSearch(oid)) == 0) {
    printf("Error: %s() No such machine found for oid =%x\n",__func__, oid);
    return;
  }

  if ((sock = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
    perror("reqNotify():socket()");
    return -1;
  }

  bzero(&remoteAddr, sizeof(remoteAddr));
  remoteAddr.sin_family = AF_INET;
  remoteAddr.sin_port = htons(LISTEN_PORT);
  remoteAddr.sin_addr.s_addr = htonl(mid);

  /* Generate unique threadid */
  threadid++;

  /* Save threadid, numoid, oidarray, versionarray, pthread_cond_variable for later processing */
  if((ndata = calloc(1, sizeof(notifydata_t))) == NULL) {
    printf("Calloc Error %s, %d\n", __FILE__, __LINE__);
    return -1;
  }
  ndata->numoid = numoid;
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
  if (connect(sock, (struct sockaddr *)&remoteAddr, sizeof(remoteAddr)) < 0) {
    printf("reqNotify():error %d connecting to %s:%d\n", errno,
           inet_ntoa(remoteAddr.sin_addr), LISTEN_PORT);
    free(ndata);
    return -1;
  } else {
    msg[0] = THREAD_NOTIFY_REQUEST;
    *((unsigned int *)(&msg[1])) = numoid;
    /* Send array of oids  */
    size = sizeof(unsigned int);

    for(i = 0;i < numoid; i++) {
      oid = oidarry[i];
      *((unsigned int *)(&msg[1] + size)) = oid;
      size += sizeof(unsigned int);
    }

    /* Send array of version  */
    for(i = 0;i < numoid; i++) {
      version = versionarry[i];
      *((unsigned short *)(&msg[1] + size)) = version;
      size += sizeof(unsigned short);
    }

    *((unsigned int *)(&msg[1] + size)) = myIpAddr; size += sizeof(unsigned int);
    *((unsigned int *)(&msg[1] + size)) = threadid;
    pthread_mutex_lock(&(ndata->threadnotify));
    size = 1 + numoid * (sizeof(unsigned int) + sizeof(unsigned short)) + 3 * sizeof(unsigned int);
    send_data(sock, msg, size);
    pthread_cond_wait(&(ndata->threadcond), &(ndata->threadnotify));
    pthread_mutex_unlock(&(ndata->threadnotify));
  }

  pthread_cond_destroy(&threadcond);
  pthread_mutex_destroy(&threadnotify);
  free(ndata);
  close(sock);
  return status;
}

void threadNotify(unsigned int oid, unsigned short version, unsigned int tid) {
  notifydata_t *ndata;
  int i, objIsFound = 0, index;
  void *ptr;

  //Look up the tid and call the corresponding pthread_cond_signal
  if((ndata = notifyhashSearch(tid)) == NULL) {
    printf("threadnotify(): No such threadid is present %s, %d\n", __FILE__, __LINE__);
    return;
  } else  {
    for(i = 0; i < ndata->numoid; i++) {
      if(ndata->oidarry[i] == oid) {
	objIsFound = 1;
	index = i;
      }
    }
    if(objIsFound == 0) {
      printf("threadNotify(): Oid not found %s, %d\n", __FILE__, __LINE__);
      return;
    } else {
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
	pthread_mutex_lock(&(ndata->threadnotify));
	pthread_cond_signal(&(ndata->threadcond));
	pthread_mutex_unlock(&(ndata->threadnotify));
      }
    }
  }
  return;
}

int notifyAll(threadlist_t **head, unsigned int oid, unsigned int version) {
  threadlist_t *ptr;
  unsigned int mid;
  struct sockaddr_in remoteAddr;
  char msg[1 + sizeof(unsigned short) + 2*sizeof(unsigned int)];
  int sock, status, size, bytesSent;

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
  STATUS(headeraddr) =0;


  return pile;
}

plistnode_t *sortPiles(plistnode_t *pileptr) {
  plistnode_t *head, *ptr, *tail;
  head = pileptr;
  ptr = pileptr;
  /* Get tail pointer */
  while(ptr!= NULL) {
    tail = ptr;
    ptr = ptr->next;
  }
  ptr = pileptr;
  plistnode_t *prev = pileptr;
  /* Arrange local machine processing at the end of the pile list */
  while(ptr != NULL) {
    if(ptr != tail) {
      if(ptr->mid == myIpAddr && (prev != pileptr)) {
	prev->next = ptr->next;
	ptr->next = NULL;
	tail->next = ptr;
	return pileptr;
      }
      if((ptr->mid == myIpAddr) && (prev == pileptr)) {
	prev = ptr->next;
	ptr->next = NULL;
	tail->next = ptr;
	return prev;
      }
      prev = ptr;
    }
    ptr = ptr->next;
  }
  return pileptr;
}
