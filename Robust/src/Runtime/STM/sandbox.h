#ifndef SANDBOX_H
#define SANDBOX_H

#include <setjmp.h>
#include <signal.h>
extern __thread jmp_buf aborttrans;
extern __thread int abortenabled;
extern __thread int* counter_reset_pointer;
extern __thread int transaction_check_counter;
void checkObjects();
#define LOW_CHECK_FREQUENCY 1000000
#define HIGH_CHECK_FREQUENCY 100000
int checktrans();
void errorhandler(int sig, struct sigcontext ctx);

#endif


