// TODO: DO NOT support tag!!!
#ifdef MULTICORE_GC
#include "runtime.h"
#include "multicoreruntime.h"
#include "multicoregarbage.h"
#include "multicoregcmark.h"
#include "gcqueue.h"
#include "multicoregccompact.h"
#include "multicoregcflush.h"
#include "multicoregcprofile.h"
#include "gcqueue.h"

#ifdef SMEMM
extern unsigned int gcmem_mixed_threshold;
extern unsigned int gcmem_mixed_usedmem;
#endif // SMEMM

volatile bool gcflag;
gc_status_t gc_status_info;

unsigned long long gc_output_cache_policy_time=0;

#ifdef GC_DEBUG
// dump whole mem in blocks
void dumpSMem() {
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
  sblock = 0;
  bool advanceblock = false;
  // remaining memory
  for(i=gcbaseva; (unsigned int)i<(unsigned int)(gcbaseva+BAMBOO_SHARED_MEM_SIZE); i+=4*16) {
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
          udn_tile_coord_x(), udn_tile_coord_y(),block, sblock++, x, y,
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

void initmulticoregcdata() {
  if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
    // startup core to initialize corestatus[]
    for(int i = 0; i < NUMCORESACTIVE; i++) {
      gccorestatus[i] = 1;
      gcnumsendobjs[0][i] = gcnumsendobjs[1][i] = 0;
      gcnumreceiveobjs[0][i] = gcnumreceiveobjs[1][i] = 0;
    } 
    for(int i = 0; i < NUMCORES4GC; i++) {
      gcloads[i] = 0;
      gcrequiredmems[i] = 0;
      gcstopblock[i] = 0;
      gcfilledblocks[i] = 0;
    }
  }

  bamboo_smem_zero_top = NULL;
  gcflag = false;
  gc_status_info.gcprocessing = false;
  gc_status_info.gcphase = FINISHPHASE;

  gcprecheck = true;
  gccurr_heaptop = 0;
  gcself_numsendobjs = 0;
  gcself_numreceiveobjs = 0;
  gcmarkedptrbound = 0;
  gcforwardobjtbl = allocateMGCHash_I(128);
  gcheaptop = 0;
  gcmovestartaddr = 0;
  gctomove = false;
  gcmovepending = 0;
  gcblock2fill = 0;
#ifdef SMEMM
  gcmem_mixed_threshold=(unsigned int)((BAMBOO_SHARED_MEM_SIZE-bamboo_reserved_smem*BAMBOO_SMEM_SIZE)*0.8);
  gcmem_mixed_usedmem = 0;
#endif
#ifdef MGC_SPEC
  gc_profile_flag = false;
#endif
  gc_localheap_s = false;
#ifdef GC_CACHE_ADAPT
  gccachestage = false;
#endif 

  INIT_MULTICORE_GCPROFILE_DATA();
}

void dismulticoregcdata() {
  freeMGCHash(gcforwardobjtbl);
}

void initGC() {
  if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
    for(int i = 0; i < NUMCORES4GC; i++) {
      gccorestatus[i] = 1;
      gcnumsendobjs[0][i] = gcnumsendobjs[1][i] = 0;
      gcnumreceiveobjs[0][i] = gcnumreceiveobjs[1][i] = 0;
      gcloads[i] = 0;
      gcrequiredmems[i] = 0;
      gcfilledblocks[i] = 0;
      gcstopblock[i] = 0;
    } 
    for(int i = NUMCORES4GC; i < NUMCORESACTIVE; i++) {
      gccorestatus[i] = 1;
      gcnumsendobjs[0][i] = gcnumsendobjs[1][i] = 0;
      gcnumreceiveobjs[0][i] = gcnumreceiveobjs[1][i] = 0;
    }
    gcheaptop = 0;
    gcnumsrobjs_index = 0;
  } 
  gcself_numsendobjs = 0;
  gcself_numreceiveobjs = 0;
  gcmarkedptrbound = 0;
  gcmovestartaddr = 0;
  gctomove = false;
  gcblock2fill = 0;
  gcmovepending = 0;
  gccurr_heaptop = 0;

  gc_queueinit();

  MGCHashreset(gcforwardobjtbl);

  GCPROFILE_INIT();
  gc_output_cache_policy_time=0;
} 

bool gc_checkAllCoreStatus() {
  BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
  for(int i = 0; i < NUMCORESACTIVE; i++) {
    if(gccorestatus[i] != 0) {
      BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
      return false;
    }  
  }  
  BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
  return true;
}

