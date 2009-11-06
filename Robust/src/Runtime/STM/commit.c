#include<tm.h>
#ifdef DELAYCOMP
#include<delaycomp.h>
#endif

#ifdef TRANSSTATS
#define TRANSWRAP(x) x
#else
#define TRANSWRAP(x)
#endif

#ifdef STMSTATS
#define STMWRAP(x) x
#else
#define STMWRAP(x)
#endif

#ifdef STMSTATS
#define STATFREE free(oidrdage);
#define STATALLOC oidrdage=malloc(size);
#define STATASSIGN oidrdage=rdage;
#else
#define STATFREE
#define STATALLOC
#define STATASSIGN
#endif

#if defined(STMARRAY)&&defined(DELAYCOMP)
#define ARRAYDELAYWRAP(x) x
#define ARRAYDELAYWRAP1(x) ,x
#else
#define ARRAYDELAYWRAP(x)
#define ARRAYDELAYWRAP1(x)
#endif

#ifdef DUALVIEW
#define DUALVIEWWRAP(x) x
#else
#define DUALVIEWWRAP(x)
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
  int softaborted=0;
  do {
    /* Look through all the objects in the transaction hash table */
    int finalResponse;
#ifdef DELAYCOMP
    if (c_numelements<(c_size>>3))
      finalResponse=alttraverseCache(commitmethod, primitives, locals, params);
    else
      finalResponse=traverseCache(commitmethod, primitives, locals, params);
#else
    if (c_numelements<(c_size>>3))
      finalResponse=alttraverseCache();
    else
      finalResponse=traverseCache();
#endif
    if(finalResponse == TRANS_ABORT) {
      TRANSWRAP(numTransAbort++;if (softaborted) nSoftAbortAbort++;);
      freenewobjs();
      STMWRAP(freelockedobjs(););
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
#if defined(STMARRAY)&&!defined(DUALVIEW)
      arraystack.count=0;
#endif
#endif
#ifdef SANDBOX
      abortenabled=1;
#endif

      return TRANS_ABORT;
    }
    if(finalResponse == TRANS_COMMIT) {
      TRANSWRAP(numTransCommit++;if (softaborted) nSoftAbortCommit++;);
      freenewobjs();
      STMWRAP(freelockedobjs(););
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
#if defined(STMARRAY)&&!defined(DUALVIEW)
      arraystack.count=0;
#endif
#endif
      return 0;
    }

    /* wait a random amount of time before retrying to commit transaction*/
    if(finalResponse == TRANS_SOFT_ABORT) {
      TRANSWRAP(nSoftAbort++;);
      softaborted++;
#ifdef SOFTABORT
      if (softaborted>1) {
#else
      if (1) {
#endif
	//retry if too many soft aborts
	freenewobjs();
	STMWRAP(freelockedobjs(););
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
#if defined(STMARRAY)&&!defined(DUALVIEW)
      arraystack.count=0;
#endif
#endif
	return TRANS_ABORT;
      }
    } else {
      printf("Error: in %s() Unknown outcome", __func__);
      exit(-1);
    }
  } while (1);
}

#ifdef DELAYCOMP
#define freearrays if (c_numelements>=200) { \
    STATFREE;				     \
    STMARRAYFREE;			     \
    free(oidrdlocked);			     \
    free(oidrdversion);			     \
  }					     \
  if (t_numelements>=200) {		     \
    free(oidwrlocked);			     \
    STMARRAYDELAYFREE;			     \
  }
#else
#define freearrays   if (c_numelements>=200) { \
    STATFREE;				       \
    STMARRAYFREE;			       \
    free(oidrdlocked);			       \
    free(oidrdversion);			       \
    free(oidwrlocked);			       \
  }
#endif

#ifdef DELAYCOMP
#define allocarrays int t_numelements=c_numelements+dc_c_numelements;	\
  if (t_numelements<200) {						\
    oidwrlocked=(struct garbagelist *) &wrlocked;			\
    STMARRAYDELAYASSIGN;						\
  } else {								\
    oidwrlocked=malloc(2*sizeof(INTPTR)+t_numelements*(sizeof(void *))); \
    STMARRAYDELAYALLOC;							\
  }									\
  if (c_numelements<200) {						\
    oidrdlocked=rdlocked;						\
    oidrdversion=rdversion;						\
    STATASSIGN;								\
    STMARRAYASSIGN;							\
  } else {								\
    int size=c_numelements*sizeof(void*);				\
    oidrdlocked=malloc(size);						\
    oidrdversion=malloc(size);						\
    STATALLOC;								\
    STMARRAYALLOC;							\
  }									\
  dirwrlocked=oidwrlocked->array;
#else
#define allocarrays if (c_numelements<200) {	  \
    oidrdlocked=rdlocked;			  \
    oidrdversion=rdversion;			  \
    oidwrlocked=(struct garbagelist *) &wrlocked; \
    STATASSIGN;					  \
    STMARRAYASSIGN;				  \
  } else {					  \
    int size=c_numelements*sizeof(void*);	  \
    oidrdlocked=malloc(size);			  \
    oidrdversion=malloc(size);			  \
    oidwrlocked=malloc(size+2*sizeof(INTPTR));	  \
    STATALLOC;					  \
    STMARRAYALLOC;				  \
  }						  \
  dirwrlocked=oidwrlocked->array;
#endif

