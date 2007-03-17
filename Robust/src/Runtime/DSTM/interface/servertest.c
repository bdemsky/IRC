#include <pthread.h>
#include "dstm.h"

extern objstr_t *mainobjstore;
int classsize[]={sizeof(int),sizeof(char),sizeof(short), sizeof(void *)};

unsigned int createObjects(transrecord_t *record, unsigned short type) {
	objheader_t *header, *tmp;
	unsigned int size;
	size = sizeof(objheader_t) + classsize[type] ;
	header = transCreateObj(record, type);
	tmp = (objheader_t *) objstrAlloc(mainobjstore, size);
	memcpy(tmp, header, size);
	mhashInsert(tmp->oid, tmp);
	lhashInsert(tmp->oid, 1);
	return 0;
}

int main()
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
	pthread_join(thread_Listen, NULL);
	return 0;
}
