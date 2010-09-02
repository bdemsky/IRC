#include "mlp_lock.h"
#include "WaitingQueue.h"
//TODO check that the right path is pionted to by the below #include
#include "RuntimeConflictResolver.h"

//Note: is global to all processes
struct BinVector * freeBinVectors = NULL;

//TODO perhaps print a map of allocsites to arrayIndex?
//Unique queue for each hashtable
struct WaitingQueue * mallocWaitingQueue(int size) {
  //TODO perhaps in the future get rid of the WaitingQueue object all together to improve performance (but reduce clarity)
  struct WaitingQueue * q = (struct WaitingQueue *) malloc(sizeof(struct WaitingQueue));
  q->array = (struct BinElement *) malloc(sizeof(struct BinElement) * size);
  return q;
}

//NOTE: allocSiteID is NOT the same as allocsite, rather it's an ID generated by the traverser for an alloc site for a traversal.
void put(int allocSiteID, struct WaitingQueue * queue, int effectType, void * resumePtr, int traverserID) {
  //lock bin
  struct BinVector * head;
  struct BinVector * currentVector;
  struct TraverserData * b;
  do {
    head = (struct BinVector *) 0x1;
    LOCKXCHG(((queue->array)[allocSiteID]).head, head);
  } while (head == (struct BinVector *) 0x1);
  //now the current bin is locked.

  //completely empty case
  if (queue->array[allocSiteID].tail == NULL) {
    currentVector = getUsableVector();
    head = currentVector;
    queue->array[allocSiteID].tail = currentVector; //We do not set the head here because we need lock
  }
  //Tail bin full
  else if (queue->array[allocSiteID].tail->index == NUMITEMS) {
    currentVector = getUsableVector();
    queue->array[allocSiteID].tail->next = currentVector;
    queue->array[allocSiteID].tail = currentVector;
  } else { //the bin not full case
    currentVector = queue->array[allocSiteID].tail;
  }

  //add item
  b = &(currentVector->array[currentVector->index++]);

  b->resumePtr = resumePtr;
  b->traverserID = traverserID;
  b->effectType = effectType;

  queue->array[allocSiteID].size++;
  queue->array[allocSiteID].head = head; // release lock
}

//!0 = no one in line.
//=0 = someone is in line.
int check(struct WaitingQueue * queue, int allocSiteID) {
  return ((queue->array)[allocSiteID]).size == 0;
}

//NOTE: Only the HashTable calls this function
void resolveChain(struct WaitingQueue * queue, int allocSiteID) {
  struct BinVector * head;
  struct BinVector * next;
  struct BinVector * currentVector;
  int i;
  do {
    head = (struct BinVector *) 0x1;
    LOCKXCHG(((queue->array)[allocSiteID]).head, head);
  } while (head == (struct BinVector *) 0x1);
  //now the current bin is locked.

  //I don't think this case would ever happen, but just in case.
  if(head == NULL) {
    ((queue->array)[allocSiteID]).head = NULL; //release lock
    return;
  }

  //To prevent live lock, clear and unlock the chain and then process it on the side
  currentVector = head;
  ((queue->array)[allocSiteID]).size = 0;
  ((queue->array)[allocSiteID]).tail = NULL;
  ((queue->array)[allocSiteID]).head = NULL; //lock released

  //processing the chain.
  while(currentVector != NULL) {
    for(i = 0; i < currentVector->index; i++) {
      struct TraverserData * td = &(currentVector->array[i]);
      runRCRTraverse(td->resumePtr, td->traverserID);
    }

    //return this vector to the free-pool
    next = currentVector->next;
    returnVectorToFreePool(currentVector);
    currentVector = next;
  }
}

//NOTE: Only the traverser should be able to call this function and it clears the entire chain.
void returnVectorToFreePool(struct BinVector *ptr) {
  struct BinVector * freeHead;
  do {
    freeHead = (struct BinVector *) 0x1;
    //TODO check if this cuts off part of the mem addr or not.
    LOCKXCHG(freeBinVectors, freeHead);
  } while (freeHead == (struct BinVector *) 0x1);
  //free bins locked

  if(freeHead == NULL) {
    freeBinVectors = ptr; //lock released
  }
  else {
    ptr->next = freeHead;
    freeBinVectors = ptr; //lock released
  }
}

struct BinVector * getUsableVector() {
  //Attempt to take one from the free bin first
  struct BinVector * ptr;
  do {
    ptr = (struct BinVector *) 0x1;
    LOCKXCHG(freeBinVectors, ptr);
  } while (ptr == (struct BinVector *) 0x1);
  //free bins locked

  if (ptr == NULL) {
    freeBinVectors = NULL; //lock released
    return mallocNewVector();
  } else {
    freeBinVectors = ptr->next; //lock released
    ptr->next = NULL;
    ptr->index = 0;
  }
}

struct BinVector * mallocNewVector() {
  struct BinVector * retval = (struct BinVector *) malloc(
      sizeof(struct BinVector));
  retval->next = NULL;
  retval->index = 0;
  return retval;
}

