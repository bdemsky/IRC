#ifndef BAMBOO_MULTICORE_GC_H
#define BAMBOO_MULTICORE_GC_H

struct garbagelist {
  int size;
  struct garbagelist *next;
  void * array[];
};

void * gctopva; // top va for shared memory without reserved sblocks
void * gcbaseva; // base va for shared memory without reserved sblocks
void * incoherentbaseva;

unsigned long numGCs;
unsigned long long GCtime;

#endif // BAMBOO_MULTICORE_GC_H
