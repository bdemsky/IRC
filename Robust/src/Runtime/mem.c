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
  int isize = 2*kCacheLineSize-4+(size-1)&(~kCacheLineMask);
  BAMBOO_START_CRITICAL_SECTION_MEM();
  p = BAMBOO_SHARE_MEM_CALLOC(m, isize); // calloc(m, isize);
  BAMBOO_CLOSE_CRITICAL_SECTION_MEM();
  return (void *)(kCacheLineSize+((int)p-1)&(~kCacheLineMask));
}

void * mycalloc_i(int m, int size) {
  void * p = NULL;
  int isize = 2*kCacheLineSize-4+(size-1)&(~kCacheLineMask);
  p = BAMBOO_SHARE_MEM_CALLOC(m, isize); // calloc(m, isize);
  return (void *)(kCacheLineSize+((int)p-1)&(~kCacheLineMask));
}

void myfree(void * ptr) {
  return;
}

#endif
