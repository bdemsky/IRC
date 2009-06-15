/* =============================================================================
 *
 * adtree.java
 *
 * =============================================================================
 *
 * Copyright (C) Stanford University, 2006.  All Rights Reserved.
 * Author: Chi Cao Minh
 *
 * =============================================================================
 *
 * Reference:
 *
 * A. Moore and M.-S. Lee. Cached sufficient statistics for efficient machine
 * learning with large datasets. Journal of Artificial Intelligence Research 8
 * (1998), pp 67-91.
 *
 * =============================================================================
 *
 * For the license of bayes/sort.h and bayes/sort.c, please see the header
 * of the files.
 * 
 * ------------------------------------------------------------------------
 * 
 * For the license of kmeans, please see kmeans/LICENSE.kmeans
 * 
 * ------------------------------------------------------------------------
 * 
 * For the license of ssca2, please see ssca2/COPYRIGHT
 * 
 * ------------------------------------------------------------------------
 * 
 * For the license of lib/mt19937ar.c and lib/mt19937ar.h, please see the
 * header of the files.
 * 
 * ------------------------------------------------------------------------
 * 
 * For the license of lib/rbtree.h and lib/rbtree.c, please see
 * lib/LEGALNOTICE.rbtree and lib/LICENSE.rbtree
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
 /*
#include <assert.h>
#include <stdlib.h>
#include "adtree.h"
#include "data.h"
#include "query.h"
#include "utility.h"
#include "vector.h"
*/

public class Adtree {
  int numVar;
  int numRecord;
  AdtreeNode rootNodePtr;

  public Adtree() {

  }

  /* =============================================================================
   * freeNode
   * =============================================================================
   */
  public void
    freeNode (AdtreeNode nodePtr)
    {
      nodePtr.varyVectorPtr.vector_free();
      nodePtr = null;
      //free(nodePtr);
    }



  /* =============================================================================
   * freeVary
   * =============================================================================
   */
  public void
    freeVary (AdtreeVary varyPtr)
    {
      varyPtr = null;
      //free(varyPtr);
    }


  /* =============================================================================
   * adtree_alloc
   * =============================================================================
   */
  public static Adtree adtree_alloc ()
    {
      Adtree adtreePtr = new Adtree();
      //adtree_t* adtreePtr;
      //adtreePtr = (adtree_t*)malloc(sizeof(adtree_t));
      if (adtreePtr != null) {
        adtreePtr.numVar = -1;
        adtreePtr.numRecord = -1;
        adtreePtr.rootNodePtr = null;
      }

      return adtreePtr;
    }


  /* =============================================================================
   * freeNodes
   * =============================================================================
   */
  public void
    freeNodes (AdtreeNode nodePtr)
    {
      if (nodePtr != null) {
        Vector_t varyVectorPtr = nodePtr.varyVectorPtr;
        //vector_t* varyVectorPtr = nodePtr.varyVectorPtr;
        //int v;
        int numVary = varyVectorPtr.vector_getSize();
        for (int v = 0; v < numVary; v++) {
          AdtreeVary varyPtr = (AdtreeVary)(varyVectorPtr.vector_at(v));
          //adtree_vary_t* varyPtr = (adtree_vary_t*)vector_at(varyVectorPtr, v);
          freeNodes(varyPtr.zeroNodePtr);
          freeNodes(varyPtr.oneNodePtr);
          freeVary(varyPtr);
        }
        freeNode(nodePtr);
      }
    }


  /* =============================================================================
   * adtree_free
   * =============================================================================
   */
  public void
    adtree_free ()
    {
      freeNodes(rootNodePtr);
      this = null;
      //free(adtreePtr);
    }


  /*
  static adtree_vary_t*
    makeVary (int parentIndex,
        int index,
        int start,
        int numRecord,
        Data* dataPtr);

  static adtree_node_t*
    makeNode (int parentIndex,
        int index,
        int start,
        int numRecord,
        Data* dataPtr);
        */


