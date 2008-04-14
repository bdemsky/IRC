#include "queue.h"

primarypfq_t pqueue; //Global queue

void queueInit(void) {
  /* Intitialize primary queue */
  pqueue.front = pqueue.rear = NULL;
  pthread_mutexattr_init(&pqueue.qlockattr);
  pthread_mutexattr_settype(&pqueue.qlockattr, PTHREAD_MUTEX_RECURSIVE_NP);
  pthread_mutex_init(&pqueue.qlock, &pqueue.qlockattr);
  pthread_cond_init(&pqueue.qcond, NULL);
}

/* Delete the node pointed to by the front ptr of the queue */
void delqnode() {
  prefetchqelem_t *delnode;
  if(pqueue.front == NULL) {
    printf("The queue is empty: UNDERFLOW %s, %d\n", __FILE__, __LINE__);
    return;
  } else if (pqueue.front == pqueue.rear) {
    free(pqueue.front);
    pqueue.front = pqueue.rear = NULL;
  } else {
    delnode = pqueue.front;
    pqueue.front = pqueue.front->next;
    free(delnode);
  }
}

void queueDelete(void) {
  /* Remove each element */
  while(pqueue.front != NULL)
    delqnode();
}

/* Inserts to the rear of primary prefetch queue */
void pre_enqueue(prefetchqelem_t *qnode) {
  if(pqueue.front == NULL) {
    pqueue.front = pqueue.rear = qnode;
    qnode->next=NULL;
  } else {
    qnode->next = NULL;
    pqueue.rear->next = qnode;
    pqueue.rear = qnode;
  }
}

/* Return the node pointed to by the front ptr of the queue */
prefetchqelem_t *pre_dequeue(void) {
  prefetchqelem_t *retnode;
  if (pqueue.front == NULL) {
    printf("Queue empty: Underflow %s, %d\n", __FILE__, __LINE__);
    return NULL;
  }
  retnode = pqueue.front;
  pqueue.front = pqueue.front->next;
  if (pqueue.front == NULL)
    pqueue.rear = NULL;
  retnode->next = NULL;
  
  return retnode;
}

void queueDisplay() {
  int offset = sizeof(prefetchqelem_t);
  int *ptr;
  int ntuples;
  char *ptr1;
  prefetchqelem_t *tmp = pqueue.front;
  while(tmp != NULL) {
    ptr1 = (char *) tmp;
    ptr = (int *)(ptr1 + offset);
    ntuples = *ptr;
    tmp = tmp->next;
  }
}

void predealloc(prefetchqelem_t *node) {
  free(node);
}

