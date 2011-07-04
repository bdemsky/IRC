#ifndef MULTICOREPROFILE_H
#define MULTICOREPROFILE_H
#include "structdefs.h"

enum eventtypes {
  EV_GCTIME,
  EV_NUMEVENTS
};

char eventnames[][30]={"gctime", "endmarker"};

struct eventprofile {
  long long totaltimestarts;
  long long totaltimestops;
  int numstarts;
  int numstops;
};

struct coreprofile {
  struct eventprofile events[EV_NUMEVENTS];
};

struct profiledata {
  struct coreprofile cores[NUMCORES];
};

extern struct profiledata * eventdata;

void startEvent(enum eventtypes event);
void stopEvent(enum eventtypes event);
void printResults();

#endif
