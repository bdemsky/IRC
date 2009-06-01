#ifndef __MLP_RUNTIME__
#define __MLP_RUNTIME__


#include <pthread.h>
#include "Queue.h"


// value mode means the variable's value
// is present in the SESEvar struct
#define SESEvar_MODE_VALUE   3001

// static move means the variable's value
// will come from a statically known SESE
#define SESEvar_MODE_STATIC  3002

// dynamic mode means the variable's value
// will come from an SESE, and the exact
// SESE will be determined at runtime
#define SESEvar_MODE_DYNAMIC 3003


// a forward delcaration for SESEvar
struct SESErecord;


struct SESEvar {
  unsigned char mode;

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
  struct SESErecord* source;
  unsigned int index;
};


struct SESErecord {  
  // the identifier for the class of sese's that
  // are instances of one particular static code block
  int classID;

  // not globally unqiue, but each parent ensures that
  // its children have unique identifiers, including to
  // the parent itself
  int instanceID;

  // for state of vars after issue
  struct SESEvar* vars;
  
  // when this sese is ready to be invoked,
  // allocate and fill in this structure, and
  // the primitives will be passed out of the
  // above var array at the call site
  void* paramStruct;

  // use a list of SESErecords and a lock to let
  // consumers tell this SESE who wants values
  // forwarded to it
  pthread_mutex_t forwardListLock;// = PTHREAD_MUTUX_INITIALIZER;
  struct Queue* forwardList;
};


void mlpInit();

struct SESErecord* mlpGetCurrent();
struct SESErecord* mlpSchedule();

void mlpIssue     ( struct SESErecord* sese );
void mlpStall     ( struct SESErecord* sese );
void mlpNotifyExit( struct SESErecord* sese );


extern struct SESErecord* rootsese;


#endif /* __MLP_RUNTIME__ */
