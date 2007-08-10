/* Coordinator => Machine that initiates the transaction request call for commiting a transaction
 * Participant => Machines that host the objects involved in a transaction commit */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <pthread.h>
#include <netdb.h>
#include <fcntl.h>
#include "dstm.h"
#include "mlookup.h"
#include "llookup.h"

#define LISTEN_PORT 2156
#define BACKLOG 10 //max pending connections
#define RECEIVE_BUFFER_SIZE 2048
#define PRE_BUF_SIZE 2048

extern int classsize[];

objstr_t *mainobjstore;

int dstmInit(void)
{
	/* Initialize main object store */
	mainobjstore = objstrCreate(DEFAULT_OBJ_STORE_SIZE);	
	/* Create machine lookup table and location lookup table */
	if (mhashCreate(HASH_SIZE, LOADFACTOR))
		return 1; //failure
	
	if (lhashCreate(HASH_SIZE, LOADFACTOR))
		return 1; //failure
	
	return 0;
}

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
		acceptfd = accept(listenfd, (struct sockaddr *)&client_addr, &addrlength);
		pthread_create(&thread_dstm_accept, NULL, dstmAccept, (void *)acceptfd);
	}
	pthread_exit(NULL);
}
/* This function accepts a new connection request, decodes the control message in the connection 
 * and accordingly calls other functions to process new requests */
void *dstmAccept(void *acceptfd)
{
	int numbytes,i, val, retval;
	unsigned int oid;
	char buffer[RECEIVE_BUFFER_SIZE], control,ctrl;
	char *ptr;
	void *srcObj;
	objheader_t *h;
	trans_commit_data_t transinfo;
	
	int fd_flags = fcntl((int)acceptfd, F_GETFD), size;

	printf("Recieved connection: fd = %d\n", (int)acceptfd);
	/* Receive control messages from other machines */
	if((retval = recv((int)acceptfd, &control, sizeof(char), 0)) <= 0) {
		if (retval == 0) {
			return; // Testing connection
		}
		perror("Error in receiving control from coordinator\n");
		return;
	}
	
	switch(control) {
		case READ_REQUEST:
			/* Read oid requested and search if available */
			if((retval = recv((int)acceptfd, &oid, sizeof(unsigned int), 0)) <= 0) {
				perror("Error receiving object from cooridnator\n");
				return NULL;
			}
			srcObj = mhashSearch(oid);
			h = (objheader_t *) srcObj;
			size = sizeof(objheader_t) + sizeof(classsize[TYPE(h)]);
			if (h == NULL) {
				ctrl = OBJECT_NOT_FOUND;
				if(send((int)acceptfd, &ctrl, sizeof(char), MSG_NOSIGNAL) < sizeof(char)) {
					perror("Error sending control msg to coordinator\n");
					return NULL;
				}
			} else {
				/* Type */
			  char msg[]={OBJECT_FOUND, 0, 0, 0, 0};
			  *((int *)&msg[1])=size;
			  if(send((int)acceptfd, &msg, sizeof(msg), MSG_NOSIGNAL) < sizeof(msg)) {
				  perror("Error sending size of object to coordinator\n");
				  return NULL;
			  }
			  if(send((int)acceptfd, h, size, MSG_NOSIGNAL) < size) {
				  perror("Error in sending object\n");
				  return NULL;
			  }
			}
			break;
		
		case READ_MULT_REQUEST:
			printf("DEBUG-> READ_MULT_REQUEST\n");
			break;
	
		case MOVE_REQUEST:
			printf("DEBUG -> MOVE_REQUEST\n");
			break;

		case MOVE_MULT_REQUEST:
			printf("DEBUG -> MOVE_MULT_REQUEST\n");
			break;

		case TRANS_REQUEST:
			/* Read transaction request */
			printf("DEBUG -> Recv TRANS_REQUEST\n");
			if((val = readClientReq(&transinfo, (int)acceptfd)) != 0) {
				printf("Error in readClientReq\n");
				return;
			}
			break;
		case TRANS_PREFETCH:
			printf("DEBUG -> Recv TRANS_PREFETCH\n");
			if((val = prefetchReq((int)acceptfd)) != 0) {
				printf("Error in readClientReq\n");
				return;
			}
			break;

		default:
			printf("DEBUG -> dstmAccept: Error Unknown opcode %d\n", control);
	}

	/* Close connection */
	if (close((int)acceptfd) == -1)
		perror("close");
	else 
		printf("Closed connection: fd = %d\n", (int)acceptfd);
	
	pthread_exit(NULL);
}

