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

//TODO consider changing names to avoid conflicts
extern __thread unsigned int dc_c_size;
extern __thread unsigned INTPTR dc_c_mask;
extern __thread unsigned int dc_c_numelements;
extern __thread unsigned int dc_c_threshold;
extern __thread double dc_c_loadfactor;

typedef struct dchashlistnode {
  void * key;
  struct dchashlistnode *next;
  struct dchashlistnode *lnext;
} dchashlistnode_t;

#define NUMDCLIST 250
typedef struct dclist {
  struct dchashlistnode array[NUMDCLIST];
  int num;
  struct dclist *next;
} dcliststruct_t;


void hashRCRCreate(unsigned int size, double loadfactor);
int hashRCRInsert(void * key);
int hashRCRSearch(void * key);
unsigned int hashRCRResize(unsigned int newsize);
void hashRCRDelete();
void hashRCRreset();
#endif
