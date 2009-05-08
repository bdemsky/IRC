/* ============================================================
 * singleTMCommit.c
 * - single thread commit on local machine
 * =============================================================
 * Copyright (c) 2009, University of California, Irvine, USA.
 * All rights reserved.
 * Author: Alokika Dash
 *         adash@uci.edu
 * =============================================================
 *
 */

#include "tm.h"
#include "garbage.h"

/*** Globals *****/
/* Thread transaction variables */
__thread objstr_t *t_cache;
__thread objstr_t *t_reserve;
__thread struct objlist * newobjs;

#ifdef TRANSSTATS
int numTransCommit = 0;
int numTransAbort = 0;
int nSoftAbort = 0;
int nSoftAbortCommit = 0;
int nSoftAbortAbort = 0;
#endif

#ifdef STMSTATS
/* Thread variable for locking/unlocking */
__thread threadrec_t *trec;
__thread struct objlist * lockedobjs;
/** Global lock **/
int typesCausingAbort[TOTALNUMCLASSANDARRAY];
/******Keep track of objects and types causing aborts******/
/*TODO
#define DEBUGSTMSTAT(args...) { \
  printf(args); \
  fflush(stdout); \
}
*/
#define DEBUGSTMSTAT(args...)
#else
#define DEBUGSTMSTAT(args...)
#endif

#ifdef STMDEBUG
#define DEBUGSTM(x...) printf(x);
#else
#define DEBUGSTM(x...)
#endif

#define ABORTCOUNT(x) x->abortCount++;   \
  if (x->abortCount > MAXABORTS) {	 \
    x->riskyflag = 1;			 \
  }

/* ==================================================
 * stmStartup
 * This function starts up the transaction runtime.
 * ==================================================
 */
int stmStartup() {
  return 0;
}

/* ======================================
 * objstrCreate
 * - create an object store of given size
 * ======================================
 */
objstr_t *objstrCreate(unsigned int size) {
  objstr_t *tmp;
  if((tmp = calloc(1, (sizeof(objstr_t) + size))) == NULL) {
    printf("%s() Calloc error at line %d, %s\n", __func__, __LINE__, __FILE__);
    return NULL;
  }
  tmp->size = size;
  tmp->next = NULL;
  tmp->top = tmp + 1; //points to end of objstr_t structure!
  return tmp;
}

void objstrReset() {
  while(t_cache->next!=NULL) {
    objstr_t *next=t_cache->next;
    t_cache->next=t_reserve;
    t_reserve=t_cache;
    t_cache=next;
  }
  t_cache->top=t_cache+1;
}

//free entire list, starting at store
void objstrDelete(objstr_t *store) {
  objstr_t *tmp;
  while (store != NULL) {
    tmp = store->next;
    free(store);
    store = tmp;
  }
  return;
}

/* =================================================
 * transStart
 * This function initializes things required in the
 * transaction start
 * =================================================
 */
void transStart() {
  //Transaction start is currently free...commit and aborting is not
}

/* =======================================================
 * transCreateObj
 * This function creates objects in the transaction record
 * =======================================================
 */
objheader_t *transCreateObj(void * ptr, unsigned int size) {
  objheader_t *tmp = mygcmalloc(ptr, (sizeof(objheader_t) + size));
  objheader_t *retval=&tmp[1];
  tmp->lock=RW_LOCK_BIAS;
  tmp->version = 1;
  tmp->abortCount = 0;
  tmp->accessCount = 0;
  tmp->riskyflag = 0;
  tmp->trec = NULL;
  //initialize obj lock
  pthread_mutex_init(&tmp->objlock, NULL);
  STATUS(tmp)=NEW;
  // don't insert into table
  if (newobjs->offset<MAXOBJLIST) {
    newobjs->objs[newobjs->offset++]=retval;
  } else {
    struct objlist *tmp=malloc(sizeof(struct objlist));
    tmp->next=newobjs;
    tmp->objs[0]=retval;
    tmp->offset=1;
    newobjs=tmp;
  }
  return retval; //want space after object header
}

/* This functions inserts randowm wait delays in the order of msec
 * Mostly used when transaction commits retry*/
