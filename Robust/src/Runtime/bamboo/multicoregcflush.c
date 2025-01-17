#ifdef MULTICORE_GC
#include "multicoregccompact.h"
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
//#define UPDATEOBJ(obj) {void *updatetmpptr=obj; if (updatetmpptr!=NULL) obj=updateObj(updatetmpptr);if (obj<gcbaseva) tprintf("BAD PTR %x to %x in %u\n", updatetmpptr, obj, __LINE__);}
#define UPDATEOBJ(obj) {void *updatetmpptr=obj; if (updatetmpptr!=NULL) {obj=updateObj(updatetmpptr);}}
//if (!ISVALIDPTR(obj)) tprintf("Mapping problem for object %x -> %x, mark=%u, line=%u\n", updatetmpptr, obj, getMarkedLength(updatetmpptr),__LINE__);}}

#define UPDATEOBJNONNULL(obj) {void *updatetmpptr=obj; obj=updateObj(updatetmpptr);}
// if (!ISVALIDPTR(obj)) tprintf("Mapping parameter for object %x -> %x, mark=%u, line=%u\n", updatetmpptr, obj, getMarkedLength(updatetmpptr),__LINE__);}

void updategarbagelist(struct garbagelist *listptr) {
  for(;listptr!=NULL; listptr=listptr->next) {
    for(int i=0; i<listptr->size; i++) {
      UPDATEOBJ(listptr->array[i]);
    }
  }
}

void updateRuntimePtrs(struct garbagelist * stackptr) {
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
          UPDATEOBJNONNULL(ptr->key);
        }
        ObjectHashrehash(set);
      }
    }
  }

  // update current task descriptor
  if(currtpd != NULL) {
    for(int i=0; i<currtpd->numParameters; i++) {
      // the parameter can not be NULL
      UPDATEOBJNONNULL(currtpd->parameterArray[i]);
    }
  }

  // update active tasks
  if(activetasks != NULL) {
    for(struct genpointerlist * ptr=activetasks->list;ptr!=NULL;ptr=ptr->inext){
      struct taskparamdescriptor *tpd=ptr->src;
      for(int i=0; i<tpd->numParameters; i++) {
        // the parameter can not be NULL
	UPDATEOBJNONNULL(tpd->parameterArray[i]);
      }
    }
    genrehash(activetasks);
  }

  // update cached transferred obj
  for(struct QueueItem * tmpobjptr =  getHead(&objqueue);tmpobjptr != NULL;tmpobjptr = getNextQueueItem(tmpobjptr)) {
    struct transObjInfo * objInfo=(struct transObjInfo *)(tmpobjptr->objectptr);
    // the obj can not be NULL
    UPDATEOBJNONNULL(objInfo->objptr);
  }

  // update cached objs to be transferred
  for(struct QueueItem * item = getHead(totransobjqueue);item != NULL;item = getNextQueueItem(item)) {
    struct transObjInfo * totransobj = (struct transObjInfo *)(item->objectptr);
    // the obj can not be NULL
    UPDATEOBJNONNULL(totransobj->objptr);
  }  

  // enqueue lock related info
  for(int i = 0; i < runtime_locklen; ++i) {
    UPDATEOBJ(runtime_locks[i].redirectlock);
    UPDATEOBJ(runtime_locks[i].value);
  }
#endif

#ifdef MGC
  // update the bamboo_threadlocks
  for(int i = 0; i < bamboo_threadlocks.index; i++) {
    // the locked obj can not be NULL
    UPDATEOBJNONNULL(bamboo_threadlocks.locks[i].object);
  }

  // update the bamboo_current_thread
  UPDATEOBJ(bamboo_current_thread);

  // update global thread queue
  if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
    unsigned int thread_counter = *((unsigned int*)(bamboo_thread_queue+1));
    if(thread_counter > 0) {
      unsigned int start = *((unsigned int*)(bamboo_thread_queue+2));
      for(int i = thread_counter; i > 0; i--) {
        // the thread obj can not be NULL
        UPDATEOBJNONNULL(*((void **)&bamboo_thread_queue[4+start]));
        start = (start+1)&bamboo_max_thread_num_mask;
      }
    }
    unlockthreadqueue();
  }
#endif
}

void updatePtrsInObj(void * ptr) {
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
      UPDATEOBJ(*((void **)(((char *)ptr)+offset)));
    }
