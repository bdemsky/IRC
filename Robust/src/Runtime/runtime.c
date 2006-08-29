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

#ifdef TASK
#include "checkpoint.h"
#include "Queue.h"
#include "SimpleHash.h"
#include "GenericHashtable.h"

struct Queue * activetasks;
struct parameterwrapper * objectqueues[NUMCLASSES];
struct genhashtable * failedtasks;

int main(int argc, char **argv) {
  int i;
  /* Allocate startup object */
  struct ___StartupObject___ *startupobject=(struct ___StartupObject___*) allocate_new(STARTUPTYPE);
  struct ArrayObject * stringarray=allocate_newarray(STRINGARRAYTYPE, argc); 
  failedtasks=genallocatehashtable((unsigned int (*)(void *)) &hashCodetpd, 
				   (int (*)(void *,void *)) &comparetpd);
  
  activetasks=createQueue();

  /* Set flags */
  processtasks();
  flagorand(startupobject,1,0xFFFFFFFF);

  /* Build array of strings */

  startupobject->___parameters___=stringarray;

  for(i=0;i<argc;i++) {
    int length=strlen(argv[i]);
    struct ___String___ *newstring=NewString(argv[i],length);
    ((void **)(((char *)& stringarray->___length___)+sizeof(int)))[i]=newstring;
  }
  executetasks();
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
  struct SimpleHash *flagptr=(struct SimpleHash *)(((int*)ptr)[2]);
  flag|=ormask;
  flag&=andmask;
  ((int*)ptr)[1]=flag;
  /*Remove from all queues */
  while(flagptr!=NULL) {
    struct SimpleHash *next;
    SimpleHashget(flagptr, (int) ptr, (int *) &next);
    SimpleHashremove(flagptr, (int)ptr, (int) next);
    flagptr=next;
  }
  
  {
    struct QueueItem *tmpptr;
    struct parameterwrapper * parameter=objectqueues[((int *)ptr)[0]];
    int i;
    struct SimpleHash * prevptr=NULL;
    while(parameter!=NULL) {
      for(i=0;i<parameter->numberofterms;i++) {
	int andmask=parameter->intarray[i*2];
	int checkmask=parameter->intarray[i*2+1];
	if ((flag&andmask)==checkmask) {
	  SimpleHashadd(parameter->objectset, (int) ptr, (int) prevptr);
	  prevptr=parameter->objectset;
	  {
	    struct SimpleIterator iteratorarray[MAXTASKPARAMS];
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
		SimpleHashiterator(pw->objectset, &iteratorarray[j]);
		if (hasNext(&iteratorarray[j]))
		  taskpointerarray[j]=(void *) next(&iteratorarray[j]);
		else
		  break; /* No tasks to dispatch */
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
	      
	      /* This loop iterates to the next paramter combination */
	      for(j=0;j<numparams;j++) {
		if (j==newindex) {
		  if ((j+1)==numparams)
		    done=0;
		  continue;
		}
		if (hasNext(&iteratorarray[j])) {
		  taskpointerarray[j]=(void *) next(&iteratorarray[j]);
		  break;
		} else if ((j+1)!=numparams) {
		  SimpleHashiterator(task->descriptorarray[j]->queue, &iteratorarray[j]);
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
    ((struct SimpleHash **)ptr)[2]=prevptr;
  }
}

/* Handler for signals */
void myhandler(int sig, struct __siginfo *info, void *uap) {
  printf("sig=%d\n",sig);
  printf("signal\n");
  longjmp(error_handler,1);
}

void executetasks() {
  void * taskpointerarray[MAXTASKPARAMS];

  /* Set up signal handlers */
  struct sigaction sig;
  sig.sa_sigaction=&myhandler;
  sig.sa_flags=SA_SIGINFO;
  sig.sa_mask=0;

  /* Catch bus errors, segmentation faults, and floating point exceptions*/
  sigaction(SIGBUS,&sig,0);
  sigaction(SIGSEGV,&sig,0);
  sigaction(SIGFPE,&sig,0);

  /* Map first block of memory to protected, anonymous page */
  mmap(0, 0x1000, 0, MAP_SHARED|MAP_FIXED|MAP_ANON, -1, 0);

  newtask:
  while(!isEmpty(activetasks)) {
    struct QueueItem * qi=(struct QueueItem *) getTail(activetasks);
    struct taskparamdescriptor *tpd=(struct taskparamdescriptor *) qi->objectptr;
    int i;
    removeItem(activetasks, qi);
    
    for(i=0;i<tpd->task->numParameters;i++) {
      void * parameter=tpd->parameterArray[i];
      struct parameterdescriptor * pd=tpd->task->descriptorarray[i];
      struct parameterwrapper *pw=(struct parameterwrapper *) pd->queue;
      if (!SimpleHashcontainskey(pw->objectset, (int) parameter))
	goto newtask;
      taskpointerarray[i]=parameter;
    }
    {
      struct SimpleHash * forward=allocateSimpleHash(100);
      struct SimpleHash * reverse=allocateSimpleHash(100);
      void ** checkpoint=makecheckpoint(tpd->task->numParameters, taskpointerarray, forward, reverse);
      if (setjmp(error_handler)) {
	/* Recover */
	int h;
	printf("Recovering\n");
	genputtable(failedtasks,tpd,tpd);
	restorecheckpoint(tpd->task->numParameters, taskpointerarray, checkpoint, forward, reverse);
      } else {
	/* Actually call task */
	((void (*) (void **)) tpd->task->taskptr)(taskpointerarray);
      }
    }
  }
}

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
      parameter->objectset=allocateSimpleHash(10);
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

int ___Object______hashcode____(struct ___Object___ * ___this___) {
  return (int) ___this___;
}

void ___System______printString____L___String___(struct ___String___ * s) {
    struct ArrayObject * chararray=s->___string___;
    int i;
    for(i=0;i<chararray->___length___;i++) {
	short s= ((short *)(((char *)& chararray->___length___)+sizeof(int)))[i];
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

struct ___String___ * NewString(char *str,int length) {
  struct ArrayObject * chararray=allocate_newarray(CHARARRAYTYPE, length);
  struct ___String___ * strobj=allocate_new(STRINGTYPE);
  int i;
  strobj->___string___=chararray;
  for(i=0;i<length;i++) {
    ((short *)(((char *)& chararray->___length___)+sizeof(int)))[i]=(short)str[i];  }
  return strobj;
}

void failedboundschk() {
#ifndef TASK
  printf("Array out of bounds\n");
  exit(-1);
#else
  longjmp(error_handler,2);
#endif
}

void failednullptr() {
#ifndef TASK
  printf("Dereferenced a null pointer\n");
  exit(-1);
#else
  longjmp(error_handler,3);
#endif
}
