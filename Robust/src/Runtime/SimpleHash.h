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

struct SimpleHash * noargallocateSimpleHash();
struct SimpleHash * allocateSimpleHash(int size);
void SimpleHashaddChild(struct SimpleHash *thisvar, struct SimpleHash * child);
void freeSimpleHash(struct SimpleHash *);


int SimpleHashadd(struct SimpleHash *, int key, int data);
int SimpleHashremove(struct SimpleHash *,int key, int data);
bool SimpleHashcontainskey(struct SimpleHash *,int key);
bool SimpleHashcontainskeydata(struct SimpleHash *,int key, int data);
int SimpleHashget(struct SimpleHash *,int key, int* data);
int SimpleHashcountdata(struct SimpleHash *,int data);
void SimpleHashaddParent(struct SimpleHash *,struct SimpleHash* parent);
int SimpleHashfirstkey(struct SimpleHash *);
struct SimpleIterator* SimpleHashcreateiterator(struct SimpleHash *);
void SimpleHashiterator(struct SimpleHash *, struct SimpleIterator * it);
int SimpleHashcount(struct SimpleHash *, int key);
void SimpleHashaddAll(struct SimpleHash *, struct SimpleHash * set);
struct SimpleHash * SimpleHashimageSet(struct SimpleHash *, int key);

struct SimpleHash {
    int numelements;
    int size;
    struct SimpleNode **bucket;
    struct ArraySimple *listhead;
    struct ArraySimple *listtail;
    int tailindex;
};

inline int SimpleHashcountset(struct SimpleHash * thisvar);

/* SimpleHashException  *************************************************/


/* SimpleIterator *****************************************************/
#define ARRAYSIZE 100

struct SimpleNode {
  struct SimpleNode *next;
  int data;
  int key;
  int inuse;
};

struct ArraySimple {
  struct SimpleNode nodes[ARRAYSIZE];
  struct ArraySimple * nextarray;
};


struct SimpleIterator {
  struct ArraySimple *cur, *tail;
  int index,tailindex;
};

inline struct SimpleIterator * noargallocateSimpleIterator();

inline struct SimpleIterator * allocateSimpleIterator(struct ArraySimple *start, struct ArraySimple *tl, int tlindex);

inline int hasNext(struct SimpleIterator *thisvar);

inline int next(struct SimpleIterator *thisvar);

inline int key(struct SimpleIterator *thisvar);

#endif
