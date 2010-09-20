#ifdef TASK
#include "runtime.h"
#include "multicoreruntime.h"
#include "runtime_arch.h"
#include "GenericHashtable.h"

#ifndef INLINE
#define INLINE    inline __attribute__((always_inline))
#endif // #ifndef INLINE

//  data structures for task invocation
struct genhashtable * activetasks;
struct taskparamdescriptor * currtpd;
struct LockValue runtime_locks[MAXTASKPARAMS];
int runtime_locklen;

// specific functions used inside critical sections
void enqueueObject_I(void * ptr,
                     struct parameterwrapper ** queues,
                     int length);
int enqueuetasks_I(struct parameterwrapper *parameter,
                   struct parameterwrapper *prevptr,
                   struct ___Object___ *ptr,
                   int * enterflags,
                   int numenterflags);

#ifdef MULTICORE_GC
#ifdef SMEMF
#define NUM_CORES2TEST 5
#ifdef GC_1
int core2test[1][NUM_CORES2TEST] = {
  {0, -1, -1, -1, -1}
};
#elif defined GC_56
int core2test[56][NUM_CORES2TEST] = {
  { 0, -1,  7, -1,  1}, { 1, -1,  8,  0,  2}, { 2, -1,  9,  1,  3},
  { 3, -1, 10,  2,  4}, { 4, -1, 11,  3,  5}, { 5, -1, 12,  4,  6},
  { 6, -1, 13,  5, -1}, { 7,  0, 14, -1,  8}, { 8,  1, 15,  7,  9},
  { 9,  2, 16,  8, 10}, {10,  3, 17,  9, 11}, {11,  4, 18, 10, 12},
  {12,  5, 19, 11, 13}, {13,  6, 20, 12, -1}, {14,  7, 21, -1, 15},
  {15,  8, 22, 14, 16}, {16,  9, 23, 15, 17}, {17, 10, 24, 16, 18},
  {18, 11, 25, 17, 19}, {19, 12, 26, 18, 20}, {20, 13, 27, 19, -1},
  {21, 14, 28, -1, 22}, {22, 15, 29, 21, 23}, {23, 16, 30, 22, 24},
  {24, 17, 31, 23, 25}, {25, 18, 32, 24, 26}, {26, 19, 33, 25, 27},
  {27, 20, 34, 26, -1}, {28, 21, 35, -1, 29}, {29, 22, 36, 28, 30},
  {30, 23, 37, 29, 31}, {31, 24, 38, 30, 32}, {32, 25, 39, 31, 33},
  {33, 26, 40, 32, 34}, {34, 27, 41, 33, -1}, {35, 28, 42, -1, 36},
  {36, 29, 43, 35, 37}, {37, 30, 44, 36, 38}, {38, 31, 45, 37, 39},
  {39, 32, 46, 38, 40}, {40, 33, 47, 39, 41}, {41, 34, 48, 40, -1},
  {42, 35, 49, -1, 43}, {43, 36, 50, 42, 44}, {44, 37, 51, 43, 45},
  {45, 38, 52, 44, 46}, {46, 39, 53, 45, 47}, {47, 40, 54, 46, 48},
  {48, 41, 55, 47, -1}, {49, 42, -1, -1, 50}, {50, 43, -1, 49, 51},
  {51, 44, -1, 50, 52}, {52, 45, -1, 51, 53}, {53, 46, -1, 52, 54},
  {54, 47, -1, 53, 55}, {55, 48, -1, 54, -1}
};
#elif defined GC_62
int core2test[62][NUM_CORES2TEST] = {
  { 0, -1,  6, -1,  1}, { 1, -1,  7,  0,  2}, { 2, -1,  8,  1,  3},
  { 3, -1,  9,  2,  4}, { 4, -1, 10,  3,  5}, { 5, -1, 11,  4, -1},
  { 6,  0, 14, -1,  7}, { 7,  1, 15,  6,  8}, { 8,  2, 16,  7,  9},
  { 9,  3, 17,  8, 10}, {10,  4, 18,  9, 11}, {11,  5, 19, 10, 12},
  {12, -1, 20, 11, 13}, {13, -1, 21, 12, -1}, {14,  6, 22, -1, 15},
  {15,  7, 23, 14, 16}, {16,  8, 24, 15, 17}, {17,  9, 25, 16, 18},
  {18, 10, 26, 17, 19}, {19, 11, 27, 18, 20}, {20, 12, 28, 19, 21},
  {21, 13, 29, 28, -1}, {22, 14, 30, -1, 23}, {23, 15, 31, 22, 24},
  {24, 16, 32, 23, 25}, {25, 17, 33, 24, 26}, {26, 18, 34, 25, 27},
  {27, 19, 35, 26, 28}, {28, 20, 36, 27, 29}, {29, 21, 37, 28, -1},
  {30, 22, 38, -1, 31}, {31, 23, 39, 30, 32}, {32, 24, 40, 31, 33},
  {33, 25, 41, 32, 34}, {34, 26, 42, 33, 35}, {35, 27, 43, 34, 36},
  {36, 28, 44, 35, 37}, {37, 29, 45, 36, -1}, {38, 30, 46, -1, 39},
  {39, 31, 47, 38, 40}, {40, 32, 48, 39, 41}, {41, 33, 49, 40, 42},
  {42, 34, 50, 41, 43}, {43, 35, 51, 42, 44}, {44, 36, 52, 43, 45},
  {45, 37, 53, 44, -1}, {46, 38, 54, -1, 47}, {47, 39, 55, 46, 48},
  {48, 40, 56, 47, 49}, {49, 41, 57, 48, 50}, {50, 42, 58, 49, 51},
  {51, 43, 59, 50, 52}, {52, 44, 60, 51, 53}, {53, 45, 61, 52, -1},
  {54, 46, -1, -1, 55}, {55, 47, -1, 54, 56}, {56, 48, -1, 55, 57},
  {57, 49, -1, 56, 59}, {58, 50, -1, 57, 59}, {59, 51, -1, 58, 60},
  {60, 52, -1, 59, 61}, {61, 53, -1, 60, -1}
};
#endif // GC_1
#elif defined SMEMM
unsigned int gcmem_mixed_threshold = 0;
unsigned int gcmem_mixed_usedmem = 0;
#define NUM_CORES2TEST 9
#ifdef GC_1
int core2test[1][NUM_CORES2TEST] = {
  {0, -1, -1, -1, -1, -1, -1, -1, -1}
};
#elif defined GC_56
int core2test[56][NUM_CORES2TEST] = {
  { 0, -1,  7, -1,  1, -1, 14, -1,  2}, 
  { 1, -1,  8,  0,  2, -1, 15, -1,  3}, 
  { 2, -1,  9,  1,  3, -1, 16,  0,  4}, 
  { 3, -1, 10,  2,  4, -1, 17,  1,  5}, 
  { 4, -1, 11,  3,  5, -1, 18,  2,  6}, 
  { 5, -1, 12,  4,  6, -1, 19,  3, -1},
  { 6, -1, 13,  5, -1, -1, 20,  4, -1}, 
  { 7,  0, 14, -1,  8, -1, 21, -1,  9}, 
  { 8,  1, 15,  7,  9, -1, 22, -1, 10}, 
  { 9,  2, 16,  8, 10, -1, 23,  7, 11}, 
  {10,  3, 17,  9, 11, -1, 24,  8, 12}, 
  {11,  4, 18, 10, 12, -1, 25,  9, 13},
  {12,  5, 19, 11, 13, -1, 26, 10, -1}, 
  {13,  6, 20, 12, -1, -1, 27, 11, -1}, 
  {14,  7, 21, -1, 15,  0, 28, -1, 16}, 
  {15,  8, 22, 14, 16,  1, 29, -1, 17}, 
  {16,  9, 23, 15, 17,  2, 30, 14, 18}, 
  {17, 10, 24, 16, 18,  3, 31, 15, 19},
  {18, 11, 25, 17, 19,  4, 32, 16, 20}, 
  {19, 12, 26, 18, 20,  5, 33, 17, -1}, 
  {20, 13, 27, 19, -1,  6, 34, 18, -1}, 
  {21, 14, 28, -1, 22,  7, 35, -1, 23}, 
  {22, 15, 29, 21, 23,  8, 36, -1, 24}, 
  {23, 16, 30, 22, 24,  9, 37, 21, 25},
  {24, 17, 31, 23, 25, 10, 38, 22, 26}, 
  {25, 18, 32, 24, 26, 11, 39, 23, 27}, 
  {26, 19, 33, 25, 27, 12, 40, 24, -1}, 
  {27, 20, 34, 26, -1, 13, 41, 25, -1}, 
  {28, 21, 35, -1, 29, 14, 42, -1, 30}, 
  {29, 22, 36, 28, 30, 15, 43, -1, 31},
  {30, 23, 37, 29, 31, 16, 44, 28, 32}, 
  {31, 24, 38, 30, 32, 17, 45, 29, 33}, 
  {32, 25, 39, 31, 33, 18, 46, 30, 34}, 
  {33, 26, 40, 32, 34, 19, 47, 31, -1}, 
  {34, 27, 41, 33, -1, 20, 48, 32, -1}, 
  {35, 28, 42, -1, 36, 21, 49, -1, 37},
  {36, 29, 43, 35, 37, 22, 50, -1, 38}, 
  {37, 30, 44, 36, 38, 23, 51, 35, 39}, 
  {38, 31, 45, 37, 39, 24, 52, 36, 40}, 
  {39, 32, 46, 38, 40, 25, 53, 37, 41}, 
  {40, 33, 47, 39, 41, 26, 54, 38, -1}, 
  {41, 34, 48, 40, -1, 27, 55, 39, -1},
  {42, 35, 49, -1, 43, 28, -1, -1, 44}, 
  {43, 36, 50, 42, 44, 29, -1, -1, 45}, 
  {44, 37, 51, 43, 45, 30, -1, 42, 46}, 
  {45, 38, 52, 44, 46, 31, -1, 43, 47}, 
  {46, 39, 53, 45, 47, 32, -1, 44, 48}, 
  {47, 40, 54, 46, 48, 33, -1, 45, -1},
  {48, 41, 55, 47, -1, 34, -1, 46, -1}, 
  {49, 42, -1, -1, 50, 35, -1, -1, 51}, 
  {50, 43, -1, 49, 51, 36, -1, -1, 52}, 
  {51, 44, -1, 50, 52, 37, -1, 49, 53}, 
  {52, 45, -1, 51, 53, 38, -1, 50, 54}, 
  {53, 46, -1, 52, 54, 39, -1, 51, 55},
  {54, 47, -1, 53, 55, 40, -1, 52, -1}, 
  {55, 48, -1, 54, -1, 41, -1, 53, -1}
};
#elif defined GC_62
int core2test[62][NUM_CORES2TEST] = {
  { 0, -1,  6, -1,  1, -1, 14, -1,  2}, 
  { 1, -1,  7,  0,  2, -1, 15, -1,  3}, 
  { 2, -1,  8,  1,  3, -1, 16,  0,  4}, 
  { 3, -1,  9,  2,  4, -1, 17,  1,  5}, 
  { 4, -1, 10,  3,  5, -1, 18,  2, -1}, 
  { 5, -1, 11,  4, -1, -1, 19,  3, -1},
  { 6,  0, 14, -1,  7, -1, 22, -1,  8}, 
  { 7,  1, 15,  6,  8, -1, 23, -1,  9}, 
  { 8,  2, 16,  7,  9, -1, 24,  6, 10}, 
  { 9,  3, 17,  8, 10, -1, 25,  7, 11}, 
  {10,  4, 18,  9, 11, -1, 26,  8, 12}, 
  {11,  5, 19, 10, 12, -1, 27,  9, 13},
  {12, -1, 20, 11, 13, -1, 28, 10, -1}, 
  {13, -1, 21, 12, -1, -1, 29, 11, -1}, 
  {14,  6, 22, -1, 15,  0, 30, -1, 16}, 
  {15,  7, 23, 14, 16,  1, 31, -1, 17}, 
  {16,  8, 24, 15, 17,  2, 32, 14, 18}, 
  {17,  9, 25, 16, 18,  3, 33, 15, 19},
  {18, 10, 26, 17, 19,  4, 34, 16, 20}, 
  {19, 11, 27, 18, 20,  5, 35, 17, 21}, 
  {20, 12, 28, 19, 21, -1, 36, 18, -1}, 
  {21, 13, 29, 28, -1, -1, 37, 19, -1}, 
  {22, 14, 30, -1, 23,  6, 38, -1, 24}, 
  {23, 15, 31, 22, 24,  7, 39, -1, 25},
  {24, 16, 32, 23, 25,  8, 40, 22, 26}, 
  {25, 17, 33, 24, 26,  9, 41, 23, 27}, 
  {26, 18, 34, 25, 27, 10, 42, 24, 28}, 
  {27, 19, 35, 26, 28, 11, 43, 25, 29}, 
  {28, 20, 36, 27, 29, 12, 44, 26, -1}, 
  {29, 21, 37, 28, -1, 13, 45, 27, -1},
  {30, 22, 38, -1, 31, 22, 46, -1, 32}, 
  {31, 23, 39, 30, 32, 15, 47, -1, 33}, 
  {32, 24, 40, 31, 33, 16, 48, 30, 34}, 
  {33, 25, 41, 32, 34, 17, 49, 31, 35}, 
  {34, 26, 42, 33, 35, 18, 50, 32, 36}, 
  {35, 27, 43, 34, 36, 19, 51, 33, 37},
  {36, 28, 44, 35, 37, 20, 52, 34, -1}, 
  {37, 29, 45, 36, -1, 21, 53, 35, -1}, 
  {38, 30, 46, -1, 39, 22, 54, -1, 40}, 
  {39, 31, 47, 38, 40, 23, 55, -1, 41}, 
  {40, 32, 48, 39, 41, 24, 56, 38, 42}, 
  {41, 33, 49, 40, 42, 25, 57, 39, 43},
  {42, 34, 50, 41, 43, 26, 58, 40, 44}, 
  {43, 35, 51, 42, 44, 27, 59, 41, 45}, 
  {44, 36, 52, 43, 45, 28, 60, 42, -1}, 
  {45, 37, 53, 44, -1, 29, 61, 43, -1}, 
  {46, 38, 54, -1, 47, 30, -1, -1, 48}, 
  {47, 39, 55, 46, 48, 31, -1, -1, 49},
  {48, 40, 56, 47, 49, 32, -1, 46, 50}, 
  {49, 41, 57, 48, 50, 33, -1, 47, 51}, 
  {50, 42, 58, 49, 51, 34, -1, 48, 52}, 
  {51, 43, 59, 50, 52, 35, -1, 49, 53}, 
  {52, 44, 60, 51, 53, 36, -1, 50, -1}, 
  {53, 45, 61, 52, -1, 37, -1, 51, -1},
  {54, 46, -1, -1, 55, 38, -1, -1, 56}, 
  {55, 47, -1, 54, 56, 39, -1, -1, 57}, 
  {56, 48, -1, 55, 57, 40, -1, 54, 58}, 
  {57, 49, -1, 56, 59, 41, -1, 55, 59}, 
  {58, 50, -1, 57, 59, 42, -1, 56, 60}, 
  {59, 51, -1, 58, 60, 43, -1, 57, 61},
  {60, 52, -1, 59, 61, 44, -1, 58, -1}, 
  {61, 53, -1, 60, -1, 45, -1, 59, -1}
};
#endif // GC_1
#endif

inline __attribute__((always_inline))
void setupsmemmode(void) {
#ifdef SMEML
  // Only allocate local mem chunks to each core.
  // If a core has used up its local shared memory, start gc.
  bamboo_smem_mode = SMEMLOCAL;
#elif defined SMEMF
  // Allocate the local shared memory to each core with the highest priority,
  // if a core has used up its local shared memory, try to allocate the 
  // shared memory that belong to its neighbours, if also failed, start gc.
  bamboo_smem_mode = SMEMFIXED;
#elif defined SMEMM
  // Allocate the local shared memory to each core with the highest priority,
  // if a core has used up its local shared memory, try to allocate the 
  // shared memory that belong to its neighbours first, if failed, check 
  // current memory allocation rate, if it has already reached the threshold,
  // start gc, otherwise, allocate the shared memory globally.  If all the 
  // shared memory has been used up, start gc.
  bamboo_smem_mode = SMEMMIXED;
#elif defined SMEMG
  // Allocate all the memory chunks globally, do not consider the host cores
  // When all the shared memory are used up, start gc.
  bamboo_smem_mode = SMEMGLOBAL;
#else
  // defaultly using local mode
  bamboo_smem_mode = SMEMLOCAL;
  //bamboo_smem_mode = SMEMGLOBAL;
  //bamboo_smem_mode = SMEMFIXED;
#endif
} // void setupsmemmode(void)
#endif

inline __attribute__((always_inline))
void initruntimedata() {
  int i;
  // initialize the arrays
  if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
    // startup core to initialize corestatus[]
    for(i = 0; i < NUMCORESACTIVE; ++i) {
      corestatus[i] = 1;
      numsendobjs[i] = 0;
      numreceiveobjs[i] = 0;
#ifdef PROFILE
      // initialize the profile data arrays
      profilestatus[i] = 1;
#endif
#ifdef MULTICORE_GC
      gccorestatus[i] = 1;
      gcnumsendobjs[0][i] = gcnumsendobjs[1][i] = 0;
      gcnumreceiveobjs[0][i] = gcnumreceiveobjs[1][i] = 0;
#endif
    } // for(i = 0; i < NUMCORESACTIVE; ++i)
#ifdef MULTICORE_GC
    for(i = 0; i < NUMCORES4GC; ++i) {
      gcloads[i] = 0;
      gcrequiredmems[i] = 0;
      gcstopblock[i] = 0;
      gcfilledblocks[i] = 0;
    } // for(i = 0; i < NUMCORES4GC; ++i)
#ifdef GC_PROFILE
    gc_infoIndex = 0;
    gc_infoOverflow = false;
	gc_num_livespace = 0;
	gc_num_freespace = 0;
#endif
#endif
    numconfirm = 0;
    waitconfirm = false;

    // TODO for test
    total_num_t6 = 0;
  }

  busystatus = true;
  self_numsendobjs = 0;
  self_numreceiveobjs = 0;

  for(i = 0; i < BAMBOO_MSG_BUF_LENGTH; ++i) {
    msgdata[i] = -1;
  }
  msgdataindex = 0;
  msgdatalast = 0;
  msglength = BAMBOO_MSG_BUF_LENGTH;
  msgdatafull = false;
  for(i = 0; i < BAMBOO_OUT_BUF_LENGTH; ++i) {
    outmsgdata[i] = -1;
  }
  outmsgindex = 0;
  outmsglast = 0;
  outmsgleft = 0;
  isMsgHanging = false;
  //isMsgSending = false;

  smemflag = true;
  bamboo_cur_msp = NULL;
  bamboo_smem_size = 0;
  totransobjqueue = createQueue_I();

#ifdef MULTICORE_GC
  bamboo_smem_zero_top = NULL;
  gcflag = false;
  gcprocessing = false;
  gcphase = FINISHPHASE;
  //gcnumpre = 0;
  gcprecheck = true;
  gccurr_heaptop = 0;
  gcself_numsendobjs = 0;
  gcself_numreceiveobjs = 0;
  gcmarkedptrbound = 0;
#ifdef LOCALHASHTBL_TEST
  gcpointertbl = allocateRuntimeHash_I(20);
#else
  gcpointertbl = mgchashCreate_I(2000, 0.75);
