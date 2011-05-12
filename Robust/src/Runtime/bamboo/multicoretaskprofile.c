#ifdef TASK
#ifdef PROFILE
#include "multicoretaskprofile.h"

int profilestatus[NUMCORESACTIVE]; // records status of each core
                                   // 1: running tasks
                                   // 0: stall
///////////////////////////////////////////////////////////////////////////////
// This global variable records the profiling information of tasks including 
// when the task starts and ends, the exit path of each execution of the task,
// and how many new objs are created as well as the type of the new objs. There 
// also have an index indicates how many tasks have been recorded and if the 
// buffer has overflowed. These profile information is supposed to be dumped 
// out before the execution of the program is terminated.
//
// Maintaining protocols: 
//     INIT_TASKPROFILE_DATA() initializes this variable and should be invoked 
//     before executing any tasks.
//
//     PROFILE_TASK_START() creates a new item to record a task's execution. It 
//     should be invoked right before starting a new task as it also records 
//     the start time of a task's execution.
//
//     PROFILE_TASK_END() records the stop time of a task's execution and close 
//     a task record item. It should be invoked immediately when a task 
//     finishes execution.
// 
//   The following functions record corresponding task information during the 
//   execution of a task and should be wrapped with a 
//   PROFILE_TASK_START()/PROFILE_TASK_END() pair.
//     setTaskExitIndex() records the exit path of the execution.
//     addNewObjInfo() records the information of new objs created by the task.
//
//   This variable can only be updated with the functions/MACROs listed above! 
///////////////////////////////////////////////////////////////////////////////
TaskProfile_t taskProfileInfo;

#ifdef PROFILE_INTERRUPT
///////////////////////////////////////////////////////////////////////////////
// This global variable records the profiling information of the interrupts 
// happended during the execution of a program. It records when an interrupt 
// happended and when it returns to normal program execution.
//
// Maintaining protocols: 
//     INIT_TASKPROFILE_DATA() initializes this variable and should be invoked 
//     before executing any tasks.
//
//     PROFILE_INTERRUPT_START() creates a new item to record the information 
//     of an interrupt. It should be invoked at the very beginning of an
//     interrupt handler.
//
//     PROFILE_INTERRUPT_END() records when an interrupt returns from its 
//     handler.  It should be invoked right before an interrupt handler returns.
// 
//   This variable can only be updated with the functions/MACROs listed above! 
///////////////////////////////////////////////////////////////////////////////
InterruptProfile_t interruptProfileInfo;
#endif

void inittaskprofiledata() {
  stall = false;
  totalexetime = -1;
  taskProfileInfo.taskInfoIndex = 0;
  taskProfileInfo.taskInfoOverflow = false;
#ifdef PROFILE_INTERRUPT
  interruptProfileInfo.interruptInfoIndex = 0;
  interruptProfileInfo.interruptInfoOverflow = false;
#endif 
}

