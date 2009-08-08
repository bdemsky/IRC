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
INTPTR curr_heapbound = 0;

bool isLarge(void * ptr, 
		         int * ttype, 
						 int * tsize) {
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
	host = (x==0)?(x*bamboo_height+y):(x*bamboo_height+y-2);
	return host;
}

bool isLocal(void * ptr) {
	// check if a pointer is in shared heap on this core
	return hostcore(ptr) == BAMBOO_NUM_OF_CORE;
}

void transferMarkResults() {
	// TODO, need distiguish between send and cache
	// invoked inside interruptiong handler
	int msgsize = 4;
  int i = 0;

	// TODO check large objs here

  isMsgSending = true;
  DynamicHeader msgHdr = tmc_udn_header_from_cpu(STARTUPCORE);

	// send header
  __tmc_udn_send_header_with_size_and_tag(msgHdr, msgsize, 
			                                    UDN0_DEMUX_TAG);  
#ifdef DEBUG
  BAMBOO_DEBUGPRINT(0xbbbb);
  BAMBOO_DEBUGPRINT(0xb000 + STARTUPCORE);       // targetcore
#endif
  udn_send(GCLOBJINFO);
#ifdef DEBUG
  BAMBOO_DEBUGPRINT(GCLOBJINFO);
#endif
  udn_send(msgsize);
#ifdef DEBUG
  BAMBOO_DEBUGPRINT_REG(msgsize);
#endif
	udn_send(BAMBOO_NUM_OF_CORE);
#ifdef DEBUG
  BAMBOO_DEBUGPRINT_REG(BAMBOO_NUM_OF_CORE);
#endif
	udn_send(curr_heaptop);
#ifdef DEBUG
  BAMBOO_DEBUGPRINT_REG(curr_heaptop);
#endif
	// TODO large objs here
	
#ifdef DEBUG
  BAMBOO_DEBUGPRINT(0xffff);
#endif

  // end of sending this msg, set sand msg flag false
  isMsgSending = false;
  send_hanging_msg();
}

