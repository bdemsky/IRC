#ifdef MULTICORE_GC
#include "multicoregccompact.h"
#include "runtime_arch.h"
#include "multicoreruntime.h"
#include "multicoregarbage.h"

INLINE bool gc_checkCoreStatus() {
  BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
  for(int i = 0; i < NUMCORES4GC; ++i) {
    if(gccorestatus[i] != 0) {
      BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
      return false;
    }
  }  
  BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
  return true;
}

INLINE void gc_resetCoreStatus() {
  BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
  for(int i = 0; i < NUMCORES4GC; ++i) {
    gccorestatus[i] = 1;
  }
  BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
}

// should be invoked with interrupt closed
INLINE int assignSpareMem_I(unsigned int sourcecore,unsigned int * requiredmem, void ** tomove, void ** startaddr) {
  unsigned int b = 0;
  BLOCKINDEX(topptrs[sourcecore], b);
  void * boundptr = BOUNDPTR(b);
  unsigned INTPTR remain = (unsigned INTPTR) (boundptr - topptrs[sourcecore]);
  unsigned int memneed = requiredmem + BAMBOO_CACHE_LINE_SIZE;
  *startaddr = topptrs[sourcecore];
  *tomove = gcfilledblocks[sourcecore] + 1;
  if(memneed < remain) {
    topptrs[sourcecore] += memneed;
    return 0;
  } else {
    // next available block
    gcfilledblocks[sourcecore] += 1;
    void * newbase = NULL;
    BASEPTR(sourcecore, gcfilledblocks[sourcecore], &newbase);
    topptrs[sourcecore] = newbase;
    return requiredmem-remain;
  }
}

INLINE int assignSpareMem(unsigned int sourcecore,unsigned int * requiredmem,unsigned int * tomove, void ** startaddr) {
  BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
  int retval=assignSpareMem_I(sourcecore, requiredmem, tomove, startaddr);
  BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
  return retval;
}

INLINE void compact2Heaptophelper_I(unsigned int coren, void ** p,unsigned int* numblocks,unsigned int* remain) {
  unsigned int b;
  unsigned int memneed = gcrequiredmems[coren] + BAMBOO_CACHE_LINE_SIZE;
  if(STARTUPCORE == coren) {
    gctomove = true;
    gcmovestartaddr = *p;
    gcdstcore = gctopcore;
    gcblock2fill = *numblocks + 1;
  } else {
    if(BAMBOO_CHECK_SEND_MODE()) {
      cache_msg_4_I(coren,GCMOVESTART,gctopcore,*p,(*numblocks)+1);
    } else {
      send_msg_4_I(coren,GCMOVESTART,gctopcore,*p,(*numblocks)+1);
    }
  }
  if(memneed < *remain) {
    *p = *p + memneed;
    gcrequiredmems[coren] = 0;
    topptrs[gctopcore] += memneed;
    *remain = *remain - memneed;
  } else {
    // next available block
    *p = *p + *remain;
    gcfilledblocks[gctopcore] += 1;
    void * newbase = NULL;
    BASEPTR(gctopcore, gcfilledblocks[gctopcore], &newbase);
    topptrs[gctopcore] = newbase;
    gcrequiredmems[coren] -= *remain - BAMBOO_CACHE_LINE_SIZE;
    gcstopblock[gctopcore]++;
    gctopcore = NEXTTOPCORE(gctopblock);
    gctopblock++;
    *numblocks = gcstopblock[gctopcore];
    *p = topptrs[gctopcore];
    BLOCKINDEX(*p, b);
    *remain=GC_BLOCK_REMAIN_SIZE(b, (*p));
  }  
  gcmovepending--;
} 

