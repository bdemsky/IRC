#ifdef MULTICORE_GC
#include "multicoregcflush.h"
#include "multicoreruntime.h"
#include "ObjectHash.h"
#include "GenericHashtable.h"

extern int corenum;
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

#ifdef SMEMM
extern unsigned int gcmem_mixed_threshold;
extern unsigned int gcmem_mixed_usedmem;
#endif

#ifdef MGC
extern struct lockvector bamboo_threadlocks;
#endif

extern struct pointerblock *gchead;
extern int gcheadindex;
extern struct pointerblock *gctail;
extern int gctailindex;
extern struct pointerblock *gctail2;
extern int gctailindex2;
extern struct pointerblock *gcspare;

extern struct lobjpointerblock *gclobjhead;
extern int gclobjheadindex;
extern struct lobjpointerblock *gclobjtail;
extern int gclobjtailindex;
extern struct lobjpointerblock *gclobjtail2;
extern int gclobjtailindex2;
extern struct lobjpointerblock *gclobjspare;

// NOTE: the objptr should not be NULL and should not be non shared ptr
INLINE void * flushObj(void * objptr, int linenum, void * ptr, int tt) {
  void * dstptr = gcmappingtbl[OBJMAPPINGINDEX((unsigned int)objptr)];
  return dstptr;
}

INLINE void flushRuntimeObj(struct garbagelist * stackptr) {
  int i,j;
  // flush current stack
  while(stackptr!=NULL) {
    for(i=0; i<stackptr->size; i++) {
      if(stackptr->array[i] != NULL) {
        stackptr->array[i] = 
          flushObj(stackptr->array[i], __LINE__, stackptr->array[i], i);
      }
    }
    stackptr=stackptr->next;
  }

  // flush static pointers global_defs_p
  if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
    struct garbagelist * staticptr=(struct garbagelist *)global_defs_p;
    for(i=0; i<staticptr->size; i++) {
      if(staticptr->array[i] != NULL) {
        staticptr->array[i] = 
          flushObj(staticptr->array[i], __LINE__, staticptr->array[i], i);
      }
    }
  }

#ifdef TASK
  // flush objectsets
  if(BAMBOO_NUM_OF_CORE < NUMCORESACTIVE) {
    for(i=0; i<NUMCLASSES; i++) {
      struct parameterwrapper ** queues = objectqueues[BAMBOO_NUM_OF_CORE][i];
      int length = numqueues[BAMBOO_NUM_OF_CORE][i];
      for(j = 0; j < length; ++j) {
        struct parameterwrapper * parameter = queues[j];
        struct ObjectHash * set=parameter->objectset;
        struct ObjectNode * ptr=set->listhead;
        while(ptr!=NULL) {
          ptr->key = flushObj((void *)ptr->key, __LINE__, (void *)ptr->key, 0);
          ptr=ptr->lnext;
        }
        ObjectHashrehash(set);
      }
    }
  }

  // flush current task descriptor
  if(currtpd != NULL) {
    for(i=0; i<currtpd->numParameters; i++) {
      // the parameter can not be NULL
      currtpd->parameterArray[i] = flushObj(currtpd->parameterArray[i], 
          __LINE__, currtpd->parameterArray[i], i);
    }
  }

  // flush active tasks
  if(activetasks != NULL) {
    struct genpointerlist * ptr=activetasks->list;
    while(ptr!=NULL) {
      struct taskparamdescriptor *tpd=ptr->src;
      int i;
      for(i=0; i<tpd->numParameters; i++) {
        // the parameter can not be NULL
        tpd->parameterArray[i] = 
          flushObj(tpd->parameterArray[i], __LINE__, tpd->parameterArray[i], i);
      }
      ptr=ptr->inext;
    }
    genrehash(activetasks);
  }

  // flush cached transferred obj
  struct QueueItem * tmpobjptr =  getHead(&objqueue);
  while(tmpobjptr != NULL) {
    struct transObjInfo * objInfo=(struct transObjInfo *)(tmpobjptr->objectptr);
    // the obj can not be NULL
    objInfo->objptr = flushObj(objInfo->objptr, __LINE__, objInfo->objptr, 0);
    tmpobjptr = getNextQueueItem(tmpobjptr);
  }

  // flush cached objs to be transferred
  struct QueueItem * item = getHead(totransobjqueue);
  while(item != NULL) {
    struct transObjInfo * totransobj = (struct transObjInfo *)(item->objectptr);
    // the obj can not be NULL
    totransobj->objptr = 
      flushObj(totransobj->objptr, __LINE__, totransobj->objptr, 0);
    item = getNextQueueItem(item);
  }  

  // enqueue lock related info
  for(i = 0; i < runtime_locklen; ++i) {
    if(runtime_locks[i].redirectlock != NULL) {
      runtime_locks[i].redirectlock = flushObj(runtime_locks[i].redirectlock, 
          __LINE__, runtime_locks[i].redirectlock, i);
    }
    if(runtime_locks[i].value != NULL) {
      runtime_locks[i].value = flushObj(runtime_locks[i].value, 
          __LINE__, runtime_locks[i].value, i);
    }
  }
