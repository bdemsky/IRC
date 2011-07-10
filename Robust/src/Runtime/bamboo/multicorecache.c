#ifdef GC_CACHE_ADAPT
#include "multicorecache.h"
#include "multicoremsg.h"
#include "multicoregcprofile.h"

gc_cache_revise_info_t gc_cache_revise_information;

/* This function initialize the gc_cache_revise_information. It should be 
 * invoked before we start compaction.
 */
void samplingDataReviseInit(struct moveHelper * orig,struct moveHelper * to) {
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
void firstPageConvert(bool origclosefirst, unsigned INTPTR main_factor, unsigned INTPTR delta_factor) {
  unsigned INTPTR topage=gc_cache_revise_information.to_page_index*NUMCORESACTIVE;
  unsigned INTPTR oldpage=gc_cache_revise_information.orig_page_index*NUMCORESACTIVE;
  int * newtable=&gccachesamplingtbl_r[topage];
  int * oldtable=&gccachesamplingtbl[oldpage];
  // compute the revised profiling info for the start destination page
  if(origclosefirst) {
    // the start original page closes first, now compute the revised profiling
    // info for the start destination page.
    // The start destination page = the rest of the start original page + 
    //                              delta_fator from the next original page
    int * oldtable_next=&gccachesamplingtbl[oldpage+NUMCORESACTIVE];
    for(int tt = 0; tt < NUMCORESACTIVE; tt++) {
      (*newtable)=(*newtable)+((*oldtable)*main_factor+(*oldtable_next)*delta_factor)>>BAMBOO_PAGE_SIZE_BITS;
      newtable++;
      oldtable++;
      oldtable_next++;
    }
    // close the start original page 
    gc_cache_revise_information.orig_page_start_va+=main_factor+delta_factor;
    gc_cache_revise_information.orig_page_end_va+=BAMBOO_PAGE_SIZE;
    gc_cache_revise_information.orig_page_index++;
  } else {
    // the start destination page closes first, now compute the revised 
    // profiling info for it.
    for(int tt = 0; tt < NUMCORESACTIVE; tt++) {
      (*newtable)=(*newtable)+((*oldtable)*main_factor)>>BAMBOO_PAGE_SIZE_BITS;
      newtable++;
      oldtable++;
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
void restClosedPageConvert(void * current_ptr, unsigned INTPTR main_factor, unsigned INTPTR delta_factor) {
  while(gc_cache_revise_information.to_page_end_va<=current_ptr) {
    unsigned INTPTR topage=gc_cache_revise_information.to_page_index*NUMCORESACTIVE;
    unsigned INTPTR oldpage=gc_cache_revise_information.orig_page_index*NUMCORESACTIVE;
    int *newtable=&gccachesamplingtbl_r[topage];
    int *oldtable=&gccachesamplingtbl[oldpage];
    int *oldtable_next=&gccachesamplingtbl[oldpage+NUMCORESACTIVE];

    for(int tt = 0; tt < NUMCORESACTIVE; tt++) {
      (*newtable)=(*newtable)+((*oldtable)*main_factor+(*oldtable_next)*delta_factor)>>BAMBOO_PAGE_SIZE_BITS;
      newtable++;
      oldtable++;
      oldtable_next++;
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
void lastPageConvert(void * current_ptr) {
  unsigned INTPTR to_factor=current_ptr-gc_cache_revise_information.to_page_start_va;
  unsigned INTPTR topage=gc_cache_revise_information.to_page_index*NUMCORESACTIVE;
  unsigned INTPTR oldpage=gc_cache_revise_information.orig_page_index*NUMCORESACTIVE;
  int *newtable=&gccachesamplingtbl_r[topage];
  int *oldtable=&gccachesamplingtbl[oldpage];

  for(int tt = 0; tt < NUMCORESACTIVE; tt++) {
    (*newtable)=(*newtable)+((*oldtable)*to_factor)>>BAMBOO_PAGE_SIZE_BITS;
    newtable++;
    oldtable++;
  }
  // do not need to set gc_cache_revise_information here for the last 
  // original/destination page as it will be set in completePageConvert()
}

/* This function converts multiple original pages profiling data to multiple 
 * destination pages' profiling data
 */
void samplingDataConvertMultiple(void * current_ptr) {
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
void samplingDataConvert(void * current_ptr) {
  if(gc_cache_revise_information.to_page_end_va<current_ptr) {
    // multiple pages are closed
    samplingDataConvertMultiple(current_ptr);
  } else {
    unsigned INTPTR tmp_factor=(unsigned INTPTR)(current_ptr-gc_cache_revise_information.to_page_start_va);
    if(tmp_factor) {
      unsigned INTPTR topage=gc_cache_revise_information.to_page_index*NUMCORESACTIVE;
      unsigned INTPTR oldpage=gc_cache_revise_information.orig_page_index*NUMCORESACTIVE;
      int * newtable=&gccachesamplingtbl_r[topage];
      int * oldtable=&gccachesamplingtbl[oldpage];
  
      for(int tt = 0; tt < NUMCORESACTIVE; tt++) {
        (*newtable)=(*newtable)+((*oldtable)*tmp_factor)>>BAMBOO_PAGE_SIZE_BITS;
        newtable++;
        oldtable++;
      }
    }
  }
} 

/* This function computes the impact of an original page on a destination page
 * in terms of profiling data. It can only be invoked when there is an original 
 * page that is closed or a destination page that is closed. When finished 
 * computing the revised profiling info of the current destination page, it 
 * sets up the gc_cache_revise_information to the latest position in the 
 * original page and the destination page.
 */
void completePageConvert(void * origptr, void * toptr, void * current_ptr) {
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

// prepare for cache adaption:
//   -- flush the shared heap
//   -- clean dtlb entries
//   -- change cache strategy
void cacheAdapt_gc(bool isgccachestage) {
  // flush the shared heap
  BAMBOO_CACHE_FLUSH_L2();

  // clean the dtlb entries
  BAMBOO_CLEAN_DTLB();

  // change the cache strategy
  gccachestage = isgccachestage;
} 

// the master core decides how to adapt cache strategy for the mutator 
// according to collected statistic data

// find the core that accesses the page #page_index most
#define CACHEADAPT_FIND_HOTTEST_CORE(page_index,hottestcore,hotfreq) \
  { \
    int *local_tbl=&gccachesamplingtbl_r[page_index*NUMCORESACTIVE]; \
    for(int i = 0; i < NUMCORESACTIVE; i++) { \
      int freq = *local_tbl; \
      local_tbl++; \
      if(hotfreq < freq) { \
        hotfreq = freq; \
        hottestcore = i; \
      } \
    } \
  }
// find the core that accesses the page #page_index most and comput the total
// access time of the page at the same time
#define CACHEADAPT_FIND_HOTTEST_CORE_W_TOTALFREQ(page_index,hottestcore,hotfreq,totalfreq) \
  { \
    int *local_tbl=&gccachesamplingtbl_r[page_index*NUMCORESACTIVE]; \
    for(int i = 0; i < NUMCORESACTIVE; i++) { \
      int freq = *local_tbl; \
      local_tbl++; \
      totalfreq += freq; \
      if(hotfreq < freq) { \
        hotfreq = freq; \
        hottestcore = i; \
      } \
    } \
  }
// Set the policy as hosted by coren
// NOTE: (x,y) should be changed to (x+1, y+1)!!!
#define CACHEADAPT_POLICY_SET_HOST_CORE(policy, coren) \
  { \
    (policy).cache_mode = BAMBOO_CACHE_MODE_COORDS; \    
    (policy).lotar_x = bamboo_cpu2coords[2*(coren)]+1; \
    (policy).lotar_y = bamboo_cpu2coords[2*(coren)+1]+1; \
  }
// store the new policy information at tmp_p in gccachepolicytbl
#define CACHEADAPT_CHANGE_POLICY_4_PAGE(tmp_p,page_index,policy) \
  { \
    ((int*)(tmp_p))[page_index] = (policy).word; \
  }

// make all pages hfh
void cacheAdapt_policy_h4h(int coren){
  unsigned int page_num=(BAMBOO_SHARED_MEM_SIZE)/(BAMBOO_PAGE_SIZE);
  unsigned int page_gap=page_num/NUMCORESACTIVE;
  unsigned int page_index=page_gap*coren;
  unsigned int page_index_end=(coren==NUMCORESACTIVE-1)?page_num:(page_index+page_gap);
  VA page_sva = gcbaseva+(BAMBOO_PAGE_SIZE)*page_index;
  int * tmp_p = gccachepolicytbl;
  for(; page_index < page_index_end; page_index++) {
    bamboo_cache_policy_t policy = {0};
    policy.cache_mode = BAMBOO_CACHE_MODE_HASH;
    CACHEADAPT_CHANGE_POLICY_4_PAGE(tmp_p,page_index,policy);
    page_sva += BAMBOO_PAGE_SIZE;
  }
} 

// make all pages local as non-cache-adaptable gc local mode
void cacheAdapt_policy_local(int coren){
  unsigned int page_num=(BAMBOO_SHARED_MEM_SIZE)/(BAMBOO_PAGE_SIZE);
  unsigned int page_gap=page_num/NUMCORESACTIVE;
  unsigned int page_index=page_gap*coren;
  unsigned int page_index_end=(coren==NUMCORESACTIVE-1)?page_num:(page_index+page_gap);
  VA page_sva = gcbaseva+(BAMBOO_PAGE_SIZE)*page_index;
  int * tmp_p = gccachepolicytbl;
  for(; page_index < page_index_end; page_index++) {
    bamboo_cache_policy_t policy = {0};
    unsigned int block = 0;
    BLOCKINDEX(block, (void *) page_sva);
    unsigned int coren = gc_block2core[block%(NUMCORES4GC*2)];
    CACHEADAPT_POLICY_SET_HOST_CORE(policy, coren);
    CACHEADAPT_CHANGE_POLICY_4_PAGE(tmp_p,page_index,policy);
    page_sva += BAMBOO_PAGE_SIZE;
  }
} 

void cacheAdapt_policy_hottest(int coren){
  unsigned int page_num=(BAMBOO_SHARED_MEM_SIZE)/(BAMBOO_PAGE_SIZE);
  unsigned int page_gap=page_num/NUMCORESACTIVE;
  unsigned int page_index=page_gap*coren;
  unsigned int page_index_end=(coren==NUMCORESACTIVE-1)?page_num:(page_index+page_gap);
  VA page_sva = gcbaseva+(BAMBOO_PAGE_SIZE)*page_index;
  int * tmp_p = gccachepolicytbl;
  for(; page_index < page_index_end; page_index++) {
    bamboo_cache_policy_t policy = {0};
    unsigned int hottestcore = 0;
    unsigned int hotfreq = 0;
    CACHEADAPT_FIND_HOTTEST_CORE(page_index,hottestcore,hotfreq);
    // TODO
    // Decide the cache strategy for this page
    // If decide to adapt a new cache strategy, write into the shared block of
    // the gcsharedsamplingtbl. The mem recording information that has been 
    // written is enough to hold the information.
    // Format: page start va + cache strategy(hfh/(host core+[x,y]))
    if(hotfreq != 0) {
      // locally cache the page in the hottest core
      CACHEADAPT_POLICY_SET_HOST_CORE(policy, hottestcore);
    }
    CACHEADAPT_CHANGE_POLICY_4_PAGE(tmp_p,page_index,policy);
    page_sva += BAMBOO_PAGE_SIZE;
  }
} 

#define GC_CACHE_ADAPT_DOMINATE_THRESHOLD  1
// cache the page on the core that accesses it the most if that core accesses 
// it more than (GC_CACHE_ADAPT_DOMINATE_THRESHOLD)% of the total.  Otherwise,
// h4h the page.
void cacheAdapt_policy_dominate(int coren){
  unsigned int page_num=(BAMBOO_SHARED_MEM_SIZE)/(BAMBOO_PAGE_SIZE);
  unsigned int page_gap=page_num/NUMCORESACTIVE;
  unsigned int page_index=page_gap*coren;
  unsigned int page_index_end=(coren==NUMCORESACTIVE-1)?page_num:(page_index+page_gap);
  VA page_sva = gcbaseva+(BAMBOO_PAGE_SIZE)*page_index;
  int * tmp_p = gccachepolicytbl;
  for(; page_index < page_index_end; page_index++) {
    bamboo_cache_policy_t policy = {0};
    unsigned int hottestcore = 0;
    unsigned int totalfreq = 0;
    unsigned int hotfreq = 0;
    CACHEADAPT_FIND_HOTTEST_CORE_W_TOTALFREQ(page_index,hottestcore,hotfreq,totalfreq);
    // Decide the cache strategy for this page
    // If decide to adapt a new cache strategy, write into the shared block of
    // the gcpolicytbl 
    // Format: page start va + cache policy
    if(hotfreq != 0) {
      totalfreq=totalfreq>>GC_CACHE_ADAPT_DOMINATE_THRESHOLD;
      if((unsigned int)hotfreq < (unsigned int)totalfreq) {
        // use hfh
        policy.cache_mode = BAMBOO_CACHE_MODE_HASH;
        /*unsigned int block = 0;
        BLOCKINDEX(block, (void *) page_sva);
        unsigned int coren = gc_block2core[block%(NUMCORES4GC*2)];
        CACHEADAPT_POLICY_SET_HOST_CORE(policy, coren);*/
      } else {
        // locally cache the page in the hottest core
        CACHEADAPT_POLICY_SET_HOST_CORE(policy, hottestcore);
      }     
    }
    CACHEADAPT_CHANGE_POLICY_4_PAGE(tmp_p,page_index,policy);
    page_sva += BAMBOO_PAGE_SIZE;
  }
}

unsigned int cacheAdapt_decision(int coren) {
  BAMBOO_CACHE_MF();
  // check the statistic data
  // for each page, decide the new cache strategy
#ifdef GC_CACHE_ADAPT_POLICY1
  cacheAdapt_policy_h4h(coren);
#elif defined GC_CACHE_ADAPT_POLICY2
  cacheAdapt_policy_local(coren);
#elif defined GC_CACHE_ADAPT_POLICY3
  cacheAdapt_policy_hottest(coren);
#elif defined GC_CACHE_ADAPT_POLICY4
  cacheAdapt_policy_dominate(coren);
#endif
}

// adapt the cache strategy for the mutator
void cacheAdapt_mutator() {
  BAMBOO_CACHE_MF();
  // check the changes and adapt them
  int * tmp_p = gccachepolicytbl;
  unsigned int page_sva = gcbaseva;
  for(; page_sva<gctopva; page_sva+=BAMBOO_PAGE_SIZE) {
    // read out the policy
    bamboo_cache_policy_t policy = (bamboo_cache_policy_t)(*(tmp_p));
    // adapt the policy
    if(policy.word != 0) {
      bamboo_adapt_cache_policy(page_sva,policy,BAMBOO_PAGE_SIZE);
    }
    tmp_p += 1;
  }
}

// Cache adapt phase process for clients
void cacheAdapt_phase_client() {
  WAITFORGCPHASE(CACHEPOLICYPHASE);
  GC_PRINTF("Start cachepolicy phase\n");
  cacheAdapt_decision(BAMBOO_NUM_OF_CORE);
  //send init finish msg to core coordinator
  send_msg_2(STARTUPCORE, GCFINISHCACHEPOLICY, BAMBOO_NUM_OF_CORE);
  GC_PRINTF("Finish cachepolicy phase\n");

  WAITFORGCPHASE(PREFINISHPHASE);
  GC_PRINTF("Start prefinish phase\n");
  // cache adapt phase
  cacheAdapt_mutator();
  cacheAdapt_gc(false);
  //send init finish msg to core coordinator
  send_msg_2(STARTUPCORE, GCFINISHPREF, BAMBOO_NUM_OF_CORE);
  GC_PRINTF("Finish prefinish phase\n");
  CACHEADAPT_SAMPLING_RESET();
  if(BAMBOO_NUM_OF_CORE < NUMCORESACTIVE) {
    // zero out the gccachesamplingtbl
    BAMBOO_MEMSET_WH(gccachesamplingtbl_local,0,size_cachesamplingtbl_local);  
    BAMBOO_MEMSET_WH(gccachesamplingtbl_local_r,0,size_cachesamplingtbl_local_r);
  }
}

extern unsigned long long gc_output_cache_policy_time;

// Cache adpat phase process for the master
void cacheAdapt_phase_master() {
  GCPROFILE_ITEM();
  unsigned long long tmpt = BAMBOO_GET_EXE_TIME();
  CACHEADAPT_OUTPUT_CACHE_SAMPLING_R();
  gc_output_cache_policy_time += (BAMBOO_GET_EXE_TIME()-tmpt);
  // let all cores to parallelly process the revised profile data and decide 
  // the cache policy for each page
  gc_status_info.gcphase = CACHEPOLICYPHASE;
  GC_SEND_MSG_1_TO_CLIENT(GCSTARTCACHEPOLICY);
  GC_PRINTF("Start cachepolicy phase \n");
  // cache adapt phase
  cacheAdapt_decision(BAMBOO_NUM_OF_CORE);
  GC_CHECK_ALL_CORE_STATUS();
  BAMBOO_CACHE_MF();

  // let all cores to adopt new policies
  gc_status_info.gcphase = PREFINISHPHASE;
  // Note: all cores should flush their runtime data including non-gc cores
  GC_SEND_MSG_1_TO_CLIENT(GCSTARTPREF);
  GC_PRINTF("Start prefinish phase \n");
  // cache adapt phase
  cacheAdapt_mutator();
  cacheAdapt_gc(false);
  GC_CHECK_ALL_CORE_STATUS();
  
  CACHEADAPT_SAMPLING_RESET();
  if(BAMBOO_NUM_OF_CORE < NUMCORESACTIVE) {
    // zero out the gccachesamplingtbl
    BAMBOO_MEMSET_WH(gccachesamplingtbl_local,0,size_cachesamplingtbl_local);
    BAMBOO_MEMSET_WH(gccachesamplingtbl_local_r,0,size_cachesamplingtbl_local_r);
    BAMBOO_MEMSET_WH(gccachepolicytbl,0,size_cachepolicytbl);
  }
}

// output original cache sampling data for each page
void gc_output_cache_sampling() {
  //extern volatile bool gc_profile_flag;
  //if(!gc_profile_flag) return;
  unsigned int page_index = 0;
  VA page_sva = 0;
  unsigned int page_num = (BAMBOO_SHARED_MEM_SIZE) / (BAMBOO_PAGE_SIZE);
  for(page_index = 0; page_index < page_num; page_index++) {
    page_sva = gcbaseva + (BAMBOO_PAGE_SIZE) * page_index;
    unsigned int block = 0;
    BLOCKINDEX(block, (void *) page_sva);
    unsigned int coren = gc_block2core[block%(NUMCORES4GC*2)];
    printf("%x,  %d,  %d,  ",(int)page_sva,page_index,coren);
    int * local_tbl = &gccachesamplingtbl[page_index*NUMCORESACTIVE];
    for(int i = 0; i < NUMCORESACTIVE; i++) {
      int freq = *local_tbl;
      local_tbl++;
      //if(freq != 0) {
        printf("%d,  ", freq);
      //}
    }
    printf("\n");
  }
  printf("=================\n");
} 

// output revised cache sampling data for each page after compaction
void gc_output_cache_sampling_r() {
  //extern volatile bool gc_profile_flag;
  //if(!gc_profile_flag) return;
  // TODO summary data
  unsigned int sumdata[NUMCORESACTIVE][NUMCORESACTIVE];
  for(int i = 0; i < NUMCORESACTIVE; i++) {
    for(int j = 0; j < NUMCORESACTIVE; j++) {
      sumdata[i][j] = 0;
    }
  }
  tprintf("cache sampling_r \n");
  unsigned int page_index = 0;
  VA page_sva = 0;
  unsigned int page_num = (BAMBOO_SHARED_MEM_SIZE) / (BAMBOO_PAGE_SIZE);
  for(page_index = 0; page_index < page_num; page_index++) {
    page_sva = gcbaseva + (BAMBOO_PAGE_SIZE) * page_index;
    unsigned int block = 0;
    BLOCKINDEX(block, (void *)page_sva);
    unsigned int coren = gc_block2core[block%(NUMCORES4GC*2)];
    printf(" %x,  %d,  %d,  ",(int)page_sva,page_index,coren);
    int accesscore = 0; // TODO
    int * local_tbl = &gccachesamplingtbl_r[page_index*NUMCORESACTIVE];
    for(int i = 0; i < NUMCORESACTIVE; i++) {
      int freq = *local_tbl; 
      printf("%d,  ", freq);
      if(freq != 0) {
        accesscore++;// TODO
      }
      local_tbl++;
    }
    if(accesscore!=0) {
      int * local_tbl = &gccachesamplingtbl_r[page_index*NUMCORESACTIVE];
      for(int i = 0; i < NUMCORESACTIVE; i++) {
        int freq = *local_tbl;
        sumdata[accesscore-1][i]+=freq;
        local_tbl++;
      }
    }
  
    printf("\n");
  }
  printf("+++++\n");
  // TODO printout the summary data
  for(int i = 0; i < NUMCORESACTIVE; i++) {
    printf("%d  ", i);
    for(int j = 0; j < NUMCORESACTIVE; j++) {
      printf(" %d  ", sumdata[j][i]);
    }
    printf("\n");
  }
  printf("=================\n");
} 
#endif // GC_CACHE_ADAPT
