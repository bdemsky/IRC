#ifndef RUNTIME
#define RUNTIME
#include <stdlib.h>
#ifndef MULTICORE
#include <setjmp.h>
extern jmp_buf error_handler;
extern int instructioncount;
extern int failurecount;
#endif
#ifdef DSTM
#ifdef RECOVERY
#include "DSTM/interface_recovery/dstm.h"
#else
#include "DSTM/interface/dstm.h"
#endif
#endif

#ifdef AFFINITY
void set_affinity();
#endif

#ifndef INTPTR
#ifdef BIT64
#define INTPTR long
#define INTPTRSHIFT 3
#else
#define INTPTR int
#define INTPTRSHIFT 2
#endif
#endif

#ifndef CACHELINESIZE
// The L1 and L2 cache line size for the
// AMD Opteron 6168 (dc-10) is 64 bytes.  Source:
// http://www.cs.virginia.edu/~skadron/cs451/opteron/opteron.ppt
#define CACHELINESIZE 64
#endif

extern void * curr_heapbase;
extern void * curr_heaptop;

#define likely(x) __builtin_expect((x),1)
#define unlikely(x) __builtin_expect((x),0)


#define TAGARRAYINTERVAL 10
#define OBJECTARRAYINTERVAL 10

#define ARRAYSET(array, type, index, value) \
  ((type *)(&(&array->___length___)[1]))[index]=value

#define ARRAYGET(array, type, index) \
  ((type *)(&(&array->___length___)[1]))[index]

#ifdef OPTIONAL
#define OPTARG(x) , x
#else
#define OPTARG(x)
#endif

#ifdef DSTM
__attribute__((malloc)) void * allocate_newglobal(int type);
__attribute__((malloc)) struct ArrayObject * allocate_newarrayglobal(int type, int length);
#endif

#ifdef STM
__attribute__((malloc)) void * allocate_newtrans(void * ptr, int type);
__attribute__((malloc)) struct ArrayObject * allocate_newarraytrans(void * ptr, int type, int length);
#endif

#ifdef PRECISE_GC
#include "garbage.h"
#ifdef MLP
__attribute__((malloc)) void * allocate_new_mlp(void *, int type, int oid, int allocsite);
__attribute__((malloc)) void * allocate_new(void *, int type);
__attribute__((malloc)) struct ArrayObject * allocate_newarray_mlp(void *, int type, int length, int oid, int allocsite);
__attribute__((malloc)) struct ArrayObject * allocate_newarray(void * ptr, int type, int length);
#else
__attribute__((malloc)) void * allocate_new(void *, int type);
__attribute__((malloc)) struct ArrayObject * allocate_newarray(void *, int type, int length);
#endif
__attribute__((malloc)) struct ___String___ * NewString(void *, const char *str,int length);
__attribute__((malloc)) struct ___String___ * NewStringShort(void *, const short *str,int length);
__attribute__((malloc)) struct ___TagDescriptor___ * allocate_tag(void *ptr, int index);
#elif defined MULTICORE_GC
__attribute__((malloc)) void * allocate_new(void *, int type);
__attribute__((malloc)) struct ArrayObject * allocate_newarray(void *, int type, int length);
__attribute__((malloc)) struct ___String___ * NewString(void *, const char *str,int length);
__attribute__((malloc)) struct ___String___ * NewStringShort(void *, const short *str,int length);
__attribute__((malloc)) struct ___TagDescriptor___ * allocate_tag(void *ptr, int index);
#else
__attribute__((malloc)) void * allocate_new(int type);
__attribute__((malloc)) struct ArrayObject * allocate_newarray(int type, int length);
__attribute__((malloc)) struct ___String___ * NewString(const char *str,int length);
__attribute__((malloc)) struct ___String___ * NewStringShort(const short *str,int length);
__attribute__((malloc)) struct ___TagDescriptor___ * allocate_tag(int index);
#endif



void initializeexithandler();
void failedboundschk(int num);
void failednullptr(void * stackptr);
void abort_task();
void injectinstructionfailure();
#ifdef MULTICORE
void createstartupobject(int argc, char ** argv);
#else
void createstartupobject();
#endif

