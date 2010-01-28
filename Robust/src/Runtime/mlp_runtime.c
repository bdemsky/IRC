#include "runtime.h"

#include <stdlib.h>
#include <stdio.h>
#include <assert.h>

#include "mem.h"
#include "Queue.h"
#include "mlp_runtime.h"
#include "workschedule.h"


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

AllocSite* mlpCreateAllocSiteArray(int numAllocSites){
  int i;
  AllocSite* newAllocSite=(AllocSite*)RUNMALLOC( sizeof( AllocSite ) * numAllocSites );
  for(i=0; i<numAllocSites; i++){
    newAllocSite[i].waitingQueue=createQueue();
  }
  return newAllocSite;
}

ConflictNode* mlpCreateConflictNode(int id){
  ConflictNode* newConflictNode=(ConflictNode*)RUNMALLOC( sizeof( ConflictNode ) );
  newConflictNode->id=id;
  return newConflictNode;
}

WaitingElement* mlpCreateWaitingElement(int status, void* seseToIssue, struct Queue* queue, int id){
  WaitingElement* newElement=(WaitingElement*)RUNMALLOC(sizeof(WaitingElement));
  newElement->status=status;
  newElement->seseRec=seseToIssue;
  newElement->list=queue;
  newElement->id=id;
  return newElement;
}

struct QueueItem* addWaitingQueueElement2(AllocSite* waitingQueueArray, int numWaitingQueue, int waitingID, WaitingElement* element){

  int i;
  struct QueueItem* newItem=NULL;
  for(i=0;i<numWaitingQueue;i++){
    if(waitingQueueArray[i].id==waitingID){
      newItem=addNewItemBack(waitingQueueArray[i].waitingQueue,element);
      return newItem;
      //printf("add new item %d into waiting queue:%d\n",((SESEcommon*)seseRec)->classID,allocID);
    }
  }
  return newItem;  
  
}

struct QueueItem* addWaitingQueueElement(AllocSite* allocSiteArray, int numAllocSites, long allocID, void *seseRec){

  int i;
  struct QueueItem* newItem=NULL;
  for(i=0;i<numAllocSites;i++){
    if(allocSiteArray[i].id==allocID){
      newItem=addNewItemBack(allocSiteArray[i].waitingQueue,seseRec);
      return newItem;
      //printf("add new item %d into waiting queue:%d\n",((SESEcommon*)seseRec)->classID,allocID);
    }
  }
  return newItem;
}

int getQueueIdx(AllocSite* allocSiteArray, int numAllocSites, long  allocID){

  int i;
  for(i=0;i<numAllocSites;i++){
    if(allocSiteArray[i].id==allocID){
      return i;      
    }
  }
  return -1;
}

int isRunnable(struct Queue* waitingQueue, struct QueueItem* qItem){

  if(!isEmpty(waitingQueue)){

    struct QueueItem* current=getHead(waitingQueue);
    while(current!=NULL){
         if(current!=qItem){
	   if(isConflicted(current,qItem)){
	     return 0;
	   }
	 }else{
	   return 1;
	 }
	 current=getNextQueueItem(current);
    }
  }
  return 1;
}

int isConflicted(struct QueueItem* prevItem, struct QueueItem* item){

  WaitingElement* element=item->objectptr;
  WaitingElement* prevElement=prevItem->objectptr;

  if(prevElement->id!=element->id){

    if(element->status==0){ // fine read
    
      if(prevElement->status==1 || prevElement->status==3){
      
	if(isOverlapped(prevElement->list,element->list)){
	  return 1;
	}
      
      }else{
	return 0;
      }
        
    }else if(element->status==1){ // fine write
      if(isOverlapped(prevElement->list,element->list)){
	return 1;
      }
    }else if(element->status==2){// coarse read
      
      if(prevElement->status==1 || prevElement->status==3){
	if(isOverlapped(prevElement->list,element->list)){
	  return 1;
	}
      }

    }else if(element->status==3){// coarse write
      return 1;
    }

  }

  return 0;
}

int isOverlapped(struct Queue* prevList, struct Queue* itemList){

  if(!isEmpty(prevList)){
    struct QueueItem* queueItemA=getHead(prevList); 
    
    while(queueItemA!=NULL){
      ConflictNode* nodeA=queueItemA->objectptr;

      if(!isEmpty(itemList)){
	struct QueueItem* queueItemB=getHead(itemList);
	while(queueItemB!=NULL){
	  ConflictNode* nodeB=queueItemB->objectptr;
	  if(nodeA->id==nodeB->id){
	    return 1;
	  }
	  queueItemB=getNextQueueItem(queueItemB);
	}
      }
      queueItemA=getNextQueueItem(queueItemA);      
    }
  }
  return 0;
  
}

int resolveWaitingQueue(struct Queue* waitingQueue,struct QueueItem* qItem){

  if(!isEmpty(waitingQueue)){

    SESEcommon* qCommon=qItem->objectptr;
    
    struct QueueItem* current=getHead(waitingQueue);
    while(current!=NULL){
         if(current!=qItem){
	   SESEcommon* currentCommon=current->objectptr;
	   if(hasConflicts(currentCommon->classID,qCommon->connectedList)){
	     return 0;
	   }
	 }else{
	   return 1;
	 }
	 current=getNextQueueItem(current);
    }
  }
  return 1;
}

int hasConflicts(int classID, struct Queue* connectedList){
  
  if(!isEmpty(connectedList)){
    struct QueueItem* queueItem=getHead(connectedList); 
    
    while(queueItem!=NULL){
      ConflictNode* node=queueItem->objectptr;
      if(node->id==classID){
	return 1;
      }
      queueItem=getNextQueueItem(queueItem);      
    }
  }
  return 0;
}

void addNewConflictNode(ConflictNode* node, struct Queue* connectedList){
  
  if(!isEmpty(connectedList)){
    struct QueueItem* qItem=getHead(connectedList);
    while(qItem!=NULL){
      ConflictNode* qNode=qItem->objectptr;
      if(qNode->id==node->id){
	return;
      }
      qItem=getNextQueueItem(qItem);
    }
  }

  addNewItem(connectedList,node);

}

int contains(struct Queue* queue, struct QueueItem* qItem){

  if(!isEmpty(queue)){
    struct QueueItem* nextQItem=getHead(queue);
    while(nextQItem!=NULL){
      if((nextQItem->objectptr)==qItem){
	return 1;
      } 
      nextQItem=getNextQueueItem(nextQItem);
    }
  }

  return 0;

}
