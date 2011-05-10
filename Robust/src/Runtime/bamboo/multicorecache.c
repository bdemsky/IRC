#ifdef GC_CACHE_ADAPT
#include "multicorecache.h"

typedef struct gc_cache_revise_info {
  unsigned int orig_page_start_va;
  unsigned int orig_page_end_va;
  unsigned int orig_page_index;
  unsigned int to_page_start_va;
  unsigned int to_page_end_va;
  unsigned int to_page_index;
  unsigned int revised_sampling[NUMCORESACTIVE];
} gc_cache_revise_info_t;
gc_cache_revise_info_t gc_cache_revise_infomation;

INLINE void samplingDataInit() {
  gc_cache_revise_infomation.to_page_start_va = (unsigned int)to->ptr;
  unsigned int toindex = (unsigned int)(tobase-gcbaseva)/(BAMBOO_PAGE_SIZE);
  gc_cache_revise_infomation.to_page_end_va = gcbaseva + 
    (BAMBOO_PAGE_SIZE)*(toindex+1);
  gc_cache_revise_infomation.to_page_index = toindex;
  gc_cache_revise_infomation.orig_page_start_va = (unsigned int)orig->ptr;
  gc_cache_revise_infomation.orig_page_end_va = gcbaseva+(BAMBOO_PAGE_SIZE)
  *(((unsigned int)(orig->ptr)-gcbaseva)/(BAMBOO_PAGE_SIZE)+1);
  gc_cache_revise_infomation.orig_page_index = 
    ((unsigned int)(orig->blockbase)-gcbaseva)/(BAMBOO_PAGE_SIZE);
}

INLINE void samplingDataConvert(unsigned int current_ptr) {
  unsigned int tmp_factor = 
  current_ptr-gc_cache_revise_infomation.to_page_start_va;
  unsigned int topage=gc_cache_revise_infomation.to_page_index;
  unsigned int oldpage = gc_cache_revise_infomation.orig_page_index;
  int * newtable=&gccachesamplingtbl_r[topage];
  int * oldtable=&gccachesamplingtbl[oldpage];
  
  for(int tt = 0; tt < NUMCORESACTIVE; tt++) {
    (*newtable) = ((*newtable)+(*oldtable)*tmp_factor);
    newtable=(int*)(((char *)newtable)+size_cachesamplingtbl_local_r);
    oldtable=(int*) (((char *)oldtable)+size_cachesamplingtbl_local);
  }
} 

