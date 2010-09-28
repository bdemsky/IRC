#ifdef MULTICORE_GC

#include "GCSharedHash.h"
#ifdef MULTICORE
#include "runtime_arch.h"
#else
#include <stdio.h>
#endif

#ifndef INTPTR
#ifdef BIT64
#define INTPTR long
#define INTPTRSHIFT 3
#else
#define INTPTR int
#define INTPTRSHIFT 2
#endif
#endif

#ifndef INLINE
#define INLINE    inline __attribute__((always_inline))
#endif // #ifndef INLINE

#define GC_SHIFT_BITS  4

/* GCSHARED HASH ********************************************************/

// params: startaddr -- the start addr of the shared memory
//         rsize -- remaining size of the available shared memory
struct GCSharedHash * noargallocateGCSharedHash() {
  return allocateGCSharedHash(100);
}

struct GCSharedHash * allocateGCSharedHash(int size) {
  struct GCSharedHash *thisvar; 
  if (size <= 0) {
#ifdef MULTICORE
    BAMBOO_EXIT(0xf301);
#else
    printf("Negative Hashtable size Exception\n");
    exit(-1);
#endif
  } 
  thisvar=(struct GCSharedHash *)FREEMALLOC_NGC(sizeof(struct GCSharedHash));
  if(thisvar == NULL) {
	return NULL;
  }
  thisvar->size = size;
  thisvar->bucket = 
	(struct GCSharedNode **)FREEMALLOC_NGC(sizeof(struct GCSharedNode *)*size);
  if(thisvar->bucket == NULL) {
	FREE_NGC(thisvar);
	return NULL;
  }
  /* Set allocation blocks*/
  thisvar->listhead=NULL;
  thisvar->listtail=NULL;
  /*Set data counts*/
  thisvar->numelements = 0;
  return thisvar;
}

void freeGCSharedHash(struct GCSharedHash *thisvar) {
  struct GCSharedNode *ptr=thisvar->listhead;
  FREE_NGC(thisvar->bucket);
  while(ptr) {
    struct GCSharedNode *next=ptr->lnext;
    FREE_NGC(ptr);
    ptr=next;
  }
  FREE_NGC(thisvar);
}

bool GCSharedHashrehash(struct GCSharedHash * thisvar) {
  int newsize=thisvar->size;
  struct GCSharedNode ** newbucket = (struct GCSharedNode **)
	FREEMALLOC_NGC(sizeof(struct GCSharedNode *)*newsize);
  if(newbucket == NULL) {
	return false;
  }
  int i;
  for(i=thisvar->size-1; i>=0; i--) {
    struct GCSharedNode *ptr;
    for(ptr=thisvar->bucket[i]; ptr!=NULL;) {
      struct GCSharedNode * nextptr=ptr->next;
      unsigned int newhashkey=(unsigned int)ptr->key % newsize;
      ptr->next=newbucket[newhashkey];
      newbucket[newhashkey]=ptr;
      ptr=nextptr;
    }
  }
  thisvar->size=newsize;
  FREE_NGC(thisvar->bucket);
  thisvar->bucket=newbucket;
  return true;
}

