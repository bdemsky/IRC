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
#elif defined PMC_GC
#define GC_PROFILE_NUM_FIELD 15
#else
#define GC_PROFILE_NUM_FIELD 14
#endif // GC_CACHE_ADAPT

typedef struct gc_info {
  unsigned long long time[GC_PROFILE_NUM_FIELD];
  unsigned int index;
} GCInfo;

// the following data are supposed to be only valid on the master core
// the other cores should not maintain them
GCInfo * gc_infoArray[GCINFOLENGTH];
unsigned int gc_infoIndex;
bool gc_infoOverflow;
unsigned int gc_num_profiles;
unsigned int gc_size_allocatedobj;
unsigned long long gc_num_livespace;
unsigned long long gc_num_freespace;
unsigned long long gc_num_lobjspace;
unsigned int gc_num_lobj;

// these data should be maintained by all gc cores
unsigned int gc_num_liveobj;
unsigned int gc_num_forwardobj;


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

// these *_master function can only be invoked by the master core
INLINE static void gc_profileStart_master(void) {
  if(!gc_infoOverflow) {
    GCInfo* gcInfo = RUNMALLOC(sizeof(struct gc_info));
    gc_infoArray[gc_infoIndex] = gcInfo;
    gcInfo->index = 1;
    gcInfo->time[0] = BAMBOO_GET_EXE_TIME();
  }
}

INLINE static void gc_profileItem_master(void) {
  if(!gc_infoOverflow) {
    GCInfo* gcInfo = gc_infoArray[gc_infoIndex];
    gcInfo->time[gcInfo->index++] = BAMBOO_GET_EXE_TIME();
  }
}

INLINE static void gc_profileEnd_master(void) {
  if(!gc_infoOverflow) {
    GCInfo* gcInfo = gc_infoArray[gc_infoIndex];
    gcInfo->time[gcInfo->index++] = BAMBOO_GET_EXE_TIME();
    gcInfo->time[gcInfo->index++] = gc_num_livespace;
    gcInfo->time[gcInfo->index++] = gc_num_freespace;
    gcInfo->time[gcInfo->index++] = gc_num_lobj;
    gcInfo->time[gcInfo->index++] = gc_num_lobjspace;
    gcInfo->time[gcInfo->index++] = gc_size_allocatedobj;
    gcInfo->time[gcInfo->index++] = gc_num_liveobj;
    gcInfo->time[gcInfo->index++] = gc_num_forwardobj;
    gc_infoIndex++;
    gc_size_allocatedobj = 0; // reset the counter of allocated obj
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
      send_msg_3(STARTUPCORE,GCPROFILES,gc_num_liveobj,gc_num_forwardobj); \
    }\
  }

#ifdef MGC_SPEC
// record allocated obj info
#define GCPROFILE_RECORD_ALLOCATED_OBJ(size) \
  { \
    if(gc_profile_flag) {\
      gc_size_allocatedobj += size; \
    } \
  }
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
#ifdef PMC_GC
// check the live/free space info
#define GCPROFILE_RECORD_SPACE_MASTER() \
  { \
    if(gc_profile_flag) { \
      gc_num_freespace = 0; \
      for(int i=0;i<NUMCORES4GC;i+=2) { \
        void *startptr=pmc_heapptr->regions[i].lastptr; \
        void *finishptr=(i+1)<NUMCORES4GC?pmc_heapptr->regions[i+1].lastptr:pmc_heapptr->regions[i].endptr; \
        gc_num_freespace += finishptr-startptr; \ 
      } \
      gc_num_livespace = (BAMBOO_SHARED_MEM_SIZE) - gc_num_freespace; \
    } \
  }
