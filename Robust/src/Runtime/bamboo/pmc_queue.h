#ifndef PMC_QUEUE_H
#define PMC_QUEUE_H
#include "multicore.h"
#include <tmc/spin.h>

#define NUM_PMC_QUEUE_OBJECTS 256
struct pmc_queue_segment {
  volatile void * objects[NUM_PMC_QUEUE_OBJECTS];
  struct pmc_queue_segment * next;
};

struct pmc_queue {
  volatile struct pmc_queue_segment *head;
  volatile struct pmc_queue_segment *tail;
  volatile int headindex;
  volatile int tailindex;
  tmc_spin_mutex_t lock;
};

void * pmc_dequeue(struct pmc_queue *queue);
void pmc_enqueue(struct pmc_queue* queue, void *ptr);
bool pmc_isEmpty(struct pmc_queue *queue);
void pmc_queueinit(struct pmc_queue *queue);
#endif
