#ifdef MULTICORE_GC
#include "multicoregcmark.h"
#include "runtime.h"
#include "multicoreruntime.h"
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

// should be invoked with interruption closed
INLINE void gc_enqueue_I(unsigned int ptr) {
  if (gcheadindex==NUMPTRS) {
    struct pointerblock * tmp;
    if (gcspare!=NULL) {
      tmp=gcspare;
      gcspare=NULL;
      tmp->next = NULL;
    } else {
      tmp=RUNMALLOC_I(sizeof(struct pointerblock));
    } 
    gchead->next=tmp;
    gchead=tmp;
    gcheadindex=0;
  } 
  gchead->ptrs[gcheadindex++]=ptr;
} 

// dequeue and destroy the queue
INLINE unsigned int gc_dequeue_I() {
  if (gctailindex==NUMPTRS) {
    struct pointerblock *tmp=gctail;
    gctail=gctail->next;
    gctailindex=0;
    if (gcspare!=NULL) {
      RUNFREE_I(tmp);
    } else {
      gcspare=tmp;
      gcspare->next = NULL;
    } 
  } 
  return gctail->ptrs[gctailindex++];
} 

// dequeue and do not destroy the queue
INLINE unsigned int gc_dequeue2_I() {
  if (gctailindex2==NUMPTRS) {
    struct pointerblock *tmp=gctail2;
    gctail2=gctail2->next;
    gctailindex2=0;
  } 
  return gctail2->ptrs[gctailindex2++];
}

INLINE int gc_moreItems_I() {
  return !((gchead==gctail)&&(gctailindex==gcheadindex));
} 

INLINE int gc_moreItems2_I() {
  return !((gchead==gctail2)&&(gctailindex2==gcheadindex));
} 

// should be invoked with interruption closed 
// enqueue a large obj: start addr & length
INLINE void gc_lobjenqueue_I(unsigned int ptr,
                             unsigned int length,
                             unsigned int host) {
  if (gclobjheadindex==NUMLOBJPTRS) {
    struct lobjpointerblock * tmp;
    if (gclobjspare!=NULL) {
      tmp=gclobjspare;
      gclobjspare=NULL;
      tmp->next = NULL;
      tmp->prev = NULL;
    } else {
      tmp=RUNMALLOC_I(sizeof(struct lobjpointerblock));
    }  
    gclobjhead->next=tmp;
    tmp->prev = gclobjhead;
    gclobjhead=tmp;
    gclobjheadindex=0;
  } 
  gclobjhead->lobjs[gclobjheadindex]=ptr;
  gclobjhead->lengths[gclobjheadindex]=length;
  gclobjhead->hosts[gclobjheadindex++]=host;
} 

// dequeue and destroy the queue
INLINE unsigned int gc_lobjdequeue_I(unsigned int * length,
                                     unsigned int * host) {
  if (gclobjtailindex==NUMLOBJPTRS) {
    struct lobjpointerblock *tmp=gclobjtail;
    gclobjtail=gclobjtail->next;
    gclobjtailindex=0;
    gclobjtail->prev = NULL;
    if (gclobjspare!=NULL) {
      RUNFREE_I(tmp);
    } else {
      gclobjspare=tmp;
      tmp->next = NULL;
      tmp->prev = NULL;
    }  
  } 
  if(length != NULL) {
    *length = gclobjtail->lengths[gclobjtailindex];
  }
  if(host != NULL) {
    *host = (unsigned int)(gclobjtail->hosts[gclobjtailindex]);
  }
  return gclobjtail->lobjs[gclobjtailindex++];
} 

INLINE int gc_lobjmoreItems_I() {
  return !((gclobjhead==gclobjtail)&&(gclobjtailindex==gclobjheadindex));
} 

// dequeue and don't destroy the queue
INLINE void gc_lobjdequeue2_I() {
  if (gclobjtailindex2==NUMLOBJPTRS) {
    gclobjtail2=gclobjtail2->next;
    gclobjtailindex2=1;
  } else {
    gclobjtailindex2++;
  }  
}

INLINE int gc_lobjmoreItems2_I() {
  return !((gclobjhead==gclobjtail2)&&(gclobjtailindex2==gclobjheadindex));
} 

// 'reversly' dequeue and don't destroy the queue
INLINE void gc_lobjdequeue3_I() {
  if (gclobjtailindex2==0) {
    gclobjtail2=gclobjtail2->prev;
    gclobjtailindex2=NUMLOBJPTRS-1;
  } else {
    gclobjtailindex2--;
  }  
}

