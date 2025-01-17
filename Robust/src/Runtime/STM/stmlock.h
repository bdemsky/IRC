#ifndef _STMLOCK_H_
#define _STMLOCK_H_

#define likely(x) __builtin_expect((x),1)
#define unlikely(x) __builtin_expect((x),0)

#define SWAP_LOCK_BIAS                 1
#define CFENCE   asm volatile("":::"memory");

#define RW_LOCK_BIAS             0x01000000

#define LOCK_PREFIX \
  ".section .smp_locks,\"a\"\n"   \
  "  .align 4\n"                  \
  "  .long 661f\n"             /* address */\
  ".previous\n"                   \
  "661:\n\tlock; "

static inline initdsmlocks(volatile int *addr) {
  (*addr) = SWAP_LOCK_BIAS;
}
//int write_trylock(volatile unsigned int *lock);
//void write_unlock(volatile unsigned int *lock);

/*
static inline void initdsmlocks(volatile unsigned int *addr) {
  (*addr) = RW_LOCK_BIAS;
}
*/

static inline int write_trylock(volatile int *lock) {
  int retval=0;
  __asm__ __volatile__("xchgl %0,%1"
		       : "=r"(retval)
		       : "m"(*lock), "0"(retval)
		       : "memory");
  return retval;
}

static inline void write_unlock(volatile int *lock) {
  __asm__ __volatile__("movl $1, %0" : "+m" (*lock)::"memory");
}


static inline void atomic_add(int i, volatile int *v) {
  __asm__ __volatile__ (LOCK_PREFIX "addl %1,%0"
                        : "+m" (*v)
                        : "ir" (i));
}

static inline void rwread_unlock(volatile int *rw) {
  __asm__ __volatile__ (LOCK_PREFIX "incl %0" : "+m" (*rw) : : "memory");
}

static inline void rwwrite_unlock(volatile int *rw) {
  __asm__ __volatile__ (LOCK_PREFIX "addl %1, %0"
                        : "+m" (*rw) : "i" (RW_LOCK_BIAS) : "memory");
}

static inline void rwconvert_unlock(volatile int *rw) {
  __asm__ __volatile__ (LOCK_PREFIX "addl %1, %0"
                        : "+m" (*rw) : "i" (RW_LOCK_BIAS-1) : "memory");
}

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

static inline int rwwrite_trylock(volatile int  *ptr) {
//static inline unsigned long cas(volatile unsigned int* ptr) {
  int prev;
  __asm__ __volatile__("lock;"
		       "cmpxchgl %1, %2;"
		       : "=a"(prev)
		       : "r"(0), "m"(*ptr), "a"(RW_LOCK_BIAS)
		       : "memory");
  return prev==RW_LOCK_BIAS;
}


#define atomic_read(v)          (*v)

static inline int rwread_trylock(volatile int  *lock) {
  atomic_dec(lock);
  if (likely(atomic_read(lock) >=0 ))
    return 1; //can aquire a new read lock
  atomic_inc(lock);
  return 0; //failure
}

//static inline int rwwrite_trylock(volatile unsigned int  *lock) {
//  if (likely(atomic_sub_and_test(RW_LOCK_BIAS, lock))) {
//  return 1; // get a write lock
//  }
//  atomic_add(RW_LOCK_BIAS, lock);
//  return 0; // failed to acquire a write lock
//}

static inline int rwconvert_trylock(volatile int  *lock) {
  if (likely(atomic_sub_and_test((RW_LOCK_BIAS-1), lock))) {
    return 1; // get a write lock
  }
  atomic_add((RW_LOCK_BIAS-1), lock);
  return 0; // failed to acquire a write lock
}
#endif
