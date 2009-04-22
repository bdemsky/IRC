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
int outmsgdata[30];
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
struct RuntimeHash locktable;
static struct RuntimeHash* locktbl = &locktable;
struct LockValue {
	int redirectlock;
	int value;
};
struct RuntimeHash * objRedirectLockTbl;
int lockobj;
int lock2require;
int lockresult;
bool lockflag;

// data structures for waiting objs
struct Queue objqueue;

// data structures for profile mode
#ifdef PROFILE

#define TASKINFOLENGTH 10000
//#define INTERRUPTINFOLENGTH 500

bool stall;
//bool isInterrupt;
int totalexetime;

typedef struct task_info {
  char* taskName;
  int startTime;
  int endTime;
  int exitIndex;
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

inline void send_msg_1(int targetcore, int n0) __attribute__((always_inline));
inline void send_msg_2(int targetcore, int n0, int n1) __attribute__((always_inline));
inline void send_msg_3(int targetcore, int n0, int n1, int n2) __attribute__((always_inline));
inline void send_msg_4(int targetcore, int n0, int n1, int n2, int n3) __attribute__((always_inline));
inline void send_msg_5(int targetcore, int n0, int n1, int n2, int n3, int n4) __attribute__((always_inline));
inline void send_msg_6(int targetcore, int n0, int n1, int n2, int n3, int n4, int n5) __attribute__((always_inline));
inline void cache_msg_2(int targetcore, int n0, int n1) __attribute__((always_inline));
inline void cache_msg_3(int targetcore, int n0, int n1, int n2) __attribute__((always_inline));
inline void cache_msg_4(int targetcore, int n0, int n1, int n2, int n3) __attribute__((always_inline));
inline void cache_msg_6(int targetcore, int n0, int n1, int n2, int n3, int n4, int n5) __attribute__((always_inline));
inline void transferObject(struct transObjInfo * transObj);
inline int receiveMsg(void) __attribute__((always_inline)) __attribute__((always_inline));

#ifdef PROFILE
inline void profileTaskStart(char * taskname) __attribute__((always_inline));
inline void profileTaskEnd(void) __attribute__((always_inline));
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
//  BAMBOO_CACHE_FLUSH_RANGE(x, y): flush cache lines started at x with length y   //
//  BAMBOO_CACHE_FLUSH_ALL(): flush the whole cache of a core if necessary         //
//  BAMBOO_EXIT(x): exit routine                                                   //
//  BAMBOO_MSG_AVAIL(): checking if there are msgs coming in                       //
//  BAMBOO_GET_EXE_TIME(): rountine to get current clock cycle number              //
/////////////////////////////////////////////////////////////////////////////////////

#endif  // #ifdef MULTICORE
#endif  // #ifdef TASK
#endif  // #ifndef MULTICORE_RUNTIME
