#ifdef MULTICORE
#include "runtime_arch.h"
#include "multicoreruntime.h"

#ifdef MULTICORE_GC
#include "multicoregarbage.h"
#include "multicorehelper.h"
#include "multicoremem_helper.h"

INLINE void * mallocmem(int tofindb,
                        int totest,
                        int size,
                        int * allocsize) {
  void * mem = NULL;
  // find suitable block
  mem=gcbaseva+bamboo_smemtbl[tofindb]+OFFSET2BASEVA(tofindb);
  *allocsize = size;
  // set bamboo_smemtbl
  for(int i = tofindb; i <= totest; i++) {
    bamboo_smemtbl[i]=BLOCKSIZE(i<NUMCORES4GC);
  }
  if(tofindb == bamboo_free_block) {
    bamboo_free_block = totest+1;
  }
  return mem;
}

// 06/07/11 add a parameter minremain, it specifies the minimum number of 
// blocks to leave for each core for local allocation. 
INLINE void * searchBlock4Mem(int* tofindb, 
                              int* totest,
                              int gccorenum,
                              int isize,
                              int * allocsize,
                              int minremain) {
  int i=0;
  int j=0;
  int size = 0;
  int bound = BAMBOO_SMEM_SIZE_L;
  int freeblocks=(gcnumblock-bamboo_reserved_smem-1)/NUMCORES4GC+1;
  while((*totest<(gcnumblock-bamboo_reserved_smem))&&(freeblocks>minremain)) {
    bound = BLOCKSIZE(*totest<NUMCORES4GC);
    int nsize = bamboo_smemtbl[*totest];
    if((nsize==bound)||((nsize != 0)&&(*totest != *tofindb))) {
      // a fully/partially occupied partition, can not be appended 
      //the last continuous block is not big enough,check the next local block
      j+=i;
      i=(i+1)&1;

      *tofindb=*totest=gc_core2block[2*gccorenum+i]+(NUMCORES4GC*2)*j;
      freeblocks--;
    } else {
      // an empty block or a partially occupied block that can be set as the 
      // first block
      if(*totest == *tofindb) {
        // the first partition
        size = bound - nsize;
      } else if(nsize == 0) {
        // an empty partition, can be appended
        size += bound;
      } 
      if(size >= isize) {
        // have enough space in the block, malloc
        return mallocmem(*tofindb, *totest, size, allocsize);
      } else {
        // no enough space yet, try to append next continuous block
        *totest = *totest + 1;
      }  
    }
  }
  return NULL;
}

INLINE void * searchBlock4Mem_global(int* tofindb, 
                                     int* totest,
                                     int isize,
                                     int * allocsize) {
  int size = 0;
  int bound = BAMBOO_SMEM_SIZE_L;
  while(*totest<(gcnumblock-bamboo_reserved_smem)) {
    bound = BLOCKSIZE(*totest<NUMCORES4GC);
    int nsize = bamboo_smemtbl[*totest];
    if((nsize==bound)||((nsize != 0)&&(*totest != *tofindb))) {
      // a fully/partially occupied partition, can not be appended 
      // set the next block as a new start
      *totest = *totest+1;
      *tofindb = *totest;
    } else {
      // an empty block or a partially occupied block that can be set as the 
      // first block
      if(*totest == *tofindb) {
        // the first partition
        size = bound - nsize;
      } else if(nsize == 0) {
        // an empty partition, can be appended
        size += bound;
      } 
      if(size >= isize) {
        // have enough space in the block, malloc
        return mallocmem(*tofindb, *totest, size, allocsize);
      } else {
        // no enough space yet, try to append next continuous block
        *totest = *totest + 1;
      }  
    }
  }
  return NULL;
}

// Only allocate local mem chunks to each core.
// If a core has used up its local shared memory, start gc.
void * localmalloc_I(int coren,
                     int isize,
                     int * allocsize) {
  void * mem=NULL;
  int gccorenum=(coren<NUMCORES4GC)?(coren):(coren%NUMCORES4GC);
  int tofindb=gc_core2block[2*gccorenum];
  int totest=tofindb;
  mem=searchBlock4Mem(&tofindb,&totest,gccorenum,isize,allocsize,0);
  if(mem==NULL) {
    // no more local mem, do not find suitable block
    *allocsize=0;
  }
  return mem;
} 

#define LOCALMEMRESERVATION 2

