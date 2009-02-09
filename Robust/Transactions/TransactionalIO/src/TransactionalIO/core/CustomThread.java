/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package TransactionalIO.core;

import TransactionalIO.exceptions.AbortedException;
import TransactionalIO.benchmarks.benchmark;
import TransactionalIO.interfaces.TransactionalProgram;
import java.util.Vector;

/**
 *
 * @author navid
 */
public class CustomThread implements Runnable{

    
   
    private static ThreadLocal onAbort = new ThreadLocal();
    private TransactionalProgram ioprogram;
    private static ThreadLocal/*<TransactionalProgram>*/ program = new ThreadLocal();
    public Thread runner;
   
    
   
    public CustomThread(TransactionalProgram ioprogram) {
        this.ioprogram = ioprogram;
        runner = new Thread(this);
        runner.start();
    }
    
    
    
  /*  public static void setTransaction(ExtendedTransaction transaction){
        transactioncontainer.set(transaction);
    }
    
    public static ExtendedTransaction getTransaction(){
        return (ExtendedTransaction) transactioncontainer.get(); 
    }*/
    
    public static void setProgram(TransactionalProgram transaction){
        program.set(transaction);
    }
    
    public static TransactionalProgram getProgram(){
        return (TransactionalProgram) program.get(); 
    }
    
    public static Vector getonAbort(){
        return (Vector) onAbort.get();
    }
    
    public static void setonAbort(Vector s){
        onAbort.set(s);
    }
    
    
    public void run() {
      
        setProgram(ioprogram);
       // setonAbort(new Vector());
//        System.out.println(Thread.currentThread().getName());
        while (true){
            try{
               /* getonAbort().add(new terminateHandler() {
                    public void cleanup() {
                        synchronized(benchmark.lock){
                            System.out.println(Thread.currentThread() +" KEWL");
                        }
                    }
                });
                */
                
                Wrapper.Initialize(null);
              //  transaction = new ExtendedTransaction();
               // setTransaction(transaction);
                synchronized(benchmark.lock){
                    benchmark.transacctions.add(Wrapper.getTransaction());
                }
                ioprogram.execute();
                Wrapper.prepareIOCommit();
                
                Wrapper.commitIO();
                 break;
            }
            catch (AbortedException e){
              /*  Iterator it = getonAbort().iterator();
                while(it.hasNext()) {
                    terminateHandler th = (terminateHandler)it.next();
                    th.cleanup();
                }
                getonAbort().clear();*/
                
                
             /*   synchronized(benchmark.lock){
                    System.out.println(Thread.currentThread() +" retried");
                }*/
               
            }
            finally{
                Wrapper.getTransaction().unlockAllLocks();
            }
        }
    }
    

   
}