INLINE int gc_lobjmoreItems3_I() {
  return !((gclobjtail==gclobjtail2)&&(gclobjtailindex2==gclobjtailindex));
} 

INLINE void gc_lobjqueueinit4_I() {
  gclobjtail2 = gclobjtail;
  gclobjtailindex2 = gclobjtailindex;
} 

INLINE unsigned int gc_lobjdequeue4_I(unsigned int * length,
                                      unsigned int * host) {
  if (gclobjtailindex2==NUMLOBJPTRS) {
    gclobjtail2=gclobjtail2->next;
    gclobjtailindex2=0;
  } 
  if(length != NULL) {
    *length = gclobjtail2->lengths[gclobjtailindex2];
  }
  if(host != NULL) {
    *host = (unsigned int)(gclobjtail2->hosts[gclobjtailindex2]);
  }
  return gclobjtail2->lobjs[gclobjtailindex2++];
} 

INLINE int gc_lobjmoreItems4_I() {
  return !((gclobjhead==gclobjtail2)&&(gclobjtailindex2==gclobjheadindex));
}

INLINE void gettype_size(void * ptr,
                         int * ttype,
                         unsigned int * tsize) {
  int type = ((int *)ptr)[0];
  unsigned int size = 0;
  if(type < NUMCLASSES) {
    // a normal object
    size = classsize[type];
  } else {
    // an array
    struct ArrayObject *ao=(struct ArrayObject *)ptr;
    unsigned int elementsize=classsize[type];
    unsigned int length=ao->___length___;
    size=sizeof(struct ArrayObject)+length*elementsize;
  } 
  *ttype = type;
  *tsize = size;
}

INLINE bool isLarge(void * ptr,
                    int * ttype,
                    unsigned int * tsize) {
  // check if a pointer is referring to a large object
  gettype_size(ptr, ttype, tsize);
  unsigned int bound = (BAMBOO_SMEM_SIZE);
  if(((unsigned int)ptr-gcbaseva) < (BAMBOO_LARGE_SMEM_BOUND)) {
    bound = (BAMBOO_SMEM_SIZE_L);
  }
  // ptr is a start of a block  OR it acrosses the boundary of current block
  return (((((unsigned int)ptr-gcbaseva)%(bound))==0)||
      ((bound-(((unsigned int)ptr-gcbaseva)%bound)) < (*tsize)));
} 

INLINE unsigned int hostcore(void * ptr) {
  // check the host core of ptr
  unsigned int host = 0;
  RESIDECORE(ptr, &host);
  return host;
} 

// NOTE: the objptr should not be NULL and should be a shared obj
INLINE void markObj(void * objptr, int linenum, void * ptr, int ii) {
  unsigned int host = hostcore(objptr);
  if(BAMBOO_NUM_OF_CORE == host) {
    // on this core
    BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
    if(((struct ___Object___ *)objptr)->marked == INIT) {
      // this is the first time that this object is discovered,
      // set the flag as DISCOVERED
      ((struct ___Object___ *)objptr)->marked = DISCOVERED;
      BAMBOO_CACHE_FLUSH_LINE(objptr);
      gc_enqueue_I(objptr);
    }
    BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
  } else {
    // check if this obj has been forwarded
    if(!MGCHashcontains(gcforwardobjtbl, (int)objptr)) {
      // send a msg to host informing that objptr is active
      send_msg_2(host, GCMARKEDOBJ, objptr, false);
      GCPROFILE_RECORD_FORWARD_OBJ();
      gcself_numsendobjs++;
      MGCHashadd(gcforwardobjtbl, (int)objptr);
    }
  }
} 

// enqueue root objs
INLINE void tomark(struct garbagelist * stackptr) {
  if(MARKPHASE != gcphase) {
    BAMBOO_EXIT(0xb010);
  }
  gcbusystatus = true;
  gcnumlobjs = 0;

  int i,j;
  // enqueue current stack
  while(stackptr!=NULL) {
    for(i=0; i<stackptr->size; i++) {
      if(stackptr->array[i] != NULL) {
        markObj(stackptr->array[i], __LINE__, stackptr->array[i], i);
      }
    }
    stackptr=stackptr->next;
  }

  // enqueue static pointers global_defs_p
  if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
    struct garbagelist * staticptr=(struct garbagelist *)global_defs_p;
    while(staticptr != NULL) {
      for(i=0; i<staticptr->size; i++) {
        if(staticptr->array[i] != NULL) {
          markObj(staticptr->array[i], __LINE__, staticptr->array[i], i);
        }
      }
      staticptr = staticptr->next;
    }
  }

