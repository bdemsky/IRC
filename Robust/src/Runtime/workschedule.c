#include <stdlib.h>
#include <stdio.h>
#include <pthread.h>

#include "mem.h"
#include "workschedule.h"
#include "mlp_runtime.h"
#include "psemaphore.h"
#include "coreprof/coreprof.h"
#ifdef SQUEUE
#include "squeue.h"
#else
#include "deque.h"
#endif
#ifdef RCR
#include "rcr_runtime.h"
#include "trqueue.h"
#endif


//////////////////////////////////////////////////
//
//  for coordination with the garbage collector
//
//////////////////////////////////////////////////
int threadcount;
pthread_mutex_t gclock;
pthread_mutex_t gclistlock;
pthread_cond_t gccond;
#ifdef RCR
extern pthread_mutex_t queuelock;
#endif
// in garbage.h, listitem is a struct with a pointer
// to a stack, objects, etc. such that the garbage
// collector can find pointers for garbage collection

// this is a global list of listitem structs that the
// garbage collector uses to know about each thread
extern struct listitem* list;

// this is the local thread's item on the above list,
// it should be added to the global list before a thread
// starts doing work, and should be removed only when
// the thread is completely finished--in OoOJava/MLP the
// only thing hanging from this litem should be a single
// task record that the worker thread is executing, if any!
extern __thread struct listitem litem;
//////////////////////////////////////////////////
//
//  end coordination with the garbage collector
//
//////////////////////////////////////////////////




typedef struct workerData_t {
  pthread_t workerThread;
  int       id;
} WorkerData;

// a thread should know its worker id in any
// functions below
static __thread int myWorkerID;

// the original thread starts up the work scheduler
// and sleeps while it is running, it has no worker
// ID so use this to realize that
static const int workerID_NOTAWORKER = 0xffffff0;


int numWorkSchedWorkers;
static WorkerData*  workerDataArray;
static pthread_t*   workerArray;

static void(*workFunc)(void*);

// each thread can create objects but should assign
// globally-unique object ID's (oid) so have threads
// give out this as next id, then increment by number
// of threads to ensure disjoint oid sets
__thread int oid;

// global array of work-stealing deques, where
// each thread uses its ID as the index to its deque
deque* deques;



#ifdef RCR
#include "trqueue.h"
__thread struct trQueue * TRqueue=NULL;
#endif



// this is a read-by-all and write-by-one variable
// IT IS UNPROTECTED, BUT SAFE for all threads to
// read it (periodically, only when they can find no work)
// and only the worker that retires the main thread will
// write it to 1, at which time other workers will see
// that they should exit gracefully
static volatile int mainTaskRetired = FALSE;




