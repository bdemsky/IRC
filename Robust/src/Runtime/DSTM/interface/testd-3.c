#include<stdio.h>
#include<pthread.h>
#include "dstm.h"
#include "llookup.h"
#include "ip.h"

#define LISTEN_PORT 2156

extern objstr_t *mainobjstore;

int classsize[]={sizeof(int),sizeof(char),sizeof(short), sizeof(void *)};	

int main() 
{
//	test2();
//	test3();
//	test4();
	test5();
//	test5a();
//	test2a();
//	test2b();
//	test7();

}

int test1(void) {

	transrecord_t *record;
	objheader_t *h1,*h2,*h3,*h4,*h5, *h6;

	dstmInit();
	record = transStart();
	printf("DEBUG -> Init done\n");
	h1 = transRead(record, 1);
	printf("oid = %d\tsize = %d\n", h1->oid,classsize[h1->type]);
	h3 = transRead(record, 3);
	printf("oid = %d\tsize = %d\n", h3->oid,classsize[h3->type]);
	h4 = transRead(record, 4);
	printf("oid = %d\tsize = %d\n", h4->oid,classsize[h4->type]);
	h2 = transRead(record, 2);
	printf("oid = %d\tsize = %d\n", h2->oid,classsize[h2->type]);
	h4 = transRead(record, 4);
	printf("oid = %d\tsize = %d\n", h4->oid,classsize[h4->type]);
	h3 = transRead(record, 3);
	printf("oid = %d\tsize = %d\n", h3->oid,classsize[h3->type]);
	h5 = transRead(record, 5);
	printf("oid = %d\tsize = %d\n", h5->oid,classsize[h5->type]);
}

int test2(void) {

	transrecord_t *record;
	objheader_t *h1,*h2,*h3,*h4,*h5, *h6;

	dstmInit();
	record = transStart();

	lhashInsert(1,1);
	lhashInsert(2,1);
	lhashInsert(3,1);
	lhashInsert(4,1);
	lhashInsert(5,1);
	lhashInsert(6,1);
	printf("DEBUG -> Init done\n");
	h1 = transRead(record, 1);
	lhashInsert(h1->oid, 1);
	h2 = transRead(record, 2);
	lhashInsert(h2->oid, 1);
	h3 = transRead(record, 3);
	lhashInsert(h3->oid, 1);
	h4 = transRead(record, 4);
	lhashInsert(h4->oid, 1);
	h5 = transRead(record, 5);
	lhashInsert(h5->oid, 1);
	h6 = transRead(record, 6);
	lhashInsert(h6->oid, 1);
	
	transCommit(record);

	return 0;
}

//Read objects when objects are found in remote location
int test2a(void) {
	 unsigned int val, mid;
	 transrecord_t *myTrans;
	 unsigned int size;
	 objheader_t *header, *h1, *h2;
	 pthread_t thread_Listen;
	 pthread_attr_t attr;

	 dstmInit();
	 pthread_attr_init(&attr);
	 pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_JOINABLE);

	 //Create and Insert Oid 20
	 size = sizeof(objheader_t) + classsize[2] ;
	 header = (objheader_t *) objstrAlloc(mainobjstore, size);
	 header->oid = 20;
	 header->type = 2;
	 header->version = 1;
	 header->rcount = 0; //? not sure how to handle this yet
	 header->status = 0;
	 header->status |= NEW;
	 mhashInsert(header->oid, header);
	 mid = iptoMid("128.200.9.29");
	 lhashInsert(header->oid, mid);

	 //Create and Insert Oid 21
	 size = sizeof(objheader_t) + classsize[1] ;
	 header = (objheader_t *) objstrAlloc(mainobjstore, size);
	 header->oid = 21;
	 header->type = 1;
	 header->version = 1;
	 header->rcount = 0; //? not sure how to handle this yet
	 header->status = 0;
	 header->status |= NEW;
	 mhashInsert(header->oid, header);
	 mid = iptoMid("128.200.9.29");
	 lhashInsert(header->oid, mid);

	 //Create and Insert Oid 22
	 size = sizeof(objheader_t) + classsize[3] ;
	 header = (objheader_t *) objstrAlloc(mainobjstore, size);
	 header->oid = 22;
	 header->type = 3;
	 header->version = 1;
	 header->rcount = 0; //? not sure how to handle this yet
	 header->status = 0;
	 header->status |= NEW;
	 mhashInsert(header->oid, header);
	 mid = iptoMid("128.200.9.29");
	 lhashInsert(header->oid, mid);

	 //Inserting into lhashtable
	 mid = iptoMid("128.200.9.30"); //d-4.eecs.uci.edu
	 lhashInsert(31, mid);
	 lhashInsert(32, mid);

	 mid = iptoMid("128.200.9.10"); //demsky.eecs.uci.edu
	 //Inserting into lhashtable
	 lhashInsert(1, mid);
	 lhashInsert(2, mid);
	 lhashInsert(3, mid);
	 lhashInsert(4, mid);

	 pthread_create(&thread_Listen, &attr, dstmListen, NULL);

	 //Check if machine demsky is up and running
	 checkServer(mid, "128.200.9.10");
	 mid = iptoMid("128.200.9.30");
	 //Check if machine d-4 is up and running
	 checkServer(mid, "128.200.9.30");

	 // Start Transaction    
	 myTrans = transStart();

	 //read object 1
	 if((h1 = transRead(myTrans, 1)) == NULL){
		 printf("Object not found\n");
	 }
	 //read object 2
	 if((h2 = transRead(myTrans, 2)) == NULL) {
		 printf("Object not found\n");
	 }

	 pthread_join(thread_Listen, NULL);
	 return 0;
}

