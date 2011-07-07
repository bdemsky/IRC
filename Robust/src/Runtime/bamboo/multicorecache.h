#ifndef BAMBOO_MULTICORE_CACHE_H
#define BAMBOO_MULTICORE_CACHE_H
#ifdef MULTICORE_GC
#include "multicore.h"
#include "multicoremem.h"
#include "multicoregccompact.h"
#include "multicoregarbage.h"

#ifdef GC_CACHE_ADAPT
// sampling unit to compute access frequency, this should be consistent all the
// time.
#define GC_CACHE_SAMPLING_UNIT 0x80000 
// freqeuency to trigger timer interrupt
#define GC_TILE_TIMER_EVENT_SETTING 10000000  

// data structure to record policy information for a page
// should be consistent with multicoreruntime.h
typedef union
{
  unsigned int word;
  struct
  {
    // policy type, should be enough to accommodate all 4 possible polices
    unsigned int cache_mode   : 3;
    // Reserved.
    unsigned int __reserved_0 : 5;
    // Location Override Target Y
    unsigned int lotar_y      : 4;
    // Reserved.
    unsigned int __reserved_1 : 4;
    // Location Override Target X
    unsigned int lotar_x      : 4;
    // Reserved.
    unsigned int __reserved_2 : 12;
  };
} bamboo_cache_policy_t;

#define BAMBOO_CACHE_MODE_LOCAL 1  // locally cached
#define BAMBOO_CACHE_MODE_HASH 2   // hash-for-home
#define BAMBOO_CACHE_MODE_NONE 3   // no caching
#define BAMBOO_CACHE_MODE_COORDS 4 // cached on a specific core

// data structure to hold page information while calculating revised sampling 
// info during compaction
typedef struct gc_cache_revise_info {
  // the start address in current original page to be compacted to current 
  // destination page
  void * orig_page_start_va; 
  // the end of current original page to be compacted
  void * orig_page_end_va;
  // the index of current original page
  unsigned int orig_page_index;
  // the start address in current destination page to where the current original
  // page is compacted
  void * to_page_start_va;
  // the end of current destination page
  void * to_page_end_va;
  // the index of current destination page
  unsigned int to_page_index;
} gc_cache_revise_info_t;

// global variable holding page information to compute revised sampling info
extern gc_cache_revise_info_t gc_cache_revise_information;

void samplingDataReviseInit(struct moveHelper * orig,struct moveHelper * to);
void completePageConvert(void * origptr, void * toptr, void * current_ptr); 
void cacheAdapt_gc(bool isgccachestage);
void cacheAdapt_master();
void cacheAdapt_mutator();
void cacheAdapt_phase_client();
void cacheAdapt_phase_master();
void gc_output_cache_sampling();
void gc_output_cache_sampling_r();

#ifdef GC_CACHE_SAMPLING
// enable the timer interrupt
#define CACHEADAPT_ENABLE_TIMER() \
  { \
    bamboo_tile_timer_set_next_event(GC_TILE_TIMER_EVENT_SETTING); \
    bamboo_unmask_timer_intr(); \
    bamboo_dtlb_sampling_process(); \
  }
#else
#define CACHEADAPT_ENABLE_TIMER() 
#endif
// disable the TILE_TIMER interrupt
#define CACHEADAPT_DISABLE_TIMER() bamboo_mask_timer_intr() 

#ifdef GC_CACHE_SAMPLING
// reset the sampling arrays
#define CACHEADAPT_SAMPING_RESET()  bamboo_dtlb_sampling_reset()
#else // GC_CACHE_SAMPING
#define CACHEADAPT_SAMPING_RESET() 
#endif

#define CACHEADAPT_SAMPLING_DATA_REVISE_INIT(o,t) \
  samplingDataReviseInit((o),(t))
//#define CACHEADAPT_SAMPLING_DATA_CONVERT(p) samplingDataConvert((p))
#define CACHEADAPT_COMPLETE_PAGE_CONVERT(o, t, p) \
  completePageConvert((o), (t), (p));

#define CACHEADAPT_GC(b) cacheAdapt_gc(b)
#define CACHEADAPT_MASTER() cacheAdapt_master()
#define CACHEADAPT_PHASE_CLIENT() cacheAdapt_phase_client()
#define CACHEADAPT_PHASE_MASTER() cacheAdapt_phase_master()

#ifdef GC_CACHE_ADAPT_OUTPUT
#define CACHEADAPT_OUTPUT_CACHE_SAMPLING() gc_output_cache_sampling()
#define CACHEADAPT_OUTPUT_CACHE_SAMPLING_R() gc_output_cache_sampling_r()
#else
#define CACHEADAPT_OUTPUT_CACHE_SAMPLING()
#define CACHEADAPT_OUTPUT_CACHE_SAMPLING_R() 
#endif

#ifdef GC_CACHE_ADAPT_OUTPUT_POLICY
#ifdef MGC_SPEC
#define CACHEADAPT_OUTPUT_CACHE_POLICY() \
  { \
    if(gc_profile_flag) { \
      bamboo_output_cache_policy(); \
    } \
  }
#else // MGC_SPEC
#define CACHEADAPT_OUTPUT_CACHE_POLICY() bamboo_output_cache_policy()
#endif // MGC_SPEC
#else // GC_CACHE_ADAPT_OUTPUT_POLICY
#define CACHEADAPT_OUTPUT_CACHE_POLICY() 
#endif // GC_CACHE_ADAPT_OUTPUT

#else // GC_CACHE_ADAPT
#define CACHEADAPT_ENABLE_TIMER() 
#define CACHEADAPT_DISABLE_TIMER() 
#define CACHEADAPT_SAMPING_RESET()
#define CACHEADAPT_SAMPLING_DATA_REVISE_INIT(o,t) 
#define CACHEADAPT_SAMPLING_DATA_CONVERT(p) 
#define CACHEADAPT_COMPLETE_PAGE_CONVERT(o, t, p, b) 
#define CACHEADAPT_GC(b)
#define CACHEADAPT_MASTER()
#define CACHEADAPT_PHASE_CLIENT() 
#define CACHEADAPT_PHASE_MASTER() 
#define CACHEADAPT_OUTPUT_CACHE_SAMPLING()
#define CACHEADAPT_OUTPUT_CACHE_SAMPLING_R() 
#define CACHEADAPT_OUTPUT_CACHE_POLICY() 
#endif // GC_CACHE_ADAPT
#else // MULTICORE_GC
#define CACHEADAPT_ENABLE_TIMER() 
#define CACHEADAPT_DISABLE_TIMER()
#endif // MULTICORE_GC

#endif // BAMBOO_MULTICORE_CACHE_H
