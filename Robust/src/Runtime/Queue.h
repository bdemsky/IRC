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

struct Queue * createQueue();
struct QueueItem * addNewItem(struct Queue * queue, void * ptr);
void removeItem(struct Queue * queue, struct QueueItem * item);
int isEmpty(struct Queue *queue);
struct QueueItem * getTail(struct Queue * queue);


#endif
