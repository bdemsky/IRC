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
#define likely(x) x
/* Per thread transaction variables */
__thread objstr_t *t_cache;
__thread objstr_t *t_reserve;
__thread struct objlist * newobjs;

#ifdef DELAYCOMP
#include "delaycomp.h"
__thread struct pointerlist ptrstack;
__thread struct primitivelist primstack;
__thread struct branchlist branchstack;
struct pointerlist *c_ptrstack;
struct primitivelist *c_primstack;
struct branchlist *c_branchstack;
#endif

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
/* TODO uncomment for later use
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

#ifdef FASTMEMCPY
void * A_memcpy (void * dest, const void * src, size_t count);
#else
#define A_memcpy memcpy
#endif

extern void * curr_heapbase;
extern void * curr_heapptr;
extern void * curr_heaptop;

#ifdef STMSTATS
/*** Global variables *****/
objlockstate_t *objlockscope;
/**
 * ABORTCOUNT
 * params: object header
 * Increments the abort count for each object
 **/
void ABORTCOUNT(objheader_t * x) {
  x->abortCount++;  
  if (x->abortCount > MAXABORTS && (x->riskyflag != 1)) {	 
    //makes riskflag sticky
    pthread_mutex_lock(&lockedobjstore); 
    if (objlockscope->offset<MAXOBJLIST) { 
      x->objlock=&(objlockscope->lock[objlockscope->offset++]);
    } else { 
      objlockstate_t *tmp=malloc(sizeof(objlockstate_t)); 
      tmp->next=objlockscope; 
      tmp->offset=1; 
      x->objlock=&(tmp->lock[0]); 
      objlockscope=tmp;
    } 
    pthread_mutex_unlock(&lockedobjstore); 
    pthread_mutex_init(x->objlock, NULL);
    //should put a memory barrier here
    x->riskyflag = 1;			 
  }
}
#endif

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
#ifdef DELAYCOMP
  c_ptrstack=&ptrstack;
  c_primstack=&primstack;
  c_branchstack=&branchstack;
#endif
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
  //initialize obj lock to the header
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
__attribute__ ((pure)) void *transRead(void * oid, void *gl) {
  objheader_t *tmp, *objheader;
  objheader_t *objcopy;
  int size;

  /* Read from the main heap */
  //No lock for now
  objheader_t *header = (objheader_t *)(((char *)oid) - sizeof(objheader_t));
  GETSIZE(size, header);
  size += sizeof(objheader_t);
  objcopy = (objheader_t *) objstrAlloc(size);
#ifdef STMSTATS
  header->accessCount++;
  if(header->riskyflag) {
    header=needLock(header,gl);
  }
#endif
  A_memcpy(objcopy, header, size);
  /* Insert into cache's lookup table */
  STATUS(objcopy)=0;
  if (((unsigned INTPTR)oid)<((unsigned INTPTR ) curr_heapbase)|| ((unsigned INTPTR)oid) >((unsigned INTPTR) curr_heapptr))
    printf("ERROR! Bad object address!\n");
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
#ifdef DELAYCOMP
int transCommit(void (*commitmethod)(void *, void *, void *), void * primitives, void * locals, void * params) {
#else
int transCommit() {
#endif
  int softaborted=0;
  do {
    /* Look through all the objects in the transaction hash table */
    int finalResponse;
#ifdef DELAYCOMP
    if (c_numelements<(c_size>>3))
      finalResponse= alttraverseCache(commitmethod, primitives, locals, params);
    else
      finalResponse= traverseCache(commitmethod, primitives, locals, params);
#else
    if (c_numelements<(c_size>>3))
      finalResponse= alttraverseCache();
    else
      finalResponse= traverseCache();
#endif
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
#ifdef READSET
      rd_t_chashreset();
#endif
#ifdef DELAYCOMP
      dc_t_chashreset();
      ptrstack.count=0;
      primstack.count=0;
      branchstack.count=0;
#endif
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
#ifdef READSET
      rd_t_chashreset();
#endif
#ifdef DELAYCOMP
      dc_t_chashreset();
      ptrstack.count=0;
      primstack.count=0;
      branchstack.count=0;
#endif
      return 0;
    }

    /* wait a random amount of time before retrying to commit transaction*/
    if(finalResponse == TRANS_SOFT_ABORT) {
#ifdef TRANSSTATS
      nSoftAbort++;
#endif
      softaborted++;
#ifdef SOFTABORT
      if (softaborted>1) {
#else
      if (1) {
#endif
	//retry if too many soft aborts
	freenewobjs();
#ifdef STMSTATS
    freelockedobjs();
#endif
	objstrReset();
	t_chashreset();
#ifdef READSET
	rd_t_chashreset();
#endif
#ifdef DELAYCOMP
	dc_t_chashreset();
	ptrstack.count=0;
	primstack.count=0;
	branchstack.count=0;
#endif
	return TRANS_ABORT;
      }
      //randomdelay(softaborted);
    } else {
      printf("Error: in %s() Unknown outcome", __func__);
      exit(-1);
    }
  } while (1);
}

#ifdef DELAYCOMP
#define freearrays   if (c_numelements>=200) { \
    free(oidrdlocked); \
    free(oidrdversion); \
  } \
  if (t_numelements>=200) { \
    free(oidwrlocked); \
  }
#else
#define freearrays   if (c_numelements>=200) { \
    free(oidrdlocked); \
    free(oidrdversion); \
    free(oidwrlocked); \
  }
