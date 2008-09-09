/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dstm2.file.factory;

import dstm2.Transaction;
import dstm2.Thread;
import dstm2.exceptions.AbortedException;
import dstm2.file.interfaces.BlockAccessModesEnum;
import dstm2.file.interfaces.FileAccessModesEum;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author navid
 */
public class ExtendedTransaction extends Transaction {

    static ExtendedTransaction getTransaction() {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    //  Vector<ReentrantLock> heldlocks;    
    private Vector heldlocks;
    //  HashMap<INode, TransactionLocalFileAttributes> FilesAccesses;  
    private HashMap FilesAccesses;
    //HashMap<INode, TransactionLocalFileAttributes.MODE> FilesAccessMode;  
    private HashMap FilesAccessModes;

    public HashMap getFilesAccessModes() {
        return FilesAccessModes;
    }

    public HashMap getFilesAccesses() {
        return FilesAccesses;
    }

    public void addtoFileAccessModeMap(INode inode, FileAccessModesEum mode) {
        /*  if (FilesAccessModes.containsKey(inode)) {
        if (((FileAccessModesEum) (FilesAccessModes.get(inode))) == FileAccessModesEum.APPEND) {
        FilesAccessModes.put(inode, mode);
        } else if (((FileAccessModesEum) (FilesAccessModes.get(inode))) == FileAccessModesEum.READ) {
        if (mode == FileAccessModesEum.READ_WRITE) {
        FilesAccessModes.put(inode, mode);
        }
        }
        } else {*/
        FilesAccessModes.put(inode, mode);
    //}
    }

    public ExtendedTransaction() {
        super();
    }

    public Vector getHeldlocks() {
        return heldlocks;
    }

    public Map getSortedFileAccessMap(HashMap hmap) {
        Map sortedMap = new TreeMap(hmap);
        return sortedMap;
    }

    public void addFile(TransactionalFile tf/*, TransactionLocalFileAttributes.MODE mode*/) {


        if (tf.appendmode) {
            this.addtoFileAccessModeMap(tf.getInode(), FileAccessModesEum.APPEND);
        } else if (tf.writemode) {
            this.addtoFileAccessModeMap(tf.getInode(), FileAccessModesEum.READ_WRITE);
        } else {
            this.addtoFileAccessModeMap(tf.getInode(), FileAccessModesEum.READ);
        }
        boolean flag = tf.to_be_created;
        RandomAccessFile fd = tf.file;
        ReentrantLock lock = tf.offsetlock;
        Offset commitedoffset = tf.commitedoffset;

        synchronized (tf.adapter) {
            //Adapter ad = new Adapter(tf.adapter);
            FilesAccesses.put(tf.getInode(), new TransactionLocalFileAttributes(/*ad*/tf.adapter, flag/*, mode*/, fd, lock, commitedoffset));
        }

    }

    @Override
    public boolean commit() {   /// Locking offsets for File Descriptors

        // Vector offsetlocks = new Vector();
        Map hm = getSortedFileAccessMap(FilesAccesses);
        //lock phase
        Iterator iter = hm.keySet().iterator();
        TransactionLocalFileAttributes value;
        while (iter.hasNext() && (this.getStatus() == Status.ACTIVE)) {
            INode key = (INode) iter.next();
            value = (TransactionLocalFileAttributes) hm.get(key);

            if (value.getAccesedblocks().isEmpty()) {
                value.setValidatelocaloffset(false);
            } else if ((value.getAccesedblocks().values().contains(BlockAccessModesEnum.READ)) || (value.getAccesedblocks().values().contains(BlockAccessModesEnum.READ_WRITE))) {
                value.setValidatelocaloffset(true);
            } else {
                value.setValidatelocaloffset(false);
            }
            if (value.isValidatelocaloffset()) {
                if (value.getCopylocaloffset() == value.currentcommitedoffset.getOffsetnumber()) {
                    value.offsetlock.lock();
                    //offsetlocks.add(value.offsetlock);
                    heldlocks.add(value.offsetlock);
                    if (!(value.getCopylocaloffset() == value.currentcommitedoffset.getOffsetnumber())) {
                        /*  for (int i = 0; i < offsetlocks.size(); i++) {
                        ((ReentrantLock) offsetlocks.get(i)).unlock();
                        }*/
                        unlockAllLocks();
                        //throw new AbortedException();
                        return false;
                    }
                } else {
                    /*for (int i = 0; i < offsetlocks.size(); i++) {
                    ((ReentrantLock) offsetlocks.get(i)).unlock();
                    }*/
                    unlockAllLocks();
                    return false;
                // throw new AbortedException();
                }
            } else {
                value.offsetlock.lock();
                heldlocks.add(value.offsetlock);
            }
        }
        return true;


    // return ok;
        /*
    if (ok) {
    if (!(super.commit())) {
    unlockAllLocks();
    return false;
    } else {
    return true;
    }
    } else {
    return false;
    }*/
    }

    public boolean lock(BlockLock block, Adapter adapter, BlockAccessModesEnum mode, int expvalue/*INode inode, TransactionLocalFileAttributes tf*/) {

        final ReentrantLock lock = block.lock;
        while (this.getStatus() == Status.ACTIVE) {
            if (lock.tryLock()) {
                Thread.onAbortOnce(new Runnable() {

                    public void run() {
                        lock.unlock();
                    }
                });

                heldlocks.add(lock);
                if (mode != BlockAccessModesEnum.WRITE) {
                    if (block.version.get() != expvalue) {
                        unlockAllLocks();
                        return false;
                    }
                }
                return true;
            } else {
                getContentionManager().resolveConflict(this, block.owner);
            }
        }
        return false;
    }

    public void endTransaction() {

        if (commit()) {
            ///////////////////////////
            Map hm = getSortedFileAccessMap(FilesAccesses);
            Iterator iter = hm.keySet().iterator();
            TransactionLocalFileAttributes value;
            boolean ok = true;
            while (iter.hasNext() && (this.getStatus() == Status.ACTIVE) && ok) {
                int expvalue;
                INode key = (INode) iter.next();

                value = (TransactionLocalFileAttributes) hm.get(key);
                if (((FileAccessModesEum) (this.FilesAccessModes.get(key))) == FileAccessModesEum.APPEND) {
                    Range tmp = (Range) (value.getWrittendata().firstKey());
                    int startblock = FileBlockManager.getCurrentFragmentIndexofTheFile(value.currentcommitedoffset.getOffsetnumber());
                    int targetblock = FileBlockManager.getTargetFragmentIndexofTheFile(value.currentcommitedoffset.getOffsetnumber(), (int) (tmp.getEnd() - tmp.getStart()));
                    for (int i = startblock; i <= targetblock; i++) {
                        value.getAccesedblocks().put(Integer.valueOf(i), BlockAccessModesEnum.WRITE);
                    }
                }





                Iterator it = value.getAccesedblocks().keySet().iterator();
                while (it.hasNext() && (this.getStatus() == Status.ACTIVE)) {
                    Integer blockno = (Integer) it.next();
                    BlockLock blockobj = (BlockLock) value.adapter.lockmap.get(blockno);
                    expvalue = ((Integer) value.getBlockversions().get(it)).intValue();
                    if (((BlockAccessModesEnum) (value.getAccesedblocks().get(blockno))) != BlockAccessModesEnum.WRITE) {

                        if (blockobj.version.get() == expvalue) {
                            // ok = this.lock(key, value/*value.adapter*/);
                            ok = this.lock(blockobj, value.adapter, (BlockAccessModesEnum) (value.getAccesedblocks().get(blockno)), expvalue);
                            if (ok == false) {
                                //        unlockAllLocks();
                                break;
                            }
                        } else {
                            ok = false;
                            //    unlockAllLocks();
                            break;
                        //    return false;
                        }
                    } else {

                        ok = this.lock(blockobj, value.adapter, (BlockAccessModesEnum) (value.getAccesedblocks().get(blockno)), expvalue);
                        if (ok == false) {
                            break;
                        }
                    }
                }
            }

            if (!(ok)) {
                unlockAllLocks();
                throw new AbortedException();


            }

            if (!(super.commit())) {
                unlockAllLocks();
                throw new AbortedException();

            }

            iter = hm.keySet().iterator();
            while (iter.hasNext() && (this.getStatus() == Status.ACTIVE)) {
                INode key = (INode) iter.next();
                value = (TransactionLocalFileAttributes) hm.get(key);
                if (((FileAccessModesEum) (this.FilesAccessModes.get(key))) == FileAccessModesEum.APPEND) {
                    try {
                        Range range = (Range) value.getWrittendata().firstKey();


                        //synchronized(value.adapter){
                        //value.f.seek(value.adapter.commitedfilesize.get());
                        value.f.seek(value.currentcommitedoffset.getOffsetnumber());
                        //}

                        Byte[] data = new Byte[(int) (range.getEnd() - range.getStart())];
                        byte[] bytedata = new byte[(int) (range.getEnd() - range.getStart())];
                        data = (Byte[]) value.getWrittendata().get(range);

                        for (int i = 0; i < data.length; i++) {
                            bytedata[i] = data[i];
                        }
                        value.f.write(bytedata);

                    } catch (IOException ex) {
                        Logger.getLogger(ExtendedTransaction.class.getName()).log(Level.SEVERE, null, ex);
                    }

                } else if (((FileAccessModesEum) (this.FilesAccessModes.get(key))) == FileAccessModesEum.READ) {
                    continue;
                } else {
                    int tobeaddedoffset = 0;
                    
                    if (value.isValidatelocaloffset())
                        tobeaddedoffset = 0;
                    else
                        tobeaddedoffset = (int) (value.currentcommitedoffset.getOffsetnumber() - value.getCopylocaloffset());
                    
                    Iterator it = value.getWrittendata().keySet().iterator();
                    while (it.hasNext() && (this.getStatus() == Status.ACTIVE)) {
                        try {
                            Range range = (Range) it.next();
                            value.f.seek(range.getStart() + tobeaddedoffset);
                            Byte[] data = new Byte[(int) (range.getEnd() - range.getStart())];
                            byte[] bytedata = new byte[(int) (range.getEnd() - range.getStart())];
                            data = (Byte[]) value.getWrittendata().get(range);

                            for (int i = 0; i < data.length; i++) {
                                bytedata[i] = data[i];
                            }
                            value.f.write(bytedata);

                        } catch (IOException ex) {
                            Logger.getLogger(ExtendedTransaction.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                }
            }


            iter = hm.keySet().iterator();
            while (iter.hasNext() && (this.getStatus() == Status.ACTIVE)) {
                INode key = (INode) iter.next();
                value = (TransactionLocalFileAttributes) hm.get(key);
                Iterator it = value.getAccesedblocks().keySet().iterator();

                while (it.hasNext() && (this.getStatus() == Status.ACTIVE)) {
                    Integer blockno = (Integer) it.next();
                    BlockLock blockobj = (BlockLock) value.adapter.lockmap.get(blockno);
                    blockobj.version.getAndIncrement();
                    value.currentcommitedoffset.setOffsetnumber(value.getLocaloffset());
                    synchronized (value.adapter) {
                        value.adapter.commitedfilesize.getAndSet(value.getFilelength());
                    }
                }
            }


            // unlock phase
            /*iter = hm.keySet().iterator();
            while (iter.hasNext() && (this.getStatus() == Status.ACTIVE)) {
                INode key = (INode) iter.next();
                value = (TransactionLocalFileAttributes) hm.get(key);
                value.offsetlock.unlock();
            }*/
            unlockAllLocks();
        } else {
            throw new AbortedException();
        }
    }

    private void unlockAllLocks() {
        Iterator it = heldlocks.iterator();
        while (it.hasNext()) {
            ReentrantLock lock = (ReentrantLock) it.next();
            lock.unlock();
        }
    }
}



