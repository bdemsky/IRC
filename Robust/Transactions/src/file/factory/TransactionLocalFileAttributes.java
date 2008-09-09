/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dstm2.file.factory;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author navid
 */
public class TransactionLocalFileAttributes {

    //public int recordedversion;
    public Adapter adapter;
    
    public boolean to_be_created = false;
    RandomAccessFile f;
    private boolean validatelocaloffset = true;
    public boolean writetoabsoluteoffset = false;
    public int writetoabsoluteoffsetfromhere = -1;
    public ReentrantLock offsetlock;
    //Vector<Integer> startoffset; 
    //Vector<Byte[]> data;
    //Vector<Integer> occupiedblocks;
    // private Vector readoccupiedblocks;
    //private Vector writeoccupiedblocks;
    // private Vector startoffset; 
    // private Vector data;
    //private TreeMap<Range, Byte[]> writtendata;
    private TreeMap writtendata;
    //private TreeMap<Integer, MODE> accesesblocks;
    private TreeMap accesedblocks;
    //private TreeMap<Integer, Integer> blockversions;
    private TreeMap blockversions;
    
    
    public Offset currentcommitedoffset;
    private final long copylocaloffset;
    private final long copycurrentfilesize;
    private long localoffset;
    private long filelength;


    public boolean isValidatelocaloffset() {
        return validatelocaloffset;
    }

    public void setValidatelocaloffset(boolean validatelocaloffset) {
        this.validatelocaloffset = validatelocaloffset;
    }

    public long getFilelength() {
        return filelength;
    }

    public void setFilelength(long filelength) {
        this.filelength = filelength;
    }

    public long getLocaloffset() {
        return localoffset;
    }

    public void setLocaloffset(long localoffset) {
        this.localoffset = localoffset;
    }
    
    //public MODE accessmode;

    public TreeMap getAccesedblocks() {
        return accesedblocks;
    }

    public TreeMap getBlockversions() {
        return blockversions;
    }

    public long getCopycurrentfilesize() {
        return copycurrentfilesize;
    }

    public long getCopylocaloffset() {
        return copylocaloffset;
    }
    

    /*    public Vector getData() {
    return data;
    }
    
    public Vector getReadoccupiedblocks() {
    return readoccupiedblocks;
    }
    
    public Vector getWriteoccupiedblocks() {
    return writeoccupiedblocks;
    }
     */

    /*    public Vector getStartoffset() {
    return startoffset;
    }
     */
    public TransactionLocalFileAttributes(Adapter adapter, boolean to_be_created, /*TransactionLocalFileAttributes.MODE mode,*/ RandomAccessFile f, ReentrantLock offsetlock, Offset currentcommitedoffset) {

            this.adapter = adapter;
            this.localoffset = currentcommitedoffset.getOffsetnumber();
            this.copylocaloffset = this.localoffset;
            this.currentcommitedoffset = currentcommitedoffset;
            this.filelength = adapter.commitedfilesize.get();
            this.copycurrentfilesize = this.filelength;
            this.to_be_created = to_be_created;
            this.f = f;
            //this.filelength = filelength;
            writtendata = new TreeMap();
            validatelocaloffset = false;
            this.offsetlock = offsetlock;
            
            //readoccupiedblocks = new Vector();
            //writeoccupiedblocks = new Vector();
            //accessmode = mode;
            //recordedversion = adapter.version.get();
            //startoffset = new Vector();
            //data = new Vector();
            //readoccupiedblocks = new Vector();
            //writeoccupiedblocks = new Vector();
    }

    public TreeMap getWrittendata() {
        return writtendata;
    }
}
