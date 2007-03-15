#ifndef RUNTIME
#define RUNTIME
#include <setjmp.h>
extern jmp_buf error_handler;
extern int instructioncount;
extern int failurecount;

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
void flagorand(void * ptr, int ormask, int andmask);
void flagorandinit(void * ptr, int ormask, int andmask);
void flagbody(void *ptr, int flag);
void executetasks();
void processtasks();

struct parameterwrapper {
  struct parameterwrapper *next;
  struct RuntimeHash * objectset;
  int numberofterms;
  int * intarray;
  struct taskdescriptor * task;
};

struct taskparamdescriptor {
  struct taskdescriptor * task;
  int numParameters;
  void ** parameterArray;
};

struct tpdlist {
  struct taskparamdescriptor * task;
  struct tpdlist * next;
};

int hashCodetpd(struct taskparamdescriptor *);
int comparetpd(struct taskparamdescriptor *, struct taskparamdescriptor *);
#endif

#endif
