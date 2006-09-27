#ifndef RUNTIME
#define RUNTIME
#include <setjmp.h>
extern jmp_buf error_handler;

void * allocate_new(int type);
struct ArrayObject * allocate_newarray(int type, int length);
struct ___String___ * NewString(const char *str,int length);

void failedboundschk();
void failednullptr();
void abort_task();

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

int hashCodetpd(struct taskparamdescriptor *);
int comparetpd(struct taskparamdescriptor *, struct taskparamdescriptor *);
#endif

#endif
