#ifndef BAMBOO_MULTICORE_GC_PROFILE_H
#define BAMBOO_MULTICORE_GC_PROFILE_H
#if defined(MULTICORE_GC)||defined(PMC_GC)
#include "multicore.h"
#include "runtime_arch.h"
#include "structdefs.h"

#ifdef GC_PROFILE
#define GCINFOLENGTH 100

#ifdef GC_CACHE_ADAPT
#define GC_PROFILE_NUM_FIELD 15
#else
#define GC_PROFILE_NUM_FIELD 14
#endif // GC_CACHE_ADAPT

typedef struct gc_info {
  unsigned long long time[GC_PROFILE_NUM_FIELD];
  unsigned int index;
} GCInfo;

GCInfo * gc_infoArray[GCINFOLENGTH];
unsigned int gc_infoIndex;
bool gc_infoOverflow;
unsigned long long gc_num_livespace;
unsigned long long gc_num_freespace;
unsigned long long gc_num_lobjspace;
unsigned int gc_num_lobj;

unsigned int gc_num_liveobj;
unsigned int gc_num_obj;
unsigned int gc_num_forwardobj;
unsigned int gc_num_profiles;

#ifdef MGC_SPEC
volatile bool gc_profile_flag;
#endif

void initmulticoregcprofiledata(void);
void gc_outputProfileData();
void gc_outputProfileDataReadable();

INLINE static void gc_profileInit() {
  gc_num_livespace = 0;
  gc_num_freespace = 0;
  gc_num_lobj = 0;
  gc_num_lobjspace = 0;
  gc_num_liveobj = 0;
  gc_num_forwardobj = 0;
  gc_num_profiles = NUMCORESACTIVE - 1;
}

INLINE static void gc_profileStart(void) {
  if(!gc_infoOverflow) {
    GCInfo* gcInfo = RUNMALLOC(sizeof(struct gc_info));
    gc_infoArray[gc_infoIndex] = gcInfo;
    gcInfo->index = 1;
    gcInfo->time[0] = BAMBOO_GET_EXE_TIME();
  }
}

INLINE static void gc_profileItem(void) {
  if(!gc_infoOverflow) {
    GCInfo* gcInfo = gc_infoArray[gc_infoIndex];
    gcInfo->time[gcInfo->index++] = BAMBOO_GET_EXE_TIME();
  }
}

INLINE static void gc_profileEnd(void) {
  if(!gc_infoOverflow) {
    GCInfo* gcInfo = gc_infoArray[gc_infoIndex];
    gcInfo->time[gcInfo->index++] = BAMBOO_GET_EXE_TIME();
    gcInfo->time[gcInfo->index++] = gc_num_livespace;
    gcInfo->time[gcInfo->index++] = gc_num_freespace;
    gcInfo->time[gcInfo->index++] = gc_num_lobj;
    gcInfo->time[gcInfo->index++] = gc_num_lobjspace;
    gcInfo->time[gcInfo->index++] = gc_num_obj;
    gcInfo->time[gcInfo->index++] = gc_num_liveobj;
    gcInfo->time[gcInfo->index++] = gc_num_forwardobj;
    gc_infoIndex++;
    if(gc_infoIndex == GCINFOLENGTH) {
      gc_infoOverflow = true;
    }
  }
}

#define INIT_MULTICORE_GCPROFILE_DATA() initmulticoregcprofiledata()
#define GC_OUTPUT_PROFILE_DATA() gc_outputProfileData()
// send the num of obj/liveobj/forwardobj to the startupcore
#define GCPROFILE_INFO_2_MASTER() \
  { \
    if(STARTUPCORE != BAMBOO_NUM_OF_CORE) { \
      send_msg_4(STARTUPCORE,GCPROFILES,gc_num_obj,gc_num_liveobj,gc_num_forwardobj); \
    }\
    gc_num_obj = 0; \
  }

#ifdef MGC_SPEC
// record lobj info
#define GCPROFILE_RECORD_LOBJ() \
  { \
      gc_num_lobj++; \
  }
// record lobj space info
#define GCPROFILE_RECORD_LOBJSPACE() \
  { \
    if(gc_profile_flag) { \
      gc_num_lobjspace = sumsize; \
    } \
  }
// check the live/free space info
#define GCPROFILE_RECORD_SPACE() \
  { \
    if(gc_profile_flag) { \
      gc_num_livespace = 0; \
      for(int tmpi = 0; tmpi < GCNUMBLOCK; tmpi++) { \
        gc_num_livespace += bamboo_smemtbl[tmpi]; \
      } \
      gc_num_freespace = (BAMBOO_SHARED_MEM_SIZE) - gc_num_livespace; \
    } \
  }
// record forward obj info
#define GCPROFILE_RECORD_FORWARD_OBJ() \
  { \
      gc_num_forwardobj++; \
  }
// record live obj info
#define GCPROFILE_RECORD_LIVE_OBJ() \
  { \
      gc_num_liveobj++; \
  }
#define GCPROFILE_START() \
  { \
    if(gc_profile_flag) { \
      gc_profileStart(); \
    } \
  }
#define GCPROFILE_ITEM() \
  { \
    if(gc_profile_flag) { \
      gc_profileItem(); \
    } \
  }
#define GCPROFILE_END() \
  { \
    if(gc_profile_flag) { \
      gc_profileEnd(); \
    } \
  }
#else // MGC_SPEC
#define GCPROFILE_RECORD_LOBJ() (gc_num_lobj++)
#define GCPROFILE_RECORD_LOBJSPACE() (gc_num_lobjspace = sumsize)
#define GCPROFILE_RECORD_SPACE() \
  { \
    gc_num_livespace = 0; \
    for(int tmpi = 0; tmpi < GCNUMBLOCK; tmpi++) { \
      gc_num_livespace += bamboo_smemtbl[tmpi]; \
    } \
    gc_num_freespace = (BAMBOO_SHARED_MEM_SIZE) - gc_num_livespace; \
  }
#define GCPROFILE_RECORD_FORWARD_OBJ() (gc_num_forwardobj++)
#define GCPROFILE_RECORD_LIVE_OBJ() (gc_num_liveobj++)
#define GCPROFILE_START() gc_profileStart()
#define GCPROFILE_ITEM() gc_profileItem()
#define GCPROFILE_END() gc_profileEnd()
#endif // MGC_SPEC

#define GCPROFILE_INIT() gc_profileInit()

#else // GC_PROFILE
#define INIT_MULTICORE_GCPROFILE_DATA()
#define GC_OUTPUT_PROFILE_DATA() 
#define GCPROFILE_INFO_2_MASTER() 
#define GCPROFILE_RECORD_LOBJ()
#define GCPROFILE_RECORD_LOBJSPACE()
#define GCPROFILE_RECORD_SPACE()
#define GCPROFILE_RECORD_FORWARD_OBJ() 
#define GCPROFILE_RECORD_LIVE_OBJ() 
#define GCPROFILE_START()
#define GCPROFILE_ITEM()
#define GCPROFILE_END()
#define GCPROFILE_INIT()
#endif // GC_PROFILE

#endif // MULTICORE_GC
#endif // BAMBOO_MULTICORE_GC_PROFILE_H
