#ifdef PROFILE

#include "multicoretaskprofile.h"

INLINE void inittaskprofiledata() {
  if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
    // startup core to initialize corestatus[]
    for(i = 0; i < NUMCORESACTIVE; ++i) {
      // initialize the profile data arrays
      profilestatus[i] = 1;
    } // for(i = 0; i < NUMCORESACTIVE; ++i)
  }

  stall = false;
  totalexetime = -1;
  taskInfoIndex = 0;
  taskInfoOverflow = false;
#ifdef PROFILE_INTERRUPT
  interruptInfoIndex = 0;
  interruptInfoOverflow = false;
#endif // PROFILE_INTERRUPT
}

inline void setTaskExitIndex(int index) {
  taskInfoArray[taskInfoIndex]->exitIndex = index;
}

inline void addNewObjInfo(void * nobj) {
  if(taskInfoArray[taskInfoIndex]->newObjs == NULL) {
    taskInfoArray[taskInfoIndex]->newObjs = createQueue();
  }
  addNewItem(taskInfoArray[taskInfoIndex]->newObjs, nobj);
}

inline void profileTaskStart(char * taskname) {
  if(!taskInfoOverflow) {
    TaskInfo* taskInfo = RUNMALLOC(sizeof(struct task_info));
    taskInfoArray[taskInfoIndex] = taskInfo;
    taskInfo->taskName = taskname;
    taskInfo->startTime = BAMBOO_GET_EXE_TIME();
    taskInfo->endTime = -1;
    taskInfo->exitIndex = -1;
    taskInfo->newObjs = NULL;
  }
}

inline void profileTaskEnd() {
  if(!taskInfoOverflow) {
    taskInfoArray[taskInfoIndex]->endTime = BAMBOO_GET_EXE_TIME();
    taskInfoIndex++;
    if(taskInfoIndex == TASKINFOLENGTH) {
      taskInfoOverflow = true;
    }
  }
}

#ifdef PROFILE_INTERRUPT
INLINE void profileInterruptStart_I(void) {
  if(!interruptInfoOverflow) {
    InterruptInfo* intInfo = RUNMALLOC_I(sizeof(struct interrupt_info));
    interruptInfoArray[interruptInfoIndex] = intInfo;
    intInfo->startTime = BAMBOO_GET_EXE_TIME();
    intInfo->endTime = -1;
  }
}

INLINE void profileInterruptEnd_I(void) {
  if(!interruptInfoOverflow) {
    interruptInfoArray[interruptInfoIndex]->endTime=BAMBOO_GET_EXE_TIME();
    interruptInfoIndex++;
    if(interruptInfoIndex == INTERRUPTINFOLENGTH) {
      interruptInfoOverflow = true;
    }
  }
}
#endif // PROFILE_INTERRUPT

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
  for(i = 0; i < taskInfoIndex; i++) {
    TaskInfo* tmpTInfo = taskInfoArray[i];
    unsigned long long duration = tmpTInfo->endTime - tmpTInfo->startTime;
    printf("%s, %lld, %lld, %lld, %lld", tmpTInfo->taskName, 
        tmpTInfo->startTime, tmpTInfo->endTime, duration, tmpTInfo->exitIndex);
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

  if(taskInfoOverflow) {
    printf("Caution: task info overflow!\n");
  }

  other = totalexetime-totaltasktime-preprocessingtime-postprocessingtime;
  averagetasktime /= tasknum;

  printf("\nTotal time: %lld\n", totalexetime);
  printf("Total task execution time: %lld (%d%%)\n", totaltasktime,
         (int)(((double)totaltasktime/(double)totalexetime)*100));
  printf("Total objqueue checking time: %lld (%d%%)\n",
         objqueuecheckingtime,
         (int)(((double)objqueuecheckingtime/(double)totalexetime)*100));
  printf("Total pre-processing time: %lld (%d%%)\n", preprocessingtime,
         (int)(((double)preprocessingtime/(double)totalexetime)*100));
  printf("Total post-processing time: %lld (%d%%)\n", postprocessingtime,
         (int)(((double)postprocessingtime/(double)totalexetime)*100));
  printf("Other time: %lld (%d%%)\n", other,
         (int)(((double)other/(double)totalexetime)*100));

  printf("\nAverage task execution time: %lld\n", averagetasktime);

#else
  int i = 0;
  int j = 0;

  BAMBOO_PRINT(0xdddd);
  // output task related info
  for(i= 0; i < taskInfoIndex; i++) {
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

  if(taskInfoOverflow) {
	BAMBOO_PRINT(0xefee);
  }

#ifdef PROFILE_INTERRUPT
  // output interrupt related info
  for(i = 0; i < interruptInfoIndex; i++) {
    InterruptInfo* tmpIInfo = interruptInfoArray[i];
    BAMBOO_PRINT(0xddde);
    BAMBOO_PRINT_REG(tmpIInfo->startTime);
    BAMBOO_PRINT_REG(tmpIInfo->endTime);
    BAMBOO_PRINT(0xdddf);
  }

  if(interruptInfoOverflow) {
    BAMBOO_PRINT(0xefef);
  }
#endif // PROFILE_INTERRUPT

  BAMBOO_PRINT(0xeeee);
#endif
}

#endif // PROFILE
