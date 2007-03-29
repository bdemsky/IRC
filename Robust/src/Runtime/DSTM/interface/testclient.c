#include<stdio.h>
#include "dstm.h"

int classsize[]={sizeof(int),sizeof(char),sizeof(short), sizeof(void *)};	

int test1(void);
int test2(void);

int main() 
{
	test2();
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
	printf("DEBUG -> Init done\n");
	h1 = transRead(record, 1);
//	printf("oid = %d\tsize = %d\n", h1->oid,classsize[h1->type]);
	h2 = transRead(record, 2);
//	printf("oid = %d\tsize = %d\n", h2->oid,classsize[h2->type]);
	h3 = transRead(record, 3);
//	printf("oid = %d\tsize = %d\n", h3->oid,classsize[h3->type]);
	h4 = transRead(record, 4);
//	printf("oid = %d\tsize = %d\n", h4->oid,classsize[h4->type]);
	h4->status |= DIRTY;
	h5 = transRead(record, 5);
//	printf("oid = %d\tsize = %d\n", h5->oid,classsize[h5->type]);
	h6 = transRead(record, 6);
//	printf("oid = %d\tsize = %d\n", h6->oid,classsize[h6->type]);
	h6->status |= DIRTY;
	transCommit(record);
}


