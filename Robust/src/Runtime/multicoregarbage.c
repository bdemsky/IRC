#ifdef MULTICORE_GC
#include "multicoregarbage.h"
#include "multicoreruntime.h"
#include "runtime_arch.h"
#include "SimpleHash.h"
#include "GenericHashtable.h"

extern struct genhashtable * activetasks;
extern struct parameterwrapper ** objectqueues[][NUMCLASSES];
extern struct taskparamdescriptor *currtpdo;

inline void gc_enqueue(void *ptr) {
  if (gcheadindex==NUMPTRS) {
    struct pointerblock * tmp;
    if (gcspare!=NULL) {
      tmp=gcspare;
      gcspare=NULL;
    } else {
      tmp=malloc(sizeof(struct pointerblock));
		} // if (gcspare!=NULL)
    gchead->next=tmp;
    gchead=tmp;
    gcheadindex=0;
  } // if (gcheadindex==NUMPTRS)
  gchead->ptrs[gcheadindex++]=ptr;
} // void gc_enqueue(void *ptr)

// dequeue and destroy the queue
inline void * gc_dequeue() {
  if (gctailindex==NUMPTRS) {
    struct pointerblock *tmp=gctail;
    gctail=gctail->next;
    gctailindex=0;
    if (gcspare!=NULL) {
      free(tmp);
		} else {
      gcspare=tmp;
		} // if (gcspare!=NULL)
  } // if (gctailindex==NUMPTRS)
  return gctail->ptrs[gctailindex++];
} // void * gc_dequeue()

// dequeue and do not destroy the queue
inline void * gc_dequeue2() {
	if (gctailindex2==NUMPTRS) {
    struct pointerblock *tmp=gctail2;
    gctail2=gctail2->next;
    gctailindex2=0;
  } // if (gctailindex2==NUMPTRS)
  return gctail2->ptrs[gctailindex2++];
} // void * gc_dequeue2() 

inline int gc_moreItems() {
  if ((gchead==gctail)&&(gctailindex==gcheadindex))
    return 0;
  return 1;
} // int gc_moreItems() 

inline int gc_moreItems2() {
  if ((gchead==gctail2)&&(gctailindex2==gcheadindex))
    return 0;
  return 1;
} // int gc_moreItems2()

// enqueue a large obj: start addr & length
inline void gc_lobjenqueue(void *ptr, 
		                       int length, 
										       int host = 0) {
  if (gclobjheadindex==NUMLOBJPTRS) {
    struct lobjpointerblock * tmp;
    if (gclobjspare!=NULL) {
      tmp=gclobjspare;
      gclobjspare=NULL;
    } else {
      tmp=malloc(sizeof(struct lobjpointerblock));
		} // if (gclobjspare!=NULL)
    gclobjhead->next=tmp;
    gclobjhead=tmp;
    gclobjheadindex=0;
  } // if (gclobjheadindex==NUMLOBJPTRS)
  gclobjhead->lobjs[gclobjheadindex]=ptr;
	gclobjhead->lengths[gclobjheadindex]=length;
	gclobjhead->hosts[gclobjheadindex]=host;
	/*if(oirg == NULL) {
		gclobjhead->origs[gclobjheadindex++]=ptr;
	} else {
		gclobjhead->origs[gclobjheadindex++]=orig;
	}*/
} // void gc_lobjenqueue(void *ptr...)

// dequeue and destroy the queue
inline void * gc_lobjdequeue(int * length
		                         int * host) {
  if (gclobjtailindex==NUMLOBJPTRS) {
    struct lobjpointerblock *tmp=gclobjtail;
    gclobjtail=gclobjtail->next;
    gclobjtailindex=0;
    if (gclobjspare!=NULL) {
      free(tmp);
		} else {
      gclobjspare=tmp;
		} // if (gclobjspare!=NULL)
  } // if (gclobjtailindex==NUMLOBJPTRS)
	if(length != NULL) {
		*length = gclobjtail->lengths[gclobjtailindex];
	}
	if(host != NULL) {
		*host = (int)(gclobjtail->hosts[gclobjtailindex]);
	}
  return gclobjtail->lobjs[gclobjtailindex++];
} // void * gc_lobjdequeue()

