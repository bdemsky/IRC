#include "runtime.h"

#include <stdlib.h>
#include <stdio.h>
#include <assert.h>

#define __xg(x) ((volatile INTPTR *)(x))

#define CFENCE   asm volatile("":::"memory");

#define LOCK_PREFIX \
  ".section .smp_locks,\"a\"\n"   \
  "  .align 4\n"                  \
  "  .long 661f\n"             /* address */\
  ".previous\n"                   \
  "661:\n\tlock; "


static inline void atomic_dec(volatile int *v) {
  __asm__ __volatile__ (LOCK_PREFIX "decl %0"
                        : "+m" (*v));
}

static inline void atomic_inc(volatile int *v) {
  __asm__ __volatile__ (LOCK_PREFIX "incl %0"
                        : "+m" (*v));
}

static inline int atomic_sub_and_test(int i, volatile int *v) {
  unsigned char c;

  __asm__ __volatile__ (LOCK_PREFIX "subl %2,%0; sete %1"
                        : "+m" (*v), "=qm" (c)
                        : "ir" (i) : "memory");
  return c;
}

#ifdef BIT64
static inline INTPTR LOCKXCHG(volatile INTPTR * ptr, INTPTR val){
  INTPTR retval;
  //note: xchgl always implies lock 
  __asm__ __volatile__("xchgq %0,%1"
		       : "=r"(retval)
		       : "m"(*ptr), "0"(val)
		       : "memory");
  return retval;
 
}
#else
static inline int LOCKXCHG(volatile int* ptr, int val){
  int retval;
  //note: xchgl always implies lock 
  __asm__ __volatile__("xchgl %0,%1"
		       : "=r"(retval)
		       : "m"(*ptr), "0"(val)
		       : "memory");
  return retval;
 
}
#endif

/*
static inline int write_trylock(volatile int *lock) {
  int retval=0;
  __asm__ __volatile__("xchgl %0,%1"
		       : "=r"(retval)
		       : "m"(*lock), "0"(retval)
		       : "memory");
  return retval;
}
*/

#ifdef BIT64
static inline INTPTR CAS(volatile void *ptr, unsigned INTPTR old, unsigned INTPTR new){
  unsigned INTPTR prev;
  __asm__ __volatile__("lock; cmpxchgq %1,%2"
		       : "=a"(prev)
		       : "r"(new), "m"(*__xg(ptr)), "0"(old)
		       : "memory");
  return prev;
}
#else
static inline long CAS(volatile void *ptr, unsigned long old, unsigned long new){
  unsigned long prev;
  __asm__ __volatile__("lock; cmpxchgl %k1,%2"
		       : "=a"(prev)
		       : "r"(new), "m"(*__xg(ptr)), "0"(old)
		       : "memory");
  return prev;
}
#endif

static inline int BARRIER(){
  CFENCE;
  return 1;
}