void randomdelay(int softaborted) {
  struct timespec req;
  struct timeval t;

  gettimeofday(&t,NULL);

  req.tv_sec = 0;
  req.tv_nsec = (long)((t.tv_usec)%(1<<softaborted))<<1; //1-11 microsec
  nanosleep(&req, NULL);
  return;
}

/* ==============================================
 * objstrAlloc
 * - allocate space in an object store
 * ==============================================
 */
void *objstrAlloc(unsigned int size) {
  void *tmp;
  int i=0;
  objstr_t *store=t_cache;
  if ((size&7)!=0) {
    size+=(8-(size&7));
  }

  for(; i<2; i++) {
    if (OSFREE(store)>=size) {
      tmp=store->top;
      store->top +=size;
      return tmp;
    }
    if ((store=store->next)==NULL)
      break;
  }

  {
    unsigned int newsize=size>DEFAULT_OBJ_STORE_SIZE ? size : DEFAULT_OBJ_STORE_SIZE;
    objstr_t **otmp=&t_reserve;
    objstr_t *ptr;
    while((ptr=*otmp)!=NULL) {
      if (ptr->size>=newsize) {
	//remove from list
	*otmp=ptr->next;
	ptr->next=t_cache;
	t_cache=ptr;
	ptr->top=((char *)(&ptr[1]))+size;
	return &ptr[1];
      }
    }

    objstr_t *os=(objstr_t *)calloc(1,(sizeof(objstr_t) + newsize));
    void *nptr=&os[1];
    os->next=t_cache;
    t_cache=os;
    os->size=newsize;
    os->top=((char *)nptr)+size;
    return nptr;
  }
}

/* =============================================================
 * transRead
 * -finds the objects either in main heap
 * -copies the object into the transaction cache
 * =============================================================
 */
__attribute__((pure)) void *transRead(void * oid) {
  objheader_t *tmp, *objheader;
  objheader_t *objcopy;
  int size;

  /* Read from the main heap */
  //No lock for now
  objheader_t *header = (objheader_t *)(((char *)oid) - sizeof(objheader_t));
  GETSIZE(size, header);
  size += sizeof(objheader_t);
  objcopy = (objheader_t *) objstrAlloc(size);
  memcpy(objcopy, header, size);
#ifdef STMSTATS
  header->accessCount++;
  //FIXME riskratio fix
  //float riskratio = ((header->abortCount)/(header->accessCount));
  //DEBUGSTMSTAT("type: %d, header->abortCount: %d, header->accessCount: %d, riskratio: %f\n", TYPE(header), header->abortCount, header->accessCount, riskratio);
  //DEBUGSTMSTAT("type: %d, header->abortCount: %d, header->accessCount: %d\n", TYPE(header), header->abortCount, header->accessCount);
  //if(header->abortCount > MAXABORTS &&  riskratio > NEED_LOCK_THRESHOLD) {
  if (header->riskyflag) {
    //makes riskflag sticky
    needLock(header);
  }
#endif
  /* Insert into cache's lookup table */
  STATUS(objcopy)=0;
  t_chashInsert(oid, &objcopy[1]);
  return &objcopy[1];
}

void freenewobjs() {
  struct objlist *ptr=newobjs;
  while(ptr->next!=NULL) {
    struct objlist *tmp=ptr->next;
    free(ptr);
    ptr=tmp;
  }
  ptr->offset=0;
  newobjs=ptr;
}

#ifdef STMSTATS
void freelockedobjs() {
  struct objlist *ptr=lockedobjs;
  while(ptr->next!=NULL) {
    struct objlist *tmp=ptr->next;
    free(ptr);
    ptr=tmp;
  }
  ptr->offset=0;
  lockedobjs=ptr;
}
#endif

/* ================================================================
 * transCommit
 * - This function initiates the transaction commit process
 * - goes through the transaction cache and decides
 * - a final response
 * ================================================================
 */
