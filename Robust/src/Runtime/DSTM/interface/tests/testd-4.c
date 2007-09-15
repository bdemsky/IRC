#include <pthread.h>
#include "dstm.h"

extern objstr_t *mainobjstore;
int classsize[]={sizeof(int),sizeof(char),sizeof(short), sizeof(void *)};

int main()
{
	test1();
//	test2();
//	test3();
//	test4();
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

//Test case to create objects and do nothing else
int test1() {
	unsigned int val, mid;
	unsigned int size;
	transrecord_t *myTrans;
	objheader_t *header;
	pthread_t thread_Listen;
	pthread_attr_t attr;

	dstmInit();
	pthread_attr_init(&attr);
	pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_JOINABLE);

	//Create and Insert Oid 31
	size = sizeof(objheader_t) + classsize[2] ;
	header = (objheader_t *) objstrAlloc(mainobjstore, size);
	init_obj(header, 31, 2, 1, 0, NEW);
	mhashInsert(header->oid, header);
	mid = iptoMid("128.195.175.69");
	lhashInsert(header->oid, mid);

	//Create and Insert Oid 32
	size = sizeof(objheader_t) + classsize[1] ;
	header = (objheader_t *) objstrAlloc(mainobjstore, size);
	init_obj(header, 32, 1, 1, 0, NEW);
	mhashInsert(header->oid, header);
	mid = iptoMid("128.195.175.69");
	lhashInsert(header->oid, mid);

	//Create and Insert Oid 33
	size = sizeof(objheader_t) + classsize[0] ;
	header = (objheader_t *) objstrAlloc(mainobjstore, size);
	init_obj(header, 33, 0, 1, 0, NEW);
	mhashInsert(header->oid, header);
	mid = iptoMid("128.195.175.69");
	lhashInsert(header->oid, mid);

	//Inserting into lhashtable into d-3.eecs
	mid = iptoMid("128.200.9.29"); //d-3.eecs.uci.edu
	lhashInsert(20, mid);
	lhashInsert(21, mid);
	lhashInsert(22, mid);

	mid = iptoMid("128.200.9.10"); //demsky.eecs.uci.edu
	//Inserting into lhashtable of demsky.eecs
	lhashInsert(1, mid);
	lhashInsert(2, mid);
	lhashInsert(3, mid);
	lhashInsert(4, mid);
	
	pthread_create(&thread_Listen, &attr, dstmListen, NULL);
	//Check if machine demsky is up and running
	checkServer(mid, "128.200.9.10");
	mid = iptoMid("128.200.9.29");
	//Check if machine d-3 is up and running
	checkServer(mid, "128.200.9.29");

	pthread_join(thread_Listen, NULL);

	return 0;
}

