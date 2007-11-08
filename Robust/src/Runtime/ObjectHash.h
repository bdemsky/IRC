#ifndef OBJECTHASH_H
#define OBJECTHASH_H

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

/* ObjectHash *********************************************************/

struct ObjectHash * noargallocateObjectHash();
struct ObjectHash * allocateObjectHash(int size);
void ObjectHashaddChild(struct ObjectHash *thisvar, struct ObjectHash * child);
void freeObjectHash(struct ObjectHash *);

void ObjectHashrehash(struct ObjectHash * thisvar);
int ObjectHashadd(struct ObjectHash *, int key, int data, int data2, int data3, int data4);
int ObjectHashremove(struct ObjectHash *,int key);
bool ObjectHashcontainskey(struct ObjectHash *,int key);
bool ObjectHashcontainskeydata(struct ObjectHash *,int key, int data);
int ObjectHashget(struct ObjectHash *,int key, int* data, int* data2, int * data3, int* data4);
void ObjectHashaddParent(struct ObjectHash *,struct ObjectHash* parent);
int ObjectHashfirstkey(struct ObjectHash *);
struct ObjectIterator* ObjectHashcreateiterator(struct ObjectHash *);
void ObjectHashiterator(struct ObjectHash *, struct ObjectIterator * it);
int ObjectHashcount(struct ObjectHash *, int key);

struct ObjectHash {
    int numelements;
    int size;
    struct ObjectNode **bucket;
    struct ObjectNode *listhead;
    struct ObjectNode *listtail;
};

inline int ObjectHashcountset(struct ObjectHash * thisvar);

/* ObjectIterator *****************************************************/

struct ObjectNode {
  struct ObjectNode *next;
  struct ObjectNode *lnext;
  struct ObjectNode *lprev;
  int key;
  int data;
  int data2;
  int data3;
  int data4;
};

struct ObjectIterator {
  struct ObjectNode *cur;
};

inline struct ObjectIterator * noargallocateObjectIterator();

inline struct ObjectIterator * allocateObjectIterator(struct ObjectNode *start);

inline int ObjhasNext(struct ObjectIterator *thisvar);

inline int Objnext(struct ObjectIterator *thisvar);

inline int Objkey(struct ObjectIterator *thisvar);

inline int Objdata(struct ObjectIterator *thisvar);
inline int Objdata2(struct ObjectIterator *thisvar);
inline int Objdata3(struct ObjectIterator *thisvar);
inline int Objdata4(struct ObjectIterator *thisvar);


#endif
