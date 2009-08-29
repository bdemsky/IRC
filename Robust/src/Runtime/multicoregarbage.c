#ifdef MULTICORE_GC
#include "runtime.h"
#include "multicoregarbage.h"
#include "multicoreruntime.h"
#include "runtime_arch.h"
#include "SimpleHash.h"
#include "GenericHashtable.h"
#include "ObjectHash.h"

extern int corenum;
extern struct parameterwrapper ** objectqueues[][NUMCLASSES];
extern int numqueues[][NUMCLASSES];

extern struct genhashtable * activetasks;
extern struct parameterwrapper ** objectqueues[][NUMCLASSES];
extern struct taskparamdescriptor *currtpd;

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

#define NUMLOBJPTRS 20

struct lobjpointerblock {
  void * lobjs[NUMLOBJPTRS];
	//void * dsts[NUMLOBJPTRS];
	int lengths[NUMLOBJPTRS];
	//void * origs[NUMLOBJPTRS];
	int hosts[NUMLOBJPTRS];
  struct lobjpointerblock *next;
};

struct lobjpointerblock *gclobjhead=NULL;
int gclobjheadindex=0;
struct lobjpointerblock *gclobjtail=NULL;
int gclobjtailindex=0;
struct lobjpointerblock *gclobjtail2=NULL;
int gclobjtailindex2=0;
struct lobjpointerblock *gclobjspare=NULL;

#ifdef GC_DEBUG
inline void dumpSMem() {
	int block = 0;
	int sblock = 0;
	int j = 0;
	int i = 0;
	bool reverse = false;
	int coren = 0;
	int x = 0;
	int y = 0;
	tprintf("Dump shared mem: \n");
	tprintf("++++ reserved sblocks ++++ \n");
	for(i=BAMBOO_BASE_VA; i<gcbaseva; i+= 4*16) {
		tprintf("0x%08x 0x%08x 0x%08x 0x%08x 0x%08x 0x%08x 0x%08x 0x%08x 0x%08x 0x%08x 0x%08x 0x%08x 0x%08x 0x%08x 0x%08x 0x%08x \n",
            *((int *)(i)), *((int *)(i + 4)), *((int *)(i + 4*2)), *((int *)(i + 4*3)), 
						*((int *)(i + 4*4)), *((int *)(i + 4*5)), *((int *)(i + 4*6)), *((int *)(i + 4*7)), 
						*((int *)(i + 4*8)), *((int *)(i + 4*9)), *((int *)(i + 4*10)), *((int *)(i + 4*11)),
						*((int *)(i + 4*12)), *((int *)(i + 4*13)), *((int *)(i + 4*14)), *((int *)(i + 4*15)));
	}
	sblock = gcreservedsb;
	bool advanceblock = false;
	for(i=gcbaseva;i<BAMBOO_BASE_VA+BAMBOO_SHARED_MEM_SIZE;i+=4*16){
		advanceblock = false;
		if(j%((BAMBOO_SMEM_SIZE)/(4*16)) == 0) {
			if(j < ((BAMBOO_LARGE_SMEM_BOUND)/(4*16))) {
				if((j > 0) && (j%((BAMBOO_SMEM_SIZE_L)/(4*16)) == 0)) {
					block++;
					advanceblock = true;
				}
			} else {
				block++;
				advanceblock = true;
			}
			if(advanceblock) {
				if(block%NUMCORES == 0) {
					reverse = !reverse;
				} else {
					if(((!reverse) && (y%2)) || (reverse && ((y%2)==0))) {
						if((x == 0) || ((x==1)&&(y==6))){
							if(reverse) {
								if(coren > 48) {
									coren -= bamboo_width - 1;
								} else {
									coren -= bamboo_width;
								}
							} else {
								if(coren > 47) {
									coren += bamboo_width - 1;
								} else {
									coren += bamboo_width;
								}
							}
						} else {
							coren--;
						}
					} else {
						if(x==7) {
							if(reverse) {
								if(coren > 48) {
									coren -= bamboo_width - 1;
								} else {
									coren -= bamboo_width;
								}
							} else {
								if(coren > 47) {
									coren += bamboo_width-1;
								} else {
									coren += bamboo_width;
								}
							}
						} else {
							coren++;
						}
					}
				}
			}
			int tmpcore = coren;
			if(NUMCORES==62) {
				if(tmpcore > 54) {
					tmpcore+=2;
				} else if(tmpcore > 47) {
					tmpcore++;
				}
			}
			x = tmpcore%bamboo_width;
			y = tmpcore/bamboo_width;
			tprintf("==== %d, %d : core (%d,%d), saddr %x====\n", block, sblock++, 
					    x, y, (sblock-1)*(BAMBOO_SMEM_SIZE)+BAMBOO_BASE_VA);
		}
		j++;
    tprintf("0x%08x 0x%08x 0x%08x 0x%08x 0x%08x 0x%08x 0x%08x 0x%08x 0x%08x 0x%08x 0x%08x 0x%08x 0x%08x 0x%08x 0x%08x 0x%08x \n",
            *((int *)(i)), *((int *)(i + 4)), *((int *)(i + 4*2)), *((int *)(i + 4*3)), 
						*((int *)(i + 4*4)), *((int *)(i + 4*5)), *((int *)(i + 4*6)), *((int *)(i + 4*7)), 
						*((int *)(i + 4*8)), *((int *)(i + 4*9)), *((int *)(i + 4*10)), *((int *)(i + 4*11)),
						*((int *)(i + 4*12)), *((int *)(i + 4*13)), *((int *)(i + 4*14)), *((int *)(i + 4*15)));
	}
	tprintf("\n");
}
#endif

// should be invoked with interruption closed
inline void gc_enqueue_I(void *ptr) {
#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xe601);
	BAMBOO_DEBUGPRINT_REG(ptr);
#endif
  if (gcheadindex==NUMPTRS) {
    struct pointerblock * tmp;
    if (gcspare!=NULL) {
      tmp=gcspare;
      gcspare=NULL;
    } else {
      tmp=RUNMALLOC_I(sizeof(struct pointerblock));
		} // if (gcspare!=NULL)
    gchead->next=tmp;
    gchead=tmp;
    gcheadindex=0;
  } // if (gcheadindex==NUMPTRS)
  gchead->ptrs[gcheadindex++]=ptr;
#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xe602);
#endif
} // void gc_enqueue_I(void *ptr)

// dequeue and destroy the queue
inline void * gc_dequeue() {
  if (gctailindex==NUMPTRS) {
    struct pointerblock *tmp=gctail;
    gctail=gctail->next;
    gctailindex=0;
    if (gcspare!=NULL) {
      RUNFREE(tmp);
		} else {
      gcspare=tmp;
		} // if (gcspare!=NULL)
  } // if (gctailindex==NUMPTRS)
  return gctail->ptrs[gctailindex++];
} // void * gc_dequeue()

// dequeue and do not destroy the queue
inline void * gc_dequeue2() {
	if (gctailindex2==NUMPTRS) {
    struct pointerblock *tmp=gctail2;
    gctail2=gctail2->next;
    gctailindex2=0;
  } // if (gctailindex2==NUMPTRS)
  return gctail2->ptrs[gctailindex2++];
} // void * gc_dequeue2() 

inline int gc_moreItems() {
  if ((gchead==gctail)&&(gctailindex==gcheadindex))
    return 0;
  return 1;
} // int gc_moreItems() 

inline int gc_moreItems2() {
  if ((gchead==gctail2)&&(gctailindex2==gcheadindex))
    return 0;
  return 1;
} // int gc_moreItems2()

// should be invoked with interruption closed
// enqueue a large obj: start addr & length
inline void gc_lobjenqueue_I(void *ptr, 
		                         int length, 
										         int host) {
#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xe901);
#endif
  if (gclobjheadindex==NUMLOBJPTRS) {
    struct lobjpointerblock * tmp;
    if (gclobjspare!=NULL) {
      tmp=gclobjspare;
      gclobjspare=NULL;
    } else {
      tmp=RUNMALLOC_I(sizeof(struct lobjpointerblock));
		} // if (gclobjspare!=NULL)
    gclobjhead->next=tmp;
    gclobjhead=tmp;
    gclobjheadindex=0;
  } // if (gclobjheadindex==NUMLOBJPTRS)
  gclobjhead->lobjs[gclobjheadindex]=ptr;
	gclobjhead->lengths[gclobjheadindex]=length;
	gclobjhead->hosts[gclobjheadindex++]=host;
#ifdef DEBUG
	BAMBOO_DEBUGPRINT_REG(gclobjhead->lobjs[gclobjheadindex-1]);
	BAMBOO_DEBUGPRINT_REG(gclobjhead->lengths[gclobjheadindex-1]);
	BAMBOO_DEBUGPRINT_REG(gclobjhead->hosts[gclobjheadindex-1]);
#endif
} // void gc_lobjenqueue_I(void *ptr...)

// dequeue and destroy the queue
inline void * gc_lobjdequeue(int * length,
		                         int * host) {
  if (gclobjtailindex==NUMLOBJPTRS) {
    struct lobjpointerblock *tmp=gclobjtail;
    gclobjtail=gclobjtail->next;
    gclobjtailindex=0;
    if (gclobjspare!=NULL) {
      RUNFREE(tmp);
		} else {
      gclobjspare=tmp;
		} // if (gclobjspare!=NULL)
  } // if (gclobjtailindex==NUMLOBJPTRS)
	if(length != NULL) {
		*length = gclobjtail->lengths[gclobjtailindex];
	}
	if(host != NULL) {
		*host = (int)(gclobjtail->hosts[gclobjtailindex]);
	}
  return gclobjtail->lobjs[gclobjtailindex++];
} // void * gc_lobjdequeue()

inline int gc_lobjmoreItems() {
  if ((gclobjhead==gclobjtail)&&(gclobjtailindex==gclobjheadindex))
    return 0;
  return 1;
} // int gc_lobjmoreItems()

// dequeue and don't destroy the queue
inline void gc_lobjdequeue2() {
  if (gclobjtailindex2==NUMLOBJPTRS) {
    gclobjtail2=gclobjtail2->next;
    gclobjtailindex2=1;
  } else {
		gclobjtailindex2++;
	}// if (gclobjtailindex2==NUMLOBJPTRS)
} // void * gc_lobjdequeue2()

inline int gc_lobjmoreItems2() {
  if ((gclobjhead==gclobjtail2)&&(gclobjtailindex2==gclobjheadindex))
    return 0;
  return 1;
} // int gc_lobjmoreItems2()

INTPTR gccurr_heapbound = 0;