/* This function reads the information available in a transaction request
 * and makes a function call to process the request */
int readClientReq(trans_commit_data_t *transinfo, int acceptfd) {
	char *ptr;
	void *modptr;
	fixed_data_t fixed;
	int sum = 0, i, N, n, val;

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
	if(fixed.nummod != 0) { // If pile contains more than one modified object,
				// allocate new object store and recv all modified objects
				// TODO deallocate this space
		if ((modptr = objstrAlloc(mainobjstore, fixed.sum_bytes)) == NULL) {
			printf("objstrAlloc error for modified objects %s, %d\n", __FILE__, __LINE__);
			return 1;
		}
		sum = 0;
		do { // Recv the objs that are modified by the Coordinator
			n = recv((int)acceptfd, modptr+sum, fixed.sum_bytes-sum, 0);
			sum += n;
		} while (sum < fixed.sum_bytes && n != 0);
	}

	/*Process the information read */
	if((val = processClientReq(&fixed, transinfo, listmid, objread, modptr, acceptfd)) != 0) {
		printf("Error in processClientReq %s, %d\n", __FILE__, __LINE__);
		return 1;
	}

	return 0;
}

/* This function processes the Coordinator's transaction request using "handleTransReq" 
 * function and sends a reply to the co-ordinator.
 * Following this it also receives a new control message from the co-ordinator and processes this message*/
int processClientReq(fixed_data_t *fixed, trans_commit_data_t *transinfo,
		unsigned int *listmid, char *objread, void *modptr, int acceptfd) {
	char *ptr, control, sendctrl;
	objheader_t *tmp_header;
	void *header;
	int  i = 0, val, retval;

	/* Send reply to the Coordinator */
	if((retval = handleTransReq(fixed, transinfo, listmid, objread, modptr,acceptfd)) == 0 ) {
		printf("Handle Trans Req error %s, %d\n", __FILE__, __LINE__);
		return 1;
	}

	/* Read new control message from Coordiator */
	if((retval = recv((int)acceptfd, &control, sizeof(char), 0)) <= 0 ) {
		perror("Error in receiving control message\n");
		return 1;
	}

	/* Process the new control message */
	switch(control) {
		case TRANS_ABORT:
			/* Set all ref counts as 1 and do garbage collection */
			ptr = modptr;
			for(i = 0; i< fixed->nummod; i++) {
				tmp_header = (objheader_t *)ptr;
				tmp_header->rcount = 1;
				ptr += sizeof(objheader_t) + classsize[TYPE(tmp_header)];
			}
			/* Unlock objects that was locked due to this transaction */
			for(i = 0; i< transinfo->numlocked; i++) {
				header = mhashSearch(transinfo->objlocked[i]);// find the header address
				STATUS(((objheader_t *)header)) &= ~(LOCK); 		
			}
		
			/* Send ack to Coordinator */
			printf("DEBUG -> Recv TRANS_ABORT\n");
			sendctrl = TRANS_SUCESSFUL;
			if(send((int)acceptfd, &sendctrl, sizeof(char), MSG_NOSIGNAL) < sizeof(char)) {
				perror("Error sending ACK to coordinator\n");
				return 1;
			}
			ptr = NULL;
			break;

		case TRANS_COMMIT:
			/* Invoke the transCommit process() */
			printf("DEBUG -> Recv TRANS_COMMIT \n");
			if((val = transCommitProcess(transinfo, (int)acceptfd)) != 0) {
				printf("Error in transCommitProcess %s, %d\n", __FILE__, __LINE__);
				return 1;
			}
			break;

		case TRANS_ABORT_BUT_RETRY_COMMIT_WITH_RELOCATING:
			//TODO expect another transrequest from client
			printf("DEBUG -> Recv TRANS_ABORT_BUT_RETRY_COMMIT_WITH_RELOCATING\n");
			break;
		default:
			printf("No response to TRANS_AGREE OR DISAGREE protocol\n");
			//TODO Use fixed.trans_id  TID since Client may have died
			break;
	}
	/* Free memory */
	printf("DEBUG -> Freeing...\n");
	fflush(stdout);
	if (transinfo->objmod != NULL) {
		free(transinfo->objmod);
		transinfo->objmod = NULL;
	}
	if (transinfo->objlocked != NULL) {
		free(transinfo->objlocked);
		transinfo->objlocked = NULL;
	}
	if (transinfo->objnotfound != NULL) {
		free(transinfo->objnotfound);
		transinfo->objnotfound = NULL;
	}
	return 0;
}

