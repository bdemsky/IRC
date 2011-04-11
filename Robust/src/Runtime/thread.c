#include "runtime.h"
#include <sys/types.h>
#include <sys/mman.h>
#include <unistd.h>
#include <errno.h>
#include <stdlib.h>
#include "thread.h"
#include "option.h"
#include <signal.h>
#include "methodheaders.h"
#ifndef MULTICORE
#include "mlp_lock.h"
#endif

#ifdef DSTM
#ifdef RECOVERY
#include <DSTM/interface_recovery/dstm.h>
#include <DSTM/interface_recovery/llookup.h>
#else
#include <DSTM/interface/dstm.h>
#include <DSTM/interface/llookup.h>
#endif
#endif

#ifndef RAW
#include <stdio.h>
#endif
#ifdef STM
#include "tm.h"
#endif
#include <execinfo.h>
#ifdef EVENTMONITOR
#include "monitor.h"
#endif


int threadcount;
pthread_mutex_t gclock;
pthread_mutex_t gclistlock;
pthread_cond_t gccond;
pthread_mutex_t objlock;
pthread_cond_t objcond;

pthread_mutex_t atomiclock;

pthread_mutex_t joinlock;
pthread_cond_t joincond;
pthread_key_t threadlocks;
pthread_key_t macthreadid;
pthread_mutex_t threadnotifylock;
pthread_cond_t threadnotifycond;
pthread_key_t oidval;

#if defined(THREADS) || defined(DSTM) || defined(STM)||defined(MLP)
#ifndef MAC
extern __thread struct listitem litem;
#else
pthread_key_t litemkey;
#endif
extern struct listitem * list;
#endif

void threadexit() {
#ifdef DSTM
  objheader_t* ptr;
  unsigned int oidvalue;
#endif
  void *value;

#ifdef THREADS
#ifdef MAC
  struct lockvector *lptr=(struct lockvector *) pthread_getspecific(threadlocks);
#else
  struct lockvector *lptr=&lvector;
#endif
  for(lptr->index--;lptr->index>=0;lptr->index--) {
    if (lptr->locks[lptr->index].islastlock) {
      struct ___Object___ *ll=lptr->locks[lptr->index].object;
      ll->tid=0;
    }
  }

  pthread_mutex_lock(&objlock); //wake everyone up
  pthread_cond_broadcast(&objcond);
  pthread_mutex_unlock(&objlock);
#endif
  pthread_mutex_lock(&gclistlock);

#ifndef MAC
  if (litem.prev==NULL) {
    list=litem.next;
  } else {
    litem.prev->next=litem.next;
  }
  if (litem.next!=NULL) {
    litem.next->prev=litem.prev;
  }
#else
  {
    struct listitem *litem=pthread_getspecific(litemkey);
    if (litem->prev==NULL) {
      list=litem->next;
    } else {
      litem->prev->next=litem->next;
    }
    if (litem->next!=NULL) {
      litem->next->prev=litem->prev;
    }
  }
#endif
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
    transStart();
    ptr = transRead(oidvalue);
    struct ___Thread___ *p = (struct ___Thread___ *) ptr;
    p->___threadDone___ = 1;
    *((unsigned int *)&((struct ___Object___ *) p)->___localcopy___) |=DIRTY;
    if(transCommit() != 0) {
      goto transstart;
    }
  }
#endif
  pthread_exit(NULL);
}

void threadhandler(int sig, struct sigcontext ctx) {
  void *buffer[100];
  char **strings;
  int nptrs,j;

  printf("We just took sig=%d\n",sig);
  printf("signal\n");
  printf("To get stack trace, set breakpoint in threadhandler in gdb\n");
  nptrs = backtrace(buffer, 100);
#ifdef BIT64
  buffer[1]=(void *)ctx.rip;
#else
  buffer[1]=(void *)ctx.eip;
#endif

  strings = backtrace_symbols(buffer, nptrs);
  if (strings == NULL) {
    perror("backtrace_symbols");
    exit(EXIT_FAILURE);
  }
  
  for (j = 0; j < nptrs; j++)
    printf("%s\n", strings[j]);
  
  threadexit();
}

