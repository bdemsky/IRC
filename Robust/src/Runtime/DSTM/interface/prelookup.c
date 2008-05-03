/* LOCK THE ENTIRE HASH TABLE */
#include "prelookup.h"

prehashtable_t pflookup; //Global prefetch cache table

unsigned int prehashCreate(unsigned int size, float loadfactor) {
  prehashlistnode_t *nodes; 
  int i; 
  
  // Allocate space for the hash table 
  if((nodes = calloc(size, sizeof(prehashlistnode_t))) == NULL) { 
    printf("Calloc error %s %d\n", __FILE__, __LINE__);
    return 1;
  }
  
  pflookup.table = nodes;
  pflookup.size = size; 
  pflookup.numelements = 0; // Initial number of elements in the hash
  pflookup.loadfactor = loadfactor;
  
  //Intiliaze and set prefetch table mutex attribute
  pthread_mutexattr_init(&pflookup.prefetchmutexattr);
  //NOTE:PTHREAD_MUTEX_RECURSIVE is currently inside a #if_def UNIX98 in the pthread.h file
  //Therefore use PTHREAD_MUTEX_RECURSIVE_NP instead
  pthread_mutexattr_settype(&pflookup.prefetchmutexattr, PTHREAD_MUTEX_RECURSIVE_NP);
  
  //Initialize mutex var
  pthread_mutex_init(&pflookup.lock, &pflookup.prefetchmutexattr);
  //pthread_mutex_init(&pflookup.lock, NULL);
  pthread_cond_init(&pflookup.cond, NULL); 
  return 0;
}

//Assign keys to bins inside hash table
unsigned int prehashFunction(unsigned int key) {
  return ( key % (pflookup.size));
}

//Store oids and their pointers into hash
unsigned int prehashInsert(unsigned int key, void *val) {
  unsigned int newsize;
  int index;
  prehashlistnode_t *ptr, *node;
  
  if(pflookup.numelements > (pflookup.loadfactor * pflookup.size)) {
    //Resize
    newsize = 2 * pflookup.size + 1;
    pthread_mutex_lock(&pflookup.lock);
    prehashResize(newsize);
    pthread_mutex_unlock(&pflookup.lock);
  }
  
  ptr = pflookup.table;
  pflookup.numelements++;
  index = prehashFunction(key);
  
  pthread_mutex_lock(&pflookup.lock);
  if(ptr[index].next == NULL && ptr[index].key == 0) {	// Insert at the first position in the hashtable
    ptr[index].key = key;
    ptr[index].val = val;
  } else {			// Insert in the beginning of linked list
    if ((node = calloc(1, sizeof(prehashlistnode_t))) == NULL) {
      printf("Calloc error %s, %d\n", __FILE__, __LINE__);
      pthread_mutex_unlock(&pflookup.lock);
      return 1;
    }
    node->key = key;
    node->val = val ;
    node->next = ptr[index].next;
    ptr[index].next = node;
  }
  pthread_mutex_unlock(&pflookup.lock);
  return 0;
}

// Search for an address for a given oid
void *prehashSearch(unsigned int key) {
  int index;
  prehashlistnode_t *ptr, *node;
  
  ptr = pflookup.table;
  index = prehashFunction(key);
  node = &ptr[index];
  pthread_mutex_lock(&pflookup.lock);
  while(node != NULL) {
    if(node->key == key) {
      pthread_mutex_unlock(&pflookup.lock);
      return node->val;
    }
    node = node->next;
  }
  pthread_mutex_unlock(&pflookup.lock);
  return NULL;
}

