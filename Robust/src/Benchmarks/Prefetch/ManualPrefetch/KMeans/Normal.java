/* =============================================================================
 *
 * normal.java
 * -- Implementation of normal k-means clustering algorithm
 *
 * =============================================================================
 *
 * Author:
 *
 * Wei-keng Liao
 * ECE Department, Northwestern University
 * email: wkliao@ece.northwestern.edu
 *
 *
 * Edited by:
 *
 * Jay Pisharath
 * Northwestern University.
 *
 * Chi Cao Minh
 * Stanford University
 *
 * Alokika Dash
 * University of California, Irvine
 * Ported to Java
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

public class Normal {
  int CHUNK;

  public Normal() {
    CHUNK = 3;
  }

  /* =============================================================================
   * work
   * =============================================================================
   */
  public static void work(int myId, GlobalArgs args) {
    int CHUNK=3;
    float[][] feature;
    int nfeatures;
    int npoints;
    int nclusters;
    int[] membership;
    float[][] clusters;
    intwrapper[] new_centers_len;
    float[][] new_centers;
    int index, start, stop;

    start = myId * CHUNK;
    nclusters = args.nclusters;
    npoints = args.npoints;

    //Add Manual prefetch for 
    //args.feature[start-> stop]
    short[] offsets = new short[4];
    offsets[0] = getoffset{GlobalArgs, feature};
    offsets[1] = (short) 0;
    offsets[2] = (short) start;
    offsets[3] = (short) npoints;
    System.rangePrefetch(args, offsets);
    //args.clusters[0-> nclusters]
    offsets[0] = getoffset{GlobalArgs, clusters};
    offsets[1] = (short) 0;
    offsets[2] = (short) 0;
    offsets[3] = (short) nclusters;
    System.rangePrefetch(args, offsets);


    feature = args.feature;
    nfeatures = args.nfeatures;
    membership = args.membership;
    clusters = args.clusters;
    new_centers_len = args.new_centers_len;
    new_centers = args.new_centers;

    float delta = 0.0f;

    //System.out.println("myId= " + myId + " start= " + start + " stop= " + stop + " npoints= " + npoints);
    while (start < npoints) {
      stop = (((start + CHUNK) < npoints) ? (start + CHUNK) : npoints);
      for (int i = start; i < stop; i++) {
        index = Common.common_findNearestPoint(feature[i],
            nfeatures,
            clusters,
            nclusters);
        //
        // If membership changes, increase delta by 1.
        // membership[i] cannot be changed by other threads
        //
        if (membership[i] != index) {
          delta += 1.0f;
        }

        // Assign the membership to object i
        // membership[i] can't be changed by other thread
        membership[i] = index;

        // Update new cluster centers : sum of objects located within 
        new_centers_len[index] = new_centers_len[index] + 1;
        for (int j = 0; j < nfeatures; j++) {
          new_centers[index][j] = new_centers[index][j] + feature[i][j];
        }
      }

      // Update task queue 
      if (start + CHUNK < npoints) {
        start = args.global_i;
        args.global_i = start + CHUNK;
      } else {
        break;
      }
    }

    args.global_delta = args.global_delta + delta;
  }

  /* =============================================================================
   * normal_exec
   * =============================================================================
   */
  public float[][] normal_exec (
      int       nthreads,
      float[][]   feature,    /* in: [npoints][nfeatures] */
      int       nfeatures,
      int       npoints,
      int       nclusters,
      float     threshold,
      int[]      membership,
      Random     randomPtr,  /* out: [npoints] */
      GlobalArgs args)
  {
    float delta;
    float[][] clusters;      /* out: [nclusters][nfeatures] */


    /* Randomly pick cluster centers */
    atomic {
      /* Allocate space for returning variable clusters[] */
      clusters = global new float[nclusters][nfeatures];

      for (int i = 0; i < nclusters; i++) {
        int n = (int)(randomPtr.random_generate() % npoints);
        for (int j = 0; j < nfeatures; j++) {
          clusters[i][j] = feature[n][j];
        }
      }
      for (int i = 0; i < npoints; i++) {
        membership[i] = -1;
      }
    }

    /*
     * Need to initialize new_centers_len and new_centers[0] to all 0.
     * Allocate clusters on different cache lines to reduce false sharing.
     */
    intwrapper[] new_centers_len;
    float[][] new_centers;
    atomic {
      new_centers_len  = global new intwrapper[nclusters];
      new_centers = global new float[nclusters][nfeatures];
    }

    int loop = 0;

    GlobalArgs tmp_args;
    atomic {
      tmp_args = args;
    }

    Barrier barr = new Barrier("128.195.136.162");
    do {
      delta = (float) 0.0;

      atomic {
        tmp_args.feature         = feature;
        tmp_args.clusters        = clusters;
        tmp_args.new_centers_len = new_centers_len;
        tmp_args.new_centers     = new_centers;

        tmp_args.nfeatures       = nfeatures;
        tmp_args.npoints         = npoints;
        tmp_args.nclusters       = nclusters;
        tmp_args.membership      = membership;
        tmp_args.global_i = nthreads * CHUNK;
        tmp_args.global_delta = delta;
      }

      //Work in parallel with other threads
      thread_work(tmp_args, barr);

      atomic {
        delta = tmp_args.global_delta;

        // Replace old cluster centers with new_centers 
        for (int i = 0; i < nclusters; i++) {
          for (int j = 0; j < nfeatures; j++) {
            if (new_centers_len[i] > 0) {
              clusters[i][j] = new_centers[i][j] / new_centers_len[i];
            }
            new_centers[i][j] = (float)0.0;   // set back to 0 
          }
          new_centers_len[i] = 0;   // set back to 0 
        }
      }

      delta /= npoints;
      //System.out.println("delta= " + delta + " loop= " + loop + " threshold= " + threshold);
    } while ((delta > threshold) && (loop++ < 500));

    return clusters;
  }

  /**
   * Work done by primary thread in parallel with other threads
   **/
  void thread_work(GlobalArgs args, Barrier barr) {
    //System.out.println("Inside thread_work\n");
    Barrier.enterBarrier(barr);
    atomic {
      Normal.work(0, args); //threadId = 0 because primary thread
    }
    Barrier.enterBarrier(barr);
  }
}

/* =============================================================================
 *
 * End of normal.java
 *
 * =============================================================================
 */
