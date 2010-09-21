#ifndef MULTICORE_RUNTIME
#define MULTICORE_RUNTIME

#ifndef INLINE
#define INLINE    inline __attribute__((always_inline))
#endif

#ifndef bool
#define bool int
#define true 1
#define false 0
#endif

////////////////////////////////////////////////////////////////
// global variables                                          //
///////////////////////////////////////////////////////////////

// record the starting time
unsigned long long bamboo_start_time;

// data structures for msgs
#define BAMBOO_OUT_BUF_LENGTH 2048
#define BAMBOO_OUT_BUF_MASK (0x7FF)
#define BAMBOO_MSG_BUF_LENGTH 2048
#define BAMBOO_MSG_BUF_MASK (0x7FF)
int msgdata[BAMBOO_MSG_BUF_LENGTH];
volatile int msgdataindex;
volatile int msgdatalast;
int msglength;
volatile bool msgdatafull;
int outmsgdata[BAMBOO_OUT_BUF_LENGTH];
int outmsgindex;
int outmsglast;
int outmsgleft;
volatile bool isMsgHanging;
//volatile bool isMsgSending;

#define MSG_INDEXINC_I() \
  msgdataindex = (msgdataindex + 1) & (BAMBOO_MSG_BUF_MASK) //% (BAMBOO_MSG_BUF_LENGTH)

#define MSG_LASTINDEXINC_I() \
  msgdatalast = (msgdatalast + 1) & (BAMBOO_MSG_BUF_MASK) // % (BAMBOO_MSG_BUF_LENGTH)

#define MSG_CACHE_I(n) \
  msgdata[msgdatalast] = (n); \
  MSG_LASTINDEXINC_I()

// NOTE: if msgdataindex == msgdatalast, it always means that the buffer if
//       full. In the case that the buffer is empty, should never call this
//       MACRO
#define MSG_REMAINSIZE_I(s) \
  if(msgdataindex < msgdatalast) { \
    (*(int*)s) = msgdatalast - msgdataindex; \
  } else if((msgdataindex == msgdatalast) && (!msgdatafull)) { \
    (*(int*)s) = 0; \
  } else { \
    (*(int*)s) = (BAMBOO_MSG_BUF_LENGTH) - msgdataindex + msgdatalast; \
  }

#define OUTMSG_INDEXINC() \
  outmsgindex = (outmsgindex + 1) & (BAMBOO_OUT_BUF_MASK) //% (BAMBOO_OUT_BUF_LENGTH)

#define OUTMSG_LASTINDEXINC() \
  outmsglast = (outmsglast + 1) & (BAMBOO_OUT_BUF_MASK) //% (BAMBOO_OUT_BUF_LENGTH); \
  if(outmsglast == outmsgindex) { \
    BAMBOO_EXIT(0xdd01); \
  }

#define OUTMSG_CACHE(n) \
  outmsgdata[outmsglast] = (n); \
  OUTMSG_LASTINDEXINC();

#define MAX_PACKET_WORDS 5

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
 *      15 -- flush phase start
 *      16 -- init phase finish
 *      17 -- mark phase finish
 *      18 -- compact phase finish
 *      19 -- flush phase finish
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
 *             8 + corenum
 *               (size is always 2 * sizeof(int))
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
  MSGSTART = 0xD0,       // 0xD0
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
  GCSTARTMAPINFO,        // 0xE6
  GCSTARTFLUSH,          // 0xE7
  GCFINISHPRE,           // 0xE8
  GCFINISHINIT,          // 0xE9
  GCFINISHMARK,          // 0xEa
  GCFINISHCOMPACT,       // 0xEb
  GCFINISHMAPINFO,       // 0xEc
  GCFINISHFLUSH,         // 0xEd
  GCFINISH,              // 0xEe
  GCMARKCONFIRM,         // 0xEf
  GCMARKREPORT,          // 0xF0
  GCMARKEDOBJ,           // 0xF1
  GCMOVESTART,           // 0xF2
  GCMAPREQUEST,          // 0xF3
  GCMAPINFO,             // 0xF4
  GCMAPTBL,              // 0xF5
  GCLOBJREQUEST,         // 0xF6
  GCLOBJINFO,            // 0xF7
  GCLOBJMAPPING,         // 0xF8
#ifdef GC_PROFILE
  GCPROFILES,            // 0xF9
