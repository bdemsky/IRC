#include "runtime.h"
#include "structdefs.h"
#include <string.h>

extern int classsize[];
#ifdef BOEHM_GC
#include "gc.h"
#define FREEMALLOC(x) GC_malloc(x)
#else
#define FREEMALLOC(x) calloc(1,x)
#endif

#ifdef TASK
#include "tasks.h"

int main(int argc, char **argv) {
  int i;
  /* Allocate startup object */
  struct ___StartupObject___ *startupobject=(struct ___StartupObject___*) allocate_new(STARTUPTYPE);
  /* Set flags */
  flagorand(startupobject,1,0xFFFFFFFF);

  /* Build array of strings */
  struct ArrayObject * stringarray=allocate_newarray(STRINGARRAYTYPE, argc); 
  startupobject->___parameters___=stringarray;

  for(i=0;i<argc;i++) {
    int length=strlen(argv[i]);
    struct ___String___ *newstring=NewString(argv[i],length);
    ((void **)(((char *)& stringarray->___length___)+sizeof(int)))[i]=newstring;
  }
  processtasks();
}

void flagorand(void * ptr, int ormask, int andmask) {
  ((int *)ptr)[1]|=ormask;
  ((int *)ptr)[1]&=andmask;
}

void processtasks() {
  int i;

  for(i=0;i<numtasks;i++) {
    struct taskdescriptor * task=taskarray[i];
    int j;

    for(j=0;j<task->numParameters;j++) {
      struct parameterdescriptor *param=task->descriptorarray[j];
      
    }
  }

}
#endif

int ___Object______hashcode____(struct ___Object___ * ___this___) {
  return (int) ___this___;
}

void ___System______printString____L___String___(struct ___String___ * s) {
    struct ArrayObject * chararray=s->___string___;
    int i;
    for(i=0;i<chararray->___length___;i++) {
	short s= ((short *)(((char *)& chararray->___length___)+sizeof(int)))[i];
	putchar(s);
    }
}

void * allocate_new(int type) {
  void * v=FREEMALLOC(classsize[type]);
  *((int *)v)=type;
  return v;
}

struct ArrayObject * allocate_newarray(int type, int length) {
  struct ArrayObject * v=FREEMALLOC(sizeof(struct ArrayObject)+length*classsize[type]);
  v->type=type;
  v->___length___=length;
  return v;
}

struct ___String___ * NewString(char *str,int length) {
  struct ArrayObject * chararray=allocate_newarray(CHARARRAYTYPE, length);
  struct ___String___ * strobj=allocate_new(STRINGTYPE);
  int i;
  strobj->___string___=chararray;
  for(i=0;i<length;i++) {
    ((short *)(((char *)& chararray->___length___)+sizeof(int)))[i]=(short)str[i];  }
  return strobj;
}

void failedboundschk() {
  printf("Array out of bounds\n");
  exit(-1);
}

void failednullptr() {
  printf("Dereferenced a null pointer\n");
  exit(-1);
}
