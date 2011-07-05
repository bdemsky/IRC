#include "pmc_forward.h"

void pmc_count() {
  for(int i=0;i<NUMPMCUNITS;i++) {
    if (!tmc_spin_mutex_trylock(&pmc_heapptr->units[i].lock)) {
      //got lock
      void *unitbase=gcbaseva+i*UNITSIZE;
      void *unittop=unitbase+UNITSIZE;
      pmc_countbytes(&pmc_heapptr->unit[i], unitbase, unittop);
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
    if (((struct ___Object___ *)tmpptr)->mark)
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