  /* =============================================================================
   * makeVary
   * =============================================================================
   */
  public AdtreeVary
    makeVary (int parentIndex,
        int index,
        int start,
        int numRecord,
        Data dataPtr)
    {
      AdtreeVary varyPtr = AdtreeVary.allocVary(index);
      //assert(varyPtr);

      if ((parentIndex + 1 != index) && (numRecord > 1)) {
        dataPtr.data_sort(start, numRecord, index);
      }

      int num0 = dataPtr.data_findSplit(start, numRecord, index);
      int num1 = numRecord - num0;

      int mostCommonValue = ((num0 >= num1) ? 0 : 1);
      varyPtr.mostCommonValue = mostCommonValue;

      if (num0 == 0 || mostCommonValue == 0) {
        varyPtr.zeroNodePtr = null;
      } else {
        varyPtr.zeroNodePtr =
          makeNode(index, index, start, num0, dataPtr);
        varyPtr.zeroNodePtr.value = 0;
      }

      if (num1 == 0 || mostCommonValue == 1) {
        varyPtr.oneNodePtr = null;
      } else {
        varyPtr.oneNodePtr =
          makeNode(index, index, (start + num0), num1, dataPtr);
        varyPtr.oneNodePtr.value = 1;
      }

      return varyPtr;
    }


  /* =============================================================================
   * makeNode
   * =============================================================================
   */
  public AdtreeNode
    makeNode (int parentIndex,
        int index,
        int start,
        int numRecord,
        Data dataPtr)
    {
      AdtreeNode nodePtr = AdtreeNode.allocNode(index);
      //adtree_node_t* nodePtr = allocNode(index);
      //assert(nodePtr);

      nodePtr.count = numRecord;

      Vector_t varyVectorPtr = nodePtr.varyVectorPtr;

      //vector_t* varyVectorPtr = nodePtr.varyVectorPtr;

      //int v;
      int numVar = dataPtr.numVar;
      for (int v = (index + 1); v < numVar; v++) {
        AdtreeVary varyPtr =
        //adtree_vary_t* varyPtr =
          makeVary(parentIndex, v, start, numRecord, dataPtr);
        //assert(varyPtr);
        boolean status;
        if((status = varyVectorPtr.vector_pushBack(varyPtr)) != true) {
          System.out.println("varyVectorPtr.vector_pushBack != true");
          System.exit(0);
        }
        //assert(status);
      }

      return nodePtr;
    }


  /* =============================================================================
   * adtree_make
   * -- Records in dataPtr will get rearranged
   * =============================================================================
   */
  public void
    adtree_make (Data dataPtr)
    {
      int numRecord = dataPtr.numRecord;
      numVar = dataPtr.numVar;
      numRecord = dataPtr.numRecord;
      dataPtr.data_sort(0, numRecord, 0);
      rootNodePtr = makeNode(-1, -1, 0, numRecord, dataPtr);
    }


  /* =============================================================================
   * getCount
   * =============================================================================
   */
  public int
    getCount (AdtreeNode nodePtr,
        int i,
        int q,
        Vector_t queryVectorPtr,
        int lastQueryIndex)
    {
      if (nodePtr == null) {
        return 0;
      }

      int nodeIndex = nodePtr.index;
      if (nodeIndex >= lastQueryIndex) {
        return nodePtr.count;
      }

      int count = 0;

      Query queryPtr = (Query)(queryVectorPtr.vector_at(q));

      //query_t* queryPtr = (query_t*)vector_at(queryVectorPtr, q);
      if (queryPtr != null) {
        return nodePtr.count;
      }
      int queryIndex = queryPtr.index;
      if(queryIndex > lastQueryIndex) {
        System.out.println("Assert failed");
        System.exit(0);
      }
      //assert(queryIndex <= lastQueryIndex);
      Vector_t varyVectorPtr = nodePtr.varyVectorPtr;
      //vector_t* varyVectorPtr = nodePtr.varyVectorPtr;
      AdtreeVary varyPtr = (AdtreeVary)(varyVectorPtr.vector_at((queryIndex - nodeIndex - 1)));
      //adtree_vary_t* varyPtr =
      //  (adtree_vary_t*)vector_at(varyVectorPtr,
      //      (queryIndex - nodeIndex - 1));
      //assert(varyPtr);

      int queryValue = queryPtr.value;

      if (queryValue == varyPtr.mostCommonValue) {

        /*
         * We do not explicitly store the counts for the most common value.
         * We can calculate it by finding the count of the query without
         * the current (superCount) and subtracting the count for the
         * query with the current toggled (invertCount).
         */
        int numQuery = queryVectorPtr.vector_getSize();
        Vector_t superQueryVectorPtr = Vector_t.vector_alloc(numQuery - 1);
        //vector_t* superQueryVectorPtr = PVECTOR_ALLOC(numQuery - 1); //MEMORY ALLOACATED FROM THREAD POOL
        //assert(superQueryVectorPtr);

        //int qq;
        for (int qq = 0; qq < numQuery; qq++) {
          if (qq != q) {
            boolean status = superQueryVectorPtr.vector_pushBack(
                queryVectorPtr.vector_at(qq));
            //assert(status);
          }
        }
        int superCount = adtree_getCount(superQueryVectorPtr);

        superQueryVectorPtr.vector_free();
        //PVECTOR_FREE(superQueryVectorPtr);

        int invertCount;
        if (queryValue == 0) {
          queryPtr.value = 1;
          invertCount = getCount(nodePtr,
              i,
              q,
              queryVectorPtr,
              lastQueryIndex);
          queryPtr.value = 0;
        } else {
          queryPtr.value = 0;
          invertCount = getCount(nodePtr,
              i,
              q,
              queryVectorPtr,
              lastQueryIndex);
          queryPtr.value = 1;
        }
        count += superCount - invertCount;

      } else {

        if (queryValue == 0) {
          count += getCount(varyPtr.zeroNodePtr,
              (i + 1),
              (q + 1),
              queryVectorPtr,
              lastQueryIndex);
        } else if (queryValue == 1) {
          count += getCount(varyPtr.oneNodePtr,
              (i + 1),
              (q + 1),
              queryVectorPtr,
              lastQueryIndex);
        } else { /* QUERY_VALUE_WILDCARD */
          /*
#if 0
          count += getCount(varyPtr.zeroNodePtr,
              (i + 1),
              (q + 1),
              queryVectorPtr,
              lastQueryIndex);
          count += getCount(varyPtr.oneNodePtr,
              (i + 1),
              (q + 1),
              queryVectorPtr,
              lastQueryIndex,
              adtreePtr);
#else
          assert(0); // catch bugs in learner 
#endif
          */
        }

      }

      return count;
    }


