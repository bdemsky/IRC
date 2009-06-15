/* =============================================================================
 *
 * data.java
 *
 * =============================================================================
 *
 * Copyright (C) Stanford University, 2006.  All Rights Reserved.
 * Author: Chi Cao Minh
 *
 * =============================================================================
 *
 * For the license of bayes/sort.h and bayes/sort.c, please see the header
 * of the files.
 * 
 * ------------------------------------------------------------------------
 * 
 * Unless otherwise noted, the following license applies to STAMP files:
 * 
 * Copyright (c) 2007, Stanford University
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * 
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 * 
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 * 
 *     * Neither the name of Stanford University nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY STANFORD UNIVERSITY ``AS IS'' AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL STANFORD UNIVERSITY BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 *
 * =============================================================================
 */

#define DATA_PRECISION    100
#define DATA_INIT         2 /* not 0 or 1 */

public class Data {
  int numVar;
  int numRecord;
  char[] records; /* coordination of all records */
  Random randomPtr;

  public Data() {
  }

  /* =============================================================================
   * data_alloc
   * =============================================================================
   */
  public static Data data_alloc (int numVar, int numRecord, Random randomPtr)
  {
    //data_t* dataPtr;
    Data dataPtr = new Data();

    //dataPtr = (data_t*)malloc(sizeof(data_t));
    //if (dataPtr) {
    int numDatum = numVar * numRecord;
    dataPtr.records = new char[numDatum];
    for(int i = 0; i<numDatum; i++)
      dataPtr.records[i] = (char)DATA_INIT;

    dataPtr.numVar = numVar;
    dataPtr.numRecord = numRecord;
    dataPtr.randomPtr = randomPtr;

    //memset(dataPtr.records, DATA_INIT, (numDatum * sizeof(char)));
    //dataPtr.numVar = numVar;
    //dataPtr.numRecord = numRecord;
    //dataPtr.randomPtr = randomPtr;
    //}

    return dataPtr;
  }


  /* =============================================================================
   * data_free
   * =============================================================================
   */
  void
    data_free ()
    {
      records = null;
    }

