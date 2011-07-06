#ifndef PMC_MARK_H
#define PMC_MARK_H
#include "multicore.h"
#include "multicoregc.h"
#include "structdefs.h"

void pmc_markObj(struct ___Object___ *ptr);
void pmc_scanPtrsInObj(void * ptr, int type);
void pmc_mark(struct garbagelist *stackptr);
bool pmc_trysteal();
void pmc_marklocal();
void pmc_tomark(struct garbagelist * stackptr);
void pmc_markgarbagelist(struct garbagelist * listptr);
#endif
