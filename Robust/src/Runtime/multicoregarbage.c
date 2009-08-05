#ifdef MULTICORE_GC
#include "multicoregarbage.h"
#include "multicoreruntime.h"
#include "runtime_arch.h"
#include "SimpleHash.h"
#include "GenericHashtable.h"

extern struct genhashtable * activetasks;
extern struct parameterwrapper ** objectqueues[][NUMCLASSES];
extern struct taskparamdescriptor *currtpdo;

struct largeObjList {
	struct largeObjItem * head;
	struct largeObjItem * tail;
};

struct largeObjList lObjList;

#define NUMPTRS 100

void gc_enqueue(void *ptr) {
  if (gcheadindex==NUMPTRS) {
    struct pointerblock * tmp;
    if (gcspare!=NULL) {
      tmp=gcspare;
      gcspare=NULL;
    } else
      tmp=malloc(sizeof(struct pointerblock));
    gchead->next=tmp;
    gchead=tmp;
    gcheadindex=0;
  }
  gchead->ptrs[gcheadindex++]=ptr;
}

// dequeue and destroy the queue
void * gc_dequeue() {
  if (gctailindex==NUMPTRS) {
    struct pointerblock *tmp=tail;
    gctail=gctail->next;
    gctailindex=0;
    if (gcspare!=NULL)
      free(tmp);
    else
      gcspare=tmp;
  }
  return gctail->ptrs[gctailindex++];
}

// dequeue and do not destroy the queue
void * gc_dequeue2() {
	if (gctailindex2==NUMPTRS) {
    struct pointerblock *tmp=tail;
    gctail2=gctail2->next;
    gctailindex2=0;
  }
  return gctail2->ptrs[gctailindex2++];
}

int gc_moreItems() {
  if ((gchead==gctail)&&(gctailindex==gcheadindex))
    return 0;
  return 1;
}

int gc_moreItems2() {
  if ((gchead==gctail2)&&(gctailindex2==gcheadindex))
    return 0;
  return 1;
}

INTPTR curr_heaptop = 0;

bool isLarge(void * ptr, int * ttype, int * tsize) {
	// check if a pointer is referring to a large object
	int type = ((int *)ptr)[0];
	int size = 0;
	if(type < NUMCLASSES) {
		// a normal object
		size = classsize[type];
	} else {	
		// an array 
		struct ArrayObject *ao=(struct ArrayObject *)ptr;
		int elementsize=classsize[type];
		int length=ao->___length___; 
		size=sizeof(struct ArrayObject)+length*elementsize;
	}
	*ttype = type;
	*tsize = size;
	return(!isLocal(ptr + size));
}

int hostcore(void * ptr) {
	// check the host core of ptr
	int host = 0;
	int x = 0;
	int y = 0;
	RESIDECORE(ptr, &x, &y);
	host = (x == 0)?(x * bamboo_height + y) : (x * bamboo_height + y - 2);
	return host;
}

bool isLocal(void * ptr) {
	// check if a pointer is in shared heap on this core
	return hostcore(ptr) == BAMBOO_NUM_OF_CORE;
}

void transferMarkResults() {
	// TODO, need distiguish between send and cache
	// invoked inside interruptiong handler
}

