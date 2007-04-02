#include "dstm.h"
#include "clookup.h"
#include "mlookup.h"
#include "llookup.h"
#include "plookup.h"
#include<pthread.h>
#include<sys/types.h>
#include<sys/socket.h>
#include<netdb.h>
#include<netinet/in.h>

#define LISTEN_PORT 2156
#define MACHINE_IP "127.0.0.1"
#define RECEIVE_BUFFER_SIZE 2048

extern int classsize[];

transrecord_t *transStart()
{
	transrecord_t *tmp = malloc(sizeof(transrecord_t));
	tmp->cache = objstrCreate(1048576);
	tmp->lookupTable = chashCreate(HASH_SIZE, LOADFACTOR);
	return tmp;
}

objheader_t *transRead(transrecord_t *record, unsigned int oid)
{	
	unsigned int machinenumber;
	objheader_t *tmp, *objheader;
	void *objcopy;
	int size;
	void *buf;
		//check cache
	if((objheader =(objheader_t *)chashSearch(record->lookupTable, oid)) != NULL){
		return(objheader);
	} else if ((objheader = (objheader_t *) mhashSearch(oid)) != NULL) {
		//Look up in Machine lookup table and found
		printf("oid not found in local cache\n");
		tmp = mhashSearch(oid);
		size = sizeof(objheader_t)+classsize[tmp->type];
		//Copy into cache
		objcopy = objstrAlloc(record->cache, size);
		memcpy(objcopy, (void *)tmp, size);
		//Insert into cache's lookup table
		chashInsert(record->lookupTable, objheader->oid, objcopy); 
		return(objcopy);
	} else {
		//Get the object from the remote location
		//printf("oid not found in local machine lookup\n");
		machinenumber = lhashSearch(oid);
		objcopy = getRemoteObj(record, machinenumber, oid);
		if(objcopy == NULL)
			//If object is not found in Remote location
			printf("Object not found in Machine %d\n", machinenumber);
		else
			return(objcopy);
	} 
}

objheader_t *transCreateObj(transrecord_t *record, unsigned short type)
{
	objheader_t *tmp = (objheader_t *) objstrAlloc(record->cache, (sizeof(objheader_t) + classsize[type]));
	tmp->oid = getNewOID();
	tmp->type = type;
	tmp->version = 1;
	tmp->rcount = 0; //? not sure how to handle this yet
	tmp->status = 0;
	tmp->status |= NEW;
	chashInsert(record->lookupTable, tmp->oid, tmp);
	return tmp;
}
//TODO Change the values inside write() to exact size of the message being sent 
//int decideResponse(thread_data_array_t *tdata, char *control, int sd) {
int decideResponse(thread_data_array_t *tdata, int sd) {
	int i, j, n, N, sum, oidcount = 0, transagree = 0, transdisagree = 0, transsoftabort = 0, transmiss = 0;
	char ctrl, control, *ptr;
	unsigned int *oidnotfound[tdata->pilecount];
	objheader_t *header;

	//Check common data structure 
	for (i = 0 ; i < tdata->pilecount ; i++) {
		//Switch case
		control = tdata->recvmsg[i].rcv_status;
		switch(control) {
			case TRANS_DISAGREE:
				printf("DEBUG-> Inside TRANS_DISAGREE\n");
				transdisagree++;
				//Free transaction records
				objstrDelete(tdata->rec->cache);
				chashDelete(tdata->rec->lookupTable);
				free(tdata->rec);
				//send Abort
				ctrl = TRANS_ABORT;
				if (write(sd, &ctrl, sizeof(char)) < 0) {
					perror("Error sending ctrl message for participant\n");
					return 1;
				}
				break;
				
			case TRANS_AGREE:
				printf("DEBUG-> Inside TRANS_AGREE\n");
				transagree++;
				break;
				
			case TRANS_SOFT_ABORT:
				printf("DEBUG-> Inside TRANS_SOFT_ABORT\n");
				transsoftabort++;
				//Read list of objects missing
				if(read(sd, &oidcount, sizeof(int)) != 0) {
					if (oidcount == 0) {
						sleep(2);
						break;
					}
					N = oidcount * sizeof(unsigned int);
					if((oidnotfound[i] = calloc(oidcount, sizeof(unsigned int))) == NULL) {
						printf("Calloc error %s, %d\n", __FILE__, __LINE__);
					}
					ptr = (char *) oidnotfound[i];
					do {
						n = read(sd, ptr+sum, N-sum);
						sum += n;
					} while(sum < N	&& n !=0);
				}
				break;
				
			case TRANS_AGREE_BUT_MISSING_OBJECTS:
				printf("DEBUG-> Inside TRANS_AGREE_BUT_MISSING_OBJECTS\n");
				transmiss++;
				//Read list of objects missing
				read(sd, &oidcount, sizeof(int));
				N = oidcount * sizeof(unsigned int);
				if((oidnotfound[i] = calloc(oidcount, sizeof(unsigned int))) == NULL) {
					printf("Calloc error %s, %d\n", __FILE__, __LINE__);
				}
				ptr = (char *) oidnotfound[i];
				do {
					n = read(sd, ptr+sum, N-sum);
					sum += n;
				} while(sum < N	&& n !=0);


				break;
			default:
				printf("Participant sent unknown message\n");
		}
	}
	
	//For Debug purposes 
	for(i=0 ; i< tdata->pilecount; i++) {
		for(j=0 ; j < oidcount; j++) {
			printf("DEBUG-> Oid %d missing for pilecount: %d\n", oidnotfound[j], i+1);
		}
	}

	//Decide what control message to send to Participant 	
	if(transagree == tdata->pilecount){
		//Send Commit
		ctrl = TRANS_COMMIT;
		printf("Sending TRANS_COMMIT\n");
		if (write(sd, &ctrl, sizeof(char)) < 0) {
			perror("Error sending ctrl message for participant\n");
			return 1;
		}
	}

	if(transsoftabort > 0 && transdisagree == 0) {
		//Send abort but retry commit
		ctrl = TRANS_ABORT_BUT_RETRY_COMMIT;
		printf("Sending TRANS_ABORT_BUT_RETRY_COMMIT\n");
		if (write(sd, &ctrl, sizeof(char)) < 0) {
			perror("Error sending ctrl message for participant\n");
			return 1;
		}
		//TODO
		//Relookup all missing objects
		//set up a new connection readClientReq()
		//rebuilt the pile and llookup table
		//i.e. wait at the participant end and then resend either agree or disagree
	}
	if(transmiss > 0 && transsoftabort == 0 && transdisagree == 0) {
		//Send abort but retry commit
		ctrl = TRANS_ABORT_BUT_RETRY_COMMIT_WITH_RELOCATING;
		printf("Sending TRANS_ABORT_BUT_RETRY_COMMIT_WITH_RELOCATING\n");
		if (write(sd, &ctrl, sizeof(char)) < 0) {
			perror("Error sending ctrl message for participant\n");
			return 1;
		}
		//TODO
		//Relookup all missing objects
		//set up a new connection readClientReq()
		//rebuilt the pile and llookup table
		//send missing mising object/ objects
	}
	
	//Free pointers
	for(i=0 ; i< tdata->pilecount; i++) {
		free(oidnotfound[i]);
	}
	

	return 0;
}