/* This function increments counters while running a voting decision on all objects involved 
 * in TRANS_REQUEST and If a TRANS_DISAGREE sends the response immediately back to the coordinator */
char handleTransReq(fixed_data_t *fixed, trans_commit_data_t *transinfo, unsigned int *listmid, char *objread, void *modptr, int acceptfd) {
	int val, i = 0;
	short version;
	char control = 0, *ptr;
	unsigned int oid;
	unsigned int *oidnotfound, *oidlocked, *oidmod;
	void *mobj;
	objheader_t *headptr;

	/* Counters and arrays to formulate decision on control message to be sent */
	oidnotfound = (unsigned int *) calloc(fixed->numread + fixed->nummod, sizeof(unsigned int)); 
	oidlocked = (unsigned int *) calloc(fixed->numread + fixed->nummod, sizeof(unsigned int)); 
	oidmod = (unsigned int *) calloc(fixed->nummod, sizeof(unsigned int));
	int objnotfound = 0, objlocked = 0, objmod =0, v_nomatch = 0, v_matchlock = 0, v_matchnolock = 0;
	int objmodnotfound = 0, nummodfound = 0;

	/* modptr points to the beginning of the object store 
	 * created at the Pariticipant. 
	 * Object store holds the modified objects involved in the transaction request */ 
	ptr = modptr;
	
	/* Process each oid in the machine pile/ group per thread */
	for (i = 0; i < fixed->numread + fixed->nummod; i++) {
		if (i < fixed->numread) {//Objs only read and not modified
			int incr = sizeof(unsigned int) + sizeof(short);// Offset that points to next position in the objread array
			incr *= i;
			oid = *((unsigned int *)(objread + incr));
			incr += sizeof(unsigned int);
			version = *((short *)(objread + incr));
		} else {//Objs modified
			headptr = (objheader_t *) ptr;
			oid = OID(headptr);
			oidmod[objmod] = oid;//Array containing modified oids
			objmod++;
			version = headptr->version;
			ptr += sizeof(objheader_t) + classsize[TYPE(headptr)];
		}
		
		/* Check if object is still present in the machine since the beginning of TRANS_REQUEST */

		if ((mobj = mhashSearch(oid)) == NULL) {/* Obj not found */
			/* Save the oids not found and number of oids not found for later use */
			//oidnotfound[objnotfound] = OID(((objheader_t *)mobj));
			oidnotfound[objnotfound] = oid;
			objnotfound++;
		} else { /* If Obj found in machine (i.e. has not moved) */
			/* Check if Obj is locked by any previous transaction */
			if ((STATUS(((objheader_t *)mobj)) & LOCK) == LOCK) { 		
				if (version == ((objheader_t *)mobj)->version) {      /* If not locked then match versions */
					v_matchlock++;
				} else {/* If versions don't match ...HARD ABORT */
					v_nomatch++;
					/* Send TRANS_DISAGREE to Coordinator */
					control = TRANS_DISAGREE;
					if((val = send(acceptfd, &control, sizeof(char), MSG_NOSIGNAL)) < sizeof(char)) {
						perror("Error in sending control to the Coordinator\n");
						return 0;
					}
					printf("DEBUG -> Sending TRANS_DISAGREE\n");
					return control;
				}
			} else {/* If Obj is not locked then lock object */
				STATUS(((objheader_t *)mobj)) |= LOCK;
			       
				/*TESTING Add random wait to make transactions run for a long time such that
				 * we can test for soft abort case */
			
				randomdelay();

				/* Save all object oids that are locked on this machine during this transaction request call */
				oidlocked[objlocked] = OID(((objheader_t *)mobj));
				objlocked++;
				if (version == ((objheader_t *)mobj)->version) { /* Check if versions match */
					v_matchnolock++;
				} else { /* If versions don't match ...HARD ABORT */
					v_nomatch++;
					control = TRANS_DISAGREE;
					/* Send TRANS_DISAGREE to Coordinator */
					if((val = send(acceptfd, &control, sizeof(char), MSG_NOSIGNAL)) < sizeof(char)) {
						perror("Error in sending control to the Coordinator\n");
						return 0;
					}
					printf("DEBUG -> Sending TRANS_DISAGREE\n");
					return control;
				}
			}
		}
	}
	
	/* Decide what control message to send to Coordinator */
	if ((val = decideCtrlMessage(fixed, transinfo, &v_matchnolock, &v_matchlock, &v_nomatch, &objnotfound, &objlocked,
					modptr, oidnotfound, oidlocked, oidmod, acceptfd)) == 0) {
		printf("Error in decideCtrlMessage %s, %d\n", __FILE__, __LINE__);
		return 0;
	}
	
	return val;

}
/* This function decides what control message such as TRANS_AGREE, TRANS_DISAGREE or TRANS_SOFT_ABORT
 * to send to Coordinator based on the votes of oids involved in the transaction */
