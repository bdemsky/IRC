/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package TransactionalIO.core;


import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 *
 * @author navid
 */
public class BlockDataStructure {
    //private ReentrantReadWriteLock lock;
    private MYReadWriteLock lock;
    private ExtendedTransaction owner;
    private INode inode;
    private int blocknumber;
    private AtomicInteger version;
    private int referncount;
    //private Vector<ExtendedTransaction> readers;
    private Vector blockreaders;

    public static enum MODE {READ, WRITE, READ_WRITE};
    private MODE accessmode;
    
    protected BlockDataStructure(INode inode, int blocknumber) {
        version = new AtomicInteger(0);
        //lock = new ReentrantReadWriteLock();
        blockreaders = new Vector();
        //lock = new ReentrantReadWriteLock();
        lock = new MYReadWriteLock();
        this.inode = inode;
        this.blocknumber = blocknumber;
        referncount = 0;
        owner = null;
    }

    public Vector getReaders() {
        return blockreaders;
    }

    public void setReaders(Vector readers) {
        this.blockreaders = readers;
    }
    
    public MYReadWriteLock getLock() {
        return lock;
    }

    public void setLock(MYReadWriteLock lock) {
        this.lock = lock;
    }

    public synchronized ExtendedTransaction getOwner() {
        return owner;
    }
    
    public synchronized void setOwner(ExtendedTransaction owner) {
        this.owner = owner;
    }

    public INode getInode() {
        return inode;
    }

    public void setInode(INode inode) {
        this.inode = inode;
    }

    public int getBlocknumber() {
        return blocknumber;
    }

    public void setBlocknumber(int blocknumber) {
        this.blocknumber = blocknumber;
    }

    public AtomicInteger getVersion() {
        return version;
    }

    public void setVersion(AtomicInteger version) {
        this.version = version;
    }

    public MODE getAccessmode() {
        return accessmode;
    }

    public void setAccessmode(MODE accessmode) {
        this.accessmode = accessmode;
    }

    public synchronized int getReferncount() {
        return referncount;
    }

    public synchronized void setReferncount(int referncount) {
        this.referncount = referncount;
    }
    
    

}