//Read objects that are both remote and local and are available on machines
int test2b(void) {

	unsigned int val, mid;
	transrecord_t *myTrans;
	unsigned int size;
	objheader_t *header, *h1, *h2, *h3, *h4;
	pthread_t thread_Listen;
	pthread_attr_t attr;

	dstmInit();
	pthread_attr_init(&attr);
	pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_JOINABLE);

	//Create and Insert Oid 20
	size = sizeof(objheader_t) + classsize[2] ;
	header = (objheader_t *) objstrAlloc(mainobjstore, size);
	header->oid = 20;
	header->type = 2;
	header->version = 1;
	header->rcount = 0; //? not sure how to handle this yet
	header->status = 0;
	header->status |= NEW;
	mhashInsert(header->oid, header);
	mid = iptoMid("128.200.9.29");
	lhashInsert(header->oid, mid);

	//Create and Insert Oid 21
	size = sizeof(objheader_t) + classsize[1] ;
	header = (objheader_t *) objstrAlloc(mainobjstore, size);
	header->oid = 21;
	header->type = 1;
	header->version = 1;
	header->rcount = 0; //? not sure how to handle this yet
	header->status = 0;
	header->status |= NEW;
	mhashInsert(header->oid, header);
	mid = iptoMid("128.200.9.29");
	lhashInsert(header->oid, mid);

	//Create and Insert Oid 22
	size = sizeof(objheader_t) + classsize[3] ;
	header = (objheader_t *) objstrAlloc(mainobjstore, size);
	header->oid = 22;
	header->type = 3;
	header->version = 1;
	header->rcount = 0; //? not sure how to handle this yet
	header->status = 0;
	header->status |= NEW;
	mhashInsert(header->oid, header);
	mid = iptoMid("128.200.9.29");
	lhashInsert(header->oid, mid);

	//Inserting into lhashtable
	mid = iptoMid("128.200.9.30"); //d-4.eecs.uci.edu
	lhashInsert(31, mid);
	lhashInsert(32, mid);
	lhashInsert(33, mid);

	mid = iptoMid("128.200.9.10"); //demsky.eecs.uci.edu
	//Inserting into lhashtable
	lhashInsert(1, mid);
	lhashInsert(2, mid);
	lhashInsert(3, mid);
	lhashInsert(4, mid);

	pthread_create(&thread_Listen, &attr, dstmListen, NULL);

	//Check if machine demsky is up and running
	checkServer(mid, "128.200.9.10");
	mid = iptoMid("128.200.9.30");
	//Check if machine d-4 is up and running
	checkServer(mid, "128.200.9.30");

	// Start Transaction    
	myTrans = transStart();

	//read object 1 (found on demksy)
	if((h1 = transRead(myTrans, 1)) == NULL){
		printf("Object not found\n");
	}
	//read object 2 (found on demsky)
	if((h2 = transRead(myTrans, 2)) == NULL) {
		printf("Object not found\n");
	}
	
	//read object 21 (found on local)
	if((h3 = transRead(myTrans, 21)) == NULL) {
		printf("Object not found\n");
	}
	
	//read object 32 (found on d-4)
	if((h4 = transRead(myTrans, 32)) == NULL) {
		printf("Object not found\n");
	}

	pthread_join(thread_Listen, NULL);
	return 0;

}


