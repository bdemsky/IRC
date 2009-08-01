#ifdef MULTICORE_GC
#include "multicoregarbage.h"
#include "multicoreruntime.h"
#include "runtime_arch.h"
#include "SimpleHash.h"
#include "GenericHashtable.h"

extern struct genhashtable * activetasks;
extern struct parameterwrapper ** objectqueues[][NUMCLASSES];
extern struct taskparamdescriptor *currtpd;

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
			(gcwaitconfirm && (gcnumconfirm == 0))) {
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
				if(!gcwaitconfirm) {
					// the first time found all cores stall
					// send out status confirm msg to all other cores
					// reset the corestatus array too
					gccorestatus[BAMBOO_NUM_OF_CORE] = 1;
					gcwaitconfirm = true;
					gcnumconfirm = NUMCORES - 1;
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

void gc() {
	// check if do gc
	if(!gcflag) {
		return;
	} else {
		// do gc
		gcflag = false;
	}

	// TODO, preparation

	// core coordinator routine
	if(0 == BAMBOO_NUM_OF_CORE) {
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
			mark(isfirst);
			if(isfirst) {
				isfirst = false;
			}

			// check gcstatus
			checkMarkStatue(); 
		}  // while(MARKPHASE == gcphase)
		// send msgs to all cores requiring large objs info
		gcnumconfirm = NUMCORES - 1;
		for(i = 1; i < NUMCORES; ++i) {
			send_msg_1(i, GCLOBJREQUEST);
		}	
		while(gcnumconfirm != 0) {} // wait for responses
		// TODO compute load balance

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
		gc_collect();
	}
}

void mark(bool isfirst) {
	if(isfirst) {
		if(MARKPHASE != gcphase) {
			BAMBOO_EXIT(0xb002);
		}
		gcbusystatus = 1;
		// initialize gctomark queue
		while(!isEmpty(gctomark)) {
			getItem(gctomark);
		}
		// enqueue current stack  TODO
		
		// enqueue objectsets
		int i;
		for(i=0; i<NUMCLASSES; i++) {
			struct parameterwrapper ** queues=objectqueues[BAMBOO_NUM_OF_CORE][i];
			int length = numqueues[BAMBOO_NUM_OF_CORE][i];
			for(j = 0; j < length; ++j) {
				struct parameterwrapper * parameter = queues[j];
				struct ObjectHash * set=parameter->objectset;
				struct ObjectNode * ptr=set->listhead;
				while(ptr!=NULL) {
					void *orig=(void *)ptr->key;
					addNewItem(gctomark, orig); 
					ptr=ptr->lnext;
				}
			}
		}
		// euqueue current task descriptor
		for(i=0; i<currtpd->numParameters; i++) {
			void *orig=currtpd->parameterArray[i];
			addNewItem(gctomark, orig);  
		}
		// euqueue active tasks
		struct genpointerlist * ptr=activetasks->list;
		while(ptr!=NULL) {
			struct taskparamdescriptor *tpd=ptr->src;
			int i;
			for(i=0; i<tpd->numParameters; i++) {
				void * orig=tpd->parameterArray[i];
				addNewItem(gctomark, orig); 
			}
			ptr=ptr->inext;
		}
	}

	// mark phase
	while(MARKPHASE == gcphase) {
		while(!isEmpty(gctomark)) {
			voit * ptr = getItem(gctomark);
			int size = 0;
			int type = 0;
			if(isLarge(ptr, &type, &size)) {
				// ptr is a large object
				// TODO
/*				struct largeObjItem * loi = 
					(struct largeObjItem *)RUNMALLOC(sizeof(struct largeObjItem));  
				loi->orig = (INTPTR)ptr;
				loi->dst = (INTPTR)0;
				loi->length = size;
				if(lObjList.head == NULL) {
					lObjList.head = lObjList.tail = loi;
				} else {
					lObjList.tail->next = loi;
					lObjList.tail = loi;
				}*/
			} else if (isLocal(ptr)) {
				// ptr is an active object on this core
				if(type == -1) {
					// nothing to do 
				}
				curr_heaptop += size;

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
						addNewItem(gctomark, objptr);  
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
						addNewItem(gctomark, objptr);  
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
		send_msg_4(STARTUPCORE, GCFINISHMARK, BAMBOO_NUM_OF_CORE, gcself_numsendobjs, gcself_numreceiveobjs); 

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

void gc_collect() {
	// core collector routine
	// change to UDN1
	bme_install_interrupt_handler(INT_UDN_AVAIL, gc_msghandler);
#ifdef DEBUG
	tprintf("Process %x(%d): change udn interrupt handler\n", BAMBOO_NUM_OF_CORE, 
			BAMBOO_NUM_OF_CORE);
#endif
	__insn_mtspr(SPR_UDN_TAG_1, UDN1_DEMUX_TAG);
	// enable udn interrupts
	//__insn_mtspr(SPR_INTERRUPT_MASK_RESET_2_1, INT_MASK_HI(INT_UDN_AVAIL));
	__insn_mtspr(SPR_UDN_AVAIL_EN, (1<<1));
	BAMBOO_CLOSE_CRITICAL_SECTION_MSG();

	lObjList.head = NULL;
	lObjList.tail = NULL;
	mObjList.head = NULL;
	mObjList.tail = NULL;
	mark(true);
	compact();
	while(FLUSHPHASE != gcphase) {}
	flush();
	
	while(true) {
		if(FINISHPHASE == gcphase) {
			// change to UDN0
			bme_install_interrupt_handler(INT_UDN_AVAIL, udn_inter_handle);
#ifdef DEBUG
			tprintf("Process %x(%d): change back udn interrupt handler\n", BAMBOO_NUM_OF_CORE, 
					BAMBOO_NUM_OF_CORE);
#endif
			__insn_mtspr(SPR_UDN_TAG_0, UDN0_DEMUX_TAG);
			// enable udn interrupts
			//__insn_mtspr(SPR_INTERRUPT_MASK_RESET_2_1, INT_MASK_HI(INT_UDN_AVAIL));
			__insn_mtspr(SPR_UDN_AVAIL_EN, (1<<0));
			BAMBOO_START_CRITICAL_SECTION_MSG();

			return;
		}
	}
}

#endif
