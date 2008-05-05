/* Coordinator => Machine that initiates the transaction request call for commiting a transaction
 * Participant => Machines that host the objects involved in a transaction commit */

#include <netinet/tcp.h>
#include "dstm.h"
#include "mlookup.h"
#include "llookup.h"
#include "threadnotify.h"
#ifdef COMPILER
#include "thread.h"
#endif

#define BACKLOG 10 //max pending connections
#define RECEIVE_BUFFER_SIZE 2048

extern int classsize[];
extern int numHostsInSystem;
extern pthread_mutex_t notifymutex;
extern unsigned int myIpAddr;

objstr_t *mainobjstore;
pthread_mutex_t mainobjstore_mutex;
pthread_mutex_t lockObjHeader;
pthread_mutexattr_t mainobjstore_mutex_attr; /* Attribute for lock to make it a recursive lock */

sockPoolHashTable_t *transPResponseSocketPool;

/* This function initializes the main objects store and creates the 
 * global machine and location lookup table */

int dstmInit(void)
{
  mainobjstore = objstrCreate(DEFAULT_OBJ_STORE_SIZE);
  /* Initialize attribute for mutex */
  pthread_mutexattr_init(&mainobjstore_mutex_attr);
  pthread_mutexattr_settype(&mainobjstore_mutex_attr, PTHREAD_MUTEX_RECURSIVE_NP);
  pthread_mutex_init(&mainobjstore_mutex, &mainobjstore_mutex_attr);
  pthread_mutex_init(&lockObjHeader,NULL);
  if (mhashCreate(HASH_SIZE, LOADFACTOR))
    return 1; //failure

  if (lhashCreate(HASH_SIZE, LOADFACTOR))
    return 1; //failure

  if (notifyhashCreate(N_HASH_SIZE, N_LOADFACTOR))
    return 1; //failure

  //Initialize socket pool
  if((transPResponseSocketPool = createSockPool(transPResponseSocketPool, 2*numHostsInSystem+1)) == NULL) {
    printf("Error in creating new socket pool at  %s line %d\n", __FILE__, __LINE__);
    return 0;
  }
  return 0;
}

/* This function starts the thread to listen on a socket 
 * for tranaction calls */
void *dstmListen()
{
  int listenfd, acceptfd;
  struct sockaddr_in my_addr;
  struct sockaddr_in client_addr;
  socklen_t addrlength = sizeof(struct sockaddr);
  pthread_t thread_dstm_accept;
  int i;
  int setsockflag=1;

  listenfd = socket(AF_INET, SOCK_STREAM, 0);
  if (listenfd == -1)
  {
    perror("socket");
    exit(1);
  }

  if (setsockopt(listenfd, SOL_SOCKET, SO_REUSEADDR, &setsockflag, sizeof (setsockflag)) < 0) {
    perror("socket");
    exit(1);
  }
#ifdef MAC
  if (setsockopt(listenfd, SOL_SOCKET, SO_NOSIGPIPE, &setsockflag, sizeof (setsockflag)) < 0) {
    perror("socket");
    exit(1);
  }
#endif

  my_addr.sin_family = AF_INET;
  my_addr.sin_port = htons(LISTEN_PORT);
  my_addr.sin_addr.s_addr = INADDR_ANY;
  memset(&(my_addr.sin_zero), '\0', 8);

  if (bind(listenfd, (struct sockaddr *)&my_addr, addrlength) == -1)
  {
    perror("bind");
    exit(1);
  }

  if (listen(listenfd, BACKLOG) == -1)
  {
    perror("listen");
    exit(1);
  }

  printf("Listening on port %d, fd = %d\n", LISTEN_PORT, listenfd);
  while(1)
  {
    int retval;
    int flag=1;
    acceptfd = accept(listenfd, (struct sockaddr *)&client_addr, &addrlength);
    if (acceptfd < 0) {
      perror("Error in accept: ");
      printf("error %x", acceptfd); fflush(stdout);
    }
    setsockopt(acceptfd, IPPROTO_TCP, TCP_NODELAY, (char *) &flag, sizeof(flag));
    do {
      retval=pthread_create(&thread_dstm_accept, NULL, dstmAccept, (void *)acceptfd);
    } while(retval!=0);
    pthread_detach(thread_dstm_accept);
  }
}
/* This function accepts a new connection request, decodes the control message in the connection 
 * and accordingly calls other functions to process new requests */