#define downpage(x) ((void *)(((INTPTR)x)&~((INTPTR)4095)))

void initializethreads() {
  struct sigaction sig;
  threadcount=1;
#ifdef THREADS
  pthread_mutex_init(&atomiclock, NULL);
#endif
  pthread_mutex_init(&gclock, NULL);
  pthread_mutex_init(&gclistlock, NULL);
  pthread_cond_init(&gccond, NULL);
  pthread_mutex_init(&objlock,NULL);
  pthread_cond_init(&objcond,NULL);
  pthread_mutex_init(&joinlock,NULL);
  pthread_cond_init(&joincond,NULL);
#ifdef MAC
  pthread_key_create(&macthreadid, NULL);
  pthread_key_create(&threadlocks, NULL);
  pthread_key_create(&litem, NULL);
#endif
  processOptions();
  initializeexithandler();
#ifdef AFFINITY
  set_affinity();
#endif

  //deprecated use of sighandler, but apparently still works
#ifdef SANDBOX
  sig.sa_handler=(void *)errorhandler;
  abortenabled=0;
#else
  sig.sa_handler=(void *)threadhandler;
#endif
  sig.sa_flags=SA_RESTART;
  sigemptyset(&sig.sa_mask);

  /* Catch bus errors, segmentation faults, and floating point exceptions*/
  sigaction(SIGBUS,&sig,0);
  sigaction(SIGSEGV,&sig,0);
  sigaction(SIGFPE,&sig,0);
  signal(SIGPIPE, SIG_IGN);
#ifdef STM
  newobjs=calloc(1, sizeof(struct objlist));
  t_cache = objstrCreate(1048576);
  t_reserve=NULL;
  t_chashCreate(CHASH_SIZE, CLOADFACTOR);
#ifdef READSET
  rd_t_chashCreate(CHASH_SIZE, CLOADFACTOR);
#endif
#ifdef DELAYCOMP
  dc_t_chashCreate(CHASH_SIZE, CLOADFACTOR);
  ptrstack.count=0;
  primstack.count=0;
  branchstack.count=0;
#if defined(STMARRAY)&&!defined(DUALVIEW)
  arraystack.count=0;
#endif
  int a=mprotect((downpage(&ptrstack.buffer[1024])), 4096, PROT_NONE);
  if (a==-1)
    perror("ptrstack");
  a=mprotect(downpage(&primstack.array[MAXVALUES]), 4096, PROT_NONE);
  if (a==-1)
    perror("primstack");
  a=mprotect(downpage(&branchstack.array[MAXBRANCHES]), 4096, PROT_NONE);
  if (a==-1)
    perror("branchstack");
#if defined(STMARRAY)&&!defined(DUALVIEW)
  a=mprotect(downpage(&arraystack.index[MAXARRAY]), 4096, PROT_NONE);
  if (a==-1)
    perror("arraystack");
#endif
#endif
#ifdef STMSTATS
  trec=calloc(1, sizeof(threadrec_t));
  trec->blocked = 0;
  lockedobjs=calloc(1, sizeof(struct objlist));
  objlockscope = calloc(1, sizeof(objlockstate_t));
  pthread_mutex_init(&lockedobjstore, NULL);
  { 
    int i;
    for(i=0; i<TOTALNUMCLASSANDARRAY; i++) {
      typesCausingAbort[i].numaccess = 0;
      typesCausingAbort[i].numabort = 0;
      typesCausingAbort[i].numtrans = 0;
    }
  }
#endif
#endif
#ifdef MAC
  struct listitem *litem=malloc(sizeof(struct listitem));
  struct lockvector *lvector=malloc(sizeof(struct lockvector));
  litem->lockvector=lvector;
  lvector->index=0;
  pthread_setspecific(threadlocks, lvector);
  pthread_setspecific(macthreadid, 0);
  pthread_setspecific(litemkey, litem);
  litem->prev=NULL;
  litem->next=list;
  if(list!=NULL)
    list->prev=litem;
  list=litem;
#else
  //Add our litem to list of threads
  mythreadid=0;
  litem.prev=NULL;
  litem.next=list;
  litem.lvector=&lvector;
  lvector.index=0;
  if(list!=NULL)
    list->prev=&litem;
  list=&litem;
#endif
#ifdef EVENTMONITOR
  createmonitor();
#endif
}

