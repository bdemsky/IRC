#include "object.h"
#ifdef MULTICORE
#include "runtime_arch.h"
#else
#include "stdio.h"
#endif
#include "stdlib.h"

#ifdef THREADS
#include "thread.h"
#endif

int CALL01(___Object______nativehashCode____, struct ___Object___ * ___this___) {
  return (int)((INTPTR) VAR(___this___));
}

int CALL01(___Object______getType____, struct ___Object___ * ___this___) {
  return ((int *)VAR(___this___))[0];
}

#ifdef THREADS
int CALL01(___Object______MonitorEnter____, struct ___Object___ * ___this___) {
  pthread_t self=pthread_self();
  if (self==VAR(___this___)->tid) {
    VAR(___this___)->lockcount++;
  } else {
#ifdef PRECISE_GC
    struct listitem *tmp=stopforgc((struct garbagelist *)___params___);
#endif
    pthread_mutex_lock(&objlock);
#ifdef PRECISE_GC
    restartaftergc(tmp);
#endif
    while(1) {
      if (VAR(___this___)->tid==0) {
	VAR(___this___)->___prevlockobject___=NULL;
	VAR(___this___)->___nextlockobject___=(struct ___Object___ *)pthread_getspecific(threadlocks);
	if (VAR(___this___)->___nextlockobject___!=NULL)
	  VAR(___this___)->___nextlockobject___->___prevlockobject___=VAR(___this___);
	pthread_setspecific(threadlocks, VAR(___this___));
	VAR(___this___)->lockcount=1;
	VAR(___this___)->tid=self;
	pthread_mutex_unlock(&objlock);
	break;
      }
      {
#ifdef PRECISE_GC
	struct listitem *tmp=stopforgc((struct garbagelist *)___params___);
#endif
	pthread_cond_wait(&objcond, &objlock);
#ifdef PRECISE_GC
	restartaftergc(tmp);
#endif
      }
    }
  }
}

int CALL01(___Object______MonitorExit____, struct ___Object___ * ___this___) {
  pthread_t self=pthread_self();
  if (self==VAR(___this___)->tid) {
    VAR(___this___)->lockcount--;
    if (VAR(___this___)->lockcount==0) {
      if (VAR(___this___)->___prevlockobject___==NULL) {
	pthread_setspecific(threadlocks, VAR(___this___)->___nextlockobject___);
      } else
	VAR(___this___)->___prevlockobject___->___nextlockobject___=VAR(___this___)->___nextlockobject___;
      if (VAR(___this___)->___nextlockobject___!=NULL)
	VAR(___this___)->___nextlockobject___->___prevlockobject___=VAR(___this___)->___prevlockobject___;
      VAR(___this___)->lockentry=NULL;
      VAR(___this___)->tid=0;
    }
    pthread_mutex_lock(&objlock);
    pthread_cond_broadcast(&objcond);
    pthread_mutex_unlock(&objlock);
  } else {
#ifdef MULTICORE
    BAMBOO_EXIT(-1);
#else
    printf("ERROR...UNLOCKING LOCK WE DON'T HAVE\n");
    exit(-1);
#endif
  }
}
#endif
