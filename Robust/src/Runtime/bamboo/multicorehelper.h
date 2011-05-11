// NOTE: this file should be changed to be generated automatically

#ifndef BAMBOO_MULTICORE_HELPER_H
#define BAMBOO_MULTICORE_HELPER_H

#ifdef GC_1
// NUMCORES4GC = 1
static unsigned int gc_core2block[2] = {0,1};

static unsigned int gc_block2core[2] = { 0,  0};
#elif defined GC_2
// NUMCORES4GC = 2
static unsigned int gc_core2block[4] = {
  0,3,  
  1,2
};

static unsigned int gc_block2core[4] = {
  0, 1, 
  1, 0
};
#elif defined GC_4
// NUMCORES4GC = 4
static unsigned int gc_core2block[8] = {
  0,7,  3,4,  
  1,6,  2,5
};

static unsigned int gc_block2core[8] = {
  0, 2, 3, 1,
  1, 3, 2, 0
};
#elif defined GC_8
// NUMCORES4GC = 8
static unsigned int gc_core2block[16] = {
  0,15,   3,12,   4,11,   7,8, 
  1,14,   2,13,   5,10,   6,9
};

static unsigned int gc_block2core[16] = {
  0,  4,  5,  1,  2,  6,  7,  3,
  3,  7,  6,  2,  1,  5,  4,  0
};
#elif defined GC_16
// NUMCORES4GC = 16
static unsigned int gc_core2block[32] = {
  0,31,   7,24,   8,23,  15,16,
  1,30,   6,25,   9,22,  14,17,
  2,29,   5,26,  10,21,  13,18,
  3,28,   4,27,  11,20,  12,19
};

static unsigned int gc_block2core[32] = {
  0,  4,  8, 12, 13,  9,  5,  1,
  2,  6, 10, 14, 15, 11,  7,  3,
  3,  7, 11, 15, 14, 10,  6,  2,
  1,  5,  9, 13, 12,  8,  4,  0
};
#elif defined GC_32
// NUMCORES4GC = 32
static unsigned int gc_core2block[64] = {
  0,63,  15,48,  16,47,  31,32,   
  1,62,  14,49,  17,46,  30,33,
  2,61,  13,50,  18,45,  29,34, 
  3,60,  12,51,  19,44,  28,35,
  4,59,  11,52,  20,43,  27,36,  
  5,58,  10,53,  21,42,  26,37,
  6,57,   9,54,  22,41,  25,38,  
  7,56,   8,55,  23,40,  24,39
};

static unsigned int gc_block2core[64] = {
  0,  4,  8, 12, 16, 20, 24, 28, 29, 25, 21, 17, 13,  9,  5,  1,
  2,  6, 10, 14, 18, 22, 26, 30, 31, 27, 23, 19, 15, 11,  7,  3,
  3,  7, 11, 15, 19, 23, 27, 31, 30, 26, 22, 18, 14, 10,  6,  2,
  1,  5,  9, 13, 17, 21, 25, 29, 28, 24, 20, 16, 12,  8,  4,  0
};
#elif defined GC_48
// NUMCORES4GC = 50
static unsigned int gc_core2block[96] = {
  0,95,  15,80,  16,79,  31,64,  32,63,  47,48,
  1,94,  14,81,  17,78,  30,65,  33,62,  46,49,
  2,93,  13,82,  18,77,  29,66,  34,61,  45,50,
  3,92,  12,83,  19,76,  28,67,  35,60,  44,51,
  4,91,  11,84,  20,75,  27,68,  36,59,  43,52,
  5,90,  10,85,  21,74,  26,69,  37,58,  42,53,
  6,89,   9,86,  22,73,  25,70,  38,57,  41,54,
  7,88,   8,87,  23,72,  24,71,  39,56,  40,55
};

static unsigned int gc_block2core[96] = {
  0,  6, 12, 18, 24, 30, 36, 42, 43, 37, 31, 25, 19, 13,  7,  1,
  2,  8, 14, 20, 26, 32, 38, 44, 45, 39, 33, 27, 21, 15,  9,  3,
  4, 10, 16, 22, 28, 34, 40, 46, 47, 41, 35, 29, 23, 17, 11,  5,
  5, 11, 17, 23, 29, 35, 41, 47, 46, 40, 34, 28, 22, 16, 10,  4,
  3,  9, 15, 21, 27, 33, 39, 45, 44, 38, 32, 26, 20, 14,  8,  2,
  1,  7, 13, 19, 25, 31, 37, 43, 42, 36, 30, 24, 18, 12,  6,  0
};
#elif defined GC_50
// NUMCORES4GC = 50
static unsigned int gc_core2block[100] = {
  0,99,  15,84,  16,83,  31,68,  32,67,  47,52,
  1,98,  14,85,  17,82,  30,69,  33,66,  46,53,
  2,97,  13,86,  18,81,  29,70,  34,65,  45,54,
  3,96,  12,87,  19,80,  28,71,  35,64,  44,55,
  4,95,  11,88,  20,79,  27,72,  36,63,  43,56,
  5,94,  10,89,  21,78,  26,73,  37,62,  42,57,
  6,93,   9,90,  22,77,  25,74,  38,61,  41,58,  48,51,
  7,92,   8,91,  23,76,  24,75,  39,60,  40,59,  49,50
};

