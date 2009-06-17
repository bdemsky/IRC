#ifndef __MLP_RUNTIME__
#define __MLP_RUNTIME__


#include <pthread.h>
#include "Queue.h"


// forward delcarations
struct SESErecord_t;
struct invokeSESEargs_t;


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


typedef struct SESErecord_t {  
  // the identifier for the class of sese's that
  // are instances of one particular static code block
  int classID;

  // pointers to SESEs directly above or below
  // in the heirarchy
  //struct SESErecord_t* parent;
  //struct Queue*        childrenList;
  // IMPLEMENT THIS LIKE STALLS--EVERY PARENTS EXIT MUST
  // "STALL" on COMPLETETION OF ALL ISSUED CHILDREN, SO
  // ALWAYS GIVE A CHILD A SEMAPHORE THAT IS ON YOUR LIST
  // OF THINGS TO BLOCK ON AT EXIT

  // for state of vars after issue
  SESEvar* vars;
  
  // when this sese is ready to be invoked,
  // allocate and fill in this structure, and
  // the primitives will be passed out of the
  // above var array at the call site
  void* paramStruct;

  // for signaling transition from issue 
  // to execute
  pthread_cond_t*  startCondVar;
  pthread_mutex_t* startCondVarLock;
  int startedExecuting;

  // use a list of SESErecords and a lock to let
  // consumers tell this SESE who wants values
  // forwarded to it
  pthread_mutex_t* forwardListLock;
  struct Queue*    forwardList;
  int doneExecuting;

} SESErecord;


typedef struct invokeSESEargs_t {
  int classID;
  SESErecord* invokee;
  SESErecord* parent;
} invokeSESEargs;


// simple mechanical allocation and deallocation
// of SESE records
SESErecord* mlpCreateSESErecord( int         classID,
                                 int         instanceID,
                                 SESErecord* parent,
                                 int         numVars,
                                 void*       paramStruct
                                 );

void mlpDestroySESErecord( SESErecord* sese );


// main library functions
void mlpInit();

SESErecord* mlpGetCurrent();
SESErecord* mlpSchedule();

void mlpIssue     ( SESErecord* sese );
void mlpStall     ( SESErecord* sese );
void mlpNotifyExit( SESErecord* sese );


extern SESErecord* rootsese;


#endif /* __MLP_RUNTIME__ */
