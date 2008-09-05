#include "dsmlock.h"
#include <stdio.h>

inline void initdsmlocks(volatile unsigned int *addr) {
  (*addr) = RW_LOCK_BIAS;
}


inline void readLock(volatile unsigned int *addr) {
  __asm__ __volatile__ ("" " subl $1,(%0)\n\t"
                        "jns 1f\n"
                        "1:\n"
                        :: "a" (addr) : "memory");
}

inline void writeLock(volatile unsigned int *addr) {
  __asm__ __volatile__ ("" " subl %1,(%0)\n\t"
                        "jz 1f\n"
                        "1:\n"
                        :: "a" (addr), "i" (RW_LOCK_BIAS) : "memory");
}

static inline void atomic_dec(atomic_t *v) {
  __asm__ __volatile__ (LOCK_PREFIX "decl %0"
			: "+m" (v->counter));
}

static inline void atomic_inc(atomic_t *v) {
  __asm__ __volatile__ (LOCK_PREFIX "incl %0"
			: "+m" (v->counter));
}

static inline int atomic_sub_and_test(int i, atomic_t *v) {
  unsigned char c;

  __asm__ __volatile__ (LOCK_PREFIX "subl %2,%0; sete %1"
			: "+m" (v->counter), "=qm" (c)
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
static inline void atomic_add(int i, atomic_t *v) {
  __asm__ __volatile__ (LOCK_PREFIX "addl %1,%0"
			: "+m" (v->counter)
			: "ir" (i));
}

inline int read_trylock(volatile unsigned int  *lock) {
  atomic_t *count = (atomic_t *)lock;

  atomic_dec(count);
  if (atomic_read(count) >= 0)
    return 1; //can aquire a new read lock
  atomic_inc(count);
  return 0; //failure
}

inline int write_trylock(volatile unsigned int  *lock) {
  atomic_t *count = (atomic_t *)lock;
  if (atomic_sub_and_test(RW_LOCK_BIAS, count)) {
    return 1; // get a write lock
  }
  atomic_add(RW_LOCK_BIAS, count);
  return 0; // failed to acquire a write lock
}

inline void read_unlock(volatile unsigned int *rw) {
  __asm__ __volatile__ (LOCK_PREFIX "incl %0" : "+m" (*rw) : : "memory");
}

inline void write_unlock(volatile unsigned int *rw) {
  __asm__ __volatile__ (LOCK_PREFIX "addl %1, %0"
			: "+m" (*rw) : "i" (RW_LOCK_BIAS) : "memory");
}
