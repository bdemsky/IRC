#ifdef GC_CACHE_ADAPT
#include "multicorecache.h"
#include "multicoremsg.h"
#include "multicoregcprofile.h"

gc_cache_revise_info_t gc_cache_revise_information;

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
    int *local_tbl=&gccachesamplingtbl_r[page_index]; \
    for(int i = 0; i < NUMCORESACTIVE; i++) { \
      int freq = *local_tbl; \
      local_tbl=(int *)(((void *)local_tbl)+size_cachesamplingtbl_local_r); \
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
    int *local_tbl=&gccachesamplingtbl_r[page_index]; \
    for(int i = 0; i < NUMCORESACTIVE; i++) { \
      int freq = *local_tbl; \
      local_tbl=(int *)(((void *)local_tbl)+size_cachesamplingtbl_local_r); \
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
        //policy.cache_mode = BAMBOO_CACHE_MODE_HASH;
        unsigned int block = 0;
        BLOCKINDEX(block, (void *) page_sva);
        unsigned int coren = gc_block2core[block%(NUMCORES4GC*2)];
        CACHEADAPT_POLICY_SET_HOST_CORE(policy, coren);
      } else {
        // locally cache the page in the hottest core
        CACHEADAPT_POLICY_SET_HOST_CORE(policy, hottestcore);
      }     
    }
    CACHEADAPT_CHANGE_POLICY_4_PAGE(tmp_p,page_index,policy);
    page_sva += BAMBOO_PAGE_SIZE;
  }
}

#if 0
#define GC_CACHE_ADAPT_OVERLOAD_THRESHOLD 10
// record the worklocad of the hottestcore into core2heavypages
#define CACHEADAPT_RECORD_PAGE_WORKLOAD(hottestcore,totalfreq,hotfreq,remoteaccess,tmp_p) \
  { \
    workload[hottestcore] += (totalfreq); \
    total_workload += (totalfreq); \
    unsigned long long remoteaccess = (totalfreq) - (hotfreq); \
    unsigned int index = (unsigned int)core2heavypages[hottestcore][0]; \
    core2heavypages[hottestcore][3*index+3] = (remoteaccess); \
    core2heavypages[hottestcore][3*index+2] = (totalfreq); \
    core2heavypages[hottestcore][3*index+1] = (unsigned long long)((tmp_p)-1); \
    core2heavypages[hottestcore][0]++; \
  }

void gc_quicksort(unsigned long long *array,unsigned int left,unsigned int right,unsigned int offset) {
  unsigned int pivot = 0;;
  unsigned int leftIdx = left;
  unsigned int rightIdx = right;
  if((right-left+1) >= 1) {
    pivot = (left+right)/2;
    while((leftIdx <= pivot) && (rightIdx >= pivot)) {
      unsigned long long pivotValue = array[pivot*3-offset];
      while((array[leftIdx*3-offset] > pivotValue) && (leftIdx <= pivot)) {
        leftIdx++;
      }
      while((array[rightIdx*3-offset] < pivotValue) && (rightIdx >= pivot)) {
        rightIdx--;
      }
      // swap [leftIdx] & [rightIdx]
      for(int k = 0; k < 3; k++) {
        unsigned long long tmp = array[3*rightIdx-k];
        array[3*rightIdx-k] = array[3*leftIdx-k];
        array[3*leftIdx-k] = tmp;
      }
      leftIdx++;
      rightIdx--;
      if((leftIdx-1) == pivot) {
        pivot = rightIdx = rightIdx + 1;
      } else if((leftIdx+1) == pivot) {
        pivot = leftIdx = leftIdx-1;
      }
    }
    gc_quicksort(array, left, pivot-1, offset);
    gc_quicksort(array, pivot+1, right, offset);
  }
  return;
}

INLINE int cacheAdapt_h4h_remote_accesses(unsigned long long workload_threshold,unsigned long long ** core2heavypages, unsigned long long * workload,int i) {
  int j = 1;
  unsigned int index = (unsigned int)core2heavypages[i][0];
  if(workload[i] > workload_threshold) {
    // sort according to the remoteaccess
    gc_quicksort(&core2heavypages[i][0], 1, index, 0);
    while((workload[i] > workload_threshold) && (j<index*3)) {
      // hfh those pages with more remote accesses 
      bamboo_cache_policy_t policy = {0};
      policy.cache_mode = BAMBOO_CACHE_MODE_HASH;
      *((unsigned int*)core2heavypages[i][j]) = policy.word;
      workload[i] -= core2heavypages[i][j+1];
      j += 3;
    }
  }
  return j;
}

