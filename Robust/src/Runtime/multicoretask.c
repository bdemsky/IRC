#ifdef TASK
#include "runtime.h"
#include "multicoreruntime.h"
#include "runtime_arch.h"
#include "GenericHashtable.h"
/*
   extern int injectfailures;
   extern float failurechance;
 */
extern int debugtask;
extern int instaccum;

void * curr_heapbase=0;
void * curr_heaptop=0;

#if 0
#ifdef CONSCHECK
#include "instrument.h"
#endif
#endif  // if 0: for recovery

//  data structures for task invocation
struct genhashtable * activetasks;
struct genhashtable * failedtasks;
struct taskparamdescriptor * currtpd;
#if 0
struct RuntimeHash * forward;
struct RuntimeHash * reverse;
#endif // if 0: for recovery

#ifdef PROFILE
void outputProfileData();
#endif

bool getreadlock(void* ptr);
void releasereadlock(void* ptr);
bool getwritelock(void* ptr);
void releasewritelock(void* ptr);
void releasewritelock_r(void * lock, void * redirectlock);

// specific functions used inside critical sections
void enqueueObject_I(void * ptr, struct parameterwrapper ** queues, int length);
int enqueuetasks_I(struct parameterwrapper *parameter, struct parameterwrapper *prevptr, struct ___Object___ *ptr, int * enterflags, int numenterflags);
bool getreadlock_I_r(void * ptr, void * redirectlock, int core, bool cache);
bool getwritelock_I(void* ptr);
bool getwritelock_I_r(void* lock, void* redirectlock, int core, bool cache);
void releasewritelock_I(void * ptr);

// main function for each core
inline void run(void * arg) {
  int i = 0;
  int argc = 1;
  char ** argv = NULL;
  bool sendStall = false;
  bool isfirst = true;
  bool tocontinue = false;
  struct QueueItem * objitem = NULL;
  struct transObjInfo * objInfo = NULL;
  int grount = 0;
  bool allStall = true;
  int sumsendobj = 0;

  corenum = BAMBOO_GET_NUM_OF_CORE();
#ifdef DEBUG
  BAMBOO_DEBUGPRINT(0xeeee);
  BAMBOO_DEBUGPRINT_REG(corenum);
  BAMBOO_DEBUGPRINT(STARTUPCORE);
#endif

  // initialize the arrays
  if(STARTUPCORE == corenum) {
    // startup core to initialize corestatus[]
    for(i = 0; i < NUMCORES; ++i) {
      corestatus[i] = 1;
      numsendobjs[i] = 0;                   // assume all variables are local variables! MAY BE WRONG!!!
      numreceiveobjs[i] = 0;
    }
	numconfirm = 0;
	waitconfirm = false; 
#ifdef PROFILE
    // initialize the profile data arrays
    for(i = 0; i < NUMCORES; ++i) {
      profilestatus[i] = 1;
    }  
#endif
  }
  busystatus = true;
  self_numsendobjs = 0;
  self_numreceiveobjs = 0;

  for(i = 0; i < 30; ++i) {
    msgdata[i] = -1;
  }
  msgtype = -1;
  msgdataindex = 0;
  msglength = 30;
  for(i = 0; i < 30; ++i) {
    outmsgdata[i] = -1;
  }
  outmsgindex = 0;
  outmsglast = 0;
  outmsgleft = 0;
  isMsgHanging = false;
  isMsgSending = false;

  // create the lock table, lockresult table and obj queue
  locktable.size = 20;
  locktable.bucket = (struct RuntimeNode **) RUNMALLOC_I(sizeof(struct RuntimeNode *)*20);
  /* Set allocation blocks*/
  locktable.listhead=NULL;
  locktable.listtail=NULL;
  /*Set data counts*/
  locktable.numelements = 0;
  lockobj = 0;
  lock2require = 0;
  lockresult = 0;
  lockflag = false;
#ifndef INTERRUPT
  reside = false;
#endif  
  objqueue.head = NULL;
  objqueue.tail = NULL;
  lockRedirectTbl = allocateRuntimeHash(20);
  objRedirectLockTbl = allocateRuntimeHash(20);

#ifdef PROFILE
  stall = false;
  //isInterrupt = true;
  totalexetime = -1;
  taskInfoIndex = 0;
  /*interruptInfoIndex = 0;
  taskInfoOverflow = false;
  interruptInfoOverflow = false;*/
#endif

  // other architecture related initialization
  initialization();

  initCommunication();

#if 0
#ifdef BOEHM_GC
  GC_init(); // Initialize the garbage collector
#endif
#ifdef CONSCHECK
  initializemmap();
#endif
  processOptions();
#endif // #if 0: for recovery and garbage collection
  initializeexithandler();

  // main process of the execution module
  if(corenum > NUMCORES - 1) {
	// non-executing cores, only processing communications
    failedtasks = NULL;
    activetasks = NULL;
/*#ifdef PROFILE
        BAMBOO_DEBUGPRINT(0xee01);
        BAMBOO_DEBUGPRINT_REG(taskInfoIndex);
        BAMBOO_DEBUGPRINT_REG(taskInfoOverflow);
		profileTaskStart("msg handling");
        }
 #endif*/
#ifdef PROFILE
    //isInterrupt = false;
#endif
	fakeExecution();
  } else {
	  /* Create table for failed tasks */
#if 0
	  failedtasks=genallocatehashtable((unsigned int (*)(void *)) &hashCodetpd,
                                       (int (*)(void *,void *)) &comparetpd);
#endif // #if 0: for recovery
	  failedtasks = NULL;
	  /* Create queue of active tasks */
	  activetasks=genallocatehashtable((unsigned int(*) (void *)) &hashCodetpd,
                                       (int(*) (void *,void *)) &comparetpd);
	  
	  /* Process task information */
	  processtasks();
	  
	  if(STARTUPCORE == corenum) {
		  /* Create startup object */
		  createstartupobject(argc, argv);
	  }

#ifdef DEBUG
	  BAMBOO_DEBUGPRINT(0xee00);
#endif

	  while(true) {
		  // check if there are new active tasks can be executed
		  executetasks();

#ifndef INTERRUPT
		  while(receiveObject() != -1) {
		  }
#endif  

#ifdef DEBUG
		  BAMBOO_DEBUGPRINT(0xee01);
#endif  
		  
		  // check if there are some pending objects, if yes, enqueue them and executetasks again
		  tocontinue = false;
#ifdef PROFILE
		  {
			  bool isChecking = false;
			  if(!isEmpty(&objqueue)) {
				  profileTaskStart("objqueue checking");
				  isChecking = true;
			  }
#endif
		  while(!isEmpty(&objqueue)) {
			  void * obj = NULL;
			  BAMBOO_START_CRITICAL_SECTION_OBJ_QUEUE();
#ifdef DEBUG
			  BAMBOO_DEBUGPRINT(0xf001);
#endif
#ifdef PROFILE
			  //isInterrupt = false;
#endif 
#ifdef DEBUG
			  BAMBOO_DEBUGPRINT(0xeee1);
#endif
			  sendStall = false;
			  tocontinue = true;
			  objitem = getTail(&objqueue);
			  objInfo = (struct transObjInfo *)objitem->objectptr;
			  obj = objInfo->objptr;
#ifdef DEBUG
			  BAMBOO_DEBUGPRINT_REG((int)obj);
#endif
			  // grab lock and flush the obj
			  grount = 0;
			  getwritelock_I(obj);
			  while(!lockflag) {
				  BAMBOO_WAITING_FOR_LOCK();
			  }
			  grount = lockresult;
#ifdef DEBUG
			  BAMBOO_DEBUGPRINT_REG(grount);
#endif

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
#ifdef CACHEFLUSH
				  BAMBOO_CACHE_FLUSH_RANGE((int)obj, classsize[((struct ___Object___ *)obj)->type]);
#endif
				  // enqueue the object
				  for(k = 0; k < objInfo->length; ++k) {
					  int taskindex = objInfo->queues[2 * k];
					  int paramindex = objInfo->queues[2 * k + 1];
					  struct parameterwrapper ** queues = &(paramqueues[corenum][taskindex][paramindex]);
#ifdef DEBUG
					  BAMBOO_DEBUGPRINT_REG(taskindex);
					  BAMBOO_DEBUGPRINT_REG(paramindex);
					  struct ___Object___ * tmpptr = (struct ___Object___ *)obj;
	  tprintf("Process %x(%d): receive obj %x(%lld), ptrflag %x\n", corenum, corenum, (int)obj, (long)obj, tmpptr->flag);
#endif

					  enqueueObject_I(obj, queues, 1);
#ifdef DEBUG
	  BAMBOO_DEBUGPRINT_REG(hashsize(activetasks));
#endif
				  }
				  removeItem(&objqueue, objitem);
				  releasewritelock_I(obj);
				  RUNFREE(objInfo->queues);
				  RUNFREE(objInfo);
			  } else {
				  // can not get lock
				  // put it at the end of the queue
				  // and try to execute active tasks already enqueued first
				  removeItem(&objqueue, objitem);
				  addNewItem_I(&objqueue, objInfo);
#ifdef PROFILE
				  //isInterrupt = true;
#endif
				  BAMBOO_CLOSE_CRITICAL_SECTION_OBJ_QUEUE();
#ifdef DEBUG
				  BAMBOO_DEBUGPRINT(0xf000);
#endif
				  break;
			  }
			  BAMBOO_CLOSE_CRITICAL_SECTION_OBJ_QUEUE();
#ifdef DEBUG
			  BAMBOO_DEBUGPRINT(0xf000);
#endif
		  }
#ifdef PROFILE
		      if(isChecking) {
				  profileTaskEnd();
			  }
		  }
#endif
#ifdef DEBUG
		  BAMBOO_DEBUGPRINT(0xee02);
#endif

		  if(!tocontinue) {
			  // check if stop
			  if(STARTUPCORE == corenum) {
				  if(isfirst) {
#ifdef DEBUG
					  BAMBOO_DEBUGPRINT(0xee03);
#endif
					  isfirst = false;
				  }
				  if((!waitconfirm) || 
						  (waitconfirm && (numconfirm == 0))) {
#ifdef DEBUG
					  BAMBOO_DEBUGPRINT(0xee04);
					  BAMBOO_DEBUGPRINT_REG(waitconfirm);
#endif
					  BAMBOO_START_CRITICAL_SECTION_STATUS();
#ifdef DEBUG
					  BAMBOO_DEBUGPRINT(0xf001);
#endif
					  corestatus[corenum] = 0;
					  numsendobjs[corenum] = self_numsendobjs;
					  numreceiveobjs[corenum] = self_numreceiveobjs;
					  // check the status of all cores
					  allStall = true;
#ifdef DEBUG
					  BAMBOO_DEBUGPRINT_REG(NUMCORES);
#endif
					  for(i = 0; i < NUMCORES; ++i) {
#ifdef DEBUG
						  BAMBOO_DEBUGPRINT(0xe000 + corestatus[i]);
#endif
						  if(corestatus[i] != 0) {
							  allStall = false;
							  break;
						  }
					  }
					  if(allStall) {
						  // check if the sum of send objs and receive obj are the same
						  // yes->check if the info is the latest; no->go on executing
						  sumsendobj = 0;
						  for(i = 0; i < NUMCORES; ++i) {
							  sumsendobj += numsendobjs[i];
#ifdef DEBUG
							  BAMBOO_DEBUGPRINT(0xf000 + numsendobjs[i]);
#endif
						  }		
						  for(i = 0; i < NUMCORES; ++i) {
							  sumsendobj -= numreceiveobjs[i];
#ifdef DEBUG
							  BAMBOO_DEBUGPRINT(0xf000 + numreceiveobjs[i]);
#endif
						  }
						  if(0 == sumsendobj) {
							  if(!waitconfirm) {
								  // the first time found all cores stall
								  // send out status confirm msg to all other cores
								  // reset the corestatus array too
#ifdef DEBUG
								  BAMBOO_DEBUGPRINT(0xee05);
#endif
								  corestatus[corenum] = 1;
								  for(i = 1; i < NUMCORES; ++i) {	
									  corestatus[i] = 1;
									  // send status confirm msg to core i
									  send_msg_1(i, 0xc);
								  }
								  waitconfirm = true;
								  numconfirm = NUMCORES - 1;
							  } else {
								  // all the core status info are the latest
								  // terminate; for profiling mode, send request to all
								  // other cores to pour out profiling data
#ifdef DEBUG
								  BAMBOO_DEBUGPRINT(0xee06);
#endif						  
							 
#ifdef USEIO
								  totalexetime = BAMBOO_GET_EXE_TIME();
#else
								  BAMBOO_DEBUGPRINT(0xbbbbbbbb);
								  BAMBOO_DEBUGPRINT((int)BAMBOO_GET_EXE_TIME());
#endif
								  // profile mode, send msgs to other cores to request pouring
								  // out progiling data
#ifdef PROFILE
								  BAMBOO_CLOSE_CRITICAL_SECTION_STATUS();
#ifdef DEBUG
								  BAMBOO_DEBUGPRINT(0xf000);
#endif
								  for(i = 1; i < NUMCORES; ++i) {
									  // send profile request msg to core i
									  send_msg_2(i, 6, totalexetime);
								  }
								  // pour profiling data on startup core
								  outputProfileData();
								  while(true) {
									  BAMBOO_START_CRITICAL_SECTION_STATUS();
#ifdef DEBUG
									  BAMBOO_DEBUGPRINT(0xf001);
#endif
									  profilestatus[corenum] = 0;
									  // check the status of all cores
									  allStall = true;
#ifdef DEBUG
									  BAMBOO_DEBUGPRINT_REG(NUMCORES);
#endif	
									  for(i = 0; i < NUMCORES; ++i) {
#ifdef DEBUG
										  BAMBOO_DEBUGPRINT(0xe000 + profilestatus[i]);
#endif
										  if(profilestatus[i] != 0) {
											  allStall = false;
											  break;
										  }
									  }
									  if(!allStall) {
										  int halt = 100;
										  BAMBOO_CLOSE_CRITICAL_SECTION_STATUS();
#ifdef DEBUG
										  BAMBOO_DEBUGPRINT(0xf000);
#endif
										  while(halt--) {
										  }
									  } else {
										  break;
									  }
								  }
#endif
								  terminate(); // All done.
							  } // if-else of line 364: if(!waitconfirm)
						  } else {
							  // still some objects on the fly on the network
							  // reset the waitconfirm and numconfirm
#ifdef DEBUG
								  BAMBOO_DEBUGPRINT(0xee07);
#endif
							  waitconfirm = false;
							  numconfirm = 0;
						  } // if-else of line 363: if(0 == sumsendobj)
					  } else {
						  // not all cores are stall, keep on waiting
#ifdef DEBUG
						  BAMBOO_DEBUGPRINT(0xee08);
#endif
						  waitconfirm = false;
						  numconfirm = 0;
					  } // if-else of line 347: if(allStall)
					  BAMBOO_CLOSE_CRITICAL_SECTION_STATUS();
#ifdef DEBUG
					  BAMBOO_DEBUGPRINT(0xf000);
#endif
				  } // if-else of line 320: if((!waitconfirm) || 
			  } else {
				  if(!sendStall) {
#ifdef DEBUG
					  BAMBOO_DEBUGPRINT(0xee09);
#endif
#ifdef PROFILE
					  if(!stall) {
#endif
						  if(isfirst) {
							  // wait for some time
							  int halt = 10000;
#ifdef DEBUG
							  BAMBOO_DEBUGPRINT(0xee0a);
#endif
							  while(halt--) {
							  }
							  isfirst = false;
						  } else {
							  // send StallMsg to startup core
#ifdef DEBUG
							  BAMBOO_DEBUGPRINT(0xee0b);
#endif
							  // send stall msg
							  send_msg_4(STARTUPCORE, 1, corenum, self_numsendobjs, self_numreceiveobjs);
							  sendStall = true;
							  isfirst = true;
							  busystatus = false;
						  }
#ifdef PROFILE
					  }
#endif
				  } else {
					  isfirst = true;
					  busystatus = false;
#ifdef DEBUG
					  BAMBOO_DEBUGPRINT(0xee0c);
#endif
				  } // if-else of line 464: if(!sendStall)
			  } // if-else of line 313: if(STARTUPCORE == corenum) 
		  } // if-else of line 311: if(!tocontinue)
	  } // line 193:  while(true) 
  } // right-bracket for if-else of line 153: if(corenum > NUMCORES - 1)

} // run()

