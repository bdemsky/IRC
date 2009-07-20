#ifndef _STMLOCK_H_
#define _STMLOCK_H_

#define RW_LOCK_BIAS                 1
#define LOCK_UNLOCKED          { LOCK_BIAS }
#define CFENCE   asm volatile("":::"memory");

struct __xchg_dummy {
	unsigned long a[100];
};

#define __xg(x) ((struct __xchg_dummy *)(x))

void initdsmlocks(volatile unsigned int *addr);
//int write_trylock(volatile unsigned int *lock);
void write_unlock(volatile unsigned int *lock);

/*
static inline void initdsmlocks(volatile unsigned int *addr) {
  (*addr) = RW_LOCK_BIAS;
}
*/
static inline int write_trylock(volatile unsigned int *lock) {
  int retval=0;
  __asm__ __volatile__("xchgl %0,%1"
		       : "=r"(retval)
		       : "m"(*lock), "0"(retval)
		       : "memory");
  return retval;
}

/*
static inline void write_unlock(volatile unsigned int *lock) {
  __asm __volatile__("movl $1, %0" : "+m" (*__xg(lock))::"memory");
}
*/

#endif
