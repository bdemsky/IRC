#ifndef RUNTIME
#define RUNTIME


void * allocate_new(int type);
struct ArrayObject * allocate_newarray(int type, int length);
struct ___String___ * NewString(char *str,int length);

void failedboundschk();
void failednullptr();

#ifdef TASK
#include "Queue.h"
void flagorand(void * ptr, int ormask, int andmask);
void executetasks();
void processtasks();

struct parameterwrapper {
  struct parameterwrapper *next;
  struct Queue * queue;
  int numberofterms;
  int * intarray;
  struct taskdescriptor * task;
}
#endif

#endif
