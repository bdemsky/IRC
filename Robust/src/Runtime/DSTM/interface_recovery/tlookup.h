#ifndef _TTLOOKUP_H_
#define _TTLOOKUP_H_

#include <stdlib.h>
#include <stdio.h>
#include <pthread.h>

#define SIMPLE_TTLOOKUP

#define LOADFACTOR 0.5
#define THASH_SIZE 10000

typedef struct thashlistnode {
  unsigned int transid;
  char decision;
  struct thashlistnode *next;
} thashlistnode_t;

typedef struct thashtable {
  thashlistnode_t *table;       // points to beginning of hash table
  unsigned int size;
  unsigned int numelements;
  float loadfactor;
  pthread_mutex_t locktable;
} thashtable_t;

//returns 0 for success and 1 for failure
unsigned int thashCreate(unsigned int size, float loadfactor);
//returns 0 for success and 1 for failure
unsigned int thashInsert(unsigned int transid, char decision);
//returns mid, 0 if not found
char thashSearch(unsigned int transid);
//returns 0 for success and 1 for failure
unsigned int thashRemove(unsigned int transid);

//helper functions
unsigned int thashResize(unsigned int newsize);
unsigned int thashFunction(unsigned int transid);
#endif
