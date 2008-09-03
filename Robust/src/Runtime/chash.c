#include "chash.h"

void crehash(ctable_t *table) {
  cResize(table, table->size);
}

ctable_t *cCreate(unsigned int size, float loadfactor) {
  ctable_t *ctable;
  cnode_t *nodes;
  int i;

  if((ctable = calloc(1, sizeof(ctable_t))) == NULL) {
    printf("Calloc error %s %d\n", __FILE__, __LINE__);
    return NULL;
  }

  // Allocate space for the hash table
  if((nodes = calloc(size, sizeof(cnode_t))) == NULL) {
    printf("Calloc error %s %d\n", __FILE__, __LINE__);
    free(ctable);
    return NULL;
  }

  ctable->table = nodes;
  ctable->size = size;
  ctable->mask = (size << 2)-1;
  ctable->numelements = 0; // Initial number of elements in the hash
  ctable->loadfactor = loadfactor;
  ctable->resize=loadfactor*size;
  ctable->listhead=NULL;

  return ctable;
}

//Store objects and their pointers into hash
INLINE void cInsert(ctable_t *table, unsigned int key, void *val) {
  unsigned int newsize;
  int index;
  cnode_t *ptr, *node;

  ptr = &table->table[(key & table->mask)>>2];
  if (ptr->key==0) {
    ptr->key=key;
    ptr->val=val;
    ptr->lnext=table->listhead;
    table->listhead=ptr;
    return;
  }

  cnode_t *tmp=malloc(sizeof(cnode_t));
  tmp->next=ptr->next;
  ptr->next=tmp;
  tmp->key=key;
  tmp->val=val;
  tmp->lnext=table->listhead;
  table->listhead=tmp;

  table->numelements++;
  if(table->numelements > table->resize) {
    newsize = table->size << 1;
    cResize(table,newsize);
  }
}

// Search for an address for a given oid
INLINE void * cSearch(ctable_t *table, unsigned int key) {
  //REMOVE HASH FUNCTION CALL TO MAKE SURE IT IS INLINED HERE
  cnode_t *node = &table->table[(key & table->mask)>>2];

  while(node != NULL) {
    if(node->key == key) {
      return node->val;
    }
    node = node->next;
  }
  return NULL;
}

unsigned int cRemove(ctable_t *table, unsigned int key) {
  int index;
  cnode_t *curr, *prev;
  cnode_t *ptr, *node;

  ptr = table->table;
  index =(key & table->mask)>>2;
  curr = &ptr[index];

  for (; curr != NULL; curr = curr->next) {
    if (curr->key == key) {         // Find a match in the hash table
      table->numelements--;  // Decrement the number of elements in the global hashtable
      if ((curr == &ptr[index]) && (curr->next == NULL)) {  // Delete the first item inside the hashtable with no linked list of cnode_t
	curr->key = 0;
	curr->val = NULL;
      } else if ((curr == &ptr[index]) && (curr->next != NULL)) { //Delete the first item with a linked list of cnode_t  connected
	curr->key = curr->next->key;
	curr->val = curr->next->val;
	node = curr->next;
	curr->next = curr->next->next;
	free(node);
      } else {                                          // Regular delete from linked listed
	prev->next = curr->next;
	free(curr);
      }
      return 0;
    }
    prev = curr;
  }
  return 1;
}

unsigned int cResize(ctable_t *table, unsigned int newsize) {
  int i;
  cnode_t *last=NULL;
  int mask=(newsize<<2)-1;
  int oldsize = table->size;
  cnode_t *ptr = table->table;
  cnode_t * ntable=calloc(newsize, sizeof(cnode_t));

  table->table = ntable;
  table->size = newsize;
  table->mask = mask;
  table->resize=newsize*table->loadfactor;

  for(i = 0; i < oldsize; i++) {
    int isfirst=1;
    cnode_t * curr=&ptr[i];
    if (curr->key==0)
      continue;
    while(curr!=NULL) {
      cnode_t * next = curr->next;
      int index =(curr->key & mask)>>2;
      cnode_t * newnode=&ntable[index];

      if(newnode->key==0) {
	newnode->key=curr->key;
	newnode->val=curr->val;
	newnode->lnext=last;
	last=newnode;
      } else {
	cnode_t *tmp=malloc(sizeof(cnode_t));
	tmp->next=newnode->next;
	newnode->next=tmp;
	tmp->key=curr->key;
	tmp->val=curr->val;
	tmp->lnext=last;
	last=tmp;
      }
      if (isfirst) {
	isfirst=0;
      } else {
	free(curr);
      }
      curr = next;
    }
  }
  table->listhead=last;
  free(ptr);            //Free the memory of the old hash table
  return 0;
}

//Delete the entire hash table
void cDelete(ctable_t *ctable) {
  int i, isFirst;
  cnode_t *ptr, *curr, *next;
  ptr = ctable->table;

  for(i=0 ; i<ctable->size ; i++) {
    curr = &ptr[i];
    isFirst = 1 ;
    while(curr  != NULL) {
      next = curr->next;
      if(isFirst != 1) {
	free(curr);
      }
      isFirst = 0;
      curr = next;
    }
  }

  free(ptr);
  ptr = NULL;
  free(ctable);
  ctable = NULL;
}
