#include "trqueue.h"
#include "mlp_runtime.h"
#include "rcr_runtime.h"
#include "structdefs.h"
#include "RuntimeConflictResolver.h"

void * workerTR(void *x) {
  struct trQueue * queue=(struct trQueue *)x;
  while(1) {
    SESEcommon * tmp;
    do {
      tmp=(SESEcommon *) dequeueTR(queue);
      if (tmp!=NULL) {
	tasktraverse(tmp);
#ifndef OOO_DISABLE_TASKMEMPOOL
	RELEASE_REFERENCE_TO(tmp);
#endif
      } else {
	sched_yield();
      }
    } while(1);
  }
  return NULL;
}

__thread SESEstall stallrecord;
