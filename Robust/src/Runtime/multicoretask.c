#ifdef TASK
#include "runtime.h"
#include "multicoreruntime.h"
#include "runtime_arch.h"
#include "GenericHashtable.h"

//  data structures for task invocation
struct genhashtable * activetasks;
struct taskparamdescriptor * currtpd;

// specific functions used inside critical sections
void enqueueObject_I(void * ptr, 
		                 struct parameterwrapper ** queues, 
										 int length);
int enqueuetasks_I(struct parameterwrapper *parameter, 
		               struct parameterwrapper *prevptr, 
									 struct ___Object___ *ptr, 
									 int * enterflags, 
									 int numenterflags);

inline void initruntimedata() {
	int i;
	// initialize the arrays
  if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
    // startup core to initialize corestatus[]
    for(i = 0; i < NUMCORES; ++i) {
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
			gcloads[i] = 0;
			gcrequiredmems[i] = 0;
			gcstopblock[i] = 0;
#endif
    } // for(i = 0; i < NUMCORES; ++i)
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
  msgtype = -1;
  msgdataindex = 0;
  msglength = BAMBOO_MSG_BUF_LENGTH;
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

#ifdef MULTICORE_GC
	gcflag = false;
	gcprocessing = false;
	gcphase = FINISHPHASE;
	gcself_numsendobjs = 0;
	gcself_numreceiveobjs = 0;
	gcmarkedptrbound = 0;
	gcpointertbl = allocateRuntimeHash(20);
	gcobj2map = 0;
	gcmappedobj = 0;
	gcismapped = false;
	gcnumlobjs = 0;
	gcheaptop = 0;
	gctopcore = 0;
	gcheapdirection = 1;
	gcreservedsb = 0;
	gcmovestartaddr = 0;
	gctomove = false;
	gcstopblock = 0;
	gchead = gctail = gctail2 = NULL;
	gclobjhead = gclobjtail = gclobjtail2 = NULL;
	gcheadindex=0;
	gctailindex=0;
	gctailindex2 = 0;
	gclobjheadindex=0;
	gclobjtailindex=0;
	gclobjtailindex2 = 0;
	gcmovepending = 0;
	gcblocks2fill = 0;
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

#ifdef PROFILE
  stall = false;
  //isInterrupt = true;
  totalexetime = -1;
  taskInfoIndex = 0;
  taskInfoOverflow = false;
  /*interruptInfoIndex = 0;
  interruptInfoOverflow = false;*/
#endif
}

inline void disruntimedata() {
#ifdef MULTICORE_GC
	freeRuntimeHash(gcpointertbl);
#else
	freeRuntimeHash(lockRedirectTbl);
	freeRuntimeHash(objRedirectLockTbl);
	RUNFREE(locktable.bucket);
#endif
	genfreehashtable(activetasks);
	RUNFREE(currtpd);
}

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

