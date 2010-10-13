#include "Queue_RCR.h"
#include "stdlib.h"
#include "stdio.h"

__thread struct RCRQueue myRCRQueue;

void resetRCRQueue() {
  myRCRQueue.head = 0;
  myRCRQueue.tail = 0;
}

//0 would mean sucess
//1 would mean fail
//since if we reach SIZE, we will stop operation, it doesn't matter
//that we overwrite the element in the queue
int enqueueRCRQueue(void * ptr) {
  unsigned int head=myRCRQueue.head+1;
  if (head&SIZE)
    head=0;

  if (head==myRCRQueue.tail)
    return 1;
  
  myRCRQueue.elements[head] = ptr;
  myRCRQueue.head=head;
  return 0;
}

void * dequeueRCRQueue() {
  if(myRCRQueue.head==myRCRQueue.tail)
    return NULL;
  unsigned int tail=myRCRQueue.tail;
  void * ptr = myRCRQueue.elements[tail];
  tail++;
  if(tail & SIZE)
    tail =  0;
  myRXRQueue.tail=tail;
  return ptr;
}

int isEmptyRCRQueue() {
  return myRCRQueue.head=myRCRQueue.tail;
}


