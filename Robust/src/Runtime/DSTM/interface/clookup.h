#ifndef _CLOOKUP_H_
#define _CLOOKUP_H_

#include <stdlib.h>
#include <stdio.h>

#define LOADFACTOR 0.75
#define HASH_SIZE 100

typedef struct chashlistnode {
	unsigned int key;
	void *val; //this can be cast to another type or used to point to a larger structure
	struct chashlistnode *next;
} chashlistnode_t;

typedef struct chashtable {
	chashlistnode_t *table;	// points to beginning of hash table
	unsigned int size;
	unsigned int numelements;
	float loadfactor;
} chashtable_t;

/* Prototypes for hash*/
chashtable_t *chashCreate(unsigned int size, float loadfactor);
unsigned int chashFunction(chashtable_t *table, unsigned int key);
unsigned int chashInsert(chashtable_t *table, unsigned int key, void *val);
void *chashSearch(chashtable_t *table, unsigned int key); //returns val, NULL if not found
unsigned int chashRemove(chashtable_t *table, unsigned int key); //returns -1 if not found
unsigned int chashResize(chashtable_t *table, unsigned int newsize);
void chashDelete(chashtable_t *table);
/* end hash */

#endif

