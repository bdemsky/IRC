#include "MGCHash.h"
#ifdef MULTICORE
#include "runtime_arch.h"
#else
#include <stdio.h>
#endif
#ifdef DMALLOC
#include "dmalloc.h"
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


/* MGCHASH ********************************************************/
mgchashlistnode_t *mgc_table;
unsigned int mgc_size;
unsigned INTPTR mgc_mask;
unsigned int mgc_numelements;
unsigned int mgc_threshold;
double mgc_loadfactor;
mgcliststruct_t *mgc_structs;

void mgchashCreate(unsigned int size, double loadfactor) {
  mgchashtable_t *ctable;
  mgchashlistnode_t *nodes;
  int i;

  // Allocate space for the hash table
  mgc_table = RUNMALLOC(size*sizeof(mgchashlistnode_t));
  mgc_loadfactor = loadfactor;
  mgc_size = size;
  mgc_threshold=size*loadfactor;
	
#ifdef BIT64
  mgc_mask = ((size << 6)-1)&~(15UL);
#else
  mgc_mask = ((size << 6)-1)&~15;
#endif

  mgc_structs=RUNMALLOC(1*sizeof(mgcliststruct_t));
  mgc_numelements = 0; // Initial number of elements in the hash
}

void mgchashreset() {
  mgchashlistnode_t *ptr = mgc_table;
  int i;

  /*if (mgc_numelements<(mgc_size>>6)) {
    mgchashlistnode_t *top=&ptr[mgc_size];
    mgchashlistnode_t *tmpptr=mgc_list;
    while(tmpptr!=NULL) {
      mgchashlistnode_t *next=tmpptr->lnext;
      if (tmpptr>=ptr&&tmpptr<top) {
				//zero in list
				tmpptr->key=NULL;
				tmpptr->next=NULL;
      }
      tmpptr=next;
    }
  } else {*/
	  BAMBOO_MEMSET_WH(mgc_table, '\0', sizeof(mgchashlistnode_t)*mgc_size);
  //}
  while(mgc_structs->next!=NULL) {
    mgcliststruct_t *next=mgc_structs->next;
    RUNFREE(mgc_structs);
    mgc_structs=next;
  }
  mgc_structs->num = 0;
  mgc_numelements = 0;
}

//Store objects and their pointers into hash
void mgchashInsert(void * key, void *val) {
  mgchashlistnode_t *ptr;

  if(mgc_numelements > (mgc_threshold)) {
    //Resize
    unsigned int newsize = mgc_size << 1 + 1;
    mgchashResize(newsize);
  }

	//int hashkey = (unsigned int)key % mgc_size; 
  ptr=&mgc_table[(((unsigned INTPTR)key)&mgc_mask)>>6];//&mgc_table[hashkey];
  mgc_numelements++;

  if(ptr->key==0) {
		// the first time insert a value for the key
    ptr->key=key;
    ptr->val=val;
  } else { // Insert in the beginning of linked list
    mgchashlistnode_t * node;
    if (mgc_structs->num<NUMMGCLIST) {
      node=&mgc_structs->array[mgc_structs->num];
      mgc_structs->num++;
    } else {
      //get new list
      mgcliststruct_t *tcl=RUNMALLOC(1*sizeof(mgcliststruct_t));
      tcl->next=mgc_structs;
      mgc_structs=tcl;
      node=&tcl->array[0];
      tcl->num=1;
    }
    node->key = key;
    node->val = val;
    node->next = ptr->next;
    ptr->next = node;
  }
}