void *dstmAccept(void *acceptfd) {
  int val, retval, size, sum, sockid;
  unsigned int oid;
  char *buffer;
  char control,ctrl;
  char *ptr;
  void *srcObj;
  objheader_t *h;
  trans_commit_data_t transinfo;
  unsigned short objType, *versionarry, version;
  unsigned int *oidarry, numoid, mid, threadid;

  /* Receive control messages from other machines */
  if((retval = recv_data_errorcode((int)acceptfd, &control, sizeof(char))) <= 0) {
    printf("%s() Error: Receiving control = %d at %s, %d\n", __func__, control, __FILE__, __LINE__);
    pthread_exit(NULL);
  }

  switch(control) {
    case READ_REQUEST:
      do {
        /* Read oid requested and search if available */
        recv_data((int)acceptfd, &oid, sizeof(unsigned int));
        if((srcObj = mhashSearch(oid)) == NULL) {
          printf("Error: Object 0x%x is not found in Main Object Store %s, %d\n", oid, __FILE__, __LINE__);
          break;
        }
        h = (objheader_t *) srcObj;
        GETSIZE(size, h);
        size += sizeof(objheader_t);
        sockid = (int) acceptfd;

        if (h == NULL) {
          ctrl = OBJECT_NOT_FOUND;
          send_data(sockid, &ctrl, sizeof(char));
        } else {
          /* Type */
          char msg[]={OBJECT_FOUND, 0, 0, 0, 0};
          *((int *)&msg[1])=size;
          send_data(sockid, &msg, sizeof(msg));
          send_data(sockid, h, size);
        }
        if((retval = recv_data_errorcode((int)acceptfd, &control, sizeof(char))) <= 0)  {
          pthread_exit(NULL);
        }
      } while(control == READ_REQUEST);
      break;

    case READ_MULT_REQUEST:
      break;

    case MOVE_REQUEST:
      break;

    case MOVE_MULT_REQUEST:
      break;

    case TRANS_REQUEST:
      /* Read transaction request */
      transinfo.objlocked = NULL;
      transinfo.objnotfound = NULL;
      transinfo.modptr = NULL;
      transinfo.numlocked = 0;
      transinfo.numnotfound = 0;
      if((val = readClientReq(&transinfo, (int)acceptfd)) != 0) {
        printf("Error: In readClientReq() %s, %d\n", __FILE__, __LINE__);
        break;
      }
      break;
    case TRANS_PREFETCH:
      do {
        if((val = prefetchReq((int)acceptfd)) != 0) {
          printf("Error: In prefetchReq() %s, %d\n", __FILE__, __LINE__);
          break;
        }
        if((retval = recv_data_errorcode((int)acceptfd, &control, sizeof(char))) <= 0)  {
          pthread_exit(NULL);
        }
      } while (control == TRANS_PREFETCH);
      break;
    case TRANS_PREFETCH_RESPONSE:
      do {
        if((val = getPrefetchResponse((int) acceptfd)) != 0) {
          printf("Error: In getPrefetchResponse() %s, %d\n", __FILE__, __LINE__);
          break;
        }
        if((retval = recv_data_errorcode((int)acceptfd, &control, sizeof(char))) <= 0)  {
          pthread_exit(NULL);
        }
      } while (control == TRANS_PREFETCH_RESPONSE);
      break;
    case START_REMOTE_THREAD:
      recv_data((int)acceptfd, &oid, sizeof(unsigned int));
      objType = getObjType(oid);
      startDSMthread(oid, objType);
      break;

    case THREAD_NOTIFY_REQUEST:
      recv_data((int)acceptfd, &numoid, sizeof(unsigned int));
      size = (sizeof(unsigned int) + sizeof(unsigned short)) * numoid + 2 * sizeof(unsigned int);
      if((buffer = calloc(1,size)) == NULL) {
        printf("%s() Calloc error at %s, %d\n", __func__, __FILE__, __LINE__);
        pthread_exit(NULL);
      }

      recv_data((int)acceptfd, buffer, size);

      oidarry = calloc(numoid, sizeof(unsigned int)); 
      memcpy(oidarry, buffer, sizeof(unsigned int) * numoid);
      size = sizeof(unsigned int) * numoid;
      versionarry = calloc(numoid, sizeof(unsigned short));
      memcpy(versionarry, buffer+size, sizeof(unsigned short) * numoid);
      size += sizeof(unsigned short) * numoid;
      mid = *((unsigned int *)(buffer+size));
      size += sizeof(unsigned int);
      threadid = *((unsigned int *)(buffer+size));
      processReqNotify(numoid, oidarry, versionarry, mid, threadid);
      free(buffer);

      break;

    case THREAD_NOTIFY_RESPONSE:
      size = sizeof(unsigned short) + 2 * sizeof(unsigned int);
      if((buffer = calloc(1,size)) == NULL) {
        printf("%s() Calloc error at %s, %d\n", __func__, __FILE__, __LINE__);
        pthread_exit(NULL);
      }

      recv_data((int)acceptfd, buffer, size);

      oid = *((unsigned int *)buffer);
      size = sizeof(unsigned int);
      version = *((unsigned short *)(buffer+size));
      size += sizeof(unsigned short);
      threadid = *((unsigned int *)(buffer+size));
      threadNotify(oid,version,threadid);
      free(buffer);
      break;

    case CLOSE_CONNECTION:
      goto closeconnection;

    default:
      printf("Error: dstmAccept() Unknown opcode %d at %s, %d\n", control, __FILE__, __LINE__);
  }

closeconnection:
  /* Close connection */
  if (close((int)acceptfd) == -1)
    perror("close");
  pthread_exit(NULL);
}
  
