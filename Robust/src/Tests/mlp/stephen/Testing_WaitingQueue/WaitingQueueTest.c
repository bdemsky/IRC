/*
 * WaitingQueueTest.c
 *
 *  Created on: Sep 25, 2010
 *      Author: stephey
 */

#include "WaitingQueue.h"
#include "mlp_lock.h"
#include "RuntimeConflictResolver.h"
#include <stdio.h>
#include <stdlib.h>
//Make sure you only test ONE thing at a time.
void testMalloc(int maxTests) {
  printf("Testing malloc from 1 to %u\n", maxTests);
  int i = 1;
  WaitingQueueBin * array[maxTests];

  for(i = 0; i < maxTests; i++) {
    array[i] = NULL;
  }

  for(i = 0; i <= maxTests; i++) {
    array[i-1] = mallocWaitingQueue(i);
  }

  for(i = 0; i <maxTests; i++) {
    if(array[i] == NULL) {
      printf("Crap, it didn't work somewhere at index %u\n", i);
    }
  }
}

//P.S. make sure this is the FIRST and ONLY thing to run if you want to test this.
void testWaitingQueueFreeBinsSingleTest() {
  WaitingQueueBinVector * ptr = getUsableWaitingQueueBinVector();

  if(ptr == NULL) {
    printf("waitingQueueBinVector didn't work ><\n");
  }

  if(debug_GetTheFreeBinsPtr() != NULL) {
    printf("either testWaitingQueueFreeBins wasn't called first or somehow it's not null....");
  }

  if(returnWaitingQueueBinVectorToFreePool(ptr) != NULL) {
    printf("Returning the .next in the waiting queue didn't quite work...");
  }

  if(debug_GetTheFreeBinsPtr() != ptr) {
    printf("The old ptr wasn't returned into the free bins pool");
  }
}

void waitingQueuePutAndRemoveTestSingle() {
  WaitingQueueBin * waitingQueue = mallocWaitingQueue(200);
  int i;

  for(i = 0; i < 200; i++) {
    if(!isEmptyForWaitingQ(waitingQueue, i)) {
      printf("Waiting Queue is not empty at Bin %u of %u\n", i, 200);
    }

    if(waitingQueue[i].head != NULL || waitingQueue[i].size != 0  || waitingQueue[i].tail !=NULL){
      printf("Something was initialized incorrectly at bin %u of %u\n", i, 200);
    }
  }

  //void putWaintingQueue(int allocSiteID, WaitingQueueBin * queue, int effectType, void * resumePtr, int traverserID);
  putIntoWaitingQueue(100, waitingQueue, 1, 2, 3);

  if(isEmptyForWaitingQ(waitingQueue, 100)) {
    printf("The one item added at location %u did not actually get put there according to isEmpty\n", 100);
  }

  if(waitingQueue[100].head == NULL || waitingQueue[100].size != 1 || waitingQueue[100].tail != waitingQueue[100].head) {
    printf("Add information contained within the bin appears to be incorrect\n");
  }

  TraverserResumeDataFromWaitingQ * td = &(waitingQueue[100].head->array[waitingQueue[100].head->headIndex]);

  if(td->effectType != 1 || td->resumePtr != 2 || td->traverserID != 3) {
    printf("Something went wrong in putting the item into the waitingQueue\n");
  }

  for(i = 0; i < 200; i++) {
    if(i != 100) {
      if(!isEmptyForWaitingQ(waitingQueue, i)) {
        printf("Waiting Queue is not empty at Bin %u of %u\n", i, 200);
      }

      if(waitingQueue[i].head != NULL || waitingQueue[i].size != 0  || waitingQueue[i].tail !=NULL){
        printf("Something was initialized incorrectly at bin %u of %u\n", i, 200);
      }
    }
  }

  //int removeFromQueue(WaitingQueueBin * wQueue, int allocSiteID, int TraverserID)
  if(removeFromWaitingQueue(waitingQueue, 100, 3) != 1) {
    printf("it appears that removing doens't remove the correct # of items\n");
  }

  if(waitingQueue[100].head == NULL || waitingQueue[100].size != 0 || waitingQueue[100].tail != waitingQueue[100].head) {
      printf("Remove information contained within the bin appears to be incorrect\n");
    }

  printf("just ran waitingQueuePutTestSingle()\n");

}