#endif
  //gcpointertbl = allocateMGCHash_I(20);
  gcforwardobjtbl = allocateMGCHash_I(20, 3);
  gcobj2map = 0;
  gcmappedobj = 0;
  //gcismapped = false;
  gcnumlobjs = 0;
  gcheaptop = 0;
  gctopcore = 0;
  gctopblock = 0;
  gcmovestartaddr = 0;
  gctomove = false;
  gcmovepending = 0;
  gcblock2fill = 0;
  gcsbstarttbl = BAMBOO_BASE_VA;
  bamboo_smemtbl = (void *)gcsbstarttbl
               + (BAMBOO_SHARED_MEM_SIZE/BAMBOO_SMEM_SIZE)*sizeof(INTPTR);
  if(BAMBOO_NUM_OF_CORE < NUMCORES4GC) {
	int t_size = ((BAMBOO_RMSP_SIZE)-sizeof(mgcsharedhashtbl_t)*2
		-128*sizeof(size_t))/sizeof(mgcsharedhashlistnode_t)-2;
	int kk = 0;
	unsigned int tmp_k = 1 << (sizeof(int)*8 -1);
	while(((t_size & tmp_k) == 0) && (kk < sizeof(int)*8)) {
	  t_size = t_size << 1;
	  kk++;
	}
	t_size = tmp_k >> kk;
	gcsharedptbl = mgcsharedhashCreate_I(t_size,0.30);//allocateGCSharedHash_I(20);
  } else {
	gcsharedptbl = NULL;
  }
  BAMBOO_MEMSET_WH(gcrpointertbls, 0, 
	  sizeof(mgcsharedhashtbl_t *)*NUMCORES4GC);
	  //sizeof(struct RuntimeHash *)*NUMCORES4GC);
#ifdef SMEMM
  gcmem_mixed_threshold = (unsigned int)((BAMBOO_SHARED_MEM_SIZE
		-bamboo_reserved_smem*BAMBOO_SMEM_SIZE)*0.8);
  gcmem_mixed_usedmem = 0;
#endif
#ifdef GC_PROFILE
  gc_num_obj = 0;
  gc_num_liveobj = 0;
  gc_num_forwardobj = 0;
  gc_num_profiles = NUMCORESACTIVE - 1;
#endif
#ifdef GC_FLUSH_DTLB
  gc_num_flush_dtlb = 0;
#endif
  gc_localheap_s = false;
#ifdef GC_CACHE_ADAPT
  gccachestage = false;
  // enable the timer interrupt
#ifdef GC_CACHE_SAMPLING
  bamboo_tile_timer_set_next_event(GC_TILE_TIMER_EVENT_SETTING); // TODO
  bamboo_unmask_timer_intr();
  bamboo_dtlb_sampling_process();
#endif // GC_CACHE_SAMPLING
#endif // GC_CACHE_ADAPT
#else
  // create the lock table, lockresult table and obj queue
  locktable.size = 20;
  locktable.bucket =
    (struct RuntimeNode **) RUNMALLOC_I(sizeof(struct RuntimeNode *)*20);
  /* Set allocation blocks*/
  locktable.listhead=NULL;
  locktable.listtail=NULL;
  /*Set data counts*/
  locktable.numelements = 0;
  lockobj = 0;
  lock2require = 0;
  lockresult = 0;
  lockflag = false;
  lockRedirectTbl = allocateRuntimeHash_I(20);
  objRedirectLockTbl = allocateRuntimeHash_I(20);
#endif
#ifndef INTERRUPT
  reside = false;
#endif
  objqueue.head = NULL;
  objqueue.tail = NULL;

  currtpd = NULL;

#ifdef PROFILE
  stall = false;
  //isInterrupt = true;
  totalexetime = -1;
  //interrupttime = 0;
  taskInfoIndex = 0;
  taskInfoOverflow = false;
#ifdef PROFILE_INTERRUPT
  interruptInfoIndex = 0;
  interruptInfoOverflow = false;
#endif // PROFILE_INTERRUPT
#endif // PROFILE

  for(i = 0; i < MAXTASKPARAMS; i++) {
    runtime_locks[i].redirectlock = 0;
    runtime_locks[i].value = 0;
  }
  runtime_locklen = 0;
}

inline __attribute__((always_inline))
void disruntimedata() {
#ifdef MULTICORE_GC
#ifdef LOCALHASHTBL_TEST
  freeRuntimeHash(gcpointertbl);
#else
  mgchashDelete(gcpointertbl);
#endif
  //freeMGCHash(gcpointertbl);
  freeMGCHash(gcforwardobjtbl);
  // for mapping info structures
  //freeRuntimeHash(gcrcoretbl);
#else
  freeRuntimeHash(lockRedirectTbl);
  freeRuntimeHash(objRedirectLockTbl);
  RUNFREE(locktable.bucket);
#endif
  if(activetasks != NULL) {
    genfreehashtable(activetasks);
  }
  if(currtpd != NULL) {
    RUNFREE(currtpd->parameterArray);
    RUNFREE(currtpd);
    currtpd = NULL;
  }
  BAMBOO_LOCAL_MEM_CLOSE();
  BAMBOO_SHARE_MEM_CLOSE();
}

inline __attribute__((always_inline))
bool checkObjQueue() {
  bool rflag = false;
  struct transObjInfo * objInfo = NULL;
  int grount = 0;

#ifdef PROFILE
#ifdef ACCURATEPROFILE
  bool isChecking = false;
  if(!isEmpty(&objqueue)) {
    profileTaskStart("objqueue checking");
    isChecking = true;
  }       // if(!isEmpty(&objqueue))
#endif
#endif

  while(!isEmpty(&objqueue)) {
    void * obj = NULL;
    BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
#ifdef DEBUG
    BAMBOO_DEBUGPRINT(0xf001);
#endif
#ifdef PROFILE
    //isInterrupt = false;
#endif
#ifdef DEBUG
    BAMBOO_DEBUGPRINT(0xeee1);
#endif
    rflag = true;
    objInfo = (struct transObjInfo *)getItem(&objqueue);
    obj = objInfo->objptr;
#ifdef DEBUG
    BAMBOO_DEBUGPRINT_REG((int)obj);
#endif
    // grab lock and flush the obj
    grount = 0;
    getwritelock_I(obj);
    while(!lockflag) {
      BAMBOO_WAITING_FOR_LOCK(0);
    }   // while(!lockflag)
    grount = lockresult;
#ifdef DEBUG
    BAMBOO_DEBUGPRINT_REG(grount);
#endif

    lockresult = 0;
    lockobj = 0;
    lock2require = 0;
    lockflag = false;
#ifndef INTERRUPT
    reside = false;
#endif

    if(grount == 1) {
      int k = 0;
      // flush the object
#ifdef CACHEFLUSH
      BAMBOO_CACHE_FLUSH_RANGE((int)obj,sizeof(int));
      BAMBOO_CACHE_FLUSH_RANGE((int)obj,
		  classsize[((struct ___Object___ *)obj)->type]);
#endif
      // enqueue the object
      for(k = 0; k < objInfo->length; ++k) {
		int taskindex = objInfo->queues[2 * k];
		int paramindex = objInfo->queues[2 * k + 1];
		struct parameterwrapper ** queues =
		  &(paramqueues[BAMBOO_NUM_OF_CORE][taskindex][paramindex]);
#ifdef DEBUG
		BAMBOO_DEBUGPRINT_REG(taskindex);
		BAMBOO_DEBUGPRINT_REG(paramindex);
		struct ___Object___ * tmpptr = (struct ___Object___ *)obj;
		tprintf("Process %x(%d): receive obj %x(%lld), ptrflag %x\n",
				BAMBOO_NUM_OF_CORE, BAMBOO_NUM_OF_CORE, (int)obj,
				(long)obj, tmpptr->flag);
#endif
		enqueueObject_I(obj, queues, 1);
#ifdef DEBUG
		BAMBOO_DEBUGPRINT_REG(hashsize(activetasks));
#endif
      }  // for(k = 0; k < objInfo->length; ++k)
      releasewritelock_I(obj);
      RUNFREE(objInfo->queues);
      RUNFREE(objInfo);
    } else {
      // can not get lock
      // put it at the end of the queue if no update version in the queue
      struct QueueItem * qitem = getHead(&objqueue);
      struct QueueItem * prev = NULL;
      while(qitem != NULL) {
		struct transObjInfo * tmpinfo =
			(struct transObjInfo *)(qitem->objectptr);
		if(tmpinfo->objptr == obj) {
		  // the same object in the queue, which should be enqueued
		  // recently. Current one is outdate, do not re-enqueue it
		  RUNFREE(objInfo->queues);
		  RUNFREE(objInfo);
		  goto objqueuebreak;
		} else {
		  prev = qitem;
		}  // if(tmpinfo->objptr == obj)
		qitem = getNextQueueItem(prev);
	  }  // while(qitem != NULL)
      // try to execute active tasks already enqueued first
      addNewItem_I(&objqueue, objInfo);
#ifdef PROFILE
      //isInterrupt = true;
#endif
objqueuebreak:
      BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
#ifdef DEBUG
      BAMBOO_DEBUGPRINT(0xf000);
#endif
      break;
    }  // if(grount == 1)
    BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
#ifdef DEBUG
    BAMBOO_DEBUGPRINT(0xf000);
#endif
  }  // while(!isEmpty(&objqueue))

#ifdef PROFILE
#ifdef ACCURATEPROFILE
  if(isChecking) {
    profileTaskEnd();
  }  // if(isChecking)
#endif
#endif

#ifdef DEBUG
  BAMBOO_DEBUGPRINT(0xee02);
#endif
  return rflag;
}

inline __attribute__((always_inline))
void checkCoreStatus() {
  bool allStall = false;
  int i = 0;
  int sumsendobj = 0;
  if((!waitconfirm) ||
     (waitconfirm && (numconfirm == 0))) {
#ifdef DEBUG
    BAMBOO_DEBUGPRINT(0xee04);
    BAMBOO_DEBUGPRINT_REG(waitconfirm);
#endif
    BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
#ifdef DEBUG
    BAMBOO_DEBUGPRINT(0xf001);
#endif
    corestatus[BAMBOO_NUM_OF_CORE] = 0;
    numsendobjs[BAMBOO_NUM_OF_CORE] = self_numsendobjs;
    numreceiveobjs[BAMBOO_NUM_OF_CORE] = self_numreceiveobjs;
    // check the status of all cores
    allStall = true;
#ifdef DEBUG
    BAMBOO_DEBUGPRINT_REG(NUMCORESACTIVE);
#endif
    for(i = 0; i < NUMCORESACTIVE; ++i) {
#ifdef DEBUG
      BAMBOO_DEBUGPRINT(0xe000 + corestatus[i]);
#endif
      if(corestatus[i] != 0) {
		allStall = false;
		break;
      }
    }  // for(i = 0; i < NUMCORESACTIVE; ++i)
    if(allStall) {
      // check if the sum of send objs and receive obj are the same
      // yes->check if the info is the latest; no->go on executing
      sumsendobj = 0;
      for(i = 0; i < NUMCORESACTIVE; ++i) {
		sumsendobj += numsendobjs[i];
#ifdef DEBUG
		BAMBOO_DEBUGPRINT(0xf000 + numsendobjs[i]);
#endif
      }  // for(i = 0; i < NUMCORESACTIVE; ++i)
      for(i = 0; i < NUMCORESACTIVE; ++i) {
		sumsendobj -= numreceiveobjs[i];
#ifdef DEBUG
		BAMBOO_DEBUGPRINT(0xf000 + numreceiveobjs[i]);
#endif
      }  // for(i = 0; i < NUMCORESACTIVE; ++i)
      if(0 == sumsendobj) {
		if(!waitconfirm) {
		  // the first time found all cores stall
		  // send out status confirm msg to all other cores
		  // reset the corestatus array too
#ifdef DEBUG
		  BAMBOO_DEBUGPRINT(0xee05);
#endif
		  corestatus[BAMBOO_NUM_OF_CORE] = 1;
		  waitconfirm = true;
		  numconfirm = NUMCORESACTIVE - 1;
		  BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
		  for(i = 1; i < NUMCORESACTIVE; ++i) {
			corestatus[i] = 1;
			// send status confirm msg to core i
			send_msg_1(i, STATUSCONFIRM, false);
		  }   // for(i = 1; i < NUMCORESACTIVE; ++i)
		  return;
		} else {
		  // all the core status info are the latest
		  // terminate; for profiling mode, send request to all
		  // other cores to pour out profiling data
#ifdef DEBUG
		  BAMBOO_DEBUGPRINT(0xee06);
#endif

#ifdef USEIO
		  totalexetime = BAMBOO_GET_EXE_TIME() - bamboo_start_time;
#else
#ifdef PROFILE
		  //BAMBOO_DEBUGPRINT_REG(interrupttime);
#endif

		  BAMBOO_DEBUGPRINT(BAMBOO_GET_EXE_TIME() - bamboo_start_time);
		  //BAMBOO_DEBUGPRINT_REG(total_num_t6); // TODO for test
#ifdef GC_FLUSH_DTLB
		  BAMBOO_DEBUGPRINT_REG(gc_num_flush_dtlb);
#endif
#ifndef BAMBOO_MEMPROF
		  BAMBOO_DEBUGPRINT(0xbbbbbbbb);
#endif
#endif
		  // profile mode, send msgs to other cores to request pouring
		  // out progiling data
#ifdef PROFILE
		  BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
#ifdef DEBUG
		  BAMBOO_DEBUGPRINT(0xf000);
#endif
		  for(i = 1; i < NUMCORESACTIVE; ++i) {
			// send profile request msg to core i
			send_msg_2(i, PROFILEOUTPUT, totalexetime, false);
		  } // for(i = 1; i < NUMCORESACTIVE; ++i)
#ifndef RT_TEST
		  // pour profiling data on startup core
		  outputProfileData();
#endif
		  while(true) {
			BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
#ifdef DEBUG
			BAMBOO_DEBUGPRINT(0xf001);
#endif
			profilestatus[BAMBOO_NUM_OF_CORE] = 0;
			// check the status of all cores
			allStall = true;
#ifdef DEBUG
			BAMBOO_DEBUGPRINT_REG(NUMCORESACTIVE);
#endif
			for(i = 0; i < NUMCORESACTIVE; ++i) {
#ifdef DEBUG
			  BAMBOO_DEBUGPRINT(0xe000 + profilestatus[i]);
#endif
			  if(profilestatus[i] != 0) {
				allStall = false;
				break;
			  }
			}  // for(i = 0; i < NUMCORESACTIVE; ++i)
			if(!allStall) {
			  int halt = 100;
			  BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
#ifdef DEBUG
			  BAMBOO_DEBUGPRINT(0xf000);
#endif
			  while(halt--) {
			  }
			} else {
			  BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
			  break;
			}  // if(!allStall)
		  }  // while(true)
#endif

		  // gc_profile mode, output gc prfiling data
#ifdef MULTICORE_GC
#ifdef GC_CACHE_ADAPT
		  bamboo_mask_timer_intr(); // disable the TILE_TIMER interrupt
#endif // GC_CACHE_ADAPT
#ifdef GC_PROFILE
		  gc_outputProfileData();
#endif // #ifdef GC_PROFILE
#endif // #ifdef MULTICORE_GC
		  disruntimedata();
		  BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
		  terminate();  // All done.
		}  // if(!waitconfirm)
      } else {
		// still some objects on the fly on the network
		// reset the waitconfirm and numconfirm
#ifdef DEBUG
		BAMBOO_DEBUGPRINT(0xee07);
#endif
		waitconfirm = false;
		numconfirm = 0;
	  }  //  if(0 == sumsendobj)
    } else {
      // not all cores are stall, keep on waiting
#ifdef DEBUG
      BAMBOO_DEBUGPRINT(0xee08);
#endif
      waitconfirm = false;
      numconfirm = 0;
    }  //  if(allStall)
    BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
#ifdef DEBUG
    BAMBOO_DEBUGPRINT(0xf000);
#endif
  }  // if((!waitconfirm) ||
}

// main function for each core
inline void run(void * arg) {
  int i = 0;
  int argc = 1;
  char ** argv = NULL;
  bool sendStall = false;
  bool isfirst = true;
  bool tocontinue = false;

  corenum = BAMBOO_GET_NUM_OF_CORE();
#ifdef DEBUG
  BAMBOO_DEBUGPRINT(0xeeee);
  BAMBOO_DEBUGPRINT_REG(corenum);
  BAMBOO_DEBUGPRINT(STARTUPCORE);
#endif
  //BAMBOO_DEBUGPRINT(BAMBOO_GET_EXE_TIME()); // TODO

  // initialize runtime data structures
  initruntimedata();

  // other architecture related initialization
  initialization();
  initCommunication();

  initializeexithandler();

  // main process of the execution module
  if(BAMBOO_NUM_OF_CORE > NUMCORESACTIVE - 1) {
    // non-executing cores, only processing communications
    activetasks = NULL;
#ifdef PROFILE
    //isInterrupt = false;
#endif
    fakeExecution();
  } else {
    /* Create queue of active tasks */
    activetasks=
      genallocatehashtable((unsigned int (*)(void *)) &hashCodetpd,
                           (int (*)(void *,void *)) &comparetpd);

    /* Process task information */
    processtasks();

    if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
      /* Create startup object */
      createstartupobject(argc, argv);
    }

#ifdef DEBUG
    BAMBOO_DEBUGPRINT(0xee00);
#endif

    while(true) {

#ifdef MULTICORE_GC
      // check if need to do GC
      if(gcflag) {
		gc(NULL);
	  }
#endif // MULTICORE_GC

      // check if there are new active tasks can be executed
      executetasks();
      if(busystatus) {
		sendStall = false;
      }

#ifndef INTERRUPT
      while(receiveObject() != -1) {
      }
#endif

#ifdef DEBUG
      BAMBOO_DEBUGPRINT(0xee01);
#endif

      // check if there are some pending objects,
      // if yes, enqueue them and executetasks again
      tocontinue = checkObjQueue();

      if(!tocontinue) {
		// check if stop
		if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
		  if(isfirst) {
#ifdef DEBUG
			BAMBOO_DEBUGPRINT(0xee03);
#endif
			isfirst = false;
		  }
		  checkCoreStatus();
		} else {
		  if(!sendStall) {
#ifdef DEBUG
			BAMBOO_DEBUGPRINT(0xee09);
#endif
#ifdef PROFILE
			if(!stall) {
#endif
			if(isfirst) {
			  // wait for some time
			  int halt = 10000;
#ifdef DEBUG
			  BAMBOO_DEBUGPRINT(0xee0a);
#endif
			  while(halt--) {
			  }
			  isfirst = false;
			} else {
			  // send StallMsg to startup core
#ifdef DEBUG
			  BAMBOO_DEBUGPRINT(0xee0b);
#endif
			  // send stall msg
			  send_msg_4(STARTUPCORE, TRANSTALL, BAMBOO_NUM_OF_CORE,
						 self_numsendobjs, self_numreceiveobjs, false);
			  sendStall = true;
			  isfirst = true;
			  busystatus = false;
			}
#ifdef PROFILE
		  }
#endif
		  } else {
			isfirst = true;
			busystatus = false;
#ifdef DEBUG
			BAMBOO_DEBUGPRINT(0xee0c);
#endif
		  }   // if(!sendStall)
		}   // if(STARTUPCORE == BAMBOO_NUM_OF_CORE)
      }  // if(!tocontinue)
    }  // while(true)
  } // if(BAMBOO_NUM_OF_CORE > NUMCORESACTIVE - 1)

} // run()

struct ___createstartupobject____I_locals {
  INTPTR size;
  void * next;
  struct  ___StartupObject___ * ___startupobject___;
  struct ArrayObject * ___stringarray___;
}; // struct ___createstartupobject____I_locals

void createstartupobject(int argc,
                         char ** argv) {
  int i;

  /* Allocate startup object     */
#ifdef MULTICORE_GC
  struct ___createstartupobject____I_locals ___locals___ = 
  {2, NULL, NULL, NULL};
  struct ___StartupObject___ *startupobject=
    (struct ___StartupObject___*) allocate_new(&___locals___, STARTUPTYPE);
  ___locals___.___startupobject___ = startupobject;
  struct ArrayObject * stringarray=
    allocate_newarray(&___locals___, STRINGARRAYTYPE, argc-1);
  ___locals___.___stringarray___ = stringarray;
#else
  struct ___StartupObject___ *startupobject=
    (struct ___StartupObject___*) allocate_new(STARTUPTYPE);
  struct ArrayObject * stringarray=
    allocate_newarray(STRINGARRAYTYPE, argc-1);
#endif
  /* Build array of strings */
  startupobject->___parameters___=stringarray;
  for(i=1; i<argc; i++) {
    int length=strlen(argv[i]);
#ifdef MULTICORE_GC
    struct ___String___ *newstring=NewString(&___locals___, argv[i],length);
#else
    struct ___String___ *newstring=NewString(argv[i],length);
#endif
    ((void **)(((char *)&stringarray->___length___)+sizeof(int)))[i-1]=
      newstring;
  }

  startupobject->version = 0;
  startupobject->lock = NULL;

  /* Set initialized flag for startup object */
  flagorandinit(startupobject,1,0xFFFFFFFF);
  enqueueObject(startupobject, NULL, 0);
#ifdef CACHEFLUSH
  BAMBOO_CACHE_FLUSH_ALL();
#endif
}

