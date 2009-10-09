#include "tm.h"
#include "garbage.h"

#ifdef STMSTATS
extern __thread threadrec_t *trec;
extern __thread struct objlist * lockedobjs;

/* Collect stats for object classes causing abort */
extern objtypestat_t typesCausingAbort[TOTALNUMCLASSANDARRAY];

INLINE void getTransSize(objheader_t *header , int *isObjTypeTraverse) {
  (typesCausingAbort[TYPE(header)]).numabort++;
  if(isObjTypeTraverse[TYPE(header)] != 1) {
    (typesCausingAbort[TYPE(header)]).numaccess+=c_numelements;
    (typesCausingAbort[TYPE(header)]).numtrans+=1; //should this count be kept per object
  }
  isObjTypeTraverse[TYPE(header)]=1;
}
#endif

#ifdef STMSTATS
#define DEBUGSTMSTAT(args...)
#else
#define DEBUGSTMSTAT(args...)
#endif

#ifdef STMDEBUG
#define DEBUGSTM(x...) printf(x);
#else
#define DEBUGSTM(x...);
#endif

#ifdef STATDEBUG
#define DEBUGSTATS(x...) printf(x);
#else
#define DEBUGSTATS(x...);
#endif

#ifdef STMSTATS
/*** Global variables *****/
objlockstate_t *objlockscope;
/**
 * ABORTCOUNT
 * params: object header
 * Increments the abort count for each object
 **/
void ABORTCOUNT(objheader_t * x) {
  int avgTransSize = typesCausingAbort[TYPE(x)].numaccess / typesCausingAbort[TYPE(x)].numtrans; 
  float transAbortProbForObj = (PERCENT_ALLOWED_ABORT*FACTOR)/(float) avgTransSize;
  float ObjAbortProb = x->abortCount/(float) (x->accessCount);
  DEBUGSTM("ABORTSTATS: oid= %x, type= %2d, transAbortProb= %2.2f, ObjAbortProb= %2.2f, Typenumaccess= %3d, avgtranssize = %2d, ObjabortCount= %2d, ObjaccessCount= %3d\n", OID(x), TYPE(x), transAbortProbForObj, ObjAbortProb, typesCausingAbort[TYPE(x)].numaccess, avgTransSize, x->abortCount, x->accessCount);
  /* Condition for locking objects */
  if (((ObjAbortProb*100) >= transAbortProbForObj) && (x->riskyflag != 1)) {	 
    DEBUGSTATS("AFTER LOCK ABORTSTATS: oid= %x, type= %2d, transAbortProb= %2.2f, ObjAbortProb= %2.2f, Typenumaccess= %3d, avgtranssize = %2d, ObjabortCount= %2d, ObjaccessCount= %3d\n", OID(x), TYPE(x), transAbortProbForObj, ObjAbortProb, typesCausingAbort[TYPE(x)].numaccess, avgTransSize, x->abortCount, x->accessCount);
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

#if defined(STMSTATS)||defined(SOFTABORT)
/** ========================================================================================
 * getTotalAbortCount (for traverseCache only)
 * params : start: start index of the loop
 *        : stop: stop index of the loop
 *        : startptr: pointer that points to where to start looking in the cache hash table
 *        : numoidrdlocked : number of objects read that are locked
 *        : oidrdlocked : array of objects read and currently locked
 *        : oidrdversion : array of versions of object read
 *        : oidrdage : array of ages of objects read ina transaction cache
 *        : ObjSeqId : sequence Id/age to start the comparision with
 * =========================================================================================
 **/
int getTotalAbortCount(int start, int stop, void *startptr, int numoidrdlocked, 
    void *oidrdlocked, int *oidrdversion, int *oidrdage, int ObjSeqId, objheader_t *header, int *isObjTypeTraverse) {
  int i;
  int hardabort=0;
  int isFirstTime=0;
  objheader_t *ObjToBeLocked=header;
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
        header->abortCount++;
        if(ObjSeqId > headeraddr->accessCount) {
          ObjSeqId = headeraddr->accessCount;
          ObjToBeLocked = header;
        }
        getTransSize(header, isObjTypeTraverse);
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
      objheader_t *header = ((void **) oidrdlocked)[i];
      int OidAge = oidrdage[i];
      unsigned int version = oidrdversion[i];
      if(version != header->version) { /* versions do not match */
#ifdef STMSTATS
        header->abortCount++;
        if(ObjSeqId > OidAge) {
          ObjSeqId = OidAge;
          ObjToBeLocked = header;
        }
        getTransSize(header, isObjTypeTraverse);
#endif
        hardabort=1;
      }
    }
  }

  /* Acquire lock on the oldest object accessed in the transaction cache */
  if(ObjToBeLocked != NULL) {
    ABORTCOUNT(ObjToBeLocked);
  }

  return hardabort;
}

