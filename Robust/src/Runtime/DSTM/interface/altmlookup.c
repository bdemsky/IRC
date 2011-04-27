#include "altmlookup.h"
#include "dsmlock.h"
#include <sched.h>

mhashtable_t mlookup;   //Global hash table

// Creates a machine lookup table with size =" size"
unsigned int mhashCreate(unsigned int size, double loadfactor) {
  mhashlistnode_t *nodes;
  // Allocate space for the hash table
  if((nodes = calloc(size, sizeof(mhashlistnode_t))) == NULL) {
    printf("Calloc error %s %d\n", __FILE__, __LINE__);
    return 1;
  }

  mlookup.table = nodes;
  mlookup.size = size;
  mlookup.threshold=size*loadfactor;
  mlookup.mask = size -1;
  mlookup.numelements = 0;       // Initial number of elements in the hash
  mlookup.loadfactor = loadfactor;
  int i;
  for(i=0; i<NUMLOCKS; i++)
    mlookup.larray[i].lock=RW_LOCK_BIAS;
  //Initialize the pthread_mutex variable
  return 0;
}

// Assign to keys to bins inside hash table
unsigned int mhashFunction(unsigned int key) {
  return ( key & mlookup.mask) >>1;
}

// Insert value and key mapping into the hash table
void mhashInsert(unsigned int key, void *val) {
  mhashlistnode_t *node;

  if (mlookup.numelements > mlookup.threshold) {
    //Resize Table
    unsigned int newsize = mlookup.size << 1;
    mhashResize(newsize);
  }

  unsigned int keyindex=key>>1;
  volatile unsigned int * lockptr=&mlookup.larray[keyindex&LOCKMASK].lock;
  while(!write_trylock(lockptr)) {
    sched_yield();
  }

  mhashlistnode_t * ptr = &mlookup.table[keyindex&mlookup.mask];
  atomic_inc(&mlookup.numelements);

  if(ptr->key ==0) {
    ptr->key=key;
    ptr->val=val;
  } else {                              // Insert in the beginning of linked list
    node = calloc(1, sizeof(mhashlistnode_t));
    node->key = key;
    node->val = val;
    node->next = ptr->next;
    ptr->next=node;
  }
  write_unlock(lockptr);
}

// Return val for a given key in the hash table
void *mhashSearch(unsigned int key) {
  int index;

  unsigned int keyindex=key>>1;
  volatile unsigned int * lockptr=&mlookup.larray[keyindex&LOCKMASK].lock;

  while(!read_trylock(lockptr)) {
    sched_yield();
  }

  mhashlistnode_t *node = &mlookup.table[keyindex&mlookup.mask];

  do {
    if(node->key == key) {
      void * tmp=node->val;
      read_unlock(lockptr);
      return tmp;
    }
    node = node->next;
  } while (node!=NULL);
  read_unlock(lockptr);
  return NULL;
}

// Remove an entry from the hash table
unsigned int mhashRemove(unsigned int key) {
  int index;
  mhashlistnode_t *prev;
  mhashlistnode_t *ptr, *node;

  unsigned int keyindex=key>>1;
  volatile unsigned int * lockptr=&mlookup.larray[keyindex&LOCKMASK].lock;

  while(!write_trylock(lockptr)) {
    sched_yield();
  }

  mhashlistnode_t *curr = &mlookup.table[keyindex&mlookup.mask];

  for (; curr != NULL; curr = curr->next) {
    if (curr->key == key) {
      atomic_dec(&(mlookup.numelements));
      if ((curr == &ptr[index]) && (curr->next == NULL)) {
	curr->key = 0;
	curr->val = NULL;
      } else if ((curr == &ptr[index]) && (curr->next != NULL)) {
	curr->key = curr->next->key;
	curr->val = curr->next->val;
	node = curr->next;
	curr->next = curr->next->next;
	free(node);
      } else {
	prev->next = curr->next;
	free(curr);
      }
      write_unlock(lockptr);
      return 0;
    }
    prev = curr;
  }
  write_unlock(lockptr);
  return 1;
}

// Resize table
void mhashResize(unsigned int newsize) {
  mhashlistnode_t *node, *curr;
  int isfirst;
  unsigned int i,index;
  unsigned int mask;

  for(i=0; i<NUMLOCKS; i++) {
    volatile unsigned int * lockptr=&mlookup.larray[i].lock;

    while(!write_trylock(lockptr)) {
      sched_yield();
    }
  }

  if (mlookup.numelements < mlookup.threshold) {
    //release lock and return
    for(i=0; i<NUMLOCKS; i++) {
      volatile unsigned int * lockptr=&mlookup.larray[i].lock;
      write_unlock(lockptr);
    }
    return;
  }

  mhashlistnode_t * ptr = mlookup.table;
  unsigned int oldsize = mlookup.size;

  if((node = calloc(newsize, sizeof(mhashlistnode_t))) == NULL) {
    printf("Calloc error %s %d\n", __FILE__, __LINE__);
    return;
  }

  mlookup.table = node;
  mlookup.size = newsize;
  mlookup.threshold=newsize*mlookup.loadfactor;
  mask=mlookup.mask = newsize -1;

  for(i = 0; i < oldsize; i++) {
    curr = &ptr[i];
    isfirst = 1;
    do {
      unsigned int key;
      mhashlistnode_t *tmp,*next;

      if ((key=curr->key) == 0) {
	break;
      }
      next = curr->next;
      index = (key >> 1) & mask;
      tmp=&mlookup.table[index];

      if(tmp->key ==0) {
	tmp->key=curr->key;
	tmp->val=curr->val;
	if (!isfirst)
	  free(curr);
      } /*

	   NOTE:  Add this case if you change this...
	   This case currently never happens because of the way things rehash....
	   else if (isfirst) {
	   mhashlistnode_t *newnode = calloc(1, sizeof(mhashlistnode_t));
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

  free(ptr);
  for(i=0; i<NUMLOCKS; i++) {
    volatile unsigned int * lockptr=&mlookup.larray[i].lock;
    write_unlock(lockptr);
  }
  return;
}
/*
   unsigned int *mhashGetKeys(unsigned int *numKeys) {
   unsigned int *keys;
   int i, keyindex;
   mhashlistnode_t *curr;

   pthread_mutex_lock(&mlookup.locktable);

 *numKeys = mlookup.numelements;
   keys = calloc(*numKeys, sizeof(unsigned int));

   keyindex = 0;
   for (i = 0; i < mlookup.size; i++) {
    if (mlookup.table[i].key != 0) {
      curr = &mlookup.table[i];
      while (curr != NULL) {
        keys[keyindex++] = curr->key;
        curr = curr->next;
      }
    }
   }

   if (keyindex != *numKeys)
    printf("mhashGetKeys(): WARNING: incorrect mlookup.numelements value!\n");

   pthread_mutex_unlock(&mlookup.locktable);
   return keys;
   }*/

