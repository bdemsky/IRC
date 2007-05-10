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
#ifdef DMALLOC
#include "dmalloc.h"
#endif


#ifdef TASK
#include "checkpoint.h"
#include "Queue.h"
#include "SimpleHash.h"
#include "GenericHashtable.h"
#include <sys/select.h>

#ifdef CONSCHECK
#include "instrument.h"
#endif

struct genhashtable * activetasks;
struct parameterwrapper * objectqueues[NUMCLASSES];
struct genhashtable * failedtasks;
struct taskparamdescriptor * currtpd;
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
  initializeexithandler();
  /* Create table for failed tasks */
  failedtasks=genallocatehashtable((unsigned int (*)(void *)) &hashCodetpd, 
				   (int (*)(void *,void *)) &comparetpd);
  /* Create queue of active tasks */
  activetasks=genallocatehashtable((unsigned int (*)(void *)) &hashCodetpd, 
				   (int (*)(void *,void *)) &comparetpd);


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


/* This function sets a tag. */
#ifdef PRECISE_GC
void tagset(void *ptr, struct ___Object___ * obj, struct ___TagDescriptor___ * tagd) {
#else
void tagset(struct ___Object___ * obj, struct ___TagDescriptor___ * tagd) {
#endif
  struct ___Object___ * tagptr=obj->___tags___;
  if (tagptr==NULL) {
    obj->___tags___=(struct ___Object___ *)tagd;
  } else {
    /* Have to check if it is already set */
    if (tagptr->type==TAGTYPE) {
      struct ___TagDescriptor___ * td=(struct ___TagDescriptor___ *) tagptr;
      if (td==tagd)
	return;
#ifdef PRECISE_GC
      int ptrarray[]={2, (int) ptr, (int) obj, (int)tagd};
      struct ArrayObject * ao=allocate_newarray(&ptrarray,TAGARRAYTYPE,TAGARRAYINTERVAL);
      obj=(struct ___Object___ *)ptrarray[2];
      tagd=(struct ___TagDescriptor___ *)ptrarray[3];
      td=(struct ___TagDescriptor___ *) obj->___tags___;
#else
      struct ArrayObject * ao=allocate_newarray(TAGARRAYTYPE,TAGARRAYINTERVAL);
#endif
      ARRAYSET(ao, struct ___TagDescriptor___ *, 0, td);
      ARRAYSET(ao, struct ___TagDescriptor___ *, 1, tagd);
      obj->___tags___=(struct ___Object___ *) ao;
      ao->___cachedCode___=2;
    } else {
      /* Array Case */
      int i;
      struct ArrayObject *ao=(struct ArrayObject *) tagptr;
      for(i=0;i<ao->___cachedCode___;i++) {
	struct ___TagDescriptor___ * td=ARRAYGET(ao, struct ___TagDescriptor___*, i);
	if (td==tagd)
	  return;
      }
      if (ao->___cachedCode___<ao->___length___) {
	ARRAYSET(ao, struct ___TagDescriptor___ *, ao->___cachedCode___, tagd);
	ao->___cachedCode___++;
      } else {
#ifdef PRECISE_GC
	int ptrarray[]={2,(int) ptr, (int) obj, (int) tagd};
	struct ArrayObject * aonew=allocate_newarray(&ptrarray,TAGARRAYTYPE,TAGARRAYINTERVAL+ao->___length___);
	obj=(struct ___Object___ *)ptrarray[2];
	tagd=(struct ___TagDescriptor___ *) ptrarray[3];
	ao=(struct ArrayObject *)obj->___tags___;
#else
	struct ArrayObject * aonew=allocate_newarray(TAGARRAYTYPE,TAGARRAYINTERVAL+ao->___length___);
#endif
	aonew->___cachedCode___=ao->___length___+1;
	for(i=0;i<ao->___length___;i++) {
	  ARRAYSET(aonew, struct ___TagDescriptor___*, i, ARRAYGET(ao, struct ___TagDescriptor___*, i));
	}
	ARRAYSET(aonew, struct ___TagDescriptor___ *, ao->___length___, tagd);
      }
    }
  }

  {
    struct ___Object___ * tagset=tagd->flagptr;
    
    if(tagset==NULL) {
      tagd->flagptr=obj;
    } else if (tagset->type!=OBJECTARRAYTYPE) {
#ifdef PRECISE_GC
      int ptrarray[]={2, (int) ptr, (int) obj, (int)tagd};
      struct ArrayObject * ao=allocate_newarray(&ptrarray,OBJECTARRAYTYPE,OBJECTARRAYINTERVAL);
      obj=(struct ___Object___ *)ptrarray[2];
      tagd=(struct ___TagDescriptor___ *)ptrarray[3];
#else
      struct ArrayObject * ao=allocate_newarray(OBJECTARRAYTYPE,OBJECTARRAYINTERVAL);
#endif
      ARRAYSET(ao, struct ___Object___ *, 0, tagd->flagptr);
      ARRAYSET(ao, struct ___Object___ *, 1, obj);
      ao->___cachedCode___=2;
      tagd->flagptr=(struct ___Object___ *)ao;
    } else {
      struct ArrayObject *ao=(struct ArrayObject *) tagset;
      if (ao->___cachedCode___<ao->___length___) {
	ARRAYSET(ao, struct ___Object___*, ao->___cachedCode___++, obj);
      } else {
	int i;
#ifdef PRECISE_GC
	int ptrarray[]={2, (int) ptr, (int) obj, (int)tagd};
	struct ArrayObject * aonew=allocate_newarray(&ptrarray,OBJECTARRAYTYPE,OBJECTARRAYINTERVAL+ao->___length___);
	obj=(struct ___Object___ *)ptrarray[2];
	tagd=(struct ___TagDescriptor___ *)ptrarray[3];
	ao=(struct ArrayObject *)tagd->flagptr;
#else
	struct ArrayObject * aonew=allocate_newarray(OBJECTARRAYTYPE,OBJECTARRAYINTERVAL);
#endif
	aonew->___cachedCode___=ao->___cachedCode___+1;
	for(i=0;i<ao->___length___;i++) {
	  ARRAYSET(aonew, struct ___Object___*, i, ARRAYGET(ao, struct ___Object___*, i));
	}
	ARRAYSET(aonew, struct ___Object___ *, ao->___cachedCode___, obj);
	tagd->flagptr=(struct ___Object___ *) ao;
      }
    }
  }
}

/* This function clears a tag. */
#ifdef PRECISE_GC
void tagclear(void *ptr, struct ___Object___ * obj, struct ___TagDescriptor___ * tagd) {
#else
void tagclear(struct ___Object___ * obj, struct ___TagDescriptor___ * tagd) {
#endif
  /* We'll assume that tag is alway there.
     Need to statically check for this of course. */
  struct ___Object___ * tagptr=obj->___tags___;

  if (tagptr->type==TAGTYPE) {
    if ((struct ___TagDescriptor___ *)tagptr==tagd)
      obj->___tags___=NULL;
    else
      printf("ERROR 1 in tagclear\n");
  } else {
    struct ArrayObject *ao=(struct ArrayObject *) tagptr;
    int i;
    for(i=0;i<ao->___cachedCode___;i++) {
      struct ___TagDescriptor___ * td=ARRAYGET(ao, struct ___TagDescriptor___ *, i);
      if (td==tagd) {
	ao->___cachedCode___--;
	if (i<ao->___cachedCode___)
	  ARRAYSET(ao, struct ___TagDescriptor___ *, i, ARRAYGET(ao, struct ___TagDescriptor___ *, ao->___cachedCode___));
	ARRAYSET(ao, struct ___TagDescriptor___ *, ao->___cachedCode___, NULL);
	if (ao->___cachedCode___==0)
	  obj->___tags___=NULL;
	goto PROCESSCLEAR;
      }
    }
    printf("ERROR 2 in tagclear\n");
  }
 PROCESSCLEAR:
  {
    struct ___Object___ *tagset=tagd->flagptr;
    if (tagset->type!=OBJECTARRAYTYPE) {
      if (tagset==obj)
	tagd->flagptr=NULL;
      else
	printf("ERROR 3 in tagclear\n");
    } else {
      struct ArrayObject *ao=(struct ArrayObject *) tagset;
      int i;
      for(i=0;i<ao->___cachedCode___;i++) {
	struct ___Object___ * tobj=ARRAYGET(ao, struct ___Object___ *, i);
	if (tobj==obj) {
	  ao->___cachedCode___--;
	  if (i<ao->___cachedCode___)
	    ARRAYSET(ao, struct ___Object___ *, i, ARRAYGET(ao, struct ___Object___ *, ao->___cachedCode___));
	  ARRAYSET(ao, struct ___Object___ *, ao->___cachedCode___, NULL);
	  if (ao->___cachedCode___==0)
	    tagd->flagptr=NULL;
	  goto ENDCLEAR;
	}
      }
      printf("ERROR 4 in tagclear\n");
    }
  }
 ENDCLEAR:
  return;
  
}
 
/* This function allocates a new tag. */
#ifdef PRECISE_GC
struct ___TagDescriptor___ * allocate_tag(void *ptr, int index) {
  struct ___TagDescriptor___ * v=(struct ___TagDescriptor___ *) mygcmalloc((struct garbagelist *) ptr, classsize[TAGTYPE]);
#else
struct ___TagDescriptor___ * allocate_tag(int index) {
  struct ___TagDescriptor___ * v=FREEMALLOC(classsize[TAGTYPE]);
#endif
  v->type=TAGTYPE;
  v->flag=index;
  return v;
} 



/* This function updates the flag for object ptr.  It or's the flag
   with the or mask and and's it with the andmask. */

void flagbody(struct ___Object___ *ptr, int flag);

void flagorand(void * ptr, int ormask, int andmask) {
  int oldflag=((int *)ptr)[1];
  int flag=ormask|oldflag;
  flag&=andmask;
  // Not sure why this was necessary
  //  if (flag==oldflag) /* Don't do anything */
  //  return;
  //else 
  flagbody(ptr, flag);
}

void intflagorand(void * ptr, int ormask, int andmask) {
  int oldflag=((int *)ptr)[1];
  int flag=ormask|oldflag;
  flag&=andmask;
  if (flag==oldflag) /* Don't do anything */
    return;
  else flagbody(ptr, flag);
}

void flagorandinit(void * ptr, int ormask, int andmask) {
  int oldflag=((int *)ptr)[1];
  int flag=ormask|oldflag;
  flag&=andmask;
  flagbody(ptr,flag);
}

void flagbody(struct ___Object___ *ptr, int flag) {
  struct parameterwrapper *flagptr=(struct parameterwrapper *)ptr->flagptr;
  ptr->flag=flag;
  
  /*Remove object from all queues */
  while(flagptr!=NULL) {
    struct parameterwrapper *next;
    struct ___Object___ * tag=ptr->___tags___;
    RuntimeHashget(flagptr->objectset, (int) ptr, (int *) &next);
    RuntimeHashremove(flagptr->objectset, (int)ptr, (int) next);
    flagptr=next;
  }
  
  {
    struct QueueItem *tmpptr;
    struct parameterwrapper * parameter=objectqueues[ptr->type];
    int i;
    struct parameterwrapper * prevptr=NULL;
    struct ___Object___ *tagptr=ptr->___tags___;
      
    /* Outer loop iterates through all parameter queues an object of
       this type could be in.  */

    while(parameter!=NULL) {
      /* Check tags */
      if (parameter->numbertags>0) {
	if (tagptr==NULL)
	  goto nextloop;
	else if(tagptr->type==TAGTYPE) {
	  struct ___TagDescriptor___ * tag=(struct ___TagDescriptor___*) tagptr;
	  for(i=0;i<parameter->numbertags;i++) {
	    //slotid is parameter->tagarray[2*i];
	    int tagid=parameter->tagarray[2*i+1];
	    if (tagid!=tagptr->flag)
	      goto nextloop; /*We don't have this tag */	  
	  }
	} else {
	  struct ArrayObject * ao=(struct ArrayObject *) tagptr;
	  for(i=0;i<parameter->numbertags;i++) {
	    //slotid is parameter->tagarray[2*i];
	    int tagid=parameter->tagarray[2*i+1];
	    int j;
	    for(j=0;j<ao->___cachedCode___;j++) {
	      if (tagid==ARRAYGET(ao, struct ___TagDescriptor___*, i)->flag)
		goto foundtag;
	    }
	    goto nextloop;
	  foundtag:
	    ;
	  }
	}
      }

      /* Check flags */
      for(i=0;i<parameter->numberofterms;i++) {
	int andmask=parameter->intarray[i*2];
	int checkmask=parameter->intarray[i*2+1];
	if ((flag&andmask)==checkmask) {
	  enqueuetasks(parameter, prevptr, ptr);
	  prevptr=parameter;
	  break;
	}
      }
    nextloop:
      parameter=parameter->next;
    }
    ptr->flagptr=prevptr;
  }
}
  
void enqueuetasks(struct parameterwrapper *parameter, struct parameterwrapper *prevptr, struct ___Object___ *ptr) {
  void * taskpointerarray[MAXTASKPARAMS];
  int j;
  int numparams=parameter->task->numParameters;
  int numiterators=parameter->task->numTotal-1;

  struct taskdescriptor * task=parameter->task;
  
  RuntimeHashadd(parameter->objectset, (int) ptr, (int) prevptr);
  
  /* Add enqueued object to parameter vector */
  taskpointerarray[parameter->slot]=ptr;

  /* Reset iterators */
  for(j=0;j<numiterators;j++) {
    toiReset(&parameter->iterators[j]);
  }

  /* Find initial state */
  for(j=0;j<numiterators;j++) {
  backtrackinit:
    if(toiHasNext(&parameter->iterators[j], taskpointerarray))
      toiNext(&parameter->iterators[j], taskpointerarray);
    else if (j>0) {
      /* Need to backtrack */
      toiReset(&parameter->iterators[j]);
      j--;
      goto backtrackinit;
    } else {
      /* Nothing to enqueue */
      return;
    }
  }

  
  while(1) {
    /* Enqueue current state */
    struct taskparamdescriptor *tpd=RUNMALLOC(sizeof(struct taskparamdescriptor));
    tpd->task=task;
    tpd->numParameters=numiterators+1;
    tpd->parameterArray=RUNMALLOC(sizeof(void *)*(numiterators+1));
    for(j=0;j<=numiterators;j++)
      tpd->parameterArray[j]=taskpointerarray[j];
    
    /* Enqueue task */
    if (!gencontains(failedtasks, tpd)&&!gencontains(activetasks,tpd)) {
      genputtable(activetasks, tpd, tpd);
    } else {
      RUNFREE(tpd->parameterArray);
      RUNFREE(tpd);
    }
    
    /* This loop iterates to the next parameter combination */
    if (numiterators==0)
      return;

    for(j=numiterators-1; j<numiterators;j++) {
    backtrackinc:
      if(toiHasNext(&parameter->iterators[j], taskpointerarray))
	toiNext(&parameter->iterators[j], taskpointerarray);
      else if (j>0) {
	/* Need to backtrack */
	toiReset(&parameter->iterators[j]);
	j--;
	goto backtrackinc;
      } else {
	/* Nothing more to enqueue */
	return;
      }
    }
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
  sigaction(SIGPIPE,&sig,0);

  /* Zero fd set */
  FD_ZERO(&readfds);
  maxreadfd=0;
  fdtoobject=allocateRuntimeHash(100);

  /* Map first block of memory to protected, anonymous page */
  mmap(0, 0x1000, 0, MAP_SHARED|MAP_FIXED|MAP_ANON, -1, 0);

  newtask:
  while((hashsize(activetasks)>0)||(maxreadfd>0)) {

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
	    //	    printf("Setting fd %d\n",fd);
	    if (RuntimeHashget(fdtoobject, fd,(int *) &objptr)) {
	      intflagorand(objptr,1,0xFFFFFFFF); /* Set the first flag to 1 */
	    }
	  }
	}
      }
    }

    /* See if there are any active tasks */
    if (hashsize(activetasks)>0) {
      int i;
      currtpd=(struct taskparamdescriptor *) getfirstkey(activetasks);
      genfreekey(activetasks, currtpd);

      /* Check if this task has failed */
      if (gencontains(failedtasks, currtpd)) {
	// Free up task parameter descriptor
	RUNFREE(currtpd->parameterArray);
	RUNFREE(currtpd);
	goto newtask;
      }
      int numparams=currtpd->task->numParameters;
      int numtotal=currtpd->task->numTotal;

      /* Make sure that the parameters are still in the queues */
      for(i=0;i<numparams;i++) {
	void * parameter=currtpd->parameterArray[i];
	struct parameterdescriptor * pd=currtpd->task->descriptorarray[i];
	struct parameterwrapper *pw=(struct parameterwrapper *) pd->queue;
	int j;
	/* Check that object is still in queue */
	if (!RuntimeHashcontainskey(pw->objectset, (int) parameter)) {
	  RUNFREE(currtpd->parameterArray);
	  RUNFREE(currtpd);
	  goto newtask;
	}
	/* Check that object still has necessary tags */
	for(j=0;j<pd->numbertags;j++) {
	  int slotid=pd->tagarray[2*i]+numparams;
	  struct ___TagDescriptor___ *tagd=currtpd->parameterArray[slotid];
	  if (!containstag(parameter, tagd)) {
	    RUNFREE(currtpd->parameterArray);
	    RUNFREE(currtpd);
	    goto newtask;
	  }
	}
	
	taskpointerarray[i+OFFSET]=parameter;
      }
      /* Copy the tags */
      for(;i<numtotal;i++) {
	taskpointerarray[i+OFFSET]=currtpd->parameterArray[i];
      }

      {
	/* Checkpoint the state */
	forward=allocateRuntimeHash(100);
	reverse=allocateRuntimeHash(100);
	void ** checkpoint=makecheckpoint(currtpd->task->numParameters, currtpd->parameterArray, forward, reverse);
	int x;
	if (x=setjmp(error_handler)) {
	  /* Recover */
	  int h;
#ifdef DEBUG
	  printf("Fatal Error=%d, Recovering!\n",x);
#endif
	  genputtable(failedtasks,currtpd,currtpd);
	  restorecheckpoint(currtpd->task->numParameters, currtpd->parameterArray, checkpoint, forward, reverse);
	  freeRuntimeHash(forward);
	  freeRuntimeHash(reverse);
	  freemalloc();
	  forward=NULL;
	  reverse=NULL;
	} else {
	  if (injectfailures) {
	    if ((((double)random())/RAND_MAX)<failurechance) {
	      printf("\nINJECTING TASK FAILURE to %s\n", currtpd->task->name);
	      longjmp(error_handler,10);
	    }
	  }
	  /* Actually call task */
#ifdef PRECISE_GC
	  ((int *)taskpointerarray)[0]=currtpd->task->numParameters;
	  taskpointerarray[1]=NULL;
#endif

	  if (debugtask) {
	    printf("ENTER %s count=%d\n",currtpd->task->name, (instaccum-instructioncount));
	    ((void (*) (void **)) currtpd->task->taskptr)(taskpointerarray);
	    printf("EXIT %s count=%d\n",currtpd->task->name, (instaccum-instructioncount));
	  } else
	    ((void (*) (void **)) currtpd->task->taskptr)(taskpointerarray);
	  freeRuntimeHash(forward);
	  freeRuntimeHash(reverse);
	  freemalloc();
	  // Free up task parameter descriptor
	  RUNFREE(currtpd->parameterArray);
	  RUNFREE(currtpd);
	  forward=NULL;
	  reverse=NULL;
	}
      }
    }
  }
}

