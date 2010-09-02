#ifndef MGCHASH_H
#define MGCHASH_H

#ifndef bool
#define bool int
#endif

#ifndef true
#define true 1
#endif

#ifndef false
#define false 0
#endif

#ifndef INLINE
#define INLINE    inline __attribute__((always_inline))
#endif

#include "mem.h"

/* mgchash *********************************************************/
typedef struct mgchashlistnode {
  void * key;
  void * val; //this can be cast to another type or used to point to a
              //larger structure
  struct mgchashlistnode *next;
  //struct mgchashlistnode *lnext;
} mgchashlistnode_t;

#define NUMMGCLIST 250
typedef struct mgclist {
  struct mgchashlistnode array[NUMMGCLIST];
  int num;
  struct mgclist *next;
} mgcliststruct_t;

typedef struct mgchashtable {
  mgchashlistnode_t * table;       // points to beginning of hash table
  //mgchashlistnode_t * list;
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
struct MGCHash * allocateMGCHash(int size, int conflicts);
void freeMGCHash(struct MGCHash *);

//void MGCHashrehash(struct MGCHash * thisvar);
int MGCHashadd(struct MGCHash *, int data);
#ifdef MULTICORE
struct MGCHash * allocateMGCHash_I(int size, int conflicts);
int MGCHashadd_I(struct MGCHash *, int data);
#endif
int MGCHashcontains(struct MGCHash *,int data);

struct MGCHash {
  int num4conflicts;
  int size;
  struct MGCNode *bucket;
};

/* MGCHashException  *************************************************/

struct MGCNode {
  struct MGCNode * next;
  int data;
};

#endif
