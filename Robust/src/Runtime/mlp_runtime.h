#ifndef __MLP_RUNTIME__
#define __MLP_RUNTIME__


#include <pthread.h>
#include "Queue.h"
#include "psemaphore.h"


// forward delcarations
struct SESErecord_t;



/*
typedef struct SESEvar_t {
  // the value when it is known will be placed
  // in this location, which can be accessed
  // as a variety of types
  union {
    char      sesetype_byte;
    int       sesetype_boolean;
    short     sesetype_short;
    int       sesetype_int;
    long long sesetype_long;
    short     sesetype_char;
    float     sesetype_float;
    double    sesetype_double;
    void*     sesetype_object;
  };  
} SESEvar;
*/

typedef struct SESErecord_t {  
  // the identifier for the class of sese's that
  // are instances of one particular static code block
  int classID;

  // The following fields have this structure:
  // [INTPTR numPtrs][void* next][ptr0][ptr1]...
  void* inSetObjs;
  void* outSetObjsNotInInSet;

  // The following fields point to compile-time
  // generated structures that have named 
  // primitive fields
  void* inSetPrims;
  void* outSetPrimsNotInInSet;

  // the lock guards the following data SESE's
  // use to coordinate with one another
  pthread_mutex_t lock;
  struct Queue*   forwardList;
  int             doneExecuting;

} SESErecord;


// simple mechanical allocation and deallocation
// of SESE records
SESErecord* mlpCreateSESErecord( int   classID,
				 void* inSetObjs,
				 void* outSetObjsNotInInSet,
				 void* inSetPrims,
				 void* outSetPrimsNotInInSet
                               );

void mlpDestroySESErecord( SESErecord* sese );


// main library functions
void mlpInit();
void mlpIssue( SESErecord* sese );
void mlpStall( SESErecord* sese );


#endif /* __MLP_RUNTIME__ */
