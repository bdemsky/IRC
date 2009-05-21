/* =============================================================================
 *
 * computeGraph.java
 *
 * =============================================================================
 *
 * For the license of ssca2, please see ssca2/COPYRIGHT
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

public class ComputeGraph {
  public Graph GPtr;
  public GraphSDG SDGdataPtr;

  public int[] global_p;
  public int global_maxNumVertices;
  public int global_outVertexListSize;
  public int[] global_impliedEdgeList;
  public int[][] global_auxArr;

  public ComputeGraph() {
    global_p                 = null;
    global_maxNumVertices    = 0;
    global_outVertexListSize = 0;
    global_impliedEdgeList   = null;
    global_auxArr            = null;
  }

  public int NOSHARE(int x) {
    x = x << 7;
    return x;
  }

  /* =============================================================================
   * prefix_sums
   * =============================================================================
   */
  public void
    prefix_sums (int myId, int numThread, int[] result, int[] input, int arraySize)
    {
      int[]  p;
      if (myId == 0) {
        p = new int[NOSHARE(numThread)];
        global_p = p;
      }

      Barrier.enterBarrier();

      p = global_p;

      int start;
      int end;

      int r = arraySize / numThread;
      start = myId * r + 1;
      end = (myId + 1) * r;
      if (myId == (numThread - 1)) {
        end = arraySize;
      }

      for (int j =  start; j <  end; j++) {
        result[j] = input[j-1] + result[j-1];
      }

      p[NOSHARE(myId)] = result[end-1];

      Barrier.enterBarrier();

      if (myId == 0) {
        for (int j = 1; j < numThread; j++) {
          p[NOSHARE(j)] += p[NOSHARE(j-1)];
        }
      }

      Barrier.enterBarrier();

      if (myId > 0) {
        int add_value = p[NOSHARE(myId-1)];
        for (int j = start-1; j < end; j++) {
          result[j] += add_value;
        }
      }

      Barrier.enterBarrier();

      if (myId == 0) {
        p = null;
      }
    }


  /* =============================================================================
   * computeGraph
   * =============================================================================
   */
  public static void
    computeGraph (int myId, int numThread, Globals glb, ComputeGraph computeGraphArgs) 

    {
      Barrier.enterBarrier();

      //Graph GPtr = computeGraphArgs.GPtr;
      //GraphSDG SDGdataPtr = computeGraphArgs.SDGdata;

      //int j;
      int maxNumVertices = 0;
      int numEdgesPlaced = computeGraphArgs.SDGdataPtr.numEdgesPlaced;

      /*
       * First determine the number of vertices by scanning the tuple
       * startVertex list
       */
      LocalStartStop lss = new LocalStartStop();
      CreatePartition.createPartition(0, numEdgesPlaced, myId, numThread, lss);

      for (int i = lss.i_start; i < lss.i_stop; i++) {
        if (computeGraphArgs.SDGdataPtr.startVertex[i] > maxNumVertices) {
          maxNumVertices = computeGraphArgs.SDGdataPtr.startVertex[i];
        }
      }

      atomic {
        int tmp_maxNumVertices = computeGraphArgs.global_maxNumVertices;
        int new_maxNumVertices = CreatePartition.MAX( tmp_maxNumVertices, maxNumVertices) + 1;
        computeGraphArgs.global_maxNumVertices = new_maxNumVertices;
      }

      Barrier.enterBarrier();

      maxNumVertices = computeGraphArgs.global_maxNumVertices;

      if (myId == 0) {

        computeGraphArgs.GPtr.numVertices = maxNumVertices;
        computeGraphArgs.GPtr.numEdges    = numEdgesPlaced;
        computeGraphArgs.GPtr.intWeight   = computeGraphArgs.SDGdataPtr.intWeight;
        computeGraphArgs.GPtr.strWeight   = computeGraphArgs.SDGdataPtr.strWeight;

        for (int i = 0; i < numEdgesPlaced; i++) {
          if (computeGraphArgs.GPtr.intWeight[numEdgesPlaced-i-1] < 0) {
            computeGraphArgs.GPtr.numStrEdges = -(computeGraphArgs.GPtr.intWeight[numEdgesPlaced-i-1]) + 1;
            computeGraphArgs.GPtr.numIntEdges = numEdgesPlaced - computeGraphArgs.GPtr.numStrEdges;
            break;
          }
        }

        computeGraphArgs.GPtr.outDegree = new int[computeGraphArgs.GPtr.numVertices];

        computeGraphArgs.GPtr.outVertexIndex = new int[computeGraphArgs.GPtr.numVertices];
      }

      Barrier.enterBarrier();

      CreatePartition.createPartition(0, computeGraphArgs.GPtr.numVertices, myId, numThread, lss);

      for (int i = lss.i_start; i < lss.i_stop; i++) {
        computeGraphArgs.GPtr.outDegree[i] = 0;
        computeGraphArgs.GPtr.outVertexIndex[i] = 0;
      }

      int outVertexListSize = 0;

      Barrier.enterBarrier();

      int i0 = -1;

      for (int i = lss.i_start; i < lss.i_stop; i++) {
        int k = i;
        if ((outVertexListSize == 0) && (k != 0)) {
          while (i0 == -1) {
            for (int j = 0; j < numEdgesPlaced; j++) {
              if (k == computeGraphArgs.SDGdataPtr.startVertex[j]) {
                i0 = j;
                break;
              }

            }
            k--;
          }
        }

        if ((outVertexListSize == 0) && (k == 0)) {
          i0 = 0;
        }

        for (int j = i0; j < numEdgesPlaced; j++) {
          if (i == computeGraphArgs.GPtr.numVertices-1) {
            break;
          }
          if ((i != computeGraphArgs.SDGdataPtr.startVertex[j])) {
            if ((j > 0) && (i == computeGraphArgs.SDGdataPtr.startVertex[j-1])) {
              if (j-i0 >= 1) {
                outVertexListSize++;
                computeGraphArgs.GPtr.outDegree[i]++;
                for (int t = (i0+1); t < j; t++) {
                  if (computeGraphArgs.SDGdataPtr.endVertex[t] !=
                      computeGraphArgs.SDGdataPtr.endVertex[t-1])
                  {
                    outVertexListSize++;
                    computeGraphArgs.GPtr.outDegree[i] = computeGraphArgs.GPtr.outDegree[i]+1;
                  }
                }
              }
            }
            i0 = j;
            break;
          }
        }

        if (i == computeGraphArgs.GPtr.numVertices-1) {
          if (numEdgesPlaced-i0 >= 0) {
            outVertexListSize++;
            computeGraphArgs.GPtr.outDegree[i]++;
            for (int t =  (i0+1); t < numEdgesPlaced; t++) {
              if (computeGraphArgs.SDGdataPtr.endVertex[t] != computeGraphArgs.SDGdataPtr.endVertex[t-1]) {
                outVertexListSize++;
                computeGraphArgs.GPtr.outDegree[i]++;
              }
            }
          }
        }

      } /* for i */

      Barrier.enterBarrier();

      computeGraphArgs.prefix_sums(myId, numThread, computeGraphArgs.GPtr.outVertexIndex, computeGraphArgs.GPtr.outDegree, computeGraphArgs.GPtr.numVertices);

      Barrier.enterBarrier();

      atomic {
        computeGraphArgs.global_outVertexListSize = computeGraphArgs.global_outVertexListSize + outVertexListSize;
      }

      Barrier.enterBarrier();

      outVertexListSize = computeGraphArgs.global_outVertexListSize;

      if (myId == 0) {
        computeGraphArgs.GPtr.numDirectedEdges = outVertexListSize;
        computeGraphArgs.GPtr.outVertexList = new int[outVertexListSize];
        computeGraphArgs.GPtr.paralEdgeIndex = new int[outVertexListSize];
        computeGraphArgs.GPtr.outVertexList[0] = computeGraphArgs.SDGdataPtr.endVertex[0];
      }

      Barrier.enterBarrier();

      /*
       * Evaluate outVertexList
       */

      i0 = -1;

      for (int i = lss.i_start; i < lss.i_stop; i++) {

        int k =  i;
        while ((i0 == -1) && (k != 0)) {
          for (int j = 0; j < numEdgesPlaced; j++) {
            if (k == computeGraphArgs.SDGdataPtr.startVertex[j]) {
              i0 = j;
              break;
            }
          }
          k--;
        }

        if ((i0 == -1) && (k == 0)) {
          i0 = 0;
        }

        for (int j = i0; j < numEdgesPlaced; j++) {
          if (i == computeGraphArgs.GPtr.numVertices-1) {
            break;
          }
          if (i != computeGraphArgs.SDGdataPtr.startVertex[j]) {
            if ((j > 0) && (i == computeGraphArgs.SDGdataPtr.startVertex[j-1])) {
              if (j-i0 >= 1) {
                int ii =  (computeGraphArgs.GPtr.outVertexIndex[i]);
                int r = 0;
                computeGraphArgs.GPtr.paralEdgeIndex[ii] = i0;
                computeGraphArgs.GPtr.outVertexList[ii] = computeGraphArgs.SDGdataPtr.endVertex[ i0];
                r++;
                for (int t =  (i0+1); t < j; t++) {
                  if (computeGraphArgs.SDGdataPtr.endVertex[t] !=
                      computeGraphArgs.SDGdataPtr.endVertex[t-1])
                  {
                    computeGraphArgs.GPtr.paralEdgeIndex[ii+r] = t;
                    computeGraphArgs.GPtr.outVertexList[ii+r] = computeGraphArgs.SDGdataPtr.endVertex[t];
                    r++;
                  }
                }

              }
            }
            i0 = j;
            break;
          }
        } /* for j */

        if (i == computeGraphArgs.GPtr.numVertices-1) {
          int r = 0;
          if (numEdgesPlaced-i0 >= 0) {
            int ii = computeGraphArgs.GPtr.outVertexIndex[i];
            computeGraphArgs.GPtr.paralEdgeIndex[ii+r] = i0;
            computeGraphArgs.GPtr.outVertexList[ii+r] = computeGraphArgs.SDGdataPtr.endVertex[i0];
            r++;
            for (int t = i0+1; t < numEdgesPlaced; t++) {
              if (computeGraphArgs.SDGdataPtr.endVertex[t] != computeGraphArgs.SDGdataPtr.endVertex[t-1]) {
                computeGraphArgs.GPtr.paralEdgeIndex[ii+r] = t;
                computeGraphArgs.GPtr.outVertexList[ii+r] = computeGraphArgs.SDGdataPtr.endVertex[t];
                r++;
              }
            }
          }
        }

      } /* for i */

      Barrier.enterBarrier();

      if (myId == 0) {
        computeGraphArgs.SDGdataPtr.startVertex = null;
        computeGraphArgs.SDGdataPtr.endVertex = null;
        computeGraphArgs.GPtr.inDegree = new int[computeGraphArgs.GPtr.numVertices];
        computeGraphArgs.GPtr.inVertexIndex = new int[computeGraphArgs.GPtr.numVertices];
      }

      Barrier.enterBarrier();

      for (int i = lss.i_start; i < lss.i_stop; i++) {
        computeGraphArgs.GPtr.inDegree[i] = 0;
        computeGraphArgs.GPtr.inVertexIndex[i] = 0;
      }

      /* A temp. array to store the inplied edges */
      int[] impliedEdgeList;
      
      if (myId == 0) {
        impliedEdgeList = new int[computeGraphArgs.GPtr.numVertices * glb.MAX_CLUSTER_SIZE];
        computeGraphArgs.global_impliedEdgeList = impliedEdgeList;
      }

      Barrier.enterBarrier();

      impliedEdgeList = computeGraphArgs.global_impliedEdgeList;

      CreatePartition.createPartition(0,
          (computeGraphArgs.GPtr.numVertices * glb.MAX_CLUSTER_SIZE),
          myId,
          numThread,
          lss);

      for (int i = lss.i_start; i < lss.i_stop; i++) {
        impliedEdgeList[i] = 0;
      }

      /*
       * An auxiliary array to store implied edges, in case we overshoot
       * MAX_CLUSTER_SIZE
       */

      int[][] auxArr;
      if (myId == 0) {
        auxArr = new int[computeGraphArgs.GPtr.numVertices][glb.MAX_CLUSTER_SIZE];
        computeGraphArgs.global_auxArr = auxArr;
      }

      Barrier.enterBarrier();

      auxArr = computeGraphArgs.global_auxArr;

      CreatePartition.createPartition(0, computeGraphArgs.GPtr.numVertices, myId, numThread, lss);

      for (int i = lss.i_start; i < lss.i_stop; i++) {
        /* Inspect adjacency list of vertex i */
        for (int j = computeGraphArgs.GPtr.outVertexIndex[i];
            j < (computeGraphArgs.GPtr.outVertexIndex[i] + computeGraphArgs.GPtr.outDegree[i]);
            j++)
        {
          int v =  (computeGraphArgs.GPtr.outVertexList[j]);
          int k;
          for (k = computeGraphArgs.GPtr.outVertexIndex[v];
              k < (computeGraphArgs.GPtr.outVertexIndex[v] + computeGraphArgs.GPtr.outDegree[v]);
              k++)
          {
            if (computeGraphArgs.GPtr.outVertexList[k] == i) {
              break;
            }
          }
          if (k == computeGraphArgs.GPtr.outVertexIndex[v]+computeGraphArgs.GPtr.outDegree[v]) {
            atomic {
              /* Add i to the impliedEdgeList of v */
              int inDegree = computeGraphArgs.GPtr.inDegree[v];
              computeGraphArgs.GPtr.inDegree[v] =  (inDegree + 1);
              if ( inDegree < glb.MAX_CLUSTER_SIZE) {
                impliedEdgeList[v*glb.MAX_CLUSTER_SIZE+inDegree] = i;
              } else {
                /* Use auxiliary array to store the implied edge */
                /* Create an array if it's not present already */
                int[] a = null;
                if ((inDegree % glb.MAX_CLUSTER_SIZE) == 0) {
                  a = new int[glb.MAX_CLUSTER_SIZE];
                  auxArr[v] = a;
                } else {
                  a = auxArr[v];
                }
                a[inDegree % glb.MAX_CLUSTER_SIZE] = i;
              }
            }
          }
        }
      } /* for i */

      Barrier.enterBarrier();

      computeGraphArgs.prefix_sums(myId, numThread, computeGraphArgs.GPtr.inVertexIndex, computeGraphArgs.GPtr.inDegree, computeGraphArgs.GPtr.numVertices);

      if (myId == 0) {
        computeGraphArgs.GPtr.numUndirectedEdges = computeGraphArgs.GPtr.inVertexIndex[computeGraphArgs.GPtr.numVertices-1]
          + computeGraphArgs.GPtr.inDegree[computeGraphArgs.GPtr.numVertices-1];
        computeGraphArgs.GPtr.inVertexList = new int[computeGraphArgs.GPtr.numUndirectedEdges];
      }

      Barrier.enterBarrier();

      /*
       * Create the inVertex List
       */

      for (int i = lss.i_start; i < lss.i_stop; i++) {
        for (int j = computeGraphArgs.GPtr.inVertexIndex[i];
            j < (computeGraphArgs.GPtr.inVertexIndex[i] + computeGraphArgs.GPtr.inDegree[i]);
            j++)
        {
          if ((j - computeGraphArgs.GPtr.inVertexIndex[i]) < glb.MAX_CLUSTER_SIZE) {
            computeGraphArgs.GPtr.inVertexList[j] =
              impliedEdgeList[i*glb.MAX_CLUSTER_SIZE+j-computeGraphArgs.GPtr.inVertexIndex[i]];
          } else {
            computeGraphArgs.GPtr.inVertexList[j] =
              auxArr[i][(j-computeGraphArgs.GPtr.inVertexIndex[i]) % glb.MAX_CLUSTER_SIZE];
          }
        }
      }

      Barrier.enterBarrier();

      if (myId == 0) {
        impliedEdgeList = null;
      }

      for (int i = lss.i_start; i < lss.i_stop; i++) {
        if (computeGraphArgs.GPtr.inDegree[i] > glb.MAX_CLUSTER_SIZE) {
          auxArr[i] = null;
        }
      }

      Barrier.enterBarrier();

      if (myId == 0) {
        auxArr = null;
      }

    }
}

/* =============================================================================
 *
 * End of computeGraph.java
 *
 * =============================================================================
 */
