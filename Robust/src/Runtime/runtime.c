#include "runtime.h"
#include "structdefs.h"
#include <signal.h>
#include "mem.h"
#include <fcntl.h>
#include <errno.h>
#include <stdio.h>
#include "option.h"
#include "methodheaders.h"
#ifdef DSTM
#include "dstm.h"
#include "prelookup.h"
#include "prefetch.h"
#endif
#ifdef STM
#include "tm.h"
#include <pthread.h>
/* Global barrier for STM */
pthread_barrier_t barrier; 
pthread_barrierattr_t attr;
#endif
#include <string.h>

extern int classsize[];
extern int typearray[];
extern int typearray2[];
jmp_buf error_handler;
int instructioncount;

char *options;
int injectfailures=0;
float failurechance=0;
int errors=0;
int debugtask=0;
int injectinstructionfailures;
int failurecount;
float instfailurechance=0;
int numfailures;
int instaccum=0;
#ifdef DMALLOC
#include "dmalloc.h"
#endif

int instanceof(struct ___Object___ *ptr, int type) {
  int i=ptr->type;
  do {
    if (i==type)
      return 1;
    i=typearray[i];
  } while(i!=-1);
  i=ptr->type;
  if (i>NUMCLASSES) {
    do {
      if (i==type)
	return 1;
      i=typearray2[i-NUMCLASSES];
    } while(i!=-1);
  }
  return 0;
}

void exithandler(int sig, siginfo_t *info, void * uap) {
  exit(0);
}

void initializeexithandler() {
  struct sigaction sig;
  sig.sa_sigaction=&exithandler;
  sig.sa_flags=SA_SIGINFO;
  sigemptyset(&sig.sa_mask);
  sigaction(SIGUSR2, &sig, 0);
}


/* This function inject failures */

void injectinstructionfailure() {
#ifdef TASK
  if (injectinstructionfailures) {
    if (numfailures==0)
      return;
    instructioncount=failurecount;
    instaccum+=failurecount;
    if ((((double)random())/RAND_MAX)<instfailurechance) {
      if (numfailures>0)
	numfailures--;
      printf("FAILURE!!! %d\n",numfailures);
      longjmp(error_handler,11);
    }
  }
#else
#ifdef THREADS
  if (injectinstructionfailures) {
    if (numfailures==0)
      return;
    instaccum+=failurecount;
    if ((((double)random())/RAND_MAX)<instfailurechance) {
      if (numfailures>0)
	numfailures--;
      printf("FAILURE!!! %d\n",numfailures);
      threadexit();
    }
  }
#endif
#endif
}

void CALL11(___System______exit____I,int ___status___, int ___status___) {
#ifdef TRANSSTATS
  printf("numTransCommit = %d\n", numTransCommit);
  printf("numTransAbort = %d\n", numTransAbort);
  printf("nSoftAbort = %d\n", nSoftAbort);
#ifdef STM
  printf("nSoftAbortCommit = %d\n", nSoftAbortCommit);
  printf("nSoftAbortAbort = %d\n", nSoftAbortAbort);
#endif
#endif
  exit(___status___);
}

#ifdef D___Vector______removeElement_____AR_L___Object____II
void CALL23(___Vector______removeElement_____AR_L___Object____II, int ___index___, int ___size___, struct ArrayObject * ___array___, int ___index___, int ___size___) {
  char* offset=((char *)(&VAR(___array___)->___length___))+sizeof(unsigned int)+sizeof(void *)*___index___;
  memmove(offset, offset+sizeof(void *),(___size___-___index___-1)*sizeof(void *));
}
#endif

void CALL11(___System______printI____I,int ___status___, int ___status___) {
  printf("%d\n",___status___);
}

long long CALL00(___System______currentTimeMillis____) {
  struct timeval tv; long long retval;
  gettimeofday(&tv, NULL);
  retval = tv.tv_sec; /* seconds */
  retval*=1000; /* milliseconds */
  retval+= (tv.tv_usec/1000); /* adjust milliseconds & add them in */
  return retval;
}

void CALL01(___System______printString____L___String___,struct ___String___ * ___s___) {
  struct ArrayObject * chararray=VAR(___s___)->___value___;
  int i;
  int offset=VAR(___s___)->___offset___;
  for(i=0; i<VAR(___s___)->___count___; i++) {
    short sc=((short *)(((char *)&chararray->___length___)+sizeof(int)))[i+offset];
    putchar(sc);
  }
}

