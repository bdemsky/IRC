/*
 * waitingQueue.h
 *
 *  Created on: Sep 1, 2010
 *      Author: stephey
 */
#ifndef WAITINGQUEUE_H_
#define WAITINGQUEUE_H_

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

void putIntoWaitingQueue(int allocSiteID, WaitingQueueBin * queue, int effectType, void * resumePtr, int traverserID);
int isEmptyForWaitingQ(WaitingQueueBin * queue, int allocSiteID);
WaitingQueueBin * mallocWaitingQueue(int size);
WaitingQueueBinVector * returnWaitingQueueBinVectorToFreePool(struct BinVector_wq *ptr);
int removeFromWaitingQueue(WaitingQueueBin * queue, int allocSiteID, int TraverserID);
WaitingQueueBinVector * mallocNewWaitingQueueBinVector();
WaitingQueueBinVector * getUsableWaitingQueueBinVector();

#endif
