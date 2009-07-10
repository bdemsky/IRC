/* Coordinator => Machine that initiates the transaction request call for commiting a transaction
 * Participant => Machines that host the objects involved in a transaction commit */

#include <netinet/tcp.h>
#include <ip.h>
#include "dstm.h"
#include "mlookup.h"
#include "llookup.h"
#include "threadnotify.h"
#include "prefetch.h"
#include <sched.h>
#ifdef COMPILER
#include "thread.h"
#endif
#include "gCollect.h"

#ifdef RECOVERY
#include <unistd.h>
#include <signal.h>
#endif

#define BACKLOG 10 //max pending connections
#define RECEIVE_BUFFER_SIZE 2048

extern int classsize[];
extern int numHostsInSystem;
extern pthread_mutex_t notifymutex;

extern int *liveHosts;
extern unsigned int *locateObjHosts;
pthread_mutex_t liveHosts_mutex;
pthread_mutex_t leaderFixing_mutex;

extern int liveHostsValid;
extern int numLiveHostsInSystem;
extern __thread int timeoutFlag;
extern __thread int timeoutFlag;
int testcount = 0;

objstr_t *mainobjstore;
pthread_mutex_t mainobjstore_mutex;
pthread_mutex_t lockObjHeader;
pthread_mutexattr_t mainobjstore_mutex_attr; /* Attribute for lock to make it a recursive lock */

sockPoolHashTable_t *transPResponseSocketPool;
extern sockPoolHashTable_t *transRequestSockPool;

int failFlag = 0; //debug
int leaderFixing;

/******************************
 * Global variables for Paxos
 ******************************/
extern int n_a;
extern unsigned int v_a;
extern int n_h;
extern int my_n;
extern int leader;
extern int paxosRound;
/* This function initializes the main objects store and creates the
 * global machine and location lookup table */

int dstmInit(void) {
  mainobjstore = objstrCreate(DEFAULT_OBJ_STORE_SIZE);
  /* Initialize attribute for mutex */
  pthread_mutexattr_init(&mainobjstore_mutex_attr);
  pthread_mutexattr_settype(&mainobjstore_mutex_attr, PTHREAD_MUTEX_RECURSIVE_NP);
  pthread_mutex_init(&mainobjstore_mutex, &mainobjstore_mutex_attr);
  pthread_mutex_init(&lockObjHeader,NULL);

	pthread_mutex_init(&liveHosts_mutex, NULL);
	pthread_mutex_init(&leaderFixing_mutex, NULL);

  if (mhashCreate(MHASH_SIZE, MLOADFACTOR))
    return 1;             //failure

  if (lhashCreate(HASH_SIZE, LOADFACTOR))
    return 1;             //failure

  if (notifyhashCreate(N_HASH_SIZE, N_LOADFACTOR))
    return 1;             //failure

  //Initialize socket pool
  if((transPResponseSocketPool = createSockPool(transPResponseSocketPool, DEFAULTSOCKPOOLSIZE)) == NULL) {
    printf("Error in creating new socket pool at  %s line %d\n", __FILE__, __LINE__);
    return 0;
  }

  return 0;
}

