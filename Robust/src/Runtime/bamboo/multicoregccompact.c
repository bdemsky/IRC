#ifdef MULTICORE_GC
#include "structdefs.h"
#include "multicoregccompact.h"
#include "runtime_arch.h"
#include "multicoreruntime.h"
#include "multicoregarbage.h"
#include "markbit.h"
#include "multicoremem_helper.h"
#include "gcqueue.h"

int gc_countRunningCores() {
  int count=0;
  for(int i = 0; i < NUMCORES4GC; i++) {
    if(returnedmem[i]) {
      count++;
    }
  }
  return count;
}

void initOrig_Dst(struct moveHelper * orig,struct moveHelper * to) {
  // init the dst ptr
  to->localblocknum = 0;
  BASEPTR(to->base, BAMBOO_NUM_OF_CORE, to->localblocknum);
  to->ptr = to->base;
  to->bound=to->base+BLOCKSIZE(to->localblocknum);
  
  // init the orig ptr
  orig->localblocknum = 0;
  orig->ptr=orig->base = to->base;
  orig->bound=orig->base+BLOCKSIZE(orig->localblocknum);
#ifdef GC_CACHE_ADAPT
  to->pagebound=to->base+BAMBOO_PAGE_SIZE;
  orig->pagebound=orig->base+BAMBOO_PAGE_SIZE;
#endif
}

void getSpaceLocally(struct moveHelper *to) {
  //we have space on our core...just keep going
  to->localblocknum++;
  BASEPTR(to->base,BAMBOO_NUM_OF_CORE, to->localblocknum);
  to->ptr=to->base;
  to->bound=to->base+BLOCKSIZE(to->localblocknum);
#ifdef GC_CACHE_ADAPT
  to->pagebound=to->base+BAMBOO_PAGE_SIZE;
#endif
}

//This function is called on the master core only...and typically by
//the message interrupt handler

void handleReturnMem_I(unsigned int cnum, void *heaptop) {
  unsigned int blockindex;
  BLOCKINDEX(blockindex, heaptop);
  unsigned INTPTR localblocknum=GLOBALBLOCK2LOCAL(blockindex);
  //this core is done as far as memory usage is concerned
  returnedmem[cnum]=0;

  struct blockrecord * blockrecord=&allocationinfo.blocktable[blockindex];

  blockrecord->status=BS_FREE;
  blockrecord->usedspace=(unsigned INTPTR)(heaptop-OFFSET2BASEVA(blockindex)-gcbaseva);
  blockrecord->freespace=BLOCKSIZE(localblocknum)-blockrecord->usedspace;
  /* Update the lowest free block */
  if (blockindex < allocationinfo.lowestfreeblock) {
    allocationinfo.lowestfreeblock=blockindex;
  }

  /* This is our own block...means we should mark other blocks above us as free*/
  
  if (cnum==blockrecord->corenum) {
    unsigned INTPTR nextlocalblocknum=localblocknum+1;
    for(;nextlocalblocknum<numblockspercore;nextlocalblocknum++) {
      unsigned INTPTR blocknum=BLOCKINDEX2(cnum, nextlocalblocknum);
      struct blockrecord * nextblockrecord=&allocationinfo.blocktable[blocknum];
      nextblockrecord->status=BS_FREE;
      nextblockrecord->usedspace=0;
      //this is true because this cannot be the lowest block
      nextblockrecord->freespace=BLOCKSIZE(1);
    }
  }

  //this could be the last one....
  int count=gc_countRunningCores();
  if (gcmovepending==count) {
    // All cores have stopped...hand out memory as necessary to handle all requests
    handleMemoryRequests_I();
  } else {
    //see if returned memory blocks let us resolve requests
    useReturnedMem(cnum, allocationinfo.lowestfreeblock);
  }
}

