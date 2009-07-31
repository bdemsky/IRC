#ifdef MULTICORE_GC
#include "multicoregarbage.h"
#include "multicoreruntime.h"
#include "runtime_arch.h"
#include "SimpleHash.h"
#include "GenericHashtable.h"

extern struct genhashtable * activetasks;
extern struct parameterwrapper ** objectqueues[][NUMCLASSES];
extern struct taskparamdescriptor *currtpd;
//extern struct RuntimeHash *fdtoobject;

struct largeObjList lObjList;
struct markedObjList mObjList;
INTPTR curr_heaptop = 0;

struct markedObjList {
	struct markedObjItem * head;
	struct markedObjItem * tail;
};

struct largeObjList {
	struct largeObjItem * head;
	struct largeObjItem * tail;
};

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

void insertMarkedObj(struct markedObjList * list, struct markedObjItem * toinsert) {
	// insert a markedObjItem
	struct markedObjItem * tmp = list->head;
	if(tmp == NULL) {
		list->head = toinsert;
		list->tail = toinsert;
	} else if(tmp->orig > toinsert->orig) {
		// insert into the head of the list
		toinsert->next = tmp;
		list->head = toinsert;
	} else {
		struct markedObjItem * next = tmp->next;
		while(next != NULL) {
			if(next->orig < toinsert->orig) {
				tmp = next;
				next = tmp->next;
			} else if((next->orig == toinsert->orig) || (tmp->orig == toinsert->orig)) {
				// has been inserted
				RUNFREE(toinsert);
				toinsert = NULL;
				break;
			} else {
				// insert after tmp
				toinsert->next = next;
				tmp->next = tmp;
				break;
			}
		} // while(next != NULL)
		if(next == NULL) {
			if(tmp->orig == toinsert->orig) {
				RUNFREE(toinsert);
				toinsert = NULL;
			} else {
				// insert to the tail of the list
				tmp->next = toinsert;
				list->tail = toinsert;
			}
		} // if(next == NULL)
	}
}

struct markedObjItem * getStartItem(struct markedObjItem * moi, INTPTR start) {
	// find the markedobj whose start address is start
	struct markedObjItem * tostart = moi;
	while(tostart->orig < start) {
		tostart = tostart->next;
	}
	return tostart;
}

void transferMarkResults() {
	// TODO, need distiguish between send and cache
	// invoked inside interruptiong handler
}

void gc() {
	// core coordinator routine
	if(0 == BAMBOO_NUM_OF_CORE) {
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

		int i = 0;
		gcwaitconfirm = false;
		gcwaitconfirm = 0;
		gcphase = 0;
		for(i = 1; i < NUMCORES - 1; i++) {
			// send GC start messages to all cores
			send_msg_1(i, 0x11);
		}
		bool isfirst = true;
		lObjList.head = NULL;
		lObjList.tail = NULL;
		mObjList.head = NULL;
		mObjList.tail = NULL;

		// mark phase
		while(gcphase == 0) {
			mark(isfirst);
			if(isfirst) {
				isfirst = false;
			}

			bool allStall = false;
			// check gcstatus
			if((!gcwaitconfirm) || 
					(gcwaitconfirm && (gcnumconfirm == 0))) {
				BAMBOO_START_CRITICAL_SECTION_STATUS();  
				gccorestatus[BAMBOO_NUM_OF_CORE] = 0;
				gcnumsendobjs[BAMBOO_NUM_OF_CORE] = gcself_numsendobjs;
				gcnumreceiveobjs[BAMBOO_NUM_OF_CORE] = gcself_numreceiveobjs;
				// check the status of all cores
				allStall = true;
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
								send_msg_1(i, 0x18);
							}
						} else {
							// all the core status info are the latest
							// stop mark phase
							gcphase = 1;
							for(i = 0; i < NUMCORES; ++i) {
								gccorestatus[i] = 1;
							}
						} // if(!gcwautconfirm) else()
					} // if(0 == sumsendobj)
				} // if(allStall)
				BAMBOO_CLOSE_CRITICAL_SECTION_STATUS();  
			} // if((!gcwaitconfirm)...
		}  // while(gcphase == 0)
		// send msgs to all cores requiring large objs info
		gcnumconfirm = NUMCORES - 1;
		for(i = 1; i < NUMCORES; ++i) {
			send_msg_1(i, 0x1e);
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
		while(gcphase == 1) {
			// check the status of all cores
			allStall = true;
			for(i = 0; i < NUMCORES; ++i) {
				if(gccorestatus[i] != 0) {
					allStall = false;
					break;
				}
			}	
			if(allStall) {
				for(i = 0; i < NUMCORES; ++i) {
					gccorestatus[i] = 1;
				}
				break;
			}
		} // while(gcphase == 1)
		// TODO merge all mapping information
		gcphase = 2;
		for(i = 1; i < NUMCORES; ++i) {
			// send start flush messages to all cores
			send_msg_1(i, 0x13);
		}

		// flush phase
		flush();
		gccorestatus[BAMBOO_NUM_OF_CORE] = 0;
		while(gcphase == 2) {
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
		} // while(gcphase == 2)
		// TODO merge all mapping information
		gcphase = 3;
		for(i = 1; i < NUMCORES; ++i) {
			// send gc finish messages to all cores
			send_msg_1(i, 0x17);
		}

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
	} else {
		BAMBOO_EXIT(0xb001);
	}
}

