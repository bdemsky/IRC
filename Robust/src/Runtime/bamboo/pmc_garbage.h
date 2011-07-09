#ifndef PMC_GARBAGE_H
#define PMC_GARBAGE_H
#include <tmc/spin.h>
#include "pmc_queue.h"
#include "structdefs.h"

#define PMC_MINALLOC 131072
//#define PMC_MINALLOC 2048
#define NUMPMCUNITS (4*NUMCORES4GC)
#define UNITSIZE (BAMBOO_SHARED_MEM_SIZE/NUMPMCUNITS)

struct pmc_unit {
  tmc_spin_mutex_t lock;
  unsigned int numbytes;
  unsigned int regionnum;
  void * endptr;
};

struct pmc_region {
  void * allocptr;
  void * lastptr;
  void * startptr;
  void * endptr;
  unsigned int lowunit;
  unsigned int highunit;
  tmc_spin_mutex_t lock;
  struct ___Object___ * lastobj;
  struct pmc_queue markqueue;
};

struct pmc_heap {
  struct pmc_unit units[NUMPMCUNITS];
  struct pmc_region regions[NUMCORES4GC];
  tmc_spin_mutex_t lock;
  volatile unsigned int numthreads;
  tmc_spin_barrier_t barrier;
};

extern struct pmc_heap * pmc_heapptr;
extern struct pmc_queue * pmc_localqueue;

void gettype_size(void * ptr, unsigned int * ttype, unsigned int * tsize);
void padspace(void *ptr, unsigned int length);
void * pmc_unitend(unsigned int index);
void incrementthreads();
void decrementthreads();
void pmc_onceInit();
void pmc_init();
void gc(struct garbagelist *gl);
#endif