#ifdef PRECISE_GC
#define VAR(name) ___params___->name
#define CALL00(name) name(struct name ## _params * ___params___)
#define CALL01(name, alt) name(struct name ## _params * ___params___)
#define CALL02(name, alt1, alt2) name(struct name ## _params * ___params___)
#define CALL11(name,rest, alt) name(struct name ## _params * ___params___, rest)
#define CALL12(name,rest, alt1, alt2) name(struct name ## _params * ___params___, rest)
#define CALL22(name, rest, rest2, alt1, alt2) name(struct name ## _params * ___params___, rest, rest2)
#define CALL23(name, rest, rest2, alt1, alt2, alt3) name(struct name ## _params * ___params___, rest, rest2)
#define CALL24(name, rest, rest2, alt1, alt2, alt3, alt4) name(struct name ## _params * ___params___, rest, rest2)
#define CALL34(name, rest, rest2, rest3, alt1, alt2, alt3, alt4) name(struct name ## _params * ___params___, rest, rest2, rest3)
#define CALL35(name, rest, rest2, rest3, alt1, alt2, alt3, alt4, alt5) name(struct name ## _params * ___params___, rest, rest2, rest3)
#elif defined MULTICORE_GC
#define VAR(name) ___params___->name
#define CALL00(name) name(struct name ## _params * ___params___)
#define CALL01(name, alt) name(struct name ## _params * ___params___)
#define CALL02(name, alt1, alt2) name(struct name ## _params * ___params___)
#define CALL11(name,rest, alt) name(struct name ## _params * ___params___, rest)
#define CALL12(name,rest, alt1, alt2) name(struct name ## _params * ___params___, rest)
#define CALL22(name, rest, rest2, alt1, alt2) name(struct name ## _params * ___params___, rest, rest2)
#define CALL23(name, rest, rest2, alt1, alt2, alt3) name(struct name ## _params * ___params___, rest, rest2)
#define CALL24(name, rest, rest2, alt1, alt2, alt3, alt4) name(struct name ## _params * ___params___, rest, rest2)
#define CALL34(name, rest, rest2, rest3, alt1, alt2, alt3, alt4) name(struct name ## _params * ___params___, rest, rest2, rest3)
#define CALL35(name, rest, rest2, rest3, alt1, alt2, alt3, alt4, alt5) name(struct name ## _params * ___params___, rest, rest2, rest3)
#else
#define VAR(name) name
#define CALL00(name) name()
#define CALL01(name, alt) name(alt)
#define CALL02(name, alt1, alt2) name(alt1, alt2)
#define CALL11(name,rest, alt) name(alt)
#define CALL12(name,rest, alt1, alt2) name(alt1, alt2)
#define CALL22(name, rest, rest2, alt1, alt2) name(alt1, alt2)
#define CALL23(name, rest, rest2, alt1, alt2, alt3) name(alt1, alt2, alt3)
#define CALL24(name, rest, rest2, alt1, alt2, alt3, alt4) name(alt1, alt2, alt3, alt4)
#define CALL34(name, rest, rest2, rest3, alt1, alt2, alt3, alt4) name(alt1, alt2, alt3, alt4)
#define CALL35(name, rest, rest2, rest3, alt1, alt2, alt3, alt4, alt5) name(alt1, alt2, alt3, alt4, alt5)
#endif

#ifdef MULTICORE
#include "SimpleHash.h"
inline void run(int argc, char** argv);
int receiveObject_I();
void * smemalloc_I(int coren, int size, int * allocsize);
#ifdef MULTICORE_GC
inline void setupsmemmode(void);
#endif
#endif

#ifdef THREADS
#define MAXLOCKS 256

struct lockpair {
  struct ___Object___ *object;
  int islastlock;
};

struct lockvector {
  int index;
  struct lockpair locks[MAXLOCKS];
};

#ifndef MAC
extern __thread struct lockvector lvector;
extern __thread int mythreadid;
#endif
#endif

#ifdef TASK
#ifndef MULTICORE
#include "chash.h"
#include "ObjectHash.h"
#include "structdefs.h"
#endif
#include "task.h"
#ifdef OPTIONAL
#include "optionalstruct.h"
#endif


#ifdef OPTIONAL
struct failedtasklist {
  struct taskdescriptor *task;
  int index;
  int numflags;
  int *flags;
  struct failedtasklist *next;
};
#endif