INLINE void compact2Heaptop() {
  // no cores with spare mem and some cores are blocked with pending move
  // find the current heap top and make them move to the heap top
  void * p = topptrs[gctopcore];
  unsigned int numblocks = gcfilledblocks[gctopcore];
  unsigned int b;
  BLOCKINDEX(p, b);
  unsigned int remain=GC_BLOCK_REMAIN_SIZE(b, p);
  // check if the top core finishes
  BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
  if(gccorestatus[gctopcore] != 0) {
    // let the top core finishes its own work first
    compact2Heaptophelper_I(gctopcore, &p, &numblocks, &remain);
    BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
    return;
  }
  BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();

  for(int i = 0; i < NUMCORES4GC; i++) {
    BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
    if((gccorestatus[i] != 0) && (gcrequiredmems[i] > 0)) {
      compact2Heaptophelper_I(i, &p, &numblocks, &remain);
      if(gccorestatus[gctopcore] != 0) {
        BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
        // the top core is not free now
        return;
      }
    }  
    BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
  } 
}

INLINE void resolvePendingMoveRequest() {
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
        }  
      }
      i++;
    }  
    if(!haspending) {
      if(gccorestatus[j] != 0) {
        // not finished, check if it has pending move requests
        if((gcfilledblocks[j]==gcstopblock[j])&&(gcrequiredmems[j]>0)) {
          dstcore = j;
          haspending = true;
        } else {
          hasrunning = true;
        } 
      } 
      j++;
    }  
    if(!nosparemem && haspending) {
      // find match
      unsigned int tomove = 0;
      unsigned int startaddr = 0;
      gcrequiredmems[dstcore] = assignSpareMem(sourcecore,gcrequiredmems[dstcore],&tomove,&startaddr);
      if(STARTUPCORE == dstcore) {
        gcdstcore = sourcecore;
        gctomove = true;
        gcmovestartaddr = startaddr;
        gcblock2fill = tomove;
      } else {
        send_msg_4(dstcore,GCMOVESTART,sourcecore,startaddr,tomove);
      }
      gcmovepending--;
      nosparemem = true;
      haspending = false;
      noblock = true;
    }
  }  
  
  if(!hasrunning && !noblock) {
    gc_status_info.gcphase = SUBTLECOMPACTPHASE;
    compact2Heaptop();
  }
} 

// If out of boundary of valid shared memory, return false, else return true
INLINE bool nextSBlock(struct moveHelper * orig) {
  orig->blockbase = orig->blockbound;
  
  bool sbchanged = false;
  unsigned int origptr = orig->ptr;
  unsigned int blockbase = orig->blockbase;
  unsigned int blockbound = orig->blockbound;
  unsigned int bound = orig->bound;
outernextSBlock:
  // check if across a big block
  // TODO now do not zero out the whole memory, maybe the last two conditions
  // are useless now
  if((blockbase>=bound)||(origptr>=bound)||((origptr!=NULL)&&(*((int*)origptr))==0)||((*((int*)blockbase))==0)) {
  innernextSBlock:
    // end of current heap block, jump to next one
    orig->numblocks++;
    BASEPTR(BAMBOO_NUM_OF_CORE, orig->numblocks, &(orig->base));
    if(orig->base >= gcbaseva + BAMBOO_SHARED_MEM_SIZE) {
      // out of boundary
      orig->ptr = orig->base; // set current ptr to out of boundary too
      return false;
    }
    orig->blockbase = orig->base;
    orig->sblockindex=(unsigned int)(orig->blockbase-gcbaseva)/BAMBOO_SMEM_SIZE;
    sbchanged = true;
    unsigned int blocknum = 0;
    BLOCKINDEX(orig->base, blocknum);
    if(bamboo_smemtbl[blocknum] == 0) {
      // goto next block
      goto innernextSBlock;
    }
    // check the bamboo_smemtbl to decide the real bound
    orig->bound = orig->base + bamboo_smemtbl[blocknum];
  } else if(0 == ((unsigned INTPTR)orig->blockbase)%BAMBOO_SMEM_SIZE) {
    orig->sblockindex += 1;
    sbchanged = true;
  }  

  // check if this sblock should be skipped or have special start point
  int sbstart = gcsbstarttbl[orig->sblockindex];
  if(sbstart == -1) {
    // goto next sblock
    orig->sblockindex += 1;
    orig->blockbase += BAMBOO_SMEM_SIZE;
    goto outernextSBlock;
  } else if((sbstart != 0) && (sbchanged)) {
    // the first time to access this SBlock
    // not start from the very beginning
    orig->blockbase = sbstart;
  } 

  // setup information for this sblock
  orig->blockbound = orig->blockbase+(unsigned int)*((int*)(orig->blockbase));
  orig->offset = BAMBOO_CACHE_LINE_SIZE;
  orig->ptr = orig->blockbase + orig->offset;
  if(orig->ptr >= orig->bound) {
    // met a lobj, move to next block
    goto innernextSBlock;
  }

  return true;
} 

