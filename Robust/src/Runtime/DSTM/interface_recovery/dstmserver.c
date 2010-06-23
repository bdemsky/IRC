/* Coordinator => Machine that initiates the transaction request call for commiting a transaction
 * Participant => Machines that host the objects involved in a transaction commit */

#include <netinet/tcp.h>
#include <ip.h>
#include "dstm.h"
#include "altmlookup.h"
#include "llookup.h"
#include "threadnotify.h"
#include "prefetch.h"
#include <sched.h>
#ifdef COMPILER
#include "thread.h"
#endif
#include "gCollect.h"
#include "readstruct.h"

#ifdef RECOVERY
#include <unistd.h>
#include <signal.h>
#include "tlookup.h"
#include "translist.h"
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
pthread_mutex_t clearNotifyList_mutex;
pthread_mutex_t translist_mutex;

tlist_t* transList;
int okCommit; // machine flag
extern numWaitMachine;
extern unsigned int currentEpoch;
extern unsigned int currentBackupMachine;
unsigned int leader_index;

#endif

objstr_t *mainobjstore;
pthread_mutex_t mainobjstore_mutex;
pthread_mutex_t lockObjHeader;
pthread_mutexattr_t mainobjstore_mutex_attr; /* Attribute for lock to make it a recursive lock */

sockPoolHashTable_t *transPResponseSocketPool;

#ifdef RECOVERY

long long myrdtsc(void)
{
  unsigned hi, lo; 
  __asm__ __volatile__ ("rdtsc" : "=a"(lo), "=d"(hi));
  return ( (unsigned long long)lo)|( ((unsigned long long)hi)<<32 );
}

#endif

/* This function initializes the main objects store and creates the
 * global machine and location lookup table */
int dstmInit(void) {
  mainobjstore = objstrCreate(DEFAULT_OBJ_STORE_SIZE);
  /* Initialize attribute for mutex */
  pthread_mutexattr_init(&mainobjstore_mutex_attr);
  pthread_mutexattr_settype(&mainobjstore_mutex_attr, PTHREAD_MUTEX_RECURSIVE_NP);
  pthread_mutex_init(&mainobjstore_mutex, &mainobjstore_mutex_attr);
  pthread_mutex_init(&lockObjHeader,NULL);

#ifdef RECOVERY
	pthread_mutex_init(&liveHosts_mutex, NULL);
	pthread_mutex_init(&recovery_mutex, NULL);
  pthread_mutex_init(&clearNotifyList_mutex,NULL);
  pthread_mutex_init(&translist_mutex,NULL);
#endif

  if (mhashCreate(MHASH_SIZE, MLOADFACTOR))
    return 1;             //failure

  if (lhashCreate(HASH_SIZE, LOADFACTOR))
    return 1;             //failure

#ifdef RECOVERY
  if (thashCreate(THASH_SIZE, LOADFACTOR))
    return 1;
  if ((transList = tlistCreate())== NULL) {
    printf("well error\n");
    return 1;
  }
  
  okCommit = TRANS_OK;
  currentEpoch = 1;
  leader_index = -1;

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
  int firsttime = 1;          // these two are for periodic checking
  pthread_t thread_dstm_asking;
#endif

  printf("Listening on port %d, fd = %d\n", LISTEN_PORT, listenfd);
  while(1) {
    int retval;
    int flag=1;
    acceptfd = accept(listenfd, (struct sockaddr *)&client_addr, &addrlength);
    setsockopt(acceptfd, IPPROTO_TCP, TCP_NODELAY, (char *) &flag, sizeof(flag));

#ifdef RECOVERY
    if(firsttime) {
      do {
        retval = pthread_create(&thread_dstm_asking, NULL, startPolling, NULL);
      }while(retval!=0);
      firsttime=0;
      pthread_detach(thread_dstm_asking);
    }
#endif

    do {
      	retval=pthread_create(&thread_dstm_accept, NULL, dstmAccept, (void *)acceptfd);
    } while(retval!=0);
    pthread_detach(thread_dstm_accept);
  }
}

#ifdef RECOVERY
void* startPolling()
{
  unsigned int deadMachineIndex = -1;
  int i;
  int validHost;
  int *socklist;
  int sd;

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
        notifyLeaderDeadMachine(hostIpAddrs[deadMachineIndex]);
        freeSockWithLock(transPResponseSocketPool, hostIpAddrs[deadMachineIndex], socklist[deadMachineIndex]);
        socklist[deadMachineIndex] = -1;
      } // end of if 2
    } // end of while 1

    free(socklist);
}


