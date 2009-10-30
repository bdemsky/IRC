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
#if defined(STMARRAY)&&!defined(DUALVIEW)
__thread struct arraylist arraystack;
#endif
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
}

/* =======================================================
 * transCreateObj
 * This function creates objects in the transaction record
 * =======================================================
 */
#ifdef STMARRAY
objheader_t *transCreateObj(void * ptr, unsigned int size, int bytelength) {
  char *tmpchar = mygcmalloc(ptr, (sizeof(objheader_t) + size));
  objheader_t *tmp = (objheader_t *) (tmpchar+bytelength);
#else
objheader_t *transCreateObj(void * ptr, unsigned int size) {
  objheader_t *tmp = mygcmalloc(ptr, (sizeof(objheader_t) + size));
#endif
  objheader_t *retval=tmp+1;
#ifdef DUALVIEW
  if (bytelength==0) {
    tmp->lock=SWAP_LOCK_BIAS;
  } else {
    tmp->lock=RW_LOCK_BIAS;
  }
#else
  tmp->lock=SWAP_LOCK_BIAS;
#endif
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

//void *TR(void *x, void * y, void *z) {
//  void * inputvalue;				
//  if ((inputvalue=y)==NULL) x=NULL;		
//  else {
//    chashlistnode_t * cnodetmp=&c_table[(((unsigned INTPTR)inputvalue)&c_mask)>>4]; 
//    do { 
//      if (cnodetmp->key==inputvalue) {x=cnodetmp->val; break;} 
//      cnodetmp=cnodetmp->next; 
//      if (cnodetmp==NULL) {if (((struct ___Object___*)inputvalue)->___objstatus___&NEW) {x=inputvalue; break;} else
//			     {x=transRead(inputvalue,z); asm volatile ("" : "=m" (c_table),"\=m" (c_mask)); break;}}
//    } while(1);
//  }
//  return x;
//}

//__attribute__ ((pure)) 
void *transRead(void * oid, void *gl) {
  objheader_t *tmp, *objheader;
  objheader_t *objcopy;
  int size;

  objheader_t *header = (objheader_t *)(((char *)oid) - sizeof(objheader_t));
#ifdef STMSTATS
  header->accessCount++;
  if(header->riskyflag) {
    header=needLock(header,gl);
  }
#endif
#ifdef STMARRAY
  int type=TYPE(header);
  if (type>=NUMCLASSES) {
    int basesize=((struct ArrayObject *)oid)->___length___*classsize[type];
    basesize=(basesize+LOWMASK)&HIGHMASK;
    int metasize=sizeof(int)*2*(basesize>>INDEXSHIFT);
    size = basesize + sizeof(objheader_t)+metasize+sizeof(struct ArrayObject);
    char *tmpptr = (char *) objstrAlloc(size);
    //    bzero(tmpptr, metasize);//clear out stm data
    objcopy=(objheader_t *) (tmpptr+metasize);
    A_memcpy(objcopy, header, sizeof(objheader_t)+sizeof(struct ArrayObject)); //copy the metadata and base array info
  } else {
    GETSIZE(size, header);
    size += sizeof(objheader_t);
    objcopy = (objheader_t *) objstrAlloc(size);
    A_memcpy(objcopy, header, size);
  }
#else
  GETSIZE(size, header);
  size += sizeof(objheader_t);
  objcopy = (objheader_t *) objstrAlloc(size);
  A_memcpy(objcopy, header, size);
#endif
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

#ifdef STMARRAY
//caller needs to mark data as present
 void arraycopy(struct ArrayObject *oid, int byteindex) {
   struct ArrayObject * orig=(struct ArrayObject *) oid->___objlocation___;
   int baseoffset=byteindex&HIGHMASK;
   unsigned int mainversion;
   int baseindex=baseoffset>>INDEXSHIFT;
   if (oid->lowindex>baseindex) {
     unsigned int * ptr;
     if (oid->lowindex==MAXARRAYSIZE) {
       GETLOCKPTR(ptr, oid, baseindex);
       bzero(ptr, sizeof(int)*2);
     } else {
       GETLOCKPTR(ptr, oid, oid->lowindex-1);
       int length=oid->lowindex-baseindex;
       bzero(ptr, sizeof(int)*2*length);
     }
     oid->lowindex=baseindex;
   }
   if (oid->highindex<baseindex) {
     unsigned int * ptr;
     if (oid->highindex==-1) {
       GETLOCKPTR(ptr, oid, baseindex);
       bzero(ptr, 2*sizeof(int));
     } else {
       GETLOCKPTR(ptr, oid, baseindex);
       bzero(ptr, 2*sizeof(int)*(baseindex-oid->highindex));
     }
     oid->highindex=baseindex;
   }
   GETVERSIONVAL(mainversion, orig, baseindex);
   SETVERSION(oid, baseindex, mainversion);
   A_memcpy(((char *)&oid[1])+baseoffset, ((char *)&orig[1])+baseoffset, INDEXLENGTH);
 }
#endif

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

