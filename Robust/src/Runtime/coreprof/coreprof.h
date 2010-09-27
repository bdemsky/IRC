#ifndef COREPROF_H
#define COREPROF_H


#ifndef COREPROF
// Core Prof turned off

#define CP_LOGEVENT( eventID, eventType ) ;
#define CP_CREATE()                       ;
#define CP_EXIT()                         ; 
#define CP_DUMP()                         ;


#else
// Core Prof defined

#include <stdlib.h>
#include "runtime.h"

#ifndef CP_MAXEVENTWORDS
#define CP_MAXEVENTWORDS (1024*128)
#endif


// MASK should be enough bits to mask
// the values of the following event types
// and BASESHIFT is for shifting IDs
// past the type bits
#define CP_EVENTTYPE_BEGIN  0x1
#define CP_EVENTTYPE_END    0x2
#define CP_EVENTTYPE_ONEOFF 0x3

#define CP_EVENT_MASK       3
#define CP_EVENT_BASESHIFT  8


// Event IDs, only those enabled explicitly as a build option
// will be defined and included in the compilation
#ifdef cpe_main
#define CP_EVENTID_MAIN               0x04
#endif

#ifdef cpe_runmalloc
#define CP_EVENTID_RUNMALLOC          0x10
#endif

#ifdef cpe_runfree
#define CP_EVENTID_RUNFREE            0x11
#endif

#ifdef cpe_count_poolalloc
#define CP_EVENTID_COUNT_POOLALLOC    0x15
#endif

#ifdef cpe_count_poolreuse
#define CP_EVENTID_COUNT_POOLREUSE    0x16
#endif

#ifdef cpe_workschedgrab
#define CP_EVENTID_WORKSCHEDGRAB      0x20
#endif

#ifdef cpe_taskdispatch
#define CP_EVENTID_TASKDISPATCH       0x30
#endif

#ifdef cpe_taskexecute
#define CP_EVENTID_TASKEXECUTE        0x31
#endif

#ifdef cpe_taskretire
#define CP_EVENTID_TASKRETIRE         0x32
#endif

#ifdef cpe_taskstallvar
#define CP_EVENTID_TASKSTALLVAR       0x40
#endif

#ifdef cpe_taskstallmem
#define CP_EVENTID_TASKSTALLMEM       0x41
#endif


// Note: application-specific events (assigned
// during code gen) start at 0x200



extern __thread int                     cp_threadnum;
extern __thread struct coreprofmonitor* cp_monitor;
extern          struct coreprofmonitor* cp_monitorList;


struct coreprofmonitor {
  struct coreprofmonitor* next;
  
  // index for next unused word in the following array;
  // individual events may use a variable number of
  // words to store information
  int          numWords;
  unsigned int data[CP_MAXEVENTWORDS];
};


#ifndef COREPROF_CHECKOVERFLOW
#define CP_CHECKOVERFLOW ;
#else
#define CP_CHECKOVERFLOW if \
  ( cp_monitor->numWords >= CP_MAXEVENTWORDS ) \
  { cp_reportOverflow(); }
#endif


#define CP_LOGEVENT( eventID, eventType ) { \
  CP_CHECKOVERFLOW; \
  cp_monitor->data[cp_monitor->numWords] = \
    ((eventID << CP_EVENT_BASESHIFT) | eventType); \
  cp_monitor->numWords += 1; \
  CP_LOGTIME; \
}


#define CP_LOGTIME CP_CHECKOVERFLOW; \
  *((long long *)&cp_monitor->data[cp_monitor->numWords]) = rdtsc(); \
  cp_monitor->numWords += 2;



#define CP_CREATE() cp_create();
#define CP_EXIT()   cp_exit();
#define CP_DUMP()   cp_dump();

void cp_create();
void cp_exit();
void cp_dump();
void cp_reportOverflow();


static inline void* cp_calloc( int size ) {
#ifdef CP_EVENTID_RUNMALLOC
    CP_LOGEVENT( CP_EVENTID_RUNMALLOC, CP_EVENTTYPE_BEGIN );
#endif
  void* mem = calloc( 1, size );
#ifdef CP_EVENTID_RUNMALLOC
  CP_LOGEVENT( CP_EVENTID_RUNMALLOC, CP_EVENTTYPE_END );
#endif
  return mem;
}

static inline void cp_free( void* ptr ) {
#ifdef CP_EVENTID_RUNFREE
  CP_LOGEVENT( CP_EVENTID_RUNFREE, CP_EVENTTYPE_BEGIN );
#endif
  free( ptr );
#ifdef CP_EVENTID_RUNFREE
  CP_LOGEVENT( CP_EVENTID_RUNFREE, CP_EVENTTYPE_END );
#endif
}


#endif
#endif
