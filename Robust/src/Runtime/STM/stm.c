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

#ifdef SANDBOX
#include "sandbox.h"
#endif

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

void * A_memcpy (void * dest, const void * src, size_t count) {
  int off=0;
  INTPTR *desti=(INTPTR *)dest;
  INTPTR *srci=(INTPTR *)src;

  //word copy
  while(count>=sizeof(INTPTR)) {
    desti[off]=srci[off];
    off+=1;
    count-=sizeof(INTPTR);
  }
  off*=sizeof(INTPTR);
  //byte copy
  while(count>0) {
    ((char *)dest)[off]=((char *)src)[off];
    off++;
    count--;
  }
}

/* ==================================================
 * stmStartup
 * This function starts up the transaction runtime.
 * ==================================================
 */
int stmStartup() {
  return 0;
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

/* =============================================================
 * transRead
 * -finds the objects either in main heap
 * -copies the object into the transaction cache
 * =============================================================
 */
//__attribute__ ((pure)) 
void *transRead(void * oid, void *gl) {
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
#ifdef STMSTATS
  /* keep track of the object's access sequence in a transaction */
  objheader_t *tmpheader = objcopy;
  tmpheader->accessCount = ++t_objnumcount;
#endif

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