int decideCtrlMessage(fixed_data_t *fixed, trans_commit_data_t *transinfo, int *v_matchnolock, int *v_matchlock, 
		int *v_nomatch, int *objnotfound, int *objlocked, void *modptr, 
		unsigned int *oidnotfound, unsigned int *oidlocked, unsigned int *oidmod,
		int acceptfd) {
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
		printf("DEBUG -> Sending TRANS_AGREE\n");
	}
	/* Condition to send TRANS_SOFT_ABORT */
	if((*(v_matchlock) > 0 && *(v_nomatch) == 0) || (*(objnotfound) > 0 && *(v_nomatch) == 0)) {
		control = TRANS_SOFT_ABORT;
		char msg[]={TRANS_SOFT_ABORT, 0,0,0,0};
		*((int*)&msg[1])= *(objnotfound);

		printf("DEBUG -> Sending TRANS_SOFT_ABORT\n");
		/* Send control message */
		if((val = send(acceptfd, &msg, sizeof(msg),MSG_NOSIGNAL)) < sizeof(msg)) {
			perror("Error in sending no of objects that are not found\n");
			return 0;
		}
		/* Send number of oids not found and the missing oids if objects are missing in the machine */
		if(*(objnotfound) != 0) { 
			int size = sizeof(unsigned int)* *(objnotfound);
			if((val = send(acceptfd, oidnotfound, size ,MSG_NOSIGNAL)) < size) {
				perror("Error in sending objects that are not found\n");
				return 0;
			}
		}
	}

	/* Fill out the trans_commit_data_t data structure. This is required for a trans commit process
	 * if Participant receives a TRANS_COMMIT */
	transinfo->objmod = oidmod;
	transinfo->objlocked = oidlocked;
	transinfo->objnotfound = oidnotfound;
	transinfo->modptr = modptr;
	transinfo->nummod = fixed->nummod;
	transinfo->numlocked = *(objlocked);
	transinfo->numnotfound = *(objnotfound);
	
	return control;
}

/* This function processes all modified objects involved in a TRANS_COMMIT and updates pointer 
 * addresses in lookup table and also changes version number
 * Sends an ACK back to Coordinator */
int transCommitProcess(trans_commit_data_t *transinfo, int acceptfd) {
	objheader_t *header;
	int i = 0, offset = 0;
	char control;
	/* Process each modified object saved in the mainobject store */
	for(i=0; i<transinfo->nummod; i++) {
		if((header = (objheader_t *) mhashSearch(transinfo->objmod[i])) == NULL) {
			printf("mhashsearch returns NULL at %s, %d\n", __FILE__, __LINE__);
		}
		/* Change reference count of older address and free space in objstr ?? */
		header->rcount = 1; //Not sure what would be the val

		/* Change ptr address in mhash table */
		printf("DEBUG -> removing object oid = %d\n", transinfo->objmod[i]);
		mhashRemove(transinfo->objmod[i]);
		mhashInsert(transinfo->objmod[i], (transinfo->modptr + offset));
		offset += sizeof(objheader_t) + classsize[TYPE(header)];

		/* Update object version number */
		header = (objheader_t *) mhashSearch(transinfo->objmod[i]);
		header->version += 1; 
	}
	/* Unlock locked objects */
	for(i=0; i<transinfo->numlocked; i++) {
		header = (objheader_t *) mhashSearch(transinfo->objlocked[i]);
		STATUS(header) &= ~(LOCK);
	}

	//TODO Update location lookup table

	/* Send ack to coordinator */
	control = TRANS_SUCESSFUL;
	printf("DEBUG-> TRANS_SUCESSFUL\n");
	if(send((int)acceptfd, &control, sizeof(char), MSG_NOSIGNAL) < sizeof(char)) {
		perror("Error sending ACK to coordinator\n");
	}
	
	return 0;
}

