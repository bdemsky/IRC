/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dstm2.benchmark;

import TransactionalIO.core.MyDefaults;
import dstm2.Defaults;
import dstm2.Thread;
import dstm2.SpecialTransactionalFile;
import TransactionalIO.core.TransactionalFile;
import java.io.RandomAccessFile;
import java.io.File;
import java.io.IOException;

/**
 *
 * @author navid
 */
public class Main {

    public static void main(String args[]){
	// Code For Inserting Words From Random File to The Binary Tree
    int numThreads = Defaults.THREADS;
    int numMillis  = Defaults.TIME;
    int experiment = Defaults.EXPERIMENT;
    String managerClassName  = Defaults.MANAGER;
    Class managerClass = null;
    String benchmarkClassName = null;
    Class benchmarkClass = null;

    
    String adapterClassName = Defaults.ADAPTER;
    
    // discard statistics from previous runs
    Thread.clear();
    
    // Parse and check the args
    int argc = 0;
    int version=0;
    try {
      while (argc < args.length) {
        String option = args[argc++];
        if (option.equals("-m")){
          managerClassName = args[argc];
        }
       
        
        
        else if (option.equals("-b")){
          benchmarkClassName = args[argc];
        }
        else if (option.equals("-inevitable")){
          Defaults.INEVITABLE = Boolean.parseBoolean(args[argc]);
        }
        else if (option.equals("-t"))
          numThreads = Integer.parseInt(args[argc]);
        else if (option.equals("-a"))
          adapterClassName = args[argc];
        else if (option.equals("-version"))
          version = Integer.parseInt(args[argc]);
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
       System.out.println("Inevitable: " + Defaults.INEVITABLE);
       System.out.println(managerClassName);    
      managerClass = Class.forName(managerClassName);
            System.out.println(managerClass);    
      Thread.setContentionManagerClass(managerClass);
    } catch (ClassNotFoundException ex) {
      reportUsageErrorAndDie();
    }
    
    // Initialize adapter class
    Thread.setAdapterClass(adapterClassName);
    RandomAccessFile f=null; 
    //SpecialTransactionalFile f2=null; 
    //TransactionalFile f3=null; 

    try{ 
	    f= new RandomAccessFile("/scratch/TransactionalIO/test", "rw");
	    byte[] data;
	    data = new byte[1023];
	    for (int i=0; i<data.length; i++)
        	   data[i] = (byte)'b';

	    for (int j=0; j<3200; j++)
      	           f.write(data);

           // f.seek(0);
    	   // f2 = new SpecialTransactionalFile("/scratch/TransactionalIO/test", "rw");
    	  //  f3 = new TransactionalFile("/scratch/TransactionalIO/test", "rw");

    }catch(IOException e){
	e.printStackTrace();
    }

//    CustomThread.globallocktestfile = f;
//    CustomThread.inevitabletestfile = f2;
//    CustomThread.outoftransactiontestfile = f3;
    System.out.println("Initi Finished: ");


   // Set up the benchmark
    long startTime = 0;
    
    CustomThread[] thread = new CustomThread[numThreads];
    System.out.println("Benchmark: " + benchmarkClassName);
    System.out.println("Adapter: " + adapterClassName);
    System.out.println("Contention manager: " + managerClassName);
    System.out.println("Threads: " + numThreads);
    System.out.println("Mix: " + experiment + "% updates");
    
 
    try {
       for (int i = 0; i < numThreads; i++){
             
            thread[i] = new CustomThread(benchmarkClassName, version);
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
     // benchmark.sanityCheck();
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
    //benchmark.report();
    System.out.println("Elapsed time: " + elapsed + " seconds.");
    System.out.println("------------------------------------------");


    new File("/scratch/TransactionalIO/test").delete();
    
    
    }
    private static void reportUsageErrorAndDie() {
        System.out.println("usage: dstm2.Main -b <benchmarkclass> [-m <managerclass>] [-t <#threads>] [-n <#time-in-ms>] [-e <experiment#>] [-a <adapter>]");
        System.exit(0); 
    }

}