void transferCompactStart(int core) {
	// send start compact messages to all cores
	// TODO no large obj info
  int msgsize = 3;
  int i = 0;
	int ismove = 0;
	int movenum = 0;

	// both lcore and rcore have the same action: either 
	// move objs or have incoming objs
	if(gcdeltal[core] > 0) {
		ismove = 0; // have incoming objs
		movenum++;
	} else if(gcdeltal[core] < 0) {
		ismove = 1; // have objs to move
		movenum++;
	} 
	if(gcdeltar[core] > 0) {
		ismove = 0; // have incoming objs
		movenum++;
	} else if(gcdeltar[core] < 0) {
		ismove = 1; // have objs to move
		movenum++;
	}
	msgsize += (movenum == 0) ? 0 : 2 + movenum * 2;

  isMsgSending = true;
  DynamicHeader msgHdr = tmc_udn_header_from_cpu(core);

	// send header
  __tmc_udn_send_header_with_size_and_tag(msgHdr, msgsize, 
			                                    UDN0_DEMUX_TAG);  
#ifdef DEBUG
  BAMBOO_DEBUGPRINT(0xbbbb);
  BAMBOO_DEBUGPRINT(0xb000 + core);       // targetcore
#endif
  udn_send(GCSTARTCOMPACT);
#ifdef DEBUG
  BAMBOO_DEBUGPRINT(GCSTARTCOMPACT);
#endif
  udn_send(msgsize);
#ifdef DEBUG
  BAMBOO_DEBUGPRINT_REG(msgsize);
#endif
	udn_send(gcreloads[core]);
#ifdef DEBUG
  BAMBOO_DEBUGPRINT_REG(gcreloads[core]);
#endif
	if(movenum > 0) {
		udn_send(movenum);
#ifdef DEBUG
		BAMBOO_DEBUGPRINT_REG(movenum);
#endif
		udn_send(ismove);
#ifdef DEBUG
		BAMBOO_DEBUGPRINT_REG(ismove);
#endif
		int dst = 0;
		if(gcdeltal[core] != 0) {
			LEFTNEIGHBOUR(core, &dst);
			udn_send(abs(gcdeltal[core]));
#ifdef DEBUG
			BAMBOO_DEBUGPRINT_REG(abs(gcdeltal[core]));
#endif
			udn_send(dst);
#ifdef DEBUG
			BAMBOO_DEBUGPRINT_REG(dst);
#endif
		}
		if(gcdeltar[core] != 0) {
			RIGHTNEIGHBOUR(core, &dst);
			udn_send(abs(gcdeltar[core]));
#ifdef DEBUG
			BAMBOO_DEBUGPRINT_REG(abs(gcdeltar[core]));
#endif
			udn_send(dst);
#ifdef DEBUG
			BAMBOO_DEBUGPRINT_REG(dst);
#endif
		}
	}
#ifdef DEBUG
  BAMBOO_DEBUGPRINT(0xffff);
#endif

  // end of sending this msg, set sand msg flag false
  isMsgSending = false;
  send_hanging_msg();
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

	// iteratively balance the loads
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

	// decide how to do load balance
	for(i = 0; i < NUMCORES; i++) {
		gcdeltal[i] = gcdeltar[i] = 0;
	}
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

		gcprocessing = true;
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
			//send start compact messages to all cores
			transferCompactStart(i);
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

		// need to create free memory list and invalidate all 
		// shared mem pointers TODO

		gcflag = false;
		gcprocessing = false;
		return;
	} else {
		gcprocessing = true;
		gc_collect(stackptr);
	}
	// invalidate all shared mem pointers
	bamboo_cur_msp = NULL;
	bamboo_smem_size = 0;
	gcflag = false;
	gcprocessing = false;

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
		struct parameterwrapper ** queues = 
			objectqueues[BAMBOO_NUM_OF_CORE][i];
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
		struct transObjInfo * objInfo = 
			(struct transObjInfo *)(tmpobjptr->objectptr); 
		gc_enqueue(objInfo->objptr);
		getNextQueueItem(tmpobjptr);
	}
}