void useReturnedMem(unsigned int retcorenum, block_t localblockindex) {
  for(int i=0;i<NUMCORES4GC;i++) {
    unsigned INTPTR requiredmem=gcrequiredmems[i];
    if (requiredmem) {
      unsigned INTPTR desiredmem=maxusefulmems[i];
      unsigned INTPTR threshold=(desiredmem<MINMEMORYCHUNKSIZE)? desiredmem: MINMEMORYCHUNKSIZE;
      unsigned INTPTR memcheck=requiredmem>threshold?requiredmem:threshold;


      for(block_t nextlocalblocknum=localblockindex;nextlocalblocknum<numblockspercore;nextlocalblocknum++) {
	unsigned INTPTR blocknum=BLOCKINDEX2(retcorenum, nextlocalblocknum);
	struct blockrecord * nextblockrecord=&allocationinfo.blocktable[blocknum];
	if (nextblockrecord->status==BS_FREE) {
	  unsigned INTPTR freespace=nextblockrecord->freespace&~BAMBOO_CACHE_LINE_MASK;
	  if (freespace>=memcheck) {
	    nextblockrecord->status=BS_USED;
	    void *blockptr=OFFSET2BASEVA(blocknum)+gcbaseva;
	    unsigned INTPTR usedspace=((nextblockrecord->usedspace-1)&~BAMBOO_CACHE_LINE_MASK)+BAMBOO_CACHE_LINE_SIZE;
	    //taken care of one block
	    gcmovepending--;
	    void *startaddr=blockptr+usedspace;
	    gcrequiredmems[i]=0;
	    maxusefulmems[i]=0;
	    if (i==STARTUPCORE) {
	      gctomove = true;
	      gcmovestartaddr = startaddr;
	    } else if(BAMBOO_CHECK_SEND_MODE()) {
	      cache_msg_2_I(i,GCMOVESTART,startaddr);
	    } else {
	      send_msg_2_I(i,GCMOVESTART,startaddr);
	    }
	  }
	}
      }
    }
  }
}

void handleReturnMem(unsigned int cnum, void *heaptop) {
  BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
  handleReturnMem_I(cnum, heaptop);
  BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
}

void getSpaceRemotely(struct moveHelper *to, unsigned int minimumbytes) {
  //need to get another block from elsewhere
  //set flag to wait for memory

  if (BAMBOO_NUM_OF_CORE==STARTUPCORE) {
    gctomove=false;
    BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
    void *startaddr=handlegcfinishcompact_I(BAMBOO_NUM_OF_CORE, minimumbytes, gccurr_heaptop);
    BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();

    if (startaddr) {
      gcmovestartaddr=startaddr;
    } else {
      while(!gctomove) ;
    }
  } else {
    gctomove=false;
    //send request for memory
    send_msg_4(STARTUPCORE,GCFINISHCOMPACT,BAMBOO_NUM_OF_CORE, minimumbytes, gccurr_heaptop);
    //wait for flag to be set that we received message
    while(!gctomove)
      ;
  }

  //store pointer
  to->ptr = gcmovestartaddr;

  //set localblock number to high number to indicate this block isn't local
  to->localblocknum = MAXBLOCK;
  unsigned int globalblocknum;
  BLOCKINDEX(globalblocknum, to->ptr);
  to->base = gcbaseva + OFFSET2BASEVA(globalblocknum);
  to->bound=gcbaseva+BOUNDPTR(globalblocknum);
#ifdef GC_CACHE_ADAPT
  to->pagebound=(void *)((int)((int)(to->ptr)&(~(BAMBOO_PAGE_SIZE-1)))+BAMBOO_PAGE_SIZE);
#endif
}

void getSpace(struct moveHelper *to, unsigned int minimumbytes) {
  //need more space to compact into
  if ((to->localblocknum+1) < gcblock2fill) {
    getSpaceLocally(to);
  } else {
    getSpaceRemotely(to, minimumbytes);
  }
}

void compacthelper(struct moveHelper * orig,struct moveHelper * to) {
  bool senttopmessage=false;
  while(true) {
    if ((gccurr_heaptop <= ((unsigned INTPTR)(to->bound-to->ptr)))&&!senttopmessage) {
      //This block is the last for this core...let the startup know
      if (BAMBOO_NUM_OF_CORE==STARTUPCORE) {
	handleReturnMem(BAMBOO_NUM_OF_CORE, to->ptr+gccurr_heaptop);
      } else {
	send_msg_3(STARTUPCORE, GCRETURNMEM, BAMBOO_NUM_OF_CORE, to->ptr+gccurr_heaptop);
      }
      //Only send the message once
      senttopmessage=true;
    }
    unsigned int minimumbytes=COMPACTUNITS(orig, to);
    if (orig->ptr==orig->bound) {
      //need more data to compact
      //increment the core
      orig->localblocknum++;
      BASEPTR(orig->base,BAMBOO_NUM_OF_CORE, orig->localblocknum);
      orig->ptr=orig->base;
      orig->bound=orig->base+BLOCKSIZE(orig->localblocknum);
#ifdef GC_CACHE_ADAPT
      orig->pagebound=orig->base+BAMBOO_PAGE_SIZE;
#endif
      if (orig->base >= gcbaseva+BAMBOO_SHARED_MEM_SIZE) {
	CACHEADAPT_FINISH_COMPACT(to->ptr);
	break;
      }
    }
    if (minimumbytes!=0) {
      getSpace(to, minimumbytes);
    }
  }
  if (BAMBOO_NUM_OF_CORE==STARTUPCORE) {
    BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
    handlegcfinishcompact_I(BAMBOO_NUM_OF_CORE, 0, 0);
    BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
  } else {
    send_msg_4(STARTUPCORE,GCFINISHCOMPACT,BAMBOO_NUM_OF_CORE, 0, 0);
  }
}

