#ifndef _LLOOKUP_H_
#define _LLOOKUP_H_

#include <stdlib.h>
#include <stdio.h>
#include <pthread.h>

#define SIMPLE_LLOOKUP

#define LOADFACTOR 0.5
#define HASH_SIZE 100

typedef struct lhashlistnode {
	unsigned int oid;
	unsigned int mid;
	struct lhashlistnode *next;
} lhashlistnode_t;

typedef struct lhashtable {
	lhashlistnode_t *table;	// points to beginning of hash table
	unsigned int size;
	unsigned int numelements;
	float loadfactor;
	pthread_mutex_t locktable;
} lhashtable_t;

//returns 0 for success and 1 for failure
unsigned int lhashCreate(unsigned int size, float loadfactor);
//returns 0 for success and 1 for failure
unsigned int lhashInsert(unsigned int oid, unsigned int mid);
//returns mid, 0 if not found
unsigned int lhashSearch(unsigned int oid);
//returns 0 for success and 1 for failure
unsigned int lhashRemove(unsigned int oid);

//helper functions
unsigned int lhashResize(unsigned int newsize);
unsigned int lhashFunction(unsigned int oid);

#endif
