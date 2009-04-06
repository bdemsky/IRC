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
/* Thread transaction variables */
__thread objstr_t *t_cache;


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
  t_cache = objstrCreate(1048576);
  t_chashCreate(CHASH_SIZE, CLOADFACTOR);
}

/* =======================================================
 * transCreateObj
 * This function creates objects in the transaction record 
 * =======================================================
 */
objheader_t *transCreateObj(unsigned int size) {
  objheader_t *tmp = (objheader_t *) objstrAlloc(&t_cache, (sizeof(objheader_t) + size));
  OID(tmp) = getNewOID();
  tmp->version = 1;
  STATUS(tmp) = NEW;
  t_chashInsert(OID(tmp), tmp);

#ifdef COMPILER
  return &tmp[1]; //want space after object header
#else
  return tmp;
#endif
}

/* This functions inserts randowm wait delays in the order of msec
 * Mostly used when transaction commits retry*/
void randomdelay() {
  struct timespec req;
  time_t t;

  t = time(NULL);
  req.tv_sec = 0;
  req.tv_nsec = (long)(1000 + (t%10000)); //1-11 microsec
  nanosleep(&req, NULL);
  return;
}

/* ==============================================
 * objstrAlloc
 * - allocate space in an object store
 * ==============================================
 */
void *objstrAlloc(objstr_t **osptr, unsigned int size) {
  void *tmp;
  int i=0;
  objstr_t *store=*osptr;
  if ((size&7)!=0) {
    size+=(8-(size&7));
  }

  for(;i<3;i++) {
    if (OSFREE(store)>=size) {
      tmp=store->top;
      store->top +=size;
      return tmp;
    }
    if ((store=store->next)==NULL)
      break;
  }

  {
    unsigned int newsize=size>DEFAULT_OBJ_STORE_SIZE?size:DEFAULT_OBJ_STORE_SIZE;
    objstr_t *os=(objstr_t *)calloc(1,(sizeof(objstr_t) + newsize));
    void *ptr=&os[1];
    os->next=store;
    (*osptr)=os;
    os->size=newsize;
    os->top=((char *)ptr)+size;
    return ptr;
  }
}

/* =============================================================
 * transRead
 * -finds the objects either in main heap
 * -copies the object into the transaction cache
 * =============================================================
 */
__attribute__((pure)) objheader_t *transRead(unsigned int oid) {
  unsigned int machinenumber;
  objheader_t *tmp, *objheader;
  objheader_t *objcopy;
  int size;

  /* Read from the main heap */
  objheader_t *header = (objheader_t *)(((char *)(&oid)) - sizeof(objheader_t)); 
  if(read_trylock(STATUSPTR(header))) { //Can further acquire read locks
    GETSIZE(size, header);
    size += sizeof(objheader_t);
    objcopy = (objheader_t *) objstrAlloc(&t_cache, size);
    memcpy(objcopy, header, size);
    /* Insert into cache's lookup table */
    STATUS(objcopy)=0;
    t_chashInsert(OID(header), objcopy);
#ifdef COMPILER
    return &objcopy[1];
#else
    return objcopy;
#endif
  }
  read_unlock(STATUSPTR(header));
}

/* ================================================================
 * transCommit
 * - This function initiates the transaction commit process
 * - goes through the transaction cache and decides
 * - a final response 
 * ================================================================
 */
int transCommit() {
  char finalResponse;
  char treplyretry; /* keeps track of the common response that needs to be sent */

  do {
    treplyretry = 0;
    /* Look through all the objects in the transaction hash table */
    finalResponse = traverseCache(&treplyretry);
    if(finalResponse == TRANS_ABORT) {
      break;
    }
    if(finalResponse == TRANS_COMMIT) {
      break;
    }
    /* wait a random amount of time before retrying to commit transaction*/
    if(treplyretry && (finalResponse == TRANS_SOFT_ABORT)) {
      randomdelay();
    }
    if(finalResponse != TRANS_ABORT || finalResponse != TRANS_COMMIT || finalResponse != TRANS_SOFT_ABORT) {
      printf("Error: in %s() Unknown outcome", __func__);
      exit(-1);
    }
    /* Retry trans commit procedure during soft_abort case */
  } while (treplyretry);

  if(finalResponse == TRANS_ABORT) {
    /* Free Resources */
    objstrDelete(t_cache);
    t_chashDelete();
    return TRANS_ABORT;
  } else if(finalResponse == TRANS_COMMIT) {
    /* Free Resources */
    objstrDelete(t_cache);
    t_chashDelete();
    return 0;
  } else {
    //TODO Add other cases
    printf("Error: in %s() THIS SHOULD NOT HAPPEN.....EXIT PROGRAM\n", __func__);
    exit(-1);
  }
  return 0;
}

