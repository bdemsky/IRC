#ifndef BAMBOO_MULTICORE_MGC_H
#define BAMBOO_MULTICORE_MGC_H
#ifdef MGC
// shared memory pointer for global thread queue
// In MGC version, this block of memory is located at the very bottom of the 
// shared memory with the base address as BAMBOO_BASE_VA.
// The bottom of the shared memory = global thread queue + sbstart tbl 
//                                  + smemtbl + NUMCORES4GC bamboo_rmsp
// This queue is always reside at the bottom of the shared memory.  It is 
// considered as runtime structure, during gc, it is scanned for mark and flush 
// phase but never been compacted.
//
// This is a loop array and the first 4 int fields of the queue are:
//     mutex + thread counter + start pointer + end pointer
// data structures for threads
unsigned int * bamboo_thread_queue;
unsigned int bamboo_max_thread_num_mask;
unsigned int bamboo_current_thread;

//extern int corenum;
#endif // MGC
#endif // BAMBOO_MULTICORE_MGC_H
