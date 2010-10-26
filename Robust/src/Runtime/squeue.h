#ifndef ___MYQUE_H__
#define ___MYQUE_H__

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
#include "memPool.h"

#define CACHELINESIZE 64
#define DQ_POP_EMPTY NULL
#define DQ_POP_ABORT NULL


typedef struct dequeItem_t {
  void *otherqueue;
  struct dequeItem_t * next;
  volatile void *work;
} dequeItem;

typedef struct deque_t {
  dequeItem* head;

  // avoid cache line contention between producer/consumer...
  char buffer[CACHELINESIZE - sizeof(void*)];

  dequeItem* tail;
  
  MemPool objret;
} deque;

#define EXTRACTPTR(x) (x&0x0000ffffffffffff)
#define INCREMENTTAG     0x0001000000000000

// the memory pool must always have at least one
// item in it
static void dqInit(deque *q) {
  q->head       = calloc( 1, sizeof(dequeItem) );
  q->head->next = NULL;
  q->tail       = q->head;
  q->objret.itemSize=sizeof(dequeItem);
  q->objret.head=calloc(1, sizeof(dequeItem));
  q->objret.head->next=NULL;
  q->objret.tail=q->objret.head;
}

static inline void tagpoolfreeinto( MemPool* p, void* ptr, void *realptr ) {
  MemPoolItem* tailCurrent;
  MemPoolItem* tailActual;
  
  // set up the now unneeded record to as the tail of the
  // free list by treating its first bytes as next pointer,
  MemPoolItem* tailNew = (MemPoolItem*) realptr;
  tailNew->next = NULL;

  while( 1 ) {
    // make sure the null happens before the insertion,
    // also makes sure that we reload tailCurrent, etc..
    BARRIER();

    tailCurrent = p->tail;
    tailActual = (MemPoolItem*)
      CAS( &(p->tail),         // ptr to set
           (INTPTR) tailCurrent, // current tail's next should be NULL
           (INTPTR) realptr);  // try set to our new tail
    
    if( tailActual == tailCurrent ) {
      // success, update tail
      tailCurrent->next = (MemPoolItem *) ptr;
      return;
    }

    // if CAS failed, retry entire operation
  }
}

static inline void* tagpoolalloc( MemPool* p ) {

  // to protect CAS in poolfree from dereferencing
  // null, treat the queue as empty when there is
  // only one item.  The dequeue operation is only
  // executed by the thread that owns the pool, so
  // it doesn't require an atomic op
  MemPoolItem* headCurrent = p->head;
  MemPoolItem* realHead=(MemPoolItem *) EXTRACTPTR((INTPTR)headCurrent);
  MemPoolItem* next=realHead->next;
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
  MemPoolItem* realNext=(MemPoolItem *) EXTRACTPTR((INTPTR)next);
  asm volatile( "prefetcht0 (%0)" :: "r" (realNext));
  realNext=(MemPoolItem*)(((char *)realNext)+CACHELINESIZE);
  asm volatile( "prefetcht0 (%0)" :: "r" (realNext));

  return (void*)headCurrent;
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

static inline void dqPushBottom( deque* p, void* work ) {
  dequeItem *ptr=(dequeItem *) tagpoolalloc(&p->objret);
  //  dequeItem *ptr=(dequeItem *) calloc(1,sizeof(dequeItem));
  dequeItem *realptr=(dequeItem *) EXTRACTPTR((INTPTR)ptr);
  ptr=(dequeItem *) (((INTPTR)ptr)+INCREMENTTAG);
  realptr->work=work;
  BARRIER();
  p->tail->next=ptr;
  p->tail=realptr;
}

static inline void* dqPopTop(deque *p) {
  dequeItem *ptr=p->head;
  dequeItem *realptr=(dequeItem *) EXTRACTPTR((INTPTR)ptr);
  dequeItem *next=realptr->next;
  //remove if we can..steal work no matter what
  if (likely(next!=NULL)) {
    if (((dequeItem *)CAS(&(p->head),(INTPTR)ptr, (INTPTR)next))!=ptr)
      return DQ_POP_EMPTY;
    void * item=NULL;
    item=(void *)LOCKXCHG((unsigned INTPTR*) &(realptr->work), (unsigned INTPTR) item);
    realptr->next=NULL;
    BARRIER();
    tagpoolfreeinto(&p->objret,ptr, realptr);
    return item;
  } else {
    void * item=NULL;
    item=(void *) LOCKXCHG((unsigned INTPTR*) &(realptr->work), (unsigned INTPTR) item);
    return item;
  }
}

#define dqPopBottom dqPopTop


#endif // ___MEMPOOL_H__