/* This function reads the information available in a transaction request
 * and makes a function call to process the request */
int readClientReq(trans_commit_data_t *transinfo, int acceptfd) {
	char *ptr;
	void *modptr;
	unsigned int *oidmod, oid;
	fixed_data_t fixed;
	objheader_t *headaddr;
	int sum, i, size, n, val;

	oidmod = NULL;

	/* Read fixed_data_t data structure */ 
	size = sizeof(fixed) - 1;
	ptr = (char *)&fixed;;
	fixed.control = TRANS_REQUEST;
	recv_data((int)acceptfd, ptr+1, size);

	/* Read list of mids */
	int mcount = fixed.mcount;
	size = mcount * sizeof(unsigned int);
	unsigned int listmid[mcount];
	ptr = (char *) listmid;
	recv_data((int)acceptfd, ptr, size);
	
	/* Read oid and version tuples for those objects that are not modified in the transaction */
	int numread = fixed.numread;
	size = numread * (sizeof(unsigned int) + sizeof(unsigned short));
	char objread[size];
	if(numread != 0) { //If pile contains more than one object to be read, 
			  // keep reading all objects
		recv_data((int)acceptfd, objread, size);	
	}
	
	/* Read modified objects */
	if(fixed.nummod != 0) {
		if ((modptr = calloc(1, fixed.sum_bytes)) == NULL) {
			printf("calloc error for modified objects %s, %d\n", __FILE__, __LINE__);
			return 1;
		}
		size = fixed.sum_bytes;
		recv_data((int)acceptfd, modptr, size);	
	}

	/* Create an array of oids for modified objects */
	oidmod = (unsigned int *) calloc(fixed.nummod, sizeof(unsigned int));
	if (oidmod == NULL)
	{
		printf("calloc error %s, %d\n", __FILE__, __LINE__);
		return 1;
	}
	ptr = (char *) modptr;
	for(i = 0 ; i < fixed.nummod; i++) {
	  int tmpsize;
	  headaddr = (objheader_t *) ptr;
	  oid = OID(headaddr);
	  oidmod[i] = oid;
	  GETSIZE(tmpsize, headaddr);
	  ptr += sizeof(objheader_t) + tmpsize;
	}
	
	/*Process the information read */
	if((val = processClientReq(&fixed, transinfo, listmid, objread, modptr, oidmod, acceptfd)) != 0) {
		printf("Error: In processClientReq() %s, %d\n", __FILE__, __LINE__);
		/* Free resources */
		if(oidmod != NULL) {
			free(oidmod);
		}
		return 1;
	}

	/* Free resources */
	if(oidmod != NULL) {
		free(oidmod);
	}

	return 0;
}

