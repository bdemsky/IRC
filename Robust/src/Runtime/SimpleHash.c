#include "SimpleHash.h"
#ifdef RAW
#include <raw.h>
#else
#include <stdio.h>
#endif
#ifdef DMALLOC
#include "dmalloc.h"
#endif

/* SIMPLE HASH ********************************************************/
struct RuntimeIterator* RuntimeHashcreateiterator(struct RuntimeHash * thisvar) {
  return allocateRuntimeIterator(thisvar->listhead);
}

void RuntimeHashiterator(struct RuntimeHash *thisvar, struct RuntimeIterator * it) {
  it->cur=thisvar->listhead;
}

struct RuntimeHash * noargallocateRuntimeHash() {
  return allocateRuntimeHash(100);
}

struct RuntimeHash * allocateRuntimeHash(int size) {
  struct RuntimeHash *thisvar;  //=(struct RuntimeHash *)RUNMALLOC(sizeof(struct RuntimeHash));
  if (size <= 0) {
#ifdef RAW
    raw_test_done(0xb001);
#else
    printf("Negative Hashtable size Exception\n");
    exit(-1);
#endif
  }
  thisvar=(struct RuntimeHash *)RUNMALLOC(sizeof(struct RuntimeHash));
  thisvar->size = size;
  thisvar->bucket = (struct RuntimeNode **) RUNMALLOC(sizeof(struct RuntimeNode *)*size);
  /* Set allocation blocks*/
  thisvar->listhead=NULL;
  thisvar->listtail=NULL;
  /*Set data counts*/
  thisvar->numelements = 0;
  return thisvar;
}

void freeRuntimeHash(struct RuntimeHash *thisvar) {
  struct RuntimeNode *ptr=thisvar->listhead;
  RUNFREE(thisvar->bucket);
  while(ptr) {
    struct RuntimeNode *next=ptr->lnext;
    RUNFREE(ptr);
    ptr=next;
  }
  RUNFREE(thisvar);
}

inline int RuntimeHashcountset(struct RuntimeHash * thisvar) {
  return thisvar->numelements;
}

int RuntimeHashfirstkey(struct RuntimeHash *thisvar) {
  struct RuntimeNode *ptr=thisvar->listhead;
  return ptr->key;
}

int RuntimeHashremovekey(struct RuntimeHash *thisvar, int key) {
  unsigned int hashkey = (unsigned int)key % thisvar->size;

  struct RuntimeNode **ptr = &thisvar->bucket[hashkey];
  int i;

  while (*ptr) {
    if ((*ptr)->key == key) {
      struct RuntimeNode *toremove=*ptr;
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

int RuntimeHashremove(struct RuntimeHash *thisvar, int key, int data) {
  unsigned int hashkey = (unsigned int)key % thisvar->size;

  struct RuntimeNode **ptr = &thisvar->bucket[hashkey];
  int i;

  while (*ptr) {
    if ((*ptr)->key == key && (*ptr)->data == data) {
      struct RuntimeNode *toremove=*ptr;
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

void RuntimeHashrehash(struct RuntimeHash * thisvar) {
  int newsize=thisvar->size;
  struct RuntimeNode ** newbucket = (struct RuntimeNode **) RUNMALLOC(sizeof(struct RuntimeNode *)*newsize);
  int i;
  for(i=thisvar->size-1; i>=0; i--) {
    struct RuntimeNode *ptr;
    for(ptr=thisvar->bucket[i]; ptr!=NULL;) {
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

int RuntimeHashadd(struct RuntimeHash * thisvar,int key, int data) {
  /* Rehash code */
  unsigned int hashkey;
  struct RuntimeNode **ptr;

  if (thisvar->numelements>=thisvar->size) {
    int newsize=2*thisvar->size+1;
    struct RuntimeNode ** newbucket = (struct RuntimeNode **) RUNMALLOC(sizeof(struct RuntimeNode *)*newsize);
    int i;
    for(i=thisvar->size-1; i>=0; i--) {
      struct RuntimeNode *ptr;
      for(ptr=thisvar->bucket[i]; ptr!=NULL;) {
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

  {
    struct RuntimeNode *node=RUNMALLOC(sizeof(struct RuntimeNode));
    node->data=data;
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

#ifdef RAW
int RuntimeHashadd_I(struct RuntimeHash * thisvar,int key, int data) {
  /* Rehash code */
  unsigned int hashkey;
  struct RuntimeNode **ptr;

  if (thisvar->numelements>=thisvar->size) {
    int newsize=2*thisvar->size+1;
    struct RuntimeNode ** newbucket = (struct RuntimeNode **) RUNMALLOC_I(sizeof(struct RuntimeNode *)*newsize);
    int i;
    for(i=thisvar->size-1; i>=0; i--) {
      struct RuntimeNode *ptr;
      for(ptr=thisvar->bucket[i]; ptr!=NULL;) {
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

  {
    struct RuntimeNode *node=RUNMALLOC_I(sizeof(struct RuntimeNode));
    node->data=data;
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
      return 1;       /* success */
    }
    ptr = ptr->next;
  }

  return 0;   /* failure */
}

inline struct RuntimeIterator * noargallocateRuntimeIterator() {
  return (struct RuntimeIterator*)RUNMALLOC(sizeof(struct RuntimeIterator));
}

inline struct RuntimeIterator * allocateRuntimeIterator(struct RuntimeNode *start) {
  struct RuntimeIterator *thisvar=(struct RuntimeIterator*)RUNMALLOC(sizeof(struct RuntimeIterator));
  thisvar->cur = start;
  return thisvar;
}

inline int RunhasNext(struct RuntimeIterator *thisvar) {
  return (thisvar->cur!=NULL);
}

inline int Runnext(struct RuntimeIterator *thisvar) {
  int curr=thisvar->cur->data;
  thisvar->cur=thisvar->cur->lnext;
  return curr;
}

inline int Runkey(struct RuntimeIterator *thisvar) {
  return thisvar->cur->key;
}
