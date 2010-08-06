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

void* workerMain( void* arg ) {
  void* workUnit;
  WorkerData* myData = (WorkerData*) arg;
  //Start profiler
  CREATEPROFILER();
  
  oid=myData->id;
  // make sure init mlp once-per-thread stuff

  // all workers wait until system is ready

  // then continue to process work
  while( 1 ) {
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
  EXITPROFILER();
  return NULL;
}

void workScheduleInit( int numProcessors,
                       void(*func)(void*) ) {
  int i, status;
  CREATEPROFILER();
  pthread_mutex_init(&gclock, NULL);
  pthread_mutex_init(&gclistlock, NULL);
  pthread_cond_init(&gccond, NULL);

  numWorkers = numProcessors + 1;

  workFunc   = func;

  headqi=tailqi=RUNMALLOC(sizeof(struct QI));
  headqi->next=NULL;
  
  status = pthread_mutex_init( &systemLockIn, NULL );
  status = pthread_mutex_init( &systemLockOut, NULL );

  workerDataArray = RUNMALLOC( sizeof( WorkerData ) * numWorkers );

  for( i = 0; i < numWorkers; ++i ) {   
    workerDataArray[i].id=i+2;
    status = pthread_create( &(workerDataArray[i].workerThread), 
                             NULL,
                             workerMain,
                             (void*) &(workerDataArray[i])
                           );
    if( status != 0 ) { printf( "Error\n" ); exit( -1 ); }

    // yield and let all workers get to the beginx3
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


// really should be named "wait until work is finished"
void workScheduleBegin() {
  int i;  
  WorkerData *workerData = RUNMALLOC( sizeof( WorkerData ) );
  workerData->id=1;
  workerMain(workerData);

  // tell all workers to begin
  for( i = 0; i < numWorkers; ++i ) {
    pthread_join( workerDataArray[i].workerThread, NULL );
  }
}
