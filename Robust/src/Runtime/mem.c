#include "mem.h"

#ifdef MULTICORE
#include "runtime.h"
#include "runtime_arch.h"

#ifdef MULTICORE_GC
#include "bambooalign.h"
#include "multicoremem.h"
#include "multicoregarbage.h"


extern volatile bool gcflag;
void * mycalloc_share(struct garbagelist * stackptr, int size) {
  void * p = NULL;
  int isize = ((size-1)&(~(ALIGNMENTSIZE-1)))+ALIGNMENTSIZE;
  int hasgc = 0;

  while(true) {
    if(gcflag) {
      gc(stackptr);
    }
    p = BAMBOO_SHARE_MEM_CALLOC(isize); // calloc(m, isize);
    if(p == NULL) {
      // no more global shared memory
      if(hasgc < 5) {
	// start gc
	if(gcflag) {
	  gc(stackptr);
	}
	hasgc++;
      } else {
	// no more global shared memory
	BAMBOO_EXIT();
      }
    } else
      break;
  }
  
  return p;
}
#else
void * mycalloc_share(int size) {
  int isize = ((size-1)&(~(BAMBOO_CACHE_LINE_MASK)))+(BAMBOO_CACHE_LINE_SIZE);
  void * p = BAMBOO_SHARE_MEM_CALLOC(isize); // calloc(m, isize);
  if(p == NULL) {
    // no more global shared memory
    BAMBOO_EXIT();
  }
  return (void *)(BAMBOO_CACHE_LINE_SIZE+((int)p-1)&(~BAMBOO_CACHE_LINE_MASK));
}
#endif

void * mycalloc(int size, char * file, int line) {
  BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
  void * p = mycalloc_i(size, file, line);
  BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
  return p;
}


void * mycalloc_i(int size, char * file, int line) {
  void * p = BAMBOO_LOCAL_MEM_CALLOC(size);
  if(p == NULL) {
    tprintf("mycalloc_i %s %d \n", file, line);
    BAMBOO_EXIT();
  }
  return p;
}

void myfree(void * ptr) {
  BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
  BAMBOO_LOCAL_MEM_FREE(ptr);
  BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
  return;
}

void myfree_i(void * ptr) {
  BAMBOO_LOCAL_MEM_FREE(ptr);
  return;
}

#endif
