#ifndef _ADDPREFETCHENHANCE_H_
#define _ADDPREFETCHENHANCE_H_

typedef struct prefetchCountStats {
  int retrycount;    /* keeps track of when to retry and check if we can turn on this prefetch site */ 
  int uselesscount; /* keeps track of how long was the prefetching at site useles */ 
  char operMode; /* 1 = on , 0 = off */
  int callcount;
} pfcstats_t;

pfcstats_t *initPrefetchStats();
int getRetryCount(int siteid);
int getUselessCount(int siteid);
char getOperationMode(int);
void handleDynPrefetching(int, int, int);

#endif