/* ==================================================
 * traverseCache
 * - goes through the transaction cache and
 * - decides if a transaction should commit or abort
 * ==================================================
 */
char traverseCache(char *treplyretry) {
  /* Create info for newly creately objects */
  int numcreated=0;
  unsigned int oidcreated[c_numelements];
  /* Create info to keep track of objects that can be locked */
  int numoidrdlocked=0;
  int numoidwrlocked=0;
  unsigned int oidrdlocked[c_numelements];
  unsigned int oidwrlocked[c_numelements];
  /* Counters to decide final response of this transaction */
  int vmatch_lock;
  int vmatch_nolock;
  int vnomatch;
  int numoidread;
  int numoidmod;
  char response;

  int i;
  chashlistnode_t *ptr = c_table;
  /* Represents number of bins in the chash table */
  unsigned int size = c_size;
  for(i = 0; i<size; i++) {
    chashlistnode_t *curr = &ptr[i];
    /* Inner loop to traverse the linked list of the cache lookupTable */
    while(curr != NULL) {
      //if the first bin in hash table is empty
      if(curr->key == 0)
        break;
      objheader_t * headeraddr=(objheader_t *) curr->val;
      response = decideResponse(headeraddr, oidcreated, &numcreated, oidrdlocked, &numoidrdlocked, oidwrlocked, &numoidwrlocked,
                                &vmatch_lock, &vmatch_nolock, &vnomatch, &numoidmod, &numoidread);
      if(response == TRANS_ABORT) {
        *treplyretry = 0;
        transAbortProcess(oidrdlocked, &numoidrdlocked, oidwrlocked, &numoidwrlocked);
        return TRANS_ABORT;
      }
      curr = curr->next;
    }
  } //end of for
  
  /* Decide the final response */
  if(vmatch_nolock == (numoidread + numoidmod)) {
    *treplyretry = 0;
    transCommitProcess(oidcreated, &numcreated, oidrdlocked, &numoidrdlocked, oidwrlocked, &numoidwrlocked);
    response = TRANS_COMMIT;
  }
  if(vmatch_lock > 0 && vnomatch == 0) {
    *treplyretry = 1;
    response = TRANS_SOFT_ABORT;
  }
  return response;
}

/* ===========================================================================
 * decideResponse
 * - increments counters that keep track of objects read, modified or locked
 * - updates the oids locked and oids newly created 
 * ===========================================================================
 */
char decideResponse(objheader_t *headeraddr, unsigned int *oidcreated, int *numcreated, unsigned int* oidrdlocked, int *numoidrdlocked,
    unsigned int*oidwrlocked, int *numoidwrlocked, int *vmatch_lock, int *vmatch_nolock, int *vnomatch, int *numoidmod, int *numoidread) {
  unsigned short version = headeraddr->version;
  unsigned int oid = OID(headeraddr);
  if(STATUS(headeraddr) & NEW) {
    oidcreated[(*numcreated)++] = OID(headeraddr);
  } else if(STATUS(headeraddr) & DIRTY) {
    (*numoidmod)++;
    /* Read from the main heap  and compare versions */
    objheader_t *header = (objheader_t *)(((char *)(&oid)) - sizeof(objheader_t)); 
    if(write_trylock(STATUSPTR(header))) { //can aquire write lock
      if (version == header->version) {/* versions match */
        /* Keep track of objects locked */
        (*vmatch_nolock)++;
        oidwrlocked[(*numoidwrlocked)++] = OID(header);
      } else { 
        (*vnomatch)++;
        oidwrlocked[(*numoidwrlocked)++] = OID(header);
        return TRANS_ABORT;
      }
    } else { /* cannot aquire lock */
      if(version == header->version) /* versions match */
        (*vmatch_lock)++;
      else {
        (*vnomatch)++;
        return TRANS_ABORT;
      }
    }
  } else {
    (*numoidread)++;
    /* Read from the main heap  and compare versions */
    objheader_t *header = (objheader_t *)(((char *)(&oid)) - sizeof(objheader_t)); 
    if(read_trylock(STATUSPTR(header))) { //can further aquire read locks
      if(version == header->version) {/* versions match */
        (*vmatch_nolock)++;
        oidrdlocked[(*numoidrdlocked)++] = OID(header);
      } else {
        (*vnomatch)++;
        oidrdlocked[(*numoidrdlocked)++] = OID(header);
        return TRANS_ABORT;
      }
    } else { /* cannot aquire lock */
      if(version == header->version)
        (*vmatch_lock)++;
      else {
        (*vnomatch)++;
        return TRANS_ABORT;
      }
    }
  }
  return 0;
}

