// BAMBOO_EXIT(0xb000);
// TODO: DO NOT support tag!!!
#ifdef MULTICORE_GC
#include "runtime.h"
#include "multicoregarbage.h"
#include "multicoregcmark.h"
#include "multicoregccompact.h"
#include "multicoregcflush.h"
#include "multicoreruntime.h"
#include "multicoregcprofile.h"

struct pointerblock *gchead=NULL;
int gcheadindex=0;
struct pointerblock *gctail=NULL;
int gctailindex=0;
struct pointerblock *gctail2=NULL;
int gctailindex2=0;
struct pointerblock *gcspare=NULL;

struct lobjpointerblock *gclobjhead=NULL;
int gclobjheadindex=0;
struct lobjpointerblock *gclobjtail=NULL;
int gclobjtailindex=0;
struct lobjpointerblock *gclobjtail2=NULL;
int gclobjtailindex2=0;
struct lobjpointerblock *gclobjspare=NULL;

#ifdef MULTICORE_GC
#ifdef SMEMM
extern unsigned int gcmem_mixed_threshold;
extern unsigned int gcmem_mixed_usedmem;
#endif // SMEMM
#endif // MULTICORE_GC

#ifdef GC_DEBUG
// dump whole mem in blocks
INLINE void dumpSMem() {
  int block = 0;
  int sblock = 0;
  unsigned int j = 0;
  void * i = 0;
  int coren = 0;
  int x = 0;
  int y = 0;
  printf("(%x,%x) Dump shared mem: \n",udn_tile_coord_x(),udn_tile_coord_y());
  // reserved blocks for sblocktbl
  printf("(%x,%x) ++++ reserved sblocks ++++ \n", udn_tile_coord_x(),
      udn_tile_coord_y());
  for(i=BAMBOO_BASE_VA; (unsinged int)i<(unsigned int)gcbaseva; i+= 4*16) {
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
  for(i=gcbaseva;
      (unsigned int)i<(unsigned int)(gcbaseva+BAMBOO_SHARED_MEM_SIZE); 
      i+=4*16) {
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
      x = BAMBOO_COORDS_X(coren);
      y = BAMBOO_COORDS_Y(coren);
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

INLINE void initmulticoregcdata() {
  int i = 0;
  if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
    // startup core to initialize corestatus[]
    for(i = 0; i < NUMCORESACTIVE; ++i) {
      gccorestatus[i] = 1;
      gcnumsendobjs[0][i] = gcnumsendobjs[1][i] = 0;
      gcnumreceiveobjs[0][i] = gcnumreceiveobjs[1][i] = 0;
    } 
    for(i = 0; i < NUMCORES4GC; ++i) {
      gcloads[i] = 0;
      gcrequiredmems[i] = 0;
      gcstopblock[i] = 0;
      gcfilledblocks[i] = 0;
    }
  }

  bamboo_smem_zero_top = NULL;
  gcflag = false;
  gcprocessing = false;
  gcphase = FINISHPHASE;
  gcprecheck = true;
  gccurr_heaptop = 0;
  gcself_numsendobjs = 0;
  gcself_numreceiveobjs = 0;
  gcmarkedptrbound = 0;
  gcforwardobjtbl = allocateMGCHash_I(20, 3);
  gcnumlobjs = 0;
  gcheaptop = 0;
  gctopcore = 0;
  gctopblock = 0;
  gcmovestartaddr = 0;
  gctomove = false;
  gcmovepending = 0;
  gcblock2fill = 0;
#ifdef SMEMM
  gcmem_mixed_threshold = (unsigned int)((BAMBOO_SHARED_MEM_SIZE
		-bamboo_reserved_smem*BAMBOO_SMEM_SIZE)*0.8);
  gcmem_mixed_usedmem = 0;
#endif
#ifdef MGC_SPEC
  gc_profile_flag = false;
#endif
#ifdef GC_FLUSH_DTLB
  gc_num_flush_dtlb = 0;
#endif
  gc_localheap_s = false;
#ifdef GC_CACHE_ADAPT
  gccachestage = false;
#endif 

  INIT_MULTICORE_GCPROFILE_DATA();
}

INLINE void dismulticoregcdata() {
  freeMGCHash(gcforwardobjtbl);
}

INLINE void initGC() {
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
    } 
    for(i = NUMCORES4GC; i < NUMCORESACTIVE; ++i) {
      gccorestatus[i] = 1;
      gcnumsendobjs[0][i] = gcnumsendobjs[1][i] = 0;
      gcnumreceiveobjs[0][i] = gcnumreceiveobjs[1][i] = 0;
    }
    gcheaptop = 0;
    gctopcore = 0;
    gctopblock = 0;
  gcnumsrobjs_index = 0;
  } 
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
    gctailindex = gctailindex2 = gcheadindex = 0;
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

  GCPROFILE_INIT();
} 

