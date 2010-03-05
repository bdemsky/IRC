#include "clookup.h"

#define NUMCLIST 250
typedef struct clist {
  struct chashlistnode array[NUMCLIST];
  int num;
  struct clist *next;
} cliststruct_t;

__thread chashlistnode_t *c_table;
__thread unsigned int c_size;
__thread unsigned int c_mask;
__thread unsigned int c_numelements;
__thread unsigned int c_threshold;
__thread double c_loadfactor;
__thread cliststruct_t *c_structs;

void t_chashCreate(unsigned int size, double loadfactor) {
  chashtable_t *ctable;
  chashlistnode_t *nodes;
  int i;

  // Allocate space for the hash table
  

  c_table = calloc(size, sizeof(chashlistnode_t));
  c_loadfactor = loadfactor;
  c_size = size;
  c_threshold=size*loadfactor;
  c_mask = (size << 1)-1;
  c_structs=calloc(1,sizeof(cliststruct_t));
  c_numelements = 0; // Initial number of elements in the hash
}

chashtable_t *chashCreate(unsigned int size, double loadfactor) {
  chashtable_t *ctable;
  chashlistnode_t *nodes;
  int i;

  if((ctable = calloc(1, sizeof(chashtable_t))) == NULL) {
    printf("Calloc error %s %d\n", __FILE__, __LINE__);
    return NULL;
  }

  // Allocate space for the hash table
  if((nodes = calloc(size, sizeof(chashlistnode_t))) == NULL) {
    printf("Calloc error %s %d\n", __FILE__, __LINE__);
    free(ctable);
    return NULL;
  }

  ctable->table = nodes;
  ctable->loadfactor = loadfactor;
  ctable->size = size;
  ctable->threshold=size*loadfactor;
  ctable->mask = (size << 1)-1;
  ctable->numelements = 0; // Initial number of elements in the hash


  return ctable;
}

//Finds the right bin in the hash table
static INLINE unsigned int chashFunction(chashtable_t *table, unsigned int key) {
  return ( key & (table->mask))>>1; //throw away low order bit
}

//Store objects and their pointers into hash
void chashInsert(chashtable_t *table, unsigned int key, void *val) {
  chashlistnode_t *ptr;


  if(table->numelements > (table->threshold)) {
    //Resize
    unsigned int newsize = table->size << 1;
    chashResize(table,newsize);
  }

  ptr = &table->table[(key&table->mask)>>1];
  table->numelements++;

  if(ptr->key==0) {
    ptr->key=key;
    ptr->val=val;
  } else { // Insert in the beginning of linked list
    chashlistnode_t * node = calloc(1, sizeof(chashlistnode_t));
    node->key = key;
    node->val = val;
    node->next = ptr->next;
    ptr->next=node;
  }
}

// Search for an address for a given oid
INLINE void * chashSearch(chashtable_t *table, unsigned int key) {
  //REMOVE HASH FUNCTION CALL TO MAKE SURE IT IS INLINED HERE
  chashlistnode_t *node = &table->table[(key & table->mask)>>1];

  do {
    if(node->key == key) {
      return node->val;
    }
    node = node->next;
  } while(node != NULL);

  return NULL;
}

//Store objects and their pointers into hash
void t_chashInsert(unsigned int key, void *val) {
  chashlistnode_t *ptr;

  if(c_numelements > (c_threshold)) {
    //Resize
    unsigned int newsize = c_size << 1;
    t_chashResize(newsize);
  }

  ptr = &c_table[(key&c_mask)>>1];
  c_numelements++;

  if(ptr->key==0) {
    ptr->key=key;
    ptr->val=val;
  } else { // Insert in the beginning of linked list
    chashlistnode_t * node;
    if (c_structs->num<NUMCLIST) {
      node=&c_structs->array[c_structs->num];
      c_structs->num++;
    } else {
      //get new list                                                                
      cliststruct_t *tcl=calloc(1,sizeof(cliststruct_t));
      tcl->next=c_structs;
      c_structs=tcl;
      node=&tcl->array[0];
      tcl->num=1;
    }
    node->key = key;
    node->val = val;
    node->next = ptr->next;
    ptr->next=node;
  }
}

// Search for an address for a given oid
INLINE void * t_chashSearch(unsigned int key) {
  //REMOVE HASH FUNCTION CALL TO MAKE SURE IT IS INLINED HERE
  chashlistnode_t *node = &c_table[(key & c_mask)>>1];

  do {
    if(node->key == key) {
      return node->val;
    }
    node = node->next;
  } while(node != NULL);

  return NULL;
}

unsigned int chashRemove(chashtable_t *table, unsigned int key) {
  return chashRemove2(table, key)==NULL;

}

