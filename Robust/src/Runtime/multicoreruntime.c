#include "runtime.h"
#include "structdefs.h"
#include "mem.h"
#ifndef MULTICORE
#include <fcntl.h>
#include <errno.h>
#include <signal.h>
#endif
#ifndef RAW
#include <stdio.h>
#endif
#ifdef MULTICORE
#include "runtime_arch.h"
#endif
//#include "option.h"

extern int classsize[];
#ifndef MULTICORE
jmp_buf error_handler;
int instructioncount;

char *options;
int injectfailures=0;
float failurechance=0;
int errors=0;
int injectinstructionfailures;
int failurecount;
float instfailurechance=0;
int numfailures;
int instaccum=0;
#ifdef DMALLOC
#include "dmalloc.h"
#endif
#endif

int debugtask=0;

#ifdef MULTICORE
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
#ifdef MULTICORE
  // not supported in MULTICORE version
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

#ifdef D___Double______nativeparsedouble____L___String___
double CALL01(___Double______nativeparsedouble____L___String___,struct ___String___ * ___str___) {
  int length=VAR(___str___)->___count___;
  int maxlength=(length>60)?60:length;
  char str[maxlength+1];
  struct ArrayObject * chararray=VAR(___str___)->___value___;
  int i;
  int offset=VAR(___str___)->___offset___;
  for(i=0; i<maxlength; i++) {
    str[i]=((short *)(((char *)&chararray->___length___)+sizeof(int)))[i+offset];
  }
  str[i]=0;
  double d=atof(str);
  return d;
}
#endif

#ifdef D___String______convertdoubletochar____D__AR_C
int CALL12(___String______convertdoubletochar____D__AR_C, double ___val___, double ___val___, struct ArrayObject ___chararray___) {
  int length=VAR(___chararray___)->___length___;
  char str[length];
  int i;
  int num=snprintf(str, length, "%f",___val___);
  if (num>=length)
    num=length-1;
  for(i=0; i<length; i++) {
    ((short *)(((char *)&VAR(___chararray___)->___length___)+sizeof(int)))[i]=(short)str[i];
  }
  return num;
}
#else
int CALL12(___String______convertdoubletochar____D__AR_C, double ___val___, double ___val___, struct ArrayObject ___chararray___) {
	return 0;
}
#endif

void CALL11(___System______exit____I,int ___status___, int ___status___) {
#ifdef MULTICORE
  BAMBOO_EXIT(___status___);
#else
#ifdef DEBUG
  printf("exit in CALL11\n");
#endif
  exit(___status___);
#endif
}

//#ifdef D___Vector______removeElement_____AR_L___Object____I_I
void CALL23(___Vector______removeElement_____AR_L___Object____I_I, int ___index___, int ___size___, struct ArrayObject * ___array___, int ___index___, int ___size___) {
  char* offset=((char *)(&VAR(___array___)->___length___))+sizeof(unsigned int)+sizeof(void *)*___index___;
  memmove(offset, offset+sizeof(void *),(___size___-___index___-1)*sizeof(void *));
}
//#endif

void CALL11(___System______printI____I,int ___status___, int ___status___) {
#ifdef MULTICORE
  BAMBOO_DEBUGPRINT(0x1111);
  BAMBOO_DEBUGPRINT_REG(___status___);
#else
#ifdef DEBUG
  printf("printI in CALL11\n");
#endif
  printf("%d\n", ___status___);
#endif
}

long CALL00(___System______currentTimeMillis____) {
#ifdef MULTICORE
  // not supported in MULTICORE version
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
#ifdef MULTICORE
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

#ifdef MULTICORE_GC
void * allocate_new(void * ptr, int type) {
  struct ___Object___ * v=(struct ___Object___ *)FREEMALLOC((struct garbagelist *) ptr, classsize[type]);
#ifdef DEBUG
	tprintf("new object: %x \n", v);
#endif
  v->type=type;
  v->version = 0;
  v->lock = NULL;
	initlock(v);
  return v;
}

/* Array allocation function */

struct ArrayObject * allocate_newarray(void * ptr, int type, int length) {
  struct ArrayObject * v=(struct ArrayObject *)FREEMALLOC((struct garbagelist *) ptr, sizeof(struct ArrayObject)+length*classsize[type]);
#ifdef DEBUG
	tprintf("new array object: %x \n", v);
#endif
  v->type=type;
  v->version = 0;
  v->lock = NULL;
  if (length<0) {
    return NULL;
  }
  v->___length___=length;
	initlock(v);
  return v;
}

#else
void * allocate_new(int type) {
  struct ___Object___ * v=FREEMALLOC(classsize[type]);
  v->type=type;
  v->version = 0;
  //v->numlocks = 0;
  v->lock = NULL;
	initlock(v);
  return v;
}

/* Array allocation function */

struct ArrayObject * allocate_newarray(int type, int length) {
  struct ArrayObject * v=FREEMALLOC(sizeof(struct ArrayObject)+length*classsize[type]);
  v->type=type;
  v->version = 0;
  //v->numlocks = 0;
  v->lock = NULL;
  v->___length___=length;
	initlock(v);
  return v;
}
#endif


/* Converts C character arrays into Java strings */
#ifdef MULTICORE_GC
struct ___String___ * NewString(void * ptr, const char *str,int length) {
#else
struct ___String___ * NewString(const char *str,int length) {
#endif
  int i;
#ifdef MULTICORE_GC
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
#ifndef MULTICORE
  printf("Array out of bounds\n");
  longjmp(error_handler,2);
#endif
#endif
}

/* Abort task call */
void abort_task() {
#ifdef TASK
#ifndef MULTICORE
  printf("Aborting\n");
  longjmp(error_handler,4);
#endif
#else
  printf("Aborting\n");
  exit(-1);
#endif
}
