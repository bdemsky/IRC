#include "dstm.h"
#include "clookup.h"
#include "mlookup.h"
#include "llookup.h"
#include "plookup.h"
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

int transCommit(transrecord_t *record){	
	chashlistnode_t *curr, *ptr, *next;
	unsigned int size;//Represents number of bins in the chash table
	unsigned int machinenum;
	objheader_t *headeraddr, *localheaderaddr;
	plistnode_t *tmp, *pile = NULL;
	int sd,n,i;
	short numread = 0,nummod = 0;
	struct sockaddr_in serv_addr;
	struct hostent *server;
	char buffer[RECEIVE_BUFFER_SIZE],control;
	
	ptr = record->lookupTable->table;
	size = record->lookupTable->size;
	//Look through all the objects in the cache and make pils
	//Outer loop for chashtable
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
			// Make piles
			pInsert(pile, machinenum, curr->key, record->lookupTable->numelements);	
			curr = next;
		}
	}

	tmp = pile;	
	unsigned int oidmod[record->lookupTable->numelements];
	unsigned int oidread[record->lookupTable->numelements];
	//Process each machine in pile
	while(tmp != NULL) {
		//Identify which oids have been updated and which ones have been just read
		for(i = 0; i < pile->index; i++) {
			headeraddr = (objheader_t *) chashSearch(record->lookupTable, pile->obj[i]);
			//check if object modified in cache  ??
			if(headeraddr->status >>= DIRTY){
				//Keep track of oids that have been modified	
				oidmod[nummod] = headeraddr->oid;
				nummod++;
			} else {
				oidread[numread] = headeraddr->oid;
				numread++;
			}
		}
		//Send Trans Request in the form
		if ((sd = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
			perror("Error in socket for TRANS_REQUEST");
			return 1;
		}
		bzero((char*) &serv_addr, sizeof(serv_addr));
		serv_addr.sin_family = AF_INET;
		serv_addr.sin_port = htons(LISTEN_PORT);
		serv_addr.sin_addr.s_addr = inet_addr(MACHINE_IP);
		//serv_addr.sin_addr.s_addr = inet_addr(pile->mid);

		if (connect(sd, (struct sockaddr *) &serv_addr, sizeof(struct sockaddr)) < 0) {
			perror("Error in connect for TRANS_REQUEST");
			return 1;
		}
		
		bzero((char *)buffer,sizeof(buffer));
		control = TRANS_REQUEST;
		buffer[0] = control;
		//Send numread, nummod, sizeof header for objects read, size of header+objects that are modified
		int offset = 1;
		memcpy(buffer+offset, &numread, sizeof(short));
		offset += sizeof(short);
		memcpy(buffer+offset, &nummod, sizeof(short));
		offset += sizeof(short);
		for( i= 0; i< numread; i++) {
			headeraddr = (objheader_t *) chashSearch(record->lookupTable, oidread[i]);
			memcpy(buffer+offset, headeraddr, sizeof(objheader_t));
			offset += sizeof(objheader_t);
		}
		for( i= 0; i< nummod; i++) {
			headeraddr = (objheader_t *) chashSearch(record->lookupTable, oidmod[i]);
			memcpy(buffer+offset, headeraddr, sizeof(objheader_t) + classsize[headeraddr->type]);
			offset += sizeof(objheader_t) + classsize[headeraddr->type];
		}
		if (offset > RECEIVE_BUFFER_SIZE) {
			printf("Error: Buffersize too small");
		}
		if (write(sd, buffer, sizeof(buffer)) < 0) {
			perror("Error sending message");
			return 1;
		}
#ifdef DEBUG1
		printf("DEBUG -> ready to rcv ...\n");
#endif
		read(sd, buffer, sizeof(buffer));
		close(sd);
		printf("Server sent %d\n",buffer[0]);
		/*
		if (buffer[0] == TRANS_AGREE) {
			//change machine pile
			
		} 
		*/
		//Reset numread and nummod for the next pile
	       numread = nummod = 0;	
	       tmp = tmp->next;

	}

}


#if 0
int transCommit(transrecord_t *record){	
	//Look through all the objects in the cache
	int i,numelements,isFirst;
	unsigned int size,machinenum;//Represents number of buckets
	void *address;
	objheader_t *headeraddr,localheaderaddr;
	chashlistnode_t *curr, *ptr, *next;
	int sd, size;
	struct sockaddr_in serv_addr;
	struct hostent *server;
	char buffer[RECEIVE_BUFFER_SIZE],control;
	
	ptr = record->lookupTable->table;
	size = record->lookupTable->size;
	//Outer loop for chashtable
	for(i = 0; i< size ;i++) {
		curr = &ptr[i];
		//Inner look to traverse the linked list of the cache lookupTable
		while(curr != NULL) {
			if(curr->key == 0) {
				break;
			}
			//Find if local or remote
			address = mhashSearch(curr->key);
			d
			localheaderaddr = (objheader_t *) curr->value;
			if(address != NULL) {
				//Is local so  check if the local copy has been updated	
				headeraddr = (objheader_t *) address;				
				if(localheaderaddr->version == headeraddr->version){
					//Lock Object
					
				}
				else {
					//vote as DISAGREE
					//Start TransAbort();
					//Unlock object
				}
			}
			else {
				//Is remote
				//Find which machine it belongs to
				machinenum = lhashSearch(curr->key);
				//Start TRANS_REQUEST to machine

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

			}
			next = curr->next;
		}
		curr = next;
	}	

}
#endif


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