unsigned int prehashRemove(unsigned int key) {
  int index;
  prehashlistnode_t *curr, *prev;
  prehashlistnode_t *ptr, *node;
  
  ptr = pflookup.table;
  index = prehashFunction(key);
  curr = &ptr[index];
  
  pthread_mutex_lock(&pflookup.lock);
  for (; curr != NULL; curr = curr->next) {
    if (curr->key == key) {         // Find a match in the hash table
      pflookup.numelements--;  // Decrement the number of elements in the global hashtable
      if ((curr == &ptr[index]) && (curr->next == NULL))  { // Delete the first item inside the hashtable with no linked list of prehashlistnode_t 
	curr->key = 0;
	curr->val = NULL;
      } else if ((curr == &ptr[index]) && (curr->next != NULL)) { //Delete the first item with a linked list of prehashlistnode_t  connected 
	curr->key = curr->next->key;
	curr->val = curr->next->val;
	node = curr->next;
	curr->next = curr->next->next;
	free(node);
      } else {						// Regular delete from linked listed 	
	prev->next = curr->next;
	free(curr);
      }
      pthread_mutex_unlock(&pflookup.lock);
      return 0;
    }       
    prev = curr; 
  }
  pthread_mutex_unlock(&pflookup.lock);
  return 1;
}

unsigned int prehashResize(unsigned int newsize) {
  prehashlistnode_t *node, *ptr, *curr, *next;	// curr and next keep track of the current and the next chashlistnodes in a linked list
  unsigned int oldsize;
  int isfirst;    // Keeps track of the first element in the prehashlistnode_t for each bin in hashtable
  int i,index;   	
  prehashlistnode_t *newnode; 		
  
  ptr = pflookup.table;
  oldsize = pflookup.size;
  
  if((node = calloc(newsize, sizeof(prehashlistnode_t))) == NULL) {
    printf("Calloc error %s %d\n", __FILE__, __LINE__);
    return 1;
  }
  
  pflookup.table = node; 		//Update the global hashtable upon resize()
  pflookup.size = newsize;
  pflookup.numelements = 0;
  
  for(i = 0; i < oldsize; i++) {			//Outer loop for each bin in hash table
    curr = &ptr[i];
    isfirst = 1;			
    while (curr != NULL) {			//Inner loop to go through linked lists
      if (curr->key == 0) {		//Exit inner loop if there the first element for a given bin/index is NULL
	break;			//key = val =0 for element if not present within the hash table
      }
      next = curr->next;
      index = prehashFunction(curr->key);
      // Insert into the new table
      if(pflookup.table[index].next == NULL && pflookup.table[index].key == 0) { 
	pflookup.table[index].key = curr->key;
	pflookup.table[index].val = curr->val;
	pflookup.numelements++;
      }else { 
	if((newnode = calloc(1, sizeof(prehashlistnode_t))) == NULL) { 
	  printf("Calloc error %s, %d\n", __FILE__, __LINE__);
	  return 1;
	}       
	newnode->key = curr->key;
	newnode->val = curr->val;
	newnode->next = pflookup.table[index].next;
	pflookup.table[index].next = newnode;    
	pflookup.numelements++;
      }       
      
      //free the linked list of prehashlistnode_t if not the first element in the hash table
      if (isfirst != 1) {
	free(curr);
      } 
      
      isfirst = 0;
      curr = next;
    }
  }
  
  free(ptr);		//Free the memory of the old hash table	
  return 0;
}

/* Deletes the prefetch Cache */
void prehashDelete() {
  int i, isFirst;
  prehashlistnode_t *ptr, *curr, *next;
  ptr = pflookup.table; 
  
  for(i=0 ; i<pflookup.size ; i++) {
    curr = &ptr[i];
    isFirst = 1;
    while(curr != NULL) {
      next = curr->next;
      if(isFirst != 1) {
	free(curr);
      }
      isFirst = 0;
      curr = next;
    }
  }
  
  free(ptr);
}

//Note: This is based on the implementation of the inserting a key in the first position of the hashtable 
void prehashClear() {
  int i, isFirstBin;
  prehashlistnode_t *ptr, *prev, *curr;
  
  pthread_mutex_lock(&pflookup.lock);
  ptr = pflookup.table; 
  for(i = 0; i < pflookup.size; i++) {
    prev = &ptr[i];
    isFirstBin = 1;
    while(prev->next != NULL) {
      isFirstBin = 0;
      curr = prev->next;
      prev->next = curr->next;
      free(curr);
    }
    if(isFirstBin == 1) {
      prev->key = 0;
      prev->next = NULL;
    }
  }
  pthread_mutex_unlock(&pflookup.lock);
}

