#ifndef MGCHASH_H
#define MGCHASH_H

#include "multicore.h"
#include "mem.h"

/* mgchash *********************************************************/
typedef struct mgchashlistnode {
  void * key;
  void * val; //this can be cast to another type or used to point to a
              //larger structure
  struct mgchashlistnode *next;
} mgchashlistnode_t;

#define NUMMGCLIST 250
typedef struct mgclist {
  struct mgchashlistnode array[NUMMGCLIST];
  int num;
  struct mgclist *next;
} mgcliststruct_t;

typedef struct mgchashtable {
  mgchashlistnode_t * table;       // points to beginning of hash table
  mgcliststruct_t * structs;
  unsigned int size;
  unsigned int mask;
  unsigned int numelements;
  unsigned int threshold;
  double loadfactor;
} mgchashtable_t;

mgchashtable_t * mgchashCreate(unsigned int size, double loadfactor);
void mgchashInsert(mgchashtable_t * tbl, void * key, void *val);
void * mgchashSearch(mgchashtable_t * tbl, void * key);
unsigned int mgchashResize(mgchashtable_t * tbl, unsigned int newsize);
#ifdef MULTICORE_GC
mgchashtable_t * mgchashCreate_I(unsigned int size, double loadfactor);
void mgchashInsert_I(mgchashtable_t * tbl, void * key, void *val);
unsigned int mgchashResize_I(mgchashtable_t * tbl, unsigned int newsize);
#endif
void mgchashDelete(mgchashtable_t * tbl);
void mgchashreset(mgchashtable_t * tbl);


/** MGCHash *******************************************************************/
//must be a power of 2
struct MGCHash * allocateMGCHash(int size);
void freeMGCHash(struct MGCHash *);
void MGCHashreset(struct MGCHash *thisvar);
int MGCHashadd(struct MGCHash *, unsigned INTPTR data);
#ifdef MULTICORE
struct MGCHash * allocateMGCHash_I(int size);
int MGCHashadd_I(struct MGCHash *, unsigned INTPTR data);
#endif
int MGCHashcontains(struct MGCHash *, unsigned INTPTR data);

struct MGCHash {
  int mask;
  int size;
  unsigned INTPTR *bucket;
};

/* MGCHashException  *************************************************/


#endif
