#include "multicoregc.h"
#include "multicoreruntime.h"
#include "pmc_garbage.h"
#include "runtime_arch.h"

struct pmc_heap * pmc_heapptr;
struct pmc_queue * pmc_localqueue;
volatile bool gcflag;

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

void * pmc_unitend(unsigned int index) {
  return gcbaseva+(index+1)*NUMPMCUNITS;
}

void pmc_onceInit() {
  pmc_localqueue=&pmc_heapptr->regions[BAMBOO_NUM_OF_CORE].markqueue;
  pmc_queueinit(pmc_localqueue);
  tmc_spin_barrier_init(&pmc_heapptr->barrier, NUMCORES4GC);
  for(int i=0;i<NUMPMCUNITS;i++) {
    pmc_heapptr->units[i].endptr=pmc_unitend(i);
  }
}

void pmc_init() {
  if (BAMBOO_NUM_OF_CORE==STARTUPCORE) {
    pmc_heapptr->numthreads=NUMCORES4GC;
  }
  tmc_spin_barrier_wait(&pmc_heapptr->barrier);
}

void gc(struct garbagelist *gl) {
  tprintf("init\n");
  pmc_init();
  //mark live objects
  tprintf("mark\n");
  pmc_mark(gl);
  //count live objects per unit
  tprintf("count\n");
  pmc_count();
  tmc_spin_barrier_wait(&pmc_heapptr->barrier);
  //divide up work
  tprintf("divide\n");
  if (BAMBOO_NUM_OF_CORE==STARTUPCORE) {
    pmc_processunits();
  }
  tmc_spin_barrier_wait(&pmc_heapptr->barrier);
  //set up forwarding pointers
  tprintf("forward\n");
  pmc_doforward();
  tmc_spin_barrier_wait(&pmc_heapptr->barrier);
  //update pointers
  tprintf("updaterefs\n");
  pmc_doreferenceupdate();
  tmc_spin_barrier_wait(&pmc_heapptr->barrier);
  //compact data
  tprintf("compact\n");
  pmc_docompact();
  tmc_spin_barrier_wait(&pmc_heapptr->barrier);
}

void gettype_size(void * ptr, int * ttype, unsigned int * tsize) {
  int type = ((int *)ptr)[0];
  if(type < NUMCLASSES) {
    // a normal object
    *tsize = classsize[type];
    *ttype = type;
  } else {
    // an array
    struct ArrayObject *ao=(struct ArrayObject *)ptr;
    unsigned int elementsize=classsize[type];
    unsigned int length=ao->___length___;
    *tsize = sizeof(struct ArrayObject)+length*elementsize;
    *ttype = type;
  } 
}
