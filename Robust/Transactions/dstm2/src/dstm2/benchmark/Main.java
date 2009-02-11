/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dstm2.benchmark;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Level;
import dstm2.Thread;
import dstm2.Defaults;
import java.io.File;


/**
 *
 * @author navid
 */
public class Main {

    /**
     * @param args the command line arguments
     * The first args is the name of the output file args[0]
     * The second args is the name of the randomwords input file args[1]
     * The third args is the name of the sequential words input file args[2]
     */
    public static void main(String[] args) {
        
        // Code For Inserting Words From Random File to The Binary Tree
        
    int numThreads = 20;//THREADS;
    int numMillis  = Defaults.TIME;
    int experiment = Defaults.EXPERIMENT;
    String managerClassName = Defaults.MANAGER;
    Class managerClass = null;
    String benchmarkClassName = null;
    Class benchmarkClass = null;
    double ii = (double)new File("/home/navid/iliad.text").length() / (double)20;
    
    System.out.println(Math.ceil(ii));
    String adapterClassName = Defaults.ADAPTER;
    
    // discard statistics from previous runs
    Thread.clear();
    // Parse and check the args
    int argc = 0;
    try {
      while (argc < args.length) {
        String option = args[argc++];
        if (option.equals("-m"))
          managerClassName = args[argc];
        else if (option.equals("-b"))
          benchmarkClassName = args[argc];
        else if (option.equals("-t"))
          numThreads = Integer.parseInt(args[argc]);
        else if (option.equals("-n"))
          numMillis = Integer.parseInt(args[argc]);
        else if (option.equals("-e"))
          experiment = Integer.parseInt(args[argc]);
        else if (option.equals("-a"))
          adapterClassName = args[argc];
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
    
    // Initialize contention manager.
    try {
      managerClass = Class.forName(Defaults.MANAGER);
      Thread.setContentionManagerClass(managerClass);
    } catch (ClassNotFoundException ex) {
      reportUsageErrorAndDie();
    }
    
    // Initialize adapter class
    Thread.setAdapterClass(adapterClassName);
    
    // initialize benchmark
    Benchmark benchmark = null;
    try {
      benchmarkClass = Class.forName(benchmarkClassName);
      benchmark = (Benchmark) benchmarkClass.newInstance();
      
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
    
    Thread[] thread = new Thread[numThreads];
    System.out.println("Benchmark: " + benchmarkClass);
    System.out.println("Adapter: " + adapterClassName);
    System.out.println("Contention manager: " + managerClassName);
    System.out.println("Threads: " + numThreads);
    System.out.println("Mix: " + experiment + "% updates");
    
    
        TransactionalIO.benchmarks.benchmark.init();
    
   // System.out.println((char)97);
    int j = 97;
    try {
        for (int i = 0; i < numThreads; i++){
             
            //thread[i] = benchmark.createThread(experiment, (char)j);
            thread[i] = benchmark.createThread(experiment, (char)j);
            j++;
        }
      
      startTime = System.currentTimeMillis();
      for (int i = 0; i < numThreads; i++)
        thread[i].start();
     // Thread.sleep(numMillis);
    //  Thread.stop = true;     // notify threads to stop
      for (int i = 0; i < numThreads; i++) {
        thread[i].join();
      }
    } catch (Exception e) {
      e.printStackTrace(System.out);
      System.exit(0);
    }
    long stopTime = System.currentTimeMillis();
    
    double elapsed = (double)(stopTime - startTime) / 1000.0;
    
    // Run the sanity check for this benchmark
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
    System.out.println("------------------------------------------");
    
  
  
        
    }
      private static void reportUsageErrorAndDie() {
    System.out.println("usage: dstm2.Main -b <benchmarkclass> [-m <managerclass>] [-t <#threads>] [-n <#time-in-ms>] [-e <experiment#>] [-a <adapter>]");
    System.exit(0);
  }

}
