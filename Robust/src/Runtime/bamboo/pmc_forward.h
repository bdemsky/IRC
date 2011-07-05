#ifndef PMC_FORWARD_H
#define PMC_FORWARD_H
#include "pmc_garbage.h"

void pmc_countbytes(struct pmc_unit * region, void *bottomptr, void *topptr);

void pmc_forward(unsigned int totalbytes, void *bottomptr, void *topptr, bool fwddirection);


#endif
