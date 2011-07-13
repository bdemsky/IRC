#ifdef GC_CACHE_ADAPT
#include "multicorecache.h"
#include "multicoremsg.h"
#include "multicoregcprofile.h"

void cacheadapt_finish_src_page(void *srcptr, void *tostart, void *tofinish) {
  unsigned int srcpage=(srcptr-gcbaseva)>>BAMBOO_PAGE_SIZE_BITS;
  unsigned int dstpage=(tostart-gcbase)>>BAMBOO_PAGE_SIZE_BITS;
  unsigned int numbytes=tofinish-tostart;
  
  unsigned int * oldtable=&gccachesamplingtbl[srcpage*NUMCORESACTIVE];
  unsigned int * newtable=&gccachesamplingtbl_r[dstpage*NUMCORESACTIVE];
  
  unsigned int page64th=numbytes>>(BAMBOO_PAGE_SIZE_BITS-6);

  for(int core = 0; core < NUMCORESACTIVE; core++) {
    (*newtable)+=page64th*(*oldtable);
    newtable++;
    oldtable++;
  }  
}

void cacheadapt_finish_dst_page(void *origptr, void *tostart, void *toptr, unsigned int bytesneeded) {
  unsigned int numbytes=toptr-tostart;

  void *tobound=(tostart&~(BAMBOO_PAGE_SIZE-1))+BAMBOO_PAGE_SIZE;
  void *origbound=(origstart&~(BAMBOO_PAGE_SIZE-1))+BAMBOO_PAGE_SIZE;
  
  unsigned int topage=(tostart-gcbase)>>BAMBOO_PAGE_SIZE_BITS; 
  unsigned int origpage=(origptr-gcbaseva)>>BAMBOO_PAGE_SIZE_BITS;

  unsigned int * totable=&gccachesamplingtbl_r[topage*NUMCORESACTIVE];
  unsigned int * origtable=&gccachesamplingtbl[origpage*NUMCORESACTIVE];

  unsigned int remaintobytes=tobound-toptr;
  unsigned int remainorigbytes=origbound-origptr;

  do {
    //round source bytes down....don't want to close out page if not necessary
    remainorigbytes=(remainorigbytes>bytesneeded)?bytesneeded:remainorigbytes;

    if (remaintobytes<=remainorigbytes) {
      //Need to close out to page

      numbytes+=remaintobytes;
      unsigned int page64th=numbytes>>(BAMBOO_PAGE_SIZE_BITS-6);

      for(int core = 0; core < NUMCORESACTIVE; core++) {
	(*totable)=(*totable+page64th*(*origtable))>>6;
	totable++;
	origtable++;
      }
      toptr+=remaintobytes;
      origptr+=remaintobytes;
      bytesneeded-=remaintobytes;
      topage++;//to page is definitely done
      tobound+=BAMBOO_PAGE_SIZE;
      origpage=(origptr-gcbaseva)>>BAMBOO_PAGE_SIZE_BITS;//handle exact match case
      origbound=(origptr&~(BAMBOO_PAGE_SIZE-1))+BAMBOO_PAGE_SIZE;
    } else {
      //Finishing off orig page

      numbytes+=remainorigbytes;
      unsigned int page64th=numbytes>>(BAMBOO_PAGE_SIZE_BITS-6);
      
      for(int core = 0; core < NUMCORESACTIVE; core++) {
	(*totable)+=page64th*(*origtable);
	totable++;
	origtable++;
      }
      toptr+=remainorigbytes;
      origptr+=remainorigbytes;
      bytesneeded-=remainorigbytes;
      origpage++;//just orig page is done
      origbound+=BAMBOO_PAGE_SIZE;
    }
    totable=&gccachesamplingtbl_r[topage*NUMCORESACTIVE];
    origtable=&gccachesamplingtbl[origpage*NUMCORESACTIVE];
    
    remaintobytes=tobound-toptr;
    remainorigbytes=origbound-origptr;
    
    numbytes=0;
  } while(bytesneeded!=0);
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

  if(isgccachestage) {
    bamboo_install_dtlb_handler_for_gc();
  } else {
    bamboo_install_dtlb_handler_for_mutator();
  }
} 

// the master core decides how to adapt cache strategy for the mutator 
// according to collected statistic data

// find the core that accesses the page #page_index most
#define CACHEADAPT_FIND_HOTTEST_CORE(page_index,hottestcore,hotfreq) \
  { \
    unsigned int *local_tbl=&gccachesamplingtbl_r[page_index*NUMCORESACTIVE];	\
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
    unsigned int *local_tbl=&gccachesamplingtbl_r[page_index*NUMCORESACTIVE];	\
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
  unsigned int page_num=(BAMBOO_SHARED_MEM_SIZE)>>(BAMBOO_PAGE_SIZE_BITS);
  unsigned int page_gap=page_num/NUMCORESACTIVE;
  unsigned int page_index=page_gap*coren;
  unsigned int page_index_end=(coren==NUMCORESACTIVE-1)?page_num:(page_index+page_gap);
  VA page_sva = gcbaseva+(BAMBOO_PAGE_SIZE)*page_index;
  unsigned int * tmp_p = gccachepolicytbl;
  for(; page_index < page_index_end; page_index++) {
    bamboo_cache_policy_t policy = {0};
    policy.cache_mode = BAMBOO_CACHE_MODE_HASH;
    CACHEADAPT_CHANGE_POLICY_4_PAGE(tmp_p,page_index,policy);
    page_sva += BAMBOO_PAGE_SIZE;
  }
} 

