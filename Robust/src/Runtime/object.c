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
__thread int mythreadid;
#else

#endif

#ifdef D___Object______hashCode____
int CALL01(___Object______hashCode____, struct ___Object___ * ___this___) {
  return VAR(___this___)->hashcode;
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
  int self=(int)(long)pthread_getspecific(macthreadid);
#else
  struct lockvector *lptr=&lvector;
  int self=mythreadid;
#endif
  struct lockpair *lpair=&lptr->locks[lptr->index++];
  lpair->object=VAR(___this___);

  if (self==VAR(___this___)->tid) {
    lpair->islastlock=0;
  } else {
    lpair->islastlock=1;
    while(1) {
      if (VAR(___this___)->tid==0) {
	if (CAS32(&VAR(___this___)->tid, 0, self)==0) {
	  return;
	}
      }
      {
#ifdef PRECISE_GC
	if (unlikely(needtocollect))
	  checkcollect((struct garbagelist *)___params___);
#endif
      }
    }
  }
}
#endif


#ifdef D___Object______notify____
void CALL01(___Object______notify____, struct ___Object___ * ___this___) {
  VAR(___this___)->notifycount++;
}
#endif

#ifdef D___Object______notifyAll____
void CALL01(___Object______notifyAll____, struct ___Object___ * ___this___) {
  VAR(___this___)->notifycount++;
}
#endif

#ifdef D___Object______wait____
void CALL01(___Object______wait____, struct ___Object___ * ___this___) {
#ifdef MAC
  int self=(int)(long)pthread_getspecific(macthreadid);
#else
  int self=mythreadid;
#endif
  int notifycount=VAR(___this___)->notifycount;
  BARRIER();
  VAR(___this___)->tid=0;
  BARRIER();
  
  while(notifycount==VAR(___this___)->notifycount) {
#ifdef PRECISE_GC
    if (unlikely(needtocollect))
      checkcollect((struct garbagelist *)___params___);
#endif
  }

  while(1) {
    if (VAR(___this___)->tid==0) {
      if (CAS32(&VAR(___this___)->tid, 0, self)==0) {
	BARRIER();
	return;
      }
    }
#ifdef PRECISE_GC
    if (unlikely(needtocollect))
      checkcollect((struct garbagelist *)___params___);
#endif
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
  
  if (lpair->islastlock) {
    MBARRIER();
    lpair->object->tid=0;
  }
}
#endif
#endif
