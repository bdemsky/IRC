#ifndef _CHASH_H_
#define _CHASH_H_

#include <stdlib.h>
#include <stdio.h>

typedef struct cnode {
  unsigned int key;
  void *val;       //this can be cast to another type or used to point to a larger structure
  struct cnode *next;
} cnode_t;

typedef struct ctable {
  cnode_t *table;       // points to beginning of hash table
  unsigned int size;
  unsigned int mask;
  unsigned int numelements;
  float loadfactor;
} ctable_t;

/* Prototypes for hash*/
ctable_t *cCreate(unsigned int size, float loadfactor);
unsigned int cInsert(ctable_t *table, unsigned int key, unsigned int val);
unsigned int cSearch(chashtable_t *table, unsigned int key); //returns val, NULL if not found
unsigned int cRemove(chashtable_t *table, unsigned int key); //returns -1 if not found
unsigned int cResize(chashtable_t *table, unsigned int newsize);
void cDelete(chashtable_t *table);
/* end hash */

#endif

