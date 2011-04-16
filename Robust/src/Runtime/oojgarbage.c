#ifdef MLP
#include "garbage.h"
#ifdef SQUEUE
#include "squeue.h"
#else
#include "deque.h"
#endif
#include "mlp_runtime.h"
#include "workschedule.h"
extern volatile int    numWorkSchedWorkers;
extern deque* deques;

__thread SESEcommon* seseCommon;

void searchoojroots() {
#ifdef SQUEUE
  {
    int        i;
    deque*     dq;
    dequeItem *di;
    int        j;

    // goes over ready-to-run SESEs
    for( i = 0; i < numWorkSchedWorkers; ++i ) {
      dq = &(deques[i]);

      di=dq->head;

      do {
        // check all the relevant indices of this
        // node in the deque, noting if we are in
        // the top/bottom node which can be partially
        // full

          // WHAT? 
          //SESEcommon* common = (SESEcommon*) n->itsDataArr[j];
          //if(common==seseCommon){
          // skip the current running SESE
          //  continue;
          //}
	di=(dequeItem *) EXTRACTPTR((INTPTR)di);
	SESEcommon* seseRec = (SESEcommon*) di->work;
	if (seseRec!=NULL) {
          struct garbagelist* gl     = (struct garbagelist*) &(seseRec[1]);
          struct garbagelist* glroot = gl;
	  
          updateAscendantSESE( seseRec );
	  
          while( gl != NULL ) {
            int k;
            for( k = 0; k < gl->size; k++ ) {
              void* orig = gl->array[k];
              ENQUEUE( orig, gl->array[k] );
            }
            gl = gl->next;
          } 
        }
        // we only have to move across the nodes
        // of the deque if the top and bottom are
        // not the same already
	  di=di->next;
      } while( di !=NULL) ;
    }
  }    
#else
  {
    int        i;
    deque*     dq;
    dequeNode* botNode;
    int        botIndx;
    dequeNode* topNode;
    int        topIndx;
    dequeNode* n;
    int        j;
    int        jLo;
    int        jHi;
    
    // goes over ready-to-run SESEs
    for( i = 0; i < numWorkSchedWorkers; ++i ) {
      dq = &(deques[i]);
      
      botNode = dqDecodePtr( dq->bottom );
      botIndx = dqDecodeIdx( dq->bottom );
      
      topNode = dqDecodePtr( dq->top );
      topIndx = dqDecodeIdx( dq->top );
      
      
      n = botNode;
      do {
	// check all the relevant indices of this
	// node in the deque, noting if we are in
	// the top/bottom node which can be partially
	// full
	if( n == botNode ) { jLo = botIndx; } else { jLo = 0; }
	if( n == topNode ) { jHi = topIndx; } else { jHi = DQNODE_ARRAYSIZE; }
	
	for( j = jLo; j < jHi; ++j ) {
	  
	  // WHAT?
	  //SESEcommon* common = (SESEcommon*) n->itsDataArr[j];
	  //if(common==seseCommon){
	  //  continue;
	  //}
	  
          SESEcommon* seseRec = (SESEcommon*) n->itsDataArr[j];
	  
	  struct garbagelist* gl     = (struct garbagelist*) &(seseRec[1]);
          struct garbagelist* glroot = gl;
	  
          updateAscendantSESE( seseRec );
	  
          while( gl != NULL ) {
            int k;
            for( k = 0; k < gl->size; k++ ) {
              void* orig = gl->array[k];
              ENQUEUE( orig, gl->array[k] );
	    }
	    gl = gl->next;
	  }
	}
	
	// we only have to move across the nodes
	// of the deque if the top and bottom are
	// not the same already
        if( botNode != topNode ) {
          n = n->next;
        }
      } while( n != topNode );
    }
  }
#endif
}

updateForwardList(struct Queue *forwardList, int prevUpdate) {
  struct QueueItem * fqItem=getHead(forwardList);
  while(fqItem!=NULL){
    SESEcommon* seseRec = (SESEcommon*)(fqItem->objectptr);
    struct garbagelist * gl=(struct garbagelist *)&(seseRec[1]);
    if(prevUpdate==TRUE){
      updateAscendantSESE(seseRec);	
    }
    // do something here
    while(gl!=NULL) {
      int i;
      for(i=0; i<gl->size; i++) {
        void * orig=gl->array[i];
        ENQUEUE(orig, gl->array[i]);
      }
      gl=gl->next;
    }    
    // iterate forwarding list of seseRec
    struct Queue* fList=&seseRec->forwardList;
    updateForwardList(fList,prevUpdate);   
    fqItem=getNextQueueItem(fqItem);
  }   

}

