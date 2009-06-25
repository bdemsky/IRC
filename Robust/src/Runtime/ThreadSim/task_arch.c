#ifdef TASK
#include "runtime.h"
#include "runtime_arch.h"

int main(int argc, char **argv) {
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

#if 0
#ifdef BOEHM_GC
  GC_init(); // Initialize the garbage collector
#endif
#ifdef CONSCHECK
  initializemmap();
#endif
  processOptions();
#endif // #if 0: for recovery and garbage collection
  initializeexithandler();
  /* Create table for failed tasks */
#if 0
  failedtasks=genallocatehashtable((unsigned int (*)(void *)) &hashCodetpd,
                                   (int (*)(void *,void *)) &comparetpd);
#endif // #if 0: for recovery
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

  /* Start executing the tasks */
  executetasks();

  int i = 0;
  // check if there are new objects coming
  bool sendStall = false;

  int numofcore = BAMBOO_GET_NUM_OF_CORE();
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

}

/* Message format:
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
}

// send terminate message to targetcore
// format: -1
bool transStallMsg(int targetcore) {
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
}

void transStatusConfirmMsg(int targetcore) {
	// TODO
}

int receiveObject() {
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
	// TODO no longer use isolate flag
    /*if(0 == newobj->isolate) {
      newobj->original=tmpptr;
    }*/
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
}

bool getreadlock(void * ptr) {
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
}

void releasereadlock(void * ptr) {
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
}

bool getwritelock(void * ptr) {
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
}

void releasewritelock(void * ptr) {
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
}

void releasewritelock_r(void * lock, void * redirectlock) {
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
}

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

#if 0
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
#endif  // #if 0: non-multicore

#if 0
  /* Zero fd set */
  FD_ZERO(&readfds);
#endif
  maxreadfd=0;
#if 0
  fdtoobject=allocateRuntimeHash(100);
#endif

#if 0
  /* Map first block of memory to protected, anonymous page */
  mmap(0, 0x1000, 0, MAP_SHARED|MAP_FIXED|MAP_ANON, -1, 0);
#endif

newtask:
  while((hashsize(activetasks)>0)||(maxreadfd>0)) {

#ifdef DEBUG
    DEBUGPRINT(0xe990);
#endif
#if 0
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
#ifdef PROFILE
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

      // TODO no longer use isolate flag
	  int isolateflags[numparams];

	 // clear the lockRedirectTbl (TODO, this table should be empty after all locks are released)
	  // TODO: need modification according to added alias locks

#ifdef DEBUG
	DEBUGPRINT(0xe993);
#endif
      /* Make sure that the parameters are still in the queues */
      for(i=0; i<numparams; i++) {
	void * parameter=currtpd->parameterArray[i];
	tmpparam = (struct ___Object___ *)parameter;
	// TODO no longer use isolate flag
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
	pd=currtpd->task->descriptorarray[i];
	pw=(struct parameterwrapper *) pd->queue;
	/* Check that object is still in queue */
	{
	  if (!ObjectHashcontainskey(pw->objectset, (int) parameter)) {
#ifdef DEBUG
	    DEBUGPRINT(0xe994);
#endif
	    // release grabbed locks
	    RUNFREE(currtpd->parameterArray);
	    RUNFREE(currtpd);
	    goto newtask;
	  }
	}
parameterpresent:
	;
	/* Check that object still has necessary tags */
	for(j=0; j<pd->numbertags; j++) {
	  int slotid=pd->tagarray[2*j]+numparams;
	  struct ___TagDescriptor___ *tagd=currtpd->parameterArray[slotid];
	  if (!containstag(parameter, tagd)) {
#ifdef DEBUG
	    DEBUGPRINT(0xe996);
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

      for(i = 0; i < numparams; ++i) {
	if(0 == isolateflags[i]) {
	  struct ___Object___ * tmpparam = (struct ___Object___ *)taskpointerarray[i+OFFSET];
	  if(tmpparam != tmpparam->original) {
	    taskpointerarray[i+OFFSET] = tmpparam->original;
	  }
	}
      }

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
#ifdef DEBUG
	  PRINTF("Fatal Error=%d, Recovering!\n",x);
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
	  exit(-1);
	} else {
	  /*if (injectfailures) {
	     if ((((double)random())/RAND_MAX)<failurechance) {
	      printf("\nINJECTING TASK FAILURE to %s\n", currtpd->task->name);
	      longjmp(error_handler,10);
	     }
	     }*/
	  /* Actually call task */
#if 0
#ifdef PRECISE_GC
	  ((int *)taskpointerarray)[0]=currtpd->numParameters;
	  taskpointerarray[1]=NULL;
#endif
#endif  // #if 0: for garbage collection
execute:
#ifdef PROFILE
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
	    PRINTF("ENTER %s count=%d\n",currtpd->task->name, (instaccum-instructioncount));
	    ((void(*) (void **))currtpd->task->taskptr)(taskpointerarray);
	    PRINTF("EXIT %s count=%d\n",currtpd->task->name, (instaccum-instructioncount));
	  } else {
#ifdef DEBUG
		  DEBUGPRINT(0xe997);
#endif
	    ((void(*) (void **))currtpd->task->taskptr)(taskpointerarray);
	  }
#ifdef PROFILE
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
#ifdef DEBUG
	  DEBUGPRINT(0xe998);
	  DEBUGPRINT_REG(islock);
#endif

	  if(islock) {
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
	  }

#ifdef PROFILE
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
	  freeRuntimeHash(forward);
	  freeRuntimeHash(reverse);
	  freemalloc();
#endif
	  // Free up task parameter descriptor
	  RUNFREE(currtpd->parameterArray);
	  RUNFREE(currtpd);
#if 0
	  forward=NULL;
	  reverse=NULL;
#endif
#ifdef DEBUG
	  DEBUGPRINT(0xe99a);
#endif
	}
      }
    }
  }
#ifdef DEBUG
  DEBUGPRINT(0xe99b);
#endif
}
#endif // #ifdef TASK
