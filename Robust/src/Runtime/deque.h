#ifndef ___DEQUE_H__
#define ___DEQUE_H__

#include "runtime.h"
#include "memPool.h"


// the bottom and top 64-bit values encode
// several sub-values, see deque.c
typedef struct deque_t {
  MemPool* memPool;
  INTPTR   bottom;

  // force bottom and top to different cache lines
  char buffer[CACHELINESIZE];

  INTPTR top;
} deque;


void  dqInit      ( deque* dq );
void  dqPushBottom( deque* dq, void* item );
void* dqPopTop    ( deque* dq );
void* dqPopBottom ( deque* dq );


// pop operations may return these values
// instead of an item
extern void* DQ_POP_EMPTY;
extern void* DQ_POP_ABORT;


//void dq_take ( deque* sem, struct garbagelist* gl );


#endif // ___DEQUE_H__
