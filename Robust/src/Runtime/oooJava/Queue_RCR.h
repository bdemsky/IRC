#ifndef QUEUE_RCR_H_
#define QUEUE_RCR_H_

//NOTE: SIZE MUST BE A POWER OF TWO;
//SIZE is used as mask to check overflow
#define SIZE 16384

struct RCRQueue {
  void * elements[SIZE];
  unsigned int head;
  unsigned int tail;
};


typedef struct RCRQueueEntry_t {
  void* object;
  int   traverserState;
} RCRQueueEntry;


int enqueueRCRQueue(void * ptr);
void * dequeueRCRQueue();
void resetRCRQueue();
int isEmptyRCRQueue();
int getSizeRCRQueue();
#endif