// return false if there are no available data to compact
INLINE bool initOrig_Dst(struct moveHelper * orig,struct moveHelper * to) {
  // init the dst ptr
  to->numblocks = 0;
  to->top = to->offset = BAMBOO_CACHE_LINE_SIZE;
  to->bound = BAMBOO_SMEM_SIZE_L;
  BASEPTR(BAMBOO_NUM_OF_CORE, to->numblocks, &(to->base));

  void * tobase = to->base;
  to->ptr = tobase + to->offset;

  // init the orig ptr
  orig->numblocks = 0;
  orig->base = tobase;
  unsigned int blocknum = 0;
  BLOCKINDEX(orig->base, blocknum);
  void * origbase = orig->base;
  // check the bamboo_smemtbl to decide the real bound
  orig->bound = origbase + (unsigned INTPTR)bamboo_smemtbl[blocknum];
  orig->blockbase = origbase;
  orig->sblockindex = (unsigned INTPTR)(origbase - gcbaseva) / BAMBOO_SMEM_SIZE;

  int sbstart = gcsbstarttbl[orig->sblockindex];
  if(sbstart == -1) {
    // goto next sblock
    orig->blockbound=gcbaseva+BAMBOO_SMEM_SIZE*(orig->sblockindex+1);
    return nextSBlock(orig);
  } else if(sbstart != 0) {
    orig->blockbase = sbstart;
  }
  orig->blockbound = orig->blockbase + *((int*)(orig->blockbase));
  orig->offset = BAMBOO_CACHE_LINE_SIZE;
  orig->ptr = orig->blockbase + orig->offset;

  return true;
}

INLINE void nextBlock(struct moveHelper * to) {
  to->top = to->bound + BAMBOO_CACHE_LINE_SIZE; // header!
  to->bound += BAMBOO_SMEM_SIZE;
  to->numblocks++;
  BASEPTR(BAMBOO_NUM_OF_CORE, to->numblocks, &(to->base));
  to->offset = BAMBOO_CACHE_LINE_SIZE;
  to->ptr = to->base + to->offset;
}

INLINE unsigned int findValidObj(struct moveHelper * orig,struct moveHelper * to,int * type) {
  unsigned int size = 0;
  while(true) {
    CACHEADAPT_COMPLETE_PAGE_CONVERT(orig, to, to->ptr, false);
    unsigned int origptr = (unsigned int)(orig->ptr);
    unsigned int origbound = (unsigned int)orig->bound;
    unsigned int origblockbound = (unsigned int)orig->blockbound;
    if((origptr >= origbound) || (origptr == origblockbound)) {
      if(!nextSBlock(orig)) {
        // finished, no more data
        return -1;
      }
      continue;
    }
    // check the obj's type, size and mark flag
    *type = ((int *)(origptr))[0];
    size = 0;
    if(*type == 0) {
      // end of this block, go to next one
      if(!nextSBlock(orig)) {
        // finished, no more data
        return -1;
      }
      continue;
    } else if(*type < NUMCLASSES) {
      // a normal object
      size = classsize[*type];
    } else {
      // an array
      struct ArrayObject *ao=(struct ArrayObject *)(origptr);
      unsigned int elementsize=classsize[*type];
      unsigned int length=ao->___length___;
      size=(unsigned int)sizeof(struct ArrayObject)+(unsigned int)(length*elementsize);
    }
    return size;
  }
}

