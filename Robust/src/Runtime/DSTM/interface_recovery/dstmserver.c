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
#include "tlookup.h"
#endif

#define BACKLOG 10 //max pending connections
#define RECEIVE_BUFFER_SIZE 2048

extern int classsize[];
extern int numHostsInSystem;
extern pthread_mutex_t notifymutex;

extern unsigned int myIpAddr;
extern unsigned int *hostIpAddrs;

#ifdef RECOVERY
extern unsigned int *locateObjHosts;
extern int *liveHosts;
extern int numLiveHostsInSystem;
int clearNotifyListFlag;
#endif

objstr_t *mainobjstore;
pthread_mutex_t mainobjstore_mutex;
pthread_mutex_t lockObjHeader;
pthread_mutex_t clearNotifyList_mutex;
pthread_mutexattr_t mainobjstore_mutex_attr; /* Attribute for lock to make it a recursive lock */

sockPoolHashTable_t *transPResponseSocketPool;
extern sockPoolHashTable_t *transRequestSockPool;
extern sockPoolHashTable_t *transReadSockPool;

int failFlag = 0; //debug

#ifdef RECOVERY
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
#endif

int dstmInit(void) {
  mainobjstore = objstrCreate(DEFAULT_OBJ_STORE_SIZE);
  /* Initialize attribute for mutex */
  pthread_mutexattr_init(&mainobjstore_mutex_attr);
  pthread_mutexattr_settype(&mainobjstore_mutex_attr, PTHREAD_MUTEX_RECURSIVE_NP);
  pthread_mutex_init(&mainobjstore_mutex, &mainobjstore_mutex_attr);
  pthread_mutex_init(&lockObjHeader,NULL);

#ifdef RECOVERY
	pthread_mutex_init(&liveHosts_mutex, NULL);
	pthread_mutex_init(&leaderFixing_mutex, NULL);
  pthread_mutex_init(&clearNotifyList_mutex,NULL);
#endif

  if (mhashCreate(MHASH_SIZE, MLOADFACTOR))
    return 1;             //failure

  if (lhashCreate(HASH_SIZE, LOADFACTOR))
    return 1;             //failure

#ifdef RECOVERY
  if (thashCreate(THASH_SIZE, LOADFACTOR))
    return 1;
#endif

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

#ifdef RECOVERY
  int firsttime = 1;
  pthread_t thread_dstm_asking;
#endif
#ifdef DEBUG
  printf("Listening on port %d, fd = %d\n", LISTEN_PORT, listenfd);
#endif
  while(1) {
    int retval;
    int flag=1;
    acceptfd = accept(listenfd, (struct sockaddr *)&client_addr, &addrlength);

#ifdef RECOVERY
    if(firsttime) {
      do {
        retval = pthread_create(&thread_dstm_asking, NULL, startAsking, NULL);
      }while(retval!=0);
      firsttime=0;
      pthread_detach(thread_dstm_asking);
    }
#endif
#ifdef debug
    printf("%s -> fd accepted\n",__func__);
#endif

    setsockopt(acceptfd, IPPROTO_TCP, TCP_NODELAY, (char *) &flag, sizeof(flag));
    do {
      	retval=pthread_create(&thread_dstm_accept, NULL, dstmAccept, (void *)acceptfd);
    } while(retval!=0);
    pthread_detach(thread_dstm_accept);
  }
}