int startlistening() {
  int listenfd;
  struct sockaddr_in my_addr;
  socklen_t addrlength = sizeof(struct sockaddr);
  int setsockflag=1;

  listenfd = socket(AF_INET, SOCK_STREAM, 0);
  if (listenfd == -1) {
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

  if (bind(listenfd, (struct sockaddr *)&my_addr, addrlength) == -1) {
    perror("bind");
    exit(1);
  }

  if (listen(listenfd, BACKLOG) == -1) {
    perror("listen");
    exit(1);
  }
  return listenfd;
}

/* This function starts the thread to listen on a socket
 * for tranaction calls */
void *dstmListen(void *lfd) {
  int listenfd=(int)lfd;
  int acceptfd;
  struct sockaddr_in client_addr;
  socklen_t addrlength = sizeof(struct sockaddr);
  pthread_t thread_dstm_accept;

  printf("Listening on port %d, fd = %d\n", LISTEN_PORT, listenfd);
  while(1) {
    int retval;
    int flag=1;
		if(failFlag) while(1);
    acceptfd = accept(listenfd, (struct sockaddr *)&client_addr, &addrlength);
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
  int val, retval, size, sum, sockid, sd = 0;
  unsigned int oid;
  char *buffer;
	char control,ctrl, response;
	char *ptr;
	void *srcObj;
	void *dupeptr;
	int i, tempsize;
	objheader_t *h;
	trans_commit_data_t transinfo;
  unsigned short objType, *versionarry, version;
	unsigned int *oidarry, numoid, mid, threadid;
  int n, v;

	printf("%s-> Entering dstmAccept\n", __func__);	fflush(stdout);
	/* Receive control messages from other machines */
	while(1) {
		int ret=recv_data_errorcode((int)acceptfd, &control, sizeof(char));
		/*	if(timeoutFlag || timeoutFlag)  {
		//is there any way to force a context switch?
		printf("recv_data_errorcode: exiting, timeoutFlag:%d, timeoutFlag:%d\n", failedMachineFlag, timeoutFlag);
		exit(0);
		}*/
		if(failFlag) {
			while(1) { 
				sleep(10);
			}
		}

		if (ret==0)
			break;
		if (ret==-1) {
			printf("DEBUG -> RECV Error!.. retrying\n");
			exit(0);
			break;
		}
		printf("%s-> dstmAccept control = %d\n", __func__, (int)control);
		switch(control) {
			case READ_REQUEST:
				/* Read oid requested and search if available */
				recv_data((int)acceptfd, &oid, sizeof(unsigned int));
				while((srcObj = mhashSearch(oid)) == NULL) {
					int ret;
					if((ret = sched_yield()) != 0) {
						printf("%s(): error no %d in thread yield\n", __func__, errno);
					}
				}
				h = (objheader_t *) srcObj;
				GETSIZE(size, h);
				size += sizeof(objheader_t);
				sockid = (int) acceptfd;
				if (h == NULL) {
					ctrl = OBJECT_NOT_FOUND;
					send_data(sockid, &ctrl, sizeof(char));
					if(timeoutFlag || timeoutFlag) {
						printf("send_data: remote machine dead, line:%d\n", __LINE__);
						timeoutFlag = 0;
						exit(1);
					}
				} else {
					// Type
					char msg[]={OBJECT_FOUND, 0, 0, 0, 0};
					*((int *)&msg[1])=size;
					printf("*****testcount:%d\n", testcount);
					printf("oid:%u, h->version:%d\n", OID(h), h->version);
					//if(OID(h) == 1 && ((h->version == 20 && liveHosts[0]) || (h->version == 15000  && liveHosts[2])))
					if(testcount == 1000)
					{
						printf("Pretending to fail\n");
            failFlag = 1;//sleep(5);
						while(1) {
							sleep(10);
						}//exit(0);
					}
					else
						testcount++;
					send_data(sockid, &msg, sizeof(msg));
					send_data(sockid, h, size);
					if(timeoutFlag || timeoutFlag) {
						printf("send_data: remote machine dead, line:%d\n", __LINE__);
						timeoutFlag = 0;
						exit(1);
					}
				}
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
					pthread_exit(NULL);
				}
				break;

			case TRANS_PREFETCH:
#ifdef RANGEPREFETCH
				if((val = rangePrefetchReq((int)acceptfd)) != 0) {
					printf("Error: In rangePrefetchReq() %s, %d\n", __FILE__, __LINE__);
					break;
				}
#else
				if((val = prefetchReq((int)acceptfd)) != 0) {
					printf("Error: In prefetchReq() %s, %d\n", __FILE__, __LINE__);
					break;
				}
#endif
				break;

			case TRANS_PREFETCH_RESPONSE:
#ifdef RANGEPREFETCH
				if((val = getRangePrefetchResponse((int)acceptfd)) != 0) {
					printf("Error: In getRangePrefetchRespose() %s, %d\n", __FILE__, __LINE__);
					break;
				}
#else
				if((val = getPrefetchResponse((int) acceptfd)) != 0) {
					printf("Error: In getPrefetchResponse() %s, %d\n", __FILE__, __LINE__);
					break;
				}
#endif
				break;

			case START_REMOTE_THREAD:
				recv_data((int)acceptfd, &oid, sizeof(unsigned int));
				objType = getObjType(oid);
				printf("%s-> Call startDSMthread\n", __func__);
				startDSMthread(oid, objType);
				printf("%s-> Finish startDSMthread\n", __func__);
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

			case RESPOND_LIVE:
				liveHostsValid = 0;
				ctrl = LIVE;
				send_data((int)acceptfd, &ctrl, sizeof(ctrl));
				if(timeoutFlag) {
					printf("send_data: remote machine dead, line:%d\n", __LINE__);
					timeoutFlag = 0;
					exit(1);
				}
				printf("%s (RESPOND_LIVE)-> Sending LIVE!\n", __func__);
				break;

			case REMOTE_RESTORE_DUPLICATED_STATE:
				printf("%s (REMOTE_RESTORE_DUPLICATED_STATE)-> Starting process\n", __func__);	
				recv_data((int)acceptfd, &mid, sizeof(unsigned int));
				ctrl = DUPLICATION_COMPLETE;
				send_data((int)acceptfd, &ctrl, sizeof(char));	
				if(!liveHosts[findHost(mid)]) 
					break;
				//ctrl = LIVE;
				//send_data((int)acceptfd, &ctrl, sizeof(char));	
				pthread_mutex_lock(&leaderFixing_mutex);
				if(!leaderFixing) {
					leaderFixing = 1;
					pthread_mutex_unlock(&leaderFixing_mutex);
					// begin fixing
					updateLiveHosts();
					if(!liveHosts[findHost(mid)]) {	//confirmed dead
						duplicateLostObjects(mid);
					}
					if(updateLiveHostsCommit() != 0) {
						printf("error updateLiveHostsCommit()\n");
						exit(1);
					}
					// finish fixing
				pthread_mutex_lock(&leaderFixing_mutex);
					leaderFixing = 0;
					pthread_mutex_unlock(&leaderFixing_mutex);
					//ctrl = DUPLICATION_COMPLETE;
					//send_data((int)acceptfd, &ctrl, sizeof(char));	
				}
				else {			
					pthread_mutex_unlock(&leaderFixing_mutex);
					//while(leaderFixing);
				}
				break;

			case UPDATE_LIVE_HOSTS:
				// update livehosts.
				printf("%s (UPDATE_LIVE_HOSTS)-> Attempt to update live machines\n", __func__);	
				// copy back
				pthread_mutex_lock(&liveHosts_mutex);
			  recv_data((int)acceptfd, liveHosts, sizeof(int)*numHostsInSystem);
				recv_data((int)acceptfd, locateObjHosts, sizeof(unsigned int)*numHostsInSystem*2);
				pthread_mutex_unlock(&liveHosts_mutex);
				liveHostsValid = 1;
				numLiveHostsInSystem = getNumLiveHostsInSystem();
				printHostsStatus();
			  printf("%s (UPDATE_LIVE_HOSTS)-> Finished\n", __func__);	
				//exit(0);
				break;

			case DUPLICATE_ORIGINAL:
				printf("%s (DUPLICATE_ORIGINAL)-> Attempt to duplicate original objects\n", __func__);	
				//object store stuffffff
				recv_data((int)acceptfd, &mid, sizeof(unsigned int));
				tempsize = mhashGetDuplicate(&dupeptr, 0);

				//send control and dupes after
				ctrl = RECEIVE_DUPES;

				if((sd = getSockWithLock(transRequestSockPool, mid)) < 0) {
					printf("DUPLICATE_ORIGINAL: socket create error\n");
					//usleep(1000);
				}
				printf("sd:%d, tempsize:%d, dupeptrfirstvalue:%d\n", sd, tempsize, *((unsigned int *)(dupeptr)));

				send_data(sd, &ctrl, sizeof(char));
				send_data(sd, dupeptr, tempsize);
				
				recv_data(sd, &response, sizeof(char));
				if(response != DUPLICATION_COMPLETE) {
					//fail message
				}
				ctrl = DUPLICATION_COMPLETE;
				send_data((int)acceptfd, &ctrl, sizeof(char));
				printf("%s (DUPLICATE_ORIGINAL)-> Finished\n", __func__);	
			 freeSockWithLock(transRequestSockPool, mid, sd);
				break;

			case DUPLICATE_BACKUP:
				printf("%s (DUPLICATE_BACKUP)-> Attempt to duplicate backup objects\n", __func__);
				//object store stuffffff
				recv_data((int)acceptfd, &mid, sizeof(unsigned int));
				tempsize = mhashGetDuplicate(&dupeptr, 1);

				printf("tempsize:%d, dupeptrfirstvalue:%d\n", tempsize, *((unsigned int *)(dupeptr)));
				//send control and dupes after
				ctrl = RECEIVE_DUPES;
				if((sd = getSockWithLock(transRequestSockPool, mid)) < 0) {
					printf("DUPLICATE_BACKUP: socket create error\n");
					//usleep(1000);
				}
				
				printf("sd:%d, tempsize:%d, dupeptrfirstvalue:%d\n", sd, tempsize, *((unsigned int *)(dupeptr)));
				send_data(sd, &ctrl, sizeof(char));
				send_data(sd, dupeptr, tempsize);
				recv_data(sd, &response, sizeof(char));
				if(response != DUPLICATION_COMPLETE) {
					//fail message
				}
				ctrl = DUPLICATION_COMPLETE;
				send_data((int)acceptfd, &ctrl, sizeof(char));
				printf("%s (DUPLICATE_BACKUP)-> Finished\n", __func__);	
				
			 freeSockWithLock(transRequestSockPool, mid, sd);
				break;

			case RECEIVE_DUPES:
				if((val = readDuplicateObjs((int)acceptfd)) != 0) {
					printf("Error: In readDuplicateObjs() %s, %d\n", __FILE__, __LINE__);
					pthread_exit(NULL);
				}
				ctrl = DUPLICATION_COMPLETE;
				send_data((int)acceptfd, &ctrl, sizeof(char));
				break;

			case PAXOS_PREPARE:
				recv_data((int)acceptfd, &val, sizeof(int));
				printf("%s (PAXOS_PREPARE)-> prop n:%d, n_h:%d\n", __func__, val, n_h);
				if (val <= n_h) {
					control = PAXOS_PREPARE_REJECT;
					send_data((int)acceptfd, &control, sizeof(char));
				}
				else {
					n_h = val;
					control = PAXOS_PREPARE_OK;
				printf("%s (PAXOS_PREPARE)-> n_h now:%d, sending OK\n", __func__, n_h);
					send_data((int)acceptfd, &control, sizeof(char));
					send_data((int)acceptfd, &n_a, sizeof(int));
					send_data((int)acceptfd, &v_a, sizeof(int));
				}
				break;

			case PAXOS_ACCEPT:
				recv_data((int)acceptfd, &n, sizeof(int));
				recv_data((int)acceptfd, &v, sizeof(int));
				if (n < n_h) {
					control = PAXOS_ACCEPT_REJECT;
					send_data((int)acceptfd, &control, sizeof(char));
				}
				else {
					n_a = n;
					v_a = v;
					n_h = n;
					control = PAXOS_ACCEPT_OK;
					send_data((int)acceptfd, &control, sizeof(char));
				}
				break;

			case PAXOS_LEARN:
				recv_data((int)acceptfd, &v, sizeof(int));
				leader = v_a;
				paxosRound++;
				printf("%s (PAXOS_LEARN)-> This is my leader!: [%s]\n", __func__, midtoIPString(leader));
				break;

			case DELETE_LEADER:
				v_a = 0;
				break;

			default:
				printf("Error: dstmAccept() Unknown opcode %d at %s, %d\n", control, __FILE__, __LINE__);
		}
	}
	printf("%s-> Exiting\n", __func__); fflush(stdout);
closeconnection:
	/* Close connection */
	if (close((int)acceptfd) == -1)
		perror("close");
	pthread_exit(NULL);
}

int readDuplicateObjs(int acceptfd) {
	int numoid, i, size, tmpsize;
	unsigned int oid;
	void *dupeptr, *ptrcreate, *ptr;
	objheader_t *header;

	printf("%s-> Start\n", __func__);
	recv_data((int)acceptfd, &numoid, sizeof(unsigned int));
	recv_data((int)acceptfd, &size, sizeof(int));	
	// do i need array of oids?
	// answer: no! now get to work
	printf("%s-> numoid:%d, size:%d\n", __func__, numoid, size);
	if(numoid != 0) {
		if ((dupeptr = calloc(1, size)) == NULL) {
			printf("calloc error for duplicated objects %s, %d\n", __FILE__, __LINE__);
			return 1;
		}

		recv_data((int)acceptfd, dupeptr, size);
		ptr = dupeptr;
		for(i = 0; i < numoid; i++) {
			header = (objheader_t *)ptr;
			oid = OID(header);
			GETSIZE(tmpsize, header);
			tmpsize += sizeof(objheader_t);
			printf("%s-> oid being received/backed:%u, version:%d, type:%d\n", __func__, oid, header->version, TYPE(header));
			printf("STATUSPTR(header):%u, STATUS:%d\n", STATUSPTR(header), STATUS(header));
			pthread_mutex_lock(&mainobjstore_mutex);
			if ((ptrcreate = objstrAlloc(&mainobjstore, tmpsize)) == NULL) {
				printf("Error: readDuplicateObjs() failed objstrAlloc %s, %d\n", __FILE__, __LINE__);
				pthread_mutex_unlock(&mainobjstore_mutex);
				return 1;
			}
			pthread_mutex_unlock(&mainobjstore_mutex);
	    memcpy(ptrcreate, header, tmpsize);

			mhashInsert(oid, ptrcreate);
			ptr += tmpsize;
		}

		printf("%s-> End\n", __func__);
		return 0;
	}
	else {
		printf("%s-> No objects duplicated\n", __func__);
		return 0;
	}
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
	printf("%s-> Entering\n", __func__);

  /* Read fixed_data_t data structure */
  size = sizeof(fixed) - 1;
  ptr = (char *)&fixed;
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
  if (oidmod == NULL) {
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

	printf("%s-> num oid read = %d, oids modified = %d, size = %d\n", __func__, fixed.numread,  fixed.nummod, size); fflush(stdout);
// sleep(1); 
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
	printf("%s-> Exiting\n", __func__);

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
  int i = 0, val;

	printf("%s-> Entering\n", __func__);
  /* Send reply to the Coordinator */
  if((retval = handleTransReq(fixed, transinfo, listmid, objread, modptr,acceptfd)) == 0 ) {
    printf("Error: In handleTransReq() %s, %d\n", __FILE__, __LINE__);
	  printf("DEBUG-> Exiting processClientReq, line = %d\n", __LINE__);
    return 1;
  }

	recv_data((int)acceptfd, &control, sizeof(char));
	/* Process the new control message */
	switch(control) {
		case TRANS_ABORT:
			if (fixed->nummod > 0)
				free(modptr);
			/* Unlock objects that was locked due to this transaction */
			int useWriteUnlock = 0;
			for(i = 0; i< transinfo->numlocked; i++) {
				if(transinfo->objlocked[i] == -1) {
					useWriteUnlock = 1;
					continue;
				}
				if((header = mhashSearch(transinfo->objlocked[i])) == NULL) {
					printf("mhashSearch returns NULL at %s, %d\n", __FILE__, __LINE__); // find the header address
					printf("%s-> Exiting, line:%d\n", __func__, __LINE__);
					return 1;
				}
				if(useWriteUnlock) {
					write_unlock(STATUSPTR(header));
				} else {
					read_unlock(STATUSPTR(header));
				}
			}
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
				printf("%s-> Exiting, line:%d\n", __func__, __LINE__);
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
	printf("%s-> Exiting, line:%d\n", __func__, __LINE__);

  return 0;
}

/* This function increments counters while running a voting decision on all objects involved
 * in TRANS_REQUEST and If a TRANS_DISAGREE sends the response immediately back to the coordinator */
char handleTransReq(fixed_data_t *fixed, trans_commit_data_t *transinfo, unsigned int *listmid, char *objread, void *modptr, int acceptfd) {
  int val, i = 0, j;
  unsigned short version;
  char control = 0, *ptr;
  unsigned int oid;
  unsigned int *oidnotfound, *oidlocked, *oidvernotmatch;
  objheader_t *headptr;

  /* Counters and arrays to formulate decision on control message to be sent */
  oidnotfound = (unsigned int *) calloc(fixed->numread + fixed->nummod, sizeof(unsigned int));
  oidlocked = (unsigned int *) calloc(fixed->numread + fixed->nummod + 1, sizeof(unsigned int));
  oidvernotmatch = (unsigned int *) calloc(fixed->numread + fixed->nummod, sizeof(unsigned int));
  int objnotfound = 0, objlocked = 0, objvernotmatch = 0;
  int v_nomatch = 0, v_matchlock = 0, v_matchnolock = 0;
  int numBytes = 0;
  /* modptr points to the beginning of the object store
   * created at the Pariticipant.
   * Object store holds the modified objects involved in the transaction request */
  ptr = (char *) modptr;

  /* Process each oid in the machine pile/ group per thread */
  for (i = 0; i < fixed->numread + fixed->nummod; i++) {
    if (i < fixed->numread) { //Objs only read and not modified
      int incr = sizeof(unsigned int) + sizeof(unsigned short); // Offset that points to next position in the objread array
      incr *= i;
      oid = *((unsigned int *)(objread + incr));
      incr += sizeof(unsigned int);
      version = *((unsigned short *)(objread + incr));
      getCommitCountForObjRead(oidnotfound, oidlocked, oidvernotmatch, &objnotfound, &objlocked, &objvernotmatch,
                               &v_matchnolock, &v_matchlock, &v_nomatch, &numBytes, &control, oid, version);
    } else {  //Objs modified
      if(i == fixed->numread) {
	oidlocked[objlocked++] = -1;
      }
      int tmpsize;
      headptr = (objheader_t *) ptr;
      oid = OID(headptr);
      version = headptr->version;
      GETSIZE(tmpsize, headptr);
      ptr += sizeof(objheader_t) + tmpsize;
      getCommitCountForObjMod(oidnotfound, oidlocked, oidvernotmatch, &objnotfound,
                              &objlocked, &objvernotmatch, &v_matchnolock, &v_matchlock, &v_nomatch,
                              &numBytes, &control, oid, version);
    }
  }

  /* send TRANS_DISAGREE and objs*/
	if(v_nomatch > 0) {
#ifdef CACHE
		char *objs = calloc(1, numBytes);
		int j, offset = 0;
		for(j = 0; j<objvernotmatch; j++) {
			objheader_t *header = mhashSearch(oidvernotmatch[j]);
			int size = 0;
			GETSIZE(size, header);
			size += sizeof(objheader_t);
			memcpy(objs+offset, header, size);
			offset += size;
		}
#endif
		if (objlocked > 0) {
			int useWriteUnlock = 0;
			for(j = 0; j < objlocked; j++) {
				if(oidlocked[j] == -1) {
					useWriteUnlock = 1;
					continue;
				}
				if((headptr = mhashSearch(oidlocked[j])) == NULL) {
					printf("mhashSearch returns NULL at %s, %d\n", __FILE__, __LINE__);
					return 0;
				}
				if(useWriteUnlock) {
					write_unlock(STATUSPTR(headptr));
				} else {
					read_unlock(STATUSPTR(headptr));
				}
			}
			free(oidlocked);
		}
		printf("control = %d, file = %s, line = %d\n", (int)control, __FILE__, __LINE__);

		send_data(acceptfd, &control, sizeof(char));
#ifdef CACHE
		send_data(acceptfd, &numBytes, sizeof(int));
		send_data(acceptfd, objs, numBytes);
if(timeoutFlag || timeoutFlag) {
printf("send_data: remote machine dead, line:%d\n", __LINE__);
timeoutFlag = 0;
timeoutFlag = 0;
exit(1);
}

		transinfo->objvernotmatch = oidvernotmatch;
		transinfo->numvernotmatch = objvernotmatch;
		free(objs);
		free(transinfo->objvernotmatch);
#endif
		return control;
  }

  /* Decide what control message to send to Coordinator */
  if ((control = decideCtrlMessage(fixed, transinfo, &v_matchnolock, &v_matchlock, &v_nomatch, &objnotfound, &objlocked,
                                   modptr, oidnotfound, oidlocked, acceptfd)) == 0) {
    printf("Error: In decideCtrlMessage() %s, %d\n", __FILE__, __LINE__);
    return 0;
  }
  return control;
}

/* Update Commit info for objects that are modified */
void getCommitCountForObjMod(unsigned int *oidnotfound, unsigned int *oidlocked,
                             unsigned int *oidvernotmatch, int *objnotfound, int *objlocked, int *objvernotmatch,
                             int *v_matchnolock, int *v_matchlock, int *v_nomatch, int *numBytes,
                             char *control, unsigned int oid, unsigned short version) {
  void *mobj;
  /* Check if object is still present in the machine since the beginning of TRANS_REQUEST */
	//printf("version number: %d\n", version);
#ifdef RECOVERY
  if(version == 1) {
		(*v_matchnolock)++;
		printf("*backup object* oid:%u\n", oid);
		return;
	}
#endif

	if ((mobj = mhashSearch(oid)) == NULL) {    /* Obj not found */
		printf("Obj not found: %s() oid = %d, type = %d\t\n", __func__, OID(mobj), TYPE((objheader_t *)mobj));
		fflush(stdout);
		/* Save the oids not found and number of oids not found for later use */
		oidnotfound[*objnotfound] = oid;
		(*objnotfound)++;
	} else {     /* If Obj found in machine (i.e. has not moved) */
		printf("Obj found: %s() oid = %d, type = %d\t\n", __func__, OID(mobj), TYPE((objheader_t *)mobj));
		fflush(stdout);
		/* Check if Obj is locked by any previous transaction */
		if (write_trylock(STATUSPTR(mobj))) { // Can acquire write lock

		printf("****%s->Trying to acquire 'remote' writelock for oid:%d, version:%d\n", __func__, oid, version);
			printf("this version: %d, mlookup version: %d\n", version, ((objheader_t *)mobj)->version);
			if (version == ((objheader_t *)mobj)->version) { /* match versions */
				(*v_matchnolock)++;
			} else { /* If versions don't match ...HARD ABORT */
				(*v_nomatch)++;
				oidvernotmatch[*objvernotmatch] = oid;
				(*objvernotmatch)++;
				int size;
				GETSIZE(size, mobj);
				size += sizeof(objheader_t);
				*numBytes += size;
				/* Send TRANS_DISAGREE to Coordinator */
				*control = TRANS_DISAGREE;
				//printf("%s() oid = %d, type = %d\t", __func__, OID(mobj), TYPE((objheader_t *)mobj));
			}
			//Keep track of oid locked
			oidlocked[(*objlocked)++] = OID(((objheader_t *)mobj));
		} else {  //we are locked
			if (version == ((objheader_t *)mobj)->version) {     /* Check if versions match */
				(*v_matchlock)++;
			} else { /* If versions don't match ...HARD ABORT */
				(*v_nomatch)++;
				oidvernotmatch[*objvernotmatch] = oid;
				(*objvernotmatch)++;
				int size;
				GETSIZE(size, mobj);
				size += sizeof(objheader_t);
				*numBytes += size;
				*control = TRANS_DISAGREE;
				//printf("%s() oid = %d, type = %d\t", __func__, OID(mobj), TYPE((objheader_t *)mobj));
			}
		}
  }
	printf("oid: %u, v_matchnolock: %d, v_matchlock: %d, v_nomatch: %d\n", oid, *v_matchnolock, *v_matchlock, *v_nomatch);
}

/* Update Commit info for objects that are read */
void getCommitCountForObjRead(unsigned int *oidnotfound, unsigned int *oidlocked, unsigned int *oidvernotmatch,
                              int *objnotfound, int *objlocked, int * objvernotmatch, int *v_matchnolock, int *v_matchlock,
                              int *v_nomatch, int *numBytes, char *control, unsigned int oid, unsigned short version) {
  void *mobj;
  /* Check if object is still present in the machine since the beginning of TRANS_REQUEST */
  //printf("version number: %d\n", version);
#ifdef RECOVERY
  if(version == 1) {
		(*v_matchnolock)++;
		printf("*backup object* oid:%u\n", oid);
		return;
	}
#endif
	if ((mobj = mhashSearch(oid)) == NULL) {    /* Obj not found */
	printf("Obj not found: %s() file:%s oid = %d, type = %d\t\n", __func__, __FILE__, OID(mobj), TYPE((objheader_t *)mobj));
	fflush(stdout);
    /* Save the oids not found and number of oids not found for later use */
    oidnotfound[*objnotfound] = oid;
    (*objnotfound)++;
  } else {     /* If Obj found in machine (i.e. has not moved) */
	printf("Obj found: %s() file:%s oid = %d, type = %d\t\n", __func__, __FILE__, OID(mobj), TYPE((objheader_t *)mobj));
	fflush(stdout);
    /* Check if Obj is locked by any previous transaction */
    if (read_trylock(STATUSPTR(mobj))) { //Can further acquire read locks
      if (version == ((objheader_t *)mobj)->version) { /* match versions */
	(*v_matchnolock)++;
      } else { /* If versions don't match ...HARD ABORT */
	(*v_nomatch)++;
	oidvernotmatch[(*objvernotmatch)++] = oid;
	int size;
	GETSIZE(size, mobj);
	size += sizeof(objheader_t);
	*numBytes += size;
	/* Send TRANS_DISAGREE to Coordinator */
	*control = TRANS_DISAGREE;
	//printf("%s() oid = %d, type = %d\t", __func__, OID(mobj), TYPE((objheader_t *)mobj));
      }
      //Keep track of oid locked
      oidlocked[(*objlocked)++] = OID(((objheader_t *)mobj));
    } else { /* Some other transaction has aquired a write lock on this object */
      if (version == ((objheader_t *)mobj)->version) { /* Check if versions match */
	(*v_matchlock)++;
      } else { /* If versions don't match ...HARD ABORT */
	(*v_nomatch)++;
	oidvernotmatch[*objvernotmatch] = oid;
	(*objvernotmatch)++;
	int size;
	GETSIZE(size, mobj);
	size += sizeof(objheader_t);
	*numBytes += size;
	*control = TRANS_DISAGREE;
	//printf("%s() oid = %d, type = %d\t", __func__, OID(mobj), TYPE((objheader_t *)mobj));
      }
    }
  }
	printf("oid: %u, v_matchnolock: %d, v_matchlock: %d, v_nomatch: %d\n", oid, *v_matchnolock, *v_matchlock, *v_nomatch);
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
		printf("control = %d, file = %s, line = %d\n", (int)control, __FILE__, __LINE__);
    send_data(acceptfd, &control, sizeof(char));
if(timeoutFlag || timeoutFlag) {
printf("send_data: remote machine dead, line:%d\n", __LINE__);
timeoutFlag = 0;
timeoutFlag = 0;
exit(1);
}

		printf("finished sending control\n");
  }
  /* Condition to send TRANS_SOFT_ABORT */
  if((*(v_matchlock) > 0 && *(v_nomatch) == 0) || (*(objnotfound) > 0 && *(v_nomatch) == 0)) {
    control = TRANS_SOFT_ABORT;

		printf("control = %d, file = %s, line = %d\n", (int)control, __FILE__, __LINE__);
    /* Send control message */
    send_data(acceptfd, &control, sizeof(char));

    /*  FIXME how to send objs Send number of oids not found and the missing oids if objects are missing in the machine */
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
  void *ptrcreate;
	printf("DEBUG-> Entering transCommitProcess, dstmserver.c\n");
	printf("nummod: %d, numlocked: %d\n", nummod, numlocked);

  /* Process each modified object saved in the mainobject store */
  for(i = 0; i < nummod; i++) {
    if((header = (objheader_t *) mhashSearch(oidmod[i])) == NULL) {
#ifndef RECOVERY
      printf("Error: mhashsearch returns NULL at dstmserver.c %d\n", __LINE__);
			return 1;
#else
			printf("DEBUG->*backup* i:%d, nummod:%d\n", i, nummod);
			header = (objheader_t *)(modptr+offset);
			header->version += 1;
			header->isBackup = 1;
      printf("oid: %u, new header version: %d\n", oidmod[i], header->version);
			GETSIZE(tmpsize, header);
			tmpsize += sizeof(objheader_t);
			pthread_mutex_lock(&mainobjstore_mutex);
			if ((ptrcreate = objstrAlloc(&mainobjstore, tmpsize)) == NULL) {
				printf("Error: transComProcess() failed objstrAlloc %s, %d\n", __FILE__, __LINE__);
				pthread_mutex_unlock(&mainobjstore_mutex);
				return 1;
			}
			pthread_mutex_unlock(&mainobjstore_mutex);
			/* Initialize read and write locks  */
			initdsmlocks(STATUSPTR(header));
			memcpy(ptrcreate, header, tmpsize);
			mhashInsert(oidmod[i], ptrcreate);

			offset += tmpsize;
#endif
    }
		else{

    GETSIZE(tmpsize,header);

    {
      struct ___Object___ *dst=(struct ___Object___*)((char*)header+sizeof(objheader_t));
      struct ___Object___ *src=(struct ___Object___*)((char*)modptr+sizeof(objheader_t)+offset);
      dst->type=src->type;
      dst->___cachedCode___=src->___cachedCode___;
      dst->___cachedHash___=src->___cachedHash___;
      memcpy(&dst[1], &src[1], tmpsize-sizeof(struct ___Object___));
    }
    header->version += 1;
    printf("oid: %u, new header version: %d\n", oidmod[i], header->version);
    /* If threads are waiting on this object to be updated, notify them */
    if(header->notifylist != NULL) {
      notifyAll(&header->notifylist, OID(header), header->version);
    }
    offset += sizeof(objheader_t) + tmpsize;
  }
}
  if (nummod > 0)
    free(modptr);

  /* Unlock locked objects */
  int useWriteUnlock = 0;
  for(i = 0; i < numlocked; i++) {
    if(oidlocked[i] == -1) {
      useWriteUnlock = 1;
      continue;
    }
    if((header = (objheader_t *) mhashSearch(oidlocked[i])) == NULL) {
      printf("Error: mhashsearch returns NULL at %s, %d\n", __FILE__, __LINE__);
      return 1;
    }
		
		printf("header oid:%d, version:%d, useWriteUnlock:%d\n", OID(header), header->version, useWriteUnlock);
    if(useWriteUnlock) {
      write_unlock(STATUSPTR(header));
    } else {
      read_unlock(STATUSPTR(header));
    }
  }
  //TODO Update location lookup table
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
    if ((header = mhashSearch(oid)) == NULL) { /* Obj not found */
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
      *((int *)(sendbuffer + incr)) = size;
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
	if(TYPE(header) >= NUMCLASSES) {
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
	} else { /* Obj Found */
	  int incr = 0;
	  GETSIZE(objsize, header);
	  size = sizeof(int) + sizeof(char) + sizeof(unsigned int) + sizeof(objheader_t) + objsize;
	  char sendbuffer[size];
	  *((int *)(sendbuffer + incr)) = size;
	  incr += sizeof(int);
	  *((char *)(sendbuffer + incr)) = OBJECT_FOUND;
	  incr += sizeof(char);
	  *((unsigned int *)(sendbuffer+incr)) = oid;
	  incr += sizeof(unsigned int);
	  memcpy(sendbuffer + incr, header, objsize + sizeof(objheader_t));

	  control = TRANS_PREFETCH_RESPONSE;
	  sendPrefetchResponse(sd, &control, sendbuffer, &size);
	}
      } //end of for
    }
  } //end of while
    //Release socket
  if (mid!=-1)
    freeSockWithLock(transPResponseSocketPool, mid, sd);

  return 0;
}

void sendPrefetchResponse(int sd, char *control, char *sendbuffer, int *size) {
		printf("control = %d, file = %s, line = %d\n", (int)control, __FILE__, __LINE__);
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
      if (write_trylock(STATUSPTR(header))) { // Can acquire write lock
	newversion = header->version;
	if(newversion == *(versionarry + i)) {
	  //Add to the notify list
	  if((header->notifylist = insNode(header->notifylist, threadid, mid)) == NULL) {
	    printf("Error: Obj notify list points to NULL %s, %d\n", __FILE__, __LINE__);
	    return;
	  }
	  write_unlock(STATUSPTR(header));
	} else {
	  write_unlock(STATUSPTR(header));
	  if ((sd = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
	    perror("processReqNotify():socket()");
	    return;
	  }
	  bzero(&remoteAddr, sizeof(remoteAddr));
	  remoteAddr.sin_family = AF_INET;
	  remoteAddr.sin_port = htons(LISTEN_PORT);
	  remoteAddr.sin_addr.s_addr = htonl(mid);

	  if (connect(sd, (struct sockaddr *)&remoteAddr, sizeof(remoteAddr)) < 0) {
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
