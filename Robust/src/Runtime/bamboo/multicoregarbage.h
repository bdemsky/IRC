#ifndef MULTICORE_GARBAGE_H
#define MULTICORE_GARBAGE_H
#include "multicoregc.h"
#include "multicorehelper.h"  // for mappins between core # and block #
#include "structdefs.h"
#include "MGCHash.h"
#include "GCSharedHash.h"
#ifdef GC_CACHE_ADAPT
#include "multicorecache.h"
#endif // GC_CACHE_ADAPT

#ifndef bool
#define bool int
#endif

// data structures for GC
#ifdef GC_DEBUG
#define BAMBOO_SMEM_SIZE_L (BAMBOO_SMEM_SIZE * 2)
#else
#define BAMBOO_SMEM_SIZE_L (BAMBOO_SMEM_SIZE * 2)
#endif
#define BAMBOO_LARGE_SMEM_BOUND (BAMBOO_SMEM_SIZE_L*NUMCORES4GC)
// let each gc core to have one big block, this is very important
// for the computation of NUMBLOCKS(s, n), DO NOT change this!

#ifdef GC_FLUSH_DTLB
#define GC_NUM_FLUSH_DTLB 1
int gc_num_flush_dtlb;
#endif

#define NUMPTRS 100

// for GC profile
#ifdef GC_PROFILE
#define GCINFOLENGTH 100

#ifdef GC_CACHE_ADAPT
#define GC_PROFILE_NUM_FIELD 16
#else
#define GC_PROFILE_NUM_FIELD 15
#endif // GC_CACHE_ADAPT

typedef struct gc_info {
  unsigned long long time[GC_PROFILE_NUM_FIELD];
  int index;
} GCInfo;

GCInfo * gc_infoArray[GCINFOLENGTH];
int gc_infoIndex;
bool gc_infoOverflow;
unsigned long long gc_num_livespace;
unsigned long long gc_num_freespace;
unsigned long long gc_num_lobjspace;
unsigned int gc_num_lobj;

unsigned int gc_num_liveobj;
unsigned int gc_num_obj;
unsigned int gc_num_forwardobj;
int gc_num_profiles;

#endif // GC_PROFILE

typedef enum {
  INIT = 0,           // 0
  DISCOVERED = 2,     // 2
  REMOTEM = 4,        // 4
  MARKED = 8,         // 8
  COMPACTED = 16,     // 16
  FLUSHED = 32,       // 32
  END = 33            // 33
} GCOBJFLAG;

typedef enum {
  INITPHASE = 0x0,         // 0x0
  MARKPHASE,               // 0x1
  COMPACTPHASE,            // 0x2
  SUBTLECOMPACTPHASE,      // 0x3
  MAPPHASE,                // 0x4
  FLUSHPHASE,              // 0x5
#ifdef GC_CACHE_ADAPT
  PREFINISHPHASE,          // 0x6
#endif // GC_CACHE_ADAPT
  FINISHPHASE              // 0x6/0x7
} GCPHASETYPE;

volatile bool gcflag;
volatile bool gcprocessing;
volatile GCPHASETYPE gcphase; // indicating GC phase

volatile bool gcpreinform; // counter for stopped cores
volatile bool gcprecheck; // indicates if there are updated pregc information

int gccurr_heaptop;
struct MGCHash * gcforwardobjtbl; // cache forwarded objs in mark phase
// for mark phase termination
volatile int gccorestatus[NUMCORESACTIVE]; // records status of each core
                                           // 1: running gc
                                           // 0: stall
volatile int gcnumsendobjs[2][NUMCORESACTIVE]; // the # of objects sent out
volatile int gcnumreceiveobjs[2][NUMCORESACTIVE]; // the # of objects received
volatile int gcnumsrobjs_index;  // indicates which entry to record the info 
		                         // received before phase 1 of the mark finish 
						         // checking process
								 // the info received in phase 2 must be 
								 // recorded in the other entry
volatile bool gcbusystatus;
int gcself_numsendobjs;
int gcself_numreceiveobjs;

// for load balancing
INTPTR gcheaptop;
int gcloads[NUMCORES4GC];
int gctopcore; // the core host the top of the heap
int gctopblock; // the number of current top block

int gcnumlobjs;

// compact instruction
INTPTR gcmarkedptrbound;
int gcblock2fill;
int gcstopblock[NUMCORES4GC]; // indicate when to stop compact phase
int gcfilledblocks[NUMCORES4GC]; //indicate how many blocks have been fulfilled
// move instruction;
INTPTR gcmovestartaddr;
int gcdstcore;
volatile bool gctomove;
int gcrequiredmems[NUMCORES4GC]; //record pending mem requests
volatile int gcmovepending;

// shared memory pointer for shared pointer mapping tbls
// In GC version, this block of memory is located at the bottom of the 
// shared memory, right on the top of the smem tbl.
// The bottom of the shared memory = sbstart tbl + smemtbl 
//                                  + NUMCORES4GC bamboo_rmsp
// These three types of table are always reside at the bottom of the shared 
// memory and will never be moved or garbage collected
#ifdef GC_SMALLPAGESIZE
#define BAMBOO_RMSP_SIZE (1024 * 1024)
#else
#define BAMBOO_RMSP_SIZE (BAMBOO_SMEM_SIZE) // (45 * 16 * 1024)
#endif
mspace bamboo_rmsp;
// shared pointer mapping tbl
mgcsharedhashtbl_t * gcsharedptbl;
// remote shared pointer tbls
mgcsharedhashtbl_t * gcrpointertbls[NUMCORES4GC];