void *transRequest(void *threadarg) {
	int sd, i, n;
	struct sockaddr_in serv_addr;
	struct hostent *server;
	thread_data_array_t *tdata;
	objheader_t *headeraddr;
	char buffer[RECEIVE_BUFFER_SIZE], control, recvcontrol;

	tdata = (thread_data_array_t *) threadarg;
	printf("DEBUG -> New thread id %d\n", tdata->thread_id);
	//Send Trans Request
	if ((sd = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
		perror("Error in socket for TRANS_REQUEST");
		return NULL;
	}
	bzero((char*) &serv_addr, sizeof(serv_addr));
	serv_addr.sin_family = AF_INET;
	serv_addr.sin_port = htons(LISTEN_PORT);
	serv_addr.sin_addr.s_addr = inet_addr(MACHINE_IP);
	//serv_addr.sin_addr.s_addr = inet_addr(tdata->mid);

	if (connect(sd, (struct sockaddr *) &serv_addr, sizeof(struct sockaddr)) < 0) {
		perror("Error in connect for TRANS_REQUEST");
		return NULL;
	}

	//Multiple writes for sending packets of data 
	//Send first few fixed bytes of the TRANS_REQUEST protocol
	printf("DEBUG -> Start sending commit data... %d\n", tdata->buffer->f.control);
//	printf("DEBUG-> Bytes sent in first write: %d\n", sizeof(fixed_data_t));
//	printf("Machine count = %d\tnumread = %d\tnummod = %d\tsum_bytes = %d\n", tdata->buffer->f.mcount, tdata->buffer->f.numread, tdata->buffer->f.nummod, tdata->buffer->f.sum_bytes);
	if (write(sd, &(tdata->buffer->f), (sizeof(fixed_data_t))) < 0) {
		perror("Error sending fixed bytes for thread");
		return NULL;
	}
	//Send list of machines involved in the transaction
//	printf("DEBUG-> Bytes sent in second write: %d\n", sizeof(unsigned int) * tdata->pilecount);
	if (write(sd, tdata->buffer->listmid, (sizeof(unsigned int) * tdata->pilecount )) < 0) {
		perror("Error sending list of machines for thread");
		return NULL;
	}
	//Send oids and version number tuples for objects that are read
//	printf("DEBUG-> Bytes sent in the third write: %d\n", (sizeof(unsigned int) + sizeof(short)) * tdata->buffer->f.numread);
//	printf(" DEBUG->Read oids are %d %x %d %d\n", *(tdata->buffer->objread), (tdata->buffer->objread + 6), *(tdata->buffer->objread + 12), *(tdata->buffer->objread +18)); 
	if (write(sd, tdata->buffer->objread, ((sizeof(unsigned int) + sizeof(short)) * tdata->buffer->f.numread )) < 0) {
		perror("Error sending tuples for thread");
		return NULL;
	}
	//Send objects that are modified
	for(i = 0; i < tdata->buffer->f.nummod ; i++) {
		headeraddr = chashSearch(tdata->rec->lookupTable, tdata->buffer->oidmod[i]);
//		printf("DEBUG -> Bytes sent for oid = %d modified %d\n", *((int *)headeraddr), sizeof(objheader_t) + classsize[headeraddr->type]);
		if (write(sd, headeraddr, sizeof(objheader_t) + classsize[headeraddr->type])  < 0) {
			perror("Error sending obj modified for thread");
			return NULL;
		}
	}
	
	//Read message  control message from participant side
	n = read(sd, &control, sizeof(char));
	recvcontrol = control;
	printf("DEBUG -> After TRANS_REQUEST, message control recv is %d\n", recvcontrol);
//	while(n != 0) {
//		n = read(sd, buffer, sizeof(buffer));
//	}
	
	//Update common data structure and increment count
	tdata->recvmsg[tdata->thread_id].rcv_status = recvcontrol;
	//Lock and update count
	//Thread sleeps until all messages from pariticipants are received by coordinator
	pthread_mutex_lock(tdata->lock);
		(*(tdata->count))++;
	
	if(*(tdata->count) == tdata->pilecount) {
		pthread_cond_broadcast(tdata->threshold);
		//process the participant's request
		if (decideResponse(tdata, sd) != 0) {
			printf("decideResponse returned error %s. %d\n", __FILE__, __LINE__);
			return NULL;
		}
	} else {
		pthread_cond_wait(tdata->threshold, tdata->lock);
	}	
	pthread_mutex_unlock(tdata->lock);
	close(sd);
	pthread_exit(NULL);
}

int transCommit(transrecord_t *record) {	
	chashlistnode_t *curr, *ptr, *next;
	unsigned int size;//Represents number of bins in the chash table
	unsigned int machinenum, tot_bytes_mod, *listmid;
	objheader_t *headeraddr;
	plistnode_t *tmp, *pile = NULL;
	int i, rc;
	int pilecount = 0, offset, numthreads = 0, trecvcount = 0;
	char buffer[RECEIVE_BUFFER_SIZE],control;
	char transid[TID_LEN];
	static int newtid = 0;

	ptr = record->lookupTable->table;
	size = record->lookupTable->size;
	//Look through all the objects in the cache and make piles
	for(i = 0; i < size ;i++) {
		curr = &ptr[i];
		//Inner loop to traverse the linked list of the cache lookupTable
		while(curr != NULL) {
			//if the first bin in hash table is empty
			if(curr->key == 0) {
				break;
			}
			next = curr->next;
			//Get machine location for object id
			/*
			if ((machinenum = lhashSearch(curr->key)) == 0) {
			       printf("Error: No such machine\n");
			       return 1;
			}		
			*/
			//TODO only for debug
			machinenum = 1;
			if ((headeraddr = chashSearch(record->lookupTable, curr->key)) == NULL) {
				printf("Error: No such oid\n");
				return 1;
			}
			//Make machine groups
			if ((pile = pInsert(pile, headeraddr, machinenum, record->lookupTable->numelements)) == NULL) {
				printf("pInsert error %s, %d\n", __FILE__, __LINE__);
				return 1;
			}
			curr = next;
		}
	}

	//Create the packet to be sent in TRANS_REQUEST
	tmp = pile;
	pilecount = pCount(pile);		//Keeps track of the number of participants
	
	//Thread related variables
	pthread_t thread[pilecount];		//Create threads for each participant
	pthread_attr_t attr;			
	pthread_cond_t tcond;
	pthread_mutex_t tlock;
	thread_data_array_t thread_data_array[pilecount];
	
	thread_response_t rcvd_control_msg[pilecount];	//Shared thread array that keeps track of responses of participants
       	
	//Initialize and set thread detach attribute
	pthread_attr_init(&attr);
	pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_JOINABLE);
	pthread_mutex_init(&tlock, NULL);
	pthread_cond_init(&tcond, NULL);
	
	//Keep track of list of machine ids per transaction	
	if((listmid = calloc(pilecount, sizeof(unsigned int))) == NULL) {
		printf("Calloc error %s, %d\n", __FILE__, __LINE__);
		return 1;
	}
				
	pListMid(pile, listmid);
	//Process each machine group
	while(tmp != NULL) {
		printf("DEBUG -> Created thread %d... \n", numthreads);
		//Create transaction id
		newtid++;
		trans_req_data_t *tosend;
		if ((tosend = calloc(1, sizeof(trans_req_data_t))) == NULL) {
			printf("Calloc error %s, %d\n", __FILE__, __LINE__);
			return 1;
		}
		tosend->f.control = TRANS_REQUEST;
		sprintf(tosend->f.trans_id, "%x_%d", tmp->mid, newtid);
		tosend->f.mcount = pilecount;
		tosend->f.numread = tmp->numread;
		tosend->f.nummod = tmp->nummod;
		tosend->f.sum_bytes = tmp->sum_bytes;
		tosend->listmid = listmid;
		tosend->objread = tmp->objread;
		tosend->oidmod = tmp->oidmod;
		thread_data_array[numthreads].thread_id = numthreads;
		thread_data_array[numthreads].mid = tmp->mid;
		thread_data_array[numthreads].pilecount = pilecount;
		thread_data_array[numthreads].buffer = tosend;
		thread_data_array[numthreads].recvmsg = rcvd_control_msg;
		thread_data_array[numthreads].threshold = &tcond;
		thread_data_array[numthreads].lock = &tlock;
		thread_data_array[numthreads].count = &trecvcount;
		thread_data_array[numthreads].rec = record;

		rc = pthread_create(&thread[numthreads], NULL, transRequest, (void *) &thread_data_array[numthreads]);  
		if (rc) {
			perror("Error in pthread create");
			return 1;
		}		
		numthreads++;		
		//TODO frees 
		free(tosend);
		tmp = tmp->next;
	}

	// Free attribute and wait for the other threads
	pthread_attr_destroy(&attr);
	for (i = 0 ;i < pilecount ; i++) {
		rc = pthread_join(thread[i], NULL);
		if (rc)
		{
			printf("ERROR return code from pthread_join() is %d\n", rc);
			return 1;
		}
	}
	
	//Free resources	
	pthread_cond_destroy(&tcond);
	pthread_mutex_destroy(&tlock);
	free(listmid);
	pDelete(pile);
	return 0;
}

