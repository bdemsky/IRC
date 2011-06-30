#ifdef MULTICORE_GC
#include "runtime.h"
#include "multicoreruntime.h"
#include "GenericHashtable.h"
#include "gcqueue.h"
#include "multicoregcmark.h"
#include "multicoregarbage.h"
#include "markbit.h"

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

INLINE void gettype_size(void * ptr, int * ttype, unsigned int * tsize) {
  int type = ((int *)ptr)[0];
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
  // check if a pointer refers to a large object
  gettype_size(ptr, ttype, tsize);
  unsigned INTPTR blocksize = (((unsigned INTPTR)(ptr-gcbaseva)) < BAMBOO_LARGE_SMEM_BOUND)? BAMBOO_SMEM_SIZE_L:BAMBOO_SMEM_SIZE;

  // ptr is a start of a block  OR it crosses the boundary of current block
  return (*tsize) > blocksize;
}

//push the null check into the mark macro

//#define MARKOBJ(objptr) {void * marktmpptr=objptr; if (marktmpptr!=NULL) {markObj(marktmpptr);if ((marktmpptr<gcbaseva)||(marktmpptr>(gcbaseva+BAMBOO_SHARED_MEM_SIZE))) tprintf("Bad pointer %x in line %u\n",marktmpptr, __LINE__);  }}

#define MARKOBJ(objptr) {void * marktmpptr=objptr; if (marktmpptr!=NULL) {markObj(marktmpptr);}}

#define MARKOBJNONNULL(objptr) {markObj(objptr);}

// NOTE: the objptr should not be NULL and should be a shared obj
void markObj(void * objptr) {
  unsigned int host = hostcore(objptr);
  if(BAMBOO_NUM_OF_CORE == host) {
    // on this core
    if(!checkAndCondSetMark(objptr)) {
      // this is the first time that this object is discovered,
      // set the flag as DISCOVERED
      gc_enqueue(objptr);
    }
  } else {
    // check if this obj has been forwarded already
    if(MGCHashadd(gcforwardobjtbl, (unsigned int)objptr)) {
      // if not, send msg to host informing that objptr is active
      send_msg_2(host,GCMARKEDOBJ,objptr);
      GCPROFILE_RECORD_FORWARD_OBJ();
      gcself_numsendobjs++;
    }
  }
}

void markgarbagelist(struct garbagelist * listptr) {
  for(;listptr!=NULL;listptr=listptr->next) {
    int size=listptr->size;
    for(int i=0; i<size; i++) {
      MARKOBJ(listptr->array[i]);
    }
  }
}

// enqueue root objs
void tomark(struct garbagelist * stackptr) {
  BAMBOO_ASSERT(MARKPHASE == gc_status_info.gcphase);
  
  gc_status_info.gcbusystatus = true;
  
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
          MARKOBJNONNULL((void *)ptr->key);
        }
      }
    }
  }
  
  // enqueue current task descriptor
  if(currtpd != NULL) {
    for(int i=0; i<currtpd->numParameters; i++) {
      // currtpd->parameterArray[i] can not be NULL
      MARKOBJNONNULL(currtpd->parameterArray[i]);
    }
  }

  // enqueue active tasks
  if(activetasks != NULL) {
    struct genpointerlist * ptr=activetasks->list;
    for(;ptr!=NULL;ptr=ptr->inext) {
      struct taskparamdescriptor *tpd=ptr->src;
      for(int i=0; i<tpd->numParameters; i++) {
        // the tpd->parameterArray[i] can not be NULL
        MARKOBJNONNULL(tpd->parameterArray[i]);
      }
    }
  }

  // enqueue cached transferred obj
  struct QueueItem * tmpobjptr =  getHead(&objqueue);
  for(;tmpobjptr != NULL;tmpobjptr=getNextQueueItem(tmpobjptr)) {
    struct transObjInfo * objInfo=(struct transObjInfo *)(tmpobjptr->objectptr);
    // the objptr can not be NULL
    MARKOBJNONNULL(objInfo->objptr);
  }

  // enqueue cached objs to be transferred
  struct QueueItem * item = getHead(totransobjqueue);
  for(;item != NULL;item=getNextQueueItem(item)) {
    struct transObjInfo * totransobj=(struct transObjInfo *)(item->objectptr);
    // the objptr can not be NULL
    MARKOBJNONNULL(totransobj->objptr);
  }

  // enqueue lock related info
  for(int i = 0; i < runtime_locklen; i++) {
    MARKOBJ((void *)(runtime_locks[i].redirectlock));
    MARKOBJ((void *)(runtime_locks[i].value));
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
        MARKOBJNONNULL((void *)bamboo_thread_queue[4+start]);
        start = (start+1)&bamboo_max_thread_num_mask;
      }
    }
  }
  // enqueue the bamboo_threadlocks
  for(int i = 0; i < bamboo_threadlocks.index; i++) {
    // the locks can not be NULL
    MARKOBJNONNULL((void *)(bamboo_threadlocks.locks[i].object));
  }
  // enqueue the bamboo_current_thread
  MARKOBJ(bamboo_current_thread);
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
      MARKOBJ(objptr);
    }
