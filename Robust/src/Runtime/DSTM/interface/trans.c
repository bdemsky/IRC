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
#include <sys/types.h>
#include <unistd.h>
#include <time.h>

#define LISTEN_PORT 2156
#define MACHINE_IP "127.0.0.1"
#define RECEIVE_BUFFER_SIZE 2048

extern int classsize[];
plistnode_t *createPiles(transrecord_t *);
/* This functions inserts randowm wait delays in the order of msec */
void randomdelay(void)
{
	struct timespec req, rem;
	time_t t;

	t = time(NULL);
	req.tv_sec = 0;
	req.tv_nsec = (long)(1000000 + (t%10000000)); //1-11 msec
	nanosleep(&req, &rem);
	return;
}

transrecord_t *transStart()
{
	transrecord_t *tmp = malloc(sizeof(transrecord_t));
	tmp->cache = objstrCreate(1048576);
	tmp->lookupTable = chashCreate(HASH_SIZE, LOADFACTOR);
	return tmp;
}

/* This function finds the location of the objects involved in a transaction
 * and returns the pointer to the object if found in a remote location */
objheader_t *transRead(transrecord_t *record, unsigned int oid)
{	
	unsigned int machinenumber;
	objheader_t *tmp, *objheader;
	void *objcopy;
	int size;
	void *buf;
		/* Search local cache */
	if((objheader =(objheader_t *)chashSearch(record->lookupTable, oid)) != NULL){
		//printf("DEBUG -> transRead oid %d found local\n", oid);
		return(objheader);
	} else if ((objheader = (objheader_t *) mhashSearch(oid)) != NULL) {
		/* Look up in machine lookup table  and copy  into cache*/
		//printf("oid is found in Local machinelookup\n");
		tmp = mhashSearch(oid);
		size = sizeof(objheader_t)+classsize[tmp->type];
		objcopy = objstrAlloc(record->cache, size);
		memcpy(objcopy, (void *)tmp, size);
		/* Insert into cache's lookup table */
		chashInsert(record->lookupTable, objheader->oid, objcopy); 
		return(objcopy);
	} else { /* If not found in machine look up */
		/* Get the object from the remote location */
		machinenumber = lhashSearch(oid);
		objcopy = getRemoteObj(record, machinenumber, oid);
		if(objcopy == NULL) {
			//If object is not found in Remote location
			//printf("Object oid = %d not found in Machine %d\n", oid, machinenumber);
			return NULL;
		}
		else {
			//printf("Object oid = %d found in Machine %d\n", oid, machinenumber);
			return(objcopy);
		}
	} 
}
/* This function creates objects in the transaction record */
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

plistnode_t *createPiles(transrecord_t *record) {
	int i = 0;
	unsigned int size;/* Represents number of bins in the chash table */
	chashlistnode_t *curr, *ptr, *next;
	plistnode_t *pile = NULL;
	unsigned int machinenum;
	objheader_t *headeraddr;

	ptr = record->lookupTable->table;
	size = record->lookupTable->size;

	for(i = 0; i < size ; i++) {
		curr = &ptr[i];
		/* Inner loop to traverse the linked list of the cache lookupTable */
		while(curr != NULL) {
			//if the first bin in hash table is empty
			if(curr->key == 0) {
				break;
			}
			next = curr->next;
			//Get machine location for object id
			
			if ((machinenum = lhashSearch(curr->key)) == 0) {
			       printf("Error: No such machine %s, %d\n", __FILE__, __LINE__);
			       return NULL;
			}
					
			if ((headeraddr = chashSearch(record->lookupTable, curr->key)) == NULL) {
				printf("Error: No such oid %s, %d\n", __FILE__, __LINE__);
				return NULL;
			}
			//Make machine groups
			if ((pile = pInsert(pile, headeraddr, machinenum, record->lookupTable->numelements)) == NULL) {
				printf("pInsert error %s, %d\n", __FILE__, __LINE__);
				return NULL;
			}
			curr = next;
		}
	}

	return pile; 
}
/* This function initiates the transaction commit process
 * Spawns threads for each of the new connections with Participants 
 * by creating new piles,
 * Fills the piles with necesaary information and 
 * Sends a transrequest() to each pile*/
