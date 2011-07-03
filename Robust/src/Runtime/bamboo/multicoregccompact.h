#ifndef BAMBOO_MULTICORE_GC_COMPACT_H
#define BAMBOO_MULTICORE_GC_COMPACT_H

#ifdef MULTICORE_GC
#include "multicore.h"
#include "gctypes.h"

struct moveHelper {
  unsigned int localblocknum;   // local block num for heap
  void * base;             // base virtual address of current heap block
  void * ptr;              // current pointer into block
  void * bound;            // upper bound of current block
#ifdef GC_CACHE_ADAPT
  void * pagebound;        // upper bound of current available page
#endif
};

int gc_countRunningCores();
void initOrig_Dst(struct moveHelper * orig,struct moveHelper * to);
void getSpaceLocally(struct moveHelper *to);
void handleReturnMem_I(unsigned int cnum, void *heaptop);
void useReturnedMem(unsigned int corenum, block_t localblockindex);
void handleReturnMem(unsigned int cnum, void *heaptop);
void getSpaceRemotely(struct moveHelper *to, unsigned int minimumbytes);
void getSpace(struct moveHelper *to, unsigned int minimumbytes);
void * checkNeighbors_I(int corenum, unsigned INTPTR requiredmem, unsigned INTPTR desiredmem);
void * globalSearch_I(unsigned int topblock, unsigned INTPTR requiredmem, unsigned INTPTR desiredmem);
void handleOneMemoryRequest(int core, unsigned int lowestblock);
void handleMemoryRequests_I();
void * gcfindSpareMem_I(unsigned INTPTR requiredmem, unsigned INTPTR desiredmem,unsigned int requiredcore);
void master_compact();
void compacthelper(struct moveHelper * orig,struct moveHelper * to);
void compact();
void compact_master(struct moveHelper * orig, struct moveHelper * to);
#ifdef GC_CACHE_ADAPT
unsigned int compactpages(struct moveHelper * orig,struct moveHelper * to);
#define COMPACTUNITS(o,t) compactpages((o), (t))
#else
unsigned int compactblocks(struct moveHelper * orig,struct moveHelper * to);
#define COMPACTUNITS(o,t) compactblocks((o), (t))
#endif
#endif // MULTICORE_GC

#endif // BAMBOO_MULTICORE_GC_COMPACT_H