  /* =============================================================================
   * adtree_getCount
   * -- queryVector must consist of queries sorted by id
   * =============================================================================
   */
  public int
    adtree_getCount (Vector_t queryVectorPtr)
    {
      //AdtreeNode rootNodePtr = adtreePtr.rootNodePtr;
      if (rootNodePtr == null) {
        return 0;
      }

      int lastQueryIndex = -1;
      int numQuery = queryVectorPtr.vector_getSize();
      if (numQuery > 0) {
        Query lastQueryPtr = (Query)(queryVectorPtr.vector_at(numQuery - 1));
        //query_t* lastQueryPtr = (query_t*)vector_at(queryVectorPtr, (numQuery - 1));
        lastQueryIndex = lastQueryPtr.index;
      }

      return getCount(rootNodePtr,
          -1,
          0,
          queryVectorPtr,
          lastQueryIndex);
    }


  /* #############################################################################
   * TEST_ADTREE
   * #############################################################################
   */
  /*
#ifdef TEST_ADTREE

#include <stdio.h>
#include "timer.h"

  static void printNode (adtree_node_t* nodePtr);
  static void printVary (adtree_vary_t* varyPtr);

  boolean global_doPrint = FALSE;


  static void
    printData (Data* dataPtr)
    {
      int numVar = dataPtr.numVar;
      int numRecord = dataPtr.numRecord;

      int r;
      for (r = 0; r < numRecord; r++) {
        printf("%4li: ", r);
        char* record = data_getRecord(dataPtr, r);
        assert(record);
        int v;
        for (v = 0; v < numVar; v++) {
          printf("%li", (int)record[v]);
        }
        puts("");
      }
    }


  static void
    printNode (adtree_node_t* nodePtr)
    {
      if (nodePtr) {
        printf("[node] index=%li value=%li count=%li\n",
            nodePtr.index, nodePtr.value, nodePtr.count);
        vector_t* varyVectorPtr = nodePtr.varyVectorPtr;
        int v;
        int numVary = vector_getSize(varyVectorPtr);
        for (v = 0; v < numVary; v++) {
          adtree_vary_t* varyPtr = (adtree_vary_t*)vector_at(varyVectorPtr, v);
          printVary(varyPtr);
        }
      }
      puts("[up]");
    }


  static void
    printVary (adtree_vary_t* varyPtr)
    {
      if (varyPtr) {
        printf("[vary] index=%li\n", varyPtr.index);
        printNode(varyPtr.zeroNodePtr);
        printNode(varyPtr.oneNodePtr);
      }
      puts("[up]");
    }


  static void
    printAdtree (adtree_t* adtreePtr)
    {
      printNode(adtreePtr.rootNodePtr);
    }


  static void
    printQuery (vector_t* queryVectorPtr)
    {
      printf("[");
      int q;
      int numQuery = vector_getSize(queryVectorPtr);
      for (q = 0; q < numQuery; q++) {
        query_t* queryPtr = (query_t*)vector_at(queryVectorPtr, q);
        printf("%li:%li ", queryPtr.index, queryPtr.value);
      }
      printf("]");
    }


  static int
    countData (Data* dataPtr, vector_t* queryVectorPtr)
    {
      int count = 0;
      int numQuery = vector_getSize(queryVectorPtr);

      int r;
      int numRecord = dataPtr.numRecord;
      for (r = 0; r < numRecord; r++) {
        char* record = data_getRecord(dataPtr, r);
        boolean isMatch = TRUE;
        int q;
        for (q = 0; q < numQuery; q++) {
          query_t* queryPtr = (query_t*)vector_at(queryVectorPtr, q);
          int queryValue = queryPtr.value;
          if ((queryValue != QUERY_VALUE_WILDCARD) &&
              ((char)queryValue) != record[queryPtr.index])
          {
            isMatch = FALSE;
            break;
          }
        }
        if (isMatch) {
          count++;
        }
      }

      return count;
    }


  static void
    testCount (adtree_t* adtreePtr,
        Data* dataPtr,
        vector_t* queryVectorPtr,
        int index,
        int numVar)
    {
      if (index >= numVar) {
        return;
      }

      int count1 = adtree_getCount(adtreePtr, queryVectorPtr);
      int count2 = countData(dataPtr, queryVectorPtr);
      if (global_doPrint) {
        printQuery(queryVectorPtr);
        printf(" count1=%li count2=%li\n", count1, count2);
        fflush(stdout);
      }
      assert(count1 == count2);

      query_t query;

      int i;
      for (i = 1; i < numVar; i++) {
        query.index = index + i;
        boolean status = vector_pushBack(queryVectorPtr, (void*)&query);
        assert(status);

        query.value = 0;
        testCount(adtreePtr, dataPtr, queryVectorPtr, query.index, numVar);

        query.value = 1;
        testCount(adtreePtr, dataPtr, queryVectorPtr, query.index, numVar);

        vector_popBack(queryVectorPtr);
      }
    }


  static void
    testCounts (adtree_t* adtreePtr, Data* dataPtr)
    {
      int numVar = dataPtr.numVar;
      vector_t* queryVectorPtr = vector_alloc(numVar);
      int v;
      for (v = -1; v < numVar; v++) {
        testCount(adtreePtr, dataPtr, queryVectorPtr, v, dataPtr.numVar);
      }
      vector_free(queryVectorPtr);
    }


  static void
    test (int numVar, int numRecord)
    {
      random_t* randomPtr = random_alloc();
      Data* dataPtr = data_alloc(numVar, numRecord, randomPtr);
      assert(dataPtr);
      data_generate(dataPtr, 0, 10, 10);
      if (global_doPrint) {
        printData(dataPtr);
      }

      Data* copyDataPtr = data_alloc(numVar, numRecord, randomPtr);
      assert(copyDataPtr);
      data_copy(copyDataPtr, dataPtr);

      adtree_t* adtreePtr = adtree_alloc();
      assert(adtreePtr);

      TIMER_T start;
      TIMER_READ(start);

      adtree_make(adtreePtr, copyDataPtr);

      TIMER_T stop;
      TIMER_READ(stop);

      printf("%lf\n", TIMER_DIFF_SECONDS(start, stop));

      if (global_doPrint) {
        printAdtree(adtreePtr);
      }

      testCounts(adtreePtr, dataPtr);

      adtree_free(adtreePtr);
      random_free(randomPtr);
      data_free(dataPtr);
    }


  int
    main ()
    {
      puts("Starting...");

      puts("Test 1:");
      test(3, 8);

      puts("Test 2:");
      test(4, 64);

      puts("Test 3:");
      test(8, 256);

      puts("Test 4:");
      test(12, 256);

      puts("Test 5:");
      test(48, 1024);

      puts("All tests passed.");

      return 0;
    }


#endif // TEST_ADTREE
  */

}
/* =============================================================================
 *
 * End of adtree.c
 *
 * =============================================================================
 */