int transCommit(transrecord_t *record) {	
	unsigned int tot_bytes_mod, *listmid;
	plistnode_t *pile;
	int i, rc;
	int pilecount = 0, offset, numthreads = 0, trecvcount = 0, tmachcount = 0;
	char buffer[RECEIVE_BUFFER_SIZE],control;
	char transid[TID_LEN];
	trans_req_data_t *tosend;
	static int newtid = 0;
	char treplyctrl = 0, treplyretry = 0; /* keeps track of the common response that needs to be sent */

	/* Look through all the objects in the transaction record and make piles 
	 * for each machine involved in the transaction*/
	pile = createPiles(record);

	/* Create the packet to be sent in TRANS_REQUEST */

	/* Count the number of participants */
	pilecount = pCount(pile);
		
	/* Create a list of machine ids(Participants) involved in transaction	*/
	if((listmid = calloc(pilecount, sizeof(unsigned int))) == NULL) {
		printf("Calloc error %s, %d\n", __FILE__, __LINE__);
		return 1;
	}		
	pListMid(pile, listmid);
	

	/* Initialize thread variables,
	 * Spawn a thread for each Participant involved in a transaction */
	pthread_t thread[pilecount];
	pthread_attr_t attr;			
	pthread_cond_t tcond;
	pthread_mutex_t tlock;
	pthread_mutex_t tlshrd;
	
	thread_data_array_t *thread_data_array;
	thread_data_array = (thread_data_array_t *) malloc(sizeof(thread_data_array_t)*pilecount);
	thread_response_t rcvd_control_msg[pilecount];	/* Shared thread array that keeps track of responses of participants */

	/* Initialize and set thread detach attribute */
	pthread_attr_init(&attr);
	pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_JOINABLE);
	pthread_mutex_init(&tlock, NULL);
	pthread_cond_init(&tcond, NULL);
	
	/* Process each machine pile */
	while(pile != NULL) {
		//Create transaction id
		newtid++;
		//trans_req_data_t *tosend;
		if ((tosend = calloc(1, sizeof(trans_req_data_t))) == NULL) {
			printf("Calloc error %s, %d\n", __FILE__, __LINE__);
			return 1;
		}
		tosend->f.control = TRANS_REQUEST;
		sprintf(tosend->f.trans_id, "%x_%d", pile->mid, newtid);
		tosend->f.mcount = pilecount;
		tosend->f.numread = pile->numread;
		tosend->f.nummod = pile->nummod;
		tosend->f.sum_bytes = pile->sum_bytes;
		tosend->listmid = listmid;
		tosend->objread = pile->objread;
		tosend->oidmod = pile->oidmod;
		thread_data_array[numthreads].thread_id = numthreads;
		thread_data_array[numthreads].mid = pile->mid;
		thread_data_array[numthreads].pilecount = pilecount;
		thread_data_array[numthreads].buffer = tosend;
		thread_data_array[numthreads].recvmsg = rcvd_control_msg;
		thread_data_array[numthreads].threshold = &tcond;
		thread_data_array[numthreads].lock = &tlock;
		thread_data_array[numthreads].count = &trecvcount;
		thread_data_array[numthreads].replyctrl = &treplyctrl;
		thread_data_array[numthreads].replyretry = &treplyretry;
		thread_data_array[numthreads].rec = record;

		rc = pthread_create(&thread[numthreads], NULL, transRequest, (void *) &thread_data_array[numthreads]);  
		if (rc) {
			perror("Error in pthread create");
			return 1;
		}		
		numthreads++;		
		//TODO frees 
		pile = pile->next;
	}

	/* Free attribute and wait for the other threads */
	pthread_attr_destroy(&attr);
	for (i = 0 ;i < pilecount ; i++) {
		rc = pthread_join(thread[i], NULL);
		if (rc)
		{
			printf("ERROR return code from pthread_join() is %d\n", rc);
			return 1;
		}
	}
	
	/* Free resources */	
	pthread_cond_destroy(&tcond);
	pthread_mutex_destroy(&tlock);
	free(tosend);
	free(listmid);
	pDelete(pile);

	/* Retry trans commit procedure if not sucessful in the first try */
	if(treplyretry == 1) {
		/* wait a random amount of time */
		randomdelay();
		//sleep(1);
		/* Retry the commiting transaction again */
		transCommit(record);
	}	
	
	return 0;
}

