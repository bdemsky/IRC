/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package TransactionalIO.core;

import TransactionalIO.exceptions.AbortedException;


import TransactionalIO.interfaces.BlockAccessModesEnum;
import TransactionalIO.interfaces.ContentionManager;
import TransactionalIO.interfaces.OffsetDependency;
import TransactionalIO.interfaces.TransactionStatu;


import dstm2.Thread;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;
import java.util.ArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author navid
 */
public class ExtendedTransaction implements TransactionStatu {

    private static native int nativepwrite(byte buff[], long offset, int size, FileDescriptor fd);
    public TransactionStatu memorystate;
    public int starttime;
    public int endtime;
    public int numberofwrites;
    public int numberofreads;
    private TreeMap<INode, ArrayList<TransactionalFile>> sortedAccesedFiles;

    public enum Status {

        ABORTED, ACTIVE, COMMITTED
    };
    private boolean writesmerged = true;
    public Vector heldlengthlocks;
    //private HashMap<INode, Vector<TransactionalFile>> AccessedFiles;
    private HashMap AccessedFiles;
    //private HashMap<INode, HashMap<Integer, BlockAccessModesEnum> > accessedBlocks;
    public HashMap accessedBlocks;
    //private HashMap<TransactionalFile, TransactionLocalFileAttributes> LocaltoGlobalMappings;
    public HashMap GlobaltoLocalMappings;
    public HashMap merge_for_writes_done;
    public HashMap writeBuffer;
    private volatile Status status;
    public MYLock[] toholoffsetlocks;
    public int offsetcount = 0;
    public Lock[] toholdblocklocks;
    public int blockcount = 0;

    public ExtendedTransaction() {
        toholoffsetlocks = new MYLock[30];
        toholdblocklocks = new Lock[100];
        heldlengthlocks= new Vector();
        AccessedFiles = new HashMap();
        GlobaltoLocalMappings = new HashMap();
        writeBuffer = new HashMap();
        status = Status.ACTIVE;
        accessedBlocks = new HashMap();
        merge_for_writes_done = new HashMap();
        writesmerged = true;
    }

    public ExtendedTransaction(TransactionStatu memorystate) {
        this();

        this.memorystate = memorystate;
    }

    public static int invokeNativepwrite(byte buff[], long offset, int size, RandomAccessFile file) {
        try {
            return nativepwrite(buff, offset, buff.length, file.getFD());
        } catch (IOException ex) {

            Logger.getLogger(TransactionalFile.class.getName()).log(Level.SEVERE, null, ex);
            return -1;
        }

    }

    public void abort() {
        synchronized (this) {
            this.status = Status.ABORTED;
            if (this.memorystate != null && !(this.memorystate).isAborted()) {
                this.memorystate.abortThisSystem();
            }
        }
    }

    public Status getStatus() {
        return status;
    }

    public boolean isActive() {
        return this.getStatus() == Status.ACTIVE;
    }

    public boolean isAborted() {
        return this.getStatus() == Status.ABORTED;
    }

   


    public boolean isWritesmerged() {
        return writesmerged;
    }

    public void setWritesmerged(boolean writesmerged) {
        this.writesmerged = writesmerged;
    }


    public TreeMap getSortedFileAccessMap(HashMap hmap) {
        sortedAccesedFiles = new TreeMap(hmap);
        return sortedAccesedFiles;
    }

    public void addFile(TransactionalFile tf, long offsetnumber) {

        tf.inodestate.commitedfilesize.lengthlock.lock();
       TransactionLocalFileAttributes tmp = new TransactionLocalFileAttributes(offsetnumber, tf.inodestate.commitedfilesize.getLength());
        tf.inodestate.commitedfilesize.lengthlock.unlock();
        ArrayList dummy;

        if (AccessedFiles.containsKey(tf.getInode())) {
            
            dummy = (ArrayList) AccessedFiles.get(tf.getInode());
        } else {
            dummy = new ArrayList();
            AccessedFiles.put(tf.getInode(), dummy);
        }
        dummy.add(tf);
        GlobaltoLocalMappings.put(tf, tmp);
        merge_for_writes_done.put(tf.getInode(), Boolean.TRUE);
    }

