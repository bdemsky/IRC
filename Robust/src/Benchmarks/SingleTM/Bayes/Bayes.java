/* =============================================================================
 *
 * bayes.java
 *
 * =============================================================================
 *
 * Copyright (C) Stanford University, 2006.  All Rights Reserved.
 * Author: Chi Cao Minh
 * Ported to Java June 2009 Alokika Dash
 * University of California, Irvine
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

#define PARAM_DEFAULT_QUALITY   1.0f
#define PARAM_EDGE      101
#define PARAM_INSERT    105
#define PARAM_NUMBER    110
#define PARAM_PERCENT   112
#define PARAM_RECORD    114
#define PARAM_SEED      115
#define PARAM_THREAD    116
#define PARAM_VAR       118

#define PARAM_DEFAULT_EDGE     -1
#define PARAM_DEFAULT_INSERT   1
#define PARAM_DEFAULT_NUMBER   4
#define PARAM_DEFAULT_PERCENT  10
#define PARAM_DEFAULT_RECORD   4096
#define PARAM_DEFAULT_SEED     1
#define PARAM_DEFAULT_THREAD   1
#define PARAM_DEFAULT_VAR      32

public class Bayes extends Thread {
  public int[] global_params; /* 256 = ascii limit */
  public int global_maxNumEdgeLearned;
  public int global_insertPenalty;
  public float global_operationQualityFactor;

  /* Number of threads */
  int numThread;

  /* thread id */
  int myId;

  /* Global learn pointer */
  Learner learnerPtr;

  public Bayes() {
    global_params = new int[256];
    global_maxNumEdgeLearned = PARAM_DEFAULT_EDGE;
    global_insertPenalty = PARAM_DEFAULT_INSERT;
    global_operationQualityFactor = PARAM_DEFAULT_QUALITY;
  }

  public Bayes(int numThread, int myId, Learner learnerPtr) {
    this.numThread = numThread;
    this.myId = myId;
    this.learnerPtr = learnerPtr;
  }


  /* =============================================================================
   * displayUsage
   * =============================================================================
   */
  public void
    displayUsage ()
    {
      System.out.println("Usage: ./Bayes.bin [options]");
      System.out.println("    e Max [e]dges learned per variable  ");
      System.out.println("    i Edge [i]nsert penalty             ");
      System.out.println("    n Max [n]umber of parents           ");
      System.out.println("    p [p]ercent chance of parent        ");
      System.out.println("    q Operation [q]uality factor        ");
      System.out.println("    r Number of [r]ecords               ");
      System.out.println("    s Random [s]eed                     ");
      System.out.println("    t Number of [t]hreads               ");
      System.out.println("    v Number of [v]ariables             ");
      System.exit(1);
    }


  /* =============================================================================
   * setDefaultParams
   * =============================================================================
   */
  public void
    setDefaultParams ()
    {
      global_params[PARAM_EDGE]    = PARAM_DEFAULT_EDGE;
      global_params[PARAM_INSERT]  = PARAM_DEFAULT_INSERT;
      global_params[PARAM_NUMBER]  = PARAM_DEFAULT_NUMBER;
      global_params[PARAM_PERCENT] = PARAM_DEFAULT_PERCENT;
      global_params[PARAM_RECORD]  = PARAM_DEFAULT_RECORD;
      global_params[PARAM_SEED]    = PARAM_DEFAULT_SEED;
      global_params[PARAM_THREAD]  = PARAM_DEFAULT_THREAD;
      global_params[PARAM_VAR]     = PARAM_DEFAULT_VAR;
    }


  /* =============================================================================
   * parseArgs
   * =============================================================================
   */
  public static void
    parseArgs (String[] args, Bayes b)
    {
      int i = 0;
      String arg;
      b.setDefaultParams();
      while(i < args.length && args[i].startsWith("-")) {
        arg = args[i++];
        //check options
        if(arg.equals("-e")) {
          if(i < args.length) {
            b.global_params[PARAM_EDGE] = new Integer(args[i++]).intValue();
          }
        } else if(arg.equals("-i")) {
          if (i < args.length) {
            b.global_params[PARAM_INSERT] = new Integer(args[i++]).intValue();
          }
        } else if (arg.equals("-n")) {
          if (i < args.length) {
            b.global_params[PARAM_NUMBER] = new Integer(args[i++]).intValue();
          }
        } else if (arg.equals("-p")) {
          if (i < args.length) {
            b.global_params[PARAM_PERCENT] = new Integer(args[i++]).intValue();
          }
        } else if (arg.equals("-r")) {
          if (i < args.length) {
            b.global_params[PARAM_RECORD] = new Integer(args[i++]).intValue();
          }
        } else if (arg.equals("-s")) {
          if (i < args.length) {
            b.global_params[PARAM_SEED] = new Integer(args[i++]).intValue();
          }
        } else if (arg.equals("-t")) {
          if (i < args.length) {
            b.global_params[PARAM_THREAD] = new Integer(args[i++]).intValue();
          }
        } else if (arg.equals("-v")) {
          if (i < args.length) {
            b.global_params[PARAM_VAR] = new Integer(args[i++]).intValue();
          }
        } else if(arg.equals("-h")) {
          b.displayUsage();
        }
      }

      if (b.global_params[PARAM_THREAD] == 0) {
        b.displayUsage();
      }
    }


  /* =============================================================================
   * score
   * =============================================================================
   */
  public float
    score (Net netPtr, Adtree adtreePtr)
    {
      /*
       * Create dummy data structures to conform to learner_score assumptions
       */

      Data dataPtr = Data.data_alloc(1, 1, null);

      Learner learnerPtr = Learner.learner_alloc(dataPtr, adtreePtr, 1, global_insertPenalty, global_maxNumEdgeLearned, global_operationQualityFactor);

      Net tmpNetPtr = learnerPtr.netPtr;
      learnerPtr.netPtr = netPtr;

      float score = learnerPtr.learner_score();

      learnerPtr.netPtr = tmpNetPtr;
      learnerPtr.learner_free();
      dataPtr.data_free();

      return score;
    }


  /**
   * parallel execution
   **/
  public void run() {
    Barrier.enterBarrier();
    Learner.createTaskList(myId, numThread, learnerPtr);
    Barrier.enterBarrier();

    Barrier.enterBarrier();
    Learner.learnStructure(myId, numThread, learnerPtr);
    Barrier.enterBarrier();
  }
    

  /* =============================================================================
   * main
   * =============================================================================
   */

  public static void main(String[] args) {
    /*
     * Initialization
     */
    Bayes b = new Bayes();
    Bayes.parseArgs(args, b);
    int numThread     = b.global_params[PARAM_THREAD];
    int numVar        = b.global_params[PARAM_VAR];
    int numRecord     = b.global_params[PARAM_RECORD];
    int randomSeed    = b.global_params[PARAM_SEED];
    int maxNumParent  = b.global_params[PARAM_NUMBER];
    int percentParent = b.global_params[PARAM_PERCENT];
    b.global_insertPenalty = b.global_params[PARAM_INSERT];
    b.global_maxNumEdgeLearned = b.global_params[PARAM_EDGE];

    /* Initiate Barriers */
    Barrier.setBarrier(numThread);

    Bayes[] binit = new Bayes[numThread];

    System.out.println("Number of threads          " + numThread);
    System.out.println("Random seed                " + randomSeed);
    System.out.println("Number of vars             " + numVar);
    System.out.println("Number of records          " + numRecord);
    System.out.println("Max num parents            " + maxNumParent);
    System.out.println("%% chance of parent        " + percentParent);
    System.out.println("Insert penalty             " + b.global_insertPenalty);
    System.out.println("Max num edge learned / var " + b.global_maxNumEdgeLearned);
    System.out.println("Operation quality factor   " + b.global_operationQualityFactor);

    /*
     * Generate data
     */

    System.out.print("Generating data... ");

    Random randomPtr = new Random();
    randomPtr.random_alloc();
    randomPtr.random_seed(randomSeed);

    Data dataPtr = Data.data_alloc(numVar, numRecord, randomPtr); 

    Net netPtr = dataPtr.data_generate(-1, maxNumParent, percentParent);
    System.out.println("done.");

    /*
     * Generate adtree
     */

    Adtree adtreePtr = Adtree.adtree_alloc();

    System.out.print("Generating adtree... ");

    adtreePtr.adtree_make(dataPtr);

    System.out.println("done.");

    /*
     * Score original network
     */

    float actualScore = b.score(netPtr, adtreePtr);
    netPtr.net_free();

    /*
     * Learn structure of Bayesian network
     */

    Learner learnerPtr = Learner.learner_alloc(dataPtr, adtreePtr, numThread, b.global_insertPenalty, b.global_maxNumEdgeLearned, b.global_operationQualityFactor);

    dataPtr.data_free(); /* save memory */

    System.out.print("Learning structure...");

    /* Create and Start Threads */
    for(int i = 1; i<numThread; i++) {
      binit[i] = new Bayes(i, numThread, learnerPtr);
    }

    for(int i = 1; i<numThread; i++) {
      binit[i].start();
    }


    /** 
      * Parallel work by all threads
      **/

    Barrier.enterBarrier();
    Learner.createTaskList(0, numThread, learnerPtr);
    Barrier.enterBarrier();

    Barrier.enterBarrier();
    Learner.learnStructure(0, numThread, learnerPtr);
    Barrier.enterBarrier();

    System.out.println("done.");

    /*
     * Check solution
     */

    boolean status = learnerPtr.netPtr.net_isCycle();
    if(status) {
      System.out.println("Assert failed: system has an incorrect result");
      System.exit(0);
    }

#ifndef SIMULATOR
    float learnScore = learnerPtr.learner_score();
    System.out.println("Learn score= " + (double)learnScore);
#endif
    System.out.println("Actual score= " + (double)actualScore);

    /*
     * Clean up
     */

#ifndef SIMULATOR
    adtreePtr.adtree_free();
#  if 0    
    learnerPtr.learner_free();
#  endif    
#endif

  }
}
/* =============================================================================
 *
 * End of bayes.java
 *
 * =============================================================================
 */
