#ifndef _DSMLOCK_H_
#define _DSMLOCK_H_

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



typedef struct {
  unsigned int counter;
} atomic_t;

void initdsmlocks(volatile unsigned int *addr);
int read_trylock(volatile unsigned int *lock);
int write_trylock(volatile unsigned int *lock);
void atomic_dec(volatile unsigned int *v);
void atomic_inc(volatile unsigned int *v);
static void atomic_add(int i, volatile unsigned int *v);
static int atomic_sub_and_test(int i, volatile unsigned int *v);
void read_unlock(volatile unsigned int *rw);
void write_unlock(volatile unsigned int *rw);
#endif
