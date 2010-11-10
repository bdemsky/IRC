#include "trqueue.h"
#include "coreprof/coreprof.h"
#include "mlp_runtime.h"
#include "rcr_runtime.h"
#include "hashStructure.h"
#include "structdefs.h"
#include "RuntimeConflictResolver.h"


void * workerTR(void *x) {
  struct trQueue * queue=(struct trQueue *)x;
  allHashStructures=queue->allHashStructures;

  CP_CREATE();

  while(1) {
    SESEcommon * tmp;
    do {
      tmp=(SESEcommon *) dequeueTR(queue);
      if (tmp!=NULL) {

#ifdef CP_EVENTID_RCR_TRAVERSE
    CP_LOGEVENT( CP_EVENTID_RCR_TRAVERSE, CP_EVENTTYPE_BEGIN );
#endif
	tasktraverse(tmp);

#ifdef CP_EVENTID_RCR_TRAVERSE
    CP_LOGEVENT( CP_EVENTID_RCR_TRAVERSE, CP_EVENTTYPE_END );
#endif

      } else {
	sched_yield();
      }
    } while(1);
  }

  CP_EXIT();

  return NULL;
}

