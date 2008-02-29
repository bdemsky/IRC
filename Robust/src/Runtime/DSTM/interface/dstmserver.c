/* Coordinator => Machine that initiates the transaction request call for commiting a transaction
 * Participant => Machines that host the objects involved in a transaction commit */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <pthread.h>
#include <netdb.h>
#include <fcntl.h>
#include <errno.h>
#include <string.h>
#include "dstm.h"
#include "mlookup.h"
#include "llookup.h"
#include "threadnotify.h"
#ifdef COMPILER
#include "thread.h"
#endif


#define LISTEN_PORT 2156
#define BACKLOG 10 //max pending connections
#define RECEIVE_BUFFER_SIZE 2048
#define PRE_BUF_SIZE 2048

extern int classsize[];

objstr_t *mainobjstore;
pthread_mutex_t mainobjstore_mutex;
pthread_mutexattr_t mainobjstore_mutex_attr; /* Attribute for lock to make it a recursive lock */
pthread_mutex_t threadnotify_mutex = PTHREAD_MUTEX_INITIALIZER;

/* This function initializes the main objects store and creates the 
 * global machine and location lookup table */

int dstmInit(void)
{
	mainobjstore = objstrCreate(DEFAULT_OBJ_STORE_SIZE);
	/* Initialize attribute for mutex */
	pthread_mutexattr_init(&mainobjstore_mutex_attr);
	pthread_mutexattr_settype(&mainobjstore_mutex_attr, PTHREAD_MUTEX_RECURSIVE_NP);
	pthread_mutex_init(&mainobjstore_mutex, &mainobjstore_mutex_attr);
	if (mhashCreate(HASH_SIZE, LOADFACTOR))
		return 1; //failure
	
	if (lhashCreate(HASH_SIZE, LOADFACTOR))
		return 1; //failure

	if (notifyhashCreate(N_HASH_SIZE, N_LOADFACTOR))
		return 1; //failure
	
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
	  acceptfd = accept(listenfd, (struct sockaddr *)&client_addr, &addrlength);
	  do {
	    retval=pthread_create(&thread_dstm_accept, NULL, dstmAccept, (void *)acceptfd);
	  } while(retval!=0);
	  pthread_detach(thread_dstm_accept);
	}
}
/* This function accepts a new connection request, decodes the control message in the connection 
 * and accordingly calls other functions to process new requests */