#if defined(THREADS)||defined(STM)
int threadcounter=0;

void initthread(struct ___Thread___ * ___this___) {
#ifdef AFFINITY
  set_affinity();
#endif
#ifdef EVENTMONITOR
  createmonitor();
#endif
#ifdef SANDBOX
  struct sigaction sig;
  abortenabled=0;
  sig.sa_handler=(void *)errorhandler;
  sig.sa_flags=SA_RESTART;
  sigemptyset(&sig.sa_mask);

  /* Catch bus errors, segmentation faults, and floating point exceptions*/
  sigaction(SIGBUS,&sig,0);
  sigaction(SIGSEGV,&sig,0);
  sigaction(SIGFPE,&sig,0);
#endif
#ifdef PRECISE_GC
  INTPTR p[]={1, (INTPTR) NULL, (INTPTR) ___this___};
  //Add our litem to list of threads
#ifdef MAC
  struct listitem litem;
  pthread_setspecific(litemkey, &litem);
  struct lockvector lvector;
  pthread_setspecific(threadlocks, &lvector);
#endif
  litem.lvector=&lvector;
  lvector.index=0;
  litem.prev=NULL;
  pthread_mutex_lock(&gclistlock);
#ifdef MAC
  pthread_setspecific(macthreadid, ++threadcounter);
#else
  mythreadid=++threadcounter;
#endif
  litem.next=list;
  if(list!=NULL)
    list->prev=&litem;
  list=&litem;
  pthread_mutex_unlock(&gclistlock);
#ifdef THREADS
  ___Thread______staticStart____L___Thread___((struct ___Thread______staticStart____L___Thread____params *)p);
#else
  newobjs=calloc(1, sizeof(struct objlist));
#ifdef STMSTATS
  trec=calloc(1, sizeof(threadrec_t));
  trec->blocked = 0;
  lockedobjs=calloc(1, sizeof(struct objlist));
#endif
  t_cache = objstrCreate(1048576);
  t_reserve=NULL;
  t_chashCreate(CHASH_SIZE, CLOADFACTOR);
#ifdef READSET
  rd_t_chashCreate(CHASH_SIZE, CLOADFACTOR);
#endif
#ifdef DELAYCOMP
  dc_t_chashCreate(CHASH_SIZE, CLOADFACTOR);
  ptrstack.count=0;
  primstack.count=0;
  branchstack.count=0;
#if defined(STMARRAY)&&!defined(DUALVIEW)
  arraystack.count=0;
#endif
  int a=mprotect(downpage(&ptrstack.buffer[1024]), 4096, PROT_NONE);
  if (a==-1)
    perror("ptrstack");
  a=mprotect(downpage(&primstack.array[MAXVALUES]), 4096, PROT_NONE);
  if (a==-1)
    perror("primstack");
  a=mprotect(downpage(&branchstack.array[MAXBRANCHES]), 4096, PROT_NONE);
  if (a==-1)
    perror("branchstack");
#if defined(STMARRAY)&!defined(DUALVIEW)
  a=mprotect(downpage(&arraystack.index[MAXARRAY]), 4096, PROT_NONE);
  if (a==-1)
    perror("arraystack");
#endif
#endif
 ___Thread____NNR____staticStart____L___Thread___((struct ___Thread____NNR____staticStart____L___Thread____params *)p);
 objstrDelete(t_cache);
 objstrDelete(t_reserve);
 t_chashDelete();
 free(newobjs);
#ifdef STMSTATS
 free(lockedobjs);
#endif
#endif
  ___this___=(struct ___Thread___ *) p[2];
#else
  ___Thread______staticStart____L___Thread___(___this___);
#endif
  ___this___->___finished___=1;
  pthread_mutex_lock(&joinlock);
  pthread_cond_signal(&joincond);
  pthread_mutex_unlock(&joinlock);

  pthread_mutex_lock(&gclistlock);
  if (litem.prev==NULL) {
    list=litem.next;
  } else {
    litem.prev->next=litem.next;
  }
  if (litem.next!=NULL) {
    litem.next->prev=litem.prev;
  }
  threadcount--;
  pthread_cond_signal(&gccond);
  pthread_mutex_unlock(&gclistlock);
}
#endif

