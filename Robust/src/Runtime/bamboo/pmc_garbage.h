#ifndef PMC_GARBAGE_H
#define PMC_GARBAGE_H
struct pmc_unit {
  unsigned int lock;
  unsigned int numbytes;
};

struct pmc_region {
  void * lastptr;
  struct ___Object___ * lastobj;
};

struct pmc_heap {
  struct pmc_region units[NUMCORES4GC*4];
  struct pmc_region regions[NUMCORES4GC];
  unsigned int lock;
  unsigned int numthreads;
};

extern struct pmc_heap * pmc_heapptr;

#endif
