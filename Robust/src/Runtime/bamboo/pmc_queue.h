#ifndef PMC_QUEUE_H
#define PMC_QUEUE_H
#include "multicore.h"
#include <tmc/spin.h>

#define NUM_PMC_QUEUE_OBJECTS 4096

struct pmc_queue {
  volatile void * objects[NUM_PMC_QUEUE_OBJECTS];
  volatile int headindex;
  volatile int tailindex;
  tmc_spin_mutex_t lock;
};

void * pmc_dequeue(struct pmc_queue *queue);
void pmc_enqueue(struct pmc_queue* queue, void *ptr);
bool pmc_isEmpty(struct pmc_queue *queue);
void pmc_queueinit(struct pmc_queue *queue);
#endif
