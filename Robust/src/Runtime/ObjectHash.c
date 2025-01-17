#include "ObjectHash.h"
#ifdef MULTICORE
#include "methodheaders.h"
#include "runtime_arch.h"
#include "multicoreruntime.h"
#else
#include <stdio.h>
#endif

#ifdef DMALLOC
#include "dmalloc.h"
#endif

/* SIMPLE HASH ********************************************************/
struct ObjectIterator* ObjectHashcreateiterator(struct ObjectHash * thisvar) {
  return allocateObjectIterator(thisvar->listhead);
}

void ObjectHashiterator(struct ObjectHash *thisvar, struct ObjectIterator * it) {
  it->cur=thisvar->listhead;
}

struct ObjectHash * noargallocateObjectHash() {
  return allocateObjectHash(100);
}

struct ObjectHash * allocateObjectHash(int size) {
  struct ObjectHash *thisvar;
  if (size <= 0) {
#ifdef MULTICORE
    BAMBOO_EXIT();
#else
    printf("Negative Hashtable size Exception\n");
    exit(-1);
#endif
  }
  thisvar=(struct ObjectHash *)RUNMALLOC(sizeof(struct ObjectHash));
  thisvar->size = size;
  thisvar->bucket = (struct ObjectNode **) RUNMALLOC(sizeof(struct ObjectNode *)*size);
  /* Set allocation blocks*/
  thisvar->listhead=NULL;
  thisvar->listtail=NULL;
  /*Set data counts*/
  thisvar->numelements = 0;
  return thisvar;
}

void freeObjectHash(struct ObjectHash *thisvar) {
  struct ObjectNode *ptr=thisvar->listhead;
  RUNFREE(thisvar->bucket);
  while(ptr) {
    struct ObjectNode *next=ptr->lnext;
    RUNFREE(ptr);
    ptr=next;
  }
  RUNFREE(thisvar);
}

inline int ObjectHashcountset(struct ObjectHash * thisvar) {
  return thisvar->numelements;
}

int ObjectHashfirstkey(struct ObjectHash *thisvar) {
  struct ObjectNode *ptr=thisvar->listhead;
  return ptr->key;
}

int ObjectHashremove(struct ObjectHash *thisvar, int key) {
  unsigned int hashkey = (unsigned int)key % thisvar->size;

  struct ObjectNode **ptr = &thisvar->bucket[hashkey];

  while (*ptr) {
    if ((*ptr)->key == key) {
      struct ObjectNode *toremove=*ptr;
      *ptr=(*ptr)->next;

      if (toremove->lprev!=NULL) {
        toremove->lprev->lnext=toremove->lnext;
      } else {
        thisvar->listhead=toremove->lnext;
      }
      if (toremove->lnext!=NULL) {
        toremove->lnext->lprev=toremove->lprev;
      } else {
        thisvar->listtail=toremove->lprev;
      }
      RUNFREE(toremove);

      thisvar->numelements--;
      return 1;
    }
    ptr = &((*ptr)->next);
  }

  return 0;
}

void ObjectHashrehash(struct ObjectHash * thisvar) {
  int newsize=thisvar->size;
  struct ObjectNode ** newbucket = (struct ObjectNode **) RUNMALLOC(sizeof(struct ObjectNode *)*newsize);
  int i;
  for(i=thisvar->size-1; i>=0; i--) {
    struct ObjectNode *ptr;
    for(ptr=thisvar->bucket[i]; ptr!=NULL; ) {
      struct ObjectNode * nextptr=ptr->next;
      unsigned int newhashkey=(unsigned int)ptr->key % newsize;
      ptr->next=newbucket[newhashkey];
      newbucket[newhashkey]=ptr;
      ptr=nextptr;
    }
  }
  thisvar->size=newsize;
  RUNFREE(thisvar->bucket);
  thisvar->bucket=newbucket;
}

int ObjectHashadd(struct ObjectHash * thisvar,int key, int data, int data2, int data3, int data4) {
  /* Rehash code */
  unsigned int hashkey;
  struct ObjectNode **ptr;

  if (thisvar->numelements>=thisvar->size) {
    int newsize=2*thisvar->size+1;
    struct ObjectNode ** newbucket = (struct ObjectNode **) RUNMALLOC(sizeof(struct ObjectNode *)*newsize);
    int i;
    for(i=thisvar->size-1; i>=0; i--) {
      struct ObjectNode *ptr;
      for(ptr=thisvar->bucket[i]; ptr!=NULL; ) {
        struct ObjectNode * nextptr=ptr->next;
        unsigned int newhashkey=(unsigned int)ptr->key % newsize;
        ptr->next=newbucket[newhashkey];
        newbucket[newhashkey]=ptr;
        ptr=nextptr;
      }
    }
    thisvar->size=newsize;
    RUNFREE(thisvar->bucket);
    thisvar->bucket=newbucket;
  }

  hashkey = (unsigned int)key % thisvar->size;
  ptr = &thisvar->bucket[hashkey];

  {
    struct ObjectNode *node=RUNMALLOC(sizeof(struct ObjectNode));
    node->data=data;
    node->data2=data2;
    node->data3=data3;
    node->data4=data4;
    node->key=key;
    node->next=(*ptr);
    *ptr=node;
    if (thisvar->listhead==NULL) {
      thisvar->listhead=node;
      thisvar->listtail=node;
      node->lnext=NULL;
      node->lprev=NULL;
    } else {
      node->lprev=NULL;
      node->lnext=thisvar->listhead;
      thisvar->listhead->lprev=node;
      thisvar->listhead=node;
    }
  }

  thisvar->numelements++;
  return 1;
}