void checkMarkStatue() {
	if((!gcwaitconfirm) || 
			(waitconfirm && (numconfirm == 0))) {
		BAMBOO_START_CRITICAL_SECTION_STATUS();  
		gccorestatus[BAMBOO_NUM_OF_CORE] = 0;
		gcnumsendobjs[BAMBOO_NUM_OF_CORE] = gcself_numsendobjs;
		gcnumreceiveobjs[BAMBOO_NUM_OF_CORE] = gcself_numreceiveobjs;
		// check the status of all cores
		bool allStall = true;
		for(i = 0; i < NUMCORES; ++i) {
			if(gccorestatus[i] != 0) {
				allStall = false;
				break;
			}
		}
		if(allStall) {
			// check if the sum of send objs and receive obj are the same
			// yes->check if the info is the latest; no->go on executing
			int sumsendobj = 0;
			for(i = 0; i < NUMCORES; ++i) {
				sumsendobj += gcnumsendobjs[i];
			}		
			for(i = 0; i < NUMCORES; ++i) {
				sumsendobj -= gcnumreceiveobjs[i];
			}
			if(0 == sumsendobj) {
				if(!waitconfirm) {
					// the first time found all cores stall
					// send out status confirm msg to all other cores
					// reset the corestatus array too
					gccorestatus[BAMBOO_NUM_OF_CORE] = 1;
					waitconfirm = true;
					numconfirm = NUMCORES - 1;
					for(i = 1; i < NUMCORES; ++i) {	
						gccorestatus[i] = 1;
						// send mark phase finish confirm request msg to core i
						send_msg_1(i, GCMARKCONFIRM);
					}
				} else {
					// all the core status info are the latest
					// stop mark phase
					gcphase = COMPACTPHASE;
					// restore the gcstatus for all cores
					for(i = 0; i < NUMCORES; ++i) {
						gccorestatus[i] = 1;
					}
				} // if(!gcwautconfirm) else()
			} // if(0 == sumsendobj)
		} // if(allStall)
		BAMBOO_CLOSE_CRITICAL_SECTION_STATUS();
	} // if((!gcwaitconfirm)...
}

bool preGC() {
	// preparation for gc
	// make sure to clear all incoming msgs espacially transfer obj msgs
	int i;
	if((!waitconfirm) || 
						  (waitconfirm && (numconfirm == 0))) {
		// send out status confirm msgs to all cores to check if there are
		// transfer obj msgs on-the-fly
		waitconfirm = true;
		numconfirm = NUMCORES - 1;
		for(i = 1; i < NUMCORES; ++i) {	
			corestatus[i] = 1;
			// send status confirm msg to core i
			send_msg_1(i, STATUSCONFIRM);
		}

		while(numconfirm != 0) {} // wait for confirmations
		numsendobjs[BAMBOO_NUM_OF_CORE] = self_numsendobjs;
		numreceiveobjs[BAMBOO_NUM_OF_CORE] = self_numreceiveobjs;
		int sumsendobj = 0;
		for(i = 0; i < NUMCORES; ++i) {
			sumsendobj += numsendobjs[i];
		}		
		for(i = 0; i < NUMCORES; ++i) {
			sumsendobj -= numreceiveobjs[i];
		}
		if(0 == sumsendobj) {
			return true;
		} else {
			// still have some transfer obj msgs on-the-fly, can not start gc
			return false;
		}
	} else {
		// previously asked for status confirmation and do not have all the 
		// confirmations yet, can not start gc
		return false;
	}
}