#else
// check the live/free space info
#define GCPROFILE_RECORD_SPACE_MASTER() \
  { \
    if(gc_profile_flag) { \
      gc_num_freespace = 0; \
      block_t lowestblock=allocationinfo.lowestfreeblock; \
      for(block_t searchblock=lowestblock;searchblock<GCNUMBLOCK;searchblock++) { \
        struct blockrecord * block=&allocationinfo.blocktable[searchblock]; \
        if (block->status==BS_FREE) { \
          gc_num_freespace+=block->freespace&~BAMBOO_CACHE_LINE_MASK; \
        } \
      } \
      gc_num_livespace = (BAMBOO_SHARED_MEM_SIZE) - gc_num_freespace; \
    } \
  }
#endif
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
#define GCPROFILE_START_MASTER() \
  { \
    if(gc_profile_flag) { \
      gc_profileStart_master(); \
    } \
  }
#define GCPROFILE_ITEM_MASTER() \
  { \
    if(gc_profile_flag) { \
      gc_profileItem_master(); \
    } \
  }
#define GCPROFILE_END_MASTER() \
  { \
    if(gc_profile_flag) { \
      gc_profileEnd_master(); \
    } \
  }
#else // MGC_SPEC
// record allocated obj info
#define GCPROFILE_RECORD_ALLOCATED_OBJ(size) \
  { \
    gc_size_allocatedobj += size; \
  }
#define GCPROFILE_RECORD_LOBJ() (gc_num_lobj++)
#define GCPROFILE_RECORD_LOBJSPACE() (gc_num_lobjspace = sumsize)
#ifdef PMC_GC
// check the live/free space info
#define GCPROFILE_RECORD_SPACE_MASTER() \
  { \
    gc_num_freespace = 0; \
    for(int i=0;i<NUMCORES4GC;i+=2) { \
      void *startptr=pmc_heapptr->regions[i].lastptr; \
      void *finishptr=(i+1)<NUMCORES4GC?pmc_heapptr->regions[i+1].lastptr:pmc_heapptr->regions[i].endptr; \
      gc_num_freespace += finishptr-startptr; \ 
    } \
    gc_num_livespace = (BAMBOO_SHARED_MEM_SIZE) - gc_num_freespace; \
  }
#else
#define GCPROFILE_RECORD_SPACE_MASTER() \
  { \
    gc_num_freespace = 0; \
    block_t lowestblock=allocationinfo.lowestfreeblock; \
    for(block_t searchblock=lowestblock;searchblock<GCNUMBLOCK;searchblock++) { \
      struct blockrecord * block=&allocationinfo.blocktable[searchblock]; \
      if (block->status==BS_FREE) { \
        gc_num_freespace+=block->freespace&~BAMBOO_CACHE_LINE_MASK; \
      } \
    } \
    gc_num_livespace = (BAMBOO_SHARED_MEM_SIZE) - gc_num_freespace; \
  }
#endif
#define GCPROFILE_RECORD_FORWARD_OBJ() (gc_num_forwardobj++)
#define GCPROFILE_RECORD_LIVE_OBJ() (gc_num_liveobj++)
#define GCPROFILE_START_MASTER() gc_profileStart_master()
#define GCPROFILE_ITEM_MASTER() gc_profileItem_master()
#define GCPROFILE_END_MASTER() gc_profileEnd_master()
#endif // MGC_SPEC

#define GCPROFILE_INIT() gc_profileInit()

#else // GC_PROFILE
#define GCPROFILE_RECORD_ALLOCATED_OBJ(size) 
#define INIT_MULTICORE_GCPROFILE_DATA()
#define GC_OUTPUT_PROFILE_DATA() 
#define GCPROFILE_INFO_2_MASTER() 
#define GCPROFILE_RECORD_LOBJ()
#define GCPROFILE_RECORD_LOBJSPACE()
#define GCPROFILE_RECORD_SPACE()
#define GCPROFILE_RECORD_FORWARD_OBJ() 
#define GCPROFILE_RECORD_LIVE_OBJ() 
#define GCPROFILE_START_MASTER()
#define GCPROFILE_ITEM_MASTER()
#define GCPROFILE_END_MASTER()
#define GCPROFILE_INIT()
#endif // GC_PROFILE

#endif // MULTICORE_GC
#endif // BAMBOO_MULTICORE_GC_PROFILE_H
