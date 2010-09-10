#include <stdlib.h>
#include <stdio.h>
#include <pthread.h>

#include "mem.h"
#include "workschedule.h"
#include "mlp_runtime.h"
#include "coreprof/coreprof.h"

// NOTE: Converting this from a work-stealing strategy
// to a single-queue thread pool protected by a single
// lock.  This will not scale, but it will support
// development of the system for now



// for convenience
typedef struct Queue deq;

typedef struct workerData_t{
  pthread_t workerThread;
  int id;
} WorkerData;


static pthread_mutex_t systemLockIn;
static pthread_mutex_t systemLockOut;

// implementation internal data
static WorkerData*     workerDataArray;
static pthread_t*      workerArray;

static int systemStarted = 0;

static pthread_cond_t  systemBeginCond  = PTHREAD_COND_INITIALIZER;
static void(*workFunc)(void*);

static pthread_cond_t  workAvailCond  = PTHREAD_COND_INITIALIZER;

int             numWorkers;

int threadcount;
pthread_mutex_t gclock;
pthread_mutex_t gclistlock;
pthread_cond_t gccond;

extern struct listitem * list;
extern __thread struct listitem litem;
extern __thread SESEcommon* seseCommon;

__thread int oid;



void workerExit( void* arg ) {
  //printf( "Thread %d canceled.\n", pthread_self() );
  CP_EXIT();
}



void* workerMain( void* arg ) {
  void*       workUnit;
  WorkerData* myData = (WorkerData*) arg;
  int         oldState;
  int         haveWork;

  // once-per-thread stuff
  CP_CREATE();

  //pthread_cleanup_push( workerExit, NULL );  
  
  oid = myData->id;

  //pthread_setcanceltype ( PTHREAD_CANCEL_ASYNCHRONOUS, &oldState );
  //pthread_setcancelstate( PTHREAD_CANCEL_ENABLE,       &oldState );

  // then continue to process work
  while( 1 ) {

    // wait for work
    CP_LOGEVENT( CP_EVENTID_WORKSCHEDGRAB, CP_EVENTTYPE_BEGIN );
    haveWork = FALSE;
    while( !haveWork ) {
      pthread_mutex_lock( &systemLockOut );
      if( headqi->next == NULL ) {
        pthread_mutex_unlock( &systemLockOut );
        sched_yield();
        continue;
      } else {
        haveWork = TRUE;
      }
    }
    struct QI * tmp=headqi;
    headqi = headqi->next;
    workUnit = headqi->value;
    pthread_mutex_unlock( &systemLockOut );
    free( tmp );
    CP_LOGEVENT( CP_EVENTID_WORKSCHEDGRAB, CP_EVENTTYPE_END );
    
    pthread_mutex_lock(&gclistlock);
    threadcount++;
    litem.seseCommon=(void*)workUnit;
    litem.prev=NULL;
    litem.next=list;
    if(list!=NULL)
      list->prev=&litem;
    list=&litem;
    seseCommon=(SESEcommon*)workUnit;   
    pthread_mutex_unlock(&gclistlock);

    workFunc( workUnit );
    
    pthread_mutex_lock(&gclistlock);
    threadcount--;
    if (litem.prev==NULL) {
      list=litem.next;
    } else {
      litem.prev->next=litem.next;
    }
    if (litem.next!=NULL) {
      litem.next->prev=litem.prev;
    }
    pthread_mutex_unlock(&gclistlock);
  }

  //pthread_cleanup_pop( 0 );

  return NULL;
}

void workScheduleInit( int numProcessors,
                       void(*func)(void*) ) {
  int i, status;

  // the original thread must call this now to
  // protect memory allocation events coming, but it
  // will also add itself to the worker pool and therefore
  // try to call it again, CP_CREATE should just ignore
  // duplicate calls
  CP_CREATE();

  pthread_mutex_init(&gclock, NULL);
  pthread_mutex_init(&gclistlock, NULL);
  pthread_cond_init(&gccond, NULL);

  numWorkers = numProcessors + 1;

  workFunc   = func;

  headqi=tailqi=RUNMALLOC(sizeof(struct QI));
  headqi->next=NULL;
  
  status = pthread_mutex_init( &systemLockIn, NULL );
  status = pthread_mutex_init( &systemLockOut, NULL );

  // allocate space for one more--the original thread (running
  // this code) will become a worker thread after setup
  workerDataArray = RUNMALLOC( sizeof( WorkerData ) * (numWorkers+1) );

  for( i = 0; i < numWorkers; ++i ) {

    // the original thread is ID 1, start counting from there
    workerDataArray[i].id = 2 + i;

    status = pthread_create( &(workerDataArray[i].workerThread), 
                             NULL,
                             workerMain,
                             (void*) &(workerDataArray[i])
                           );

    if( status != 0 ) { printf( "Error\n" ); exit( -1 ); }

    // yield and let all workers get to the begin
    // condition variable, waiting--we have to hold them
    // so they don't all see empty work queues right away
    if( sched_yield() == -1 ) { printf( "Error thread trying to yield.\n" ); exit( -1 ); }
  }
}

void workScheduleSubmit( void* workUnit ) {
  struct QI* item=RUNMALLOC(sizeof(struct QI));
  item->value=workUnit;
  item->next=NULL;
  
  pthread_mutex_lock  ( &systemLockIn );
  tailqi->next=item;
  tailqi=item;
  pthread_mutex_unlock( &systemLockIn );
}


// really should be named "add original thread as a worker"
void workScheduleBegin() {
  int i;

  // space was saved for the original thread to become a
  // worker after setup is complete
  workerDataArray[numWorkers].id           = 1;
  workerDataArray[numWorkers].workerThread = pthread_self();
  ++numWorkers;

  workerMain( &(workerDataArray[numWorkers-1]) );
}


// the above function does NOT naturally join all the worker
// threads at exit, once the main SESE/Rblock/Task completes
// we know all worker threads are finished executing other
// tasks so we can explicitly kill the workers, and therefore
// trigger any worker-specific cleanup (like coreprof!)
void workScheduleExit() {
  int i;

  // This is not working well--canceled threads don't run their
  // thread-level exit routines?  Anyway, its not critical for
  // coreprof but if we ever need a per-worker exit routine to
  // run we'll have to look back into this.

  //printf( "Thread %d performing schedule exit.\n", pthread_self() );
  //
  //for( i = 0; i < numWorkers; ++i ) {   
  //  if( pthread_self() != workerDataArray[i].workerThread ) {
  //    pthread_cancel( workerDataArray[i].workerThread );      
  //  }
  //}
  //
  //// how to let all the threads actually get canceled?  
  //sleep( 2 );
}
