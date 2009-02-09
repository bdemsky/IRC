/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package TransactionalIO.core;

/**
 *
 * @author navid
 */
public class OffsetLock {
    boolean offsetlocked = false;

    public OffsetLock() {
        offsetlocked = false;
    }
    synchronized void acquire(ExtendedTransaction me) {
        
    }
    
    synchronized void release(ExtendedTransaction me) {
        
    }
    

}