// endaddr does not contain spaces for headers
INLINE bool moveobj(struct moveHelper * orig, struct moveHelper * to, unsigned int stopblock) {
  if(stopblock == 0) {
    return true;
  }

  int type = 0;
  unsigned int size = findValidObj(orig, to, &type);
  unsigned int isize = 0;

  if(size == -1) {
    // finished, no more data
    return true;
  }
  ALIGNSIZE(size, &isize);       // no matter is the obj marked or not
                                 // should be able to across
  void * origptr = orig->ptr;
  int markedstatus;
  GETMARKED(markedstatus, origptr);
  
  if(markedstatus==MARKEDFIRST) {
    unsigned int totop = (unsigned int)to->top;
    unsigned int tobound = (unsigned int)to->bound;
    BAMBOO_ASSERT(totop<=tobound);
    GCPROFILE_RECORD_LIVE_OBJ();
    // marked obj, copy it to current heap top
    // check to see if remaining space is enough
    if((unsigned int)(totop + isize) > tobound) {
      // fill 0 indicating the end of this block
      BAMBOO_MEMSET_WH(to->ptr,  '\0', tobound - totop);
      // fill the header of this block and then go to next block
      to->offset += tobound - totop;
      CLOSEBLOCK(to->base, to->offset);
#ifdef GC_CACHE_ADAPT
      void * tmp_ptr = to->ptr;
#endif 
      nextBlock(to);
      if((to->top+isize)>(to->bound)) tprintf("%x, %x, %d, %d, %d, %d \n", to->ptr, orig->ptr, to->top, to->bound, isize, size);
      BAMBOO_ASSERT((to->top+isize)<=(to->bound));
#ifdef GC_CACHE_ADAPT
      CACHEADAPT_COMPLETE_PAGE_CONVERT(orig, to, tmp_ptr, true);
#endif 
      if(stopblock == to->numblocks) {
        // already fulfilled the block
        return true;
      }  
    }
    BAMBOO_ASSERT((to->top+isize)<=(to->bound));
    // set the mark field to 2, indicating that this obj has been moved
    // and need to be flushed
    void * toptr = to->ptr;
    if(toptr != origptr) {
      if((unsigned int)(origptr) < (unsigned int)(toptr+size)) {
        memmove(toptr, origptr, size);
      } else {
        memcpy(toptr, origptr, size);
      }
      // fill the remaining space with -2
      BAMBOO_MEMSET_WH((unsigned int)(toptr+size), -2, isize-size);
    }
    // store mapping info
    gcmappingtbl[OBJMAPPINGINDEX(origptr)]=(unsigned int)toptr;
    gccurr_heaptop -= isize;
    to->ptr += isize;
    to->offset += isize;
    to->top += isize;
    BAMBOO_ASSERT((to->top)<=(to->bound));
#ifdef GC_CACHE_ADAPT
    void * tmp_ptr = to->ptr;
#endif // GC_CACHE_ADAPT
    if(to->top == to->bound) {
      CLOSEBLOCK(to->base, to->offset);
      nextBlock(to);
    }
#ifdef GC_CACHE_ADAPT
    CACHEADAPT_COMPLETE_PAGE_CONVERT(orig, to, tmp_ptr, true);
#endif
  } 
  
  // move to next obj
  orig->ptr += isize; 
  
  return ((((unsigned int)(orig->ptr) > (unsigned int)(orig->bound))||((unsigned int)(orig->ptr) == (unsigned int)(orig->blockbound)))&&!nextSBlock(orig));
} 