void waitingQueuePutAndRemoveTestMulti() {
  WaitingQueueBin * waitingQueue = mallocWaitingQueue(200);
  int i;

  //add 2 more
  //void putWaintingQueue(int allocSiteID, WaitingQueueBin * queue, int effectType, void * resumePtr, int traverserID);
  putIntoWaitingQueue(100, waitingQueue, 1, 2, 4);
  putIntoWaitingQueue(100, waitingQueue, 1, 2, 4);
  putIntoWaitingQueue(100, waitingQueue, 1, 2, 5);

  for(i = 0; i < 200; i++) {
    if(i != 100) {
      if(!isEmptyForWaitingQ(waitingQueue, i)) {
        printf("Waiting Queue is not empty at Bin %u of %u\n", i, 200);
      }

      if(waitingQueue[i].head != NULL || waitingQueue[i].size != 0  || waitingQueue[i].tail !=NULL){
        printf("Something was initialized incorrectly at bin %u of %u\n", i, 200);
      }
    }
  }

  if(waitingQueue[100].head == NULL || waitingQueue[100].size != 2 || waitingQueue[100].tail != waitingQueue[100].head) {
    printf("Add information contained within the bin appears to be incorrect (this is add 3 with 2 duplicates)\n");
  }

//  //Return is how many things are removed. -1 would indicate error
//  int removeFromQueue(WaitingQueueBin * wQueue, int allocSiteID, int TraverserID)

  if(removeFromWaitingQueue(waitingQueue, 101,0) != -1) {
    printf("failsafe does not work in removeFromQueue\n");
  }

  if(removeFromWaitingQueue(waitingQueue, 100, 29038) != 0 || removeFromWaitingQueue(waitingQueue, 100, 5) != 0) {
    printf("removeFromQueue does not check element's traverserID before removing");
  }

  if(removeFromWaitingQueue(waitingQueue, 100, 4) != 1 || waitingQueue[100].size != 1) {
    printf("removeFromQueue didn't remove items and/or didn't decrement counter correctly 1\n");
  }

  if(removeFromWaitingQueue(waitingQueue, 100, 4) != 0 || waitingQueue[100].size != 1) {
    printf("removeFromQueue didn't remove items and/or didn't decrement counter correctly 2\n");
  }

  if(removeFromWaitingQueue(waitingQueue, 99, 5) != -1 || waitingQueue[99].size != 0) {
    printf("failsafe in remove does not work correctly\n");
  }

  if(removeFromWaitingQueue(waitingQueue, 100, 5) != 1 || waitingQueue[100].size != 0 || !isEmptyForWaitingQ(waitingQueue, 100)) {
      printf("removeFromQueue didn't remove items and/or didn't decrement counter correctly 3\n");
  }

  //next try adding 10,000 items

  for(i = 0; i < 10000; i++) {
    putIntoWaitingQueue(100, waitingQueue, 1, 2, i);
  }

  if(isEmptyForWaitingQ(waitingQueue, 100)) {
    printf("isEmpty reports that queue is empty even though it's not\n");
  }

  if(waitingQueue[100].size != 10000) {
    printf("Some of the 10000 items were not added");
  }

  if(debug_GetTheFreeBinsPtr() != NULL) {
    printf("Program put something in freeBinsPtr even though it should have never touched it\n");
  }

  for(i = 0; i <10000; i++) {
    if(removeFromWaitingQueue(waitingQueue, 100, i) != 1) {
      printf("remove from 10000 didn't properly just remove ONE item");
    }

    if(waitingQueue[100].size != 10000 - i -1) {
      printf("counter did not properly decrement itself in 10000 remove\n");
    }
  }

  if(waitingQueue[100].size != 0 ) {
    printf("Something went wrong and after 10000 removes, the size is not 0\n");
  }

  if(waitingQueue[100].head == NULL || waitingQueue[100].head != waitingQueue[100].tail) {
    printf("head tail pointers in bin element is not properly aligned after 10000 remove\n");
  }

  if(debug_GetTheFreeBinsPtr() == NULL || debug_GetTheFreeBinsPtr()->next == NULL || debug_GetTheFreeBinsPtr()->next->next == NULL) {
    printf("either the numbins constant is really high or things aren't being put into the freeBinsPtr correctly\n");
  }

  printf("Just ran waitingQueuePutAndRemoveTestMulti()\n");
}

void testWaitingQueueFreeBinsMultipleTest(int size) {
  WaitingQueueBinVector ** ptrs = malloc(sizeof(WaitingQueueBinVector *) * size);
  int i;

  for(i = 0; i < size; i++) {
    ptrs[i] = getUsableWaitingQueueBinVector();
    ptrs[i]->tailIndex = 293847; //this is to make sure we don't get a segmentation fault

    if(ptrs[i]->next != NULL || ptrs[i]->tailIndex != 293847) {
      printf("either something wasn't initialized correctly or we are given a fake pointer at getUsableVector()\n");
    }

    if(debug_GetTheFreeBinsPtr() != NULL) {
      printf("somehow they got to the freebins pool at index %u\n", i);
    }
  }

  for(i = 0; i < size; i++) {
    returnWaitingQueueBinVectorToFreePool(ptrs[i]);
    if(debug_GetTheFreeBinsPtr() != ptrs[i]) {
      printf("it appears that the returnVectorToFreePool didn't put the vector at the front at index %u\n", i);
    }
  }

  for(i = size-1; i>= 0; i--) {
    if(getUsableWaitingQueueBinVector() != ptrs[i]) {
      printf("getUsableVector does not get the correct one at index %u\n", i);
    }
  }

  if(debug_GetTheFreeBinsPtr() != NULL) {
    printf("Apparently our free loop didn't free everything correctly\n");
  }

  printf("I just tested testWaitingQueueFreeBinsMultipleTest(%u);\n", size);
}

//This test was because I wanted to make my own lockxchng method
void testLOCKXCHG() {
  int a = 5;
  int * aPtr = &a;
  int * lock = (int *) 0x1;

  printf("lockxchg test\n");
  lock = LOCKXCHG(&aPtr, lock);

  if(aPtr =! 0x1) {
    printf("lock failed\n");
  }

  if(lock != &a) {
    printf("The revtal to LOCKXCHG isn't correct; return = %p; supposed to be %p\n", lock, &a);
  }
}

int main() {
  testWaitingQueueFreeBinsMultipleTest(1000);
  return 1;
}
