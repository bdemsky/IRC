#include "runtime.h"
#include "structdefs.h"
#include <string.h>
#include <signal.h>
#include "mem.h"
#include<fcntl.h>
#include<sys/types.h>
#include<sys/mman.h>
#include<errno.h>
#include<signal.h>
#include<stdio.h>
#include "option.h"

extern int classsize[];
jmp_buf error_handler;
int instructioncount;

char *options;
int injectfailures=0;
float failurechance=0;
int debugtask=0;
int injectinstructionfailures;
int failurecount;
float instfailurechance=0;
int numfailures;
int instaccum=0;



#ifdef TASK
#include "checkpoint.h"
#include "Queue.h"
#include "SimpleHash.h"
#include "GenericHashtable.h"
#include <sys/select.h>

#ifdef CONSCHECK
#include "instrument.h"
#endif

struct Queue * activetasks;
struct parameterwrapper * objectqueues[NUMCLASSES];
struct genhashtable * failedtasks;
struct RuntimeHash * forward;
struct RuntimeHash * reverse;


int main(int argc, char **argv) {
#ifdef BOEHM_GC
  GC_init(); // Initialize the garbage collector
#endif
#ifdef CONSCHECK
  initializemmap();
#endif
  processOptions();

  /* Create table for failed tasks */
  failedtasks=genallocatehashtable((unsigned int (*)(void *)) &hashCodetpd, 
				   (int (*)(void *,void *)) &comparetpd);
  /* Create queue of active tasks */
  activetasks=createQueue();
  
  /* Process task information */
  processtasks();

  /* Create startup object */
  createstartupobject(argc, argv);

  /* Start executing the tasks */
  executetasks();
}

void createstartupobject(int argc, char ** argv) {
  int i;
  
  /* Allocate startup object     */
#ifdef PRECISE_GC
  struct ___StartupObject___ *startupobject=(struct ___StartupObject___*) allocate_new(NULL, STARTUPTYPE);
  struct ArrayObject * stringarray=allocate_newarray(NULL, STRINGARRAYTYPE, argc-1); 
#else
  struct ___StartupObject___ *startupobject=(struct ___StartupObject___*) allocate_new(STARTUPTYPE);
  struct ArrayObject * stringarray=allocate_newarray(STRINGARRAYTYPE, argc-1); 
#endif
  /* Build array of strings */
  startupobject->___parameters___=stringarray;
  for(i=1;i<argc;i++) {
    int length=strlen(argv[i]);
#ifdef PRECISE_GC
    struct ___String___ *newstring=NewString(NULL, argv[i],length);
#else
    struct ___String___ *newstring=NewString(argv[i],length);
#endif
    ((void **)(((char *)& stringarray->___length___)+sizeof(int)))[i-1]=newstring;
  }
  
  /* Set initialized flag for startup object */
  flagorand(startupobject,1,0xFFFFFFFF);
}

int hashCodetpd(struct taskparamdescriptor *ftd) {
  int hash=(int)ftd->task;
  int i;
  for(i=0;i<ftd->numParameters;i++) {
    hash^=(int)ftd->parameterArray[i];
  }
  return hash;
}

int comparetpd(struct taskparamdescriptor *ftd1, struct taskparamdescriptor *ftd2) {
  int i;
  if (ftd1->task!=ftd2->task)
    return 0;
  for(i=0;i<ftd1->numParameters;i++)
    if (ftd1->parameterArray[i]!=ftd2->parameterArray[i])
      return 0;
  return 1;
}

/* This function updates the flag for object ptr.  It or's the flag
   with the or mask and and's it with the andmask. */

