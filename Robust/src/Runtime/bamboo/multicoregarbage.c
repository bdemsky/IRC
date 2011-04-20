// BAMBOO_EXIT(0xb000);
// TODO: DO NOT support tag!!!
#ifdef MULTICORE_GC
#include "runtime.h"
#include "multicoregarbage.h"
#include "multicoreruntime.h"
#include "runtime_arch.h"
#include "SimpleHash.h"
#include "GenericHashtable.h"
#include "ObjectHash.h"
#include "GCSharedHash.h"

extern int corenum;
#ifdef TASK
extern struct parameterwrapper ** objectqueues[][NUMCLASSES];
extern int numqueues[][NUMCLASSES];
extern struct genhashtable * activetasks;
extern struct parameterwrapper ** objectqueues[][NUMCLASSES];
extern struct taskparamdescriptor *currtpd;
extern struct LockValue runtime_locks[MAXTASKPARAMS];
extern int runtime_locklen;
#endif

extern struct global_defs_t * global_defs_p;

#ifdef SMEMM
extern unsigned int gcmem_mixed_threshold;
extern unsigned int gcmem_mixed_usedmem;
#endif

#ifdef MGC
extern struct lockvector bamboo_threadlocks;
#endif

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
  int lengths[NUMLOBJPTRS];
  int hosts[NUMLOBJPTRS];
  struct lobjpointerblock *next;
  struct lobjpointerblock *prev;
};

struct lobjpointerblock *gclobjhead=NULL;
int gclobjheadindex=0;
struct lobjpointerblock *gclobjtail=NULL;
int gclobjtailindex=0;
struct lobjpointerblock *gclobjtail2=NULL;
int gclobjtailindex2=0;
struct lobjpointerblock *gclobjspare=NULL;

#ifdef GC_CACHE_ADAPT
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
#endif// GC_CACHE_ADAPT

#ifdef GC_DEBUG
// dump whole mem in blocks
inline void dumpSMem() {
  int block = 0;
  int sblock = 0;
  unsigned int j = 0;
  unsigned int i = 0;
  int coren = 0;
  int x = 0;
  int y = 0;
  printf("(%x,%x) Dump shared mem: \n", udn_tile_coord_x(), 
	     udn_tile_coord_y());
  // reserved blocks for sblocktbl
  printf("(%x,%x) ++++ reserved sblocks ++++ \n", udn_tile_coord_x(), 
	     udn_tile_coord_y());
  for(i=BAMBOO_BASE_VA; i<gcbaseva; i+= 4*16) {
    printf("(%x,%x) 0x%08x 0x%08x 0x%08x 0x%08x 0x%08x 0x%08x 0x%08x 0x%08x 0x%08x 0x%08x 0x%08x 0x%08x 0x%08x 0x%08x 0x%08x 0x%08x \n",
		   udn_tile_coord_x(), udn_tile_coord_y(),
           *((int *)(i)), *((int *)(i + 4)),
           *((int *)(i + 4*2)), *((int *)(i + 4*3)),
           *((int *)(i + 4*4)), *((int *)(i + 4*5)),
           *((int *)(i + 4*6)), *((int *)(i + 4*7)),
           *((int *)(i + 4*8)), *((int *)(i + 4*9)),
           *((int *)(i + 4*10)), *((int *)(i + 4*11)),
           *((int *)(i + 4*12)), *((int *)(i + 4*13)),
           *((int *)(i + 4*14)), *((int *)(i + 4*15)));
  }
  sblock = gcreservedsb;
  bool advanceblock = false;
  // remaining memory
  for(i=gcbaseva; i<gcbaseva+BAMBOO_SHARED_MEM_SIZE; i+=4*16) {
    advanceblock = false;
    // computing sblock # and block #, core coordinate (x,y) also
    if(j%((BAMBOO_SMEM_SIZE)/(4*16)) == 0) {
      // finished a sblock
      if(j < ((BAMBOO_LARGE_SMEM_BOUND)/(4*16))) {
		if((j > 0) && (j%((BAMBOO_SMEM_SIZE_L)/(4*16)) == 0)) {
		  // finished a block
		  block++;
		  advanceblock = true;
		}
      } else {
		// finished a block
		block++;
		advanceblock = true;
      }
      // compute core #
      if(advanceblock) {
		coren = gc_block2core[block%(NUMCORES4GC*2)];
      }
      // compute core coordinate
      BAMBOO_COORDS(coren, &x, &y);
      printf("(%x,%x) ==== %d, %d : core (%d,%d), saddr %x====\n",
		     udn_tile_coord_x(), udn_tile_coord_y(),
             block, sblock++, x, y,
             (sblock-1)*(BAMBOO_SMEM_SIZE)+gcbaseva);
    }
    j++;
    printf("(%x,%x) 0x%08x 0x%08x 0x%08x 0x%08x 0x%08x 0x%08x 0x%08x 0x%08x 0x%08x 0x%08x 0x%08x 0x%08x 0x%08x 0x%08x 0x%08x 0x%08x \n",
		   udn_tile_coord_x(), udn_tile_coord_y(),
           *((int *)(i)), *((int *)(i + 4)),
           *((int *)(i + 4*2)), *((int *)(i + 4*3)),
           *((int *)(i + 4*4)), *((int *)(i + 4*5)),
           *((int *)(i + 4*6)), *((int *)(i + 4*7)),
           *((int *)(i + 4*8)), *((int *)(i + 4*9)),
           *((int *)(i + 4*10)), *((int *)(i + 4*11)),
           *((int *)(i + 4*12)), *((int *)(i + 4*13)),
           *((int *)(i + 4*14)), *((int *)(i + 4*15)));
  }
  printf("(%x,%x) \n", udn_tile_coord_x(), udn_tile_coord_y());
}
#endif

// should be invoked with interruption closed
inline void gc_enqueue_I(void *ptr) {
  GC_BAMBOO_DEBUGPRINT(0xe601);
  GC_BAMBOO_DEBUGPRINT_REG(ptr);
  if (gcheadindex==NUMPTRS) {
    struct pointerblock * tmp;
    if (gcspare!=NULL) {
      tmp=gcspare;
      gcspare=NULL;
    } else {
      tmp=RUNMALLOC_I(sizeof(struct pointerblock));
    }  // if (gcspare!=NULL)
    gchead->next=tmp;
    gchead=tmp;
    gcheadindex=0;
  } // if (gcheadindex==NUMPTRS)
  gchead->ptrs[gcheadindex++]=ptr;
  GC_BAMBOO_DEBUGPRINT(0xe602);
} // void gc_enqueue_I(void *ptr)

// dequeue and destroy the queue
inline void * gc_dequeue_I() {
  if (gctailindex==NUMPTRS) {
    struct pointerblock *tmp=gctail;
    gctail=gctail->next;
    gctailindex=0;
    if (gcspare!=NULL) {
      RUNFREE(tmp);
    } else {
      gcspare=tmp;
    }  // if (gcspare!=NULL)
  } // if (gctailindex==NUMPTRS)
  return gctail->ptrs[gctailindex++];
} // void * gc_dequeue()

// dequeue and do not destroy the queue
inline void * gc_dequeue2_I() {
  if (gctailindex2==NUMPTRS) {
    struct pointerblock *tmp=gctail2;
    gctail2=gctail2->next;
    gctailindex2=0;
  } // if (gctailindex2==NUMPTRS)
  return gctail2->ptrs[gctailindex2++];
} // void * gc_dequeue2()

inline int gc_moreItems_I() {
  if ((gchead==gctail)&&(gctailindex==gcheadindex))
    return 0;
  return 1;
} // int gc_moreItems()

inline int gc_moreItems2_I() {
  if ((gchead==gctail2)&&(gctailindex2==gcheadindex))
    return 0;
  return 1;
} // int gc_moreItems2()

// should be invoked with interruption closed
// enqueue a large obj: start addr & length
inline void gc_lobjenqueue_I(void *ptr,
                             unsigned int length,
                             unsigned int host) {
  GC_BAMBOO_DEBUGPRINT(0xe901);
  if (gclobjheadindex==NUMLOBJPTRS) {
    struct lobjpointerblock * tmp;
    if (gclobjspare!=NULL) {
      tmp=gclobjspare;
      gclobjspare=NULL;
    } else {
      tmp=RUNMALLOC_I(sizeof(struct lobjpointerblock));
    }  // if (gclobjspare!=NULL)
    gclobjhead->next=tmp;
    tmp->prev = gclobjhead;
    gclobjhead=tmp;
    gclobjheadindex=0;
  } // if (gclobjheadindex==NUMLOBJPTRS)
  gclobjhead->lobjs[gclobjheadindex]=ptr;
  gclobjhead->lengths[gclobjheadindex]=length;
  gclobjhead->hosts[gclobjheadindex++]=host;
  GC_BAMBOO_DEBUGPRINT_REG(gclobjhead->lobjs[gclobjheadindex-1]);
  GC_BAMBOO_DEBUGPRINT_REG(gclobjhead->lengths[gclobjheadindex-1]);
  GC_BAMBOO_DEBUGPRINT_REG(gclobjhead->hosts[gclobjheadindex-1]);
} // void gc_lobjenqueue_I(void *ptr...)

// dequeue and destroy the queue
inline void * gc_lobjdequeue_I(unsigned int * length,
                               unsigned int * host) {
  if (gclobjtailindex==NUMLOBJPTRS) {
    struct lobjpointerblock *tmp=gclobjtail;
    gclobjtail=gclobjtail->next;
    gclobjtailindex=0;
    gclobjtail->prev = NULL;
    if (gclobjspare!=NULL) {
      RUNFREE(tmp);
    } else {
      gclobjspare=tmp;
      tmp->next = NULL;
      tmp->prev = NULL;
    }  // if (gclobjspare!=NULL)
  } // if (gclobjtailindex==NUMLOBJPTRS)
  if(length != NULL) {
    *length = gclobjtail->lengths[gclobjtailindex];
  }
  if(host != NULL) {
    *host = (unsigned int)(gclobjtail->hosts[gclobjtailindex]);
  }
  return gclobjtail->lobjs[gclobjtailindex++];
} // void * gc_lobjdequeue()

inline int gc_lobjmoreItems_I() {
  if ((gclobjhead==gclobjtail)&&(gclobjtailindex==gclobjheadindex))
    return 0;
  return 1;
} // int gc_lobjmoreItems()

// dequeue and don't destroy the queue
inline void gc_lobjdequeue2_I() {
  if (gclobjtailindex2==NUMLOBJPTRS) {
    gclobjtail2=gclobjtail2->next;
    gclobjtailindex2=1;
  } else {
    gclobjtailindex2++;
  }  // if (gclobjtailindex2==NUMLOBJPTRS)
} // void * gc_lobjdequeue2()

inline int gc_lobjmoreItems2_I() {
  if ((gclobjhead==gclobjtail2)&&(gclobjtailindex2==gclobjheadindex))
    return 0;
  return 1;
} // int gc_lobjmoreItems2()

// 'reversly' dequeue and don't destroy the queue
inline void gc_lobjdequeue3_I() {
  if (gclobjtailindex2==0) {
    gclobjtail2=gclobjtail2->prev;
    gclobjtailindex2=NUMLOBJPTRS-1;
  } else {
    gclobjtailindex2--;
  }  // if (gclobjtailindex2==NUMLOBJPTRS)
} // void * gc_lobjdequeue3()

inline int gc_lobjmoreItems3_I() {
  if ((gclobjtail==gclobjtail2)&&(gclobjtailindex2==gclobjtailindex))
    return 0;
  return 1;
} // int gc_lobjmoreItems3()

inline void gc_lobjqueueinit4_I() {
  gclobjtail2 = gclobjtail;
  gclobjtailindex2 = gclobjtailindex;
} // void gc_lobjqueueinit2()

inline void * gc_lobjdequeue4_I(unsigned int * length,
                                unsigned int * host) {
  if (gclobjtailindex2==NUMLOBJPTRS) {
    gclobjtail2=gclobjtail2->next;
    gclobjtailindex2=0;
  } // if (gclobjtailindex==NUMLOBJPTRS)
  if(length != NULL) {
    *length = gclobjtail2->lengths[gclobjtailindex2];
  }
  if(host != NULL) {
    *host = (unsigned int)(gclobjtail2->hosts[gclobjtailindex2]);
  }
  return gclobjtail2->lobjs[gclobjtailindex2++];
} // void * gc_lobjdequeue()

inline int gc_lobjmoreItems4_I() {
  if ((gclobjhead==gclobjtail2)&&(gclobjtailindex2==gclobjheadindex))
    return 0;
  return 1;
} // int gc_lobjmoreItems(

unsigned int gccurr_heapbound = 0;

inline void gettype_size(void * ptr,
                         int * ttype,
                         unsigned int * tsize) {
  int type = ((int *)ptr)[0];
  unsigned int size = 0;
  if(type < NUMCLASSES) {
    // a normal object
    size = classsize[type];
  } else {
    // an array
    struct ArrayObject *ao=(struct ArrayObject *)ptr;
    unsigned int elementsize=classsize[type];
    unsigned int length=ao->___length___;
    size=sizeof(struct ArrayObject)+length*elementsize;
  }  // if(type < NUMCLASSES)
  *ttype = type;
  *tsize = size;
}

inline bool isLarge(void * ptr,
                    int * ttype,
                    unsigned int * tsize) {
  GC_BAMBOO_DEBUGPRINT(0xe701);
  GC_BAMBOO_DEBUGPRINT_REG(ptr);
  // check if a pointer is referring to a large object
  gettype_size(ptr, ttype, tsize);
  GC_BAMBOO_DEBUGPRINT(*tsize);
  unsigned int bound = (BAMBOO_SMEM_SIZE);
  if(((unsigned int)ptr-gcbaseva) < (BAMBOO_LARGE_SMEM_BOUND)) {
    bound = (BAMBOO_SMEM_SIZE_L);
  }
  if((((unsigned int)ptr-gcbaseva)%(bound))==0) {
    // ptr is a start of a block
    GC_BAMBOO_DEBUGPRINT(0xe702);
    GC_BAMBOO_DEBUGPRINT(1);
    return true;
  }
  if((bound-(((unsigned int)ptr-gcbaseva)%bound)) < (*tsize)) {
    // it acrosses the boundary of current block
    GC_BAMBOO_DEBUGPRINT(0xe703);
    GC_BAMBOO_DEBUGPRINT(1);
    return true;
  }
  GC_BAMBOO_DEBUGPRINT(0);
  return false;
} // bool isLarge(void * ptr, int * ttype, int * tsize)

inline unsigned int hostcore(void * ptr) {
  // check the host core of ptr
  unsigned int host = 0;
  RESIDECORE(ptr, &host);
  GC_BAMBOO_DEBUGPRINT(0xedd0);
  GC_BAMBOO_DEBUGPRINT_REG(ptr);
  GC_BAMBOO_DEBUGPRINT_REG(host);
  return host;
} // int hostcore(void * ptr)

inline void cpu2coords(unsigned int coren,
	                   unsigned int * x,
					   unsigned int * y) {
  *x = bamboo_cpu2coords[2*coren];
  *y = bamboo_cpu2coords[2*coren+1];
} // void cpu2coords(...)

inline bool isLocal(void * ptr) {
  // check if a pointer is in shared heap on this core
  return hostcore(ptr) == BAMBOO_NUM_OF_CORE;
} // bool isLocal(void * ptr)

inline bool gc_checkCoreStatus_I() {
  bool allStall = true;
  for(int i = 0; i < NUMCORES4GC; ++i) {
    if(gccorestatus[i] != 0) {
      allStall = false;
      break;
    }  // if(gccorestatus[i] != 0)
  }  // for(i = 0; i < NUMCORES4GC; ++i)
  return allStall;
}

inline bool gc_checkAllCoreStatus_I() {
  bool allStall = true;
  for(int i = 0; i < NUMCORESACTIVE; ++i) {
    if(gccorestatus[i] != 0) {
      allStall = false;
      break;
    }  // if(gccorestatus[i] != 0)
  }  // for(i = 0; i < NUMCORESACTIVE; ++i)
  return allStall;
}

inline void checkMarkStatue() {
  GC_BAMBOO_DEBUGPRINT(0xee01);
  int i;
  if((!waitconfirm) ||
     (waitconfirm && (numconfirm == 0))) {
    GC_BAMBOO_DEBUGPRINT(0xee02);
	unsigned int entry_index = 0;
	if(waitconfirm) {
	  // phase 2
	  entry_index = (gcnumsrobjs_index == 0) ? 1 : 0;
	} else {
	  // phase 1
	  entry_index = gcnumsrobjs_index;
	}
    BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
    gccorestatus[BAMBOO_NUM_OF_CORE] = 0;
    gcnumsendobjs[entry_index][BAMBOO_NUM_OF_CORE] = gcself_numsendobjs;
    gcnumreceiveobjs[entry_index][BAMBOO_NUM_OF_CORE] = gcself_numreceiveobjs;
    // check the status of all cores
    bool allStall = gc_checkAllCoreStatus_I();
    GC_BAMBOO_DEBUGPRINT(0xee03);
    if(allStall) {
      GC_BAMBOO_DEBUGPRINT(0xee04);
      // ask for confirm
      if(!waitconfirm) {
		GC_BAMBOO_DEBUGPRINT(0xee05);
		// the first time found all cores stall
		// send out status confirm msg to all other cores
		// reset the corestatus array too
		gccorestatus[BAMBOO_NUM_OF_CORE] = 1;
		waitconfirm = true;
		numconfirm = NUMCORESACTIVE - 1;
		BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
		for(i = 1; i < NUMCORESACTIVE; ++i) {
		  gccorestatus[i] = 1;
		  // send mark phase finish confirm request msg to core i
		  send_msg_1(i, GCMARKCONFIRM, false);
		}  // for(i = 1; i < NUMCORESACTIVE; ++i)
      } else {
		// Phase 2
		// check if the sum of send objs and receive obj are the same
		// yes->check if the info is the latest; no->go on executing
		unsigned int sumsendobj = 0;
		for(i = 0; i < NUMCORESACTIVE; ++i) {
		  sumsendobj += gcnumsendobjs[gcnumsrobjs_index][i];
		}  // for(i = 0; i < NUMCORESACTIVE; ++i)
		GC_BAMBOO_DEBUGPRINT(0xee06);
		GC_BAMBOO_DEBUGPRINT_REG(sumsendobj);
		for(i = 0; i < NUMCORESACTIVE; ++i) {
		  sumsendobj -= gcnumreceiveobjs[gcnumsrobjs_index][i];
		}  // for(i = 0; i < NUMCORESACTIVE; ++i)
		GC_BAMBOO_DEBUGPRINT(0xee07);
		GC_BAMBOO_DEBUGPRINT_REG(sumsendobj);
		if(0 == sumsendobj) {
		  // Check if there are changes of the numsendobjs or numreceiveobjs on
		  // each core
		  bool ischanged = false;
		  for(i = 0; i < NUMCORESACTIVE; ++i) {
			if((gcnumsendobjs[0][i] != gcnumsendobjs[1][i]) || 
				(gcnumreceiveobjs[0][i] != gcnumreceiveobjs[1][i]) ) {
			  ischanged = true;
			  break;
			}
		  }  // for(i = 0; i < NUMCORESACTIVE; ++i)
		  GC_BAMBOO_DEBUGPRINT(0xee08);
		  GC_BAMBOO_DEBUGPRINT_REG(ischanged);
		  if(!ischanged) {
			GC_BAMBOO_DEBUGPRINT(0xee09);
			// all the core status info are the latest
			// stop mark phase
			gcphase = COMPACTPHASE;
			// restore the gcstatus for all cores
			for(i = 0; i < NUMCORESACTIVE; ++i) {
			  gccorestatus[i] = 1;
			}  // for(i = 0; i < NUMCORESACTIVE; ++i)
		  } else {
			// There were changes between phase 1 and phase 2, can not decide 
			// whether the mark phase has been finished
			waitconfirm = false;
			// As it fails in phase 2, flip the entries
			gcnumsrobjs_index = (gcnumsrobjs_index == 0) ? 1 : 0;
		  } // if(!ischanged)
		} else {
		  // There were changes between phase 1 and phase 2, can not decide 
		  // whether the mark phase has been finished
		  waitconfirm = false;
		  // As it fails in phase 2, flip the entries
		  gcnumsrobjs_index = (gcnumsrobjs_index == 0) ? 1 : 0;
		} // if(0 == sumsendobj) else ...
		BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
      } // if(!gcwaitconfirm) else()
    } else {
	  BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
    } // if(allStall)
  }  // if((!waitconfirm)...
  GC_BAMBOO_DEBUGPRINT(0xee0a);
} // void checkMarkStatue()

