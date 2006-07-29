#include "runtime.h"
#include "structdefs.h"
extern int classsize[];
#ifdef BOEHM_GC
#include "gc.h"
#define FREEMALLOC(x) GC_malloc(x)
#else
#define FREEMALLOC(x) calloc(1,x)
#endif

int ___Object______hashcode____(struct ___Object___ * ___this___) {
  return (int) ___this___;
}

/*void ___System______printInt____I(int x) {
  printf("%d",x);
  }*/

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

void * allocate_newarray(int type, int length) {
  void * v=FREEMALLOC(sizeof(struct ArrayObject)+length*classsize[type]);
  *((int *)v)=type;
  ((struct ArrayObject *)v)->___length___=length;
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
