#include "mem.h"
#include "Queue.h"
#ifdef DMALLOC
#include "dmalloc.h"
#endif

struct Queue * createQueue() {
  return RUNMALLOC(sizeof(struct Queue));
}

void freeQueue(struct Queue * q) {
  RUNFREE(q);
}

int isEmpty(struct Queue *queue) {
  return queue->head==NULL;
}

struct QueueItem * addNewItem(struct Queue * queue, void * ptr) {
  struct QueueItem * item=RUNMALLOC(sizeof(struct QueueItem));
  item->objectptr=ptr;
  item->queue=queue;
  if (queue->head==NULL) {
    queue->head=item;
    queue->tail=item;
  } else {
    item->next=queue->head;
    queue->head->prev=item;
    queue->head=item;
  }
  return item;
}

struct QueueItem * findItem(struct Queue * queue, void *ptr) {
  struct QueueItem * item=queue->head;
  while(item!=NULL) {
    if (item->objectptr==ptr)
      return item;
    item=item->next;
  }
  return NULL;
}

void removeItem(struct Queue * queue, struct QueueItem * item) {
  struct QueueItem * prev=item->prev;
  struct QueueItem * next=item->next;
  if (queue->head==item)
    queue->head=next;
  else
    prev->next=next;
  if (queue->tail==item)
    queue->tail=prev;
  else
    next->prev=prev;
  RUNFREE(item);
}

struct QueueItem * getTail(struct Queue * queue) {
  return queue->tail;
}

struct QueueItem * getNext(struct QueueItem * qi) {
  return qi->next;
}
