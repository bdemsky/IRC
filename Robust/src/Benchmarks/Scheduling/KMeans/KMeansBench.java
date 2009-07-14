/** Bamboo version
 * 
 * @author jzhou
 *
 */
/* =============================================================================
 *
 * kmeans.java
 *
 * =============================================================================
 *
 * Description:
 *
 * Takes as input a file:
 *   ascii  file: containing 1 data point per line
 *   binary file: first int is the number of objects
 *                2nd int is the no. of features of each object
 *
 * This example performs a fuzzy c-means clustering on the data. Fuzzy clustering
 * is performed using min to max clusters and the clustering that gets the best
 * score according to a compactness and separation criterion are returned.
 *
 *
 * Author:
 *
 * Wei-keng Liao
 * ECE Department Northwestern University
 * email: wkliao@ece.northwestern.edu
 *
 *
 * Edited by:
 *
 * Jay Pisharath
 * Northwestern University
 *
 * Chi Cao Minh
 * Stanford University
 *
 * Port to Java version
 * Alokika Dash
 * University of California, Irvine
 *
 * =============================================================================
 *
 * ------------------------------------------------------------------------
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

task t1(StartupObject s{initialstate}) {
  //System.printString("task t1\n");
  
  // Benchmark settings
  int max_nclusters = 40;
  int min_nclusters = 40;
  float threshold = (float)0.00001;
  int use_zscore_transform = 1;
  int nthreads = 62;
  int nloops = 1;
  
  // Initialize object data
  int numRow = nthreads * 120;
  int numDim = 16 * 2; //5;
  int numAttributes = numDim;
  int numObjects = numRow;  // should be times of nthreads
  float[][] buf = new float[numObjects][numAttributes];
  // create object info here
  long seed = 0;
  Random rand = new Random(seed);
  if(false) {
    // Clustered random using gaussian
    /*int numCenter = 16;
    int maxInt = (~0x1) + 1;
    float[][] centers = new float[numCenter][numDim];
    for(int i = 0; i < numCenter; i++) {
      for(int j = 0; j < numDim; j++) {
        centers[i][j] = (float)rand.nextInt() / maxInt;
      }
    }
    //float sigma = ((float)1.0 / numCenter) * ((float)1.0 / numCenter) * ((float)1.0 / numCenter);
    for(int i = 0; i < numRow; i++) {
      //buf[i][0] = numRow;
      int ranI = Math.abs(rand.nextInt()) % numCenter;
      float[] center = centers[ranI];
      for(int j = 0; j < numDim; j++) {
        float noise = (float)rand.nextGaussian();
        buf[i][j] = center[j] + noise;
      }
    }*/
  } else {
    // uniform random
    int maxInt = (~0x1) + 1;
    for(int i = 0; i < numRow; i++) {
      //buf[i][0] = numRow;
      for(int j = 0; j < numDim; j++) {
        buf[i][j] = (float)rand.nextInt() / maxInt;
        //buf[i][j] = (float)(i * 20000 + j * 50000) / maxInt;
      }
    }
  }
  
  Cluster cluster = new Cluster(nloops, 
                                max_nclusters,
                                min_nclusters,
                                threshold,
                                use_zscore_transform,
                                nthreads,
                                numAttributes,
                                numObjects,
                                buf) {startLoop};

  /* Create parallel parts */
  for(int i = 0; i<nthreads; i++) {
    KMeans kms = new KMeans(i,
                            nthreads,
                            numAttributes,
                            numObjects,
                            0,
                            null,
                            null) {init};  // TODO
  }
  
  taskexit(s{!initialstate});
}

task t2(Cluster cluster{startLoop}) {
  //System.printString("task t2\n");
  
  cluster.initialize();
  
  taskexit(cluster{!startLoop, setKMeans});
}

task t3(Cluster cluster{setKMeans}, KMeans kms{init}) {
  //System.printString("task t3\n");
  
  cluster.incCounterKMS();
  cluster.setKMeans(kms);
  kms.init();
  
  if(cluster.isCounterKMSFull()) {
    // Merged all KMeans
    cluster.resetCounterKMS();
    cluster.resetIsSet4KMeans();
    taskexit(cluster{!setKMeans, toendLoop}, kms{!init, run});
  } else {
    taskexit(kms{!init, run});
  }
}

task t4(KMeans kms{run}) {
  //System.printString("task t4\n");
  
  kms.run();
  
  taskexit(kms{!run, turnin});
}

task t5(Cluster cluster{toendLoop}, KMeans kms{turnin}) {
  //System.printString("task t5\n");
 
  cluster.incCounterKMS();
  cluster.mergeKMeans(kms);
  
  if(cluster.isCounterKMSFull()) {
    // Merged all KMeans
    cluster.resetCounterKMS();
    taskexit(cluster{!toendLoop, resetKMeans}, kms{!turnin, reset});
  } else {
    taskexit(kms{!turnin, reset});
  }
}
 
task t6(Cluster cluster{resetKMeans}, KMeans kms{reset}) {
  //System.printString("task t6\n");

  cluster.incCounterKMS();
  boolean isILoopFinish = cluster.isILoopFinish();
  if(isILoopFinish) {
    // Inner loop finished
    cluster.setBestClusters();
    cluster.incNClusters(); 
    if(!cluster.checkNClusters()) {
      // Outer loop also finished
      cluster.decNLoops();
    }
  } else{
    // Go on for annother inner loop, goto t4
    cluster.innerKMeansSetting(kms);
  }

  if(cluster.isCounterKMSFull()) {
    // Merged all KMeans
    cluster.resetCounterKMS();
    cluster.resetChangeNClusters();
    cluster.resetChangeNLoops();

    // Check if inner loop is finished
    if(isILoopFinish) {
      // Inner loop finished, check if start another outer loop
      if(cluster.checkNClusters()) {
        // Start another outer loop, goto t3
        taskexit(cluster{!resetKMeans, setKMeans}, kms{!reset,init});
      } else {
        // Check if start another out most loop
        if(cluster.checkNLoops()) {
          // Start another out most loop, goto t2
          taskexit(cluster{!resetKMeans, startLoop}, kms{!reset,init});
        } else {
          // Terminate
          taskexit(cluster{!resetKMeans, finish}, kms{!reset});
        }
      }
    } else {
      // Go on for annother inner loop, goto t4
      cluster.resetDelta();
      taskexit(cluster{!resetKMeans, toendLoop},kms{!reset, run});
    } 
  } else {
    // Check if inner loop is finished
    if(isILoopFinish) {
      // Inner loop finished, check if start another outer loop
      if(cluster.checkNClusters()) {
        // Start another outer loop, goto t3
        taskexit(kms{!reset,init});
      } else {
        // Check if start another out most loop
        if(cluster.checkNLoops()) {
          // Start another out most loop, goto t2
          taskexit(kms{!reset,init});
        } else {
          // Terminate
          taskexit(kms{!reset});
        }
      }
    } else {
      // Go on for annother inner loop, goto t4
      taskexit(kms{!reset, run});
    }
  }
}