void createstartupobject(int argc, char ** argv) {
  int i;

  /* Allocate startup object     */
#if 0
#ifdef PRECISE_GC
  struct ___StartupObject___ *startupobject=(struct ___StartupObject___*) allocate_new(NULL, STARTUPTYPE);
  struct ArrayObject * stringarray=allocate_newarray(NULL, STRINGARRAYTYPE, argc-1);
#else
  struct ___StartupObject___ *startupobject=(struct ___StartupObject___*) allocate_new(STARTUPTYPE);
  struct ArrayObject * stringarray=allocate_newarray(STRINGARRAYTYPE, argc-1);
#endif
#endif // #if 0: for garbage collection
  struct ___StartupObject___ *startupobject=(struct ___StartupObject___*) allocate_new(STARTUPTYPE);
  struct ArrayObject * stringarray=allocate_newarray(STRINGARRAYTYPE, argc-1);
  /* Build array of strings */
  startupobject->___parameters___=stringarray;
  for(i=1; i<argc; i++) {
    int length=strlen(argv[i]);
#if 0
#ifdef PRECISE_GC
    struct ___String___ *newstring=NewString(NULL, argv[i],length);
#else
    struct ___String___ *newstring=NewString(argv[i],length);
#endif
#endif // #if 0: for garbage collection
	struct ___String___ *newstring=NewString(argv[i],length);
    ((void **)(((char *)&stringarray->___length___)+sizeof(int)))[i-1]=newstring;
  }

  startupobject->isolate = 1;
  startupobject->version = 0;
  startupobject->lock = NULL;

  /* Set initialized flag for startup object */
  flagorandinit(startupobject,1,0xFFFFFFFF);
  enqueueObject(startupobject, NULL, 0);
#ifdef CACHEFLUSH
  BAMBOO_CACHE_FLUSH_ALL();
#endif
}

int hashCodetpd(struct taskparamdescriptor *ftd) {
  int hash=(int)ftd->task;
  int i;
  for(i=0; i<ftd->numParameters; i++) {
    hash^=(int)ftd->parameterArray[i];
  }
  return hash;
}

int comparetpd(struct taskparamdescriptor *ftd1, struct taskparamdescriptor *ftd2) {
  int i;
  if (ftd1->task!=ftd2->task)
    return 0;
  for(i=0; i<ftd1->numParameters; i++)
    if(ftd1->parameterArray[i]!=ftd2->parameterArray[i])
      return 0;
  return 1;
}

/* This function sets a tag. */
#if 0
#ifdef PRECISE_GC
void tagset(void *ptr, struct ___Object___ * obj, struct ___TagDescriptor___ * tagd) {
#else
void tagset(struct ___Object___ * obj, struct ___TagDescriptor___ * tagd) {
#endif
#endif // #if 0: for garbage collection
void tagset(struct ___Object___ * obj, struct ___TagDescriptor___ * tagd) {
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
#if 0
#ifdef PRECISE_GC
      int ptrarray[]={2, (int) ptr, (int) obj, (int)tagd};
      struct ArrayObject * ao=allocate_newarray(&ptrarray,TAGARRAYTYPE,TAGARRAYINTERVAL);
      obj=(struct ___Object___ *)ptrarray[2];
      tagd=(struct ___TagDescriptor___ *)ptrarray[3];
      td=(struct ___TagDescriptor___ *) obj->___tags___;
#else
      ao=allocate_newarray(TAGARRAYTYPE,TAGARRAYINTERVAL);
#endif
#endif // #if 0: for garbage collection
	  ao=allocate_newarray(TAGARRAYTYPE,TAGARRAYINTERVAL);

      ARRAYSET(ao, struct ___TagDescriptor___ *, 0, td);
      ARRAYSET(ao, struct ___TagDescriptor___ *, 1, tagd);
      obj->___tags___=(struct ___Object___ *) ao;
      ao->___cachedCode___=2;
    } else {
      /* Array Case */
      int i;
      struct ArrayObject *ao=(struct ArrayObject *) tagptr;
      for(i=0; i<ao->___cachedCode___; i++) {
	struct ___TagDescriptor___ * td=ARRAYGET(ao, struct ___TagDescriptor___*, i);
	if (td==tagd) {
	  return;
	}
      }
      if (ao->___cachedCode___<ao->___length___) {
	ARRAYSET(ao, struct ___TagDescriptor___ *, ao->___cachedCode___, tagd);
	ao->___cachedCode___++;
      } else {
#if 0
#ifdef PRECISE_GC
	int ptrarray[]={2,(int) ptr, (int) obj, (int) tagd};
	struct ArrayObject * aonew=allocate_newarray(&ptrarray,TAGARRAYTYPE,TAGARRAYINTERVAL+ao->___length___);
	obj=(struct ___Object___ *)ptrarray[2];
	tagd=(struct ___TagDescriptor___ *) ptrarray[3];
	ao=(struct ArrayObject *)obj->___tags___;
#else
	struct ArrayObject * aonew=allocate_newarray(TAGARRAYTYPE,TAGARRAYINTERVAL+ao->___length___);
#endif
#endif // #if 0: for garbage collection
	struct ArrayObject * aonew=allocate_newarray(TAGARRAYTYPE,TAGARRAYINTERVAL+ao->___length___);

	aonew->___cachedCode___=ao->___length___+1;
	for(i=0; i<ao->___length___; i++) {
	  ARRAYSET(aonew, struct ___TagDescriptor___*, i, ARRAYGET(ao, struct ___TagDescriptor___*, i));
	}
	ARRAYSET(aonew, struct ___TagDescriptor___ *, ao->___length___, tagd);
      }
    }
  }

  {
    struct ___Object___ * tagset=tagd->flagptr;
    if(tagset==NULL) {
      tagd->flagptr=obj;
    } else if (tagset->type!=OBJECTARRAYTYPE) {
#if 0
#ifdef PRECISE_GC
      int ptrarray[]={2, (int) ptr, (int) obj, (int)tagd};
      struct ArrayObject * ao=allocate_newarray(&ptrarray,OBJECTARRAYTYPE,OBJECTARRAYINTERVAL);
      obj=(struct ___Object___ *)ptrarray[2];
      tagd=(struct ___TagDescriptor___ *)ptrarray[3];
#else
      struct ArrayObject * ao=allocate_newarray(OBJECTARRAYTYPE,OBJECTARRAYINTERVAL);
#endif
#endif // #if 0: for garbage collection
	  struct ArrayObject * ao=allocate_newarray(OBJECTARRAYTYPE,OBJECTARRAYINTERVAL);
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
#if 0
#ifdef PRECISE_GC
	int ptrarray[]={2, (int) ptr, (int) obj, (int)tagd};
	struct ArrayObject * aonew=allocate_newarray(&ptrarray,OBJECTARRAYTYPE,OBJECTARRAYINTERVAL+ao->___length___);
	obj=(struct ___Object___ *)ptrarray[2];
	tagd=(struct ___TagDescriptor___ *)ptrarray[3];
	ao=(struct ArrayObject *)tagd->flagptr;
#else
	struct ArrayObject * aonew=allocate_newarray(OBJECTARRAYTYPE,OBJECTARRAYINTERVAL);
#endif
#endif // #if 0: for garbage collection
	struct ArrayObject * aonew=allocate_newarray(OBJECTARRAYTYPE,OBJECTARRAYINTERVAL);
	aonew->___cachedCode___=ao->___cachedCode___+1;
	for(i=0; i<ao->___length___; i++) {
	  ARRAYSET(aonew, struct ___Object___*, i, ARRAYGET(ao, struct ___Object___*, i));
	}
	ARRAYSET(aonew, struct ___Object___ *, ao->___cachedCode___, obj);
	tagd->flagptr=(struct ___Object___ *) aonew;
      }
    }
  }
}