unsigned int checkIfAnyMachineDead(int* socklist)
{
  int timeout = 0;
  int i;
  char control = RESPOND_LIVE;
  char response;
  
  while(1){

//    if(okCommit == TRANS_OK) {
      for(i = 0; i< numHostsInSystem;i++) {
        if(socklist[i] > 0) {
          send_data(socklist[i], &control,sizeof(char));
  
          if(recv_data(socklist[i], &response, sizeof(char)) < 0) {
            // if machine is dead, returns index of socket
            return i;
          }
          else {
            // machine responded
            if(response != LIVE) {
              return i;
            }
          } // end else
        }// end if(socklist[i]
      } // end for()

      clearDeadThreadsNotification();
//    }
    /*
    else {
      if(leader_index >= 0 ) {
        send_data(socklist[leader_index],&control,sizeof(char));
  
        if(recv_data(socklist[leader_index], &response, sizeof(char)) < 0) {
          // if machine is dead, returns index of socket
          return i;
        }
        else {
          // machine responded
          if(response != LIVE) {
            return i;
          }
        } // end else
      }
    }
*/
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
#ifdef RECOVERY
	void *dupeptr;
  unsigned int transIDreceived;
  char decision;
  unsigned int epoch_num;
  int timeout;
#endif

	int i;
  unsigned int tempsize;
	objheader_t *h;
	trans_commit_data_t transinfo;
  unsigned short objType, *versionarry, version;
	unsigned int *oidarry, numoid, mid, threadid;
    int n, v;

	/* Receive control messages from other machines */
	while(1) {
		int ret=recv_data_errorcode((int)acceptfd, &control, sizeof(char));
		//int ret=recv_data_errorcode_buf((int)acceptfd, &readbuffer, &control, sizeof(char));
//    printf("%s -> Received control = %d\n",__func__,control);
    dupeptr = NULL;

		if (ret==0)
			break;
		if (ret==-1) {
//			printf("DEBUG -> RECV Error!.. retrying\n");
	//		exit(0);
			break;
		}
		switch(control) {
			case READ_REQUEST:
#ifdef DEBUG
        printf("control -> READ_REQUEST\n");
#endif
				/* Read oid requested and search if available */
				timeout = recv_data((int)acceptfd, &oid, sizeof(unsigned int));

        if(timeout < 0)
          break;
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
				ctrl = LIVE;
				send_data((int)acceptfd, &ctrl, sizeof(char));
				break;
#endif
#ifdef RECOVERY
      case REQUEST_TRANS_WAIT:
        {
          unsigned int new_leader_index;
          recv_data((int)acceptfd,&epoch_num,sizeof(unsigned int));
          recv_data((int)acceptfd,&new_leader_index,sizeof(unsigned int));

          if(inspectEpoch(epoch_num,"REQUEST_TRANS_WAIT") < 0) {
            response = RESPOND_HIGHER_EPOCH;
            send_data((int)acceptfd,&response,sizeof(char));
          }
          else {
            printf("Got new Leader! : %d\n",epoch_num);
            pthread_mutex_lock(&recovery_mutex);
            currentEpoch = epoch_num;
            okCommit = TRANS_BEFORE;
            leader_index = new_leader_index;
            pthread_mutex_unlock(&recovery_mutex);
            if(stopTransactions(TRANS_BEFORE,epoch_num) < 0) {
              response = RESPOND_HIGHER_EPOCH;
              send_data((int)acceptfd,&response,sizeof(char));
            }
            else {
              response = RESPOND_TRANS_WAIT;
              send_data((int)acceptfd,&response,sizeof(char));
              sendMyList((int)acceptfd);
            }
          }
        } 
        break;

      case RELEASE_NEW_LIST:
        printf("control -> RELEASE_NEW_LIST\n");
        { 
          recv_data((int)acceptfd,&epoch_num,sizeof(unsigned int));

          if(inspectEpoch(epoch_num,"RELEASE_NEW_LIST") < 0) {
            response = RESPOND_HIGHER_EPOCH;
          }
          else 
          {
            response =  receiveNewList((int)acceptfd);
            if(stopTransactions(TRANS_AFTER,epoch_num) < -1)
              response = RESPOND_HIGHER_EPOCH;
          }
          printf("After stop transaction\n");
          send_data((int)acceptfd,&response,sizeof(char));
        }
      break;

      case REQUEST_TRANS_RESTART:

        recv_data((int)acceptfd,&epoch_num,sizeof(char));

        if(inspectEpoch(epoch_num,"REQUEST_TRANS_RESTART") < 0) break;
        
        pthread_mutex_lock(&liveHosts_mutex);
        printf("RESTART!!!\n");
        okCommit = TRANS_OK;
        pthread_mutex_unlock(&liveHosts_mutex);

        pthread_mutex_lock(&recovery_mutex);
        leader_index = -1;
        pthread_mutex_unlock(&recovery_mutex);

        break;
			case UPDATE_LIVE_HOSTS:
#ifdef DEBUG
        printf("control -> UPDATE_LIVE_HOSTS\n");
#endif
        receiveNewHostLists((int)acceptfd);
        pthread_mutex_lock(&recovery_mutex);
        currentBackupMachine = getBackupMachine(myIpAddr);
        pthread_mutex_unlock(&recovery_mutex);
#ifdef DEBUG
				printHostsStatus();
			  printf("%s (UPDATE_LIVE_HOSTS)-> Finished\n", __func__);	
#endif
				break;
#endif

#ifdef RECOVERY
			case REQUEST_DUPLICATE:
       {
         struct sockaddr_in remoteAddr;
         int sd;
         unsigned int dupeSize;
         unsigned int epoch_num;

         recv_data((int)acceptfd,&epoch_num,sizeof(unsigned int));

         if(inspectEpoch(epoch_num,"REQUEST_DUPLICATE") < 0) {
           break;
         }
		  	  	
         //object store stuffffff
         mid = getBackupMachine(myIpAddr);

         if(mid != currentBackupMachine) {
           currentBackupMachine = mid;
           dupeptr = (char*) mhashGetDuplicate(&tempsize, 0);
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
              printf("REQUEST_DUPE ERROR : %s\n",strerror(errno));
              break;
            }
            else {
    		  		send_data(sd, &ctrl, sizeof(char));
	    		  	send_data(sd, dupeptr, tempsize);

              dupeSize = tempsize;

              if((dupeSize += readDuplicateObjs(sd) ) < 0) {
                break;
              }
  	  			  recv_data(sd, &response, sizeof(char));
	  	  		
              if(response != DUPLICATION_COMPLETE) {
#ifndef DEBUG
               printf("%s(REQUEST_DUPE) -> DUPLICATION FAIL\n",__func__);
#endif
			  	  //fail message
               break;
//             exit(0);
  				    }

              close(sd);
            }
           free(dupeptr);

           ctrl = DUPLICATION_COMPLETE;
           send_data((int)acceptfd, &ctrl, sizeof(char));
           send_data((int)acceptfd, &dupeSize,sizeof(unsigned int));

#ifdef DEBUG
           printf("%s (REQUEST_DUPE)-> Finished\n", __func__);	
#endif
        }
        else {
          ctrl = DUPLICATION_COMPLETE;
          send_data((int)acceptfd,&ctrl,sizeof(char));
          tempsize = 0;
          send_data((int)acceptfd,&tempsize,sizeof(unsigned int));
        }
       }
			 break;
          

			case RECEIVE_DUPES:
#ifdef DEBUG
        printf("control -> RECEIVE_DUPES sd : %d\n",(int)acceptfd);
#endif
				if((readDuplicateObjs((int)acceptfd)) < 0) {
					printf("Error: In readDuplicateObjs() %s, %d\n", __FILE__, __LINE__);
//					pthread_exit(NULL);
				}
        else {
          dupeptr = (char*) mhashGetDuplicate(&tempsize, 1);
          send_data((int)acceptfd,dupeptr,tempsize);

          free(dupeptr);
  				ctrl = DUPLICATION_COMPLETE;
	  			send_data((int)acceptfd, &ctrl, sizeof(char));
#ifdef DEBUG
          printf("%s (RECEIVE_DUPES) -> Finished\n",__func__);
#endif
        }
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
  int timeout1;
  int timeout2;

#ifndef DEBUG
	printf("%s-> Start\n", __func__);
#endif
	timeout1 = recv_data((int)acceptfd, &numoid, sizeof(unsigned int));
	timeout2 = recv_data((int)acceptfd, &size, sizeof(int));	

  if(timeout1 < 0 || timeout2 < 0) {
    return -1;
  }
	
  if(numoid != 0) {
		if ((dupeptr = calloc(1, size)) == NULL) {
			printf("calloc error for duplicated objects %s, %d\n", __FILE__, __LINE__);
			return -1;
		}


		if(recv_data((int)acceptfd, dupeptr, size) < 0) {
      free(dupeptr);
      return -1;
    }
  

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
				  return -1;
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
#ifndef DEBUG
		printf("%s-> End\n", __func__);
#endif
    free(dupeptr);
		return size;
	}
	else {
#ifndef DEBUG
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

  if(timeout < 0)
    return 0;

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
  {
    if(modptr != NULL)
      free(modptr);
    return 0;
  }

  pthread_mutex_lock(&translist_mutex);
  transList = tlistInsertNode(transList,fixed.transid,TRYING_TO_COMMIT,TRYING_TO_COMMIT,fixed.epoch_num);  
  pthread_mutex_unlock(&translist_mutex);                                                    
    
  /* Create an array of oids for modified objects */
  oidmod = (unsigned int *) calloc(fixed.nummod, sizeof(unsigned int));
  if (oidmod == NULL) {
    printf("calloc error %s, %d\n", __FILE__, __LINE__);
    return 1;
  }
  ptr = (char *) modptr;
  for(i = 0 ; i < fixed.nummod; i++){
    int tmpsize=0;
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
  int i = 0;
  unsigned int epoch_num;
  tlist_node_t* tNode;
#ifdef DEBUG
  printf("%s -> Enter\n",__func__);
#endif

//  printf("%s -> transID : %u\n",__func__,fixed->transid);
  if(inspectEpoch(fixed->epoch_num,"procesClient1") < 0) {
    printf("%s-> Higher Epoch current epoch = %u epoch %u\n",__func__,currentEpoch,fixed->epoch_num);
    control = RESPOND_HIGHER_EPOCH;
    send_data((int)acceptfd,&control,sizeof(char));
  }
  /* Send reply to the Coordinator */
  else if((retval = handleTransReq(fixed, transinfo, listmid, objread, modptr,acceptfd)) == 0 ) {
    printf("Error: In handleTransReq() %s, %d\n", __FILE__, __LINE__);
	  printf("DEBUG-> Exiting processClientReq, line = %d\n", __LINE__);
    return 1;
  }

//  printf("%s -> Waiting for transID : %u\n",__func__,fixed->transid);
	int timeout1 = recv_data((int)acceptfd, &control, sizeof(char));
  int timeout2 = recv_data((int)acceptfd, &epoch_num, sizeof(unsigned int));

  if(timeout1 < 0 || timeout2 < 0) {  // timeout. failed to receiving data from coordinator
    control = DECISION_LOST;
  }

  pthread_mutex_lock(&translist_mutex);
  tNode = tlistSearch(transList,fixed->transid);  
  pthread_mutex_unlock(&translist_mutex);         
  
  // check if it is allowed to commit
  tNode->decision = control;
  do {
    tNode->status = TRANS_INPROGRESS; 
    if(okCommit != TRANS_BEFORE) {
      if(inspectEpoch(tNode->epoch_num,"processCleint2") > 0) {
        tNode->status = TRANS_INPROGRESS;
        thashInsert(fixed->transid,tNode->decision);
        commitObjects(tNode->decision,fixed,transinfo,modptr,oidmod,acceptfd);
        tNode->status = TRANS_AFTER;
      }
      if(okCommit == TRANS_AFTER) {
      printf("%s -> 11 \ttransID : %u decision : %d status : %d \n",__func__,tNode->transid,tNode->decision,tNode->status);
      sleep(3);     
      }
    }
    else {
      tNode->status = TRANS_WAIT;
      printf("%s -> Waiting!! \ttransID : %u decision : %d status : %d \n",__func__,tNode->transid,tNode->decision,tNode->status);
      sleep(3);
      randomdelay();
    }
    
  }while(tNode->status != TRANS_AFTER);

  if(okCommit == TRANS_AFTER)
  {
    printf("%s -> TRANS_AFTER!! \ttransID : %u decision : %d status : %d \n",__func__,tNode->transid,tNode->decision,tNode->status);
    printf("%s -> Before removing\n",__func__);
  }


  pthread_mutex_lock(&translist_mutex);
  transList = tlistRemove(transList,fixed->transid);
  pthread_mutex_unlock(&translist_mutex);

  if(okCommit == TRANS_AFTER)
    printf("%s -> After removing\n",__func__);


  /* Free memory */
  if (transinfo->objlocked != NULL) {
    free(transinfo->objlocked);
  }
  if (transinfo->objnotfound != NULL) {
    free(transinfo->objnotfound);
  }
#ifdef DEBUG
	printf("%s-> Exit\n", __func__);
#endif

  return 0;
}

void commitObjects(char control,fixed_data_t* fixed,trans_commit_data_t* transinfo,void* modptr,unsigned int* oidmod,int acceptfd)
{
  void *header;
  int val;
  int i;

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
			    exit(0);
          return ;
				}
				if(useWriteUnlock) {
				  write_unlock(STATUSPTR(header));
				} else {
				  read_unlock(STATUSPTR(header));
			  }
			}
			break;
      case TRANS_COMMIT:
      /* insert received control into thash for another transaction*/
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
        exit(0);
        return;
			}
      break;
    case TRANS_ABORT_BUT_RETRY_COMMIT_WITH_RELOCATING:
      break;
    default:
      printf("%s : No response to TRANS_AGREE OR DISAGREE protocol - transID = %u, control =  %d\a\n",__func__,fixed->transid);
      //TODO Use fixed.trans_id  TID since Client may have died
      			break;                                                                                                                        
  }
  
  return;
} 
            
            
           
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

