#include <stdlib.h>
#include <stdint.h>
#include <stdio.h>
#include <pthread.h>
#include <sys/time.h>

#define THREADS 8
#define ITERATIONS 100000

pthread_barrier_t barr;

static __inline__ unsigned long long rdtsc(void)
{
  unsigned hi, lo;
  __asm__ __volatile__ ("rdtsc" : "=a"(lo), "=d"(hi));
  return ( (unsigned long long)lo)|( ((unsigned long long)hi)<<32 );
}

static __inline__ int64_t timeInMS () //time in microsec
{
  struct timeval t;

  gettimeofday(&t, NULL);
  return (
      (int64_t)t.tv_sec * 1000000 +
      (int64_t)t.tv_usec
      );
}

void * thd (
    void * unused
    ) {
  int count = 0;
  unsigned long long tottime, time;
  while(count<ITERATIONS) {
    // Wait till we may fire away
    int rc=pthread_barrier_wait(&barr);
    if(rc != 0 && rc != PTHREAD_BARRIER_SERIAL_THREAD)
    {
      printf("Could not wait on barrier\n");
      exit(-1);
    }
    //printf("t= %lld\n", rdtsc());
    //time = timeInMS();
    time = rdtsc();
    tottime += time;
    count++;
  }
  printf("time= %lld micro secs\n", tottime/ITERATIONS);
}

int main (
    int argc,
    char ** argv
    ) {
  int64_t start;
  pthread_t t1, t2, t3, t4, t5, t6, t7, t8;
  int64_t myTime;

  // Barrier initialization
  if(pthread_barrier_init(&barr, NULL, THREADS))
  {
    printf("Could not create a barrier\n");
    return -1;
  }

  pthread_create(&t1, NULL, thd, NULL);
  pthread_create(&t2, NULL, thd, NULL);
  pthread_create(&t3, NULL, thd, NULL);
  pthread_create(&t4, NULL, thd, NULL);
  pthread_create(&t5, NULL, thd, NULL);
  pthread_create(&t6, NULL, thd, NULL);
  pthread_create(&t7, NULL, thd, NULL);
  pthread_create(&t8, NULL, thd, NULL);

  pthread_join(t1, NULL);
  pthread_join(t2, NULL);
  pthread_join(t3, NULL);
  pthread_join(t4, NULL);
  pthread_join(t5, NULL);
  pthread_join(t6, NULL);
  pthread_join(t7, NULL);
  pthread_join(t8, NULL);

  return 0;
}
