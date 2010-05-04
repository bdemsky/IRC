#include "runtime.h"

#include <stdlib.h>
#include <stdio.h>
#include <assert.h>

#include "mem.h"
#include "mlp_runtime.h"
#include "workschedule.h"
#include "methodheaders.h"



/*
__thread struct Queue* seseCallStack;
__thread pthread_once_t mlpOnceObj = PTHREAD_ONCE_INIT;
void mlpInitOncePerThread() {
  seseCallStack = createQueue();
}
*/
__thread SESEcommon_p seseCaller;


void* mlpAllocSESErecord( int size ) {
  void* newrec = RUNMALLOC( size );  
  if( newrec == 0 ) {
    printf( "mlpAllocSESErecord did not obtain memory!\n" );
    exit( -1 );
  }
  return newrec;
}


void mlpFreeSESErecord( void* seseRecord ) {
  RUNFREE( seseRecord );
}

MemoryQueue** mlpCreateMemoryQueueArray(int numMemoryQueue){
  int i;
  MemoryQueue** newMemoryQueue=(MemoryQueue**)RUNMALLOC( sizeof( MemoryQueue* ) * numMemoryQueue );
  for(i=0; i<numMemoryQueue; i++){
    newMemoryQueue[i]=createMemoryQueue();
  }
  return newMemoryQueue;
}

REntry* mlpCreateREntryArray(){
  REntry* newREntryArray=(REntry*)RUNMALLOC(sizeof(REntry)*NUMRENTRY);
  return newREntryArray;
}

REntry* mlpCreateFineREntry(int type, void* seseToIssue, void* dynID){
  REntry* newREntry=(REntry*)RUNMALLOC(sizeof(REntry));
  newREntry->type=type;
  newREntry->seseRec=seseToIssue;
  newREntry->pointer=dynID;
  if((*newREntry->pointer)!=0){// make sure it is not unresolved address.
    struct ___Object___ * obj=(struct ___Object___*)((unsigned INTPTR)*newREntry->pointer);
    newREntry->oid=obj->oid;
  }
  return newREntry;
}

REntry* mlpCreateREntry(int type, void* seseToIssue){
  REntry* newREntry=(REntry*)RUNMALLOC(sizeof(REntry));
  newREntry->type=type;
  newREntry->seseRec=seseToIssue;
  return newREntry;
}

int isParent(REntry *r) {
  if (r->type==PARENTREAD || r->type==PARENTWRITE) {
    return TRUE;
  } else {
    return FALSE;
  }
}

int isParentCoarse(REntry *r){
  if (r->type==PARENTCOARSE){
    return TRUE;
  }else{
    return FALSE;
  }
}

int isFineRead(REntry *r) {
  if (r->type==READ || r->type==PARENTREAD) {
    return TRUE;
  } else {
    return FALSE;
  }
}

int isFineWrite(REntry *r) {
  if (r->type==WRITE || r->type==PARENTWRITE) {
    return TRUE;
  } else {
    return FALSE;
  }
}

int isCoarse(REntry *r){
  if(r->type==COARSE || r->type==PARENTCOARSE){
    return TRUE;
  } else {
    return FALSE;
  }
}

int isSCC(REntry *r){
  if(r->type==SCCITEM){
    return TRUE;
  } else {
    return FALSE;
  }
}

int isSingleItem(MemoryQueueItem *qItem){
  if(qItem->type==SINGLEITEM){
    return TRUE;
  } else {
    return FALSE;
  }
}

int isHashtable(MemoryQueueItem *qItem){
  if(qItem->type==HASHTABLE){
    return TRUE;
  } else {
    return FALSE;
  }
}

