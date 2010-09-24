#ifdef MULTICORE_GC

#ifndef GCSHAREDHASH_H
#define GCSHAREDHASH_H

#ifndef bool
#define bool int
#endif

#ifndef true
#define true 1
#endif

#ifndef false
#define false 0
#endif

#include "mem.h"

/* GCSharedHash *********************************************************/

struct GCSharedHash * noargallocateGCSharedHash();
struct GCSharedHash * allocateGCSharedHash(int size);
void freeGCSharedHash(struct GCSharedHash *);

bool GCSharedHashrehash(struct GCSharedHash * thisvar);
int GCSharedHashadd(struct GCSharedHash *, int key, int data);
#ifdef MULTICORE
struct GCSharedHash * allocateGCSharedHash_I(int size);
int GCSharedHashadd_I(struct GCSharedHash *, int key, int data);
#endif
int GCSharedHashget(struct GCSharedHash *,int key, int* data);

struct GCSharedHash {
  int numelements;
  int size;
  struct GCSharedNode **bucket;
  struct GCSharedNode *listhead;
  struct GCSharedNode *listtail;
};

inline int GCSharedHashcountset(struct GCSharedHash * thisvar);

/* RuntimeHashException  *************************************************/


/* RuntimeIterator *****************************************************/
struct GCSharedNode {
  struct GCSharedNode *next;
  struct GCSharedNode *lnext;
  struct GCSharedNode *lprev;
  int data;
  int key;
};

/* MGCSharedHash *********************************************************/
typedef struct mgcsharedhashlistnode {
  void * key;
  void * val; //this can be cast to another type or used to point to a
              //larger structure
  struct mgcsharedhashlistnode * next;
} mgcsharedhashlistnode_t;

#define NUMMGCSHAREDLIST 250
typedef struct mgcsharedlist {
  struct mgcsharedhashlistnode array[NUMMGCSHAREDLIST];
  int num;
  struct mgcsharedlist *next;
} mgcsharedliststruct_t;

typedef struct mgcsharedhashtbl {
  mgcsharedhashlistnode_t * table;       // points to beginning of hash table
  mgcsharedhashlistnode_t * list;
  mgcsharedliststruct_t * structs;
  unsigned int size;
  unsigned int mask;
  unsigned int numelements;
  unsigned int threshold;
  double loadfactor;
} mgcsharedhashtbl_t;

mgcsharedhashtbl_t * mgcsharedhashCreate(unsigned int size, double loadfactor);
mgcsharedhashtbl_t * mgcsharedhashCreate_I(unsigned int size,double loadfactor);
int mgcsharedhashInsert(mgcsharedhashtbl_t * tbl, void * key, void * val);
void * mgcsharedhashSearch(mgcsharedhashtbl_t * tbl, void * key);
int mgcsharedhashInsert_I(mgcsharedhashtbl_t * tbl, void * key, void * val);
void mgcsharedhashReset(mgcsharedhashtbl_t * tbl);

#endif

#endif