inline void gettype_size(void * ptr, 
		                     int * ttype, 
							       		 int * tsize) {
	int type = ((int *)ptr)[0];
	int size = 0;
	if(type < NUMCLASSES) {
		// a normal object
		size = classsize[type];
	} else {	
		// an array 
		struct ArrayObject *ao=(struct ArrayObject *)ptr;
		int elementsize=classsize[type];
		int length=ao->___length___; 
		size=sizeof(struct ArrayObject)+length*elementsize;
	} // if(type < NUMCLASSES)
	*ttype = type;
	*tsize = size;
}

inline bool isLarge(void * ptr, 
		                int * ttype, 
										int * tsize) {
#ifdef DEBUG
		BAMBOO_DEBUGPRINT(0xe701);
		BAMBOO_DEBUGPRINT_REG(ptr);
#endif
	// check if a pointer is referring to a large object
	gettype_size(ptr, ttype, tsize);
#ifdef DEBUG
	BAMBOO_DEBUGPRINT(*tsize);
#endif
	int bound = (BAMBOO_SMEM_SIZE);
	if(((int)ptr-gcbaseva) < (BAMBOO_LARGE_SMEM_BOUND)) {
		bound = (BAMBOO_SMEM_SIZE_L);
	}
	if((((int)ptr-gcbaseva)%(bound))==0) {
		// ptr is a start of a block
#ifdef DEBUG
		BAMBOO_DEBUGPRINT(0xe702);
		BAMBOO_DEBUGPRINT(1);
#endif
		return true;
	}
	if((bound-(((int)ptr-gcbaseva)%bound)) < (*tsize)) {
		// it acrosses the boundary of current block
#ifdef DEBUG
		BAMBOO_DEBUGPRINT(0xe703);
		BAMBOO_DEBUGPRINT(1);
#endif
		return true;
	}
#ifdef DEBUG
		BAMBOO_DEBUGPRINT(0);
#endif
	return false;
} // bool isLarge(void * ptr, int * ttype, int * tsize)

inline int hostcore(void * ptr) {
	// check the host core of ptr
	int host = 0;
	int x = 0;
	int y = 0;
	RESIDECORE(ptr, &x, &y);
	host = (x==0)?(x*bamboo_height+y):(x*bamboo_height+y-2);
	return host;
} // int hostcore(void * ptr)

inline bool isLocal(void * ptr) {
	// check if a pointer is in shared heap on this core
	return hostcore(ptr) == BAMBOO_NUM_OF_CORE;
} // bool isLocal(void * ptr)

inline bool gc_checkCoreStatus() {
	bool allStall = true;
	for(int i = 0; i < NUMCORES; ++i) {
		if(gccorestatus[i] != 0) {
			allStall = false;
			break;
		} // if(gccorestatus[i] != 0)
	} // for(i = 0; i < NUMCORES; ++i)
	return allStall;
}

inline void checkMarkStatue() {
#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xee01);
#endif
	int i;
	if((!waitconfirm) || 
			(waitconfirm && (numconfirm == 0))) {
#ifdef DEBUG
		BAMBOO_DEBUGPRINT(0xee02);
#endif
		BAMBOO_START_CRITICAL_SECTION_STATUS();  
		gccorestatus[BAMBOO_NUM_OF_CORE] = 0;
		gcnumsendobjs[BAMBOO_NUM_OF_CORE] = gcself_numsendobjs;
		gcnumreceiveobjs[BAMBOO_NUM_OF_CORE] = gcself_numreceiveobjs;
		// check the status of all cores
		bool allStall = gc_checkCoreStatus();
#ifdef DEBUG
		BAMBOO_DEBUGPRINT(0xee03);
#endif
		if(allStall) {
#ifdef DEBUG
			BAMBOO_DEBUGPRINT(0xee04);
#endif
			// check if the sum of send objs and receive obj are the same
			// yes->check if the info is the latest; no->go on executing
			int sumsendobj = 0;
			for(i = 0; i < NUMCORES; ++i) {
				sumsendobj += gcnumsendobjs[i];
			} // for(i = 0; i < NUMCORES; ++i) 
#ifdef DEBUG
			BAMBOO_DEBUGPRINT(0xee05);
			BAMBOO_DEBUGPRINT_REG(sumsendobj);
#endif
			for(i = 0; i < NUMCORES; ++i) {
				sumsendobj -= gcnumreceiveobjs[i];
			} // for(i = 0; i < NUMCORES; ++i) 
#ifdef DEBUG
			BAMBOO_DEBUGPRINT(0xee06);
			BAMBOO_DEBUGPRINT_REG(sumsendobj);
#endif
			if(0 == sumsendobj) {
#ifdef DEBUG
				BAMBOO_DEBUGPRINT(0xee07);
#endif
				if(!waitconfirm) {
#ifdef DEBUG
					BAMBOO_DEBUGPRINT(0xee08);
#endif
					// the first time found all cores stall
					// send out status confirm msg to all other cores
					// reset the corestatus array too
					gccorestatus[BAMBOO_NUM_OF_CORE] = 1;
					waitconfirm = true;
					numconfirm = NUMCORES - 1;
					for(i = 1; i < NUMCORES; ++i) {	
						gccorestatus[i] = 1;
						// send mark phase finish confirm request msg to core i
						send_msg_1(i, GCMARKCONFIRM);
					} // for(i = 1; i < NUMCORES; ++i) 
				} else {
#ifdef DEBUG
					BAMBOO_DEBUGPRINT(0xee09);
#endif
					// all the core status info are the latest
					// stop mark phase
					gcphase = COMPACTPHASE;
					// restore the gcstatus for all cores
					for(i = 0; i < NUMCORES; ++i) {
						gccorestatus[i] = 1;
					} // for(i = 0; i < NUMCORES; ++i)
				} // if(!gcwautconfirm) else()
			} // if(0 == sumsendobj)
		} // if(allStall)
		BAMBOO_CLOSE_CRITICAL_SECTION_STATUS();
	} // if((!waitconfirm)...
#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xee0a);
#endif
} // void checkMarkStatue()

inline bool preGC() {
	// preparation for gc
	// make sure to clear all incoming msgs espacially transfer obj msgs
#ifdef GC_DEBUG
	BAMBOO_DEBUGPRINT(0xec01);
#endif
	int i;
	if((!waitconfirm) || 
						  (waitconfirm && (numconfirm == 0))) {
		// send out status confirm msgs to all cores to check if there are
		// transfer obj msgs on-the-fly
		waitconfirm = true;
		numconfirm = NUMCORES - 1;
		for(i = 1; i < NUMCORES; ++i) {	
			corestatus[i] = 1;
			// send status confirm msg to core i
			send_msg_1(i, STATUSCONFIRM);
		} // for(i = 1; i < NUMCORES; ++i)

#ifdef GC_DEBUG
		BAMBOO_DEBUGPRINT(0xec02);
#endif
		while(numconfirm != 0) {} // wait for confirmations
		waitconfirm = false;
		numconfirm = 0;
#ifdef DEBUG
		BAMBOO_DEBUGPRINT(0xec03);
#endif
		numsendobjs[BAMBOO_NUM_OF_CORE] = self_numsendobjs;
		numreceiveobjs[BAMBOO_NUM_OF_CORE] = self_numreceiveobjs;
		int sumsendobj = 0;
#ifdef DEBUG
		BAMBOO_DEBUGPRINT(0xec04);
#endif
		for(i = 0; i < NUMCORES; ++i) {
			sumsendobj += numsendobjs[i];
#ifdef DEBUG
			BAMBOO_DEBUGPRINT(0xf000 + numsendobjs[i]);
#endif
		} // for(i = 1; i < NUMCORES; ++i)
#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xec05);
	BAMBOO_DEBUGPRINT_REG(sumsendobj);
#endif
		for(i = 0; i < NUMCORES; ++i) {
			sumsendobj -= numreceiveobjs[i];
#ifdef DEBUG
			BAMBOO_DEBUGPRINT(0xf000 + numreceiveobjs[i]);
#endif
		} // for(i = 1; i < NUMCORES; ++i)
#ifdef DEBUG
		BAMBOO_DEBUGPRINT(0xec06);
		BAMBOO_DEBUGPRINT_REG(sumsendobj);
#endif
		if(0 == sumsendobj) {
			return true;
		} else {
			// still have some transfer obj msgs on-the-fly, can not start gc
			return false;
		} // if(0 == sumsendobj) 
	} else {
#ifdef DEBUG
		BAMBOO_DEBUGPRINT(0xec07);
#endif
		// previously asked for status confirmation and do not have all the 
		// confirmations yet, can not start gc
		return false;
	} // if((!waitconfirm) || 
} // bool preGC()

inline void initGC() {
	int i;
	if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
		for(i = 0; i < NUMCORES; ++i) {
			gccorestatus[i] = 1;
			gcnumsendobjs[i] = 0; 
			gcnumreceiveobjs[i] = 0;
			gcloads[i] = 0;
			gcrequiredmems[i] = 0;
			gcfilledblocks[i] = 0;
			gcstopblock[i] = 0;
		} // for(i = 0; i < NUMCORES; ++i)
		gcheaptop = 0;
		gctopcore = 0;
		gcheapdirection = 1;
	}
	gcself_numsendobjs = 0;
	gcself_numreceiveobjs = 0;
	gcmarkedptrbound = 0;
	gcobj2map = 0;
	gcmappedobj = 0;
	gcismapped = false;
	gcnumlobjs = 0;
	gcmovestartaddr = 0;
	gctomove = false;
	gcblock2fill = 0;
	gcmovepending = 0;
	gccurr_heaptop = 0;
	gcdstcore = 0;

	// initialize queue
	if (gchead==NULL) {
		gcheadindex=gctailindex=gctailindex2 = 0;
		gchead=gctail=gctail2=RUNMALLOC(sizeof(struct pointerblock));
	} else {
		gctailindex = gctailindex2 = gcheadindex;
		gctail = gctail2 = gchead;
	}

	// initialize the large obj queues
	if (gclobjhead==NULL) {
		gclobjheadindex=0;
		gclobjtailindex=0;
		gclobjtailindex2 = 0;
		gclobjhead=gclobjtail=gclobjtail2=
			RUNMALLOC(sizeof(struct lobjpointerblock));
	} else {
		gclobjtailindex = gclobjtailindex2 = gclobjheadindex;
		gclobjtail = gclobjtail2 = gclobjhead;
	}

	freeRuntimeHash(gcpointertbl);
	gcpointertbl = allocateRuntimeHash(20);
} // void initGC()