  /* =============================================================================
   * data_generate
   * -- Binary variables of random PDFs
   * -- If seed is <0, do not reseed
   * -- Returns random network
   * =============================================================================
   */
  public Net data_generate (int seed, int maxNumParent, int percentParent)
  {
    //Random randomPtr = dataPtr.randomPtr;
    if (seed >= 0) {
      randomPtr.random_seed(seed);
    }

    /*
     * Generate random Bayesian network
     */

    //int numVar = dataPtr.numVar;
    Net netPtr = Net.net_alloc(numVar);
    //assert(netPtr);
    netPtr.net_generateRandomEdges(maxNumParent, percentParent, randomPtr);

    /*
     * Create a threshold for each of the possible permutation of variable
     * value instances
     */

    //int** thresholdsTable = (int**)malloc(numVar * sizeof(int*));
    //int[][] thresholdsTable = new int[numVar][numThreshold];
    int[][] thresholdsTable = new int[numVar][];
    //assert(thresholdsTable);
    int v;
    for (v = 0; v < numVar; v++) {
      //list_t* parentIdListPtr = Net.net_getParentIdListPtr(netPtr, v);
      IntList parentIdListPtr = netPtr.net_getParentIdListPtr(v);
      int numThreshold = 1 << parentIdListPtr.list_getSize();
      //int numThreshold = 1 << list_getSize(parentIdListPtr);
      int[] thresholds = new int[numThreshold];
      //int* thresholds = (int*)malloc(numThreshold * sizeof(int));
      //assert(thresholds);
      //int t;
      for (int t = 0; t < numThreshold; t++) {
        int threshold = randomPtr.random_generate() % (DATA_PRECISION + 1);
        thresholds[t] = threshold;
      }
      thresholdsTable[v] = thresholds;
    }

    /*
     * Create variable dependency ordering for record generation
     */

    int[] order = new int[numVar];
    //int* order = (int*)malloc(numVar * sizeof(int));
    //assert(order);
    int numOrder = 0;

    Queue workQueuePtr = Queue.queue_alloc(-1);
    //queue_t* workQueuePtr = queue_alloc(-1);
    //assert(workQueuePtr);

    IntVector dependencyVectorPtr = IntVector.vector_alloc(1);
    //vector_t* dependencyVectorPtr = vector_alloc(1);
    //assert(dependencyVectorPtr);

    BitMap orderedBitmapPtr = BitMap.bitmap_alloc(numVar);
    //bitmap_t* orderedBitmapPtr = bitmap_alloc(numVar);
    //assert(orderedBitmapPtr);
    orderedBitmapPtr.bitmap_clearAll();

    BitMap doneBitmapPtr = BitMap.bitmap_alloc(numVar);
    //bitmap_t* doneBitmapPtr = bitmap_alloc(numVar);
    //assert(doneBitmapPtr);
    doneBitmapPtr.bitmap_clearAll();
    //bitmap_clearAle(doneBitmapPtr);
    v = -1;
    //while ((v = bitmap_findClear(doneBitmapPtr, (v + 1))) >= 0) 
    while ((v = doneBitmapPtr.bitmap_findClear(v + 1)) >= 0) {
      IntList childIdListPtr = netPtr.net_getChildIdListPtr(v);
      //list_t* childIdListPtr = net_getChildIdListPtr(netPtr, v);
      int numChild = childIdListPtr.list_getSize();
      if (numChild == 0) {

        boolean status;

        /*
         * Use breadth-first search to find net connected to this leaf
         */

        workQueuePtr.queue_clear();
        if((status = workQueuePtr.queue_push(v)) != true) {
          System.out.println("status= "+ status + "should be true");
          System.exit(0);
        }
        //assert(status);
        while (!(workQueuePtr.queue_isEmpty())) {
          int id = workQueuePtr.queue_pop();
          if((status = doneBitmapPtr.bitmap_set(id)) != true) {
            System.out.println("status= "+ status + "should be true");
            System.exit(0);
          }
          //assert(status);
          //CHECK THIS
          if((status = dependencyVectorPtr.vector_pushBack(id)) != true) {
            System.out.println("status= "+ status + "should be true");
            System.exit(0);
          }
          //assert(status);
          //list_t* parentIdListPtr = net_getParentIdListPtr(netPtr, id);
          //list_iter_t it;
          //list_iter_reset(&it, parentIdListPtr);
          IntList parentIdListPtr = netPtr.net_getParentIdListPtr(id);
          IntListNode it = parentIdListPtr.head;
          parentIdListPtr.list_iter_reset(it);
          //while (list_iter_hasNext(&it, parentIdListPtr)) 
          while (parentIdListPtr.list_iter_hasNext(it)) {
            it = it.nextPtr;
            //int parentId = (int)list_iter_next(&it, parentIdListPtr);
            int parentId = parentIdListPtr.list_iter_next(it);
            if((status = workQueuePtr.queue_push(parentId)) != true) {
              System.out.println("status= "+ status + "should be true");
              System.exit(0);
            }
            //assert(status);
          }
        }

        /*
         * Create ordering
         */

        //int i;
        int n = dependencyVectorPtr.vector_getSize();
        for (int i = 0; i < n; i++) {
          int id = dependencyVectorPtr.vector_popBack();
          if (!(orderedBitmapPtr.bitmap_isSet(id))) {
            orderedBitmapPtr.bitmap_set(id);
            order[numOrder++] = id;
          }
        }
      }
    }

    if(numOrder != numVar) {
      System.out.println("numVar should be equal to numOrder");
      System.exit(0);
    }
    //assert(numOrder == numVar);

    /*
     * Create records
     */

    char[] tmprecord = records;
    int startindex = 0;
    //int r;
    //int numRecord = dataPtr.numRecord;
    for (int r = 0; r < numRecord; r++) {
      //int o;
      for (int o = 0; o < numOrder; o++) {
        v = order[o];
        IntList parentIdListPtr = netPtr.net_getParentIdListPtr(v);
        //list_t* parentIdListPtr = net_getParentIdListPtr(netPtr, v);
        int index = 0;
        //list_iter_t it;
        //list_iter_reset(&it, parentIdListPtr);
        IntListNode it = parentIdListPtr.head;
        parentIdListPtr.list_iter_reset(it);
        while (parentIdListPtr.list_iter_hasNext(it)) {
          it = it.nextPtr;
          int parentId = parentIdListPtr.list_iter_next(it);
          int value = tmprecord[parentId];
          if(value == DATA_INIT) {
            System.out.println("value should be != DATA_INIT");
            System.exit(0);
          }
          //assert(value != DATA_INIT);
          index = (index << 1) + value;
        }
        int rnd = randomPtr.random_generate() % DATA_PRECISION;
        int threshold = thresholdsTable[v][index];
        tmprecord[v] = (char) ((rnd < threshold) ? 1 : 0);
      }
      //record += numVar;
      startindex += numVar;
      if(startindex > numRecord * numVar) {
        System.out.println("value should be != DATA_INIT in data_generate()");
        System.exit(0);
      }
      //assert(record <= (dataPtr.records + numRecord * numVar));
    }

    /*
     * Clean up
     */

    doneBitmapPtr.bitmap_free();
    orderedBitmapPtr.bitmap_free();
    dependencyVectorPtr.vector_free();
    workQueuePtr.queue_free();
    order = null;
    //bitmap_free(doneBitmapPtr);
    //bitmap_free(orderedBitmapPtr);
    //vector_free(dependencyVectorPtr);
    //queue_free(workQueuePtr);
    //free(order);
    for (v = 0; v < numVar; v++) {
      thresholdsTable[v] = null;
      //free(thresholdsTable[v]);
    }
    thresholdsTable = null;
    //free(thresholdsTable);

    return netPtr;
  }


