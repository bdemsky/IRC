#ifndef _HASHRCR_H_
#define _HASHRCR_H_

#include <stdlib.h>
#include <stdio.h>

#ifndef INTPTR
#ifdef BIT64
#define INTPTR long
#else
#define INTPTR int
#endif
#endif

//TODO consider changing loadfactor?
#define CLOADFACTOR 0.25
#define CHASH_SIZE 1024

#define INLINE    inline __attribute__((always_inline))

typedef struct chashlistnode {
  void * keyAndVal;     //this can be cast to another type or used to point to a larger structure
  struct chashlistnode *next;
  struct chashlistnode *lnext;
} chashlistnode_t;

typedef struct chashtable {
  chashlistnode_t *table;       // points to beginning of hash table
  unsigned int size;
  unsigned int mask;
  unsigned int numelements;
  unsigned int threshold;
  double loadfactor;
} chashtable_t;

#define NUMCLIST 250
typedef struct clist {
  struct chashlistnode array[NUMCLIST];
  int num;
  struct clist *next;
} cliststruct_t;


void hashRCRCreate(unsigned int size, double loadfactor);
void hashRCRInsert(void * addrIn);
void * hashRCRSearch(void * key);
unsigned int hashRCRResize(unsigned int newsize);
void hashRCRDelete();
void hashRCRreset();

//TODO add __thread after extern for all of these
//TODO consider changing names to avoid conflicts
extern chashlistnode_t *c_table;
extern chashlistnode_t *c_list;
extern unsigned int c_size;
extern unsigned INTPTR c_mask;
extern unsigned int c_numelements;
extern unsigned int c_threshold;
extern double c_loadfactor;
extern cliststruct_t *c_structs;

#endif
