#ifndef SIMPLEHASH_H
#define SIMPLEHASH_H

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

/* SimpleHash *********************************************************/

struct RuntimeHash * noargallocateRuntimeHash();
struct RuntimeHash * allocateRuntimeHash(int size);
void RuntimeHashaddChild(struct RuntimeHash *thisvar, struct RuntimeHash * child);
void freeRuntimeHash(struct RuntimeHash *);

void RuntimeHashrehash(struct RuntimeHash * thisvar);
int RuntimeHashadd(struct RuntimeHash *, int key, int data);
int RuntimeHashremove(struct RuntimeHash *,int key, int data);
bool RuntimeHashcontainskey(struct RuntimeHash *,int key);
bool RuntimeHashcontainskeydata(struct RuntimeHash *,int key, int data);
int RuntimeHashget(struct RuntimeHash *,int key, int* data);
void RuntimeHashaddParent(struct RuntimeHash *,struct RuntimeHash* parent);
int RuntimeHashfirstkey(struct RuntimeHash *);
struct RuntimeIterator* RuntimeHashcreateiterator(struct RuntimeHash *);
void RuntimeHashiterator(struct RuntimeHash *, struct RuntimeIterator * it);
int RuntimeHashcount(struct RuntimeHash *, int key);
struct RuntimeHash * RuntimeHashimageSet(struct RuntimeHash *, int key);

struct RuntimeHash {
    int numelements;
    int size;
    struct RuntimeNode **bucket;
    struct RuntimeNode *listhead;
    struct RuntimeNode *listtail;
};

inline int RuntimeHashcountset(struct RuntimeHash * thisvar);

/* RuntimeHashException  *************************************************/


/* RuntimeIterator *****************************************************/
#define ARRAYSIZE 100

struct RuntimeNode {
  struct RuntimeNode *next;
  struct RuntimeNode *lnext;
  struct RuntimeNode *lprev;
  int data;
  int key;
};

struct RuntimeIterator {
  struct RuntimeNode *cur, *tail;
};

inline struct RuntimeIterator * noargallocateRuntimeIterator();

inline struct RuntimeIterator * allocateRuntimeIterator(struct RuntimeNode *start);

inline int RunhasNext(struct RuntimeIterator *thisvar);

inline int Runnext(struct RuntimeIterator *thisvar);

inline int Runkey(struct RuntimeIterator *thisvar);

#endif
