#include "runtime.h"
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>

extern int numTransAbort;
extern int numTransCommit;


void transStatsHandler(int sig, siginfo_t* info, void *context) {
#ifdef TRANSSTATS
  printf("numTransCommit = %d\n", numTransCommit);
  printf("numTransAbort = %d\n", numTransAbort);
  exit(0);
#endif
}

void CALL00(___Signal______nativeSigAction____) {
#ifdef TRANSSTATS
  struct sigaction siga;
  siga.sa_handler = NULL;
  siga.sa_flags = SA_SIGINFO;
  siga.sa_sigaction = &transStatsHandler;
  sigemptyset(&siga.sa_mask);
  sigaction(SIGUSR1, &siga, 0);
#endif
}
