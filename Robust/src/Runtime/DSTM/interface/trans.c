#include "dstm.h"
#include "ip.h"
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
	printf("Enter TRANS_READ\n");
	unsigned int machinenumber;
	objheader_t *tmp, *objheader;
	void *objcopy;
	int size;
	void *buf;
		//check cache
	if((objheader =(objheader_t *)chashSearch(record->lookupTable, oid)) != NULL){
		printf("DEBUG -> transRead oid %d found local\n", oid);
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
		printf("machinenumber = %d\n",machinenumber);
		printf("oid = %d\n",oid);
		machinenumber = lhashSearch(oid);
		printf("machinenumber = %d\n",machinenumber);
		objcopy = getRemoteObj(record, machinenumber, oid);
		if(objcopy == NULL) {
			//If object is not found in Remote location
			printf("Object not found in Machine %d\n", machinenumber);
			return NULL;
		}
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
//int decideResponse(thread_data_array_t *tdata, char *control, int sd) {
int decideResponse(thread_data_array_t *tdata, int sd, int val) {
	int i, n, N, sum, oidcount = 0;
	int transagree = 0, transdisagree = 0, transsoftabort = 0, transsoftabortmiss = 0;
	char ctrl, control, *ptr;
	unsigned int *oidnotfound;
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
				return 0;

			case TRANS_AGREE:
				printf("DEBUG-> Inside TRANS_AGREE\n");
				transagree++;
				break;
				
			case TRANS_SOFT_ABORT:
				printf("DEBUG-> Inside TRANS_SOFT_ABORT\n");
				transsoftabort++;
				/* Do a socket read only if TRANS_SOFT_ABORT was meant for this thread */
				if ((i == tdata->thread_id) && (val == 0)) {
					//Read list of objects missing
					if(read(sd, &oidcount, sizeof(int)) != 0) {
						//Break if only objs are locked at the Participant side
						if (oidcount == 0) {
							break;
						}
						transsoftabortmiss++;
						N = oidcount * sizeof(unsigned int);
						if((oidnotfound = calloc(oidcount, sizeof(unsigned int))) == NULL) {
							printf("Calloc error %s, %d\n", __FILE__, __LINE__);
						}
						ptr = (char *) oidnotfound;
						do {
							n = read(sd, ptr+sum, N-sum);
							sum += n;
						} while(sum < N	&& n !=0);
					}
				}

				break;
			default:
				printf("Participant sent unknown message\n");
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

	if(transsoftabort > 0 && transdisagree == 0 && transsoftabortmiss == 0) {
		//Send abort but retry commit
		ctrl = TRANS_ABORT_BUT_RETRY_COMMIT;
		printf("Sending TRANS_ABORT_BUT_RETRY_COMMIT\n");
		if (write(sd, &ctrl, sizeof(char)) < 0) {
			perror("Error sending ctrl message for participant\n");
			return 1;
		}
		//Sleep
		sleep(5);
		//Read new control message from Participant
		n = read(sd, &control, sizeof(char));
		
		//Update common data structure and increment count
		tdata->recvmsg[tdata->thread_id].rcv_status = control;
		val = 1;
		decideResponse(tdata, sd, val);		//Second call to decideResponse(); indicated by parameter val = 1
	}

	if(transsoftabort > 0 && transsoftabortmiss > 0 && transdisagree == 0) {
		//Send abort but retry commit after relloking up objects
		//ctrl = TRANS_ABORT_BUT_RETRY_COMMIT_WITH_RELOCATING;
		ctrl = TRANS_ABORT;
		printf("Sending TRANS_ABORT_BUT_RETRY_COMMIT_WITH_RELOCATING\n");
		if (write(sd, &ctrl, sizeof(char)) < 0) {
			perror("Error sending ctrl message for participant\n");
			return 1;
		}
		//TODO
		//Relook up objects
		//update location table
		//Free pointers
		free(oidnotfound);
	}
	
	return 0;
}

void *transRequest(void *threadarg) {
	int sd, i, n;
	struct sockaddr_in serv_addr;
	struct hostent *server;
	thread_data_array_t *tdata;
	objheader_t *headeraddr;
	//unsigned int *oidnotfound;
	char buffer[RECEIVE_BUFFER_SIZE], control, recvcontrol;
	char machineip[16];

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
	//serv_addr.sin_addr.s_addr = inet_addr(MACHINE_IP);
	midtoIP(tdata->mid,machineip);
	machineip[15] = '\0';
	serv_addr.sin_addr.s_addr = inet_addr(machineip);
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
	
	//Update common data structure and increment count
	tdata->recvmsg[tdata->thread_id].rcv_status = recvcontrol;
	//Lock and update count
	//Thread sleeps until all messages from pariticipants are received by coordinator
	pthread_mutex_lock(tdata->lock);
		(*(tdata->count))++;
	
	if(*(tdata->count) == tdata->pilecount) {
		pthread_cond_broadcast(tdata->threshold);
		//process the participant's request
		if (decideResponse(tdata, sd, 0) != 0) { // Send the first call to decideResponse() ; indicated by parameter 0
			printf("decideResponse returned error %s. %d\n", __FILE__, __LINE__);
			pthread_mutex_unlock(tdata->lock);
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
	int pilecount = 0, offset, numthreads = 0, trecvcount = 0, tmachcount = 0;
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
			
			if ((machinenum = lhashSearch(curr->key)) == 0) {
			       printf("Error: No such machine\n");
			       return 1;
			}
					
			//TODO only for debug
			//machinenum = 1;
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
	pthread_mutex_t tlshrd;
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

//mnun will be used to represent the machine IP address later
void *getRemoteObj(transrecord_t *record, unsigned int mnum, unsigned int oid) {
	int sd, size;
	struct sockaddr_in serv_addr;
	struct hostent *server;
	char control;
	char machineip[16];
	objheader_t *h;
	void *objcopy;

	if ((sd = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
		perror("Error in socket");
		return NULL;
	}
	bzero((char*) &serv_addr, sizeof(serv_addr));
	serv_addr.sin_family = AF_INET;
	serv_addr.sin_port = htons(LISTEN_PORT);
	//serv_addr.sin_addr.s_addr = inet_addr(MACHINE_IP);
	midtoIP(mnum,machineip);
	machineip[15] = '\0';
	serv_addr.sin_addr.s_addr = inet_addr(machineip);

	if (connect(sd, (struct sockaddr *) &serv_addr, sizeof(struct sockaddr)) < 0) {
		perror("Error in connect");
		return NULL;
	}
	char readrequest[sizeof(char)+sizeof(unsigned int)];
	readrequest[0] = READ_REQUEST;
	*((unsigned int *)(&readrequest[1])) = oid;
	if (write(sd, &readrequest, sizeof(readrequest)) < 0) {
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
			//Insert into cache's lookup table
			chashInsert(record->lookupTable, oid, objcopy); 
			break;
		default:
			printf("Error in recv request from participant on a READ_REQUEST\n");
			return NULL;
	}
	return objcopy;
}
