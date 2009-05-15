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

  public long[] global_p;
  public long global_maxNumVertices;
  public long global_outVertexListSize;
  public long[] global_impliedEdgeList;
  public long[][] global_auxArr;

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
    prefix_sums (int myId, int numThread, long[] result, long[] input, int arraySize)
    {
      long[]  p;
      if (myId == 0) {
        p = new long[NOSHARE(numThread)];
        global_p = p;
      }

      Barrier.enterBarrier();

      p = global_p;

      long start;
      long end;

      long r = arraySize / numThread;
      start = myId * r + 1;
      end = (myId + 1) * r;
      if (myId == (numThread - 1)) {
        end = arraySize;
      }

      for (int j = (int) start; j < (int) end; j++) {
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
        long add_value = p[NOSHARE(myId-1)];
        for (j = start-1; j < end; j++) {
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
    computeGraph (int myId, int numThread, Graph GPtr, GraphSDG SDGdataPtrGlobals glb)
    {
      Barrier.enterBarrier();

      int j;
      long maxNumVertices = 0;
      int numEdgesPlaced = SDGdataPtr.numEdgesPlaced;

      /*
       * First determine the number of vertices by scanning the tuple
       * startVertex list
       */
      LocalStartStop lss = new LocalStartStop();
      CreatePartition.createPartition(0, numEdgesPlaced, myId, numThread, lss);

      for (int i = lss.i_start; i < lss.i_stop; i++) {
        if (SDGdataPtr.startVertex[i] > maxNumVertices) {
          maxNumVertices = SDGdataPtr.startVertex[i];
        }
      }

      atomic {
        long tmp_maxNumVertices = global_maxNumVertices;
        long new_maxNumVertices = CreatePartition.MAX((int) tmp_maxNumVertices, maxNumVertices) + 1;
        global_maxNumVertices = new_maxNumVertices;
        /*
        long tmp_maxNumVertices = (long)TM_SHARED_READ(global_maxNumVertices);
        long new_maxNumVertices = MAX(tmp_maxNumVertices, maxNumVertices) + 1;
        TM_SHARED_WRITE(global_maxNumVertices, new_maxNumVertices);
        */
      }

      Barrier.enterBarrier();

      maxNumVertices = global_maxNumVertices;

      if (myId == 0) {

        GPtr.numVertices = maxNumVertices;
        GPtr.numEdges    = numEdgesPlaced;
        GPtr.intWeight   = SDGdataPtr.intWeight;
        GPtr.strWeight   = SDGdataPtr.strWeight;

        for (int i = 0; i < numEdgesPlaced; i++) {
          if (GPtr.intWeight[numEdgesPlaced-i-1] < 0) {
            GPtr.numStrEdges = -(GPtr.intWeight[numEdgesPlaced-i-1]) + 1;
            GPtr.numIntEdges = numEdgesPlaced - GPtr.numStrEdges;
            break;
          }
        }

        GPtr.outDegree = new long[GPtr.numVertices];

        GPtr.outVertexIndex = new long[GPtr.numVertices];
      }

      Barrier.enterBarrier();

      CreatePartition.createPartition(0, GPtr.numVertices, myId, numThread, lss);

      for (i = lss.i_start; i < lss.i_stop; i++) {
        GPtr.outDegree[i] = 0;
        GPtr.outVertexIndex[i] = 0;
      }

      int outVertexListSize = 0;

      Barrier.enterBarrier();

      int i0 = -1;

      for (int i = lss.i_start; i < lss.i_stop; i++) {
        long k = i;
        if ((outVertexListSize == 0) && (k != 0)) {
          while (i0 == -1) {
            for (int j = 0; j < numEdgesPlaced; j++) {
              if (k == SDGdataPtr.startVertex[j]) {
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
          if (i == GPtr.numVertices-1) {
            break;
          }
          if ((i != SDGdataPtr.startVertex[j])) {
            if ((j > 0) && (i == SDGdataPtr.startVertex[j-1])) {
              if (j-i0 >= 1) {
                outVertexListSize++;
                GPtr.outDegree[i]++;
                for (int t = (i0+1); t < j; t++) {
                  if (SDGdataPtr.endVertex[t] !=
                      SDGdataPtr.endVertex[t-1])
                  {
                    outVertexListSize++;
                    GPtr.outDegree[i] = GPtr.outDegree[i]+1;
                  }
                }
              }
            }
            i0 = j;
            break;
          }
        }

        if (i == GPtr.numVertices-1) {
          if (numEdgesPlaced-i0 >= 0) {
            outVertexListSize++;
            GPtr.outDegree[i]++;
            for (int t = (int) (i0+1); t < numEdgesPlaced; t++) {
              if (SDGdataPtr.endVertex[t] != SDGdataPtr.endVertex[t-1]) {
                outVertexListSize++;
                GPtr.outDegree[i]++;
              }
            }
          }
        }

      } /* for i */

      Barrier.enterBarrier();

      prefix_sums(myId, numThread, GPtr.outVertexIndex, GPtr.outDegree, GPtr.numVertices);

      Barrier.enterBarrier();

      atomic {
        global_outVertexListSize = global_outVertexListSize + outVertexListSize;
      }

      Barrier.enterBarrier();

      outVertexListSize = global_outVertexListSize;

      if (myId == 0) {
        GPtr.numDirectedEdges = outVertexListSize;
        GPtr.outVertexList = new long[outVertexListSize];
        GPtr.paralEdgeIndex = new long[outVertexListSize];
        GPtr.outVertexList[0] = SDGdataPtr.endVertex[0];
      }

      Barrier.enterBarrier();

      /*
       * Evaluate outVertexList
       */

      i0 = -1;

      for (int i = lss.i_start; i < lss.i_stop; i++) {

        long k = (long) i;
        while ((i0 == -1) && (k != 0)) {
          for (int j = 0; j < numEdgesPlaced; j++) {
            if (k == SDGdataPtr.startVertex[j]) {
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
          if (i == GPtr.numVertices-1) {
            break;
          }
          if (i != SDGdataPtr.startVertex[j]) {
            if ((j > 0) && (i == SDGdataPtr.startVertex[j-1])) {
              if (j-i0 >= 1) {
                int ii = (int) (GPtr.outVertexIndex[i]);
                int r = 0;
                GPtr.paralEdgeIndex[ii] = i0;
                GPtr.outVertexList[ii] = SDGdataPtr.endVertex[(int) i0];
                r++;
                for (int t = (int) (i0+1); t < j; t++) {
                  if (SDGdataPtr.endVertex[t] !=
                      SDGdataPtr.endVertex[t-1])
                  {
                    GPtr.paralEdgeIndex[ii+r] = t;
                    GPtr.outVertexList[ii+r] = SDGdataPtr.endVertex[t];
                    r++;
                  }
                }

              }
            }
            i0 = j;
            break;
          }
        } /* for j */

        if (i == GPtr.numVertices-1) {
          int r = 0;
          if (numEdgesPlaced-i0 >= 0) {
            int ii = GPtr.outVertexIndex[i];
            GPtr.paralEdgeIndex[ii+r] = i0;
            GPtr.outVertexList[ii+r] = SDGdataPtr.endVertex[i0];
            r++;
            for (int t = i0+1; t < numEdgesPlaced; t++) {
              if (SDGdataPtr.endVertex[t] != SDGdataPtr.endVertex[t-1]) {
                GPtr.paralEdgeIndex[ii+r] = t;
                GPtr.outVertexList[ii+r] = SDGdataPtr.endVertex[t];
                r++;
              }
            }
          }
        }

      } /* for i */

      Barrier.enterBarrier();

      if (myId == 0) {
        SDGdataPtr.startVertex = null;
        SDGdataPtr.endVertex = null;
        GPtr.inDegree = new long[GPtr.numVertices];
        GPtr.inVertexIndex = new long[GPtr.numVertices];
      }

      Barrier.enterBarrier();

      for (int i = lss.i_start; i < lss.i_stop; i++) {
        GPtr.inDegree[i] = 0;
        GPtr.inVertexIndex[i] = 0;
      }

      /* A temp. array to store the inplied edges */
      long[] impliedEdgeList;
      
      if (myId == 0) {
        impliedEdgeList = new long[GPtr.numVertices * glb.MAX_CLUSTER_SIZE];
        global_impliedEdgeList = impliedEdgeList;
      }

      Barrier.enterBarrier();

      impliedEdgeList = global_impliedEdgeList;

      CreatePartition.createPartition(0,
          (GPtr.numVertices * glb.MAX_CLUSTER_SIZE),
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

      long[][] auxArr;
      if (myId == 0) {
        auxArr = new long[GPtr.numVertices][glb.MAX_CLUSTER_SIZE];
        global_auxArr = auxArr;
      }

      Barrier.enterBarrier();

      auxArr = global_auxArr;

      CreatePartition.createPartition(0, GPtr.numVertices, myId, numThread, lss);

      for (int i = lss.i_start; i < lss.i_stop; i++) {
        /* Inspect adjacency list of vertex i */
        for (j = GPtr.outVertexIndex[i];
            j < (GPtr.outVertexIndex[i] + GPtr.outDegree[i]);
            j++)
        {
          int v = (int) (GPtr.outVertexList[j]);
          int k;
          for (k = GPtr.outVertexIndex[v];
              k < (GPtr.outVertexIndex[v] + GPtr.outDegree[v]);
              k++)
          {
            if (GPtr.outVertexList[k] == i) {
              break;
            }
          }
          if (k == GPtr.outVertexIndex[v]+GPtr.outDegree[v]) {
            atomic {
              /* Add i to the impliedEdgeList of v */
              long inDegree = GPtr.inDegree[v];
              GPtr.inDegree[v] =  (inDegree + 1);
              if ((int) inDegree < glb.MAX_CLUSTER_SIZE) {
                impliedEdgeList[v*glb.MAX_CLUSTER_SIZE+inDegree] = i
              } else {
                /* Use auxiliary array to store the implied edge */
                /* Create an array if it's not present already */
                long[] a = null;
                if (((int)inDegree % glb.MAX_CLUSTER_SIZE) == 0) {
                  a = new long[glb.MAX_CLUSTER_SIZE];
                  auxArr[v] = a;
                } else {
                  a = auxArr[v];
                }
                a[inDegree % MAX_CLUSTER_SIZE] = i;
              }
            }
          }
        }
      } /* for i */

      Barrier.enterBarrier();

      prefix_sums(myId, numThread, GPtr.inVertexIndex, GPtr.inDegree, GPtr.numVertices);

      if (myId == 0) {
        GPtr.numUndirectedEdges = GPtr.inVertexIndex[GPtr.numVertices-1]
          + GPtr.inDegree[GPtr.numVertices-1];
        GPtr.inVertexList = new long[GPtr.numUndirectedEdges];
      }

      Barrier.enterBarrier();

      /*
       * Create the inVertex List
       */

      for (int i = lss.i_start; i < lss.i_stop; i++) {
        for (int j = GPtr.inVertexIndex[i];
            j < (GPtr.inVertexIndex[i] + GPtr.inDegree[i]);
            j++)
        {
          if ((j - GPtr.inVertexIndex[i]) < glb.MAX_CLUSTER_SIZE) {
            GPtr.inVertexList[j] =
              impliedEdgeList[i*glb.MAX_CLUSTER_SIZE+j-GPtr.inVertexIndex[i]];
          } else {
            GPtr.inVertexList[j] =
              auxArr[i][(j-GPtr.inVertexIndex[i]) % glb.MAX_CLUSTER_SIZE];
          }
        }
      }

      Barrier.enterBarrier();

      if (myId == 0) {
        impliedEdgeList = null;
      }

      for (i = i_start; i < i_stop; i++) {
        if (GPtr.inDegree[i] > MAX_CLUSTER_SIZE) {
          auxArr[i] = null;
        }
      }

      Barrier.enterBarrier();

      if (myId == 0) {
        auxArr = null
      }

    }
}

/* =============================================================================
 *
 * End of computeGraph.java
 *
 * =============================================================================
 */
