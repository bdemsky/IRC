#ifndef RUNTIME
#define RUNTIME
#include <setjmp.h>
extern jmp_buf error_handler;
extern int instructioncount;
extern int failurecount;

#define TAGARRAYINTERVAL 10
#define OBJECTARRAYINTERVAL 10

#define ARRAYSET(array, type, index, value) \
((type *)(&(& array->___length___)[1]))[index]=value

#define ARRAYGET(array, type, index) \
((type *)(&(& array->___length___)[1]))[index]

#ifdef PRECISE_GC
#include "garbage.h"
void * allocate_new(void *, int type);
struct ArrayObject * allocate_newarray(void *, int type, int length);
struct ___String___ * NewString(void *, const char *str,int length);
#else
void * allocate_new(int type);
struct ArrayObject * allocate_newarray(int type, int length);
struct ___String___ * NewString(const char *str,int length);
#endif

void initializeexithandler();
void failedboundschk();
void failednullptr();
void abort_task();
void injectinstructionfailure();
void createstartupobject();

#ifdef PRECISE_GC
#define VAR(name) ___params___->name
#define CALL00(name) name(struct name ## _params * ___params___)
#define CALL01(name, alt) name(struct name ## _params * ___params___)
#define CALL02(name, alt1, alt2) name(struct name ## _params * ___params___)
#define CALL11(name,rest, alt) name(struct name ## _params * ___params___, rest)
#define CALL12(name,rest, alt1, alt2) name(struct name ## _params * ___params___, rest)
#define CALL23(name, rest, rest2, alt1, alt2, alt3) name(struct name ## _params * ___params___, rest, rest2)
#define CALL24(name, rest, rest2, alt1, alt2, alt3, alt4) name(struct name ## _params * ___params___, rest, rest2)
#else
#define VAR(name) name
#define CALL00(name) name()
#define CALL01(name, alt) name(alt)
#define CALL02(name, alt1, alt2) name(alt1, alt2)
#define CALL11(name,rest, alt) name(alt)
#define CALL12(name,rest, alt1, alt2) name(alt1, alt2)
#define CALL23(name, rest, rest2, alt1, alt2, alt3) name(alt1, alt2, alt3)
#define CALL24(name, rest, rest2, alt1, alt2, alt3, alt4) name(alt1, alt2, alt3, alt4)
#endif

#ifdef TASK
#include "SimpleHash.h"
#include "task.h"
#include "structdefs.h"

void flagorand(void * ptr, int ormask, int andmask);
void flagorandinit(void * ptr, int ormask, int andmask);
void executetasks();
void processtasks();

struct tagobjectiterator {
  int istag; /* 0 if object iterator, 1 if tag iterator */
  struct RuntimeIterator it; /* Object iterator */
  struct RuntimeHash * objectset;
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
  struct RuntimeHash * objectset;
  int numberofterms;
  int * intarray;
  int numbertags;
  int * tagarray;
  struct taskdescriptor * task;
  int slot;
  struct tagobjectiterator iterators[MAXTASKPARAMS-1];
};

struct taskparamdescriptor {
  struct taskdescriptor * task;
  int numParameters;
  void ** parameterArray;
};

int hashCodetpd(struct taskparamdescriptor *);
int comparetpd(struct taskparamdescriptor *, struct taskparamdescriptor *);

void toiReset(struct tagobjectiterator * it);
int toiHasNext(struct tagobjectiterator *it, void ** objectarray);
void toiNext(struct tagobjectiterator *it , void ** objectarray);
void processobject(struct parameterwrapper *parameter, int index, struct parameterdescriptor *pd, int *iteratorcount, int * statusarray, int numparams);
void processtags(struct parameterdescriptor *pd, int index, struct parameterwrapper *parameter, int * iteratorcount, int *statusarray, int numparams);
void builditerators(struct taskdescriptor * task, int index, struct parameterwrapper * parameter);
void enqueuetasks(struct parameterwrapper *parameter, struct parameterwrapper *prevptr, struct ___Object___ *ptr);

#endif

#endif
