#include "Queue_RCR.h"
#include "stdlib.h"
#include "stdio.h"

__thread struct RCRQueue myRCRQueue;

void resetRCRQueue() {
  myRCRQueue.head = 0;
  myRCRQueue.tail = 0;
  myRCRQueue.length = 0;
}

//0 would mean success
//1 would mean fail
int enqueueRCRQueue(void * ptr, int traverserState) {
  if (myRCRQueue.length & SIZE)
      return 1;

  myRCRQueue.length++;
  myRCRQueue.elements[myRCRQueue.head].object = ptr;
  myRCRQueue.elements[myRCRQueue.head].traverserState = traverserState;
  myRCRQueue.head++;

  if (myRCRQueue.head&SIZE)
    myRCRQueue.head=0;


  return 0;
}

RCRQueueEntry * dequeueRCRQueue() {
  if(!myRCRQueue.length)
    return NULL;

  myRCRQueue.length--;
  void * ptr = &myRCRQueue.elements[myRCRQueue.tail++];
  if(myRCRQueue.tail & SIZE)
    myRCRQueue.tail =  0;
  return ptr;
}

int isEmptyRCRQueue() {
  return !myRCRQueue.length;
}


