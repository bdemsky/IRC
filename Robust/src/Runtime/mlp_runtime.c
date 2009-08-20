#include "runtime.h"

#include <stdlib.h>
#include <stdio.h>
#include <assert.h>

#include "mem.h"
#include "Queue.h"
#include "mlp_runtime.h"
#include "workschedule.h"


__thread struct Queue* seseCallStack;


void* mlpAllocSESErecord( int size ) {
  void* newrec = RUNMALLOC( size );  
  return newrec;
}


void mlpFreeSESErecord( void* seseRecord ) {
  RUNFREE( seseRecord );
}
