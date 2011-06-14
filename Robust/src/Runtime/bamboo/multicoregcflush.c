#ifdef MULTICORE_GC
#include "multicoregcflush.h"
#include "multicoreruntime.h"
#include "ObjectHash.h"
#include "GenericHashtable.h"
#include "gcqueue.h"

/* Task specific includes */

#ifdef TASK
extern struct parameterwrapper ** objectqueues[][NUMCLASSES];
extern int numqueues[][NUMCLASSES];
extern struct genhashtable * activetasks;
extern struct parameterwrapper ** objectqueues[][NUMCLASSES];
extern struct taskparamdescriptor *currtpd;
extern struct LockValue runtime_locks[MAXTASKPARAMS];
extern int runtime_locklen;
#endif

extern struct global_defs_t * global_defs_p;

#ifdef MGC
extern struct lockvector bamboo_threadlocks;
#endif

// NOTE: the objptr should not be NULL and should not be non shared ptr
#define FLUSHOBJ(obj, tt) {void *flushtmpptr=obj; if (flushtmpptr!=NULL) obj=flushObj(flushtmpptr);}
#define FLUSHOBJNONNULL(obj, tt) {void *flushtmpptr=obj; obj=flushObj(flushtmpptr);}

INLINE void * flushObj(void * objptr) {
  return gcmappingtbl[OBJMAPPINGINDEX((unsigned int)objptr)];
}

INLINE void updategarbagelist(struct garbagelist *listptr) {
  for(;listptr!=NULL; listptr=listptr->next) {
    for(int i=0; i<listptr->size; i++) {
      FLUSHOBJ(listptr->array[i], i);
    }
  }
}

INLINE void flushRuntimeObj(struct garbagelist * stackptr) {
  // flush current stack
  updategarbagelist(stackptr);

  // flush static pointers global_defs_p
  if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
    updategarbagelist((struct garbagelist *)global_defs_p);
  }

#ifdef TASK
  // flush objectsets
  if(BAMBOO_NUM_OF_CORE < NUMCORESACTIVE) {
    for(int i=0; i<NUMCLASSES; i++) {
      struct parameterwrapper ** queues = objectqueues[BAMBOO_NUM_OF_CORE][i];
      int length = numqueues[BAMBOO_NUM_OF_CORE][i];
      for(int j = 0; j < length; ++j) {
        struct parameterwrapper * parameter = queues[j];
        struct ObjectHash * set=parameter->objectset;
        for(struct ObjectNode * ptr=set->listhead;ptr!=NULL;ptr=ptr->lnext) {
          FLUSHOBJNONNULL(ptr->key, 0);
        }
        ObjectHashrehash(set);
      }
    }
  }

  // flush current task descriptor
  if(currtpd != NULL) {
    for(int i=0; i<currtpd->numParameters; i++) {
      // the parameter can not be NULL
      FLUSHOBJNONNULL(currtpd->parameterArray[i], i);
    }
  }

  // flush active tasks
  if(activetasks != NULL) {
    for(struct genpointerlist * ptr=activetasks->list;ptr!=NULL;ptr=ptr->inext){
      struct taskparamdescriptor *tpd=ptr->src;
      for(int i=0; i<tpd->numParameters; i++) {
        // the parameter can not be NULL
	FLUSHOBJNONNULL(tpd->parameterArray[i], i);
      }
    }
    genrehash(activetasks);
  }

  // flush cached transferred obj
  for(struct QueueItem * tmpobjptr =  getHead(&objqueue);tmpobjptr != NULL;tmpobjptr = getNextQueueItem(tmpobjptr)) {
    struct transObjInfo * objInfo=(struct transObjInfo *)(tmpobjptr->objectptr);
    // the obj can not be NULL
    FLUSHOBJNONNULL(objInfo->objptr, 0);
  }

  // flush cached objs to be transferred
  for(struct QueueItem * item = getHead(totransobjqueue);item != NULL;item = getNextQueueItem(item)) {
    struct transObjInfo * totransobj = (struct transObjInfo *)(item->objectptr);
    // the obj can not be NULL
    FLUSHOBJNONNULL(totransobj->objptr, 0);
  }  

  // enqueue lock related info
  for(int i = 0; i < runtime_locklen; ++i) {
    FLUSHOBJ(runtime_locks[i].redirectlock, i);
    FLUSHOBJ(runtime_locks[i].value, i);
  }