#ifdef D___Thread______sleep____J
void CALL11(___Thread______sleep____J, long long ___millis___, long long ___millis___) {
#if defined(THREADS)||defined(STM)
#ifdef PRECISE_GC
  stopforgc((struct garbagelist *)___params___);
#endif
#endif
  usleep(___millis___*1000);
#if defined(THREADS)||defined(STM)
#ifdef PRECISE_GC
  restartaftergc();
#endif
#endif
}
#endif

#ifdef D___Thread______yield____
void CALL00(___Thread______yield____) {
  pthread_yield();
}
#endif

#ifdef D___Thread______abort____
void CALL00(___Thread______abort____) {
#ifdef SANDBOX
  _longjmp(aborttrans,1);
#endif
}
#endif

#ifdef DSTM
#ifdef RECOVERY
// return if the machine is dead
#ifdef D___Thread______nativeGetStatus____I
int CALL12(___Thread______nativeGetStatus____I, int ___mid___, struct ___Thread___ * ___this___, int ___mid___) {
  return getStatus(___mid___);
}
#endif
#else 
#ifdef D___Thread______nativeGetStatus____I
int CALL12(___Thread______nativeGetStatus____I, int ___mid___, struct ___Thread___ * ___this___, int ___mid___) {
  return 0;
}
#endif
#endif
#endif
#ifdef DSTM
/* Add thread join capability */
#ifdef D___Thread______join____
void CALL01(___Thread______join____, struct ___Thread___ * ___this___) {
  unsigned int *oidarray;
  unsigned short *versionarray, version;
  objheader_t *ptr;
  /* Add transaction to check if thread finished for join operation */
transstart:
  transStart();
  ptr = transRead((unsigned int) VAR(___this___));
  struct ___Thread___ *p = (struct ___Thread___ *) ptr;
#ifdef THREADJOINDEBUG
  printf("Start join process for Oid = %x\n", (unsigned int) VAR(___this___));
#endif
  if(p->___threadDone___ == 1) {
#ifdef THREADJOINDEBUG
    printf("Thread oid = %x is done\n", (unsigned int) VAR(___this___));
#endif
    transAbort();
    return;
  }
#ifdef RECOVERY
  else if( checkiftheMachineDead(p->___mid___) == 0) {
    printf("Thread oid = %x is dead\n", (unsigned int) VAR(___this___));
    transAbort();
    return;
  }
#endif
  else {
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
    stopforgc((struct garbagelist *)___params___);
#endif

#ifdef RECOVERY
    reqNotify(oidarray, versionarray, 1,p->___mid___);
#else
    reqNotify(oidarray, versionarray, 1);
#endif
#ifdef PRECISE_GC
    restartaftergc();
#endif
    free(oidarray);
    free(versionarray);
    transAbort();
    goto transstart;
  }
  return;
}
#endif
#endif