void * checkNeighbors_I(int ncorenum, unsigned INTPTR requiredmem, unsigned INTPTR desiredmem) {
  int minblockindex=allocationinfo.lowestfreeblock/NUMCORES4GC;
  unsigned INTPTR threshold=(desiredmem<MINMEMORYCHUNKSIZE)? desiredmem: MINMEMORYCHUNKSIZE;
  unsigned INTPTR memcheck=requiredmem>threshold?requiredmem:threshold;

  for(block_t lblock=minblockindex;lblock<numblockspercore;lblock++) {
    for(int i=0;i<NUM_CORES2TEST;i++) {
      int neighborcore=core2test[ncorenum][i];
      if (neighborcore!=-1) {
	block_t globalblockindex=BLOCKINDEX2(neighborcore, lblock);
	struct blockrecord * block=&allocationinfo.blocktable[globalblockindex];
	if (block->status==BS_FREE) {
	  unsigned INTPTR freespace=block->freespace&~BAMBOO_CACHE_LINE_MASK;
	  if (memcheck<=freespace) {
	    //we have a block
	    //mark block as used
	    block->status=BS_USED;
	    void *blockptr=OFFSET2BASEVA(globalblockindex)+gcbaseva;
	    unsigned INTPTR usedspace=((block->usedspace-1)&~BAMBOO_CACHE_LINE_MASK)+BAMBOO_CACHE_LINE_SIZE;
	    return blockptr+usedspace;
	  }
	}
      }
    }
  }
  return NULL;
}

void * globalSearch_I(unsigned int topblock, unsigned INTPTR requiredmem, unsigned INTPTR desiredmem) {
  unsigned int firstfree=NOFREEBLOCK;
  unsigned INTPTR threshold=(desiredmem<MINMEMORYCHUNKSIZE)? desiredmem: MINMEMORYCHUNKSIZE;
  unsigned INTPTR memcheck=requiredmem>threshold?requiredmem:threshold;

  for(block_t i=allocationinfo.lowestfreeblock;i<topblock;i++) {
    struct blockrecord * block=&allocationinfo.blocktable[i];
    if (block->status==BS_FREE) {
      if(firstfree==NOFREEBLOCK)
	firstfree=i;
      unsigned INTPTR freespace=block->freespace&~BAMBOO_CACHE_LINE_MASK;
      if (memcheck<=freespace) {
	//we have a block
	//mark block as used
	block->status=BS_USED;
	void *blockptr=OFFSET2BASEVA(i)+gcbaseva;
	unsigned INTPTR usedspace=((block->usedspace-1)&~BAMBOO_CACHE_LINE_MASK)+BAMBOO_CACHE_LINE_SIZE;
	allocationinfo.lowestfreeblock=firstfree;
	return blockptr+usedspace;
      }
    }
  }
  allocationinfo.lowestfreeblock=firstfree;
  return NULL;
}

