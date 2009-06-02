/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dstm2.manager;

import TransactionalIO.core.Wrapper;
import dstm2.SpecialLock;
import dstm2.Transaction;
import java.util.Collection;

/**
 *
 * @author navid
 */
public class SpecialManager extends AggressiveManager {

    @Override
    public void resolveConflict(Transaction me, Transaction other) {
  
        if (me == SpecialLock.getSpecialLock().getOwnerTransaction() && other.isActive()) {
            other.abort();
        } else if (other == SpecialLock.getSpecialLock().getOwnerTransaction()) {
            me.abort();
        } 
        else {
            super.resolveConflict(me, other);
       }
    }

    @Override
    public void resolveConflict(Transaction me, Collection<Transaction> others) {

        if (me == SpecialLock.getSpecialLock().getOwnerTransaction()) {
            for (Transaction other : others) {
                if (other.isActive() && other != me) {
                    other.abort();
                }
            }
            return;
        }
        else  for (Transaction other : others) {
            if (other == SpecialLock.getSpecialLock().getOwnerTransaction()){
                me.abort();
                return;
            }
        }
        
        super.resolveConflict(me, others);
    }

    public long getPriority() {
        throw new UnsupportedOperationException();
    }

    public void setPriority(long value) {
        throw new UnsupportedOperationException();
    }
}
