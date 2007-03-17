#include "dstm.h"
#include "clookup.h"
#include "mlookup.h"
#include "llookup.h"
#include<sys/types.h>
#include<sys/socket.h>
#include<netdb.h>
#include<netinet/in.h>

#define LISTEN_PORT 2156
#define MACHINE_IP "127.0.0.1"
#define RECIEVE_BUFFER_SIZE 2048

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
		printf(" oid not found in cache\n");
		tmp = mhashSearch(oid);
		size = sizeof(objheader_t)+classsize[tmp->type];
		//Copy into cache
		objcopy = objstrAlloc(record->cache, size);
		memcpy(objcopy, (void *)tmp, size);
		//Insert into cache's lookup table
		chashInsert(record->lookupTable, objheader->oid, objcopy); 
		return(objcopy);
	} else {
		printf(" oid not found in Machine Lookup\n");
		machinenumber = lhashSearch(oid);
		//Get object from a given machine 
	/*	if (getRemoteObj(record, machinenumber, oid) != 0) {
			printf("Error getRemoteObj");
		}
	*/
		objcopy = getRemoteObj(record, machinenumber, oid);
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
	//Move objects to machine that hosts it	

}

int transAbort(transrecord_t *record){

}

//mnun will be used to represent the machine IP address later
void *getRemoteObj(transrecord_t *record, unsigned int mnum, unsigned int oid) {
	int sd, size;
	struct sockaddr_in serv_addr;
	struct hostent *server;
	char buffer[RECIEVE_BUFFER_SIZE];
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
	sprintf(buffer, "TRANS_RD %d\n", oid);
	if (write(sd, buffer, sizeof(buffer)) < 0) {
		perror("Error sending message");
		return NULL;
	}
	printf("DEBUG -> ready to rcv ...\n");
	/*
	while (read(sd, buffer, sizeof(buffer)) != 0) {
		;
	}
	*/
	read(sd, buffer, sizeof(buffer));
	h = (objheader_t *) buffer;
	size = sizeof(objheader_t) + sizeof(classsize[h->type]);
	printf("DEBUG -> Received: oid = %d, type = %d\n", h->oid, h->type);
	fflush(stdout);
	objcopy = objstrAlloc(record->cache, size);
	memcpy(objcopy, (void *)buffer, size);
	//Insert into cache's lookup table
	chashInsert(record->lookupTable, oid, objcopy); 
	return objcopy;
}
