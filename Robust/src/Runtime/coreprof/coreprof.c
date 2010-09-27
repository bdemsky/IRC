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

  if( monitor == NULL ) {
    printf( "ERROR: calloc returned NULL\n" );
    exit( -1 );
  }

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
#ifdef CP_EVENTID_MAIN
  CP_LOGEVENT( CP_EVENTID_MAIN, CP_EVENTTYPE_BEGIN );
#endif
}

// Place to do shutdown stuff
void cp_exit() {
#ifdef CP_EVENTID_MAIN
  CP_LOGEVENT( CP_EVENTID_MAIN, CP_EVENTTYPE_END );
#endif
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
  
  int fd = open( "coreprof.dat", O_RDWR | O_CREAT, S_IRWXU );
  int numThreads = 0;
  int i;

  struct coreprofmonitor* monitor;

  // WRITING HEADER

  // Write version number
  int version = 0;
  cp_writedata( fd, 
                (char*)&version, 
                sizeof( int ) );

  // Write the number of threads
  monitor = cp_monitorList;
  while( monitor != NULL ) {
    numThreads++;
    monitor = monitor->next;
  }
  cp_writedata( fd, 
                (char*)&numThreads, 
                sizeof( int ) );

  // Write the number of words used to log
  // events for each thread
  monitor = cp_monitorList;
  while( monitor != NULL ) {
    cp_writedata( fd, 
                  (char*)&monitor->numWords, 
                  sizeof( int ) );
    monitor = monitor->next;
  }

  // END HEADER, BEGIN DATA
  monitor = cp_monitorList;
  while( monitor != NULL ) {
    cp_writedata( fd, 
                  (char*)monitor->data, 
                  sizeof( unsigned int )*monitor->numWords );
    monitor = monitor->next;
  }

  close( fd );
}


void cp_reportOverflow() {
  printf( "ERROR: coreprof event overflow\n" ); 
  exit( -1 );
}
