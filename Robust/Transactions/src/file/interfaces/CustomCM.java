/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dstm2.file.interfaces;

import dstm2.Transaction.Status;
import java.util.Collection;
import dstm2.file.factory.TransactionalFile;

/**
 *
 * @author navid
 */
public class CustomCM implements ContentionManager{

    public void resolveConflict(Transaction me, Transaction other, TransactionalFile obj) {
        if (other != null)
            if (other.getStatus() == Status.ACTIVE || other.getStatus() == Status.COMMITTED)
                other.waitWhileActiveNotWaiting();
    }

    public void resolveConflict(Transaction me, Collection<Transaction> other) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public long getPriority() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void setPriority(long value) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void openSucceeded() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void committed() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void resolveConflict(Transaction me, Transaction other) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

}
