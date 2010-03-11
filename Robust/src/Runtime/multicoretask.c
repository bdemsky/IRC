#ifdef TASK
#include "runtime.h"
#include "multicoreruntime.h"
#include "runtime_arch.h"
#include "GenericHashtable.h"

#ifndef INLINE
#define INLINE    inline __attribute__((always_inline))
#endif // #ifndef INLINE

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

#ifdef MULTICORE_GC
inline __attribute__((always_inline)) 
void setupsmemmode(void) {
#ifdef SMEML
	bamboo_smem_mode = SMEMLOCAL;
#elif defined SMEMF
	bamboo_smem_mode = SMEMFIXED;
#elif defined SMEMM
	bamboo_smem_mode = SMEMMIXED;
#elif defined SMEMG
	bamboo_smem_mode = SMEMGLOBAL;
#else
	// defaultly using local mode
	//bamboo_smem_mode = SMEMLOCAL;
	bamboo_smem_mode = SMEMGLOBAL;
#endif
} // void setupsmemmode(void)
#endif

inline __attribute__((always_inline)) 
void initruntimedata() {
	int i;
	// initialize the arrays
  if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
    // startup core to initialize corestatus[]
    for(i = 0; i < NUMCORESACTIVE; ++i) {
      corestatus[i] = 1;
      numsendobjs[i] = 0; 
      numreceiveobjs[i] = 0;
#ifdef PROFILE
			// initialize the profile data arrays
			profilestatus[i] = 1;
#endif
#ifdef MULTICORE_GC
			gccorestatus[i] = 1;
			gcnumsendobjs[i] = 0; 
      gcnumreceiveobjs[i] = 0;
#endif
    } // for(i = 0; i < NUMCORESACTIVE; ++i)
#ifdef MULTICORE_GC
		for(i = 0; i < NUMCORES4GC; ++i) {
			gcloads[i] = 0;
			gcrequiredmems[i] = 0;
			gcstopblock[i] = 0;
			gcfilledblocks[i] = 0;
    } // for(i = 0; i < NUMCORES4GC; ++i)
#ifdef GC_PROFILE
		gc_infoIndex = 0;
		gc_infoOverflow = false;
#endif
#endif
		numconfirm = 0;
		waitconfirm = false; 
		
		// TODO for test
		total_num_t6 = 0;
  }

  busystatus = true;
  self_numsendobjs = 0;
  self_numreceiveobjs = 0;

  for(i = 0; i < BAMBOO_MSG_BUF_LENGTH; ++i) {
    msgdata[i] = -1;
  }
  msgdataindex = 0;
	msgdatalast = 0;
  msglength = BAMBOO_MSG_BUF_LENGTH;
	msgdatafull = false;
  for(i = 0; i < BAMBOO_OUT_BUF_LENGTH; ++i) {
    outmsgdata[i] = -1;
  }
  outmsgindex = 0;
  outmsglast = 0;
  outmsgleft = 0;
  isMsgHanging = false;
  isMsgSending = false;

  smemflag = true;
  bamboo_cur_msp = NULL;
  bamboo_smem_size = 0;
	totransobjqueue = createQueue();

#ifdef MULTICORE_GC
	gcflag = false;
	gcprocessing = false;
	gcphase = FINISHPHASE;
	gccurr_heaptop = 0;
	gcself_numsendobjs = 0;
	gcself_numreceiveobjs = 0;
	gcmarkedptrbound = 0;
	//mgchashCreate(2000, 0.75);
	gcpointertbl = allocateRuntimeHash(20);
	//gcpointertbl = allocateMGCHash(20);
	gcforwardobjtbl = allocateMGCHash(20, 3);
	gcobj2map = 0;
	gcmappedobj = 0;
	gcismapped = false;
	gcnumlobjs = 0;
	gcheaptop = 0;
	gctopcore = 0;
	gctopblock = 0;
	gcmovestartaddr = 0;
	gctomove = false;
	gcmovepending = 0;
	gcblock2fill = 0;
	gcsbstarttbl = BAMBOO_BASE_VA;
	bamboo_smemtbl = (void *)gcsbstarttbl
		+ (BAMBOO_SHARED_MEM_SIZE/BAMBOO_SMEM_SIZE)*sizeof(INTPTR); 
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
	lockRedirectTbl = allocateRuntimeHash(20);
  objRedirectLockTbl = allocateRuntimeHash(20);
#endif
#ifndef INTERRUPT
  reside = false;
#endif  
  objqueue.head = NULL;
  objqueue.tail = NULL;

	currtpd = NULL;

#ifdef PROFILE
  stall = false;
  //isInterrupt = true;
  totalexetime = -1;
  taskInfoIndex = 0;
  taskInfoOverflow = false;
  /*interruptInfoIndex = 0;
  interruptInfoOverflow = false;*/
#endif

	for(i = 0; i < MAXTASKPARAMS; i++) {
		runtime_locks[i].redirectlock = 0;
		runtime_locks[i].value = 0;
	}
	runtime_locklen = 0;
}

inline __attribute__((always_inline))
void disruntimedata() {
#ifdef MULTICORE_GC
	//mgchashDelete();
	freeRuntimeHash(gcpointertbl);
	//freeMGCHash(gcpointertbl);
	freeMGCHash(gcforwardobjtbl);
#else
	freeRuntimeHash(lockRedirectTbl);
	freeRuntimeHash(objRedirectLockTbl);
	RUNFREE(locktable.bucket);
#endif
	if(activetasks != NULL) {
		genfreehashtable(activetasks);
	}
	if(currtpd != NULL) {
		RUNFREE(currtpd->parameterArray);
		RUNFREE(currtpd);
		currtpd = NULL;
	}
}

inline __attribute__((always_inline))
bool checkObjQueue() {
	bool rflag = false;
	struct transObjInfo * objInfo = NULL;
	int grount = 0;

#ifdef PROFILE
#ifdef ACCURATEPROFILE
	bool isChecking = false;
	if(!isEmpty(&objqueue)) {
		profileTaskStart("objqueue checking");
		isChecking = true;
	} // if(!isEmpty(&objqueue))
#endif
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
		rflag = true;
		objInfo = (struct transObjInfo *)getItem(&objqueue); 
		obj = objInfo->objptr;
#ifdef DEBUG
		BAMBOO_DEBUGPRINT_REG((int)obj);
#endif
		// grab lock and flush the obj
		grount = 0;
		getwritelock_I(obj);
		while(!lockflag) {
			BAMBOO_WAITING_FOR_LOCK();
		} // while(!lockflag)
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
			BAMBOO_CACHE_FLUSH_RANGE((int)obj,sizeof(int));
			BAMBOO_CACHE_FLUSH_RANGE((int)obj, 
					classsize[((struct ___Object___ *)obj)->type]);
#endif
			// enqueue the object
			for(k = 0; k < objInfo->length; ++k) {
				int taskindex = objInfo->queues[2 * k];
				int paramindex = objInfo->queues[2 * k + 1];
				struct parameterwrapper ** queues = 
					&(paramqueues[BAMBOO_NUM_OF_CORE][taskindex][paramindex]);
#ifdef DEBUG
				BAMBOO_DEBUGPRINT_REG(taskindex);
				BAMBOO_DEBUGPRINT_REG(paramindex);
				struct ___Object___ * tmpptr = (struct ___Object___ *)obj;
				tprintf("Process %x(%d): receive obj %x(%lld), ptrflag %x\n", 
								BAMBOO_NUM_OF_CORE, BAMBOO_NUM_OF_CORE, (int)obj, 
								(long)obj, tmpptr->flag);
#endif
				enqueueObject_I(obj, queues, 1);
#ifdef DEBUG				 
				BAMBOO_DEBUGPRINT_REG(hashsize(activetasks));
#endif
			} // for(k = 0; k < objInfo->length; ++k)
			releasewritelock_I(obj);
			RUNFREE(objInfo->queues);
			RUNFREE(objInfo);
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
					RUNFREE(objInfo->queues);
					RUNFREE(objInfo);
					goto objqueuebreak;
				} else {
					prev = qitem;
				} // if(tmpinfo->objptr == obj)
				qitem = getNextQueueItem(prev);
			} // while(qitem != NULL)
			// try to execute active tasks already enqueued first
			addNewItem_I(&objqueue, objInfo);
#ifdef PROFILE
			//isInterrupt = true;
#endif
objqueuebreak:
			BAMBOO_CLOSE_CRITICAL_SECTION_OBJ_QUEUE();
#ifdef DEBUG
			BAMBOO_DEBUGPRINT(0xf000);
#endif
			break;
		} // if(grount == 1)
		BAMBOO_CLOSE_CRITICAL_SECTION_OBJ_QUEUE();
#ifdef DEBUG
		BAMBOO_DEBUGPRINT(0xf000);
#endif
	} // while(!isEmpty(&objqueue))

#ifdef PROFILE
#ifdef ACCURATEPROFILE
	if(isChecking) {
		profileTaskEnd();
	} // if(isChecking)
#endif
#endif

#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xee02);
#endif
	return rflag;
}

