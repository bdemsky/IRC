#include "runtime.h"
#include "coreprof.h"
#include <unistd.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include "mlp_lock.h"

__thread struct coreprofmonitor * cp_events;
struct coreprofmonitor * cp_eventlist=NULL;
static volatile int cp_threadcount=0;
__thread int threadnum;

//Need to have global lock before calling this method
void createprofiler() {
  struct coreprofmonitor *event=calloc(1, sizeof(struct coreprofmonitor));
  //add new eventmonitor to list
  struct coreprofmonitor *tmp;

  //add ourself to the list
  do {
    tmp=cp_eventlist;
    event->next=tmp;
  } while(CAS(&cp_eventlist, tmp, event)!=tmp);

  int ourcount=atomic_inc(&cp_threadcount);
  cp_threadnum=ourcount;

  //point thread lock variable to eventmonitor
  cp_events=event;
  CPLOGEVENT((CP_START<<CP_BASE_SHIFT)|CP_BEGIN);
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
  int VERSION=0;
  //Write version number
  cpwritedata(fd, &version, sizeof(int));
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
    cpwritedata(fd, &ptr->index, sizeof(int));
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
