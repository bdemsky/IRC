#ifndef BAMBOO_MULTICORE_GARBAGE_H
#define BAMBOO_MULTICORE_GARBAGE_H
#ifdef MULTICORE_GC
#include "multicore.h"
#include "multicoregc.h"
#include "multicorehelper.h"  // for mappings between core # and block #
#include "structdefs.h"
#include "multicoregcprofile.h"
#include "multicorecache.h"

#ifdef GC_DEBUG
#define GC_PRINTF tprintf
#else
#define GC_PRINTF if(0) tprintf
#endif 

// data structures for GC
#define BAMBOO_SMEM_SIZE_L (BAMBOO_SMEM_SIZE * 2)
#define BAMBOO_LARGE_SMEM_BOUND (BAMBOO_SMEM_SIZE_L*NUMCORES4GC)
// let each gc core to have one big block, this is very important
// for the computation of NUMBLOCKS(s, n), DO NOT change this!

#ifdef GC_FLUSH_DTLB
#define GC_NUM_FLUSH_DTLB 1
unsigned int gc_num_flush_dtlb;
#endif

typedef enum {
  INIT = 0,           // 0
  DISCOVERED = 2,     // 2
  MARKED = 4,         // 4
  COMPACTED = 8,      // 8
  END = 9             // 9
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
#endif 
  FINISHPHASE              // 0x6/0x7
} GCPHASETYPE;

volatile bool gcflag;
volatile bool gcprocessing;
volatile GCPHASETYPE gcphase; // indicating GC phase

#define WAITFORGCPHASE(phase) while(gcphase != phase) ;

volatile bool gcpreinform; // counter for stopped cores
volatile bool gcprecheck; // indicates if there are updated pregc information

unsigned int gccurr_heaptop;
struct MGCHash * gcforwardobjtbl; // cache forwarded objs in mark phase
// for mark phase termination
volatile unsigned int gccorestatus[NUMCORESACTIVE];//records status of each core
                                                   // 1: running gc
                                                   // 0: stall
volatile unsigned int gcnumsendobjs[2][NUMCORESACTIVE];//# of objects sent out
volatile unsigned int gcnumreceiveobjs[2][NUMCORESACTIVE];//# of objects received
volatile unsigned int gcnumsrobjs_index;//indicates which entry to record the  
		                        // info received before phase 1 of the mark finish 
						                // checking process
								            // the info received in phase 2 must be 
								            // recorded in the other entry
volatile bool gcbusystatus;
unsigned int gcself_numsendobjs;
unsigned int gcself_numreceiveobjs;

// for load balancing
unsigned int gcheaptop;
unsigned int gcloads[NUMCORES4GC];
unsigned int gctopcore; // the core host the top of the heap
unsigned int gctopblock; // the number of current top block

unsigned int gcnumlobjs;

// compact instruction
unsigned int gcmarkedptrbound;
unsigned int gcblock2fill;
unsigned int gcstopblock[NUMCORES4GC]; // indicate when to stop compact phase
unsigned int gcfilledblocks[NUMCORES4GC]; //indicate how many blocks have been fulfilled
// move instruction;
unsigned int gcmovestartaddr;
unsigned int gcdstcore;
volatile bool gctomove;
unsigned int gcrequiredmems[NUMCORES4GC]; //record pending mem requests
volatile unsigned int gcmovepending;

// shared memory pointer for pointer mapping tbls
// In GC version, this block of memory is located at the bottom of the 
// shared memory, right on the top of the smem tbl.
// The bottom of the shared memory = sbstart tbl + smemtbl + bamboo_rmsp
// These three types of table are always reside at the bottom of the shared 
// memory and will never be moved or garbage collected
unsigned int * gcmappingtbl;
unsigned int bamboo_rmsp_size;
unsigned int bamboo_baseobjsize;

// table recording the starting address of each small block
// (size is BAMBOO_SMEM_SIZE)
// Note: 1. this table always resides on the very bottom of the shared memory
//       2. it is not counted in the shared heap, would never be garbage 
//          collected
int * gcsbstarttbl;
#ifdef GC_TBL_DEBUG
unsigned int gcsbstarttbl_len;
#endif
unsigned int gcreservedsb;  // number of reserved sblock for sbstarttbl
unsigned int gcnumblock; // number of total blocks in the shared mem
unsigned int gcbaseva; // base va for shared memory without reserved sblocks
#ifdef GC_CACHE_ADAPT
unsigned int gctopva; // top va for shared memory without reserved sblocks
volatile bool gccachestage;
// table recording the sampling data collected for cache adaption 
int * gccachesamplingtbl;
int * gccachesamplingtbl_local;
unsigned int size_cachesamplingtbl_local;
int * gccachesamplingtbl_r;
int * gccachesamplingtbl_local_r;
unsigned int size_cachesamplingtbl_local_r;
int * gccachepolicytbl;
unsigned int size_cachepolicytbl;
#endif // GC_CACHE_ADAPT

#define OBJMAPPINGINDEX(p) (((unsigned int)p-gcbaseva)/bamboo_baseobjsize)

#define ISSHAREDOBJ(p) \
  ((((unsigned int)p)>=gcbaseva)&&(((unsigned int)p)<(gcbaseva+(BAMBOO_SHARED_MEM_SIZE))))

