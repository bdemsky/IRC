#ifdef MULTICORE
#include "multicoremsg.h"
#include "runtime.h"
#include "multicoreruntime.h"
#include "multicoretaskprofile.h"
#include "gcqueue.h"

INLINE int checkMsgLength_I(int size) {
  int type = msgdata[msgdataindex];
  switch(type) {
  case STATUSCONFIRM:
  case TERMINATE:
#ifdef MULTICORE_GC
  case GCSTARTPRE:
  case GCSTART:
  case GCSTARTINIT:
  case GCSTARTFLUSH:
  case GCFINISH:
  case GCMARKCONFIRM:
  case GCLOBJREQUEST:
#ifdef GC_CACHE_ADAPT
  case GCSTARTPREF:
#endif 
#endif 
    return 1;
    break;

#ifdef TASK
  case PROFILEOUTPUT:
  case PROFILEFINISH:
#endif
#ifdef MULTICORE_GC
  case GCSTARTCOMPACT:
  case GCMARKEDOBJ:
  case GCFINISHINIT:
  case GCFINISHFLUSH:
#ifdef GC_CACHE_ADAPT
  case GCFINISHPREF:
#endif 
#endif 
    return 2;
    break;

  case MEMREQUEST:
  case MEMRESPONSE:
    return 3;
    break;
    
  case TRANSTALL:
#ifdef TASK
  case LOCKGROUNT:
  case LOCKDENY:
  case LOCKRELEASE:
  case REDIRECTGROUNT:
  case REDIRECTDENY:
  case REDIRECTRELEASE:
#endif
#ifdef MULTICORE_GC
  case GCFINISHPRE:
  case GCFINISHMARK:
  case GCMOVESTART:
#ifdef GC_PROFILE
  case GCPROFILES:
#endif
#endif
    return 4;
    break;
    
#ifdef TASK
  case LOCKREQUEST:
#endif
  case STATUSREPORT:
#ifdef MULTICORE_GC
  case GCFINISHCOMPACT:
  case GCMARKREPORT:
#endif
    return 5;
    break;
    
#ifdef TASK
  case REDIRECTLOCK:
    return 6;
    break;
#endif

#ifdef TASK
  case TRANSOBJ:   // nonfixed size
#endif
#ifdef MULTICORE_GC
  case GCLOBJINFO:
#endif
  {  // nonfixed size
    if(size > 1) {
      return msgdata[(msgdataindex+1)&(BAMBOO_MSG_BUF_MASK)];
    } else {
      return -1;
    }
    break;
  }

  default:
    BAMBOO_EXIT();
    break;
  }
  return BAMBOO_OUT_BUF_LENGTH;
}

INLINE void processmsg_transobj_I(int msglength) {
  MSG_INDEXINC_I();
  struct transObjInfo * transObj=RUNMALLOC_I(sizeof(struct transObjInfo));
  BAMBOO_ASSERT(BAMBOO_NUM_OF_CORE <= NUMCORESACTIVE - 1);

  // store the object and its corresponding queue info, enqueue it later
  transObj->objptr = (void *)msgdata[msgdataindex]; 
  MSG_INDEXINC_I();
  transObj->length = (msglength - 3) / 2;
  transObj->queues = RUNMALLOC_I(sizeof(int)*(msglength - 3));
  for(int k = 0; k < transObj->length; k++) {
    transObj->queues[2*k] = msgdata[msgdataindex];  
    MSG_INDEXINC_I();
    transObj->queues[2*k+1] = msgdata[msgdataindex]; 
    MSG_INDEXINC_I();
  }
  // check if there is an existing duplicate item
  struct QueueItem * prev = NULL;
  for(struct QueueItem * qitem = getHead(&objqueue);qitem != NULL;qitem=(prev==NULL)?getHead(&objqueue):getNextQueueItem(prev)) {
    struct transObjInfo * tmpinfo = (struct transObjInfo *)(qitem->objectptr);
    if(tmpinfo->objptr == transObj->objptr) {
      // the same object, remove outdate one
      RUNFREE_I(tmpinfo->queues);
      RUNFREE_I(tmpinfo);
      removeItem(&objqueue, qitem);
      //break;
    } else {
      prev = qitem;
    }
  }
  addNewItem_I(&objqueue, (void *)transObj);
  
  self_numreceiveobjs++;
#ifdef MULTICORE_GC
  if(gcprocessing) {
    if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
      // set the gcprecheck to enable checking again
      gcprecheck = true;
    } else {
      // send a update pregc information msg to the master core
      if(BAMBOO_CHECK_SEND_MODE()) {
        cache_msg_4(STARTUPCORE,GCFINISHPRE,BAMBOO_NUM_OF_CORE,self_numsendobjs,self_numreceiveobjs);
      } else {
        send_msg_4(STARTUPCORE,GCFINISHPRE,BAMBOO_NUM_OF_CORE,self_numsendobjs, self_numreceiveobjs,true);
      }
    }
  }
