#include <stdlib.h>
#include <stdint.h>
#include <stdio.h>
#include <pthread.h>
#include <sys/time.h>


#define THREADS 2
#define ITERATIONS 1000
pthread_mutex_t LOCK;
//pthread_mutex_t START;
//Barrier variable
pthread_barrier_t barr;
int tottime = 0, time1, time2;

int64_t timeInMS () //time in microsec
{
  struct timeval t;

  gettimeofday(&t, NULL);
  return (
      (int64_t)t.tv_sec * 1000000 +
      (int64_t)t.tv_usec
      );
}

void * thd1 (
    void * unused
    ) {
  // Wait till we may fire away
  //pthread_mutex_lock(&START);
  //pthread_mutex_unlock(&START);

  int count = 0;
  while(count<ITERATIONS) {
    // Wait till we may fire away
    int rc=pthread_barrier_wait(&barr);
    if(rc != 0 && rc != PTHREAD_BARRIER_SERIAL_THREAD)
    {
      printf("Could not wait on barrier\n");
      exit(-1);
    }
    pthread_mutex_lock(&LOCK);
    // Delay 
    sleep(2);
    time1 = timeInMS();

    pthread_mutex_unlock(&LOCK);
    pthread_barrier_wait(&barr);
    tottime += (time2-time1);
    count++;
  }
}

void * thd2 (
    void *unused
    ) {
  // Wait till we may fire away
  //pthread_mutex_lock(&START);
  //pthread_mutex_unlock(&START);

  int count = 0;
  while(count<ITERATIONS) {
    // Wait till we may fire away
    int rc=pthread_barrier_wait(&barr);
    if(rc != 0 && rc != PTHREAD_BARRIER_SERIAL_THREAD)
    {
      printf("Could not wait on barrier\n");
      exit(-1);
    }
    // Delay 
    sleep(1);
    pthread_mutex_lock(&LOCK);
    time2 = timeInMS();

    pthread_mutex_unlock(&LOCK);
    pthread_barrier_wait(&barr);
    count++;
  }
}


int main (
    int argc,
    char ** argv
    ) {
  int64_t start;
  pthread_t t1;
  pthread_t t2;
  int64_t myTime;

  // Barrier initialization
  if(pthread_barrier_init(&barr, NULL, THREADS))
  {
    printf("Could not create a barrier\n");
    return -1;
  }

  pthread_mutex_init(&LOCK, NULL);   
  //pthread_mutex_init(&START, NULL);   

  //pthread_mutex_lock(&START);
  pthread_create(&t1, NULL, thd1, NULL);
  pthread_create(&t2, NULL, thd2, NULL);

  pthread_join(t1, NULL);
  pthread_join(t2, NULL);

  //pthread_mutex_unlock(&START);

  int locklatency = tottime/ITERATIONS;
  printf("locklatency was %u micro secs\n", locklatency);
  return 0;
}
