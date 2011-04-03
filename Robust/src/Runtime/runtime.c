
#include "runtime.h"
#include "structdefs.h"
#include <signal.h>
#include "mem.h"
#include <fcntl.h>
#include <errno.h>
#include <stdio.h>
#include "option.h"
#include "methodheaders.h"

#if defined(THREADS)||defined(DSTM)||defined(STM)||defined(MLP)
#include "thread.h"
#endif

#ifdef DSTM
#ifdef RECOVERY
#include "DSTM/interface_recovery/dstm.h"
#include "DSTM/interface_recovery/altprelookup.h"

#ifdef RECOVERYSTATS
  extern int numRecovery;
  extern unsigned int deadMachine[8];
  extern unsigned int sizeOfRedupedData[8];
  extern double elapsedTime[8];
#endif
  
#else
#include "DSTM/interface/dstm.h"
#include "DSTM/interface/altprelookup.h"
#include "DSTM/interface/prefetch.h"
#endif
#endif
#ifdef STM
#include "tm.h"
#include <pthread.h>
#endif
#ifdef STMLOG
#define ARRAY_LENGTH 700003
__thread int counter;
__thread int event[ARRAY_LENGTH];
__thread unsigned long long clkticks[ARRAY_LENGTH];
unsigned long long beginClock=0;
#define FILENAME  "log"
#endif
#ifdef EVENTMONITOR
#include "monitor.h"
__thread int objcount=0;
#define ASSIGNUID(x) {					\
    int number=((objcount++)<<EVTHREADSHIFT)|threadnum;	\
    x->objuid=number;					\
  }
#else
#define ASSIGNUID(x)
#endif

#if defined(THREADS)||defined(STM)
/* Global barrier for STM */
pthread_barrier_t barrier;
pthread_barrierattr_t attr;
#endif

#include <string.h>

#ifndef bool
#define bool int
#endif
#define GCPOINT(x) ((INTPTR)((x)*0.99))


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

#ifdef D___Double______nativeparsedouble_____AR_B_I_I 
double CALL23(___Double______nativeparsedouble_____AR_B_I_I, int start, int length,int start,int length,struct ArrayObject * ___str___) {
  int maxlength=(length>60)?60:length;
  char str[maxlength+1];
  struct ArrayObject * bytearray=VAR(___str___);
  int i;
  for(i=0; i<maxlength; i++) {
    str[i]=(((char *)&bytearray->___length___)+sizeof(int))[i+start];
  }
  str[i]=0;
  double d=atof(str);
  return d;
}
#endif

#ifdef D___Double______doubleToRawLongBits____D 
typedef union jvalue
{
  bool z;
  char    c;
  short   s;
  int     i;
  long long    j;
  float   f;
  double  d;
} jvalue;

long long CALL11(___Double______doubleToRawLongBits____D, double dval, double dval) {
  jvalue val;
  val.d = dval;

#if defined(__IEEE_BYTES_LITTLE_ENDIAN)
  /* On little endian ARM processors when using FPA, word order of
     doubles is still big endian. So take that into account here. When
     using VFP, word order of doubles follows byte order. */

#define SWAP_DOUBLE(a)    (((a) << 32) | (((a) >> 32) & 0x00000000ffffffff))

  val.j = SWAP_DOUBLE(val.j);
#endif

  return val.j;
}
#endif

