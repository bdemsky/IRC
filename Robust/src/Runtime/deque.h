#ifndef ___DEQUE_H__
#define ___DEQUE_H__

#include "runtime.h"
#include "memPool.h"



// the bottom and top 64-bit values encode
// several sub-values, see deque.c
typedef struct deque_t {
  MemPool*        memPool;
  volatile INTPTR bottom;

  // force bottom and top to different cache lines
  char buffer[CACHELINESIZE];

  volatile INTPTR top;
} deque;


void  dqInit(deque* dq);
void  dqPushBottom(deque* dq, void* item);
void* dqPopTop(deque* dq);
void* dqPopBottom(deque* dq);


// pop operations may return these values
// instead of an item
extern void* DQ_POP_EMPTY;
extern void* DQ_POP_ABORT;


// there are 9 bits for the index into a Node's array,
// so 2^9 = 512 elements per node of the deque
#define DQNODE_ARRAYSIZE 512


typedef struct dequeNode_t {
  void* itsDataArr[DQNODE_ARRAYSIZE];
  struct dequeNode_t* next;
  struct dequeNode_t* prev;
} dequeNode;


static inline int        dqDecodeTag(INTPTR E) {
  return (int)        ((0xffffe00000000000 & E) >> 45);
}
static inline dequeNode* dqDecodePtr(INTPTR E) {
  return (dequeNode*) ((0x00001ffffffffe00 & E) <<  3);
}
static inline int        dqDecodeIdx(INTPTR E) {
  return (int)        ((0x00000000000001ff & E)      );
}


#endif // ___DEQUE_H__
