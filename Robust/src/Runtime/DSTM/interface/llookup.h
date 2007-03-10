#ifndef _LLOOKUP_H_
#define _LLOOKUP_H_

#include <stdlib.h>
#include <stdio.h>
#include <pthread.h>

#define LOADFACTOR 0.75
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

unsigned int lhashCreate(unsigned int size, float loadfactor);// returns 0 for success and 0 for failure
unsigned int lhashFunction(unsigned int oid); // returns 0 for success and 0 for failure
unsigned int lhashInsert(unsigned int oid, unsigned int mid); // returns 0 for success and 0 for failure
unsigned int lhashSearch(unsigned int oid); //returns mid, 0 if not found
unsigned int lhashRemove(unsigned int oid); //returns 0 if not success
unsigned int lhashResize(unsigned int newsize);  // returns 0 for success and 0 for failure

#endif
