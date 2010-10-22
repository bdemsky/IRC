#include "trqueue.h"
#include "mlp_runtime.h"
#include "rcr_runtime.h"


void * workerTR(void *x) {
  struct trQueue * queue=(struct trQueue *)x;
  while(1) {
    SESEcommon * tmp;
    do {
      tmp=(SESEcommon *) dequeueTR(queue);
      if (tmp!=NULL)
	break;
      sched_yield();
    } while(1);
  }
  return NULL;
}

__thread SESEstall stallrecord;
