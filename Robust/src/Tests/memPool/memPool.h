#ifndef ___MEMPOOL_H__
#define ___MEMPOOL_H__

//////////////////////////////////////////////////////////
//
//  A memory pool implements POOLCREATE, POOLALLOC and 
//  POOLFREE to improve memory allocation by reusing records.
//
//  EACH THREAD should have one local pool.
//
//  
// 
//  This implementation uses a lock-free singly-linked list
//  to store reusable records.  The algorithm is a much-
//  simplified version of the list described in Valois '95
//  because it supports less features.
//
//  poolfree adds newly freed records to the list FRONT
//
//  poolalloc either takes records from BACK or mallocs
//
//  Note the use of dummy nodes between every valid list
//  element.  This is a crucial aspect in allowing the
//  implementation to have simple CAS logic.
//
//  Empty list:
//  dummyItem -->     1stPTR --> NULL
//  head --> tail --> 2ndPTR --> 1stPTR
//
//  Prepare a new record to add at head:
//  Record --> 1stPTR --> currentHead
//             2ndPTR --> 1stPTR
//  CAS( &head,
//       head,
//       2ndPtr )
//
//  Remove a record from tail:
//  
//
//
//
//
//////////////////////////////////////////////////////////

#include <stdlib.h>
#include "mlp_lock.h"


typedef struct MemPoolItem_t {
  void* next;
} MemPoolItem;


typedef struct MemPool_t {
  int itemSize;
  MemPoolItem* head;

  // avoid cache line contention between producer/consumer...
  char buffer[CACHELINESIZE - sizeof(void*)];

  MemPoolItem* tail;
} MemPool;


// the memory pool must always have at least one
// item in it
MemPool* poolcreate( int itemSize ) {
  MemPool* p    = malloc( sizeof( MemPool ) );
  p->itemSize   = itemSize;
  p->head       = malloc( itemSize );
  p->head->next = NULL;
  p->tail       = p->head;
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

static inline void poolfree( MemPool* p, void* ptr ) {

  MemPoolItem* tailCurrent;
  MemPoolItem* tailActual;
  
  // set up the now unneeded record to as the tail of the
  // free list by treating its first bytes as next pointer,
  MemPoolItem* tailNew = (MemPoolItem*) ptr;
  newTail->next = NULL;

  while( 1 ) {
    // make sure the null happens before the insertion,
    // also makes sure that we reload tailCurrent, etc..
    BARRIER();

    tailCurrent = p->tail;
    tailActual  = CAS( &(p->tail),  // ptr to set
                       tailCurrent, // current tail's next should be NULL
                       tailNew );   // try set to our new tail
    if( tailActual == tailCurrent ) {
      // success, update tail
      tailCurrent->next = newTail;
      return;
    }

    // if CAS failed, retry entire operation
  }
}


static inline void* poolalloc( MemPool* p ) {

  // to protect CAS in poolfree from dereferencing
  // null, treat the queue as empty when there is
  // only one item.  The dequeue operation is only
  // executed by the thread that owns the pool, so
  // it doesn't require an atomic op
  MemPoolItem* headCurrent = p->head;

  if( headCurrent->next == NULL ) {
    // only one item, so don't take from pool
    return calloc( 1, p->itemSize );
  }
 
  p->head = headCurrent->next;
  return headCurrent;
}


#endif // ___MEMPOOL_H__