#ifdef STMSTATS
#define ABORTSTAT1 header->abortCount++;				\
  ObjSeqId = headeraddr->accessCount;					\
  (typesCausingAbort[TYPE(header)]).numabort++;				\
  (typesCausingAbort[TYPE(header)]).numaccess+=c_numelements;		\
  (typesCausingAbort[TYPE(header)]).numtrans+=1;			\
  objtypetraverse[TYPE(header)]=1;					\
  if(getTotalAbortCount(i+1, size, (void *)(curr->next), numoidrdlocked, oidrdlocked, oidrdversion, oidrdage, ObjSeqId, header, objtypetraverse)) \
    softabort=0;
#define ABORTSTAT2							\
  ObjSeqId = oidrdage[i];						\
  header->abortCount++;							\
  (typesCausingAbort[TYPE(header)]).numabort++;				\
  (typesCausingAbort[TYPE(header)]).numaccess+=c_numelements;		\
  (typesCausingAbort[TYPE(header)]).numtrans+=1;			\
  objtypetraverse[TYPE(header)]=1;					\
  if (getReadAbortCount(i+1, numoidrdlocked, oidrdlocked, oidrdversion, oidrdage, ObjSeqId, header, objtypetraverse)) \
    softabort=0;
#define ABORTSTAT3							\
  header->abortCount++;							\
  ObjSeqId = headeraddr->accessCount;					\
  (typesCausingAbort[TYPE(header)]).numabort++;				\
  (typesCausingAbort[TYPE(header)]).numaccess+=c_numelements;		\
  (typesCausingAbort[TYPE(header)]).numtrans+=1;			\
  objtypetraverse[TYPE(header)]=1;					\
  if (getTotalAbortCount2((void *) curr->next, numoidrdlocked, oidrdlocked, oidrdversion, oidrdage, ObjSeqId, header, objtypetraverse)) \
    softabort=0;
#define ABORTSTAT4 ObjSeqId = oidrdage[i];				\
  header->abortCount++;							\
  getReadAbortCount(i+1, numoidrdlocked, oidrdlocked, oidrdversion, oidrdage, ObjSeqId, header, objtypetraverse);
#else
#define ABORTSTAT1
#define ABORTSTAT2
#define ABORTSTAT3
#define ABORTSTAT4
#endif

#ifdef DELAYCOMP
#define DELAYWRAP(x) x
#define NUMWRTOTAL numoidwrtotal
#else
#define DELAYWRAP(x)
#define NUMWRTOTAL numoidwrlocked
#endif

#if defined(STMARRAY)
#define STMARRAYFREE free(oidrdlockedarray);
#define STMARRAYALLOC oidrdlockedarray=malloc(size);
#define STMARRAYASSIGN oidrdlockedarray=rdlockedarray;

//allocation statements for dirwrindex
#define STMARRAYDELAYFREE free(dirwrindex);
#define STMARRAYDELAYALLOC dirwrindex=malloc(t_numelements*sizeof(int));
#define STMARRAYDELAYASSIGN dirwrindex=wrindex;

#define ARRAYDEFINES int numoidrdlockedarray=0;	\
  void * rdlockedarray[200];			\
  void ** oidrdlockedarray;

#define ABORT					\
  transAbortProcess(oidwrlocked, numoidwrlocked ARRAYDELAYWRAP1(NULL) ARRAYDELAYWRAP1(numoidwrlocked)); \
  freearrays;								\
  if (softabort)							\
    return TRANS_SOFT_ABORT;						\
  else									\
    return TRANS_ABORT;
  
#define ABORTREAD							\
  transAbortProcess(oidwrlocked, numoidwrtotal ARRAYDELAYWRAP1(dirwrindex) ARRAYDELAYWRAP1(numoidwrlocked)); \
  freearrays;								\
  if (softabort)							\
    return TRANS_SOFT_ABORT;						\
  else									\
    return TRANS_ABORT;


#define ARRAYABORT							\
  for(;j>=lowoffset;j--) {						\
    GETLOCKVAL(status, transao, j);					\
    if  (status==STMDIRTY) {						\
      GETLOCKPTR(lockptr, mainao,j);					\
      write_unlock(lockptr);						\
    }									\
  }									\
  ABORT

#ifdef DUALVIEW
/* Try to grab object lock...If we get it, check object access
   version and abort on mismatch */
  
#define DVGETLOCK if (!addwrobject) {					\
    unsigned int * objlock=&(&((objheader_t *)mainao)[-1])->lock;	\
    if(unlikely(!rwread_trylock(objlock))) {				\
      ABORT;								\
    }									\
}

#define DVRELEASELOCK(x) {						\
    unsigned int * objlock=&(&((objheader_t *)x)[-1])->lock;		\
    rwread_unlock(objlock);						\
  }

/*not finished...if we can't get the lock, it is okay if it is in our
    access set*/

#define DVCHECKLOCK(x)							\
  unsigned int * objlock=&(&((objheader_t *)x)[-1])->lock;		\
  if (objlock<=0) {							\
    if (!dc_t_chashSearch(x)) {						\
      ABORTREAD;							\
    }									\
  }

#define DVSETINDEX							\
  dirwrindex[numoidwrlocked]=-1;

#else
#define DVSETINDEX
#define DVGETLOCK
#define DVCHECKLOCK(x)
#define DVRELEASELOCK(x)
#endif


