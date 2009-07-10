#ifndef _CLOOKUP_H_
#define _CLOOKUP_H_

#include <stdlib.h>
#include <stdio.h>

#define CLOADFACTOR 0.25
#define CHASH_SIZE 1024

struct chashentry {
  void * ptr;
  unsigned int key;
};

typedef struct chashtable {
  struct chashentry *table;
  unsigned int size;
  unsigned int mask;
  unsigned int numelements;
  unsigned int capacity;
  float loadfactor;
} chashtable_t;

/* Prototypes for hash*/
chashtable_t *chashCreate(unsigned int size, float loadfactor);
static unsigned int chashFunction(chashtable_t *table, unsigned int key, unsigned int i);
void chashInsert(chashtable_t *table, unsigned int key, void *val);
void *chashSearch(chashtable_t *table, unsigned int key); //returns val, NULL if not found
void chashResize(chashtable_t *table, unsigned int newsize);
void chashDelete(chashtable_t *table);
/* end hash */

#endif

