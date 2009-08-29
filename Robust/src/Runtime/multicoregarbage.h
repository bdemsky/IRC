#ifndef MULTICORE_GARBAGE_H
#define MULTICORE_GARBAGE_H
#include "multicoregc.h"
#include "structdefs.h"

#ifndef bool
#define bool int
#endif

// data structures for GC
#ifdef GC_DEBUG
#define BAMBOO_SMEM_SIZE_L (BAMBOO_SMEM_SIZE)
#else
#define BAMBOO_SMEM_SIZE_L (32 * BAMBOO_SMEM_SIZE)
#endif
#define BAMBOO_LARGE_SMEM_BOUND (BAMBOO_SMEM_SIZE_L*NUMCORES) // NUMCORES=62

#define NUMPTRS 100

typedef enum {
	MARKPHASE = 0x0,   // 0x0
	COMPACTPHASE,      // 0x1
	SUBTLECOMPACTPHASE,// 0x2
	FLUSHPHASE,        // 0x3
	FINISHPHASE        // 0x4
} GCPHASETYPE;

volatile bool gcflag;
volatile bool gcprocessing;
GCPHASETYPE gcphase; // indicating GC phase

int gccurr_heaptop;
// for mark phase termination
int gccorestatus[NUMCORES]; // records status of each core
                            // 1: running gc
                            // 0: stall
int gcnumsendobjs[NUMCORES]; // records how many objects sent out
int gcnumreceiveobjs[NUMCORES]; // records how many objects received
bool gcbusystatus;
int gcself_numsendobjs;
int gcself_numreceiveobjs;

// for load balancing
INTPTR gcheaptop;
int gcloads[NUMCORES];
int gctopcore; // the core host the top of the heap
bool gcheapdirection; // 0: decrease; 1: increase

int gcnumlobjs;

// compact instruction
INTPTR gcmarkedptrbound;
int gcblock2fill;
int gcstopblock[NUMCORES]; // indicate when to stop compact phase
int gcfilledblocks[NUMCORES]; //indicate how many blocks have been fulfilled
// move instruction;
INTPTR gcmovestartaddr;
int gcdstcore;
bool gctomove;
int gcrequiredmems[NUMCORES]; //record pending mem requests
int gcmovepending;

// mapping of old address to new address
struct RuntimeHash * gcpointertbl;
int gcobj2map;
int gcmappedobj;
bool gcismapped;

// table recording the starting address of each small block
// (size is BAMBOO_SMEM_SIZE)
// Note: 1. this table always resides on the very bottom of the shared memory
//       2. the first two blocks are reserved for this table, would never be
//          moved or garbage collected.
INTPTR * gcsbstarttbl;
int gcreservedsb;  // number of reserved sblock for sbstarttbl
int gcbaseva; // base va for shared memory without reserved sblocks

#define ISSHAREDOBJ(p) \
	(((p)>gcbaseva)&&((p)<(gcbaseva+(BAMBOO_SHARED_MEM_SIZE))))

#define ALIGNSIZE(s, as) \
	(*((int*)as)) = (((s) & (~(BAMBOO_CACHE_LINE_MASK))) + (BAMBOO_CACHE_LINE_SIZE))

#define BLOCKINDEX(p, b) \
  { \
		int t = (p) - gcbaseva; \
		if(t < (BAMBOO_LARGE_SMEM_BOUND)) { \
			(*((int*)b)) = t / (BAMBOO_SMEM_SIZE_L); \
		} else { \
			(*((int*)b)) = NUMCORES+((t-(BAMBOO_LARGE_SMEM_BOUND))/(BAMBOO_SMEM_SIZE));\
		} \
	}

#define RESIDECORE(p, x, y) \
	{ \
		if(1 == (NUMCORES)) { \
			(*((int*)x)) = 0; \
			(*((int*)y)) = 0; \
		} else { \
			int b; \
			BLOCKINDEX((p), &b); \
			bool reverse = (b / (NUMCORES)) % 2; \
			int l = b % (NUMCORES); \
			BAMBOO_DEBUGPRINT_REG(b); \
			BAMBOO_DEBUGPRINT_REG(l); \
			BAMBOO_DEBUGPRINT_REG(reverse); \
			if(reverse) { \
				if(62 == (NUMCORES)) { \
					if(l < 14) { \
						l += 1; \
					} else { \
						l += 2; \
					} \
				} \
				BAMBOO_DEBUGPRINT_REG(l); \
				(*((int*)y)) = bamboo_height - 1 - (l / bamboo_width); \
				BAMBOO_DEBUGPRINT_REG(*((int*)y)); \
			} else { \
				if(62 == (NUMCORES)) {\
					if(l > 54) { \
						l += 2; \
					} else if(l > 47) {\
						l += 1; \
					} \
				} \
				(*((int*)y)) = l / bamboo_width; \
			} \
			BAMBOO_DEBUGPRINT_REG(*((int*)y)); \
		if(((!reverse)&&(*((int*)y))%2) || ((reverse)&&((*((int*)y))%2==0))){ \
				(*((int*)x)) = bamboo_width - 1 - (l % bamboo_width); \
			} else { \
				(*((int*)x)) = (l % bamboo_width); \
			} \
		} \
	}

// NOTE: n starts from 0
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

#define BLOCKINDEX2(c, n, b) \
  { \
		int x; \
		int y; \
		int t; \
		int cc = c; \
		if((62 == (NUMCORES)) && (cc > 5)) cc += 2; \
		x = cc / bamboo_height; \
		y = cc % bamboo_height; \
		if((n) % 2) { \
			if(y % 2) { \
				t = x + (bamboo_width - 1 - y) * bamboo_width; \
			} else { \
				t = bamboo_width - 1 - x + (bamboo_width - 1 - y) * bamboo_width; \
			} \
			if(62 == (NUMCORES)) {\
				if(y>5) { \
					t--; \
				} else { \
					t -= 2; \
				} \
			} \
		} else { \
			if(y % 2) { \
				t = bamboo_width - 1 - x + y * bamboo_width; \
			} else { \
				t = x + y * bamboo_width; \
			} \
			if(62 == (NUMCORES)) { \
				if(y > 6) { \
					t -= 2; \
				} else if(y > 5) { \
					t--; \
				} \
			} \
		} \
		t += (NUMCORES) * (n); \
		(*((int*)b)) = t; \
	}


#define BASEPTR(c, n, p) \
  { \
		int b; \
		BLOCKINDEX2(c, n, &b); \
		if(b < (NUMCORES)) { \
			(*((int*)p)) = gcbaseva + b * (BAMBOO_SMEM_SIZE_L); \
		} else { \
			(*((int*)p)) = gcbaseva+(BAMBOO_LARGE_SMEM_BOUND)+(b-(NUMCORES))*(BAMBOO_SMEM_SIZE); \
		} \
	}

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

#endif

