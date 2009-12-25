#include "runtime.h"
#include "monitor.h"
#include <unistd.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>

__thread struct eventmonitor * events;
struct eventmonitor * eventlist;
static volatile int threadcount=0;
__thread int threadnum;

static inline int atomicinc(volatile int *lock) {
  int retval=1;
  __asm__ __volatile__("lock; xadd %0,%1"
                       : "=r"(retval)
                       : "m"(*lock), "0"(retval)
                       : "memory");
  return retval;
}

//Need to have global lock before calling this method
void createmonitor() {
  struct eventmonitor *event=calloc(1, sizeof(struct eventmonitor));
  //add new eventmonitor to list
  event->next=eventlist;
  eventlist=event;
  
  int ourcount=atomicinc(&threadcount);
  threadnum=ourcount;
  if (threadnum>=MAXEVTHREADS) {
    printf("ERROR: Threads exceeds MAXEVTHREADS\n");
  }

  //point thread lock variable to eventmonitor
  events=event;
  EVLOGEVENT(EV_THREAD);
}

void writedata(int fd, char * buffer, int count) {
  int offset=0;
  while(count>0) {
    int size=write(fd, &buffer[offset], count);
    offset+=size;
    count-=size;
  }
}

void dumpdata() {
  int fd=open("logdata",O_RDWR|O_CREAT,S_IRWXU);
  int count=0;
  struct eventmonitor * ptr=eventlist;
  while(ptr!=NULL) {
    count++;
    if (ptr->index>MAXEVENTS) {
      printf("ERROR: EVENT COUNT EXCEEDED\n");
    }
    ptr=ptr->next;
  }
  writedata(fd, (char *)&count, sizeof(int));
  ptr=eventlist;
  while(ptr!=NULL) {
    writedata(fd, (char *) &ptr->index, sizeof(int));
    writedata(fd, (char *) ptr->value, sizeof(int)*ptr->index);
    ptr=ptr->next;
  }  
  close(fd);
}
