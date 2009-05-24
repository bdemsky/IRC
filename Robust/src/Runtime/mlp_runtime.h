#ifndef __MLP_RUNTIME__
#define __MLP_RUNTIME__


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
    char   sesetype_byte;
    char   sesetype_boolean;
    short  sesetype_short;
    int    sesetype_int;
    long   sesetype_long;
    char   sesetype_char;
    float  sesetype_float;
    double sesetype_double;
  };
  
  // a statically or dynamically known SESE
  // to gather the variable's value from
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
};


void mlpInit();

void mlpIssue     ( struct SESErecord* sese );
void mlpStall     ( struct SESErecord* sese );
void mlpNotifyExit( struct SESErecord* sese );


#endif /* __MLP_RUNTIME__ */