int hashCodetpd(struct taskparamdescriptor *ftd) {
  int hash=(int)ftd->task;
  int i;
  for(i=0; i<ftd->numParameters; i++) {
    hash^=(int)ftd->parameterArray[i];
  }
  return hash;
}

int comparetpd(struct taskparamdescriptor *ftd1,
               struct taskparamdescriptor *ftd2) {
  int i;
  if (ftd1->task!=ftd2->task)
    return 0;
  for(i=0; i<ftd1->numParameters; i++)
    if(ftd1->parameterArray[i]!=ftd2->parameterArray[i])
      return 0;
  return 1;
}

/* This function sets a tag. */
#ifdef MULTICORE_GC
void tagset(void *ptr,
            struct ___Object___ * obj,
            struct ___TagDescriptor___ * tagd) {
#else
void tagset(struct ___Object___ * obj,
            struct ___TagDescriptor___ * tagd) {
#endif
  struct ArrayObject * ao=NULL;
  struct ___Object___ * tagptr=obj->___tags___;
  if (tagptr==NULL) {
    obj->___tags___=(struct ___Object___ *)tagd;
  } else {
    /* Have to check if it is already set */
    if (tagptr->type==TAGTYPE) {
      struct ___TagDescriptor___ * td=(struct ___TagDescriptor___ *) tagptr;
      if (td==tagd) {
		return;
      }
#ifdef MULTICORE_GC
      int ptrarray[]={2, (int) ptr, (int) obj, (int)tagd};
      struct ArrayObject * ao=
        allocate_newarray(&ptrarray,TAGARRAYTYPE,TAGARRAYINTERVAL);
      obj=(struct ___Object___ *)ptrarray[2];
      tagd=(struct ___TagDescriptor___ *)ptrarray[3];
      td=(struct ___TagDescriptor___ *) obj->___tags___;
#else
      ao=allocate_newarray(TAGARRAYTYPE,TAGARRAYINTERVAL);
#endif

      ARRAYSET(ao, struct ___TagDescriptor___ *, 0, td);
      ARRAYSET(ao, struct ___TagDescriptor___ *, 1, tagd);
      obj->___tags___=(struct ___Object___ *) ao;
      ao->___cachedCode___=2;
    } else {
      /* Array Case */
      int i;
      struct ArrayObject *ao=(struct ArrayObject *) tagptr;
      for(i=0; i<ao->___cachedCode___; i++) {
		struct ___TagDescriptor___ * td=
		  ARRAYGET(ao, struct ___TagDescriptor___*, i);
		if (td==tagd) {
		  return;
		}
      }
      if (ao->___cachedCode___<ao->___length___) {
		ARRAYSET(ao, struct ___TagDescriptor___ *,ao->___cachedCode___,tagd);
		ao->___cachedCode___++;
      } else {
#ifdef MULTICORE_GC
		int ptrarray[]={2,(int) ptr, (int) obj, (int) tagd};
		struct ArrayObject * aonew=
		  allocate_newarray(&ptrarray,TAGARRAYTYPE,
							TAGARRAYINTERVAL+ao->___length___);
		obj=(struct ___Object___ *)ptrarray[2];
		tagd=(struct ___TagDescriptor___ *) ptrarray[3];
		ao=(struct ArrayObject *)obj->___tags___;
#else
		struct ArrayObject * aonew=
		  allocate_newarray(TAGARRAYTYPE,TAGARRAYINTERVAL+ao->___length___);
#endif

		aonew->___cachedCode___=ao->___length___+1;
		for(i=0; i<ao->___length___; i++) {
		  ARRAYSET(aonew, struct ___TagDescriptor___*, i,
				   ARRAYGET(ao, struct ___TagDescriptor___*, i));
		}
		ARRAYSET(aonew, struct ___TagDescriptor___ *, ao->___length___,tagd);
      }
    }
  }

  {
    struct ___Object___ * tagset=tagd->flagptr;
    if(tagset==NULL) {
      tagd->flagptr=obj;
    } else if (tagset->type!=OBJECTARRAYTYPE) {
#ifdef MULTICORE_GC
      int ptrarray[]={2, (int) ptr, (int) obj, (int)tagd};
      struct ArrayObject * ao=
        allocate_newarray(&ptrarray,OBJECTARRAYTYPE,OBJECTARRAYINTERVAL);
      obj=(struct ___Object___ *)ptrarray[2];
      tagd=(struct ___TagDescriptor___ *)ptrarray[3];
#else
      struct ArrayObject * ao=
        allocate_newarray(OBJECTARRAYTYPE,OBJECTARRAYINTERVAL);
#endif
      ARRAYSET(ao, struct ___Object___ *, 0, tagd->flagptr);
      ARRAYSET(ao, struct ___Object___ *, 1, obj);
      ao->___cachedCode___=2;
      tagd->flagptr=(struct ___Object___ *)ao;
    } else {
      struct ArrayObject *ao=(struct ArrayObject *) tagset;
      if (ao->___cachedCode___<ao->___length___) {
		ARRAYSET(ao, struct ___Object___*, ao->___cachedCode___++, obj);
      } else {
		int i;
#ifdef MULTICORE_GC
		int ptrarray[]={2, (int) ptr, (int) obj, (int)tagd};
		struct ArrayObject * aonew=
		  allocate_newarray(&ptrarray,OBJECTARRAYTYPE,
							OBJECTARRAYINTERVAL+ao->___length___);
		obj=(struct ___Object___ *)ptrarray[2];
		tagd=(struct ___TagDescriptor___ *)ptrarray[3];
		ao=(struct ArrayObject *)tagd->flagptr;
#else
		struct ArrayObject * aonew=allocate_newarray(OBJECTARRAYTYPE,
			OBJECTARRAYINTERVAL+ao->___length___);
#endif
		aonew->___cachedCode___=ao->___cachedCode___+1;
		for(i=0; i<ao->___length___; i++) {
		  ARRAYSET(aonew, struct ___Object___*, i,
				   ARRAYGET(ao, struct ___Object___*, i));
		}
		ARRAYSET(aonew, struct ___Object___ *, ao->___cachedCode___, obj);
		tagd->flagptr=(struct ___Object___ *) aonew;
      }
    }
  }
}

/* This function clears a tag. */
#ifdef MULTICORE_GC
void tagclear(void *ptr,
              struct ___Object___ * obj,
              struct ___TagDescriptor___ * tagd) {
#else
void tagclear(struct ___Object___ * obj,
              struct ___TagDescriptor___ * tagd) {
#endif
  /* We'll assume that tag is alway there.
     Need to statically check for this of course. */
  struct ___Object___ * tagptr=obj->___tags___;

  if (tagptr->type==TAGTYPE) {
    if ((struct ___TagDescriptor___ *)tagptr==tagd)
      obj->___tags___=NULL;
  } else {
    struct ArrayObject *ao=(struct ArrayObject *) tagptr;
    int i;
    for(i=0; i<ao->___cachedCode___; i++) {
      struct ___TagDescriptor___ * td=
        ARRAYGET(ao, struct ___TagDescriptor___ *, i);
      if (td==tagd) {
		ao->___cachedCode___--;
		if (i<ao->___cachedCode___)
		  ARRAYSET(ao, struct ___TagDescriptor___ *, i,
			  ARRAYGET(ao,struct ___TagDescriptor___*,ao->___cachedCode___));
		ARRAYSET(ao,struct ___TagDescriptor___ *,ao->___cachedCode___, NULL);
		if (ao->___cachedCode___==0)
		  obj->___tags___=NULL;
		goto PROCESSCLEAR;
      }
    }
  }
PROCESSCLEAR:
  {
    struct ___Object___ *tagset=tagd->flagptr;
    if (tagset->type!=OBJECTARRAYTYPE) {
      if (tagset==obj)
		tagd->flagptr=NULL;
    } else {
      struct ArrayObject *ao=(struct ArrayObject *) tagset;
      int i;
      for(i=0; i<ao->___cachedCode___; i++) {
		struct ___Object___ * tobj=ARRAYGET(ao, struct ___Object___ *, i);
		if (tobj==obj) {
		  ao->___cachedCode___--;
		  if (i<ao->___cachedCode___)
			ARRAYSET(ao, struct ___Object___ *, i,
				ARRAYGET(ao, struct ___Object___ *, ao->___cachedCode___));
		  ARRAYSET(ao, struct ___Object___ *, ao->___cachedCode___, NULL);
		  if (ao->___cachedCode___==0)
			tagd->flagptr=NULL;
		  goto ENDCLEAR;
		}
      }
    }
  }
ENDCLEAR:
  return;
}

/* This function allocates a new tag. */
#ifdef MULTICORE_GC
struct ___TagDescriptor___ * allocate_tag(void *ptr,
                                          int index) {
  struct ___TagDescriptor___ * v=
    (struct ___TagDescriptor___ *) FREEMALLOC((struct garbagelist *) ptr,
                                              classsize[TAGTYPE]);
#else
struct ___TagDescriptor___ * allocate_tag(int index) {
  struct ___TagDescriptor___ * v=FREEMALLOC(classsize[TAGTYPE]);
#endif
  v->type=TAGTYPE;
  v->flag=index;
  return v;
}



/* This function updates the flag for object ptr.  It or's the flag
   with the or mask and and's it with the andmask. */

void flagbody(struct ___Object___ *ptr,
              int flag,
              struct parameterwrapper ** queues,
              int length,
              bool isnew);

int flagcomp(const int *val1, const int *val2) {
  return (*val1)-(*val2);
}

void flagorand(void * ptr,
               int ormask,
               int andmask,
               struct parameterwrapper ** queues,
               int length) {
  {
    int oldflag=((int *)ptr)[1];
    int flag=ormask|oldflag;
    flag&=andmask;
    flagbody(ptr, flag, queues, length, false);
  }
}

bool intflagorand(void * ptr,
                  int ormask,
                  int andmask) {
  {
    int oldflag=((int *)ptr)[1];
    int flag=ormask|oldflag;
    flag&=andmask;
    if (flag==oldflag)   /* Don't do anything */
      return false;
    else {
      flagbody(ptr, flag, NULL, 0, false);
      return true;
    }
  }
}

void flagorandinit(void * ptr,
                   int ormask,
                   int andmask) {
  int oldflag=((int *)ptr)[1];
  int flag=ormask|oldflag;
  flag&=andmask;
  flagbody(ptr,flag,NULL,0,true);
}

void flagbody(struct ___Object___ *ptr,
              int flag,
              struct parameterwrapper ** vqueues,
              int vlength,
              bool isnew) {
  struct parameterwrapper * flagptr = NULL;
  int i = 0;
  struct parameterwrapper ** queues = vqueues;
  int length = vlength;
  int next;
  int UNUSED, UNUSED2;
  int * enterflags = NULL;
  if((!isnew) && (queues == NULL)) {
    if(BAMBOO_NUM_OF_CORE < NUMCORESACTIVE) {
      queues = objectqueues[BAMBOO_NUM_OF_CORE][ptr->type];
      length = numqueues[BAMBOO_NUM_OF_CORE][ptr->type];
    } else {
      return;
    }
  }
  ptr->flag=flag;

  /*Remove object from all queues */
  for(i = 0; i < length; ++i) {
    flagptr = queues[i];
    ObjectHashget(flagptr->objectset, (int) ptr, (int *) &next,
                  (int *) &enterflags, &UNUSED, &UNUSED2);
    ObjectHashremove(flagptr->objectset, (int)ptr);
    if (enterflags!=NULL)
      RUNFREE(enterflags);
  }
}

void enqueueObject(void * vptr,
                   struct parameterwrapper ** vqueues,
                   int vlength) {
  struct ___Object___ *ptr = (struct ___Object___ *)vptr;

  {
    //struct QueueItem *tmpptr;
    struct parameterwrapper * parameter=NULL;
    int j;
    int i;
    struct parameterwrapper * prevptr=NULL;
    struct ___Object___ *tagptr=NULL;
    struct parameterwrapper ** queues = vqueues;
    int length = vlength;
    if(BAMBOO_NUM_OF_CORE > NUMCORESACTIVE - 1) {
      return;
    }
    if(queues == NULL) {
      queues = objectqueues[BAMBOO_NUM_OF_CORE][ptr->type];
      length = numqueues[BAMBOO_NUM_OF_CORE][ptr->type];
    }
    tagptr=ptr->___tags___;

    /* Outer loop iterates through all parameter queues an object of
       this type could be in.  */
    for(j = 0; j < length; ++j) {
      parameter = queues[j];
      /* Check tags */
      if (parameter->numbertags>0) {
		if (tagptr==NULL)
		  goto nextloop;  //that means the object has no tag
		//but that param needs tag
		else if(tagptr->type==TAGTYPE) {     //one tag
		  //struct ___TagDescriptor___ * tag=
		  //(struct ___TagDescriptor___*) tagptr;
		  for(i=0; i<parameter->numbertags; i++) {
			//slotid is parameter->tagarray[2*i];
			int tagid=parameter->tagarray[2*i+1];
			if (tagid!=tagptr->flag)
			  goto nextloop;   /*We don't have this tag */
		  }
		} else {                         //multiple tags
		  struct ArrayObject * ao=(struct ArrayObject *) tagptr;
		  for(i=0; i<parameter->numbertags; i++) {
			//slotid is parameter->tagarray[2*i];
			int tagid=parameter->tagarray[2*i+1];
			int j;
			for(j=0; j<ao->___cachedCode___; j++) {
			  if (tagid==ARRAYGET(ao, struct ___TagDescriptor___*, j)->flag)
				goto foundtag;
			}
			goto nextloop;
foundtag:
			;
		  }
		}
      }

      /* Check flags */
      for(i=0; i<parameter->numberofterms; i++) {
		int andmask=parameter->intarray[i*2];
		int checkmask=parameter->intarray[i*2+1];
		if ((ptr->flag&andmask)==checkmask) {
		  enqueuetasks(parameter, prevptr, ptr, NULL, 0);
		  prevptr=parameter;
		  break;
		}
      }
nextloop:
      ;
    }
  }
}

void enqueueObject_I(void * vptr,
                     struct parameterwrapper ** vqueues,
                     int vlength) {
  struct ___Object___ *ptr = (struct ___Object___ *)vptr;

  {
    //struct QueueItem *tmpptr;
    struct parameterwrapper * parameter=NULL;
    int j;
    int i;
    struct parameterwrapper * prevptr=NULL;
    struct ___Object___ *tagptr=NULL;
    struct parameterwrapper ** queues = vqueues;
    int length = vlength;
    if(BAMBOO_NUM_OF_CORE > NUMCORESACTIVE - 1) {
      return;
    }
    if(queues == NULL) {
      queues = objectqueues[BAMBOO_NUM_OF_CORE][ptr->type];
      length = numqueues[BAMBOO_NUM_OF_CORE][ptr->type];
    }
    tagptr=ptr->___tags___;

    /* Outer loop iterates through all parameter queues an object of
       this type could be in.  */
    for(j = 0; j < length; ++j) {
      parameter = queues[j];
      /* Check tags */
      if (parameter->numbertags>0) {
		if (tagptr==NULL)
		  goto nextloop;      //that means the object has no tag
		//but that param needs tag
		else if(tagptr->type==TAGTYPE) {   //one tag
		//struct ___TagDescriptor___*tag=(struct ___TagDescriptor___*)tagptr;
		  for(i=0; i<parameter->numbertags; i++) {
			//slotid is parameter->tagarray[2*i];
			int tagid=parameter->tagarray[2*i+1];
			if (tagid!=tagptr->flag)
			  goto nextloop;            /*We don't have this tag */
		  }
		} else {    //multiple tags
		  struct ArrayObject * ao=(struct ArrayObject *) tagptr;
		  for(i=0; i<parameter->numbertags; i++) {
			//slotid is parameter->tagarray[2*i];
			int tagid=parameter->tagarray[2*i+1];
			int j;
			for(j=0; j<ao->___cachedCode___; j++) {
			  if (tagid==ARRAYGET(ao, struct ___TagDescriptor___*, j)->flag)
				goto foundtag;
			}
			goto nextloop;
foundtag:
			;
		  }
		}
      }

      /* Check flags */
      for(i=0; i<parameter->numberofterms; i++) {
		int andmask=parameter->intarray[i*2];
		int checkmask=parameter->intarray[i*2+1];
		if ((ptr->flag&andmask)==checkmask) {
		  enqueuetasks_I(parameter, prevptr, ptr, NULL, 0);
		  prevptr=parameter;
		  break;
		}
      }
nextloop:
      ;
    }
  }
}


int * getAliasLock(void ** ptrs,
                   int length,
                   struct RuntimeHash * tbl) {
  if(length == 0) {
    return (int*)(RUNMALLOC(sizeof(int)));
  } else {
    int i = 0;
    int locks[length];
    int locklen = 0;
    bool redirect = false;
    int redirectlock = 0;
    for(; i < length; i++) {
      struct ___Object___ * ptr = (struct ___Object___ *)(ptrs[i]);
      int lock = 0;
      int j = 0;
      if(ptr->lock == NULL) {
		lock = (int)(ptr);
      } else {
		lock = (int)(ptr->lock);
      }
      if(redirect) {
		if(lock != redirectlock) {
		  RuntimeHashadd(tbl, lock, redirectlock);
		}
      } else {
		if(RuntimeHashcontainskey(tbl, lock)) {
		  // already redirected
		  redirect = true;
		  RuntimeHashget(tbl, lock, &redirectlock);
		  for(; j < locklen; j++) {
			if(locks[j] != redirectlock) {
			  RuntimeHashadd(tbl, locks[j], redirectlock);
			}
		  }
		} else {
		  bool insert = true;
		  for(j = 0; j < locklen; j++) {
			if(locks[j] == lock) {
			  insert = false;
			  break;
			} else if(locks[j] > lock) {
			  break;
			}
		  }
		  if(insert) {
			int h = locklen;
			for(; h > j; h--) {
			  locks[h] = locks[h-1];
			}
			locks[j] = lock;
			locklen++;
		  }
		}
      }
    }
    if(redirect) {
      return (int *)redirectlock;
    } else {
      return (int *)(locks[0]);
    }
  }
}

void addAliasLock(void * ptr,
                  int lock) {
  struct ___Object___ * obj = (struct ___Object___ *)ptr;
  if(((int)ptr != lock) && (obj->lock != (int*)lock)) {
    // originally no alias lock associated or have a different alias lock
    // flush it as the new one
    obj->lock = (int *)lock;
  }
}

#ifdef PROFILE
inline void setTaskExitIndex(int index) {
  taskInfoArray[taskInfoIndex]->exitIndex = index;
}

inline void addNewObjInfo(void * nobj) {
  if(taskInfoArray[taskInfoIndex]->newObjs == NULL) {
    taskInfoArray[taskInfoIndex]->newObjs = createQueue();
  }
  addNewItem(taskInfoArray[taskInfoIndex]->newObjs, nobj);
}
#endif

#ifdef MULTICORE_GC
// Only allocate local mem chunks to each core.
// If a core has used up its local shared memory, start gc.
void * localmalloc_I(int coren,
                     int isize,
                     int * allocsize) {
  void * mem = NULL;
  int gccorenum = (coren < NUMCORES4GC) ? (coren) : (coren % NUMCORES4GC);
  int i = 0;
  int j = 0;
  int tofindb = gc_core2block[2*gccorenum+i]+(NUMCORES4GC*2)*j;
  int totest = tofindb;
  int bound = BAMBOO_SMEM_SIZE_L;
  int foundsmem = 0;
  int size = 0;
  do {
    bound = (totest < NUMCORES4GC) ? BAMBOO_SMEM_SIZE_L : BAMBOO_SMEM_SIZE;
    int nsize = bamboo_smemtbl[totest];
    bool islocal = true;
    if(nsize < bound) {
      bool tocheck = true;
      // have some space in the block
      if(totest == tofindb) {
		// the first partition
		size = bound - nsize;
      } else if(nsize == 0) {
		// an empty partition, can be appended
		size += bound;
      } else {
		// not an empty partition, can not be appended
		// the last continuous block is not big enough, go to check the next
		// local block
		islocal = true;
		tocheck = false;
      } // if(totest == tofindb) else if(nsize == 0) else ...
      if(tocheck) {
		if(size >= isize) {
		  // have enough space in the block, malloc
		  foundsmem = 1;
		  break;
		} else {
		  // no enough space yet, try to append next continuous block
		  islocal = false;
		}  // if(size > isize) else ...
      }  // if(tocheck)
    } // if(nsize < bound)
    if(islocal) {
      // no space in the block, go to check the next block
      i++;
      if(2==i) {
		i = 0;
		j++;
      }
      tofindb = totest = gc_core2block[2*gccorenum+i]+(NUMCORES4GC*2)*j;
    } else {
      totest += 1;
    }  // if(islocal) else ...
    if(totest > gcnumblock-1-bamboo_reserved_smem) {
      // no more local mem, do not find suitable block
      foundsmem = 2;
      break;
    }  // if(totest > gcnumblock-1-bamboo_reserved_smem) ...
  } while(true);

  if(foundsmem == 1) {
    // find suitable block
    mem = gcbaseva+bamboo_smemtbl[tofindb]+((tofindb<NUMCORES4GC) ?
          (BAMBOO_SMEM_SIZE_L*tofindb) : (BAMBOO_LARGE_SMEM_BOUND+
          (tofindb-NUMCORES4GC)*BAMBOO_SMEM_SIZE));
    *allocsize = size;
    // set bamboo_smemtbl
    for(i = tofindb; i <= totest; i++) {
      bamboo_smemtbl[i]=(i<NUMCORES4GC)?BAMBOO_SMEM_SIZE_L:BAMBOO_SMEM_SIZE;
    }
  } else if(foundsmem == 2) {
    // no suitable block
    *allocsize = 0;
  }

  return mem;
} // void * localmalloc_I(int, int, int *)

#ifdef SMEMF
// Allocate the local shared memory to each core with the highest priority,
// if a core has used up its local shared memory, try to allocate the 
// shared memory that belong to its neighbours, if also failed, start gc.
void * fixedmalloc_I(int coren,
                     int isize,
                     int * allocsize) {
  void * mem = NULL;
  int i = 0;
  int j = 0;
  int k = 0;
  int gccorenum = (coren < NUMCORES4GC) ? (coren) : (coren % NUMCORES4GC);
  int coords_x = bamboo_cpu2coords[gccorenum*2];
  int coords_y = bamboo_cpu2coords[gccorenum*2+1];
  int ii = 1;
  int tofindb = gc_core2block[2*core2test[gccorenum][k]+i]+(NUMCORES4GC*2)*j;
  int totest = tofindb;
  int bound = BAMBOO_SMEM_SIZE_L;
  int foundsmem = 0;
  int size = 0;
  do {
    bound = (totest < NUMCORES4GC) ? BAMBOO_SMEM_SIZE_L : BAMBOO_SMEM_SIZE;
    int nsize = bamboo_smemtbl[totest];
    bool islocal = true;
    if(nsize < bound) {
      bool tocheck = true;
      // have some space in the block
      if(totest == tofindb) {
		// the first partition
		size = bound - nsize;
      } else if(nsize == 0) {
		// an empty partition, can be appended
		size += bound;
      } else {
		// not an empty partition, can not be appended
		// the last continuous block is not big enough, go to check the next
		// local block
		islocal = true;
		tocheck = false;
      } // if(totest == tofindb) else if(nsize == 0) else ...
      if(tocheck) {
		if(size >= isize) {
		  // have enough space in the block, malloc
		  foundsmem = 1;
		  break;
		} else {
		  // no enough space yet, try to append next continuous block
		  // TODO may consider to go to next local block?
		  islocal = false;
		}  // if(size > isize) else ...
      }  // if(tocheck)
    } // if(nsize < bound)
    if(islocal) {
      // no space in the block, go to check the next block
      i++;
      if(2==i) {
		i = 0;
		j++;
      }
      tofindb=totest=
		gc_core2block[2*core2test[gccorenum][k]+i]+(NUMCORES4GC*2)*j;
    } else {
      totest += 1;
    }  // if(islocal) else ...
    if(totest > gcnumblock-1-bamboo_reserved_smem) {
      // no more local mem, do not find suitable block on local mem
	  // try to malloc shared memory assigned to the neighbour cores
	  do{
		k++;
		if(k >= NUM_CORES2TEST) {
		  // no more memory available on either coren or its neighbour cores
		  foundsmem = 2;
		  goto memsearchresult;
		}
	  } while(core2test[gccorenum][k] == -1);
	  i = 0;
	  j = 0;
	  tofindb=totest=
		gc_core2block[2*core2test[gccorenum][k]+i]+(NUMCORES4GC*2)*j;
    }  // if(totest > gcnumblock-1-bamboo_reserved_smem) ...
  } while(true);

memsearchresult:
  if(foundsmem == 1) {
    // find suitable block
    mem = gcbaseva+bamboo_smemtbl[tofindb]+((tofindb<NUMCORES4GC) ?
          (BAMBOO_SMEM_SIZE_L*tofindb) : (BAMBOO_LARGE_SMEM_BOUND+
          (tofindb-NUMCORES4GC)*BAMBOO_SMEM_SIZE));
    *allocsize = size;
    // set bamboo_smemtbl
    for(i = tofindb; i <= totest; i++) {
      bamboo_smemtbl[i]=(i<NUMCORES4GC)?BAMBOO_SMEM_SIZE_L:BAMBOO_SMEM_SIZE;
    }
  } else if(foundsmem == 2) {
    // no suitable block
    *allocsize = 0;
  }

  return mem;
} // void * fixedmalloc_I(int, int, int *)
#endif // #ifdef SMEMF

#ifdef SMEMM
// Allocate the local shared memory to each core with the highest priority,
// if a core has used up its local shared memory, try to allocate the 
// shared memory that belong to its neighbours first, if failed, check 
// current memory allocation rate, if it has already reached the threshold,
// start gc, otherwise, allocate the shared memory globally.  If all the 
// shared memory has been used up, start gc.
void * mixedmalloc_I(int coren,
                     int isize,
                     int * allocsize) {
  void * mem = NULL;
  int i = 0;
  int j = 0;
  int k = 0;
  int gccorenum = (coren < NUMCORES4GC) ? (coren) : (coren % NUMCORES4GC);
  int ii = 1;
  int tofindb = gc_core2block[2*core2test[gccorenum][k]+i]+(NUMCORES4GC*2)*j;
  int totest = tofindb;
  int bound = BAMBOO_SMEM_SIZE_L;
  int foundsmem = 0;
  int size = 0;
  do {
    bound = (totest < NUMCORES4GC) ? BAMBOO_SMEM_SIZE_L : BAMBOO_SMEM_SIZE;
    int nsize = bamboo_smemtbl[totest];
    bool islocal = true;
    if(nsize < bound) {
      bool tocheck = true;
      // have some space in the block
      if(totest == tofindb) {
		// the first partition
		size = bound - nsize;
      } else if(nsize == 0) {
		// an empty partition, can be appended
		size += bound;
      } else {
		// not an empty partition, can not be appended
		// the last continuous block is not big enough, go to check the next
		// local block
		islocal = true;
		tocheck = false;
      } // if(totest == tofindb) else if(nsize == 0) else ...
      if(tocheck) {
		if(size >= isize) {
		  // have enough space in the block, malloc
		  foundsmem = 1;
		  break;
		} else {
		  // no enough space yet, try to append next continuous block
		  // TODO may consider to go to next local block?
		  islocal = false;
		}  // if(size > isize) else ...
      }  // if(tocheck)
    } // if(nsize < bound)
    if(islocal) {
      // no space in the block, go to check the next block
      i++;
      if(2==i) {
		i = 0;
		j++;
      }
      tofindb=totest=
		gc_core2block[2*core2test[gccorenum][k]+i]+(NUMCORES4GC*2)*j;
    } else {
      totest += 1;
    }  // if(islocal) else ...
    if(totest > gcnumblock-1-bamboo_reserved_smem) {
      // no more local mem, do not find suitable block on local mem
	  // try to malloc shared memory assigned to the neighbour cores
	  do{
		k++;
		if(k >= NUM_CORES2TEST) {
		  if(gcmem_mixed_usedmem >= gcmem_mixed_threshold) {
			// no more memory available on either coren or its neighbour cores
			foundsmem = 2;
			goto memmixedsearchresult;
		  } else {
			// try allocate globally
			mem = globalmalloc_I(coren, isize, allocsize);
			return mem;
		  }
		}
	  } while(core2test[gccorenum][k] == -1);
	  i = 0;
	  j = 0;
	  tofindb=totest=
		gc_core2block[2*core2test[gccorenum][k]+i]+(NUMCORES4GC*2)*j;
    }  // if(totest > gcnumblock-1-bamboo_reserved_smem) ...
  } while(true);

memmixedsearchresult:
  if(foundsmem == 1) {
    // find suitable block
    mem = gcbaseva+bamboo_smemtbl[tofindb]+((tofindb<NUMCORES4GC) ?
          (BAMBOO_SMEM_SIZE_L*tofindb) : (BAMBOO_LARGE_SMEM_BOUND+
          (tofindb-NUMCORES4GC)*BAMBOO_SMEM_SIZE));
    *allocsize = size;
    // set bamboo_smemtbl
    for(i = tofindb; i <= totest; i++) {
      bamboo_smemtbl[i]=(i<NUMCORES4GC)?BAMBOO_SMEM_SIZE_L:BAMBOO_SMEM_SIZE;
    }
	gcmem_mixed_usedmem += size;
	if(tofindb == bamboo_free_block) {
      bamboo_free_block = totest+1;
    }
  } else if(foundsmem == 2) {
    // no suitable block
    *allocsize = 0;
  }

  return mem;
} // void * mixedmalloc_I(int, int, int *)
#endif // #ifdef SMEMM

// Allocate all the memory chunks globally, do not consider the host cores
// When all the shared memory are used up, start gc.
void * globalmalloc_I(int coren,
                      int isize,
                      int * allocsize) {
  void * mem = NULL;
  int tofindb = bamboo_free_block;       //0;
  int totest = tofindb;
  int bound = BAMBOO_SMEM_SIZE_L;
  int foundsmem = 0;
  int size = 0;
  if(tofindb > gcnumblock-1-bamboo_reserved_smem) {
	// Out of shared memory
    *allocsize = 0;
    return NULL;
  }
  do {
    bound = (totest < NUMCORES4GC) ? BAMBOO_SMEM_SIZE_L : BAMBOO_SMEM_SIZE;
    int nsize = bamboo_smemtbl[totest];
    bool isnext = false;
    if(nsize < bound) {
      bool tocheck = true;
      // have some space in the block
      if(totest == tofindb) {
		// the first partition
		size = bound - nsize;
      } else if(nsize == 0) {
		// an empty partition, can be appended
		size += bound;
      } else {
		// not an empty partition, can not be appended
		// the last continuous block is not big enough, start another block
		isnext = true;
		tocheck = false;
      }  // if(totest == tofindb) else if(nsize == 0) else ...
      if(tocheck) {
		if(size >= isize) {
		  // have enough space in the block, malloc
		  foundsmem = 1;
		  break;
		}  // if(size > isize)
      }   // if(tocheck)
    } else {
      isnext = true;
    }  // if(nsize < bound) else ...
    totest += 1;
    if(totest > gcnumblock-1-bamboo_reserved_smem) {
      // no more local mem, do not find suitable block
      foundsmem = 2;
      break;
    }  // if(totest > gcnumblock-1-bamboo_reserved_smem) ...
    if(isnext) {
      // start another block
      tofindb = totest;
    } // if(islocal)
  } while(true);

  if(foundsmem == 1) {
    // find suitable block
    mem = gcbaseva+bamboo_smemtbl[tofindb]+((tofindb<NUMCORES4GC) ?
          (BAMBOO_SMEM_SIZE_L*tofindb) : (BAMBOO_LARGE_SMEM_BOUND+
          (tofindb-NUMCORES4GC)*BAMBOO_SMEM_SIZE));
    *allocsize = size;
    // set bamboo_smemtbl
    for(int i = tofindb; i <= totest; i++) {
      bamboo_smemtbl[i]=(i<NUMCORES4GC)?BAMBOO_SMEM_SIZE_L:BAMBOO_SMEM_SIZE;
    }
    if(tofindb == bamboo_free_block) {
      bamboo_free_block = totest+1;
    }
  } else if(foundsmem == 2) {
    // no suitable block
    *allocsize = 0;
    mem = NULL;
  }

  return mem;
} // void * globalmalloc_I(int, int, int *)
#endif // #ifdef MULTICORE_GC

// malloc from the shared memory
void * smemalloc_I(int coren,
                   int size,
                   int * allocsize) {
  void * mem = NULL;
#ifdef MULTICORE_GC
  int isize = size+(BAMBOO_CACHE_LINE_SIZE);

  // go through the bamboo_smemtbl for suitable partitions
  switch(bamboo_smem_mode) {
  case SMEMLOCAL: {
    mem = localmalloc_I(coren, isize, allocsize);
    break;
  }

  case SMEMFIXED: {
#ifdef SMEMF
	mem = fixedmalloc_I(coren, isize, allocsize);
#else
	// not supported yet
	BAMBOO_EXIT(0xe001);
#endif
    break;
  }

  case SMEMMIXED: {
#ifdef SMEMM
	mem = mixedmalloc_I(coren, isize, allocsize);
#else
	// not supported yet
    BAMBOO_EXIT(0xe002);
#endif
    break;
  }

  case SMEMGLOBAL: {
    mem = globalmalloc_I(coren, isize, allocsize);
    break;
  }

  default:
    break;
  }

  if(mem == NULL) {
#else 
  int toallocate = (size>(BAMBOO_SMEM_SIZE)) ? (size) : (BAMBOO_SMEM_SIZE);
  if(toallocate > bamboo_free_smem_size) {
	// no enough mem
	mem = NULL;
  } else {
	mem = (void *)bamboo_free_smemp;
	bamboo_free_smemp = ((void*)bamboo_free_smemp) + toallocate;
	bamboo_free_smem_size -= toallocate;
  }
  *allocsize = toallocate;
  if(mem == NULL) {
#endif // MULTICORE_GC
    // no enough shared global memory
    *allocsize = 0;
#ifdef MULTICORE_GC
	if(!gcflag) {
	  gcflag = true;
	  // inform other cores to stop and wait for gc
	  gcprecheck = true;
	  for(int i = 0; i < NUMCORESACTIVE; i++) {
		// reuse the gcnumsendobjs & gcnumreceiveobjs
		gccorestatus[i] = 1;
		gcnumsendobjs[0][i] = 0;
		gcnumreceiveobjs[0][i] = 0;
	  }
	  for(int i = 0; i < NUMCORESACTIVE; i++) {
		if(i != BAMBOO_NUM_OF_CORE) {
		  if(BAMBOO_CHECK_SEND_MODE()) {
			cache_msg_1(i, GCSTARTPRE);
		  } else {
			send_msg_1(i, GCSTARTPRE, true);
		  }
		}
	  }
	}
	return NULL;
#else
    BAMBOO_DEBUGPRINT(0xe003);
    BAMBOO_EXIT(0xe003);
#endif
  }
  return mem;
}  // void * smemalloc_I(int, int, int)

INLINE int checkMsgLength_I(int size) {
#ifdef DEBUG
#ifndef TILERA
  BAMBOO_DEBUGPRINT(0xcccc);
#endif
#endif
  int type = msgdata[msgdataindex];
  switch(type) {
  case STATUSCONFIRM:
  case TERMINATE:
#ifdef MULTICORE_GC
  case GCSTARTPRE:
  case GCSTARTINIT:
  case GCSTART:
  case GCSTARTMAPINFO:
  case GCSTARTFLUSH:
  case GCFINISH:
  case GCMARKCONFIRM:
  case GCLOBJREQUEST:
#ifdef GC_CACHE_ADAPT
  case GCSTARTPREF:
#endif // GC_CACHE_ADAPT
#endif // MULTICORE_GC
  {
	msglength = 1;
	break;
  }

  case PROFILEOUTPUT:
  case PROFILEFINISH:
#ifdef MULTICORE_GC
  case GCSTARTCOMPACT:
  case GCMARKEDOBJ:
  case GCFINISHINIT:
  case GCFINISHMAPINFO:
  case GCFINISHFLUSH:
#ifdef GC_CACHE_ADAPT
  case GCFINISHPREF:
#endif // GC_CACHE_ADAPT
#endif // MULTICORE_GC
  {
	msglength = 2;
	break;
  }

  case MEMREQUEST:
  case MEMRESPONSE:
#ifdef MULTICORE_GC
  case GCMAPREQUEST:
  case GCMAPINFO:
  case GCMAPTBL:
  case GCLOBJMAPPING:
#endif
  {
	msglength = 3;
	break;
  }

  case TRANSTALL:
  case LOCKGROUNT:
  case LOCKDENY:
  case LOCKRELEASE:
  case REDIRECTGROUNT:
  case REDIRECTDENY:
  case REDIRECTRELEASE:
#ifdef MULTICORE_GC
  case GCFINISHPRE:
  case GCFINISHMARK:
  case GCMOVESTART:
#ifdef GC_PROFILE
  case GCPROFILES:
#endif
#endif
  {
	msglength = 4;
	break;
  }

  case LOCKREQUEST:
  case STATUSREPORT:
#ifdef MULTICORE_GC
  case GCFINISHCOMPACT:
  case GCMARKREPORT:
#endif
  {
	msglength = 5;
	break;
  }

  case REDIRECTLOCK:
  {
    msglength = 6;
    break;
  }

  case TRANSOBJ:   // nonfixed size
#ifdef MULTICORE_GC
  case GCLOBJINFO:
#endif
  {             // nonfixed size
	if(size > 1) {
	  msglength = msgdata[msgdataindex+1];
	} else {
	  return -1;
	}
	break;
  }

  default:
  {
    BAMBOO_DEBUGPRINT_REG(type);
	BAMBOO_DEBUGPRINT_REG(size);
    BAMBOO_DEBUGPRINT_REG(msgdataindex);
	BAMBOO_DEBUGPRINT_REG(msgdatalast);
	BAMBOO_DEBUGPRINT_REG(msgdatafull);
    int i = 6;
    while(i-- > 0) {
      BAMBOO_DEBUGPRINT(msgdata[msgdataindex+i]);
    }
    BAMBOO_EXIT(0xe004);
    break;
  }
  }
#ifdef DEBUG
#ifndef TILERA
  BAMBOO_DEBUGPRINT_REG(msgdata[msgdataindex]);
#endif
#endif
#ifdef DEBUG
#ifndef TILERA
  BAMBOO_DEBUGPRINT(0xffff);
#endif
#endif
  return msglength;
}

INLINE void processmsg_transobj_I() {
#ifdef PROFILE_INTERRUPT
  /*if(!interruptInfoOverflow) {
    InterruptInfo* intInfo = RUNMALLOC_I(sizeof(struct interrupt_info));
    interruptInfoArray[interruptInfoIndex] = intInfo;
    intInfo->startTime = BAMBOO_GET_EXE_TIME();
    intInfo->endTime = -1;
  }*/
#endif
  MSG_INDEXINC_I();
  struct transObjInfo * transObj=RUNMALLOC_I(sizeof(struct transObjInfo));
  int k = 0;
#ifdef DEBUG
#ifndef CLOSE_PRINT
  BAMBOO_DEBUGPRINT(0xe880);
#endif
#endif
  if(BAMBOO_NUM_OF_CORE > NUMCORESACTIVE - 1) {
#ifndef CLOSE_PRINT
    BAMBOO_DEBUGPRINT_REG(msgdata[msgdataindex] /*[2]*/);
#endif
    BAMBOO_EXIT(0xe005);
  }
  // store the object and its corresponding queue info, enqueue it later
  transObj->objptr = (void *)msgdata[msgdataindex];  //[2]
  MSG_INDEXINC_I();
  transObj->length = (msglength - 3) / 2;
  transObj->queues = RUNMALLOC_I(sizeof(int)*(msglength - 3));
  for(k = 0; k < transObj->length; ++k) {
    transObj->queues[2*k] = msgdata[msgdataindex];   //[3+2*k];
    MSG_INDEXINC_I();
#ifdef DEBUG
#ifndef CLOSE_PRINT
    //BAMBOO_DEBUGPRINT_REG(transObj->queues[2*k]);
#endif
#endif
    transObj->queues[2*k+1] = msgdata[msgdataindex]; //[3+2*k+1];
    MSG_INDEXINC_I();
#ifdef DEBUG
#ifndef CLOSE_PRINT
    //BAMBOO_DEBUGPRINT_REG(transObj->queues[2*k+1]);
#endif
#endif
  }
  // check if there is an existing duplicate item
  {
    struct QueueItem * qitem = getHead(&objqueue);
    struct QueueItem * prev = NULL;
    while(qitem != NULL) {
      struct transObjInfo * tmpinfo =
        (struct transObjInfo *)(qitem->objectptr);
      if(tmpinfo->objptr == transObj->objptr) {
		// the same object, remove outdate one
		RUNFREE(tmpinfo->queues);
		RUNFREE(tmpinfo);
		removeItem(&objqueue, qitem);
		//break;
      } else {
		prev = qitem;
      }
      if(prev == NULL) {
		qitem = getHead(&objqueue);
      } else {
		qitem = getNextQueueItem(prev);
      }
    }
    addNewItem_I(&objqueue, (void *)transObj);
  }
  ++(self_numreceiveobjs);
#ifdef MULTICORE_GC
  if(gcprocessing) {
	if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
	  // set the gcprecheck to enable checking again
	  gcprecheck = true;
	} else {
	  // send a update pregc information msg to the master core
	  if(BAMBOO_CHECK_SEND_MODE()) {
		cache_msg_4(STARTUPCORE, GCFINISHPRE, BAMBOO_NUM_OF_CORE, 
			self_numsendobjs, self_numreceiveobjs);
	  } else {
		send_msg_4(STARTUPCORE, GCFINISHPRE, BAMBOO_NUM_OF_CORE, 
			self_numsendobjs, self_numreceiveobjs, true);
	  }
	}
  }
#endif 
#ifdef PROFILE_INTERRUPT
  /*if(!interruptInfoOverflow) {
    interruptInfoArray[interruptInfoIndex]->endTime=BAMBOO_GET_EXE_TIME();
    interruptInfoIndex++;
    if(interruptInfoIndex == INTERRUPTINFOLENGTH) {
      interruptInfoOverflow = true;
    }
  }*/
#endif
}

INLINE void processmsg_transtall_I() {
  if(BAMBOO_NUM_OF_CORE != STARTUPCORE) {
    // non startup core can not receive stall msg
#ifndef CLOSE_PRINT
    BAMBOO_DEBUGPRINT_REG(msgdata[msgdataindex] /*[1]*/);
#endif
    BAMBOO_EXIT(0xe006);
  }
  int num_core = msgdata[msgdataindex]; //[1]
  MSG_INDEXINC_I();
  if(num_core < NUMCORESACTIVE) {
#ifdef DEBUG
#ifndef CLOSE_PRINT
    BAMBOO_DEBUGPRINT(0xe881);
#endif
#endif
    corestatus[num_core] = 0;
    numsendobjs[num_core] = msgdata[msgdataindex]; //[2];
    MSG_INDEXINC_I();
    numreceiveobjs[num_core] = msgdata[msgdataindex]; //[3];
    MSG_INDEXINC_I();
  }
}

#ifndef MULTICORE_GC
INLINE void processmsg_lockrequest_I() {
  // check to see if there is a lock exist for the required obj
  // msgdata[1] -> lock type
  int locktype = msgdata[msgdataindex]; //[1];
  MSG_INDEXINC_I();
  int data2 = msgdata[msgdataindex];  // obj pointer
  MSG_INDEXINC_I();
  int data3 = msgdata[msgdataindex];  // lock
  MSG_INDEXINC_I();
  int data4 = msgdata[msgdataindex];  // request core
  MSG_INDEXINC_I();
  // -1: redirected, 0: approved, 1: denied
  int deny=processlockrequest(locktype, data3, data2, data4, data4, true);
  if(deny == -1) {
    // this lock request is redirected
    return;
  } else {
    // send response msg
    // for 32 bit machine, the size is always 4 words, cache the msg first
    int tmp = deny==1 ? LOCKDENY : LOCKGROUNT;
    if(BAMBOO_CHECK_SEND_MODE()) {
	  cache_msg_4(data4, tmp, locktype, data2, data3);
    } else {
	  send_msg_4(data4, tmp, locktype, data2, data3, true);
    }
  }
}

INLINE void processmsg_lockgrount_I() {
  MSG_INDEXINC_I();
  if(BAMBOO_NUM_OF_CORE > NUMCORESACTIVE - 1) {
#ifndef CLOSE_PRINT
    BAMBOO_DEBUGPRINT_REG(msgdata[msgdataindex] /*[2]*/);
#endif
    BAMBOO_EXIT(0xe007);
  }
  int data2 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data3 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  if((lockobj == data2) && (lock2require == data3)) {
#ifdef DEBUG
#ifndef CLOSE_PRINT
    BAMBOO_DEBUGPRINT(0xe882);
#endif
#endif
    lockresult = 1;
    lockflag = true;
#ifndef INTERRUPT
    reside = false;
#endif
  } else {
    // conflicts on lockresults
#ifndef CLOSE_PRINT
    BAMBOO_DEBUGPRINT_REG(data2);
#endif
    BAMBOO_EXIT(0xe008);
  }
}

INLINE void processmsg_lockdeny_I() {
  MSG_INDEXINC_I();
  int data2 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data3 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  if(BAMBOO_NUM_OF_CORE > NUMCORESACTIVE - 1) {
#ifndef CLOSE_PRINT
    BAMBOO_DEBUGPRINT_REG(data2);
#endif
    BAMBOO_EXIT(0xe009);
  }
  if((lockobj == data2) && (lock2require == data3)) {
#ifdef DEBUG
#ifndef CLOSE_PRINT
    BAMBOO_DEBUGPRINT(0xe883);
#endif
#endif
    lockresult = 0;
    lockflag = true;
#ifndef INTERRUPT
    reside = false;
#endif
  } else {
    // conflicts on lockresults
#ifndef CLOSE_PRINT
    BAMBOO_DEBUGPRINT_REG(data2);
#endif
    BAMBOO_EXIT(0xe00a);
  }
}

INLINE void processmsg_lockrelease_I() {
  int data1 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data2 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  // receive lock release msg
  processlockrelease(data1, data2, 0, false);
}

INLINE void processmsg_redirectlock_I() {
  // check to see if there is a lock exist for the required obj
  int data1 = msgdata[msgdataindex];
  MSG_INDEXINC_I();    //msgdata[1]; // lock type
  int data2 = msgdata[msgdataindex];
  MSG_INDEXINC_I();    //msgdata[2]; // obj pointer
  int data3 = msgdata[msgdataindex];
  MSG_INDEXINC_I();    //msgdata[3]; // redirect lock
  int data4 = msgdata[msgdataindex];
  MSG_INDEXINC_I();    //msgdata[4]; // root request core
  int data5 = msgdata[msgdataindex];
  MSG_INDEXINC_I();    //msgdata[5]; // request core
  int deny = processlockrequest(data1, data3, data2, data5, data4, true);
  if(deny == -1) {
    // this lock request is redirected
    return;
  } else {
    // send response msg
    // for 32 bit machine, the size is always 4 words, cache the msg first
    if(BAMBOO_CHECK_SEND_MODE()) {
	  cache_msg_4(data4, deny==1 ? REDIRECTDENY : REDIRECTGROUNT,
				  data1, data2, data3);
    } else {
	  send_msg_4(data4, deny==1?REDIRECTDENY:REDIRECTGROUNT,
				 data1, data2, data3, true);
    }
  }
}

INLINE void processmsg_redirectgrount_I() {
  MSG_INDEXINC_I();
  int data2 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  if(BAMBOO_NUM_OF_CORE > NUMCORESACTIVE - 1) {
#ifndef CLOSE_PRINT
    BAMBOO_DEBUGPRINT_REG(data2);
#endif
    BAMBOO_EXIT(0xe00b);
  }
  if(lockobj == data2) {
#ifdef DEBUG
#ifndef CLOSE_PRINT
    BAMBOO_DEBUGPRINT(0xe891);
#endif
#endif
    int data3 = msgdata[msgdataindex];
    MSG_INDEXINC_I();
    lockresult = 1;
    lockflag = true;
    RuntimeHashadd_I(objRedirectLockTbl, lockobj, data3);
#ifndef INTERRUPT
    reside = false;
#endif
  } else {
    // conflicts on lockresults
#ifndef CLOSE_PRINT
    BAMBOO_DEBUGPRINT_REG(data2);
#endif
    BAMBOO_EXIT(0xe00c);
  }
}

INLINE void processmsg_redirectdeny_I() {
  MSG_INDEXINC_I();
  int data2 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  if(BAMBOO_NUM_OF_CORE > NUMCORESACTIVE - 1) {
#ifndef CLOSE_PRINT
    BAMBOO_DEBUGPRINT_REG(data2);
#endif
    BAMBOO_EXIT(0xe00d);
  }
  if(lockobj == data2) {
#ifdef DEBUG
#ifndef CLOSE_PRINT
    BAMBOO_DEBUGPRINT(0xe892);
#endif
#endif
    lockresult = 0;
    lockflag = true;
#ifndef INTERRUPT
    reside = false;
#endif
  } else {
    // conflicts on lockresults
#ifndef CLOSE_PRINT
    BAMBOO_DEBUGPRINT_REG(data2);
#endif
    BAMBOO_EXIT(0xe00e);
  }
}

INLINE void processmsg_redirectrelease_I() {
  int data1 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data2 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data3 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  processlockrelease(data1, data2, data3, true);
}
#endif // #ifndef MULTICORE_GC

#ifdef PROFILE
INLINE void processmsg_profileoutput_I() {
  if(BAMBOO_NUM_OF_CORE == STARTUPCORE) {
    // startup core can not receive profile output finish msg
    BAMBOO_EXIT(0xe00f);
  }
#ifdef DEBUG
#ifndef CLOSE_PRINT
  BAMBOO_DEBUGPRINT(0xe885);
#endif
#endif
  stall = true;
  totalexetime = msgdata[msgdataindex];  //[1]
  MSG_INDEXINC_I();
#ifdef RT_TEST
  BAMBOO_DEBUGPRINT_REG(dot_num);
#else
  outputProfileData();
#endif
  // cache the msg first
  if(BAMBOO_CHECK_SEND_MODE()) {
	cache_msg_2(STARTUPCORE, PROFILEFINISH, BAMBOO_NUM_OF_CORE);
  } else {
	send_msg_2(STARTUPCORE, PROFILEFINISH, BAMBOO_NUM_OF_CORE, true);
  }
}

INLINE void processmsg_profilefinish_I() {
  if(BAMBOO_NUM_OF_CORE != STARTUPCORE) {
    // non startup core can not receive profile output finish msg
#ifndef CLOSE_PRINT
    BAMBOO_DEBUGPRINT_REG(msgdata[msgdataindex /*1*/]);
#endif
    BAMBOO_EXIT(0xe010);
  }
#ifdef DEBUG
#ifndef CLOSE_PRINT
  BAMBOO_DEBUGPRINT(0xe886);
#endif
#endif
  int data1 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  profilestatus[data1] = 0;
}
#endif // #ifdef PROFILE

INLINE void processmsg_statusconfirm_I() {
  if((BAMBOO_NUM_OF_CORE == STARTUPCORE)
     || (BAMBOO_NUM_OF_CORE > NUMCORESACTIVE - 1)) {
    // wrong core to receive such msg
    BAMBOO_EXIT(0xe011);
  } else {
    // send response msg
#ifdef DEBUG
#ifndef CLOSE_PRINT
    BAMBOO_DEBUGPRINT(0xe887);
#endif
#endif
    // cache the msg first
    if(BAMBOO_CHECK_SEND_MODE()) {
	  cache_msg_5(STARTUPCORE, STATUSREPORT,
				  busystatus ? 1 : 0, BAMBOO_NUM_OF_CORE,
				  self_numsendobjs, self_numreceiveobjs);
    } else {
	  send_msg_5(STARTUPCORE, STATUSREPORT, busystatus?1:0,
				 BAMBOO_NUM_OF_CORE, self_numsendobjs,
				 self_numreceiveobjs, true);
    }
  }
}

INLINE void processmsg_statusreport_I() {
  int data1 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data2 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data3 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data4 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  // receive a status confirm info
  if(BAMBOO_NUM_OF_CORE != STARTUPCORE) {
    // wrong core to receive such msg
#ifndef CLOSE_PRINT
    BAMBOO_DEBUGPRINT_REG(data2);
#endif
    BAMBOO_EXIT(0xe012);
  } else {
#ifdef DEBUG
#ifndef CLOSE_PRINT
    BAMBOO_DEBUGPRINT(0xe888);
#endif
#endif
    if(waitconfirm) {
      numconfirm--;
    }
    corestatus[data2] = data1;
    numsendobjs[data2] = data3;
    numreceiveobjs[data2] = data4;
  }
}

INLINE void processmsg_terminate_I() {
#ifdef DEBUG
#ifndef CLOSE_PRINT
  BAMBOO_DEBUGPRINT(0xe889);
#endif
#endif
  disruntimedata();
#ifdef MULTICORE_GC
#ifdef GC_CACHE_ADAPT
  bamboo_mask_timer_intr(); // disable the TILE_TIMER interrupt
#endif // GC_CACHE_ADAPT
#endif // MULTICORE_GC
  BAMBOO_EXIT_APP(0);
}

INLINE void processmsg_memrequest_I() {
#ifdef PROFILE_INTERRUPT
  /*if(!interruptInfoOverflow) {
    InterruptInfo* intInfo = RUNMALLOC_I(sizeof(struct interrupt_info));
    interruptInfoArray[interruptInfoIndex] = intInfo;
    intInfo->startTime = BAMBOO_GET_EXE_TIME();
    intInfo->endTime = -1;
  }*/
#endif
  int data1 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data2 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  // receive a shared memory request msg
  if(BAMBOO_NUM_OF_CORE != STARTUPCORE) {
    // wrong core to receive such msg
#ifndef CLOSE_PRINT
    BAMBOO_DEBUGPRINT_REG(data2);
#endif
    BAMBOO_EXIT(0xe013);
  } else {
#ifdef DEBUG
#ifndef CLOSE_PRINT
    BAMBOO_DEBUGPRINT(0xe88a);
#endif
#endif
    int allocsize = 0;
    void * mem = NULL;
#ifdef MULTICORE_GC
    if(gcprocessing) {
      // is currently doing gc, dump this msg
      if(INITPHASE == gcphase) {
		// if still in the initphase of gc, send a startinit msg again,
		// cache the msg first
		if(BAMBOO_CHECK_SEND_MODE()) {
		  cache_msg_1(data2, GCSTARTINIT);
		} else {
		  send_msg_1(data2, GCSTARTINIT, true);
		}
      }
    } else {
#endif
    mem = smemalloc_I(data2, data1, &allocsize);
    if(mem != NULL) {
      // send the start_va to request core, cache the msg first
      if(BAMBOO_CHECK_SEND_MODE()) {
		cache_msg_3(data2, MEMRESPONSE, mem, allocsize);
      } else {
		send_msg_3(data2, MEMRESPONSE, mem, allocsize, true);
	  }
    } //else 
	  // if mem == NULL, the gcflag of the startup core has been set
	  // and all the other cores have been informed to start gc
#ifdef MULTICORE_GC
  }
#endif
  }
#ifdef PROFILE_INTERRUPT
  /*if(!interruptInfoOverflow) {
    interruptInfoArray[interruptInfoIndex]->endTime=BAMBOO_GET_EXE_TIME();
    interruptInfoIndex++;
    if(interruptInfoIndex == INTERRUPTINFOLENGTH) {
      interruptInfoOverflow = true;
    }
  }*/
#endif
}

INLINE void processmsg_memresponse_I() {
  int data1 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data2 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  // receive a shared memory response msg
#ifdef DEBUG
#ifndef CLOSE_PRINT
  BAMBOO_DEBUGPRINT(0xe88b);
#endif
#endif
#ifdef MULTICORE_GC
  // if is currently doing gc, dump this msg
  if(!gcprocessing) {
#endif
  if(data2 == 0) {
    bamboo_smem_size = 0;
    bamboo_cur_msp = 0;
#ifdef MULTICORE_GC
	bamboo_smem_zero_top = 0;
#endif
  } else {
#ifdef MULTICORE_GC
    // fill header to store the size of this mem block
    BAMBOO_MEMSET_WH(data1, '\0', BAMBOO_CACHE_LINE_SIZE); 
	//memset(data1, 0, BAMBOO_CACHE_LINE_SIZE);
    (*((int*)data1)) = data2;
    bamboo_smem_size = data2 - BAMBOO_CACHE_LINE_SIZE;
    bamboo_cur_msp = data1 + BAMBOO_CACHE_LINE_SIZE;
	bamboo_smem_zero_top = bamboo_cur_msp;
#else
    bamboo_smem_size = data2;
    bamboo_cur_msp =(void*)(data1);
#endif
  }
  smemflag = true;
#ifdef MULTICORE_GC
}
#endif
}

#ifdef MULTICORE_GC
INLINE void processmsg_gcstartpre_I() {
  if(gcprocessing) {
	// already stall for gc
	// send a update pregc information msg to the master core
	if(BAMBOO_CHECK_SEND_MODE()) {
	  cache_msg_4(STARTUPCORE, GCFINISHPRE, BAMBOO_NUM_OF_CORE, 
		  self_numsendobjs, self_numreceiveobjs);
	} else {
	  send_msg_4(STARTUPCORE, GCFINISHPRE, BAMBOO_NUM_OF_CORE, 
		  self_numsendobjs, self_numreceiveobjs, true);
	}
  } else {
	// the first time to be informed to start gc
	gcflag = true;
	if(!smemflag) {
	  // is waiting for response of mem request
	  // let it return NULL and start gc
	  bamboo_smem_size = 0;
	  bamboo_cur_msp = NULL;
	  smemflag = true;
	  bamboo_smem_zero_top = NULL;
	}
  }
}

INLINE void processmsg_gcstartinit_I() {
  gcphase = INITPHASE;
}

INLINE void processmsg_gcstart_I() {
#ifdef DEBUG
#ifndef CLOSE_PRINT
  BAMBOO_DEBUGPRINT(0xe88c);
#endif
#endif
  // set the GC flag
  gcphase = MARKPHASE;
}

INLINE void processmsg_gcstartcompact_I() {
  gcblock2fill = msgdata[msgdataindex];
  MSG_INDEXINC_I();  //msgdata[1];
  gcphase = COMPACTPHASE;
}

INLINE void processmsg_gcstartmapinfo_I() {
  gcphase = MAPPHASE;
}

INLINE void processmsg_gcstartflush_I() {
  gcphase = FLUSHPHASE;
}

INLINE void processmsg_gcfinishpre_I() {
  int data1 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data2 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data3 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  // received a init phase finish msg
  if(BAMBOO_NUM_OF_CORE != STARTUPCORE) {
    // non startup core can not receive this msg
#ifndef CLOSE_PRINT
    BAMBOO_DEBUGPRINT_REG(data1);
#endif
    BAMBOO_EXIT(0xe014);
  }
  // All cores should do init GC
  if(!gcprecheck) {
	gcprecheck = true;
  }
  gccorestatus[data1] = 0;
  gcnumsendobjs[0][data1] = data2;
  gcnumreceiveobjs[0][data1] = data3;
}

INLINE void processmsg_gcfinishinit_I() {
  int data1 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  // received a init phase finish msg
  if(BAMBOO_NUM_OF_CORE != STARTUPCORE) {
    // non startup core can not receive this msg
#ifndef CLOSE_PRINT
    BAMBOO_DEBUGPRINT_REG(data1);
#endif
    BAMBOO_EXIT(0xe015);
  }
#ifdef DEBUG
  BAMBOO_DEBUGPRINT(0xe88c);
  BAMBOO_DEBUGPRINT_REG(data1);
#endif
  // All cores should do init GC
  if(data1 < NUMCORESACTIVE) {
    gccorestatus[data1] = 0;
  }
}

INLINE void processmsg_gcfinishmark_I() {
  int data1 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data2 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data3 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  // received a mark phase finish msg
  if(BAMBOO_NUM_OF_CORE != STARTUPCORE) {
    // non startup core can not receive this msg
#ifndef CLOSE_PRINT
    BAMBOO_DEBUGPRINT_REG(data1);
#endif
    BAMBOO_EXIT(0xe016);
  }
  // all cores should do mark
  if(data1 < NUMCORESACTIVE) {
    gccorestatus[data1] = 0;
	int entry_index = 0;
	if(waitconfirm)  {
	  // phase 2
	  entry_index = (gcnumsrobjs_index == 0) ? 1 : 0;
	} else {
	  // phase 1
	  entry_index = gcnumsrobjs_index;
	}
    gcnumsendobjs[entry_index][data1] = data2;
    gcnumreceiveobjs[entry_index][data1] = data3;
  }
}

INLINE void processmsg_gcfinishcompact_I() {
  if(BAMBOO_NUM_OF_CORE != STARTUPCORE) {
    // non startup core can not receive this msg
    // return -1
#ifndef CLOSE_PRINT
    BAMBOO_DEBUGPRINT_REG(msgdata[msgdataindex] /*[1]*/);
#endif
    BAMBOO_EXIT(0xe017);
  }
  int cnum = msgdata[msgdataindex];
  MSG_INDEXINC_I();       //msgdata[1];
  int filledblocks = msgdata[msgdataindex];
  MSG_INDEXINC_I();       //msgdata[2];
  int heaptop = msgdata[msgdataindex];
  MSG_INDEXINC_I();       //msgdata[3];
  int data4 = msgdata[msgdataindex];
  MSG_INDEXINC_I();       //msgdata[4];
  // only gc cores need to do compact
  if(cnum < NUMCORES4GC) {
    if(COMPACTPHASE == gcphase) {
      gcfilledblocks[cnum] = filledblocks;
      gcloads[cnum] = heaptop;
    }
    if(data4 > 0) {
      // ask for more mem
      int startaddr = 0;
      int tomove = 0;
      int dstcore = 0;
      if(gcfindSpareMem_I(&startaddr, &tomove, &dstcore, data4, cnum)) {
		// cache the msg first
		if(BAMBOO_CHECK_SEND_MODE()) {
		  cache_msg_4(cnum, GCMOVESTART, dstcore, startaddr, tomove);
		} else {
		  send_msg_4(cnum, GCMOVESTART, dstcore, startaddr, tomove, true);
		}
      }
    } else {
      gccorestatus[cnum] = 0;
    }  // if(data4>0)
  }  // if(cnum < NUMCORES4GC)
}

INLINE void processmsg_gcfinishmapinfo_I() {
  int data1 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  // received a map phase finish msg
  if(BAMBOO_NUM_OF_CORE != STARTUPCORE) {
    // non startup core can not receive this msg
#ifndef CLOSE_PRINT
    BAMBOO_DEBUGPRINT_REG(data1);
#endif
    BAMBOO_EXIT(0xe018);
  }
  // all cores should do flush
  if(data1 < NUMCORES4GC) {
    gccorestatus[data1] = 0;
  }
}


INLINE void processmsg_gcfinishflush_I() {
  int data1 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  // received a flush phase finish msg
  if(BAMBOO_NUM_OF_CORE != STARTUPCORE) {
    // non startup core can not receive this msg
#ifndef CLOSE_PRINT
    BAMBOO_DEBUGPRINT_REG(data1);
#endif
    BAMBOO_EXIT(0xe019);
  }
  // all cores should do flush
  if(data1 < NUMCORESACTIVE) {
    gccorestatus[data1] = 0;
  }
}

INLINE void processmsg_gcmarkconfirm_I() {
  if((BAMBOO_NUM_OF_CORE == STARTUPCORE)
     || (BAMBOO_NUM_OF_CORE > NUMCORESACTIVE - 1)) {
    // wrong core to receive such msg
    BAMBOO_EXIT(0xe01a);
  } else {
    // send response msg, cahce the msg first
    if(BAMBOO_CHECK_SEND_MODE()) {
	  cache_msg_5(STARTUPCORE, GCMARKREPORT, BAMBOO_NUM_OF_CORE,
				  gcbusystatus, gcself_numsendobjs,
				  gcself_numreceiveobjs);
    } else {
	  send_msg_5(STARTUPCORE, GCMARKREPORT, BAMBOO_NUM_OF_CORE,
				 gcbusystatus, gcself_numsendobjs,
				 gcself_numreceiveobjs, true);
    }
  }
}

INLINE void processmsg_gcmarkreport_I() {
  int data1 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data2 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data3 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data4 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  // received a marked phase finish confirm response msg
  if(BAMBOO_NUM_OF_CORE != STARTUPCORE) {
    // wrong core to receive such msg
#ifndef CLOSE_PRINT
    BAMBOO_DEBUGPRINT_REG(data2);
#endif
    BAMBOO_EXIT(0xe01b);
  } else {
	int entry_index = 0;
    if(waitconfirm) {
	  // phse 2
      numconfirm--;
	  entry_index = (gcnumsrobjs_index == 0) ? 1 : 0;
    } else {
	  // can never reach here
	  // phase 1
	  entry_index = gcnumsrobjs_index;
	}
    gccorestatus[data1] = data2;
    gcnumsendobjs[entry_index][data1] = data3;
    gcnumreceiveobjs[entry_index][data1] = data4;
  }
}

INLINE void processmsg_gcmarkedobj_I() {
  int data1 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  // received a markedObj msg
  if(((int *)data1)[6] == INIT) {
    // this is the first time that this object is discovered,
    // set the flag as DISCOVERED
    ((int *)data1)[6] = DISCOVERED;
    gc_enqueue_I(data1);
  } 
  // set the remote flag
  ((int *)data1)[6] |= REMOTEM;
  gcself_numreceiveobjs++;
  gcbusystatus = true;
}

INLINE void processmsg_gcmovestart_I() {
  gctomove = true;
  gcdstcore = msgdata[msgdataindex];
  MSG_INDEXINC_I();       //msgdata[1];
  gcmovestartaddr = msgdata[msgdataindex];
  MSG_INDEXINC_I();       //msgdata[2];
  gcblock2fill = msgdata[msgdataindex];
  MSG_INDEXINC_I();       //msgdata[3];
}

INLINE void processmsg_gcmaprequest_I() {
#ifdef GC_PROFILE
  //unsigned long long ttime = BAMBOO_GET_EXE_TIME();
#endif
  void * dstptr = NULL;
  int data1 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
#ifdef GC_PROFILE
  // TODO unsigned long long ttime = BAMBOO_GET_EXE_TIME();
#endif
#ifdef LOCALHASHTBL_TEST
  RuntimeHashget(gcpointertbl, data1, &dstptr);
#else
  dstptr = mgchashSearch(gcpointertbl, data1);
#endif
  //MGCHashget(gcpointertbl, data1, &dstptr);
#ifdef GC_PROFILE
  // TODO flushstalltime += BAMBOO_GET_EXE_TIME() - ttime;
#endif
  int data2 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
#ifdef GC_PROFILE
  // TODO unsigned long long ttimei = BAMBOO_GET_EXE_TIME();
#endif
  if(NULL == dstptr) {
    // no such pointer in this core, something is wrong
#ifdef DEBUG
    BAMBOO_DEBUGPRINT_REG(data1);
    BAMBOO_DEBUGPRINT_REG(data2);
#endif
    BAMBOO_EXIT(0xe01c);
    //assume that the object was not moved, use the original address
    /*if(isMsgSending) {
            cache_msg_3(msgdata[2], GCMAPINFO, msgdata[1], msgdata[1]);
       } else {
            send_msg_3(msgdata[2], GCMAPINFO, msgdata[1], msgdata[1]);
       }*/
  } else {
    // send back the mapping info, cache the msg first
    if(BAMBOO_CHECK_SEND_MODE()) {
	  cache_msg_3(data2, GCMAPINFO, data1, (int)dstptr);
    } else {
	  send_msg_3(data2, GCMAPINFO, data1, (int)dstptr, true);
    }
  }
#ifdef GC_PROFILE
  // TODO flushstalltime_i += BAMBOO_GET_EXE_TIME()-ttimei;
  //num_mapinforequest_i++;
#endif
}

INLINE void processmsg_gcmapinfo_I() {
#ifdef GC_PROFILE
  //unsigned long long ttime = BAMBOO_GET_EXE_TIME();
#endif
  int data1 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  gcmappedobj = msgdata[msgdataindex];  // [2]
  MSG_INDEXINC_I();
#ifdef LOCALHASHTBL_TEST
  RuntimeHashadd_I(gcpointertbl, data1, gcmappedobj);
#else
  mgchashInsert_I(gcpointertbl, data1, gcmappedobj);
#endif
  //MGCHashadd_I(gcpointertbl, data1, gcmappedobj);
  if(data1 == gcobj2map) {
	gcismapped = true;
  }
#ifdef GC_PROFILE
  //flushstalltime += BAMBOO_GET_EXE_TIME() - ttime;
#endif
}

INLINE void processmsg_gcmaptbl_I() {
  int data1 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data2 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  gcrpointertbls[data2] = (mgcsharedhashtbl_t *)data1; //(struct GCSharedHash *)data1;
}

INLINE void processmsg_gclobjinfo_I() {
  numconfirm--;

  int data1 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data2 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  if(BAMBOO_NUM_OF_CORE > NUMCORES4GC - 1) {
#ifndef CLOSE_PRINT
    BAMBOO_DEBUGPRINT_REG(data2);
#endif
    BAMBOO_EXIT(0xe01d);
  }
  // store the mark result info
  int cnum = data2;
  gcloads[cnum] = msgdata[msgdataindex];
  MSG_INDEXINC_I();       // msgdata[3];
  int data4 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  if(gcheaptop < data4) {
    gcheaptop = data4;
  }
  // large obj info here
  for(int k = 5; k < data1; ) {
    int lobj = msgdata[msgdataindex];
    MSG_INDEXINC_I();   //msgdata[k++];
    int length = msgdata[msgdataindex];
    MSG_INDEXINC_I();   //msgdata[k++];
    gc_lobjenqueue_I(lobj, length, cnum);
    gcnumlobjs++;
  }  // for(int k = 5; k < msgdata[1];)
}

INLINE void processmsg_gclobjmapping_I() {
  int data1 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data2 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
#ifdef LOCALHASHTBL_TEST
  RuntimeHashadd_I(gcpointertbl, data1, data2);
#else
  mgchashInsert_I(gcpointertbl, data1, data2);
#endif
  //MGCHashadd_I(gcpointertbl, data1, data2);
  mgcsharedhashInsert_I(gcsharedptbl, data1, data2);
}

#ifdef GC_PROFILE
INLINE void processmsg_gcprofiles_I() {
  int data1 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data2 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data3 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  gc_num_obj += data1;
  gc_num_liveobj += data2;
  gc_num_forwardobj += data3;
  gc_num_profiles--;
}
#endif // GC_PROFILE

#ifdef GC_CACHE_ADAPT
INLINE void processmsg_gcstartpref_I() {
  gcphase = PREFINISHPHASE;
}

INLINE void processmsg_gcfinishpref_I() {
  int data1 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  // received a flush phase finish msg
  if(BAMBOO_NUM_OF_CORE != STARTUPCORE) {
    // non startup core can not receive this msg
#ifndef CLOSE_PRINT
    BAMBOO_DEBUGPRINT_REG(data1);
#endif
    BAMBOO_EXIT(0xe01e);
  }
  // all cores should do flush
  if(data1 < NUMCORESACTIVE) {
    gccorestatus[data1] = 0;
  }
}
#endif // GC_CACHE_ADAPT
#endif // #ifdef MULTICORE_GC

// receive object transferred from other cores
// or the terminate message from other cores
// Should be invoked in critical sections!!
// NOTICE: following format is for threadsimulate version only
//         RAW version please see previous description
// format: type + object
// type: -1--stall msg
//      !-1--object
// return value: 0--received an object
//               1--received nothing
//               2--received a Stall Msg
//               3--received a lock Msg
//               RAW version: -1 -- received nothing
//                            otherwise -- received msg type
int receiveObject(int send_port_pending) {
#ifdef PROFILE_INTERRUPT
  if(!interruptInfoOverflow) {
    InterruptInfo* intInfo = RUNMALLOC_I(sizeof(struct interrupt_info));
    interruptInfoArray[interruptInfoIndex] = intInfo;
    intInfo->startTime = BAMBOO_GET_EXE_TIME();
    intInfo->endTime = -1;
  }
#endif
msg:
  // get the incoming msgs
  if(receiveMsg(send_port_pending) == -1) {
    return -1;
  }
processmsg:
  // processing received msgs
  int size = 0;
  MSG_REMAINSIZE_I(&size);
  if((size == 0) || (checkMsgLength_I(size) == -1)) {
    // not a whole msg
    // have new coming msg
    if((BAMBOO_MSG_AVAIL() != 0) && !msgdatafull) {
      goto msg;
    } else {
      return -1;
    }
  }

  if(msglength <= size) {
    // have some whole msg
    MSGTYPE type;
    type = msgdata[msgdataindex]; //[0]
    MSG_INDEXINC_I();
    msgdatafull = false;
    // TODO
    //tprintf("msg type: %x\n", type);
    switch(type) {
    case TRANSOBJ: {
      // receive a object transfer msg
      processmsg_transobj_I();
      break;
    }   // case TRANSOBJ

    case TRANSTALL: {
      // receive a stall msg
      processmsg_transtall_I();
      break;
    }   // case TRANSTALL

// GC version have no lock msgs
#ifndef MULTICORE_GC
    case LOCKREQUEST: {
      // receive lock request msg, handle it right now
      processmsg_lockrequest_I();
      break;
    }   // case LOCKREQUEST

    case LOCKGROUNT: {
      // receive lock grount msg
      processmsg_lockgrount_I();
      break;
    }   // case LOCKGROUNT

    case LOCKDENY: {
      // receive lock deny msg
      processmsg_lockdeny_I();
      break;
    }   // case LOCKDENY

    case LOCKRELEASE: {
      processmsg_lockrelease_I();
      break;
    }   // case LOCKRELEASE
#endif // #ifndef MULTICORE_GC

#ifdef PROFILE
    case PROFILEOUTPUT: {
      // receive an output profile data request msg
      processmsg_profileoutput_I();
      break;
    }   // case PROFILEOUTPUT

    case PROFILEFINISH: {
      // receive a profile output finish msg
      processmsg_profilefinish_I();
      break;
    }   // case PROFILEFINISH
#endif // #ifdef PROFILE

// GC version has no lock msgs
#ifndef MULTICORE_GC
    case REDIRECTLOCK: {
      // receive a redirect lock request msg, handle it right now
      processmsg_redirectlock_I();
      break;
    }   // case REDIRECTLOCK

    case REDIRECTGROUNT: {
      // receive a lock grant msg with redirect info
      processmsg_redirectgrount_I();
      break;
    }   // case REDIRECTGROUNT

    case REDIRECTDENY: {
      // receive a lock deny msg with redirect info
      processmsg_redirectdeny_I();
      break;
    }   // case REDIRECTDENY

    case REDIRECTRELEASE: {
      // receive a lock release msg with redirect info
      processmsg_redirectrelease_I();
      break;
    }   // case REDIRECTRELEASE
#endif // #ifndef MULTICORE_GC

    case STATUSCONFIRM: {
      // receive a status confirm info
      processmsg_statusconfirm_I();
      break;
    }   // case STATUSCONFIRM

    case STATUSREPORT: {
      processmsg_statusreport_I();
      break;
    }   // case STATUSREPORT

    case TERMINATE: {
      // receive a terminate msg
      processmsg_terminate_I();
      break;
    }   // case TERMINATE

    case MEMREQUEST: {
      processmsg_memrequest_I();
      break;
    }   // case MEMREQUEST

    case MEMRESPONSE: {
      processmsg_memresponse_I();
      break;
    }   // case MEMRESPONSE

#ifdef MULTICORE_GC
    // GC msgs
    case GCSTARTPRE: {
      processmsg_gcstartpre_I();
      break;
    }   // case GCSTARTPRE
	
	case GCSTARTINIT: {
      processmsg_gcstartinit_I();
      break;
    }   // case GCSTARTINIT

    case GCSTART: {
      // receive a start GC msg
      processmsg_gcstart_I();
      break;
    }   // case GCSTART

    case GCSTARTCOMPACT: {
      // a compact phase start msg
      processmsg_gcstartcompact_I();
      break;
    }   // case GCSTARTCOMPACT

	case GCSTARTMAPINFO: {
      // received a flush phase start msg
      processmsg_gcstartmapinfo_I();
      break;
    }   // case GCSTARTFLUSH

    case GCSTARTFLUSH: {
      // received a flush phase start msg
      processmsg_gcstartflush_I();
      break;
    }   // case GCSTARTFLUSH

    case GCFINISHPRE: {
      processmsg_gcfinishpre_I();
      break;
    }   // case GCFINISHPRE
	
	case GCFINISHINIT: {
      processmsg_gcfinishinit_I();
      break;
    }   // case GCFINISHINIT

    case GCFINISHMARK: {
      processmsg_gcfinishmark_I();
      break;
    }   // case GCFINISHMARK

    case GCFINISHCOMPACT: {
      // received a compact phase finish msg
      processmsg_gcfinishcompact_I();
      break;
    }   // case GCFINISHCOMPACT

	case GCFINISHMAPINFO: {
      processmsg_gcfinishmapinfo_I();
      break;
    }   // case GCFINISHMAPINFO

    case GCFINISHFLUSH: {
      processmsg_gcfinishflush_I();
      break;
    }   // case GCFINISHFLUSH

    case GCFINISH: {
      // received a GC finish msg
      gcphase = FINISHPHASE;
      break;
    }   // case GCFINISH

    case GCMARKCONFIRM: {
      // received a marked phase finish confirm request msg
      // all cores should do mark
      processmsg_gcmarkconfirm_I();
      break;
    }   // case GCMARKCONFIRM

    case GCMARKREPORT: {
      processmsg_gcmarkreport_I();
      break;
    }   // case GCMARKREPORT

    case GCMARKEDOBJ: {
      processmsg_gcmarkedobj_I();
      break;
    }   // case GCMARKEDOBJ

    case GCMOVESTART: {
      // received a start moving objs msg
      processmsg_gcmovestart_I();
      break;
    }   // case GCMOVESTART

    case GCMAPREQUEST: {
      // received a mapping info request msg
      processmsg_gcmaprequest_I();
      break;
    }   // case GCMAPREQUEST

    case GCMAPINFO: {
      // received a mapping info response msg
      processmsg_gcmapinfo_I();
      break;
    }   // case GCMAPINFO

    case GCMAPTBL: {
      // received a mapping tbl response msg
      processmsg_gcmaptbl_I();
      break;
    }   // case GCMAPTBL
	
	case GCLOBJREQUEST: {
      // received a large objs info request msg
      transferMarkResults_I();
      break;
    }   // case GCLOBJREQUEST

    case GCLOBJINFO: {
      // received a large objs info response msg
      processmsg_gclobjinfo_I();
      break;
    }   // case GCLOBJINFO

    case GCLOBJMAPPING: {
      // received a large obj mapping info msg
      processmsg_gclobjmapping_I();
      break;
    }  // case GCLOBJMAPPING

#ifdef GC_PROFILE
	case GCPROFILES: {
      // received a gcprofiles msg
      processmsg_gcprofiles_I();
      break;
    }
#endif // GC_PROFILE

#ifdef GC_CACHE_ADAPT
	case GCSTARTPREF: {
      // received a gcstartpref msg
      processmsg_gcstartpref_I();
      break;
    }

	case GCFINISHPREF: {
      // received a gcfinishpref msg
      processmsg_gcfinishpref_I();
      break;
    }
#endif // GC_CACHE_ADAPT
#endif // #ifdef MULTICORE_GC

    default:
      break;
    }  // switch(type)
    msglength = BAMBOO_MSG_BUF_LENGTH;
    // TODO
    //printf("++ msg: %x \n", type);

    if(msgdataindex != msgdatalast) {
      // still have available msg
      goto processmsg;
    }
#ifdef DEBUG
#ifndef CLOSE_PRINT
    BAMBOO_DEBUGPRINT(0xe88d);
#endif
#endif

    // have new coming msg
    if(BAMBOO_MSG_AVAIL() != 0) {
      goto msg;
    } // TODO

#ifdef PROFILE_INTERRUPT
  if(!interruptInfoOverflow) {
    interruptInfoArray[interruptInfoIndex]->endTime=BAMBOO_GET_EXE_TIME();
    interruptInfoIndex++;
    if(interruptInfoIndex == INTERRUPTINFOLENGTH) {
      interruptInfoOverflow = true;
    }
  }
#endif
    return (int)type;
  } else {
    // not a whole msg
#ifdef DEBUG
#ifndef CLOSE_PRINT
    BAMBOO_DEBUGPRINT(0xe88e);
#endif
#endif
    return -2;
  }
}

int enqueuetasks(struct parameterwrapper *parameter,
                 struct parameterwrapper *prevptr,
                 struct ___Object___ *ptr,
                 int * enterflags,
                 int numenterflags) {
  void * taskpointerarray[MAXTASKPARAMS];
  int j;
  //int numparams=parameter->task->numParameters;
  int numiterators=parameter->task->numTotal-1;
  int retval=1;

  struct taskdescriptor * task=parameter->task;

  //this add the object to parameterwrapper
  ObjectHashadd(parameter->objectset, (int) ptr, 0, (int) enterflags,
                numenterflags, enterflags==NULL);

  /* Add enqueued object to parameter vector */
  taskpointerarray[parameter->slot]=ptr;

  /* Reset iterators */
  for(j=0; j<numiterators; j++) {
    toiReset(&parameter->iterators[j]);
  }

  /* Find initial state */
  for(j=0; j<numiterators; j++) {
backtrackinit:
    if(toiHasNext(&parameter->iterators[j],taskpointerarray OPTARG(failed)))
      toiNext(&parameter->iterators[j], taskpointerarray OPTARG(failed));
    else if (j>0) {
      /* Need to backtrack */
      toiReset(&parameter->iterators[j]);
      j--;
      goto backtrackinit;
    } else {
      /* Nothing to enqueue */
      return retval;
    }
  }

  while(1) {
    /* Enqueue current state */
    //int launch = 0;
    struct taskparamdescriptor *tpd=
      RUNMALLOC(sizeof(struct taskparamdescriptor));
    tpd->task=task;
    tpd->numParameters=numiterators+1;
    tpd->parameterArray=RUNMALLOC(sizeof(void *)*(numiterators+1));

    for(j=0; j<=numiterators; j++) {
      //store the actual parameters
      tpd->parameterArray[j]=taskpointerarray[j];
    }
    /* Enqueue task */
    if (( /*!gencontains(failedtasks, tpd)&&*/
          !gencontains(activetasks,tpd))) {
      genputtable(activetasks, tpd, tpd);
    } else {
      RUNFREE(tpd->parameterArray);
      RUNFREE(tpd);
    }

    /* This loop iterates to the next parameter combination */
    if (numiterators==0)
      return retval;

    for(j=numiterators-1; j<numiterators; j++) {
backtrackinc:
      if(toiHasNext(
			&parameter->iterators[j],taskpointerarray OPTARG(failed)))
		toiNext(&parameter->iterators[j], taskpointerarray OPTARG(failed));
      else if (j>0) {
		/* Need to backtrack */
		toiReset(&parameter->iterators[j]);
		j--;
		goto backtrackinc;
      } else {
		/* Nothing more to enqueue */
		return retval;
      }
    }
  }
  return retval;
}

int enqueuetasks_I(struct parameterwrapper *parameter,
                   struct parameterwrapper *prevptr,
                   struct ___Object___ *ptr,
                   int * enterflags,
                   int numenterflags) {
  void * taskpointerarray[MAXTASKPARAMS];
  int j;
  //int numparams=parameter->task->numParameters;
  int numiterators=parameter->task->numTotal-1;
  int retval=1;
  //int addnormal=1;
  //int adderror=1;

  struct taskdescriptor * task=parameter->task;

  //this add the object to parameterwrapper
  ObjectHashadd_I(parameter->objectset, (int) ptr, 0, (int) enterflags,
                  numenterflags, enterflags==NULL);

  /* Add enqueued object to parameter vector */
  taskpointerarray[parameter->slot]=ptr;

  /* Reset iterators */
  for(j=0; j<numiterators; j++) {
    toiReset(&parameter->iterators[j]);
  }

  /* Find initial state */
  for(j=0; j<numiterators; j++) {
backtrackinit:
    if(toiHasNext(&parameter->iterators[j],taskpointerarray OPTARG(failed)))
      toiNext(&parameter->iterators[j], taskpointerarray OPTARG(failed));
    else if (j>0) {
      /* Need to backtrack */
      toiReset(&parameter->iterators[j]);
      j--;
      goto backtrackinit;
    } else {
      /* Nothing to enqueue */
      return retval;
    }
  }

  while(1) {
    /* Enqueue current state */
    //int launch = 0;
    struct taskparamdescriptor *tpd=
      RUNMALLOC_I(sizeof(struct taskparamdescriptor));
    tpd->task=task;
    tpd->numParameters=numiterators+1;
    tpd->parameterArray=RUNMALLOC_I(sizeof(void *)*(numiterators+1));

    for(j=0; j<=numiterators; j++) {
      //store the actual parameters
      tpd->parameterArray[j]=taskpointerarray[j];
    }
    /* Enqueue task */
    if (( /*!gencontains(failedtasks, tpd)&&*/
          !gencontains(activetasks,tpd))) {
      genputtable_I(activetasks, tpd, tpd);
    } else {
      RUNFREE(tpd->parameterArray);
      RUNFREE(tpd);
    }

    /* This loop iterates to the next parameter combination */
    if (numiterators==0)
      return retval;

    for(j=numiterators-1; j<numiterators; j++) {
backtrackinc:
      if(toiHasNext(
			&parameter->iterators[j], taskpointerarray OPTARG(failed)))
		toiNext(&parameter->iterators[j], taskpointerarray OPTARG(failed));
      else if (j>0) {
		/* Need to backtrack */
		toiReset(&parameter->iterators[j]);
		j--;
		goto backtrackinc;
      } else {
		/* Nothing more to enqueue */
		return retval;
      }
    }
  }
  return retval;
}

#ifdef MULTICORE_GC
#define OFFSET 2
#else
#define OFFSET 0
#endif

int containstag(struct ___Object___ *ptr,
                struct ___TagDescriptor___ *tag);

#ifndef MULTICORE_GC
void releasewritelock_r(void * lock, void * redirectlock) {
  int targetcore = 0;
  int reallock = (int)lock;
  targetcore = (reallock >> 5) % NUMCORES;

#ifdef DEBUG
  BAMBOO_DEBUGPRINT(0xe671);
  BAMBOO_DEBUGPRINT_REG((int)lock);
  BAMBOO_DEBUGPRINT_REG(reallock);
  BAMBOO_DEBUGPRINT_REG(targetcore);
#endif

  if(targetcore == BAMBOO_NUM_OF_CORE) {
    BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
#ifdef DEBUG
    BAMBOO_DEBUGPRINT(0xf001);
#endif
    // reside on this core
    if(!RuntimeHashcontainskey(locktbl, reallock)) {
      // no locks for this object, something is wrong
      BAMBOO_EXIT(0xe01f);
    } else {
      int rwlock_obj = 0;
      struct LockValue * lockvalue = NULL;
#ifdef DEBUG
      BAMBOO_DEBUGPRINT(0xe672);
#endif
      RuntimeHashget(locktbl, reallock, &rwlock_obj);
      lockvalue = (struct LockValue *)rwlock_obj;
#ifdef DEBUG
      BAMBOO_DEBUGPRINT_REG(lockvalue->value);
#endif
      lockvalue->value++;
      lockvalue->redirectlock = (int)redirectlock;
#ifdef DEBUG
      BAMBOO_DEBUGPRINT_REG(lockvalue->value);
#endif
    }
    BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
#ifdef DEBUG
    BAMBOO_DEBUGPRINT(0xf000);
#endif
    return;
  } else {
    // send lock release with redirect info msg
    // for 32 bit machine, the size is always 4 words
    send_msg_4(targetcore, REDIRECTRELEASE, 1, (int)lock,
               (int)redirectlock, false);
  }
}
#endif

void executetasks() {
  void * taskpointerarray[MAXTASKPARAMS+OFFSET];
  int numparams=0;
  int numtotal=0;
  struct ___Object___ * tmpparam = NULL;
  struct parameterdescriptor * pd=NULL;
  struct parameterwrapper *pw=NULL;
  int j = 0;
  int x = 0;
  bool islock = true;

  int grount = 0;
  int andmask=0;
  int checkmask=0;

newtask:
  while(hashsize(activetasks)>0) {
#ifdef MULTICORE_GC
    if(gcflag) gc(NULL);
#endif
#ifdef DEBUG
    BAMBOO_DEBUGPRINT(0xe990);
#endif

    /* See if there are any active tasks */
    //if (hashsize(activetasks)>0) {
    int i;
#ifdef PROFILE
#ifdef ACCURATEPROFILE
    profileTaskStart("tpd checking");
#endif
#endif
    //long clock1;
    //clock1 = BAMBOO_GET_EXE_TIME();

    busystatus = true;
    currtpd=(struct taskparamdescriptor *) getfirstkey(activetasks);
    genfreekey(activetasks, currtpd);

    numparams=currtpd->task->numParameters;
    numtotal=currtpd->task->numTotal;

    // clear the lockRedirectTbl
    // (TODO, this table should be empty after all locks are released)
    // reset all locks
    /*for(j = 0; j < MAXTASKPARAMS; j++) {
            runtime_locks[j].redirectlock = 0;
            runtime_locks[j].value = 0;
       }*/
    // get all required locks
    runtime_locklen = 0;
    // check which locks are needed
    for(i = 0; i < numparams; i++) {
      void * param = currtpd->parameterArray[i];
      int tmplock = 0;
      int j = 0;
      bool insert = true;
      if(((struct ___Object___ *)param)->type == STARTUPTYPE) {
		islock = false;
		taskpointerarray[i+OFFSET]=param;
		goto execute;
      }
      if(((struct ___Object___ *)param)->lock == NULL) {
		tmplock = (int)param;
      } else {
		tmplock = (int)(((struct ___Object___ *)param)->lock);
      }
      // insert into the locks array
      for(j = 0; j < runtime_locklen; j++) {
		if(runtime_locks[j].value == tmplock) {
		  insert = false;
		  break;
		} else if(runtime_locks[j].value > tmplock) {
		  break;
		}
      }
      if(insert) {
		int h = runtime_locklen;
		for(; h > j; h--) {
		  runtime_locks[h].redirectlock = runtime_locks[h-1].redirectlock;
		  runtime_locks[h].value = runtime_locks[h-1].value;
		}
		runtime_locks[j].value = tmplock;
		runtime_locks[j].redirectlock = (int)param;
		runtime_locklen++;
      }
    }  // line 2713: for(i = 0; i < numparams; i++)
       // grab these required locks
#ifdef DEBUG
    BAMBOO_DEBUGPRINT(0xe991);
#endif
    //long clock2;
    //clock2 = BAMBOO_GET_EXE_TIME();

    for(i = 0; i < runtime_locklen; i++) {
      int * lock = (int *)(runtime_locks[i].redirectlock);
      islock = true;
      // require locks for this parameter if it is not a startup object
#ifdef DEBUG
      BAMBOO_DEBUGPRINT_REG((int)lock);
      BAMBOO_DEBUGPRINT_REG((int)(runtime_locks[i].value));
#endif
      getwritelock(lock);
      BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
#ifdef DEBUG
      BAMBOO_DEBUGPRINT(0xf001);
#endif
#ifdef PROFILE
      //isInterrupt = false;
#endif
      while(!lockflag) {
		BAMBOO_WAITING_FOR_LOCK(0);
	  }
#ifndef INTERRUPT
      if(reside) {
		while(BAMBOO_WAITING_FOR_LOCK(0) != -1) {
		}
      }
#endif
      grount = lockresult;

      lockresult = 0;
      lockobj = 0;
      lock2require = 0;
      lockflag = false;
#ifndef INTERRUPT
      reside = false;
#endif
#ifdef PROFILE
      //isInterrupt = true;
#endif
      BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
#ifdef DEBUG
      BAMBOO_DEBUGPRINT(0xf000);
#endif

      if(grount == 0) {
#ifdef DEBUG
		BAMBOO_DEBUGPRINT(0xe992);
		BAMBOO_DEBUGPRINT_REG(lock);
#endif
		// check if has the lock already
		// can not get the lock, try later
		// release all grabbed locks for previous parameters
		for(j = 0; j < i; ++j) {
		  lock = (int*)(runtime_locks[j].redirectlock);
		  releasewritelock(lock);
		}
		genputtable(activetasks, currtpd, currtpd);
		if(hashsize(activetasks) == 1) {
		  // only one task right now, wait a little while before next try
		  int halt = 10000;
		  while(halt--) {
		  }
		}
#ifdef PROFILE
#ifdef ACCURATEPROFILE
		// fail, set the end of the checkTaskInfo
		profileTaskEnd();
#endif
#endif
		goto newtask;
	//}
      }
    }   // line 2752:  for(i = 0; i < runtime_locklen; i++)

    /*long clock3;
       clock3 = BAMBOO_GET_EXE_TIME();
       //tprintf("sort: %d, grab: %d \n", clock2-clock1, clock3-clock2);*/

#ifdef DEBUG
    BAMBOO_DEBUGPRINT(0xe993);
#endif
    /* Make sure that the parameters are still in the queues */
    for(i=0; i<numparams; i++) {
      void * parameter=currtpd->parameterArray[i];

      // flush the object
#ifdef CACHEFLUSH
      BAMBOO_CACHE_FLUSH_RANGE((int)parameter,
		  classsize[((struct ___Object___ *)parameter)->type]);
#endif
      tmpparam = (struct ___Object___ *)parameter;
      pd=currtpd->task->descriptorarray[i];
      pw=(struct parameterwrapper *) pd->queue;
      /* Check that object is still in queue */
      {
		if (!ObjectHashcontainskey(pw->objectset, (int) parameter)) {
#ifdef DEBUG
		  BAMBOO_DEBUGPRINT(0xe994);
		  BAMBOO_DEBUGPRINT_REG(parameter);
#endif
		  // release grabbed locks
		  for(j = 0; j < runtime_locklen; ++j) {
			int * lock = (int *)(runtime_locks[j].redirectlock);
			releasewritelock(lock);
		  }
		  RUNFREE(currtpd->parameterArray);
		  RUNFREE(currtpd);
		  currtpd = NULL;
		  goto newtask;
		}
      }   // line2865
          /* Check if the object's flags still meets requirements */
      {
		int tmpi = 0;
		bool ismet = false;
		for(tmpi = 0; tmpi < pw->numberofterms; ++tmpi) {
		  andmask=pw->intarray[tmpi*2];
		  checkmask=pw->intarray[tmpi*2+1];
		  if((((struct ___Object___ *)parameter)->flag&andmask)==checkmask) {
			ismet = true;
			break;
		  }
		}
		if (!ismet) {
		  // flags are never suitable
		  // remove this obj from the queue
		  int next;
		  int UNUSED, UNUSED2;
		  int * enterflags;
#ifdef DEBUG
		  BAMBOO_DEBUGPRINT(0xe995);
		  BAMBOO_DEBUGPRINT_REG(parameter);
#endif
		  ObjectHashget(pw->objectset, (int) parameter, (int *) &next,
						(int *) &enterflags, &UNUSED, &UNUSED2);
		  ObjectHashremove(pw->objectset, (int)parameter);
		  if (enterflags!=NULL)
			RUNFREE(enterflags);
		  // release grabbed locks
		  for(j = 0; j < runtime_locklen; ++j) {
			int * lock = (int *)(runtime_locks[j].redirectlock);
			releasewritelock(lock);
		  }
		  RUNFREE(currtpd->parameterArray);
		  RUNFREE(currtpd);
		  currtpd = NULL;
#ifdef PROFILE
#ifdef ACCURATEPROFILE
		  // fail, set the end of the checkTaskInfo
		  profileTaskEnd();
#endif
#endif
		  goto newtask;
		}   // line 2878: if (!ismet)
      }   // line 2867
parameterpresent:
      ;
      /* Check that object still has necessary tags */
      for(j=0; j<pd->numbertags; j++) {
		int slotid=pd->tagarray[2*j]+numparams;
		struct ___TagDescriptor___ *tagd=currtpd->parameterArray[slotid];
		if (!containstag(parameter, tagd)) {
#ifdef DEBUG
		  BAMBOO_DEBUGPRINT(0xe996);
#endif
		  {
			// release grabbed locks
			int tmpj = 0;
			for(tmpj = 0; tmpj < runtime_locklen; ++tmpj) {
			  int * lock = (int *)(runtime_locks[tmpj].redirectlock);
			  releasewritelock(lock);
			}
		  }
		  RUNFREE(currtpd->parameterArray);
		  RUNFREE(currtpd);
		  currtpd = NULL;
		  goto newtask;
		}   // line2911: if (!containstag(parameter, tagd))
      }   // line 2808: for(j=0; j<pd->numbertags; j++)

      taskpointerarray[i+OFFSET]=parameter;
    }   // line 2824: for(i=0; i<numparams; i++)
        /* Copy the tags */
    for(; i<numtotal; i++) {
      taskpointerarray[i+OFFSET]=currtpd->parameterArray[i];
    }

    {
execute:
      /* Actually call task */
#ifdef MULTICORE_GC
      ((int *)taskpointerarray)[0]=currtpd->numParameters;
      taskpointerarray[1]=NULL;
#endif
#ifdef PROFILE
#ifdef ACCURATEPROFILE
      // check finish, set the end of the checkTaskInfo
      profileTaskEnd();
#endif
      profileTaskStart(currtpd->task->name);
#endif
      // TODO
      //long clock4;
      //clock4 = BAMBOO_GET_EXE_TIME();
      //tprintf("sort: %d, grab: %d, check: %d \n", (int)(clock2-clock1), (int)(clock3-clock2), (int)(clock4-clock3));

#ifdef DEBUG
      BAMBOO_DEBUGPRINT(0xe997);
#endif
      ((void (*)(void **))currtpd->task->taskptr)(taskpointerarray);
      // TODO
      //long clock5;
      //clock5 = BAMBOO_GET_EXE_TIME();
      // tprintf("sort: %d, grab: %d, check: %d \n", (int)(clock2-clock1), (int)(clock3-clock2), (int)(clock4-clock3));

#ifdef PROFILE
#ifdef ACCURATEPROFILE
      // task finish, set the end of the checkTaskInfo
      profileTaskEnd();
      // new a PostTaskInfo for the post-task execution
      profileTaskStart("post task execution");
#endif
#endif
#ifdef DEBUG
      BAMBOO_DEBUGPRINT(0xe998);
      BAMBOO_DEBUGPRINT_REG(islock);
#endif

      if(islock) {
#ifdef DEBUG
		BAMBOO_DEBUGPRINT(0xe999);
#endif
		for(i = 0; i < runtime_locklen; ++i) {
		  void * ptr = (void *)(runtime_locks[i].redirectlock);
		  int * lock = (int *)(runtime_locks[i].value);
#ifdef DEBUG
		  BAMBOO_DEBUGPRINT_REG((int)ptr);
		  BAMBOO_DEBUGPRINT_REG((int)lock);
		  BAMBOO_DEBUGPRINT_REG(*((int*)lock+5));
#endif
#ifndef MULTICORE_GC
		  if(RuntimeHashcontainskey(lockRedirectTbl, (int)lock)) {
			int redirectlock;
			RuntimeHashget(lockRedirectTbl, (int)lock, &redirectlock);
			RuntimeHashremovekey(lockRedirectTbl, (int)lock);
			releasewritelock_r(lock, (int *)redirectlock);
		  } else {
#else
		  {
#endif
			releasewritelock(ptr);
		  }
		}
      }     // line 3015: if(islock)

      //long clock6;
      //clock6 = BAMBOO_GET_EXE_TIME();
      //tprintf("sort: %d, grab: %d, check: %d \n", (int)(clock2-clock1), (int)(clock3-clock2), (int)(clock4-clock3));

#ifdef PROFILE
      // post task execution finish, set the end of the postTaskInfo
      profileTaskEnd();
#endif

      // Free up task parameter descriptor
      RUNFREE(currtpd->parameterArray);
      RUNFREE(currtpd);
      currtpd = NULL;
#ifdef DEBUG
      BAMBOO_DEBUGPRINT(0xe99a);
#endif
      //long clock7;
      //clock7 = BAMBOO_GET_EXE_TIME();
      //tprintf("sort: %d, grab: %d, check: %d, release: %d, other %d \n", (int)(clock2-clock1), (int)(clock3-clock2), (int)(clock4-clock3), (int)(clock6-clock5), (int)(clock7-clock6));

    }   //
    //} //  if (hashsize(activetasks)>0)
  } //  while(hashsize(activetasks)>0)
#ifdef DEBUG
  BAMBOO_DEBUGPRINT(0xe99b);
#endif
}

/* This function processes an objects tags */
void processtags(struct parameterdescriptor *pd,
                 int index,
                 struct parameterwrapper *parameter,
                 int * iteratorcount,
                 int *statusarray,
                 int numparams) {
  int i;

  for(i=0; i<pd->numbertags; i++) {
    int slotid=pd->tagarray[2*i];
    int tagid=pd->tagarray[2*i+1];

    if (statusarray[slotid+numparams]==0) {
      parameter->iterators[*iteratorcount].istag=1;
      parameter->iterators[*iteratorcount].tagid=tagid;
      parameter->iterators[*iteratorcount].slot=slotid+numparams;
      parameter->iterators[*iteratorcount].tagobjectslot=index;
      statusarray[slotid+numparams]=1;
      (*iteratorcount)++;
    }
  }
}


void processobject(struct parameterwrapper *parameter,
                   int index,
                   struct parameterdescriptor *pd,
                   int *iteratorcount,
                   int * statusarray,
                   int numparams) {
  int i;
  int tagcount=0;
  struct ObjectHash * objectset=
    ((struct parameterwrapper *)pd->queue)->objectset;

  parameter->iterators[*iteratorcount].istag=0;
  parameter->iterators[*iteratorcount].slot=index;
  parameter->iterators[*iteratorcount].objectset=objectset;
  statusarray[index]=1;

  for(i=0; i<pd->numbertags; i++) {
    int slotid=pd->tagarray[2*i];
    //int tagid=pd->tagarray[2*i+1];
    if (statusarray[slotid+numparams]!=0) {
      /* This tag has already been enqueued, use it to narrow search */
      parameter->iterators[*iteratorcount].tagbindings[tagcount]=
        slotid+numparams;
      tagcount++;
    }
  }
  parameter->iterators[*iteratorcount].numtags=tagcount;

  (*iteratorcount)++;
}

/* This function builds the iterators for a task & parameter */

void builditerators(struct taskdescriptor * task,
                    int index,
                    struct parameterwrapper * parameter) {
  int statusarray[MAXTASKPARAMS];
  int i;
  int numparams=task->numParameters;
  int iteratorcount=0;
  for(i=0; i<MAXTASKPARAMS; i++) statusarray[i]=0;

  statusarray[index]=1; /* Initial parameter */
  /* Process tags for initial iterator */

  processtags(task->descriptorarray[index], index, parameter,
              &iteratorcount, statusarray, numparams);

  while(1) {
loopstart:
    /* Check for objects with existing tags */
    for(i=0; i<numparams; i++) {
      if (statusarray[i]==0) {
		struct parameterdescriptor *pd=task->descriptorarray[i];
		int j;
		for(j=0; j<pd->numbertags; j++) {
		  int slotid=pd->tagarray[2*j];
		  if(statusarray[slotid+numparams]!=0) {
			processobject(parameter,i,pd,&iteratorcount,
				statusarray,numparams);
			processtags(pd,i,parameter,&iteratorcount,statusarray,numparams);
			goto loopstart;
		  }
		}
      }
    }

    /* Next do objects w/ unbound tags*/

    for(i=0; i<numparams; i++) {
      if (statusarray[i]==0) {
		struct parameterdescriptor *pd=task->descriptorarray[i];
		if (pd->numbertags>0) {
		  processobject(parameter,i,pd,&iteratorcount,statusarray,numparams);
		  processtags(pd,i,parameter,&iteratorcount,statusarray,numparams);
		  goto loopstart;
		}
      }
    }

    /* Nothing with a tag enqueued */

    for(i=0; i<numparams; i++) {
      if (statusarray[i]==0) {
		struct parameterdescriptor *pd=task->descriptorarray[i];
		processobject(parameter,i,pd,&iteratorcount,statusarray,numparams);
		processtags(pd,i,parameter,&iteratorcount,statusarray,numparams);
		goto loopstart;
      }
    }

    /* Nothing left */
    return;
  }
}

void printdebug() {
  int i;
  int j;
  if(BAMBOO_NUM_OF_CORE > NUMCORESACTIVE - 1) {
    return;
  }
  for(i=0; i<numtasks[BAMBOO_NUM_OF_CORE]; i++) {
    struct taskdescriptor * task=taskarray[BAMBOO_NUM_OF_CORE][i];
#ifndef RAW
    printf("%s\n", task->name);
#endif
    for(j=0; j<task->numParameters; j++) {
      struct parameterdescriptor *param=task->descriptorarray[j];
      struct parameterwrapper *parameter=param->queue;
      struct ObjectHash * set=parameter->objectset;
      struct ObjectIterator objit;
#ifndef RAW
      printf("  Parameter %d\n", j);
#endif
      ObjectHashiterator(set, &objit);
      while(ObjhasNext(&objit)) {
		struct ___Object___ * obj=(struct ___Object___ *)Objkey(&objit);
		struct ___Object___ * tagptr=obj->___tags___;
		int nonfailed=Objdata4(&objit);
		int numflags=Objdata3(&objit);
		int flags=Objdata2(&objit);
		Objnext(&objit);
#ifndef RAW
		printf("    Contains %lx\n", obj);
		printf("      flag=%d\n", obj->flag);
#endif
		if (tagptr==NULL) {
		} else if (tagptr->type==TAGTYPE) {
#ifndef RAW
		  printf("      tag=%lx\n",tagptr);
#else
		  ;
#endif
		} else {
		  int tagindex=0;
		  struct ArrayObject *ao=(struct ArrayObject *)tagptr;
		  for(; tagindex<ao->___cachedCode___; tagindex++) {
#ifndef RAW
			printf("      tag=%lx\n",ARRAYGET(ao,struct ___TagDescriptor___*,
											  tagindex));
#else
			;
#endif
		  }
		}
      }
    }
  }
}


/* This function processes the task information to create queues for
   each parameter type. */

void processtasks() {
  int i;
  if(BAMBOO_NUM_OF_CORE > NUMCORESACTIVE - 1) {
    return;
  }
  for(i=0; i<numtasks[BAMBOO_NUM_OF_CORE]; i++) {
    struct taskdescriptor * task=taskarray[BAMBOO_NUM_OF_CORE][i];
    int j;

    /* Build objectsets */
    for(j=0; j<task->numParameters; j++) {
      struct parameterdescriptor *param=task->descriptorarray[j];
      struct parameterwrapper *parameter=param->queue;
      parameter->objectset=allocateObjectHash(10);
      parameter->task=task;
    }

    /* Build iterators for parameters */
    for(j=0; j<task->numParameters; j++) {
      struct parameterdescriptor *param=task->descriptorarray[j];
      struct parameterwrapper *parameter=param->queue;
      builditerators(task, j, parameter);
    }
  }
}

void toiReset(struct tagobjectiterator * it) {
  if (it->istag) {
    it->tagobjindex=0;
  } else if (it->numtags>0) {
    it->tagobjindex=0;
  } else {
    ObjectHashiterator(it->objectset, &it->it);
  }
}

int toiHasNext(struct tagobjectiterator *it,
               void ** objectarray OPTARG(int * failed)) {
  if (it->istag) {
    /* Iterate tag */
    /* Get object with tags */
    struct ___Object___ *obj=objectarray[it->tagobjectslot];
    struct ___Object___ *tagptr=obj->___tags___;
    if (tagptr->type==TAGTYPE) {
      if ((it->tagobjindex==0)&& /* First object */
		  (it->tagid==((struct ___TagDescriptor___ *)tagptr)->flag)) /* Right tag type */
		return 1;
	  else
		return 0;
    } else {
      struct ArrayObject *ao=(struct ArrayObject *) tagptr;
      int tagindex=it->tagobjindex;
      for(; tagindex<ao->___cachedCode___; tagindex++) {
		struct ___TagDescriptor___ *td=
		  ARRAYGET(ao, struct ___TagDescriptor___ *, tagindex);
		if (td->flag==it->tagid) {
		  it->tagobjindex=tagindex; /* Found right type of tag */
		  return 1;
		}
      }
      return 0;
    }
  } else if (it->numtags>0) {
    /* Use tags to locate appropriate objects */
    struct ___TagDescriptor___ *tag=objectarray[it->tagbindings[0]];
    struct ___Object___ *objptr=tag->flagptr;
    int i;
    if (objptr->type!=OBJECTARRAYTYPE) {
      if (it->tagobjindex>0)
		return 0;
      if (!ObjectHashcontainskey(it->objectset, (int) objptr))
		return 0;
      for(i=1; i<it->numtags; i++) {
		struct ___TagDescriptor___ *tag2=objectarray[it->tagbindings[i]];
		if (!containstag(objptr,tag2))
		  return 0;
      }
      return 1;
    } else {
      struct ArrayObject *ao=(struct ArrayObject *) objptr;
      int tagindex;
      int i;
      for(tagindex=it->tagobjindex;tagindex<ao->___cachedCode___;tagindex++){
		struct ___Object___ *objptr=
		  ARRAYGET(ao,struct ___Object___*,tagindex);
		if (!ObjectHashcontainskey(it->objectset, (int) objptr))
		  continue;
		for(i=1; i<it->numtags; i++) {
		  struct ___TagDescriptor___ *tag2=objectarray[it->tagbindings[i]];
		  if (!containstag(objptr,tag2))
			goto nexttag;
		}
		it->tagobjindex=tagindex;
		return 1;
nexttag:
		;
	  }
      it->tagobjindex=tagindex;
      return 0;
    }
  } else {
    return ObjhasNext(&it->it);
  }
}

int containstag(struct ___Object___ *ptr,
                struct ___TagDescriptor___ *tag) {
  int j;
  struct ___Object___ * objptr=tag->flagptr;
  if (objptr->type==OBJECTARRAYTYPE) {
    struct ArrayObject *ao=(struct ArrayObject *)objptr;
    for(j=0; j<ao->___cachedCode___; j++) {
      if (ptr==ARRAYGET(ao, struct ___Object___*, j)) {
		return 1;
      }
    }
    return 0;
  } else {
    return objptr==ptr;
  }
}

void toiNext(struct tagobjectiterator *it,
             void ** objectarray OPTARG(int * failed)) {
  /* hasNext has all of the intelligence */
  if(it->istag) {
    /* Iterate tag */
    /* Get object with tags */
    struct ___Object___ *obj=objectarray[it->tagobjectslot];
    struct ___Object___ *tagptr=obj->___tags___;
    if (tagptr->type==TAGTYPE) {
      it->tagobjindex++;
      objectarray[it->slot]=tagptr;
    } else {
      struct ArrayObject *ao=(struct ArrayObject *) tagptr;
      objectarray[it->slot]=
        ARRAYGET(ao, struct ___TagDescriptor___ *, it->tagobjindex++);
    }
  } else if (it->numtags>0) {
    /* Use tags to locate appropriate objects */
    struct ___TagDescriptor___ *tag=objectarray[it->tagbindings[0]];
    struct ___Object___ *objptr=tag->flagptr;
    if (objptr->type!=OBJECTARRAYTYPE) {
      it->tagobjindex++;
      objectarray[it->slot]=objptr;
    } else {
      struct ArrayObject *ao=(struct ArrayObject *) objptr;
      objectarray[it->slot]=
        ARRAYGET(ao, struct ___Object___ *, it->tagobjindex++);
    }
  } else {
    /* Iterate object */
    objectarray[it->slot]=(void *)Objkey(&it->it);
    Objnext(&it->it);
  }
}

#ifdef PROFILE
inline void profileTaskStart(char * taskname) {
  if(!taskInfoOverflow) {
    TaskInfo* taskInfo = RUNMALLOC(sizeof(struct task_info));
    taskInfoArray[taskInfoIndex] = taskInfo;
    taskInfo->taskName = taskname;
    taskInfo->startTime = BAMBOO_GET_EXE_TIME();
    taskInfo->endTime = -1;
    taskInfo->exitIndex = -1;
    taskInfo->newObjs = NULL;
  }
}

inline void profileTaskEnd() {
  if(!taskInfoOverflow) {
    taskInfoArray[taskInfoIndex]->endTime = BAMBOO_GET_EXE_TIME();
    taskInfoIndex++;
    if(taskInfoIndex == TASKINFOLENGTH) {
      taskInfoOverflow = true;
      //taskInfoIndex = 0;
    }
  }
}

// output the profiling data
void outputProfileData() {
#ifdef USEIO
  int i;
  unsigned long long totaltasktime = 0;
  unsigned long long preprocessingtime = 0;
  unsigned long long objqueuecheckingtime = 0;
  unsigned long long postprocessingtime = 0;
  //int interruptiontime = 0;
  unsigned long long other = 0;
  unsigned long long averagetasktime = 0;
  int tasknum = 0;

  printf("Task Name, Start Time, End Time, Duration, Exit Index(, NewObj Name, Num)+\n");
  // output task related info
  for(i = 0; i < taskInfoIndex; i++) {
    TaskInfo* tmpTInfo = taskInfoArray[i];
    unsigned long long duration = tmpTInfo->endTime - tmpTInfo->startTime;
    printf("%s, %lld, %lld, %lld, %lld",
           tmpTInfo->taskName, tmpTInfo->startTime, tmpTInfo->endTime,
           duration, tmpTInfo->exitIndex);
    // summarize new obj info
    if(tmpTInfo->newObjs != NULL) {
      struct RuntimeHash * nobjtbl = allocateRuntimeHash(5);
      struct RuntimeIterator * iter = NULL;
      while(0 == isEmpty(tmpTInfo->newObjs)) {
		char * objtype = (char *)(getItem(tmpTInfo->newObjs));
		if(RuntimeHashcontainskey(nobjtbl, (int)(objtype))) {
		  int num = 0;
		  RuntimeHashget(nobjtbl, (int)objtype, &num);
		  RuntimeHashremovekey(nobjtbl, (int)objtype);
		  num++;
		  RuntimeHashadd(nobjtbl, (int)objtype, num);
		} else {
		  RuntimeHashadd(nobjtbl, (int)objtype, 1);
		}
		//printf(stderr, "new obj!\n");
      }

      // output all new obj info
      iter = RuntimeHashcreateiterator(nobjtbl);
      while(RunhasNext(iter)) {
		char * objtype = (char *)Runkey(iter);
		int num = Runnext(iter);
		printf(", %s, %d", objtype, num);
      }
    }
    printf("\n");
    if(strcmp(tmpTInfo->taskName, "tpd checking") == 0) {
      preprocessingtime += duration;
    } else if(strcmp(tmpTInfo->taskName, "post task execution") == 0) {
      postprocessingtime += duration;
    } else if(strcmp(tmpTInfo->taskName, "objqueue checking") == 0) {
      objqueuecheckingtime += duration;
    } else {
      totaltasktime += duration;
      averagetasktime += duration;
      tasknum++;
    }
  }

  if(taskInfoOverflow) {
    printf("Caution: task info overflow!\n");
  }

  other = totalexetime-totaltasktime-preprocessingtime-postprocessingtime;
  averagetasktime /= tasknum;

  printf("\nTotal time: %lld\n", totalexetime);
  printf("Total task execution time: %lld (%d%%)\n", totaltasktime,
         (int)(((double)totaltasktime/(double)totalexetime)*100));
  printf("Total objqueue checking time: %lld (%d%%)\n",
         objqueuecheckingtime,
         (int)(((double)objqueuecheckingtime/(double)totalexetime)*100));
  printf("Total pre-processing time: %lld (%d%%)\n", preprocessingtime,
         (int)(((double)preprocessingtime/(double)totalexetime)*100));
  printf("Total post-processing time: %lld (%d%%)\n", postprocessingtime,
         (int)(((double)postprocessingtime/(double)totalexetime)*100));
  printf("Other time: %lld (%d%%)\n", other,
         (int)(((double)other/(double)totalexetime)*100));


  printf("\nAverage task execution time: %lld\n", averagetasktime);

  //printf("\nTotal time spent for interruptions: %lld\n", interrupttime);
#else
  int i = 0;
  int j = 0;

  BAMBOO_DEBUGPRINT(0xdddd);
  // output task related info
  for(i= 0; i < taskInfoIndex; i++) {
    TaskInfo* tmpTInfo = taskInfoArray[i];
    char* tmpName = tmpTInfo->taskName;
    int nameLen = strlen(tmpName);
    BAMBOO_DEBUGPRINT(0xddda);
    for(j = 0; j < nameLen; j++) {
      BAMBOO_DEBUGPRINT_REG(tmpName[j]);
    }
    BAMBOO_DEBUGPRINT(0xdddb);
    BAMBOO_DEBUGPRINT_REG(tmpTInfo->startTime);
    BAMBOO_DEBUGPRINT_REG(tmpTInfo->endTime);
    BAMBOO_DEBUGPRINT_REG(tmpTInfo->exitIndex);
    if(tmpTInfo->newObjs != NULL) {
      struct RuntimeHash * nobjtbl = allocateRuntimeHash(5);
      struct RuntimeIterator * iter = NULL;
      while(0 == isEmpty(tmpTInfo->newObjs)) {
		char * objtype = (char *)(getItem(tmpTInfo->newObjs));
		if(RuntimeHashcontainskey(nobjtbl, (int)(objtype))) {
		  int num = 0;
		  RuntimeHashget(nobjtbl, (int)objtype, &num);
		  RuntimeHashremovekey(nobjtbl, (int)objtype);
		  num++;
		  RuntimeHashadd(nobjtbl, (int)objtype, num);
		} else {
		  RuntimeHashadd(nobjtbl, (int)objtype, 1);
		}
      }

      // ouput all new obj info
      iter = RuntimeHashcreateiterator(nobjtbl);
      while(RunhasNext(iter)) {
		char * objtype = (char *)Runkey(iter);
		int num = Runnext(iter);
		int nameLen = strlen(objtype);
		BAMBOO_DEBUGPRINT(0xddda);
		for(j = 0; j < nameLen; j++) {
		  BAMBOO_DEBUGPRINT_REG(objtype[j]);
		}
		BAMBOO_DEBUGPRINT(0xdddb);
		BAMBOO_DEBUGPRINT_REG(num);
	  }
	}
	BAMBOO_DEBUGPRINT(0xdddc);
  }

  if(taskInfoOverflow) {
	BAMBOO_DEBUGPRINT(0xefee);
  }

#ifdef PROFILE_INTERRUPT
  // output interrupt related info
  for(i = 0; i < interruptInfoIndex; i++) {
	InterruptInfo* tmpIInfo = interruptInfoArray[i];
	BAMBOO_DEBUGPRINT(0xddde);
	BAMBOO_DEBUGPRINT_REG(tmpIInfo->startTime);
	BAMBOO_DEBUGPRINT_REG(tmpIInfo->endTime);
	BAMBOO_DEBUGPRINT(0xdddf);
  }

  if(interruptInfoOverflow) {
	BAMBOO_DEBUGPRINT(0xefef);
  }
#endif // PROFILE_INTERRUPT

  BAMBOO_DEBUGPRINT(0xeeee);
#endif
}
#endif  // #ifdef PROFILE

#endif
