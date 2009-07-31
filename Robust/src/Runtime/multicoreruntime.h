#ifndef MULTICORE_RUNTIME
#define MULTICORE_RUNTIME

////////////////////////////////////////////////////////////////
// global variables                                          //
///////////////////////////////////////////////////////////////

// data structures for msgs
int msgdata[30];
int msgtype;
int msgdataindex;
int msglength;
#define BAMBOO_OUT_BUF_LENGTH 300
int outmsgdata[BAMBOO_OUT_BUF_LENGTH];
int outmsgindex;
int outmsglast;
int outmsgleft;
bool isMsgHanging;
volatile bool isMsgSending;

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

// data structures for locking
struct RuntimeHash * objRedirectLockTbl;
int lockobj;
int lock2require;
int lockresult;
bool lockflag;

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
