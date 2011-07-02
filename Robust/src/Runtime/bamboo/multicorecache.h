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

/* This function initialize the gc_cache_revise_information. It should be 
 * invoked before we start compaction.
 */
INLINE static void samplingDataReviseInit(struct moveHelper * orig,struct moveHelper * to) {
  // initialize the destination page info
  gc_cache_revise_information.to_page_start_va=to->ptr;
  unsigned int toindex=(unsigned INTPTR)(to->base-gcbaseva)/BAMBOO_PAGE_SIZE;
  gc_cache_revise_information.to_page_end_va=gcbaseva+BAMBOO_PAGE_SIZE*(toindex+1);
  gc_cache_revise_information.to_page_index=toindex;
  // initilaize the original page info
  unsigned int origindex=((unsigned INTPTR)(orig->base-gcbaseva))/BAMBOO_PAGE_SIZE;
  gc_cache_revise_information.orig_page_start_va=orig->ptr;
  gc_cache_revise_information.orig_page_end_va=gcbaseva+BAMBOO_PAGE_SIZE*(origindex+1);
  gc_cache_revise_information.orig_page_index=origindex;
}

/* This function computes the revised profiling data of the first closed destination 
 * page of an object that acrosses multiple pages
 */
INLINE static void firstPageConvert(bool origclosefirst, unsigned INTPTR main_factor, unsigned INTPTR delta_factor) {
  unsigned INTPTR topage=gc_cache_revise_information.to_page_index;
  unsigned INTPTR oldpage=gc_cache_revise_information.orig_page_index;
  int * newtable=&gccachesamplingtbl_r[topage];
  int * oldtable=&gccachesamplingtbl[oldpage];
  // compute the revised profiling info for the start destination page
  if(origclosefirst) {
    // the start original page closes first, now compute the revised profiling
    // info for the start destination page.
    // The start destination page = the rest of the start original page + 
    //                              delta_fator from the next original page
    int * oldtable_next=&gccachesamplingtbl[oldpage+1];
    for(int tt = 0; tt < NUMCORESACTIVE; tt++) {
      (*newtable)=((*newtable)+(*oldtable)*main_factor+(*oldtable_next)*delta_factor);
      newtable=(int*)(((char *)newtable)+size_cachesamplingtbl_local_r);
      oldtable=(int*) (((char *)oldtable)+size_cachesamplingtbl_local);
      oldtable_next=(int*) (((char *)oldtable_next)+size_cachesamplingtbl_local);
    }
    // close the start original page 
    gc_cache_revise_information.orig_page_start_va+=main_factor+delta_factor;
    gc_cache_revise_information.orig_page_end_va+=BAMBOO_PAGE_SIZE;
    gc_cache_revise_information.orig_page_index++;
  } else {
    // the start destination page closes first, now compute the revised 
    // profiling info for it.
    for(int tt = 0; tt < NUMCORESACTIVE; tt++) {
      (*newtable)=((*newtable)+(*oldtable)*main_factor);
      newtable=(int*)(((char *)newtable)+size_cachesamplingtbl_local_r);
      oldtable=(int*) (((char *)oldtable)+size_cachesamplingtbl_local);
    }
    // record the new start of the original page
    gc_cache_revise_information.orig_page_start_va+=main_factor;
  }
  // close the start original page and destination page
  gc_cache_revise_information.to_page_start_va=gc_cache_revise_information.to_page_end_va;
  gc_cache_revise_information.to_page_end_va+=BAMBOO_PAGE_SIZE;
  gc_cache_revise_information.to_page_index++;
}

/* This function computes the revised profiling info for closed destination 
 * pages that are occupied by one object that acrosses multiple pages.
 * the destination page = main_factor from the first unclosed original page 
 *                       + delta_factor from the next unclosed original page
 */
INLINE static void restClosedPageConvert(void * current_ptr, unsigned INTPTR main_factor, unsigned INTPTR delta_factor) {
  while(gc_cache_revise_information.to_page_end_va<=current_ptr) {
    unsigned INTPTR topage=gc_cache_revise_information.to_page_index;
    unsigned INTPTR oldpage=gc_cache_revise_information.orig_page_index;
    int *newtable=&gccachesamplingtbl_r[topage];
    int *oldtable=&gccachesamplingtbl[oldpage];
    int *oldtable_next=&gccachesamplingtbl[oldpage+1];

    for(int tt = 0; tt < NUMCORESACTIVE; tt++) {
      (*newtable)=((*newtable)+(*oldtable)*main_factor+(*oldtable_next)*delta_factor);
      newtable=(int*)(((char *)newtable)+size_cachesamplingtbl_local_r);
      oldtable=(int*) (((char *)oldtable)+size_cachesamplingtbl_local);
      oldtable_next=(int*) (((char *)oldtable_next)+size_cachesamplingtbl_local);
    }

    // close the original page and the destination page
    gc_cache_revise_information.orig_page_start_va+=BAMBOO_PAGE_SIZE;
    gc_cache_revise_information.orig_page_end_va+=BAMBOO_PAGE_SIZE;
    gc_cache_revise_information.orig_page_index++;
    gc_cache_revise_information.to_page_start_va=gc_cache_revise_information.to_page_end_va;
    gc_cache_revise_information.to_page_end_va+=BAMBOO_PAGE_SIZE;
    gc_cache_revise_information.to_page_index++;
  }
}

/* This function computes the revised profiling info for the last
 * destination page of an object that acrosses multiple pages.
 */
