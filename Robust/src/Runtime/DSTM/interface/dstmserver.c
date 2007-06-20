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

extern int classsize[];

objstr_t *mainobjstore;

int dstmInit(void)
{
	//Initialize main object store
	mainobjstore = objstrCreate(DEFAULT_OBJ_STORE_SIZE);	
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
	if((retval = recv((int)acceptfd, &control, sizeof(char), 0)) <= 0) {
		if (retval == 0) {
			return; // Testing connection
		}
		perror("Error in receiving control from coordinator\n");
		return;
	}
	switch(control) {
		case READ_REQUEST:
			if((retval = recv((int)acceptfd, &oid, sizeof(unsigned int), 0)) <= 0) {
				perror("Error receiving object from cooridnator\n");
				return;
			}
			srcObj = mhashSearch(oid);
			h = (objheader_t *) srcObj;
			size = sizeof(objheader_t) + sizeof(classsize[h->type]);
			if (h == NULL) {
				ctrl = OBJECT_NOT_FOUND;
				if(send((int)acceptfd, &ctrl, sizeof(char), MSG_NOSIGNAL) < sizeof(char)) {
					perror("Error sending control msg to coordinator\n");
				}
			} else {
				/* Type */
			  char msg[]={OBJECT_FOUND, 0, 0, 0, 0};
			  *((int *)&msg[1])=size;
			  if(send((int)acceptfd, &msg, sizeof(msg), MSG_NOSIGNAL) < sizeof(msg)) {
			    perror("Error sending size of object to coordinator\n");
			  }
			  if(send((int)acceptfd, h, size, MSG_NOSIGNAL) < size) {
			    perror("Error in sending object\n");
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
			printf("DEBUG -> Recv TRANS_REQUEST\n");
			if((val = readClientReq((int)acceptfd, &transinfo)) != 0) {
				printf("Error in readClientReq\n");
			}
			break;

		default:
			printf("DEBUG -> dstmAccept: Error Unknown opcode %d\n", control);
	}
	if (close((int)acceptfd) == -1)
		perror("close");
	else 
		printf("Closed connection: fd = %d\n", (int)acceptfd);
	
	pthread_exit(NULL);
}

// Reads transaction request per thread
int readClientReq(int acceptfd, trans_commit_data_t *transinfo) {
	char *ptr, control, prevctrl, sendctrl, newctrl;
	void *modptr, *header;
	objheader_t *tmp_header;
	fixed_data_t fixed;
	int sum = 0, i, N, n, val, retval;

	//Reads to process the TRANS_REQUEST protocol further
	// Read fixed_data
	N = sizeof(fixed) - 1;
	ptr = (char *)&fixed;;
	fixed.control = TRANS_REQUEST;
	do {
		n = recv((int)acceptfd, (void *) ptr+1+sum, N-sum, 0);
		sum += n;
	} while(sum < N && n != 0); 

	// Read list of mids
	int mcount = fixed.mcount;
	N = mcount * sizeof(unsigned int);
	unsigned int listmid[mcount];
	ptr = (char *) listmid;
	sum = 0;
	do {
		n = recv((int)acceptfd, (void *) ptr+sum, N-sum, 0);
		sum += n;
	} while(sum < N && n != 0);

	// Read oid and version tuples
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
	
	// Read modified objects
	if(fixed.nummod != 0) { // If pile contains more than one modified object,
				// allocate new object store and recv all modified objects
		if ((modptr = objstrAlloc(mainobjstore, fixed.sum_bytes)) == NULL) {
			printf("objstrAlloc error for modified objects %s, %d\n", __FILE__, __LINE__);
			return 1;
		}
		sum = 0;
		do { // Recv the objs that are modified at Coordinator
			n = recv((int)acceptfd, modptr+sum, fixed.sum_bytes-sum, 0);
			sum += n;
		} while (sum < fixed.sum_bytes && n != 0);
	}

	// Process the information available in the TRANS_REQUEST control
	//Send control message as per all votes from all oids in the machine
	if((prevctrl = handleTransReq(acceptfd, &fixed, transinfo, listmid, objread, modptr)) == 0 ) {
		printf("Handle Trans Req error %s, %d\n", __FILE__, __LINE__);
		return 1;
	}
	//Read for new control message from Coordiator
	if((retval = recv((int)acceptfd, &control, sizeof(char), 0)) <= 0 ) {
		perror("Error in receiving control message\n");
		return 1;
	}

	switch(control) {
		case TRANS_ABORT:
			//Mark all ref counts as 1 and do garbage collection
			ptr = modptr;
			for(i = 0; i< fixed.nummod; i++) {
				tmp_header = (objheader_t *)ptr;
				tmp_header->rcount = 1;
				ptr += sizeof(objheader_t) + classsize[tmp_header->type];
			}
			//Unlock objects that was locked in this machine due to this transaction
			for(i = 0; i< transinfo->numlocked; i++) {
				printf("DEBUG-> Unlocking objects\n");
				header = mhashSearch(transinfo->objlocked[i]);// find the header address
				((objheader_t *)header)->status &= ~(LOCK); 		
			}
		
			//send ack to coordinator
			printf("DEBUG -> Recv TRANS_ABORT\n");
			sendctrl = TRANS_SUCESSFUL;
			if(send((int)acceptfd, &sendctrl, sizeof(char), MSG_NOSIGNAL) < sizeof(char)) {
				perror("Error sending ACK to coordinator\n");
				return 1;
			}
		
			ptr = NULL;
			return 0;
		case TRANS_COMMIT:
			printf("DEBUG -> Recv TRANS_COMMIT \n");
			if((val = transCommitProcess(transinfo, (int)acceptfd)) != 0) {
				printf("Error in transCommitProcess %s, %d\n", __FILE__, __LINE__);
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
	//Free memory
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

//This function runs a decision after all objects involved in TRANS_REQUEST 
//and returns the appropriate control message such as TRANS_AGREE, TRANS_DISAGREE or TRANS_SOFT_ABORT to the Ccordinator 
char handleTransReq(int acceptfd, fixed_data_t *fixed, trans_commit_data_t *transinfo, unsigned int *listmid, char *objread, void *modptr) {
	int val;
	short version;
	char control = 0, ctrlmissoid, *ptr;
	int i, j = 0;
	unsigned int oid;
	unsigned int *oidnotfound, *oidlocked, *oidmod;

	oidnotfound = (unsigned int *) calloc(fixed->numread + fixed->nummod, sizeof(unsigned int)); 
	oidlocked = (unsigned int *) calloc(fixed->numread + fixed->nummod, sizeof(unsigned int)); 
	oidmod = (unsigned int *) calloc(fixed->nummod, sizeof(unsigned int));
	// Counters and arrays to formulate decision on control message to be sent
	// version match or  no match
	int objnotfound = 0, objlocked = 0, objmod =0, v_nomatch = 0, v_matchlock = 0, v_matchnolock = 0;
	int objmodnotfound = 0, nummodfound = 0;
	void *mobj;
	objheader_t *headptr;
	
	//Process each object present in the pile 
	ptr = modptr;
	
	//Process each oid in the machine pile/ group per thread
	//Should be a new function
	for (i = 0; i < fixed->numread + fixed->nummod; i++) {
		if (i < fixed->numread) {//Object is read
			int incr = sizeof(unsigned int) + sizeof(short);// Offset that points to next position in the objread array
			incr *= i;
			oid = *((unsigned int *)(objread + incr));
			incr += sizeof(unsigned int);
			version = *((short *)(objread + incr));
		} else {//Obj is modified
			headptr = (objheader_t *) ptr;
			oid = headptr->oid;
			oidmod[objmod] = oid;//Array containing modified oids
			objmod++;
			version = headptr->version;
			ptr += sizeof(objheader_t) + classsize[headptr->type];
		}
		//Check if object is still present in the machine since the beginning of TRANS_REQUEST
		if ((mobj = mhashSearch(oid)) == NULL) {// Obj not found
			//Save the oids not found for later use
			oidnotfound[objnotfound] = ((objheader_t *)mobj)->oid;
			objnotfound++;
		} else { // If obj found in machine (i.e. has not moved)
			//Check if obj is locked
			if ((((objheader_t *)mobj)->status & LOCK) == LOCK) { 		
				if (version == ((objheader_t *)mobj)->version) {      // If version match
					printf("DEBUG -> obj = %d locked\n", ((objheader_t *)mobj)->oid);
					v_matchlock++;
				} else {//If versions don't match ..HARD ABORT
					v_nomatch++;
					//send TRANS_DISAGREE to Coordinator
					control = TRANS_DISAGREE;
					if((val = send(acceptfd, &control, sizeof(char),MSG_NOSIGNAL)) < sizeof(char)) {
						perror("Error in sending control to the Coordinator\n");
						return 0;
					}
					printf("DEBUG -> Sending TRANS_DISAGREE accept_fd = %d\n", acceptfd);
					return control;
				}
			} else {//Obj is not locked , so lock object
				((objheader_t *)mobj)->status |= LOCK;
			        // TESTING Add sleep to make transactions run for a long time such that 
				// we can test for soft abort case
				sleep(1);
				//Save all object oids that are locked on this machine during this transaction request call
				oidlocked[objlocked] = ((objheader_t *)mobj)->oid;
				objlocked++;
				if (version == ((objheader_t *)mobj)->version) { //If versions match
					v_matchnolock++;
				} else { //If versions don't match
					v_nomatch++;
					//send TRANS_DISAGREE to Coordinator
					control = TRANS_DISAGREE;
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
	
	//Decide what control message(s) to send
	// Should be a new function
	if(v_matchnolock == fixed->numread + fixed->nummod) {
		//send TRANS_AGREE to Coordinator
		control = TRANS_AGREE;
		if((val = send(acceptfd, &control, sizeof(char), MSG_NOSIGNAL)) < sizeof(char)) {
			perror("Error in sending control to Coordinator\n");
			return 0;
		}
		printf("DEBUG -> Sending TRANS_AGREE\n");
	}
	//Condition to send TRANS_SOFT_ABORT
	if((v_matchlock > 0 && v_nomatch == 0) || (objnotfound > 0 && v_nomatch == 0)) {
		control = TRANS_SOFT_ABORT;
		char msg[]={TRANS_SOFT_ABORT, 0,0,0,0};
		*((int*)&msg[1])= objnotfound;

		printf("DEBUG -> Sending TRANS_SOFT_ABORT\n");
		//Sending control message
		if((val = send(acceptfd, &msg, sizeof(msg),MSG_NOSIGNAL)) < sizeof(msg)) {
			perror("Error in sending no of objects that are not found\n");
			return 0;
		}
		//send number of oids not found and the missing oids if objects are missing in the machine
		if(objnotfound != 0) { 
		  int size = sizeof(unsigned int)*objnotfound;
		  if((val = send(acceptfd, oidnotfound, size ,MSG_NOSIGNAL)) < size) {
				perror("Error in sending objects that are not found\n");
				return 0;
			}
		}
	}
	
	//Do the following when TRANS_DISAGREE is sent
	if(control == TRANS_DISAGREE) {
		//Set the reference count of the object to 1 in mainstore for garbage collection
		ptr = modptr;
		for(i = 0; i< fixed->nummod; i++) {
			headptr = (objheader_t *) ptr;
			headptr->rcount = 1;
			ptr += sizeof(objheader_t) + classsize[headptr->type];
		}
		//Unlock objects that was locked in the trans
		for(i = 0; i< objlocked ; i++) {
			mobj = mhashSearch(oidlocked[i]);// find the header address
			((objheader_t *)mobj)->status &= ~(LOCK); 		
		}	
	}	

	//Fill out the structure required for a trans commit process if pile receives a TRANS_COMMIT
	transinfo->objmod = oidmod;
	transinfo->objlocked = oidlocked;
	transinfo->objnotfound = oidnotfound;
	transinfo->modptr = modptr;
	transinfo->nummod = fixed->nummod;
	transinfo->numlocked = objlocked;
	transinfo->numnotfound = objnotfound;
	
	return control;
}

//Process oids in the TRANS_COMMIT requested by the participant and sends an ACK back to Coordinator
int transCommitProcess(trans_commit_data_t *transinfo, int acceptfd) {
	objheader_t *header;
	int i = 0, offset = 0;
	char control;
	//Process each modified object saved in the mainobject store
	for(i=0; i<transinfo->nummod; i++) {
		if((header = (objheader_t *) mhashSearch(transinfo->objmod[i])) == NULL) {
			printf("mhashsearch returns NULL at %s, %d\n", __FILE__, __LINE__);
		}
		//change reference count of older address and free space in objstr ??
		header->rcount = 1; //Not sure what would be th val
		//change ptr address in mhash table
		printf("DEBUG -> removing object oid = %d\n", transinfo->objmod[i]);
		mhashRemove(transinfo->objmod[i]);
		mhashInsert(transinfo->objmod[i], (transinfo->modptr + offset));
		offset += sizeof(objheader_t) + classsize[header->type];
		//update object version
		header = (objheader_t *) mhashSearch(transinfo->objmod[i]);
		header->version += 1; 
	}
	for(i=0; i<transinfo->numlocked; i++) {
		//unlock objects
		header = (objheader_t *) mhashSearch(transinfo->objlocked[i]);
		header->status &= ~(LOCK);
	}

	//TODO Update location lookup table

	//send ack to coordinator
	control = TRANS_SUCESSFUL;
	printf("DEBUG-> TRANS_SUCESSFUL\n");
	if(send((int)acceptfd, &control, sizeof(char), MSG_NOSIGNAL) < sizeof(char)) {
		perror("Error sending ACK to coordinator\n");
	}
	
	return 0;
}

