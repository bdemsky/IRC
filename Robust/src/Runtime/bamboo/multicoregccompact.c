#ifdef MULTICORE_GC
#include "multicoregccompact.h"
#include "runtime_arch.h"
#include "multicoreruntime.h"
#include "multicoregarbage.h"
#include "markbit.h"
#include "multicoremem_helper.h"

int gc_countRunningCores() {
  int count=0;
  for(int i = 0; i < NUMCORES4GC; ++i) {
    if(gccorestatus[i] != 0) {
      count++;
    }
  }
  return count;
}

bool gc_checkCoreStatus() {
  for(int i = 0; i < NUMCORES4GC; ++i) {
    if(gccorestatus[i] != 0) {
      return false;
    }
  }  
  return true;
}

void gc_resetCoreStatus() {
  for(int i = 0; i < NUMCORES4GC; ++i) {
    gccorestatus[i] = 1;
  }
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
  orig->bound = orig->base + BLOCKSIZE(orig->localblocknum);
}

void getSpaceLocally(struct moveHelper *to) {
  //we have space on our core...just keep going
  to->localblocknum++;
  BASEPTR(to->base,BAMBOO_NUM_OF_CORE, to->localblocknum);
  to->ptr=to->base;
  to->bound = to->base + BLOCKSIZE(to->localblocknum);
}

void getSpaceRemotely(struct moveHelper *to, unsigned int minimumbytes) {
  //need to get another block from elsewhere
  //set flag to wait for memory
  gctomove=false;
  //send request for memory
  send_msg_3(STARTUPCORE,GCFINISHCOMPACT,BAMBOO_NUM_OF_CORE, minimumbytes);
  //wait for flag to be set that we received message
  while(!gctomove) ;

  //store pointer
  to->ptr = gcmovestartaddr;

  //set localblock number to high number to indicate this block isn't local
  to->localblocknum = MAXBLOCK;
  unsigned int globalblocknum;
  BLOCKINDEX(globalblocknum, to->ptr);
  to->base = gcbaseva + OFFSET2BASEVA(globalblocknum);
  to->bound = gcbaseva + BOUNDPTR(globalblocknum);
}

void getSpace(struct moveHelper *to, unsigned int minimumbytes) {
  //need more space to compact into
  if (to->localblocknum < gcblock2fill) {
    getSpaceLocally(to);
  } else {
    getSpaceRemotely(to, minimumbytes);
  }
}

void compacthelper(struct moveHelper * orig,struct moveHelper * to) {
  bool senttopmessage=false;
  while(true) {
    if ((gccurr_heaptop < ((unsigned INTPTR)(to->bound-to->ptr)))&&!senttopmessage) {
      //This block is the last for this core...let the startup know
      send_msg_3(STARTUPCORE, GCRETURNMEM, BAMBOO_NUM_OF_CORE, to->ptr+gccurr_heaptop);
      //Only send the message once
      senttopmessage=true;
    }

    unsigned int minimumbytes=compactblocks(orig, to);
    if (orig->ptr==orig->bound) {
      //need more data to compact
      //increment the core
      orig->localblocknum++;
      BASEPTR(orig->base,BAMBOO_NUM_OF_CORE, orig->localblocknum);
      orig->ptr=orig->base;
      orig->bound = orig->base + BLOCKSIZE(orig->localblocknum);
      if (orig->base >= gcbaseva+BAMBOO_SHARED_MEM_SIZE)
	break;
    }
    if (minimumbytes!=0) {
      getSpace(to, minimumbytes);
    }
  }
  
  send_msg_3(STARTUPCORE,GCFINISHCOMPACT,BAMBOO_NUM_OF_CORE, 0);
}