#ifdef DSTM
void CALL00(___System______clearPrefetchCache____) {
  prehashClear();
}

#ifdef RANGEPREFETCH
void CALL02(___System______rangePrefetch____L___Object_____AR_S, struct ___Object___ * ___o___, struct ArrayObject * ___offsets___) {
  /* Manual Prefetches to be inserted */
  //printf("DEBUG-> %s() ___Object___ * ___o___ = %x\n", __func__, VAR(___o___));
  //printf("DEBUG-> %s() ArrayObject * = %x\n", __func__, VAR(___offsets___));
  int numoffset=VAR(___offsets___)->___length___;
  int i;
  short offArry[numoffset+2];
  offArry[0] = 0;
  offArry[1] = 0;
  for(i = 2; i<(numoffset+2); i++) {
    offArry[i] = *((short *)(((char *)&VAR(___offsets___)->___length___) + sizeof(int) + (i-2) * sizeof(short)));
    //printf("DEBUG-> offArry[%d] = %d\n", i, offArry[i]);
  }
  unsigned int oid;
  if(((unsigned int)(VAR(___o___)) & 1) != 0) { //odd
    oid =  (unsigned int) VAR(___o___); //outside transaction therefore just an oid
  } else { //even
    oid = (unsigned int) COMPOID(VAR(___o___)); //inside transaction therefore a pointer to oid
  }
  rangePrefetch(oid, (short)(numoffset+2), offArry);
}
#else
void CALL02(___System______rangePrefetch____L___Object_____AR_S, struct ___Object___ * ___o___, struct ArrayObject * ___offsets___) {
  return;
}
#endif

#endif

#ifdef STM
/* STM Barrier constructs */
void CALL11(___Barrier______setBarrier____I, int nthreads, int nthreads) {
#ifdef PRECISE_GC
  struct listitem *tmp=stopforgc((struct garbagelist *)___params___);
#endif
  // Barrier initialization
  int ret; 
  if((ret = pthread_barrier_init(&barrier, NULL, nthreads)) != 0) {
    printf("%s() Could not create a barrier: numthreads = 0 in %s\n", __func__, __FILE__);
    exit(-1);
  }
#ifdef PRECISE_GC
  restartaftergc(tmp);
#endif
} 

void CALL00(___Barrier______enterBarrier____) {
  // Synchronization point
  int ret;
  ret = pthread_barrier_wait(&barrier);
  if(ret != 0 && ret != PTHREAD_BARRIER_SERIAL_THREAD) {
    printf("%s() Could not wait on barrier: error %d in %s\n", __func__, errno, __FILE__);
    exit(-1);
  }
}
#endif

/* Object allocation function */

#ifdef DSTM
__attribute__((malloc)) void * allocate_newglobal(int type) {
  struct ___Object___ * v=(struct ___Object___ *) transCreateObj(classsize[type]);
  v->type=type;
#ifdef THREADS
  v->tid=0;
  v->lockentry=0;
  v->lockcount=0;
#endif
  return v;
}

/* Array allocation function */

__attribute__((malloc)) struct ArrayObject * allocate_newarrayglobal(int type, int length) {
  struct ArrayObject * v=(struct ArrayObject *)transCreateObj(sizeof(struct ArrayObject)+length*classsize[type]);
  if (length<0) {
    printf("ERROR: negative array\n");
    return NULL;
  }
  v->type=type;
  v->___length___=length;
#ifdef THREADS
  v->tid=0;
  v->lockentry=0;
  v->lockcount=0;
#endif
  return v;
}
#endif


#ifdef STM
// STM Versions of allocation functions

/* Object allocation function */
__attribute__((malloc)) void * allocate_newtrans(void * ptr, int type) {
  struct ___Object___ * v=(struct ___Object___ *) transCreateObj(ptr, classsize[type]);
  v->type=type;
  v->___objlocation___=v;
  return v;
}

/* Array allocation function */
__attribute__((malloc)) struct ArrayObject * allocate_newarraytrans(void * ptr, int type, int length) {
  struct ArrayObject * v=(struct ArrayObject *)transCreateObj(ptr, sizeof(struct ArrayObject)+length*classsize[type]);
  if (length<0) {
    printf("ERROR: negative array\n");
    return NULL;
  }
  v->___objlocation___=(struct ___Object___*)v;
  v->type=type;
  v->___length___=length;
  return v;
}
__attribute__((malloc)) void * allocate_new(void * ptr, int type) {
  objheader_t *tmp=mygcmalloc((struct garbagelist *) ptr, classsize[type]+sizeof(objheader_t));
  struct ___Object___ * v=(struct ___Object___ *) &tmp[1];
  initdsmlocks(&tmp->lock);
  tmp->version = 1;
  v->___objlocation___=v;
  v->type = type;
  return v;
}