int transSoftAbort(transrecord_t *record){

}

int transAbort(transrecord_t *record){


}

//mnun will be used to represent the machine IP address later
void *getRemoteObj(transrecord_t *record, unsigned int mnum, unsigned int oid) {
	int sd, size;
	struct sockaddr_in serv_addr;
	struct hostent *server;
	char buffer[RECEIVE_BUFFER_SIZE],control;
	objheader_t *h;
	void *objcopy;

	if ((sd = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
		perror("Error in socket");
		return NULL;
	}
	bzero((char*) &serv_addr, sizeof(serv_addr));
	serv_addr.sin_family = AF_INET;
	serv_addr.sin_port = htons(LISTEN_PORT);
	serv_addr.sin_addr.s_addr = inet_addr(MACHINE_IP);

	if (connect(sd, (struct sockaddr *) &serv_addr, sizeof(struct sockaddr)) < 0) {
		perror("Error in connect");
		return NULL;
	}
	bzero((char *)buffer,sizeof(buffer));
	control = READ_REQUEST;
	buffer[0] = control;
	memcpy(buffer+1, &oid, sizeof(int));
	if (write(sd, buffer, sizeof(int) + 1) < 0) {
		perror("Error sending message");
		return NULL;
	}

#ifdef DEBUG1
	printf("DEBUG -> ready to rcv ...\n");
#endif
	//Read response from the Participant
	read(sd, &control, sizeof(char));
	switch(control) {
		case OBJECT_NOT_FOUND:
			return NULL;
			break;
		case OBJECT_FOUND:
			read(sd, &size, sizeof(int));
			objcopy = objstrAlloc(record->cache, size);
			read(sd, objcopy, size);		
			break;
		default:
			printf("Error in recv request from participant on a READ_REQUEST\n");
			return NULL;
	}

#if 0
	read(sd, buffer, sizeof(buffer));
	close(sd);
	if (buffer[0] == OBJECT_NOT_FOUND) {
		return NULL;
	} else {

		h = (objheader_t *) buffer+1;
		size = sizeof(objheader_t) + sizeof(classsize[h->type]);
#ifdef DEBUG1
	printf("DEBUG -> Received: oid = %d, type = %d\n", h->oid, h->type);
#endif
	}
	objcopy = objstrAlloc(record->cache, size);
	memcpy(objcopy, (void *)buffer+1, size);

#endif
	//Insert into cache's lookup table
	chashInsert(record->lookupTable, oid, objcopy); 
	return objcopy;
}
