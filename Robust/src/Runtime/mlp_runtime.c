#include "runtime.h"

#include <stdlib.h>
#include <stdio.h>
#include <assert.h>

#include "mem.h"
#include "Queue.h"
#include "mlp_runtime.h"
#include "workschedule.h"



void* mlpAllocSESErecord( int size ) {
  void* newrec = RUNMALLOC( size );  
  return newrec;
}


void mlpFreeSESErecord( void* seseRecord ) {
  RUNFREE( seseRecord );
}

/*
void mlpInit( int numProcessors, 
	      void(*workFunc)(void*),
	      int argc, char** argv,
	      int maxSESEage ) {  

  // first initialize the work scheduler
  //workScheduleInit( numProcessors, workFunc );

  //workScheduleBegin();
}
*/
/*
void mlpCommonIssueActions( void* seseRecord ) {
  
}


void mlpStall( void* seseRecord ) {
  
}
*/