#define ALIGNSIZE(s, as) \
  (*((unsigned int*)as))=((((unsigned int)(s-1))&(~(BAMBOO_CACHE_LINE_MASK)))+(BAMBOO_CACHE_LINE_SIZE))

// mapping of pointer to block # (start from 0), here the block # is
// the global index
#define BLOCKINDEX(p, b) \
  { \
    unsigned int t = (p) - gcbaseva; \
    if(t < (BAMBOO_LARGE_SMEM_BOUND)) { \
      (*((unsigned int*)b)) = t / (BAMBOO_SMEM_SIZE_L); \
    } else { \
      (*((unsigned int*)b)) = NUMCORES4GC+((t-(BAMBOO_LARGE_SMEM_BOUND))/(BAMBOO_SMEM_SIZE)); \
    } \
  }

// mapping of pointer to core #
#define RESIDECORE(p, c) \
  { \
    if(1 == (NUMCORES4GC)) { \
      (*((unsigned int*)c)) = 0; \
    } else { \
      unsigned int b; \
      BLOCKINDEX((p), &b); \
      (*((unsigned int*)c)) = gc_block2core[(b%(NUMCORES4GC*2))]; \
    } \
  }

// NOTE: n starts from 0
// mapping of heaptop (how many bytes there are in the local heap) to
// the number of the block
// the number of the block indicates that the block is the xth block on
// the local heap
#define NUMBLOCKS(s, n) \
  if(s < (BAMBOO_SMEM_SIZE_L)) { \
    (*((unsigned int*)(n))) = 0; \
  } else { \
    (*((unsigned int*)(n))) = 1 + ((s) - (BAMBOO_SMEM_SIZE_L)) / (BAMBOO_SMEM_SIZE); \
  }

#define OFFSET(s, o) \
  if(s < BAMBOO_SMEM_SIZE_L) { \
    (*((unsigned int*)(o))) = (s); \
  } else { \
    (*((unsigned int*)(o))) = ((s)-(BAMBOO_SMEM_SIZE_L))%(BAMBOO_SMEM_SIZE); \
  }

// mapping of (core #, index of the block) to the global block index
#define BLOCKINDEX2(c, n) \
  (gc_core2block[(2*(c))+((n)%2)]+((NUMCORES4GC*2)*((n)/2)))

// mapping of (core #, number of the block) to the base pointer of the block
#define BASEPTR(c, n, p) \
  { \
    unsigned int b = BLOCKINDEX2((c), (n)); \
    if(b < (NUMCORES4GC)) { \
      (*((unsigned int*)p)) = gcbaseva + b * (BAMBOO_SMEM_SIZE_L); \
    } else { \
      (*((unsigned int*)p)) = gcbaseva+(BAMBOO_LARGE_SMEM_BOUND)+ \
                     (b-(NUMCORES4GC))*(BAMBOO_SMEM_SIZE); \
    } \
  }

// the next core in the top of the heap
#define NEXTTOPCORE(b) (gc_block2core[((b)+1)%(NUMCORES4GC*2)])

// close current block, fill the header
#define CLOSEBLOCK(base, size) \
  { \
    BAMBOO_MEMSET_WH((base), '\0', BAMBOO_CACHE_LINE_SIZE); \
    *((int*)(base)) = (size); \
  }

// check if all cores are stall now
#define GC_CHECK_ALL_CORE_STATUS(f) \
  { \
    gccorestatus[BAMBOO_NUM_OF_CORE] = 0; \
    while(f) { \
      BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT(); \
      if(gc_checkAllCoreStatus_I()) { \
        BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME(); \
        break; \
      } \
      BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME(); \
    } \
  }

// send a 1-word msg to all clients
#define GC_SEND_MSG_1_TO_CLIENT(m) \
  { \
    for(int i = 0; i < NUMCORESACTIVE; ++i) { \
      gccorestatus[i] = 1; \
      if(BAMBOO_NUM_OF_CORE != i) { \
        send_msg_1(i, (m), false); \
      } \
    } \
  }

#define ISLOCAL(p) (hostcore(p)==BAMBOO_NUM_OF_CORE)

void initmulticoregcdata();
void dismulticoregcdata();
bool gc_checkAllCoreStatus_I();
bool gc(struct garbagelist * stackptr); // core coordinator routine
void gc_collect(struct garbagelist* stackptr); //core collector routine
void gc_nocollect(struct garbagelist* stackptr); //non-gc core collector routine
void master_mark(struct garbagelist *stackptr);
void master_getlargeobjs();
void master_compact();
void master_updaterefs();
void master_finish();
void gc_master(struct garbagelist * stackptr);


INLINE void transferMarkResults_I();
INLINE bool gcfindSpareMem_I(unsigned int * startaddr,
                             unsigned int * tomove,
                             unsigned int * dstcore,
                             unsigned int requiredmem,
                             unsigned int requiredcore);

#define INITMULTICOREGCDATA() initmulticoregcdata()
#define DISMULTICOREGCDATA() dismulticoregcdata()
#else // MULTICORE_GC
#define INITMULTICOREGCDATA()
#define DISMULTICOREGCDATA()
#endif // MULTICORE_GC
#endif // BAMBOO_MULTICORE_GARBAGE_H
