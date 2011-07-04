#ifndef MULTICOREPROFILE_H
#define MULTICOREPROFILE_H

enum profileevents {
  GCTIME,
  NUMEVENTS;
};

char ** eventnames={"gctime", "endmarker"};

struct eventprofile {
  long long totaltimestarts;
  long long totaltimestops;
  int numstarts;
  int numstops;
};

struct coreprofile {
  struct eventprofile events[NUMEVENTS];
};

struct profiledata {
  struct coreprofile cores[NUMCORES];
};

extern struct profiledata * eventdata;

void startEvent(enum eventprofile event);
void stopEvent(enum eventprofile event);
void printResults();

#endif
