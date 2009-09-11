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
#endif
#ifdef STMLOG
__thread int counter;
__thread int event[100000*7+3];
__thread unsigned long long clkticks[100000*7+3];
#define FILENAME  "log"
#endif

#if defined(THREADS)||defined(STM)
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
typedef unsigned long long ticks;
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
#endif
#ifdef D___System______deepArrayCopy____L___Object____L___Object___
void deepArrayCopy(struct ___Object___ * dst, struct ___Object___ * src) {
  int dsttype=((int *)dst)[0];
  int srctype=((int *)src)[0];
  if (dsttype<NUMCLASSES||srctype<NUMCLASSES||srctype!=dsttype)
    return;
  struct ArrayObject *aodst=(struct ArrayObject *)dst;
  struct ArrayObject *aosrc=(struct ArrayObject *)src;
  int dstlength=aodst->___length___;
  int srclength=aosrc->___length___;
  if (dstlength!=srclength)
    return;
  unsigned INTPTR *pointer=pointerarray[srctype];
  if (pointer==0) {
    int elementsize=classsize[srctype];
    int size=srclength*elementsize;
    //primitives
    memcpy(((char *)&aodst->___length___)+sizeof(int) , ((char *)&aosrc->___length___)+sizeof(int), size);
  } else {
    //objects
    int i;
    for(i=0;i<srclength;i++) {
      struct ___Object___ * ptr=((struct ___Object___**)(((char*) &aosrc->___length___)+sizeof(int)))[i];
      int ptrtype=((int *)ptr)[0];
      if (ptrtype>=NUMCLASSES) {
	struct ___Object___ * dstptr=((struct ___Object___**)(((char*) &aodst->___length___)+sizeof(int)))[i];
	deepArrayCopy(dstptr,ptr);
      } else {
	//hit an object
	((struct ___Object___ **)(((char*) &aodst->___length___)+sizeof(int)))[i]=ptr;
      }
    }
  }
}

void CALL02(___System______deepArrayCopy____L___Object____L___Object___, struct ___Object___ * ___dst___, struct ___Object___ * ___src___) {
  deepArrayCopy(VAR(___dst___), VAR(___src___));
}
#endif

void CALL11(___System______exit____I,int ___status___, int ___status___) {
#ifdef TRANSSTATS
  printf("numTransCommit = %d\n", numTransCommit);
  printf("numTransAbort = %d\n", numTransAbort);
  printf("nSoftAbort = %d\n", nSoftAbort);
#ifdef STM
  printf("nSoftAbortCommit = %d\n", nSoftAbortCommit);
  printf("nSoftAbortAbort = %d\n", nSoftAbortAbort);
#ifdef STMSTATS
  int i;
  for(i=0; i<TOTALNUMCLASSANDARRAY; i++) {
    printf("typesCausingAbort[%2d] numaccess= %5d numabort= %3d\n", i, typesCausingAbort[i].numaccess, typesCausingAbort[i].numabort);
  }
#endif
#endif
#endif
  exit(___status___);
}

#if defined(__i386__)

static __inline__ unsigned long long rdtsc(void)
{
  unsigned long long int x;
  __asm__ volatile (".byte 0x0f, 0x31" : "=A" (x));
  return x;
}
#elif defined(__x86_64__)

static __inline__ unsigned long long rdtsc(void)
{
  unsigned hi, lo;
  __asm__ __volatile__ ("rdtsc" : "=a"(lo), "=d"(hi));
  return ( (unsigned long long)lo)|( ((unsigned long long)hi)<<32 );
}

#elif defined(__powerpc__)

typedef unsigned long long int unsigned long long;

static __inline__ unsigned long long rdtsc(void)
{
  unsigned long long int result=0;
  unsigned long int upper, lower,tmp;
  __asm__ volatile(
      "0:                  \n"
      "\tmftbu   %0           \n"
      "\tmftb    %1           \n"
      "\tmftbu   %2           \n"
      "\tcmpw    %2,%0        \n"
      "\tbne     0b         \n"
      : "=r"(upper),"=r"(lower),"=r"(tmp)
      );
  result = upper;
  result = result<<32;
  result = result|lower;

  return(result);
}
#endif

void CALL11(___System______logevent____I,int ___event___, int ___event___) {
#ifdef STMLOG
  event[counter] = ___event___;
  clkticks[counter] = rdtsc();
  counter++;
#endif
  return;
}

void CALL11(___System______flushToFile____I, int ___threadid___, int ___threadid___) {
#ifdef STMLOG
  FILE *fp;
  /* Flush to file */
  char filename[20];
  memset(filename, 0, 20);
  sprintf(filename, "%s_%d", FILENAME, ___threadid___);
  if ((fp = fopen(filename, "w+")) == NULL) {
    perror("fopen");
    return;
  }
  int i;
  for (i = 0; i < counter-1; i++) {
    fprintf(fp, "%d %lld %lld\n", event[i], clkticks[i], clkticks[i+1]);
  }
  fprintf(fp, "%d %lld\n", event[i], clkticks[i]);

  fclose(fp);
#endif
  return;
}

void CALL00(___System______initLog____) {
#ifdef STMLOG
  counter=0;
#endif
  return;
}

#ifdef D___Vector______removeElement_____AR_L___Object____I_I
void CALL23(___Vector______removeElement_____AR_L___Object____I_I, int ___index___, int ___size___, struct ArrayObject * ___array___, int ___index___, int ___size___) {
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

long long CALL00(___System______microTimes____) {
  struct timeval tv; 
  long long retval;
  gettimeofday(&tv, NULL);
  retval = tv.tv_sec; /* seconds */
  retval*=1000000; /* microsecs */
  retval+= (tv.tv_usec); /* adjust microseconds & add them in */
  return retval;
}

long long CALL00(___System______getticks____) {
  unsigned a, d;
  asm("cpuid");
  asm volatile("rdtsc" : "=a" (a), "=d" (d));
  return (((ticks)a) | (((ticks)d) << 32));
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

/* STM Barrier constructs */
#ifdef D___Barrier______setBarrier____I
void CALL11(___Barrier______setBarrier____I, int nthreads, int nthreads) {
  // Barrier initialization
  int ret;
  if((ret = pthread_barrier_init(&barrier, NULL, nthreads)) != 0) {
    printf("%s() Could not create a barrier: numthreads = 0 in %s\n", __func__, __FILE__);
    exit(-1);
  }
}
#endif

#ifdef D___Barrier______enterBarrier____
void CALL00(___Barrier______enterBarrier____) {
  // Synchronization point
  int ret;
#ifdef PRECISE_GC
  stopforgc((struct garbagelist *)___params___);
#endif
  ret = pthread_barrier_wait(&barrier);
#ifdef PRECISE_GC
  restartaftergc();
#endif
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
    printf("ERROR: negative array %d\n", length);
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
