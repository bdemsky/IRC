#include "runtime.h"
#include "structdefs.h"
extern int classsize[];

int ___Object______hashcode____(struct ___Object___ * ___this___) {
  return (int) ___this___;
}

void ___System______printInt____I(int x) {
  printf("%d\n",x);
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
  void * v=calloc(1,classsize[type]);
  *((int *)v)=type;
  return v;
}

void * allocate_newarray(int type, int length) {
  void * v=calloc(1,sizeof(struct ArrayObject)+length*classsize[type]);
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
