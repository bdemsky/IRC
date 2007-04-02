#include <pthread.h>
#include "dstm.h"

extern objstr_t *mainobjstore;
int classsize[]={sizeof(int),sizeof(char),sizeof(short), sizeof(void *)};

int test1(void);
int test2(void);

unsigned int createObjects(transrecord_t *record, unsigned short type) {
	objheader_t *header, *tmp;
	unsigned int size;
	size = sizeof(objheader_t) + classsize[type] ;
	header = transCreateObj(record, type);
	tmp = (objheader_t *) objstrAlloc(mainobjstore, size);
	memcpy(tmp, header, size);
//	printf("Insert oid = %d at address %x\n",tmp->oid, tmp);
	mhashInsert(tmp->oid, tmp);
	lhashInsert(tmp->oid, 1);
	//Lock oid 3 object
//	if(tmp->oid == 3)
//		tmp->status |= LOCK;
	return 0;
}

int main()
{
	test2();
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


}
