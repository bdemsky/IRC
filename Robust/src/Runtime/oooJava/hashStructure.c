#include "hashStructure.h"
#include "WaitingQueue.h"
#include "mlp_lock.h"
#include "tm.h"

//NOTE: this is only temporary (for testing) and will be removed in favor of thread local variables
//It's basically an array of hashStructures so we can simulate what would happen in a many-threaded version
HashStructure ** allHashStructures;

//NOTE: only temporary
void createMasterHashTableArray(int maxSize){
  int i;
  allHashTables = (HashTable**) malloc(sizeof(Hashtable) * maxSize);

  for(i = 0; i < maxSize; i++) {
    allHashTables[i] = createHashtable();
  }
}

HashStructure* createHashtable(int sizeofWaitingQueue){
  int i=0;
  HashStructure* newTable=(HashStructure*)RUNMALLOC(sizeof(HashStructure));
  for(i=0;i<NUMBINS;i++){
    newTable->array[i].head=NULL;
    newTable->array[i].tail=NULL;
  }

  newTable->waitingQueue=mallocWaitingQueue(sizeofWaitingQueue);
  return newTable;
}


WriteBinItem_rcr* createWriteBinItem(){
  WriteBinItem_rcr* binitem=(WriteBinItem_rcr*)RUNMALLOC(sizeof(WriteBinItem_rcr));
  binitem->item.type=WRITEBIN;
  return binitem;
}

ReadBinItem_rcr* createReadBinItem(){
  ReadBinItem_rcr* binitem=(ReadBinItem_rcr*)RUNMALLOC(sizeof(ReadBinItem_rcr));
  binitem->tail_Index=0;
  binitem->head_Index=0;
  binitem->item.type=READBIN;
  return binitem;
}

int isReadBinItem(BinItem_rcr* b){
  if(b->type==READBIN){
    return TRUE;
  }else{
    return FALSE;
  }
}

int isWriteBinItem(BinItem_rcr* b){
  if(b->type==WRITEBIN){
    return TRUE;
  }else{
    return FALSE;
  }
}

inline int generateKey(void * ptr){
  return (((struct genericObjectStruct *) ptr)->oid)&H_MASK;
}

//TODO handle logic for waiting Queues separately
//TODO pass in task to traverser
int ADDTABLEITEM(HashStructure* table, void * ptr, int type, int traverserID, SESEcommon *task, void * heaproot){
  //Removed lock since it's not needed if only 1 thread hits the hashtable.
  BinItem_rcr * val = table->array[key]->bin->head;
  int key=generateKey(ptr);

  if (val==NULL) {
    return EMPTYBINCASE(table, table->array[key], ptr, type, traverserID, task, heaproot);
  } else {
    //else create item
    if (type == WRITEEFFECT) {
      return WRITEBINCASE(table, ptr, val, key, traverserID, task, heaproot);
    } else if (type == READEFFECT) {
      return READBINCASE(table, ptr, val, key, traverserID, task, heaproot);
    }
  }
}

int EMPTYBINCASE(HashStructure *T, BinElement_rcr* be, void *ptr, int type, int traverserId, SESEcommon * task, void *heaproot){
  BinItem_rcr* b;
  TraverserData * td;
  if (type == WRITEEFFECT) {
    b=(BinItem_rcr*)createWriteBinItem();
    td = &((WriteBinItem_rcr*)b)->val;
  }
  else if (type == READEFFECT) {
    b=(BinItem_rcr*)createReadBinItem();
    ReadBinItem_rcr* readbin=(ReadBinItem_rcr*)b;
    td = &(readbin->array[readbin->tail_Index++]);
  }
  b->total=1;
  b->type= type;
  b->status = READY;

  //common to both types
  td->binitem = b;
  td->hashtable=T;
  td->resumePtr = ptr;
  td->task= task;
  td->traverserID = traverserId;
  td->heaproot = heaproot;

  be->tail=b;
  be->head=b;

  return READY;
}


