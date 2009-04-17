#ifndef _TM_H_
#define _TM_H_
#include "runtime.h"
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
#include "stmlookup.h"
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
  (*((void **)&((struct ___Object___ *)(((char *) x) + sizeof(objheader_t)))->___objlocation___))

#define COMPOID(x) \
  ((((void *) x )!=NULL) ? (*((void **)&((struct ___Object___ *) x)->___objlocation___)) : NULL)

#define STATUS(x) \
  *((unsigned int *) &(((struct ___Object___ *)(((char *) x) + sizeof(objheader_t)))->___objstatus___))

#define STATUSPTR(x) \
  ((unsigned int *) &(((struct ___Object___ *)(((char *) x) + sizeof(objheader_t)))->___objstatus___))

#define TYPE(x) \
  ((struct ___Object___ *)((char *) x + sizeof(objheader_t)))->type

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
#define OSUSED(x) (((unsigned INTPTR)(x)->top)-((unsigned INTPTR) (x+1)))
#define OSFREE(x) ((x)->size-OSUSED(x))
#define TRANSREAD(x,y) { \
  void * inputvalue;\
if ((inputvalue=y)==NULL) x=NULL;\
else { \
chashlistnode_t * cnodetmp=&c_table[(((unsigned INTPTR)inputvalue)&c_mask)>>4];	\
do { \
  if (cnodetmp->key==inputvalue) {x=cnodetmp->val;break;} \
cnodetmp=cnodetmp->next;\
 if (cnodetmp==NULL) {if (((struct ___Object___*)inputvalue)->___objstatus___&NEW) {x=inputvalue;break;} else \
{x=transRead(inputvalue); asm volatile("":"=m"(c_table),"=m"(c_mask));break;}} \
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
} objstr_t;

#define MAXOBJLIST 512
struct objlist {
  int offset;
  void * objs[MAXOBJLIST];
  struct objlist * next;
};

extern __thread struct objlist * newobjs;
extern __thread objstr_t *t_cache;
extern __thread objstr_t *t_reserve;


#ifdef TRANSSTATS
/***********************************
 * Global Variables for statistics
 **********************************/
extern int numTransCommit;
extern int numTransAbort;
extern int nSoftAbort;
extern int nSoftAbortAbort;
extern int nSoftAbortCommit;
#endif

/* ================================
 * Functions used
 * ================================
 */
int stmStartup();
void objstrReset();
void objstrDelete(objstr_t *store);
objstr_t *objstrCreate(unsigned int size);
void transStart();
objheader_t *transCreateObj(void * ptr, unsigned int size);
unsigned int getNewOID(void);
void *objstrAlloc(unsigned int size);
__attribute__((pure)) void *transRead(void * oid);
int transCommit();
int traverseCache();
int alttraverseCache();
int transAbortProcess(void **, int *, void **, int *);
int transCommmitProcess(void **, int *, void **, int *);
void randomdelay(int);

#endif
