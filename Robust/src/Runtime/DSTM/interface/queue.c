#include "queue.h"

prefetchthreadqueue_t queue; //Global shared prefetch queue

void queueInsert(int *array) {
	pthread_mutex_lock(&queue.qlock);
	queue.rear = queue.rear % ARRAY_SIZE;
	if(queue.front == queue.rear && queue.buffer[queue.front] != NULL) {
		printf("The Circular Queue is Full : OVERFLOW\n");
		pthread_mutex_unlock(&queue.qlock);
		return;
	} else {
		queue.buffer[queue.rear] = array;
		queue.rear++;
	}
	pthread_mutex_unlock(&queue.qlock);
}

int *queueDelete() {
	int *i; 
	i = NULL;
	pthread_mutex_lock(&queue.qlock);
	if(queue.front == queue.rear && queue.buffer[queue.front] == NULL) {
		printf("The Circular Queue is Empty : UNDERFLOW\n");
		pthread_mutex_unlock(&queue.qlock);
		return NULL;
	} else {
		i = queue.buffer[queue.front];
		queue.buffer[queue.front] = NULL;
		queue.front++;
		queue.front = queue.front % ARRAY_SIZE;
		pthread_mutex_unlock(&queue.qlock);
		return i;
	}
}

void queueInit() {
	int i;
	queue.front = 0;
	queue.rear = 0;
	for(i = 0; i< ARRAY_SIZE; i++) 
		queue.buffer[i] = NULL;
	/* Initialize the pthread_mutex variable */
	pthread_mutex_init(&queue.qlock, NULL);
}

/* For testing purposes */
#if 0
main() {
	int *d; 
	queueIsEmpty();
	int a[] = {5, 2, 8, -1};
	int b[] = {11, 8, 4, 19, -1};
	int c[] = {16, 8, 4, -1};
	printf("Front = %d, Rear = %d\n",queue.front, queue.rear);
	d = queueDelete();
	printf("Front = %d, Rear = %d\n",queue.front, queue.rear);
	queueInsert(a);
	printf("Enqueued ptr is %x\n", a);
	printf("1st Insert Front = %d, Rear = %d\n",queue.front, queue.rear);
	queueInsert(b);
	printf("Enqueued ptr is %x\n", b);
	printf("2nd Insert Front = %d, Rear = %d\n",queue.front, queue.rear);
	queueInsert(c);
	printf("3rd Insert Front = %d, Rear = %d\n",queue.front, queue.rear);
	d = queueDelete();
	printf("Dequeued ptr is %x\n", d);
	printf("After 1st del Front = %d, Rear = %d\n",queue.front, queue.rear);
	queueInsert(c);
	printf("Enqueued ptr is %x\n", c);
	printf("After 4th insert Front = %d, Rear = %d\n",queue.front, queue.rear);
	d = queueDelete();
	printf("Dequeued ptr is %x\n", d);
	printf("After 2nd del Front = %d, Rear = %d\n",queue.front, queue.rear);
	d = queueDelete();
	printf("Dequeued ptr is %x\n", d);
	printf("After 3rd del Front = %d, Rear = %d\n",queue.front, queue.rear);
	d = queueDelete();
	printf("Dequeued ptr is %x\n", d);
	printf("After 4th del Front = %d, Rear = %d\n",queue.front, queue.rear);
}

#endif
