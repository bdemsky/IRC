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

// each allocation site needs the following
typedef struct AllocSite_t{
  long id;
  struct Queue* waitingQueue;
} AllocSite;

typedef struct ConflictNode_t{
  int id;
} ConflictNode;


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

  AllocSite* allocSiteArray;
  int numRelatedAllocSites;
  psemaphore memoryStallSiteSem;
  struct Queue* connectedList;
  int numRelatedWaitingQueue;
  int waitingQueueItemID;

} SESEcommon;


typedef struct WaitingElement_t{
  void* seseRec;
  int status;
  int id;
  struct Queue* list;
} WaitingElement;

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

AllocSite* mlpCreateAllocSiteArray(int numAllocSites);
ConflictNode* mlpCreateConflictNode(int id);
int addWaitingQueueElement(AllocSite* allocSiteArray, int numAllocSites, long allocID, WaitingElement* wElement);
WaitingElement* mlpCreateWaitingElement(int status, void* seseToIssue, struct Queue* queue, int id);
void* mlpAllocSESErecord( int size );

#endif /* __MLP_RUNTIME__ */
