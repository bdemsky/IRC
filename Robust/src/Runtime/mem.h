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
void * mycalloc(int m, int size);
void * mycalloc_i(int m, int size);
void myfree(void * ptr);
#define RUNMALLOC(x) mycalloc(1,x) // handle interruption inside
#define RUNMALLOC_I(x) mycalloc_i(1,x) //with interruption blocked beforehand
#define RUNFREE(x) myfree(x)
#ifdef MULTICORE_GC
#include "multicoregc.h"
void * mycalloc_share(struct garbagelist * stackptr, int m, int size);
void * mycalloc_share_ngc(int m, int size);
void * mycalloc_share_ngc_I(int m, int size);
void mycalloc_free_ngc(void * ptr);
void mycalloc_free_ngc_I(void * ptr);
#define FREEMALLOC(s, x) mycalloc_share((s),1,(x))
#define FREEMALLOC_NGC(x) mycalloc_share_ngc(1, (x))
#define FREEMALLOC_NGC_I(x) mycalloc_share_ngc_I(1, (x))
#define FREE_NGC(x) mycalloc_free_ngc(x)
#define FREE_NGC_I(x) mycalloc_free_ngc_I(x)
#else
void * mycalloc_share(int m, int size);
#define FREEMALLOC(x) mycalloc_share(1,x)
#endif // #ifdef MULTICORE_GC
//#define PTR(x) (32+(x-1)&~31)
#endif  // #ifdef MULTICORE
#endif  // #ifdef PRECISE_GC
#endif  // #ifdef BOEHM_GC
#endif  // #ifdef MEMH