#endif
#ifdef GC_CACHE_ADAPT
  GCSTARTPOSTINIT,       // 0xFa
  GCSTARTPREF,           // 0xFb
  GCFINISHPOSTINIT,      // 0xFc
  GCFINISHPREF,          // 0xFd
#endif // GC_CACHE_ADAPT
#endif
  MSGEND
} MSGTYPE;

/////////////////////////////////////////////////////////////////////////////////
// NOTE: BAMBOO_TOTALCORE -- number of the available cores in the processor.
//                           No greater than the number of all the cores in
//                           the processor
//       NUMCORES -- number of cores chosen to deploy the application. It can
//                   be greater than that required to fully parallelize the
//                   application. The same as NUMCORES.
//       NUMCORESACTIVE -- number of cores that really execute the
//                         application. No greater than NUMCORES
//       NUMCORES4GC -- number of cores for gc. No greater than NUMCORES.
//                      NOTE: currently only support ontinuous cores as gc
//                            cores, i.e. 0~NUMCORES4GC-1
////////////////////////////////////////////////////////////////////////////////
// data structures of status for termination
// only check working cores
volatile int corestatus[NUMCORESACTIVE]; // records status of each core
                                         // 1: running tasks
                                         // 0: stall
volatile int numsendobjs[NUMCORESACTIVE]; // records how many objects a core
                                          // has sent out
volatile int numreceiveobjs[NUMCORESACTIVE]; // records how many objects a
                                             // core has received
volatile int numconfirm;
volatile bool waitconfirm;
bool busystatus;
int self_numsendobjs;
int self_numreceiveobjs;

// get rid of lock msgs for GC version
#ifndef MULTICORE_GC
// data structures for locking
struct RuntimeHash locktable;
static struct RuntimeHash* locktbl = &locktable;
struct RuntimeHash * lockRedirectTbl;
struct RuntimeHash * objRedirectLockTbl;
#endif
struct LockValue {
  int redirectlock;
  int value;
};
int lockobj;
int lock2require;
int lockresult;
bool lockflag;

// data structures for waiting objs
struct Queue objqueue;
struct Queue * totransobjqueue; // queue to hold objs to be transferred
                                // should be cleared whenever enter a task

// data structures for shared memory allocation
#ifdef TILERA_BME
#define BAMBOO_BASE_VA 0xd000000
#elif defined TILERA_ZLINUX
#ifdef MULTICORE_GC
#define BAMBOO_BASE_VA 0xd000000
#endif // MULTICORE_GC
#endif // TILERA_BME

#ifdef BAMBOO_MEMPROF
#define GC_BAMBOO_NUMCORES 56
#else
#define GC_BAMBOO_NUMCORES 62
#endif

#ifdef GC_DEBUG
#include "structdefs.h"
#define BAMBOO_NUM_BLOCKS (NUMCORES4GC*(2+1)+3)
#define BAMBOO_PAGE_SIZE (64 * 64)
#define BAMBOO_SMEM_SIZE (BAMBOO_PAGE_SIZE)
#define BAMBOO_SHARED_MEM_SIZE ((BAMBOO_SMEM_SIZE) *(BAMBOO_NUM_BLOCKS))

#elif defined GC_CACHE_ADAPT
#define BAMBOO_NUM_BLOCKS ((GC_BAMBOO_NUMCORES)*(2+14))
#define BAMBOO_PAGE_SIZE (64 * 1024) // 64K
#ifdef GC_LARGEPAGESIZE
#define BAMBOO_SMEM_SIZE (16 * (BAMBOO_PAGE_SIZE))
#elif defined GC_SMALLPAGESIZE
#define BAMBOO_SMEM_SIZE (BAMBOO_PAGE_SIZE)
#elif defined GC_SMALLPAGESIZE2
#define BAMBOO_PAGE_SIZE (16 * 1024)  // (4096)
#define BAMBOO_SMEM_SIZE (BAMBOO_PAGE_SIZE)
#else
#define BAMBOO_SMEM_SIZE (4 * (BAMBOO_PAGE_SIZE))
#endif // GC_LARGEPAGESIZE
#define BAMBOO_SHARED_MEM_SIZE ((BAMBOO_SMEM_SIZE) * (BAMBOO_NUM_BLOCKS))