// compute load balance for all cores
inline int loadbalance() {
	// compute load balance
	int i;

	// get the total loads
	int tloads = gcloads[STARTUPCORE];
	for(i = 1; i < NUMCORES; i++) {
		tloads += gcloads[i];
	}
	int heaptop = gcbaseva + tloads;
#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xdddd);
	BAMBOO_DEBUGPRINT_REG(tloads);
	BAMBOO_DEBUGPRINT_REG(heaptop);
#endif
	int b = 0;
	BLOCKINDEX(heaptop, &b);
	int numbpc = b / NUMCORES; // num of blocks per core
#ifdef DEBUG
	BAMBOO_DEBUGPRINT_REG(b);
	BAMBOO_DEBUGPRINT_REG(numbpc);
#endif
	gcheapdirection = (numbpc%2 == 0);
	int x = 0;
	int y = 0;
	RESIDECORE(heaptop, &x, &y);
	gctopcore = (x == 0 ? y : x * bamboo_height + y - 2);
#ifdef DEBUG
	BAMBOO_DEBUGPRINT_REG(gctopcore);
#endif
	return numbpc;
} // void loadbalance()

inline bool cacheLObjs() {
	// check the total mem size need for large objs
	int sumsize = 0;
	int size = 0;
#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xe801);
#endif
	gclobjtail2 = gclobjtail;
	gclobjtailindex2 = gclobjtailindex;
	while(gc_lobjmoreItems2()){
		gc_lobjdequeue2();
		size = gclobjtail2->lengths[gclobjtailindex2 - 1];
		sumsize += size;
#ifdef DEBUG
		BAMBOO_DEBUGPRINT_REG(size);
		BAMBOO_DEBUGPRINT_REG(sumsize);
#endif
	} // while(gc_lobjmoreItems2())

	// check if there are enough space to cache these large objs
	INTPTR dst = (BAMBOO_BASE_VA) + (BAMBOO_SHARED_MEM_SIZE) - sumsize;
	if(gcheaptop > dst) {
		// do not have enough room to cache large objs
		return false;
	}
#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xe802);
	BAMBOO_DEBUGPRINT_REG(dst);
#endif

	gcheaptop = dst; // Note: record the start of cached lobjs with gcheaptop
	// cache the largeObjs to the top of the shared heap
	gclobjtail2 = gclobjtail;
	gclobjtailindex2 = gclobjtailindex;
	while(gc_lobjmoreItems2()) {
		gc_lobjdequeue2();
		size = gclobjtail2->lengths[gclobjtailindex2 - 1];
		// set the mark field to 2, indicating that this obj has been moved and need to be flushed
		((int *)(gclobjtail2->lobjs[gclobjtailindex2-1]))[6] = 2;
		memcpy(dst, gclobjtail2->lobjs[gclobjtailindex2 - 1], size);
		dst += size;
#ifdef DEBUG
		BAMBOO_DEBUGPRINT_REG(gclobjtail2->lobjs[gclobjtailindex2-1]);
		BAMBOO_DEBUGPRINT(dst-size);
		BAMBOO_DEBUGPRINT_REG(size);
#endif
	}
	return true;
} // void cacheLObjs()

inline void moveLObjs() {
#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xea01);
#endif
	// find current heap top
	// flush all gcloads to indicate the real heap top on one core
	// previous it represents the next available ptr on a core
	if((gcloads[0] > (gcbaseva+(BAMBOO_SMEM_SIZE_L))) 
			&& ((gcloads[0]%(BAMBOO_SMEM_SIZE)) == 0)) {
		// edge of a block, check if this is exactly the heaptop
		BASEPTR(0, gcfilledblocks[0]-1, &(gcloads[0]));
		gcloads[0]+=(gcfilledblocks[0]>1?(BAMBOO_SMEM_SIZE):(BAMBOO_SMEM_SIZE_L));
	}
	int tmpheaptop = gcloads[0];
#ifdef DEBUG
	BAMBOO_DEBUGPRINT_REG(tmpheaptop);
#endif
	for(int i = 1; i < NUMCORES; i++) {
		if((gcloads[i] > (gcbaseva+(BAMBOO_SMEM_SIZE_L))) 
				&& ((gcloads[i] % (BAMBOO_SMEM_SIZE)) == 0)) {
			// edge of a block, check if this is exactly the heaptop
			BASEPTR(0, gcfilledblocks[i]-1, &gcloads[i]);
			gcloads[i]
				+=(gcfilledblocks[i]>1?(BAMBOO_SMEM_SIZE):(BAMBOO_SMEM_SIZE_L));
		}
		if(tmpheaptop < gcloads[i]) {
			tmpheaptop = gcloads[i];
		}
#ifdef DEBUG
		BAMBOO_DEBUGPRINT_REG(gcloads[i]);
		BAMBOO_DEBUGPRINT_REG(tmpheaptop);
#endif
	}
	// move large objs from gcheaptop to tmpheaptop
	// write the header first
	int tomove = (BAMBOO_BASE_VA) + (BAMBOO_SHARED_MEM_SIZE) - gcheaptop;
#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xea02);
	BAMBOO_DEBUGPRINT_REG(tomove);
#endif
	// flush the sbstartbl
	memset(&(gcsbstarttbl[gcreservedsb]), '\0', 
			   BAMBOO_SHARED_MEM_SIZE/BAMBOO_SMEM_SIZE*sizeof(INTPTR));
	if(tomove == 0) {
		gcheaptop = tmpheaptop;
		return;
	}
	// check how many blocks it acrosses
	int remain = tmpheaptop-gcbaseva;
	int b = remain/(BAMBOO_SMEM_SIZE) + gcreservedsb;
	// check the remaining space in this block
	int bound = (BAMBOO_SMEM_SIZE);
	if(remain < (BAMBOO_LARGE_SMEM_BOUND)) {
		bound = (BAMBOO_SMEM_SIZE_L);
	}
	remain = bound - remain%bound;

#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xea03);
#endif
	int size = 0;
	int isize = 0;
	int host = 0;
	int ptr = 0;
	int base = tmpheaptop;
	int cpysize = 0;
	remain -= BAMBOO_CACHE_LINE_SIZE;
	tmpheaptop += BAMBOO_CACHE_LINE_SIZE;
	while(gc_lobjmoreItems()) {
		ptr = (int)(gc_lobjdequeue(&size, &host));
		ALIGNSIZE(size, &isize);
		if(remain < isize) {
			// this object acrosses blocks
			if(cpysize > 0) {
				// close current block, fill its header
				memset(base, '\0', BAMBOO_CACHE_LINE_SIZE);
				*((int*)base) = cpysize + BAMBOO_CACHE_LINE_SIZE;
				cpysize = 0;
				base = tmpheaptop;
				if(remain == 0) {
					remain = ((tmpheaptop-gcbaseva)<(BAMBOO_LARGE_SMEM_BOUND)) ? 
						       BAMBOO_SMEM_SIZE_L : BAMBOO_SMEM_SIZE;
				} 
				remain -= BAMBOO_CACHE_LINE_SIZE;
				tmpheaptop += BAMBOO_CACHE_LINE_SIZE;
			} // if(cpysize > 0)

			// move the large obj
			memcpy(tmpheaptop, gcheaptop, size);
			// fill the remaining space with -2 padding
			memset(tmpheaptop+size, -2, isize-size);
#ifdef DEBUG
			BAMBOO_DEBUGPRINT(0xea04);
			BAMBOO_DEBUGPRINT_REG(gcheaptop);
			BAMBOO_DEBUGPRINT_REG(tmpheaptop);
			BAMBOO_DEBUGPRINT_REG(size);
			BAMBOO_DEBUGPRINT_REG(isize);
#endif
			gcheaptop += size;
			if(host == BAMBOO_NUM_OF_CORE) {
				BAMBOO_START_CRITICAL_SECTION();
				RuntimeHashadd_I(gcpointertbl, ptr, tmpheaptop);
				BAMBOO_CLOSE_CRITICAL_SECTION();
#ifdef DEBUG
				BAMBOO_DEBUGPRINT(0xcdcd);
				BAMBOO_DEBUGPRINT_REG(ptr);
				BAMBOO_DEBUGPRINT_REG(tmpheaptop);
#endif
			} else {
				// send the original host core with the mapping info
				send_msg_3(host, GCLOBJMAPPING, ptr, tmpheaptop);
#ifdef DEBUG
				BAMBOO_DEBUGPRINT(0xcdcd);
				BAMBOO_DEBUGPRINT_REG(ptr);
				BAMBOO_DEBUGPRINT_REG(tmpheaptop);
#endif
			} // if(host == BAMBOO_NUM_OF_CORE) else ...
			tmpheaptop += isize;

			// set the gcsbstarttbl
			int tmpsbs = 1+(isize-remain-1)/BAMBOO_SMEM_SIZE;
			for(int k = 1; k < tmpsbs; k++) {
				gcsbstarttbl[b+k] = (INTPTR)(-1);
			}
			b += tmpsbs;
			if(((isize-remain)%(BAMBOO_SMEM_SIZE)) == 0) {
				gcsbstarttbl[b] = (INTPTR)(-1);
				remain = ((tmpheaptop-gcbaseva)<(BAMBOO_LARGE_SMEM_BOUND)) ? 
						     BAMBOO_SMEM_SIZE_L : BAMBOO_SMEM_SIZE;
			} else {
				gcsbstarttbl[b] = (INTPTR)(tmpheaptop);
				remain = tmpheaptop-gcbaseva;
				int bound = remain<(BAMBOO_LARGE_SMEM_BOUND)?(BAMBOO_SMEM_SIZE_L):(BAMBOO_SMEM_SIZE);
				remain = bound - remain%bound;
			} // if(((isize-remain)%(BAMBOO_SMEM_SIZE)) == 0) else ...

			// close current block and fill the header
			memset(base, '\0', BAMBOO_CACHE_LINE_SIZE);
			*((int*)base) = isize + BAMBOO_CACHE_LINE_SIZE;
			cpysize = 0;
			base = tmpheaptop;
			remain -= BAMBOO_CACHE_LINE_SIZE;
			tmpheaptop += BAMBOO_CACHE_LINE_SIZE;
		} else {
			remain -= isize;
			// move the large obj
			memcpy(tmpheaptop, gcheaptop, size);
			// fill the remaining space with -2 padding
			memset(tmpheaptop+size, -2, isize-size);
#ifdef DEBUG
			BAMBOO_DEBUGPRINT(0xea05);
			BAMBOO_DEBUGPRINT_REG(gcheaptop);
			BAMBOO_DEBUGPRINT_REG(tmpheaptop);
			BAMBOO_DEBUGPRINT_REG(size);
			BAMBOO_DEBUGPRINT_REG(isize);
#endif
			gcheaptop += size;
			cpysize += isize;
			if(host == BAMBOO_NUM_OF_CORE) {
				BAMBOO_START_CRITICAL_SECTION();
				RuntimeHashadd_I(gcpointertbl, ptr, tmpheaptop);
				BAMBOO_CLOSE_CRITICAL_SECTION();
#ifdef DEBUG
				BAMBOO_DEBUGPRINT(0xcdcd);
				BAMBOO_DEBUGPRINT_REG(ptr);
				BAMBOO_DEBUGPRINT_REG(tmpheaptop);
#endif
			} else {
				// send the original host core with the mapping info
				send_msg_3(host, GCLOBJMAPPING, ptr, tmpheaptop);
#ifdef DEBUG
				BAMBOO_DEBUGPRINT(0xcdcd);
				BAMBOO_DEBUGPRINT_REG(ptr);
				BAMBOO_DEBUGPRINT_REG(tmpheaptop);
#endif
			} // if(host == BAMBOO_NUM_OF_CORE) else ...
			tmpheaptop += isize;
		} // if(remain < isize) else ...
	} // while(gc_lobjmoreItems())
	if(cpysize > 0) {
		// close current block, fill the header
		memset(base, '\0', BAMBOO_CACHE_LINE_SIZE);
		*((int*)base) = cpysize + BAMBOO_CACHE_LINE_SIZE;
	} else {
		tmpheaptop -= BAMBOO_CACHE_LINE_SIZE;
	}
	gcheaptop = tmpheaptop;
#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xea06);
	BAMBOO_DEBUGPRINT_REG(gcheaptop);
#endif
} // void moveLObjs()

