/************************************************************************************************
   IMP NOTE:
   All llookup hash function prototypes returns 0 on sucess and 1 otherwise
   llookup hash is an array of lhashlistnode_t
   oid = mid = 0 in a given lhashlistnode_t for each bin in the hash table ONLY if the entry is empty =>
   the OID's can be any unsigned int except 0

   Uses pthreads. compile using -lpthread option
 ***************************************************************************************************/
#include "llookup.h"

#ifdef SIMPLE_LLOOKUP

extern unsigned int *hostIpAddrs;
extern unsigned int oidsPerBlock;

unsigned int lhashCreate(unsigned int size, float loadfactor) {
  return 0;
}

unsigned int lhashInsert(unsigned int oid, unsigned int mid) {
  return 0;
}

unsigned int lhashSearch(unsigned int oid) {
  if (oidsPerBlock == 0)
    return hostIpAddrs[0];
  else
    return hostIpAddrs[oid / oidsPerBlock];
}

unsigned int lhashRemove(unsigned int oid) {
  return 0;
}

#else

lhashtable_t llookup;           //Global Hash table

// Creates a hash table with size and an array of lhashlistnode_t
unsigned int lhashCreate(unsigned int size, float loadfactor) {
  lhashlistnode_t *nodes;
  int i;

  // Allocate space for the hash table
  if((nodes = calloc(size, sizeof(lhashlistnode_t))) == NULL) {
    printf("Calloc error %s %d\n", __FILE__, __LINE__);
    return 1;
  }

  llookup.table = nodes;
  llookup.size = size;
  llookup.numelements = 0;       // Initial number of elements in the hash
  llookup.loadfactor = loadfactor;
  //Initialize the pthread_mutex variable
  pthread_mutex_init(&llookup.locktable, NULL);
  return 0;
}

// Assign to oids to bins inside hash table
unsigned int lhashFunction(unsigned int oid) {
  return( oid % (llookup.size));
}

// Insert oid and mid mapping into the hash table
unsigned int lhashInsert(unsigned int oid, unsigned int mid) {
  unsigned int newsize;
  int index;
  lhashlistnode_t *ptr, *node;

  if (llookup.numelements > (llookup.loadfactor * llookup.size)) {
    //Resize Table
    newsize = 2 * llookup.size + 1;
    pthread_mutex_lock(&llookup.locktable);
    lhashResize(newsize);
    pthread_mutex_unlock(&llookup.locktable);
  }

  pthread_mutex_lock(&llookup.locktable);
  ptr = llookup.table;
  llookup.numelements++;

  index = lhashFunction(oid);
#ifdef DEBUG
  printf("DEBUG(insert) oid = %d, mid =%d, index =%d\n",oid,mid, index);
#endif
  if(ptr[index].next == NULL && ptr[index].oid == 0) {          // Insert at the first position in the hashtable
    ptr[index].oid = oid;
    ptr[index].mid = mid;
  } else {                              // Insert in the linked list
    if ((node = calloc(1, sizeof(lhashlistnode_t))) == NULL) {
      printf("Calloc error %s, %d\n", __FILE__, __LINE__);
      pthread_mutex_unlock(&llookup.locktable);
      return 1;
    }
    node->oid = oid;
    node->mid = mid;
    node->next = ptr[index].next;
    ptr[index].next = node;
  }

  pthread_mutex_unlock(&llookup.locktable);
  return 0;
}

// Return mid for a given oid in the hash table
unsigned int lhashSearch(unsigned int oid) {
  int index;
  lhashlistnode_t *ptr, *node;

  pthread_mutex_lock(&llookup.locktable);
  ptr = llookup.table;          // Address of the beginning of hash table
  index = lhashFunction(oid);
  node = &ptr[index];
  while(node != NULL) {
    if(node->oid == oid) {
      pthread_mutex_unlock(&llookup.locktable);
      return node->mid;
    }
    node = node->next;
  }
  pthread_mutex_unlock(&llookup.locktable);
  return 0;
}

// Remove an entry from the hash table
unsigned int lhashRemove(unsigned int oid) {
  int index;
  lhashlistnode_t *curr, *prev;
  lhashlistnode_t *ptr, *node;

  pthread_mutex_lock(&llookup.locktable);
  ptr = llookup.table;
  index = lhashFunction(oid);
  curr = &ptr[index];

  for (; curr != NULL; curr = curr->next) {
    if (curr->oid == oid) {                     // Find a match in the hash table
      llookup.numelements--;                    // Decrement the number of elements in the global hashtable
      if ((curr == &ptr[index]) && (curr->next == NULL)) {                    // Delete the first item inside the hashtable with no linked list of lhashlistnode_t
	curr->oid = 0;
	curr->mid = 0;
      } else if ((curr == &ptr[index]) && (curr->next != NULL)) {                   //Delete the first item with a linked list of lhashlistnode_t  connected
	curr->oid = curr->next->oid;
	curr->mid = curr->next->mid;
	node = curr->next;
	curr->next = curr->next->next;
	free(node);
      } else {                                                                  // Regular delete from linked listed
	prev->next = curr->next;
	free(curr);
      }
      pthread_mutex_unlock(&llookup.locktable);
      return 0;
    }
    prev = curr;
  }
  pthread_mutex_unlock(&llookup.locktable);
  return 1;
}

// Resize table
unsigned int lhashResize(unsigned int newsize) {
  lhashlistnode_t *node, *ptr, *curr, *next;            // curr and next keep track of the current and the next lhashlistnodes in a linked list
  unsigned int oldsize;
  int isfirst;          // Keeps track of the first element in the lhashlistnode_t for each bin in hashtable
  int i,index;
  lhashlistnode_t *newnode;

  ptr = llookup.table;
  oldsize = llookup.size;

  if((node = calloc(newsize, sizeof(lhashlistnode_t))) == NULL) {
    printf("Calloc error %s %d\n", __FILE__, __LINE__);
    return 1;
  }

  llookup.table = node;                 //Update the global hashtable upon resize()
  llookup.size = newsize;
  llookup.numelements = 0;

  for(i = 0; i < oldsize; i++) {                        //Outer loop for each bin in hash table
    curr = &ptr[i];
    isfirst = 1;
    while (curr != NULL) {                              //Inner loop to go through linked lists
      if (curr->oid == 0) {                             //Exit inner loop if there the first element for a given bin/index is NULL
	break;                                          //oid = mid =0 for element if not present within the hash table
      }
      next = curr->next;
      index = lhashFunction(curr->oid);
      // Insert into the new table
      if(llookup.table[index].next == NULL && llookup.table[index].oid == 0) {
	llookup.table[index].oid = curr->oid;
	llookup.table[index].mid = curr->mid;
	llookup.numelements++;
      } else {
	if((newnode = calloc(1, sizeof(lhashlistnode_t))) == NULL) {
	  printf("Calloc error %s, %d\n", __FILE__, __LINE__);
	  return 1;
	}
	newnode->oid = curr->oid;
	newnode->mid = curr->mid;
	newnode->next = llookup.table[index].next;
	llookup.table[index].next = newnode;
	llookup.numelements++;
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

#endif

