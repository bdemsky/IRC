#include "altprelookup.h"
#include "dsmlock.h"
#include "gCollect.h"
extern objstr_t *prefetchcache;
extern pthread_mutex_t prefetchcache_mutex; //Mutex to lock Prefetch Cache
extern prefetchNodeInfo_t pNodeInfo;

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
  pflookup.mask = size -1;
  pflookup.numelements = 0; // Initial number of elements in the hash
  pflookup.loadfactor = loadfactor;
  pflookup.threshold=loadfactor*size;
  
  //Initilize 
  for(i=0;i<NUMLOCKS;i++){
    pflookup.larray[i].lock=RW_LOCK_BIAS;
  }

  return 0;
}

//Assign keys to bins inside hash table
unsigned int prehashFunction(unsigned int key) {
  return ( key & pflookup.mask) >> 1;
}

//Store oids and their pointers into hash
void prehashInsert(unsigned int key, void *val) {
  
  int isFound=0;
  prehashlistnode_t *ptr, *tmp, *node;

  if(pflookup.numelements > (pflookup.threshold)) {
    //Resize
    unsigned int newsize = pflookup.size << 1;
    prehashResize(newsize);
  }

  unsigned int keyindex=key>>1;
  volatile unsigned int * lockptr=&pflookup.larray[keyindex&LOCKMASK].lock;
  while(!write_trylock(lockptr)) {
    sched_yield();
  }

  ptr = &pflookup.table[keyindex&pflookup.mask];

  if((ptr->key==0) && (ptr->next== NULL)) { //Insert at the first bin of the table
    ptr->key = key;
    ptr->val = val;
    atomic_inc(&pflookup.numelements);
  } else {
    tmp = ptr;
    while(tmp != NULL) { 
      if(tmp->key == key) {
        isFound=1;
        tmp->val = val;//Replace value for an exsisting key
        write_unlock(lockptr);

        return;
      }
      tmp=tmp->next;
    }
    if(!isFound) { //Insert new key and value into the chain of linked list for the given bin
      node = calloc(1, sizeof(prehashlistnode_t));
      node->key = key;
      node->val = val ;
      node->next = ptr->next;
      ptr->next=node;
      atomic_inc(&pflookup.numelements);
    }
  }
  write_unlock(lockptr);
  return;
}

