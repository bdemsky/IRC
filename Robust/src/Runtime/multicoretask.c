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
#ifdef RAWPROFILE
#ifdef RAWUSEIO
#include "stdio.h"
#include "string.h"
#endif
#endif
#include <raw.h>
#include <raw_compiler_defs.h>
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
int numconfirm;
bool waitconfirm;
bool busystatus;
#ifdef RAW
struct RuntimeHash locktable;
static struct RuntimeHash* locktbl = &locktable;
struct LockValue {
	int redirectlock;
	int value;
};
struct RuntimeHash * objRedirectLockTbl;
void * curr_heapbase=0;
void * curr_heaptop=0;
int self_numsendobjs;
int self_numreceiveobjs;
int lockobj;
int lock2require;
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
int outmsgdata[30];
int outmsgindex;
int outmsglast;
int outmsgleft;
bool isMsgHanging;
volatile bool isMsgSending;
void calCoords(int core_num, int* coordY, int* coordX);
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
void transStatusConfirmMsg(int targetcore);
int receiveObject();
bool getreadlock(void* ptr);
void releasereadlock(void* ptr);
#ifdef RAW
bool getreadlock_I_r(void * ptr, void * redirectlock, int core, bool cache);
#endif
bool getwritelock(void* ptr);
void releasewritelock(void* ptr);
void releasewritelock_r(void * lock, void * redirectlock);
#ifdef RAW
bool getwritelock_I(void* ptr);
bool getwritelock_I_r(void* lock, void* redirectlock, int core, bool cache);
void releasewritelock_I(void * ptr);
#endif

// profiling mode of RAW version
#ifdef RAWPROFILE

#define TASKINFOLENGTH 10000
//#define INTERRUPTINFOLENGTH 500

bool stall;
//bool isInterrupt;
int totalexetime;

typedef struct task_info {
  char* taskName;
  int startTime;
  int endTime;
  int exitIndex;
  struct Queue * newObjs; 
} TaskInfo;

/*typedef struct interrupt_info {
   int startTime;
   int endTime;
   } InterruptInfo;*/

TaskInfo * taskInfoArray[TASKINFOLENGTH];
int taskInfoIndex;
bool taskInfoOverflow;
/*InterruptInfo * interruptInfoArray[INTERRUPTINFOLENGTH];
   int interruptInfoIndex;
   bool interruptInfoOverflow;*/
int profilestatus[NUMCORES]; // records status of each core
                             // 1: running tasks
                             // 0: stall
bool transProfileRequestMsg(int targetcore);
void outputProfileData();
#endif

#ifdef RAW
#ifdef RAWUSEIO
int main(void) {
#else
void begin() {
#endif
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

  corenum = raw_get_abs_pos_x() + raw_get_array_size_x() * raw_get_abs_pos_y();

  // initialize the arrays
  if(STARTUPCORE == corenum) {
    // startup core to initialize corestatus[]
    for(i = 0; i < NUMCORES; ++i) {
      corestatus[i] = 1;
      numsendobjs[i] = 0;                   // assume all variables in RAW are local variables! MAY BE WRONG!!!
      numreceiveobjs[i] = 0;
    }
	numconfirm = 0;
	waitconfirm = false;
#ifdef RAWPROFILE
    for(i = 0; i < NUMCORES; ++i) {
      profilestatus[i] = 1;
    }
#endif
  }
  busystatus = true;
  self_numsendobjs = 0;
  self_numreceiveobjs = 0;
  for(i = 0; i < 30; ++i) {
    msgdata[i] = -1;
  }
  msgtype = -1;
  msgdataindex = 0;
  msglength = 30;

  for(i = 0; i < 30; ++i) {
    outmsgdata[i] = -1;
  }
  outmsgindex = 0;
  outmsglast = 0;
  outmsgleft = 0;
  isMsgHanging = false;
  isMsgSending = false;

  // create the lock table, lockresult table and obj queue
  locktable.size = 20;
  locktable.bucket = (struct RuntimeNode **) RUNMALLOC_I(sizeof(struct RuntimeNode *)*20);
  /* Set allocation blocks*/
  locktable.listhead=NULL;
  locktable.listtail=NULL;
  /*Set data counts*/
  locktable.numelements = 0;
  lockobj = 0;
  lock2require = 0;
  lockresult = 0;
  lockflag = false;
#ifndef INTERRUPT
  reside = false;
#endif
  objqueue.head = NULL;
  objqueue.tail = NULL;
  lockRedirectTbl = allocateRuntimeHash(20);
  objRedirectLockTbl = allocateRuntimeHash(20);

#ifdef RAWPROFILE
  stall = false;
  //isInterrupt = true;
  totalexetime = -1;
  taskInfoIndex = 0;
  /*interruptInfoIndex = 0;
     taskInfoOverflow = false;
     interruptInfoOverflow = false;*/
#endif

#ifdef INTERRUPT
  if (corenum < NUMCORES) {
    // set up interrupts
    setup_ints();
    raw_user_interrupts_on();
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
    if (rc[i]) {
      printf("[main] ERROR; return code from pthread_create() is %d\n", rc[i]);
      fflush(stdout);
      exit(-1);
    }
  }

  while(true) {
  }
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
  /* Create table for failed tasks */
#ifdef RAW
  if(corenum > NUMCORES - 1) {
    failedtasks = NULL;
    activetasks = NULL;
/*#ifdef RAWPROFILE
        raw_test_pass(0xee01);
        raw_test_pass_reg(taskInfoIndex);
        raw_test_pass_reg(taskInfoOverflow);
        if(!taskInfoOverflow) {
        TaskInfo* taskInfo = RUNMALLOC(sizeof(struct task_info));
        taskInfoArray[taskInfoIndex] = taskInfo;
        taskInfo->taskName = "msg handling";
        taskInfo->startTime = raw_get_cycle();
        taskInfo->endTime = -1;
        }
 #endif*/
#ifdef RAWPROFILE
    //isInterrupt = false;
#endif
    while(true) {
      receiveObject();
    }
  } else {
#endif
  /*failedtasks=genallocatehashtable((unsigned int (*)(void *)) &hashCodetpd,
                                   (int (*)(void *,void *)) &comparetpd);*/
  failedtasks = NULL;
  /* Create queue of active tasks */
  activetasks=genallocatehashtable((unsigned int(*) (void *)) &hashCodetpd,
                                   (int(*) (void *,void *)) &comparetpd);

  /* Process task information */
  processtasks();

  if(STARTUPCORE == corenum) {
    /* Create startup object */
    createstartupobject(argc, argv);
  }

#ifdef RAW
#ifdef RAWDEBUG
  raw_test_pass(0xee00);
#endif

  while(true) {
    // check if there are new active tasks can be executed
    executetasks();

#ifndef INTERRUPT
    while(receiveObject() != -1) {
    }
#endif

#ifdef RAWDEBUG
    raw_test_pass(0xee01);
#endif

    // check if there are some pending objects, if yes, enqueue them and executetasks again
    tocontinue = false;
#ifdef RAWPROFILE
    {
      bool isChecking = false;
      if(!isEmpty(&objqueue)) {
	if(!taskInfoOverflow) {
	  TaskInfo* taskInfo = RUNMALLOC(sizeof(struct task_info));
	  taskInfoArray[taskInfoIndex] = taskInfo;
	  taskInfo->taskName = "objqueue checking";
	  taskInfo->startTime = raw_get_cycle();
	  taskInfo->endTime = -1;
	  taskInfo->exitIndex = -1;
	  taskInfo->newObjs = NULL;
	}
	isChecking = true;
      }
#endif
    while(!isEmpty(&objqueue)) {
      void * obj = NULL;
#ifdef INTERRUPT
      raw_user_interrupts_off();
#endif
#ifdef RAWPROFILE
      //isInterrupt = false;
#endif
#ifdef RAWDEBUG
      raw_test_pass(0xeee1);
#endif
      sendStall = false;
      tocontinue = true;
      objitem = getTail(&objqueue);
      objInfo = (struct transObjInfo *)objitem->objectptr;
      obj = objInfo->objptr;
#ifdef RAWDEBUG
      raw_test_pass_reg((int)obj);
#endif
      // grab lock and flush the obj
      grount = 0;
	getwritelock_I(obj);
	while(!lockflag) {
	  receiveObject();
	}
	grount = lockresult;
#ifdef RAWDEBUG
	raw_test_pass_reg(grount);
#endif

	lockresult = 0;
	lockobj = 0;
	lock2require = 0;
	lockflag = false;
#ifndef INTERRUPT
	reside = false;
#endif

      if(grount == 1) {
	int k = 0;
	// flush the object
#ifdef RAWCACHEFLUSH
	raw_invalidate_cache_range((int)obj, classsize[((struct ___Object___ *)obj)->type]);
#endif
	// enqueue the object
	for(k = 0; k < objInfo->length; ++k) {
	  int taskindex = objInfo->queues[2 * k];
	  int paramindex = objInfo->queues[2 * k + 1];
	  struct parameterwrapper ** queues = &(paramqueues[corenum][taskindex][paramindex]);
#ifdef RAWDEBUG
	  raw_test_pass_reg(taskindex);
	  raw_test_pass_reg(paramindex);
#endif
	  enqueueObject_I(obj, queues, 1);
	}
	removeItem(&objqueue, objitem);
	  releasewritelock_I(obj);
	RUNFREE(objInfo->queues);
	RUNFREE(objInfo);
      } else {
	// can not get lock
	// put it at the end of the queue
	// and try to execute active tasks already enqueued first
	removeItem(&objqueue, objitem);
	addNewItem_I(&objqueue, objInfo);
#ifdef RAWPROFILE
	//isInterrupt = true;
#endif
#ifdef INTERRUPT
	raw_user_interrupts_on();
#endif
	break;
      }
#ifdef INTERRUPT
      raw_user_interrupts_on();
#endif
    }
#ifdef RAWPROFILE
    if(isChecking && (!taskInfoOverflow)) {
      taskInfoArray[taskInfoIndex]->endTime = raw_get_cycle();
      taskInfoIndex++;
      if(taskInfoIndex == TASKINFOLENGTH) {
	taskInfoOverflow = true;
      }
    }
  }
#endif
#ifdef RAWDEBUG
    raw_test_pass(0xee02);
#endif

    if(!tocontinue) {
      // check if stop
      if(STARTUPCORE == corenum) {
	if(isfirst) {
#ifdef RAWDEBUG
	  raw_test_pass(0xee03);
#endif
	  isfirst = false;
	}
	if((!waitconfirm) || 
			(waitconfirm && (numconfirm == 0))) {
#ifdef RAWDEBUG
	  raw_test_pass(0xee04);
#endif
#ifdef INTERRUPT
		raw_user_interrupts_off();
#endif
		corestatus[corenum] = 0;
		numsendobjs[corenum] = self_numsendobjs;
		numreceiveobjs[corenum] = self_numreceiveobjs;
		// check the status of all cores
		allStall = true;
#ifdef RAWDEBUG
		raw_test_pass_reg(NUMCORES);
#endif
		for(i = 0; i < NUMCORES; ++i) {
#ifdef RAWDEBUG
		  raw_test_pass(0xe000 + corestatus[i]);
#endif
		  if(corestatus[i] != 0) {
	    	allStall = false;
		    break;
		  }
		}
		if(allStall) {
			if(!waitconfirm) {
				// the first time found all cores stall
				// send out status confirm msg to all other cores
				// reset the corestatus array too
#ifdef RAWDEBUG
	  raw_test_pass(0xee05);
#endif
				corestatus[corenum] = 1;
				for(i = 1; i < NUMCORES; ++i) {
					corestatus[i] = 1;
					transStatusConfirmMsg(i);
				}
				waitconfirm = true;
				numconfirm = NUMCORES - 1;
			} else {
				// all the core status info are the latest
		   	  // check if the sum of send objs and receive obj are the same
			  // yes->terminate; for profiling mode, yes->send request to all
			  // other cores to pour out profiling data
			  // no->go on executing
#ifdef RAWDEBUG
	  raw_test_pass(0xee06);
#endif
			  sumsendobj = 0;
			  for(i = 0; i < NUMCORES; ++i) {
		    	sumsendobj += numsendobjs[i];
#ifdef RAWDEBUG
	    		raw_test_pass(0xf000 + numsendobjs[i]);
#endif
	 	  	  }
			  for(i = 0; i < NUMCORES; ++i) {
		    	sumsendobj -= numreceiveobjs[i];
#ifdef RAWDEBUG
		    	raw_test_pass(0xf000 + numreceiveobjs[i]);
#endif
			  }
			  if(0 == sumsendobj) {
		    	// terminate
#ifdef RAWDEBUG
	    		raw_test_pass(0xee07);
#endif
#ifdef RAWUSEIO
	    		totalexetime = raw_get_cycle();
#else
		    	raw_test_pass(0xbbbbbbbb);
		    	raw_test_pass(raw_get_cycle());
#endif
	    		// profile mode, send msgs to other cores to request pouring
			    // out progiling data
#ifdef RAWPROFILE
#ifdef INTERRUPT
			    // reopen gdn_avail interrupts
	    		raw_user_interrupts_on();
#endif
		    	for(i = 1; i < NUMCORES; ++i) {
			      transProfileRequestMsg(i);
		    	}
			    // pour profiling data on startup core
		    	outputProfileData();
		    	while(true) {
#ifdef INTERRUPT
			      raw_user_interrupts_off();
#endif
			      profilestatus[corenum] = 0;
	    		  // check the status of all cores
			      allStall = true;
#ifdef RAWDEBUG
			      raw_test_pass_reg(NUMCORES);
#endif	
	    		  for(i = 0; i < NUMCORES; ++i) {
#ifdef RAWDEBUG
					raw_test_pass(0xe000 + profilestatus[i]);
#endif
					if(profilestatus[i] != 0) {
					  allStall = false;
					  break;
					}
				  }
				  if(!allStall) {
					int halt = 100;
#ifdef INTERRUPT
					raw_user_interrupts_on();
#endif
					while(halt--) {
					}
				  } else {
					  break;
				  }
				}
#endif
			    raw_test_done(1);                                   // All done.
			  } else {
				  // still some objects on the fly on the network
				  // reset the waitconfirm and numconfirm
				  waitconfirm = false;
				  numconfirm = 0;
			  }
			}
		}
#ifdef INTERRUPT
		raw_user_interrupts_on();
#endif
	  }
   } else {
	if(!sendStall) {
#ifdef RAWDEBUG
	  raw_test_pass(0xee08);
#endif
#ifdef RAWPROFILE
	  if(!stall) {
#endif
	  if(isfirst) {
	    // wait for some time
	    int halt = 10000;
#ifdef RAWDEBUG
	    raw_test_pass(0xee09);
#endif
	    while(halt--) {
	    }
	    isfirst = false;
	  } else {
	    // send StallMsg to startup core
#ifdef RAWDEBUG
	    raw_test_pass(0xee0a);
#endif
	    sendStall = transStallMsg(STARTUPCORE);
	    isfirst = true;
		busystatus = false;
	  }
#ifdef RAWPROFILE
	}
#endif
	} else {
	  isfirst = true;
	  busystatus = false;
#ifdef RAWDEBUG
	  raw_test_pass(0xee0b);
#endif
	}
      }
    }
  }
  } // right-bracket for if-else of line 380
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
      //printf("[run, %d] receive an object\n", numofcore);
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
      assert(STARTUPCORE == numofcore);                                     // only startup core can receive such msg
      sendStall = false;
      break;
    }

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
  for(i=1; i<argc; i++) {
    int length=strlen(argv[i]);
#ifdef PRECISE_GC
    struct ___String___ *newstring=NewString(NULL, argv[i],length);
#else
    struct ___String___ *newstring=NewString(argv[i],length);
#endif
    ((void **)(((char *)&stringarray->___length___)+sizeof(int)))[i-1]=newstring;
  }

  startupobject->isolate = 1;
  startupobject->version = 0;
  startupobject->lock = NULL;

  /* Set initialized flag for startup object */
  flagorandinit(startupobject,1,0xFFFFFFFF);
  enqueueObject(startupobject, NULL, 0);
#ifdef RAWCACHEFLUSH
  raw_flush_entire_cache();
#endif
}

int hashCodetpd(struct taskparamdescriptor *ftd) {
  int hash=(int)ftd->task;
  int i;
  for(i=0; i<ftd->numParameters; i++) {
    hash^=(int)ftd->parameterArray[i];
  }
  return hash;
}

