/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package TransactionalIO.core;

import java.util.Vector;
import java.util.ArrayList;

/**
 *
 * @author navid
 */
public class GlobalOffset {
    private long offsetnumber;
    //private Vector<ExtendedTransaction> offsetReaders;
    private ArrayList offsetReaders = new ArrayList();

    public GlobalOffset(long offsetnumber) {
        this.offsetnumber = offsetnumber;
    }
    

    public long getOffsetnumber() {
        return offsetnumber;
    }

  
    
    public void setOffsetnumber(long offsetnumber) {
        this.offsetnumber = offsetnumber;
    }

    public ArrayList getOffsetReaders() {
        return offsetReaders;
    }

    public void setOffsetReaders(ArrayList offsetReaders) {
        this.offsetReaders = offsetReaders;
    }
    
    
    

}
