#ifndef BAMBOO_MULTICORE_CACHE_H
#define BAMBOO_MULTICORE_CACHE_H
#ifdef MULTICORE_GC
#include "multicore.h"
#include "multicoremem.h"
#include "multicoregccompact.h"
#include "multicoregarbage.h"

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

typedef struct gc_cache_revise_info {
  unsigned int orig_page_start_va;
  unsigned int orig_page_end_va;
  unsigned int orig_page_index;
  unsigned int to_page_start_va;
  unsigned int to_page_end_va;
  unsigned int to_page_index;
  unsigned int revised_sampling[NUMCORESACTIVE];
} gc_cache_revise_info_t;

extern gc_cache_revise_info_t gc_cache_revise_information;

INLINE static void samplingDataReviseInit(struct moveHelper * orig,struct moveHelper * to) {
  gc_cache_revise_information.to_page_start_va=(unsigned int)to->ptr;
  unsigned int toindex=(unsigned int)(to->base-gcbaseva)/(BAMBOO_PAGE_SIZE);
  gc_cache_revise_information.to_page_end_va=gcbaseva+(BAMBOO_PAGE_SIZE)*(toindex+1);
  gc_cache_revise_information.to_page_index=toindex;
  gc_cache_revise_information.orig_page_start_va=(unsigned int)orig->ptr;
  gc_cache_revise_information.orig_page_end_va=gcbaseva+(BAMBOO_PAGE_SIZE)*(((unsigned int)(orig->ptr)-gcbaseva)/(BAMBOO_PAGE_SIZE)+1);
  gc_cache_revise_information.orig_page_index=((unsigned int)(orig->blockbase)-gcbaseva)/(BAMBOO_PAGE_SIZE);
}

INLINE static void samplingDataConvert(unsigned int current_ptr) {
  unsigned int tmp_factor=current_ptr-gc_cache_revise_information.to_page_start_va;
  unsigned int topage=gc_cache_revise_information.to_page_index;
  unsigned int oldpage=gc_cache_revise_information.orig_page_index;
  int * newtable=&gccachesamplingtbl_r[topage];
  int * oldtable=&gccachesamplingtbl[oldpage];
  
  for(int tt = 0; tt < NUMCORESACTIVE; tt++) {
    (*newtable)=((*newtable)+(*oldtable)*tmp_factor);
    newtable=(int*)(((char *)newtable)+size_cachesamplingtbl_local_r);
    oldtable=(int*) (((char *)oldtable)+size_cachesamplingtbl_local);
  }
} 

INLINE static void completePageConvert(struct moveHelper * orig,struct moveHelper * to,unsigned int current_ptr,bool closeToPage) {
  unsigned int ptr=0;
  unsigned int tocompare=0;
  if(closeToPage) {
    ptr=to->ptr;
    tocompare=gc_cache_revise_information.to_page_end_va;
  } else {
    ptr=orig->ptr;
    tocompare=gc_cache_revise_information.orig_page_end_va;
  }
  if((unsigned int)ptr>=(unsigned int)tocompare) {
    // end of an orig/to page
    // compute the impact of this page for the new page
    samplingDataConvert(current_ptr);
    // prepare for an new orig page
    unsigned int tmp_index=(unsigned int)((unsigned int)orig->ptr-gcbaseva)/(BAMBOO_PAGE_SIZE);
    gc_cache_revise_information.orig_page_start_va=orig->ptr;
    gc_cache_revise_information.orig_page_end_va=gcbaseva+(BAMBOO_PAGE_SIZE)*(unsigned int)(tmp_index+1);
    gc_cache_revise_information.orig_page_index=tmp_index;
    gc_cache_revise_information.to_page_start_va=to->ptr;
    if(closeToPage) {
      gc_cache_revise_information.to_page_end_va=gcbaseva+(BAMBOO_PAGE_SIZE)*(((unsigned int)(to->ptr)-gcbaseva)/(BAMBOO_PAGE_SIZE)+1);
      gc_cache_revise_information.to_page_index=((unsigned int)(to->ptr)-gcbaseva)/(BAMBOO_PAGE_SIZE);
    }
  }
} 

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
#define CACHEADAPT_SAMPLING_DATA_CONVERT(p) samplingDataConvert((p))
#define CACHEADAPT_COMPLETE_PAGE_CONVERT(o, t, p, b) \
  completePageConvert((o), (t), (p), (b));

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
