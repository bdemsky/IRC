#ifndef PMC_FORWARD_H
#define PMC_FORWARD_H
#include "pmc_garbage.h"


void pmc_count();
void pmc_countbytes(struct pmc_unit * region, void *bottomptr, void *topptr);
void pmc_processunits();
void pmc_doforward();
void pmc_forward(struct pmc_region *region, unsigned int totalbytes, void *bottomptr, void *topptr, bool fwddirection);


#endif