inline void initGC() {
  int i;
  if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
    for(i = 0; i < NUMCORES4GC; ++i) {
      gccorestatus[i] = 1;
      gcnumsendobjs[0][i] = gcnumsendobjs[1][i] = 0;
      gcnumreceiveobjs[0][i] = gcnumreceiveobjs[1][i] = 0;
      gcloads[i] = 0;
      gcrequiredmems[i] = 0;
      gcfilledblocks[i] = 0;
      gcstopblock[i] = 0;
    } // for(i = 0; i < NUMCORES4GC; ++i)
    for(i = NUMCORES4GC; i < NUMCORESACTIVE; ++i) {
      gccorestatus[i] = 1;
      gcnumsendobjs[0][i] = gcnumsendobjs[1][i] = 0;
      gcnumreceiveobjs[0][i] = gcnumreceiveobjs[1][i] = 0;
    }
    gcheaptop = 0;
    gctopcore = 0;
    gctopblock = 0;
#ifdef GC_TBL_DEBUG
	// initialize the gcmappingtbl
	BAMBOO_MEMSET_WH(gcmappingtbl, 0, bamboo_rmsp_size);
#endif
  } // if(STARTUPCORE == BAMBOO_NUM_OF_CORE)
  gcself_numsendobjs = 0;
  gcself_numreceiveobjs = 0;
  gcmarkedptrbound = 0;
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
    gclobjtailindex = gclobjtailindex2 = gclobjheadindex = 0;
    gclobjtail = gclobjtail2 = gclobjhead;
  }
  gclobjhead->next = gclobjhead->prev = NULL;

  freeMGCHash(gcforwardobjtbl);
  gcforwardobjtbl = allocateMGCHash(20, 3);

#ifdef GC_PROFILE
  gc_num_livespace = 0;
  gc_num_freespace = 0;
  gc_num_lobj = 0;
  gc_num_lobjspace = 0;
  gc_num_liveobj = 0;
  gc_num_forwardobj = 0;
  gc_num_profiles = NUMCORESACTIVE - 1;
#endif
} // void initGC()

// compute load balance for all cores
inline int loadbalance(unsigned int * heaptop) {
  // compute load balance
  int i;

  // get the total loads
  unsigned int tloads = gcloads[STARTUPCORE];
  for(i = 1; i < NUMCORES4GC; i++) {
    tloads += gcloads[i];
  }
  *heaptop = gcbaseva + tloads;

  GC_BAMBOO_DEBUGPRINT(0xdddd);
  GC_BAMBOO_DEBUGPRINT_REG(tloads);
  GC_BAMBOO_DEBUGPRINT_REG(*heaptop);
  unsigned int b = 0;
  BLOCKINDEX(*heaptop, &b);
  unsigned int numbpc = (unsigned int)b/(unsigned int)(NUMCORES4GC);// num of blocks per core
  GC_BAMBOO_DEBUGPRINT_REG(b);
  GC_BAMBOO_DEBUGPRINT_REG(numbpc);
  gctopblock = b;
  RESIDECORE(heaptop, &gctopcore);
  GC_BAMBOO_DEBUGPRINT_REG(gctopcore);
  return numbpc;
} // void loadbalance(int * heaptop)

inline bool cacheLObjs() {
  // check the total mem size need for large objs
  unsigned long long sumsize = 0;
  unsigned int size = 0;
  GC_BAMBOO_DEBUGPRINT(0xe801);
  gclobjtail2 = gclobjtail;
  gclobjtailindex2 = gclobjtailindex;
  unsigned int tmp_lobj = 0;
  unsigned int tmp_len = 0;
  unsigned int tmp_host = 0;
  // compute total mem size required and sort the lobjs in ascending order
  // TODO USE QUICK SORT INSTEAD?
  while(gc_lobjmoreItems2_I()) {
    gc_lobjdequeue2_I();
    tmp_lobj = gclobjtail2->lobjs[gclobjtailindex2-1];
    tmp_host = gclobjtail2->hosts[gclobjtailindex2-1];
    tmp_len = gclobjtail2->lengths[gclobjtailindex2 - 1];
    sumsize += tmp_len;
#ifdef GC_PROFILE
#ifdef MGC_SPEC
	if((STARTUPCORE != BAMBOO_NUM_OF_CORE) || gc_profile_flag) {
#endif
	gc_num_lobj++;
#ifdef MGC_SPEC
	}
#endif
#endif
    GC_BAMBOO_DEBUGPRINT_REG(gclobjtail2->lobjs[gclobjtailindex2-1]);
    GC_BAMBOO_DEBUGPRINT_REG(tmp_len);
    GC_BAMBOO_DEBUGPRINT_REG(sumsize);
    unsigned int i = gclobjtailindex2-1;
    struct lobjpointerblock * tmp_block = gclobjtail2;
    // find the place to insert
    while(true) {
      if(i == 0) {
		if(tmp_block->prev == NULL) {
		  break;
		}
		if(tmp_block->prev->lobjs[NUMLOBJPTRS-1] > tmp_lobj) {
		  tmp_block->lobjs[i] = tmp_block->prev->lobjs[NUMLOBJPTRS-1];
		  tmp_block->lengths[i] = tmp_block->prev->lengths[NUMLOBJPTRS-1];
		  tmp_block->hosts[i] = tmp_block->prev->hosts[NUMLOBJPTRS-1];
		  tmp_block = tmp_block->prev;
		  i = NUMLOBJPTRS-1;
		} else {
		  break;
		}  // if(tmp_block->prev->lobjs[NUMLOBJPTRS-1] < tmp_lobj)
	  } else {
		if(tmp_block->lobjs[i-1] > tmp_lobj) {
		  tmp_block->lobjs[i] = tmp_block->lobjs[i-1];
		  tmp_block->lengths[i] = tmp_block->lengths[i-1];
		  tmp_block->hosts[i] = tmp_block->hosts[i-1];
		  i--;
		} else {
		  break;
		}  // if(tmp_block->lobjs[i-1] < tmp_lobj)
      }  // if(i ==0 ) else {}
    }   // while(true)
    // insert it
    if(i != gclobjtailindex2 - 1) {
      tmp_block->lobjs[i] = tmp_lobj;
      tmp_block->lengths[i] = tmp_len;
      tmp_block->hosts[i] = tmp_host;
    }
  }  // while(gc_lobjmoreItems2())

#ifdef GC_PROFILE
#ifdef MGC_SPEC
	if((STARTUPCORE != BAMBOO_NUM_OF_CORE) || gc_profile_flag) {
#endif
  gc_num_lobjspace = sumsize;
#ifdef MGC_SPEC
	}
#endif
#endif
  // check if there are enough space to cache these large objs
  unsigned int dst = gcbaseva + (BAMBOO_SHARED_MEM_SIZE) -sumsize;
  if((unsigned long long)gcheaptop > (unsigned long long)dst) {
    // do not have enough room to cache large objs
    GC_BAMBOO_DEBUGPRINT(0xe802);
    GC_BAMBOO_DEBUGPRINT_REG(dst);
    GC_BAMBOO_DEBUGPRINT_REG(gcheaptop);
	GC_BAMBOO_DEBUGPRINT_REG(sumsize);
    return false;
  }
  GC_BAMBOO_DEBUGPRINT(0xe803);
  GC_BAMBOO_DEBUGPRINT_REG(dst);
  GC_BAMBOO_DEBUGPRINT_REG(gcheaptop);

  gcheaptop = dst; // Note: record the start of cached lobjs with gcheaptop
  // cache the largeObjs to the top of the shared heap
  dst = gcbaseva + (BAMBOO_SHARED_MEM_SIZE);
  while(gc_lobjmoreItems3_I()) {
    gc_lobjdequeue3_I();
    size = gclobjtail2->lengths[gclobjtailindex2];
    // set the mark field to , indicating that this obj has been moved
    // and need to be flushed
    ((int *)(gclobjtail2->lobjs[gclobjtailindex2]))[BAMBOOMARKBIT] = COMPACTED;
    dst -= size;
    if((unsigned int)dst < 
		(unsigned int)(gclobjtail2->lobjs[gclobjtailindex2]+size)) {
      memmove(dst, gclobjtail2->lobjs[gclobjtailindex2], size);
    } else {
      memcpy(dst, gclobjtail2->lobjs[gclobjtailindex2], size);
    }
    GC_BAMBOO_DEBUGPRINT(0x804);
    GC_BAMBOO_DEBUGPRINT_REG(gclobjtail2->lobjs[gclobjtailindex2]);
    GC_BAMBOO_DEBUGPRINT(dst);
    GC_BAMBOO_DEBUGPRINT_REG(size);
    GC_BAMBOO_DEBUGPRINT_REG(*((int*)gclobjtail2->lobjs[gclobjtailindex2]));
    GC_BAMBOO_DEBUGPRINT_REG(*((int*)(dst)));
  }
  return true;
} // void cacheLObjs()

// update the bmmboo_smemtbl to record current shared mem usage
void updateSmemTbl(unsigned int coren,
                   unsigned int localtop) {
  unsigned int ltopcore = 0;
  unsigned int bound = BAMBOO_SMEM_SIZE_L;
  BLOCKINDEX(localtop, &ltopcore);
  if((unsigned int)localtop>=(unsigned int)(gcbaseva+BAMBOO_LARGE_SMEM_BOUND)){
    bound = BAMBOO_SMEM_SIZE;
  }
  unsigned int load = (unsigned int)(localtop-gcbaseva)%(unsigned int)bound;
  unsigned int i = 0;
  unsigned int j = 0;
  unsigned int toset = 0;
  do {
    toset = gc_core2block[2*coren+i]+(unsigned int)(NUMCORES4GC*2)*j;
#ifdef GC_TBL_DEBUG
	if(toset >= gcnumblock) {
	  tprintf("ltopcore: %d, localtop: %x, toset: %d, gcnumblock: %d (%d, %d) \n", ltopcore, localtop, toset, gcnumblock, i, j);
	  BAMBOO_EXIT(0xb001);
	}
#endif
    if(toset < ltopcore) {
      bamboo_smemtbl[toset]=
        (toset<NUMCORES4GC) ? BAMBOO_SMEM_SIZE_L : BAMBOO_SMEM_SIZE;
#ifdef SMEMM
	  gcmem_mixed_usedmem += bamboo_smemtbl[toset];
#endif
    } else if(toset == ltopcore) {
      bamboo_smemtbl[toset] = load;
#ifdef SMEMM
	  gcmem_mixed_usedmem += bamboo_smemtbl[toset];
#endif
      break;
    } else {
      break;
    }
    i++;
    if(i == 2) {
      i = 0;
      j++;
    }
  } while(true);
} // void updateSmemTbl(int, int)

inline void moveLObjs() {
  GC_BAMBOO_DEBUGPRINT(0xea01);
#ifdef SMEMM
  // update the gcmem_mixed_usedmem
  gcmem_mixed_usedmem = 0;
#endif
  // zero out the smemtbl
  BAMBOO_MEMSET_WH(bamboo_smemtbl, 0, sizeof(int)*gcnumblock);
  // find current heap top
  // flush all gcloads to indicate the real heap top on one core
  // previous it represents the next available ptr on a core
  if(((unsigned int)gcloads[0] > (unsigned int)(gcbaseva+BAMBOO_SMEM_SIZE_L))
     && (((unsigned int)gcloads[0]%(BAMBOO_SMEM_SIZE)) == 0)) {
    // edge of a block, check if this is exactly the heaptop
    BASEPTR(0, gcfilledblocks[0]-1, &(gcloads[0]));
    gcloads[0]+=(gcfilledblocks[0]>1 ?
                 (BAMBOO_SMEM_SIZE) : (BAMBOO_SMEM_SIZE_L));
  }
  updateSmemTbl(0, gcloads[0]);
  GC_BAMBOO_DEBUGPRINT(0xea02);
  GC_BAMBOO_DEBUGPRINT_REG(gcloads[0]);
  GC_BAMBOO_DEBUGPRINT_REG(bamboo_smemtbl[0]);
  for(int i = 1; i < NUMCORES4GC; i++) {
    unsigned int tmptop = 0;
    GC_BAMBOO_DEBUGPRINT(0xf000+i);
    GC_BAMBOO_DEBUGPRINT_REG(gcloads[i]);
    GC_BAMBOO_DEBUGPRINT_REG(gcfilledblocks[i]);
    if((gcfilledblocks[i] > 0)
       && (((unsigned int)gcloads[i] % (BAMBOO_SMEM_SIZE)) == 0)) {
      // edge of a block, check if this is exactly the heaptop
      BASEPTR(i, gcfilledblocks[i]-1, &gcloads[i]);
      gcloads[i] += 
		(gcfilledblocks[i]>1 ? (BAMBOO_SMEM_SIZE) : (BAMBOO_SMEM_SIZE_L));
      tmptop = gcloads[i];
    }
    updateSmemTbl(i, gcloads[i]);
    GC_BAMBOO_DEBUGPRINT_REG(gcloads[i]);
  } // for(int i = 1; i < NUMCORES4GC; i++) {

  // find current heap top
  // TODO
  // a bug here: when using local allocation, directly move large objects
  // to the highest free chunk might not be memory efficient
  unsigned int tmpheaptop = 0;
  unsigned int size = 0;
  unsigned int bound = 0;
  int i = 0;
  for(i = gcnumblock-1; i >= 0; i--) {
    if(bamboo_smemtbl[i] > 0) {
      break;
    }
  }
  if(i == -1) {
    tmpheaptop = gcbaseva;
  } else {
    tmpheaptop = gcbaseva+bamboo_smemtbl[i]+((i<NUMCORES4GC) ?
		(BAMBOO_SMEM_SIZE_L*i) :
        (BAMBOO_SMEM_SIZE*(i-NUMCORES4GC)+BAMBOO_LARGE_SMEM_BOUND));
  }

  // move large objs from gcheaptop to tmpheaptop
  // write the header first
  unsigned int tomove = gcbaseva+(BAMBOO_SHARED_MEM_SIZE)-gcheaptop;
#ifdef SMEMM
  gcmem_mixed_usedmem += tomove;
#endif
  GC_BAMBOO_DEBUGPRINT(0xea03);
  GC_BAMBOO_DEBUGPRINT_REG(tomove);
  GC_BAMBOO_DEBUGPRINT_REG(tmpheaptop);
  GC_BAMBOO_DEBUGPRINT_REG(gcheaptop);
  // flush the sbstartbl
  BAMBOO_MEMSET_WH(&(gcsbstarttbl[gcreservedsb]), '\0',
	  (BAMBOO_SHARED_MEM_SIZE/BAMBOO_SMEM_SIZE-(unsigned int)gcreservedsb)
	  *sizeof(unsigned int));
  if(tomove == 0) {
    gcheaptop = tmpheaptop;
  } else {
    // check how many blocks it acrosses
    unsigned int remain = tmpheaptop-gcbaseva;
    unsigned int sb = remain/BAMBOO_SMEM_SIZE+(unsigned int)gcreservedsb;//number of the sblock
    unsigned int b = 0;  // number of the block
    BLOCKINDEX(tmpheaptop, &b);
    // check the remaining space in this block
    bound = (BAMBOO_SMEM_SIZE);
    if(remain < (BAMBOO_LARGE_SMEM_BOUND)) {
      bound = (BAMBOO_SMEM_SIZE_L);
    }
    remain = bound - remain%bound;

    GC_BAMBOO_DEBUGPRINT(0xea04);
    size = 0;
    unsigned int isize = 0;
    unsigned int host = 0;
    unsigned int ptr = 0;
    unsigned int base = tmpheaptop;
    unsigned int cpysize = 0;
    remain -= BAMBOO_CACHE_LINE_SIZE;
    tmpheaptop += BAMBOO_CACHE_LINE_SIZE;
    gc_lobjqueueinit4_I();
    while(gc_lobjmoreItems4_I()) {
      ptr = (unsigned int)(gc_lobjdequeue4_I(&size, &host));
      ALIGNSIZE(size, &isize);
      if(remain < isize) {
		// this object acrosses blocks
		if(cpysize > 0) {
		  // close current block, fill its header
		  BAMBOO_MEMSET_WH(base, '\0', BAMBOO_CACHE_LINE_SIZE);
		  *((int*)base) = cpysize + BAMBOO_CACHE_LINE_SIZE;
		  bamboo_smemtbl[b]+=BAMBOO_CACHE_LINE_SIZE;//add the size of header
#ifdef GC_TBL_DEBUG
		  if(b >= gcnumblock) {
			BAMBOO_EXIT(0xb002);
		  }
#endif
		  cpysize = 0;
		  base = tmpheaptop;
		  if(remain == 0) {
			remain = ((tmpheaptop-gcbaseva)<(BAMBOO_LARGE_SMEM_BOUND)) ?
					 BAMBOO_SMEM_SIZE_L : BAMBOO_SMEM_SIZE;
		  }
		  remain -= BAMBOO_CACHE_LINE_SIZE;
		  tmpheaptop += BAMBOO_CACHE_LINE_SIZE;
		  BLOCKINDEX(tmpheaptop, &b);
		  sb = (unsigned int)(tmpheaptop-gcbaseva)/(BAMBOO_SMEM_SIZE)
			+gcreservedsb;
		}  // if(cpysize > 0)

		// move the large obj
		if((unsigned int)gcheaptop < (unsigned int)(tmpheaptop+size)) {
		  memmove(tmpheaptop, gcheaptop, size);
		} else {
		  //BAMBOO_WRITE_HINT_CACHE(tmpheaptop, size);
		  memcpy(tmpheaptop, gcheaptop, size);
		}
		// fill the remaining space with -2 padding
		BAMBOO_MEMSET_WH(tmpheaptop+size, -2, isize-size);
		GC_BAMBOO_DEBUGPRINT(0xea05);
		GC_BAMBOO_DEBUGPRINT_REG(gcheaptop);
		GC_BAMBOO_DEBUGPRINT_REG(tmpheaptop);
		GC_BAMBOO_DEBUGPRINT_REG(size);
		GC_BAMBOO_DEBUGPRINT_REG(isize);
		GC_BAMBOO_DEBUGPRINT_REG(base);
		gcheaptop += size;
#ifdef GC_TBL_DEBUG
		if((gcmappingtbl[OBJMAPPINGINDEX((unsigned int)ptr)] != 3)) {
		  tprintf("Error moveLobj: %x %x \n", 
			  (int)ptr, ((int *)(ptr))[BAMBOOMARKBIT] );
		  BAMBOO_EXIT(0xb003);
		}
#endif
		// cache the mapping info 
		gcmappingtbl[OBJMAPPINGINDEX((unsigned int)ptr)] = 
		  (unsigned int)tmpheaptop;
#ifdef GC_TBL_DEBUG
		if(gcmappingtbl[OBJMAPPINGINDEX((unsigned int)ptr)] == 
			gcmappingtbl[OBJMAPPINGINDEX((unsigned int)ptr)-1]) {
		  tprintf("Error moveobj ^^ : %x, %x, %d \n", (int)ptr, 
			  (int)tmpheaptop, OBJMAPPINGINDEX((unsigned int)ptr));
		  BAMBOO_EXIT(0xb004);
		}
#endif
		GC_BAMBOO_DEBUGPRINT(0xcdca);
		GC_BAMBOO_DEBUGPRINT_REG(ptr);
		GC_BAMBOO_DEBUGPRINT_REG(tmpheaptop);
		tmpheaptop += isize;

		// set the gcsbstarttbl and bamboo_smemtbl
		unsigned int tmpsbs=1+(unsigned int)(isize-remain-1)/BAMBOO_SMEM_SIZE;
		for(int k = 1; k < tmpsbs; k++) {
		  gcsbstarttbl[sb+k] = -1;
#ifdef GC_TBL_DEBUG
		  if((sb+k) >= gcsbstarttbl_len) {
			BAMBOO_EXIT(0xb005);
		  }
#endif
		}
		sb += tmpsbs;
		bound = (b<NUMCORES4GC) ? BAMBOO_SMEM_SIZE_L : BAMBOO_SMEM_SIZE;
		BLOCKINDEX(tmpheaptop-1, &tmpsbs);
		for(; b < tmpsbs; b++) {
		  bamboo_smemtbl[b] = bound;
#ifdef GC_TBL_DEBUG
		  if(b >= gcnumblock) {
			BAMBOO_EXIT(0xb006);
		  }
#endif
		  if(b==NUMCORES4GC-1) {
			bound = BAMBOO_SMEM_SIZE;
		  }
		}
		if(((unsigned int)(isize-remain)%(BAMBOO_SMEM_SIZE)) == 0) {
		  gcsbstarttbl[sb] = -1;
		  remain = ((tmpheaptop-gcbaseva)<(BAMBOO_LARGE_SMEM_BOUND)) ?
				   BAMBOO_SMEM_SIZE_L : BAMBOO_SMEM_SIZE;
		  bamboo_smemtbl[b] = bound;
		} else {
		  gcsbstarttbl[sb] = (int)tmpheaptop;
		  remain = tmpheaptop-gcbaseva;
		  bamboo_smemtbl[b] = remain%bound;
		  remain = bound - bamboo_smemtbl[b];
		} // if(((isize-remain)%(BAMBOO_SMEM_SIZE)) == 0) else ...
#ifdef GC_TBL_DEBUG
		if(sb >= gcsbstarttbl_len) {
		  BAMBOO_EXIT(0xb007);
		}
		if(b >= gcnumblock) {
		  BAMBOO_EXIT(0xb008);
		}
#endif

		// close current block and fill the header
		BAMBOO_MEMSET_WH(base, '\0', BAMBOO_CACHE_LINE_SIZE);
		*((int*)base) = isize + BAMBOO_CACHE_LINE_SIZE;
		cpysize = 0;
		base = tmpheaptop;
		if(remain == BAMBOO_CACHE_LINE_SIZE) {
		  // fill with 0 in case
		  BAMBOO_MEMSET_WH(tmpheaptop, '\0', remain);
		}
		remain -= BAMBOO_CACHE_LINE_SIZE;
		tmpheaptop += BAMBOO_CACHE_LINE_SIZE;
      } else {
		remain -= isize;
		// move the large obj
		if((unsigned int)gcheaptop < (unsigned int)(tmpheaptop+size)) {
		  memmove(tmpheaptop, gcheaptop, size);
		} else {
		  memcpy(tmpheaptop, gcheaptop, size);
		}
		// fill the remaining space with -2 padding
		BAMBOO_MEMSET_WH(tmpheaptop+size, -2, isize-size);
		GC_BAMBOO_DEBUGPRINT(0xea06);
		GC_BAMBOO_DEBUGPRINT_REG(gcheaptop);
		GC_BAMBOO_DEBUGPRINT_REG(tmpheaptop);
		GC_BAMBOO_DEBUGPRINT_REG(size);
		GC_BAMBOO_DEBUGPRINT_REG(isize);

		gcheaptop += size;
		cpysize += isize;
#ifdef GC_TBL_DEBUG
		if((gcmappingtbl[OBJMAPPINGINDEX((unsigned int)ptr)] != 3)) {
		  tprintf("Error moveLobj: %x %x \n", (int)ptr,
			  ((int *)(ptr))[BAMBOOMARKBIT] );
		  BAMBOO_EXIT(0xb009);
		}
#endif
		// cache the mapping info
		gcmappingtbl[OBJMAPPINGINDEX((unsigned int)ptr)] = 
		  (unsigned int)tmpheaptop;
#ifdef GC_TBL_DEBUG
		if(gcmappingtbl[OBJMAPPINGINDEX((unsigned int)ptr)] == 
			gcmappingtbl[OBJMAPPINGINDEX((unsigned int)ptr)-1]) {
		  tprintf("Error moveobj ?? : %x, %x, %d \n", (int)ptr, 
			  (int)tmpheaptop, OBJMAPPINGINDEX((unsigned int)ptr));
		  BAMBOO_EXIT(0xb00a);
		}
		if(!ISSHAREDOBJ(tmpheaptop)) {
		  tprintf("Error: %x, %x \n", (int)ptr, (int)tmpheaptop);
		  BAMBOO_EXIT(0xb00b);
		}
#endif
		GC_BAMBOO_DEBUGPRINT(0xcdcc);
		GC_BAMBOO_DEBUGPRINT_REG(ptr);
		GC_BAMBOO_DEBUGPRINT_REG(tmpheaptop);
		GC_BAMBOO_DEBUGPRINT_REG(*((int*)tmpheaptop));
		tmpheaptop += isize;

		// update bamboo_smemtbl
		bamboo_smemtbl[b] += isize;
#ifdef GC_TBL_DEBUG
		if(b >= gcnumblock) {
		  BAMBOO_EXIT(0xb00c);
		}
#endif
	  }  // if(remain < isize) else ...
    }  // while(gc_lobjmoreItems())
    if(cpysize > 0) {
      // close current block, fill the header
      BAMBOO_MEMSET_WH(base, '\0', BAMBOO_CACHE_LINE_SIZE);
      *((int*)base) = cpysize + BAMBOO_CACHE_LINE_SIZE;
      bamboo_smemtbl[b] += BAMBOO_CACHE_LINE_SIZE;//add the size of the header
#ifdef GC_TBL_DEBUG
	  if(b >= gcnumblock) {
		BAMBOO_EXIT(0xb00d);
	  }
#endif
    } else {
      tmpheaptop -= BAMBOO_CACHE_LINE_SIZE;
    }
    gcheaptop = tmpheaptop;

  } // if(tomove == 0)

  GC_BAMBOO_DEBUGPRINT(0xea07);
  GC_BAMBOO_DEBUGPRINT_REG(gcheaptop);

  bamboo_free_block = 0;
  unsigned int tbound = 0;
  do {
    tbound = (bamboo_free_block<NUMCORES4GC) ?
             BAMBOO_SMEM_SIZE_L : BAMBOO_SMEM_SIZE;
    if(bamboo_smemtbl[bamboo_free_block] == tbound) {
      bamboo_free_block++;
    } else {
      // the first non-full partition
      break;
    }
  } while(true);
#ifdef GC_TBL_DEBUG
  if(bamboo_free_block >= gcnumblock) {
	BAMBOO_EXIT(0xb00e);
  }
#endif

#ifdef GC_PROFILE
#ifdef MGC_SPEC
	if((STARTUPCORE != BAMBOO_NUM_OF_CORE) || gc_profile_flag) {
#endif
  // check how many live space there are
  gc_num_livespace = 0;
  for(int tmpi = 0; tmpi < gcnumblock; tmpi++) {
	gc_num_livespace += bamboo_smemtbl[tmpi];
  }
  gc_num_freespace = (BAMBOO_SHARED_MEM_SIZE) - gc_num_livespace;
#ifdef MGC_SPEC
	}
#endif
#endif
  GC_BAMBOO_DEBUGPRINT(0xea08);
  GC_BAMBOO_DEBUGPRINT_REG(gcheaptop);
} // void moveLObjs()

