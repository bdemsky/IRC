#include "hashStructure.h"
#include "tm.h"

//TODO since hashtables can be shared amongst traversers and there's a finite number,
//have a structure to keep track of all the hash tables for HRs.

Hashtable* createHashtable(){
  int i=0;
  Hashtable* newTable=(Hashtable*)RUNMALLOC(sizeof(Hashtable));
  for(i=0;i<NUMBINS;i++){
    newTable->array[i]=(BinElement*)RUNMALLOC(sizeof(BinElement));
    newTable->array[i]->head=NULL;
    newTable->array[i]->tail=NULL;
  }

  //Todo edit here to make this be pointed to a generated waitingQueue
  newTable->unresolvedQueue=NULL;
  return newTable;
}


WriteBinItem* createWriteBinItem(){
  WriteBinItem* binitem=(WriteBinItem*)RUNMALLOC(sizeof(WriteBinItem));
  binitem->item.type=WRITEBIN;
  return binitem;
}

ReadBinItem* createReadBinItem(){
  ReadBinItem* binitem=(ReadBinItem*)RUNMALLOC(sizeof(ReadBinItem));
  binitem->index=0;
  binitem->item.type=READBIN;
  return binitem;
}

int isReadBinItem(BinItem* b){
  if(b->type==READBIN){
    return TRUE;
  }else{
    return FALSE;
  }
}

int isWriteBinItem(BinItem* b){
  if(b->type==WRITEBIN){
    return TRUE;
  }else{
    return FALSE;
  }
}

inline int generateKey(void * ptr){
  return ((struct genericObjectStruct *) ptr)->oid&H_MASK;
}

