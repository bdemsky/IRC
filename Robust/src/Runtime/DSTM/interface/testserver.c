#include <pthread.h>
#include "dstm.h"
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include "ip.h"

extern objstr_t *mainobjstore;
int classsize[]={sizeof(int),sizeof(char),sizeof(short), sizeof(void *)};

int test1(void);
int test2(void);
int test3(void);

unsigned int createObjects(transrecord_t *record, unsigned short type) {
	objheader_t *header, *tmp;
	struct sockaddr_in antelope;
	unsigned int size, mid;
	size = sizeof(objheader_t) + classsize[type] ;
	//Inserts in chashtable
	header = transCreateObj(record, type);
	tmp = (objheader_t *) objstrAlloc(mainobjstore, size);
	memcpy(tmp, header, size);
	mhashInsert(tmp->oid, tmp);
	mid = iptoMid("128.200.9.10");
	lhashInsert(tmp->oid, mid);
	//Lock oid 3 object
//	if(tmp->oid == 3)
//		tmp->status |= LOCK;
	return 0;
}

void init_obj(objheader_t *h, unsigned int oid, unsigned short type, \
		unsigned short version,\
		unsigned short rcount, char status) {
	h->oid = oid;
	h->type = type;
	h->version = version;
	h->rcount = rcount;
	h->status |= status;
	return;
}


int main()
{
//	test1();
//	test3();
	test4();
}

int test1()
{
	unsigned int val;
	transrecord_t *myTrans;
	pthread_t thread_Listen;

	dstmInit();	
	pthread_create(&thread_Listen, NULL, dstmListen, NULL);
	// Start Transaction	
	myTrans = transStart();

	printf("Creating Transaction\n");
	//Create Object1
	if((val = createObjects(myTrans, 0)) != 0) {
		printf("Error transCreateObj1");
	}
	//Create Object2
	if((val = createObjects(myTrans, 1)) != 0) {
		printf("Error transCreateObj2");
	}
	//Create Object3
	if((val = createObjects(myTrans, 2)) != 0) {
		printf("Error transCreateObj3");
	}
	//Create Object4
	if((val = createObjects(myTrans, 3)) != 0) {
		printf("Error transCreateObj4");
	}
	//Create Object5
	if((val = createObjects(myTrans, 0)) != 0) {
		printf("Error transCreateObj5");
	}
	//Create Object6
	if((val = createObjects(myTrans, 1)) != 0) {
		printf("Error transCreateObj6");
	}
	pthread_join(thread_Listen, NULL);
	return 0;
}

int test2() {
	
	unsigned int val, mid;
	transrecord_t *myTrans;
	pthread_t thread_Listen;

	dstmInit();	
	mid = iptoMid("128.200.9.27"); //d-2.eecs.uci.edu
	//Inserting into lhashtable
	lhashInsert(20, mid);
	lhashInsert(21, mid);
	lhashInsert(22, mid);
	lhashInsert(23, mid);
	lhashInsert(30, mid);
	lhashInsert(28, mid);
	lhashInsert(29, mid);
	pthread_create(&thread_Listen, NULL, dstmListen, NULL);
	// Start Transaction	
	myTrans = transStart();

	printf("Creating Transaction\n");
	//Create Object1
	if((val = createObjects(myTrans, 0)) != 0) {
		printf("Error transCreateObj1");
	}
	//Create Object2
	if((val = createObjects(myTrans, 1)) != 0) {
		printf("Error transCreateObj2");
	}
	//Create Object3
	if((val = createObjects(myTrans, 2)) != 0) {
		printf("Error transCreateObj3");
	}
	//Create Object4
	if((val = createObjects(myTrans, 3)) != 0) {
		printf("Error transCreateObj4");
	}
	//Create Object5
	if((val = createObjects(myTrans, 0)) != 0) {
		printf("Error transCreateObj5");
	}
	//Create Object6
	if((val = createObjects(myTrans, 1)) != 0) {
		printf("Error transCreateObj6");
	}
	pthread_join(thread_Listen, NULL);
}
//Commit transaction with all locally available objects
int test3() {
	unsigned int val, mid;
	transrecord_t *myTrans;
	unsigned int size;
	objheader_t *header;
	pthread_t thread_Listen;
	pthread_attr_t attr;
	objheader_t *h1, *h2, *h3;//h1,h2,h3 from local

	dstmInit();	
	pthread_attr_init(&attr);
	pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_JOINABLE);

	// Create and Insert Oid 1
	size = sizeof(objheader_t) + classsize[0] ;
	header = (objheader_t *) objstrAlloc(mainobjstore, size);
	init_obj(header, 1, 0, 1, 0, NEW);
	mhashInsert(header->oid, header);
	mid = iptoMid("128.200.9.10");
	lhashInsert(header->oid, mid);

	// Create and Insert Oid 2
	size = sizeof(objheader_t) + classsize[1] ;
	header = (objheader_t *) objstrAlloc(mainobjstore, size);
	init_obj(header, 2, 1, 1, 0, NEW);
	mhashInsert(header->oid, header);
	mid = iptoMid("128.200.9.10");
	lhashInsert(header->oid, mid);


	// Create and Insert Oid 3
	size = sizeof(objheader_t) + classsize[2] ;
	header = (objheader_t *) objstrAlloc(mainobjstore, size);
	init_obj(header, 3, 2, 1, 0, NEW);
	mhashInsert(header->oid, header);
	mid = iptoMid("128.200.9.10");
	lhashInsert(header->oid, mid);

	// Create and Insert Oid 4
	size = sizeof(objheader_t) + classsize[3] ;
	header = (objheader_t *) objstrAlloc(mainobjstore, size);
	init_obj(header, 4, 3, 1, 0, NEW);
	mhashInsert(header->oid, header);
	mid = iptoMid("128.200.9.10");
	lhashInsert(header->oid, mid);

	//Inserting into lhashtable
	mid = iptoMid("128.200.9.29"); //d-3.eecs.uci.edu
	lhashInsert(20, mid);
	lhashInsert(21, mid);
	lhashInsert(22, mid);

	mid = iptoMid("128.195.175.69"); //dw-1.eecs.uci.edu
	//Inserting into lhashtable
	lhashInsert(31, mid);
	lhashInsert(32, mid);
	lhashInsert(33, mid);
	pthread_create(&thread_Listen, &attr, dstmListen, NULL);

	//Check if machine dw-1 is up and running
	checkServer(mid, "128.195.175.69");
	mid = iptoMid("128.200.9.29");
	//Check if machine d-3 is up and running
	checkServer(mid, "128.200.9.29");

	// Start Transaction	
	myTrans = transStart();

	//read object 1(present in local machine)
	if((h1 = transRead(myTrans, 1)) == NULL){
		printf("Object not found\n");
	}
	//read object 2present in local machine)
	if((h2 = transRead(myTrans, 2)) == NULL) {
		printf("Object not found\n");
	}
	//read object 3(present in local machine)
	if((h3 = transRead(myTrans, 3)) == NULL) {
		printf("Object not found\n");
	}

	// Commit transaction
	transCommit(myTrans);

	pthread_join(thread_Listen, NULL);

	return 0;
}

