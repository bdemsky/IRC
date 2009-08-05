#include "mem.h"

#ifdef MULTICORE
#include "runtime.h"
#include "runtime_arch.h"

void * mycalloc(int m, int size) {
  void * p = NULL;
  int isize = size; 
  BAMBOO_START_CRITICAL_SECTION_MEM();
  p = BAMBOO_LOCAL_MEM_CALLOC(m, isize); // calloc(m, isize);
  if(p == NULL) {
	  BAMBOO_EXIT(0xa024);
  }
  BAMBOO_CLOSE_CRITICAL_SECTION_MEM();
  return p;
}

#ifdef MULTICORE_GC
void * mycalloc_share(struct garbagelist * stackptr, int m, int size) {
	void * p = NULL;
  int isize = 2*BAMBOO_CACHE_LINE_SIZE-4+(size-1)&(~BAMBOO_CACHE_LINE_MASK);
memalloc:
  BAMBOO_START_CRITICAL_SECTION_MEM();
  p = BAMBOO_SHARE_MEM_CALLOC_I(m, isize); // calloc(m, isize);
  if(p == NULL) {
		// no more global shared memory
		BAMBOO_CLOSE_CRITICAL_SECTION_MEM();
		// start gc
		gc(stackptr);

		// try to malloc again
		goto memalloc;
  }
  BAMBOO_CLOSE_CRITICAL_SECTION_MEM();
  return (void *)(BAMBOO_CACHE_LINE_SIZE+((int)p-1)&(~BAMBOO_CACHE_LINE_MASK));
}
#else
void * mycalloc_share(int m, int size) {
  void * p = NULL;
  int isize = 2*BAMBOO_CACHE_LINE_SIZE-4+(size-1)&(~BAMBOO_CACHE_LINE_MASK);
  BAMBOO_START_CRITICAL_SECTION_MEM();
  p = BAMBOO_SHARE_MEM_CALLOC_I(m, isize); // calloc(m, isize);
  if(p == NULL) {
		// no more global shared memory
		BAMBOO_EXIT(0xa025);
  }
  BAMBOO_CLOSE_CRITICAL_SECTION_MEM();
  return (void *)(BAMBOO_CACHE_LINE_SIZE+((int)p-1)&(~BAMBOO_CACHE_LINE_MASK));
}
#endif

void * mycalloc_i(int m, int size) {
  void * p = NULL;
  int isize = size; 
  p = BAMBOO_LOCAL_MEM_CALLOC(m, isize); // calloc(m, isize);
  if(p == NULL) {
	  BAMBOO_EXIT(0xa026);
  }
  return p;
}

void myfree(void * ptr) {
  BAMBOO_LOCAL_MEM_FREE(ptr);
  return;
}

#endif
