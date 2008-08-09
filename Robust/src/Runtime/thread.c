#include "runtime.h"
#include <sys/types.h>
#include <unistd.h>
#include <errno.h>
#include <stdlib.h>
#include "thread.h"
#include "option.h"
#include <signal.h>
#include <DSTM/interface/dstm.h>
#include <DSTM/interface/llookup.h>

#ifndef RAW
#include <stdio.h>
#endif
int threadcount;
pthread_mutex_t gclock;
pthread_mutex_t gclistlock;
pthread_cond_t gccond;
pthread_mutex_t objlock;
pthread_cond_t objcond;

pthread_mutex_t joinlock;
pthread_cond_t joincond;
pthread_key_t threadlocks;
pthread_mutex_t threadnotifylock;
pthread_cond_t threadnotifycond;
pthread_key_t oidval;

void threadexit() {
  objheader_t* ptr;
  void *value;
  transrecord_t * trans;
  unsigned int oidvalue;

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
#ifdef DSTM
  /* Add transaction to check if thread finished for join operation */
  value = pthread_getspecific(oidval);
  oidvalue = *((unsigned int *)value);
  goto transstart;
transstart:
  {
    transrecord_t * trans = transStart();
    ptr = transRead(trans, oidvalue);
    struct ___Thread___ *p = (struct ___Thread___ *) ptr;
    p->___threadDone___ = 1;
    *((unsigned int *)&((struct ___Object___ *) p)->___localcopy___) |=DIRTY;
    if(transCommit(trans) != 0) {
      goto transstart;
    }
  }
#endif	
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
  pthread_mutex_init(&joinlock,NULL);
  pthread_cond_init(&joincond,NULL);
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
  signal(SIGPIPE, SIG_IGN);
}

#ifdef THREADS
void initthread(struct ___Thread___ * ___this___) {
#ifdef PRECISE_GC
  int p[]={1, (int) NULL, (int) ___this___};
  ___Thread______staticStart____L___Thread___((struct ___Thread______staticStart____L___Thread____params *)p);
  ___this___=(struct ___Thread___ *) p[2];
#else
  ___Thread______staticStart____L___Thread___(___this___);
#endif
  ___this___->___finished___=1;
  pthread_mutex_lock(&joinlock);
  pthread_cond_signal(&joincond);
  pthread_mutex_unlock(&joinlock);

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

#if defined(DSTM)|| defined(THREADS)
void CALL00(___Thread______yield____) {
  pthread_yield();
}
#endif

#ifdef DSTM
/* Add thread join capability */
void CALL01(___Thread______join____, struct ___Thread___ * ___this___) {
  unsigned int *oidarray;
  unsigned short *versionarray, version;
  transrecord_t *trans;
  objheader_t *ptr;
  /* Add transaction to check if thread finished for join operation */
  goto transstart;
transstart:
  trans = transStart();
  ptr = transRead(trans, (unsigned int) VAR(___this___));
  struct ___Thread___ *p = (struct ___Thread___ *) ptr;
#ifdef THREADJOINDEBUG
  printf("Start join process for Oid = %x\n", (unsigned int) VAR(___this___));
#endif
  if(p->___threadDone___ == 1) {
#ifdef THREADJOINDEBUG
    printf("Thread oid = %x is done\n", (unsigned int) VAR(___this___));
#endif
	  transAbort(trans);
	  return;
  } else {

	  version = (ptr-1)->version;
	  if((oidarray = calloc(1, sizeof(unsigned int))) == NULL) {
		  printf("Calloc error %s, %d\n", __FILE__, __LINE__);
		  return;
	  }

	  oidarray[0] = (unsigned int) VAR(___this___);

	  if((versionarray = calloc(1, sizeof(unsigned short))) == NULL) {
		  printf("Calloc error %s, %d\n", __FILE__, __LINE__);
		  free(oidarray);
		  return;
	  }
	  versionarray[0] = version;
	  /* Request Notification */
#ifdef PRECISE_GC
	  struct listitem *tmp=stopforgc((struct garbagelist *)___params___);
#endif
	  reqNotify(oidarray, versionarray, 1); 
#ifdef PRECISE_GC
	  restartaftergc(tmp);
#endif
	  free(oidarray);
	  free(versionarray);
	  transAbort(trans);
	  goto transstart;
  }
  return;
}
#endif

#ifdef THREADS
void CALL01(___Thread______nativeJoin____, struct ___Thread___ * ___this___) {
  pthread_mutex_lock(&joinlock);
  while(!VAR(___this___)->___finished___)
    pthread_cond_wait(&joincond, &joinlock);
  pthread_mutex_unlock(&joinlock);  
}

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
  /* This next statement will likely not work on many machines */

  pthread_attr_destroy(&nattr);
}
#endif

#ifdef DSTM
void CALL12(___Thread______start____I, int ___mid___, struct ___Thread___ * ___this___, int ___mid___) {
  startRemoteThread((unsigned int)VAR(___this___), ___mid___);
}
#endif

#ifdef DSTM
void globalDestructor(void *value) {
	free(value);
	pthread_setspecific(oidval, NULL);
}

void initDSMthread(int *ptr) {
  objheader_t *tmp;	
  transrecord_t * trans;
  void *threadData;
  int oid=ptr[0];
  int type=ptr[1];
  free(ptr);
#ifdef PRECISE_GC
  int p[]={1, 0 /* NULL */, oid};
  ((void (*)(void *))virtualtable[type*MAXCOUNT+RUNMETHOD])(p);
#else
  ((void (*)(void *))virtualtable[type*MAXCOUNT+RUNMETHOD])(oid);
#endif
  threadData = calloc(1, sizeof(unsigned int));
  *((unsigned int *) threadData) = oid;
  pthread_setspecific(oidval, threadData);
  pthread_mutex_lock(&gclistlock);
  threadcount--;
  pthread_cond_signal(&gccond);
  pthread_mutex_unlock(&gclistlock);
  /* Add transaction to check if thread finished for join operation */
  goto transstart;
transstart:
  {
    transrecord_t * trans = transStart();
    tmp  = transRead(trans, (unsigned int) oid);
    ((struct ___Thread___ *)tmp)->___threadDone___ = 1;
    *((unsigned int *)&((struct ___Object___ *) tmp)->___localcopy___) |=DIRTY;
    if(transCommit(trans)!= 0) {
      goto transstart;
    }
  }
  pthread_exit(NULL);
}

void startDSMthread(int oid, int objType) {
	pthread_t thread;
	int retval;
	pthread_attr_t nattr;

	pthread_mutex_lock(&gclistlock);
	threadcount++;
	pthread_mutex_unlock(&gclistlock);
  pthread_attr_init(&nattr);
  pthread_attr_setdetachstate(&nattr, PTHREAD_CREATE_DETACHED);
  int * ptr=malloc(sizeof(int)*2);
  ptr[0]=oid;
  ptr[1]=objType;
  pthread_key_create(&oidval, globalDestructor);
  do {
    retval=pthread_create(&thread, &nattr, (void * (*)(void *)) &initDSMthread,  ptr);
    if (retval!=0)
      usleep(1);
  } while(retval!=0);

  pthread_attr_destroy(&nattr);
}

#endif