#ifdef RECOVERY
void* startAsking()
{
  unsigned int deadMachineIndex = -1;
  int i;
  int validHost;
  int *socklist;
  int sd;
#ifdef DEBUG
  printf("%s -> Entering\n",__func__);
#endif

    socklist = (int*) calloc(numHostsInSystem,sizeof(int)); 

    for(i = 0; i< numHostsInSystem;i++) { // for 1
        if((sd = getSockWithLock(transPResponseSocketPool,hostIpAddrs[i])) < 0) {
          printf("%s -> Cannot create socket connection to [%s]\n",__func__,midtoIPString(hostIpAddrs[i]));
          socklist[i] = -1;
        }
        else { // else 1
          socklist[i] = sd;
        }   // end of else 1
    }
  
    while(1) {

     deadMachineIndex = checkIfAnyMachineDead(socklist);

      // free socket of dead machine
      if(deadMachineIndex >= 0) { // if 2
#ifdef DEBUG
        printf("%s -> Dead Machine : %s\n",__func__, midtoIPString(hostIpAddrs[deadMachineIndex]));
#endif
        restoreDuplicationState(hostIpAddrs[deadMachineIndex]);
        freeSockWithLock(transPResponseSocketPool, hostIpAddrs[deadMachineIndex], socklist[deadMachineIndex]);
        socklist[deadMachineIndex] = -1;
      } // end of if 2
    } // end of while 1
#ifdef DEBUG
   printf("%s -> Exiting\n",__func__);
#endif
}


unsigned int checkIfAnyMachineDead(int* socklist)
{
  int timeout = 0;
  int i;
  char control = RESPOND_LIVE;
  char response;
#ifdef DEBUG
  printf("%s -> Entering\n",__func__);
#endif
  
  while(1){
    for(i = 0; i< numHostsInSystem;i++) {
#ifdef DEBUG
      printf("%s -> socklist[%d] = %d\n",__func__,i,socklist[i]);
#endif
      if(socklist[i] > 0) {
        send_data(socklist[i], &control,sizeof(char));

        if(recv_data(socklist[i], &response, sizeof(char)) < 0) {
          // if machine is dead, returns index of socket
#ifdef DEBUG
          printf("%s -> Machine dead detecteed\n",__func__);
#endif
          return i;
        }
        else {
          // machine responded
          if(response != LIVE) {
#ifdef DEBUG
            printf("%s -> Machine dead detected\n",__func__);
#endif
            return i;
          }
        } // end else
      }// end if(socklist[i]
    } // end for()

    clearDeadThreadsNotification();

    sleep(numLiveHostsInSystem);  // wait for seconds for next checking
  } // end while(1)
}
#endif


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
  unsigned int transIDreceived;
  char decision;
  struct sockaddr_in remoteAddr;

#ifdef DEBUG
	printf("%s-> Entering dstmAccept\n", __func__);	fflush(stdout);
