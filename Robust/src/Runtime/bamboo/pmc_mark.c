#include "pmc_mark.h"
#include "pmc_garbage.h"
#include "multicoremgc.h"
#include <stdlib.h>

#define PMC_MARKOBJ(objptr) {void * marktmpptr=objptr; if (marktmpptr!=NULL) {pmc_markObj(marktmpptr);}}

#define PMC_MARKOBJNONNULL(objptr) {pmc_markObj(objptr);}

void pmc_markObj(struct ___Object___ *ptr) {
  if (!ptr->marked) {
    ptr->marked=1;
    pmc_enqueue(pmc_localqueue, ptr);
  }
}

void pmc_scanPtrsInObj(void * ptr, int type) {
  // scan all pointers in ptr
  unsigned int * pointer = pointerarray[type];
  if (pointer==0) {
    /* Array of primitives */
  } else if (((unsigned int)pointer)==1) {
    /* Array of pointers */
    struct ArrayObject *ao=(struct ArrayObject *) ptr;
    int length=ao->___length___;
    for(int i=0; i<length; i++) {
      void *objptr=((void **)(((char *)&ao->___length___)+sizeof(int)))[i];
      PMC_MARKOBJ(objptr);
    }
  } else {
    /* Normal Object */
    int size=pointer[0];
    for(int i=1; i<=size; i++) {
      unsigned int offset=pointer[i];
      void * objptr=*((void **)(((char *)ptr)+offset));
      PMC_MARKOBJ(objptr);
    }
  }
}

void pmc_markgarbagelist(struct garbagelist * listptr) {
  for(;listptr!=NULL;listptr=listptr->next) {
    int size=listptr->size;
    for(int i=0; i<size; i++) {
      PMC_MARKOBJ(listptr->array[i]);
    }
  }
}

void pmc_mark(struct garbagelist *stackptr) {
  pmc_tomark(stackptr);
  while(true) {
    //scan everything in our local queue
    pmc_marklocal();
    if (pmc_trysteal())
      break;
  }
}

bool pmc_trysteal() {
  decrementthreads();
  while(pmc_heapptr->numthreads) {
    for(int i=0;i<NUMCORES4GC;i++) {
      struct pmc_queue *queue=&pmc_heapptr->regions[i].markqueue;
      if (!pmc_isEmpty(queue)) {
	incrementthreads();
	void *objptr=pmc_dequeue(queue);
	if (objptr!=NULL) {
	  unsigned int type=((struct ___Object___*)objptr)->type;
	  pmc_scanPtrsInObj(objptr, type);
	}
	return false;
      }
    }
  }
  return true;
}

void pmc_marklocal() {
  void *objptr;
  while(objptr=pmc_dequeue(pmc_localqueue)) {
    unsigned int type=((struct ___Object___*)objptr)->type;
    pmc_scanPtrsInObj(objptr, type);
  }
}

void pmc_tomark(struct garbagelist * stackptr) {
  // enqueue current stack
  pmc_markgarbagelist(stackptr);
  
  // enqueue static pointers global_defs_p
  if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
    pmc_markgarbagelist((struct garbagelist *)global_defs_p);
  }
#ifdef TASK
  // enqueue objectsets
  if(BAMBOO_NUM_OF_CORE < NUMCORESACTIVE) {
    for(int i=0; i<NUMCLASSES; i++) {
      struct parameterwrapper ** queues = objectqueues[BAMBOO_NUM_OF_CORE][i];
      int length = numqueues[BAMBOO_NUM_OF_CORE][i];
      for(int j = 0; j < length; ++j) {
        struct parameterwrapper * parameter = queues[j];
        struct ObjectHash * set=parameter->objectset;
        struct ObjectNode * ptr=set->listhead;
        for(;ptr!=NULL;ptr=ptr->lnext) {
          PMC_MARKOBJNONNULL((void *)ptr->key);
        }
      }
    }
  }
  
  // enqueue current task descriptor
  if(currtpd != NULL) {
    for(int i=0; i<currtpd->numParameters; i++) {
      // currtpd->parameterArray[i] can not be NULL
      PMC_MARKOBJNONNULL(currtpd->parameterArray[i]);
    }
  }

  // enqueue active tasks
  if(activetasks != NULL) {
    struct genpointerlist * ptr=activetasks->list;
    for(;ptr!=NULL;ptr=ptr->inext) {
      struct taskparamdescriptor *tpd=ptr->src;
      for(int i=0; i<tpd->numParameters; i++) {
        // the tpd->parameterArray[i] can not be NULL
        PMC_MARKOBJNONNULL(tpd->parameterArray[i]);
      }
    }
  }

  // enqueue cached transferred obj
  struct QueueItem * tmpobjptr =  getHead(&objqueue);
  for(;tmpobjptr != NULL;tmpobjptr=getNextQueueItem(tmpobjptr)) {
    struct transObjInfo * objInfo=(struct transObjInfo *)(tmpobjptr->objectptr);
    // the objptr can not be NULL
    PMC_MARKOBJNONNULL(objInfo->objptr);
  }

  // enqueue cached objs to be transferred
  struct QueueItem * item = getHead(totransobjqueue);
  for(;item != NULL;item=getNextQueueItem(item)) {
    struct transObjInfo * totransobj=(struct transObjInfo *)(item->objectptr);
    // the objptr can not be NULL
    PMC_MARKOBJNONNULL(totransobj->objptr);
  }

  // enqueue lock related info
  for(int i = 0; i < runtime_locklen; i++) {
    PMC_MARKOBJ((void *)(runtime_locks[i].redirectlock));
    PMC_MARKOBJ((void *)(runtime_locks[i].value));
  }
#endif 

#ifdef MGC
  // enqueue global thread queue
  if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
    lockthreadqueue();
    unsigned int thread_counter = *((unsigned int*)(bamboo_thread_queue+1));
    if(thread_counter > 0) {
      unsigned int start = *((unsigned int*)(bamboo_thread_queue+2));
      for(int i = thread_counter; i > 0; i--) {
        // the thread obj can not be NULL
        PMC_MARKOBJNONNULL((void *)bamboo_thread_queue[4+start]);
        start = (start+1)&bamboo_max_thread_num_mask;
      }
    }
  }
  // enqueue the bamboo_threadlocks
  for(int i = 0; i < bamboo_threadlocks.index; i++) {
    // the locks can not be NULL
    PMC_MARKOBJNONNULL((void *)(bamboo_threadlocks.locks[i].object));
  }
  // enqueue the bamboo_current_thread
  PMC_MARKOBJ(bamboo_current_thread);
#endif
}