inline __attribute__((always_inline))
void checkCoreStatus() {
	bool allStall = false;
	int i = 0;
	int sumsendobj = 0;
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
		corestatus[BAMBOO_NUM_OF_CORE] = 0;
		numsendobjs[BAMBOO_NUM_OF_CORE] = self_numsendobjs;
		numreceiveobjs[BAMBOO_NUM_OF_CORE] = self_numreceiveobjs;
		// check the status of all cores
		allStall = true;
#ifdef DEBUG
		BAMBOO_DEBUGPRINT_REG(NUMCORESACTIVE);
#endif
		for(i = 0; i < NUMCORESACTIVE; ++i) {
#ifdef DEBUG
			BAMBOO_DEBUGPRINT(0xe000 + corestatus[i]);
#endif
			if(corestatus[i] != 0) {
				allStall = false;
				break;
			}
		} // for(i = 0; i < NUMCORESACTIVE; ++i)
		if(allStall) {
			// check if the sum of send objs and receive obj are the same
			// yes->check if the info is the latest; no->go on executing
			sumsendobj = 0;
			for(i = 0; i < NUMCORESACTIVE; ++i) {
				sumsendobj += numsendobjs[i];
#ifdef DEBUG
				BAMBOO_DEBUGPRINT(0xf000 + numsendobjs[i]);
#endif
			} // for(i = 0; i < NUMCORESACTIVE; ++i)	
			for(i = 0; i < NUMCORESACTIVE; ++i) {
				sumsendobj -= numreceiveobjs[i];
#ifdef DEBUG
				BAMBOO_DEBUGPRINT(0xf000 + numreceiveobjs[i]);
#endif
			} // for(i = 0; i < NUMCORESACTIVE; ++i)
			if(0 == sumsendobj) {
				if(!waitconfirm) {
					// the first time found all cores stall
					// send out status confirm msg to all other cores
					// reset the corestatus array too
#ifdef DEBUG
					BAMBOO_DEBUGPRINT(0xee05);
#endif
					corestatus[BAMBOO_NUM_OF_CORE] = 1;
					for(i = 1; i < NUMCORESACTIVE; ++i) {	
						corestatus[i] = 1;
						// send status confirm msg to core i
						send_msg_1(i, STATUSCONFIRM, false);
					} // for(i = 1; i < NUMCORESACTIVE; ++i)
					waitconfirm = true;
					numconfirm = NUMCORESACTIVE - 1;
				} else {
					// all the core status info are the latest
					// terminate; for profiling mode, send request to all
					// other cores to pour out profiling data
#ifdef DEBUG
					BAMBOO_DEBUGPRINT(0xee06);
#endif						  
			 
#ifdef USEIO
					totalexetime = BAMBOO_GET_EXE_TIME() - bamboo_start_time;
#else

					BAMBOO_DEBUGPRINT(BAMBOO_GET_EXE_TIME() - bamboo_start_time);
					BAMBOO_DEBUGPRINT_REG(total_num_t6); // TODO for test
					BAMBOO_DEBUGPRINT(0xbbbbbbbb);
#endif
					// profile mode, send msgs to other cores to request pouring
					// out progiling data
#ifdef PROFILE
					BAMBOO_CLOSE_CRITICAL_SECTION_STATUS();
#ifdef DEBUG
					BAMBOO_DEBUGPRINT(0xf000);
#endif
					for(i = 1; i < NUMCORESACTIVE; ++i) {
						// send profile request msg to core i
						send_msg_2(i, PROFILEOUTPUT, totalexetime, false);
					} // for(i = 1; i < NUMCORESACTIVE; ++i)
					// pour profiling data on startup core
					outputProfileData();
					while(true) {
						BAMBOO_START_CRITICAL_SECTION_STATUS();
#ifdef DEBUG
						BAMBOO_DEBUGPRINT(0xf001);
#endif
						profilestatus[BAMBOO_NUM_OF_CORE] = 0;
						// check the status of all cores
						allStall = true;
#ifdef DEBUG
						BAMBOO_DEBUGPRINT_REG(NUMCORESACTIVE);
#endif	
						for(i = 0; i < NUMCORESACTIVE; ++i) {
#ifdef DEBUG
							BAMBOO_DEBUGPRINT(0xe000 + profilestatus[i]);
#endif
							if(profilestatus[i] != 0) {
								allStall = false;
								break;
							}
						}  // for(i = 0; i < NUMCORESACTIVE; ++i)
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
						} // if(!allStall)
					} // while(true)
#endif

					// gc_profile mode, ourput gc prfiling data
#ifdef MULTICORE_GC
#ifdef GC_PROFILE
					gc_outputProfileData();
#endif // #ifdef GC_PROFILE
#endif // #ifdef MULTICORE_GC
					disruntimedata();
					terminate(); // All done.
				} // if(!waitconfirm)
			} else {
				// still some objects on the fly on the network
				// reset the waitconfirm and numconfirm
#ifdef DEBUG
					BAMBOO_DEBUGPRINT(0xee07);
#endif
				waitconfirm = false;
				numconfirm = 0;
			} //  if(0 == sumsendobj)
		} else {
			// not all cores are stall, keep on waiting
#ifdef DEBUG
			BAMBOO_DEBUGPRINT(0xee08);
#endif
			waitconfirm = false;
			numconfirm = 0;
		} //  if(allStall)
		BAMBOO_CLOSE_CRITICAL_SECTION_STATUS();
#ifdef DEBUG
		BAMBOO_DEBUGPRINT(0xf000);
#endif
	} // if((!waitconfirm) ||
}

// main function for each core
inline void run(void * arg) {
  int i = 0;
  int argc = 1;
  char ** argv = NULL;
  bool sendStall = false;
  bool isfirst = true;
  bool tocontinue = false;

  corenum = BAMBOO_GET_NUM_OF_CORE();
#ifdef DEBUG
  BAMBOO_DEBUGPRINT(0xeeee);
  BAMBOO_DEBUGPRINT_REG(corenum);
  BAMBOO_DEBUGPRINT(STARTUPCORE);
#endif

	// initialize runtime data structures
	initruntimedata();

  // other architecture related initialization
  initialization();
  initCommunication();

  initializeexithandler();

  // main process of the execution module
  if(BAMBOO_NUM_OF_CORE > NUMCORESACTIVE - 1) {
	// non-executing cores, only processing communications
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
	  /* Create queue of active tasks */
	  activetasks=
			genallocatehashtable((unsigned int(*) (void *)) &hashCodetpd,
                           (int(*) (void *,void *)) &comparetpd);
	  
	  /* Process task information */
	  processtasks();
	  
	  if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
		  /* Create startup object */
		  createstartupobject(argc, argv);
	  }

#ifdef DEBUG
	  BAMBOO_DEBUGPRINT(0xee00);
#endif

	  while(true) {
#ifdef MULTICORE_GC
			// check if need to do GC
			gc(NULL);
#endif

		  // check if there are new active tasks can be executed
		  executetasks();
			if(busystatus) {
				sendStall = false;
			}

#ifndef INTERRUPT
		  while(receiveObject() != -1) {
		  }
#endif  

#ifdef DEBUG
		  BAMBOO_DEBUGPRINT(0xee01);
#endif  
		  
		  // check if there are some pending objects, 
			// if yes, enqueue them and executetasks again
		  tocontinue = checkObjQueue();

		  if(!tocontinue) {
			  // check if stop
			  if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
				  if(isfirst) {
#ifdef DEBUG
					  BAMBOO_DEBUGPRINT(0xee03);
#endif
					  isfirst = false;
				  }
					checkCoreStatus();
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
								send_msg_4(STARTUPCORE, TRANSTALL, BAMBOO_NUM_OF_CORE, 
										       self_numsendobjs, self_numreceiveobjs, false);
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
				  } // if(!sendStall)
			  } // if(STARTUPCORE == BAMBOO_NUM_OF_CORE) 
		  } // if(!tocontinue)
	  } // while(true) 
  } // if(BAMBOO_NUM_OF_CORE > NUMCORESACTIVE - 1)

} // run()

struct ___createstartupobject____I_locals {
  INTPTR size;
  void * next;
  struct  ___StartupObject___ * ___startupobject___;
  struct ArrayObject * ___stringarray___;
}; // struct ___createstartupobject____I_locals