  /* =============================================================================
   * data_getRecord
   * -- Returns null if invalid index
   * =============================================================================
   */
  /*
  char*
    data_getRecord (data_t* dataPtr, int index)
    {
      if (index < 0 || index >= (dataPtr.numRecord)) {
        return null;
      }

      return &dataPtr.records[index * dataPtr.numVar];
    }
    */


  /* =============================================================================
   * data_copy
   * -- Returns false on failure
   * =============================================================================
   */
  public static boolean
    data_copy (Data dstPtr, Data srcPtr)
    {
      int numDstDatum = dstPtr.numVar * dstPtr.numRecord;
      int numSrcDatum = srcPtr.numVar * srcPtr.numRecord;
      if (numDstDatum != numSrcDatum) {
        dstPtr.records = null;
        //free(dstPtr.records);
        dstPtr.records = new char[numSrcDatum];
        //dstPtr.records = (char*)calloc(numSrcDatum, sizeof(char));
        if (dstPtr.records == null) {
          return false;
        }
      }

      dstPtr.numVar    = srcPtr.numVar;
      dstPtr.numRecord = srcPtr.numRecord;
      for(int i=0; i<numSrcDatum; i++)
        dstPtr.records[i] = srcPtr.records[i];
      //memcpy(dstPtr.records, srcPtr.records, (numSrcDatum * sizeof(char)));

      return true;
    }


  /* =============================================================================
   * compareRecord
   * =============================================================================
   */
  /*
  public static int
    compareRecord (const void* p1, const void* p2, int n, int offset)
    {
      int i = n - offset;
      const char* s1 = (const char*)p1 + offset;
      const char* s2 = (const char*)p2 + offset;

      while (i-- > 0) {
        unsigned char u1 = (unsigned char)*s1++;
        unsigned char u2 = (unsigned char)*s2++;
        if (u1 != u2) {
          return (u1 - u2);
        }
      }

      return 0;
    }
    */


  /* =============================================================================
   * data_sort
   * -- In place
   * =============================================================================
   */
  public void
    data_sort (int start,
        int num,
        int offset)
    {
      if((start < 0) || (start > numRecord))
        System.out.println("start: Assert failed in data_sort");
      if((num < 0) || (num > numRecord))
        System.out.println("num: Assert failed in data_sort");
      if((start + num < 0) || (start + num > numRecord))
        System.out.println("start + num: Assert failed in data_sort");

      //assert(start >= 0 && start <= dataPtr.numRecord);
      //assert(num >= 0 && num <= dataPtr.numRecord);
      //assert(start + num >= 0 && start + num <= dataPtr.numRecord);

      //int numVar = dataPtr.numVar;

      //FIXME
      //Sort.sort((dataPtr.records + (start * numVar)),
      Sort.sort(records, 
          start * numVar,
          num,
          numVar,
          numVar,
          offset);
    }


  /* =============================================================================
   * data_findSplit
   * -- Call data_sort first with proper start, num, offset
   * -- Returns number of zeros in offset column
   * =============================================================================
   */
  public int
    data_findSplit (int start, int num, int offset)
    {
      int low = start;
      int high = start + num - 1;

      //int numVar = dataPtr.numVar;
      //char* records = dataPtr.records;

      while (low <= high) {
        int mid = (low + high) / 2;
        if (records[numVar * mid + offset] == 0) {
          low = mid + 1;
        } else {
          high = mid - 1;
        }
      }

      return (low - start);
    }
}

/* =============================================================================
 *
 * End of data.java
 *
 * =============================================================================
 */
