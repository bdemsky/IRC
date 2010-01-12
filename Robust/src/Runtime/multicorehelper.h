#ifndef MULTICORE_HELPER_H
#define MULTICORE_HELPER_H

#ifdef GC_1
// NUMCORES4GC = 1
static int gc_core2block[2] = {0,1};

static int gc_block2core[2] = { 0,  0};
#elif defined GC_62
// NUMCORES4GC = 62
static int gc_core2block[124] = {
	0,123,  15,108,  16,107,  31,92,  32,91,  47,76,    
	1,122,  14,109,  17,106,  30,93,  33,90,  46,77,  48,75,  61,62,
	2,121,  13,110,  18,105,  29,94,  34,89,  45,78,  49,74,  60,63,
	3,120,  12,111,  19,104,  28,95,  35,88,  44,79,  50,73,  59,64,
	4,119,  11,112,  20,103,  27,96,  36,87,  43,80,  51,72,  58,65,
	5,118,  10,113,  21,102,  26,97,  37,86,  42,81,  52,71,  57,66,
	6,117,   9,114,  22,101,  25,98,  38,85,  41,82,  53,70,  56,67,
	7,116,   8,115,  23,100,  24,99,  39,84,  40,83,  54,69,  55,68
};

static int gc_block2core[124] = { 
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

#endif // MULTICORE_HELPER_H
