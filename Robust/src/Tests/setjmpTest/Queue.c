#include <stdlib.h>
#include "Queue.h"

struct Queue * createQueue() {
  struct Queue * queue = (struct Queue *)malloc(sizeof(struct Queue));
  queue->head = NULL;
  queue->tail = NULL;
  return queue;
}

void freeQueue(struct Queue * q) {
  free(q);
}

struct QueueItem * addNewItem(struct Queue * queue, void * ptr) {
  struct QueueItem * item=malloc(sizeof(struct QueueItem));
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

#ifdef RAW
struct QueueItem * addNewItem_I(struct Queue * queue, void * ptr) {
  struct QueueItem * item=malloc_I(sizeof(struct QueueItem));
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
#endif

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
  free(item);
}

struct QueueItem * getTail(struct Queue * queue) {
  return queue->tail;
}

struct QueueItem * getNextQueueItem(struct QueueItem * qi) {
  return qi->next;
}

void * getItem(struct Queue * queue) {
  struct QueueItem * q=queue->head;
  void * ptr=q->objectptr;
  queue->head=q->next;
  free(q);
  return ptr;
}