INLINE bool gc_checkAllCoreStatus_I() {
  int i = 0;
  for(i = 0; i < NUMCORESACTIVE; ++i) {
    if(gccorestatus[i] != 0) {
      break;
    }  
  }  
  return (i == NUMCORESACTIVE);
}

INLINE void checkMarkStatue() {
  int i;
  if((!waitconfirm) ||
      (waitconfirm && (numconfirm == 0))) {
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
    if(allStall) {
      // ask for confirm
      if(!waitconfirm) {
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
        }
      } else {
        // Phase 2
        // check if the sum of send objs and receive obj are the same
        // yes->check if the info is the latest; no->go on executing
        unsigned int sumsendobj = 0;
        for(i = 0; i < NUMCORESACTIVE; ++i) {
          sumsendobj += gcnumsendobjs[gcnumsrobjs_index][i];
        } 
        for(i = 0; i < NUMCORESACTIVE; ++i) {
          sumsendobj -= gcnumreceiveobjs[gcnumsrobjs_index][i];
        } 
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
          }  
          if(!ischanged) {    
            // all the core status info are the latest,stop mark phase
            gcphase = COMPACTPHASE;
            // restore the gcstatus for all cores
            for(i = 0; i < NUMCORESACTIVE; ++i) {
              gccorestatus[i] = 1;
            }  
          } else {
            // There were changes between phase 1 and phase 2, can not decide 
            // whether the mark phase has been finished
            waitconfirm = false;
            // As it fails in phase 2, flip the entries
            gcnumsrobjs_index = (gcnumsrobjs_index == 0) ? 1 : 0;
          } 
        } else {
          // There were changes between phase 1 and phase 2, can not decide 
          // whether the mark phase has been finished
          waitconfirm = false;
          // As it fails in phase 2, flip the entries
          gcnumsrobjs_index = (gcnumsrobjs_index == 0) ? 1 : 0;
        } 
        BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
      }
    } else {
      BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
    } 
  } 
} 

// compute load balance for all cores
INLINE int loadbalance(unsigned int * heaptop) {
  // compute load balance
  int i;

  // get the total loads
  unsigned int tloads = gcloads[STARTUPCORE];
  for(i = 1; i < NUMCORES4GC; i++) {
    tloads += gcloads[i];
  }
  *heaptop = gcbaseva + tloads;

  unsigned int b = 0;
  BLOCKINDEX(*heaptop, &b);
  // num of blocks per core
  unsigned int numbpc = (unsigned int)b/(unsigned int)(NUMCORES4GC);
  gctopblock = b;
  RESIDECORE(heaptop, &gctopcore);
  return numbpc;
}

