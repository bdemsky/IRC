#include "pmc_forward.h"


//Comment: should build dummy byte arrays to allow skipping data...
void pmc_countbytes(struct pmc_region * region, void *bottomptr, void *topptr) {
  void *tmpptr=bottomptr;
  unsigned int totalbytes=0;
  while(tmpptr<topptr) {
    unsigned int type;
    unsigned int size;
    gettype_size(tmpptr, &type, &size);
    if (!type) {
      tmpptr+=ALIGNMENTSIZE;
      continue;
    }
    size=((size-1)&(~(ALIGNMENTSIZE-1)))+ALIGNMENTSIZE;
    if (((struct ___Object___ *)tmpptr)->mark)
      totalbytes+=size;
    tmpptr+=size;
  }
  region->numbytes=totalbytes;
}


void pmc_forward(struct pmc_region *region, unsigned int totalbytes, void *bottomptr, void *topptr, bool fwddirection) {
  void *tmpptr=bottomptr;
  void *forwardptr=fwddirection?bottomptr:(topptr-totalbytes);
  struct ___Object___ *lastobj=NULL;

  while(tmpptr>topptr) {
    unsigned int type;
    unsigned int size;
    gettype_size(tmpptr, &type, &size);
    if (!type) {
      tmpptr+=ALIGNMENTSIZE;
      continue;
    }
    size=((size-1)&(~(ALIGNMENTSIZE-1)))+ALIGNMENTSIZE;

    if (((struct ___Object___ *)tmpptr)->mark) {
      ((struct ___Object___ *)tmpptr)->mark=forwardptr;
      forwardptr+=size;
      if (lastobj&&!fwddirection) {
	tmpptr->backward=lastobj;
	lastobj=(struct ___Object___ *)tmpptr;
      }
    }
    tmpptr+=size;
  }
  region->lastobj=lastobj;
}