#else // GC_DEBUG
#ifdef GC_LARGESHAREDHEAP
#define BAMBOO_NUM_BLOCKS ((GC_BAMBOO_NUMCORES)*(2+2))
#elif defined GC_LARGESHAREDHEAP2
#define BAMBOO_NUM_BLOCKS ((GC_BAMBOO_NUMCORES)*(2+2))
#else
#define BAMBOO_NUM_BLOCKS ((GC_BAMBOO_NUMCORES)*(2+3)) //(15 * 1024) //(64 * 4 * 0.75) //(1024 * 1024 * 3.5)  3G
#endif
#ifdef GC_LARGEPAGESIZE
#define BAMBOO_PAGE_SIZE (4 * 1024 * 1024)  // (4096)
#define BAMBOO_SMEM_SIZE (BAMBOO_PAGE_SIZE)
#elif defined GC_SMALLPAGESIZE
#define BAMBOO_PAGE_SIZE (256 * 1024)  // (4096)
#define BAMBOO_SMEM_SIZE (BAMBOO_PAGE_SIZE)
#elif defined GC_SMALLPAGESIZE2
#define BAMBOO_PAGE_SIZE (64 * 1024)  // (4096)
#define BAMBOO_SMEM_SIZE (BAMBOO_PAGE_SIZE)
#else
#define BAMBOO_PAGE_SIZE (1024 * 1024)  // (4096)
#define BAMBOO_SMEM_SIZE (BAMBOO_PAGE_SIZE)
#endif // GC_LARGEPAGESIZE
#define BAMBOO_SHARED_MEM_SIZE ((BAMBOO_SMEM_SIZE) * (BAMBOO_NUM_BLOCKS)) //(1024 * 1024 * 240)
//((unsigned long long int)(3.0 * 1024 * 1024 * 1024)) // 3G 
#endif // GC_DEBUG

#ifdef MULTICORE_GC
volatile bool gc_localheap_s;
#endif

#ifdef MULTICORE_GC
#include "multicoregarbage.h"

typedef enum {
  SMEMLOCAL = 0x0,// 0x0, using local mem only
  SMEMFIXED,      // 0x1, use local mem in lower address space(1 block only)
                  //      and global mem in higher address space
  SMEMMIXED,      // 0x2, like FIXED mode but use a threshold to control
  SMEMGLOBAL,     // 0x3, using global mem only
  SMEMEND
} SMEMSTRATEGY;

SMEMSTRATEGY bamboo_smem_mode; //-DSMEML: LOCAL; -DSMEMF: FIXED;
                               //-DSMEMM: MIXED; -DSMEMG: GLOBAL;

struct freeMemItem {
  INTPTR ptr;
  int size;
  int startblock;
  int endblock;
  struct freeMemItem * next;
};

struct freeMemList {
  struct freeMemItem * head;
  struct freeMemItem * backuplist; // hold removed freeMemItem for reuse;
                                   // only maintain 1 freemMemItem
};

// table recording the number of allocated bytes on each block
// Note: this table resides on the bottom of the shared heap for all cores
//       to access
volatile int * bamboo_smemtbl;
volatile int bamboo_free_block;
//bool bamboo_smem_flushed;
//struct freeMemList * bamboo_free_mem_list;
int bamboo_reserved_smem; // reserved blocks on the top of the shared heap
                          // e.g. 20% of the heap and should not be allocated
                          // otherwise gc is invoked
volatile INTPTR bamboo_smem_zero_top;
#define BAMBOO_SMEM_ZERO_UNIT_SIZE (4 * 1024) // 4KB

#ifdef GC_CACHE_ADAPT
typedef union
{
  unsigned int word;
  struct
  {
    // policy type
    unsigned int cache_mode   : 2;
	// Reserved.
    unsigned int __reserved_0 : 6;
	// Location Override Target Y
    unsigned int lotar_y      : 4;
    // Reserved.
    unsigned int __reserved_1 : 4;
    // Location Override Target X
    unsigned int lotar_x      : 4;
    // Reserved.
    unsigned int __reserved_2 : 12;
  };
} bamboo_cache_policy_t;
#endif // GC_CACHE_ADAPT
#else
//volatile mspace bamboo_free_msp;
INTPTR bamboo_free_smemp;
int bamboo_free_smem_size;
#endif
volatile bool smemflag;
volatile INTPTR bamboo_cur_msp;
volatile int bamboo_smem_size;

// for test TODO
int total_num_t6;

// data structures for profile mode
#ifdef PROFILE

