#ifndef QUEUE_RCR_H_
#define QUEUE_RCR_H_

//NOTE: SIZE MUST BE A POWER OF TWO;
//SIZE is used as mask to check overflow
#define SIZE 16384

typedef struct RCRQueueEntry_t {
  void* object;
  int   traverserState;
} RCRQueueEntry;

struct RCRQueue {
  RCRQueueEntry elements[SIZE];
  unsigned int head;
  unsigned int tail;
  unsigned int length;
};

int enqueueRCRQueue(void * ptr, int traverserState);
RCRQueueEntry * dequeueRCRQueue();
void resetRCRQueue();
int isEmptyRCRQueue();
int getSizeRCRQueue();
#endif