void handleOneMemoryRequest(int core, unsigned int lowestblock) {
  unsigned INTPTR requiredmem=gcrequiredmems[core];
  unsigned INTPTR desiredmem=maxusefulmems[core];
  block_t firstfree=NOFREEBLOCK;
  unsigned INTPTR threshold=(desiredmem<MINMEMORYCHUNKSIZE)? desiredmem: MINMEMORYCHUNKSIZE;
  unsigned INTPTR memcheck=requiredmem>threshold?requiredmem:threshold;

  for(block_t searchblock=lowestblock;searchblock<GCNUMBLOCK;searchblock++) {
    struct blockrecord * block=&allocationinfo.blocktable[searchblock];
    if (block->status==BS_FREE) {
      if(firstfree==NOFREEBLOCK)
	firstfree=searchblock;
      //don't take a block from another core that hasn't returned its memory yet
      if (block->corenum!=core&&returnedmem[block->corenum])
	continue;
      
      unsigned INTPTR freespace=block->freespace&~BAMBOO_CACHE_LINE_MASK;
      if (freespace>=memcheck) {
	//TODO: should check memory block at same level on our own core...if that works, use it to preserve locality

	//we have a block
	//mark block as used
	block->status=BS_USED;
	void *blockptr=OFFSET2BASEVA(searchblock)+gcbaseva;
	unsigned INTPTR usedspace=((block->usedspace-1)&~BAMBOO_CACHE_LINE_MASK)+BAMBOO_CACHE_LINE_SIZE;
	allocationinfo.lowestfreeblock=firstfree;
	//taken care of one block
	gcmovepending--;
	void *startaddr=blockptr+usedspace;
	if (core==STARTUPCORE) {
	  gctomove=true;
	  gcmovestartaddr=startaddr;
	} else if(BAMBOO_CHECK_SEND_MODE()) {
	  cache_msg_2_I(core,GCMOVESTART,startaddr);
	} else {
	  send_msg_2_I(core,GCMOVESTART,startaddr);
	}
	return;
      }
    }
  }
  //this is bad...ran out of memory
  printf("Out of memory.  Was trying for %u bytes\n", threshold);
  BAMBOO_EXIT();
}

void handleMemoryRequests_I() {
  unsigned int lowestblock=allocationinfo.lowestfreeblock;
  if (lowestblock==NOFREEBLOCK) {
    lowestblock=numblockspercore*NUMCORES4GC;
  }
  
  for(int i=0;i < NUMCORES4GC; i++) {
    if (gcrequiredmems[i]) {
      handleOneMemoryRequest(i, lowestblock);
      lowestblock=allocationinfo.lowestfreeblock;
    }
  }
}

/* should be invoked with interrupt turned off */

void * gcfindSpareMem_I(unsigned INTPTR requiredmem, unsigned INTPTR desiredmem,unsigned int requiredcore) {
  if (allocationinfo.lowestfreeblock!=NOFREEBLOCK) {
    //There are spare blocks
    unsigned int topblock=numblockspercore*NUMCORES4GC;
    void *memblock;
    
    if (memblock=checkNeighbors_I(requiredcore, requiredmem, desiredmem)) {
      return memblock;
    } else if (memblock=globalSearch_I(topblock, requiredmem, desiredmem)) {
      return memblock;
    }
  }
  
  // If we cannot find spare mem right now, hold the request
  gcrequiredmems[requiredcore] = requiredmem;
  maxusefulmems[requiredcore]=desiredmem;
  gcmovepending++;

  int count=gc_countRunningCores();
  if (gcmovepending==count) {
    // All cores have stopped...hand out memory as necessary to handle all requests
    handleMemoryRequests_I();
  }

  return NULL;
} 

#ifdef GC_CACHE_ADAPT
unsigned int compactblockshelper(struct moveHelper * orig, struct moveHelper * to) {
  unsigned int minimumbytes=0;
  void *origptr=orig->ptr;
  void *origbound=orig->bound;
  void * tmporig=orig->ptr;
  void * tmpto=to->ptr;

  while(true) {
    //call compactblocks using the page boundaries at the current bounds
    minimumbytes=compactblocks(orig, to);
    if(minimumbytes == 0) {
      //bump the orig page bound...
      //use old orig pointer to make sure we get correct block
      CACHEADAPT_FINISH_SRC_PAGE(tmporig, tmpto, to->ptr);
      if (orig->ptr<origbound) {
	tmporig=orig->ptr;
	tmpto=to->ptr;
	orig->pagebound=orig->pagebound+BAMBOO_PAGE_SIZE;
      } else {
	return 0;
      }
    } else {
      // require more memory
      void *endtoptr=to->ptr+minimumbytes;
      if (endtoptr>to->bound) {
	CACHEADAPT_FINISH_DST_PAGE(orig->ptr, tmpto, to->ptr, 0);
	return minimumbytes;
      } else {
	CACHEADAPT_FINISH_DST_PAGE(orig->ptr, tmpto, to->ptr, minimumbytes);
	to->pagebound=((((unsigned INTPTR)endtoptr)-1)&~(BAMBOO_PAGE_SIZE-1))+BAMBOO_PAGE_SIZE;
	//update pointers to avoid double counting the stuff we already added in
	tmporig=orig->ptr+minimumbytes;
	tmpto=to->ptr+minimumbytes;
      }
    }
  }
}
#endif