int transCommit() {
  int softaborted=0;
  do {
    /* Look through all the objects in the transaction hash table */
    int finalResponse;
    if (c_numelements<(c_size>>3))
      finalResponse= alttraverseCache();
    else
      finalResponse= traverseCache();
    if(finalResponse == TRANS_ABORT) {
#ifdef TRANSSTATS
      numTransAbort++;
      if (softaborted) {
	nSoftAbortAbort++;
      }
#endif
      freenewobjs();
#ifdef STMSTATS
      freelockedobjs();
#endif
      objstrReset();
      t_chashreset();
      return TRANS_ABORT;
    }
    if(finalResponse == TRANS_COMMIT) {
#ifdef TRANSSTATS
      numTransCommit++;
      if (softaborted) {
	nSoftAbortCommit++;
      }
#endif
      freenewobjs();
#ifdef STMSTATS
      freelockedobjs();
#endif
      objstrReset();
      t_chashreset();
      return 0;
    }
    /* wait a random amount of time before retrying to commit transaction*/
    if(finalResponse == TRANS_SOFT_ABORT) {
#ifdef TRANSSTATS
      nSoftAbort++;
#endif
      softaborted++;
      if (softaborted>4) {
	//retry if too many soft aborts
	freenewobjs();
#ifdef STMSTATS
    freelockedobjs();
#endif
	objstrReset();
	t_chashreset();
	return TRANS_ABORT;
      }
      randomdelay(softaborted);
    } else {
      printf("Error: in %s() Unknown outcome", __func__);
      exit(-1);
    }
  } while (1);
}

/* ==================================================
 * traverseCache
 * - goes through the transaction cache and
 * - decides if a transaction should commit or abort
 * ==================================================
 */