#endif
	/* Receive control messages from other machines */
	while(1) {
		int ret=recv_data_errorcode((int)acceptfd, &control, sizeof(char));
    dupeptr = NULL;

		if (ret==0)
			break;
		if (ret==-1) {
#ifdef DEBUG
			printf("DEBUG -> RECV Error!.. retrying\n");
#endif
	//		exit(0);
			break;
		}
#ifdef DEBUG
		printf("%s-> dstmAccept control = %d\n", __func__, (int)control);
#endif
		switch(control) {
			case READ_REQUEST:
#ifdef DEBUG
        printf("control -> READ_REQUEST\n");
#endif
				/* Read oid requested and search if available */
				recv_data((int)acceptfd, &oid, sizeof(unsigned int));
				while((srcObj = mhashSearch(oid)) == NULL) {
					int ret;
//          printf("HERE!!\n");
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
				} else {
					// Type
					char msg[]={OBJECT_FOUND, 0, 0, 0, 0};
					*((int *)&msg[1])=size;
					send_data(sockid, &msg, sizeof(msg));
					send_data(sockid, h, size);
				}
				break;

			case READ_MULT_REQUEST:
				break;

			case MOVE_REQUEST:
				break;

			case MOVE_MULT_REQUEST:
				break;

			case TRANS_REQUEST:
#ifdef DEBUG
        printf("control -> TRANS_REQUEST\n");
#endif
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
#ifdef RECOVERY
      case ASK_COMMIT :

        recv_data((int)acceptfd, &transIDreceived, sizeof(unsigned int));

        decision = checkDecision(transIDreceived);

        send_data((int)acceptfd,&decision,sizeof(char));

        break;
#endif
			case TRANS_PREFETCH:
#ifdef DEBUG
        printf("control -> TRANS_PREFETCH\n");
#endif
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
#ifdef DEBUG
                printf("control -> TRANS_PREFETCH_RESPONSE\n");
#endif
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
#ifdef DEBUG
        printf("control -> START_REMOTE_THREAD\n");
#endif
				recv_data((int)acceptfd, &oid, sizeof(unsigned int));
				objType = getObjType(oid);
				startDSMthread(oid, objType);
				break;

      case THREAD_NOTIFY_REQUEST:
#ifdef DEBUG
        printf("control -> THREAD_NOTIFY_REQUEST FD : %d\n",acceptfd);
#endif
        numoid = 0;
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
#ifdef DEBUG
        printf("control -> THREAD_NOTIFY_RESPONSE\n");
#endif
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
#ifdef RECOVERY
      case CLEAR_NOTIFY_LIST:
#ifdef DEBUG
        printf("control -> CLEAR_NOTIFY_LIST\n");
#endif  
        size = sizeof(unsigned int);
        if((buffer = calloc(1,size)) == NULL) {
          printf("%s() Caclloc error at CLEAR_NOTIFY_LIST\n");
          pthread_exit(NULL);
        }

        recv_data((int)acceptfd,buffer, size);

        oid = *((unsigned int *)buffer);

        pthread_mutex_lock(&clearNotifyList_mutex);
        if(clearNotifyListFlag == 0) {
          clearNotifyListFlag = 1;
          pthread_mutex_unlock(&clearNotifyList_mutex);
          clearNotifyList(oid);
        }
        else {
          pthread_mutex_unlock(&clearNotifyList_mutex);
        }
        free(buffer);
        break;
#endif
			case CLOSE_CONNECTION:
#ifdef DEBUG
        printf("control -> CLOSE_CONNECTION\n");
#endif
				goto closeconnection;

#ifdef RECOVERY
			case RESPOND_LIVE:
#ifdef DEBUG
        printf("control -> RESPOND_LIVE\n");
#endif
				ctrl = LIVE;
				send_data((int)acceptfd, &ctrl, sizeof(ctrl));
#ifdef DEBUG
				printf("%s (RESPOND_LIVE)-> Sending LIVE!\n", __func__);
#endif
				break;
#endif
#ifdef RECOVERY
			case REMOTE_RESTORE_DUPLICATED_STATE:
#ifdef DEBUG
        printf("control -> REMOTE_RESTORE_DUPLICATED_STATE\n");
#endif
				recv_data((int)acceptfd, &mid, sizeof(unsigned int));
				if(!liveHosts[findHost(mid)]) {
#ifdef DEBUG
          printf("%s (REMOTE_RESTORE_DUPLICATED_STATE) -> already fixed\n",__func__);
#endif
					break;
        }
				pthread_mutex_lock(&leaderFixing_mutex);
				if(!leaderFixing) {
					leaderFixing = 1;
					pthread_mutex_unlock(&leaderFixing_mutex);
					// begin fixing
					updateLiveHosts();
					duplicateLostObjects(mid);
				if(updateLiveHostsCommit() != 0) {
					printf("error updateLiveHostsCommit()\n");
					exit(1);
				}

        // finish fixing
				pthread_mutex_lock(&leaderFixing_mutex);
				leaderFixing = 0;
				pthread_mutex_unlock(&leaderFixing_mutex);
				}
				else {
					pthread_mutex_unlock(&leaderFixing_mutex);
#ifdef DEBUG
          printf("%s (REMOTE_RESTORE_DUPLICATED_STATE -> LEADER is already fixing\n",__func__);
#endif
          sleep(WAIT_TIME);
				}
				break;
#endif
#ifdef RECOVERY
			case UPDATE_LIVE_HOSTS:
#ifdef DEBUG
        printf("control -> UPDATE_LIVE_HOSTS\n");
#endif
				// copy back
				pthread_mutex_lock(&liveHosts_mutex);
			  recv_data((int)acceptfd, liveHosts, sizeof(int)*numHostsInSystem);
				recv_data((int)acceptfd, locateObjHosts, sizeof(unsigned int)*numHostsInSystem*2);
				pthread_mutex_unlock(&liveHosts_mutex);
				numLiveHostsInSystem = getNumLiveHostsInSystem();
#ifdef DEBUG
				printHostsStatus();
			  printf("%s (UPDATE_LIVE_HOSTS)-> Finished\n", __func__);	
#endif
				//exit(0);
				break;
#endif

#ifdef RECOVERY
			case DUPLICATE_ORIGINAL:
#ifdef DEBUG
        printf("control -> DUPLICATE_ORIGINAL\n");
				printf("%s (DUPLICATE_ORIGINAL)-> Attempt to duplicate original objects\n", __func__);	
#endif
				//object store stuffffff
				recv_data((int)acceptfd, &mid, sizeof(unsigned int));
				tempsize = mhashGetDuplicate(&dupeptr, 0);

				//send control and dupes after
				ctrl = RECEIVE_DUPES;

        if((sd = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
          perror("ORIGINAL : ");
          exit(0);
        }

        bzero(&remoteAddr, sizeof(remoteAddr));
        remoteAddr.sin_family = AF_INET;
        remoteAddr.sin_port = htons(LISTEN_PORT);
        remoteAddr.sin_addr.s_addr = htonl(mid);

        if(connect(sd, (struct sockaddr *)&remoteAddr, sizeof(remoteAddr))<0) {
          printf("ORIGINAL ERROR : %s\n",strerror(errno));
          exit(0);
        }
        else {
  				send_data(sd, &ctrl, sizeof(char));
	  			send_data(sd, dupeptr, tempsize);

  				recv_data(sd, &response, sizeof(char));
#ifdef DEBUG
          printf("%s ->response : %d  -  %d\n",__func__,response,DUPLICATION_COMPLETE);
#endif
		  		if(response != DUPLICATION_COMPLETE) {
#ifdef DEBUG
           printf("%s(DUPLICATION_ORIGINAL) -> DUPLICATION FAIL\n",__func__);
#endif
				  //fail message
           exit(0);
  				}

          close(sd);
        }
        free(dupeptr);

        ctrl = DUPLICATION_COMPLETE;
				send_data((int)acceptfd, &ctrl, sizeof(char));
#ifndef DEBUG
				printf("%s (DUPLICATE_ORIGINAL)-> Finished\n", __func__);	
#endif
				break;

			case DUPLICATE_BACKUP:
#ifndef DEBUG
        printf("control -> DUPLICATE_BACKUP\n");
				printf("%s (DUPLICATE_BACKUP)-> Attempt to duplicate backup objects\n", __func__);
#endif
				//object store stuffffff
				recv_data((int)acceptfd, &mid, sizeof(unsigned int));


				tempsize = mhashGetDuplicate(&dupeptr, 1);

				//send control and dupes after
				ctrl = RECEIVE_DUPES;

        if((sd = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
          perror("BACKUP : ");
          exit(0);
        }

         bzero(&remoteAddr, sizeof(remoteAddr));                                       
         remoteAddr.sin_family = AF_INET;                                              
         remoteAddr.sin_port = htons(LISTEN_PORT);                                     
         remoteAddr.sin_addr.s_addr = htonl(mid);                                      
                                                                                       
         if(connect(sd, (struct sockaddr *)&remoteAddr, sizeof(remoteAddr))<0) {       
           printf("BACKUP ERROR : %s\n",strerror(errno));
           exit(0);
         }                                                                             
         else {                                                                        
          send_data(sd, &ctrl, sizeof(char));
  				send_data(sd, dupeptr, tempsize);
          
          recv_data(sd, &response, sizeof(char));
#ifdef DEBUG
          printf("%s ->response : %d  -  %d\n",__func__,response,DUPLICATION_COMPLETE);
#endif
		  		if(response != DUPLICATION_COMPLETE) {
#ifndef DEBUG
            printf("%s(DUPLICATION_BACKUP) -> DUPLICATION FAIL\n",__func__);
#endif
            exit(0);
          }

          close(sd);
         }

        free(dupeptr);

				ctrl = DUPLICATION_COMPLETE;
				send_data((int)acceptfd, &ctrl, sizeof(char));
#ifndef DEBUG
				printf("%s (DUPLICATE_BACKUP)-> Finished\n", __func__);	
#endif
				
				break;

			case RECEIVE_DUPES:
#ifndef DEBUG
        printf("control -> RECEIVE_DUPES sd : %d\n",(int)acceptfd);
#endif
				if((readDuplicateObjs((int)acceptfd)) != 0) {
					printf("Error: In readDuplicateObjs() %s, %d\n", __FILE__, __LINE__);
					pthread_exit(NULL);
				}

				ctrl = DUPLICATION_COMPLETE;
				send_data((int)acceptfd, &ctrl, sizeof(char));
#ifndef DEBUG
        printf("%s (RECEIVE_DUPES) -> Finished\n",__func__);
#endif
				break;
#endif
#ifdef RECOVERY
			case PAXOS_PREPARE:
#ifdef DEBUG
        printf("control -> PAXOS_PREPARE\n");
#endif
				recv_data((int)acceptfd, &val, sizeof(int));
				if (val <= n_h) {
					control = PAXOS_PREPARE_REJECT;
					send_data((int)acceptfd, &control, sizeof(char));
				}
				else {
					n_h = val;
					control = PAXOS_PREPARE_OK;
                    
					send_data((int)acceptfd, &control, sizeof(char));
					send_data((int)acceptfd, &n_a, sizeof(int));
					send_data((int)acceptfd, &v_a, sizeof(int));
				}
				break;

			case PAXOS_ACCEPT:
#ifdef DEBUG
        printf("control -> PAXOS_ACCEPT\n");
#endif
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
#ifdef DEBUG
        printf("control -> PAXOS_LEARN\n");
#endif
				recv_data((int)acceptfd, &v, sizeof(int));
				leader = v_a;
				paxosRound++;
#ifdef DEBUG
				printf("%s (PAXOS_LEARN)-> This is my leader!: [%s]\n", __func__, midtoIPString(leader));
#endif
				break;

			case DELETE_LEADER:
#ifdef DEBUG
        printf("control -> DELETE_LEADER\n");
#endif
				v_a = 0;
				break;
#endif
			default:
				printf("Error: dstmAccept() Unknown opcode %d at %s, %d\n", control, __FILE__, __LINE__);
		}
	}
#ifdef DEBUG
	printf("%s-> Exiting\n", __func__); fflush(stdout);
#endif
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

#ifdef DEBUG
	printf("%s-> Start\n", __func__);
#endif
	recv_data((int)acceptfd, &numoid, sizeof(unsigned int));
	recv_data((int)acceptfd, &size, sizeof(int));	
	// do i need array of oids?
	// answer: no! now get to work
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

#ifdef DEBUG
			printf("%s-> oid being received/backed:%u, version:%d, type:%d\n", __func__, oid, header->version, TYPE(header));
			printf("STATUSPTR(header):%u, STATUS:%d\n", STATUSPTR(header), STATUS(header));
#endif

      if(mhashSearch(oid) != NULL) {
#ifdef DEBUG
        printf("%s -> oid : %d is already in there\n",__func__,oid);
#endif

        if(header->notifylist != NULL) {
          unsigned int *listSize = (ptr + tmpsize);
          tmpsize += sizeof(unsigned int);
          tmpsize += sizeof(threadlist_t) * (*listSize);
        }
      }
      else {
  			pthread_mutex_lock(&mainobjstore_mutex);
	  		if ((ptrcreate = objstrAlloc(&mainobjstore, tmpsize)) == NULL) {
		  		printf("Error: readDuplicateObjs() failed objstrAlloc %s, %d\n", __FILE__, __LINE__);
			  	pthread_mutex_unlock(&mainobjstore_mutex);
				  return 1;
  			}
	  		pthread_mutex_unlock(&mainobjstore_mutex);
	      memcpy(ptrcreate, header, tmpsize);

        objheader_t* oPtr = (objheader_t*)ptrcreate;

        if(oPtr->notifylist != NULL) {
          oPtr->notifylist = NULL;  // reset for new list
          threadlist_t *listNode;
          unsigned int* listSize = (ptr + tmpsize);  // get number of notifylist
          unsigned int j;

          tmpsize += sizeof(unsigned int);   // skip number of notifylist 
          listNode = (threadlist_t*)(ptr + tmpsize); // get first element of address
          for(j = 0; j< *listSize; j++) {      // retreive all threadlist
            oPtr->notifylist = insNode(oPtr->notifylist,listNode[j].threadid,listNode[j].mid);
          
          }
          tmpsize += sizeof(threadlist_t) * (*listSize);

        }
    		mhashInsert(oid, ptrcreate);
      }
  		ptr += tmpsize;
		}
#ifdef DEBUG
		printf("%s-> End\n", __func__);
#endif
    free(dupeptr);
		return 0;
	}
	else {
#ifdef DEBUG
		printf("%s-> No objects duplicated\n", __func__);
#endif
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
  int timeout;

  oidmod = NULL;
#ifdef DEBUG
	printf("%s-> Entering\n", __func__);
#endif

  /* Read fixed_data_t data structure */
  size = sizeof(fixed) - 1;
  ptr = (char *)&fixed;
  fixed.control = TRANS_REQUEST;
  timeout = recv_data((int)acceptfd, ptr+1, size);

  /* Read list of mids */
  int mcount = fixed.mcount;
  size = mcount * sizeof(unsigned int);
  unsigned int listmid[mcount];
  ptr = (char *) listmid;
  timeout = recv_data((int)acceptfd, ptr, size);

  if(timeout < 0)   // coordinator failed
    return 0;

  /* Read oid and version tuples for those objects that are not modified in the transaction */
  int numread = fixed.numread;
  size = numread * (sizeof(unsigned int) + sizeof(unsigned short));
  char objread[size];
  if(numread != 0) { //If pile contains more than one object to be read,
    // keep reading all objects
    timeout = recv_data((int)acceptfd, objread, size);
  }

  /* Read modified objects */
  if(fixed.nummod != 0) {
    if ((modptr = calloc(1, fixed.sum_bytes)) == NULL) {
      printf("calloc error for modified objects %s, %d\n", __FILE__, __LINE__);
      return 1;
    }
    size = fixed.sum_bytes;
    timeout = recv_data((int)acceptfd, modptr, size);
  }

  if(timeout < 0) // coordinator failed
    return 0;

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
#ifdef DEBUG
	printf("%s-> num oid read = %d, oids modified = %d, size = %d\n", __func__, fixed.numread,  fixed.nummod, size);
#endif
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
#ifdef DEBUG
	printf("%s-> Exiting\n", __func__);
#endif

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
  unsigned int transID;
#ifdef DEBUG
	printf("%s-> Entering\n", __func__);
#endif

  /* receives transaction id */
  recv_data((int)acceptfd, &transID, sizeof(unsigned int));

  /* Send reply to the Coordinator */
  if((retval = handleTransReq(fixed, transinfo, listmid, objread, modptr,acceptfd)) == 0 ) {
    printf("Error: In handleTransReq() %s, %d\n", __FILE__, __LINE__);
	  printf("DEBUG-> Exiting processClientReq, line = %d\n", __LINE__);
    return 1;
  }

	int timeout = recv_data((int)acceptfd, &control, sizeof(char));
	/* Process the new control message */
#ifdef DEBUG
  printf("%s -> timeout = %d   control = %d\n",__func__,timeout,control); 
#endif
  
#ifdef RECOVERY
  if(timeout < 0) {  // timeout. failed to receiving data from coordinator
#ifdef DEBUG
    printf("%s -> timeout!! assumes coordinator is dead\n",__func__);
#endif
    control = receiveDecisionFromBackup(transID,fixed->mcount,listmid);
#ifdef DEBUG
    printf("%s -> received Decision %d\n",__func__,control);
#endif
  }    
  
  /* insert received control into thash for another transaction*/
  thashInsert(transID, control);
#endif
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
#ifdef DEBUG
	printf("%s-> Exiting, line:%d\n", __func__, __LINE__);
#endif

  return 0;
}

