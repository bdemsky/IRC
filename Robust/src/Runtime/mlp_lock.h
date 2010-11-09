#ifndef ____MLP_LOCK_H__
#define ____MLP_LOCK_H__


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

// this returns TRUE if the atomic subtraction results in
// a zero value--this way two threads cannot dec a value
// atomically, but then go ahead and both read zero,
// thinking they both are the last decrementer
static inline int atomic_sub_and_test(int i, volatile int *v) {
  unsigned char c;

  __asm__ __volatile__ (LOCK_PREFIX "subl %2,%0; sete %1"
                        : "+m" (*v), "=qm" (c)
                        : "ir" (i) : "memory");
  return c;
}


static inline void atomic_add(int i, volatile int *v) {
  __asm__ __volatile__ (LOCK_PREFIX "addl %1,%0"
                        : "+m" (*v)
                        : "ir" (i));
}

static inline int LOCKXCHG32(volatile int* ptr, int val){
  int retval;
  //note: xchgl always implies lock 
  __asm__ __volatile__("xchgl %0,%1"
		       : "=r"(retval)
		       : "m"(*ptr), "0"(val)
		       : "memory");
  return retval;
 
}


// LOCKXCH atomically does the following:
// INTPTR retval=*ptr; 
// *ptr=val; 
// return retval
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
#define LOCKXCHG LOCKXCHG32
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


#endif // ____MLP_LOCK_H__
