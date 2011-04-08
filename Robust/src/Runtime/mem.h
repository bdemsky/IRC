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
#ifdef COREPROF
#include "coreprof.h"
#define RUNMALLOC(x) cp_calloc(x)
#define RUNFREE(x) cp_free(x)
#else
#define RUNMALLOC(x) calloc(1,x)
#define RUNFREE(x) free(x)
#endif
#else
#ifdef MULTICORE
void * mycalloc(int m, int size, char * file, int line);
void * mycalloc_i(int m, int size, char * file, int line);
void myfree(void * ptr);
#define RUNMALLOC(x) mycalloc(1,x,__FILE__,__LINE__) // handle interruption inside
#define RUNMALLOC_I(x) mycalloc_i(1,x,__FILE__,__LINE__) //with interruption blocked beforehand
#define RUNFREE(x) myfree(x)
#ifdef MULTICORE_GC
#include "multicoregc.h"
void * mycalloc_share(struct garbagelist * stackptr, int m, int size);
void * mycalloc_share_ngc(int m, int size);
void * mycalloc_share_ngc_I(int m, int size);
void mycalloc_free_ngc(void * ptr);
void mycalloc_free_ngc_I(void * ptr);
#define FREEMALLOC(s, x) mycalloc_share((s),1,(x))
#else
void * mycalloc_share(int m, int size);
#define FREEMALLOC(x) mycalloc_share(1,x)
#endif // #ifdef MULTICORE_GC
//#define PTR(x) (32+(x-1)&~31)
#endif  // #ifdef MULTICORE
#endif  // #ifdef PRECISE_GC
#endif  // #ifdef BOEHM_GC
#endif  // #ifdef MEMH
