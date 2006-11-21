#ifndef MEMH
#define MEMH
#include<stdlib.h>
#include<stdio.h>

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
#define FREEMALLOC(x) calloc(1,x)
#define RUNMALLOC(x) calloc(1,x)
#define RUNFREE(x) free(x)
#endif
#endif
#endif