// compute load balance for all cores
void loadbalance() {
	// compute load balance
	// initialize the deltas
	int i;
	int delta = 1 << 32 -1;
	int deltanew = 1 << 32 - 1;
	int lcore = 0;
	int rcore = 0;
	bool stop = true;
	for(i = 0; i < NUMCORES; i++) {
		gcdeltal[i] = gcdeltar[i] = 0;
		gcreloads[i] = gcloads[i];
	}
	do {
		stop = true;
		delta = deltanew;
		// compute load balance
		for(i = 0; i < NUMCORES; i++) {
			if(gcreloads[i] > BAMBOO_SMEM_SIZE_L) {
				// too much load, try to redirect some of it to its neighbours
				LEFTNEIGHBOUR(i, &lcore);
				RIGHTNEIGHBOUR(i, &rcore);
				if(lcore != -1) {
					int tmp = (gcreloads[lcore] - gcreloads[i]) / 2;
					gcdeltal[i] = tmp;
					gcdeltar[lcore] = 0-tmp;
					deltanew += abs(gcreloads[lcore] - gcreloads[i]);
				}
				if(rcore != -1) {
					int tmp = (gcreloads[rcore] - gcreloads[i]) / 2;
					gcdeltar[i] = tmp;
					gcdeltal[rcore] = 0-tmp;
					deltanew += abs(gcreloads[rcore] - gcreloads[i]);
				}
			}
		}
		deltanew /= 2;
		if((deltanew == 0) || (delta == deltanew)) {
			break;
		}
		// flush for new loads
		for(i = 0; i < NUMCORES; i++) {
			if((gcdeltal[i] != 0) || (gcdeltar[i] != 0)) {
				stop = false;
				gcreloads[i] += gcdeltal[i] + gcdeltar[i];
				gcdeltal[i] = gcdeltar[i] = 0;
			}
		}
	} while(!stop);
	for(i = 0; i < NUMCORES; i++) {
		gcdeltal[i] = gcdeltar[i] = 0;
	}
	// decide how to do load balance
	for(i = 0; i < NUMCORES; i++) {
		int tomove = (gcloads[i] - gcreloads[i]);
		if(tomove > 0) {
			LEFTNEIGHBOUR(i, &lcore);
			RIGHTNEIGHBOUR(i, &rcore);
			int lmove = 0;
			int rmove = 0;
			if(lcore != -1) {
				lmove = (gcreloads[lcore] - gcloads[lcore] - gcdeltal[lcore]);
				if(lmove < 0) {
					lmove = 0;
				}
			}
			if(rcore != -1) {
				rmove = (gcreloads[rcore] - gcloads[rcore] - gcdeltar[rcore]);
				if(rmove < 0) {
					rmove = 0;
				}
			}
			// the one with bigger gap has higher priority
			if(lmove > rmove) {
				int ltomove = (lmove > tomove)? tomove:lmove;
				gcdeltar[lcore] = ltomove;
				gcdeltal[i] = 0-ltomove;
				gcdeltal[rcore] = tomove - ltomove;
				gcdeltar[i] = ltomove - tomove;
			} else {
				int rtomove = (rmove > tomove)? tomove:rmove;
				gcdeltal[rcore] = rtomove;
				gcdeltar[i] = 0-rtomove;
				gcdeltar[lcore] = tomove - rtomove;
				gcdeltal[i] = rtomove - tomove;
			}
		}
	}
}

void gc(struct garbagelist * stackptr) {
	// check if do gc
	if(!gcflag) {
		return;
	}

	// core coordinator routine
	if(0 == BAMBOO_NUM_OF_CORE) {
		if(!preGC()) {
			// not ready to do gc
			gcflag = true;
			return;
		}

		int i = 0;
		gcwaitconfirm = false;
		gcwaitconfirm = 0;
		gcphase = MARKPHASE;
		for(i = 1; i < NUMCORES - 1; i++) {
			// send GC start messages to all cores
			send_msg_1(i, GCSTART);
		}
		bool isfirst = true;
		bool allStall = false;

		// mark phase
		while(MARKPHASE == gcphase) {
			mark(isfirst, stackptr);
			if(isfirst) {
				isfirst = false;
			}

			// check gcstatus
			checkMarkStatue(); 
		}  // while(MARKPHASE == gcphase)
		// send msgs to all cores requiring large objs info
		numconfirm = NUMCORES - 1;
		for(i = 1; i < NUMCORES; ++i) {
			send_msg_1(i, GCLOBJREQUEST);
		}	
		while(numconfirm != 0) {} // wait for responses
		loadbalance();
		// TODO need to decide where to put large objects

		// TODO cache all large objects

		for(i = 1; i < NUMCORES; ++i) {
			//TODO send start compact messages to all cores

		}

		// compact phase
		compact();
		gccorestatus[BAMBOO_NUM_OF_CORE] = 0;
		while(COMPACTPHASE == gcphase) {
			// check the status of all cores
			allStall = true;
			for(i = 0; i < NUMCORES; ++i) {
				if(gccorestatus[i] != 0) {
					allStall = false;
					break;
				}
			}	
			if(allStall) {
				// restore the gcstatus of all cores
				for(i = 0; i < NUMCORES; ++i) {
					gccorestatus[i] = 1;
				}
				break;
			}
		} // while(COMPACTPHASE == gcphase)
		// TODO merge all mapping information

		gcphase = FLUSHPHASE;
		for(i = 1; i < NUMCORES; ++i) {
			// send start flush messages to all cores
			send_msg_1(i, GCSTARTFLUSH);
		}

		// flush phase
		flush();
		gccorestatus[BAMBOO_NUM_OF_CORE] = 0;
		while(FLUSHPHASE == gcphase) {
			// check the status of all cores
			allStall = true;
			for(i = 0; i < NUMCORES; ++i) {
				if(gccorestatus[i] != 0) {
					allStall = false;
					break;
				}
			}	
			if(allStall) {
				break;
			}
		} // while(FLUSHPHASE == gcphase)
		gcphase = FINISHPHASE;
		for(i = 1; i < NUMCORES; ++i) {
			// send gc finish messages to all cores
			send_msg_1(i, GCFINISH);
		}
		return;
	} else {
		gc_collect(stackptr);
	}
	gcflag = false;
}