    public boolean lockOffsets() {   /// Locking offsets for File Descriptors


        TreeMap<INode, ArrayList<TransactionalFile>> hm = getSortedFileAccessMap(AccessedFiles);

        offsetcount = 0;
        for (Map.Entry<INode, ArrayList<TransactionalFile>> entry : hm.entrySet()) {
            ArrayList vec = entry.getValue();
            if (vec.size() > 1) {
                Collections.sort(vec);
            }
            for (int i = 0; i < vec.size(); i++) {
                TransactionalFile value = (TransactionalFile) vec.get(i);
                value.myoffsetlock.acquire(this);
                toholoffsetlocks[offsetcount] = value.myoffsetlock;
                offsetcount++;

                if (((TransactionLocalFileAttributes) GlobaltoLocalMappings.get(value)).lenght_read) {
                    if (!(value.inodestate.commitedfilesize.lengthlock.isHeldByCurrentThread())) {
                        value.inodestate.commitedfilesize.lengthlock.lock();
                        heldlengthlocks.add(value.inodestate.commitedfilesize.lengthlock);
                    }
                }
            }
        }

        if (this.getStatus() != Status.ACTIVE) {
            for (int i = 0; i < offsetcount; i++) {
                toholoffsetlocks[i].release(this);
            }
            offsetcount = 0;
            return false;
        }
        return true;
    }

    public boolean lockBlock(BlockDataStructure block, BlockAccessModesEnum mode) {

        Lock lock;
        if (mode == BlockAccessModesEnum.READ) {

            lock =block.getLock().readLock();
            

        } else {

            lock = block.getLock().writeLock();
        }

        lock.lock();

        if (toholdblocklocks[blockcount] == null) {
            toholdblocklocks[blockcount] = new ReentrantReadWriteLock().writeLock();
            
        }
        toholdblocklocks[blockcount] = lock;
        
        blockcount++;
        return true;

    }

    public void prepareCommit() {
        if (this.status != Status.ACTIVE) {
            throw new AbortedException();
        }
        if (!lockOffsets()) {
            throw new AbortedException();
        }


        ///////////////////////////
        Map<INode, ArrayList<WriteOperations>> hm = writeBuffer;

        ArrayList vec;
        for (Map.Entry<INode, ArrayList<WriteOperations>> entry : hm.entrySet()) {

            WriteOperations value;

            INode key = entry.getKey();
            vec = entry.getValue();
            Collections.sort(vec);
            for (int j = 0; j < vec.size(); j++) {
                value = (WriteOperations) vec.get(j);
                if (value.isUnknownoffset()) {
                    long start;
                    long end;
                    start = value.range.start - value.belongingto.copylocaloffset + value.ownertransactionalfile.committedoffset.getOffsetnumber();
                    end = value.range.end - value.belongingto.copylocaloffset + value.ownertransactionalfile.committedoffset.getOffsetnumber();
                    if (value.belongingto.isUnknown_inital_offset_for_write()) {
                        value.belongingto.localoffset = value.belongingto.localoffset - value.belongingto.copylocaloffset + value.ownertransactionalfile.committedoffset.getOffsetnumber();
                        value.belongingto.setUnknown_inital_offset_for_write(false);
                    }

                    int startblock = FileBlockManager.getCurrentFragmentIndexofTheFile(start);
                    int targetblock = FileBlockManager.getTargetFragmentIndexofTheFile(start, value.range.end - value.range.start);

                    TreeMap sset;
                    if (accessedBlocks.get(key) != null) {
                        sset = (TreeMap) accessedBlocks.get(key);
                    } else {
                        sset = new TreeMap();
                        accessedBlocks.put(key, sset);
                    }


                    for (int i = startblock; i <= targetblock; i++) {
                        if (sset.containsKey(Integer.valueOf(i))) {
                            if (sset.get(Integer.valueOf(i)) != BlockAccessModesEnum.WRITE) {
                                sset.put(Integer.valueOf(i), BlockAccessModesEnum.READ_WRITE);
                            }
                        } else {
                            sset.put(Integer.valueOf(i), BlockAccessModesEnum.WRITE);
                        }
                    }

                    value.range.start = start;
                    value.range.end = end;
                }
            }

        }


        TreeMap<INode, ArrayList<TransactionalFile>> accessmap = getSortedFileAccessMap(AccessedFiles);
        for (Map.Entry<INode, ArrayList<TransactionalFile>> entry : accessmap.entrySet()) {
	    INode inode = entry.getKey();	
            GlobalINodeState inodestate = TransactionalFileWrapperFactory.getTateransactionalFileINodeState(inode);
            TreeMap vec2 = (TreeMap) accessedBlocks.get(inode);
	    Iterator iter2 = vec2.keySet().iterator();
       	    while (iter2.hasNext()) {
            	Integer num = (Integer) iter2.next();
	        BlockDataStructure blockobj = inodestate.getBlockDataStructure(num);
        	this.lockBlock(blockobj, (BlockAccessModesEnum) vec2.get(num));
            }
        }	


        if (this.getStatus() != Status.ACTIVE) {
            throw new AbortedException();
        }
        abortAllReaders();

    }