#endif
  } else if (((unsigned int)pointer)==1) {
    /* Array of pointers */
    struct ArrayObject *ao=(struct ArrayObject *) ptr;
    int length=ao->___length___;
    for(int j=0; j<length; j++) {
      UPDATEOBJ(((void **)(((char *)&ao->___length___)+sizeof(int)))[j]);
    }
#ifdef OBJECTHASPOINTERS
    pointer=pointerarray[OBJECTTYPE];
    //handle object class
    unsigned int size=pointer[0];
    
    for(int i=1; i<=size; i++) {
      unsigned int offset=pointer[i];     
      UPDATEOBJ(*((void **)(((char *)ptr)+offset)));
    }
#endif
  } else {
    unsigned int size=pointer[0];
    
    for(int i=1; i<=size; i++) {
      unsigned int offset=pointer[i];
      UPDATEOBJ(*((void **)(((char *)ptr)+offset)));
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
	if (arrayoffset>=origendoffset) {
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
    
    if (objlength!=NOTMARKED) {
      void *dstptr=gcmappingtbl[OBJMAPPINGINDEX(origptr)];
      unsigned int length=ALIGNSIZETOBYTES(objlength);
      void *endtoptr=dstptr+length;

      if (endtoptr>tobound||dstptr<tobase) {
	//get use the next block of memory
	orig->ptr=origptr;
	return dstptr;
      }

#ifdef GC_DEBUG
      {
	unsigned int size;
	unsigned int type;
	gettype_size(origptr, &type, &size);
	size=((size-1)&(~(ALIGNMENTSIZE-1)))+ALIGNMENTSIZE;
	if (size!=length) {
	  tprintf("BAD SIZE %u!=%u t=%u %x->%x\n",size,length,type, origptr, dstptr);
	  tprintf("origbase=%x origbound=%x\n",orig->base, origbound);
	  tprintf("tobase=%x tobound=%x\n",tobase, tobound);
	}
      }

      if (dstptr>origptr) {
	tprintf("move up %x -> %x\n", origptr, dstptr);
      }
      if (tmplast>origptr) {
	tprintf("Overlap with last object\n");
      }

      tmplast=dstptr+length;
#endif

      /* Move the object */
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

      /* Update the pointers in the object */
      updatePtrsInObj(dstptr);

      /* Clear the mark */
      clearMark(origptr);
      
      //good to move objects and update pointers
      origptr+=length;
    } else
      origptr+=ALIGNMENTSIZE;
  }
  orig->ptr=origptr;
  return NULL;
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
    void *dstptr=updateblocks(orig, to);
    if (dstptr) {
      //need more memory to compact into
      block_t blockindex;
      BLOCKINDEX(blockindex, dstptr);
      unsigned int blockcore;
      BLOCK2CORE(blockcore, blockindex);
      to->base=OFFSET2BASEVA(blockindex)+gcbaseva;
      to->bound=BOUNDPTR(blockindex)+gcbaseva;
      if (blockcore!=BAMBOO_NUM_OF_CORE) {
	//we have someone elses memory...need to ask to use it
	//first set flag to false
	blockgranted=false;
	send_msg_3(blockcore,GCREQBLOCK, BAMBOO_NUM_OF_CORE, to->bound);
	//wait for permission
	while(!blockgranted)
	  ;
      }
    }
    if (orig->ptr==orig->bound) {
      //inform others that we are done with previous block
      updateOrigPtr(orig->bound);

      //need more data to compact
      //increment the core
      orig->localblocknum++;
      BASEPTR(orig->base,BAMBOO_NUM_OF_CORE, orig->localblocknum);
      orig->ptr=orig->base;
      orig->bound = orig->base + BLOCKSIZE(orig->localblocknum);
      if (orig->base >= gcbaseva+BAMBOO_SHARED_MEM_SIZE) {
	break;
      }
    }
  }
}

void updateheap() {
  // initialize structs for compacting
  struct moveHelper orig;
  struct moveHelper to;
  initOrig_Dst(&orig, &to);
  updatehelper(&orig, &to);
}

void update(struct garbagelist * stackptr) {
  updateRuntimePtrs(stackptr);
  updateheap();
  // send update finish message to core coordinator
  if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
    gccorestatus[BAMBOO_NUM_OF_CORE] = 0;
  } else {
    send_msg_2(STARTUPCORE,GCFINISHUPDATE,BAMBOO_NUM_OF_CORE);
  }
} 

#endif // MULTICORE_GC
