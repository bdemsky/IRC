#include "SimpleHash.h"
#include <stdio.h>

/* SIMPLE HASH ********************************************************/
struct RuntimeIterator* RuntimeHashcreateiterator(struct RuntimeHash * thisvar) {
    return allocateRuntimeIterator(thisvar->listhead,thisvar->listtail,thisvar->tailindex/*,thisvar*/);
}

void RuntimeHashiterator(struct RuntimeHash *thisvar, struct RuntimeIterator * it) {
  it->cur=thisvar->listhead;
  it->index=0;
  it->tailindex=thisvar->tailindex;
  it->tail=thisvar->listtail;
}

struct RuntimeHash * noargallocateRuntimeHash() {
    return allocateRuntimeHash(100);
}

struct RuntimeHash * allocateRuntimeHash(int size) {
    struct RuntimeHash *thisvar=(struct RuntimeHash *)RUNMALLOC(sizeof(struct RuntimeHash));
    if (size <= 0) {
        printf("Negative Hashtable size Exception\n");
        exit(-1);
    }
    thisvar->size = size;
    thisvar->bucket = (struct RuntimeNode **) RUNMALLOC(sizeof(struct RuntimeNode *)*size);
    /* Set allocation blocks*/
    thisvar->listhead=(struct ArrayRuntime *) RUNMALLOC(sizeof(struct ArrayRuntime));
    thisvar->listtail=thisvar->listhead;
    thisvar->tailindex=0;
    /*Set data counts*/
    thisvar->numelements = 0;
    return thisvar;
}

void freeRuntimeHash(struct RuntimeHash *thisvar) {
    struct ArrayRuntime *ptr=thisvar->listhead;
    RUNFREE(thisvar->bucket);
    while(ptr) {
        struct ArrayRuntime *next=ptr->nextarray;
        RUNFREE(ptr);
        ptr=next;
    }
    RUNFREE(thisvar);
}

inline int RuntimeHashcountset(struct RuntimeHash * thisvar) {
    return thisvar->numelements;
}

int RuntimeHashfirstkey(struct RuntimeHash *thisvar) {
  struct ArrayRuntime *ptr=thisvar->listhead;
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

int RuntimeHashremove(struct RuntimeHash *thisvar, int key, int data) {
    unsigned int hashkey = (unsigned int)key % thisvar->size;

    struct RuntimeNode **ptr = &thisvar->bucket[hashkey];
    int i;

    while (*ptr) {
        if ((*ptr)->key == key && (*ptr)->data == data) {
	  struct RuntimeNode *toremove=*ptr;
	  *ptr=(*ptr)->next;

	  toremove->inuse=0; /* Marked as unused */

	  thisvar->numelements--;
	  return 1;
        }
        ptr = &((*ptr)->next);
    }

    return 0;
}

void RuntimeHashaddAll(struct RuntimeHash *thisvar, struct RuntimeHash * set) {
    struct RuntimeIterator it;
    RuntimeHashiterator(set, &it);
    while(RunhasNext(&it)) {
        int keyv=Runkey(&it);
        int data=Runnext(&it);
        RuntimeHashadd(thisvar,keyv,data);
    }
}

int RuntimeHashadd(struct RuntimeHash * thisvar,int key, int data) {
  /* Rehash code */
  unsigned int hashkey;
  struct RuntimeNode **ptr;

  if (thisvar->numelements>=thisvar->size) {
    int newsize=2*thisvar->size+1;
    struct RuntimeNode ** newbucket = (struct RuntimeNode **) RUNMALLOC(sizeof(struct RuntimeNode *)*newsize);
    int i;
    for(i=thisvar->size-1;i>=0;i--) {
        struct RuntimeNode *ptr;
        for(ptr=thisvar->bucket[i];ptr!=NULL;) {
            struct RuntimeNode * nextptr=ptr->next;
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
    thisvar->listtail->nextarray=(struct ArrayRuntime *) RUNMALLOC(sizeof(struct ArrayRuntime));
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

bool RuntimeHashcontainskey(struct RuntimeHash *thisvar,int key) {
    unsigned int hashkey = (unsigned int)key % thisvar->size;

    struct RuntimeNode *ptr = thisvar->bucket[hashkey];
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

bool RuntimeHashcontainskeydata(struct RuntimeHash *thisvar, int key, int data) {
    unsigned int hashkey = (unsigned int)key % thisvar->size;

    struct RuntimeNode *ptr = thisvar->bucket[hashkey];
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

int RuntimeHashcount(struct RuntimeHash *thisvar,int key) {
    unsigned int hashkey = (unsigned int)key % thisvar->size;
    int count = 0;

    struct RuntimeNode *ptr = thisvar->bucket[hashkey];
    while (ptr) {
        if (ptr->key == key) {
            count++;
        }
        ptr = ptr->next;
    }
    return count;
}

struct RuntimeHash * RuntimeHashimageSet(struct RuntimeHash *thisvar, int key) {
  struct RuntimeHash * newset=allocateRuntimeHash(2*RuntimeHashcount(thisvar,key)+4);
  unsigned int hashkey = (unsigned int)key % thisvar->size;

  struct RuntimeNode *ptr = thisvar->bucket[hashkey];
  while (ptr) {
    if (ptr->key == key) {
        RuntimeHashadd(newset,ptr->data,ptr->data);
    }
    ptr = ptr->next;
  }
  return newset;
}

int RuntimeHashget(struct RuntimeHash *thisvar, int key, int *data) {
    unsigned int hashkey = (unsigned int)key % thisvar->size;

    struct RuntimeNode *ptr = thisvar->bucket[hashkey];
    while (ptr) {
        if (ptr->key == key) {
            *data = ptr->data;
            return 1; /* success */
        }
        ptr = ptr->next;
    }

    return 0; /* failure */
}

int RuntimeHashcountdata(struct RuntimeHash *thisvar,int data) {
    int count = 0;
    struct ArrayRuntime *ptr = thisvar->listhead;
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

inline struct RuntimeIterator * noargallocateRuntimeIterator() {
    return (struct RuntimeIterator*)RUNMALLOC(sizeof(struct RuntimeIterator));
}

inline struct RuntimeIterator * allocateRuntimeIterator(struct ArrayRuntime *start, struct ArrayRuntime *tl, int tlindex) {
    struct RuntimeIterator *thisvar=(struct RuntimeIterator*)RUNMALLOC(sizeof(struct RuntimeIterator));
    thisvar->cur = start;
    thisvar->index=0;
    thisvar->tailindex=tlindex;
    thisvar->tail=tl;
    return thisvar;
}

inline int RunhasNext(struct RuntimeIterator *thisvar) {
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

inline int Runnext(struct RuntimeIterator *thisvar) {
    return thisvar->cur->nodes[thisvar->index++].data;
}

inline int Runkey(struct RuntimeIterator *thisvar) {
    return thisvar->cur->nodes[thisvar->index].key;
}