// should be invoked with interrupt closed
bool gcfindSpareMem_I(unsigned int * startaddr,unsigned int * tomove,unsigned int * dstcore,unsigned int requiredmem,unsigned int requiredcore) {
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
} 

bool gcfindSpareMem(unsigned int * startaddr,unsigned int * tomove,unsigned int * dstcore,unsigned int requiredmem,unsigned int requiredcore) {
  BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
  bool retval=gcfindSpareMem_I(startaddr, tomove, dstcore, requiredmem, requiredcore);
  BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
  return retval;
}

bool compacthelper(struct moveHelper * orig,struct moveHelper * to,int * filledblocks, void ** heaptopptr,bool * localcompact, bool lbmove) {
  bool loadbalancemove = lbmove;
  // scan over all objs in this block, compact the marked objs
  // loop stop when finishing either scanning all active objs or
  // fulfilled the gcstopblock
  while(true) {
    while((unsigned int)(orig->ptr) < (unsigned int)gcmarkedptrbound) {
      if(moveobj(orig, to, gcblock2fill)) {
	break;
      }
    }
    CACHEADAPT_SAMPLING_DATA_CONVERT(to->ptr);
    // if no objs have been compact, do nothing,
    // otherwise, fill the header of this block
    if(to->offset > (unsigned int)BAMBOO_CACHE_LINE_SIZE) {
      CLOSEBLOCK(to->base, to->offset);
    } else {
      to->offset = 0;
      to->ptr = to->base;
      to->top -= BAMBOO_CACHE_LINE_SIZE;
    }  
    if(*localcompact) {
      *heaptopptr = to->ptr;
      *filledblocks = to->numblocks;
    }
    
    // send msgs to core coordinator indicating that the compact is finishing
    // send compact finish message to core coordinator
    if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
      gcfilledblocks[BAMBOO_NUM_OF_CORE] = *filledblocks;
      topptrs[BAMBOO_NUM_OF_CORE] = *heaptopptr;
      //tprintf("--finish compact: %d, %d, %d, %x, %x \n", BAMBOO_NUM_OF_CORE, loadbalancemove, *filledblocks, *heaptopptr, gccurr_heaptop);
      if((unsigned int)(orig->ptr) < (unsigned int)gcmarkedptrbound) {
	// ask for more mem
	gctomove = false;
	if(gcfindSpareMem(&gcmovestartaddr,&gcblock2fill,&gcdstcore,gccurr_heaptop,BAMBOO_NUM_OF_CORE)) {
	  gctomove = true;
	} else {
	  return false;
	}
      } else {
	gccorestatus[BAMBOO_NUM_OF_CORE] = 0;
	gctomove = false;
	// write back to the Main Memory and release any DTLB entry for the 
	// last block as someone else might later write into it
	// flush the shared heap
	//BAMBOO_CACHE_FLUSH_L2();
	return true;
      }
    } else {
      if((unsigned int)(orig->ptr) < (unsigned int)gcmarkedptrbound) {
	// ask for more mem
	gctomove = false;
	send_msg_6(STARTUPCORE,GCFINISHCOMPACT,BAMBOO_NUM_OF_CORE,loadbalancemove,*filledblocks,*heaptopptr,gccurr_heaptop);
      } else {
	// finish compacting
	send_msg_6(STARTUPCORE,GCFINISHCOMPACT,BAMBOO_NUM_OF_CORE,loadbalancemove,*filledblocks,*heaptopptr, 0);
	// write back to the Main Memory and release any DTLB entry for the 
	// last block as someone else might later write into it.
	// flush the shared heap
      }
    }
    
    if(orig->ptr < gcmarkedptrbound) {
      // still have unpacked obj
      while(!gctomove) ;
      BAMBOO_CACHE_MF();
      loadbalancemove = true;
      
      gctomove = false;
      to->ptr = gcmovestartaddr;
      to->numblocks = gcblock2fill - 1;
      to->bound = BLOCKBOUND(to->numblocks);
      BASEPTR(gcdstcore, to->numblocks, &(to->base));
      to->offset = to->ptr - to->base;
      to->top=(to->numblocks==0)?(to->offset):(to->bound-BAMBOO_SMEM_SIZE+to->offset);
      to->base = to->ptr;
      to->offset = BAMBOO_CACHE_LINE_SIZE;
      to->ptr += to->offset;   // for header
      to->top += to->offset;
      *localcompact = (gcdstcore == BAMBOO_NUM_OF_CORE);
      CACHEADAPT_SAMPLING_DATA_REVISE_INIT(orig, to);
    } else
      return true;
}
}

