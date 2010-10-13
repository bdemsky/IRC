#ifndef RCR_RUNTIME_H
#define RCR_RUNTIME_H

extern __thread struct trqueue * TRqueue;

void workerTR(void *);

#define RCRSIZE 32

struct rcrRecord {
  int count;
  int index;
  int flag;
  int array[RCRSIZE];
};
#endif
