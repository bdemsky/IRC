#ifndef __MLP_RUNTIME__
#define __MLP_RUNTIME__


#include <stdlib.h>
#include <stdio.h>


#include <pthread.h>
#include "runtime.h"
#include "mem.h"
#include "Queue.h"
#include "psemaphore.h"
#include "mlp_lock.h"
#include "memPool.h"



#ifndef FALSE
#define FALSE 0
#endif

#ifndef TRUE
#define TRUE 1
#endif

#define NUMBINS 128
#define NUMREAD 64
#define NUMITEMS 64
#define NUMRENTRY 256

#define READY 1
#define NOTREADY 0

#define READ 0
#define WRITE 1
#define PARENTREAD 2
#define PARENTWRITE 3
#define COARSE 4
#define PARENTCOARSE 5
#define SCCITEM 6

#define HASHTABLE 0
#define VECTOR 1
#define SINGLEITEM 2

#define READBIN 0
#define WRITEBIN 1

#define H_MASK (NUMBINS)-1

#ifndef FALSE
#define FALSE 0
#endif

#ifndef TRUE
#define TRUE 1
#endif


// these are useful for interpreting an INTPTR to an
// Object at runtime to retrieve the object's type
// or object id (OID), 64-bit safe
#define OBJPTRPTR_2_OBJTYPE( opp ) ((int*)*(opp))[0]
// int hashcode is the [1] NOW!!!
#define OBJPTRPTR_2_OBJOID(  opp ) ((int*)*(opp))[2]

// forwarding list elements is a linked
// structure of arrays, should help task
// dispatch because the first element is
// an embedded member of the task record,
// only have to do memory allocation if
// a lot of items are on the list
#define FLIST_ITEMS_PER_ELEMENT 30
typedef struct ForwardingListElement_t {
  int                             numItems;
  struct ForwardingListElement_t* nextElement;
  INTPTR                          items[FLIST_ITEMS_PER_ELEMENT];
} ForwardingListElement;

struct MemPool_t;

// these fields are common to any SESE, and casting the
// generated SESE record to this can be used, because
// the common structure is always the first item in a
// customized SESE record
typedef struct SESEcommon_t {  

  // the identifier for the class of sese's that
  // are instances of one particular static code block
  // IMPORTANT: the class ID must be the first field of
  // the task record so task dispatch works correctly!
  int classID;
  volatile int    unresolvedDependencies;

  // a parent waits on this semaphore when stalling on
  // this child, the child gives it at its SESE exit
  psemaphore* parentsStallSem;

  
  // NOTE: first element is embedded in the task
  // record, so don't free it!
  //ForwardingListElement forwardList;
  struct Queue forwardList;

  volatile int             doneExecuting;
  volatile int             numRunningChildren;

  struct SESEcommon_t*   parent;
  
  int numMemoryQueue;
  int rentryIdx;
  int unresolvedRentryIdx;
  volatile int refCount;
  int numDependentSESErecords;
  int offsetToDepSESErecords;
  struct MemPool_t *     taskRecordMemPool;

  struct MemoryQueue_t** memoryQueueArray;
  struct REntry_t* rentryArray[NUMRENTRY];
  struct REntry_t* unresolvedRentryArray[NUMRENTRY];

#ifdef RCR
  struct Hashtable_rcr ** allHashStructures;
  int offsetToParamRecords;
  volatile int rcrstatus;
  volatile int retired;
#endif

  // the lock guards the following data SESE's
  // use to coordinate with one another
  pthread_mutex_t lock;
  pthread_cond_t  runningChildrenCond;
} SESEcommon;

// a thread-local var refers to the currently
// running task
extern __thread SESEcommon* runningSESE;
extern __thread int childSESE;

// there only needs to be one stall semaphore
// per thread, just give a reference to it to
// the task you are about to block on
extern __thread psemaphore runningSESEstallSem;



typedef struct REntry_t{
  // fine read:0, fine write:1, parent read:2, 
  // parent write:3 coarse: 4, parent coarse:5, scc: 6
  int type;
#ifdef RCR
  int count;
#else
  int isBufMode;
#endif
  struct MemoryQueueItem_t *qitem;
  struct BinItem_t* binitem;
  struct MemoryQueue_t* queue;
  SESEcommon* seseRec;
  INTPTR* pointer;
#ifdef RCR
  INTPTR mask;
  int index;
#else
  psemaphore * parentStallSem;
  int tag;
#endif
} REntry;

#ifdef RCR
#define RCRSIZE 32
#define RUNBIAS 1000000

struct rcrRecord {
  int count;
  int index;
  int flag;
  int array[RCRSIZE];
  void * ptrarray[RCRSIZE];
  struct rcrRecord *next;
};

typedef struct SESEstall_t { 
  SESEcommon common;
  int size;
  void * next;
  struct ___Object___* ___obj___;
  struct rcrRecord rcrRecords[1];
  int tag;
} SESEstall;
#endif

typedef struct MemoryQueueItem_t {
  int type; // hashtable:0, vector:1, singleitem:2
  int total;        //total non-retired
  int status;       //NOTREADY, READY
  struct MemoryQueueItem_t *next;
  
} MemoryQueueItem;

