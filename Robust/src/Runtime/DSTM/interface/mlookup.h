#ifndef _MLOOKUP_H_
#define _MLOOKUP_H_

#include <stdlib.h>
#include <stdio.h>

#define LOADFACTOR 0.75
#define HASH_SIZE 100

typedef struct hashlistnode {
	unsigned int key;
	void *val; //this can be cast to another type or used to point to a larger structure
	struct hashlistnode *next;
} mhashlistnode_t;

typedef struct hashtable {
	mhashlistnode_t *table;	// points to beginning of hash table
	unsigned int size;
	unsigned int numelements;
	float loadfactor;
} mhashtable_t;

unsigned int mhashCreate(unsigned int size, float loadfactor);
unsigned int mhashFunction(unsigned int key);
unsigned mhashInsert(unsigned int key, void *val);
void *mhashSearch(unsigned int key); //returns val, NULL if not found
unsigned int mhashRemove(unsigned int key); //returns -1 if not found
unsigned int mhashResize(unsigned int newsize);

#endif