// enqueue root objs
void tomark(struct garbagelist * stackptr) {
	if(MARKPHASE != gcphase) {
		BAMBOO_EXIT(0xb002);
	}
	gcbusystatus = 1;
	// initialize queue
	if (gchead==NULL) {
		gcheadindex=0;
		gctailindex=0;
		gctailindex2 = 0;
		gchead=gctail=gctail2=malloc(sizeof(struct pointerblock));
	}
	int i;
	// enqueue current stack 
	while(stackptr!=NULL) {
		for(i=0; i<stackptr->size; i++) {
			gc_enqueue(stackptr->array[i]);
		}
		stackptr=stackptr->next;
	}
	// enqueue objectsets
	for(i=0; i<NUMCLASSES; i++) {
		struct parameterwrapper ** queues=objectqueues[BAMBOO_NUM_OF_CORE][i];
		int length = numqueues[BAMBOO_NUM_OF_CORE][i];
		for(j = 0; j < length; ++j) {
			struct parameterwrapper * parameter = queues[j];
			struct ObjectHash * set=parameter->objectset;
			struct ObjectNode * ptr=set->listhead;
			while(ptr!=NULL) {
				gc_enqueue((void *)ptr->key);
				ptr=ptr->lnext;
			}
		}
	}
	// euqueue current task descriptor
	for(i=0; i<currtpd->numParameters; i++) {
		gc_enqueue(currtpd->parameterArray[i]);
	}
	// euqueue active tasks
	struct genpointerlist * ptr=activetasks->list;
	while(ptr!=NULL) {
		struct taskparamdescriptor *tpd=ptr->src;
		int i;
		for(i=0; i<tpd->numParameters; i++) {
			gc_enqueue(tpd->parameterArray[i]);
		}
		ptr=ptr->inext;
	}
	// enqueue cached transferred obj
	struct QueueItem * tmpobjptr =  getHead(&objqueue);
	while(tmpobjptr != NULL) {
		struct transObjInfo * objInfo = (struct transObjInfo *)(tmpobjptr->objectptr); 
		gc_enqueue(objInfo->objptr);
		getNextQueueItem(tmpobjptr);
	}
}

