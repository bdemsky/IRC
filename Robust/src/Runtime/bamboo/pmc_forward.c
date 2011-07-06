#include "pmc_forward.h"
#include "runtime_arch.h"
#include "bambooalign.h"
#include "pmc_garbage.h"
#include "multicoregc.h"

void pmc_count() {
  for(int i=0;i<NUMPMCUNITS;i++) {
    if (!tmc_spin_mutex_trylock(&pmc_heapptr->units[i].lock)) {
      //got lock
      void *unitbase=(i==0)?gcbaseva:pmc_heapptr->units[i-1]->endptr;
      void *unittop=pmc_heapptr->units[i]->endptr;
      pmc_countbytes(&pmc_heapptr->units[i], unitbase, unittop);
    }
  }
}

//Comment: should build dummy byte arrays to allow skipping data...
void pmc_countbytes(struct pmc_unit * unit, void *bottomptr, void *topptr) {
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
    if (((struct ___Object___ *)tmpptr)->marked)
      totalbytes+=size;
    tmpptr+=size;
  }
  unit->numbytes=totalbytes;
}

void pmc_processunits() {
  unsigned int livebytes=0;
  for(int i=0;i<NUMPMCUNITS;i++) {
    livebytes+=pmc_heapptr->units[i].numbytes;
  }
  //make sure to round up
  unsigned int livebytespercore=((livebytes-1)/NUMCORES4GC)+1;
  unsigned int regionnum=0;
  int totalbytes=0;
  int numregions=0;

  for(int i=0;i<NUMPMCUNITS;i++) {
    if (numregions>0&&(totalbytes+pmc_heapptr->units[i].numbytes)>livebytespercore) {
      regionnum++;
      totalbytes-=livebytespercore;
      numregions=0;
    }
    numregions++;
    pmc_heapptr->units[i].regionnum=regionnum;
    tmc_spin_mutex_init(&pmc_heapptr->units[i].lock);
    totalbytes+=pmc_heapptr->units[i].numbytes;
  }
}

void pmc_doforward() {
  int startregion=-1;
  int endregion=-1;
  unsigned int totalbytes=0;
  struct pmc_region * region=&pmc_heapptr->regions[BAMBOO_NUM_OF_CORE];
  for(int i=0;i<NUMPMCUNITS;i++) {
    if (startregion==-1&&BAMBOO_NUM_OF_CORE==pmc_heapptr->units[i].regionnum) {
      startregion=i;
    }
    if (BAMBOO_NUM_OF_CORE<pmc_heapptr->units[i].regionnum) {
      endregion=i;
      break;
    }
    if (startregion!=-1) {
      totalbytes+=pmc_heapptr->units[i].numbytes;
    }
  }
  if (startregion==-1) 
    return;
  if (endregion==-1)
    endregion=NUMPMCUNITS;
  region->lowunit=startregion;
  region->highunit=endregion;
  region->startptr=(startregion==0)?gcbaseva:pmc_heapptr->units[startregion-1].endptr;
  region->endptr=pmc_heapptr->units[endregion].endptr;

  if (BAMBOO_NUM_OF_CORE&1) {
    //backward direction
    region->lastptr=region->endptr-totalbytes;
  } else {
    //forward direction
    region->lastptr=region->startptr+totalbytes;
  }

  pmc_forward(region, totalbytes, region->startptr, region->endptr, !(BAMBOO_NUM_OF_CORE&1));
}


void pmc_forward(struct pmc_region *region, unsigned int totalbytes, void *bottomptr, void *topptr, bool fwddirection) {
  void *tmpptr=bottomptr;
  void *forwardptr=fwddirection?bottomptr:(topptr-totalbytes);
  struct ___Object___ *lastobj=NULL;
  unsigned int currunit=region->lowunit;
  void *endunit=pmc_unitend(currunit);

  if (!fwddirection) {
    //reset boundaries of beginning units
    while(endunit<forwardptr) {
      pmc_heapptr->units[currunit].endptr=endunit;
      currunit++;
      endunit=pmc_unitend(currunit);
    }
  } else {
    //reset boundaries of end units
    unsigned int lastunit=region->highunit-1;
    void * lastunitend=pmc_unitend(lastunit);
    while(lastunitend>forwardptr) {
      pmc_heapptr->units[currunit].endptr=lastunitend;
      lastunit--;
      lastunitend=pmc_unitend(lastunit);
    }
  }

  while(tmpptr>topptr) {
    unsigned int type;
    unsigned int size;
    gettype_size(tmpptr, &type, &size);
    if (!type) {
      tmpptr+=ALIGNMENTSIZE;
      continue;
    }
    size=((size-1)&(~(ALIGNMENTSIZE-1)))+ALIGNMENTSIZE;

    if (((struct ___Object___ *)tmpptr)->marked) {
      ((struct ___Object___ *)tmpptr)->marked=forwardptr;
      void *newforwardptr=forwardptr+size;
      while(newforwardptr>endunit) {
	pmc_heapptr->regions[currunit].endptr=newforwardptr;
	currunit++;
	endunit=pmc_unitend(currunit);
      }

      forwardptr=newforwardptr;
      if (lastobj&&!fwddirection) {
	tmpptr->backward=lastobj;
	lastobj=(struct ___Object___ *)tmpptr;
      }
    }
    tmpptr+=size;
  }
  region->lastobj=lastobj;
}