/* Array allocation function */

__attribute__((malloc)) struct ArrayObject * allocate_newarray(void * ptr, int type, int length) {
  objheader_t *tmp=mygcmalloc((struct garbagelist *) ptr, sizeof(struct ArrayObject)+length*classsize[type]+sizeof(objheader_t));
  struct ArrayObject * v=(struct ArrayObject *) &tmp[1];
  initdsmlocks(&tmp->lock);
  tmp->version=1;
  v->type=type;
  if (length<0) {
    printf("ERROR: negative array\n");
    return NULL;
  }
  v->___objlocation___=(struct ___Object___ *)v;
  v->___length___=length;
  return v;
}
#endif

#ifndef STM
#if defined(PRECISE_GC)
__attribute__((malloc)) void * allocate_new(void * ptr, int type) {
  struct ___Object___ * v=(struct ___Object___ *) mygcmalloc((struct garbagelist *) ptr, classsize[type]);
  v->type=type;
#ifdef THREADS
  v->tid=0;
  v->lockentry=0;
  v->lockcount=0;
#endif
#ifdef OPTIONAL
  v->fses=0;
#endif
  return v;
}

/* Array allocation function */

__attribute__((malloc)) struct ArrayObject * allocate_newarray(void * ptr, int type, int length) {
  struct ArrayObject * v=mygcmalloc((struct garbagelist *) ptr, sizeof(struct ArrayObject)+length*classsize[type]);
  v->type=type;
  if (length<0) {
    printf("ERROR: negative array\n");
    return NULL;
  }
  v->___length___=length;
#ifdef THREADS
  v->tid=0;
  v->lockentry=0;
  v->lockcount=0;
#endif
#ifdef OPTIONAL
  v->fses=0;
#endif
  return v;
}

#else
__attribute__((malloc)) void * allocate_new(int type) {
  struct ___Object___ * v=FREEMALLOC(classsize[type]);
  v->type=type;
#ifdef OPTIONAL
  v->fses=0;
#endif
  return v;
}

/* Array allocation function */

__attribute__((malloc)) struct ArrayObject * allocate_newarray(int type, int length) {
  __attribute__((malloc))  struct ArrayObject * v=FREEMALLOC(sizeof(struct ArrayObject)+length*classsize[type]);
  v->type=type;
  v->___length___=length;
#ifdef OPTIONAL
  v->fses=0;
#endif
  return v;
}
#endif
#endif


/* Converts C character arrays into Java strings */
#ifdef PRECISE_GC
__attribute__((malloc)) struct ___String___ * NewString(void * ptr, const char *str,int length) {
#else
__attribute__((malloc)) struct ___String___ * NewString(const char *str,int length) {
#endif
  int i;
#ifdef PRECISE_GC
  struct ArrayObject * chararray=allocate_newarray((struct garbagelist *)ptr, CHARARRAYTYPE, length);
  INTPTR ptrarray[]={1, (INTPTR) ptr, (INTPTR) chararray};
  struct ___String___ * strobj=allocate_new((struct garbagelist *) &ptrarray, STRINGTYPE);
  chararray=(struct ArrayObject *) ptrarray[2];
#else
  struct ArrayObject * chararray=allocate_newarray(CHARARRAYTYPE, length);
  struct ___String___ * strobj=allocate_new(STRINGTYPE);
#endif
  strobj->___value___=chararray;
  strobj->___count___=length;
  strobj->___offset___=0;

  for(i=0; i<length; i++) {
    ((short *)(((char *)&chararray->___length___)+sizeof(int)))[i]=(short)str[i];
  }
  return strobj;
}

/* Generated code calls this if we fail a bounds check */

void failedboundschk() {
#ifndef TASK
  printf("Array out of bounds\n");
#ifdef THREADS
  threadexit();
#else
  exit(-1);
#endif
#else
  longjmp(error_handler,2);
#endif
}

/* Abort task call */
void abort_task() {
#ifdef TASK
  longjmp(error_handler,4);
#else
  printf("Aborting\n");
  exit(-1);
#endif
}
