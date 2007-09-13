#include "runtime.h"
#include <sys/types.h>
#include <unistd.h>
#include <errno.h>
#include <stdlib.h>
#include "thread.h"
#include "option.h"
#include <signal.h>

#include <stdio.h>
int threadcount;
pthread_mutex_t gclock;
pthread_mutex_t gclistlock;
pthread_cond_t gccond;
pthread_mutex_t objlock;
pthread_cond_t objcond;
pthread_key_t threadlocks;

void threadexit() {
#ifdef THREADS
  struct ___Object___ *ll=pthread_getspecific(threadlocks);
  while(ll!=NULL) {
    struct ___Object___ *llnext=ll->___nextlockobject___;    
    ll->___nextlockobject___=NULL;
    ll->___prevlockobject___=NULL;
    ll->lockcount=0;
    ll->tid=0; //unlock it
    ll=llnext;
  }
  pthread_mutex_lock(&objlock);//wake everyone up
  pthread_cond_broadcast(&objcond);
  pthread_mutex_unlock(&objlock);
#endif
  pthread_mutex_lock(&gclistlock);
  threadcount--;
  pthread_cond_signal(&gccond);
  pthread_mutex_unlock(&gclistlock);
  pthread_exit(NULL);
}

void threadhandler(int sig, siginfo_t *info, void *uap) {
#ifdef DEBUG
  printf("sig=%d\n",sig);
  printf("signal\n");
#endif
  threadexit();
}

void initializethreads() {
  struct sigaction sig;
  threadcount=1;
  pthread_mutex_init(&gclock, NULL);
  pthread_mutex_init(&gclistlock, NULL);
  pthread_cond_init(&gccond, NULL);
  pthread_mutex_init(&objlock,NULL);
  pthread_cond_init(&objcond,NULL);
  pthread_key_create(&threadlocks, NULL);
  processOptions();
  initializeexithandler();

  sig.sa_sigaction=&threadhandler;
  sig.sa_flags=SA_SIGINFO;
  sigemptyset(&sig.sa_mask);

  /* Catch bus errors, segmentation faults, and floating point exceptions*/
  sigaction(SIGBUS,&sig,0);
  sigaction(SIGSEGV,&sig,0);
  sigaction(SIGFPE,&sig,0);
}

#ifdef THREADS
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
#endif

void CALL11(___Thread______sleep____J, long long ___millis___, long long ___millis___) {
#ifdef THREADS
#ifdef PRECISE_GC
  struct listitem *tmp=stopforgc((struct garbagelist *)___params___);
#endif
#endif
  usleep(___millis___);  
#ifdef THREADS
#ifdef PRECISE_GC
  restartaftergc(tmp);
#endif
#endif
}

#ifdef THREADS
void CALL01(___Thread______nativeCreate____, struct ___Thread___ * ___this___) {
  pthread_t thread;
  int retval;
  pthread_attr_t nattr;

  pthread_mutex_lock(&gclistlock);
  threadcount++;
  pthread_mutex_unlock(&gclistlock);
  pthread_attr_init(&nattr);
  pthread_attr_setdetachstate(&nattr, PTHREAD_CREATE_DETACHED);
  
  do {
    retval=pthread_create(&thread, &nattr, (void * (*)(void *)) &initthread, VAR(___this___));
    if (retval!=0)
      usleep(1);
  } while(retval!=0);

  pthread_attr_destroy(&nattr);
}
#endif

#ifdef DSTM
void CALL12(___Thread______start____I, int ___mid___, struct ___Thread___ * ___this___, int ___mid___) {
  startRemoteThread((unsigned int *)VAR(___this___), ___mid___);
}
#endif