#endif

#ifdef MGC
  // flush the bamboo_threadlocks
  for(i = 0; i < bamboo_threadlocks.index; i++) {
    // the locked obj can not be NULL
    bamboo_threadlocks.locks[i].object = 
      flushObj((void *)(bamboo_threadlocks.locks[i].object), 
          __LINE__, (void *)(bamboo_threadlocks.locks[i].object), i);
  }

  // flush the bamboo_current_thread
  if(bamboo_current_thread != 0) {
    bamboo_current_thread = 
      (unsigned int)(flushObj((void *)bamboo_current_thread, 
            __LINE__, (void *)bamboo_current_thread, 0));
  }

  // flush global thread queue
  if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
    unsigned int thread_counter = *((unsigned int*)(bamboo_thread_queue+1));
    if(thread_counter > 0) {
      unsigned int start = *((unsigned int*)(bamboo_thread_queue+2));
      for(i = thread_counter; i > 0; i--) {
        // the thread obj can not be NULL
        bamboo_thread_queue[4+start] = 
          (INTPTR)(flushObj((void *)bamboo_thread_queue[4+start], 
                __LINE__, (void *)bamboo_thread_queue, 0));
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
  unsigned int * pointer;
  pointer=pointerarray[type];
  if (pointer==0) {
    /* Array of primitives */
    pointer=pointerarray[OBJECTTYPE];
    //handle object class
    unsigned int size=pointer[0];
    int i;
    for(i=1; i<=size; i++) {
      unsigned int offset=pointer[i];
      void * objptr=*((void **)(((char *)ptr)+offset));
      if(objptr != NULL) {
        *((void **)(((char *)ptr)+offset)) = flushObj(objptr, __LINE__, ptr, i);
      }
    }
  } else if (((unsigned int)pointer)==1) {
    /* Array of pointers */
    struct ArrayObject *ao=(struct ArrayObject *) ptr;
    int length=ao->___length___;
    int j;
    for(j=0; j<length; j++) {
      void *objptr=((void **)(((char *)&ao->___length___)+sizeof(int)))[j];
      if(objptr != NULL) {
        ((void **)(((char *)&ao->___length___)+sizeof(int)))[j] = 
          flushObj(objptr, __LINE__, ptr, j);
      }
    }
    {
      pointer=pointerarray[OBJECTTYPE];
      //handle object class
      unsigned int size=pointer[0];
      int i;
      for(i=1; i<=size; i++) {
        unsigned int offset=pointer[i];     
        void * objptr=*((void **)(((char *)ptr)+offset));
        if(objptr != NULL) {
          *((void **)(((char *)ptr)+offset)) = 
            flushObj(objptr, __LINE__, ptr, i);
        }
      }
    }
  } else {
    unsigned int size=pointer[0];
    int i;
    for(i=1; i<=size; i++) {
      unsigned int offset=pointer[i];
      void * objptr=*((void **)(((char *)ptr)+offset));
      if(objptr != NULL) {
        *((void **)(((char *)ptr)+offset)) = flushObj(objptr, __LINE__, ptr, i);
      }
    } 
  }  
}

void flush(struct garbagelist * stackptr) {
  BAMBOO_CACHE_MF();

  flushRuntimeObj(stackptr);

  while(true) {
    BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
    if(!gc_moreItems_I()) {
      BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
      break;
    }

    unsigned int ptr = gc_dequeue_I();
    BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
    // should be a local shared obj and should have mapping info
    ptr = flushObj(ptr, __LINE__, ptr, 0);
    if(ptr == NULL) {
      BAMBOO_EXIT(0xb02a);
    }
    if(((struct ___Object___ *)ptr)->marked == COMPACTED) {
      flushPtrsInObj((void *)ptr);
      // restore the mark field, indicating that this obj has been flushed
      ((struct ___Object___ *)ptr)->marked = INIT;
    }
  } 

  // TODO bug here: the startup core contains all lobjs' info, thus all the
  // lobjs are flushed in sequence.
  // flush lobjs
  while(gc_lobjmoreItems_I()) {
    unsigned int ptr = gc_lobjdequeue_I(NULL, NULL);
    ptr = flushObj(ptr, __LINE__, ptr, 0);
    if(ptr == NULL) {
      BAMBOO_EXIT(0xb02d);
    }
    if(((struct ___Object___ *)ptr)->marked == COMPACTED) {
      flushPtrsInObj((void *)ptr);
      // restore the mark field, indicating that this obj has been flushed
      ((struct ___Object___ *)ptr)->marked = INIT;
    }     
  } 

  // send flush finish message to core coordinator
  if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
    gccorestatus[BAMBOO_NUM_OF_CORE] = 0;
  } else {
    send_msg_2(STARTUPCORE, GCFINISHFLUSH, BAMBOO_NUM_OF_CORE, false);
  }
} 

#endif // MULTICORE_GC
