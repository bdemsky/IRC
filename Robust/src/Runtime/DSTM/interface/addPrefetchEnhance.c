#include "dstm.h"
#include "addPrefetchEnhance.h"

extern int numprefetchsites;
//pfcstats_t evalPrefetch[numprefetchsites]; //Global array for all prefetch sites in the executable
extern pfcstats_t *evalPrefetch;

pfcstats_t *initPrefetchStats() {
  pfcstats_t *ptr;
  if((ptr = calloc(numprefetchsites, sizeof(pfcstats_t))) == NULL) {
    printf("%s() Calloc error in %s at line %d\n", __func__, __FILE__, __LINE__);
    return NULL;
  }
  int i;
  /* Enable prefetching at the beginning */
  for(i=0; i<numprefetchsites; i++) {
    ptr[i].operMode = 1;
    ptr[i].callcount = 0;
    ptr[i].retrycount = RETRYINTERVAL; //N
    ptr[i].uselesscount = SHUTDOWNINTERVAL; //M
  }
  return ptr;
}

int getRetryCount(int siteid) {
  return evalPrefetch[siteid].retrycount;
}

int getUselessCount(int siteid) {
  return evalPrefetch[siteid].uselesscount;
}

char getOperationMode(int siteid) {
  return evalPrefetch[siteid].operMode;
}

void handleDynPrefetching(int numLocal, int ntuples, int siteid) {
  if(numLocal < ntuples) {
    /* prefetch not found locally(miss in cache) */
    evalPrefetch[siteid].operMode = 1;
    evalPrefetch[siteid].uselesscount = SHUTDOWNINTERVAL;
  } else {
    if(getOperationMode(siteid) != 0) {
      evalPrefetch[siteid].uselesscount--;
      if(evalPrefetch[siteid].uselesscount <= 0)
        evalPrefetch[siteid].operMode = 0;
    }
  }
}
