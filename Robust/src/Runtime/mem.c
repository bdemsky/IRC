#include "mem.h"

#ifdef MULTICORE
#include "runtime.h"
#include "runtime_arch.h"

#ifdef MULTICORE_GC
void * mycalloc_share(struct garbagelist * stackptr, 
		              int m, 
					  int size) {
	void * p = NULL;
  //int isize = 2*BAMBOO_CACHE_LINE_SIZE-4+(size-1)&(~BAMBOO_CACHE_LINE_MASK);
  int isize = (size & (~(BAMBOO_CACHE_LINE_MASK))) + (BAMBOO_CACHE_LINE_SIZE);
	int hasgc = 0;
memalloc:
  BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
  p = BAMBOO_SHARE_MEM_CALLOC_I(m, isize); // calloc(m, isize);
  if(p == NULL) {
		// no more global shared memory
		BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
		if(hasgc < 5) {
		    // start gc
			if(gc(stackptr)) {
			  hasgc++;
			}
		} else {
			// no more global shared memory
			BAMBOO_EXIT(0xc001);
		}

		// try to malloc again
		goto memalloc;
  }
  BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
	void * alignedp = 
		(void *)(BAMBOO_CACHE_LINE_SIZE+((int)p-1)&(~BAMBOO_CACHE_LINE_MASK));
	BAMBOO_MEMSET_WH(p, -2, (alignedp - p));
  BAMBOO_MEMSET_WH(alignedp + size, -2, p + isize - alignedp - size);
	return alignedp;
}
#else
void * mycalloc_share(int m, 
		                  int size) {
  void * p = NULL;
  //int isize = 2*BAMBOO_CACHE_LINE_SIZE-4+(size-1)&(~BAMBOO_CACHE_LINE_MASK);
  int isize = (size & (~(BAMBOO_CACHE_LINE_MASK))) + (BAMBOO_CACHE_LINE_SIZE);
  BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
  p = BAMBOO_SHARE_MEM_CALLOC_I(m, isize); // calloc(m, isize);
  if(p == NULL) {
		// no more global shared memory
		BAMBOO_EXIT(0xc002);
  }
  BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
  return 
		(void *)(BAMBOO_CACHE_LINE_SIZE+((int)p-1)&(~BAMBOO_CACHE_LINE_MASK));
}
#endif

void * mycalloc(int m, 
		        int size) {
  void * p = NULL;
  int isize = size; 
  BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
  p = BAMBOO_LOCAL_MEM_CALLOC(m, isize); // calloc(m, isize);
  if(p == NULL) {
	  BAMBOO_EXIT(0xc003);
  }
  BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
  return p;
}


void * mycalloc_i(int m, 
		          int size) {
  void * p = NULL;
  int isize = size; 
  p = BAMBOO_LOCAL_MEM_CALLOC(m, isize); // calloc(m, isize);
  if(p == NULL) {
	BAMBOO_EXIT(0xc004);
  }
  return p;
}

void myfree(void * ptr) {
  BAMBOO_LOCAL_MEM_FREE(ptr);
  return;
}

#endif
