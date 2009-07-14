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
  // Flags
  flag startLoop;
  flag setKMeans;
  flag toendLoop;
  flag resetKMeans;
  flag finish;
  
  flag setStartZ;
  flag startZ;
  
  /** 
   * Numbers of loops to perform
   */
  int nloops;
  
  /*
   * Flag to indicate if nloops has been changes
   */
  boolean changeNLoops;
  
  /**
   * User input for max clusters
   **/
  int max_nclusters;

  /**
   * User input for min clusters
   **/
  int min_nclusters;

  /**
   * Using zscore transformation for cluster center 
   * deviating from distribution's mean
   **/
  int use_zscore_transform;

  /**
   * Total number of threads
   **/
  int nthreads;

  /**
   * threshold until which kmeans cluster continues
   **/
  float threshold;
  
  /**
   * List of attributes
   **/
  public float[][] feature;

  /**
   * Number of attributes per Object
   **/
  public int nfeatures;

  /**
   * Number of Objects
   **/
  public int npoints;
  
  /**
   * Since zscore transform may perform in cluster() which modifies the
   * contents of attributes[][], we need to re-store the originals each time
   * starting a loop.
   */
  public float[][] attributes;
  
  /**
   * 
   */
  MyRandom randomPtr;
  
  /*
   * Number of clusters to compute
   */
  int nclusters;
  
  /*
   * Flag indicating if nclusters has been changed
   */
  boolean changeNClusters;
  
  /*
   * Counter for aggregation of KMeans
   */
  int counterKMS;
  
  /*
   * 
   */
  intwrapper[] new_centers_len;
  float[][] new_centers;
  
  /*
   * 
   */
  float delta;
  
  /*
   * Counter for finished inner loops
   */
  int counterILoop;
  
  /*
   * Flag for initialization before setting KMeans
   */
  boolean isSet4KMeans;

  /**
   * Output:  Number of best clusters
   **/
  int best_nclusters;

  /**
   * Output: Cluster centers
   **/
  float[][] cluster_centres;
  
  public Cluster(int nloops, 
                 int max_nclusters,
                 int min_nclusters,
                 float threshold,
                 int use_zscore_transform,
                 int nthreads,
                 int numAttributes,
                 int numObjects,
                 float[][] buf) {
    this.nloops = nloops;
    this.max_nclusters = max_nclusters;
    this.min_nclusters = min_nclusters;
    this.threshold = threshold;
    this.use_zscore_transform = use_zscore_transform;
    this.nthreads = nthreads;
    this.nfeatures = numAttributes;
    this.npoints = numObjects;
    this.feature = buf;
    this.attributes = new float[this.npoints][this.nfeatures];
    this.randomPtr = new MyRandom();
    randomPtr.random_alloc();
    this.nclusters = 0;
    this.changeNClusters = false;
    this.counterILoop = 0;
    this.counterKMS = 0;
    this.new_centers_len = null;
    this.new_centers = null;
    this.best_nclusters = 0;
    this.cluster_centres = null;
    this.isSet4KMeans = false;
  }
  
  /*
   * 
   */
  public boolean initialize() {
    this.nclusters = this.min_nclusters;
    this.changeNClusters = false;
    this.changeNLoops = false;
    this.counterILoop = 0;
    this.counterKMS = 0;
    this.new_centers_len = null;
    this.new_centers = null;
    
    float[][] attributes = this.attributes;
    float[][] feature = this.feature;
    int npoints = this.npoints;
    int nfeatures = this.nfeatures;
    /*
     * Since zscore transform may perform in cluster() which modifies the
     * contents of attributes[][], we need to re-store the originals
     */
    for(int x = 0; x < npoints; x++) {
      for(int y = 0; y < nfeatures; y++) {
        attributes[x][y] = feature[x][y];
      }
    }
    
    if(this.use_zscore_transform == 1) {
      zscoreTransform(attributes, npoints, nfeatures);
    }
  }

  /* =============================================================================
   * extractMoments
   * =============================================================================
   */
  public static float[] extractMoments (float []data, 
                                        int num_elts, 
                                        int num_moments) {
    float[] moments = new float[num_moments];

    float mzero=0.0f;
    for (int i = 0; i < num_elts; i++) {
      mzero += data[i];
    }

    moments[0] = mzero / num_elts;
    for (int j = 1; j < num_moments; j++) {
      moments[j] = 0;
      for (int i = 0; i < num_elts; i++) {
        moments[j] += (float) Math.pow((data[i]-moments[0]), j+1);
      }
      moments[j] = moments[j] / num_elts;
    }
    return moments;
  }

  /* =============================================================================
   * zscoreTransform
   * =============================================================================
   */
  public static void zscoreTransform (float[][] data, /* in & out: [numObjects][numAttributes] */
                                      int     numObjects,
                                      int     numAttributes) {
    float[] moments;
    float[] single_variable = new float[numObjects];
    for (int i = 0; i < numAttributes; i++) {
      for (int j = 0; j < numObjects; j++) {
        single_variable[j] = data[j][i];
      }
      moments = extractMoments(single_variable, numObjects, 2);
      moments[1] = (float) Math.sqrt((double)moments[1]);
      for (int j = 0; j < numObjects; j++) {
        data[j][i] = (data[j][i]-moments[0])/moments[1];
      }
    }
  }
  
  public void resetCounterKMS() {
    this.counterKMS = 0;
  }
  
  public void incCounterKMS() {
    this.counterKMS++;
  }
  
  public boolean isCounterKMSFull() {
    return (this.counterKMS == this.nthreads);
  }
  
  public void resetCounterILoop() {
    this.counterILoop = 0;
  }
  
  public boolean isILoopFinish() {
    return !((this.delta > this.threshold) && (this.counterILoop < 500 + 1));
  }
  
  public void setBestClusters() {
    this.best_nclusters = this.nclusters;
  }
  
  public void decNLoops() {
    if(!this.changeNLoops) {
      this.nloops--;
      this.changeNLoops = true;
    }
  }
  
  public boolean checkNLoops() {
    return (this.nloops > 0);
  }
  
  public void resetChangeNLoops() {
    this.changeNLoops = false;
  }
  
  public void incNClusters() {
    if(!this.changeNClusters) {
      this.nclusters++;
      this.changeNClusters = true;
    }
  }
  
  public boolean checkNClusters() {
    return (this.nclusters <= this.max_nclusters);
  }
  
  public void resetChangeNClusters() {
    this.changeNClusters = false;
  }
  
  public void resetIsSet4KMeans() {
    this.isSet4KMeans = false;
  }

  public void setKMeans(KMeans kms) {
    if(!this.isSet4KMeans) {
      this.randomPtr.random_seed(7);
      this.new_centers_len = new intwrapper[this.nclusters];
      this.new_centers = new float[this.nclusters][this.nfeatures];
      this.cluster_centres = new float[this.nclusters][this.nfeatures];
      float[][] cluster_centres = this.cluster_centres;
      float[][] attributes = this.attributes;
      int npoints = this.npoints;
      int nclusters = this.nclusters;
      int nfeatures = this.nfeatures;
      MyRandom randomPtr = this.randomPtr;
      /* Randomly pick cluster centers */
      for (int i = 0; i < nclusters; i++) {
        int n = (int)(randomPtr.random_generate() % npoints);
        for (int j = 0; j < nfeatures; j++) {
          cluster_centres[i][j] = attributes[n][j];
        }
      }
      this.delta = (float) 0.0;
      this.isSet4KMeans = true;
    }
    
    this.innerKMeansSetting(kms);
  }
  
  public void innerKMeansSetting(KMeans kms) {
    kms.setFeature(this.attributes);
    kms.setClusters(this.nclusters, this.cluster_centres);
  }
  
  public void resetDelta() {
    this.delta = (float) 0.0;
  }
  
  public void mergeKMeans(KMeans kms) {
    int nclusters = this.nclusters;
    int nfeatures = this.nfeatures;
    float[][] new_centers = this.new_centers;
    float[][] kmsnew_centers = kms.new_centers;
    intwrapper[] new_centers_len = this.new_centers_len;
    intwrapper[] kmsnew_centers_len = kms.new_centers_len;
               
    this.delta += kms.delta;
    for (int i = 0; i < nclusters; i++) {
      for (int j = 0; j < nfeatures; j++) {
        new_centers[i][j] += kmsnew_centers[i][j];
        kmsnew_centers[i][j] = (float)0.0;   /* set back to 0 */
      }
      new_centers_len[i] += kmsnew_centers_len[i];
      kmsnew_centers_len[i] = 0;   /* set back to 0 */
    }
    
    if(this.isCounterKMSFull()) {
      // finish one inner loop
      this.counterILoop++;
      
      float[][] cluster_centres = this.cluster_centres;
      /* Replace old cluster centers with new_centers */
      for (int i = 0; i < nclusters; i++) {
        for (int j = 0; j < nfeatures; j++) {
          if (new_centers_len[i] > 0) {
            cluster_centres[i][j] = new_centers[i][j] / new_centers_len[i];
          }
          new_centers[i][j] = (float)0.0;   /* set back to 0 */
        }
        new_centers_len[i] = 0;   /* set back to 0 */
      }

      this.delta /= this.npoints;
    }
  }
}
/* =============================================================================
 *
 * End of cluster.java
 *
 * =============================================================================
 */