#ifdef TASK
  // enqueue objectsets
  if(BAMBOO_NUM_OF_CORE < NUMCORESACTIVE) {
    for(i=0; i<NUMCLASSES; i++) {
      struct parameterwrapper ** queues = objectqueues[BAMBOO_NUM_OF_CORE][i];
      int length = numqueues[BAMBOO_NUM_OF_CORE][i];
      for(j = 0; j < length; ++j) {
        struct parameterwrapper * parameter = queues[j];
        struct ObjectHash * set=parameter->objectset;
        struct ObjectNode * ptr=set->listhead;
        while(ptr!=NULL) {
          markObj((void *)ptr->key, __LINE__, ptr, 0);
          ptr=ptr->lnext;
        }
      }
    }
  }

  // euqueue current task descriptor
  if(currtpd != NULL) {
    for(i=0; i<currtpd->numParameters; i++) {
      // currtpd->parameterArray[i] can not be NULL
      markObj(currtpd->parameterArray[i],__LINE__,currtpd->parameterArray[i],i);
    }
  }

  // euqueue active tasks
  if(activetasks != NULL) {
    struct genpointerlist * ptr=activetasks->list;
    while(ptr!=NULL) {
      struct taskparamdescriptor *tpd=ptr->src;
      int i;
      for(i=0; i<tpd->numParameters; i++) {
        // the tpd->parameterArray[i] can not be NULL
        markObj(tpd->parameterArray[i], __LINE__, tpd->parameterArray[i], i);
      }
      ptr=ptr->inext;
    }
  }

  // enqueue cached transferred obj
  struct QueueItem * tmpobjptr =  getHead(&objqueue);
  while(tmpobjptr != NULL) {
    struct transObjInfo * objInfo=(struct transObjInfo *)(tmpobjptr->objectptr);
    // the objptr can not be NULL
    markObj(objInfo->objptr, __LINE__, objInfo->objptr, 0);
    tmpobjptr = getNextQueueItem(tmpobjptr);
  }

  // enqueue cached objs to be transferred
  struct QueueItem * item = getHead(totransobjqueue);
  while(item != NULL) {
    struct transObjInfo * totransobj=(struct transObjInfo *)(item->objectptr);
    // the objptr can not be NULL
    markObj(totransobj->objptr, __LINE__, totransobj->objptr, 0);
    item = getNextQueueItem(item);
  } // while(item != NULL)

  // enqueue lock related info
  for(i = 0; i < runtime_locklen; ++i) {
    if(runtime_locks[i].redirectlock != NULL) {
      markObj((void *)(runtime_locks[i].redirectlock), __LINE__, 
          (void *)(runtime_locks[i].redirectlock), 0);
    }
    if(runtime_locks[i].value != NULL) {
      markObj((void *)(runtime_locks[i].value), __LINE__, 
          (void *)(runtime_locks[i].value), i);
    }
  }
#endif 

#ifdef MGC
  // enqueue global thread queue
  if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
    lockthreadqueue();
    unsigned int thread_counter = *((unsigned int*)(bamboo_thread_queue+1));
    if(thread_counter > 0) {
      unsigned int start = *((unsigned int*)(bamboo_thread_queue+2));
      for(i = thread_counter; i > 0; i--) {
        // the thread obj can not be NULL
        markObj((void *)bamboo_thread_queue[4+start], __LINE__,
            (void *)bamboo_thread_queue[4+start], 0);
        start = (start+1)&bamboo_max_thread_num_mask;
      }
    }
  }

  // enqueue the bamboo_threadlocks
  for(i = 0; i < bamboo_threadlocks.index; i++) {
    // the locks can not be NULL
    markObj((void *)(bamboo_threadlocks.locks[i].object), __LINE__,
        (void *)(bamboo_threadlocks.locks[i].object), i);
  }

  // enqueue the bamboo_current_thread
  if(bamboo_current_thread != 0) {
    markObj((void *)bamboo_current_thread, __LINE__, 
        (void *)bamboo_current_thread, 0);
  }
#endif
}

