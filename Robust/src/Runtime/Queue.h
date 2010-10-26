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

void initQueue(struct Queue *);
struct Queue * createQueue();
void freeQueue(struct Queue * q);

struct QueueItem * addNewItem(struct Queue * queue, void * ptr);
struct QueueItem * addNewItemBack(struct Queue * queue, void * ptr);
#ifdef MULTICORE
struct Queue * createQueue_I();
struct QueueItem * addNewItem_I(struct Queue * queue, void * ptr);
#endif

struct QueueItem * getTail(struct Queue * queue);
struct QueueItem * getHead(struct Queue * queue);
struct QueueItem * getNextQueueItem(struct QueueItem * qi);

struct QueueItem * findItem(struct Queue * queue, void * ptr);

void removeItem(struct Queue * queue, struct QueueItem * item);

void * getItem(struct Queue * queue);
void * getItemBack(struct Queue * queue);

void * peekItem(struct Queue * queue);
void * peekItemBack(struct Queue * queue);

void clearQueue(struct Queue * queue);


// for debugging, only included if macro is defined
#ifdef DEBUG_QUEUE

// returns 1 if queue's pointers are valid, 0 otherwise
int assertQueue(struct Queue * queue);

// use this to print head, tail and next, prev of each item
void printQueue(struct Queue * queue);
#endif

#endif