/* This function sends information involved in the transaction request and 
 * accepts a response from particpants.
 * It calls decideresponse() to decide on what control message 
 * to send next and sends the message using sendResponse()*/
void *transRequest(void *threadarg) {
	int sd, i, n;
	struct sockaddr_in serv_addr;
	struct hostent *server;
	thread_data_array_t *tdata;
	objheader_t *headeraddr;
	char buffer[RECEIVE_BUFFER_SIZE], control, recvcontrol;
	char machineip[16], retval;

	tdata = (thread_data_array_t *) threadarg;

	/* Send Trans Request */
	if ((sd = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
		perror("Error in socket for TRANS_REQUEST\n");
		return NULL;
	}
	bzero((char*) &serv_addr, sizeof(serv_addr));
	serv_addr.sin_family = AF_INET;
	serv_addr.sin_port = htons(LISTEN_PORT);
	midtoIP(tdata->mid,machineip);
	machineip[15] = '\0';
	serv_addr.sin_addr.s_addr = inet_addr(machineip);
	/* Open Connection */
	if (connect(sd, (struct sockaddr *) &serv_addr, sizeof(struct sockaddr)) < 0) {
		perror("Error in connect for TRANS_REQUEST\n");
		return NULL;
	}
	
	printf("DEBUG-> trans.c Sending TRANS_REQUEST to mid %s\n", machineip);
	/* Send bytes of data with TRANS_REQUEST control message */
	if (send(sd, &(tdata->buffer->f), sizeof(fixed_data_t),MSG_NOSIGNAL) < sizeof(fixed_data_t)) {
		perror("Error sending fixed bytes for thread\n");
		return NULL;
	}
	/* Send list of machines involved in the transaction */
	{
	  int size=sizeof(unsigned int)*tdata->pilecount;
	  if (send(sd, tdata->buffer->listmid, size, MSG_NOSIGNAL) < size) {
	    perror("Error sending list of machines for thread\n");
	    return NULL;
	  }
	}
	/* Send oids and version number tuples for objects that are read */
	{
	  int size=(sizeof(unsigned int)+sizeof(short))*tdata->buffer->f.numread;
	  if (send(sd, tdata->buffer->objread, size, MSG_NOSIGNAL) < size) {
	    perror("Error sending tuples for thread\n");
	    return NULL;
	  }
	}
	/* Send objects that are modified */
	for(i = 0; i < tdata->buffer->f.nummod ; i++) {
	  int size;
	  headeraddr = chashSearch(tdata->rec->lookupTable, tdata->buffer->oidmod[i]);
	  size=sizeof(objheader_t)+classsize[headeraddr->type];
	  if (send(sd, headeraddr, size, MSG_NOSIGNAL)  < size) {
	    perror("Error sending obj modified for thread\n");
	    return NULL;
	  }
	}

	/* Read control message from Participant */
	if((n = read(sd, &control, sizeof(char))) <= 0) {
		perror("Error in reading control message from Participant\n");
		return NULL;
	}
	recvcontrol = control;
	
	/* Update common data structure and increment count */
	tdata->recvmsg[tdata->thread_id].rcv_status = recvcontrol;

	/* Lock and update count */
	//Thread sleeps until all messages from pariticipants are received by coordinator
	pthread_mutex_lock(tdata->lock);

	(*(tdata->count))++; /* keeps track of no of messages received by the coordinator */

	/* Wake up the threads and invoke decideResponse (once) */
	if(*(tdata->count) == tdata->pilecount) {
		if (decideResponse(tdata) != 0) { 
			printf("decideResponse returned error %s,%d\n", __FILE__, __LINE__);
			pthread_mutex_unlock(tdata->lock);
			close(sd);
			return NULL;
		}
		pthread_cond_broadcast(tdata->threshold);
	} else {
		pthread_cond_wait(tdata->threshold, tdata->lock);
	}	

	pthread_mutex_unlock(tdata->lock);

	/* Send the final response such as TRANS_COMMIT or TRANS_ABORT t
	 * to all participants in their respective socket */
	if (sendResponse(tdata, sd) == 0) { 
		printf("sendResponse returned error %s,%d\n", __FILE__, __LINE__);
		pthread_mutex_unlock(tdata->lock);
		close(sd);
		return NULL;
	}

	/* Close connection */
	close(sd);
	pthread_exit(NULL);
}

/* This function decides the reponse that needs to be sent to 
 * all Participant machines involved in the transaction commit */
int decideResponse(thread_data_array_t *tdata) {
	char control;
	int i, transagree = 0, transdisagree = 0, transsoftabort = 0; /* Counters to formulate decision of what
									 message to send */

	//Check common data structure 
	for (i = 0 ; i < tdata->pilecount ; i++) {
		/*Switch on response from Participant */
		control = tdata->recvmsg[i].rcv_status; /* tdata: keeps track of all participant responses
							   written onto the shared array */
		switch(control) {
			case TRANS_DISAGREE:
				printf("DEBUG-> trans.c Recv TRANS_DISAGREE\n");
				transdisagree++;
				break;

			case TRANS_AGREE:
				printf("DEBUG-> trans.c Recv TRANS_AGREE\n");
				transagree++;
				break;
				
			case TRANS_SOFT_ABORT:
				printf("DEBUG-> trans.c Recv TRANS_SOFT_ABORT\n");
				transsoftabort++;
				break;
			default:
				printf("Participant sent unknown message in %s, %d\n", __FILE__, __LINE__);
				return -1;
		}
	}
	
	/* Decide what control message to send to Participant */	
	if(transdisagree > 0) {
		/* Send Abort */
		*(tdata->replyctrl) = TRANS_ABORT;
		printf("DEBUG-> trans.c Sending TRANS_ABORT\n");
		objstrDelete(tdata->rec->cache);
		chashDelete(tdata->rec->lookupTable);
		free(tdata->rec);
	} else if(transagree == tdata->pilecount){
		/* Send Commit */
		*(tdata->replyctrl) = TRANS_COMMIT;
		printf("DEBUG-> trans.c Sending TRANS_COMMIT\n");
		objstrDelete(tdata->rec->cache);
		chashDelete(tdata->rec->lookupTable);
		free(tdata->rec);
	} else if(transsoftabort > 0 && transdisagree == 0) {
		/* Send Abort in soft abort case followed by retry commiting transaction again*/
		*(tdata->replyctrl) = TRANS_ABORT;
		*(tdata->replyretry) = 1;
		printf("DEBUG-> trans.c Sending TRANS_ABORT\n");
	} else {
		printf("DEBUG -> %s, %d: Error: undecided response\n", __FILE__, __LINE__);
		return -1;
	}
	
	return 0;
}
/* This function sends the final response to all threads in their respective socket id */
char sendResponse(thread_data_array_t *tdata, int sd) {
	int n, N, sum, oidcount = 0;
	char *ptr, retval = 0;
	unsigned int *oidnotfound;

	/* If the decided response is due to a soft abort and missing objects at the Participant's side */
	if(tdata->recvmsg[tdata->thread_id].rcv_status == TRANS_SOFT_ABORT) {
		/* Read list of objects missing */
		if((read(sd, &oidcount, sizeof(int)) != 0) && (oidcount != 0)) {
			N = oidcount * sizeof(unsigned int);
			if((oidnotfound = calloc(oidcount, sizeof(unsigned int))) == NULL) {
				printf("Calloc error %s, %d\n", __FILE__, __LINE__);
			}
			ptr = (char *) oidnotfound;
			do {
				n = read(sd, ptr+sum, N-sum);
				sum += n;
			} while(sum < N && n !=0);
		}
		retval =  TRANS_SOFT_ABORT;
	}
	/* If the decided response is TRANS_ABORT */
	if(*(tdata->replyctrl) == TRANS_ABORT) {
		retval = TRANS_ABORT;
	}
	/* If the decided response is TRANS_COMMIT */
	if(*(tdata->replyctrl) == TRANS_COMMIT) {
		retval = TRANS_COMMIT;
	}
	/* Send response to the Participant */
	if (send(sd, tdata->replyctrl, sizeof(char),MSG_NOSIGNAL) < sizeof(char)) {
		perror("Error sending ctrl message for participant\n");
	}

	return retval;
}

/* This function opens a connection, places an object read request to the 
 * remote machine, reads the control message and object if available  and 
 * copies the object and its header to the local cache.
 * TODO replace mnum and midtoIP() with MACHINE_IP address later */ 

void *getRemoteObj(transrecord_t *record, unsigned int mnum, unsigned int oid) {
	int sd, size, val;
	struct sockaddr_in serv_addr;
	struct hostent *server;
	char control;
	char machineip[16];
	objheader_t *h;
	void *objcopy;

	if ((sd = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
		perror("Error in socket\n");
		return NULL;
	}
	bzero((char*) &serv_addr, sizeof(serv_addr));
	serv_addr.sin_family = AF_INET;
	serv_addr.sin_port = htons(LISTEN_PORT);
	//serv_addr.sin_addr.s_addr = inet_addr(MACHINE_IP);
	midtoIP(mnum,machineip);
	machineip[15] = '\0';
	serv_addr.sin_addr.s_addr = inet_addr(machineip);
	/* Open connection */
	if (connect(sd, (struct sockaddr *) &serv_addr, sizeof(struct sockaddr)) < 0) {
		perror("Error in connect\n");
		return NULL;
	}
	char readrequest[sizeof(char)+sizeof(unsigned int)];
	readrequest[0] = READ_REQUEST;
	*((unsigned int *)(&readrequest[1])) = oid;
	if (send(sd, &readrequest, sizeof(readrequest), MSG_NOSIGNAL) < sizeof(readrequest)) {
		perror("Error sending message\n");
		return NULL;
	}

#ifdef DEBUG1
	printf("DEBUG -> ready to rcv ...\n");
#endif
	/* Read response from the Participant */
	if((val = read(sd, &control, sizeof(char))) <= 0) {
		perror("No control response for getRemoteObj sent\n");
		return NULL;
	}
	switch(control) {
		case OBJECT_NOT_FOUND:
			printf("DEBUG -> Control OBJECT_NOT_FOUND received\n");
			return NULL;
		case OBJECT_FOUND:
			/* Read object if found into local cache */
			if((val = read(sd, &size, sizeof(int))) <= 0) {
				perror("No size is read from the participant\n");
				return NULL;
			}
			objcopy = objstrAlloc(record->cache, size);
			if((val = read(sd, objcopy, size)) <= 0) {
				perror("No objects are read from the remote participant\n");
				return NULL;
			}
			/* Insert into cache's lookup table */
			chashInsert(record->lookupTable, oid, objcopy); 
			break;
		default:
			printf("Error in recv request from participant on a READ_REQUEST %s, %d\n",__FILE__, __LINE__);
			return NULL;
	}
	/* Close connection */
	close(sd);
	return objcopy;
}
