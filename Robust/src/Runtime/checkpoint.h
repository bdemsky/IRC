#ifndef CHECKPOINT_H
#define CHECKPOINT_H
#include "SimpleHash.h"

void ** makecheckpoint(int numparams, void ** pointerarray, struct RuntimeHash * forward, struct RuntimeHash * reverse);

void restorecheckpoint(int numparams, void ** original, void ** checkpoint, struct RuntimeHash *forward, struct RuntimeHash * reverse);

void * createcopy(void * orig);

#endif
