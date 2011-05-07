#ifndef BAMBOO_MULTICORE_GC_FLUSH_H
#define BAMBOO_MULTICORE_GC_FLUSH_H

#ifdef MULTICORE_GC
#include "multicore.h"
#include "runtime.h"

void flush(struct garbagelist * stackptr);
#endif // MULTICORE_GC
#endif // BAMBOO_MULTICORE_GC_FLUSH_H
