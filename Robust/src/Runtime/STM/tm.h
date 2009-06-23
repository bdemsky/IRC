#ifndef _TM_H_
#define _TM_H_
#include "runtime.h"
/* ==================
 * Control Messages
 * ==================
 */
#define TRANS_SOFT_ABORT    12
#define TRANS_ABORT         13
#define TRANS_COMMIT        14


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
#include "stmlookup.h"
#include "stmlock.h"

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

typedef struct threadrec {
  int blocked;
} threadrec_t;

typedef struct objheader {
  unsigned int version;
  unsigned int lock;          /* reader and writer lock for object header */
#ifdef STMSTATS
  int abortCount;             /* track how many times does this object cause abort */
  int accessCount;            /* track how many times is this object accessed */
  threadrec_t *trec;           /* some thread that locked this object */
  int riskyflag;              /* track how risky is the object */
  pthread_mutex_t *objlock;    /* lock this object */
  int padding;
#endif
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
#define MAXABORTS 2
#define NEED_LOCK_THRESHOLD 0.020000
#define OSUSED(x) (((unsigned INTPTR)(x)->top)-((unsigned INTPTR) (x+1)))
#define OSFREE(x) ((x)->size-OSUSED(x))
#define TRANSREAD(x,y,z) { \
    void * inputvalue; \
    if ((inputvalue=y)==NULL) x=NULL;\
         else { \
           chashlistnode_t * cnodetmp=&c_table[(((unsigned INTPTR)inputvalue)&c_mask)>>4]; \
           do { \
             if (cnodetmp->key==inputvalue) {x=cnodetmp->val; break;} \
             cnodetmp=cnodetmp->next; \
             if (cnodetmp==NULL) {if (((struct ___Object___*)inputvalue)->___objstatus___&NEW) {x=inputvalue; break;} else \
                                  {x=transRead(inputvalue,z); asm volatile ("" : "=m" (c_table),"=m" (c_mask)); break;}} \
	   } while(1); \
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
#ifdef STMSTATS
typedef struct objlockstate {
  int offset;
  pthread_mutex_t lock[MAXOBJLIST];
  struct objlockstate *next;
} objlockstate_t;
extern __thread threadrec_t *trec;
extern __thread struct objlist * lockedobjs;
extern objlockstate_t *objlockscope;
pthread_mutex_t lockedobjstore;
#endif


/***********************************
 * Global Variables for statistics
 **********************************/
#ifdef TRANSSTATS
extern int numTransCommit;
extern int numTransAbort;
extern int nSoftAbort;
extern int nSoftAbortAbort;
extern int nSoftAbortCommit;
#endif

#ifdef STMSTATS
extern int typesCausingAbort[];
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
__attribute__((pure)) void *transRead(void *, void *);
#ifdef DELAYCOMP
int transCommit(void (*commitmethod)(void *, void *, void *), void * primitives, void * locals, void * params);
int traverseCache(void (*commitmethod)(void *, void *, void *), void * primitives, void * locals, void * params);
int alttraverseCache(void (*commitmethod)(void *, void *, void *), void * primitives, void * locals, void * params);
void transCommitProcess(void **, int, int, void (*commitmethod)(void *, void *, void *), void * primitives, void * locals, void * params);
#else
int transCommit();
int traverseCache();
int alttraverseCache();
void transCommitProcess(void **, int);
#endif
int altalttraverseCache();
void transAbortProcess(void **, int);
void randomdelay(int);
#if defined(STMSTATS)||defined(SOFTABORT)
int getTotalAbortCount(int, int, void *, void *, int);
#endif
#ifdef STMSTATS
objheader_t * needLock(objheader_t *, void *);
#endif
#endif
