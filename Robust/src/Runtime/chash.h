#ifndef _CHASH_H_
#define _CHASH_H_

#include <stdlib.h>
#include <stdio.h>
#define INLINE    inline __attribute__((always_inline))
//#define INLINE

typedef struct cnode {
  unsigned int key;
  void *val;       //this can be cast to another type or used to point to a larger structure
  struct cnode *next;
  struct cnode *lnext;
} cnode_t;

typedef struct ctable {
  cnode_t *table;       // points to beginning of hash table
  unsigned int size;
  unsigned int mask;
  unsigned int numelements;
  unsigned int resize;
  float loadfactor;
  struct cnode *listhead;  
} ctable_t;

/* Prototypes for hash*/
ctable_t *cCreate(unsigned int size, float loadfactor);
void cInsert(ctable_t *table, unsigned int key, void * val);
void * cSearch(ctable_t *table, unsigned int key); //returns val, NULL if not found
unsigned int cRemove(ctable_t *table, unsigned int key); //returns -1 if not found
unsigned int cResize(ctable_t *table, unsigned int newsize);
void cDelete(ctable_t *table);
void crehash(ctable_t *table);
/* end hash */

#endif

