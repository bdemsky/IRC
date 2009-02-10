/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package TransactionalIO.core;

import TransactionalIO.core.ExtendedTransaction.Status;
import TransactionalIO.exceptions.AbortedException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author navid
 */
public class MYLock {
    boolean offsetlocked = false;

    public MYLock() {
        offsetlocked = false;
    }
    
    synchronized void acquire(ExtendedTransaction me) {
        try {
            while (offsetlocked) {
                wait();
            }
            if (me.getStatus() == Status.ACTIVE) {
                //locking the offset
                offsetlocked = true;
            }

            /*if (me.getStatus() == Status.ACTIVE) {                        //locking the offset
            offsetlock.writeLock().lock();
            locked = true;
            }*/

            if (me.getStatus() != Status.ACTIVE) {
                /* if (locked) {
                if (me.toholoffsetlocks[me.offsetcount] == null) {
                me.toholoffsetlocks[me.offsetcount] = new ReentrantReadWriteLock();
                }
                me.toholoffsetlocks[me.offsetcount] = offsetlock;
                me.offsetcount++;
                //me.getHeldoffsetlocks().add(offsetlock);
                }*/

                offsetlocked = false;
                throw new AbortedException();
            }
        } catch (InterruptedException ex) {
            Logger.getLogger(MYLock.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    synchronized void release(ExtendedTransaction me) {
        offsetlocked = false;
        notifyAll();
        
    }
    
    synchronized void non_Transactional_Acquire() {
        try {
            while (offsetlocked) {
                wait();
            }
            offsetlocked = true;
        } catch (InterruptedException ex) {
            Logger.getLogger(MYLock.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    synchronized void non_Transactional_Release() {
        offsetlocked = false;
        notifyAll();
    }
    

}