static unsigned int gc_block2core[100] = {
  0,  6, 12, 18, 24, 30, 36, 43, 44, 37, 31, 25, 19, 13,  7,  1,
  2,  8, 14, 20, 26, 32, 38, 45, 46, 39, 33, 27, 21, 15,  9,  3,
  4, 10, 16, 22, 28, 34, 40, 47, 48, 41, 35, 29, 23, 17, 11,  5,
                         42, 49, 49, 42,
  5, 11, 17, 23, 29, 35, 41, 48, 47, 40, 34, 28, 22, 16, 10,  4,
  3,  9, 15, 21, 27, 33, 39, 46, 45, 38, 32, 26, 20, 14,  8,  2,
  1,  7, 13, 19, 25, 31, 37, 44, 43, 36, 30, 24, 18, 12,  6,  0
};
#elif defined GC_56
// NUMCORES4GC = 56
static unsigned int gc_core2block[112] = {
  0,111,  15, 96,  16,95,  31,80,  32,79,  47,64,  48,63,
  1,110,  14, 97,  17,94,  30,81,  33,78,  46,65,  49,62,
  2,109,  13, 98,  18,93,  29,82,  34,77,  45,66,  50,61,
  3,108,  12, 99,  19,92,  28,83,  35,76,  44,67,  51,60,
  4,107,  11,100,  20,91,  27,84,  36,75,  43,68,  52,59,
  5,106,  10,101,  21,90,  26,85,  37,74,  42,69,  53,58,
  6,105,   9,102,  22,89,  25,86,  38,73,  41,70,  54,57,
  7,104,   8,103,  23,88,  24,87,  39,72,  40,71,  55,56
};

static unsigned int gc_block2core[112] = {
  0,  7, 14, 21, 28, 35, 42, 49, 50, 43, 36, 29, 22, 15,  8,  1,
  2,  9, 16, 23, 30, 37, 44, 51, 52, 45, 38, 31, 24, 17, 10,  3,
  4, 11, 18, 25, 32, 39, 46, 53, 54, 47, 40, 33, 26, 19, 12,  5,
  6, 13, 20, 27, 34, 41, 48, 55, 55, 48, 41, 34, 27, 20, 13,  6,
  5, 12, 19, 26, 33, 40, 47, 54, 53, 46, 39, 32, 25, 18, 11,  4,
  3, 10, 17, 24, 31, 38, 45, 52, 51, 44, 37, 30, 23, 16,  9,  2,
  1,  8, 15, 22, 29, 36, 43, 50, 49, 42, 35, 28, 21, 14,  7,  0
};
#elif defined GC_62
// NUMCORES4GC = 62
static unsigned int gc_core2block[124] = {
  0,123,  15,108,  16,107,  31,92,  32,91,  47,76,
  1,122,  14,109,  17,106,  30,93,  33,90,  46,77,  48,75,  61,62,
  2,121,  13,110,  18,105,  29,94,  34,89,  45,78,  49,74,  60,63,
  3,120,  12,111,  19,104,  28,95,  35,88,  44,79,  50,73,  59,64,
  4,119,  11,112,  20,103,  27,96,  36,87,  43,80,  51,72,  58,65,
  5,118,  10,113,  21,102,  26,97,  37,86,  42,81,  52,71,  57,66,
  6,117,   9,114,  22,101,  25,98,  38,85,  41,82,  53,70,  56,67,
  7,116,   8,115,  23,100,  24,99,  39,84,  40,83,  54,69,  55,68
};

static unsigned int gc_block2core[124] = {
  0,  6, 14, 22, 30, 38, 46, 54, 55, 47, 39, 31, 23, 15,  7,  1,
  2,  8, 16, 24, 32, 40, 48, 56, 57, 49, 41, 33, 25, 17,  9,  3,
  4, 10, 18, 26, 34, 42, 50, 58, 59, 51, 43, 35, 27, 19, 11,  5,
  12, 20, 28, 36, 44, 52, 60, 61, 53, 45, 37, 29, 21, 13,
  13, 21, 29, 37, 45, 53, 61, 60, 52, 44, 36, 28, 20, 12,
  5, 11, 19, 27, 35, 43, 51, 59, 58, 50, 42, 34, 26, 18, 10,  4,
  3,  9, 17, 25, 33, 41, 49, 57, 56, 48, 40, 32, 24, 16,  8,  2,
  1,  7, 15, 23, 31, 39, 47, 55, 54, 46, 38, 30, 22, 14,  6,  0
};
#endif

#endif // BAMBOO_MULTICORE_HELPER_H
