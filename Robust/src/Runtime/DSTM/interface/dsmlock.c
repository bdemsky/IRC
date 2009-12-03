#include "dsmlock.h"
#include <stdio.h>

inline void initdsmlocks(volatile int *addr) {
  (*addr) = RW_LOCK_BIAS;
}

inline void atomic_dec(volatile int *v) {
  __asm__ __volatile__ (LOCK_PREFIX "decl %0"
			: "+m" (*v));
}

inline void atomic_inc(volatile int *v) {
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

/**
 * atomic_add - add integer to atomic variable
 * @i: integer value to add
 * @v: pointer of type atomic_t
 *
 * Atomically adds @i to @v.
 */
static inline void atomic_add(int i, volatile int *v) {
  __asm__ __volatile__ (LOCK_PREFIX "addl %1,%0"
			: "+m" (*v)
			: "ir" (i));
}

inline int read_trylock(volatile int  *lock) {
  atomic_dec(lock);
  if (atomic_read(lock) >= 0)
    return 1; //can aquire a new read lock
  atomic_inc(lock);
  return 0; //failure
}

inline int write_trylock(volatile int  *lock) {
  if (atomic_sub_and_test(RW_LOCK_BIAS, lock)) {
    return 1; // get a write lock
  }
  atomic_add(RW_LOCK_BIAS, lock);
  return 0; // failed to acquire a write lock
}

inline void read_unlock(volatile int *rw) {
  __asm__ __volatile__ (LOCK_PREFIX "incl %0" : "+m" (*rw) : : "memory");
}

inline void write_unlock(volatile int *rw) {
  __asm__ __volatile__ (LOCK_PREFIX "addl %1, %0"
			: "+m" (*rw) : "i" (RW_LOCK_BIAS) : "memory");
}
