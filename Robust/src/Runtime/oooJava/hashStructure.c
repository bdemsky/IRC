#include "hashStructure.h"
//#include "WaitingQueue.h"
#include "mlp_lock.h"
#include "rcr_runtime.h"
#include "mem.h"
#include "classdefs.h"

__thread HashStructure ** allHashStructures;
#define ISWRITEBIN(x) (x&BINMASK)
#define ISREADBIN(x) (!(x&BINMASK))
//#define POPCOUNT(x) __builtin_popcountll(x)
//__builtin_popcountll
#define ONEVAL 1ULL


inline reenqueuerecord(struct rcrRecord *rcrrec, int tmpkey, BinItem_rcr *olditem, BinItem_rcr *newitem) {
  if (likely(rcrrec!=NULL)) {
    struct rcrRecord * tmprec=rcrrec;;
    int i;
    do {
      for(i=tmprec->index-1;i>=0;i--) {
	if (tmprec->ptrarray[i]==olditem&&tmprec->array[i]==tmpkey) {
	  tmprec->ptrarray[i]=newitem;
	  return;
	}
      }
      tmprec=tmprec->next;
    } while(1);
  }
}

inline enqueuerecord(struct rcrRecord *rcrrec, int tmpkey, BinItem_rcr *item) {
  if (likely(rcrrec!=NULL)) {
    struct rcrRecord * tmprec;
    if(likely(rcrrec->index<RCRSIZE)) {
      int index=rcrrec->index++;
      rcrrec->ptrarray[index]=(void *) item;
      rcrrec->array[index]=tmpkey;
    } else if(likely((tmprec=rcrrec->next)!=NULL)&&likely(tmprec->index<RCRSIZE)) {
      int index=tmprec->index++;
      tmprec->ptrarray[index]=(void *) item;
      tmprec->array[index]=tmpkey;
    } else {
      struct rcrRecord *trec=RUNMALLOC(sizeof(struct rcrRecord));
      trec->ptrarray[0]=(void *) item;
      trec->array[0]=tmpkey;
      trec->index=1;
      trec->next=tmprec;
      rcrrec->next=trec;
    }
  }
}

HashStructure ** rcr_createMasterHashTableArray(int maxSize){
  return (HashStructure **) malloc(sizeof(HashStructure *) * maxSize);
}

HashStructure* rcr_createHashtable(){
  int i=0;
  HashStructure* newTable=(HashStructure*)RUNMALLOC(sizeof(HashStructure));
  for(i=0;i<RNUMBINS;i++){
    newTable->array[i].head=NULL;
    newTable->array[i].tail=NULL;
  }
  //newTable->memPoolRead = poolcreate( sizeof(ReadBinItem_rcr), NULL );
  //newTable->memPoolWrite = poolcreate( sizeof(WriteBinItem_rcr), NULL );
  return newTable;
}

#define WBMAX 256
__thread WriteBinItem_rcr* bank=NULL;
__thread offset=WBMAX;


WriteBinItem_rcr* rcr_createWriteBinItem( HashStructure* htable ){
  //WriteBinItem_rcr* binitem=(WriteBinItem_rcr*)poolalloc( htable->memPoolWrite );
  if (offset==WBMAX) {
    bank=(WriteBinItem_rcr*)RUNMALLOC(sizeof(WriteBinItem_rcr)*WBMAX);
    offset=0;
  }
  
  WriteBinItem_rcr* binitem=&bank[offset++];
  //(WriteBinItem_rcr*)RUNMALLOC(sizeof(WriteBinItem_rcr));
  binitem->item.type=WRITEBIN;
  binitem->item.next=NULL;
  return binitem;
}


ReadBinItem_rcr* rcr_createReadBinItem( HashStructure* htable ){
  //ReadBinItem_rcr* binitem=(ReadBinItem_rcr*)poolalloc( htable->memPoolRead );
  ReadBinItem_rcr* binitem=(ReadBinItem_rcr*)RUNMALLOC(sizeof(ReadBinItem_rcr));
  binitem->index=0;
  binitem->item.type=READBIN;
  binitem->item.next=NULL;
  return binitem;
}

