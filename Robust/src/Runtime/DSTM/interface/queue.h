#ifndef _QUEUE_H_
#define _QUEUE_H_

#include<stdio.h>
#include<stdlib.h>
#include<pthread.h>

#define ARRAY_SIZE 20

// DS that contains information to be shared between threads.
typedef struct prefetchthreadqueue {
	int *buffer[ARRAY_SIZE];
	int front;
	int rear;
	pthread_mutex_t qlock;
} prefetchthreadqueue_t;

void queueInsert(int *);
int *queueDelete();
void queueInit(); //Initializes the queue and qlock mutex 

#endif