//Read objects when objects are not found in  any participant 
int test3(void){
	transrecord_t *record;
	objheader_t *h1,*h2;

	dstmInit();
	record = transStart();
	printf("DEBUG -> Init done\n");
	//read object 11
	if((h1 = transRead(record, 11)) == NULL){
		printf("Object not found\n");
	}
	//read object 12
	if((h2 = transRead(record, 12)) == NULL) {
		printf("Object not found\n");
	}
	transCommit(record);

	return 0;
}

//Read objects when some objects are found and other objects not found in  any participant 
int test4(void) {
	transrecord_t *record;
	objheader_t *h1,*h2, *h3, *h4;

	dstmInit();
	record = transStart();
	printf("DEBUG -> Init done\n");
	//read object 1
	if((h1 = transRead(record, 1)) == NULL){
		printf("Object not found\n");
	}
	//read object 2
	if((h2 = transRead(record, 2)) == NULL) {
		printf("Object not found\n");
	}
	//read object 11
	if((h3 = transRead(record, 11)) == NULL) {
		printf("Object not found\n");
	}
	//read object 13
	if((h4 = transRead(record, 13)) == NULL) {
		printf("Object not found\n");
	}
	if((h1 != NULL) && (h2 != NULL) && (h3 != NULL) && h4 !=NULL) { 
		transCommit(record);
	}else {
		printf("Cannot complete this transaction\n");
	}

	return 0;
}

