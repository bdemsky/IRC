#ifndef ___MEMPOOL_H__
#define ___MEMPOOL_H__

//////////////////////////////////////////////////////////
//
//  A memory pool implements POOLCREATE, POOLALLOC and 
//  POOLFREE to improve memory allocation by reusing records.
//
//  This implementation uses a lock-free singly-linked list
//  to store reusable records.  The list is initialized with
//  one valid record, and the list is considered empty when
//  it has only one record; this allows the enqueue operation's
//  CAS to assume tail can always be dereferenced.
//
//  poolfree adds newly freed records to the list BACK
//
//  poolalloc either takes records from FRONT or mallocs
//
//////////////////////////////////////////////////////////

#include <stdlib.h>
#include "runtime.h"
#include "mem.h"
#include "mlp_lock.h"


#define CACHELINESIZE 64


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
static MemPool* poolcreate( int itemSize ) {
  MemPool* p    = calloc( 1, sizeof( MemPool ) );
  p->itemSize   = itemSize;
  p->head       = calloc( 1, itemSize );
  p->head->next = NULL;
  p->tail       = p->head;
  return p;
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

static inline void poolfreeinto( MemPool* p, void* ptr ) {

  MemPoolItem* tailCurrent;
  MemPoolItem* tailActual;
  
  // set up the now unneeded record to as the tail of the
  // free list by treating its first bytes as next pointer,
  MemPoolItem* tailNew = (MemPoolItem*) ptr;
  tailNew->next = NULL;

  while( 1 ) {
    // make sure the null happens before the insertion,
    // also makes sure that we reload tailCurrent, etc..
    BARRIER();

    tailCurrent = p->tail;
    tailActual = (MemPoolItem*)
      CAS( &(p->tail),         // ptr to set
           (long) tailCurrent, // current tail's next should be NULL
           (long) tailNew      // try set to our new tail
           );   
    if( tailActual == tailCurrent ) {
      // success, update tail
      tailCurrent->next = tailNew;
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
  MemPoolItem* next=headCurrent->next;
  int i;
  if(next == NULL) {
    // only one item, so don't take from pool
    return (void*) RUNMALLOC( p->itemSize );
  }
 
  p->head = next;

  //////////////////////////////////////////////////////////
  //
  //
  //  static inline void prefetch(void *x) 
  //  { 
  //    asm volatile("prefetcht0 %0" :: "m" (*(unsigned long *)x));
  //  } 
  //
  //
  //  but this built-in gcc one seems the most portable:
  //////////////////////////////////////////////////////////
  //__builtin_prefetch( &(p->head->next) );
  asm volatile( "prefetcht0 (%0)" :: "r" (next));
  next=(MemPoolItem*)(((char *)next)+CACHELINESIZE);
  asm volatile( "prefetcht0 (%0)" :: "r" (next));

  return (void*)headCurrent;
}


static void pooldestroy( MemPool* p ) {
  MemPoolItem* i = p->head;
  MemPoolItem* n;

  while( i != NULL ) {
    n = i->next;
    free( i );
    i = n;
  }

  free( p );
}


#endif // ___MEMPOOL_H__