/* This function processes the Coordinator's transaction request using "handleTransReq" 
 * function and sends a reply to the co-ordinator.
 * Following this it also receives a new control message from the co-ordinator and processes this message*/
int processClientReq(fixed_data_t *fixed, trans_commit_data_t *transinfo,
		unsigned int *listmid, char *objread, void *modptr, unsigned int *oidmod, int acceptfd) {
	char control, sendctrl, retval;
	objheader_t *tmp_header;
	void *header;
	int  i = 0, val;

	/* Send reply to the Coordinator */
	if((retval = handleTransReq(fixed, transinfo, listmid, objread, modptr,acceptfd)) == 0 ) {
		printf("Error: In handleTransReq() %s, %d\n", __FILE__, __LINE__);
		return 1;
	}

	recv_data((int)acceptfd, &control, sizeof(char));
	
	/* Process the new control message */
	switch(control) {
		case TRANS_ABORT:
			if (fixed->nummod > 0)
				free(modptr);
			/* Unlock objects that was locked due to this transaction */
			for(i = 0; i< transinfo->numlocked; i++) {
				if((header = mhashSearch(transinfo->objlocked[i])) == NULL) {
					printf("mhashSearch returns NULL at %s, %d\n", __FILE__, __LINE__);// find the header address
					return 1;
				}
				UnLock(STATUSPTR(header));
			}

			/* Send ack to Coordinator */
			sendctrl = TRANS_UNSUCESSFUL;
			send_data((int)acceptfd, &sendctrl, sizeof(char));
			break;

		case TRANS_COMMIT:
			/* Invoke the transCommit process() */
			if((val = transCommitProcess(modptr, oidmod, transinfo->objlocked, fixed->nummod, transinfo->numlocked, (int)acceptfd)) != 0) {
				printf("Error: In transCommitProcess() %s, %d\n", __FILE__, __LINE__);
				/* Free memory */
				if (transinfo->objlocked != NULL) {
					free(transinfo->objlocked);
				}
				if (transinfo->objnotfound != NULL) {
					free(transinfo->objnotfound);
				}
				return 1;
			}
			break;

		case TRANS_ABORT_BUT_RETRY_COMMIT_WITH_RELOCATING:
			break;
		default:
			printf("Error: No response to TRANS_AGREE OR DISAGREE protocol %s, %d\n", __FILE__, __LINE__);
			//TODO Use fixed.trans_id  TID since Client may have died
			break;
	}

	/* Free memory */
	if (transinfo->objlocked != NULL) {
		free(transinfo->objlocked);
	}
	if (transinfo->objnotfound != NULL) {
		free(transinfo->objnotfound);
	}

	return 0;
}

/* This function increments counters while running a voting decision on all objects involved 
 * in TRANS_REQUEST and If a TRANS_DISAGREE sends the response immediately back to the coordinator */
