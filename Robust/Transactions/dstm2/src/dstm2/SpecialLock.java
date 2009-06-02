/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dstm2;


import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author navid
 */
public class SpecialLock{
    private static SpecialLock instance = null;
    private Transaction ownerTransaction = null;
    private boolean locked = false;

    private SpecialLock() {
    }
    
    
    public synchronized void lock(Transaction tr){
        try {
            while (locked)
                wait();
            locked = true;
            setOwnerTransaction(tr);
            Thread.getTransaction().setIOTransaction(true);
        } catch (InterruptedException ex) {
            Logger.getLogger(SpecialLock.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public synchronized void unlock(Transaction tr){
        locked = false;
        setOwnerTransaction(null);
        Thread.getTransaction().setIOTransaction(false);
        notifyAll();
    }
    
    public synchronized void setOwnerTransaction(Transaction tr){
        ownerTransaction = tr;
    }
    
    public synchronized Transaction getOwnerTransaction(){
        return ownerTransaction;
    }
    
    public synchronized  static SpecialLock getSpecialLock(){
        if (instance == null){ 
            instance = new SpecialLock();
            return instance;
        }
        else{
            return instance;    
        }
        
    }

}
