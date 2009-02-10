/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package TransactionalIO.core;

import TransactionalIO.core.ExtendedTransaction;

import TransactionalIO.core.CustomThread;
import TransactionalIO.interfaces.TransactionStatu;
import java.util.Vector;


/**
 *
 * @author navid
 */
public class Wrapper{
    
    
    private ExtendedTransaction transaction = new ExtendedTransaction();
    private static ThreadLocal IOtransactioncontainer = new ThreadLocal();
    private static ThreadLocal onMemoryAbort = new ThreadLocal();
    private static ThreadLocal onIOAbort = new ThreadLocal();
    

    
    public static void prepareIOCommit(){
        
        getTransaction().prepareCommit();
    }
    
    public static void commitIO(){
        getTransaction().commitChanges();
    }
    
    public static void realseOffsets(){
        getTransaction().commitOffset();
    }
    
    
    public static void Initialize(TransactionStatu memory){
        ExtendedTransaction transaction = new ExtendedTransaction();
        setTransaction(transaction);
        transaction.setOtherSystem(memory);
        
        if (memory != null)
            memory.setOtherSystem(transaction);
        
        
        
   /*     setonIOAbort(new Vector());
        setonMemoryAbort(new Vector());
        
        getonIOAbort().add(new terminateHandler() {
                    public void cleanup() {
                        Thread.getTransaction().abort();
                        synchronized(benchmark.lock){
                            System.out.println(Thread.currentThread() +" KEWL");
                        }
                    }
        });
        
        getonMemoryAbort().add(new terminateHandler() {
                    public void cleanup() {
                        CustomThread.getTransaction().abort();
                        synchronized(benchmark.lock){
                            System.out.println(Thread.currentThread() +" KEWL");
                        }
                    }
        });*/
    }
    
    public static void memoryCommit(){
        
    }
    
    
    public static void setTransaction(ExtendedTransaction transaction){
        IOtransactioncontainer.set(transaction);
    }
    
   
    
    public static ExtendedTransaction getTransaction(){
        return (ExtendedTransaction) IOtransactioncontainer.get(); 
    }
    
    public static void setonIOAbort(Vector vec){
        onIOAbort.set(vec);
    }
    
    
    private static Vector getonIOAbort(){
         return (Vector) onIOAbort.get();
    }

    public static void setonMemoryAbort(Vector vec){
        onMemoryAbort.set(vec);
    }
    
    
    private static Vector getonMemoryAbort(){
         return (Vector) onMemoryAbort.get();
    }

        
    
    
    
    

 
}



