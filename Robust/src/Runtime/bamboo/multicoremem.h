#ifndef BAMBOO_MULTICORE_MEM_H
#define BAMBOO_MULTICORE_MEM_H
#include "multicore.h"
#include "Queue.h"
#include "SimpleHash.h"

// data structures for shared memory allocation
#ifdef TILERA_BME
#ifdef MGC
#define BAMBOO_BASE_VA 0x600000  
#else 
#define BAMBOO_BASE_VA 0xd000000
#endif
#elif defined TILERA_ZLINUX
#ifdef MULTICORE_GC
#ifdef MGC
#define BAMBOO_BASE_VA 0x600000 
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
#define BAMBOO_NUM_BLOCKS (NUMCORES4GC*(2+1)+3)
#define BAMBOO_PAGE_SIZE (64 * 64)
#define BAMBOO_SMEM_SIZE ((unsigned int)(BAMBOO_PAGE_SIZE))
#define BAMBOO_SHARED_MEM_SIZE ((unsigned int)((BAMBOO_SMEM_SIZE) *(BAMBOO_NUM_BLOCKS)))

#elif defined GC_CACHE_ADAPT
#ifdef GC_LARGESHAREDHEAP
#define BAMBOO_NUM_BLOCKS ((unsigned int)((GC_BAMBOO_NUMCORES)*(2+24)))
#elif defined MGC
#define BAMBOO_NUM_BLOCKS ((unsigned int)((GC_BAMBOO_NUMCORES)*(72))) // 72M per core
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
#define BAMBOO_NUM_BLOCKS ((unsigned int)((GC_BAMBOO_NUMCORES)*(2+2)))
#elif defined GC_LARGESHAREDHEAP2
#define BAMBOO_NUM_BLOCKS ((unsigned int)((GC_BAMBOO_NUMCORES)*(2+2)))
#elif defined MGC
#define BAMBOO_NUM_BLOCKS ((unsigned int)((GC_BAMBOO_NUMCORES)*72)) // 72M per core
#else
#define BAMBOO_NUM_BLOCKS ((unsigned int)((GC_BAMBOO_NUMCORES)*(2+3))) //(15 * 1024) //(64 * 4 * 0.75) //(1024 * 1024 * 3.5)  3G
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

#ifdef MULTICORE_GC
#ifdef GC_SMALLPAGESIZE
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

volatile bool gc_localheap_s;

typedef enum {
  SMEMLOCAL = 0x0,// 0x0, using local mem only
  SMEMFIXED,      // 0x1, use local mem in lower address space(1 block only)
                  //      and global mem in higher address space
  SMEMMIXED,      // 0x2, like FIXED mode but use a threshold to control
  SMEMGLOBAL,     // 0x3, using global mem only
  SMEMEND
} SMEMSTRATEGY;

SMEMSTRATEGY bamboo_smem_mode; //-DSMEML: LOCAL; -DSMEMF: FIXED;
                               //-DSMEMM: MIXED; -DSMEMG: GLOBAL;

struct freeMemItem {
  unsigned int ptr;
  int size;
  int startblock;
  int endblock;
  struct freeMemItem * next;
};

struct freeMemList {
  struct freeMemItem * head;
  struct freeMemItem * backuplist; // hold removed freeMemItem for reuse;
                                   // only maintain 1 freemMemItem
};

// Zero out the remaining bamboo_cur_msp. Only zero out the first 4 bytes 
// of the remaining memory
#define BAMBOO_CLOSE_CUR_MSP() \
  { \
    if((bamboo_cur_msp!=0)&&(bamboo_smem_zero_top==bamboo_cur_msp) \
        &&(bamboo_smem_size>0)) { \
      *((int *)bamboo_cur_msp) = 0; \
    } \
  }

// table recording the number of allocated bytes on each block
// Note: this table resides on the bottom of the shared heap for all cores
//       to access
volatile unsigned int * bamboo_smemtbl;
#ifdef GC_TBL_DEBUG
// the length of the bamboo_smemtbl is gcnumblock
#endif
volatile unsigned int bamboo_free_block;
unsigned int bamboo_reserved_smem; // reserved blocks on the top of the shared 
                                   // heap e.g. 20% of the heap and should not 
								   // be allocated otherwise gc is invoked
volatile unsigned int bamboo_smem_zero_top;
#define BAMBOO_SMEM_ZERO_UNIT_SIZE ((unsigned int)(4 * 1024)) // 4KB
#else
//volatile mspace bamboo_free_msp;
unsigned int bamboo_free_smemp;
int bamboo_free_smem_size;
#endif // MULTICORE_GC
volatile bool smemflag;
volatile unsigned int bamboo_cur_msp;
volatile int bamboo_smem_size;

#endif // BAMBOO_MULTICORE_MEM_H
