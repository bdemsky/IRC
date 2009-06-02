/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package TransactionalIO.core;


import TransactionalIO.interfaces.TransactionStatu;



/**
 *
 * @author navid
 */
public class Wrapper{
    
    
    private static ThreadLocal IOtransactioncontainer = new ThreadLocal();
  
    

    
    public static void prepareIOCommit(){
        
        getTransaction().prepareCommit();
    }
    
    public static void commitIO(){
        getTransaction().commitChanges();
    }
    
    
    
    public static void Initialize(TransactionStatu memory){
        ExtendedTransaction transaction = new ExtendedTransaction();
        setTransaction(transaction);
        transaction.setOtherSystem(memory);
        
        if (memory != null)
            memory.setOtherSystem(transaction);
    }
    
    public static void memoryCommit(){
        
    }
    
    
    public static void setTransaction(ExtendedTransaction transaction){
        IOtransactioncontainer.set(transaction);
    }
    
   
    
    public static ExtendedTransaction getTransaction(){
        return (ExtendedTransaction) IOtransactioncontainer.get(); 
    }
    


        
    
    
    
    

 
}



