#ifndef MULTICORE_MEM_H
#define MULTICORE_MEM_H
#include "Queue.h"
#include "SimpleHash.h"

#ifndef INTPTR
#ifdef BIT64
#define INTPTR long
#define INTPTRSHIFT 3
#else
#define INTPTR int
#define INTPTRSHIFT 2
#endif
#endif

#ifndef bool
#define bool int
#define true 1
#define false 0
#endif

// data structures for shared memory allocation
#ifdef TILERA_BME
#define BAMBOO_BASE_VA 0xd000000
#elif defined TILERA_ZLINUX
#ifdef MULTICORE_GC
#define BAMBOO_BASE_VA 0xd000000
#endif // MULTICORE_GC
#endif // TILERA_BME

#ifdef BAMBOO_MEMPROF
#define GC_BAMBOO_NUMCORES 56
#else
#define GC_BAMBOO_NUMCORES 62
#endif

#ifdef GC_DEBUG
#include "structdefs.h"
#define BAMBOO_NUM_BLOCKS (NUMCORES4GC*(2+1)+3)
#define BAMBOO_PAGE_SIZE (64 * 64)
#define BAMBOO_SMEM_SIZE (BAMBOO_PAGE_SIZE)
#define BAMBOO_SHARED_MEM_SIZE ((BAMBOO_SMEM_SIZE) *(BAMBOO_NUM_BLOCKS))

#elif defined GC_CACHE_ADAPT
#ifdef GC_LARGESHAREDHEAP
#define BAMBOO_NUM_BLOCKS ((GC_BAMBOO_NUMCORES)*(2+24))
#else
#define BAMBOO_NUM_BLOCKS ((GC_BAMBOO_NUMCORES)*(2+14))
#endif
#define BAMBOO_PAGE_SIZE (64 * 1024) // 64K
#ifdef GC_LARGEPAGESIZE
#define BAMBOO_PAGE_SIZE (4 * 64 * 1024)
#define BAMBOO_SMEM_SIZE (4 * (BAMBOO_PAGE_SIZE))
#elif defined GC_SMALLPAGESIZE
#define BAMBOO_SMEM_SIZE (BAMBOO_PAGE_SIZE)
#elif defined GC_SMALLPAGESIZE2
#define BAMBOO_PAGE_SIZE (16 * 1024)  // (4096)
#define BAMBOO_SMEM_SIZE (BAMBOO_PAGE_SIZE)
#elif defined GC_LARGEPAGESIZE2
#define BAMBOO_PAGE_SIZE (4 * 64 * 1024) // 64K
#define BAMBOO_SMEM_SIZE ((BAMBOO_PAGE_SIZE))
#else
#define BAMBOO_SMEM_SIZE (4 * (BAMBOO_PAGE_SIZE))
#endif // GC_LARGEPAGESIZE
#define BAMBOO_SHARED_MEM_SIZE ((BAMBOO_SMEM_SIZE) * (BAMBOO_NUM_BLOCKS))

#else // GC_DEBUG
#ifdef GC_LARGESHAREDHEAP
#define BAMBOO_NUM_BLOCKS ((GC_BAMBOO_NUMCORES)*(2+2))
#elif defined GC_LARGESHAREDHEAP2
#define BAMBOO_NUM_BLOCKS ((GC_BAMBOO_NUMCORES)*(2+2))
#else
#define BAMBOO_NUM_BLOCKS ((GC_BAMBOO_NUMCORES)*(2+3)) //(15 * 1024) //(64 * 4 * 0.75) //(1024 * 1024 * 3.5)  3G
#endif
#ifdef GC_LARGEPAGESIZE
#define BAMBOO_PAGE_SIZE (4 * 1024 * 1024)  // (4096)
#define BAMBOO_SMEM_SIZE (BAMBOO_PAGE_SIZE)
#elif defined GC_SMALLPAGESIZE
#define BAMBOO_PAGE_SIZE (256 * 1024)  // (4096)
#define BAMBOO_SMEM_SIZE (BAMBOO_PAGE_SIZE)
#elif defined GC_SMALLPAGESIZE2
#define BAMBOO_PAGE_SIZE (64 * 1024)  // (4096)
#define BAMBOO_SMEM_SIZE (BAMBOO_PAGE_SIZE)
#else
#define BAMBOO_PAGE_SIZE (1024 * 1024)  // (4096)
#define BAMBOO_SMEM_SIZE (BAMBOO_PAGE_SIZE)
#endif // GC_LARGEPAGESIZE
#define BAMBOO_SHARED_MEM_SIZE ((BAMBOO_SMEM_SIZE) * (BAMBOO_NUM_BLOCKS)) //(1024 * 1024 * 240) //((unsigned long long int)(3.0 * 1024 * 1024 * 1024)) // 3G 
#endif // GC_DEBUG

#ifdef MULTICORE_GC
volatile bool gc_localheap_s;
#include "multicoregarbage.h"

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
  INTPTR ptr;
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

// table recording the number of allocated bytes on each block
// Note: this table resides on the bottom of the shared heap for all cores
//       to access
volatile int * bamboo_smemtbl;
volatile int bamboo_free_block;
unsigned int bamboo_reserved_smem; // reserved blocks on the top of the shared 
                                   // heap e.g. 20% of the heap and should not 
								   // be allocated otherwise gc is invoked
volatile INTPTR bamboo_smem_zero_top;
#define BAMBOO_SMEM_ZERO_UNIT_SIZE (4 * 1024) // 4KB
#else
//volatile mspace bamboo_free_msp;
INTPTR bamboo_free_smemp;
int bamboo_free_smem_size;
#endif // MULTICORE_GC
volatile bool smemflag;
volatile INTPTR bamboo_cur_msp;
volatile int bamboo_smem_size;

#endif