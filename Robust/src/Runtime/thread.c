#include "runtime.h"
#include <sys/types.h>
#include <unistd.h>
#include <errno.h>
#include <stdlib.h>
#include "thread.h"


#include <stdio.h>
int threadcount;
pthread_mutex_t gclock;
pthread_mutex_t gclistlock;
pthread_cond_t gccond;
pthread_mutex_t objlock;
pthread_cond_t objcond;

void initializethreads() {
  threadcount=1;
  pthread_mutex_init(&gclock, NULL);
  pthread_mutex_init(&gclistlock, NULL);
  pthread_cond_init(&gccond, NULL);
  pthread_mutex_init(&objlock,NULL);
  pthread_cond_init(&objcond,NULL);
}

void initthread(struct ___Thread___ * ___this___) {
#ifdef PRECISE_GC
  struct ___Thread______staticStart____L___Thread____params p={1, NULL, ___this___};
  ___Thread______staticStart____L___Thread___(&p);
#else
  ___Thread______staticStart____L___Thread___(___this___);
#endif
  pthread_mutex_lock(&gclistlock);
  threadcount--;
  pthread_cond_signal(&gccond);
  pthread_mutex_unlock(&gclistlock);
}

void CALL01(___Thread______nativeCreate____, struct ___Thread___ * ___this___) {
  pthread_t thread;
  pthread_mutex_lock(&gclistlock);
  threadcount++;
  pthread_mutex_unlock(&gclistlock);
  pthread_create(&thread, NULL,(void * (*)(void *)) &initthread, VAR(___this___));
}
