/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package TransactionalIO.core;


import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 *
 * @author navid
 */
public class BlockDataStructure {
    //private ReentrantReadWriteLock lock;
    private ReentrantReadWriteLock lock;
    private ExtendedTransaction owner;
    private INode inode;
    private int blocknumber;
    private AtomicInteger version;
    private int referncount;
    private ArrayList blockreaders;

    public static enum MODE {READ, WRITE, READ_WRITE};
    private MODE accessmode;
    
    protected BlockDataStructure(INode inode, int blocknumber) {
        version = new AtomicInteger(0);
        blockreaders = new ArrayList();
        lock = new ReentrantReadWriteLock();
        this.inode = inode;
        this.blocknumber = blocknumber;
        referncount = 0;
        owner = null;
    }

    public ArrayList getReaders() {
        return blockreaders;
    }

    public void setReaders(ArrayList readers) {
        this.blockreaders = readers;
    }
    
    public ReentrantReadWriteLock getLock() {
        return lock;
    }

    public void setLock(ReentrantReadWriteLock lock) {
        this.lock = lock;
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

}