// compute total mem size required and sort the lobjs in ascending order
INLINE unsigned int sortLObjs() {
  unsigned int tmp_lobj = 0;
  unsigned int tmp_len = 0;
  unsigned int tmp_host = 0;
  unsigned int sumsize = 0;

  gclobjtail2 = gclobjtail;
  gclobjtailindex2 = gclobjtailindex;
  // TODO USE QUICK SORT INSTEAD?
  while(gc_lobjmoreItems2_I()) {
    gc_lobjdequeue2_I();
    tmp_lobj = gclobjtail2->lobjs[gclobjtailindex2-1];
    tmp_host = gclobjtail2->hosts[gclobjtailindex2-1];
    tmp_len = gclobjtail2->lengths[gclobjtailindex2 - 1];
    sumsize += tmp_len;
    GCPROFILE_RECORD_LOBJ();
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
    }  
      } 
    }  
    // insert it
    if(i != gclobjtailindex2 - 1) {
      tmp_block->lobjs[i] = tmp_lobj;
      tmp_block->lengths[i] = tmp_len;
      tmp_block->hosts[i] = tmp_host;
    }
  }
  return sumsize;
}

INLINE bool cacheLObjs() {
  // check the total mem size need for large objs
  unsigned long long sumsize = 0;
  unsigned int size = 0;
  
  sumsize = sortLObjs();

  GCPROFILE_RECORD_LOBJSPACE();

  // check if there are enough space to cache these large objs
  unsigned int dst = gcbaseva + (BAMBOO_SHARED_MEM_SIZE) -sumsize;
  if((unsigned long long)gcheaptop > (unsigned long long)dst) {
    // do not have enough room to cache large objs
    return false;
  }

  gcheaptop = dst; // Note: record the start of cached lobjs with gcheaptop
  // cache the largeObjs to the top of the shared heap
  dst = gcbaseva + (BAMBOO_SHARED_MEM_SIZE);
  while(gc_lobjmoreItems3_I()) {
    gc_lobjdequeue3_I();
    size = gclobjtail2->lengths[gclobjtailindex2];
    // set the mark field to , indicating that this obj has been moved
    // and need to be flushed
    ((struct ___Object___ *)(gclobjtail2->lobjs[gclobjtailindex2]))->marked = 
      COMPACTED;
    dst -= size;
    if((unsigned int)dst < 
        (unsigned int)(gclobjtail2->lobjs[gclobjtailindex2]+size)) {
      memmove(dst, gclobjtail2->lobjs[gclobjtailindex2], size);
    } else {
      memcpy(dst, gclobjtail2->lobjs[gclobjtailindex2], size);
    }
  }
  return true;
} 

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
}

INLINE unsigned int checkCurrHeapTop() {
  // update the smemtbl
  BAMBOO_MEMSET_WH(bamboo_smemtbl, 0, sizeof(int)*gcnumblock);
  // flush all gcloads to indicate the real heap top on one core
  // previous it represents the next available ptr on a core
  if(((unsigned int)gcloads[0] > (unsigned int)(gcbaseva+BAMBOO_SMEM_SIZE_L))
     && (((unsigned int)gcloads[0]%(BAMBOO_SMEM_SIZE)) == 0)) {
    // edge of a block, check if this is exactly the heaptop
    BASEPTR(0, gcfilledblocks[0]-1, &(gcloads[0]));
    gcloads[0]+=(gcfilledblocks[0]>1?(BAMBOO_SMEM_SIZE):(BAMBOO_SMEM_SIZE_L));
  }
  updateSmemTbl(0, gcloads[0]);
  for(int i = 1; i < NUMCORES4GC; i++) {
    unsigned int tmptop = 0;
    if((gcfilledblocks[i] > 0)
       && (((unsigned int)gcloads[i] % (BAMBOO_SMEM_SIZE)) == 0)) {
      // edge of a block, check if this is exactly the heaptop
      BASEPTR(i, gcfilledblocks[i]-1, &gcloads[i]);
      gcloads[i] +=
        (gcfilledblocks[i]>1?(BAMBOO_SMEM_SIZE):(BAMBOO_SMEM_SIZE_L));
      tmptop = gcloads[i];
    }
    updateSmemTbl(i, gcloads[i]);
  } 

  // find current heap top
  // TODO
  // a bug here: when using local allocation, directly move large objects
  // to the highest free chunk might not be memory efficient
  unsigned int tmpheaptop = 0;
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
  return tmpheaptop;
}

