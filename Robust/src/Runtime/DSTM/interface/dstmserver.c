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
	//todo:initialize main object store
	//do we want this to be a global variable, or provide
	//separate access funtions and hide the structure?
	mainobjstore = objstrCreate(DEFAULT_OBJ_STORE_SIZE);	
	if (mhashCreate(HASH_SIZE, LOADFACTOR))
		return 1; //failure
	
	if (lhashCreate(HASH_SIZE, LOADFACTOR))
		return 1; //failure
	
	//pthread_t threadListen;
	//pthread_create(&threadListen, NULL, dstmListen, NULL);
	
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

	listenfd = socket(AF_INET, SOCK_STREAM, 0);
	if (listenfd == -1)
	{
		perror("socket");
		exit(1);
	}

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
	int numbytes,i, val;
	unsigned int oid;
	char buffer[RECEIVE_BUFFER_SIZE], control;
	char *ptr;
	void *srcObj;
	objheader_t *h;
	
	int fd_flags = fcntl((int)acceptfd, F_GETFD), size;

	printf("Recieved connection: fd = %d\n", (int)acceptfd);
	recv((int)acceptfd, &control, sizeof(char), 0);
	switch(control) {
		case READ_REQUEST:
			recv((int)acceptfd, &oid, sizeof(unsigned int), 0);
			srcObj = mhashSearch(oid);
			h = (objheader_t *) srcObj;
			if (h == NULL) {
				buffer[0] = OBJECT_NOT_FOUND;
			} else {
				buffer[0] = OBJECT_FOUND;
				size = sizeof(objheader_t) + sizeof(classsize[h->type]);
				memcpy(buffer+1, srcObj, size);
			}
			if(send((int)acceptfd, (void *)buffer, sizeof(buffer), 0) < 0) {
				perror("");
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
			//printf("DEBUG -> TRANS_REQUEST\n");
			if((val = readClientReq((int)acceptfd)) == 1) {
				printf("Error in readClientReq\n");
			}
			break;

		default:
			printf("Error receiving\n");
	}
	
	//Read for new control message from Coordiator
	recv((int)acceptfd, &control, sizeof(char), 0);
	switch(control) {
		case TRANS_ABORT:
			printf("DEBUG -> TRANS_ABORT\n");
			write((int)acceptfd, &control, sizeof(char));
			break;

		case TRANS_COMMIT:
			printf("DEBUG -> TRANS_COMMIT\n");
			write((int)acceptfd, &control, sizeof(char));
			//TODO 
			//change ptr address in mhash table
			//unlock objects
			//update object version
			//change reference count of older address??
			//free space in objstr ??
			//Update location lookup table
			break;
	}

	if (close((int)acceptfd) == -1)
	{
		perror("close");
	}
	else
		printf("Closed connection: fd = %d\n", (int)acceptfd);
	
	pthread_exit(NULL);
}

int readClientReq(int acceptfd) {
	char *ptr, control;
	void *modptr;
	objheader_t *h, tmp_header;
	fixed_data_t fixed;
	int sum = 0, N, n;

	// Read fixed_data
	N = sizeof(fixed) - 1;
	ptr = (char *)&fixed;;
	fixed.control = TRANS_REQUEST;
	do {
		n = recv((int)acceptfd, (void *) ptr+1+sum, N-sum, 0);
	//	printf("DEBUG -> 1. Reading %d bytes \n", n);
		sum += n;
	} while(sum < N && n != 0); 

	//printf("Machine count = %d\tnumread = %d\tnummod = %d\tsum_bytes = %d\n", fixed.mcount, fixed.numread, fixed.nummod, fixed.sum_bytes);
	// Read list of mids
	int mcount = fixed.mcount;
	N = mcount * sizeof(unsigned int);
	unsigned int listmid[mcount];
	ptr = (char *) listmid;
	sum = 0;
	do {
		n = recv((int)acceptfd, (void *) ptr+sum, N-sum, 0);
	//	printf("DEBUG -> 2. Reading %d bytes cap = %d\n", n, N);
		sum += n;
	} while(sum < N && n != 0);

	// Read oid and version tuples
	int numread = fixed.numread;
	N = numread * (sizeof(unsigned int) + sizeof(short));
	char objread[N];
	sum = 0;
	do {
		n = recv((int)acceptfd, (void *) objread, N, 0);
	//	printf("DEBUG -> 3. Reading %d bytes cap = %d\n", n, N);
		sum += n;
	} while(sum < N && n != 0);
	//printf("DEBUG -> %d %d %d %d\n", *objread, *(objread + 6), *(objread + 12), *(objread + 18));

	// Read modified objects
	if ((modptr = objstrAlloc(mainobjstore, fixed.sum_bytes)) == NULL) {
	//	printf("objstrAlloc error for modified objects %s, %d", __FILE__, __LINE__);
		return 1;
	}
	sum = 0;
	do {
		n = recv((int)acceptfd, modptr+sum, fixed.sum_bytes-sum, 0);
		//printf("DEBUG -> 4. Reading %d bytes cap = %d, oid = %d\n", n, fixed.sum_bytes, *((int *)modptr));
		sum += n;
	} while (sum < fixed.sum_bytes && n != 0);
	//Send control message as per all votes from the particpants
	handleTransReq(acceptfd, &fixed, listmid, objread, modptr);

	
	
	return 0;
}

