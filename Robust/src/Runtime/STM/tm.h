#ifndef _TM_H_
#define _TM_H_

/* ==================
 * Control Messages
 * ==================
 */
#define TRANS_AGREE         10
#define TRANS_DISAGREE      11
#define TRANS_SOFT_ABORT    12
#define TRANS_ABORT         13
#define TRANS_COMMIT        14
#define READ_OBJ            15
#define THREAD_NOTIFY       16
#define THREAD_RESPONSE     17


/* ========================
 * Library header files
 * ========================
 */
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <pthread.h>
#include <sys/time.h>
#include <errno.h>
#include "threadnotify.h"
#include "clookup.h"
#include "dsmlock.h"

/* ==================================
 * Bit designation for status field
 * of object header 
 * ==================================
 */
#define DIRTY 0x01
#define NEW   0x02
#define LOCK  0x04

#ifdef COMPILER
#include "structdefs.h"
typedef struct objheader {
  unsigned int version;
  unsigned int lock;
} objheader_t;

#define OID(x) \
  (*((unsigned int *)&((struct ___Object___ *)((unsigned int) x + sizeof(objheader_t)))->___objlocation___))

#define COMPOID(x) \
  ((void*)((((void *) x )!=NULL) ? (*((unsigned int *)&((struct ___Object___ *) x)->___objlocation___)) : 0))

#define STATUS(x) \
  *((unsigned int *) &(((struct ___Object___ *)((unsigned int) x + sizeof(objheader_t)))->___objstatus___))

#define STATUSPTR(x) \
  ((unsigned int *) &(((struct ___Object___ *)((unsigned int) x + sizeof(objheader_t)))->___objstatus___))

#define TYPE(x) \
  ((struct ___Object___ *)((unsigned int) x + sizeof(objheader_t)))->type

#define GETSIZE(size, x) { \
    int type=TYPE(x); \
    if (type<NUMCLASSES) { \
      size=classsize[type]; \
    } else { \
      size=classsize[type]*((struct ArrayObject *)&((objheader_t *)x)[1])->___length___+sizeof(struct ArrayObject); \
    } \
}

#else
#define OID(x) x->oid
#define TYPE(x) x->type
#define STATUS(x) x->status
#define STATUSPTR(x) &x->status
#define GETSIZE(size, x) size=classsize[TYPE(x)]
#endif


/* ================================
 * Constants
 * ================================
 */
#define DEFAULT_OBJ_STORE_SIZE 1048510 //1MB
#define OSUSED(x) (((unsigned int)(x)->top)-((unsigned int) (x+1)))
#define OSFREE(x) ((x)->size-OSUSED(x))
#define TRANSREAD(x,y) { \
  unsigned int inputvalue;\
if ((inputvalue=(unsigned int)y)==0) x=NULL;\
else { \
chashlistnode_t * cnodetmp=&c_table[(inputvalue&c_mask)>>1];	\
do { \
  if (cnodetmp->key==inputvalue) {x=(void *)cnodetmp->val;break;} \
cnodetmp=cnodetmp->next;\
 if (cnodetmp==NULL) {x=transRead((void *)inputvalue); asm volatile("":"=m"(c_table),"=m"(c_mask));break;} \
} while(1);\
}}

/* =================================
 * Data structures 
 * =================================
 */
typedef struct objstr {
  unsigned int size;       //this many bytes are allocated after this header
  void *top;
  struct objstr *next;
  struct objstr *prev;
} objstr_t;

typedef struct newObjCreated {
  unsigned int numcreated;
  unsigned int *oidcreated;
} newObjCreated_t;


#ifdef TRANSSTATS
/***********************************
 * Global Variables for statistics
 **********************************/
extern int numTransCommit;
extern int numTransAbort;
extern int nSoftAbort;
#endif

/* ================================
 * Functions used
 * ================================
 */
int stmStartup();
void objstrDelete(objstr_t *store);
objstr_t *objstrCreate(unsigned int size);
void transStart();
objheader_t *transCreateObj(void * ptr, unsigned int size);
unsigned int getNewOID(void);
void *objstrAlloc(objstr_t **osptr, unsigned int size);
__attribute__((pure)) void *transRead(void * oid);
int transCommit();
int traverseCache();
int decideResponse(objheader_t *, unsigned int *, int *, unsigned int*, int *);
int transAbortProcess(unsigned int *, int *, unsigned int *, int *);
int transCommmitProcess(unsigned int *, int *, unsigned int *, int *);
void randomdelay();

#endif
