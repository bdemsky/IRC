#include "mem.h"

#ifdef MULTICORE
#include "runtime.h"
#include "runtime_arch.h"

/*void * m_calloc(int m, int size) {
        void * p = malloc(m*size);
        int i = 0;
        for(i = 0; i < size; ++i) {
 *(char *)(p+i) = 0;
        }
        return p;
   }*/

void * mycalloc(int m, int size) {
  void * p = NULL;
  int isize = size; //2*BAMBOO_CACHE_LINE_SIZE-4+(size-1)&(~BAMBOO_CACHE_LINE_MASK);
  BAMBOO_START_CRITICAL_SECTION_MEM();
  p = BAMBOO_LOCAL_MEM_CALLOC(m, isize); // calloc(m, isize);
  if(p == NULL) {
	  BAMBOO_EXIT(0xa024);
  }
  BAMBOO_CLOSE_CRITICAL_SECTION_MEM();
  //return (void *)(BAMBOO_CACHE_LINE_SIZE+((int)p-1)&(~BAMBOO_CACHE_LINE_MASK));
  return p;
}

void * mycalloc_share(int m, int size) {
  void * p = NULL;
  int isize = 2*BAMBOO_CACHE_LINE_SIZE-4+(size-1)&(~BAMBOO_CACHE_LINE_MASK);
  BAMBOO_START_CRITICAL_SECTION_MEM();
  p = BAMBOO_SHARE_MEM_CALLOC_I(m, isize); // calloc(m, isize);
  if(p == NULL) {
	  BAMBOO_EXIT(0xa025);
  }
  BAMBOO_CLOSE_CRITICAL_SECTION_MEM();
  return (void *)(BAMBOO_CACHE_LINE_SIZE+((int)p-1)&(~BAMBOO_CACHE_LINE_MASK));
}

void * mycalloc_i(int m, int size) {
  void * p = NULL;
  int isize = size; //2*BAMBOO_CACHE_LINE_SIZE-4+(size-1)&(~BAMBOO_CACHE_LINE_MASK);
  p = BAMBOO_LOCAL_MEM_CALLOC(m, isize); // calloc(m, isize);
  if(p == NULL) {
	  BAMBOO_EXIT(0xa026);
  }
  return p;
  //return (void *)(BAMBOO_CACHE_LINE_SIZE+((int)p-1)&(~BAMBOO_CACHE_LINE_MASK));
}

void myfree(void * ptr) {
  BAMBOO_LOCAL_MEM_FREE(ptr);
  return;
}

#endif
