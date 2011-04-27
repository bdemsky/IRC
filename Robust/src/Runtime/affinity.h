#ifndef AFFINITY_H
#define AFFINITY_H
#include <sys/syscall.h>
#include <sched.h>
#include <errno.h>

static void set_affinity(unsigned long cpu) {
  int err;
  cpu_set_t cpumask;

  CPU_ZERO(&cpumask);
  CPU_SET(cpu, &cpumask);
  err = sched_setaffinity(syscall(SYS_gettid),
                          sizeof(cpu_set_t), &cpumask);

  if (err == -1)
    printf("set_affinity: %s\n", strerror(errno));
}
#endif
