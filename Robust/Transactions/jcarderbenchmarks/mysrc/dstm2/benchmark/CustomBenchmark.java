/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dstm2.benchmark;

import TransactionalIO.benchmarks.benchmark;
import TransactionalIO.core.TransactionalFile;
import TransactionalIO.exceptions.GracefulException;
import dstm2.atomic;
import dstm2.benchmark.Counter.CountKeeper;
import dstm2.factory.Factory;
import dstm2.Thread;
import dstm2.util.Random;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 * @author navid
 */
public abstract class CustomBenchmark{
    
 public ReentrantLock programlock = new ReentrantLock();

   public static final Object lock = new Object();
  /**
   * local variable
   */
   int element;
  /**
   * local variable
   */
   int value;

  /**
   * Number of calls to insert()
   */
   int insertCalls = 0;
  /**
   * number of calls to contains()
   */
   int containsCalls = 0;
  /**
   * number of calls to remove()
   */
   int removeCalls = 0;
  /**
   * amount by which the set size has changed
   */
   int delta = 0;
 
   protected abstract void init();
   
   protected abstract void execute(Vector arguments);
   
   protected abstract void printResults();
    
   public CustomBenchmark() {
     init();
   }
   // public static Vector hotwords;


  
  /**
   * Prints an error message to <code>System.out</code>, including a
   *  standard header to identify the message as an error message.
   * @param s String describing error
   */
  protected static void reportError(String s) {
    System.out.println(" ERROR: " + s);
    System.out.flush();
  }
  
  public void report() {
    System.out.println("Insert/Remove calls:\t" + (insertCalls + removeCalls));
    System.out.println("Contains calls:\t" + containsCalls);
  }

    

  
  public void sanityCheck() {
    long expected =  delta;
    int length = 1;
    
    int prevValue = Integer.MIN_VALUE;
  /*  for (int value : this) {
      length++;
      if (value < prevValue) {
        System.out.println("ERROR: set  not sorted");
        System.exit(0);
      }
      if (value == prevValue) {
        System.out.println("ERROR: set has duplicates!");
        System.exit(0);
      }
      if (length == expected) {
        System.out.println("ERROR: set has bad length!");
        System.exit(0);
      }
     
    }*/
    System.out.println("Integer Set OK");
  }
  



 }