int GCSharedHashadd(struct GCSharedHash * thisvar,int key, int data) {
  /* Rehash code */
  unsigned int hashkey;
  struct GCSharedNode **ptr;

  if (thisvar->numelements>=thisvar->size) {
    int newsize=2*thisvar->size+1;
    struct GCSharedNode ** newbucket = 
	  (struct GCSharedNode **)FREEMALLOC_NGC(
		  sizeof(struct GCSharedNode *)*newsize);
	if(newbucket == NULL) {
	  return -1;
	}
    int i;
    for(i=thisvar->size-1; i>=0; i--) {
      struct GCSharedNode *ptr;
      for(ptr=thisvar->bucket[i]; ptr!=NULL;) {
	struct GCSharedNode * nextptr=ptr->next;
	unsigned int newhashkey=(unsigned int)ptr->key % newsize;
	ptr->next=newbucket[newhashkey];
	newbucket[newhashkey]=ptr;
	ptr=nextptr;
      }
    }
    thisvar->size=newsize;
    FREE_NGC(thisvar->bucket);
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
    struct GCSharedNode *node=FREEMALLOC_NGC(sizeof(struct GCSharedNode));
	if(node == NULL) {
	  return -1;
	}
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

#ifdef MULTICORE 
struct GCSharedHash * allocateGCSharedHash_I(int size) {
  struct GCSharedHash *thisvar;
  if (size <= 0) {
#ifdef MULTICORE
    BAMBOO_EXIT(0xf302);
#else
    printf("Negative Hashtable size Exception\n");
    exit(-1);
#endif
  }
  thisvar=(struct GCSharedHash *)FREEMALLOC_NGC_I(sizeof(struct GCSharedHash));
  if(thisvar == NULL) {
	return NULL;
  }
  thisvar->size = size;
  thisvar->bucket = 
	(struct GCSharedNode **)FREEMALLOC_NGC_I(
		sizeof(struct GCSharedNode *)*size);
  if(thisvar->bucket == NULL) {
	FREE_NGC_I(thisvar);
	return NULL;
  }
  /* Set allocation blocks*/
  thisvar->listhead=NULL;
  thisvar->listtail=NULL;
  /*Set data counts*/
  thisvar->numelements = 0;
  return thisvar;
}

int GCSharedHashadd_I(struct GCSharedHash * thisvar,int key, int data) {
  /* Rehash code */
  unsigned int hashkey;
  struct GCSharedNode **ptr;

  if (thisvar->numelements>=thisvar->size) {
    int newsize=2*thisvar->size+1;
    struct GCSharedNode ** newbucket = 
	  (struct GCSharedNode **)FREEMALLOC_NGC_I(
		  sizeof(struct GCSharedNode *)*newsize);
	if(newbucket == NULL) {
	  return -1;
	}
    int i;
    for(i=thisvar->size-1; i>=0; i--) {
      struct GCSharedNode *ptr;
      for(ptr=thisvar->bucket[i]; ptr!=NULL;) {
	struct GCSharedNode * nextptr=ptr->next;
	unsigned int newhashkey=(unsigned int)ptr->key % newsize;
	ptr->next=newbucket[newhashkey];
	newbucket[newhashkey]=ptr;
	ptr=nextptr;
      }
    }
    thisvar->size=newsize;
    FREE_NGC_I(thisvar->bucket);
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
    struct GCSharedNode *node=FREEMALLOC_NGC_I(sizeof(struct GCSharedNode));
	if(node == NULL) {
	  return -1;
	}
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

int GCSharedHashget(struct GCSharedHash *thisvar, int key, int *data) {
  unsigned int hashkey = (unsigned int)key % thisvar->size;

  struct GCSharedNode *ptr = thisvar->bucket[hashkey];
  while (ptr) {
    if (ptr->key == key) {
      *data = ptr->data;
      return 1;       /* success */
    }
    ptr = ptr->next;
  }

  return 0;   /* failure */
}

/* MGCSHAREDHASH ********************************************************/

mgcsharedhashtbl_t * mgcsharedhashCreate(unsigned int size, 
                                         double loadfactor) {
  mgcsharedhashtbl_t * ctable;
  mgcsharedhashlistnode_t * nodes;
  int i;

  ctable = (mgcsharedhashtbl_t *)FREEMALLOC_NGC(sizeof(mgcsharedhashtbl_t));
  if(ctable == NULL) {
	// TODO
	BAMBOO_EXIT(0xf303);
	return NULL;
  }
  // Allocate space for the hash table
  ctable->table = (mgcsharedhashlistnode_t *)FREEMALLOC_NGC(
	  size*sizeof(mgcsharedhashlistnode_t));
  if(ctable->table == NULL) {
	BAMBOO_EXIT(0xf304); // TODO
	return NULL;
  }
  ctable->size = size;
  ctable->loadfactor = loadfactor;
  ctable->threshold = size*loadfactor;

  ctable->mask = (size << (GC_SHIFT_BITS))-1;

  ctable->structs = NULL ; 
  ctable->numelements = 0; // Initial number of elements in the hash
  ctable->list = NULL;

  return ctable;
}

mgcsharedhashtbl_t * mgcsharedhashCreate_I(unsigned int size, 
                                           double loadfactor) {
  mgcsharedhashtbl_t * ctable;
  mgcsharedhashlistnode_t * nodes;
  int i;

  ctable = (mgcsharedhashtbl_t *)FREEMALLOC_NGC_I(sizeof(mgcsharedhashtbl_t));
  if(ctable == NULL) {
	// TODO
	BAMBOO_EXIT(0xf305);
	return NULL;
  }
  // Allocate space for the hash table
  ctable->table = (mgcsharedhashlistnode_t *)FREEMALLOC_NGC_I(
	  size*sizeof(mgcsharedhashlistnode_t));
  if(ctable->table == NULL) {
	BAMBOO_EXIT(0xf306); // TODO
	return NULL;
  }
  ctable->size = size;
  ctable->loadfactor = loadfactor;
  ctable->threshold = size*loadfactor;

  ctable->mask = (size << (GC_SHIFT_BITS))-1;

  ctable->structs = NULL ; 
  ctable->numelements = 0; // Initial number of elements in the hash
  ctable->list = NULL;

  return ctable;
}

void mgcsharedhashReset(mgcsharedhashtbl_t * tbl) {
  mgcsharedhashlistnode_t * ptr = tbl->table;

  if ((tbl->numelements) < (tbl->size>>6)) {
	mgcsharedhashlistnode_t *top = &ptr[tbl->size];
	mgcsharedhashlistnode_t * list = tbl->list;
	while(list != NULL) {  
      mgcsharedhashlistnode_t * next = list->next;
      if ((list >= ptr) && (list < top)) {
		//zero in list
        list->key=NULL;
        list->next=NULL;
      }
      list = next;
	}
  } else {
	BAMBOO_MEMSET_WH(tbl->table, '\0', 
		sizeof(mgcsharedhashlistnode_t)*tbl->size);
  }

  mgcsharedliststruct_t * structs = tbl->structs;
  while(structs != NULL) {
    mgcsharedliststruct_t * next = structs->next;
	BAMBOO_MEMSET_WH(structs->array, '\0', 
		structs->num * sizeof(mgcsharedhashlistnode_t));
	structs->num = 0;
    structs = next;
  }
  tbl->numelements = 0;
}

//Store objects and their pointers into hash
//Using open addressing
int mgcsharedhashInsert(mgcsharedhashtbl_t * tbl, void * key, void * val) {
  mgcsharedhashlistnode_t * ptr;

  if(tbl->numelements > (tbl->threshold)) {
    //Never resize, simply don't insert any more
    return -1;
  }

  ptr=&tbl->table[(((unsigned INTPTR)key)&tbl->mask)>>(GC_SHIFT_BITS)];

  if(ptr->key==0) {
    // the first time insert a value for the key
    ptr->key=key;
    ptr->val=val;
  } else { // Insert to the next empty place
	mgcsharedhashlistnode_t *top = &tbl->table[tbl->size];
    do {
	  ptr++;
	} while((ptr < top) && (ptr->key != NULL));
	if(ptr >= top) {
	  return -1;
	} else {
	  ptr->key = key;
	  ptr->val = val;
	}
  }
  ptr->next = tbl->list;
  tbl->list = ptr;
  tbl->numelements++;
  return 1;
}

int mgcsharedhashInsert_I(mgcsharedhashtbl_t * tbl, void * key, void * val) {
  mgcsharedhashlistnode_t * ptr;

  if(tbl->numelements > (tbl->threshold)) {
    //Never resize, simply don't insert any more
    return -1;
  }

  ptr=&tbl->table[(((unsigned INTPTR)key)&tbl->mask)>>(GC_SHIFT_BITS)];

  if(ptr->key==0) {
    // the first time insert a value for the key
    ptr->key=key;
    ptr->val=val;
  } else { // Insert to the next empty place
	mgcsharedhashlistnode_t * top = &tbl->table[tbl->size];
	mgcsharedhashlistnode_t * start = ptr;
    do {
	  ptr++;
	  if(ptr->key == 0) {
		break;
	  }
	} while(ptr < top);
	if(ptr >= top) {
	  return -1;
	} else {
	  ptr->key = key;
	  ptr->val = val;
	}
  }
  ptr->next = tbl->list;
  tbl->list = ptr;
  tbl->numelements++;
  return 1;
}

// Search for an address for a given oid
INLINE void * mgcsharedhashSearch(mgcsharedhashtbl_t * tbl, void * key) {
  //REMOVE HASH FUNCTION CALL TO MAKE SURE IT IS INLINED HERE]
  mgcsharedhashlistnode_t * node = 
	&tbl->table[(((unsigned INTPTR)key)&tbl->mask)>>(GC_SHIFT_BITS)];
  mgcsharedhashlistnode_t *top = &tbl->table[tbl->size];

  do {
    if(node->key == key) {
      return node->val;
    }
    node++;
  } while(node < top);

  return NULL;
}

#endif
