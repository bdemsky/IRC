#ifndef BAMBOO_MULTICORE_RUNTIME_H
#define BAMBOO_MULTICORE_RUNTIME_H
#ifdef MULTICORE
#include "structdefs.h"
#include "multicore.h"
#include "multicoremsg.h"
#include "multicoremem.h"
#include "multicoretask.h"
#include "multicoremgc.h"

//Define the following line if the base object type has pointers
//#define OBJECTHASPOINTERS


#ifdef MULTICORE_GC
#define GCCHECK(p) \
  if(gcflag) gc(p)
#else
#define GCCHECK(p)
#endif // MULTICORE_GC

////////////////////////////////////////////////////////////////
// global variables                                          //
///////////////////////////////////////////////////////////////
// record the starting time
unsigned long long bamboo_start_time;
bool stall;
int totalexetime;
#ifndef INTERRUPT
bool reside;
#endif

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

/////////////////////////////////////////////////////////////

////////////////////////////////////////////////////////////
// these are functions should be implemented in           //
// multicore runtime for any multicore processors         //
////////////////////////////////////////////////////////////
INLINE void initialization(void);
INLINE void initCommunication(void);
INLINE void fakeExecution(void);
INLINE void terminate(void);
INLINE void initlock(struct ___Object___ * v);
#ifdef BAMBOO_MEMPROF
INLINE void terminatememprof(void);
#endif // BAMBOO_MEMPROF

///////////////////////////////////////////////////////////

/////////////////////////////////////////////////////////////////////////////
// For each version of BAMBOO runtime, there should be a header file named //
// runtim_arch.h defining following MARCOS:                                //
// BAMBOO_NUM_OF_CORE: the # of current residing core                      //
// BAMBOO_GET_NUM_OF_CORE(): compute the # of current residing core        //
// BAMBOO_COORDS(c, x, y): convert the cpu # to coords (*x, *y)            //
// BAMBOO_COORDS_X(c): convert the cpu # to coords x                       //
// BAMBOO_COORDS_Y(c): convert the cpu # to coordsy                        //
// BAMBOO_DEBUGPRINT(x): print out integer x                               //
// BAMBOO_DEBUGPRINT_REG(x): print out value of variable x                 //
// BAMBOO_EXIT_APP(x): exit the whole application                          //
// BAMBOO_EXIT(x): error exit routine with file name and line #            //
// BAMBOO_DIE(x): error exit routine with error msg                        //
// BAMBOO_ASSERT(x) : check if condition x is met                          //
// BAMBOO_ASSERTMSG(x.y): check if condition x is met or not, if not, print//
//                        out msg y                                        //
// BAMBOO_GET_EXE_TIME(): rountine to get current clock cycle number       //
// BAMBOO_MSG_AVAIL(): checking if there are msgs coming in                //
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
// BAMBOO_WAITING_FOR_LOCK_I(): routine executed while waiting for lock    //
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
#endif  // BAMBOO_MULTICORE_RUNTIME_H
