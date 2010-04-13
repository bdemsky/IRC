#ifndef _ALTMLOOKUP_H_
#define _ALTMLOOKUP_H_

#include <stdlib.h>
#include <stdio.h>
#include <pthread.h>

#define MLOADFACTOR 0.25
#define MHASH_SIZE 1024

typedef struct mhashlistnode {
  unsigned int key;
  void *val;       //this can be cast to another type or used to point to a larger structure
  struct mhashlistnode *next;
} mhashlistnode_t;

struct lockarray {
  volatile unsigned int lock;
  int buf[15];
};

#define NUMLOCKS 16
#define LOCKMASK (NUMLOCKS-1)

typedef struct mhashtable {
  mhashlistnode_t *table;       // points to beginning of hash table
  unsigned int size;
  unsigned int mask;
  unsigned int numelements;
  unsigned int threshold;
  double loadfactor;
  struct lockarray larray[NUMLOCKS];
} mhashtable_t;

unsigned int mhashCreate(unsigned int size, double loadfactor);
unsigned int mhashFunction(unsigned int key);
void mhashInsert(unsigned int key, void *val);
void *mhashSearch(unsigned int key); //returns val, NULL if not found
unsigned int mhashRemove(unsigned int key); //returns -1 if not found
void mhashResize(unsigned int newsize);
//unsigned int *mhashGetKeys(unsigned int *numKeys);
void mhashPrint();

#endif

