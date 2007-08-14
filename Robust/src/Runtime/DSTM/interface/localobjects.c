#include "localobjects.h"
void REVERT_OBJ(struct ___Object___ * obj) {
  int type=((int *)obj)[0];
  struct ___Object___ * copyobj=obj->localcopy;
  if (type<NUMCLASSES) {
    /* We have a normal object */
    int size=classsize[type];
    memcpy(obj, copyobj, size);
  } else {
    /* We have an array */
    struct ArrayObject *ao=(struct ArrayObject *)orig;
    int elementsize=classsize[type];
    int length=ao->___length___;
    int size=sizeof(struct ArrayObject)+length*elementsize;
    memcpy(obj, copyobj, size);
  }
}

#ifdef PRECISE_GC
void COPY_OBJ(struct garbagelist * gl, struct ___Object___ *obj) {
#else
void COPY_OBJ(struct ___Object___ *obj) {
#endif
  int type=((int *)obj)[0];
  if (type<NUMCLASSES) {
    /* We have a normal object */
    int size=classsize[type];
#ifdef PRECISE_GC
    int ptrarray[]={1, (int) gl, (int) obj};
    struct ___Object___ * newobj=mygcmalloc(ptrarray, size);
#else
    struct ___Object___ * newobj=FREEMALLOC(size);
#endif
    memcpy(newobj, (struct ___Object___ *) ptrarray[1], size);
    ((struct ___Object___*)ptrarray[1])->localcopy=newobj;
  } else {
    /* We have an array */
    struct ArrayObject *ao=(struct ArrayObject *)orig;
    int elementsize=classsize[type];
    int length=ao->___length___;
    int size=sizeof(struct ArrayObject)+length*elementsize;
#ifdef PRECISE_GC
    int ptrarray[]={1, (int) gl, (int) obj};
    struct ___Object___ * newobj=mygcmalloc(ptrarray, size);
#else
    struct ___Object___ * newobj=FREEMALLOC(size);
#endif
    ((struct ___Object___*)ptrarray[1])->localcopy=newobj;
}
