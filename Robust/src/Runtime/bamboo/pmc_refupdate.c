#include <stdlib.h>
#include "structdefs.h"
#include "bambooalign.h"
#include "multicoregc.h"
#include "runtime_arch.h"
#include "pmc_forward.h"
#include "pmc_refupdate.h"
#include "multicoremgc.h"
#include "runtime.h"
#include "thread.h"


#define pmcupdateObj(objptr) ((void *)((struct ___Object___ *)objptr)->marked)

#define PMCUPDATEOBJ(obj) {void *updatetmpptr=obj; if (updatetmpptr!=NULL) {obj=pmcupdateObj(updatetmpptr);}}
//if (obj==NULL) {tprintf("BAD REF UPDATE %x->%x in %u\n",updatetmpptr,obj,__LINE__);}}}

#define PMCUPDATEOBJNONNULL(obj) {void *updatetmpptr=obj; obj=pmcupdateObj(updatetmpptr);}
//if (obj==NULL) {tprintf("BAD REF UPDATE in %x->%x %u\n",updatetmpptr,obj,__LINE__);}}

void pmc_updatePtrs(void *ptr, int type) {
  unsigned int * pointer=pointerarray[type];
  //tprintf("Updating pointers in %x\n", ptr);
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
  //tprintf("done\n");
}

void pmc_updategarbagelist(struct garbagelist *listptr) {
  for(;listptr!=NULL; listptr=listptr->next) {
    for(int i=0; i<listptr->size; i++) {
      PMCUPDATEOBJ(listptr->array[i]);
    }
  }
}

void pmc_updateRuntimePtrs(struct garbagelist * stackptr) {
  // update current stack
  pmc_updategarbagelist(stackptr);

  // update static pointers global_defs_p
  if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
    pmc_updategarbagelist((struct garbagelist *)global_defs_p);
  }

#ifdef TASK
  // update objectsets
  if(BAMBOO_NUM_OF_CORE < NUMCORESACTIVE) {
    for(int i=0; i<NUMCLASSES; i++) {
      struct parameterwrapper ** queues = objectqueues[BAMBOO_NUM_OF_CORE][i];
      int length = numqueues[BAMBOO_NUM_OF_CORE][i];
      for(int j = 0; j < length; ++j) {
        struct parameterwrapper * parameter = queues[j];
        struct ObjectHash * set=parameter->objectset;
        for(struct ObjectNode * ptr=set->listhead;ptr!=NULL;ptr=ptr->lnext) {
          PMCUPDATEOBJNONNULL(ptr->key);
        }
        ObjectHashrehash(set);
      }
    }
  }

  // update current task descriptor
  if(currtpd != NULL) {
    for(int i=0; i<currtpd->numParameters; i++) {
      // the parameter can not be NULL
      PMCUPDATEOBJNONNULL(currtpd->parameterArray[i]);
    }
  }

  // update active tasks
  if(activetasks != NULL) {
    for(struct genpointerlist * ptr=activetasks->list;ptr!=NULL;ptr=ptr->inext){
      struct taskparamdescriptor *tpd=ptr->src;
      for(int i=0; i<tpd->numParameters; i++) {
        // the parameter can not be NULL
	PMCUPDATEOBJNONNULL(tpd->parameterArray[i]);
      }
    }
    genrehash(activetasks);
  }

  // update cached transferred obj
  for(struct QueueItem * tmpobjptr =  getHead(&objqueue);tmpobjptr != NULL;tmpobjptr = getNextQueueItem(tmpobjptr)) {
    struct transObjInfo * objInfo=(struct transObjInfo *)(tmpobjptr->objectptr);
    // the obj can not be NULL
    PMCUPDATEOBJNONNULL(objInfo->objptr);
  }

  // update cached objs to be transferred
  for(struct QueueItem * item = getHead(totransobjqueue);item != NULL;item = getNextQueueItem(item)) {
    struct transObjInfo * totransobj = (struct transObjInfo *)(item->objectptr);
    // the obj can not be NULL
    PMCUPDATEOBJNONNULL(totransobj->objptr);
  }  

  // enqueue lock related info
  for(int i = 0; i < runtime_locklen; ++i) {
    PMCUPDATEOBJ(runtime_locks[i].redirectlock);
    PMCUPDATEOBJ(runtime_locks[i].value);
  }
#endif

#ifdef MGC
  // update the bamboo_threadlocks
  for(int i = 0; i < bamboo_threadlocks.index; i++) {
    // the locked obj can not be NULL
    PMCUPDATEOBJNONNULL(bamboo_threadlocks.locks[i].object);
  }

  // update the bamboo_current_thread
  PMCUPDATEOBJ(bamboo_current_thread);

  // update global thread queue
  if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
    unsigned int thread_counter = *((unsigned int*)(bamboo_thread_queue+1));
    if(thread_counter > 0) {
      unsigned int start = *((unsigned int*)(bamboo_thread_queue+2));
      for(int i = thread_counter; i > 0; i--) {
        // the thread obj can not be NULL
        PMCUPDATEOBJNONNULL(*((void **)&bamboo_thread_queue[4+start]));
        start = (start+1)&bamboo_max_thread_num_mask;
      }
    }
    unlockthreadqueue();
  }