INLINE void completePageConvert(struct moveHelper * orig,
                                struct moveHelper * to,
                                unsigned int current_ptr,
                                bool closeToPage) {
  unsigned int ptr = 0;
  unsigned int tocompare = 0;
  if(closeToPage) {
    ptr = to->ptr;
    tocompare = gc_cache_revise_infomation.to_page_end_va;
  } else {
    ptr = orig->ptr;
    tocompare = gc_cache_revise_infomation.orig_page_end_va;
  }
  if((unsigned int)ptr >= (unsigned int)tocompare) {
    // end of an orig/to page
    // compute the impact of this page for the new page
    samplingDataConvert(current_ptr);
    // prepare for an new orig page
    unsigned int tmp_index = 
      (unsigned int)((unsigned int)orig->ptr-gcbaseva)/(BAMBOO_PAGE_SIZE);
    gc_cache_revise_infomation.orig_page_start_va = orig->ptr;
    gc_cache_revise_infomation.orig_page_end_va = gcbaseva + 
      (BAMBOO_PAGE_SIZE)*(unsigned int)(tmp_index+1);
    gc_cache_revise_infomation.orig_page_index = tmp_index;
    gc_cache_revise_infomation.to_page_start_va = to->ptr;
    if(closeToPage) {
      gc_cache_revise_infomation.to_page_end_va = gcbaseva+(BAMBOO_PAGE_SIZE)
        *(((unsigned int)(to->ptr)-gcbaseva)/(BAMBOO_PAGE_SIZE)+1);
      gc_cache_revise_infomation.to_page_index = 
        ((unsigned int)(to->ptr)-gcbaseva)/(BAMBOO_PAGE_SIZE);
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

// make all pages hfh
int cacheAdapt_policy_h4h(){
  unsigned int page_index = 0;
  VA page_sva = 0;
  unsigned int page_num = (BAMBOO_SHARED_MEM_SIZE) / (BAMBOO_PAGE_SIZE);
  unsigned int numchanged = 0;
  int * tmp_p = gccachepolicytbl+1;
  for(page_index = 0; page_index < page_num; page_index++) {
    page_sva = gcbaseva + (BAMBOO_PAGE_SIZE) * page_index;
    bamboo_cache_policy_t policy = {0};
    policy.cache_mode = BAMBOO_CACHE_MODE_HASH;
    *tmp_p = page_index;
    tmp_p++;
    *tmp_p = policy.word;
    tmp_p++;
    numchanged++;
  }

  return numchanged;
} 

// make all pages local as non-cache-adaptable gc local mode
int cacheAdapt_policy_local(){
  unsigned int page_index = 0;
  VA page_sva = 0;
  unsigned int page_num = (BAMBOO_SHARED_MEM_SIZE) / (BAMBOO_PAGE_SIZE);
  unsigned int numchanged = 0;
  int * tmp_p = gccachepolicytbl+1;
  for(page_index = 0; page_index < page_num; page_index++) {
    page_sva = gcbaseva + (BAMBOO_PAGE_SIZE) * page_index;
    bamboo_cache_policy_t policy = {0};
    unsigned int block = 0;
    BLOCKINDEX(page_sva, &block);
    unsigned int coren = gc_block2core[block%(NUMCORES4GC*2)];
    // locally cache the page in the hotest core
    // NOTE: (x,y) should be changed to (x+1, y+1)!!!
    policy.cache_mode = BAMBOO_CACHE_MODE_COORDS;
    policy.lotar_x = bamboo_cpu2coords[2*coren]+1;
    policy.lotar_y = bamboo_cpu2coords[2*coren+1]+1;
    *tmp_p = page_index;
    tmp_p++;
    *tmp_p = policy.word;
    tmp_p++;
    numchanged++;
  }

  return numchanged;
} 

int cacheAdapt_policy_hotest(){
  unsigned int page_index = 0;
  VA page_sva = 0;
  unsigned int page_num = (BAMBOO_SHARED_MEM_SIZE) / (BAMBOO_PAGE_SIZE);
  unsigned int numchanged = 0;
  int * tmp_p = gccachepolicytbl+1;
  for(page_index = 0; page_index < page_num; page_index++) {
    page_sva = gcbaseva + (BAMBOO_PAGE_SIZE) * page_index;
    bamboo_cache_policy_t policy = {0};
    unsigned int hotestcore = 0;
    unsigned int hotfreq = 0;

    int *local_tbl=&gccachesamplingtbl_r[page_index];
    for(int i = 0; i < NUMCORESACTIVE; i++) {
      int freq = *local_tbl;
      local_tbl=(int *)(((char *)local_tbl)+size_cachesamplingtbl_local_r);

      // check the freqency, decide if this page is hot for the core
      if(hotfreq < freq) {
        hotfreq = freq;
        hotestcore = i;
      }
    }
    // TODO
    // Decide the cache strategy for this page
    // If decide to adapt a new cache strategy, write into the shared block of
    // the gcsharedsamplingtbl. The mem recording information that has been 
    // written is enough to hold the information.
    // Format: page start va + cache strategy(hfh/(host core+[x,y]))
    if(hotfreq == 0) {
      // this page has not been accessed, do not change its cache policy
      continue;
    } else {
      // locally cache the page in the hotest core
      // NOTE: (x,y) should be changed to (x+1, y+1)!!!
      policy.cache_mode = BAMBOO_CACHE_MODE_COORDS;
      policy.lotar_x = bamboo_cpu2coords[2*hotestcore]+1;
      policy.lotar_y = bamboo_cpu2coords[2*hotestcore+1]+1;
      *tmp_p = page_index;
      tmp_p++;
      *tmp_p = policy.word;
      tmp_p++;
      numchanged++;
    }
  }

  return numchanged;
} 

#define GC_CACHE_ADAPT_DOMINATE_THRESHOLD  50
// cache the page on the core that accesses it the most if that core accesses 
// it more than (GC_CACHE_ADAPT_DOMINATE_THRESHOLD)% of the total.  Otherwise,
// h4h the page.
int cacheAdapt_policy_dominate(){
  unsigned int page_index = 0;
  VA page_sva = 0;
  unsigned int page_num = (BAMBOO_SHARED_MEM_SIZE) / (BAMBOO_PAGE_SIZE);
  unsigned int numchanged = 0;
  int * tmp_p = gccachepolicytbl+1;
  for(page_index = 0; page_index < page_num; page_index++) {
    page_sva = gcbaseva + (BAMBOO_PAGE_SIZE) * page_index;
    bamboo_cache_policy_t policy = {0};
    unsigned int hotestcore = 0;
    unsigned long long totalfreq = 0;
    unsigned int hotfreq = 0;
  
    int *local_tbl=&gccachesamplingtbl_r[page_index];
    for(int i = 0; i < NUMCORESACTIVE; i++) {
      int freq = *local_tbl;
      local_tbl=(int *)(((char *)local_tbl)+size_cachesamplingtbl_local_r);
      totalfreq += freq;
      // check the freqency, decide if this page is hot for the core
      if(hotfreq < freq) {
        hotfreq = freq;
        hotestcore = i;
      }
    }

    // Decide the cache strategy for this page
    // If decide to adapt a new cache strategy, write into the shared block of
    // the gcpolicytbl 
    // Format: page start va + cache policy
    if(hotfreq == 0) {
      // this page has not been accessed, do not change its cache policy
      continue;
    }
    totalfreq = 
      (totalfreq*GC_CACHE_ADAPT_DOMINATE_THRESHOLD)/100/BAMBOO_PAGE_SIZE;
    hotfreq/=BAMBOO_PAGE_SIZE;
    if(hotfreq < totalfreq) {
      // use hfh
      policy.cache_mode = BAMBOO_CACHE_MODE_HASH;
    } else {
      // locally cache the page in the hotest core
      // NOTE: (x,y) should be changed to (x+1, y+1)!!!
      policy.cache_mode = BAMBOO_CACHE_MODE_COORDS;
      policy.lotar_x = bamboo_cpu2coords[2*hotestcore]+1;
      policy.lotar_y = bamboo_cpu2coords[2*hotestcore+1]+1;
    }
    *tmp_p = page_index;
    tmp_p++;
    *tmp_p = policy.word;    
    tmp_p++;
    numchanged++;
  }

  return numchanged;
}

#define GC_CACHE_ADAPT_OVERLOAD_THRESHOLD 10

void gc_quicksort(unsigned long long *array,
                  unsigned int left,
                  unsigned int right,
                  unsigned int offset) {
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

// Every page cached on the core that accesses it the most. 
// Check to see if any core's pages total more accesses than threshold 
// GC_CACHE_ADAPT_OVERLOAD_THRESHOLD.  If so, find the pages with the 
// most remote accesses and hash for home them until we get below 
// GC_CACHE_ADAPT_OVERLOAD_THRESHOLD
int cacheAdapt_policy_overload(){
  unsigned int page_index = 0;
  VA page_sva = 0;
  unsigned int page_num = (BAMBOO_SHARED_MEM_SIZE) / (BAMBOO_PAGE_SIZE);
  unsigned int numchanged = 0;
  int * tmp_p = gccachepolicytbl+1;
  unsigned long long workload[NUMCORESACTIVE];
  memset(workload, 0, NUMCORESACTIVE*sizeof(unsigned long long));
  unsigned long long total_workload = 0;
  unsigned long long core2heavypages[NUMCORESACTIVE][page_num*3+1];
  memset(core2heavypages,0,
      sizeof(unsigned long long)*(page_num*3+1)*NUMCORESACTIVE);
  for(page_index = 0; page_index < page_num; page_index++) {
    page_sva = gcbaseva + (BAMBOO_PAGE_SIZE) * page_index;
    bamboo_cache_policy_t policy = {0};
    unsigned int hotestcore = 0;
    unsigned long long totalfreq = 0;
    unsigned int hotfreq = 0;
  
    int *local_tbl=&gccachesamplingtbl_r[page_index];
    for(int i = 0; i < NUMCORESACTIVE; i++) {
      int freq = *local_tbl;
      local_tbl=(int *)(((char *)local_tbl)+size_cachesamplingtbl_local_r);
      totalfreq += freq;
      // check the freqency, decide if this page is hot for the core
      if(hotfreq < freq) {
        hotfreq = freq;
        hotestcore = i;
      }
    }
    // Decide the cache strategy for this page
    // If decide to adapt a new cache strategy, write into the shared block of
    // the gcsharedsamplingtbl. The mem recording information that has been 
    // written is enough to hold the information.
    // Format: page start va + cache strategy(hfh/(host core+[x,y]))
    if(hotfreq == 0) {
      // this page has not been accessed, do not change its cache policy
      continue;
    }

    totalfreq/=BAMBOO_PAGE_SIZE;
    hotfreq/=BAMBOO_PAGE_SIZE;
    // locally cache the page in the hotest core
    // NOTE: (x,y) should be changed to (x+1, y+1)!!!
    policy.cache_mode = BAMBOO_CACHE_MODE_COORDS;
    policy.lotar_x = bamboo_cpu2coords[2*hotestcore]+1;
    policy.lotar_y = bamboo_cpu2coords[2*hotestcore+1]+1;
    *tmp_p = page_index;
    tmp_p++;
    *tmp_p = policy.word;
    tmp_p++;
    numchanged++;
    workload[hotestcore] += totalfreq;
    total_workload += totalfreq;
    // insert into core2heavypages using quicksort
    unsigned long long remoteaccess = totalfreq - hotfreq;
    unsigned int index = (unsigned int)core2heavypages[hotestcore][0];
    core2heavypages[hotestcore][3*index+3] = remoteaccess;
    core2heavypages[hotestcore][3*index+2] = totalfreq;
    core2heavypages[hotestcore][3*index+1] = (unsigned long long)(tmp_p-1);
    core2heavypages[hotestcore][0]++;
  }

  unsigned long long workload_threshold = 
  total_workload/GC_CACHE_ADAPT_OVERLOAD_THRESHOLD;
  // Check the workload of each core
  for(int i = 0; i < NUMCORESACTIVE; i++) {
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
int cacheAdapt_policy_crowd(){
  unsigned int page_index = 0;
  VA page_sva = 0;
  unsigned int page_num = (BAMBOO_SHARED_MEM_SIZE) / (BAMBOO_PAGE_SIZE);
  unsigned int numchanged = 0;
  int * tmp_p = gccachepolicytbl+1;
  unsigned long long workload[NUMCORESACTIVE];
  memset(workload, 0, NUMCORESACTIVE*sizeof(unsigned long long));
  unsigned long long total_workload = 0;
  unsigned long long core2heavypages[NUMCORESACTIVE][page_num*3+1];
  memset(core2heavypages,0,
    sizeof(unsigned long long)*(page_num*3+1)*NUMCORESACTIVE);
  for(page_index = 0; page_index < page_num; page_index++) {
    page_sva = gcbaseva + (BAMBOO_PAGE_SIZE) * page_index;
    bamboo_cache_policy_t policy = {0};
    unsigned int hotestcore = 0;
    unsigned long long totalfreq = 0;
    unsigned int hotfreq = 0;
  
    int *local_tbl=&gccachesamplingtbl_r[page_index];
    for(int i = 0; i < NUMCORESACTIVE; i++) {
      int freq = *local_tbl;
      local_tbl=(int *)(((char *)local_tbl)+size_cachesamplingtbl_local_r);
      totalfreq += freq;
      // check the freqency, decide if this page is hot for the core
      if(hotfreq < freq) {
        hotfreq = freq;
        hotestcore = i;
      }
    }
    // Decide the cache strategy for this page
    // If decide to adapt a new cache strategy, write into the shared block of
    // the gcsharedsamplingtbl. The mem recording information that has been 
    // written is enough to hold the information.
    // Format: page start va + cache strategy(hfh/(host core+[x,y]))
    if(hotfreq == 0) {
      // this page has not been accessed, do not change its cache policy
      continue;
    }
    totalfreq/=BAMBOO_PAGE_SIZE;
    hotfreq/=BAMBOO_PAGE_SIZE;
    // locally cache the page in the hotest core
    // NOTE: (x,y) should be changed to (x+1, y+1)!!!
    policy.cache_mode = BAMBOO_CACHE_MODE_COORDS;
    policy.lotar_x = bamboo_cpu2coords[2*hotestcore]+1;
    policy.lotar_y = bamboo_cpu2coords[2*hotestcore+1]+1;
    *tmp_p = page_index;
    tmp_p++;
    *tmp_p = policy.word;
    tmp_p++;
    numchanged++;
    workload[hotestcore] += totalfreq;
    total_workload += totalfreq;
    // insert into core2heavypages using quicksort
    unsigned long long remoteaccess = totalfreq - hotfreq;
    unsigned int index = (unsigned int)core2heavypages[hotestcore][0];
    core2heavypages[hotestcore][3*index+3] = remoteaccess;
    core2heavypages[hotestcore][3*index+2] = totalfreq;
    core2heavypages[hotestcore][3*index+1] = (unsigned long long)(tmp_p-1);
    core2heavypages[hotestcore][0]++;
  }

  unsigned long long workload_threshold = 
  total_workload / GC_CACHE_ADAPT_OVERLOAD_THRESHOLD;
  // Check the workload of each core
  for(int i = 0; i < NUMCORESACTIVE; i++) {
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

    // Check if the accesses are crowded on few pages
    // sort according to the total access
inner_crowd:
    gc_quicksort(&core2heavypages[i][0], j/3+1, index, 1);
    unsigned long long threshold = 
      GC_CACHE_ADAPT_ACCESS_THRESHOLD*workload[i]/100;
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

void cacheAdapt_master() {
  CACHEADAPT_OUTPUT_CACHE_SAMPLING_R();
  unsigned int numchanged = 0;
  // check the statistic data
  // for each page, decide the new cache strategy
#ifdef GC_CACHE_ADAPT_POLICY1
  numchanged = cacheAdapt_policy_h4h();
#elif defined GC_CACHE_ADAPT_POLICY2
  numchanged = cacheAdapt_policy_local();
#elif defined GC_CACHE_ADAPT_POLICY3
  numchanged = cacheAdapt_policy_hotest();
#elif defined GC_CACHE_ADAPT_POLICY4
  numchanged = cacheAdapt_policy_dominate();
#elif defined GC_CACHE_ADAPT_POLICY5
  numchanged = cacheAdapt_policy_overload();
#elif defined GC_CACHE_ADAPT_POLICY6
  numchanged = cacheAdapt_policy_crowd();
#endif
  *gccachepolicytbl = numchanged;
}

// adapt the cache strategy for the mutator
void cacheAdapt_mutator() {
  int numchanged = *gccachepolicytbl;
  // check the changes and adapt them
  int * tmp_p = gccachepolicytbl+1;
  while(numchanged--) {
    // read out the policy
    int page_index = *tmp_p;
    bamboo_cache_policy_t policy = (bamboo_cache_policy_t)(*(tmp_p+1));
    // adapt the policy
    bamboo_adapt_cache_policy(page_index*(BAMBOO_PAGE_SIZE)+gcbaseva, 
        policy, BAMBOO_PAGE_SIZE);

    tmp_p += 2;
  }
}

void cacheAdapt_phase_client() {
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
    BAMBOO_MEMSET_WH(gccachesamplingtbl_local_r,0,
        size_cachesamplingtbl_local_r);
  }
}

void cacheAdapt_phase_master() {
  GCPROFILEITEM();
  gcphase = PREFINISHPHASE;
  // Note: all cores should flush their runtime data including non-gc cores
  GC_SEND_MSG_1_TO_CLIENT(GCSTARTPREF);
  GC_PRINTF("Start prefinish phase \n");
  // cache adapt phase
  cacheAdapt_mutator();
  CACHEADPAT_OUTPUT_CACHE_POLICY();
  cacheAdapt_gc(false);

  GC_CHECK_ALL_CORE_STATUS(PREFINISHPHASE == gcphase);

  CACHEADAPT_SAMPING_RESET();
  if(BAMBOO_NUM_OF_CORE < NUMCORESACTIVE) {
    // zero out the gccachesamplingtbl
    BAMBOO_MEMSET_WH(gccachesamplingtbl_local,0,size_cachesamplingtbl_local);
    BAMBOO_MEMSET_WH(gccachesamplingtbl_local_r,0,size_cachesamplingtbl_local_r);
    BAMBOO_MEMSET_WH(gccachepolicytbl,0,size_cachepolicytbl);
  }
}

void gc_output_cache_sampling() {
  unsigned int page_index = 0;
  VA page_sva = 0;
  unsigned int page_num = (BAMBOO_SHARED_MEM_SIZE) / (BAMBOO_PAGE_SIZE);
  for(page_index = 0; page_index < page_num; page_index++) {
    page_sva = gcbaseva + (BAMBOO_PAGE_SIZE) * page_index;
    unsigned int block = 0;
    BLOCKINDEX(page_sva, &block);
    unsigned int coren = gc_block2core[block%(NUMCORES4GC*2)];
    tprintf("va: %x page_index: %d host: %d\n",(int)page_sva,page_index,coren);
    for(int i = 0; i < NUMCORESACTIVE; i++) {
      int * local_tbl = (int *)((void *)gccachesamplingtbl
          +size_cachesamplingtbl_local*i);
      int freq = local_tbl[page_index];
      printf("%8d ",freq);
    }
    printf("\n");
  }
  printf("=================\n");
} 

void gc_output_cache_sampling_r() {
  unsigned int page_index = 0;
  VA page_sva = 0;
  unsigned int page_num = (BAMBOO_SHARED_MEM_SIZE) / (BAMBOO_PAGE_SIZE);
  for(page_index = 0; page_index < page_num; page_index++) {
    page_sva = gcbaseva + (BAMBOO_PAGE_SIZE) * page_index;
    unsigned int block = 0;
    BLOCKINDEX(page_sva, &block);
    unsigned int coren = gc_block2core[block%(NUMCORES4GC*2)];
    tprintf("va: %x page_index: %d host: %d\n",(int)page_sva,page_index,coren);
    for(int i = 0; i < NUMCORESACTIVE; i++) {
      int * local_tbl = (int *)((void *)gccachesamplingtbl_r
          +size_cachesamplingtbl_local_r*i);
      int freq = local_tbl[page_index]/BAMBOO_PAGE_SIZE;
      printf("%8d ",freq);
    }
  
    printf("\n");
  }
  printf("=================\n");
} 
#endif // GC_CACHE_ADAPT
