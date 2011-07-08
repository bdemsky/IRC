#ifndef PMC_REFUPDATE_H
#define PMC_REFUPDATE_H

#include "multicore.h"
#include "multicoregc.h"
#include "structdefs.h"

void pmc_updatePtrs(void *ptr, int type);
void pmc_doreferenceupdate(struct garbagelist *);
void pmc_referenceupdate(void *bottomptr, void *topptr);
void pmc_docompact();
void pmc_compact(struct pmc_region * region, int forward, void *bottomptr, void *topptr);
void pmc_updategarbagelist(struct garbagelist *listptr);
void pmc_updateRuntimePtrs(struct garbagelist * stackptr);

#endif
