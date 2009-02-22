#ifndef _CLOOKUP_H_
#define _CLOOKUP_H_

#include <stdlib.h>
#include <stdio.h>

#define CLOADFACTOR 0.25
#define CHASH_SIZE 1024

typedef struct chashlistnode {
  unsigned int key;
  void *val;       //this can be cast to another type or used to point to a larger structure
  struct chashlistnode *next;
} chashlistnode_t;

typedef struct chashtable {
  chashlistnode_t *table;       // points to beginning of hash table
  unsigned int size;
  unsigned int mask;
  unsigned int numelements;
  unsigned int threshold;
  double loadfactor;
} chashtable_t;

/* Prototypes for hash*/
chashtable_t *chashCreate(unsigned int size, double loadfactor);
static unsigned int chashFunction(chashtable_t *table, unsigned int key);
void chashInsert(chashtable_t *table, unsigned int key, void *val);
void *chashSearch(chashtable_t *table, unsigned int key); //returns val, NULL if not found
unsigned int chashRemove(chashtable_t *table, unsigned int key); //returns -1 if not found
void * chashRemove2(chashtable_t *table, unsigned int key); //returns -1 if not found
unsigned int chashResize(chashtable_t *table, unsigned int newsize);
void chashDelete(chashtable_t *table);
/* end hash */

#endif

