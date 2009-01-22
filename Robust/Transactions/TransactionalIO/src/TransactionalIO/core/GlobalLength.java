/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package TransactionalIO.core;

import java.util.Vector;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 * @author navid
 */
public class GlobalLength {
   private long length;
    //private Vector<ExtendedTransaction> offsetReaders;
    private Vector lengthReaders = new Vector();
    private ExtendedTransaction offsetOwner;
    public ReentrantLock lengthlock;

    public GlobalLength(long length) {
        this.length = length;
    }
    

    public long getLength() {
        return length;
    }


    public void setLength(long offsetnumber) {
        this.length = offsetnumber;
    }

    public Vector getLengthReaders() {
        return lengthReaders;
    }

    public void setLengthReaders(Vector offsetReaders) {
        this.lengthReaders = offsetReaders;
    }
    
}
