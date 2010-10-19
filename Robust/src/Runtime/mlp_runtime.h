#ifndef __MLP_RUNTIME__
#define __MLP_RUNTIME__


#include <pthread.h>
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
#define OBJPTRPTR_2_OBJTYPE( opp ) ((int*)(*(opp)))[0]
#define OBJPTRPTR_2_OBJOID(  opp ) ((int*)(*(opp)))[1]



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


  // a parent waits on this semaphore when stalling on
  // this child, the child gives it at its SESE exit
  psemaphore* parentsStallSem;

  
  // the lock guards the following data SESE's
  // use to coordinate with one another
  pthread_mutex_t lock;

  // NOTE: first element is embedded in the task
  // record, so don't free it!
  //ForwardingListElement forwardList;
  struct Queue* forwardList;


  volatile int    unresolvedDependencies;

  pthread_cond_t  doneCond;
  int             doneExecuting;

  pthread_cond_t  runningChildrenCond;
  int             numRunningChildren;

  struct SESEcommon_t*   parent;

  int numMemoryQueue;
  int rentryIdx;
  int unresolvedRentryIdx;
  struct MemoryQueue_t** memoryQueueArray;
  struct REntry_t* rentryArray[NUMRENTRY];
  struct REntry_t* unresolvedRentryArray[NUMRENTRY];

  int numDependentSESErecords;
  int offsetToDepSESErecords;
#ifdef RCR
  int offsetToParamRecords;
#endif

  // for determining when task records can be returned
  // to the parent record's memory pool
  MemPool*     taskRecordMemPool;
  volatile int refCount;

} SESEcommon;


// a thread-local var refers to the currently
// running task
extern __thread SESEcommon* runningSESE;

// there only needs to be one stall semaphore
// per thread, just give a reference to it to
// the task you are about to block on
extern __thread psemaphore runningSESEstallSem;



typedef struct REntry_t{
  // fine read:0, fine write:1, parent read:2, 
  // parent write:3 coarse: 4, parent coarse:5, scc: 6
  int type;
  struct Hashtable_t* hashtable;
  struct BinItem_t* binitem;
  struct Vector_t* vector;
  struct SCC_t* scc;
  struct MemoryQueue_t* queue;
  psemaphore parentStallSem;
  SESEcommon* seseRec;
  INTPTR* pointer;
  int isBufMode;
} REntry;

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
REntry* mlpCreateFineREntry(int type, SESEcommon* seseToIssue, void* dynID);
REntry* mlpCreateREntry    (int type, SESEcommon* seseToIssue);
MemoryQueue* createMemoryQueue();
void rehashMemoryQueue(SESEcommon* seseParent);


static inline void ADD_REFERENCE_TO( SESEcommon* seseRec ) {
  atomic_inc( &(seseRec->refCount) );
}

static inline void RELEASE_REFERENCE_TO( SESEcommon* seseRec ) {
  if( atomic_sub_and_test( 1, &(seseRec->refCount) ) ) {
    poolfreeinto( seseRec->parent->taskRecordMemPool, seseRec );
  }
}


#endif /* __MLP_RUNTIME__ */
