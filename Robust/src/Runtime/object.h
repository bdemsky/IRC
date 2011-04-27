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
#ifdef D___Object______MonitorEnter____
void CALL01(___Object______MonitorEnter____, struct ___Object___ * ___this___);
#endif
#ifdef D___Object______MonitorExit____
void CALL01(___Object______MonitorExit____, struct ___Object___ * ___this___);
#endif
#ifdef D___Object______notify____
void CALL01(___Object______notify____, struct ___Object___ * ___this___);
#endif
#ifdef D___Object______notifyAll____
void CALL01(___Object______notifyAll____, struct ___Object___ * ___this___);
#endif
#ifdef D___Object______wait____
void CALL01(___Object______wait____, struct ___Object___ * ___this___);
#endif
#endif