inline void updateFreeMemList() {
	struct freeMemItem * tochange = bamboo_free_mem_list->head;
	if(tochange == NULL) {
		bamboo_free_mem_list->head = tochange = 
			(struct freeMemItem *)RUNMALLOC(sizeof(struct freeMemItem));
	}
	// handle the top of the heap
	tochange->ptr = gcheaptop;
	tochange->size = BAMBOO_SHARED_MEM_SIZE + BAMBOO_BASE_VA - gcheaptop;
	// zero out all these spare memory
	memset(tochange->ptr, '\0', tochange->size);
	if(bamboo_free_mem_list->tail != tochange) {
		bamboo_free_mem_list->tail = tochange;
		if(bamboo_free_mem_list->tail != NULL) {
			RUNFREE(bamboo_free_mem_list->tail);
		}
	}
} // void updateFreeMemList()

// enqueue root objs
inline void tomark(struct garbagelist * stackptr) {
	if(MARKPHASE != gcphase) {
		BAMBOO_EXIT(0xb101);
	}
	gcbusystatus = true;
	gcnumlobjs = 0;
	
	int i,j;
	// enqueue current stack 
	while(stackptr!=NULL) {
#ifdef DEBUG
		BAMBOO_DEBUGPRINT(0xe501);
		BAMBOO_DEBUGPRINT_REG(stackptr->size);
		BAMBOO_DEBUGPRINT_REG(stackptr->next);
		BAMBOO_DEBUGPRINT_REG(stackptr->array[0]);
#endif
		for(i=0; i<stackptr->size; i++) {
			if(stackptr->array[i] != NULL) {
				BAMBOO_START_CRITICAL_SECTION();
				gc_enqueue_I(stackptr->array[i]);
				BAMBOO_CLOSE_CRITICAL_SECTION();
			}
		}
		stackptr=stackptr->next;
	}

#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xe503);
#endif
	// enqueue objectsets
	for(i=0; i<NUMCLASSES; i++) {
		struct parameterwrapper ** queues = 
			objectqueues[BAMBOO_NUM_OF_CORE][i];
		int length = numqueues[BAMBOO_NUM_OF_CORE][i];
		for(j = 0; j < length; ++j) {
			struct parameterwrapper * parameter = queues[j];
			struct ObjectHash * set=parameter->objectset;
			struct ObjectNode * ptr=set->listhead;
			while(ptr!=NULL) {
				BAMBOO_START_CRITICAL_SECTION();
				gc_enqueue_I((void *)ptr->key);
				BAMBOO_CLOSE_CRITICAL_SECTION();
				ptr=ptr->lnext;
			}
		}
	}

	// euqueue current task descriptor
	if(currtpd != NULL) {
#ifdef DEBUG
		BAMBOO_DEBUGPRINT(0xe504);
#endif
		for(i=0; i<currtpd->numParameters; i++) {
			BAMBOO_START_CRITICAL_SECTION();
			gc_enqueue_I(currtpd->parameterArray[i]);
			BAMBOO_CLOSE_CRITICAL_SECTION();
		}
	}

#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xe505);
#endif
	// euqueue active tasks
	struct genpointerlist * ptr=activetasks->list;
	while(ptr!=NULL) {
		struct taskparamdescriptor *tpd=ptr->src;
		int i;
		for(i=0; i<tpd->numParameters; i++) {
			BAMBOO_START_CRITICAL_SECTION();
			gc_enqueue_I(tpd->parameterArray[i]);
			BAMBOO_CLOSE_CRITICAL_SECTION();
		}
		ptr=ptr->inext;
	}

#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xe506);
#endif
	// enqueue cached transferred obj
	struct QueueItem * tmpobjptr =  getHead(&objqueue);
	while(tmpobjptr != NULL) {
		struct transObjInfo * objInfo = 
			(struct transObjInfo *)(tmpobjptr->objectptr); 
		BAMBOO_START_CRITICAL_SECTION();
		gc_enqueue_I(objInfo->objptr);
		BAMBOO_CLOSE_CRITICAL_SECTION();
		tmpobjptr = getNextQueueItem(tmpobjptr);
	}

#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xe507);
#endif
	// enqueue cached objs to be transferred
	struct QueueItem * item = getHead(totransobjqueue);
	while(item != NULL) {
		struct transObjInfo * totransobj = 
			(struct transObjInfo *)(item->objectptr);
		BAMBOO_START_CRITICAL_SECTION();
		gc_enqueue_I(totransobj->objptr);
		BAMBOO_CLOSE_CRITICAL_SECTION();
		item = getNextQueueItem(item);
	} // while(item != NULL)
} // void tomark(struct garbagelist * stackptr)

inline void markObj(void * objptr) {
	if(objptr == NULL) {
		return;
	}
	if(ISSHAREDOBJ(objptr)) {
		int host = hostcore(objptr);
		if(BAMBOO_NUM_OF_CORE == host) {
			// on this core
			BAMBOO_START_CRITICAL_SECTION();
			gc_enqueue_I(objptr);  
			BAMBOO_CLOSE_CRITICAL_SECTION();
		} else {
#ifdef DEBUG
			BAMBOO_DEBUGPRINT(0xbbbb);
			BAMBOO_DEBUGPRINT_REG(host);
			BAMBOO_DEBUGPRINT_REG(objptr);
#endif
			// send a msg to host informing that objptr is active
			send_msg_2(host, GCMARKEDOBJ, objptr);
			gcself_numsendobjs++;
		}
	} else {
		BAMBOO_START_CRITICAL_SECTION();
		gc_enqueue_I(objptr);
		BAMBOO_CLOSE_CRITICAL_SECTION();
	} // if(ISSHAREDOBJ(objptr))
} // void markObj(void * objptr) 

inline void mark(bool isfirst, 
		             struct garbagelist * stackptr) {
#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xed01);
#endif
	if(isfirst) {
#ifdef DEBUG
		BAMBOO_DEBUGPRINT(0xed02);
#endif
		// enqueue root objs
		tomark(stackptr);
		gccurr_heaptop = 0; // record the size of all active objs in this core
		                  // aligned but does not consider block boundaries
		gcmarkedptrbound = 0;
	}
#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xed03);
#endif
	int isize = 0;
	bool checkfield = true;
	bool sendStall = false;
	// mark phase
	while(MARKPHASE == gcphase) {
#ifdef DEBUG
		BAMBOO_DEBUGPRINT(0xed04);
#endif
		while(gc_moreItems2()) {
#ifdef DEBUG
			BAMBOO_DEBUGPRINT(0xed05);
#endif
			sendStall = false;
			gcbusystatus = true;
			checkfield = true;
			void * ptr = gc_dequeue2();
#ifdef DEBUG
			BAMBOO_DEBUGPRINT_REG(ptr);
#endif
			int size = 0;
			int isize = 0;
			int type = 0;
			// check if it is a shared obj
			if(ISSHAREDOBJ(ptr)) {
				// a shared obj, check if it is a local obj on this core
				if(isLarge(ptr, &type, &size)) {
					// ptr is a large object
					if(((int *)ptr)[6] == 0) {
						// not marked and not enqueued
						BAMBOO_START_CRITICAL_SECTION();
						gc_lobjenqueue_I(ptr, size, BAMBOO_NUM_OF_CORE);
						gcnumlobjs++;
						BAMBOO_CLOSE_CRITICAL_SECTION();
						// mark this obj
						((int *)ptr)[6] = 1;
					}
				} else {
					bool islocal = isLocal(ptr);
					if (islocal && (((int *)ptr)[6] == 0)) {
						// ptr is an unmarked active object on this core
						ALIGNSIZE(size, &isize);
						gccurr_heaptop += isize;
#ifdef DEBUG
						BAMBOO_DEBUGPRINT(0xaaaa);
						BAMBOO_DEBUGPRINT_REG(ptr);
						BAMBOO_DEBUGPRINT_REG(isize);
#endif
						// mark this obj
						((int *)ptr)[6] = 1;
						if(ptr + size > gcmarkedptrbound) {
							gcmarkedptrbound = ptr + size;
						} // if(ptr + size > gcmarkedptrbound)
					} else if ((!islocal) && (((int *)ptr)[6] == 0)) {
						int host = hostcore(ptr);
#ifdef DEBUG
						BAMBOO_DEBUGPRINT(0xbbbb);
						BAMBOO_DEBUGPRINT_REG(host);
						BAMBOO_DEBUGPRINT_REG(ptr);
#endif
						// send a msg to host informing that ptr is active
						send_msg_2(host, GCMARKEDOBJ, ptr);
						gcself_numsendobjs++;
						checkfield = false;
					}// if(isLocal(ptr)) else ...
				} // if(isLarge(ptr, &type, &size)) else ...
			} // if(ISSHAREDOBJ(ptr))
#ifdef DEBUG
			BAMBOO_DEBUGPRINT(0xed06);
#endif

			if(checkfield) {
				// scan all pointers in ptr
				unsigned INTPTR * pointer;
				pointer=pointerarray[type];
				if (pointer==0) {
					/* Array of primitives */
					/* Do nothing */
				} else if (((INTPTR)pointer)==1) {
					/* Array of pointers */
					struct ArrayObject *ao=(struct ArrayObject *) ptr;
					int length=ao->___length___;
					int j;
					for(j=0; j<length; j++) {
						void *objptr = 
							((void **)(((char *)&ao->___length___)+sizeof(int)))[j];
						markObj(objptr);
					}
				} else {
					INTPTR size=pointer[0];
					int i;
					for(i=1; i<=size; i++) {
						unsigned int offset=pointer[i];
						void * objptr=*((void **)(((char *)ptr)+offset));
						markObj(objptr);
					}
				} // if (pointer==0) else if ... else ...
			} // if(checkfield)
		} // while(!isEmpty(gctomark))
#ifdef DEBUG
		BAMBOO_DEBUGPRINT(0xed07);
#endif
		gcbusystatus = false;
		// send mark finish msg to core coordinator
		if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
#ifdef DEBUG
			BAMBOO_DEBUGPRINT(0xed08);
#endif
			gccorestatus[BAMBOO_NUM_OF_CORE] = 0;
			gcnumsendobjs[BAMBOO_NUM_OF_CORE] = gcself_numsendobjs;
			gcnumreceiveobjs[BAMBOO_NUM_OF_CORE] = gcself_numreceiveobjs;
			gcloads[BAMBOO_NUM_OF_CORE] = gccurr_heaptop;
		} else {
			if(!sendStall) {
#ifdef DEBUG
				BAMBOO_DEBUGPRINT(0xed09);
#endif
				send_msg_4(STARTUPCORE, GCFINISHMARK, BAMBOO_NUM_OF_CORE,
									 gcself_numsendobjs, gcself_numreceiveobjs);
				sendStall = true;
			}
		}
#ifdef DEBUG
		BAMBOO_DEBUGPRINT(0xed0a);
#endif

		if(BAMBOO_NUM_OF_CORE == 0) {
#ifdef DEBUG
			BAMBOO_DEBUGPRINT(0xed0b);
#endif
			return;
		}
	} // while(MARKPHASE == gcphase)
} // mark()