#ifdef MULTICORE
struct transObjInfo {
  void * objptr;
  int targetcore;
  int * queues;
  int length;
};
#endif

#ifdef FASTCHECK
extern struct ___Object___ * ___fcrevert___;
#endif

#ifdef MULTICORE
void flagorand(void * ptr, int ormask, int andmask, struct parameterwrapper ** queues, int length);
void flagorandinit(void * ptr, int ormask, int andmask);
void enqueueObject(void * ptr, struct parameterwrapper ** queues,int length);
#ifdef PROFILE
inline void setTaskExitIndex(int index);
inline void addNewObjInfo(void * nobj);
#endif
int * getAliasLock(void ** ptrs, int length, struct RuntimeHash * tbl);
void addAliasLock(void * ptr, int lock);
#else
void flagorand(void * ptr, int ormask, int andmask);
void flagorandinit(void * ptr, int ormask, int andmask);
void enqueueObject(void * ptr);
#endif
void executetasks();
void processtasks();

#ifndef MULTICORE
struct tagobjectiterator {
  int istag; /* 0 if object iterator, 1 if tag iterator */
  struct ObjectIterator it; /* Object iterator */
  struct ObjectHash * objectset;
#ifdef OPTIONAL
  int failedstate;
#endif
  int slot;
  int tagobjindex; /* Index for tag or object depending on use */
  /*if tag we have an object binding */
  int tagid;
  int tagobjectslot;
  /*if object, we may have one or more tag bindings */
  int numtags;
  int tagbindings[MAXTASKPARAMS-1]; /* list slots */
};

struct parameterwrapper {
  struct parameterwrapper *next;
  struct ObjectHash * objectset;
  int numberofterms;
  int * intarray;
  int numbertags;
  int * tagarray;
  struct taskdescriptor * task;
  int slot;
  struct tagobjectiterator iterators[MAXTASKPARAMS-1];
};
#endif

struct taskparamdescriptor {
  struct taskdescriptor * task;
  int numParameters;
  void ** parameterArray;
#ifdef OPTIONAL
  int * failed;
#endif
};

int hashCodetpd(struct taskparamdescriptor *);
int comparetpd(struct taskparamdescriptor *, struct taskparamdescriptor *);

void toiReset(struct tagobjectiterator * it);
int toiHasNext(struct tagobjectiterator *it, void ** objectarray OPTARG(int * failed));
void toiNext(struct tagobjectiterator *it, void ** objectarray OPTARG(int * failed));
void processobject(struct parameterwrapper *parameter, int index, struct parameterdescriptor *pd, int *iteratorcount, int * statusarray, int numparams);
void processtags(struct parameterdescriptor *pd, int index, struct parameterwrapper *parameter, int * iteratorcount, int *statusarray, int numparams);
void builditerators(struct taskdescriptor * task, int index, struct parameterwrapper * parameter);
int enqueuetasks(struct parameterwrapper *parameter, struct parameterwrapper *prevptr, struct ___Object___ *ptr, int * enterflags, int numenterflags);

#endif

#if defined(__i386__)

static __inline__ unsigned long long rdtsc(void) {
  unsigned long long int x;
  __asm__ volatile (".byte 0x0f, 0x31" : "=A" (x));
  return x;
}
#elif defined(__x86_64__)

static __inline__ unsigned long long rdtsc(void) {
  unsigned hi, lo;
  __asm__ __volatile__ ("rdtsc" : "=a" (lo), "=d" (hi));
  return ( (unsigned long long)lo)|( ((unsigned long long)hi)<<32 );
}

#elif defined(__powerpc__)

typedef unsigned long long int unsigned long long;

static __inline__ unsigned long long rdtsc(void) {
  unsigned long long int result=0;
  unsigned long int upper, lower,tmp;
  __asm__ volatile (
    "0:                  \n"
    "\tmftbu   %0           \n"
    "\tmftb    %1           \n"
    "\tmftbu   %2           \n"
    "\tcmpw    %2,%0        \n"
    "\tbne     0b         \n"
    : "=r" (upper),"=r" (lower),"=r" (tmp)
    );
  result = upper;
  result = result<<32;
  result = result|lower;

  return(result);
}
#endif


#endif