#ifdef RECOVERY
char checkDecision(unsigned int transID) 
{
#ifdef DEBUG
  printf("%s -> transID :  %u\n",__func__,transID);
#endif

  char response = thashSearch(transID);

  if(response == 0)
    return -1;
  else
    return response;
}
#endif

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
#ifdef DEBUG
      printf("%s -> oid : %u    version : %d\n",__func__,oid,version);
#endif
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
    

#ifdef DEBUG
		printf("%s -> control = %d, file = %s, line = %d\n", __func__,(int)control, __FILE__, __LINE__);
#endif

		send_data(acceptfd, &control, sizeof(char));
#ifdef CACHE
		send_data(acceptfd, &numBytes, sizeof(int));
		send_data(acceptfd, objs, numBytes);

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
#ifdef DEBUG
		printf("%s -> *backup object* oid:%u\n", __func__,oid);
#endif
		return;
	}
#endif

	if ((mobj = mhashSearch(oid)) == NULL) {    /* Obj not found */
#ifdef DEBUG
		printf("Obj not found: %s() oid = %d, type = %d\t\n", __func__, OID(mobj), TYPE((objheader_t *)mobj));
		fflush(stdout);
#endif
		/* Save the oids not found and number of oids not found for later use */
		oidnotfound[*objnotfound] = oid;
		(*objnotfound)++;
	} else {     /* If Obj found in machine (i.e. has not moved) */
		/* Check if Obj is locked by any previous transaction */
		if (write_trylock(STATUSPTR(mobj))) { // Can acquire write lock
#ifdef DEBUG
		  printf("****%s->Trying to acquire 'remote' writelock for oid:%d, version:%d\n", __func__, oid, version);
			printf("this version: %d, mlookup version: %d\n", version, ((objheader_t *)mobj)->version);
#endif
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
#ifdef DEBUG
	printf("%s -> oid: %u, v_matchnolock: %d, v_matchlock: %d, v_nomatch: %d\n",__func__,oid, *v_matchnolock, *v_matchlock, *v_nomatch);
#endif
}

/* Update Commit info for objects that are read */
void getCommitCountForObjRead(unsigned int *oidnotfound, unsigned int *oidlocked, unsigned int *oidvernotmatch,
                              int *objnotfound, int *objlocked, int * objvernotmatch, int *v_matchnolock, int *v_matchlock,
                              int *v_nomatch, int *numBytes, char *control, unsigned int oid, unsigned short version) {
  void *mobj;
  /* Check if object is still present in the machine since the beginning of TRANS_REQUEST */
  //printf("version number: %d\n", version);
#ifdef DEBUG
  printf("%s -> Entering\n",__func__);
#endif
#ifdef RECOVERY
  if(version == 1) {
		(*v_matchnolock)++;
		printf("*backup object* oid:%u\n", oid);
		return;
	}
#endif

	if ((mobj = mhashSearch(oid)) == NULL) {    /* Obj not found */
#ifdef DEBUG
    printf("%s -> Obj not found!\n",__func__);
	  printf("%s -> Obj not found: oid = %d, type = %d\t\n", __func__,OID(mobj), TYPE((objheader_t *)mobj));
  	fflush(stdout);
#endif
    /* Save the oids not found and number of oids not found for later use */
    oidnotfound[*objnotfound] = oid;
    (*objnotfound)++;
  } else {     /* If Obj found in machine (i.e. has not moved) */
#ifdef DEBUG
    printf("%s -> Obj found!!\n",__func__);
  	printf("%s -> Obj found: oid = %d, type = %d\t\n", __func__, OID(mobj), TYPE((objheader_t *)mobj));
	  fflush(stdout);
#endif
    
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
      }
    }
  }
