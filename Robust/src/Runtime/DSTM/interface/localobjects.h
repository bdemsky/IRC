#ifndef LOCALOBJECT_H
#define LOCALOBJECT_H
#include "structdefs.h"
#include "garbage.h"
void REVERT_OBJ(struct ___Object___ *);
#define COMMIT_OBJ(obj) obj->___localcopy___=NULL

#ifdef PRECISE_GC
void COPY_OBJ(struct garbagelist * gl, struct ___Object___ *obj);
#else
void COPY_OBJ(struct ___Object___ *obj);
#endif
#endif