inline void markObj(void * objptr) {
  if(objptr == NULL) {
    return;
  }
  if(ISSHAREDOBJ(objptr)) {
    unsigned int host = hostcore(objptr);
    if(BAMBOO_NUM_OF_CORE == host) {
      // on this core
      BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
      if(((int *)objptr)[BAMBOOMARKBIT] == INIT) {
		// this is the first time that this object is discovered,
		// set the flag as DISCOVERED
		((int *)objptr)[BAMBOOMARKBIT] = DISCOVERED;
		BAMBOO_CACHE_FLUSH_LINE(objptr);
		gc_enqueue_I(objptr);
#ifdef GC_TBL_DEBUG
		// for test
		gcmappingtbl[OBJMAPPINGINDEX((unsigned int)objptr)]=1;
#endif
	  }
      BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
    } else {
      GC_BAMBOO_DEBUGPRINT(0xbbbb);
      GC_BAMBOO_DEBUGPRINT_REG(host);
      GC_BAMBOO_DEBUGPRINT_REG(objptr);
      // check if this obj has been forwarded
      if(!MGCHashcontains(gcforwardobjtbl, (int)objptr)) {
		// send a msg to host informing that objptr is active
		send_msg_2(host, GCMARKEDOBJ, objptr, false);
#ifdef GC_PROFILE
#ifdef MGC_SPEC
	if((STARTUPCORE != BAMBOO_NUM_OF_CORE) || gc_profile_flag) {
#endif
		gc_num_forwardobj++;
#ifdef MGC_SPEC
	}
#endif
#endif // GC_PROFILE
		gcself_numsendobjs++;
		MGCHashadd(gcforwardobjtbl, (int)objptr);
      }
    }
  } else {
#ifdef GC_TBL_DEBUG
	tprintf("Non shared pointer to be marked %x \n", (int)objptr);
	BAMBOO_EXIT(0xb00f);
#endif
    BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
    gc_enqueue_I(objptr);
    BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
  }  // if(ISSHAREDOBJ(objptr))
} // void markObj(void * objptr)

// enqueue root objs
inline void tomark(struct garbagelist * stackptr) {
  if(MARKPHASE != gcphase) {
    GC_BAMBOO_DEBUGPRINT_REG(gcphase);
    BAMBOO_EXIT(0xb010);
  }
  gcbusystatus = true;
  gcnumlobjs = 0;


  int i,j;
  // enqueue current stack
  while(stackptr!=NULL) {
    GC_BAMBOO_DEBUGPRINT(0xe501);
    GC_BAMBOO_DEBUGPRINT_REG(stackptr->size);
    GC_BAMBOO_DEBUGPRINT_REG(stackptr->next);
    GC_BAMBOO_DEBUGPRINT_REG(stackptr->array[0]);
    for(i=0; i<stackptr->size; i++) {
      if(stackptr->array[i] != NULL) {
		markObj(stackptr->array[i]);
      }
    }
    stackptr=stackptr->next;
  }
  GC_BAMBOO_DEBUGPRINT(0xe502);

  // enqueue static pointers global_defs_p
  if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
	struct garbagelist * staticptr=(struct garbagelist *)global_defs_p;
	while(staticptr != NULL) {
	  for(i=0; i<staticptr->size; i++) {
		if(staticptr->array[i] != NULL) {
		  markObj(staticptr->array[i]);
		}
	  }
	  staticptr = staticptr->next;
	}
  }
  GC_BAMBOO_DEBUGPRINT(0xe503);

#ifdef TASK
  // enqueue objectsets
  if(BAMBOO_NUM_OF_CORE < NUMCORESACTIVE) {
    for(i=0; i<NUMCLASSES; i++) {
      struct parameterwrapper ** queues =
        objectqueues[BAMBOO_NUM_OF_CORE][i];
      int length = numqueues[BAMBOO_NUM_OF_CORE][i];
      for(j = 0; j < length; ++j) {
		struct parameterwrapper * parameter = queues[j];
		struct ObjectHash * set=parameter->objectset;
		struct ObjectNode * ptr=set->listhead;
		while(ptr!=NULL) {
		  markObj((void *)ptr->key);
		  ptr=ptr->lnext;
		}
      }
    }
  }

  // euqueue current task descriptor
  if(currtpd != NULL) {
    GC_BAMBOO_DEBUGPRINT(0xe504);
    for(i=0; i<currtpd->numParameters; i++) {
      markObj(currtpd->parameterArray[i]);
    }
  }

  GC_BAMBOO_DEBUGPRINT(0xe505);
  // euqueue active tasks
  if(activetasks != NULL) {
    struct genpointerlist * ptr=activetasks->list;
    while(ptr!=NULL) {
      struct taskparamdescriptor *tpd=ptr->src;
      int i;
      for(i=0; i<tpd->numParameters; i++) {
		markObj(tpd->parameterArray[i]);
      }
      ptr=ptr->inext;
    }
  }

  GC_BAMBOO_DEBUGPRINT(0xe506);
  // enqueue cached transferred obj
  struct QueueItem * tmpobjptr =  getHead(&objqueue);
  while(tmpobjptr != NULL) {
    struct transObjInfo * objInfo =
      (struct transObjInfo *)(tmpobjptr->objectptr);
    markObj(objInfo->objptr);
    tmpobjptr = getNextQueueItem(tmpobjptr);
  }

  GC_BAMBOO_DEBUGPRINT(0xe507);
  // enqueue cached objs to be transferred
  struct QueueItem * item = getHead(totransobjqueue);
  while(item != NULL) {
    struct transObjInfo * totransobj =
      (struct transObjInfo *)(item->objectptr);
    markObj(totransobj->objptr);
    item = getNextQueueItem(item);
  } // while(item != NULL)

  GC_BAMBOO_DEBUGPRINT(0xe508);
  // enqueue lock related info
  for(i = 0; i < runtime_locklen; ++i) {
    markObj((void *)(runtime_locks[i].redirectlock));
    if(runtime_locks[i].value != NULL) {
      markObj((void *)(runtime_locks[i].value));
    }
  }
  GC_BAMBOO_DEBUGPRINT(0xe509);
#endif 

#ifdef MGC
  // enqueue global thread queue
  if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
	lockthreadqueue();
	unsigned int thread_counter = *((unsigned int*)(bamboo_thread_queue+1));
	if(thread_counter > 0) {
	  unsigned int start = *((unsigned int*)(bamboo_thread_queue+2));
	  for(i = thread_counter; i > 0; i--) {
		markObj((void *)bamboo_thread_queue[4+start]);
		start = (start+1)&bamboo_max_thread_num_mask;
	  }
	}
  }

  // enqueue the bamboo_threadlocks
  for(i = 0; i < bamboo_threadlocks.index; i++) {
	markObj((void *)(bamboo_threadlocks.locks[i].object));
  }

  // enqueue the bamboo_current_thread
  if(bamboo_current_thread != 0) {
	markObj((void *)bamboo_current_thread);
  }

  GC_BAMBOO_DEBUGPRINT(0xe50a);
#endif
} // void tomark(struct garbagelist * stackptr)

inline void mark(bool isfirst,
                 struct garbagelist * stackptr) {
  if(BAMBOO_NUM_OF_CORE == 0) GC_BAMBOO_DEBUGPRINT(0xed01);
  if(isfirst) {
    if(BAMBOO_NUM_OF_CORE == 0) GC_BAMBOO_DEBUGPRINT(0xed02);
    // enqueue root objs
    tomark(stackptr);
    gccurr_heaptop = 0; // record the size of all active objs in this core
                        // aligned but does not consider block boundaries
    gcmarkedptrbound = 0;
  }
  if(BAMBOO_NUM_OF_CORE == 0) GC_BAMBOO_DEBUGPRINT(0xed03);
  unsigned int isize = 0;
  bool checkfield = true;
  bool sendStall = false;
  // mark phase
  while(MARKPHASE == gcphase) {
    if(BAMBOO_NUM_OF_CORE == 0) GC_BAMBOO_DEBUGPRINT(0xed04);
    while(true) {
      BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
      bool hasItems = gc_moreItems2_I();
      BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
      GC_BAMBOO_DEBUGPRINT(0xed05);
      if(!hasItems) {
		break;
      }
      sendStall = false;
      gcbusystatus = true;
      checkfield = true;
      void * ptr = gc_dequeue2_I();

      GC_BAMBOO_DEBUGPRINT_REG(ptr);
      unsigned int size = 0;
      unsigned int isize = 0;
      unsigned int type = 0;
      // check if it is a shared obj
      if(ISSHAREDOBJ(ptr)) {
		// a shared obj, check if it is a local obj on this core
		unsigned int host = hostcore(ptr);
		bool islocal = (host == BAMBOO_NUM_OF_CORE);
		if(islocal) {
		  bool isnotmarked = (((int *)ptr)[BAMBOOMARKBIT] == DISCOVERED);
		  if(isLarge(ptr, &type, &size) && isnotmarked) {
			// ptr is a large object and not marked or enqueued
			GC_BAMBOO_DEBUGPRINT(0xecec);
			GC_BAMBOO_DEBUGPRINT_REG(ptr);
			GC_BAMBOO_DEBUGPRINT_REG(*((int*)ptr));
			BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
			gc_lobjenqueue_I(ptr, size, BAMBOO_NUM_OF_CORE);
			gcnumlobjs++;
			BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
			// mark this obj
			((int *)ptr)[BAMBOOMARKBIT] = MARKED;
			BAMBOO_CACHE_FLUSH_LINE(ptr);
#ifdef GC_TBL_DEBUG
			// for test
			gcmappingtbl[OBJMAPPINGINDEX((unsigned int)ptr)]=3;
#endif
		  } else if(isnotmarked) {
			// ptr is an unmarked active object on this core
			ALIGNSIZE(size, &isize);
			gccurr_heaptop += isize;
			GC_BAMBOO_DEBUGPRINT(0xaaaa);
			GC_BAMBOO_DEBUGPRINT_REG(ptr);
			GC_BAMBOO_DEBUGPRINT_REG(isize);
			GC_BAMBOO_DEBUGPRINT(((int *)(ptr))[0]);
			// mark this obj
			((int *)ptr)[BAMBOOMARKBIT] = MARKED;
			BAMBOO_CACHE_FLUSH_LINE(ptr);
#ifdef GC_TBL_DEBUG
			// for test
			gcmappingtbl[OBJMAPPINGINDEX((unsigned int)ptr)]=2;
#endif
		  
			if((unsigned int)(ptr + size) > (unsigned int)gcmarkedptrbound) {
			  gcmarkedptrbound = (unsigned int)(ptr + size);
			} // if(ptr + size > gcmarkedptrbound)
		  } else {
			// ptr is not an active obj or has been marked
			checkfield = false;
		  } // if(isLarge(ptr, &type, &size)) else ...
		} 
#ifdef GC_TBL_DEBUG
		else {
		  tprintf("Error mark: %x, %d, %d \n", (int)ptr, BAMBOO_NUM_OF_CORE, 
			  hostcore(ptr));
		  BAMBOO_EXIT(0xb011);
		}
#endif /* can never reach here
		else {
		  // check if this obj has been forwarded
		  if(!MGCHashcontains(gcforwardobjtbl, (int)ptr)) {
			// send a msg to host informing that ptr is active
			send_msg_2(host, GCMARKEDOBJ, ptr, false);
			gcself_numsendobjs++;
			MGCHashadd(gcforwardobjtbl, (int)ptr);
		  }
			checkfield = false;
		}// if(isLocal(ptr)) else ...*/
	  }   // if(ISSHAREDOBJ(ptr))
      GC_BAMBOO_DEBUGPRINT(0xed06);

      if(checkfield) {
		// scan all pointers in ptr
		unsigned int * pointer;
		pointer=pointerarray[type];
		if (pointer==0) {
		  /* Array of primitives */
		  /* Do nothing */
		} else if (((unsigned int)pointer)==1) {
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
		  unsigned int size=pointer[0];
		  int i;
		  for(i=1; i<=size; i++) {
			unsigned int offset=pointer[i];
			void * objptr=*((void **)(((char *)ptr)+offset));
			markObj(objptr);
		  }
		}     // if (pointer==0) else if ... else ...
		{
		  pointer=pointerarray[OBJECTTYPE];
		  //handle object class
		  unsigned int size=pointer[0];
		  int i;
		  for(i=1; i<=size; i++) {
			unsigned int offset=pointer[i];
			void * objptr=*((void **)(((char *)ptr)+offset));
			markObj(objptr);
		  }
		}
      }   // if(checkfield)
    }     // while(gc_moreItems2())
    GC_BAMBOO_DEBUGPRINT(0xed07);
	gcbusystatus = false;
    // send mark finish msg to core coordinator
    if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
      GC_BAMBOO_DEBUGPRINT(0xed08);
	  int entry_index = 0;
	  if(waitconfirm)  {
		// phase 2
		entry_index = (gcnumsrobjs_index == 0) ? 1 : 0;
	  } else {
		// phase 1
		entry_index = gcnumsrobjs_index;
	  }
      gccorestatus[BAMBOO_NUM_OF_CORE] = 0;
      gcnumsendobjs[entry_index][BAMBOO_NUM_OF_CORE]=gcself_numsendobjs;
      gcnumreceiveobjs[entry_index][BAMBOO_NUM_OF_CORE]=gcself_numreceiveobjs;
      gcloads[BAMBOO_NUM_OF_CORE] = gccurr_heaptop;
    } else {
      if(!sendStall) {
		GC_BAMBOO_DEBUGPRINT(0xed09);
		send_msg_4(STARTUPCORE, GCFINISHMARK, BAMBOO_NUM_OF_CORE,
				   gcself_numsendobjs, gcself_numreceiveobjs, false);
		sendStall = true;
      }
    }  // if(STARTUPCORE == BAMBOO_NUM_OF_CORE) ...
    GC_BAMBOO_DEBUGPRINT(0xed0a);

    if(BAMBOO_NUM_OF_CORE == STARTUPCORE) {
      GC_BAMBOO_DEBUGPRINT(0xed0b);
      return;
    }
  } // while(MARKPHASE == gcphase)

  BAMBOO_CACHE_MF();
} // mark()

