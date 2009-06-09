#ifndef __MLP_RUNTIME__
#define __MLP_RUNTIME__


#include <pthread.h>
#include "Queue.h"


// a forward delcaration for SESEvar
struct SESErecord_t;


typedef struct SESEvar_t {
  //unsigned char mode;

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
  
  // a statically or dynamically known SESE
  // to gather the variable's value from
  // if source==NULL it indicates the root
  // SESE, which has no record, just normal
  // temp names
  //struct SESErecord_t* source;
  //unsigned int         index;
} SESEvar;


typedef struct SESErecord_t {  
  // the identifier for the class of sese's that
  // are instances of one particular static code block
  int classID;

  // not globally unqiue, but each parent ensures that
  // its children have unique identifiers, including to
  // the parent itself
  int instanceID;

  // used to give out IDs to children
  int childInstanceIDs;

  // pointers to SESEs directly above or below
  // in the heirarchy
  struct SESErecord_t* parent;
  struct Queue*        childrenList;

  // for state of vars after issue
  SESEvar* vars;
  
  // when this sese is ready to be invoked,
  // allocate and fill in this structure, and
  // the primitives will be passed out of the
  // above var array at the call site
  void* paramStruct;


  pthread_cond_t*  startCondVar;
  pthread_mutex_t* startCondVarLock;


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
