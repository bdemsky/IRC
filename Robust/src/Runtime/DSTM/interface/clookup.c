#include "clookup.h"
#define INLINE    inline __attribute__((always_inline))

chashtable_t *chashCreate(unsigned int size, float loadfactor) {
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
  ctable->size = size;
  ctable->mask = (size << 1)-1;
  ctable->numelements = 0; // Initial number of elements in the hash
  ctable->loadfactor = loadfactor;

  return ctable;
}

//Finds the right bin in the hash table
static INLINE unsigned int chashFunction(chashtable_t *table, unsigned int key) {
  return ( key & (table->mask))>>1; //throw away low order bit
}

//Store objects and their pointers into hash
unsigned int chashInsert(chashtable_t *table, unsigned int key, void *val) {
  unsigned int newsize;
  int index;
  chashlistnode_t *ptr, *node;

  if(table->numelements > (table->loadfactor * table->size)) {
    //Resize
    newsize = table->size << 1;
    chashResize(table,newsize);
  }

  ptr = table->table;
  table->numelements++;
  index = chashFunction(table, key);
#ifdef DEBUG
  printf("chashInsert(): DEBUG -> index = %d, key = %d, val = %x\n", index, key, val);
#endif
  if(ptr[index].next == NULL && ptr[index].key == 0) {  // Insert at the first position in the hashtable
    ptr[index].key = key;
    ptr[index].val = val;
  } else { // Insert in the beginning of linked list
    if ((node = calloc(1, sizeof(chashlistnode_t))) == NULL) {
      printf("Calloc error %s, %d\n", __FILE__, __LINE__);
      return 1;
    }
    node->key = key;
    node->val = val;
    node->next = ptr[index].next;
    ptr[index].next = node;
  }
  return 0;
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

unsigned int chashRemove(chashtable_t *table, unsigned int key) {
  int index;
  chashlistnode_t *curr, *prev;
  chashlistnode_t *ptr, *node;

  ptr = table->table;
  index = chashFunction(table,key);
  curr = &ptr[index];

  for (; curr != NULL; curr = curr->next) {
    if (curr->key == key) {         // Find a match in the hash table
      table->numelements--;  // Decrement the number of elements in the global hashtable
      if ((curr == &ptr[index]) && (curr->next == NULL)) {  // Delete the first item inside the hashtable with no linked list of chashlistnode_t
	curr->key = 0;
	curr->val = NULL;
      } else if ((curr == &ptr[index]) && (curr->next != NULL)) { //Delete the first item with a linked list of chashlistnode_t  connected
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

unsigned int chashResize(chashtable_t *table, unsigned int newsize) {
  chashlistnode_t *node, *ptr, *curr, *next;    // curr and next keep track of the current and the next chashlistnodes in a linked list
  unsigned int oldsize;
  int isfirst;    // Keeps track of the first element in the chashlistnode_t for each bin in hashtable
  int i,index;
  chashlistnode_t *newnode;

  ptr = table->table;
  oldsize = table->size;

  if((node = calloc(newsize, sizeof(chashlistnode_t))) == NULL) {
    printf("Calloc error %s %d\n", __FILE__, __LINE__);
    return 1;
  }

  table->table = node;          //Update the global hashtable upon resize()
  table->size = newsize;
  table->mask = (newsize << 1)-1;
  table->numelements = 0;

  for(i = 0; i < oldsize; i++) {                        //Outer loop for each bin in hash table
    curr = &ptr[i];
    isfirst = 1;
    while (curr != NULL) {                      //Inner loop to go through linked lists
      if (curr->key == 0) {             //Exit inner loop if there the first element for a given bin/index is NULL
	break;                  //key = val =0 for element if not present within the hash table
      }
      next = curr->next;

      index = chashFunction(table, curr->key);
#ifdef DEBUG
      printf("DEBUG(resize) -> index = %d, key = %d, val = %x\n", index, curr->key, curr->val);
#endif
      // Insert into the new table
      if(table->table[index].next == NULL && table->table[index].key == 0) {
	table->table[index].key = curr->key;
	table->table[index].val = curr->val;
	table->numelements++;
      } else {
	if((newnode = calloc(1, sizeof(chashlistnode_t))) == NULL) {
	  printf("Calloc error %s, %d\n", __FILE__, __LINE__);
	  return 1;
	}
	newnode->key = curr->key;
	newnode->val = curr->val;
	newnode->next = table->table[index].next;
	table->table[index].next = newnode;
	table->numelements++;
      }

      //free the linked list of chashlistnode_t if not the first element in the hash table
      if (isfirst != 1) {
	free(curr);
      }

      isfirst = 0;
      curr = next;
    }
  }

  free(ptr);            //Free the memory of the old hash table
  return 0;
}

//Delete the entire hash table
void chashDelete(chashtable_t *ctable) {
  int i, isFirst;
  chashlistnode_t *ptr, *curr, *next;
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