inline void compact2Heaptophelper_I(unsigned int coren,
                                    unsigned int* p,
                                    unsigned int* numblocks,
                                    unsigned int* remain) {
  unsigned int b;
  unsigned int memneed = gcrequiredmems[coren] + BAMBOO_CACHE_LINE_SIZE;
  if(STARTUPCORE == coren) {
    gctomove = true;
    gcmovestartaddr = *p;
    gcdstcore = gctopcore;
    gcblock2fill = *numblocks + 1;
  } else {
    send_msg_4(coren, GCMOVESTART, gctopcore, *p, (*numblocks) + 1, false);
  }
  GC_BAMBOO_DEBUGPRINT_REG(coren);
  GC_BAMBOO_DEBUGPRINT_REG(gctopcore);
  GC_BAMBOO_DEBUGPRINT_REG(*p);
  GC_BAMBOO_DEBUGPRINT_REG(*numblocks+1);
  if(memneed < *remain) {
    GC_BAMBOO_DEBUGPRINT(0xd104);
    *p = *p + memneed;
    gcrequiredmems[coren] = 0;
    gcloads[gctopcore] += memneed;
    *remain = *remain - memneed;
  } else {
    GC_BAMBOO_DEBUGPRINT(0xd105);
    // next available block
    *p = *p + *remain;
    gcfilledblocks[gctopcore] += 1;
    unsigned int newbase = 0;
    BASEPTR(gctopcore, gcfilledblocks[gctopcore], &newbase);
    gcloads[gctopcore] = newbase;
    gcrequiredmems[coren] -= *remain - BAMBOO_CACHE_LINE_SIZE;
    gcstopblock[gctopcore]++;
    gctopcore = NEXTTOPCORE(gctopblock);
    gctopblock++;
    *numblocks = gcstopblock[gctopcore];
    *p = gcloads[gctopcore];
    BLOCKINDEX(*p, &b);
    *remain=(b<NUMCORES4GC) ?
             ((BAMBOO_SMEM_SIZE_L)-((*p)%(BAMBOO_SMEM_SIZE_L)))
	     : ((BAMBOO_SMEM_SIZE)-((*p)%(BAMBOO_SMEM_SIZE)));
    GC_BAMBOO_DEBUGPRINT(0xd106);
    GC_BAMBOO_DEBUGPRINT_REG(gctopcore);
    GC_BAMBOO_DEBUGPRINT_REG(*p);
    GC_BAMBOO_DEBUGPRINT_REG(b);
    GC_BAMBOO_DEBUGPRINT_REG(*remain);
  }  // if(memneed < remain)
  gcmovepending--;
} // void compact2Heaptophelper_I(int, int*, int*, int*)

inline void compact2Heaptop() {
  // no cores with spare mem and some cores are blocked with pending move
  // find the current heap top and make them move to the heap top
  unsigned int p;
  unsigned int numblocks = gcfilledblocks[gctopcore];
  p = gcloads[gctopcore];
  unsigned int b;
  BLOCKINDEX(p, &b);
  unsigned int remain = (b<NUMCORES4GC) ?
               ((BAMBOO_SMEM_SIZE_L)-(p%(BAMBOO_SMEM_SIZE_L)))
	       : ((BAMBOO_SMEM_SIZE)-(p%(BAMBOO_SMEM_SIZE)));
  // check if the top core finishes
  BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
  if(gccorestatus[gctopcore] != 0) {
    GC_BAMBOO_DEBUGPRINT(0xd101);
    GC_BAMBOO_DEBUGPRINT_REG(gctopcore);
    // let the top core finishes its own work first
    compact2Heaptophelper_I(gctopcore, &p, &numblocks, &remain);
    BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
    return;
  }
  BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();

  GC_BAMBOO_DEBUGPRINT(0xd102);
  GC_BAMBOO_DEBUGPRINT_REG(gctopcore);
  GC_BAMBOO_DEBUGPRINT_REG(p);
  GC_BAMBOO_DEBUGPRINT_REG(b);
  GC_BAMBOO_DEBUGPRINT_REG(remain);
  for(int i = 0; i < NUMCORES4GC; i++) {
    BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
    if((gccorestatus[i] != 0) && (gcrequiredmems[i] > 0)) {
      GC_BAMBOO_DEBUGPRINT(0xd103);
      compact2Heaptophelper_I(i, &p, &numblocks, &remain);
      if(gccorestatus[gctopcore] != 0) {
		GC_BAMBOO_DEBUGPRINT(0xd101);
		GC_BAMBOO_DEBUGPRINT_REG(gctopcore);
		BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
		// the top core is not free now
		return;
      }
    }  // if((gccorestatus[i] != 0) && (gcrequiredmems[i] > 0))
    BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
  }   // for(i = 0; i < NUMCORES4GC; i++)
  GC_BAMBOO_DEBUGPRINT(0xd106);
} // void compact2Heaptop()

inline void resolvePendingMoveRequest() {
  GC_BAMBOO_DEBUGPRINT(0xeb01);
  GC_BAMBOO_DEBUGPRINT(0xeeee);
  for(int k = 0; k < NUMCORES4GC; k++) {
    GC_BAMBOO_DEBUGPRINT(0xf000+k);
    GC_BAMBOO_DEBUGPRINT_REG(gccorestatus[k]);
    GC_BAMBOO_DEBUGPRINT_REG(gcloads[k]);
    GC_BAMBOO_DEBUGPRINT_REG(gcfilledblocks[k]);
    GC_BAMBOO_DEBUGPRINT_REG(gcstopblock[k]);
  }
  GC_BAMBOO_DEBUGPRINT(0xffff);
  int i;
  int j;
  bool nosparemem = true;
  bool haspending = false;
  bool hasrunning = false;
  bool noblock = false;
  unsigned int dstcore = 0;       // the core who need spare mem
  unsigned int sourcecore = 0;       // the core who has spare mem
  for(i = j = 0; (i < NUMCORES4GC) && (j < NUMCORES4GC); ) {
    if(nosparemem) {
      // check if there are cores with spare mem
      if(gccorestatus[i] == 0) {
		// finished working, check if it still have spare mem
		if(gcfilledblocks[i] < gcstopblock[i]) {
		  // still have spare mem
		  nosparemem = false;
		  sourcecore = i;
		}  // if(gcfilledblocks[i] < gcstopblock[i]) else ...
      }
      i++;
    }  // if(nosparemem)
    if(!haspending) {
      if(gccorestatus[j] != 0) {
		// not finished, check if it has pending move requests
		if((gcfilledblocks[j]==gcstopblock[j])&&(gcrequiredmems[j]>0)) {
		  dstcore = j;
		  haspending = true;
		} else {
		  hasrunning = true;
		}  // if((gcfilledblocks[i] == gcstopblock[i])...) else ...
      }  // if(gccorestatus[i] == 0) else ...
      j++;
    }  // if(!haspending)
    if(!nosparemem && haspending) {
      // find match
      unsigned int tomove = 0;
      unsigned int startaddr = 0;
      BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
      gcrequiredmems[dstcore] = assignSpareMem_I(sourcecore,
                                                 gcrequiredmems[dstcore],
                                                 &tomove,
                                                 &startaddr);
      BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
      GC_BAMBOO_DEBUGPRINT(0xeb02);
      GC_BAMBOO_DEBUGPRINT_REG(sourcecore);
      GC_BAMBOO_DEBUGPRINT_REG(dstcore);
      GC_BAMBOO_DEBUGPRINT_REG(startaddr);
      GC_BAMBOO_DEBUGPRINT_REG(tomove);
      if(STARTUPCORE == dstcore) {
		GC_BAMBOO_DEBUGPRINT(0xeb03);
		gcdstcore = sourcecore;
		gctomove = true;
		gcmovestartaddr = startaddr;
		gcblock2fill = tomove;
      } else {
		GC_BAMBOO_DEBUGPRINT(0xeb04);
		send_msg_4(dstcore, GCMOVESTART, sourcecore,
				   startaddr, tomove, false);
      }
      gcmovepending--;
      nosparemem = true;
      haspending = false;
      noblock = true;
    }
  }   // for(i = 0; i < NUMCORES4GC; i++)
  GC_BAMBOO_DEBUGPRINT(0xcccc);
  GC_BAMBOO_DEBUGPRINT_REG(hasrunning);
  GC_BAMBOO_DEBUGPRINT_REG(haspending);
  GC_BAMBOO_DEBUGPRINT_REG(noblock);

  if(!hasrunning && !noblock) {
    gcphase = SUBTLECOMPACTPHASE;
    compact2Heaptop();
  }

} // void resovePendingMoveRequest()

struct moveHelper {
  unsigned int numblocks;       // block num for heap
  unsigned int base;       // base virtual address of current heap block
  unsigned int ptr;       // virtual address of current heap top
  unsigned int offset;       // offset in current heap block
  unsigned int blockbase;   // virtual address of current small block to check
  unsigned int blockbound;     // bound virtual address of current small blcok
  unsigned int sblockindex;       // index of the small blocks
  unsigned int top;       // real size of current heap block to check
  unsigned int bound;       // bound size of current heap block to check
}; // struct moveHelper

// If out of boundary of valid shared memory, return false, else return true
inline bool nextSBlock(struct moveHelper * orig) {
  orig->blockbase = orig->blockbound;

  bool sbchanged = false;
  unsigned int origptr = orig->ptr;
  unsigned int blockbase = orig->blockbase;
  unsigned int blockbound = orig->blockbound;
  unsigned int bound = orig->bound;
  GC_BAMBOO_DEBUGPRINT(0xecc0);
  GC_BAMBOO_DEBUGPRINT_REG(blockbase);
  GC_BAMBOO_DEBUGPRINT_REG(blockbound);
  GC_BAMBOO_DEBUGPRINT_REG(bound);
  GC_BAMBOO_DEBUGPRINT_REG(origptr);
outernextSBlock:
  // check if across a big block
  // TODO now do not zero out the whole memory, maybe the last two conditions
  // are useless now
  if((blockbase>=bound)||(origptr>=bound)
	  ||((origptr!=NULL)&&(*((int*)origptr))==0)||((*((int*)blockbase))==0)) {
innernextSBlock:
    // end of current heap block, jump to next one
    orig->numblocks++;
    GC_BAMBOO_DEBUGPRINT(0xecc1);
    GC_BAMBOO_DEBUGPRINT_REG(orig->numblocks);
    BASEPTR(BAMBOO_NUM_OF_CORE, orig->numblocks, &(orig->base));
    GC_BAMBOO_DEBUGPRINT(orig->base);
    if(orig->base >= gcbaseva + BAMBOO_SHARED_MEM_SIZE) {
      // out of boundary
      orig->ptr = orig->base; // set current ptr to out of boundary too
      return false;
    }
    orig->blockbase = orig->base;
    orig->sblockindex = 
	  (unsigned int)(orig->blockbase-gcbaseva)/BAMBOO_SMEM_SIZE;
    sbchanged = true;
    unsigned int blocknum = 0;
    BLOCKINDEX(orig->base, &blocknum);
    if(bamboo_smemtbl[blocknum] == 0) {
#ifdef GC_TBL_DEBUG
	  if(blocknum >= gcnumblock) {
		BAMBOO_EXIT(0xb012);
	  }
#endif
      // goto next block
      goto innernextSBlock;
    }
	// check the bamboo_smemtbl to decide the real bound
	orig->bound = orig->base + bamboo_smemtbl[blocknum];
  } else if(0 == (orig->blockbase%BAMBOO_SMEM_SIZE)) {
    orig->sblockindex += 1;
    sbchanged = true;
  }  // if((orig->blockbase >= orig->bound) || (orig->ptr >= orig->bound)...

  // check if this sblock should be skipped or have special start point
  int sbstart = gcsbstarttbl[orig->sblockindex];
#ifdef GC_TBL_DEBUG
  if((orig->sblockindex) >= gcsbstarttbl_len) {
	BAMBOO_EXIT(0xb013);
  }
#endif
  if(sbstart == -1) {
    // goto next sblock
    GC_BAMBOO_DEBUGPRINT(0xecc2);
    orig->sblockindex += 1;
    orig->blockbase += BAMBOO_SMEM_SIZE;
    goto outernextSBlock;
  } else if((sbstart != 0) && (sbchanged)) {
    // the first time to access this SBlock
    GC_BAMBOO_DEBUGPRINT(0xecc3);
    // not start from the very beginning
    orig->blockbase = sbstart;
  }  // if(gcsbstarttbl[orig->sblockindex] == -1) else ...

  // setup information for this sblock
  orig->blockbound = orig->blockbase+(unsigned int)*((int*)(orig->blockbase));
  orig->offset = BAMBOO_CACHE_LINE_SIZE;
  orig->ptr = orig->blockbase + orig->offset;
  GC_BAMBOO_DEBUGPRINT(0xecc4);
  GC_BAMBOO_DEBUGPRINT_REG(orig->base);
  GC_BAMBOO_DEBUGPRINT_REG(orig->bound);
  GC_BAMBOO_DEBUGPRINT_REG(orig->ptr);
  GC_BAMBOO_DEBUGPRINT_REG(orig->blockbound);
  GC_BAMBOO_DEBUGPRINT_REG(orig->blockbase);
  GC_BAMBOO_DEBUGPRINT_REG(orig->offset);
  if(orig->ptr >= orig->bound) {
    // met a lobj, move to next block
    goto innernextSBlock;
  }

  return true;
} // bool nextSBlock(struct moveHelper * orig)

// return false if there are no available data to compact
inline bool initOrig_Dst(struct moveHelper * orig,
                         struct moveHelper * to) {
  // init the dst ptr
  to->numblocks = 0;
  to->top = to->offset = BAMBOO_CACHE_LINE_SIZE;
  to->bound = BAMBOO_SMEM_SIZE_L;
  BASEPTR(BAMBOO_NUM_OF_CORE, to->numblocks, &(to->base));

  GC_BAMBOO_DEBUGPRINT(0xef01);
  GC_BAMBOO_DEBUGPRINT_REG(to->base);
  unsigned int tobase = to->base;
  to->ptr = tobase + to->offset;
#ifdef GC_CACHE_ADAPT
  // initialize the gc_cache_revise_information
  gc_cache_revise_infomation.to_page_start_va = to->ptr;
  unsigned int toindex = (unsigned int)(tobase-gcbaseva)/(BAMBOO_PAGE_SIZE);
  gc_cache_revise_infomation.to_page_end_va = (BAMBOO_PAGE_SIZE)*
	(toindex+1);
  gc_cache_revise_infomation.to_page_index = toindex;
  gc_cache_revise_infomation.orig_page_start_va = -1;
#endif // GC_CACHE_ADAPT

  // init the orig ptr
  orig->numblocks = 0;
  orig->base = tobase;
  unsigned int blocknum = 0;
  BLOCKINDEX(orig->base, &blocknum);
  unsigned int origbase = orig->base;
  // check the bamboo_smemtbl to decide the real bound
  orig->bound = origbase + (unsigned int)bamboo_smemtbl[blocknum];
#ifdef GC_TBL_DEBUG
  if((orig->sblockindex) >= gcsbstarttbl_len) {
	BAMBOO_EXIT(0xb014);
  }
#endif
  orig->blockbase = origbase;
  orig->sblockindex = (unsigned int)(origbase - gcbaseva) / BAMBOO_SMEM_SIZE;
  GC_BAMBOO_DEBUGPRINT(0xef02);
  GC_BAMBOO_DEBUGPRINT_REG(origbase);
  GC_BAMBOO_DEBUGPRINT_REG(orig->sblockindex);
  GC_BAMBOO_DEBUGPRINT_REG(gcsbstarttbl);
  GC_BAMBOO_DEBUGPRINT_REG(gcsbstarttbl[orig->sblockindex]);

  int sbstart = gcsbstarttbl[orig->sblockindex];
#ifdef GC_TBL_DEBUG
  if((orig->sblockindex) >= gcsbstarttbl_len) {
	BAMBOO_EXIT(0xb015);
  }
#endif
  if(sbstart == -1) {
    GC_BAMBOO_DEBUGPRINT(0xef03);
    // goto next sblock
    orig->blockbound =
      gcbaseva+BAMBOO_SMEM_SIZE*(orig->sblockindex+1);
    return nextSBlock(orig);
  } else if(sbstart != 0) {
    GC_BAMBOO_DEBUGPRINT(0xef04);
    orig->blockbase = sbstart;
  }
  GC_BAMBOO_DEBUGPRINT(0xef05);
  orig->blockbound = orig->blockbase + *((int*)(orig->blockbase));
  orig->offset = BAMBOO_CACHE_LINE_SIZE;
  orig->ptr = orig->blockbase + orig->offset;
  GC_BAMBOO_DEBUGPRINT(0xef06);
  GC_BAMBOO_DEBUGPRINT_REG(orig->base);

  return true;
} // bool initOrig_Dst(struct moveHelper * orig, struct moveHelper * to)

inline void nextBlock(struct moveHelper * to) {
  to->top = to->bound + BAMBOO_CACHE_LINE_SIZE; // header!
  to->bound += BAMBOO_SMEM_SIZE;
  to->numblocks++;
  BASEPTR(BAMBOO_NUM_OF_CORE, to->numblocks, &(to->base));
  to->offset = BAMBOO_CACHE_LINE_SIZE;
  to->ptr = to->base + to->offset;
} // void nextBlock(struct moveHelper * to)

#ifdef GC_CACHE_ADAPT
inline void samplingDataConvert(unsigned int current_ptr) {
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
} // inline void samplingDataConvert(int)

inline void completePageConvert(struct moveHelper * orig,
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
} // inline void completePageConvert(...)
#endif // GC_CACHE_ADAPT

