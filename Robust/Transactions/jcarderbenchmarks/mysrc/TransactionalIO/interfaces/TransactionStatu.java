/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package TransactionalIO.interfaces;

import TransactionalIO.core.ExtendedTransaction.Status;

/**
 *
 * @author navid
 */
public interface TransactionStatu {
    
    public void abortThisSystem();
    public TransactionStatu getOtherSystem();
    public void setOtherSystem(TransactionStatu othersystem);
    public boolean isActive();
    public boolean isCommitted();
    public boolean isAborted();
    
}