int traverseCache() {
  /* Create info to keep track of objects that can be locked */
  int numoidrdlocked=0;
  int numoidwrlocked=0;
  void * rdlocked[200];
  int rdversion[200];
  void * wrlocked[200];
  int softabort=0;
  int i;
  void ** oidrdlocked;
  void ** oidwrlocked;
  int * oidrdversion;
  if (c_numelements<200) {
    oidrdlocked=rdlocked;
    oidrdversion=rdversion;
    oidwrlocked=wrlocked;
  } else {
    int size=c_numelements*sizeof(void*);
    oidrdlocked=malloc(size);
    oidrdversion=malloc(size);
    oidwrlocked=malloc(size);
  }
  chashlistnode_t *ptr = c_table;
  /* Represents number of bins in the chash table */
  unsigned int size = c_size;
  for(i = 0; i<size; i++) {
    chashlistnode_t *curr = &ptr[i];
    /* Inner loop to traverse the linked list of the cache lookupTable */
    while(curr != NULL) {
      //if the first bin in hash table is empty
      if(curr->key == NULL)
	break;
      objheader_t * headeraddr=&((objheader_t *) curr->val)[-1];
      objheader_t *header=(objheader_t *)(((char *)curr->key)-sizeof(objheader_t));
      unsigned int version = headeraddr->version;

      if(STATUS(headeraddr) & DIRTY) {
	/* Read from the main heap  and compare versions */
	if(write_trylock(&header->lock)) { //can aquire write lock
	  if (version == header->version) { /* versions match */
	    /* Keep track of objects locked */
	    oidwrlocked[numoidwrlocked++] = OID(header);
	  } else {
	    oidwrlocked[numoidwrlocked++] = OID(header);
	    transAbortProcess(oidwrlocked, numoidwrlocked);
#ifdef STMSTATS
	    ABORTCOUNT(header);
	    (typesCausingAbort[TYPE(header)])++;
	    getTotalAbortCount(i+1, size, (void *)(curr->next), NULL, 1);
#endif
	    DEBUGSTM("WR Abort: rd: %u wr: %u tot: %u type: %u ver: %u\n", numoidrdlocked, numoidwrlocked, c_numelements, TYPE(header), header->version);
	    DEBUGSTMSTAT("WR Abort: Access Count: %u AbortCount: %u type: %u ver: %u \n", header->accessCount, header->abortCount, TYPE(header), header->version);
	    if (c_numelements>=200) {
	      free(oidrdlocked);
	      free(oidrdversion);
	      free(oidwrlocked);
	    }
	    return TRANS_ABORT;
	  }
	} else { /* cannot aquire lock */
	  if(version == header->version) {
	    /* versions match */
	    softabort=1;
	  } else {
	    transAbortProcess(oidwrlocked, numoidwrlocked);
#ifdef STMSTATS
	    ABORTCOUNT(header);
	    (typesCausingAbort[TYPE(header)])++;
	    getTotalAbortCount(i+1, size, (void *)(curr->next), NULL, 1);
#endif
	    DEBUGSTM("WR Abort: rd: %u wr: %u tot: %u type: %u ver: %u\n", numoidrdlocked, numoidwrlocked, c_numelements, TYPE(header), header->version);
	    DEBUGSTMSTAT("WR Abort: Access Count: %u AbortCount: %u type: %u ver: %u \n", header->accessCount, header->abortCount, TYPE(header), header->version);
	    if (c_numelements>=200) {
	      free(oidrdlocked);
	      free(oidrdversion);
	      free(oidwrlocked);
	    }
	    return TRANS_ABORT;
	  }
	}
      } else {
	oidrdversion[numoidrdlocked]=version;
	oidrdlocked[numoidrdlocked++] = header;
      }
      curr = curr->next;
    }
  } //end of for

  //THIS IS THE SERIALIZATION POINT *****

  for(i=0; i<numoidrdlocked; i++) {
    /* Read from the main heap  and compare versions */
    objheader_t *header=oidrdlocked[i];
    unsigned int version=oidrdversion[i];
    if(header->lock>0) { //not write locked
      if(version != header->version) { /* versions do not match */
	oidrdlocked[numoidrdlocked++] = OID(header);
	transAbortProcess(oidwrlocked, numoidwrlocked);
#ifdef STMSTATS
	ABORTCOUNT(header);
	(typesCausingAbort[TYPE(header)])++;
	getTotalAbortCount(i+1, numoidrdlocked, oidrdlocked, (void *) oidrdversion, 0);
#endif
	DEBUGSTM("RD Abort: rd: %u wr: %u tot: %u type: %u ver: %u\n", numoidrdlocked, numoidwrlocked, c_numelements, TYPE(header), header->version);
	DEBUGSTMSTAT("RD Abort: Access Count: %u AbortCount: %u type: %u ver: %u \n", header->accessCount, header->abortCount, TYPE(header), header->version);
	if (c_numelements>=200) {
	  free(oidrdlocked);
	  free(oidrdversion);
	  free(oidwrlocked);
	}
	return TRANS_ABORT;
      }
    } else { /* cannot aquire lock */
      //do increment as we didn't get lock
      if(version == header->version) {
	softabort=1;
      } else {
	transAbortProcess(oidwrlocked, numoidwrlocked);
#ifdef STMSTATS
	ABORTCOUNT(header);
	(typesCausingAbort[TYPE(header)])++;
	getTotalAbortCount(i+1, numoidrdlocked, oidrdlocked, (void *) oidrdversion, 0);
#endif
	DEBUGSTM("RD Abort: rd: %u wr: %u tot: %u type: %u ver: %u\n", numoidrdlocked, numoidwrlocked, c_numelements, TYPE(header), header->version);
	DEBUGSTMSTAT("RD Abort: Access Count: %u AbortCount: %u type: %u ver: %u \n", header->accessCount, header->abortCount, TYPE(header), header->version);
	if (c_numelements>=200) {
	  free(oidrdlocked);
	  free(oidrdversion);
	  free(oidwrlocked);
	}
	return TRANS_ABORT;
      }
    }
  }

  /* Decide the final response */
  if (softabort) {
    transAbortProcess(oidwrlocked, numoidwrlocked);
    DEBUGSTM("Soft Abort: rd: %u wr: %u tot: %u\n", numoidrdlocked, numoidwrlocked, c_numelements);
    if (c_numelements>=200) {
      free(oidrdlocked);
      free(oidrdversion);
      free(oidwrlocked);
    }
    return TRANS_SOFT_ABORT;
  } else {
    transCommitProcess(oidwrlocked, numoidwrlocked);
    DEBUGSTM("Commit: rd: %u wr: %u tot: %u\n", numoidrdlocked, numoidwrlocked, c_numelements);
    if (c_numelements>=200) {
      free(oidrdlocked);
      free(oidrdversion);
      free(oidwrlocked);
    }
    return TRANS_COMMIT;
  }
}

/* ==================================================
 * alttraverseCache
 * - goes through the transaction cache and
 * - decides if a transaction should commit or abort
 * ==================================================
 */
