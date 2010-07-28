#ifndef QUEUE_RCR_H_
#define QUEUE_RCR_H_

#define SIZE 16384

struct RCRQueue {
  //Size is a power of 2
  void * elements[SIZE];
  unsigned int head;
  unsigned int tail;
  unsigned int size;
};

int enqueueRCRQueue(void * ptr);
void * dequeueRCRQueue();
void resetRCRQueue();
int isEmptyRCRQueue();
int getSizeRCRQueue();

#endif
