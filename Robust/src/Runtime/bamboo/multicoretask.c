#ifdef TASK
#include "runtime.h"
#include "multicoreruntime.h"
#include "multicoretaskprofile.h"
#include "multicoretask.h"

#ifndef INLINE
#define INLINE    inline __attribute__((always_inline))
#endif 

//  data structures for task invocation
struct genhashtable * activetasks;
struct taskparamdescriptor * currtpd;
struct LockValue runtime_locks[MAXTASKPARAMS];
int runtime_locklen;

// specific functions used inside critical sections
void enqueueObject_I(void * ptr,
                     struct parameterwrapper ** queues,
                     int length);
int enqueuetasks_I(struct parameterwrapper *parameter,
                   struct parameterwrapper *prevptr,
                   struct ___Object___ *ptr,
                   int * enterflags,
                   int numenterflags);

INLINE void initlocktable() {
#ifdef MULTICORE_GC
  // do nothing
#else
  // create the lock table, lockresult table and obj queue
  locktable.size = 20;
  locktable.bucket =
    (struct RuntimeNode **) RUNMALLOC_I(sizeof(struct RuntimeNode *)*20);
  /* Set allocation blocks*/
  locktable.listhead=NULL;
  locktable.listtail=NULL;
  /*Set data counts*/
  locktable.numelements = 0;
  lockobj = 0;
  lock2require = 0;
  lockresult = 0;
  lockflag = false;
  lockRedirectTbl = allocateRuntimeHash_I(20);
  objRedirectLockTbl = allocateRuntimeHash_I(20);
#endif
}

INLINE void dislocktable() {
#ifdef MULTICORE_GC
  // do nothing
#else
  freeRuntimeHash(lockRedirectTbl);
  freeRuntimeHash(objRedirectLockTbl);
  RUNFREE(locktable.bucket);
#endif
}

INLINE void inittaskdata() {
  if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
    // startup core to initialize corestatus[]
    total_num_t6 = 0; // TODO for test
  }
  totransobjqueue = createQueue_I();
  objqueue.head = NULL;
  objqueue.tail = NULL;
  currtpd = NULL;
  for(int i = 0; i < MAXTASKPARAMS; i++) {
    runtime_locks[i].redirectlock = 0;
    runtime_locks[i].value = 0;
  }
  runtime_locklen = 0;
  initlocktable();
  INIT_TASKPROFILE_DATA();
}

INLINE void distaskdata() {
  if(activetasks != NULL) {
    genfreehashtable(activetasks);
  }
  if(currtpd != NULL) {
    RUNFREE(currtpd->parameterArray);
    RUNFREE(currtpd);
    currtpd = NULL;
  }
  dislocktable();
}

INLINE bool checkObjQueue() {
  bool rflag = false;
  struct transObjInfo * objInfo = NULL;
  int grount = 0;

#ifdef ACCURATEPROFILE
  PROFILE_TASK_START("objqueue checking");
#endif

  while(!isEmpty(&objqueue)) {
    void * obj = NULL;
    BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
    rflag = true;
    objInfo = (struct transObjInfo *)getItem(&objqueue);
    obj = objInfo->objptr;
    // grab lock and flush the obj
    grount = 0;
    struct ___Object___ * tmpobj = (struct ___Object___ *)obj;
    while(tmpobj->lock != NULL) {
      tmpobj = (struct ___Object___ *)(tmpobj->lock);
    }
    getwritelock_I(tmpobj);
    while(!lockflag) {
      BAMBOO_WAITING_FOR_LOCK_I();
    } 
    grount = lockresult;

    lockresult = 0;
    lockobj = 0;
    lock2require = 0;
    lockflag = false;
#ifndef INTERRUPT
    reside = false;
#endif

    if(grount == 1) {
      int k = 0;
      // flush the object
      BAMBOO_CACHE_FLUSH_RANGE((int)obj,sizeof(int));
      BAMBOO_CACHE_FLUSH_RANGE((int)obj, 
          classsize[((struct ___Object___ *)obj)->type]);
      // enqueue the object
      for(k = 0; k < objInfo->length; k++) {
	int taskindex = objInfo->queues[2 * k];
	int paramindex = objInfo->queues[2 * k + 1];
	struct parameterwrapper ** queues =
	  &(paramqueues[BAMBOO_NUM_OF_CORE][taskindex][paramindex]);
	enqueueObject_I(obj, queues, 1);
      } 
      releasewritelock_I(tmpobj);
      RUNFREE_I(objInfo->queues);
      RUNFREE_I(objInfo);
    } else {
      // can not get lock
      // put it at the end of the queue if no update version in the queue
      struct QueueItem * qitem = getHead(&objqueue);
      struct QueueItem * prev = NULL;
      while(qitem != NULL) {
		struct transObjInfo * tmpinfo =
			(struct transObjInfo *)(qitem->objectptr);
		if(tmpinfo->objptr == obj) {
		  // the same object in the queue, which should be enqueued
		  // recently. Current one is outdate, do not re-enqueue it
		  RUNFREE_I(objInfo->queues);
		  RUNFREE_I(objInfo);
		  goto objqueuebreak;
		} else {
		  prev = qitem;
		} 
		qitem = getNextQueueItem(prev);
	  } 
      // try to execute active tasks already enqueued first
      addNewItem_I(&objqueue, objInfo);
objqueuebreak:
      BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
      break;
    } 
    BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
  }

#ifdef ACCURATEPROFILE
  PROFILE_TASK_END();
#endif

  return rflag;
}

struct ___createstartupobject____I_locals {
  INTPTR size;
  void * next;
  struct  ___StartupObject___ * ___startupobject___;
  struct ArrayObject * ___stringarray___;
};

void createstartupobject(int argc,
                         char ** argv) {
  int i;

  /* Allocate startup object     */
#ifdef MULTICORE_GC
  struct ___createstartupobject____I_locals ___locals___ = 
  {2, NULL, NULL, NULL};
  struct ___StartupObject___ *startupobject=
    (struct ___StartupObject___*) allocate_new(&___locals___, STARTUPTYPE);
  ___locals___.___startupobject___ = startupobject;
  struct ArrayObject * stringarray=
    allocate_newarray(&___locals___, STRINGARRAYTYPE, argc-1);
  ___locals___.___stringarray___ = stringarray;
#else
  struct ___StartupObject___ *startupobject=
    (struct ___StartupObject___*) allocate_new(STARTUPTYPE);
  struct ArrayObject * stringarray=
    allocate_newarray(STRINGARRAYTYPE, argc-1);
#endif
  /* Build array of strings */
  startupobject->___parameters___=stringarray;
  for(i=1; i<argc; i++) {
    int length=strlen(argv[i]);
#ifdef MULTICORE_GC
    struct ___String___ *newstring=NewString(&___locals___, argv[i],length);
#else
    struct ___String___ *newstring=NewString(argv[i],length);
#endif
    ((void **)(((char *)&stringarray->___length___)+sizeof(int)))[i-1]=
      newstring;
  }

  startupobject->version = 0;
  startupobject->lock = NULL;

  /* Set initialized flag for startup object */
  flagorandinit(startupobject,1,0xFFFFFFFF);
  enqueueObject(startupobject, NULL, 0);
  BAMBOO_CACHE_FLUSH_ALL();
}

