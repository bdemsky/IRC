#ifndef BAMBOO_MULTICORE_TASK_H
#define BAMBOO_MULTICORE_TASK_H
#ifdef TASK
#include "multicore.h"
// TASK specific data structures
// get rid of lock msgs for GC version
#ifndef MULTICORE_GC
// data structures for locking
struct RuntimeHash locktable;
static struct RuntimeHash* locktbl = &locktable;
struct RuntimeHash * lockRedirectTbl;
struct RuntimeHash * objRedirectLockTbl;
#endif // ifndef MULTICORE_GC
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
					
// for test TODO
int total_num_t6;

// lock related functions
bool getreadlock(void* ptr);
void releasereadlock(void* ptr);
bool getwritelock(void* ptr);
void releasewritelock(void* ptr);
bool getwritelock_I(void* ptr);
void releasewritelock_I(void * ptr);
#ifndef MULTICORE_GC
void releasewritelock_r(void * lock, void * redirectlock);
#endif // ifndef MULTICORE_GC
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

INLINE void inittaskdata();
INLINE void distaskdata();

#define INITTASKDATA() inittaskdata()
#define DISTASKDATA() distaskdata()
#else // TASK
#define INITTASKDATA()
#define DISTASKDATA()
#endif // TASK
#endif // BAMBOO_MULTICORE_TASK_H