#if defined(THREADS)||defined(STM)
#ifdef D___Thread______nativeJoin____
void CALL01(___Thread______nativeJoin____, struct ___Thread___ * ___this___) {
  pthread_mutex_lock(&joinlock);
  while(!VAR(___this___)->___finished___) {
#ifdef PRECISE_GC
  stopforgc((struct garbagelist *)___params___);
#endif
    pthread_cond_wait(&joincond, &joinlock);
#ifdef PRECISE_GC
    restartaftergc();
#endif
  }
  pthread_mutex_unlock(&joinlock);
}
#endif

#ifdef D___Thread______nativeCreate____
void CALL01(___Thread______nativeCreate____, struct ___Thread___ * ___this___) {
  pthread_t thread;
  int retval;
  pthread_attr_t nattr;

  pthread_mutex_lock(&gclistlock);
  threadcount++;
  pthread_mutex_unlock(&gclistlock);
  pthread_attr_init(&nattr);
  pthread_attr_setdetachstate(&nattr, PTHREAD_CREATE_DETACHED);
  INTPTR stacksize;
  pthread_attr_getstacksize(&nattr, &stacksize);
  do {
    retval=pthread_create(&thread, &nattr, (void * (*)(void *)) &initthread, VAR(___this___));
    if (retval!=0)
      usleep(1);
  } while(retval!=0);
  /* This next statement will likely not work on many machines */

  pthread_attr_destroy(&nattr);
}
#endif
#endif

#ifdef DSTM
#ifdef D___Thread______start____I
void CALL12(___Thread______start____I, int ___mid___, struct ___Thread___ * ___this___, int ___mid___) {
  startRemoteThread((unsigned int)VAR(___this___), ___mid___);
}
#endif
#endif

#ifdef DSTM
void globalDestructor(void *value) {
  free(value);
  pthread_setspecific(oidval, NULL);
}

void initDSMthread(int *ptr) {
  objheader_t *tmp;
  void *threadData;
  int oid=ptr[0];
  int type=ptr[1];
  free(ptr);
#ifdef PRECISE_GC
  int p[]={1, 0 /* NULL */, oid};
#ifdef MAC
  struct listitem litem;
  pthread_setspecific(litemkey, &litem);
#endif

  //Add our litem to list of threads
  litem.prev=NULL;
  pthread_mutex_lock(&gclistlock);
  litem.next=list;
  if(list!=NULL)
    list->prev=&litem;
  list=&litem;
  pthread_mutex_unlock(&gclistlock);

  ((void(*) (void *))virtualtable[type*MAXCOUNT+RUNMETHOD])(p);
#else
  ((void(*) (void *))virtualtable[type*MAXCOUNT+RUNMETHOD])(oid);
#endif
  threadData = calloc(1, sizeof(unsigned int));
  *((unsigned int *) threadData) = oid;
  pthread_setspecific(oidval, threadData);
  pthread_mutex_lock(&gclistlock);

#ifdef THREADS
  pthread_setspecific(threadlocks, litem.locklist);
#endif
  if (litem.prev==NULL) {
    list=litem.next;
  } else {
    litem.prev->next=litem.next;
  }
  if (litem.next!=NULL) {
    litem.next->prev=litem.prev;
  }
  threadcount--;
  pthread_cond_signal(&gccond);
  pthread_mutex_unlock(&gclistlock);
  /* Add transaction to check if thread finished for join operation */
  goto transstart;
transstart:
  {
    transStart();
    tmp  = transRead((unsigned int) oid);
    ((struct ___Thread___ *)tmp)->___threadDone___ = 1;
    *((unsigned int *)&((struct ___Object___ *) tmp)->___localcopy___) |=DIRTY;
    if(transCommit()!= 0) {
      goto transstart;
    }
  }
  pthread_exit(NULL);
}

void startDSMthread(int oid, int objType) {
  pthread_t thread;
  int retval;
  pthread_attr_t nattr;

//  printf("%s -> oid : %u\n",__func__,oid);

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