#endif 
}

INLINE void processmsg_transtall_I() {
  BAMBOO_ASSERT(BAMBOO_NUM_OF_CORE == STARTUPCORE);
  
  int num_core = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data2 = msgdata[msgdataindex]; 
  MSG_INDEXINC_I();
  int data3 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  if(num_core < NUMCORESACTIVE) {
    corestatus[num_core] = 0;
    numsendobjs[num_core] = data2; 
    numreceiveobjs[num_core] = data3; 
  }
}

#ifndef MULTICORE_GC
INLINE void processmsg_lockrequest_I() {
  // check to see if there is a lock exist for the required obj
  // msgdata[1] -> lock type
  int locktype = msgdata[msgdataindex]; 
  MSG_INDEXINC_I();
  int data2 = msgdata[msgdataindex];  // obj pointer
  MSG_INDEXINC_I();
  int data3 = msgdata[msgdataindex];  // lock
  MSG_INDEXINC_I();
  int data4 = msgdata[msgdataindex];  // request core
  MSG_INDEXINC_I();
  // -1: redirected, 0: approved, 1: denied
  int deny=processlockrequest(locktype, data3, data2, data4, data4, true);
  if(deny != -1) {
    // send response msg
    // for 32 bit machine, the size is always 4 words, cache the msg first
    int tmp = deny==1 ? LOCKDENY : LOCKGROUNT;
    if(BAMBOO_CHECK_SEND_MODE()) {
      cache_msg_4(data4, tmp, locktype, data2, data3);
    } else {
      send_msg_4(data4, tmp, locktype, data2, data3, true);
    }
  }
}

INLINE void processmsg_lockgrount_I() {
  MSG_INDEXINC_I();
  BAMBOO_ASSERT(BAMBOO_NUM_OF_CORE <= NUMCORESACTIVE - 1);
  int data2 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data3 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  BAMBOO_ASSERT((lockobj == data2) && (lock2require == data3));
  lockresult = 1;
  lockflag = true;
#ifndef INTERRUPT
  reside = false;
#endif
}

INLINE void processmsg_lockdeny_I() {
  MSG_INDEXINC_I();
  int data2 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data3 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  BAMBOO_ASSERT(BAMBOO_NUM_OF_CORE <= NUMCORESACTIVE - 1);
  BAMBOO_ASSERT((lockobj == data2) && (lock2require == data3));
  lockresult = 0;
  lockflag = true;
#ifndef INTERRUPT
  reside = false;
#endif
}

INLINE void processmsg_lockrelease_I() {
  int data1 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data2 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data3 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  // receive lock release msg
  processlockrelease(data1, data2, 0, false);
}

