#include <stdlib.h>
#include "pmc_queue.h"
#include "mem.h"
#include "runtime_arch.h"

void pmc_queueinit(struct pmc_queue *queue) {
  queue->headindex=queue->tailindex=0;
}

void * pmc_dequeue(struct pmc_queue *queue) {
  void *value=NULL;
  tmc_spin_mutex_lock(&queue->lock);

  if (queue->tailindex!=queue->headindex) {
    value=queue->objects[queue->tailindex];
    queue->tailindex++;
    //check for possible rollover
    if (queue->tailindex==NUM_PMC_QUEUE_OBJECTS) {
      queue->tailindex=0;
    }
  }

  tmc_spin_mutex_unlock(&queue->lock);
  return value;
}

void pmc_enqueue(struct pmc_queue* queue, void *ptr) {
  unsigned int currindex=queue->headindex;
  queue->objects[currindex]=ptr;
  //need fence to prevent reordering
  __insn_mf();

  currindex++;
  if (currindex==NUM_PMC_QUEUE_OBJECTS)
    currindex=0;

  if (currindex==queue->tailindex) {
    tprintf("queue full event...\n");
    BAMBOO_EXIT();
  }

  queue->headindex=currindex;
}

bool pmc_isEmpty(struct pmc_queue *queue) {
  tmc_spin_mutex_lock(&queue->lock);
  bool status=(queue->headindex==queue->tailindex);
  tmc_spin_mutex_unlock(&queue->lock);
  return status;
}