// endaddr does not contain spaces for headers
inline bool moveobj(struct moveHelper * orig,
                    struct moveHelper * to,
                    unsigned int stopblock) {
  if(stopblock == 0) {
    return true;
  }

  GC_BAMBOO_DEBUGPRINT(0xe201);
  GC_BAMBOO_DEBUGPRINT_REG(orig->ptr);
  GC_BAMBOO_DEBUGPRINT_REG(to->ptr);
#ifdef GC_TBL_DEBUG
  unsigned int bkptr = (unsigned int)(orig->ptr);

  if((unsigned int)(to->ptr) > (unsigned int)(orig->ptr)) {
	tprintf("Error to->ptr > orig->ptr: %x, %x \n", (int)(to->ptr), (int)(orig->ptr));
	BAMBOO_EXIT(0xb016);
  }
#endif

  int type = 0;
  unsigned int size = 0;
  unsigned int isize = 0;
innermoveobj:
  /*while((*((char*)(orig->ptr))) == (char)(-2)) {
	orig->ptr = (unsigned int)((void*)(orig->ptr) + 1);
  }*/
#ifdef GC_CACHE_ADAPT
  completePageConvert(orig, to, to->ptr, false);
#endif
  unsigned int origptr = (unsigned int)(orig->ptr);
  unsigned int origbound = (unsigned int)orig->bound;
  unsigned int origblockbound = (unsigned int)orig->blockbound;
  if((origptr >= origbound) || (origptr == origblockbound)) {
    if(!nextSBlock(orig)) {
      // finished, no more data
#ifdef GC_TBL_DEBUG
	  tprintf("AAAA %x \n", (int)(orig->ptr));
#endif
      return true;
    }
    goto innermoveobj;
  }
  GC_BAMBOO_DEBUGPRINT(0xe202);
  GC_BAMBOO_DEBUGPRINT_REG(origptr);
  GC_BAMBOO_DEBUGPRINT(((int *)(origptr))[0]);
  // check the obj's type, size and mark flag
  type = ((int *)(origptr))[0];
  size = 0;
  if(type == 0) {
	// end of this block, go to next one
    if(!nextSBlock(orig)) {
      // finished, no more data
#ifdef GC_TBL_DEBUG
	  tprintf("BBBB %x \n", (int)(orig->ptr));
#endif
      return true;
    }
    goto innermoveobj;
  } else if(type < NUMCLASSES) {
    // a normal object
    size = classsize[type];
  } else {
    // an array
    struct ArrayObject *ao=(struct ArrayObject *)(origptr);
    unsigned int elementsize=classsize[type];
    unsigned int length=ao->___length___;
    size=(unsigned int)sizeof(struct ArrayObject)
	  +(unsigned int)(length*elementsize);
  }
  GC_BAMBOO_DEBUGPRINT(0xe203);
  GC_BAMBOO_DEBUGPRINT_REG(origptr);
  GC_BAMBOO_DEBUGPRINT_REG(size);
  ALIGNSIZE(size, &isize);       // no matter is the obj marked or not
                                 // should be able to across
#ifdef GC_TBL_DEBUG
  int sindex = OBJMAPPINGINDEX((unsigned int)bkptr);
  int eindex = OBJMAPPINGINDEX((unsigned int)(origptr));
  for(int tmpi = sindex+1; tmpi < eindex; tmpi++) {
	if((gcmappingtbl[tmpi] != 0) && 
		(hostcore(gcbaseva+bamboo_baseobjsize*tmpi)==BAMBOO_NUM_OF_CORE) && 
		(hostcore(gcbaseva+bamboo_baseobjsize*(tmpi+1))==BAMBOO_NUM_OF_CORE)) {
	  tprintf("Error moveobj --: %x, %x, %x, %d, %x \n", (int)bkptr, 
		  (int)origptr, (int)(gcbaseva+bamboo_baseobjsize*tmpi), 
		  (int)gcmappingtbl[tmpi], (int)(*((char*)(bkptr))));
	  BAMBOO_EXIT(0xb017);
	}
  }
#endif
  if(((int *)(origptr))[BAMBOOMARKBIT] == MARKED) {
	unsigned int totop = (unsigned int)to->top;
	unsigned int tobound = (unsigned int)to->bound;
    GC_BAMBOO_DEBUGPRINT(0xe204);
#ifdef GC_PROFILE
#ifdef MGC_SPEC
	if((STARTUPCORE != BAMBOO_NUM_OF_CORE) || gc_profile_flag) {
#endif
	gc_num_liveobj++;
#ifdef MGC_SPEC
	}
#endif
#endif
    // marked obj, copy it to current heap top
    // check to see if remaining space is enough
    if((unsigned int)(totop + isize) > tobound) {
      // fill 0 indicating the end of this block
      BAMBOO_MEMSET_WH(to->ptr,  '\0', tobound - totop);
      // fill the header of this block and then go to next block
      to->offset += tobound - totop;
      BAMBOO_MEMSET_WH(to->base, '\0', BAMBOO_CACHE_LINE_SIZE);
      (*((int*)(to->base))) = to->offset;
#ifdef GC_CACHE_ADAPT
	  unsigned int tmp_ptr = to->ptr;
#endif // GC_CACHE_ADAPT
      nextBlock(to);
#ifdef GC_CACHE_ADAPT
	  completePageConvert(orig, to, tmp_ptr, true);
#endif // GC_CACHE_ADAPT
      if(stopblock == to->numblocks) {
		// already fulfilled the block
#ifdef GC_TBL_DEBUG
		tprintf("CCCC %x \n", (int)(orig->ptr));
#endif
		return true;
      }   // if(stopblock == to->numblocks)
    }   // if(to->top + isize > to->bound)
    // set the mark field to 2, indicating that this obj has been moved
    // and need to be flushed
    ((int *)(origptr))[BAMBOOMARKBIT] = COMPACTED;
	unsigned int toptr = (unsigned int)to->ptr;
#ifdef GC_TBL_DEBUG
	{
	  // scan all pointers in ptr
	  unsigned int * tt_pointer;
	  tt_pointer=pointerarray[type];
	  if (tt_pointer==0) {
		/* Array of primitives */
		/* Do nothing */
	  } else if (((unsigned int)tt_pointer)==1) {
		/* Array of pointers */
		struct ArrayObject *ao=(struct ArrayObject *)(origptr);
		int tt_length=ao->___length___;
		int tt_j;
		for(tt_j=0; tt_j<tt_length; tt_j++) {
		  void *objptr =
			((void **)(((char *)&ao->___length___)+sizeof(int)))[tt_j];
		  if((objptr != 0) && 
			  ((gcmappingtbl[OBJMAPPINGINDEX((unsigned int)objptr)] == 0) || 
			   (gcmappingtbl[OBJMAPPINGINDEX((unsigned int)objptr)] == 1))) {
			tprintf("Error moveobj, missing live obj ++: %x, %x, %d, %d, %d, %d, %d, %d, %d, %d \n", 
				(int)origptr, (int)objptr, __LINE__, tt_j, 
				((int *)(origptr))[0], ((int *)(objptr))[0], 
				((int *)(objptr))[BAMBOOMARKBIT], 
				gcmappingtbl[OBJMAPPINGINDEX((unsigned int)objptr)], 
				hostcore(objptr), BAMBOO_NUM_OF_CORE);
			BAMBOO_EXIT(0xb018);
		  }
		}
	  } else {
		unsigned int tt_size=tt_pointer[0];
		int tt_i;
		for(tt_i=1; tt_i<=tt_size; tt_i++) {
		  unsigned int tt_offset=tt_pointer[tt_i];
		  void * objptr=*((void **)(((char *)origptr)+tt_offset));
		  if((objptr!= 0) && 
			  ((gcmappingtbl[OBJMAPPINGINDEX((unsigned int)objptr)] == 0) || 
			   (gcmappingtbl[OBJMAPPINGINDEX((unsigned int)objptr)] == 1))) {
			tprintf("Error moveobj, missing live obj ++: %x, %x, %d, %d, %d, %d, %d, %d, %d, %d \n", 
				(int)origptr, (int)objptr, __LINE__, tt_i,
				((int *)(origptr))[0], ((int *)(objptr))[0],
				((int *)(objptr))[BAMBOOMARKBIT], 
				gcmappingtbl[OBJMAPPINGINDEX((unsigned int)objptr)], 
				hostcore(objptr), BAMBOO_NUM_OF_CORE);
			BAMBOO_EXIT(0xb019);
		  }
		}
	  }     // if (pointer==0) else if ... else ...
	  {
		  tt_pointer=pointerarray[OBJECTTYPE];
		  //handle object class
		  unsigned int tt_size=tt_pointer[0];
		  int tt_i;
		  for(tt_i=1; tt_i<=tt_size; tt_i++) {
			unsigned int tt_offset=tt_pointer[i];
			void * objptr=*((void **)(((char *)origptr)+tt_offset));
			if((objptr!= 0) && 
			  ((gcmappingtbl[OBJMAPPINGINDEX((unsigned int)objptr)] == 0) || 
			   (gcmappingtbl[OBJMAPPINGINDEX((unsigned int)objptr)] == 1))) {
			  tprintf("Error moveobj, missing live obj ++: %x, %x, %d, %d, %d, %d, %d, %d, %d, %d \n", 
				  (int)origptr, (int)objptr, __LINE__, tt_i,
				  ((int *)(origptr))[0], ((int *)(objptr))[0],
				  ((int *)(objptr))[BAMBOOMARKBIT], 
				  gcmappingtbl[OBJMAPPINGINDEX((unsigned int)objptr)],
				  hostcore(objptr), BAMBOO_NUM_OF_CORE);
			  BAMBOO_EXIT(0xb01a);
			}
		  }
	  }
	}
	if((unsigned int)(toptr) > (unsigned int)(origptr)) {
	  tprintf("Error to->ptr > orig->ptr: %x, %x \n", (int)(toptr), 
		  (int)(origptr));
	  BAMBOO_EXIT(0xb01b);
	}
#endif
    if(toptr != origptr) {
      if((unsigned int)(origptr) < (unsigned int)(toptr+size)) {
		memmove(toptr, origptr, size);
      } else {
		memcpy(toptr, origptr, size);
      }
      // fill the remaining space with -2
      BAMBOO_MEMSET_WH((unsigned int)(toptr+size), -2, isize-size);
    }
#ifdef GC_TBL_DEBUG
	if((gcmappingtbl[OBJMAPPINGINDEX((unsigned int)origptr)] != 2)) {
	  tprintf("Error moveobj: %x, %x, %d \n", (int)origptr, 
		  ((int *)(origptr))[BAMBOOMARKBIT], 
		  gcmappingtbl[OBJMAPPINGINDEX((unsigned int)origptr)]);
	  BAMBOO_EXIT(0xb01c);
	}
#endif
    // store mapping info
	gcmappingtbl[OBJMAPPINGINDEX((unsigned int)origptr)]=(unsigned int)toptr;
#ifdef GC_TBL_DEBUG
	if(gcmappingtbl[OBJMAPPINGINDEX((unsigned int)origptr)] == 
		gcmappingtbl[OBJMAPPINGINDEX((unsigned int)origptr)-1]) {
	  tprintf("Error moveobj ++ : %x, %x, %d \n", (int)origptr, (int)toptr, 
		  OBJMAPPINGINDEX((unsigned int)origptr));
	  BAMBOO_EXIT(0xb01d);
	}
	// scan all pointers in ptr
	unsigned int * tt_pointer;
	tt_pointer=pointerarray[type];
	if (tt_pointer==0) {
	  /* Array of primitives */
	  /* Do nothing */
	} else if (((unsigned int)tt_pointer)==1) {
	  /* Array of pointers */
	  struct ArrayObject *ao=(struct ArrayObject *)(toptr);
	  int tt_length=ao->___length___;
	  int tt_j;
	  for(tt_j=0; tt_j<tt_length; tt_j++) {
		void *objptr =
		  ((void **)(((char *)&ao->___length___)+sizeof(int)))[tt_j];
		if((objptr != 0) && 
			(gcmappingtbl[OBJMAPPINGINDEX((unsigned int)objptr)] == 0)) {
		  tprintf("Error moveobj, missing live obj ++: %x, %x, %d, %d, %d, %d, %d, %d, %d, %d \n", 
			  (int)origptr, (int)objptr, __LINE__, tt_i, 
			  ((int *)(origptr))[0], ((int *)(objptr))[0], 
			  ((int *)(objptr))[BAMBOOMARKBIT], 
			  gcmappingtbl[OBJMAPPINGINDEX((unsigned int)objptr)], 
			  hostcore(objptr), BAMBOO_NUM_OF_CORE);
		  BAMBOO_EXIT(0xb01e);
		}
	  }
	} else {
	  unsigned int tt_size=tt_pointer[0];
	  int tt_i;
	  for(tt_i=1; tt_i<=tt_size; tt_i++) {
		unsigned int tt_offset=tt_pointer[tt_i];
		void * objptr=*((void **)(((char *)toptr)+tt_offset));
		if((objptr != 0) && 
			(gcmappingtbl[OBJMAPPINGINDEX((unsigned int)objptr)] == 0)) {
		  tprintf("Error moveobj, missing live obj ++: %x, %x, %d, %d, %d, %d, %d, %d, %d, %d \n", 
			  (int)origptr, (int)objptr, __LINE__, tt_i, 
			  ((int *)(origptr))[0], ((int *)(objptr))[0], 
			  ((int *)(objptr))[BAMBOOMARKBIT], 
			  gcmappingtbl[OBJMAPPINGINDEX((unsigned int)objptr)], 
			  hostcore(objptr), BAMBOO_NUM_OF_CORE);
		  BAMBOO_EXIT(0xb01f);
		}
	  }
	}     // if (pointer==0) else if ... else ...
	{
		  tt_pointer=pointerarray[OBJECTTYPE];
		  //handle object class
		  unsigned int tt_size=tt_pointer[0];
		  int tt_i;
		  for(tt_i=1; tt_i<=tt_size; tt_i++) {
			unsigned int tt_offset=tt_pointer[i];
			void * objptr=*((void **)(((char *)origptr)+tt_offset));
			if((objptr!= 0) && 
			  ((gcmappingtbl[OBJMAPPINGINDEX((unsigned int)objptr)] == 0) || 
			   (gcmappingtbl[OBJMAPPINGINDEX((unsigned int)objptr)] == 1))) {
			  tprintf("Error moveobj, missing live obj ++: %x, %x, %d, %d, %d, %d, %d, %d, %d, %d \n", 
				  (int)origptr, (int)objptr, __LINE__, tt_i,
				  ((int *)(origptr))[0], ((int *)(objptr))[0],
				  ((int *)(objptr))[BAMBOOMARKBIT], 
				  gcmappingtbl[OBJMAPPINGINDEX((unsigned int)objptr)],
				  hostcore(objptr), BAMBOO_NUM_OF_CORE);
			  BAMBOO_EXIT(0xb020);
			}
		  }
	  }
	if(!ISSHAREDOBJ(toptr)) {
	  tprintf("Error: %x, %x \n", (int)origptr, (int)toptr);
	  BAMBOO_EXIT(0xb021);
	}
#endif
	GC_BAMBOO_DEBUGPRINT(0xcdce);
    GC_BAMBOO_DEBUGPRINT_REG(origptr);
    GC_BAMBOO_DEBUGPRINT_REG(toptr);
    GC_BAMBOO_DEBUGPRINT_REG(isize);
    gccurr_heaptop -= isize;
    to->ptr += isize;
    to->offset += isize;
    to->top += isize;
#ifdef GC_CACHE_ADAPT
	unsigned int tmp_ptr = to->ptr;
#endif // GC_CACHE_ADAPT
    if(to->top == to->bound) {
      // fill the header of this block and then go to next block
      BAMBOO_MEMSET_WH(to->base, '\0', BAMBOO_CACHE_LINE_SIZE);
      (*((int*)(to->base))) = to->offset;
      nextBlock(to);
    }
#ifdef GC_CACHE_ADAPT
	completePageConvert(orig, to, tmp_ptr, true);
#endif // GC_CACHE_ADAPT
  } // if(mark == 1)
#ifdef GC_TBL_DEBUG
  else {
	// skip the whole obj
	int sindex = OBJMAPPINGINDEX((unsigned int)origptr);
	int eindex = OBJMAPPINGINDEX((unsigned int)(origptr+size));
	for(int tmpi = sindex; tmpi < eindex; tmpi++) {
	  if((gcmappingtbl[tmpi] != 0) && 
		  (hostcore(gcbaseva+bamboo_baseobjsize*tmpi)==BAMBOO_NUM_OF_CORE) && 
		  (hostcore(gcbaseva+bamboo_baseobjsize*(tmpi+1))==BAMBOO_NUM_OF_CORE))
	  {
		tprintf("Error moveobj **: %x, %x, %x, %d, (%d, %d, %x) \n", 
			(int)origptr, (int)(origptr+isize), 
			(int)(gcbaseva+bamboo_baseobjsize*tmpi), gcmappingtbl[tmpi], type,
			isize, ((int *)(origptr))[BAMBOOMARKBIT]);
		BAMBOO_EXIT(0xb022);
	  }
	}
  }
#endif
  GC_BAMBOO_DEBUGPRINT(0xe205);
  
  // move to next obj
  orig->ptr += isize; // size;

#ifdef GC_TBL_DEBUG
  if(!ISSHAREDOBJ(orig->ptr) || !ISSHAREDOBJ(to->ptr)) {
	tprintf("Error moveobj out of boundary: %x, %x, %d, %d \n", 
		(int)(orig->ptr), (int)(to->ptr), size, isize);
	BAMBOO_EXIT(0x2022);
  }
#endif

  GC_BAMBOO_DEBUGPRINT_REG(isize);
  GC_BAMBOO_DEBUGPRINT_REG(size);
  GC_BAMBOO_DEBUGPRINT_REG(orig->ptr);
  GC_BAMBOO_DEBUGPRINT_REG(orig->bound);
  if(((unsigned int)(orig->ptr) > (unsigned int)(orig->bound))
	  || ((unsigned int)(orig->ptr) == (unsigned int)(orig->blockbound))) {
    GC_BAMBOO_DEBUGPRINT(0xe206);
    if(!nextSBlock(orig)) {
      // finished, no more data
#ifdef GC_TBL_DEBUG
	  tprintf("DDDD %x \n", (int)(orig->ptr));
#endif
      return true;
    }
  }
  GC_BAMBOO_DEBUGPRINT(0xe207);
  GC_BAMBOO_DEBUGPRINT_REG(orig->ptr);
  return false;
} //bool moveobj(struct moveHelper* orig,struct moveHelper* to,int* endaddr)

// should be invoked with interrupt closed
inline int assignSpareMem_I(unsigned int sourcecore,
                            unsigned int * requiredmem,
                            unsigned int * tomove,
                            unsigned int * startaddr) {
  unsigned int b = 0;
  BLOCKINDEX(gcloads[sourcecore], &b);
  unsigned int boundptr = (b<NUMCORES4GC) ? ((b+1)*BAMBOO_SMEM_SIZE_L)
		 : (BAMBOO_LARGE_SMEM_BOUND+(b-NUMCORES4GC+1)*BAMBOO_SMEM_SIZE);
  unsigned int remain = boundptr - gcloads[sourcecore];
  unsigned int memneed = requiredmem + BAMBOO_CACHE_LINE_SIZE;
  *startaddr = gcloads[sourcecore];
  *tomove = gcfilledblocks[sourcecore] + 1;
  if(memneed < remain) {
    gcloads[sourcecore] += memneed;
    return 0;
  } else {
    // next available block
    gcfilledblocks[sourcecore] += 1;
    unsigned int newbase = 0;
    BASEPTR(sourcecore, gcfilledblocks[sourcecore], &newbase);
    gcloads[sourcecore] = newbase;
    return requiredmem-remain;
  }
} // int assignSpareMem_I(int ,int * , int * , int * )

// should be invoked with interrupt closed
inline bool gcfindSpareMem_I(unsigned int * startaddr,
                             unsigned int * tomove,
                             unsigned int * dstcore,
                             unsigned int requiredmem,
                             unsigned int requiredcore) {
  for(int k = 0; k < NUMCORES4GC; k++) {
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
                          unsigned int * heaptopptr,
                          bool * localcompact) {
  // scan over all objs in this block, compact the marked objs
  // loop stop when finishing either scanning all active objs or
  // fulfilled the gcstopblock
  GC_BAMBOO_DEBUGPRINT(0xe101);
  GC_BAMBOO_DEBUGPRINT_REG(gcblock2fill);
  GC_BAMBOO_DEBUGPRINT_REG(gcmarkedptrbound);
innercompact:
  while((unsigned int)(orig->ptr) < (unsigned int)gcmarkedptrbound) {
    bool stop = moveobj(orig, to, gcblock2fill);
    if(stop) {
      break;
    }
  }
#ifdef GC_TBL_DEBUG
  tprintf("finish mark %x \n", (int)gcmarkedptrbound);
#endif
#ifdef GC_CACHE_ADAPT
  // end of an to page, wrap up its information
  samplingDataConvert(to->ptr);
#endif // GC_CACHE_ADAPT
  // if no objs have been compact, do nothing,
  // otherwise, fill the header of this block
  if(to->offset > (unsigned int)BAMBOO_CACHE_LINE_SIZE) {
    BAMBOO_MEMSET_WH(to->base, '\0', BAMBOO_CACHE_LINE_SIZE);
    (*((int*)(to->base))) = to->offset;
  } else {
    to->offset = 0;
    to->ptr = to->base;
    to->top -= BAMBOO_CACHE_LINE_SIZE;
  }  // if(to->offset > BAMBOO_CACHE_LINE_SIZE) else ...
  if(*localcompact) {
    *heaptopptr = to->ptr;
    *filledblocks = to->numblocks;
  }
  GC_BAMBOO_DEBUGPRINT(0xe102);
  GC_BAMBOO_DEBUGPRINT_REG(orig->ptr);
  GC_BAMBOO_DEBUGPRINT_REG(gcmarkedptrbound);
  GC_BAMBOO_DEBUGPRINT_REG(*heaptopptr);
  GC_BAMBOO_DEBUGPRINT_REG(*filledblocks);
  GC_BAMBOO_DEBUGPRINT_REG(gccurr_heaptop);

  // send msgs to core coordinator indicating that the compact is finishing
  // send compact finish message to core coordinator
  if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
    gcfilledblocks[BAMBOO_NUM_OF_CORE] = *filledblocks;
    gcloads[BAMBOO_NUM_OF_CORE] = *heaptopptr;
    if((unsigned int)(orig->ptr) < (unsigned int)gcmarkedptrbound) {
      GC_BAMBOO_DEBUGPRINT(0xe103);
      // ask for more mem
      gctomove = false;
      BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
      if(gcfindSpareMem_I(&gcmovestartaddr, &gcblock2fill, &gcdstcore,
                          gccurr_heaptop, BAMBOO_NUM_OF_CORE)) {
		GC_BAMBOO_DEBUGPRINT(0xe104);
		gctomove = true;
      } else {
		BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
		GC_BAMBOO_DEBUGPRINT(0xe105);
		return false;
      }
      BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
    } else {
      GC_BAMBOO_DEBUGPRINT(0xe106);
      gccorestatus[BAMBOO_NUM_OF_CORE] = 0;
      gctomove = false;
      return true;
    }
  } else {
    if((unsigned int)(orig->ptr) < (unsigned int)gcmarkedptrbound) {
      GC_BAMBOO_DEBUGPRINT(0xe107);
      // ask for more mem
      gctomove = false;
      send_msg_5(STARTUPCORE, GCFINISHCOMPACT, BAMBOO_NUM_OF_CORE,
                 *filledblocks, *heaptopptr, gccurr_heaptop, false);
    } else {
      GC_BAMBOO_DEBUGPRINT(0xe108);
      GC_BAMBOO_DEBUGPRINT_REG(*heaptopptr);
      // finish compacting
      send_msg_5(STARTUPCORE, GCFINISHCOMPACT, BAMBOO_NUM_OF_CORE,
                 *filledblocks, *heaptopptr, 0, false);
    }
  }       // if(STARTUPCORE == BAMBOO_NUM_OF_CORE)

  if(orig->ptr < gcmarkedptrbound) {
    GC_BAMBOO_DEBUGPRINT(0xe109);
    // still have unpacked obj
    while(true) {
      if(gctomove) {
		break;
      }
    }
    ;
	gctomove = false;
    GC_BAMBOO_DEBUGPRINT(0xe10a);

    to->ptr = gcmovestartaddr;
    to->numblocks = gcblock2fill - 1;
    to->bound = (to->numblocks==0) ?
                BAMBOO_SMEM_SIZE_L :
                BAMBOO_SMEM_SIZE_L+BAMBOO_SMEM_SIZE*to->numblocks;
    BASEPTR(gcdstcore, to->numblocks, &(to->base));
    to->offset = to->ptr - to->base;
    to->top = (to->numblocks==0) ?
              (to->offset) : (to->bound-BAMBOO_SMEM_SIZE+to->offset);
    to->base = to->ptr;
    to->offset = BAMBOO_CACHE_LINE_SIZE;
    to->ptr += to->offset;   // for header
    to->top += to->offset;
    if(gcdstcore == BAMBOO_NUM_OF_CORE) {
      *localcompact = true;
    } else {
      *localcompact = false;
    }
#ifdef GC_CACHE_ADAPT
	// initialize the gc_cache_revise_information
	gc_cache_revise_infomation.to_page_start_va = (unsigned int)to->ptr;
	gc_cache_revise_infomation.to_page_end_va = gcbaseva+(BAMBOO_PAGE_SIZE)
	  *(((unsigned int)(to->base)-gcbaseva)/(BAMBOO_PAGE_SIZE)+1);
	gc_cache_revise_infomation.to_page_index = 
	  ((unsigned int)(to->base)-gcbaseva)/(BAMBOO_PAGE_SIZE);
	gc_cache_revise_infomation.orig_page_start_va = orig->ptr;
	gc_cache_revise_infomation.orig_page_end_va = gcbaseva+(BAMBOO_PAGE_SIZE)
	  *(((unsigned int)(orig->ptr)-gcbaseva)/(BAMBOO_PAGE_SIZE)+1);
	gc_cache_revise_infomation.orig_page_index = 
	  ((unsigned int)(orig->blockbase)-gcbaseva)/(BAMBOO_PAGE_SIZE);
#endif // GC_CACHE_ADAPT
    goto innercompact;
  }
  GC_BAMBOO_DEBUGPRINT(0xe10b);
  return true;
} // void compacthelper()

