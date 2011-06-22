#ifdef MULTICORE_GC
#include "multicoregcflush.h"
#include "multicoreruntime.h"
#include "ObjectHash.h"
#include "GenericHashtable.h"
#include "gcqueue.h"
#include "markbit.h"

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
#define updateObj(objptr) gcmappingtbl[OBJMAPPINGINDEX(objptr)]
#define UPDATEOBJ(obj, tt) {void *updatetmpptr=obj; if (updatetmpptr!=NULL) obj=updateObj(updatetmpptr);}
#define UPDATEOBJNONNULL(obj, tt) {void *updatetmpptr=obj; obj=updateObj(updatetmpptr);}

#define dbpr() if (STARTUPCORE==BAMBOO_NUM_OF_CORE) tprintf("FL: %d\n", __LINE__);

INLINE void updategarbagelist(struct garbagelist *listptr) {
  for(;listptr!=NULL; listptr=listptr->next) {
    for(int i=0; i<listptr->size; i++) {
      UPDATEOBJ(listptr->array[i], i);
    }
  }
}

INLINE void updateRuntimePtrs(struct garbagelist * stackptr) {
  // update current stack
  updategarbagelist(stackptr);

  // update static pointers global_defs_p
  if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
    updategarbagelist((struct garbagelist *)global_defs_p);
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
          UPDATEOBJNONNULL(ptr->key, 0);
        }
        ObjectHashrehash(set);
      }
    }
  }

  // update current task descriptor
  if(currtpd != NULL) {
    for(int i=0; i<currtpd->numParameters; i++) {
      // the parameter can not be NULL
      UPDATEOBJNONNULL(currtpd->parameterArray[i], i);
    }
  }

  // update active tasks
  if(activetasks != NULL) {
    for(struct genpointerlist * ptr=activetasks->list;ptr!=NULL;ptr=ptr->inext){
      struct taskparamdescriptor *tpd=ptr->src;
      for(int i=0; i<tpd->numParameters; i++) {
        // the parameter can not be NULL
	UPDATEOBJNONNULL(tpd->parameterArray[i], i);
      }
    }
    genrehash(activetasks);
  }

  // update cached transferred obj
  for(struct QueueItem * tmpobjptr =  getHead(&objqueue);tmpobjptr != NULL;tmpobjptr = getNextQueueItem(tmpobjptr)) {
    struct transObjInfo * objInfo=(struct transObjInfo *)(tmpobjptr->objectptr);
    // the obj can not be NULL
    UPDATEOBJNONNULL(objInfo->objptr, 0);
  }

  // update cached objs to be transferred
  for(struct QueueItem * item = getHead(totransobjqueue);item != NULL;item = getNextQueueItem(item)) {
    struct transObjInfo * totransobj = (struct transObjInfo *)(item->objectptr);
    // the obj can not be NULL
    UPDATEOBJNONNULL(totransobj->objptr, 0);
  }  

  // enqueue lock related info
  for(int i = 0; i < runtime_locklen; ++i) {
    UPDATEOBJ(runtime_locks[i].redirectlock, i);
    UPDATEOBJ(runtime_locks[i].value, i);
  }
#endif

#ifdef MGC
  // update the bamboo_threadlocks
  for(int i = 0; i < bamboo_threadlocks.index; i++) {
    // the locked obj can not be NULL
    UPDATEOBJNONNULL(bamboo_threadlocks.locks[i].object, i);
  }

  // update the bamboo_current_thread
  UPDATEOBJ(bamboo_current_thread, 0);

  // update global thread queue
  if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
    unsigned int thread_counter = *((unsigned int*)(bamboo_thread_queue+1));
    if(thread_counter > 0) {
      unsigned int start = *((unsigned int*)(bamboo_thread_queue+2));
      for(int i = thread_counter; i > 0; i--) {
        // the thread obj can not be NULL
        UPDATEOBJNONNULL(bamboo_thread_queue[4+start], 0);
        start = (start+1)&bamboo_max_thread_num_mask;
      }
    }
    unlockthreadqueue();
  }
#endif
}

INLINE void updatePtrsInObj(void * ptr) {
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
      UPDATEOBJ(*((void **)(((char *)ptr)+offset)), i);
    }
#endif
  } else if (((unsigned int)pointer)==1) {
    /* Array of pointers */
    struct ArrayObject *ao=(struct ArrayObject *) ptr;
    int length=ao->___length___;
    for(int j=0; j<length; j++) {
      UPDATEOBJ(((void **)(((char *)&ao->___length___)+sizeof(int)))[j], j);
    }
#ifdef OBJECTHASPOINTERS
    pointer=pointerarray[OBJECTTYPE];
    //handle object class
    unsigned int size=pointer[0];
    
    for(int i=1; i<=size; i++) {
      unsigned int offset=pointer[i];     
      UPDATEOBJ(*((void **)(((char *)ptr)+offset)), i);
    }
#endif
  } else {
    unsigned int size=pointer[0];
    
    for(int i=1; i<=size; i++) {
      unsigned int offset=pointer[i];
      UPDATEOBJ(*((void **)(((char *)ptr)+offset)), i);
    }
  }  
}

/* This function is performance critical...  spend more time optimizing it */