#if defined(DELAYCOMP)&&!defined(DUALVIEW)
#define READCHECK							\
  else if (dc_t_chashSearchArray(mainao,j)) {				\
    CFENCE;								\
    unsigned int localversion;						\
    unsigned int remoteversion;						\
    GETVERSIONVAL(localversion, transao, j);				\
    GETVERSIONVAL(remoteversion, mainao, j);				\
    if (localversion != remoteversion) {				\
      transAbortProcess(oidwrlocked, NUMWRTOTAL ARRAYDELAYWRAP1(dirwrindex) ARRAYDELAYWRAP1(numoidwrlocked)); \
      freearrays;							\
      return TRANS_ABORT;						\
    }									\
  }
#else
#define READCHECK
#endif

  /* This code checks arrays to try to lock them if dirty or check
     later if they were only read. */

#define PROCESSARRAY							\
  int type=((int *)cachedobj)[0];					\
  if (type>=NUMCLASSES) {						\
    struct ArrayObject *transao=(struct ArrayObject *) cachedobj;	\
    struct ArrayObject *mainao=(struct ArrayObject *) objptr;		\
    int lowoffset=(transao->lowindex);					\
    int highoffset=(transao->highindex);				\
    int j;								\
    int addwrobject=0, addrdobject=0;					\
    for(j=lowoffset; j<=highoffset;j++) {				\
      unsigned int status;						\
      GETLOCKVAL(status, transao, j);					\
      if (status==STMDIRTY) {						\
	DVGETLOCK;							\
	unsigned int * lockptr;						\
	GETLOCKPTR(lockptr, mainao,j);					\
	if (likely(write_trylock(lockptr))) {				\
	  unsigned int localversion;					\
	  unsigned int remoteversion;					\
	  GETVERSIONVAL(localversion, transao, j);			\
	  GETVERSIONVAL(remoteversion, mainao, j);			\
	  if (likely(localversion == remoteversion)) {			\
	    addwrobject=1;						\
	  } else {							\
	    DVRELEASELOCK(mainao);					\
	    ARRAYABORT;							\
	  }								\
	} else {							\
	  j--;								\
	  DVRELEASELOCK(mainao);					\
	  ARRAYABORT;							\
	}								\
      } else if (status==STMCLEAN) {					\
	addrdobject=1;							\
      }									\
    }									\
    if (addwrobject) {							\
      DVSETINDEX							\
      dirwrlocked[numoidwrlocked++] = objptr;				\
    }   								\
    if (addrdobject) {							\
      oidrdlockedarray[numoidrdlockedarray++]=objptr;			\
    }									\
  } else


  /** This code allows us to skip the expensive checks in some cases */

#ifdef DUALVIEW 
#define QUICKCHECK {							\
    objheader_t * head=&((objheader_t *)mainao)[-1];			\
    if (head->lock==RW_LOCK_BIAS&&					\
	((objheader_t *)transao)[-1].version==head->version)		\
      continue;								\
  }
#define ARRAYCHECK					\
  if (transao->arrayversion!=mainao->arrayversion) {	\
    ABORT;}
#else
#define QUICKCHECK
#define ARRAYCHECK
#endif
  
#define READARRAYS							\
  for(i=0; i<numoidrdlockedarray; i++) {				\
    struct ArrayObject * transao=(struct ArrayObject *) oidrdlockedarray[i]; \
    struct ArrayObject * mainao=(struct ArrayObject *) transao->___objlocation___; \
    ARRAYCHECK;								\
    QUICKCHECK;								\
    DVCHECKLOCK(mainao);						\
    int lowoffset=(transao->lowindex);					\
    int highoffset=(transao->highindex);				\
    int j;								\
    for(j=lowoffset; j<=highoffset;j++) {				\
      unsigned int locallock;GETLOCKVAL(locallock,transao,j);		\
      if (locallock==STMCLEAN) {					\
	/* do read check */						\
	unsigned int mainlock; GETLOCKVAL(mainlock, mainao, j);		\
	if (mainlock>0) {						\
	  CFENCE;							\
	  unsigned int localversion;					\
	  unsigned int remoteversion;					\
	  GETVERSIONVAL(localversion, transao, j);			\
	  GETVERSIONVAL(remoteversion, mainao, j);			\
	  if (localversion != remoteversion) {				\
	    transAbortProcess(oidwrlocked, NUMWRTOTAL ARRAYDELAYWRAP1(dirwrindex) ARRAYDELAYWRAP1(numoidwrlocked)); \
	    freearrays;							\
	    return TRANS_ABORT;						\
	  }								\
	}								\
	READCHECK							\
	else {								\
	  unsigned int localversion;					\
	  unsigned int remoteversion;					\
	  GETVERSIONVAL(localversion, transao, j);			\
	  GETVERSIONVAL(remoteversion, mainao, j);			\
	  if (localversion==remoteversion)				\
	    softabort=1;						\
	  transAbortProcess(oidwrlocked, NUMWRTOTAL ARRAYDELAYWRAP1(dirwrindex) ARRAYDELAYWRAP1(numoidwrlocked)); \
	  freearrays;							\
	  if (softabort)						\
	    return TRANS_SOFT_ABORT;					\
	  else								\
	    return TRANS_ABORT;						\
	}								\
      }									\
    }									\
  }
#else
#define ARRAYDEFINES
#define PROCESSARRAY
#define READARRAYS
#define STMARRAYFREE
#define STMARRAYALLOC
#define STMARRAYASSIGN
#define STMARRAYDELAYFREE
#define STMARRAYDELAYALLOC
#define STMARRAYDELAYASSIGN
#endif

