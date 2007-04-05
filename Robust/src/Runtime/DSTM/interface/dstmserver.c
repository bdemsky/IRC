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
	char buffer[RECEIVE_BUFFER_SIZE], control,ctrl;
	char *ptr;
	void *srcObj;
	objheader_t *h;
	trans_commit_data_t transinfo;
	
	int fd_flags = fcntl((int)acceptfd, F_GETFD), size;

	printf("Recieved connection: fd = %d\n", (int)acceptfd);
	recv((int)acceptfd, &control, sizeof(char), 0);
	switch(control) {
		case READ_REQUEST:
			printf("DEBUG -> Recv READ_REQUEST from Coordinator\n");
			recv((int)acceptfd, &oid, sizeof(unsigned int), 0);
			srcObj = mhashSearch(oid);
			h = (objheader_t *) srcObj;
			size = sizeof(objheader_t) + sizeof(classsize[h->type]);
			if (h == NULL) {
				ctrl = OBJECT_NOT_FOUND;
				if(send((int)acceptfd, &ctrl, sizeof(char), 0) < 0) {
					perror("Error sending control msg to coordinator\n");
				}
			} else {
				//char responsemessage[sizeof(char)+sizeof(int)];
				/* Type */
				ctrl = OBJECT_FOUND;
				if(send((int)acceptfd, &ctrl, sizeof(char), 0) < 0) {
					perror("Error sending control msg to coordinator\n");
				}

				//responsemessage[0]=OBJECT_FOUND;
				/* Size of object */
				//*((int *)(&responsemessage[1])) = sizeof(objheader_t) + classsize[h->type];
				//if(send((int)acceptfd, &responsemessage, sizeof(responsemessage), 0) < 0) {
				//	perror("Error sending control msg to coordinator\n");
				//}

				/* Size of object */
				if(send((int)acceptfd, &size, sizeof(int), 0) < 0) {
					perror("Error sending size of object to coordinator\n");
				}
				if(send((int)acceptfd, h, size, 0) < 0) {
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
			printf("DEBUG -> Recv TRANS_REQUEST from Coordinator\n");
			if((val = readClientReq((int)acceptfd, &transinfo)) != 0) {
				printf("Error in readClientReq\n");
			}
			break;

		default:
			printf("Error receiving\n");
	}
	if (close((int)acceptfd) == -1)
	{
		perror("close");
	}
	else
		printf("Closed connection: fd = %d\n", (int)acceptfd);
	
	//Free memory
	free(transinfo.objmod);
	free(transinfo.objlocked);
	free(transinfo.objnotfound);
	pthread_exit(NULL);
}

int readClientReq(int acceptfd, trans_commit_data_t *transinfo) {
	char *ptr, control, prevctrl, sendctrl, newctrl;
	void *modptr, *header;
	objheader_t *tmp_header;
	fixed_data_t fixed;
	int sum = 0, i, N, n, val;

	//Reads to process the TRANS_REQUEST protocol further
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
		printf("objstrAlloc error for modified objects %s, %d", __FILE__, __LINE__);
		return 1;
	}
	sum = 0;
	do {
		n = recv((int)acceptfd, modptr+sum, fixed.sum_bytes-sum, 0);
		//printf("DEBUG -> 4. Reading %d bytes cap = %d, oid = %d\n", n, fixed.sum_bytes, *((int *)modptr));
		sum += n;
	} while (sum < fixed.sum_bytes && n != 0);
	
	//Send control message as per all votes from all oids in the machine
	if((prevctrl = handleTransReq(acceptfd, &fixed, transinfo, listmid, objread, modptr)) == 0 ) {
		printf("Handle req error\n");
	}
		
	//Read for new control message from Coordiator
	recv((int)acceptfd, &control, sizeof(char), 0);
	switch(control) {
		case TRANS_ABORT:
			printf("DEBUG -> Recv TRANS_ABORT from Coordinator\n");
			//send ack to coordinator
			sendctrl = TRANS_SUCESSFUL;
			if(send((int)acceptfd, &sendctrl, sizeof(char), 0) < 0) {
				perror("Error sending ACK to coordinator\n");
			}
			//Mark all ref counts as 1 and do garbage collection
			ptr = modptr;
			for(i = 0; i< fixed.nummod; i++) {
				tmp_header = (objheader_t *)ptr;
				tmp_header->rcount = 1;
				ptr += sizeof(objheader_t) + classsize[tmp_header->type];
			}
			//Unlock objects that was locked in this machine due to this transaction
			for(i = 0; i< transinfo->numlocked; i++) {
				header = mhashSearch(transinfo->objlocked[i]);// find the header address
				((objheader_t *)header)->status &= ~(LOCK); 		
			}
			ptr = NULL;
			return 0;

		case TRANS_COMMIT:
			printf("DEBUG -> Recv TRANS_COMMIT from Coordinator\n");
			if((val = transCommitProcess(transinfo, (int)acceptfd)) != 0) {
				printf("Error in transCommitProcess %s, %d\n", __FILE__, __LINE__);
			}
			break;
		case TRANS_ABORT_BUT_RETRY_COMMIT:
			printf("DEBUG -> Recv TRANS_ABORT_BUT_RETRY_COMMIT from Coordinator\n");
			//Process again after waiting for sometime and on prev control message sent
			switch(prevctrl) {
				case TRANS_AGREE:
					sendctrl = TRANS_AGREE;
					if(send((int)acceptfd, &sendctrl, sizeof(char), 0) < 0) {
						perror("Error sending ACK to coordinator\n");
					}
					sleep(5);
					break;
				case TRANS_SOFT_ABORT:
					if((newctrl = handleTransReq(acceptfd, &fixed, transinfo, listmid, objread, modptr)) == 0 ) {
						printf("Handle req error\n");
					}
					if(newctrl == prevctrl){
						//Send ABORT
						newctrl = TRANS_DISAGREE;
						if(send((int)acceptfd, &newctrl, sizeof(char), 0) < 0) {
							perror("Error sending ACK to coordinator\n");
						}
						//Set the reference count of the object to 1 in mainstore for garbage collection
						ptr = modptr;
						for(i = 0; i< fixed.nummod; i++) {
							tmp_header = (objheader_t *) ptr;
							tmp_header->rcount = 1;
							ptr += sizeof(objheader_t) + classsize[tmp_header->type];
						}
						//Unlock objects that was locked in this machine due to this transaction
						for(i = 0; i< transinfo->numlocked; i++) {
							ptr = mhashSearch(transinfo->objlocked[i]);// find the header address
							((objheader_t *)ptr)->status &= ~(LOCK); 		
						}
						return 0;
					} else {
						//Send new control message
						if(send((int)acceptfd, &newctrl, sizeof(char), 0) < 0) {
							perror("Error sending ACK to coordinator\n");
						}
					}
						
					break;
			}
			
			break;
		case TRANS_ABORT_BUT_RETRY_COMMIT_WITH_RELOCATING:
			//TODO expect another transrequest from client
			printf("DEBUG -> Recv TRANS_ABORT_BUT_RETRY_COMMIT_WITH_RELOCATING from Coordinator\n");
			break;
		default:
			printf("No response to TRANS_AGREE OR DISAGREE protocol\n");
			break;
	}

	return 0;
}

