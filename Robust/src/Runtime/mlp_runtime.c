#include <stdlib.h>
#include <stdio.h>
#include <assert.h>
#include "mlp_runtime.h"
#include "Queue.h"


// the root sese is accessible globally so
// buildcode can generate references to it
struct SESErecord* rootsese;


// the issuedQ, in this simple version, spits
// out SESErecord's in the order they were issued
static struct Queue* issuedQ;


// the class_age2instance maps an SESE class id and
// age value to a particular SESErecord instance
static struct SESErecord** class_age2instance;


// each core should have a current SESE
static struct SESErecord* current;


void mlpInit( int totalNumSESEs, int maxSESEage ) {  
  rootsese = (struct SESErecord*) malloc( sizeof( struct SESErecord ) );

  issuedQ = createQueue();

  class_age2instance = (struct SESErecord**) malloc( sizeof( struct SESErecord* ) *
                                                     maxSESEage *
                                                     totalNumSESEs
                                                   );
   
  current = rootsese;
}


struct SESErecord* mlpGetCurrent() {
  return current;
}


void mlpIssue( struct SESErecord* sese ) {
  addNewItem( issuedQ, (void*) sese );
}


struct SESErecord* mlpSchedule() {
  assert( !isEmpty( issuedQ ) );
  return (struct SESErecord*) getItem( issuedQ );  
}


void mlpStall( struct SESErecord* sese ) {
  
}


void mlpNotifyExit( struct SESErecord* sese ) {
  
}
