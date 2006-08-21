#include "runtime.h"
#include "structdefs.h"
#include <string.h>

extern int classsize[];
#include "mem.h"

#ifdef TASK
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
    ((struct QueueItem **)ptr)[2]=flagptr;
  }
}

void executetasks() {
  void * pointerarray[MAXTASKPARAMS];
  while(1) {
  newtask:
    {
    struct taskdescriptor * task=(struct taskdescriptor *) SimpleHashfirstkey(activetasks);
    int i;
    if (task==NULL)
      break;
    for(i=0;i<task->numParameters;i++) {
      struct parameterwrapper * parameter=(struct parameterwrapper *) task->descriptorarray[i]->queue;
      struct Queue * queue=parameter->queue;
      if (isEmpty(queue)) {
	SimpleHashremove(activetasks, (int)task, (int)task);
	goto newtask;
      }
      pointerarray[i]=getTail(queue)->objectptr;
    }
    ((void (*) (void **)) task->taskptr)(pointerarray);
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
      while(*ptr!=NULL)
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
  printf("Array out of bounds\n");
  exit(-1);
}

void failednullptr() {
  printf("Dereferenced a null pointer\n");
  exit(-1);
}
