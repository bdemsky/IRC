#ifndef _STMLOCK_H_
#define _STMLOCK_H_

#define RW_LOCK_BIAS                 1
#define LOCK_UNLOCKED          { LOCK_BIAS }

struct __xchg_dummy {
	unsigned long a[100];
};

#define __xg(x) ((struct __xchg_dummy *)(x))

void initdsmlocks(volatile unsigned int *addr);
int write_trylock(volatile unsigned int *lock);
void write_unlock(volatile unsigned int *lock);

#endif
