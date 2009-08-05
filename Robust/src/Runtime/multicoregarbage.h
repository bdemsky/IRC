#ifndef MULTICORE_GARBAGE_H
#define MULTICORE_GARBAGE_H
#include "Queue.h"

// data structures for GC
#define BAMBOO_NUM_PAGES 1024 * 512
#define BAMBOO_PAGE_SIZE 4096
#define BAMBOO_SHARED_MEM_SIZE BAMBOO_PAGE_SIZE * BAMBOO_PAGE_SIZE
#define BAMBOO_BASE_VA 0xd000000
#define BAMBOO_SMEM_SIZE 16 * BAMBOO_PAGE_SIZE
#define BAMBOO_SMEM_SIZE_L 512 * BAMBOO_PAGE_SIZE
#define BAMBOO_LARGE_SMEM_BOUND BAMBOO_SMEM_SIZE_L * NUMCORES // NUMCORES = 62

struct garbagelist {
  int size;
  struct garbagelist *next;
  void * array[];
};

struct listitem {
  struct listitem * prev;
  struct listitem * next;
  struct garbagelist * stackptr;
};

struct pointerblock {
  void * ptrs[NUMPTRS];
  struct pointerblock *next;
};

struct pointerblock *gchead=NULL;
int gcheadindex=0;
struct pointerblock *gctail=NULL;
int gctailindex=0;
struct pointerblock *gctail2=NULL;
int gctailindex2=0;
struct pointerblock *gcspare=NULL;

struct largeObjItem {
	INTPTR orig;
	INTPTR dst;
	int length;
	struct largeObjItem * next;
};
/*
struct moveObj {
	INTPTR * starts;
	INTPTR * ends;
	INTPTR * dststarts;
	int * dsts;
	int length;
};

struct compactInstr {
	struct moveObj * tomoveobjs;
	struct moveObj * incomingobjs;
	struct largeObjItem * largeobjs;
};
*/
enum GCPHASETYPE {
	MARKPHASE = 0x0,   // 0x0
	COMPACTPHASE,      // 0x1
	FLUSHPHASE,        // 0x2
	FINISHPHASE        // 0x3
};

volatile bool gcflag;
volatile bool gcprocessing;
GCPHASETYPE gcphase; // indicating GC phase
bool gctomove; // flag indicating if can start moving objects to other cores
struct Queue * gcdsts;
// for mark phase termination
int gccorestatus[NUMCORES]; // records status of each core
                            // 1: running gc
                            // 0: stall
int gcnumsendobjs[NUMCORES]; // records how many objects a core has sent out
int gcnumreceiveobjs[NUMCORES]; // records how many objects a core has received
int gcself_numsendobjs;
int gcself_numreceiveobjs;
// for load balancing
int gcloads[NUMCORES];
int gcreloads[NUMCORES];
int gcdeltal[NUMCORES];
int gcdeltar[NUMCORES];

// compact instruction
struct compactInstr * cinstruction;
// mapping of old address to new address
struct genhashtable * pointertbl;
int obj2map;
int mappedobj;
bool ismapped;

#define BLOCKINDEX(p, b) \
	if((p) < BAMBOO_LARGE_SMEM_BOUND) { \
		(*((int*)b)) = (p) / BAMBOO_SMEM_SIZE_L; \
	} else { \
		(*((int*)b)) = NUMCORES + ((p) - BAMBOO_LARGE_SMEM_BOUND) / BAMBOO_SMEM_SIZE; \
	}

#define RESIDECORE(p, x, y) \
	int b; \
	BLOCKINDEX((p), &b); \
	bool reverse = (b / NUMCORES) % 2; \
	int l = b % NUMCORES; \
	if(reverse) { \
		if(l < 14) { \
			l += 1; \
		} else { \
			l += 2; \
		} \
		(*((int*)y)) = bamboo_width - 1 - l / bamboo_width; \
	} else { \
		if(l > 54) { \
			l += 2; \
		} else if(l > 47) {\
			l += 1; \
		} \
		(*((int*)y)) = l / bamboo_width; \
	} \
	if((l/bamboo_width)%2) { \
		(*((int*)x)) = bamboo_width - 1 - l % bamboo_width; \
	} else { \
		(*((int*)x)) = l % bamboo_width; \
	}

// NOTE: n starts from 0
#define NUMBLOCKS(s, n) \
	if(s < BAMBOO_SMEM_SIZE_L) { \
		(*((int*)n)) = 0; \
	} else { \
		(*((int*)n)) = 1 + (s - BAMBOO_SMEM_SIZE_L) / BAMBOO_SMEM_SIZE; \
	}

#define BASEPTR(c, n, p) \
	int x; \
  int y; \
	int b; \
  if(c > 5) c += 2; \
  x = c / bamboo_height; \
	y = c % bamboo_height; \
	if(n%2) { \
		if(y % 2) { \
			b = bamboo_width - 1 - x + (bamboo_width - 1 - y) * bamboo_width; \
		} else { \
			b = x + (bamboo_width - 1 - y) * bamboo_width; \
		} \
		if(y>5) { \
			b--; \
		} else { \
			b -= 2; \
		} \
		b += NUMCORES * n; \
	} else { \
		if(y % 2) { \
			b = bamboo_width - 1 - x + y * bamboo_width; \
		} else { \
			b = x + y * bamboo_width; \
		} \
		if(y>5) b--; \
		b += NUMCORES * n; \
	} \
	if(b < NUMCORES) { \
		(*((int*)p)) = b * BAMBOO_SMEM_SIZE_L; \
	} else { \
		(*((int*)p)) = BAMBOO_LARGE_SMEM_BOUND + (b - NUMCORES) * BAMBOO_SMEM_SIZE; \
	} 

#define LEFTNEIGHBOUR(n, c) \
	int x; \
  int y; \
  if(n > 5) n += 2; \
  x = n / bamboo_height; \
	y = n % bamboo_height; \
	if((0 == n) || (15 == n)) { \
		(*((int*)c)) = -1; \
	} else if(n < 5) { \
		if( 0 == y % 2) { \
			(*((int*)c)) = y - 1; \
		} else { \
			(*((int*)c)) = y + 1; \
		} \
	} else if(5 == n) { \
		(*((int*)c)) = (x + 1) * bamboo_height + y + 1 - 2; \
	} else if(14 == n) { \
		(*((int*)c)) = 5; \
	} else { \
		(*((int*)c)) = (x - 1) * bamboo_height + y - 2; \
	} 

#define RIGHTNEIGHBOUR(n, c) \
	int x; \
  int y; \
  if(n > 5) n += 2; \
  x = n / bamboo_height; \
	y = n % bamboo_height; \
	if(n < 56) { \
		(*((int*)c)) = (x + 1) * bamboo_height + y - 2; \
	} else if( 0 == y % 2) { \
		(*((int*)c)) = x * bamboo_height + y + 1 - 2; \
	} else { \
		(*((int*)c)) = x * bamboo_height + y - 1 - 2; \
	} 

void gc(struct garbagelist * stackptr); // core coordinator routine
void gc_collect(struct garbagelist * stackptr); // core collector routine
void transferMarkResults();
void gc_enqueue(void *ptr);

#endif