INLINE void moveLObjs() {
#ifdef SMEMM
  // update the gcmem_mixed_usedmem
  gcmem_mixed_usedmem = 0;
#endif
  unsigned int size = 0;
  unsigned int bound = 0;
  unsigned int tmpheaptop = checkCurrHeapTop();

  // move large objs from gcheaptop to tmpheaptop
  // write the header first
  unsigned int tomove = gcbaseva+(BAMBOO_SHARED_MEM_SIZE)-gcheaptop;
#ifdef SMEMM
  gcmem_mixed_usedmem += tomove;
#endif
  // flush the sbstartbl
  BAMBOO_MEMSET_WH(&(gcsbstarttbl[gcreservedsb]), '\0',
    (BAMBOO_SHARED_MEM_SIZE/BAMBOO_SMEM_SIZE-(unsigned int)gcreservedsb)
    *sizeof(unsigned int));
  if(tomove == 0) {
    gcheaptop = tmpheaptop;
  } else {
    // check how many blocks it acrosses
    unsigned int remain = tmpheaptop-gcbaseva;
    //number of the sblock
    unsigned int sb = remain/BAMBOO_SMEM_SIZE+(unsigned int)gcreservedsb;
    unsigned int b = 0;  // number of the block
    BLOCKINDEX(tmpheaptop, &b);
    // check the remaining space in this block
    bound = (BAMBOO_SMEM_SIZE);
    if(remain < (BAMBOO_LARGE_SMEM_BOUND)) {
      bound = (BAMBOO_SMEM_SIZE_L);
    }
    remain = bound - remain%bound;

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
      if(remain >= isize) {
    remain -= isize;
    // move the large obj
    if((unsigned int)gcheaptop < (unsigned int)(tmpheaptop+size)) {
      memmove(tmpheaptop, gcheaptop, size);
    } else {
      memcpy(tmpheaptop, gcheaptop, size);
    }
    // fill the remaining space with -2 padding
    BAMBOO_MEMSET_WH(tmpheaptop+size, -2, isize-size);

    gcheaptop += size;
    cpysize += isize;
    // cache the mapping info
    gcmappingtbl[OBJMAPPINGINDEX((unsigned int)ptr)]=(unsigned int)tmpheaptop;
    tmpheaptop += isize;

    // update bamboo_smemtbl
    bamboo_smemtbl[b] += isize;
      } else {
    // this object acrosses blocks
    if(cpysize > 0) {
      CLOSEBLOCK(base, cpysize+BAMBOO_CACHE_LINE_SIZE);
      bamboo_smemtbl[b] += BAMBOO_CACHE_LINE_SIZE;
      cpysize = 0;
      base = tmpheaptop;
      if(remain == 0) {
        remain = ((tmpheaptop-gcbaseva)<(BAMBOO_LARGE_SMEM_BOUND)) ?
          BAMBOO_SMEM_SIZE_L : BAMBOO_SMEM_SIZE;
      }
      remain -= BAMBOO_CACHE_LINE_SIZE;
      tmpheaptop += BAMBOO_CACHE_LINE_SIZE;
      BLOCKINDEX(tmpheaptop, &b);
      sb = (unsigned int)(tmpheaptop-gcbaseva)/(BAMBOO_SMEM_SIZE)+gcreservedsb;
    } 

    // move the large obj
    if((unsigned int)gcheaptop < (unsigned int)(tmpheaptop+size)) {
      memmove(tmpheaptop, gcheaptop, size);
    } else {
      memcpy(tmpheaptop, gcheaptop, size);
    }
    // fill the remaining space with -2 padding
    BAMBOO_MEMSET_WH(tmpheaptop+size, -2, isize-size);
    gcheaptop += size;
    // cache the mapping info 
    gcmappingtbl[OBJMAPPINGINDEX((unsigned int)ptr)]=(unsigned int)tmpheaptop;
    tmpheaptop += isize;

    // set the gcsbstarttbl and bamboo_smemtbl
    unsigned int tmpsbs=1+(unsigned int)(isize-remain-1)/BAMBOO_SMEM_SIZE;
    for(int k = 1; k < tmpsbs; k++) {
      gcsbstarttbl[sb+k] = -1;
    }
    sb += tmpsbs;
    bound = (b<NUMCORES4GC) ? BAMBOO_SMEM_SIZE_L : BAMBOO_SMEM_SIZE;
    BLOCKINDEX(tmpheaptop-1, &tmpsbs);
    for(; b < tmpsbs; b++) {
      bamboo_smemtbl[b] = bound;
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
    } 

    CLOSEBLOCK(base, isize+BAMBOO_CACHE_LINE_SIZE);
    cpysize = 0;
    base = tmpheaptop;
    if(remain == BAMBOO_CACHE_LINE_SIZE) {
      // fill with 0 in case
      BAMBOO_MEMSET_WH(tmpheaptop, '\0', remain);
    }
    remain -= BAMBOO_CACHE_LINE_SIZE;
    tmpheaptop += BAMBOO_CACHE_LINE_SIZE;
      } 
    }

    if(cpysize > 0) {
      CLOSEBLOCK(base, cpysize+BAMBOO_CACHE_LINE_SIZE);
      bamboo_smemtbl[b] += BAMBOO_CACHE_LINE_SIZE;
    } else {
      tmpheaptop -= BAMBOO_CACHE_LINE_SIZE;
    }
    gcheaptop = tmpheaptop;
  } 

  bamboo_free_block = 0;
  unsigned int tbound = 0;
  do {
    tbound=(bamboo_free_block<NUMCORES4GC)?BAMBOO_SMEM_SIZE_L:BAMBOO_SMEM_SIZE;
    if(bamboo_smemtbl[bamboo_free_block] == tbound) {
      bamboo_free_block++;
    } else {
      // the first non-full partition
      break;
    }
  } while(true);

  GCPROFILE_RECORD_SPACE();
} 

