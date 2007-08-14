#ifndef LOCALOBJECT_H
#include LOCALOBJECT_H
#include "structdefs.h"
void REVERT_OBJ(struct ___Object___ *);
#define COMMIT_OBJ(obj) obj->localcopy=NULL

#ifdef PRECISE_GC
void COPY_OBJ(struct garbagelist * gl, struct ___Object___ *obj);
#else
void COPY_OBJ(struct ___Object___ *obj);
#endif
#endif