inline int rcr_generateKey(void * ptr){
  return (((struct ___Object___ *) ptr)->oid)&RH_MASK;
}

inline int rcr_BWRITEBINCASE(HashStructure *T, int key, SESEcommon *task, struct rcrRecord *rcrrec, int index, int mode) {
  //chain of bins exists => tail is valid
  //if there is something in front of us, then we are not ready
  BinItem_rcr * val;
  BinElement_rcr* be= &(T->array[key]); //do not grab head from here since it's locked (i.e. = 0x1)

  //LOCK is still needed as different threads will remove items...
  do {  
    val=(BinItem_rcr *)0x1;       
    val=(BinItem_rcr *)LOCKXCHG((unsigned INTPTR*)&(be->head), (unsigned INTPTR)val);
  } while(val==(BinItem_rcr*)0x1);     

  if (val==NULL) {
    if (((INTPTR)task)&PARENTBIN) {
      be->head=val;
      return READY;
    }

    BinItem_rcr * b=(BinItem_rcr*)rcr_createWriteBinItem( T );
    WriteBinItem_rcr * td = (WriteBinItem_rcr*)b;
    b->total=1;
    b->status=READY;
    
    //common to both types
    td->task=task;
    td->bitindexrd=td->bitindexwr=ONEVAL<<index;
    be->tail=b;
    BARRIER();//do tail before head
    //release lock
    be->head=b;
    enqueuerecord(rcrrec, key, b);
    return READY;
  }
  BARRIER();//read head before tail
  BinItem_rcr *bintail=be->tail;
  bitvt rdmask=0,wrmask=0;
  int status=NOTREADY;

  WriteBinItem_rcr *b;
  if (ISWRITEBIN(bintail->type)) {
    WriteBinItem_rcr * td = (WriteBinItem_rcr *)bintail;
    //last one is to check for SESE blocks in a while loop.
    if(unlikely(td->task == task)) {

      bitvt bit=ONEVAL<<index;
      if (!(bit & td->bitindexwr)) {
	td->bitindexwr|=bit;
	td->bitindexrd|=bit;
	be->head=val;
	if (mode) {
	  while(bintail->status!=READY) {
	    BARRIER();
	  }
	  return READY;
	} else {
	  return bintail->status;
	}
      } else {
	be->head=val;
	return READY;
      }
    }
    b=rcr_createWriteBinItem( T );
  } else {
    TraverserData * td = &((ReadBinItem_rcr *)bintail)->array[((ReadBinItem_rcr *)bintail)->index - 1];
    b=rcr_createWriteBinItem( T );
    if(unlikely(td->task == task)) {
      //if it matches, then we remove it and the code below will upgrade it to a write.
      ((ReadBinItem_rcr *)bintail)->index--;
      atomic_dec(&bintail->total);
      rdmask=td->bitindex;
      if (bintail->status!=READY)
	wrmask=rdmask;
      status=SPECNOTREADY;
      {
	//yank the old one out of the retire list and put us in place
	int oldindex=__builtin_ctz(rdmask);
	struct rcrRecord * oldrec=&rcrrec[oldindex-index];
	reenqueuerecord(oldrec, key, bintail, (BinItem_rcr *) b);
      }
    }
  }

  b->item.total=1;
  b->task=task;

  bitvt bit=ONEVAL<<index;
  if (wrmask&bit) {
    //count already includes this
    status=SPECREADY;
  }
  b->bitindexwr=bit|wrmask;
  b->bitindexrd=bit|rdmask;
  b->item.status=status&READYMASK;
  bintail->next=(BinItem_rcr*)b;
  be->tail=(BinItem_rcr*)b;
  MBARRIER(); //need to make sure that the read below doesn't pass the write above
  if (bintail->status==READY&&bintail->total==0) {
    //we may have to set write as ready
    while(1) {
      if (val==((BinItem_rcr *)b)) {
	if (((INTPTR)task)&PARENTBIN) {
	  //pull b from bin
	  be->head=NULL;
	  return READY;
	}
	b->item.status=READY;
	be->head=val;
	if (status&SPEC) {
	  return READY;
	} else {
	  enqueuerecord(rcrrec, key, (BinItem_rcr *) b);
	  return READY;
	}
      } else if (val->total!=0) {
	break;
      }
      //TODO: WHEN WE DO POOLALLOC, WE LEAK NODES HERE AND ACCESS THEM W/O LOCKING BIN...
      val=val->next;
    }
  }

  //RELEASE LOCK
  be->head=val;
  if (mode) {
    while(b->item.status==NOTREADY) {
      BARRIER();
    }
    if (!(status&SPEC))
      enqueuerecord(rcrrec, key, (BinItem_rcr *) b);
    return status&READY;
  } else {
    if (!(status&SPEC))
      enqueuerecord(rcrrec, key, (BinItem_rcr *) b);
    return status&READY;
  }
}