#endif

#ifdef MGC
  // flush the bamboo_threadlocks
  for(int i = 0; i < bamboo_threadlocks.index; i++) {
    // the locked obj can not be NULL
    FLUSHOBJNONNULL(bamboo_threadlocks.locks[i].object, i);
  }

  // flush the bamboo_current_thread
  FLUSHOBJ(bamboo_current_thread, 0);

  // flush global thread queue
  if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
    unsigned int thread_counter = *((unsigned int*)(bamboo_thread_queue+1));
    if(thread_counter > 0) {
      unsigned int start = *((unsigned int*)(bamboo_thread_queue+2));
      for(int i = thread_counter; i > 0; i--) {
        // the thread obj can not be NULL
        FLUSHOBJNONNULL(bamboo_thread_queue[4+start], 0);
        start = (start+1)&bamboo_max_thread_num_mask;
      }
    }
    unlockthreadqueue();
  }
#endif
}

INLINE void flushPtrsInObj(void * ptr) {
  int type = ((int *)(ptr))[0];
  // scan all pointers in ptr
  unsigned int * pointer=pointerarray[type];
  if (pointer==0) {
    /* Array of primitives */
#ifdef OBJECTHASPOINTERS
    //handle object class
    pointer=pointerarray[OBJECTTYPE];
    unsigned int size=pointer[0];
    for(int i=1; i<=size; i++) {
      unsigned int offset=pointer[i];
      FLUSHOBJ(*((void **)(((char *)ptr)+offset)), i);
    }
#endif
  } else if (((unsigned int)pointer)==1) {
    /* Array of pointers */
    struct ArrayObject *ao=(struct ArrayObject *) ptr;
    int length=ao->___length___;
    for(int j=0; j<length; j++) {
      FLUSHOBJ(((void **)(((char *)&ao->___length___)+sizeof(int)))[j], j);
    }
#ifdef OBJECTHASPOINTERS
    pointer=pointerarray[OBJECTTYPE];
    //handle object class
    unsigned int size=pointer[0];
    
    for(int i=1; i<=size; i++) {
      unsigned int offset=pointer[i];     
      FLUSHOBJ(*((void **)(((char *)ptr)+offset)), i);
    }
#endif
  } else {
    unsigned int size=pointer[0];
    
    for(int i=1; i<=size; i++) {
      unsigned int offset=pointer[i];
      FLUSHOBJ(*((void **)(((char *)ptr)+offset)), i);
    }
  }  
}

void flush(struct garbagelist * stackptr) {
  BAMBOO_CACHE_MF();

  flushRuntimeObj(stackptr);
  while(gc_moreItems()) {
    void * ptr = (void *) gc_dequeue();
    // should be a local shared obj and should have mapping info
    FLUSHOBJNONNULL(ptr, 0);
    BAMBOO_ASSERT(ptr != NULL);
    int markedstatus;
    GETMARKED(markedstatus, ptr);

    if(markedstatus==MARKEDFIRST) {
      flushPtrsInObj((void *)ptr);
      // restore the mark field, indicating that this obj has been flushed
      RESETMARKED(ptr);
    }
  } 

  // TODO bug here: the startup core contains all lobjs' info, thus all the
  // lobjs are flushed in sequence.
  // flush lobjs
  while(gc_lobjmoreItems_I()) {
    void * ptr = (void *) gc_lobjdequeue_I(NULL, NULL);
    FLUSHOBJ(ptr, 0);
    BAMBOO_ASSERT(ptr!=NULL);

    int markedstatus;
    GETMARKED(markedstatus, ptr);

    if(markedstatus==MARKEDFIRST) {
      flushPtrsInObj(ptr);
      // restore the mark field, indicating that this obj has been flushed
      RESETMARKED(ptr);
    }     
  } 

  // send flush finish message to core coordinator
  if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
    gccorestatus[BAMBOO_NUM_OF_CORE] = 0;
  } else {
    send_msg_2(STARTUPCORE,GCFINISHFLUSH,BAMBOO_NUM_OF_CORE);
  }

  //tprintf("flush: %lld \n", BAMBOO_GET_EXE_TIME()-tmpt); // TODO
} 

#endif // MULTICORE_GC