// Every page cached on the core that accesses it the most. 
// Check to see if any core's pages total more accesses than threshold 
// GC_CACHE_ADAPT_OVERLOAD_THRESHOLD.  If so, find the pages with the 
// most remote accesses and hash for home them until we get below 
// GC_CACHE_ADAPT_OVERLOAD_THRESHOLD
int cacheAdapt_policy_overload(int coren){
  unsigned int page_index = 0;
  VA page_sva = gcbaseva;
  unsigned int page_num = BAMBOO_SHARED_MEM_SIZE/BAMBOO_PAGE_SIZE;
  unsigned int numchanged = 0;
  int * tmp_p = gccachepolicytbl+1;
  unsigned long long workload[NUMCORESACTIVE];
  memset(workload, 0, NUMCORESACTIVE*sizeof(unsigned long long));
  unsigned long long total_workload = 0;
  unsigned long long core2heavypages[NUMCORESACTIVE][page_num*3+1];
  memset(core2heavypages,0,sizeof(unsigned long long)*(page_num*3+1)*NUMCORESACTIVE);
  for(page_index = 0; page_sva < gctopva; page_index++) {
    bamboo_cache_policy_t policy = {0};
    unsigned int hottestcore = 0;
    unsigned long long totalfreq = 0;
    unsigned int hotfreq = 0;
    CACHEADAPT_FIND_HOTTEST_CORE_W_TOTALFREQ(page_index,hottestcore,hotfreq,totalfreq);
    // Decide the cache strategy for this page
    // If decide to adapt a new cache strategy, write into the shared block of
    // the gcsharedsamplingtbl. The mem recording information that has been 
    // written is enough to hold the information.
    // Format: page start va + cache strategy(hfh/(host core+[x,y]))
    if(hotfreq != 0) {
      totalfreq/=BAMBOO_PAGE_SIZE;
      hotfreq/=BAMBOO_PAGE_SIZE;
      // locally cache the page in the hottest core
      CACHEADAPT_POLICY_SET_HOST_CORE(policy, hottestcore);
      CACHEADAPT_CHANGE_POLICY_4_PAGE(tmp_p,page_index,policy,numchanged);
      CACHEADAPT_RECORD_PAGE_WORKLOAD(hottestcore,totalfreq,hotfreq,remoteaccess,tmp_p);    
    }
    page_sva += BAMBOO_PAGE_SIZE;
  }

  unsigned long long workload_threshold=total_workload/GC_CACHE_ADAPT_OVERLOAD_THRESHOLD;
  // Check the workload of each core
  for(int i = 0; i < NUMCORESACTIVE; i++) {
    cacheAdapt_h4h_remote_accesses(workload_threshold,core2heavypages,workload,i);
  }

  return numchanged;
}

