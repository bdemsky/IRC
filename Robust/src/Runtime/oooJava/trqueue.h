#ifndef TRQUEUE_H_
#define TRQUEUE_H_

//NOTE: SIZE MUST BE A POWER OF TWO;
//SIZE is used as mask to check overflow
#define TRSIZE 16384

struct trQueue {
  void * elements[TRSIZE];
  volatile unsigned int head;
  char buffer[60];//buffer us to the next cache line
  volatile unsigned int tail;
  struct trQueue *next;
  struct Hashtable_rcr ** allHashStructures;
  int id;
};

void enqueueTR(struct trQueue *, void * ptr);
void * dequeueTR(struct trQueue *);
void createTR();
void returnTR();
#endif