#ifdef D___Double______longBitsToDouble____J 
double CALL11(___Double______longBitsToDouble____J, long long lval, long long lval) {
  jvalue val;
  val.j = lval;

#if defined(__IEEE_BYTES_LITTLE_ENDIAN)
#ifndef SWAP_DOUBLE
#define SWAP_DOUBLE(a)    (((a) << 32) | (((a) >> 32) & 0x00000000ffffffff))
#endif
  val.j = SWAP_DOUBLE(val.j);
#endif

  return val.d;
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
#ifdef STMARRAY
  src=src->___objlocation___;
#endif
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

#ifdef D___System______arraycopy____L___Object____I_L___Object____I_I
void arraycopy(struct ___Object___ *src, int srcPos, struct ___Object___ *dst, int destPos, int length) {
  int dsttype=((int *)dst)[0];
  int srctype=((int *)src)[0];

  //not an array or type mismatch
  if (dsttype<NUMCLASSES||srctype<NUMCLASSES)
    return;
  if (srctype!=dsttype)
    printf("Potential type mismatch in arraycopy\n");

  struct ArrayObject *aodst=(struct ArrayObject *)dst;
  struct ArrayObject *aosrc=(struct ArrayObject *)src;
  int dstlength=aodst->___length___;
  int srclength=aosrc->___length___;

  if (length<=0)
    return;
  if (srcPos+length>srclength)
    return;
  if (destPos+length>dstlength)
    return;

  unsigned INTPTR *pointer=pointerarray[srctype];
  if (pointer==0) {
    int elementsize=classsize[srctype];
    int size=length*elementsize;
    //primitives
    memcpy(((char *)&aodst->___length___)+sizeof(int)+destPos*elementsize, ((char *)&aosrc->___length___)+sizeof(int)+srcPos*elementsize, size);
  } else {
    //objects
    int i;
    for(i=0;i<length;i++) {
      struct ___Object___ * ptr=((struct ___Object___**)(((char*) &aosrc->___length___)+sizeof(int)))[i+srcPos];
      //hit an object
      ((struct ___Object___ **)(((char*) &aodst->___length___)+sizeof(int)))[i+destPos]=ptr;
    }
  }
}

void CALL35(___System______arraycopy____L___Object____I_L___Object____I_I, int ___srcPos___, int ___destPos___, int ___length___, struct ___Object___ * ___src___, int ___srcPos___, struct ___Object___ * ___dst___, int  ___destPos___, int ___length___) {
  arraycopy(VAR(___src___), ___srcPos___, VAR(___dst___), ___destPos___, ___length___);
}
#endif

#ifdef D___Runtime______availableProcessors____
int CALL01(___Runtime______availableProcessors____, struct ___Runtime___ * ___this___) {
  printf("Unimplemented Runtime.availableProcessors\n");
  return 24;
}
#endif

#ifdef D___Runtime______freeMemory____
long long CALL01(___Runtime______freeMemory____, struct ___Runtime___ * ___this___) {
  printf("Unimplemented Runtime.freeMemory\n");
  return 1024*1024*1024;
}
#endif

#ifdef D___Runtime______totalMemory____
long long CALL01(___Runtime______totalMemory____, struct ___Runtime___ * ___this___) {
  printf("Unimplemented Runtime.totalMemory\n");
  return 1024*1024*1024;
}
#endif

#ifdef D___Runtime______maxMemory____
long long CALL01(___Runtime______maxMemory____, struct ___Runtime___ * ___this___) {
  printf("Unimplemented Runtime.maxMemory\n");
  return 1024*1024*1024;
}
#endif

void CALL11(___System______exit____I,int ___status___, int ___status___) {
#ifdef TRANSSTATS
#ifndef RECOVERY
  printf("numTransCommit = %d\n", numTransCommit);
  printf("numTransAbort = %d\n", numTransAbort);
  printf("nSoftAbort = %d\n", nSoftAbort);
#endif
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
#ifdef EVENTMONITOR
  dumpdata();
#endif
  exit(___status___);
}

void CALL11(___System______logevent____I,int ___event___, int ___event___) {
#ifdef STMLOG
  event[counter] = ___event___;
  clkticks[counter] = rdtsc();
  counter++;
#endif
  return;
}

void CALL00(___System______logevent____) {
#ifdef STMLOG
  beginClock= rdtsc();
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
    fprintf(fp, "%d %lld %lld\n", event[i], clkticks[i]-beginClock, clkticks[i+1]-beginClock);
  }
  fprintf(fp, "%d %lld\n", event[i], clkticks[i]-beginClock);

  fclose(fp);
#endif
  return;
}

