/* =============================================================================
 *
 * genScalData.java
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

public class GenScalData {

  public long[] global_permV;
  public long[] global_cliqueSizes;
  public int global_totCliques;
  public long[] global_firstVsInCliques;
  public long[] global_lastVsInCliques;
  public long[] global_i_edgeStartCounter;
  public long[] global_i_edgeEndCounter;
  public long global_edgeNum;
  public long global_numStrWtEdges;
  public long[] global_startVertex;
  public long[] global_endVertex;
  public long[] global_tempIndex;

  /**
   * Constructor
   **/
  public GenScalData() {
    global_permV;              = null;
    global_cliqueSizes        = null;
    global_totCliques         = 0;
    global_firstVsInCliques   = null;
    global_lastVsInCliques    = null;
    global_i_edgeStartCounter = null;
    global_i_edgeEndCounter   = null;
    global_edgeNum            = 0L;
    global_numStrWtEdges      = 0L;
    global_startVertex        = null;
    global_endVertex          = null;
    global_tempIndex          = null;
  }


  /**
   * =============================================================================
   *      genScalData_seq
   * =============================================================================
   **/
  void
    genScalData_seq (graphSDG* SDGdataPtr)
    {
      System.out.println("Call to genScalData_seq(), Unimplemented: TODO\n");
      System.exit(0);
    }


  /**
   * =============================================================================
   *       genScalData
   * =============================================================================
   */

  public static void
    genScalData (int myId, int numThread, Globals glb, GraphSDG SDGdataPtr)
    {
      /*
       * STEP 0: Create the permutations required to randomize the vertices
       */

      Random randomPtr = new Random();
      randomPtr = randomPtr.random_alloc(randomPtr);
      randomPtr.random_seed(randomPtr, myId);

      long[] permV;

      if (myId == 0) {
        permV = new long[glb.TOT_VERTICES];
        global_permV = permV;
      }

      Barrier.enterBarrier();

      permV = global_permV;

      LocalStartStop lss = new LocalStartStop();
      CreatePartition cp = new CreatePartition();
      cp.createPartition(0, glb.TOT_VERTICES, myId, numThread, lss);

      /* Initialize the array */
      for (int i = lss.i_start; i < lss.i_stop; i++) {
        permV[i] = i;
      }

      Barrier.enterBarrier();

      for (int i = i_start; i < i_stop; i++) {
        int t1 = (int) (randomPtr.random_generate(randomPtr));
        int t = i + t1 % (glb.TOT_VERTICES - i);
        if (t != i) {
          atomic {
            long t2 = permV[t];
            permV[t] = permV[i];
            permV[i] = t2;
          }
        }
      }

      /*
       * STEP 1: Create Cliques
       */

      long[] cliqueSizes;

      int estTotCliques = (int)(Math.ceil(1.5d * glb.TOT_VERTICES / ((1+glb.MAX_CLIQUE_SIZE)/2)));

      /*
       * Allocate mem for Clique array
       * Estimate number of clique required and pad by 50%
       */
      if (myId == 0) {
        cliqueSizes = new long[estTotCliques];
        global_cliqueSizes = cliqueSizes;
      }

      Barrier.enterBarrier();

      cliqueSizes = global_cliqueSizes;

      cp.createPartition(0, estTotCliques, myId, numThread, lss);

      /* Generate random clique sizes. */
      for (int i = lss.i_start; i < lss.i_stop; i++) {
        cliqueSizes[i] = 1 + (randomPtr.random_generate(randomPtr) % MAX_CLIQUE_SIZE);
      }

      Barrier.enterBarrier();

      int totCliques = 0;

      /*
       * Allocate memory for cliqueList
       */

      long[] firstVsInCliques;
      long[] firstVsInCliques;

      if (myId == 0) {
        lastVsInCliques = new long[estTotCliques];
        global_lastVsInCliques = lastVsInCliques;
        firstVsInCliques = new long[estTotCliques];
        global_firstVsInCliques = firstVsInCliques;

        /*
         * Sum up vertices in each clique to determine the lastVsInCliques array
         */

        lastVsInCliques[0] = cliqueSizes[0] - 1;
        for (int i = 1; i < estTotCliques; i++) {
          lastVsInCliques[i] = cliqueSizes[i] + lastVsInCliques[i-1];
          if (lastVsInCliques[i] >= glb.TOT_VERTICES-1) {
            break;
          }
        }
        totCliques = i + 1;

        global_totCliques = totCliques;

        /*
         * Fix the size of the last clique
         */
        cliqueSizes[totCliques-1] =
          glb.TOT_VERTICES - lastVsInCliques[totCliques-2] - 1;
        lastVsInCliques[totCliques-1] = glb.TOT_VERTICES - 1;

        firstVsInCliques[0] = 0;

      }

      Barrier.enterBarrier();

      lastVsInCliques  = global_lastVsInCliques;
      firstVsInCliques = global_firstVsInCliques;
      totCliques = global_totCliques;

      /* Compute start Vertices in cliques. */
      createPartition(1, totCliques, myId, numThread, lss);
      for (i = i_start; i < i_stop; i++) {
        firstVsInCliques[i] = lastVsInCliques[i-1] + 1;
      }

      /* TODO : if required 

#ifdef WRITE_RESULT_FILES
Barrier.enterBarrier();

      // Write the generated cliques to file for comparison with Kernel 4 
      if (myId == 0) {
      FILE* outfp = fopen("cliques.txt", "w");
      fprintf(outfp, "No. of cliques - %lu\n", totCliques);
      for (i = 0; i < totCliques; i++) {
      fprintf(outfp, "Clq %lu - ", i);
      long j;
      for (j = firstVsInCliques[i]; j <= lastVsInCliques[i]; j++) {
      fprintf(outfp, "%lu ", permV[j]);
      }
      fprintf(outfp, "\n");
      }
      fclose(outfp);
      }

      Barrier.enterBarrier();
#endif
*/

      /*
       * STEP 2: Create the edges within the cliques
       */

      /*
       * Estimate number of edges - using an empirical measure
       */
      long estTotEdges;
      if (glb.SCALE >= 12) {
        estTotEdges = (long) (Math.ceil(1.0d *((glb.MAX_CLIQUE_SIZE-1) * glb.TOT_VERTICES)));
      } else {
        estTotEdges = (long) (Math.ceil(1.2d * (((glb.MAX_CLIQUE_SIZE-1)*glb.TOT_VERTICES)
                * ((1 + glb.MAX_PARAL_EDGES)/2) + glb.TOT_VERTICES*2)));
      }

      /*
       * Initialize edge counter
       */
      int i_edgePtr = 0;

      /*
       * Partial edgeLists
       */

      long[] startV;
      long[] endV;

      if (numThread > 3) {
        int numByte = 1.5 * (estTotEdges/numThread);
        startV = new long[numByte];
        endV = new long[numByte];
      } else  {
        int numByte = (estTotEdges/numThread);
        startV = new long[numByte];
        endV = new long[numByte];
      }

      /*
       * Tmp array to keep track of the no. of parallel edges in each direction
       */
      long[][] tmpEdgeCounter = new long[glb.MAX_CLIQUE_SIZE][glb.MAX_CLIQUE_SIZE];

      /*
       * Create edges in parallel
       */
      int i_clique;
      cp.createPartition(0, totCliques, myId, numThread, lss);

      double p = glb.PROB_UNIDIRECTIONAL;
      for (i_clique = lss.i_start; i_clique < lss.i_stop; i_clique++) {

        /*
         * Get current clique parameters
         */

        long i_cliqueSize = cliqueSizes[i_clique];
        long i_firstVsInClique = firstVsInCliques[i_clique];

        /*
         * First create at least one edge between two vetices in a clique
         */

        int i;
        int j;
        for (i = 0; i < (int) i_cliqueSize; i++) {
          for (j = 0; j < i; j++) {
            double r = (double)(randomPtr.random_generate(randomPtr) % 1000) / (double)1000;
            if (r >= p) {

              startV[i_edgePtr] = i + i_firstVsInClique;
              endV[i_edgePtr] = j + i_firstVsInClique;
              i_edgePtr++;
              tmpEdgeCounter[i][j] = 1;

              startV[i_edgePtr] = j + i_firstVsInClique;
              endV[i_edgePtr] = i + i_firstVsInClique;
              i_edgePtr++;
              tmpEdgeCounter[j][i] = 1;

            } else  if (r >= 0.5) {

              startV[i_edgePtr] = i + i_firstVsInClique;
              endV[i_edgePtr] = j + i_firstVsInClique;
              i_edgePtr++;
              tmpEdgeCounter[i][j] = 1;
              tmpEdgeCounter[j][i] = 0;

            } else {

              startV[i_edgePtr] = j + i_firstVsInClique;
              endV[i_edgePtr] = i + i_firstVsInClique;
              i_edgePtr++;
              tmpEdgeCounter[j][i] = 1;
              tmpEdgeCounter[i][j] = 0;

            }

          } /* for j */
        } /* for i */

        if (i_cliqueSize != 1) {
          long randNumEdges = (long)(randomPtr.random_generate(randomPtr)
              % (2*i_cliqueSize*glb.MAX_PARAL_EDGES));
          int i_paralEdge;
          for (i_paralEdge = 0; i_paralEdge < (int) randNumEdges; i_paralEdge++) {
            i = (int) (randomPtr.random_generate(randomPtr) % i_cliqueSize);
            int j = (int) (randomPtr.random_generate(randomPtr) % i_cliqueSize);
            if ((i != j) && (tmpEdgeCounter[i][j] < glb.MAX_PARAL_EDGES)) {
              double r = (double)(randomPtr.random_generate(randomPtr) % 1000) / (double)1000;
              if (r >= p) {
                /* Copy to edge structure. */
                startV[i_edgePtr] = i + i_firstVsInClique;
                endV[i_edgePtr] = j + i_firstVsInClique;
                i_edgePtr++;
                tmpEdgeCounter[i][j]++;
              }
            }
          }
        }

      } /* for i_clique */

      /*
       * Merge partial edge lists
       */

      long[] i_edgeStartCounter;
      long[] i_edgeEndCounter;

      if (myId == 0) {
        i_edgeStartCounter = new long[numThread];
        global_i_edgeStartCounter = i_edgeStartCounter;
        i_edgeEndCounter = new long[numThread];
        global_i_edgeEndCounter = i_edgeEndCounter;
      }

      Barrier.enterBarrier();

      i_edgeStartCounter = global_i_edgeStartCounter;
      i_edgeEndCounter   = global_i_edgeEndCounter;

      i_edgeEndCounter[myId] = i_edgePtr;
      i_edgeStartCounter[myId] = 0;

      Barrier.enterBarrier();

      if (myId == 0) {
        for (i = 1; i < numThread; i++) {
          i_edgeEndCounter[i] = i_edgeEndCounter[i-1] + i_edgeEndCounter[i];
          i_edgeStartCounter[i] = i_edgeEndCounter[i-1];
        }
      }

      atomic {
        global_edgeNum = (long) (global_edgeNum + i_edgePtr);
      }

      Barrier.enterBarrier();

      long edgeNum = global_edgeNum;

      /*
       * Initialize edge list arrays
       */

      long[] startVertex;
      long[] endVertex;

      if (myId == 0) {
        if (glb.SCALE < 10) {
          int numByte = 2 * edgeNum;
          startVertex = new long[numByte];
          endVertex = new long[numByte];
        } else {
          int numByte = (edgeNum + glb.MAX_PARAL_EDGES * glb.TOT_VERTICES);
          startVertex = new long[numByte];
          endVertex = new long[numByte];
        }
        global_startVertex = startVertex;
        global_endVertex = endVertex;
      }

      Barrier.enterBarrier();

      startVertex = global_startVertex;
      endVertex = global_endVertex;

      for (int i = (int) i_edgeStartCounter[myId]; i < (int) i_edgeEndCounter[myId]; i++) {
        startVertex[i] = startV[i-i_edgeStartCounter[myId]];
        endVertex[i] = endV[i-i_edgeStartCounter[myId]];
      }

      int numEdgesPlacedInCliques = edgeNum;

      Barrier.enterBarrier();

      /*
       * STEP 3: Connect the cliques
       */

      i_edgePtr = 0;
      p = PROB_INTERCL_EDGES;

      /*
       * Generating inter-clique edges as given in the specs
       */

      cp.createPartition(0, glb.TOT_VERTICES, myId, numThread, lss);

      for (int i = lss.i_start; i < lss.i_stop; i++) {
        long tempVertex1 = (long) i;
        long h = totCliques;
        long l = 0;
        long t = -1;
        while (h - l > 1) {
          int m = (int) ((h + l) / 2);
          if (tempVertex1 >= firstVsInCliques[m]) {
            l = m;
          } else {
            if ((tempVertex1 < firstVsInCliques[m]) && (m > 0)) {
              if (tempVertex1 >= firstVsInCliques[m-1]) {
                t = m - 1;
                break;
              } else {
                h = m;
              }
            }
          }
        }

        if (t == -1) {
          int m;
          for (m = (l + 1); m < h; m++) {
            if (tempVertex1<firstVsInCliques[m]) {
              break;
            }
          }
          t = m-1;
        }

        long t1 = firstVsInCliques[t];

        int d;
        for (d = 1, p = glb.PROB_INTERCL_EDGES; d < glb.TOT_VERTICES; d *= 2, p /= 2) {

          double r = (double)(randomPtr.random_generate(randomPtr) % 1000) / (double)1000;

          if (r <= p) {

            long tempVertex2 = (long) ((i+d) % glb.TOT_VERTICES);

            h = totCliques;
            l = 0;
            t = -1;
            while (h - l > 1) {
              int m = (int) ((h + l) / 2);
              if (tempVertex2 >= firstVsInCliques[m]) {
                l = m;
              } else {
                if ((tempVertex2 < firstVsInCliques[m]) && (m > 0)) {
                  if (firstVsInCliques[m-1] <= tempVertex2) {
                    t = m - 1;
                    break;
                  } else {
                    h = m;
                  }
                }
              }
            }

            if (t == -1) {
              //long m;
              int m;
              for (m = (l + 1); m < h; m++) {
                if (tempVertex2 < firstVsInCliques[m]) {
                  break;
                }
              }
              t = m - 1;
            }

            long t2 = firstVsInCliques[t];

            if (t1 != t2) {
              long randNumEdges =
                randomPtr.random_generate(randomPtr) % glb.MAX_PARAL_EDGES + 1;
              int j;
              for (j = 0; j < randNumEdges; j++) {
                startV[i_edgePtr] = tempVertex1;
                endV[i_edgePtr] = tempVertex2;
                i_edgePtr++;
              }
            }

          } /* r <= p */

          double r0 = (double)(randomPtr.random_generate(randomPtr) % 1000) / (double)1000;

          if ((r0 <= p) && (i-d>=0)) {
            long tempVertex2 = (i - d) % glb.TOT_VERTICES;

            h = totCliques;
            l = 0;
            t = -1;
            while (h - l > 1) {
              int m = (int)((h + l) / 2);
              if (tempVertex2 >= firstVsInCliques[m]) {
                l = m;
              } else {
                if ((tempVertex2 < firstVsInCliques[m]) && (m > 0)) {
                  if (firstVsInCliques[m-1] <= tempVertex2) {
                    t = m - 1;
                    break;
                  } else {
                    h = m;
                  }
                }
              }
            }

            if (t == -1) {
              int m;
              for (m = (int) (l + 1); m < (int) h; m++) {
                if (tempVertex2 < firstVsInCliques[m]) {
                  break;
                }
              }
              t = m - 1;
            }

            long t2 = firstVsInCliques[t];

            if (t1 != t2) {
              long randNumEdges =
                randomPtr.random_generate(randomPtr) % glb.MAX_PARAL_EDGES + 1;
              int j;
              for (j = 0; j < (int) randNumEdges; j++) {
                startV[i_edgePtr] = tempVertex1;
                endV[i_edgePtr] = tempVertex2;
                i_edgePtr++;
              }
            }

          } /* r0 <= p && (i-d) > 0 */

        } /* for d, p */

      } /* for i */


      i_edgeEndCounter[myId] = i_edgePtr;
      i_edgeStartCounter[myId] = 0;

      if (myId == 0) {
        global_edgeNum = 0;
      }

      Barrier.enterBarrier();

      if (myId == 0) {
        for (int i = 1; i < numThread; i++) {
          i_edgeEndCounter[i] = i_edgeEndCounter[i-1] + i_edgeEndCounter[i];
          i_edgeStartCounter[i] = i_edgeEndCounter[i-1];
        }
      }

      atomic {
        global_edgeNum = global_edgeNum + i_edgePtr;
      }

      Barrier.enterBarrier();

      edgeNum = global_edgeNum;
      long numEdgesPlacedOutside = global_edgeNum;

      for (int i = (int) i_edgeStartCounter[myId]; i < (int) i_edgeEndCounter[myId]; i++) {
        startVertex[i+numEdgesPlacedInCliques] = startV[i-i_edgeStartCounter[myId]];
        endVertex[i+numEdgesPlacedInCliques] = endV[i-i_edgeStartCounter[myId]];
      }

      Barrier.enterBarrier();

      long numEdgesPlaced = numEdgesPlacedInCliques + numEdgesPlacedOutside;

      if (myId == 0) {

        SDGdataPtr.numEdgesPlaced = (int) numEdgesPlaced;

        printf("Finished generating edges\n");
        printf("No. of intra-clique edges - %lu\n", numEdgesPlacedInCliques);
        printf("No. of inter-clique edges - %lu\n", numEdgesPlacedOutside);
        printf("Total no. of edges        - %lu\n", numEdgesPlaced);
      }

      Barrier.enterBarrier();

      /*
       * STEP 4: Generate edge weights
       */

      if (myId == 0) {
        SDGdataPtr.intWeight = new long[(int) numEdgesPlaced];
      }

      Barrier.enterBarrier();

      p = glb.PERC_INT_WEIGHTS;
      long numStrWtEdges  = 0;

      cp.createPartition(0, (int) numEdgesPlaced, myId, numThread, lss);

      for (int i = lss.i_start; i < lss.i_stop; i++) {
        double r = (double)(randomPtr.random_generate(randomPtr) % 1000) / (double)1000;
        if (r <= p) {
          SDGdataPtr.intWeight[i] =
            1 + (randomPtr.random_generate(randomPtr) % (MAX_INT_WEIGHT-1));
        } else {
          SDGdataPtr.intWeight[i] = -1;
          numStrWtEdges++;
        }
      }

      Barrier.enterBarrier();

      if (myId == 0) {
        int t = 0;
        for (int i = 0; i < (int) numEdgesPlaced; i++) {
          if (SDGdataPtr.intWeight[i] < 0) {
            SDGdataPtr.intWeight[i] = -t;
            t++;
          }
        }
      }

      atomic {
        global_numStrWtEdges = global_numStrWtEdges + numStrWtEdges;
      }

      Barrier.enterBarrier();

      numStrWtEdges = global_numStrWtEdges;

      if (myId == 0) {
        SDGdataPtr.strWeight = new char[numStrWtEdges * glb.MAX_STRLEN];
      }

      Barrier.enterBarrier();

      cp.createPartition(0, (int) numEdgesPlaced, myId, numThread, lss);

      for (int i = lss.i_start; i < lss.i_stop; i++) {
        if (SDGdataPtr.intWeight[i] <= 0) {
          for (int j = 0; j < glb.MAX_STRLEN; j++) {
            SDGdataPtr.strWeight[(-(int) SDGdataPtr.intWeight[i])*glb.MAX_STRLEN+j] =
              //     (char) (1 + PRANDOM_GENERATE(stream) % 127);
              //FIXME
              (char) (1 + (randomPtr.random_generate(randomPtr) % 127));
          }
        }
      }

      /*
       * Choose SOUGHT STRING randomly if not assigned
       */

      if (myId == 0) {

        if (SOUGHT_STRING.length != glb.MAX_STRLEN) {
          glb.SOUGHT_STRING = new char[MAX_STRLEN];
        }

        long t = randomPtr.random_generate(randomPtr) % numStrWtEdges;
        for (int j = 0; j < glb.MAX_STRLEN; j++) {
          //FIXME
          SOUGHT_STRING[j] =
            (char) ((long) SDGdataPtr.strWeight[(int) (t*glb.MAX_STRLEN+j)]);
        }

      }

      Barrier.enterBarrier();

      /*
       * STEP 5: Permute Vertices
       */

      for (int i = lss.i_start; i < lss.i_stop; i++) {
        startVertex[i] = permV[(startVertex[i])];
        endVertex[i] = permV[(endVertex[i])];
      }

      Barrier.enterBarrier();

      /*
       * STEP 6: Sort Vertices
       */

      /*
       * Radix sort with StartVertex as primary key
       */

      if (myId == 0) {
        int numByte = (int) numEdgesPlaced;
        SDGdataPtr.startVertex = new long[numByte];
        SDGdataPtr.endVertex = new long[numByte];
      }

      Barrier.enterBarrier();

      Alg_Radix_Smp.all_radixsort_node_aux_s3(numEdgesPlaced,
          startVertex,
          SDGdataPtr.startVertex,
          endVertex,
          SDGdataPtr.endVertex);

      Barrier.enterBarrier();

      if (glb.SCALE < 12) {

        /*
         * Sort with endVertex as secondary key
         */

        if (myId == 0) {

          int i0 = 0;
          int i1 = 0;
          int i = 0;

          while (i < (int) numEdgesPlaced) {

            for (i = i0; i < (int) numEdgesPlaced; i++) {
              if (SDGdataPtr.startVertex[i] !=
                  SDGdataPtr.startVertex[i1])
              {
                i1 = i;
                break;
              }
            }

            int j;
            for (j = i0; j < i1; j++) {
              int k;
              for (k = j+1; k < i1; k++) {
                if (SDGdataPtr.endVertex[k] <
                    SDGdataPtr.endVertex[j])
                {
                  long t = SDGdataPtr.endVertex[j];
                  SDGdataPtr.endVertex[j] = SDGdataPtr.endVertex[k];
                  SDGdataPtr.endVertex[k] = t;
                }
              }
            }

            if (SDGdataPtr.startVertex[i0] != (long) glb.TOT_VERTICES-1) {
              i0 = i1;
            } else {
              int j;
              for (j=i0; j<(int)numEdgesPlaced; j++) {
                int k;
                for (k=j+1; k<(int)numEdgesPlaced; k++) {
                  if (SDGdataPtr.endVertex[k] <
                      SDGdataPtr.endVertex[j])
                  {
                    long t = SDGdataPtr.endVertex[j];
                    SDGdataPtr.endVertex[j] = SDGdataPtr.endVertex[k];
                    SDGdataPtr.endVertex[k] = t;
                  }
                }
              }
            }

          } /* while i < numEdgesPlaced */

        }

      } else {

        long[] tempIndex;

        if (myId == 0) {

          tempIndex = new long[glb.TOT_VERTICES + 1];
          global_tempIndex = tempIndex;

          /*
           * Update degree of each vertex
           */

          tempIndex[0] = 0;
          tempIndex[glb.TOT_VERTICES] = numEdgesPlaced;
          int i0 = 0;

          for (int i=0; i < glb.TOT_VERTICES; i++) {
            tempIndex[i+1] = tempIndex[i];
            int j;
            for (j = i0; j < (int) numEdgesPlaced; j++) {
              if (SDGdataPtr.startVertex[j] !=
                  SDGdataPtr.startVertex[i0])
              {
                if (SDGdataPtr.startVertex[i0] == i) {
                  tempIndex[i+1] = j;
                  i0 = j;
                  break;
                }
              }
            }
          }
        }

        Barrier.enterBarrier();

        tempIndex = global_tempIndex;

        /*
         * Insertion sort for now, replace with something better later on
         */


        if (myId == 0) {
          for (int i = 0; i < glb.TOT_VERTICES; i++) {
            for (int j = (int) tempIndex[i]; j < (int) tempIndex[i+1]; j++) {
              for (int k = (j + 1); k < tempIndex[i+1]; k++) {
                if (SDGdataPtr.endVertex[k] <
                    SDGdataPtr.endVertex[j])
                {
                  long t = SDGdataPtr.endVertex[j];
                  SDGdataPtr.endVertex[j] = SDGdataPtr.endVertex[k];
                  SDGdataPtr.endVertex[k] = t;
                }
              }
            }
          }
        }

      } /* SCALE >= 12 */

    }
}

/* =============================================================================
 *
 * End of genScalData.java
 *
 * =============================================================================
 */
