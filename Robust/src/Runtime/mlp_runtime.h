#ifndef __MLP_RUNTIME__
#define __MLP_RUNTIME__


#include <pthread.h>
#include "Queue.h"
#include "psemaphore.h"
#include "mlp_lock.h"

#ifndef FALSE
#define FALSE 0
#endif

#ifndef TRUE
#define TRUE 1
#endif

#define NUMBINS 64
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

#define H_MASK (NUMBINS<<4)-1

#ifndef FALSE
#define FALSE 0
#endif

#ifndef TRUE
#define TRUE 1
#endif

typedef struct REntry_t{
  int type; // fine read:0, fine write:1, parent read:2, parent write:3 coarse: 4, parent coarse:5, scc: 6
  struct Hashtable_t* hashtable;
  struct BinItem_t* binitem;
  struct Vector_t* vector;
  struct SCC_t* scc;
  struct MemoryQueue_t* queue;
  psemaphore parentStallSem;
  void* seseRec;
  INTPTR* pointer;
  int oid;
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


// forward declaration of pointer type
typedef struct SESEcommon_t* SESEcommon_p;

// these fields are common to any SESE, and casting the
// generated SESE record to this can be used, because
// the common structure is always the first item in a
// customized SESE record
typedef struct SESEcommon_t {  

  // the identifier for the class of sese's that
  // are instances of one particular static code block
  int classID;

  // a parent waits on this semaphore when stalling on
  // this child, the child gives it at its SESE exit
  psemaphore stallSem;

  
  // the lock guards the following data SESE's
  // use to coordinate with one another
  pthread_mutex_t lock;

  struct Queue*   forwardList;
  volatile int             unresolvedDependencies;

  pthread_cond_t  doneCond;
  int             doneExecuting;

  pthread_cond_t  runningChildrenCond;
  int             numRunningChildren;

  SESEcommon_p    parent;

  psemaphore parentStallSem;
  pthread_cond_t stallDone;

  int numMemoryQueue;
  int rentryIdx;
  int unresolvedRentryIdx;
  struct MemoryQueue_t** memoryQueueArray;
  struct REntry_t* rentryArray[NUMRENTRY];
  struct REntry_t* unresolvedRentryArray[NUMRENTRY];
  int offsetsize;
} SESEcommon;

// a thread-local stack of SESEs and function to
// ensure it is initialized once per thread
/*
extern __thread struct Queue* seseCallStack;
extern __thread pthread_once_t mlpOnceObj;
void mlpInitOncePerThread();
*/
extern __thread SESEcommon_p seseCaller;


// simple mechanical allocation and 
// deallocation of SESE records
void* mlpCreateSESErecord( int size );
void  mlpDestroySESErecord( void* seseRecord );
void* mlpAllocSESErecord( int size );

MemoryQueue** mlpCreateMemoryQueueArray(int numMemoryQueue);
REntry* mlpCreateFineREntry(int type, void* seseToIssue, void* dynID);
REntry* mlpCreateREntry(int type, void* seseToIssue);
MemoryQueue* createMemoryQueue();
void rehashMemoryQueue(SESEcommon_p seseParent);

#endif /* __MLP_RUNTIME__ */
