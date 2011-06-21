#ifndef BAMBOO_MULTICORE_MSG_H
#define BAMBOO_MULTICORE_MSG_H
#include "multicore.h"
#ifdef MULTICORE
// data structures for msgs
#define BAMBOO_OUT_BUF_LENGTH 2048
#define BAMBOO_OUT_BUF_MASK (0x7FF)
#define BAMBOO_MSG_BUF_LENGTH 2048
#define BAMBOO_MSG_BUF_MASK (0x7FF)
int msgdata[BAMBOO_MSG_BUF_LENGTH];
volatile int msgdataindex;
volatile int msgdatalast;
volatile bool msgdatafull;
int outmsgdata[BAMBOO_OUT_BUF_LENGTH];
int outmsgindex;
int outmsglast;
int outmsgleft;
volatile bool isMsgHanging;

#define MSG_INDEXINC_I() \
  msgdataindex = (msgdataindex + 1) & (BAMBOO_MSG_BUF_MASK) 

#define MSG_LASTINDEXINC_I() \
  msgdatalast = (msgdatalast + 1) & (BAMBOO_MSG_BUF_MASK)

#define MSG_CACHE_I(n) \
  msgdata[msgdatalast] = (n); \
  MSG_LASTINDEXINC_I()

#define MSG_REMAINSIZE_I(s)				       \
  if(msgdataindex < msgdatalast) {			       \
    s = msgdatalast - msgdataindex;			       \
  } else if((msgdataindex == msgdatalast) && (!msgdatafull)) { \
    s = 0;						       \
  } else {						       \
    s = (BAMBOO_MSG_BUF_LENGTH) - msgdataindex + msgdatalast;  \
  }

#define OUTMSG_INDEXINC() \
  outmsgindex = (outmsgindex + 1) & (BAMBOO_OUT_BUF_MASK)

#define OUTMSG_LASTINDEXINC() \
  outmsglast = (outmsglast + 1) & (BAMBOO_OUT_BUF_MASK); \
  if(outmsglast == outmsgindex) { \
    BAMBOO_EXIT(); \
  }

#define OUTMSG_CACHE(n) \
  outmsgdata[outmsglast] = (n); \
  OUTMSG_LASTINDEXINC();

/* Message format:
 *      type + Msgbody
 * type: 1 -- transfer object
 *       2 -- transfer stall msg
 *       3 -- lock request
 *       4 -- lock grount
 *       5 -- lock deny
 *       6 -- lock release
 *       // add for profile info
 *       7 -- transfer profile output msg
 *       8 -- transfer profile output finish msg
 *       // add for alias lock strategy
 *       9 -- redirect lock request
 *       a -- lock grant with redirect info
 *       b -- lock deny with redirect info
 *       c -- lock release with redirect info
 *       d -- status confirm request
 *       e -- status report msg
 *       f -- terminate
 *      10 -- requiring for new memory
 *      11 -- response for new memory request
 *      12 -- GC init phase start
 *      13 -- GC start
 *      14 -- compact phase start
 *      15 -- update phase start
 *      16 -- init phase finish
 *      17 -- mark phase finish
 *      18 -- compact phase finish
 *      19 -- update phase finish
 *      1a -- GC finish
 *      1b -- marked phase finish confirm request
 *      1c -- marked phase finish confirm response
 *      1d -- markedObj msg
 *      1e -- start moving objs msg
 *      1f -- ask for mapping info of a markedObj
 *      20 -- mapping info of a markedObj
 *      21 -- large objs info request
 *      22 -- large objs info response
 *      23 -- large objs mapping info
 *
 * ObjMsg: 1 + size of msg + obj's address + (task index + param index)+
 * StallMsg: 2 + corenum + sendobjs + receiveobjs
 *             (size is always 4 * sizeof(int))
 * LockMsg: 3 + lock type + obj pointer + lock + request core
 *            (size is always 5 * sizeof(int))
 *          4/5/6 + lock type + obj pointer + lock
 *            (size is always 4 * sizeof(int))
 *          9 + lock type + obj pointer +  redirect lock + root request core
 *            + request core
 *            (size is always 6 * sizeof(int))
 *          a/b + lock type + obj pointer + redirect lock
 *              (size is always 4 * sizeof(int))
 *          c + lock type + lock + redirect lock
 *            (size is always 4 * sizeof(int))
 *          lock type: 0 -- read; 1 -- write
 * ProfileMsg: 7 + totalexetime
 *               (size is always 2 * sizeof(int))
 *             8 
 *               (size is always sizeof(int))
 * StatusMsg: d (size is always 1 * sizeof(int))
 *            e + status + corenum + sendobjs + receiveobjs
 *              (size is always 5 * sizeof(int))
 *            status: 0 -- stall; 1 -- busy
 * TerminateMsg: f (size is always 1 * sizeof(int)
 * MemoryMsg: 10 + size + corenum
 *              (size is always 3 * sizeof(int))
 *           11 + base_va + size
 *              (size is always 3 * sizeof(int))
 * GCMsg: 12/13 (size is always 1 * sizeof(int))
 *        14 + size of msg + (num of objs to move + (start address
 *           + end address + dst core + start dst)+)?
 *           + (num of incoming objs + (start dst + orig core)+)?
 *           + (num of large obj lists + (start address + lenght
 *           + start dst)+)?
 *        15 (size is always 1 * sizeof(int))
 *        16 + corenum
 *           (size is always 2 * sizeof(int))
 *        17 + corenum + gcsendobjs + gcreceiveobjs
 *           (size if always 4 * sizeof(int))
 *        18 + corenum + fulfilled blocks num + (finish compact(1) + current
 *           heap top)/(need mem(0) + mem need)
 *           size is always 5 * sizeof(int))
 *        19 + corenum
 *              (size is always 2 * sizeof(int))
 *        1a (size is always 1 * sizeof(int))
 *        1b (size if always 1 * sizeof(int))
 *        1c + size of msg + corenum + gcsendobjs + gcreceiveobjs
 *           (size is always 5 * sizeof(int))
 *        1d + obj's address + request core
 *           (size is always 3 * sizeof(int))
 *        1e + corenum + start addr + end addr
 *           (size if always 4 * sizeof(int))
 *        1f + obj's address + corenum
 *           (size is always 3 * sizeof(int))
 *        20 + obj's address + dst address
 *           (size if always 3 * sizeof(int))
 *        21 (size is always 1 * sizeof(int))
 *        22 + size of msg + corenum + current heap size
 *           + (num of large obj lists + (start address + length)+)?
 *        23 + orig large obj ptr + new large obj ptr
 *            (size is always 3 * sizeof(int))
 */
