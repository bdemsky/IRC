#ifndef RUNTIME_ARCH
#define RUNTIME_ARCH

#ifdef PROFILE
#ifdef RAWUSEIO
#include "stdio.h"
#include "string.h"
#endif
#endif
#include <raw.h>
#include <raw_compiler_defs.h>

#define BAMBOO_CACHE_LINE_SIZE (kCacheLineSize)
#define BAMBOO_CACHE_LINE_MASK (kCacheLineMask)

#define BAMBOO_TOTALCORE (raw_get_num_tiles())  // the total # of cores available in the processor
#define BAMBOO_NUM_OF_CORE corenum   // the # of current residing core
#define BAMBOO_GET_NUM_OF_CORE() (raw_get_abs_pos_x() + raw_get_array_size_x() * raw_get_abs_pos_y())  // compute the # of current residing core
#define BAMBOO_DEBUGPRINT(x) (raw_test_pass((x)))
#define BAMBOO_DEBUGPRINT_REG(x) (raw_test_pass_reg((x)))

#define BAMBOO_SHARE_MEM_CALLOC(x, y) (calloc((x), (y)))  // allocate an array of x elements each of whose size in bytes is y on shared memory 

#ifdef INTERRUPT
// locks for global data structures related to obj queue
#define BAMBOO_START_CRITICAL_SECTION_OBJ_QUEUE() raw_user_interrupts_off()
#define BAMBOO_CLOSE_CRITICAL_SECTION_OBJ_QUEUE() raw_user_interrupts_on()
// locks for global data structures related to status data
#define BAMBOO_START_CRITICAL_SECTION_STATUS() raw_user_interrupts_off()
#define BAMBOO_CLOSE_CRITICAL_SECTION_STATUS() raw_user_interrupts_on()
// locks for global data structures related to msg data
#define BAMBOO_START_CRITICAL_SECTION_MSG() raw_user_interrupts_off()
#define BAMBOO_CLOSE_CRITICAL_SECTION_MSG() raw_user_interrupts_on()
// locks for global data structures related to lock table
#define BAMBOO_START_CRITICAL_SECTION_LOCK() raw_user_interrupts_off()
#define BAMBOO_CLOSE_CRITICAL_SECTION_LOCK() raw_user_interrupts_on()
// locks for allocating memory
#define BAMBOO_START_CRITICAL_SECTION_MEM() raw_user_interrupts_off()
#define BAMBOO_CLOSE_CRITICAL_SECTION_MEM() raw_user_interrupts_on()
// locks for all global data structures
#define BAMBOO_START_CRITICAL_SECTION() raw_user_interrupts_off()
#define BAMBOO_CLOSE_CRITICAL_SECTION() raw_user_interrupts_on()
#else
// locks for global data structures related to obj queue
#define BAMBOO_START_CRITICAL_SECTION_OBJ_QUEUE()  
#define BAMBOO_CLOSE_CRITICAL_SECTION_OBJ_QUEUE()  
// locks for global data structures related to status data
#define BAMBOO_START_CRITICAL_SECTION_STATUS()  
#define BAMBOO_CLOSE_CRITICAL_SECTION_STATUS()  
// locks for global data structures related to msg data
#define BAMBOO_START_CRITICAL_SECTION_MSG()  
#define BAMBOO_CLOSE_CRITICAL_SECTION_MSG()  
// locks for global data structures related to lock table
#define BAMBOO_START_CRITICAL_SECTION_LOCK()  
#define BAMBOO_CLOSE_CRITICAL_SECTION_LOCK()  
// locks for allocating memory
#define BAMBOO_START_CRITICAL_SECTION_MEM()  
#define BAMBOO_CLOSE_CRITICAL_SECTION_MEM()  
// locks for all global data structures
#define BAMBOO_START_CRITICAL_SECTION()  
#define BAMBOO_CLOSE_CRITICAL_SECTION()  
#endif

#define BAMBOO_WAITING_FOR_LOCK() (receiveObject())
#define BAMBOO_CACHE_FLUSH_RANGE(x, y)  (raw_invalidate_cache_range((x), (y)))
#define BAMBOO_CACHE_FLUSH_ALL() (raw_flush_entire_cache())
#define BAMBOO_EXIT(x) (raw_test_done((x)))
#define BAMBOO_MSG_AVAIL() (gdn_input_avail())
#define BAMBOO_GET_EXE_TIME() (raw_get_cycle())

#endif // #ifndef RUNTIME_ARCH