#define TASKINFOLENGTH 3000 // 0
#ifdef PROFILE_INTERRUPT
#define INTERRUPTINFOLENGTH 50 //0
#endif // PROFILE_INTERRUPT

bool stall;
//bool isInterrupt;
int totalexetime;
//unsigned long long interrupttime;

typedef struct task_info {
  char* taskName;
  unsigned long long startTime;
  unsigned long long endTime;
  unsigned long long exitIndex;
  struct Queue * newObjs;
} TaskInfo;

TaskInfo * taskInfoArray[TASKINFOLENGTH];
int taskInfoIndex;
bool taskInfoOverflow;
#ifdef PROFILE_INTERRUPT
typedef struct interrupt_info {
  unsigned long long startTime;
  unsigned long long endTime;
} InterruptInfo;

InterruptInfo * interruptInfoArray[INTERRUPTINFOLENGTH];
int interruptInfoIndex;
bool interruptInfoOverflow;
#endif // PROFILE_INTERUPT
volatile int profilestatus[NUMCORESACTIVE]; // records status of each core
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
INLINE void initialization(void);
INLINE void initCommunication(void);
INLINE void fakeExecution(void);
INLINE void terminate(void);
INLINE void initlock(struct ___Object___ * v);
#ifdef BAMBOO_MEMPROF
INLINE void terminatememprof(void);
#endif

// lock related functions
bool getreadlock(void* ptr);
void releasereadlock(void* ptr);
bool getwritelock(void* ptr);
void releasewritelock(void* ptr);
bool getwritelock_I(void* ptr);
void releasewritelock_I(void * ptr);
#ifndef MULTICORE_GC
void releasewritelock_r(void * lock, void * redirectlock);
#endif
/* this function is to process lock requests.
 * can only be invoked in receiveObject() */
// if return -1: the lock request is redirected
//            0: the lock request is approved
//            1: the lock request is denied
INLINE int processlockrequest(int locktype,
                              int lock,
                              int obj,
                              int requestcore,
                              int rootrequestcore,
                              bool cache);
INLINE void processlockrelease(int locktype,
                               int lock,
                               int redirectlock,
                               bool redirect);

// msg related functions
INLINE void send_hanging_msg(bool isInterrupt);
INLINE void send_msg_1(int targetcore,
                       unsigned long n0,
					   bool isInterrupt);
INLINE void send_msg_2(int targetcore,
                       unsigned long n0,
                       unsigned long n1,
					   bool isInterrupt);
INLINE void send_msg_3(int targetcore,
                       unsigned long n0,
                       unsigned long n1,
                       unsigned long n2,
					   bool isInterrupt);
INLINE void send_msg_4(int targetcore,
                       unsigned long n0,
                       unsigned long n1,
                       unsigned long n2,
                       unsigned long n3,
					   bool isInterrupt);
INLINE void send_msg_5(int targetcore,
                       unsigned long n0,
                       unsigned long n1,
                       unsigned long n2,
                       unsigned long n3,
                       unsigned long n4,
					   bool isInterrupt);
INLINE void send_msg_6(int targetcore,
                       unsigned long n0,
                       unsigned long n1,
                       unsigned long n2,
                       unsigned long n3,
                       unsigned long n4,
                       unsigned long n5,
					   bool isInterrupt);
INLINE void cache_msg_1(int targetcore,
                        unsigned long n0);
INLINE void cache_msg_2(int targetcore,
                        unsigned long n0,
                        unsigned long n1);
INLINE void cache_msg_3(int targetcore,
                        unsigned long n0,
                        unsigned long n1,
                        unsigned long n2);
INLINE void cache_msg_4(int targetcore,
                        unsigned long n0,
                        unsigned long n1,
                        unsigned long n2,
                        unsigned long n3);
INLINE void cache_msg_5(int targetcore,
                        unsigned long n0,
                        unsigned long n1,
                        unsigned long n2,
                        unsigned long n3,
                        unsigned long n4);
INLINE void cache_msg_6(int targetcore,
                        unsigned long n0,
                        unsigned long n1,
                        unsigned long n2,
                        unsigned long n3,
                        unsigned long n4,
                        unsigned long n5);
INLINE void transferObject(struct transObjInfo * transObj);
INLINE int receiveMsg(uint32_t send_port_pending);

#ifdef MULTICORE_GC
INLINE void transferMarkResults();
#endif

#ifdef PROFILE
INLINE void profileTaskStart(char * taskname);
INLINE void profileTaskEnd(void);
void outputProfileData();
#endif  // #ifdef PROFILE
///////////////////////////////////////////////////////////

