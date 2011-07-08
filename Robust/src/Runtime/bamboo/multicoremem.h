#ifndef BAMBOO_MULTICORE_MEM_H
#define BAMBOO_MULTICORE_MEM_H
#include "multicore.h"
#include "Queue.h"
#include "SimpleHash.h"

// data structures for shared memory allocation
#ifdef TILERA_BME
#ifdef MGC
#define BAMBOO_BASE_VA 0x1000000  
#else 
#define BAMBOO_BASE_VA 0xd000000
#endif
#elif defined TILERA_ZLINUX
#ifdef MULTICORE_GC
#ifdef MGC
#define BAMBOO_BASE_VA 0x1000000 
#else 
#define BAMBOO_BASE_VA 0xd000000
#endif
#endif // MULTICORE_GC
#endif // TILERA_BME

#ifdef BAMBOO_MEMPROF
#define GC_BAMBOO_NUMCORES 56
#else
#ifdef MGC
#define GC_BAMBOO_NUMCORES (NUMCORES)
#else
#define GC_BAMBOO_NUMCORES 62
#endif
#endif

#ifdef GC_DEBUG
#include "structdefs.h"
#define BAMBOO_NUM_BLOCKS (NUMCORES4GC*(2+3))
#define BAMBOO_PAGE_SIZE (64 * 64)
#define BAMBOO_SMEM_SIZE ((unsigned int)(BAMBOO_PAGE_SIZE))
#define BAMBOO_SHARED_MEM_SIZE ((unsigned int)((BAMBOO_SMEM_SIZE) *(BAMBOO_NUM_BLOCKS)))

#elif defined GC_CACHE_ADAPT
#ifdef GC_LARGESHAREDHEAP
#define BAMBOO_NUM_BLOCKS ((unsigned int)((GC_BAMBOO_NUMCORES)*(2+24)))
#elif defined MGC
#define BAMBOO_NUM_BLOCKS ((unsigned int)((GC_BAMBOO_NUMCORES)*72)) // 72M per core
#else
#define BAMBOO_NUM_BLOCKS ((unsigned int)((GC_BAMBOO_NUMCORES)*(2+14)))
#endif
#define BAMBOO_PAGE_SIZE ((unsigned int)(64 * 1024)) // 64K
#ifdef GC_LARGEPAGESIZE
#define BAMBOO_PAGE_SIZE ((unsigned int)(4 * 64 * 1024))
#define BAMBOO_SMEM_SIZE ((unsigned int)(4 * (BAMBOO_PAGE_SIZE)))
#elif defined GC_SMALLPAGESIZE
#define BAMBOO_SMEM_SIZE ((unsigned int)(BAMBOO_PAGE_SIZE))
#elif defined GC_SMALLPAGESIZE2
//#define BAMBOO_PAGE_SIZE ((unsigned int)(16 * 1024))  // (4096)
#define BAMBOO_SMEM_SIZE ((unsigned int)(BAMBOO_PAGE_SIZE))
#elif defined GC_LARGEPAGESIZE2
#define BAMBOO_PAGE_SIZE ((unsigned int)(4 * 64 * 1024)) // 64K
#define BAMBOO_SMEM_SIZE ((unsigned int)(BAMBOO_PAGE_SIZE))
#elif defined MGC
#define BAMBOO_SMEM_SIZE ((unsigned int)(16*(BAMBOO_PAGE_SIZE)))  // 1M
#else
#define BAMBOO_SMEM_SIZE ((unsigned int)(4 * (BAMBOO_PAGE_SIZE)))
#endif // GC_LARGEPAGESIZE
#define BAMBOO_SHARED_MEM_SIZE ((unsigned int)((BAMBOO_SMEM_SIZE) * (BAMBOO_NUM_BLOCKS)))

