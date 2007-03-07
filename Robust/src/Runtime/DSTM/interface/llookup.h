#ifndef _LLOOKUP_H_
#define _LLOOKUP_H_

#include <stdlib.h>
#include <stdio.h>

#define LOADFACTOR 0.75
#define HASH_SIZE 100

typedef struct hashlistnode {
	unsigned int oid;
	unsigned int mid;
	struct hashlistnode *next;
} lhashlistnode_t;

typedef struct hashtable {
	lhashlistnode_t *table;	// points to beginning of hash table
	unsigned int size;
	unsigned int numelements;
	float loadfactor;
} lhashtable_t;

/* Prototypes for hash*/
lhashtable_t lhashCreate(unsigned int size, float loadfactor);
unsigned int lhashFunction(lhashtable_t table, unsigned int oid);
void lhashInsert(lhashtable_t table, unsigned int oid, unsigned int mid);
int lhashSearch(lhashtable_t table, unsigned int oid); //returns oid, -1 if not found
int lhashRemove(lhashtable_t table, unsigned int oid); //returns -1 if not found
void lhashResize(lhashtable_t table, unsigned int newsize);
/* end hash */

#endif

