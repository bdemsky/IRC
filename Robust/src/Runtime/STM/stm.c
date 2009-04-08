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
/* Thread transaction variables */
__thread objstr_t *t_cache;

#ifdef TRANSSTATS
int numTransCommit = 0;
int numTransAbort = 0;
int nSoftAbort = 0;
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
objheader_t *transCreateObj(void * ptr, unsigned int size) {
  objheader_t *tmp = mygcmalloc(ptr, (sizeof(objheader_t) + size));
  objheader_t *retval=&tmp[1];
  initdsmlocks(&tmp->lock);
  tmp->version = 1;
  STATUS(tmp)=NEW;
  t_chashInsert((unsigned int) retval, retval);
  return retval; //want space after object header
}

/* This functions inserts randowm wait delays in the order of msec
 * Mostly used when transaction commits retry*/
void randomdelay() {
  struct timespec req;
  time_t t;

  t = time(NULL);
  req.tv_sec = 0;
  req.tv_nsec = (long)(t%100); //1-11 microsec
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
__attribute__((pure)) void *transRead(void * oid) {
  unsigned int machinenumber;
  objheader_t *tmp, *objheader;
  objheader_t *objcopy;
  int size;

  /* Read from the main heap */
  objheader_t *header = (objheader_t *)(((char *)oid) - sizeof(objheader_t)); 
  if(read_trylock(&header->lock)) { //Can further acquire read locks
    GETSIZE(size, header);
    size += sizeof(objheader_t);
    objcopy = (objheader_t *) objstrAlloc(&t_cache, size);
    memcpy(objcopy, header, size);
    /* Insert into cache's lookup table */
    STATUS(objcopy)=0;
    t_chashInsert((unsigned int)oid, &objcopy[1]);
    read_unlock(&header->lock);
    return &objcopy[1];
  }
}

/* ================================================================
 * transCommit
 * - This function initiates the transaction commit process
 * - goes through the transaction cache and decides
 * - a final response 
 * ================================================================
 */
int transCommit() {
  do {
    /* Look through all the objects in the transaction hash table */
    int finalResponse = traverseCache();
    if(finalResponse == TRANS_ABORT) {
#ifdef TRANSSTATS
      numTransAbort++;
#endif
      objstrDelete(t_cache);
      t_chashDelete();
      return TRANS_ABORT;
    }
    if(finalResponse == TRANS_COMMIT) {
#ifdef TRANSSTATS
      numTransCommit++;
#endif
      objstrDelete(t_cache);
      t_chashDelete();
      return 0;
    }
    /* wait a random amount of time before retrying to commit transaction*/
    if(finalResponse == TRANS_SOFT_ABORT) {
#ifdef TRANSSTATS
      nSoftAbort++;
#endif
      randomdelay();
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
  unsigned int oidrdlocked[c_numelements];
  unsigned int oidwrlocked[c_numelements];
  int softabort=0;
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
      objheader_t * headeraddr=&((objheader_t *) curr->val)[-1];
      
      unsigned int version = headeraddr->version;
      objheader_t *header=(objheader_t *) (((char *)curr->key)-sizeof(objheader_t));
      
      if(STATUS(headeraddr) & NEW) {
	STATUS(headeraddr)=0;
      } else if(STATUS(headeraddr) & DIRTY) {
	/* Read from the main heap  and compare versions */
	if(write_trylock(&header->lock)) { //can aquire write lock
	  if (version == header->version) {/* versions match */
	    /* Keep track of objects locked */
	    oidwrlocked[numoidwrlocked++] = OID(header);
	  } else { 
	    oidwrlocked[numoidwrlocked++] = OID(header);
	    transAbortProcess(oidrdlocked, &numoidrdlocked, oidwrlocked, &numoidwrlocked);
	    return TRANS_ABORT;
	  }
	} else { /* cannot aquire lock */
	  if(version == header->version) /* versions match */
	    softabort=1;
	  else {
	    transAbortProcess(oidrdlocked, &numoidrdlocked, oidwrlocked, &numoidwrlocked);
	    return TRANS_ABORT;
	  }
	}
      } else {
	/* Read from the main heap  and compare versions */
	if(read_trylock(&header->lock)) { //can further aquire read locks
	  if(version == header->version) {/* versions match */
	    oidrdlocked[numoidrdlocked++] = OID(header);
	  } else {
	    oidrdlocked[numoidrdlocked++] = OID(header);
	    transAbortProcess(oidrdlocked, &numoidrdlocked, oidwrlocked, &numoidwrlocked);
	    return TRANS_ABORT;
	  }
	} else { /* cannot aquire lock */
	  if(version == header->version)
	    softabort=1;
	  else {
	    transAbortProcess(oidrdlocked, &numoidrdlocked, oidwrlocked, &numoidwrlocked);
	    return TRANS_ABORT;
	  }
	}
      }
    
      curr = curr->next;
    }
  } //end of for
  
  /* Decide the final response */
  if (softabort) {
    return TRANS_SOFT_ABORT;
  } else {
    transCommitProcess(oidrdlocked, &numoidrdlocked, oidwrlocked, &numoidwrlocked);
    return TRANS_COMMIT;
  }
}

/* ===========================================================================
 * decideResponse
 * - increments counters that keep track of objects read, modified or locked
 * - updates the oids locked and oids newly created 
 * ===========================================================================
 */


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
    header = (objheader_t *)(((char *)(oidrdlocked[i])) - sizeof(objheader_t));
    read_unlock(&header->lock);
  }

  /* Release write locks */
  for(i=0; i< *numoidwrlocked; i++) {
    /* Read from the main heap */
    header = (objheader_t *)(((char *)(oidwrlocked[i])) - sizeof(objheader_t));
    write_unlock(&header->lock);
  }
}

/* ==================================
 * transCommitProcess
 *
 * =================================
 */
int transCommitProcess(unsigned int *oidrdlocked, int *numoidrdlocked,
                    unsigned int *oidwrlocked, int *numoidwrlocked) {
  objheader_t *header;
  void *ptrcreate;
  int i;

  /* Copy from transaction cache -> main object store */
  for (i = 0; i < *numoidwrlocked; i++) {
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
  
  /* Release read locks */
  for(i=0; i< *numoidrdlocked; i++) {
    /* Read from the main heap */
    header = (objheader_t *)(((char *)(oidrdlocked[i])) - sizeof(objheader_t)); 
    read_unlock(&header->lock);
  }

  /* Release write locks */
  for(i=0; i< *numoidwrlocked; i++) {
    header = (objheader_t *)(((char *)(oidwrlocked[i])) - sizeof(objheader_t)); 
    write_unlock(&header->lock);
  }

  return 0;
}