// Search for an address for a given oid
INLINE void *prehashSearch(unsigned int key) {
  int index;
 
  unsigned int keyindex=key>>1;
  volatile unsigned int * lockptr=&pflookup.larray[keyindex&LOCKMASK].lock;
  while(!read_trylock(lockptr)) {
    sched_yield();
  }
  prehashlistnode_t *node = &pflookup.table[keyindex&pflookup.mask];
 
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

unsigned int prehashRemove(unsigned int key) {
  int index;
  prehashlistnode_t *prev;
  prehashlistnode_t *ptr, *node;

  unsigned int keyindex=key>>1;
  volatile unsigned int * lockptr=&pflookup.larray[keyindex&LOCKMASK].lock;

  while(!write_trylock(lockptr)) {
    sched_yield();
  }
  
  prehashlistnode_t *curr = &pflookup.table[keyindex&pflookup.mask];
  
  for (; curr != NULL; curr = curr->next) {
    if (curr->key == key) {        
      // Find a match in the hash table
      //decrement the number of elements in the global hashtable  
      atomic_dec(&(pflookup.numelements));
      
     if ((curr == &ptr[index]) && (curr->next == NULL)) {  
       // Delete the first item inside the hashtable with no linked list of prehashlistnode_t
	curr->key = 0;
	curr->val = NULL;
      } else if ((curr == &ptr[index]) && (curr->next != NULL)) { 
       //Delete the first item with a linked list of prehashlistnode_t  connected
	curr->key = curr->next->key;
	curr->val = curr->next->val;
	node = curr->next;
	curr->next = curr->next->next;
	free(node);
      } else {                                          
       // Regular delete from linked listed
	prev->next = curr->next;
	free(curr);
      }
      //pthread_mutex_unlock(&pflookup.lock);
     write_unlock(lockptr);
      return 0;
    }
    prev = curr;
  }
  write_unlock(lockptr);

  return 1;
}

unsigned int prehashResize(unsigned int newsize) {
  prehashlistnode_t *node, *ptr;  // curr and next keep track of the current and the next chashlistnodes in a linked list
  unsigned int oldsize;
  int i,index;
  unsigned int mask;

  for(i=0;i<NUMLOCKS;i++) {
    volatile unsigned int * lockptr=&pflookup.larray[i].lock;
    
    while(!write_trylock(lockptr)) {
      sched_yield();
    }
  }
  
  if (pflookup.numelements < pflookup.threshold) {
    //release lock and return
    for(i=0;i<NUMLOCKS;i++) {
      volatile unsigned int * lockptr=&pflookup.larray[i].lock;
      write_unlock(lockptr);
    }
    return;
  }

  ptr = pflookup.table;
  oldsize = pflookup.size;

  if((node = calloc(newsize, sizeof(prehashlistnode_t))) == NULL) {
    printf("Calloc error %s %d\n", __FILE__, __LINE__);
    return 1;
  }

  pflookup.table = node;                //Update the global hashtable upon resize()
  pflookup.size = newsize;
  pflookup.threshold=newsize*pflookup.loadfactor;
  mask=pflookup.mask = (newsize << 1) -1;

  for(i = 0; i < oldsize; i++) {                        //Outer loop for each bin in hash table
    prehashlistnode_t * curr = &ptr[i];
    prehashlistnode_t *tmp, *next;
    int isfirst = 1;
    do {
      unsigned int key;
      if ((key=curr->key) == 0) {             //Exit inner loop if there the first element for a given bin/index is NULL
	break;                  //key = val =0 for element if not present within the hash table
      }
      next = curr->next;
      //index = (key & mask)>>1;
      index = (key >> 1) & mask;
      tmp=&pflookup.table[index];
      // Insert into the new table
      if(tmp->key==0) {
	tmp->key=curr->key;
	tmp->val=curr->val;
	if (!isfirst)
	  free(curr);
      } /*
         NOTE:  Add this case if you change this...                                                        
         This case currently never happens because of the way things rehash....                            
else if (isfirst) {
	prehashlistnode_t * newnode = calloc(1, sizeof(prehashlistnode_t));
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
  for(i=0;i<NUMLOCKS;i++) {
    volatile unsigned int * lockptr=&pflookup.larray[i].lock;
    write_unlock(lockptr);
  }
  return ;
}

//Note: This is based on the implementation of the inserting a key in the first position of the hashtable
void prehashClear() {
  /*
#ifdef CACHE
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
  {
    int stale;
    pthread_mutex_unlock(&pflookup.lock);
    pthread_mutex_lock(&prefetchcache_mutex);
    if (pNodeInfo.newstale==NULL) {
      //transfer the list wholesale;
      pNodeInfo.oldstale=pNodeInfo.oldptr;
      pNodeInfo.newstale=pNodeInfo.newptr;
    } else {
      //merge the two lists
      pNodeInfo.newstale->prev=pNodeInfo.oldptr;
      pNodeInfo.newstale=pNodeInfo.newptr;
    }
    stale=STALL_THRESHOLD-pNodeInfo.stale_count;
    
    if (stale>0&&stale>pNodeInfo.stall)
      pNodeInfo.stall=stale;

    pNodeInfo.stale_count+=pNodeInfo.os_count;
    pNodeInfo.oldptr=getObjStr(DEFAULT_OBJ_STORE_SIZE);
    pNodeInfo.newptr=pNodeInfo.oldptr;
    pNodeInfo.os_count=1;
    pthread_mutex_unlock(&prefetchcache_mutex);
  }
#endif
  */
}

