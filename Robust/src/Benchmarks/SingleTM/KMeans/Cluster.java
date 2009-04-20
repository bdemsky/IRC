/* =============================================================================
 *
 * cluster.java
 *
 * =============================================================================
 *
 * Description:
 *
 * Takes as input a file, containing 1 data point per per line, and performs a
 * fuzzy c-means clustering on the data. Fuzzy clustering is performed using
 * min to max clusters and the clustering that gets the best score according to
 * a compactness and separation criterion are returned.
 *
 *
 * Author:
 *
 * Brendan McCane
 * James Cook University of North Queensland. Australia.
 * email: mccane@cs.jcu.edu.au
 *
 *
 * Edited by:
 *
 * Jay Pisharath, Wei-keng Liao
 * Northwestern University
 *
 * Chi Cao Minh
 * Stanford University
 *
 * Ported to Java:
 * Alokika Dash
 * University of California, Irvine
 *
 * =============================================================================
 *
 * For the license of kmeans, please see kmeans/LICENSE.kmeans
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

public class Cluster {

  public Cluster() {

  }

  /* =============================================================================
   * extractMoments
   * =============================================================================
   */
  public double[]
    extractMoments (double []data, int num_elts, int num_moments)
    {
      double[] moments = new double[num_moments];

      for (int i = 0; i < num_elts; i++) {
        moments[0] += data[i];
      }

      moments[0] = moments[0] / num_elts;
      for (int j = 1; j < num_moments; j++) {
        moments[j] = 0;
        for (int i = 0; i < num_elts; i++) {
          moments[j] += Math.pow((data[i]-moments[0]), j+1);
        }
        moments[j] = moments[j] / num_elts;
      }
      return moments;
    }


  /* =============================================================================
   * zscoreTransform
   * =============================================================================
   */
  public void
    zscoreTransform (double[][] data, /* in & out: [numObjects][numAttributes] */
        int     numObjects,
        int     numAttributes)
    {
      double[] moments;
      double[] single_variable = new double[numObjects];
      for (int i = 0; i < numAttributes; i++) {
        for (int j = 0; j < numObjects; j++) {
          single_variable[j] = data[j][i];
        }
        moments = extractMoments(single_variable, numObjects, 2);
        moments[1] = Math.sqrt((double)moments[1]);
        for (int j = 0; j < numObjects; j++) {
          data[j][i] = (data[j][i]-moments[0])/moments[1];
        }
      }
    }


  /* =============================================================================
   * cluster_exec
   * =============================================================================
   */
  public void
    cluster_exec (
        int      nthreads,               /* in: number of threads*/
        int      numObjects,             /* number of input objects */
        int      numAttributes,          /* size of attribute of each object */
        double[][]  attributes,           /* [numObjects][numAttributes] */
        int      use_zscore_transform,
        int      min_nclusters,          /* testing k range from min to max */
        int      max_nclusters,
        double    threshold,              /* in:   */
        int     best_nclusters,          /* out: number between min and max */
        double[][] cluster_centres,       /* out: [best_nclusters][numAttributes] */
        int[]     cluster_assign,        /* out: [numObjects] */
        GlobalArgs args                 /* Thread arguments */
        )
    {
      int itime;
      int nclusters;

      double[][] tmp_cluster_centres = null;
      int[] membership = new int[numObjects];

      Random randomPtr = new Random();
      randomPtr = randomPtr.random_alloc(randomPtr);

      if (use_zscore_transform == 1) {
        zscoreTransform(attributes, numObjects, numAttributes);
      }

      itime = 0;

      /*
       * From min_nclusters to max_nclusters, find best_nclusters
       */
      for (nclusters = min_nclusters; nclusters <= max_nclusters; nclusters++) {
        //System.out.println("ncluster= " + nclusters);

        randomPtr.random_seed(randomPtr, 7);
        args.nclusters = nclusters;

        Normal norm = new Normal();

        tmp_cluster_centres = norm.normal_exec(nthreads,
            attributes,
            numAttributes,
            numObjects,
            nclusters,
            threshold,
            membership,
            randomPtr,
            args);

        {
          cluster_centres = tmp_cluster_centres;
          best_nclusters = nclusters;
        }

        itime++;
      } /* nclusters */

      randomPtr = null;
    }
}


/* =============================================================================
 *
 * End of cluster.java
 *
 * =============================================================================
 */