int prefetchReq(int acceptfd) {
	int i, length, sum, n, numbytes, numoffset, N, objnotfound = 0, size, count = 0;
	unsigned int oid, index = 0;
	char *ptr, buffer[PRE_BUF_SIZE];
	void *mobj;
	unsigned int objoid;
	char *header, control;
	objheader_t * head;
	
	/* Repeatedly recv the oid and offset pairs sent for prefetch */
	while(numbytes = recv((int)acceptfd, &length, sizeof(int), 0) != 0) {
		count++;
		if(length == -1)
			break;
		sum = 0;
		index = sizeof(unsigned int); // Index starts with sizeof  unsigned int because the 
					      // first 4 bytes are saved to send the
				              // size of the buffer (that is computed at the end of the loop)
		oid = recv((int)acceptfd, &oid, sizeof(unsigned int), 0);
		numoffset = (length - (sizeof(int) + sizeof(unsigned int)))/ sizeof(short);
		N = numoffset * sizeof(short);
		short offset[numoffset];
		ptr = (char *)&offset;
		/* Recv the offset values per oid */ 
		do {
			n = recv((int)acceptfd, (void *)ptr+sum, N-sum, 0); 
			sum += n; 
		} while(sum < N && n != 0);	

		/* Process each oid */
		/* Check if object is still present in the machine since the beginning of TRANS_PREFETCH */
		if ((mobj = mhashSearch(oid)) == NULL) {/* Obj not found */
			/* Save the oids not found in buffer for later use */
			*(buffer + index) = OBJECT_NOT_FOUND;
			index += sizeof(char);
			memcpy(buffer+index, &oid, sizeof(unsigned int));
			index += sizeof(unsigned int);
		} else { /* If Obj found in machine (i.e. has not moved) */
			/* send the oid, it's size, it's header and data */
			header = (char *) mobj;
			head = (objheader_t *) header; 
			size = sizeof(objheader_t) + sizeof(classsize[TYPE(head)]);
			*(buffer + index) = OBJECT_FOUND;
			index += sizeof(char);
			memcpy(buffer+index, &oid, sizeof(unsigned int));
			index += sizeof(unsigned int);
			memcpy(buffer+index, &size, sizeof(int));
			index += sizeof(int);
			memcpy(buffer + index, header, size);
			index += size;
			/* Calculate the oid corresponding to the offset value */
			for(i = 0 ; i< numoffset ; i++) {
				objoid = *((int *)(header + sizeof(objheader_t) + offset[i]));
				if((header = (char *) mhashSearch(objoid)) == NULL) {
					/* Obj not found, send oid */
					*(buffer + index) = OBJECT_NOT_FOUND;
					index += sizeof(char);
					memcpy(buffer+index, &oid, sizeof(unsigned int));
					index += sizeof(unsigned int);
					break;
				} else {/* Obj Found */
					/* send the oid, it's size, it's header and data */
					head = (objheader_t *) header; 
					size = sizeof(objheader_t) + sizeof(classsize[TYPE(head)]);
					*(buffer + index) = OBJECT_FOUND;
					index += sizeof(char);
					memcpy(buffer+index, &oid, sizeof(unsigned int));
					index += sizeof(unsigned int);
					memcpy(buffer+index, &size, sizeof(int));
					index += sizeof(int);
					memcpy(buffer + index, header, size);
					index += size;
					continue;
				}
			}
		}
		/* Check for overflow in the buffer */
		if (index >= PRE_BUF_SIZE) {
			printf("Char buffer is overflowing\n");
			return 1;
		}
		/* Send Prefetch response control message only once*/
		if(count == 1) {
			control = TRANS_PREFETCH_RESPONSE;
			if((numbytes = send(acceptfd, &control, sizeof(char), MSG_NOSIGNAL)) < sizeof(char)) {
				perror("Error in sending PREFETCH RESPONSE to Coordinator\n");
				return 1;
			}
		}

		/* Add the buffer size into buffer as a parameter */
		memcpy(buffer, &index, sizeof(unsigned int));
		/* Send the entire buffer with its size and oids found and not found */
		if(send((int)acceptfd, &buffer, sizeof(index - 1), MSG_NOSIGNAL) < sizeof(index -1)) {
			perror("Error sending oids found\n");
			return 1;
		}
	}
	return 0;
}