/* This function increments counters while running a voting decision on all objects involved
 * in TRANS_REQUEST and If a TRANS_DISAGREE sends the response immediately back to the coordinator */
char handleTransReq(fixed_data_t *fixed, trans_commit_data_t *transinfo, unsigned int *listmid, char *objread, void *modptr, int acceptfd) {
  int val, i = 0, j;
  unsigned short version;
  char control = 0, *ptr;
  unsigned int oid;
  unsigned int *oidnotfound, *oidlocked, *oidvernotmatch;
  objheader_t *headptr;
#ifdef DEBUG
  printf("%s -> Enter\n",__func__);
#endif

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

  char retval;

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
      retval=getCommitCountForObjRead(oidnotfound, oidlocked, oidvernotmatch, &objnotfound, &objlocked, &objvernotmatch,
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

      retval=getCommitCountForObjMod(oidnotfound, oidlocked, oidvernotmatch, &objnotfound,
                              &objlocked, &objvernotmatch, &v_matchnolock, &v_matchlock, &v_nomatch,
                              &numBytes, &control, oid, version);
    }
    if(retval==TRANS_DISAGREE || retval==TRANS_SOFT_ABORT) {
      //unlock objects as soon versions mismatch or else 
      //locks cannot be acquired elsewhere
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
        if(v_nomatch > 0)
          free(oidlocked);
      }
      objlocked=0;
      break;
    }
  }

  //go through rest of the objects for version mismatches
  if(retval==TRANS_DISAGREE || retval==TRANS_SOFT_ABORT) {
    i++;
    procRestObjs(objread, ptr, i, fixed->numread, fixed->nummod, oidnotfound, oidvernotmatch, &objnotfound, &objvernotmatch, &v_nomatch, &numBytes);
  }

  /* send TRANS_DISAGREE and objs that caused the ABORTS*/
	if(v_nomatch > 0) {
#ifdef CACHE
		char *objs = calloc(1, numBytes);
		int j, offset = 0;
		for(j = 0; j<objvernotmatch; j++) {
			objheader_t *header = mhashSearch(oidvernotmatch[j]);
			int size = 0;
			GETSIZE(size, header);
			size += sizeof(objheader_t);
            //printf("%s() DEBUG: oid= %u, type= %u\n", __func__, OID(header), TYPE(header));
            //fflush(stdout);
			memcpy(objs+offset, header, size);
			offset += size;
		}
#endif
    
#ifdef DEBUG
		printf("%s -> control = %d, file = %s, line = %d\n", __func__,(int)control, __FILE__, __LINE__);
#endif

    if(control < 0)
      printf("control = %d\n",control);
    control=TRANS_DISAGREE;

    printf("%s -> Sent message!\n",__func__);
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
#ifdef DEBUG
  printf("%s -> Exit\n",__func__);
#endif
  return control;
}