// output the profiling data
void outputProfileData() {
#ifdef USEIO
  int i;
  unsigned long long totaltasktime = 0;
  unsigned long long preprocessingtime = 0;
  unsigned long long objqueuecheckingtime = 0;
  unsigned long long postprocessingtime = 0;
  unsigned long long other = 0;
  unsigned long long averagetasktime = 0;
  int tasknum = 0;

  printf("Task Name, Start Time, End Time, Duration, Exit Index(, NewObj Name, Num)+\n");
  // output task related info
  for(i = 0; i < taskProfileInfo.taskInfoIndex; i++) {
    TaskInfo* tmpTInfo = taskProfileInfo.taskInfoArray[i];
    unsigned long long duration = tmpTInfo->endTime - tmpTInfo->startTime;
    printf("%s, %lld, %lld, %lld, %lld",tmpTInfo->taskName,tmpTInfo->startTime,tmpTInfo->endTime,duration,tmpTInfo->exitIndex);
    // summarize new obj info
    if(tmpTInfo->newObjs != NULL) {
      struct RuntimeHash * nobjtbl = allocateRuntimeHash(5);
      struct RuntimeIterator * iter = NULL;
      while(0 == isEmpty(tmpTInfo->newObjs)) {
        char * objtype = (char *)(getItem(tmpTInfo->newObjs));
        if(RuntimeHashcontainskey(nobjtbl, (int)(objtype))) {
          int num = 0;
          RuntimeHashget(nobjtbl, (int)objtype, &num);
          RuntimeHashremovekey(nobjtbl, (int)objtype);
          num++;
          RuntimeHashadd(nobjtbl, (int)objtype, num);
        } else {
          RuntimeHashadd(nobjtbl, (int)objtype, 1);
        }
      }

      // output all new obj info
      iter = RuntimeHashcreateiterator(nobjtbl);
      while(RunhasNext(iter)) {
        char * objtype = (char *)Runkey(iter);
        int num = Runnext(iter);
        printf(", %s, %d", objtype, num);
      }
    }
    printf("\n");
    if(strcmp(tmpTInfo->taskName, "tpd checking") == 0) {
      preprocessingtime += duration;
    } else if(strcmp(tmpTInfo->taskName, "post task execution") == 0) {
      postprocessingtime += duration;
    } else if(strcmp(tmpTInfo->taskName, "objqueue checking") == 0) {
      objqueuecheckingtime += duration;
    } else {
      totaltasktime += duration;
      averagetasktime += duration;
      tasknum++;
    }
  }

  if(taskProfileInfo.taskInfoOverflow) {
    printf("Caution: task info overflow!\n");
  }

  other = totalexetime-totaltasktime-preprocessingtime-postprocessingtime;
  averagetasktime /= tasknum;

  printf("\nTotal time: %lld\n", totalexetime);
  printf("Total task execution time: %lld (%d%%)\n",totaltasktime,(int)(((double)totaltasktime/(double)totalexetime)*100));
  printf("Total objqueue checking time: %lld (%d%%)\n",objqueuecheckingtime,(int)(((double)objqueuecheckingtime/(double)totalexetime)*100));
  printf("Total pre-processing time: %lld (%d%%)\n", preprocessingtime,(int)(((double)preprocessingtime/(double)totalexetime)*100));
  printf("Total post-processing time: %lld (%d%%)\n", postprocessingtime,(int)(((double)postprocessingtime/(double)totalexetime)*100));
  printf("Other time: %lld (%d%%)\n", other,(int)(((double)other/(double)totalexetime)*100));

  printf("\nAverage task execution time: %lld\n", averagetasktime);

#else
  int i = 0;
  int j = 0;

  BAMBOO_PRINT(0xdddd);
  // output task related info
  for(i= 0; i < taskProfileInfo.taskInfoIndex; i++) {
    TaskInfo* tmpTInfo = taskInfoArray[i];
    char* tmpName = tmpTInfo->taskName;
    int nameLen = strlen(tmpName);
    BAMBOO_PRINT(0xddda);
    for(j = 0; j < nameLen; j++) {
      BAMBOO_PRINT_REG(tmpName[j]);
    }
    BAMBOO_PRINT(0xdddb);
    BAMBOO_PRINT_REG(tmpTInfo->startTime);
    BAMBOO_PRINT_REG(tmpTInfo->endTime);
    BAMBOO_PRINT_REG(tmpTInfo->exitIndex);
    if(tmpTInfo->newObjs != NULL) {
      struct RuntimeHash * nobjtbl = allocateRuntimeHash(5);
      struct RuntimeIterator * iter = NULL;
      while(0 == isEmpty(tmpTInfo->newObjs)) {
        char * objtype = (char *)(getItem(tmpTInfo->newObjs));
        if(RuntimeHashcontainskey(nobjtbl, (int)(objtype))) {
          int num = 0;
          RuntimeHashget(nobjtbl, (int)objtype, &num);
          RuntimeHashremovekey(nobjtbl, (int)objtype);
          num++;
          RuntimeHashadd(nobjtbl, (int)objtype, num);
        } else {
          RuntimeHashadd(nobjtbl, (int)objtype, 1);
        }
      }

      // ouput all new obj info
      iter = RuntimeHashcreateiterator(nobjtbl);
      while(RunhasNext(iter)) {
        char * objtype = (char *)Runkey(iter);
        int num = Runnext(iter);
        int nameLen = strlen(objtype);
        BAMBOO_PRINT(0xddda);
        for(j = 0; j < nameLen; j++) {
          BAMBOO_PRINT_REG(objtype[j]);
        }
        BAMBOO_PRINT(0xdddb);
        BAMBOO_PRINT_REG(num);
      }
    }
    BAMBOO_PRINT(0xdddc);
  }

  if(taskProfileInfo.taskInfoOverflow) {
    BAMBOO_PRINT(0xefee);
  }

#ifdef PROFILE_INTERRUPT
  // output interrupt related info
  for(i = 0; i < interruptProfileInfo.interruptInfoIndex; i++) {
    InterruptInfo* tmpIInfo = interruptProfileInfo.interruptInfoArray[i];
    BAMBOO_PRINT(0xddde);
    BAMBOO_PRINT_REG(tmpIInfo->startTime);
    BAMBOO_PRINT_REG(tmpIInfo->endTime);
    BAMBOO_PRINT(0xdddf);
  }

  if(interruptProfileInfo.interruptInfoOverflow) {
    BAMBOO_PRINT(0xefef);
  }
#endif 

  BAMBOO_PRINT(0xeeee);
#endif
}

#endif // PROFILE 
#endif // TASK
