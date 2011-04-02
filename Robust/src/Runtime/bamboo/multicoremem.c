#ifdef MULTICORE
#include "runtime_arch.h"
#include "multicoreruntime.h"

extern int corenum;

#ifdef MULTICORE_GC
#include "multicorehelper.h"

#ifdef SMEMF
#define NUM_CORES2TEST 5
#ifdef GC_1
int core2test[1][NUM_CORES2TEST] = {
  {0, -1, -1, -1, -1}
};
#elif defined GC_56
int core2test[56][NUM_CORES2TEST] = {
  { 0, -1,  7, -1,  1}, { 1, -1,  8,  0,  2}, { 2, -1,  9,  1,  3},
  { 3, -1, 10,  2,  4}, { 4, -1, 11,  3,  5}, { 5, -1, 12,  4,  6},
  { 6, -1, 13,  5, -1}, { 7,  0, 14, -1,  8}, { 8,  1, 15,  7,  9},
  { 9,  2, 16,  8, 10}, {10,  3, 17,  9, 11}, {11,  4, 18, 10, 12},
  {12,  5, 19, 11, 13}, {13,  6, 20, 12, -1}, {14,  7, 21, -1, 15},
  {15,  8, 22, 14, 16}, {16,  9, 23, 15, 17}, {17, 10, 24, 16, 18},
  {18, 11, 25, 17, 19}, {19, 12, 26, 18, 20}, {20, 13, 27, 19, -1},
  {21, 14, 28, -1, 22}, {22, 15, 29, 21, 23}, {23, 16, 30, 22, 24},
  {24, 17, 31, 23, 25}, {25, 18, 32, 24, 26}, {26, 19, 33, 25, 27},
  {27, 20, 34, 26, -1}, {28, 21, 35, -1, 29}, {29, 22, 36, 28, 30},
  {30, 23, 37, 29, 31}, {31, 24, 38, 30, 32}, {32, 25, 39, 31, 33},
  {33, 26, 40, 32, 34}, {34, 27, 41, 33, -1}, {35, 28, 42, -1, 36},
  {36, 29, 43, 35, 37}, {37, 30, 44, 36, 38}, {38, 31, 45, 37, 39},
  {39, 32, 46, 38, 40}, {40, 33, 47, 39, 41}, {41, 34, 48, 40, -1},
  {42, 35, 49, -1, 43}, {43, 36, 50, 42, 44}, {44, 37, 51, 43, 45},
  {45, 38, 52, 44, 46}, {46, 39, 53, 45, 47}, {47, 40, 54, 46, 48},
  {48, 41, 55, 47, -1}, {49, 42, -1, -1, 50}, {50, 43, -1, 49, 51},
  {51, 44, -1, 50, 52}, {52, 45, -1, 51, 53}, {53, 46, -1, 52, 54},
  {54, 47, -1, 53, 55}, {55, 48, -1, 54, -1}
};
#elif defined GC_62
int core2test[62][NUM_CORES2TEST] = {
  { 0, -1,  6, -1,  1}, { 1, -1,  7,  0,  2}, { 2, -1,  8,  1,  3},
  { 3, -1,  9,  2,  4}, { 4, -1, 10,  3,  5}, { 5, -1, 11,  4, -1},
  { 6,  0, 14, -1,  7}, { 7,  1, 15,  6,  8}, { 8,  2, 16,  7,  9},
  { 9,  3, 17,  8, 10}, {10,  4, 18,  9, 11}, {11,  5, 19, 10, 12},
  {12, -1, 20, 11, 13}, {13, -1, 21, 12, -1}, {14,  6, 22, -1, 15},
  {15,  7, 23, 14, 16}, {16,  8, 24, 15, 17}, {17,  9, 25, 16, 18},
  {18, 10, 26, 17, 19}, {19, 11, 27, 18, 20}, {20, 12, 28, 19, 21},
  {21, 13, 29, 28, -1}, {22, 14, 30, -1, 23}, {23, 15, 31, 22, 24},
  {24, 16, 32, 23, 25}, {25, 17, 33, 24, 26}, {26, 18, 34, 25, 27},
  {27, 19, 35, 26, 28}, {28, 20, 36, 27, 29}, {29, 21, 37, 28, -1},
  {30, 22, 38, -1, 31}, {31, 23, 39, 30, 32}, {32, 24, 40, 31, 33},
  {33, 25, 41, 32, 34}, {34, 26, 42, 33, 35}, {35, 27, 43, 34, 36},
  {36, 28, 44, 35, 37}, {37, 29, 45, 36, -1}, {38, 30, 46, -1, 39},
  {39, 31, 47, 38, 40}, {40, 32, 48, 39, 41}, {41, 33, 49, 40, 42},
  {42, 34, 50, 41, 43}, {43, 35, 51, 42, 44}, {44, 36, 52, 43, 45},
  {45, 37, 53, 44, -1}, {46, 38, 54, -1, 47}, {47, 39, 55, 46, 48},
  {48, 40, 56, 47, 49}, {49, 41, 57, 48, 50}, {50, 42, 58, 49, 51},
  {51, 43, 59, 50, 52}, {52, 44, 60, 51, 53}, {53, 45, 61, 52, -1},
  {54, 46, -1, -1, 55}, {55, 47, -1, 54, 56}, {56, 48, -1, 55, 57},
  {57, 49, -1, 56, 59}, {58, 50, -1, 57, 59}, {59, 51, -1, 58, 60},
  {60, 52, -1, 59, 61}, {61, 53, -1, 60, -1}
};
#endif // GC_1
#elif defined SMEMM
unsigned int gcmem_mixed_threshold = 0;
unsigned int gcmem_mixed_usedmem = 0;
#define NUM_CORES2TEST 13
#ifdef GC_1
int core2test[1][NUM_CORES2TEST] = {
  {0, -1, -1, -1, -1, -1, -1, -1, -1}
};
#elif defined GC_56
int core2test[56][NUM_CORES2TEST] = {
  { 0, -1, -1,  7,  1, -1, -1, -1, -1, 14,  8,  2, -1}, 
  { 1, -1,  0,  8,  2, -1, -1, -1,  7, 15,  9,  3, -1}, 
  { 2, -1,  1,  9,  3, -1, -1,  0,  8, 16, 10,  4, -1}, 
  { 3, -1,  2, 10,  4, -1, -1,  1,  9, 17, 11,  5, -1}, 
  { 4, -1,  3, 11,  5, -1, -1,  2, 10, 18, 12,  6, -1}, 
  { 5, -1,  4, 12,  6, -1, -1,  3, 11, 19, 13, -1, -1},
  { 6, -1,  5, 13, -1, -1, -1,  4, 12, 20, -1, -1, -1}, 
  { 7,  0, -1, 14,  8, -1, -1, -1, -1, 21, 15,  9,  1}, 
  { 8,  1,  7, 15,  9, -1,  0, -1, 14, 22, 16, 10,  2}, 
  { 9,  2,  8, 16, 10, -1,  1,  7, 15, 23, 17, 11,  3}, 
  {10,  3,  9, 17, 11, -1,  2,  8, 16, 24, 18, 12,  4}, 
  {11,  4, 10, 18, 12, -1,  3,  9, 17, 25, 19, 13,  5},
  {12,  5, 11, 19, 13, -1,  4, 10, 18, 26, 20, -1,  6}, 
  {13,  6, 12, 20, -1, -1,  5, 11, 19, 27, -1, -1, -1}, 
  {14,  7, -1, 21, 15,  0, -1, -1, -1, 28, 22, 16,  8}, 
  {15,  8, 14, 22, 16,  1,  7, -1, 21, 29, 23, 17,  9}, 
  {16,  9, 15, 23, 17,  2,  8, 14, 22, 30, 24, 18, 10}, 
  {17, 10, 16, 24, 18,  3,  9, 15, 23, 31, 25, 19, 11},
  {18, 11, 17, 25, 19,  4, 10, 16, 24, 32, 26, 20, 12}, 
  {19, 12, 18, 26, 20,  5, 11, 17, 25, 33, 27, -1, 13}, 
  {20, 13, 19, 27, -1,  6, 12, 18, 26, 34, -1, -1, -1}, 
  {21, 14, -1, 28, 22,  7, -1, -1, -1, 35, 29, 23, 15}, 
  {22, 15, 21, 29, 23,  8, 14, -1, 28, 36, 30, 24, 16}, 
  {23, 16, 22, 30, 24,  9, 15, 21, 29, 37, 31, 25, 17},
  {24, 17, 23, 31, 25, 10, 16, 22, 40, 38, 32, 26, 18}, 
  {25, 18, 24, 32, 26, 11, 17, 23, 31, 39, 33, 27, 19}, 
  {26, 19, 25, 33, 27, 12, 18, 24, 32, 40, 34, -1, 20}, 
  {27, 20, 26, 34, -1, 13, 19, 25, 33, 41, -1, -1, -1}, 
  {28, 21, -1, 35, 29, 14, -1, -1, -1, 42, 36, 30, 22}, 
  {29, 22, 28, 36, 30, 15, 21, -1, 35, 43, 37, 31, 23},
  {30, 23, 29, 37, 31, 16, 22, 28, 36, 44, 38, 32, 24}, 
  {31, 24, 30, 38, 32, 17, 23, 29, 37, 45, 39, 33, 25}, 
  {32, 25, 31, 39, 33, 18, 24, 30, 38, 46, 40, 34, 26}, 
  {33, 26, 32, 40, 34, 19, 25, 31, 39, 47, 41, -1, 27}, 
  {34, 27, 33, 41, -1, 20, 26, 32, 40, 48, -1, -1, -1}, 
  {35, 28, -1, 42, 36, 21, -1, -1, -1, 49, 43, 37, 29},
  {36, 29, 35, 43, 37, 22, 28, -1, 42, 50, 44, 38, 30}, 
  {37, 30, 36, 44, 38, 23, 29, 35, 43, 51, 45, 39, 31}, 
  {38, 31, 37, 45, 39, 24, 30, 36, 44, 52, 46, 40, 32}, 
  {39, 32, 38, 46, 40, 25, 31, 37, 45, 53, 47, 41, 33}, 
  {40, 33, 39, 47, 41, 26, 32, 38, 46, 54, 48, -1, 34}, 
  {41, 34, 40, 48, -1, 27, 33, 39, 47, 55, -1, -1, -1},
  {42, 35, -1, 49, 43, 28, -1, -1, -1, -1, 50, 44, 36}, 
  {43, 36, 42, 50, 44, 29, 35, -1, 49, -1, 51, 45, 37}, 
  {44, 37, 43, 51, 45, 30, 36, 42, 50, -1, 52, 46, 38}, 
  {45, 38, 44, 52, 46, 31, 37, 43, 51, -1, 53, 47, 39}, 
  {46, 39, 45, 53, 47, 32, 38, 44, 52, -1, 54, 48, 40}, 
  {47, 40, 46, 54, 48, 33, 39, 45, 53, -1, 55, -1, 41},
  {48, 41, 47, 55, -1, 34, 40, 46, 54, -1, -1, -1, -1}, 
  {49, 42, -1, -1, 50, 35, -1, -1, -1, -1, -1, 51, 43}, 
  {50, 43, 49, -1, 51, 36, 42, -1, -1, -1, -1, 52, 44}, 
  {51, 44, 50, -1, 52, 37, 43, 49, -1, -1, -1, 53, 45}, 
  {52, 45, 51, -1, 53, 38, 44, 50, -1, -1, -1, 54, 46}, 
  {53, 46, 52, -1, 54, 39, 45, 51, -1, -1, -1, 55, 47},
  {54, 47, 53, -1, 55, 40, 46, 52, -1, -1, -1, -1, 48}, 
  {55, 48, 54, -1, -1, 41, 47, 53, -1, -1, -1, -1, -1}
};
#elif defined GC_62
int core2test[62][NUM_CORES2TEST] = {
  { 0, -1, -1,  6,  1, -1, -1, -1, -1, 14,  7,  2, -1}, 
  { 1, -1,  0,  7,  2, -1, -1, -1,  6, 15,  8,  3, -1}, 
  { 2, -1,  1,  8,  3, -1, -1,  0,  7, 16,  9,  4, -1}, 
  { 3, -1,  2,  9,  4, -1, -1,  1,  8, 17, 10,  5, -1}, 
  { 4, -1,  3, 10,  5, -1, -1,  2,  9, 18,  11, -1, -1}, 
  { 5, -1,  4, 11, -1, -1, -1,  3, 10, 19, 12, -1, -1},
  { 6,  0, -1, 14,  7, -1, -1, -1, -1, 22, 15,  8,  1}, 
  { 7,  1,  6, 15,  8, -1,  0, -1, 14, 23, 16,  9,  2}, 
  { 8,  2,  7, 16,  9, -1,  1,  6, 15, 24, 17, 10,  3}, 
  { 9,  3,  8, 17, 10, -1,  2,  7, 16, 25, 18, 11,  4}, 
  {10,  4,  9, 18, 11, -1,  3,  8, 17, 26, 19, 12,  5}, 
  {11,  5, 10, 19, 12, -1,  4,  9, 18, 27, 20, 13, -1},
  {12, -1, 11, 20, 13, -1,  5, 10, 19, 28, 21, -1, -1}, 
  {13, -1,  12, 21, -1, -1, -1, 11, 20, 29, -1, -1, -1}, 
  {14,  6, -1, 22, 15,  0, -1, -1, -1, 30, 23, 16,  7}, 
  {15,  7, 14, 23, 16,  1,  6, -1, 22, 31, 24, 17,  8}, 
  {16,  8, 15, 24, 17,  2,  7, 14, 23, 32, 25, 18,  9}, 
  {17,  9, 16, 25, 18,  3,  8, 15, 24, 33, 26, 19, 10},
  {18, 10, 17, 26, 19,  4,   9, 16, 25, 34, 27, 20, 11}, 
  {19, 11, 18, 27, 20,  5, 10, 17, 26, 35, 28, 21, 12}, 
  {20, 12, 19, 28, 21, -1, 11, 18, 27, 36, 29, -1, 13}, 
  {21, 13, 28, 29, -1, -1, 12, 19, 28, 37, -1, -1, -1}, 
  {22, 14, -1, 30, 23,  6, -1, -1, -1, 38, 31, 24, 15}, 
  {23, 15, 22, 31, 24,  7, 14, -1, 30, 39, 32, 25, 16},
  {24, 16, 23, 32, 25,  8, 15, 22, 31, 40, 33, 26, 17}, 
  {25, 17, 24, 33, 26,  9, 16, 23, 32, 41, 34, 27, 18}, 
  {26, 18, 25, 34, 27, 10, 17, 24, 33, 42, 35, 28, 19}, 
  {27, 19, 26, 35, 28, 11, 18, 25, 34, 43, 36, 29, 20}, 
  {28, 20, 27, 36, 29, 12, 19, 26, 35, 44, 37, -1, 21}, 
  {29, 21, 28, 37, -1, 13, 20, 27, 36, 45, -1, -1, -1},
  {30, 22, -1, 38, 31, 14, -1, -1, -1, 46, 39, 32, 23}, 
  {31, 23, 30, 39, 32, 15, 22, -1, 38, 47, 40, 33, 24}, 
  {32, 24, 31, 40, 33, 16, 23, 30, 39, 48, 41, 34, 25}, 
  {33, 25, 32, 41, 34, 17, 24, 31, 40, 49, 42, 35, 26}, 
  {34, 26, 33, 42, 35, 18, 25, 32, 41, 50, 43, 36, 27}, 
  {35, 27, 34, 43, 36, 19, 26, 33, 42, 51, 44, 37, 28},
  {36, 28, 35, 44, 37, 20, 27, 34, 43, 52, 45, -1, 29}, 
  {37, 29, 36, 45, -1, 21, 28, 35, 44, 53, -1, -1, -1}, 
  {38, 30, -1, 46, 39, 22, -1, -1, -1, 54, 47, 40, 31}, 
  {39, 31, 38, 47, 40, 23, 30, -1, 46, 55, 48, 41, 32}, 
  {40, 32, 39, 48, 41, 24, 31, 38, 47, 56, 49, 42, 33}, 
  {41, 33, 40, 49, 42, 25, 32, 39, 48, 57, 50, 43, 34},
  {42, 34, 41, 50, 43, 26, 33, 40, 49, 58, 51, 44, 35}, 
  {43, 35, 42, 51, 44, 27, 34, 41, 50, 59, 52, 45, 36}, 
  {44, 36, 43, 52, 45, 28, 35, 42, 51, 60, 53, -1, 37}, 
  {45, 37, 44, 53, -1, 29, 36, 43, 52, 61, -1, -1}, 
  {46, 38, -1, 54, 47, 30, -1, -1, -1, -1, 55, 48, 39}, 
  {47, 39, 46, 55, 48, 31, 38, -1, 54, -1, 56, 49, 40},
  {48, 40, 47, 56, 49, 32, 39, 46, 55 -1, 57, 50, 41}, 
  {49, 41, 48, 57, 50, 33, 40, 47, 56, -1, 58, 51, 42}, 
  {50, 42, 49, 58, 51, 34, 41, 48, 57, -1, 59, 52, 43}, 
  {51, 43, 50, 59, 52, 35, 42, 49, 58, -1, 60, 53, 44}, 
  {52, 44, 51, 60, 53, 36, 43, 50, 59, -1, 61, -1, 45}, 
  {53, 45, 52, 61, -1, 37, 44, 51, 60, -1, -1, -1, -1},
  {54, 46, -1, -1, 55, 38, -1, -1, -1, -1, -1, 56, 47}, 
  {55, 47, 54, -1, 56, 39, 46, -1, -1, -1, -1, 57, 48}, 
  {56, 48, 55, -1, 57, 40, 47, 54, -1, -1, -1, 58, 49}, 
  {57, 49, 56, -1, 58, 41, 48, 55, -1, -1, -1, 59, 50}, 
  {58, 50, 57, -1, 59, 42, 49, 56, -1, -1, -1, 60, 51}, 
  {59, 51, 58, -1, 60, 43, 50, 57, -1, -1, -1, 61, 52},
  {60, 52, 59, -1, 61, 44, 51, 58, -1, -1, -1, -1, 53}, 
  {61, 53, 60, -1, -1, 45, 52, 59, -1, -1, -1, -1, -1}
};
#endif // GC_1
#endif // SMEMF

