#ifndef RCR_RUNTIME_H
#define RCR_RUNTIME_H

extern __thread struct trqueue * TRqueue;

void workerTR(void *);

#endif
