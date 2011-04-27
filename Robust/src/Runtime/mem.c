#include "mem.h"

#ifdef MULTICORE
#include "runtime.h"
#include "runtime_arch.h"

#ifdef MULTICORE_GC
extern volatile bool gcflag;
void * mycalloc_share(struct garbagelist * stackptr,
                      int m,
                      int size) {
  void * p = NULL;
  //int isize = 2*BAMBOO_CACHE_LINE_SIZE-4+(size-1)&(~BAMBOO_CACHE_LINE_MASK);
  int isize = (size & (~(BAMBOO_CACHE_LINE_MASK))) + (BAMBOO_CACHE_LINE_SIZE);
  int hasgc = 0;
memalloc:
  BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
  while(gcflag) {
    BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
    gc(stackptr);
    BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
  }
  p = BAMBOO_SHARE_MEM_CALLOC_I(m, isize); // calloc(m, isize);
  if(p == NULL) {
    // no more global shared memory
    BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
    if(hasgc < 5) {
      // start gc
      while(gcflag) {
        gc(stackptr);
      }
      hasgc++;
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
                int size,
                char * file,
                int line) {
  void * p = NULL;
  int isize = size;
  BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
#ifdef MULTICORE_GC
  extern bool gc_localheap_s;
inermycalloc_i:
  p = gc_localheap_s ? BAMBOO_LOCAL_MEM_CALLOC_S(m, isize) :
      BAMBOO_LOCAL_MEM_CALLOC(m, isize);
#else
  p = BAMBOO_LOCAL_MEM_CALLOC(m, isize); // calloc(m, isize);
#endif
  if(p == NULL) {
#ifdef MULTICORE_GC
    if(!gc_localheap_s) {
      gc_localheap_s = true;
      goto inermycalloc_i;
    }
#endif
    printf("mycalloc %s %d \n", file, line);
    BAMBOO_EXIT(0xc003);
  }
  BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
  return p;
}


void * mycalloc_i(int m,
                  int size,
                  char * file,
                  int line) {
  void * p = NULL;
  int isize = size;
#ifdef MULTICORE_GC
  extern bool gc_localheap_s;
inermycalloc_i:
  p = gc_localheap_s ? BAMBOO_LOCAL_MEM_CALLOC_S(m, isize) :
      BAMBOO_LOCAL_MEM_CALLOC(m, isize);
#else
  p = BAMBOO_LOCAL_MEM_CALLOC(m, isize); // calloc(m, isize);
#endif
  if(p == NULL) {
#ifdef MULTICORE_GC
    if(!gc_localheap_s) {
      gc_localheap_s = true;
      goto inermycalloc_i;
    }
#endif
    tprintf("macalloc_i %s %d \n", file, line);
    BAMBOO_EXIT(0xc004);
  }
  return p;
}

void myfree(void * ptr) {
#ifdef MULTICORE_GC
  if(ptr >= BAMBOO_LOCAL_HEAP_START_VA ) {
#endif
  BAMBOO_LOCAL_MEM_FREE(ptr);
#ifdef MULTICORE_GC
} else if(ptr >= BAMBOO_LOCAL_HEAP_START_VA_S) {
  BAMBOO_LOCAL_MEM_FREE_S(ptr);
}
#endif
  return;
}

#endif