inline int gc_lobjmoreItems() {
  if ((gclobjhead==gclobjtail)&&(gclobjtailindex==gclobjheadindex))
    return 0;
  return 1;
} // int gc_lobjmoreItems()

// dequeue and don't destroy the queue
inline void gc_lobjdequeue2() {
  if (gclobjtailindex2==NUMLOBJPTRS) {
    gclobjtail2=gclobjtail2->next;
    gclobjtailindex2=1;
  } else {
		gclobjtailindex2++;
	}// if (gclobjtailindex2==NUMLOBJPTRS)
} // void * gc_lobjdequeue2()

inline int gc_lobjmoreItems2() {
  if ((gclobjhead==gclobjtail2)&&(gclobjtailindex2==gclobjheadindex))
    return 0;
  return 1;
} // int gc_lobjmoreItems2()

INTPTR curr_heaptop = 0;
INTPTR curr_heapbound = 0;

inline void gettype_size(void * ptr, 
		                     int * ttype, 
							       		 int * tsize) {
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
	} // if(type < NUMCLASSES)
	*ttype = type;
	*tsize = size;
}

inline bool isLarge(void * ptr, 
		                int * ttype, 
										int * tsize) {
	// check if a pointer is referring to a large object
	gettype_size(ptr, ttype, tsize);
	return(!isLocal(ptr + size));
} // bool isLarge(void * ptr, int * ttype, int * tsize)

inline int hostcore(void * ptr) {
	// check the host core of ptr
	int host = 0;
	int x = 0;
	int y = 0;
	RESIDECORE(ptr, &x, &y);
	host = (x==0)?(x*bamboo_height+y):(x*bamboo_height+y-2);
	return host;
} // int hostcore(void * ptr)

inline bool isLocal(void * ptr) {
	// check if a pointer is in shared heap on this core
	return hostcore(ptr) == BAMBOO_NUM_OF_CORE;
} // bool isLocal(void * ptr)

inline void transferMarkResults() {
	// invoked inside interruptiong handler
	int msgsize = 5 + gcnumlobjs;
  int i = 0;

  if(isMsgSending) {
		// cache the msg
		isMsgHanging = true;
		// cache the msg in outmsgdata and send it later
		// msglength + target core + msg
		OUTMSG_CACHE(msgsize);
		OUTMSG_CACHE(STARTUPCORE);
		OUTMSG_CACHE(GCLOBJINFO);
		OUTMSG_CACHE(msgsize);
		OUTMSG_CACHE(curr_heaptop);
		OUTMSG_CACHE(gcmarkedptrbound);
		// large objs here
		void * lobj = NULL;
		int length = 0;
		while(gc_lobjmoreItems()) {
			lobj = gc_lobjdequeue(&length);
			OUTMSG_CACHE(lobj);
			OUTMSG_CACHE(length);
		} // while(gc_lobjmoreItems())
	} else {
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
		udn_send(gcmarkedptrbound);
#ifdef DEBUG
		BAMBOO_DEBUGPRINT_REG(gcmarkedptrbound);
#endif
		// large objs here
		void * lobj = NULL;
		int length = 0;
		while(gc_lobjmoreItems()) {
			lobj = gc_lobjdequeue(&length);
			OUTMSG_CACHE(lobj);
#ifdef DEBUG
			BAMBOO_DEBUGPRINT_REG(lobj);
#endif
			OUTMSG_CACHE(length);
#ifdef DEBUG
			BAMBOO_DEBUGPRINT_REG(length);
#endif
		} // while(gc_lobjmoreItems())
		
#ifdef DEBUG
		BAMBOO_DEBUGPRINT(0xffff);
#endif
	} // if(isMsgSending)
} // void transferMarkResults() 