/* This function processes an objects tags */
void processtags(struct parameterdescriptor *pd, int index, struct parameterwrapper *parameter, int * iteratorcount, int *statusarray, int numparams) {
  int i;

  for(i=0;i<pd->numbertags;i++) {
    int slotid=pd->tagarray[2*i];
    int tagid=pd->tagarray[2*i+1];
    
    if (statusarray[slotid+numparams]==0) {
      parameter->iterators[*iteratorcount].istag=1;
      parameter->iterators[*iteratorcount].tagid=tagid;
      parameter->iterators[*iteratorcount].slot=slotid+numparams;
      parameter->iterators[*iteratorcount].tagobjectslot=index;
      statusarray[slotid+numparams]=1;
      (*iteratorcount)++;
    }
  }
}


void processobject(struct parameterwrapper *parameter, int index, struct parameterdescriptor *pd, int *iteratorcount, int * statusarray, int numparams) {
  int i;
  int tagcount=0;
  struct RuntimeHash * objectset=((struct parameterwrapper *)pd->queue)->objectset;

  parameter->iterators[*iteratorcount].istag=0;
  parameter->iterators[*iteratorcount].slot=index;
  parameter->iterators[*iteratorcount].objectset=objectset;
  statusarray[index]=1;

  for(i=0;i<pd->numbertags;i++) {
    int slotid=pd->tagarray[2*i];
    int tagid=pd->tagarray[2*i+1];
    if (statusarray[slotid+numparams]!=0) {
      /* This tag has already been enqueued, use it to narrow search */
      parameter->iterators[*iteratorcount].tagbindings[tagcount]=slotid+numparams;
      tagcount++;
    }
  }
  parameter->iterators[*iteratorcount].numtags=tagcount;

  (*iteratorcount)++;
}