/** ========================================================================================
 * getTotalAbortCount2 (for alttraverseCache only)
 * params : startptr: pointer that points to where to start looking in the cache hash table
 *        : numoidrdlocked : number of objects read that are locked
 *        : oidrdlocked : array of objects read and currently locked
 *        : oidrdversion : array of versions of object read
 *        : oidrdage : array of ages of objects read ina transaction cache
 *        : ObjSeqId : sequence Id/age to start the comparision with
 * =========================================================================================
 **/
int getTotalAbortCount2(void *startptr, int numoidrdlocked, void *oidrdlocked, 
    int *oidrdversion, int *oidrdage, int ObjSeqId, objheader_t *header, int *isObjTypeTraverse) {
  int hardabort=0;
  chashlistnode_t *curr = (chashlistnode_t *) startptr;
  objheader_t *ObjToBeLocked=header;

  /* Inner loop to traverse the linked list of the cache lookupTable */
  while(curr != NULL) {
    objheader_t *headeraddr=&((objheader_t *) curr->val)[-1];
    objheader_t *header=(objheader_t *)(((char *)curr->key)-sizeof(objheader_t));
    unsigned int version = headeraddr->version;
    /* versions do not match */
    if(version != header->version) {
#ifdef STMSTATS
      header->abortCount++;
      if(ObjSeqId > headeraddr->accessCount) {
        ObjSeqId = headeraddr->accessCount;
        ObjToBeLocked = header;
      }
      getTransSize(header, isObjTypeTraverse);
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
      int OidAge = oidrdage[i];
      if(version != header->version) { /* versions do not match */
#ifdef STMSTATS
        header->abortCount++;
        if(ObjSeqId > OidAge) {
          ObjSeqId = OidAge;
          ObjToBeLocked = header;
        }
        getTransSize(header, isObjTypeTraverse);
#endif
        hardabort=1;
      }
    }
  }

  if(ObjToBeLocked!=NULL) {
    ABORTCOUNT(ObjToBeLocked);
  }

  return hardabort;
}

/**
 * getReadAbortCount : Tells the number of aborts caused by objects that are read by
 *                    visiting the read array
 * params: int start, int stop are indexes to readLocked array
 *         void  *oidrdlocked = readLocked array
 *         int *oidrdversion = version array
 *        : oidrdage : array of ages of objects read ina transaction cache
 *        : ObjSeqId : sequence Id/age to start the comparision with
 **/
int getReadAbortCount(int start, int stop, void *oidrdlocked, int *oidrdversion, 
    int *oidrdage, int ObjSeqId, objheader_t *header, int *isObjTypeTraverse) {
  int i;
  int hardabort=0;
  objheader_t *ObjToBeLocked=header;

  /* Go through oids read that are locked */
  for(i = start; i < stop; i++) {
    objheader_t *header = ((void **)oidrdlocked)[i];
    unsigned int version = oidrdversion[i];
    int OidAge = oidrdage[i];
    if(version != header->version) { /* versions do not match */
#ifdef STMSTATS
      header->abortCount++;
      if(ObjSeqId > OidAge) {
        ObjSeqId = OidAge;
        ObjToBeLocked = header;
      }
      getTransSize(header, isObjTypeTraverse);
#endif
      hardabort=1;
    }
  }

  if(ObjToBeLocked != NULL) {
    ABORTCOUNT(ObjToBeLocked);
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
    CFENCE;
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

/**
 * Inline fuction to get Transaction size per object type for those
 * objects that cause 
 *
 **/
/*
INLINE void getTransSize(objheader_t *header , int *isObjTypeTraverse) {
  (typesCausingAbort[TYPE(header)]).numabort++;
  if(isObjTypeTraverse[TYPE(header)] != 1)
    (typesCausingAbort[TYPE(header)]).numaccess+=c_numelements;
  isObjTypeTraverse[TYPE(header)]=1;
}
*/

#endif