/* Update Commit info for objects that are modified */
char getCommitCountForObjMod(unsigned int *oidnotfound, unsigned int *oidlocked,
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
        *control = TRANS_DISAGREE;
	} else {     /* If Obj found in machine (i.e. has not moved) */
		/* Check if Obj is locked by any previous transaction */
		if (write_trylock(STATUSPTR(mobj))) { // Can acquire write lock
#ifdef DEBUG
		  printf("****%s->Trying to acquire 'remote' writelock for oid:%d, version:%d\n", __func__, oid, version);
			printf("this version: %d, mlookup version: %d\n", version, ((objheader_t *)mobj)->version);
#endif
			if (version == ((objheader_t *)mobj)->version) { /* match versions */
				(*v_matchnolock)++;
				*control = TRANS_AGREE;
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
				//printf("%s() DEBUG: acquire lock, modified, oid = %d, type = %d\n", __func__, OID(mobj), TYPE((objheader_t *)mobj));
			}
			//Keep track of oid locked
			oidlocked[(*objlocked)++] = OID(((objheader_t *)mobj));
		} else {  //we are locked
			if (version == ((objheader_t *)mobj)->version) {     /* Check if versions match */
				(*v_matchlock)++;
				*control = TRANS_SOFT_ABORT;
				//printf("%s() DEBUG: soft abort, oid = %d, type = %d\n", __func__, OID(mobj), TYPE((objheader_t *)mobj));
			} else { /* If versions don't match ...HARD ABORT */
				(*v_nomatch)++;
				oidvernotmatch[*objvernotmatch] = oid;
				(*objvernotmatch)++;
				int size;
				GETSIZE(size, mobj);
				size += sizeof(objheader_t);
				*numBytes += size;
				*control = TRANS_DISAGREE;
				//printf("%s() DEBUG: modified, couldn't get lock oid = %d, type = %d\n", __func__, OID(mobj), TYPE((objheader_t *)mobj));
			}
		}
  }
