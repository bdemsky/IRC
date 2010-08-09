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


#ifndef CP_MAXEVENTS
#define CP_MAXEVENTS (1024*1024*128)
#endif


// MASK should be enough bits to mask
// the values of the following event types
// and BASESHIFT is for shifting IDs
// past the type bits
#define CP_EVENT_MASK       3
#define CP_EVENT_BASESHIFT  2

#define CP_EVENTTYPE_BEGIN  0
#define CP_EVENTTYPE_END    1
#define CP_EVENTTYPE_ONEOFF 2

// Event IDs
#define CP_EVENTID_MAIN         0
#define CP_EVENTID_RUNMALLOC    1
#define CP_EVENTID_RUNFREE      2
#define CP_EVENTID_TASKDISPATCH 3
#define CP_EVENTID_TASKRETIRE   4
#define CP_EVENTID_TASKSTALLVAR 5
#define CP_EVENTID_TASKSTALLMEM 6


struct coreprofmonitor {
  struct coreprofmonitor* next;
  
  // index for next empty slot in the following arrays
  int          numEvents; 
  unsigned int events     [CP_MAXEVENTS];
  long long    logTimes_ms[CP_MAXEVENTS];
};


extern __thread int                     cp_threadnum;
extern __thread struct coreprofmonitor* cp_monitor;
extern          struct coreprofmonitor* cp_monitorList;


#ifndef COREPROF_CHECKOVERFLOW
// normal, no overflow check version
#define CP_LOGEVENT( eventID, eventType ) { \
  cp_monitor->events[cp_monitor->numEvents] = \
    ((eventID<<CP_EVENT_BASESHIFT)|eventType); \
  cp_monitor->logTimes_ms[cp_monitor->numEvents] = rdtsc(); \
  cp_monitor->numEvents++; \
}
#else
// check for event overflow, DEBUG ONLY!
void cp_reportOverflow();
#define CP_LOGEVENT( eventID, eventType ) { \
  if( cp_monitor->numEvents == CP_MAXEVENTS ) \
    { cp_reportOverflow(); }                  \
  cp_monitor->events[cp_monitor->numEvents] = \
    ((eventID<<CP_EVENT_BASESHIFT)|eventType); \
  cp_monitor->logTimes_ms[cp_monitor->numEvents] = rdtsc(); \
  cp_monitor->numEvents++; \
}
#endif


#define CP_CREATE() cp_create();
#define CP_EXIT()   cp_exit();
#define CP_DUMP()   cp_dump();

void cp_create();
void cp_exit();
void cp_dump();



static inline void* cp_calloc( int size ) {
  //CP_LOGEVENT( CP_EVENTID_RUNMALLOC, CP_EVENTTYPE_BEGIN );
  void* mem = calloc( 1, size );
  //CP_LOGEVENT( CP_EVENTID_RUNMALLOC, CP_EVENTTYPE_END );
  return mem;
}

static inline void cp_free( void* ptr ) {
  //CP_LOGEVENT( CP_EVENTID_RUNFREE, CP_EVENTTYPE_BEGIN );
  free( ptr );
  //CP_LOGEVENT( CP_EVENTID_RUNFREE, CP_EVENTTYPE_END );
}


#endif
#endif
