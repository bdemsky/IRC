#include "Queue_RCR.h"
#include "stdlib.h"
#include "stdio.h"

__thread struct RCRQueue myRCRQueue;

void resetRCRQueue() {
  myRCRQueue.head = 0;
  myRCRQueue.tail = 0;
}

//0 would mean success
//1 would mean fail
int enqueueRCRQueue(void * ptr, int traverserState) {
  unsigned int oldhead=myRCRQueue.head;
  unsigned int head=oldhead+1;
  if (head&SIZE)
    head=0;

  if (head==myRCRQueue.tail)
    return 1;

  myRCRQueue.elements[oldhead].object = ptr;
  myRCRQueue.elements[oldhead].traverserState = traverserState;
  myRCRQueue.head=head;

  return 0;
}

RCRQueueEntry * dequeueRCRQueue() {
  unsigned int tail=myRCRQueue.tail;
  if(myRCRQueue.head==tail)
    return NULL;
  RCRQueueEntry * ptr = &myRCRQueue.elements[tail];
  tail++;
  if (tail & SIZE)
    tail=0;
  myRCRQueue.tail=tail;
  return ptr;
}

int isEmptyRCRQueue() {
  return myRCRQueue.head==myRCRQueue.tail;
}


