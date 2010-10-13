#include "trqueue.h"
#include "stdlib.h"
#include "stdio.h"

//0 would mean sucess
//1 would mean fail
//since if we reach SIZE, we will stop operation, it doesn't matter
//that we overwrite the element in the queue
void enqueueTR(trQueue *q, void * ptr) {
  unsigned int head=q->head+1;
  if (head&TRSIZE)
    head=0;

  while (head==q->tail)
    sched_yield();
  
  q->elements[head] = ptr;
  BARRIER();
  q->head=head;
  return 0;
}

void * dequeueTR(trQueue *q) {
  unsigned int tail=q->tail;
  if(q->head==tail)
    return NULL;

  void * ptr = q->elements[tail];
  tail++;
  if(tail & TRSIZE)
    tail =  0;
  q->tail=tail;
  return ptr;
}

struct trQueue * allocTR() {
  struct trQueue *ptr=malloc(sizeof(struct trQueue));
  ptr->head=0;
  ptr->tail=0;
  return ptr;
}