char handleTransReq(fixed_data_t *fixed, trans_commit_data_t *transinfo, unsigned int *listmid, char *objread, void *modptr, int acceptfd) {
	int val, i = 0, j;
	unsigned short version;
	char control = 0, *ptr;
	unsigned int oid;
	unsigned int *oidnotfound, *oidlocked;
	void *mobj;
	objheader_t *headptr;

	/* Counters and arrays to formulate decision on control message to be sent */
	oidnotfound = (unsigned int *) calloc(fixed->numread + fixed->nummod, sizeof(unsigned int)); 
	oidlocked = (unsigned int *) calloc(fixed->numread + fixed->nummod, sizeof(unsigned int)); 
	int objnotfound = 0, objlocked = 0;
	int v_nomatch = 0, v_matchlock = 0, v_matchnolock = 0;

	/* modptr points to the beginning of the object store 
	 * created at the Pariticipant. 
	 * Object store holds the modified objects involved in the transaction request */ 
	ptr = (char *) modptr;
	
	/* Process each oid in the machine pile/ group per thread */
	for (i = 0; i < fixed->numread + fixed->nummod; i++) {
		if (i < fixed->numread) {//Objs only read and not modified
			int incr = sizeof(unsigned int) + sizeof(unsigned short);// Offset that points to next position in the objread array
			incr *= i;
			oid = *((unsigned int *)(objread + incr));
			incr += sizeof(unsigned int);
			version = *((unsigned short *)(objread + incr));
		} else {//Objs modified
		  int tmpsize;
		  headptr = (objheader_t *) ptr;
		  oid = OID(headptr);
          version = headptr->version;
          GETSIZE(tmpsize, headptr);
          ptr += sizeof(objheader_t) + tmpsize;
        }

        /* Check if object is still present in the machine since the beginning of TRANS_REQUEST */

        if ((mobj = mhashSearch(oid)) == NULL) {/* Obj not found */
          /* Save the oids not found and number of oids not found for later use */
          oidnotfound[objnotfound] = oid;
          objnotfound++;
        } else { /* If Obj found in machine (i.e. has not moved) */
          /* Check if Obj is locked by any previous transaction */
          if (test_and_set(STATUSPTR(mobj))) {
	    //don't have lock
            if (version == ((objheader_t *)mobj)->version) {      /* If locked then match versions */
              v_matchlock++;
            } else {/* If versions don't match ...HARD ABORT */
              v_nomatch++;
              /* Send TRANS_DISAGREE to Coordinator */
              control = TRANS_DISAGREE;
              if (objlocked > 0) {
                for(j = 0; j < objlocked; j++) {
                  if((headptr = mhashSearch(oidlocked[j])) == NULL) {
                    printf("mhashSearch returns NULL at %s, %d\n", __FILE__, __LINE__);
                    return 0;
                  }
		  UnLock(STATUSPTR(headptr));
                }
                free(oidlocked);
              }
              send_data(acceptfd, &control, sizeof(char));
              return control;
            }
          } else {/* If Obj is not locked then lock object */
            /* Save all object oids that are locked on this machine during this transaction request call */
            oidlocked[objlocked] = OID(((objheader_t *)mobj));
            objlocked++;
            if (version == ((objheader_t *)mobj)->version) { /* Check if versions match */
              v_matchnolock++;
            } else { /* If versions don't match ...HARD ABORT */
              v_nomatch++;
              control = TRANS_DISAGREE;
              if (objlocked > 0) {
                for(j = 0; j < objlocked; j++) {
                  if((headptr = mhashSearch(oidlocked[j])) == NULL) {
                    printf("mhashSearch returns NULL at %s, %d\n", __FILE__, __LINE__);
                    return 0;
                  }
		  UnLock(STATUSPTR(headptr));
                }
                free(oidlocked);
              }

              /* Send TRANS_DISAGREE to Coordinator */
              send_data(acceptfd, &control, sizeof(char));
              return control;
            }
          }
        }
	}
	
	/* Decide what control message to send to Coordinator */
	if ((control = decideCtrlMessage(fixed, transinfo, &v_matchnolock, &v_matchlock, &v_nomatch, &objnotfound, &objlocked,
					modptr, oidnotfound, oidlocked, acceptfd)) == 0) {
		printf("Error: In decideCtrlMessage() %s, %d\n", __FILE__, __LINE__);
		return 0;
	}
	
	return control;

}
/* This function decides what control message such as TRANS_AGREE, TRANS_DISAGREE or TRANS_SOFT_ABORT
 * to send to Coordinator based on the votes of oids involved in the transaction */