void CALL00(___System______initLog____) {
#ifdef STMLOG
  counter=0;
  int i;
  for(i=0; i<ARRAY_LENGTH; i++) {
    event[i] = 0;
    clkticks[i] = 0;
  }

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

#ifdef D___System______gc____
void CALL00(___System______gc____) {
#if defined(THREADS)||defined(DSTM)||defined(STM)||defined(MLP)
  while (pthread_mutex_trylock(&gclock)!=0) {
    stopforgc((struct garbagelist *)___params___);
    restartaftergc();
  }
#endif

  /* Grow the to heap if necessary */
  {
    INTPTR curr_heapsize=curr_heaptop-curr_heapbase;
    INTPTR to_heapsize=to_heaptop-to_heapbase;

    if (curr_heapsize>to_heapsize) {
      free(to_heapbase);
      to_heapbase=malloc(curr_heapsize);
      if (to_heapbase==NULL) {
	printf("Error Allocating enough memory\n");
	exit(-1);
      }
      to_heaptop=to_heapbase+curr_heapsize;
      to_heapptr=to_heapbase;
    }
  }


  collect((struct garbagelist *)___params___);

  {
  void * tmp=to_heapbase;
  to_heapbase=curr_heapbase;
  curr_heapbase=tmp;

  tmp=to_heaptop;
  to_heaptop=curr_heaptop;
  curr_heaptop=tmp;

  tmp=to_heapptr;
  curr_heapptr=to_heapptr;
  curr_heapgcpoint=((char *) curr_heapbase)+GCPOINT(curr_heaptop-curr_heapbase);
  to_heapptr=to_heapbase;
  bzero(tmp, curr_heaptop-tmp);

  }

#if defined(THREADS)||defined(DSTM)||defined(STM)||defined(MLP)
  pthread_mutex_unlock(&gclock);
#endif
}
#endif

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
#ifdef RECOVERYSTATS
  fflush(stdout);
  fflush(stdout);
#endif
}

#ifdef D___RecoveryStat______printRecoveryStat____ 
#ifdef RECOVERYSTATS
void CALL00(___RecoveryStat______printRecoveryStat____) {
  printRecoveryStat();
}
#else
void CALL00(___RecoveryStat______printRecoveryStat____) {
  printf("No Stat\n");
  fflush(stdout);
}
#endif
#endif

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

#ifdef D___Task______execution____ 
extern void* virtualtable[];
// associated with Task.execution(). finds proper execute method and call it
void CALL01(___Task______execution____,struct ___Task___ * ___this___)
{
  unsigned int oid;
  oid = (unsigned int) VAR(___this___);   // object id
  int type = getObjType(oid);             // object type

#ifdef PRECISE_GC
  int p[] = {1,0 , oid};
  ((void(*) (void *))virtualtable[type*MAXCOUNT + EXECUTEMETHOD])(p);
#else
  // call the proper execute method
  ((void(*) (void *))virtualtable[type*MAXCOUNT + EXECUTEMETHOD])(oid);
#endif
}
#endif

#endif // DSTM

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
#ifdef EVENTMONITOR
  EVLOGEVENT(EV_ENTERBARRIER);
#endif
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
#ifdef EVENTMONITOR
  EVLOGEVENT(EV_EXITBARRIER);
#endif
}
#endif

/* Object allocation function */

#ifdef DSTM
__attribute__((malloc)) void * allocate_newglobal(int type) {
  struct ___Object___ * v=(struct ___Object___ *) transCreateObj(classsize[type]);
  v->type=type;
  //printf("DEBUG %s(), type= %x\n", __func__, type);
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
#ifdef STMARRAY
  struct ___Object___ * v=(struct ___Object___ *) transCreateObj(ptr, classsize[type], 0);
#else
  struct ___Object___ * v=(struct ___Object___ *) transCreateObj(ptr, classsize[type]);
#endif
  ASSIGNUID(v);
  v->type=type;
  v->___objlocation___=v;
  return v;
}

