#include "MGCHash.h"
#ifdef MULTICORE
#include "runtime_arch.h"
#else
#include <stdio.h>
#endif
#ifdef DMALLOC
#include "dmalloc.h"
#endif

#define GC_SHIFT_BITS 4

/* mgchash ********************************************************/
mgchashtable_t * mgchashCreate(unsigned int size, double loadfactor) {
  mgchashtable_t *ctable;
  mgchashlistnode_t *nodes;
  int i;

  if (size <= 0) {
#ifdef MULTICORE
    BAMBOO_EXIT();
#else
    printf("Negative Hashtable size Exception\n");
    exit(-1);
#endif
  }

  // Allocate space for the hash table
  ctable = (mgchashtable_t *)RUNMALLOC(sizeof(mgchashtable_t));
  if(ctable == NULL) {
    // Run out of local memory
    BAMBOO_EXIT();
  }
  ctable->table = (mgchashlistnode_t*)RUNMALLOC(size*sizeof(mgchashlistnode_t));
  if(ctable->table == NULL) {
    // Run out of local memory
    BAMBOO_EXIT();
  }
  ctable->loadfactor = loadfactor;
  ctable->size = size;
  ctable->threshold=size*loadfactor;

  ctable->mask = (size << (GC_SHIFT_BITS))-1;
  ctable->structs = (mgcliststruct_t*)RUNMALLOC(1*sizeof(mgcliststruct_t));
  ctable->numelements = 0; // Initial number of elements in the hash

  return ctable;
}

void mgchashreset(mgchashtable_t * tbl) {
  mgchashlistnode_t *ptr = tbl->table;
  int i;

  /*if (tbl->numelements<(tbl->size>>6)) {
	mgchashlistnode_t *top=&ptr[tbl->size];
	mgchashlistnode_t * list = tbl->list;
	while(list != NULL) {
      mgchashlistnode_t * next = list->lnext;
      if ((list >= ptr) && (list < top)) {
		//zero in list
        list->key=NULL;
        list->next=NULL;
      }
      list = next;
	}
  } else {*/
	BAMBOO_MEMSET_WH(tbl->table, '\0', sizeof(mgchashlistnode_t)*tbl->size);
  //}
  // TODO now never release any allocated memory, may need to be changed
  while(tbl->structs->next!=NULL) {
    mgcliststruct_t * next = tbl->structs->next;
    RUNFREE(tbl->structs);
    tbl->structs=next;
  }
  tbl->structs->num = 0;
  tbl->numelements = 0;
}

//Store objects and their pointers into hash
void mgchashInsert(mgchashtable_t * tbl, void * key, void *val) {
  mgchashlistnode_t *ptr;

  if(tbl->numelements > (tbl->threshold)) {
    //Resize
    unsigned int newsize = tbl->size << 1 + 1;
    mgchashResize(tbl, newsize);
  }

  ptr=&tbl->table[(((unsigned INTPTR)key)&tbl->mask)>>(GC_SHIFT_BITS)]; 
  tbl->numelements++;

  if(ptr->key==0) {
    // the first time insert a value for the key
    ptr->key=key;
    ptr->val=val;
  } else { // Insert in the beginning of linked list
    mgchashlistnode_t * node;
    if (tbl->structs->num<NUMMGCLIST) {
      node=&tbl->structs->array[tbl->structs->num];
      tbl->structs->num++;
    } else {
      //get new list
      mgcliststruct_t *tcl=RUNMALLOC(1*sizeof(mgcliststruct_t));
      tcl->next=tbl->structs;
      tbl->structs=tcl;
      node=&tcl->array[0];
      tcl->num=1;
    }
    node->key = key;
    node->val = val;
    node->next = ptr->next;
    ptr->next = node;
  }
}