inline int rcr_BREADBINCASE(HashStructure *T, int key, SESEcommon *task, struct rcrRecord *rcrrec, int index, int mode) {
  BinItem_rcr * val;
  BinElement_rcr * be = &(T->array[key]);
  
  //LOCK is still needed as different threads will remove items...
  do {  
    val=(BinItem_rcr *)0x1;       
    val=(BinItem_rcr *)LOCKXCHG((unsigned INTPTR*)&(be->head), (unsigned INTPTR)val);
  } while(val==(BinItem_rcr*)0x1);     
  
  if (val==NULL) {
    if (((INTPTR)task)&PARENTBIN) {
      be->head=val;
      return READY;
    }

    BinItem_rcr * b=(BinItem_rcr*)rcr_createReadBinItem( T );
    ReadBinItem_rcr* readbin=(ReadBinItem_rcr*)b;
    TraverserData * td = &(readbin->array[readbin->index++]);
    b->total=1;
    b->status=READY;
    
    //common to both types
    td->task=task;
    td->bitindex=ONEVAL<<index;
    be->tail=b;
    
    //release lock
    be->head=b;
    enqueuerecord(rcrrec, key, b);    
    return READY;
  }


  BinItem_rcr * bintail=be->tail;
  
  //check if already added item or not.
  if (ISWRITEBIN(bintail->type)) {
    WriteBinItem_rcr * td = (WriteBinItem_rcr *)bintail;
    if(unlikely(td->task==task)) {
      //RELEASE LOCK
      bitvt bit=ONEVAL<<index;
      int status=bintail->status;
      if (!(td->bitindexrd & bit)) {
	td->bitindexrd|=bit;
	td->bitindexwr|=bit;
      } else 
	status=READY;
      be->head=val;
      if (mode) {
	while(bintail->status!=READY) {
	  BARRIER();
	}
	return READY;
      } else {
	return status;
      }
    }
    rcr_TAILWRITECASE(T, val, bintail, key, task, rcrrec, index);
    if (mode) {
      struct BinItem_rcr * bt=be->tail;
      while(bt->status!=READY) {
	BARRIER();
      }
      return READY;
    } else {
      return NOTREADY;
    }
  } else {
    TraverserData * td = &((ReadBinItem_rcr *)bintail)->array[((ReadBinItem_rcr *)bintail)->index - 1];
    if (unlikely(td->task==task)) {
      //RELEASE LOCK
      bitvt bit=ONEVAL<<index;
      int status=bintail->status;
      if (!(td->bitindex & bit)) {
	td->bitindex|=bit;
      } else 
	status=READY;
      be->head=val;
      if (mode) {
	while(bintail->status!=READY) {
	  BARRIER();
	}
      }
      return status;
    }

    if ((((INTPTR)task)&PARENTBIN)&&(bintail->status==READY)) {
      be->head=val;
      return READY;
    }

    int stat=rcr_TAILREADCASE(T, val, bintail, key, task, rcrrec, index);
    if (mode) {
      struct BinItem_rcr * bt=be->tail;
      while(bt->status!=READY) {
	BARRIER();
      }
      return READY;
    } else {
      return stat;
    }
  }
}