typedef struct MemoryQueue_t {
  MemoryQueueItem * head;
  MemoryQueueItem * tail;  
  REntry * binbuf[NUMBINS];
  REntry * buf[NUMRENTRY];
  int bufcount;
#ifndef OOO_DISABLE_TASKMEMPOOL
  MemPool * rentrypool;
#endif
} MemoryQueue;

typedef struct BinItem_t {
  int total;
  int status;       //NOTREADY, READY
  int type;         //READBIN:0, WRITEBIN:1
  struct BinItem_t * next;
} BinItem;

typedef struct Hashtable_t {
  MemoryQueueItem item;
  struct BinElement_t* array[NUMBINS];
  struct Queue*   unresolvedQueue;
} Hashtable;

typedef struct BinElement_t {
  BinItem * head;
  BinItem * tail;
} BinElement;

typedef struct WriteBinItem_t {
  BinItem item;
  REntry * val;
} WriteBinItem;

typedef struct ReadBinItem_t {
  BinItem item;
  REntry * array[NUMREAD];
  int index;
} ReadBinItem;

typedef struct Vector_t {
  MemoryQueueItem item;
  REntry * array[NUMITEMS];
  int index;
} Vector;

typedef struct SCC_t {
  MemoryQueueItem item;
  REntry * val;
} SCC;

int ADDRENTRY(MemoryQueue* q, REntry * r);
void RETIRERENTRY(MemoryQueue* Q, REntry * r);




static inline void ADD_FORWARD_ITEM( ForwardingListElement* e,
                                     SESEcommon*            s ) {
  //atomic_inc( &(s->refCount) );
}

// simple mechanical allocation and 
// deallocation of SESE records
void* mlpAllocSESErecord( int size );
void  mlpFreeSESErecord( SESEcommon* seseRecord );

MemoryQueue** mlpCreateMemoryQueueArray(int numMemoryQueue);
REntry* mlpCreateFineREntry(MemoryQueue *q, int type, SESEcommon* seseToIssue, void* dynID);
#ifdef RCR
REntry* mlpCreateREntry(MemoryQueue *q, int type, SESEcommon* seseToIssue, INTPTR mask);
#else
REntry* mlpCreateREntry(MemoryQueue *q, int type, SESEcommon* seseToIssue);
#endif
MemoryQueue* createMemoryQueue();
void rehashMemoryQueue(SESEcommon* seseParent);
void TAILWRITECASE(Hashtable *T, REntry *r, BinItem *val, BinItem *bintail, int key, int inc);
void RETIRESCC(MemoryQueue *Q, REntry *r);
void RETIREHASHTABLE(MemoryQueue *q, REntry *r);
void RETIREBIN(Hashtable *T, REntry *r, BinItem *b);
void RETIREVECTOR(MemoryQueue *Q, REntry *r);
void RESOLVECHAIN(MemoryQueue *Q);
void RESOLVEHASHTABLE(MemoryQueue *Q, Hashtable *T);
void RESOLVEVECTOR(MemoryQueue *q, Vector *V);
void RESOLVESCC(MemoryQueue *q, SCC *S);
void resolveDependencies(REntry* rentry);

#ifndef RCR
int RESOLVEBUF(MemoryQueue * q, SESEcommon *seseCommon);
void resolvePointer(REntry* rentry);
#endif

static inline void ADD_REFERENCE_TO( SESEcommon* seseRec ) {
  atomic_inc( &(seseRec->refCount) );
}

static inline int RELEASE_REFERENCE_TO( SESEcommon* seseRec ) {
  if( atomic_sub_and_test( 1, &(seseRec->refCount) ) ) {
    poolfreeinto( seseRec->parent->taskRecordMemPool, seseRec );
    return 1;
  }
  return 0;
}

static inline int RELEASE_REFERENCES_TO( SESEcommon* seseRec, int refCount) {
  if( atomic_sub_and_test( refCount, &(seseRec->refCount) ) ) {
    poolfreeinto( seseRec->parent->taskRecordMemPool, seseRec );
    return 1;
  }
  return 0;
}

#define CHECK_RECORD(x) ;


////////////////////////////////////////////////
// 
//  Some available debug versions of the above
//  pool allocation-related helpers.  The lower
//  'x' appended to names means they are not hooked
//  up, but check em in so we can switch names and
//  use them for debugging
//
////////////////////////////////////////////////
#define ADD_REFERENCE_TOx(x) atomic_inc( &((x)->refCount) ); printf("0x%x ADD 0x%x on %d\n",(INTPTR)runningSESE,(INTPTR)(x),__LINE__);

#define RELEASE_REFERENCE_TOx(x) if (atomic_sub_and_test(1, &((x)->refCount))) {poolfreeinto(x->parent->taskRecordMemPool, x);printf("0x%x REL 0x%x on %d\n",(INTPTR)runningSESE,(INTPTR)(x),__LINE__);}

#define CHECK_RECORDx(x) {                                              \
    if( ((SESEcommon*)(x))->refCount < 0 ||                             \
        ((SESEcommon*)(x))->refCount < 0 ) {                            \
      printf( "Acquired 0x%x from poolalloc, with refCount=%d\n", (INTPTR)(x), ((SESEcommon*)(x))->refCount ); } \
  }



// this is for using a memPool to allocate task records,
// pass this into the poolcreate so it will run your
// custom init code ONLY for fresh records, reused records
// can be returned as is
void freshTaskRecordInitializer( void* seseRecord );
  

#endif /* __MLP_RUNTIME__ */
