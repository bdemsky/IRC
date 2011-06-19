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
void * mycalloc(int size, char * file, int line);
void * mycalloc_i(int size, char * file, int line);
void myfree(void * ptr);
void myfree_i(void * ptr);

#define RUNMALLOC(x) mycalloc(x,__FILE__,__LINE__) // handle interruption inside
#define RUNCALLOC(x) mycalloc(x,__FILE__,__LINE__) // handle interruption inside
#define RUNMALLOC_I(x) mycalloc_i(x,__FILE__,__LINE__) //with interruption blocked beforehand
#define RUNCALLOC_I(x) mycalloc_i(x,__FILE__,__LINE__) //with interruption blocked beforehand

#define RUNFREE(x) myfree(x)
#define RUNFREE_I(x) myfree_i(x)
#ifdef MULTICORE_GC
#include "multicoregc.h"
void * mycalloc_share(struct garbagelist * stackptr, int size);
void * mycalloc_share_ngc(int size);
void * mycalloc_share_ngc_I(int size);
void mycalloc_free_ngc(void * ptr);
void mycalloc_free_ngc_I(void * ptr);
#define FREEMALLOC(s, x) mycalloc_share(s,x)
#else
void * mycalloc_share(int size);
#define FREEMALLOC(x) mycalloc_share(x)
#endif // #ifdef MULTICORE_GC
#endif  // #ifdef MULTICORE
#endif  // #ifdef PRECISE_GC
#endif  // #ifdef BOEHM_GC
#endif  // #ifdef MEMH