inline void compact2Heaptop() {
	// no cores with spare mem and some cores are blocked with pending move
	// find the current heap top and make them move to the heap top
	int p;
	int numblocks = gcfilledblocks[gctopcore];
	BASEPTR(gctopcore, numblocks, &p);
	int b;
	BLOCKINDEX(p, &b);
	int remain = b<NUMCORES ? BAMBOO_SMEM_SIZE_L : BAMBOO_SMEM_SIZE;
#ifdef GC_DEBUG
	BAMBOO_DEBUGPRINT(0xd101);
	BAMBOO_DEBUGPRINT_REG(gctopcore);
	BAMBOO_DEBUGPRINT_REG(p);
	BAMBOO_DEBUGPRINT_REG(b);
	BAMBOO_DEBUGPRINT_REG(remain);
#endif
	/*if((gctopcore == STARTUPCORE) && (b == 0)) {
		remain -= gcreservedsb*BAMBOO_SMEM_SIZE;
		p += gcreservedsb*BAMBOO_SMEM_SIZE;
	}*/
	for(int i = 0; i < NUMCORES; i++) {
		BAMBOO_START_CRITICAL_SECTION();
		if((gccorestatus[i] != 0) && (gcrequiredmems[i] > 0)) {
#ifdef GC_DEBUG
			BAMBOO_DEBUGPRINT(0xd102);
#endif
			int memneed = gcrequiredmems[i] + BAMBOO_CACHE_LINE_SIZE;
			if(STARTUPCORE == i) {
				gctomove = true;
				gcmovestartaddr = p;
				gcdstcore = gctopcore;
				gcblock2fill = numblocks + 1;
			} else {
				send_msg_4(i, GCMOVESTART, gctopcore, p, numblocks + 1);
			}
#ifdef GC_DEBUG
			BAMBOO_DEBUGPRINT_REG(i);
			BAMBOO_DEBUGPRINT_REG(gctopcore);
			BAMBOO_DEBUGPRINT_REG(p);
			BAMBOO_DEBUGPRINT_REG(numblocks+1);
#endif
			if(memneed < remain) {
#ifdef GC_DEBUG
				BAMBOO_DEBUGPRINT(0xd103);
#endif
				p += memneed;
				gcrequiredmems[i] = 0;
				gcmovepending--;
				gcloads[gctopcore] += memneed;
			} else {
#ifdef GC_DEBUG
				BAMBOO_DEBUGPRINT(0xd104);
#endif
				// next available block
				p += remain;
				gcfilledblocks[gctopcore] += 1;
				int newbase = 0;
				BASEPTR(gctopcore, gcfilledblocks[gctopcore], &newbase);
				gcloads[gctopcore] = newbase;
				gcrequiredmems[i] -= remain - BAMBOO_CACHE_LINE_SIZE;
				gcstopblock[gctopcore]++;
				gctopcore = nextTopcore(gctopcore, gcheapdirection);
				numblocks = gcstopblock[gctopcore];
				BASEPTR(gctopcore, numblocks, &p);
				BLOCKINDEX(p, &b);
				remain = (b<NUMCORES) ? (BAMBOO_SMEM_SIZE_L) : (BAMBOO_SMEM_SIZE);
#ifdef GC_DEBUG
				BAMBOO_DEBUGPRINT(0xd105);
				BAMBOO_DEBUGPRINT_REG(gctopcore);
				BAMBOO_DEBUGPRINT_REG(p);
				BAMBOO_DEBUGPRINT_REG(b);
				BAMBOO_DEBUGPRINT_REG(remain);
#endif
			} // if(memneed < remain)
		} // if((gccorestatus[i] != 0) && (gcrequiredmems[i] > 0))
		BAMBOO_CLOSE_CRITICAL_SECTION();
	} // for(i = 0; i < NUMCORES; i++)
#ifdef GC_DEBUG
	BAMBOO_DEBUGPRINT(0xd106);
#endif
} // void compact2Heaptop()

inline int nextTopcore(int topcore, bool direction) {
	int nextopcore = topcore;
	if((NUMCORES == 62) && (nextopcore>5)) {
		nextopcore += 2;
	}
	int x = nextopcore / bamboo_height;
	int y = nextopcore % bamboo_height;
	if((direction && (y%2 == 0)) || ((!direction) && (y%2))) {
		// increase
		if(x == 7) {
			if(direction) {
				y++;
			} else {
				y--;
			}
		} else {
			x++;
		}
	} else {
		// decrease
		if((x == 0) || ((x==1) &&(y==6))) {
			if(direction) {
				y++;
				if(y==6) {
					x = 1;
				}
			} else {
				y--;
				if(y==5) {
					x = 0;
				}
			}
		} else {
			x--;
		}
	}
	nextopcore = x*bamboo_height+y;
	if(NUMCORES==62) {
		if(x>0) {
			nextopcore -= 2;
		}
	}
	return nextopcore;
} // int nextTopcore(int topcore, bool direction)

inline void resolvePendingMoveRequest() {
#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xeb01);
#endif
	int i;
	int j;
	bool nosparemem = true;
	bool haspending = false;
	bool hasrunning = false;
	bool noblock = false;
	int dstcore = 0; // the core who need spare mem
	int sourcecore = 0; // the core who has spare mem
	for(i = j = 0; (i < NUMCORES) && (j < NUMCORES);) {
		if(nosparemem) {
			// check if there are cores with spare mem
			if(gccorestatus[i] == 0) {
				// finished working, check if it still have spare mem
				if(gcfilledblocks[i] < gcstopblock[i]) {
					// still have spare mem
					nosparemem = false;
					sourcecore = i;
				} // if(gcfilledblocks[i] < gcstopblock[i]) else ...
			}
			i++;
		} // if(nosparemem)
		if(!haspending) {
			if(gccorestatus[j] != 0) {
				// not finished, check if it has pending move requests
				if((gcfilledblocks[j]==gcstopblock[j])&&(gcrequiredmems[j]>0)) {
					dstcore = j;
					haspending = true;
				} else {
					hasrunning = true;
				} // if((gcfilledblocks[i] == gcstopblock[i])...) else ...
			} // if(gccorestatus[i] == 0) else ...
			j++;
		} // if(!haspending)
		if(!nosparemem && haspending) {
			// find match
			int tomove = 0;
			int startaddr = 0;
			BAMBOO_START_CRITICAL_SECTION();
			gcrequiredmems[dstcore] = assignSpareMem_I(sourcecore, 
					                                       gcrequiredmems[dstcore], 
																							   &tomove, 
																							   &startaddr);
			BAMBOO_CLOSE_CRITICAL_SECTION();
			if(STARTUPCORE == dstcore) {
				gcdstcore = sourcecore;
				gctomove = true;
				gcmovestartaddr = startaddr;
				gcblock2fill = tomove;
			} else {
				send_msg_4(dstcore, GCMOVESTART, sourcecore, startaddr, tomove);
			}
			if(gcrequiredmems[dstcore] == 0) {
				gcmovepending--;
			}
			nosparemem = true;
			haspending = false;
			noblock = true;
		}
	} // for(i = 0; i < NUMCORES; i++)
#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xcccc);
	BAMBOO_DEBUGPRINT_REG(hasrunning);
	BAMBOO_DEBUGPRINT_REG(haspending);
	BAMBOO_DEBUGPRINT_REG(noblock);
#endif

	if(!hasrunning && !noblock) {
		gcphase = SUBTLECOMPACTPHASE;
		compact2Heaptop();
	}

} // void resovePendingMoveRequest()

struct moveHelper {
	int numblocks; // block num for heap
	INTPTR base; // base virtual address of current heap block
	INTPTR ptr; // virtual address of current heap top
	int offset; // offset in current heap block
	int blockbase; // virtual address of current small block to check
	int blockbound; // bound virtual address of current small blcok
	int sblockindex; // index of the small blocks
	int top; // real size of current heap block to check
	int bound; // bound size of current heap block to check
}; // struct moveHelper

inline void nextSBlock(struct moveHelper * orig) {
	orig->blockbase = orig->blockbound;
innernextSBlock:
	if(orig->blockbase >= orig->bound) {
		// end of current heap block, jump to next one
		orig->numblocks++;
		BASEPTR(BAMBOO_NUM_OF_CORE, orig->numblocks, &(orig->base));
		orig->bound = orig->base + BAMBOO_SMEM_SIZE;
		orig->blockbase = orig->base;
	}
	orig->sblockindex = (orig->blockbase-BAMBOO_BASE_VA)/BAMBOO_SMEM_SIZE;
	if(gcsbstarttbl[orig->sblockindex] == -1) {
		// goto next sblock
		orig->sblockindex += 1;
		orig->blockbase += BAMBOO_SMEM_SIZE;
		goto innernextSBlock;
	} else if(gcsbstarttbl[orig->sblockindex] != 0) {
		// not start from the very beginning
		orig->blockbase = gcsbstarttbl[orig->sblockindex];
	}
	orig->blockbound = orig->blockbase + *((int*)(orig->blockbase));
	orig->offset = BAMBOO_CACHE_LINE_SIZE;
	orig->ptr = orig->blockbase + orig->offset;
} // void nextSBlock(struct moveHelper * orig) 

