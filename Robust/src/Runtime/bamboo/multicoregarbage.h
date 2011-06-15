#ifndef BAMBOO_MULTICORE_GARBAGE_H
#define BAMBOO_MULTICORE_GARBAGE_H
#ifdef MULTICORE_GC
#include "multicore.h"
#include "multicoregc.h"
#include "multicorehelper.h"  // for mappings between core # and block #
#include "structdefs.h"
#include "multicoregcprofile.h"

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

typedef enum {
  INITPHASE = 0x0,         // 0x0
  MARKPHASE,               // 0x1
  COMPACTPHASE,            // 0x2
  SUBTLECOMPACTPHASE,      // 0x3
  MAPPHASE,                // 0x4
  FLUSHPHASE,              // 0x5
#ifdef GC_CACHE_ADAPT
  CACHEPOLICYPHASE,        // 0x6
  PREFINISHPHASE,          // 0x7
#endif 
  FINISHPHASE              // 0x6/0x8
} GCPHASETYPE;

typedef struct gc_status {
  volatile bool gcprocessing;
  volatile GCPHASETYPE gcphase; // indicating GC phase
  volatile bool gcbusystatus;
} gc_status_t;

extern volatile bool gcflag;
extern gc_status_t gc_status_info;
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

unsigned int gcself_numsendobjs;
unsigned int gcself_numreceiveobjs;

// for load balancing
unsigned int gcheaptop;
void * gcloads[NUMCORES4GC];
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
void ** gcmappingtbl;
unsigned int bamboo_rmsp_size;

unsigned int * gcmarktbl;


// table recording the starting address of each small block
// (size is BAMBOO_SMEM_SIZE)
// Note: 1. this table always resides on the very bottom of the shared memory
//       2. it is not counted in the shared heap, would never be garbage 
//          collected
int * gcsbstarttbl;
#ifdef GC_TBL_DEBUG
unsigned int gcsbstarttbl_len;
#endif
unsigned int gcnumblock; // number of total blocks in the shared mem
void * gcbaseva; // base va for shared memory without reserved sblocks

#ifdef GC_CACHE_ADAPT
void * gctopva; // top va for shared memory without reserved sblocks
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
#endif

#define WAITFORGCPHASE(phase) while(gc_status_info.gcphase != phase) ;



#define ISSHAREDOBJ(p) \
  ((((unsigned int)p)>=gcbaseva)&&(((unsigned int)p)<(gcbaseva+(BAMBOO_SHARED_MEM_SIZE))))


#define ALIGNMENTBYTES 32
#define ALIGNMENTSHIFT 5

/* Number of bits used for each alignment unit */
#define BITSPERALIGNMENT 2
#define ALIGNOBJSIZE(x) (x>>ALIGNMENTSHIFT)
#define OBJMAPPINGINDEX(p) ALIGNOBJSIZE((unsigned INTPTR)(p-gcbaseva))

//There are two bits per object
//00 means not marked
//11 means first block of object
//10 means marked block

#define UNMARKED 0
#define MARKEDFIRST 3
#define MARKEDLATER 2

//sets y to the marked status of x
#define GETMARKED(y,x) { unsigned INTPTR offset=ALIGNOBJSIZE((unsigned INTPTR)(x-gcbaseva)); \
    y=(gcmarktbl[offset>>4]>>((offset&15)<<1))&3; }

//sets the marked status of x to y (assumes zero'd)
#define SETMARKED(y,x) { unsigned INTPTR offset=ALIGNOBJSIZE((unsigned INTPTR)(x-gcbaseva)); \
    gcmarktbl[offset>>4]|=y<<((offset&15)<<1); }

//sets the marked status of x to y (assumes zero'd)
#define RESETMARKED(x) { unsigned INTPTR offset=ALIGNOBJSIZE((unsigned INTPTR)(x-gcbaseva)); \
    gcmarktbl[offset>>4]&=~(3<<((offset&15)<<1)); }

