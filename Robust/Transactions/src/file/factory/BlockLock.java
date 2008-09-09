/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dstm2.file.factory;

import dstm2.Transaction;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 *
 * @author navid
 */
public class BlockLock {
    
    //public ReentrantReadWriteLock lock;
    public ReentrantLock lock;
    public Transaction owner;
    public INode inode;
    public int blocknumber;
    public AtomicInteger version;
    int referncount;
    public static enum MODE {READ, WRITE, READ_WRITE};
    public MODE accessmode;

    public BlockLock(INode inode, int blocknumber) {
        version = new AtomicInteger(0);
        //lock = new ReentrantReadWriteLock();
        lock = new ReentrantLock();
        this.inode = inode;
        this.blocknumber = blocknumber;
        referncount = 0;
    }
    

    public synchronized int getReferncount() {
        return referncount;
    }

    public synchronized void setReferncount(int referncount) {
        this.referncount = referncount;
    }
    
    

}