/* Array allocation function */
__attribute__((malloc)) struct ArrayObject * allocate_newarraytrans(void * ptr, int type, int length) {
#ifdef STMARRAY
  int basesize=length*classsize[type];
  //round the base size up
  basesize=(basesize+LOWMASK)&HIGHMASK;
  int numlocks=basesize>>INDEXSHIFT;
  int bookkeepsize=numlocks*2*sizeof(int);
  struct ArrayObject * v=(struct ArrayObject *)transCreateObj(ptr, sizeof(struct ArrayObject)+basesize+bookkeepsize, bookkeepsize);
  unsigned int *intptr=(unsigned int *)(((char *)v)-sizeof(objheader_t));
  for(;numlocks>0;numlocks--) {
    intptr-=2;
    intptr[0]=1;
  }
  v->highindex=-1;
  v->lowindex=MAXARRAYSIZE;
#else
  struct ArrayObject * v=(struct ArrayObject *)transCreateObj(ptr, sizeof(struct ArrayObject)+length*classsize[type]);
#endif
  ASSIGNUID(v);
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
  ASSIGNUID(v);
  initdsmlocks(&tmp->lock);
  tmp->version = 1;
  v->___objlocation___=v;
  v->type = type;
  return v;
}

/* Array allocation function */

__attribute__((malloc)) struct ArrayObject * allocate_newarray(void * ptr, int type, int length) {
#ifdef STMARRAY
  int basesize=length*classsize[type];
  //round the base size up
  basesize=(basesize+LOWMASK)&HIGHMASK;
  int numlocks=basesize>>INDEXSHIFT;
  int bookkeepsize=(numlocks)*2*sizeof(int);
  int *tmpint=mygcmalloc((struct garbagelist *) ptr, sizeof(struct ArrayObject)+basesize+sizeof(objheader_t)+bookkeepsize);
  for(;numlocks>0;numlocks--) {
    tmpint[0]=1;
    tmpint+=2;
  }
  objheader_t *tmp=(objheader_t *)tmpint;
  struct ArrayObject * v=(struct ArrayObject *) &tmp[1];
  v->highindex=-1;
  v->lowindex=MAXARRAYSIZE;
#else
  objheader_t *tmp=mygcmalloc((struct garbagelist *) ptr, sizeof(struct ArrayObject)+length*classsize[type]+sizeof(objheader_t));
  struct ArrayObject * v=(struct ArrayObject *) &tmp[1];
#endif
#ifdef DUALVIEW
  tmp->lock=RW_LOCK_BIAS;
#else
  initdsmlocks(&tmp->lock);
#endif
  tmp->version=1;
  ASSIGNUID(v);
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
#ifdef MLP
__attribute__((malloc)) void * allocate_new(void * ptr, int type) {
  return allocate_new_mlp(ptr, type, 0, 0);
}
__attribute__((malloc)) void * allocate_new_mlp(void * ptr, int type, int oid, int allocsite) {
#else
__attribute__((malloc)) void * allocate_new(void * ptr, int type) {
#endif
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
#ifdef MLP
  v->oid=oid;
  v->allocsite=allocsite;
#endif
  return v;
}

/* Array allocation function */
#ifdef MLP
__attribute__((malloc)) struct ArrayObject * allocate_newarray(void * ptr, int type, int length) {
  return allocate_newarray_mlp(ptr, type, length, 0, 0);
}
 __attribute__((malloc)) struct ArrayObject * allocate_newarray_mlp(void * ptr, int type, int length, int oid, int allocsite) {
#else
__attribute__((malloc)) struct ArrayObject * allocate_newarray(void * ptr, int type, int length) {
#endif
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
#ifdef MLP
  v->oid=oid;
  v->allocsite=allocsite;
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
__attribute__((malloc)) struct ___String___ * NewStringShort(void * ptr, const short *str,int length) {
#else
__attribute__((malloc)) struct ___String___ * NewStringShort(const short *str,int length) {
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
    ((short *)(((char *)&chararray->___length___)+sizeof(int)))[i]=str[i];
  }
  return strobj;
}

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

void failedboundschk(int num) {
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

#ifndef SANDBOX
#ifdef D___System______Assert____Z
 void CALL11(___System______Assert____Z, int ___status___, int ___status___) {
   if (!___status___) {
     printf("Assertion violation\n");
     *((int *)(NULL)); //force stack trace error
   }
 }
#endif
#endif