#define GC_CACHE_ADAPT_ACCESS_THRESHOLD 70
#define GC_CACHE_ADAPT_CROWD_THRESHOLD  20
// Every page cached on the core that accesses it the most. 
// Check to see if any core's pages total more accesses than threshold 
// GC_CACHE_ADAPT_OVERLOAD_THRESHOLD.  If so, find the pages with the 
// most remote accesses and hash for home them until we get below 
// GC_CACHE_ADAPT_OVERLOAD_THRESHOLD.  
// Sort pages based on activity.... 
// If more then GC_CACHE_ADAPT_ACCESS_THRESHOLD% of the accesses for a
// core's pages are from more than GC_CACHE_ADAPT_CROWD_THRESHOLD pages, 
// then start hfh these pages(selecting the ones with the most remote 
// accesses first or fewest local accesses) until we get below 
// GC_CACHE_ADAPT_CROWD_THRESHOLD pages.
int cacheAdapt_policy_crowd(int coren){
  unsigned int page_index = 0;
  VA page_sva = gcbaseva;
  unsigned int page_num = BAMBOO_SHARED_MEM_SIZE/BAMBOO_PAGE_SIZE;
  unsigned int numchanged = 0;
  int * tmp_p = gccachepolicytbl+1;
  unsigned long long workload[NUMCORESACTIVE];
  memset(workload, 0, NUMCORESACTIVE*sizeof(unsigned long long));
  unsigned long long total_workload = 0;
  unsigned long long core2heavypages[NUMCORESACTIVE][page_num*3+1];
  memset(core2heavypages,0,sizeof(unsigned long long)*(page_num*3+1)*NUMCORESACTIVE);
  for(page_index = 0; page_sva < gctopva; page_index++) {
    bamboo_cache_policy_t policy = {0};
    unsigned int hottestcore = 0;
    unsigned long long totalfreq = 0;
    unsigned int hotfreq = 0;
    CACHEADAPT_FIND_HOTTEST_CORE_W_TOTALFREQ(page_index,hottestcore,hotfreq,totalfreq);
    // Decide the cache strategy for this page
    // If decide to adapt a new cache strategy, write into the shared block of
    // the gcsharedsamplingtbl. The mem recording information that has been 
    // written is enough to hold the information.
    // Format: page start va + cache strategy(hfh/(host core+[x,y]))
    if(hotfreq != 0) {
      totalfreq/=BAMBOO_PAGE_SIZE;
      hotfreq/=BAMBOO_PAGE_SIZE;
      // locally cache the page in the hottest core
      CACHEADAPT_POLICY_SET_HOST_CORE(policy, hottestcore);
      CACHEADAPT_CHANGE_POLICY_4_PAGE(tmp_p,page_index,policy,numchanged);
      CACHEADAPT_RECORD_PAGE_WORKLOAD(hottestcore,totalfreq,hotfreq,remoteaccess,tmp_p);
    }
    page_sva += BAMBOO_PAGE_SIZE;
  }

  unsigned long long workload_threshold=total_workload/GC_CACHE_ADAPT_OVERLOAD_THRESHOLD;
  // Check the workload of each core
  for(int i = 0; i < NUMCORESACTIVE; i++) {
    unsigned int index=(unsigned int)core2heavypages[i][0];
    int j=cacheAdapt_h4h_remote_accesses(workload_threshold,core2heavypages,workload,i);
    // Check if the accesses are crowded on few pages
    // sort according to the total access
inner_crowd:
    gc_quicksort(&core2heavypages[i][0], j/3+1, index, 1);
    unsigned long long threshold=GC_CACHE_ADAPT_ACCESS_THRESHOLD*workload[i]/100;
    int num_crowded = 0;
    unsigned long long t_workload = 0;
    do {
      t_workload += core2heavypages[i][j+num_crowded*3+1];
      num_crowded++;
    } while(t_workload < threshold);
    // num_crowded <= GC_CACHE_ADAPT_CROWD_THRESHOLD and if there are enough 
    // items, it is always == GC_CACHE_ADAPT_CROWD_THRESHOLD
    if(num_crowded > GC_CACHE_ADAPT_CROWD_THRESHOLD) {
      // need to hfh these pages
      // sort the pages according to remote access
      gc_quicksort(&core2heavypages[i][0], j/3+1, j/3+num_crowded, 0);
      // h4h those pages with more remote accesses 
      bamboo_cache_policy_t policy = {0};
      policy.cache_mode = BAMBOO_CACHE_MODE_HASH;
      *((unsigned int*)core2heavypages[i][j]) = policy.word;
      workload[i] -= core2heavypages[i][j+1];
      t_workload -= core2heavypages[i][j+1];
      j += 3;
      threshold = GC_CACHE_ADAPT_ACCESS_THRESHOLD*workload[i]/100;
      goto inner_crowd;
    }
  }

  return numchanged;
} 
#endif

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
//#elif defined GC_CACHE_ADAPT_POLICY5
//  cacheAdapt_policy_overload(coren);
//#elif defined GC_CACHE_ADAPT_POLICY6
//  cacheAdapt_policy_crowd(coren);
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
  CACHEADAPT_SAMPING_RESET();
  if(BAMBOO_NUM_OF_CORE < NUMCORESACTIVE) {
    // zero out the gccachesamplingtbl
    BAMBOO_MEMSET_WH(gccachesamplingtbl_local,0,size_cachesamplingtbl_local);  
    BAMBOO_MEMSET_WH(gccachesamplingtbl_local_r,0,size_cachesamplingtbl_local_r);
  }
}

extern unsigned long long gc_output_cache_policy_time;

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

  CACHEADAPT_SAMPING_RESET();
  if(BAMBOO_NUM_OF_CORE < NUMCORESACTIVE) {
    // zero out the gccachesamplingtbl
    BAMBOO_MEMSET_WH(gccachesamplingtbl_local,0,size_cachesamplingtbl_local);
    BAMBOO_MEMSET_WH(gccachesamplingtbl_local_r,0,size_cachesamplingtbl_local_r);
    BAMBOO_MEMSET_WH(gccachepolicytbl,0,size_cachepolicytbl);
  }
}

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
    for(int i = 0; i < NUMCORESACTIVE; i++) {
      int * local_tbl = (int *)((void *)gccachesamplingtbl+size_cachesamplingtbl_local*i);
      int freq = local_tbl[page_index];
      //if(freq != 0) {
        printf("%d,  ", freq);
      //}
    }
    printf("\n");
  }
  printf("=================\n");
} 

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
    for(int i = 0; i < NUMCORESACTIVE; i++) {
      int * local_tbl = (int *)((void *)gccachesamplingtbl_r+size_cachesamplingtbl_local_r*i);
      int freq = local_tbl[page_index]/BAMBOO_PAGE_SIZE;
      printf("%d,  ", freq);
      if(freq != 0) {
        accesscore++;// TODO
      }
    }
    if(accesscore!=0) {
      for(int i = 0; i < NUMCORESACTIVE; i++) {
        int * local_tbl = (int *)((void *)gccachesamplingtbl_r+size_cachesamplingtbl_local_r*i);
        int freq = local_tbl[page_index]/BAMBOO_PAGE_SIZE;
        sumdata[accesscore-1][i]+=freq;
      }
    }
  
    printf("\n");
  }
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