char decideCtrlMessage(fixed_data_t *fixed, trans_commit_data_t *transinfo, int *v_matchnolock, int *v_matchlock, 
		int *v_nomatch, int *objnotfound, int *objlocked, void *modptr, 
		unsigned int *oidnotfound, unsigned int *oidlocked, int acceptfd) {
	int val;
	char control = 0;

	/* Condition to send TRANS_AGREE */
	if(*(v_matchnolock) == fixed->numread + fixed->nummod) {
		control = TRANS_AGREE;
		/* Send control message */
		send_data(acceptfd, &control, sizeof(char));
	}
	/* Condition to send TRANS_SOFT_ABORT */
	if((*(v_matchlock) > 0 && *(v_nomatch) == 0) || (*(objnotfound) > 0 && *(v_nomatch) == 0)) {
		control = TRANS_SOFT_ABORT;

		/* Send control message */
		send_data(acceptfd, &control, sizeof(char));
	
		/* Send number of oids not found and the missing oids if objects are missing in the machine */
		if(*(objnotfound) != 0) { 
			int msg[1];
			msg[0] = *(objnotfound);
			send_data(acceptfd, &msg, sizeof(int));
			int size = sizeof(unsigned int)* *(objnotfound);
			send_data(acceptfd, oidnotfound, size);
		}
	}

	/* Fill out the trans_commit_data_t data structure. This is required for a trans commit process
	 * if Participant receives a TRANS_COMMIT */
	transinfo->objlocked = oidlocked;
	transinfo->objnotfound = oidnotfound;
	transinfo->modptr = modptr;
	transinfo->numlocked = *(objlocked);
	transinfo->numnotfound = *(objnotfound);

	return control;
}

/* This function processes all modified objects involved in a TRANS_COMMIT and updates pointer 
 * addresses in lookup table and also changes version number
 * Sends an ACK back to Coordinator */
int transCommitProcess(void *modptr, unsigned int *oidmod, unsigned int *oidlocked, int nummod, int numlocked, int acceptfd) {
  objheader_t *header;
  objheader_t *newheader;
  int i = 0, offset = 0;
  char control;
  int tmpsize;
  
  /* Process each modified object saved in the mainobject store */
  for(i = 0; i < nummod; i++) {
    if((header = (objheader_t *) mhashSearch(oidmod[i])) == NULL) {
      printf("Error: mhashsearch returns NULL at %s, %d\n", __FILE__, __LINE__);
      return 1;
    }
    GETSIZE(tmpsize,header);
    memcpy((char*)header + sizeof(objheader_t), ((char *)modptr + sizeof(objheader_t) + offset), tmpsize);
    header->version += 1; 
    /* If threads are waiting on this object to be updated, notify them */
    if(header->notifylist != NULL) {
      notifyAll(&header->notifylist, OID(header), header->version);
    }
    offset += sizeof(objheader_t) + tmpsize;
  }
  
  if (nummod > 0)
    free(modptr);
  
  /* Unlock locked objects */
  for(i = 0; i < numlocked; i++) {
    if((header = (objheader_t *) mhashSearch(oidlocked[i])) == NULL) {
      printf("Error: mhashsearch returns NULL at %s, %d\n", __FILE__, __LINE__);
      return 1;
    }
    UnLock(STATUSPTR(header));
  }
  //TODO Update location lookup table
  
  /* Send ack to coordinator */
  control = TRANS_SUCESSFUL;
  send_data((int)acceptfd, &control, sizeof(char));
  return 0;
}

