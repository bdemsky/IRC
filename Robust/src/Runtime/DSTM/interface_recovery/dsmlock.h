#ifndef _DSMLOCK_H_
#define _DSMLOCK_H_

#define CFENCE   asm volatile("":::"memory");
#define RW_LOCK_BIAS             0x01000000
#define atomic_read(v)          (*v)
#define RW_LOCK_UNLOCKED          { RW_LOCK_BIAS }
//#define LOCK_PREFIX ""
#define LOCK_PREFIX \
  ".section .smp_locks,\"a\"\n"   \
  "  .align 4\n"                  \
  "  .long 661f\n"             /* address */\
  ".previous\n"                   \
  "661:\n\tlock; "


void initdsmlocks(volatile int *addr);
int read_trylock(volatile int *lock);
int write_trylock(volatile int *lock);
void atomic_dec(volatile int *v);
void atomic_inc(volatile int *v);
static void atomic_add(int i, volatile int *v);
static int atomic_sub_and_test(int i, volatile int *v);
void read_unlock(volatile int *rw);
void write_unlock(volatile int *rw);
int is_write_locked(volatile int *lock);
int is_read_locked(volatile int *lock);
#endif