//Commit for transaction objects when the objs are part of other 
//transactions running simultaneously
int test5(void) {
	unsigned int val, mid;
	transrecord_t *myTrans;
	unsigned int size;
	objheader_t *header, *h1, *h2, *h3, *h4, *h5, *h6;
	pthread_t thread_Listen;
	pthread_attr_t attr;

	dstmInit();
	pthread_attr_init(&attr);
	pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_JOINABLE);

	//Create and Insert Oid 20
	size = sizeof(objheader_t) + classsize[2] ;
	header = (objheader_t *) objstrAlloc(mainobjstore, size);
	header->oid = 20;
	header->type = 2;
	header->version = 1;
	header->rcount = 0; //? not sure how to handle this yet
	header->status = 0;
	header->status |= NEW;
	mhashInsert(header->oid, header);
	mid = iptoMid("128.200.9.29");
	lhashInsert(header->oid, mid);

	//Create and Insert Oid 21
	size = sizeof(objheader_t) + classsize[1] ;
	header = (objheader_t *) objstrAlloc(mainobjstore, size);
	header->oid = 21;
	header->type = 1;
	header->version = 1;
	header->rcount = 0; //? not sure how to handle this yet
	header->status = 0;
	header->status |= NEW;
	mhashInsert(header->oid, header);
	mid = iptoMid("128.200.9.29");
	lhashInsert(header->oid, mid);

	//Create and Insert Oid 22
	size = sizeof(objheader_t) + classsize[3] ;
	header = (objheader_t *) objstrAlloc(mainobjstore, size);
	header->oid = 22;
	header->type = 3;
	header->version = 1;
	header->rcount = 0; //? not sure how to handle this yet
	header->status = 0;
	header->status |= NEW;
	mhashInsert(header->oid, header);
	mid = iptoMid("128.200.9.29");
	lhashInsert(header->oid, mid);

	//Inserting into lhashtable
	mid = iptoMid("128.200.9.30"); //d-4.eecs.uci.edu
	lhashInsert(31, mid);
	lhashInsert(32, mid);
	lhashInsert(33, mid);

	mid = iptoMid("128.200.9.10"); //demsky.eecs.uci.edu
	//Inserting into lhashtable
	lhashInsert(1, mid);
	lhashInsert(2, mid);
	lhashInsert(3, mid);
	lhashInsert(4, mid);

	pthread_create(&thread_Listen, &attr, dstmListen, NULL);
	//Check if machine demsky is up and running
	checkServer(mid, "128.200.9.10");
	mid = iptoMid("128.200.9.30");
	//Check if machine d-4 is up and running
	checkServer(mid, "128.200.9.30");

	// Start Transaction    
	myTrans = transStart();

	//read object 1 (found on demksy)
	if((h1 = transRead(myTrans, 1)) == NULL){
		printf("Object not found\n");
	}
	//read object 2 (found on demksy)
	if((h1 = transRead(myTrans,2)) == NULL){
		printf("Object not found\n");
	}
	//read object 31 (found on d-4)
	if((h2 = transRead(myTrans, 31)) == NULL) {
		printf("Object not found\n");
	}
	
	//Commit transaction
	transCommit(myTrans);
	
	pthread_join(thread_Listen, NULL);
	return 0;
}
int test5a(void) {
	unsigned int val, mid;
	transrecord_t *myTrans;
	unsigned int size;
	objheader_t *header, *h1, *h2, *h3;
	pthread_t thread_Listen;
	pthread_attr_t attr;

	dstmInit();
	pthread_attr_init(&attr);
	pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_JOINABLE);

	//Create and Insert Oid 20
	size = sizeof(objheader_t) + classsize[2] ;
	header = (objheader_t *) objstrAlloc(mainobjstore, size);
	header->oid = 20;
	header->type = 2;
	header->version = 1;
	header->rcount = 0; //? not sure how to handle this yet
	header->status = 0;
	header->status |= NEW;
	mhashInsert(header->oid, header);
	mid = iptoMid("128.200.9.29");
	lhashInsert(header->oid, mid);

	//Create and Insert Oid 21
	size = sizeof(objheader_t) + classsize[1] ;
	header = (objheader_t *) objstrAlloc(mainobjstore, size);
	header->oid = 21;
	header->type = 1;
	//read object 31 (found on d-4)
	if((h2 = transRead(myTrans, 31)) == NULL) {
		printf("Object not found\n");
	}
	
	//Commit transaction
	transCommit(myTrans);
	
	pthread_join(thread_Listen, NULL);
	return 0;
}
int test5b(void) {
	unsigned int val, mid;
	transrecord_t *myTrans;
	unsigned int size;
	objheader_t *header, *h1, *h2, *h3;
	pthread_t thread_Listen;
	pthread_attr_t attr;

	dstmInit();
	pthread_attr_init(&attr);
	pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_JOINABLE);

	//Create and Insert Oid 20
	size = sizeof(objheader_t) + classsize[2] ;
	header = (objheader_t *) objstrAlloc(mainobjstore, size);
	header->oid = 20;
	header->type = 2;
	header->version = 1;
	header->rcount = 0; //? not sure how to handle this yet
	header->status = 0;
	header->status |= NEW;
	mhashInsert(header->oid, header);
	mid = iptoMid("128.200.9.29");
	lhashInsert(header->oid, mid);

	//Create and Insert Oid 21
	size = sizeof(objheader_t) + classsize[1] ;
	header = (objheader_t *) objstrAlloc(mainobjstore, size);
	header->oid = 21;
	header->type = 1;
	header->version = 1;
	header->rcount = 0; //? not sure how to handle this yet
	header->status = 0;
	header->status |= NEW;
	mhashInsert(header->oid, header);
	mid = iptoMid("128.200.9.29");
	lhashInsert(header->oid, mid);

	//Create and Insert Oid 22
	size = sizeof(objheader_t) + classsize[3] ;
	header = (objheader_t *) objstrAlloc(mainobjstore, size);
	header->oid = 22;
	header->type = 3;
	header->version = 1;
	header->rcount = 0; //? not sure how to handle this yet
	header->status = 0;
	header->status |= NEW;
	mhashInsert(header->oid, header);
	mid = iptoMid("128.200.9.29");
	lhashInsert(header->oid, mid);

	//Inserting into lhashtable
	mid = iptoMid("128.200.9.30"); //d-4.eecs.uci.edu
	lhashInsert(31, mid);
	lhashInsert(32, mid);
	lhashInsert(33, mid);

	mid = iptoMid("128.200.9.10"); //demsky.eecs.uci.edu
	//Inserting into lhashtable
	lhashInsert(1, mid);
	lhashInsert(2, mid);
	lhashInsert(3, mid);
	lhashInsert(4, mid);

	pthread_create(&thread_Listen, &attr, dstmListen, NULL);

	//Check if machine demsky is up and running
	checkServer(mid, "128.200.9.10");
	mid = iptoMid("128.200.9.30");
	//Check if machine d-4 is up and running
	checkServer(mid, "128.200.9.30");

	// Start Transaction    
	myTrans = transStart();

	//read object 1 (found on demksy)
	if((h1 = transRead(myTrans, 1)) == NULL){
		printf("Object not found\n");
	}
	//read object 31 (found on d-4)
	if((h2 = transRead(myTrans, 31)) == NULL) {
		printf("Object not found\n");
	}
	
	//read object 22 (found locally)
	if((h3 = transRead(myTrans, 22)) == NULL) {
		printf("Object not found\n");
	}
	
	//Commit transaction
	if((h1 != NULL) && (h2 != NULL) && (h3 != NULL))
		transCommit(myTrans);
	else
		printf("Cannot complete this transaction \n");

	pthread_join(thread_Listen, NULL);
	return 0;
}
//Commit transactions on local and remote objects that are NOT a part of 
//any other transaction
int test7(void) {
	unsigned int val, mid;
	transrecord_t *myTrans;
	unsigned int size;
	objheader_t *header, *h1, *h2, *h3;
	pthread_t thread_Listen;
	pthread_attr_t attr;

	dstmInit();
	pthread_attr_init(&attr);
	pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_JOINABLE);

	//Create and Insert Oid 20
	size = sizeof(objheader_t) + classsize[2] ;
	header = (objheader_t *) objstrAlloc(mainobjstore, size);
	header->oid = 20;
	header->type = 2;
	header->version = 1;
	header->rcount = 0; //? not sure how to handle this yet
	header->status = 0;
	header->status |= NEW;
	mhashInsert(header->oid, header);
	mid = iptoMid("128.200.9.29");
	lhashInsert(header->oid, mid);

	//Create and Insert Oid 21
	size = sizeof(objheader_t) + classsize[1] ;
	header = (objheader_t *) objstrAlloc(mainobjstore, size);
	header->oid = 21;
	header->type = 1;
	header->version = 1;
	header->rcount = 0; //? not sure how to handle this yet
	header->status = 0;
	header->status |= NEW;
	mhashInsert(header->oid, header);
	mid = iptoMid("128.200.9.29");
	lhashInsert(header->oid, mid);

	//Create and Insert Oid 22
	size = sizeof(objheader_t) + classsize[3] ;
	header = (objheader_t *) objstrAlloc(mainobjstore, size);
	header->oid = 22;
	header->type = 3;
	header->version = 1;
	header->rcount = 0; //? not sure how to handle this yet
	header->status = 0;
	header->status |= NEW;
	mhashInsert(header->oid, header);
	mid = iptoMid("128.200.9.29");
	lhashInsert(header->oid, mid);

	//Inserting into lhashtable
	mid = iptoMid("128.200.9.30"); //d-4.eecs.uci.edu
	lhashInsert(31, mid);
	lhashInsert(32, mid);
	lhashInsert(33, mid);

	mid = iptoMid("128.200.9.10"); //demsky.eecs.uci.edu
	//Inserting into lhashtable
	lhashInsert(1, mid);
	lhashInsert(2, mid);
	lhashInsert(3, mid);
	lhashInsert(4, mid);

	pthread_create(&thread_Listen, &attr, dstmListen, NULL);

	//Check if machine demsky is up and running
	checkServer(mid, "128.200.9.10");
	mid = iptoMid("128.200.9.30");
	//Check if machine d-4 is up and running
	checkServer(mid, "128.200.9.30");

	// Start Transaction    
	myTrans = transStart();

	//read object 3 (found on demksy)
	if((h1 = transRead(myTrans, 3)) == NULL){
		printf("Object not found\n");
	}
	//read object 32 (found on d-4)
	if((h2 = transRead(myTrans, 32)) == NULL) {
		printf("Object not found\n");
	}
	
	//read object 22 (found locally)
	if((h3 = transRead(myTrans, 22)) == NULL) {
		printf("Object not found\n");
	}
	
	//Commit transaction
	transCommit(myTrans);

	pthread_join(thread_Listen, NULL);
	return 0;
}