void mark(bool isfirst, struct garbagelist * stackptr) {
	if(isfirst) {
		// enqueue root objs
		tomark(stackptr);
	}

	// mark phase
	while(MARKPHASE == gcphase) {
		while(gc_moreItems2()) {
			voit * ptr = gc_dequeue2();
			int size = 0;
			int type = 0;
			if(isLarge(ptr, &type, &size)) {
				// ptr is a large object
				struct largeObjItem * loi = 
					(struct largeObjItem *)RUNMALLOC(sizeof(struct largeObjItem));  
				loi->orig = (INTPTR)ptr;
				loi->dst = (INTPTR)0;
				loi->length = size;
				if(lObjList.head == NULL) {
					lObjList.head = lObjList.tail = loi;
				} else {
					lObjList.tail->next = loi;
					lObjList.tail = loi;
				}
			} else if (isLocal(ptr)) {
				// ptr is an active object on this core
				if(type == -1) {
					// nothing to do 
				}
				curr_heaptop += size;
				// mark this obj
				((int *)ptr)[6] = 1;
			}
			// scan all pointers in ptr
			unsigned INTPTR * pointer;
			pointer=pointerarray[type];
			if (pointer==0) {
				/* Array of primitives */
				/* Do nothing */
			} else if (((INTPTR)pointer)==1) {
				/* Array of pointers */
				struct ArrayObject *ao=(struct ArrayObject *) ptr;
				int length=ao->___length___;
				int j;
				for(j=0; j<length; j++) {
					void *objptr=((void **)(((char *)&ao->___length___)+sizeof(int)))[j];
					int host = hostcore(objptr);
					if(BAMBOO_NUM_OF_CORE == host) {
						// on this core
						gc_enqueue(objptr);  
					} else {
						// send a msg to host informing that objptr is active
						send_msg_2(host, GCMARKEDOBJ, objptr);
						gcself_numsendobjs++;
					}
				}
			} else {
				INTPTR size=pointer[0];
				int i;
				for(i=1; i<=size; i++) {
					unsigned int offset=pointer[i];
					void * objptr=*((void **)(((char *)ptr)+offset));
					int host = hostcore(objptr);
					if(BAMBOO_NUM_OF_CORE == host) {
						// on this core
						gc_enqueue(objptr);  
					} else {
						// send a msg to host informing that objptr is active
						send_msg_2(host, GCMARKEDOBJ, objptr);
						gcself_numsendobjs++;
					}
				}
			}
		} // while(!isEmpty(gctomark))
		gcbusystatus = false;
		// send mark finish msg to core coordinator
		send_msg_4(STARTUPCORE, GCFINISHMARK, BAMBOO_NUM_OF_CORE,
				       gcself_numsendobjs, gcself_numreceiveobjs); 

		if(BAMBOO_NUM_OF_CORE == 0) {
			return;
		}
	} // while(MARKPHASE == gcphase)
} // mark()

void compact() {
	if(COMPACTPHASE != gcphase) {
		BAMBOO_EXIT(0xb003);
	}
	curr_heaptop = 0;
	struct markedObjItem * moi = mObjList.head;
	bool iscopy = true;
	while(moi != NULL) {
		if((cinstruction == NULL) || (cinstruction->tomoveobjs == NULL) 
				|| (curr_heaptop < cinstruction->tomoveobjs->starts[0])) {
			// objs to compact
			int type = ((int *)(moi->orig))[0];
			int size = 0;
			if(type == -1) {
				// do nothing 
			}
			if(type < NUMCLASSES) {
				// a normal object
				size = classsize[type];
				moi->dst = curr_heaptop;
				curr_heaptop += size;
				if(iscopy) {
					memcpy(moi->dst, moi->orig, size);
					genputtable(pointertbl, moi->orig, moi->dst);
				}
			} else {
				// an array 
				struct ArrayObject *ao=(struct ArrayObject *)ptr;
				int elementsize=classsize[type];
				int length=ao->___length___;
				size=sizeof(struct ArrayObject)+length*elementsize;
				moi->dst = curr_heaptop;
				curr_heaptop += size;
				if(iscopy) {
					memcpy(moi->dst, moi->orig, size);
					genputtable(pointertbl, moi->orig, moi->dst);
				}
			}
		} else {
			iscopy = false;;
		}
		moi = moi->next;
	} // while(moi != NULL)
	if(moi == NULL) {
		if(cinstruction->incomingobjs != NULL) {
			for(int j = 0; j < cinstruction->incomingobjs->length; j++) {
				// send messages to corresponding cores to start moving
				send_msg_2(cinstruction->incomingobjs->dsts[j], GCMOVESTART, 
						       BAMBOO_NUM_OF_CORE);
			}
		}
	} else {
		int num_dsts = cinstruction->tomoveobjs->length;
		while(num_dsts > 0) {
			while(!gctomove) {}
			// start moving objects to other cores
			gctomove = 0;
			while(!isEmpty(gcdsts)) {
				int dst = (int)(getItem(gcdsts));
				num_dsts--;
				int j = 0;
				for(j = 0; j < cinstruction->tomoveobjs->length; j++) {
					if(dst == cinstruction->tomoveobjs->dsts[j]) {
						break;
					}
				}
				INTPTR top = cinstruction->tomoveobjs->dststarts[j];
				INTPTR start = cinstruction->tomoveobjs->starts[j];
				INTPTR end = cinstruction->tomoveobjs->ends[j];
				struct markedObjItem * tomove = getStartItem(moi, start);
				do {
					int type = ((int *)(tomove->orig))[0];
					int size = 0;
					if(type == -1) {
						// do nothing
					}
					if(type < NUMCLASSES) {
						// a normal object
						size = classsize[type];
						moi->dst = top;
						top += size;
						memcpy(moi->dst, moi->orig, size);
						genputtable(pointertbl, moi->orig, moi->dst);
					} else {						
						// an array 
						struct ArrayObject *ao=(struct ArrayObject *)ptr;
						int elementsize=classsize[type];
						int length=ao->___length___;
						size=sizeof(struct ArrayObject)+length*elementsize;
						moi->dst = top;
						top += size;
						memcpy(moi->dst, moi->orig, size);
						genputtable(pointertbl, moi->orig, moi->dst);
					}
					tomove = tomove->next;
				} while(tomove->orig < end);
			} // while(!isEmpty(gcdsts))
		} // while(num_dsts > 0)
	} // if(moi == NULL) else()
	if((cinstruction != NULL) && (cinstruction->largeobjs != NULL)) {
		// move all large objects
		do {
			// dequeue the first large obj
			struct largeObjItem * loi = cinstruction->largeobjs;
			cinstruction->largeobjs = loi->next;
			// move this large obj
			memcpy(loi->dst, loi->orig, loi->length);
			genputtable(pointertbl, loi->orig, loi->dst);
			RUNFREE(loi);
		}while(cinstruction->largeobjs != NULL);
	}
	// send compact finish message to core coordinator
	send_msg_2(STARTUPCORE, GCFINISHCOMPACT, BAMBOO_NUM_OF_CORE);
	
} // compact()

