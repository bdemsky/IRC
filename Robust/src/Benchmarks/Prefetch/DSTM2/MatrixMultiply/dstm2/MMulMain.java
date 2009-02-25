/*
 * Main.java
 *
 * Copyright 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, U.S.A.  All rights reserved.  
 * 
 * Sun Microsystems, Inc. has intellectual property rights relating to
 * technology embodied in the product that is described in this
 * document.  In particular, and without limitation, these
 * intellectual property rights may include one or more of the
 * U.S. patents listed at http://www.sun.com/patents and one or more
 * additional patents or pending patent applications in the U.S. and
 * in other countries.
 * 
 * U.S. Government Rights - Commercial software.
 * Government users are subject to the Sun Microsystems, Inc. standard
 * license agreement and applicable provisions of the FAR and its
 * supplements.  Use is subject to license terms.  Sun, Sun
 * Microsystems, the Sun logo and Java are trademarks or registered
 * trademarks of Sun Microsystems, Inc. in the U.S. and other
 * countries.  
 * 
 * This product is covered and controlled by U.S. Export Control laws
 * and may be subject to the export or import laws in other countries.
 * Nuclear, missile, chemical biological weapons or nuclear maritime
 * end uses or end users, whether direct or indirect, are strictly
 * prohibited.  Export or reexport to countries subject to
 * U.S. embargo or to entities identified on U.S. export exclusion
 * lists, including, but not limited to, the denied persons and
 * specially designated nationals lists is strictly prohibited.
 */

package dstm2;
import static dstm2.Defaults.*;
import dstm2.benchmark.MMulBenchMark;
import dstm2.AtomicArray;

public class MMulMain {
  
  /**
   * @param args the command line arguments
   * usage: dstm.benchmark.Main  -b <benchmarkclass> [-m <managerclass>] [-t <#threads>] [-n <#time-in-ms>] [-e <experiment#>] [-f <factory>"
   */
  public static void main(String args[]) {
    int numThreads = THREADS;
    int numMillis  = TIME;
    int experiment = EXPERIMENT;
    String managerClassName = MANAGER;
    Class managerClass = null;
    String benchmarkClassName = null;
    Class benchmarkClass = null;
    
    String adapterClassName = Defaults.ADAPTER;
    
    
    // setting for matrix
    int SIZE = 600;	
    
    // discard statistics from previous runs
    Thread.clear();
    // Parse and check the args
    int argc = 0;
    try {
      while (argc < args.length) {
        String option = args[argc++];
        
        if (option.equals("-b"))
        	benchmarkClassName = args[argc];
        else if (option.equals("-t"))
        	numThreads = Integer.parseInt(args[argc]);
        else if (option.equals("-n"))
        	numMillis = Integer.parseInt(args[argc]);
        else if (option.equals("-s"))					// parse size  	
        	SIZE = Integer.parseInt(args[argc]);
        else
          reportUsageErrorAndDie();
        argc++;
      }
    } catch (NumberFormatException e) {
      System.out.println("Expected a number: " + args[argc]);
      System.exit(0);
    } catch (Exception e) {
      reportUsageErrorAndDie();
    }
    
////////////////////////Don't know what they are /////////////
    // Initialize contention manager.
    try {
      managerClass = Class.forName(MANAGER);
      Thread.setContentionManagerClass(managerClass);
    } catch (ClassNotFoundException ex) {
      reportUsageErrorAndDie();
    }
    
    // Initialize adapter class
    Thread.setAdapterClass(adapterClassName);
    
/////////////////////////////////////////////////////////////
    
    // initialize benchmark
    MMulBenchMark benchmark = null;
    try {
      benchmarkClass = Class.forName(benchmarkClassName);
      benchmark = (MMulBenchMark) benchmarkClass.newInstance();
    } catch (InstantiationException e) {
      System.out.format("%s does not implement dstm.benchmark.Benchmark: %s\n", benchmarkClass, e);
      System.exit(0);
    } catch (ClassCastException e) {
      System.out.format("Exception when creating class %s: %s\n", benchmarkClass, e);
      System.exit(0);
    } catch (Exception e) {
      e.printStackTrace(System.out);
      System.exit(0);
    }
    
  
      
    // Set up the benchmark
    long startTime = 0;
    System.out.println("Testing start");
    
////////////// Setting for matrix multiplication
    int dataSet[] = {600,800,1200};	// size of mat
    int threadSet[] = {1,2,4,6,8};	// number of thread;
          
    for(int dSet:dataSet) {
    	for(int tSet : threadSet) {
    		numThreads = tSet;	// 1,2,4,6,8	
    		SIZE = dSet;		// 600,800,1200
       
    		System.out.println("Threads: " + numThreads);
    		System.out.println("Size   : " + SIZE);
    		Thread[] thread = new Thread[numThreads];
    
    		benchmark.init(SIZE,numThreads);
   
    		System.out.println("init completed");
       
    		try {
    			for (int i = 0; i < numThreads - 1; i++) {
    				// 	each thread will call different rows to compute Matrix multiplication   
    				thread[i] = benchmark.createThread(i);
    			}
    			// rests of matrix rows will be computed in the last thread
    			// all thread will share the same matrix but compute different rows
    			thread[numThreads-1] =  benchmark.createThread(numThreads-1);
      
    			startTime = System.currentTimeMillis();
      
    			for (int i = 0; i < numThreads; i++)
    				thread[i].start();
    			Thread.sleep(numMillis);
    			Thread.stop = true;     // notify threads to stop
    			for (int i = 0; i < numThreads; i++) {
    				thread[i].join();
    			}
    		} catch (Exception e) {
    			e.printStackTrace(System.out);
    			System.exit(0);
    		}
    		long stopTime = System.currentTimeMillis();
    
    		double elapsed = (double)(stopTime - startTime) / 1000.0;
    		
    	    System.out.println("Elapsed time: " + elapsed + " seconds.");
    	    System.out.println("----------------------------------------");
    	    benchmark.sanityCheck();
    	}	// tSet
    }	// dSet
  } 
    /*
    //Run the sanity check for this benchmark
    		try {
      benchmark.sanityCheck();
    } catch (Exception e) {
      e.printStackTrace(System.out);
    }
    
    long committed = Thread.totalCommitted;
    long total = Thread.totalTotal;
    if (total > 0) {
      System.out.printf("Committed: %d\nTotal: %d\nPercent committed: (%d%%)\n",
          committed,
          total,
          (100 * committed) / total);
    } else {
      System.out.println("No transactions executed!");
    }
    benchmark.report();
    System.out.println("Elapsed time: " + elapsed + " seconds.");
    System.out.println("----------------------------------------");
    
  }
 */
  
  private static void reportUsageErrorAndDie() {
    System.out.println("usage: dstm2.Main -b <benchmarkclass> [-m <managerclass>] [-t <#threads>] [-n <#time-in-ms>] [-e <experiment#>] [-a <adapter>]");
    System.exit(0);
  }
  
}

