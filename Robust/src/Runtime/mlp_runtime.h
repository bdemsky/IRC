#ifndef __MLP_RUNTIME__
#define __MLP_RUNTIME__


#include <pthread.h>
#include "Queue.h"
#include "psemaphore.h"


#ifndef FALSE
#define FALSE 0
#endif

#ifndef TRUE
#define TRUE 1
#endif

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
  int             unresolvedDependencies;

  pthread_cond_t  doneCond;
  int             doneExecuting;

  pthread_cond_t  runningChildrenCond;
  int             numRunningChildren;

  SESEcommon_p    parent;

} SESEcommon;


// a thread-local stack of SESE's that have called a
// new method context
extern __thread struct Queue* seseCallStack;


// simple mechanical allocation and 
// deallocation of SESE records
void* mlpCreateSESErecord( int size );
void  mlpDestroySESErecord( void* seseRecord );


#endif /* __MLP_RUNTIME__ */