#else // GC_DEBUG
#ifdef GC_LARGESHAREDHEAP
#define BAMBOO_NUM_BLOCKS ((unsigned int)((GC_BAMBOO_NUMCORES)*(2+5)))
#elif defined MGC
#define BAMBOO_NUM_BLOCKS ((unsigned int)((GC_BAMBOO_NUMCORES)*72)) // 72M per core
#else
#define BAMBOO_NUM_BLOCKS ((unsigned int)((GC_BAMBOO_NUMCORES)*(2+2))) //(15 * 1024) //(64 * 4 * 0.75) //(1024 * 1024 * 3.5)  3G
#endif
#ifdef GC_LARGEPAGESIZE
#define BAMBOO_PAGE_SIZE ((unsigned int)(4 * 1024 * 1024))  // (4096)
#define BAMBOO_SMEM_SIZE ((unsigned int)(BAMBOO_PAGE_SIZE))
#elif defined GC_SMALLPAGESIZE
#define BAMBOO_PAGE_SIZE ((unsigned int)(256 * 1024))  // (4096)
#define BAMBOO_SMEM_SIZE ((unsigned int)(BAMBOO_PAGE_SIZE))
#elif defined GC_SMALLPAGESIZE2
#define BAMBOO_PAGE_SIZE ((unsigned int)(256 * 1024))  // (4096) 64
#define BAMBOO_SMEM_SIZE ((unsigned int)(BAMBOO_PAGE_SIZE))
#else
#define BAMBOO_PAGE_SIZE ((unsigned int)(1024 * 1024))  // (4096)
#define BAMBOO_SMEM_SIZE ((unsigned int)(BAMBOO_PAGE_SIZE))
#endif // GC_LARGEPAGESIZE
#define BAMBOO_SHARED_MEM_SIZE ((unsigned int)((BAMBOO_SMEM_SIZE) * (BAMBOO_NUM_BLOCKS))) //(1024 * 1024 * 240) //((unsigned long long int)(3.0 * 1024 * 1024 * 1024)) // 3G 
#endif // GC_DEBUG

#if defined(MULTICORE_GC)||defined(PMC_GC)
#if defined(GC_SMALLPAGESIZE)||defined(PMC_GC)
// memory for globals
#define BAMBOO_GLOBAL_DEFS_SIZE (1024 * 1024)
#define BAMBOO_GLOBAL_DEFS_PRIM_SIZE (1024 * 512)
// memory for thread queue
#define BAMBOO_THREAD_QUEUE_SIZE (1024 * 1024)
#else
// memory for globals
#define BAMBOO_GLOBAL_DEFS_SIZE (BAMBOO_SMEM_SIZE)
#define BAMBOO_GLOBAL_DEFS_PRIM_SIZE (BAMBOO_SMEM_SIZE/2)
// memory for thread queue
#define BAMBOO_THREAD_QUEUE_SIZE (BAMBOO_SMEM_SIZE) // (45 * 16 * 1024)
#endif // GC_SMALLPAGESIZE

//keeps track of the top address that has been zero'd by the allocator
volatile void * bamboo_smem_zero_top;
volatile unsigned int totalbytestozero;

//BAMBOO_SMEM_ZERO_UNIT_SIZE must evenly divide the page size and be a
//power of two(we rely on both in the allocation function)
#define BAMBOO_SMEM_ZERO_UNIT_SIZE 4096
#else
//This is for memory allocation with no garbage collection
unsigned int bamboo_free_smemp;
int bamboo_free_smem_size;
#endif // MULTICORE_GC
//This flag indicates that a memory request was services
volatile bool smemflag;
//Pointer to new block of memory after request
volatile void * bamboo_cur_msp;
//Number of bytes in new block of memory
volatile unsigned INTPTR bamboo_smem_size;

void * localmalloc_I(int coren, unsigned INTPTR memcheck, unsigned INTPTR * allocsize);
void * fixedmalloc_I(int coren, unsigned INTPTR memcheck, unsigned INTPTR * allocsize);
void * mixedmalloc_I(int coren, unsigned INTPTR isize, unsigned INTPTR * allocsize);
void * globalmalloc_I(int coren, unsigned INTPTR memcheck, unsigned INTPTR * allocsize);
void * smemalloc(int coren, unsigned INTPTR isize, unsigned INTPTR * allocsize);
void * smemalloc_I(int coren, unsigned INTPTR isize, unsigned INTPTR * allocsize);


#endif // BAMBOO_MULTICORE_MEM_H