#ifdef MULTICORE
void mgchashInsert_I(void * key, void *val) {
  mgchashlistnode_t *ptr;

  if(mgc_numelements > (mgc_threshold)) {
    //Resize
    unsigned int newsize = mgc_size << 1 + 1;
    mgchashResize_I(newsize);
  }

	//int hashkey = (unsigned int)key % mgc_size; 
  //ptr=&mgc_table[hashkey];
  ptr = &mgc_table[(((unsigned INTPTR)key)&mgc_mask)>>6];
  mgc_numelements++;

  if(ptr->key==0) {
    ptr->key=key;
    ptr->val=val;
		return; 
  } else { // Insert in the beginning of linked list
    mgchashlistnode_t * node;
    if (mgc_structs->num<NUMMGCLIST) {
      node=&mgc_structs->array[mgc_structs->num];
      mgc_structs->num++;
    } else {
      //get new list
      mgcliststruct_t *tcl=RUNMALLOC_I(1*sizeof(mgcliststruct_t));
      tcl->next=mgc_structs;
      mgc_structs=tcl;
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
INLINE void * mgchashSearch(void * key) {
  //REMOVE HASH FUNCTION CALL TO MAKE SURE IT IS INLINED HERE]
	//int hashkey = (unsigned int)key % mgc_size;
  mgchashlistnode_t *node = &mgc_table[(((unsigned INTPTR)key)&mgc_mask)>>6];
		//&mgc_table[hashkey];

  do {
    if(node->key == key) {
      return node->val;
    }
    node = node->next;
  } while(node != NULL);

  return NULL;
}

unsigned int mgchashResize(unsigned int newsize) {
  mgchashlistnode_t *node, *ptr, *curr;    // curr and next keep track of the current and the next mgchashlistnodes in a linked list
  unsigned int oldsize;
  int isfirst;    // Keeps track of the first element in the chashlistnode_t for each bin in hashtable
  unsigned int i,index;
  unsigned int mask;

  ptr = mgc_table;
  oldsize = mgc_size;

  if((node = RUNMALLOC(newsize*sizeof(mgchashlistnode_t))) == NULL) {
    printf("Calloc error %s %d\n", __FILE__, __LINE__);
    return 1;
  }

  mgc_table = node;          //Update the global hashtable upon resize()
  mgc_size = newsize;
  mgc_threshold = newsize * mgc_loadfactor;
  mask=mgc_mask = (newsize << 6)-1;

  for(i = 0; i < oldsize; i++) {                        //Outer loop for each bin in hash table
    curr = &ptr[i];
    isfirst = 1;
    do {                      //Inner loop to go through linked lists
      void * key;
      mgchashlistnode_t *tmp,*next;

      if ((key=curr->key) == 0) {             //Exit inner loop if there the first element is 0
	break;                  //key = val =0 for element if not present within the hash table
      }
			//index = (unsigned int)key % mgc_size; 
      index = (((unsigned INTPTR)key) & mask) >>6;
      tmp=&node[index];
      next = curr->next;
      // Insert into the new table
      if(tmp->key == 0) {
				tmp->key = key;
				tmp->val = curr->val;
      } /*
          NOTE:  Add this case if you change this...
          This case currently never happens because of the way things rehash....
          else if (isfirst) {
          chashlistnode_t *newnode= calloc(1, sizeof(chashlistnode_t));
          newnode->key = curr->key;
          newnode->val = curr->val;
          newnode->next = tmp->next;
          tmp->next=newnode;
          } */
      else {
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
unsigned int mgchashResize_I(unsigned int newsize) {
  mgchashlistnode_t *node, *ptr, *curr;    // curr and next keep track of the current and the next mgchashlistnodes in a linked list
  unsigned int oldsize;
  int isfirst;    // Keeps track of the first element in the chashlistnode_t for each bin in hashtable
  unsigned int i,index;
  unsigned int mask;

  ptr = mgc_table;
  oldsize = mgc_size;

  if((node = RUNMALLOC_I(newsize*sizeof(mgchashlistnode_t))) == NULL) {
		BAMBOO_EXIT(0xe001);
    printf("Calloc error %s %d\n", __FILE__, __LINE__);
    return 1;
  }

  mgc_table = node;          //Update the global hashtable upon resize()
  mgc_size = newsize;
  mgc_threshold = newsize * mgc_loadfactor;
  mask=mgc_mask = (newsize << 6)-1;

  for(i = 0; i < oldsize; i++) {                        //Outer loop for each bin in hash table
    curr = &ptr[i];
    isfirst = 1;
    do {                      //Inner loop to go through linked lists
      void * key;
      mgchashlistnode_t *tmp,*next;

      if ((key=curr->key) == 0) {             
				//Exit inner loop if there the first element is 0
	      break;                  
				//key = val =0 for element if not present within the hash table
      }
			//index = (unsigned int)key % mgc_size; 
      index = (((unsigned INTPTR)key) & mask) >>6;
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
				mgchashlistnode_t *newnode=RUNMALLOC_I(1*sizeof(mgchashlistnode_t));
				newnode->key = curr->key;
				newnode->val = curr->val;
				newnode->next = tmp->next;
				tmp->next=newnode;
			} 
      else {
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
#endif

//Delete the entire hash table
void mgchashDelete() {
  int i;
  mgcliststruct_t *ptr=mgc_structs;
  while(ptr!=NULL) {
    mgcliststruct_t *next=ptr->next;
    RUNFREE(ptr);
    ptr=next;
  }
  RUNFREE(mgc_table);
  mgc_table=NULL;
  mgc_structs=NULL;
}

struct MGCHash * allocateMGCHash(int size,
		                             int conflicts) {
  struct MGCHash *thisvar;  
  if (size <= 0) {
#ifdef MULTICORE
    BAMBOO_EXIT(0xf101);
#else
    printf("Negative Hashtable size Exception\n");
    exit(-1);
#endif
  }
  thisvar=(struct MGCHash *)RUNMALLOC(sizeof(struct MGCHash));
  thisvar->size = size;
  thisvar->bucket = 
		(struct MGCNode *) RUNMALLOC(sizeof(struct MGCNode)*size);
	// zero out all the buckets
	BAMBOO_MEMSET_WH(thisvar->bucket, '\0', sizeof(struct MGCNode)*size);
  //Set data counts
  thisvar->num4conflicts = conflicts;
  return thisvar;
}

void freeMGCHash(struct MGCHash *thisvar) {
	int i = 0;
	for(i=thisvar->size-1; i>=0; i--) {
    struct MGCNode *ptr;
    for(ptr=thisvar->bucket[i].next; ptr!=NULL;) {
      struct MGCNode * nextptr=ptr->next;
			RUNFREE(ptr);
      ptr=nextptr;
    }
  }
  RUNFREE(thisvar->bucket);
  RUNFREE(thisvar);
}
/*
void MGCHashrehash(struct MGCHash * thisvar) {
  int newsize=thisvar->size;
  struct MGCNode ** newbucket = (struct MGCNode **) RUNMALLOC(sizeof(struct MGCNode *)*newsize);
  int i;
  for(i=thisvar->size-1; i>=0; i--) {
    struct MGCNode *ptr;
    for(ptr=thisvar->bucket[i]; ptr!=NULL;) {
      struct MGCNode * nextptr=ptr->next;
      unsigned int newhashkey=(unsigned int)ptr->key % newsize;
      ptr->next=newbucket[newhashkey];
      newbucket[newhashkey]=ptr;
      ptr=nextptr;
    }
  }
  thisvar->size=newsize;
  RUNFREE(thisvar->bucket);
  thisvar->bucket=newbucket;
}*/

int MGCHashadd(struct MGCHash * thisvar, int data) {
  // Rehash code 
  unsigned int hashkey;
  struct MGCNode *ptr;

  /*if (thisvar->numelements>=thisvar->size) {
    int newsize=2*thisvar->size+1;
    struct MGCNode ** newbucket = (struct MGCNode **) RUNMALLOC(sizeof(struct MGCNode *)*newsize);
    int i;
    for(i=thisvar->size-1; i>=0; i--) {
      struct MGCNode *ptr;
      for(ptr=thisvar->bucket[i]; ptr!=NULL;) {
	struct MGCNode * nextptr=ptr->next;
	unsigned int newhashkey=(unsigned int)ptr->key % newsize;
	ptr->next=newbucket[newhashkey];
	newbucket[newhashkey]=ptr;
	ptr=nextptr;
      }
    }
    thisvar->size=newsize;
    RUNFREE(thisvar->bucket);
    thisvar->bucket=newbucket;
  }*/

  hashkey = (unsigned int)data % thisvar->size;
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
int MGCHashadd_I(struct MGCHash * thisvar, int data) {
  // Rehash code 
  unsigned int hashkey;
  struct MGCNode *ptr;

  /*if (thisvar->numelements>=thisvar->size) {
    int newsize=2*thisvar->size+1;
    struct MGCNode ** newbucket = (struct MGCNode **) RUNMALLOC_I(sizeof(struct MGCNode *)*newsize);
    int i;
    for(i=thisvar->size-1; i>=0; i--) {
      struct MGCNode *ptr;
      for(ptr=thisvar->bucket[i]; ptr!=NULL;) {
	struct MGCNode * nextptr=ptr->next;
	unsigned int newhashkey=(unsigned int)ptr->key % newsize;
	ptr->next=newbucket[newhashkey];
	newbucket[newhashkey]=ptr;
	ptr=nextptr;
      }
    }
    thisvar->size=newsize;
    RUNFREE(thisvar->bucket);
    thisvar->bucket=newbucket;
  }*/

  hashkey = (unsigned int)data % thisvar->size;
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
  unsigned int hashkey = (unsigned int)data % thisvar->size;

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