inline void checkMarkStatue() {
	if((!waitconfirm) || 
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
			} // if(gccorestatus[i] != 0)
		} // for(i = 0; i < NUMCORES; ++i)
		if(allStall) {
			// check if the sum of send objs and receive obj are the same
			// yes->check if the info is the latest; no->go on executing
			int sumsendobj = 0;
			for(i = 0; i < NUMCORES; ++i) {
				sumsendobj += gcnumsendobjs[i];
			} // for(i = 0; i < NUMCORES; ++i) 
			for(i = 0; i < NUMCORES; ++i) {
				sumsendobj -= gcnumreceiveobjs[i];
			} // for(i = 0; i < NUMCORES; ++i) 
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
					} // for(i = 1; i < NUMCORES; ++i) 
				} else {
					// all the core status info are the latest
					// stop mark phase
					gcphase = COMPACTPHASE;
					// restore the gcstatus for all cores
					for(i = 0; i < NUMCORES; ++i) {
						gccorestatus[i] = 1;
					} // for(i = 0; i < NUMCORES; ++i)
				} // if(!gcwautconfirm) else()
			} // if(0 == sumsendobj)
		} // if(allStall)
		BAMBOO_CLOSE_CRITICAL_SECTION_STATUS();
	} // if((!waitconfirm)...
} // void checkMarkStatue()

inline bool preGC() {
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
		} // for(i = 1; i < NUMCORES; ++i)

		while(numconfirm != 0) {} // wait for confirmations
		numsendobjs[BAMBOO_NUM_OF_CORE] = self_numsendobjs;
		numreceiveobjs[BAMBOO_NUM_OF_CORE] = self_numreceiveobjs;
		int sumsendobj = 0;
		for(i = 0; i < NUMCORES; ++i) {
			sumsendobj += numsendobjs[i];
		} // for(i = 1; i < NUMCORES; ++i)	
		for(i = 0; i < NUMCORES; ++i) {
			sumsendobj -= numreceiveobjs[i];
		} // for(i = 1; i < NUMCORES; ++i)
		if(0 == sumsendobj) {
			return true;
		} else {
			// still have some transfer obj msgs on-the-fly, can not start gc
			return false;
		} // if(0 == sumsendobj) 
	} else {
		// previously asked for status confirmation and do not have all the 
		// confirmations yet, can not start gc
		return false;
	} // if((!waitconfirm) || 
} // bool preGC()

// compute load balance for all cores
inline int loadbalance(int heaptop) {
	// compute load balance
	int i;

	// get the total loads
	gcloads[0]+=BAMBOO_SMEM_SIZE*gcreservedsb;//reserved sblocks for sbstartbl
	int tloads = gcloads[0];
	for(i = 1; i < NUMCORES; i++) {
		tloads += gcloads[i];
	}
	int heaptop = BAMBOO_BASE_VA + tloads;
	int b = 0;
	BLOCKINDEX(heaptop, &b);
	int numbpc = b / NUMCORES; // num of blocks per core

	gcheapdirection = (numbpc%2 == 0);
	int x = 0;
	int y = 0;
	RESIDECORE(heaptop, &x, &y);
	gctopcore = (x == 0 ? y : x * bamboo_height + y - 2);
	return numbpc;
} // void loadbalance()

inline bool cacheLObjs() {
	// check the total mem size need for large objs
	int sumsize = 0;
	int size = 0;
	int isize = 0;
	while(gc_lobjmoreItems2()){
		gc_lobjdequeue2();
		size = gclobjtail2->lengths[gclobjtailindex2 - 1];
		ALIGNSIZE(size, &isize);
		sumsize += isize;
	} // while(gc_lobjmoreItems2())

	// check if there are enough space to cache these large objs
	INTPTR dst = BAMBOO_BASE_VA + BAMBOO_SHARED_MEM_SIZE - sumsize;
	if(gcheaptop > dst) {
		// do not have enough room to cache large objs
		return false;
	}

	gcheaptop = dst; // Note: record the start of cached lobjs with gcheaptop
	// cache the largeObjs to the top of the shared heap
	gclobjtail2 = gclobjtail;
	gclobjtailindex2 = 0;
	while(gc_lobjmoreItems2()) {
		gc_lobjdequeue2();
		size = gclobjtail2->lengths[gclobjtailindex2 - 1];
		ALIGNSIZE(size, &isize);
		memcpy(dst, gclobjtail2->lobjs[gclobjtailindex2 - 1], size);
		// fill the remaining space with -2
		memset(dst+size, -2, isize-size);
		// set the new addr of this obj
		//gclobjtail2->origs[gclobjtailindex2 - 1] = 
		//	gclobjtail2->lobjs[gclobjtailindex2 - 1];
		//gclobjtail2->lobjs[gclobjtailindex2 - 1] = dst;
		dst += isize;
	}
	return true;
} // void cacheLObjs()