/* This function builds the iterators for a task & parameter */

void builditerators(struct taskdescriptor * task, int index, struct parameterwrapper * parameter) {
  int statusarray[MAXTASKPARAMS];
  int i;
  int numparams=task->numParameters;
  int iteratorcount=0;
  for(i=0;i<MAXTASKPARAMS;i++) statusarray[i]=0;

  statusarray[index]=1; /* Initial parameter */
  /* Process tags for initial iterator */
  
  processtags(task->descriptorarray[index], index, parameter, & iteratorcount, statusarray, numparams);
  
  while(1) {
  loopstart:
    /* Check for objects with existing tags */
    for(i=0;i<numparams;i++) {
      if (statusarray[i]==0) {
	struct parameterdescriptor *pd=task->descriptorarray[i];
	int j;
	for(j=0;j<pd->numbertags;j++) {
	  int slotid=pd->tagarray[2*j];
	  if(statusarray[slotid+numparams]!=0) {
	    processobject(parameter, i, pd, &iteratorcount, statusarray, numparams);
	    processtags(pd, i, parameter, &iteratorcount, statusarray, numparams);
	    goto loopstart;
	  }
	}
      }
    }
    /* Nothing with a tag enqueued */

    for(i=0;i<numparams;i++) {
      if (statusarray[i]==0) {
	struct parameterdescriptor *pd=task->descriptorarray[i];
	processobject(parameter, i, pd, &iteratorcount, statusarray, numparams);
	processtags(pd, i, parameter, &iteratorcount, statusarray, numparams);
	goto loopstart;
      }
    }

    /* Nothing left */
    return;
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
      parameter->numbertags=param->numbertags;
      parameter->tagarray=param->tagarray;
      parameter->task=task;
      /* Link new queue in */
      while((*ptr)!=NULL)
	ptr=&((*ptr)->next);
      (*ptr)=parameter;
    }

    /* Build iterators for parameters */
    for(j=0;j<task->numParameters;j++) {
      struct parameterdescriptor *param=task->descriptorarray[j];
      struct parameterwrapper *parameter=param->queue;      
      parameter->slot=j;
      builditerators(task, j, parameter);
    }
  }
}