void * checkNeighbors(int corenum, unsigned INTPTR requiredmem) {
  int minblockindex=allocationinfo.lowestfreeblock/NUMCORES4GC;
  for(int i=0;i<NUM_CORES2TEST;i++) {
    int neighborcore=core2test[corenum][i];
    if (neighborcore!=-1) {
      for(block_t lblock=minblockindex;lblock<numblockspercore;lblock++) {
	block_t globalblockindex=BLOCKINDEX2(neighborcore, lblock);
	struct blockrecord * block=&allocationinfo.blocktable[globalblockindex];
	if (block->status==BS_FREE) {
	  unsigned INTPTR freespace=block->freespace&~BAMBOO_CACHE_LINE_MASK;
	  if (requiredmem<freespace) {
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

void * globalSearch(unsigned int topblock, unsigned INTPTR requiredmem) {
  unsigned int firstfree=NOFREEBLOCK;
  for(block_t i=allocationinfo.lowestfreeblock;i<topblock;i++) {
    struct blockrecord * block=&allocationinfo.blocktable[i];
    if (block->status==BS_FREE) {
      if(firstfree==NOFREEBLOCK)
	firstfree=i;
      unsigned INTPTR freespace=block->freespace&~BAMBOO_CACHE_LINE_MASK;
      if (requiredmem<freespace) {
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

/* should be invoked with interrupt turned off */

void * gcfindSpareMem_I(unsigned INTPTR requiredmem,unsigned int requiredcore) {
  if (allocationinfo.lowestfreeblock!=NOFREEBLOCK) {
    //There are spare blocks
    unsigned int topblock=numblockspercore*NUMCORES4GC;
    void *memblock;
    
    if (memblock=checkNeighbors(requiredcore, requiredmem)) {
      return memblock;
    } else if (memblock=globalSearch(topblock, requiredmem)) {
      return memblock;
    }
  }
  
  // If we cannot find spare mem right now, hold the request
  gcrequiredmems[requiredcore] = requiredmem;
  gcmovepending++;

  int count=gc_countRunningCores();
  if (gcmovepending==count) {
    // All cores have stopped...hand out memory as necessary to handle all requests
    
  }

  return NULL;
} 

bool gcfindSpareMem(unsigned int requiredmem,unsigned int requiredcore) {
  BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
  bool retval=gcfindSpareMem_I(requiredmem, requiredcore);
  BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
  return retval;
}

/* This function is performance critical...  spend more time optimizing it */

unsigned int compactblocks(struct moveHelper * orig, struct moveHelper * to) {
  void *toptrinit=to->ptr;
  void *toptr=toptr;
  void *tobound=to->bound;
  void *origptr=orig->ptr;
  void *origbound=orig->bound;
  unsigned INTPTR origendoffset=ALIGNTOTABLEINDEX((unsigned INTPTR)(origbound-gcbaseva));
  unsigned int objlength;

  while(origptr<origbound) {
    //Try to skip over stuff fast first
    unsigned INTPTR offset=(unsigned INTPTR) (origptr-gcbaseva);
    unsigned INTPTR arrayoffset=ALIGNTOTABLEINDEX(offset);
    if (!gcmarktbl[arrayoffset]) {
      do {
	arrayoffset++;
	if (arrayoffset<origendoffset) {
	  //finished with block...
	  origptr=origbound;
	  to->ptr=toptr;
	  orig->ptr=origptr;
	  gccurr_heaptop-=(unsigned INTPTR)(toptr-toptrinit);
	  return 0;
	}
      } while(!gcmarktbl[arrayoffset]);
      origptr=CONVERTTABLEINDEXTOPTR(arrayoffset);
    }

    //Scan more carefully next
    objlength=getMarkedLength(origptr);

    if (objlength!=NOTMARKED) {
      unsigned int length=ALIGNSIZETOBYTES(objlength);
      void *endtoptr=toptr+length;
      if (endtoptr>tobound) {
	gccurr_heaptop-=(unsigned INTPTR)(toptr-toptrinit);
	to->ptr=tobound;
	orig->ptr=origptr;
	return length;
      }
      //good to move objects and update pointers
      gcmappingtbl[OBJMAPPINGINDEX(origptr)]=toptr;
      origptr+=length;
      toptr=endtoptr;
    } else
      origptr+=ALIGNMENTSIZE;
  }
}

void compact() {
  BAMBOO_ASSERT(COMPACTPHASE == gc_status_info.gcphase);
  BAMBOO_CACHE_MF();
  
  // initialize structs for compacting
  struct moveHelper orig={0,NULL,NULL,0,NULL,0,0,0,0};
  struct moveHelper to={0,NULL,NULL,0,NULL,0,0,0,0};
  initOrig_Dst(&orig, &to);

  CACHEADAPT_SAMPLING_DATA_REVISE_INIT(&orig, &to);

  compacthelper(&orig, &to);
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
    //send start compact messages to all cores
    if(i != STARTUPCORE) {
      send_msg_2(i, GCSTARTCOMPACT, numblockspercore);
    } else {
      gcblock2fill = numblockspercore;
    }
  }
  BAMBOO_CACHE_MF();
  GCPROFILE_ITEM();
  // compact phase
  compact();
  /* wait for all cores to finish compacting */

  while(gc_checkCoreStatus())
    ;

  GCPROFILE_ITEM();

  GC_PRINTF("compact phase finished \n");
}

#endif // MULTICORE_GC
