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

// Only allocate local mem chunks to each core.
// If a core has used up its local shared memory, start gc.
void * localmalloc_I(int coren,
                     int isize,
                     int * allocsize) {
  void * mem=globalmalloc_I(coren,isize,allocsize);
  return mem;
} 

#ifdef SMEMF
// Allocate the local shared memory to each core with the highest priority,
// if a core has used up its local shared memory, try to allocate the 
// shared memory that belong to its neighbours, if also failed, start gc.
void * fixedmalloc_I(int coren,
                     int isize,
                     int * allocsize) {
  void * mem=globalmalloc_I(coren,isize,allocsize);
  return mem;
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
  void * mem=globalmalloc_I(coren,isize,allocsize);
  return mem;
} 
#endif 

// Allocate all the memory chunks globally, do not consider the host cores
// When all the shared memory are used up, start gc.
void * globalmalloc_I(int coren, unsigned INTPTR memcheck, int * allocsize) {
  block_t firstfree=NOFREEBLOCK;
  block_t lowestblock=allocationinfo.lowestfreeblock;

  for(block_t searchblock=lowestblock;searchblock<GCNUMBLOCK;searchblock++) {
    struct blockrecord * block=&allocationinfo.blocktable[searchblock];
    if (block->status==BS_FREE) {
      if(firstfree==NOFREEBLOCK)
	firstfree=searchblock;
      unsigned INTPTR freespace=block->freespace&~BAMBOO_CACHE_LINE_MASK;
      if (freespace>=memcheck) {
	//we have a block
	//mark block as used
	block->status=BS_USED;
	void *blockptr=OFFSET2BASEVA(searchblock)+gcbaseva;
	unsigned INTPTR usedspace=((block->usedspace-1)&~BAMBOO_CACHE_LINE_MASK)+BAMBOO_CACHE_LINE_SIZE;
	allocationinfo.lowestfreeblock=firstfree;
	void *startaddr=blockptr+usedspace;
	*allocsize=freespace;
	return startaddr;
      }
    }
  }
  return NULL;
} 

void * smemalloc(int coren, int isize, int * allocsize) {
  BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
  void *retval=smemalloc_I(coren, isize, allocsize);
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
  //  tprintf("Handing core %u memory block %x of size %u\n", coren, mem, *allocsize);
  if(mem == NULL) {
    // no enough shared global memory
    // trigger gc
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
