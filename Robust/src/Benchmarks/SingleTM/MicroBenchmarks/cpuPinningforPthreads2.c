/*******************************************************************
 * Example that uses sched_affinity for cpu pinning 
 *
 * adash@uci.edu
 ********************************************************************/

#define _GNU_SOURCE

#include <errno.h>
#include <pthread.h>
#include <sched.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/syscall.h>
#include <unistd.h>

#define ITERATIONS 100000
pthread_barrier_t barr;

static __inline__ unsigned long long rdtsc(void)
{
  unsigned hi, lo;
  __asm__ __volatile__ ("rdtsc" : "=a"(lo), "=d"(hi));
  return ( (unsigned long long)lo)|( ((unsigned long long)hi)<<32 );
}

static void set_affinity(unsigned long cpu)
{
  int err;
  cpu_set_t cpumask;

  CPU_ZERO(&cpumask);
  CPU_SET(cpu, &cpumask);
  err = sched_setaffinity(syscall(SYS_gettid),
      sizeof(cpu_set_t), &cpumask);

  if (err == -1)
    printf("set_affinity: %s\n", strerror(errno));
}

static void *_thread(void *data)
{
  /* thread_id, cpu id */
  unsigned long cpu = (unsigned long) data;
  long retval;

  set_affinity(cpu);

  int count = 0;
  unsigned long long tottime, time;
  /* calculate the clock skew for each core */
  while(count<ITERATIONS) {
    // Wait till we may fire away
    int rc=pthread_barrier_wait(&barr);
    if(rc != 0 && rc != PTHREAD_BARRIER_SERIAL_THREAD)
    {
      printf("Could not wait on barrier\n");
      exit(-1);
    }
    time = rdtsc();
    tottime += time;
    count++;
  }
  printf("time= %lld clock ticks, cpu id= %ld\n", tottime/ITERATIONS, cpu);
  pthread_exit(NULL);
}

int main(int argc, char *argv[])
{
  unsigned long nr_cpus;
  pthread_t *readers;
  unsigned long i;
  int signal;

  nr_cpus = sysconf(_SC_NPROCESSORS_ONLN);

  // Barrier initialization
  if(pthread_barrier_init(&barr, NULL, nr_cpus))
  {
    printf("Could not create a barrier\n");
    return -1;
  }

  readers = calloc(nr_cpus, sizeof(pthread_t));
  if (!readers)
    printf("Out of memory!\n");

  for (i = 0; i < nr_cpus; i++) {
    int err;

    err = pthread_create(&readers[i], NULL, _thread,
        (void *) i);
    if (err)
      printf("Could not pthread_create(): %s!\n",
          strerror(errno));
  }

  for (i = 0; i < nr_cpus; i++)
    pthread_join(readers[i], NULL);

  return 0;
}
