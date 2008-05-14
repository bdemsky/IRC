#ifdef TASK
#include "runtime.h"
#include "structdefs.h"
#include "mem.h"
#include "checkpoint.h"
#include "Queue.h"
#include "SimpleHash.h"
#include "GenericHashtable.h"
#include <sys/select.h>
#include <sys/types.h>
#include <sys/mman.h>
#include <string.h>
#include <signal.h>
#include <assert.h>
#include <errno.h>
#ifdef RAW
#elif defined THREADSIMULATE
#if 0
#include <sys/mman.h> // for mmap
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>

int offset_transObj = 0;
#endif

// use POSIX message queue
// for each core, its message queue named as
// /msgqueue_corenum
#include <mqueue.h>
#include <sys/stat.h>
#endif
/*
extern int injectfailures;
extern float failurechance;
*/
extern int debugtask;
extern int instaccum;

#ifdef CONSCHECK
#include "instrument.h"
#endif

struct genhashtable * activetasks;
struct genhashtable * failedtasks;
struct taskparamdescriptor * currtpd;
struct RuntimeHash * forward;
struct RuntimeHash * reverse;

int corestatus[NUMCORES]; // records status of each core
                          // 1: running tasks
						  // 0: stall
int numsendobjs[NUMCORES]; // records how many objects a core has sent out
int numreceiveobjs[NUMCORES]; // records how many objects a core has received
#ifdef THREADSIMULATE
struct thread_data {
	int corenum;
	int argc;
	char** argv;
	int numsendobjs;
	int numreceiveobjs;
};
struct thread_data thread_data_array[NUMCORES];
mqd_t mqd[NUMCORES];
static pthread_key_t key;
static struct RuntimeHash* locktbl;
static pthread_rwlock_t rwlock_tbl;
static pthread_rwlock_t rwlock_init;
#endif
bool transStallMsg(int targetcore);
void transTerminateMsg(int targetcore);
void run(void * arg);
bool getreadlock(void* ptr);
void releasereadlock(void* ptr);
bool getwritelock(void* ptr);
void releasewritelock(void* ptr);

int main(int argc, char **argv) {
#ifdef THREADSIMULATE
	errno = 0;
	int tids[NUMCORES];
	int rc[NUMCORES];
	pthread_t threads[NUMCORES];
	int i = 0;

	// initialize three arrays and msg queue array
	char * pathhead = "/msgqueue_";
	int targetlen = strlen(pathhead);
	for(i = 0; i < NUMCORES; ++i) {
		corestatus[i] = 1;
		numsendobjs[i] = 0;
		numreceiveobjs[i] = 0;

		char corenumstr[3];
		int sourcelen = 0;
		if(i < 10) {
			corenumstr[0] = i + '0';
			corenumstr[1] = '\0';
			sourcelen = 1;
		} else if(i < 100) {
			corenumstr[1] = i %10 + '0';
			corenumstr[0] = (i / 10) + '0';
			corenumstr[2] = '\0';
			sourcelen = 2;
		} else {
			printf("Error: i >= 100\n");
			fflush(stdout);
			exit(-1);
		}
		char path[targetlen + sourcelen + 1];
		strcpy(path, pathhead);
		strncat(path, corenumstr, sourcelen);
		int oflags = O_RDONLY|O_CREAT|O_NONBLOCK;
		int omodes = S_IRWXU|S_IRWXG|S_IRWXO;
		mq_unlink(path);
		mqd[i]= mq_open(path, oflags, omodes, NULL);
		if(mqd[i] == -1) {
			printf("[Main] mq_open %s fails: %d, error: %s\n", path, mqd[i], strerror(errno));
			exit(-1);
		} else {
			printf("[Main] mq_open %s returns: %d\n", path, mqd[i]);
		}
	}

	// create the key
	pthread_key_create(&key, NULL);

	// create the lock table and initialize its mutex
	locktbl = allocateRuntimeHash(20);
	int rc_locktbl = pthread_rwlock_init(&rwlock_tbl, NULL);
	printf("[Main] initialize the rwlock for lock table: %d error: \n", rc_locktbl, strerror(rc_locktbl));

/*	if(argc < 2) {
		printf("Usage: <bin> <corenum>\n");
		fflush(stdout);
		exit(-1);
	}

	int cnum = 0;
	char * number = argv[1];
	int len = strlen(number);
	for(i = 0; i < len; ++i) {
		cnum = (number[i] - '0') + cnum * 10;	
	}
*/
	for(i = 0; i < NUMCORES; ++i) {
	/*	if(STARTUPCORE == i) {
			continue;
		}*/
		thread_data_array[i].corenum = i;
		thread_data_array[i].argc = argc;
		thread_data_array[i].argv = argv;
		thread_data_array[i].numsendobjs = 0;
		thread_data_array[i].numreceiveobjs = 0;
		printf("[main] creating thread %d\n", i);
		rc[i] = pthread_create(&threads[i], NULL, run, (void *)&thread_data_array[i]);
        if (rc[i]){
			printf("[main] ERROR; return code from pthread_create() is %d\n", rc[i]);
			fflush(stdout);
			exit(-1);
		}
	}//*/
	
	/*// do stuff of startup core
	thread_data_array[STARTUPCORE].corenum = STARTUPCORE;
	thread_data_array[STARTUPCORE].argc = argc;// - 1;
	thread_data_array[STARTUPCORE].argv = argv;//&argv[1];
	thread_data_array[STARTUPCORE].numsendobjs = 0;
	thread_data_array[STARTUPCORE].numreceiveobjs = 0;
	run(&thread_data_array[STARTUPCORE]);*/
	pthread_exit(NULL);
}