void * chashRemove2(chashtable_t *table, unsigned int key) {
  int index;
  chashlistnode_t *curr, *prev;
  chashlistnode_t *ptr, *node;
  void *value;

  ptr = table->table;
  index = chashFunction(table,key);
  curr = &ptr[index];

  for (; curr != NULL; curr = curr->next) {
    if (curr->key == key) {         // Find a match in the hash table
      table->numelements--;  // Decrement the number of elements in the global hashtable
      if ((curr == &ptr[index]) && (curr->next == NULL)) {  // Delete the first item inside the hashtable with no linked list of chashlistnode_t
	curr->key = 0;
	value=curr->val;
	curr->val = NULL;
      } else if ((curr == &ptr[index]) && (curr->next != NULL)) { //Delete the first item with a linked list of chashlistnode_t  connected
	curr->key = curr->next->key;
	value=curr->val;
	curr->val = curr->next->val;
	node = curr->next;
	curr->next = curr->next->next;
	free(node);
      } else {                                          // Regular delete from linked listed
	prev->next = curr->next;
	value=curr->val;
	free(curr);
      }
      return value;
    }
    prev = curr;
  }
  return NULL;
}

unsigned int chashResize(chashtable_t *table, unsigned int newsize) {
  chashlistnode_t *node, *ptr, *curr;    // curr and next keep track of the current and the next chashlistnodes in a linked list
  unsigned int oldsize;
  int isfirst;    // Keeps track of the first element in the chashlistnode_t for each bin in hashtable
  unsigned int i,index;
  unsigned int mask;
  
  ptr = table->table;
  oldsize = table->size;

  if((node = calloc(newsize, sizeof(chashlistnode_t))) == NULL) {
    printf("Calloc error %s %d\n", __FILE__, __LINE__);
    return 1;
  }

  table->table = node;          //Update the global hashtable upon resize()
  table->size = newsize;
  table->threshold = newsize * table->loadfactor;
  mask=table->mask = (newsize << 1)-1;

  for(i = 0; i < oldsize; i++) {                        //Outer loop for each bin in hash table
    curr = &ptr[i];
    isfirst = 1;
    do {                      //Inner loop to go through linked lists
      unsigned int key;
      chashlistnode_t *tmp,*next;
      
      if ((key=curr->key) == 0) {             //Exit inner loop if there the first element is 0
	break;                  //key = val =0 for element if not present within the hash table
      }
      next = curr->next;
      index = (key & mask) >>1;
      tmp=&node[index];
      // Insert into the new table
      if(tmp->key == 0) {
	tmp->key = curr->key;
	tmp->val = curr->val;
	if (!isfirst) {
	  free(curr);
	}
      }/*
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

  free(ptr);            //Free the memory of the old hash table
  return 0;
}

unsigned int t_chashResize(unsigned int newsize) {
  chashlistnode_t *node, *ptr, *curr;    // curr and next keep track of the current and the next chashlistnodes in a linked list
  unsigned int oldsize;
  int isfirst;    // Keeps track of the first element in the chashlistnode_t for each bin in hashtable
  unsigned int i,index;
  unsigned int mask;
  
  ptr = c_table;
  oldsize = c_size;

  if((node = calloc(newsize, sizeof(chashlistnode_t))) == NULL) {
    printf("Calloc error %s %d\n", __FILE__, __LINE__);
    return 1;
  }

  c_table = node;          //Update the global hashtable upon resize()
  c_size = newsize;
  c_threshold = newsize * c_loadfactor;
  mask=c_mask = (newsize << 1)-1;

  for(i = 0; i < oldsize; i++) {                        //Outer loop for each bin in hash table
    curr = &ptr[i];
    isfirst = 1;
    do {                      //Inner loop to go through linked lists
      unsigned int key;
      chashlistnode_t *tmp,*next;
      
      if ((key=curr->key) == 0) {             //Exit inner loop if there the first element is 0
	break;                  //key = val =0 for element if not present within the hash table
      }
      index = (key & mask) >>1;
      tmp=&node[index];
      next = curr->next;
      // Insert into the new table
      if(tmp->key == 0) {
	tmp->key = key;
	tmp->val = curr->val;
      }/*
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

  free(ptr);            //Free the memory of the old hash table
  return 0;
}

//Delete the entire hash table
void chashDelete(chashtable_t *ctable) {
  int i;
  chashlistnode_t *ptr = ctable->table;

  for(i=0 ; i<ctable->size ; i++) {
    chashlistnode_t * curr = ptr[i].next;
    while(curr!=NULL) {
      chashlistnode_t * next = curr->next;
      free(curr);
      curr=next;
    }
  }
  free(ptr);
  free(ctable);
}

//Delete the entire hash table
void t_chashDelete() {
  cliststruct_t *ptr=c_structs;
  while(ptr!=NULL) {
    cliststruct_t *next=ptr->next;
    free(ptr);
    ptr=next;
  }
  free(c_table);
  c_table=NULL;
  c_structs=NULL;
}