INLINE void setupsmemmode(void) {
#ifdef SMEML
  // Only allocate local mem chunks to each core.
  // If a core has used up its local shared memory, start gc.
  bamboo_smem_mode = SMEMLOCAL;
#elif defined SMEMF
  // Allocate the local shared memory to each core with the highest priority,
  // if a core has used up its local shared memory, try to allocate the 
  // shared memory that belong to its neighbours, if also failed, start gc.
  bamboo_smem_mode = SMEMFIXED;
#elif defined SMEMM
  // Allocate the local shared memory to each core with the highest priority,
  // if a core has used up its local shared memory, try to allocate the 
  // shared memory that belong to its neighbours first, if failed, check 
  // current memory allocation rate, if it has already reached the threshold,
  // start gc, otherwise, allocate the shared memory globally.  If all the 
  // shared memory has been used up, start gc.
  bamboo_smem_mode = SMEMMIXED;
#elif defined SMEMG
  // Allocate all the memory chunks globally, do not consider the host cores
  // When all the shared memory are used up, start gc.
  bamboo_smem_mode = SMEMGLOBAL;
#else
  // defaultly using local mode
  bamboo_smem_mode = SMEMLOCAL;
#endif // SMEML
} // void setupsmemmode(void)

// Only allocate local mem chunks to each core.
// If a core has used up its local shared memory, start gc.
void * localmalloc_I(int coren,
                     int isize,
                     int * allocsize) {
  void * mem = NULL;
  int gccorenum = (coren < NUMCORES4GC) ? (coren) : (coren % NUMCORES4GC);
  int i = 0;
  int j = 0;
  int tofindb = gc_core2block[2*gccorenum+i]+(NUMCORES4GC*2)*j;
  int totest = tofindb;
  int bound = BAMBOO_SMEM_SIZE_L;
  int foundsmem = 0;
  int size = 0;
  do {
    bound = (totest < NUMCORES4GC) ? BAMBOO_SMEM_SIZE_L : BAMBOO_SMEM_SIZE;
    int nsize = bamboo_smemtbl[totest];
    bool islocal = true;
    if(nsize < bound) {
      bool tocheck = true;
      // have some space in the block
      if(totest == tofindb) {
		// the first partition
		size = bound - nsize;
      } else if(nsize == 0) {
		// an empty partition, can be appended
		size += bound;
      } else {
		// not an empty partition, can not be appended
		// the last continuous block is not big enough, go to check the next
		// local block
		islocal = true;
		tocheck = false;
      } // if(totest == tofindb) else if(nsize == 0) else ...
      if(tocheck) {
		if(size >= isize) {
		  // have enough space in the block, malloc
		  foundsmem = 1;
		  break;
		} else {
		  // no enough space yet, try to append next continuous block
		  islocal = false;
		}  // if(size > isize) else ...
      }  // if(tocheck)
    } // if(nsize < bound)
    if(islocal) {
      // no space in the block, go to check the next block
      i++;
      if(2==i) {
		i = 0;
		j++;
      }
      tofindb = totest = gc_core2block[2*gccorenum+i]+(NUMCORES4GC*2)*j;
    } else {
      totest += 1;
    }  // if(islocal) else ...
    if(totest > gcnumblock-1-bamboo_reserved_smem) {
      // no more local mem, do not find suitable block
      foundsmem = 2;
      break;
    }  // if(totest > gcnumblock-1-bamboo_reserved_smem) ...
  } while(true);

  if(foundsmem == 1) {
    // find suitable block
    mem = gcbaseva+bamboo_smemtbl[tofindb]+((tofindb<NUMCORES4GC) ?
          (BAMBOO_SMEM_SIZE_L*tofindb) : (BAMBOO_LARGE_SMEM_BOUND+
          (tofindb-NUMCORES4GC)*BAMBOO_SMEM_SIZE));
    *allocsize = size;
    // set bamboo_smemtbl
    for(i = tofindb; i <= totest; i++) {
      bamboo_smemtbl[i]=(i<NUMCORES4GC)?BAMBOO_SMEM_SIZE_L:BAMBOO_SMEM_SIZE;
    }
  } else if(foundsmem == 2) {
    // no suitable block
    *allocsize = 0;
  }

  return mem;
} // void * localmalloc_I(int, int, int *)

