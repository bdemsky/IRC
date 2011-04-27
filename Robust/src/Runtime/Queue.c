#ifdef DEBUG_QUEUE
#include <stdio.h>
#endif

#include "mem.h"
#include "Queue.h"

#ifdef DMALLOC
#include "dmalloc.h"
#endif

struct Queue * createQueue() {
  struct Queue * queue = (struct Queue *)RUNMALLOC(sizeof(struct Queue));
  queue->head = NULL;
  queue->tail = NULL;
  return queue;
}

void initQueue(struct Queue * q) {
  q->head=NULL;
  q->tail=NULL;
}

void freeQueue(struct Queue * q) {
  RUNFREE(q);
}

struct QueueItem * addNewItem(struct Queue * queue, void * ptr) {
  struct QueueItem * item=RUNMALLOC(sizeof(struct QueueItem));
  item->objectptr=ptr;
  item->queue=queue;
  if (queue->head==NULL) {
    queue->head=item;
    queue->tail=item;
    item->next=NULL;
    item->prev=NULL;
  } else {
    item->next=queue->head;
    item->prev=NULL;
    queue->head->prev=item;
    queue->head=item;
  }
  return item;
}

struct QueueItem * addNewItemBack(struct Queue * queue, void * ptr) {
  struct QueueItem * item=RUNMALLOC(sizeof(struct QueueItem));
  item->objectptr=ptr;
  item->queue=queue;
  if (queue->tail==NULL) {
    queue->head=item;
    queue->tail=item;
    item->next=NULL;
    item->prev=NULL;
  } else {
    item->prev=queue->tail;
    item->next=NULL;
    queue->tail->next=item;
    queue->tail=item;
  }
  return item;
}

#ifdef MULTICORE
struct Queue * createQueue_I() {
  struct Queue * queue = (struct Queue *)RUNMALLOC_I(sizeof(struct Queue));
  queue->head = NULL;
  queue->tail = NULL;
  return queue;
}

struct QueueItem * addNewItem_I(struct Queue * queue, void * ptr) {
  struct QueueItem * item=RUNMALLOC_I(sizeof(struct QueueItem));
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

struct QueueItem * getTail(struct Queue * queue) {
  return queue->tail;
}

struct QueueItem * getHead(struct Queue * queue) {
  return queue->head;
}

struct QueueItem * getNextQueueItem(struct QueueItem * qi) {
  return qi->next;
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

void * getItem(struct Queue * queue) {
  struct QueueItem * q=queue->head;
  void * ptr=q->objectptr;
  if(queue->tail==queue->head) {
    queue->tail=NULL;
  } else {
    q->next->prev=NULL;
  }
  queue->head=q->next;
  if(queue->tail == q) {
    queue->tail = NULL;
  }
  RUNFREE(q);
  return ptr;
}

void * getItemBack(struct Queue * queue) {
  struct QueueItem * q=queue->tail;
  void * ptr=q->objectptr;
  if(queue->head==queue->tail) {
    queue->head=NULL;
  } else {
    q->prev->next=NULL;
  }
  queue->tail=q->prev;
  RUNFREE(q);
  return ptr;
}

void * peekItem(struct Queue * queue) {
  struct QueueItem * q=queue->head;
  void * ptr=q->objectptr;
  return ptr;
}

void * peekItemBack(struct Queue * queue) {
  struct QueueItem * q=queue->tail;
  void * ptr=q->objectptr;
  return ptr;
}

void clearQueue(struct Queue * queue) {
  struct QueueItem * item=queue->head;
  while(item!=NULL) {
    struct QueueItem * next=item->next;
    RUNFREE(item);
    item=next;
  }
  queue->head=queue->tail=NULL;
  return;
}

#ifdef DEBUG_QUEUE
int assertQueue(struct Queue * queue) {

  struct QueueItem* i = queue->head;

  if( i == NULL && queue->tail != NULL ) {
    return 0;
  }

  while( i != NULL ) {

    if( queue->head == i && i->prev != NULL ) {
      return 0;
    }

    if( i->prev == NULL ) {
      if( queue->head != i ) {
	return 0;
      }

      // i->prev != NULL
    } else {
      if( i->prev->next == NULL ) {
	return 0;
      } else if( i->prev->next != i ) {
	return 0;
      }
    }

    if( i->next == NULL ) {
      if( queue->tail != i ) {
	return 0;
      }

      // i->next != NULL
    } else {
      if( i->next->prev == NULL ) {
	return 0;
      } else if( i->next->prev != i ) {
	return 0;
      }
    }

    if( queue->tail == i && i->next != NULL ) {
      return 0;
    }

    i = getNextQueueItem(i);
  }

  return 1;
}

void printQueue(struct Queue * queue) {
  struct QueueItem* i;

  printf("Queue empty? %d\n", isEmpty(queue));

  printf("head        ");
  i = queue->head;
  while( i != NULL ) {
    printf("item        ");
    i = getNextQueueItem(i);
  }
  printf("tail\n");

  printf("[%08x]  ", (int)queue->head);
  i = queue->head;
  while( i != NULL ) {
    printf("[%08x]  ", (int)i);
    i = getNextQueueItem(i);
  }
  printf("[%08x]\n", (int)queue->tail);

  printf("   (next)   ");
  i = queue->head;
  while( i != NULL ) {
    printf("[%08x]  ", (int)(i->next));
    i = getNextQueueItem(i);
  }
  printf("\n");

  printf("   (prev)   ");
  i = queue->head;
  while( i != NULL ) {
    printf("[%08x]  ", (int)(i->prev));
    i = getNextQueueItem(i);
  }
  printf("\n");
}
#endif