inline void moveLObjs() {
	// find current heap top
	int tmpheaptop = gcloads[0];
	for(int i = 1; i < NUMCORES; i++) {
		if(tmpheaptop < gcloads[i]) {
			tmpheaptop = gcloads[i];
		}
	}
	// move large objs from gcheaptop to tmpheaptop
	// write the header first
	int tomove = BAMBOO_BASE_VA + BAMBOO_SHARED_MEM_SIZE - gcheaptop;
	// check how many blocks it acrosses
	int b = 0;
	BLOCKINDEX(tmpheaptop, &b);
	// check the remaining space in this block
	int remain = (b < NUMCORES? (b+1)*BAMBOO_SMEM_SIZE_L  
  		        : BAMBOO_LARGE_SMEM_BOUND+(b-NUMCORES+1)*BAMBOO_SMEM_SIZE)
		          -(mem-BAMBOO_BASE_VA);
	if(remain <= BAMBOO_CACHE_LINE_SIZE) {
		// fill the following space with -1, go to next block
		(*((int *)tmpheaptop)) = -1;
		b++;
		remain = b < NUMCORES? BAMBOO_SMEM_SIZE_L : BAMBOO_SMEM_SIZE;
		tmpheaptop += remain;
	}
	(*((int *)tmpheaptop)) = tomove + BAMBOO_CACHE_LINE_SIZE;
	tmpheaptop += BAMBOO_CACHE_LINE_SIZE;
	memcpy(tmpheaptop, gcheaptop, tomove);
	gcheaptop = tmpheaptop + tomove;
	// flush the sbstartbl
	memset(sbstarttbl, '\0', 
			   BAMBOO_SHARED_MEM_SIZE/BAMBOO_SMEM_SIZE*sizeof(INTPTR));
	int size = 0;
	int isize = 0;
	int host = 0;
	int ptr = 0;
	remain -= BAMBOO_CACHE_LINE_SIZE;
	while(gc_lobjmoreItems()) {
		ptr = (int)(gc_lobjdequeue(&size, &host));
		ALIGNSIZE(size, &isize);
		if(remain < isize) {
			// this object acrosses blocks
			int tmpsbs = 1+(isize-remain-1)/BAMBOO_SMEM_SIZE;
			for(int k = 0; k < tmpsbs-1; k++) {
				sbstarttbl[k+b] = (INTPTR)(-1);
			}
			b += tmpsbs;
			remain = b < NUMCORES ? BAMBOO_SMEM_SIZE_L : BAMBOO_SMEM_SIZE;
			if((isize-remain)%BAMBOO_SMEM_SIZE == 0) {
				sbstarttbl[b+tmpsbs-1] = (INTPTR)(-1);
			} else {
				sbstarttbl[b+tmpsbs-1] = (INTPTR)(tmpheaptop+isize);
				remain -= (isize-remain)%BAMBOO_SMEM_SIZE;
			}
		}
		// send the original host core with the mapping info
		send_msg_3(host, GCLOBJMAPPING, ptr, tmpheaptop);
		tmpheaptop += isize;
	}
} // void moveLObjs()

inline void updateFreeMemList() {
	int i = 0;
	int tmptop = gcloads[0];
	struct freeMemItem * tochange = bamboo_free_mem_list->head;
	if(tochange == NULL) {
		bamboo_free_mem_list->head = tochange = 
			(struct freeMemItem *)RUNMALLOC(sizeof(struct freeMemItem));
	}
	for(i = 1; i < NUMCORES; ++i) {
		int toadd = gcloads[i];
		if(tmptop < toadd) {
			toadd = tmptop;
			tmptop = gcloads[i];
		} // tmptop can never == toadd
		int blocki = 0;
		BLOCKINDEX(toadd, &blocki);
		tochange->ptr = toadd;
		tochange->size = (blocki<NUMCORES)
			?((blocki+1)*BAMBOO_SMEM_SIZE_L+BAMBOO_BASE_VA-toadd)
			:(BAMBOO_LARGE_SMEM_BOUND+(blocki+1-NUMCORES)*BAMBOO_SMEM_SIZE
					+BAMBOO_BASE_VA-toadd);
		if(tochange->next == NULL) {
			tochange->next = 
				(struct freeMemItem *)RUNMALLOC(sizeof(struct freeMemItem));
		}
		// zero out all these spare memory
		memset(tochange->ptr, '\0', tochange->size);
		tochange = tochange->next;
	} // for(i = 1; i < NUMCORES; ++i)
	// handle the top of the heap
	tmptop = gcheaptop;
	BLOCKINDEX(tmptop, &blocki);
	tochange->ptr = tmptop;
	tochange->size = BAMBOO_SHARED_MEM_SIZE + BAMBOO_BASE_VA - tmptop;
	// zero out all these spare memory
	memset(tochange->ptr, '\0', tochange->size);
	bamboo_free_mem_list->tail = tochange;
} // void updateFreeMemList()

