////////////////////////////////////////////////////////////////
//
//  This is an implementation of the structure described in
//  A Dynamic-Sized Nonblocking Work Stealing Deque
//  Hendler, Lev, Moir, and Shavit
//   
//  The bottom and top values for the deque must be CAS-able
//  and fit into 64 bits.  Our strategy for this is:
//  
//    19-bit Tag    36-bit Node Pointer     9-bit Index
//   +-----------+-------------------------+------------+
//   | 63 ... 45 | 44 ...                9 | 8 ...    0 |
//   +-----------+-------------------------+------------+
//
//  Let's call the encoded info E.  To retrieve the values:  
//    tag = (0xffffe00000000000 & E) >> 45;
//    ptr = (0x00001ffffffffe00 & E) <<  3;
//    idx = (0x00000000000001ff & E);
//
//  Increment the tag without decrypting:
//    E = (0x00001fffffffffff | E) + 1;
//
//  Increment (decrement) the index when it is not equal to
//  MAXINDEX (0) with E++ (E--).
//
//  x86 64-bit processors currently only use the lowest 48 bits for
//  virtual addresses, source:
//  http://en.wikipedia.org/wiki/X86-64#Virtual_address_space_details
//  And 64-bit addresses are 2^3=8 byte aligned, so the lower 3 bits
//  of a 64-bit pointer are always zero.  This means if we are only
//  alloted 36 bits to store a pointer to a Node we have 
//  48 - 3 - 36 = 9 bits that could be lost.  Instead of aligning Node
//  pointers to 8 bytes we can align them to 2^(3+9)=4096 bytes and be
//  sure the lower 12 bits of the address are zero.  THEREFORE:
//  Nodes must be 4096-byte aligned so the lower 12 bits are zeroes and
//  we can ecnode the rest in 36 bits without a loss of information.  
//
////////////////////////////////////////////////////////////////

#ifdef DEBUG_DEQUE
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#endif

#include "deque.h"


void* DQ_POP_EMPTY = (void*)0x17;
void* DQ_POP_ABORT = (void*)0x33;


// define a 19-bit dummy tag for the bottom
// value with a pattern that will expose errors
#define BOTTOM_NULL_TAG 0x40001



// the dequeNode struct must be 4096-byte aligned, 
// see above, so use the following magic to ask
// the allocator for a space that wastes 4095 bytes
// but gaurantees the address of the struct within
// that space is 4096-aligned
const INTPTR DQNODE_SIZETOREQUEST = sizeof( dequeNode ) + 4095;

static inline dequeNode* dqGet4096aligned( void* fromAllocator ) { 
  INTPTR aligned = ((INTPTR)fromAllocator + 4095) & (~4095);

#ifdef DEBUG_DEQUE
  //printf( "from allocator: 0x%08x to 0x%08x\n", (INTPTR)fromAllocator, (INTPTR)fromAllocator + DQNODE_SIZETOREQUEST );
  //printf( "aligned:        0x%08x to 0x%08x\n", aligned,               aligned               + sizeof( dequeNode )  );
  memset( (void*) aligned, 0x9, sizeof( dequeNode ) );
#endif

  return (dequeNode*) aligned;
}


static inline INTPTR dqEncode( int tag, dequeNode* ptr, int idx ) {
  INTPTR ptrE = (0x00001ffffffffe00 &  // second, mask off the addr's high-order 1's
                 (((INTPTR)ptr) >> 3)); // first, shift down 8-byte alignment bits

  INTPTR E =
    (((INTPTR)tag) << 45) |
    (ptrE)                |
    ((INTPTR)idx);
#ifdef DEBUG_DEQUE
  int tagOut = dqDecodeTag( E ); 
  if( tag != tagOut ) { printf( "Lost tag information.\n" ); exit( -1 ); }

  dequeNode* ptrOut = dqDecodePtr( E );
  if( ptr != ptrOut ) { printf( "Lost ptr information.\n" ); exit( -1 ); }

  int idxOut = dqDecodeIdx( E );
  if( idx != idxOut ) { printf( "Lost idx information.\n" ); exit( -1 ); }
#endif
  return E;
}


static inline int dqIndicateEmpty( INTPTR bottom, INTPTR top ) {
  dequeNode* botNode = dqDecodePtr( bottom );
  int        botIndx = dqDecodeIdx( bottom );
  dequeNode* topNode = dqDecodePtr( top );
  int        topIndx = dqDecodeIdx( top );  

  if( (botNode == topNode) &&
      (botIndx == topIndx || botIndx == (topIndx+1))
      ) {
    return 1;
  }

  if( (botNode == topNode->next) &&
      (botIndx == 0)             &&
      (topIndx == DQNODE_ARRAYSIZE - 1)
      ) {
    return 1;
  }

  return 0;
}



void dqInit( deque* dq ) {

  dq->memPool = poolcreate( DQNODE_SIZETOREQUEST, NULL );

  dequeNode* a = dqGet4096aligned( poolalloc( dq->memPool ) );
  dequeNode* b = dqGet4096aligned( poolalloc( dq->memPool ) );
  
  a->next = b;
  b->prev = a;

  dq->bottom = dqEncode( BOTTOM_NULL_TAG, a, DQNODE_ARRAYSIZE - 1 );
  dq->top    = dqEncode( 0,               a, DQNODE_ARRAYSIZE - 1 );
}


