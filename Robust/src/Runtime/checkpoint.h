#ifndef CHECKPOINT_H
#define CHECKPOINT_H
#include "chash.h"

void ** makecheckpoint(int numparams, void ** pointerarray, struct ctable * forward, struct ctable * reverse);

void restorecheckpoint(int numparams, void ** original, void ** checkpoint, struct ctable *forward, struct ctable * reverse);

void * createcopy(void * orig);
void freemalloc();
#endif
