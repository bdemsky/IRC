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
    
    protected byte[] data;
    protected Range range;
    private boolean unknownoffset;
    protected TransactionLocalFileAttributes belongingto;
    protected TransactionalFile ownertransactionalfile;

    public WriteOperations(byte[] data, Range range, boolean unknownoffset, TransactionalFile ownertransactionalfile, TransactionLocalFileAttributes belongingto) {
        this.data = data;
        this.range = range;
        this.unknownoffset = unknownoffset;
        this.ownertransactionalfile = ownertransactionalfile;
        this.belongingto = belongingto;
    }
    
    

   
  

    public boolean isUnknownoffset() {
        return unknownoffset;
    }

    public void setData(byte[] data) {
        this.data = new byte[data.length];
        System.arraycopy(data, 0, this.data, 0, data.length);
    }


    public void setUnknownoffset(boolean unknownoffset) {
        this.unknownoffset = unknownoffset;
    }


    
    public int compareTo(Object other) {
        WriteOperations tmp = (WriteOperations) other;
        return this.range.compareTo(tmp.range);
    }
    
    
    

}
