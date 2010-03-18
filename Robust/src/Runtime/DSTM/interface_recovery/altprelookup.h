#ifndef _PRELOOKUP_H_
#define _PRELOOKUP_H_

#include <stdlib.h>
#include <stdio.h>
#include <pthread.h>

#define PLOADFACTOR 0.25
#define PHASH_SIZE 1024

typedef struct prehashlistnode {
  unsigned int key;
  void *val;       //this can be cast to another type or used to point to a larger structure
  struct prehashlistnode *next;
} prehashlistnode_t;


struct prelockarray {
   volatile unsigned int lock;
   int buf[15];
};

#define PRENUMLOCKS 16
#define PRELOCKMASK (PRENUMLOCKS-1)


struct objstr;

typedef struct prehashtable {
  prehashlistnode_t *table;     // points to beginning of hash table
  unsigned int size;
  unsigned int mask;
  unsigned int numelements;
  unsigned int threshold;
  double loadfactor;
  struct prelockarray larray[PRENUMLOCKS];
} prehashtable_t;

/* Prototypes for hash*/
unsigned int prehashCreate(unsigned int size, float loadfactor);
unsigned int prehashFunction(unsigned int key);
void prehashInsert(unsigned int key, void *val);
void *prehashSearch(unsigned int key); //returns val, NULL if not found
unsigned int prehashRemove(unsigned int key); //returns -1 if not found
unsigned int prehashResize(unsigned int newsize);
void prehashClear();
/* end hash */

#endif