#ifdef SMEMF
// Allocate the local shared memory to each core with the highest priority,
// if a core has used up its local shared memory, try to allocate the 
// shared memory that belong to its neighbours, if also failed, start gc.
void * fixedmalloc_I(int coren,
                     int isize,
                     int * allocsize) {
  void * mem = NULL;
  int i = 0;
  int j = 0;
  int k = 0;
  int gccorenum = (coren < NUMCORES4GC) ? (coren) : (coren % NUMCORES4GC);
  int ii = 1;
  int tofindb = gc_core2block[2*core2test[gccorenum][k]+i]+(NUMCORES4GC*2)*j;
  int totest = tofindb;
  int bound = BAMBOO_SMEM_SIZE_L;
  int foundsmem = 0;
  int size = 0;
  do {
    bound = (totest < NUMCORES4GC) ? BAMBOO_SMEM_SIZE_L : BAMBOO_SMEM_SIZE;
    int nsize = bamboo_smemtbl[totest];
    bool islocal = true;
    if(nsize < bound) {
      bool tocheck = true;
      // have some space in the block
      if(totest == tofindb) {
		// the first partition
		size = bound - nsize;
      } else if(nsize == 0) {
		// an empty partition, can be appended
		size += bound;
      } else {
		// not an empty partition, can not be appended
		// the last continuous block is not big enough, go to check the next
		// local block
		islocal = true;
		tocheck = false;
      } // if(totest == tofindb) else if(nsize == 0) else ...
      if(tocheck) {
		if(size >= isize) {
		  // have enough space in the block, malloc
		  foundsmem = 1;
		  break;
		} else {
		  // no enough space yet, try to append next continuous block
		  // TODO may consider to go to next local block?
		  islocal = false;
		}  // if(size > isize) else ...
      }  // if(tocheck)
    } // if(nsize < bound)
    if(islocal) {
      // no space in the block, go to check the next block
      i++;
      if(2==i) {
		i = 0;
		j++;
      }
      tofindb=totest=
		gc_core2block[2*core2test[gccorenum][k]+i]+(NUMCORES4GC*2)*j;
    } else {
      totest += 1;
    }  // if(islocal) else ...
    if(totest > gcnumblock-1-bamboo_reserved_smem) {
      // no more local mem, do not find suitable block on local mem
	  // try to malloc shared memory assigned to the neighbour cores
	  do{
		k++;
		if(k >= NUM_CORES2TEST) {
		  // no more memory available on either coren or its neighbour cores
		  foundsmem = 2;
		  goto memsearchresult;
		}
	  } while(core2test[gccorenum][k] == -1);
	  i = 0;
	  j = 0;
	  tofindb=totest=
		gc_core2block[2*core2test[gccorenum][k]+i]+(NUMCORES4GC*2)*j;
    }  // if(totest > gcnumblock-1-bamboo_reserved_smem) ...
  } while(true);

memsearchresult:
  if(foundsmem == 1) {
    // find suitable block
    mem = gcbaseva+bamboo_smemtbl[tofindb]+((tofindb<NUMCORES4GC) ?
          (BAMBOO_SMEM_SIZE_L*tofindb) : (BAMBOO_LARGE_SMEM_BOUND+
          (tofindb-NUMCORES4GC)*BAMBOO_SMEM_SIZE));
    *allocsize = size;
    // set bamboo_smemtbl
    for(i = tofindb; i <= totest; i++) {
      bamboo_smemtbl[i]=(i<NUMCORES4GC)?BAMBOO_SMEM_SIZE_L:BAMBOO_SMEM_SIZE;
    }
  } else if(foundsmem == 2) {
    // no suitable block
    *allocsize = 0;
  }

  return mem;
} // void * fixedmalloc_I(int, int, int *)
#endif // #ifdef SMEMF

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
  void * mem = NULL;
  int i = 0;
  int j = 0;
  int k = 0;
  int gccorenum = (coren < NUMCORES4GC) ? (coren) : (coren % NUMCORES4GC);
  int ii = 1;
  int tofindb = gc_core2block[2*core2test[gccorenum][k]+i]+(NUMCORES4GC*2)*j;
  int totest = tofindb;
  int bound = BAMBOO_SMEM_SIZE_L;
  int foundsmem = 0;
  int size = 0;
  do {
    bound = (totest < NUMCORES4GC) ? BAMBOO_SMEM_SIZE_L : BAMBOO_SMEM_SIZE;
    int nsize = bamboo_smemtbl[totest];
    bool islocal = true;
    if(nsize < bound) {
      bool tocheck = true;
      // have some space in the block
      if(totest == tofindb) {
		// the first partition
		size = bound - nsize;
      } else if(nsize == 0) {
		// an empty partition, can be appended
		size += bound;
      } else {
		// not an empty partition, can not be appended
		// the last continuous block is not big enough, go to check the next
		// local block
		islocal = true;
		tocheck = false;
      } // if(totest == tofindb) else if(nsize == 0) else ...
      if(tocheck) {
		if(size >= isize) {
		  // have enough space in the block, malloc
		  foundsmem = 1;
		  break;
		} else {
		  // no enough space yet, try to append next continuous block
		  // TODO may consider to go to next local block?
		  islocal = false;
		}  // if(size > isize) else ...
      }  // if(tocheck)
    } // if(nsize < bound)
    if(islocal) {
      // no space in the block, go to check the next block
      i++;
      if(2==i) {
		i = 0;
		j++;
      }
      tofindb=totest=
		gc_core2block[2*core2test[gccorenum][k]+i]+(NUMCORES4GC*2)*j;
    } else {
      totest += 1;
    }  // if(islocal) else ...
    if(totest > gcnumblock-1-bamboo_reserved_smem) {
      // no more local mem, do not find suitable block on local mem
	  // try to malloc shared memory assigned to the neighbour cores
	  do{
		k++;
		if(k >= NUM_CORES2TEST) {
		  if(gcmem_mixed_usedmem >= gcmem_mixed_threshold) {
			// no more memory available on either coren or its neighbour cores
			foundsmem = 2;
			goto memmixedsearchresult;
		  } else {
			// try allocate globally
			mem = globalmalloc_I(coren, isize, allocsize);
			return mem;
		  }
		}
	  } while(core2test[gccorenum][k] == -1);
	  i = 0;
	  j = 0;
	  tofindb=totest=
		gc_core2block[2*core2test[gccorenum][k]+i]+(NUMCORES4GC*2)*j;
    }  // if(totest > gcnumblock-1-bamboo_reserved_smem) ...
  } while(true);

