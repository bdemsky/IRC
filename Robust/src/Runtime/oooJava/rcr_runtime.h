#ifndef RCR_RUNTIME_H
#define RCR_RUNTIME_H

extern __thread struct trQueue * TRqueue;

void * workerTR(void *);

extern __thread SESEstall stallrecord;
#endif
