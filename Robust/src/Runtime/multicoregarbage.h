#ifndef MULTICORE_GARBAGE_H
#define MULTICORE_GARBAGE_H
#include "multicoregc.h"
#include "multicorehelper.h"  // for mappins between core # and block #
#include "structdefs.h"
#include "MGCHash.h"

#ifndef bool
#define bool int
#endif

// data structures for GC
#ifdef GC_DEBUG
#define BAMBOO_SMEM_SIZE_L (BAMBOO_SMEM_SIZE * 2)
#else
#define BAMBOO_SMEM_SIZE_L (32 * BAMBOO_SMEM_SIZE)
#endif
#define BAMBOO_LARGE_SMEM_BOUND (BAMBOO_SMEM_SIZE_L*NUMCORES4GC) // NUMCORES=62

#define NUMPTRS 100

typedef enum {
	INIT = 0,     // 0
	DISCOVERED,   // 1
	MARKED,       // 2
	COMPACTED,    // 3
	FLUSHED,      // 4
	END           // 5
} GCOBJFLAG;

typedef enum {
	INITPHASE = 0x0,   // 0x0
	MARKPHASE,         // 0x1
	COMPACTPHASE,      // 0x2
	SUBTLECOMPACTPHASE,// 0x3
	FLUSHPHASE,        // 0x4
	FINISHPHASE        // 0x5
} GCPHASETYPE;

volatile bool gcflag;
volatile bool gcprocessing;
volatile GCPHASETYPE gcphase; // indicating GC phase

int gccurr_heaptop;
// for mark phase termination
int gccorestatus[NUMCORES4GC]; // records status of each core
                            // 1: running gc
                            // 0: stall
int gcnumsendobjs[NUMCORES4GC]; // records how many objects sent out
int gcnumreceiveobjs[NUMCORES4GC]; // records how many objects received
bool gcbusystatus;
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

// mapping of old address to new address
struct RuntimeHash * gcpointertbl;
//struct MGCHash * gcpointertbl;
int gcobj2map;
int gcmappedobj;
volatile bool gcismapped;

// table recording the starting address of each small block
// (size is BAMBOO_SMEM_SIZE)
// Note: 1. this table always resides on the very bottom of the shared memory
//       2. the first two blocks are reserved for this table, would never be
//          moved or garbage collected.
INTPTR * gcsbstarttbl;
int gcreservedsb;  // number of reserved sblock for sbstarttbl
int gcnumblock; // number of total blocks in the shared mem
int gcbaseva; // base va for shared memory without reserved sblocks

// table recording the number of used bytes in each block
// Note: this table resides on master core's local heap
int * gcsmemtbl;

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
			(*((int*)b)) = NUMCORES4GC+((t-(BAMBOO_LARGE_SMEM_BOUND))/(BAMBOO_SMEM_SIZE));\
		} \
	}

// mapping of pointer to core #
#define RESIDECORE(p, c) \
{ \
	if(1 == (NUMCORES4GC)) { \
		(*((int*)c)) = 0; \
	} else {\
		int b; \
		BLOCKINDEX((p), &b); \
		(*((int*)c)) = gc_block2core[(b%(NUMCORES4GC*2))]; \
	}\
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

inline void gc(struct garbagelist * stackptr); // core coordinator routine
inline void gc_collect(struct garbagelist* stackptr);//core collector routine
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

#endif

