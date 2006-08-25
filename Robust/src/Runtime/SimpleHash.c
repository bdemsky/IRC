#include "SimpleHash.h"
#include <stdio.h>

/* SIMPLE HASH ********************************************************/
struct SimpleIterator* SimpleHashcreateiterator(struct SimpleHash * thisvar) {
    return allocateSimpleIterator(thisvar->listhead,thisvar->listtail,thisvar->tailindex/*,thisvar*/);
}

void SimpleHashiterator(struct SimpleHash *thisvar, struct SimpleIterator * it) {
  it->cur=thisvar->listhead;
  it->index=0;
  it->tailindex=thisvar->tailindex;
  it->tail=thisvar->listtail;
}

struct SimpleHash * noargallocateSimpleHash() {
    return allocateSimpleHash(100);
}

struct SimpleHash * allocateSimpleHash(int size) {
    struct SimpleHash *thisvar=(struct SimpleHash *)RUNMALLOC(sizeof(struct SimpleHash));
    if (size <= 0) {
        printf("Negative Hashtable size Exception\n");
        exit(-1);
    }
    thisvar->size = size;
    thisvar->bucket = (struct SimpleNode **) calloc(sizeof(struct SimpleNode *)*size,1);
    /* Set allocation blocks*/
    thisvar->listhead=(struct ArraySimple *) calloc(sizeof(struct ArraySimple),1);
    thisvar->listtail=thisvar->listhead;
    thisvar->tailindex=0;
    /*Set data counts*/
    thisvar->numelements = 0;
    return thisvar;
}

void freeSimpleHash(struct SimpleHash *thisvar) {
    struct ArraySimple *ptr=thisvar->listhead;
    RUNFREE(thisvar->bucket);
    while(ptr) {
        struct ArraySimple *next=ptr->nextarray;
        RUNFREE(ptr);
        ptr=next;
    }
    RUNFREE(thisvar);
}

inline int SimpleHashcountset(struct SimpleHash * thisvar) {
    return thisvar->numelements;
}

int SimpleHashfirstkey(struct SimpleHash *thisvar) {
  struct ArraySimple *ptr=thisvar->listhead;
  int index=0;
  while((index==ARRAYSIZE)||!ptr->nodes[index].inuse) {
    if (index==ARRAYSIZE) {
      index=0;
      ptr=ptr->nextarray;
    } else
      index++;
  }
  return ptr->nodes[index].key;
}

int SimpleHashremove(struct SimpleHash *thisvar, int key, int data) {
    unsigned int hashkey = (unsigned int)key % thisvar->size;

    struct SimpleNode **ptr = &thisvar->bucket[hashkey];
    int i;

    while (*ptr) {
        if ((*ptr)->key == key && (*ptr)->data == data) {
	  struct SimpleNode *toremove=*ptr;
	  *ptr=(*ptr)->next;

	  toremove->inuse=0; /* Marked as unused */

	  thisvar->numelements--;
	  return 1;
        }
        ptr = &((*ptr)->next);
    }

    return 0;
}

void SimpleHashaddAll(struct SimpleHash *thisvar, struct SimpleHash * set) {
    struct SimpleIterator it;
    SimpleHashiterator(set, &it);
    while(hasNext(&it)) {
        int keyv=key(&it);
        int data=next(&it);
        SimpleHashadd(thisvar,keyv,data);
    }
}

