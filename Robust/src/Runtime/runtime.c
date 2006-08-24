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
#include "task.h"

struct SimpleHash * activetasks;
struct parameterwrapper * objectqueues[NUMCLASSES];

int main(int argc, char **argv) {
  int i;
  /* Allocate startup object */
  struct ___StartupObject___ *startupobject=(struct ___StartupObject___*) allocate_new(STARTUPTYPE);
  struct ArrayObject * stringarray=allocate_newarray(STRINGARRAYTYPE, argc); 

  activetasks=allocateSimpleHash(50);

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

void flagorand(void * ptr, int ormask, int andmask) {
  int flag=((int *)ptr)[1];
  struct QueueItem *flagptr=(struct QueueItem *)(((int*)ptr)[2]);
  flag|=ormask;
  flag&=andmask;
  ((int*)ptr)[1]=flag;
  /*Remove from all queues */
  while(flagptr!=NULL) {
    struct QueueItem * next=flagptr->nextqueue;
    removeItem(flagptr->queue, flagptr);
    flagptr=next;
  }
  
  {
    struct QueueItem *tmpptr;
    struct parameterwrapper * parameter=objectqueues[((int *)ptr)[0]];
    int i;
    flagptr=NULL;
    while(parameter!=NULL) {
      for(i=0;i<parameter->numberofterms;i++) {
	int andmask=parameter->intarray[i*2];
	int checkmask=parameter->intarray[i*2+1];
	if ((flag&andmask)==checkmask) {
	  struct QueueItem * qitem=addNewItem(parameter->queue, ptr);
	  if (flagptr==NULL) {
	    flagptr=qitem;
	    tmpptr=flagptr;
	  } else {
	    tmpptr->nextqueue=qitem;
	    tmpptr=qitem;
	  }
	  SimpleHashadd(activetasks, (int)parameter->task, (int)parameter->task);
	  break;
	}
      }
      parameter=parameter->next;
    }
    ((struct QueueItem **)ptr)[2]=flagptr;
  }
}

/* Handler for signals */
void myhandler(int sig, struct __siginfo *info, void *uap) {
  printf("sig=%d\n",sig);
  printf("signal\n");
  longjmp(error_handler,1);
}

void executetasks() {
  void * pointerarray[MAXTASKPARAMS];
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
  while(SimpleHashcountset(activetasks)!=0) {
    struct taskdescriptor * task=(struct taskdescriptor *) SimpleHashfirstkey(activetasks);
    int i;
    for(i=0;i<task->numParameters;i++) {
      struct parameterwrapper * parameter=(struct parameterwrapper *) task->descriptorarray[i]->queue;
      struct Queue * queue=parameter->queue;
      if (isEmpty(queue)) {
	SimpleHashremove(activetasks, (int)task, (int)task);
	goto newtask;
      }
      pointerarray[i]=getTail(queue)->objectptr;
    }
    {
      struct SimpleHash * forward=allocateSimpleHash(100);
      struct SimpleHash * reverse=allocateSimpleHash(100);
      void ** checkpoint=makecheckpoint(task->numParameters, pointerarray, forward, reverse);
      if (setjmp(error_handler)) {
	/* Recover */
	restorecheckpoint(task->numParameters, pointerarray, checkpoint, forward, reverse);
	/* TODO: REMOVE TASK FROM QUEUE */
      } else {
	/* Actually call task */
	((void (*) (void **)) task->taskptr)(pointerarray);
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
      parameter->queue=createQueue();
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