INLINE void gc_collect(struct garbagelist * stackptr) {
  gcprocessing = true;
  tprintf("gc \n");
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
  GC_PRINTF("Do initGC\n");
  initGC();
  CACHEADAPT_GC(true);
  //send init finish msg to core coordinator
  send_msg_2(STARTUPCORE, GCFINISHINIT, BAMBOO_NUM_OF_CORE, false);

  while(true) {
    if(MARKPHASE == gcphase) {
      break;
    }
  }
  GC_PRINTF("Start mark phase\n");
  mark(true, stackptr);
  GC_PRINTF("Finish mark phase, start compact phase\n");
  compact();
  GC_PRINTF("Finish compact phase\n");

  while(true) {
    if(FLUSHPHASE == gcphase) {
      break;
    }
  }
  GC_PRINTF("Start flush phase\n");
  GCPROFILE_INFO_2_MASTER();
  flush(stackptr);
  GC_PRINTF("Finish flush phase\n");

  CACHEADAPT_PHASE_CLIENT();

  // invalidate all shared mem pointers
  bamboo_cur_msp = NULL;
  bamboo_smem_size = 0;
  bamboo_smem_zero_top = NULL;

  gcflag = false;
  while(true) {
    if(FINISHPHASE == gcphase) {
      break;
    }
  }

  GC_PRINTF("Finish gc! \n");
} 

