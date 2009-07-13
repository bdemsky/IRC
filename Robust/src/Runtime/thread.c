#include "runtime.h"
#include <sys/types.h>
#include <unistd.h>
#include <errno.h>
#include <stdlib.h>
#include "thread.h"
#include "option.h"
#include <signal.h>

#ifdef DSTM
#include <DSTM/interface/dstm.h>
#include <DSTM/interface/llookup.h>
#endif

#ifndef RAW
#include <stdio.h>
#endif
#ifdef STM
#include "tm.h"
#endif
#include <execinfo.h>


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
pthread_mutex_t threadnotifylock;
pthread_cond_t threadnotifycond;
pthread_key_t oidval;

#if defined(THREADS) || defined(DSTM) || defined(STM)
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
  struct ___Object___ *ll=pthread_getspecific(threadlocks);
  while(ll!=NULL) {
    struct ___Object___ *llnext=ll->___nextlockobject___;
    ll->___nextlockobject___=NULL;
    ll->___prevlockobject___=NULL;
    ll->lockcount=0;
    ll->tid=0; //unlock it
    ll=llnext;
  }
  pthread_mutex_lock(&objlock); //wake everyone up
  pthread_cond_broadcast(&objcond);
  pthread_mutex_unlock(&objlock);
#endif
  pthread_mutex_lock(&gclistlock);
#ifdef THREADS
  pthread_setspecific(threadlocks, litem.locklist);
#endif
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

struct primitivelist *pl;

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
  pthread_key_create(&threadlocks, NULL);
#ifdef MAC
  pthread_key_create(&litem, NULL);
#endif
  processOptions();
  initializeexithandler();

  //deprecated use of sighandler, but apparently still works
  sig.sa_handler=(void *)threadhandler;
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
  pl=&primstack;
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
      typesCausingAbort[i] = 0;
    }
  }
#endif
#endif
#ifdef MAC
  struct listitem *litem=malloc(sizeof(struct listitem));
  pthread_setspecific(litemkey, litem);
  litem->prev=NULL;
  litem->next=list;
  if(list!=NULL)
    list->prev=litem;
  list=litem;
#else
  //Add our litem to list of threads
  litem.prev=NULL;
  litem.next=list;
  if(list!=NULL)
    list->prev=&litem;
  list=&litem;
#endif
}

#if defined(THREADS)||defined(STM)
void initthread(struct ___Thread___ * ___this___) {
#ifdef PRECISE_GC
  INTPTR p[]={1, (INTPTR) NULL, (INTPTR) ___this___};
  //Add our litem to list of threads
#ifdef MAC
  struct listitem litem;
  pthread_setspecific(litemkey, &litem);
#endif
  litem.prev=NULL;
  pthread_mutex_lock(&gclistlock);
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
}
#endif

void CALL11(___Thread______sleep____J, long long ___millis___, long long ___millis___) {
#if defined(THREADS)||defined(STM)
#ifdef PRECISE_GC
  stopforgc((struct garbagelist *)___params___);
#endif
#endif
  usleep(___millis___);
#if defined(THREADS)||defined(STM)
#ifdef PRECISE_GC
  restartaftergc();
#endif
#endif
}

#if defined(DSTM)|| defined(THREADS)||defined(STM)
void CALL00(___Thread______yield____) {
  pthread_yield();
}
#endif

#ifdef DSTM
/* Add thread join capability */
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
    stopforgc((struct garbagelist *)___params___);
#endif
    reqNotify(oidarray, versionarray, 1);
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

#if defined(THREADS)||defined(STM)
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
  printf("STACKSIZE=%u\n",stacksize);
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
