#ifndef BABMOO_MULTICORE_GC_MARK_H
#define BAMBOO_MULTICORE_GC_MARK_H
#ifdef MULTICORE_GC
#include "multicore.h"

void gettype_size(void * ptr, int * ttype, unsigned int * tsize);
bool isLarge(void * ptr, int * ttype, unsigned int * tsize);
void markgarbagelist(struct garbagelist * listptr);
void tomark(struct garbagelist * stackptr);
void scanPtrsInObj(void * ptr, int type);
void markObj(void * objptr);
void mark(struct garbagelist * stackptr);

#endif // MULTICORE_GC
#endif // BAMBOO_MULTICORE_GC_MARK_H