#endif

#ifdef DELAYCOMP
#define allocarrays int t_numelements=c_numelements+dc_c_numelements; \
  if (t_numelements<200) { \
    oidwrlocked=wrlocked; \
  } else { \
    oidwrlocked=malloc(t_numelements*sizeof(void *)); \
  } \
  if (c_numelements<200) { \
    oidrdlocked=rdlocked; \
    oidrdversion=rdversion; \
  } else { \
    int size=c_numelements*sizeof(void*); \
    oidrdlocked=malloc(size); \
    oidrdversion=malloc(size); \
  }
#else
#define allocarrays if (c_numelements<200) { \
    oidrdlocked=rdlocked; \
    oidrdversion=rdversion; \
    oidwrlocked=wrlocked; \
  } else { \
    int size=c_numelements*sizeof(void*); \
    oidrdlocked=malloc(size); \
    oidrdversion=malloc(size); \
    oidwrlocked=malloc(size); \
  }
#endif




/* ==================================================
 * traverseCache
 * - goes through the transaction cache and
 * - decides if a transaction should commit or abort
 * ==================================================
 */
#ifdef DELAYCOMP
int traverseCache(void (*commitmethod)(void *, void *, void *), void * primitives, void * locals, void * params) {
#else
int traverseCache() {
#endif
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
  allocarrays;

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
	    oidwrlocked[numoidwrlocked++] = header;
	  } else {
	    oidwrlocked[numoidwrlocked++] = header;
	    transAbortProcess(oidwrlocked, numoidwrlocked);
#ifdef STMSTATS
	    ABORTCOUNT(header);
	    (typesCausingAbort[TYPE(header)])++;
        getTotalAbortCount(i+1, size, (void *)(curr->next), numoidrdlocked, oidrdlocked, oidrdversion);
#endif
	    DEBUGSTM("WR Abort: rd: %u wr: %u tot: %u type: %u ver: %u\n", numoidrdlocked, numoidwrlocked, c_numelements, TYPE(header), header->version);
	    DEBUGSTMSTAT("WR Abort: Access Count: %u AbortCount: %u type: %u ver: %u \n", header->accessCount, header->abortCount, TYPE(header), header->version);
	    freearrays;
	    if (softabort)
	    return TRANS_SOFT_ABORT;
	      else 
	    return TRANS_ABORT;
	  }
	} else {
	  if(version == header->version) {
	    /* versions match */
	    softabort=1;
	  }
	  transAbortProcess(oidwrlocked, numoidwrlocked);
#ifdef STMSTATS
	  ABORTCOUNT(header);
	  (typesCausingAbort[TYPE(header)])++;
#endif
#if defined(STMSTATS)||defined(SOFTABORT)
      if(getTotalAbortCount(i+1, size, (void *)(curr->next), numoidrdlocked, oidrdlocked, oidrdversion))
	    softabort=0;
#endif
	  DEBUGSTM("WR Abort: rd: %u wr: %u tot: %u type: %u ver: %u\n", numoidrdlocked, numoidwrlocked, c_numelements, TYPE(header), header->version);
	  DEBUGSTMSTAT("WR Abort: Access Count: %u AbortCount: %u type: %u ver: %u \n", header->accessCount, header->abortCount, TYPE(header), header->version);
	  freearrays;
	  if (softabort)
	    return TRANS_SOFT_ABORT;
	  else 
	    return TRANS_ABORT;
      
	}
      } else {
	oidrdversion[numoidrdlocked]=version;
	oidrdlocked[numoidrdlocked++] = header;
      }
      curr = curr->next;
    }
  } //end of for

