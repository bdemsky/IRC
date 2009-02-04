/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package TransactionalIO.core;

import TransactionalIO.exceptions.AbortedException;
import TransactionalIO.benchmarks.benchmark;
import TransactionalIO.benchmarks.customhandler;
import TransactionalIO.benchmarks.customhandler;
import TransactionalIO.interfaces.BlockAccessModesEnum;
import TransactionalIO.interfaces.ContentionManager;
import TransactionalIO.interfaces.TransactionStatu;
//import dstm2.file.managers.BaseManager;
import java.awt.event.ActionListener;
import java.beans.EventHandler;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;
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
    

    {
        System.load("/home/navid/libkooni.so");
    }
    
    private boolean flag = true;
    public TransactionStatu memorystate;
    private PropertyChangeSupport changes = new PropertyChangeSupport(this);
    public int starttime;
    public int endtime;
    public TreeMap msg = new TreeMap();
    public int numberofwrites;
    public int numberofreads;

    public enum Status {

        ABORTED, ACTIVE, COMMITTED
    };
    private boolean writesmerged = true;
    private Vector heldlengthlocks;
    //private Vector<ReentrantLock> heldoffsetlocks;    
    private Vector heldoffsetlocks;
    //private Vector<ReentrantLock> heldblocklocks;    
    private Vector heldblocklocks;
    //private HashMap<INode, Vector<TransactionalFile>> AccessedFiles;
    private HashMap AccessedFiles;
    //private HashMap<INode, HashMap<Integer, BlockAccessModesEnum> > accessedBlocks;
    private HashMap accessedBlocks;
    //private HashMap<TransactionalFile, TransactionLocalFileAttributes> LocaltoGlobalMappings;
    private HashMap GlobaltoLocalMappings;
    public HashMap merge_for_writes_done;
    private HashMap writeBuffer;
    private ContentionManager contentionmanager;
    private volatile Status status;
    private int id;
    
    
    public ReentrantLock[] toholoffsetlocks;
    public int offsetcount = 0;
    
    public Lock[] toholdblocklocks;
    public int blockcount = 0;

    public ExtendedTransaction() {
        //  super();
        // id = Integer.valueOf(Thread.currentThread().getName().substring(7));
        heldlengthlocks = new Vector();
        heldblocklocks = new Vector();
        heldoffsetlocks = new Vector();
        AccessedFiles = new HashMap();
        GlobaltoLocalMappings = new HashMap/*<TransactionalFile, TransactionLocalFileAttributes >*/();
        writeBuffer = new HashMap();
        status = Status.ACTIVE;
        accessedBlocks = new HashMap();
        merge_for_writes_done = new HashMap();
        writesmerged = true;
    //   setContentionmanager(new BaseManager());
    //    beginTransaction();

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

    public void beginTransaction() {
        this.addPropertyChangeListener(new customhandler(Status.ABORTED));
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

    public ContentionManager getContentionmanager() {
        return contentionmanager;
    }

    public void setContentionmanager(ContentionManager contentionmanager) {
        this.contentionmanager = contentionmanager;
    }

    public HashMap getWriteBuffer() {
        return writeBuffer;
    }

    public HashMap getAccessedFiles() {
        return AccessedFiles;
    }

    public boolean isWritesmerged() {
        return writesmerged;
    }

    public void setWritesmerged(boolean writesmerged) {
        this.writesmerged = writesmerged;
    }

    public HashMap getGlobaltoLocalMappings() {
        return GlobaltoLocalMappings;
    }

    public HashMap getAccessedBlocks() {
        return accessedBlocks;
    }

    public ContentionManager getBlockContentionManager() {
        return ManagerRepository.getBlockcm();
    }

    public ContentionManager getOffsetContentionManager() {
        return ManagerRepository.getOffsetcm();
    }

    public TreeMap getSortedFileAccessMap(HashMap hmap) {
        /*TreeMap sortedMap = new TreeMap(hmap);
        return sortedMap;*/
        return new TreeMap(hmap);
    }

    public void setStatus(Status st) {
        Status oldst = getStatus();
        this.status = st;
        this.changes.firePropertyChange("status", oldst, st);
    }

    public void addFile(TransactionalFile tf, long offsetnumber/*, TransactionLocalFileAttributes tmp*/) {
        tf.getInodestate().commitedfilesize.lengthlock.lock();
        TransactionLocalFileAttributes tmp = new TransactionLocalFileAttributes(offsetnumber, tf.getInodestate().commitedfilesize.getLength());
        tf.getInodestate().commitedfilesize.lengthlock.unlock();
        Vector dummy;

        if (AccessedFiles.containsKey(tf.getInode())) {
            dummy = (Vector) AccessedFiles.get(tf.getInode());
        } else {
            dummy = new Vector();
            AccessedFiles.put(tf.getInode(), dummy);
        }
        dummy.add(tf);
        GlobaltoLocalMappings.put(tf, tmp);
        merge_for_writes_done.put(tf.getInode(), Boolean.TRUE);
    }

    public boolean lockOffsets() {   /// Locking offsets for File Descriptors

      //  toholoffsetlocks = new ReentrantLock[30];
        
        TreeMap hm = getSortedFileAccessMap(AccessedFiles);
        Iterator iter = hm.keySet().iterator();
        offsetcount = 0;
        while (iter.hasNext() && (this.getStatus() == Status.ACTIVE)) {
            INode key = (INode) iter.next();
            Vector vec = (Vector) AccessedFiles.get(key);
            
       /*     if (vec.size() == 1){
                TransactionalFile tf = ((TransactionalFile)vec.firstElement());
                tf.offsetlock.lock();
             //   toholoffsetlocks[offsetcount] = tf.offsetlock;
            //   offsetcount++;
                heldoffsetlocks.add(tf.offsetlock);
                continue;
            }*/
            
            Collections.sort(vec);
            Iterator it = vec.iterator();
            while (it.hasNext() /*&& this.getStatus() == Status.ACTIVE*/) {
               TransactionalFile value = (TransactionalFile) it.next();
               value.offsetlock.lock();
             //   toholoffsetlocks[offsetcount] = value.offsetlock;
            //    offsetcount++;
                heldoffsetlocks.add(value.offsetlock);
                
                if (((TransactionLocalFileAttributes) GlobaltoLocalMappings.get(value)).lenght_read){ 
                    if (!(value.getInodestate().commitedfilesize.lengthlock.isHeldByCurrentThread())){
                        value.getInodestate().commitedfilesize.lengthlock.lock();
                        heldlengthlocks.add(value.getInodestate().commitedfilesize.lengthlock);
                    }
                }
                break;
            }
        }

        if (this.getStatus() != Status.ACTIVE) {
         //   for (int j=0; j<offsetcount; j++){
         //       heldoffsetlocks.add(toholoffsetlocks[j]);
         //   }
            return false;
        }
        return true;
    }
    
    
    

    public boolean lockBlock(BlockDataStructure block, BlockAccessModesEnum mode/*, GlobalINodeState adapter, BlockAccessModesEnum mode, int expvalue, INode inode, TransactionLocalFileAttributes tf*/) {

        Lock lock;
        if (mode == BlockAccessModesEnum.READ) {
            lock = block.getLock().readLock();
        } else {

            lock = block.getLock().writeLock();
        }
        
        lock.lock();
      //  toholdblocklocks[blockcount] = lock;
      //  blockcount++;
        heldblocklocks.add(lock);
        return true;
       
    }

    public void prepareCommit() {
        if (this.status != Status.ACTIVE) {
            throw new AbortedException();
        }
        boolean offsetsok = true;
        if (!lockOffsets()) {
            throw new AbortedException();
        }
        
      //  boolean lengthslock = true;
     //   if (!lockOffsets()) {
     //       throw new AbortedException();
     //   }


        ///////////////////////////


        Map hm = getWriteBuffer();

        Iterator iter = hm.keySet().iterator();
        WriteOperations value;
        Vector vec = new Vector();
        while (iter.hasNext() && (this.getStatus() == Status.ACTIVE) && offsetsok) {
            INode key = (INode) iter.next();
            vec = (Vector) hm.get(key);
            Collections.sort(vec);
            Iterator it = vec.iterator();
            while (it.hasNext()) {

                value = (WriteOperations) it.next();
                if (value.isUnknownoffset()) {

                    long start;
                    long end;
                    start = value.getRange().getStart() - value.getBelongingto().getCopylocaloffset() + value.getOwnertransactionalFile().getCommitedoffset().getOffsetnumber();
                    end = value.getRange().getEnd() - value.getBelongingto().getCopylocaloffset() + value.getOwnertransactionalFile().getCommitedoffset().getOffsetnumber();
                    if (value.getBelongingto().isUnknown_inital_offset_for_write()) {
                        value.getBelongingto().setLocaloffset(value.getBelongingto().getLocaloffset() - value.getBelongingto().getCopylocaloffset() + value.getOwnertransactionalFile().getCommitedoffset().getOffsetnumber());
                        value.getBelongingto().setUnknown_inital_offset_for_write(false);
                    }

                    int startblock = FileBlockManager.getCurrentFragmentIndexofTheFile(start);
                    int targetblock = FileBlockManager.getTargetFragmentIndexofTheFile(start, value.getRange().getEnd() - value.getRange().getStart());

                    TreeMap sset;
                    if (this.getAccessedBlocks().get(key) != null) {
                        sset = (TreeMap) this.getAccessedBlocks().get(key);
                    } else {
                        sset = new TreeMap();
                        this.getAccessedBlocks().put(key, sset);
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

                    value.getRange().setStart(start);
                    value.getRange().setEnd(end);
                }
            }

        }
        
        //toholdblocklocks = new Lock[100];
        
        Iterator it = this.getAccessedBlocks().keySet().iterator();
        BlockDataStructure[] blocks = new BlockDataStructure[100];
        //if (this.getStatus() == Status.ACTIVE)
            while (it.hasNext() /*&& (this.getStatus() == Status.ACTIVE)*/) {
                INode inode = (INode) it.next();
                GlobalINodeState inodestate = TransactionalFileWrapperFactory.getTateransactionalFileINodeState(inode);
                TreeMap vec2 = (TreeMap) this.getAccessedBlocks().get(inode);
                Iterator iter2 = vec2.keySet().iterator();
            
                while (iter2.hasNext() && this.getStatus() == Status.ACTIVE) {
                    Integer num = (Integer) iter2.next();    
                    BlockDataStructure blockobj = inodestate.getBlockDataStructure(num);
                    this.lockBlock(blockobj, (BlockAccessModesEnum) vec2.get(num));
                    
                }
            }

        if (this.getStatus() != Status.ACTIVE) {
        //    for (int i=0; i<blockcount; i++)
        //        heldblocklocks.add(toholdblocklocks[i]); 
            throw new AbortedException();
        }
        abortAllReaders();

    }

    public void commitChanges() {

        Map hm = getWriteBuffer();
        Iterator iter = hm.keySet().iterator();
        Iterator it;
        WriteOperations writeop;
        Vector vec;
        while (iter.hasNext()) {
            INode key = (INode) iter.next();

            vec = (Vector) hm.get(key);
            Collections.sort(vec);
            it = vec.iterator();
            while (it.hasNext()) {

                writeop = (WriteOperations) it.next();
                Byte[] data = new Byte[(int) (writeop.getRange().getEnd() - writeop.getRange().getStart())];
                byte[] bytedata = new byte[(int) (writeop.getRange().getEnd() - writeop.getRange().getStart())];
                data = (Byte[]) writeop.getData();

                for (int i = 0; i < data.length; i++) {
                    bytedata[i] = data[i];
                }
                invokeNativepwrite(bytedata, writeop.getRange().getStart(), bytedata.length, writeop.getOwnertransactionalFile().file);
            }
        }

        Iterator k = GlobaltoLocalMappings.keySet().iterator();
        while (k.hasNext()) {
            TransactionalFile trf = (TransactionalFile) (k.next());
            trf.getCommitedoffset().setOffsetnumber(((TransactionLocalFileAttributes) GlobaltoLocalMappings.get(trf)).getLocaloffset());
            if (((TransactionLocalFileAttributes) GlobaltoLocalMappings.get(trf)).getInitiallocallength() != ((TransactionLocalFileAttributes) GlobaltoLocalMappings.get(trf)).getLocalsize()){
                try {
                    if (!(trf.getInodestate().commitedfilesize.lengthlock.isHeldByCurrentThread()))
                        trf.getInodestate().commitedfilesize.lengthlock.lock();
                    
                    Iterator it2 = trf.getInodestate().commitedfilesize.getLengthReaders().iterator();
                    if (((TransactionLocalFileAttributes)getGlobaltoLocalMappings().get(trf)).getInitiallocallength() != ((TransactionLocalFileAttributes)getGlobaltoLocalMappings().get(trf)).getLocalsize())
                    {
                        while (it2.hasNext()) {
                            ExtendedTransaction tr = (ExtendedTransaction) it2.next();
                            if (tr != this) {
                                tr.abort();
                            }
                        }
                        trf.getInodestate().commitedfilesize.getLengthReaders().clear();
                    }
                    trf.getInodestate().commitedfilesize.setLength(trf.file.length());
                    
                    if (trf.getInodestate().commitedfilesize.lengthlock.isHeldByCurrentThread()){
                        heldlengthlocks.remove(trf.getInodestate().commitedfilesize.lengthlock);
                        trf.getInodestate().commitedfilesize.lengthlock.unlock();
                    }
                    if (((TransactionLocalFileAttributes) GlobaltoLocalMappings.get(trf)).lenght_read){
                        trf.getInodestate().commitedfilesize.getLengthReaders().remove(this);
                        //heldlengthlocks.remove(trf.getInodestate().commitedfilesize.lengthlock);
                        //trf.getInodestate().commitedfilesize.lengthlock.unlock();
                    }
                    
                } catch (IOException ex) {
                    Logger.getLogger(ExtendedTransaction.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            
          
        }
        
        
      /*  for (int i =0; i<blockcount; i++){
            toholdblocklocks[i].unlock();
        }
        for (int i =0; i<offsetcount; i++){
            toholoffsetlocks[i].unlock();
        }*/
    }

    public void unlockAllLocks() {
        Iterator it = heldblocklocks.iterator();

        while (it.hasNext()) {

            Lock lock = (Lock) it.next();
            lock.unlock();
        }
        heldblocklocks.clear();

        it = heldoffsetlocks.iterator();
        while (it.hasNext()) {
            ReentrantLock lock = (ReentrantLock) it.next();
            lock.unlock();
        }
        heldoffsetlocks.clear();
        
       it = heldlengthlocks.iterator(); 
       while (it.hasNext()) {
            ReentrantLock lock = (ReentrantLock) it.next();
            lock.unlock();
        }
        heldlengthlocks.clear();
    }

    public void abortAllReaders() {
        TreeMap hm = getSortedFileAccessMap(AccessedFiles);
        //lock phase
        Iterator iter = hm.keySet().iterator();
        TransactionalFile value;
        while (iter.hasNext()) {
            INode key = (INode) iter.next();
            Vector vec = (Vector) AccessedFiles.get(key);
            Iterator it = vec.iterator();
            while (it.hasNext()) {

                value = (TransactionalFile) it.next();
                Iterator it2 = value.getCommitedoffset().getOffsetReaders().iterator(); // for visible readers strategy

                while (it2.hasNext()) {
                    ExtendedTransaction tr = (ExtendedTransaction) it2.next();
                    if (tr != this) {
                        tr.abort();
                    }
                }
                value.getCommitedoffset().getOffsetReaders().clear();
                
            
                
            }

            TreeMap vec2;
            if (accessedBlocks.get(key) != null) {
                vec2 = (TreeMap) accessedBlocks.get(key);
            } else {
                vec2 = new TreeMap();

            }
            GlobalINodeState inodestate = TransactionalFileWrapperFactory.getTateransactionalFileINodeState(key);
            Iterator it2 = vec2.keySet().iterator();

            while (it2.hasNext()) {

                Integer num = (Integer) it2.next();
                if (vec2.get(num) != BlockAccessModesEnum.READ) {
                    BlockDataStructure blockobj = (BlockDataStructure) inodestate.getBlockDataStructure(num);
                    //lockmap.get(num);
                    Iterator it4 = blockobj.getReaders().iterator(); // from here for visible readers strategy

                    while (it4.hasNext()) {

                        ExtendedTransaction tr = (ExtendedTransaction) it4.next();
                        if (this != tr) {
                            tr.abort();
                        }
                    }
                    blockobj.getReaders().clear();
                }
            }


        }
    }
    
  

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        this.changes.addPropertyChangeListener("status", listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        this.changes.removePropertyChangeListener("status", listener);
    }

    public TransactionStatu getOtherSystem() {
        return memorystate;
    }

    public void setOtherSystem(TransactionStatu othersystem) {
        memorystate = othersystem;
    }

    public Vector getHeldblocklocks() {
        return heldblocklocks;
    }

    public void setHeldblocklocks(Vector heldblocklocks) {
        this.heldblocklocks = heldblocklocks;
    }

    public Vector getHeldoffsetlocks() {
        return heldoffsetlocks;
    }
    
    public Vector getHeldlengthlocks() {
        return heldlengthlocks;
    }

    public void setHeldoffsetlocks(Vector heldoffsetlocks) {
        this.heldoffsetlocks = heldoffsetlocks;
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
/*
public boolean lockBlock(BlockDataStructure block, Adapter adapter, BlockAccessModesEnum mode, int expvalue) { // from here for visible readers strategy
while (this.getStatus() == Status.ACTIVE) {
if (lock.tryLock()) {
Thread.onAbortOnce(new Runnable() {

public void run() {
lock.unlock();
}
});

heldblocklocks.add(lock);

synchronized (adapter) {
block.setOwner(this);
//        Iterator it =  block.getReaders().iterator(); 
//        while (it.hasNext())
//        {
//            ExtendedTransaction tr = (ExtendedTransaction) it.next();
//            tr.abort();
//       }
}

return true;
} else {
getBlockContentionManager().resolveConflict(this, block.getOwner());
}
}
return false;*/
/*
public boolean lockBlock(BlockDataStructure block, Adapter adapter, BlockAccessModesEnum mode, int expvalue) { // versioning strat
while (this.getStatus() == Status.ACTIVE) {
if (lock.tryLock()) {
Thread.onAbortOnce(new Runnable() {

public void run() {
lock.unlock();
}
});

heldblocklocks.add(lock);
if (mode != BlockAccessModesEnum.WRITE) {   egy
if (block.getVersion().get() != expvalue) {
unlockAllLocks();
return false;
}
}
synchronized (adapter) {
block.setOwner(this);
}

return true;
} else {
getContentionManager().resolveConflict(this, block.getOwner());
}
}
return false;
}*/
// }
//expvalue = ((Integer) value.getBlockversions().get(it)).intValue(); //for versioning strategy
            /*if (!(value.isValidatelocaloffset())) {
if (((BlockAccessModesEnum) (value.getAccesedblocks().get(blockno))) != BlockAccessModesEnum.WRITE) { //versioning strategy

/if (blockobj.getVersion().get() == expvalue) {

ok = this.lock(blockobj, value.adapter, (BlockAccessModesEnum) (value.getAccesedblocks().get(blockno)), expvalue);
if (ok == false) {
//        unlockAllLocks();
break;
}
} else {
ok = false;
break;
}
} else {

ok = this.lock(blockobj, value.adapter, (BlockAccessModesEnum) (value.getAccesedblocks().get(blockno)), expvalue);
if (ok == false) {
break;
}
}
}


if (!(ok)) {
unlockAllLocks();
throw new AbortedException();
}*/
   



