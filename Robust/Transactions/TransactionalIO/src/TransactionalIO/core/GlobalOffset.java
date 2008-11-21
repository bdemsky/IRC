/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package TransactionalIO.core;

import java.util.Vector;

/**
 *
 * @author navid
 */
public class GlobalOffset {
    private long offsetnumber;
    //private Vector<ExtendedTransaction> offsetReaders;
    private Vector offsetReaders = new Vector();
    private ExtendedTransaction offsetOwner;

    public GlobalOffset(long offsetnumber) {
        this.offsetnumber = offsetnumber;
    }
    

    public long getOffsetnumber() {
        return offsetnumber;
    }

    public ExtendedTransaction getOffsetOwner(){
        return offsetOwner;
    }
    
    public void setOffsetOwner(ExtendedTransaction ex){
        offsetOwner = ex;
    }
    
    public void setOffsetnumber(long offsetnumber) {
        this.offsetnumber = offsetnumber;
    }

    public Vector getOffsetReaders() {
        return offsetReaders;
    }

    public void setOffsetReaders(Vector offsetReaders) {
        this.offsetReaders = offsetReaders;
    }
    
    
    

}