void* workerMain( void* arg ) {
  void*       workUnit;
  WorkerData* myData  = (WorkerData*) arg;
  deque*      myDeque = &(deques[myData->id]);
  int         keepRunning = TRUE;
  int         haveWork;
  int         lastVictim = 0;
  int         i;

  myWorkerID = myData->id;

  // ensure that object ID's start at 1 so that using
  // oid with value 0 indicates an invalid object
  oid = myData->id + 1;

  // each thread has a single semaphore that a running
  // task should hand off to children threads it is
  // going to stall on
  psem_init( &runningSESEstallSem );

  // the worker threads really have no context relevant to the
  // user program, so build an empty garbage list struct to
  // pass to the collector if collection occurs
  struct garbagelist emptygarbagelist = { 0, NULL };

  // Add this worker to the gc list
  pthread_mutex_lock( &gclistlock );
  threadcount++;
  litem.prev = NULL;
  litem.next = list;
  if( list != NULL ) 
    list->prev = &litem;
  list = &litem;
  pthread_mutex_unlock( &gclistlock );


  // start timing events in this thread
  CP_CREATE();


  // then continue to process work
  while( keepRunning ) {

    // wait for work
#ifdef CP_EVENTID_WORKSCHEDGRAB
    CP_LOGEVENT( CP_EVENTID_WORKSCHEDGRAB, CP_EVENTTYPE_BEGIN );
#endif

    haveWork = FALSE;
    while( !haveWork ) {

      workUnit = dqPopBottom( myDeque );


      if( workUnit != DQ_POP_EMPTY ) {
        haveWork = TRUE;
        goto dowork;
      } else {
        // try to steal from another queue, starting
        // with the last successful victim, don't check
        // your own deque
        for( i = 0; i < numWorkSchedWorkers - 1; ++i ) {

          workUnit = dqPopTop( &(deques[lastVictim]) );
          
#ifdef SQUEUE
          if( workUnit != DQ_POP_EMPTY ) {
#else
	  if( workUnit != DQ_POP_ABORT &&
	      workUnit != DQ_POP_EMPTY ) {
#endif
            // successful steal!
            haveWork = TRUE;
            goto dowork;
          }
       
          // choose next victim
          lastVictim++; if( lastVictim == numWorkSchedWorkers ) { lastVictim = 0; }
          
          if( lastVictim == myWorkerID ) {
            lastVictim++; if( lastVictim == numWorkSchedWorkers ) { lastVictim = 0; }
          }
        }
        // end steal attempts


        // if we successfully stole work, break out of the
        // while-not-have-work loop, otherwise we looked
        // everywhere, so drop down to "I'm idle" code below
        if( haveWork ) {
	  goto dowork;
        }
      }

      // if we drop down this far, we didn't find any work,
      // so do a garbage collection, yield the processor,
      // then check if the entire system is out of work
      if( unlikely( needtocollect ) ) {
        checkcollect( &emptygarbagelist );
      }

      sched_yield();

      if( mainTaskRetired ) {
        keepRunning = FALSE;
        break;
      }

    } // end the while-not-have-work loop

    dowork:

#ifdef CP_EVENTID_WORKSCHEDGRAB
    CP_LOGEVENT( CP_EVENTID_WORKSCHEDGRAB, CP_EVENTTYPE_END );
#endif

    // when is no work left we will pop out
    // here, so only do work if any left
    if( haveWork ) {
      // let GC see current work
      litem.seseCommon = (void*)workUnit;

#ifdef DEBUG_DEQUE
      if( workUnit == NULL ) {
        printf( "About to execute a null work item\n" );
      }
#endif

      workFunc( workUnit );
    }
  } 


  CP_EXIT();


  // remove from GC list
  pthread_mutex_lock( &gclistlock );
  threadcount--;
  if( litem.prev == NULL ) {
    list = litem.next;
  } else {
    litem.prev->next = litem.next;
  }
  if( litem.next != NULL ) {
    litem.next->prev = litem.prev;
  }
  pthread_mutex_unlock( &gclistlock );


  return NULL;
}


void workScheduleInit( int numProcessors,
                       void(*func)(void*) ) {
  int i, status;
  pthread_attr_t attr;

  // the original thread must call this now to
  // protect memory allocation events coming
  CP_CREATE();

  // the original thread is a worker
  myWorkerID = 0;
  oid = 1;

#ifdef RCR
  pthread_mutex_init( &queuelock,     NULL );
#endif
  pthread_mutex_init( &gclock,     NULL );
  pthread_mutex_init( &gclistlock, NULL );
  pthread_cond_init ( &gccond,     NULL );


  numWorkSchedWorkers = numProcessors;

  workFunc = func;

  deques          = RUNMALLOC( sizeof( deque      )*numWorkSchedWorkers );
  workerDataArray = RUNMALLOC( sizeof( WorkerData )*numWorkSchedWorkers );

  for( i = 0; i < numWorkSchedWorkers; ++i ) {
    dqInit( &(deques[i]) );
  }
  
  pthread_attr_init( &attr );
  pthread_attr_setdetachstate( &attr, 
                               PTHREAD_CREATE_JOINABLE );

  workerDataArray[0].id = 0;

  for( i = 1; i < numWorkSchedWorkers; ++i ) {

    workerDataArray[i].id = i;

    status = pthread_create( &(workerDataArray[i].workerThread), 
                             &attr,
                             workerMain,
                             (void*) &(workerDataArray[i])
                             );

    if( status != 0 ) { printf( "Error\n" ); exit( -1 ); }
  }
}


void workScheduleSubmit( void* workUnit ) {
  CP_LOGEVENT( CP_EVENTID_WORKSCHEDSUBMIT, CP_EVENTTYPE_BEGIN );
  dqPushBottom( &(deques[myWorkerID]), workUnit );
  CP_LOGEVENT( CP_EVENTID_WORKSCHEDSUBMIT, CP_EVENTTYPE_END );
}


// really should be named "wait for work in system to complete"
void workScheduleBegin() {
  int i;

  // original thread becomes a worker
  workerMain( (void*) &(workerDataArray[0]) );

  // then wait for all other workers to exit gracefully
  for( i = 1; i < numWorkSchedWorkers; ++i ) {
    pthread_join( workerDataArray[i].workerThread, NULL );
  }

  // write all thread's events to disk
  CP_DUMP();
}


// only the worker that executes and then retires
// the main task should invoke this, which indicates to
// all other workers they should exit gracefully
void workScheduleExit() {
  mainTaskRetired = 1;
}
