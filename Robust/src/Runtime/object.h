#ifndef OBJECT_H
#define OBJECT_H
#include "runtime.h"
#include "structdefs.h"
#include "methodheaders.h"
#ifdef D___Object______nativehashCode____
int CALL01(___Object______nativehashCode____, struct ___Object___ * ___this___);
#endif 
#ifdef D___Object______hashCode____
int CALL01(___Object______hashCode____, struct ___Object___ * ___this___); 
#endif
int CALL01(___Object______getType____, struct ___Object___ * ___this___);
#ifdef THREADS
int CALL01(___Object______MonitorEnter____, struct ___Object___ * ___this___);
int CALL01(___Object______MonitorExit____, struct ___Object___ * ___this___);
#endif
#endif