void * updateblocks(struct moveHelper * orig, struct moveHelper * to) {
  void *tobase=to->base;
  void *tobound=to->bound;
  void *origptr=orig->ptr;
  void *origbound=orig->bound;
  unsigned INTPTR origendoffset=ALIGNTOTABLEINDEX((unsigned INTPTR)(origbound-gcbaseva));
  unsigned int objlength;

  while(origptr<origbound) {
    //Try to skip over stuff fast first
    unsigned INTPTR offset=(unsigned INTPTR) (origptr-gcbaseva);
    unsigned INTPTR arrayoffset=ALIGNTOTABLEINDEX(offset);
    if (!gcmarktbl[arrayoffset]) {
      do {
	arrayoffset++;
	if (arrayoffset<origendoffset) {
	  //finished with block...
	  origptr=origbound;
	  orig->ptr=origptr;
	  return NULL;
	}
      } while(!gcmarktbl[arrayoffset]);
      origptr=CONVERTTABLEINDEXTOPTR(arrayoffset);
    }

    //Scan more carefully next
    objlength=getMarkedLength(origptr);
    void *dstptr=gcmappingtbl[OBJMAPPINGINDEX(origptr)];
    
    if (objlength!=NOTMARKED) {
      unsigned int length=ALIGNSIZETOBYTES(objlength);
      void *endtoptr=dstptr+length;

      if (endtoptr>tobound||endtoptr<tobase) {
	//get use the next block of memory
	orig->ptr=origptr;
	if (BAMBOO_NUM_OF_CORE==STARTUPCORE) {
	  tprintf("dstptr=%x\n",dstptr);
	  tprintf("endtoptrptr=%x\n",endtoptr);
	  tprintf("tobound=%x\n",tobound);
	  tprintf("tobase=%x\n",tobase);
	  tprintf("origptr=%x\n",origptr);
	  tprintf("length=%d\n",length);
	}
	return dstptr;
      }
      
      /* Move the object */
      if(origptr <= endtoptr) {
        memmove(dstptr, origptr, length);
      } else {
        memcpy(dstptr, origptr, length);
      }
      
      /* Update the pointers in the object */
      updatePtrsInObj(dstptr);

      /* Clear the mark */
      clearMark(origptr);

      //good to move objects and update pointers
      origptr+=length;
    } else
      origptr+=ALIGNMENTSIZE;
  }
}

void updateOrigPtr(void *currtop) {
  //Ordering is important...
  //Update heap top first...
  update_origblockptr=currtop;

  //Then check for waiting cores
  if (origarraycount>0) {
    for(int i=0;i<NUMCORES4GC;i++) {
      void *ptr=origblockarray[i];
      if (ptr!=NULL&&ptr<currtop) {
	origarraycount--;
	origblockarray[i]=NULL;
	send_msg_1(i,GCGRANTBLOCK);
      }
    }
  }
}

void updatehelper(struct moveHelper * orig,struct moveHelper * to) {
  while(true) {
    dbpr();
    void *dstptr=updateblocks(orig, to);
    dbpr();
    if (dstptr) {
      dbpr();
      printf("M: %x\n", dstptr);
   //need more memory to compact into
      block_t blockindex;
      BLOCKINDEX(blockindex, dstptr);
      unsigned int corenum;
      BLOCK2CORE(corenum, blockindex);
      to->base=OFFSET2BASEVA(blockindex)+gcbaseva;
      to->bound=BOUNDPTR(blockindex)+gcbaseva;
      if (corenum!=BAMBOO_NUM_OF_CORE) {
	//we have someone elses memory...need to ask to use it
	//first set flag to false
	blockgranted=false;
	send_msg_3(corenum,GCREQBLOCK, BAMBOO_NUM_OF_CORE, to->bound);
	//wait for permission
	while(!blockgranted)
	  ;
      }
    }
    dbpr();
    if (orig->ptr==orig->bound) {
      dbpr();
      //inform others that we are done with previous block
      updateOrigPtr(orig->bound);

      //need more data to compact
      //increment the core
      orig->localblocknum++;
      BASEPTR(orig->base,BAMBOO_NUM_OF_CORE, orig->localblocknum);
      update_origblockptr=orig->base;
      orig->ptr=orig->base;
      orig->bound = orig->base + BLOCKSIZE(orig->localblocknum);
      if (orig->base >= gcbaseva+BAMBOO_SHARED_MEM_SIZE) {
	//free our entire memory for others to use
	break;
      }
    }
    dbpr();
  }
}

void updateheap() {
  // initialize structs for compacting
  struct moveHelper orig={0,NULL,NULL,0,NULL,0,0,0,0};
  struct moveHelper to={0,NULL,NULL,0,NULL,0,0,0,0};
  dbpr();
  initOrig_Dst(&orig, &to);
  dbpr();
  updatehelper(&orig, &to);
  dbpr();
}

void update(struct garbagelist * stackptr) {
  dbpr();
  updateRuntimePtrs(stackptr);
  dbpr();
  updateheap();
  dbpr();
  // send update finish message to core coordinator
  if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
    gccorestatus[BAMBOO_NUM_OF_CORE] = 0;
  } else {
    send_msg_2(STARTUPCORE,GCFINISHUPDATE,BAMBOO_NUM_OF_CORE);
  }
} 

#endif // MULTICORE_GC