//Read objects from remote and local machines ; NOTE objects are all available
int test2() {

	unsigned int val, mid;
	unsigned int size;
	transrecord_t *myTrans;
	objheader_t *header, *h1, *h2, *h3, *h4;
	pthread_t thread_Listen;
	pthread_attr_t attr;

	dstmInit();
	pthread_attr_init(&attr);
	pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_JOINABLE);

	//Create and Insert Oid 31
	size = sizeof(objheader_t) + classsize[2] ;
	header = (objheader_t *) objstrAlloc(mainobjstore, size);
	init_obj(header, 31, 2, 1, 0, NEW);
	mhashInsert(header->oid, header);
	mid = iptoMid("128.195.175.69");
	lhashInsert(header->oid, mid);

	//Create and Insert Oid 32
	size = sizeof(objheader_t) + classsize[1] ;
	header = (objheader_t *) objstrAlloc(mainobjstore, size);
	init_obj(header, 32, 1, 1, 0, NEW);
	mhashInsert(header->oid, header);
	mid = iptoMid("128.195.175.69");
	lhashInsert(header->oid, mid);

	//Create and Insert Oid 33
	size = sizeof(objheader_t) + classsize[0] ;
	header = (objheader_t *) objstrAlloc(mainobjstore, size);
	init_obj(header, 33, 0, 1, 0, NEW);
	mhashInsert(header->oid, header);
	mid = iptoMid("128.195.175.69");
	lhashInsert(header->oid, mid);

	//Inserting into lhashtable into d-3.eecs
	mid = iptoMid("128.200.9.29"); //d-3.eecs.uci.edu
	lhashInsert(20, mid);
	lhashInsert(21, mid);
	lhashInsert(22, mid);

	mid = iptoMid("128.200.9.10"); //demsky.eecs.uci.edu
	//Inserting into lhashtable of demsky.eecs
	lhashInsert(1, mid);
	lhashInsert(2, mid);
	lhashInsert(3, mid);
	lhashInsert(4, mid);
	
	pthread_create(&thread_Listen, &attr, dstmListen, NULL);

	//Check if machine demsky is up and running
	checkServer(mid, "128.200.9.10");
	mid = iptoMid("128.200.9.29");
	//Check if machine d-2 is up and running
	checkServer(mid, "128.200.9.29");

	// Start Transaction
	myTrans = transStart();

	//read object 1 (found on demksy)
	if((h1 = transRead(myTrans, 1)) == NULL){
		printf("Object not found\n");
	}
	
	//read object 33 (found on local)
	if((h2 = transRead(myTrans, 33)) == NULL){
		printf("Object not found\n");
	}

	//read object 21 (found on d-3)
	if((h3 = transRead(myTrans, 21)) == NULL){
		printf("Object not found\n");
	}

	//read object 32 (found on local)
	if((h4 = transRead(myTrans, 32)) == NULL){
		printf("Object not found\n");
	}

	pthread_join(thread_Listen, NULL);

	return 0;
}

