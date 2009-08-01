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

struct markedObjItem {
	INTPTR orig;
	INTPTR dst;
	struct markedObjItem * next;
};

struct largeObjItem {
	INTPTR orig;
	INTPTR dst;
	int length;
	struct largeObjItem * next;
};

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

enum GCPHASETYPE {
	MARKPHASE = 0x0,   // 0x0
	COMPACTPHASE,      // 0x1
	FLUSHPHASE,        // 0x2
	FINISHPHASE        // 0x3
};

volatile bool gcflag;
GCPHASETYPE gcphase; // indicating GC phase
bool gctomove; // flag indicating if can start moving objects to other cores
struct Queue * gcdsts;
struct Queue gctomark; // global queue of objects to mark
// for mark phase termination
int gccorestatus[NUMCORES]; // records status of each core
                           // 1: running gc
                           // 0: stall
int gcnumsendobjs[NUMCORES]; // records how many objects a core has sent out
int gcnumreceiveobjs[NUMCORES]; // records how many objects a core has received
int gcnumconfirm;
bool gcwaitconfirm;
bool gcbusystatus;
int gcself_numsendobjs;
int gcself_numreceiveobjs;
// compact instruction
struct compactInstr * cinstruction;
// mapping of old address to new address
struct genhashtable * pointertbl;
int obj2map;
int mappedobj;
bool ismapped;

#define BLOCKNUM(p, b) \
	if((p) < BAMBOO_LARGE_SMEM_BOUND) { \
		(*((int*)b)) = (p) / BAMBOO_SMEM_SIZE_L; \
	} else { \
		(*((int*)b)) = NUMCORES + ((p) - BAMBOO_LARGE_SMEM_BOUND) / BAMBOO_SMEM_SIZE; \
	}

#define RESIDECORE(p, x, y) \
	int b; \
	BLOCKNUM((p), &b); \
	bool reverse = (b / NUMCORES) % 2; \
	int l = b % NUMCORES; \
	if(reverse) { \
		if(l < 16) { \
			l += 1; \
		} else { \
			l += 2; \
		} \
		(*((int*)y)) = 7 - l / 8; \
	} else { \
		if(l > 54) { \
			l += 2; \
		} else if(l > 47) {\
			l += 1; \
		} \
		(*((int*)y)) = l / 8; \
	} \
	if((l/8)%2) { \
		(*((int*)x)) = 1 - l % 8; \
	} else { \
		(*((int*)x)) = l % 8; \
	}

void gc(); // core coordinator routine
void gc_collect(); // core collector routine
void transferMarkResults(); 

#endif

