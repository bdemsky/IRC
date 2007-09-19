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
		mcqueue.front = mcqueue.rear = node;
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
	if (mcqueue.front == NULL)
		mcqueue.rear = NULL;
	retnode->next = NULL;

	return retnode;
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

/* Delete prefetchpile_t and everything it points to */
void mcdealloc(prefetchpile_t *node) {
	prefetchpile_t *prefetchpile_ptr;
	prefetchpile_t *prefetchpile_next_ptr;
	objpile_t *objpile_ptr;
	objpile_t *objpile_next_ptr;

	prefetchpile_ptr = node;

	while (prefetchpile_ptr != NULL)
	{
		objpile_ptr = prefetchpile_ptr->objpiles;
		while (objpile_ptr != NULL)
		{
			if (objpile_ptr->numoffset > 0)
				free(objpile_ptr->offset);
			objpile_next_ptr = objpile_ptr->next;
			free(objpile_ptr);
			objpile_ptr = objpile_next_ptr;
		}
		prefetchpile_next_ptr = prefetchpile_ptr->next;
		free(prefetchpile_ptr);
		prefetchpile_ptr = prefetchpile_next_ptr;
	}
}