#ifdef DEBUG
	printf("%s -> oid: %u, v_matchnolock: %d, v_matchlock: %d, v_nomatch: %d\n",__func__,oid, *v_matchnolock, *v_matchlock, *v_nomatch);
#endif
    return *control;
}

/* Update Commit info for objects that are read */
char getCommitCountForObjRead(unsigned int *oidnotfound, unsigned int *oidlocked, unsigned int *oidvernotmatch,
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
	*control = TRANS_DISAGREE;
  } else {     /* If Obj found in machine (i.e. has not moved) */
    /* Check if Obj is locked by any previous transaction */
    if (read_trylock(STATUSPTR(mobj))) { //Can further acquire read locks
      if (version == ((objheader_t *)mobj)->version) { /* match versions */
      	(*v_matchnolock)++;
        *control = TRANS_AGREE;
      } else { /* If versions don't match ...HARD ABORT */
      	(*v_nomatch)++;
      	oidvernotmatch[(*objvernotmatch)++] = oid;
      	int size;
      	GETSIZE(size, mobj);
      	size += sizeof(objheader_t);
      	*numBytes += size;
      
        /* Send TRANS_DISAGREE to Coordinator */
      	*control = TRANS_DISAGREE;
        //printf("%s() DEBUG: read, lock aquired, oid = %d, type = %d\n", __func__, OID(mobj), TYPE((objheader_t *)mobj));
      }

      //Keep track of oid locked
      oidlocked[(*objlocked)++] = OID(((objheader_t *)mobj));
    } else { /* Some other transaction has aquired a write lock on this object */
      if (version == ((objheader_t *)mobj)->version) { /* Check if versions match */
      	(*v_matchlock)++;
      	*control = TRANS_SOFT_ABORT;
        //printf("%s() DEBUG: soft abort, read oid = %d, type = %d\n", __func__, OID(mobj), TYPE((objheader_t *)mobj));
      } else { /* If versions don't match ...HARD ABORT */
      	(*v_nomatch)++;
      	oidvernotmatch[*objvernotmatch] = oid;
      	(*objvernotmatch)++;
      	int size;
      	GETSIZE(size, mobj);
      	size += sizeof(objheader_t);
      	*numBytes += size;
      	*control = TRANS_DISAGREE;
        //printf("%s() DEBUG: read, couldn't aquire lock, oid = %d, type = %d\n", __func__, OID(mobj), TYPE((objheader_t *)mobj));
      }
    }
  }