void run(void* arg) {
	struct thread_data * my_tdata = (struct thread_data *)arg;
	//corenum = my_tdata->corenum;
	//void * ptr = malloc(sizeof(int));
	//*((int*)ptr) = my_tdata->corenum;
	pthread_setspecific(key, (void *)my_tdata->corenum);
	int argc = my_tdata->argc;
	char** argv = my_tdata->argv;
	printf("[run, %d] Thread %d runs: %x\n", my_tdata->corenum, my_tdata->corenum, (int)pthread_self());
	fflush(stdout);

#endif

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

#ifdef THREADSIMULATE

  int i = 0;
  // check if there are new objects coming
  bool sendStall = false;

  int numofcore = pthread_getspecific(key);
  while(true) {
	  switch(receiveObject()) {
		  case 0: {
					  printf("[run, %d] receive an object\n", numofcore);
					  sendStall = false;
					  // received an object
					  // check if there are new active tasks can be executed
					  executetasks();
					  break;
				  }
		  case 1: {
					  //printf("[run, %d] no msg\n", numofcore);
					  // no msg received
					  if(STARTUPCORE == numofcore) {
						  corestatus[numofcore] = 0;
						  // check the status of all cores
						  bool allStall = true;
						  for(i = 0; i < NUMCORES; ++i) {
							  if(corestatus[i] != 0) {
								  allStall = false;
								  break;
							  }
						  }
						  if(allStall) {
							  // check if the sum of send objs and receive obj are the same
							  // yes->terminate
							  // no->go on executing
							  int sumsendobj = 0;
							  for(i = 0; i < NUMCORES; ++i) {
								  sumsendobj += numsendobjs[i];
							  }
							  for(i = 0; i < NUMCORES; ++i) {
								  sumsendobj -= numreceiveobjs[i];
							  }
							  if(0 == sumsendobj) {
								  // terminate
								  // TODO
								 /* for(i = 0; i < NUMCORES; ++i) {
									  if(i != corenum) {
										  transTerminateMsg(i);
									  }
								  }
								  mq_close(mqd[corenum]);*/
								  
								  // release all locks
								  struct RuntimeIterator* it_lock = RuntimeHashcreateiterator(locktbl); 
								  while(0 != RunhasNext(it_lock)) {
									  int key = Runkey(it_lock);
									  pthread_rwlock_t* rwlock_obj = (pthread_rwlock_t*)Runnext(it_lock);
									  int rc_des = pthread_rwlock_destroy(rwlock_obj);
									  printf("[run, %d] destroy the rwlock for object: %d error: \n", numofcore, key, strerror(rc_des));
								  }
								  freeRuntimeHash(locktbl);
								  locktbl = NULL;
								  RUNFREE(it_lock);

								  // destroy all message queues
								  char * pathhead = "/msgqueue_";
								  int targetlen = strlen(pathhead);
								  for(i = 0; i < NUMCORES; ++i) {
									  char corenumstr[3];
									  int sourcelen = 0;
									  if(i < 10) {
										  corenumstr[0] = i + '0';
										  corenumstr[1] = '\0';
										  sourcelen = 1;
									  } else if(i < 100) {
										  corenumstr[1] = i %10 + '0';
										  corenumstr[0] = (i / 10) + '0';
										  corenumstr[2] = '\0';
										  sourcelen = 2;
									  } else {
										  printf("Error: i >= 100\n");	
										  fflush(stdout);
										  exit(-1);
									  }
									  char path[targetlen + sourcelen + 1];
									  strcpy(path, pathhead);
									  strncat(path, corenumstr, sourcelen);
									  mq_unlink(path);
								  }

								  printf("[run, %d] terminate!\n", numofcore);
								  fflush(stdout);
								  exit(0);
							  }
						  }
					  } else {
						  if(!sendStall) {
							  // send StallMsg to startup core
							  sendStall = transStallMsg(STARTUPCORE);
						  }
					  }
					  break;
				  }
		  case 2: {
					  printf("[run, %d] receive a stall msg\n", numofcore);
					  // receive a Stall Msg, do nothing
					  assert(STARTUPCORE == numofcore); // only startup core can receive such msg
					  sendStall = false;
					  break;
				  }
		 /* case 3: {
					  printf("[run, %d] receive a terminate msg\n", numofcore);
					  // receive a terminate Msg
					  assert(STARTUPCORE != corenum); // only non-startup core can receive such msg
					  mq_close(mqd[corenum]);
					  fflush(stdout);
					  exit(0);
					  break;
				  }*/
		  default: {
					   printf("[run, %d] Error: invalid message type.\n", numofcore);
					   fflush(stdout);
					   exit(-1);
					   break;
				   }
	  }
  }
#endif
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
  
  startupobject->isolate = 1;
  startupobject->version = 0;

  /* Set initialized flag for startup object */ 
  flagorandinit(startupobject,1,0xFFFFFFFF);
  enqueueObject(startupobject, NULL, 0);
  //enqueueObject(startupobject, objq4startupobj[corenum], numqueues4startupobj[corenum]);
}

int hashCodetpd(struct taskparamdescriptor *ftd) {
  int hash=(int)ftd->task;
  int i;
  for(i=0;i<ftd->numParameters;i++){ 
    hash^=(int)ftd->parameterArray[i];
  }
  return hash;
}

