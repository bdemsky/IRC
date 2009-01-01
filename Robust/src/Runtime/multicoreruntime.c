#include "runtime.h"
#include "structdefs.h"
#include <signal.h>
#include "mem.h"
#include <fcntl.h>
#include <errno.h>
#include <signal.h>
#ifndef RAW
#include <stdio.h>
#endif
//#include "option.h"

extern int classsize[];
jmp_buf error_handler;
int instructioncount;

char *options;
int injectfailures=0;
float failurechance=0;
int debugtask=0;
int errors=0;
int injectinstructionfailures;
int failurecount;
float instfailurechance=0;
int numfailures;
int instaccum=0;
#ifdef DMALLOC
#include "dmalloc.h"
#endif

#ifdef RAW
void initializeexithandler() {
}
#else
void exithandler(int sig, siginfo_t *info, void * uap) {
#ifdef DEBUG
  printf("exit in exithandler\n");
#endif
  exit(0);
}

void initializeexithandler() {
  struct sigaction sig;
  sig.sa_sigaction=&exithandler;
  sig.sa_flags=SA_SIGINFO;
  sigemptyset(&sig.sa_mask);
  sigaction(SIGUSR2, &sig, 0);
}
#endif

/* This function inject failures */

void injectinstructionfailure() {
#ifdef RAW
  // not supported in RAW version
  return;
#else
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
#endif
}

void CALL11(___System______exit____I,int ___status___, int ___status___) {
#ifdef DEBUG
  printf("exit in CALL11\n");
#endif
#ifdef RAW
  raw_test_done(___status___);
#else
  exit(___status___);
#endif
}

void CALL11(___System______printI____I,int ___status___, int ___status___) {
#ifdef DEBUG
  printf("printI in CALL11\n");
#endif
#ifdef RAW
  raw_test_pass(0x1111);
  raw_test_pass_reg(___status___);
#else
  printf("%d\n", ___status___);
#endif
}

long CALL00(___System______currentTimeMillis____) {
#ifdef RAW
  // not supported in RAW version
  return -1;
#else
  struct timeval tv; long long retval;
  gettimeofday(&tv, NULL);
  retval = tv.tv_sec; /* seconds */
  retval*=1000; /* milliseconds */
  retval+= (tv.tv_usec/1000); /* adjust milliseconds & add them in */
  return retval;
#endif
}

void CALL01(___System______printString____L___String___,struct ___String___ * ___s___) {
#ifdef RAW
#else
  struct ArrayObject * chararray=VAR(___s___)->___value___;
  int i;
  int offset=VAR(___s___)->___offset___;
  for(i=0; i<VAR(___s___)->___count___; i++) {
    short sc=((short *)(((char *)&chararray->___length___)+sizeof(int)))[i+offset];
    putchar(sc);
  }
#endif
}

/* Object allocation function */

#ifdef PRECISE_GC
void * allocate_new(void * ptr, int type) {
  struct ___Object___ * v=(struct ___Object___ *) mygcmalloc((struct garbagelist *) ptr, classsize[type]);
  v->type=type;
  v->isolate = 1;
  v->version = 0;
  //v->numlocks = 0;
  v->lock = NULL;
#ifdef THREADS
  v->tid=0;
  v->lockentry=0;
  v->lockcount=0;
#endif
  return v;
}

/* Array allocation function */

struct ArrayObject * allocate_newarray(void * ptr, int type, int length) {
  struct ArrayObject * v=mygcmalloc((struct garbagelist *) ptr, sizeof(struct ArrayObject)+length*classsize[type]);
  v->type=type;
  v->isolate = 1;
  v->version = 0;
  //v->numlocks = 0;
  v->lock = NULL;
  if (length<0) {
#ifndef RAW
    printf("ERROR: negative array\n");
#endif
    return NULL;
  }
  v->___length___=length;
#ifdef THREADS
  v->tid=0;
  v->lockentry=0;
  v->lockcount=0;
#endif
  return v;
}

#else
void * allocate_new(int type) {
  struct ___Object___ * v=FREEMALLOC(classsize[type]);
  v->type=type;
  v->isolate = 1;
  v->version = 0;
  //v->numlocks = 0;
  v->lock = NULL;
  return v;
}

/* Array allocation function */

struct ArrayObject * allocate_newarray(int type, int length) {
  struct ArrayObject * v=FREEMALLOC(sizeof(struct ArrayObject)+length*classsize[type]);
  v->type=type;
  v->isolate = 1;
  v->version = 0;
  //v->numlocks = 0;
  v->lock = NULL;
  v->___length___=length;
  return v;
}
#endif


/* Converts C character arrays into Java strings */
#ifdef PRECISE_GC
struct ___String___ * NewString(void * ptr, const char *str,int length) {
#else
struct ___String___ * NewString(const char *str,int length) {
#endif
  int i;
#ifdef PRECISE_GC
  struct ArrayObject * chararray=allocate_newarray((struct garbagelist *)ptr, CHARARRAYTYPE, length);
  int ptrarray[]={1, (int) ptr, (int) chararray};
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