int rcr_WRITEBINCASE(HashStructure *T, int key, SESEcommon *task, struct rcrRecord *rcrrec, int index) {
  return rcr_BWRITEBINCASE(T, key, task, rcrrec, index, 0);
}
int rcr_READBINCASE(HashStructure *T, int key, SESEcommon * task, struct rcrRecord *rcrrec, int index) {
  return rcr_BREADBINCASE(T, key, task, rcrrec, index, 0);
}

int rcr_WTWRITEBINCASE(HashStructure *T, int key, SESEcommon *task, struct rcrRecord *rcrrec, int index) {
  return rcr_BWRITEBINCASE(T, key, task, rcrrec, index, 1);
}

int rcr_WTREADBINCASE(HashStructure *T, int key, SESEcommon * task, struct rcrRecord *rcrrec, int index) {
  return rcr_BREADBINCASE(T, key, task, rcrrec, index, 1);
}

 int rcr_TAILREADCASE(HashStructure *T, BinItem_rcr *val, BinItem_rcr *bintail, int key, SESEcommon * task, struct rcrRecord * rcrrec, int index) {
  ReadBinItem_rcr * readbintail=(ReadBinItem_rcr*)T->array[key].tail;
  int status, retval;
  TraverserData *td;
  if (readbintail->item.status==READY) {
    status=READY;
    retval=READY;
  } else {
    status=NOTREADY;
    retval=NOTREADY;
  }

  if (readbintail->index==RNUMREAD) { // create new read group
    ReadBinItem_rcr* rb=rcr_createReadBinItem( T );
    td = &rb->array[rb->index++];
    td->task=task;
    td->bitindex=ONEVAL<<index;
    rb->item.total=1;
    rb->item.status=status;
    T->array[key].tail->next=(BinItem_rcr*)rb;
    T->array[key].tail=(BinItem_rcr*)rb;
    enqueuerecord(rcrrec, key, (BinItem_rcr *) rb);
  } else { // group into old tail
    td = &readbintail->array[readbintail->index];
    td->task=task;
    td->bitindex=ONEVAL<<index;
    BARRIER();//ordering is to prevent retiring task from trashing us...
    readbintail->index++;
    atomic_inc(&readbintail->item.total);
    enqueuerecord(rcrrec, key, (BinItem_rcr *) readbintail);
  }



  T->array[key].head=val;//released lock
  return retval;
}

void rcr_TAILWRITECASE(HashStructure *T, BinItem_rcr *val, BinItem_rcr *bintail, int key, SESEcommon * task, struct rcrRecord *rcrrec, int index) {
  ReadBinItem_rcr* rb=rcr_createReadBinItem( T );
  TraverserData * td = &(rb->array[rb->index++]);
  rb->item.total=1;
  rb->item.status=NOTREADY;

  td->task=task;
  td->bitindex=ONEVAL<<index;
  enqueuerecord(rcrrec, key, (BinItem_rcr *) rb);

  T->array[key].tail->next=(BinItem_rcr*)rb;
  T->array[key].tail=(BinItem_rcr*)rb;
  T->array[key].head=val;//released lock
}

