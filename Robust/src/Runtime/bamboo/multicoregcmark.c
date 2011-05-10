#ifdef MULTICORE_GC
#include "multicoregcmark.h"
#include "runtime.h"
#include "multicoreruntime.h"
#include "GenericHashtable.h"
#include "gcqueue.h"
#ifdef MGC
#include "runtime_arch.h"

extern int corenum;
#endif

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

INLINE void gettype_size(void * ptr, int * ttype, unsigned int * tsize) {
  int type = ((int *)ptr)[0];
  unsigned int size;
  if(type < NUMCLASSES) {
    // a normal object
    *tsize = classsize[type];
    *ttype = type;
  } else {
    // an array
    struct ArrayObject *ao=(struct ArrayObject *)ptr;
    unsigned int elementsize=classsize[type];
    unsigned int length=ao->___length___;
    *tsize = sizeof(struct ArrayObject)+length*elementsize;
    *ttype = type;
  } 
}

INLINE bool isLarge(void * ptr, int * ttype, unsigned int * tsize) {
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

//push the null check into the mark macro
//#define MARKOBJ(objptr, ii) {void * marktmpptr=objptr; if (marktmpptr!=NULL) markObj(marktmpptr, __LINE__, ii);}

//#define MARKOBJNONNULL(objptr, ii) {markObj(objptr, __LINE__, ii);}

#define MARKOBJ(objptr, ii) {void * marktmpptr=objptr; if (marktmpptr!=NULL) markObj(marktmpptr);}

#define MARKOBJNONNULL(objptr, ii) {markObj(objptr);}

// NOTE: the objptr should not be NULL and should be a shared obj
INLINE void markObj(void * objptr) {
  unsigned int host = hostcore(objptr);
  if(BAMBOO_NUM_OF_CORE == host) {
    // on this core
    if(((struct ___Object___ *)objptr)->marked == INIT) {
      // this is the first time that this object is discovered,
      // set the flag as DISCOVERED
      ((struct ___Object___ *)objptr)->marked = DISCOVERED;
      BAMBOO_CACHE_FLUSH_LINE(objptr);
      gc_enqueue(objptr);
    }
  } else {
    // check if this obj has been forwarded
    if(!MGCHashcontains(gcforwardobjtbl, (int)objptr)) {
      // send a msg to host informing that objptr is active
      send_msg_2(host,GCMARKEDOBJ,objptr);
      GCPROFILE_RECORD_FORWARD_OBJ();
      gcself_numsendobjs++;
      MGCHashadd(gcforwardobjtbl, (int)objptr);
    }
  }
} 

INLINE void markgarbagelist(struct garbagelist * listptr) {
  for(;listptr!=NULL;listptr=listptr->next) {
    int size=listptr->size;
    for(int i=0; i<size; i++) {
      MARKOBJ(listptr->array[i], i);
    }
  }
}

// enqueue root objs
INLINE void tomark(struct garbagelist * stackptr) {
  BAMBOO_ASSERT(MARKPHASE == gcphase);

  gcbusystatus = true;
  gcnumlobjs = 0;

  // enqueue current stack
  markgarbagelist(stackptr);

  // enqueue static pointers global_defs_p
  if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
    markgarbagelist((struct garbagelist *)global_defs_p);
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
          MARKOBJNONNULL((void *)ptr->key, 0);
        }
      }
    }
  }

  // euqueue current task descriptor
  if(currtpd != NULL) {
    for(int i=0; i<currtpd->numParameters; i++) {
      // currtpd->parameterArray[i] can not be NULL
      MARKOBJNONNULL(currtpd->parameterArray[i], i);
    }
  }

  // euqueue active tasks
  if(activetasks != NULL) {
    struct genpointerlist * ptr=activetasks->list;
    for(;ptr!=NULL;ptr=ptr->inext) {
      struct taskparamdescriptor *tpd=ptr->src;
      for(int i=0; i<tpd->numParameters; i++) {
        // the tpd->parameterArray[i] can not be NULL
        MARKOBJNONNULL(tpd->parameterArray[i], i);
      }
    }
  }

  // enqueue cached transferred obj
  struct QueueItem * tmpobjptr =  getHead(&objqueue);
  for(;tmpobjptr != NULL;tmpobjptr=getNextQueueItem(tmpobjptr)) {
    struct transObjInfo * objInfo=(struct transObjInfo *)(tmpobjptr->objectptr);
    // the objptr can not be NULL
    MARKOBJNONNULL(objInfo->objptr, 0);
  }

  // enqueue cached objs to be transferred
  struct QueueItem * item = getHead(totransobjqueue);
  for(;item != NULL;item=getNextQueueItem(item)) {
    struct transObjInfo * totransobj=(struct transObjInfo *)(item->objectptr);
    // the objptr can not be NULL
    MARKOBJNONNULL(totransobj->objptr, 0);
  }

  // enqueue lock related info
  for(int i = 0; i < runtime_locklen; i++) {
    MARKOBJ((void *)(runtime_locks[i].redirectlock), 0);
    MARKOBJ((void *)(runtime_locks[i].value), i);
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
        MARKOBJNONNULL((void *)bamboo_thread_queue[4+start], 0);
        start = (start+1)&bamboo_max_thread_num_mask;
      }
    }
  }

  // enqueue the bamboo_threadlocks
  for(int i = 0; i < bamboo_threadlocks.index; i++) {
    // the locks can not be NULL
    MARKOBJNONNULL((void *)(bamboo_threadlocks.locks[i].object), i);
  }

  // enqueue the bamboo_current_thread
  MARKOBJ((void *)bamboo_current_thread, 0);
