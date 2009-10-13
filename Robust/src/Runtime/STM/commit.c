#include<tm.h>
#ifdef DELAYCOMP
#include<delaycomp.h>
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
#ifdef SANDBOX
  abortenabled=0;
#endif
#ifdef TRANSSTATS
  numTransCommit++;
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
#ifdef SANDBOX
      abortenabled=1;
#endif
      return TRANS_ABORT;
    }
    if(finalResponse == TRANS_COMMIT) {
#ifdef TRANSSTATS
      //numTransCommit++;
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

#ifdef STMSTATS
#define STATFREE free(oidrdage);
#define STATALLOC oidrdage=malloc(size);
#define STATASSIGN oidrdage=rdage;
#else
  #define STATFREE
  #define STATALLOC
  #define STATASSIGN
#endif

#ifdef DELAYCOMP
#define freearrays if (c_numelements>=200) { \
    STATFREE				     \
    free(oidrdlocked);		     \
    free(oidrdversion);			     \
  } \
  if (t_numelements>=200) { \
    free(oidwrlocked); \
  }
#else
#define freearrays   if (c_numelements>=200) { \
    STATFREE \
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
    STATASSIGN		    \
  } else { \
    int size=c_numelements*sizeof(void*); \
    oidrdlocked=malloc(size); \
    oidrdversion=malloc(size); \
    STATALLOC		       \
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
    STATALLOC		      \
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
#ifdef STMSTATS
  int rdage[200];
  int * oidrdage;
  int ObjSeqId;
  int objtypetraverse[TOTALNUMCLASSANDARRAY];
#endif
  int * oidrdversion;
  allocarrays;

#ifdef STMSTATS
  for(i=0; i<TOTALNUMCLASSANDARRAY; i++)
    objtypetraverse[i] = 0;
#endif

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
      objheader_t * headeraddr=&((objheader_t *) curr->val)[-1]; //cached object
      objheader_t *header=(objheader_t *)(((char *)curr->key)-sizeof(objheader_t)); //real object
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
        header->abortCount++;
        ObjSeqId = headeraddr->accessCount;
	    (typesCausingAbort[TYPE(header)]).numabort++;
	    (typesCausingAbort[TYPE(header)]).numaccess+=c_numelements;
        (typesCausingAbort[TYPE(header)]).numtrans+=1; 
        objtypetraverse[TYPE(header)]=1;
        getTotalAbortCount(i+1, size, (void *)(curr->next), numoidrdlocked, oidrdlocked, oidrdversion, oidrdage, ObjSeqId, header, objtypetraverse);
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
      header->abortCount++;
      ObjSeqId = headeraddr->accessCount;
      (typesCausingAbort[TYPE(header)]).numabort++;
      (typesCausingAbort[TYPE(header)]).numaccess+=c_numelements;
      (typesCausingAbort[TYPE(header)]).numtrans+=1; 
      objtypetraverse[TYPE(header)]=1;
	  //(typesCausingAbort[TYPE(header)])++;
#endif
#if defined(STMSTATS)||defined(SOFTABORT)
      if(getTotalAbortCount(i+1, size, (void *)(curr->next), numoidrdlocked, oidrdlocked, oidrdversion, oidrdage, ObjSeqId, header, objtypetraverse))
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
#ifdef STMSTATS
    oidrdage[numoidrdlocked]=headeraddr->accessCount;
#endif
    oidrdversion[numoidrdlocked]=version;
    oidrdlocked[numoidrdlocked++]=header;
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
      ObjSeqId = headeraddr->accessCount;
      header->abortCount++;
      (typesCausingAbort[TYPE(header)]).numabort++;
      (typesCausingAbort[TYPE(header)]).numaccess+=c_numelements;
      (typesCausingAbort[TYPE(header)]).numtrans+=1; 
      objtypetraverse[TYPE(header)]=1;
#endif
#if defined(STMSTATS)||defined(SOFTABORT)
      if(getTotalAbortCount(i+1, size, (void *)(curr->next), numoidrdlocked, oidrdlocked, oidrdversion, oidrdage, ObjSeqId, header, objtypetraverse))
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
      CFENCE;
      if(version != header->version) { /* versions do not match */
#ifdef DELAYCOMP
	transAbortProcess(oidwrlocked, numoidwrtotal);
#else
	transAbortProcess(oidwrlocked, numoidwrlocked);
#endif
#ifdef STMSTATS
    ObjSeqId = oidrdage[i];
    header->abortCount++;
    (typesCausingAbort[TYPE(header)]).numabort++;
    (typesCausingAbort[TYPE(header)]).numaccess+=c_numelements;
    (typesCausingAbort[TYPE(header)]).numtrans+=1; 
    objtypetraverse[TYPE(header)]=1;
	getReadAbortCount(i+1, numoidrdlocked, oidrdlocked, oidrdversion, oidrdage, ObjSeqId, header, objtypetraverse);
#endif
	DEBUGSTM("RD Abort: rd: %u wr: %u tot: %u type: %u ver: %u oid: %x\n", numoidrdlocked, numoidwrlocked, c_numelements, TYPE(header), header->version, OID(header));
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
    ObjSeqId = oidrdage[i];
    header->abortCount++;
    (typesCausingAbort[TYPE(header)]).numabort++;
    (typesCausingAbort[TYPE(header)]).numaccess+=c_numelements;
    (typesCausingAbort[TYPE(header)]).numtrans+=1; 
    objtypetraverse[TYPE(header)]=1;
	getReadAbortCount(i+1, numoidrdlocked, oidrdlocked, oidrdversion, oidrdage, ObjSeqId, header, objtypetraverse);
#endif
	DEBUGSTM("RD Abort: rd: %u wr: %u tot: %u type: %u ver: %u, oid: %x\n", numoidrdlocked, numoidwrlocked, c_numelements, TYPE(header), header->version, OID(header));
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
      ObjSeqId = oidrdage[i];
      header->abortCount++;
      (typesCausingAbort[TYPE(header)]).numabort++;
      (typesCausingAbort[TYPE(header)]).numaccess+=c_numelements;
      (typesCausingAbort[TYPE(header)]).numtrans+=1; 
      objtypetraverse[TYPE(header)]=1;
#endif
#if defined(STMSTATS)||defined(SOFTABORT)
      if(getReadAbortCount(i+1, numoidrdlocked, oidrdlocked, oidrdversion, oidrdage, ObjSeqId, header, objtypetraverse))
	softabort=0;
#endif
      DEBUGSTM("RD Abort: rd: %u wr: %u tot: %u type: %u ver: %u oid: %x\n", numoidrdlocked, numoidwrlocked, c_numelements, TYPE(header), header->version, OID(header));
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
	//ABORTCOUNT(header);
	(typesCausingAbort[TYPE(header)])++;
#endif
#if defined(STMSTATS)||defined(SOFTABORT)
	//if(getTotalAbortCount2((void *) curr->next, numoidrdlocked, oidrdlocked, oidrdversion))
	//  softabort=0;
#endif
	DEBUGSTM("WR Abort: rd: %u wr: %u tot: %u type: %u ver: %u oid: %x\n", numoidrdlocked, numoidwrlocked, c_numelements, TYPE(header), header->version, OID(header));
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
      //ABORTCOUNT(header);
      (typesCausingAbort[TYPE(header)])++;
#endif
#if defined(STMSTATS)||defined(SOFTABORT)
      //if(getTotalAbortCount2((void *) curr->next, numoidrdlocked, oidrdlocked, oidrdversion))
	//softabort=0;
#endif
      DEBUGSTM("WR Abort: rd: %u wr: %u tot: %u type: %u ver: %u oid: %x\n", numoidrdlocked, numoidwrlocked, c_numelements, TYPE(header), header->version, OID(header));
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
#ifdef STMSTATS
  int rdage[200];
  int * oidrdage;
  int ObjSeqId;
  int objtypetraverse[TOTALNUMCLASSANDARRAY];
#endif
  void ** oidwrlocked;
  allocarrays;

#ifdef STMSTATS
  for(i=0; i<TOTALNUMCLASSANDARRAY; i++)
    objtypetraverse[i] = 0;
#endif
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
      header->abortCount++;
      ObjSeqId = headeraddr->accessCount; 
      (typesCausingAbort[TYPE(header)]).numabort++;
      (typesCausingAbort[TYPE(header)]).numaccess+=c_numelements;
      (typesCausingAbort[TYPE(header)]).numtrans+=1; 
      objtypetraverse[TYPE(header)]=1;
      getTotalAbortCount2((void *) curr->next, numoidrdlocked, oidrdlocked, oidrdversion, oidrdage, ObjSeqId, header, objtypetraverse);
#endif
	  DEBUGSTM("WR Abort: rd: %u wr: %u tot: %u type: %u ver: %u oid: %x\n", numoidrdlocked, numoidwrlocked, c_numelements, TYPE(header), header->version, OID(header));
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
    header->abortCount++;
    ObjSeqId = headeraddr->accessCount; 
    (typesCausingAbort[TYPE(header)]).numabort++;
    (typesCausingAbort[TYPE(header)]).numaccess+=c_numelements;
    (typesCausingAbort[TYPE(header)]).numtrans+=1; 
    objtypetraverse[TYPE(header)]=1;
#endif
#if defined(STMSTATS)||defined(SOFTABORT)
    if(getTotalAbortCount2((void *) curr->next, numoidrdlocked, oidrdlocked, oidrdversion, oidrdage, ObjSeqId, header, objtypetraverse))
	  softabort=0;
#endif
	DEBUGSTM("WR Abort: rd: %u wr: %u tot: %u type: %u ver: %u oid: %x\n", numoidrdlocked, numoidwrlocked, c_numelements, TYPE(header), header->version, OID(header));
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
      header->abortCount++;
      ObjSeqId = headeraddr->accessCount; 
      (typesCausingAbort[TYPE(header)]).numabort++;
      (typesCausingAbort[TYPE(header)]).numaccess+=c_numelements;
      (typesCausingAbort[TYPE(header)]).numtrans+=1; 
      objtypetraverse[TYPE(header)]=1;
#endif
#if defined(STMSTATS)||defined(SOFTABORT)
      if(getTotalAbortCount2((void *) curr->next, numoidrdlocked, oidrdlocked, oidrdversion, oidrdage, ObjSeqId, header, objtypetraverse))
	softabort=0;
#endif
      DEBUGSTM("WR Abort: rd: %u wr: %u tot: %u type: %u ver: %u oid: %x\n", numoidrdlocked, numoidwrlocked, c_numelements, TYPE(header), header->version, OID(header));
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
    if(header->lock>0) {
      CFENCE;
      if(version != header->version) {
#ifdef DELAYCOMP
	transAbortProcess(oidwrlocked, numoidwrtotal);
#else
	transAbortProcess(oidwrlocked, numoidwrlocked);
#endif
#ifdef STMSTATS
    ObjSeqId = oidrdage[i];
    header->abortCount++;
    (typesCausingAbort[TYPE(header)]).numabort++;
    (typesCausingAbort[TYPE(header)]).numaccess+=c_numelements;
    (typesCausingAbort[TYPE(header)]).numtrans+=1; 
    objtypetraverse[TYPE(header)]=1;
	getReadAbortCount(i+1, numoidrdlocked, oidrdlocked, oidrdversion, oidrdage, ObjSeqId, header, objtypetraverse);
#endif
	DEBUGSTM("RD Abort: rd: %u wr: %u tot: %u type: %u ver: %u oid: %x\n", numoidrdlocked, numoidwrlocked, c_numelements, TYPE(header), header->version, OID(header));
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
    ObjSeqId = oidrdage[i];
    header->abortCount++;
	getReadAbortCount(i+1, numoidrdlocked, oidrdlocked, oidrdversion, oidrdage, ObjSeqId, header, objtypetraverse);
#endif
	DEBUGSTM("RD Abort: rd: %u wr: %u tot: %u type: %u ver: %u oid: %x\n", numoidrdlocked, numoidwrlocked, c_numelements, TYPE(header), header->version, OID(header));
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
      ObjSeqId = oidrdage[i];
      header->abortCount++;
      (typesCausingAbort[TYPE(header)]).numabort++;
      (typesCausingAbort[TYPE(header)]).numaccess+=c_numelements;
      (typesCausingAbort[TYPE(header)]).numtrans+=1; 
      objtypetraverse[TYPE(header)]=1;
#endif
#if defined(STMSTATS)||defined(SOFTABORT)
      if(getReadAbortCount(i+1, numoidrdlocked, oidrdlocked, oidrdversion, oidrdage, ObjSeqId, header, objtypetraverse))
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
	//ABORTCOUNT(header);
	(typesCausingAbort[TYPE(header)])++;
#endif
#if defined(STMSTATS)||defined(SOFTABORT)
	//if(getTotalAbortCount2((void *) curr->next, numoidrdlocked, oidrdlocked, oidrdversion))
	//  softabort=0;
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
      //ABORTCOUNT(header);
      (typesCausingAbort[TYPE(header)])++;
#endif
#if defined(STMSTATS)||defined(SOFTABORT)
     // if(getTotalAbortCount2((void *) curr->next, numoidrdlocked, oidrdlocked, oidrdversion))
	//softabort=0;
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
  }
  CFENCE;

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
    header->version++;
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