//Commit transaction with few locally available objects and other objects from machine d-1
// and d-2
int test4() {

	unsigned int val, mid;
	transrecord_t *myTrans;
	unsigned int size;
	objheader_t *header;
	pthread_t thread_Listen;
	pthread_attr_t attr;
	objheader_t *h1, *h2, *h3, *h4;//h1,h2 from local ; h3 from d-1 , h-4 from d-2

	dstmInit();	
	pthread_attr_init(&attr);
	pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_JOINABLE);

	// Create and Insert Oid 1
	size = sizeof(objheader_t) + classsize[0] ;
	header = (objheader_t *) objstrAlloc(mainobjstore, size);
	init_obj(header, 1, 0, 1, 0, NEW);
	mhashInsert(header->oid, header);
	mid = iptoMid("128.200.9.10");
	lhashInsert(header->oid, mid);

	// Create and Insert Oid 2
	size = sizeof(objheader_t) + classsize[1] ;
	header = (objheader_t *) objstrAlloc(mainobjstore, size);
	init_obj(header, 2, 1, 1, 0, NEW);
	mhashInsert(header->oid, header);
	mid = iptoMid("128.200.9.10");
	lhashInsert(header->oid, mid);


	// Create and Insert Oid 3
	size = sizeof(objheader_t) + classsize[2] ;
	header = (objheader_t *) objstrAlloc(mainobjstore, size);
	init_obj(header, 3, 2, 1, 0, NEW);
	mhashInsert(header->oid, header);
	mid = iptoMid("128.200.9.10");
	lhashInsert(header->oid, mid);

	// Create and Insert Oid 4
	size = sizeof(objheader_t) + classsize[3] ;
	header = (objheader_t *) objstrAlloc(mainobjstore, size);
	init_obj(header, 4, 3, 1, 0, NEW);
	mhashInsert(header->oid, header);
	mid = iptoMid("128.200.9.10");
	lhashInsert(header->oid, mid);

	//Inserting into lhashtable
	mid = iptoMid("128.200.9.29"); //d-3.eecs.uci.edu
	lhashInsert(20, mid);
	lhashInsert(21, mid);
	lhashInsert(22, mid);

	mid = iptoMid("128.195.175.69"); //dw-1.eecs.uci.edu
	//Inserting into lhashtable
	lhashInsert(31, mid);
	lhashInsert(32, mid);
	lhashInsert(33, mid);

	pthread_create(&thread_Listen, &attr, dstmListen, NULL);
	//Check if machine dw-1 is up and running
	checkServer(mid, "128.195.175.69");
	mid = iptoMid("128.200.9.29");
	//Check if machine d-3 is up and running
	checkServer(mid, "128.200.9.29");

	// Start Transaction	
	myTrans = transStart();

	//read object 1(present in local machine)
	if((h1 = transRead(myTrans, 2)) == NULL){
		printf("Object not found\n");
	}

	//read object 2present in local machine)
	if((h2 = transRead(myTrans, 1)) == NULL) {
		printf("Object not found\n");
	}
	//read object 31(present in dw-1 machine)
	if((h3 = transRead(myTrans, 31)) == NULL) {
		printf("Object not found\n");
	}
	//read object 21(present in d-3 machine)
	if((h4 = transRead(myTrans, 21)) == NULL) {
		printf("Object not found\n");
	}
	
	// Commit transaction
	transCommit(myTrans);

	pthread_join(thread_Listen, NULL);

	return 0;
}
int test5() {
	
	unsigned int val, mid;
	transrecord_t *myTrans;
	unsigned int size;
	objheader_t *header;
	pthread_t thread_Listen;
	pthread_attr_t attr;
	objheader_t *h1, *h2, *h3, *h4, *h5;

	dstmInit();	
	pthread_attr_init(&attr);
	pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_JOINABLE);

	mid = iptoMid("128.200.9.27"); //d-2.eecs.uci.edu
	//Inserting into lhashtable
	lhashInsert(20, mid);
	lhashInsert(21, mid);
	lhashInsert(22, mid);

	mid = iptoMid("128.200.9.26"); //d-1.eecs.uci.edu
	//Inserting into lhashtable
	lhashInsert(31, mid);
	lhashInsert(32, mid);
	lhashInsert(33, mid);
	pthread_create(&thread_Listen, &attr, dstmListen, NULL);

	printf("DEBUG -> mid = %d\n", mid);
	checkServer(mid, "128.200.9.26");
	mid = iptoMid("128.200.9.27");
	printf("DEBUG -> mid = %d\n", mid);
	checkServer(mid, "128.200.9.27");

	// Start Transaction	
	myTrans = transStart();

	// Create and Insert Oid 1
	size = sizeof(objheader_t) + classsize[0] ;
	header = (objheader_t *) objstrAlloc(mainobjstore, size);
	init_obj(header, 1, 0, 1, 0, NEW);
	mhashInsert(header->oid, header);
	mid = iptoMid("128.200.9.10");
	lhashInsert(header->oid, mid);

	// Create and Insert Oid 2
	size = sizeof(objheader_t) + classsize[1] ;
	header = (objheader_t *) objstrAlloc(mainobjstore, size);
	init_obj(header, 2, 1, 1, 0, NEW);
	mhashInsert(header->oid, header);
	mid = iptoMid("128.200.9.10");
	lhashInsert(header->oid, mid);


	// Create and Insert Oid 3
	size = sizeof(objheader_t) + classsize[2] ;
	header = (objheader_t *) objstrAlloc(mainobjstore, size);
	init_obj(header, 3, 2, 1, 0, NEW);
	mhashInsert(header->oid, header);
	mid = iptoMid("128.200.9.10");
	lhashInsert(header->oid, mid);

	// Create and Insert Oid 4
	size = sizeof(objheader_t) + classsize[3] ;
	header = (objheader_t *) objstrAlloc(mainobjstore, size);
	init_obj(header, 4, 3, 1, 0, NEW);
	mhashInsert(header->oid, header);
	mid = iptoMid("128.200.9.10");
	lhashInsert(header->oid, mid);

	// Create and Insert Oid 5
	size = sizeof(objheader_t) + classsize[0] ;
	header = (objheader_t *) objstrAlloc(mainobjstore, size);
	init_obj(header, 5, 0, 1, 0, NEW);
	mhashInsert(header->oid, header);
	mid = iptoMid("128.200.9.10");
	lhashInsert(header->oid, mid);
	
	// Create and Insert Oid 6
	size = sizeof(objheader_t) + classsize[1] ;
	header = (objheader_t *) objstrAlloc(mainobjstore, size);
	init_obj(header, 6, 1, 1, 0, NEW);
	mhashInsert(header->oid, header);
	mid = iptoMid("128.200.9.10");
	lhashInsert(header->oid, mid);
	
	//read object 1(present in local machine)
	if((h1 = transRead(myTrans, 1)) == NULL){
		printf("Object not found\n");
	}
	//read object 2present in local machine)
	if((h2 = transRead(myTrans, 2)) == NULL) {
		printf("Object not found\n");
	}
	//read object 3(present in local machine)
	if((h3 = transRead(myTrans, 3)) == NULL) {
		printf("Object not found\n");
	}
	//read object 31 (present in d-1. eecs)
	if((h4 = transRead(myTrans, 31)) == NULL) {
		printf("Object not found\n");
	}
	//read object 20 (present in d-2. eecs)
	if((h5 = transRead(myTrans, 20)) == NULL) {
		printf("Object not found\n");
	}

	transCommit(myTrans);

	pthread_join(thread_Listen, NULL);

	return 0;
}