inline void gc(struct garbagelist * stackptr) {
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
		waitconfirm = false;
		waitconfirm = 0;
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
		if(!cacheLObjs()) {
			// no enough space to cache large objs
			BAMBOO_EXIT(0xd001);
		}
		int numpbc = loadbalance();

		if((gcheapdirection) && (0 <= gctopcore)
				|| ((!gcheapdirection) && (0 == gctopcore))) {
			gcstopblock = numpbc + 1;
		} else {
			gcstopblock = numpbc;
		}
		for(i = 1; i < NUMCORES; ++i) {
			//send start compact messages to all cores
			if((gcheapdirection) && (i <= gctopcore)
					|| ((!gcheapdirection) && (i >= gctopcore))) {
					send_msg_2(i, GCSTARTCOMPACT, numpbc+1); 
			} else {
					send_msg_2(i, GCSTARTCOMPACT, numpbc);
			}
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
		// move largeObjs
		moveLObjs();

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

		// need to create free memory list  
		updateFreeMemList();
	} else {
		gcprocessing = true;
		gc_collect(stackptr);
	}

	// invalidate all shared mem pointers
	bamboo_cur_msp = NULL;
	bamboo_smem_size = 0;

	gcflag = false;
	gcprocessing = false;

} // void gc(struct garbagelist * stackptr)

// enqueue root objs
inline void tomark(struct garbagelist * stackptr) {
	if(MARKPHASE != gcphase) {
		BAMBOO_EXIT(0xb002);
	}
	gcbusystatus = 1;
	gcnumlobjs = 0;
	
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
} // void tomark(struct garbagelist * stackptr)

inline void markObj(void * objptr) {
	if(ISSHAREDOBJ(objptr)) {
		int host = hostcore(objptr);
		if(BAMBOO_NUM_OF_CORE == host) {
			// on this core
			gc_enqueue(objptr);  
		} else {
			// send a msg to host informing that objptr is active
			send_msg_2(host, GCMARKEDOBJ, objptr);
			gcself_numsendobjs++;
		}
	} else {
		gc_enqueue(objptr);
	} // if(ISSHAREDOBJ(objptr))
} // void markObj(void * objptr) 

inline void mark(bool isfirst, 
		             struct garbagelist * stackptr) {
	if(isfirst) {
		// enqueue root objs
		tomark(stackptr);
		curr_heaptop = 0; // record the size of all active objs in this core
		                  // aligned but does not consider block boundaries
		gcmarkedptrbound = 0;
	}

	int isize = 0;
	// mark phase
	while(MARKPHASE == gcphase) {
		while(gc_moreItems2()) {
			gcbusystatus = true;
			void * ptr = gc_dequeue2();
			int size = 0;
			int isize = 0;
			int type = 0;
			// check if it is a shared obj
			if(ISSHAREDOBJ(ptr)) {
				// a shared obj, check if it is a local obj on this core
				if(isLarge(ptr, &type, &size)) {
					// ptr is a large object
					gc_lobjenqueue(ptr, size);
					gcnumlobjs++;
				} else if (isLocal(ptr)) {
					// ptr is an active object on this core
					ALIGNSIZE(size, &isize);
					curr_heaptop += isize;
					// mark this obj
					((int *)ptr)[6] = 1;
					if(ptr + size > gcmarkedptrbound) {
						gcmarkedptrbound = ptr + size;
					} // if(ptr + size > gcmarkedptrbound)
				} // if(isLarge(ptr, &type, &size)) else if(isLocal(ptr))
			} // if(ISSHAREDOBJ(ptr))

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
					markObj(objptr);
				}
			} else {
				INTPTR size=pointer[0];
				int i;
				for(i=1; i<=size; i++) {
					unsigned int offset=pointer[i];
					void * objptr=*((void **)(((char *)ptr)+offset));
					markObj(objptr);
				}
			}
		} // while(!isEmpty(gctomark))
		gcbusystatus = false;
		// send mark finish msg to core coordinator
		if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
			gccorestatus[BAMBOO_NUM_OF_CORE] = 0;
			gcnumsendobjs[BAMBOO_NUM_OF_CORE] = gcself_numsendobjs;
			gcnumreceiveobjs[BAMBOO_NUM_OF_CORE] = gcself_numreceiveobjs;
		} else {
			send_msg_4(STARTUPCORE, GCFINISHMARK, BAMBOO_NUM_OF_CORE,
								 gcself_numsendobjs, gcself_numreceiveobjs);
		}

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
	int sblockindex; // index of the small blocks
	int top; // real size of current heap block to check
	int bound; // bound size of current heap block to check
}; // struct moveHelper