// make all pages local as non-cache-adaptable gc local mode
void cacheAdapt_policy_local(int coren){
  unsigned int page_num=(BAMBOO_SHARED_MEM_SIZE)>>(BAMBOO_PAGE_SIZE_BITS);
  unsigned int page_gap=page_num/NUMCORESACTIVE;
  unsigned int page_index=page_gap*coren;
  unsigned int page_index_end=(coren==NUMCORESACTIVE-1)?page_num:(page_index+page_gap);
  VA page_sva = gcbaseva+(BAMBOO_PAGE_SIZE)*page_index;
  unsigned int * tmp_p = gccachepolicytbl;
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
  unsigned int page_num=(BAMBOO_SHARED_MEM_SIZE)>>(BAMBOO_PAGE_SIZE_BITS);
  unsigned int page_gap=page_num/NUMCORESACTIVE;
  unsigned int page_index=page_gap*coren;
  unsigned int page_index_end=(coren==NUMCORESACTIVE-1)?page_num:(page_index+page_gap);
  VA page_sva = gcbaseva+(BAMBOO_PAGE_SIZE)*page_index;
  unsigned int * tmp_p = gccachepolicytbl;
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
  unsigned int page_num=(BAMBOO_SHARED_MEM_SIZE)>>(BAMBOO_PAGE_SIZE_BITS);
  unsigned int page_gap=page_num/NUMCORESACTIVE;
  unsigned int page_index=page_gap*coren;
  unsigned int page_index_end=(coren==NUMCORESACTIVE-1)?page_num:(page_index+page_gap);
  VA page_sva = gcbaseva+(BAMBOO_PAGE_SIZE)*page_index;
  unsigned int * tmp_p = gccachepolicytbl;
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
  unsigned int * tmp_p = gccachepolicytbl;
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
  extern volatile bool gc_profile_flag;
  if(!gc_profile_flag) return;
  unsigned int page_index = 0;
  VA page_sva = 0;
  unsigned int page_num = (BAMBOO_SHARED_MEM_SIZE) >> (BAMBOO_PAGE_SIZE_BITS);
  for(page_index = 0; page_index < page_num; page_index++) {
    page_sva = gcbaseva + (BAMBOO_PAGE_SIZE) * page_index;
    unsigned int block = 0;
    BLOCKINDEX(block, (void *) page_sva);
    unsigned int coren = gc_block2core[block%(NUMCORES4GC*2)];
    //printf("%x,  %d,  %d,  ",(int)page_sva,page_index,coren);
    unsigned int * local_tbl = &gccachesamplingtbl[page_index*NUMCORESACTIVE];
    int accesscore = 0;
    for(int i = 0; i < NUMCORESACTIVE; i++) {
      int freq = *local_tbl;
      local_tbl++;
      if(freq != 0) {
        accesscore++;
        //printf("%d,  ", freq);
      }
    }
    if(accesscore!=0) {
      printf("%x,  %d,  %d,  ",(int)page_sva,page_index,coren);
      unsigned int * local_tbl = &gccachesamplingtbl[page_index*NUMCORESACTIVE];
      for(int i = 0; i < NUMCORESACTIVE; i++) {
        int freq = *local_tbl;
        local_tbl++;
        printf("%d,  ", freq);
      }
      printf("\n");
    }
    //printf("\n");
  }
  printf("=================\n");
} 

// output revised cache sampling data for each page after compaction
void gc_output_cache_sampling_r() {
  extern volatile bool gc_profile_flag;
  if(!gc_profile_flag) return;
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
  unsigned int page_num = (BAMBOO_SHARED_MEM_SIZE) >> (BAMBOO_PAGE_SIZE_BITS);
  for(page_index = 0; page_index < page_num; page_index++) {
    page_sva = gcbaseva + (BAMBOO_PAGE_SIZE) * page_index;
    unsigned int block = 0;
    BLOCKINDEX(block, (void *)page_sva);
    unsigned int coren = gc_block2core[block%(NUMCORES4GC*2)];
    //printf("%x,  %d,  %d,  ",(int)page_sva,page_index,coren);
    int accesscore = 0; // TODO
    unsigned int * local_tbl = &gccachesamplingtbl_r[page_index*NUMCORESACTIVE];
    for(int i = 0; i < NUMCORESACTIVE; i++) {
      int freq = *local_tbl; 
      //printf("%d,  ", freq);
      if(freq != 0) {
        accesscore++;// TODO
      }
      local_tbl++;
    }
    if(accesscore!=0) {
      printf("%x,  %d,  %d,  ",(int)page_sva,page_index,coren);
      unsigned int * local_tbl = &gccachesamplingtbl_r[page_index*NUMCORESACTIVE];
      for(int i = 0; i < NUMCORESACTIVE; i++) {
        int freq = *local_tbl;
        printf("%d,  ", freq);
        sumdata[accesscore-1][i]+=freq;
        local_tbl++;
      }
      printf("\n");
    }  
    //printf("\n");
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
