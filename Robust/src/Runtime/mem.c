#include "mem.h"

#ifdef RAW
#include "runtime.h"
#include <raw.h>

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
#ifdef RAWDEBUG
  raw_test_pass(0xdd00);
#endif
#ifdef INTERRUPT
  // shut down interrupt
  raw_user_interrupts_off();
#endif
  p = calloc(m, isize);
  //p = m_calloc(m, isize);
#ifdef RAWDEBUG
  raw_test_pass_reg(p);
  raw_test_pass_reg((kCacheLineSize+((int)p-1)&(~kCacheLineMask)));
#endif
#ifdef INTERRUPT
  // re-open interruption
  raw_user_interrupts_on();
#endif
  return (void *)(kCacheLineSize+((int)p-1)&(~kCacheLineMask));
}

void * mycalloc_i(int m, int size) {
  void * p = NULL;
  int isize = 2*kCacheLineSize-4+(size-1)&(~kCacheLineMask);
#ifdef RAWDEBUG
  raw_test_pass(0xdd00);
#endif
  p = calloc(m, isize);
  //p = m_calloc(m, isize);
#ifdef RAWDEBUG
  raw_test_pass_reg(p);
  raw_test_pass_reg((kCacheLineSize+((int)p-1)&(~kCacheLineMask)));
#endif
  return (void *)(kCacheLineSize+((int)p-1)&(~kCacheLineMask));
}

void myfree(void * ptr) {
  return;
}

#endif
