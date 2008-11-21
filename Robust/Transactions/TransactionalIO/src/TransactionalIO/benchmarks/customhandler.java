/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package TransactionalIO.benchmarks;

import TransactionalIO.core.CustomThread;
import TransactionalIO.core.ExtendedTransaction;
import TransactionalIO.core.ExtendedTransaction.Status;
import TransactionalIO.interfaces.TransactionStatu;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 *
 * @author navid
 */
    public class customhandler implements PropertyChangeListener{
    private Object abortcondition;    
        
    public void setAbortCondition(Object abortcondition){
        this.abortcondition = abortcondition;
    }

    public customhandler() {
    }

    
    public customhandler(Object abortcondition) {
        this.abortcondition = abortcondition;
    }

    public void propertyChange(PropertyChangeEvent e) {
        if (e.getNewValue().equals(abortcondition))
        if (e.getSource().getClass() == ExtendedTransaction.class){
            //if (e.getNewValue() == Status.ABORTED){
//                ((ExtendedTransaction)e.getSource()).memorystate.abort();
               // synchronized(benchmark.lock)
               // {
                    //System.out.println("1- " +Thread.currentThread() + " " + e.getNewValue());
                    //System.out.println("2- " +CustomThread.getTransaction());
               //     System.out.println("3- " +e.getSource());
                //}
            // Code for aborting the memory system for the correspondent Thread(transaction) 
            //Thread.getTransaction().abort();
            //}
        }
        else{ 
               //check to see if the memory is aborted
            e.getSource().getClass().cast(e.getSource()).hashCode();
               
        }
        
        if (e.getNewValue().equals(abortcondition))
            if (((TransactionStatu)e.getSource()).getOtherSystem() != null)
                ((TransactionStatu)e.getSource()).getOtherSystem().abortThisSystem();
    }

}
     