void mark(bool isfirst, 
		      struct garbagelist * stackptr) {
	if(isfirst) {
		// enqueue root objs
		tomark(stackptr);
		curr_heaptop = BAMBOO_CACHE_LINE_SIZE;
		curr_heapbound = BAMBOO_SMEM_SIZE_L;
		markedptrbound = 0;
	}

	int isize = 0;
	// mark phase
	while(MARKPHASE == gcphase) {
		while(gc_moreItems2()) {
			voit * ptr = gc_dequeue2();
			int size = 0;
			int type = 0;
			if(isLarge(ptr, &type, &size)) {
				// ptr is a large object
				struct largeObjItem * loi = 
					(struct largeObjItem*)RUNMALLOC(sizeof(struct largeObjItem)); 
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
				ALIGNSIZE(size, &isize);
				curr_heaptop += isize;
				if(curr_heaptop > curr_heapbound) {
					// change to another block
					curr_heaptop = curr_heapbound+BAMBOO_CACHE_LINE_SIZE+isize;
					curr_heapbound += BAMBOO_SMEM_SIZE;
				}
				// mark this obj
				((int *)ptr)[6] = 1;
				if(ptr > markedptrbound) {
					markedptrbound = ptr;
				}
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
					void *objptr = 
						((void **)(((char *)&ao->___length___)+sizeof(int)))[j];
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

struct moveHelper {
	int numblocks; // block num for heap
	INTPTR base; // base virtual address of current heap block
	INTPTR ptr; // virtual address of current heap top
	int offset; // offset in current heap block
	int blockbase; // virtual address of current small block to check
	int blockbound; // bound virtual address of current small blcok 
	int top; // real size of current heap block to check
	int bound; // bound size of current heap block to check
};

void nextSBlock(struct moveHelper * orig) {
	orig->blockbase = orig->blockbound;
	if(orig->blockbase == orig->bound) {
		// end of current heap block, jump to next one
		orig->numblocks++;
		BASEPTR(BAMBOO_NUM_OF_CORE, orig->numblocks, &(orig->base));
		orig->bound = orig->base + BAMBOO_SMEM_SIZE;
		orig->blockbase = orig->base;
	}
	orig->blockbound = orig->blockbase + *((int*)(orig->blockbase));
	orig->offset = BAMBOO_CACHE_LINE_SIZE;
	orig->ptr = orig->blockbase + orig->offset;
}

void nextBlock(struct moveHelper * to) {
	to->top = to->bound + BAMBOO_CACHE_LINE_SIZE; // header!
	to->bound += BAMBOO_SMEM_SIZE;
	to->numblocks++;
	BASEPTR(BAMBOO_NUM_OF_CORE, to->numblocks, &(to->base));
	to->offset = BAMBOO_CACHE_LINE_SIZE;
	to->ptr = to->base + to->offset;
}

// endaddr does not contain spaces for headers
bool moveobj(struct moveHelper * orig, 
		         struct moveHelper * to, 
						 INTPTR * endaddr) {
	int type = 0;
	int size = 0;
	int mark = 0;
	int isize = 0;
innermoveobj:
	while((*((int*)(orig->ptr))) == -2) {
		orig->ptr++;
		if(orig->ptr == orig->blockbound) {
			nextSBlock(orig);
			goto innermoveobj;
		}
	}
	// check the obj's type, size and mark flag
	type = ((int *)(orig->ptr))[0];
	size = 0;
	if(type == -1) {
		// end of this block, go to next one
		nextSBlock(orig);
		goto innermoveobj;
	} else if(type < NUMCLASSES) {
		// a normal object
		size = classsize[type];
	} else {	
		// an array 
		struct ArrayObject *ao=(struct ArrayObject *)(orig->ptr);
		int elementsize=classsize[type];
		int length=ao->___length___; 
		size=sizeof(struct ArrayObject)+length*elementsize;
	}
	mark = ((int *)(orig->ptr))[6];
	if(mark == 1) {
		// marked obj, copy it to current heap top
		// check to see if remaining space is enough
		ALIGNSIZE(size, &isize);
		if((endaddr != NULL) && (to->top + isize > *endaddr)) {
			// reached the endaddr 
			// fill offset to the endaddr for later configuration of header
			to->offset += *endaddr - to->top;
			to->top += *endaddr - to->top;
			return true;
		}
		if(to->top + isize > to->bound) {
			// fill the header of this block and then go to next block
    	to->offset += to->bound - to->top;
			(*((int*)(to->base))) = to->offset;
			if(endaddr != NULL) {
				*endaddr = *endaddr + BAMBOO_CACHE_LINE_SIZE; 
			}
			nextBlock(to);
		}
		memcpy(to->ptr, orig->ptr, size);
		// store mapping info
		RuntimeHashadd(pointertbl, orig->ptr, to->ptr); 
		to->ptr += isize;
		to->offset += isize;
		to->top += isize;
	} 
	// move to next obj
	orig->ptr += size;
	if(orig->ptr == orig->blockbound) {
		nextSBlock(orig);
	}
	return false;
}

void migrateobjs(struct moveHelper * orig) {
	int num_dsts = cinstruction->movenum;
	while(num_dsts > 0) {
		while(!gctomove) {}
		// start moving objects to other cores
		gctomove = false;
		struct moveHelper * into = 
			(struct moveHelper *)RUNMALLOC(sizeof(struct moveHelper));
		for(int j = 0; j < cinstruction->movenum; j++) {
			if(cinstruction->moveflag[j] == 1) {
				// can start moving to corresponding core
				int dst = cinstruction->dsts[j];
				num_dsts--;
				into->ptr = cinstruction->startaddrs[j];
				BLOCKINDEX(into->ptr, &(into->numblocks));
				into->bound = (into->numblocks==0)?
					BAMBOO_SMEM_SIZE_L:
					BAMBOO_SMEM_SIZE_L+BAMBOO_SMEM_SIZE*into->numblocks;
				BASEPTR(BAMBOO_NUM_OF_CORE, into->numblocks, &(into->base));
				into->offset = into->ptr - into->base;
				into->top = (into->numblocks==0)?
					(into->offset):(into->bound-BAMBOO_SMEM_SIZE+into->offset);
				into->base = into->ptr;
				into->offset = BAMBOO_CACHE_LINE_SIZE;
				into->ptr += into->offset; // for header
				into->top += into->offset;
				int endaddr = into->top + cinstruction->endaddrs[j];
				do {
					bool stop = moveobj(orig, into, &endaddr);
					if(stop) {
						// all objs before endaddr have been moved
						// STOP the loop
						break;
					}							
				} while(orig->ptr < markedptrbound + 1);
				// set the flag indicating move finished
				cinstruction->moveflag[j] = 2; 
				// fill the header of this blockk
				(*((int*)(into->base))) = into->offset;
			} // if(cinstruction->moveflag[j] == 1)
		} // for(int j = 0; j < cinstruction->movenum; j++)
		RUNFREE(into);
	} // while(num_dsts > 0)
}

void compact() {
	if(COMPACTPHASE != gcphase) {
		BAMBOO_EXIT(0xb003);
	}

	INTPTR heaptopptr = 0;

	// initialize pointers for comapcting
	struct moveHelper * orig = 
		(struct moveHelper *)RUNMALLOC(sizeof(struct moveHelper));
	struct moveHelper * to = 
		(struct moveHelper *)RUNMALLOC(sizeof(struct moveHelper));
	to->numblocks = 0;
	to->top = to->offset = BAMBOO_CACHE_LINE_SIZE;
	to->bound = BAMBOO_SMEM_SIZE_L;
	BASEPTR(BAMBOO_NUM_OF_CORE, to->numblocks, &(to->base));
	to->ptr = to->base + to->offset;
	orig->numblocks = 0;
	orig->ptr = to->ptr;
	orig->base = to->base;
	orig->bound = to->bound;
	orig->blockbase = to->base;
	orig->blockbound = orig->blockbase + *((int*)(orig->blockbase));

	// scan over all objs in this block, compact those scheduled to 
	// reside on this core
	// loop stop when finishing either scanning all active objs or moving
	// all objs to reside on this core
	int endaddr = cinstruction->loads;
	do {
		bool stop = moveobj(orig, to, &endaddr);
		curr_heaptop = to->top;
		curr_heapbound = to->bound;
		if(stop && (cinstruction->movenum != 0)) {
			// all objs to reside on this core have been moved
			// the remainging objs should be moved to other cores
			// STOP the loop
			break;
		}
	} while(orig->ptr < markedptrbound + 1); 
	// fill the header of this block
	(*((int*)(to->base))) = to->offset;
	heaptopptr = to->ptr;

	// move objs
	if(cinstruction->movenum != 0) {
		if(cinstruction->ismove) {
			// have objs to move to other cores
			migrateobjs(orig);

			// might still have objs left, compact them to this core
			// leave space for header
			if(orig->ptr < markedptrbound + 1) {
				if(to->top + BAMBOO_CACHE_LINE_SIZE > to->bound) {
					// fill the left part of current block
					memset(to->top, -2, to->bound - to->top);
					// go to next block
					nextBlock(to);
				} else {
					to->top += BAMBOO_CACHE_LINE_SIZE; // for header
					to->offset = BAMBOO_CACHE_LINE_SIZE;
					to->base = to->ptr;
					to->ptr += BAMBOO_CACHE_LINE_SIZE;
				}
				while(orig->ptr < markedptrbound + 1) {
					moveobj(orig, to, NULL);
					curr_heaptop = to->top;
					curr_heapbound = to->bound;
				}
				// fill the header of this blockk
				(*((int*)(to->base))) = to->offset;
			}
			heaptopptr = to->ptr;
		} else {
			// have incoming objs, send messages to corresponding cores 
			// to start moving
			INTPTR startaddr = 0;
			INTPTR endaddr = 0;
			int heapptr = curr_heapptr;
			int top = curr_heaptop;
			int bound = curr_heapbound;
			for(int j = 0; j < cinstruction->movenum; j++) {
				startaddr = heapptr;
				top = top+cinstruction->size2move[j]+BAMBOO_CACHE_LINE_SIZE;
				if(top > bound) {
					// will cross block boundary
					int numb = (top - bound) / BAMBOO_SMEM_SIZE + 1;
					top += numb * BAMBOO_CACHE_LINE_SIZE;
					BASEPTR(BAMBOO_NUM_OF_CORE, numblocks + numb, &endaddr);
					endaddr += 
						(top-bound)%BAMBOO_SMEM_SIZE+BAMBOO_CACHE_LINE_SIZE;
					heapptr = endaddr;
					bound += BAMBOO_SMEM_SIZE * numb;
				} else {
					endaddr = 
						heapptr+cinstruction->size2move[j]+BAMBOO_CACHE_LINE_SIZE;
					heapptr = endaddr;
				}
				send_msg_4(cinstruction->dsts[j], GCMOVESTART, 
						       BAMBOO_NUM_OF_CORE, startaddr, 
									 cinstruction->size2move[j]);
			}
			heaptopptr = heapptr;
		} // if(cinstruction->ismove) 
	} // if(cinstruction->movenum != 0)
	
	// TODO large obj
	/*
	if((cinstruction != NULL) && (cinstruction->largeobjs != NULL)) {
		// move all large objects
		do {
			// dequeue the first large obj
			struct largeObjItem * loi = cinstruction->largeobjs;
			cinstruction->largeobjs = loi->next;
			// move this large obj
			memcpy(loi->dst, loi->orig, loi->length);
			RuntimeHashadd(pointertbl, loi->orig, loi->dst);
			RUNFREE(loi);
		}while(cinstruction->largeobjs != NULL);
	}*/
	// send compact finish message to core coordinator
	send_msg_3(STARTUPCORE, GCFINISHCOMPACT, 
			       BAMBOO_NUM_OF_CORE, to->ptr);

	RUNFREE(orig);
	RUNFREE(to);
} // compact()

void flush() {
	while(gc_moreItems()) {
		voit * ptr = gc_dequeue();
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
				void *objptr=
					((void **)(((char *)&ao->___length___)+sizeof(int)))[j];
				// change to new address
				void *dstptr = NULL;
				RuntimeHashget(pointertbl, objptr, &dstptr);
				if(NULL == dstptr) {
					// send msg to host core for the mapping info
					obj2map = (int)objptr;
					ismapped = false;
					mappedobj = NULL;
					send_msg_3(hostcore(objptr), GCMAPREQUEST, (int)objptr, 
							       BAMBOO_NUM_OF_CORE);
					while(!ismapped) {}
					RuntimeHashget(pointertbl, objptr, &dstptr);
				}
				((void **)(((char *)&ao->___length___)+sizeof(int)))[j]=dstptr;
			}
		} else {
			INTPTR size=pointer[0];
			int i;
			for(i=1; i<=size; i++) {
				unsigned int offset=pointer[i];
				void * objptr=*((void **)(((char *)ptr)+offset));
				// change to new address
				void *dstptr = NULL;
				RuntimeHashget(pointertbl, objptr, &dstptr);
				if(NULL == dstptr) {
					// send msg to host core for the mapping info
					obj2map = (int)objptr;
					ismapped = false;
					mappedobj = NULL;
					send_msg_3(hostcore(objptr), GCMAPREQUEST, (int)objptr, 
							       BAMBOO_NUM_OF_CORE);
					while(!ismapped) {}
					RuntimeHashget(pointertbl, objptr, &dstptr);
				}
				*((void **)(((char *)ptr)+offset)) = dstptr;
			}
		}
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

	while(FINISHPHASE != gcphase) {}
}

#endif
