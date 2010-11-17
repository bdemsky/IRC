#ifndef RCR_RUNTIME_H
#define RCR_RUNTIME_H

extern __thread struct trQueue * TRqueue;
extern __thread int myWorkerID;

void * workerTR(void *);

#endif