inline void compact() {
  if(COMPACTPHASE != gcphase) {
    BAMBOO_EXIT(0xb023);
  }

  // initialize pointers for comapcting
  struct moveHelper * orig =
    (struct moveHelper *)RUNMALLOC(sizeof(struct moveHelper));
  struct moveHelper * to =
    (struct moveHelper *)RUNMALLOC(sizeof(struct moveHelper));
  if(!initOrig_Dst(orig, to)) {
    // no available data to compact
    // send compact finish msg to STARTUP core
    GC_BAMBOO_DEBUGPRINT(0xe001);
    GC_BAMBOO_DEBUGPRINT_REG(to->base);
    send_msg_5(STARTUPCORE, GCFINISHCOMPACT, BAMBOO_NUM_OF_CORE,
               0, to->base, 0, false);
    RUNFREE(orig);
    RUNFREE(to);
    return;
  }
#ifdef GC_CACHE_ADAPT
  gc_cache_revise_infomation.orig_page_start_va = (unsigned int)orig->ptr;
  gc_cache_revise_infomation.orig_page_end_va = gcbaseva+(BAMBOO_PAGE_SIZE)
	*(((unsigned int)(orig->ptr)-gcbaseva)/(BAMBOO_PAGE_SIZE)+1);
  gc_cache_revise_infomation.orig_page_index = 
	((unsigned int)(orig->blockbase)-gcbaseva)/(BAMBOO_PAGE_SIZE);
#endif // GC_CACHE_ADAPT

  unsigned int filledblocks = 0;
  unsigned int heaptopptr = 0;
  bool localcompact = true;
  compacthelper(orig, to, &filledblocks, &heaptopptr, &localcompact);
  RUNFREE(orig);
  RUNFREE(to);
} // compact()

// if return NULL, means
//   1. objptr is NULL
//   2. objptr is not a shared obj
// in these cases, remain the original value is OK
#ifdef GC_TBL_DEBUG
inline void * flushObj(void * objptr, int linenum, void * ptr, int tt) {
#else
inline void * flushObj(void * objptr) {
#endif
  GC_BAMBOO_DEBUGPRINT(0xe401);
  if(objptr == NULL) {
    return NULL;
  }
  void * dstptr = NULL;
  if(ISSHAREDOBJ(objptr)) {
    GC_BAMBOO_DEBUGPRINT(0xe402);
    GC_BAMBOO_DEBUGPRINT_REG(objptr);
    // a shared obj ptr, change to new address
	dstptr = gcmappingtbl[OBJMAPPINGINDEX((unsigned int)objptr)];
    GC_BAMBOO_DEBUGPRINT_REG(dstptr);
#ifdef GC_TBL_DEBUG
	if(ISSHAREDOBJ(dstptr) && ((unsigned int)(((int*)dstptr)[0]) >= (unsigned int)NUMTYPES)) {
	  tprintf("Error flushObj  ** : %x, %x, %d, %d, %d, %d, %x, %x, %x, %d, %x, %d %d \n", 
		  (int)objptr, (int)dstptr, ((int*)dstptr)[0], hostcore(objptr), 
		  hostcore(objptr)==BAMBOO_NUM_OF_CORE, 
		  OBJMAPPINGINDEX((unsigned int)objptr), (int)gcmappingtbl, 
		  &(gcmappingtbl[OBJMAPPINGINDEX((unsigned int)objptr)]), 
		  (int)gcbaseva, linenum, (int)ptr, ((int*)ptr)[0], tt);
	  BAMBOO_EXIT(0xb024);
	}
#endif

    if(!ISSHAREDOBJ(dstptr)) {
#ifdef GC_TBL_DEBUG
	  tprintf("Error flushObj  ++ : %x, %x, %d, %d, %d, %x, %x, %x, %d, %x, %d %d \n", 
		  (int)objptr, (int)dstptr, hostcore(objptr), 
		  hostcore(objptr)==BAMBOO_NUM_OF_CORE, 
		  OBJMAPPINGINDEX((unsigned int)objptr), (int)gcmappingtbl, 
		  &(gcmappingtbl[OBJMAPPINGINDEX((unsigned int)objptr)]), 
		  (int)gcbaseva, linenum, (int)ptr, ((int*)ptr)[0], tt);
	  tprintf("gcmappingtbl: \n");
	  int tmp = OBJMAPPINGINDEX((unsigned int)objptr) - 50;
	  for(int jj = 0; jj < 100; jj+=10) {
		tprintf("%8x, %8x, %8x, %8x, %8x, %8x, %8x, %8x, %8x, %8x, %d \n", 
			(int)gcmappingtbl[tmp++], (int)gcmappingtbl[tmp++], 
			(int)gcmappingtbl[tmp++], (int)gcmappingtbl[tmp++], 
			(int)gcmappingtbl[tmp++], (int)gcmappingtbl[tmp++], 
			(int)gcmappingtbl[tmp++], (int)gcmappingtbl[tmp++], 
			(int)gcmappingtbl[tmp++], (int)gcmappingtbl[tmp++], tmp);
	  }
	  BAMBOO_EXIT(0xb025);
#else
      // no mapping info
      GC_BAMBOO_DEBUGPRINT(0xe403);
      GC_BAMBOO_DEBUGPRINT_REG(objptr);
      GC_BAMBOO_DEBUGPRINT_REG(hostcore(objptr));
	  // error! the obj is right on this core, but cannot find it
	  GC_BAMBOO_DEBUGPRINT_REG(objptr);
	  tprintf("Error flushObj  ++ : %x, %x, %d, %d, %x, %x, %x, %x\n", 
		  (int)objptr, (int)dstptr, hostcore(objptr), 
		  hostcore(objptr)==BAMBOO_NUM_OF_CORE, 
		  OBJMAPPINGINDEX((unsigned int)objptr), (int)gcmappingtbl, 
		  &(gcmappingtbl[OBJMAPPINGINDEX((unsigned int)objptr)]), 
		  (int)gcbaseva);
	  BAMBOO_EXIT(0xb026);
#endif
    }  // if(NULL == dstptr)
  }   // if(ISSHAREDOBJ(objptr))
#ifdef GC_TBL_DEBUG
  else {
	tprintf("Error flushObj: %x \n", (int)objptr);
	BAMBOO_EXIT(0xb027);
  }
#endif
  // if not a shared obj, return NULL to indicate no need to flush
  GC_BAMBOO_DEBUGPRINT(0xe404);
  return dstptr;
} // void flushObj(void * objptr)

inline void flushRuntimeObj(struct garbagelist * stackptr) {
  int i,j;
  // flush current stack
  while(stackptr!=NULL) {
    for(i=0; i<stackptr->size; i++) {
      if(stackptr->array[i] != NULL) {
#ifdef GC_TBL_DEBUG
		void * dst = flushObj(stackptr->array[i], 
			__LINE__, stackptr->array[i], i);
#else
		void * dst = flushObj(stackptr->array[i]);
#endif
		if(dst != NULL) {
		  stackptr->array[i] = dst;
		}
      }
    }
    stackptr=stackptr->next;
  }

  // flush static pointers global_defs_p
  if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
	struct garbagelist * staticptr=(struct garbagelist *)global_defs_p;
	for(i=0; i<staticptr->size; i++) {
	  if(staticptr->array[i] != NULL) {
#ifdef GC_TBL_DEBUG
		void * dst = flushObj(staticptr->array[i], 
			__LINE__, staticptr->array[i], i);
#else
		void * dst = flushObj(staticptr->array[i]);
#endif
		if(dst != NULL) {
		  staticptr->array[i] = dst;
		}
	  }
	}
  }

#ifdef TASK
  // flush objectsets
  if(BAMBOO_NUM_OF_CORE < NUMCORESACTIVE) {
    for(i=0; i<NUMCLASSES; i++) {
      struct parameterwrapper ** queues =
        objectqueues[BAMBOO_NUM_OF_CORE][i];
      int length = numqueues[BAMBOO_NUM_OF_CORE][i];
      for(j = 0; j < length; ++j) {
		struct parameterwrapper * parameter = queues[j];
		struct ObjectHash * set=parameter->objectset;
		struct ObjectNode * ptr=set->listhead;
		while(ptr!=NULL) {
#ifdef GC_TBL_DEBUG
		  void * dst = flushObj((void *)ptr->key, 
			  __LINE__, (void *)ptr->key, 0);
#else
		  void * dst = flushObj((void *)ptr->key);
#endif
		  if(dst != NULL) {
			ptr->key = dst;
		  }
		  ptr=ptr->lnext;
		}
		ObjectHashrehash(set);
      }
    }
  }

  // flush current task descriptor
  if(currtpd != NULL) {
    for(i=0; i<currtpd->numParameters; i++) {
#ifdef GC_TBL_DEBUG
	  void * dst = flushObj(currtpd->parameterArray[i], 
		  __LINE__, currtpd->parameterArray[i], i);
#else
      void * dst = flushObj(currtpd->parameterArray[i]);
#endif
      if(dst != NULL) {
		currtpd->parameterArray[i] = dst;
      }
    }
  }

  // flush active tasks
  if(activetasks != NULL) {
    struct genpointerlist * ptr=activetasks->list;
    while(ptr!=NULL) {
      struct taskparamdescriptor *tpd=ptr->src;
      int i;
      for(i=0; i<tpd->numParameters; i++) {
#ifdef GC_TBL_DEBUG
		void * dst = flushObj(tpd->parameterArray[i], 
			__LINE__, tpd->parameterArray[i], i);
#else
		void * dst = flushObj(tpd->parameterArray[i]);
#endif
		if(dst != NULL) {
		  tpd->parameterArray[i] = dst;
		}
      }
      ptr=ptr->inext;
    }
    genrehash(activetasks);
  }

  // flush cached transferred obj
  struct QueueItem * tmpobjptr =  getHead(&objqueue);
  while(tmpobjptr != NULL) {
    struct transObjInfo * objInfo =
      (struct transObjInfo *)(tmpobjptr->objectptr);
#ifdef GC_TBL_DEBUG
	void * dst = flushObj(objInfo->objptr, __LINE__, 
		objInfo->objptr, 0);
#else
    void * dst = flushObj(objInfo->objptr);
#endif
    if(dst != NULL) {
      objInfo->objptr = dst;
    }
    tmpobjptr = getNextQueueItem(tmpobjptr);
  }

  // flush cached objs to be transferred
  struct QueueItem * item = getHead(totransobjqueue);
  while(item != NULL) {
    struct transObjInfo * totransobj =
      (struct transObjInfo *)(item->objectptr);
#ifdef GC_TBL_DEBUG
	void * dst = flushObj(totransobj->objptr, __LINE__, 
		totransobj->objptr, 0);
#else
    void * dst = flushObj(totransobj->objptr);
#endif
    if(dst != NULL) {
      totransobj->objptr = dst;
    }
    item = getNextQueueItem(item);
  }  // while(item != NULL)

  // enqueue lock related info
  for(i = 0; i < runtime_locklen; ++i) {
#ifdef GC_TBL_DEBUG
	void * dst = flushObj(runtime_locks[i].redirectlock, 
		__LINE__, runtime_locks[i].redirectlock, i);
#else
    void * dst = flushObj(runtime_locks[i].redirectlock);
#endif
    if(dst != NULL) {
      runtime_locks[i].redirectlock = (int)dst;
    }
    if(runtime_locks[i].value != NULL) {
#ifdef GC_TBL_DEBUG
	  void * dst=flushObj(runtime_locks[i].value, 
		  __LINE__, runtime_locks[i].value, i);
#else
      void * dst=flushObj(runtime_locks[i].value);
#endif
      if(dst != NULL) {
		runtime_locks[i].value = (int)dst;
      }
    }
  }
#endif

#ifdef MGC
  // flush the bamboo_threadlocks
  for(i = 0; i < bamboo_threadlocks.index; i++) {
#ifdef GC_TBL_DEBUG
	void * dst = flushObj((void *)(bamboo_threadlocks.locks[i].object),
			__LINE__, (void *)(bamboo_threadlocks.locks[i].object), i);
#else
	void * dst = flushObj((void *)(bamboo_threadlocks.locks[i].object));
#endif
	if(dst != NULL) {
	  bamboo_threadlocks.locks[i].object = (struct ___Object___ *)dst;
	}
  }

  // flush the bamboo_current_thread
  if(bamboo_current_thread != 0) {
#ifdef GC_TBL_DEBUG
	bamboo_current_thread = 
	  (unsigned int)(flushObj((void *)bamboo_current_thread,
			__LINE__, (void *)bamboo_current_thread, 0));
#else
	bamboo_current_thread = 
	  (unsigned int)(flushObj((void *)bamboo_current_thread));
#endif
  }

  // flush global thread queue
  if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
	unsigned int thread_counter = *((unsigned int*)(bamboo_thread_queue+1));
	if(thread_counter > 0) {
	  unsigned int start = *((unsigned int*)(bamboo_thread_queue+2));
	  for(i = thread_counter; i > 0; i--) {
#ifdef GC_TBL_DEBUG
		bamboo_thread_queue[4+start] = 
		  (INTPTR)(flushObj((void *)bamboo_thread_queue[4+start
				], __LINE__, (void *)bamboo_thread_queue, 0));
#else
		bamboo_thread_queue[4+start] = 
		  (INTPTR)(flushObj((void *)bamboo_thread_queue[4+start]));
#endif
		start = (start+1)&bamboo_max_thread_num_mask;
	  }
	}
	unlockthreadqueue();
  }
#endif
} // void flushRuntimeObj(struct garbagelist * stackptr)