// NOTE: should be invoked with interrupts turned off
bool gc_checkAllCoreStatus_I() {
  for(int i = 0; i < NUMCORESACTIVE; i++) {
    if(gccorestatus[i] != 0) {
      return false;
    }  
  }  
  return true;
}

void checkMarkStatus_p2() {
  // check if the sum of send objs and receive obj are the same
  // yes->check if the info is the latest; no->go on executing
  unsigned int sumsendobj = 0;
  for(int i = 0; i < NUMCORESACTIVE; i++) {
    sumsendobj += gcnumsendobjs[gcnumsrobjs_index][i];
  } 
  for(int i = 0; i < NUMCORESACTIVE; i++) {
    sumsendobj -= gcnumreceiveobjs[gcnumsrobjs_index][i];
  } 
  if(0 == sumsendobj) {
    // Check if there are changes of the numsendobjs or numreceiveobjs 
    // on each core
    int i = 0;
    for(i = 0; i < NUMCORESACTIVE; i++) {
      if((gcnumsendobjs[0][i]!=gcnumsendobjs[1][i])||(gcnumreceiveobjs[0][i]!=gcnumreceiveobjs[1][i]) ) {
        break;
      }
    }  
    if(i == NUMCORESACTIVE) {    
      // all the core status info are the latest,stop mark phase
      gc_status_info.gcphase = COMPACTPHASE;
      // restore the gcstatus for all cores
      for(int i = 0; i < NUMCORESACTIVE; i++) {
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
}

void checkMarkStatus() {
  if((!waitconfirm)||(waitconfirm && (numconfirm == 0))) {
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
    if (gc_checkAllCoreStatus_I()) {
      // ask for confirm
      if(!waitconfirm) {
        // the first time found all cores stall
        // send out status confirm msg to all other cores
        // reset the corestatus array too    
        waitconfirm = true;
        numconfirm = NUMCORESACTIVE - 1;
        BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
        GC_SEND_MSG_1_TO_CLIENT(GCMARKCONFIRM);
      } else {
        // Phase 2
        checkMarkStatus_p2(); 
        BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
      }
    } else {
      BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
    } 
  } 
} 

// compute load balance for all cores
int loadbalance(void ** heaptop) {
  // compute load balance
  // get the total loads
  unsigned int tloads = 0;
  for(int i = 0; i < NUMCORES4GC; i++) {
    tloads += gcloads[i];
  }
  *heaptop = gcbaseva + tloads;

  unsigned int topblockindex;
  
  BLOCKINDEX(topblockindex, *heaptop);
  // num of blocks per core
  unsigned int numbpc = (topblockindex+NUMCORES4GC-1)/NUMCORES4GC;
  
  return numbpc;
}


// update the bmmboo_smemtbl to record current shared mem usage
void updateSmemTbl(unsigned int coren, void * localtop) {
  unsigned int ltopcore = 0;
  unsigned int bound = BAMBOO_SMEM_SIZE_L;
  BLOCKINDEX(ltopcore, localtop);
  if((unsigned int)localtop>=(unsigned int)(gcbaseva+BAMBOO_LARGE_SMEM_BOUND)){
    bound = BAMBOO_SMEM_SIZE;
  }
  unsigned int load = (unsigned INTPTR)(localtop-gcbaseva)%(unsigned int)bound;
  unsigned int toset = 0;
  for(int j=0; 1; j++) {
    for(int i=0; i<2; i++) {
      toset = gc_core2block[2*coren+i]+(unsigned int)(NUMCORES4GC*2)*j;
      if(toset < ltopcore) {
        bamboo_smemtbl[toset]=BLOCKSIZE(toset<NUMCORES4GC);
#ifdef SMEMM
        gcmem_mixed_usedmem += bamboo_smemtbl[toset];
#endif
      } else if(toset == ltopcore) {
        bamboo_smemtbl[toset] = load;
#ifdef SMEMM
        gcmem_mixed_usedmem += bamboo_smemtbl[toset];
#endif
        return;
      } else {
        return;
      }
    }
  }
}

void gc_collect(struct garbagelist * stackptr) {
  gc_status_info.gcprocessing = true;
  // inform the master that this core is at a gc safe point and is ready to 
  // do gc
  send_msg_4(STARTUPCORE,GCFINISHPRE,BAMBOO_NUM_OF_CORE,self_numsendobjs,self_numreceiveobjs);

  // core collector routine
  //wait for init phase
  WAITFORGCPHASE(INITPHASE);

  GC_PRINTF("Do initGC\n");
  initGC();
  CACHEADAPT_GC(true);
  //send init finish msg to core coordinator
  send_msg_2(STARTUPCORE,GCFINISHINIT,BAMBOO_NUM_OF_CORE);

  //wait for mark phase
  WAITFORGCPHASE(MARKPHASE);

  GC_PRINTF("Start mark phase\n");
  mark(true, stackptr);
  GC_PRINTF("Finish mark phase, start compact phase\n");
  compact();
  GC_PRINTF("Finish compact phase\n");

  WAITFORGCPHASE(UPDATEPHASE);

  GC_PRINTF("Start flush phase\n");
  GCPROFILE_INFO_2_MASTER();
  update(stackptr);
  GC_PRINTF("Finish flush phase\n");

  CACHEADAPT_PHASE_CLIENT();

  // invalidate all shared mem pointers
  bamboo_cur_msp = NULL;
  bamboo_smem_size = 0;
  bamboo_smem_zero_top = NULL;
  gcflag = false;

  WAITFORGCPHASE(FINISHPHASE);

  GC_PRINTF("Finish gc! \n");
} 

void gc_nocollect(struct garbagelist * stackptr) {
  gc_status_info.gcprocessing = true;
  // inform the master that this core is at a gc safe point and is ready to 
  // do gc
  send_msg_4(STARTUPCORE,GCFINISHPRE,BAMBOO_NUM_OF_CORE,self_numsendobjs,self_numreceiveobjs);
  
  WAITFORGCPHASE(INITPHASE);

  GC_PRINTF("Do initGC\n");
  initGC();
  CACHEADAPT_GC(true);
  //send init finish msg to core coordinator
  send_msg_2(STARTUPCORE,GCFINISHINIT,BAMBOO_NUM_OF_CORE);

  WAITFORGCPHASE(MARKPHASE);

  GC_PRINTF("Start mark phase\n"); 
  mark(true, stackptr);
  GC_PRINTF("Finish mark phase, wait for flush\n");

  // non-gc core collector routine
  WAITFORGCPHASE(UPDATEPHASE);

  GC_PRINTF("Start flush phase\n");
  GCPROFILE_INFO_2_MASTER();
  update(stackptr);
  GC_PRINTF("Finish flush phase\n"); 

  CACHEADAPT_PHASE_CLIENT();

  // invalidate all shared mem pointers
  bamboo_cur_msp = NULL;
  bamboo_smem_size = 0;
  bamboo_smem_zero_top = NULL;

  gcflag = false;
  WAITFORGCPHASE(FINISHPHASE);

  GC_PRINTF("Finish gc! \n");
}

void master_mark(struct garbagelist *stackptr) {
  bool isfirst = true;

  GC_PRINTF("Start mark phase \n");
  GC_SEND_MSG_1_TO_CLIENT(GCSTART);
  gc_status_info.gcphase = MARKPHASE;
  // mark phase

  while(MARKPHASE == gc_status_info.gcphase) {
    mark(isfirst, stackptr);
    isfirst=false;
    // check gcstatus
    checkMarkStatus();
  }
}

void master_getlargeobjs() {
  // send msgs to all cores requiring large objs info
  // Note: only need to ask gc cores, non-gc cores do not host any objs
  numconfirm = NUMCORES4GC - 1;
  for(int i = 1; i < NUMCORES4GC; i++) {
    send_msg_1(i,GCLOBJREQUEST);
  }
  gcloads[BAMBOO_NUM_OF_CORE] = gccurr_heaptop;
  //spin until we have all responses
  while(numconfirm!=0) ;

  // check the heaptop
  if(gcheaptop < gcmarkedptrbound) {
    gcheaptop = gcmarkedptrbound;
  }
  GCPROFILE_ITEM();
  GC_PRINTF("prepare to cache large objs \n");

}


void master_updaterefs(struct garbagelist * stackptr) {
  gc_status_info.gcphase = UPDATEPHASE;
  GC_SEND_MSG_1_TO_CLIENT(GCSTARTUPDATE);
  GCPROFILE_ITEM();
  GC_PRINTF("Start flush phase \n");
  // flush phase
  update(stackptr);
  GC_CHECK_ALL_CORE_STATUS(UPDATEPHASE==gc_status_info.gcphase);
  GC_PRINTF("Finish flush phase \n");
}

void master_finish() {
  gc_status_info.gcphase = FINISHPHASE;
  
  // invalidate all shared mem pointers
  // put it here as it takes time to inform all the other cores to
  // finish gc and it might cause problem when some core resumes
  // mutator earlier than the other cores
  bamboo_cur_msp = NULL;
  bamboo_smem_size = 0;
  bamboo_smem_zero_top = NULL;
  
  GCPROFILE_END();
  unsigned long long tmpt = BAMBOO_GET_EXE_TIME();
  CACHEADAPT_OUTPUT_CACHE_POLICY();
  gc_output_cache_policy_time += (BAMBOO_GET_EXE_TIME()-tmpt);
  gcflag = false;
  GC_SEND_MSG_1_TO_CLIENT(GCFINISH);
  
  gc_status_info.gcprocessing = false;
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
}

void gc_master(struct garbagelist * stackptr) {
  tprintf("start GC !!!!!!!!!!!!! \n");
  gc_status_info.gcprocessing = true;
  gc_status_info.gcphase = INITPHASE;

  waitconfirm = false;
  numconfirm = 0;
  initGC();
  GC_SEND_MSG_1_TO_CLIENT(GCSTARTINIT);
  CACHEADAPT_GC(true);
  GC_PRINTF("Check core status \n");
  GC_CHECK_ALL_CORE_STATUS(true);
  GCPROFILE_ITEM();
  unsigned long long tmpt = BAMBOO_GET_EXE_TIME();
  CACHEADAPT_OUTPUT_CACHE_SAMPLING();
  gc_output_cache_policy_time += (BAMBOO_GET_EXE_TIME()-tmpt);

  // do mark phase
  master_mark(stackptr);

  // get large objects from all cores
  master_getlargeobjs();

  // compact the heap
  master_compact();
  
  // update the references
  master_updaterefs(stackptr);

  // do cache adaptation
  CACHEADAPT_PHASE_MASTER();

  // do finish up stuff
  master_finish();

  GC_PRINTF("gc finished   \n");
  tprintf("finish GC ! %d \n",gcflag);
} 

void pregccheck() {
  while(true) {
    BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
    gcnumsendobjs[0][BAMBOO_NUM_OF_CORE] = self_numsendobjs;
    gcnumreceiveobjs[0][BAMBOO_NUM_OF_CORE] = self_numreceiveobjs;
    int sumsendobj = 0;
    for(int i = 0; i < NUMCORESACTIVE; i++) {
      sumsendobj += gcnumsendobjs[0][i];
    }  
    for(int i = 0; i < NUMCORESACTIVE; i++) {
      sumsendobj -= gcnumreceiveobjs[0][i];
    } 
    if(0 != sumsendobj) {
      // there were still some msgs on the fly, wait until there 
      // are some update pregc information coming and check it again
      gcprecheck = false;
      BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();

      while(!gcprecheck) ;
    } else {
      BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
      return;
    }
  }
}

void pregcprocessing() {
#if defined(GC_CACHE_ADAPT)&&defined(GC_CACHE_SAMPLING)
  // disable the timer interrupt
  bamboo_mask_timer_intr();
#endif
  // Zero out the remaining memory here because for the GC_CACHE_ADAPT version,
  // we need to make sure during the gcinit phase the shared heap is not 
  // touched. Otherwise, there would be problem when adapt the cache strategy.
  BAMBOO_CLOSE_CUR_MSP();
#if defined(GC_CACHE_ADAPT)&&defined(GC_CACHE_SAMPLING)
  // get the sampling data 
  bamboo_output_dtlb_sampling();
#endif
}

void postgcprocessing() {
#if defined(GC_CACHE_ADAPT)&&defined(GC_CACHE_SAMPLING)
  // enable the timer interrupt
  bamboo_tile_timer_set_next_event(GC_TILE_TIMER_EVENT_SETTING); 
  bamboo_unmask_timer_intr();
#endif
}

bool gc(struct garbagelist * stackptr) {
  // check if do gc
  if(!gcflag) {
    gc_status_info.gcprocessing = false;
    return false;
  }

  // core coordinator routine
  if(0 == BAMBOO_NUM_OF_CORE) {
    GC_PRINTF("Check if we can do gc or not\n");
    gccorestatus[BAMBOO_NUM_OF_CORE] = 0;
    if(!gc_checkAllCoreStatus()) {
      // some of the cores are still executing the mutator and did not reach
      // some gc safe point, therefore it is not ready to do gc
      gcflag = true;
      return false;
    } else {
      GCPROFILE_START();
      pregccheck();
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
