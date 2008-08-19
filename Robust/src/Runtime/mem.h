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
#ifdef RAW
void * mycalloc(int m, int size);
void * mycalloc_i(int m, int size);
void myfree(void * ptr);
#define FREEMALLOC(x) mycalloc(1,x)
#define RUNMALLOC(x) mycalloc(1,x) // handle interruption inside
#define RUNMALLOC_I(x) mycalloc_i(1,x) // with interruption blocked beforehand
#define RUNFREE(x) myfree(x);
//#define PTR(x) (32+(x-1)&~31)
#else
#define FREEMALLOC(x) calloc(1,x)
#define RUNMALLOC(x) calloc(1,x)
#define RUNFREE(x) free(x)
//#define PTR(x) (x)
#endif
#endif
#endif
#endif