#ifdef DELAYCOMP
  //acquire access set locks
  unsigned int numoidwrtotal=numoidwrlocked;

  chashlistnode_t *dc_curr = dc_c_list;
  /* Inner loop to traverse the linked list of the cache lookupTable */
  while(likely(dc_curr != NULL)) {
    //if the first bin in hash table is empty
    objheader_t * headeraddr=&((objheader_t *) dc_curr->val)[-1];
    objheader_t *header=(objheader_t *)(((char *)dc_curr->key)-sizeof(objheader_t));
    if(write_trylock(&header->lock)) { //can aquire write lock    
      oidwrlocked[numoidwrtotal++] = header;
    } else {
      //maybe we already have lock
      void * key=dc_curr->key;
      chashlistnode_t *node = &c_table[(((unsigned INTPTR)key) & c_mask)>>4];
      
      do {
	if(node->key == key) {
	  objheader_t * headeraddr=&((objheader_t *) node->val)[-1];	  
	  if(STATUS(headeraddr) & DIRTY) {
	    goto nextloop;
	  } else
	    break;
	}
	node = node->next;
      } while(node != NULL);

      //have to abort to avoid deadlock
      transAbortProcess(oidwrlocked, numoidwrtotal);
#ifdef STMSTATS
      ABORTCOUNT(header);
      (typesCausingAbort[TYPE(header)])++;
#endif
#if defined(STMSTATS)||defined(SOFTABORT)
      if(getTotalAbortCount(i+1, size, (void *)(curr->next), numoidrdlocked, oidrdlocked, oidrdversion))
	softabort=0;
#endif
      DEBUGSTM("WR Abort: rd: %u wr: %u tot: %u type: %u ver: %u\n", numoidrdlocked, numoidwrlocked, c_numelements, TYPE(header), header->version);
      DEBUGSTMSTAT("WR Abort: Access Count: %u AbortCount: %u type: %u ver: %u \n", header->accessCount, header->abortCount, TYPE(header), header->version);
      freearrays;
      if (softabort)
	return TRANS_SOFT_ABORT;
      else
	return TRANS_ABORT;
    }
  nextloop:
    dc_curr = dc_curr->lnext;
  }
#endif

  //THIS IS THE SERIALIZATION END POINT (START POINT IS END OF EXECUTION)*****

  for(i=0; i<numoidrdlocked; i++) {
    /* Read from the main heap  and compare versions */
    objheader_t *header=oidrdlocked[i];
    unsigned int version=oidrdversion[i];
    if(header->lock>0) { //not write locked
      if(version != header->version) { /* versions do not match */
#ifdef DELAYCOMP
	transAbortProcess(oidwrlocked, numoidwrtotal);
#else
	transAbortProcess(oidwrlocked, numoidwrlocked);
#endif
#ifdef STMSTATS
	ABORTCOUNT(header);
	(typesCausingAbort[TYPE(header)])++;
	getReadAbortCount(i+1, numoidrdlocked, oidrdlocked, oidrdversion);
#endif
	DEBUGSTM("RD Abort: rd: %u wr: %u tot: %u type: %u ver: %u\n", numoidrdlocked, numoidwrlocked, c_numelements, TYPE(header), header->version);
	DEBUGSTMSTAT("RD Abort: Access Count: %u AbortCount: %u type: %u ver: %u \n", header->accessCount, header->abortCount, TYPE(header), header->version);
	freearrays;
	return TRANS_ABORT;
      }
#if DELAYCOMP
    } else if (dc_t_chashSearch(((char *)header)+sizeof(objheader_t))!=NULL) {
      //couldn't get lock because we already have it
      //check if it is the right version number
      if (version!=header->version) {
	transAbortProcess(oidwrlocked, numoidwrtotal);
#ifdef STMSTATS
	ABORTCOUNT(header);
	(typesCausingAbort[TYPE(header)])++;
	getReadAbortCount(i+1, numoidrdlocked, oidrdlocked, oidrdversion);
#endif
	DEBUGSTM("RD Abort: rd: %u wr: %u tot: %u type: %u ver: %u\n", numoidrdlocked, numoidwrlocked, c_numelements, TYPE(header), header->version);
	DEBUGSTMSTAT("RD Abort: Access Count: %u AbortCount: %u type: %u ver: %u \n", header->accessCount, header->abortCount, TYPE(header), header->version);
	freearrays;
	return TRANS_ABORT;
      }
#endif
    } else { /* cannot aquire lock */
      //do increment as we didn't get lock
      if(version == header->version) {
	softabort=1;
      }
#ifdef DELAYCOMP
      transAbortProcess(oidwrlocked, numoidwrtotal);
#else
      transAbortProcess(oidwrlocked, numoidwrlocked);
#endif
#ifdef STMSTATS
      ABORTCOUNT(header);
      (typesCausingAbort[TYPE(header)])++;
#endif
#if defined(STMSTATS)||defined(SOFTABORT)
      if(getReadAbortCount(i+1, numoidrdlocked, oidrdlocked, oidrdversion))
	softabort=0;
#endif
      DEBUGSTM("RD Abort: rd: %u wr: %u tot: %u type: %u ver: %u\n", numoidrdlocked, numoidwrlocked, c_numelements, TYPE(header), header->version);
      DEBUGSTMSTAT("RD Abort: Access Count: %u AbortCount: %u type: %u ver: %u \n", header->accessCount, header->abortCount, TYPE(header), header->version);
      freearrays;
      if (softabort)
	return TRANS_SOFT_ABORT;
      else 
	return TRANS_ABORT;
    }
  }