#ifdef DEBUG
	printf("%s -> oid: %u, v_matchnolock: %d, v_matchlock: %d, v_nomatch: %d\n",__func__, oid, *v_matchnolock, *v_matchlock, *v_nomatch);
#endif
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
#ifdef DEBUG
		printf("%s -> control = %s\n", __func__,"TRANS_AGREE");
#endif
    send_data(acceptfd, &control, sizeof(char));
    
#ifdef DEBUG
		printf("%s -> finished sending control\n",__func__);
#endif
  }
  /* Condition to send TRANS_SOFT_ABORT */
  else if((*(v_matchlock) > 0 && *(v_nomatch) == 0) || (*(objnotfound) > 0 && *(v_nomatch) == 0)) {
    control = TRANS_SOFT_ABORT;
#ifdef DEBUG
		printf("%s -> control = %s\n", __func__,"TRANS_SOFT_ABORT");
#endif
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
#ifdef DEBUG
	printf("DEBUG-> Entering transCommitProcess, dstmserver.c\n");
	printf("nummod: %d, numlocked: %d\n", nummod, numlocked);
#endif

  /* Process each modified object saved in the mainobject store */
  for(i = 0; i < nummod; i++) {
    if((header = (objheader_t *) mhashSearch(oidmod[i])) == NULL) {
#ifndef RECOVERY
      printf("Error: mhashsearch returns NULL at dstmserver.c %d\n", __LINE__);
			return 1;
#else
			header = (objheader_t *)(modptr+offset);
			header->version += 1;
			header->isBackup = 1;
#ifdef DEBUG
      printf("oid: %u, new header version: %d\n", oidmod[i], header->version);
#endif
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
#ifdef DEBUG
    printf("oid: %u, new header version: %d\n", oidmod[i], header->version);
#endif
    /* If threads are waiting on this object to be updated, notify them */
    if(header->notifylist != NULL) {
#ifdef DEBUG
      printf("%s -> type : %d notifylist : %d\n",__func__,TYPE(header),header->notifylist);
#endif

#ifdef RECOVERY
      if(header->isBackup != 0)
        notifyAll(&header->notifylist, OID(header), header->version);
      else
        clearNotifyList(OID(header));
#else
        notifyAll(&header->notifylist, OID(header), header->version);
#endif

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
		
#ifdef DEBUG
		printf("header oid:%d, version:%d, useWriteUnlock:%d\n", OID(header), header->version, useWriteUnlock);
#endif
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
#ifdef DEBUG
		printf("control = %d, file = %s, line = %d\n", (int)control, __FILE__, __LINE__);
#endif
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
      	} 
        else {
          write_unlock(STATUSPTR(header));
      	  if ((sd = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
	          perror("processReqNotify():socket()");
	          return;
    	    }
  	      bzero(&remoteAddr, sizeof(remoteAddr));
    	    remoteAddr.sin_family = AF_INET;
  	      remoteAddr.sin_port = htons(LISTEN_PORT);
    	    remoteAddr.sin_addr.s_addr = htonl(mid);
  
      	  if(connect(sd, (struct sockaddr *)&remoteAddr, sizeof(remoteAddr)) < 0) {
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

#ifdef RECOVERY
/* go through oid's notifylist and clear them */
void clearNotifyList(unsigned int oid)
{
#ifdef DEBUG
  printf("%s -> Entering\n",__func__);
#endif

  objheader_t* header;
  threadlist_t* t;
  threadlist_t* tmp;
  
  if((header = (objheader_t *) mhashSearch(oid)) == NULL) {
    printf("%s -> mhashSearch returned NULL!!\n",__func__);
  }

  if(header->notifylist != NULL) {
      t = header->notifylist;
       
      while(t) {
        tmp = t;
        t = t->next;

        free(tmp);
      }
      header->notifylist = NULL;
  }
  
  pthread_mutex_lock(&clearNotifyList_mutex);
  clearNotifyListFlag = 0;
  pthread_mutex_unlock(&clearNotifyList_mutex);
#ifdef DEBUG
  printf("%s -> finished\n",__func__);
#endif
}
#endif