/////////////////////////////////////////////////////////////////////////////
// For each version of BAMBOO runtime, there should be a header file named //
// runtim_arch.h defining following MARCOS:                                //
// BAMBOO_NUM_OF_CORE: the # of current residing core                      //
// BAMBOO_GET_NUM_OF_CORE(): compute the # of current residing core        //
// BAMBOO_COORDS(c, x, y): convert the cpu # to coords (*x, *y)            //
// BAMBOO_DEBUGPRINT(x): print out integer x                               //
// BAMBOO_DEBUGPRINT_REG(x): print out value of variable x                 //
// BAMBOO_EXIT_APP(x): exit the whole application                          //
// BAMBOO_EXIT(x): error exit routine with error #                         //
// BAMBOO_DIE(x): error exit routine with error msg                        //
// BAMBOO_GET_EXE_TIME(): rountine to get current clock cycle number       //
// BAMBOO_MSG_AVAIL(): checking if there are msgs coming in                //
// BAMBOO_GCMSG_AVAIL(): checking if there are gcmsgs coming in            //
// BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT(): change to runtime mode from    //
//                                          client mode                    //
// BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME(): change to client mode from     //
//                                          runtime mode                   //
// BAMBOO_ENTER_SEND_MODE_FROM_CLIENT(): change to send mode from          //
//                                       client mode                       //
// BAMBOO_ENTER_CLIENT_MODE_FROM_SEND(): change to client mode from        //
//                                       send mode                         //
// BAMBOO_ENTER_RUNTIME_MODE_FROM_SEND(): change to runtime mode from      //
//                                        send mode                        //
// BAMBOO_ENTER_SEND_MODE_FROM_RUNTIME(): change to send mode from         //
//                                        runtime mode                     //
// BAMBOO_WAITING_FOR_LOCK(): routine executed while waiting for lock      //
//                            request response                             //
// BAMBOO_LOCAL_MEM_CALLOC(x, y): allocate an array of x elements each of  //
//                                whose size in bytes is y on local memory //
//                                which is given by the hypervisor         //
// BAMBOO_LOCAL_MEM_FREE(x): free space with ptr x on local memory         //
// BAMBOO_LOCAL_MEM_CLOSE(): close the local heap                          //
// BAMBOO_LOCAL_MEM_CALLOC_S(x, y): allocate an array of x elements each of//
//                                  whose size in bytes is y on local      //
//                                  memory which is not from the hypervisor//
//                                  but is allocated from the free memory  //
// BAMBOO_LOCAL_MEM_FREE_S(x): free space with ptr x on self-allocated     //
//                             local memory                                //
// BAMBOO_LOCAL_MEM_CLOSE_S(): close the self-allocated local heap        //
// BAMBOO_SHARE_MEM_CALLOC_I(x, y): allocate an array of x elements each of//
//                                whose size in bytes is y on shared memory//
// BAMBOO_SHARE_MEM_CLOSE(): close the shared heap                         //
// BAMBOO_CACHE_LINE_SIZE: the cache line size                             //
// BAMBOO_CACHE_LINE_MASK: mask for a cache line                           //
// BAMBOO_CACHE_FLUSH_RANGE(x, y): flush cache lines started at x with     //
//                                 length y                                //
// BAMBOO_CACHE_FLUSH_ALL(): flush the whole cache of a core if necessary  //
// BAMBOO_MEMSET_WH(x, y, z): memset the specified region of memory (start //
//                            address x, size z) to value y with write     //
//                            hint, the processor will not fetch the       //
//                            current content of the memory and directly   //
//                            write                                        //
// BAMBOO_CLEAN_DTLB(): zero-out all the dtlb entries                      //
// BAMBOO_CACHE_FLUSH_L2(): Flush the contents of this tile's L2 back to   //
//                          main memory                                    //
// BAMBOO_CACHE_FLUSH_RANGE_NO_FENCE(x, y): flush a range of mem without   //
//                                          mem fence                      //
// BAMBOO_CACHE_MEM_FENCE_INCOHERENT(): fence to guarantee visibility of   //
//                                      stores to incoherent memory        //
/////////////////////////////////////////////////////////////////////////////

#endif  // #ifdef MULTICORE
#endif  // #ifdef TASK
#endif  // #ifndef MULTICORE_RUNTIME
