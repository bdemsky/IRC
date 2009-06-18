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
#ifdef MULTICORE
struct QueueItem * addNewItem_I(struct Queue * queue, void * ptr);
#endif
struct QueueItem * findItem(struct Queue * queue, void * ptr);
void removeItem(struct Queue * queue, struct QueueItem * item);
struct QueueItem * getTail(struct Queue * queue);
struct QueueItem * getHead(struct Queue * queue);
struct QueueItem * getNextQueueItem(struct QueueItem * qi);

// to implement a double-ended queue
void * getItemBack(struct Queue * queue);
struct QueueItem * addNewItemBack(struct Queue * queue, void * ptr);


// for debugging, only included if macro is defined
#ifdef DEBUG_QUEUE

// returns 1 if queue's pointers are valid, 0 otherwise
int assertQueue(struct Queue * queue);

// use this to print head, tail and next, prev of each item
void printQueue(struct Queue * queue);
#endif

#endif