void *dstmAccept(void *acceptfd)
{
	int val, retval, size;
	unsigned int oid;
	char buffer[RECEIVE_BUFFER_SIZE], control,ctrl;
	char *ptr;
	void *srcObj;
	objheader_t *h;
	trans_commit_data_t transinfo;
	unsigned short objType, *versionarry, version;
	unsigned int *oidarry, numoid, mid, threadid;
	
	int i;

	transinfo.objlocked = NULL;
	transinfo.objnotfound = NULL;
	transinfo.modptr = NULL;
	transinfo.numlocked = 0;
	transinfo.numnotfound = 0;

	/* Receive control messages from other machines */
	if((retval = recv((int)acceptfd, &control, sizeof(char), 0)) <= 0) {
		perror("Error: in receiving control from coordinator\n");
		pthread_exit(NULL);
	}
	
	switch(control) {
		case READ_REQUEST:
			/* Read oid requested and search if available */
			if((retval = recv((int)acceptfd, &oid, sizeof(unsigned int), 0)) <= 0) {
				perror("Error: receiving 0x0 object from cooridnator\n");
				pthread_exit(NULL);
			}
			if((srcObj = mhashSearch(oid)) == NULL) {
				printf("Error: Object 0x%x is not found in Main Object Store %s, %d\n", oid, __FILE__, __LINE__);
				pthread_exit(NULL);
			}
			h = (objheader_t *) srcObj;
			GETSIZE(size, h);
			size += sizeof(objheader_t);

			if (h == NULL) {
				ctrl = OBJECT_NOT_FOUND;
				if(send((int)acceptfd, &ctrl, sizeof(char), MSG_NOSIGNAL) < sizeof(char)) {
					perror("Error sending control msg to coordinator\n");
					pthread_exit(NULL);
				}
			} else {
				/* Type */
				char msg[]={OBJECT_FOUND, 0, 0, 0, 0};
				*((int *)&msg[1])=size;
				if(send((int)acceptfd, &msg, sizeof(msg), MSG_NOSIGNAL) < sizeof(msg)) {
					perror("Error sending size of object to coordinator\n");
					pthread_exit(NULL);
				}
				if(send((int)acceptfd, h, size, MSG_NOSIGNAL) < size) {
					perror("Error in sending object\n");
					pthread_exit(NULL);
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
			if((val = readClientReq(&transinfo, (int)acceptfd)) != 0) {
				printf("Error: In readClientReq() %s, %d\n", __FILE__, __LINE__);
				pthread_exit(NULL);
			}
			break;
		case TRANS_PREFETCH:
			if((val = prefetchReq((int)acceptfd)) != 0) {
				printf("Error: In prefetchReq() %s, %d\n", __FILE__, __LINE__);
				pthread_exit(NULL);
			}
			break;
		case START_REMOTE_THREAD:
			retval = recv((int)acceptfd, &oid, sizeof(unsigned int), 0);
			if (retval <= 0)
				perror("dstmAccept(): error receiving START_REMOTE_THREAD msg");
			else if (retval != sizeof(unsigned int))
				printf("dstmAccept(): incorrect msg size %d for START_REMOTE_THREAD %s, %d\n",
					retval, __FILE__, __LINE__);
			else
			{
				objType = getObjType(oid);
				startDSMthread(oid, objType);
			}
			break;

		case THREAD_NOTIFY_REQUEST:
			size = sizeof(unsigned int);
			bzero(&buffer, RECEIVE_BUFFER_SIZE);
			retval = recv((int)acceptfd, &buffer, size, 0);
			numoid = *((unsigned int *) &buffer);
			size = (sizeof(unsigned int) + sizeof(unsigned short)) * numoid + 2 * sizeof(unsigned int);
			bzero(&buffer, RECEIVE_BUFFER_SIZE);
			retval = recv((int)acceptfd, &buffer, size, 0);
			if(retval <=0)
				perror("dstmAccept(): error receiving THREAD_NOTIFY_REQUEST");
			else if( retval != (2* sizeof(unsigned int) + (sizeof(unsigned int) + sizeof(unsigned short)) * numoid))
				printf("dstmAccept(): incorrect msg size %d for THREAD_NOTIFY_REQUEST %s, %d\n", retval, 
						__FILE__, __LINE__);
			else {
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
			}

			break;

		case THREAD_NOTIFY_RESPONSE:
			size = sizeof(unsigned short) + 2 * sizeof(unsigned int);
			bzero(&buffer, RECEIVE_BUFFER_SIZE);
			retval = recv((int)acceptfd, &buffer, size, 0);
			if(retval <= 0) 
				perror("dstmAccept(): error receiving THREAD_NOTIFY_RESPONSE");
			else if( retval != 2*sizeof(unsigned int) + sizeof(unsigned short))
				printf("dstmAccept(): incorrect msg size %d for THREAD_NOTIFY_RESPONSE msg %s, %d\n", 
						retval, __FILE__, __LINE__);
			else {
				oid = *((unsigned int *)buffer);
				size = sizeof(unsigned int);
				version = *((unsigned short *)(buffer+size));
				size += sizeof(unsigned short);
				threadid = *((unsigned int *)(buffer+size));
				threadNotify(oid,version,threadid);
			}

			break;

		default:
			printf("Error: dstmAccept() Unknown opcode %d at %s, %d\n", control, __FILE__, __LINE__);
	}

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
	int sum = 0, i, N, n, val;

	oidmod = NULL;

	/* Read fixed_data_t data structure */ 
	N = sizeof(fixed) - 1;
	ptr = (char *)&fixed;;
	fixed.control = TRANS_REQUEST;
	do {
		n = recv((int)acceptfd, (void *) ptr+1+sum, N-sum, 0);
		sum += n;
	} while(sum < N && n != 0); 

	/* Read list of mids */
	int mcount = fixed.mcount;
	N = mcount * sizeof(unsigned int);
	unsigned int listmid[mcount];
	ptr = (char *) listmid;
	sum = 0;
	do {
		n = recv((int)acceptfd, (void *) ptr+sum, N-sum, 0);
		sum += n;
	} while(sum < N && n != 0);

	/* Read oid and version tuples for those objects that are not modified in the transaction */
	int numread = fixed.numread;
	N = numread * (sizeof(unsigned int) + sizeof(short));
	char objread[N];
	if(numread != 0) { //If pile contains more than one object to be read, 
			  // keep reading all objects
		sum = 0;
		do {
			n = recv((int)acceptfd, (void *) objread, N, 0);
			sum += n;
		} while(sum < N && n != 0);
	}
	
	/* Read modified objects */
	if(fixed.nummod != 0) {
		if ((modptr = calloc(1, fixed.sum_bytes)) == NULL) {
			printf("calloc error for modified objects %s, %d\n", __FILE__, __LINE__);
			return 1;
		}
		sum = 0;
		do { // Recv the objs that are modified by the Coordinator
			n = recv((int)acceptfd, (char *) modptr+sum, fixed.sum_bytes-sum, 0);
			sum += n;
		} while (sum < fixed.sum_bytes && n != 0);
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

	do {
		retval = recv((int)acceptfd, &control, sizeof(char), 0);
	} while(retval < sizeof(char));

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
				STATUS(((objheader_t *)header)) &= ~(LOCK); 		
			}

			/* Send ack to Coordinator */
			sendctrl = TRANS_UNSUCESSFUL;
			if(send((int)acceptfd, &sendctrl, sizeof(char), MSG_NOSIGNAL) < sizeof(char)) {
				perror("Error: In sending ACK to coordinator\n");
				if (transinfo->objlocked != NULL) {
					free(transinfo->objlocked);
				}
				if (transinfo->objnotfound != NULL) {
					free(transinfo->objnotfound);
				}

				return 1;
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
			int incr = sizeof(unsigned int) + sizeof(short);// Offset that points to next position in the objread array
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
			if ((STATUS(((objheader_t *)mobj)) & LOCK) == LOCK) { 		
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
							STATUS(headptr) &= ~(LOCK);
						}
						free(oidlocked);
					}
					if((val = send(acceptfd, &control, sizeof(char), MSG_NOSIGNAL)) < sizeof(char)) {
						perror("Error in sending control to the Coordinator\n");
						return 0;
					}
					return control;
				}
			} else {/* If Obj is not locked then lock object */
				STATUS(((objheader_t *)mobj)) |= LOCK;
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
							STATUS(headptr) &= ~(LOCK);
						}
						free(oidlocked);
					}

					/* Send TRANS_DISAGREE to Coordinator */
					if((val = send(acceptfd, &control, sizeof(char), MSG_NOSIGNAL)) < sizeof(char)) {
						perror("Error in sending control to the Coordinator\n");
						return 0;
					}
					
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
		if((val = send(acceptfd, &control, sizeof(char), MSG_NOSIGNAL)) < sizeof(char)) {
			perror("Error in sending control to Coordinator\n");
			return 0;
		}
	}
	/* Condition to send TRANS_SOFT_ABORT */
	if((*(v_matchlock) > 0 && *(v_nomatch) == 0) || (*(objnotfound) > 0 && *(v_nomatch) == 0)) {
		control = TRANS_SOFT_ABORT;

		/* Send control message */
		if((val = send(acceptfd, &control, sizeof(char),MSG_NOSIGNAL)) < sizeof(char)) {
			perror("Error in sending TRANS_SOFT_ABORT control\n");
			return 0;
		}

		/* Send number of oids not found and the missing oids if objects are missing in the machine */
		if(*(objnotfound) != 0) { 
			int msg[1];
			msg[0] = *(objnotfound);
			if((val = send(acceptfd, msg, sizeof(int) ,MSG_NOSIGNAL)) < sizeof(int)) {
				perror("Error in sending objects that are not found\n");
				return 0;
			}
			int size = sizeof(unsigned int)* *(objnotfound);
			if((val = send(acceptfd, oidnotfound, size ,MSG_NOSIGNAL)) < size) {
				perror("Error in sending objects that are not found\n");
				return 0;
			}
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
		pthread_mutex_lock(&mainobjstore_mutex);
		memcpy((char*)header + sizeof(objheader_t), ((char *)modptr + sizeof(objheader_t) + offset), tmpsize);
		header->version += 1; 
		/* If threads are waiting on this object to be updated, notify them */
		if(header->notifylist != NULL) {
			notifyAll(&header->notifylist, OID(header), header->version);
		}
		pthread_mutex_unlock(&mainobjstore_mutex);
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
		STATUS(header) &= ~(LOCK);
	}
	//TODO Update location lookup table

	/* Send ack to coordinator */
	control = TRANS_SUCESSFUL;
	if(send((int)acceptfd, &control, sizeof(char), MSG_NOSIGNAL) < sizeof(char)) {
		perror("Error sending ACK to coordinator\n");
		return 1;
	}
	
	return 0;
}

