/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package TransactionalIO.core;

import TransactionalIO.Utilities.Range;

/**
 *
 * @author navid
 */
public class WriteOperations implements Comparable{
    
    private Byte[] data;
    private Range range;
    private boolean unknownoffset;
    private TransactionLocalFileAttributes belongingto;
    private TransactionalFile ownertransactionalfile;

    public WriteOperations(Byte[] data, Range range, boolean unknownoffset, TransactionalFile ownertransactionalfile, TransactionLocalFileAttributes belongingto) {
        this.data = data;
        this.range = range;
        this.unknownoffset = unknownoffset;
        this.ownertransactionalfile = ownertransactionalfile;
        this.belongingto = belongingto;
    }

    public TransactionalFile getOwnerTF() {
        return ownertransactionalfile;
    }

    public void setOwnerTF(TransactionalFile ownertransaction) {
        this.ownertransactionalfile = ownertransaction;
    }
    
    

    public Byte[] getData() {
        return data;
    }

    public Range getRange() {
        return range;
    }

    public boolean isUnknownoffset() {
        return unknownoffset;
    }

    public void setData(Byte[] data) {
        this.data = new Byte[data.length];
        System.arraycopy(data, 0, this.data, 0, data.length);
    }

    public void setRange(Range range) {
        this.range = range;
    }

    public void setUnknownoffset(boolean unknownoffset) {
        this.unknownoffset = unknownoffset;
    }

    public TransactionLocalFileAttributes getTFA() {
        return belongingto;
    }

    public void setTFA(TransactionLocalFileAttributes belongingto) {
        this.belongingto = belongingto;
    }
    
    
    public int compareTo(Object other) {
        WriteOperations tmp = (WriteOperations) other;
        return this.range.compareTo(tmp.range);
    }
    
    
    

}
