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
  int             doneExecuting;

} SESEcommon;


/*
// a parent remembers an SESE instance, say class ID=2
// and age=0, by declaring an SESEvarSrc seseID2_age0
// and keeping the fields up-to-date
typedef struct SESEvarSrc_t {
  void*  sese;
  INTPTR addr;
} SESEvarSrc;
*/


// simple mechanical allocation and 
// deallocation of SESE records
void* mlpCreateSESErecord( int size );
void  mlpDestroySESErecord( void* seseRecord );


// main library functions
/*
void mlpInit();
void mlpCommonIssueActions( void* seseRecord );
void mlpStall( void* seseRecord );
*/

#endif /* __MLP_RUNTIME__ */