inline void nextSBlock(struct moveHelper * orig) {
	orig->blockbase = orig->blockbound;
innernextSBlock:
	if(orig->blockbase >= orig->bound) {
		// end of current heap block, jump to next one
		orig->numblocks++;
		BASEPTR(BAMBOO_NUM_OF_CORE, orig->numblocks, &(orig->base));
		orig->bound = orig->base + BAMBOO_SMEM_SIZE;
		orig->blockbase = orig->base;
	}
	orig->sblockindex = (orig->blockbase-BAMBOO_BASE_VA)/BAMBOO_SMEM_SIZE;
	if(sbstarttbl[orig->sblockindex] == -1) {
		// goto next sblock
		orig->sblockindex += 1;
		orig->blockbase += BAMBOO_SMEM_SIZE;
		goto innernextSBlock;
	} else if(sbstarttbl[orig->sblockindex] != 0) {
		// not start from the very beginning
		orig->blockbase = sbstarttbl[orig->sblockindex];
	}
	orig->blockbound = orig->blockbase + *((int*)(orig->blockbase));
	orig->offset = BAMBOO_CACHE_LINE_SIZE;
	orig->ptr = orig->blockbase + orig->offset;
} // void nextSBlock(struct moveHelper * orig) 

inline void initOrig_Dst(struct moveHelper * orig, 
		                     struct moveHelper * to) {
	// init the dst ptr
	to->numblocks = 0;
	to->top = to->offset = BAMBOO_CACHE_LINE_SIZE;
	to->bound = BAMBOO_SMEM_SIZE_L;
	BASEPTR(BAMBOO_NUM_OF_CORE, to->numblocks, &(to->base));
	if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
		to->base += gcreservedsb * BAMBOO_SMEM_SIZE;
		to->top += gcreservedsb * BAMBOO_SMEM_SIZE;
		curr_heaptop -= gcreservedsb * BAMBOO_SMEM_SIZE;
	}
	to->ptr = to->base + to->offset;

	// init the orig ptr
	orig->numblocks = 0;
	orig->base = to->base;
	orig->bound = to->base + BAMBOO_SMEM_SIZE_L;
	orig->blockbase = orig->base;
	if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
		orig->sblockindex = reservedsb;
	} else {
		orig->sblockindex = (orig->base - BAMBOO_BASE_VA) / BAMBOO_SMEM_SIZE;
	}
	if(sbstarttbl[sblockindex] == -1) {
		// goto next sblock
		orig->blockbound = 
			BAMBOO_BASE_VA+BAMBOO_SMEM_SIZE*(orig->sblockindex+1);
		nextSBlock(orig);
		return;
	} else if(sbstarttbl[orig->sblockindex] != 0) {
		orig->blockbase = sbstarttbl[sblockindex];
	}
	orig->blockbound = orig->blockbase + *((int*)(orig->blockbase));
	orig->offset = BAMBOO_CACHE_LINE_SIZE;
	orig->ptr = orig->blockbase + orig->offset;
} // void initOrig_Dst(struct moveHelper * orig, struct moveHelper * to) 