void flagorand(void * ptr, int ormask, int andmask) {
  int flag=((int *)ptr)[1];
  struct RuntimeHash *flagptr=(struct RuntimeHash *)(((int*)ptr)[2]);
  flag|=ormask;
  flag&=andmask;
  ((int*)ptr)[1]=flag;

  /*Remove object from all queues */
  while(flagptr!=NULL) {
    struct RuntimeHash *next;
    RuntimeHashget(flagptr, (int) ptr, (int *) &next);
    RuntimeHashremove(flagptr, (int)ptr, (int) next);
    flagptr=next;
  }
  
  {
    struct QueueItem *tmpptr;
    struct parameterwrapper * parameter=objectqueues[((int *)ptr)[0]];
    int i;
    struct RuntimeHash * prevptr=NULL;
    while(parameter!=NULL) {
      for(i=0;i<parameter->numberofterms;i++) {
	int andmask=parameter->intarray[i*2];
	int checkmask=parameter->intarray[i*2+1];
	if ((flag&andmask)==checkmask) {
	  RuntimeHashadd(parameter->objectset, (int) ptr, (int) prevptr);
	  prevptr=parameter->objectset;
	  {
	    struct RuntimeIterator iteratorarray[MAXTASKPARAMS];
	    void * taskpointerarray[MAXTASKPARAMS];
	    int j;
	    int numparams=parameter->task->numParameters;
	    int done=1;
	    struct taskdescriptor * task=parameter->task;
	    int newindex=-1;
	    for(j=0;j<numparams;j++) {
	      struct parameterwrapper *pw=(struct parameterwrapper *)task->descriptorarray[j]->queue;
	      if (parameter==pw) {
		taskpointerarray[j]=ptr;
		newindex=j;
	      } else {
		RuntimeHashiterator(pw->objectset, &iteratorarray[j]);
		if (RunhasNext(&iteratorarray[j])) {
		  taskpointerarray[j]=(void *) Runkey(&iteratorarray[j]);
		  Runnext(&iteratorarray[j]);
		} else {
		  done=0;
		  break; /* No tasks to dispatch */
		}
	      }
	    }
	    /* Queue task items... */

	    while(done) {
	      struct taskparamdescriptor *tpd=RUNMALLOC(sizeof(struct taskparamdescriptor));
	      tpd->task=task;
	      tpd->numParameters=numparams;
	      tpd->parameterArray=RUNMALLOC(sizeof(void *)*numparams);
	      for(j=0;j<numparams;j++)
		tpd->parameterArray[j]=taskpointerarray[j];
	      /* Queue task */
	      if (!gencontains(failedtasks, tpd))
		addNewItem(activetasks, tpd);
	      
	      /* This loop iterates to the next parameter combination */
	      for(j=0;j<numparams;j++) {
		if (j==newindex) {
		  if ((j+1)==numparams)
		    done=0;
		  continue;
		}
		if (RunhasNext(&iteratorarray[j])) {
		  taskpointerarray[j]=(void *) Runkey(&iteratorarray[j]);
		  Runnext(&iteratorarray[j]);
		  break;
		} else if ((j+1)!=numparams) {
		  RuntimeHashiterator(task->descriptorarray[j]->queue, &iteratorarray[j]);
		} else {
		  done=0;
		  break;
		}
	      }
	    }
	  }
	  break;
	}
      }
      parameter=parameter->next;
    }
    ((struct RuntimeHash **)ptr)[2]=prevptr;
  }
}

/* Handler for signals. The signals catch null pointer errors and
   arithmatic errors. */

void myhandler(int sig, siginfo_t *info, void *uap) {
#ifdef DEBUG
  printf("sig=%d\n",sig);
  printf("signal\n");
#endif
  longjmp(error_handler,1);
}

fd_set readfds;
int maxreadfd;
struct RuntimeHash *fdtoobject;

void addreadfd(int fd) {
  if (fd>=maxreadfd)
    maxreadfd=fd+1;
  FD_SET(fd, &readfds);
}

void removereadfd(int fd) {
  FD_CLR(fd, &readfds);
  if (maxreadfd==(fd+1)) {
    maxreadfd--;
    while(maxreadfd>0&&!FD_ISSET(maxreadfd-1, &readfds))
      maxreadfd--;
  }
}

#ifdef PRECISE_GC
#define OFFSET 2
#else
#define OFFSET 0
#endif