#endif
}


void pmc_doreferenceupdate(struct garbagelist *stackptr) {
  struct pmc_region * region=&pmc_heapptr->regions[BAMBOO_NUM_OF_CORE];
  pmc_updateRuntimePtrs(stackptr);
  pmc_referenceupdate(region->startptr, region->endptr);
}

void pmc_referenceupdate(void *bottomptr, void *topptr) {
  void *tmpptr=bottomptr;
  //tprintf("%x -- %x\n", bottomptr, topptr);
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
    if (((struct ___Object___ *) tmpptr)->marked) {
      pmc_updatePtrs(tmpptr, type);
    }
    tmpptr+=size;
  }
}

void pmc_docompact() {
  struct pmc_region * region=&pmc_heapptr->regions[BAMBOO_NUM_OF_CORE];
  pmc_compact(region, !(BAMBOO_NUM_OF_CORE&1), region->startptr, region->endptr);
}

void moveforward(void *dstptr, void *origptr, unsigned int length) {
  void *endtoptr=dstptr+length;

  if(origptr < endtoptr&&dstptr < origptr+length) {
    unsigned int *sptr=origptr;
    unsigned int *dptr=dstptr;
    unsigned int len=length;
    //we will never have an object of size 0....
    
    do {
      //#1
      unsigned int tmpptr0=*sptr;
      sptr++;
      *dptr=tmpptr0;
      dptr++;
      
      //#2
      tmpptr0=*sptr;
      sptr++;
      *dptr=tmpptr0;
      dptr++;
      
      //#3
      tmpptr0=*sptr;
      sptr++;
      *dptr=tmpptr0;
      dptr++;
      
      //#4
      tmpptr0=*sptr;
      sptr++;
      *dptr=tmpptr0;
      dptr++;
      
      //#5
      tmpptr0=*sptr;
      sptr++;
      *dptr=tmpptr0;
      dptr++;
      
      //#6
      tmpptr0=*sptr;
      sptr++;
      *dptr=tmpptr0;
      dptr++;
      
      //#7
      tmpptr0=*sptr;
      sptr++;
      *dptr=tmpptr0;
      dptr++;
      
      //#8
      tmpptr0=*sptr;
      sptr++;
      *dptr=tmpptr0;
      dptr++;
      
      len=len-32;
    } while(len);
    //memmove(dstptr, origptr, length);
  } else if (origptr!=dstptr) {
    //no need to copy if the source & dest are equal....
    memcpy(dstptr, origptr, length);
  }
}

void movebackward(void *dstptr, void *origptr, unsigned int length) {
  void *endtoptr=dstptr+length;

  if(origptr < endtoptr&&dstptr < origptr+length) {
    unsigned int *sptr=origptr+length;
    unsigned int *dptr=endtoptr;
    unsigned int len=length;
    //we will never have an object of size 0....
    
    do {
      //#1
      unsigned int tmpptr0=*sptr;
      sptr--;
      *dptr=tmpptr0;
      dptr--;
      
      //#2
      tmpptr0=*sptr;
      sptr--;
      *dptr=tmpptr0;
      dptr--;
      
      //#3
      tmpptr0=*sptr;
      sptr--;
      *dptr=tmpptr0;
      dptr--;
      
      //#4
      tmpptr0=*sptr;
      sptr--;
      *dptr=tmpptr0;
      dptr--;
      
      //#5
      tmpptr0=*sptr;
      sptr--;
      *dptr=tmpptr0;
      dptr--;
      
      //#6
      tmpptr0=*sptr;
      sptr--;
      *dptr=tmpptr0;
      dptr--;
      
      //#7
      tmpptr0=*sptr;
      sptr--;
      *dptr=tmpptr0;
      dptr--;
      
      //#8
      tmpptr0=*sptr;
      sptr--;
      *dptr=tmpptr0;
      dptr--;
      
      len=len-32;
    } while(len);
    //memmove(dstptr, origptr, length);
  } else if (origptr!=dstptr) {
    //no need to copy if the source & dest are equal....
    memcpy(dstptr, origptr, length);
  }
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
	//tprintf("Compacting %x\n",tmpptr);
	//	memmove(forwardptr, tmpptr, size);
	memforward(forwardptr, tmpptr, size);
      }
      tmpptr+=size;
    }
  } else {
    struct ___Object___ *backward=region->lastobj;
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
      //tprintf("Compacting %x\n",lastobj);
      //memmove(forwardptr, lastobj, size);
      membackward(forwardptr, lastobj, size);
    }
  }
}
