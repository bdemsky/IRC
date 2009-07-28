#include "runtime.h"

#include <stdlib.h>
#include <stdio.h>
#include <assert.h>

#include "mem.h"
#include "Queue.h"
#include "mlp_runtime.h"
#include "workschedule.h"


#define FALSE 0
#define TRUE  1



void* mlpAllocSESErecord( int size ) {

  void* newrec = RUNMALLOC( size );
  
  /*
  SESErecord* commonView = (SESErecord*) newrec;
  commonView->classID = classID;
  pthread_mutex_init( &(newrec->lock),  NULL );
  newrec->forwardList   = createQueue();
  newrec->doneExecuting = FALSE;
  */

  return newrec;
}


void mlpFreeSESErecord( void* seseRecord ) {

  /*
  SESErecord* commonView = (SESErecord*) seseRecord;
  pthread_mutex_destroy( &(commonView->lock) );
  freeQueue( commonView->forwardList );
  */

  RUNFREE( seseRecord );
}


/*
struct rootSESEinSetObjs  { char** argv; };
struct rootSESEinSetPrims { int argc;    };
*/

void mlpInit( int numProcessors, 
	      void(*workFunc)(void*),
	      int argc, char** argv,
	      int maxSESEage ) {  

  //SESErecord* rootSESE;
  
  //struct rootSESEinSetObjs*  inObjs  = RUNMALLOC( sizeof( struct rootSESEinSetObjs ) );
  //struct rootSESEinSetPrims* inPrims = RUNMALLOC( sizeof( struct rootSESEinSetPrims ) );

  // first initialize the work scheduler
  workScheduleInit( numProcessors, workFunc );

  // the prepare the root SESE
  //inObjs->argv  = argv;
  //inPrims->argc = argc;
  //rootSESE = mlpCreateSESErecord( 0, inObjs, NULL, inPrims, NULL );

  // skip the issue step because the root SESE will
  // never have outstanding dependencies
  //workScheduleSubmit( (void*) rootSESE );

  // now the work scheduler is initialized and work is
  // in the hopper, so begin processing.  This call 
  // will not return
  workScheduleBegin();
}


void mlpIssue( void* seseRecord ) {

}


void mlpStall( void* seseRecord ) {
  
}
