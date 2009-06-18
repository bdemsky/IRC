#include "runtime.h"

#include <stdlib.h>
#include <stdio.h>
#include <assert.h>

#include "mlp_runtime.h"
#include "Queue.h"


#define FALSE 0
#define TRUE  1


// the root sese is accessible globally so
// buildcode can generate references to it
//SESErecord* rootsese;


// the issuedQ, in this simple version, spits
// out SESErecord's in the order they were issued
static struct Queue* issuedQ;



SESErecord* mlpCreateSESErecord( int   classID,
                                 void* namespace,
                                 void* paramStruct
			       ) {

  SESErecord* newrec = malloc( sizeof( SESErecord ) );

  //newrec->parent           = parent;
  //newrec->childrenList     = createQueue();
  //newrec->vars             = malloc( sizeof( SESEvar ) * numVars );

  newrec->classID          = classID;
  newrec->namespace        = namespace;
  newrec->paramStruct      = paramStruct;

  newrec->forwardList      = createQueue();
  newrec->doneExecuting    = FALSE;
  //newrec->startedExecuting = FALSE;

  psem_init         ( &(newrec->stallSem)            );

  /*
  pthread_cond_init ( newrec->startCondVar,     NULL );
  pthread_mutex_init( newrec->startCondVarLock, NULL );
  pthread_mutex_init( newrec->forwardListLock,  NULL );
  */

  return newrec;
}


void mlpDestroySESErecord( SESErecord* sese ) {

  /*
  pthread_cond_destroy ( sese->startCondVar     );
  pthread_mutex_destroy( sese->startCondVarLock );
  pthread_mutex_destroy( sese->forwardListLock  );
  */

  /*
  free     ( sese->startCondVar     );
  free     ( sese->startCondVarLock );
  free     ( sese->forwardListLock  );
  freeQueue( sese->forwardList      );
  //freeQueue( sese->childrenList     );
  free     ( sese->vars             );
  */
  free     ( sese->namespace        );
  free     ( sese                   );
}


void mlpInit( int totalNumSESEs, int maxSESEage ) {  

  issuedQ = createQueue();

  /*
  class_age2instance = (SESErecord**) malloc( sizeof( SESErecord* ) *
                                              maxSESEage            *
                                              totalNumSESEs
                                            );
  */
  //current = rootsese;
  //current = NULL;
}


/*
SESErecord* mlpGetCurrent() {
  return current;
}
*/

void mlpIssue( SESErecord* sese ) {
  addNewItem( issuedQ, (void*) sese );
}


SESErecord* mlpSchedule() {
  assert( !isEmpty( issuedQ ) );
  return (SESErecord*) getItem( issuedQ );  
}


void mlpStall( SESErecord* sese ) {
  
}


void mlpNotifyExit( SESErecord* sese ) {
  
}
