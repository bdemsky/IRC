#include "runtime.h"
#include <sys/types.h>
#include <unistd.h>
#include <errno.h>
#include <stdlib.h>
#include "thread.h"


#include <stdio.h>
pthread_mutex_t threadtable;
int threadcount;
pthread_mutex_t gclock;
pthread_mutex_t gclistlock;
pthread_cond_t gccond;

void initializethreads() {
  pthread_mutex_init(&threadtable,NULL);
  threadcount=1;
  pthread_mutex_init(&gclock, NULL);
  pthread_mutex_init(&gclistlock, NULL);
  pthread_cond_init(&gccond, NULL);
}

void initthread(struct ___Thread___ * ___this___) {
#ifdef PRECISE_GC
  struct ___Thread______staticStart____L___Thread____params p={1, NULL, ___this___};
  ___Thread______staticStart____L___Thread___(&p);
#else
  ___Thread______staticStart____L___Thread___(___this___);
#endif
  pthread_mutex_lock(&threadtable);
  threadcount--;
  pthread_mutex_unlock(&threadtable);
  pthread_mutex_lock(&gclistlock);
  pthread_cond_signal(&gccond);
  pthread_mutex_unlock(&gclistlock);
}

void CALL01(___Thread______nativeCreate____, struct ___Thread___ * ___this___) {
  pthread_t thread;
  pthread_mutex_lock(&threadtable);
  threadcount++;
  pthread_mutex_unlock(&threadtable);
  pthread_create(&thread, NULL,(void * (*)(void *)) &initthread, VAR(___this___));
}
