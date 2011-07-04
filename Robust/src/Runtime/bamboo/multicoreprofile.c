#include "multicoreprofile.h"
#include "runtime_arch.h"

struct profiledata * eventdata;

void startEvent(enum eventprofile event) {
  struct eventprofile *profile=&eventdata->cores[BAMBOO_NUM_OF_CORE].events[event];
  profile->totaltimestarts+=BAMBOO_GET_EXE_TIME();
  profile->numstarts++;
}

void stopEvent(enum eventprofile event) {
  struct eventprofile *profile=&eventdata->cores[BAMBOO_NUM_OF_CORE].events[event];
  profile->totaltimestops+=BAMBOO_GET_EXE_TIME();
  profile->numstops++;
}

void printResults() {
  for(int core=0;core<NUMCORES;core++) {
    printf("Core: %u", core);
    for(int event=0;event<NUMEVENTS;event++) {
      printf("  Event:%s\n", eventnames[event]);
      struct eventprofile *profile=&eventdata->cores[core].events[event];
      if (profile->numstarts!=profile->numstops) {
	printf("    Mismatched starts and stops\n");
      }
      long long totaltime=profile->totaltimestops-profile->totaltimestarts;
      printf("    Total time: %llu Total events: %u Average time:%f\n", totaltime, profile->numstarts, ((double)totaltime)/profile->numstarts);
    }
  }
}