#ifdef DEBUG
	printf("%s -> oid: %u, v_matchnolock: %d, v_matchlock: %d, v_nomatch: %d\n",__func__, oid, *v_matchnolock, *v_matchlock, *v_nomatch);
#endif
  return *control;
}

void procRestObjs(char *objread, 
                  char *objmod, 
                  int index, 
                  int numread, 
                  int nummod, 
                  unsigned int *oidnotfound, 
                  unsigned int *oidvernotmatch,
                  int *objnotfound, 
                  int *objvernotmatch, 
                  int *v_nomatch, 
                  int *numBytes) {
  int i;
  unsigned int oid;
  unsigned short version;

  /* Process each oid in the machine pile/ group per thread */
  for (i = index; i < numread+nummod; i++) {
    if (i < numread) { //Objs only read and not modified
      int incr = sizeof(unsigned int) + sizeof(unsigned short); // Offset that points to next position in the objread array
      incr *= i;
      oid = *((unsigned int *)(objread + incr));
      incr += sizeof(unsigned int);
      version = *((unsigned short *)(objread + incr));
    } else {  //Objs modified
      objheader_t *headptr;
      headptr = (objheader_t *) objmod;
      oid = OID(headptr);
      version = headptr->version;
      int tmpsize;
      GETSIZE(tmpsize, headptr);
      objmod += sizeof(objheader_t) + tmpsize;
    }
    processVerNoMatch(oidnotfound,
        oidvernotmatch,
        objnotfound,
        objvernotmatch,
        v_nomatch,
        numBytes,
        oid, 
        version);
  }
  return;
}

