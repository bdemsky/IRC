#include "tlookup.h"

thashtable_t tlookup;           //Global Hash table

// Creates a hash table with size and an array of lhashlistnode_t
unsigned int thashCreate(unsigned int size, float loadfactor) {
  thashlistnode_t *nodes;
  int i;

  // Allocate space for the hash table
  if((nodes = calloc(size, sizeof(thashlistnode_t))) == NULL) {
    printf("Calloc error %s %d\n", __FILE__, __LINE__);
    return 1;
  }

  tlookup.table = nodes;
  tlookup.size = size;
  tlookup.numelements = 0;       // Initial number of elements in the hash
  tlookup.loadfactor = loadfactor;
  //Initialize the pthread_mutex variable
  pthread_mutex_init(&tlookup.locktable, NULL);
  return 0;
}

// Assign to transids to bins inside hash table
unsigned int thashFunction(unsigned int transid) {
  return( transid % (tlookup.size));
}

// Insert transid and decision mapping into the hash table
unsigned int thashInsert(unsigned int transid, char decision) {
  unsigned int newsize;
  int index;
  thashlistnode_t *ptr, *node;

  if (tlookup.numelements > (tlookup.loadfactor * tlookup.size)) {
    //Resize Table
    newsize = 2 * tlookup.size + 1;
    pthread_mutex_lock(&tlookup.locktable);
    thashResize(newsize);
    pthread_mutex_unlock(&tlookup.locktable);
  }

  pthread_mutex_lock(&tlookup.locktable);
  index = thashFunction(transid);
  ptr = tlookup.table;
  tlookup.numelements++;

#ifdef DEBUG
  printf("DEBUG(insert) transid = %d, decision  = %d, index = %d\n",transid, decision, index);
#endif
  if(ptr[index].next == NULL && ptr[index].transid == 0) {          // Insert at the first position in the hashtable
    ptr[index].transid = transid;
    ptr[index].decision = decision;
  } else {                              // Insert in the linked list
    if ((node = calloc(1, sizeof(thashlistnode_t))) == NULL) {
      printf("Calloc error %s, %d\n", __FILE__, __LINE__);
      pthread_mutex_unlock(&tlookup.locktable);
      return 1;
    }
    node->transid = transid;
    node->decision = decision;
    node->next = ptr[index].next;
    ptr[index].next = node;
  }

  pthread_mutex_unlock(&tlookup.locktable);
  return 0;
}

// Return decision for a given transid in the hash table
char thashSearch(unsigned int transid) {
  int index;
  thashlistnode_t *ptr, *node;

  pthread_mutex_lock(&tlookup.locktable);
  ptr = tlookup.table;          // Address of the beginning of hash table
  index = thashFunction(transid);
  node = &ptr[index];
  while(node != NULL) {
    if(node->transid == transid) {
      pthread_mutex_unlock(&tlookup.locktable);
      return node->decision;
    }
    node = node->next;
  }
  pthread_mutex_unlock(&tlookup.locktable);
  return 0;
}

// Remove an entry from the hash table
unsigned int thashRemove(unsigned int transid) {
  int index;
  thashlistnode_t *curr, *prev;
  thashlistnode_t *ptr, *node;

  pthread_mutex_lock(&tlookup.locktable);
  ptr = tlookup.table;
  index = thashFunction(transid);
  curr = &ptr[index];

  for (; curr != NULL; curr = curr->next) {
    if (curr->transid == transid) {                     // Find a match in the hash table
      tlookup.numelements--;                    // Decrement the number of elements in the global hashtable
      if ((curr == &ptr[index]) && (curr->next == NULL)) {                    // Delete the first item inside the hashtable with no linked list of lhashlistnode_t
	curr->transid = 0;
	curr->decision = 0;
      } else if ((curr == &ptr[index]) && (curr->next != NULL)) {                   //Delete the first item with a linked list of lhashlistnode_t  connected
	curr->transid = curr->next->transid;
	curr->decision = curr->next->decision;
	node = curr->next;
	curr->next = curr->next->next;
	free(node);
      } else {                                                                  // Regular delete from linked listed
	prev->next = curr->next;
	free(curr);
      }
      pthread_mutex_unlock(&tlookup.locktable);
      return 0;
    }
    prev = curr;
  }
  pthread_mutex_unlock(&tlookup.locktable);
  return 1;
}

// Resize table
unsigned int thashResize(unsigned int newsize) {
  thashlistnode_t *node, *ptr, *curr, *next;            // curr and next keep track of the current and the next lhashlistnodes in a linked list
  unsigned int oldsize;
  int isfirst;          // Keeps track of the first element in the lhashlistnode_t for each bin in hashtable
  int i,index;
  thashlistnode_t *newnode;

  ptr = tlookup.table;
  oldsize = tlookup.size;

  if((node = calloc(newsize, sizeof(thashlistnode_t))) == NULL) {
    printf("Calloc error %s %d\n", __FILE__, __LINE__);
    return 1;
  }

  tlookup.table = node;                 //Update the global hashtable upon resize()
  tlookup.size = newsize;
  tlookup.numelements = 0;

  for(i = 0; i < oldsize; i++) {                        //Outer loop for each bin in hash table
    curr = &ptr[i];
    isfirst = 1;

    while (curr != NULL) {                              //Inner loop to go through linked lists
      if (curr->transid == 0) {                             //Exit inner loop if there the first element for a given bin/index is NULL
      	break;                                          //transid = decision =0 for element if not present within the hash table
      }
      next = curr->next;
      index = thashFunction(curr->transid);
      // Insert into the new table
      if(tlookup.table[index].next == NULL && tlookup.table[index].transid == 0) {
        tlookup.table[index].transid = curr->transid;
      	tlookup.table[index].decision = curr->decision;
      	tlookup.numelements++;
      } else {
        if((newnode = calloc(1, sizeof(thashlistnode_t))) == NULL) {
    	    printf("Calloc error %s, %d\n", __FILE__, __LINE__);
    	    return 1;
    	  }
       	newnode->transid = curr->transid;
    	  newnode->decision = curr->decision;
      	newnode->next = tlookup.table[index].next;
      	tlookup.table[index].next = newnode;
    	  tlookup.numelements++;
      }

      //free the linked list of lhashlistnode_t if not the first element in the hash table
      if (isfirst != 1) {
    	  free(curr);
      } 

      isfirst = 0;
      curr = next;
    }
  }

  free(ptr);                    //Free the memory of the old hash table
  return 0;
}