void rcr_RETIREHASHTABLE(HashStructure *T, SESEcommon *task, int key, BinItem_rcr *b) {
  atomic_dec(&b->total);

  //Need to clear ourself out of the read bin so that we aren't resolved after being freed
  if(ISREADBIN(b->type)) {
    //Have to clear our entry out of bin if we retired early

    if (b->status==NOTREADY) {
      ReadBinItem_rcr* rptr=(ReadBinItem_rcr*)b;
      BinElement_rcr * be = &(T->array[key]);
      int i;
      // CHECK FIRST IF next is nonnull to guarantee that b.total cannot change
      BinItem_rcr * val=(BinItem_rcr *)0x1;
      do {
	val=(BinItem_rcr*)LOCKXCHG((unsigned INTPTR*)&(be->head), (unsigned INTPTR)val);
      } while(val==(BinItem_rcr*)0x1);
      for (i=0;i<rptr->index;i++) {
	TraverserData * td=&rptr->array[i];
	if (task==td->task) {
	  //remove item from bin...
	  td->task=NULL;
	  break;
	}
      }      
      be->head=val;
    }
    if (b->next==NULL || b->total>0) {
      //need to remove ourself to avoid writecombining problems
      ReadBinItem_rcr* rptr=(ReadBinItem_rcr*)b;
      TraverserData * td=&rptr->array[rptr->index-1];
      if (td->task==task)
	td->task=NULL;
      return;
    }
  }
    
  
  //We either have a write bin or we are at the end of a read bin
  BinElement_rcr * be = &(T->array[key]);
  {
    // CHECK FIRST IF next is nonnull to guarantee that b.total cannot change
    BinItem_rcr * val=(BinItem_rcr *)0x1;
    do {
      val=(BinItem_rcr*)LOCKXCHG((unsigned INTPTR*)&(be->head), (unsigned INTPTR)val);
    } while(val==(BinItem_rcr*)0x1);

    // at this point have locked bin
    BinItem_rcr *ptr=val;
    BinItem_rcr *next;
    int haveread=FALSE;
    int i;
    while (ptr!=NULL) {
      next = ptr->next;
      if (ISREADBIN(ptr->type)) {
	if (ptr->status==NOTREADY) {
	  ReadBinItem_rcr* rptr=(ReadBinItem_rcr*)ptr;
	  for (i=0;i<rptr->index;i++) {
	    TraverserData * td=&rptr->array[i];
	    SESEcommon *record=td->task;
	    if (((INTPTR)record)&PARENTBIN) {
	      //parents go immediately
	      atomic_dec(&rptr->item.total);
	      record=(SESEcommon *)(((INTPTR)record)&~1ULL);
	    }
	    if (record!=NULL)
	      RESOLVE(record, td->bitindex);
          }
	  ptr->status=READY;
        }
        if (ptr->next==NULL) {
          break;
        }
        if (ptr->total!=0) {
          haveread=TRUE;
        } else if (ptr==val) {
          val=val->next;

          // THE 3 POOLFREE's IN THIS FUNCTION ARE TOO EARLY:
          // OTHER FUNCTIONS IN THIS COMPILATION UNIT LOCK THE BIN ELEMENT
          // BUT MAY TOUCH THE STATUS FIELDS OF BIN ITEMS AFTER RELEASING
          // THE LOCK, WE HAVE TO MAKE SOME CAREFUL CHANGES TO ALLOW THE
          // POOLFREE's TO WORK!

          //poolfreeinto( T->memPoolRead, ptr );
        }
      } else if (ptr->total==0) {
	//skip past retired item
	if (ptr==val) {
	  val=val->next;
          //poolfreeinto( T->memPoolWrite, ptr );
        }
      } else {
	//write bin case
        if (haveread)
          break;
	if(ptr->status==NOTREADY) {
	  WriteBinItem_rcr* wptr=(WriteBinItem_rcr*)ptr;
	  RESOLVE((SESEcommon *)(((INTPTR)wptr->task)&~1ULL), wptr->bitindexwr);
	  ptr->status=READY;
	  if(((INTPTR)wptr->task)&PARENTBIN) {
	    val=val->next;
            //poolfreeinto( T->memPoolWrite, ptr );
	  } else
	    break;
	} else
	  break;
      }
      ptr = next;
    }
    be->head=val; // release lock
  }
}
 
void RESOLVE(SESEcommon *record, bitvt mask) {
  int index=-1;
  struct rcrRecord * array=(struct rcrRecord *)(((char *)record)+record->offsetToParamRecords);
  while(mask!=0) {
    int shift=__builtin_ctzll(mask)+1;
    index+=shift;
    if (atomic_sub_and_test(1,&array[index].count)) {
      if(unlikely(record->classID<0)) {
	//parent stall...clear it
	psem_give_tag(record->parentsStallSem, ((SESEstall *)record)->tag);
	//mark the record unused
	BARRIER();
	record->rcrstatus=0;
#ifndef OOO_DISABLE_TASKMEMPOOL
	RELEASE_REFERENCE_TO(record);
#endif
      } else {
	int flag=LOCKXCHG32(&array[index].flag,0);
	if (flag) {
	  if(atomic_sub_and_test(1, &(record->unresolvedDependencies))) 
	    workScheduleSubmit((void *)record);
	}
      }
    }
    mask=mask>>shift;
  }
}
