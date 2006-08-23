#ifndef CHECKPOINT_H
#define CHECKPOINT_H
#include "SimpleHash.h"

void ** makecheckpoint(int numparams, void ** pointerarray, struct SimpleHash * forward, struct SimpleHash * reverse);

void restorecheckpoint(int numparams, void ** original, void ** checkpoint, struct SimpleHash *forward, struct SimpleHash * reverse);

void * createcopy(void * orig);

#endif