void toiReset(struct tagobjectiterator * it) {
  if (it->istag) {
    it->tagobjindex=0;
  } else if (it->numtags>0) {
    it->tagobjindex=0;
  } else {
    RuntimeHashiterator(it->objectset, &it->it);
  }
}

int toiHasNext(struct tagobjectiterator *it, void ** objectarray) {
  if (it->istag) {
    /* Iterate tag */
    /* Get object with tags */
    struct ___Object___ *obj=objectarray[it->tagobjectslot];
    struct ___Object___ *tagptr=obj->___tags___;
    if (tagptr->type==TAGTYPE) {
      if ((it->tagobjindex==0)&& /* First object */
	  (it->tagid==((struct ___TagDescriptor___ *)tagptr)->flag)) /* Right tag type */
	return 1;
      else
	return 0;
    } else {
      struct ArrayObject *ao=(struct ArrayObject *) tagptr;
      int tagindex=it->tagobjindex;
      for(;tagindex<ao->___cachedCode___;tagindex++) {
	struct ___TagDescriptor___ *td=ARRAYGET(ao, struct ___TagDescriptor___ *, tagindex);
	if (td->flag==it->tagid) {
	  it->tagobjindex=tagindex; /* Found right type of tag */
	  return 1;
	}
      }
      return 0;
    }
  } else if (it->numtags>0) {
    /* Use tags to locate appropriate objects */
    struct ___TagDescriptor___ *tag=objectarray[it->tagbindings[0]];
    struct ___Object___ *objptr=tag->flagptr;
    int i;
    if (objptr->type!=OBJECTARRAYTYPE) {
      if (it->tagobjindex>0)
	return 0;
      if (!RuntimeHashcontainskey(it->objectset, (int) objptr))
	return 0;
      for(i=1;i<it->numtags;i++) {
	struct ___TagDescriptor___ *tag2=objectarray[it->tagbindings[i]];
	if (!containstag(objptr,tag2))
	  return 0;
      }
      return 1;
    } else {
      struct ArrayObject *ao=(struct ArrayObject *) objptr;
      int tagindex;
      int i;
      for(tagindex=it->tagobjindex;tagindex<ao->___cachedCode___;tagindex++) {
	struct ___Object___ *objptr=ARRAYGET(ao, struct ___Object___*, tagindex);
	if (!RuntimeHashcontainskey(it->objectset, (int) objptr))
	  continue;
	for(i=1;i<it->numtags;i++) {
	  struct ___TagDescriptor___ *tag2=objectarray[it->tagbindings[i]];
	  if (!containstag(objptr,tag2))
	    goto nexttag;
	}
	return 1;
      nexttag:
	;
      }
      it->tagobjindex=tagindex;
      return 0;
    }
  } else {
    return RunhasNext(&it->it);
  }
}

