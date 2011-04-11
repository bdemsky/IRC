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
#ifndef MULTICORE
#include "mlp_lock.h"
#endif

#ifndef MAC
__thread struct lockvector lvector;
#endif

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

#ifdef D___Object______getType____
int CALL01(___Object______getType____, struct ___Object___ * ___this___) {
  return ((int *)VAR(___this___))[0];
}
#endif

#ifdef THREADS
#ifdef D___Object______MonitorEnter____
void CALL01(___Object______MonitorEnter____, struct ___Object___ * ___this___) {
#ifdef MAC
  struct lockvector *lptr=(struct lockvector *)pthread_getspecific(threadlocks);
#else
  struct lockvector *lptr=&lvector;
#endif
  struct lockpair *lpair=&lptr->locks[lptr->index];
  pthread_t self=pthread_self();
  lpair->object=VAR(___this___);
  lptr->index++;

  
  if (self==VAR(___this___)->tid) {
    lpair->islastlock=0;
  } else {
    lpair->islastlock=1;
    while(1) {
      if (VAR(___this___)->lockcount==0) {
	if (CAS32(&VAR(___this___)->lockcount, 0, 1)==0) {
	  VAR(___this___)->tid=self;
	  return;
	}
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
}
#endif


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

  VAR(___this___)->tid=0;
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
    if (VAR(___this___)->lockcount==0) {
      if (CAS32(&VAR(___this___)->lockcount, 0, 1)==0) {
	VAR(___this___)->tid=self;
	BARRIER();
	return;
      }
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

#ifdef D___Object______MonitorExit____
void CALL01(___Object______MonitorExit____, struct ___Object___ * ___this___) {
#ifdef MAC
  struct lockvector *lptr=(struct lockvector *)pthread_getspecific(threadlocks);
#else
  struct lockvector *lptr=&lvector;
#endif
  struct lockpair *lpair=&lptr->locks[--lptr->index];
  pthread_t self=pthread_self();
  
  if (lpair->islastlock) {
    lpair->object->tid=0;
    BARRIER();
    lpair->object->lockcount=0;
  }
}
#endif
#endif