int test3() {

	unsigned int val, mid;
	unsigned int size;
	transrecord_t *myTrans;
	objheader_t *header, *h1, *h2, *h3;
	pthread_t thread_Listen;
	pthread_attr_t attr;

	dstmInit();
	pthread_attr_init(&attr);
	pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_JOINABLE);

	//Create and Insert Oid 31
	size = sizeof(objheader_t) + classsize[2] ;
	header = (objheader_t *) objstrAlloc(mainobjstore, size);
	init_obj(header, 31, 2, 1, 0, NEW);
	mhashInsert(header->oid, header);
	mid = iptoMid("128.195.175.69");
	lhashInsert(header->oid, mid);

	//Create and Insert Oid 32
	size = sizeof(objheader_t) + classsize[1] ;
	header = (objheader_t *) objstrAlloc(mainobjstore, size);
	init_obj(header, 32, 1, 1, 0, NEW);
	mhashInsert(header->oid, header);
	mid = iptoMid("128.195.175.69");
	lhashInsert(header->oid, mid);

	//Create and Insert Oid 33
	size = sizeof(objheader_t) + classsize[0] ;
	header = (objheader_t *) objstrAlloc(mainobjstore, size);
	init_obj(header, 33, 0, 1, 0, NEW);
	mhashInsert(header->oid, header);
	mid = iptoMid("128.195.175.69");
	lhashInsert(header->oid, mid);

	//Inserting into lhashtable into d-3.eecs
	mid = iptoMid("128.200.9.29"); //d-3.eecs.uci.edu
	lhashInsert(20, mid);
	lhashInsert(21, mid);
	lhashInsert(22, mid);

	mid = iptoMid("128.200.9.10"); //demsky.eecs.uci.edu
	//Inserting into lhashtable of demsky.eecs
	lhashInsert(1, mid);
	lhashInsert(2, mid);
	lhashInsert(3, mid);
	lhashInsert(4, mid);
	
	pthread_create(&thread_Listen, &attr, dstmListen, NULL);

	//Check if machine demsky is up and running
	checkServer(mid, "128.200.9.10");
	mid = iptoMid("128.200.9.29");
	//Check if machine d-3 is up and running
	checkServer(mid, "128.200.9.29");

	// Start Transaction
	myTrans = transStart();

	//read object 4 (found on demksy)
	if((h1 = transRead(myTrans, 4)) == NULL){
		printf("Object not found\n");
	}
	//read object 33 (found on local)
	if((h2 = transRead(myTrans, 33)) == NULL){
		printf("Object not found\n");
	}
	//read object 20 (found on d-3)
	if((h3 = transRead(myTrans, 20)) == NULL){
		printf("Object not found\n");
	}

	//Commit transaction
	transCommit(myTrans);

	pthread_join(thread_Listen, NULL);

	return 0;
}
//Commit transaction for some objects that are available and some that are
//not available anywhere
int test4() {
	unsigned int val, mid;
	unsigned int size;
	transrecord_t *myTrans;
	objheader_t *header, *h1, *h2, *h3, *h4;
	pthread_t thread_Listen;
	pthread_attr_t attr;

	dstmInit();
	pthread_attr_init(&attr);
	pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_JOINABLE);

	//Create and Insert Oid 31
	size = sizeof(objheader_t) + classsize[2] ;
	header = (objheader_t *) objstrAlloc(mainobjstore, size);
	init_obj(header, 31, 2, 1, 0, NEW);
	mhashInsert(header->oid, header);
	mid = iptoMid("128.195.175.69");
	lhashInsert(header->oid, mid);

	//Create and Insert Oid 32
	size = sizeof(objheader_t) + classsize[1] ;
	header = (objheader_t *) objstrAlloc(mainobjstore, size);
	init_obj(header, 32, 1, 1, 0, NEW);
	mhashInsert(header->oid, header);
	mid = iptoMid("128.195.175.69");
	lhashInsert(header->oid, mid);

	//Create and Insert Oid 33
	size = sizeof(objheader_t) + classsize[0] ;
	header = (objheader_t *) objstrAlloc(mainobjstore, size);
	init_obj(header, 33, 0, 1, 0, NEW);
	mhashInsert(header->oid, header);
	mid = iptoMid("128.195.175.69");
	lhashInsert(header->oid, mid);

	//Inserting into lhashtable into d-3.eecs
	mid = iptoMid("128.200.9.29"); //d-3.eecs.uci.edu
	lhashInsert(20, mid);
	lhashInsert(21, mid);
	lhashInsert(22, mid);

	mid = iptoMid("128.200.9.10"); //demsky.eecs.uci.edu
	//Inserting into lhashtable of demsky.eecs
	lhashInsert(1, mid);
	lhashInsert(2, mid);
	lhashInsert(3, mid);
	lhashInsert(4, mid);
	
	pthread_create(&thread_Listen, &attr, dstmListen, NULL);

	//Check if machine demsky is up and running
	checkServer(mid, "128.200.9.10");
	mid = iptoMid("128.200.9.29");
	//Check if machine d-2 is up and running
	checkServer(mid, "128.200.9.29");

	// Start Transaction
	myTrans = transStart();

	//read object 4 (found on demksy)
	if((h1 = transRead(myTrans, 4)) == NULL){
		printf("Object not found\n");
	}
	//read object 33 (found on local)
	if((h2 = transRead(myTrans, 33)) == NULL){
		printf("Object not found\n");
	}
	//read object 24 (found nowhere)
	if((h3 = transRead(myTrans, 24)) == NULL){
		printf("Object not found\n");
	}
	//read object 50 (found nowhere)
	if((h4 = transRead(myTrans, 50)) == NULL){
		printf("Object not found\n");
	}

	//Commit transaction
	if((h1 != NULL) && (h2 != NULL) && (h3 != NULL) && (h4 !=NULL)) 
		transCommit(myTrans);
	else
		printf("Cannot complete this transaction\n");

	pthread_join(thread_Listen, NULL);
	return 0;

}
