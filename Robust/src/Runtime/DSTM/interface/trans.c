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
		printf("oid not found in local machine lookup\n");
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
	tmp->status |= NEW;
	chashInsert(record->lookupTable, tmp->oid, tmp);
	return tmp;
}

void *transRequest(void *threadarg) {
	int sd, transagree = 0, transabort = 0, transcommit = 0, transmiss = 0, transsoftabort = 0;
	struct sockaddr_in serv_addr;
	struct hostent *server;
	thread_data_array_t *tdata;
	char buffer[RECEIVE_BUFFER_SIZE], control, recvcontrol;

	tdata = (thread_data_array_t *) threadarg;
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

	if (write(sd, tdata->buffer, (sizeof(char) * RECEIVE_BUFFER_SIZE)) < 0) {
		perror("Error sending message for thread");
		return NULL;
	}
	//Read message from participant side
	read(sd, buffer, sizeof(buffer));
	//process the participant's request
	recvcontrol = buffer[0];
	//Update common data structure and increment count
	tdata->recvmsg[tdata->thread_id] = recvcontrol;
	//Lock and update count
	//Thread sleeps until all messages from pariticipants are received by coordinator
	pthread_mutex_lock(tdata->lock);
		(*(tdata->count))++;
	
	if(*(tdata->count) == tdata->pilecount) {
		pthread_cond_broadcast(tdata->threshold);
		//Check common data structure 
		for (i = 0 ; i < tdata->pilecount ; i++) {
			//Check in any DISAGREE has come
			if(tdata->recvmsg[i] == TRANS_DISAGREE) {
				//Send abort
				transabort++;
				buffer[0] = TRANS_ABORT;
				if (write(sd, tdata->buffer, (sizeof(char) * RECEIVE_BUFFER_SIZE)) < 0) {
					perror("Error sending message for thread");
					return NULL;
				}
			} else if(tdata->recvmsg[i] == AGREE) {
				transagree++;
			} else if(tdata->recvmsg[i] == AGREE_BUT_MISSING_OBJECTS) {
				transmiss++;
			} else
				transsoftabort++;
		}
		if(transagree == tdata->pilecount){
			//Send Commit
			buffer[0] = TRANS_COMMIT;
			if (write(sd, tdata->buffer, (sizeof(char) * RECEIVE_BUFFER_SIZE)) < 0) {
				perror("Error sending message for thread");
				return NULL;
			}
		}
		if(transsoftabort > 0 && transabort == 0) {
			//Send abort but retry commit
			//i.e. wait at the participant end and then resend either agree or disagree
			//

		}
		if(transmiss > 0 && transsoftabort == 0 && transabort == 0) {
			//Relookup all missing objects
			//send missing mising object/ objects
		}

	} else {
		pthread_cond_wait(tdata->threshold, tdata->lock);
	}	
	pthread_mutex_unlock(tdata->lock);
	close(sd);
	//Reset numread and nummod for the next machine
	pthread_exit(NULL);
}