/* This function recevies the oid and offset tuples from the Coordinator's prefetch call.
 * Looks for the objects to be prefetched in the main object store.
 * If objects are not found then record those and if objects are found
 * then use offset values to prefetch references to other objects */

int prefetchReq(int acceptfd) {
  int i, size, objsize, numoffset = 0;
  int length;
  char *recvbuffer, control;
  unsigned int oid, mid=-1;
  objheader_t *header;
  oidmidpair_t oidmid;
  int sd = -1;
      
  while(1) {
    recv_data((int)acceptfd, &numoffset, sizeof(int));
    if(numoffset == -1) 
      break;
    recv_data((int)acceptfd, &oidmid, 2*sizeof(unsigned int));
    oid = oidmid.oid;
    if (mid != oidmid.mid) {
      if (mid!=-1) {
	freeSockWithLock(transPResponseSocketPool, mid, sd);
      }
      mid=oidmid.mid;
      sd = getSockWithLock(transPResponseSocketPool, mid);
    }
    short offsetarry[numoffset];
    recv_data((int) acceptfd, offsetarry, numoffset*sizeof(short));
    
    /*Process each oid */
    if ((header = mhashSearch(oid)) == NULL) {/* Obj not found */
      /* Save the oids not found in buffer for later use */
      size = sizeof(int) + sizeof(char) + sizeof(unsigned int) ;
      char sendbuffer[size];
      *((int *) sendbuffer) = size;
      *((char *)(sendbuffer + sizeof(int))) = OBJECT_NOT_FOUND;
      *((unsigned int *)(sendbuffer + sizeof(int) + sizeof(char))) = oid;
      control = TRANS_PREFETCH_RESPONSE;
      sendPrefetchResponse(sd, &control, sendbuffer, &size);
    } else { /* Object Found */
      int incr = 0;
      GETSIZE(objsize, header);
      size = sizeof(int) + sizeof(char) + sizeof(unsigned int) + sizeof(objheader_t) + objsize;
      char sendbuffer[size];
      *((int *) (sendbuffer + incr)) = size;
      incr += sizeof(int);
      *((char *)(sendbuffer + incr)) = OBJECT_FOUND;
      incr += sizeof(char);
      *((unsigned int *)(sendbuffer+incr)) = oid;
      incr += sizeof(unsigned int);
      memcpy(sendbuffer + incr, header, objsize + sizeof(objheader_t));
      
      control = TRANS_PREFETCH_RESPONSE;
      sendPrefetchResponse(sd, &control, sendbuffer, &size);
      
      /* Calculate the oid corresponding to the offset value */
      for(i = 0 ; i< numoffset ; i++) {
	/* Check for arrays  */
	if(TYPE(header) > NUMCLASSES) {
	  int elementsize = classsize[TYPE(header)];
	  struct ArrayObject *ao = (struct ArrayObject *) (((char *)header) + sizeof(objheader_t));
	  unsigned short length = ao->___length___;
	  /* Check if array out of bounds */
	  if(offsetarry[i]< 0 || offsetarry[i] >= length) {
	    break;
	  }
	  oid = *((unsigned int *)(((char *)header) + sizeof(objheader_t) + sizeof(struct ArrayObject) + (elementsize*offsetarry[i])));
	} else {
	  oid = *((unsigned int *)(((char *)header) + sizeof(objheader_t) + offsetarry[i]));
	}
	
	/* Don't continue if we hit a NULL pointer */
	if (oid==0)
	  break;

	if((header = mhashSearch(oid)) == NULL) {
	  size = sizeof(int) + sizeof(char) + sizeof(unsigned int) ;
	  char sendbuffer[size];
	  *((int *) sendbuffer) = size;
	  *((char *)(sendbuffer + sizeof(int))) = OBJECT_NOT_FOUND;
	  *((unsigned int *)(sendbuffer + sizeof(int) + sizeof(char))) = oid;
	  
	  control = TRANS_PREFETCH_RESPONSE;
	  sendPrefetchResponse(sd, &control, sendbuffer, &size);
	  break;
	} else {/* Obj Found */
	  int incr = 0;
	  GETSIZE(objsize, header);
	  size = sizeof(int) + sizeof(char) + sizeof(unsigned int) + sizeof(objheader_t) + objsize;
	  char sendbuffer[size];
	  *((int *) (sendbuffer + incr)) = size;
	  incr += sizeof(int);
	  *((char *)(sendbuffer + incr)) = OBJECT_FOUND;
	  incr += sizeof(char);
	  *((unsigned int *)(sendbuffer+incr)) = oid;
	  incr += sizeof(unsigned int);
	  memcpy(sendbuffer + incr, header, objsize + sizeof(objheader_t));
	  
	  control = TRANS_PREFETCH_RESPONSE;
	  sendPrefetchResponse(sd, &control, sendbuffer, &size);
	}
      }
    }
  }
  //Release socket
  if (mid!=-1)
    freeSockWithLock(transPResponseSocketPool, mid, sd);
    
  return 0;
}

