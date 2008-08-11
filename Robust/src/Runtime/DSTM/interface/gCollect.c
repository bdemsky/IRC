#include "gCollect.h"
#include "prelookup.h"

extern objstr_t *prefetchcache; //Global Prefetch cache
extern pthread_mutex_t prefetchcache_mutex; //Mutex to lock Prefetch Cache
extern prehashtable_t pflookup; //Global prefetch cache  lookup table
prefetchNodeInfo_t *pNodeInfo; //Global prefetch holding metadata

void initializePCache() {
  pNodeInfo = calloc(1, sizeof(prefetchNodeInfo_t)); //Not freed yet
  pNodeInfo->oldptr = prefetchcache;
  pNodeInfo->newptr = NULL;
  pNodeInfo->num_old_objstr = 1; //for prefetch cache allocated by objstralloc in trans.c file
  pNodeInfo->maxsize = DEFAULT_OBJ_STORE_SIZE;
}

void *prefetchobjstrAlloc(unsigned int size) {
  void * ptr = NULL;
  if(pNodeInfo->num_old_objstr <= PREFETCH_FLUSH_COUNT_THRESHOLD) {
    //regular allocation 
    if((ptr = normalPrefetchAlloc(prefetchcache, size)) == NULL) {
      printf("Error: %s() prefetch cache alloc error %s, %d\n", __func__, __FILE__, __LINE__);
      return NULL;
    }
    return ptr;
  } else {
    // Iterate through available blocks to see if size can be allocated
    if((ptr = lookUpFreeSpace(pNodeInfo->newptr, pNodeInfo->oldptr, size)) != NULL) {
      return ptr;
    } else { //allocate new block if size not available
      if(size >= pNodeInfo->maxsize) {
        if((ptr = allocateNew(size)) == NULL) {
          printf("Error: %s() Calloc error %s %d\n", __func__, __FILE__, __LINE__);
          return NULL;
        }
        return ptr;
      } else { //If size less then reclaim old blocks
        clearNBlocks(pNodeInfo->oldptr, pNodeInfo->newptr);
        //update oldptr and newptr
        updatePtrs();
        //look for free space if available in the free blocks
        if((ptr = lookUpFreeSpace(pNodeInfo->newptr, pNodeInfo->oldptr, size)) != NULL) {
          return ptr;
        } else {
          if((ptr = allocateNew(size)) == NULL) {
            printf("Error: %s() Calloc error %s %d\n", __func__, __FILE__, __LINE__);
            return NULL;
          }
          return ptr;
        }
      }
    }
  }
}

void *normalPrefetchAlloc(objstr_t *store, unsigned int size) {
  void *tmp;
  while (1) {
    if(((unsigned int)store->top - (((unsigned int)store) + sizeof(objstr_t)) + size) <= store->size) { //store not full
      tmp = store->top;
      store->top += size;
      return tmp;
    }   
    //store full
    if(store->next == NULL) {
      //end of list, all full
      if(size > DEFAULT_OBJ_STORE_SIZE) {
        //in case of large objects
        if((store->next = (objstr_t *) calloc(1,(sizeof(objstr_t) + size))) == NULL) {
          printf("%s() Calloc error at line %d, %s\n", __func__, __LINE__, __FILE__);
          return NULL;
        }   
        store = store->next;
        store->size = size;
      } else {
        if((store->next = (objstr_t *) calloc(1, (sizeof(objstr_t) + DEFAULT_OBJ_STORE_SIZE))) == NULL) {
          printf("%s() Calloc error at line %d, %s\n", __func__, __LINE__, __FILE__);
          return NULL;
        }   
        store = store->next;
        store->size = DEFAULT_OBJ_STORE_SIZE;
      }
      //Update maxsize of objstr blocks, num of blocks and newptr 
      pNodeInfo->num_old_objstr++;
      if(pNodeInfo->num_old_objstr <= PREFETCH_FLUSH_COUNT_THRESHOLD/2)
        pNodeInfo->newptr = store;
      if(pNodeInfo->maxsize < size)
        pNodeInfo->maxsize = size;
      store->top = (void *)(((unsigned int)store) + sizeof(objstr_t) + size);
      return (void *)(((unsigned int)store) + sizeof(objstr_t));
    } else {
      store = store->next;
    }
  }
}

void *lookUpFreeSpace(void *startAddr, void *endAddr, int size) {
  objstr_t *ptr;
  void *tmp;
  ptr = (objstr_t *) (startAddr);
  while(ptr != NULL && ((unsigned long int)ptr!= (unsigned long int)endAddr)) { 
    if(((unsigned int)ptr->top - (((unsigned int)ptr) + sizeof(objstr_t)) + size) <= ptr->size) { //store not full
      tmp = ptr->top;
      ptr->top += size;
      return tmp;
    }
    ptr = ptr->next;
  }
  return NULL;
}

void clearNBlocks(void *oldaddr, void * newaddr) {
  int count = 0;
  objstr_t *tmp = (objstr_t *) oldaddr;
  pthread_mutex_lock(&pflookup.lock);
  while(((unsigned int) tmp != (unsigned int)newaddr) && (tmp != NULL)) {
    void * begin = (void *)tmp+sizeof(objstr_t);
    void * end = (void *)tmp+sizeof(objstr_t)+tmp->size;
    tmp->top = (void *)tmp+sizeof(objstr_t);
    clearPLookUpTable(begin, end);
    //TODO only for testing purpose, remove later
    memset(tmp->top, 0, tmp->size);
    tmp = tmp->next;
  }
  pthread_mutex_unlock(&pflookup.lock);
}

void clearPLookUpTable(void *begin, void *end) {
  unsigned long int tmpbegin;
  unsigned long int tmpend;
  tmpbegin = (unsigned long int) begin;
  tmpend = (unsigned long int) end;
  int i, j;
  prehashlistnode_t *ptr = pflookup.table;
  for(i = 0; i<pflookup.size; i++) {
    prehashlistnode_t *curr = &ptr[i];
    for(; curr != NULL; curr = curr->next) {
      if(((unsigned long int)(curr->val) >= tmpbegin) && ((unsigned long int)(curr->val) < tmpend)) {
        unsigned int oid = curr->key;
        objheader_t *objheader;
        if((objheader = prehashSearch(oid)) != NULL) {
          prehashRemove(oid);
#ifdef CHECKTA
        printf("%s() clearing Look up table for oid = %x\n", __func__, oid);
#endif
        }
      }
    }
  }
}

void updatePtrs() {
  void *ptr;
  ptr = pNodeInfo->oldptr;
  pNodeInfo->oldptr = pNodeInfo->newptr;
  pNodeInfo->newptr = ptr;
}

void *allocateNew(unsigned int size) {
  objstr_t *tmp;
  if((tmp = (objstr_t *) calloc(1, (sizeof(objstr_t) +size))) == NULL) {
    printf("Error: %s() Calloc error %s %d\n", __func__, __FILE__, __LINE__);
    return NULL;
  }
  tmp->size = size;
  tmp->top = (void *)(((unsigned int)tmp) + sizeof(objstr_t) + size);
  //Insert newly allocated block into linked list of prefetch cache
  tmp->next = ((objstr_t *)(pNodeInfo->newptr))->next;
  ((objstr_t *)(pNodeInfo->newptr))->next = tmp;
  pNodeInfo->num_old_objstr++;
  // Update maxsize of prefetch objstr blocks 
  if(pNodeInfo->maxsize < tmp->size)
    pNodeInfo->maxsize = tmp->size;
  return (void *)(((unsigned int)tmp) + sizeof(objstr_t));
}