#ifdef READSET
  //need to validate auxilary readset
  rdchashlistnode_t *rd_curr = rd_c_list;
  /* Inner loop to traverse the linked list of the cache lookupTable */
  while(likely(rd_curr != NULL)) {
    //if the first bin in hash table is empty
    unsigned int version=rd_curr->version;
    objheader_t *header=(objheader_t *)(((char *)rd_curr->key)-sizeof(objheader_t));
    if(header->lock>0) { //object is not locked
      if (version!=header->version) {
	//have to abort
#ifdef DELAYCOMP
	transAbortProcess(oidwrlocked, numoidwrtotal);
#else
	transAbortProcess(oidwrlocked, numoidwrlocked);
#endif
#ifdef STMSTATS
	ABORTCOUNT(header);
	(typesCausingAbort[TYPE(header)])++;
#endif
#if defined(STMSTATS)||defined(SOFTABORT)
	if(getTotalAbortCount2((void *) curr->next, numoidrdlocked, oidrdlocked, oidrdversion))
	  softabort=0;
#endif
	DEBUGSTM("WR Abort: rd: %u wr: %u tot: %u type: %u ver: %u\n", numoidrdlocked, numoidwrlocked, c_numelements, TYPE(header), header->version);
	DEBUGSTMSTAT("WR Abort: Access Count: %u AbortCount: %u type: %u ver: %u \n", header->accessCount, header->abortCount, TYPE(header), header->version);
	freearrays;
	if (softabort)
	  return TRANS_SOFT_ABORT;
	else
	  return TRANS_ABORT;	
      }
    } else {
      //maybe we already have lock
      if (version==header->version) {
	void * key=rd_curr->key;
#ifdef DELAYCOMP
	//check to see if it is in the delaycomp table
	{
	  chashlistnode_t *node = &dc_c_table[(((unsigned INTPTR)key) & dc_c_mask)>>4];
	  do {
	    if(node->key == key)
	      goto nextloopread;
	    node = node->next;
	  } while(node != NULL);
	}
#endif
	//check normal table
	{
	  chashlistnode_t *node = &c_table[(((unsigned INTPTR)key) & c_mask)>>4];
	  do {
	    if(node->key == key) {
	      objheader_t * headeraddr=&((objheader_t *) node->val)[-1];	  
	      if(STATUS(headeraddr) & DIRTY) {
		goto nextloopread;
	      }
	    }
	    node = node->next;
	  } while(node != NULL);
	}
      }
#ifdef DELAYCOMP
      //have to abort to avoid deadlock
      transAbortProcess(oidwrlocked, numoidwrtotal);
#else
      transAbortProcess(oidwrlocked, numoidwrlocked);
#endif

#ifdef STMSTATS
      ABORTCOUNT(header);
      (typesCausingAbort[TYPE(header)])++;
#endif
#if defined(STMSTATS)||defined(SOFTABORT)
      if(getTotalAbortCount2((void *) curr->next, numoidrdlocked, oidrdlocked, oidrdversion))
	softabort=0;
#endif
      DEBUGSTM("WR Abort: rd: %u wr: %u tot: %u type: %u ver: %u\n", numoidrdlocked, numoidwrlocked, c_numelements, TYPE(header), header->version);
      DEBUGSTMSTAT("WR Abort: Access Count: %u AbortCount: %u type: %u ver: %u \n", header->accessCount, header->abortCount, TYPE(header), header->version);
      freearrays;
      if (softabort)
	return TRANS_SOFT_ABORT;
      else
	return TRANS_ABORT;
    }
  nextloopread:
    rd_curr = rd_curr->lnext;
  }
#endif
  
  /* Decide the final response */
#ifdef DELAYCOMP
  transCommitProcess(oidwrlocked, numoidwrlocked, numoidwrtotal, commitmethod, primitives, locals, params);
#else
  transCommitProcess(oidwrlocked, numoidwrlocked);
#endif
  DEBUGSTM("Commit: rd: %u wr: %u tot: %u\n", numoidrdlocked, numoidwrlocked, c_numelements);
  freearrays;
  return TRANS_COMMIT;
}

/* ==================================================
 * alttraverseCache
 * - goes through the transaction cache and
 * - decides if a transaction should commit or abort
 * ==================================================
 */

