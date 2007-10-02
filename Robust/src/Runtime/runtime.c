#include "runtime.h"
#include "structdefs.h"
#include <signal.h>
#include "mem.h"
#include<fcntl.h>
#include<errno.h>
#include<signal.h>
#include<stdio.h>
#include "option.h"
#ifdef DSTM
#include "dstm.h"
#endif

extern int classsize[];
jmp_buf error_handler;
int instructioncount;

char *options;
int injectfailures=0;
float failurechance=0;
int debugtask=0;
int injectinstructionfailures;
int failurecount;
float instfailurechance=0;
int numfailures;
int instaccum=0;
#ifdef DMALLOC
#include "dmalloc.h"
#endif

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

void CALL01(___System______printString____L___String___,struct ___String___ * ___s___) {
    struct ArrayObject * chararray=VAR(___s___)->___value___;
    int i;
    int offset=VAR(___s___)->___offset___;
    for(i=0;i<VAR(___s___)->___count___;i++) {
	short sc=((short *)(((char *)& chararray->___length___)+sizeof(int)))[i+offset];
	putchar(sc);
    }
}

/* Object allocation function */

#ifdef DSTM
void * allocate_newglobal(transrecord_t *trans, int type) {
  struct ___Object___ * v=(struct ___Object___ *) transCreateObj(trans, classsize[type]);
  v->type=type;
#ifdef THREADS
  v->tid=0;
  v->lockentry=0;
  v->lockcount=0;
#endif
  return v;
}

/* Array allocation function */

struct ArrayObject * allocate_newarrayglobal(transrecord_t *trans, int type, int length) {
  struct ArrayObject * v=(struct ArrayObject *)transCreateObj(trans, sizeof(struct ArrayObject)+length*classsize[type]);
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


#ifdef PRECISE_GC
void * allocate_new(void * ptr, int type) {
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

struct ArrayObject * allocate_newarray(void * ptr, int type, int length) {
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
void * allocate_new(int type) {
  struct ___Object___ * v=FREEMALLOC(classsize[type]);
  v->type=type;
#ifdef OPTIONAL
  v->fses=0;
#endif
  return v;
}

/* Array allocation function */

struct ArrayObject * allocate_newarray(int type, int length) {
  struct ArrayObject * v=FREEMALLOC(sizeof(struct ArrayObject)+length*classsize[type]);
  v->type=type;
  v->___length___=length;
#ifdef OPTIONAL
  v->fses=0;
#endif
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

  for(i=0;i<length;i++) {
    ((short *)(((char *)& chararray->___length___)+sizeof(int)))[i]=(short)str[i];  }
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