    public void commitChanges() {

        Map<INode, ArrayList<WriteOperations>> hm = writeBuffer;
        WriteOperations writeop;
        ArrayList vec;

        for (Map.Entry<INode, ArrayList<WriteOperations>> entry : hm.entrySet()) {


            vec = entry.getValue();
            Collections.sort(vec);
            for (int j = 0; j < vec.size(); j++) {
                writeop = (WriteOperations) vec.get(j);
                invokeNativepwrite(writeop.data, writeop.range.start, writeop.data.length, writeop.ownertransactionalfile.file);
            }
        }

        for (Map.Entry<TransactionalFile, TransactionLocalFileAttributes> entry : ((HashMap<TransactionalFile, TransactionLocalFileAttributes>) GlobaltoLocalMappings).entrySet()) {
            entry.getKey().committedoffset.setOffsetnumber((entry.getValue().localoffset));


            if (((TransactionLocalFileAttributes) GlobaltoLocalMappings.get(entry.getKey())).getInitiallocallength() != ((TransactionLocalFileAttributes) GlobaltoLocalMappings.get(entry.getKey())).getLocalsize()) {
                try {
                    if (!(entry.getKey().inodestate.commitedfilesize.lengthlock.isHeldByCurrentThread())) {
                        entry.getKey().inodestate.commitedfilesize.lengthlock.lock();
                    }


                    for (int adad = 0; adad < entry.getKey().inodestate.commitedfilesize.getLengthReaders().size(); adad++) {
                        ExtendedTransaction tr = (ExtendedTransaction) entry.getKey().inodestate.commitedfilesize.getLengthReaders().get(adad);
                    	if (tr!=null && this != tr && tr.isActive()) {
                            tr.abort();
                        }
                    }
                    entry.getKey().inodestate.commitedfilesize.getLengthReaders().clear();
                    entry.getKey().inodestate.commitedfilesize.setLength(entry.getKey().file.length());

                    if (entry.getKey().inodestate.commitedfilesize.lengthlock.isHeldByCurrentThread()) {
                        heldlengthlocks.remove(entry.getKey().inodestate.commitedfilesize.lengthlock);
                        entry.getKey().inodestate.commitedfilesize.lengthlock.unlock();
                    }
                    if (((TransactionLocalFileAttributes) GlobaltoLocalMappings.get(entry.getKey())).lenght_read) {
                        entry.getKey().inodestate.commitedfilesize.getLengthReaders().remove(this);
                    }

                } catch (IOException ex) {
                    Logger.getLogger(ExtendedTransaction.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }


    }

    public void unlockAllLocks() {


        for (int i = 0; i < offsetcount; i++) {
            toholoffsetlocks[i].release(this);
        }
        offsetcount = 0;

        for (int i = 0; i < blockcount; i++) {
            toholdblocklocks[i].unlock();
        }
        blockcount = 0;
        for (int i = 0; i <heldlengthlocks.size(); i++) {
            ((ReentrantLock)heldlengthlocks.get(i)).unlock();
        }

    }

    public void abortAllReaders() {

        //lock phase

        TransactionalFile value;
        for (Map.Entry<INode, ArrayList<TransactionalFile>> entry : sortedAccesedFiles.entrySet()) {
            ArrayList vec = entry.getValue();
            INode key = entry.getKey();
            for (int i = 0; i < vec.size(); i++) {
                value = (TransactionalFile) vec.get(i);

                for (int j = 0; j < value.committedoffset.getOffsetReaders().size(); j++) {
                    ExtendedTransaction tr = (ExtendedTransaction) value.committedoffset.getOffsetReaders().get(j);
                    if (tr!=null && this != tr && tr.isActive()) {
                        tr.abort();
                    }
                }
                value.committedoffset.getOffsetReaders().clear();



            }


            TreeMap<Integer, BlockAccessModesEnum> vec2;
            if (accessedBlocks.get(key) != null) {
                vec2 = (TreeMap) accessedBlocks.get(key);
            } else {
                vec2 = new TreeMap();

            }
            GlobalINodeState inodestate = TransactionalFileWrapperFactory.getTateransactionalFileINodeState(key);

            for (Map.Entry<Integer, BlockAccessModesEnum> entry2 : vec2.entrySet()) {

                if (entry2.getValue() != BlockAccessModesEnum.READ) {
                    BlockDataStructure blockobj = (BlockDataStructure) inodestate.getBlockDataStructure(entry2.getKey());
                    for (int i = 0; i < blockobj.getReaders().size(); i++) {
                        ExtendedTransaction tr = (ExtendedTransaction) blockobj.getReaders().get(i);
                        if (tr!=null && this != tr && tr.isActive()) {
                            tr.abort();
                        }
                    }
                    blockobj.getReaders().clear();
                }
            }
        }
    }

    public TransactionStatu getOtherSystem() {
        return memorystate;
    }

    public void setOtherSystem(TransactionStatu othersystem) {
        memorystate = othersystem;
    }


    public void abortThisSystem() {
        abort();
    }

    public boolean isCommitted() {
        if (this.status == Status.COMMITTED) {
            return true;
        }
        return false;

    }
}