void mark(bool isfirst) {
	if(isfirst) {
		if(gcphase != 0) {
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
	while(gcphase == 0) {
		while(!isEmpty(gctomark)) {
			voit * ptr = getItem(gctomark);
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
				struct markedObjItem * moi = 
					(struct markedObjItem *)RUNMALLOC(sizeof(struct markedObjItem)); 
				moi->orig = (INTPTR)ptr;
				moi->dst = (INTPTR)0;
				insertMarkedObj(&mObjList, moi);
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
						send_msg_2(host, 0x1a, objptr);
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
						send_msg_2(host, 0x1a, objptr);
						gcself_numsendobjs++;
					}
				}
			}
		} // while(!isEmpty(gctomark))
		gcbusystatus = false;
		// send mark finish msg to core coordinator
		send_msg_4(STARTUPCORE, 0x14, BAMBOO_NUM_OF_CORE, gcself_numsendobjs, gcself_numreceiveobjs); 

		if(BAMBOO_NUM_OF_CORE == 0) {
			return;
		}
	} // while(gcphase == 0)
} // mark()

void compact() {
	if(gcphase != 1) {
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
				send_msg_2(cinstruction->incomingobjs->dsts[j], 0x1b, BAMBOO_NUM_OF_CORE);
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
	send_msg_2(STARTUPCORE, 0x15, BAMBOO_NUM_OF_CORE);
	
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
					send_msg_3(hostcore(objptr), 0x1c, (int)objptr, BAMBOO_NUM_OF_CORE);
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
					send_msg_3(hostcore(objptr), 0x1c, (int)objptr, BAMBOO_NUM_OF_CORE);
					while(!ismapped) {}
					dstptr = mappedobj;
				}
				*((void **)(((char *)ptr)+offset)) = dstptr;
			}
		}
		moi = moi->next;
	} // while(moi != NULL)
	// send flush finish message to core coordinator
	send_msg_2(STARTUPCORE, 0x16, BAMBOO_NUM_OF_CORE);
	
} // flush()

