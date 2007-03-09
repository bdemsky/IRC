#ifndef _CLOOKUP_H_
#define _CLOOKUP_H_

#include <stdlib.h>
#include <stdio.h>

#define LOADFACTOR 0.75
#define HASH_SIZE 100

typedef struct hashlistnode {
	unsigned int key;
	void *val; //this can be cast to another type or used to point to a larger structure
	struct hashlistnode *next;
} cachehashlistnode_t;

typedef struct hashtable {
	cachehashlistnode_t *table;	// points to beginning of hash table
	unsigned int size;
	unsigned int numelements;
	float loadfactor;
} cachehashtable_t;

/* Prototypes for hash*/
cachehashtable_t *cachehashCreate(unsigned int size, float loadfactor);
unsigned int cachehashFunction(cachehashtable_t *table, unsigned int key);
unsigned int cachehashInsert(cachehashtable_t *table, unsigned int key, void *val);
void *cachehashSearch(cachehashtable_t *table, unsigned int key); //returns val, NULL if not found
unsigned int cachehashRemove(cachehashtable_t *table, unsigned int key); //returns -1 if not found
unsigned int cachehashResize(cachehashtable_t *table, unsigned int newsize);
void cachehashDelete(cachehashtable_t *table);
/* end hash */

#endif