INLINE void scanPtrsInObj(void * ptr,
                          int type) {
  // scan all pointers in ptr
  unsigned int * pointer;
  pointer=pointerarray[type];
  if (pointer==0) {
    /* Array of primitives */
    pointer=pointerarray[OBJECTTYPE];
    //handle object class
    int size=pointer[0];
    int i;
    for(i=1; i<=size; i++) {
      unsigned int offset=pointer[i];
      void * objptr=*((void **)(((char *)ptr)+offset));
      if(objptr != NULL) {
        markObj(objptr, __LINE__, ptr, i);
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
        markObj(objptr, __LINE__, ptr, j);
      }
    }
    {
      pointer=pointerarray[OBJECTTYPE];
      //handle object class
      int size=pointer[0];
      int i;
      for(i=1; i<=size; i++) {
        unsigned int offset=pointer[i];
        void * objptr=*((void **)(((char *)ptr)+offset));
        if(objptr != NULL) {
          markObj(objptr, __LINE__, ptr, i);
        }
     }
    }
  } else {
    int size=pointer[0];
    int i;
    for(i=1; i<=size; i++) {
      unsigned int offset=pointer[i];
      void * objptr=*((void **)(((char *)ptr)+offset));
      if(objptr != NULL) {
        markObj(objptr, __LINE__, ptr, i);
      }
    }
  }
}

INLINE void mark(bool isfirst,
                 struct garbagelist * stackptr) {
  if(isfirst) {
    // enqueue root objs
    tomark(stackptr);
    gccurr_heaptop = 0; // record the size of all active objs in this core
                        // aligned but does not consider block boundaries
    gcmarkedptrbound = 0;
  }
  unsigned int isize = 0;
  bool sendStall = false;
  // mark phase
  while(MARKPHASE == gcphase) {
    int counter = 0;
    while(true) {
      BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
      if(!gc_moreItems2_I()) {
        BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
        break;
      }
      sendStall = false;
      gcbusystatus = true;
      unsigned int ptr = gc_dequeue2_I();
      BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();

      unsigned int size = 0;
      unsigned int isize = 0;
      unsigned int type = 0;
      // check if it is a local obj on this core
      if(((struct ___Object___ *)ptr)->marked!=DISCOVERED) {
        // ptr has been marked
        continue;
      } else if(isLarge(ptr, &type, &size)) {
        // ptr is a large object and not marked or enqueued
        BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
        gc_lobjenqueue_I(ptr, size, BAMBOO_NUM_OF_CORE);
        gcnumlobjs++;
        BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
        // mark this obj
        ((struct ___Object___ *)ptr)->marked = MARKED;
        BAMBOO_CACHE_FLUSH_LINE(ptr);
      } else {
        // ptr is an unmarked active object on this core
        ALIGNSIZE(size, &isize);
        gccurr_heaptop += isize;
        // mark this obj
        ((struct ___Object___ *)ptr)->marked = MARKED;
        BAMBOO_CACHE_FLUSH_LINE(ptr);

        if((unsigned int)(ptr + size) > (unsigned int)gcmarkedptrbound) {
          gcmarkedptrbound = (unsigned int)(ptr + size);
        }
      }

      scanPtrsInObj(ptr, type);      
    }   
    gcbusystatus = false;
    // send mark finish msg to core coordinator
    if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
      int entry_index = 0;
      if(waitconfirm)  {
        // phase 2
        entry_index = (gcnumsrobjs_index == 0) ? 1 : 0;
      } else {
        // phase 1
        entry_index = gcnumsrobjs_index;
      }
      gccorestatus[BAMBOO_NUM_OF_CORE] = 0;
      gcnumsendobjs[entry_index][BAMBOO_NUM_OF_CORE]=gcself_numsendobjs;
      gcnumreceiveobjs[entry_index][BAMBOO_NUM_OF_CORE]=gcself_numreceiveobjs;
      gcloads[BAMBOO_NUM_OF_CORE] = gccurr_heaptop;
    } else {
      if(!sendStall) {
        send_msg_4(STARTUPCORE, GCFINISHMARK, BAMBOO_NUM_OF_CORE,
            gcself_numsendobjs, gcself_numreceiveobjs, false);
        sendStall = true;
      }
    }

    if(BAMBOO_NUM_OF_CORE == STARTUPCORE) {
      return;
    }
  } 

  BAMBOO_CACHE_MF();
} 

#endif // MULTICORE_GC
