#ifndef BABMOO_MULTICORE_GC_MARK_H
#define BAMBOO_MULTICORE_GC_MARK_H
#ifdef MULTICORE_GC
#include "multicore.h"

INLINE void gettype_size(void * ptr, int * ttype, unsigned int * tsize);
INLINE bool isLarge(void * ptr, int * ttype, unsigned int * tsize);
INLINE unsigned int hostcore(void * ptr);
INLINE void markgarbagelist(struct garbagelist * listptr);
INLINE void tomark(struct garbagelist * stackptr);
INLINE void scanPtrsInObj(void * ptr, int type);
void markObj(void * objptr);
void mark(struct garbagelist * stackptr);

#endif // MULTICORE_GC
#endif // BAMBOO_MULTICORE_GC_MARK_H
