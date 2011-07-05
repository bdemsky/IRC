#include "pmc_queue.h"

void pmc_queueinit(struct pmc_queue *queue) {
  queue->head=queue->tail=RUNMALLOC(struct pmc_queue_segment);
  queue->headindex=queue->tailindex=0;
}

void * pmc_dequeue(struct pmc_queue *queue) {
  void *value=NULL;
  tmc_spin_mutex_lock(&queue->lock);
  //do-while loop allows sharing cleanup code
  do {
    //check for possible rollover
    if (queue->tailindex==NUM_PMC_QUEUE_OBJECTS) {
      if (queue->tail!=queue->head) {
	struct pmc_queue_segment *oldtail=queue->tail;
	queue->tail=oldtail->next;
	queue->tailindex=0;
	RUNFREE(oldtail);
      } else break;
    }
    //now try to decrement
    if (queue->tailindex!=queue->headindex) {
      value=queue->tail[queue->tailindex];
      queue->tailindex++;
    }
  } while(false);
  tmc_spin_mutex_unlock(&queue->lock);
  return status;
}

void pmc_enqueue(struct pmc_queue* queue, void *ptr) {
  if (queue->headindex<NUM_PMC_QUEUE_OBJECTS) {
    queue->head->objects[queue->headindex]=ptr;
    //need fence to prevent reordering
    __insn_mf();
    queue->headindex++;
    return;
  } else {
    struct pmc_queue_segment * seg=RUNMALLOC(struct pmc_queue_segment);
    seg->objects[0]=ptr;
    //simplify everything by grabbing a lock on segment change
    tmc_spin_mutex_lock(&queue->lock);
    queue->headindex=1;
    queue->head->next=seg;
    queue->head=seg;
    tmc_spin_mutex_unlock(&queue->lock);
  }
}

bool pmc_isEmpty(struct pmc_queue *queue) {
  tmc_spin_mutex_lock(&queue->lock);
  bool status=(queue->head==queue->tail)&&(queue->headindex==queue->tailindex);
  tmc_spin_mutex_unlock(&queue->lock);
  return status;
}
