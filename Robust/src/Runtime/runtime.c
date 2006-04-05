#include "runtime.h"
#include "structdefs.h"
extern int classsize[];

int ___Object______hashcode____(struct ___Object___ * ___this___) {
  return (int) ___this___;
}

void ___System______printInt____I(int x) {
  printf("%d\n",x);
}

void * allocate_new(int type) {
  void * v=calloc(1,classsize[type]);
  *((int *)v)=type;
  return v;
}
