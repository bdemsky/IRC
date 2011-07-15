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

#define BAMBOO_SHARED_RUNTIME_PAGE_SIZE ((unsigned int)(1<<24))  //16M

// include the header file that defines the BAMBOO_NUM_BLOCKS, BAMBOO_PAGE_SIZE, BAMBOO_PAGE_SIZE_BITS and BAMBOO_SMEM_SIZE
#include "multicorememsize.h"
#define BAMBOO_SHARED_MEM_SIZE ((unsigned int)((BAMBOO_SMEM_SIZE) * (BAMBOO_NUM_BLOCKS)))

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
