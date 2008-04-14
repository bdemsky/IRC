#include "mcpileq.h"

mcpileq_t mcqueue; //Global queue

void mcpileqInit(void) {
  /* Initialize machine queue that containing prefetch oids and offset values  sorted by remote machineid */  
  mcqueue.front = mcqueue.rear = NULL;
  //Intiliaze and set machile pile queue's mutex attribute
  pthread_mutexattr_init(&mcqueue.qlockattr);
  pthread_mutexattr_settype(&mcqueue.qlockattr, PTHREAD_MUTEX_RECURSIVE_NP);
  pthread_mutex_init(&mcqueue.qlock,&mcqueue.qlockattr); 
  pthread_cond_init(&mcqueue.qcond, NULL); 
}

/* Insert to the rear of machine pile queue */
void mcpileenqueue(prefetchpile_t *node, prefetchpile_t *tail) {
  if(mcqueue.front == NULL) {
    mcqueue.front = node;
    mcqueue.rear = tail;
  } else {
    mcqueue.rear->next = node;
    mcqueue.rear = tail;
  }
}

/* Return the node pointed to by the front ptr of the queue */
prefetchpile_t *mcpiledequeue(void) {
  prefetchpile_t *retnode=mcqueue.front;
  if(retnode == NULL) {
    printf("Machine pile queue empty: Underflow %s %d\n", __FILE__, __LINE__);
    return NULL;
  }
  mcqueue.front = retnode->next;
  if (mcqueue.front == NULL)
    mcqueue.rear = NULL;
  retnode->next = NULL;
  
  return retnode;
}

void mcpiledelete(void) {
  /* Remove each element */
  while(mcqueue.front != NULL)
    delqnode();
}


void mcpiledisplay() {
  int mid;
  
  prefetchpile_t *tmp = mcqueue.front;
  while(tmp != NULL) {
    printf("Remote machine id = %d\n", tmp->mid);
    tmp = tmp->next;
  }
}

/* Delete prefetchpile_t and everything it points to */
void mcdealloc(prefetchpile_t *node) {
  prefetchpile_t *prefetchpile_ptr;
  prefetchpile_t *prefetchpile_next_ptr;
  objpile_t *objpile_ptr;
  objpile_t *objpile_next_ptr;
  
  prefetchpile_ptr = node;
  
  while (prefetchpile_ptr != NULL) {
    prefetchpile_next_ptr = prefetchpile_ptr;
    while(prefetchpile_ptr->objpiles != NULL) {
      //offsets aren't owned by us, so we don't free them.
      objpile_ptr = prefetchpile_ptr->objpiles;
      prefetchpile_ptr->objpiles = objpile_ptr->next;
      free(objpile_ptr);
    }
    prefetchpile_ptr = prefetchpile_next_ptr->next;
    free(prefetchpile_next_ptr);
  }
}