#ifdef DELAYCOMP
#if defined(STMARRAY)&&!defined(DUALVIEW)
#define ARRAYLOCK							\
  int intkey=dc_curr->intkey;						\
  if (intkey!=-1) {							\
    unsigned int *lockptr;						\
    GETLOCKPTR(lockptr, objptr, intkey);				\
    if (likely(write_trylock(lockptr))) {				\
      /*have lock on element */						\
      dirwrlocked[numoidwrtotal]=objptr;				\
      dirwrindex[numoidwrtotal++]=intkey;				\
    } else {								\
      chashlistnode_t *node = &c_table[(((unsigned INTPTR)objptr) & c_mask)>>4]; \
      do {								\
	if(node->key == objptr) {					\
	  unsigned int lockval;						\
	  GETLOCKVAL(lockval, ((struct ArrayObject *)node->val), intkey); \
	  if (lockval!=STMDIRTY) {					\
	    /*have to abort to avoid deadlock*/				\
	    transAbortProcess(oidwrlocked, numoidwrtotal, dirwrindex, numoidwrlocked); \
	    ABORTSTAT1;							\
	    freearrays;							\
	    if (softabort)						\
	      return TRANS_SOFT_ABORT;					\
	    else							\
	      return TRANS_ABORT;					\
	  }								\
	  break;							\
	}								\
	node = node->next;						\
        if(node==NULL) {						\
	  transAbortProcess(oidwrlocked, numoidwrtotal, dirwrindex, numoidwrlocked); \
	  ABORTSTAT1;							\
	  freearrays;							\
	  if (softabort)						\
	    return TRANS_SOFT_ABORT;					\
	  else								\
	    return TRANS_ABORT;						\
	}								\
       } while(1);							\
    }									\
  } else

#elif defined(STMARRAY)&&defined(DUALVIEW)
#define ARRAYLOCK							\
  if (((struct ___Object___ *)objptr)->type>=NUMCLASSES) {		\
    if (likely(rwwrite_trylock(&header->lock))) {			\
      dirwrindex[numoidwrtotal]=0;					\
      dirwrlocked[numoidwrtotal++] = objptr;				\
    } else {								\
      chashlistnode_t *node = &c_table[(((unsigned INTPTR)objptr) & c_mask)>>4]; \
      do {								\
	if(node->key == objptr) {					\
	  objheader_t * headeraddr=&((objheader_t *) node->val)[-1];	\
	  if(STATUS(headeraddr) & DIRTY) {				\
	    if (likely(rwconvert_trylock(&header->lock))) {		\
	      dirwrindex[numoidwrtotal]=1;				\
	      dirwrlocked[numoidwrtotal++] = objptr;			\
	      goto nextloop;						\
	    }								\
	  }								\
	  break;							\
	}								\
	node = node->next;						\
      } while(node != NULL);						\
      ABORTREAD;							\
    }									\
  } else
#else
#define ARRAYLOCK
#endif


#define ACCESSLOCKS							\
  unsigned int numoidwrtotal=numoidwrlocked;				\
  dchashlistnode_t *dc_curr = dc_c_list;				\
  /* Inner loop to traverse the linked list of the cache lookupTable */ \
  while(likely(dc_curr != NULL)) {					\
    /*if the first bin in hash table is empty	*/			\
    void *objptr=dc_curr->key;						\
    objheader_t *header=(objheader_t *)(((char *)objptr)-sizeof(objheader_t)); \
    ARRAYLOCK								\
    if(likely(write_trylock(&header->lock))) { /*can aquire write lock*/ \
      ARRAYDELAYWRAP(dirwrindex[numoidwrtotal]=-1;)			\
      dirwrlocked[numoidwrtotal++] = objptr;				\
    } else {								\
      /* maybe we already have lock*/					\
      chashlistnode_t *node = &c_table[(((unsigned INTPTR)objptr) & c_mask)>>4]; \
      									\
      do {								\
	if(node->key == objptr) {					\
	  objheader_t * headeraddr=&((objheader_t *) node->val)[-1];	\
	  if(STATUS(headeraddr) & DIRTY) {				\
	    goto nextloop;						\
	  } else							\
	    break;							\
	}								\
	node = node->next;						\
      } while(node != NULL);						\
									\
      /*have to abort to avoid deadlock	*/				\
      transAbortProcess(oidwrlocked, numoidwrtotal ARRAYDELAYWRAP1(dirwrindex) ARRAYDELAYWRAP1(numoidwrlocked)); \
      ABORTSTAT1;							\
      freearrays;							\
      if (softabort)							\
	return TRANS_SOFT_ABORT;					\
      else								\
	return TRANS_ABORT;						\
    }									\
  nextloop:								\
    dc_curr = dc_curr->lnext;						\
  }							
#else
#define ACCESSLOCKS
#endif

