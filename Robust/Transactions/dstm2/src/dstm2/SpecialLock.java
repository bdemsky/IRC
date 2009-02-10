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
            //System.out.println("trying to lock " + Thread.currentThread());
            //super.lock();
            while (locked)
                wait();
            locked = true;
            setOwnerTransaction(tr);
            Thread.getTransaction().setIOTransaction(true);
           // System.out.println(Thread.currentThread() + " locked the lock");
        } catch (InterruptedException ex) {
            Logger.getLogger(SpecialLock.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public synchronized void unlock(Transaction tr){
     //   System.out.println(Thread.currentThread() + " unlocking the lock");
        //super.unlock();
        locked = false;
        setOwnerTransaction(null);
        Thread.getTransaction().setIOTransaction(false);
        notifyAll();
       // System.out.println(Thread.currentThread() + " unlocked the lock");
    }
    
    public synchronized void setOwnerTransaction(Transaction tr){
        ownerTransaction = tr;
    }
    
    public synchronized Transaction getOwnerTransaction(){
        return ownerTransaction;
    }
    
    public synchronized  static SpecialLock getSpecialLock(){
        if (instance == null){ 
           // System.out.println(Thread.currentThread() + " lock");
            instance = new SpecialLock();
            return instance;
        }
        else{
           // System.out.println(Thread.currentThread() + " lock-ret");
            return instance;    
        }
        
    }

}
