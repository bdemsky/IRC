#ifndef _DSTM_H_
#define _DSTM_H_

#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include "clookup.h"

#define DEFAULT_OBJ_STORE_SIZE 1048510 //1MB
//bit designations for status field of objheader
#define DIRTY 0x01
#define NEW   0x02

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

#endif