INLINE void gc_nocollect(struct garbagelist * stackptr) {
  gcprocessing = true;
  tprintf("gc \n");
  // inform the master that this core is at a gc safe point and is ready to 
  // do gc
  send_msg_4(STARTUPCORE, GCFINISHPRE, BAMBOO_NUM_OF_CORE, self_numsendobjs, 
    self_numreceiveobjs, false);
  
  while(true) {
    if(INITPHASE == gcphase) {
      break;
    }
  }
  GC_PRINTF("Do initGC\n");
  initGC();
  CACHEADAPT_GC(true);
  //send init finish msg to core coordinator
  send_msg_2(STARTUPCORE, GCFINISHINIT, BAMBOO_NUM_OF_CORE, false);

  while(true) {
    if(MARKPHASE == gcphase) {
      break;
    }
  }
  GC_PRINTF("Start mark phase\n"); 
  mark(true, stackptr);
  GC_PRINTF("Finish mark phase, wait for flush\n");

  // non-gc core collector routine
  while(true) {
    if(FLUSHPHASE == gcphase) {
      break;
    }
  }
  GC_PRINTF("Start flush phase\n");
  GCPROFILE_INFO_2_MASTER();
  flush(stackptr);
  GC_PRINTF("Finish flush phase\n"); 

  CACHEADAPT_PHASE_CLIENT();

  // invalidate all shared mem pointers
  bamboo_cur_msp = NULL;
  bamboo_smem_size = 0;
  bamboo_smem_zero_top = NULL;

  gcflag = false;
  while(true) {
    if(FINISHPHASE == gcphase) {
      break;
    }
  }
  GC_PRINTF("Finish gc! \n");
}

INLINE void gc_master(struct garbagelist * stackptr) {
  gcprocessing = true;
  tprintf("start GC !!!!!!!!!!!!! \n");

  gcphase = INITPHASE;
  int i = 0;
  waitconfirm = false;
  numconfirm = 0;
  initGC();
  GC_SEND_MSG_1_TO_CLIENT(GCSTARTINIT);
  CACHEADAPT_GC(true);
  GC_PRINTF("Check core status \n");
  GC_CHECK_ALL_CORE_STATUS(true);
  GCPROFILE_ITEM();
  CACHEADAPT_OUTPUT_CACHE_SAMPLING();

  GC_PRINTF("(%x,%x) Start mark phase \n");
  GC_SEND_MSG_1_TO_CLIENT(GCSTART);
  gcphase = MARKPHASE;
  // mark phase
  bool isfirst = true;
  while(MARKPHASE == gcphase) {
    mark(isfirst, stackptr);
    if(isfirst) {
      isfirst = false;
    }

    // check gcstatus
    checkMarkStatue();
  }

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
  GCPROFILE_ITEM();
  GC_PRINTF("prepare to cache large objs \n");
  // cache all large objs
  if(!cacheLObjs()) {
    // no enough space to cache large objs
    BAMBOO_EXIT(0xb02e);
  }
  // predict number of blocks to fill for each core
  unsigned int tmpheaptop = 0;
  int numpbc = loadbalance(&tmpheaptop);
  // TODO
  numpbc = (BAMBOO_SHARED_MEM_SIZE)/(BAMBOO_SMEM_SIZE);
  GC_PRINTF("mark phase finished \n");

  //int tmptopptr = 0;
  //BASEPTR(gctopcore, 0, &tmptopptr);
  // TODO
  //tmptopptr = gcbaseva + (BAMBOO_SHARED_MEM_SIZE);
  tmpheaptop = gcbaseva + (BAMBOO_SHARED_MEM_SIZE);
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
      }
    } else {
      gcstopblock[i] = numpbc;
      if(i != STARTUPCORE) {
        send_msg_2(i, GCSTARTCOMPACT, numpbc, false);
      } else {
        gcblock2fill = numpbc;
      }
    }
  }
  BAMBOO_CACHE_MF();
  GCPROFILE_ITEM();
  // compact phase
  struct moveHelper * orig =
    (struct moveHelper *)RUNMALLOC(sizeof(struct moveHelper));
  struct moveHelper * to =
    (struct moveHelper *)RUNMALLOC(sizeof(struct moveHelper));
  compact_master(orig, to); 
  GCPROFILE_ITEM();
  GC_PRINTF("prepare to move large objs \n");
  // move largeObjs
  moveLObjs();
  GC_PRINTF("compact phase finished \n");
  RUNFREE(orig);
  RUNFREE(to);
  orig = to = NULL;

  gcphase = FLUSHPHASE;
  GC_SEND_MSG_1_TO_CLIENT(GCSTARTFLUSH);
  GCPROFILE_ITEM();
  GC_PRINTF("Start flush phase \n");
  // flush phase
  flush(stackptr);
  // now the master core need to decide the new cache strategy
  CACHEADAPT_MASTER();
  GC_CHECK_ALL_CORE_STATUS(FLUSHPHASE==gcphase);
  GC_PRINTF("Finish flush phase \n");

  CACHEADAPT_PHASE_MASTER();

  gcphase = FINISHPHASE;

  // invalidate all shared mem pointers
  // put it here as it takes time to inform all the other cores to
  // finish gc and it might cause problem when some core resumes
  // mutator earlier than the other cores
  bamboo_cur_msp = NULL;
  bamboo_smem_size = 0;
  bamboo_smem_zero_top = NULL;

  GCPROFILE_END();
  gcflag = false;
  GC_SEND_MSG_1_TO_CLIENT(GCFINISH);

  gcprocessing = false;
  if(gcflag) {
    // inform other cores to stop and wait for gc
    gcprecheck = true;
    for(int i = 0; i < NUMCORESACTIVE; i++) {
      // reuse the gcnumsendobjs & gcnumreceiveobjs
      gcnumsendobjs[0][i] = 0;
      gcnumreceiveobjs[0][i] = 0;
    }
    GC_SEND_MSG_1_TO_CLIENT(GCSTARTPRE);
  }
  GC_PRINTF("gc finished   \n");
  tprintf("finish GC ! %d \n", gcflag);
} 