int isVector(MemoryQueueItem *qItem){
  if(qItem->type==VECTOR){
    return TRUE;
  } else {
    return FALSE;
  }
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

int generateKey(unsigned int data){
  return (data&H_MASK)>> 4;
}

Hashtable* createHashtable(){
  int i=0;
  Hashtable* newTable=(Hashtable*)RUNMALLOC(sizeof(Hashtable));
  newTable->item.type=HASHTABLE;
  for(i=0;i<NUMBINS;i++){
    newTable->array[i]=(BinElement*)RUNMALLOC(sizeof(BinElement));
    newTable->array[i]->head=NULL;
    newTable->array[i]->tail=NULL;
  }
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

Vector* createVector(){
  Vector* vector=(Vector*)RUNMALLOC(sizeof(Vector));
  vector->index=0;
  vector->item.type=VECTOR;
  return vector;
}

SCC* createSCC(){
  SCC* scc=(SCC*)RUNMALLOC(sizeof(SCC));
  scc->item.type=SINGLEITEM;
  return scc;
}

MemoryQueue* createMemoryQueue(){
  MemoryQueue* queue = (MemoryQueue*)RUNMALLOC(sizeof(MemoryQueue));
  MemoryQueueItem* dummy=(MemoryQueueItem*)RUNMALLOC(sizeof(MemoryQueueItem));
  dummy->type=3; // dummy type
  dummy->total=0;
  dummy->status=READY;
  queue->head = dummy;
  queue->tail = dummy;
  return queue;
}

int ADDRENTRY(MemoryQueue * q, REntry * r) {
  if (isFineRead(r) || isFineWrite(r)) { 
    return ADDTABLE(q, r);
  } else if (isCoarse(r)) {
    return ADDVECTOR(q, r);
  } else if (isSCC(r)) {
    return ADDSCC(q, r);
  }
}

int ADDTABLE(MemoryQueue *q, REntry *r) {
  if(!isHashtable(q->tail)) {
    //Fast Case
    MemoryQueueItem* tail=q->tail;
    if (isParent(r) && tail->total==0 && q->tail==q->head) {
      return READY;
    }

    //Add table
    Hashtable* h=createHashtable();
    tail->next=(MemoryQueueItem*)h;
    //************NEED memory barrier here to ensure compiler does not cache Q.tail.status********
    if (BARRIER() && tail->status==READY && tail->total==0 && q->tail==q->head) { 
      //previous Q item is finished
      h->item.status=READY;
    }
    q->tail=(MemoryQueueItem*)h;
    // handle the the queue item case
    if(q->head->type==3){
      q->head=(MemoryQueueItem*)h;
    }
  }

  //at this point, have table
  Hashtable* table=(Hashtable*)q->tail;
  r->hashtable=table; // set rentry's hashtable
  if((*(r->pointer)==0 || (*(r->pointer)!=0 && BARRIER() && table->unresolvedQueue!=NULL))){
    struct Queue* val;
    // grab lock on the queue    
    do {  
      val=(struct Queue*)0x1;       
      val=(struct Queue*)LOCKXCHG((unsigned INTPTR*)&(table->unresolvedQueue), (unsigned INTPTR)val);
    } while(val==(struct Queue*)0x1);     
    if(val==NULL){
      //queue is null, first case
      if(*(r->pointer)!=0){
	// check whether pointer is already resolved, or not.
	table->unresolvedQueue=NULL; //released lock;
	return ADDTABLEITEM(table,r,TRUE);
      }
      struct Queue* queue=createQueue();
      addNewItemBack(queue,r);
      atomic_inc(&table->item.total); 
      table->unresolvedQueue=queue; // expose new queue     
    }else{ 
      // add unresolved rentry at the end of the queue.
      addNewItemBack(val,r);
      atomic_inc(&table->item.total);    
      table->unresolvedQueue=val; // released lock
    }   
    return NOTREADY;
  }
  BinItem * val;
  //int key=generateKey((unsigned int)(unsigned INTPTR)*(r->pointer));  
  int key=generateKey(r->oid);
  do {  
    val=(BinItem*)0x1;       
    BinElement* bin=table->array[key];
    val=(BinItem*)LOCKXCHG((unsigned INTPTR*)&(bin->head), (unsigned INTPTR)val);//note...talk to me about optimizations here. 
  } while(val==(BinItem*)0x1);
  //at this point have locked bin
  if (val==NULL) {
    return EMPTYBINCASE(table, table->array[key], r, TRUE);
  } else {
    if (isFineWrite(r)) {
      return WRITEBINCASE(table, r, val, key, TRUE);
    } else if (isFineRead(r)) {
      return READBINCASE(table, r, val, key, TRUE);
    }
  }
}

int ADDTABLEITEM(Hashtable* table, REntry* r, int inc){
 
  BinItem * val;
  //  int key=generateKey((unsigned int)(unsigned INTPTR)*(r->pointer));
  int key=generateKey(r->oid);
  do {  
    val=(BinItem*)0x1;       
    BinElement* bin=table->array[key];
    val=(BinItem*)LOCKXCHG((unsigned INTPTR*)&(bin->head), (unsigned INTPTR)val); 
  } while(val==(BinItem*)0x1);
  //at this point have locked bin
  if (val==NULL) {
    return EMPTYBINCASE(table, table->array[key], r, inc);
  } else {
    if (isFineWrite(r)) {
      return WRITEBINCASE(table, r, val, key, inc);
    } else if (isFineRead(r)) {
      return READBINCASE(table, r, val, key, inc);
    }
  }
}

int EMPTYBINCASE(Hashtable *T, BinElement* be, REntry *r, int inc) {
  int retval;
  BinItem* b;
  if (isFineWrite(r)) {
    b=(BinItem*)createWriteBinItem();
    ((WriteBinItem*)b)->val=r;//<-only different statement
  } else if (isFineRead(r)) {
    b=(BinItem*)createReadBinItem();
    ReadBinItem* readbin=(ReadBinItem*)b;
    readbin->array[readbin->index++]=r;
  }
  b->total=1;

  if (T->item.status==READY) { 
    //current entry is ready
    b->status=READY;
    retval=READY;
    if (isParent(r)) {
      be->head=NULL; // released lock
      return retval;
    }
  } else {
    b->status=NOTREADY;
    retval=NOTREADY;
  }

  if(inc){
    atomic_inc(&T->item.total);
  }
  r->hashtable=T;
  r->binitem=b;
  be->tail=b;
  be->head=b;//released lock
  return retval;
}

int WRITEBINCASE(Hashtable *T, REntry *r, BinItem *val, int key, int inc) {
  //chain of bins exists => tail is valid
  //if there is something in front of us, then we are not ready

  int retval;
  BinElement* be=T->array[key];

  BinItem *bintail=be->tail;

  WriteBinItem *b=createWriteBinItem();
  b->val=r;
  b->item.total=1;

  // note: If current table clears all dependencies, then write bin is ready
  if (T->item.total==0){
    retval=READY;    
  }else{
    retval=NOTREADY;
  }
  b->item.status=retval;
  //  b->item.status=NOTREADY;
  
  if(inc){
    atomic_inc(&T->item.total);
  }

  r->hashtable=T;
  r->binitem=(BinItem*)b;

  be->tail->next=(BinItem*)b;
  be->tail=(BinItem*)b;
  be->head=val;
  return retval;
}

READBINCASE(Hashtable *T, REntry *r, BinItem *val, int key, int inc) {
  BinItem * bintail=T->array[key]->tail;
  if (isReadBinItem(bintail)) {
    return TAILREADCASE(T, r, val, bintail, key, inc);
  } else if (!isReadBinItem(bintail)) {
    TAILWRITECASE(T, r, val, bintail, key, inc);
    return NOTREADY;
  }
}

int TAILREADCASE(Hashtable *T, REntry *r, BinItem *val, BinItem *bintail, int key, int inc) {
  ReadBinItem * readbintail=(ReadBinItem*)T->array[key]->tail;
  int status, retval;
  if (readbintail->item.status=READY) { 
    status=READY;
    retval=READY;
    if (isParent(r)) {
      T->array[key]->head=val;//released lock
      return READY;
    }
  } else {
    status=NOTREADY;
    retval=NOTREADY;
  }

  if (readbintail->index==NUMREAD) { // create new read group
    ReadBinItem* rb=createReadBinItem();
    rb->array[rb->index++]=r;
    rb->item.total=1;//safe only because item could not have started
    rb->item.status=status;
    T->array[key]->tail->next=(BinItem*)rb;
    T->array[key]->tail=(BinItem*)rb;
    r->binitem=(BinItem*)rb;
  } else { // group into old tail
    readbintail->array[readbintail->index++]=r;
    atomic_inc(&readbintail->item.total);
    r->binitem=(BinItem*)readbintail;
    //printf("grouping with %d\n",readbintail->index);
  }
  if(inc){
    atomic_inc(&T->item.total);
  }
  r->hashtable=T;
  T->array[key]->head=val;//released lock
  return retval;
}

TAILWRITECASE(Hashtable *T, REntry *r, BinItem *val, BinItem *bintail, int key, int inc) {
  //  WriteBinItem* wb=createWriteBinItem();
  //wb->val=r;
  //wb->item.total=1;//safe because item could not have started
  //wb->item.status=NOTREADY;
  ReadBinItem* rb=createReadBinItem();
  rb->array[rb->index++]=r;
  rb->item.total=1;//safe because item could not have started
  rb->item.status=NOTREADY;
  if(inc){
    atomic_inc(&T->item.total);
  }
  r->hashtable=T;
  r->binitem=(BinItem*)rb;
  T->array[key]->tail->next=(BinItem*)rb;
  T->array[key]->tail=(BinItem*)rb;
  T->array[key]->head=val;//released lock
}

ADDVECTOR(MemoryQueue *Q, REntry *r) {
  if(!isVector(Q->tail)) {
    //Fast Case
    if (isParentCoarse(r) && Q->tail->total==0 && Q->tail==Q->head) { 
      return READY;
    }

    //added vector
    Vector* V=createVector();
    Q->tail->next=(MemoryQueueItem*)V;     
    //************NEED memory barrier here to ensure compiler does not cache Q.tail.status******
    if (BARRIER() && Q->tail->status==READY&&Q->tail->total==0) { 
      //previous Q item is finished
      V->item.status=READY;
    }
    Q->tail=(MemoryQueueItem*)V;
    // handle the the queue item case
    if(Q->head->type==3){
      Q->head=(MemoryQueueItem*)V;
    }
  }
  //at this point, have vector
  Vector* V=(Vector*)Q->tail;
  if (V->index==NUMITEMS) {
    //vector is full
    //added vector
    V=createVector();
    V->item.status=NOTREADY;
    Q->tail->next=(MemoryQueueItem*)V;
    //***NEED memory barrier here to ensure compiler does not cache Q.tail.status******
    if (BARRIER() && Q->tail->status==READY) { 
      V->item.status=READY;
    }
    Q->tail=(MemoryQueueItem*)V;
  }

  atomic_inc(&V->item.total);
  //expose entry
  int index=V->index;
  V->array[index]=r;
  //*****NEED memory barrier here to ensure compiler does not reorder writes to V.array and V.index
  BARRIER();
  V->index++;
  //*****NEED memory barrier here to ensure compiler does not cache V.status*********
  r->vector=V;
  if (BARRIER() && V->item.status==READY) {
    void* flag=NULL;
    flag=(void*)LOCKXCHG((unsigned INTPTR*)&(V->array[index]), (unsigned INTPTR)flag); 
    if (flag!=NULL) {
      if (isParent(r)) { //parent's retire immediately
        atomic_dec(&V->item.total);
      }
      return READY;
    } else {
      return NOTREADY;//<- means that some other dispatcher got this one...so need to do accounting correctly
    }
  } else {
    return NOTREADY;
  }
}


//SCC's don't come in parent variety
ADDSCC(MemoryQueue *Q, REntry *r) {
  //added SCC
  SCC* S=createSCC();
  S->item.total=1; 
  S->val=r;
  r->scc=S;
  Q->tail->next=(MemoryQueueItem*)S;
  //*** NEED BARRIER HERE
  if (BARRIER() && Q->tail->status==READY && Q->tail->total==0 && Q->tail==Q->head) {
    //previous Q item is finished
    S->item.status=READY;
    Q->tail=(MemoryQueueItem*)S;
    // handle the the queue item case
    if(Q->head->type==3){
      Q->head=(MemoryQueueItem*)S;
    }
    void* flag=NULL;
    flag=(void*)LOCKXCHG((unsigned INTPTR*)&(S->val), (unsigned INTPTR)flag);
    if (flag!=NULL) {
      return READY;
    } else {
      return NOTREADY;//<- means that some other dispatcher got this one...so need to do accounting correctly
    }
  } else {
    Q->tail=(MemoryQueueItem*)S;
    return NOTREADY;
  }
}


void RETIRERENTRY(MemoryQueue* Q, REntry * r) {
  if (isFineWrite(r)||isFineRead(r)) {
    RETIREHASHTABLE(Q, r);
  } else if (isCoarse(r)) {
    RETIREVECTOR(Q, r);
  } else if (isSCC(r)) {
    RETIRESCC(Q, r);
  }
}

RETIRESCC(MemoryQueue *Q, REntry *r) {
  SCC* s=r->scc;
  s->item.total=0;//don't need atomicdec
  RESOLVECHAIN(Q);
}


RETIREHASHTABLE(MemoryQueue *q, REntry *r) {
  Hashtable *T=r->hashtable;
  BinItem *b=r->binitem;
  RETIREBIN(T,r,b);
  atomic_dec(&T->item.total);
  if (T->item.next!=NULL && T->item.total==0) { 
    RESOLVECHAIN(q);
  }
}

RETIREBIN(Hashtable *T, REntry *r, BinItem *b) {
  //  int key=generateKey((unsigned int)(unsigned INTPTR)*(r->pointer));
  int key=generateKey(r->oid);
  if(isFineRead(r)) {
    atomic_dec(&b->total);
  }
  if (isFineWrite(r) || (isFineRead(r) && b->next!=NULL && b->total==0)) {
      // CHECK FIRST IF next is nonnull to guarantee that b.total cannot change
    BinItem * val;
    do {  
      val=(BinItem*)0x1;
      val=(BinItem*)LOCKXCHG((unsigned INTPTR*)&(T->array[key]->head), (unsigned INTPTR)val);
    } while(val==(BinItem*)0x1);
    // at this point have locked bin
    BinItem *ptr=val;
    int haveread=FALSE;
    int i;
    while (ptr!=NULL) {
       if (isReadBinItem(ptr)) {
	ReadBinItem* rptr=(ReadBinItem*)ptr;
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
        } else if ((BinItem*)rptr==val) {
          val=val->next;
        }
      } else if(isWriteBinItem(ptr)) {
        if (haveread)  
          break;
	if(ptr->status==NOTREADY){
	  resolveDependencies(((WriteBinItem*)ptr)->val);
	  ptr->status=READY;
	  if(isParent(((WriteBinItem*)ptr)->val)){
	    atomic_dec(&T->item.total);
	    val=val->next;	  
	  }else
	    break;
	}else{ // write bin is already resolved
	  val=val->next;
	}
	/*
        if(ptr->status==NOTREADY) {   
	  resolveDependencies(((WriteBinItem*)ptr)->val);
	}        
	  ptr->status=READY; 	  
	  if (isParent(((WriteBinItem*)ptr)->val)) {  
	    atomic_dec(&T->item.total);
	    //val=val->next;
	    val=ptr->next;
	  } else
	    break;
        } 
	*/
      } 
      ptr=ptr->next;
    }
    T->array[key]->head=val; // release lock
  }
}


RETIREVECTOR(MemoryQueue *Q, REntry *r) {
  Vector* V=r->vector;
  atomic_dec(&V->item.total);
  if (V->item.next!=NULL && V->item.total==0) { //NOTE: ORDERING CRUCIAL HERE
    RESOLVECHAIN(Q);
  }
}

RESOLVECHAIN(MemoryQueue *Q) {
  while(TRUE) {
    MemoryQueueItem* head=Q->head;
    if (head->next==NULL||head->total!=0) { 
      //item is not finished
      if (head->status!=READY) {  
        //need to update status
        head->status=READY;
        if (isHashtable(head)) {
          RESOLVEHASHTABLE(Q, head);
        } else if (isVector(head)) {
          RESOLVEVECTOR(Q, head);
        } else if (isSingleItem(head)) {
          RESOLVESCC(head);
        }
        if (head->next==NULL)
          break;
        if (head->total!=0)
          break;
      } else
        break;
    }
    MemoryQueueItem* nextitem=head->next;
    CAS((unsigned INTPTR*)&(Q->head), (unsigned INTPTR)head, (unsigned INTPTR)nextitem);
    //oldvalue not needed...  if we fail we just repeat
  }
}


RESOLVEHASHTABLE(MemoryQueue *Q, Hashtable *T) {  
  int binidx;
  for (binidx=0;binidx<NUMBINS;binidx++) {    
    BinElement* bin=T->array[binidx];
    BinItem* val;
    do {
      val=(BinItem*)1;
      val=(BinItem*)LOCKXCHG((unsigned INTPTR*)&(bin->head), (unsigned INTPTR)val);
    } while (val==(BinItem*)1);
    //at this point have locked bin    
    int haveread=FALSE; 
    BinItem* ptr=val;
    if(ptr!=NULL&&ptr->status==NOTREADY) {
      do {
        if (isWriteBinItem(ptr)) {
          if (haveread)
	    break;	  
	  resolveDependencies(((WriteBinItem*)ptr)->val);
	  ptr->status=READY;  
          if (isParent(((WriteBinItem*)ptr)->val)) {
            atomic_dec(&T->item.total);
            val=val->next;
          } else
	    break;
        } else if (isReadBinItem(ptr)) {
	  int i;
	  ReadBinItem* rptr=(ReadBinItem*)ptr;
          for(i=0;i<rptr->index;i++) {
	    resolveDependencies(rptr->array[i]);
	    if (isParent(rptr->array[i])) {
              atomic_dec(&rptr->item.total);
              atomic_dec(&T->item.total);
            }
          }
          if (rptr->item.next==NULL||rptr->item.total!=0) {
            haveread=TRUE;
          } else if((BinItem*)rptr==val) {
            val=val->next;
          }
          rptr->item.status=READY; { 
	  }
	  ptr=ptr->next;
	} 
      }while(ptr!=NULL);   
    }
    bin->head=val; // released lock;
  }
}

RESOLVEVECTOR(MemoryQueue *q, Vector *V) {
  int i;
  Vector* tmp=V;
  //handle ready cases
  while(TRUE) {
    //enqueue everything
    for (i=0;i<NUMITEMS;i++) {
      REntry* val=NULL;
      val=(REntry*)LOCKXCHG((unsigned INTPTR*)&(tmp->array[i]), (unsigned INTPTR)val); 
      if (val!=NULL) { 
	resolveDependencies(val);
	if (isParent(val)) {
          atomic_dec(&tmp->item.total);
        }
      }
    }
    if (tmp->item.next!=NULL&&isVector(tmp->item.next)) {
      tmp=(Vector*)tmp->item.next;
    } else {
      break;
    }
  }
}

RESOLVESCC(SCC *S) {
  //precondition: SCC's state is READY
  void* flag=NULL;
  flag=(void*)LOCKXCHG((unsigned INTPTR*)&(S->val), (unsigned INTPTR)flag); 
  if (flag!=NULL) {
    resolveDependencies(flag);
  }
}


resolveDependencies(REntry* rentry){
  SESEcommon* seseCommon=(SESEcommon*)rentry->seseRec;
  if(rentry->type==READ || rentry->type==WRITE || rentry->type==COARSE || rentry->type==SCCITEM){   
    if( atomic_sub_and_test(1, &(seseCommon->unresolvedDependencies)) ){
      workScheduleSubmit(seseCommon);
    }   
  }else if(rentry->type==PARENTREAD || rentry->type==PARENTWRITE ||rentry->type==PARENTCOARSE){
     psem_give(&(rentry->parentStallSem));
  }
}

resolvePointer(REntry* rentry){  
 
  Hashtable* table=rentry->hashtable;
  if(table==NULL){
    //resolved already before related rentry is enqueued to the waiting queue
    return;
  }
  struct Queue* val;
  do {  
    val=(struct Queue*)0x1;       
    val=(struct Queue*)LOCKXCHG((unsigned INTPTR*)&(table->unresolvedQueue), (unsigned INTPTR)val);
  } while(val==(struct Queue*)0x1); 
  if(val!=NULL && getHead(val)->objectptr==rentry){
    // handling pointer is the first item of the queue
    // start to resolve until it reaches unresolved pointer or end of queue
    do{
      struct QueueItem* head=getHead(val);
      if(head!=NULL){
	REntry* rentry=(REntry*)head->objectptr;  
	if(*(rentry->pointer)==0){
	  // encounters following unresolved pointer
	  table->unresolvedQueue=val;//released lock
	  break;
	}
	removeItem(val,head);
	//now, address is resolved. update OID field.
	struct ___Object___ * obj=(struct ___Object___*)((unsigned INTPTR)*rentry->pointer);
	rentry->oid=obj->oid;
	if(ADDTABLEITEM(table, rentry, FALSE)==READY){
	  resolveDependencies(rentry);
	}
      }else{
	table->unresolvedQueue=NULL; // set hashtable as normal-mode.
	break;
      }
    }while(TRUE);
  }else{
    // resolved rentry is not head of queue
    table->unresolvedQueue=val;//released lock;
  }  
}

void rehashMemoryQueue(SESEcommon_p seseParent){    
  // update memory queue
  int i,binidx;
  for(i=0; i<seseParent->numMemoryQueue; i++){
    MemoryQueue *memoryQueue=seseParent->memoryQueueArray[i];
    MemoryQueueItem *memoryItem=memoryQueue->head;
    MemoryQueueItem *prevItem=NULL;
    while(memoryItem!=NULL){
      if(memoryItem->type==HASHTABLE){
	//do re-hash!
	Hashtable* ht=(Hashtable*)memoryItem;
	Hashtable* newht=createHashtable();	
	int binidx;
	for(binidx=0; binidx<NUMBINS; binidx++){
	  BinElement *bin=ht->array[binidx];
	  BinItem *binItem=bin->head;
	  //traverse over the list of each bin
	  while(binItem!=NULL){
	    if(binItem->type==READBIN){
	      ReadBinItem* readBinItem=(ReadBinItem*)binItem;
	      int ridx;
	      for(ridx=0; ridx<readBinItem->index; ridx++){
		REntry *rentry=readBinItem->array[ridx];
		int newkey=generateKey((unsigned int)(unsigned INTPTR)*(rentry->pointer));	
		int status=rentry->binitem->status;	      
		ADDTABLEITEM(newht,rentry,TRUE);
		rentry->binitem->status=status; // update bin status as before rehash
	      }
	    }else{//write bin
	      REntry *rentry=((WriteBinItem*)binItem)->val;
	      int newkey=generateKey((unsigned int)(unsigned INTPTR)*(rentry->pointer));	
	      int status=rentry->binitem->status;	      
	      ADDTABLEITEM(newht,rentry,TRUE);    	      
	      int newstatus=rentry->binitem->status;
	      //printf("[%d]old status=%d new status=%d\n",i,status,newstatus);
	      rentry->binitem->status=status; // update bin status as before rehash
	    }
	    binItem=binItem->next;
	  }
	}
	newht->item.status=ht->item.status; // update hashtable status
	if(prevItem!=NULL){
	  prevItem->next=(MemoryQueueItem*)newht;
	}else{
	  if(memoryQueue->head==memoryQueue->tail){
	    memoryQueue->tail=(MemoryQueueItem*)newht;
	  }
	  memoryQueue->head=(MemoryQueueItem*)newht;
	}
	newht->item.next=ht->item.next;	
      }
      prevItem=memoryItem;
      memoryItem=memoryItem->next;
    }
  }

}
