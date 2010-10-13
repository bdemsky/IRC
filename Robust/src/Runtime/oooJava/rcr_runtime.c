#include "rcr_runtime.h"
#include "mlp_runtime.h"

void workerTR(void *x) {
  struct trQueue * queue=(struct trQueue *)x;
  while(true) {
    SESEcommon * tmp;
    do {
      tmp=(SESEcommon *) dequeueTR(TRqueue);
      if (tmp!=NULL)
	break;
      sched_yield();
    } while(1);
    
    
  }
}
