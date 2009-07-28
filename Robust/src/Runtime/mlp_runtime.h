#ifndef __MLP_RUNTIME__
#define __MLP_RUNTIME__


#include <pthread.h>
#include "Queue.h"
#include "psemaphore.h"


// forward delcarations
//struct SESErecord_t;


// note that this record is never used other than
// to cast a customized record and have easy access
// the common fields listed here
typedef struct SESErecord_t {  

  // the identifier for the class of sese's that
  // are instances of one particular static code block
  int classID;

  // the lock guards the following data SESE's
  // use to coordinate with one another
  pthread_mutex_t lock;
  struct Queue*   forwardList;
  int             doneExecuting;

} SESErecord;


/*
typedef struct SESEvarSrc_t {
  void* seseRecord;
  int         offset;
} SESEvarSrc;
*/


// simple mechanical allocation and 
// deallocation of SESE records
void* mlpCreateSESErecord( int classID, int size );
void  mlpDestroySESErecord( void* seseRecord );


// main library functions
void mlpInit();
void mlpIssue( void* seseRecord );
void mlpStall( void* seseRecord );


#endif /* __MLP_RUNTIME__ */