/* This function recevies the oid and offset tuples from the Coordinator's prefetch call.
 * Looks for the objects to be prefetched in the main object store.
 * If objects are not found then record those and if objects are found
 * then use offset values to prefetch references to other objects */

int prefetchReq(int acceptfd) {
	int i, length, sum, n, numbytes, numoffset, N, objnotfound = 0, size, count = 0;
	int isArray = 0;
	unsigned int oid, index = 0;
	char *ptr, buffer[PRE_BUF_SIZE];
	void *mobj;
	unsigned int objoid;
	char control;
	objheader_t * header;
	int bytesRecvd;

	/* Repeatedly recv one oid and offset pair sent for prefetch */
	while(numbytes = recv((int)acceptfd, &length, sizeof(int), 0) != 0) {
		count++;
		if(length == -1)
			break;
		index = 0;  
		bytesRecvd = 0;
		do {
			bytesRecvd += recv((int)acceptfd, (char *)&oid +bytesRecvd,
					sizeof(unsigned int) - bytesRecvd, 0);
		} while (bytesRecvd < sizeof(unsigned int));
		numoffset = (length - (sizeof(int) + sizeof(unsigned int)))/ sizeof(short);
		N = numoffset * sizeof(short);
		short offset[numoffset];
		ptr = (char *)&offset;
		sum = 0;
		/* Recv the offset values per oid */ 
		do {
			n = recv((int)acceptfd, (void *)ptr+sum, N-sum, 0); 
			sum += n; 
		} while(sum < N && n != 0);	

		bzero(&buffer, PRE_BUF_SIZE);
		/* Process each oid */
		if ((mobj = mhashSearch(oid)) == NULL) {/* Obj not found */
			/* Save the oids not found in buffer for later use */
			*(buffer + index) = OBJECT_NOT_FOUND;
			index += sizeof(char);
			*((unsigned int *)(buffer+index)) = oid;
			index += sizeof(unsigned int);
		} else { /* If Obj found in machine (i.e. has not moved) */
			/* send the oid, it's size, it's header and data */
			header = (objheader_t *)mobj;
			GETSIZE(size, header);
			size += sizeof(objheader_t);
			*(buffer + index) = OBJECT_FOUND;
			index += sizeof(char);
			*((unsigned int *)(buffer+index)) = oid;
			index += sizeof(unsigned int);
			*((int *)(buffer+index)) = size;
			index += sizeof(int);
			memcpy(buffer + index, header, size);
			index += size;
			/* Calculate the oid corresponding to the offset value */
			for(i = 0 ; i< numoffset ; i++) {
				/* Check for arrays  */
				if(TYPE(header) > NUMCLASSES) {
					isArray = 1;
				}
				if(isArray == 1) {
					int elementsize = classsize[TYPE(header)];
					objoid = *((unsigned int *)(((char *)header) + sizeof(objheader_t) + sizeof(struct ArrayObject) + (elementsize*offset[i])));
				} else {
					objoid = *((unsigned int *)(((char *)header) + sizeof(objheader_t) + offset[i]));
				}
				if((header = mhashSearch(objoid)) == NULL) {
					/* Obj not found, send oid */
					*(buffer + index) = OBJECT_NOT_FOUND;
					index += sizeof(char);
					*((unsigned int *)(buffer+index)) = objoid;
					index += sizeof(unsigned int);
					break;
				} else {/* Obj Found */
					/* send the oid, it's size, it's header and data */
					GETSIZE(size, header);
					size+=sizeof(objheader_t);
					*(buffer+index) = OBJECT_FOUND;
					index += sizeof(char);
					*((unsigned int *)(buffer+index)) = objoid;
					index += sizeof(unsigned int);
					*((int *)(buffer+index)) = size;
					index += sizeof(int);
					memcpy(buffer+index, header, size);
					index += size;
					isArray = 0;
					continue;
				}
			}
		}

		/* Check for overflow in the buffer */
		if (index >= PRE_BUF_SIZE) {
			printf("Error: Buffer array overflow %s, %d\n", __FILE__, __LINE__);
			return 1;
		}
		/* Send Prefetch response control message only once*/
		if(count == 1){
			control = TRANS_PREFETCH_RESPONSE;
			if((numbytes = send(acceptfd, &control, sizeof(char), MSG_NOSIGNAL)) < sizeof(char)) {
				perror("Error: in sending PREFETCH RESPONSE to Coordinator\n");
				return 1;
			}
		}

		//Send buffer size 
		if((numbytes = send(acceptfd, &index, sizeof(unsigned int), MSG_NOSIGNAL)) < sizeof(unsigned int)) {
			perror("Error: in sending PREFETCH RESPONSE to Coordinator\n");
			return 1;
		}

		/* Send the entire buffer with its size and oids found and not found */
		if(send((int)acceptfd, &buffer, index, MSG_NOSIGNAL) < sizeof(index)) {
			perror("Error: sending oids found\n");
			return 1;
		}
	}
	return 0;
}