#define ALIGNSIZE(s, as) (*((unsigned int*)as))=((((unsigned int)(s-1))&(~(BAMBOO_CACHE_LINE_MASK)))+(BAMBOO_CACHE_LINE_SIZE))


// mapping of pointer to block # (start from 0), here the block # is
// the global index
#define BLOCKINDEX(p, b) \
  { \
    unsigned INTPTR t = (unsigned INTPTR)(p - gcbaseva);	\
    if(t < BAMBOO_LARGE_SMEM_BOUND) { \
      b = t / BAMBOO_SMEM_SIZE_L; \
    } else { \
      b = NUMCORES4GC+((t-BAMBOO_LARGE_SMEM_BOUND)/BAMBOO_SMEM_SIZE); \
    } \
  }

#define RESIDECORE(p, c) { \
    if(1 == (NUMCORES4GC)) { \
      c = 0; \
    } else { \
      unsigned INTPTR b; \
      BLOCKINDEX(p, b); \
      c = gc_block2core[(b%(NUMCORES4GC*2))]; \
    } \
  }

INLINE static unsigned int hostcore(void * ptr) {
  // check the host core of ptr
  unsigned int host = 0;
  RESIDECORE(ptr, host);
  return host;
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

#define OFFSET2BASEVA(i) \
  (((i)<NUMCORES4GC)?(BAMBOO_SMEM_SIZE_L*(i)):(BAMBOO_SMEM_SIZE*((i)-NUMCORES4GC)+BAMBOO_LARGE_SMEM_BOUND))

#define BLOCKSIZE(c) \
  ((c)?BAMBOO_SMEM_SIZE_L:BAMBOO_SMEM_SIZE)

// mapping of (core #, index of the block) to the global block index
#define BLOCKINDEX2(c, n) \
  (gc_core2block[(2*(c))+((n)%2)]+((NUMCORES4GC*2)*((n)/2)))

#define BOUNDPTR(b) \
  (((b)<NUMCORES4GC)?(((b)+1)*BAMBOO_SMEM_SIZE_L):(BAMBOO_LARGE_SMEM_BOUND+((b)-NUMCORES4GC+1)*BAMBOO_SMEM_SIZE))

#define BLOCKBOUND(n) \
  (((n)==0)?BAMBOO_SMEM_SIZE_L:BAMBOO_SMEM_SIZE_L+BAMBOO_SMEM_SIZE*(n))

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
      if(gc_checkAllCoreStatus()) { \
        break; \
      } \
    } \
  }

// send a 1-word msg to all clients
#define GC_SEND_MSG_1_TO_CLIENT(m) \
  { \
    for(int i = 0; i < NUMCORESACTIVE; ++i) { \
      gccorestatus[i] = 1; \
      if(BAMBOO_NUM_OF_CORE != i) { \
        send_msg_1(i, (m)); \
      } \
    } \
  }

#define ISLOCAL(p) (hostcore(p)==BAMBOO_NUM_OF_CORE)

void initmulticoregcdata();
void dismulticoregcdata();
bool gc_checkAllCoreStatus();
bool gc(struct garbagelist * stackptr); // core coordinator routine
void gc_collect(struct garbagelist* stackptr); //core collector routine
void gc_nocollect(struct garbagelist* stackptr); //non-gc core collector routine
void master_mark(struct garbagelist *stackptr);
void master_getlargeobjs();
void master_compact();
void master_updaterefs();
void master_finish();
void gc_master(struct garbagelist * stackptr);


void transferMarkResults_I();
bool gcfindSpareMem_I(unsigned int * startaddr,unsigned int * tomove,unsigned int * dstcore,unsigned int requiredmem,unsigned int requiredcore);

#define INITMULTICOREGCDATA() initmulticoregcdata()
#define DISMULTICOREGCDATA() dismulticoregcdata()
#else // MULTICORE_GC
#define INITMULTICOREGCDATA()
#define DISMULTICOREGCDATA()
#endif // MULTICORE_GC
#endif // BAMBOO_MULTICORE_GARBAGE_H
