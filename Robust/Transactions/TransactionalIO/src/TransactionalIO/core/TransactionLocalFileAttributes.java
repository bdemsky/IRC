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
    public OffsetDependency offsetdependency;
    protected long copylocaloffset;
    private long initiallocallength;
    protected boolean unknown_inital_offset_for_write = true;
    protected long localoffset;
    protected long localsize;
    
    public TransactionLocalFileAttributes(long initialoffset, long initialsize){

        localoffset = initialoffset;
        copylocaloffset = initialoffset;
        localsize = initialsize;
        initiallocallength = initialsize;
        unknown_inital_offset_for_write = true; 
        offsetdependency = OffsetDependency.NO_ACCESS;
    }

   
    
    
    
    public long getInitiallocallength() {
        return initiallocallength;
    }
    
   
    public long getLocalsize() {
        return localsize;
    }

    public void setLocalsize(long localsize) {
        this.localsize = localsize;
    }
  

    public boolean isUnknown_inital_offset_for_write() {
        return unknown_inital_offset_for_write;
    }

    public void setUnknown_inital_offset_for_write(boolean unknown_inital_offset_for_write) {
        this.unknown_inital_offset_for_write = unknown_inital_offset_for_write;
    }
    

    
}
