#include "hashStructure.h"
//#include "WaitingQueue.h"
#include "mlp_lock.h"
#include "mem.h"
#include "classdefs.h"
#include "rcr_runtime.h"

//NOTE: this is only temporary (for testing) and will be removed in favor of thread local variables
//It's basically an array of hashStructures so we can simulate what would happen in a many-threaded version
HashStructure ** allHashStructures;
#define ISWRITEBIN(x) (x&BINMASK)
#define ISREADBIN(x) (!(x&BINMASK))
//#define POPCOUNT(x) __builtin_popcountll(x)
//__builtin_popcountll


//NOTE: only temporary
void rcr_createMasterHashTableArray(int maxSize){
}

HashStructure* rcr_createHashtable(int sizeofWaitingQueue){
  int i=0;
  HashStructure* newTable=(HashStructure*)RUNMALLOC(sizeof(HashStructure));
  for(i=0;i<RNUMBINS;i++){
    newTable->array[i].head=NULL;
    newTable->array[i].tail=NULL;
  }

  return newTable;
}

WriteBinItem_rcr* rcr_createWriteBinItem(){
  WriteBinItem_rcr* binitem=(WriteBinItem_rcr*)RUNMALLOC(sizeof(WriteBinItem_rcr));
  binitem->item.type=WRITEBIN;
  return binitem;
}

ReadBinItem_rcr* rcr_createReadBinItem(){
  ReadBinItem_rcr* binitem=(ReadBinItem_rcr*)RUNMALLOC(sizeof(ReadBinItem_rcr));
  binitem->index=0;
  binitem->item.type=READBIN;
  return binitem;
}

inline int rcr_generateKey(void * ptr){
  return (((struct ___Object___ *) ptr)->oid)&RH_MASK;
}

//consider SPEC flag
int rcr_WRITEBINCASE(HashStructure *T, void *ptr, SESEcommon *task, int index) {
  //chain of bins exists => tail is valid
  //if there is something in front of us, then we are not ready
  BinItem_rcr * val;
  int key=rcr_generateKey(ptr);
  BinElement_rcr* be= &(T->array[key]); //do not grab head from here since it's locked (i.e. = 0x1)

  //LOCK is still needed as different threads will remove items...
  do {  
    val=(BinItem_rcr *)0x1;       
    val=(BinItem_rcr *)LOCKXCHG((unsigned INTPTR*)&(be->head), (unsigned INTPTR)val);
  } while(val==(BinItem_rcr*)0x1);     

  if (val==NULL) {
    BinItem_rcr * b=(BinItem_rcr*)rcr_createWriteBinItem();
    WriteBinItem_rcr * td = (WriteBinItem_rcr*)b;
    b->total=1;
    b->status=READY;
    
    //common to both types
    td->task=task;
    td->bitindexrd=td->bitindexwr=1<<index;
    be->tail=b;
    
    //release lock
    be->head=b;
    return READY;
  }

  BinItem_rcr *bintail=be->tail;
  bitvt rdmask=0,wrmask=0;
  int status=NOTREADY;

  if (ISWRITEBIN(bintail->type)) {
    WriteBinItem_rcr * td = (WriteBinItem_rcr *)bintail;
    //last one is to check for SESE blocks in a while loop.
    if(unlikely(td->task == task)) {
      be->head=val;
      bitvt bit=1<<index;
      if (!(bit & td->bitindexwr)) {
	td->bitindexwr|=bit;
	td->bitindexrd|=bit;
	return (bintail->status==READY)?SPECREADY:SPECNOTREADY;
      } else
	return SPECREADY;
    }
  } else {
    TraverserData * td = &((ReadBinItem_rcr *)bintail)->array[((ReadBinItem_rcr *)bintail)->index - 1];
    if(unlikely(td->task == task)) {
      //if it matches, then we remove it and the code below will upgrade it to a write.
      ((ReadBinItem_rcr *)bintail)->index--;
      atomic_dec(&bintail->total);
      rdmask=td->bitindex;
      if (bintail->status!=READY)
	wrmask=rdmask;
      status=SPECNOTREADY;
    }
  }

  WriteBinItem_rcr *b=rcr_createWriteBinItem();
  b->item.total=1;
  b->task=task;

  bitvt bit=1<<index;
  if (wrmask&bit) {
    //count already includes this
    status=SPECREADY;
  }
  b->bitindexwr=bit|wrmask;
  b->bitindexrd=bit|rdmask;
  b->item.status=status;
  bintail->next=(BinItem_rcr*)b;
  be->tail=(BinItem_rcr*)b;
  
  if (bintail->status==READY&&bintail->total==0) {
    //we may have to set write as ready
    while(val->total==0) {
      if (val==((BinItem_rcr *)b)) {
	b->item.status=READY;
	be->head=val;
	if (status&SPEC)
	  return SPECREADY;
	else
	  return READY;
      }
      val=val->next;
    }
  }

  //RELEASE LOCK
  be->head=val;
  return status;
}