void sendPrefetchResponse(int sd, char *control, char *sendbuffer, int *size) {
	send_data(sd, control, sizeof(char));
	/* Send the buffer with its size */
	int length = *(size);
	send_data(sd, sendbuffer, length);
}

void processReqNotify(unsigned int numoid, unsigned int *oidarry, unsigned short *versionarry, unsigned int mid, unsigned int threadid) {
  objheader_t *header;
  unsigned int oid;
  unsigned short newversion;
  char msg[1+  2 * sizeof(unsigned int) + sizeof(unsigned short)];
  int sd;
  struct sockaddr_in remoteAddr;
  int bytesSent;
  int size;
  int i = 0;
  
  while(i < numoid) {
    oid = *(oidarry + i);
    if((header = (objheader_t *) mhashSearch(oid)) == NULL) {
      printf("Error: mhashsearch returns NULL at %s, %d\n", __FILE__, __LINE__);
      return;
    } else {
      /* Check to see if versions are same */
    checkversion:
      if (test_and_set(STATUSPTR(header))==0) {
	//have lock
	newversion = header->version;
	if(newversion == *(versionarry + i)) {
	  //Add to the notify list 
	  if((header->notifylist = insNode(header->notifylist, threadid, mid)) == NULL) {
	    printf("Error: Obj notify list points to NULL %s, %d\n", __FILE__, __LINE__); 
	    return;
	  }
	  UnLock(STATUSPTR(header));
	} else {
	  UnLock(STATUSPTR(header));
	  if ((sd = socket(AF_INET, SOCK_STREAM, 0)) < 0){
	    perror("processReqNotify():socket()");
	    return;
	  }
	  bzero(&remoteAddr, sizeof(remoteAddr));
	  remoteAddr.sin_family = AF_INET;
	  remoteAddr.sin_port = htons(LISTEN_PORT);
	  remoteAddr.sin_addr.s_addr = htonl(mid);
	  
	  if (connect(sd, (struct sockaddr *)&remoteAddr, sizeof(remoteAddr)) < 0){
	    printf("Error: processReqNotify():error %d connecting to %s:%d\n", errno,
		   inet_ntoa(remoteAddr.sin_addr), LISTEN_PORT);
	    close(sd);
	    return;
	  } else {
	    //Send Update notification
	    msg[0] = THREAD_NOTIFY_RESPONSE;
	    *((unsigned int *)&msg[1]) = oid;
	    size = sizeof(unsigned int);
	    *((unsigned short *)(&msg[1]+size)) = newversion;
	    size += sizeof(unsigned short);
	    *((unsigned int *)(&msg[1]+size)) = threadid;
	    size = 1+ 2*sizeof(unsigned int) + sizeof(unsigned short);
	    send_data(sd, msg, size);
	  }
	  close(sd);
	}
      } else {
	randomdelay();
	goto checkversion;
      }
    }
    i++;
  }
  free(oidarry);
  free(versionarry);
}
