#include "clookup2.h"
#define INLINE    inline __attribute__((always_inline))

chashtable_t *chashCreate(unsigned int size, float loadfactor) {
  chashtable_t *ctable;
  struct chashentry *nodes;
  int i;

  if((ctable = calloc(1, sizeof(chashtable_t))) == NULL) {
    printf("Calloc error %s %d\n", __FILE__, __LINE__);
    return NULL;
  }

  // Allocate space for the hash table
  if((nodes = calloc(size, sizeof(struct chashentry))) == NULL) {
    printf("Calloc error %s %d\n", __FILE__, __LINE__);
    free(ctable);
    return NULL;
  }

  ctable->table = nodes;
  ctable->size = size;
  ctable->mask = (size << 1)-1;
  ctable->numelements = 0; // Initial number of elements in the hash
  ctable->loadfactor = loadfactor;
  ctable->capacity=ctable->loadfactor*ctable->size;
  return ctable;
}

//Finds the right bin in the hash table
static INLINE unsigned int chashFunction(chashtable_t *table, unsigned int key) {
  return ( key & (table->mask))>>1; //throw away low order bit
}

//Store objects and their pointers into hash
void chashInsert(chashtable_t *table, unsigned int key, void *val) {
  unsigned int newsize;
  unsigned int index;
  struct chashentry *node;

  if(table->numelements > table->capacity) {
    //Resize
    newsize = table->size << 1;
    chashResize(table,newsize);
  }
  index=(key &table->mask)>>1;
  while(1) {
    node = & table->table[index];    
    if (node->ptr==NULL) {
      node->ptr=val;
      node->key=key;
      return;
    }
    index++;
    if (index==table->size)
      index=0;
  }
}

// Search for an address for a given oid
INLINE void * chashSearch(chashtable_t *table, unsigned int key) {
  //REMOVE HASH FUNCTION CALL TO MAKE SURE IT IS INLINED HERE
  unsigned int tmp=(key &table->mask)>>1;
  struct chashentry *node;
  unsigned int ckey;
  while(1) {
    node = & table->table[tmp];    
    ckey=node->key;
    if (ckey==key)
      return node->ptr;
    if (ckey==0)
      return NULL;
    tmp++;
    if (tmp==table->size)
      tmp=0;
  }
}

void chashResize(chashtable_t *table, unsigned int newsize) {
  unsigned int oldsize=table->size;
  struct chashentry *ptr= table->table;
  struct chashentry *node= calloc(newsize, sizeof(struct chashentry));
  unsigned int mask;
  unsigned int i;
  struct chashentry *newnode;
  unsigned int bin;
  unsigned int key;
  struct chashentry *curr;

  if(node == NULL) {
    printf("Calloc error %s %d\n", __FILE__, __LINE__);
    return;
  }

  table->table = node;          //Update the global hashtable upon resize()
  table->size = newsize;
  mask=table->mask = (newsize << 1)-1;
  table->numelements = 0;

  for(i = 0; i < oldsize; i++) {                        //Outer loop for each bin in hash table
    curr=& ptr[i];
    key=curr->key;
    if (key != 0) {
      bin=(key&mask)>>1;
      while(1) {
	newnode= & table->table[bin];
	if (newnode->key==0) {
	  newnode->key=key;
	  newnode->ptr=curr->ptr;
	  break;
	}
	bin++;
	if (bin==newsize)
	  bin=0;
      }
      
    }
  }
  free(ptr);            //Free the memory of the old hash table
}

//Delete the entire hash table
void chashDelete(chashtable_t *ctable) {
  free(ctable->table);
  free(ctable);
}