#ifdef MULTICORE_GC
mgchashtable_t * mgchashCreate_I(unsigned int size, double loadfactor) {
  mgchashtable_t *ctable;
  mgchashlistnode_t *nodes;
  int i;

  if (size <= 0) {
#ifdef MULTICORE
    BAMBOO_EXIT();
#else
    printf("Negative Hashtable size Exception\n");
    exit(-1);
#endif
  }

  // Allocate space for the hash table
  ctable = (mgchashtable_t*)RUNMALLOC_I(sizeof(mgchashtable_t));
  if(ctable == NULL) {
    // Run out of local memory
    BAMBOO_EXIT();
  }
  ctable->table=(mgchashlistnode_t*)RUNMALLOC_I(size*sizeof(mgchashlistnode_t));
  if(ctable->table == NULL) {
    // Run out of local memory
    BAMBOO_EXIT();
  }
  ctable->loadfactor = loadfactor;
  ctable->size = size;
  ctable->threshold=size*loadfactor;

  ctable->mask = (size << (GC_SHIFT_BITS))-1;
  ctable->structs = (mgcliststruct_t*)RUNMALLOC_I(1*sizeof(mgcliststruct_t));
  ctable->numelements = 0; // Initial number of elements in the hash

  return ctable;
}

void mgchashInsert_I(mgchashtable_t * tbl, void * key, void *val) {
  mgchashlistnode_t *ptr;

  if(tbl->numelements > (tbl->threshold)) {
    //Resize
    unsigned int newsize = tbl->size << 1 + 1;
    mgchashResize_I(tbl, newsize);
  }

  ptr = &tbl->table[(((unsigned INTPTR)key)&tbl->mask)>>(GC_SHIFT_BITS)];
  tbl->numelements++;

  if(ptr->key==0) {
    ptr->key=key;
    ptr->val=val;
    return;
  } else { // Insert in the beginning of linked list
    mgchashlistnode_t * node;
    if (tbl->structs->num<NUMMGCLIST) {
      node=&tbl->structs->array[tbl->structs->num];
      tbl->structs->num++;
    } else {
      //get new list
      mgcliststruct_t *tcl=RUNMALLOC_I(1*sizeof(mgcliststruct_t));
      tcl->next=tbl->structs;
      tbl->structs=tcl;
      node=&tcl->array[0];
      tcl->num=1;
    }
    node->key = key;
    node->val = val;
    node->next = ptr->next;
    ptr->next = node;
  }
}
#endif

// Search for an address for a given oid
void * mgchashSearch(mgchashtable_t * tbl, void * key) {
  //REMOVE HASH FUNCTION CALL TO MAKE SURE IT IS INLINED HERE]
  mgchashlistnode_t *node = 
	&tbl->table[(((unsigned INTPTR)key)&tbl->mask)>>(GC_SHIFT_BITS)];

  do {
    if(node->key == key) {
      return node->val;
    }
    node = node->next;
  } while(node != NULL);

  return NULL;
}

unsigned int mgchashResize(mgchashtable_t * tbl, unsigned int newsize) {
  mgchashlistnode_t *node, *ptr, *curr;  // curr and next keep track of the 
                                         // current and the next 
                                         // mgchashlistnodes in a linked list
  unsigned int oldsize;
  int isfirst;    // Keeps track of the first element in the 
                  // chashlistnode_t for each bin in hashtable
  unsigned int i,index;
  unsigned int mask;

  ptr = tbl->table;
  oldsize = tbl->size;

  if((node = RUNMALLOC(newsize*sizeof(mgchashlistnode_t))) == NULL) {
    printf("Calloc error %s %d\n", __FILE__, __LINE__);
    return 1;
  }

  tbl->table = node; //Update the global hashtable upon resize()
  tbl->size = newsize;
  tbl->threshold = newsize * tbl->loadfactor;
  mask = tbl->mask = (newsize << (GC_SHIFT_BITS)) - 1;

  for(i = 0; i < oldsize; i++) {   //Outer loop for each bin in hash table
    curr = &ptr[i];
    isfirst = 1;
    do {  //Inner loop to go through linked lists
      void * key;
      mgchashlistnode_t *tmp,*next;

      if ((key=curr->key) == 0) { 
        //Exit inner loop if there the first element is 0
        break;
        //key = val =0 for element if not present within the hash table
      }
      index = (((unsigned INTPTR)key) & mask) >> (GC_SHIFT_BITS);
      tmp=&node[index];
      next = curr->next;
      // Insert into the new table
      if(tmp->key == 0) {
        tmp->key = key;
        tmp->val = curr->val;
      } /*
	   NOTE:  Add this case if you change this...
	   This case currently never happens because of the way things rehash....*/
	   else if (isfirst) {
       mgchashlistnode_t *newnode= RUNMALLOC(1*sizeof(mgchashlistnode_t));
       newnode->key = curr->key;
       newnode->val = curr->val;
       newnode->next = tmp->next;
       tmp->next=newnode;
     } else {
       curr->next=tmp->next;
       tmp->next=curr;
      }

      isfirst = 0;
      curr = next;
    } while(curr!=NULL);
  }

  RUNFREE(ptr);            //Free the memory of the old hash table
  return 0;
}

