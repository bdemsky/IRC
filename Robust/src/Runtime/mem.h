#ifndef MEMH
#define MEMH
#include <stdlib.h>
#ifndef RAW
#include <stdio.h>
#endif

#ifdef BOEHM_GC
#include "gc.h"
#define FREEMALLOC(x) GC_malloc(x)
#define RUNMALLOC(x) GC_malloc(x)
#define RUNFREE(x)
#else
#ifdef PRECISE_GC
#include "garbage.h"
#define RUNMALLOC(x) calloc(1,x)
#define RUNFREE(x) free(x)
#else
#ifdef MULTICORE
#ifdef THREADSIMULATE
#define FREEMALLOC(x) calloc(1,x)
#define RUNMALLOC(x) calloc(1,x)
#define RUNFREE(x) free(x)
//#define PTR(x) (x)
#else
void * mycalloc(int m, int size);
void * mycalloc_share(int m, int size);
void * mycalloc_i(int m, int size);
void myfree(void * ptr);
#define FREEMALLOC(x) mycalloc_share(1,x)
#define RUNMALLOC(x) mycalloc(1,x) // handle interruption inside
#define RUNMALLOC_I(x) mycalloc_i(1,x) // with interruption blocked beforehand
#define RUNFREE(x) myfree(x)
//#define PTR(x) (32+(x-1)&~31)
#endif  // #ifdef THREADSIMULATE
#endif  // #ifdef MULTICORE
#endif  // #ifdef PRECISE_GC
#endif  // #ifdef BOEHM_GC
#endif  // #ifdef MEMH
