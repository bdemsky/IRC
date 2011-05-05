#ifndef BAMBOO_MULTICORE_GC_COMPACT_H
#define BAMBOO_MULTICORE_GC_COMPACT_H

#ifdef MULTICORE_GC
#include "multicore.h"

struct moveHelper {
  unsigned int numblocks;       // block num for heap
  unsigned int base;       // base virtual address of current heap block
  unsigned int ptr;       // virtual address of current heap top
  unsigned int offset;       // offset in current heap block
  unsigned int blockbase;   // virtual address of current small block to check
  unsigned int blockbound;     // bound virtual address of current small blcok
  unsigned int sblockindex;       // index of the small blocks
  unsigned int top;       // real size of current heap block to check
  unsigned int bound;       // bound size of current heap block to check
};

// compute the remaining size of block #b
// p--relative position to the bottom of the shared heap
#define GC_BLOCK_REMAIN_SIZE(b, p) \
  ((b)<NUMCORES4GC)?((BAMBOO_SMEM_SIZE_L)-((p)%(BAMBOO_SMEM_SIZE_L))):((BAMBOO_SMEM_SIZE)-((p)%(BAMBOO_SMEM_SIZE)))

INLINE bool gcfindSpareMem_I(unsigned int * startaddr,
                             unsigned int * tomove,
                             unsigned int * dstcore,
                             unsigned int requiredmem,
                             unsigned int requiredcore);
INLINE void compact();
INLINE void compact_master(struct moveHelper * orig,
                           struct moveHelper * to);
#endif // MULTICORE_GC

#endif // BAMBOO_MULTICORE_GC_COMPACT_H
