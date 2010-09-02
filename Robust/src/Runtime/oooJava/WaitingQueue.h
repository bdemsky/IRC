/*
 * waitingQueue.h
 *
 *  Created on: Sep 1, 2010
 *      Author: stephey
 */
#ifndef WAITINGQUEUE_H_
#define WAITINGQUEUE_H_

#define NUMITEMS 20

/* print header */
struct TraverserData {
  void * resumePtr;
  int traverserID;
  int effectType;
};

struct BinVector {
  struct TraverserData array[NUMITEMS];
  struct BinVector * next;
  int index;
};

struct BinElement {
  struct BinVector * head;
  struct BinVector * tail;
  int size;
};


//TODO in the future, remove this struct all together
struct WaitingQueue {
  struct BinElement * array;
};

void put(int allocSiteID, struct WaitingQueue * queue, int effectType, void * resumePtr, int traverserID);
int check(struct WaitingQueue * queue, int allocSiteID);
struct WaitingQueue * mallocWaitingQueue(int size);
void returnVectorToFreePool(struct BinVector *ptr);
void resolveChain(struct WaitingQueue * queue, int allocSiteID);
struct BinVector * mallocNewVector();
struct BinVector * getUsableVector();
struct BinVector * getUsableVector();

#endif /* WAITINGQUEUE_H_ */