memmixedsearchresult:
  if(foundsmem == 1) {
    // find suitable block
    mem = gcbaseva+bamboo_smemtbl[tofindb]+((tofindb<NUMCORES4GC) ?
          (BAMBOO_SMEM_SIZE_L*tofindb) : (BAMBOO_LARGE_SMEM_BOUND+
          (tofindb-NUMCORES4GC)*BAMBOO_SMEM_SIZE));
    *allocsize = size;
    // set bamboo_smemtbl
    for(i = tofindb; i <= totest; i++) {
      bamboo_smemtbl[i]=(i<NUMCORES4GC)?BAMBOO_SMEM_SIZE_L:BAMBOO_SMEM_SIZE;
    }
	gcmem_mixed_usedmem += size;
	if(tofindb == bamboo_free_block) {
      bamboo_free_block = totest+1;
    }
  } else if(foundsmem == 2) {
    // no suitable block
    *allocsize = 0;
  }

  return mem;
} // void * mixedmalloc_I(int, int, int *)
#endif // #ifdef SMEMM

// Allocate all the memory chunks globally, do not consider the host cores
// When all the shared memory are used up, start gc.
void * globalmalloc_I(int coren,
                      int isize,
                      int * allocsize) {
  void * mem = NULL;
  int tofindb = bamboo_free_block;       //0;
  int totest = tofindb;
  int bound = BAMBOO_SMEM_SIZE_L;
  int foundsmem = 0;
  int size = 0;
  if(tofindb > gcnumblock-1-bamboo_reserved_smem) {
	// Out of shared memory
    *allocsize = 0;
    return NULL;
  }
  do {
    bound = (totest < NUMCORES4GC) ? BAMBOO_SMEM_SIZE_L : BAMBOO_SMEM_SIZE;
    int nsize = bamboo_smemtbl[totest];
    bool isnext = false;
    if(nsize < bound) {
      bool tocheck = true;
      // have some space in the block
      if(totest == tofindb) {
		// the first partition
		size = bound - nsize;
      } else if(nsize == 0) {
		// an empty partition, can be appended
		size += bound;
      } else {
		// not an empty partition, can not be appended
		// the last continuous block is not big enough, start another block
		isnext = true;
		tocheck = false;
      }  // if(totest == tofindb) else if(nsize == 0) else ...
      if(tocheck) {
		if(size >= isize) {
		  // have enough space in the block, malloc
		  foundsmem = 1;
		  break;
		}  // if(size > isize)
      }   // if(tocheck)
    } else {
      isnext = true;
    }  // if(nsize < bound) else ...
    totest += 1;
    if(totest > gcnumblock-1-bamboo_reserved_smem) {
      // no more local mem, do not find suitable block
      foundsmem = 2;
      break;
    }  // if(totest > gcnumblock-1-bamboo_reserved_smem) ...
    if(isnext) {
      // start another block
      tofindb = totest;
    } // if(islocal)
  } while(true);

  if(foundsmem == 1) {
    // find suitable block
    mem = gcbaseva+bamboo_smemtbl[tofindb]+((tofindb<NUMCORES4GC) ?
          (BAMBOO_SMEM_SIZE_L*tofindb) : (BAMBOO_LARGE_SMEM_BOUND+
          (tofindb-NUMCORES4GC)*BAMBOO_SMEM_SIZE));
    *allocsize = size;
    // set bamboo_smemtbl
    for(int i = tofindb; i <= totest; i++) {
      bamboo_smemtbl[i]=(i<NUMCORES4GC)?BAMBOO_SMEM_SIZE_L:BAMBOO_SMEM_SIZE;
    }
    if(tofindb == bamboo_free_block) {
      bamboo_free_block = totest+1;
    }
  } else if(foundsmem == 2) {
    // no suitable block
    *allocsize = 0;
    mem = NULL;
  }

  return mem;
} // void * globalmalloc_I(int, int, int *)
#endif // MULTICORE_GC

