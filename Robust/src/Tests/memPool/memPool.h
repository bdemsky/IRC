#ifndef ___MEMPOOL_H__
#define ___MEMPOOL_H__

#include <stdlib.h>
#include "mlp_lock.h"


// A memory pool implements POOLALLOCATE and POOLFREE
// to improve memory allocation by reusing records.
// 


typedef struct MemPool_t {
  int itemSize;

  int poolSize;
  INTPTR* ringBuffer;

  int head;
  int tail;
} MemPool;



MemPool* poolcreate( int itemSize, int poolSize ) {
  MemPool* p = malloc( sizeof( MemPool ) );
  p->itemSize = itemSize;
  p->poolSize = poolSize;
  p->ringBuffer = malloc( poolSize * sizeof( INTPTR ) );
  p->head = 0;
  p->tail = 0;
}


// CAS
// in: a ptr, expected old, desired new
// return: actual old
//
// Pass in a ptr, what you expect the old value is and
// what you want the new value to be.
// The CAS returns what the value is actually: if it matches
// your proposed old value then you assume the update was successful,
// otherwise someone did CAS before you, so try again (the return
// value is the old value you will pass next time.)


static inline void* poolalloc( MemPool* p ) {
  int headNow;
  int headNext;
  int headActual;
  
  while( 1 ) {
    // if there are no free items available, just do regular
    // allocation and move on--ok to use unnsynched reads 
    // for this test, its conservative
    if( p->head == p->tail ) {
      return calloc( 1, p->itemSize );
    }

    // otherwise, attempt to grab a free item from the
    // front of the free list
    headNow  = p->head;
    headNext = headNow + 1; 
    if( headNext == p->poolSize ) {
      headNext = 0;
    }

    headActual = CAS( &(p->head), headNow, headNext );
    if( headActual == headNow ) {
      // can't some other pool accesses happen during
      // this time, before return???
      return (void*) p->ringBuffer[headActual];
    }

    // if CAS failed, retry entire operation
  }
}


static inline void poolfree( MemPool* p, void* ptr ) {
  int tailNow;
  int tailNext;
  int tailActual;

  while( 1 ) {
    // if the ring buffer is full, just do regular free, ok to 
    // use unsyhcronized reads for this test, its conservative
    if( p->tail + 1 == p->head ||
        (  p->tail == p->poolSize - 1 && 
           p->head == 0
           ) 
        ) {
      free( ptr );
      return;
    }

    // otherwise, attempt to grab add the free item to the
    // end of the free list
    tailNow  = p->tail;
    tailNext = tailNow + 1; 
    if( tailNext == p->poolSize ) {
      tailNext = 0;
    }

    tailActual = CAS( &(p->tail), tailNow, tailNext );
    if( tailActual == tailNow ) {
      // can't some other pool accesses happen during
      // this time, before return???
      p->ringBuffer[tailActual] = (INTPTR)ptr;
      return;
    }

    // if CAS failed, retry entire operation
  }
}



#endif // ___MEMPOOL_H__