/* This function is performance critical...  spend more time optimizing it */

unsigned int compactblocks(struct moveHelper * orig, struct moveHelper * to) {
  void *toptrinit=to->ptr;
  void *toptr=toptrinit;
  void *origptr=orig->ptr;
#ifdef GC_CACHE_ADAPT
  void *origbound=orig->pagebound;
  void *tobound=to->pagebound;
  //set to the first line so we don't need conditions
  void *lastflush=(void *)(((unsigned INTPTR)&gcmappingtbl[OBJMAPPINGINDEX(origptr)])&~(BAMBOO_CACHE_LINE_MASK));
#else
  void *origbound=orig->bound;
  void *tobound=to->bound;
#endif
  unsigned INTPTR origendoffset=ALIGNTOTABLEINDEX((unsigned INTPTR)(origbound-gcbaseva));
  unsigned int objlength;

  while(origptr<origbound) {
    //Try to skip over stuff fast first
    unsigned INTPTR offset=(unsigned INTPTR) (origptr-gcbaseva);
    unsigned INTPTR arrayoffset=ALIGNTOTABLEINDEX(offset);
    if (!gcmarktbl[arrayoffset]) {
      do {
	arrayoffset++;
	if (arrayoffset>=origendoffset) {
	  //finished with block(a page in CACHE_ADAPT version)...
	  to->ptr=toptr;
	  orig->ptr=origbound;
	  gccurr_heaptop-=(unsigned INTPTR)(toptr-toptrinit);
#ifdef GC_CACHE_ADAPT
	  BAMBOO_CACHE_FLUSH_LINE(lastflush);
#endif
	  return 0;
	}
      } while(!gcmarktbl[arrayoffset]);
      origptr=CONVERTTABLEINDEXTOPTR(arrayoffset);
    }
    
    //Scan more carefully next
    objlength=getMarkedLength(origptr);

    if (objlength!=NOTMARKED) {
      unsigned int length=ALIGNSIZETOBYTES(objlength);

      //code between this and next comment should be removed
#ifdef GC_DEBUG
      unsigned int size;
      unsigned int type;
      gettype_size(origptr, &type, &size);
      size=((size-1)&(~(ALIGNMENTSIZE-1)))+ALIGNMENTSIZE;
      
      if (size!=length) {
	tprintf("BAD SIZE IN BITMAP: type=%u object=%x size=%u length=%u\n", type, origptr, size, length);
	unsigned INTPTR alignsize=ALIGNOBJSIZE((unsigned INTPTR)(origptr-gcbaseva));
	unsigned INTPTR hibits=alignsize>>4;
	unsigned INTPTR lobits=(alignsize&15)<<1;
	tprintf("hibits=%x lobits=%x\n", hibits, lobits);
	tprintf("hi=%x lo=%x\n", gcmarktbl[hibits], gcmarktbl[hibits+1]);
      }
#endif
      //end of code to remove

      void *endtoptr=toptr+length;
      if (endtoptr>tobound) {
	gccurr_heaptop-=(unsigned INTPTR)(toptr-toptrinit);
	to->ptr=toptr;
	orig->ptr=origptr;
#ifdef GC_CACHE_ADAPT
	BAMBOO_CACHE_FLUSH_LINE(lastflush);
#endif
	return length;
      }
      //good to move objects and update pointers
      
      void ** mapptr=&gcmappingtbl[OBJMAPPINGINDEX(origptr)];
      *mapptr=toptr;

#ifdef GC_CACHE_ADAPT
      void *maskmapptr=(void *)(((unsigned INTPTR)mapptr)&~(BAMBOO_CACHE_LINE_MASK));

      if (lastflush!=maskmapptr) {
	BAMBOO_CACHE_FLUSH_LINE(lastflush);
	lastflush=maskmapptr;
      }
#endif

      origptr+=length;
      toptr=endtoptr;
    } else
      origptr+=ALIGNMENTSIZE;
  }
  to->ptr=toptr;
  orig->ptr=origptr;
  gccurr_heaptop-=(unsigned INTPTR)(toptr-toptrinit);
#ifdef GC_CACHE_ADAPT
  BAMBOO_CACHE_FLUSH_LINE(lastflush);
#endif
  return 0;
}