int rcr_READBINCASE(HashStructure *T, void *ptr, SESEcommon * task, int index) {
  BinItem_rcr * val;
  int key=rcr_generateKey(ptr);
  BinElement_rcr * be = &(T->array[key]);

  //LOCK is still needed as different threads will remove items...
  do {  
    val=(BinItem_rcr *)0x1;       
    val=(BinItem_rcr *)LOCKXCHG((unsigned INTPTR*)&(be->head), (unsigned INTPTR)val);
  } while(val==(BinItem_rcr*)0x1);     

  if (val==NULL) {
    BinItem_rcr * b=(BinItem_rcr*)rcr_createReadBinItem();
    ReadBinItem_rcr* readbin=(ReadBinItem_rcr*)b;
    TraverserData * td = &(readbin->array[readbin->index++]);
    b->total=1;
    b->status=READY;
    
    //common to both types
    td->task=task;
    td->bitindex=1<<index;
    be->tail=b;
    
    //release lock
    be->head=b;
    
    return READY;
  }


  BinItem_rcr * bintail=be->tail;

  //check if already added item or not.
  if (ISWRITEBIN(bintail->type)) {
    WriteBinItem_rcr * td = (WriteBinItem_rcr *)bintail;
    if(unlikely(td->task==task)) {
      //RELEASE LOCK
      bitvt bit=1<<index;
      int status=bintail->status;
      if (!(td->bitindexrd & bit)) {
	td->bitindexrd|=bit;
	td->bitindexwr|=bit;
	status=status|SPEC;
      } else 
	status=SPECREADY;
      be->head=val;
      return status;
    }
  } else {
    TraverserData * td = &((ReadBinItem_rcr *)bintail)->array[((ReadBinItem_rcr *)bintail)->index - 1];
    if (unlikely(td->task==task)) {
      //RELEASE LOCK
      bitvt bit=1<<index;
      int status=bintail->status;
      if (!(td->bitindex & bit)) {
	td->bitindex|=bit;
	status=status|SPEC;
      } else 
	status=SPECREADY;
      be->head=val;
      return status;
    }
  }

  if (ISREADBIN(bintail->type)) {
    return rcr_TAILREADCASE(T, ptr, val, bintail, key, task, index);
  } else {
    rcr_TAILWRITECASE(T, ptr, val, bintail, key, task, index);
    return NOTREADY;
  }
}


int rcr_TAILREADCASE(HashStructure *T, void * ptr, BinItem_rcr *val, BinItem_rcr *bintail, int key, SESEcommon * task, int index) {
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
    ReadBinItem_rcr* rb=rcr_createReadBinItem();
    td = &rb->array[rb->index++];

    rb->item.total=1;
    rb->item.status=status;
    T->array[key].tail->next=(BinItem_rcr*)rb;
    T->array[key].tail=(BinItem_rcr*)rb;
  } else { // group into old tail
    td = &readbintail->array[readbintail->index++];
    atomic_inc(&readbintail->item.total);
  }

  td->task=task;
  td->bitindex=1<<index;

  T->array[key].head=val;//released lock
  return retval;
}

void rcr_TAILWRITECASE(HashStructure *T, void *ptr, BinItem_rcr *val, BinItem_rcr *bintail, int key, SESEcommon * task, int index) {
  ReadBinItem_rcr* rb=rcr_createReadBinItem();
  TraverserData * td = &(rb->array[rb->index++]);
  rb->item.total=1;
  rb->item.status=NOTREADY;

  td->task=task;
  td->bitindex=1<<index;

  T->array[key].tail->next=(BinItem_rcr*)rb;
  T->array[key].tail=(BinItem_rcr*)rb;
  T->array[key].head=val;//released lock
}

void rcr_RETIREHASHTABLE(HashStructure *T, SESEcommon *task, int key) {
  BinElement_rcr * be = &(T->array[key]);
  BinItem_rcr *b=be->head;

  if(ISREADBIN(READBIN)) {
    atomic_dec(&b->total);
    if (b->next==NULL || b->total>0) {
      return;
    }
  }
  //We either have a write bin or we are at the end of a read bin

  {
    // CHECK FIRST IF next is nonnull to guarantee that b.total cannot change
    BinItem_rcr * val=(BinItem_rcr *)0x1;
    do {
      val=(BinItem_rcr*)LOCKXCHG((unsigned INTPTR*)&(be->head), (unsigned INTPTR)val);
    } while(val==(BinItem_rcr*)0x1);
    
    // at this point have locked bin
    BinItem_rcr *ptr=val;
    int haveread=FALSE;
    int i;
    while (ptr!=NULL) {
      if (ISREADBIN(ptr->type)) {
	if (ptr->status==NOTREADY) {
	  ReadBinItem_rcr* rptr=(ReadBinItem_rcr*)ptr;
	  for (i=0;i<rptr->index;i++) {
	    TraverserData * td=&rptr->array[i];
	    if (task==td->task) {
	      RESOLVE(td->task, td->bitindex);
	      if (((INTPTR)rptr->array[i].task)&PARENTBIN) {
		//parents go immediately
		atomic_dec(&rptr->item.total);
	      }
	      break;
	    }
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
        }
      } else {
	//write bin case
        if (haveread)
          break;
	if(ptr->status==NOTREADY) {
	  WriteBinItem_rcr* wptr=(WriteBinItem_rcr*)ptr;
	  RESOLVE(wptr->task, wptr->bitindexwr);
	  ptr->status=READY;
	  if(((INTPTR)wptr->task)&PARENTBIN) {
	    val=val->next;
	  } else
	    break;
	} else { // write bin is already resolved
	  val=val->next;
	}
      }
      ptr=ptr->next;
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
      int flag=LOCKXCHG32(&array[index].flag,0);
      if (flag) {
	if(atomic_sub_and_test(1, &(record->unresolvedDependencies))) 
	  workScheduleSubmit((void *)record);
      }
    }
    mask=mask>>shift;
  }
}