typedef enum {
  MSGSTART = 0x0,        // 0xD0
  TRANSOBJ,              // 0xD1
  TRANSTALL,             // 0xD2
  LOCKREQUEST,           // 0xD3
  LOCKGROUNT,            // 0xD4
  LOCKDENY,              // 0xD5
  LOCKRELEASE,           // 0xD6
  PROFILEOUTPUT,         // 0xD7
  PROFILEFINISH,         // 0xD8
  REDIRECTLOCK,          // 0xD9
  REDIRECTGROUNT,        // 0xDa
  REDIRECTDENY,          // 0xDb
  REDIRECTRELEASE,       // 0xDc
  STATUSCONFIRM,         // 0xDd
  STATUSREPORT,          // 0xDe
  TERMINATE,             // 0xDf
  MEMREQUEST,            // 0xE0
  MEMRESPONSE,           // 0xE1
#ifdef MULTICORE_GC
  GCSTARTPRE,            // 0xE2
  GCSTARTINIT,           // 0xE3
  GCSTART,               // 0xE4
  GCSTARTCOMPACT,        // 0xE5
  GCSTARTUPDATE,          // 0xE6
  GCFINISHPRE,           // 0xE7
  GCFINISHINIT,          // 0xE8
  GCFINISHMARK,          // 0xE9
  GCFINISHCOMPACT,       // 0xEa
  GCRETURNMEM,
  GCFINISHUPDATE,         // 0xEb
  GCFINISH,              // 0xEc
  GCMARKCONFIRM,         // 0xEd
  GCMARKREPORT,          // 0xEe
  GCMARKEDOBJ,           // 0xEf
  GCMOVESTART,           // 0xF0
  GCLOBJREQUEST,         // 0xF1   
  GCREQBLOCK,
  GCGRANTBLOCK,  
  GCLOBJINFO,            // 0xF2
#ifdef GC_PROFILE
  GCPROFILES,            // 0xF3
#endif // GC_PROFILE
#ifdef GC_CACHE_ADAPT
  GCSTARTCACHEPOLICY,    // 0xF4
  GCFINISHCACHEPOLICY,   // 0xF5
  GCSTARTPREF,           // 0xF6
  GCFINISHPREF,          // 0xF7
#endif // GC_CACHE_ADAPT
#endif // MULTICORE_GC
  MSGEND
} MSGTYPE;

// msg related functions
void send_msg_1(int targetcore,unsigned long n0);
void send_msg_2(int targetcore,unsigned long n0,unsigned long n1);
void send_msg_3(int targetcore,unsigned long n0,unsigned long n1,unsigned long n2);
void send_msg_4(int targetcore,unsigned long n0,unsigned long n1,unsigned long n2,unsigned long n3);
void send_msg_5(int targetcore,unsigned long n0,unsigned long n1,unsigned long n2,unsigned long n3,unsigned long n4);
void send_msg_6(int targetcore,unsigned long n0,unsigned long n1,unsigned long n2,unsigned long n3,unsigned long n4,unsigned long n5);
void send_msg_1_I(int targetcore,unsigned long n0);
void send_msg_2_I(int targetcore,unsigned long n0,unsigned long n1);
void send_msg_3_I(int targetcore,unsigned long n0,unsigned long n1,unsigned long n2);
void send_msg_4_I(int targetcore,unsigned long n0,unsigned long n1,unsigned long n2,unsigned long n3);
void send_msg_5_I(int targetcore,unsigned long n0,unsigned long n1,unsigned long n2,unsigned long n3,unsigned long n4);
void send_msg_6_I(int targetcore,unsigned long n0,unsigned long n1,unsigned long n2,unsigned long n3,unsigned long n4,unsigned long n5);
void cache_msg_1_I(int targetcore,unsigned long n0);
void cache_msg_2_I(int targetcore,unsigned long n0,unsigned long n1);
void cache_msg_3_I(int targetcore,unsigned long n0,unsigned long n1,unsigned long n2);
void cache_msg_4_I(int targetcore,unsigned long n0,unsigned long n1,unsigned long n2,unsigned long n3);
void cache_msg_5_I(int targetcore,unsigned long n0,unsigned long n1,unsigned long n2,unsigned long n3,unsigned long n4);
void cache_msg_6_I(int targetcore,unsigned long n0,unsigned long n1,unsigned long n2,unsigned long n3,unsigned long n4,unsigned long n5);
int receiveMsg_I();
#ifdef TASK
void transferObject(struct transObjInfo * transObj);
#endif

#ifdef MULTICORE_GC
void transferMarkResults();
#endif 

#endif // MULTICORE
#endif // BAMBOO_MULTICORE_MSG_H