INLINE void processmsg_redirectlock_I() {
  // check to see if there is a lock exist for the required obj
  int data1 = msgdata[msgdataindex];
  MSG_INDEXINC_I();    // lock type
  int data2 = msgdata[msgdataindex];
  MSG_INDEXINC_I();    // obj pointer
  int data3 = msgdata[msgdataindex];
  MSG_INDEXINC_I();    // redirect lock
  int data4 = msgdata[msgdataindex];
  MSG_INDEXINC_I();    // root request core
  int data5 = msgdata[msgdataindex];
  MSG_INDEXINC_I();    // request core
  int deny = processlockrequest_I(data1, data3, data2, data5, data4, true);
  if(deny != -1) {
    // send response msg
    // for 32 bit machine, the size is always 4 words, cache the msg first
    if(BAMBOO_CHECK_SEND_MODE()) {
      cache_msg_4(data4,deny==1?REDIRECTDENY:REDIRECTGROUNT,data1,data2,data3);
    } else {
      send_msg_4(data4,deny==1?REDIRECTDENY:REDIRECTGROUNT,data1,data2,data3,true);
    }
  }
}

INLINE void processmsg_redirectgrount_I() {
  MSG_INDEXINC_I();
  int data2 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  BAMBOO_ASSERT(BAMBOO_NUM_OF_CORE <= NUMCORESACTIVE - 1);
  BAMBOO_ASSERT(lockobj == data2, 0xe207);
  int data3 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  lockresult = 1;
  lockflag = true;
  RuntimeHashadd_I(objRedirectLockTbl, lockobj, data3);
#ifndef INTERRUPT
  reside = false;
#endif
}

INLINE void processmsg_redirectdeny_I() {
  MSG_INDEXINC_I();
  int data2 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data3 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  BAMBOO_ASSERT(BAMBOO_NUM_OF_CORE <= NUMCORESACTIVE - 1);
  BAMBOO_ASSERT(lockobj == data2);
  lockresult = 0;
  lockflag = true;
#ifndef INTERRUPT
  reside = false;
#endif
}

INLINE void processmsg_redirectrelease_I() {
  int data1 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data2 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data3 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  processlockrelease_I(data1, data2, data3, true);
}
#endif // #ifndef MULTICORE_GC

#ifdef PROFILE
INLINE void processmsg_profileoutput_I() {
  BAMBOO_ASSERT(BAMBOO_NUM_OF_CORE != STARTUPCORE);
  stall = true;
  totalexetime = msgdata[msgdataindex];
  MSG_INDEXINC_I();
#ifdef RT_TEST
#else
  outputProfileData();
#endif
  // cache the msg first
  if(BAMBOO_CHECK_SEND_MODE()) {
    cache_msg_2(STARTUPCORE, PROFILEFINISH, BAMBOO_NUM_OF_CORE);
  } else {
    send_msg_2(STARTUPCORE, PROFILEFINISH, BAMBOO_NUM_OF_CORE, true);
  }
}

INLINE void processmsg_profilefinish_I() {
  BAMBOO_ASSERT(BAMBOO_NUM_OF_CORE == STARTUPCORE);
  int data1 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  profilestatus[data1] = 0;
}
#endif // PROFILE

