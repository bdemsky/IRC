#ifndef BAMBOO_MULTICORE_GC_COMPACT_H
#define BAMBOO_MULTICORE_GC_COMPACT_H

#ifdef MULTICORE_GC
#include "multicore.h"

struct moveHelper {
  unsigned int localblocknum;   // local block num for heap
  void * base;             // base virtual address of current heap block
  void * ptr;              // current pointer into block
  void * bound;            // upper bound of current block
};

void initOrig_Dst(struct moveHelper * orig,struct moveHelper * to);
void compacthelper(struct moveHelper * orig,struct moveHelper * to);
unsigned int compactblocks(struct moveHelper * orig,struct moveHelper * to);
void compact();
void compact_master(struct moveHelper * orig, struct moveHelper * to);
#endif // MULTICORE_GC

#endif // BAMBOO_MULTICORE_GC_COMPACT_H