#ifdef MULTICORE
int ObjectHashadd_I(struct ObjectHash * thisvar,int key, int data, int data2, int data3, int data4) {
  /* Rehash code */
  unsigned int hashkey;
  struct ObjectNode **ptr;

  if (thisvar->numelements>=thisvar->size) {
    int newsize=2*thisvar->size+1;
    struct ObjectNode ** newbucket = (struct ObjectNode **) RUNMALLOC_I(sizeof(struct ObjectNode *)*newsize);
    int i;
    for(i=thisvar->size-1; i>=0; i--) {
      struct ObjectNode *ptr;
      for(ptr=thisvar->bucket[i]; ptr!=NULL; ) {
        struct ObjectNode * nextptr=ptr->next;
        unsigned int newhashkey=(unsigned int)ptr->key % newsize;
        ptr->next=newbucket[newhashkey];
        newbucket[newhashkey]=ptr;
        ptr=nextptr;
      }
    }
    thisvar->size=newsize;
    RUNFREE_I(thisvar->bucket);
    thisvar->bucket=newbucket;
  }

  hashkey = (unsigned int)key % thisvar->size;
  ptr = &thisvar->bucket[hashkey];

  {
    struct ObjectNode *node=RUNMALLOC_I(sizeof(struct ObjectNode));
    node->data=data;
    node->data2=data2;
    node->data3=data3;
    node->data4=data4;
    node->key=key;
    node->next=(*ptr);
    *ptr=node;
    if (thisvar->listhead==NULL) {
      thisvar->listhead=node;
      thisvar->listtail=node;
      node->lnext=NULL;
      node->lprev=NULL;
    } else {
      node->lprev=NULL;
      node->lnext=thisvar->listhead;
      thisvar->listhead->lprev=node;
      thisvar->listhead=node;
    }
  }

  thisvar->numelements++;
  return 1;
}
#endif

bool ObjectHashcontainskey(struct ObjectHash *thisvar,int key) {
  unsigned int hashkey = (unsigned int)key % thisvar->size;

  struct ObjectNode *ptr = thisvar->bucket[hashkey];
  while (ptr) {
    if (ptr->key == key) {
      /* we already have thisvar object
         stored in the hash so just return */
      return true;
    }
    ptr = ptr->next;
  }
  return false;
}

bool ObjectHashcontainskeydata(struct ObjectHash *thisvar, int key, int data) {
  unsigned int hashkey = (unsigned int)key % thisvar->size;

  struct ObjectNode *ptr = thisvar->bucket[hashkey];
  while (ptr) {
    if (ptr->key == key && ptr->data == data) {
      /* we already have thisvar object
         stored in the hash so just return*/
      return true;
    }
    ptr = ptr->next;
  }
  return false;
}

int ObjectHashcount(struct ObjectHash *thisvar,int key) {
  unsigned int hashkey = (unsigned int)key % thisvar->size;
  int count = 0;

  struct ObjectNode *ptr = thisvar->bucket[hashkey];
  while (ptr) {
    if (ptr->key == key) {
      count++;
    }
    ptr = ptr->next;
  }
  return count;
}

int ObjectHashget(struct ObjectHash *thisvar, int key, int *data, int *data2, int *data3, int *data4) {
  unsigned int hashkey = (unsigned int)key % thisvar->size;

  struct ObjectNode *ptr = thisvar->bucket[hashkey];
  while (ptr) {
    if (ptr->key == key) {
      *data = ptr->data;
      *data2 = ptr->data2;
      *data3 = ptr->data3;
      *data4 = ptr->data4;
      return 1;       /* success */
    }
    ptr = ptr->next;
  }

  return 0;   /* failure */
}

int ObjectHashupdate(struct ObjectHash *thisvar, int key, int data, int data2, int data3, int data4) {
  unsigned int hashkey = (unsigned int)key % thisvar->size;

  struct ObjectNode *ptr = thisvar->bucket[hashkey];
  while (ptr) {
    if (ptr->key == key) {
      ptr->data=data;
      ptr->data2=data2;
      ptr->data3=data3;
      ptr->data4=data4;
      return 1;     /* success */
    }
    ptr = ptr->next;
  }
  return 0;   /* failure */
}


inline struct ObjectIterator * noargallocateObjectIterator() {
  return (struct ObjectIterator*)RUNMALLOC(sizeof(struct ObjectIterator));
}

inline struct ObjectIterator * allocateObjectIterator(struct ObjectNode *start) {
  struct ObjectIterator *thisvar=(struct ObjectIterator*)RUNMALLOC(sizeof(struct ObjectIterator));
  thisvar->cur = start;
  return thisvar;
}

inline int ObjhasNext(struct ObjectIterator *thisvar) {
  return (thisvar->cur!=NULL);
}

inline int Objnext(struct ObjectIterator *thisvar) {
  int curr=thisvar->cur->data;
  thisvar->cur=thisvar->cur->lnext;
  return curr;
}

inline int Objkey(struct ObjectIterator *thisvar) {
  return thisvar->cur->key;
}

inline int Objdata(struct ObjectIterator *thisvar) {
  return thisvar->cur->data;
}

inline int Objdata2(struct ObjectIterator *thisvar) {
  return thisvar->cur->data2;
}

inline int Objdata3(struct ObjectIterator *thisvar) {
  return thisvar->cur->data3;
}

inline int Objdata4(struct ObjectIterator *thisvar) {
  return thisvar->cur->data4;
}
