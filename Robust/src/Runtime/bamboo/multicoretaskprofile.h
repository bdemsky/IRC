#ifndef BAMBOO_MULTICORE_TASK_PROFILE_H
#define BAMBOO_MULTICORE_TASK_PROFILE_H
#include "multicore.h"

#ifdef TASK
// data structures for profile mode
#ifdef PROFILE
#define TASKINFOLENGTH 3000

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
volatile int profilestatus[NUMCORESACTIVE]; // records status of each core
                                            // 1: running tasks
                                            // 0: stall
#ifdef PROFILE_INTERRUPT
#define INTERRUPTINFOLENGTH 50
typedef struct interrupt_info {
  unsigned long long startTime;
  unsigned long long endTime;
} InterruptInfo;

InterruptInfo * interruptInfoArray[INTERRUPTINFOLENGTH];
int interruptInfoIndex;
bool interruptInfoOverflow;
#endif

INLINE void profileTaskStart(char * taskname);
INLINE void profileTaskEnd(void);
void outputProfileData();
INLINE void inittaskprofiledata();

#define INIT_TASKPROFILE_DATA() inittaskprofiledata()
#define PROFILE_TASK_START(s) profileTaskStart(s)
#define PROFILE_TASK_END() profileTaskEnd()
#ifdef PROFILE_INTERRUPT
INLINE void profileInterruptStart_I(void);
INLINE void profileInterruptEnd_I(void);

#define PROFILE_INTERRUPT_START() profileInterruptStart_I()
#define PROFILE_INTERRUPT_END() profileInterruptEnd_I()
#else
#define PROFILE_INTERRUPT_START() 
#define PROFILE_INTERRUPT_END() 
#endif
#else // PROFILE
#define INIT_TASKPROFILE_DATA() 
#define PROFILE_TASK_START(s)
#define PROFILE_TASK_END()
#define PROFILE_INTERRUPT_START() 
#define PROFILE_INTERRUPT_END() 
#endif // PROFILE
#else // TASK
#define INIT_TASKPROFILE_DATA() 
#define PROFILE_TASK_START(s)
#define PROFILE_TASK_END()
#define PROFILE_INTERRUPT_START() 
#define PROFILE_INTERRUPT_END()
#endif // TASK
#endif // BAMBOO_MULTICORE_TASK_PROFILE_H