inline void initOrig_Dst(struct moveHelper * orig, 
		                     struct moveHelper * to) {
	// init the dst ptr
	to->numblocks = 0;
	to->top = to->offset = BAMBOO_CACHE_LINE_SIZE;
	to->bound = BAMBOO_SMEM_SIZE_L;
	BASEPTR(BAMBOO_NUM_OF_CORE, to->numblocks, &(to->base));

#ifdef GC_DEBUG
	BAMBOO_DEBUGPRINT(0xef01);
	BAMBOO_DEBUGPRINT_REG(to->base);
#endif
	/*if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
		to->base += gcreservedsb * BAMBOO_SMEM_SIZE;
		to->top += gcreservedsb * BAMBOO_SMEM_SIZE;
	}*/
	to->ptr = to->base + to->offset;

	// init the orig ptr
	orig->numblocks = 0;
	orig->base = to->base;
	orig->bound = to->base + BAMBOO_SMEM_SIZE_L;
	orig->blockbase = orig->base;
	/*if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
		orig->sblockindex = gcreservedsb;
	} else {*/
		orig->sblockindex = (orig->base - BAMBOO_BASE_VA) / BAMBOO_SMEM_SIZE;
	//}
#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xef02);
	BAMBOO_DEBUGPRINT_REG(orig->base);
	BAMBOO_DEBUGPRINT_REG(orig->sblockindex);
	BAMBOO_DEBUGPRINT_REG(gcsbstarttbl);
	BAMBOO_DEBUGPRINT_REG(gcsbstarttbl[orig->sblockindex]);
#endif

	if(gcsbstarttbl[orig->sblockindex] == -1) {
#ifdef DEBUG
		BAMBOO_DEBUGPRINT(0xef03);
#endif
		// goto next sblock
		orig->blockbound = 
			BAMBOO_BASE_VA+BAMBOO_SMEM_SIZE*(orig->sblockindex+1);
		nextSBlock(orig);
		return;
	} else if(gcsbstarttbl[orig->sblockindex] != 0) {
#ifdef DEBUG
		BAMBOO_DEBUGPRINT(0xef04);
#endif
		orig->blockbase = gcsbstarttbl[orig->sblockindex];
	}
#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xef05);
#endif
	orig->blockbound = orig->blockbase + *((int*)(orig->blockbase));
	orig->offset = BAMBOO_CACHE_LINE_SIZE;
	orig->ptr = orig->blockbase + orig->offset;
#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xef06);
	BAMBOO_DEBUGPRINT_REG(to->base);
#endif
} // void initOrig_Dst(struct moveHelper * orig, struct moveHelper * to) 

inline void nextBlock(struct moveHelper * to) {
	to->top = to->bound + BAMBOO_CACHE_LINE_SIZE; // header!
	to->bound += BAMBOO_SMEM_SIZE;
	to->numblocks++;
	BASEPTR(BAMBOO_NUM_OF_CORE, to->numblocks, &(to->base));
	to->offset = BAMBOO_CACHE_LINE_SIZE;
	to->ptr = to->base + to->offset;
} // void nextBlock(struct moveHelper * to)

// endaddr does not contain spaces for headers
inline bool moveobj(struct moveHelper * orig, 
		                struct moveHelper * to, 
						        int stopblock) {
	if(stopblock == 0) {
		return true;
	}

#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xe201);
	BAMBOO_DEBUGPRINT_REG(orig->ptr);
	BAMBOO_DEBUGPRINT_REG(to->ptr);
#endif

	int type = 0;
	int size = 0;
	int mark = 0;
	int isize = 0;
innermoveobj:
	while((char)(*((int*)(orig->ptr))) == (char)(-2)) {
		orig->ptr = (int*)(orig->ptr) + 1;
	}
	if((orig->ptr > orig->bound) || (orig->ptr == orig->blockbound)) {
		nextSBlock(orig);
		goto innermoveobj;
	}
#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xe202);
#endif
	// check the obj's type, size and mark flag
	type = ((int *)(orig->ptr))[0];
	size = 0;
	if(type == -1) {
		// end of this block, go to next one
		nextSBlock(orig);
		goto innermoveobj;
	} else if(type < NUMCLASSES) {
		// a normal object
		size = classsize[type];
	} else {	
		// an array 
		struct ArrayObject *ao=(struct ArrayObject *)(orig->ptr);
		int elementsize=classsize[type];
		int length=ao->___length___; 
		size=sizeof(struct ArrayObject)+length*elementsize;
	}
	mark = ((int *)(orig->ptr))[6];
#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xe203);
#endif
	if(mark == 1) {
#ifdef DEBUG
		BAMBOO_DEBUGPRINT(0xe204);
#endif
		// marked obj, copy it to current heap top
		// check to see if remaining space is enough
		ALIGNSIZE(size, &isize);
		if(to->top + isize > to->bound) {
			// fill -1 indicating the end of this block
			if(to->top != to->bound) {
				*((int*)to->ptr) = -1;
			}
			//memset(to->ptr+1,  -2, to->bound - to->top - 1);
			// fill the header of this block and then go to next block
    	to->offset += to->bound - to->top;
			memset(to->base, '\0', BAMBOO_CACHE_LINE_SIZE);
			(*((int*)(to->base))) = to->offset;
			nextBlock(to);
			if(stopblock == to->numblocks) {
				// already fulfilled the block
				to->offset = 0;
				to->ptr = to->base;
				return true;
			} // if(stopblock == to->numblocks)
		} // if(to->top + isize > to->bound)
		// set the mark field to 2, indicating that this obj has been moved and need to be flushed
		((int *)(orig->ptr))[6] = 2;
		if(to->ptr != orig->ptr) {
			memcpy(to->ptr, orig->ptr, size);
			// fill the remaining space with -2
			memset(to->ptr+size, -2, isize-size);
		}
		// store mapping info
		BAMBOO_START_CRITICAL_SECTION();
		RuntimeHashadd_I(gcpointertbl, orig->ptr, to->ptr); 
		BAMBOO_CLOSE_CRITICAL_SECTION();
#ifdef DEBUG
		BAMBOO_DEBUGPRINT(0xcdcd);
		BAMBOO_DEBUGPRINT_REG(orig->ptr);
		BAMBOO_DEBUGPRINT_REG(to->ptr);
#endif
		gccurr_heaptop -= isize;
		to->ptr += isize;
		to->offset += isize;
		to->top += isize;
	} // if(mark == 1)
#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xe205);
#endif
	// move to next obj
	orig->ptr += isize;
#ifdef DEBUG
	BAMBOO_DEBUGPRINT_REG(isize);
	BAMBOO_DEBUGPRINT_REG(orig->ptr);
#endif
	if((orig->ptr > orig->bound) || (orig->ptr == orig->blockbound)) {
#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xe206);
#endif
		nextSBlock(orig);
	}
#ifdef DEBUG
	BAMBOO_DEBUGPRINT_REG(orig->ptr);
#endif
	return false;
} //bool moveobj(struct moveHelper* orig,struct moveHelper* to,int* endaddr)

// should be invoked with interrupt closed
inline int assignSpareMem_I(int sourcecore,
        		                int * requiredmem,
													  int * tomove,
													  int * startaddr) {
	int b = 0;
	BLOCKINDEX(gcloads[sourcecore], &b);
	int boundptr = b<NUMCORES?(b+1)*BAMBOO_SMEM_SIZE_L
		:BAMBOO_LARGE_SMEM_BOUND+(b-NUMCORES+1)*BAMBOO_SMEM_SIZE;
	int remain = boundptr - gcloads[sourcecore];
	int memneed = requiredmem + BAMBOO_CACHE_LINE_SIZE;
	*startaddr = gcloads[sourcecore];
	*tomove = gcfilledblocks[sourcecore] + 1;
	if(memneed < remain) {
		gcloads[sourcecore] += memneed;
		return 0;
	} else {
		// next available block
		gcfilledblocks[sourcecore] += 1;
		int newbase = 0;
		BASEPTR(sourcecore, gcfilledblocks[sourcecore], &newbase);
		gcloads[sourcecore] = newbase;
		return requiredmem-remain;
	}
} // int assignSpareMem_I(int ,int * , int * , int * )

// should be invoked with interrupt closed
inline bool gcfindSpareMem_I(int * startaddr,
		                         int * tomove,
								  				   int * dstcore,
									  			   int requiredmem,
										  		   int requiredcore) {
	for(int k = 0; k < NUMCORES; k++) {
		if((gccorestatus[k] == 0) && (gcfilledblocks[k] < gcstopblock[k])) {
			// check if this stopped core has enough mem
			assignSpareMem_I(k, requiredmem, tomove, startaddr);
			*dstcore = k;
			return true;
		}
	}
	// if can not find spare mem right now, hold the request
	gcrequiredmems[requiredcore] = requiredmem;
	gcmovepending++;
	return false;
} //bool gcfindSpareMem_I(int* startaddr,int* tomove,int mem,int core)

