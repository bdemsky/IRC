#ifndef _MLOOKUP_H_
#define _MLOOKUP_H_

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

/* Prototypes for hash*/
mhashtable_t *mhashCreate(unsigned int size, float loadfactor);
unsigned int mhashFunction(mhashtable_t *table, unsigned int key);
void mhashInsert(mhashtable_t *table, unsigned int key, void *val);
void *mhashSearch(mhashtable_t *table, unsigned int key); //returns val, NULL if not found
int mhashRemove(mhashtable_t *table, unsigned int key); //returns -1 if not found
void mhashResize(mhashtable_t *table, unsigned int newsize);
/* end hash */

#endif