int alttraverseCache() {
  /* Create info to keep track of objects that can be locked */
  int numoidrdlocked=0;
  int numoidwrlocked=0;
  void * rdlocked[200];
  int rdversion[200];
  void * wrlocked[200];
  int softabort=0;
  int i;
  void ** oidrdlocked;
  int * oidrdversion;
  void ** oidwrlocked;
  if (c_numelements<200) {
    oidrdlocked=rdlocked;
    oidrdversion=rdversion;
    oidwrlocked=wrlocked;
  } else {
    int size=c_numelements*sizeof(void*);
    oidrdlocked=malloc(size);
    oidrdversion=malloc(size);
    oidwrlocked=malloc(size);
  }
  chashlistnode_t *curr = c_list;
  /* Inner loop to traverse the linked list of the cache lookupTable */
  while(curr != NULL) {
    //if the first bin in hash table is empty
    objheader_t * headeraddr=&((objheader_t *) curr->val)[-1];
    objheader_t *header=(objheader_t *)(((char *)curr->key)-sizeof(objheader_t));
    unsigned int version = headeraddr->version;

    if(STATUS(headeraddr) & DIRTY) {
      /* Read from the main heap  and compare versions */
      if(write_trylock(&header->lock)) { //can aquire write lock
	if (version == header->version) { /* versions match */
	  /* Keep track of objects locked */
	  oidwrlocked[numoidwrlocked++] = OID(header);
	} else {
	  oidwrlocked[numoidwrlocked++] = OID(header);
	  transAbortProcess(oidwrlocked, numoidwrlocked);
#ifdef STMSTATS
	  ABORTCOUNT(header);
	  (typesCausingAbort[TYPE(header)])++;
	  getTotalAbortCount(0, 1, (void *) curr->next, NULL, 1);
#endif
	  DEBUGSTM("WR Abort: rd: %u wr: %u tot: %u type: %u ver: %u\n", numoidrdlocked, numoidwrlocked, c_numelements, TYPE(header), header->version);
	  DEBUGSTMSTAT("WR Abort: Access Count: %u AbortCount: %u type: %u ver: %u \n", header->accessCount, header->abortCount, TYPE(header), header->version);
	  if (c_numelements>=200) {
	    free(oidrdlocked);
	    free(oidrdversion);
	    free(oidwrlocked);
	  }
	  return TRANS_ABORT;
	}
      } else { /* cannot aquire lock */
	if(version == header->version) {
	  /* versions match */
	  softabort=1;
	} else {
	  transAbortProcess(oidwrlocked, numoidwrlocked);
#ifdef STMSTATS
	  ABORTCOUNT(header);
	  (typesCausingAbort[TYPE(header)])++;
	  getTotalAbortCount(0, 1, (void *) curr->next, NULL, 1);
#endif
	  DEBUGSTM("WR Abort: rd: %u wr: %u tot: %u type: %u ver: %u\n", numoidrdlocked, numoidwrlocked, c_numelements, TYPE(header), header->version);
	  DEBUGSTMSTAT("WR Abort: Access Count: %u AbortCount: %u type: %u ver: %u \n", header->accessCount, header->abortCount, TYPE(header), header->version);
	  if (c_numelements>=200) {
	    free(oidrdlocked);
	    free(oidrdversion);
	    free(oidwrlocked);
	  }
	  return TRANS_ABORT;
	}
      }
    } else {
      /* Read from the main heap  and compare versions */
      oidrdversion[numoidrdlocked]=version;
      oidrdlocked[numoidrdlocked++] = header;
    }
    curr = curr->lnext;
  }
  //THIS IS THE SERIALIZATION POINT *****
  for(i=0; i<numoidrdlocked; i++) {
    objheader_t * header = oidrdlocked[i];
    unsigned int version=oidrdversion[i];
    if(header->lock>=0) {
      if(version != header->version) {
	transAbortProcess(oidwrlocked, numoidwrlocked);
#ifdef STMSTATS
	ABORTCOUNT(header);
	(typesCausingAbort[TYPE(header)])++;
	getTotalAbortCount(i+1, numoidrdlocked, oidrdlocked, (void *)oidrdversion, 0);
#endif
	DEBUGSTM("RD Abort: rd: %u wr: %u tot: %u type: %u ver: %u\n", numoidrdlocked, numoidwrlocked, c_numelements, TYPE(header), header->version);
	DEBUGSTMSTAT("RD Abort: Access Count: %u AbortCount: %u type: %u ver: %u \n", header->accessCount, header->abortCount, TYPE(header), header->version);
	if (c_numelements>=200) {
	  free(oidrdlocked);
	  free(oidrdversion);
	  free(oidwrlocked);
	}
	return TRANS_ABORT;
      }
    } else { /* cannot aquire lock */
      if(version == header->version) {
	softabort=1;
      } else {
	transAbortProcess(oidwrlocked, numoidwrlocked);
#ifdef STMSTATS
	ABORTCOUNT(header);
	(typesCausingAbort[TYPE(header)])++;
	getTotalAbortCount(i+1, numoidrdlocked, oidrdlocked, (void *)oidrdversion, 0);
#endif
	DEBUGSTM("RD Abort: rd: %u wr: %u tot: %u type: %u ver: %u\n", numoidrdlocked, numoidwrlocked, c_numelements, TYPE(header), header->version);
	DEBUGSTMSTAT("RD Abort: Access Count: %u AbortCount: %u type: %u ver: %u \n", header->accessCount, header->abortCount, TYPE(header), header->version);
	if (c_numelements>=200) {
	  free(oidrdlocked);
	  free(oidrdversion);
	  free(oidwrlocked);
	}
	return TRANS_ABORT;
      }
    }
  }

  /* Decide the final response */
  if (softabort) {
    transAbortProcess(oidwrlocked, numoidwrlocked);
    DEBUGSTM("Soft Abort: rd: %u wr: %u tot: %u\n", numoidrdlocked, numoidwrlocked, c_numelements);
    if (c_numelements>=200) {
      free(oidrdlocked);
      free(oidrdversion);
      free(oidwrlocked);
    }
    return TRANS_SOFT_ABORT;
  } else {
    transCommitProcess(oidwrlocked, numoidwrlocked);
    DEBUGSTM("Commit: rd: %u wr: %u tot: %u\n", numoidrdlocked, numoidwrlocked, c_numelements);
    if (c_numelements>=200) {
      free(oidrdlocked);
      free(oidrdversion);
      free(oidwrlocked);
    }
    return TRANS_COMMIT;
  }
}


