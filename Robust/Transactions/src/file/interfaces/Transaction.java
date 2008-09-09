/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dstm2.file.interfaces;

/**
 *
 * @author navid
 */
/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */


import dstm2.Transaction.Status;

/**
 *
 * @author navid
 */
public interface Transaction {

    /**
     * Tries to abort transaction
     * @return whether transaction was aborted (not necessarily by this call)
     */
    boolean abort();

    /**
     * Tries to commit transaction
     * @return whether transaction was committed
     */
    boolean commit();

    /**
     * This transaction's contention manager
     * @return the manager
     */
    ContentionManager getContentionManager();

    /**
     * Access the transaction's current status.
     * @return current transaction status
     */
    Status getStatus();

    /**
     * Tests whether transaction is aborted.
     * @return whether transaction is aborted
     */
    boolean isAborted();

    /**
     * Tests whether transaction is active.
     * @return whether transaction is active
     */
    boolean isActive();

    /**
     * Tests whether transaction is committed.
     * @return whether transaction is committed
     */
    boolean isCommitted();

    /**
     * Returns a string representation of this transaction
     * @return the string representcodes[ation
     */
    String toString();

    /**
     * Tests whether transaction is committed or active.
     * @return whether transaction is committed or active
     */
    boolean validate();

    /**
     * Block caller while transaction is active.
     */
    void waitWhileActive();

    /**
     * Block caller while transaction is active.
     */
    void waitWhileActiveNotWaiting();

    /**
     * Wake up any transactions waiting for this one to finish.
     */
    void wakeUp();

}

