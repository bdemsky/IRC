#ifndef _DSMLOCK_H_
#define _DSMLOCK_H_

#define RW_LOCK_BIAS             0x01000000
#define atomic_read(v)          ((v)->counter)
#define RW_LOCK_UNLOCKED          { RW_LOCK_BIAS }
#define LOCK_PREFIX ""

typedef struct {
  int counter;
} atomic_t;

void initdsmlocks(volatile unsigned int *addr);
void readLock(volatile unsigned int *addr);
void writeLock(volatile unsigned int *addr);
int read_trylock(volatile unsigned int *lock);
int write_trylock(volatile unsigned int *lock);
static void atomic_dec(atomic_t *v);
static void atomic_inc(atomic_t *v);
static void atomic_add(int i, atomic_t *v);
static int atomic_sub_and_test(int i, atomic_t *v);
void read_unlock(volatile unsigned int *rw);
void write_unlock(volatile unsigned int *rw);
#endif
