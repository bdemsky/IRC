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
#include "mlp_lock.h"

#ifdef D___Object______nativehashCode____
int CALL01(___Object______nativehashCode____, struct ___Object___ * ___this___) {
  return (int)((INTPTR) VAR(___this___));
}
#endif

#ifdef D___Object______hashCode____
int CALL01(___Object______hashCode____, struct ___Object___ * ___this___) {
  if (!VAR(___this___)->___cachedHash___) {
    VAR(___this___)->___cachedHash___=1;
    VAR(___this___)->___cachedCode___=(int)((INTPTR)VAR(___this___));
  }
  return VAR(___this___)->___cachedCode___;
}
#endif

int CALL01(___Object______getType____, struct ___Object___ * ___this___) {
  return ((int *)VAR(___this___))[0];
}

#ifdef THREADS
int CALL01(___Object______MonitorEnter____, struct ___Object___ * ___this___) {
#ifndef NOLOCK
  pthread_t self=pthread_self();
  if (self==VAR(___this___)->tid) {
    atomic_inc(&VAR(___this___)->lockcount);
  } else {
    while(1) {
      if (CAS32(&VAR(___this___)->lockcount, 0, 1)==0) {
	VAR(___this___)->___prevlockobject___=NULL;
	VAR(___this___)->___nextlockobject___=(struct ___Object___ *)pthread_getspecific(threadlocks);
	if (VAR(___this___)->___nextlockobject___!=NULL)
	  VAR(___this___)->___nextlockobject___->___prevlockobject___=VAR(___this___);
	pthread_setspecific(threadlocks, VAR(___this___));
	VAR(___this___)->lockcount=1;
	VAR(___this___)->tid=self;
	pthread_mutex_unlock(&objlock);
	BARRIER();
	return;
      }
      {
#ifdef PRECISE_GC
	stopforgc((struct garbagelist *)___params___);
#endif
#ifdef PRECISE_GC
	restartaftergc();
#endif
      }
    }
  }
#endif
}

#ifdef D___Object______notify____
void CALL01(___Object______notify____, struct ___Object___ * ___this___) {
}
#endif
#ifdef D___Object______notifyAll____
void CALL01(___Object______notifyAll____, struct ___Object___ * ___this___) {
}
#endif
#ifdef D___Object______wait____
void CALL01(___Object______wait____, struct ___Object___ * ___this___) {
  pthread_t self=pthread_self();
  int lockcount=VAR(___this___)->lockcount;
  //release lock
  if (VAR(___this___)->___prevlockobject___==NULL) {
    pthread_setspecific(threadlocks, VAR(___this___)->___nextlockobject___);
  } else
    VAR(___this___)->___prevlockobject___->___nextlockobject___=VAR(___this___)->___nextlockobject___;
  if (VAR(___this___)->___nextlockobject___!=NULL)
    VAR(___this___)->___nextlockobject___->___prevlockobject___=VAR(___this___)->___prevlockobject___;
  VAR(___this___)->___nextlockobject___=NULL;
  VAR(___this___)->___prevlockobject___=NULL;
  VAR(___this___)->lockentry=NULL;
  VAR(___this___)->tid=0;
  //release lock
  BARRIER();
  VAR(___this___)->lockcount=0;
  
  //allow gc
#ifdef PRECISE_GC
  stopforgc((struct garbagelist *)___params___);
#endif
  sched_yield();
#ifdef PRECISE_GC
  restartaftergc();
#endif

  while(1) {
    if (CAS32(&VAR(___this___)->lockcount, 0, lockcount)==0) {
      VAR(___this___)->___prevlockobject___=NULL;
      VAR(___this___)->___nextlockobject___=(struct ___Object___ *)pthread_getspecific(threadlocks);
      if (VAR(___this___)->___nextlockobject___!=NULL)
	VAR(___this___)->___nextlockobject___->___prevlockobject___=VAR(___this___);
      pthread_setspecific(threadlocks, VAR(___this___));
      VAR(___this___)->lockcount=1;
      VAR(___this___)->tid=self;
      pthread_mutex_unlock(&objlock);
      BARRIER();
      return;
    }
    {
#ifdef PRECISE_GC
      stopforgc((struct garbagelist *)___params___);
#endif
#ifdef PRECISE_GC
      restartaftergc();
#endif
    }
  }
}
#endif

int CALL01(___Object______MonitorExit____, struct ___Object___ * ___this___) {
#ifndef NOLOCK
  pthread_t self=pthread_self();
  if (self==VAR(___this___)->tid) {
    //release one lock...
    BARRIER();
    if (VAR(___this___)->lockcount==1) {
      if (VAR(___this___)->___prevlockobject___==NULL) {
	pthread_setspecific(threadlocks, VAR(___this___)->___nextlockobject___);
      } else
	VAR(___this___)->___prevlockobject___->___nextlockobject___=VAR(___this___)->___nextlockobject___;
      if (VAR(___this___)->___nextlockobject___!=NULL)
	VAR(___this___)->___nextlockobject___->___prevlockobject___=VAR(___this___)->___prevlockobject___;
      VAR(___this___)->___nextlockobject___=NULL;
      VAR(___this___)->___prevlockobject___=NULL;
      VAR(___this___)->lockentry=NULL;
      VAR(___this___)->tid=0;
    }
    atomic_dec(&VAR(___this___)->lockcount);
  } else {
#ifdef MULTICORE
    BAMBOO_EXIT(0xf201);
#else
    printf("ERROR...UNLOCKING LOCK WE DON'T HAVE\n");
    exit(-1);
#endif
  }
#endif
}
#endif
