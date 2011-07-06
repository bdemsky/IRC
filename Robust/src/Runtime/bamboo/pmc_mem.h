#ifndef PMC_MEM_H
#define PMC_MEM_H

void * pmc_alloc(unsigned int * numbytesallocated, unsigned int minimumbytes) {
  for(int i=0;i<NUMCORES4GC;i+=2) {
    void *startptr=pmc_heapptr->regions[i].lastptr;
    void *finishptr=pmc_heapptr->regions[i+1].lastptr;
    if ((finishptr-startptr)>minimumbytes) {
      
    }
  }
}

#endif
