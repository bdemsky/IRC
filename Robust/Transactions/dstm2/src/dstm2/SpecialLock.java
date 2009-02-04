/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dstm2;


import java.util.concurrent.locks.ReentrantLock;

/**
 *
 * @author navid
 */
public class SpecialLock extends ReentrantLock{
    private static SpecialLock instance = null;
    private Transaction ownerTransaction = null;

    private SpecialLock() {
    }
    
    
    public synchronized void lock(Transaction tr){
        super.lock();
        setOwnerTransaction(ownerTransaction);
    }
    
    public synchronized void unlock(Transaction tr){
        super.unlock();
        setOwnerTransaction(null);
    }
    
    public synchronized void setOwnerTransaction(Transaction tr){
        ownerTransaction = tr;
    }
    
    public synchronized Transaction getOwnerTransaction(){
        return ownerTransaction;
    }
    
    public synchronized static SpecialLock getSpecialLock(){
        if (instance == null){ 
            instance = new SpecialLock();
            return instance;
        }
        else
            return instance;    
        
    }

}