#ifdef LOCALHASHTBL_TEST
struct RuntimeHash * gcpointertbl;
#else
mgchashtable_t * gcpointertbl;
#endif
int gcobj2map;
int gcmappedobj;
volatile bool gcismapped;

// table recording the starting address of each small block
// (size is BAMBOO_SMEM_SIZE)
// Note: 1. this table always resides on the very bottom of the shared memory
//       2. it is not counted in the shared heap, would never be garbage 
//          collected
INTPTR * gcsbstarttbl;
int gcreservedsb;  // number of reserved sblock for sbstarttbl
int gcnumblock; // number of total blocks in the shared mem
int gcbaseva; // base va for shared memory without reserved sblocks
#ifdef GC_CACHE_ADAPT
int gctopva; // top va for shared memory without reserved sblocks
volatile bool gccachestage;
// table recording the sampling data collected for cache adaption 
unsigned int * gccachesamplingtbl;
unsigned int * gccachesamplingtbl_local;
unsigned int size_cachesamplingtbl_local;
unsigned int * gccachesamplingtbl_r;
unsigned int * gccachesamplingtbl_local_r;
unsigned int size_cachesamplingtbl_local_r;
int * gccachepolicytbl;
unsigned int size_cachepolicytbl;
#endif // GC_CACHE_ADAPT

#define ISSHAREDOBJ(p) \
  ((((int)p)>gcbaseva)&&(((int)p)<(gcbaseva+(BAMBOO_SHARED_MEM_SIZE))))

#define ALIGNSIZE(s, as) \
  (*((int*)as)) = (((s) & (~(BAMBOO_CACHE_LINE_MASK))) + (BAMBOO_CACHE_LINE_SIZE))

// mapping of pointer to block # (start from 0), here the block # is
// the global index
#define BLOCKINDEX(p, b) \
  { \
    int t = (p) - gcbaseva; \
    if(t < (BAMBOO_LARGE_SMEM_BOUND)) { \
      (*((int*)b)) = t / (BAMBOO_SMEM_SIZE_L); \
    } else { \
      (*((int*)b)) = NUMCORES4GC+((t-(BAMBOO_LARGE_SMEM_BOUND))/(BAMBOO_SMEM_SIZE)); \
    } \
  }

// mapping of pointer to core #
#define RESIDECORE(p, c) \
  { \
    if(1 == (NUMCORES4GC)) { \
      (*((int*)c)) = 0; \
    } else { \
      int b; \
      BLOCKINDEX((p), &b); \
      (*((int*)c)) = gc_block2core[(b%(NUMCORES4GC*2))]; \
    } \
  }

// NOTE: n starts from 0
// mapping of heaptop (how many bytes there are in the local heap) to
// the number of the block
// the number of the block indicates that the block is the xth block on
// the local heap
#define NUMBLOCKS(s, n) \
  if(s < (BAMBOO_SMEM_SIZE_L)) { \
    (*((int*)(n))) = 0; \
  } else { \
    (*((int*)(n))) = 1 + ((s) - (BAMBOO_SMEM_SIZE_L)) / (BAMBOO_SMEM_SIZE); \
  }

#define OFFSET(s, o) \
  if(s < BAMBOO_SMEM_SIZE_L) { \
    (*((int*)(o))) = (s); \
  } else { \
    (*((int*)(o))) = ((s) - (BAMBOO_SMEM_SIZE_L)) % (BAMBOO_SMEM_SIZE); \
  }

// mapping of (core #, index of the block) to the global block index
#define BLOCKINDEX2(c, n) (gc_core2block[(2*(c))+((n)%2)]+((NUMCORES4GC*2)*((n)/2)))

// mapping of (core #, number of the block) to the base pointer of the block
#define BASEPTR(c, n, p) \
  { \
    int b = BLOCKINDEX2((c), (n)); \
    if(b < (NUMCORES4GC)) { \
      (*((int*)p)) = gcbaseva + b * (BAMBOO_SMEM_SIZE_L); \
    } else { \
      (*((int*)p)) = gcbaseva+(BAMBOO_LARGE_SMEM_BOUND)+ \
                     (b-(NUMCORES4GC))*(BAMBOO_SMEM_SIZE); \
    } \
  }

// the next core in the top of the heap
#define NEXTTOPCORE(b) (gc_block2core[((b)+1)%(NUMCORES4GC*2)])

inline bool gc(struct garbagelist * stackptr); // core coordinator routine
inline void gc_collect(struct garbagelist* stackptr); //core collector routine
inline void gc_nocollect(struct garbagelist* stackptr); //non-gc core collector routine
inline void transferMarkResults_I();
inline void gc_enqueue_I(void *ptr);
inline void gc_lobjenqueue_I(void *ptr, int length, int host);
inline bool gcfindSpareMem_I(int * startaddr,
                             int * tomove,
                             int * dstcore,
                             int requiredmem,
                             int requiredcore);

inline void * gc_lobjdequeue4(int * length, int * host);
inline int gc_lobjmoreItems4();
inline void gc_lobjqueueinit4();

#ifdef GC_PROFILE
INLINE void gc_profileStart(void);
INLINE void gc_profileItem(void);
INLINE void gc_profileEnd(void);
void gc_outputProfileData();
#endif

#endif

