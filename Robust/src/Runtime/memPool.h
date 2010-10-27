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

#ifdef MEMPOOL_DETECT_MISUSE
#include <stdio.h>
#include <sys/mman.h>
#include <unistd.h>
#include <errno.h>
#include <string.h>
static INTPTR pageSize;
#endif

#include "runtime.h"
#include "mem.h"
#include "mlp_lock.h"


#define CACHELINESIZE 64



typedef struct MemPoolItem_t {
  void* next;
} MemPoolItem;


typedef struct MemPool_t {
  int itemSize;

  // only invoke this on items that are
  // actually new, saves time for reused
  // items
  void(*initFreshlyAllocated)(void*);

#ifdef MEMPOOL_DETECT_MISUSE
  int allocSize;
#else
  //normal version
  MemPoolItem* head;
  // avoid cache line contention between producer/consumer...
  char buffer[CACHELINESIZE];
  MemPoolItem* tail;
#endif
} MemPool;


// the memory pool must always have at least one
// item in it
static MemPool* poolcreate( int itemSize, 
                            void(*initializer)(void*) 
                            ) {

  MemPool* p  = RUNMALLOC( sizeof( MemPool ) );
  p->itemSize = itemSize;
  
  p->initFreshlyAllocated = initializer;

#ifdef MEMPOOL_DETECT_MISUSE
  // when detecting misuse, round the item size
  // up to a page and add a page, so whatever
  // allocated memory you get, you can use a
  // page-aligned subset as the record  
  pageSize = sysconf( _SC_PAGESIZE );

  if( itemSize % pageSize == 0 ) {
    // if the item size is already an exact multiple
    // of the page size, just increase by one page
    p->allocSize = itemSize + pageSize;
  } else {
    // otherwise, round down to a page size, then add two
    p->allocSize = (itemSize & ~(pageSize-1)) + 2*pageSize;
  }
#else

  // normal version
  p->head = RUNMALLOC( p->itemSize );

  if( p->initFreshlyAllocated != NULL ) {
    p->initFreshlyAllocated( p->head );
  }

  p->head->next = NULL;
  p->tail       = p->head;
#endif

  return p;
}



#ifdef MEMPOOL_DETECT_MISUSE

static inline void poolfreeinto( MemPool* p, void* ptr ) {
  // don't actually return memory to the pool, just lock
  // it up tight so first code to touch it badly gets caught
  // also, mprotect automatically protects full pages
  if( mprotect( ptr, p->itemSize, PROT_NONE ) != 0 ) {
    printf( "mprotect failed, %s.\n", strerror( errno ) );
    exit( -1 );
  }
}

#else

// normal version
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
           (INTPTR) tailCurrent, // current tail's next should be NULL
           (INTPTR) tailNew      // try set to our new tail
           );   
    if( tailActual == tailCurrent ) {
      // success, update tail
      tailCurrent->next = tailNew;
      return;
    }

    // if CAS failed, retry entire operation
  }
}
#endif



#ifdef MEMPOOL_DETECT_MISUSE

static inline void* poolalloc( MemPool* p ) {
  // put the memory we intend to expose to client
  // on a page-aligned boundary, always return
  // new memory
  INTPTR nonAligned = (INTPTR) RUNMALLOC( p->allocSize );

  void* newRec = (void*)((nonAligned + pageSize-1) & ~(pageSize-1));

  if( p->initFreshlyAllocated != NULL ) {
    p->initFreshlyAllocated( newRec );
  }

  return newRec;
}

#else

// normal version
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
    void* newRec = RUNMALLOC( p->itemSize );

    if( p->initFreshlyAllocated != NULL ) {
      p->initFreshlyAllocated( newRec );
    }

    return newRec;
  }
 
  p->head = next;

  asm volatile( "prefetcht0 (%0)" :: "r" (next));
  next=(MemPoolItem*)(((char *)next)+CACHELINESIZE);
  asm volatile( "prefetcht0 (%0)" :: "r" (next));

  return (void*)headCurrent;
}
#endif



static void pooldestroy( MemPool* p ) {

#ifndef MEMPOOL_DETECT_MISUSE
  MemPoolItem* i = p->head;
  MemPoolItem* n;

  while( i != NULL ) {
    n = i->next;
    free( i );
    i = n;
  }
#endif

  free( p );
}


#endif // ___MEMPOOL_H__










