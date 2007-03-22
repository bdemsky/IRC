#ifndef _DSTM_H_
#define _DSTM_H_

//Client Messages
#define READ_REQUEST 		1
#define READ_MULT_REQUEST 	2
#define MOVE_REQUEST 		3
#define MOVE_MULT_REQUEST	4
#define	TRANS_REQUEST		5
#define	TRANS_ABORT		6
#define TRANS_COMMIT 		7

//Server Messages
#define OBJECT_FOUND		8
#define OBJECT_NOT_FOUND	9
#define OBJECTS_FOUND 		10
#define OBJECTS_NOT_FOUND	11
#define TRANS_AGREE 		12
#define TRANS_DISAGREE		13//for soft abort
#define TRANS_DISAGREE_ABORT	14//for hard abort
#define TRANS_SUCESSFUL		15//Not necessary for now

#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include "clookup.h"

#define DEFAULT_OBJ_STORE_SIZE 1048510 //1MB
//bit designations for status field of objheader
#define DIRTY 0x01
#define NEW   0x02
#define LOCK  0x04

typedef struct objheader {
	unsigned int oid;
	unsigned short type;
	unsigned short version;
	unsigned short rcount;
	char status;
} objheader_t;

typedef struct objstr {
	unsigned int size; //this many bytes are allocated after this header
	void *top;
	struct objstr *next;
} objstr_t;

typedef struct transrecord {
	objstr_t *cache;
	chashtable_t *lookupTable;
} transrecord_t;

typedef struct pile {
	unsigned int mid;
	unsigned int oid;
	struct pile *next;
}pile_t;

/* Initialize main object store and lookup tables, start server thread. */
int dstmInit(void);

/* Prototypes for object header */
unsigned int getNewOID(void);
unsigned int objSize(objheader_t *object);
/* end object header */

/* Prototypes for object store */
objstr_t *objstrCreate(unsigned int size); //size in bytes
void objstrDelete(objstr_t *store); //traverse and free entire list
void *objstrAlloc(objstr_t *store, unsigned int size); //size in bytes
/* end object store */

/* Prototypes for server portion */
void *dstmListen();
void *dstmAccept(void *);
/* end server portion */

/* Prototypes for transactions */
transrecord_t *transStart();
objheader_t *transRead(transrecord_t *record, unsigned int oid);
objheader_t *transCreateObj(transrecord_t *record, unsigned short type); //returns oid
int transCommit(transrecord_t *record); //return 0 if successful
/* end transactions */

void *getRemoteObj(transrecord_t *, unsigned int, unsigned int);

#endif
