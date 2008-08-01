#include "dstm.h"
#include "addPrefetchEnhance.h"
#include <signal.h>

extern int numTransAbort;
extern int numTransCommit;
extern int numprefetchsites;
void handle();
extern pfcstats_t *evalPrefetch;

void transStatsHandler(int sig, siginfo_t* info, void *context) {
#ifdef TRANSSTATS
  printf("******  Transaction Stats   ******\n");
  printf("numTransAbort = %d\n", numTransAbort);
  printf("numTransCommit = %d\n", numTransCommit);
  int i;
  for(i=0; i<numprefetchsites; i++) {
    printf("siteid = %d,  callCount = %d\n", i, evalPrefetch[i].callcount);
  }
  exit(0);
#endif
}

void handle() {
#ifdef TRANSSTATS
  struct sigaction siga;
  siga.sa_handler = NULL;
  siga.sa_flags = SA_SIGINFO;
  siga.sa_flags = 0;
  siga.sa_sigaction = &transStatsHandler;
  sigemptyset(&siga.sa_mask);
  sigaction(SIGUSR1, &siga, 0);
#endif
}