void processReqNotify(unsigned int numoid, unsigned int *oidarry, unsigned short *versionarry, unsigned int mid, unsigned int threadid) {
	objheader_t *header;
	unsigned int oid;
	unsigned short newversion;
 	char msg[1+  2 * sizeof(unsigned int) + sizeof(unsigned short)];
	int sd;
	struct sockaddr_in remoteAddr;
	int bytesSent;
	int status, size;

	int i = 0;
	while(i < numoid) {
		oid = *(oidarry + i);
		if((header = (objheader_t *) mhashSearch(oid)) == NULL) {
			printf("Error: mhashsearch returns NULL at %s, %d\n", __FILE__, __LINE__);
			return;
		} else {
			/* Check to see if versions are same */
checkversion:
			if ((STATUS(header) & LOCK) != LOCK) { 		
				//FIXME make locking atomic
				STATUS(header) |= LOCK;
				newversion = header->version;
				if(newversion == *(versionarry + i)) {
					//Add to the notify list 
					if((header->notifylist = insNode(header->notifylist, threadid, mid)) == NULL) {
						printf("Error: Obj notify list points to NULL %s, %d\n", __FILE__, __LINE__); 
						return;
					}
					STATUS(header) &= ~(LOCK); 		
				} else {
					STATUS(header) &= ~(LOCK); 		
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
						status = -1;
					} else {
						//Send Update notification
						msg[0] = THREAD_NOTIFY_RESPONSE;
						*((unsigned int *)&msg[1]) = oid;
						size = sizeof(unsigned int);
						*((unsigned short *)(&msg[1]+size)) = newversion;
						size += sizeof(unsigned short);
						*((unsigned int *)(&msg[1]+size)) = threadid;
						bytesSent = send(sd, msg, 1+ 2*sizeof(unsigned int) + sizeof(unsigned short), 0);
						if (bytesSent < 0){
							perror("processReqNotify():send()");
							status = -1;
						} else if (bytesSent != 1 + sizeof(unsigned short) + 2*sizeof(unsigned int)){
							printf("Error: processReqNotify(): error, sent %d bytes %s, %d\n", 
									bytesSent, __FILE__, __LINE__);
							status = -1;
						} else {
							status = 0;
						}

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