void executetasks() {
  void * taskpointerarray[MAXTASKPARAMS+OFFSET];

  /* Set up signal handlers */
  struct sigaction sig;
  sig.sa_sigaction=&myhandler;
  sig.sa_flags=SA_SIGINFO;
  sigemptyset(&sig.sa_mask);

  /* Catch bus errors, segmentation faults, and floating point exceptions*/
  sigaction(SIGBUS,&sig,0);
  sigaction(SIGSEGV,&sig,0);
  sigaction(SIGFPE,&sig,0);

  /* Zero fd set */
  FD_ZERO(&readfds);
  maxreadfd=0;
  fdtoobject=allocateRuntimeHash(100);

  /* Map first block of memory to protected, anonymous page */
  mmap(0, 0x1000, 0, MAP_SHARED|MAP_FIXED|MAP_ANON, -1, 0);

  newtask:
  while(!isEmpty(activetasks)||(maxreadfd>0)) {

    /* Check if any filedescriptors have IO pending */
    if (maxreadfd>0) {
      int i;
      struct timeval timeout={0,0};
      fd_set tmpreadfds;
      int numselect;
      tmpreadfds=readfds;
      numselect=select(maxreadfd, &tmpreadfds, NULL, NULL, &timeout);
      if (numselect>0) {
	/* Process ready fd's */
	int fd;
	for(fd=0;fd<maxreadfd;fd++) {
	  if (FD_ISSET(fd, &tmpreadfds)) {
	    /* Set ready flag on object */
	    void * objptr;
	    if (RuntimeHashget(fdtoobject, fd,(int *) &objptr)) {
	      flagorand(objptr,1,0xFFFFFFFF); /* Set the first flag to 1 */
	    }
	  }
	}
      }
    }

    /* See if there are any active tasks */
    if (!isEmpty(activetasks)) {
      int i;
      struct QueueItem * qi=(struct QueueItem *) getTail(activetasks);
      struct taskparamdescriptor *tpd=(struct taskparamdescriptor *) qi->objectptr;
      removeItem(activetasks, qi);

      /* Check if this task has failed */
      if (gencontains(failedtasks, tpd))
	goto newtask;
      
      /* Make sure that the parameters are still in the queues */
      for(i=0;i<tpd->task->numParameters;i++) {
	void * parameter=tpd->parameterArray[i];
	struct parameterdescriptor * pd=tpd->task->descriptorarray[i];
	struct parameterwrapper *pw=(struct parameterwrapper *) pd->queue;
	if (!RuntimeHashcontainskey(pw->objectset, (int) parameter))
	  goto newtask;
	taskpointerarray[i+OFFSET]=parameter;
      }
      {
	/* Checkpoint the state */
	forward=allocateRuntimeHash(100);
	reverse=allocateRuntimeHash(100);
	void ** checkpoint=makecheckpoint(tpd->task->numParameters, &taskpointerarray[OFFSET], forward, reverse);
	int x;
	if (x=setjmp(error_handler)) {
	  /* Recover */
	  int h;
#ifdef DEBUG
	  printf("Fatal Error=%d, Recovering!\n",x);
#endif
	  genputtable(failedtasks,tpd,tpd);
	  restorecheckpoint(tpd->task->numParameters, &taskpointerarray[OFFSET], checkpoint, forward, reverse);
	  freeRuntimeHash(forward);
	  freeRuntimeHash(reverse);
	  freemalloc();
	  forward=NULL;
	  reverse=NULL;
	} else {
	  if (injectfailures) {
	    if ((((double)random())/RAND_MAX)<failurechance) {
	      printf("\nINJECTING TASK FAILURE to %s\n", tpd->task->name);
	      longjmp(error_handler,10);
	    }
	  }
	  /* Actually call task */
#ifdef PRECISE_GC
	  ((int *)taskpointerarray)[0]=tpd->task->numParameters;
	  taskpointerarray[1]=NULL;
#endif

	  if (debugtask) {
	    printf("ENTER %s count=%d\n",tpd->task->name, (instaccum-instructioncount));
	    ((void (*) (void **)) tpd->task->taskptr)(taskpointerarray);
	    printf("EXIT %s count=%d\n",tpd->task->name, (instaccum-instructioncount));
	  } else
	    ((void (*) (void **)) tpd->task->taskptr)(taskpointerarray);
	  freeRuntimeHash(forward);
	  freeRuntimeHash(reverse);
	  freemalloc();
	  forward=NULL;
	  reverse=NULL;
	}
      }
    }
  }
}

/* This function processes the task information to create queues for
   each parameter type. */

