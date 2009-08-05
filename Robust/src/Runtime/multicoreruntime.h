#ifndef MULTICORE_RUNTIME
#define MULTICORE_RUNTIME

////////////////////////////////////////////////////////////////
// global variables                                          //
///////////////////////////////////////////////////////////////

// data structures for msgs
#define BAMBOO_OUT_BUF_LENGTH 300
int msgdata[30];
int msgtype;
int msgdataindex;
int msglength;
int outmsgdata[BAMBOO_OUT_BUF_LENGTH];
int outmsgindex;
int outmsglast;
int outmsgleft;
bool isMsgHanging;
volatile bool isMsgSending;

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
 *       f -- requiring for new memory
 *      10 -- response for new memory request
 *      11 -- GC start
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
 *            d + status + corenum + sendobjs + receiveobjs (size is always 5 * sizeof(int))
 *            status: 0 -- stall; 1 -- busy
 * TerminateMsg: e (size is always 1 * sizeof(int)
 * MemoryMsg: f + size + corenum (size is always 3 * sizeof(int))
 *           10 + base_va + size (size is always 3 * sizeof(int))
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
 *        1f + size of msg + corenum + current heap size + (num of large obj lists + (start address + length)+)?
 */
enum MSGTYPE {
	TRANSOBJ = 0x0,  // 0x0
	TRANSTALL,       // 0x1
	LOCKREQUEST,     // 0x2
	LOCKGROUNT,      // 0x3
	LOCKDENY,        // 0x4
	LOCKRELEASE,     // 0x5
	PROFILEOUTPUT,   // 0x6
	PROFILEFINISH,   // 0x7
	REDIRECTLOCK,    // 0x8
	REDIRECTGROUNT,  // 0x9
	REDIRECTDENY,    // 0xa
	REDIRECTRELEASE, // 0xb
	STATUSCONFIRM,   // 0xc
	STATUSREPORT,    // 0xd
	TERMINATE,       // 0xe
	MEMREQUEST,      // 0xf
	MEMRESPONSE,     // 0x10
#ifdef MULTICORE_GC
	GCSTART,         // 0x11
	GCSTARTCOMPACT,  // 0x12
	GCSTARTFLUSH,    // 0x13
	GCFINISHMARK,    // 0x14
	GCFINISHCOMPACT, // 0x15
	GCFINISHFLUSH,   // 0x16
	GCFINISH,        // 0x17
	GCMARKCONFIRM,   // 0x18
	GCMARKREPORT,    // 0x19
	GCMARKEDOBJ,     // 0x1a
	GCMOVESTART,     // 0x1b
	GCMAPREQUEST,    // 0x1c
	GCMAPINFO,       // 0x1d
	GCLOBJREQUEST,   // 0x1e
	GCLOBJINFO,      // 0x1f
#endif
	MSGEND
};

// data structures of status for termination
int corestatus[NUMCORES]; // records status of each core
                          // 1: running tasks
                          // 0: stall
int numsendobjs[NUMCORES]; // records how many objects a core has sent out
int numreceiveobjs[NUMCORES]; // records how many objects a core has received
int numconfirm;
bool waitconfirm;
bool busystatus;
int self_numsendobjs;
int self_numreceiveobjs;

// get rid of lock msgs for GC version
#ifndef MULTICORE_GC
// data structures for locking
struct RuntimeHash * objRedirectLockTbl;
int lockobj;
int lock2require;
int lockresult;
bool lockflag;
#endif

// data structures for waiting objs
struct Queue objqueue;

// data structures for shared memory allocation
#ifdef MULTICORE_GC
#include "multicoregarbage.h"
#else
#define BAMBOO_NUM_PAGES 1024 * 512
#define BAMBOO_PAGE_SIZE 4096
#define BAMBOO_SHARED_MEM_SIZE BAMBOO_PAGE_SIZE * BAMBOO_PAGE_SIZE
#define BAMBOO_BASE_VA 0xd000000
#define BAMBOO_SMEM_SIZE 16 * BAMBOO_PAGE_SIZE

bool smemflag;
mspace bamboo_free_msp;
mspace bamboo_cur_msp;
int bamboo_smem_size;
#endif

// for test TODO
int total_num_t6;

// data structures for profile mode
#ifdef PROFILE

#define TASKINFOLENGTH 30000
//#define INTERRUPTINFOLENGTH 500

bool stall;
//bool isInterrupt;
int totalexetime;

typedef struct task_info {
  char* taskName;
  unsigned long long startTime;
  unsigned long long endTime;
  unsigned long long exitIndex;
  struct Queue * newObjs; 
} TaskInfo;

/*typedef struct interrupt_info {
   int startTime;
   int endTime;
   } InterruptInfo;*/

TaskInfo * taskInfoArray[TASKINFOLENGTH];
int taskInfoIndex;
bool taskInfoOverflow;
/*InterruptInfo * interruptInfoArray[INTERRUPTINFOLENGTH];
   int interruptInfoIndex;
   bool interruptInfoOverflow;*/
int profilestatus[NUMCORES]; // records status of each core
                             // 1: running tasks
                             // 0: stall
#endif // #ifdef PROFILE

#ifndef INTERRUPT
bool reside;
#endif
/////////////////////////////////////////////////////////////

////////////////////////////////////////////////////////////
// these are functions should be implemented in           //
// multicore runtime for any multicore processors         //
////////////////////////////////////////////////////////////
#ifdef TASK
#ifdef MULTICORE
inline void initialization(void) __attribute__((always_inline));
inline void initCommunication(void) __attribute__((always_inline));
inline void fakeExecution(void) __attribute__((always_inline));
inline void terminate(void) __attribute__((always_inline));

// lock related functions
bool getreadlock(void* ptr);
void releasereadlock(void* ptr);
bool getwritelock(void* ptr);
void releasewritelock(void* ptr);
bool getwritelock_I(void* ptr);
void releasewritelock_I(void * ptr);
/* this function is to process lock requests. 
 * can only be invoked in receiveObject() */
// if return -1: the lock request is redirected
//            0: the lock request is approved
//            1: the lock request is denied
inline int processlockrequest(int locktype, int lock, int obj, int requestcore, int rootrequestcore, bool cache) __attribute_((always_inline));
inline void processlockrelease(int locktype, int lock, int redirectlock, bool isredirect) __attribute_((always_inline));

// msg related functions
inline void send_msg_1(int targetcore, unsigned long n0) __attribute__((always_inline));
inline void send_msg_2(int targetcore, unsigned long n0, unsigned long n1) __attribute__((always_inline));
inline void send_msg_3(int targetcore, unsigned long n0, unsigned long n1, unsigned long n2) __attribute__((always_inline));
inline void send_msg_4(int targetcore, unsigned long n0, unsigned long n1, unsigned long n2, unsigned long n3) __attribute__((always_inline));
inline void send_msg_5(int targetcore, unsigned long n0, unsigned long n1, unsigned long n2, unsigned long n3, unsigned long n4) __attribute__((always_inline));
inline void send_msg_6(int targetcore, unsigned long n0, unsigned long n1, unsigned long n2, unsigned long n3, unsigned long n4, unsigned long n5) __attribute__((always_inline));
inline void cache_msg_2(int targetcore, unsigned long n0, unsigned long n1) __attribute__((always_inline));
inline void cache_msg_3(int targetcore, unsigned long n0, unsigned long n1, unsigned long n2) __attribute__((always_inline));
inline void cache_msg_4(int targetcore, unsigned long n0, unsigned long n1, unsigned long n2, unsigned long n3) __attribute__((always_inline));
inline void cache_msg_5(int targetcore, unsigned long n0, unsigned long n1, unsigned long n2, unsigned long n3, unsigned long n4) __attribute__((always_inline));
inline void cache_msg_6(int targetcore, unsigned long n0, unsigned long n1, unsigned long n2, unsigned long n3, unsigned long n4, unsigned long n5) __attribute__((always_inline));
inline void transferObject(struct transObjInfo * transObj);
inline int receiveMsg(void) __attribute__((always_inline));
inline int receiveGCMsg(void) __attribute__((always_inline));

#ifdef PROFILE
inline void profileTaskStart(char * taskname) __attribute__((always_inline));
inline void profileTaskEnd(void) __attribute__((always_inline));
void outputProfileData();
#endif  // #ifdef PROFILE
///////////////////////////////////////////////////////////

//////////////////////////////////////////////////////////////////////////////////////
//  For each version of BAMBOO runtime, there should be a header file named        //
//  runtim_arch.h defining following MARCOS:                                       //
//  BAMBOO_TOTALCORE: the total # of cores available in the processor              //
//  BAMBOO_NUM_OF_CORE: the # of current residing core                             //
//  BAMBOO_GET_NUM_OF_CORE(): compute the # of current residing core               //
//  BAMBOO_DEBUGPRINT(x): print out integer x                                      //
//  BAMBOO_DEBUGPRINT_REG(x): print out value of variable x                        //
//  BAMBOO_LOCAL_MEM_CALLOC(x, y): allocate an array of x elements each of whose   //
//                                 size in bytes is y on local memory              //
//  BAMBOO_LOCAL_MEM_FREE(x): free space with ptr x on local memory                //
//  BAMBOO_SHARE_MEM_CALLOC(x, y): allocate an array of x elements each of whose   //
//                                 size in bytes is y on shared memory             //
//  BAMBOO_START_CRITICAL_SECTION_OBJ_QUEUE()                                      //
//  BAMBOO_CLOSE_CRITICAL_SECTION_OBJ_QUEUE(): locks for global data structures    //
//                                             related to obj queue                //
//  BAMBOO_START_CRITICAL_SECTION_STATUS()                                         //
//  BAMBOO_CLOSE_CRITICAL_SECTION_STATUS(): locks for global data structures       //
//                                          related to status data                 //
//  BAMBOO_START_CRITICAL_SECTION_MSG()                                            //
//  BAMBOO_CLOSE_CRITICAL_SECTION_MSG(): locks for global data structures related  //
//                                       to msg data                               //
//  BAMBOO_START_CRITICAL_SECTION_LOCK()                                           //
//  BAMBOO_CLOSE_CRITICAL_SECTION_LOCK(): locks for global data structures related //
//                                        to lock table                            //
//  BAMBOO_START_CRITICAL_SECTION_MEM()                                            //
//  BAMBOO_CLOSE_CRITICAL_SECTION_MEM(): locks for allocating memory               //
//  BAMBOO_START_CRITICAL_SECTION()                                                //
//  BAMBOO_CLOSE_CRITICAL_SECTION(): locks for all global data structures          //
//  BAMBOO_WAITING_FOR_LOCK(): routine executed while waiting for lock request     //
//                             response                                            //
//  BAMBOO_CACHE_LINE_SIZE: the cache line size                                    //
//  BAMBOO_CACHE_LINE_MASK: mask for a cache line                                  //
//  BAMBOO_CACHE_FLUSH_RANGE(x, y): flush cache lines started at x with length y   //
//  BAMBOO_CACHE_FLUSH_ALL(): flush the whole cache of a core if necessary         //
//  BAMBOO_EXIT(x): exit routine                                                   //
//  BAMBOO_MSG_AVAIL(): checking if there are msgs coming in                       //
//  BAMBOO_GCMSG_AVAIL(): checking if there are gcmsgs coming in                   //
//  BAMBOO_GET_EXE_TIME(): rountine to get current clock cycle number              //
/////////////////////////////////////////////////////////////////////////////////////

#endif  // #ifdef MULTICORE
#endif  // #ifdef TASK
#endif  // #ifndef MULTICORE_RUNTIME