inline void checkCoreStatus() {
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
		} // for(i = 0; i < NUMCORES; ++i)
		if(allStall) {
			// check if the sum of send objs and receive obj are the same
			// yes->check if the info is the latest; no->go on executing
			sumsendobj = 0;
			for(i = 0; i < NUMCORES; ++i) {
				sumsendobj += numsendobjs[i];
#ifdef DEBUG
				BAMBOO_DEBUGPRINT(0xf000 + numsendobjs[i]);
#endif
			} // for(i = 0; i < NUMCORES; ++i)	
			for(i = 0; i < NUMCORES; ++i) {
				sumsendobj -= numreceiveobjs[i];
#ifdef DEBUG
				BAMBOO_DEBUGPRINT(0xf000 + numreceiveobjs[i]);
#endif
			} // for(i = 0; i < NUMCORES; ++i)
			if(0 == sumsendobj) {
				if(!waitconfirm) {
					// the first time found all cores stall
					// send out status confirm msg to all other cores
					// reset the corestatus array too
#ifdef DEBUG
					BAMBOO_DEBUGPRINT(0xee05);
#endif
					corestatus[BAMBOO_NUM_OF_CORE] = 1;
					for(i = 1; i < NUMCORES; ++i) {	
						corestatus[i] = 1;
						// send status confirm msg to core i
						send_msg_1(i, STATUSCONFIRM);
					} // for(i = 1; i < NUMCORES; ++i)
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
					BAMBOO_DEBUGPRINT(BAMBOO_GET_EXE_TIME());
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
					for(i = 1; i < NUMCORES; ++i) {
						// send profile request msg to core i
						send_msg_2(i, PROFILEOUTPUT, totalexetime);
					} // for(i = 1; i < NUMCORES; ++i)
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
						}  // for(i = 0; i < NUMCORES; ++i)
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
  if(BAMBOO_NUM_OF_CORE > NUMCORES - 1) {
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
							  send_msg_4(STARTUPCORE, 1, BAMBOO_NUM_OF_CORE, 
										       self_numsendobjs, self_numreceiveobjs);
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
  } // if(BAMBOO_NUM_OF_CORE > NUMCORES - 1)

} // run()

void createstartupobject(int argc, 
		                     char ** argv) {
  int i;

  /* Allocate startup object     */
#ifdef MULTICORE_GC
  struct ___StartupObject___ *startupobject=
		(struct ___StartupObject___*) allocate_new(NULL, STARTUPTYPE);
  struct ArrayObject * stringarray=
		allocate_newarray(NULL, STRINGARRAYTYPE, argc-1);
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
    struct ___String___ *newstring=NewString(NULL, argv[i],length);
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
		allocate_newarray(OBJECTARRAYTYPE,OBJECTARRAYINTERVAL);
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
    if(BAMBOO_NUM_OF_CORE < NUMCORES) {
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
		if(BAMBOO_NUM_OF_CORE > NUMCORES - 1) {
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
		if(BAMBOO_NUM_OF_CORE > NUMCORES - 1) {
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

void * smemalloc(int size, 
		             int * allocsize) {
#ifdef MULTICORE_GC
	// go through free mem list for suitable blocks
	struct freeMemItem * freemem = bamboo_free_mem_list->head;
	struct freeMemItem * prev = NULL;
	do {
		if(freemem->size > size) {
			// found one
			break;
		}
		prev = freemem;
		freemem = freemem->next;
	} while(freemem != NULL);
	if(freemem != NULL) {
		void * mem = (void *)(freemem->ptr);
		*allocsize = size;
		freemem->ptr = ((void*)freemem->ptr) + size;
		freemem->size -= size;
		// check how many blocks it acrosses
		int b = 0;
		BLOCKINDEX(mem, &b);
		// check the remaining space in this block
		int remain = (b < NUMCORES? (b+1)*BAMBOO_SMEM_SIZE_L  
				        : BAMBOO_LARGE_SMEM_BOUND+(b-NUMCORES+1)*BAMBOO_SMEM_SIZE)
			          -(mem-BAMBOO_BASE_VA);
		if(remain < size) {
			// this object acrosses blocks
			int tmpsbs = 1+(size-remain-1)/BAMBOO_SMEM_SIZE;
			for(int k = 0; k < tmpsbs-1; k++) {
				sbstarttbl[k+b] = (INTPTR)(-1);
			}
			if((size-remain)%BAMBOO_SMEM_SIZE == 0) {
				sbstarttbl[b+tmpsbs-1] = (INTPTR)(-1);
			} else {
				sbstarttbl[b+tmpsbs-1] = (INTPTR)(mem+size);
			}
		}
	} else {
#else
	void * mem = mspace_calloc(bamboo_free_msp, 1, size);
	*allocsize = size;
	if(mem == NULL) {
#endif
		// no enough shared global memory
		*allocsize = 0;
#ifdef MULTICORE_GC
		gcflag = true;
		gcrequiredmem = size;
		return NULL;
#else
		BAMBOO_DEBUGPRINT(0xa016);
		BAMBOO_EXIT(0xa016);
#endif
	}
	return mem;
}

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
  
msg:
  if(receiveMsg() == -1) {
	  return -1;
  }

  if(msgdataindex == msglength) {
    // received a whole msg
    MSGTYPE type; 
    type = msgdata[0];
    switch(type) {
    case TRANSOBJ: {
      // receive a object transfer msg
      struct transObjInfo * transObj = 
				RUNMALLOC_I(sizeof(struct transObjInfo));
      int k = 0;
#ifdef DEBUG
#ifndef TILERA
			BAMBOO_DEBUGPRINT(0xe880);
#endif
#endif
      if(BAMBOO_NUM_OF_CORE > NUMCORES - 1) {
#ifndef TILERA
				BAMBOO_DEBUGPRINT_REG(msgdata[2]);
#endif
				BAMBOO_EXIT(0xa005);
			} 
      // store the object and its corresponding queue info, enqueue it later
      transObj->objptr = (void *)msgdata[2]; 
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
				struct QueueItem * qitem = getHead(&objqueue);
				struct QueueItem * prev = NULL;
				while(qitem != NULL) {
					struct transObjInfo * tmpinfo = 
						(struct transObjInfo *)(qitem->objectptr);
					if(tmpinfo->objptr == transObj->objptr) {
						// the same object, remove outdate one
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
      break;
    }

    case TRANSTALL: {
      // receive a stall msg
      if(BAMBOO_NUM_OF_CORE != STARTUPCORE) {
		  // non startup core can not receive stall msg
#ifndef TILERA
				BAMBOO_DEBUGPRINT_REG(msgdata[1]);
#endif
				BAMBOO_EXIT(0xa006);
      } 
      if(msgdata[1] < NUMCORES) {
#ifdef DEBUG
#ifndef TILERA
				BAMBOO_DEBUGPRINT(0xe881);
#endif
#endif
				corestatus[msgdata[1]] = 0;
				numsendobjs[msgdata[1]] = msgdata[2];
				numreceiveobjs[msgdata[1]] = msgdata[3];
      }
      break;
    }

// GC version have no lock msgs
#ifndef MULTICORE_GC
    case LOCKREQUEST: {
      // receive lock request msg, handle it right now
      // check to see if there is a lock exist for the required obj
			// msgdata[1] -> lock type
			int data2 = msgdata[2]; // obj pointer
      int data3 = msgdata[3]; // lock
			int data4 = msgdata[4]; // request core
			// -1: redirected, 0: approved, 1: denied
      deny = processlockrequest(msgdata[1], data3, data2, 
					                      data4, data4, true);  
			if(deny == -1) {
				// this lock request is redirected
				break;
			} else {
				// send response msg
				// for 32 bit machine, the size is always 4 words
				int tmp = deny==1?LOCKDENY:LOCKGROUNT;
				if(isMsgSending) {
					cache_msg_4(data4, tmp, msgdata[1], data2, data3);
				} else {
					send_msg_4(data4, tmp, msgdata[1], data2, data3);
				}
			}
      break;
    }

    case LOCKGROUNT: {
      // receive lock grount msg
      if(BAMBOO_NUM_OF_CORE > NUMCORES - 1) {
#ifndef TILERA
				BAMBOO_DEBUGPRINT_REG(msgdata[2]);
#endif
				BAMBOO_EXIT(0xa007);
      } 
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

    case LOCKDENY: {
      // receive lock deny msg
      if(BAMBOO_NUM_OF_CORE > NUMCORES - 1) {
#ifndef TILERA
				BAMBOO_DEBUGPRINT_REG(msgdata[2]);
#endif
				BAMBOO_EXIT(0xa009);
      } 
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

    case LOCKRELEASE: {
      // receive lock release msg
			processlockrelease(msgdata[1], msgdata[2], 0, false);
      break;
    }
#endif

#ifdef PROFILE
    case PROFILEOUTPUT: {
      // receive an output profile data request msg
      if(BAMBOO_NUM_OF_CORE == STARTUPCORE) {
				// startup core can not receive profile output finish msg
				BAMBOO_EXIT(0xa00c);
      }
#ifdef DEBUG
#ifndef TILEAR
			BAMBOO_DEBUGPRINT(0xe885);
#endif
#endif
			stall = true;
			totalexetime = msgdata[1];
			outputProfileData();
			if(isMsgSending) {
				cache_msg_2(STARTUPCORE, PROFILEFINISH, BAMBOO_NUM_OF_CORE);
			} else {
				send_msg_2(STARTUPCORE, PROFILEFINISH, BAMBOO_NUM_OF_CORE);
			}
      break;
    }

    case PROFILEFINISH: {
      // receive a profile output finish msg
      if(BAMBOO_NUM_OF_CORE != STARTUPCORE) {
				// non startup core can not receive profile output finish msg
#ifndef TILERA
				BAMBOO_DEBUGPRINT_REG(msgdata[1]);
#endif
				BAMBOO_EXIT(0xa00d);
      }
#ifdef DEBUG
#ifndef TILERA
			BAMBOO_DEBUGPRINT(0xe886);
#endif
#endif
			profilestatus[msgdata[1]] = 0;
      break;
    }
#endif

// GC version has no lock msgs
#ifndef MULTICORE_GC
	case REDIRECTLOCK: {
	  // receive a redirect lock request msg, handle it right now
		// check to see if there is a lock exist for the required obj
	  int data1 = msgdata[1]; // lock type
	  int data2 = msgdata[2]; // obj pointer
		int data3 = msgdata[3]; // redirect lock
	  int data4 = msgdata[4]; // root request core
	  int data5 = msgdata[5]; // request core
	  deny = processlockrequest(msgdata[1], data3, data2, data5, data4, true);
	  if(deny == -1) {
		  // this lock request is redirected
		  break;
	  } else {
		  // send response msg
		  // for 32 bit machine, the size is always 4 words
		  if(isMsgSending) {
			  cache_msg_4(data4, deny==1?REDIRECTDENY:REDIRECTGROUNT, 
						        data1, data2, data3);
		  } else {
			  send_msg_4(data4, deny==1?REDIRECTDENY:REDIRECTGROUNT, 
						       data1, data2, data3);
		  }
	  }
	  break;
	}

	case REDIRECTGROUNT: {
		// receive a lock grant msg with redirect info
		if(BAMBOO_NUM_OF_CORE > NUMCORES - 1) {
#ifndef TILERA
			BAMBOO_DEBUGPRINT_REG(msgdata[2]);
#endif
			BAMBOO_EXIT(0xa00e);
		}
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
	
	case REDIRECTDENY: {
	  // receive a lock deny msg with redirect info
	  if(BAMBOO_NUM_OF_CORE > NUMCORES - 1) {
#ifndef TILERA
		  BAMBOO_DEBUGPRINT_REG(msgdata[2]);
#endif
		  BAMBOO_EXIT(0xa010);
	  }
		if(lockobj == msgdata[2]) {
#ifdef DEBUG
#ifndef TILERA
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
#ifndef TILERA
		  BAMBOO_DEBUGPRINT_REG(msgdata[2]);
#endif
		  BAMBOO_EXIT(0xa011);
		}
		break;
	}

	case REDIRECTRELEASE: {
	  // receive a lock release msg with redirect info
		processlockrelease(msgdata[1], msgdata[2], msgdata[3], true);
		break;
	}
#endif
	
	case STATUSCONFIRM: {
      // receive a status confirm info
	  if((BAMBOO_NUM_OF_CORE == STARTUPCORE) 
				|| (BAMBOO_NUM_OF_CORE > NUMCORES - 1)) {
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
			  cache_msg_5(STARTUPCORE, STATUSREPORT, 
						        busystatus?1:0, BAMBOO_NUM_OF_CORE,
										self_numsendobjs, self_numreceiveobjs);
		  } else {
			  send_msg_5(STARTUPCORE, STATUSREPORT, 
						       busystatus?1:0, BAMBOO_NUM_OF_CORE,
									 self_numsendobjs, self_numreceiveobjs);
		  }
		}
	  break;
	}

	case STATUSREPORT: {
	  // receive a status confirm info
	  if(BAMBOO_NUM_OF_CORE != STARTUPCORE) {
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
			numsendobjs[msgdata[1]] = msgdata[2];
			numreceiveobjs[msgdata[1]] = msgdata[3];
		}
	  break;
	}

	case TERMINATE: {
	  // receive a terminate msg
#ifdef DEBUG
#ifndef TILERA
		BAMBOO_DEBUGPRINT(0xe889);
#endif
#endif
		disruntimedata();
		BAMBOO_EXIT(0);
	  break;
	}

	case MEMREQUEST: {
	  // receive a shared memory request msg
	  if(BAMBOO_NUM_OF_CORE != STARTUPCORE) {
		  // wrong core to receive such msg
#ifndef TILERA
		  BAMBOO_DEBUGPRINT_REG(msgdata[2]);
#endif
		  BAMBOO_EXIT(0xa015);
		} else {
#ifdef DEBUG
#ifndef TILERA
		  BAMBOO_DEBUGPRINT(0xe88a);
#endif
#endif
#ifdef MULTICORE_GC
			if(gcprocessing) {
				// is currently doing gc, dump this msg
				break;
			}
#endif
			int allocsize = 0;
		  void * mem = smemalloc(msgdata[1], &allocsize);
			if(mem == NULL) {
				break;
			}
			// send the start_va to request core
			if(isMsgSending) {
				cache_msg_3(msgdata[2], MEMRESPONSE, mem, allocsize);
			} else {
				send_msg_3( msgdata[2], MEMRESPONSE, mem, allocsize);
			} 
		}
	  break;
	}

	case MEMRESPONSE: {
		// receive a shared memory response msg
#ifdef DEBUG
#ifndef TILERA
	  BAMBOO_DEBUGPRINT(0xe88b);
#endif
#endif
#ifdef MULTICORE_GC
		if(gcprocessing) {
			// is currently doing gc, dump this msg
			break;
		}
#endif
	  if(msgdata[2] == 0) {
		  bamboo_smem_size = 0;
		  bamboo_cur_msp = 0;
	  } else {
			// fill header to store the size of this mem block
			(*((int*)msgdata[1])) = msgdata[2];
		  bamboo_smem_size = msgdata[2] - BAMBOO_CACHE_LINE_SIZE;
#ifdef MULTICORE_GC
			bamboo_cur_msp = msgdata[1] + BAMBOO_CACHE_LINE_SIZE;
#else
		  bamboo_cur_msp = 
				create_mspace_with_base((void*)(msgdata[1]+BAMBOO_CACHE_LINE_SIZE),
				                         msgdata[2]-BAMBOO_CACHE_LINE_SIZE, 
																 0);
#endif
	  }
	  smemflag = true;
	  break;
	}

#ifdef MULTICORE_GC
	// GC msgs
	case GCSTART: {
		// receive a start GC msg
#ifdef DEBUG
#ifndef TILERA
	  BAMBOO_DEBUGPRINT(0xe88c);
#endif
#endif
	  // set the GC flag
		gcflag = true;
		gcphase = MARKPHASE;
		if(!smemflag) {
			// is waiting for response of mem request
			// let it return NULL and start gc
			bamboo_smem_size = 0;
			bamboo_cur_msp = NULL;
			smemflag = true;
		}
	  break;
	}

	case GCSTARTCOMPACT: {
		// a compact phase start msg
		gcblocks2fill = msgdata[1];
		gcphase = COMPACTPHASE;
		break;
	}

	case GCSTARTFLUSH: {
		// received a flush phase start msg
		gcphase = FLUSHPHASE;
		break;
	}

	case GCFINISHMARK: {
		// received a mark phase finish msg
		if(BAMBOO_NUM_OF_CORE != STARTUPCORE) {
		  // non startup core can not receive this msg
#ifndef TILERA
		  BAMBOO_DEBUGPRINT_REG(msgdata[1]);
#endif
		  BAMBOO_EXIT(0xb006);
		} 
		if(msgdata[1] < NUMCORES) {
			gccorestatus[msgdata[1]] = 0;
			gcnumsendobjs[msgdata[1]] = gcmsgdata[2];
			gcnumreceiveobjs[msgdata[1]] = gcmsgdata[3];
		}
	  break;
	}
	
	case GCFINISHCOMPACT: {
		// received a compact phase finish msg
		if(BAMBOO_NUM_OF_CORE != STARTUPCORE) {
		  // non startup core can not receive this msg
		  // return -1
#ifndef TILERA
		  BAMBOO_DEBUGPRINT_REG(msgdata[1]);
#endif
		  BAMBOO_EXIT(0xb006);
		}
		int cnum = msgdata[1];
		int filledblocks = msgdata[2];
		int heaptop = msgdata[3];
		int data4 = msgdata[4];
		if(cnum < NUMCORES) {
			if(COMPACTPHASE == gcphase) {
				gcfilledblocks[cnum] = filledblocks;
				gcloads[cnum] = heaptop;
			}
			if(data4 > 0) {
				// ask for more mem
				int startaddr = 0;
				int tomove = 0;
				int dstcore = 0;
				if(findSpareMem(&startaddr, &tomove, &dstcore, data4, cnum)) {
					send_msg_4(cnum, GCMOVESTART, dstcore, startaddr, tomove);
				}
			} else {
				gccorestatus[cnum] = 0;
				// check if there is pending move request
				if(gcmovepending > 0) {
					int j;
					for(j = 0; j < NUMCORES; j++) {
						if(gcrequiredmems[j]>0) {
							break;
						}
					}
					if(j < NUMCORES) {
						// find match
						int tomove = 0;
						int startaddr = 0;
						gcrequiredmems[j] = assignSpareMem(cnum, 
																							 gcrequiredmems[j], 
																							 &tomove, 
																							 &startaddr);
						if(STARTUPCORE == j) {
							gcdstcore = cnum;
							gctomove = true;
							gcmovestartaddr = startaddr;
							gcblock2fill = tomove;
						} else {
							send_msg_4(j, GCMOVESTART, cnum, startaddr, tomove);
						} // if(STARTUPCORE == j)
						if(gcrequiredmems[j] == 0) {
							gcmovepending--;
						}
					} // if(j < NUMCORES)
				} // if(gcmovepending > 0)
			} // if(flag == 0)
		} // if(cnum < NUMCORES)
	  break;
	}

	case GCFINISHFLUSH: {
		// received a flush phase finish msg
		if(BAMBOO_NUM_OF_CORE != STARTUPCORE) {
		  // non startup core can not receive this msg
		  // return -1
#ifndef TILERA
		  BAMBOO_DEBUGPRINT_REG(msgdata[1]);
#endif
		  BAMBOO_EXIT(0xb006);
		} 
		if(msgdata[1] < NUMCORES) {
		  gccorestatus[msgdata[1]] = 0;
		}
	  break;
	}

	case GCFINISH: {
		// received a GC finish msg
		gcphase = FINISHPHASE;
		break;
	}

	case GCMARKCONFIRM: {
		// received a marked phase finish confirm request msg
		if((BAMBOO_NUM_OF_CORE == STARTUPCORE) 
				|| (BAMBOO_NUM_OF_CORE > NUMCORES - 1)) {
		  // wrong core to receive such msg
		  BAMBOO_EXIT(0xa013);
		} else {
		  // send response msg
		  if(isMsgSending) {
			  cache_msg_5(STARTUPCORE, GCMARKREPORT, BAMBOO_NUM_OF_CORE, 
						        gcbusystatus, gcself_numsendobjs, 
										gcself_numreceiveobjs);
		  } else {
			  send_msg_5(STARTUPCORE, GCMARKREPORT, BAMBOO_NUM_OF_CORE, 
						       gcbusystatus, gcself_numsendobjs, gcself_numreceiveobjs);
		  }
		}
	  break;
	}

	case GCMARKREPORT: {
		// received a marked phase finish confirm response msg
		if(BAMBOO_NUM_OF_CORE != STARTUPCORE) {
		  // wrong core to receive such msg
#ifndef TILERA
		  BAMBOO_DEBUGPRINT_REG(msgdata[2]);
#endif
		  BAMBOO_EXIT(0xb014);
		} else {
		  if(waitconfirm) {
			  numconfirm--;
		  }
		  gccorestatus[msgdata[1]] = gcmsgdata[2];
		  gcnumsendobjs[msgdata[1]] = gcmsgdata[3];
		  gcnumreceiveobjs[msgdata[1]] = gcmsgdata[4];
		}
	  break;
	}

	case GCMARKEDOBJ: {
		// received a markedObj msg
		gc_enqueue(msgdata[1]);
		gcself_numreceiveobjs++;
		gcbusystatus = true;
		break;
	}

	case GCMOVESTART: {
		// received a start moving objs msg
		gctomove = true;
		gcdstcore = msgdata[1];
		gcmovestartaddr = msgdata[2];
		gcblock2fill = msgdata[3];
		break;
	}
	
	case GCMAPREQUEST: {
		// received a mapping info request msg
		void * dstptr = NULL;
		RuntimeHashget(gcpointertbl, msgdata[1], &dstptr);
		if(NULL == dstptr) {
			// no such pointer in this core, something is wrong
			BAMBOO_EXIT(0xb008);
		} else {
			// send back the mapping info
			if(isMsgSending) {
				cache_msg_3(msgdata[2], GCMAPINFO, msgdata[1], (int)dstptr);
			} else {
				send_msg_3(msgdata[2], GCMAPINFO, msgdata[1], (int)dstptr);
			}
		}
		break;
	}

	case GCMAPINFO: {
		// received a mapping info response msg
		if(msgdata[1] != gcobj2map) {
			// obj not matched, something is wrong
			BAMBOO_EXIT(0xb009);
		} else {
			gcmappedobj = msgdata[2];
			RuntimeHashadd(gcpointertbl, gcobj2map, gcmappedobj);
		}
		gcismapped = true;
		break;
	}

	case GCLOBJREQUEST: {
		// received a large objs info request msg
		transferMarkResults();
		break;
	}

	case GCLOBJINFO: {
		// received a large objs info response msg
		waitconfirm--;

		if(BAMBOO_NUM_OF_CORE > NUMCORES - 1) {
#ifndef TILERA
			BAMBOO_DEBUGPRINT_REG(msgdata[2]);
#endif
			BAMBOO_EXIT(0xa005);
		} 
		// store the mark result info 
		int cnum = msgdata[2];
		gcloads[cnum] = msgdata[3];
		if(gcheaptop < msgdata[4]) {
			gcheaptop = msgdata[4];
		}
		// large obj info here
	  for(int k = 5; k < msgdata[1];) {
			gc_lobjenqueue(msgdata[k++], msgdata[k++], cnum, NULL);
		} // for(int k = 5; k < msgdata[1];)
		break;
	}
	
	case GCLOBJMAPPING: {
		// received a large obj mapping info msg
		RuntimeHashadd(gcpointertbl, msgdata[1], msgdata[2]);
		break;
	}

#endif

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
	BAMBOO_DEBUGPRINT(0xe88d);
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
	return (int)type;
} else {
	// not a whole msg
#ifdef DEBUG
#ifndef TILERA
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
      BAMBOO_EXIT(0xa01d);
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
	  send_msg_4(targetcore, REDIRECTRELEASE, 1, (int)lock, (int)redirectlock);
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

  struct LockValue locks[MAXTASKPARAMS];
  int locklen = 0;
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
    if (hashsize(activetasks)>0) {
      int i;
#ifdef PROFILE
#ifdef ACCURATEPROFILE
	  profileTaskStart("tpd checking");
#endif
#endif
	  busystatus = true;
      currtpd=(struct taskparamdescriptor *) getfirstkey(activetasks);
      genfreekey(activetasks, currtpd);

      numparams=currtpd->task->numParameters;
      numtotal=currtpd->task->numTotal;

	  // clear the lockRedirectTbl 
		// (TODO, this table should be empty after all locks are released)
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
#ifdef ACCURATEPROFILE
			  // fail, set the end of the checkTaskInfo
			  profileTaskEnd();
#endif
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
	    ObjectHashget(pw->objectset, (int) parameter, (int *) &next, 
					          (int *) &enterflags, &UNUSED, &UNUSED2);
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
	  /* Actually call task */
#ifdef MULTICORE_GC
	  ((int *)taskpointerarray)[0]=currtpd->numParameters;
	  taskpointerarray[1]=NULL;
#endif
execute:
#ifdef PROFILE
#ifdef ACCURATEPROFILE
	  // check finish, set the end of the checkTaskInfo
	  profileTaskEnd();
#endif
	  profileTaskStart(currtpd->task->name);
#endif

#ifdef DEBUG
		BAMBOO_DEBUGPRINT(0xe997);
#endif
		((void(*) (void **))currtpd->task->taskptr)(taskpointerarray);
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
	    for(i = 0; i < locklen; ++i) {
				void * ptr = (void *)(locks[i].redirectlock);
	      int * lock = (int *)(locks[i].value);
#ifdef DEBUG
		  BAMBOO_DEBUGPRINT_REG((int)ptr);
		  BAMBOO_DEBUGPRINT_REG((int)lock);
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

#ifdef PROFILE
	  // post task execution finish, set the end of the postTaskInfo
	  profileTaskEnd();
#endif

	  // Free up task parameter descriptor
	  RUNFREE(currtpd->parameterArray);
	  RUNFREE(currtpd);
#ifdef DEBUG
	  BAMBOO_DEBUGPRINT(0xe99a);
#endif
      } //  
    } //  if (hashsize(activetasks)>0)  
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
  if(BAMBOO_NUM_OF_CORE > NUMCORES - 1) {
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
  if(BAMBOO_NUM_OF_CORE > NUMCORES - 1) {
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
      if (ptr==ARRAYGET(ao, struct ___Object___*, j))
	return 1;
    }
    return 0;
  } else
    return objptr==ptr;
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