void processtasks() {
  int i;
  for(i=0;i<numtasks;i++) {
    struct taskdescriptor * task=taskarray[i];
    int j;

    for(j=0;j<task->numParameters;j++) {
      struct parameterdescriptor *param=task->descriptorarray[j];
      struct parameterwrapper * parameter=RUNMALLOC(sizeof(struct parameterwrapper));
      struct parameterwrapper ** ptr=&objectqueues[param->type];

      param->queue=parameter;
      parameter->objectset=allocateRuntimeHash(10);
      parameter->numberofterms=param->numberterms;
      parameter->intarray=param->intarray;
      parameter->task=task;
      /* Link new queue in */
      while((*ptr)!=NULL)
	ptr=&((*ptr)->next);
      (*ptr)=parameter;
    }
  }
}

#endif

/* This function inject failures */

void injectinstructionfailure() {
#ifdef TASK
  if (injectinstructionfailures) {
    if (numfailures==0)
      return;
    instructioncount=failurecount;    
    instaccum+=failurecount;
    if ((((double)random())/RAND_MAX)<instfailurechance) {
      if (numfailures>0)
	numfailures--;
      printf("FAILURE!!!\n");
      longjmp(error_handler,11);
    }
  }
#else
#endif
}

int CALL01(___Object______hashCode____, struct ___Object___ * ___this___) {
  return (int) VAR(___this___);
}

int CALL01(___Object______getType____, struct ___Object___ * ___this___) {
  return ((int *)VAR(___this___))[0];
}

void CALL01(___System______printString____L___String___,struct ___String___ * ___s___) {
    struct ArrayObject * chararray=VAR(___s___)->___value___;
    int i;
    int offset=VAR(___s___)->___offset___;
    for(i=0;i<VAR(___s___)->___count___;i++) {
	short sc=((short *)(((char *)& chararray->___length___)+sizeof(int)))[i+offset];
	putchar(sc);
    }
}

/* Object allocation function */

#ifdef PRECISE_GC
void * allocate_new(void * ptr, int type) {
  void * v=mygcmalloc((struct garbagelist *) ptr, classsize[type]);
  *((int *)v)=type;
  return v;
}

/* Array allocation function */

struct ArrayObject * allocate_newarray(void * ptr, int type, int length) {
  struct ArrayObject * v=mygcmalloc((struct garbagelist *) ptr, sizeof(struct ArrayObject)+length*classsize[type]);
  v->type=type;
  v->___length___=length;
  return v;
}

#else
void * allocate_new(int type) {
  void * v=FREEMALLOC(classsize[type]);
  *((int *)v)=type;
  return v;
}

/* Array allocation function */

struct ArrayObject * allocate_newarray(int type, int length) {
  struct ArrayObject * v=FREEMALLOC(sizeof(struct ArrayObject)+length*classsize[type]);
  v->type=type;
  v->___length___=length;
  return v;
}
#endif


/* Converts C character arrays into Java strings */
#ifdef PRECISE_GC
struct ___String___ * NewString(void * ptr, const char *str,int length) {
#else
struct ___String___ * NewString(const char *str,int length) {
#endif
#ifdef PRECISE_GC
  struct ArrayObject * chararray=allocate_newarray((struct garbagelist *)ptr, CHARARRAYTYPE, length);
  struct ___String___ * strobj=allocate_new((struct garbagelist *) ptr, STRINGTYPE);
#else
  struct ArrayObject * chararray=allocate_newarray(CHARARRAYTYPE, length);
  struct ___String___ * strobj=allocate_new(STRINGTYPE);
#endif
  int i;
  strobj->___value___=chararray;
  strobj->___count___=length;
  strobj->___offset___=0;

  for(i=0;i<length;i++) {
    ((short *)(((char *)& chararray->___length___)+sizeof(int)))[i]=(short)str[i];  }
  return strobj;
}

/* Generated code calls this if we fail a bounds check */

void failedboundschk() {
#ifndef TASK
  printf("Array out of bounds\n");
  exit(-1);
#else
  longjmp(error_handler,2);
#endif
}

/* Abort task call */
void abort_task() {
#ifdef TASK
  longjmp(error_handler,4);
#else
  printf("Aborting\n");
  exit(-1);
#endif
}
