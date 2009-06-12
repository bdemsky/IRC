#ifndef __WORK_SCHEDULE__
#define __WORK_SCHEDULE__


// initialize the work schedule system, after 
// which some preliminary work units can be
// scheduled.  Note the supplied work func 
// should operate on a work unit of the type
// submitted in the function below
void workScheduleInit( int numProcessors,
                       void(*workFunc)(void*) );

// your main program, before beginning this
// system, or the execution of worker threads
// themselves use this submit function to
// distribute work units among the worker pool
// threads.  The workers will dynamically
// steal from one another to load balance
void workScheduleSubmit( void* workUnit );

// once you call this begin function your main
// thread becomes a work thread, so programs
// should not expect to return from this
void workScheduleBegin();


#endif /* __WORK_SCHEDULE__ */
