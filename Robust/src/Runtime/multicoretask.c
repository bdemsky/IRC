#ifdef TASK
#include "runtime.h"
#ifndef RAW
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
#endif
#ifdef RAW
#include <raw.h>
#include <raw_compiler_defs.h>
//#include <libints.h>
#elif defined THREADSIMULATE
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

#ifdef RAW
#define TOTALCORE raw_get_num_tiles()
#endif

#ifdef CONSCHECK
#include "instrument.h"
#endif

struct genhashtable * activetasks;
struct genhashtable * failedtasks;
struct taskparamdescriptor * currtpd;
#ifndef RAW
struct RuntimeHash * forward;
struct RuntimeHash * reverse;
#endif

int corestatus[NUMCORES]; // records status of each core
                          // 1: running tasks
						  // 0: stall
int numsendobjs[NUMCORES]; // records how many objects a core has sent out
int numreceiveobjs[NUMCORES]; // records how many objects a core has received
#ifdef RAW
struct RuntimeHash locktable;
static struct RuntimeHash* locktbl = &locktable;
void * curr_heapbase=0;
void * curr_heaptop=0;
int self_numsendobjs;
int self_numreceiveobjs;
int lockobj;
int lockresult;
bool lockflag;
#ifndef INTERRUPT
bool reside;
#endif
struct Queue objqueue;
int msgdata[30];
int msgtype;
int msgdataindex;
int msglength;
void calCoords(int core_num, int* coordY, int* coordX);
void recvMsg(void);
#elif defined THREADSIMULATE
static struct RuntimeHash* locktbl;
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
static pthread_rwlock_t rwlock_tbl;
static pthread_rwlock_t rwlock_init;

void run(void * arg);
#endif

bool transStallMsg(int targetcore);
void transTerminateMsg(int targetcore);
int receiveObject();
bool getreadlock(void* ptr);
void releasereadlock(void* ptr);
#ifdef RAW
bool getreadlock_I(void* ptr);
void releasereadlock_I(void* ptr);
#endif
bool getwritelock(void* ptr);
void releasewritelock(void* ptr);

void flushAll(void);

void flushAll(void) {
#ifdef RAW
	int i = 0;
#ifdef INTERRUPT
	raw_user_interrupts_off();
#endif
	raw_test_pass(0xec00);
	for(i = 0; i < 512; ++i) {
		int base = i << 5;
		int off1 = 1 << 14;
		flushCacheline(base);
		flushCacheline(base|off1);
	}
#ifdef INTERRUPT
	raw_user_interrupts_on();
#endif
	raw_test_pass(0xec02);
#endif
}

#ifdef RAW
void recvMsg() {
	//raw_test_pass(0xefee);
	//raw_user_interrupts_off();
	//raw_test_pass(0xef00);
	receiveObject();
	//raw_test_pass(0xefff);
	//raw_user_interrupts_on();
	//raw_test_pass(0xefef);
}

