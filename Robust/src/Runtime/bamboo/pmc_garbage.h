#ifndef PMC_GARBAGE_H
#define PMC_GARBAGE_H
#include <tmc/spin.h>

struct pmc_unit {
  tmc_spin_mutex_t lock;
  unsigned int numbytes;
};

struct pmc_region {
  void * lastptr;
  struct ___Object___ * lastobj;
  struct pmc_queue markqueue;
};

struct pmc_heap {
  struct pmc_region units[NUMCORES4GC*4];
  struct pmc_region regions[NUMCORES4GC];
  tmc_spin_mutex_t lock;
  volatile unsigned int numthreads;
};

extern struct pmc_heap * pmc_heapptr;

void incrementthreads();
void decrementthreads() {


#endif
