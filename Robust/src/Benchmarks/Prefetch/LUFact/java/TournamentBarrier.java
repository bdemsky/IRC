/**************************************************************************
 *                                                                         *
 *         Java Grande Forum Benchmark Suite - Thread Version 1.0          *
 *                                                                         *
 *                            produced by                                  *
 *                                                                         *
 *                  Java Grande Benchmarking Project                       *
 *                                                                         *
 *                                at                                       *
 *                                                                         *
 *                Edinburgh Parallel Computing Centre                      *
 *                                                                         *
 *                email: epcc-javagrande@epcc.ed.ac.uk                     *
 *                                                                         *
 *                                                                         *
 *      This version copyright (c) The University of Edinburgh, 2001.      *
 *                         All rights reserved.                            *
 *                                                                         *
 **************************************************************************/

// This implements a simple tournament-based barrier, using entirely its
// own synchronisation. At present Yield() is called to stop busy-waiting
// processes hogging the processor(s)!

import java.util.*;

public class TournamentBarrier {
  // Array of flags indicating whether the given process and all those
  // for which it is responsible have finished. The "sense" of this
  // array alternates with each barrier, to prevent having to
  // reinitialise.
  boolean[] IsDone;
  public int maxBusyIter;
  int numThreads;

  public TournamentBarrier(int n) {
    numThreads = n;
    maxBusyIter = 1;
    // Superclass constructor should record the number of threads
    // and thread manager.
    //super(n);

    // Initialise the IsDone array. The choice of initial value is
    // arbitrary, but must be consistent!
    IsDone = new boolean[numThreads];
    for(int i = 0; i < n; i++) {
      IsDone[i] = false;
    }
  }

  // Uses the manager's debug function, so this can only be used after
  // construction!
  public void debug(String s) {
    // System.err.println("Debug message");
  }

  public void setMaxBusyIter(int b) {
    maxBusyIter = b;
  }

  public void DoBarrier(int myid) {
    int b;
    // debug("Thread " + myid + " checking in");

    int roundmask = 3;
    boolean donevalue = !IsDone[myid];

    while(((myid & roundmask) == 0) && (roundmask<(numThreads<<2))) {
      int spacing = (roundmask+1) >> 2;
      for(int i=1; i<=3 && myid+i*spacing < numThreads; i++) {
        // debug("Thread " + myid + " waiting for thread " + (myid+i*spacing));
        b = maxBusyIter;
        while(IsDone[myid+i*spacing] != donevalue) {
          b--;
          if(b==0) {
           //Thread.yield();
            b = maxBusyIter;
          }
        }
      }
      roundmask = (roundmask << 2) + 3;
    }
    // debug("Thread " + myid + " reporting done");
    IsDone[myid] = donevalue;
    b = maxBusyIter;
    while(IsDone[0] != donevalue) {
      b--;
      if(b==0) {
        //Thread.yield();
        b = maxBusyIter;
      }
    }
    //debug("Thread " + myid + " checking out");

  }
}