void dqPushBottom( deque* dq, void* item ) {

#ifdef DEBUG_DEQUE
  if( item == 0x0 ) {
    printf( "Pushing invalid work into the deque.\n" );
  }
#endif

  dequeNode* currNode = dqDecodePtr( dq->bottom );
  int        currIndx = dqDecodeIdx( dq->bottom );

  currNode->itsDataArr[currIndx] = item;

  dequeNode* newNode;
  int        newIndx;

  if( currIndx != 0 ) {
    newNode = currNode;
    newIndx = currIndx - 1;

  } else {
    newNode        = dqGet4096aligned( poolalloc( dq->memPool ) );
    newNode->next  = currNode;
    currNode->prev = newNode;
    newIndx        = DQNODE_ARRAYSIZE - 1;
  }

  dq->bottom = dqEncode( BOTTOM_NULL_TAG, newNode, newIndx );
}


void* dqPopTop( deque* dq ) {

  INTPTR currTop = dq->top;

  int        currTopTag  = dqDecodeTag( currTop );
  dequeNode* currTopNode = dqDecodePtr( currTop );
  int        currTopIndx = dqDecodeIdx( currTop );


  // read of top followed by read of bottom, algorithm
  // says specifically must be in this order
  BARRIER();
  
  INTPTR currBottom = dq->bottom;

  if( dqIndicateEmpty( currBottom, currTop ) ) {
    if( currTop == dq->top ) {
      return DQ_POP_EMPTY;
    } else {
      return DQ_POP_ABORT;
    }
  }

  dequeNode* nodeToFree;
  int        newTopTag;
  dequeNode* newTopNode;
  int        newTopIndx;

  if( currTopIndx != 0 ) {
    nodeToFree = NULL;
    newTopTag  = currTopTag;
    newTopNode = currTopNode;
    newTopIndx = currTopIndx - 1;

  } else {
    nodeToFree = currTopNode->next;
    newTopTag  = currTopTag + 1;
    newTopNode = currTopNode->prev;
    newTopIndx = DQNODE_ARRAYSIZE - 1;
  }

  void* retVal = currTopNode->itsDataArr[currTopIndx];

  INTPTR newTop = dqEncode( newTopTag, newTopNode, newTopIndx );

  // algorithm states above should happen
  // before attempting the CAS
  BARRIER();

  INTPTR actualTop = (INTPTR)
    CAS( &(dq->top), // location
         currTop,    // expected value
         newTop );   // desired value

  if( actualTop == currTop ) {
    // CAS succeeded
    if( nodeToFree != NULL ) {
      poolfreeinto( dq->memPool, nodeToFree );
    }
    return retVal;

  } else {
    return DQ_POP_ABORT;
  }
}


void* dqPopBottom ( deque* dq ) {

  INTPTR oldBot = dq->bottom;

  dequeNode* oldBotNode = dqDecodePtr( oldBot );
  int        oldBotIndx = dqDecodeIdx( oldBot );
  
  dequeNode* newBotNode;
  int        newBotIndx;

  if( oldBotIndx != DQNODE_ARRAYSIZE - 1 ) {
    newBotNode = oldBotNode;
    newBotIndx = oldBotIndx + 1;

  } else {
    newBotNode = oldBotNode->next;
    newBotIndx = 0;
  }

  void* retVal = newBotNode->itsDataArr[newBotIndx];

  dq->bottom = dqEncode( BOTTOM_NULL_TAG, newBotNode, newBotIndx );

  // algorithm states above should happen
  // before attempting the CAS
  BARRIER();

  INTPTR currTop = dq->top;

  int        currTopTag  = dqDecodeTag( currTop );
  dequeNode* currTopNode = dqDecodePtr( currTop );
  int        currTopIndx = dqDecodeIdx( currTop );

  if( oldBotNode == currTopNode &&
      oldBotIndx == currTopIndx ) {
    dq->bottom = dqEncode( BOTTOM_NULL_TAG, oldBotNode, oldBotIndx );
    return DQ_POP_EMPTY;

  } else if( newBotNode == currTopNode &&
             newBotIndx == currTopIndx ) {
    INTPTR newTop = dqEncode( currTopTag + 1, currTopNode, currTopIndx );

    INTPTR actualTop = (INTPTR)
      CAS( &(dq->top), // location
           currTop,    // expected value
           newTop );   // desired value

    if( actualTop == currTop ) {
      // CAS succeeded
      if( oldBotNode != newBotNode ) {
        poolfreeinto( dq->memPool, oldBotNode );
      }
      return retVal;
      
    } else {
      dq->bottom = dqEncode( BOTTOM_NULL_TAG, oldBotNode, oldBotIndx );      
      return DQ_POP_EMPTY;
    }
    
  } else {
    if( oldBotNode != newBotNode ) {
      poolfreeinto( dq->memPool, oldBotNode );
    }
    return retVal;    
  }
}