updateMemoryQueue(SESEcommon* seseParent){
  // update memory queue
  int i,binidx;
  for(i=0; i<seseParent->numMemoryQueue; i++){
    MemoryQueue *memoryQueue=seseParent->memoryQueueArray[i];
    MemoryQueueItem *memoryItem=memoryQueue->head;
    while(memoryItem!=NULL){
      if(memoryItem->type==HASHTABLE){
	Hashtable *ht=(Hashtable*)memoryItem;
	for(binidx=0; binidx<NUMBINS; binidx++){
	  BinElement *bin=ht->array[binidx];
	  BinItem *binItem=bin->head;
	  while(binItem!=NULL){
	    if(binItem->type==READBIN){
	      ReadBinItem* readBinItem=(ReadBinItem*)binItem;
	      int ridx;
	      for(ridx=0; ridx<readBinItem->index; ridx++){
		REntry *rentry=readBinItem->array[ridx];
                SESEcommon* seseRec = (SESEcommon*)(rentry->seseRec);
		struct garbagelist * gl= (struct garbagelist *)&(seseRec[1]);
		updateAscendantSESE(seseRec);
		while(gl!=NULL) {
		  int i;
		  for(i=0; i<gl->size; i++) {
		    void * orig=gl->array[i];
		    ENQUEUE(orig, gl->array[i]);
		  }
		  gl=gl->next;
		} 
	      }	
	    }else{ //writebin
	      REntry *rentry=((WriteBinItem*)binItem)->val;
              SESEcommon* seseRec = (SESEcommon*)(rentry->seseRec);
              struct garbagelist * gl= (struct garbagelist *)&(seseRec[1]);
	      updateAscendantSESE(seseRec);
	      while(gl!=NULL) {
		int i;
		for(i=0; i<gl->size; i++) {
		  void * orig=gl->array[i];
		  ENQUEUE(orig, gl->array[i]);
		}
		gl=gl->next;
	      } 
	    }
	    binItem=binItem->next;
	  }
	}
      }else if(memoryItem->type==VECTOR){
	Vector *vt=(Vector*)memoryItem;
	int idx;
	for(idx=0; idx<vt->index; idx++){
	  REntry *rentry=vt->array[idx];
	  if(rentry!=NULL){
            SESEcommon* seseRec = (SESEcommon*)(rentry->seseRec);
	    struct garbagelist * gl= (struct garbagelist *)&(seseRec[1]);
	    updateAscendantSESE(seseRec);
	    while(gl!=NULL) {
	      int i;
	      for(i=0; i<gl->size; i++) {
		void * orig=gl->array[i];
		ENQUEUE(orig, gl->array[i]);
	      }
	      gl=gl->next;
	    } 
	  }
	}
      }else if(memoryItem->type==SINGLEITEM){
	SCC *scc=(SCC*)memoryItem;
	REntry *rentry=scc->val;
	if(rentry!=NULL){
          SESEcommon* seseRec = (SESEcommon*)(rentry->seseRec);
	  struct garbagelist * gl= (struct garbagelist *)&(seseRec[1]);
	  updateAscendantSESE(seseRec);
	  while(gl!=NULL) {
	    int i;
	    for(i=0; i<gl->size; i++) {
	      void * orig=gl->array[i];
	      ENQUEUE(orig, gl->array[i]);
	    }
	    gl=gl->next;
	  } 
	}
      }
      memoryItem=memoryItem->next;
    }
  }     
 }
 
 updateAscendantSESE(SESEcommon* seseRec){   
  int prevIdx;
  for(prevIdx=0; prevIdx<(seseRec->numDependentSESErecords); prevIdx++){
    SESEcommon* prevSESE = (SESEcommon*) 
      (
       ((INTPTR)seseRec) + 
       seseRec->offsetToDepSESErecords +
       (sizeof(INTPTR)*prevIdx)
      );
       
    if(prevSESE!=NULL){
      struct garbagelist * prevgl=(struct garbagelist *)&(((SESEcommon*)(prevSESE))[1]);
      while(prevgl!=NULL) {
	int i;
	for(i=0; i<prevgl->size; i++) {
	  void * orig=prevgl->array[i];
	  ENQUEUE(orig, prevgl->array[i]);
	}
	prevgl=prevgl->next;
      } 
    }
  }
 }
#endif
