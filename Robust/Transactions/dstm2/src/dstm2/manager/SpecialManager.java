/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dstm2.manager;

import dstm2.SpecialLock;
import dstm2.Transaction;

/**
 *
 * @author navid
 */
public class SpecialManager extends PriorityManager{
    
    public void resolveConflict(Transaction me, Transaction other) {
        if (me == SpecialLock.getSpecialLock().getOwnerTransaction())
            other.abort();
        else if (other == SpecialLock.getSpecialLock().getOwnerTransaction())
            me.abort();

        else 
            super.resolveConflict(me, other);
      }

      public long getPriority() {
        throw new UnsupportedOperationException();
      }

      public void setPriority(long value) {
        throw new UnsupportedOperationException();
      }
}
