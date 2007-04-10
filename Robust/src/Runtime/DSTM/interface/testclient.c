#include<stdio.h>
#include "dstm.h"
#include "llookup.h"
#include "ip.h"
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>

extern objstr_t *mainobjstore;
//extern lhashtable_t llookup;		//Global Hash table
//extern mhashtable_t mlookup;		//Global Hash table

int classsize[]={sizeof(int),sizeof(char),sizeof(short), sizeof(void *)};	

int test1(void);
int test2(void);

unsigned int createObjects(transrecord_t *record) {
	objheader_t *header, *tmp;
	struct sockaddr_in antelope;
	unsigned int size, mid;
	int i = 0;
	for(i = 20 ; i< 23; i++) {
		size = sizeof(objheader_t) + classsize[i-20] ;
		tmp = (objheader_t *)objstrAlloc(record->cache, size);
		tmp->oid = i;
		tmp->type = (i-20);
		tmp->version = 1;
		tmp->rcount = 0; //? not sure how to handle this yet
		tmp->status = 0;
		tmp->status |= NEW;
		chashInsert(record->lookupTable, tmp->oid, tmp);
		header = (objheader_t *) objstrAlloc(mainobjstore, size);
		memcpy(header, tmp, size);
		mhashInsert(header->oid, header);
		//inet_aton("127.0.0.1", &antelope.sin_addr); // store IP in antelope
	        //mid = iptoMid(inet_ntoa(antelope.sin_addr));
		mid = iptoMid("127.0.0.1");
		lhashInsert(header->oid, mid);
	//	lhashInsert(header->oid, 1);
	}
	//      printf("Insert oid = %d at address %x\n",tmp->oid, tmp);
	size = sizeof(objheader_t) + classsize[0] ;
	header = (objheader_t *) objstrAlloc(mainobjstore, size);
	header->oid = 30;
	header->type = 0;
	header->version = 1;
	header->rcount = 0; //? not sure how to handle this yet
	header->status = 0;
	header->status |= NEW;
	mhashInsert(header->oid, header);
	//inet_aton("127.0.0.1", &antelope.sin_addr); // store IP in antelope
	//mid = iptoMid(inet_ntoa(antelope.sin_addr));
	mid = iptoMid("127.0.0.1");
	lhashInsert(header->oid, mid);
	size = sizeof(objheader_t) + classsize[1] ;
	header = (objheader_t *) objstrAlloc(mainobjstore, size);
	header->oid = 28;
	header->type = 1;
	header->version = 1;
	header->rcount = 0; //? not sure how to handle this yet
	header->status = 0;
	header->status |= LOCK;
	mhashInsert(header->oid, header);
	//inet_aton("127.0.0.1", &antelope.sin_addr); // store IP in antelope
	//mid = iptoMid(inet_ntoa(antelope.sin_addr));
	mid = iptoMid("127.0.0.1");
	lhashInsert(header->oid, mid);
	size = sizeof(objheader_t) + classsize[2] ;
	header = (objheader_t *) objstrAlloc(mainobjstore, size);
	header->oid = 29;
	header->type = 2;
	header->version = 1;
	header->rcount = 0; //? not sure how to handle this yet
	header->status = 0;
	header->status |= LOCK;
	mhashInsert(header->oid, header);
	//inet_aton("127.0.0.1", &antelope.sin_addr); // store IP in antelope
	//mid = iptoMid(inet_ntoa(antelope.sin_addr));
	mid = iptoMid("127.0.0.1");
	lhashInsert(header->oid, mid);
	return 0;
}

int main() 
{
//	test2();
//	test3();
//	test4();
	test5();
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
//	getRemoteObj(&record, 0,1);
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
//	h4->status |= DIRTY;
	h5 = transRead(record, 5);
	lhashInsert(h5->oid, 1);
	h6 = transRead(record, 6);
	lhashInsert(h6->oid, 1);
//	h6->status |= DIRTY;
	
	transCommit(record);

	return 0;
}
//Test Read objects when objects are not found in  any participant 
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

//Test Read objects when some objects are found and other objects not found in  any participant 
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

//Test for transaction objects when the objs are part of the Coordinator machine starting the 
//trans commit 
int test5(void) {
	transrecord_t *record;
	unsigned int mid;
	objheader_t *h1,*h2, *h3, *h4;

	dstmInit();
	record = transStart();
	printf("DEBUG -> Init done\n");
	mid = iptoMid("127.0.0.1");	
	lhashInsert(1,mid);
	lhashInsert(2,mid);
	lhashInsert(3,mid);
	lhashInsert(4,mid);
	lhashInsert(5,mid);
	lhashInsert(6,mid);
	createObjects(record);
	//read object 1
	if((h1 = transRead(record, 1)) == NULL){
		printf("Object not found\n");
	}
	//read object 5
	if((h2 = transRead(record, 5)) == NULL) {
		printf("Object not found\n");
	}
	//read object 20(present in local machine)
	if((h3 = transRead(record, 20)) == NULL) {
		printf("Object not found\n");
	}
	//read object 21(present in local machine)
	if((h4 = transRead(record, 21)) == NULL) {
		printf("Object not found\n");
	}
	
	transCommit(record);
}