inline void nextBlock(struct moveHelper * to) {
	to->top = to->bound + BAMBOO_CACHE_LINE_SIZE; // header!
	to->bound += BAMBOO_SMEM_SIZE;
	to->numblocks++;
	BASEPTR(BAMBOO_NUM_OF_CORE, to->numblocks, &(to->base));
	to->offset = BAMBOO_CACHE_LINE_SIZE;
	to->ptr = to->base + to->offset;
} // void nextBlock(struct moveHelper * to)

// endaddr does not contain spaces for headers
inline bool moveobj(struct moveHelper * orig, 
		                struct moveHelper * to, 
						        int stopblock) {
	if(stopblock == 0) {
		return true;
	}

	int type = 0;
	int size = 0;
	int mark = 0;
	int isize = 0;
innermoveobj:
	while((*((int*)(orig->ptr))) == -2) {
		orig->ptr++;
		if((orig->ptr > orig->bound) || (orig->ptr == orig->blockbound)) {
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
		if(to->top + isize > to->bound) {
			// fill -1 indicating the end of this block
			if(to->top != to->bound) {
				*((int*)to->ptr) = -1;
			}
			memset(to->ptr+1, -2, to->bound - to->top - 1);
			// fill the header of this block and then go to next block
    	to->offset += to->bound - to->top;
			(*((int*)(to->base))) = to->offset;
			nextBlock(to);
			if(stopblock == to->numblocks) {
				// already fulfilled the block
				to->offset = 0;
				to->ptr = to->base;
				return true;
			}
		}
		memcpy(to->ptr, orig->ptr, size);
		// fill the remaining space with -2
		memset(to->ptr+size, -2, isize-size);
		// store mapping info
		RuntimeHashadd(gcpointertbl, orig->ptr, to->ptr); 
		curr_heaptop -= isize;
		to->ptr += isize;
		to->offset += isize;
		to->top += isize;
	} 
	// move to next obj
	orig->ptr += size;
	if((orig->ptr > orig->bound) || (orig->ptr == orig->blockbound)) {
		nextSBlock(orig);
	}
	return false;
} //bool moveobj(struct moveHelper* orig,struct moveHelper* to,int* endaddr)

inline bool findSpareMem(int * startaddr,
		                     int * tomove,
												 int requiredmem) {
	for(int k = 0; k < NUMCORES; k++) {
		if((gccorestatus[k] == 0) && (gcnumblocks[k] < gcstopblock)) {
			// check if this stopped core has enough mem
			int b = 0;
			BLOCKINDEX(gcloads[k], &b);
			int boundptr = b<NUMCORES?(b+1)*BAMBOO_SMEM_SIZE_L
				:BAMBOO_LARGE_SMEM_BOUND+(b-NUMCORES+1)*BAMBOO_SMEM_SIZE;
			int remain = boundptr - gcloads[k];
			*tomove = requiredmem + BAMBOO_CACHE_LINE_SIZE;
			*startaddr = gcloads[k];
			if(*tomove < remain) {
				gcloads[k] += *tomove;
			} else {
				// next available block
				gcnumblocks[k] += 1;
				int newbase = 0;
				BASEPTR(k, gcnumblocks[k], &newbase);
				gcloads[k] = newbase;
				*tomove = remain;
			}
			return true;
		}
	}
	// TODO if can not find spare mem right now, hold the request
	return false;
}

inline void compact() {
	if(COMPACTPHASE != gcphase) {
		BAMBOO_EXIT(0xb003);
	}

	INTPTR heaptopptr = 0;

	// initialize pointers for comapcting
	struct moveHelper * orig = 
		(struct moveHelper *)RUNMALLOC(sizeof(struct moveHelper));
	struct moveHelper * to = 
		(struct moveHelper *)RUNMALLOC(sizeof(struct moveHelper));
	initOrig_Dst(orig, to);
	
	// scan over all objs in this block, compact the marked objs 
	// loop stop when finishing either scanning all active objs or 
	// fulfilled the gcstopblock
innercompact:
	do {
		bool stop = moveobj(orig, to, gcstopblock);
		if(stop) {
			break;
		}
	} while(orig->ptr < gcmarkedptrbound); 
	// fill the header of this block
	(*((int*)(to->base))) = to->offset;
	heaptopptr = to->ptr;

	// send msgs to core coordinator indicating that the compact is finishing
	// send compact finish message to core coordinator
	if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
		gcnumblocks[0] = to->numblocks;
		if(orig->ptr < gcmarkedptrbound) {
			// ask for more mem
			gctomove = false;
			if(findSpareMem(&gcmovestartaddr, &gcstopblock, curr_heaptop)) {
				gctomove = true;
			} else {
				// TODO hold the request
			}
		} else {
			gccorestatus[BAMBOO_NUM_OF_CORE] = 0;
			gcloads[BAMBOO_NUM_OF_CORE] = to->ptr;
		}
	} else {
		if(orig->ptr < gcmarkedptrbound) {
			// ask for more mem
			gctomove = false;
			send_msg_5(STARTUPCORE, GCFINISHCOMPACT, BAMBOO_NUM_OF_CORE, 
					       to->numblocks, 0, curr_heaptop);
		} else {
			// finish compacting
			send_msg_5(STARTUPCORE, GCFINISHCOMPACT, BAMBOO_NUM_OF_CORE,
					       to->numblocks, 1, to->ptr);
		}
	} // if(STARTUPCORE == BAMBOO_NUM_OF_CORE)

	if(orig->ptr < gcmarkedptrbound) {
		// still have unpacked obj
		while(!gctomove) {};

		to->ptr = gcmovestartaddr;
		to->numblocks = gcstopblock - 1;
		to->bound = (to->numblocks==0)?
			BAMBOO_SMEM_SIZE_L:
			BAMBOO_SMEM_SIZE_L+BAMBOO_SMEM_SIZE*to->numblocks;
		BASEPTR(BAMBOO_NUM_OF_CORE, to->numblocks, &(to->base));
		to->offset = to->ptr - to->base;
		to->top = (to->numblocks==0)?
			(to->offset):(to->bound-BAMBOO_SMEM_SIZE+to->offset);
		to->base = to->ptr;
		to->offset = BAMBOO_CACHE_LINE_SIZE;
		to->ptr += to->offset; // for header
		to->top += to->offset;
		goto innercompact;
	}
	// TODO finish?

	RUNFREE(orig);
	RUNFREE(to);
} // compact()