void collect() {
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
	while(gcphase != 2) {}
	flush();
	
	while(true) {
		if(gcphase == 3) {
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

/* Message format:
 *      type + Msgbody
 * type:11 -- GC start
 *      12 -- compact phase start
 *      13 -- flush phase start
 *      14 -- mark phase finish
 *      15 -- compact phase finish
 *      16 -- flush phase finish
 *      17 -- GC finish
 *      18 -- marked phase finish confirm request
 *      19 -- marked phase finish confirm response
 *      1a -- markedObj msg
 *      1b -- start moving objs msg
 *      1c -- ask for mapping info of a markedObj
 *      1d -- mapping info of a markedObj
 *      1e -- large objs info request
 *      1f -- large objs info response
 *
 * GCMsg: 11 (size is always 1 * sizeof(int))
 *        12 + size of msg + (num of objs to move + (start address + end address + dst core + start dst)+)? + (num of incoming objs + (start dst + orig core)+)? + (num of large obj lists + (start address + lenght + start dst)+)?
 *        13 (size is always 1 * sizeof(int))
 *        14 + corenum + gcsendobjs + gcreceiveobjs (size if always 4 * sizeof(int))
 *        15/16 + corenum (size is always 2 * sizeof(int))
 *        17 (size is always 1 * sizeof(int))
 *        18 (size if always 1 * sizeof(int))
 *        19 + size of msg + corenum + gcsendobjs + gcreceiveobjs (size is always 5 * sizeof(int))
 *        1a + obj's address (size is always 2 * sizeof(int))
 *        1b + corenum ( size is always 2 * sizeof(int))
 *        1c + obj's address + corenum (size is always 3 * sizeof(int))
 *        1d + obj's address + dst address (size if always 3 * sizeof(int))
 *        1e (size is always 1 * sizeof(int))
 *        1f + size of msg + corenum + (num of large obj lists + (start address + length)+)?
 *
 * NOTE: for Tilera, all GCMsgs except the GC start msg should be processed with a different net/port with other msgs
 */


int gc_msghandler() {
  int deny = 0;
  int i = 0;
  
gcmsg:
  if(receiveGCMsg() == -1) {
	  return -1;
  }

  if(gcmsgdataindex == gcmsglength) {
    // received a whole msg
    int type, data1;             // will receive at least 2 words including type
    type = gcmsgdata[0];
    data1 = gcmsgdata[1];
    switch(gctype) {
    case 0x12: {
		// a compact phase start msg
		if(cinstruction == NULL) {
			cinstruction = (struct compactInstr *)RUNMALLOC(sizeof(struct compactInstr));
		} else {
			// clean up out of data info
			if(cinstruction->tomoveobjs != NULL) {
				RUNFREE(cinstruction->tomoveobjs->starts);
				RUNFREE(cinstruction->tomoveobjs->ends);
				RUNFREE(cinstruction->tomoveobjs->dststarts);
				RUNFREE(cinstruction->tomoveobjs->dsts);
				RUNFREE(cinstruction->tomoveobjs);
				cinstruction->tomoveobjs = NULL;
			}
			if(cinstruction->incomingobjs != NULL) {
				RUNFREE();
				RUNFREE(cinstruction->incomingobjs->starts);
				RUNFREE(cinstruction->incomingobjs->dsts);
				RUNFREE(cinstruction->incomingobjs);
				cinstruction->incomingobjs = NULL;
			}
			// largeobj items should have been freed when processed
			if(cinstruction->largeobjs != NULL) {
				BAMBOO_EXIT(0xb005);
			}
		}
		if(data1 > 2) {
			// have objs to move etc.
			int startindex = 2;
			// process objs to move
			int num = gcmsgdata[startindex++];
			if(num > 0) {
				cinstruction->tomoveobjs = (struct moveObj *)RUNMALLOC(sizeof(struct moveObj));
				cinstruction->tomoveobjs->length = num;
				cinstruction->tomoveobjs->starts = (INTPTR *)RUNMALLOC(num * sizeof(INTPTR));
				cinstruction->tomoveobjs->ends = (INTPTR *)RUNMALLOC(num * sizeof(INTPTR));
				cinstruction->tomoveobjs->dststarts = (INTPTR *)RUNMALLOC(num * sizeof(INTPTR));
				cinstruction->tomoveobjs->dsts = (INTPTR *)RUNMALLOC(num * sizeof(INTPTR));
				for(i = 0; i < num; i++) {
					cinstruction->tomoveobjs->starts[i] = gcmsgdata[startindex++];
					cinstruction->tomoveobjs->ends[i] = gcmsgdata[startindex++];
					cinstruction->tomoveobjs->dsts[i] = gcmsgdata[startindex++];
					cinstruction->tomoveobjs->dststarts[i] = gcmsgdata[startindex++];
				}
			}
			// process incoming objs
			num = gcmsgdata[startindex++];
			if(num > 0) {
				cinstruction->incomingobjs = (struct moveObj *)RUNMALLOC(sizeof(struct moveObj));
				cinstruction->incomingobjs->length = num;
				cinstruction->incomingobjs->starts = (INTPTR *)RUNMALLOC(num * sizeof(INTPTR));
				cinstruction->incomingobjs->dsts = (INTPTR *)RUNMALLOC(num * sizeof(INTPTR));
				for(i = 0; i < num; i++) {
					cinstruction->incomingobjs->starts[i] = gcmsgdata[startindex++];
					cinstruction->incomingobjs->dsts[i] = gcmsgdata[startindex++];
				}
			}
			// process large objs
			num = gcmsgdata[startindex++];
			for(i = 0; i < num; i++) {
				struct largeObjItem * loi = (struct largeObjItem *)RUNMALLOC(sizeof(struct largeObjItem ));
				loi->orig = gcmsgdata[startindex++];
				loi->length = gcmsgdata[startindex++];
				loi->dst = gcmsgdata[startindex++];
				loi->next = NULL;
				if(i > 0) {
					cinstruction->largeobjs->next = loi;
				}
				cinstruction->largeobjs = loi;
			}
		}
		gcphase = 1;
		break;
	}

	case 0x13: {
		// received a flush phase start msg
		gcphase = 2;
		break;
	}

	case 0x14: {
		// received a mark phase finish msg
		if(BAMBOO_NUM_OF_CORE != STARTUPCORE) {
		  // non startup core can not receive this msg
		  // return -1
#ifndef TILERA
		  BAMBOO_DEBUGPRINT_REG(data1);
#endif
		  BAMBOO_EXIT(0xb006);
      } 
      if(data1 < NUMCORES) {
		  gccorestatus[data1] = 0;
		  gcnumsendobjs[data1] = gcmsgdata[2];
		  gcnumreceiveobjs[data1] = gcmsgdata[3];
      }
	  break;
	}
	
	case 0x15: {
		// received a compact phase finish msg
		if(BAMBOO_NUM_OF_CORE != STARTUPCORE) {
		  // non startup core can not receive this msg
		  // return -1
#ifndef TILERA
		  BAMBOO_DEBUGPRINT_REG(data1);
#endif
		  BAMBOO_EXIT(0xb006);
      } 
      if(data1 < NUMCORES) {
		  gccorestatus[data1] = 0;
      }
	  break;
	}

	case 0x16: {
		// received a flush phase finish msg
		if(BAMBOO_NUM_OF_CORE != STARTUPCORE) {
		  // non startup core can not receive this msg
		  // return -1
#ifndef TILERA
		  BAMBOO_DEBUGPRINT_REG(data1);
#endif
		  BAMBOO_EXIT(0xb006);
      } 
      if(data1 < NUMCORES) {
		  gccorestatus[data1] = 0;
      }
	  break;
	}

	case 0x17: {
		// received a GC finish msg
		gcphase = 3;
		break;
	}

	case 0x18: {
		// received a marked phase finish confirm request msg
		if((BAMBOO_NUM_OF_CORE == STARTUPCORE) || (BAMBOO_NUM_OF_CORE > NUMCORES - 1)) {
		  // wrong core to receive such msg
		  BAMBOO_EXIT(0xa013);
      } else {
		  // send response msg
		  if(gcisMsgSending) {
			  cache_msg_5(STARTUPCORE, 0x19, BAMBOO_NUM_OF_CORE, gcbusystatus, gcself_numsendobjs, gcself_numreceiveobjs);
		  } else {
			  send_msg_5(STARTUPCORE, 0x19, BAMBOO_NUM_OF_CORE, gcbusystatus, gcself_numsendobjs, gcself_numreceiveobjs);
		  }
      }
	  break;
	}

	case 0x19: {
		// received a marked phase finish confirm response msg
		if(BAMBOO_NUM_OF_CORE != STARTUPCORE) {
		  // wrong core to receive such msg
#ifndef TILERA
		  BAMBOO_DEBUGPRINT_REG(gcmsgdata[2]);
#endif
		  BAMBOO_EXIT(0xb014);
      } else {
		  if(gcwaitconfirm) {
			  gcnumconfirm--;
		  }
		  gccorestatus[data1] = gcmsgdata[2];
		  gcnumsendobjs[data1] = gcmsgdata[3];
		  gcnumreceiveobjs[data1] = gcmsgdata[4];
      }
	  break;
	}

	case 0x1a: {
		// received a markedObj msg
		addNewItem(gctomark, data1);
		gcself_numreceiveobjs++;
		gcbusystatus = true;
		break;
	}

	case 0x1b: {
		// received a start moving objs msg
		addNewItem_I(gcdsts, data1);
		tomove = true;
		break;
	}
	
	case 0x1c: {
		// received a mapping info request msg
		void * dstptr = gengettable(pointertbl, data1);
		if(NULL == dstptr) {
			// no such pointer in this core, something is wrong
			BAMBOO_EXIT(0xb008);
		} else {
			// send back the mapping info
			if(gcisMsgSending) {
				cache_msg_3(gcmsgdata[2], 0x1d, data1, dstptr);
			} else {
				send_msg_3(gcmsgdata[2], 0x1d, data1, dstptr);
			}
		}
		break;
	}

	case 0x1d: {
		// received a mapping info response msg
		if(data1 != obj2map) {
			// obj not matched, something is wrong
			BAMBOO_EXIT(0xb009);
		} else {
			mappedobj = gcmsgdata[2];
			genputtable(pointertbl, obj2map, mappedobj);
		}
		ismapped = true;
		break;
	}

	case 0x1e: {
		// received a large objs info request msg
		transferMarkResults();
		break;
	}

	case 0x1f: {
		// received a large objs info response msg
		// TODO
		gcwaitconfirm--;
		break;
	}
			
	default:
      break;
    }
    for(gcmsgdataindex--; gcmsgdataindex > 0; --gcmsgdataindex) {
      gcmsgdata[gcmsgdataindex] = -1;
    }
    gcmsgtype = -1;
    gcmsglength = 30;

    if(BAMBOO_GCMSG_AVAIL() != 0) {
      goto gcmsg;
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
    BAMBOO_DEBUGPRINT(0xe88d);
#endif
#endif
    return -2;
  }
}
#endif
