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

extern int classsize[];
jmp_buf error_handler;
int instructioncount;

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
/*struct genhashtable * failedobjects;*/
extern char ** environ;
char *options;
int injectfailures=0;
float failurechance=0;
int debugtask=0;
int injectinstructionfailures;
int failurecount;
float instfailurechance=0;

void processOptions() {
  int i;
  options=NULL;
  for(i=0;environ[i]!=0;i++) {
    if (strncmp(environ[i],"BRISTLECONE=",12)==0) {
      options=environ[i]+12;
      break;
    }
  }
  
  while(options!=NULL) {
    if (strncmp(options,"-injectfailures",sizeof("-injectfailures")-1)==0) {
      options=strchr(options,' ');
      if (options!=NULL) options++;
      if (options==NULL)
	break;
      sscanf(options, "%f", &failurechance);
      injectfailures=1;
      printf("Injecting errors with chance=%f\n",failurechance);
      options=strchr(options,' ');
      if (options!=NULL) options++;
    } else if (strncmp(options,"-injectinstructionfailures",sizeof("-injectinstructionfailures")-1)==0) {
      options=strchr(options,' ');
      if (options!=NULL) options++;
      if (options==NULL)
	break;
      sscanf(options, "%d", &failurecount);
      options=strchr(options,' ');
      if (options!=NULL) options++;
      if (options==NULL)
	break;
      sscanf(options, "%f", &instfailurechance);
      options=strchr(options,' ');
      if (options!=NULL) options++;
      instructioncount=failurecount;
      injectinstructionfailures=1;
      printf("Injecting errors with count=%d\n",failurecount);
      printf("Injecting errors with chance=%f\n",instfailurechance);

    } else if (strncmp(options, "-debugtask",sizeof("-debugtask")-1)==0) {
      options=strchr(options,' ');
      if (options!=NULL) options++;
      debugtask=1;
      printf("Debug task option on.\n");
    } else
      break;
  }
}