inline void flush(struct garbagelist * stackptr) {

  flushRuntimeObj(stackptr);

  while(true) {
    BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
    bool hasItems = gc_moreItems_I();
    BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
    if(!hasItems) {
      break;
    }

    GC_BAMBOO_DEBUGPRINT(0xe301);
    BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
    void * ptr = gc_dequeue_I();
#ifdef GC_TBL_DEBUG
    unsigned int bkptr = (unsigned int)ptr;
#endif
    BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
    if(ISSHAREDOBJ(ptr)) {
      // should be a local shared obj and should have mapping info
#ifdef GC_TBL_DEBUG
	  ptr = flushObj(ptr, __LINE__, ptr, 0);
#else
      ptr = flushObj(ptr);
#endif
      GC_BAMBOO_DEBUGPRINT(0xe302);
      GC_BAMBOO_DEBUGPRINT_REG(ptr);
      if(ptr == NULL) {
		BAMBOO_EXIT(0xb028);
      }
    } // if(ISSHAREDOBJ(ptr))
    if((!ISSHAREDOBJ(ptr))||(((int *)(ptr))[BAMBOOMARKBIT] == COMPACTED)) {
      int type = ((int *)(ptr))[0];
#ifdef GC_TBL_DEBUG
	  if((unsigned int)type >= (unsigned int)NUMTYPES) {
		tprintf("Error flushObj  %x, %x, %d, %d \n", bkptr, (int)ptr, type, 
			((int *)(ptr))[BAMBOOMARKBIT]);
		BAMBOO_EXIT(0xb029);
	  }
#endif
      // scan all pointers in ptr
      unsigned int * pointer;
      pointer=pointerarray[type];
      GC_BAMBOO_DEBUGPRINT(0xe303);
      GC_BAMBOO_DEBUGPRINT_REG(pointer);
      if (pointer==0) {
		/* Array of primitives */
		/* Do nothing */
      } else if (((unsigned int)pointer)==1) {
		GC_BAMBOO_DEBUGPRINT(0xe304);
		/* Array of pointers */
		struct ArrayObject *ao=(struct ArrayObject *) ptr;
		int length=ao->___length___;
		int j;
		for(j=0; j<length; j++) {
		  GC_BAMBOO_DEBUGPRINT(0xe305);
		  void *objptr=
			((void **)(((char *)&ao->___length___)+sizeof(int)))[j];
		  GC_BAMBOO_DEBUGPRINT_REG(objptr);
		  if(objptr != NULL) {
#ifdef GC_TBL_DEBUG
			void * dst = flushObj(objptr, __LINE__, ptr, j);
#else
			void * dst = flushObj(objptr);
#endif
			if(dst != NULL) {
			  ((void **)(((char *)&ao->___length___)+sizeof(int)))[j] = dst;
			}
		  }
		}
      } else {
		GC_BAMBOO_DEBUGPRINT(0xe306);
		unsigned int size=pointer[0];
		int i;
		for(i=1; i<=size; i++) {
		  GC_BAMBOO_DEBUGPRINT(0xe307);
		  unsigned int offset=pointer[i];
		  void * objptr=*((void **)(((char *)ptr)+offset));
		  GC_BAMBOO_DEBUGPRINT_REG(objptr);
		  if(objptr != NULL) {
#ifdef GC_TBL_DEBUG
			void * dst = flushObj(objptr, __LINE__, ptr, i);
#else
			void * dst = flushObj(objptr);
#endif
			if(dst != NULL) {
			  *((void **)(((char *)ptr)+offset)) = dst;
			}
		  }
		} // for(i=1; i<=size; i++)
      }  // if (pointer==0) else if (((INTPTR)pointer)==1) else ()
	  {
		pointer=pointerarray[OBJECTTYPE];
		//handle object class
		unsigned int size=pointer[0];
		int i;
		for(i=1; i<=size; i++) {
		  unsigned int offset=pointer[i];
		  void * objptr=*((void **)(((char *)ptr)+offset));
		  if(objptr != NULL) {
#ifdef GC_TBL_DEBUG
			void * dst = flushObj(objptr, __LINE__, ptr, i);
#else
			void * dst = flushObj(objptr);
#endif
			if(dst != NULL) {
			  *((void **)(((char *)ptr)+offset)) = dst;
			}
		  }
		}
	  }
      // restore the mark field, indicating that this obj has been flushed
      if(ISSHAREDOBJ(ptr)) {
		((int *)(ptr))[BAMBOOMARKBIT] = INIT;
      }
    }  //if((!ISSHAREDOBJ(ptr))||(((int *)(ptr))[BAMBOOMARKBIT] == COMPACTED))
  }   // while(gc_moreItems())
  GC_BAMBOO_DEBUGPRINT(0xe308);

  // TODO bug here: the startup core contains all lobjs' info, thus all the
  // lobjs are flushed in sequence.
  // flush lobjs
  while(gc_lobjmoreItems_I()) {
    GC_BAMBOO_DEBUGPRINT(0xe309);
    void * ptr = gc_lobjdequeue_I(NULL, NULL);
#ifdef GC_TBL_DEBUG
	ptr = flushObj(ptr, __LINE__, ptr, 0);
#else
    ptr = flushObj(ptr);
#endif
    GC_BAMBOO_DEBUGPRINT(0xe30a);
    GC_BAMBOO_DEBUGPRINT_REG(ptr);
    GC_BAMBOO_DEBUGPRINT_REG(tptr);
    GC_BAMBOO_DEBUGPRINT_REG(((int *)(tptr))[0]);
    if(ptr == NULL) {
      BAMBOO_EXIT(0xb02a);
    }
    if(((int *)(ptr))[BAMBOOMARKBIT] == COMPACTED) {
      int type = ((int *)(ptr))[0];
      // scan all pointers in ptr
      unsigned int * pointer;
      pointer=pointerarray[type];
      GC_BAMBOO_DEBUGPRINT(0xe30b);
      GC_BAMBOO_DEBUGPRINT_REG(pointer);
      if (pointer==0) {
		/* Array of primitives */
		/* Do nothing */
      } else if (((unsigned int)pointer)==1) {
		GC_BAMBOO_DEBUGPRINT(0xe30c);
		/* Array of pointers */
		struct ArrayObject *ao=(struct ArrayObject *) ptr;
		int length=ao->___length___;
		int j;
		for(j=0; j<length; j++) {
		  GC_BAMBOO_DEBUGPRINT(0xe30d);
		  void *objptr=
			((void **)(((char *)&ao->___length___)+sizeof(int)))[j];
		  GC_BAMBOO_DEBUGPRINT_REG(objptr);
		  if(objptr != NULL) {
#ifdef GC_TBL_DEBUG
			void * dst = flushObj(objptr, __LINE__, ptr, j);
#else
			void * dst = flushObj(objptr);
#endif
			if(dst != NULL) {
			  ((void **)(((char *)&ao->___length___)+sizeof(int)))[j] = dst;
			}
		  }
		}
      } else {
		GC_BAMBOO_DEBUGPRINT(0xe30e);
		unsigned int size=pointer[0];
		int i;
		for(i=1; i<=size; i++) {
		  GC_BAMBOO_DEBUGPRINT(0xe30f);
		  unsigned int offset=pointer[i];
		  void * objptr=*((void **)(((char *)ptr)+offset));

		  GC_BAMBOO_DEBUGPRINT_REG(objptr);
		  if(objptr != NULL) {
#ifdef GC_TBL_DEBUG
			void * dst = flushObj(objptr, __LINE__, ptr, i);
#else
			void * dst = flushObj(objptr);
#endif
			if(dst != NULL) {
			  *((void **)(((char *)ptr)+offset)) = dst;
			}
		  }
		}  // for(i=1; i<=size; i++)
      }  // if (pointer==0) else if (((INTPTR)pointer)==1) else ()
	  {
		pointer=pointerarray[OBJECTTYPE];
		//handle object class
		unsigned int size=pointer[0];
		int i;
		for(i=1; i<=size; i++) {
		  unsigned int offset=pointer[i];
		  void * objptr=*((void **)(((char *)ptr)+offset));
		  if(objptr != NULL) {
#ifdef GC_TBL_DEBUG
			void * dst = flushObj(objptr, __LINE__, ptr, i);
#else
			void * dst = flushObj(objptr);
#endif
			if(dst != NULL) {
			  *((void **)(((char *)ptr)+offset)) = dst;
			}
		  }
		}
	  }
      // restore the mark field, indicating that this obj has been flushed
      ((int *)(ptr))[BAMBOOMARKBIT] = INIT;
    }     // if(((int *)(ptr))[BAMBOOMARKBIT] == COMPACTED)
  }     // while(gc_lobjmoreItems())
  GC_BAMBOO_DEBUGPRINT(0xe310);

  // send flush finish message to core coordinator
  if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
    gccorestatus[BAMBOO_NUM_OF_CORE] = 0;
  } else {
    send_msg_2(STARTUPCORE, GCFINISHFLUSH, BAMBOO_NUM_OF_CORE, false);
  }
  GC_BAMBOO_DEBUGPRINT(0xe311);
} // flush()

#ifdef GC_CACHE_ADAPT
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
} // cacheAdapt_gc(bool isgccachestage)

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
} // int cacheAdapt_policy_hfh()

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
} // int cacheAdapt_policy_local()

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
} // int cacheAdapt_policy_hotest()

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
} // int cacheAdapt_policy_dominate()

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
} // void gc_quicksort(...)

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
} // int cacheAdapt_policy_overload()

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
} // int cacheAdapt_policy_overload()