//This function runs a decision after all objects are weighed under one of the 4 possibilities 
//and returns the appropriate control message to the Ccordinator 
int handleTransReq(int acceptfd, fixed_data_t *fixed, unsigned int *listmid, char *objread, void *modptr) {
	short version;
	char control, *ptr;
	int i;
	unsigned int oid, oidnotfound[fixed->numread + fixed->nummod], oidlocked[fixed->nummod + fixed->numread];
	int objnotfound = 0, objlocked = 0, v_nomatch = 0, v_matchlock = 0, v_matchnolock = 0;// Counters to formulate decision on control message to be sent
	void *mobj;
	objheader_t *headptr;
	objinfo_t objinfo[fixed->nummod + fixed->numread];// Structure that saves the possibility per object(if version match, object not found on machine etc)
	
	//Process each object present in the pile 
	ptr = modptr;
	//Process each oid in the machine pile/ group
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
			version = headptr->version;
			ptr += sizeof(objheader_t) + classsize[headptr->type];
		}
		//Check if object is still present in the machine since the beginning of TRANS_REQUEST
		if ((mobj = mhashSearch(oid)) == NULL) {// Obj not found
			objinfo[i].poss_val = OBJECT_NOT_FOUND;
			//Save the oids not found for later use
			oidnotfound[objnotfound] = ((objheader_t *)mobj)->oid;
			objnotfound++;
		} else { // If obj found in machine (i.e. has not moved)
			//Check if obj is locked
			if ((((objheader_t *)mobj)->status & LOCK) == LOCK) { 		
				if (version == ((objheader_t *)mobj)->version) {      // If version match
					objinfo[i].poss_val = OBJ_LOCKED_BUT_VERSION_MATCH;
					v_matchlock++;
				} else {//If versions don't match ..HARD ABORT
					objinfo[i].poss_val = VERSION_NO_MATCH;
					v_nomatch++;
					//send TRANS_DISAGREE to Coordinator
					control = TRANS_DISAGREE;
					write(acceptfd, &control, sizeof(char));
					//TODO when TRANS_DISAGREE is sent
					//Free space allocated in main objstore
					//Unlock objects that was locked in the trans
					return 0;
				}
			} else {//Obj is not locked , so lock object
				((objheader_t *)mobj)->status |= LOCK;
				//Save all object oids that are locked on this machine during this transaction request call
				oidlocked[objlocked] = ((objheader_t *)mobj)->oid;
				objlocked++;
				if (version == ((objheader_t *)mobj)->version) { //If versions match
					objinfo[i].poss_val = OBJ_UNLOCK_BUT_VERSION_MATCH;
					v_matchnolock++;
				} else { //If versions don't match
					objinfo[i].poss_val = VERSION_NO_MATCH;
					v_nomatch++;
					//send TRANS_DISAGREE to Coordinator
					control = TRANS_DISAGREE;
					write(acceptfd, &control, sizeof(char));
					return 0;
				}
			}
		}
	}

	//Decide what control message(s) to send
	if(v_matchnolock == fixed->numread + fixed->nummod) {
		//send TRANS_AGREE to Coordinator
		control = TRANS_AGREE;
		write(acceptfd, &control, sizeof(char));
	}
	
	if(objnotfound > 0 && v_matchlock == 0 && v_nomatch == 0) {
		//send TRANS_AGREE_BUT_MISSING_OBJECTS to Coordinator
		control = TRANS_AGREE_BUT_MISSING_OBJECTS;
		write(acceptfd, &control, sizeof(char));
		//send missing oids  and number of oids not found with it
		write(acceptfd, &objnotfound, sizeof(int));
		write(acceptfd, oidnotfound, (sizeof(unsigned int) * objnotfound));
	}
	
	if(v_matchlock > 0 && v_nomatch == 0) {
		//send TRANS_SOFT_ABORT to Coordinator
		control = TRANS_SOFT_ABORT;
		write(acceptfd, &control, sizeof(char));
		//send missing oids  and number of oids not found with it
		write(acceptfd, &objnotfound, sizeof(int));
		write(acceptfd, oidnotfound, (sizeof(unsigned int) * objnotfound));
	}
	
	//TODO when TRANS_DISAGREE is sent
	//Free space allocated in main objstore
	//Unlock objects that was locked in the trans
	if(control == TRANS_DISAGREE) {
		for(i = 0; i< objlocked ; i++) {
			mobj = mhashSearch(oidlocked[i]);// find the header address
			((objheader_t *)mobj)->status &= ~(LOCK); 		
		}	
	}	
	return 0;
}