/* ==================================
 * transAbortProcess
 *
 * =================================
 */
int transAbortProcess(unsigned int *oidrdlocked, int *numoidrdlocked, unsigned int *oidwrlocked, int *numoidwrlocked) {
  int i;
  objheader_t *header;
  /* Release read locks */
  for(i=0; i< *numoidrdlocked; i++) {
    /* Read from the main heap */
    if((header = (objheader_t *)(((char *)(&oidrdlocked[i])) - sizeof(objheader_t))) == NULL) {
      printf("Error: %s() main heap returned NULL at %s, %d\n", __func__, __FILE__, __LINE__);
      return 1;
    }
    read_unlock(STATUSPTR(header));
  }

  /* Release write locks */
  for(i=0; i< *numoidwrlocked; i++) {
    /* Read from the main heap */
    if((header = (objheader_t *)(((char *)(&oidwrlocked[i])) - sizeof(objheader_t))) == NULL) {
      printf("Error: %s() main heap returned NULL at %s, %d\n", __func__, __FILE__, __LINE__);
      return 1;
    }
    write_unlock(STATUSPTR(header));
  }
}

/* ==================================
 * transCommitProcess
 *
 * =================================
 */
int transCommitProcess(unsigned int *oidcreated, int *numoidcreated, unsigned int *oidrdlocked, int *numoidrdlocked,
                    unsigned int *oidwrlocked, int *numoidwrlocked) {
  objheader_t *header, *tcptr;
  void *ptrcreate;

  int i;
  /* If object is newly created inside transaction then commit it */
  for (i = 0; i < *numoidcreated; i++) {
    if ((header = ((objheader_t *) t_chashSearch(oidcreated[i]))) == NULL) {
      printf("Error: %s() chashSearch returned NULL for oid = %x at %s, %d\n", __func__, oidcreated[i], __FILE__, __LINE__);
      return 1;
    }
    int tmpsize;
    GETSIZE(tmpsize, header);
    tmpsize += sizeof(objheader_t);
    /* FIXME Is this correct? */
#ifdef PRECISE_GC
    ptrcreate = mygcmalloc((struct garbagelist *)header, tmpsize);
#else
    ptrcreate = FREEMALLOC(tmpsize);
#endif
    /* Initialize read and write locks */
    initdsmlocks(STATUSPTR(header));
    memcpy(ptrcreate, header, tmpsize);
  }

  /* Copy from transaction cache -> main object store */
  for (i = 0; i < *numoidwrlocked; i++) {
    /* Read from the main heap */ 
    if((header = (objheader_t *)(((char *)(&oidwrlocked[i])) - sizeof(objheader_t))) == NULL) {
      printf("Error: %s() main heap returns NULL at %s, %d\n", __func__, __FILE__, __LINE__);
      return 1;
    }
    if ((tcptr = ((objheader_t *) t_chashSearch(oidwrlocked[i]))) == NULL) {
      printf("Error: %s() chashSearch returned NULL at %s, %d\n", __func__, __FILE__, __LINE__);
      return 1;
    }
    int tmpsize;
    GETSIZE(tmpsize, header);
    char *tmptcptr = (char *) tcptr;
    {
      struct ___Object___ *dst=(struct ___Object___*)((char*)header+sizeof(objheader_t));
      struct ___Object___ *src=(struct ___Object___*)((char*)tmptcptr+sizeof(objheader_t));
      dst->___cachedCode___=src->___cachedCode___;
      dst->___cachedHash___=src->___cachedHash___;

      memcpy(&dst[1], &src[1], tmpsize-sizeof(struct ___Object___));
    }

    header->version += 1;
  }
  
  /* Release read locks */
  for(i=0; i< *numoidrdlocked; i++) {
    /* Read from the main heap */
    header = (objheader_t *)(((char *)(&oidrdlocked[i])) - sizeof(objheader_t)); 
    read_unlock(STATUSPTR(header));
  }

  /* Release write locks */
  for(i=0; i< *numoidwrlocked; i++) {
    header = (objheader_t *)(((char *)(&oidwrlocked[i])) - sizeof(objheader_t)); 
    write_unlock(STATUSPTR(header));
  }

  return 0;
}

