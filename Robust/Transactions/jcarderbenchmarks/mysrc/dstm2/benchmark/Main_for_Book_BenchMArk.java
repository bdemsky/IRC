/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dstm2.benchmark;

import dstm2.Defaults;
import dstm2.Thread;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Vector;
import javax.swing.KeyStroke;

/**
 *
 * @author navid
 */
public class Main_for_Book_BenchMArk {
    public static void main(String args[]){
           // Code For Inserting Words From Random File to The Binary Tree
    int numThreads = 2;//THREADS;
    int numMillis  = Defaults.TIME;
    int experiment = Defaults.EXPERIMENT;
    String managerClassName = Defaults.MANAGER;
    Class managerClass = null;
    String benchmarkClassName = null;
    Class benchmarkClass = null;
    
    
    
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
    CustomBenchmark benchmark = null;
    try {
      benchmarkClass = Class.forName(benchmarkClassName);
      benchmark = (CustomBenchmark) benchmarkClass.newInstance();
      
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
    
    CustomThread[] thread = new CustomThread[numThreads];
    System.out.println("Benchmark: " + benchmarkClass);
    System.out.println("Adapter: " + adapterClassName);
    System.out.println("Contention manager: " + managerClassName);
    System.out.println("Threads: " + numThreads);
    System.out.println("Mix: " + experiment + "% updates");
    TransactionalIO.benchmarks.benchmark.init();
    
  
  //  for(int k=0 ; k<400; k++){
    
    
   // System.out.println((char)97);
    int j = 97;
    try {
       for (int i = 0; i < numThreads; i++){
             
            //thread[i] = benchmark.createThread(experiment, (char)j);
           
            thread[i] = new CustomThread(benchmark);
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
    
   /* BufferedReader dataIn = new BufferedReader(new
                InputStreamReader( System.in) );
      
    String name = "";
    try{
    name = dataIn.readLine();
        }catch( IOException e ){
            System.out.println("Error!");
        }*/
 //   }
  //  benchmark.printResults();
        
    }
    private static void reportUsageErrorAndDie() {
        System.out.println("usage: dstm2.Main -b <benchmarkclass> [-m <managerclass>] [-t <#threads>] [-n <#time-in-ms>] [-e <experiment#>] [-a <adapter>]");
        System.exit(0); 
    }

}
