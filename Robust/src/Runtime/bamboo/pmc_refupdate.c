#include <stdlib.h>
#include "structdefs.h"
#include "bambooalign.h"
#include "runtime_arch.h"
#include "pmc_forward.h"
#include "pmc_refupdate.h"


#define pmcupdateObj(objptr) ((void *)((struct ___Object___ *)objptr)->marked)

#define PMCUPDATEOBJ(obj) {void *updatetmpptr=obj; tprintf("UP%x\n", updatetmpptr); if (updatetmpptr!=NULL) {obj=pmcupdateObj(updatetmpptr);}}

#define PMCUPDATEOBJNONNULL(obj) {void *updatetmpptr=obj; obj=pmcupdateObj(updatetmpptr);}

void pmc_updatePtrs(void *ptr, int type) {
  unsigned int * pointer=pointerarray[type];
  if (pointer==0) {
    /* Array of primitives */
  } else if (((unsigned int)pointer)==1) {
    /* Array of pointers */
    struct ArrayObject *ao=(struct ArrayObject *) ptr;
    int length=ao->___length___;
    for(int j=0; j<length; j++) {
      PMCUPDATEOBJ(((void **)(((char *)&ao->___length___)+sizeof(int)))[j]);
    }
  } else {
    unsigned int size=pointer[0];
    
    for(int i=1; i<=size; i++) {
      unsigned int offset=pointer[i];
      PMCUPDATEOBJ(*((void **)(((char *)ptr)+offset)));
    }
  }  
}

void pmc_doreferenceupdate() {
  struct pmc_region * region=&pmc_heapptr->regions[BAMBOO_NUM_OF_CORE];
  pmc_referenceupdate(region->startptr, region->endptr);
}

void pmc_referenceupdate(void *bottomptr, void *topptr) {
  void *tmpptr=bottomptr;
  tprintf("%x -- %x\n", bottomptr, topptr);
  while(tmpptr<topptr) {
    unsigned int type;
    unsigned int size;
    gettype_size(tmpptr, &type, &size);
    size=((size-1)&(~(ALIGNMENTSIZE-1)))+ALIGNMENTSIZE;
    tprintf("%x typ=%u sz=%u\n", tmpptr, type, size);
    if (!type) {
      tmpptr+=ALIGNMENTSIZE;
      continue;
    }
    //if marked we update the pointers
    if (((struct ___Object___ *) tmpptr)->marked) {
      pmc_updatePtrs(tmpptr, type);
    }
    tmpptr+=size;
    tprintf("INC\n");
  }
}

void pmc_docompact() {
  struct pmc_region * region=&pmc_heapptr->regions[BAMBOO_NUM_OF_CORE];
  pmc_compact(region, !(BAMBOO_NUM_OF_CORE&1), region->startptr, region->endptr);
}


void pmc_compact(struct pmc_region * region, int forward, void *bottomptr, void *topptr) {
  if (forward) {
    void *tmpptr=bottomptr;
    while(tmpptr<topptr) {
      unsigned int type;
      unsigned int size;
      gettype_size(tmpptr, &type, &size);
      size=((size-1)&(~(ALIGNMENTSIZE-1)))+ALIGNMENTSIZE;
      if (!type) {
	tmpptr+=ALIGNMENTSIZE;
	continue;
      }
      //if marked we update the pointers
      void *forwardptr=(void *)((struct ___Object___ *) tmpptr)->marked;
      ((struct ___Object___ *) tmpptr)->marked=NULL;
      if (forwardptr) {
	memmove(forwardptr, tmpptr, size);
      }
      tmpptr+=size;
    }
  } else {
    struct ___Object___ *backward=((struct ___Object___ *) region)->backward;
    struct ___Object___ *lastobj=NULL;
    while(backward) {
      lastobj=backward;
      backward=backward->backward;
      unsigned int type;
      unsigned int size;
      gettype_size(lastobj, &type, &size);
      size=((size-1)&(~(ALIGNMENTSIZE-1)))+ALIGNMENTSIZE;
      void *forwardptr=(void *)((struct ___Object___ *) lastobj)->marked;
      ((struct ___Object___ *) lastobj)->marked=NULL;
      if (forwardptr) {
	memmove(forwardptr, lastobj, size);
      }
    }
  }
}
