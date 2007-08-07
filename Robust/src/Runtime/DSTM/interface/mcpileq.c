#include "mcpileq.h"

mcpileq_t mcqueue;

void mcpileqInit(void) {
	/* Initialize machine queue that containing prefetch oids and offset values  sorted by remote machineid */  
	mcqueue.front = mcqueue.rear = NULL;
	pthread_mutex_init(&mcqueue.qlock, NULL); 
	pthread_cond_init(&mcqueue.qcond, NULL); 
}

/* Insert to the rear of machine pile queue */
/*
void mcpileenqueue(prefetchpile_t *node) {
	if(mcqueue.front == NULL && mcqueue.rear == NULL) {
		mcqueue.front = mcqueue.rear = node;
	} else {
		node->next = NULL;
		mcqueue.rear->next = node;
		mcqueue.rear = node;
	}
}
*/

/* Insert to the rear of machine pile queue */
void mcpileenqueue(prefetchpile_t *node) {
	prefetchpile_t *tmp, *prev;
	if(mcqueue.front == NULL && mcqueue.rear == NULL) {
		tmp = mcqueue.front = node;
		while(tmp != NULL) {
			prev = tmp;
			tmp = tmp->next;
		}
		mcqueue.rear = prev;
	} else {
		tmp = mcqueue.rear->next = node;
		while(tmp != NULL) {
			prev = tmp;
			tmp = tmp->next;
		}
		mcqueue.rear = prev;
	}
}

/* Return the node pointed to by the front ptr of the queue */
prefetchpile_t *mcpiledequeue(void) {
	prefetchpile_t *retnode;
	if(mcqueue.front == NULL) {
		printf("Machune pile queue empty: Underfloe %s %d\n", __FILE__, __LINE__);
		return NULL;
	}
	retnode = mcqueue.front;
	mcqueue.front = mcqueue.front->next;
	retnode->next = NULL;

	return retnode;
}

/* Delete the node pointed to by the front ptr of the queue */
void delnode() {
	prefetchpile_t *delnode;
	if((mcqueue.front == NULL) && (mcqueue.rear == NULL)) {
		printf("The queue is empty: UNDERFLOW %s, %d\n", __FILE__, __LINE__);
		return;
	} else if ((mcqueue.front == mcqueue.rear) && mcqueue.front != NULL && mcqueue.rear != NULL) {
		printf("TEST1\n");
		free(mcqueue.front);
		mcqueue.front = mcqueue.rear = NULL;
	} else {
		delnode = mcqueue.front;
		mcqueue.front = mcqueue.front->next;
		printf("TEST2\n");
		free(delnode);
	}
}

void mcpiledelete(void) {
	/* Remove each element */
	while(mcqueue.front != NULL)
		delqnode();
	mcqueue.front = mcqueue.rear = NULL;
}


void mcpiledisplay() {
	int mid;

	prefetchpile_t *tmp = mcqueue.front;
	while(tmp != NULL) {
		printf("Remote machine id = %d\n", tmp->mid);
		tmp = tmp->next;
	}
}

void mcdealloc(prefetchpile_t *node) {
	/* Remove the offset ptr and linked lists of objpile_t */
	objpile_t *delnode;
	while(node->objpiles != NULL) {
		node->objpiles->offset = NULL;
		delnode = node->objpiles;
		node->objpiles = node->objpiles->next;
		free(delnode);
		node->objpiles->next = NULL;
	}
	free(node);
	node->next = NULL;
}