void compact() {
  BAMBOO_ASSERT(COMPACTPHASE == gc_status_info.gcphase);
  BAMBOO_CACHE_MF();
  
  // initialize pointers for comapcting
  struct moveHelper * orig = (struct moveHelper *)RUNMALLOC(sizeof(struct moveHelper));
  struct moveHelper * to = (struct moveHelper *)RUNMALLOC(sizeof(struct moveHelper));
  if(!initOrig_Dst(orig, to)) {
    // no available data to compact
    // send compact finish msg to STARTUP core
    send_msg_6(STARTUPCORE,GCFINISHCOMPACT,BAMBOO_NUM_OF_CORE,false,0,to->base,0);
    RUNFREE(orig);
    RUNFREE(to);
  } else {
    CACHEADAPT_SAMPLING_DATA_REVISE_INIT(orig, to);

    unsigned int filledblocks = 0;
    void * heaptopptr = NULL;
    bool localcompact = true;
    compacthelper(orig, to, &filledblocks, &heaptopptr, &localcompact, false);
    RUNFREE(orig);
    RUNFREE(to);
  }
} 

void compact_master(struct moveHelper * orig, struct moveHelper * to) {
  // initialize pointers for comapcting
  initOrig_Dst(orig, to);
  CACHEADAPT_SAMPLING_DATA_REVISE_INIT(orig, to);
  int filledblocks = 0;
  void * heaptopptr = NULL;
  bool finishcompact = false;
  bool iscontinue = true;
  bool localcompact = true;
  bool lbmove = false;
  while((COMPACTPHASE == gc_status_info.gcphase) || (SUBTLECOMPACTPHASE == gc_status_info.gcphase)) {
    if((!finishcompact) && iscontinue) {
      finishcompact = compacthelper(orig,to,&filledblocks,&heaptopptr,&localcompact, lbmove);
    }
    
    if(gc_checkCoreStatus()) {
      // all cores have finished compacting restore the gcstatus of all cores
      gc_resetCoreStatus();
      break;
    } else {
      // check if there are spare mem for pending move requires
      if(COMPACTPHASE == gc_status_info.gcphase) {
        resolvePendingMoveRequest();
      } else {
        compact2Heaptop();
      }
    } 

    if(gctomove) {
      BAMBOO_CACHE_MF();
      to->ptr = gcmovestartaddr;
      to->numblocks = gcblock2fill - 1;
      to->bound = BLOCKBOUND(to->numblocks);
      BASEPTR(gcdstcore, to->numblocks, &(to->base));
      to->offset = to->ptr - to->base;
      to->top = (to->numblocks==0)?(to->offset):(to->bound-BAMBOO_SMEM_SIZE+to->offset);
      to->base = to->ptr;
      to->offset = BAMBOO_CACHE_LINE_SIZE;
      to->ptr += to->offset;  // for header
      to->top += to->offset;
      localcompact = (gcdstcore == BAMBOO_NUM_OF_CORE);
      gctomove = false;
      iscontinue = true;
      lbmove = true;
    } else if(!finishcompact) {
      // still pending
      iscontinue = false;
      lbmove = false;
    }
  }
}

#endif // MULTICORE_GC