int containstag(struct ___Object___ *ptr, struct ___TagDescriptor___ *tag) {
  int j;
  struct ___Object___ * objptr=tag->flagptr;
  if (objptr->type==OBJECTARRAYTYPE) {
    struct ArrayObject *ao=(struct ArrayObject *)objptr;
    for(j=0;j<ao->___cachedCode___;j++) {
      if (ptr==ARRAYGET(ao, struct ___Object___*, j))
	return 1;
    }
    return 0;
  } else
    return objptr==ptr;
}

void toiNext(struct tagobjectiterator *it , void ** objectarray) {
  /* hasNext has all of the intelligence */
  if(it->istag) {
    /* Iterate tag */
    /* Get object with tags */
    struct ___Object___ *obj=objectarray[it->tagobjectslot];
    struct ___Object___ *tagptr=obj->___tags___;
    if (tagptr->type==TAGTYPE) {
      it->tagobjindex++;
      objectarray[it->slot]=tagptr;
    } else {
      struct ArrayObject *ao=(struct ArrayObject *) tagptr;
      objectarray[it->slot]=ARRAYGET(ao, struct ___TagDescriptor___ *, it->tagobjindex++);
    }
  } else if (it->numtags>0) {
    /* Use tags to locate appropriate objects */
    struct ___TagDescriptor___ *tag=objectarray[it->tagbindings[0]];
    struct ___Object___ *objptr=tag->flagptr;
    if (objptr->type!=OBJECTARRAYTYPE) {
      it->tagobjindex++;
      objectarray[it->slot]=objptr;
    } else {
      struct ArrayObject *ao=(struct ArrayObject *) objptr;
      objectarray[it->slot]=ARRAYGET(ao, struct ___Object___ *, it->tagobjindex++);
    }
  } else {
    /* Iterate object */
    objectarray[it->slot]=(void *)Runkey(&it->it);
    Runnext(&it->it);
  }
}


