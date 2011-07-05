#ifndef PMC_REFUPDATE_H
#define PMC_REFUPDATE_H

void pmc_updatePtrs(void *ptr, int type);
void pmc_referenceupdate(void *bottomptr, void *topptr);
void pmc_compact(struct pmc_region * region, int forward, void *bottomptr, void *topptr);
#endif