void compact() {
  BAMBOO_ASSERT(COMPACTPHASE == gc_status_info.gcphase);
  
  // initialize structs for compacting
  struct moveHelper orig;
  struct moveHelper to;
  initOrig_Dst(&orig, &to);

  compacthelper(&orig, &to);
#ifdef GC_CACHE_ADAPT
  BAMBOO_CACHE_MF();
#endif
} 

void master_compact() {
  // predict number of blocks to fill for each core
  numblockspercore = loadbalance()+1;
  
  GC_PRINTF("mark phase finished \n");
  
  gc_resetCoreStatus();
  //initialize local data structures first....we don't want remote requests messing data up
  unsigned int initblocks=numblockspercore*NUMCORES4GC;
  allocationinfo.lowestfreeblock=NOFREEBLOCK;

  //assigned blocks
  for(int i=0;i<initblocks;i++) {
    allocationinfo.blocktable[i].status=BS_USED;
  }

  //free blocks
  for(int i=initblocks;i<GCNUMBLOCK;i++) {
    allocationinfo.blocktable[i].status=BS_FREE;
    allocationinfo.blocktable[i].usedspace=0;
    //this is true because all cores have at least one block already...
    allocationinfo.blocktable[i].freespace=BLOCKSIZE(1);
  }

  //start all of the cores
  for(int i = 0; i < NUMCORES4GC; i++) {
    // init some data strutures for compact phase
    gcrequiredmems[i] = 0;
    gccorestatus[i] = 1;
    returnedmem[i] = 1;
    //send start compact messages to all cores
    if(i != STARTUPCORE) {
      send_msg_2(i, GCSTARTCOMPACT, numblockspercore);
    } else {
      gcblock2fill = numblockspercore;
    }
  }
  GCPROFILE_ITEM();
  // compact phase
  compact();
  /* wait for all cores to finish compacting */
  GC_PRINTF("master finished\n");

  while(!gc_checkCoreStatus())
    ;

#ifdef GC_DEBUG
  void *nextvalid=gcbaseva;
  for(void *tmp=gcbaseva; tmp<gcbaseva+BAMBOO_SHARED_MEM_SIZE;tmp+=ALIGNMENTSIZE) {
    unsigned int objlength=getMarkedLength(tmp);
    void *forwarding=gcmappingtbl[OBJMAPPINGINDEX(tmp)];
    if (tmp>=nextvalid&&((objlength!=0)!=(forwarding!=NULL))) {
      tprintf("Maps disagree tmp=%x olength=%u forwarding=%x\n",tmp, objlength, forwarding);
    }
    if (tmp<nextvalid&&forwarding!=NULL) {
      tprintf("Weird forwarding pointer\n");
    }
    if (tmp>=nextvalid&&(objlength!=0||forwarding!=NULL)) {
      unsigned int length=ALIGNSIZETOBYTES(objlength);
      unsigned int size;
      unsigned int type;
      nextvalid=tmp+length;
      gettype_size(tmp, &type, &size);
      size=((size-1)&(~(ALIGNMENTSIZE-1)))+ALIGNMENTSIZE;
      if (size!=length) {
	tprintf("Bad size in bitmap: tmp=%x length=%u size=%u type=%u\n", tmp, length, size, type);
      }
      block_t blockindex;
      BLOCKINDEX(blockindex, forwarding);
      struct blockrecord * block=&allocationinfo.blocktable[blockindex];
      void *blockptr=OFFSET2BASEVA(blockindex)+gcbaseva;

      if (block->status==BS_FREE) {
	if (forwarding>(blockptr+block->usedspace)) {
	  tprintf("Pointer references free space forwarding=%x tmp=%x length=%u type=%u blockindex=%u, baseptr=%x, usedspace=%u, status=%u\n", forwarding, tmp, length, type,blockindex, blockptr, block->usedspace, block->status);
	}
      }
    }
  }
#endif

  GCPROFILE_ITEM();

  //just in case we didn't get blocks back...
  if (allocationinfo.lowestfreeblock==NOFREEBLOCK)
    allocationinfo.lowestfreeblock=numblockspercore*NUMCORES4GC;

  // compute live object space
  GCPROFILE_RECORD_SPACE();
  GC_PRINTF("compact phase finished \n");
}

#endif // MULTICORE_GC