//TODO handle logic for waiting Queues separately
//TODO pass in task to traverser
int ADDTABLEITEM(Hashtable* table, void * ptr, int type, int traverserID, SESEcommon *task, void * heaproot){
  BinItem * val;
  int key=generateKey(ptr);
  do {
    val=(BinItem*)0x1;
    BinElement* bin=table->array[key];
    val=(BinItem*)LOCKXCHG((unsigned INTPTR*) (&(bin->head)), (unsigned INTPTR)val);
  } while(val==(BinItem*)0x1);
  //at this point have locked bin
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

int EMPTYBINCASE(Hashtable *T, BinElement* be, void *ptr, int type, int traverserId, SESEcommon * task, void *heaproot) {
  BinItem* b;
  TraverserData * td;
  if (type == WRITEEFFECT) {
    b=(BinItem*)createWriteBinItem();
    td = &((WriteBinItem*)b)->val;
  }
  else if (type == READEFFECT) {
    b=(BinItem*)createReadBinItem();
    ReadBinItem* readbin=(ReadBinItem*)b;
    td = &(readbin->array[readbin->index++]);
  }
  b->total=1;
  b->type= type;
  b->status = READY;

  //common to both types
  td->binitem = b;
  td->hashtable=T;
  td->pointer = ptr;
  td->task= task;
  td->traverserID = traverserId;
  td->heaproot = heaproot;

  be->tail=b;
  be->head=b;//released lock

  return READY;
}


int WRITEBINCASE(Hashtable *T, void *ptr, BinItem *originalHead, int key, int traverserID, SESEcommon *task, void *heaproot) {
  //chain of bins exists => tail is valid
  //if there is something in front of us, then we are not ready
  int status=NOTREADY;
  BinElement* be=T->array[key]; //do not grab head from here since it's locked (i.e. = 0x1)

  if(be->tail->type == WRITEBIN) {
    TraverserData * td = &(((WriteBinItem *)be->tail)->val);
    if(unlikely(td->pointer == ptr) && td->traverserID == traverserID) {
      be->head = originalHead; //lock released
      return be->tail->status;
    }
  } else if(be->tail->type == READBIN) {
    TraverserData * td = &((ReadBinItem *)be->tail)->array[((ReadBinItem *)be->tail)->index - 1];
    if(unlikely(td->pointer == ptr) && td->traverserID == traverserID) {
      //if it matches, then we remove it and the code below will upgrade it to a write.
      ((ReadBinItem *)be->tail)->index--;
      be->tail->total--;
    }
  }

  WriteBinItem *b=createWriteBinItem();
  TraverserData * td = &b->val;
  b->item.total=1;

  //fillout traverserData
  //Note: this list could be smaller in the future, for now I'm just including all the info I may need.
  td->binitem = b;
  td->hashtable=T;
  td->pointer = ptr;
  td->task= task;
  td->traverserID = traverserID;
  td->heaproot = heaproot;

  b->item.status=status;
  be->tail->next=(BinItem*)b;
  be->tail=(BinItem*)b;
  be->head=originalHead; // lock released
  return status;
}

int READBINCASE(Hashtable *T, void *ptr, BinItem *originalHead, int key, int traverserID, SESEcommon * task, void *heaproot) {
  BinElement * be = T->array[key];
  BinItem * bintail=be->tail;
  //check if already added item or not.
  if(bintail->type == WRITEBIN) {
    TraverserData * td = &(((WriteBinItem *)bintail)->val);
    if(unlikely(td->pointer == ptr) && td->traverserID == traverserID) {
      be->head = originalHead; //lock released
      return bintail->status;
    }
  }
  else if(bintail->type == READEFFECT) {
    TraverserData * td = &((ReadBinItem *)bintail)->array[((ReadBinItem *)bintail)->index - 1];
    if(unlikely(td->pointer == ptr) && td->traverserID == traverserID) {
      return bintail->status;
  }

  if (isReadBinItem(bintail)) {
    return TAILREADCASE(T, ptr, originalHead, bintail, key, traverserID, task, heaproot);
  } else if (!isReadBinItem(bintail)) {
    TAILWRITECASE(T, ptr, originalHead, bintail, key, traverserID, task, heaproot);
    return NOTREADY;
  }
}

int TAILREADCASE(Hashtable *T, void * ptr, BinItem *originalHead, BinItem *bintail, int key, int traverserID, SESEcommon * task, void *heaproot) {
  ReadBinItem * readbintail=(ReadBinItem*)T->array[key]->tail;
  int status, retval;
  TraverserData *td;
  if (readbintail->item.status==READY) {
    status=READY;
    retval=READY;
  } else {
    status=NOTREADY;
    retval=NOTREADY;
  }

  if (readbintail->index==NUMREAD) { // create new read group
    ReadBinItem* rb=createReadBinItem();
    td = &rb->array[rb->index++];

    rb->item.total=1;
    rb->item.status=status;
    T->array[key]->tail->next=(BinItem*)rb;
    T->array[key]->tail=(BinItem*)rb;
  } else { // group into old tail
    td = &readbintail->array[readbintail->index++];
    atomic_inc(&readbintail->item.total);
    //printf("grouping with %d\n",readbintail->index);
  }

  td->binitem = bintail;
  td->hashtable=T;
  td->pointer = ptr;
  td->task= task;
  td->traverserID = traverserID;
  td->heaproot = heaproot;

  T->array[key]->head=originalHead;//released lock
  return retval;
}

void TAILWRITECASE(Hashtable *T, void *ptr, BinItem *val, BinItem *bintail, int key, int traverserID, SESEcommon * task, void *heaproot) {
  //  WriteBinItem* wb=createWriteBinItem();
  //wb->val=r;
  //wb->item.total=1;//safe because item could not have started
  //wb->item.status=NOTREADY;
  ReadBinItem* rb=createReadBinItem();
  TraverserData * td = &(rb->array[rb->index++]);
  rb->item.total=1;//safe because item could not have started
  rb->item.status=NOTREADY;

  td->binitem = rb;
  td->hashtable=T;
  td->pointer = ptr;
  td->task= task;
  td->traverserID = traverserID;
  td->heaproot = heaproot;

  T->array[key]->tail->next=(BinItem*)rb;
  T->array[key]->tail=(BinItem*)rb;
  T->array[key]->head=val;//released lock
}