int WRITEBINCASE(HashStructure *T, void *ptr, int key, int traverserID, SESEcommon *task, void *heaproot){
  //chain of bins exists => tail is valid
  //if there is something in front of us, then we are not ready
  int status=NOTREADY;
  BinElement_rcr* be= &(T->array[key]); //do not grab head from here since it's locked (i.e. = 0x1)

  if(be->tail->type == WRITEBIN) {
    TraverserData * td = &(((WriteBinItem_rcr *)be->tail)->val);
    //last one is to check for SESE blocks in a while loop.
    if(unlikely(td->resumePtr == ptr) && td->traverserID == traverserID && td->task == task) {
      return be->tail->status;
    }
  } else if(be->tail->type == READBIN) {
    TraverserData * td = &((ReadBinItem_rcr *)be->tail)->array[((ReadBinItem_rcr *)be->tail)->tail_Index - 1];
    if(unlikely(td->resumePtr == ptr) && td->traverserID == traverserID) {
      //if it matches, then we remove it and the code below will upgrade it to a write.
      ((ReadBinItem_rcr *)be->tail)->tail_Index--;
      be->tail->total--;
    }
  }

  WriteBinItem_rcr *b=createWriteBinItem();
  TraverserData * td = &b->val;
  b->item.total=1;

  //fillout traverserData
  //Note: this list could be smaller in the future, for now I'm just including all the info I may need.
  td->binitem = b;
  td->hashtable=T;
  td->resumePtr = ptr;
  td->task= task;
  td->traverserID = traverserID;
  td->heaproot = heaproot;

  b->item.status=status;
  be->tail->next=(BinItem_rcr*)b;
  be->tail=(BinItem_rcr*)b;
  return status;
}

int READBINCASE(HashStructure *T, void *ptr, int key, int traverserID, SESEcommon * task, void *heaproot){
  BinElement_rcr * be = &(T->array[key]);
  BinItem_rcr * bintail=be->tail;
  //check if already added item or not.
  if(bintail->type == WRITEBIN) {
    TraverserData * td = &(((WriteBinItem_rcr *)bintail)->val);
    if(unlikely(td->resumePtr == ptr) && td->traverserID == traverserID) {
      return bintail->status;
    }
  }
  else if(bintail->type == READEFFECT) {
    TraverserData * td = &((ReadBinItem_rcr *)bintail)->array[((ReadBinItem_rcr *)bintail)->tail_Index - 1];
    if(unlikely(td->resumePtr == ptr) && td->traverserID == traverserID) {
      return bintail->status;
    }
  }

  if (isReadBinItem(bintail)) {
    return TAILREADCASE(T, ptr, bintail, key, traverserID, task, heaproot);
  } else if (!isReadBinItem(bintail)) {
    TAILWRITECASE(T, ptr, bintail, key, traverserID, task, heaproot);
    return NOTREADY;
  }
}

int TAILREADCASE(HashStructure *T, void * ptr, BinItem_rcr *bintail, int key, int traverserID, SESEcommon * task, void *heaproot){
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

  if (readbintail->tail_Index==NUMREAD) { // create new read group
    ReadBinItem_rcr* rb=createReadBinItem();
    td = &rb->array[rb->tail_Index++];

    rb->item.total=1;
    rb->item.status=status;
    T->array[key].tail->next=(BinItem_rcr*)rb;
    T->array[key].tail=(BinItem_rcr*)rb;
  } else { // group into old tail
    td = &readbintail->array[readbintail->tail_Index++];
    atomic_inc(&readbintail->item.total);
    //printf("grouping with %d\n",readbintail->index);
  }

  td->binitem = bintail;
  td->hashtable=T;
  td->resumePtr = ptr;
  td->task= task;
  td->traverserID = traverserID;
  td->heaproot = heaproot;

  return retval;
}

void TAILWRITECASE(HashStructure *T, void *ptr, BinItem_rcr *bintail, int key, int traverserID, SESEcommon * task, void *heaproot){
  //  WriteBinItem* wb=createWriteBinItem();
  //wb->val=r;
  //wb->item.total=1;//safe because item could not have started
  //wb->item.status=NOTREADY;
  ReadBinItem_rcr* rb=createReadBinItem();
  TraverserData * td = &(rb->array[rb->tail_Index++]);
  rb->item.total=1;
  rb->item.status=NOTREADY;

  td->binitem = rb;
  td->hashtable=T;
  td->resumePtr = ptr;
  td->task= task;
  td->traverserID = traverserID;
  td->heaproot = heaproot;

  T->array[key].tail->next=(BinItem_rcr*)rb;
  T->array[key].tail=(BinItem_rcr*)rb;
}

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
