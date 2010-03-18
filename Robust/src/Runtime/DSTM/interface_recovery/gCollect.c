#include "gCollect.h"
#include "altprelookup.h"


extern pthread_mutex_t prefetchcache_mutex; //Mutex to lock Prefetch Cache
extern prehashtable_t pflookup; //Global prefetch cache  lookup table
prefetchNodeInfo_t pNodeInfo; //Global prefetch holding metadata

#define OSUSED(x) (((unsigned int)(x)->top)-((unsigned int) (x+1)))
#define OSFREE(x) ((x)->size-OSUSED(x))

void initializePCache() {
  objstr_t * os=objstrCreate(DEFAULT_OBJ_STORE_SIZE);
  pNodeInfo.oldptr = os;
  pNodeInfo.newptr = os;
  pNodeInfo.os_count = 1; //for prefetch cache allocated by objstralloc in trans.c file
  pNodeInfo.oldstale=NULL;
  pNodeInfo.newstale=NULL;
  pNodeInfo.stale_count=0;
  pNodeInfo.stall=0;
}

objstr_t * getObjStr(unsigned int size) {
  if (pNodeInfo.stall>0)
    pNodeInfo.stall--;
  if (size<=DEFAULT_OBJ_STORE_SIZE&&pNodeInfo.stale_count>STALE_MINTHRESHOLD&&pNodeInfo.stall==0) {
    //recycle
    objstr_t * tmp=pNodeInfo.oldstale;
    pNodeInfo.oldstale=pNodeInfo.oldstale->prev;
    if (pNodeInfo.oldstale==NULL)
      pNodeInfo.newstale=NULL;
    pNodeInfo.stale_count--;
    tmp->top=tmp+1;
    tmp->prev=NULL;
    return tmp;
  } else {
    int allocsize=(size>DEFAULT_OBJ_STORE_SIZE)?size:DEFAULT_OBJ_STORE_SIZE;
    return objstrCreate(allocsize);
  }
}

void *prefetchobjstrAlloc(unsigned int size) {
  //try existing space in first two OS
  objstr_t *os=pNodeInfo.newptr;
  if ((size&7)!=0)
    size+=(8-(size&7));
  if (size<=OSFREE(os)) {
    void *tmp=os->top;
    os->top=((char *)os->top)+size;
    return tmp;
  }
  if ((os=os->next)!=NULL&&(size<=OSFREE(os))) {
    void *tmp=os->top;
    os->top=((char *)os->top)+size;
    return tmp;
  }
  //need to allocate new space
  objstr_t *tmp=getObjStr(size);;

  //link new node in
  tmp->next=pNodeInfo.newptr;
  pNodeInfo.newptr->prev=tmp;
  pNodeInfo.newptr=tmp;
  pNodeInfo.os_count++;

  if (pNodeInfo.os_count>PREFETCH_FLUSH_THRESHOLD) {
    //remove oldest from linked list
    objstr_t *tofree=pNodeInfo.oldptr;
    pNodeInfo.oldptr=tofree->prev;
    pNodeInfo.os_count--;
    //need to flush cache
    clearBlock(tofree);
    if (pNodeInfo.newstale==NULL) {
      //first store
      pNodeInfo.newstale=pNodeInfo.oldstale=tofree;
      tofree->prev=NULL;
      pNodeInfo.stale_count++;
    } else {
      //just add it to the list
      pNodeInfo.newstale->prev=tofree;
      pNodeInfo.newstale=tofree;
      pNodeInfo.stale_count++;
    }
    if (pNodeInfo.stale_count>STALE_MAXTHRESHOLD) {
      //need to toss a store
      tofree=pNodeInfo.oldstale;
      pNodeInfo.oldstale=tofree->prev;
      pNodeInfo.stale_count--;
      free(tofree);
    }
  }

  void *ptr=tmp->top;
  tmp->top=((char *)tmp->top)+size;
  return ptr;
}

void clearBlock(objstr_t *block) {

  unsigned long int tmpbegin=(unsigned int)block;
  unsigned long int tmpend=(unsigned int)block->top;
  int i, j;
  prehashlistnode_t *ptr;

  int lockindex=0;
  ptr = pflookup.table;
  volatile unsigned int * lockptr_current=&pflookup.larray[lockindex].lock;
  while(!write_trylock(lockptr_current)) {
    sched_yield();
  }

  for(i = 0; i<pflookup.size; i++) {

    prehashlistnode_t *orig=&ptr[i];
    prehashlistnode_t *curr = orig;
    prehashlistnode_t *next=curr->next;
    for(; next != NULL; curr=next, next = next->next) {
      unsigned int val=(unsigned int)next->val;
      if ((val>=tmpbegin)&(val<tmpend)) {
	prehashlistnode_t *tmp=curr->next=next->next;
	free(next);
	next=curr;
	//loop condition is broken now...need to check before incrementing
	//	if (next==NULL)
	// break;
      }
    }
    {
      unsigned int val=(unsigned int)orig->val;
      if ((val>=tmpbegin)&(val<tmpend)) {
	if (orig->next==NULL) {
	  orig->key=0;
	  orig->val=NULL;
	} else {
	  next=orig->next;
	  orig->val=next->val;
	  orig->key=next->key;
	  orig->next=next->next;
	  free(next);
	}
      }
    }

    if(((i+1)&(pflookup.mask>>4))==0 && (i+1)<pflookup.size){
      // try to grab new lock
      lockindex++;
      volatile unsigned int * lockptr_new=&pflookup.larray[lockindex].lock;
      while(!write_trylock(lockptr_new)){
        sched_yield();
      }
      write_unlock(lockptr_current);
      lockptr_current=lockptr_new;      
    }
    
  }// end of for (pflokup)
  
  write_unlock(lockptr_current);
}

objstr_t *allocateNew(unsigned int size) {
  objstr_t *tmp;
  if((tmp = (objstr_t *) calloc(1, (sizeof(objstr_t) +size))) == NULL) {
    printf("Error: %s() Calloc error %s %d\n", __func__, __FILE__, __LINE__);
    return NULL;
  }
  tmp->size = size;
  tmp->top = (void *)(((unsigned int)tmp) + sizeof(objstr_t) + size);
  //Insert newly allocated block into linked list of prefetch cache
  // Update maxsize of prefetch objstr blocks
  return tmp;
}
