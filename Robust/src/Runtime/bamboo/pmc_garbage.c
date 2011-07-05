#include "pmc_garbage.h"

struct pmc_queue * pmc_localqueue;

void incrementthreads() {
  tmc_spin_mutex_lock(&pmc_heapptr->lock);
  pmc_heapptr->numthreads++;
  tmc_spin_mutex_unlock(&pmc_heapptr->lock);
}

void decrementthreads() {
  tmc_spin_mutex_lock(&pmc_heapptr->lock);
  pmc_heapptr->numthreads--;
  tmc_spin_mutex_unlock(&pmc_heapptr->lock);
}

void pmc_onceInit() {
  pmc_localqueue=&pmc_heapptr->regions[BAMBOO_NUM_OF_THREADS].markqueue;
  pmc_queueinit(pmc_localqueue);
  tmc_spin_barrier_init(&pmc_heapptr->barrier, NUMCORES4GC);
}

void pmc_init() {
  if (BAMBOO_NUM_OF_THREADS==STARTUPCORE) {
    pmc_heapptr->numthreads=NUMCORES4GC;
  }
  tmc_spin_barrier_wait(&pmc_heapptr->barrier);
}

void gc(struct garbagelist *gl) {
  pmc_init();
  //mark live objects
  pmc_mark(gl);
  //count live objects per unit
  pmc_count();
  tmc_spin_barrier_wait(&pmc_heapptr->barrier);
  //divide up work
  if (BAMBOO_NUM_OF_THREADS==STARTUPCORE) {
    pmc_processunits();
  }
  tmc_spin_barrier_wait(&pmc_heapptr->barrier);
  //set up forwarding pointers
  pmc_doforward();
  tmc_spin_barrier_wait(&pmc_heapptr->barrier);
  //update pointers
  pmc_doreferenceupdate();
  tmc_spin_barrier_wait(&pmc_heapptr->barrier);
  //compact data
  pmc_docompact();
  tmc_spin_barrier_wait(&pmc_heapptr->barrier);
}