#endif
  } else if (((unsigned int)pointer)==1) {
    /* Array of pointers */
    struct ArrayObject *ao=(struct ArrayObject *) ptr;
    int length=ao->___length___;
    for(int i=0; i<length; i++) {
      void *objptr=((void **)(((char *)&ao->___length___)+sizeof(int)))[i];
      MARKOBJ(objptr);
    }
#ifdef OBJECTHASPOINTERS
    pointer=pointerarray[OBJECTTYPE];
    //handle object class
    int size=pointer[0];
    for(int i=1; i<=size; i++) {
      unsigned int offset=pointer[i];
      void * objptr=*((void **)(((char *)ptr)+offset));
      MARKOBJ(objptr);
    }
#endif
  } else {
    /* Normal Object */
    int size=pointer[0];
    for(int i=1; i<=size; i++) {
      unsigned int offset=pointer[i];
      void * objptr=*((void **)(((char *)ptr)+offset));
      MARKOBJ(objptr);
    }
  }
}

void mark(struct garbagelist * stackptr) {
  // enqueue root objs
  tomark(stackptr);

  unsigned int isize = 0;
  gc_status_info.gcbusystatus = true;

  // mark phase
  while(MARKPHASE == gc_status_info.gcphase) {
    int counter = 0;
    while(gc_moreItems()) {
      gc_status_info.gcbusystatus = true;
      void * ptr = gc_dequeue();
      unsigned int size = 0;
      unsigned int type = 0;
      bool islarge=isLarge(ptr, &type, &size);
      unsigned int iunits = ALIGNUNITS(size);

      //debugging for the next five lines
#ifdef GC_DEBUG
      unsigned INTPTR alignsize=ALIGNOBJSIZE((unsigned INTPTR)(ptr-gcbaseva));
      unsigned INTPTR hibits=alignsize>>4;
      unsigned INTPTR lobits=(alignsize&15)<<1;
      unsigned INTPTR ohigh=gcmarktbl[hibits];
      unsigned INTPTR olow=gcmarktbl[hibits+1];
#endif
      setLengthMarked(ptr,iunits);

#ifdef GC_DEBUG
      unsigned int unit=getMarkedLength(ptr);
      if (unit!=iunits) {
	tprintf("Bad mark on %x %u!=%u\n", ptr, unit, iunits);
	tprintf("hibits=%x lobits=%x\n", hibits, lobits);
	tprintf("ohigh=%x olow=%x", ohigh, olow);
	unsigned INTPTR nhigh=gcmarktbl[hibits];
	unsigned INTPTR nlow=gcmarktbl[hibits+1];
	tprintf("nhigh=%x nlow=%x", nhigh, nlow);
      }
#endif
      if(islarge) {
        // ptr is a large object and not marked or enqueued
	printf("NEED TO SUPPORT LARGE OBJECTS!\n");
      } else {
        // ptr is an unmarked active object on this core
	unsigned int isize=iunits<<ALIGNMENTSHIFT;
        gccurr_heaptop += isize;
      }
      // scan the pointers in object
      scanPtrsInObj(ptr, type);
    }

    BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
    //make sure the queue is still empty...now we have interrupts off, things can't change on us...

    if (!gc_moreItems_I()) {
      // send mark finish msg to core coordinator
      if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
	int entry_index = waitconfirm ? (gcnumsrobjs_index==0) : gcnumsrobjs_index;
	gccorestatus[BAMBOO_NUM_OF_CORE] = 0;
	gcnumsendobjs[entry_index][BAMBOO_NUM_OF_CORE]=gcself_numsendobjs;
	gcnumreceiveobjs[entry_index][BAMBOO_NUM_OF_CORE]=gcself_numreceiveobjs;
	BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
	checkMarkStatus();
      } else {
	if(gc_status_info.gcbusystatus) {
	  gc_status_info.gcbusystatus = false;
	  send_msg_4(STARTUPCORE,GCFINISHMARK,BAMBOO_NUM_OF_CORE,gcself_numsendobjs,gcself_numreceiveobjs);
	}
	BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
      }
    }
  }
} 

#endif // MULTICORE_GC