// malloc from the shared memory
void * smemalloc_I(int coren,
                   int size,
                   int * allocsize) {
  void * mem = NULL;
#ifdef MULTICORE_GC
  int isize = size+(BAMBOO_CACHE_LINE_SIZE);

  // go through the bamboo_smemtbl for suitable partitions
  switch(bamboo_smem_mode) {
  case SMEMLOCAL: {
    mem = localmalloc_I(coren, isize, allocsize);
    break;
  }

  case SMEMFIXED: {
#ifdef SMEMF
	mem = fixedmalloc_I(coren, isize, allocsize);
#else
	// not supported yet
	BAMBOO_EXIT(0xe001);
#endif
    break;
  }

  case SMEMMIXED: {
#ifdef SMEMM
	mem = mixedmalloc_I(coren, isize, allocsize);
#else
	// not supported yet
    BAMBOO_EXIT(0xe002);
#endif
    break;
  }

  case SMEMGLOBAL: {
    mem = globalmalloc_I(coren, isize, allocsize);
    break;
  }

  default:
    break;
  }

  if(mem == NULL) {
#else 
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
#endif // MULTICORE_GC
    // no enough shared global memory
    *allocsize = 0;
#ifdef MULTICORE_GC
	if(!gcflag) {
	  gcflag = true;
	  // inform other cores to stop and wait for gc
	  gcprecheck = true;
	  for(int i = 0; i < NUMCORESACTIVE; i++) {
		// reuse the gcnumsendobjs & gcnumreceiveobjs
		gccorestatus[i] = 1;
		gcnumsendobjs[0][i] = 0;
		gcnumreceiveobjs[0][i] = 0;
	  }
	  for(int i = 0; i < NUMCORESACTIVE; i++) {
		if(i != BAMBOO_NUM_OF_CORE) {
		  if(BAMBOO_CHECK_SEND_MODE()) {
			cache_msg_1(i, GCSTARTPRE);
		  } else {
			send_msg_1(i, GCSTARTPRE, true);
		  }
		}
	  }
	}
	return NULL;
#else
    BAMBOO_EXIT(0xe003);
#endif
  }
  return mem;
}  // void * smemalloc_I(int, int, int)

#endif // MULTICORE