void cacheAdapt_master() {
#ifdef GC_CACHE_ADAPT_SAMPLING_OUTPUT
  gc_output_cache_sampling_r();
#endif // GC_CACHE_ADAPT_SAMPLING_OUTPUT
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

void gc_output_cache_sampling() {
  unsigned int page_index = 0;
  VA page_sva = 0;
  unsigned int page_num = (BAMBOO_SHARED_MEM_SIZE) / (BAMBOO_PAGE_SIZE);
  for(page_index = 0; page_index < page_num; page_index++) {
	page_sva = gcbaseva + (BAMBOO_PAGE_SIZE) * page_index;
	unsigned int block = 0;
	BLOCKINDEX(page_sva, &block);
	unsigned int coren = gc_block2core[block%(NUMCORES4GC*2)];
	tprintf("va: %x page_index: %d host: %d\n", 
		(int)page_sva, page_index, coren);
	for(int i = 0; i < NUMCORESACTIVE; i++) {
	  int * local_tbl = (int *)((void *)gccachesamplingtbl
		  +size_cachesamplingtbl_local*i);
	  int freq = local_tbl[page_index];
	  printf("%8d ",freq);
	}
	printf("\n");
  }
  printf("=================\n");
} // gc_output_cache_sampling

void gc_output_cache_sampling_r() {
  unsigned int page_index = 0;
  VA page_sva = 0;
  unsigned int page_num = (BAMBOO_SHARED_MEM_SIZE) / (BAMBOO_PAGE_SIZE);
  for(page_index = 0; page_index < page_num; page_index++) {
	page_sva = gcbaseva + (BAMBOO_PAGE_SIZE) * page_index;
	unsigned int block = 0;
	BLOCKINDEX(page_sva, &block);
	unsigned int coren = gc_block2core[block%(NUMCORES4GC*2)];
	tprintf("va: %x page_index: %d host: %d\n", 
		(int)page_sva, page_index, coren);
	for(int i = 0; i < NUMCORESACTIVE; i++) {
	  int * local_tbl = (int *)((void *)gccachesamplingtbl_r
		  +size_cachesamplingtbl_local_r*i);
	  int freq = local_tbl[page_index]/BAMBOO_PAGE_SIZE;
	  printf("%8d ",freq);
	}
	printf("\n");
  }
  printf("=================\n");
} // gc_output_cache_sampling
#endif // GC_CACHE_ADAPT

inline void gc_collect(struct garbagelist * stackptr) {
  // inform the master that this core is at a gc safe point and is ready to 
  // do gc
  send_msg_4(STARTUPCORE, GCFINISHPRE, BAMBOO_NUM_OF_CORE, self_numsendobjs, 
	  self_numreceiveobjs, false);

  // core collector routine
  while(true) {
    if(INITPHASE == gcphase) {
      break;
    }
  }
#ifdef RAWPATH // TODO GC_DEBUG
  printf("(%X,%X) Do initGC\n", udn_tile_coord_x(), udn_tile_coord_y());
#endif
  initGC();
#ifdef GC_CACHE_ADAPT
  // prepare for cache adaption:
  cacheAdapt_gc(true);
#endif // GC_CACHE_ADAPT
  //send init finish msg to core coordinator
  send_msg_2(STARTUPCORE, GCFINISHINIT, BAMBOO_NUM_OF_CORE, false);

  while(true) {
    if(MARKPHASE == gcphase) {
      break;
    }
  }
#ifdef RAWPATH // TODO GC_DEBUG
  printf("(%x,%x) Start mark phase\n", udn_tile_coord_x(), 
	     udn_tile_coord_y());
#endif
  mark(true, stackptr);
#ifdef RAWPATH // TODO GC_DEBUG
  printf("(%x,%x) Finish mark phase, start compact phase\n", 
	     udn_tile_coord_x(), udn_tile_coord_y());
#endif
  compact();
#ifdef RAWPATH // TODO GC_DEBUG
  printf("(%x,%x) Finish compact phase\n", udn_tile_coord_x(),
	     udn_tile_coord_y());
#endif

  while(true) {
    if(FLUSHPHASE == gcphase) {
      break;
    }
  }
#ifdef RAWPATH // TODO GC_DEBUG
  printf("(%x,%x) Start flush phase\n", udn_tile_coord_x(), 
	     udn_tile_coord_y());
#endif
#ifdef GC_PROFILE
  // send the num of obj/liveobj/forwardobj to the startupcore
  if(STARTUPCORE != BAMBOO_NUM_OF_CORE) {
	send_msg_4(STARTUPCORE, GCPROFILES, gc_num_obj, 
		gc_num_liveobj, gc_num_forwardobj, false);
  }
  gc_num_obj = 0;
#endif // GC_PROFLIE
  flush(stackptr);
#ifdef RAWPATH // TODO GC_DEBUG
  printf("(%x,%x) Finish flush phase\n", udn_tile_coord_x(),
	     udn_tile_coord_y());
#endif

#ifdef GC_CACHE_ADAPT
  while(true) {
    if(PREFINISHPHASE == gcphase) {
      break;
    }
  }
#ifdef RAWPATH // TODO GC_DEBUG
  printf("(%x,%x) Start prefinish phase\n", udn_tile_coord_x(), 
	     udn_tile_coord_y());
#endif
  // cache adapt phase
  cacheAdapt_mutator();
  cacheAdapt_gc(false);
  //send init finish msg to core coordinator
  send_msg_2(STARTUPCORE, GCFINISHPREF, BAMBOO_NUM_OF_CORE, false);
#ifdef RAWPATH // TODO GC_DEBUG
  printf("(%x,%x) Finish prefinish phase\n", udn_tile_coord_x(),
	     udn_tile_coord_y());
#endif
#ifdef GC_CACHE_SAMPLING
  // reset the sampling arrays
  bamboo_dtlb_sampling_reset();
#endif // GC_CACHE_SAMPLING
  if(BAMBOO_NUM_OF_CORE < NUMCORESACTIVE) {
	// zero out the gccachesamplingtbl
	BAMBOO_MEMSET_WH(gccachesamplingtbl_local,0,size_cachesamplingtbl_local);
	BAMBOO_MEMSET_WH(gccachesamplingtbl_local_r,0,
		size_cachesamplingtbl_local_r);
  }
#endif // GC_CACHE_ADAPT

  // invalidate all shared mem pointers
  bamboo_cur_msp = NULL;
  bamboo_smem_size = 0;
  bamboo_smem_zero_top = NULL;

  while(true) {
    if(FINISHPHASE == gcphase) {
      break;
    }
  }

#ifdef RAWPATH // TODO GC_DEBUG
  printf("(%x,%x) Finish gc! \n", udn_tile_coord_x(), udn_tile_coord_y());
#endif
} // void gc_collect(struct garbagelist * stackptr)

inline void gc_nocollect(struct garbagelist * stackptr) {
  // inform the master that this core is at a gc safe point and is ready to 
  // do gc
  send_msg_4(STARTUPCORE, GCFINISHPRE, BAMBOO_NUM_OF_CORE, self_numsendobjs, 
	  self_numreceiveobjs, false);
  
  while(true) {
    if(INITPHASE == gcphase) {
      break;
    }
  }
#ifdef RAWPATH // TODO GC_DEBUG
  printf("(%x,%x) Do initGC\n", udn_tile_coord_x(), udn_tile_coord_y());
#endif
  initGC();
#ifdef GC_CACHE_ADAPT
  // prepare for cache adaption:
  cacheAdapt_gc(true);
#endif // GC_CACHE_ADAPT
  //send init finish msg to core coordinator
  send_msg_2(STARTUPCORE, GCFINISHINIT, BAMBOO_NUM_OF_CORE, false);

  while(true) {
    if(MARKPHASE == gcphase) {
      break;
    }
  }
#ifdef RAWPATH // TODO GC_DEBUG
  printf("(%x,%x) Start mark phase\n", udn_tile_coord_x(), 
	     udn_tile_coord_y());
#endif
  mark(true, stackptr);
#ifdef RAWPATH // TODO GC_DEBUG
  printf("(%x,%x) Finish mark phase, wait for flush\n", 
	     udn_tile_coord_x(), udn_tile_coord_y());
#endif

  // non-gc core collector routine
  while(true) {
    if(FLUSHPHASE == gcphase) {
      break;
    }
  }
#ifdef RAWPATH // TODO GC_DEBUG
  printf("(%x,%x) Start flush phase\n", udn_tile_coord_x(), 
	     udn_tile_coord_y());
#endif
#ifdef GC_PROFILE
  if(STARTUPCORE != BAMBOO_NUM_OF_CORE) {
	send_msg_4(STARTUPCORE, GCPROFILES, gc_num_obj, 
		gc_num_liveobj, gc_num_forwardobj, false);
  }
  gc_num_obj = 0;
#endif // GC_PROFLIE
  flush(stackptr);
#ifdef RAWPATH // TODO GC_DEBUG
  printf("(%x,%x) Finish flush phase\n", udn_tile_coord_x(), 
	     udn_tile_coord_y());
#endif

#ifdef GC_CACHE_ADAPT
  while(true) {
    if(PREFINISHPHASE == gcphase) {
      break;
    }
  }
#ifdef RAWPATH // TODO GC_DEBUG
  printf("(%x,%x) Start prefinish phase\n", udn_tile_coord_x(), 
	     udn_tile_coord_y());
#endif
  // cache adapt phase
  cacheAdapt_mutator();
  cacheAdapt_gc(false);
  //send init finish msg to core coordinator
  send_msg_2(STARTUPCORE, GCFINISHPREF, BAMBOO_NUM_OF_CORE, false);
#ifdef RAWPATH // TODO GC_DEBUG
  printf("(%x,%x) Finish prefinish phase\n", udn_tile_coord_x(),
	     udn_tile_coord_y());
#endif
#ifdef GC_CACHE_SAMPLING
  // reset the sampling arrays
  bamboo_dtlb_sampling_reset();
#endif // GC_CACHE_SAMPLING
  if(BAMBOO_NUM_OF_CORE < NUMCORESACTIVE) {
	// zero out the gccachesamplingtbl
	BAMBOO_MEMSET_WH(gccachesamplingtbl_local,0,size_cachesamplingtbl_local);
	BAMBOO_MEMSET_WH(gccachesamplingtbl_local_r,0,
		size_cachesamplingtbl_local_r);
  }
#endif // GC_CACHE_ADAPT

  // invalidate all shared mem pointers
  bamboo_cur_msp = NULL;
  bamboo_smem_size = 0;
  bamboo_smem_zero_top = NULL;

  while(true) {
    if(FINISHPHASE == gcphase) {
      break;
    }
  }
#ifdef RAWPATH // TODO GC_DEBUG
  printf("(%x,%x) Finish gc! \n", udn_tile_coord_x(), udn_tile_coord_y());
#endif
} // void gc_collect(struct garbagelist * stackptr)

inline void gc_master(struct garbagelist * stackptr) {
  tprintf("start GC !!!!!!!!!!!!! \n");

  gcphase = INITPHASE;
  int i = 0;
  waitconfirm = false;
  numconfirm = 0;
  initGC();

  // Note: all cores need to init gc including non-gc cores
  for(i = 1; i < NUMCORESACTIVE /*NUMCORES4GC*/; i++) {
	// send GC init messages to all cores
	send_msg_1(i, GCSTARTINIT, false);
  }
  bool isfirst = true;
  bool allStall = false;

#ifdef GC_CACHE_ADAPT
  // prepare for cache adaption:
  cacheAdapt_gc(true);
#endif // GC_CACHE_ADAPT

#ifdef RAWPATH // TODO GC_DEBUG
  printf("(%x,%x) Check core status \n", udn_tile_coord_x(), 
		 udn_tile_coord_y());
#endif

  gccorestatus[BAMBOO_NUM_OF_CORE] = 0;
  while(true) {
	BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
	if(gc_checkAllCoreStatus_I()) {
	  BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
	  break;
	}
	BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
  }
#ifdef GC_PROFILE
#ifdef MGC_SPEC
	if(gc_profile_flag) {
#endif
  gc_profileItem();
#ifdef MGC_SPEC
	}
#endif
#endif
#ifdef GC_CACHE_ADAPT_POLICY_OUTPUT
  gc_output_cache_sampling();
#endif // GC_CACHE_ADAPT
#ifdef RAWPATH // TODO GC_DEBUG
  printf("(%x,%x) Start mark phase \n", udn_tile_coord_x(), 
		 udn_tile_coord_y());
#endif
  // restore the gcstatus of all cores
  // Note: all cores have to do mark including non-gc cores
  gccorestatus[BAMBOO_NUM_OF_CORE] = 1;
  for(i = 1; i < NUMCORESACTIVE; ++i) {
	gccorestatus[i] = 1;
	// send GC start messages to all cores
	send_msg_1(i, GCSTART, false);
  }

  gcphase = MARKPHASE;
  // mark phase
  while(MARKPHASE == gcphase) {
	mark(isfirst, stackptr);
	if(isfirst) {
	  isfirst = false;
	}

	// check gcstatus
	checkMarkStatue();
  }   // while(MARKPHASE == gcphase)
  // send msgs to all cores requiring large objs info
  // Note: only need to ask gc cores, non-gc cores do not host any objs
  numconfirm = NUMCORES4GC - 1;
  for(i = 1; i < NUMCORES4GC; ++i) {
	send_msg_1(i, GCLOBJREQUEST, false);
  }
  gcloads[BAMBOO_NUM_OF_CORE] = gccurr_heaptop;
  while(true) {
	if(numconfirm==0) {
	  break;
	}
  }   // wait for responses
  // check the heaptop
  if(gcheaptop < gcmarkedptrbound) {
	gcheaptop = gcmarkedptrbound;
  }
#ifdef GC_PROFILE
#ifdef MGC_SPEC
	if(gc_profile_flag) {
#endif
  gc_profileItem();
#ifdef MGC_SPEC
	}
#endif
#endif
#ifdef RAWPATH // TODO GC_DEBUG
  printf("(%x,%x) prepare to cache large objs \n", udn_tile_coord_x(),
		 udn_tile_coord_y());
#endif
  // cache all large objs
  if(!cacheLObjs()) {
	// no enough space to cache large objs
	BAMBOO_EXIT(0xb02b);
  }
  // predict number of blocks to fill for each core
  unsigned int tmpheaptop = 0;
  int numpbc = loadbalance(&tmpheaptop);
  // TODO
  numpbc = (BAMBOO_SHARED_MEM_SIZE)/(BAMBOO_SMEM_SIZE);
#ifdef RAWPATH // TODO GC_DEBUG
  printf("(%x,%x) mark phase finished \n", udn_tile_coord_x(), 
		 udn_tile_coord_y());
#endif
  //int tmptopptr = 0;
  //BASEPTR(gctopcore, 0, &tmptopptr);
  // TODO
  //tmptopptr = gcbaseva + (BAMBOO_SHARED_MEM_SIZE);
  tmpheaptop = gcbaseva + (BAMBOO_SHARED_MEM_SIZE);
  GC_BAMBOO_DEBUGPRINT(0xabab);
  GC_BAMBOO_DEBUGPRINT_REG(tmpheaptop);
  for(i = 0; i < NUMCORES4GC; ++i) {
	unsigned int tmpcoreptr = 0;
	BASEPTR(i, numpbc, &tmpcoreptr);
	// init some data strutures for compact phase
	gcloads[i] = 0;
	gcfilledblocks[i] = 0;
	gcrequiredmems[i] = 0;
	gccorestatus[i] = 1;
	//send start compact messages to all cores
	//TODO bug here, do not know if the direction is positive or negtive?
	if (tmpcoreptr < tmpheaptop) {
	  gcstopblock[i] = numpbc + 1;
	  if(i != STARTUPCORE) {
		send_msg_2(i, GCSTARTCOMPACT, numpbc+1, false);
	  } else {
		gcblock2fill = numpbc+1;
	  }   // if(i != STARTUPCORE)
	} else {
	  gcstopblock[i] = numpbc;
	  if(i != STARTUPCORE) {
		send_msg_2(i, GCSTARTCOMPACT, numpbc, false);
	  } else {
		gcblock2fill = numpbc;
	  }  // if(i != STARTUPCORE)
	}
	GC_BAMBOO_DEBUGPRINT(0xf000+i);
	GC_BAMBOO_DEBUGPRINT_REG(tmpcoreptr);
	GC_BAMBOO_DEBUGPRINT_REG(gcstopblock[i]);
  }

  BAMBOO_CACHE_MF();

#ifdef GC_PROFILE
#ifdef MGC_SPEC
	if(gc_profile_flag) {
#endif
  gc_profileItem();
#ifdef MGC_SPEC
	}
#endif
#endif

  // compact phase
  bool finalcompact = false;
  // initialize pointers for comapcting
  struct moveHelper * orig =
	(struct moveHelper *)RUNMALLOC(sizeof(struct moveHelper));
  struct moveHelper * to =
	(struct moveHelper *)RUNMALLOC(sizeof(struct moveHelper));
  initOrig_Dst(orig, to);
  int filledblocks = 0;
  unsigned int heaptopptr = 0;
  bool finishcompact = false;
  bool iscontinue = true;
  bool localcompact = true;
  while((COMPACTPHASE == gcphase) || (SUBTLECOMPACTPHASE == gcphase)) {
	if((!finishcompact) && iscontinue) {
	  GC_BAMBOO_DEBUGPRINT(0xeaa01);
	  GC_BAMBOO_DEBUGPRINT_REG(numpbc);
	  GC_BAMBOO_DEBUGPRINT_REG(gcblock2fill);
	  finishcompact = compacthelper(orig, to, &filledblocks,
									&heaptopptr, &localcompact);
	  GC_BAMBOO_DEBUGPRINT(0xeaa02);
	  GC_BAMBOO_DEBUGPRINT_REG(finishcompact);
	  GC_BAMBOO_DEBUGPRINT_REG(gctomove);
	  GC_BAMBOO_DEBUGPRINT_REG(gcrequiredmems[0]);
	  GC_BAMBOO_DEBUGPRINT_REG(gcfilledblocks[0]);
	  GC_BAMBOO_DEBUGPRINT_REG(gcstopblock[0]);
	}

	BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
	if(gc_checkCoreStatus_I()) {
	  // all cores have finished compacting
	  // restore the gcstatus of all cores
	  for(i = 0; i < NUMCORES4GC; ++i) {
		gccorestatus[i] = 1;
	  }
	  BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
	  break;
	} else {
	  BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
	  // check if there are spare mem for pending move requires
	  if(COMPACTPHASE == gcphase) {
		GC_BAMBOO_DEBUGPRINT(0xeaa03);
		resolvePendingMoveRequest();
		GC_BAMBOO_DEBUGPRINT_REG(gctomove);
	  } else {
		GC_BAMBOO_DEBUGPRINT(0xeaa04);
		compact2Heaptop();
	  }
	}   // if(gc_checkCoreStatus_I()) else ...

	if(gctomove) {
	  GC_BAMBOO_DEBUGPRINT(0xeaa05);
	  GC_BAMBOO_DEBUGPRINT_REG(gcmovestartaddr);
	  GC_BAMBOO_DEBUGPRINT_REG(gcblock2fill);
	  GC_BAMBOO_DEBUGPRINT_REG(gctomove);
	  to->ptr = gcmovestartaddr;
	  to->numblocks = gcblock2fill - 1;
	  to->bound = (to->numblocks==0) ?
				  BAMBOO_SMEM_SIZE_L :
				  BAMBOO_SMEM_SIZE_L+BAMBOO_SMEM_SIZE*to->numblocks;
	  BASEPTR(gcdstcore, to->numblocks, &(to->base));
	  to->offset = to->ptr - to->base;
	  to->top = (to->numblocks==0) ?
				(to->offset) : (to->bound-BAMBOO_SMEM_SIZE+to->offset);
	  to->base = to->ptr;
	  to->offset = BAMBOO_CACHE_LINE_SIZE;
	  to->ptr += to->offset;                         // for header
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
	}  // if(gctomove)
  }  // while(COMPACTPHASE == gcphase)
#ifdef GC_PROFILE
#ifdef MGC_SPEC
	if(gc_profile_flag) {
#endif
  gc_profileItem();
#ifdef MGC_SPEC
	}
#endif
#endif
#ifdef RAWPATH // TODO GC_DEBUG
  printf("(%x,%x) prepare to move large objs \n", udn_tile_coord_x(),
		 udn_tile_coord_y());
#endif
  // move largeObjs
  moveLObjs();
#ifdef RAWPATH // TODO GC_DEBUG
  printf("(%x,%x) compact phase finished \n", udn_tile_coord_x(), 
		 udn_tile_coord_y());
#endif
  RUNFREE(orig);
  RUNFREE(to);
  orig = to = NULL;

  gcphase = FLUSHPHASE;
  gccorestatus[BAMBOO_NUM_OF_CORE] = 1;
  // Note: all cores should flush their runtime data including non-gc
  //       cores
  for(i = 1; i < NUMCORESACTIVE; ++i) {
	// send start flush messages to all cores
	gccorestatus[i] = 1;
	send_msg_1(i, GCSTARTFLUSH, false);
  }
#ifdef GC_PROFILE
#ifdef MGC_SPEC
	if(gc_profile_flag) {
#endif
  gc_profileItem();
#ifdef MGC_SPEC
	}
#endif
#endif
#ifdef RAWPATH // TODO GC_DEBUG
  printf("(%x,%x) Start flush phase \n", udn_tile_coord_x(), 
		 udn_tile_coord_y());
#endif
  // flush phase
  flush(stackptr);

#ifdef GC_CACHE_ADAPT
  // now the master core need to decide the new cache strategy
  cacheAdapt_master();
#endif // GC_CACHE_ADAPT

  gccorestatus[BAMBOO_NUM_OF_CORE] = 0;
  while(FLUSHPHASE == gcphase) {
	// check the status of all cores
	BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
	if(gc_checkAllCoreStatus_I()) {
	  BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
	  break;
	}
	BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
  }  // while(FLUSHPHASE == gcphase)
#ifdef RAWPATH // TODO GC_DEBUG
  printf("(%x,%x) Finish flush phase \n", udn_tile_coord_x(), 
		 udn_tile_coord_y());
#endif

#ifdef GC_CACHE_ADAPT
#ifdef GC_PROFILE
#ifdef MGC_SPEC
	if(gc_profile_flag) {
#endif
  gc_profileItem();
#ifdef MGC_SPEC
	}
#endif
#endif
  gcphase = PREFINISHPHASE;
  gccorestatus[BAMBOO_NUM_OF_CORE] = 1;
  // Note: all cores should flush their runtime data including non-gc
  //       cores
  for(i = 1; i < NUMCORESACTIVE; ++i) {
	// send start flush messages to all cores
	gccorestatus[i] = 1;
	send_msg_1(i, GCSTARTPREF, false);
  }
#ifdef RAWPATH // TODO GC_DEBUG
  printf("(%x,%x) Start prefinish phase \n", udn_tile_coord_x(), 
		 udn_tile_coord_y());
#endif
  // cache adapt phase
  cacheAdapt_mutator();
#ifdef MGC_SPEC
  if(gc_profile_flag) {
#endif
#ifdef GC_CACHE_ADAPT_OUTPUT
  bamboo_output_cache_policy();
#endif
#ifdef MGC_SPEC
  }
#endif
  cacheAdapt_gc(false);

  gccorestatus[BAMBOO_NUM_OF_CORE] = 0;
  while(PREFINISHPHASE == gcphase) {
	// check the status of all cores
	BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
	if(gc_checkAllCoreStatus_I()) {
	  BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
	  break;
	}
	BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
  }  // while(PREFINISHPHASE == gcphase)

#ifdef GC_CACHE_SAMPLING
  // reset the sampling arrays
  bamboo_dtlb_sampling_reset();
#endif // GC_CACHE_SAMPLING
  if(BAMBOO_NUM_OF_CORE < NUMCORESACTIVE) {
	// zero out the gccachesamplingtbl
	BAMBOO_MEMSET_WH(gccachesamplingtbl_local,0,size_cachesamplingtbl_local);
	BAMBOO_MEMSET_WH(gccachesamplingtbl_local_r,0,
		size_cachesamplingtbl_local_r);
	BAMBOO_MEMSET_WH(gccachepolicytbl,0,size_cachepolicytbl);
  }
#endif // GC_CACHE_ADAPT

  gcphase = FINISHPHASE;

  // invalidate all shared mem pointers
  // put it here as it takes time to inform all the other cores to
  // finish gc and it might cause problem when some core resumes
  // mutator earlier than the other cores
  bamboo_cur_msp = NULL;
  bamboo_smem_size = 0;
  bamboo_smem_zero_top = NULL;

#ifdef GC_PROFILE
#ifdef MGC_SPEC
	if(gc_profile_flag) {
#endif
  gc_profileEnd();
#ifdef MGC_SPEC
	}
#endif
#endif
  gccorestatus[BAMBOO_NUM_OF_CORE] = 1;
  for(i = 1; i < NUMCORESACTIVE; ++i) {
	// send gc finish messages to all cores
	send_msg_1(i, GCFINISH, false);
	gccorestatus[i] = 1;
  }

  gcflag = false;
  gcprocessing = false;
#ifdef RAWPATH // TODO GC_DEBUG
  printf("(%x,%x) gc finished   \n", udn_tile_coord_x(), 
		 udn_tile_coord_y());
#endif
  tprintf("finish GC ! \n");
} // void gc_master(struct garbagelist * stackptr)

inline bool gc(struct garbagelist * stackptr) {
  // check if do gc
  if(!gcflag) {
    gcprocessing = false;
    return false;
  }

#ifdef GC_CACHE_ADAPT
#ifdef GC_CACHE_SAMPLING
    // disable the timer interrupt
    bamboo_mask_timer_intr();
#endif 
#endif
  // core coordinator routine
  if(0 == BAMBOO_NUM_OF_CORE) {
#ifdef GC_DEBUG
    printf("(%x,%x) Check if can do gc or not\n", udn_tile_coord_x(),
		   udn_tile_coord_y());
#endif
	bool isallstall = true;
	gccorestatus[BAMBOO_NUM_OF_CORE] = 0;
	BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
	int ti = 0;
	for(ti = 0; ti < NUMCORESACTIVE; ++ti) {
	  if(gccorestatus[ti] != 0) {
		isallstall = false;
		break;
	  }
	}
	if(!isallstall) {
	  BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
	  // some of the cores are still executing the mutator and did not reach
	  // some gc safe point, therefore it is not ready to do gc
	  gcflag = true;
	  return false;
	} else {
#ifdef GC_PROFILE
#ifdef MGC_SPEC
	if(gc_profile_flag) {
#endif
    gc_profileStart();
#ifdef MGC_SPEC
	}
#endif
#endif
pregccheck:
	  gcnumsendobjs[0][BAMBOO_NUM_OF_CORE] = self_numsendobjs;
	  gcnumreceiveobjs[0][BAMBOO_NUM_OF_CORE] = self_numreceiveobjs;
	  int sumsendobj = 0;
	  GC_BAMBOO_DEBUGPRINT(0xec04);
	  for(int i = 0; i < NUMCORESACTIVE; ++i) {
		sumsendobj += gcnumsendobjs[0][i];
		GC_BAMBOO_DEBUGPRINT(0xf000 + gcnumsendobjs[0][i]);
	  }  // for(i = 1; i < NUMCORESACTIVE; ++i)
	  GC_BAMBOO_DEBUGPRINT(0xec05);
	  GC_BAMBOO_DEBUGPRINT_REG(sumsendobj);
	  for(int i = 0; i < NUMCORESACTIVE; ++i) {
		sumsendobj -= gcnumreceiveobjs[0][i];
		GC_BAMBOO_DEBUGPRINT(0xf000 + gcnumreceiveobjs[i]);
	  }  // for(i = 1; i < NUMCORESACTIVE; ++i)
	  GC_BAMBOO_DEBUGPRINT(0xec06);
	  GC_BAMBOO_DEBUGPRINT_REG(sumsendobj);
	  if(0 != sumsendobj) {
		// there were still some msgs on the fly, wait until there 
		// are some update pregc information coming and check it again
		gcprecheck = false;
		BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
		while(true) {
		  if(gcprecheck) {
			break;
		  }
		}
		goto pregccheck;
	  } else {
		BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
	  }
	}
#ifdef RAWPATH // TODO GC_DEBUG
    printf("(%x,%x) start gc! \n", udn_tile_coord_x(), udn_tile_coord_y());
#endif
	// Zero out the remaining bamboo_cur_msp 
	// Only zero out the first 4 bytes of the remaining memory
	// Move the operation here because for the GC_CACHE_ADAPT version,
	// we need to make sure during the gcinit phase the shared heap is not 
	// touched. Otherwise, there would be problem when adapt the cache 
	// strategy.
	if((bamboo_cur_msp != 0) 
		&& (bamboo_smem_zero_top == bamboo_cur_msp) 
		&& (bamboo_smem_size > 0)) {
	  *((int *)bamboo_cur_msp) = 0;
	}
#ifdef GC_FLUSH_DTLB
	if(gc_num_flush_dtlb < GC_NUM_FLUSH_DTLB) {
	  BAMBOO_CLEAN_DTLB();
	  gc_num_flush_dtlb++;
	}
#endif
#ifdef GC_CACHE_ADAPT
#ifdef GC_CACHE_SAMPLING
    // get the sampling data 
    bamboo_output_dtlb_sampling();
#endif // GC_CACHE_SAMPLING
#endif // GC_CACHE_ADAPT
	gcprocessing = true;
	gc_master(stackptr);
  } else if(BAMBOO_NUM_OF_CORE < NUMCORES4GC) {
	// Zero out the remaining bamboo_cur_msp 
	// Only zero out the first 4 bytes of the remaining memory
	// Move the operation here because for the GC_CACHE_ADAPT version,
	// we need to make sure during the gcinit phase the shared heap is not 
	// touched. Otherwise, there would be problem when adapt the cache 
	// strategy.
	if((bamboo_cur_msp != 0) 
		&& (bamboo_smem_zero_top == bamboo_cur_msp) 
		&& (bamboo_smem_size > 0)) {
	  *((int *)bamboo_cur_msp) = 0;
	}
#ifdef GC_FLUSH_DTLB
	if(gc_num_flush_dtlb < GC_NUM_FLUSH_DTLB) {
	  BAMBOO_CLEAN_DTLB();
	  gc_num_flush_dtlb++;
	}
#endif
#ifdef GC_CACHE_ADAPT
#ifdef GC_CACHE_SAMPLING
	if(BAMBOO_NUM_OF_CORE < NUMCORESACTIVE) {
	  // get the sampling data 
	  bamboo_output_dtlb_sampling();
	}
#endif // GC_CACHE_SAMPLING
#endif // GC_CACHE_ADAPT
    gcprocessing = true;
    gc_collect(stackptr);
  } else {
	// Zero out the remaining bamboo_cur_msp 
	// Only zero out the first 4 bytes of the remaining memory
	// Move the operation here because for the GC_CACHE_ADAPT version,
	// we need to make sure during the gcinit phase the shared heap is not 
	// touched. Otherwise, there would be problem when adapt the cache 
	// strategy.
	if((bamboo_cur_msp != 0) 
		&& (bamboo_smem_zero_top == bamboo_cur_msp) 
		&& (bamboo_smem_size > 0)) {
	  *((int *)bamboo_cur_msp) = 0;
	}
#ifdef GC_FLUSH_DTLB
	if(gc_num_flush_dtlb < GC_NUM_FLUSH_DTLB) {
	  BAMBOO_CLEAN_DTLB();
	  gc_num_flush_dtlb++;
	}
#endif
#ifdef GC_CACHE_ADAPT
#ifdef GC_CACHE_SAMPLING
	if(BAMBOO_NUM_OF_CORE < NUMCORESACTIVE) {
	  // get the sampling data 
	  bamboo_output_dtlb_sampling();
	}
#endif // GC_CACHE_SAMPLING
#endif // GC_CACHE_ADAPT
    // not a gc core, should wait for gcfinish msg
    gcprocessing = true;
    gc_nocollect(stackptr);
  }
#ifdef GC_CACHE_ADAPT
#ifdef GC_CACHE_SAMPLING
  // enable the timer interrupt
  bamboo_tile_timer_set_next_event(GC_TILE_TIMER_EVENT_SETTING); 
  bamboo_unmask_timer_intr();
#endif // GC_CACHE_SAMPLING
#endif // GC_CACHE_ADAPT

  return true;
} // void gc(struct garbagelist * stackptr)

#ifdef GC_PROFILE
inline void gc_profileStart(void) {
  if(!gc_infoOverflow) {
    GCInfo* gcInfo = RUNMALLOC(sizeof(struct gc_info));
    gc_infoArray[gc_infoIndex] = gcInfo;
    gcInfo->index = 1;
    gcInfo->time[0] = BAMBOO_GET_EXE_TIME();
  }
}

inline void gc_profileItem(void) {
  if(!gc_infoOverflow) {
    GCInfo* gcInfo = gc_infoArray[gc_infoIndex];
    gcInfo->time[gcInfo->index++] = BAMBOO_GET_EXE_TIME();
  }
}

inline void gc_profileEnd(void) {
  if(!gc_infoOverflow) {
    GCInfo* gcInfo = gc_infoArray[gc_infoIndex];
    gcInfo->time[gcInfo->index++] = BAMBOO_GET_EXE_TIME();
	gcInfo->time[gcInfo->index++] = gc_num_livespace;
	gcInfo->time[gcInfo->index++] = gc_num_freespace;
	gcInfo->time[gcInfo->index++] = gc_num_lobj;
	gcInfo->time[gcInfo->index++] = gc_num_lobjspace;
	gcInfo->time[gcInfo->index++] = gc_num_obj;
	gcInfo->time[gcInfo->index++] = gc_num_liveobj;
	gcInfo->time[gcInfo->index++] = gc_num_forwardobj;
    gc_infoIndex++;
    if(gc_infoIndex == GCINFOLENGTH) {
      gc_infoOverflow = true;
      //taskInfoIndex = 0;
    }
  }
}

// output the profiling data
void gc_outputProfileData() {
  int i = 0;
  int j = 0;
  unsigned long long totalgc = 0;

#ifndef BAMBOO_MEMPROF
  BAMBOO_PRINT(0xdddd);
#endif
  // output task related info
  for(i= 0; i < gc_infoIndex; i++) {
    GCInfo * gcInfo = gc_infoArray[i];
#ifdef BAMBOO_MEMPROF
    unsigned long long tmp=gcInfo->time[gcInfo->index-8]-gcInfo->time[0]; //0;
#else
	unsigned long long tmp = 0;
    BAMBOO_PRINT(0xddda);
    for(j = 0; j < gcInfo->index - 7; j++) {
      BAMBOO_PRINT(gcInfo->time[j]);
      BAMBOO_PRINT(gcInfo->time[j]-tmp);
      BAMBOO_PRINT(0xdddb);
      tmp = gcInfo->time[j];
    }
    tmp = (tmp-gcInfo->time[0]);
    BAMBOO_PRINT_REG(tmp);
	BAMBOO_PRINT(0xdddc);
	BAMBOO_PRINT(gcInfo->time[gcInfo->index - 7]);
	BAMBOO_PRINT(gcInfo->time[gcInfo->index - 6]);
	BAMBOO_PRINT(gcInfo->time[gcInfo->index - 5]);
	BAMBOO_PRINT(gcInfo->time[gcInfo->index - 4]);
	BAMBOO_PRINT(gcInfo->time[gcInfo->index - 3]);
	BAMBOO_PRINT(gcInfo->time[gcInfo->index - 2]);
	BAMBOO_PRINT(gcInfo->time[gcInfo->index - 1]);
    BAMBOO_PRINT(0xddde);
#endif
    totalgc += tmp;
  }
#ifndef BAMBOO_MEMPROF
  BAMBOO_PRINT(0xdddf);
#endif
  BAMBOO_PRINT_REG(totalgc);

  if(gc_infoOverflow) {
    BAMBOO_PRINT(0xefee);
  }

#ifndef BAMBOO_MEMPROF
  BAMBOO_PRINT(0xeeee);
#endif
}
#endif  // #ifdef GC_PROFILE

#endif