#ifdef DELAYCOMP
int alttraverseCache(void (*commitmethod)(void *, void *, void *), void * primitives, void * locals, void * params) {
#else
int alttraverseCache() {
#endif
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
  allocarrays;

  chashlistnode_t *curr = c_list;
  /* Inner loop to traverse the linked list of the cache lookupTable */
  while(likely(curr != NULL)) {
    //if the first bin in hash table is empty
    objheader_t * headeraddr=&((objheader_t *) curr->val)[-1];
    objheader_t *header=(objheader_t *)(((char *)curr->key)-sizeof(objheader_t));
    unsigned int version = headeraddr->version;

    if(STATUS(headeraddr) & DIRTY) {
      /* Read from the main heap  and compare versions */
      if(likely(write_trylock(&header->lock))) { //can aquire write lock
	if (likely(version == header->version)) { /* versions match */
	  /* Keep track of objects locked */
	  oidwrlocked[numoidwrlocked++] = header;
	} else {
	  oidwrlocked[numoidwrlocked++] = header;
	  transAbortProcess(oidwrlocked, numoidwrlocked);
#ifdef STMSTATS
	  ABORTCOUNT(header);
	  (typesCausingAbort[TYPE(header)])++;
      getTotalAbortCount2((void *) curr->next, numoidrdlocked, oidrdlocked, oidrdversion);
#endif
	  DEBUGSTM("WR Abort: rd: %u wr: %u tot: %u type: %u ver: %u\n", numoidrdlocked, numoidwrlocked, c_numelements, TYPE(header), header->version);
	  DEBUGSTMSTAT("WR Abort: Access Count: %u AbortCount: %u type: %u ver: %u \n", header->accessCount, header->abortCount, TYPE(header), header->version);
	  freearrays;
	  return TRANS_ABORT;
	}
      } else { /* cannot aquire lock */
	if(version == header->version) {
	  /* versions match */
	  softabort=1;
	}
	transAbortProcess(oidwrlocked, numoidwrlocked);
#ifdef STMSTATS
	ABORTCOUNT(header);
	(typesCausingAbort[TYPE(header)])++;
#endif
#if defined(STMSTATS)||defined(SOFTABORT)
    if(getTotalAbortCount2((void *) curr->next, numoidrdlocked, oidrdlocked, oidrdversion))
	  softabort=0;
#endif
	DEBUGSTM("WR Abort: rd: %u wr: %u tot: %u type: %u ver: %u\n", numoidrdlocked, numoidwrlocked, c_numelements, TYPE(header), header->version);
	DEBUGSTMSTAT("WR Abort: Access Count: %u AbortCount: %u type: %u ver: %u \n", header->accessCount, header->abortCount, TYPE(header), header->version);
	freearrays;
	if (softabort)
	  return TRANS_SOFT_ABORT;
	else 
	  return TRANS_ABORT;
      }
    } else {
      /* Read from the main heap  and compare versions */
      oidrdversion[numoidrdlocked]=version;
      oidrdlocked[numoidrdlocked++] = header;
    }
    curr = curr->lnext;
  }

#ifdef DELAYCOMP
  //acquire other locks
  unsigned int numoidwrtotal=numoidwrlocked;
  chashlistnode_t *dc_curr = dc_c_list;
  /* Inner loop to traverse the linked list of the cache lookupTable */
  while(likely(dc_curr != NULL)) {
    //if the first bin in hash table is empty
    objheader_t * headeraddr=&((objheader_t *) dc_curr->val)[-1];
    objheader_t *header=(objheader_t *)(((char *)dc_curr->key)-sizeof(objheader_t));
    if(write_trylock(&header->lock)) { //can aquire write lock
      oidwrlocked[numoidwrtotal++] = header;
    } else {
      //maybe we already have lock
      void * key=dc_curr->key;
      chashlistnode_t *node = &c_table[(((unsigned INTPTR)key) & c_mask)>>4];
      
      do {
	if(node->key == key) {
	  objheader_t * headeraddr=&((objheader_t *) node->val)[-1];
	  if(STATUS(headeraddr) & DIRTY) {
	    goto nextloop;
	  }
	}
	node = node->next;
      } while(node != NULL);

      //have to abort to avoid deadlock
      transAbortProcess(oidwrlocked, numoidwrtotal);
#ifdef STMSTATS
      ABORTCOUNT(header);
      (typesCausingAbort[TYPE(header)])++;
#endif
#if defined(STMSTATS)||defined(SOFTABORT)
      if(getTotalAbortCount2((void *) curr->next, numoidrdlocked, oidrdlocked, oidrdversion))
	softabort=0;
#endif
      DEBUGSTM("WR Abort: rd: %u wr: %u tot: %u type: %u ver: %u\n", numoidrdlocked, numoidwrlocked, c_numelements, TYPE(header), header->version);
      DEBUGSTMSTAT("WR Abort: Access Count: %u AbortCount: %u type: %u ver: %u \n", header->accessCount, header->abortCount, TYPE(header), header->version);
      freearrays;
      if (softabort)
	return TRANS_SOFT_ABORT;
      else
	return TRANS_ABORT;
    }
  nextloop:
    dc_curr = dc_curr->lnext;
  }
#endif

  //THIS IS THE SERIALIZATION END POINT (START POINT IS END OF EXECUTION)*****

  for(i=0; i<numoidrdlocked; i++) {
    objheader_t * header=oidrdlocked[i];
    unsigned int version=oidrdversion[i];
    if(header->lock>=0) {
      if(version != header->version) {
#ifdef DELAYCOMP
	transAbortProcess(oidwrlocked, numoidwrtotal);
#else
	transAbortProcess(oidwrlocked, numoidwrlocked);
#endif
#ifdef STMSTATS
	ABORTCOUNT(header);
	(typesCausingAbort[TYPE(header)])++;
	getReadAbortCount(i+1, numoidrdlocked, oidrdlocked, oidrdversion);
#endif
	DEBUGSTM("RD Abort: rd: %u wr: %u tot: %u type: %u ver: %u\n", numoidrdlocked, numoidwrlocked, c_numelements, TYPE(header), header->version);
	DEBUGSTMSTAT("RD Abort: Access Count: %u AbortCount: %u type: %u ver: %u \n", header->accessCount, header->abortCount, TYPE(header), header->version);
	freearrays;
	return TRANS_ABORT;
      }
#ifdef DELAYCOMP
    } else if (dc_t_chashSearch(((char *)header)+sizeof(objheader_t))!=NULL) {
      //couldn't get lock because we already have it
      //check if it is the right version number
      if (version!=header->version) {
	transAbortProcess(oidwrlocked, numoidwrtotal);
#ifdef STMSTATS
	ABORTCOUNT(header);
	(typesCausingAbort[TYPE(header)])++;
	getReadAbortCount(i+1, numoidrdlocked, oidrdlocked, oidrdversion);
#endif
	DEBUGSTM("RD Abort: rd: %u wr: %u tot: %u type: %u ver: %u\n", numoidrdlocked, numoidwrlocked, c_numelements, TYPE(header), header->version);
	DEBUGSTMSTAT("RD Abort: Access Count: %u AbortCount: %u type: %u ver: %u \n", header->accessCount, header->abortCount, TYPE(header), header->version);
	freearrays;
	return TRANS_ABORT;
      }
#endif
    } else { /* cannot aquire lock */
      if(version == header->version) {
	softabort=1;
      }
#ifdef DELAYCOMP
      transAbortProcess(oidwrlocked, numoidwrtotal);
#else
      transAbortProcess(oidwrlocked, numoidwrlocked);
#endif
#ifdef STMSTATS
      ABORTCOUNT(header);
      (typesCausingAbort[TYPE(header)])++;
#endif
#if defined(STMSTATS)||defined(SOFTABORT)
      if(getReadAbortCount(i+1, numoidrdlocked, oidrdlocked, oidrdversion))
	softabort=0;
#endif
      DEBUGSTM("RD Abort: rd: %u wr: %u tot: %u type: %u ver: %u\n", numoidrdlocked, numoidwrlocked, c_numelements, TYPE(header), header->version);
      DEBUGSTMSTAT("RD Abort: Access Count: %u AbortCount: %u type: %u ver: %u \n", header->accessCount, header->abortCount, TYPE(header), header->version);
      freearrays;
      if (softabort)
	return TRANS_SOFT_ABORT;
      else 
	return TRANS_ABORT;
    }
  }

#ifdef READSET
  //need to validate auxilary readset
  rdchashlistnode_t *rd_curr = rd_c_list;
  /* Inner loop to traverse the linked list of the cache lookupTable */
  while(likely(rd_curr != NULL)) {
    //if the first bin in hash table is empty
    int version=rd_curr->version;
    objheader_t *header=(objheader_t *)(((char *)rd_curr->key)-sizeof(objheader_t));
    if(header->lock>0) { //object is not locked
      if (version!=header->version) {
	//have to abort
#ifdef DELAYCOMP
	transAbortProcess(oidwrlocked, numoidwrtotal);
#else
	transAbortProcess(oidwrlocked, numoidwrlocked);
#endif
#ifdef STMSTATS
	ABORTCOUNT(header);
	(typesCausingAbort[TYPE(header)])++;
#endif
#if defined(STMSTATS)||defined(SOFTABORT)
	if(getTotalAbortCount2((void *) curr->next, numoidrdlocked, oidrdlocked, oidrdversion))
	  softabort=0;
#endif
	DEBUGSTM("WR Abort: rd: %u wr: %u tot: %u type: %u ver: %u\n", numoidrdlocked, numoidwrlocked, c_numelements, TYPE(header), header->version);
	DEBUGSTMSTAT("WR Abort: Access Count: %u AbortCount: %u type: %u ver: %u \n", header->accessCount, header->abortCount, TYPE(header), header->version);
	freearrays;
	if (softabort)
	  return TRANS_SOFT_ABORT;
	else
	  return TRANS_ABORT;	
      }
    } else {
      if (version==header->version) {
	void * key=rd_curr->key;
#ifdef DELAYCOMP
	//check to see if it is in the delaycomp table
	{
	  chashlistnode_t *node = &dc_c_table[(((unsigned INTPTR)key) & dc_c_mask)>>4];
	  do {
	    if(node->key == key)
	      goto nextloopread;
	    node = node->next;
	  } while(node != NULL);
	}
#endif
	//check normal table
	{
	  chashlistnode_t *node = &c_table[(((unsigned INTPTR)key) & c_mask)>>4];
	  do {
	    if(node->key == key) {
	      objheader_t * headeraddr=&((objheader_t *) node->val)[-1];	  
	      if(STATUS(headeraddr) & DIRTY) {
		goto nextloopread;
	      }
	    }
	    node = node->next;
	  } while(node != NULL);
	}
      }
#ifdef DELAYCOMP
	//have to abort to avoid deadlock
	transAbortProcess(oidwrlocked, numoidwrtotal);
#else
	transAbortProcess(oidwrlocked, numoidwrlocked);
#endif
#ifdef STMSTATS
      ABORTCOUNT(header);
      (typesCausingAbort[TYPE(header)])++;
#endif
#if defined(STMSTATS)||defined(SOFTABORT)
      if(getTotalAbortCount2((void *) curr->next, numoidrdlocked, oidrdlocked, oidrdversion))
	softabort=0;
#endif
      DEBUGSTM("WR Abort: rd: %u wr: %u tot: %u type: %u ver: %u\n", numoidrdlocked, numoidwrlocked, c_numelements, TYPE(header), header->version);
      DEBUGSTMSTAT("WR Abort: Access Count: %u AbortCount: %u type: %u ver: %u \n", header->accessCount, header->abortCount, TYPE(header), header->version);
      freearrays;
      if (softabort)
	return TRANS_SOFT_ABORT;
      else
	return TRANS_ABORT;
    }
  nextloopread:
    rd_curr = rd_curr->lnext;
  }
#endif

  /* Decide the final response */
#ifdef DELAYCOMP
  transCommitProcess(oidwrlocked, numoidwrlocked, numoidwrtotal, commitmethod, primitives, locals, params);
#else
  transCommitProcess(oidwrlocked, numoidwrlocked);
#endif
  DEBUGSTM("Commit: rd: %u wr: %u tot: %u\n", numoidrdlocked, numoidwrlocked, c_numelements);
  freearrays;
  return TRANS_COMMIT;
}

/* ==================================
 * transAbortProcess
 *
 * =================================
 */
void transAbortProcess(void **oidwrlocked, int numoidwrlocked) {
  int i;
  objheader_t *header;
  /* Release read locks */

  /* Release write locks */
  for(i=numoidwrlocked-1; i>=0; i--) {
    /* Read from the main heap */
    header = (objheader_t *)oidwrlocked[i];
    write_unlock(&header->lock);
  }

#ifdef STMSTATS
  /* clear trec and then release objects locked */
  struct objlist *ptr=lockedobjs;
  while(ptr!=NULL) {
    int max=ptr->offset;
    for(i=max-1; i>=0; i--) {
      header = (objheader_t *)ptr->objs[i];
      header->trec = NULL;
      pthread_mutex_unlock(header->objlock);
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
#ifdef DELAYCOMP
 void transCommitProcess(void ** oidwrlocked, int numoidwrlocked, int numoidwrtotal, void (*commitmethod)(void *, void *, void *), void * primitives, void * locals, void * params) {
#else
   void transCommitProcess(void ** oidwrlocked, int numoidwrlocked) {
#endif
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
  for (i = numoidwrlocked-1; i >=0; i--) {
    /* Read from the main heap */
    header = (objheader_t *)oidwrlocked[i];
    int tmpsize;
    GETSIZE(tmpsize, header);
    struct ___Object___ *dst=(struct ___Object___*)(((char *)oidwrlocked[i])+sizeof(objheader_t));
    struct ___Object___ *src=t_chashSearch(dst);
    dst->___cachedCode___=src->___cachedCode___;
    dst->___cachedHash___=src->___cachedHash___;
    A_memcpy(&dst[1], &src[1], tmpsize-sizeof(struct ___Object___));
    __asm__ __volatile__("": : :"memory");
#ifndef DELAYCOMP
    header->version++;
#endif
  }
  __asm__ __volatile__("": : :"memory");

#ifdef DELAYCOMP
  //  call commit method
  ptrstack.count=0;
  primstack.count=0;
  branchstack.count=0;
  commitmethod(params, locals, primitives);
#endif

  /* Release write locks */
#ifdef DELAYCOMP
  for(i=numoidwrtotal-1; i>=0; i--) {
#else
  for(i=numoidwrlocked-1; i>=0; i--) {
#endif
    header = (objheader_t *)oidwrlocked[i];
#ifdef DELAYCOMP
    header->version++;
#endif
    write_unlock(&header->lock);
  }

#ifdef STMSTATS
  /* clear trec and then release objects locked */
  ptr=lockedobjs;
  while(ptr!=NULL) {
    int max=ptr->offset;
    for(i=max-1; i>=0; i--) {
      header = (objheader_t *)ptr->objs[i];
      header->trec = NULL;
      pthread_mutex_unlock(header->objlock);
    }
    ptr=ptr->next;
  }
#endif
}

#if defined(STMSTATS)||defined(SOFTABORT)
/** ========================================================================================
 * getTotalAbortCount (for traverseCache only)
 * params : start: start index of the loop
 *        : stop: stop index of the loop
 *        : startptr: pointer that points to where to start looking in the cache hash table
 *        : numoidrdlocked : number of objects read that are locked
 *        : oidrdlocked : array of objects read and currently locked
 *        : oidrdversion : array of versions of object read
 * =========================================================================================
 **/
int getTotalAbortCount(int start, int stop, void *startptr, int numoidrdlocked, void *oidrdlocked, int *oidrdversion) {
  int i;
  int hardabort=0;
  int isFirstTime=0;
  chashlistnode_t *curr = (chashlistnode_t *) startptr;
  chashlistnode_t *ptr = c_table;
  /* First go through all objects left in the cache that have not been covered yet */
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
#ifdef STMSTATS
        ABORTCOUNT(header);
        (typesCausingAbort[TYPE(header)])++;
#endif
        hardabort=1;
      }
      curr = curr->next;
    }
    isFirstTime = 1;
  }

  /* Then go through all objects that are read and are currently present in the readLockedArray */
  if(numoidrdlocked>0) {
    for(i=0; i<numoidrdlocked; i++) {
      objheader_t *header = ((void **)oidrdlocked)[i];
      unsigned int version = oidrdversion[i];
      if(version != header->version) { /* versions do not match */
#ifdef STMSTATS
        ABORTCOUNT(header);
        (typesCausingAbort[TYPE(header)])++;
#endif
        hardabort=1;
      }
    }
  }

  return hardabort;
}

/** ========================================================================================
 * getTotalAbortCount2 (for alttraverseCache only)
 * params : startptr: pointer that points to where to start looking in the cache hash table
 *        : numoidrdlocked : number of objects read that are locked
 *        : oidrdlocked : array of objects read and currently locked
 *        : oidrdversion : array of versions of object read
 * =========================================================================================
 **/
int getTotalAbortCount2(void *startptr, int numoidrdlocked, void *oidrdlocked, int *oidrdversion) {
  int hardabort=0;
  chashlistnode_t *curr = (chashlistnode_t *) startptr;
  /* Inner loop to traverse the linked list of the cache lookupTable */
  while(curr != NULL) {
    objheader_t *headeraddr=&((objheader_t *) curr->val)[-1];
    objheader_t *header=(objheader_t *)(((char *)curr->key)-sizeof(objheader_t));
    unsigned int version = headeraddr->version;
    /* versions do not match */
    if(version != header->version) {
#ifdef STMSTATS
      ABORTCOUNT(header);
      (typesCausingAbort[TYPE(header)])++;
#endif
      hardabort=1;
    }
    curr = curr->next;
  }

  /* Then go through all objects that are read and are currently present in the readLockedArray */
  if(numoidrdlocked>0) {
    int i;
    for(i=0; i<numoidrdlocked; i++) {
      objheader_t *header = ((void **)oidrdlocked)[i];
      unsigned int version = oidrdversion[i];
      if(version != header->version) { /* versions do not match */
#ifdef STMSTATS
        ABORTCOUNT(header);
        (typesCausingAbort[TYPE(header)])++;
#endif
        hardabort=1;
      }
    }
  }

  return hardabort;
}

/**
 * getReadAbortCount : Tells the number of aborts caused by objects that are read by
 *                    visiting the read array
 * params: int start, int stop are indexes to readLocked array
 *         void  *oidrdlocked = readLocked array
 *         int *oidrdversion = version array
 **/
int getReadAbortCount(int start, int stop, void *oidrdlocked, int *oidrdversion) {
  int i;
  int hardabort=0;
  /* Go through oids read that are locked */
  for(i = start; i < stop; i++) {
    objheader_t *header = ((void **)oidrdlocked)[i];
    unsigned int version = oidrdversion[i];
    if(version != header->version) { /* versions do not match */
#ifdef STMSTATS
      ABORTCOUNT(header);
      (typesCausingAbort[TYPE(header)])++;
#endif
      hardabort=1;
    }
  }
  return hardabort;
}

/**
 * needLock
 * params: Object header, ptr to garbage collector
 * Locks an object that causes aborts
 **/
objheader_t * needLock(objheader_t *header, void *gl) {
  int lockstatus;
  threadrec_t *ptr;
  while((lockstatus = pthread_mutex_trylock(header->objlock)) 
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
#ifdef PRECISE_GC
      INTPTR ptrarray[]={1, (INTPTR)gl, (INTPTR) header};
      void *lockptr=header->objlock;
      stopforgc((struct garbagelist *)ptrarray);
      //grab lock and wait our turn
      pthread_mutex_lock(lockptr);
      restartaftergc();
      header=(objheader_t *) ptrarray[2];
#else
      pthread_mutex_lock(header->objptr);
#endif
      /* we have lock, so we are not blocked anymore */
      trec->blocked = 0;
      /* Set our trec */
      header->trec = trec;
    }
  }
  //trec->blocked is zero now

  /* Save the locked object */
  if (lockedobjs->offset<MAXOBJLIST) {
    lockedobjs->objs[lockedobjs->offset++]=header;
  } else {
    struct objlist *tmp=malloc(sizeof(struct objlist));
    tmp->next=lockedobjs;
    tmp->objs[0]=header;
    tmp->offset=1;
    lockedobjs=tmp;
  }
  return header;
}

#endif