#endif

void exithandler(int sig, siginfo_t *info, void * uap) {
  exit(0);
}

void initializeexithandler() {
  struct sigaction sig;
  sig.sa_sigaction=&exithandler;
  sig.sa_flags=SA_SIGINFO;
  sigemptyset(&sig.sa_mask);
  sigaction(SIGUSR2, &sig, 0);
}


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
      printf("FAILURE!!! %d\n",numfailures);
      longjmp(error_handler,11);
    }
  }
#else
#ifdef THREADS
  if (injectinstructionfailures) {
    if (numfailures==0)
      return;
    instaccum+=failurecount;
    if ((((double)random())/RAND_MAX)<instfailurechance) {
      if (numfailures>0)
	numfailures--;
      printf("FAILURE!!! %d\n",numfailures);
      threadexit();
    }
  }
#endif
#endif
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
  struct ___Object___ * v=(struct ___Object___ *) mygcmalloc((struct garbagelist *) ptr, classsize[type]);
  v->type=type;
#ifdef THREADS
  v->tid=0;
  v->lockentry=0;
  v->lockcount=0;
#endif
  return v;
}

/* Array allocation function */

struct ArrayObject * allocate_newarray(void * ptr, int type, int length) {
  struct ArrayObject * v=mygcmalloc((struct garbagelist *) ptr, sizeof(struct ArrayObject)+length*classsize[type]);
  v->type=type;
  if (length<0) {
    printf("ERROR: negative array\n");
    return NULL;
  }
  v->___length___=length;
#ifdef THREADS
  v->tid=0;
  v->lockentry=0;
  v->lockcount=0;
#endif
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
  int i;
#ifdef PRECISE_GC
  struct ArrayObject * chararray=allocate_newarray((struct garbagelist *)ptr, CHARARRAYTYPE, length);
  int ptrarray[]={1, (int) ptr, (int) chararray};
  struct ___String___ * strobj=allocate_new((struct garbagelist *) &ptrarray, STRINGTYPE);
  chararray=(struct ArrayObject *) ptrarray[2];
#else
  struct ArrayObject * chararray=allocate_newarray(CHARARRAYTYPE, length);
  struct ___String___ * strobj=allocate_new(STRINGTYPE);
#endif
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
#ifdef THREADS
  threadexit();
#else
  exit(-1);
#endif
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