void begin() {
#else
int main(int argc, char **argv) {
#endif
#ifdef RAW
	int i = 0;
	int argc = 1;
	char ** argv = NULL;
	bool sendStall = false;
	bool isfirst = true;
	bool tocontinue = false;
	struct QueueItem * objitem = NULL;
	struct transObjInfo * objInfo = NULL;
	int grount = 0;
	bool allStall = true;
	int sumsendobj = 0;

	raw_test_pass_reg(&locktable);
	raw_test_pass_reg(&msglength);
	raw_test_pass_reg(&i);
	raw_test_pass(0xee01);
	corenum = raw_get_abs_pos_x() + 4 * raw_get_abs_pos_y();

	// initialize the arrays
	if(STARTUPCORE == corenum) {
		// startup core to initialize corestatus[]
		for(i = 0; i < NUMCORES; ++i) {
			corestatus[i] = 1;
			numsendobjs[i] = 0; // assume all variables in RAW are local variables! MAY BE WRONG!!!
			numreceiveobjs[i] = 0;
		}
	}
	self_numsendobjs = 0;
	self_numreceiveobjs = 0;
	for(i = 0; i < 30; ++i) {
		msgdata[i] = -1;
	}
	//msgdata = NULL;
	msgtype = -1;
	msgdataindex = 0;
	msglength = 30;
	raw_test_pass(0xee02);
	
	// create the lock table, lockresult table and obj queue
	locktable.size = 20;
    locktable.bucket = (struct RuntimeNode **) RUNMALLOC_I(sizeof(struct RuntimeNode *)*20);
    /* Set allocation blocks*/
    locktable.listhead=NULL;
    locktable.listtail=NULL;
    /*Set data counts*/
    locktable.numelements = 0;
	lockobj = 0;
	lockresult = 0;
	lockflag = false;
#ifndef INTERRUPT
	reside = false;
#endif
	objqueue.head = NULL;
	objqueue.tail = NULL;
	raw_test_pass(0xee03);

#ifdef INTERRUPT
	if (corenum < NUMCORES) {
	// set up interrupts
	setup_ints();
	//setup_interrupts();
	//start_gdn_avail_ints(recvMsg);
	raw_user_interrupts_on();
	raw_test_pass(0xee04);
	}
#endif

#elif defined THREADSIMULATE
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

	for(i = 0; i < NUMCORES; ++i) {
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
	}
	
	//pthread_exit(NULL);
	while(true) {}
}

void run(void* arg) {
	struct thread_data * my_tdata = (struct thread_data *)arg;
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
#ifndef RAW
  processOptions();
#endif
  initializeexithandler();
#ifdef RAW
  raw_test_pass(0xee05);
#endif
  /* Create table for failed tasks */
#ifdef RAW
  if(corenum > NUMCORES - 1) {
	  failedtasks = NULL;
	  activetasks = NULL;
	  while(true) {
		  receiveObject();
	  }
  } else {
	  raw_test_pass(0xee06);
#endif
  /*failedtasks=genallocatehashtable((unsigned int (*)(void *)) &hashCodetpd, 
				   (int (*)(void *,void *)) &comparetpd);*/
	  failedtasks = NULL;
#ifdef RAW
  raw_test_pass(0xee07);
#endif
  /* Create queue of active tasks */
  activetasks=genallocatehashtable((unsigned int (*)(void *)) &hashCodetpd, 
				   (int (*)(void *,void *)) &comparetpd);
#ifdef RAW
  raw_test_pass(0xee08);
#endif
  
  /* Process task information */
  processtasks();
#ifdef RAW
  raw_test_pass(0xee09);
#endif

  /* Create startup object */
  createstartupobject(argc, argv);
#ifdef RAW
  raw_test_pass(0xee0a);
#endif

#ifdef RAW
  raw_test_pass(0xee0b);
	
  while(true) {
/*#ifndef INTERRUPT
	  while(receiveObject() != -1) {
	  }
#endif*/

	  // check if there are new active tasks can be executed
	  executetasks();

#ifndef INTERRUPT
	  while(receiveObject() != -1) {
	  }
#endif

	  raw_test_pass(0xee0c);

	  // check if there are some pending objects, if yes, enqueue them and executetasks again
	  tocontinue = false;
	  raw_test_pass(0xee0d);
	  while(!isEmpty(&objqueue)) {
		  void * obj = NULL;
#ifdef INTERRUPT
		  raw_user_interrupts_off();
#endif
		  raw_test_pass(0xeee1);
		  sendStall = false;
		  tocontinue = true;
		  objitem = getTail(&objqueue);
		  //obj = objitem->objectptr;
		  objInfo = (struct transObjInfo *)objitem->objectptr;
		  obj = objInfo->objptr;
		  raw_test_pass_reg((int)obj);
		  // grab lock and flush the obj
		  getreadlock_I(obj);
		  while(!lockflag) {
			  receiveObject();
		  }
		  grount = lockresult;
		  raw_test_pass_reg(grount);

		  lockresult = 0;
		  lockobj = 0;
		  lockflag = false;
#ifndef INTERRUPT
		  reside = false;
#endif

		  if(grount == 1) {
			  int k = 0;
			  // flush the obj
			  for(k = 0; k < classsize[((struct ___Object___ *)obj)->type]; ++k) {
				  invalidateAddr(obj + k);
			  }
			  // enqueue the object
			  for(k = 0; k < objInfo->length; ++k) {
				  int taskindex = objInfo->queues[2 * k];
				  int paramindex = objInfo->queues[2 * k + 1];
				  struct parameterwrapper ** queues = &(paramqueues[corenum][taskindex][paramindex]);
				  raw_test_pass_reg(taskindex);
				  raw_test_pass_reg(paramindex);
				  enqueueObject_I(obj, queues, 1);
			  }
			  removeItem(&objqueue, objitem);
			  releasereadlock_I(obj);
			  RUNFREE(objInfo->queues);
			  RUNFREE(objInfo);
			  /*enqueueObject_I(obj, NULL, 0);
			  removeItem(&objqueue, objitem);
			  releasereadlock_I(obj);*/
		  } else {
			  // can not get lock
			  // put it at the end of the queue
			  // and try to execute active tasks already enqueued first
			  removeItem(&objqueue, objitem);
			  addNewItem_I(&objqueue, objInfo);
			  break;
		  }
#ifdef INTERRUPT
		  raw_user_interrupts_on();
#endif
		  raw_test_pass(0xee0e);
	  }
	  raw_test_pass(0xee0f);
	  
	  if(!tocontinue) {
		  // check if stop
		  if(STARTUPCORE == corenum) {
			  if(isfirst) {
				  raw_test_pass(0xee10);
				  isfirst = false;
			  }
#ifdef INTERRUPT
			  raw_user_interrupts_off();
#endif
			  corestatus[corenum] = 0;
			  numsendobjs[corenum] = self_numsendobjs;
			  numreceiveobjs[corenum] = self_numreceiveobjs;
			  // check the status of all cores
			  allStall = true;
			  raw_test_pass_reg(NUMCORES);
			  for(i = 0; i < NUMCORES; ++i) {
				  raw_test_pass(0xe000 + corestatus[i]);
				  if(corestatus[i] != 0) {
					  allStall = false;
					  break;
				  }
			  }
			  if(allStall) {
				  // check if the sum of send objs and receive obj are the same
				  // yes->terminate
				  // no->go on executing
				  sumsendobj = 0;
				  for(i = 0; i < NUMCORES; ++i) {
					  sumsendobj += numsendobjs[i];
					  raw_test_pass(0xf000 + numsendobjs[i]);
				  }
				  for(i = 0; i < NUMCORES; ++i) {
					  sumsendobj -= numreceiveobjs[i];
					  raw_test_pass(0xf000 + numreceiveobjs[i]);
				  }
				  if(0 == sumsendobj) {
					  // terminate 
					  raw_test_pass(0xee11);
					  raw_test_done(1);	// All done.
				  }
			  }
#ifdef INTERRUPT
			  raw_user_interrupts_on();
#endif
		  } else {
			  if(!sendStall) {
				  raw_test_pass(0xee12);
				  if(isfirst) {
					  // wait for some time
					  int halt = 10000;
					  raw_test_pass(0xee13);
					  while(halt--){}
					  isfirst = false;
					  raw_test_pass(0xee14);
				  } else {
				  	// send StallMsg to startup core
					raw_test_pass(0xee15);
				  	sendStall = transStallMsg(STARTUPCORE);
					isfirst = true;
				  }
			  } else {
				  isfirst = true;
				  raw_test_pass(0xee16);
			  }
		  }
	  }
  }
  }
#elif defined THREADSIMULATE
  /* Start executing the tasks */
  executetasks();

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
								  
								  // release all locks
								  int rc_tbl = pthread_rwlock_wrlock(&rwlock_tbl);
								  printf("[run, %d] getting the write lock for locktbl: %d error: \n", numofcore, rc_tbl, strerror(rc_tbl));
								  struct RuntimeIterator* it_lock = RuntimeHashcreateiterator(locktbl); 
								  while(0 != RunhasNext(it_lock)) {
									  int key = Runkey(it_lock);
									  pthread_rwlock_t* rwlock_obj = (pthread_rwlock_t*)Runnext(it_lock);
									  int rc_des = pthread_rwlock_destroy(rwlock_obj);
									  printf("[run, %d] destroy the rwlock for object: %d error: \n", numofcore, key, strerror(rc_des));
									  RUNFREE(rwlock_obj);
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
#ifdef RAW
  flushAll();
#endif
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
	struct ArrayObject * ao=NULL;
  struct ___Object___ * tagptr=obj->___tags___;
#ifdef RAW
  raw_test_pass(0xebb0);
#endif
  if (tagptr==NULL) {
#ifdef RAW
	  raw_test_pass(0xebb1);
#endif
    obj->___tags___=(struct ___Object___ *)tagd;
  } else {
    /* Have to check if it is already set */
    if (tagptr->type==TAGTYPE) {
      struct ___TagDescriptor___ * td=(struct ___TagDescriptor___ *) tagptr;
#ifdef RAW
	  raw_test_pass(0xebb2);
#endif
      if (td==tagd) {
#ifdef RAW
		  raw_test_pass(0xebb3);
#endif
	return;
	  }
#ifdef PRECISE_GC
      int ptrarray[]={2, (int) ptr, (int) obj, (int)tagd};
      struct ArrayObject * ao=allocate_newarray(&ptrarray,TAGARRAYTYPE,TAGARRAYINTERVAL);
      obj=(struct ___Object___ *)ptrarray[2];
      tagd=(struct ___TagDescriptor___ *)ptrarray[3];
      td=(struct ___TagDescriptor___ *) obj->___tags___;
#else
#ifdef RAW
	  raw_test_pass(0xebb4);
#endif
      ao=allocate_newarray(TAGARRAYTYPE,TAGARRAYINTERVAL);
#endif
#ifdef RAW
	  raw_test_pass(0xebb5);
#endif
      ARRAYSET(ao, struct ___TagDescriptor___ *, 0, td);
      ARRAYSET(ao, struct ___TagDescriptor___ *, 1, tagd);
      obj->___tags___=(struct ___Object___ *) ao;
      ao->___cachedCode___=2;
#ifdef RAW
	  raw_test_pass(0xebb6);
#endif
    } else {
      /* Array Case */
      int i;
      struct ArrayObject *ao=(struct ArrayObject *) tagptr;
#ifdef RAW
	  raw_test_pass(0xebb7);
#endif
      for(i=0;i<ao->___cachedCode___;i++) {
	struct ___TagDescriptor___ * td=ARRAYGET(ao, struct ___TagDescriptor___*, i);
#ifdef RAW
		raw_test_pass(0xebb8);
#endif
	if (td==tagd) {
#ifdef RAW
		raw_test_pass(0xebb9);
#endif
	  return;
	}
      }
      if (ao->___cachedCode___<ao->___length___) {
#ifdef RAW
		raw_test_pass(0xebba);
#endif
	ARRAYSET(ao, struct ___TagDescriptor___ *, ao->___cachedCode___, tagd);
	ao->___cachedCode___++;
#ifdef RAW
	raw_test_pass(0xebbb);
#endif
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
#ifdef RAW
		raw_test_pass(0xebbc);
#endif
	aonew->___cachedCode___=ao->___length___+1;
	for(i=0;i<ao->___length___;i++) {
#ifdef RAW
		raw_test_pass(0xebbd);
#endif
	  ARRAYSET(aonew, struct ___TagDescriptor___*, i, ARRAYGET(ao, struct ___TagDescriptor___*, i));
	}
#ifdef RAW
		raw_test_pass(0xebbe);
#endif
	ARRAYSET(aonew, struct ___TagDescriptor___ *, ao->___length___, tagd);
#ifdef RAW
	raw_test_pass(0xebbf);
#endif
      }
    }
  }

  {
    struct ___Object___ * tagset=tagd->flagptr;
#ifdef RAW
	raw_test_pass(0xb008);
#endif
    if(tagset==NULL) {
#ifdef RAW
		raw_test_pass(0xb009);
#endif
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
#ifdef RAW
	  raw_test_pass(0xb00a);
#endif
    } else {
      struct ArrayObject *ao=(struct ArrayObject *) tagset;
      if (ao->___cachedCode___<ao->___length___) {
#ifdef RAW
		  raw_test_pass(0xb00b);
#endif
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
#ifdef RAW
	raw_test_pass(0xb00c);
#endif
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
#ifndef RAW
      printf("ERROR 1 in tagclear\n");
#endif
	;
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
#ifndef RAW
    printf("ERROR 2 in tagclear\n");
#endif
	;
  }
 PROCESSCLEAR:
  {
    struct ___Object___ *tagset=tagd->flagptr;
    if (tagset->type!=OBJECTARRAYTYPE) {
      if (tagset==obj)
	tagd->flagptr=NULL;
      else
#ifndef RAW
	printf("ERROR 3 in tagclear\n");
#endif
	  ;
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
#ifndef RAW
      printf("ERROR 4 in tagclear\n");
#endif
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
#ifdef RAW
	  raw_test_pass(0xaa000000 + oldflag);
	  raw_test_pass(0xaa000000 + flag);
#endif
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
#ifdef RAW
	  raw_test_pass(0xaa100000 + oldflag);
	  raw_test_pass(0xaa100000 + flag);
#endif
  flagbody(ptr,flag,NULL,0,true);
}

void flagbody(struct ___Object___ *ptr, int flag, struct parameterwrapper ** vqueues, int vlength, bool isnew) {
  struct parameterwrapper * flagptr = NULL;
  int i = 0;
  struct parameterwrapper ** queues = vqueues;
  int length = vlength;
  int next;
  int UNUSED, UNUSED2;
  int * enterflags = NULL;
  if((!isnew) && (queues == NULL)) {
#ifdef THREADSIMULATE
	  int numofcore = pthread_getspecific(key);
	  queues = objectqueues[numofcore][ptr->type];
	  length = numqueues[numofcore][ptr->type];
#else
#ifdef RAW
	  if(corenum < NUMCORES) {
#endif
	  queues = objectqueues[corenum][ptr->type];
	  length = numqueues[corenum][ptr->type];
#ifdef RAW
	  } else {
		  return;
	  }
#endif
#endif
  }
  ptr->flag=flag;
#ifdef RAW
  raw_test_pass(0xbb000000 + ptr->flag);
#endif
  
  /*Remove object from all queues */
  for(i = 0; i < length; ++i) {
	  flagptr = queues[i];
    ObjectHashget(flagptr->objectset, (int) ptr, (int *) &next, (int *) &enterflags, &UNUSED, &UNUSED2);
    ObjectHashremove(flagptr->objectset, (int)ptr);
    if (enterflags!=NULL)
      RUNFREE(enterflags);
  }
 }

 void enqueueObject(void * vptr, struct parameterwrapper ** vqueues, int vlength) {
   struct ___Object___ *ptr = (struct ___Object___ *)vptr;
  
  {
    struct QueueItem *tmpptr;
	struct parameterwrapper * parameter=NULL;
	int j;
	int i;
    struct parameterwrapper * prevptr=NULL;
    struct ___Object___ *tagptr=NULL;
	struct parameterwrapper ** queues = vqueues;
	int length = vlength;
#ifdef RAW
	if(corenum > NUMCORES - 1) {
		return;
	}
#endif
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
    tagptr=ptr->___tags___;
    
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
#ifdef RAW
		raw_test_pass(0xcc000000 + andmask);
		raw_test_pass_reg((int)ptr);
		raw_test_pass(0xcc000000 + ptr->flag);
		raw_test_pass(0xcc000000 + checkmask);
#endif
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

#ifdef RAW
void enqueueObject_I(void * vptr, struct parameterwrapper ** vqueues, int vlength) {
   struct ___Object___ *ptr = (struct ___Object___ *)vptr;
  
  {
    struct QueueItem *tmpptr;
	struct parameterwrapper * parameter=NULL;
	int j;
	int i;
    struct parameterwrapper * prevptr=NULL;
    struct ___Object___ *tagptr=NULL;
	struct parameterwrapper ** queues = vqueues;
	int length = vlength;
#ifdef RAW
	if(corenum > NUMCORES - 1) {
		return;
	}
#endif
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
#ifdef RAW
	raw_test_pass(0xeaa1);
	raw_test_pass_reg(queues);
	raw_test_pass_reg(length);
#endif
    tagptr=ptr->___tags___;
    
    /* Outer loop iterates through all parameter queues an object of
       this type could be in.  */
    for(j = 0; j < length; ++j) {
		parameter = queues[j];
      /* Check tags */
      if (parameter->numbertags>0) {
#ifdef RAW
	raw_test_pass(0xeaa2);
	raw_test_pass_reg(tagptr);
#endif
	if (tagptr==NULL)
	  goto nextloop;//that means the object has no tag but that param needs tag
	else if(tagptr->type==TAGTYPE) {//one tag
	  struct ___TagDescriptor___ * tag=(struct ___TagDescriptor___*) tagptr;
#ifdef RAW
	raw_test_pass(0xeaa3);
#endif
	  for(i=0;i<parameter->numbertags;i++) {
	    //slotid is parameter->tagarray[2*i];
	    int tagid=parameter->tagarray[2*i+1];
	    if (tagid!=tagptr->flag) {
#ifdef RAW
	raw_test_pass(0xeaa4);
#endif
	      goto nextloop; /*We don't have this tag */
		}
	   }
	} else {//multiple tags
	  struct ArrayObject * ao=(struct ArrayObject *) tagptr;
#ifdef RAW
	raw_test_pass(0xeaa5);
#endif
	  for(i=0;i<parameter->numbertags;i++) {
	    //slotid is parameter->tagarray[2*i];
	    int tagid=parameter->tagarray[2*i+1];
	    int j;
	    for(j=0;j<ao->___cachedCode___;j++) {
	      if (tagid==ARRAYGET(ao, struct ___TagDescriptor___*, j)->flag) {
		goto foundtag;
		  }
	    }
#ifdef RAW
	raw_test_pass(0xeaa6);
#endif
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
#ifdef RAW
	raw_test_pass(0xeaa7);
	raw_test_pass(0xcc000000 + andmask);
	raw_test_pass_reg(ptr);
	raw_test_pass(0xcc000000 + ptr->flag);
	raw_test_pass(0xcc000000 + checkmask);
#endif
	if ((ptr->flag&andmask)==checkmask) {
#ifdef RAW
	raw_test_pass(0xeaa8);
#endif
	  enqueuetasks_I(parameter, prevptr, ptr, NULL, 0);
	  prevptr=parameter;
	  break;
	}
      }
    nextloop:
	  ;
    }
  }
}

// helper function to compute the coordinates of a core from the core number
void calCoords(int core_num, int* coordY, int* coordX) {
	*coordX = core_num % 4;
	*coordY = core_num / 4;
}
#endif

/* Message format for RAW version:
 *      type + Msgbody 
 * type: 0 -- transfer object
 *       1 -- transfer stall msg
 *       2 -- lock request
 *       3 -- lock grount
 *       4 -- lock deny
 *       5 -- lock release
 *
 * ObjMsg: 0 + size of msg + obj's address + (task index + param index)+
 * StallMsg: 1 + corenum + sendobjs + receiveobjs (size is always 4 * sizeof(int))
 * LockMsg: 2 + lock type + obj pointer + request core (size is always 4 * sizeof(int))
 *          3/4/5 + lock type + obj pointer (size is always 3 * sizeof(int))
 *          lock type: 0 -- read; 1 -- write
 */

// transfer an object to targetcore
// format: object
void transferObject(struct transObjInfo * transObj) {
	void * obj = transObj->objptr;
	int type=((int *)obj)[0];
    int size=classsize[type];
	int targetcore = transObj->targetcore;
	//assert(type < NUMCLASSES); // can only transfer normal object

#ifdef RAW
	unsigned msgHdr;
	int self_y, self_x, target_y, target_x;
	//int isshared = 0;
	// for 32 bit machine, the size of fixed part is always 3 words
	//int msgsize = sizeof(int) * 2 + sizeof(void *);
	int msgsize = 3 + transObj->length * 2;
	int i = 0;

	struct ___Object___ * newobj = (struct ___Object___ *)obj;
	/*if(0 == newobj->isolate) {
		isshared = 1;
	}*/

	calCoords(corenum, &self_y, &self_x);
	calCoords(targetcore, &target_y, &target_x);
	// Build the message header
    msgHdr = construct_dyn_hdr(0, msgsize, 0,		// msgsize word sent.
			                   self_y, self_x,
							   target_y, target_x);
	gdn_send(msgHdr);		// Send the message header to EAST to handle fab(n - 1).
	raw_test_pass(0xbbbb);
	raw_test_pass(0xb000 + targetcore); // targetcore
    gdn_send(0);
    raw_test_pass(0);
	gdn_send(msgsize);
	raw_test_pass_reg(msgsize);
    gdn_send(obj);
	raw_test_pass_reg(obj);
	//gdn_send(isshared);
	//raw_test_pass_reg(isshared);
	for(i = 0; i < transObj->length; ++i) {
		int taskindex = transObj->queues[2*i];
		int paramindex = transObj->queues[2*i+1];
		gdn_send(taskindex);
		raw_test_pass_reg(taskindex);
		gdn_send(paramindex);
		raw_test_pass_reg(paramindex);
	}
	raw_test_pass(0xffff);
	++(self_numsendobjs);
#elif defined THREADSIMULATE
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
	/*struct ___Object___ * newobj = (struct ___Object___ *)obj;
	if(0 == newobj->isolate) {
		newobj = RUNMALLOC(size);
		memcpy(newobj, obj, size);
		newobj->original=obj;
	}*/
	struct transObjInfo * tmptransObj = RUNMALLOC(sizeof(struct transObjInfo));
	memcpy(tmptransObj, transObj, sizeof(struct transObjInfo));
	int * tmpqueue = RUNMALLOC(sizeof(int)*2*tmptransObj->length);
	memcpy(tmpqueue, tmptransObj->queues, sizeof(int)*2*tmptransObj->length);
	tmptransObj->queues = tmpqueue;
	struct ___Object___ * newobj = RUNMALLOC(sizeof(struct ___Object___));
	newobj->type = ((struct ___Object___ *)obj)->type;
	newobj->original = (struct ___Object___ *)tmptransObj;
	int ret;
	do {
		ret=mq_send(mqdnum, (void *)newobj, sizeof(struct ___Object___), 0); // send the object into the queue
		if(ret != 0) {
			printf("[transferObject, %d] mq_send to %s returned: %d, error: %s\n", numofcore, path, ret, strerror(errno));
		}
	}while(ret!=0);
	RUNFREE(newobj);
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
#ifdef RAW
	unsigned msgHdr;
	int self_y, self_x, target_y, target_x;
	// for 32 bit machine, the size is always 4 words
	//int msgsize = sizeof(int) * 4;
	int msgsize = 4;

	calCoords(corenum, &self_y, &self_x);
	calCoords(targetcore, &target_y, &target_x);
	// Build the message header
    msgHdr = construct_dyn_hdr(0, msgsize, 0,		// msgsize word sent.
			                   self_y, self_x,
							   target_y, target_x);
	gdn_send(msgHdr);		// Send the message header to EAST to handle fab(n - 1).
	raw_test_pass(0xbbbb);
	raw_test_pass(0xb000 + targetcore); // targetcore
    gdn_send(1);
    raw_test_pass(1);
    gdn_send(corenum);
	raw_test_pass_reg(corenum);
	gdn_send(self_numsendobjs);
    raw_test_pass_reg(self_numsendobjs);
    gdn_send(self_numreceiveobjs);
	raw_test_pass_reg(self_numreceiveobjs);
	raw_test_pass(0xffff);
	return true;
#elif defined THREADSIMULATE
	struct ___Object___ *newobj = RUNMALLOC(sizeof(struct ___Object___));
	// use the first four int field to hold msgtype/corenum/sendobj/receiveobj
	newobj->type = -1;
	int numofcore = pthread_getspecific(key);
	newobj->flag = numofcore;
	newobj->___cachedHash___ = thread_data_array[numofcore].numsendobjs;
	newobj->___cachedCode___ = thread_data_array[numofcore].numreceiveobjs;

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
	ret=mq_send(mqdnum, (void *)newobj, sizeof(struct ___Object___), 0); // send the object into the queue
	if(ret != 0) {
		printf("[transStallMsg, %d] mq_send to %s returned: %d, error: %s\n", numofcore, path, ret, strerror(errno));
		RUNFREE(newobj);
		return false;
	} else {
		printf("[transStallMsg, %d] mq_send to %s returned: $%x\n", numofcore, path, ret);
		printf("<transStallMsg> to %s index: %d, sendobjs: %d, receiveobjs: %d\n", path, newobj->flag, newobj->___cachedHash___, newobj->___cachedCode___);
		RUNFREE(newobj);
		return true;
	}
#endif
}

// receive object transferred from other cores
// or the terminate message from other cores
// NOTICE: following format is for threadsimulate version only
//         RAW version please see previous description
// format: type + object
// type: -1--stall msg
//      !-1--object
// return value: 0--received an object
//               1--received nothing
//               2--received a Stall Msg
//               3--received a lock Msg
//               RAW version: -1 -- received nothing
//                            otherwise -- received msg type
int receiveObject() {
#ifdef RAW
	bool deny = false;
	unsigned msgHdr;
	int self_y, self_x, target_y, target_x;
	int targetcore = 0;
	if(gdn_input_avail() == 0) {
		if(corenum < NUMCORES) {
			raw_test_pass(0xd001);
		}
		return -1;
	}
msg:
	raw_test_pass(0xcccc);
	while((gdn_input_avail() != 0) && (msgdataindex < msglength)) {
		msgdata[msgdataindex] = gdn_receive();
		if(msgdataindex == 0) {
			if(msgdata[0] > 2) {
				msglength = 3;
			} else if(msgdata[0] > 0) {
				msglength = 4;
			}
		} else if((msgdataindex == 1) && (msgdata[0] == 0)) {
			msglength = msgdata[msgdataindex];
		}
		raw_test_pass_reg(msgdata[msgdataindex]);
		msgdataindex++;

		/*if(msgdataindex == 0) {
			// type
			msgtype = gdn_receive();
			if(msgtype > 2) {
				msglength = 3;
			} else {
				msglength = 4;
			}
			if(msgtype != 0) {
				msgdata = (int *)RUNMALLOC_I(msglength * sizeof(int));
				msgdata[msgdataindex] = msgtype;
			}
			raw_test_pass_reg(msgtype);
		} else if((msgdataindex == 1) && (msgtype == 0)) {
			// object transfer msg
			msglength = gdn_receive();
			msgdata = (int *)RUNMALLOC_I(msglength * sizeof(int));
			msgdata[0] = msgtype;
			msgdata[msgdataindex] = msglength;
			raw_test_pass_reg(msgdata[msgdataindex]);
		} else {
			msgdata[msgdataindex] = gdn_receive();
			raw_test_pass_reg(msgdata[msgdataindex]);
		}
		msgdataindex++;*/
	}
	raw_test_pass(0xffff);
	if(msgdataindex == msglength) {
		// received a whole msg
		int type, data1, data2; // will receive at least 3 words including type
		type = msgdata[0];
		data1 = msgdata[1];
		data2 = msgdata[2];
		switch(type) {
			case 0: {
						// receive a object transfer msg
						struct transObjInfo * transObj = RUNMALLOC_I(sizeof(struct transObjInfo));
						int k = 0;
						if(corenum > NUMCORES - 1) {
							raw_test_done(0xa00a);
						}
						// store the object and its corresponding queue info, enqueue it later
						transObj->objptr = (void *)data2; // data1 is now size of the msg
						transObj->length = (msglength - 3) / 2;
						transObj->queues = RUNMALLOC_I(sizeof(int)*(msglength - 3));
						for(k = 0; k < transObj->length; ++k) {
							transObj->queues[2*k] = msgdata[3+2*k];
							raw_test_pass_reg(transObj->queues[2*k]);
							transObj->queues[2*k+1] = msgdata[3+2*k+1];
							raw_test_pass_reg(transObj->queues[2*k+1]);
						}
						//memcpy(transObj->queues, msgdata[3], sizeof(int)*(msglength - 3));
						addNewItem_I(&objqueue, (void *)transObj);
						++(self_numreceiveobjs);
						raw_test_pass(0xe881);
						/*
						addNewItem_I(&objqueue, (void *)data2);
						++(self_numreceiveobjs);
						raw_test_pass(0xe881);*/
						break;
					} 
			case 1: {
						// receive a stall msg
						if(corenum != STARTUPCORE) {
							// non startup core can not receive stall msg
							// return -1
							raw_test_done(0xa001);
						}
						if(data1 < NUMCORES) {
							raw_test_pass(0xe882);
							corestatus[data1] = 0;
							numsendobjs[data1] = data2;
							numreceiveobjs[data1] = msgdata[3];
						}
						break;
					} 
			case 2: {
						// receive lock request msg
						// for 32 bit machine, the size is always 3 words			
						//int msgsize = sizeof(int) * 3;
						int msgsize = 3;
						// lock request msg, handle it right now
						// check to see if there is a lock exist in locktbl for the required obj
						int data3 = msgdata[3];
						deny = false;
						if(!RuntimeHashcontainskey(locktbl, data2)) {
							// no locks for this object
							// first time to operate on this shared object
							// create a lock for it
							// the lock is an integer: 0 -- stall, >0 -- read lock, -1 -- write lock
							raw_test_pass(0xe883);
							if(data1 == 0) {
								RuntimeHashadd_I(locktbl, data2, 1);
							} else {
								RuntimeHashadd_I(locktbl, data2, -1);
							}
						} else {
							int rwlock_obj = 0;
							raw_test_pass(0xe884);
							RuntimeHashget(locktbl, data2, &rwlock_obj);
							raw_test_pass_reg(rwlock_obj);
							if(0 == rwlock_obj) {
								if(data1 == 0) {
									rwlock_obj = 1;
								} else {
									rwlock_obj = -1;
								}
								RuntimeHashremovekey(locktbl, data2);
								RuntimeHashadd_I(locktbl, data2, rwlock_obj);
							} else if((rwlock_obj > 0) && (data1 == 0)) {
								// read lock request and there are only read locks
								rwlock_obj++;
								RuntimeHashremovekey(locktbl, data2);
								RuntimeHashadd_I(locktbl, data2, rwlock_obj);
							} else {
								deny = true;
							}	
							raw_test_pass_reg(rwlock_obj);
						}
						targetcore = data3;
						calCoords(corenum, &self_y, &self_x);
						calCoords(targetcore, &target_y, &target_x);
						// Build the message header
						msgHdr = construct_dyn_hdr(0, msgsize, 0,		// msgsize word sent.
								                   self_y, self_x,
												   target_y, target_x);
						gdn_send(msgHdr);		// Send the message header to EAST to handle fab(n - 1).
						raw_test_pass(0xbbbb);
						raw_test_pass(0xb000 + targetcore); // targetcore
						if(deny == true) {
							// deny the lock request
							gdn_send(4); // lock request
							raw_test_pass(4);
						} else {
							// grount the lock request		
							gdn_send(3); // lock request
							raw_test_pass(3);
						}
						gdn_send(data1); // lock type
						raw_test_pass_reg(data1);
						gdn_send(data2); // lock target
						raw_test_pass_reg(data2);
						raw_test_pass(0xffff);
						break;
					}
			case 3: {
						// receive lock grount msg
						if(corenum > NUMCORES - 1) {
							raw_test_done(0xa00b);
						}	
						if(lockobj == data2) {
							lockresult = 1;
							lockflag = true;
#ifndef INTERRUPT
							reside = false;
#endif
						} else {
							// conflicts on lockresults
						raw_test_done(0xa002);
						}
						break;
					}
			case 4: {
						// receive lock grount/deny msg
						if(corenum > NUMCORES - 1) {
							raw_test_done(0xa00c);
						}
						if(lockobj == data2) {
							lockresult = 0;
							lockflag = true;
#ifndef INTERRUPT
							reside = false;
#endif
						} else {
							// conflicts on lockresults
							raw_test_done(0xa003);
						}	
						break;
					}
			case 5: {
						// receive lock release msg
						if(!RuntimeHashcontainskey(locktbl, data2)) {
							// no locks for this object, something is wrong
							raw_test_done(0xa004);
						} else {
							int rwlock_obj = 0;
							RuntimeHashget(locktbl, data2, &rwlock_obj);
							raw_test_pass(0xe885);
							raw_test_pass_reg(rwlock_obj);
							if(data1 == 0) {
								rwlock_obj--;
							} else {
								rwlock_obj++;
							}
							RuntimeHashremovekey(locktbl, data2);
							RuntimeHashadd_I(locktbl, data2, rwlock_obj);
							raw_test_pass_reg(rwlock_obj);
						}
						break;
					}
			default:
					break;
		}
		//RUNFREE(msgdata);
		//msgdata = NULL;
		for(msgdataindex--;msgdataindex > 0; --msgdataindex) {
			msgdata[msgdataindex] = -1;
		}
		msgtype = -1;
		//msgdataindex = 0;
		msglength = 30;
		raw_test_pass(0xe886);
		if(gdn_input_avail() != 0) {
			goto msg;
		}
		return type;
	} else {
		// not a whole msg
		raw_test_pass(0xe887);
		return -2;
	}
#elif defined THREADSIMULATE
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
		struct ___Object___ * tmpptr = (struct ___Object___ *)msgptr;
		struct transObjInfo * transObj = (struct transObjInfo *)tmpptr->original;
		tmpptr = (struct ___Object___ *)(transObj->objptr);
		int type = tmpptr->type;
		int size=classsize[type];
		struct ___Object___ * newobj=RUNMALLOC(size);
		memcpy(newobj, tmpptr, size);
		if(0 == newobj->isolate) {
			newobj->original=tmpptr;
		}
		RUNFREE(msgptr);
		tmpptr = NULL;
		int k = 0;
		for(k = 0; k < transObj->length; ++k) {
			int taskindex = transObj->queues[2 * k];
			int paramindex = transObj->queues[2 * k + 1];
			struct parameterwrapper ** queues = &(paramqueues[numofcore][taskindex][paramindex]);
			enqueueObject(newobj, queues, 1);
		}
		RUNFREE(transObj->queues);
		RUNFREE(transObj);
		return 0;
	}
#endif
}

bool getreadlock(void * ptr) {
#ifdef RAW
	unsigned msgHdr;
	int self_y, self_x, target_y, target_x;
	int targetcore = ((int)ptr) % TOTALCORE;
	// for 32 bit machine, the size is always 4 words
	//int msgsize = sizeof(int) * 4;
	int msgsize = 4;

	lockobj = (int)ptr;
	lockflag = false;
#ifndef INTERRUPT
	reside = false;
#endif
	lockresult = 0;

	if(targetcore == corenum) {
		// reside on this core
		bool deny = false;
#ifdef INTERRUPT
		raw_user_interrupts_off();
#endif
		if(!RuntimeHashcontainskey(locktbl, (int)ptr)) {
			// no locks for this object
			// first time to operate on this shared object
			// create a lock for it
			// the lock is an integer: 0 -- stall, >0 -- read lock, -1 -- write lock
			RuntimeHashadd_I(locktbl, (int)ptr, 1);
		} else {
			int rwlock_obj = 0;
			RuntimeHashget(locktbl, (int)ptr, &rwlock_obj);
			if(-1 != rwlock_obj) {
				rwlock_obj++;
				RuntimeHashremovekey(locktbl, (int)ptr);
				RuntimeHashadd_I(locktbl, (int)ptr, rwlock_obj);
			} else {
				deny = true;
			}
		}
#ifdef INTERRUPT
		raw_user_interrupts_on();
#endif
		if(lockobj == (int)ptr) {
			if(deny) {
				lockresult = 0;
			} else {
				lockresult = 1;
			}
			lockflag = true;
#ifndef INTERRUPT
			reside = true;
#endif
		} else {
			// conflicts on lockresults
			raw_test_done(0xa005);
		}
		return true;
	}

	calCoords(corenum, &self_y, &self_x);
	calCoords(targetcore, &target_y, &target_x);
	// Build the message header
    msgHdr = construct_dyn_hdr(0, msgsize, 0,		// msgsize word sent.
			                   self_y, self_x,
							   target_y, target_x);
	gdn_send(msgHdr);		// Send the message header to EAST to handle fab(n - 1).
	raw_test_pass(0xbbbb);
	raw_test_pass(0xb000 + targetcore); // targetcore
    gdn_send(2); // lock request
    raw_test_pass(2);
	gdn_send(0); // read lock
    raw_test_pass(0);
    gdn_send(ptr);
	raw_test_pass_reg(ptr);
	gdn_send(corenum);
	raw_test_pass_reg(corenum);
	raw_test_pass(0xffff);
	return true;
#elif defined THREADSIMULATE
	int numofcore = pthread_getspecific(key);

	int rc = pthread_rwlock_tryrdlock(&rwlock_tbl);
	printf("[getreadlock, %d] getting the read lock for locktbl: %d error: \n", numofcore, rc, strerror(rc));
	if(0 != rc) {
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
		if(0 != rc) {
			RUNFREE(rwlock);
			return false;
		} else {
			if(!RuntimeHashcontainskey(locktbl, (int)ptr)) {
				// check again
				RuntimeHashadd(locktbl, (int)ptr, (int)rwlock);
			} else {
				RUNFREE(rwlock);
				RuntimeHashget(locktbl, (int)ptr, (int*)&rwlock);
			}
			rc = pthread_rwlock_unlock(&rwlock_tbl);
				printf("[getreadlock, %d] release the write lock for locktbl: %d error: \n", numofcore, rc, strerror(rc));
		}
		rc = pthread_rwlock_tryrdlock(rwlock);
		printf("[getreadlock, %d] getting read lock for object %d: %d error: \n", numofcore, (int)ptr, rc, strerror(rc));	
		if(0 != rc) {
			return false;
		} else {
			return true;
		}
	} else {
		pthread_rwlock_t* rwlock_obj = NULL;
		RuntimeHashget(locktbl, (int)ptr, (int*)&rwlock_obj);
		rc = pthread_rwlock_unlock(&rwlock_tbl);
		printf("[getreadlock, %d] release the read lock for locktbl: %d error: \n", numofcore, rc, strerror(rc));
		int rc_obj = pthread_rwlock_tryrdlock(rwlock_obj);
		printf("[getreadlock, %d] getting read lock for object %d: %d error: \n", numofcore, (int)ptr, rc_obj, strerror(rc_obj));
		if(0 != rc_obj) {
			return false;
		} else {
			return true;
		}
	}
#endif
}

void releasereadlock(void * ptr) {
#ifdef RAW
	unsigned msgHdr;
	int self_y, self_x, target_y, target_x;
	int targetcore = ((int)ptr) % TOTALCORE;
	// for 32 bit machine, the size is always 3 words
	//int msgsize = sizeof(int) * 3;
	int msgsize = 3;

	if(targetcore == corenum) {
#ifdef INTERRUPT
		raw_user_interrupts_off();
#endif
		// reside on this core
		if(!RuntimeHashcontainskey(locktbl, (int)ptr)) {
			// no locks for this object, something is wrong
			raw_test_done(0xa006);
		} else {
			int rwlock_obj = 0;
			RuntimeHashget(locktbl, (int)ptr, &rwlock_obj);
			rwlock_obj--;
			RuntimeHashremovekey(locktbl, (int)ptr);
			RuntimeHashadd_I(locktbl, (int)ptr, rwlock_obj);
		}
#ifdef INTERRUPT
		raw_user_interrupts_on();
#endif
		return;
	}

	calCoords(corenum, &self_y, &self_x);
	calCoords(targetcore, &target_y, &target_x);
	// Build the message header
    msgHdr = construct_dyn_hdr(0, msgsize, 0,		// msgsize word sent.
			                   self_y, self_x,
							   target_y, target_x);
	gdn_send(msgHdr);		// Send the message header to EAST to handle fab(n - 1).
	raw_test_pass(0xbbbb);
	raw_test_pass(0xb000 + targetcore); // targetcore
    gdn_send(5); // lock release
    raw_test_pass(5);
	gdn_send(0); // read lock
    raw_test_pass(0);
    gdn_send(ptr);
	raw_test_pass_reg(ptr);
	raw_test_pass(0xffff);
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

#ifdef RAW
bool getreadlock_I(void * ptr) {
	unsigned msgHdr;
	int self_y, self_x, target_y, target_x;
	int targetcore = ((int)ptr) % TOTALCORE;
	// for 32 bit machine, the size is always 4 words
	//int msgsize = sizeof(int) * 4;
	int msgsize = 4;

	lockobj = (int)ptr;
	lockflag = false;
#ifndef INTERRUPT
	reside = false;
#endif
	lockresult = 0;

	if(targetcore == corenum) {
		// reside on this core
		bool deny = false;
		if(!RuntimeHashcontainskey(locktbl, (int)ptr)) {
			// no locks for this object
			// first time to operate on this shared object
			// create a lock for it
			// the lock is an integer: 0 -- stall, >0 -- read lock, -1 -- write lock
			RuntimeHashadd_I(locktbl, (int)ptr, 1);
		} else {
			int rwlock_obj = 0;
			RuntimeHashget(locktbl, (int)ptr, &rwlock_obj);
			if(-1 != rwlock_obj) {
				rwlock_obj++;
				RuntimeHashremovekey(locktbl, (int)ptr);
				RuntimeHashadd_I(locktbl, (int)ptr, rwlock_obj);
			} else {
				deny = true;
			}
		}
		if(lockobj == (int)ptr) {
			if(deny) {
				lockresult = 0;
			} else {
				lockresult = 1;
			}
			lockflag = true;
#ifndef INTERRUPT
			reside = true;
#endif
		} else {
			// conflicts on lockresults
			raw_test_done(0xa005);
		}
		return true;
	}

	calCoords(corenum, &self_y, &self_x);
	calCoords(targetcore, &target_y, &target_x);
	// Build the message header
    msgHdr = construct_dyn_hdr(0, msgsize, 0,		// msgsize word sent.
			                   self_y, self_x,
							   target_y, target_x);
	gdn_send(msgHdr);		// Send the message header to EAST to handle fab(n - 1).
	raw_test_pass(0xbbbb);
	raw_test_pass(0xb000 + targetcore); // targetcore
    gdn_send(2); // lock request
    raw_test_pass(2);
	gdn_send(0); // read lock
    raw_test_pass(0);
    gdn_send(ptr);
	raw_test_pass_reg(ptr);
	gdn_send(corenum);
	raw_test_pass_reg(corenum);
	raw_test_pass(0xffff);
	return true;
}

void releasereadlock_I(void * ptr) {
	unsigned msgHdr;
	int self_y, self_x, target_y, target_x;
	int targetcore = ((int)ptr) % TOTALCORE;
	// for 32 bit machine, the size is always 3 words
	//int msgsize = sizeof(int) * 3;
	int msgsize = 3;

	if(targetcore == corenum) {
		// reside on this core
		if(!RuntimeHashcontainskey(locktbl, (int)ptr)) {
			// no locks for this object, something is wrong
			raw_test_done(0xa006);
		} else {
			int rwlock_obj = 0;
			RuntimeHashget(locktbl, (int)ptr, &rwlock_obj);
			rwlock_obj--;
			RuntimeHashremovekey(locktbl, (int)ptr);
			RuntimeHashadd_I(locktbl, (int)ptr, rwlock_obj);
		}
		return;
	}

	calCoords(corenum, &self_y, &self_x);
	calCoords(targetcore, &target_y, &target_x);
	// Build the message header
    msgHdr = construct_dyn_hdr(0, msgsize, 0,		// msgsize word sent.
			                   self_y, self_x,
							   target_y, target_x);
	gdn_send(msgHdr);		// Send the message header to EAST to handle fab(n - 1).
	raw_test_pass(0xbbbb);
	raw_test_pass(0xb000 + targetcore); // targetcore
    gdn_send(5); // lock release
    raw_test_pass(5);
	gdn_send(0); // read lock
    raw_test_pass(0);
    gdn_send(ptr);
	raw_test_pass_reg(ptr);
	raw_test_pass(0xffff);
}
#endif

bool getwritelock(void * ptr) {
#ifdef RAW
	unsigned msgHdr;
	int self_y, self_x, target_y, target_x;
	int targetcore = ((int)ptr) % TOTALCORE;
	// for 32 bit machine, the size is always 4 words
	//int msgsize = sizeof(int) * 4;
	int msgsize= 4;

	lockobj = (int)ptr;
	lockflag = false;
#ifndef INTERRUPT
	reside = false;
#endif
	lockresult = 0;

	if(targetcore == corenum) {
		// reside on this core
		bool deny = false;
#ifdef INTERRUPT
		raw_user_interrupts_off();
#endif
		if(!RuntimeHashcontainskey(locktbl, (int)ptr)) {
			// no locks for this object
			// first time to operate on this shared object
			// create a lock for it
			// the lock is an integer: 0 -- stall, >0 -- read lock, -1 -- write lock
			raw_test_pass(0xe552);
			RuntimeHashadd_I(locktbl, (int)ptr, -1);
		} else {
			int rwlock_obj = 0;
			RuntimeHashget(locktbl, (int)ptr, &rwlock_obj);
			raw_test_pass(0xe553);
			raw_test_pass_reg(rwlock_obj);
			if(0 == rwlock_obj) {
				rwlock_obj = -1;
				RuntimeHashremovekey(locktbl, (int)ptr);
				RuntimeHashadd_I(locktbl, (int)ptr, rwlock_obj);
			} else {
				deny = true;
			}
		}
#ifdef INTERRUPT
		raw_user_interrupts_on();
#endif
		raw_test_pass(0xe554);
		raw_test_pass_reg(lockresult);
		if(lockobj == (int)ptr) {
			if(deny) {
				lockresult = 0;
				raw_test_pass(0);
			} else {
				lockresult = 1;
				raw_test_pass(1);
			}
			lockflag = true;
#ifndef INTERRUPT
			reside = true;
#endif
		} else {
			// conflicts on lockresults
			raw_test_done(0xa007);
		}
		return true;
	}

	raw_test_pass(0xe555);
	calCoords(corenum, &self_y, &self_x);
	calCoords(targetcore, &target_y, &target_x);
	// Build the message header
    msgHdr = construct_dyn_hdr(0, msgsize, 0,		// msgsize word sent.
			                   self_y, self_x,
							   target_y, target_x);
	gdn_send(msgHdr);		// Send the message header to EAST to handle fab(n - 1).
	raw_test_pass(0xbbbb);
	raw_test_pass(0xb000 + targetcore); // targetcore
    gdn_send(2); // lock request
    raw_test_pass(2);
	gdn_send(1); // write lock
    raw_test_pass(1);
    gdn_send(ptr);
	raw_test_pass_reg(ptr);
	gdn_send(corenum);
	raw_test_pass_reg(corenum);
	raw_test_pass(0xffff);
	return true;
#elif defined THREADSIMULATE
	int numofcore = pthread_getspecific(key);

	int rc = pthread_rwlock_tryrdlock(&rwlock_tbl);
	printf("[getwritelock, %d] getting the read lock for locktbl: %d error: \n", numofcore, rc, strerror(rc));
	if(0 != rc) {
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
		if(0 != rc) {
			pthread_rwlock_destroy(rwlock);
			RUNFREE(rwlock);
			return false;
		} else {
			if(!RuntimeHashcontainskey(locktbl, (int)ptr)) {
				// check again
				RuntimeHashadd(locktbl, (int)ptr, (int)rwlock);
			} else {
				pthread_rwlock_destroy(rwlock);
				RUNFREE(rwlock);
				RuntimeHashget(locktbl, (int)ptr, (int*)&rwlock);
			}
			rc = pthread_rwlock_unlock(&rwlock_tbl);
			printf("[getwritelock, %d] release the write lock for locktbl: %d error: \n", numofcore, rc, strerror(rc));
		}
		rc = pthread_rwlock_trywrlock(rwlock);
		printf("[getwritelock, %d] getting write lock for object %d: %d error: \n", numofcore, (int)ptr, rc, strerror(rc));	
		if(0 != rc) {
			return false;
		} else {
			return true;
		}
	} else {
		pthread_rwlock_t* rwlock_obj = NULL;
		RuntimeHashget(locktbl, (int)ptr, (int*)&rwlock_obj);
		rc = pthread_rwlock_unlock(&rwlock_tbl);
		printf("[getwritelock, %d] release the read lock for locktbl: %d error: \n", numofcore, rc, strerror(rc));
		int rc_obj = pthread_rwlock_trywrlock(rwlock_obj);
		printf("[getwritelock, %d] getting write lock for object %d: %d error: \n", numofcore, (int)ptr, rc_obj, strerror(rc_obj));
		if(0 != rc_obj) {
			return false;
		} else {
			return true;
		}
	}

#endif
}

void releasewritelock(void * ptr) {
#ifdef RAW
	unsigned msgHdr;
	int self_y, self_x, target_y, target_x;
	int targetcore = ((int)ptr) % TOTALCORE;
	// for 32 bit machine, the size is always 3 words
	//int msgsize = sizeof(int) * 3;
	int msgsize = 3;

	if(targetcore == corenum) {
#ifdef INTERRUPT
		raw_user_interrupts_off();
#endif
		// reside on this core
		if(!RuntimeHashcontainskey(locktbl, (int)ptr)) {
			// no locks for this object, something is wrong
			raw_test_done(0xa008);
		} else {
			int rwlock_obj = 0;
			raw_test_pass(0xe662);
			RuntimeHashget(locktbl, (int)ptr, &rwlock_obj);
			raw_test_pass_reg(rwlock_obj);
			rwlock_obj++;
			RuntimeHashremovekey(locktbl, (int)ptr);
			RuntimeHashadd_I(locktbl, (int)ptr, rwlock_obj);
			raw_test_pass_reg(rwlock_obj);
		}
#ifdef INTERRUPT
		raw_user_interrupts_on();
#endif
		return;
	}

	raw_test_pass(0xe663);
	calCoords(corenum, &self_y, &self_x);
	calCoords(targetcore, &target_y, &target_x);
	// Build the message header
    msgHdr = construct_dyn_hdr(0, msgsize, 0,		// msgsize word sent.
			                   self_y, self_x,
							   target_y, target_x);
	gdn_send(msgHdr);		// Send the message header to EAST to handle fab(n - 1).
	raw_test_pass(0xbbbb);
	raw_test_pass(0xb000 + targetcore);
    gdn_send(5); // lock release
    raw_test_pass(5);
	gdn_send(1); // write lock
    raw_test_pass(1);
    gdn_send(ptr);
	raw_test_pass_reg(ptr);
	raw_test_pass(0xffff);
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
    if ((/*!gencontains(failedtasks, tpd)&&*/!gencontains(activetasks,tpd))) {
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

#ifdef RAW
int enqueuetasks_I(struct parameterwrapper *parameter, struct parameterwrapper *prevptr, struct ___Object___ *ptr, int * enterflags, int numenterflags) {
  void * taskpointerarray[MAXTASKPARAMS];
  int j;
  int numparams=parameter->task->numParameters;
  int numiterators=parameter->task->numTotal-1;
  int retval=1;
  int addnormal=1;
  int adderror=1;

  struct taskdescriptor * task=parameter->task;

	ObjectHashadd_I(parameter->objectset, (int) ptr, 0, (int) enterflags, numenterflags, enterflags==NULL);//this add the object to parameterwrapper
 
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
    struct taskparamdescriptor *tpd=RUNMALLOC_I(sizeof(struct taskparamdescriptor));
    tpd->task=task;
    tpd->numParameters=numiterators+1;
    tpd->parameterArray=RUNMALLOC_I(sizeof(void *)*(numiterators+1));
    for(j=0;j<=numiterators;j++){
      tpd->parameterArray[j]=taskpointerarray[j];//store the actual parameters
    }
    /* Enqueue task */
    if ((/*!gencontains(failedtasks, tpd)&&*/!gencontains(activetasks,tpd))) {
      genputtable_I(activetasks, tpd, tpd);
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
#endif

/* Handler for signals. The signals catch null pointer errors and
   arithmatic errors. */
#ifndef RAW
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
#endif

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
  int numparams=0;
  int numtotal=0;
  struct ___Object___ * tmpparam = NULL;
  struct parameterdescriptor * pd=NULL;
  struct parameterwrapper *pw=NULL;
  int j = 0;
  int x = 0;

#ifdef RAW
  int grount = 0;
  int andmask=0;
  int checkmask=0;

  raw_test_pass(0xe991);
#endif

#ifndef RAW
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
#endif

#ifndef RAW
  /* Zero fd set */
  FD_ZERO(&readfds);
#endif
  maxreadfd=0;
#ifndef RAW
  fdtoobject=allocateRuntimeHash(100);
#endif

#ifndef RAW
  /* Map first block of memory to protected, anonymous page */
  mmap(0, 0x1000, 0, MAP_SHARED|MAP_FIXED|MAP_ANON, -1, 0);
#endif

  newtask:
  while((hashsize(activetasks)>0)||(maxreadfd>0)) {

#ifdef RAW
	  raw_test_pass(0xe992);
#else
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
#endif

    /* See if there are any active tasks */
    if (hashsize(activetasks)>0) {
      int i;
      currtpd=(struct taskparamdescriptor *) getfirstkey(activetasks);
      genfreekey(activetasks, currtpd);
      
      /* Check if this task has failed, allow a task that contains optional objects to fire */
      /*if (gencontains(failedtasks, currtpd)) {
	// Free up task parameter descriptor
	RUNFREE(currtpd->parameterArray);
	RUNFREE(currtpd);
	goto newtask;
      }*/
      numparams=currtpd->task->numParameters;
      numtotal=currtpd->task->numTotal;
      
#ifdef THREADSIMULATE
	  int isolateflags[numparams];
#endif
      /* Make sure that the parameters are still in the queues */
      for(i=0;i<numparams;i++) {
	void * parameter=currtpd->parameterArray[i];
#ifdef RAW
	raw_test_pass(0xe993);
	// require locks for this parameter
	getwritelock(parameter);
	grount = 0;
	raw_user_interrupts_off();
	while(!lockflag) {
//#ifndef INTERRUPT
		receiveObject();
//#endif
	}
#ifndef INTERRUPT
	if(reside) {
		while(receiveObject() != -1) {
		}
	}
#endif
	grount = lockresult;
	
	lockresult = 0;
	lockobj = 0;
	lockflag = false;
#ifndef INTERRUPT
	reside = false;
#endif
	raw_user_interrupts_on();

	if(grount == 0) {
		raw_test_pass(0xe994);
		// can not get the lock, try later
		for(j = 0; j < i; ++j) {
			releasewritelock(taskpointerarray[j+OFFSET]);
		}
		genputtable(activetasks, currtpd, currtpd);
		goto newtask;
	}
	// flush the object
	{
		int tmp = 0;
		for(tmp = 0; tmp < classsize[((struct ___Object___ *)parameter)->type]; ++tmp) {
			invalidateAddr(parameter + tmp);
		}
	}
#endif
	tmpparam = (struct ___Object___ *)parameter;
#ifdef THREADSIMULATE
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
					releasewritelock(((struct ___Object___ *)taskpointerarray[j+OFFSET])->original);
				}
			}
			genputtable(activetasks, currtpd, currtpd);
			goto newtask;
		}
		if(tmpparam->version != tmpparam->original->version) {
			// some task on another core has changed this object
			// flush this object
			//memcpy(tmpparam, tmpparam->original, classsize[tmpparam->type]);
			// release all obtained locks 
			int j = 0;
			for(j = 0; j < i; ++j) {
				if(0 == isolateflags[j]) {
					releasewritelock(((struct ___Object___ *)taskpointerarray[j+OFFSET])->original);
				}
			}
			releasewritelock(tmpparam->original);

			// dequeue this object
			int numofcore = pthread_getspecific(key);
			struct parameterwrapper ** queues = objectqueues[numofcore][tmpparam->type];
			int length = numqueues[numofcore][tmpparam->type];
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
			//enqueueObject(tmpparam, NULL, 0);
			// Free up task parameter descriptor
			RUNFREE(currtpd->parameterArray);
			RUNFREE(currtpd);
			goto newtask;
		}
	} else {
		isolateflags[i] = 1;
	}
#endif
	pd=currtpd->task->descriptorarray[i];
	pw=(struct parameterwrapper *) pd->queue;
	/* Check that object is still in queue */
	{
	  if (!ObjectHashcontainskey(pw->objectset, (int) parameter)) {
#ifdef RAW
		  raw_test_pass(0xe995);
#endif
		// release grabbed locks
		for(j = 0; j < i; ++j) {
			releasewritelock(taskpointerarray[j+OFFSET]);
		}
		releasewritelock(parameter);
	    RUNFREE(currtpd->parameterArray);
	    RUNFREE(currtpd);
	    goto newtask;
	  }
	}
#ifdef RAW
	/* Check if the object's flags still meets requirements */
	{
		int tmpi = 0;
		bool ismet = false;
		for(tmpi = 0; tmpi < pw->numberofterms; ++tmpi) {
			andmask=pw->intarray[tmpi*2];
			checkmask=pw->intarray[tmpi*2+1];
			raw_test_pass(0xdd000000 + andmask);
			raw_test_pass_reg((int)parameter);
			raw_test_pass(0xdd000000 + ((struct ___Object___ *)parameter)->flag);
			raw_test_pass(0xdd000000 + checkmask);
			if((((struct ___Object___ *)parameter)->flag&andmask)==checkmask) {
				ismet = true;
				break;
			}
		}
		if (!ismet) {
			// flags are never suitable
			// remove this obj from the queue
			int next;
			int UNUSED, UNUSED2;
			int * enterflags;
			raw_test_pass(0xe996);
			ObjectHashget(pw->objectset, (int) parameter, (int *) &next, (int *) &enterflags, &UNUSED, &UNUSED2);
			ObjectHashremove(pw->objectset, (int)parameter);
			if (enterflags!=NULL)
				free(enterflags);
			// release grabbed locks
			for(j = 0; j < i; ++j) {
				releasewritelock(taskpointerarray[j+OFFSET]);
			}
			releasewritelock(parameter);
			RUNFREE(currtpd->parameterArray);
			RUNFREE(currtpd);
			goto newtask;
		}
	}
#endif
      parameterpresent:
	;
	/* Check that object still has necessary tags */
	for(j=0;j<pd->numbertags;j++) {
	  int slotid=pd->tagarray[2*j]+numparams;
	  struct ___TagDescriptor___ *tagd=currtpd->parameterArray[slotid];
	  if (!containstag(parameter, tagd)) {
#ifdef RAW
		  raw_test_pass(0xe997);
#endif
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

#ifdef THREADSIMULATE
	 for(i = 0; i < numparams; ++i) {
		  if(0 == isolateflags[i]) {
			  struct ___Object___ * tmpparam = (struct ___Object___ *)taskpointerarray[i+OFFSET];
			  if(tmpparam != tmpparam->original) {
				  taskpointerarray[i+OFFSET] = tmpparam->original;
			  }
		  }
	  }
#endif

      {
#if 0
#ifndef RAW
	/* Checkpoint the state */
	forward=allocateRuntimeHash(100);
	reverse=allocateRuntimeHash(100);
	//void ** checkpoint=makecheckpoint(currtpd->task->numParameters, currtpd->parameterArray, forward, reverse);
#endif
#endif
	if (x=setjmp(error_handler)) {
	  int counter;
	  /* Recover */
#ifndef RAW	  
#ifdef DEBUG
	  printf("Fatal Error=%d, Recovering!\n",x);
#endif
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
	  //fflush(stdout);
#ifdef RAW
	  raw_test_pass_reg(x);
	  raw_test_done(0xa009);
#else
	  exit(-1);
#endif
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
#ifndef RAW
	    printf("ENTER %s count=%d\n",currtpd->task->name, (instaccum-instructioncount));
#endif
	    ((void (*) (void **)) currtpd->task->taskptr)(taskpointerarray);
#ifndef RAW
	    printf("EXIT %s count=%d\n",currtpd->task->name, (instaccum-instructioncount));
#endif
	  } else
	    ((void (*) (void **)) currtpd->task->taskptr)(taskpointerarray);

#ifdef RAW
	  for(i = 0; i < numparams; ++i) {
		  int j = 0;
		  struct ___Object___ * tmpparam = (struct ___Object___ *)taskpointerarray[i+OFFSET];
		  raw_test_pass(0xe998);
		raw_test_pass(0xdd100000 + tmpparam->flag);
		  releasewritelock(tmpparam);
	  }
#elif defined THREADSIMULATE
	  for(i = 0; i < numparams; ++i) {
		  if(0 == isolateflags[i]) {
			  struct ___Object___ * tmpparam = (struct ___Object___ *)taskpointerarray[i+OFFSET];
			  releasewritelock(tmpparam);
		  }
	  }
#endif

#if 0
#ifndef RAW
	  freeRuntimeHash(forward);
	  freeRuntimeHash(reverse);
#endif
#endif
	  freemalloc();
	  // Free up task parameter descriptor
	  RUNFREE(currtpd->parameterArray);
	  RUNFREE(currtpd);
#if 0
#ifndef RAW
	  forward=NULL;
	  reverse=NULL;
#endif
#endif
	}
      }
    }
  }
#ifdef RAW
	raw_test_pass(0xe999);
#endif
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
#ifdef RAW
	   if(corenum > NUMCORES - 1) {
		   return;
	   }
#endif
   for(i=0;i<numtasks[corenum];i++) {
     struct taskdescriptor * task=taskarray[corenum][i];
#endif
#ifndef RAW
     printf("%s\n", task->name);
#endif
     for(j=0;j<task->numParameters;j++) {
       struct parameterdescriptor *param=task->descriptorarray[j];
       struct parameterwrapper *parameter=param->queue;
       struct ObjectHash * set=parameter->objectset;
       struct ObjectIterator objit;
#ifndef RAW
       printf("  Parameter %d\n", j);
#endif
       ObjectHashiterator(set, &objit);
       while(ObjhasNext(&objit)) {
	 struct ___Object___ * obj=(struct ___Object___ *)Objkey(&objit);
	 struct ___Object___ * tagptr=obj->___tags___;
	 int nonfailed=Objdata4(&objit);
	 int numflags=Objdata3(&objit);
	 int flags=Objdata2(&objit);
	 Objnext(&objit);
#ifndef RAW
	 printf("    Contains %lx\n", obj);
	 printf("      flag=%d\n", obj->flag); 
#endif
	 if (tagptr==NULL) {
	 } else if (tagptr->type==TAGTYPE) {
#ifndef RAW
	   printf("      tag=%lx\n",tagptr);
#endif
	   ;
	 } else {
	   int tagindex=0;
	   struct ArrayObject *ao=(struct ArrayObject *)tagptr;
	   for(;tagindex<ao->___cachedCode___;tagindex++) {
#ifndef RAW
	     printf("      tag=%lx\n",ARRAYGET(ao, struct ___TagDescriptor___*, tagindex));
#endif
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
#ifdef RAW
	  if(corenum > NUMCORES - 1) {
		  return;
	  }
#endif
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