int comparetpd(struct taskparamdescriptor *ftd1, struct taskparamdescriptor *ftd2) {
  int i;
  if (ftd1->task!=ftd2->task)
    return 0;
  for(i=0; i<ftd1->numParameters; i++)
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
  if (tagptr==NULL) {
    obj->___tags___=(struct ___Object___ *)tagd;
  } else {
    /* Have to check if it is already set */
    if (tagptr->type==TAGTYPE) {
      struct ___TagDescriptor___ * td=(struct ___TagDescriptor___ *) tagptr;
      if (td==tagd) {
	return;
      }
#ifdef PRECISE_GC
      int ptrarray[]={2, (int) ptr, (int) obj, (int)tagd};
      struct ArrayObject * ao=allocate_newarray(&ptrarray,TAGARRAYTYPE,TAGARRAYINTERVAL);
      obj=(struct ___Object___ *)ptrarray[2];
      tagd=(struct ___TagDescriptor___ *)ptrarray[3];
      td=(struct ___TagDescriptor___ *) obj->___tags___;
#else
      ao=allocate_newarray(TAGARRAYTYPE,TAGARRAYINTERVAL);
#endif
      ARRAYSET(ao, struct ___TagDescriptor___ *, 0, td);
      ARRAYSET(ao, struct ___TagDescriptor___ *, 1, tagd);
      obj->___tags___=(struct ___Object___ *) ao;
      ao->___cachedCode___=2;
    } else {
      /* Array Case */
      int i;
      struct ArrayObject *ao=(struct ArrayObject *) tagptr;
      for(i=0; i<ao->___cachedCode___; i++) {
	struct ___TagDescriptor___ * td=ARRAYGET(ao, struct ___TagDescriptor___*, i);
	if (td==tagd) {
	  return;
	}
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
	for(i=0; i<ao->___length___; i++) {
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
	for(i=0; i<ao->___length___; i++) {
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
#ifndef RAW
      printf("ERROR 1 in tagclear\n");
#endif
      ;
  } else {
    struct ArrayObject *ao=(struct ArrayObject *) tagptr;
    int i;
    for(i=0; i<ao->___cachedCode___; i++) {
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
      for(i=0; i<ao->___cachedCode___; i++) {
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
    flagbody(ptr, flag, queues, length, false);
  }
}

bool intflagorand(void * ptr, int ormask, int andmask) {
  {
    int oldflag=((int *)ptr)[1];
    int flag=ormask|oldflag;
    flag&=andmask;
    if (flag==oldflag)   /* Don't do anything */
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
	  goto nextloop; //that means the object has no tag but that param needs tag
	else if(tagptr->type==TAGTYPE) { //one tag
	  struct ___TagDescriptor___ * tag=(struct ___TagDescriptor___*) tagptr;
	  for(i=0; i<parameter->numbertags; i++) {
	    //slotid is parameter->tagarray[2*i];
	    int tagid=parameter->tagarray[2*i+1];
	    if (tagid!=tagptr->flag)
	      goto nextloop; /*We don't have this tag */
	  }
	} else { //multiple tags
	  struct ArrayObject * ao=(struct ArrayObject *) tagptr;
	  for(i=0; i<parameter->numbertags; i++) {
	    //slotid is parameter->tagarray[2*i];
	    int tagid=parameter->tagarray[2*i+1];
	    int j;
	    for(j=0; j<ao->___cachedCode___; j++) {
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
      for(i=0; i<parameter->numberofterms; i++) {
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
    tagptr=ptr->___tags___;

    /* Outer loop iterates through all parameter queues an object of
       this type could be in.  */
    for(j = 0; j < length; ++j) {
      parameter = queues[j];
      /* Check tags */
      if (parameter->numbertags>0) {
	if (tagptr==NULL)
	  goto nextloop; //that means the object has no tag but that param needs tag
	else if(tagptr->type==TAGTYPE) { //one tag
	  struct ___TagDescriptor___ * tag=(struct ___TagDescriptor___*) tagptr;
	  for(i=0; i<parameter->numbertags; i++) {
	    //slotid is parameter->tagarray[2*i];
	    int tagid=parameter->tagarray[2*i+1];
	    if (tagid!=tagptr->flag) {
	      goto nextloop; /*We don't have this tag */
	    }
	  }
	} else { //multiple tags
	  struct ArrayObject * ao=(struct ArrayObject *) tagptr;
	  for(i=0; i<parameter->numbertags; i++) {
	    //slotid is parameter->tagarray[2*i];
	    int tagid=parameter->tagarray[2*i+1];
	    int j;
	    for(j=0; j<ao->___cachedCode___; j++) {
	      if (tagid==ARRAYGET(ao, struct ___TagDescriptor___*, j)->flag) {
		goto foundtag;
	      }
	    }
	    goto nextloop;
foundtag:
	    ;
	  }
	}
      }

      /* Check flags */
      for(i=0; i<parameter->numberofterms; i++) {
	int andmask=parameter->intarray[i*2];
	int checkmask=parameter->intarray[i*2+1];
	if ((ptr->flag&andmask)==checkmask) {
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
  *coordX = core_num % raw_get_array_size_x();
  *coordY = core_num / raw_get_array_size_x();
}
#endif

int * getAliasLock(void ** ptrs, int length, struct RuntimeHash * tbl) {
	if(length == 0) {
		return (int*)(RUNMALLOC(sizeof(int)));
	} else {
		int i = 0;
		int locks[length];
		int locklen = 0;
		bool redirect = false;
		int redirectlock = 0;
		for(; i < length; i++) {
			struct ___Object___ * ptr = (struct ___Object___ *)(ptrs[i]);
			int lock = 0;
			int j = 0;
			if(ptr->lock == NULL) {
				lock = (int)(ptr);
			} else {
				lock = (int)(ptr->lock);
			}
			if(redirect) {
				if(lock != redirectlock) {
					RuntimeHashadd(tbl, lock, redirectlock);
				}
			} else {
				if(RuntimeHashcontainskey(tbl, lock)) {
					// already redirected
					redirect = true;
					RuntimeHashget(tbl, lock, &redirectlock);
					for(; j < locklen; j++) {
						if(locks[j] != redirectlock) {
							RuntimeHashadd(tbl, locks[j], redirectlock);
						}
					}
				} else {
					bool insert = true;
					for(j = 0; j < locklen; j++) {
						if(locks[j] == lock) {
							insert = false;
							break;
						} else if(locks[j] > lock) {
							break;
						}
					}
					if(insert) {
						int h = locklen;
						for(; h > j; h--) {
							locks[h] = locks[h-1];
						}	
						locks[j] = lock;
						locklen++;
					}
				}
			}
		}
		if(redirect) {
			return (int *)redirectlock;
		} else {
			return (int *)(locks[0]);
		}
	}
}

void addAliasLock(void * ptr, int lock) {
  struct ___Object___ * obj = (struct ___Object___ *)ptr;
  if(((int)ptr != lock) && (obj->lock != (int*)lock)) {
    // originally no alias lock associated or have a different alias lock
    // flush it as the new one
    obj->lock = (int *)lock;
  }
}

/* Message format for RAW version:
 *      type + Msgbody
 * type: 0 -- transfer object
 *       1 -- transfer stall msg
 *       2 -- lock request
 *       3 -- lock grount
 *       4 -- lock deny
 *       5 -- lock release
 *       // add for profile info
 *       6 -- transfer profile output msg
 *       7 -- transfer profile output finish msg
 *       // add for alias lock strategy
 *       8 -- redirect lock request
 *       9 -- lock grant with redirect info
 *       a -- lock deny with redirect info
 *       b -- lock release with redirect info
 *       c -- status confirm request
 *       d -- status report msg
 *
 * ObjMsg: 0 + size of msg + obj's address + (task index + param index)+
 * StallMsg: 1 + corenum + sendobjs + receiveobjs (size is always 4 * sizeof(int))
 * LockMsg: 2 + lock type + obj pointer + lock + request core (size is always 5 * sizeof(int))
 *          3/4/5 + lock type + obj pointer + lock (size is always 4 * sizeof(int))
 *          8 + lock type + obj pointer +  redirect lock + root request core + request core (size is always 6 * sizeof(int))
 *          9/a + lock type + obj pointer + redirect lock (size is always 4 * sizeof(int))
 *          b + lock type + lock + redirect lock (size is always 4 * sizeof(int))
 *          lock type: 0 -- read; 1 -- write
 * ProfileMsg: 6 + totalexetime (size is always 2 * sizeof(int))
 *             7 + corenum (size is always 2 * sizeof(int))
 * StatusMsg: c (size is always 1 * sizeof(int))
 *            d + status + corenum (size is always 3 * sizeof(int))
 *            status: 0 -- stall; 1 -- busy
 */

// transfer an object to targetcore
// format: object
void transferObject(struct transObjInfo * transObj) {
  void * obj = transObj->objptr;
  int type=((int *)obj)[0];
  int size=classsize[type];
  int targetcore = transObj->targetcore;

#ifdef RAW
  unsigned msgHdr;
  int self_y, self_x, target_y, target_x;
  // for 32 bit machine, the size of fixed part is always 3 words
  int msgsize = 3 + transObj->length * 2;
  int i = 0;

  struct ___Object___ * newobj = (struct ___Object___ *)obj;

  calCoords(corenum, &self_y, &self_x);
  calCoords(targetcore, &target_y, &target_x);
  isMsgSending = true;
  // Build the message header
  msgHdr = construct_dyn_hdr(0, msgsize, 0,             // msgsize word sent.
                             self_y, self_x,
                             target_y, target_x);
  // start sending msg, set sand msg flag
  //isMsgSending = true;
  gdn_send(msgHdr);                     // Send the message header to EAST to handle fab(n - 1).
#ifdef RAWDEBUG
  raw_test_pass(0xbbbb);
  raw_test_pass(0xb000 + targetcore);       // targetcore
#endif
  gdn_send(0);
#ifdef RAWDEBUG
  raw_test_pass(0);
#endif
  gdn_send(msgsize);
#ifdef RAWDEBUG
  raw_test_pass_reg(msgsize);
#endif
  gdn_send((int)obj);
#ifdef RAWDEBUG
  raw_test_pass_reg(obj);
#endif
  for(i = 0; i < transObj->length; ++i) {
    int taskindex = transObj->queues[2*i];
    int paramindex = transObj->queues[2*i+1];
    gdn_send(taskindex);
#ifdef RAWDEBUG
    raw_test_pass_reg(taskindex);
#endif
    gdn_send(paramindex);
#ifdef RAWDEBUG
    raw_test_pass_reg(paramindex);
#endif
  }
#ifdef RAWDEBUG
  raw_test_pass(0xffff);
#endif
  ++(self_numsendobjs);
  // end of sending this msg, set sand msg flag false
  isMsgSending = false;
  // check if there are pending msgs
  while(isMsgHanging) {
    // get the msg from outmsgdata[]
    // length + target + msg
    outmsgleft = outmsgdata[outmsgindex++];
    targetcore = outmsgdata[outmsgindex++];
    calCoords(targetcore, &target_y, &target_x);
	isMsgSending = true;
    // Build the message header
    msgHdr = construct_dyn_hdr(0, outmsgleft, 0,                        // msgsize word sent.
                               self_y, self_x,
                               target_y, target_x);
    //isMsgSending = true;
    gdn_send(msgHdr);                           
#ifdef RAWDEBUG
    raw_test_pass(0xbbbb);
    raw_test_pass(0xb000 + targetcore);             // targetcore
#endif
    while(outmsgleft-- > 0) {
      gdn_send(outmsgdata[outmsgindex++]);
#ifdef RAWDEBUG
      raw_test_pass_reg(outmsgdata[outmsgindex - 1]);
#endif
    }
#ifdef RAWDEBUG
    raw_test_pass(0xffff);
#endif
    isMsgSending = false;
#ifdef INTERRUPT
    raw_user_interrupts_off();
#endif
    // check if there are still msg hanging
    if(outmsgindex == outmsglast) {
      // no more msgs
      outmsgindex = outmsglast = 0;
      isMsgHanging = false;
    }
#ifdef INTERRUPT
    raw_user_interrupts_on();
#endif
  }
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
    ret=mq_send(mqdnum, (void *)newobj, sizeof(struct ___Object___), 0);             // send the object into the queue
    if(ret != 0) {
      printf("[transferObject, %d] mq_send to %s returned: %d, error: %s\n", numofcore, path, ret, strerror(errno));
    }
  } while(ret!=0);
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
  isMsgSending = true;
  // Build the message header
  msgHdr = construct_dyn_hdr(0, msgsize, 0,             // msgsize word sent.
                             self_y, self_x,
                             target_y, target_x);
  // start sending msgs, set msg sending flag
  //isMsgSending = true;
  gdn_send(msgHdr);                     // Send the message header to EAST to handle fab(n - 1).
#ifdef RAWDEBUG
  raw_test_pass(0xbbbb);
  raw_test_pass(0xb000 + targetcore);       // targetcore
#endif
  gdn_send(1);
#ifdef RAWDEBUG
  raw_test_pass(1);
#endif
  gdn_send(corenum);
#ifdef RAWDEBUG
  raw_test_pass_reg(corenum);
#endif
  gdn_send(self_numsendobjs);
#ifdef RAWDEBUG
  raw_test_pass_reg(self_numsendobjs);
#endif
  gdn_send(self_numreceiveobjs);
#ifdef RAWDEBUG
  raw_test_pass_reg(self_numreceiveobjs);
  raw_test_pass(0xffff);
#endif
  // end of sending this msg, set sand msg flag false
  isMsgSending = false;
  // check if there are pending msgs
  while(isMsgHanging) {
    // get the msg from outmsgdata[]
    // length + target + msg
    outmsgleft = outmsgdata[outmsgindex++];
    targetcore = outmsgdata[outmsgindex++];
    calCoords(targetcore, &target_y, &target_x);
	isMsgSending = true;
    // Build the message header
    msgHdr = construct_dyn_hdr(0, outmsgleft, 0,                        // msgsize word sent.
                               self_y, self_x,
                               target_y, target_x);
	//isMsgSending = true;
    gdn_send(msgHdr);                           // Send the message header to EAST to handle fab(n - 1).
#ifdef RAWDEBUG
    raw_test_pass(0xbbbb);
    raw_test_pass(0xb000 + targetcore);             // targetcore
#endif
    while(outmsgleft-- > 0) {
      gdn_send(outmsgdata[outmsgindex++]);
#ifdef RAWDEBUG
      raw_test_pass_reg(outmsgdata[outmsgindex - 1]);
#endif
    }
#ifdef RAWDEBUG
    raw_test_pass(0xffff);
#endif
    isMsgSending = false;
#ifdef INTERRUPT
    raw_user_interrupts_off();
#endif
    // check if there are still msg hanging
    if(outmsgindex == outmsglast) {
      // no more msgs
      outmsgindex = outmsglast = 0;
      isMsgHanging = false;
    }
#ifdef INTERRUPT
    raw_user_interrupts_on();
#endif
  }
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
  ret=mq_send(mqdnum, (void *)newobj, sizeof(struct ___Object___), 0);       // send the object into the queue
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

void transStatusConfirmMsg(int targetcore) {
#ifdef RAW
  unsigned msgHdr;
  int self_y, self_x, target_y, target_x;
  // for 32 bit machine, the size is always 1 words
  //int msgsize = sizeof(int) * 1;
  int msgsize = 1;

  calCoords(corenum, &self_y, &self_x);
  calCoords(targetcore, &target_y, &target_x);
  isMsgSending = true;
  // Build the message header
  msgHdr = construct_dyn_hdr(0, msgsize, 0,             // msgsize word sent.
                             self_y, self_x,
                             target_y, target_x);
  // start sending msgs, set msg sending flag
  //isMsgSending = true;
  gdn_send(msgHdr);                     // Send the message header to EAST to handle fab(n - 1).
#ifdef RAWDEBUG
  raw_test_pass(0xbbbb);
  raw_test_pass(0xb000 + targetcore);       // targetcore
#endif
  gdn_send(0xc);
#ifdef RAWDEBUG
  raw_test_pass(0xc);
  raw_test_pass(0xffff);
#endif
  // end of sending this msg, set sand msg flag false
  isMsgSending = false;
  // check if there are pending msgs
  while(isMsgHanging) {
    // get the msg from outmsgdata[]
    // length + target + msg
    outmsgleft = outmsgdata[outmsgindex++];
    targetcore = outmsgdata[outmsgindex++];
    calCoords(targetcore, &target_y, &target_x);
	isMsgSending = true;
    // Build the message header
    msgHdr = construct_dyn_hdr(0, outmsgleft, 0,                        // msgsize word sent.
                               self_y, self_x,
                               target_y, target_x);
	//isMsgSending = true;
    gdn_send(msgHdr);                           // Send the message header to EAST to handle fab(n - 1).
#ifdef RAWDEBUG
    raw_test_pass(0xbbbb);
    raw_test_pass(0xb000 + targetcore);             // targetcore
#endif
    while(outmsgleft-- > 0) {
      gdn_send(outmsgdata[outmsgindex++]);
#ifdef RAWDEBUG
      raw_test_pass_reg(outmsgdata[outmsgindex - 1]);
#endif
    }
#ifdef RAWDEBUG
    raw_test_pass(0xffff);
#endif
    isMsgSending = false;
#ifdef INTERRUPT
    raw_user_interrupts_off();
#endif
    // check if there are still msg hanging
    if(outmsgindex == outmsglast) {
      // no more msgs
      outmsgindex = outmsglast = 0;
      isMsgHanging = false;
    }
#ifdef INTERRUPT
    raw_user_interrupts_on();
#endif
  }
#elif defined THREADSIMULATE
// TODO
#endif
}

#ifdef RAWPROFILE
// send profile request message to targetcore
// format: 6
bool transProfileRequestMsg(int targetcore) {
  unsigned msgHdr;
  int self_y, self_x, target_y, target_x;
  // for 32 bit machine, the size is always 4 words
  int msgsize = 2;

  calCoords(corenum, &self_y, &self_x);
  calCoords(targetcore, &target_y, &target_x);
  isMsgSending = true;
  // Build the message header
  msgHdr = construct_dyn_hdr(0, msgsize, 0,             // msgsize word sent.
                             self_y, self_x,
                             target_y, target_x);
  // start sending msgs, set msg sending flag
  //isMsgSending = true;
  gdn_send(msgHdr);                     // Send the message header to EAST to handle fab(n - 1).
#ifdef RAWDEBUG
  raw_test_pass(0xbbbb);
  raw_test_pass(0xb000 + targetcore);       // targetcore
#endif
  gdn_send(6);
#ifdef RAWDEBUG
  raw_test_pass(6);
#endif
  gdn_send(totalexetime);
#ifdef RAWDEBUG
  raw_test_pass_reg(totalexetime);
  raw_test_pass(0xffff);
#endif
  // end of sending this msg, set sand msg flag false
  isMsgSending = false;
  // check if there are pending msgs
  while(isMsgHanging) {
    // get the msg from outmsgdata[]
    // length + target + msg
    outmsgleft = outmsgdata[outmsgindex++];
    targetcore = outmsgdata[outmsgindex++];
    calCoords(targetcore, &target_y, &target_x);
	isMsgSending = true;
    // Build the message header
    msgHdr = construct_dyn_hdr(0, outmsgleft, 0,                        // msgsize word sent.
                               self_y, self_x,
                               target_y, target_x);
	//isMsgSending = true;
    gdn_send(msgHdr);
#ifdef RAWDEBUG
    raw_test_pass(0xbbbb);
    raw_test_pass(0xb000 + targetcore);             // targetcore
#endif
    while(outmsgleft-- > 0) {
      gdn_send(outmsgdata[outmsgindex++]);
#ifdef RAWDEBUG
      raw_test_pass_reg(outmsgdata[outmsgindex - 1]);
#endif
    }
#ifdef RAWDEBUG
    raw_test_pass(0xffff);
#endif
    isMsgSending = false;
#ifdef INTERRUPT
    raw_user_interrupts_off();
#endif
    // check if there are still msg hanging
    if(outmsgindex == outmsglast) {
      // no more msgs
      outmsgindex = outmsglast = 0;
      isMsgHanging = false;
    }
#ifdef INTERRUPT
    raw_user_interrupts_on();
#endif
  }
  return true;
}

// output the profiling data
void outputProfileData() {
#ifdef RAWUSEIO
  FILE * fp;
  char fn[50];
  int self_y, self_x;
  char c_y, c_x;
  int i;
  int totaltasktime = 0;
  int preprocessingtime = 0;
  int objqueuecheckingtime = 0;
  int postprocessingtime = 0;
  //int interruptiontime = 0;
  int other = 0;
  int averagetasktime = 0;
  int tasknum = 0;

  for(i = 0; i < 50; i++) {
    fn[i] = 0;
  }

  calCoords(corenum, &self_y, &self_x);
  c_y = (char)self_y + '0';
  c_x = (char)self_x + '0';
  strcat(fn, "profile_");
  strcat(fn, &c_x);
  strcat(fn, "_");
  strcat(fn, &c_y);
  strcat(fn, ".rst");

  if((fp = fopen(fn, "w+")) == NULL) {
    fprintf(stderr, "fopen error\n");
    return;
  }

  fprintf(fp, "Task Name, Start Time, End Time, Duration, Exit Index(, NewObj Name, Num)+\n");
  // output task related info
  for(i = 0; i < taskInfoIndex; i++) {
    TaskInfo* tmpTInfo = taskInfoArray[i];
    int duration = tmpTInfo->endTime - tmpTInfo->startTime;
    fprintf(fp, "%s, %d, %d, %d, %d", tmpTInfo->taskName, tmpTInfo->startTime, tmpTInfo->endTime, duration, tmpTInfo->exitIndex);
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
			//fprintf(stderr, "new obj!\n");
		}

		// output all new obj info
		iter = RuntimeHashcreateiterator(nobjtbl);
		while(RunhasNext(iter)) {
			char * objtype = (char *)Runkey(iter);
			int num = Runnext(iter);
			fprintf(fp, ", %s, %d", objtype, num);
		}
	}
	fprintf(fp, "\n");
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
    fprintf(stderr, "Caution: task info overflow!\n");
  }

  other = totalexetime - totaltasktime - preprocessingtime - postprocessingtime;
  averagetasktime /= tasknum;

  fprintf(fp, "\nTotal time: %d\n", totalexetime);
  fprintf(fp, "Total task execution time: %d (%f%%)\n", totaltasktime, ((double)totaltasktime/(double)totalexetime)*100);
  fprintf(fp, "Total objqueue checking time: %d (%f%%)\n", objqueuecheckingtime, ((double)objqueuecheckingtime/(double)totalexetime)*100);
  fprintf(fp, "Total pre-processing time: %d (%f%%)\n", preprocessingtime, ((double)preprocessingtime/(double)totalexetime)*100);
  fprintf(fp, "Total post-processing time: %d (%f%%)\n", postprocessingtime, ((double)postprocessingtime/(double)totalexetime)*100);
  fprintf(fp, "Other time: %d (%f%%)\n", other, ((double)other/(double)totalexetime)*100);

  fprintf(fp, "\nAverage task execution time: %d\n", averagetasktime);

  fclose(fp);
#else
  int i = 0;
  int j = 0;

  raw_test_pass(0xdddd);
  // output task related info
  for(i= 0; i < taskInfoIndex; i++) {
    TaskInfo* tmpTInfo = taskInfoArray[i];
    char* tmpName = tmpTInfo->taskName;
    int nameLen = strlen(tmpName);
    raw_test_pass(0xddda);
    for(j = 0; j < nameLen; j++) {
      raw_test_pass_reg(tmpName[j]);
    }
    raw_test_pass(0xdddb);
    raw_test_pass_reg(tmpTInfo->startTime);
    raw_test_pass_reg(tmpTInfo->endTime);
	raw_test_pass_reg(tmpTInfo->exitIndex);
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
			raw_test_pass(0xddda);
			for(j = 0; j < nameLen; j++) {
				raw_test_pass_reg(objtype[j]);
			}
			raw_test_pass(0xdddb);
			raw_test_pass_reg(num);
		}
	}
    raw_test_pass(0xdddc);
  }

  if(taskInfoOverflow) {
    raw_test_pass(0xefee);
  }

  // output interrupt related info
  /*for(i = 0; i < interruptInfoIndex; i++) {
       InterruptInfo* tmpIInfo = interruptInfoArray[i];
       raw_test_pass(0xddde);
       raw_test_pass_reg(tmpIInfo->startTime);
       raw_test_pass_reg(tmpIInfo->endTime);
       raw_test_pass(0xdddf);
     }

     if(interruptInfoOverflow) {
       raw_test_pass(0xefef);
     }*/

  raw_test_pass(0xeeee);
#endif
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
#endif

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
#ifdef RAWDEBUG
    if(corenum < NUMCORES) {
      raw_test_pass(0xd001);
    }
#endif
    return -1;
  }
#ifdef RAWPROFILE
  /*if(isInterrupt && (!interruptInfoOverflow)) {
     // raw_test_pass(0xffff);
     interruptInfoArray[interruptInfoIndex] = RUNMALLOC_I(sizeof(struct interrupt_info));
     interruptInfoArray[interruptInfoIndex]->startTime = raw_get_cycle();
     interruptInfoArray[interruptInfoIndex]->endTime = -1;
     }*/
#endif
msg:
#ifdef RAWDEBUG
  raw_test_pass(0xcccc);
#endif
  while((gdn_input_avail() != 0) && (msgdataindex < msglength)) {
    msgdata[msgdataindex] = gdn_receive();
    if(msgdataindex == 0) {
		if(msgdata[0] > 0xc) {
			msglength = 3;
		} else if (msgdata[0] == 0xc) {
			msglength = 1;
		} else if(msgdata[0] > 8) {
			msglength = 4;
		} else if(msgdata[0] == 8) {
			msglength = 6;
		} else if(msgdata[0] > 5) {
			msglength = 2;
		} else if (msgdata[0] > 2) {
			msglength = 4;
		} else if (msgdata[0] == 2) {
			msglength = 5;
		} else if (msgdata[0] > 0) {
			msglength = 4;
		}
    } else if((msgdataindex == 1) && (msgdata[0] == 0)) {
      msglength = msgdata[msgdataindex];
    }
#ifdef RAWDEBUG
    raw_test_pass_reg(msgdata[msgdataindex]);
#endif
    msgdataindex++;
  }
#ifdef RAWDEBUG
  raw_test_pass(0xffff);
#endif
  if(msgdataindex == msglength) {
    // received a whole msg
    int type, data1;             // will receive at least 2 words including type
    type = msgdata[0];
    data1 = msgdata[1];
    switch(type) {
    case 0: {
      // receive a object transfer msg
      struct transObjInfo * transObj = RUNMALLOC_I(sizeof(struct transObjInfo));
      int k = 0;
#ifdef RAWDEBUG
	  raw_test_pass(0xe880);
#endif
      if(corenum > NUMCORES - 1) {
		  raw_test_pass_reg(msgdata[2]);
	raw_test_done(0xa001);
      } else if((corenum == STARTUPCORE) && waitconfirm) {
		  waitconfirm = false;
		  numconfirm = 0;
	  }
      // store the object and its corresponding queue info, enqueue it later
      transObj->objptr = (void *)msgdata[2];                                           // data1 is now size of the msg
      transObj->length = (msglength - 3) / 2;
      transObj->queues = RUNMALLOC_I(sizeof(int)*(msglength - 3));
      for(k = 0; k < transObj->length; ++k) {
	transObj->queues[2*k] = msgdata[3+2*k];
#ifdef RAWDEBUG
	raw_test_pass_reg(transObj->queues[2*k]);
#endif
	transObj->queues[2*k+1] = msgdata[3+2*k+1];
#ifdef RAWDEBUG
	raw_test_pass_reg(transObj->queues[2*k+1]);
#endif
      }
      // check if there is an existing duplicate item
      {
	struct QueueItem * qitem = getTail(&objqueue);
	struct QueueItem * prev = NULL;
	while(qitem != NULL) {
	  struct transObjInfo * tmpinfo = (struct transObjInfo *)(qitem->objectptr);
	  if(tmpinfo->objptr == transObj->objptr) {
	    // the same object, remove outdate one
	    removeItem(&objqueue, qitem);
	  } else {
	    prev = qitem;
	  }
	  if(prev == NULL) {
	    qitem = getTail(&objqueue);
	  } else {
	    qitem = getNextQueueItem(prev);
	  }
	}
	addNewItem_I(&objqueue, (void *)transObj);
      }
      ++(self_numreceiveobjs);
      break;
    }

    case 1: {
      // receive a stall msg
      if(corenum != STARTUPCORE) {
	// non startup core can not receive stall msg
	// return -1
	raw_test_pass_reg(data1);
	raw_test_done(0xa002);
      } else if(waitconfirm) {
		  waitconfirm = false;
		  numconfirm = 0;
	  }
      if(data1 < NUMCORES) {
#ifdef RAWDEBUG
	raw_test_pass(0xe881);
#endif
	corestatus[data1] = 0;
	numsendobjs[data1] = msgdata[2];
	numreceiveobjs[data1] = msgdata[3];
      }
      break;
    }

    case 2: {
      // receive lock request msg
      // for 32 bit machine, the size is always 4 words
      //int msgsize = sizeof(int) * 4;
      int msgsize = 4;
      // lock request msg, handle it right now
      // check to see if there is a lock exist in locktbl for the required obj
	  // data1 -> lock type
	  int data2 = msgdata[2]; // obj pointer
      int data3 = msgdata[3]; // lock
	  int data4 = msgdata[4]; // request core
      deny = false;
	  if( ((data3 >> 5) % TOTALCORE) != corenum ) {
		  // the lock should not be on this core
		  raw_test_pass_reg(data4);
		  raw_test_done(0xa003);
	  }
	  if((corenum == STARTUPCORE) && waitconfirm) {
		  waitconfirm = false;
		  numconfirm = 0;
	  }
      if(!RuntimeHashcontainskey(locktbl, data3)) {
	// no locks for this object
	// first time to operate on this shared object
	// create a lock for it
	// the lock is an integer: 0 -- stall, >0 -- read lock, -1 -- write lock
	struct LockValue * lockvalue = (struct LockValue *)(RUNMALLOC_I(sizeof(struct LockValue)));
	lockvalue->redirectlock = 0;
#ifdef RAWDEBUG
	raw_test_pass(0xe882);
#endif
	if(data1 == 0) {
		lockvalue->value = 1;
	} else {
		lockvalue->value = -1;
	}
	RuntimeHashadd_I(locktbl, data3, (int)lockvalue);
      } else {
	int rwlock_obj = 0;
	struct LockValue * lockvalue = NULL;
#ifdef RAWDEBUG
	raw_test_pass(0xe883);
#endif
	RuntimeHashget(locktbl, data3, &rwlock_obj);
	lockvalue = (struct LockValue *)(rwlock_obj);
#ifdef RAWDEBUG
	raw_test_pass_reg(lockvalue->redirectlock);
#endif
	if(lockvalue->redirectlock != 0) {
		// this lock is redirected
#ifdef RAWDEBUG
		raw_test_pass(0xe884);
#endif
		if(data1 == 0) {
			getreadlock_I_r((void *)data2, (void *)lockvalue->redirectlock, data4, true);
		} else {
			getwritelock_I_r((void *)data2, (void *)lockvalue->redirectlock, data4, true);
		}
		break;
	} else {
#ifdef RAWDEBUG
		raw_test_pass_reg(lockvalue->value);
#endif
		if(0 == lockvalue->value) {
			if(data1 == 0) {
				lockvalue->value = 1;
			} else {
				lockvalue->value = -1;
			}
		} else if((lockvalue->value > 0) && (data1 == 0)) {
		  // read lock request and there are only read locks
		  lockvalue->value++;
		} else {
		  deny = true;
		}
#ifdef RAWDEBUG
		raw_test_pass_reg(lockvalue->value);
#endif
	}
	  }
      	targetcore = data4;
      	// check if there is still some msg on sending
     	if(isMsgSending) {
#ifdef RAWDEBUG
			raw_test_pass(0xe885);
#endif
			isMsgHanging = true;
			// cache the msg in outmsgdata and send it later
			// msglength + target core + msg
			outmsgdata[outmsglast++] = msgsize;
			outmsgdata[outmsglast++] = targetcore;
			if(deny == true) {
				outmsgdata[outmsglast++] = 4;
			} else {
				outmsgdata[outmsglast++] = 3;
			}
			outmsgdata[outmsglast++] = data1;
			outmsgdata[outmsglast++] = data2;
			outmsgdata[outmsglast++] = data3;
		} else {
#ifdef RAWDEBUG
			raw_test_pass(0xe886);
#endif
			// no msg on sending, send it out
			calCoords(corenum, &self_y, &self_x);
			calCoords(targetcore, &target_y, &target_x);
			// Build the message header
			msgHdr = construct_dyn_hdr(0, msgsize, 0,                                                               // msgsize word sent.
			                           self_y, self_x,
	    		                       target_y, target_x);
			gdn_send(msgHdr);                                                               // Send the message header to EAST to handle fab(n - 1).
#ifdef RAWDEBUG
			raw_test_pass(0xbbbb);
			raw_test_pass(0xb000 + targetcore);                                                 // targetcore
#endif
			if(deny == true) {
			  // deny the lock request
			  gdn_send(4);                                                       // lock request
#ifdef RAWDEBUG
			  raw_test_pass(4);
#endif
			} else {
			  // grount the lock request
			  gdn_send(3);                                                       // lock request
#ifdef RAWDEBUG
			  raw_test_pass(3);
#endif
			}
			gdn_send(data1);                                                 // lock type
#ifdef RAWDEBUG
			raw_test_pass_reg(data1);
#endif
			gdn_send(data2);                                                 // obj pointer
#ifdef RAWDEBUG
			raw_test_pass_reg(data2);
#endif
			gdn_send(data3);                                                 // lock
#ifdef RAWDEBUG
			raw_test_pass_reg(data3);
			raw_test_pass(0xffff);
#endif
		}
      break;
    }

    case 3: {
      // receive lock grount msg
      if(corenum > NUMCORES - 1) {
		  raw_test_pass_reg(msgdata[2]);
	raw_test_done(0xa004);
      } else if((corenum == STARTUPCORE) && waitconfirm) {
		  waitconfirm = false;
		  numconfirm = 0;
	  }
      if((lockobj == msgdata[2]) && (lock2require == msgdata[3])) {
#ifdef RAWDEBUG
		  raw_test_pass(0xe887);
#endif
	lockresult = 1;
	lockflag = true;
#ifndef INTERRUPT
	reside = false;
#endif
      } else {
	// conflicts on lockresults
	raw_test_pass_reg(msgdata[2]);
	raw_test_done(0xa005);
      }
      break;
    }

    case 4: {
      // receive lock grount/deny msg
      if(corenum > NUMCORES - 1) {
		  raw_test_pass_reg(msgdata[2]);
	raw_test_done(0xa006);
      } else if((corenum == STARTUPCORE) && waitconfirm) {
		  waitconfirm = false;
		  numconfirm = 0;
	  }
      if((lockobj == msgdata[2]) && (lock2require == msgdata[3])) {
#ifdef RAWDEBUG
		  raw_test_pass(0xe888);
#endif
	lockresult = 0;
	lockflag = true;
#ifndef INTERRUPT
	reside = false;
#endif
      } else {
	// conflicts on lockresults
	raw_test_pass_reg(msgdata[2]);
	raw_test_done(0xa007);
      }
      break;
    }

    case 5: {
      // receive lock release msg
	  if((corenum == STARTUPCORE) && waitconfirm) {
		  waitconfirm = false;
		  numconfirm = 0;
	  }
      if(!RuntimeHashcontainskey(locktbl, msgdata[3])) {
	// no locks for this object, something is wrong
	raw_test_pass_reg(msgdata[3]);
	raw_test_done(0xa008);
      } else {
	int rwlock_obj = 0;
	struct LockValue * lockvalue = NULL;
	RuntimeHashget(locktbl, msgdata[3], &rwlock_obj);
	lockvalue = (struct LockValue*)(rwlock_obj);
#ifdef RAWDEBUG
	raw_test_pass(0xe889);
	raw_test_pass_reg(lockvalue->value);
#endif
	if(data1 == 0) {
	  lockvalue->value--;
	} else {
	  lockvalue->value++;
	}
#ifdef RAWDEBUG
	raw_test_pass_reg(lockvalue->value);
#endif
      }
      break;
    }

#ifdef RAWPROFILE
    case 6: {
      // receive an output request msg
      if(corenum == STARTUPCORE) {
	// startup core can not receive profile output finish msg
	raw_test_done(0xa009);
      }
#ifdef RAWDEBUG
	  raw_test_pass(0xe88a);
#endif
      {
	int msgsize = 2;
	stall = true;
	totalexetime = data1;
	outputProfileData();
	/*if(data1 >= NUMCORES) {
	   raw_test_pass_reg(taskInfoIndex);
	   raw_test_pass_reg(taskInfoOverflow);
	        if(!taskInfoOverflow) {
	                taskInfoArray[taskInfoIndex]->endTime = raw_get_cycle();
	                taskInfoIndex++;
	                if(taskInfoIndex == TASKINFOLENGTH) {
	                        taskInfoOverflow = true;
	                }
	        }
	   }*/
	// no msg on sending, send it out
	targetcore = STARTUPCORE;
	calCoords(corenum, &self_y, &self_x);
	calCoords(targetcore, &target_y, &target_x);
	// Build the message header
	msgHdr = construct_dyn_hdr(0, msgsize, 0,                                                               // msgsize word sent.
	                           self_y, self_x,
	                           target_y, target_x);
	gdn_send(msgHdr);
#ifdef RAWDEBUG
	raw_test_pass(0xbbbb);
	raw_test_pass(0xb000 + targetcore);                                                 // targetcore
#endif
	gdn_send(7);
#ifdef RAWDEBUG
	raw_test_pass(7);
#endif
	gdn_send(corenum);
#ifdef RAWDEBUG
	raw_test_pass_reg(corenum);
	raw_test_pass(0xffff);
#endif
      }
      break;
    }

    case 7: {
      // receive a profile output finish msg
      if(corenum != STARTUPCORE) {
	// non startup core can not receive profile output finish msg
	raw_test_pass_reg(data1);
	raw_test_done(0xa00a);
      }
#ifdef RAWDEBUG
	  raw_test_pass(0xe88b);
#endif
      profilestatus[data1] = 0;
      break;
    }
#endif

	case 8: {
		// receive a redirect lock request msg
		// for 32 bit machine, the size is always 4 words
      //int msgsize = sizeof(int) * 4;
      int msgsize = 4;
      // lock request msg, handle it right now
      // check to see if there is a lock exist in locktbl for the required obj
	  // data1 -> lock type
	  int data2 = msgdata[2]; // obj pointer
      int data3 = msgdata[3]; // redirect lock
	  int data4 = msgdata[4]; // root request core
	  int data5 = msgdata[5]; // request core
	  if( ((data3 >> 5) % TOTALCORE) != corenum ) {
		  // the lock should not be on this core
		  raw_test_pass_reg(data5);
		  raw_test_done(0xa00b);
	  } if((corenum == STARTUPCORE) && waitconfirm) {
		  waitconfirm = false;
		  numconfirm = 0;
	  }
      deny = false;
      if(!RuntimeHashcontainskey(locktbl, data3)) {
	// no locks for this object
	// first time to operate on this shared object
	// create a lock for it
	// the lock is an integer: 0 -- stall, >0 -- read lock, -1 -- write lock
	struct LockValue * lockvalue = (struct LockValue *)(RUNMALLOC_I(sizeof(struct LockValue)));
	lockvalue->redirectlock = 0;
#ifdef RAWDEBUG
	raw_test_pass(0xe88c);
#endif
	if(data1 == 0) {
		lockvalue->value = 1;
	} else {
		lockvalue->value = -1;
	}
	RuntimeHashadd_I(locktbl, data3, (int)lockvalue);
      } else {
	int rwlock_obj = 0;
	struct LockValue * lockvalue = NULL;
#ifdef RAWDEBUG
	raw_test_pass(0xe88d);
#endif
	RuntimeHashget(locktbl, data3, &rwlock_obj);
	lockvalue = (struct LockValue *)(rwlock_obj);
#ifdef RAWDEBUG
	raw_test_pass_reg(lockvalue->redirectlock);
#endif
	if(lockvalue->redirectlock != 0) {
		// this lock is redirected
#ifdef RAWDEBUG
		raw_test_pass(0xe88e);
#endif
		if(data1 == 0) {
			getreadlock_I_r((void *)data2, (void *)lockvalue->redirectlock, data4, true);
		} else {
			getwritelock_I_r((void *)data2, (void *)lockvalue->redirectlock, data4, true);
		}
		break;
	} else {
#ifdef RAWDEBUG
		raw_test_pass_reg(lockvalue->value);
#endif
		if(0 == lockvalue->value) {
			if(data1 == 0) {
				lockvalue->value = 1;
			} else {
				lockvalue->value = -1;
			}
		} else if((lockvalue->value > 0) && (data1 == 0)) {
		  // read lock request and there are only read locks
		  lockvalue->value++;
		} else {
		  deny = true;
		}
#ifdef RAWDEBUG
		raw_test_pass_reg(lockvalue->value);
#endif
	}
	  }
      	targetcore = data4;
      	// check if there is still some msg on sending
     	if(isMsgSending) {
#ifdef RAWDEBUG
			raw_test_pass(0xe88f);
#endif
			isMsgHanging = true;
			// cache the msg in outmsgdata and send it later
			// msglength + target core + msg
			outmsgdata[outmsglast++] = msgsize;
			outmsgdata[outmsglast++] = targetcore;
			if(deny == true) {
				outmsgdata[outmsglast++] = 0xa;
			} else {
				outmsgdata[outmsglast++] = 9;
			}
			outmsgdata[outmsglast++] = data1;
			outmsgdata[outmsglast++] = data2;
			outmsgdata[outmsglast++] = data3; 
		} else {
#ifdef RAWDEBUG
			raw_test_pass(0xe890);
#endif
			// no msg on sending, send it out
			calCoords(corenum, &self_y, &self_x);
			calCoords(targetcore, &target_y, &target_x);
			// Build the message header
			msgHdr = construct_dyn_hdr(0, msgsize, 0,                                                               // msgsize word sent.
			                           self_y, self_x,
	    		                       target_y, target_x);
			gdn_send(msgHdr);                                                               // Send the message header to EAST to handle fab(n - 1).
#ifdef RAWDEBUG
			raw_test_pass(0xbbbb);
			raw_test_pass(0xb000 + targetcore);                                                 // targetcore
#endif
			if(deny == true) {
			  // deny the lock request
			  gdn_send(0xa);                                                       // lock request
#ifdef RAWDEBUG
			  raw_test_pass(0xa);
#endif
			} else {
			  // grount the lock request
			  gdn_send(9);                                                       // lock request
#ifdef RAWDEBUG
			  raw_test_pass(9);
#endif
			}
			gdn_send(data1);                                                 // lock type
#ifdef RAWDEBUG
			raw_test_pass_reg(data1);
#endif
			gdn_send(data2);                                                 // obj pointer
#ifdef RAWDEBUG
			raw_test_pass_reg(data2);
#endif
			gdn_send(data3);                                                 // lock
#ifdef RAWDEBUG
			raw_test_pass_reg(data3);
			raw_test_pass(0xffff);
#endif
		}
		break;
	}

	case 9: {
		// receive a lock grant msg with redirect info
		if(corenum > NUMCORES - 1) {
	raw_test_pass_reg(msgdata[2]);
	raw_test_done(0xa00c);
      } if((corenum == STARTUPCORE) && waitconfirm) {
		  waitconfirm = false;
		  numconfirm = 0;
	  }
      if(lockobj == msgdata[2]) {
#ifdef RAWDEBUG
		  raw_test_pass(0xe891);
#endif
	lockresult = 1;
	lockflag = true;
	RuntimeHashadd_I(objRedirectLockTbl, lockobj, msgdata[3]);
#ifndef INTERRUPT
	reside = false;
#endif
      } else {
	// conflicts on lockresults
	raw_test_pass_reg(msgdata[2]);
	raw_test_done(0xa00d);
      }
		break;
	}
	
	case 0xa: {
		// receive a lock deny msg with redirect info
		if(corenum > NUMCORES - 1) {
			raw_test_pass_reg(msgdata[2]);
	raw_test_done(0xa00e);
      } if((corenum == STARTUPCORE) && waitconfirm) {
		  waitconfirm = false;
		  numconfirm = 0;
	  }
      if(lockobj == msgdata[2]) {
#ifdef RAWDEBUG
		  raw_test_pass(0xe892);
#endif
	lockresult = 0;
	lockflag = true;
	//RuntimeHashadd_I(objRedirectLockTbl, lockobj, msgdata[3]);
#ifndef INTERRUPT
	reside = false;
#endif
      } else {
	// conflicts on lockresults
	raw_test_pass_reg(msgdata[2]);
	raw_test_done(0xa00f);
      }
		break;
	}

	case 0xb: {
		// receive a lock release msg with redirect info
		if((corenum == STARTUPCORE) && waitconfirm) {
		  waitconfirm = false;
		  numconfirm = 0;
	  }
		if(!RuntimeHashcontainskey(locktbl, msgdata[2])) {
	// no locks for this object, something is wrong
	raw_test_pass_reg(msgdata[2]);
	raw_test_done(0xa010);
      } else {
	int rwlock_obj = 0;
	struct LockValue * lockvalue = NULL;
	RuntimeHashget(locktbl, msgdata[2], &rwlock_obj);
	lockvalue = (struct LockValue*)(rwlock_obj);
#ifdef RAWDEBUG
	raw_test_pass(0xe893);
	raw_test_pass_reg(lockvalue->value);
#endif
	if(data1 == 0) {
	  lockvalue->value--;
	} else {
	  lockvalue->value++;
	}
#ifdef RAWDEBUG
	raw_test_pass_reg(lockvalue->value);
#endif
	lockvalue->redirectlock = msgdata[3];
      }
		break;
	}
	
	case 0xc: {
		// receive a status confirm info
		if((corenum == STARTUPCORE) || (corenum > NUMCORES - 1)) {
	// wrong core to receive such msg
	raw_test_done(0xa011);
      } else {
		  int msgsize = 3;
		  int targetcore = STARTUPCORE;
#ifdef RAWDEBUG
	  raw_test_pass(0xe888);
#endif
		  // check if there is still some msg on sending
     	if(isMsgSending) {
#ifdef RAWDEBUG
	  raw_test_pass(0xe888);
#endif
			isMsgHanging = true;
			// cache the msg in outmsgdata and send it later
			// msglength + target core + msg
			outmsgdata[outmsglast++] = msgsize;
			outmsgdata[outmsglast++] = targetcore;
			outmsgdata[outmsglast++] = 0xd;
			if(busystatus) {
				outmsgdata[outmsglast++] = 1;
			} else {
				outmsgdata[outmsglast++] = 0;
			}
			outmsgdata[outmsglast++] = corenum;
		} else {
#ifdef RAWDEBUG
	  raw_test_pass(0xe888);
#endif
			// no msg on sending, send it out
			calCoords(corenum, &self_y, &self_x);
			calCoords(targetcore, &target_y, &target_x);
			// Build the message header
			msgHdr = construct_dyn_hdr(0, msgsize, 0,                                                               // msgsize word sent.
			                           self_y, self_x,
	    		                       target_y, target_x);
			gdn_send(msgHdr);   
#ifdef RAWDEBUG
			raw_test_pass(0xbbbb);
			raw_test_pass(0xb000 + targetcore);                                            
#endif
			gdn_send(0xd);                                                       // status report
#ifdef RAWDEBUG
			raw_test_pass(0xd);
#endif
			if(busystatus == true) {
			  // busy
			  gdn_send(1);   
#ifdef RAWDEBUG
			  raw_test_pass(1);
#endif
			} else {
			  // stall
			  gdn_send(0);
#ifdef RAWDEBUG
			  raw_test_pass(0);
#endif
			}
			gdn_send(corenum);                                                 // corenum
#ifdef RAWDEBUG
			raw_test_pass_reg(corenum);
			raw_test_pass(0xffff);
#endif
		}
      }
		break;
	}

	case 0xd: {
		// receive a status confirm info
		if(corenum != STARTUPCORE) {
	// wrong core to receive such msg
	raw_test_pass_reg(msgdata[2]);
	raw_test_done(0xa012);
      } else {
#ifdef RAWDEBUG
	  raw_test_pass(0xe888);
#endif
		  if(waitconfirm) {
			  numconfirm--;
		  }
		  corestatus[msgdata[2]] = msgdata[1];
      }
		break;
	}
	
    default:
      break;
    }
    for(msgdataindex--; msgdataindex > 0; --msgdataindex) {
      msgdata[msgdataindex] = -1;
    }
    msgtype = -1;
    msglength = 30;
#ifdef RAWDEBUG
    raw_test_pass(0xe894);
#endif
    if(gdn_input_avail() != 0) {
      goto msg;
    }
#ifdef RAWPROFILE
/*    if(isInterrupt && (!interruptInfoOverflow)) {
      interruptInfoArray[interruptInfoIndex]->endTime = raw_get_cycle();
      interruptInfoIndex++;
      if(interruptInfoIndex == INTERRUPTINFOLENGTH) {
        interruptInfoOverflow = true;
      }
    }*/
#endif
    return type;
  } else {
    // not a whole msg
#ifdef RAWDEBUG
    raw_test_pass(0xe895);
#endif
#ifdef RAWPROFILE
/*    if(isInterrupt && (!interruptInfoOverflow)) {
      interruptInfoArray[interruptInfoIndex]->endTime = raw_get_cycle();
      interruptInfoIndex++;
      if(interruptInfoIndex == INTERRUPTINFOLENGTH) {
        interruptInfoOverflow = true;
      }
    }*/
#endif
    return -2;
  }
#elif defined THREADSIMULATE
  int numofcore = pthread_getspecific(key);
  // use POSIX message queue to transfer object
  int msglen = 0;
  struct mq_attr mqattr;
  mq_getattr(mqd[numofcore], &mqattr);
  void * msgptr =RUNMALLOC(mqattr.mq_msgsize);
  msglen=mq_receive(mqd[numofcore], msgptr, mqattr.mq_msgsize, NULL);       // receive the object into the queue
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
  } else {
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
  int targetcore = 0;
  // for 32 bit machine, the size is always 5 words
  int msgsize = 5;

  lockobj = (int)ptr;
  if(((struct ___Object___ *)ptr)->lock == NULL) {
	lock2require = lockobj;
  } else {
	lock2require = (int)(((struct ___Object___ *)ptr)->lock);
  }
  targetcore = (lock2require >> 5) % TOTALCORE;
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
    if(!RuntimeHashcontainskey(locktbl, lock2require)) {
      // no locks for this object
      // first time to operate on this shared object
      // create a lock for it
      // the lock is an integer: 0 -- stall, >0 -- read lock, -1 -- write lock
	  struct LockValue * lockvalue = (struct LockValue *)(RUNMALLOC_I(sizeof(struct LockValue)));
	  lockvalue->redirectlock = 0;
	  lockvalue->value = 1;
      RuntimeHashadd_I(locktbl, lock2require, (int)lockvalue);
    } else {
      int rwlock_obj = 0;
	  struct LockValue* lockvalue;
      RuntimeHashget(locktbl, lock2require, &rwlock_obj);
	  lockvalue = (struct LockValue*)rwlock_obj;
	  if(lockvalue->redirectlock != 0) {
		  // the lock is redirected
		  getreadlock_I_r(ptr, (void *)lockvalue->redirectlock, corenum, false);
		  return true;
	  } else {
	      if(-1 != lockvalue->value) {
			  lockvalue->value++;
		  } else {
			  deny = true;
		  }
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
      raw_test_done(0xa013);
    }
    return true;
  }

  calCoords(corenum, &self_y, &self_x);
  calCoords(targetcore, &target_y, &target_x);
  isMsgSending = true;
  // Build the message header
  msgHdr = construct_dyn_hdr(0, msgsize, 0,             // msgsize word sent.
                             self_y, self_x,
                             target_y, target_x);
  // start sending the msg, set send msg flag
  //isMsgSending = true;
  gdn_send(msgHdr);                     // Send the message header to EAST to handle fab(n - 1).
#ifdef RAWDEBUG
  raw_test_pass(0xbbbb);
  raw_test_pass(0xb000 + targetcore);       // targetcore
#endif
  gdn_send(2);   // lock request
#ifdef RAWDEBUG
  raw_test_pass(2);
#endif
  gdn_send(0);       // read lock
#ifdef RAWDEBUG
  raw_test_pass(0);
#endif
  gdn_send((int)ptr);  // obj pointer
#ifdef RAWDEBUG
  raw_test_pass_reg(ptr);
#endif
  gdn_send(lock2require); // lock
#ifdef RAWDEBUG
  raw_test_pass_reg(lock2require);
#endif
  gdn_send(corenum);  // request core
#ifdef RAWDEBUG
  raw_test_pass_reg(corenum);
  raw_test_pass(0xffff);
#endif
  // end of sending this msg, set sand msg flag false
  isMsgSending = false;
  // check if there are pending msgs
  while(isMsgHanging) {
    // get the msg from outmsgdata[]
    // length + target + msg
    outmsgleft = outmsgdata[outmsgindex++];
    targetcore = outmsgdata[outmsgindex++];
    calCoords(targetcore, &target_y, &target_x);
	isMsgSending = true;
    // Build the message header
    msgHdr = construct_dyn_hdr(0, outmsgleft, 0,                        // msgsize word sent.
                               self_y, self_x,
                               target_y, target_x);
	//isMsgSending = true;
    gdn_send(msgHdr);                           // Send the message header to EAST to handle fab(n - 1).
#ifdef RAWDEBUG
    raw_test_pass(0xbbbb);
    raw_test_pass(0xb000 + targetcore);             // targetcore
#endif
    while(outmsgleft-- > 0) {
      gdn_send(outmsgdata[outmsgindex++]);
#ifdef RAWDEBUG
      raw_test_pass_reg(outmsgdata[outmsgindex - 1]);
#endif
    }
#ifdef RAWDEBUG
    raw_test_pass(0xffff);
#endif
    isMsgSending = false;
#ifdef INTERRUPT
    raw_user_interrupts_off();
#endif
    // check if there are still msg hanging
    if(outmsgindex == outmsglast) {
      // no more msgs
      outmsgindex = outmsglast = 0;
      isMsgHanging = false;
    }
#ifdef INTERRUPT
    raw_user_interrupts_on();
#endif
  }
  return true;
#elif defined THREADSIMULATE
  // TODO : need modification for alias lock
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
  int targetcore = 0;
  // for 32 bit machine, the size is always 4 words
  int msgsize = 4;

  int reallock = 0;
  if(((struct ___Object___ *)ptr)->lock == NULL) {
	reallock = (int)ptr;
  } else {
	reallock = (int)(((struct ___Object___ *)ptr)->lock);
  }
  targetcore = (reallock >> 5) % TOTALCORE;

  if(targetcore == corenum) {
#ifdef INTERRUPT
    raw_user_interrupts_off();
#endif
    // reside on this core
    if(!RuntimeHashcontainskey(locktbl, reallock)) {
      // no locks for this object, something is wrong
      raw_test_done(0xa014);
    } else {
      int rwlock_obj = 0;
	  struct LockValue * lockvalue = NULL;
      RuntimeHashget(locktbl, reallock, &rwlock_obj);
	  lockvalue = (struct LockValue *)rwlock_obj;
      lockvalue->value--;
    }
#ifdef INTERRUPT
    raw_user_interrupts_on();
#endif
    return;
  }

  calCoords(corenum, &self_y, &self_x);
  calCoords(targetcore, &target_y, &target_x);
  isMsgSending = true;
  // Build the message header
  msgHdr = construct_dyn_hdr(0, msgsize, 0,             // msgsize word sent.
                             self_y, self_x,
                             target_y, target_x);
  // start sending the msg, set send msg flag
  //isMsgSending = true;
  gdn_send(msgHdr);                     // Send the message header to EAST to handle fab(n - 1).
#ifdef RAWDEBUG
  raw_test_pass(0xbbbb);
  raw_test_pass(0xb000 + targetcore);       // targetcore
#endif
  gdn_send(5);   // lock release
#ifdef RAWDEBUG
  raw_test_pass(5);
#endif
  gdn_send(0);       // read lock
#ifdef RAWDEBUG
  raw_test_pass(0);
#endif
  gdn_send((int)ptr);       // obj pointer
#ifdef RAWDEBUG
  raw_test_pass_reg(ptr);
#endif
  gdn_send(reallock);  // lock
#ifdef RAWDEBUG
  raw_test_pass_reg(reallock);
  raw_test_pass(0xffff);
#endif
  // end of sending this msg, set sand msg flag false
  isMsgSending = false;
  // check if there are pending msgs
  while(isMsgHanging) {
    // get the msg from outmsgdata[]
    // length + target + msg
    outmsgleft = outmsgdata[outmsgindex++];
    targetcore = outmsgdata[outmsgindex++];
    calCoords(targetcore, &target_y, &target_x);
	isMsgSending = true;
    // Build the message header
    msgHdr = construct_dyn_hdr(0, outmsgleft, 0,                        // msgsize word sent.
                               self_y, self_x,
                               target_y, target_x);
	//isMsgSending = true;
    gdn_send(msgHdr);                           // Send the message header to EAST to handle fab(n - 1).
#ifdef RAWDEBUG
    raw_test_pass(0xbbbb);
    raw_test_pass(0xb000 + targetcore);             // targetcore
#endif
    while(outmsgleft-- > 0) {
      gdn_send(outmsgdata[outmsgindex++]);
#ifdef RAWDEBUG
      raw_test_pass_reg(outmsgdata[outmsgindex - 1]);
#endif
    }
#ifdef RAWDEBUG
    raw_test_pass(0xffff);
#endif
    isMsgSending = false;
#ifdef INTERRUPT
    raw_user_interrupts_off();
#endif
    // check if there are still msg hanging
    if(outmsgindex == outmsglast) {
      // no more msgs
      outmsgindex = outmsglast = 0;
      isMsgHanging = false;
    }
#ifdef INTERRUPT
    raw_user_interrupts_on();
#endif
  }
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
// redirected lock request
bool getreadlock_I_r(void * ptr, void * redirectlock, int core, bool cache) {
  unsigned msgHdr;
  int self_y, self_x, target_y, target_x;
  int targetcore = 0;
  // for 32 bit machine, the size is always 6 words
  int msgsize = 6;

  if(core == corenum) {
	  lockobj = (int)ptr;
	  lock2require = (int)redirectlock;
	  lockflag = false;
#ifndef INTERRUPT
	  reside = false;
#endif
	  lockresult = 0;
  }  
  targetcore = ((int)redirectlock >> 5) % TOTALCORE;
  
  if(targetcore == corenum) {
    // reside on this core
    bool deny = false;
    if(!RuntimeHashcontainskey(locktbl, (int)redirectlock)) {
      // no locks for this object
      // first time to operate on this shared object
      // create a lock for it
      // the lock is an integer: 0 -- stall, >0 -- read lock, -1 -- write lock
	  struct LockValue * lockvalue = (struct LockValue *)(RUNMALLOC_I(sizeof(struct LockValue)));
	  lockvalue->redirectlock = 0;
	  lockvalue->value = 1;
      RuntimeHashadd_I(locktbl, (int)redirectlock, (int)lockvalue);
    } else {
      int rwlock_obj = 0;
	  struct LockValue* lockvalue;
      RuntimeHashget(locktbl, (int)redirectlock, &rwlock_obj);
	  lockvalue = (struct LockValue*)rwlock_obj;
	  if(lockvalue->redirectlock != 0) {
		  // the lock is redirected
		  getreadlock_I_r(ptr, (void *)lockvalue->redirectlock, core, cache);
		  return true;
	  } else {
		  if(-1 != lockvalue->value) {
			  lockvalue->value++;
		  } else {
			  deny = true;
		  }
	  }
    }
	if(core == corenum) {
    	if(lockobj == (int)ptr) {
	      if(deny) {
		lockresult = 0;
	      } else {
		lockresult = 1;
		RuntimeHashadd_I(objRedirectLockTbl, (int)ptr, (int)redirectlock);
	      }
    	  lockflag = true;
#ifndef INTERRUPT
	      reside = true;
#endif
    	} else {
	      // conflicts on lockresults
    	  raw_test_done(0xa015);
    	}
	    return true;
	} else {
		// send lock grant/deny request to the root requiring core
		// check if there is still some msg on sending
		int msgsize1 = 4;
		if((!cache) || (cache && !isMsgSending)) {
			calCoords(corenum, &self_y, &self_x);
			calCoords(core, &target_y, &target_x);
			// Build the message header
			msgHdr = construct_dyn_hdr(0, msgsize1, 0,             // msgsize word sent.
					                   self_y, self_x,
									   target_y, target_x);
			gdn_send(msgHdr);                     // Send the message header to EAST to handle fab(n - 1).
#ifdef RAWDEBUG
			raw_test_pass(0xbbbb);
			raw_test_pass(0xb000 + core);       // targetcore
#endif
			if(deny) {
				// deny
				gdn_send(0xa);   // lock deny with redirected info
#ifdef RAWDEBUG
				raw_test_pass(0xa);
#endif
			} else {
				// grant
				gdn_send(9);   // lock grant with redirected info
#ifdef RAWDEBUG
				raw_test_pass(9);
#endif
			}
			gdn_send(0);       // read lock
#ifdef RAWDEBUG
			raw_test_pass(0);
#endif
			gdn_send((int)ptr);  // obj pointer
#ifdef RAWDEBUG
			raw_test_pass_reg(ptr);
#endif
			gdn_send((int)redirectlock); // redirected lock
#ifdef RAWDEBUG
			raw_test_pass_reg((int)redirectlock);
			raw_test_pass(0xffff);
#endif
		} else if(cache && isMsgSending) {
			isMsgHanging = true;
			// cache the msg in outmsgdata and send it later
			// msglength + target core + msg
			outmsgdata[outmsglast++] = msgsize1;
			outmsgdata[outmsglast++] = core;
			if(deny) {
				outmsgdata[outmsglast++] = 0xa; // deny
			} else {
				outmsgdata[outmsglast++] = 9; // grant
			}
			outmsgdata[outmsglast++] = 0;
			outmsgdata[outmsglast++] = (int)ptr;
			outmsgdata[outmsglast++] = (int)redirectlock;
		}
	}
  }

  // check if there is still some msg on sending
  if((!cache) || (cache && !isMsgSending)) {
	  calCoords(corenum, &self_y, &self_x);
	  calCoords(targetcore, &target_y, &target_x);
	  // Build the message header
	  msgHdr = construct_dyn_hdr(0, msgsize, 0,             // msgsize word sent.
    	                         self_y, self_x,
        	                     target_y, target_x);
	  gdn_send(msgHdr);                     // Send the message header to EAST to handle fab(n - 1).
#ifdef RAWDEBUG
	  raw_test_pass(0xbbbb);
	  raw_test_pass(0xb000 + targetcore);       // targetcore
#endif
	  gdn_send(8);   // redirected lock request
#ifdef RAWDEBUG
	  raw_test_pass(8);
#endif
	  gdn_send(0);       // read lock
#ifdef RAWDEBUG
	  raw_test_pass(0);
#endif
	  gdn_send((int)ptr);  // obj pointer
#ifdef RAWDEBUG
	  raw_test_pass_reg(ptr);
#endif
	  gdn_send(lock2require); // redirected lock
#ifdef RAWDEBUG
	  raw_test_pass_reg(lock2require);
#endif
	  gdn_send(core);  // root request core
#ifdef RAWDEBUG
	  raw_test_pass_reg(core);
#endif
	  gdn_send(corenum);  // request core
#ifdef RAWDEBUG
	  raw_test_pass_reg(corenum);
	  raw_test_pass(0xffff);
#endif
  } else if(cache && isMsgSending) {
	  isMsgHanging = true;
	  // cache the msg in outmsgdata and send it later
	  // msglength + target core + msg
	  outmsgdata[outmsglast++] = msgsize;
	  outmsgdata[outmsglast++] = targetcore;
	  outmsgdata[outmsglast++] = 8;
	  outmsgdata[outmsglast++] = 0;
	  outmsgdata[outmsglast++] = (int)ptr;
	  outmsgdata[outmsglast++] = lock2require;
	  outmsgdata[outmsglast++] = core;
	  outmsgdata[outmsglast++] = corenum;
  }
  return true;
}
#endif

// not reentrant
bool getwritelock(void * ptr) {
#ifdef RAW
  unsigned msgHdr;
  int self_y, self_x, target_y, target_x;
  int targetcore = 0;
  // for 32 bit machine, the size is always 5 words
  int msgsize = 5;

  lockobj = (int)ptr;
  if(((struct ___Object___ *)ptr)->lock == NULL) {
	lock2require = lockobj;
  } else {
	lock2require = (int)(((struct ___Object___ *)ptr)->lock);
  }
  targetcore = (lock2require >> 5) % TOTALCORE;
  lockflag = false;
#ifndef INTERRUPT
  reside = false;
#endif
  lockresult = 0;

#ifdef RAWDEBUG
  raw_test_pass(0xe551);
  raw_test_pass_reg(lockobj);
  raw_test_pass_reg(lock2require);
  raw_test_pass_reg(targetcore);
#endif

  if(targetcore == corenum) {
    // reside on this core
    bool deny = false;
#ifdef INTERRUPT
    raw_user_interrupts_off();
#endif
    if(!RuntimeHashcontainskey(locktbl, lock2require)) {
      // no locks for this object
      // first time to operate on this shared object
      // create a lock for it
      // the lock is an integer: 0 -- stall, >0 -- read lock, -1 -- write lock
#ifdef RAWDEBUG
      raw_test_pass(0xe552);
#endif
	  struct LockValue * lockvalue = (struct LockValue *)(RUNMALLOC_I(sizeof(struct LockValue)));
	  lockvalue->redirectlock = 0;
	  lockvalue->value = -1;
      RuntimeHashadd_I(locktbl, lock2require, (int)lockvalue);
    } else {
      int rwlock_obj = 0;
	  struct LockValue* lockvalue;
      RuntimeHashget(locktbl, (int)ptr, &rwlock_obj);
	  lockvalue = (struct LockValue*)rwlock_obj;
#ifdef RAWDEBUG
      raw_test_pass(0xe553);
      raw_test_pass_reg(lockvalue->value);
#endif
	  if(lockvalue->redirectlock != 0) {
		  // the lock is redirected
#ifdef  RAWDEBUG
		  raw_test_pass(0xe554);
#endif
		  getwritelock_I_r(ptr, (void *)lockvalue->redirectlock, corenum, false);
		  return true;
	  } else {
	      if(0 == lockvalue->value) {
			  lockvalue->value = -1;
		  } else {
			  deny = true;
		  }
	  }
    }
#ifdef INTERRUPT
    raw_user_interrupts_on();
#endif
#ifdef RAWDEBUG
    raw_test_pass(0xe555);
    raw_test_pass_reg(lockresult);
#endif
    if(lockobj == (int)ptr) {
      if(deny) {
	lockresult = 0;
#ifdef RAWDEBUG
	raw_test_pass(0);
#endif
      } else {
	lockresult = 1;
#ifdef RAWDEBUG
	raw_test_pass(1);
#endif
      }
      lockflag = true;
#ifndef INTERRUPT
      reside = true;
#endif
    } else {
      // conflicts on lockresults
      raw_test_done(0xa016);
    }
    return true;
  }

#ifdef RAWDEBUG
  raw_test_pass(0xe556);
#endif
  calCoords(corenum, &self_y, &self_x);
  calCoords(targetcore, &target_y, &target_x);
#ifdef RAWDEBUG
  raw_test_pass_reg(self_y);
  raw_test_pass_reg(self_x);
  raw_test_pass_reg(target_y);
  raw_test_pass_reg(target_x);
#endif
  isMsgSending = true;
  // Build the message header
  msgHdr = construct_dyn_hdr(0, msgsize, 0,             // msgsize word sent.
                             self_y, self_x,
                             target_y, target_x);
  // start sending the msg, set send msg flag
  //isMsgSending = true;
  gdn_send(msgHdr);                     // Send the message header to EAST to handle fab(n - 1).
#ifdef RAWDEBUG
  raw_test_pass(0xbbbb);
  raw_test_pass(0xb000 + targetcore);       // targetcore
#endif
  gdn_send(2);   // lock request
#ifdef RAWDEBUG
  raw_test_pass(2);
#endif
  gdn_send(1);       // write lock
#ifdef RAWDEBUG
  raw_test_pass(1);
#endif
  gdn_send((int)ptr);  // obj pointer
#ifdef RAWDEBUG
  raw_test_pass_reg(ptr);
#endif
  gdn_send(lock2require); // lock
#ifdef RAWDEBUG
  raw_test_pass_reg(lock2require);
#endif
  gdn_send(corenum);  // request core
#ifdef RAWDEBUG
  raw_test_pass_reg(corenum);
  raw_test_pass(0xffff);
#endif
  // end of sending this msg, set sand msg flag false
  isMsgSending = false;
  // check if there are pending msgs
  while(isMsgHanging) {
    // get the msg from outmsgdata[]
    // length + target + msg
    outmsgleft = outmsgdata[outmsgindex++];
    targetcore = outmsgdata[outmsgindex++];
    calCoords(targetcore, &target_y, &target_x);
	isMsgSending = true;
    // Build the message header
    msgHdr = construct_dyn_hdr(0, outmsgleft, 0,                        // msgsize word sent.
                               self_y, self_x,
                               target_y, target_x);
	//isMsgSending = true;
    gdn_send(msgHdr);                           // Send the message header to EAST to handle fab(n - 1).
#ifdef RAWDEBUG
	raw_test_pass(0xe557);
    raw_test_pass(0xbbbb);
    raw_test_pass(0xb000 + targetcore);             // targetcore
#endif
    while(outmsgleft-- > 0) {
      gdn_send(outmsgdata[outmsgindex++]);
#ifdef RAWDEBUG
      raw_test_pass_reg(outmsgdata[outmsgindex - 1]);
#endif
    }
#ifdef RAWDEBUG
    raw_test_pass(0xffff);
#endif
    isMsgSending = false;
#ifdef INTERRUPT
    raw_user_interrupts_off();
#endif
    // check if there are still msg hanging
    if(outmsgindex == outmsglast) {
      // no more msgs
      outmsgindex = outmsglast = 0;
      isMsgHanging = false;
    }
#ifdef INTERRUPT
    raw_user_interrupts_on();
#endif
  }
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
  int targetcore = 0;
  // for 32 bit machine, the size is always 4 words
  int msgsize = 4;

  int reallock = 0;
  if(((struct ___Object___ *)ptr)->lock == NULL) {
	reallock = (int)ptr;
  } else {
	reallock = (int)(((struct ___Object___ *)ptr)->lock);
  }
  targetcore = (reallock >> 5) % TOTALCORE;

#ifdef RAWDEBUG
  raw_test_pass(0xe661);
  raw_test_pass_reg((int)ptr);
  raw_test_pass_reg(reallock);
  raw_test_pass_reg(targetcore);
#endif

  if(targetcore == corenum) {
#ifdef INTERRUPT
    raw_user_interrupts_off();
#endif
    // reside on this core
    if(!RuntimeHashcontainskey(locktbl, reallock)) {
      // no locks for this object, something is wrong
      raw_test_done(0xa017);
    } else {
      int rwlock_obj = 0;
	  struct LockValue * lockvalue = NULL;
#ifdef RAWDEBUG
      raw_test_pass(0xe662);
#endif
      RuntimeHashget(locktbl, reallock, &rwlock_obj);
	  lockvalue = (struct LockValue *)rwlock_obj;
#ifdef RAWDEBUG
      raw_test_pass_reg(lockvalue->value);
#endif
      lockvalue->value++;
#ifdef RAWDEBUG
      raw_test_pass_reg(lockvalue->value);
#endif
    }
#ifdef INTERRUPT
    raw_user_interrupts_on();
#endif
    return;
  }

#ifdef RAWDEBUG
  raw_test_pass(0xe663);
#endif
  calCoords(corenum, &self_y, &self_x);
  calCoords(targetcore, &target_y, &target_x);
  isMsgSending = true;
  // Build the message header
  msgHdr = construct_dyn_hdr(0, msgsize, 0,             // msgsize word sent.
                             self_y, self_x,
                             target_y, target_x);
  // start sending the msg, set send msg flag
  //isMsgSending = true;
  gdn_send(msgHdr);                     // Send the message header to EAST to handle fab(n - 1).
#ifdef RAWDEBUG
  raw_test_pass(0xbbbb);
  raw_test_pass(0xb000 + targetcore);
#endif
  gdn_send(5);   // lock release
 #ifdef RAWDEBUG
  raw_test_pass(5);
#endif
  gdn_send(1);       // write lock
#ifdef RAWDEBUG
  raw_test_pass(1);
#endif
  gdn_send((int)ptr);  // obj pointer
#ifdef RAWDEBUG
  raw_test_pass_reg(ptr);
#endif
  gdn_send(reallock);  // lock
#ifdef RAWDEBUG
  raw_test_pass_reg(reallock);
  raw_test_pass(0xffff);
#endif
  // end of sending this msg, set sand msg flag false
  isMsgSending = false;
  // check if there are pending msgs
  while(isMsgHanging) {
    // get the msg from outmsgdata[]
    // length + target + msg
    outmsgleft = outmsgdata[outmsgindex++];
    targetcore = outmsgdata[outmsgindex++];
    calCoords(targetcore, &target_y, &target_x);
	isMsgSending = true;
    // Build the message header
    msgHdr = construct_dyn_hdr(0, outmsgleft, 0,                        // msgsize word sent.
                               self_y, self_x,
                               target_y, target_x);
	//isMsgSending = true;
    gdn_send(msgHdr);                           // Send the message header to EAST to handle fab(n - 1).
#ifdef RAWDEBUG
    raw_test_pass(0xbbbb);
    raw_test_pass(0xb000 + targetcore);             // targetcore
#endif
    while(outmsgleft-- > 0) {
      gdn_send(outmsgdata[outmsgindex++]);
#ifdef RAWDEBUG
      raw_test_pass_reg(outmsgdata[outmsgindex - 1]);
#endif
    }
#ifdef RAWDEBUG
    raw_test_pass(0xffff);
#endif
    isMsgSending = false;
#ifdef INTERRUPT
    raw_user_interrupts_off();
#endif
    // check if there are still msg hanging
    if(outmsgindex == outmsglast) {
      // no more msgs
      outmsgindex = outmsglast = 0;
      isMsgHanging = false;
    }
#ifdef INTERRUPT
    raw_user_interrupts_on();
#endif
  }
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

void releasewritelock_r(void * lock, void * redirectlock) {
#ifdef RAW
  unsigned msgHdr;
  int self_y, self_x, target_y, target_x;
  int targetcore = 0;
  // for 32 bit machine, the size is always 4 words
  int msgsize = 4;

  int reallock = (int)lock;
  targetcore = (reallock >> 5) % TOTALCORE;

#ifdef RAWDEBUG
  raw_test_pass(0xe671);
  raw_test_pass_reg((int)lock);
  raw_test_pass_reg(reallock);
  raw_test_pass_reg(targetcore);
#endif

  if(targetcore == corenum) {
#ifdef INTERRUPT
    raw_user_interrupts_off();
#endif
    // reside on this core
    if(!RuntimeHashcontainskey(locktbl, reallock)) {
      // no locks for this object, something is wrong
      raw_test_done(0xa018);
    } else {
      int rwlock_obj = 0;
	  struct LockValue * lockvalue = NULL;
#ifdef RAWDEBUG
      raw_test_pass(0xe672);
#endif
      RuntimeHashget(locktbl, reallock, &rwlock_obj);
	  lockvalue = (struct LockValue *)rwlock_obj;
#ifdef RAWDEBUG
      raw_test_pass_reg(lockvalue->value);
#endif
      lockvalue->value++;
	  lockvalue->redirectlock = (int)redirectlock;
#ifdef RAWDEBUG
      raw_test_pass_reg(lockvalue->value);
#endif
    }
#ifdef INTERRUPT
    raw_user_interrupts_on();
#endif
    return;
  }

#ifdef RAWDEBUG
  raw_test_pass(0xe673);
#endif
  calCoords(corenum, &self_y, &self_x);
  calCoords(targetcore, &target_y, &target_x);
  isMsgSending = true;
  // Build the message header
  msgHdr = construct_dyn_hdr(0, msgsize, 0,             // msgsize word sent.
                             self_y, self_x,
                             target_y, target_x);
  // start sending the msg, set send msg flag
  //isMsgSending = true;
  gdn_send(msgHdr);                     
#ifdef RAWDEBUG
  raw_test_pass(0xbbbb);
  raw_test_pass(0xb000 + targetcore);
#endif
  gdn_send(0xb);   // lock release with redirect info
#ifdef RAWDEBUG
  raw_test_pass(0xb);
#endif
  gdn_send(1);       // write lock
#ifdef RAWDEBUG
  raw_test_pass(1);
#endif
  gdn_send((int)lock); // lock
#ifdef RAWDEBUG
  raw_test_pass_reg(lock);
#endif
  gdn_send((int)redirectlock); // redirect lock
#ifdef RAWDEBUG
  raw_test_pass_reg(redirectlock);
  raw_test_pass(0xffff);
#endif
  // end of sending this msg, set sand msg flag false
  isMsgSending = false;
  // check if there are pending msgs
  while(isMsgHanging) {
    // get the msg from outmsgdata[]
    // length + target + msg
    outmsgleft = outmsgdata[outmsgindex++];
    targetcore = outmsgdata[outmsgindex++];
    calCoords(targetcore, &target_y, &target_x);
	isMsgSending = true;
    // Build the message header
    msgHdr = construct_dyn_hdr(0, outmsgleft, 0,                        // msgsize word sent.
                               self_y, self_x,
                               target_y, target_x);
	//isMsgSending = true;
    gdn_send(msgHdr);                           // Send the message header to EAST to handle fab(n - 1).
#ifdef RAWDEBUG
    raw_test_pass(0xbbbb);
    raw_test_pass(0xb000 + targetcore);             // targetcore
#endif
    while(outmsgleft-- > 0) {
      gdn_send(outmsgdata[outmsgindex++]);
#ifdef RAWDEBUG
      raw_test_pass_reg(outmsgdata[outmsgindex - 1]);
#endif
    }
#ifdef RAWDEBUG
    raw_test_pass(0xffff);
#endif
    isMsgSending = false;
#ifdef INTERRUPT
    raw_user_interrupts_off();
#endif
    // check if there are still msg hanging
    if(outmsgindex == outmsglast) {
      // no more msgs
      outmsgindex = outmsglast = 0;
      isMsgHanging = false;
    }
#ifdef INTERRUPT
    raw_user_interrupts_on();
#endif
  }
#elif defined THREADSIMULATE
  // TODO, need modification according to alias lock
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

#ifdef RAW
bool getwritelock_I(void * ptr) {
  unsigned msgHdr;
  int self_y, self_x, target_y, target_x;
  int targetcore = 0;
  // for 32 bit machine, the size is always 5 words
  int msgsize = 5;

  lockobj = (int)ptr;
  if(((struct ___Object___ *)ptr)->lock == NULL) {
	lock2require = lockobj;
  } else {
	lock2require = (int)(((struct ___Object___ *)ptr)->lock);
  }
  targetcore = (lock2require >> 5) % TOTALCORE;
  lockflag = false;
#ifndef INTERRUPT
  reside = false;
#endif
  lockresult = 0;

#ifdef RAWDEBUG
  raw_test_pass(0xe561);
  raw_test_pass_reg(lockobj);
  raw_test_pass_reg(lock2require);
  raw_test_pass_reg(targetcore);
#endif

  if(targetcore == corenum) {
    // reside on this core
    bool deny = false;
    if(!RuntimeHashcontainskey(locktbl, lock2require)) {
      // no locks for this object
      // first time to operate on this shared object
      // create a lock for it
      // the lock is an integer: 0 -- stall, >0 -- read lock, -1 -- write lock
#ifdef RAWDEBUG
      raw_test_pass(0xe562);
#endif
	  struct LockValue * lockvalue = (struct LockValue *)(RUNMALLOC_I(sizeof(struct LockValue)));
	  lockvalue->redirectlock = 0;
	  lockvalue->value = -1;
      RuntimeHashadd_I(locktbl, lock2require, (int)lockvalue);
    } else {
      int rwlock_obj = 0;
	  struct LockValue* lockvalue;
      RuntimeHashget(locktbl, (int)ptr, &rwlock_obj);
	  lockvalue = (struct LockValue*)rwlock_obj;
#ifdef RAWDEBUG
      raw_test_pass(0xe563);
      raw_test_pass_reg(lockvalue->value);
#endif
	  if(lockvalue->redirectlock != 0) {
		  // the lock is redirected
#ifdef RAWDEBUG
		  raw_test_pass(0xe564);
#endif
		  getwritelock_I_r(ptr, (void *)lockvalue->redirectlock, corenum, false);
		  return true;
	  } else {
	      if(0 == lockvalue->value) {
			  lockvalue->value = -1;
		  } else {
			  deny = true;
		  }
	  }
    }
#ifdef RAWDEBUG
    raw_test_pass(0xe565);
    raw_test_pass_reg(lockresult);
#endif
    if(lockobj == (int)ptr) {
      if(deny) {
	lockresult = 0;
#ifdef RAWDEBUG
	raw_test_pass(0);
#endif
      } else {
	lockresult = 1;
#ifdef RAWDEBUG
	raw_test_pass(1);
#endif
      }
      lockflag = true;
#ifndef INTERRUPT
      reside = true;
#endif
    } else {
      // conflicts on lockresults
      raw_test_done(0xa019);
    }
    return true;
  }

#ifdef RAWDEBUG
  raw_test_pass(0xe566);
#endif
  calCoords(corenum, &self_y, &self_x);
  calCoords(targetcore, &target_y, &target_x);
  // Build the message header
  msgHdr = construct_dyn_hdr(0, msgsize, 0,             // msgsize word sent.
                             self_y, self_x,
                             target_y, target_x);
  // start sending the msg, set send msg flag
  gdn_send(msgHdr);                     // Send the message header to EAST to handle fab(n - 1).
#ifdef RAWDEBUG
  raw_test_pass(0xbbbb);
  raw_test_pass(0xb000 + targetcore);       // targetcore
#endif
  gdn_send(2);   // lock request
#ifdef RAWDEBUG
  raw_test_pass(2);
#endif
  gdn_send(1);       // write lock
#ifdef RAWDEBUG
  raw_test_pass(1);
#endif
  gdn_send((int)ptr);  // obj pointer
#ifdef RAWDEBUG
  raw_test_pass_reg(ptr);
#endif
  gdn_send(lock2require); // lock
#ifdef RAWDEBUG
  raw_test_pass_reg(lock2require);
#endif
  gdn_send(corenum);  // request core
#ifdef RAWDEBUG
  raw_test_pass_reg(corenum);
  raw_test_pass(0xffff);
#endif
  return true;
}

// redirected lock request
bool getwritelock_I_r(void * ptr, void * redirectlock, int core, bool cache) {
  unsigned msgHdr;
  int self_y, self_x, target_y, target_x;
  int targetcore = 0;
  // for 32 bit machine, the size is always 6 words
  int msgsize = 6;

  if(core == corenum) {
	  lockobj = (int)ptr;
	  lock2require = (int)redirectlock;
	  lockflag = false;
#ifndef INTERRUPT
	  reside = false;
#endif
	  lockresult = 0;
  }
  targetcore = ((int)redirectlock >> 5) % TOTALCORE;

#ifdef RAWDEBUG
  raw_test_pass(0xe571);
  raw_test_pass_reg((int)ptr);
  raw_test_pass_reg((int)redirectlock);
  raw_test_pass_reg(core);
  raw_test_pass_reg((int)cache);
  raw_test_pass_reg(targetcore);
#endif


  if(targetcore == corenum) {
    // reside on this core
    bool deny = false;
    if(!RuntimeHashcontainskey(locktbl, (int)redirectlock)) {
      // no locks for this object
      // first time to operate on this shared object
      // create a lock for it
      // the lock is an integer: 0 -- stall, >0 -- read lock, -1 -- write lock
	  struct LockValue * lockvalue = (struct LockValue *)(RUNMALLOC_I(sizeof(struct LockValue)));
	  lockvalue->redirectlock = 0;
	  lockvalue->value = -1;
      RuntimeHashadd_I(locktbl, (int)redirectlock, (int)lockvalue);
    } else {
      int rwlock_obj = 0;
	  struct LockValue* lockvalue;
      RuntimeHashget(locktbl, (int)redirectlock, &rwlock_obj);
	  lockvalue = (struct LockValue*)rwlock_obj;
	  if(lockvalue->redirectlock != 0) {
		  // the lock is redirected
#ifdef RAWDEBUG
		  raw_test_pass(0xe572);
#endif
		  getwritelock_I_r(ptr, (void *)lockvalue->redirectlock, core, cache);
		  return true;
	  } else {
		  if(0 == lockvalue->value) {
			  lockvalue->value = -1;
		  } else {
			  deny = true;
		  }
	  }
    }
	if(core == corenum) {
    	if(lockobj == (int)ptr) {
	      if(deny) {
		lockresult = 0;
	      } else {
		lockresult = 1;
		RuntimeHashadd_I(objRedirectLockTbl, (int)ptr, (int)redirectlock);
	      }
    	  lockflag = true;
#ifndef INTERRUPT
	      reside = true;
#endif
    	} else {
	      // conflicts on lockresults
    	  raw_test_done(0xa01a);
    	}
	    return true;
	} else {
		// send lock grant/deny request to the root requiring core
		// check if there is still some msg on sending
		int msgsize1 = 4;
		if((!cache) || (cache && !isMsgSending)) {
			calCoords(corenum, &self_y, &self_x);
			calCoords(core, &target_y, &target_x);
			// Build the message header
			msgHdr = construct_dyn_hdr(0, msgsize1, 0,             // msgsize word sent.
					                   self_y, self_x,
									   target_y, target_x);
			gdn_send(msgHdr);                     // Send the message header to EAST to handle fab(n - 1).
#ifdef RAWDEBUG
			raw_test_pass(0xbbbb);
			raw_test_pass(0xb000 + core);       // targetcore
#endif
			if(deny) {
				// deny
				gdn_send(0xa);   // lock deny with redirected info
#ifdef RAWDEBUG
				raw_test_pass(0xa);
#endif
			} else {
				// grant
				gdn_send(9);   // lock grant with redirected info
#ifdef RAWDEBUG
				raw_test_pass(9);
#endif
			}
			gdn_send(1);       // write lock
#ifdef RAWDEBUG
			raw_test_pass(1);
#endif
			gdn_send((int)ptr);  // obj pointer
#ifdef RAWDEBUG
			raw_test_pass_reg(ptr);
#endif
			gdn_send((int)redirectlock); // redirected lock
#ifdef RAWDEBUG
			raw_test_pass_reg((int)redirectlock);
			raw_test_pass(0xffff);
#endif
		} else if(cache && isMsgSending) {
			isMsgHanging = true;
			// cache the msg in outmsgdata and send it later
			// msglength + target core + msg
			outmsgdata[outmsglast++] = msgsize1;
			outmsgdata[outmsglast++] = core;
			if(deny) {
				outmsgdata[outmsglast++] = 0xa; // deny
			} else {
				outmsgdata[outmsglast++] = 9; // grant
			}
			outmsgdata[outmsglast++] = 1;
			outmsgdata[outmsglast++] = (int)ptr;
			outmsgdata[outmsglast++] = (int)redirectlock;
		}
	}
  }

  // check if there is still some msg on sending
  if((!cache) || (cache && !isMsgSending)) {
	  calCoords(corenum, &self_y, &self_x);
	  calCoords(targetcore, &target_y, &target_x);
	  // Build the message header
	  msgHdr = construct_dyn_hdr(0, msgsize, 0,             // msgsize word sent.
    	                         self_y, self_x,
        	                     target_y, target_x);
	  gdn_send(msgHdr);                     // Send the message header to EAST to handle fab(n - 1).
#ifdef RAWDEBUG
	  raw_test_pass(0xbbbb);
	  raw_test_pass(0xb000 + targetcore);       // targetcore
#endif
	  gdn_send(8);   // redirected lock request
#ifdef RAWDEBUG
	  raw_test_pass(8);
#endif
	  gdn_send(1);       // write lock
#ifdef RAWDEBUG
	  raw_test_pass(1);
#endif
	  gdn_send((int)ptr);  // obj pointer
#ifdef RAWDEBUG
	  raw_test_pass_reg(ptr);
#endif
	  gdn_send((int)redirectlock); // redirected lock
#ifdef RAWDEBUG
	  raw_test_pass_reg((int)redirectlock);
#endif
	  gdn_send(core);  // root request core
#ifdef RAWDEBUG
	  raw_test_pass_reg(core);
#endif
	  gdn_send(corenum);  // request core
#ifdef RAWDEBUG
	  raw_test_pass_reg(corenum);
	  raw_test_pass(0xffff);
#endif
  } else if(cache && isMsgSending) {
	  isMsgHanging = true;
	  // cache the msg in outmsgdata and send it later
	  // msglength + target core + msg
	  outmsgdata[outmsglast++] = msgsize;
	  outmsgdata[outmsglast++] = targetcore;
	  outmsgdata[outmsglast++] = 8;
	  outmsgdata[outmsglast++] = 1;
	  outmsgdata[outmsglast++] = (int)ptr;
	  outmsgdata[outmsglast++] = (int)redirectlock;
	  outmsgdata[outmsglast++] = core;
	  outmsgdata[outmsglast++] = corenum;
  }
  return true;
}

void releasewritelock_I(void * ptr) {
  unsigned msgHdr;
  int self_y, self_x, target_y, target_x;
  int targetcore = 0;
  // for 32 bit machine, the size is always 4 words
  int msgsize = 4;

  int reallock = 0;
  if(((struct ___Object___ *)ptr)->lock == NULL) {
	reallock = (int)ptr;
  } else {
	reallock = (int)(((struct ___Object___ *)ptr)->lock);
  }
  targetcore = (reallock >> 5) % TOTALCORE;

#ifdef RAWDEBUG
  raw_test_pass(0xe681);
  raw_test_pass_reg((int)ptr);
  raw_test_pass_reg(reallock);
  raw_test_pass_reg(targetcore);
#endif

  if(targetcore == corenum) {
    // reside on this core
    if(!RuntimeHashcontainskey(locktbl, reallock)) {
      // no locks for this object, something is wrong
      raw_test_done(0xa01b);
    } else {
      int rwlock_obj = 0;
	  struct LockValue * lockvalue = NULL;
      RuntimeHashget(locktbl, reallock, &rwlock_obj);
	  lockvalue = (struct LockValue *)rwlock_obj;
      lockvalue->value++;
    }
    return;
  }

  calCoords(corenum, &self_y, &self_x);
  calCoords(targetcore, &target_y, &target_x);
  // Build the message header
  msgHdr = construct_dyn_hdr(0, msgsize, 0,             // msgsize word sent.
                             self_y, self_x,
                             target_y, target_x);
  gdn_send(msgHdr);                     // Send the message header to EAST to handle fab(n - 1).
#ifdef RAWDEBUG
  raw_test_pass(0xbbbb);
  raw_test_pass(0xb000 + targetcore);       // targetcore
#endif
  gdn_send(5);   // lock release
#ifdef RAWDEBUG
  raw_test_pass(5);
#endif
  gdn_send(1);       // write lock
#ifdef RAWDEBUG
  raw_test_pass(1);
#endif
  gdn_send((int)ptr);  // obj pointer
#ifdef RAWDEBUG
  raw_test_pass_reg(ptr);
#endif
  gdn_send(reallock);  // lock
#ifdef RAWDEBUG
  raw_test_pass_reg(reallock);
  raw_test_pass(0xffff);
#endif
}

void releasewritelock_I_r(void * lock, void * redirectlock) {
  unsigned msgHdr;
  int self_y, self_x, target_y, target_x;
  int targetcore = 0;
  // for 32 bit machine, the size is always 4 words
  int msgsize = 4;

  int reallock = (int)lock;
  targetcore = (reallock >> 5) % TOTALCORE;

#ifdef RAWDEBUG
  raw_test_pass(0xe691);
  raw_test_pass_reg((int)lock);
  raw_test_pass_reg(reallock);
  raw_test_pass_reg(targetcore);
#endif

  if(targetcore == corenum) {
    // reside on this core
    if(!RuntimeHashcontainskey(locktbl, reallock)) {
      // no locks for this object, something is wrong
      raw_test_done(0xa01c);
    } else {
      int rwlock_obj = 0;
	  struct LockValue * lockvalue = NULL;
#ifdef RAWDEBUG
      raw_test_pass(0xe692);
#endif
      RuntimeHashget(locktbl, reallock, &rwlock_obj);
	  lockvalue = (struct LockValue *)rwlock_obj;
#ifdef RAWDEBUG
      raw_test_pass_reg(lockvalue->value);
#endif
      lockvalue->value++;
	  lockvalue->redirectlock = (int)redirectlock;
#ifdef RAWDEBUG
      raw_test_pass_reg(lockvalue->value);
#endif
    }
    return;
  }

#ifdef RAWDEBUG
  raw_test_pass(0xe693);
#endif
	  calCoords(corenum, &self_y, &self_x);
	  calCoords(targetcore, &target_y, &target_x);
	  // Build the message header
	  msgHdr = construct_dyn_hdr(0, msgsize, 0,             // msgsize word sent.
    	                         self_y, self_x,
        	                     target_y, target_x);
	  // start sending the msg, set send msg flag
	  gdn_send(msgHdr);                     
#ifdef RAWDEBUG
	  raw_test_pass(0xbbbb);
	  raw_test_pass(0xb000 + targetcore);
#endif
	  gdn_send(0xb);   // lock release with redirect info
#ifdef RAWDEBUG
	  raw_test_pass(0xb);
#endif
	  gdn_send(1);       // write lock
#ifdef RAWDEBUG
	  raw_test_pass(1);
#endif
	  gdn_send((int)lock); // lock
#ifdef RAWDEBUG
	  raw_test_pass_reg(lock);
#endif
	  gdn_send((int)redirectlock); // redirect lock
#ifdef RAWDEBUG
	  raw_test_pass_reg(redirectlock);
	  raw_test_pass(0xffff);
#endif
}
#endif

int enqueuetasks(struct parameterwrapper *parameter, struct parameterwrapper *prevptr, struct ___Object___ *ptr, int * enterflags, int numenterflags) {
  void * taskpointerarray[MAXTASKPARAMS];
  int j;
  int numparams=parameter->task->numParameters;
  int numiterators=parameter->task->numTotal-1;
  int retval=1;
  int addnormal=1;
  int adderror=1;

  struct taskdescriptor * task=parameter->task;

  ObjectHashadd(parameter->objectset, (int) ptr, 0, (int) enterflags, numenterflags, enterflags==NULL);      //this add the object to parameterwrapper

  /* Add enqueued object to parameter vector */
  taskpointerarray[parameter->slot]=ptr;

  /* Reset iterators */
  for(j=0; j<numiterators; j++) {
    toiReset(&parameter->iterators[j]);
  }

  /* Find initial state */
  for(j=0; j<numiterators; j++) {
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
    for(j=0; j<=numiterators; j++) {
      tpd->parameterArray[j]=taskpointerarray[j]; //store the actual parameters
    }
    /* Enqueue task */
    if ((/*!gencontains(failedtasks, tpd)&&*/ !gencontains(activetasks,tpd))) {
      genputtable(activetasks, tpd, tpd);
    } else {
      RUNFREE(tpd->parameterArray);
      RUNFREE(tpd);
    }

    /* This loop iterates to the next parameter combination */
    if (numiterators==0)
      return retval;

    for(j=numiterators-1; j<numiterators; j++) {
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

  ObjectHashadd_I(parameter->objectset, (int) ptr, 0, (int) enterflags, numenterflags, enterflags==NULL);      //this add the object to parameterwrapper

  /* Add enqueued object to parameter vector */
  taskpointerarray[parameter->slot]=ptr;

  /* Reset iterators */
  for(j=0; j<numiterators; j++) {
    toiReset(&parameter->iterators[j]);
  }

  /* Find initial state */
  for(j=0; j<numiterators; j++) {
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
    for(j=0; j<=numiterators; j++) {
      tpd->parameterArray[j]=taskpointerarray[j]; //store the actual parameters
    }
    /* Enqueue task */
    if ((/*!gencontains(failedtasks, tpd)&&*/ !gencontains(activetasks,tpd))) {
      genputtable_I(activetasks, tpd, tpd);
    } else {
      RUNFREE(tpd->parameterArray);
      RUNFREE(tpd);
    }

    /* This loop iterates to the next parameter combination */
    if (numiterators==0)
      return retval;

    for(j=numiterators-1; j<numiterators; j++) {
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
  bool islock = true;

#ifdef RAW
  struct LockValue * locks[MAXTASKPARAMS];
  int locklen;
  int grount = 0;
  int andmask=0;
  int checkmask=0;

  for(j = 0; j < MAXTASKPARAMS; j++) {
	  locks[j] = (struct LockValue *)(RUNMALLOC(sizeof(struct LockValue)));
	  locks[j]->redirectlock = 0;
	  locks[j]->value = 0;
  }
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
#ifdef RAWDEBUG
    raw_test_pass(0xe990);
#endif
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
	for(fd=0; fd<maxreadfd; fd++) {
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
#ifdef RAWPROFILE
      if(!taskInfoOverflow) {
	TaskInfo* checkTaskInfo = RUNMALLOC(sizeof(struct task_info));
	taskInfoArray[taskInfoIndex] = checkTaskInfo;
	checkTaskInfo->taskName = "tpd checking";
	checkTaskInfo->startTime = raw_get_cycle();
	checkTaskInfo->endTime = -1;
	checkTaskInfo->exitIndex = -1;
	checkTaskInfo->newObjs = NULL;
      }
#endif
	  busystatus = true;
      currtpd=(struct taskparamdescriptor *) getfirstkey(activetasks);
      genfreekey(activetasks, currtpd);

      numparams=currtpd->task->numParameters;
      numtotal=currtpd->task->numTotal;

#ifdef THREADSIMULATE
      int isolateflags[numparams];
#endif

	 // clear the lockRedirectTbl (TODO, this table should be empty after all locks are released)
#ifdef RAW
	  // get all required locks
	  locklen = 0;
	  // check which locks are needed
	  for(i = 0; i < numparams; i++) {
		  void * param = currtpd->parameterArray[i];
		  int tmplock = 0;
		  int j = 0;
		  bool insert = true;
		  if(((struct ___Object___ *)param)->type == STARTUPTYPE) {
			  islock = false;
			  taskpointerarray[i+OFFSET]=param;
			  goto execute;
		  }
		  if(((struct ___Object___ *)param)->lock == NULL) {
			  tmplock = (int)param;
		  } else {
			  tmplock = (int)(((struct ___Object___ *)param)->lock);
		  }
		  // insert into the locks array
		  for(j = 0; j < locklen; j++) {
			  if(locks[j]->value == tmplock) {
				  insert = false;
				  break;
			  } else if(locks[j]->value > tmplock) {
				  break;
			  }
		  }
		  if(insert) {
			  int h = locklen;
			  for(; h > j; h--) {
				  locks[h]->redirectlock = locks[h-1]->redirectlock;
				  locks[h]->value = locks[h-1]->value;
			  }
			  locks[j]->value = tmplock;
			  locks[j]->redirectlock = (int)param;
			  locklen++;
		  }		  
	  }
	  // grab these required locks
#ifdef RAWDEBUG
	  raw_test_pass(0xe991);
#endif
	  for(i = 0; i < locklen; i++) {
		  int * lock = (int *)(locks[i]->redirectlock);
		  islock = true;
		  // require locks for this parameter if it is not a startup object
#ifdef RAWDEBUG
		  raw_test_pass_reg((int)lock);
		  raw_test_pass_reg((int)(locks[i]->value));
#endif
		  getwritelock(lock);

#ifdef INTERRUPT
		  raw_user_interrupts_off();
#endif
#ifdef RAWPROFILE
		  //isInterrupt = false;
#endif 
		  while(!lockflag) { 
			  receiveObject();
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
		  lock2require = 0;
		  lockflag = false;
#ifndef INTERRUPT
		  reside = false;
#endif
#ifdef RAWPROFILE
		  //isInterrupt = true;
#endif
#ifdef INTERRUPT
		  raw_user_interrupts_on();
#endif

		  if(grount == 0) {
			  int j = 0;
#ifdef RAWDEBUG
			  raw_test_pass(0xe992);
#endif
			  // can not get the lock, try later
			  // releas all grabbed locks for previous parameters
			  for(j = 0; j < i; ++j) {
				  lock = (int*)(locks[j]->redirectlock);
				  releasewritelock(lock);
			  }
			  genputtable(activetasks, currtpd, currtpd);
			  if(hashsize(activetasks) == 1) {
				  // only one task right now, wait a little while before next try
				  int halt = 10000;
				  while(halt--) {
				  }
			  }
#ifdef RAWPROFILE
			  // fail, set the end of the checkTaskInfo
			  if(!taskInfoOverflow) {
				  taskInfoArray[taskInfoIndex]->endTime = raw_get_cycle();
				  taskInfoIndex++;
				  if(taskInfoIndex == TASKINFOLENGTH) {
					  taskInfoOverflow = true;
				  }
			  }
#endif
			  goto newtask;
		  }
	  }
#elif defined THREADSIMULATE
	  // TODO: need modification according to added alias locks
#endif

#ifdef RAWDEBUG
	raw_test_pass(0xe993);
#endif
      /* Make sure that the parameters are still in the queues */
      for(i=0; i<numparams; i++) {
	void * parameter=currtpd->parameterArray[i];
#ifdef RAW

	// flush the object
#ifdef RAWCACHEFLUSH
	{
	  raw_invalidate_cache_range((int)parameter, classsize[((struct ___Object___ *)parameter)->type]);
	}
#ifdef INTERRUPT
		  raw_user_interrupts_off();
#endif
	/*if(RuntimeHashcontainskey(objRedirectLockTbl, (int)parameter)) {
		int redirectlock_r = 0;
		RuntimeHashget(objRedirectLockTbl, (int)parameter, &redirectlock_r);
		((struct ___Object___ *)parameter)->lock = redirectlock_r;
		RuntimeHashremovekey(objRedirectLockTbl, (int)parameter);
	}*/
#ifdef INTERRUPT
		  raw_user_interrupts_on();
#endif
#endif
#endif
	tmpparam = (struct ___Object___ *)parameter;
#ifdef THREADSIMULATE
	if(0 == tmpparam->isolate) {
	  isolateflags[i] = 0;
	  // shared object, need to flush with current value
	  // TODO: need modification according to added alias locks
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
#ifdef RAWDEBUG
	    raw_test_pass(0xe994);
#endif
	    // release grabbed locks
#ifdef RAW
	    for(j = 0; j < locklen; ++j) {
		int * lock = (int *)(locks[j]->redirectlock);
		releasewritelock(lock);
	    }
#elif defined THREADSIMULATE
#endif
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
#ifdef RAWDEBUG
	    raw_test_pass(0xe995);
#endif
	    ObjectHashget(pw->objectset, (int) parameter, (int *) &next, (int *) &enterflags, &UNUSED, &UNUSED2);
	    ObjectHashremove(pw->objectset, (int)parameter);
	    if (enterflags!=NULL)
	      free(enterflags);
	    // release grabbed locks
	    for(j = 0; j < locklen; ++j) {
		 int * lock = (int *)(locks[j]->redirectlock);
		releasewritelock(lock);
	    }
	    RUNFREE(currtpd->parameterArray);
	    RUNFREE(currtpd);
#ifdef RAWPROFILE
	    // fail, set the end of the checkTaskInfo
	    if(!taskInfoOverflow) {
	      taskInfoArray[taskInfoIndex]->endTime = raw_get_cycle();
	      taskInfoIndex++;
	      if(taskInfoIndex == TASKINFOLENGTH) {
		taskInfoOverflow = true;
	      }
	    }
#endif
	    goto newtask;
	  }
	}
#endif
parameterpresent:
	;
	/* Check that object still has necessary tags */
	for(j=0; j<pd->numbertags; j++) {
	  int slotid=pd->tagarray[2*j]+numparams;
	  struct ___TagDescriptor___ *tagd=currtpd->parameterArray[slotid];
	  if (!containstag(parameter, tagd)) {
#ifdef RAWDEBUG
	    raw_test_pass(0xe996);
#endif
		{
		// release grabbed locks
		int tmpj = 0;
	    for(tmpj = 0; tmpj < locklen; ++tmpj) {
		 int * lock = (int *)(locks[tmpj]->redirectlock);
		releasewritelock(lock);
	    }
		}
	    RUNFREE(currtpd->parameterArray);
	    RUNFREE(currtpd);
	    goto newtask;
	  }
	}

	taskpointerarray[i+OFFSET]=parameter;
      }
      /* Copy the tags */
      for(; i<numtotal; i++) {
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
	  raw_test_done(0xa01d);
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
execute:
#ifdef RAWPROFILE
	  {
	    // check finish, set the end of the checkTaskInfo
	    if(!taskInfoOverflow) {
	      taskInfoArray[taskInfoIndex]->endTime = raw_get_cycle();
	      taskInfoIndex++;
	      if(taskInfoIndex == TASKINFOLENGTH) {
		taskInfoOverflow = true;
	      }
	    }
	  }
	  if(!taskInfoOverflow) {
	    // new a taskInfo for the task execution
	    TaskInfo* taskInfo = RUNMALLOC(sizeof(struct task_info));
	    taskInfoArray[taskInfoIndex] = taskInfo;
	    taskInfo->taskName = currtpd->task->name;
	    taskInfo->startTime = raw_get_cycle();
	    taskInfo->endTime = -1;
		taskInfo->exitIndex = -1;
		taskInfo->newObjs = NULL;
	  }
#endif

	  if(debugtask) {
#ifndef RAW
	    printf("ENTER %s count=%d\n",currtpd->task->name, (instaccum-instructioncount));
#endif
	    ((void(*) (void **))currtpd->task->taskptr)(taskpointerarray);
#ifndef RAW
	    printf("EXIT %s count=%d\n",currtpd->task->name, (instaccum-instructioncount));
#endif
	  } else {
#ifdef RAWDEBUG
		  raw_test_pass(0xe997);
#endif
	    ((void(*) (void **))currtpd->task->taskptr)(taskpointerarray);
	  }
#ifdef RAWPROFILE
	  // task finish, set the end of the checkTaskInfo
	  if(!taskInfoOverflow) {
	    taskInfoArray[taskInfoIndex]->endTime = raw_get_cycle();
	    taskInfoIndex++;
	    if(taskInfoIndex == TASKINFOLENGTH) {
	      taskInfoOverflow = true;
	    }
	  }
	  // new a PostTaskInfo for the post-task execution
	  if(!taskInfoOverflow) {
	    TaskInfo* postTaskInfo = RUNMALLOC(sizeof(struct task_info));
	    taskInfoArray[taskInfoIndex] = postTaskInfo;
	    postTaskInfo->taskName = "post task execution";
	    postTaskInfo->startTime = raw_get_cycle();
	    postTaskInfo->endTime = -1;
		postTaskInfo->exitIndex = -1;
		postTaskInfo->newObjs = NULL;
	  }
#endif
#ifdef RAWDEBUG
	  raw_test_pass(0xe998);
	  raw_test_pass_reg(islock);
#endif

	  if(islock) {
#ifdef RAW
#ifdef RAWDEBUG
		  raw_test_pass(0xe999);
#endif
	    for(i = 0; i < locklen; ++i) {
		  void * ptr = (void *)(locks[i]->redirectlock);
	      int * lock = (int *)(locks[i]->value);
#ifdef RAWDEBUG
		  raw_test_pass_reg((int)ptr);
		  raw_test_pass_reg((int)lock);
#endif
		  if(RuntimeHashcontainskey(lockRedirectTbl, (int)lock)) {
			  int redirectlock;
			  RuntimeHashget(lockRedirectTbl, (int)lock, &redirectlock);
			  RuntimeHashremovekey(lockRedirectTbl, (int)lock);
			  releasewritelock_r(lock, (int *)redirectlock);
		  } else {
		releasewritelock(ptr);
		  }
	    }
#elif defined THREADSIMULATE
		// TODO : need modification for alias lock
	    for(i = 0; i < numparams; ++i) {
	      int * lock;
	      if(0 == isolateflags[i]) {
		struct ___Object___ * tmpparam = (struct ___Object___ *)taskpointerarray[i+OFFSET];
		if(tmpparam->lock == NULL) {
		  lock = (int*)tmpparam;
		} else {
		  lock = tmpparam->lock;
		}
		  releasewritelock(lock);
	      }
	    }
#endif
	  }

#ifdef RAWPROFILE
	  // post task execution finish, set the end of the postTaskInfo
	  if(!taskInfoOverflow) {
	    taskInfoArray[taskInfoIndex]->endTime = raw_get_cycle();
	    taskInfoIndex++;
	    if(taskInfoIndex == TASKINFOLENGTH) {
	      taskInfoOverflow = true;
	    }
	  }
#endif

#if 0
#ifndef RAW
	  freeRuntimeHash(forward);
	  freeRuntimeHash(reverse);
	  freemalloc();
#endif
#endif
	  // Free up task parameter descriptor
	  RUNFREE(currtpd->parameterArray);
	  RUNFREE(currtpd);
#if 0
#ifndef RAW
	  forward=NULL;
	  reverse=NULL;
#endif
#endif
#ifdef RAWDEBUG
	  raw_test_pass(0xe99a);
#endif
	}
      }
    }
  }
#ifdef RAWDEBUG
  raw_test_pass(0xe99b);
#endif
}

/* This function processes an objects tags */
void processtags(struct parameterdescriptor *pd, int index, struct parameterwrapper *parameter, int * iteratorcount, int *statusarray, int numparams) {
  int i;

  for(i=0; i<pd->numbertags; i++) {
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

  for(i=0; i<pd->numbertags; i++) {
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
  for(i=0; i<MAXTASKPARAMS; i++) statusarray[i]=0;

  statusarray[index]=1; /* Initial parameter */
  /* Process tags for initial iterator */

  processtags(task->descriptorarray[index], index, parameter, &iteratorcount, statusarray, numparams);

  while(1) {
loopstart:
    /* Check for objects with existing tags */
    for(i=0; i<numparams; i++) {
      if (statusarray[i]==0) {
	struct parameterdescriptor *pd=task->descriptorarray[i];
	int j;
	for(j=0; j<pd->numbertags; j++) {
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

    for(i=0; i<numparams; i++) {
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

    for(i=0; i<numparams; i++) {
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
  for(i=0; i<numtasks[numofcore]; i++) {
    struct taskdescriptor * task=taskarray[numofcore][i];
#else
#ifdef RAW
  if(corenum > NUMCORES - 1) {
    return;
  }
#endif
  for(i=0; i<numtasks[corenum]; i++) {
    struct taskdescriptor * task=taskarray[corenum][i];
#endif
#ifndef RAW
    printf("%s\n", task->name);
#endif
    for(j=0; j<task->numParameters; j++) {
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
	  for(; tagindex<ao->___cachedCode___; tagindex++) {
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
  for(i=0; i<numtasks[numofcore]; i++) {
    struct taskdescriptor *task=taskarray[numofcore][i];
#else
  for(i=0; i<numtasks[corenum]; i++) {
    struct taskdescriptor * task=taskarray[corenum][i];
#endif
    int j;

    /* Build objectsets */
    for(j=0; j<task->numParameters; j++) {
      struct parameterdescriptor *param=task->descriptorarray[j];
      struct parameterwrapper *parameter=param->queue;
      parameter->objectset=allocateObjectHash(10);
      parameter->task=task;
    }

    /* Build iterators for parameters */
    for(j=0; j<task->numParameters; j++) {
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
      for(; tagindex<ao->___cachedCode___; tagindex++) {
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
      for(i=1; i<it->numtags; i++) {
	struct ___TagDescriptor___ *tag2=objectarray[it->tagbindings[i]];
	if (!containstag(objptr,tag2))
	  return 0;
      }
      return 1;
    } else {
      struct ArrayObject *ao=(struct ArrayObject *) objptr;
      int tagindex;
      int i;
      for(tagindex=it->tagobjindex; tagindex<ao->___cachedCode___; tagindex++) {
	struct ___Object___ *objptr=ARRAYGET(ao, struct ___Object___*, tagindex);
	if (!ObjectHashcontainskey(it->objectset, (int) objptr))
	  continue;
	for(i=1; i<it->numtags; i++) {
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
    for(j=0; j<ao->___cachedCode___; j++) {
      if (ptr==ARRAYGET(ao, struct ___Object___*, j))
	return 1;
    }
    return 0;
  } else
    return objptr==ptr;
}

void toiNext(struct tagobjectiterator *it, void ** objectarray OPTARG(int * failed)) {
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
