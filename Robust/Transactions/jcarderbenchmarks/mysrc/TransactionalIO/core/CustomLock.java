/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package TransactionalIO.core;


import TransactionalIO.benchmarks.benchmark;
import TransactionalIO.benchmarks.thread1;
import TransactionalIO.interfaces.TransactionalProgram;

/**
 *
 * @author navid
 */
public class CustomLock implements Runnable{

    private static ThreadLocal transactioncontainer = new ThreadLocal();
    private TransactionalProgram ioprogram;
    private static ThreadLocal/*<TransactionalProgram>*/ program = new ThreadLocal();
    private ExtendedTransaction transaction;
    public Thread runner;
    
   
    public CustomLock(TransactionalProgram ioprogram) {
        this.ioprogram = ioprogram;
        transaction = new ExtendedTransaction();
        runner = new Thread(this);
        runner.start();
    }
    
    public static void setTransaction(ExtendedTransaction transaction){
        transactioncontainer.set(transaction);
    }
    
    public static ExtendedTransaction getTransaction(){
        return (ExtendedTransaction) transactioncontainer.get(); 
    }
    
    public static void setProgram(TransactionalProgram transaction){
        program.set(transaction);
    }
    
    public static TransactionalProgram getProgram(){
        return (TransactionalProgram) program.get(); 
    }
    
    
    public void run() {
        setTransaction(transaction);
        setProgram(ioprogram);
        synchronized(benchmark.lock){
            benchmark.transacctions.add(transaction);
        }
//        System.out.println(Thread.currentThread().getName());
        ioprogram.execute();
        transaction.prepareCommit();
        transaction.commitChanges();
    }
    
}