#ifdef SMEMF
// Allocate the local shared memory to each core with the highest priority,
// if a core has used up its local shared memory, try to allocate the 
// shared memory that belong to its neighbours, if also failed, start gc.
void * fixedmalloc_I(int coren,
                     int isize,
                     int * allocsize) {
  void * mem;
  int k;
  int gccorenum=(coren<NUMCORES4GC)?(coren):(coren%NUMCORES4GC);
  int totest,tofindb;
  int bound=BAMBOO_SMEM_SIZE_L;
  int foundsmem=0;
  int size=0;
  for(k=0;k<NUM_CORES2TEST;k++) {
    if(core2test[gccorenum][k]==-1) {
      // try next neighbour
      continue;
    }
    tofindb=totest=gc_core2block[2*core2test[gccorenum][k]];
    mem=searchBlock4Mem(&tofindb,&totest,core2test[gccorenum][k],isize,allocsize,(k==0)?0:((gcnumblock/NUMCORES4GC)>>LOCALMEMRESERVATION));
    if(mem!=NULL) {
      return mem;
    }
  }
  // no more memory available on either coren or its neighbour cores
  *allocsize=0;
  return NULL;
} 
#endif 

#ifdef SMEMM
// Allocate the local shared memory to each core with the highest priority,
// if a core has used up its local shared memory, try to allocate the 
// shared memory that belong to its neighbours first, if failed, check 
// current memory allocation rate, if it has already reached the threshold,
// start gc, otherwise, allocate the shared memory globally.  If all the 
// shared memory has been used up, start gc.
void * mixedmalloc_I(int coren,
                     int isize,
                     int * allocsize) {
  void * mem;
  int k;
  int gccorenum=(coren<NUMCORES4GC)?(coren):(coren%NUMCORES4GC);
  int totest,tofindb;
  int size=0;
  for(k=0;k<NUM_CORES2TEST;k++) {
    if(core2test[gccorenum][k]==-1) {
      // try next neighbour
      continue;
    }
    tofindb=totest=gc_core2block[2*core2test[gccorenum][k]];
    mem=searchBlock4Mem(&tofindb,&totest,core2test[gccorenum][k],isize,allocsize,(k==0)?0:((gcnumblock/NUMCORES4GC)>>LOCALMEMRESERVATION));
    if(mem!=NULL) {
      gcmem_mixed_usedmem+=size;
      return mem;
    }
  }
  if(gcmem_mixed_usedmem>=gcmem_mixed_threshold) {
    // no more memory available on either coren or its neighbour cores
    *allocsize = 0;
    return NULL; 
  } else {
    // try allocate globally
    mem=globalmalloc_I(coren,isize,allocsize);
    if(mem!=NULL) {
      gcmem_mixed_usedmem+=size;
    }
    return mem;
  }
} 
#endif 

// Allocate all the memory chunks globally, do not consider the host cores
// When all the shared memory are used up, start gc.
void * globalmalloc_I(int coren,
                      int isize,
                      int * allocsize) {
  void * mem = NULL;
  int tofindb = bamboo_free_block;
  int totest = tofindb;
  if(tofindb > gcnumblock-1-bamboo_reserved_smem) {
    // Out of shared memory
    *allocsize = 0;
    return NULL;
  }
  mem=searchBlock4Mem_global(&tofindb, &totest, isize, allocsize);
  if(mem == NULL) {
    *allocsize = 0;
  }
  return mem;
} 

void * smemalloc(int coren, int isize, int * allocsize) {
  BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
  void *retval=smemalloc(coren, isize, allocsize);
  BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
  return retval;
}

// malloc from the shared memory
void * smemalloc_I(int coren, int isize, int * allocsize) {
#ifdef SMEML
  void *mem = localmalloc_I(coren, isize, allocsize);
#elif defined(SMEMF)
  void *mem = fixedmalloc_I(coren, isize, allocsize);
#elif defined(SMEMM)
  void *mem = mixedmalloc_I(coren, isize, allocsize);
#elif defined(SMEMG)
  void *mem = globalmalloc_I(coren, isize, allocsize);
#endif

  if(mem == NULL) {
    // no enough shared global memory
    // trigger gc
    *allocsize = 0;
    if(!gcflag) {
      gcflag = true;
      if(!gc_status_info.gcprocessing) {
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
    return NULL;
  }
  return mem;
}
#else
// malloc from the shared memory
void * smemalloc_I(int coren,
                   int size,
                   int * allocsize) {
  void * mem = NULL;
  int toallocate = (size>(BAMBOO_SMEM_SIZE)) ? (size) : (BAMBOO_SMEM_SIZE);
  if(toallocate > bamboo_free_smem_size) {
    // no enough mem
    mem = NULL;
  } else {
    mem = (void *)bamboo_free_smemp;
    bamboo_free_smemp = ((void*)bamboo_free_smemp) + toallocate;
    bamboo_free_smem_size -= toallocate;
  }
  *allocsize = toallocate;
  if(mem == NULL) {
    // no enough shared global memory
    *allocsize = 0;
    BAMBOO_EXIT();
  }
  return mem;
} 
#endif // MULTICORE_GC

#endif // MULTICORE
