#include "addPrefetchEnhance.h"
#include "prelookup.h"

extern int numprefetchsites; // Number of prefetch sites
extern pfcstats_t *evalPrefetch; //Global array that keeps track of operation mode (ON/OFF) for each prefetch site
extern objstr_t *prefetchcache; //Global Prefetch cache
extern pthread_mutex_t prefetchcache_mutex; //Mutex to lock Prefetch Cache
extern unsigned int myIpAddr;

/* This function creates and initializes the
 * evalPrefetch global array */
pfcstats_t *initPrefetchStats() {
  pfcstats_t *ptr;
  if((ptr = calloc(numprefetchsites, sizeof(pfcstats_t))) == NULL) {
    printf("%s() Calloc error in %s at line %d\n", __func__, __FILE__, __LINE__);
    return NULL;
  }
  int i;
  /* Enable prefetching at the beginning */
  for(i=0; i<numprefetchsites; i++) {
    ptr[i].operMode = 1;
    ptr[i].callcount = 0;
    ptr[i].retrycount = RETRYINTERVAL; //N
    ptr[i].uselesscount = SHUTDOWNINTERVAL; //M
  }
  return ptr;
}

int getRetryCount(int siteid) {
  return evalPrefetch[siteid].retrycount;
}

int getUselessCount(int siteid) {
  return evalPrefetch[siteid].uselesscount;
}

char getOperationMode(int siteid) {
  return evalPrefetch[siteid].operMode;
}

/* This function updates counters and mode of operation of a
 * prefetch site during runtime. When the prefetch call at a site
 * generates oids that are found/not found in the prefetch cache,
 * we take action accordingly */
void handleDynPrefetching(int numLocal, int ntuples, int siteid) {
  if(numLocal < ntuples) {
    /* prefetch not found locally(miss in cache) */
    evalPrefetch[siteid].operMode = 1;
    evalPrefetch[siteid].uselesscount = SHUTDOWNINTERVAL;
  } else {
    if(getOperationMode(siteid) != 0) {
      evalPrefetch[siteid].uselesscount--;
      if(evalPrefetch[siteid].uselesscount <= 0) {
	printf("O");
	evalPrefetch[siteid].operMode = 0;
      }
    }
  }
}

#if 1
/* This function clears from prefetch cache those
 * entries that caused a transaction abort */
void cleanPCache() {
  unsigned int size = c_size;
  chashlistnode_t *ptr = c_table;
  int i;
  for(i = 0; i < size; i++) {
    chashlistnode_t *curr = &ptr[i]; //for each entry in the cache lookupTable
    while(curr != NULL) {
      if(curr->key == 0)
	break;
      objheader_t *header1, *header2;
      /* Not found in local machine's object store and found in prefetch cache */
      if((header1 = mhashSearch(curr->key)) == NULL && ((header2 = prehashSearch(curr->key)) != NULL)) {
	/* Remove from prefetch cache */
	prehashRemove(curr->key);
      }
      curr = curr->next;
    }
  }
}
#else
/* This function clears from prefetch cache those
 * entries that caused a transaction abort */
void cleanPCache() {
  unsigned int size = c_size;
  struct chashentry *ptr = c_table;
  int i;
  for(i = 0; i < size; i++) {
    struct chashentry *curr = &ptr[i]; //for each entry in the cache lookupTable
    if(curr->key == 0)
      continue;
    objheader_t *header1, *header2;
    /* Not found in local machine's object store and found in prefetch cache */
    if((header1 = mhashSearch(curr->key)) == NULL && ((header2 = prehashSearch(curr->key)) != NULL)) {
      /* Remove from prefetch cache */
      prehashRemove(curr->key);
    }
  }
}
#endif

/* This function updates the prefetch cache with
 * entries from the transaction cache when a
 * transaction commits
 * Return -1 on error else returns 0 */
int updatePrefetchCache(trans_req_data_t *tdata) {
  int retval;
  char oidType;
  oidType = 'R';
  if(tdata->f.numread > 0) {
    if((retval = copyToCache(tdata->f.numread, (unsigned int *)(tdata->objread), oidType)) != 0) {
      printf("%s(): Error in copying objects read at %s, %d\n", __func__, __FILE__, __LINE__);
      return -1;
    }
  }
  if(tdata->f.nummod > 0) {
    oidType = 'M';
    if((retval = copyToCache(tdata->f.nummod, tdata->oidmod, oidType)) != 0) {
      printf("%s(): Error in copying objects read at %s, %d\n", __func__, __FILE__, __LINE__);
      return -1;
    }
  }
  return 0;
}

int copyToCache(int numoid, unsigned int *oidarray, char oidType) {
  int i;
  for (i = 0; i < numoid; i++) {
    unsigned int oid;
    if(oidType == 'R') {
      char * objread = (char *) oidarray;
      oid = *((unsigned int *)(objread+(sizeof(unsigned int)+
                                        sizeof(unsigned short))*i));
    } else {
      oid = oidarray[i];
    }
    pthread_mutex_lock(&prefetchcache_mutex);
    objheader_t * header;
    if((header = (objheader_t *) t_chashSearch(oid)) == NULL) {
      printf("%s() obj %x is no longer in transaction cache at %s , %d\n", __func__, oid,__FILE__, __LINE__);
      fflush(stdout);
      return -1;
    }
    //copy into prefetch cache
    int size;
    GETSIZE(size, header);
    objheader_t * newAddr;
    if((newAddr = prefetchobjstrAlloc(size + sizeof(objheader_t))) == NULL) {
      printf("%s(): Error in getting memory from prefetch cache at %s, %d\n", __func__,
             __FILE__, __LINE__);
      pthread_mutex_unlock(&prefetchcache_mutex);
      return -1;
    }
    pthread_mutex_unlock(&prefetchcache_mutex);
    memcpy(newAddr, header, size+sizeof(objheader_t));
    //Increment version for every modified object
    if(oidType == 'M') {
      newAddr->version += 1;
      newAddr->notifylist = NULL;
    }
    //make an entry in prefetch lookup hashtable
    void *oldptr;
    if((oldptr = prehashSearch(oid)) != NULL) {
      prehashRemove(oid);
      prehashInsert(oid, newAddr);
    } else {
      prehashInsert(oid, newAddr);
    }
  } //end of for
  return 0;
}
