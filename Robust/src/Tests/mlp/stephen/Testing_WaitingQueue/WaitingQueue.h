/*
 * waitingQueue.h
 *
 *  Created on: Sep 1, 2010
 *      Author: stephey
 */
#ifndef WAITINGQUEUE_H_
#define WAITINGQUEUE_H_

#define NOT_AT_FRONT = 3;
#define TRAVERSER_FINISHED = 2;
#define NUMITEMS_WQ 20

/* print header */
typedef struct TraverserData_WQ {
  void * resumePtr;
  int effectType;
  int traverserID;
} TraverserResumeDataFromWaitingQ;

typedef struct BinVector_wq {
  struct TraverserData_WQ array[NUMITEMS_WQ];
  struct BinVector_wq * next;
  int headIndex;
  int tailIndex;
} WaitingQueueBinVector;


typedef struct BinElement_wq {
  struct BinVector_wq * head;
  struct BinVector_wq * tail;
  int size;
} WaitingQueueBin;


//TODO in the future, remove this struct all together
//struct WaitingQueue {
//  struct BinElement_wq * array;
//};

void putWaitingQueue(int allocSiteID, WaitingQueueBin * queue, int effectType, void * resumePtr, int traverserID);
int isEmptyForWaitingQ(WaitingQueueBin * queue, int allocSiteID);
WaitingQueueBin * mallocWaitingQueue(int size);
WaitingQueueBinVector * returnVectorToFreePool(struct BinVector_wq *ptr);
int removeFromQueue(WaitingQueueBin * queue, int allocSiteID, int TraverserID);
//int resolveWaitingQueueChain(struct WaitingQueue * queue, int allocSiteID, int traverserID);
WaitingQueueBinVector * mallocNewVector();
WaitingQueueBinVector * getUsableVector();

//TODO this only a debug method GET RID OF IT WHEN DONE!!
WaitingQueueBinVector * debug_GetTheFreeBinsPtr();
#endif /* WAITINGQUEUE_H_ */