void createstartupobject(int argc, 
		                     char ** argv) {
  int i;

  /* Allocate startup object     */
#ifdef MULTICORE_GC
	struct ___createstartupobject____I_locals ___locals___={2, NULL, NULL, NULL};
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
	ARRAYSET(ao, struct ___TagDescriptor___ *, ao->___cachedCode___, tagd);
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
	ARRAYSET(aonew, struct ___TagDescriptor___ *, ao->___length___, tagd);
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
	struct ArrayObject * aonew=
		allocate_newarray(OBJECTARRAYTYPE,OBJECTARRAYINTERVAL+ao->___length___);
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
void tagclear(void *ptr, 
		          struct ___Object___ * obj, 
							struct ___TagDescriptor___ * tagd) {
#else
void tagclear(struct ___Object___ * obj, 
		          struct ___TagDescriptor___ * tagd) {
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
				ARRAYGET(ao, struct ___TagDescriptor___ *, ao->___cachedCode___));
	ARRAYSET(ao, struct ___TagDescriptor___ *, ao->___cachedCode___, NULL);
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
struct ___TagDescriptor___ * allocate_tag(void *ptr, 
		                                      int index) {
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

void flagbody(struct ___Object___ *ptr, 
		          int flag, 
							struct parameterwrapper ** queues, 
							int length, 
							bool isnew);

int flagcomp(const int *val1, const int *val2) {
  return (*val1)-(*val2);
}

void flagorand(void * ptr, 
		           int ormask, 
							 int andmask, 
							 struct parameterwrapper ** queues, 
							 int length) {
  {
    int oldflag=((int *)ptr)[1];
    int flag=ormask|oldflag;
    flag&=andmask;
    flagbody(ptr, flag, queues, length, false);
  }
}

bool intflagorand(void * ptr, 
		              int ormask, 
									int andmask) {
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

void flagorandinit(void * ptr, 
		               int ormask, 
									 int andmask) {
  int oldflag=((int *)ptr)[1];
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
  for(i = 0; i < length; ++i) {
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
		//struct QueueItem *tmpptr;
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
		for(j = 0; j < length; ++j) {
			parameter = queues[j];     
			/* Check tags */
			if (parameter->numbertags>0) {
				if (tagptr==NULL)
					goto nextloop; //that means the object has no tag 
				                 //but that param needs tag
				else if(tagptr->type==TAGTYPE) { //one tag
					//struct ___TagDescriptor___ * tag=
					//(struct ___TagDescriptor___*) tagptr;	 
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

void enqueueObject_I(void * vptr, 
		                 struct parameterwrapper ** vqueues, 
										 int vlength) {
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
		for(j = 0; j < length; ++j) {
			parameter = queues[j];     
			/* Check tags */
			if (parameter->numbertags>0) {
				if (tagptr==NULL)
					goto nextloop; //that means the object has no tag 
				                 //but that param needs tag
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


int * getAliasLock(void ** ptrs, 
		               int length, 
									 struct RuntimeHash * tbl) {
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

void addAliasLock(void * ptr, 
		              int lock) {
  struct ___Object___ * obj = (struct ___Object___ *)ptr;
  if(((int)ptr != lock) && (obj->lock != (int*)lock)) {
    // originally no alias lock associated or have a different alias lock
    // flush it as the new one
    obj->lock = (int *)lock;
  }
}

#ifdef PROFILE
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

#ifdef MULTICORE_GC
void * localmalloc_I(int coren,
		                 int isize,
		                 int * allocsize) {
	void * mem = NULL;
	int i = 0;
	int j = 0;
	int tofindb = gc_core2block[2*coren+i]+(NUMCORES4GC*2)*j;
	int totest = tofindb;
	int bound = BAMBOO_SMEM_SIZE_L;
	int foundsmem = 0;
	int size = 0;
	do {
		bound = (totest < NUMCORES4GC) ? BAMBOO_SMEM_SIZE_L : BAMBOO_SMEM_SIZE;
		int nsize = bamboo_smemtbl[totest];
		bool islocal = true;
		if(nsize < bound) {
			bool tocheck = true;
			// have some space in the block
			if(totest == tofindb) {
				// the first partition
				size = bound - nsize;
			} else if(nsize == 0) {
				// an empty partition, can be appended
				size += bound;
			} else {
				// not an empty partition, can not be appended
				// the last continuous block is not big enough, go to check the next
				// local block
				islocal = true;
				tocheck = false;
			} // if(totest == tofindb) else if(nsize == 0) else ...
			if(tocheck) {
				if(size >= isize) {
					// have enough space in the block, malloc
					foundsmem = 1;
					break;
				} else {
					// no enough space yet, try to append next continuous block
					islocal = false;
				} // if(size > isize) else ...
			} // if(tocheck)
		} // if(nsize < bound)
		if(islocal) {
			// no space in the block, go to check the next block
			i++;
			if(2==i) {
				i = 0;
				j++;
			}
			tofindb = totest = gc_core2block[2*coren+i]+(NUMCORES4GC*2)*j;
		} else {
			totest += 1;
		} // if(islocal) else ...
		if(totest > gcnumblock-1-bamboo_reserved_smem) {
			// no more local mem, do not find suitable block
			foundsmem = 2;
			break;
		} // if(totest > gcnumblock-1-bamboo_reserved_smem) ...
	} while(true);

	if(foundsmem == 1) {
		// find suitable block
		mem = gcbaseva+bamboo_smemtbl[tofindb]+((tofindb<NUMCORES4GC)?
				(BAMBOO_SMEM_SIZE_L*tofindb):(BAMBOO_LARGE_SMEM_BOUND+
					(tofindb-NUMCORES4GC)*BAMBOO_SMEM_SIZE));
		*allocsize = size;
		// set bamboo_smemtbl
		for(i = tofindb; i <= totest; i++) {
			bamboo_smemtbl[i]=(i<NUMCORES4GC)?BAMBOO_SMEM_SIZE_L:BAMBOO_SMEM_SIZE;
		}
	} else if(foundsmem == 2) {
		// no suitable block
		*allocsize = 0;
	}

	return mem;
} // void * localmalloc_I(int, int, int *)

void * globalmalloc_I(int coren,
		                  int isize,
		                  int * allocsize) {
	void * mem = NULL;
	int tofindb = bamboo_free_block; //0;
	int totest = tofindb;
	int bound = BAMBOO_SMEM_SIZE_L;
	int foundsmem = 0;
	int size = 0;
	if(tofindb > gcnumblock-1-bamboo_reserved_smem) {
		*allocsize = 0;
		return NULL;
	}
	do {
		bound = (totest < NUMCORES4GC) ? BAMBOO_SMEM_SIZE_L : BAMBOO_SMEM_SIZE;
		int nsize = bamboo_smemtbl[totest];
		bool isnext = false;
		if(nsize < bound) {
			bool tocheck = true;
			// have some space in the block
			if(totest == tofindb) {
				// the first partition
				size = bound - nsize;
			} else if(nsize == 0) {
				// an empty partition, can be appended
				size += bound;
			} else {
				// not an empty partition, can not be appended
				// the last continuous block is not big enough, start another block
				isnext = true;
				tocheck = false;
			} // if(totest == tofindb) else if(nsize == 0) else ...
			if(tocheck) {
				if(size >= isize) {
					// have enough space in the block, malloc
					foundsmem = 1;
					break;
				} // if(size > isize) 
			} // if(tocheck)
		} else {
			isnext = true;
		}// if(nsize < bound) else ...
		totest += 1;
		if(totest > gcnumblock-1-bamboo_reserved_smem) {
			// no more local mem, do not find suitable block
			foundsmem = 2;
			break;
		} // if(totest > gcnumblock-1-bamboo_reserved_smem) ...
		if(isnext) {
			// start another block
			tofindb = totest;
		} // if(islocal) 
	} while(true);

	if(foundsmem == 1) {
		// find suitable block
		mem = gcbaseva+bamboo_smemtbl[tofindb]+((tofindb<NUMCORES4GC)?
				(BAMBOO_SMEM_SIZE_L*tofindb):(BAMBOO_LARGE_SMEM_BOUND+
					(tofindb-NUMCORES4GC)*BAMBOO_SMEM_SIZE));
		*allocsize = size;
		// set bamboo_smemtbl
		for(int i = tofindb; i <= totest; i++) {
			bamboo_smemtbl[i]=(i<NUMCORES4GC)?BAMBOO_SMEM_SIZE_L:BAMBOO_SMEM_SIZE;
		}
		if(tofindb == bamboo_free_block) {
			bamboo_free_block = totest+1;
		}
	} else if(foundsmem == 2) {
		// no suitable block
		*allocsize = 0;
		mem = NULL;
	}

	return mem;
} // void * globalmalloc_I(int, int, int *)
#endif // #ifdef MULTICORE_GC

// malloc from the shared memory
void * smemalloc_I(int coren,
		               int size, 
		               int * allocsize) {
	void * mem = NULL;
#ifdef MULTICORE_GC
	int isize = size+(BAMBOO_CACHE_LINE_SIZE);

	// go through the bamboo_smemtbl for suitable partitions
	switch(bamboo_smem_mode) {
		case SMEMLOCAL: {
		  mem = localmalloc_I(coren, isize, allocsize);
			break;
	  }

		case SMEMFIXED: {
			// TODO not supported yet
			BAMBOO_EXIT(0xe001);
			break;
		}

		case SMEMMIXED: {
			// TODO not supported yet
			BAMBOO_EXIT(0xe002);
			break;
		}

		case SMEMGLOBAL: {
			mem = globalmalloc_I(coren, isize, allocsize);
			break;
		}

		default:
			break;
	}

	if(mem == NULL) {
#else
	int toallocate = (size>(BAMBOO_SMEM_SIZE)) ? (size):(BAMBOO_SMEM_SIZE);
	mem = mspace_calloc(bamboo_free_msp, 1, toallocate);
	*allocsize = toallocate;
	if(mem == NULL) {
#endif
		// no enough shared global memory
		*allocsize = 0;
#ifdef MULTICORE_GC
		gcflag = true;
		return NULL;
#else
		BAMBOO_DEBUGPRINT(0xa001);
		BAMBOO_EXIT(0xa001);
#endif
	}
	return mem;
}  // void * smemalloc_I(int, int, int)

INLINE int checkMsgLength_I(int size) {
#ifdef DEBUG
#ifndef TILERA
  BAMBOO_DEBUGPRINT(0xcccc);
#endif
#endif
	int type = msgdata[msgdataindex];
	switch(type) {
		case STATUSCONFIRM:
		case TERMINATE:
#ifdef MULTICORE_GC
		case GCSTARTINIT: 
		case GCSTART: 
		case GCSTARTFLUSH: 
		case GCFINISH: 
		case GCMARKCONFIRM: 
		case GCLOBJREQUEST: 
#endif 
		{
			msglength = 1;
			break;
		}
		case PROFILEOUTPUT:
		case PROFILEFINISH:
#ifdef MULTICORE_GC
		case GCSTARTCOMPACT:
		case GCFINISHINIT: 
		case GCFINISHFLUSH: 
		case GCMARKEDOBJ: 
#endif
		{
			msglength = 2;
			break;
		}
		case MEMREQUEST: 
		case MEMRESPONSE:
#ifdef MULTICORE_GC
		case GCMAPREQUEST: 
		case GCMAPINFO: 
		case GCLOBJMAPPING: 
#endif 
		{
			msglength = 3;
			break;
		}
		case TRANSTALL:
		case LOCKGROUNT:
		case LOCKDENY:
		case LOCKRELEASE:
		case REDIRECTGROUNT:
		case REDIRECTDENY:
		case REDIRECTRELEASE:
#ifdef MULTICORE_GC
		case GCFINISHMARK:
		case GCMOVESTART:
#endif
		{ 
			msglength = 4;
			break;
		}
		case LOCKREQUEST:
		case STATUSREPORT:
#ifdef MULTICORE_GC
		case GCFINISHCOMPACT:
		case GCMARKREPORT: 
#endif 
		{
			msglength = 5;
			break;
		}
		case REDIRECTLOCK: 
		{
			msglength = 6;
			break;
		}
		case TRANSOBJ:  // nonfixed size
#ifdef MULTICORE_GC
		case GCLOBJINFO: 
#endif
		{ // nonfixed size 
			if(size > 1) {
				msglength = msgdata[msgdataindex+1];
			} else {
				return -1;
			}
			break;
		}
		default: 
		{
			BAMBOO_DEBUGPRINT_REG(type);
			int i = 6;
			while(i-- > 0) {
				BAMBOO_DEBUGPRINT(msgdata[msgdataindex+i]);
			}
			BAMBOO_EXIT(0xd005);
			break;
		}
	}
#ifdef DEBUG
#ifndef TILERA
	BAMBOO_DEBUGPRINT_REG(msgdata[msgdataindex]);
#endif
#endif
#ifdef DEBUG
#ifndef TILERA
  BAMBOO_DEBUGPRINT(0xffff);
#endif
#endif
	return msglength;
}

INLINE void processmsg_transobj_I() {
	MSG_INDEXINC_I();
	struct transObjInfo * transObj = RUNMALLOC_I(sizeof(struct transObjInfo));
	int k = 0;
#ifdef DEBUG
#ifndef CLOSE_PRINT
	BAMBOO_DEBUGPRINT(0xe880);
#endif
#endif
	if(BAMBOO_NUM_OF_CORE > NUMCORESACTIVE - 1) {
#ifndef CLOSE_PRINT
		BAMBOO_DEBUGPRINT_REG(msgdata[msgdataindex]/*[2]*/);
#endif
		BAMBOO_EXIT(0xa002);
	} 
	// store the object and its corresponding queue info, enqueue it later
	transObj->objptr = (void *)msgdata[msgdataindex]; //[2]
	MSG_INDEXINC_I();
	transObj->length = (msglength - 3) / 2;
	transObj->queues = RUNMALLOC_I(sizeof(int)*(msglength - 3));
	for(k = 0; k < transObj->length; ++k) {
		transObj->queues[2*k] = msgdata[msgdataindex]; //[3+2*k];
		MSG_INDEXINC_I();
#ifdef DEBUG
#ifndef CLOSE_PRINT
		//BAMBOO_DEBUGPRINT_REG(transObj->queues[2*k]);
#endif
#endif
		transObj->queues[2*k+1] = msgdata[msgdataindex]; //[3+2*k+1];
		MSG_INDEXINC_I();
#ifdef DEBUG
#ifndef CLOSE_PRINT
		//BAMBOO_DEBUGPRINT_REG(transObj->queues[2*k+1]);
#endif
#endif
	}
	// check if there is an existing duplicate item
	{
		struct QueueItem * qitem = getHead(&objqueue);
		struct QueueItem * prev = NULL;
		while(qitem != NULL) {
			struct transObjInfo * tmpinfo = 
				(struct transObjInfo *)(qitem->objectptr);
			if(tmpinfo->objptr == transObj->objptr) {
				// the same object, remove outdate one
				RUNFREE(tmpinfo->queues);
				RUNFREE(tmpinfo);
				removeItem(&objqueue, qitem);
				//break;
			} else {
				prev = qitem;
			}
			if(prev == NULL) {
				qitem = getHead(&objqueue);
			} else {
				qitem = getNextQueueItem(prev);
			}
		}
		addNewItem_I(&objqueue, (void *)transObj);
	}
	++(self_numreceiveobjs);
}

INLINE void processmsg_transtall_I() {
	if(BAMBOO_NUM_OF_CORE != STARTUPCORE) {
	// non startup core can not receive stall msg
#ifndef CLOSE_PRINT
		BAMBOO_DEBUGPRINT_REG(msgdata[msgdataindex]/*[1]*/);
#endif
		BAMBOO_EXIT(0xa003);
	} 
	int num_core = msgdata[msgdataindex]; //[1]
	MSG_INDEXINC_I();
	if(num_core < NUMCORESACTIVE) {
#ifdef DEBUG
#ifndef CLOSE_PRINT
		BAMBOO_DEBUGPRINT(0xe881);
#endif
#endif
		corestatus[num_core] = 0;
		numsendobjs[num_core] = msgdata[msgdataindex]; //[2];
		MSG_INDEXINC_I();
		numreceiveobjs[num_core] = msgdata[msgdataindex]; //[3];
		MSG_INDEXINC_I();
	}
}

#ifndef MULTICORE_GC
INLINE void processmsg_lockrequest_I() {
	// check to see if there is a lock exist for the required obj
	// msgdata[1] -> lock type
	int locktype = msgdata[msgdataindex]; //[1];
	MSG_INDEXINC_I();
	int data2 = msgdata[msgdataindex]; // obj pointer
	MSG_INDEXINC_I();
	int data3 = msgdata[msgdataindex]; // lock
	MSG_INDEXINC_I();
	int data4 = msgdata[msgdataindex]; // request core
	MSG_INDEXINC_I();
	// -1: redirected, 0: approved, 1: denied
	int deny = processlockrequest(locktype, data3, data2, data4, data4, true);  
	if(deny == -1) {
		// this lock request is redirected
		return;
	} else {
		// send response msg
		// for 32 bit machine, the size is always 4 words
		int tmp = deny==1?LOCKDENY:LOCKGROUNT;
		if(isMsgSending) {
			cache_msg_4(data4, tmp, locktype, data2, data3);
		} else {
			send_msg_4(data4, tmp, locktype, data2, data3, true);
		}
	}
}

INLINE void processmsg_lockgrount_I() {
	MSG_INDEXINC_I();
	if(BAMBOO_NUM_OF_CORE > NUMCORESACTIVE - 1) {
#ifndef CLOSE_PRINT
		BAMBOO_DEBUGPRINT_REG(msgdata[msgdataindex]/*[2]*/);
#endif
		BAMBOO_EXIT(0xa004);
	} 
	int data2 = msgdata[msgdataindex];
	MSG_INDEXINC_I();
	int data3 = msgdata[msgdataindex];
	MSG_INDEXINC_I();
	if((lockobj == data2) && (lock2require == data3)) {
#ifdef DEBUG
#ifndef CLOSE_PRINT
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
#ifndef CLOSE_PRINT
		BAMBOO_DEBUGPRINT_REG(data2);
#endif
		BAMBOO_EXIT(0xa005);
	}
}

INLINE void processmsg_lockdeny_I() {
	MSG_INDEXINC_I();
	int data2 = msgdata[msgdataindex];
	MSG_INDEXINC_I();
	int data3 = msgdata[msgdataindex];
	MSG_INDEXINC_I();
	if(BAMBOO_NUM_OF_CORE > NUMCORESACTIVE - 1) {
#ifndef CLOSE_PRINT
		BAMBOO_DEBUGPRINT_REG(data2);
#endif
		BAMBOO_EXIT(0xa006);
	} 
	if((lockobj == data2) && (lock2require == data3)) {
#ifdef DEBUG
#ifndef CLOSE_PRINT
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
#ifndef CLOSE_PRINT
		BAMBOO_DEBUGPRINT_REG(data2);
#endif
		BAMBOO_EXIT(0xa007);
	}
}

INLINE void processmsg_lockrelease_I() {
	int data1 = msgdata[msgdataindex];
	MSG_INDEXINC_I();
	int data2 = msgdata[msgdataindex];
	MSG_INDEXINC_I();
	// receive lock release msg
	processlockrelease(data1, data2, 0, false);
}

INLINE void processmsg_redirectlock_I() {
	// check to see if there is a lock exist for the required obj
	int data1 = msgdata[msgdataindex];
	MSG_INDEXINC_I(); //msgdata[1]; // lock type
	int data2 = msgdata[msgdataindex];
	MSG_INDEXINC_I();//msgdata[2]; // obj pointer
	int data3 = msgdata[msgdataindex];
	MSG_INDEXINC_I(); //msgdata[3]; // redirect lock
	int data4 = msgdata[msgdataindex];
	MSG_INDEXINC_I(); //msgdata[4]; // root request core
	int data5 = msgdata[msgdataindex];
	MSG_INDEXINC_I(); //msgdata[5]; // request core
	int deny = processlockrequest(data1, data3, data2, data5, data4, true);
	if(deny == -1) {
		// this lock request is redirected
		return;
	} else {
		// send response msg
		// for 32 bit machine, the size is always 4 words
		if(isMsgSending) {
			cache_msg_4(data4, deny==1?REDIRECTDENY:REDIRECTGROUNT, 
									data1, data2, data3);
		} else {
			send_msg_4(data4, deny==1?REDIRECTDENY:REDIRECTGROUNT, 
								 data1, data2, data3, true);
		}
	}
}

INLINE void processmsg_redirectgrount_I() {
	MSG_INDEXINC_I();
	int data2 = msgdata[msgdataindex];
	MSG_INDEXINC_I();
	if(BAMBOO_NUM_OF_CORE > NUMCORESACTIVE - 1) {
#ifndef CLOSE_PRINT
		BAMBOO_DEBUGPRINT_REG(data2);
#endif
		BAMBOO_EXIT(0xa00a);
	}
	if(lockobj == data2) {
#ifdef DEBUG
#ifndef CLOSE_PRINT
		BAMBOO_DEBUGPRINT(0xe891);
#endif
#endif
		int data3 = msgdata[msgdataindex];
		MSG_INDEXINC_I();
		lockresult = 1;
		lockflag = true;
		RuntimeHashadd_I(objRedirectLockTbl, lockobj, data3);
#ifndef INTERRUPT
		reside = false;
#endif
	} else {
		// conflicts on lockresults
#ifndef CLOSE_PRINT
		BAMBOO_DEBUGPRINT_REG(data2);
#endif
		BAMBOO_EXIT(0xa00b);
	}
}

INLINE void processmsg_redirectdeny_I() {
	MSG_INDEXINC_I();
	int data2 = msgdata[msgdataindex];
	MSG_INDEXINC_I();
	if(BAMBOO_NUM_OF_CORE > NUMCORESACTIVE - 1) {
#ifndef CLOSE_PRINT
		BAMBOO_DEBUGPRINT_REG(data2);
#endif
		BAMBOO_EXIT(0xa00c);
	}
	if(lockobj == data2) {
#ifdef DEBUG
#ifndef CLOSE_PRINT
		BAMBOO_DEBUGPRINT(0xe892);
#endif
#endif
		lockresult = 0;
		lockflag = true;
#ifndef INTERRUPT
		reside = false;
#endif
	} else {
		// conflicts on lockresults
#ifndef CLOSE_PRINT
		BAMBOO_DEBUGPRINT_REG(data2);
#endif
		BAMBOO_EXIT(0xa00d);
	}
}

INLINE void processmsg_redirectrelease_I() {
	int data1 = msgdata[msgdataindex];
	MSG_INDEXINC_I();
	int data2 = msgdata[msgdataindex];
	MSG_INDEXINC_I();
	int data3 = msgdata[msgdataindex];
	MSG_INDEXINC_I();
	processlockrelease(data1, data2, data3, true);
}
#endif // #ifndef MULTICORE_GC

#ifdef PROFILE
INLINE void processmsg_profileoutput_I() {
	if(BAMBOO_NUM_OF_CORE == STARTUPCORE) {
		// startup core can not receive profile output finish msg
		BAMBOO_EXIT(0xa008);
	}
#ifdef DEBUG
#ifndef CLOSE_PRINT
	BAMBOO_DEBUGPRINT(0xe885);
#endif
#endif
	stall = true;
	totalexetime = msgdata[msgdataindex]; //[1]
	MSG_INDEXINC_I();
	outputProfileData();
	if(isMsgSending) {
		cache_msg_2(STARTUPCORE, PROFILEFINISH, BAMBOO_NUM_OF_CORE);
	} else {
		send_msg_2(STARTUPCORE, PROFILEFINISH, BAMBOO_NUM_OF_CORE, true);
	}
}

INLINE void processmsg_profilefinish_I() {
	if(BAMBOO_NUM_OF_CORE != STARTUPCORE) {
		// non startup core can not receive profile output finish msg
#ifndef CLOSE_PRINT
		BAMBOO_DEBUGPRINT_REG(msgdata[msgdataindex/*1*/]);
#endif
		BAMBOO_EXIT(0xa009);
	}
#ifdef DEBUG
#ifndef CLOSE_PRINT
	BAMBOO_DEBUGPRINT(0xe886);
#endif
#endif
	int data1 = msgdata[msgdataindex];
	MSG_INDEXINC_I();
	profilestatus[data1] = 0;
}
#endif // #ifdef PROFILE

INLINE void processmsg_statusconfirm_I() {
	if((BAMBOO_NUM_OF_CORE == STARTUPCORE) 
			|| (BAMBOO_NUM_OF_CORE > NUMCORESACTIVE - 1)) {
		// wrong core to receive such msg
		BAMBOO_EXIT(0xa00e);
	} else {
		// send response msg
#ifdef DEBUG
#ifndef CLOSE_PRINT
		BAMBOO_DEBUGPRINT(0xe887);
#endif
#endif
		if(isMsgSending) {
			cache_msg_5(STARTUPCORE, STATUSREPORT, 
									busystatus?1:0, BAMBOO_NUM_OF_CORE,
									self_numsendobjs, self_numreceiveobjs);
		} else {
			send_msg_5(STARTUPCORE, STATUSREPORT, busystatus?1:0, 
								 BAMBOO_NUM_OF_CORE, self_numsendobjs, 
								 self_numreceiveobjs, true);
		}
	}
}

INLINE void processmsg_statusreport_I() {
	int data1 = msgdata[msgdataindex];
	MSG_INDEXINC_I();
	int data2 = msgdata[msgdataindex];
	MSG_INDEXINC_I();
	int data3 = msgdata[msgdataindex];
	MSG_INDEXINC_I();
	int data4 = msgdata[msgdataindex];
	MSG_INDEXINC_I();
	// receive a status confirm info
	if(BAMBOO_NUM_OF_CORE != STARTUPCORE) {
		// wrong core to receive such msg
#ifndef CLOSE_PRINT
		BAMBOO_DEBUGPRINT_REG(data2);
#endif
		BAMBOO_EXIT(0xa00f);
	} else {
#ifdef DEBUG
#ifndef CLOSE_PRINT
		BAMBOO_DEBUGPRINT(0xe888);
#endif
#endif
		if(waitconfirm) {
			numconfirm--;
		}
		corestatus[data2] = data1;
		numsendobjs[data2] = data3;
		numreceiveobjs[data2] = data4;
	}
}

INLINE void processmsg_terminate_I() {
#ifdef DEBUG
#ifndef CLOSE_PRINT
	BAMBOO_DEBUGPRINT(0xe889);
#endif
#endif
	disruntimedata();
	BAMBOO_EXIT(0);
}

INLINE void processmsg_memrequest_I() {
	int data1 = msgdata[msgdataindex];
	MSG_INDEXINC_I();
	int data2 = msgdata[msgdataindex];
	MSG_INDEXINC_I();
	// receive a shared memory request msg
	if(BAMBOO_NUM_OF_CORE != STARTUPCORE) {
		// wrong core to receive such msg
#ifndef CLOSE_PRINT
		BAMBOO_DEBUGPRINT_REG(data2);
#endif
		BAMBOO_EXIT(0xa010);
	} else {
#ifdef DEBUG
#ifndef CLOSE_PRINT
		BAMBOO_DEBUGPRINT(0xe88a);
#endif
#endif
		int allocsize = 0;
		void * mem = NULL;
#ifdef MULTICORE_GC
		if(gcprocessing) {
			// is currently doing gc, dump this msg
			if(INITPHASE == gcphase) {
				// if still in the initphase of gc, send a startinit msg again
				if(isMsgSending) {
					cache_msg_1(data2, GCSTARTINIT);
				} else {
					send_msg_1(data2, GCSTARTINIT, true);
				}
			}
		} else { 
#endif
		mem = smemalloc_I(data2, data1, &allocsize);
		if(mem != NULL) {
			// send the start_va to request core
			if(isMsgSending) {
				cache_msg_3(data2, MEMRESPONSE, mem, allocsize);
			} else {
				send_msg_3(data2, MEMRESPONSE, mem, allocsize, true);
			} 
		} // if mem == NULL, the gcflag of the startup core has been set
			// and the gc should be started later, then a GCSTARTINIT msg
			// will be sent to the requesting core to notice it to start gc
			// and try malloc again
#ifdef MULTICORE_GC
		}
#endif
	}
}

INLINE void processmsg_memresponse_I() {
	int data1 = msgdata[msgdataindex];
	MSG_INDEXINC_I();
	int data2 = msgdata[msgdataindex];
	MSG_INDEXINC_I();
	// receive a shared memory response msg
#ifdef DEBUG
#ifndef CLOSE_PRINT
	BAMBOO_DEBUGPRINT(0xe88b);
#endif
#endif
#ifdef MULTICORE_GC
	// if is currently doing gc, dump this msg
	if(!gcprocessing) {
#endif
	if(data2 == 0) {
		bamboo_smem_size = 0;
		bamboo_cur_msp = 0;
	} else {
#ifdef MULTICORE_GC
		// fill header to store the size of this mem block
		memset(data1, 0, BAMBOO_CACHE_LINE_SIZE);
		(*((int*)data1)) = data2;
		bamboo_smem_size = data2 - BAMBOO_CACHE_LINE_SIZE;
		bamboo_cur_msp = data1 + BAMBOO_CACHE_LINE_SIZE;
#else
		bamboo_smem_size = data2;
		bamboo_cur_msp =(void*)(data1);
#endif
	}
	smemflag = true;
#ifdef MULTICORE_GC
	}
#endif
}

#ifdef MULTICORE_GC
INLINE void processmsg_gcstartinit_I() {
	gcflag = true;
	gcphase = INITPHASE;
	if(!smemflag) {
		// is waiting for response of mem request
		// let it return NULL and start gc
		bamboo_smem_size = 0;
		bamboo_cur_msp = NULL;
		smemflag = true;
	}
}

INLINE void processmsg_gcstart_I() {
#ifdef DEBUG
#ifndef CLOSE_PRINT
	BAMBOO_DEBUGPRINT(0xe88c);
#endif
#endif
	// set the GC flag
	gcphase = MARKPHASE;
}

INLINE void processmsg_gcstartcompact_I() {
	gcblock2fill = msgdata[msgdataindex];
	MSG_INDEXINC_I(); //msgdata[1];
	gcphase = COMPACTPHASE;
}

INLINE void processmsg_gcstartflush_I() {
	gcphase = FLUSHPHASE;
}

INLINE void processmsg_gcfinishinit_I() {
	int data1 = msgdata[msgdataindex];
	MSG_INDEXINC_I();
	// received a init phase finish msg
	if(BAMBOO_NUM_OF_CORE != STARTUPCORE) {
		// non startup core can not receive this msg
#ifndef CLOSE_PRINT
		BAMBOO_DEBUGPRINT_REG(data1);
#endif
		BAMBOO_EXIT(0xb001);
	}
#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xe88c);
	BAMBOO_DEBUGPRINT_REG(data1);
#endif
	// All cores should do init GC
	if(data1 < NUMCORESACTIVE) {
		gccorestatus[data1] = 0;
	}
}

INLINE void processmsg_gcfinishmark_I() {
	int data1 = msgdata[msgdataindex];
	MSG_INDEXINC_I();
	int data2 = msgdata[msgdataindex];
	MSG_INDEXINC_I();
	int data3 = msgdata[msgdataindex];
	MSG_INDEXINC_I();
	// received a mark phase finish msg
	if(BAMBOO_NUM_OF_CORE != STARTUPCORE) {
		// non startup core can not receive this msg
#ifndef CLOSE_PRINT
		BAMBOO_DEBUGPRINT_REG(data1);
#endif
		BAMBOO_EXIT(0xb002);
	}
	// all cores should do mark
	if(data1 < NUMCORESACTIVE) {
		gccorestatus[data1] = 0;
		gcnumsendobjs[data1] = data2;
		gcnumreceiveobjs[data1] = data3;
	}
}

INLINE void processmsg_gcfinishcompact_I() {
	if(BAMBOO_NUM_OF_CORE != STARTUPCORE) {
		// non startup core can not receive this msg
		// return -1
#ifndef CLOSE_PRINT
		BAMBOO_DEBUGPRINT_REG(msgdata[msgdataindex]/*[1]*/);
#endif
		BAMBOO_EXIT(0xb003);
	}
	int cnum = msgdata[msgdataindex];
	MSG_INDEXINC_I(); //msgdata[1];
	int filledblocks = msgdata[msgdataindex];
	MSG_INDEXINC_I(); //msgdata[2];
	int heaptop = msgdata[msgdataindex];
	MSG_INDEXINC_I(); //msgdata[3];
	int data4 = msgdata[msgdataindex];
	MSG_INDEXINC_I(); //msgdata[4];
	// only gc cores need to do compact
	if(cnum < NUMCORES4GC) {
		if(COMPACTPHASE == gcphase) {
			gcfilledblocks[cnum] = filledblocks;
			gcloads[cnum] = heaptop;
		}
		if(data4 > 0) {
			// ask for more mem
			int startaddr = 0;
			int tomove = 0;
			int dstcore = 0;
			if(gcfindSpareMem_I(&startaddr, &tomove, &dstcore, data4, cnum)) {
				if(isMsgSending) {
					cache_msg_4(cnum, GCMOVESTART, dstcore, startaddr, tomove);
			  } else {
					send_msg_4(cnum, GCMOVESTART, dstcore, startaddr, tomove, true);
				}
			}
		} else {
			gccorestatus[cnum] = 0;
		} // if(data4>0)
	} // if(cnum < NUMCORES4GC)
}

INLINE void processmsg_gcfinishflush_I() {
	int data1 = msgdata[msgdataindex];
	MSG_INDEXINC_I();
	// received a flush phase finish msg
	if(BAMBOO_NUM_OF_CORE != STARTUPCORE) {
		// non startup core can not receive this msg
		// return -1
#ifndef CLOSE_PRINT
		BAMBOO_DEBUGPRINT_REG(data1);
#endif
		BAMBOO_EXIT(0xb004);
	} 
	// all cores should do flush
	if(data1 < NUMCORESACTIVE) {
		gccorestatus[data1] = 0;
	}
}

INLINE void processmsg_gcmarkconfirm_I() {
	if((BAMBOO_NUM_OF_CORE == STARTUPCORE) 
			|| (BAMBOO_NUM_OF_CORE > NUMCORESACTIVE - 1)) {
		// wrong core to receive such msg
		BAMBOO_EXIT(0xb005);
	} else {
		// send response msg
		if(isMsgSending) {
			cache_msg_5(STARTUPCORE, GCMARKREPORT, BAMBOO_NUM_OF_CORE, 
									gcbusystatus, gcself_numsendobjs, 
									gcself_numreceiveobjs);
		} else {
			send_msg_5(STARTUPCORE, GCMARKREPORT, BAMBOO_NUM_OF_CORE, 
								 gcbusystatus, gcself_numsendobjs, 
								 gcself_numreceiveobjs, true);
		}
	}
}

INLINE void processmsg_gcmarkreport_I() {
	int data1 = msgdata[msgdataindex];
	MSG_INDEXINC_I();
	int data2 = msgdata[msgdataindex];
	MSG_INDEXINC_I();
	int data3 = msgdata[msgdataindex];
	MSG_INDEXINC_I();
	int data4 = msgdata[msgdataindex];
	MSG_INDEXINC_I();
	// received a marked phase finish confirm response msg
	if(BAMBOO_NUM_OF_CORE != STARTUPCORE) {
		// wrong core to receive such msg
#ifndef CLOSE_PRINT
		BAMBOO_DEBUGPRINT_REG(data2);
#endif
		BAMBOO_EXIT(0xb006);
	} else {
		if(waitconfirm) {
			numconfirm--;
		}
		gccorestatus[data1] = data2;
		gcnumsendobjs[data1] = data3;
		gcnumreceiveobjs[data1] = data4;
	}
}

INLINE void processmsg_gcmarkedobj_I() {
	int data1 = msgdata[msgdataindex];
	MSG_INDEXINC_I();
	// received a markedObj msg
	if(((int *)data1)[6] == INIT) {
			// this is the first time that this object is discovered,
			// set the flag as DISCOVERED
			((int *)data1)[6] = DISCOVERED;
			gc_enqueue_I(data1);
	}
	gcself_numreceiveobjs++;
	gcbusystatus = true;
}

INLINE void processmsg_gcmovestart_I() {
	gctomove = true;
	gcdstcore = msgdata[msgdataindex];
	MSG_INDEXINC_I(); //msgdata[1];
	gcmovestartaddr = msgdata[msgdataindex];
	MSG_INDEXINC_I(); //msgdata[2];
	gcblock2fill = msgdata[msgdataindex];
	MSG_INDEXINC_I(); //msgdata[3];
}

INLINE void processmsg_gcmaprequest_I() {
#ifdef GC_PROFILE
	//unsigned long long ttime = BAMBOO_GET_EXE_TIME();
#endif
	void * dstptr = NULL;
	int data1 = msgdata[msgdataindex];
	MSG_INDEXINC_I();
	//dstptr = mgchashSearch(msgdata[1]);
#ifdef GC_PROFILE
	unsigned long long ttime = BAMBOO_GET_EXE_TIME();
#endif
	RuntimeHashget(gcpointertbl, data1, &dstptr);
#ifdef GC_PROFILE
	flushstalltime += BAMBOO_GET_EXE_TIME() - ttime;
#endif
	int data2 = msgdata[msgdataindex];
	MSG_INDEXINC_I();
	//MGCHashget(gcpointertbl, msgdata[1], &dstptr);
#ifdef GC_PROFILE
	unsigned long long ttimei = BAMBOO_GET_EXE_TIME();
#endif
	if(NULL == dstptr) {
		// no such pointer in this core, something is wrong
#ifdef DEBUG
		BAMBOO_DEBUGPRINT_REG(data1);
		BAMBOO_DEBUGPRINT_REG(data2);
#endif
		BAMBOO_EXIT(0xb007);
		//assume that the object was not moved, use the original address
		/*if(isMsgSending) {
			cache_msg_3(msgdata[2], GCMAPINFO, msgdata[1], msgdata[1]);
		} else {
			send_msg_3(msgdata[2], GCMAPINFO, msgdata[1], msgdata[1], true);
		}*/
	} else {
		// send back the mapping info
		if(isMsgSending) {
			cache_msg_3(data2, GCMAPINFO, data1, (int)dstptr);
		} else {
			send_msg_3(data2, GCMAPINFO, data1, (int)dstptr, true);
		}
	}
#ifdef GC_PROFILE
	flushstalltime_i += BAMBOO_GET_EXE_TIME()-ttimei;
	//num_mapinforequest_i++;
#endif
}

INLINE void processmsg_gcmapinfo_I() {
#ifdef GC_PROFILE
	//unsigned long long ttime = BAMBOO_GET_EXE_TIME();
#endif
	int data1 = msgdata[msgdataindex];
	MSG_INDEXINC_I();
	if(data1 != gcobj2map) {
			// obj not matched, something is wrong
#ifdef DEBUG
			BAMBOO_DEBUGPRINT_REG(gcobj2map);
			BAMBOO_DEBUGPRINT_REG(msgdata[1]);
#endif
			BAMBOO_EXIT(0xb008);
		} else {
			gcmappedobj = msgdata[msgdataindex]; // [2]
      MSG_INDEXINC_I();
			//mgchashReplace_I(msgdata[1], msgdata[2]);
			//mgchashInsert_I(gcobj2map, gcmappedobj);
			RuntimeHashadd_I(gcpointertbl, gcobj2map, gcmappedobj);
			//MGCHashadd_I(gcpointertbl, gcobj2map, gcmappedobj);
		}
		gcismapped = true;
#ifdef GC_PROFILE
			//flushstalltime += BAMBOO_GET_EXE_TIME() - ttime;
#endif
}

INLINE void processmsg_gclobjinfo_I() {
	numconfirm--;

	int data1 = msgdata[msgdataindex];
	MSG_INDEXINC_I();
	int data2 = msgdata[msgdataindex];
	MSG_INDEXINC_I();
	if(BAMBOO_NUM_OF_CORE > NUMCORES4GC - 1) {
#ifndef CLOSE_PRINT
		BAMBOO_DEBUGPRINT_REG(data2);
#endif
		BAMBOO_EXIT(0xb009);
	} 
	// store the mark result info 
	int cnum = data2;
	gcloads[cnum] = msgdata[msgdataindex];
	MSG_INDEXINC_I(); // msgdata[3];
	int data4 = msgdata[msgdataindex];
	MSG_INDEXINC_I();
	if(gcheaptop < data4) {
		gcheaptop = data4;
	}
	// large obj info here
	for(int k = 5; k < data1;) {
		int lobj = msgdata[msgdataindex];
		MSG_INDEXINC_I(); //msgdata[k++];
		int length = msgdata[msgdataindex];
		MSG_INDEXINC_I(); //msgdata[k++];
		gc_lobjenqueue_I(lobj, length, cnum);
		gcnumlobjs++;
	} // for(int k = 5; k < msgdata[1];)
}

INLINE void processmsg_gclobjmapping_I() {
	int data1 = msgdata[msgdataindex];
	MSG_INDEXINC_I();
	int data2 = msgdata[msgdataindex];
	MSG_INDEXINC_I();
	//mgchashInsert_I(msgdata[1], msgdata[2]);
	RuntimeHashadd_I(gcpointertbl, data1, data2);
	//MGCHashadd_I(gcpointertbl, msgdata[1], msgdata[2]);
}
#endif // #ifdef MULTICORE_GC

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
msg:
	// get the incoming msgs
  if(receiveMsg() == -1) {
	  return -1;
  }
processmsg:
	// processing received msgs
	int size = 0;
	MSG_REMAINSIZE_I(&size);
  if(checkMsgLength_I(size) == -1) {
		// not a whole msg
		// have new coming msg
		if(BAMBOO_MSG_AVAIL() != 0) {
			goto msg;
		} else {
			return -1;
		}
	}

	if(msglength <= size) {
		// have some whole msg
  //if(msgdataindex == msglength) {
    // received a whole msg
    MSGTYPE type;
    type = msgdata[msgdataindex]; //[0]
		MSG_INDEXINC_I();
		msgdatafull = false;
		// TODO
		//tprintf("msg type: %x\n", type);
    switch(type) {
			case TRANSOBJ: {
				// receive a object transfer msg
				processmsg_transobj_I();
				break;
			} // case TRANSOBJ

			case TRANSTALL: {
				// receive a stall msg
				processmsg_transtall_I();
				break;
			} // case TRANSTALL

// GC version have no lock msgs
#ifndef MULTICORE_GC
			case LOCKREQUEST: {
				// receive lock request msg, handle it right now
				processmsg_lockrequest_I();
				break;
			} // case LOCKREQUEST

			case LOCKGROUNT: {
				// receive lock grount msg
				processmsg_lockgrount_I();
				break;
			} // case LOCKGROUNT

			case LOCKDENY: {
				// receive lock deny msg
				processmsg_lockdeny_I();
				break;
			} // case LOCKDENY

			case LOCKRELEASE: {
				processmsg_lockrelease_I();
				break;
			} // case LOCKRELEASE
#endif // #ifndef MULTICORE_GC

#ifdef PROFILE
			case PROFILEOUTPUT: {
				// receive an output profile data request msg
				processmsg_profileoutput_I();
				break;
			} // case PROFILEOUTPUT

			case PROFILEFINISH: {
				// receive a profile output finish msg
				processmsg_profilefinish_I();
				break;
			} // case PROFILEFINISH
#endif // #ifdef PROFILE

// GC version has no lock msgs
#ifndef MULTICORE_GC
			case REDIRECTLOCK: {
				// receive a redirect lock request msg, handle it right now
				processmsg_redirectlock_I();
				break;
			} // case REDIRECTLOCK

			case REDIRECTGROUNT: {
				// receive a lock grant msg with redirect info
				processmsg_redirectgrount_I();
				break;
			} // case REDIRECTGROUNT
			
			case REDIRECTDENY: {
				// receive a lock deny msg with redirect info
				processmsg_redirectdeny_I();
				break;
			} // case REDIRECTDENY

			case REDIRECTRELEASE: {
				// receive a lock release msg with redirect info
				processmsg_redirectrelease_I();
				break;
			} // case REDIRECTRELEASE
#endif // #ifndef MULTICORE_GC
	
			case STATUSCONFIRM: {
				// receive a status confirm info
				processmsg_statusconfirm_I();
				break;
			} // case STATUSCONFIRM

			case STATUSREPORT: {
				processmsg_statusreport_I();
				break;
			} // case STATUSREPORT

			case TERMINATE: {
				// receive a terminate msg
				processmsg_terminate_I();
				break;
			} // case TERMINATE

			case MEMREQUEST: {
				processmsg_memrequest_I();
				break;
			} // case MEMREQUEST

			case MEMRESPONSE: {
				processmsg_memresponse_I();
				break;
			} // case MEMRESPONSE

#ifdef MULTICORE_GC
			// GC msgs
			case GCSTARTINIT: {
				processmsg_gcstartinit_I();
				break;
			} // case GCSTARTINIT

			case GCSTART: {
				// receive a start GC msg
				processmsg_gcstart_I();
				break;
			} // case GCSTART

			case GCSTARTCOMPACT: {
				// a compact phase start msg
				processmsg_gcstartcompact_I();
				break;
			} // case GCSTARTCOMPACT

			case GCSTARTFLUSH: {
				// received a flush phase start msg
				processmsg_gcstartflush_I();
				break;
			} // case GCSTARTFLUSH
			
			case GCFINISHINIT: {
				processmsg_gcfinishinit_I();
				break;
			} // case GCFINISHINIT

			case GCFINISHMARK: {
				processmsg_gcfinishmark_I();
				break;
			} // case GCFINISHMARK
			
			case GCFINISHCOMPACT: {
				// received a compact phase finish msg
				processmsg_gcfinishcompact_I();
				break;
			} // case GCFINISHCOMPACT

			case GCFINISHFLUSH: {
				processmsg_gcfinishflush_I();
				break;
			} // case GCFINISHFLUSH

			case GCFINISH: {
				// received a GC finish msg
				gcphase = FINISHPHASE;
				break;
			} // case GCFINISH

			case GCMARKCONFIRM: {
				// received a marked phase finish confirm request msg
				// all cores should do mark
				processmsg_gcmarkconfirm_I();
				break;
			} // case GCMARKCONFIRM

			case GCMARKREPORT: {
				processmsg_gcmarkreport_I();
				break;
			} // case GCMARKREPORT

			case GCMARKEDOBJ: {
				processmsg_gcmarkedobj_I();
				break;
			} // case GCMARKEDOBJ

			case GCMOVESTART: {
				// received a start moving objs msg
				processmsg_gcmovestart_I();
				break;
			} // case GCMOVESTART
			
			case GCMAPREQUEST: {
				// received a mapping info request msg
				processmsg_gcmaprequest_I();
				break;
			} // case GCMAPREQUEST

			case GCMAPINFO: {
				// received a mapping info response msg
				processmsg_gcmapinfo_I();
				break;
			} // case GCMAPINFO

			case GCLOBJREQUEST: {
				// received a large objs info request msg
				transferMarkResults_I();
				break;
			} // case GCLOBJREQUEST

			case GCLOBJINFO: {
				// received a large objs info response msg
				processmsg_gclobjinfo_I();
				break;
			} // case GCLOBJINFO
			
			case GCLOBJMAPPING: {
				// received a large obj mapping info msg
				processmsg_gclobjmapping_I();
				break;
			} // case GCLOBJMAPPING

#endif // #ifdef MULTICORE_GC

			default:
				break;
		} // switch(type)
		//memset(msgdata, '\0', sizeof(int) * msgdataindex);
		//msgdataindex = 0;
		msglength = BAMBOO_MSG_BUF_LENGTH;
		// TODO
		//printf("++ msg: %x \n", type);
		if(msgdataindex != msgdatalast) {
			// still have available msg
			goto processmsg;
		}
#ifdef DEBUG
#ifndef CLOSE_PRINT
		BAMBOO_DEBUGPRINT(0xe88d);
#endif
#endif

		// have new coming msg
		if(BAMBOO_MSG_AVAIL() != 0) {
			goto msg;
		}

#ifdef PROFILE
/*if(isInterrupt) {
		profileTaskEnd();
	}*/
#endif
		return (int)type;
	} else {
		// not a whole msg
#ifdef DEBUG
#ifndef CLOSE_PRINT
		BAMBOO_DEBUGPRINT(0xe88e);
#endif
#endif
#ifdef PROFILE
	/*  if(isInterrupt) {
				profileTaskEnd();
			}*/
#endif
    return -2;
  }
}

int enqueuetasks(struct parameterwrapper *parameter, 
		             struct parameterwrapper *prevptr, 
								 struct ___Object___ *ptr, 
								 int * enterflags, 
								 int numenterflags) {
  void * taskpointerarray[MAXTASKPARAMS];
  int j;
  //int numparams=parameter->task->numParameters;
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
    if ((/*!gencontains(failedtasks, tpd)&&*/ 
					!gencontains(activetasks,tpd))) {
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
  //int numparams=parameter->task->numParameters;
  int numiterators=parameter->task->numTotal-1;
  int retval=1;
  //int addnormal=1;
  //int adderror=1;

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
    if ((/*!gencontains(failedtasks, tpd)&&*/ 
					!gencontains(activetasks,tpd))) {
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
  targetcore = (reallock >> 5) % BAMBOO_TOTALCORE;

#ifdef DEBUG
  BAMBOO_DEBUGPRINT(0xe671);
  BAMBOO_DEBUGPRINT_REG((int)lock);
  BAMBOO_DEBUGPRINT_REG(reallock);
  BAMBOO_DEBUGPRINT_REG(targetcore);
#endif

  if(targetcore == BAMBOO_NUM_OF_CORE) {
	BAMBOO_START_CRITICAL_SECTION_LOCK();
#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xf001);
#endif
    // reside on this core
    if(!RuntimeHashcontainskey(locktbl, reallock)) {
      // no locks for this object, something is wrong
      BAMBOO_EXIT(0xa011);
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
		send_msg_4(targetcore, REDIRECTRELEASE, 1, (int)lock, 
				       (int)redirectlock, false);
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
#ifdef MULTICORE_GC
		gc(NULL);
#endif
#ifdef DEBUG
    BAMBOO_DEBUGPRINT(0xe990);
#endif

    /* See if there are any active tasks */
    //if (hashsize(activetasks)>0) {
      int i;
#ifdef PROFILE
#ifdef ACCURATEPROFILE
	  profileTaskStart("tpd checking");
#endif
#endif
	  //long clock1;
	  //clock1 = BAMBOO_GET_EXE_TIME();

	  busystatus = true;
		currtpd=(struct taskparamdescriptor *) getfirstkey(activetasks);
		genfreekey(activetasks, currtpd);

		numparams=currtpd->task->numParameters;
		numtotal=currtpd->task->numTotal;

	  // clear the lockRedirectTbl 
		// (TODO, this table should be empty after all locks are released)
	  // reset all locks
	  /*for(j = 0; j < MAXTASKPARAMS; j++) {
		  runtime_locks[j].redirectlock = 0;
		  runtime_locks[j].value = 0;
	  }*/
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
		  if(((struct ___Object___ *)param)->lock == NULL) {
			  tmplock = (int)param;
		  } else {
			  tmplock = (int)(((struct ___Object___ *)param)->lock);
		  }
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
	  } // line 2713: for(i = 0; i < numparams; i++) 
	  // grab these required locks
#ifdef DEBUG
	  BAMBOO_DEBUGPRINT(0xe991);
#endif
	  //long clock2;
	  //clock2 = BAMBOO_GET_EXE_TIME();

	  for(i = 0; i < runtime_locklen; i++) {
		  int * lock = (int *)(runtime_locks[i].redirectlock);
		  islock = true;
		  // require locks for this parameter if it is not a startup object
#ifdef DEBUG
		  BAMBOO_DEBUGPRINT_REG((int)lock);
		  BAMBOO_DEBUGPRINT_REG((int)(runtime_locks[i].value));
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
#ifdef DEBUG
			  BAMBOO_DEBUGPRINT(0xe992);
				BAMBOO_DEBUGPRINT_REG(lock);
#endif
				// check if has the lock already
			  // can not get the lock, try later
			  // release all grabbed locks for previous parameters
			  for(j = 0; j < i; ++j) { 
				  lock = (int*)(runtime_locks[j].redirectlock);
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
#ifdef ACCURATEPROFILE
			  // fail, set the end of the checkTaskInfo
			  profileTaskEnd();
#endif
#endif
			  goto newtask;
				//}
		  }
	  } // line 2752:  for(i = 0; i < runtime_locklen; i++)

	  /*long clock3;
	  clock3 = BAMBOO_GET_EXE_TIME();
	  //tprintf("sort: %d, grab: %d \n", clock2-clock1, clock3-clock2);*/

#ifdef DEBUG
	BAMBOO_DEBUGPRINT(0xe993);
#endif
      /* Make sure that the parameters are still in the queues */
      for(i=0; i<numparams; i++) {
	void * parameter=currtpd->parameterArray[i];

	// flush the object
#ifdef CACHEFLUSH
	BAMBOO_CACHE_FLUSH_RANGE((int)parameter, 
			classsize[((struct ___Object___ *)parameter)->type]);
#endif
	tmpparam = (struct ___Object___ *)parameter;
	pd=currtpd->task->descriptorarray[i];
	pw=(struct parameterwrapper *) pd->queue;
	/* Check that object is still in queue */
	{
	  if (!ObjectHashcontainskey(pw->objectset, (int) parameter)) {
#ifdef DEBUG
	    BAMBOO_DEBUGPRINT(0xe994);
			BAMBOO_DEBUGPRINT_REG(parameter);
#endif
	    // release grabbed locks
	    for(j = 0; j < runtime_locklen; ++j) {
		int * lock = (int *)(runtime_locks[j].redirectlock);
		releasewritelock(lock);
	    }
	    RUNFREE(currtpd->parameterArray);
	    RUNFREE(currtpd);
			currtpd = NULL;
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
			BAMBOO_DEBUGPRINT_REG(parameter);
#endif
	    ObjectHashget(pw->objectset, (int) parameter, (int *) &next, 
					          (int *) &enterflags, &UNUSED, &UNUSED2);
	    ObjectHashremove(pw->objectset, (int)parameter);
	    if (enterflags!=NULL)
	      RUNFREE(enterflags);
	    // release grabbed locks
	    for(j = 0; j < runtime_locklen; ++j) {
		 int * lock = (int *)(runtime_locks[j].redirectlock);
		releasewritelock(lock);
	    }
	    RUNFREE(currtpd->parameterArray);
	    RUNFREE(currtpd);
			currtpd = NULL;
#ifdef PROFILE
#ifdef ACCURATEPROFILE
	    // fail, set the end of the checkTaskInfo
		profileTaskEnd();
#endif
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
	    for(tmpj = 0; tmpj < runtime_locklen; ++tmpj) {
		 int * lock = (int *)(runtime_locks[tmpj].redirectlock);
		releasewritelock(lock);
	    }
		}
	    RUNFREE(currtpd->parameterArray);
	    RUNFREE(currtpd);
			currtpd = NULL;
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
execute:
	  /* Actually call task */
#ifdef MULTICORE_GC
	  ((int *)taskpointerarray)[0]=currtpd->numParameters;
	  taskpointerarray[1]=NULL;
#endif
#ifdef PROFILE
#ifdef ACCURATEPROFILE
	  // check finish, set the end of the checkTaskInfo
	  profileTaskEnd();
#endif
	  profileTaskStart(currtpd->task->name);
#endif
	  // TODO
	  //long clock4;
	  //clock4 = BAMBOO_GET_EXE_TIME();
	  //tprintf("sort: %d, grab: %d, check: %d \n", (int)(clock2-clock1), (int)(clock3-clock2), (int)(clock4-clock3));

#ifdef DEBUG
		BAMBOO_DEBUGPRINT(0xe997);
#endif
		((void(*) (void **))currtpd->task->taskptr)(taskpointerarray);
		// TODO
		//long clock5;
	  //clock5 = BAMBOO_GET_EXE_TIME();
	 // tprintf("sort: %d, grab: %d, check: %d \n", (int)(clock2-clock1), (int)(clock3-clock2), (int)(clock4-clock3));

#ifdef PROFILE
#ifdef ACCURATEPROFILE
	  // task finish, set the end of the checkTaskInfo
	  profileTaskEnd();
	  // new a PostTaskInfo for the post-task execution
	  profileTaskStart("post task execution");
#endif
#endif
#ifdef DEBUG
	  BAMBOO_DEBUGPRINT(0xe998);
	  BAMBOO_DEBUGPRINT_REG(islock);
#endif

	  if(islock) {
#ifdef DEBUG
		  BAMBOO_DEBUGPRINT(0xe999);
#endif 
	    for(i = 0; i < runtime_locklen; ++i) {
				void * ptr = (void *)(runtime_locks[i].redirectlock);
	      int * lock = (int *)(runtime_locks[i].value);
#ifdef DEBUG
		  BAMBOO_DEBUGPRINT_REG((int)ptr);
		  BAMBOO_DEBUGPRINT_REG((int)lock);
			BAMBOO_DEBUGPRINT_REG(*((int*)lock+5));
#endif
#ifndef MULTICORE_GC
		  if(RuntimeHashcontainskey(lockRedirectTbl, (int)lock)) {
			  int redirectlock;
			  RuntimeHashget(lockRedirectTbl, (int)lock, &redirectlock);
			  RuntimeHashremovekey(lockRedirectTbl, (int)lock);
			  releasewritelock_r(lock, (int *)redirectlock);
		  } else {
#else
				{
#endif
		releasewritelock(ptr);
		  }
	    }
	  } // line 3015: if(islock)

		//long clock6;
	  //clock6 = BAMBOO_GET_EXE_TIME();
	  //tprintf("sort: %d, grab: %d, check: %d \n", (int)(clock2-clock1), (int)(clock3-clock2), (int)(clock4-clock3));

#ifdef PROFILE
	  // post task execution finish, set the end of the postTaskInfo
	  profileTaskEnd();
#endif

	  // Free up task parameter descriptor
	  RUNFREE(currtpd->parameterArray);
	  RUNFREE(currtpd);
		currtpd = NULL;
#ifdef DEBUG
	  BAMBOO_DEBUGPRINT(0xe99a);
#endif
	  //long clock7;
	  //clock7 = BAMBOO_GET_EXE_TIME();
	  //tprintf("sort: %d, grab: %d, check: %d, release: %d, other %d \n", (int)(clock2-clock1), (int)(clock3-clock2), (int)(clock4-clock3), (int)(clock6-clock5), (int)(clock7-clock6));

      } //  
    //} //  if (hashsize(activetasks)>0)  
  } //  while(hashsize(activetasks)>0)
#ifdef DEBUG
  BAMBOO_DEBUGPRINT(0xe99b);
#endif
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
    //int tagid=pd->tagarray[2*i+1];
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
	    processobject(parameter, i, pd, &iteratorcount, statusarray, 
					          numparams);
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
#else
	  ;
#endif
	} else {
	  int tagindex=0;
	  struct ArrayObject *ao=(struct ArrayObject *)tagptr;
	  for(; tagindex<ao->___cachedCode___; tagindex++) {
#ifndef RAW
		  printf("      tag=%lx\n",ARRAYGET(ao, struct ___TagDescriptor___*, 
						 tagindex));
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
      for(tagindex=it->tagobjindex;tagindex<ao->___cachedCode___;tagindex++) {
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

#ifdef PROFILE
inline void profileTaskStart(char * taskname) {
  if(!taskInfoOverflow) {
	  TaskInfo* taskInfo = RUNMALLOC(sizeof(struct task_info));
	  taskInfoArray[taskInfoIndex] = taskInfo;
	  taskInfo->taskName = taskname;
	  taskInfo->startTime = BAMBOO_GET_EXE_TIME();
	  taskInfo->endTime = -1;
	  taskInfo->exitIndex = -1;
	  taskInfo->newObjs = NULL;
  }
}

inline void profileTaskEnd() {
  if(!taskInfoOverflow) {
	  taskInfoArray[taskInfoIndex]->endTime = BAMBOO_GET_EXE_TIME();
	  taskInfoIndex++;
	  if(taskInfoIndex == TASKINFOLENGTH) {
		  taskInfoOverflow = true;
		  //taskInfoIndex = 0;
	  }
  }
}

// output the profiling data
void outputProfileData() {
#ifdef USEIO
  int i;
  unsigned long long totaltasktime = 0;
  unsigned long long preprocessingtime = 0;
  unsigned long long objqueuecheckingtime = 0;
  unsigned long long postprocessingtime = 0;
  //int interruptiontime = 0;
  unsigned long long other = 0;
  unsigned long long averagetasktime = 0;
  int tasknum = 0;

  printf("Task Name, Start Time, End Time, Duration, Exit Index(, NewObj Name, Num)+\n");
  // output task related info
  for(i = 0; i < taskInfoIndex; i++) {
    TaskInfo* tmpTInfo = taskInfoArray[i];
    unsigned long long duration = tmpTInfo->endTime - tmpTInfo->startTime;
    printf("%s, %lld, %lld, %lld, %lld", 
			tmpTInfo->taskName, tmpTInfo->startTime, tmpTInfo->endTime, 
			duration, tmpTInfo->exitIndex);
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
			//printf(stderr, "new obj!\n");
		}

		// output all new obj info
		iter = RuntimeHashcreateiterator(nobjtbl);
		while(RunhasNext(iter)) {
			char * objtype = (char *)Runkey(iter);
			int num = Runnext(iter);
			printf(", %s, %d", objtype, num);
		}
	}
	printf("\n");
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
    printf("Caution: task info overflow!\n");
  }

  other = totalexetime-totaltasktime-preprocessingtime-postprocessingtime;
  averagetasktime /= tasknum;

  printf("\nTotal time: %lld\n", totalexetime);
  printf("Total task execution time: %lld (%d%%)\n", totaltasktime, 
			   (int)(((double)totaltasktime/(double)totalexetime)*100));
  printf("Total objqueue checking time: %lld (%d%%)\n", 
			   objqueuecheckingtime, 
				 (int)(((double)objqueuecheckingtime/(double)totalexetime)*100));
  printf("Total pre-processing time: %lld (%d%%)\n", preprocessingtime, 
			   (int)(((double)preprocessingtime/(double)totalexetime)*100));
  printf("Total post-processing time: %lld (%d%%)\n", postprocessingtime, 
			   (int)(((double)postprocessingtime/(double)totalexetime)*100));
  printf("Other time: %lld (%d%%)\n", other, 
			   (int)(((double)other/(double)totalexetime)*100));

  printf("\nAverage task execution time: %lld\n", averagetasktime);
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
#endif  // #ifdef PROFILE

#endif
