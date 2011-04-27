#ifndef __WORK_SCHEDULE__
#define __WORK_SCHEDULE__


// initialize the work schedule system, after
// which some preliminary work units can be
// scheduled.  Note the supplied work func
// should operate on a work unit of the type
// submitted in the function below
void workScheduleInit(int numProcessors,
                      void (*workFunc)(void*) );

// as the scheduler evolves, looks like this is
// a better way to shut down the system
void workScheduleExit();


// your main program, before beginning this
// system, or the execution of worker threads
// themselves use this submit function to
// distribute work units among the worker pool
// threads.  The workers will dynamically
// steal from one another to load balance
void workScheduleSubmit(void* workUnit);

// once you call this begin function your main
// thread becomes a work thread, so programs
// should not expect to return from this
void workScheduleBegin();



// this is a pattern where whatever the
// driving runtime component is for your compiler
// mode should provide the following synchronization
// variables to share with the garbage collector
// (so thread.c has them and uses them, too)
// and the key is you must coordinate with the garbage
// collector when the runtime wants to block threads
extern int threadcount;
extern pthread_mutex_t gclock;
extern pthread_mutex_t gclistlock;
extern pthread_cond_t gccond;


#endif /* __WORK_SCHEDULE__ */
