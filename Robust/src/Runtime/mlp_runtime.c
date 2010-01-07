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

void addWaitingQueueElement(AllocSite* allocSiteArray, int numAllocSites, long allocID, void *seseRec){

  int i;
  for(i=0;i<numAllocSites;i++){
    if(allocSiteArray[i].id==allocID){
      addNewItemBack(allocSiteArray[i].waitingQueue,seseRec);
      //printf("add new item %d into waiting queue:%d\n",((SESEcommon*)seseRec)->classID,allocID);
      break;
    }
  }
 
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