INLINE void processmsg_statusconfirm_I() {
  BAMBOO_ASSERT(((BAMBOO_NUM_OF_CORE != STARTUPCORE) && (BAMBOO_NUM_OF_CORE <= NUMCORESACTIVE - 1)));
  // send response msg
  // cache the msg first
  if(BAMBOO_CHECK_SEND_MODE()) {
    cache_msg_5(STARTUPCORE,STATUSREPORT,busystatus?1:0,BAMBOO_NUM_OF_CORE,self_numsendobjs,self_numreceiveobjs);
  } else {
    send_msg_5(STARTUPCORE,STATUSREPORT,busystatus?1:0,BAMBOO_NUM_OF_CORE,self_numsendobjs,self_numreceiveobjs,true);
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
  BAMBOO_ASSERT(BAMBOO_NUM_OF_CORE == STARTUPCORE);
  if(waitconfirm) {
    numconfirm--;
  }
  corestatus[data2] = data1;
  numsendobjs[data2] = data3;
  numreceiveobjs[data2] = data4;
}

INLINE void processmsg_terminate_I() {
  disruntimedata();
#ifdef MULTICORE_GC
#ifdef GC_CACHE_ADAPT
  bamboo_mask_timer_intr(); // disable the TILE_TIMER interrupt
#endif
#endif
  BAMBOO_EXIT_APP(0);
}

INLINE void processmsg_memrequest_I() {
  int data1 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data2 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  // receive a shared memory request msg
  BAMBOO_ASSERT(BAMBOO_NUM_OF_CORE == STARTUPCORE);
  int allocsize = 0;
  void * mem = NULL;
#ifdef MULTICORE_GC
  if(!gcprocessing || !gcflag) {
    // either not doing GC or the master core has decided to stop GC but 
    // // still sending msgs to other cores to inform them to stop the GC
#endif
    mem = smemalloc_I(data2, data1, &allocsize);
    if(mem != NULL) {
      // send the start_va to request core, cache the msg first
      if(BAMBOO_CHECK_SEND_MODE()) {
        cache_msg_3(data2, MEMRESPONSE, mem, allocsize);
      } else {
        send_msg_3(data2, MEMRESPONSE, mem, allocsize, true);
      }
    } //else if mem == NULL, the gcflag of the startup core has been set
    // and all the other cores have been informed to start gc
#ifdef MULTICORE_GC
  }
#endif
}

INLINE void processmsg_memresponse_I() {
  int data1 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data2 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  // receive a shared memory response msg
#ifdef MULTICORE_GC
  // if is currently doing gc, dump this msg
  if(!gcprocessing) {
#endif
  if(data2 == 0) {
#ifdef MULTICORE_GC
    // Zero out the remaining memory here because for the GC_CACHE_ADAPT 
    // version, we need to make sure during the gcinit phase the shared heap 
    // is not touched. Otherwise, there would be problem when adapt the cache 
    // strategy.
    BAMBOO_CLOSE_CUR_MSP();
    bamboo_smem_zero_top = NULL;
#endif
    bamboo_smem_size = 0;
    bamboo_cur_msp = 0;
  } else {
#ifdef MULTICORE_GC
    CLOSEBLOCK(data1, data2);
    bamboo_smem_size = data2 - BAMBOO_CACHE_LINE_SIZE;
    bamboo_cur_msp = data1 + BAMBOO_CACHE_LINE_SIZE;
    bamboo_smem_zero_top = bamboo_cur_msp;
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
INLINE void processmsg_gcstartpre_I() {
  // the first time to be informed to start gc
  gcflag = true;
  if(!smemflag) {
    // Zero out the remaining memory here because for the GC_CACHE_ADAPT 
    // version, we need to make sure during the gcinit phase the shared heap 
    // is not touched. Otherwise, there would be problem when adapt the cache 
    // strategy.
    BAMBOO_CLOSE_CUR_MSP();
    bamboo_smem_size = 0;
    bamboo_cur_msp = NULL;
    smemflag = true;
    bamboo_smem_zero_top = NULL;
  }
}

INLINE void processmsg_gcstartinit_I() {
  gcphase = INITPHASE;
}

INLINE void processmsg_gcstart_I() {
  // set the GC flag
  gcphase = MARKPHASE;
}

INLINE void processmsg_gcstartcompact_I() {
  gcblock2fill = msgdata[msgdataindex];
  MSG_INDEXINC_I();  
  gcphase = COMPACTPHASE;
}

INLINE void processmsg_gcstartflush_I() {
  gcphase = FLUSHPHASE;
}

INLINE void processmsg_gcfinishpre_I() {
  int data1 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data2 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data3 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  // received a init phase finish msg
  BAMBOO_ASSERT(BAMBOO_NUM_OF_CORE == STARTUPCORE);

  // All cores should do init GC
  gcprecheck = true;
  gccorestatus[data1] = 0;
  gcnumsendobjs[0][data1] = data2;
  gcnumreceiveobjs[0][data1] = data3;
}

INLINE void processmsg_gcfinishinit_I() {
  int data1 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  // received a init phase finish msg
  BAMBOO_ASSERT(BAMBOO_NUM_OF_CORE == STARTUPCORE);

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
  BAMBOO_ASSERT(BAMBOO_NUM_OF_CORE == STARTUPCORE);

  // all cores should do mark
  if(data1 < NUMCORESACTIVE) {
    gccorestatus[data1] = 0;
    int entry_index = 0;
    if(waitconfirm)  {
      // phase 2
      entry_index = (gcnumsrobjs_index == 0) ? 1 : 0;
    } else {
      // phase 1
      entry_index = gcnumsrobjs_index;
    }
    gcnumsendobjs[entry_index][data1] = data2;
    gcnumreceiveobjs[entry_index][data1] = data3;
  }
}

INLINE void processmsg_gcfinishcompact_I() {
  BAMBOO_ASSERT(BAMBOO_NUM_OF_CORE == STARTUPCORE);

  int cnum = msgdata[msgdataindex];
  MSG_INDEXINC_I();      
  int filledblocks = msgdata[msgdataindex];
  MSG_INDEXINC_I();    
  int heaptop = msgdata[msgdataindex];
  MSG_INDEXINC_I();   
  int data4 = msgdata[msgdataindex];
  MSG_INDEXINC_I(); 
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
        // cache the msg first
        if(BAMBOO_CHECK_SEND_MODE()) {
          cache_msg_4(cnum, GCMOVESTART, dstcore, startaddr, tomove);
        } else {
          send_msg_4(cnum, GCMOVESTART, dstcore, startaddr, tomove, true);
        }
      }
    } else {
      gccorestatus[cnum] = 0;
    } 
  }  
}

INLINE void processmsg_gcfinishflush_I() {
  int data1 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  // received a flush phase finish msg
  BAMBOO_ASSERT(BAMBOO_NUM_OF_CORE == STARTUPCORE);

  // all cores should do flush
  if(data1 < NUMCORESACTIVE) {
    gccorestatus[data1] = 0;
  }
}

INLINE void processmsg_gcfinish_I() {
  // received a GC finish msg
  gcphase = FINISHPHASE;
  gcprocessing = false;
}

INLINE void processmsg_gcmarkconfirm_I() {
  BAMBOO_ASSERT(((BAMBOO_NUM_OF_CORE!=STARTUPCORE)&&(BAMBOO_NUM_OF_CORE<=NUMCORESACTIVE-1)));
  gcbusystatus = gc_moreItems2_I();
  // send response msg, cahce the msg first
  if(BAMBOO_CHECK_SEND_MODE()) {
    cache_msg_5(STARTUPCORE,GCMARKREPORT,BAMBOO_NUM_OF_CORE,gcbusystatus,gcself_numsendobjs,gcself_numreceiveobjs);
  } else {
    send_msg_5(STARTUPCORE,GCMARKREPORT,BAMBOO_NUM_OF_CORE,gcbusystatus,gcself_numsendobjs,gcself_numreceiveobjs, true);
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
  BAMBOO_ASSERT(BAMBOO_NUM_OF_CORE == STARTUPCORE);
  int entry_index = 0;
  BAMBOO_ASSERT(waitconfirm);

  // phase 2
  numconfirm--;
  entry_index = (gcnumsrobjs_index == 0) ? 1 : 0;
  gccorestatus[data1] = data2;
  gcnumsendobjs[entry_index][data1] = data3;
  gcnumreceiveobjs[entry_index][data1] = data4;

}

INLINE void processmsg_gcmarkedobj_I() {
  int data1 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  BAMBOO_ASSERT(ISSHAREDOBJ(data1));

  // received a markedObj msg
  if(((struct ___Object___ *)data1)->marked == INIT) {
    // this is the first time that this object is discovered,
    // set the flag as DISCOVERED
    ((struct ___Object___ *)data1)->marked = DISCOVERED;
    gc_enqueue_I(data1);
  }
  gcself_numreceiveobjs++;
  gcbusystatus = true;
}

INLINE void processmsg_gcmovestart_I() {
  gctomove = true;
  gcdstcore = msgdata[msgdataindex];
  MSG_INDEXINC_I();       
  gcmovestartaddr = msgdata[msgdataindex];
  MSG_INDEXINC_I();     
  gcblock2fill = msgdata[msgdataindex];
  MSG_INDEXINC_I();     
}

INLINE void processmsg_gclobjinfo_I() {
  numconfirm--;
  int data1 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data2 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  BAMBOO_ASSERT(BAMBOO_NUM_OF_CORE <= NUMCORES4GC - 1);

  // store the mark result info
  int cnum = data2;
  gcloads[cnum] = msgdata[msgdataindex];
  MSG_INDEXINC_I();     
  int data4 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  if(gcheaptop < data4) {
    gcheaptop = data4;
  }
  // large obj info here
  for(int k = 5; k < data1; k+=2) {
    int lobj = msgdata[msgdataindex];
    MSG_INDEXINC_I();  
    int length = msgdata[msgdataindex];
    MSG_INDEXINC_I();   
    gc_lobjenqueue_I(lobj, length, cnum);
    gcnumlobjs++;
  }
}

#ifdef GC_PROFILE
INLINE void processmsg_gcprofiles_I() {
  int data1 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data2 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data3 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
#ifdef MGC_SPEC
  if(gc_profile_flag) {
#endif
    gc_num_obj += data1;
    gc_num_liveobj += data2;
    gc_num_forwardobj += data3;
#ifdef MGC_SPEC
  }
#endif
  gc_num_profiles--;
}
#endif // GC_PROFILE

#ifdef GC_CACHE_ADAPT
INLINE void processmsg_gcstartpref_I() {
  gcphase = PREFINISHPHASE;
}

INLINE void processmsg_gcfinishpref_I() {
  int data1 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  // received a flush phase finish msg
  BAMBOO_ASSERT(BAMBOO_NUM_OF_CORE == STARTUPCORE);

  // all cores should do flush
  if(data1 < NUMCORESACTIVE) {
    gccorestatus[data1] = 0;
  }
}
#endif // GC_CACHE_ADAPT
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
int receiveObject(int send_port_pending) {
  PROFILE_INTERRUPT_START(); 
msg:
  // get the incoming msgs
  if(receiveMsg(send_port_pending) == -1) {
    return -1;
  }
  if(BAMBOO_CHECK_SEND_MODE()) {
    // during send, don't process the msg now
    return -3; 
  }
processmsg:
  // processing received msgs
  int size = 0;
  int msglength = 0;
  MSG_REMAINSIZE_I(&size);
  if((size == 0) || ((msglength=checkMsgLength_I(size)) == -1)) {
    // not a whole msg
    // have new coming msg
    if((BAMBOO_MSG_AVAIL() != 0) && !msgdatafull) {
      goto msg;
    } else {
      return -1;
    }
  }

  if(msglength <= size) {
    // have some whole msg
    MSGTYPE type;
    type = msgdata[msgdataindex]; //[0]
    MSG_INDEXINC_I();
    msgdatafull = false;
    switch(type) {
#ifdef TASK
    case TRANSOBJ: {
      // receive a object transfer msg
      processmsg_transobj_I(msglength);
      break;
    }  
#endif 
    case TRANSTALL: {
      // receive a stall msg
      processmsg_transtall_I();
      break;
    }   

#ifdef TASK
    // GC version have no lock msgs
#ifndef MULTICORE_GC
    case LOCKREQUEST: {
      // receive lock request msg, handle it right now
      processmsg_lockrequest_I();
      break;
    }   
    case LOCKGROUNT: {
      // receive lock grount msg
      processmsg_lockgrount_I();
      break;
    } 
    case LOCKDENY: {
      // receive lock deny msg
      processmsg_lockdeny_I();
      break;
    }  
    case LOCKRELEASE: {
      processmsg_lockrelease_I();
      break;
    }   
#endif

#ifdef PROFILE
    case PROFILEOUTPUT: {
      // receive an output profile data request msg
      processmsg_profileoutput_I();
      break;
    }   
    case PROFILEFINISH: {
      // receive a profile output finish msg
      processmsg_profilefinish_I();
      break;
    }  
#endif 

    // GC version has no lock msgs
#ifndef MULTICORE_GC
    case REDIRECTLOCK: {
      // receive a redirect lock request msg, handle it right now
      processmsg_redirectlock_I();
      break;
    }  

    case REDIRECTGROUNT: {
      // receive a lock grant msg with redirect info
      processmsg_redirectgrount_I();
      break;
    } 

    case REDIRECTDENY: {
      // receive a lock deny msg with redirect info
      processmsg_redirectdeny_I();
      break;
    }   

    case REDIRECTRELEASE: {
      // receive a lock release msg with redirect info
      processmsg_redirectrelease_I();
      break;
    }   // case REDIRECTRELEASE
#endif
#endif 

    case STATUSCONFIRM: {
      // receive a status confirm info
      processmsg_statusconfirm_I();
      break;
    }  

    case STATUSREPORT: {
      processmsg_statusreport_I();
      break;
    } 

    case TERMINATE: {
      // receive a terminate msg
      processmsg_terminate_I();
      break;
    } 

    case MEMREQUEST: {
      processmsg_memrequest_I();
      break;
    }

    case MEMRESPONSE: {
      processmsg_memresponse_I();
      break;
    }

#ifdef MULTICORE_GC
    // GC msgs
    case GCSTARTPRE: {
      processmsg_gcstartpre_I();
      break;
    }
	
    case GCSTARTINIT: {
      processmsg_gcstartinit_I();
      break;
    }

    case GCSTART: {
      // receive a start GC msg
      processmsg_gcstart_I();
      break;
    }

    case GCSTARTCOMPACT: {
      // a compact phase start msg
      processmsg_gcstartcompact_I();
      break;
    }

    case GCSTARTFLUSH: {
      // received a flush phase start msg
      processmsg_gcstartflush_I();
      break;
    }

    case GCFINISHPRE: {
      processmsg_gcfinishpre_I();
      break;
    }
	
    case GCFINISHINIT: {
      processmsg_gcfinishinit_I();
      break;
    }

    case GCFINISHMARK: {
      processmsg_gcfinishmark_I();
      break;
    }

    case GCFINISHCOMPACT: {
      // received a compact phase finish msg
      processmsg_gcfinishcompact_I();
      break;
    }

    case GCFINISHFLUSH: {
      processmsg_gcfinishflush_I();
      break;
    }  

    case GCFINISH: {
      processmsg_gcfinish_I();
      break;
    } 

    case GCMARKCONFIRM: {
      // received a marked phase finish confirm request msg
      // all cores should do mark
      processmsg_gcmarkconfirm_I();
      break;
    } 

    case GCMARKREPORT: {
      processmsg_gcmarkreport_I();
      break;
    } 

    case GCMARKEDOBJ: {
      processmsg_gcmarkedobj_I();
      break;
    } 

    case GCMOVESTART: {
      // received a start moving objs msg
      processmsg_gcmovestart_I();
      break;
    } 

    case GCLOBJREQUEST: {
      // received a large objs info request msg
      transferMarkResults_I();
      break;
    } 

    case GCLOBJINFO: {
      // received a large objs info response msg
      processmsg_gclobjinfo_I();
      break;
    } 

#ifdef GC_PROFILE
    case GCPROFILES: {
      // received a gcprofiles msg
      processmsg_gcprofiles_I();
      break;
    }
#endif // GC_PROFILE

#ifdef GC_CACHE_ADAPT
    case GCSTARTPREF: {
      // received a gcstartpref msg
      processmsg_gcstartpref_I();
      break;
    }

    case GCFINISHPREF: {
      // received a gcfinishpref msg
      processmsg_gcfinishpref_I();
      break;
    }
#endif
#endif 

    default:
      break;
    }

    if((msgdataindex != msgdatalast) || (msgdatafull)) {
      // still have available msg
      goto processmsg;
    }

    // have new coming msg
    if(BAMBOO_MSG_AVAIL() != 0) {
      goto msg;
    } 

    PROFILE_INTERRUPT_END();
    return (int)type;
  } else {
    // not a whole msg
    return -2;
  }
}
#endif // MULTICORE