int comparetpd(struct taskparamdescriptor *ftd1, struct taskparamdescriptor *ftd2) {
  int i;
  if (ftd1->task!=ftd2->task)
    return 0;
  for(i=0;i<ftd1->numParameters;i++)
    if(ftd1->parameterArray[i]!=ftd2->parameterArray[i])
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
	tagd->flagptr=(struct ___Object___ *) aonew;
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

void flagbody(struct ___Object___ *ptr, int flag, struct parameterwrapper ** queues, int length, bool isnew);
 
 int flagcomp(const int *val1, const int *val2) {
   return (*val1)-(*val2);
 } 

void flagorand(void * ptr, int ormask, int andmask, struct parameterwrapper ** queues, int length) {
    {
      int oldflag=((int *)ptr)[1];
      int flag=ormask|oldflag;
      flag&=andmask;
	  flagbody(ptr, flag, queues, length, false);
    }
}
 
bool intflagorand(void * ptr, int ormask, int andmask) {
    {
      int oldflag=((int *)ptr)[1];
      int flag=ormask|oldflag;
      flag&=andmask;
      if (flag==oldflag) /* Don't do anything */
	return false;
      else {
		 flagbody(ptr, flag, NULL, 0, false);
		 return true;
	  }
    }
}

void flagorandinit(void * ptr, int ormask, int andmask) {
  int oldflag=((int *)ptr)[1];
  int flag=ormask|oldflag;
  flag&=andmask;
  flagbody(ptr,flag,NULL,0,true);
}

void flagbody(struct ___Object___ *ptr, int flag, struct parameterwrapper ** vqueues, int vlength, bool isnew) {
  struct parameterwrapper * flagptr = NULL;
  int i = 0;
  struct parameterwrapper ** queues = vqueues;
  int length = vlength;
  if((!isnew) && (queues == NULL)) {
#ifdef THREADSIMULATE
	  int numofcore = pthread_getspecific(key);
	  queues = objectqueues[numofcore][ptr->type];
	  length = numqueues[numofcore][ptr->type];
#else
	  queues = objectqueues[corenum][ptr->type];
	  length = numqueues[corenum][ptr->type];
#endif
  }
  ptr->flag=flag;
  
  /*Remove object from all queues */
  for(i = 0; i < length; ++i) {
	  flagptr = queues[i];
	int next;
    int UNUSED, UNUSED2;
    int * enterflags;
    ObjectHashget(flagptr->objectset, (int) ptr, (int *) &next, (int *) &enterflags, &UNUSED, &UNUSED2);
    ObjectHashremove(flagptr->objectset, (int)ptr);
    if (enterflags!=NULL)
      free(enterflags);
  }
 }

 void enqueueObject(void * vptr, struct parameterwrapper ** vqueues, int vlength) {
   struct ___Object___ *ptr = (struct ___Object___ *)vptr;
  
  {
    struct QueueItem *tmpptr;
	struct parameterwrapper * parameter=NULL;
	int j;
	struct parameterwrapper ** queues = vqueues;
	int length = vlength;
	if(queues == NULL) {
#ifdef THREADSIMULATE
		int numofcore = pthread_getspecific(key);
		queues = objectqueues[numofcore][ptr->type];
		length = numqueues[numofcore][ptr->type];
#else
		queues = objectqueues[corenum][ptr->type];
		length = numqueues[corenum][ptr->type];
#endif
	}
    int i;
    struct parameterwrapper * prevptr=NULL;
    struct ___Object___ *tagptr=ptr->___tags___;
    
    /* Outer loop iterates through all parameter queues an object of
       this type could be in.  */
    
	for(j = 0; j < length; ++j) {
		parameter = queues[j];
      /* Check tags */
      if (parameter->numbertags>0) {
	if (tagptr==NULL)
	  goto nextloop;//that means the object has no tag but that param needs tag
	else if(tagptr->type==TAGTYPE) {//one tag
	  struct ___TagDescriptor___ * tag=(struct ___TagDescriptor___*) tagptr;
	  for(i=0;i<parameter->numbertags;i++) {
	    //slotid is parameter->tagarray[2*i];
	    int tagid=parameter->tagarray[2*i+1];
	    if (tagid!=tagptr->flag)
	      goto nextloop; /*We don't have this tag */	  
	   }
	} else {//multiple tags
	  struct ArrayObject * ao=(struct ArrayObject *) tagptr;
	  for(i=0;i<parameter->numbertags;i++) {
	    //slotid is parameter->tagarray[2*i];
	    int tagid=parameter->tagarray[2*i+1];
	    int j;
	    for(j=0;j<ao->___cachedCode___;j++) {
	      if (tagid==ARRAYGET(ao, struct ___TagDescriptor___*, j)->flag)
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
	if ((ptr->flag&andmask)==checkmask) {
	  enqueuetasks(parameter, prevptr, ptr, NULL, 0);
	  prevptr=parameter;
	  break;
	}
      }
    nextloop:
	  ;
    }
  }
}

// transfer an object to targetcore
// format: object
void transferObject(void * obj, int targetcore) {
	int type=((int *)obj)[0];
	assert(type < NUMCLASSES); // can only transfer normal object
    int size=classsize[type];

#ifdef RAW

#elif defined THREADSIMULATE
#if 0
    // use shared memory to transfer objects between cores
	int fd = 0; // mapped file
	void * p_map = NULL;
	char * filepath = "/scratch/transObj/file_" + targetcore + ".txt";
	int offset;
	// open the file 
	fd = open(filepath, O_CREAT|O_WRONLY|O_APPEND, 00777); // append to end of the file
	offset = lseek(fd, 0, SEEK_CUR);
	if(offset == -1) {
		printf("fail to open file " + filepath + " in transferObject.\n");
		fflush(stdout);
		exit(-1);
	}
	lseek(fd, size + sizeof(int)*2, SEEK_CUR);
	write(fd, "", 1); 
	p_map = (void *)mmap(NULL,size+sizeof(int)*2,PROT_WRITE,MAP_SHARED,fd,offset);
	close(fd);
	memcpy(p_map, type, sizeof(int));
	memcpy(p_map+sizeof(int), corenum, sizeof(int));
	memcpy((p_map+sizeof(int)*2), obj, size);
	munmap(p_map, size+sizeof(int)*2); 
	//printf( "umap ok \n" );
#endif

	int numofcore = pthread_getspecific(key);

	// use POSIX message queue to transfer objects between cores
	mqd_t mqdnum;
	char corenumstr[3];
	int sourcelen = 0;
	if(targetcore < 10) {
		corenumstr[0] = targetcore + '0';
		corenumstr[1] = '\0';
		sourcelen = 1;
	} else if(targetcore < 100) {
		corenumstr[1] = targetcore % 10 + '0';
		corenumstr[0] = (targetcore / 10) + '0';
		corenumstr[2] = '\0';
		sourcelen = 2;
	} else {
		printf("Error: targetcore >= 100\n");
		fflush(stdout);
		exit(-1);
	}
	char * pathhead = "/msgqueue_";
	int targetlen = strlen(pathhead);
	char path[targetlen + sourcelen + 1];
	strcpy(path, pathhead);
	strncat(path, corenumstr, sourcelen);
	int oflags = O_WRONLY|O_NONBLOCK;
	int omodes = S_IRWXU|S_IRWXG|S_IRWXO;
	mqdnum = mq_open(path, oflags, omodes, NULL);
	if(mqdnum==-1) {
		printf("[transferObject, %d] mq_open %s fail: %d, error: %s\n", numofcore, path, mqdnum, strerror(errno));
		fflush(stdout);
		exit(-1);
	}
	struct ___Object___ * newobj = (struct ___Object___ *)obj;
	if(0 == newobj->isolate) {
		newobj = RUNMALLOC(size);
		memcpy(newobj, obj, size);
		newobj->original=obj;
	}
	int ret;
	do {
		ret=mq_send(mqdnum, (void *)newobj, size, 0); // send the object into the queue
		if(ret != 0) {
			printf("[transferObject, %d] mq_send to %s returned: %d, error: %s\n", numofcore, path, ret, strerror(errno));
		}
	}while(ret!=0);	
	if(numofcore == STARTUPCORE) {
		++numsendobjs[numofcore];
	} else {
		++(thread_data_array[numofcore].numsendobjs);
	}
	printf("[transferObject, %d] mq_send to %s returned: $%x\n", numofcore, path, ret);
#endif
}

// send terminate message to targetcore
// format: -1
bool transStallMsg(int targetcore) {
	struct ___Object___ newobj;
	// use the first four int field to hold msgtype/corenum/sendobj/receiveobj
	newobj.type = -1;

#ifdef RAW
	newobj.flag = corenum;
	newobj.___cachedHash___ = thread_data_array[corenum].numsendobjs;
	newobj.___cachedCode___ = thread_data_array[corenum].numreceiveobjs;

#elif defined THREADSIMULATE
	int numofcore = pthread_getspecific(key);
	newobj.flag = numofcore;
	newobj.___cachedHash___ = thread_data_array[numofcore].numsendobjs;
	newobj.___cachedCode___ = thread_data_array[numofcore].numreceiveobjs;
#if 0
    // use shared memory to transfer objects between cores
	int fd = 0; // mapped file
	void * p_map = NULL;
	char * filepath = "/scratch/transObj/file_" + targetcore + ".txt";
	int offset;
	// open the file 
	fd = open(filepath, O_CREAT|O_WRONLY|O_APPEND, 00777); // append to end of the file
	offset = lseek(fd, 0, SEEK_CUR);
	if(offset == -1) {
		printf("fail to open file " + filepath + " in transferObject.\n");
		fflush(stdout);
		exit(-1);
	}
	lseek(fd, sizeof(int)*2, SEEK_CUR);
	write(fd, "", 1); 
	p_map = (void *)mmap(NULL,sizeof(int)*2,PROT_WRITE,MAP_SHARED,fd,offset);
	close(fd);
	memcpy(p_map, type, sizeof(int));
	memcpy(p_map+sizeof(int), corenum, sizeof(int));
	munmap(p_map, sizeof(int)*2); 
	//printf( "umap ok \n" );
#endif

	// use POSIX message queue to send stall msg to startup core
	assert(targetcore == STARTUPCORE);
	mqd_t mqdnum;
	char corenumstr[3];
	int sourcelen = 0;
	if(targetcore < 10) {
		corenumstr[0] = targetcore + '0';
		corenumstr[1] = '\0';
		sourcelen = 1;
	} else if(targetcore < 100) {
		corenumstr[1] = targetcore % 10 + '0';
		corenumstr[0] = (targetcore / 10) + '0';
		corenumstr[2] = '\0';
		sourcelen = 2;
	} else {
		printf("Error: targetcore >= 100\n");
		fflush(stdout);
		exit(-1);
	}
	char * pathhead = "/msgqueue_";
	int targetlen = strlen(pathhead);
	char path[targetlen + sourcelen + 1];
	strcpy(path, pathhead);
	strncat(path, corenumstr, sourcelen);
	int oflags = O_WRONLY|O_NONBLOCK;
	int omodes = S_IRWXU|S_IRWXG|S_IRWXO;
	mqdnum = mq_open(path, oflags, omodes, NULL);
	if(mqdnum==-1) {
		printf("[transStallMsg, %d] mq_open %s fail: %d, error: %s\n", numofcore, path, mqdnum, strerror(errno));
		fflush(stdout);
		exit(-1);
	}
	int ret;
	ret=mq_send(mqdnum, (void *)&newobj, sizeof(struct ___Object___), 0); // send the object into the queue
	if(ret != 0) {
		printf("[transStallMsg, %d] mq_send to %s returned: %d, error: %s\n", numofcore, path, ret, strerror(errno));
		return false;
	} else {
		printf("[transStallMsg, %d] mq_send to %s returned: $%x\n", numofcore, path, ret);
		printf("<transStallMsg> to %s index: %d, sendobjs: %d, receiveobjs: %d\n", path, newobj.flag, newobj.___cachedHash___, newobj.___cachedCode___);
		return true;
	}
#endif
}
#if 0
// send terminate message to targetcore
// format: -1
void transTerminateMsg(int targetcore) {
	// use the first four int field to hold msgtype/corenum/sendobj/receiveobj
	int type = -2;

#ifdef RAW

#elif defined THREADSIMULATE

	// use POSIX message queue to send stall msg to startup core
	assert(targetcore != STARTUPCORE);
	mqd_t mqdnum;
	char corenumstr[3];
	int sourcelen = 0;
	if(targetcore < 10) {
		corenumstr[0] = targetcore + '0';
		corenumstr[1] = '\0';
		sourcelen = 1;
	} else if(corenum < 100) {
		corenumstr[1] = targetcore % 10 + '0';
		corenumstr[0] = (targetcore / 10) + '0';
		corenumstr[2] = '\0';
		sourcelen = 2;
	} else {
		printf("Error: targetcore >= 100\n");
		fflush(stdout);
		exit(-1);
	}
	char * pathhead = "/msgqueue_";
	int targetlen = strlen(pathhead);
	char path[targetlen + sourcelen + 1];
	strcpy(path, pathhead);
	strncat(path, corenumstr, sourcelen);
	int oflags = O_WRONLY|O_NONBLOCK;
	int omodes = S_IRWXU|S_IRWXG|S_IRWXO;
	mqdnum = mq_open(path, oflags, omodes, NULL);
	if(mqdnum==-1) {
		printf("[transStallMsg] mq_open fail: %d, error: %s\n", mqdnum, strerror(errno));
		fflush(stdout);
		exit(-1);
	}
	int ret;
	do {
		ret=mq_send(mqdnum, (void *)&type, sizeof(int), 0); // send the object into the queue
		if(ret != 0) {
			printf("[transStallMsg] mq_send returned: %d, error: %s\n", ret, strerror(errno));
		}
	}while(ret != 0);
#endif
}
#endif
// receive object transferred from other cores
// or the terminate message from other cores
// format: type [+ object]
// type: -1--stall msg
//      !-1--object
// return value: 0--received an object
//               1--received nothing
//               2--received a Stall Msg
int receiveObject() {
#ifdef RAW

#elif defined THREADSIMULATE
#if 0
    char * filepath = "/scratch/transObj/file_" + corenum + ".txt";
	int fd = 0;
	void * p_map = NULL;
	int type = 0;
	int sourcecorenum = 0;
	int size = 0;
	fd = open(filepath, O_CREAT|O_RDONLY, 00777);
	lseek(fd, offset_transObj, SEEK_SET);
	p_map = (void*)mmap(NULL,sizeof(int)*2,PROT_READ,MAP_SHARED,fd,offset_transObj);
	type = *(int*)p_map;
	sourcecorenum = *(int*)(p_map+sinzeof(int));
	offset_transObj += sizeof(int)*2;
	munmap(p_map,sizeof(int)*2);
	if(type == -1) {
		// sourecorenum has terminated
		++offset_transObj;
		return;
	}
	size = classsize[type];
	p_map = (void*)mmap(NULL,size,PROT_READ,MAP_SHARED,fd,offset_transObj);
	struct ___Object___ * newobj=RUNMALLOC(size);
    memcpy(newobj, p_map, size);
	++offset_transObj;
	enqueueObject(newobj,NULL,0);
#endif
	int numofcore = pthread_getspecific(key);
	// use POSIX message queue to transfer object
	int msglen = 0;
	struct mq_attr mqattr;
	mq_getattr(mqd[numofcore], &mqattr);
	void * msgptr =RUNMALLOC(mqattr.mq_msgsize);
	msglen=mq_receive(mqd[numofcore], msgptr, mqattr.mq_msgsize, NULL); // receive the object into the queue
	if(-1 == msglen) {
		// no msg
		free(msgptr);
		return 1;
	}
	//printf("msg: %s\n",msgptr);
	if(((int*)msgptr)[0] == -1) {
		// StallMsg
		struct ___Object___ * tmpptr = (struct ___Object___ *)msgptr;
		int index = tmpptr->flag;
		corestatus[index] = 0;
		numsendobjs[index] = tmpptr->___cachedHash___;
		numreceiveobjs[index] = tmpptr->___cachedCode___;
		printf("<receiveObject> index: %d, sendobjs: %d, reveiveobjs: %d\n", index, numsendobjs[index], numreceiveobjs[index]);
		free(msgptr);
		return 2;
	} /*else if(((int*)msgptr)[0] == -2) {
		// terminate msg
		return 3;
	} */else {
		// an object
		if(numofcore == STARTUPCORE) {
			++(numreceiveobjs[numofcore]);
		} else {
			++(thread_data_array[numofcore].numreceiveobjs);
		}
		struct ___Object___ * newobj=RUNMALLOC(msglen);
		memcpy(newobj, msgptr, msglen);
		free(msgptr);
		enqueueObject(newobj, NULL, 0);
		return 0;
	}
#endif
}

bool getreadlock(void * ptr) {
#ifdef RAW

#elif defined THREADSIMULATE
	int numofcore = pthread_getspecific(key);

	int rc = pthread_rwlock_tryrdlock(&rwlock_tbl);
	printf("[getreadlock, %d] getting the read lock for locktbl: %d error: \n", numofcore, rc, strerror(rc));
	if(EBUSY == rc) {
		return false;
	}
	if(!RuntimeHashcontainskey(locktbl, (int)ptr)) {
		// no locks for this object
		// first time to operate on this shared object
		// create a lock for it
		rc = pthread_rwlock_unlock(&rwlock_tbl);
		printf("[getreadlock, %d] release the read lock for locktbl: %d error: \n", numofcore, rc, strerror(rc));
		pthread_rwlock_t* rwlock = (pthread_rwlock_t *)RUNMALLOC(sizeof(pthread_rwlock_t));
		memcpy(rwlock, &rwlock_init, sizeof(pthread_rwlock_t));
		rc = pthread_rwlock_init(rwlock, NULL);
		printf("[getreadlock, %d] initialize the rwlock for object %d: %d error: \n", numofcore, (int)ptr, rc, strerror(rc));
		rc = pthread_rwlock_trywrlock(&rwlock_tbl);
		printf("[getreadlock, %d] getting the write lock for locktbl: %d error: \n", numofcore, rc, strerror(rc));
		if(EBUSY == rc) {
			return false;
		} else {
			RuntimeHashadd(locktbl, (int)ptr, (int)rwlock);
			rc = pthread_rwlock_unlock(&rwlock_tbl);
			printf("[getreadlock, %d] release the write lock for locktbl: %d error: \n", numofcore, rc, strerror(rc));
		}
		//rc = pthread_rwlock_rdlock(&rwlock);
		rc = pthread_rwlock_tryrdlock(rwlock);
		printf("[getreadlock, %d] getting read lock for object %d: %d error: \n", numofcore, (int)ptr, rc, strerror(rc));	
		if(EBUSY == rc) {
			return false;
		} else {
			return true;
		}
	} else {
		pthread_rwlock_t* rwlock_obj = NULL;
		RuntimeHashget(locktbl, (int)ptr, (int*)&rwlock_obj);
		rc = pthread_rwlock_unlock(&rwlock_tbl);
		printf("[getreadlock, %d] release the read lock for locktbl: %d error: \n", numofcore, rc, strerror(rc));
		//int rc_obj = pthread_rwlock_rdlock(&rwlock_obj);
		int rc_obj = pthread_rwlock_tryrdlock(rwlock_obj);
		printf("[getreadlock, %d] getting read lock for object %d: %d error: \n", numofcore, (int)ptr, rc_obj, strerror(rc_obj));
		if(EBUSY == rc_obj) {
			return false;
		} else {
			return true;
		}
	}
#endif
}

void releasereadlock(void * ptr) {
#ifdef RAW

#elif defined THREADSIMULATE
	int numofcore = pthread_getspecific(key);
	int rc = pthread_rwlock_rdlock(&rwlock_tbl);
	printf("[releasereadlock, %d] getting the read lock for locktbl: %d error: \n", numofcore, rc, strerror(rc));
	if(!RuntimeHashcontainskey(locktbl, (int)ptr)) {
		printf("[releasereadlock, %d] Error: try to release a lock without previously grab it\n", numofcore);
		exit(-1);
	}
	pthread_rwlock_t* rwlock_obj = NULL;
	RuntimeHashget(locktbl, (int)ptr, (int*)&rwlock_obj);
	int rc_obj = pthread_rwlock_unlock(rwlock_obj);
	printf("[releasereadlock, %d] unlocked object %d: %d error: \n", numofcore, (int)ptr, rc_obj, strerror(rc_obj));
	rc = pthread_rwlock_unlock(&rwlock_tbl);
	printf("[releasereadlock, %d] release the read lock for locktbl: %d error: \n", numofcore, rc, strerror(rc));
#endif
}

bool getwritelock(void * ptr) {
#ifdef RAW

#elif defined THREADSIMULATE
	int numofcore = pthread_getspecific(key);

	int rc = pthread_rwlock_tryrdlock(&rwlock_tbl);
	printf("[getwritelock, %d] getting the read lock for locktbl: %d error: \n", numofcore, rc, strerror(rc));
	if(EBUSY == rc) {
		return false;
	}
	if(!RuntimeHashcontainskey(locktbl, (int)ptr)) {
		// no locks for this object
		// first time to operate on this shared object
		// create a lock for it
		rc = pthread_rwlock_unlock(&rwlock_tbl);
		printf("[getwritelock, %d] release the read lock for locktbl: %d error: \n", numofcore, rc, strerror(rc));
		pthread_rwlock_t* rwlock = (pthread_rwlock_t *)RUNMALLOC(sizeof(pthread_rwlock_t));
		memcpy(rwlock, &rwlock_init, sizeof(pthread_rwlock_t));
		rc = pthread_rwlock_init(rwlock, NULL);
		printf("[getwritelock, %d] initialize the rwlock for object %d: %d error: \n", numofcore, (int)ptr, rc, strerror(rc));
		rc = pthread_rwlock_trywrlock(&rwlock_tbl);
		printf("[getwritelock, %d] getting the write lock for locktbl: %d error: \n", numofcore, rc, strerror(rc));
		if(EBUSY == rc) {
			return false;
		} else {
			RuntimeHashadd(locktbl, (int)ptr, (int)rwlock);
			rc = pthread_rwlock_unlock(&rwlock_tbl);
			printf("[getwritelock, %d] release the write lock for locktbl: %d error: \n", numofcore, rc, strerror(rc));
		}
		//rc = pthread_rwlock_wrlock(rwlock);
		rc = pthread_rwlock_trywrlock(rwlock);
		printf("[getwritelock, %d] getting write lock for object %d: %d error: \n", numofcore, (int)ptr, rc, strerror(rc));	
		if(EBUSY == rc) {
			return false;
		} else {
			return true;
		}
	} else {
		pthread_rwlock_t* rwlock_obj = NULL;
		RuntimeHashget(locktbl, (int)ptr, (int*)&rwlock_obj);
		rc = pthread_rwlock_unlock(&rwlock_tbl);
		printf("[getwritelock, %d] release the read lock for locktbl: %d error: \n", numofcore, rc, strerror(rc));
		//int rc_obj = pthread_rwlock_wrlock(rwlock_obj);
		int rc_obj = pthread_rwlock_trywrlock(rwlock_obj);
		printf("[getwritelock, %d] getting write lock for object %d: %d error: \n", numofcore, (int)ptr, rc_obj, strerror(rc_obj));
		if(EBUSY == rc_obj) {
			return false;
		} else {
			return true;
		}
	}

#endif
}

void releasewritelock(void * ptr) {
#ifdef RAW

#elif defined THREADSIMULATE
	int numofcore = pthread_getspecific(key);
	int rc = pthread_rwlock_rdlock(&rwlock_tbl);
	printf("[releasewritelock, %d] getting the read lock for locktbl: %d error: \n", numofcore, rc, strerror(rc));
	if(!RuntimeHashcontainskey(locktbl, (int)ptr)) {
		printf("[releasewritelock, %d] Error: try to release a lock without previously grab it\n", numofcore);
		exit(-1);
	}
	pthread_rwlock_t* rwlock_obj = NULL;
	RuntimeHashget(locktbl, (int)ptr, (int*)&rwlock_obj);
	int rc_obj = pthread_rwlock_unlock(rwlock_obj);
	printf("[releasewritelock, %d] unlocked object %d: %d error:\n", numofcore, (int)ptr, rc_obj, strerror(rc_obj));
	rc = pthread_rwlock_unlock(&rwlock_tbl);
	printf("[releasewritelock, %d] release the read lock for locktbl: %d error: \n", numofcore, rc, strerror(rc));
#endif
}

int enqueuetasks(struct parameterwrapper *parameter, struct parameterwrapper *prevptr, struct ___Object___ *ptr, int * enterflags, int numenterflags) {
  void * taskpointerarray[MAXTASKPARAMS];
  int j;
  int numparams=parameter->task->numParameters;
  int numiterators=parameter->task->numTotal-1;
  int retval=1;
  int addnormal=1;
  int adderror=1;

  struct taskdescriptor * task=parameter->task;

	ObjectHashadd(parameter->objectset, (int) ptr, 0, (int) enterflags, numenterflags, enterflags==NULL);//this add the object to parameterwrapper
 
  /* Add enqueued object to parameter vector */
  taskpointerarray[parameter->slot]=ptr;

  /* Reset iterators */
  for(j=0;j<numiterators;j++) {
    toiReset(&parameter->iterators[j]);
  }

  /* Find initial state */
  for(j=0;j<numiterators;j++) {
  backtrackinit:
    if(toiHasNext(&parameter->iterators[j], taskpointerarray OPTARG(failed)))
      toiNext(&parameter->iterators[j], taskpointerarray OPTARG(failed));
    else if (j>0) {
      /* Need to backtrack */
      toiReset(&parameter->iterators[j]);
      j--;
      goto backtrackinit;
    } else {
      /* Nothing to enqueue */
      return retval;
    }
  }

  
  while(1) {
    /* Enqueue current state */
    int launch = 0;
    struct taskparamdescriptor *tpd=RUNMALLOC(sizeof(struct taskparamdescriptor));
    tpd->task=task;
    tpd->numParameters=numiterators+1;
    tpd->parameterArray=RUNMALLOC(sizeof(void *)*(numiterators+1));
    for(j=0;j<=numiterators;j++){
      tpd->parameterArray[j]=taskpointerarray[j];//store the actual parameters
    }
    /* Enqueue task */
    if ((!gencontains(failedtasks, tpd)&&!gencontains(activetasks,tpd))) {
      genputtable(activetasks, tpd, tpd);
    } else {
      RUNFREE(tpd->parameterArray);
      RUNFREE(tpd);
    }
    
    /* This loop iterates to the next parameter combination */
    if (numiterators==0)
      return retval;

    for(j=numiterators-1; j<numiterators;j++) {
    backtrackinc:
      if(toiHasNext(&parameter->iterators[j], taskpointerarray OPTARG(failed)))
	toiNext(&parameter->iterators[j], taskpointerarray OPTARG(failed));
      else if (j>0) {
	/* Need to backtrack */
	toiReset(&parameter->iterators[j]);
	j--;
	goto backtrackinc;
      } else {
	/* Nothing more to enqueue */
	return retval;
      }
    }
  }
  return retval;
}
 
/* Handler for signals. The signals catch null pointer errors and
   arithmatic errors. */

void myhandler(int sig, siginfo_t *info, void *uap) {
  sigset_t toclear;
#ifdef DEBUG
  printf("sig=%d\n",sig);
  printf("signal\n");
#endif
  sigemptyset(&toclear);
  sigaddset(&toclear, sig);
  sigprocmask(SIG_UNBLOCK, &toclear,NULL); 
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
	      if(intflagorand(objptr,1,0xFFFFFFFF)) { /* Set the first flag to 1 */
			 enqueueObject(objptr, NULL, 0);
		  }
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
      
      /* Check if this task has failed, allow a task that contains optional objects to fire */
      if (gencontains(failedtasks, currtpd)) {
	// Free up task parameter descriptor
	RUNFREE(currtpd->parameterArray);
	RUNFREE(currtpd);
	goto newtask;
      }
      int numparams=currtpd->task->numParameters;
      int numtotal=currtpd->task->numTotal;
      
	  int isolateflags[numparams];
      /* Make sure that the parameters are still in the queues */
      for(i=0;i<numparams;i++) {
	void * parameter=currtpd->parameterArray[i];
	struct ___Object___ * tmpparam = (struct ___Object___ *)parameter;
	if(0 == tmpparam->isolate) {
		isolateflags[i] = 0;
		// shared object, need to flush with current value
		//if(!getreadlock(tmpparam->original)) {
		//	// fail to get read lock of the original object, try this task later
		if(!getwritelock(tmpparam->original)) {
			// fail to get write lock, release all obtained locks and try this task later
			int j = 0;
			for(j = 0; j < i; ++j) {
				if(0 == isolateflags[j]) {
					releasewritelock(taskpointerarray[j]);
				}
			}
			genputtable(activetasks, currtpd, currtpd);
			goto newtask;
		}
		if(tmpparam->version != tmpparam->original->version) {
			// flush this object
			memcpy(tmpparam, tmpparam->original, classsize[tmpparam->type]);
			//releasereadlock(tmpparam->original);
			// fail to get write lock, release all obtained locks and try this task later
			int j = 0;
			for(j = 0; j < i; ++j) {
				if(0 == isolateflags[j]) {
					releasewritelock(((struct ___Object___ *)taskpointerarray[j+OFFSET])->original);
				}
			}
			releasewritelock(tmpparam->original);

			// some task on another core has changed this object
			// Free up task parameter descriptor
			RUNFREE(currtpd->parameterArray);
			RUNFREE(currtpd);
			// dequeue this object
#ifdef THREADSIMULATE
			int numofcore = pthread_getspecific(key);
			struct parameterwrapper ** queues = objectqueues[numofcore][tmpparam->type];
			int length = numqueues[numofcore][tmpparam->type];
#else
			struct parameterwrapper ** queues = objectqueues[corenum][tmpparam->type];
			int length = numqueues[corenum][tmpparam->type];
#endif
			for(j = 0; j < length; ++j) {
				struct parameterwrapper * pw = queues[j];
				if(ObjectHashcontainskey(pw->objectset, (int)tmpparam)) {
					int next;
					int UNUSED, UNUSED2;
					int * enterflags;
					ObjectHashget(pw->objectset, (int) tmpparam, (int *) &next, (int *) &enterflags, &UNUSED, &UNUSED2);
					ObjectHashremove(pw->objectset, (int)tmpparam);
					if (enterflags!=NULL)
						free(enterflags);
				}
			}
			// try to enqueue it again to check if it feeds other tasks;
			enqueueObject(tmpparam, NULL, 0);
			goto newtask;
		}
		//releasereadlock(tmpparam->original);
	} else {
		isolateflags[i] = 1;
	}
	struct parameterdescriptor * pd=currtpd->task->descriptorarray[i];
	struct parameterwrapper *pw=(struct parameterwrapper *) pd->queue;
	int j;
	/* Check that object is still in queue */
	{
	  if (!ObjectHashcontainskey(pw->objectset, (int) parameter)) {
	    RUNFREE(currtpd->parameterArray);
	    RUNFREE(currtpd);
	    goto newtask;
	  }
	}
      parameterpresent:
	;
	/* Check that object still has necessary tags */
	for(j=0;j<pd->numbertags;j++) {
	  int slotid=pd->tagarray[2*j]+numparams;
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

	 for(i = 0; i < numparams; ++i) {
		  if(0 == isolateflags[i]) {
			  struct ___Object___ * tmpparam = (struct ___Object___ *)taskpointerarray[i+OFFSET];
			  // shared object, need to replace this copy with original one
			  /*if(!getwritelock(tmpparam->original)) {
				  // fail to get write lock, release all obtained locks and try this task later
				  int j = 0;
				  for(j = 0; j < i; ++j) {
					  if(0 == isolateflags[j]) {
						  releasewritelock(taskpointerarray[j]);
					  }
				  }
				  genputtable(activetasks, tpd, tpd);
				  goto newtask;
			  }*/
			  if(tmpparam != tmpparam->original) {
				  taskpointerarray[i+OFFSET] = tmpparam->original;
			  }
		  }
	  }

      {
	/* Checkpoint the state */
	forward=allocateRuntimeHash(100);
	reverse=allocateRuntimeHash(100);
	//void ** checkpoint=makecheckpoint(currtpd->task->numParameters, currtpd->parameterArray, forward, reverse);
	int x;
	if (x=setjmp(error_handler)) {
	  int counter;
	  /* Recover */
	  
#ifdef DEBUG
	  printf("Fatal Error=%d, Recovering!\n",x);
#endif
	 /*
	  genputtable(failedtasks,currtpd,currtpd);
	  //restorecheckpoint(currtpd->task->numParameters, currtpd->parameterArray, checkpoint, forward, reverse);

	  freeRuntimeHash(forward);
	  freeRuntimeHash(reverse);
	  freemalloc();
	  forward=NULL;
	  reverse=NULL;
	  */
	  fflush(stdout);
	  exit(-1);
	} else {
	  /*if (injectfailures) {
	    if ((((double)random())/RAND_MAX)<failurechance) {
	      printf("\nINJECTING TASK FAILURE to %s\n", currtpd->task->name);
	      longjmp(error_handler,10);
	    }
	  }*/
	  /* Actually call task */
#ifdef PRECISE_GC
	  ((int *)taskpointerarray)[0]=currtpd->numParameters;
	  taskpointerarray[1]=NULL;
#endif
	  if(debugtask){
	    printf("ENTER %s count=%d\n",currtpd->task->name, (instaccum-instructioncount));
	    ((void (*) (void **)) currtpd->task->taskptr)(taskpointerarray);
	    printf("EXIT %s count=%d\n",currtpd->task->name, (instaccum-instructioncount));
	  } else
	    ((void (*) (void **)) currtpd->task->taskptr)(taskpointerarray);

	  for(i = 0; i < numparams; ++i) {
		  if(0 == isolateflags[i]) {
			  struct ___Object___ * tmpparam = (struct ___Object___ *)taskpointerarray[i+OFFSET];
			  releasewritelock(tmpparam);
		  }
	  }

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
  struct ObjectHash * objectset=((struct parameterwrapper *)pd->queue)->objectset;

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

    /* Next do objects w/ unbound tags*/

    for(i=0;i<numparams;i++) {
      if (statusarray[i]==0) {
	struct parameterdescriptor *pd=task->descriptorarray[i];
	if (pd->numbertags>0) {
	  processobject(parameter, i, pd, &iteratorcount, statusarray, numparams);
	  processtags(pd, i, parameter, &iteratorcount, statusarray, numparams);
	  goto loopstart;
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

 void printdebug() {
   int i;
   int j;
#ifdef THREADSIMULATE
   int numofcore = pthread_getspecific(key);
   for(i=0;i<numtasks[numofcore];i++) {
	   struct taskdescriptor * task=taskarray[numofcore][i];
#else
   for(i=0;i<numtasks[corenum];i++) {
     struct taskdescriptor * task=taskarray[corenum][i];
#endif
     printf("%s\n", task->name);
     for(j=0;j<task->numParameters;j++) {
       struct parameterdescriptor *param=task->descriptorarray[j];
       struct parameterwrapper *parameter=param->queue;
       struct ObjectHash * set=parameter->objectset;
       struct ObjectIterator objit;
       printf("  Parameter %d\n", j);
       ObjectHashiterator(set, &objit);
       while(ObjhasNext(&objit)) {
	 struct ___Object___ * obj=(struct ___Object___ *)Objkey(&objit);
	 struct ___Object___ * tagptr=obj->___tags___;
	 int nonfailed=Objdata4(&objit);
	 int numflags=Objdata3(&objit);
	 int flags=Objdata2(&objit);
	 Objnext(&objit);
	 printf("    Contains %lx\n", obj);
	 printf("      flag=%d\n", obj->flag); 
	 if (tagptr==NULL) {
	 } else if (tagptr->type==TAGTYPE) {
	   printf("      tag=%lx\n",tagptr);
	 } else {
	   int tagindex=0;
	   struct ArrayObject *ao=(struct ArrayObject *)tagptr;
	   for(;tagindex<ao->___cachedCode___;tagindex++) {
	     printf("      tag=%lx\n",ARRAYGET(ao, struct ___TagDescriptor___*, tagindex));
	   }
	 }
       }
     }
   }
 }
 

/* This function processes the task information to create queues for
   each parameter type. */

void processtasks() {
  int i;
#ifdef THREADSIMULATE
  int numofcore = pthread_getspecific(key);
  for(i=0;i<numtasks[numofcore];i++) {
	  struct taskdescriptor *task=taskarray[numofcore][i];
#else
  for(i=0;i<numtasks[corenum];i++) {
    struct taskdescriptor * task=taskarray[corenum][i];
#endif
    int j;

	/* Build objectsets */
	for(j=0;j<task->numParameters;j++) {
      struct parameterdescriptor *param=task->descriptorarray[j];
      struct parameterwrapper *parameter=param->queue;
	  parameter->objectset=allocateObjectHash(10);
	  parameter->task=task;
    }

    /* Build iterators for parameters */
    for(j=0;j<task->numParameters;j++) {
      struct parameterdescriptor *param=task->descriptorarray[j];
      struct parameterwrapper *parameter=param->queue;
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
    ObjectHashiterator(it->objectset, &it->it);
  }
}

int toiHasNext(struct tagobjectiterator *it, void ** objectarray OPTARG(int * failed)) {
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
      if (!ObjectHashcontainskey(it->objectset, (int) objptr))
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
	if (!ObjectHashcontainskey(it->objectset, (int) objptr))
	  continue;
	for(i=1;i<it->numtags;i++) {
	  struct ___TagDescriptor___ *tag2=objectarray[it->tagbindings[i]];
	  if (!containstag(objptr,tag2))
	    goto nexttag;
	}
	it->tagobjindex=tagindex;
	return 1;
      nexttag:
	;
      }
      it->tagobjindex=tagindex;
      return 0;
    }
  } else {
    return ObjhasNext(&it->it);
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

void toiNext(struct tagobjectiterator *it , void ** objectarray OPTARG(int * failed)) {
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
    objectarray[it->slot]=(void *)Objkey(&it->it);
    Objnext(&it->it);
  }
}
#endif
