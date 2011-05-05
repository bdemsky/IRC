#ifndef BAMBOO_MULTICORE_CACHE_H
#define BAMBOO_MULTICORE_CACHE_H
#ifdef MULTICORE_GC
#include "multicore.h"

#ifdef GC_CACHE_ADAPT
#define GC_CACHE_SAMPLING_UNIT 100000000
#define GC_TILE_TIMER_EVENT_SETTING 10000000 //0  

// should be consistent with multicoreruntime.h
typedef union
{
  unsigned int word;
  struct
  {
    // policy type
    unsigned int cache_mode   : 2;
	// Reserved.
    unsigned int __reserved_0 : 6;
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

#define BAMBOO_CACHE_MODE_LOCAL 0
#define BAMBOO_CACHE_MODE_HASH 1
#define BAMBOO_CACHE_MODE_NONE 2
#define BAMBOO_CACHE_MODE_COORDS 3

INLINE void samplingDataReviseInit(); 
INLINE void samplingDataConvert(unsigned int current_ptr);
INLINE void completePageConvert(struct moveHelper * orig,
                                struct moveHelper * to,
                                unsigned int current_ptr,
                                bool closeToPage);
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

#define CACHEADAPT_SAMPLING_DATA_REVISE_INIT() samplingDataReviseInit()
#define CACHEADAPT_SAMPLING_DATA_CONVERT(p) samplingDataConvert((p))
#define CACHEADAPT_COMPLETE_PAGE_CONVERT(o, t, p, b) \
  completePageConvert((o), (t), (p), (b));

#define CACHEADAPT_GC(b) cacheAdapt_gc(b)
#define CACHEADAPT_MASTER() cacheAdapt_master()
#define CACHEADAPT_PHASE_CLIENT() cacheAdpat_phase_client()
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
#define CACHEADAPT_SAMPLING_DATA_REVISE_INIT() 
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
