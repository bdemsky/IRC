#include "runtime.h"
#include "coreprof.h"
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include "mlp_lock.h"

__thread int                     cp_threadnum;
__thread struct coreprofmonitor* cp_monitor     = NULL;
         struct coreprofmonitor* cp_monitorList = NULL;
static volatile int              cp_threadCount = 0;


static inline int atomicinc(volatile int *lock) {
  int retval=1;
  __asm__ __volatile__("lock; xadd %0,%1"
                       : "=r"(retval)
                       : "m"(*lock), "0"(retval)
                       : "memory");
  return retval;
}


// Need to have global lock before calling this method
void cp_create() {
  if( cp_monitor != NULL )
    return;

  struct coreprofmonitor* monitor = 
    calloc( 1, sizeof( struct coreprofmonitor ) );

  struct coreprofmonitor* tmp;

  // add ourself to the list
  do {
    tmp           = cp_monitorList;
    monitor->next = tmp;
  } while( CAS( &cp_monitorList, 
                (INTPTR) tmp, 
                (INTPTR) monitor 
                ) != ((INTPTR)tmp)
           );

  int ourcount = atomicinc( &cp_threadCount );
  cp_threadnum = ourcount;

  // point thread lock variable to event monitor
  cp_monitor = monitor;
  CP_LOGEVENT( CP_EVENTID_MAIN, CP_EVENTTYPE_BEGIN );
}

// Place to do shutdown stuff
void cp_exit() {
  CP_LOGEVENT( CP_EVENTID_MAIN, CP_EVENTTYPE_END );
}

void cp_writedata( int fd, char* buffer, int count ) {
  int offset = 0;
  while( count > 0 ) {
    int size = write( fd, &buffer[offset], count );
    offset += size;
    count  -= size;
  }
}


void cp_dump() {
  
  //int fdh   = open( "coreprof-head.dat", O_RDWR | O_CREAT, S_IRWXU );
  //int fde   = open( "coreprof-evnt.dat", O_RDWR | O_CREAT, S_IRWXU );
  //int fdt   = open( "coreprof-time.dat", O_RDWR | O_CREAT, S_IRWXU );
  int fd    = open( "coreprof.dat", O_RDWR | O_CREAT, S_IRWXU );
  int count = 0;
  int i;

  struct coreprofmonitor* monitor;

  // WRITING HEADER

  // Write version number
  int version = 0;
  cp_writedata( fd, 
                (char*)&version, 
                sizeof( int ) );

  // check for overflow
  monitor = cp_monitorList;
  while( monitor != NULL ) {
    count++;
    if( monitor->numEvents > CP_MAXEVENTS ) {
      printf( "ERROR: EVENT COUNT EXCEEDED\n" );
    }
    monitor = monitor->next;
  }

  // Write the number of threads
  cp_writedata( fd, 
                (char*)&count, 
                sizeof( int ) );

  monitor = cp_monitorList;
  while( monitor != NULL ) {

    // Write the number of events for each thread
    cp_writedata( fd, 
                  (char*)&monitor->numEvents, 
                  sizeof( int ) );

    monitor = monitor->next;
  }

  // END HEADER, BEGIN DATA

  monitor = cp_monitorList;
  while( monitor != NULL ) {

    // Write the event IDs (index matches time below)
    cp_writedata( fd, 
                  (char*)monitor->events, 
                  sizeof( unsigned int )*monitor->numEvents );

    // Write the event timestamps (index matches above)
    cp_writedata( fd, 
                  (char*)monitor->logTimes_ms, 
                  sizeof( long long )*monitor->numEvents );
    monitor = monitor->next;
  }

  close( fd );
  //close( fde );
  //close( fdt );
}


void cp_reportOverflow() {
  printf( "ERROR: coreprof event overflow\n" ); 
  exit( -1 );
}