void processVerNoMatch(unsigned int *oidnotfound, 
                      unsigned int *oidvernotmatch, 
                      int *objnotfound, 
                      int *objvernotmatch, 
                      int *v_nomatch, 
                      int *numBytes,
                      unsigned int oid, 
                      unsigned short version) {
  void *mobj;
  /* Check if object is still present in the machine since the beginning of TRANS_REQUEST */

  if ((mobj = mhashSearch(oid)) == NULL) {    /* Obj not found */
    /* Save the oids not found and number of oids not found for later use */
    oidnotfound[*objnotfound] = oid;
    (*objnotfound)++;
  } else {     /* If Obj found in machine (i.e. has not moved) */
    /* Check if Obj is locked by any previous transaction */
    if (version != ((objheader_t *)mobj)->version) { /* match versions */
      (*v_nomatch)++;
      oidvernotmatch[*objvernotmatch] = oid;
	  (*objvernotmatch)++;
	  int size;
      GETSIZE(size, mobj);
      size += sizeof(objheader_t);
      *numBytes += size;
    }
  }
}

/* This function decides what control message such as TRANS_AGREE, TRANS_DISAGREE or TRANS_SOFT_ABORT
 * to send to Coordinator based on the votes of oids involved in the transaction */
char decideCtrlMessage(fixed_data_t *fixed, trans_commit_data_t *transinfo, int *v_matchnolock, int *v_matchlock,
                       int *v_nomatch, int *objnotfound, int *objlocked, void *modptr,
                       unsigned int *oidnotfound, unsigned int *oidlocked, int acceptfd) {
  int val;
  char control = 0;
#ifdef DEBUG
  printf("%s -> Enter\n",__func__);
#endif

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
  }

  /* Fill out the trans_commit_data_t data structure. This is required for a trans commit process
   * if Participant receives a TRANS_COMMIT */
  transinfo->objlocked = oidlocked;
  transinfo->objnotfound = oidnotfound;
  transinfo->modptr = modptr;
  transinfo->numlocked = *(objlocked);
  transinfo->numnotfound = *(objnotfound);

#ifdef DEBUG
  printf("%s -> Exit\n",__func__);
#endif
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
      printf("%s -> to notifyAll\n",__func__);
      if(header->isBackup == 0) {
        printf("%s -> called notifyAll\n",__func__);
        notifyAll(&header->notifylist, OID(header), header->version);
      }
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
      //sd = getSockWithLock(transPResponseSocketPool, mid);
      if((sd = getSockWithLock(transPResponseSocketPool, mid)) < 0) {
        printf("%s() Socket Create Error at %s, %d\n", __func__, __FILE__, __LINE__);
        return -1;
      }
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

     //   printf("%s -> newversion : %d versionarray : %d i : %d\n",__func__,newversion,*(versionarry + i),i); 
	  
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
            printf("%s -> Call frm here\n",__func__);
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

void receiveNewHostLists(int acceptfd)
{
  // copy back
	pthread_mutex_lock(&liveHosts_mutex);
	recv_data((int)acceptfd, liveHosts, sizeof(int)*numHostsInSystem);
	recv_data((int)acceptfd, locateObjHosts, sizeof(unsigned int)*numHostsInSystem*2);
	pthread_mutex_unlock(&liveHosts_mutex);
	
  numLiveHostsInSystem = getNumLiveHostsInSystem();
}