/* ==================================
 * transAbortProcess
 *
 * =================================
 */
int transAbortProcess(void **oidwrlocked, int numoidwrlocked) {
  int i;
  objheader_t *header;
  /* Release read locks */

  /* Release write locks */
  for(i=0; i< numoidwrlocked; i++) {
    /* Read from the main heap */
    header = (objheader_t *)(((char *)(oidwrlocked[i])) - sizeof(objheader_t));
    write_unlock(&header->lock);
  }

#ifdef STMSTATS
  /* clear trec and then release objects locked */
  struct objlist *ptr=lockedobjs;
  while(ptr!=NULL) {
    int max=ptr->offset;
    for(i=0; i<max; i++) {
      header = (objheader_t *)((char *)(ptr->objs[i]) - sizeof(objheader_t));
      header->trec = NULL;
      pthread_mutex_unlock(&(header->objlock));
    }
    ptr=ptr->next;
  }
#endif
}

/* ==================================
 * transCommitProcess
 *
 * =================================
 */
int transCommitProcess(void ** oidwrlocked, int numoidwrlocked) {
  objheader_t *header;
  void *ptrcreate;
  int i;
  struct objlist *ptr=newobjs;
  while(ptr!=NULL) {
    int max=ptr->offset;
    for(i=0; i<max; i++) {
      //clear the new flag
      ((struct ___Object___ *)ptr->objs[i])->___objstatus___=0;
    }
    ptr=ptr->next;
  }

  /* Copy from transaction cache -> main object store */
  for (i = 0; i < numoidwrlocked; i++) {
    /* Read from the main heap */
    header = (objheader_t *)(((char *)(oidwrlocked[i])) - sizeof(objheader_t));
    int tmpsize;
    GETSIZE(tmpsize, header);
    struct ___Object___ *dst=(struct ___Object___*)oidwrlocked[i];
    struct ___Object___ *src=t_chashSearch(oidwrlocked[i]);
    dst->___cachedCode___=src->___cachedCode___;
    dst->___cachedHash___=src->___cachedHash___;
    memcpy(&dst[1], &src[1], tmpsize-sizeof(struct ___Object___));
    header->version += 1;
  }

  /* Release write locks */
  for(i=0; i< numoidwrlocked; i++) {
    header = (objheader_t *)(((char *)(oidwrlocked[i])) - sizeof(objheader_t));
    write_unlock(&header->lock);
  }

#ifdef STMSTATS
  /* clear trec and then release objects locked */
  ptr=lockedobjs;
  while(ptr!=NULL) {
    int max=ptr->offset;
    for(i=0; i<max; i++) {
      header = (objheader_t *)(((char *)(ptr->objs[i])) - sizeof(objheader_t));
      header->trec = NULL;
      pthread_mutex_unlock(&(header->objlock));
    }
    ptr=ptr->next;
  }
#endif
  return 0;
}