int transCommit(transrecord_t *record){	
	chashlistnode_t *curr, *ptr, *next;
	unsigned int size;//Represents number of bins in the chash table
	unsigned int machinenum, tot_bytes_mod;
	objheader_t *headeraddr;
	plistnode_t *tmp, *pile = NULL;
	int i, rc;
	int pilecount = 0, offset, numthreads = 0, trecvcount = 0;
	short numread = 0,nummod = 0;
	char buffer[RECEIVE_BUFFER_SIZE],control;
	char tmpbuffer[RECEIVE_BUFFER_SIZE];
	char transid[TID_LEN];
	static int newtid = 0;
	pthread_cond_t threshold;
	pthread_mutex_t count;

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
			machinenum = lhashSearch(curr->key);
			//Make machine groups
			if ((pile = pInsert(pile, machinenum, curr->key, record->lookupTable->numelements)) == NULL) {
				perror("pInsert calloc error");
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
	
	char rcvd_control_msg[pilecount];      //Shared thread array that keeps track of responses of participants
       	
	//Initialize and set thread detach attribute
	pthread_attr_init(&attr);
	pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_JOINABLE);
	pthread_mutex_init(&tlock, NULL);
	pthread_cond_init(&tcond, NULL);
	
	//Keep track of list of machine ids per transaction	
	unsigned int *listmid = calloc(pilecount, sizeof(unsigned int));
	pListMid(pile, listmid);
	//Process each machine group
	while(tmp != NULL) {
		unsigned int *oidmod = calloc(record->lookupTable->numelements, sizeof(unsigned int));
		unsigned int *oidread = calloc(record->lookupTable->numelements, sizeof(unsigned int));
		nummod = numread = tot_bytes_mod = 0;
		offset = 0;
		
		
		//Create transaction id
		newtid++;
		sprintf(transid, "%x_%d", tmp->mid, newtid);
		//Browse through each oid in machine group
		for(i = 0; i < tmp->index; i++) {
			headeraddr = (objheader_t *) chashSearch(record->lookupTable, tmp->obj[i]);
			//check if object modified in cache
			if((headeraddr->status >> 1) == 1){
				//Keep track of oids that have been modified	
				oidmod[nummod] = headeraddr->oid;
				nummod++;
				tot_bytes_mod += (sizeof(objheader_t) + classsize[headeraddr->type]); //Keeps track of total bytes of modified object 
			} else {
				//Keep track of oids that are read	
				oidread[numread] = headeraddr->oid;
				//create <oid,version> tuples in temporary buffer
				memcpy(tmpbuffer+offset, &headeraddr->oid, sizeof(unsigned int)); 	
				offset += sizeof(unsigned int);
				memcpy(tmpbuffer+offset, &headeraddr->version, sizeof(short)); 	
				offset += sizeof(short);
				numread++;
			}
		}
		//Copy each field of the packet into buffer
		bzero((char *)buffer,sizeof(buffer));
		offset = 0;
		buffer[offset] = TRANS_REQUEST;
		offset = offset + 1;
		memcpy(buffer+offset, transid, sizeof(char) * TID_LEN);
		offset += (sizeof(char) * TID_LEN);
		memcpy(buffer+offset, &pilecount, sizeof(int));
		offset += sizeof(int);
		memcpy(buffer+offset, &numread, sizeof(short));
		offset += sizeof(short);
		memcpy(buffer+offset, &nummod, sizeof(short));
		offset += sizeof(short);
		memcpy(buffer+offset, &tot_bytes_mod, sizeof(unsigned int));
		offset += sizeof(unsigned int);
		memcpy(buffer+offset, listmid, sizeof(unsigned int) * pilecount);
		offset += (sizeof(unsigned int) * pilecount);
		memcpy(buffer+offset, tmpbuffer, sizeof(char) * RECEIVE_BUFFER_SIZE);
		offset += (sizeof(char) * RECEIVE_BUFFER_SIZE);
		//send objects for all objects modified
		for( i= 0; i< nummod; i++) {
			headeraddr = (objheader_t *) chashSearch(record->lookupTable, oidmod[i]);
			memcpy(buffer+offset, headeraddr, sizeof(objheader_t) + classsize[headeraddr->type]);
			offset += sizeof(objheader_t) + classsize[headeraddr->type];
		}
		if (offset > RECEIVE_BUFFER_SIZE) {
			printf("Error: Buffersize too small");
		}
		//Create thread input to pass multiple arguments via structure 
		thread_data_array[numthreads].thread_id = numthreads;
		thread_data_array[numthreads].mid = tmp->mid;
		thread_data_array[numthreads].pilecount = pilecount;
		thread_data_array[numthreads].buffer = buffer;
		thread_data_array[numthreads].recvmsg = rcvd_control_msg;
		thread_data_array[numthreads].threshold = &tcond;
		thread_data_array[numthreads].lock = &tlock;
		thread_data_array[numthreads].count = &trecvcount;
		//Spawn thread for each TRANS_REQUEST
		rc = pthread_create(&thread[numthreads], &attr, transRequest, (void *) &thread_data_array[numthreads]);  
		if (rc) {
			perror("Error in pthread create");
			exit(-1);	
		}		
		numthreads++;		
		sleep(2);
		free(oidmod);
		free(oidread);
		tmp = tmp->next;
	}
	// Free attribute and wait for the other threads
	pthread_attr_destroy(&attr);
	for (i = 0 ;i < pilecount ; i++) {
		rc = pthread_join(thread[i], NULL);
		if (rc)
		{
			printf("ERROR return code from pthread_join() is %d\n", rc);
			exit(-1);
		}
	}
		
	
	//Free resources	
	pthread_cond_destroy(&tcond);
	pthread_mutex_destroy(&tlock);
	pthread_exit(NULL);
	free(listmid);
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
	if (write(sd, buffer, sizeof(buffer)) < 0) {
		perror("Error sending message");
		return NULL;
	}

#ifdef DEBUG1
	printf("DEBUG -> ready to rcv ...\n");
#endif
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
	//Insert into cache's lookup table
	chashInsert(record->lookupTable, oid, objcopy); 
	return objcopy;
}
