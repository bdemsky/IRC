#include "mem.h"

#ifdef MULTICORE
#include "runtime.h"
#include "runtime_arch.h"

void * mycalloc(int m, 
		        int size) {
  void * p = NULL;
  int isize = size; 
  BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
  p = BAMBOO_LOCAL_MEM_CALLOC(m, isize); // calloc(m, isize);
  if(p == NULL) {
	  BAMBOO_EXIT(0xc001);
  }
  BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
  return p;
}

#ifdef MULTICORE_GC
void * mycalloc_share(struct garbagelist * stackptr, 
		              int m, 
					  int size) {
	void * p = NULL;
  int isize = 2*BAMBOO_CACHE_LINE_SIZE-4+(size-1)&(~BAMBOO_CACHE_LINE_MASK);
	bool hasgc = false;
memalloc:
  BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
#ifdef DEBUG
	tprintf("ask for shared mem: %x \n", isize);
#endif
  p = BAMBOO_SHARE_MEM_CALLOC_I(m, isize); // calloc(m, isize);
#ifdef DEBUG
	tprintf("new obj in shared mem: %x, %x \n", p, isize);
#endif
  if(p == NULL) {
		// no more global shared memory
		BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
		if(!hasgc) {
			// start gc
			gc(stackptr);
			hasgc = true;
		} else {
			// no more global shared memory
			BAMBOO_EXIT(0xc002);
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

void * mycalloc_share_ngc(int m, 
					      int size) {
  void * p = NULL;
  BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
#ifdef DEBUG
	tprintf("ask for shared mem: %x \n", size);
#endif
  p = BAMBOO_SHARED_MEM_CALLOC_NGC_I(m, size); // calloc(m, isize);
#ifdef DEBUG
  printf("new obj in shared mem: %x, %x \n", p, size);
#endif
  BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
  return p;
}

void * mycalloc_share_ngc_I(int m, 
					        int size) {
  void * p = NULL;
#ifdef DEBUG
	tprintf("ask for shared mem: %x \n", size);
#endif
  p = BAMBOO_SHARED_MEM_CALLOC_NGC_I(m, size); // calloc(m, isize);
#ifdef DEBUG
  printf("new obj in shared mem: %x, %x \n", p, size);
#endif
  return p;
}

void mycalloc_free_ngc(void * ptr) {
  BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
  BAMBOO_SHARED_MEM_FREE_NGC_I(ptr);
  BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
}

void mycalloc_free_ngc_I(void * ptr) {
  BAMBOO_SHARED_MEM_FREE_NGC_I(ptr);
}
#else
void * mycalloc_share(int m, 
		                  int size) {
  void * p = NULL;
  int isize = 2*BAMBOO_CACHE_LINE_SIZE-4+(size-1)&(~BAMBOO_CACHE_LINE_MASK);
  BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
  p = BAMBOO_SHARE_MEM_CALLOC_I(m, isize); // calloc(m, isize);
  if(p == NULL) {
		// no more global shared memory
		BAMBOO_EXIT(0xc003);
  }
  BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
  return 
		(void *)(BAMBOO_CACHE_LINE_SIZE+((int)p-1)&(~BAMBOO_CACHE_LINE_MASK));
}
#endif

void * mycalloc_i(int m, 
		          int size) {
  void * p = NULL;
  int isize = size; 
#ifdef DEBUG
  tprintf("ask for local mem: %x \n", isize);
#endif
  p = BAMBOO_LOCAL_MEM_CALLOC(m, isize); // calloc(m, isize);
#ifdef DEBUG
  tprintf("new obj in local mem: %x, %x \n", p, isize);
#endif
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
