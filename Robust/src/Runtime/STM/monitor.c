#include "monitor.h"
#include <unistd.h>
#include <fcntl.h>
#include <stdio.h>

//Need to have global lock before calling this method
void createmonitor() {
  struct eventmonitor *event=calloc(1, sizeof(struct eventmonitor));
  //add new eventmonitor to list
  event->next=eventlist;
  eventlist=events;
  
  //point thread lock variable to eventmonitor
  events=event;
  EVLOGEVENT(EM_THREAD);
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
  int fd=open("logdata",O_RDWR|O_CREAT);
  int count=0;
  struct eventmonitor * ptr=eventlist;
  while(ptr!=NULL) {
    count++;
    ptr=ptr->next;
  }
  writedata(fd, &count, sizeof(int));
  ptr=eventlist;
  if (ptr->index>MAXEVENTS) {
    printf("ERROR: EVENT COUNT EXCEEDED\n")
  }
  while(ptr!=NULL) {
    writedata(fd, &ptr->index, sizeof(int));
    writedata(fd, ptr->value, sizeof(int)*ptr->index);
    ptr=ptr->next;
  }  
  close(fd);
}