#ifdef MULTICORE_GC
unsigned int mgchashResize_I(mgchashtable_t * tbl, unsigned int newsize) {
  mgchashlistnode_t *node, *ptr, *curr; // curr and next keep track of the 
                                        // current and the next 
                                        // mgchashlistnodes in a linked list
  unsigned int oldsize;
  int isfirst; // Keeps track of the first element in the chashlistnode_t 
               // for each bin in hashtable
  unsigned int i,index;
  unsigned int mask;

  ptr = tbl->table;
  oldsize = tbl->size;

  if((node = RUNMALLOC_I(newsize*sizeof(mgchashlistnode_t))) == NULL) {
    BAMBOO_EXIT();
    printf("Calloc error %s %d\n", __FILE__, __LINE__);
    return 1;
  }

  tbl->table = node;  //Update the global hashtable upon resize()
  tbl->size = newsize;
  tbl->threshold = newsize * tbl->loadfactor;
  mask = tbl->mask = (newsize << (GC_SHIFT_BITS))-1;

  for(i = 0; i < oldsize; i++) {  //Outer loop for each bin in hash table
    curr = &ptr[i];
    isfirst = 1;
    do { //Inner loop to go through linked lists
      void * key;
      mgchashlistnode_t *tmp,*next;

      if ((key=curr->key) == 0) {
        //Exit inner loop if there the first element is 0
        break;
        //key = val =0 for element if not present within the hash table
      }
      index = (((unsigned INTPTR)key) & mask) >> (GC_SHIFT_BITS);
      tmp=&node[index];
      next = curr->next;
      // Insert into the new table
      if(tmp->key == 0) {
        tmp->key = key;
        tmp->val = curr->val;
      } /*NOTE:  Add this case if you change this...
          This case currently never happens because of the way things rehash..*/
      else if (isfirst) {
        mgchashlistnode_t *newnode=RUNMALLOC_I(1*sizeof(mgchashlistnode_t)); 
        newnode->key = curr->key;
        newnode->val = curr->val;
        newnode->next = tmp->next;
        tmp->next=newnode;
      } else {
        curr->next=tmp->next;
        tmp->next=curr;
      }

      isfirst = 0;
      curr = next;
    } while(curr!=NULL);
  }
  RUNFREE_I(ptr); //Free the memory of the old hash table
  return 0;
}
#endif

//Delete the entire hash table
void mgchashDelete(mgchashtable_t * tbl) {
  int i;
  mgcliststruct_t *ptr=tbl->structs;
  while(ptr!=NULL) {
    mgcliststruct_t *next=ptr->next;
    RUNFREE(ptr);
    ptr=next;
  }
  RUNFREE(tbl->table);
  tbl->table=NULL;
  tbl->structs=NULL;
}

/* MGCHASH ********************************************************/

struct MGCHash * allocateMGCHash(int size,
                                 int conflicts) {
  struct MGCHash *thisvar;
  if (size <= 0) {
#ifdef MULTICORE
    BAMBOO_EXIT();
#else
    printf("Negative Hashtable size Exception\n");
    exit(-1);
#endif
  }
  thisvar=(struct MGCHash *)RUNMALLOC(sizeof(struct MGCHash));
  thisvar->size = size;
  thisvar->bucket=(struct MGCNode *) RUNMALLOC(sizeof(struct MGCNode)*size);
  //Set data counts
  thisvar->num4conflicts = conflicts;
  return thisvar;
}

