#include <stdlib.h>
#include <stdio.h>
#include <pthread.h>

#include "mem.h"
#include "Queue.h"
#include "workschedule.h"


// for convenience
typedef struct Queue deq;


// each worker needs the following
typedef struct workerData_t {
  pthread_t       workerThread;
  pthread_mutex_t dequeLock;
  deq*            dequeWorkUnits;
  int             nextWorkerToLoad;
} workerData;


// implementation internal data
static int             numWorkers;
static workerData*     workerDataArray;
static pthread_mutex_t systemBeginLock  = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t  systemBeginCond  = PTHREAD_COND_INITIALIZER;
static pthread_mutex_t systemReturnLock = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t  systemReturnCond = PTHREAD_COND_INITIALIZER;
static void(*workFunc)(void*);


// helper func
int threadID2workerIndex( pthread_t id ) {
  int i;
  for( i = 0; i < numWorkers; ++i ) {
    if( workerDataArray[i].workerThread == id ) {
      return i;
    }
  }
  // if we didn't find it, we are an outside
  // thread and should pick arbitrary worker
  return 0;
}



// the worker thread main func, which takes a func
// from user for processing any one work unit, then
// workers use it to process work units and steal
// them from one another
void* workerMain( void* arg ) {

  workerData* myData = (workerData*) arg;
  
  void* workUnit;

  int i;
  int j;

  // all workers wait until system is ready
  pthread_mutex_lock  ( &systemBeginLock );
  pthread_cond_wait   ( &systemBeginCond, &systemBeginLock );
  pthread_mutex_unlock( &systemBeginLock );

  while( 1 ) {

    // lock my deque
    pthread_mutex_lock( &(myData->dequeLock) );

    if( isEmpty( myData->dequeWorkUnits ) ) {

      // my deque is empty, try to steal
      pthread_mutex_unlock( &(myData->dequeLock) );
      
      workUnit = NULL;
      j = myData->nextWorkerToLoad;

      // look at everyone's queue at least twice
      for( i = 0; i < numWorkers; ++i ) {
	if( sched_yield() == -1 ) { printf( "Error thread trying to yield.\n" ); exit( -1 ); }
	
	++j; if( j == numWorkers ) { j = 0; }

	pthread_mutex_lock( &(workerDataArray[j].dequeLock) );

	if( isEmpty( workerDataArray[j].dequeWorkUnits ) ) {
	  pthread_mutex_unlock( &(workerDataArray[j].dequeLock) );
	  // no work here, keep looking
	  continue;
	}

	// found some work in another deque, steal it
	workUnit = getItemBack( workerDataArray[j].dequeWorkUnits );
	pthread_mutex_unlock( &(workerDataArray[j].dequeLock) );
	break;
      }

      // didn't find any work, even in my own deque,
      // after checking everyone twice?  Exit thread
      if( workUnit == NULL ) {
	break;
      }

    } else {
      // have work in own deque, take out from front
      workUnit = getItem( myData->dequeWorkUnits );
      pthread_mutex_unlock( &(myData->dequeLock) );
    }

    // wherever the work came from, process it
    workFunc( workUnit );

    if( sched_yield() == -1 ) { printf( "Error thread trying to yield.\n" ); exit( -1 ); }
  }

  printf( "Worker %d exiting.\n", myData->workerThread );
  fflush( stdout );

  return NULL;
}


void workScheduleInit( int numProcessors,
                       void(*func)(void*) ) {
  int i, status;

  numWorkers = numProcessors;
  workFunc   = func;

  // allocate space for worker data
  workerDataArray = RUNMALLOC( sizeof( workerData ) * numWorkers );
  for( i = 0; i < numWorkers; ++i ) {    

    // the deque
    workerDataArray[i].dequeWorkUnits = createQueue();

    // set the next worker to add work to as itself
    workerDataArray[i].nextWorkerToLoad = i;

    // it's lock
    status = pthread_mutex_init( &(workerDataArray[i].dequeLock), 
				 NULL
				 );
    if( status != 0 ) { printf( "Error\n" ); exit( -1 ); }
  }

  // only create the actual pthreads after all workers
  // have data that is protected with initialized locks
  for( i = 0; i < numWorkers; ++i ) {    
    status = pthread_create( &(workerDataArray[i].workerThread), 
                             NULL,
                             workerMain,
                             (void*) &(workerDataArray[i])
                           );
    if( status != 0 ) { printf( "Error\n" ); exit( -1 ); }
  }

  // yield and let all workers get to the begin
  // condition variable, waiting--we have to hold them
  // so they don't all see empty work queues right away
  if( sched_yield() == -1 ) {
    printf( "Error thread trying to yield.\n" );
    exit( -1 );
  }
}


void workScheduleSubmit( void* workUnit ) {

  // query who is submitting and find out who they are scheduled to load
  int submitterIndex = threadID2workerIndex( pthread_self() );
  int workerIndex    = workerDataArray[submitterIndex].nextWorkerToLoad;
  
  // choose a new index and save it
  ++workerIndex;
  if( workerIndex == numWorkers ) {
    workerIndex = 0;
  }
  workerDataArray[submitterIndex].nextWorkerToLoad = workerIndex;

  // load the chosen worker
  pthread_mutex_lock  ( &(workerDataArray[workerIndex].dequeLock) );
  addNewItemBack      (   workerDataArray[workerIndex].dequeWorkUnits, workUnit );
  pthread_mutex_unlock( &(workerDataArray[workerIndex].dequeLock) );
}


// really should be named "wait until work is finished"
void workScheduleBegin() {
  
  int i;

  // tell all workers to begin
  pthread_mutex_lock    ( &systemBeginLock );
  pthread_cond_broadcast( &systemBeginCond );
  pthread_mutex_unlock  ( &systemBeginLock );  

  // wait until workers inform that all work is complete
  /*
  pthread_mutex_lock  ( &systemReturnLock );
  pthread_cond_wait   ( &systemReturnCond, &systemReturnLock );
  pthread_mutex_unlock( &systemReturnLock );
  */

  for( i = 0; i < numWorkers; ++i ) {
    pthread_join( workerDataArray[i].workerThread, NULL );
  }
}