//This function runs a decision after all objects are weighed under one of the 4 possibilities 
//and returns the appropriate control message to the Ccordinator 
char handleTransReq(int acceptfd, fixed_data_t *fixed, trans_commit_data_t *transinfo, unsigned int *listmid, char *objread, void *modptr) {
	short version;
	char control = 0, ctrlmissoid, *ptr;
	int i, j = 0;
	unsigned int oid;
	unsigned int *oidnotfound, *oidlocked, *oidmod;

	oidnotfound = (unsigned int *) calloc(fixed->numread + fixed->nummod, sizeof(unsigned int)); 
	oidlocked = (unsigned int *) calloc(fixed->numread + fixed->nummod, sizeof(unsigned int)); 
	oidmod = (unsigned int *) calloc(fixed->nummod, sizeof(unsigned int));

	// Counters and arrays to formulate decision on control message to be sent
	int objnotfound = 0, objlocked = 0, objmod =0, v_nomatch = 0, v_matchlock = 0, v_matchnolock = 0;
	int objmodnotfound = 0, nummodfound = 0;
	void *mobj;
	objheader_t *headptr;
	
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
					v_matchlock++;
				} else {//If versions don't match ..HARD ABORT
					v_nomatch++;
					//send TRANS_DISAGREE to Coordinator
					control = TRANS_DISAGREE;
					write(acceptfd, &control, sizeof(char));
					printf("DEBUG -> Sending TRANS_DISAGREE\n");
					return control;
				}
			} else {//Obj is not locked , so lock object
				((objheader_t *)mobj)->status |= LOCK;
				//Save all object oids that are locked on this machine during this transaction request call
				oidlocked[objlocked] = ((objheader_t *)mobj)->oid;
				objlocked++;
				if (version == ((objheader_t *)mobj)->version) { //If versions match
					v_matchnolock++;
				} else { //If versions don't match
					v_nomatch++;
					//send TRANS_DISAGREE to Coordinator
					control = TRANS_DISAGREE;
					write(acceptfd, &control, sizeof(char));
					printf("DEBUG -> Sending TRANS_DISAGREE\n");
					return control;
				}
			}
		}
	}

	printf("No of objs locked = %d\n", objlocked);
	printf("No of v_nomatch = %d\n", v_nomatch);
	printf("No of objs v_match but are did not have locks before = %d\n", v_matchnolock);
	printf("No of objs v_match but had locks before = %d\n", v_matchlock);
	printf("No of objs not found = %d\n", objnotfound);
	printf("No of objs modified but not found = %d\n", objmodnotfound);

	//Decide what control message(s) to send
	if(v_matchnolock == fixed->numread + fixed->nummod) {
		//send TRANS_AGREE to Coordinator
		control = TRANS_AGREE;
		write(acceptfd, &control, sizeof(char));
		printf("DEBUG -> Sending TRANS_AGREE\n");
	}

	if((v_matchlock > 0 && v_nomatch == 0) || (objnotfound > 0 && v_nomatch == 0)) {
		//send TRANS_SOFT_ABORT to Coordinator
		control = TRANS_SOFT_ABORT;
		write(acceptfd, &control, sizeof(char));
		printf("DEBUG -> Sending TRANS_SOFT_ABORT\n");
		//send number of oids not found and the missing oids 
		write(acceptfd, &objnotfound, sizeof(int));
		if(objnotfound != 0) 
			write(acceptfd, oidnotfound, (sizeof(unsigned int) * objnotfound));
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

//Processes oids in the TRANS_COMMIT request at the participant end and sends an ack back
int transCommitProcess(trans_commit_data_t *transinfo, int acceptfd) {
	objheader_t *header;
	int i = 0, offset = 0;
	char control;
	//Process each modified object saved in the mainobject store
	for(i=0; i<transinfo->nummod; i++) {
		if((header = (objheader_t *) mhashSearch(transinfo->objmod[i])) == NULL) {
			printf("mhashserach returns NULL\n");
		}
		//change reference count of older address and free space in objstr ??
		header->rcount = 1; //Not sure what would be th val
		//change ptr address in mhash table
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
	if(send((int)acceptfd, &control, sizeof(char), 0) < 0) {
		perror("Error sending ACK to coordinator\n");
	}

	return 0;
}
