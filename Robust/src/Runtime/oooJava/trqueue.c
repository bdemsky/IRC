#define _GNU_SOURCE
#include "trqueue.h"
#include "stdlib.h"
#include "stdio.h"
#include <sched.h>
#include "mlp_lock.h"
#include <pthread.h>
#include "structdefs.h"
#include "RuntimeConflictResolver.h"

extern volatile int numWorkSchedWorkers;

struct trQueue * queuelist=NULL;
pthread_mutex_t queuelock;

//0 would mean sucess
//1 would mean fail
//since if we reach SIZE, we will stop operation, it doesn't matter
//that we overwrite the element in the queue
void enqueueTR(struct trQueue *q, void * ptr) {
  unsigned int head=q->head+1;
  if (head&TRSIZE)
    head=0;

  while (head==q->tail)
    sched_yield();
  
  q->elements[head] = ptr;
  BARRIER();
  q->head=head;
}

void * dequeueTR(struct trQueue *q) {
  unsigned int tail=q->tail;
  if(q->head==tail)
    return NULL;

  tail++;
  if(tail & TRSIZE)
    tail =  0;

  void * ptr = q->elements[tail];
  q->tail=tail;
  return ptr;
}

void createTR() {
  struct trQueue *ptr=NULL;
  int myid;
  pthread_mutex_lock(&queuelock);
  ptr=queuelist;
  if (ptr!=NULL) {
    queuelist=ptr->next;
  } else {
    myid=numWorkSchedWorkers;
    numWorkSchedWorkers++;
  }  
  pthread_mutex_unlock(&queuelock);
  if (ptr==NULL) {
    pthread_t thread;
    pthread_attr_t nattr;
    pthread_attr_init(&nattr);
    pthread_attr_setdetachstate( &nattr, PTHREAD_CREATE_DETACHED);
    ptr=malloc(sizeof(struct trQueue));
    ptr->head=0;
    ptr->tail=0;
    ptr->id=myid;
#if COREPIN
    cpu_set_t cpuset;    
    CPU_ZERO(&cpuset);
    CPU_SET(myid, &cpuset);
    pthread_attr_setaffinity_np(&nattr, sizeof(cpuset), &cpuset);    
    printf("assign workerTR to core %d\n",myid);
#endif
    ptr->allHashStructures=createAndFillMasterHashStructureArray();
    int status=pthread_create( &thread, &nattr, workerTR, (void *) ptr);
    //    int status=pthread_create( &thread, NULL, workerTR, (void *) ptr);
    if (status!=0) {printf("ERROR\n");exit(-1);}
    pthread_attr_destroy(&nattr);
  }
  TRqueue=ptr;
}

void returnTR() {
  //return worker thread to pool
  pthread_mutex_lock(&queuelock);
  TRqueue->next=queuelist;
  queuelist=TRqueue;
  pthread_mutex_unlock(&queuelock);
  //release our worker thread
  TRqueue=NULL;
}