int main(int argc, char **argv) {
  GC_init(); // Initialize the garbage collector
#ifdef CONSCHECK
  initializemmap();
#endif
  processOptions();

  {
  int i;
  /* Allocate startup object */
  struct ___StartupObject___ *startupobject=(struct ___StartupObject___*) allocate_new(STARTUPTYPE);
  struct ArrayObject * stringarray=allocate_newarray(STRINGARRAYTYPE, argc-1); 
  failedtasks=genallocatehashtable((unsigned int (*)(void *)) &hashCodetpd, 
				   (int (*)(void *,void *)) &comparetpd);
  /*  failedobjects=genallocatehashtable(NULL,NULL);*/
  
  activetasks=createQueue();

  /* Set flags */
  processtasks();
  flagorand(startupobject,1,0xFFFFFFFF);

  /* Build array of strings */

  startupobject->___parameters___=stringarray;

  for(i=1;i<argc;i++) {
    int length=strlen(argv[i]);
    struct ___String___ *newstring=NewString(argv[i],length);
    ((void **)(((char *)& stringarray->___length___)+sizeof(int)))[i-1]=newstring;
  }
  executetasks();
  }
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

void flagorand(void * ptr, int ormask, int andmask) {
  int flag=((int *)ptr)[1];
  struct RuntimeHash *flagptr=(struct RuntimeHash *)(((int*)ptr)[2]);
  flag|=ormask;
  flag&=andmask;
  ((int*)ptr)[1]=flag;
  /*Remove from all queues */
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

void myhandler(int sig, struct __siginfo *info, void *uap) {
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

/*
void restoreObject(void * obj) {
  if (gencontains(failedobjects, obj)) {
    struct tpdlist *tpd=gengettable(failedobjects, obj);
    genfreekey(failedobjects, obj);
    while(tpd!=NULL) {
      int i;
      struct taskparamdescriptor *task=tpd->task;
      genfreekey(failedtasks, task);
      for(i=0;i<task->numParameters;i++) {
	void *objother=task->parameterArray[i];
	struct tpdlist *tpdother=gengettable(failedobjects, objother);
	struct tpdlist *tmpptr;
	genfreekey(failedobjects, objother);
	struct tpdlist **tpdptr=&tpdother;
	while((*tpdptr)->task!=task)
	  tpdptr=&((*tpdptr)->next);
	tmpptr=*tpdptr;
	(*tpdptr)=(*tpdptr)->next;
	RUNFREE(tmpptr);
	if (tpdother!=NULL)
	  genputtable(failedobjects, objother, tpdother);
      }
      RUNFREE(task);
      {
	struct tpdlist *old=tpd;
	tpd=tpd->next;
	RUNFREE(old);
      }
    }
  }
}
*/

void executetasks() {
  void * taskpointerarray[MAXTASKPARAMS];

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
      FD_COPY(&readfds, &tmpreadfds);
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
      
      /* Make sure that the parameters are still in the queues */
      for(i=0;i<tpd->task->numParameters;i++) {
	void * parameter=tpd->parameterArray[i];
	struct parameterdescriptor * pd=tpd->task->descriptorarray[i];
	struct parameterwrapper *pw=(struct parameterwrapper *) pd->queue;
	if (!RuntimeHashcontainskey(pw->objectset, (int) parameter))
	  goto newtask;
	taskpointerarray[i]=parameter;
      }
      {
	/* Checkpoint the state */
	struct RuntimeHash * forward=allocateRuntimeHash(100);
	struct RuntimeHash * reverse=allocateRuntimeHash(100);
	void ** checkpoint=makecheckpoint(tpd->task->numParameters, taskpointerarray, forward, reverse);
	int x;
	if (x=setjmp(error_handler)) {
	  /* Recover */
	  int h;
#ifdef DEBUG
	  printf("Fatal Error=%d, Recovering!\n",x);
#endif
	  genputtable(failedtasks,tpd,tpd);
       /* for(i=0;i<tpd->task->numParameters;i++) {
	    void *parameter=tpd->parameterArray[i];
	    {
	      // Create mapping from object -> failed tasks 
	      struct tpdlist * tpnew=RUNMALLOC(sizeof(struct tpdlist));
	      tpnew->task=tpd;
	      if (gencontains(failedobjects, parameter)) {
		struct tpdlist * tpdptr=gengettable(failedobjects, parameter);
		tpnew->next=tpdptr->next;
		tpdptr->next=tpnew;
	      } else {
		tpnew->next=NULL;
		genputtable(failedobjects, parameter, tpnew);
	      }
	    }
	  }  */
	  restorecheckpoint(tpd->task->numParameters, taskpointerarray, checkpoint, forward, reverse);
	} else {
	  if (injectfailures) {
	    if ((((double)random())/RAND_MAX)<failurechance) {
	      printf("\nINJECTING TASK FAILURE to %s\n", tpd->task->name);
	      longjmp(error_handler,10);
	    }
	  }
	  /* Actually call task */
	  if (debugtask) {
	    printf("ENTER %s\n",tpd->task->name);
	    ((void (*) (void **)) tpd->task->taskptr)(taskpointerarray);
	    printf("EXIT %s\n",tpd->task->name);
	  } else
	    ((void (*) (void **)) tpd->task->taskptr)(taskpointerarray);
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
    instructioncount=failurecount;    
    if ((((double)random())/RAND_MAX)<instfailurechance) {
      printf("FAILURE!!!\n");
      longjmp(error_handler,11);
    }
  }
#else
#endif
}

int ___Object______hashcode____(struct ___Object___ * ___this___) {
  return (int) ___this___;
}

void ___System______printString____L___String___(struct ___String___ * s) {
    struct ArrayObject * chararray=s->___value___;
    int i;
    int offset=s->___offset___;
    for(i=0;i<s->___count___;i++) {
	short s= ((short *)(((char *)& chararray->___length___)+sizeof(int)))[i+offset];
	putchar(s);
    }
}

void * allocate_new(int type) {
  void * v=FREEMALLOC(classsize[type]);
  *((int *)v)=type;
  return v;
}

struct ArrayObject * allocate_newarray(int type, int length) {
  struct ArrayObject * v=FREEMALLOC(sizeof(struct ArrayObject)+length*classsize[type]);
  v->type=type;
  v->___length___=length;
  return v;
}

struct ___String___ * NewString(const char *str,int length) {
  struct ArrayObject * chararray=allocate_newarray(CHARARRAYTYPE, length);
  struct ___String___ * strobj=allocate_new(STRINGTYPE);
  int i;
  strobj->___value___=chararray;
  strobj->___count___=length;
  strobj->___offset___=0;

  for(i=0;i<length;i++) {
    ((short *)(((char *)& chararray->___length___)+sizeof(int)))[i]=(short)str[i];  }
  return strobj;
}

/* Generated code calls this if we fail a bounds check *.
void failedboundschk() {
#ifndef TASK
  printf("Array out of bounds\n");
  exit(-1);
#else
  longjmp(error_handler,2);
#endif
}

void abort_task() {
#ifdef TASK
  longjmp(error_handler,4);
#else
  printf("Aborting\n");
  exit(-1);
#endif
}
