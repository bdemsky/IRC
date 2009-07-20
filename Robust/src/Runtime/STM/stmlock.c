#include "stmlock.h"
#include <stdio.h>


inline void initdsmlocks(volatile unsigned int *addr) {
  (*addr) = RW_LOCK_BIAS;
}

/*
int write_trylock(volatile unsigned int *lock) {
  int retval=0;
  __asm__ __volatile__("xchgl %0,%1"
	       : "=r"(retval)
	       : "m"(*__xg(lock)), "0"(retval)
	       : "memory");
  return retval;
}
*/

void write_unlock(volatile unsigned int *lock) {
  __asm __volatile__("movl $1, %0" : "+m" (*lock)::"memory");
}