inline bool compacthelper(struct moveHelper * orig,
		                      struct moveHelper * to,
													int * filledblocks,
													int * heaptopptr,
													bool * localcompact) {
	// scan over all objs in this block, compact the marked objs 
	// loop stop when finishing either scanning all active objs or 
	// fulfilled the gcstopblock
#ifdef GC_DEBUG
	BAMBOO_DEBUGPRINT(0xe101);
	BAMBOO_DEBUGPRINT_REG(gcblock2fill);
#endif
innercompact:
	do {
		bool stop = moveobj(orig, to, gcblock2fill);
		if(stop) {
			break;
		}
	} while(orig->ptr < gcmarkedptrbound);
	// if no objs have been compact, do nothing, 
	// otherwise, fill the header of this block
	if(to->offset > BAMBOO_CACHE_LINE_SIZE) {
		memset(to->base, '\0', BAMBOO_CACHE_LINE_SIZE);
		(*((int*)(to->base))) = to->offset;
	} else {
		to->offset = 0;
		to->ptr = to->base;
		to->top -= BAMBOO_CACHE_LINE_SIZE;
	} // if(to->offset > BAMBOO_CACHE_LINE_SIZE) else ...
	if(*localcompact) {
		*heaptopptr = to->ptr;
		*filledblocks = to->numblocks;
	}
#ifdef GC_DEBUG
	BAMBOO_DEBUGPRINT(0xe102);
	BAMBOO_DEBUGPRINT_REG(orig->ptr);
	BAMBOO_DEBUGPRINT_REG(gcmarkedptrbound);
	BAMBOO_DEBUGPRINT_REG(*heaptopptr);
	BAMBOO_DEBUGPRINT_REG(*filledblocks);
	BAMBOO_DEBUGPRINT_REG(gccurr_heaptop);
#endif

	// send msgs to core coordinator indicating that the compact is finishing
	// send compact finish message to core coordinator
	if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
		gcfilledblocks[BAMBOO_NUM_OF_CORE] = *filledblocks;
		gcloads[BAMBOO_NUM_OF_CORE] = *heaptopptr;
		if(orig->ptr < gcmarkedptrbound) {
#ifdef GC_DEBUG
			BAMBOO_DEBUGPRINT(0xe103);
#endif
			// ask for more mem
			gctomove = false;
			BAMBOO_START_CRITICAL_SECTION();
			if(gcfindSpareMem_I(&gcmovestartaddr, &gcblock2fill, &gcdstcore, 
						              gccurr_heaptop, BAMBOO_NUM_OF_CORE)) {
#ifdef GC_DEBUG
				BAMBOO_DEBUGPRINT(0xe104);
#endif
				gctomove = true;
			} else {
				BAMBOO_CLOSE_CRITICAL_SECTION();
#ifdef GC_DEBUG
				BAMBOO_DEBUGPRINT(0xe105);
#endif
				return false; 
			}
			BAMBOO_CLOSE_CRITICAL_SECTION();
		} else {
#ifdef GC_DEBUG
			BAMBOO_DEBUGPRINT(0xe106);
#endif
			gccorestatus[BAMBOO_NUM_OF_CORE] = 0;
			gctomove = false;
			return true;
		}
	} else {
		if(orig->ptr < gcmarkedptrbound) {
#ifdef GC_DEBUG
			BAMBOO_DEBUGPRINT(0xe107);
#endif
			// ask for more mem
			gctomove = false;
			send_msg_5(STARTUPCORE, GCFINISHCOMPACT, BAMBOO_NUM_OF_CORE, 
					       *filledblocks, *heaptopptr, gccurr_heaptop);
		} else {
#ifdef GC_DEBUG
			BAMBOO_DEBUGPRINT(0xe108);
#endif
			// finish compacting
			send_msg_5(STARTUPCORE, GCFINISHCOMPACT, BAMBOO_NUM_OF_CORE,
					       *filledblocks, *heaptopptr, 0);
		}
	} // if(STARTUPCORE == BAMBOO_NUM_OF_CORE)

	if(orig->ptr < gcmarkedptrbound) {
#ifdef GC_DEBUG
		BAMBOO_DEBUGPRINT(0xe109);
#endif
		// still have unpacked obj
		while(!gctomove) {};
		gctomove = false;
#ifdef GC_DEBUG
		BAMBOO_DEBUGPRINT(0xe10a);
#endif

		to->ptr = gcmovestartaddr;
		to->numblocks = gcblock2fill - 1;
		to->bound = (to->numblocks==0)?
			BAMBOO_SMEM_SIZE_L:
			BAMBOO_SMEM_SIZE_L+BAMBOO_SMEM_SIZE*to->numblocks;
		BASEPTR(BAMBOO_NUM_OF_CORE, to->numblocks, &(to->base));
		to->offset = to->ptr - to->base;
		to->top = (to->numblocks==0)?
			(to->offset):(to->bound-BAMBOO_SMEM_SIZE+to->offset);
		to->base = to->ptr;
		to->offset = BAMBOO_CACHE_LINE_SIZE;
		to->ptr += to->offset; // for header
		to->top += to->offset;
		if(gcdstcore == BAMBOO_NUM_OF_CORE) {
			*localcompact = true;
		} else {
			*localcompact = false;
		}
		goto innercompact;
	}
#ifdef GC_DEBUG
	BAMBOO_DEBUGPRINT(0xe10b);
#endif
	return true;
} // void compacthelper()

inline void compact() {
	if(COMPACTPHASE != gcphase) {
		BAMBOO_EXIT(0xb102);
	}

	// initialize pointers for comapcting
	struct moveHelper * orig = 
		(struct moveHelper *)RUNMALLOC(sizeof(struct moveHelper));
	struct moveHelper * to = 
		(struct moveHelper *)RUNMALLOC(sizeof(struct moveHelper));

	initOrig_Dst(orig, to);
	
	int filledblocks = 0;
	INTPTR heaptopptr = 0;
	bool localcompact = true;
	compacthelper(orig, to, &filledblocks, &heaptopptr, &localcompact);
	
	RUNFREE(orig);
	RUNFREE(to);
} // compact()

inline void * flushObj(void * objptr) {
#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xe401);
#endif
	void * dstptr = NULL;
	if(ISSHAREDOBJ(objptr)) {
#ifdef DEBUG
		BAMBOO_DEBUGPRINT(0xe402);
		BAMBOO_DEBUGPRINT_REG(objptr);
#endif
		// a shared obj ptr, change to new address
		BAMBOO_START_CRITICAL_SECTION();
		RuntimeHashget(gcpointertbl, objptr, &dstptr);
		BAMBOO_CLOSE_CRITICAL_SECTION();
#ifdef DEBUG
		BAMBOO_DEBUGPRINT_REG(dstptr);
#endif
		if(NULL == dstptr) {
#ifdef DEBUG
			BAMBOO_DEBUGPRINT(0xe403);
#endif
			// send msg to host core for the mapping info
			gcobj2map = (int)objptr;
			gcismapped = false;
			gcmappedobj = NULL;
			send_msg_3(hostcore(objptr), GCMAPREQUEST, (int)objptr, 
								 BAMBOO_NUM_OF_CORE);
			while(!gcismapped) {}
			BAMBOO_START_CRITICAL_SECTION();
			RuntimeHashget(gcpointertbl, objptr, &dstptr);
			BAMBOO_CLOSE_CRITICAL_SECTION();
#ifdef DEBUG
			BAMBOO_DEBUGPRINT_REG(dstptr);
#endif
		}
	} // if(ISSHAREDOBJ(objptr))
#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xe404);
#endif
	return dstptr;
} // void flushObj(void * objptr, void ** tochange)

inline void flushRuntimeObj(struct garbagelist * stackptr) {
	int i,j;
	// flush current stack 
	while(stackptr!=NULL) {
		for(i=0; i<stackptr->size; i++) {
			if(stackptr->array[i] != NULL) {
				stackptr->array[i] = flushObj(stackptr->array[i]);
			}
		}
		stackptr=stackptr->next;
	}

	// flush objectsets
	for(i=0; i<NUMCLASSES; i++) {
		struct parameterwrapper ** queues = 
			objectqueues[BAMBOO_NUM_OF_CORE][i];
		int length = numqueues[BAMBOO_NUM_OF_CORE][i];
		for(j = 0; j < length; ++j) {
			struct parameterwrapper * parameter = queues[j];
			struct ObjectHash * set=parameter->objectset;
			struct ObjectNode * ptr=set->listhead;
			while(ptr!=NULL) {
				ptr->key = flushObj((void *)ptr->key);
				ptr=ptr->lnext;
			}
		}
	}

	// flush current task descriptor
	if(currtpd != NULL) {
		for(i=0; i<currtpd->numParameters; i++) {
			currtpd->parameterArray[i] = flushObj(currtpd->parameterArray[i]);
		}
	}

	// flush active tasks
	struct genpointerlist * ptr=activetasks->list;
	while(ptr!=NULL) {
		struct taskparamdescriptor *tpd=ptr->src;
		int i;
		for(i=0; i<tpd->numParameters; i++) {
			tpd->parameterArray[i] = flushObj(tpd->parameterArray[i]);
		}
		ptr=ptr->inext;
	}

	// flush cached transferred obj
	struct QueueItem * tmpobjptr =  getHead(&objqueue);
	while(tmpobjptr != NULL) {
		struct transObjInfo * objInfo = 
			(struct transObjInfo *)(tmpobjptr->objectptr); 
		objInfo->objptr = flushObj(objInfo->objptr);
		tmpobjptr = getNextQueueItem(tmpobjptr);
	}

	// flush cached objs to be transferred
	struct QueueItem * item = getHead(totransobjqueue);
	while(item != NULL) {
		struct transObjInfo * totransobj = 
			(struct transObjInfo *)(item->objectptr);
		totransobj->objptr = flushObj(totransobj->objptr);
		item = getNextQueueItem(item);
	} // while(item != NULL)
} // void flushRuntimeObj(struct garbagelist * stackptr)

inline void flush(struct garbagelist * stackptr) {
	flushRuntimeObj(stackptr);
	while(gc_moreItems()) {
#ifdef DEBUG
		BAMBOO_DEBUGPRINT(0xe301);
#endif
		void * ptr = gc_dequeue();
#ifdef DEBUG
		BAMBOO_DEBUGPRINT_REG(ptr);
#endif
		void * tptr = flushObj(ptr);
#ifdef DEBUG
		BAMBOO_DEBUGPRINT(0xe302);
		BAMBOO_DEBUGPRINT_REG(ptr);
		BAMBOO_DEBUGPRINT_REG(tptr);
#endif
		if(tptr != NULL) {
			ptr = tptr;
		}
		if(((int *)(ptr))[6] == 2) {
			int type = ((int *)(ptr))[0];
			// scan all pointers in ptr
			unsigned INTPTR * pointer;
			pointer=pointerarray[type];
#ifdef DEBUG
			BAMBOO_DEBUGPRINT(0xe303);
			BAMBOO_DEBUGPRINT_REG(pointer);
#endif
			if (pointer==0) {
				/* Array of primitives */
				/* Do nothing */
			} else if (((INTPTR)pointer)==1) {
#ifdef DEBUG
				BAMBOO_DEBUGPRINT(0xe304);
#endif
				/* Array of pointers */
				struct ArrayObject *ao=(struct ArrayObject *) ptr;
				int length=ao->___length___;
				int j;
				for(j=0; j<length; j++) {
#ifdef DEBUG
					BAMBOO_DEBUGPRINT(0xe305);
#endif
					void *objptr=
						((void **)(((char *)&ao->___length___)+sizeof(int)))[j];
#ifdef DEBUG
					BAMBOO_DEBUGPRINT_REG(objptr);
#endif
					((void **)(((char *)&ao->___length___)+sizeof(int)))[j] = 
						flushObj(objptr);
				}
			} else {
#ifdef DEBUG
				BAMBOO_DEBUGPRINT(0xe306);
#endif
				INTPTR size=pointer[0];
				int i;
				for(i=1; i<=size; i++) {
#ifdef DEBUG
					BAMBOO_DEBUGPRINT(0xe307);
#endif
					unsigned int offset=pointer[i];
					void * objptr=*((void **)(((char *)ptr)+offset));
#ifdef DEBUG
					BAMBOO_DEBUGPRINT_REG(objptr);
#endif
					*((void **)(((char *)ptr)+offset)) = flushObj(objptr);
				} // for(i=1; i<=size; i++) 
			} // if (pointer==0) else if (((INTPTR)pointer)==1) else ()
			// restore the mark field, indicating that this obj has been flushed
			((int *)(ptr))[6] = 0;
		} // if(((int *)(ptr))[6] == 2)
	} // while(moi != NULL)
#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xe308);
#endif
	// send flush finish message to core coordinator
	if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
		gccorestatus[BAMBOO_NUM_OF_CORE] = 0;
	} else {
		send_msg_2(STARTUPCORE, GCFINISHFLUSH, BAMBOO_NUM_OF_CORE);
	}
