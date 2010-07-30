#include "Queue_RCR.h"
#include "stdlib.h"
#include "stdio.h"

__thread struct RCRQueue myRCRQueue;

void resetRCRQueue()
{
  myRCRQueue.head = 0;
  myRCRQueue.tail = 0;
  myRCRQueue.size = 0;
}


//0 would mean sucess
//1 would mean fail
//since if we reach SIZE, we will stop operation, it doesn't matter
//that we overwrite the element in the queue
int enqueueRCRQueue(void * ptr)
{
  myRCRQueue.elements[myRCRQueue.head++] =  ptr;

  if(myRCRQueue.head & SIZE)
    myRCRQueue.head = 0;

  return myRCRQueue.size++ == SIZE;
}

void * dequeueRCRQueue()
{  
  if(myRCRQueue.size) {
    void * ptr = myRCRQueue.elements[myRCRQueue.tail++];

    if(myRCRQueue.tail & SIZE)
      myRCRQueue.tail =  0;
    

    myRCRQueue.size--;
    return ptr;
  }
  else
    return NULL;
}

int isEmptyRCRQueue()
{
  return !myRCRQueue.size;
}

int getSizeRCRQueue()
{
  return myRCRQueue.size;
}


