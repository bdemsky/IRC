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
objstr_t *mainobjstore;
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
		//LOCAL Object
		objheader->status |= LOCAL;
		//printf("DEBUG -> transRead oid %d found local\n", oid);
		return(objheader);
	} else if ((objheader = (objheader_t *) mhashSearch(oid)) != NULL) {
		/* Look up in machine lookup table  and copy  into cache*/
		//printf("oid is found in Local machinelookup\n");
		tmp = mhashSearch(oid);
		size = sizeof(objheader_t)+classsize[tmp->type];
		objcopy = objstrAlloc(record->cache, size);
		memcpy(objcopy, (void *)tmp, size);
		//LOCAL Object
		((objheader_t *) objcopy)->status |= LOCAL;
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
/* This function creates machine piles based on all machines involved in a
 * transaction commit request */
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
			/* Check if local */
			if((headeraddr->status & LOCAL) == LOCAL) {
				pile->local = 1; //True i.e. local
			}
			curr = next;
		}
	}

	return pile; 
}
/* This function initiates the transaction commit process
 * Spawns threads for each of the new connections with Participants 
 * and creates new piles by calling the createPiles(),
 * Fills the piles with necesaary information and 
 * Sends a transrequest() to each pile*/
int transCommit(transrecord_t *record) {	
	unsigned int tot_bytes_mod, *listmid;
	plistnode_t *pile;
	int i, rc, val;
	int pilecount = 0, offset, threadnum = 0, trecvcount = 0, tmachcount = 0;
	char buffer[RECEIVE_BUFFER_SIZE],control;
	char transid[TID_LEN];
	trans_req_data_t *tosend;
	trans_commit_data_t transinfo;
	static int newtid = 0;
	char treplyctrl = 0, treplyretry = 0; /* keeps track of the common response that needs to be sent */
	char localstat = 0;

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
	local_thread_data_array_t *ltdata;
	if((ltdata = calloc(1, sizeof(local_thread_data_array_t))) == NULL) {
		printf("Calloc error %s, %d\n", __FILE__, __LINE__);
		return 1;
	}

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
		printf("DEBUG-> pile numread = %d\n", pile->numread);
		tosend->f.nummod = pile->nummod;
		tosend->f.sum_bytes = pile->sum_bytes;
		tosend->listmid = listmid;
		tosend->objread = pile->objread;
		tosend->oidmod = pile->oidmod;
		thread_data_array[threadnum].thread_id = threadnum;
		thread_data_array[threadnum].mid = pile->mid;
		thread_data_array[threadnum].pilecount = pilecount;
		thread_data_array[threadnum].buffer = tosend;
		thread_data_array[threadnum].recvmsg = rcvd_control_msg;
		thread_data_array[threadnum].threshold = &tcond;
		thread_data_array[threadnum].lock = &tlock;
		thread_data_array[threadnum].count = &trecvcount;
		//thread_data_array[threadnum].localstatus = &localstat;
		thread_data_array[threadnum].replyctrl = &treplyctrl;
		thread_data_array[threadnum].replyretry = &treplyretry;
		thread_data_array[threadnum].rec = record;
		/* If local do not create any extra connection */
		if(pile->local != 1) {
			rc = pthread_create(&thread[threadnum], NULL, transRequest, (void *) &thread_data_array[threadnum]);  
			if (rc) {
				perror("Error in pthread create\n");
				return 1;
			}
		} else {
			/*Unset the pile->local flag*/
			pile->local = 0;
			//header->status &= ~(LOCK);
			/*Handle request of local pile */
			/*Set flag to identify that Local machine is involved*/
			ltdata->tdata = &thread_data_array[threadnum];
			printf("DEBUG->Address of ltdata sent = %x\n", &ltdata);
			ltdata->transinfo = &transinfo;
			printf("DEBUG-> Machine Pile numread = %d\n", ltdata->tdata->buffer->f.numread);
			val = pthread_create(&thread[threadnum], NULL, handleLocalReq, (void *) &ltdata);
			if (val) {
				perror("Error in pthread create\n");
				return 1;
			}
		}
		threadnum++;		
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
	free(thread_data_array);
	free(ltdata);

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
/*
	if((*(tdata->localstatus) & LM_EXISTS) == LM_EXISTS) { //If there is a local machine involved in the transaction
		if(*(tdata->count) == tdata->pilecount - 1) {
			while(*(tdata->localstatus) & LM_UPDATED != LM_UPDATED) {
				;//Do nothing and wait until Local machine thread updates the common data structure
			}
			if(decideResponse(tdata) != 0) {
				printf("decideResponse returned error %s,%d\n", __FILE__, __LINE__);
				pthread_mutex_unlock(tdata->lock);
				return NULL;
			}
			pthread_cond_broadcast(tdata->threshold);
		}
	} else if ((*(tdata->localstatus) & LM_EXISTS) == 0) { //No local m/c involved in transaction
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
	}
*/

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
	} else if(*(tdata->replyctrl) == TRANS_COMMIT) { /* If the decided response is TRANS_COMMIT */
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

/*This function handles the local trans requests involved in a transaction commiting process
 * makes a decision if the local machine sends AGREE or DISAGREE or SOFT_ABORT
 * Activates the other nonlocal threads that are waiting for the decision and the
 * based on common decision by all groups involved in the transaction it 
 * either commits or aborts the transaction.
 * It also frees the calloced memory resources
 */

//int handleLocalReq(thread_data_array_t *tdata, trans_commit_data_t *transinfo) {
void *handleLocalReq(void *threadarg) {
	int val, i = 0;
	short version;
	char control = 0, *ptr;
	unsigned int oid;
	unsigned int *oidnotfound = NULL, *oidlocked = NULL, *oidmod = NULL;
	void *mobj, *modptr;
	objheader_t *headptr;
	local_thread_data_array_t *localtdata;

	localtdata = (local_thread_data_array_t *) threadarg;
	printf("DEBUG->Address of localtdata = %x\n", localtdata);

	printf("DEBUG-> Machine Pile numread recv = %d\n", localtdata->tdata->buffer->f.numread);
	/* Counters and arrays to formulate decision on control message to be sent */
	printf("DEBUG -> %d %d\n",localtdata->tdata->buffer->f.numread, localtdata->tdata->buffer->f.nummod);
	oidnotfound = (unsigned int *) calloc((localtdata->tdata->buffer->f.numread + localtdata->tdata->buffer->f.nummod), sizeof(unsigned int));
	oidlocked = (unsigned int *) calloc((localtdata->tdata->buffer->f.numread + localtdata->tdata->buffer->f.nummod), sizeof(unsigned int));
	oidmod = (unsigned int *) calloc(localtdata->tdata->buffer->f.nummod, sizeof(unsigned int));
	int objnotfound = 0, objlocked = 0, objmod =0, v_nomatch = 0, v_matchlock = 0, v_matchnolock = 0;
	int objmodnotfound = 0, nummodfound = 0;

	/* modptr points to the beginning of the object store 
	 * created at the Pariticipant */ 
	if ((modptr = objstrAlloc(mainobjstore, localtdata->tdata->buffer->f.sum_bytes)) == NULL) {
		printf("objstrAlloc error for modified objects %s, %d\n", __FILE__, __LINE__);
		return NULL;
	}

	ptr = modptr;

	/* Process each oid in the machine pile/ group per thread */
	for (i = 0; i < localtdata->tdata->buffer->f.numread + localtdata->tdata->buffer->f.nummod; i++) {
		if (i < localtdata->tdata->buffer->f.numread) {//Objs only read and not modified
			int incr = sizeof(unsigned int) + sizeof(short);// Offset that points to next position in the objread array
			incr *= i;
			oid = *((unsigned int *)(localtdata->tdata->buffer->objread + incr));
			incr += sizeof(unsigned int);
			version = *((short *)(localtdata->tdata->buffer->objread + incr));
		} else {//Objs modified
			headptr = (objheader_t *) ptr;
			oid = headptr->oid;
			oidmod[objmod] = oid;//Array containing modified oids
			objmod++;
			version = headptr->version;
			ptr += sizeof(objheader_t) + classsize[headptr->type];
		}

		/* Check if object is still present in the machine since the beginning of TRANS_REQUEST */

		/* Save the oids not found and number of oids not found for later use */
		if ((mobj = mhashSearch(oid)) == NULL) {/* Obj not found */
			/* Save the oids not found and number of oids not found for later use */

			oidnotfound[objnotfound] = ((objheader_t *)mobj)->oid;
			objnotfound++;
		} else { /* If Obj found in machine (i.e. has not moved) */
			/* Check if Obj is locked by any previous transaction */
			if ((((objheader_t *)mobj)->status & LOCK) == LOCK) {
				if (version == ((objheader_t *)mobj)->version) {      /* If not locked then match versions */ 
					v_matchlock++;
				} else {/* If versions don't match ...HARD ABORT */
					v_nomatch++;
					/* Send TRANS_DISAGREE to Coordinator */
					localtdata->tdata->recvmsg[localtdata->tdata->thread_id].rcv_status = TRANS_DISAGREE;
					printf("DEBUG -> Sending TRANS_DISAGREE\n");
					//return tdata->recvmsg[tdata->thread_id].rcv_status;  
				}
			} else {/* If Obj is not locked then lock object */
				((objheader_t *)mobj)->status |= LOCK;
				//TODO Remove this for Testing
				randomdelay();

				/* Save all object oids that are locked on this machine during this transaction request call */
				oidlocked[objlocked] = ((objheader_t *)mobj)->oid;
				objlocked++;
				if (version == ((objheader_t *)mobj)->version) { /* Check if versions match */
					v_matchnolock++;
				} else { /* If versions don't match ...HARD ABORT */
					v_nomatch++;
					/* Send TRANS_DISAGREE to Coordinator */
					localtdata->tdata->recvmsg[localtdata->tdata->thread_id].rcv_status = TRANS_DISAGREE;
					printf("DEBUG -> Sending TRANS_DISAGREE\n");
				//	return tdata->recvmsg[tdata->thread_id].rcv_status;  
				}
			}
		}
	}

	/*Decide the response to be sent to the Coordinator( the local machine in this case)*/

	/* Condition to send TRANS_AGREE */
	if(v_matchnolock == localtdata->tdata->buffer->f.numread + localtdata->tdata->buffer->f.nummod) {
		localtdata->tdata->recvmsg[localtdata->tdata->thread_id].rcv_status = TRANS_AGREE;
		printf("DEBUG -> Sending TRANS_AGREE\n");
	}
	/* Condition to send TRANS_SOFT_ABORT */
	if((v_matchlock > 0 && v_nomatch == 0) || (objnotfound > 0 && v_nomatch == 0)) {
		localtdata->tdata->recvmsg[localtdata->tdata->thread_id].rcv_status = TRANS_SOFT_ABORT;
		printf("DEBUG -> Sending TRANS_SOFT_ABORT\n");
		/* Send number of oids not found and the missing oids if objects are missing in the machine */
		/* TODO Remember to store the oidnotfound for later use
		if(objnotfound != 0) {
			int size = sizeof(unsigned int)* objnotfound;
		}
		*/
	}

	/* Fill out the trans_commit_data_t data structure. This is required for a trans commit process
	 * if Participant receives a TRANS_COMMIT */
	localtdata->transinfo->objmod = oidmod;
	localtdata->transinfo->objlocked = oidlocked;
	localtdata->transinfo->objnotfound = oidnotfound;
	localtdata->transinfo->modptr = modptr;
	localtdata->transinfo->nummod = localtdata->tdata->buffer->f.nummod;
	localtdata->transinfo->numlocked = objlocked;
	localtdata->transinfo->numnotfound = objnotfound;

	/*Set flag to show that common data structure for this individual thread has been written to */
	//*(tdata->localstatus) |= LM_UPDATED;
	
	/* Lock and update count */
	//Thread sleeps until all messages from pariticipants are received by coordinator
	pthread_mutex_lock(localtdata->tdata->lock);
	(*(localtdata->tdata->count))++; /* keeps track of no of messages received by the coordinator */

	/* Wake up the threads and invoke decideResponse (once) */
	if(*(localtdata->tdata->count) == localtdata->tdata->pilecount) {
		if (decideResponse(localtdata->tdata) != 0) { 
			printf("decideResponse returned error %s,%d\n", __FILE__, __LINE__);
			pthread_mutex_unlock(localtdata->tdata->lock);
			return NULL;
		}
		pthread_cond_broadcast(localtdata->tdata->threshold);
	} else {
		pthread_cond_wait(localtdata->tdata->threshold, localtdata->tdata->lock);
	}
	pthread_mutex_unlock(localtdata->tdata->lock);

	/*Based on DecideResponse(), Either COMMIT or ABORT the operation*/
	if(*(localtdata->tdata->replyctrl) == TRANS_ABORT){
		if(transAbortProcess(modptr,oidlocked, localtdata->transinfo->numlocked, localtdata->transinfo->nummod) != 0) {
			printf("Error in transAbortProcess() %s,%d\n", __FILE__, __LINE__);
			return NULL;
		}
	}else if(*(localtdata->tdata->replyctrl) == TRANS_COMMIT){
		if(transComProcess(localtdata->transinfo) != 0) {
			printf("Error in transComProcess() %s,%d\n", __FILE__, __LINE__);
			return NULL;
		}
	}

	/* Free memory */
	printf("DEBUG -> Freeing...\n");
	fflush(stdout);
	if (localtdata->transinfo->objmod != NULL) {
		free(localtdata->transinfo->objmod);
		localtdata->transinfo->objmod = NULL;
	}
	if (localtdata->transinfo->objlocked != NULL) {
		free(localtdata->transinfo->objlocked);
		localtdata->transinfo->objlocked = NULL;
	}
	if (localtdata->transinfo->objnotfound != NULL) {
		free(localtdata->transinfo->objnotfound);
		localtdata->transinfo->objnotfound = NULL;
	}
	
	pthread_exit(NULL);
}
/* This function completes the ABORT process if the transaction is aborting 
 */
int transAbortProcess(void *modptr, unsigned int *objlocked, int numlocked, int nummod) {
	char *ptr;
	int i;
	objheader_t *tmp_header;
	void *header;

	printf("DEBUG -> Recv TRANS_ABORT\n");
	/* Set all ref counts as 1 and do garbage collection */
	ptr = modptr;
	for(i = 0; i< nummod; i++) {
		tmp_header = (objheader_t *)ptr;
		tmp_header->rcount = 1;
		ptr += sizeof(objheader_t) + classsize[tmp_header->type];
	}
	/* Unlock objects that was locked due to this transaction */
	for(i = 0; i< numlocked; i++) {
		header = mhashSearch(objlocked[i]);// find the header address
		((objheader_t *)header)->status &= ~(LOCK);
	}

	/* Send ack to Coordinator */
	printf("DEBUG-> TRANS_SUCCESSFUL\n");

	/*Free the pointer */
	ptr = NULL;
	return 0;
}

/*This function completes the COMMIT process is the transaction is commiting
 */
 int transComProcess(trans_commit_data_t *transinfo) {
	 objheader_t *header;
	 int i = 0, offset = 0;
	 char control;
	 
	 printf("DEBUG -> Recv TRANS_COMMIT\n");
	 /* Process each modified object saved in the mainobject store */
	 for(i=0; i<transinfo->nummod; i++) {
		 if((header = (objheader_t *) mhashSearch(transinfo->objmod[i])) == NULL) {
			 printf("mhashsearch returns NULL at %s, %d\n", __FILE__, __LINE__);
		 }
		 /* Change reference count of older address and free space in objstr ?? */
		 header->rcount = 1; //TODO Not sure what would be the val

		 /* Change ptr address in mhash table */
		 printf("DEBUG -> removing object oid = %d\n", transinfo->objmod[i]);
		 mhashRemove(transinfo->objmod[i]);
		 mhashInsert(transinfo->objmod[i], (transinfo->modptr + offset));
		 offset += sizeof(objheader_t) + classsize[header->type];

		 /* Update object version number */
		 header = (objheader_t *) mhashSearch(transinfo->objmod[i]);
		 header->version += 1;
	 }

	 /* Unlock locked objects */
	 for(i=0; i<transinfo->numlocked; i++) {
		 header = (objheader_t *) mhashSearch(transinfo->objlocked[i]);
		 header->status &= ~(LOCK);
	 }

	 //TODO Update location lookup table

	 /* Send ack to Coordinator */
	 printf("DEBUG-> TRANS_SUCESSFUL\n");
	 return 0;
 }

/*This function makes piles to prefetch records and prefetches the oids from remote machines */
int transPrefetch(transrecord_t *record, trans_prefetchtuple_t *prefetchtuple){
	/* Create Pile*/
	/* For each Pile in the machine send TRANS_PREFETCH */
}
