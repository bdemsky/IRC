#include <stdio.h>
#include <pthread.h>
#include <sys/syscall.h>
#include <unistd.h>
#include <errno.h>
#include <sched.h>
#include <stdarg.h>
#include <stdlib.h>
#include <runtime.h>
#include "mlp_lock.h"

static volatile unsigned int corecount=0;

void set_affinity() {
  int err;
  cpu_set_t cpumask;

  CPU_ZERO(&cpumask);

  int ourcount=atomicincandread(&corecount);
  ourcount=ourcount&7;
  int newvalue=ourcount>>1;
  if (ourcount&1) {
    newvalue=newvalue|4;
  }

  CPU_SET(newvalue, &cpumask);

  err = sched_setaffinity(syscall(SYS_gettid), sizeof(cpu_set_t), &cpumask);

  if (err == -1)
    printf("set_affinity: %s\n", strerror(errno));
}