int hashCodetpd(struct taskparamdescriptor *ftd) {
  int hash=(int)ftd->task;
  int i;
  for(i=0; i<ftd->numParameters; i++) {
    hash^=(int)ftd->parameterArray[i];
  }
  return hash;
}

int comparetpd(struct taskparamdescriptor *ftd1,
               struct taskparamdescriptor *ftd2) {
  int i;
  if (ftd1->task!=ftd2->task)
    return 0;
  for(i=0; i<ftd1->numParameters; i++)
    if(ftd1->parameterArray[i]!=ftd2->parameterArray[i])
      return 0;
  return 1;
}

/* This function sets a tag. */
#ifdef MULTICORE_GC
void tagset(void *ptr,
            struct ___Object___ * obj,
            struct ___TagDescriptor___ * tagd) {
#else
void tagset(struct ___Object___ * obj,
            struct ___TagDescriptor___ * tagd) {
#endif
  struct ArrayObject * ao=NULL;
  struct ___Object___ * tagptr=obj->___tags___;
  if (tagptr==NULL) {
    obj->___tags___=(struct ___Object___ *)tagd;
  } else {
    /* Have to check if it is already set */
    if (tagptr->type==TAGTYPE) {
      struct ___TagDescriptor___ * td=(struct ___TagDescriptor___ *) tagptr;
      if (td==tagd) {
		return;
      }
#ifdef MULTICORE_GC
      int ptrarray[]={2, (int) ptr, (int) obj, (int)tagd};
      struct ArrayObject * ao=
        allocate_newarray(&ptrarray,TAGARRAYTYPE,TAGARRAYINTERVAL);
      obj=(struct ___Object___ *)ptrarray[2];
      tagd=(struct ___TagDescriptor___ *)ptrarray[3];
      td=(struct ___TagDescriptor___ *) obj->___tags___;
#else
      ao=allocate_newarray(TAGARRAYTYPE,TAGARRAYINTERVAL);
#endif

      ARRAYSET(ao, struct ___TagDescriptor___ *, 0, td);
      ARRAYSET(ao, struct ___TagDescriptor___ *, 1, tagd);
      obj->___tags___=(struct ___Object___ *) ao;
      ao->___cachedCode___=2;
    } else {
      /* Array Case */
      int i;
      struct ArrayObject *ao=(struct ArrayObject *) tagptr;
      for(i=0; i<ao->___cachedCode___; i++) {
		struct ___TagDescriptor___ * td=
		  ARRAYGET(ao, struct ___TagDescriptor___*, i);
		if (td==tagd) {
		  return;
		}
      }
      if (ao->___cachedCode___<ao->___length___) {
		ARRAYSET(ao, struct ___TagDescriptor___ *,ao->___cachedCode___,tagd);
		ao->___cachedCode___++;
      } else {
#ifdef MULTICORE_GC
		int ptrarray[]={2,(int) ptr, (int) obj, (int) tagd};
		struct ArrayObject * aonew=
		  allocate_newarray(&ptrarray,TAGARRAYTYPE,
							TAGARRAYINTERVAL+ao->___length___);
		obj=(struct ___Object___ *)ptrarray[2];
		tagd=(struct ___TagDescriptor___ *) ptrarray[3];
		ao=(struct ArrayObject *)obj->___tags___;
#else
		struct ArrayObject * aonew=
		  allocate_newarray(TAGARRAYTYPE,TAGARRAYINTERVAL+ao->___length___);
#endif

		aonew->___cachedCode___=ao->___length___+1;
		for(i=0; i<ao->___length___; i++) {
		  ARRAYSET(aonew, struct ___TagDescriptor___*, i,
				   ARRAYGET(ao, struct ___TagDescriptor___*, i));
		}
		ARRAYSET(aonew, struct ___TagDescriptor___ *, ao->___length___,tagd);
      }
    }
  }

  {
    struct ___Object___ * tagset=tagd->flagptr;
    if(tagset==NULL) {
      tagd->flagptr=obj;
    } else if (tagset->type!=OBJECTARRAYTYPE) {
#ifdef MULTICORE_GC
      int ptrarray[]={2, (int) ptr, (int) obj, (int)tagd};
      struct ArrayObject * ao=
        allocate_newarray(&ptrarray,OBJECTARRAYTYPE,OBJECTARRAYINTERVAL);
      obj=(struct ___Object___ *)ptrarray[2];
      tagd=(struct ___TagDescriptor___ *)ptrarray[3];
#else
      struct ArrayObject * ao=
        allocate_newarray(OBJECTARRAYTYPE,OBJECTARRAYINTERVAL);
#endif
      ARRAYSET(ao, struct ___Object___ *, 0, tagd->flagptr);
      ARRAYSET(ao, struct ___Object___ *, 1, obj);
      ao->___cachedCode___=2;
      tagd->flagptr=(struct ___Object___ *)ao;
    } else {
      struct ArrayObject *ao=(struct ArrayObject *) tagset;
      if (ao->___cachedCode___<ao->___length___) {
	ARRAYSET(ao, struct ___Object___*, ao->___cachedCode___++, obj);
      } else {
	int i;
#ifdef MULTICORE_GC
	int ptrarray[]={2, (int) ptr, (int) obj, (int)tagd};
	struct ArrayObject * aonew=
	  allocate_newarray(&ptrarray,OBJECTARRAYTYPE,
			    OBJECTARRAYINTERVAL+ao->___length___);
	obj=(struct ___Object___ *)ptrarray[2];
	tagd=(struct ___TagDescriptor___ *)ptrarray[3];
	ao=(struct ArrayObject *)tagd->flagptr;
#else
	struct ArrayObject * aonew=allocate_newarray(OBJECTARRAYTYPE,
						     OBJECTARRAYINTERVAL+ao->___length___);
#endif
	aonew->___cachedCode___=ao->___cachedCode___+1;
	for(i=0; i<ao->___length___; i++) {
	  ARRAYSET(aonew, struct ___Object___*, i,
		   ARRAYGET(ao, struct ___Object___*, i));
	}
	ARRAYSET(aonew, struct ___Object___ *, ao->___cachedCode___, obj);
	tagd->flagptr=(struct ___Object___ *) aonew;
      }
    }
  }
}
 
/* This function clears a tag. */
#ifdef MULTICORE_GC
void tagclear(void *ptr, struct ___Object___ * obj, struct ___TagDescriptor___ * tagd) {
#else
void tagclear(struct ___Object___ * obj, struct ___TagDescriptor___ * tagd) {
#endif
  /* We'll assume that tag is alway there.
     Need to statically check for this of course. */
  struct ___Object___ * tagptr=obj->___tags___;
  
  if (tagptr->type==TAGTYPE) {
    if ((struct ___TagDescriptor___ *)tagptr==tagd)
      obj->___tags___=NULL;
  } else {
    struct ArrayObject *ao=(struct ArrayObject *) tagptr;
    int i;
    for(i=0; i<ao->___cachedCode___; i++) {
      struct ___TagDescriptor___ * td=
        ARRAYGET(ao, struct ___TagDescriptor___ *, i);
      if (td==tagd) {
	ao->___cachedCode___--;
	if (i<ao->___cachedCode___)
	  ARRAYSET(ao, struct ___TagDescriptor___ *, i,
		   ARRAYGET(ao,struct ___TagDescriptor___*,ao->___cachedCode___));
	ARRAYSET(ao,struct ___TagDescriptor___ *,ao->___cachedCode___, NULL);
	if (ao->___cachedCode___==0)
	  obj->___tags___=NULL;
	goto PROCESSCLEAR;
      }
    }
  }
 PROCESSCLEAR:
  {
    struct ___Object___ *tagset=tagd->flagptr;
    if (tagset->type!=OBJECTARRAYTYPE) {
      if (tagset==obj)
	tagd->flagptr=NULL;
    } else {
      struct ArrayObject *ao=(struct ArrayObject *) tagset;
      int i;
      for(i=0; i<ao->___cachedCode___; i++) {
	struct ___Object___ * tobj=ARRAYGET(ao, struct ___Object___ *, i);
	if (tobj==obj) {
	  ao->___cachedCode___--;
	  if (i<ao->___cachedCode___)
	    ARRAYSET(ao, struct ___Object___ *, i,
		     ARRAYGET(ao, struct ___Object___ *, ao->___cachedCode___));
	  ARRAYSET(ao, struct ___Object___ *, ao->___cachedCode___, NULL);
	  if (ao->___cachedCode___==0)
	    tagd->flagptr=NULL;
	  goto ENDCLEAR;
	}
      }
    }
  }
 ENDCLEAR:
  return;
}

/* This function allocates a new tag. */
#ifdef MULTICORE_GC
struct ___TagDescriptor___ * allocate_tag(void *ptr, int index) {
  struct ___TagDescriptor___ * v=
    (struct ___TagDescriptor___ *) FREEMALLOC((struct garbagelist *) ptr,
                                              classsize[TAGTYPE]);
#else
struct ___TagDescriptor___ * allocate_tag(int index) {
  struct ___TagDescriptor___ * v=FREEMALLOC(classsize[TAGTYPE]);
#endif
  v->type=TAGTYPE;
  v->flag=index;
  return v;
}

/* This function updates the flag for object ptr.  It or's the flag
   with the or mask and and's it with the andmask. */

void flagbody(struct ___Object___ *ptr, int flag, struct parameterwrapper ** queues, int length, bool isnew);

int flagcomp(const int *val1, const int *val2) {
  return (*val1)-(*val2);
}
 
void flagorand(void * ptr, int ormask, int andmask, struct parameterwrapper ** queues, int length) {
  int oldflag=((int *)ptr)[2]; // the flag field is now the third one
  int flag=ormask|oldflag;
  flag&=andmask;
  flagbody(ptr, flag, queues, length, false);
}

bool intflagorand(void * ptr, int ormask, int andmask) {
  int oldflag=((int *)ptr)[2]; // the flag field is the third one
  int flag=ormask|oldflag;
  flag&=andmask;
  if (flag==oldflag)   /* Don't do anything */
    return false;
  else {
    flagbody(ptr, flag, NULL, 0, false);
    return true;
  }
}
 
void flagorandinit(void * ptr, int ormask, int andmask) {
  int oldflag=((int *)ptr)[2]; // the flag field is the third one
  int flag=ormask|oldflag;
  flag&=andmask;
  flagbody(ptr,flag,NULL,0,true);
}
 
void flagbody(struct ___Object___ *ptr,
              int flag,
              struct parameterwrapper ** vqueues,
              int vlength,
              bool isnew) {
  struct parameterwrapper * flagptr = NULL;
  int i = 0;
  struct parameterwrapper ** queues = vqueues;
  int length = vlength;
  int next;
  int UNUSED, UNUSED2;
  int * enterflags = NULL;
  if((!isnew) && (queues == NULL)) {
    if(BAMBOO_NUM_OF_CORE < NUMCORESACTIVE) {
      queues = objectqueues[BAMBOO_NUM_OF_CORE][ptr->type];
      length = numqueues[BAMBOO_NUM_OF_CORE][ptr->type];
    } else {
      return;
    }
  }
  ptr->flag=flag;

  /*Remove object from all queues */
  for(i = 0; i < length; i++) {
    flagptr = queues[i];
    ObjectHashget(flagptr->objectset, (int) ptr, (int *) &next,
                  (int *) &enterflags, &UNUSED, &UNUSED2);
    ObjectHashremove(flagptr->objectset, (int)ptr);
    if (enterflags!=NULL)
      RUNFREE(enterflags);
  }
}

void enqueueObject(void * vptr,
                   struct parameterwrapper ** vqueues,
                   int vlength) {
  struct ___Object___ *ptr = (struct ___Object___ *)vptr;
  
  {
    struct parameterwrapper * parameter=NULL;
    int j;
    int i;
    struct parameterwrapper * prevptr=NULL;
    struct ___Object___ *tagptr=NULL;
    struct parameterwrapper ** queues = vqueues;
    int length = vlength;
    if(BAMBOO_NUM_OF_CORE > NUMCORESACTIVE - 1) {
      return;
    }
    if(queues == NULL) {
      queues = objectqueues[BAMBOO_NUM_OF_CORE][ptr->type];
      length = numqueues[BAMBOO_NUM_OF_CORE][ptr->type];
    }
    tagptr=ptr->___tags___;

    /* Outer loop iterates through all parameter queues an object of
       this type could be in.  */
    for(j = 0; j < length; j++) {
      parameter = queues[j];
      /* Check tags */
      if (parameter->numbertags>0) {
	if (tagptr==NULL)
	  goto nextloop;  //that means the object has no tag
	//but that param needs tag
	else if(tagptr->type==TAGTYPE) {     //one tag
	  for(i=0; i<parameter->numbertags; i++) {
	    //slotid is parameter->tagarray[2*i];
	    int tagid=parameter->tagarray[2*i+1];
	    if (tagid!=tagptr->flag)
	      goto nextloop;   /*We don't have this tag */
	  }
	} else {   //multiple tags
	  struct ArrayObject * ao=(struct ArrayObject *) tagptr;
	  for(i=0; i<parameter->numbertags; i++) {
	    //slotid is parameter->tagarray[2*i];
	    int tagid=parameter->tagarray[2*i+1];
	    int j;
	    for(j=0; j<ao->___cachedCode___; j++) {
	      if (tagid==ARRAYGET(ao, struct ___TagDescriptor___*, j)->flag)
		goto foundtag;
	    }
	    goto nextloop;
	  foundtag:
	    ;
	  }
	}
      }
      
      /* Check flags */
      for(i=0; i<parameter->numberofterms; i++) {
	int andmask=parameter->intarray[i*2];
	int checkmask=parameter->intarray[i*2+1];
	if ((ptr->flag&andmask)==checkmask) {
	  enqueuetasks(parameter, prevptr, ptr, NULL, 0);
	  prevptr=parameter;
	  break;
	}
      }
    nextloop:
      ;
    }
  }
}

void enqueueObject_I(void * vptr,
                     struct parameterwrapper ** vqueues,
                     int vlength) {
  struct ___Object___ *ptr = (struct ___Object___ *)vptr;

  {
    struct parameterwrapper * parameter=NULL;
    int j;
    int i;
    struct parameterwrapper * prevptr=NULL;
    struct ___Object___ *tagptr=NULL;
    struct parameterwrapper ** queues = vqueues;
    int length = vlength;
    if(BAMBOO_NUM_OF_CORE > NUMCORESACTIVE - 1) {
      return;
    }
    if(queues == NULL) {
      queues = objectqueues[BAMBOO_NUM_OF_CORE][ptr->type];
      length = numqueues[BAMBOO_NUM_OF_CORE][ptr->type];
    }
    tagptr=ptr->___tags___;

    /* Outer loop iterates through all parameter queues an object of
       this type could be in.  */
    for(j = 0; j < length; j++) {
      parameter = queues[j];
      /* Check tags */
      if (parameter->numbertags>0) {
	if (tagptr==NULL)
	  goto nextloop;      //that means the object has no tag
	//but that param needs tag
	else if(tagptr->type==TAGTYPE) {   //one tag
	  for(i=0; i<parameter->numbertags; i++) {
	    //slotid is parameter->tagarray[2*i];
	    int tagid=parameter->tagarray[2*i+1];
	    if (tagid!=tagptr->flag)
	      goto nextloop;            /*We don't have this tag */
	  }
	} else {    //multiple tags
	  struct ArrayObject * ao=(struct ArrayObject *) tagptr;
	  for(i=0; i<parameter->numbertags; i++) {
	    //slotid is parameter->tagarray[2*i];
	    int tagid=parameter->tagarray[2*i+1];
	    int j;
	    for(j=0; j<ao->___cachedCode___; j++) {
	      if (tagid==ARRAYGET(ao, struct ___TagDescriptor___*, j)->flag)
		goto foundtag;
	    }
	    goto nextloop;
	  foundtag:
	    ;
	  }
	}
      }
      
      /* Check flags */
      for(i=0; i<parameter->numberofterms; i++) {
	int andmask=parameter->intarray[i*2];
	int checkmask=parameter->intarray[i*2+1];
	if ((ptr->flag&andmask)==checkmask) {
	  enqueuetasks_I(parameter, prevptr, ptr, NULL, 0);
	  prevptr=parameter;
	  break;
	}
      }
    nextloop:
      ;
    }
  }
}


int * getAliasLock(void ** ptrs, int length, struct RuntimeHash * tbl) {
#ifdef TILERA_BME
  int i = 0;
  int locks[length];
  int locklen = 0;
  // sort all the locks required by the objs in the aliased set
  for(; i < length; i++) {
    struct ___Object___ * ptr = (struct ___Object___ *)(ptrs[i]);
    int lock = 0;
    int j = 0;
    if(ptr->lock == NULL) {
      lock = (int)(ptr);
    } else {
      lock = (int)(ptr->lock);
    }
    bool insert = true;
    for(j = 0; j < locklen; j++) {
      if(locks[j] == lock) {
	insert = false;
	break;
      } else if(locks[j] > lock) {
	break;
      }
    }
    if(insert) {
      int h = locklen;
      for(; h > j; h--) {
	locks[h] = locks[h-1];
      }
      locks[j] = lock;
      locklen++;
    }
  }
  // use the smallest lock as the shared lock for the whole set
  return (int *)(locks[0]);
#else // TILERA_BME
  // TODO possible bug here!!!
  if(length == 0) {
    return (int*)(RUNMALLOC(sizeof(int)));
  } else {
    int i = 0;
    int locks[length];
    int locklen = 0;
    bool redirect = false;
    int redirectlock = 0;
    for(; i < length; i++) {
      struct ___Object___ * ptr = (struct ___Object___ *)(ptrs[i]);
      int lock = 0;
      int j = 0;
      if(ptr->lock == NULL) {
	lock = (int)(ptr);
      } else {
	lock = (int)(ptr->lock);
      }
      if(redirect) {
	if(lock != redirectlock) {
	  RuntimeHashadd(tbl, lock, redirectlock);
	}
      } else {
	if(RuntimeHashcontainskey(tbl, lock)) {
	  // already redirected
	  redirect = true;
	  RuntimeHashget(tbl, lock, &redirectlock);
	  for(; j < locklen; j++) {
	    if(locks[j] != redirectlock) {
	      RuntimeHashadd(tbl, locks[j], redirectlock);
	    }
	  }
	} else {
	  bool insert = true;
	  for(j = 0; j < locklen; j++) {
	    if(locks[j] == lock) {
	      insert = false;
	      break;
	    } else if(locks[j] > lock) {
	      break;
	    }
	  }
	  if(insert) {
	    int h = locklen;
	    for(; h > j; h--) {
	      locks[h] = locks[h-1];
	    }
	    locks[j] = lock;
	    locklen++;
	  }
	}
      }
    }
    if(redirect) {
      return (int *)redirectlock;
    } else {
      // use the first lock as the shared lock
      for(j = 1; j < locklen; j++) {
	if(locks[j] != locks[0]) {
	  RuntimeHashadd(tbl, locks[j], locks[0]);
	}
      }
      return (int *)(locks[0]);
    }
  }
#endif // TILERA_BME
}

void addAliasLock(void * ptr, int lock) {
  struct ___Object___ * obj = (struct ___Object___ *)ptr;
  if(((int)ptr != lock) && (obj->lock != (int*)lock)) {
    // originally no alias lock associated or have a different alias lock
    // flush it as the new one
#ifdef TILERA_BME
    while(obj->lock != NULL) {
      // previously have alias lock, trace the 'root' obj and redirect it
      obj = (struct ___Object___ *)(obj->lock);
    } 
#endif // TILERA_BME
    obj->lock = (int *)lock;
  }
}

int enqueuetasks(struct parameterwrapper *parameter,
                 struct parameterwrapper *prevptr,
                 struct ___Object___ *ptr,
                 int * enterflags,
                 int numenterflags) {
  void * taskpointerarray[MAXTASKPARAMS];
  int j;
  int numiterators=parameter->task->numTotal-1;
  int retval=1;

  struct taskdescriptor * task=parameter->task;

  //this add the object to parameterwrapper
  ObjectHashadd(parameter->objectset, (int) ptr, 0, (int) enterflags,
      numenterflags, enterflags==NULL);

  /* Add enqueued object to parameter vector */
  taskpointerarray[parameter->slot]=ptr;

  /* Reset iterators */
  for(j=0; j<numiterators; j++) {
    toiReset(&parameter->iterators[j]);
  }

  /* Find initial state */
  for(j=0; j<numiterators; j++) {
backtrackinit:
    if(toiHasNext(&parameter->iterators[j],taskpointerarray OPTARG(failed)))
      toiNext(&parameter->iterators[j], taskpointerarray OPTARG(failed));
    else if (j>0) {
      /* Need to backtrack */
      toiReset(&parameter->iterators[j]);
      j--;
      goto backtrackinit;
    } else {
      /* Nothing to enqueue */
      return retval;
    }
  }

  while(1) {
    /* Enqueue current state */
    //int launch = 0;
    struct taskparamdescriptor *tpd=
      RUNMALLOC(sizeof(struct taskparamdescriptor));
    tpd->task=task;
    tpd->numParameters=numiterators+1;
    tpd->parameterArray=RUNMALLOC(sizeof(void *)*(numiterators+1));

    for(j=0; j<=numiterators; j++) {
      //store the actual parameters
      tpd->parameterArray[j]=taskpointerarray[j];
    }
    /* Enqueue task */
    if (!gencontains(activetasks,tpd)) {
      genputtable(activetasks, tpd, tpd);
    } else {
      RUNFREE(tpd->parameterArray);
      RUNFREE(tpd);
    }

    /* This loop iterates to the next parameter combination */
    if (numiterators==0)
      return retval;

    for(j=numiterators-1; j<numiterators; j++) {
backtrackinc:
      if(toiHasNext(&parameter->iterators[j],taskpointerarray OPTARG(failed)))
        toiNext(&parameter->iterators[j], taskpointerarray OPTARG(failed));
      else if (j>0) {
        /* Need to backtrack */
        toiReset(&parameter->iterators[j]);
        j--;
        goto backtrackinc;
      } else {
        /* Nothing more to enqueue */
        return retval;
      }
    }
  }
  return retval;
}

int enqueuetasks_I(struct parameterwrapper *parameter,
                   struct parameterwrapper *prevptr,
                   struct ___Object___ *ptr,
                   int * enterflags,
                   int numenterflags) {
  void * taskpointerarray[MAXTASKPARAMS];
  int j;
  int numiterators=parameter->task->numTotal-1;
  int retval=1;

  struct taskdescriptor * task=parameter->task;

  //this add the object to parameterwrapper
  ObjectHashadd_I(parameter->objectset, (int) ptr, 0, (int) enterflags,
                  numenterflags, enterflags==NULL);

  /* Add enqueued object to parameter vector */
  taskpointerarray[parameter->slot]=ptr;

  /* Reset iterators */
  for(j=0; j<numiterators; j++) {
    toiReset(&parameter->iterators[j]);
  }

  /* Find initial state */
  for(j=0; j<numiterators; j++) {
backtrackinit:
    if(toiHasNext(&parameter->iterators[j],taskpointerarray OPTARG(failed)))
      toiNext(&parameter->iterators[j], taskpointerarray OPTARG(failed));
    else if (j>0) {
      /* Need to backtrack */
      toiReset(&parameter->iterators[j]);
      j--;
      goto backtrackinit;
    } else {
      /* Nothing to enqueue */
      return retval;
    }
  }

  while(1) {
    /* Enqueue current state */
    //int launch = 0;
    struct taskparamdescriptor *tpd=
      RUNMALLOC_I(sizeof(struct taskparamdescriptor));
    tpd->task=task;
    tpd->numParameters=numiterators+1;
    tpd->parameterArray=RUNMALLOC_I(sizeof(void *)*(numiterators+1));

    for(j=0; j<=numiterators; j++) {
      //store the actual parameters
      tpd->parameterArray[j]=taskpointerarray[j];
    }
    /* Enqueue task */
    if (!gencontains(activetasks,tpd)) {
      genputtable_I(activetasks, tpd, tpd);
    } else {
      RUNFREE_I(tpd->parameterArray);
      RUNFREE_I(tpd);
    }

    /* This loop iterates to the next parameter combination */
    if (numiterators==0)
      return retval;

    for(j=numiterators-1; j<numiterators; j++) {
backtrackinc:
      if(toiHasNext(&parameter->iterators[j], taskpointerarray OPTARG(failed)))
        toiNext(&parameter->iterators[j], taskpointerarray OPTARG(failed));
      else if (j>0) {
        /* Need to backtrack */
        toiReset(&parameter->iterators[j]);
        j--;
        goto backtrackinc;
      } else {
        /* Nothing more to enqueue */
        return retval;
      }
    }
  }
  return retval;
}

#ifdef MULTICORE_GC
#define OFFSET 2
#else
#define OFFSET 0
#endif

int containstag(struct ___Object___ *ptr,
                struct ___TagDescriptor___ *tag);

#ifndef MULTICORE_GC
void releasewritelock_r(void * lock, void * redirectlock) {
  int targetcore = 0;
  int reallock = (int)lock;
  targetcore = (reallock >> 5) % NUMCORES;

  if(targetcore == BAMBOO_NUM_OF_CORE) {
    BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
    // reside on this core
    if(!RuntimeHashcontainskey(locktbl, reallock)) {
      // no locks for this object, something is wrong
      BAMBOO_EXIT();
    } else {
      int rwlock_obj = 0;
      struct LockValue * lockvalue = NULL;
      RuntimeHashget(locktbl, reallock, &rwlock_obj);
      lockvalue = (struct LockValue *)rwlock_obj;
      lockvalue->value++;
      lockvalue->redirectlock = (int)redirectlock;
    }
    BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
    return;
  } else {
    // send lock release with redirect info msg
    // for 32 bit machine, the size is always 4 words
    send_msg_4(targetcore,REDIRECTRELEASE,1,(int)lock,(int)redirectlock);
  }
}
#endif

void executetasks() {
  void * taskpointerarray[MAXTASKPARAMS+OFFSET];
  int numparams=0;
  int numtotal=0;
  struct ___Object___ * tmpparam = NULL;
  struct parameterdescriptor * pd=NULL;
  struct parameterwrapper *pw=NULL;
  int j = 0;
  int x = 0;
  bool islock = true;

  int grount = 0;
  int andmask=0;
  int checkmask=0;

newtask:
  while(hashsize(activetasks)>0) {
    GCCHECK(NULL);

    /* See if there are any active tasks */
    int i;
#ifdef ACCURATEPROFILE
    PROFILE_TASK_START("tpd checking");
#endif

    busystatus = true;
    currtpd=(struct taskparamdescriptor *) getfirstkey(activetasks);
    genfreekey(activetasks, currtpd);

    numparams=currtpd->task->numParameters;
    numtotal=currtpd->task->numTotal;

    // (TODO, this table should be empty after all locks are released)
    // reset all locks
    // get all required locks
    runtime_locklen = 0;
    // check which locks are needed
    for(i = 0; i < numparams; i++) {
      void * param = currtpd->parameterArray[i];
      int tmplock = 0;
      int j = 0;
      bool insert = true;
      if(((struct ___Object___ *)param)->type == STARTUPTYPE) {
        islock = false;
        taskpointerarray[i+OFFSET]=param;
        goto execute;
      }
      struct ___Object___ * obj = (struct ___Object___ *)param;
      while(obj->lock != NULL) {
        obj = (struct ___Object___ *)(obj->lock);
      }
      tmplock = (int)(obj);
      // insert into the locks array
      for(j = 0; j < runtime_locklen; j++) {
        if(runtime_locks[j].value == tmplock) {
          insert = false;
          break;
        } else if(runtime_locks[j].value > tmplock) {
          break;
        }
      }
      if(insert) {
        int h = runtime_locklen;
        for(; h > j; h--) {
          runtime_locks[h].redirectlock = runtime_locks[h-1].redirectlock;
          runtime_locks[h].value = runtime_locks[h-1].value;
        }
        runtime_locks[j].value = tmplock;
        runtime_locks[j].redirectlock = (int)param;
        runtime_locklen++;
      }
    }  // for(i = 0; i < numparams; i++)
    // grab these required locks

    for(i = 0; i < runtime_locklen; i++) {
      int * lock = (int *)(runtime_locks[i].value);
      islock = true;
      // require locks for this parameter if it is not a startup object
      getwritelock(lock);
      BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
      while(!lockflag) {
        BAMBOO_WAITING_FOR_LOCK_I();
      }
#ifndef INTERRUPT
      if(reside) {
        while(BAMBOO_WAITING_FOR_LOCK_I() != -1) {
        }
      }
#endif
      grount = lockresult;

      lockresult = 0;
      lockobj = 0;
      lock2require = 0;
      lockflag = false;
#ifndef INTERRUPT
      reside = false;
#endif
      BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();

      if(grount == 0) {
        // check if has the lock already
        // can not get the lock, try later
        // release all grabbed locks for previous parameters		
        for(j = 0; j < i; j++) {
          lock = (int*)(runtime_locks[j].value/*redirectlock*/);
          releasewritelock(lock);
        }
        genputtable(activetasks, currtpd, currtpd);
        if(hashsize(activetasks) == 1) {
          // only one task right now, wait a little while before next try
          int halt = 10000;
          while(halt--) {
          }
        }
#ifdef ACCURATEPROFILE
        // fail, set the end of the checkTaskInfo
        PROFILE_TASK_END();
#endif
        goto newtask;
      }
    }   // line 2752:  for(i = 0; i < runtime_locklen; i++)

    /* Make sure that the parameters are still in the queues */
    for(i=0; i<numparams; i++) {
      void * parameter=currtpd->parameterArray[i];

      // flush the object
      BAMBOO_CACHE_FLUSH_RANGE((int)parameter,
          classsize[((struct ___Object___ *)parameter)->type]);
      tmpparam = (struct ___Object___ *)parameter;
      pd=currtpd->task->descriptorarray[i];
      pw=(struct parameterwrapper *) pd->queue;
      /* Check that object is still in queue */
      {
        if (!ObjectHashcontainskey(pw->objectset, (int) parameter)) {
          // release grabbed locks
          for(j = 0; j < runtime_locklen; j++) {
            int * lock = (int *)(runtime_locks[j].value);
            releasewritelock(lock);
          }
          RUNFREE(currtpd->parameterArray);
          RUNFREE(currtpd);
          currtpd = NULL;
          goto newtask;
        }
      } 
      /* Check if the object's flags still meets requirements */
      {
        int tmpi = 0;
        bool ismet = false;
        for(tmpi = 0; tmpi < pw->numberofterms; tmpi++) {
          andmask=pw->intarray[tmpi*2];
          checkmask=pw->intarray[tmpi*2+1];
          if((((struct ___Object___ *)parameter)->flag&andmask)==checkmask) {
            ismet = true;
            break;
          }
        }
        if (!ismet) {
          // flags are never suitable
          // remove this obj from the queue
          int next;
          int UNUSED, UNUSED2;
          int * enterflags;
          ObjectHashget(pw->objectset, (int) parameter, (int *) &next,
              (int *) &enterflags, &UNUSED, &UNUSED2);
          ObjectHashremove(pw->objectset, (int)parameter);
          if (enterflags!=NULL)
            RUNFREE(enterflags);
          // release grabbed locks
          for(j = 0; j < runtime_locklen; j++) {
            int * lock = (int *)(runtime_locks[j].value/*redirectlock*/);
            releasewritelock(lock);
          }
          RUNFREE(currtpd->parameterArray);
          RUNFREE(currtpd);
          currtpd = NULL;
#ifdef ACCURATEPROFILE
          // fail, set the end of the checkTaskInfo
          PROFILE_TASK_END();
#endif
          goto newtask;
        }   //if (!ismet)
      } 
parameterpresent:
      ;
      /* Check that object still has necessary tags */
      for(j=0; j<pd->numbertags; j++) {
        int slotid=pd->tagarray[2*j]+numparams;
        struct ___TagDescriptor___ *tagd=currtpd->parameterArray[slotid];
        if (!containstag(parameter, tagd)) {
	  // release grabbed locks
	  int tmpj = 0;
	  for(tmpj = 0; tmpj < runtime_locklen; tmpj++) {
	    int * lock = (int *)(runtime_locks[tmpj].value/*redirectlock*/);
	    releasewritelock(lock);
	  }
          RUNFREE(currtpd->parameterArray);
          RUNFREE(currtpd);
          currtpd = NULL;
          goto newtask;		
        }   
      }   

      taskpointerarray[i+OFFSET]=parameter;
    }   // for(i=0; i<numparams; i++)
    /* Copy the tags */
    for(; i<numtotal; i++) {
      taskpointerarray[i+OFFSET]=currtpd->parameterArray[i];
    }

    {
execute:
      /* Actually call task */
#ifdef MULTICORE_GC
      ((int *)taskpointerarray)[0]=currtpd->numParameters;
      taskpointerarray[1]=NULL;
#endif
#ifdef ACCURATEPROFILE
      // check finish, set the end of the checkTaskInfo
      PROFILE_TASK_END();
#endif
      PROFILE_TASK_START(currtpd->task->name);

      ((void (*)(void **))currtpd->task->taskptr)(taskpointerarray);

#ifdef ACCURATEPROFILE
      // task finish, set the end of the checkTaskInfo
      PROFILE_TASK_END();
      // new a PostTaskInfo for the post-task execution
      PROFILE_TASK_START("post task execution");
#endif

      if(islock) {
        for(i = runtime_locklen; i>0; i--) {
          void * ptr = (void *)(runtime_locks[i-1].redirectlock);
          int * lock = (int *)(runtime_locks[i-1].value);
#ifndef MULTICORE_GC
#ifndef TILERA_BME
          if(RuntimeHashcontainskey(lockRedirectTbl, (int)lock)) {
            int redirectlock;
            RuntimeHashget(lockRedirectTbl, (int)lock, &redirectlock);
            RuntimeHashremovekey(lockRedirectTbl, (int)lock);
            releasewritelock_r(lock, (int *)redirectlock);
          } else {
#else
          {
#endif
#else
          {
#endif
            releasewritelock(lock); // ptr
          }
        }
      }     // if(islock)

      // post task execution finish, set the end of the postTaskInfo
      PROFILE_TASK_END();

      // Free up task parameter descriptor
      RUNFREE(currtpd->parameterArray);
      RUNFREE(currtpd);
      currtpd = NULL;
    }   
  } //  while(hashsize(activetasks)>0)
}

/* This function processes an objects tags */
void processtags(struct parameterdescriptor *pd,
                 int index,
                 struct parameterwrapper *parameter,
                 int * iteratorcount,
                 int *statusarray,
                 int numparams) {
  int i;

  for(i=0; i<pd->numbertags; i++) {
    int slotid=pd->tagarray[2*i];
    int tagid=pd->tagarray[2*i+1];

    if (statusarray[slotid+numparams]==0) {
      parameter->iterators[*iteratorcount].istag=1;
      parameter->iterators[*iteratorcount].tagid=tagid;
      parameter->iterators[*iteratorcount].slot=slotid+numparams;
      parameter->iterators[*iteratorcount].tagobjectslot=index;
      statusarray[slotid+numparams]=1;
      (*iteratorcount)++;
    }
  }
}

void processobject(struct parameterwrapper *parameter,
                   int index,
                   struct parameterdescriptor *pd,
                   int *iteratorcount,
                   int * statusarray,
                   int numparams) {
  int i;
  int tagcount=0;
  struct ObjectHash * objectset=
    ((struct parameterwrapper *)pd->queue)->objectset;

  parameter->iterators[*iteratorcount].istag=0;
  parameter->iterators[*iteratorcount].slot=index;
  parameter->iterators[*iteratorcount].objectset=objectset;
  statusarray[index]=1;

  for(i=0; i<pd->numbertags; i++) {
    int slotid=pd->tagarray[2*i];
    if (statusarray[slotid+numparams]!=0) {
      /* This tag has already been enqueued, use it to narrow search */
      parameter->iterators[*iteratorcount].tagbindings[tagcount]=
        slotid+numparams;
      tagcount++;
    }
  }
  parameter->iterators[*iteratorcount].numtags=tagcount;

  (*iteratorcount)++;
}

/* This function builds the iterators for a task & parameter */

void builditerators(struct taskdescriptor * task,
                    int index,
                    struct parameterwrapper * parameter) {
  int statusarray[MAXTASKPARAMS];
  int i;
  int numparams=task->numParameters;
  int iteratorcount=0;
  for(i=0; i<MAXTASKPARAMS; i++) statusarray[i]=0;

  statusarray[index]=1; /* Initial parameter */
  /* Process tags for initial iterator */

  processtags(task->descriptorarray[index], index, parameter,
              &iteratorcount, statusarray, numparams);

  while(1) {
loopstart:
    /* Check for objects with existing tags */
    for(i=0; i<numparams; i++) {
      if (statusarray[i]==0) {
	struct parameterdescriptor *pd=task->descriptorarray[i];
	int j;
	for(j=0; j<pd->numbertags; j++) {
	  int slotid=pd->tagarray[2*j];
	  if(statusarray[slotid+numparams]!=0) {
	    processobject(parameter,i,pd,&iteratorcount,
			  statusarray,numparams);
	    processtags(pd,i,parameter,&iteratorcount,statusarray,numparams);
	    goto loopstart;
	  }
	}
      }
    }
    
    /* Next do objects w/ unbound tags*/
    
    for(i=0; i<numparams; i++) {
      if (statusarray[i]==0) {
	struct parameterdescriptor *pd=task->descriptorarray[i];
	if (pd->numbertags>0) {
	  processobject(parameter,i,pd,&iteratorcount,statusarray,numparams);
	  processtags(pd,i,parameter,&iteratorcount,statusarray,numparams);
	  goto loopstart;
	}
      }
    }
    
    /* Nothing with a tag enqueued */
    
    for(i=0; i<numparams; i++) {
      if (statusarray[i]==0) {
	struct parameterdescriptor *pd=task->descriptorarray[i];
	processobject(parameter,i,pd,&iteratorcount,statusarray,numparams);
	processtags(pd,i,parameter,&iteratorcount,statusarray,numparams);
	goto loopstart;
      }
    }
    
    /* Nothing left */
    return;
  }
}

void printdebug() {
  int i;
  int j;
  if(BAMBOO_NUM_OF_CORE > NUMCORESACTIVE - 1) {
    return;
  }
  for(i=0; i<numtasks[BAMBOO_NUM_OF_CORE]; i++) {
    struct taskdescriptor * task=taskarray[BAMBOO_NUM_OF_CORE][i];
#ifndef RAW
    printf("%s\n", task->name);
#endif
    for(j=0; j<task->numParameters; j++) {
      struct parameterdescriptor *param=task->descriptorarray[j];
      struct parameterwrapper *parameter=param->queue;
      struct ObjectHash * set=parameter->objectset;
      struct ObjectIterator objit;
#ifndef RAW
      printf("  Parameter %d\n", j);
#endif
      ObjectHashiterator(set, &objit);
      while(ObjhasNext(&objit)) {
	struct ___Object___ * obj=(struct ___Object___ *)Objkey(&objit);
	struct ___Object___ * tagptr=obj->___tags___;
	int nonfailed=Objdata4(&objit);
	int numflags=Objdata3(&objit);
	int flags=Objdata2(&objit);
	Objnext(&objit);
#ifndef RAW
	printf("    Contains %lx\n", obj);
	printf("      flag=%d\n", obj->flag);
#endif
	if (tagptr==NULL) {
	} else if (tagptr->type==TAGTYPE) {
#ifndef RAW
	  printf("      tag=%lx\n",tagptr);
#endif
	} else {
	  int tagindex=0;
	  struct ArrayObject *ao=(struct ArrayObject *)tagptr;
	  for(; tagindex<ao->___cachedCode___; tagindex++) {
#ifndef RAW
	    printf("      tag=%lx\n",ARRAYGET(ao,struct ___TagDescriptor___*, tagindex));
#else
	    ;
#endif
	  }
	}
      }
    }
  }
}

/* This function processes the task information to create queues for
   each parameter type. */
void processtasks() {
  int i;
  if(BAMBOO_NUM_OF_CORE > NUMCORESACTIVE - 1) {
    return;
  }
  for(i=0; i<numtasks[BAMBOO_NUM_OF_CORE]; i++) {
    struct taskdescriptor * task=taskarray[BAMBOO_NUM_OF_CORE][i];
    int j;

    /* Build objectsets */
    for(j=0; j<task->numParameters; j++) {
      struct parameterdescriptor *param=task->descriptorarray[j];
      struct parameterwrapper *parameter=param->queue;
      parameter->objectset=allocateObjectHash(10);
      parameter->task=task;
    }

    /* Build iterators for parameters */
    for(j=0; j<task->numParameters; j++) {
      struct parameterdescriptor *param=task->descriptorarray[j];
      struct parameterwrapper *parameter=param->queue;
      builditerators(task, j, parameter);
    }
  }
}

void toiReset(struct tagobjectiterator * it) {
  if (it->istag) {
    it->tagobjindex=0;
  } else if (it->numtags>0) {
    it->tagobjindex=0;
  } else {
    ObjectHashiterator(it->objectset, &it->it);
  }
}

int toiHasNext(struct tagobjectiterator *it,
               void ** objectarray OPTARG(int * failed)) {
  if (it->istag) {
    /* Iterate tag */
    /* Get object with tags */
    struct ___Object___ *obj=objectarray[it->tagobjectslot];
    struct ___Object___ *tagptr=obj->___tags___;
    if (tagptr->type==TAGTYPE) {
      if ((it->tagobjindex==0)&& /* First object */
		  (it->tagid==((struct ___TagDescriptor___ *)tagptr)->flag)) /* Right tag type */
		return 1;
	  else
		return 0;
    } else {
      struct ArrayObject *ao=(struct ArrayObject *) tagptr;
      int tagindex=it->tagobjindex;
      for(; tagindex<ao->___cachedCode___; tagindex++) {
		struct ___TagDescriptor___ *td=
		  ARRAYGET(ao, struct ___TagDescriptor___ *, tagindex);
		if (td->flag==it->tagid) {
		  it->tagobjindex=tagindex; /* Found right type of tag */
		  return 1;
		}
      }
      return 0;
    }
  } else if (it->numtags>0) {
    /* Use tags to locate appropriate objects */
    struct ___TagDescriptor___ *tag=objectarray[it->tagbindings[0]];
    struct ___Object___ *objptr=tag->flagptr;
    int i;
    if (objptr->type!=OBJECTARRAYTYPE) {
      if (it->tagobjindex>0)
	return 0;
      if (!ObjectHashcontainskey(it->objectset, (int) objptr))
	return 0;
      for(i=1; i<it->numtags; i++) {
	struct ___TagDescriptor___ *tag2=objectarray[it->tagbindings[i]];
	if (!containstag(objptr,tag2))
	  return 0;
      }
      return 1;
    } else {
      struct ArrayObject *ao=(struct ArrayObject *) objptr;
      int tagindex;
      int i;
      for(tagindex=it->tagobjindex;tagindex<ao->___cachedCode___;tagindex++){
	struct ___Object___ *objptr=
	  ARRAYGET(ao,struct ___Object___*,tagindex);
	if (!ObjectHashcontainskey(it->objectset, (int) objptr))
	  continue;
	for(i=1; i<it->numtags; i++) {
	  struct ___TagDescriptor___ *tag2=objectarray[it->tagbindings[i]];
	  if (!containstag(objptr,tag2))
	    goto nexttag;
	}
	it->tagobjindex=tagindex;
	return 1;
      nexttag:
	;
      }
      it->tagobjindex=tagindex;
      return 0;
    }
  } else {
    return ObjhasNext(&it->it);
  }
}

int containstag(struct ___Object___ *ptr,
                struct ___TagDescriptor___ *tag) {
  int j;
  struct ___Object___ * objptr=tag->flagptr;
  if (objptr->type==OBJECTARRAYTYPE) {
    struct ArrayObject *ao=(struct ArrayObject *)objptr;
    for(j=0; j<ao->___cachedCode___; j++) {
      if (ptr==ARRAYGET(ao, struct ___Object___*, j)) {
	return 1;
      }
    }
    return 0;
  } else {
    return objptr==ptr;
  }
}

void toiNext(struct tagobjectiterator *it,
             void ** objectarray OPTARG(int * failed)) {
  /* hasNext has all of the intelligence */
  if(it->istag) {
    /* Iterate tag */
    /* Get object with tags */
    struct ___Object___ *obj=objectarray[it->tagobjectslot];
    struct ___Object___ *tagptr=obj->___tags___;
    if (tagptr->type==TAGTYPE) {
      it->tagobjindex++;
      objectarray[it->slot]=tagptr;
    } else {
      struct ArrayObject *ao=(struct ArrayObject *) tagptr;
      objectarray[it->slot]=
        ARRAYGET(ao, struct ___TagDescriptor___ *, it->tagobjindex++);
    }
  } else if (it->numtags>0) {
    /* Use tags to locate appropriate objects */
    struct ___TagDescriptor___ *tag=objectarray[it->tagbindings[0]];
    struct ___Object___ *objptr=tag->flagptr;
    if (objptr->type!=OBJECTARRAYTYPE) {
      it->tagobjindex++;
      objectarray[it->slot]=objptr;
    } else {
      struct ArrayObject *ao=(struct ArrayObject *) objptr;
      objectarray[it->slot]=
        ARRAYGET(ao, struct ___Object___ *, it->tagobjindex++);
    }
  } else {
    /* Iterate object */
    objectarray[it->slot]=(void *)Objkey(&it->it);
    Objnext(&it->it);
  }
}
#endif
