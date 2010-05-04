#include <stdlib.h>
#include <stdio.h>
#include <pthread.h>

#include "mem.h"
#include "workschedule.h"
#include "mlp_runtime.h"


// NOTE: Converting this from a work-stealing strategy
// to a single-queue thread pool protected by a single
// lock.  This will not scale, but it will support
// development of the system for now



// for convenience
typedef struct Queue deq;


/*
// each worker needs the following
typedef struct workerData_t {
  pthread_t       workerThread;
  pthread_mutex_t dequeLock;
  deq*            dequeWorkUnits;
  int             nextWorkerToLoad;
} workerData;
*/

typedef struct workerData_t{
  pthread_t workerThread;
  int id;
} WorkerData;


static pthread_mutex_t systemLockIn;
static pthread_mutex_t systemLockOut;

// just one queue for everyone
//static pthread_mutex_t dequeLock;



// implementation internal data
static WorkerData*     workerDataArray;
static pthread_t*      workerArray;

static int systemStarted = 0;

//static pthread_mutex_t systemBeginLock  = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t  systemBeginCond  = PTHREAD_COND_INITIALIZER;
//static pthread_mutex_t systemReturnLock = PTHREAD_MUTEX_INITIALIZER;
//static pthread_cond_t  systemReturnCond = PTHREAD_COND_INITIALIZER;
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

/*
struct QI {
  struct QI * next;
  void * value;
};

struct QI * headqi;
struct QI * tailqi;
*/

/*
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
*/


/*
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
	  // no work here, yield and then keep looking
	  if( sched_yield() == -1 ) { printf( "Error thread trying to yield.\n" ); exit( -1 ); }
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
*/


void* workerMain( void* arg ) {
  
  void* workUnit;
  WorkerData* myData = (WorkerData*) arg;
  oid=myData->id;

  // make sure init mlp once-per-thread stuff
  //pthread_once( &mlpOnceObj, mlpInitOncePerThread );

  // all workers wait until system is ready

  // then continue to process work
  while( 1 ) {
   
    /*
    while(1){
      if(pthread_mutex_trylock(&systemLock)==0){
	if(isEmpty(dequeWorkUnits)){
	  pthread_mutex_unlock(&systemLock);
	}else{
	  break;
	}
      }
    }
    workUnit = getItem( dequeWorkUnits );
    pthread_mutex_unlock( &systemLock );
    */
    
    pthread_mutex_lock( &systemLockOut );
    // wait for work
    if (headqi->next==NULL) {
      pthread_mutex_unlock( &systemLockOut );
      sched_yield();
      continue;
    }
    struct QI * tmp=headqi;
    headqi = headqi->next;
    workUnit = headqi->value;
    pthread_mutex_unlock( &systemLockOut );
    free(tmp);
    // yield processor before moving on, just to exercise
    // system's out-of-order correctness
    //if( sched_yield() == -1 ) { printf( "Error thread trying to yield.\n" ); exit( -1 ); }
    //if( sched_yield() == -1 ) { printf( "Error thread trying to yield.\n" ); exit( -1 ); }

    
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

  return NULL;
}


/*
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
*/


void workScheduleInit( int numProcessors,
                       void(*func)(void*) ) {
  int i, status;

  
  pthread_mutex_init(&gclock, NULL);
  pthread_mutex_init(&gclistlock, NULL);
  pthread_cond_init(&gccond, NULL);

  //numWorkers = numProcessors*5;
  numWorkers = numProcessors + 1;

  workFunc   = func;

  headqi=tailqi=RUNMALLOC(sizeof(struct QI));
  headqi->next=NULL;
  
  status = pthread_mutex_init( &systemLockIn, NULL );
  status = pthread_mutex_init( &systemLockOut, NULL );

  //workerArray = RUNMALLOC( sizeof( pthread_t ) * numWorkers );
  workerDataArray = RUNMALLOC( sizeof( WorkerData ) * numWorkers );

  for( i = 0; i < numWorkers; ++i ) {   
    workerDataArray[i].id=i+2;
    status = pthread_create( &(workerDataArray[i].workerThread), 
                             NULL,
                             workerMain,
                             (void*) &(workerDataArray[i])
                           );
    //status = pthread_create( &(workerArray[i]), NULL, workerMain, NULL );
    if( status != 0 ) { printf( "Error\n" ); exit( -1 ); }

    // yield and let all workers get to the beginx3
    // condition variable, waiting--we have to hold them
    // so they don't all see empty work queues right away
    if( sched_yield() == -1 ) { printf( "Error thread trying to yield.\n" ); exit( -1 ); }
  }
}


/*
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
*/

void workScheduleSubmit( void* workUnit ) {
  /*
   while(1){
      if(pthread_mutex_trylock(&systemLock)==0){
	 addNewItemBack( dequeWorkUnits, workUnit );
	 break;
      }
    }
    pthread_mutex_unlock( &systemLock );
  */
  struct QI* item=RUNMALLOC(sizeof(struct QI));
  item->value=workUnit;
  item->next=NULL;
  
  pthread_mutex_lock  ( &systemLockIn );
  tailqi->next=item;
  tailqi=item;
  pthread_mutex_unlock( &systemLockIn );
}


// really should be named "wait until work is finished"
void workScheduleBegin() {
  
  int i;  
  WorkerData *workerData = RUNMALLOC( sizeof( WorkerData ) );
  workerData->id=1;
  // workerMain(NULL);
  workerMain(workerData);

  // tell all workers to begin
  for( i = 0; i < numWorkers; ++i ) {
    //pthread_join( workerArray[i], NULL );
    pthread_join( workerDataArray[i].workerThread, NULL );
  }
}
