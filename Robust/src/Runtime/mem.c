#include "mem.h"

#ifdef MULTICORE
#include "runtime.h"
#include "runtime_arch.h"

void * mycalloc(int m, 
		            int size) {
  void * p = NULL;
  int isize = size; 
  BAMBOO_START_CRITICAL_SECTION_MEM();
  p = BAMBOO_LOCAL_MEM_CALLOC(m, isize); // calloc(m, isize);
  if(p == NULL) {
	  BAMBOO_EXIT(0xc001);
  }
  BAMBOO_CLOSE_CRITICAL_SECTION_MEM();
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
  BAMBOO_START_CRITICAL_SECTION_MEM();
#ifdef DEBUG
	tprintf("ask for shared mem: %x \n", isize);
#endif
  p = BAMBOO_SHARE_MEM_CALLOC_I(m, isize); // calloc(m, isize);
#ifdef DEBUG
	tprintf("new obj in shared mem: %x, %x \n", p, isize);
#endif
  if(p == NULL) {
		// no more global shared memory
		BAMBOO_CLOSE_CRITICAL_SECTION_MEM();
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
  BAMBOO_CLOSE_CRITICAL_SECTION_MEM();
	void * alignedp = 
		(void *)(BAMBOO_CACHE_LINE_SIZE+((int)p-1)&(~BAMBOO_CACHE_LINE_MASK));
	memset(p, -2, (alignedp - p));
  memset(alignedp + size, -2, p + isize - alignedp - size);
	return alignedp;
}
#else
void * mycalloc_share(int m, 
		                  int size) {
  void * p = NULL;
  int isize = 2*BAMBOO_CACHE_LINE_SIZE-4+(size-1)&(~BAMBOO_CACHE_LINE_MASK);
  BAMBOO_START_CRITICAL_SECTION_MEM();
  p = BAMBOO_SHARE_MEM_CALLOC_I(m, isize); // calloc(m, isize);
  if(p == NULL) {
		// no more global shared memory
		BAMBOO_EXIT(0xc003);
  }
  BAMBOO_CLOSE_CRITICAL_SECTION_MEM();
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
