#ifndef RUNTIME
#define RUNTIME
#include <setjmp.h>
extern jmp_buf error_handler;
extern int instructioncount;

void * allocate_new(int type);
struct ArrayObject * allocate_newarray(int type, int length);
struct ___String___ * NewString(const char *str,int length);

void failedboundschk();
void failednullptr();
void abort_task();
void injectinstructionfailure();

#ifdef PRECISE_GC
#define VAR(name) ___params___->name
#define CALL01(name, alt) name(struct name ## _params * ___params___)
#define CALL02(name, alt1, alt2) name(struct name ## _params * ___params___)
#define CALL11(name,rest, alt) name(struct name ## _params * ___params___, rest)
#define CALL12(name,rest, alt1, alt2) name(struct name ## _params * ___params___, rest)
#define CALL21(name,rest, rest2, alt) name(struct name ## _params * ___params___, rest, rest2)
#define CALL23(name, rest, rest2, alt1, alt2, alt3) name(struct name ## _params * ___params___, rest, rest2)
#else
#define VAR(name) name
#define CALL01(name, alt) name(alt)
#define CALL02(name, alt1, alt2) name(alt1, alt2)
#define CALL11(name,rest, alt) name(alt)
#define CALL12(name,rest, alt1, alt2) name(alt1, alt2)
#define CALL21(name,rest, rest2, alt) name(alt)
#define CALL23(name, rest, rest2, alt1, alt2, alt3) name(alt1, alt2, alt3)
#endif

#ifdef TASK
#include "SimpleHash.h"
#include "task.h"
void flagorand(void * ptr, int ormask, int andmask);
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
