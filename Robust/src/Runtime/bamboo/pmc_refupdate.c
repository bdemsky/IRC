#include "pmc_forward.h"
#include "pmc_refupdate.h"

#define pmcupdateObj(objptr) ((void *)((struct ___Object___ *)objptr)->mark)

#define PMCUPDATEOBJ(obj) {void *updatetmpptr=obj; if (updatetmpptr!=NULL) {obj=pmcupdateObj(updatetmpptr);}}

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
  while(tmpptr<topptr) {
    unsigned int type;
    unsigned int size;
    gettype_size(tmpptr, &type, &size);
    if (!type) {
      tmpptr+=ALIGNMENTSIZE;
      continue;
    }
    //if marked we update the pointers
    if (((struct ___Object___ *) tmpptr)->mark) {
      pmc_updatePtrs(tmpptr, type);
    }
    tmpptr+=size;
  }
}

void pmc_docompact() {
  struct pmc_region * region=&pmc_heapptr->regions[BAMBOO_NUM_OF_CORE];
  pmc_compact(region, BAMBOO_NUM_OF_CORE&1, region->startptr, region->endptr);
}


void pmc_compact(struct pmc_region * region, int forward, void *bottomptr, void *topptr) {
  if (forward) {
    void *tmpptr=bottomptr;
    void *lastptr;
    while(tmpptr<topptr) {
      unsigned int type;
      unsigned int size;
      gettype_size(tmpptr, &type, &size);
      if (!type) {
	tmpptr+=ALIGNMENTSIZE;
	continue;
      }
      //if marked we update the pointers
      void *forwardptr=(void *)((struct ___Object___ *) tmpptr)->mark;
      ((struct ___Object___ *) tmpptr)->mark=NULL;
      if (forwardptr) {
	memmove(forwardptr, tmpptr, size);
      }
      lastptr=forwardptr+size;
      tmpptr+=size;
    }
    region->lastptr=lastptr;
  } else {
    struct ___Object___ *backward=region->lastobj;
    struct ___Object___ *lastobj=NULL;
    while(backward) {
      lastobj=backward;
      backward=backward->lastobj;
      unsigned int type;
      unsigned int size;
      gettype_size(tmpptr, &type, &size);
      void *forwardptr=(void *)((struct ___Object___ *) lastobj)->mark;
      ((struct ___Object___ *) lastobj)->mark=NULL;
      if (forwardptr) {
	memmove(forwardptr, lastobj, size);
      }
    }
  }
}
