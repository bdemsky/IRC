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

  return NULL;
}

INLINE void * searchBlock4Mem_global(int* tofindb, 
                                     int* totest,
                                     int isize,
                                     int * allocsize) {

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
    mem=searchBlock4Mem(&tofindb,&totest,core2test[gccorenum][k],isize,allocsize,(k==0)?0:((GCNUMBLOCK/NUMCORES4GC)>>LOCALMEMRESERVATION));
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
    mem=searchBlock4Mem(&tofindb,&totest,core2test[gccorenum][k],isize,allocsize,(k==0)?0:((GCNUMBLOCK/NUMCORES4GC)>>LOCALMEMRESERVATION));
    if(mem!=NULL) {
      return mem;
    }
  }

  // try allocate globally
  mem=globalmalloc_I(coren,isize,allocsize);
  return mem;
} 
#endif 

// Allocate all the memory chunks globally, do not consider the host cores
// When all the shared memory are used up, start gc.
void * globalmalloc_I(int coren,
                      int isize,
                      int * allocsize) {
  return NULL;
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
