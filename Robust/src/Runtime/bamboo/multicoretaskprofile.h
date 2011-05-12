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
} TaskInfo_t;

typedef struct task_profile {
  TaskInfo_t * taskInfoArray[TASKINFOLENGTH];
  int taskInfoIndex;
  bool taskInfoOverflow;
} TaskProfile_t;

extern TaskProfile_t taskProfileInfo;
#ifdef PROFILE_INTERRUPT
#define INTERRUPTINFOLENGTH 50
typedef struct interrupt_info {
  unsigned long long startTime;
  unsigned long long endTime;
} InterruptInfo_t;

typedef struct interrupt_profile {
  InterruptInfo_t * interruptInfoArray[INTERRUPTINFOLENGTH];
  int interruptInfoIndex;
  bool interruptInfoOverflow;
} InterruptProfile_t;

extern InterruptProfile_t interruptProfileInfo;
#endif

void outputProfileData();
void inittaskprofiledata();

INLINE static void setTaskExitIndex(int index) {
  taskProfileInfo.taskInfoArray[taskProfileInfo.taskInfoIndex]->exitIndex = index;
}

INLINE static void addNewObjInfo(void * nobj) {
  if(taskProfileInfo.taskInfoArray[taskProfileInfo.taskInfoIndex]->newObjs==NULL) {
    taskProfileInfo.taskInfoArray[taskProfileInfo.taskInfoIndex]->newObjs=createQueue();
  }
  addNewItem(taskProfileInfo.taskInfoArray[taskProfileInfo.taskInfoIndex]->newObjs, nobj);
}

INLINE static void profileTaskStart(char * taskname) {
  if(!taskProfileInfo.taskInfoOverflow) {
    TaskInfo* taskInfo=RUNMALLOC(sizeof(struct task_info));
    taskProfileInfo.taskInfoArray[taskProfileInfo.taskInfoIndex]=taskInfo;
    taskInfo->taskName=taskname;
    taskInfo->startTime=BAMBOO_GET_EXE_TIME();
    taskInfo->endTime=-1;
    taskInfo->exitIndex=-1;
    taskInfo->newObjs=NULL;
  }
}

INLINE staitc void profileTaskEnd() {
  if(!taskProfileInfo.taskInfoOverflow) {
    taskProfileInfo.taskInfoArray[taskProfileInfo.taskInfoIndex]->endTime=BAMBOO_GET_EXE_TIME();
    taskProfileInfo.taskInfoIndex++;
    if(taskProfileInfo.taskInfoIndex == TASKINFOLENGTH) {
      taskProfileInfo.taskInfoOverflow=true;
    }
  }
}

#ifdef PROFILE_INTERRUPT
INLINE static void profileInterruptStart_I(void) {
  if(!interruptProfileInfo.interruptInfoOverflow) {
    InterruptInfo* intInfo=RUNMALLOC_I(sizeof(struct interrupt_info));
    interruptProfileInfo.interruptInfoArray[interruptProfileInfo.interruptInfoIndex]=intInfo;
    intInfo->startTime=BAMBOO_GET_EXE_TIME();
    intInfo->endTime=-1;
  }
}

INLINE static void profileInterruptEnd_I(void) {
  if(!interruptProfileInfo.interruptInfoOverflow) {
    interruptProfileInfo.interruptInfoArray[interruptProfileInfo.interruptInfoIndex]->endTime=BAMBOO_GET_EXE_TIME();
    interruptProfileInfo.interruptInfoIndex++;
    if(interruptProfileInfo.interruptInfoIndex==INTERRUPTINFOLENGTH) {
      interruptProfileInfo.interruptInfoOverflow=true;
    }
  }
}
#endif // PROFILE_INTERRUPT

#define INIT_TASKPROFILE_DATA() inittaskprofiledata()
#define PROFILE_TASK_START(s) profileTaskStart(s)
#define PROFILE_TASK_END() profileTaskEnd()
#ifdef PROFILE_INTERRUPT
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
