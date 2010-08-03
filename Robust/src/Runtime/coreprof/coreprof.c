#include "runtime.h"
#include "coreprof.h"
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include "mlp_lock.h"

__thread struct coreprofmonitor * cp_events=NULL;
struct coreprofmonitor * cp_eventlist=NULL;
static volatile int cp_threadcount=0;
__thread int cp_threadnum;

static inline int atomicinc(volatile int *lock) {
  int retval=1;
  __asm__ __volatile__("lock; xadd %0,%1"
                       : "=r"(retval)
                       : "m"(*lock), "0"(retval)
                       : "memory");
  return retval;
}


//Need to have global lock before calling this method
void createprofiler() {
  if (cp_events!=NULL)
    return;
  struct coreprofmonitor *event=calloc(1, sizeof(struct coreprofmonitor));
  //add new eventmonitor to list
  struct coreprofmonitor *tmp;

  //add ourself to the list
  do {
    tmp=cp_eventlist;
    event->next=tmp;
  } while(CAS(&cp_eventlist, (INTPTR) tmp, (INTPTR) event)!=((INTPTR)tmp));

  int ourcount=atomicinc(&cp_threadcount);
  cp_threadnum=ourcount;

  //point thread lock variable to eventmonitor
  cp_events=event;
  CPLOGEVENT(CP_MAIN, CP_BEGIN);
}

//Place to do shutdown stuff
void exitprofiler() {
  CPLOGEVENT(CP_MAIN, CP_END);
}

void cpwritedata(int fd, char * buffer, int count) {
  int offset=0;
  while(count>0) {
    int size=write(fd, &buffer[offset], count);
    offset+=size;
    count-=size;
  }
}

void dumpprofiler() {
  int fd=open("logdata",O_RDWR|O_CREAT,S_IRWXU);
  int count=0;
  struct coreprofmonitor * ptr=cp_eventlist;
  int version=0;
  //Write version number
  cpwritedata(fd, (char *)&version, sizeof(int));
  while(ptr!=NULL) {
    count++;
    if (ptr->index>CPMAXEVENTS) {
      printf("ERROR: EVENT COUNT EXCEEDED\n");
    }
    ptr=ptr->next;
  }

  //Write the number of threads
  cpwritedata(fd, (char *)&count, sizeof(int));

  //Write the number of events for each thread
  ptr=cp_eventlist;
  while(ptr!=NULL) {
    cpwritedata(fd, (char *)&ptr->index, sizeof(int));
    ptr=ptr->next;
  }

  //Dump the data
  ptr=cp_eventlist;
  while(ptr!=NULL) {
    cpwritedata(fd, (char *) ptr->value, sizeof(int)*ptr->index);
    ptr=ptr->next;
  }  
  close(fd);
}
