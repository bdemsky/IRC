#include "hashStructure.h"
//#include "WaitingQueue.h"
#include "mlp_lock.h"
#include "mem.h"

//NOTE: this is only temporary (for testing) and will be removed in favor of thread local variables
//It's basically an array of hashStructures so we can simulate what would happen in a many-threaded version
HashStructure ** allHashStructures;

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

  //  newTable->waitingQueue=mallocWaitingQueue(sizeofWaitingQueue);
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

int rcr_isReadBinItem(BinItem_rcr* b){
  return b->type==READBIN;
}

int rcr_isWriteBinItem(BinItem_rcr* b){
  return b->type==WRITEBIN;
}

inline int rcr_generateKey(void * ptr){
  return (((struct genericObjectStruct *) ptr)->oid)&RH_MASK;
}

int rcr_WRITEBINCASE(HashStructure *T, void *ptr, int traverserID, SESEcommon *task, void *heaproot) {
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
    TraverserData * td = &((WriteBinItem_rcr*)b)->val;
    b->total=1;
    b->status=READY;
    
    //common to both types
    td->binitem = b;
    td->hashtable=T;
    td->resumePtr = ptr;
    td->task= task;
    td->traverserID = traverserID;
    td->heaproot = heaproot;
    be->tail=b;
    
    //release lock
    be->head=b;
    return READY;
  }

  int status=NOTREADY;
  BinItem_rcr *bintail=be->tail;

  if (bintail->type == WRITEBIN) {
    TraverserData * td = &(((WriteBinItem_rcr *)bintail)->val);
    //last one is to check for SESE blocks in a while loop.
    if(unlikely(td->task == task)) {
      be->head=val;
      //return ready...this constraint is already handled
      return READY;
    }
  } else if (bintail->type == READBIN) {
    TraverserData * td = &((ReadBinItem_rcr *)bintail)->array[((ReadBinItem_rcr *)bintail)->index - 1];
    if(unlikely(td->task == task)) {
      //if it matches, then we remove it and the code below will upgrade it to a write.
      ((ReadBinItem_rcr *)bintail)->index--;
      bintail->total--;
    }
  }

  WriteBinItem_rcr *b=rcr_createWriteBinItem();
  TraverserData * td = &b->val;
  b->item.total=1;

  //fillout traverserData
  //Note: this list could be smaller in the future, for now I'm just including all the info I may need.
  td->binitem = (BinItem_rcr*)b;
  td->hashtable=T;
  td->resumePtr = ptr;
  td->task= task;
  td->traverserID = traverserID;
  td->heaproot = heaproot;

  b->item.status=status;
  bintail->next=(BinItem_rcr*)b;
  be->tail=(BinItem_rcr*)b;
  //RELEASE LOCK
  be->head=val;
  return status;
}

int rcr_READBINCASE(HashStructure *T, void *ptr, int traverserID, SESEcommon * task, void *heaproot) {
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
    b->status = READY;
    
    //common to both types
    td->binitem = b;
    td->hashtable=T;
    td->resumePtr = ptr;
    td->task= task;
    td->traverserID = traverserID;
    td->heaproot = heaproot;
    be->tail=b;
    
    //release lock
    be->head=b;
    
    return READY;
  }


  BinItem_rcr * bintail=be->tail;

  //check if already added item or not.
  if (bintail->type == WRITEBIN) {
    TraverserData * td = &(((WriteBinItem_rcr *)bintail)->val);
    if(unlikely(td->resumePtr == ptr) && td->traverserID == traverserID) {
      //RELEASE LOCK
      be->head=val;
      return bintail->status;
    }
  } else if (bintail->type == READEFFECT) {
    TraverserData * td = &((ReadBinItem_rcr *)bintail)->array[((ReadBinItem_rcr *)bintail)->index - 1];
    if (unlikely(td->resumePtr == ptr) && td->traverserID == traverserID) {
      //RELEASE LOCK
      be->head=val;
      return bintail->status;
    }
  }

  if (isReadBinItem(bintail)) {
    return rcr_TAILREADCASE(T, ptr, val, bintail, key, traverserID, task, heaproot);
  } else if (!isReadBinItem(bintail)) {
    rcr_TAILWRITECASE(T, ptr, val, bintail, key, traverserID, task, heaproot);
    return NOTREADY;
  }
}


int rcr_TAILREADCASE(HashStructure *T, void * ptr, BinItem_rcr *val, BinItem_rcr *bintail, int key, int traverserID, SESEcommon * task, void *heaproot) {
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
    //printf("grouping with %d\n",readbintail->index);
  }

  td->binitem = bintail;
  td->hashtable=T;
  td->resumePtr = ptr;
  td->task= task;
  td->traverserID = traverserID;
  td->heaproot = heaproot;

  T->array[key].head=val;//released lock
  return retval;
}