inline void * flushObj(void * objptr) {
	void * dstptr = NULL;
	if(ISSHAREDOBJ(objptr)) {
		// a shared obj ptr, change to new address
		RuntimeHashget(gcpointertbl, objptr, &dstptr);
		if(NULL == dstptr) {
			// send msg to host core for the mapping info
			gcobj2map = (int)objptr;
			gcismapped = false;
			gcmappedobj = NULL;
			send_msg_3(hostcore(objptr), GCMAPREQUEST, (int)objptr, 
								 BAMBOO_NUM_OF_CORE);
			while(!gcismapped) {}
			RuntimeHashget(gcpointertbl, objptr, &dstptr);
		}
	} // if(ISSHAREDOBJ(objptr))
	return dstptr;
} // void flushObj(void * objptr, void ** tochange)

inline void flush() {
	while(gc_moreItems()) {
		void * ptr = gc_dequeue();
		void * tptr = flushObj(ptr);
		if(tptr != NULL) {
			ptr = tptr;
		}
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
				((void **)(((char *)&ao->___length___)+sizeof(int)))[j] = 
					flushObj(objptr);
			}
		} else {
			INTPTR size=pointer[0];
			int i;
			for(i=1; i<=size; i++) {
				unsigned int offset=pointer[i];
				void * objptr=*((void **)(((char *)ptr)+offset));
				((void **)(((char *)ptr)+offset)) = flushObj(objptr);
			} // for(i=1; i<=size; i++) 
		} // if (pointer==0) else if (((INTPTR)pointer)==1) else ()
	} // while(moi != NULL)
	// send flush finish message to core coordinator
	if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
		gccorestatus[BAMBOO_NUM_OF_CORE] = 0;
	} else {
		send_msg_2(STARTUPCORE, GCFINISHFLUSH, BAMBOO_NUM_OF_CORE);
	}
} // flush()

inline void gc_collect(struct garbagelist * stackptr) {
	// core collector routine
	mark(true, stackptr);
	compact();
	while(FLUSHPHASE != gcphase) {}
	flush();

	while(FINISHPHASE != gcphase) {}
} // void gc_collect(struct garbagelist * stackptr)

#endif
