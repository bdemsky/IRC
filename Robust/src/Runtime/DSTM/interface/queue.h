#ifndef _QUEUE_H_
#define _QUEUE_H_

#include<stdio.h>
#include<stdlib.h>
#include<pthread.h>
#include<string.h>

// DS that contains information to be shared between threads.
typedef struct prefetchqelem {
	struct prefetchqelem *next;
} prefetchqelem_t;

typedef struct primarypfq {
	prefetchqelem_t *front, *rear;
	pthread_mutex_t qlock;
	pthread_cond_t qcond;
} primarypfq_t; 


void queueInit(void);
void delqnode(); 
void queueDelete(void);
void enqueue(prefetchqelem_t *qnode);
prefetchqelem_t *dequeue(void);
void queueDisplay();
#endif