void rcr_TAILWRITECASE(HashStructure *T, void *ptr, BinItem_rcr *val, BinItem_rcr *bintail, int key, int traverserID, SESEcommon * task, void *heaproot) {
  ReadBinItem_rcr* rb=rcr_createReadBinItem();
  TraverserData * td = &(rb->array[rb->index++]);
  rb->item.total=1;
  rb->item.status=NOTREADY;

  td->binitem = (BinItem_rcr *) rb;
  td->hashtable=T;
  td->resumePtr = ptr;
  td->task= task;
  td->traverserID = traverserID;
  td->heaproot = heaproot;

  T->array[key].tail->next=(BinItem_rcr*)rb;
  T->array[key].tail=(BinItem_rcr*)rb;
  T->array[key].head=val;//released lock
}

//TODO write deletion/removal methods

RETIREHASHTABLE(HashStructure *T, SESECommon *task, int key) {
  BinItem_rcr *b=T->array[key].head;
  if(isFineRead(r)) {
    atomic_dec(&b->total);
  }
  if (isFineWrite(r) || (isFineRead(r) && b->next!=NULL && b->total==0)) {
    // CHECK FIRST IF next is nonnull to guarantee that b.total cannot change
    BinItem_rcr * val;
    do {  
      val=(BinItem_rcr*)0x1;
      val=(BinItem_rcr*)LOCKXCHG((unsigned INTPTR*)&(T->array[key]->head), (unsigned INTPTR)val);
    } while(val==(BinItem_rcr*)0x1);

    // at this point have locked bin
    BinItem_rcr *ptr=val;
    int haveread=FALSE;
    int i;
    while (ptr!=NULL) {
       if (isReadBinItem(ptr)) {
	ReadBinItem_rcr* rptr=(ReadBinItem_rcr*)ptr;
        if (rptr->item.status==NOTREADY) {
          for (i=0;i<rptr->index;i++) {	    
	    resolveDependencies(rptr->array[i]);
            if (isParent(rptr->array[i])) {
              //parents go immediately
              atomic_dec(&rptr->item.total);
              atomic_dec(&T->item.total);
            }
          }
        }
        rptr->item.status=READY; 
        if (rptr->item.next==NULL) {
          break;
        }
        if (rptr->item.total!=0) {
          haveread=TRUE; 
        } else if ((BinItem_rcr*)rptr==val) {
          val=val->next;
        }
      } else if(isWriteBinItem(ptr)) {
        if (haveread)  
          break;
	if(ptr->status==NOTREADY){
	  resolveDependencies(((WriteBinItem_rcr*)ptr)->val);
	  ptr->status=READY;
	  if(isParent(((WriteBinItem_rcr*)ptr)->val)){
	    atomic_dec(&T->item.total);
	    val=val->next;	  
	  }else
	    break;
	}else{ // write bin is already resolved
	  val=val->next;
	}
      } 
      ptr=ptr->next;
    }
    T->array[key]->head=val; // release lock
  }
}


/*
//Int will return success/fail. -1 indicates error (i.e. there's nothing there).
//0 = nothing removed, >0 something was removed
int REMOVETABLEITEM(HashStructure* table, void * ptr, int traverserID, SESEcommon *task, void * heaproot) {
  int key = generateKey(ptr);
  BinElement_rcr * bucket = table->array[key];

  if(bucket->head == NULL) {
    return -1;
    //This can occur if we try to remove something that's in the waitingQueue directly from the hashtable.
  }

  if(bucket->head->type == WRITEBIN) {
    TraverserData * td = &(((WriteBinItem_rcr *) head)->val);
    if(td->resumePtr == ptr && td->traverserID == traverserID && td->heaproot == heaproot) {
      BinItem_rcr * temp = bucket->head;
      bucket->head = bucket->head->next;
      free(temp); //TODO perhaps implement a linked list of free BinElements as was done in WaitingQueue

      //Handle items behind write item
      if(bucket->head == NULL) {
        bucket->tail == NULL;
        return 1;
      }

      int type = bucket->head->type;
      switch(bucket->head->type) {
        case WRITEBIN:
          bucket->head->status = READY;
          //TODO Decrement dependency count
          return 1;
        case WAITINGQUEUENOTE:
          int retval = removeFromWaitingQueue(table->waitingQueue, ((WaitingQueueNote *) bucket->head)->allocSiteID, traverserID);
          if(retval >0) {
            //we set both to NULL because the note should ALWAYS be the last item in the hashStructure.
             bucket->head = NULL;
             bucket->tail = NULL;
          }
          return retval;
        default:
          BinItem_rcr * temp = bucket->head;
          while(temp != NULL && temp->type == READBIN) {
            temp->status = READY;
            temp = temp->next;
            //TODO decrement dependency count
          }
          return 1;
      }
    } else {
      return 0;
    }
  }

  if(bucket->head->type == READBIN) {
    //TODO There's an issue here; bins are groups of items that may be enqueued by different ids.
    //I may have to search through them to find which one to remove but then there'd be a blank spot in an
    //otherwise clean array. It wouldn't make sense to just check the first one since reads are reorderable.
    //Nor does it make sense to lop off the reads since that may signal the next write to be ready even before it
    //really is ready.
  }

  if(bucket->head->type == WAITINGQUEUENOTE) {
    int retval = removeFromWaitingQueue(table->waitingQueue, ((WaitingQueueNote *) bucket->head)->allocSiteID, traverserID);
    if(retval >0) {
       bucket->head = NULL;
       bucket->tail = NULL;
    }
    return retval;
  }

  return -1;
}

*/
