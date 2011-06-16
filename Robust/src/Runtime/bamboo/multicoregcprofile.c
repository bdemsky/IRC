#ifdef MULTICORE_GC
#ifdef GC_PROFILE
#include "multicoregcprofile.h"
#include "runtime_arch.h"
#include "mem.h"

void initmulticoregcprofiledata() {
  if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
    // startup core to initialize corestatus[]
    gc_infoIndex = 0;
    gc_infoOverflow = false;
    gc_num_livespace = 0;
    gc_num_freespace = 0;
  }
  gc_num_obj = 0;
  gc_num_liveobj = 0;
  gc_num_forwardobj = 0;
  gc_num_profiles = NUMCORESACTIVE - 1;
}

// output the profiling data
void gc_outputProfileData() {
  int i = 0;
  int j = 0;
  unsigned long long totalgc = 0;

#ifndef BAMBOO_MEMPROF
  BAMBOO_PRINT(0xdddd);
#endif
  // output task related info
  for(i= 0; i < gc_infoIndex; i++) {
    GCInfo * gcInfo = gc_infoArray[i];
#ifdef BAMBOO_MEMPROF
    unsigned long long tmp=gcInfo->time[gcInfo->index-8]-gcInfo->time[0]; //0;
#else
    unsigned long long tmp = 0;
    BAMBOO_PRINT(0xddda);
    for(j = 0; j < gcInfo->index - 7; j++) {
      BAMBOO_PRINT(gcInfo->time[j]);
      BAMBOO_PRINT(gcInfo->time[j]-tmp);
      BAMBOO_PRINT(0xdddb);
      tmp = gcInfo->time[j];
    }
    tmp = (tmp-gcInfo->time[0]);
    BAMBOO_PRINT_REG(tmp);
    BAMBOO_PRINT(0xdddc);
    BAMBOO_PRINT(gcInfo->time[gcInfo->index - 7]);
    BAMBOO_PRINT(gcInfo->time[gcInfo->index - 6]);
    BAMBOO_PRINT(gcInfo->time[gcInfo->index - 5]);
    BAMBOO_PRINT(gcInfo->time[gcInfo->index - 4]);
    BAMBOO_PRINT(gcInfo->time[gcInfo->index - 3]);
    BAMBOO_PRINT(gcInfo->time[gcInfo->index - 2]);
    BAMBOO_PRINT(gcInfo->time[gcInfo->index - 1]);
    BAMBOO_PRINT(0xddde);
#endif
    totalgc += tmp;
  }
#ifndef BAMBOO_MEMPROF
  BAMBOO_PRINT(0xdddf);
#endif
  BAMBOO_PRINT_REG(totalgc);

  if(gc_infoOverflow) {
    BAMBOO_PRINT(0xefee);
  }

#ifndef BAMBOO_MEMPROF
  BAMBOO_PRINT(0xeeee);
#endif
}
#endif  // GC_PROFILE
#endif // MULTICORE_GC