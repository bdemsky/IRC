#ifndef PMC_GARBAGE_H
#define PMC_GARBAGE_H
#include <tmc/spin.h>

#define NUMPMCUNITS (4*NUMCORES4GC)
#define UNITSIZE (BAMBOO_SHARED_MEM_SIZE/NUMPMCUNITS)

struct pmc_unit {
  tmc_spin_mutex_t lock;
  unsigned int numbytes;
  unsigned int regionnum;
};

struct pmc_region {
  void * lastptr;
  struct ___Object___ * lastobj;
  struct pmc_queue markqueue;
};

struct pmc_heap {
  struct pmc_region units[NUMPMCUNITS];
  struct pmc_region regions[NUMCORES4GC];
  tmc_spin_mutex_t lock;
  volatile unsigned int numthreads;
};

extern struct pmc_heap * pmc_heapptr;
extern struct pmc_queue * pmc_localqueue;

void incrementthreads();
void decrementthreads();
void pmc_onceInit();
void pmc_init();


#endif
