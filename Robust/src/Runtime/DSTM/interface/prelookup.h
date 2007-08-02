#ifndef _PRELOOKUP_H_
#define _PRELOOKUP_H_

#include <stdlib.h>
#include <stdio.h>
#include <pthread.h>

#define LOADFACTOR 0.75
#define HASH_SIZE 100

typedef struct prehashlistnode {
	unsigned int key;
	void *val; //this can be cast to another type or used to point to a larger structure
	struct prehashlistnode *next;
} prehashlistnode_t;

typedef struct prehashtable {
	prehashlistnode_t *table;	// points to beginning of hash table
	unsigned int size;
	unsigned int numelements;
	float loadfactor;
	pthread_mutex_t lock;
	pthread_cond_t cond;
} prehashtable_t;

/* Prototypes for hash*/
unsigned int prehashCreate(unsigned int size, float loadfactor);
unsigned int prehashFunction(unsigned int key);
unsigned int prehashInsert(unsigned int key, void *val);
void *prehashSearch(unsigned int key); //returns val, NULL if not found
unsigned int prehashRemove(unsigned int key); //returns -1 if not found
unsigned int prehashResize(unsigned int newsize);
/* end hash */

#endif