#endif
}

INLINE void scanPtrsInObj(void * ptr, int type) {
  // scan all pointers in ptr
  unsigned int * pointer = pointerarray[type];
  if (pointer==0) {
    /* Array of primitives */
#ifdef OBJECTHASPOINTERS
    pointer=pointerarray[OBJECTTYPE];
    //handle object class
    int size=pointer[0];
    for(int i=1; i<=size; i++) {
      unsigned int offset=pointer[i];
      void * objptr=*((void **)(((char *)ptr)+offset));
      MARKOBJ(objptr, i);
    }
#endif
  } else if (((unsigned int)pointer)==1) {
    /* Array of pointers */
    struct ArrayObject *ao=(struct ArrayObject *) ptr;
    int length=ao->___length___;
    for(int i=0; i<length; i++) {
      void *objptr=((void **)(((char *)&ao->___length___)+sizeof(int)))[i];
      MARKOBJ(objptr, i);
    }
#ifdef OBJECTHASPOINTERS
    pointer=pointerarray[OBJECTTYPE];
    //handle object class
    int size=pointer[0];
    for(int i=1; i<=size; i++) {
      unsigned int offset=pointer[i];
      void * objptr=*((void **)(((char *)ptr)+offset));
      MARKOBJ(objptr, i);
    }
#endif
  } else {
    /* Normal Object */
    int size=pointer[0];
    for(int i=1; i<=size; i++) {
      unsigned int offset=pointer[i];
      void * objptr=*((void **)(((char *)ptr)+offset));
      MARKOBJ(objptr, i);
    }
  }
}

INLINE void mark(bool isfirst, struct garbagelist * stackptr) {
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
    while(gc_moreItems2()) {
      sendStall = false;
      gcbusystatus = true;
      unsigned int ptr = gc_dequeue2();

      unsigned int size = 0;
      unsigned int isize = 0;
      unsigned int type = 0;
      // check if it is a local obj on this core
      if(((struct ___Object___ *)ptr)->marked!=DISCOVERED) {
        // ptr has been marked
        continue;
      } else if(isLarge(ptr, &type, &size)) {
        // ptr is a large object and not marked or enqueued
        gc_lobjenqueue(ptr, size, BAMBOO_NUM_OF_CORE);
        gcnumlobjs++;
      } else {
        // ptr is an unmarked active object on this core
        ALIGNSIZE(size, &isize);
        gccurr_heaptop += isize;
	
        if((unsigned int)(ptr + size) > (unsigned int)gcmarkedptrbound) {
          gcmarkedptrbound = (unsigned int)(ptr + size);
        }
      }
      // mark this obj
      ((struct ___Object___ *)ptr)->marked = MARKED;
      BAMBOO_CACHE_FLUSH_LINE(ptr);
      // scan the pointers in object
      scanPtrsInObj(ptr, type);      
    }   
    gcbusystatus = false;
    // send mark finish msg to core coordinator
    if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
      int entry_index = waitconfirm ? (gcnumsrobjs_index==0) : gcnumsrobjs_index;
      gccorestatus[BAMBOO_NUM_OF_CORE] = 0;
      gcnumsendobjs[entry_index][BAMBOO_NUM_OF_CORE]=gcself_numsendobjs;
      gcnumreceiveobjs[entry_index][BAMBOO_NUM_OF_CORE]=gcself_numreceiveobjs;
      gcloads[BAMBOO_NUM_OF_CORE] = gccurr_heaptop;
    } else {
      if(!sendStall) {
        send_msg_4(STARTUPCORE,GCFINISHMARK,BAMBOO_NUM_OF_CORE,gcself_numsendobjs,gcself_numreceiveobjs);
        sendStall = true;
      }
    }
    
    if(BAMBOO_NUM_OF_CORE == STARTUPCORE)
      return;
  }

  BAMBOO_CACHE_MF();
} 

#endif // MULTICORE_GC
