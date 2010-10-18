#include "trqueue.h"
#include "rcr_runtime.h"
#include "mlp_runtime.h"

__thread struct trQueue * TRqueue;

void workerTR(void *x) {
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
}