/* wait until all transaction waits for leader's decision */
int stopTransactions(int TRANS_FLAG,unsigned int epoch_num)
{
//  printf("%s - > Enter flag :%d\n",__func__,TRANS_FLAG);
  int size = transList->size;
  int i;
  int flag;
  tlist_node_t* walker;

  if(TRANS_FLAG == TRANS_BEFORE) {
    okCommit = TRANS_BEFORE;
    /* make sure that all transactions are stopped */
    do {
      transList->flag = 0;
      walker = transList->head;

      while(walker)
      {
        // locking
        while(walker->status != TRANS_WAIT && tlistSearch(transList,walker->transid) != NULL) {
          printf("%s -> BEFORE transid : %u - decision %d Status : %d \n",__func__,walker->transid,walker->decision,walker->status);
          if(inspectEpoch(epoch_num,"stopTrans_Before") < 0) {
            printf("%s -> Higher Epoch is seen, walker->epoch = %u currentEpoch = %u\n",__func__,epoch_num,currentEpoch);
            return -1;                                     
         }
          sleep(3);
        }
      walker = walker->next;
      }

      pthread_mutex_lock(&translist_mutex);
      flag = transList->flag;
      pthread_mutex_unlock(&translist_mutex);
    }while(flag == 1);
  }
  else if(TRANS_FLAG == TRANS_AFTER)
  {
    printf("%s -> TRANS_AFTER\n",__func__);
    okCommit = TRANS_AFTER;
    do {
      pthread_mutex_lock(&translist_mutex);
      size = transList->size;
      printf("%s -> size = %d\n",__func__,size);
      printf("%s -> okCommit = %d\n",__func__,okCommit);
      walker = transList->head;
      while(walker){
        printf("%s -> AFTER transid : %u - decision %d Status : %d epoch = %u  current epoch : %u\n",__func__,walker->transid,walker->decision,walker->status,walker->epoch_num,currentEpoch);
        walker = walker->next;
      }
      pthread_mutex_unlock(&translist_mutex);

      if(inspectEpoch(epoch_num,"stopTrans_Before") < 0) {
        printf("%s -> 222Higher Epoch is seen, walker->epoch = %u currentEpoch = %u\n",__func__,epoch_num,currentEpoch);
        return -1;
      }

      sleep(3);
    }while(size != 0);
  }

  return 0;
}

void sendMyList(int acceptfd)
{
  pthread_mutex_lock(&liveHosts_mutex);
  send_data((int)acceptfd,liveHosts,sizeof(int) * numHostsInSystem);
  pthread_mutex_unlock(&liveHosts_mutex);

  sendTransList(acceptfd);
}

void sendTransList(int acceptfd)
{
  printf("%s -> Enter\n",__func__);
  int size;
  char response;
  int transid;
  int i;
  tlist_node_t* walker = transList->head;

  // send on-going transaction
  pthread_mutex_lock(&translist_mutex);
  tlist_node_t* transArray = tlistToArray(transList,&size);
  pthread_mutex_unlock(&translist_mutex);

  if(transList->size != 0)
    tlistPrint(transList);

  printf("%s -> transList->size : %d  size = %d\n",__func__,transList->size,size);

  for(i = 0; i< size; i++) {
    printf("ID : %u  Decision : %d  status : %d\n",transArray[i].transid,transArray[i].decision,transArray[i].status);
  }
  printf("%s -> End transArray\n",__func__);

  send_data((int)acceptfd,&size,sizeof(int));
  send_data((int)acceptfd,transArray, sizeof(tlist_node_t) * size);

  // check if it already commit the decision for a transaction
  recv_data((int)acceptfd,&response, sizeof(char));

  while(response == REQUEST_TRANS_CHECK && response != REQUEST_TRANS_COMPLETE )
  {  
    int transid;
    recv_data((int)acceptfd,&transid, sizeof(unsigned int));

    response = checkDecision(transid);
    send_data((int)acceptfd,&response, sizeof(char));

    recv_data((int)acceptfd,&response,sizeof(char));
  }

  free(transArray);
}

int receiveNewList(int acceptfd)
{
  printf("%s -> Enter\n",__func__);
  int size;
  tlist_node_t* tArray;
  tlist_node_t* walker;
  int i;
  int flag = 1;
  char response;

  // new host lists
  pthread_mutex_lock(&liveHosts_mutex);
  recv_data((int)acceptfd,liveHosts,sizeof(int)*numHostsInSystem);
  pthread_mutex_unlock(&liveHosts_mutex);

  setLocateObjHosts();

  // new transaction list
  recv_data((int)acceptfd,&size,sizeof(int));


  if(size > 0) {
    if((tArray = calloc(size,sizeof(tlist_node_t) * size)) == NULL)
    {
      printf("%s -> calloc error\n",__func__);
      exit(0);
    }

    recv_data((int)acceptfd,tArray,sizeof(tlist_node_t) * size);
    flag = combineTransactionList(tArray,size);
    free(tArray);
  }

  if(flag == 1)
  {
    response = TRANS_OK;
  }
  else
  {
    response = -1;
  }

  printf("%s -> Exit\n",__func__);
  return response;
}


int combineTransactionList(tlist_node_t* tArray,int size)
{
  int flag = 1;
  tlist_node_t* walker;
  int i;

  walker = transList->head;

  while(walker){
      for(i = 0; i < size; i++)
      {
        if(walker->transid == tArray[i].transid)
        {
          walker->decision = tArray[i].decision;
          walker->epoch_num = tArray[i].epoch_num;
          break;
        }
      }
    walker = walker->next;
  }

  return flag;
}

#endif
