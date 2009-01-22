/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package TransactionalIO.core;


import TransactionalIO.interfaces.OffsetDependency;
import java.io.RandomAccessFile;

/**
 *
 * @author navid
 */
public class TransactionLocalFileAttributes {


    private INode inode;
    public boolean lenght_read = false;
    public boolean to_be_created = false;
    RandomAccessFile f;
    OffsetDependency offsetdependency;
    private long copylocaloffset;
    private long initiallocallength;
    private boolean unknown_inital_offset_for_write = true;
    private long localoffset;
    private long localsize;
    
    public TransactionLocalFileAttributes(long initialoffset, long initialsize){
        localoffset = initialoffset;
        copylocaloffset = initialoffset;
        localsize = initialsize;
        initiallocallength = initialsize;
        //copylocaloffset = 0;
        
        unknown_inital_offset_for_write = true; 
        offsetdependency = OffsetDependency.NO_ACCESS;
        //localsize = initialsize;
    }

    public long getCopylocaloffset() {
        return copylocaloffset;
    }
    
    public long getInitiallocallength() {
        return initiallocallength;
    }
    
    public void setCopylocaloffset(long copylocaloffset) {
        this.copylocaloffset = copylocaloffset;
    }

    public long getLocalsize() {
        return localsize;
    }

    public void setLocalsize(long localsize) {
        this.localsize = localsize;
    }
    

    public OffsetDependency getOffsetdependency() {
        return offsetdependency;
    }

    public void setOffsetdependency(OffsetDependency offsetdependency) {
        this.offsetdependency = offsetdependency;
    }

    public boolean isUnknown_inital_offset_for_write() {
        return unknown_inital_offset_for_write;
    }

    public void setUnknown_inital_offset_for_write(boolean unknown_inital_offset_for_write) {
        this.unknown_inital_offset_for_write = unknown_inital_offset_for_write;
    }
    
    public long getLocaloffset() {
        return localoffset;
    }

    public void setLocaloffset(long localoffset) {
        this.localoffset = localoffset;
    }
    
}