INLINE void pregccheck_I() {
  while(true) {
    gcnumsendobjs[0][BAMBOO_NUM_OF_CORE] = self_numsendobjs;
    gcnumreceiveobjs[0][BAMBOO_NUM_OF_CORE] = self_numreceiveobjs;
    int sumsendobj = 0;
    int i = 0;
    for(i = 0; i < NUMCORESACTIVE; ++i) {
      sumsendobj += gcnumsendobjs[0][i];
    }  
    for(i = 0; i < NUMCORESACTIVE; ++i) {
      sumsendobj -= gcnumreceiveobjs[0][i];
    } 
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
      BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
    } else {
      return;
    }
  }
}

INLINE void pregcprocessing() {
#ifdef GC_CACHE_ADAPT
#ifdef GC_CACHE_SAMPLING
  // disable the timer interrupt
  bamboo_mask_timer_intr();
#endif 
#endif
  // Zero out the remaining memory here because for the GC_CACHE_ADAPT version,
  // we need to make sure during the gcinit phase the shared heap is not 
  // touched. Otherwise, there would be problem when adapt the cache strategy.
  BAMBOO_CLOSE_CUR_MSP();
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
#endif
#endif
}

INLINE void postgcprocessing() {
#ifdef GC_CACHE_ADAPT
#ifdef GC_CACHE_SAMPLING
  // enable the timer interrupt
  bamboo_tile_timer_set_next_event(GC_TILE_TIMER_EVENT_SETTING); 
  bamboo_unmask_timer_intr();
#endif
#endif
}

INLINE bool gc(struct garbagelist * stackptr) {
  // check if do gc
  if(!gcflag) {
    gcprocessing = false;
    return false;
  }

  // core coordinator routine
  if(0 == BAMBOO_NUM_OF_CORE) {
    GC_PRINTF("Check if we can do gc or not\n");
    gccorestatus[BAMBOO_NUM_OF_CORE] = 0;
    BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
    if(!gc_checkAllCoreStatus_I()) {
      BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
      // some of the cores are still executing the mutator and did not reach
      // some gc safe point, therefore it is not ready to do gc
      gcflag = true;
      return false;
    } else {
      GCPROFILE_START();
      pregccheck_I();
      BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
    }
    GC_PRINTF("start gc! \n");
    pregcprocessing();
    gc_master(stackptr);
  } else if(BAMBOO_NUM_OF_CORE < NUMCORES4GC) {
    pregcprocessing();
    gc_collect(stackptr);
  } else {
    pregcprocessing();
    gc_nocollect(stackptr);
  }
  postgcprocessing();

  return true;
} 

#endif