void flush() {
	struct markedObjItem * moi = mObjList.head;
	while(moi != NULL) {
		void * ptr = moi->dst;
		int type = ((int *)(ptr))[0];
		// scan all pointers in ptr
		unsigned INTPTR * pointer;
		pointer=pointerarray[type];
		if (pointer==0) {
			/* Array of primitives */
			/* Do nothing */
		} else if (((INTPTR)pointer)==1) {
			/* Array of pointers */
			struct ArrayObject *ao=(struct ArrayObject *) ptr;
			int length=ao->___length___;
			int j;
			for(j=0; j<length; j++) {
				void *objptr=((void **)(((char *)&ao->___length___)+sizeof(int)))[j];
				// change to new address
				void *dstptr = gengettable(pointertbl, objptr);
				if(NULL == dstptr) {
					// send msg to host core for the mapping info
					obj2map = (int)objptr;
					ismapped = false;
					mappedobj = NULL;
					send_msg_3(hostcore(objptr), GCMAPREQUEST, (int)objptr, 
							       BAMBOO_NUM_OF_CORE);
					while(!ismapped) {}
					dstptr = mappedobj;
				}
				((void **)(((char *)&ao->___length___)+sizeof(int)))[j] = dstptr;
			}
		} else {
			INTPTR size=pointer[0];
			int i;
			for(i=1; i<=size; i++) {
				unsigned int offset=pointer[i];
				void * objptr=*((void **)(((char *)ptr)+offset));
				// change to new address
				void *dstptr = gengettable(pointertbl, objptr);
				if(NULL == dstptr) {
					// send msg to host core for the mapping info
					obj2map = (int)objptr;
					ismapped = false;
					mappedobj = NULL;
					send_msg_3(hostcore(objptr), GCMAPREQUEST, (int)objptr, 
							       BAMBOO_NUM_OF_CORE);
					while(!ismapped) {}
					dstptr = mappedobj;
				}
				*((void **)(((char *)ptr)+offset)) = dstptr;
			}
		}
		moi = moi->next;
	} // while(moi != NULL)
	// send flush finish message to core coordinator
	send_msg_2(STARTUPCORE, GCFINISHFLUSH, BAMBOO_NUM_OF_CORE);
	
} // flush()

void gc_collect(struct garbagelist * stackptr) {
	// core collector routine
	mark(true, stackptr);
	compact();
	while(FLUSHPHASE != gcphase) {}
	flush();
	
	while(true) {
		if(FINISHPHASE == gcphase) {
			return;
		}
	}
}

#endif
