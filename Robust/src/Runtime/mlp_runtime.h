#ifndef __MLP_RUNTIME__
#define __MLP_RUNTIME__


#include <pthread.h>
#include "Queue.h"
#include "psemaphore.h"

/*
// forward delcarations
struct SESErecord_t;


typedef struct SESErecord_t {  
  // the identifier for the class of sese's that
  // are instances of one particular static code block
  int classID;

  // This field is a structure of in-set and out-set
  // objects with the following layout:
  // [INTPTR numPtrs][void* next][ptr0][ptr1]...
  void* inSetOutSetObjs;

  // This field is a structure of primitives for
  // the in-set and out-set
  void* inSetOutSetPrims;

  // the lock guards the following data SESE's
  // use to coordinate with one another
  pthread_mutex_t lock;
  struct Queue*   forwardList;
  int             doneExecuting;

} SESErecord;
*/

/*
typedef struct SESEvarSrc_t {
  void* seseRecord;
  int         offset;
} SESEvarSrc;
*/

/*
// simple mechanical allocation and deallocation
// of SESE records
SESErecord* mlpCreateSESErecord( int   classID,
				 void* inSetOutSetObjs,
				 void* inSetOutSetPrims
                               );

void mlpDestroySESErecord( SESErecord* sese );
*/


// main library functions
void mlpInit();
void mlpIssue( void* seseRecord );
void mlpStall( void* seseRecord );


#endif /* __MLP_RUNTIME__ */