INLINE static void lastPageConvert(void * current_ptr) {
  unsigned INTPTR to_factor=current_ptr-gc_cache_revise_information.to_page_start_va;
  unsigned INTPTR topage=gc_cache_revise_information.to_page_index;
  unsigned INTPTR oldpage=gc_cache_revise_information.orig_page_index;
  int *newtable=&gccachesamplingtbl_r[topage];
  int *oldtable=&gccachesamplingtbl[oldpage];

  for(int tt = 0; tt < NUMCORESACTIVE; tt++) {
    (*newtable)=((*newtable)+(*oldtable)*to_factor);
    newtable=(int*)(((char *)newtable)+size_cachesamplingtbl_local_r);
    oldtable=(int*) (((char *)oldtable)+size_cachesamplingtbl_local);
  }
  // do not need to set gc_cache_revise_information here for the last 
  // original/destination page as it will be set in completePageConvert()
}

/* This function converts multiple original pages profiling data to multiple 
 * destination pages' profiling data
 */
INLINE static void samplingDataConvertMultiple(void * current_ptr) {
  // first decide which page close first: original or destination?
  unsigned INTPTR to_factor=(unsigned INTPTR)(gc_cache_revise_information.to_page_end_va-gc_cache_revise_information.to_page_start_va);
  unsigned INTPTR orig_factor=(unsigned INTPTR)(gc_cache_revise_information.orig_page_end_va-gc_cache_revise_information.orig_page_start_va);
  bool origclosefirst=to_factor>orig_factor;
  unsigned INTPTR delta_factor=(origclosefirst)?(to_factor-orig_factor):(orig_factor-to_factor);
  unsigned INTPTR main_factor=(origclosefirst)?orig_factor:to_factor;

  // compute the revised profiling info for the start destination page
  firstPageConvert(origclosefirst, main_factor, delta_factor);
  // update main_factor/delta_factor
  if(origclosefirst) {
    // for the following destination pages that are fully used:
    // the destination page = (page_size-delta_factor) from the 
    //                        first unclosed original page + delta_factor 
    //                        from the next unclosed original page
    // we always use main_factor to represent the factor from the first 
    // unclosed original page
    main_factor=BAMBOO_PAGE_SIZE-delta_factor;
  } else {
    // for the following destination pages that are fully used:
    // the destination page = delta_factor from the first unclosed original    
    //                        page + (page_size-delta_factor) from the next 
    //                        unclosed original page
    // we always use main_factor to represent the factor from the first
    // unclosed original page
    main_factor=delta_factor;
    delta_factor=BAMBOO_PAGE_SIZE-delta_factor;
  }

  // compute the revised profiling info for the following closed destination
  // pages
  restClosedPageConvert(current_ptr, main_factor, delta_factor);

  // compute the revised profiling info for the last destination page if needed
  lastPageConvert(current_ptr);
}

/* This function converts originial pages' profiling data to destination pages'
 * profiling data.
 * The parameter current_ptr indicates the current position in the destination 
 * pages.
 * Note that there could be objects that across pages. In such cases, there are 
 * multiple orig/to pages are closed and all these to pages' profiling data 
 * should be properly updated.
 */
INLINE static void samplingDataConvert(void * current_ptr) {
  if(gc_cache_revise_information.to_page_end_va<current_ptr) {
    // multiple pages are closed
    samplingDataConvertMultiple(current_ptr);
  } else {
    unsigned INTPTR tmp_factor=(unsigned INTPTR)(current_ptr-gc_cache_revise_information.to_page_start_va);
    if(tmp_factor) {
      unsigned INTPTR topage=gc_cache_revise_information.to_page_index;
      unsigned INTPTR oldpage=gc_cache_revise_information.orig_page_index;
      int * newtable=&gccachesamplingtbl_r[topage];
      int * oldtable=&gccachesamplingtbl[oldpage];
  
      for(int tt = 0; tt < NUMCORESACTIVE; tt++) {
        (*newtable)=((*newtable)+(*oldtable)*tmp_factor);
        newtable=(int*)(((char *)newtable)+size_cachesamplingtbl_local_r);
        oldtable=(int*) (((char *)oldtable)+size_cachesamplingtbl_local);
      }
    }
  }
} 

/*
 */
INLINE static void completePageConvert(void * origptr, void * toptr, void * current_ptr) {
  bool closeToPage=(unsigned int)(toptr)>=(unsigned int)(gc_cache_revise_information.to_page_end_va);
  bool closeOrigPage=(unsigned int)(origptr)>=(unsigned int)(gc_cache_revise_information.orig_page_end_va);
  if(closeToPage||closeOrigPage) {
    // end of one or more orig/to page
    // compute the impact of the original page(s) for the desitination page(s)
    samplingDataConvert(current_ptr);
    // prepare for an new orig page
    unsigned INTPTR tmp_index=((unsigned INTPTR)(origptr-gcbaseva))/BAMBOO_PAGE_SIZE;
    gc_cache_revise_information.orig_page_start_va=origptr;
    gc_cache_revise_information.orig_page_end_va=gcbaseva+BAMBOO_PAGE_SIZE*(tmp_index+1);
    gc_cache_revise_information.orig_page_index=tmp_index;
    gc_cache_revise_information.to_page_start_va=toptr;
    if(closeToPage) {
      unsigned INTPTR to_index=((unsigned INTPTR)(toptr-gcbaseva))/BAMBOO_PAGE_SIZE;
      gc_cache_revise_information.to_page_end_va=gcbaseva+BAMBOO_PAGE_SIZE*(to_index+1);
      gc_cache_revise_information.to_page_index=to_index;
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
    if(1) { \
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