/* This function clears a tag. */
#if 0
#ifdef PRECISE_GC
void tagclear(void *ptr, struct ___Object___ * obj, struct ___TagDescriptor___ * tagd) {
#else
void tagclear(struct ___Object___ * obj, struct ___TagDescriptor___ * tagd) {
#endif
#endif // #if 0: for garbage collection
void tagclear(struct ___Object___ * obj, struct ___TagDescriptor___ * tagd) {
  /* We'll assume that tag is alway there.
     Need to statically check for this of course. */
  struct ___Object___ * tagptr=obj->___tags___;

  if (tagptr->type==TAGTYPE) {
    if ((struct ___TagDescriptor___ *)tagptr==tagd)
      obj->___tags___=NULL;
    else
#ifndef MULTICORE
      printf("ERROR 1 in tagclear\n");
#else
	  ;
#endif
  } else {
    struct ArrayObject *ao=(struct ArrayObject *) tagptr;
    int i;
    for(i=0; i<ao->___cachedCode___; i++) {
      struct ___TagDescriptor___ * td=ARRAYGET(ao, struct ___TagDescriptor___ *, i);
      if (td==tagd) {
	ao->___cachedCode___--;
	if (i<ao->___cachedCode___)
	  ARRAYSET(ao, struct ___TagDescriptor___ *, i, ARRAYGET(ao, struct ___TagDescriptor___ *, ao->___cachedCode___));
	ARRAYSET(ao, struct ___TagDescriptor___ *, ao->___cachedCode___, NULL);
	if (ao->___cachedCode___==0)
	  obj->___tags___=NULL;
	goto PROCESSCLEAR;
      }
    }
#ifndef MULTICORE
    printf("ERROR 2 in tagclear\n");
#endif
  }
PROCESSCLEAR:
  {
    struct ___Object___ *tagset=tagd->flagptr;
    if (tagset->type!=OBJECTARRAYTYPE) {
      if (tagset==obj)
	tagd->flagptr=NULL;
      else
#ifndef MULTICORE
	printf("ERROR 3 in tagclear\n");
#else
	  ;
#endif
    } else {
      struct ArrayObject *ao=(struct ArrayObject *) tagset;
      int i;
      for(i=0; i<ao->___cachedCode___; i++) {
	struct ___Object___ * tobj=ARRAYGET(ao, struct ___Object___ *, i);
	if (tobj==obj) {
	  ao->___cachedCode___--;
	  if (i<ao->___cachedCode___)
	    ARRAYSET(ao, struct ___Object___ *, i, ARRAYGET(ao, struct ___Object___ *, ao->___cachedCode___));
	  ARRAYSET(ao, struct ___Object___ *, ao->___cachedCode___, NULL);
	  if (ao->___cachedCode___==0)
	    tagd->flagptr=NULL;
	  goto ENDCLEAR;
	}
      }
#ifndef MULTICORE
      printf("ERROR 4 in tagclear\n");
#endif
    }
  }
ENDCLEAR:
  return;
}

#if 0
/* This function allocates a new tag. */
#ifdef PRECISE_GC
struct ___TagDescriptor___ * allocate_tag(void *ptr, int index) {
  struct ___TagDescriptor___ * v=(struct ___TagDescriptor___ *) mygcmalloc((struct garbagelist *) ptr, classsize[TAGTYPE]);
#else
struct ___TagDescriptor___ * allocate_tag(int index) {
  struct ___TagDescriptor___ * v=FREEMALLOC(classsize[TAGTYPE]);
#endif
#endif // #if 0: for garbage collection
struct ___TagDescriptor___ * allocate_tag(int index) {
  struct ___TagDescriptor___ * v=FREEMALLOC(classsize[TAGTYPE]);
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
  {
    int oldflag=((int *)ptr)[1];
    int flag=ormask|oldflag;
    flag&=andmask;
    flagbody(ptr, flag, queues, length, false);
  }
}

bool intflagorand(void * ptr, int ormask, int andmask) {
  {
    int oldflag=((int *)ptr)[1];
    int flag=ormask|oldflag;
    flag&=andmask;
    if (flag==oldflag)   /* Don't do anything */
      return false;
    else {
      flagbody(ptr, flag, NULL, 0, false);
      return true;
    }
  }
}

void flagorandinit(void * ptr, int ormask, int andmask) {
  int oldflag=((int *)ptr)[1];
  int flag=ormask|oldflag;
  flag&=andmask;
  flagbody(ptr,flag,NULL,0,true);
}

void flagbody(struct ___Object___ *ptr, int flag, struct parameterwrapper ** vqueues, int vlength, bool isnew) {
  struct parameterwrapper * flagptr = NULL;
  int i = 0;
  struct parameterwrapper ** queues = vqueues;
  int length = vlength;
  int next;
  int UNUSED, UNUSED2;
  int * enterflags = NULL;
  if((!isnew) && (queues == NULL)) {
    if(corenum < NUMCORES) {
		queues = objectqueues[BAMBOO_NUM_OF_CORE][ptr->type];
		length = numqueues[BAMBOO_NUM_OF_CORE][ptr->type];
	} else {
		return;
	}
  }
  ptr->flag=flag;

  /*Remove object from all queues */
  for(i = 0; i < length; ++i) {
    flagptr = queues[i];
    ObjectHashget(flagptr->objectset, (int) ptr, (int *) &next, (int *) &enterflags, &UNUSED, &UNUSED2);
    ObjectHashremove(flagptr->objectset, (int)ptr);
    if (enterflags!=NULL)
      RUNFREE(enterflags);
  }
}

void enqueueObject(void * vptr, struct parameterwrapper ** vqueues, int vlength) {
	struct ___Object___ *ptr = (struct ___Object___ *)vptr;
	
	{
		//struct QueueItem *tmpptr;
		struct parameterwrapper * parameter=NULL;
		int j;
		int i;
		struct parameterwrapper * prevptr=NULL;
		struct ___Object___ *tagptr=NULL;
		struct parameterwrapper ** queues = vqueues;
		int length = vlength;
		if(corenum > NUMCORES - 1) {
			return;
		}
		if(queues == NULL) {
			queues = objectqueues[BAMBOO_NUM_OF_CORE][ptr->type];
			length = numqueues[BAMBOO_NUM_OF_CORE][ptr->type];
		}
		tagptr=ptr->___tags___;

		/* Outer loop iterates through all parameter queues an object of
		   this type could be in.  */
		for(j = 0; j < length; ++j) {
			parameter = queues[j];     
			/* Check tags */
			if (parameter->numbertags>0) {
				if (tagptr==NULL)
					goto nextloop; //that means the object has no tag but that param needs tag
				else if(tagptr->type==TAGTYPE) { //one tag
					//struct ___TagDescriptor___ * tag=(struct ___TagDescriptor___*) tagptr;	 
					for(i=0; i<parameter->numbertags; i++) {
						//slotid is parameter->tagarray[2*i];
						int tagid=parameter->tagarray[2*i+1];
						if (tagid!=tagptr->flag)
							goto nextloop; /*We don't have this tag */
					}
				} else { //multiple tags
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

void enqueueObject_I(void * vptr, struct parameterwrapper ** vqueues, int vlength) {
	struct ___Object___ *ptr = (struct ___Object___ *)vptr;
	
	{
		//struct QueueItem *tmpptr;
		struct parameterwrapper * parameter=NULL;
		int j;
		int i;
		struct parameterwrapper * prevptr=NULL;
		struct ___Object___ *tagptr=NULL;
		struct parameterwrapper ** queues = vqueues;
		int length = vlength;
		if(corenum > NUMCORES - 1) {
			return;
		}
		if(queues == NULL) {
			queues = objectqueues[BAMBOO_NUM_OF_CORE][ptr->type];
			length = numqueues[BAMBOO_NUM_OF_CORE][ptr->type];
		}
		tagptr=ptr->___tags___;

		/* Outer loop iterates through all parameter queues an object of
		   this type could be in.  */
		for(j = 0; j < length; ++j) {
			parameter = queues[j];     
			/* Check tags */
			if (parameter->numbertags>0) {
				if (tagptr==NULL)
					goto nextloop; //that means the object has no tag but that param needs tag
				else if(tagptr->type==TAGTYPE) { //one tag
					//struct ___TagDescriptor___ * tag=(struct ___TagDescriptor___*) tagptr;	 
					for(i=0; i<parameter->numbertags; i++) {
						//slotid is parameter->tagarray[2*i];
						int tagid=parameter->tagarray[2*i+1];
						if (tagid!=tagptr->flag)
							goto nextloop; /*We don't have this tag */
					}
				} else { //multiple tags
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
			return (int *)(locks[0]);
		}
	}
}

void addAliasLock(void * ptr, int lock) {
  struct ___Object___ * obj = (struct ___Object___ *)ptr;
  if(((int)ptr != lock) && (obj->lock != (int*)lock)) {
    // originally no alias lock associated or have a different alias lock
    // flush it as the new one
    obj->lock = (int *)lock;
  }
}

/* Message format:
 *      type + Msgbody
 * type: 0 -- transfer object
 *       1 -- transfer stall msg
 *       2 -- lock request
 *       3 -- lock grount
 *       4 -- lock deny
 *       5 -- lock release
 *       // add for profile info
 *       6 -- transfer profile output msg
 *       7 -- transfer profile output finish msg
 *       // add for alias lock strategy
 *       8 -- redirect lock request
 *       9 -- lock grant with redirect info
 *       a -- lock deny with redirect info
 *       b -- lock release with redirect info
 *       c -- status confirm request
 *       d -- status report msg
 *       e -- terminate
 *
 * ObjMsg: 0 + size of msg + obj's address + (task index + param index)+
 * StallMsg: 1 + corenum + sendobjs + receiveobjs (size is always 4 * sizeof(int))
 * LockMsg: 2 + lock type + obj pointer + lock + request core (size is always 5 * sizeof(int))
 *          3/4/5 + lock type + obj pointer + lock (size is always 4 * sizeof(int))
 *          8 + lock type + obj pointer +  redirect lock + root request core + request core (size is always 6 * sizeof(int))
 *          9/a + lock type + obj pointer + redirect lock (size is always 4 * sizeof(int))
 *          b + lock type + lock + redirect lock (size is always 4 * sizeof(int))
 *          lock type: 0 -- read; 1 -- write
 * ProfileMsg: 6 + totalexetime (size is always 2 * sizeof(int))
 *             7 + corenum (size is always 2 * sizeof(int))
 * StatusMsg: c (size is always 1 * sizeof(int))
 *            d + status + corenum (size is always 3 * sizeof(int))
 *            status: 0 -- stall; 1 -- busy
 * TerminateMsg: e (size is always 1 * sizeof(int)
 */

#ifdef PROFILE
// output the profiling data
void outputProfileData() {
#ifdef USEIO
  FILE * fp;
  char fn[50];
  int self_y, self_x;
  char c_y, c_x;
  int i;
  int totaltasktime = 0;
  int preprocessingtime = 0;
  int objqueuecheckingtime = 0;
  int postprocessingtime = 0;
  //int interruptiontime = 0;
  int other = 0;
  int averagetasktime = 0;
  int tasknum = 0;

  for(i = 0; i < 50; i++) {
    fn[i] = 0;
  }

  calCoords(corenum, &self_y, &self_x);
  c_y = (char)self_y + '0';
  c_x = (char)self_x + '0';
  strcat(fn, "profile_");
  strcat(fn, &c_x);
  strcat(fn, "_");
  strcat(fn, &c_y);
  strcat(fn, ".rst");

  if((fp = fopen(fn, "w+")) == NULL) {
    fprintf(stderr, "fopen error\n");
    return;
  }

  fprintf(fp, "Task Name, Start Time, End Time, Duration, Exit Index(, NewObj Name, Num)+\n");
  // output task related info
  for(i = 0; i < taskInfoIndex; i++) {
    TaskInfo* tmpTInfo = taskInfoArray[i];
    int duration = tmpTInfo->endTime - tmpTInfo->startTime;
    fprintf(fp, "%s, %d, %d, %d, %d", tmpTInfo->taskName, tmpTInfo->startTime, tmpTInfo->endTime, duration, tmpTInfo->exitIndex);
	// summarize new obj info
	if(tmpTInfo->newObjs != NULL) {
		struct RuntimeHash * nobjtbl = allocateRuntimeHash(5);
		struct RuntimeIterator * iter = NULL;
		while(0 == isEmpty(tmpTInfo->newObjs)) {
			char * objtype = (char *)(getItem(tmpTInfo->newObjs));
			if(RuntimeHashcontainskey(nobjtbl, (int)(objtype))) {
				int num = 0;
				RuntimeHashget(nobjtbl, (int)objtype, &num);
				RuntimeHashremovekey(nobjtbl, (int)objtype);
				num++;
				RuntimeHashadd(nobjtbl, (int)objtype, num);
			} else {
				RuntimeHashadd(nobjtbl, (int)objtype, 1);
			}
			//fprintf(stderr, "new obj!\n");
		}

		// output all new obj info
		iter = RuntimeHashcreateiterator(nobjtbl);
		while(RunhasNext(iter)) {
			char * objtype = (char *)Runkey(iter);
			int num = Runnext(iter);
			fprintf(fp, ", %s, %d", objtype, num);
		}
	}
	fprintf(fp, "\n");
    if(strcmp(tmpTInfo->taskName, "tpd checking") == 0) {
      preprocessingtime += duration;
    } else if(strcmp(tmpTInfo->taskName, "post task execution") == 0) {
      postprocessingtime += duration;
    } else if(strcmp(tmpTInfo->taskName, "objqueue checking") == 0) {
      objqueuecheckingtime += duration;
    } else {
      totaltasktime += duration;
      averagetasktime += duration;
      tasknum++;
    }
  }

  if(taskInfoOverflow) {
    fprintf(stderr, "Caution: task info overflow!\n");
  }

  other = totalexetime - totaltasktime - preprocessingtime - postprocessingtime;
  averagetasktime /= tasknum;

  fprintf(fp, "\nTotal time: %d\n", totalexetime);
  fprintf(fp, "Total task execution time: %d (%f%%)\n", totaltasktime, ((double)totaltasktime/(double)totalexetime)*100);
  fprintf(fp, "Total objqueue checking time: %d (%f%%)\n", objqueuecheckingtime, ((double)objqueuecheckingtime/(double)totalexetime)*100);
  fprintf(fp, "Total pre-processing time: %d (%f%%)\n", preprocessingtime, ((double)preprocessingtime/(double)totalexetime)*100);
  fprintf(fp, "Total post-processing time: %d (%f%%)\n", postprocessingtime, ((double)postprocessingtime/(double)totalexetime)*100);
  fprintf(fp, "Other time: %d (%f%%)\n", other, ((double)other/(double)totalexetime)*100);

  fprintf(fp, "\nAverage task execution time: %d\n", averagetasktime);

  fclose(fp);
#else
  int i = 0;
  int j = 0;

  BAMBOO_DEBUGPRINT(0xdddd);
  // output task related info
  for(i= 0; i < taskInfoIndex; i++) {
    TaskInfo* tmpTInfo = taskInfoArray[i];
    char* tmpName = tmpTInfo->taskName;
    int nameLen = strlen(tmpName);
    BAMBOO_DEBUGPRINT(0xddda);
    for(j = 0; j < nameLen; j++) {
      BAMBOO_DEBUGPRINT_REG(tmpName[j]);
    }
    BAMBOO_DEBUGPRINT(0xdddb);
    BAMBOO_DEBUGPRINT_REG(tmpTInfo->startTime);
    BAMBOO_DEBUGPRINT_REG(tmpTInfo->endTime);
	BAMBOO_DEBUGPRINT_REG(tmpTInfo->exitIndex);
	if(tmpTInfo->newObjs != NULL) {
		struct RuntimeHash * nobjtbl = allocateRuntimeHash(5);
		struct RuntimeIterator * iter = NULL;
		while(0 == isEmpty(tmpTInfo->newObjs)) {
			char * objtype = (char *)(getItem(tmpTInfo->newObjs));
			if(RuntimeHashcontainskey(nobjtbl, (int)(objtype))) {
				int num = 0;
				RuntimeHashget(nobjtbl, (int)objtype, &num);
				RuntimeHashremovekey(nobjtbl, (int)objtype);
				num++;
				RuntimeHashadd(nobjtbl, (int)objtype, num);
			} else {
				RuntimeHashadd(nobjtbl, (int)objtype, 1);
			}
		}

		// ouput all new obj info
		iter = RuntimeHashcreateiterator(nobjtbl);
		while(RunhasNext(iter)) {
			char * objtype = (char *)Runkey(iter);
			int num = Runnext(iter);
			int nameLen = strlen(objtype);
			BAMBOO_DEBUGPRINT(0xddda);
			for(j = 0; j < nameLen; j++) {
				BAMBOO_DEBUGPRINT_REG(objtype[j]);
			}
			BAMBOO_DEBUGPRINT(0xdddb);
			BAMBOO_DEBUGPRINT_REG(num);
		}
	}
    BAMBOO_DEBUGPRINT(0xdddc);
  }

  if(taskInfoOverflow) {
    BAMBOO_DEBUGPRINT(0xefee);
  }

  // output interrupt related info
  /*for(i = 0; i < interruptInfoIndex; i++) {
       InterruptInfo* tmpIInfo = interruptInfoArray[i];
       BAMBOO_DEBUGPRINT(0xddde);
       BAMBOO_DEBUGPRINT_REG(tmpIInfo->startTime);
       BAMBOO_DEBUGPRINT_REG(tmpIInfo->endTime);
       BAMBOO_DEBUGPRINT(0xdddf);
     }

     if(interruptInfoOverflow) {
       BAMBOO_DEBUGPRINT(0xefef);
     }*/

  BAMBOO_DEBUGPRINT(0xeeee);
#endif
}

inline void setTaskExitIndex(int index) {
	taskInfoArray[taskInfoIndex]->exitIndex = index;
}

inline void addNewObjInfo(void * nobj) {
	if(taskInfoArray[taskInfoIndex]->newObjs == NULL) {
		taskInfoArray[taskInfoIndex]->newObjs = createQueue();
	}
	addNewItem(taskInfoArray[taskInfoIndex]->newObjs, nobj);
}
#endif

/* this function is to process lock requests. 
 * can only be invoked in receiveObject() */
// if return -1: the lock request is redirected
//            0: the lock request is approved
//            1: the lock request is denied
int processlockrequest(int locktype, int lock, int obj, int requestcore, int rootrequestcore, bool cache);

// receive object transferred from other cores
// or the terminate message from other cores
// Should be invoked in critical sections!!
// NOTICE: following format is for threadsimulate version only
//         RAW version please see previous description
// format: type + object
// type: -1--stall msg
//      !-1--object
// return value: 0--received an object
//               1--received nothing
//               2--received a Stall Msg
//               3--received a lock Msg
//               RAW version: -1 -- received nothing
//                            otherwise -- received msg type
int receiveObject() {
  int deny = 0;
  //int targetcore = 0;
  
msg:
  if(receiveMsg() == -1) {
	  return -1;
  }

  if(msgdataindex == msglength) {
    // received a whole msg
    int type, data1;             // will receive at least 2 words including type
    type = msgdata[0];
    data1 = msgdata[1];
    switch(type) {
    case 0: {
      // receive a object transfer msg
      struct transObjInfo * transObj = RUNMALLOC_I(sizeof(struct transObjInfo));
      int k = 0;
#ifdef DEBUG
#ifndef TILERA
	  BAMBOO_DEBUGPRINT(0xe880);
#endif
#endif
      if(corenum > NUMCORES - 1) {
#ifndef TILERA
		  BAMBOO_DEBUGPRINT_REG(msgdata[2]);
#endif
		  BAMBOO_EXIT(0xa005);
      } /*else if((corenum == STARTUPCORE) && waitconfirm) {
		  waitconfirm = false;
		  numconfirm = 0;
	  }*/
      // store the object and its corresponding queue info, enqueue it later
      transObj->objptr = (void *)msgdata[2];                                           // data1 is now size of the msg
      transObj->length = (msglength - 3) / 2;
      transObj->queues = RUNMALLOC_I(sizeof(int)*(msglength - 3));
      for(k = 0; k < transObj->length; ++k) {
		  transObj->queues[2*k] = msgdata[3+2*k];
#ifdef DEBUG
#ifndef TILERA
		  BAMBOO_DEBUGPRINT_REG(transObj->queues[2*k]);
#endif
#endif
		  transObj->queues[2*k+1] = msgdata[3+2*k+1];
#ifdef DEBUG
#ifndef TILERA
		  BAMBOO_DEBUGPRINT_REG(transObj->queues[2*k+1]);
#endif
#endif
      }
      // check if there is an existing duplicate item
      {
		  struct QueueItem * qitem = getTail(&objqueue);
		  struct QueueItem * prev = NULL;
		  while(qitem != NULL) {
			  struct transObjInfo * tmpinfo = (struct transObjInfo *)(qitem->objectptr);
			  if(tmpinfo->objptr == transObj->objptr) {
				  // the same object, remove outdate one
				  removeItem(&objqueue, qitem);
			  } else {
				  prev = qitem;
			  }
			  if(prev == NULL) {
				  qitem = getTail(&objqueue);
			  } else {
				  qitem = getNextQueueItem(prev);
			  }
		  }
		  addNewItem_I(&objqueue, (void *)transObj);
	  }
      ++(self_numreceiveobjs);
      break;
    }

    case 1: {
      // receive a stall msg
      if(corenum != STARTUPCORE) {
		  // non startup core can not receive stall msg
		  // return -1
#ifndef TILERA
		  BAMBOO_DEBUGPRINT_REG(data1);
#endif
		  BAMBOO_EXIT(0xa006);
      } /*else if(waitconfirm) {
		  waitconfirm = false;
		  numconfirm = 0;
	  }*/
      if(data1 < NUMCORES) {
#ifdef DEBUG
#ifndef TILERA
		  BAMBOO_DEBUGPRINT(0xe881);
#endif
#endif
		  corestatus[data1] = 0;
		  numsendobjs[data1] = msgdata[2];
		  numreceiveobjs[data1] = msgdata[3];
      }
      break;
    }

    case 2: {
      // receive lock request msg, handle it right now
      // check to see if there is a lock exist in locktbl for the required obj
	  // data1 -> lock type
	  int data2 = msgdata[2]; // obj pointer
      int data3 = msgdata[3]; // lock
	  int data4 = msgdata[4]; // request core
      deny = processlockrequest(data1, data3, data2, data4, data4, true);  // -1: redirected, 0: approved, 1: denied
	  if(deny == -1) {
		  // this lock request is redirected
		  break;
	  } else {
		  // send response msg
		  // for 32 bit machine, the size is always 4 words
		  int tmp = deny==1?4:3;
		  if(isMsgSending) {
			  cache_msg_4(data4, tmp, data1, data2, data3);
		  } else {
			  send_msg_4(data4, tmp, data1, data2, data3);
		  }
	  }
      break;
    }

    case 3: {
      // receive lock grount msg
      if(corenum > NUMCORES - 1) {
#ifndef TILERA
		  BAMBOO_DEBUGPRINT_REG(msgdata[2]);
#endif
		  BAMBOO_EXIT(0xa007);
      } /*else if((corenum == STARTUPCORE) && waitconfirm) {
		  waitconfirm = false;
		  numconfirm = 0;
	  }*/
      if((lockobj == msgdata[2]) && (lock2require == msgdata[3])) {
#ifdef DEBUG
#ifndef TILERA
		  BAMBOO_DEBUGPRINT(0xe882);
#endif
#endif
		  lockresult = 1;
		  lockflag = true;
#ifndef INTERRUPT
		  reside = false;
#endif
	  } else {
		  // conflicts on lockresults
#ifndef TILERA
		  BAMBOO_DEBUGPRINT_REG(msgdata[2]);
#endif
		  BAMBOO_EXIT(0xa008);
      }
      break;
    }

    case 4: {
      // receive lock grount/deny msg
      if(corenum > NUMCORES - 1) {
#ifndef TILERA
		  BAMBOO_DEBUGPRINT_REG(msgdata[2]);
#endif
		  BAMBOO_EXIT(0xa009);
      } /*else if((corenum == STARTUPCORE) && waitconfirm) {
		  waitconfirm = false;
		  numconfirm = 0;
	  }*/
      if((lockobj == msgdata[2]) && (lock2require == msgdata[3])) {
#ifdef DEBUG
#ifndef TILERA
		  BAMBOO_DEBUGPRINT(0xe883);
#endif
#endif
		  lockresult = 0;
		  lockflag = true;
#ifndef INTERRUPT
		  reside = false;
#endif
      } else {
		  // conflicts on lockresults
#ifndef TILERA
		  BAMBOO_DEBUGPRINT_REG(msgdata[2]);
#endif
		  BAMBOO_EXIT(0xa00a);
      }
      break;
    }

    case 5: {
      // receive lock release msg
	  /*if((corenum == STARTUPCORE) && waitconfirm) {
		  waitconfirm = false;
		  numconfirm = 0;
	  }*/
      if(!RuntimeHashcontainskey(locktbl, msgdata[3])) {
		  // no locks for this object, something is wrong
#ifndef TILERA
		  BAMBOO_DEBUGPRINT_REG(msgdata[3]);
#endif
		  BAMBOO_EXIT(0xa00b);
      } else {
		  int rwlock_obj = 0;
		  struct LockValue * lockvalue = NULL;
		  RuntimeHashget(locktbl, msgdata[3], &rwlock_obj);
		  lockvalue = (struct LockValue*)(rwlock_obj);
#ifdef DEBUG
#ifndef TILERA
		  BAMBOO_DEBUGPRINT(0xe884);
		  BAMBOO_DEBUGPRINT_REG(lockvalue->value);
#endif
#endif
		  if(data1 == 0) {
			  lockvalue->value--;
		  } else {
			  lockvalue->value++;
		  }
#ifdef DEBUG
#ifndef TILERA
		  BAMBOO_DEBUGPRINT_REG(lockvalue->value);
#endif
#endif
      }
      break;
    }

#ifdef PROFILE
    case 6: {
      // receive an output profile data request msg
      if(corenum == STARTUPCORE) {
		  // startup core can not receive profile output finish msg
		  BAMBOO_EXIT(0xa00c);
      }
#ifdef DEBUG
#ifndef TILEAR
	  BAMBOO_DEBUGPRINT(0xe885);
#endif
#endif
	  stall = true;
	  totalexetime = data1;
	  outputProfileData();
	  if(isMsgSending) {
		  cache_msg_2(STARTUPCORE, 7, corenum);
	  } else {
		  send_msg_2(STARTUPCORE, 7, corenum);
	  }
      break;
    }

    case 7: {
      // receive a profile output finish msg
      if(corenum != STARTUPCORE) {
		  // non startup core can not receive profile output finish msg
#ifndef TILERA
		  BAMBOO_DEBUGPRINT_REG(data1);
#endif
		  BAMBOO_EXIT(0xa00d);
      }
#ifdef DEBUG
#ifndef TILERA
	  BAMBOO_DEBUGPRINT(0xe886);
#endif
#endif
      profilestatus[data1] = 0;
      break;
    }
#endif

	case 8: {
	  // receive a redirect lock request msg, handle it right now
      // check to see if there is a lock exist in locktbl for the required obj
	  // data1 -> lock type
	  int data2 = msgdata[2]; // obj pointer
      int data3 = msgdata[3]; // redirect lock
	  int data4 = msgdata[4]; // root request core
	  int data5 = msgdata[5]; // request core
	  deny = processlockrequest(data1, data3, data2, data5, data4, true);
	  if(deny == -1) {
		  // this lock request is redirected
		  break;
	  } else {
		  // send response msg
		  // for 32 bit machine, the size is always 4 words
		  if(isMsgSending) {
			  cache_msg_4(data4, deny==1?0xa:9, data1, data2, data3);
		  } else {
			  send_msg_4(data4, deny==1?0xa:9, data1, data2, data3);
		  }
	  }
	  break;
	}

	case 9: {
		// receive a lock grant msg with redirect info
		if(corenum > NUMCORES - 1) {
#ifndef TILERA
			BAMBOO_DEBUGPRINT_REG(msgdata[2]);
#endif
			BAMBOO_EXIT(0xa00e);
		}/* else if((corenum == STARTUPCORE) && waitconfirm) {
		  waitconfirm = false;
		  numconfirm = 0;
	  }*/
      if(lockobj == msgdata[2]) {
#ifdef DEBUG
#ifndef TILERA
		  BAMBOO_DEBUGPRINT(0xe891);
#endif
#endif
		  lockresult = 1;
		  lockflag = true;
		  RuntimeHashadd_I(objRedirectLockTbl, lockobj, msgdata[3]);
#ifndef INTERRUPT
		  reside = false;
#endif
      } else {
		  // conflicts on lockresults
#ifndef TILERA
		  BAMBOO_DEBUGPRINT_REG(msgdata[2]);
#endif
		  BAMBOO_EXIT(0xa00f);
      }
		break;
	}
	
	case 0xa: {
	  // receive a lock deny msg with redirect info
	  if(corenum > NUMCORES - 1) {
#ifndef TILERA
		  BAMBOO_DEBUGPRINT_REG(msgdata[2]);
#endif
		  BAMBOO_EXIT(0xa010);
	  }/* else if((corenum == STARTUPCORE) && waitconfirm) {
		  waitconfirm = false;
		  numconfirm = 0;
	  }*/
      if(lockobj == msgdata[2]) {
#ifdef DEBUG
#ifndef TILERA
		  BAMBOO_DEBUGPRINT(0xe892);
#endif
#endif
		  lockresult = 0;
		  lockflag = true;
		  //RuntimeHashadd_I(objRedirectLockTbl, lockobj, msgdata[3]);
#ifndef INTERRUPT
		  reside = false;
#endif
      } else {
		  // conflicts on lockresults
#ifndef TILERA
		  BAMBOO_DEBUGPRINT_REG(msgdata[2]);
#endif
		  BAMBOO_EXIT(0xa011);
      }
		break;
	}

	case 0xb: {
	  // receive a lock release msg with redirect info
	  /*if((corenum == STARTUPCORE) && waitconfirm) {
		  waitconfirm = false;
		  numconfirm = 0;
	  }*/
	  if(!RuntimeHashcontainskey(locktbl, msgdata[2])) {
		  // no locks for this object, something is wrong
#ifndef TILERA
		  BAMBOO_DEBUGPRINT_REG(msgdata[2]);
#endif
		  BAMBOO_EXIT(0xa012);
      } else {
		  int rwlock_obj = 0;
		  struct LockValue * lockvalue = NULL;
		  RuntimeHashget(locktbl, msgdata[2], &rwlock_obj);
		  lockvalue = (struct LockValue*)(rwlock_obj);
#ifdef DEBUG
#ifndef TILERA
		  BAMBOO_DEBUGPRINT(0xe893);
		  BAMBOO_DEBUGPRINT_REG(lockvalue->value);
#endif
#endif
		  if(data1 == 0) {
			  lockvalue->value--;
		  } else {
			  lockvalue->value++;
		  }
#ifdef DEBUG
#ifndef TILERA
		  BAMBOO_DEBUGPRINT_REG(lockvalue->value);
#endif
#endif
		  lockvalue->redirectlock = msgdata[3];
	  }
	  break;
	}
	
	case 0xc: {
      // receive a status confirm info
	  if((corenum == STARTUPCORE) || (corenum > NUMCORES - 1)) {
		  // wrong core to receive such msg
		  BAMBOO_EXIT(0xa013);
      } else {
		  // send response msg
#ifdef DEBUG
#ifndef TILERA
		  BAMBOO_DEBUGPRINT(0xe887);
#endif
#endif
		  if(isMsgSending) {
			  cache_msg_3(STARTUPCORE, 0xd, busystatus?1:0, corenum);
		  } else {
			  send_msg_3(STARTUPCORE, 0xd, busystatus?1:0, corenum);
		  }
      }
	  break;
	}

	case 0xd: {
	  // receive a status confirm info
	  if(corenum != STARTUPCORE) {
		  // wrong core to receive such msg
#ifndef TILERA
		  BAMBOO_DEBUGPRINT_REG(msgdata[2]);
#endif
		  BAMBOO_EXIT(0xa014);
      } else {
#ifdef DEBUG
#ifndef TILERA
		  BAMBOO_DEBUGPRINT(0xe888);
#endif
#endif
		  if(waitconfirm) {
			  numconfirm--;
		  }
		  corestatus[msgdata[2]] = msgdata[1];
      }
	  break;
	}

	case 0xe: {
	  // receive a terminate msg
#ifdef DEBUG
#ifndef TILERA
				  BAMBOO_DEBUGPRINT(0xe889);
#endif
#endif
				  BAMBOO_EXIT(0);
	  break;
	}
	
    default:
      break;
    }
    for(msgdataindex--; msgdataindex > 0; --msgdataindex) {
      msgdata[msgdataindex] = -1;
    }
    msgtype = -1;
    msglength = 30;
#ifdef DEBUG
#ifndef TILERA
    BAMBOO_DEBUGPRINT(0xe88a);
#endif
#endif

    if(BAMBOO_MSG_AVAIL() != 0) {
      goto msg;
    }
#ifdef PROFILE
	/*if(isInterrupt) {
	    profileTaskEnd();
    }*/
#endif
    return type;
  } else {
    // not a whole msg
#ifdef DEBUG
#ifndef TILERA
    BAMBOO_DEBUGPRINT(0xe88b);
#endif
#endif
#ifdef PROFILE
/*    if(isInterrupt) {
          profileTaskEnd();
      }*/
#endif
    return -2;
  }
}

/* this function is to process lock requests. 
 * can only be invoked in receiveObject() */
// if return -1: the lock request is redirected
//            0: the lock request is approved
//            1: the lock request is denied
int processlockrequest(int locktype, int lock, int obj, int requestcore, int rootrequestcore, bool cache) {
  int deny = 0;
  if( ((lock >> 5) % BAMBOO_TOTALCORE) != corenum ) {
	  // the lock should not be on this core
#ifndef TILERA
	  BAMBOO_DEBUGPRINT_REG(requestcore);
	  BAMBOO_DEBUGPRINT_REG(lock);
	  BAMBOO_DEBUGPRINT_REG(corenum);
#endif
	  BAMBOO_EXIT(0xa015);
  }
  /*if((corenum == STARTUPCORE) && waitconfirm) {
	  waitconfirm = false;
	  numconfirm = 0;
  }*/
  if(!RuntimeHashcontainskey(locktbl, lock)) {
	  // no locks for this object
	  // first time to operate on this shared object
	  // create a lock for it
	  // the lock is an integer: 0 -- stall, >0 -- read lock, -1 -- write lock
	  struct LockValue * lockvalue = (struct LockValue *)(RUNMALLOC_I(sizeof(struct LockValue)));
	  lockvalue->redirectlock = 0;
#ifdef DEBUG
#ifndef TILERA
	  BAMBOO_DEBUGPRINT(0xe110);
#endif
#endif
	  if(locktype == 0) {
		  lockvalue->value = 1;
	  } else {
		  lockvalue->value = -1;
	  }
	  RuntimeHashadd_I(locktbl, lock, (int)lockvalue);
  } else {
	  int rwlock_obj = 0;
	  struct LockValue * lockvalue = NULL;
#ifdef DEBUG
#ifndef TILERA
	  BAMBOO_DEBUGPRINT(0xe111);
#endif
#endif
	  RuntimeHashget(locktbl, lock, &rwlock_obj);
	  lockvalue = (struct LockValue *)(rwlock_obj);
#ifdef DEBUG
#ifndef TILERA
	  BAMBOO_DEBUGPRINT_REG(lockvalue->redirectlock);
#endif
#endif
	  if(lockvalue->redirectlock != 0) {
		  // this lock is redirected
#ifdef DEBUG
#ifndef TILERA
		  BAMBOO_DEBUGPRINT(0xe112);
#endif
#endif
		  if(locktype == 0) {
			  getreadlock_I_r((void *)obj, (void *)lockvalue->redirectlock, rootrequestcore, cache);
		  } else {
			  getwritelock_I_r((void *)obj, (void *)lockvalue->redirectlock, rootrequestcore, cache);
		  }
		  return -1;  // redirected
	  } else {
#ifdef DEBUG
#ifndef TILERA
		  BAMBOO_DEBUGPRINT_REG(lockvalue->value);
#endif
#endif
		  if(0 == lockvalue->value) {
			  if(locktype == 0) {
				  lockvalue->value = 1;
			  } else {
				  lockvalue->value = -1;
			  }
		  } else if((lockvalue->value > 0) && (locktype == 0)) {
			  // read lock request and there are only read locks
			  lockvalue->value++;
		  } else {
			  deny = 1;
		  }
#ifdef DEBUG
#ifndef TILERA
		  BAMBOO_DEBUGPRINT_REG(lockvalue->value);
#endif
#endif
	  }
  }
  return deny;
}

bool getreadlock(void * ptr) {
  int targetcore = 0;
  lockobj = (int)ptr;
  if(((struct ___Object___ *)ptr)->lock == NULL) {
	lock2require = lockobj;
  } else {
	lock2require = (int)(((struct ___Object___ *)ptr)->lock);
  }
  targetcore = (lock2require >> 5) % BAMBOO_TOTALCORE;
  lockflag = false;
#ifndef INTERRUPT
  reside = false;
#endif
  lockresult = 0;

  if(targetcore == corenum) {
    // reside on this core
    int deny = 0;
	BAMBOO_START_CRITICAL_SECTION_LOCK();
#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xf001);
#endif
	deny = processlockrequest(0, lock2require, (int)ptr, corenum, corenum, false);
	BAMBOO_CLOSE_CRITICAL_SECTION_LOCK();
#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xf000);
#endif
    if(deny == -1) {
		// redirected
		return true;
	} else {
		if(lockobj == (int)ptr) {
			if(deny) {
				lockresult = 0;
			} else {
				lockresult = 1;
			}
			lockflag = true;
#ifndef INTERRUPT
			reside = true;
#endif
		} else {
			// conflicts on lockresults
			BAMBOO_EXIT(0xa016);
		}
	}
    return true;
  } else {
	  // send lock request msg
	  // for 32 bit machine, the size is always 5 words
	  send_msg_5(targetcore, 2, 0, (int)ptr, lock2require, corenum);
  }
  return true;
}

void releasereadlock(void * ptr) {
  int targetcore = 0;
  int reallock = 0;
  if(((struct ___Object___ *)ptr)->lock == NULL) {
	reallock = (int)ptr;
  } else {
	reallock = (int)(((struct ___Object___ *)ptr)->lock);
  }
  targetcore = (reallock >> 5) % BAMBOO_TOTALCORE;

  if(targetcore == corenum) {
	BAMBOO_START_CRITICAL_SECTION_LOCK();
#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xf001);
#endif
    // reside on this core
    if(!RuntimeHashcontainskey(locktbl, reallock)) {
      // no locks for this object, something is wrong
      BAMBOO_EXIT(0xa017);
    } else {
      int rwlock_obj = 0;
	  struct LockValue * lockvalue = NULL;
      RuntimeHashget(locktbl, reallock, &rwlock_obj);
	  lockvalue = (struct LockValue *)rwlock_obj;
      lockvalue->value--;
    }
	BAMBOO_CLOSE_CRITICAL_SECTION_LOCK();
#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xf000);
#endif
    return;
  } else {
	// send lock release msg
	// for 32 bit machine, the size is always 4 words
	send_msg_4(targetcore, 5, 0, (int)ptr, reallock);
  }
}

// redirected lock request
bool getreadlock_I_r(void * ptr, void * redirectlock, int core, bool cache) {
  int targetcore = 0;
  
  if(core == corenum) {
	  lockobj = (int)ptr;
	  lock2require = (int)redirectlock;
	  lockflag = false;
#ifndef INTERRUPT
	  reside = false;
#endif
	  lockresult = 0;
  }  
  targetcore = ((int)redirectlock >> 5) % BAMBOO_TOTALCORE;
  
  if(targetcore == corenum) {
    // reside on this core
    int deny = processlockrequest(0, (int)redirectlock, (int)ptr, corenum, core, cache);
	if(deny == -1) {
		// redirected
		return true;
	} else {
		if(core == corenum) {
			if(lockobj == (int)ptr) {
				if(deny) {
					lockresult = 0;
				} else {
					lockresult = 1;
					RuntimeHashadd_I(objRedirectLockTbl, (int)ptr, (int)redirectlock);
				}
				lockflag = true;
#ifndef INTERRUPT
				reside = true;
#endif
			} else {
				// conflicts on lockresults
				BAMBOO_EXIT(0xa018);
			}
			return true;
		} else {
			// send lock grant/deny request to the root requiring core
			// check if there is still some msg on sending
			if((!cache) || (cache && !isMsgSending)) {
				send_msg_4(core, deny==1?0xa:9, 0, (int)ptr, (int)redirectlock);
			} else {
				cache_msg_4(core, deny==1?0xa:9, 0, (int)ptr, (int)redirectlock);
			}
		}
	}
  } else {
	// redirect the lock request
	// for 32 bit machine, the size is always 6 words
	if((!cache) || (cache && !isMsgSending)) {
		send_msg_6(targetcore, 8, 0, (int)ptr, lock2require, core, corenum);
	} else {
		cache_msg_6(targetcore, 8, 0, (int)ptr, lock2require, core, corenum);
	}
  }
  return true;
}

// not reentrant
bool getwritelock(void * ptr) {
  int targetcore = 0;

  // for 32 bit machine, the size is always 5 words
  //int msgsize = 5;

  lockobj = (int)ptr;
  if(((struct ___Object___ *)ptr)->lock == NULL) {
	lock2require = lockobj;
  } else {
	lock2require = (int)(((struct ___Object___ *)ptr)->lock);
  }
  targetcore = (lock2require >> 5) % BAMBOO_TOTALCORE;
  lockflag = false;
#ifndef INTERRUPT
  reside = false;
#endif
  lockresult = 0;

#ifdef DEBUG
  BAMBOO_DEBUGPRINT(0xe551);
  BAMBOO_DEBUGPRINT_REG(lockobj);
  BAMBOO_DEBUGPRINT_REG(lock2require);
  BAMBOO_DEBUGPRINT_REG(targetcore);
#endif

  if(targetcore == corenum) {
    // reside on this core
    int deny = 0;
	BAMBOO_START_CRITICAL_SECTION_LOCK();
#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xf001);
#endif
	deny = processlockrequest(1, lock2require, (int)ptr, corenum, corenum, false);
	BAMBOO_CLOSE_CRITICAL_SECTION_LOCK();
#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xf000);
#endif
#ifdef DEBUG
    BAMBOO_DEBUGPRINT(0xe555);
    BAMBOO_DEBUGPRINT_REG(lockresult);
#endif
    if(deny == -1) {
		// redirected
		return true;
	} else {
		if(lockobj == (int)ptr) {
			if(deny) {
				lockresult = 0;
			} else {
				lockresult = 1;
			}
			lockflag = true;
#ifndef INTERRUPT
			reside = true;
#endif
		} else {
			// conflicts on lockresults
			BAMBOO_EXIT(0xa019);
		}
	}
    return true;
  } else {
	  // send lock request msg
	  // for 32 bit machine, the size is always 5 words
	  send_msg_5(targetcore, 2, 1, (int)ptr, lock2require, corenum);
  }
  return true;
}

void releasewritelock(void * ptr) {
  int targetcore = 0;
  int reallock = 0;
  if(((struct ___Object___ *)ptr)->lock == NULL) {
	reallock = (int)ptr;
  } else {
	reallock = (int)(((struct ___Object___ *)ptr)->lock);
  }
  targetcore = (reallock >> 5) % BAMBOO_TOTALCORE;

#ifdef DEBUG
  BAMBOO_DEBUGPRINT(0xe661);
  BAMBOO_DEBUGPRINT_REG((int)ptr);
  BAMBOO_DEBUGPRINT_REG(reallock);
  BAMBOO_DEBUGPRINT_REG(targetcore);
#endif

  if(targetcore == corenum) {
	BAMBOO_START_CRITICAL_SECTION_LOCK();
#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xf001);
#endif
    // reside on this core
    if(!RuntimeHashcontainskey(locktbl, reallock)) {
      // no locks for this object, something is wrong
      BAMBOO_EXIT(0xa01a);
    } else {
      int rwlock_obj = 0;
	  struct LockValue * lockvalue = NULL;
      RuntimeHashget(locktbl, reallock, &rwlock_obj);
	  lockvalue = (struct LockValue *)rwlock_obj;
      lockvalue->value++;
    }
	BAMBOO_CLOSE_CRITICAL_SECTION_LOCK();
#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xf000);
#endif
    return;
  } else {
	// send lock release msg
	// for 32 bit machine, the size is always 4 words
	send_msg_4(targetcore, 5, 1, (int)ptr, reallock);
  }
}

void releasewritelock_r(void * lock, void * redirectlock) {
  int targetcore = 0;
  int reallock = (int)lock;
  targetcore = (reallock >> 5) % BAMBOO_TOTALCORE;

#ifdef DEBUG
  BAMBOO_DEBUGPRINT(0xe671);
  BAMBOO_DEBUGPRINT_REG((int)lock);
  BAMBOO_DEBUGPRINT_REG(reallock);
  BAMBOO_DEBUGPRINT_REG(targetcore);
#endif

  if(targetcore == corenum) {
	BAMBOO_START_CRITICAL_SECTION_LOCK();
#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xf001);
#endif
    // reside on this core
    if(!RuntimeHashcontainskey(locktbl, reallock)) {
      // no locks for this object, something is wrong
      BAMBOO_EXIT(0xa01b);
    } else {
      int rwlock_obj = 0;
	  struct LockValue * lockvalue = NULL;
#ifdef DEBUG
      BAMBOO_DEBUGPRINT(0xe672);
#endif
      RuntimeHashget(locktbl, reallock, &rwlock_obj);
	  lockvalue = (struct LockValue *)rwlock_obj;
#ifdef DEBUG
      BAMBOO_DEBUGPRINT_REG(lockvalue->value);
#endif
      lockvalue->value++;
	  lockvalue->redirectlock = (int)redirectlock;
#ifdef DEBUG
      BAMBOO_DEBUGPRINT_REG(lockvalue->value);
#endif
    }
	BAMBOO_CLOSE_CRITICAL_SECTION_LOCK();
#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xf000);
#endif
    return;
  } else {
	  // send lock release with redirect info msg
	  // for 32 bit machine, the size is always 4 words
	  send_msg_4(targetcore, 0xb, 1, (int)lock, (int)redirectlock);
  }
}

bool getwritelock_I(void * ptr) {
  int targetcore = 0;
  lockobj = (int)ptr;
  if(((struct ___Object___ *)ptr)->lock == NULL) {
	lock2require = lockobj;
  } else {
	lock2require = (int)(((struct ___Object___ *)ptr)->lock);
  }
  targetcore = (lock2require >> 5) % BAMBOO_TOTALCORE;
  lockflag = false;
#ifndef INTERRUPT
  reside = false;
#endif
  lockresult = 0;

#ifdef DEBUG
  BAMBOO_DEBUGPRINT(0xe561);
  BAMBOO_DEBUGPRINT_REG(lockobj);
  BAMBOO_DEBUGPRINT_REG(lock2require);
  BAMBOO_DEBUGPRINT_REG(targetcore);
#endif

  if(targetcore == corenum) {
    // reside on this core
	int deny = processlockrequest(1, (int)lock2require, (int)ptr, corenum, corenum, false);
	if(deny == -1) {
		// redirected
		return true;
	} else {
		if(lockobj == (int)ptr) {
			if(deny) {
				lockresult = 0;
#ifdef DEBUG
				BAMBOO_DEBUGPRINT(0);
#endif
			} else {
				lockresult = 1;
#ifdef DEBUG
				BAMBOO_DEBUGPRINT(1);
#endif
			}
			lockflag = true;
#ifndef INTERRUPT
			reside = true;
#endif
		} else {
			// conflicts on lockresults
			BAMBOO_EXIT(0xa01c);
		}
		return true;
	}
  } else {
	  // send lock request msg
	  // for 32 bit machine, the size is always 5 words
	  send_msg_5(targetcore, 2, 1, (int)ptr, lock2require, corenum);
  }
  return true;
}

// redirected lock request
bool getwritelock_I_r(void * ptr, void * redirectlock, int core, bool cache) {
  int targetcore = 0;

  if(core == corenum) {
	  lockobj = (int)ptr;
	  lock2require = (int)redirectlock;
	  lockflag = false;
#ifndef INTERRUPT
	  reside = false;
#endif
	  lockresult = 0;
  }
  targetcore = ((int)redirectlock >> 5) % BAMBOO_TOTALCORE;

#ifdef DEBUG
  BAMBOO_DEBUGPRINT(0xe571);
  BAMBOO_DEBUGPRINT_REG((int)ptr);
  BAMBOO_DEBUGPRINT_REG((int)redirectlock);
  BAMBOO_DEBUGPRINT_REG(core);
  BAMBOO_DEBUGPRINT_REG((int)cache);
  BAMBOO_DEBUGPRINT_REG(targetcore);
#endif


  if(targetcore == corenum) {
    // reside on this core
	int deny = processlockrequest(1, (int)redirectlock, (int)ptr, corenum, core, cache);
	if(deny == -1) {
		// redirected
		return true;
	} else {
		if(core == corenum) {
			if(lockobj == (int)ptr) {
				if(deny) {
					lockresult = 0;
				} else {
					lockresult = 1;
					RuntimeHashadd_I(objRedirectLockTbl, (int)ptr, (int)redirectlock);
				}
				lockflag = true;
#ifndef INTERRUPT
				reside = true;
#endif
			} else {
				// conflicts on lockresults
				BAMBOO_EXIT(0xa01d);
			}
			return true;
		} else {
			// send lock grant/deny request to the root requiring core
			// check if there is still some msg on sending
			if((!cache) || (cache && !isMsgSending)) {
				send_msg_4(core, deny==1?0xa:9, 1, (int)ptr, (int)redirectlock);
			} else {
				cache_msg_4(core, deny==1?0xa:9, 1, (int)ptr, (int)redirectlock);
			}
		}
	}
  } else {
	// redirect the lock request
	// for 32 bit machine, the size is always 6 words
	if((!cache) || (cache && !isMsgSending)) {
		send_msg_6(targetcore, 8, 1, (int)ptr, (int)redirectlock, core, corenum);
	} else {
		cache_msg_6(targetcore, 8, 1, (int)ptr, (int)redirectlock, core, corenum);
	}
  }
  return true;
}

void releasewritelock_I(void * ptr) {
  int targetcore = 0;
  int reallock = 0;
  if(((struct ___Object___ *)ptr)->lock == NULL) {
	reallock = (int)ptr;
  } else {
	reallock = (int)(((struct ___Object___ *)ptr)->lock);
  }
  targetcore = (reallock >> 5) % BAMBOO_TOTALCORE;

#ifdef DEBUG
  BAMBOO_DEBUGPRINT(0xe681);
  BAMBOO_DEBUGPRINT_REG((int)ptr);
  BAMBOO_DEBUGPRINT_REG(reallock);
  BAMBOO_DEBUGPRINT_REG(targetcore);
#endif

  if(targetcore == corenum) {
    // reside on this core
    if(!RuntimeHashcontainskey(locktbl, reallock)) {
      // no locks for this object, something is wrong
      BAMBOO_EXIT(0xa01e);
    } else {
      int rwlock_obj = 0;
	  struct LockValue * lockvalue = NULL;
      RuntimeHashget(locktbl, reallock, &rwlock_obj);
	  lockvalue = (struct LockValue *)rwlock_obj;
      lockvalue->value++;
    }
    return;
  } else {
	// send lock release msg
	// for 32 bit machine, the size is always 4 words
	send_msg_4(targetcore, 5, 1, (int)ptr, reallock);
  }
}

void releasewritelock_I_r(void * lock, void * redirectlock) {
  int targetcore = 0;
  int reallock = (int)lock;
  targetcore = (reallock >> 5) % BAMBOO_TOTALCORE;

#ifdef DEBUG
  BAMBOO_DEBUGPRINT(0xe691);
  BAMBOO_DEBUGPRINT_REG((int)lock);
  BAMBOO_DEBUGPRINT_REG(reallock);
  BAMBOO_DEBUGPRINT_REG(targetcore);
#endif

  if(targetcore == corenum) {
    // reside on this core
    if(!RuntimeHashcontainskey(locktbl, reallock)) {
      // no locks for this object, something is wrong
      BAMBOO_EXIT(0xa01f);
    } else {
      int rwlock_obj = 0;
	  struct LockValue * lockvalue = NULL;
#ifdef DEBUG
      BAMBOO_DEBUGPRINT(0xe692);
#endif
      RuntimeHashget(locktbl, reallock, &rwlock_obj);
	  lockvalue = (struct LockValue *)rwlock_obj;
#ifdef DEBUG
      BAMBOO_DEBUGPRINT_REG(lockvalue->value);
#endif
      lockvalue->value++;
	  lockvalue->redirectlock = (int)redirectlock;
#ifdef DEBUG
      BAMBOO_DEBUGPRINT_REG(lockvalue->value);
#endif
    }
    return;
  } else {
	// send lock release msg
	// for 32 bit machine, the size is always 4 words
	send_msg_4(targetcore, 0xb, 1, (int)lock, (int)redirectlock);
  }
}

int enqueuetasks(struct parameterwrapper *parameter, struct parameterwrapper *prevptr, struct ___Object___ *ptr, int * enterflags, int numenterflags) {
  void * taskpointerarray[MAXTASKPARAMS];
  int j;
  //int numparams=parameter->task->numParameters;
  int numiterators=parameter->task->numTotal-1;
  int retval=1;
  //int addnormal=1;
  //int adderror=1;

  struct taskdescriptor * task=parameter->task;

   //this add the object to parameterwrapper
   ObjectHashadd(parameter->objectset, (int) ptr, 0, (int) enterflags, numenterflags, enterflags==NULL);

  /* Add enqueued object to parameter vector */
  taskpointerarray[parameter->slot]=ptr;

  /* Reset iterators */
  for(j=0; j<numiterators; j++) {
    toiReset(&parameter->iterators[j]);
  }

  /* Find initial state */
  for(j=0; j<numiterators; j++) {
backtrackinit:
    if(toiHasNext(&parameter->iterators[j], taskpointerarray OPTARG(failed)))
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
    struct taskparamdescriptor *tpd=RUNMALLOC(sizeof(struct taskparamdescriptor));
    tpd->task=task;
    tpd->numParameters=numiterators+1;
    tpd->parameterArray=RUNMALLOC(sizeof(void *)*(numiterators+1));

    for(j=0; j<=numiterators; j++) {
      tpd->parameterArray[j]=taskpointerarray[j]; //store the actual parameters
    }
    /* Enqueue task */
    if ((/*!gencontains(failedtasks, tpd)&&*/ !gencontains(activetasks,tpd))) {
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

int enqueuetasks_I(struct parameterwrapper *parameter, struct parameterwrapper *prevptr, struct ___Object___ *ptr, int * enterflags, int numenterflags) {
  void * taskpointerarray[MAXTASKPARAMS];
  int j;
  //int numparams=parameter->task->numParameters;
  int numiterators=parameter->task->numTotal-1;
  int retval=1;
  //int addnormal=1;
  //int adderror=1;

  struct taskdescriptor * task=parameter->task;

   //this add the object to parameterwrapper
   ObjectHashadd_I(parameter->objectset, (int) ptr, 0, (int) enterflags, numenterflags, enterflags==NULL);  

  /* Add enqueued object to parameter vector */
  taskpointerarray[parameter->slot]=ptr;

  /* Reset iterators */
  for(j=0; j<numiterators; j++) {
    toiReset(&parameter->iterators[j]);
  }

  /* Find initial state */
  for(j=0; j<numiterators; j++) {
backtrackinit:
    if(toiHasNext(&parameter->iterators[j], taskpointerarray OPTARG(failed)))
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
    struct taskparamdescriptor *tpd=RUNMALLOC_I(sizeof(struct taskparamdescriptor));
    tpd->task=task;
    tpd->numParameters=numiterators+1;
    tpd->parameterArray=RUNMALLOC_I(sizeof(void *)*(numiterators+1));

    for(j=0; j<=numiterators; j++) {
      tpd->parameterArray[j]=taskpointerarray[j]; //store the actual parameters
    }
    /* Enqueue task */
    if ((/*!gencontains(failedtasks, tpd)&&*/ !gencontains(activetasks,tpd))) {
		genputtable_I(activetasks, tpd, tpd);
    } else {
      RUNFREE(tpd->parameterArray);
      RUNFREE(tpd);
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

/* Handler for signals. The signals catch null pointer errors and
   arithmatic errors. */
#ifndef MULTICORE
void myhandler(int sig, siginfo_t *info, void *uap) {
  sigset_t toclear;
#ifdef DEBUG
  printf("sig=%d\n",sig);
  printf("signal\n");
#endif
  sigemptyset(&toclear);
  sigaddset(&toclear, sig);
  sigprocmask(SIG_UNBLOCK, &toclear,NULL);
  longjmp(error_handler,1);
}
#endif

#ifndef MULTICORE
fd_set readfds;
int maxreadfd;
struct RuntimeHash *fdtoobject;

void addreadfd(int fd) {
  if (fd>=maxreadfd)
    maxreadfd=fd+1;
  FD_SET(fd, &readfds);
}

void removereadfd(int fd) {
  FD_CLR(fd, &readfds);
  if (maxreadfd==(fd+1)) {
    maxreadfd--;
    while(maxreadfd>0&&!FD_ISSET(maxreadfd-1, &readfds))
      maxreadfd--;
  }
}
#endif

#ifdef PRECISE_GC
#define OFFSET 2
#else
#define OFFSET 0
#endif

int containstag(struct ___Object___ *ptr, struct ___TagDescriptor___ *tag);

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

  struct LockValue locks[MAXTASKPARAMS];
  int locklen = 0;
  int grount = 0;
  int andmask=0;
  int checkmask=0;

#if 0
  /* Set up signal handlers */
  struct sigaction sig;
  sig.sa_sigaction=&myhandler;
  sig.sa_flags=SA_SIGINFO;
  sigemptyset(&sig.sa_mask);

  /* Catch bus errors, segmentation faults, and floating point exceptions*/
  sigaction(SIGBUS,&sig,0);
  sigaction(SIGSEGV,&sig,0);
  sigaction(SIGFPE,&sig,0);
  sigaction(SIGPIPE,&sig,0);
#endif  // #if 0: non-multicore

#if 0
  /* Zero fd set */
  FD_ZERO(&readfds);
#endif
#ifndef MULTICORE
  maxreadfd=0;
#endif
#if 0
  fdtoobject=allocateRuntimeHash(100);
#endif

#if 0
  /* Map first block of memory to protected, anonymous page */
  mmap(0, 0x1000, 0, MAP_SHARED|MAP_FIXED|MAP_ANON, -1, 0);
#endif

newtask:
#ifdef MULTICORE
  while(hashsize(activetasks)>0) {
#else
  while((hashsize(activetasks)>0)||(maxreadfd>0)) {
#endif
#ifdef DEBUG
    BAMBOO_DEBUGPRINT(0xe990);
#endif
#if 0
    /* Check if any filedescriptors have IO pending */
    if (maxreadfd>0) {
      int i;
      struct timeval timeout={0,0};
      fd_set tmpreadfds;
      int numselect;
      tmpreadfds=readfds;
      numselect=select(maxreadfd, &tmpreadfds, NULL, NULL, &timeout);
      if (numselect>0) {
	/* Process ready fd's */
	int fd;
	for(fd=0; fd<maxreadfd; fd++) {
	  if (FD_ISSET(fd, &tmpreadfds)) {
	    /* Set ready flag on object */
	    void * objptr;
	    //	    printf("Setting fd %d\n",fd);
	    if (RuntimeHashget(fdtoobject, fd,(int *) &objptr)) {
	      if(intflagorand(objptr,1,0xFFFFFFFF)) { /* Set the first flag to 1 */
		enqueueObject(objptr, NULL, 0);
	      }
	    }
	  }
	}
      }
    }
#endif

    /* See if there are any active tasks */
    if (hashsize(activetasks)>0) {
      int i;
#ifdef PROFILE
	  profileTaskStart("tpd checking");
#endif
	  busystatus = true;
      currtpd=(struct taskparamdescriptor *) getfirstkey(activetasks);
      genfreekey(activetasks, currtpd);

      numparams=currtpd->task->numParameters;
      numtotal=currtpd->task->numTotal;

	  // clear the lockRedirectTbl (TODO, this table should be empty after all locks are released)
	  // reset all locks
	  for(j = 0; j < MAXTASKPARAMS; j++) {
		  locks[j].redirectlock = 0;
		  locks[j].value = 0;
	  }
	  // get all required locks
	  locklen = 0;
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
		  if(((struct ___Object___ *)param)->lock == NULL) {
			  tmplock = (int)param;
		  } else {
			  tmplock = (int)(((struct ___Object___ *)param)->lock);
		  }
		  // insert into the locks array
		  for(j = 0; j < locklen; j++) {
			  if(locks[j].value == tmplock) {
				  insert = false;
				  break;
			  } else if(locks[j].value > tmplock) {
				  break;
			  }
		  }
		  if(insert) {
			  int h = locklen;
			  for(; h > j; h--) {
				  locks[h].redirectlock = locks[h-1].redirectlock;
				  locks[h].value = locks[h-1].value;
			  }
			  locks[j].value = tmplock;
			  locks[j].redirectlock = (int)param;
			  locklen++;
		  }		  
	  } // line 2713: for(i = 0; i < numparams; i++) 
	  // grab these required locks
#ifdef DEBUG
	  BAMBOO_DEBUGPRINT(0xe991);
#endif
	  for(i = 0; i < locklen; i++) {
		  int * lock = (int *)(locks[i].redirectlock);
		  islock = true;
		  // require locks for this parameter if it is not a startup object
#ifdef DEBUG
		  BAMBOO_DEBUGPRINT_REG((int)lock);
		  BAMBOO_DEBUGPRINT_REG((int)(locks[i].value));
#endif
		  getwritelock(lock);
		  BAMBOO_START_CRITICAL_SECTION();
#ifdef DEBUG
		  BAMBOO_DEBUGPRINT(0xf001);
#endif
#ifdef PROFILE
		  //isInterrupt = false;
#endif 
		  while(!lockflag) { 
			  BAMBOO_WAITING_FOR_LOCK();
		  }
#ifndef INTERRUPT
		  if(reside) {
			  while(BAMBOO_WAITING_FOR_LOCK() != -1) {
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
#ifdef PROFILE
		  //isInterrupt = true;
#endif
		  BAMBOO_CLOSE_CRITICAL_SECTION();
#ifdef DEBUG
		  BAMBOO_DEBUGPRINT(0xf000);
#endif

		  if(grount == 0) {
			  int j = 0;
#ifdef DEBUG
			  BAMBOO_DEBUGPRINT(0xe992);
#endif
			  // can not get the lock, try later
			  // releas all grabbed locks for previous parameters
			  for(j = 0; j < i; ++j) {
				  lock = (int*)(locks[j].redirectlock);
				  releasewritelock(lock);
			  }
			  genputtable(activetasks, currtpd, currtpd);
			  if(hashsize(activetasks) == 1) {
				  // only one task right now, wait a little while before next try
				  int halt = 10000;
				  while(halt--) {
				  }
			  }
#ifdef PROFILE
			  // fail, set the end of the checkTaskInfo
			  profileTaskEnd();
#endif
			  goto newtask;
		  } // line 2794: if(grount == 0)
	  } // line 2752:  for(i = 0; i < locklen; i++)

#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xe993);
#endif
      /* Make sure that the parameters are still in the queues */
      for(i=0; i<numparams; i++) {
	void * parameter=currtpd->parameterArray[i];

	// flush the object
#ifdef CACHEFLUSH
	BAMBOO_CACHE_FLUSH_RANGE((int)parameter, classsize[((struct ___Object___ *)parameter)->type]);
	/*
	BAMBOO_START_CRITICAL_SECTION_LOCK();
#ifdef DEBUG
    BAMBOO_DEBUGPRINT(0xf001);
#endif
	if(RuntimeHashcontainskey(objRedirectLockTbl, (int)parameter)) {
		int redirectlock_r = 0;
		RuntimeHashget(objRedirectLockTbl, (int)parameter, &redirectlock_r);
		((struct ___Object___ *)parameter)->lock = redirectlock_r;
		RuntimeHashremovekey(objRedirectLockTbl, (int)parameter);
	}
	BAMBOO_CLOSE_CRITICAL_SECTION_LOCK();
#ifdef DEBUG
    BAMBOO_DEBUGPRINT(0xf000);
#endif
*/
#endif
	tmpparam = (struct ___Object___ *)parameter;
	pd=currtpd->task->descriptorarray[i];
	pw=(struct parameterwrapper *) pd->queue;
	/* Check that object is still in queue */
	{
	  if (!ObjectHashcontainskey(pw->objectset, (int) parameter)) {
#ifdef DEBUG
	    BAMBOO_DEBUGPRINT(0xe994);
#endif
	    // release grabbed locks
	    for(j = 0; j < locklen; ++j) {
		int * lock = (int *)(locks[j].redirectlock);
		releasewritelock(lock);
	    }
	    RUNFREE(currtpd->parameterArray);
	    RUNFREE(currtpd);
	    goto newtask;
	  }
	} // line2865
	/* Check if the object's flags still meets requirements */
	{
	  int tmpi = 0;
	  bool ismet = false;
	  for(tmpi = 0; tmpi < pw->numberofterms; ++tmpi) {
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
#ifdef DEBUG
	    BAMBOO_DEBUGPRINT(0xe995);
#endif
	    ObjectHashget(pw->objectset, (int) parameter, (int *) &next, (int *) &enterflags, &UNUSED, &UNUSED2);
	    ObjectHashremove(pw->objectset, (int)parameter);
	    if (enterflags!=NULL)
	      RUNFREE(enterflags);
	    // release grabbed locks
	    for(j = 0; j < locklen; ++j) {
		 int * lock = (int *)(locks[j].redirectlock);
		releasewritelock(lock);
	    }
	    RUNFREE(currtpd->parameterArray);
	    RUNFREE(currtpd);
#ifdef PROFILE
	    // fail, set the end of the checkTaskInfo
		profileTaskEnd();
#endif
	    goto newtask;
	  } // line 2878: if (!ismet)
	} // line 2867
parameterpresent:
	;
	/* Check that object still has necessary tags */
	for(j=0; j<pd->numbertags; j++) {
	  int slotid=pd->tagarray[2*j]+numparams;
	  struct ___TagDescriptor___ *tagd=currtpd->parameterArray[slotid];
	  if (!containstag(parameter, tagd)) {
#ifdef DEBUG
	    BAMBOO_DEBUGPRINT(0xe996);
#endif
		{
		// release grabbed locks
		int tmpj = 0;
	    for(tmpj = 0; tmpj < locklen; ++tmpj) {
		 int * lock = (int *)(locks[tmpj].redirectlock);
		releasewritelock(lock);
	    }
		}
	    RUNFREE(currtpd->parameterArray);
	    RUNFREE(currtpd);
	    goto newtask;
	  } // line2911: if (!containstag(parameter, tagd))
	} // line 2808: for(j=0; j<pd->numbertags; j++)

	taskpointerarray[i+OFFSET]=parameter;
      } // line 2824: for(i=0; i<numparams; i++)
      /* Copy the tags */
      for(; i<numtotal; i++) {
	taskpointerarray[i+OFFSET]=currtpd->parameterArray[i];
      }

      {
#if 0
#ifndef RAW
	/* Checkpoint the state */
	forward=allocateRuntimeHash(100);
	reverse=allocateRuntimeHash(100);
	//void ** checkpoint=makecheckpoint(currtpd->task->numParameters, currtpd->parameterArray, forward, reverse);
#endif
#endif  // #if 0: for recovery
#ifndef MULTICORE
	if (x=setjmp(error_handler)) {
	  //int counter;
	  /* Recover */
#ifdef DEBUG
#ifndef MULTICORE
	  printf("Fatal Error=%d, Recovering!\n",x);
#endif
#endif
#if 0
	     genputtable(failedtasks,currtpd,currtpd);
	     //restorecheckpoint(currtpd->task->numParameters, currtpd->parameterArray, checkpoint, forward, reverse);

	     freeRuntimeHash(forward);
	     freeRuntimeHash(reverse);
	     freemalloc();
	     forward=NULL;
	     reverse=NULL;
#endif  // #if 0: for recovery
	  BAMBOO_DEBUGPRINT_REG(x);
	  BAMBOO_EXIT(0xa020);
	} else {
#endif // #ifndef MULTICORE
#if 0 
		if (injectfailures) {
	     if ((((double)random())/RAND_MAX)<failurechance) {
	      printf("\nINJECTING TASK FAILURE to %s\n", currtpd->task->name);
	      longjmp(error_handler,10);
	     }
	     }
#endif  // #if 0: for recovery
	  /* Actually call task */
#if 0
#ifdef PRECISE_GC
	  ((int *)taskpointerarray)[0]=currtpd->numParameters;
	  taskpointerarray[1]=NULL;
#endif
#endif  // #if 0: for garbage collection
execute:
#ifdef PROFILE
	  // check finish, set the end of the checkTaskInfo
	  profileTaskEnd();
	  profileTaskStart(currtpd->task->name);
#endif

	  if(debugtask) {
#ifndef MULTICORE
        printf("ENTER %s count=%d\n",currtpd->task->name, (instaccum-instructioncount));
#endif
	    ((void(*) (void **))currtpd->task->taskptr)(taskpointerarray);
#ifndef MULTICORE
	    printf("EXIT %s count=%d\n",currtpd->task->name, (instaccum-instructioncount));
#endif
	  } else {
#ifdef DEBUG
		  BAMBOO_DEBUGPRINT(0xe997);
#endif
	    ((void(*) (void **))currtpd->task->taskptr)(taskpointerarray);
	  } // line 2990: if(debugtask)
#ifdef PROFILE
	  // task finish, set the end of the checkTaskInfo
	  profileTaskEnd();
	  // new a PostTaskInfo for the post-task execution
	  profileTaskStart("post task execution");
#endif
#ifdef DEBUG
	  BAMBOO_DEBUGPRINT(0xe998);
	  BAMBOO_DEBUGPRINT_REG(islock);
#endif

	  if(islock) {
#ifdef DEBUG
		  BAMBOO_DEBUGPRINT(0xe999);
#endif
	    for(i = 0; i < locklen; ++i) {
		  void * ptr = (void *)(locks[i].redirectlock);
	      int * lock = (int *)(locks[i].value);
#ifdef DEBUG
		  BAMBOO_DEBUGPRINT_REG((int)ptr);
		  BAMBOO_DEBUGPRINT_REG((int)lock);
#endif
		  if(RuntimeHashcontainskey(lockRedirectTbl, (int)lock)) {
			  int redirectlock;
			  RuntimeHashget(lockRedirectTbl, (int)lock, &redirectlock);
			  RuntimeHashremovekey(lockRedirectTbl, (int)lock);
			  releasewritelock_r(lock, (int *)redirectlock);
		  } else {
		releasewritelock(ptr);
		  }
	    }
	  } // line 3015: if(islock)

#ifdef PROFILE
	  // post task execution finish, set the end of the postTaskInfo
	  profileTaskEnd();
#endif

#if 0
	  freeRuntimeHash(forward);
	  freeRuntimeHash(reverse);
	  freemalloc();
#endif
	  // Free up task parameter descriptor
	  RUNFREE(currtpd->parameterArray);
	  RUNFREE(currtpd);
#if 0
	  forward=NULL;
	  reverse=NULL;
#endif
#ifdef DEBUG
	  BAMBOO_DEBUGPRINT(0xe99a);
#endif
#ifndef MULTICORE
	} // line 2946: if (x=setjmp(error_handler))
#endif
      } // line2936: 
    } // line 2697: if (hashsize(activetasks)>0)  
  } // line 2659: while(hashsize(activetasks)>0)
#ifdef DEBUG
  BAMBOO_DEBUGPRINT(0xe99b);
#endif
}

/* This function processes an objects tags */
void processtags(struct parameterdescriptor *pd, int index, struct parameterwrapper *parameter, int * iteratorcount, int *statusarray, int numparams) {
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


void processobject(struct parameterwrapper *parameter, int index, struct parameterdescriptor *pd, int *iteratorcount, int * statusarray, int numparams) {
  int i;
  int tagcount=0;
  struct ObjectHash * objectset=((struct parameterwrapper *)pd->queue)->objectset;

  parameter->iterators[*iteratorcount].istag=0;
  parameter->iterators[*iteratorcount].slot=index;
  parameter->iterators[*iteratorcount].objectset=objectset;
  statusarray[index]=1;

  for(i=0; i<pd->numbertags; i++) {
    int slotid=pd->tagarray[2*i];
    //int tagid=pd->tagarray[2*i+1];
    if (statusarray[slotid+numparams]!=0) {
      /* This tag has already been enqueued, use it to narrow search */
      parameter->iterators[*iteratorcount].tagbindings[tagcount]=slotid+numparams;
      tagcount++;
    }
  }
  parameter->iterators[*iteratorcount].numtags=tagcount;

  (*iteratorcount)++;
}

/* This function builds the iterators for a task & parameter */

void builditerators(struct taskdescriptor * task, int index, struct parameterwrapper * parameter) {
  int statusarray[MAXTASKPARAMS];
  int i;
  int numparams=task->numParameters;
  int iteratorcount=0;
  for(i=0; i<MAXTASKPARAMS; i++) statusarray[i]=0;

  statusarray[index]=1; /* Initial parameter */
  /* Process tags for initial iterator */

  processtags(task->descriptorarray[index], index, parameter, &iteratorcount, statusarray, numparams);

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
	    processobject(parameter, i, pd, &iteratorcount, statusarray, numparams);
	    processtags(pd, i, parameter, &iteratorcount, statusarray, numparams);
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
	  processobject(parameter, i, pd, &iteratorcount, statusarray, numparams);
	  processtags(pd, i, parameter, &iteratorcount, statusarray, numparams);
	  goto loopstart;
	}
      }
    }

    /* Nothing with a tag enqueued */

    for(i=0; i<numparams; i++) {
      if (statusarray[i]==0) {
	struct parameterdescriptor *pd=task->descriptorarray[i];
	processobject(parameter, i, pd, &iteratorcount, statusarray, numparams);
	processtags(pd, i, parameter, &iteratorcount, statusarray, numparams);
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
  if(corenum > NUMCORES - 1) {
    return;
  }
  for(i=0; i<numtasks[corenum]; i++) {
    struct taskdescriptor * task=taskarray[BAMBOO_NUM_OF_CORE][i];
#ifndef MULTICORE
	printf("%s\n", task->name);
#endif
    for(j=0; j<task->numParameters; j++) {
      struct parameterdescriptor *param=task->descriptorarray[j];
      struct parameterwrapper *parameter=param->queue;
      struct ObjectHash * set=parameter->objectset;
      struct ObjectIterator objit;
#ifndef MULTICORE
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
#ifndef MULTICORE
	printf("    Contains %lx\n", obj);
	printf("      flag=%d\n", obj->flag);
#endif
	if (tagptr==NULL) {
	} else if (tagptr->type==TAGTYPE) {
#ifndef MULTICORE
	  printf("      tag=%lx\n",tagptr);
#else
	  ;
#endif
	} else {
	  int tagindex=0;
	  struct ArrayObject *ao=(struct ArrayObject *)tagptr;
	  for(; tagindex<ao->___cachedCode___; tagindex++) {
#ifndef MULTICORE
		  printf("      tag=%lx\n",ARRAYGET(ao, struct ___TagDescriptor___*, tagindex));
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
  if(corenum > NUMCORES - 1) {
    return;
  }
  for(i=0; i<numtasks[corenum]; i++) {
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

int toiHasNext(struct tagobjectiterator *it, void ** objectarray OPTARG(int * failed)) {
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
	struct ___TagDescriptor___ *td=ARRAYGET(ao, struct ___TagDescriptor___ *, tagindex);
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
      for(tagindex=it->tagobjindex; tagindex<ao->___cachedCode___; tagindex++) {
	struct ___Object___ *objptr=ARRAYGET(ao, struct ___Object___*, tagindex);
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

int containstag(struct ___Object___ *ptr, struct ___TagDescriptor___ *tag) {
  int j;
  struct ___Object___ * objptr=tag->flagptr;
  if (objptr->type==OBJECTARRAYTYPE) {
    struct ArrayObject *ao=(struct ArrayObject *)objptr;
    for(j=0; j<ao->___cachedCode___; j++) {
      if (ptr==ARRAYGET(ao, struct ___Object___*, j))
	return 1;
    }
    return 0;
  } else
    return objptr==ptr;
}

void toiNext(struct tagobjectiterator *it, void ** objectarray OPTARG(int * failed)) {
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
      objectarray[it->slot]=ARRAYGET(ao, struct ___TagDescriptor___ *, it->tagobjindex++);
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
      objectarray[it->slot]=ARRAYGET(ao, struct ___Object___ *, it->tagobjindex++);
    }
  } else {
    /* Iterate object */
    objectarray[it->slot]=(void *)Objkey(&it->it);
    Objnext(&it->it);
  }
}
#endif
