#ifndef QUEUE_H
#define QUEUE_H

struct Queue {
  struct QueueItem * head;
  struct QueueItem * tail;
};

struct QueueItem {
  void * objectptr;
  struct Queue * queue;
  struct QueueItem * next;
  struct QueueItem * prev;
};

#define isEmpty(x) ((x)->head==NULL)

void * getItem(struct Queue * queue);
void freeQueue(struct Queue * q);
struct Queue * createQueue();
struct QueueItem * addNewItem(struct Queue * queue, void * ptr);
#ifdef RAW
struct QueueItem * addNewItem_I(struct Queue * queue, void * ptr);
#endif
struct QueueItem * findItem(struct Queue * queue, void * ptr);
void removeItem(struct Queue * queue, struct QueueItem * item);
struct QueueItem * getTail(struct Queue * queue);


#endif