#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xe309);
#endif
} // flush()

inline void gc_collect(struct garbagelist * stackptr) {
	// core collector routine
#ifdef GC_DEBUG
	tprintf("Do initGC\n");
#endif
	initGC();
	//send init finish msg to core coordinator
	send_msg_2(STARTUPCORE, GCFINISHINIT, BAMBOO_NUM_OF_CORE);
#ifdef GC_DEBUG
	tprintf("Start mark phase\n");
#endif
	mark(true, stackptr);
#ifdef GC_DEBUG
	tprintf("Finish mark phase, start compact phase\n");
#endif
	compact();
#ifdef GC_DEBUG
	tprintf("Finish compact phase\n");
#endif
	while(FLUSHPHASE != gcphase) {}
#ifdef GC_DEBUG
	tprintf("Start flush phase\n");
#endif
	flush(stackptr);
#ifdef GC_DEBUG
	tprintf("Finish flush phase\n");
#endif

	while(FINISHPHASE != gcphase) {}
#ifdef GC_DEBUG
	tprintf("Finish gc!\n");
#endif
} // void gc_collect(struct garbagelist * stackptr)

inline void gc(struct garbagelist * stackptr) {
	// check if do gc
	if(!gcflag) {
		return;
	}

	// core coordinator routine
	if(0 == BAMBOO_NUM_OF_CORE) {
#ifdef GC_DEBUG
	tprintf("Check if can do gc or not\n");
#endif
		if(!preGC()) {
			// not ready to do gc
			gcflag = true;
			return;
		}

#ifdef GC_DEBUG
		tprintf("start gc! \n");
		dumpSMem();
#endif

		gcprocessing = true;
		int i = 0;
		waitconfirm = false;
		waitconfirm = 0;
		gcphase = MARKPHASE;
		for(i = 1; i < NUMCORES; i++) {
			// send GC init messages to all cores
			send_msg_1(i, GCSTARTINIT);
		}
		bool isfirst = true;
		bool allStall = false;

		initGC();

		gccorestatus[BAMBOO_NUM_OF_CORE] = 0;
		while(!gc_checkCoreStatus()) {}
		// all cores have finished compacting
		// restore the gcstatus of all cores
		gccorestatus[BAMBOO_NUM_OF_CORE] = 1;
		for(i = 1; i < NUMCORES; ++i) {
			gccorestatus[i] = 1;
			// send GC start messages to all cores
			send_msg_1(i, GCSTART);
		}

		// mark phase
		while(MARKPHASE == gcphase) {
			mark(isfirst, stackptr);
			if(isfirst) {
				isfirst = false;
			}

			// check gcstatus
			checkMarkStatue(); 
		}  // while(MARKPHASE == gcphase)
		// send msgs to all cores requiring large objs info
		numconfirm = NUMCORES - 1;
		for(i = 1; i < NUMCORES; ++i) {
			send_msg_1(i, GCLOBJREQUEST);
		}
		gcloads[BAMBOO_NUM_OF_CORE] = gccurr_heaptop;
		while(numconfirm) {
			int tmp = 100;
			while(tmp--) {}
		} // wait for responses
#ifdef GC_DEBUG
		tprintf("prepare to cache large objs \n");
		dumpSMem();
#endif
		// cache all large objs
		if(!cacheLObjs()) {
			// no enough space to cache large objs
			BAMBOO_EXIT(0xb103);
		}
		// predict number of blocks to fill for each core
		int numpbc = loadbalance();
#ifdef GC_DEBUG
		tprintf("mark phase finished \n");
		dumpSMem();
#endif
		for(i = 0; i < NUMCORES; ++i) {
			//send start compact messages to all cores
			if((gcheapdirection) && (i < gctopcore)
					|| ((!gcheapdirection) && (i > gctopcore))) {
				gcstopblock[i] =numpbc + 1;
				if(i != STARTUPCORE) {
					send_msg_2(i, GCSTARTCOMPACT, numpbc+1); 
				} else {
					gcblock2fill = numpbc+1;
				} // if(i != STARTUPCORE)
			} else {
				gcstopblock[i] = numpbc;
				if(i != STARTUPCORE) {
					send_msg_2(i, GCSTARTCOMPACT, numpbc);
				} else {
					gcblock2fill = numpbc;
				} // if(i != STARTUPCORE)
			}
			// init some data strutures for compact phase
			gcloads[i] = 0;
			gcfilledblocks[i] = 0;
			gcrequiredmems[i] = 0;
		}

		// compact phase
		bool finalcompact = false;
		// initialize pointers for comapcting
		struct moveHelper * orig = 
			(struct moveHelper *)RUNMALLOC(sizeof(struct moveHelper));
		struct moveHelper * to = 
			(struct moveHelper *)RUNMALLOC(sizeof(struct moveHelper));
		initOrig_Dst(orig, to);
		int filledblocks = 0;
		INTPTR heaptopptr = 0;
		bool finishcompact = false;
		bool iscontinue = true;
		bool localcompact = true;
		while((COMPACTPHASE == gcphase) || (SUBTLECOMPACTPHASE == gcphase)) {
			if((!finishcompact) && iscontinue) {
#ifdef GC_DEBUG
				BAMBOO_DEBUGPRINT(0xe001);
				BAMBOO_DEBUGPRINT_REG(gcblock2fill);
#endif
				finishcompact = compacthelper(orig, to, &filledblocks, 
						                          &heaptopptr, &localcompact);
#ifdef DEBUG
				BAMBOO_DEBUGPRINT(0xe002);
				BAMBOO_DEBUGPRINT_REG(finishcompact);
				BAMBOO_DEBUGPRINT_REG(gctomove);
				BAMBOO_DEBUGPRINT_REG(gcrequiredmems[0]);
				BAMBOO_DEBUGPRINT_REG(gcfilledblocks[0]);
				BAMBOO_DEBUGPRINT_REG(gcstopblock[0]);
				//dumpSMem();
#endif
			}

			if(gc_checkCoreStatus()) {
				// all cores have finished compacting
				// restore the gcstatus of all cores
				for(i = 0; i < NUMCORES; ++i) {
					gccorestatus[i] = 1;
				}
				break;
			} else {
				// check if there are spare mem for pending move requires
				if(COMPACTPHASE == gcphase) {
#ifdef GC_DEBUG
					BAMBOO_DEBUGPRINT(0xe003);
#endif
					resolvePendingMoveRequest();
#ifdef GC_DEBUG
					BAMBOO_DEBUGPRINT_REG(gctomove);
#endif
				} else {
#ifdef DEBUG
					BAMBOO_DEBUGPRINT(0xe004);
#endif
					compact2Heaptop();
				}
			} // if(gc_checkCoreStatus()) else ...

			if(gctomove) {
#ifdef GC_DEBUG
				BAMBOO_DEBUGPRINT(0xe005);
				BAMBOO_DEBUGPRINT_REG(gcmovestartaddr);
				BAMBOO_DEBUGPRINT_REG(gcblock2fill);
				BAMBOO_DEBUGPRINT_REG(gctomove);
#endif
				to->ptr = gcmovestartaddr;
				to->numblocks = gcblock2fill - 1;
				to->bound = (to->numblocks==0)?
					BAMBOO_SMEM_SIZE_L:
					BAMBOO_SMEM_SIZE_L+BAMBOO_SMEM_SIZE*to->numblocks;
				BASEPTR(BAMBOO_NUM_OF_CORE, to->numblocks, &(to->base));
				to->offset = to->ptr - to->base;
				to->top = (to->numblocks==0)?
					(to->offset):(to->bound-BAMBOO_SMEM_SIZE+to->offset);
				to->base = to->ptr;
				to->offset = BAMBOO_CACHE_LINE_SIZE;
				to->ptr += to->offset; // for header
				to->top += to->offset;
				if(gcdstcore == BAMBOO_NUM_OF_CORE) {
					localcompact = true;
				} else {
					localcompact = false;
				}
				gctomove = false;
				iscontinue = true;
			} else if(!finishcompact) {
				// still pending
				iscontinue = false;
			} // if(gctomove)

		} // while(COMPACTPHASE == gcphase) 
#ifdef GC_DEBUG
		tprintf("prepare to move large objs \n");
		dumpSMem();
#endif
		// move largeObjs
		moveLObjs();
#ifdef GC_DEBUG
		tprintf("compact phase finished \n");
		dumpSMem();
#endif

		gcphase = FLUSHPHASE;
		for(i = 1; i < NUMCORES; ++i) {
			// send start flush messages to all cores
			send_msg_1(i, GCSTARTFLUSH);
		}

		// flush phase
		flush(stackptr);
		gccorestatus[BAMBOO_NUM_OF_CORE] = 0;
		while(FLUSHPHASE == gcphase) {
			// check the status of all cores
			allStall = true;
			for(i = 0; i < NUMCORES; ++i) {
				if(gccorestatus[i] != 0) {
					allStall = false;
					break;
				}
			}	
			if(allStall) {
				break;
			}
		} // while(FLUSHPHASE == gcphase)
		gcphase = FINISHPHASE;
		for(i = 1; i < NUMCORES; ++i) {
			// send gc finish messages to all cores
			send_msg_1(i, GCFINISH);
		}
#ifdef GC_DEBUG
		tprintf("flush phase finished \n");
		dumpSMem();
#endif

		// need to create free memory list  
		updateFreeMemList();
#ifdef GC_DEBUG
		tprintf("gc finished \n");
		dumpSMem();
#endif
	} else {
		gcprocessing = true;
		gc_collect(stackptr);
	}

	// invalidate all shared mem pointers
	bamboo_cur_msp = NULL;
	bamboo_smem_size = 0;

	gcflag = false;
	gcprocessing = false;

} // void gc(struct garbagelist * stackptr)

#endif
