#ifndef _HASHTABLE_H_
#define _HASHTABLE_H_

#define LOADFACTOR 0.75
#define HASH_SIZE 100

typedef struct hashlistnode {
	unsigned int key;
	void *val; //this can be cast to another type or used to point to a larger structure
	struct hashlistnode *next;
} hashlistnode_t;

typedef struct hashtable {
	hashlistnode_t *table;	// points to beginning of hash table
	unsigned int size;
	unsigned int numelements;
	float loadfactor;
} hashtable_t;

/* Prototypes for hash*/
hashtable_t *hashCreate(unsigned int size, float loadfactor);
unsigned int hashFunction(hashtable_t *table, unsigned int key);
void hashInsert(hashtable_t *table, unsigned int key, void *val);
void *hashSearch(hashtable_t *table, unsigned int key); //returns val, NULL if not found
int hashRemove(hashtable_t *table, unsigned int key); //returns -1 if not found
void hashResize(hashtable_t *table, unsigned int newsize);
/* end hash */

#endif