int SimpleHashadd(struct SimpleHash * thisvar,int key, int data) {
  /* Rehash code */
  unsigned int hashkey;
  struct SimpleNode **ptr;

  if (thisvar->numelements>=thisvar->size) {
    int newsize=2*thisvar->size+1;
    struct SimpleNode ** newbucket = (struct SimpleNode **) calloc(sizeof(struct SimpleNode *)*newsize,1);
    int i;
    for(i=thisvar->size-1;i>=0;i--) {
        struct SimpleNode *ptr;
        for(ptr=thisvar->bucket[i];ptr!=NULL;) {
            struct SimpleNode * nextptr=ptr->next;
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

  /* check that thisvar key/object pair isn't already here */
  /* TBD can be optimized for set v. relation */

  while (*ptr) {
    if ((*ptr)->key == key && (*ptr)->data == data) {
      return 0;
    }
    ptr = &((*ptr)->next);
  }
  if (thisvar->tailindex==ARRAYSIZE) {
    thisvar->listtail->nextarray=(struct ArraySimple *) calloc(sizeof(struct ArraySimple),1);
    thisvar->tailindex=0;
    thisvar->listtail=thisvar->listtail->nextarray;
  }

  *ptr = &thisvar->listtail->nodes[thisvar->tailindex++];
  (*ptr)->key=key;
  (*ptr)->data=data;
  (*ptr)->inuse=1;

  thisvar->numelements++;
  return 1;
}

bool SimpleHashcontainskey(struct SimpleHash *thisvar,int key) {
    unsigned int hashkey = (unsigned int)key % thisvar->size;

    struct SimpleNode *ptr = thisvar->bucket[hashkey];
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

bool SimpleHashcontainskeydata(struct SimpleHash *thisvar, int key, int data) {
    unsigned int hashkey = (unsigned int)key % thisvar->size;

    struct SimpleNode *ptr = thisvar->bucket[hashkey];
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

int SimpleHashcount(struct SimpleHash *thisvar,int key) {
    unsigned int hashkey = (unsigned int)key % thisvar->size;
    int count = 0;

    struct SimpleNode *ptr = thisvar->bucket[hashkey];
    while (ptr) {
        if (ptr->key == key) {
            count++;
        }
        ptr = ptr->next;
    }
    return count;
}

struct SimpleHash * SimpleHashimageSet(struct SimpleHash *thisvar, int key) {
  struct SimpleHash * newset=allocateSimpleHash(2*SimpleHashcount(thisvar,key)+4);
  unsigned int hashkey = (unsigned int)key % thisvar->size;

  struct SimpleNode *ptr = thisvar->bucket[hashkey];
  while (ptr) {
    if (ptr->key == key) {
        SimpleHashadd(newset,ptr->data,ptr->data);
    }
    ptr = ptr->next;
  }
  return newset;
}

int SimpleHashget(struct SimpleHash *thisvar, int key, int *data) {
    unsigned int hashkey = (unsigned int)key % thisvar->size;

    struct SimpleNode *ptr = thisvar->bucket[hashkey];
    while (ptr) {
        if (ptr->key == key) {
            *data = ptr->data;
            return 1; /* success */
        }
        ptr = ptr->next;
    }

    return 0; /* failure */
}

int SimpleHashcountdata(struct SimpleHash *thisvar,int data) {
    int count = 0;
    struct ArraySimple *ptr = thisvar->listhead;
    while(ptr) {
      if (ptr->nextarray) {
          int i;
          for(i=0;i<ARRAYSIZE;i++)
              if (ptr->nodes[i].data == data
                  &&ptr->nodes[i].inuse) {
                  count++;
              }
      } else {
          int i;
          for(i=0;i<thisvar->tailindex;i++)
              if (ptr->nodes[i].data == data
                  &&ptr->nodes[i].inuse) {
                  count++;
              }
      }
      ptr = ptr->nextarray;
    }
    return count;
}

inline struct SimpleIterator * noargallocateSimpleIterator() {
    return (struct SimpleIterator*)RUNMALLOC(sizeof(struct SimpleIterator));
}

inline struct SimpleIterator * allocateSimpleIterator(struct ArraySimple *start, struct ArraySimple *tl, int tlindex) {
    struct SimpleIterator *thisvar=(struct SimpleIterator*)RUNMALLOC(sizeof(struct SimpleIterator));
    thisvar->cur = start;
    thisvar->index=0;
    thisvar->tailindex=tlindex;
    thisvar->tail=tl;
    return thisvar;
}

inline int hasNext(struct SimpleIterator *thisvar) {
    if (thisvar->cur==thisvar->tail &&
	thisvar->index==thisvar->tailindex)
        return 0;
    while((thisvar->index==ARRAYSIZE)||!thisvar->cur->nodes[thisvar->index].inuse) {
        if (thisvar->index==ARRAYSIZE) {
            thisvar->index=0;
            thisvar->cur=thisvar->cur->nextarray;
        } else
            thisvar->index++;
    }
    if (thisvar->cur->nodes[thisvar->index].inuse)
        return 1;
    else
        return 0;
}

inline int next(struct SimpleIterator *thisvar) {
    return thisvar->cur->nodes[thisvar->index++].data;
}

inline int key(struct SimpleIterator *thisvar) {
    return thisvar->cur->nodes[thisvar->index].key;
}