/** ========================================================================================
 * getTotalAbortCount
 * params : start: start index of the loop
 *        : stop: stop index of the loop
 *        : startptr: pointer that points to where to start looking in the array/ linked list
 *          0='r'/1='w' if found when visiting objects read/ objects modified
 * =========================================================================================
 **/
#ifdef STMSTATS
void getTotalAbortCount(int start, int stop, void *startptr, void *checkptr, int type) {
  int i;
  if(type) {
    int isFirstTime = 0;
    chashlistnode_t *curr = (chashlistnode_t *) startptr;
    chashlistnode_t *ptr = c_table;
    for(i = start; i < stop; i++) {
      if(!isFirstTime)
	curr = &ptr[i];
      /* Inner loop to traverse the linked list of the cache lookupTable */
      while(curr != NULL) {
	if(curr->key == NULL)
	  break;
	objheader_t * headeraddr=&((objheader_t *) curr->val)[-1];
	objheader_t *header=(objheader_t *)(((char *)curr->key)-sizeof(objheader_t));
	unsigned int version = headeraddr->version;
	/* versions do not match */
	if(version != header->version) {
	  ABORTCOUNT(header);
	  (typesCausingAbort[TYPE(header)])++;
	}
	curr = curr->next;
      }
      isFirstTime = 1;
    }
  } else {
    /* Go through oids read that are locked */
    for(i = start; i < stop; i++) {
      objheader_t *header = ((void **)startptr)[i];
      unsigned int version = ((int *)checkptr)[i];
      if(version != header->version) { /* versions do not match */
	ABORTCOUNT(header);
	(typesCausingAbort[TYPE(header)])++;
      }
    }
  }
}

/**
 * needLock
 * params: Object header
 * Locks an object that causes aborts
 **/
void needLock(objheader_t *header) {
  int lockstatus;
  threadrec_t *ptr;
  while((lockstatus = pthread_mutex_trylock(&(header->objlock))) 
      && ((ptr = header->trec) == NULL)) { //retry
    ;
  }
  if(lockstatus==0) { //acquired lock
    /* Set trec */
    header->trec = trec;
  } else { //failed to get lock
    trec->blocked=1;
    //memory barrier
    __asm__ __volatile__("":::"memory");
    //see if other thread is blocked
    if(ptr->blocked == 1) {
      //it might be block, so ignore lock and clear our blocked flag
      trec->blocked=0;
      return;
    } else { 
      //grab lock and wait our turn
      pthread_mutex_lock(&(header->objlock));
      /* we have lock, so we are not blocked anymore */
      trec->blocked = 0;
      /* Set our trec */
      header->trec = trec;
    }
  }
  //trec->blocked is zero now

  /* Save the locked object */
  if (lockedobjs->offset<MAXOBJLIST) {
    lockedobjs->objs[lockedobjs->offset++]=OID(header);
  } else {
    struct objlist *tmp=malloc(sizeof(struct objlist));
    tmp->next=lockedobjs;
    tmp->objs[0]=OID(header);
    tmp->offset=1;
    lockedobjs=tmp;
  }
}
#endif
