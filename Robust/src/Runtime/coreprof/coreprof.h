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
#define CP_EVENT_MASK       3
#define CP_EVENT_BASESHIFT  8

#define CP_EVENTTYPE_BEGIN  1
#define CP_EVENTTYPE_END    2
#define CP_EVENTTYPE_ONEOFF 3

// Event IDs
#define CP_EVENTID_MAIN          0x04
#define CP_EVENTID_RUNMALLOC     0x10
#define CP_EVENTID_RUNFREE       0x11
#define CP_EVENTID_WORKSCHEDGRAB 0x20
#define CP_EVENTID_TASKDISPATCH  0x30
#define CP_EVENTID_TASKEXECUTE   0x31
#define CP_EVENTID_TASKRETIRE    0x32
#define CP_EVENTID_TASKSTALLVAR  0x40
#define CP_EVENTID_TASKSTALLMEM  0x41
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
  CP_LOGEVENT( CP_EVENTID_RUNMALLOC, CP_EVENTTYPE_BEGIN );
  void* mem = calloc( 1, size );
  CP_LOGEVENT( CP_EVENTID_RUNMALLOC, CP_EVENTTYPE_END );
  return mem;
}

static inline void cp_free( void* ptr ) {
  CP_LOGEVENT( CP_EVENTID_RUNFREE, CP_EVENTTYPE_BEGIN );
  free( ptr );
  CP_LOGEVENT( CP_EVENTID_RUNFREE, CP_EVENTTYPE_END );
}


#endif
#endif