#ifdef DELAYCOMP
void lwreset(dchashlistnode_t *dc_curr) {
  dchashlistnode_t *ptr = dc_c_table;				
  dchashlistnode_t *top=&ptr[dc_c_size];			
  dchashlistnode_t *tmpptr=dc_c_list;				
  int reset=1;
  while(tmpptr!=NULL) {						
    dchashlistnode_t *next=tmpptr->lnext;			
    if (reset) {
      if (tmpptr==dc_curr) {
	reset=0;
      } else {
	struct ___Object___ * objptr=tmpptr->key;		
	objheader_t *header=&((objheader_t *)objptr)[-1];	
	if (objptr->type>=NUMCLASSES) {			
	  rwwrite_unlock(&header->lock);
	} else {
	  write_unlock(&header->lock);
	}
      }
    }
    if (tmpptr>=ptr&&tmpptr<top) {				
      //zero in list						
      tmpptr->key=NULL;						
      tmpptr->next=NULL;					
    }								
    tmpptr=next;
  }								
  while(dc_c_structs->next!=NULL) {				
    dcliststruct_t *next=dc_c_structs->next;			
    free(dc_c_structs);						
    dc_c_structs=next;						
  }								
  dc_c_structs->num = 0;					
  dc_c_numelements = 0;					       
  dc_c_list=NULL;
  ptrstack.count=0;
  primstack.count=0;
  branchstack.count=0;
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
  struct fixedlist wrlocked;
  int softabort=0;
  int i;
  void ** oidrdlocked;
  int * oidrdversion;
  ARRAYDEFINES;
  STMWRAP(int rdage[200];int * oidrdage;int ObjSeqId;int objtypetraverse[TOTALNUMCLASSANDARRAY];);
  struct garbagelist * oidwrlocked;
  void ** dirwrlocked;
#if defined(STMARRAY)&&defined(DELAYCOMP)
  int wrindex[200];
  int * dirwrindex;
#endif
  allocarrays;

  STMWRAP(for(i=0; i<TOTALNUMCLASSANDARRAY; i++) objtypetraverse[i] = 0;);

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
      objheader_t * cachedobj=curr->val;
      objheader_t * headeraddr=&((objheader_t *) cachedobj)[-1]; //cached object
      void * objptr=curr->key;
      objheader_t *header=(objheader_t *)(((char *)objptr)-sizeof(objheader_t)); //real object
      unsigned int version = headeraddr->version;

      if(STATUS(headeraddr) & DIRTY) {
	PROCESSARRAY
	/* Read from the main heap  and compare versions */
	if(likely(write_trylock(&header->lock))) { //can aquire write lock
	  if (likely(version == header->version)) { /* versions match */
	    /* Keep track of objects locked */
	    dirwrlocked[numoidwrlocked++] = objptr;
	  } else {
	    dirwrlocked[numoidwrlocked++] = objptr;
	    transAbortProcess(oidwrlocked, numoidwrlocked ARRAYDELAYWRAP1(NULL) ARRAYDELAYWRAP1(numoidwrlocked));
	    ABORTSTAT1;
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
	  transAbortProcess(oidwrlocked, numoidwrlocked ARRAYDELAYWRAP1(NULL) ARRAYDELAYWRAP1(numoidwrlocked));
	  ABORTSTAT1;
	  freearrays;
	  if (softabort)
	    return TRANS_SOFT_ABORT;
	  else 
	    return TRANS_ABORT;
      
	}
      } else {
	STMWRAP(oidrdage[numoidrdlocked]=headeraddr->accessCount;);
	oidrdversion[numoidrdlocked]=version;
	oidrdlocked[numoidrdlocked++]=header;
      }
      curr = curr->next;
    }
  } //end of for
  
  ACCESSLOCKS;

  //THIS IS THE SERIALIZATION END POINT (START POINT IS END OF EXECUTION)*****
  READARRAYS;

  for(i=0; i<numoidrdlocked; i++) {
    /* Read from the main heap  and compare versions */
    objheader_t *header=oidrdlocked[i];
    unsigned int version=oidrdversion[i];
#if defined(STMARRAY)&&defined(DUALVIEW)
    unsigned int isobject=((struct ___Object___ *)&header[1])->type<NUMCLASSES;
    if(likely((isobject&&header->lock>0)||(!isobject&&header->lock==RW_LOCK_BIAS))) {
#else      
    if(likely(header->lock>0)) { //not write locked
#endif
      CFENCE;
      if(version != header->version) { /* versions do not match */
	transAbortProcess(oidwrlocked, NUMWRTOTAL ARRAYDELAYWRAP1(dirwrindex) ARRAYDELAYWRAP1(numoidwrlocked));
	ABORTSTAT2;
	freearrays;
	return TRANS_ABORT;
      }
#if DELAYCOMP
    } else if (dc_t_chashSearch(((char *)header)+sizeof(objheader_t))) {
      //couldn't get lock because we already have it
      //check if it is the right version number
      if (version!=header->version) {
	transAbortProcess(oidwrlocked, numoidwrtotal ARRAYDELAYWRAP1(dirwrindex) ARRAYDELAYWRAP1(numoidwrlocked));
	ABORTSTAT2;
	freearrays;
	return TRANS_ABORT;
      }
#endif
    } else { /* cannot aquire lock */
      //do increment as we didn't get lock
      if(version == header->version) {
	softabort=1;
      }
      transAbortProcess(oidwrlocked, NUMWRTOTAL ARRAYDELAYWRAP1(dirwrindex) ARRAYDELAYWRAP1(numoidwrlocked));
      ABORTSTAT2;
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
    struct ___Object___ * objptr=rd_curr->key;
    objheader_t *header=(objheader_t *)(((char *)objptr)-sizeof(objheader_t));
#if defined(STMARRAY)&&defined(DUALVIEW)
    unsigned int isobject=objptr->type<NUMCLASSES;
    if(likely((isobject&&header->lock>0)||(!isobject&&header->lock==RW_LOCK_BIAS))) {
#else      
    if(likely(header->lock>0)) { //not write locked
#endif
      //object is not write locked
      if (unlikely(version!=header->version)) {
	//have to abort
	transAbortProcess(oidwrlocked, NUMWRTOTAL ARRAYDELAYWRAP1(dirwrindex) ARRAYDELAYWRAP1(numoidwrlocked));
	STMWRAP((typesCausingAbort[TYPE(header)])++;);
	freearrays;
	if (softabort)
	  return TRANS_SOFT_ABORT;
	else
	  return TRANS_ABORT;
      }
    } else {
      //maybe we already have lock
      if (likely(version==header->version)) {
	void * key=rd_curr->key;
#ifdef DELAYCOMP
	//check to see if it is in the delaycomp table
	{
	  dchashlistnode_t *node = &dc_c_table[(((unsigned INTPTR)key) & dc_c_mask)>>4];
	  do {
	    if(node->key == key) {
	      goto nextloopread;
	    }
	    node = node->next;
	  } while(node != NULL);
	}
#endif
	//check normal table
#ifdef STMARRAY
      if (likely(objptr->type<NUMCLASSES||header->lock==(RW_LOCK_BIAS-1))) {	
#else
	{
#endif
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
      //have to abort to avoid deadlock
      transAbortProcess(oidwrlocked, NUMWRTOTAL ARRAYDELAYWRAP1(dirwrindex) ARRAYDELAYWRAP1(numoidwrlocked));
      STMWRAP((typesCausingAbort[TYPE(header)])++;);
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
  transCommitProcess(oidwrlocked ARRAYDELAYWRAP1(dirwrindex), numoidwrlocked, numoidwrtotal, commitmethod, primitives, locals, params);
#else
  transCommitProcess(oidwrlocked, numoidwrlocked);
#endif
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
  struct fixedlist wrlocked;
  int softabort=0;
  int i;
  void ** oidrdlocked;
  int * oidrdversion;
  STMWRAP(int rdage[200];int * oidrdage;int ObjSeqId;int objtypetraverse[TOTALNUMCLASSANDARRAY];);
  ARRAYDEFINES;
  struct garbagelist * oidwrlocked;
  void ** dirwrlocked;
#if defined(STMARRAY)&&defined(DELAYCOMP)
  int wrindex[200];
  int * dirwrindex;
#endif
  allocarrays;

  STMWRAP(for(i=0; i<TOTALNUMCLASSANDARRAY; i++) objtypetraverse[i] = 0;);

  chashlistnode_t *curr = c_list;
  /* Inner loop to traverse the linked list of the cache lookupTable */
  while(likely(curr != NULL)) {
    //if the first bin in hash table is empty
      objheader_t * cachedobj=curr->val;
    objheader_t * headeraddr=&((objheader_t *) cachedobj)[-1];
    void *objptr=curr->key;
    objheader_t *header=(objheader_t *)(((char *)objptr)-sizeof(objheader_t));
    unsigned int version = headeraddr->version;

    if(STATUS(headeraddr) & DIRTY) {
      PROCESSARRAY
      /* Read from the main heap  and compare versions */
      if(likely(write_trylock(&header->lock))) { //can aquire write lock
	if (likely(version == header->version)) { /* versions match */
	  /* Keep track of objects locked */
	  dirwrlocked[numoidwrlocked++] = objptr;
	} else {
	  dirwrlocked[numoidwrlocked++] = objptr;
	  transAbortProcess(oidwrlocked, numoidwrlocked ARRAYDELAYWRAP1(NULL) ARRAYDELAYWRAP1(numoidwrlocked));
	  ABORTSTAT3;
	  freearrays;
	  return TRANS_ABORT;
	}
      } else { /* cannot aquire lock */
	if(version == header->version) {
	  /* versions match */
	  softabort=1;
	}
	transAbortProcess(oidwrlocked, numoidwrlocked ARRAYDELAYWRAP1(NULL) ARRAYDELAYWRAP1(numoidwrlocked));
	ABORTSTAT3;
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

  ACCESSLOCKS;

  //THIS IS THE SERIALIZATION END POINT (START POINT IS END OF EXECUTION)*****
  READARRAYS;

  for(i=0; i<numoidrdlocked; i++) {
    objheader_t * header=oidrdlocked[i];
    unsigned int version=oidrdversion[i];
#if defined(STMARRAY)&&defined(DUALVIEW)
    unsigned int isobject=((struct ___Object___ *)&header[1])->type<NUMCLASSES;
    if(likely((isobject&&header->lock>0)||(!isobject&&header->lock==RW_LOCK_BIAS))) {
#else      
    if(likely(header->lock>0)) { //not write locked
#endif
      CFENCE;
      if(unlikely(version != header->version)) {
	transAbortProcess(oidwrlocked, NUMWRTOTAL ARRAYDELAYWRAP1(dirwrindex) ARRAYDELAYWRAP1(numoidwrlocked));
	ABORTSTAT2;
	freearrays;
	return TRANS_ABORT;
      }
#ifdef DELAYCOMP
    } else if (dc_t_chashSearch(((char *)header)+sizeof(objheader_t))) {
      //couldn't get lock because we already have it
      //check if it is the right version number
      if (version!=header->version) {
	transAbortProcess(oidwrlocked, numoidwrtotal ARRAYDELAYWRAP1(dirwrindex) ARRAYDELAYWRAP1(numoidwrlocked));
	ABORTSTAT4;
	freearrays;
	return TRANS_ABORT;
      }
#endif
    } else { /* cannot aquire lock */
      if(version == header->version) {
	softabort=1;
      }
      transAbortProcess(oidwrlocked, NUMWRTOTAL ARRAYDELAYWRAP1(dirwrindex) ARRAYDELAYWRAP1(numoidwrlocked));
      ABORTSTAT2;
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
    struct ___Object___ * objptr=rd_curr->key;
    objheader_t *header=(objheader_t *)(((char *)objptr)-sizeof(objheader_t));
#if defined(STMARRAY)&&defined(DUALVIEW)
    unsigned int isobject=objptr->type<NUMCLASSES;
    if(likely((isobject&&header->lock>0)||(!isobject&&header->lock==RW_LOCK_BIAS))) {
#else      
    if(likely(header->lock>0)) { //not write locked
#endif
      if (unlikely(version!=header->version)) {
	//have to abort
	transAbortProcess(oidwrlocked, NUMWRTOTAL ARRAYDELAYWRAP1(dirwrindex) ARRAYDELAYWRAP1(numoidwrlocked));
	STMWRAP((typesCausingAbort[TYPE(header)])++;);
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
	  dchashlistnode_t *node = &dc_c_table[(((unsigned INTPTR)key) & dc_c_mask)>>4];
	  do {
	    if(node->key == key)
	      goto nextloopread;
	    node = node->next;
	  } while(node != NULL);
	}
#endif
	//check normal table
#ifdef STMARRAY
	if (likely(objptr->type<NUMCLASSES||header->lock==(RW_LOCK_BIAS-1))) {	
#else
	  {
#endif
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
      //have to abort to avoid deadlock
      transAbortProcess(oidwrlocked, NUMWRTOTAL ARRAYDELAYWRAP1(dirwrindex) ARRAYDELAYWRAP1(numoidwrlocked));
      STMWRAP((typesCausingAbort[TYPE(header)])++;);
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
  transCommitProcess(oidwrlocked ARRAYDELAYWRAP1(dirwrindex), numoidwrlocked, numoidwrtotal, commitmethod, primitives, locals, params);
#else
  transCommitProcess(oidwrlocked, numoidwrlocked);
#endif
  freearrays;
  return TRANS_COMMIT;
}

/* ==================================
 * transAbortProcess
 *
 * =================================
 */

int logflag=1;
 
#if defined(STMARRAY)&&defined(DELAYCOMP)
void transAbortProcess(struct garbagelist *oidwrlocked, int numoidwrtotal, int * dirwrindex, int numoidwrlocked) {
#else
void transAbortProcess(struct garbagelist *oidwrlocked, int numoidwrlocked) {
#endif
  int i;
  objheader_t *header;
  /* Release read locks */
  void ** dirwrlocked=oidwrlocked->array;
  /* Release write locks */
  for(i=numoidwrlocked-1; i>=0; i--) {
    /* Read from the main heap */
    struct ___Object___ * dst=dirwrlocked[i];
    header = &((objheader_t *)dst)[-1];
#ifdef STMARRAY
    int type=dst->type;
    if (type>=NUMCLASSES) {
      //have array, do unlocking of bins
      struct ArrayObject *src=(struct ArrayObject *)t_chashSearch(dst);
      int lowoffset=(src->lowindex);
      int highoffset=(src->highindex);
      int j;
      int addwrobject=0, addrdobject=0;
      for(j=lowoffset; j<=highoffset;j++) {
	unsigned int status;
	GETLOCKVAL(status, src, j);
	if (status==STMDIRTY) {
	  unsigned int *lockptr;
	  GETLOCKPTR(lockptr, ((struct ArrayObject *)dst), j);
	  write_unlock(lockptr);
	}
      }
#ifdef DUALVIEW
      //release object array lock
      rwread_unlock(&header->lock);
#endif
    } else
#endif
      write_unlock(&header->lock);
  }
#if defined(STMARRAY)&&defined(DELAYCOMP)&&!defined(DUALVIEW)
  //release access locks
  for(i=numoidwrtotal-1; i>=numoidwrlocked; i--) {
    struct ___Object___ * dst=dirwrlocked[i];
    header = &((objheader_t *)dst)[-1];
    int wrindex=dirwrindex[i];
    if (wrindex==-1) {
      //normal object
      write_unlock(&header->lock);
    } else {
      //array element
      unsigned int *intptr;
      GETLOCKPTR(intptr, ((struct ArrayObject *)dst), wrindex);
      write_unlock(intptr);
    }
  }
#endif
#if defined(STMARRAY)&&defined(DELAYCOMP)&&defined(DUALVIEW)
  //release access locks
  for(i=numoidwrtotal-1; i>=numoidwrlocked; i--) {
    struct ___Object___ * dst=dirwrlocked[i];
    header = &((objheader_t *)dst)[-1];
    int wrindex=dirwrindex[i];
    if (wrindex==-1) {
      //normal object
      write_unlock(&header->lock);
    } else if (wrindex==0) {
      //array element
      rwwrite_unlock(&header->lock);
    } else {
      rwconvert_unlock(&header->lock);
    }
  }
#endif
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
void transCommitProcess(struct garbagelist * oidwrlocked ARRAYDELAYWRAP1(int * dirwrindex), int numoidwrlocked, int numoidwrtotal, void (*commitmethod)(void *, void *, void *), void * primitives, void * locals, void * params) {
#else
void transCommitProcess(struct garbagelist * oidwrlocked, int numoidwrlocked) {
#endif
  objheader_t *header;
  void *ptrcreate;
  int i;
  struct objlist *ptr=newobjs;
  void **dirwrlocked=oidwrlocked->array;
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
    header = &((objheader_t *)dirwrlocked[i])[-1];
    int tmpsize;
    GETSIZE(tmpsize, header);
    struct ___Object___ *dst=(struct ___Object___*)dirwrlocked[i];
    struct ___Object___ *src=t_chashSearch(dst);
    dst->___cachedCode___=src->___cachedCode___;
    dst->___cachedHash___=src->___cachedHash___;
#ifdef STMARRAY
    int type=dst->type;
    if (type>=NUMCLASSES) {
      //have array, do copying of bins
      int lowoffset=(((struct ArrayObject *)src)->lowindex);
      int highoffset=(((struct ArrayObject *)src)->highindex);
      int j;
      int addwrobject=0, addrdobject=0;
      int elementsize=classsize[type];
      int baseoffset=(lowoffset<<INDEXSHIFT)+sizeof(int)+((int)&(((struct ArrayObject *)0)->___length___));
      char *dstptr=((char *)dst)+baseoffset;
      char *srcptr=((char *)src)+baseoffset;
      for(j=lowoffset; j<=highoffset;j++, srcptr+=INDEXLENGTH,dstptr+=INDEXLENGTH) {
	unsigned int status;
	GETLOCKVAL(status, ((struct ArrayObject *)src), j);
	if (status==STMDIRTY) {
	  A_memcpy(dstptr, srcptr, INDEXLENGTH);
	}
      }
    } else
#endif 
      A_memcpy(&dst[1], &src[1], tmpsize-sizeof(struct ___Object___));
  }
  CFENCE;

#ifdef DELAYCOMP
  //  call commit method
  ptrstack.maxcount=0;
  primstack.count=0;
  branchstack.count=0;
#if defined(STMARRAY)&&!defined(DUALVIEW)
  arraystack.maxcount=0;
#endif
  //splice oidwrlocked in
  oidwrlocked->size=numoidwrtotal;
  oidwrlocked->next=params;
  ((struct garbagelist *)locals)->next=oidwrlocked;
  if (commitmethod!=NULL)
    commitmethod(params, locals, primitives);
  ((struct garbagelist *)locals)->next=params;
#endif

  /* Release write locks */
#if defined(STMARRAY)&&defined(DELAYCOMP)
  for(i=numoidwrlocked-1; i>=0; i--) {
#else
  for(i=NUMWRTOTAL-1; i>=0; i--) {
#endif
    struct ___Object___ * dst=dirwrlocked[i];
    header = &((objheader_t *)dst)[-1];
#ifdef STMARRAY
    int type=dst->type;
    if (type>=NUMCLASSES) {
      //have array, do unlocking of bins
      struct ArrayObject *src=(struct ArrayObject *)t_chashSearch(dst);
      int lowoffset=(src->lowindex);
      int highoffset=(src->highindex);
      int j;
      int addwrobject=0, addrdobject=0;
      for(j=lowoffset; j<=highoffset;j++) {
	unsigned int status;
	GETLOCKVAL(status, src, j);
	if (status==STMDIRTY) {
	  unsigned int *intptr;
	  GETVERSIONPTR(intptr, ((struct ArrayObject *)dst), j);
	  (*intptr)++;
	  GETLOCKPTR(intptr, ((struct ArrayObject *)dst), j);
	  write_unlock(intptr);
	}
      }
      atomic_inc(&header->version);
#ifdef DUALVIEW
      rwread_unlock(&header->lock);
#endif
    } else
#endif
    {
      header->version++;
      write_unlock(&header->lock);
    }
  }
#if defined(STMARRAY)&&defined(DELAYCOMP)&&defined(DUALVIEW)
  //release access locks
  for(i=numoidwrtotal-1; i>=numoidwrlocked; i--) {
    struct ___Object___ * dst=dirwrlocked[i];
    int wrlock=dirwrindex[i];
    header = &((objheader_t *)dst)[-1];
    if (wrlock==-1) {
      header->version++;
      write_unlock(&header->lock);
    } else if (wrlock==0) {
      header->version++;
      rwwrite_unlock(&header->lock);
    } else {
      header->version++;
#ifdef DUALVIEW
      ((struct ArrayObject*)dst)->arrayversion++;
#endif
      rwconvert_unlock(&header->lock);
    }
  }
#endif
#if defined(STMARRAY)&&defined(DELAYCOMP)&&!defined(DUALVIEW)
  //release access locks
  for(i=numoidwrtotal-1; i>=numoidwrlocked; i--) {
    struct ___Object___ * dst=dirwrlocked[i];
    header = &((objheader_t *)dst)[-1];
    int wrindex=dirwrindex[i];
    if (wrindex==-1) {
      //normal object
      header->version++;
      write_unlock(&header->lock);
    } else {
      //array element
      unsigned int *intptr;
      atomic_inc(&header->version);
      GETVERSIONPTR(intptr, ((struct ArrayObject *)dst), wrindex);
      (*intptr)++;
      GETLOCKPTR(intptr, ((struct ArrayObject *)dst), wrindex);
      write_unlock(intptr);
    }
  }
#endif
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
