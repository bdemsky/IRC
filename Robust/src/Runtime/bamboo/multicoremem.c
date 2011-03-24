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
#define NUM_CORES2TEST 9
#ifdef GC_1
int core2test[1][NUM_CORES2TEST] = {
  {0, -1, -1, -1, -1, -1, -1, -1, -1}
};
#elif defined GC_56
int core2test[56][NUM_CORES2TEST] = {
  { 0, -1,  7, -1,  1, -1, 14, -1,  2}, 
  { 1, -1,  8,  0,  2, -1, 15, -1,  3}, 
  { 2, -1,  9,  1,  3, -1, 16,  0,  4}, 
  { 3, -1, 10,  2,  4, -1, 17,  1,  5}, 
  { 4, -1, 11,  3,  5, -1, 18,  2,  6}, 
  { 5, -1, 12,  4,  6, -1, 19,  3, -1},
  { 6, -1, 13,  5, -1, -1, 20,  4, -1}, 
  { 7,  0, 14, -1,  8, -1, 21, -1,  9}, 
  { 8,  1, 15,  7,  9, -1, 22, -1, 10}, 
  { 9,  2, 16,  8, 10, -1, 23,  7, 11}, 
  {10,  3, 17,  9, 11, -1, 24,  8, 12}, 
  {11,  4, 18, 10, 12, -1, 25,  9, 13},
  {12,  5, 19, 11, 13, -1, 26, 10, -1}, 
  {13,  6, 20, 12, -1, -1, 27, 11, -1}, 
  {14,  7, 21, -1, 15,  0, 28, -1, 16}, 
  {15,  8, 22, 14, 16,  1, 29, -1, 17}, 
  {16,  9, 23, 15, 17,  2, 30, 14, 18}, 
  {17, 10, 24, 16, 18,  3, 31, 15, 19},
  {18, 11, 25, 17, 19,  4, 32, 16, 20}, 
  {19, 12, 26, 18, 20,  5, 33, 17, -1}, 
  {20, 13, 27, 19, -1,  6, 34, 18, -1}, 
  {21, 14, 28, -1, 22,  7, 35, -1, 23}, 
  {22, 15, 29, 21, 23,  8, 36, -1, 24}, 
  {23, 16, 30, 22, 24,  9, 37, 21, 25},
  {24, 17, 31, 23, 25, 10, 38, 22, 26}, 
  {25, 18, 32, 24, 26, 11, 39, 23, 27}, 
  {26, 19, 33, 25, 27, 12, 40, 24, -1}, 
  {27, 20, 34, 26, -1, 13, 41, 25, -1}, 
  {28, 21, 35, -1, 29, 14, 42, -1, 30}, 
  {29, 22, 36, 28, 30, 15, 43, -1, 31},
  {30, 23, 37, 29, 31, 16, 44, 28, 32}, 
  {31, 24, 38, 30, 32, 17, 45, 29, 33}, 
  {32, 25, 39, 31, 33, 18, 46, 30, 34}, 
  {33, 26, 40, 32, 34, 19, 47, 31, -1}, 
  {34, 27, 41, 33, -1, 20, 48, 32, -1}, 
  {35, 28, 42, -1, 36, 21, 49, -1, 37},
  {36, 29, 43, 35, 37, 22, 50, -1, 38}, 
  {37, 30, 44, 36, 38, 23, 51, 35, 39}, 
  {38, 31, 45, 37, 39, 24, 52, 36, 40}, 
  {39, 32, 46, 38, 40, 25, 53, 37, 41}, 
  {40, 33, 47, 39, 41, 26, 54, 38, -1}, 
  {41, 34, 48, 40, -1, 27, 55, 39, -1},
  {42, 35, 49, -1, 43, 28, -1, -1, 44}, 
  {43, 36, 50, 42, 44, 29, -1, -1, 45}, 
  {44, 37, 51, 43, 45, 30, -1, 42, 46}, 
  {45, 38, 52, 44, 46, 31, -1, 43, 47}, 
  {46, 39, 53, 45, 47, 32, -1, 44, 48}, 
  {47, 40, 54, 46, 48, 33, -1, 45, -1},
  {48, 41, 55, 47, -1, 34, -1, 46, -1}, 
  {49, 42, -1, -1, 50, 35, -1, -1, 51}, 
  {50, 43, -1, 49, 51, 36, -1, -1, 52}, 
  {51, 44, -1, 50, 52, 37, -1, 49, 53}, 
  {52, 45, -1, 51, 53, 38, -1, 50, 54}, 
  {53, 46, -1, 52, 54, 39, -1, 51, 55},
  {54, 47, -1, 53, 55, 40, -1, 52, -1}, 
  {55, 48, -1, 54, -1, 41, -1, 53, -1}
};
#elif defined GC_62
int core2test[62][NUM_CORES2TEST] = {
  { 0, -1,  6, -1,  1, -1, 14, -1,  2}, 
  { 1, -1,  7,  0,  2, -1, 15, -1,  3}, 
  { 2, -1,  8,  1,  3, -1, 16,  0,  4}, 
  { 3, -1,  9,  2,  4, -1, 17,  1,  5}, 
  { 4, -1, 10,  3,  5, -1, 18,  2, -1}, 
  { 5, -1, 11,  4, -1, -1, 19,  3, -1},
  { 6,  0, 14, -1,  7, -1, 22, -1,  8}, 
  { 7,  1, 15,  6,  8, -1, 23, -1,  9}, 
  { 8,  2, 16,  7,  9, -1, 24,  6, 10}, 
  { 9,  3, 17,  8, 10, -1, 25,  7, 11}, 
  {10,  4, 18,  9, 11, -1, 26,  8, 12}, 
  {11,  5, 19, 10, 12, -1, 27,  9, 13},
  {12, -1, 20, 11, 13, -1, 28, 10, -1}, 
  {13, -1, 21, 12, -1, -1, 29, 11, -1}, 
  {14,  6, 22, -1, 15,  0, 30, -1, 16}, 
  {15,  7, 23, 14, 16,  1, 31, -1, 17}, 
  {16,  8, 24, 15, 17,  2, 32, 14, 18}, 
  {17,  9, 25, 16, 18,  3, 33, 15, 19},
  {18, 10, 26, 17, 19,  4, 34, 16, 20}, 
  {19, 11, 27, 18, 20,  5, 35, 17, 21}, 
  {20, 12, 28, 19, 21, -1, 36, 18, -1}, 
  {21, 13, 29, 28, -1, -1, 37, 19, -1}, 
  {22, 14, 30, -1, 23,  6, 38, -1, 24}, 
  {23, 15, 31, 22, 24,  7, 39, -1, 25},
  {24, 16, 32, 23, 25,  8, 40, 22, 26}, 
  {25, 17, 33, 24, 26,  9, 41, 23, 27}, 
  {26, 18, 34, 25, 27, 10, 42, 24, 28}, 
  {27, 19, 35, 26, 28, 11, 43, 25, 29}, 
  {28, 20, 36, 27, 29, 12, 44, 26, -1}, 
  {29, 21, 37, 28, -1, 13, 45, 27, -1},
  {30, 22, 38, -1, 31, 22, 46, -1, 32}, 
  {31, 23, 39, 30, 32, 15, 47, -1, 33}, 
  {32, 24, 40, 31, 33, 16, 48, 30, 34}, 
  {33, 25, 41, 32, 34, 17, 49, 31, 35}, 
  {34, 26, 42, 33, 35, 18, 50, 32, 36}, 
  {35, 27, 43, 34, 36, 19, 51, 33, 37},
  {36, 28, 44, 35, 37, 20, 52, 34, -1}, 
  {37, 29, 45, 36, -1, 21, 53, 35, -1}, 
  {38, 30, 46, -1, 39, 22, 54, -1, 40}, 
  {39, 31, 47, 38, 40, 23, 55, -1, 41}, 
  {40, 32, 48, 39, 41, 24, 56, 38, 42}, 
  {41, 33, 49, 40, 42, 25, 57, 39, 43},
  {42, 34, 50, 41, 43, 26, 58, 40, 44}, 
  {43, 35, 51, 42, 44, 27, 59, 41, 45}, 
  {44, 36, 52, 43, 45, 28, 60, 42, -1}, 
  {45, 37, 53, 44, -1, 29, 61, 43, -1}, 
  {46, 38, 54, -1, 47, 30, -1, -1, 48}, 
  {47, 39, 55, 46, 48, 31, -1, -1, 49},
  {48, 40, 56, 47, 49, 32, -1, 46, 50}, 
  {49, 41, 57, 48, 50, 33, -1, 47, 51}, 
  {50, 42, 58, 49, 51, 34, -1, 48, 52}, 
  {51, 43, 59, 50, 52, 35, -1, 49, 53}, 
  {52, 44, 60, 51, 53, 36, -1, 50, -1}, 
  {53, 45, 61, 52, -1, 37, -1, 51, -1},
  {54, 46, -1, -1, 55, 38, -1, -1, 56}, 
  {55, 47, -1, 54, 56, 39, -1, -1, 57}, 
  {56, 48, -1, 55, 57, 40, -1, 54, 58}, 
  {57, 49, -1, 56, 59, 41, -1, 55, 59}, 
  {58, 50, -1, 57, 59, 42, -1, 56, 60}, 
  {59, 51, -1, 58, 60, 43, -1, 57, 61},
  {60, 52, -1, 59, 61, 44, -1, 58, -1}, 
  {61, 53, -1, 60, -1, 45, -1, 59, -1}
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