void freeMGCHash(struct MGCHash *thisvar) {
  int i = 0;
  for(i=thisvar->size-1; i>=0; i--) {
    struct MGCNode *ptr;
    for(ptr=thisvar->bucket[i].next; ptr!=NULL; ) {
      struct MGCNode * nextptr=ptr->next;
      RUNFREE(ptr);
      ptr=nextptr;
    }
  }
  RUNFREE(thisvar->bucket);
  RUNFREE(thisvar);
}

int MGCHashadd(struct MGCHash * thisvar, int data) {
  // Rehash code
  unsigned int hashkey;
  struct MGCNode *ptr;

  int mask = (thisvar->size << (GC_SHIFT_BITS))-1;
  hashkey = (((unsigned INTPTR)data)&mask)>>(GC_SHIFT_BITS); 
  ptr = &thisvar->bucket[hashkey];

  struct MGCNode * prev = NULL;
  if(ptr->data < thisvar->num4conflicts) {
    struct MGCNode *node=RUNMALLOC(sizeof(struct MGCNode));
    node->data=data;
    node->next=(ptr->next);
    ptr->next=node;
    ptr->data++;
  } else {
    while (ptr->next!=NULL) {
      prev = ptr;
      ptr = ptr->next;
    }
    ptr->data = data;
    ptr->next = thisvar->bucket[hashkey].next;
    thisvar->bucket[hashkey].next = ptr;
    prev->next = NULL;
  }

  return 1;
}

#ifdef MULTICORE
struct MGCHash * allocateMGCHash_I(int size,int conflicts) {
  struct MGCHash *thisvar;
  if (size <= 0) {
#ifdef MULTICORE
    BAMBOO_EXIT();
#else
    printf("Negative Hashtable size Exception\n");
    exit(-1);
#endif
  }
  thisvar=(struct MGCHash *)RUNMALLOC_I(sizeof(struct MGCHash));
  thisvar->size = size;
  thisvar->bucket=(struct MGCNode *) RUNMALLOC_I(sizeof(struct MGCNode)*size);
  //Set data counts
  thisvar->num4conflicts = conflicts;
  return thisvar;
}

int MGCHashadd_I(struct MGCHash * thisvar, int data) {
  // Rehash code
  unsigned int hashkey;
  struct MGCNode *ptr;

  int mask = (thisvar->size << (GC_SHIFT_BITS))-1;
  hashkey = (((unsigned INTPTR)data)&mask)>>(GC_SHIFT_BITS);
  ptr = &thisvar->bucket[hashkey];

  struct MGCNode * prev = NULL;
  if(ptr->data < thisvar->num4conflicts) {
    struct MGCNode *node=RUNMALLOC_I(sizeof(struct MGCNode));
    node->data=data;
    node->next=(ptr->next);
    ptr->next=node;
    ptr->data++;
  } else {
    while (ptr->next!=NULL) {
      prev = ptr;
      ptr = ptr->next;
    }
    ptr->data = data;
    ptr->next = thisvar->bucket[hashkey].next;
    thisvar->bucket[hashkey].next = ptr;
    prev->next = NULL;
  }

  return 1;
}
#endif

int MGCHashcontains(struct MGCHash *thisvar, int data) {
  int mask = (thisvar->size << (GC_SHIFT_BITS))-1;
  unsigned int hashkey = (((unsigned INTPTR)data)&mask)>>(GC_SHIFT_BITS);

  struct MGCNode *ptr = thisvar->bucket[hashkey].next;
  struct MGCNode *prev = NULL;
  while (ptr!=NULL) {
    if (ptr->data == data) {
      if(prev != NULL) {
        prev->next = NULL;
        ptr->next = thisvar->bucket[hashkey].next;
        thisvar->bucket[hashkey].next = ptr;
      }

      return 1;       // success
    }
    prev = ptr;
    ptr = ptr->next;
  }

  return 0;   // failure
}

