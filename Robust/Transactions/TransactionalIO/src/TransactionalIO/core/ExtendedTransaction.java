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
    
    
    private native int nativepwrite(byte buff[], long offset, int size, FileDescriptor fd);
    
   // {
   //     System.load("/home/navid/libkooni.so");
   // }
    
    private boolean flag = true;
    public TransactionStatu memorystate; 
    private PropertyChangeSupport changes = new PropertyChangeSupport(this);
    public int starttime;
    public int endtime;
    
    public TreeMap msg = new TreeMap();
    public int numberofwrites;
    public int numberofreads;
    
    public enum Status {ABORTED, ACTIVE, COMMITTED};
    private boolean writesmerged = true;
    
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
    private /*volatile*/ Status status;
    
    private int id;


    
    public ExtendedTransaction() {
      //  super();
       // id = Integer.valueOf(Thread.currentThread().getName().substring(7));
        heldblocklocks = new Vector() ;
        heldoffsetlocks= new Vector();
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
    
    public ExtendedTransaction(TransactionStatu memorystate){
        this();
        /*    heldblocklocks = new Vector() ;
        heldoffsetlocks= new Vector();
        AccessedFiles = new HashMap();
        GlobaltoLocalMappings = new HashMap();
        writeBuffer = new HashMap();
        status = Status.ACTIVE;
        accessedBlocks = new HashMap();
        merge_for_writes_done = new HashMap();
        writesmerged = true;*/
        this.memorystate = memorystate ;
    }
    
     private int invokeNativepwrite(byte buff[], long offset, int size, RandomAccessFile file) {
        try {
            //System.out.println(buff.length);
           // System.out.println(offset);
            return nativepwrite(buff, offset, buff.length, file.getFD());
        } catch (IOException ex) {
            
            Logger.getLogger(TransactionalFile.class.getName()).log(Level.SEVERE, null, ex);
            return -1;
        }
        
    }
    
    public void beginTransaction(){
        this.addPropertyChangeListener(new customhandler(Status.ABORTED));
    }
    
    
   
  
    public void abort() {
        synchronized(this){
          //  Status oldst = getStatus();         
    /*        synchronized(benchmark.lock){
                    System.out.println("be ga raftim 0");
                }*/
            this.status = Status.ABORTED;
            if (this.memorystate !=null && !(this.memorystate).isAborted()){
        /*        synchronized(benchmark.lock){
                    System.out.println(Thread.currentThread() +" be ga raftim 1 file");
                }*/
                this.memorystate.abortThisSystem();
               /* synchronized(benchmark.lock){
                    System.out.println(Thread.currentThread() + " be ga raftim 2 file");
                }*/
            }
           // Thread[] group = new Thread[30];
          //  Thread.currentThread().enumerate(group);
          //  group[this.id].interrupt();
            /*synchronized(benchmark.lock){
                System.out.println("/////////////");
                System.out.println(Thread.currentThread() + " " +Thread.currentThread().enumerate(group));
                System.out.println(Thread.currentThread() + " " +group[0]);
                System.out.println(Thread.currentThread() + " " +group[1]);
                System.out.println(Thread.currentThread() + " " +group[2]);
                System.out.println("/////////////");
            }*/
            
            
          //  this.changes.firePropertyChange("status", oldst, Status.ABORTED);
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

    
    public ContentionManager getBlockContentionManager(){
        return ManagerRepository.getBlockcm();
    }
    
    public ContentionManager getOffsetContentionManager(){
        return ManagerRepository.getOffsetcm();
    }
    
    public TreeMap getSortedFileAccessMap(HashMap hmap) {
        /*TreeMap sortedMap = new TreeMap(hmap);
        return sortedMap;*/
        return new TreeMap(hmap);
    }
    
    
    public void setStatus(Status st){
        Status oldst = getStatus();
        this.status = st;
        this.changes.firePropertyChange("status", oldst, st);
    }

    
    

    public void addFile(TransactionalFile tf/*, TransactionLocalFileAttributes tmp*/) {


       /* if (tf.appendmode) {
            this.addtoFileAccessModeMap(tf.getInode(), FileAccessModesEum.APPEND);
        } else if (tf.writemode) {
            this.addtoFileAccessModeMap(tf.getInode(), FileAccessModesEum.READ_WRITE);
        } else {
            this.addtoFileAccessModeMap(tf.getInode(), FileAccessModesEum.READ);
        }*/
      //  System.out.println("dsadssasadssa");
                
          tf.lockOffset(this);
          
          //tf.offsetlock.lock();
           TransactionLocalFileAttributes tmp = new TransactionLocalFileAttributes(tf.getCommitedoffset().getOffsetnumber()/*, tf.getInodestate().commitedfilesize.get()*/);
          //this.heldoffsetlocks.remove(tf.offsetlock);  
              tf.offsetlock.unlock();     
          
            Vector dummy;     
            
            if (AccessedFiles.containsKey(tf.getInode())){
                    dummy = (Vector) AccessedFiles.get(tf.getInode());
            }
            else{ 
                dummy = new Vector();
                AccessedFiles.put(tf.getInode(), dummy);
            }
            
        
           
      //      this.msg.put(System.nanoTime(), Thread.currentThread().getName() + " Unlocked the offset lock " + tf.offsetlock + " for file " + tf.getInode() + " form descriptor " + tf.getSequenceNum());
            dummy.add(tf);
            GlobaltoLocalMappings.put(tf, tmp);
            merge_for_writes_done.put(tf.getInode(), Boolean.TRUE);
            
        
 
        //}

    }


    public boolean lockOffsets() {   /// Locking offsets for File Descriptors


        TreeMap hm = getSortedFileAccessMap(AccessedFiles);
        Iterator iter = hm.keySet().iterator();     
    
        while (iter.hasNext() && (this.getStatus() == Status.ACTIVE)) {
            INode key = (INode) iter.next();
            
            Vector vec = (Vector) AccessedFiles.get(key);
            Collections.sort(vec);
            Iterator it = vec.iterator();
            while (it.hasNext()){
                TransactionalFile value = (TransactionalFile) it.next();
                while (this.getStatus() ==Status.ACTIVE){
                    //if (value.offsetlock.tryLock()) {
                    value.offsetlock.lock();
                  
                     //   synchronized(value.getCommitedoffset()){
                     //       value.getCommitedoffset().setOffsetOwner(this);
      
         //               this.msg.put(System.nanoTime(), Thread.currentThread().getName() + " Locked the offset lock in commit for file " + value.getInode() + " from descriptor "+ value.getSequenceNum() +"\n");
                        heldoffsetlocks.add(value.offsetlock);
  
                        //else 
                        //    getContentionmanager().resolveConflict(this, value.getCommitedoffset());
                        break;
                    //}
                }
                if (this.getStatus() != Status.ACTIVE){
  
                           
                    return false;
                }
            }
           // outercounter++;
        }
        if (this.getStatus() != Status.ACTIVE){
    
            
            return false;
        }
        return true;
    }         
            
    /*public boolean commit() {   /// Locking offsets for File Descriptors

        Map hm = getSortedFileAccessMap(FilesAccesses);
        //lock phase
        Iterator iter = hm.keySet().iterator();
        TransactionLocalFileAttributes value;
        while (iter.hasNext() && (this.getStatus() == Status.ACTIVE)) {
            INode key = (INode) iter.next();
            value = (TransactionLocalFileAttributes) hm.get(key);
            synchronized(value.getCurrentcommitedoffset()){
                if (value.offsetlock.tryLock()) {
                    value.getCurrentcommitedoffset().setOffsetOwner(this);
                    heldblocklocks.add(value.offsetlock);
                    Iterator it =  value.getCurrentcommitedoffset().getOffsetReaders().iterator(); // for in-place aborting visible readers strategy
                    while (it.hasNext())
                    {
                        ExtendedTransaction tr = (ExtendedTransaction) it.next();
                        tr.abort();
                    }
                }
                } 
            }
            getOffsetContentionManager().resolveConflict(this, value.getCurrentcommitedoffset().getOffsetOwner());
        }
        return true;
    } */        
    
   /*public boolean commit() {   /// Locking offsets for File Descriptors with checking strategy

        Map hm = getSortedFileAccessMap(FilesAccesses);
        //lock phase
        Iterator iter = hm.keySet().iterator();
        TransactionLocalFileAttributes value;
        while (iter.hasNext() && (this.getStatus() == Status.ACTIVE)) {
            INode key = (INode) iter.next();
            value = (TransactionLocalFileAttributes) hm.get(key);
    
             if (value.isValidatelocaloffset()) {
                if (value.getCopylocaloffset() == value.currentcommitedoffset.getOffsetnumber()) {
                    value.offsetlock.lock();
                    heldoffsetlocks.add(value.offsetlock);
                    if (!(value.getCopylocaloffset() == value.currentcommitedoffset.getOffsetnumber())) {
                        unlockAllLocks();
                        return false;
                    }
                } else {
                    unlockAllLocks();
                    return false;
                }
            } else {
                value.offsetlock.lock();
                heldoffsetlocks.add(value.offsetlock);
            }
        }
    }*/


   

    public boolean lockBlock(BlockDataStructure block, BlockAccessModesEnum mode/*, GlobalINodeState adapter, BlockAccessModesEnum mode, int expvalue, INode inode, TransactionLocalFileAttributes tf*/) {

        
        //boolean locked = false;
        Lock lock;
      
        
        
        if (mode == BlockAccessModesEnum.READ){
                lock = block.getLock().readLock();
                  
             
            }
        else {
              
            lock = block.getLock().writeLock();
            
        }
        
        while (this.getStatus() == Status.ACTIVE) {
            //synchronized(block){
                
              //  if (lock.tryLock()) {
                lock.lock();    
                   // synchronized(benchmark.lock){
                  //      System.out.println(Thread.currentThread() + " Lock the block lock for " + lock +" number " + block.getBlocknumber());
                  //  }
                    heldblocklocks.add(lock);
                //    block.setOwner(this);
                    return true;
               // }
                
                
                    //getContentionmanager().resolveConflict(this, block);
        }
        
        return false;
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

    public void prepareCommit() {
        if (this.status != Status.ACTIVE)
            throw new AbortedException();
        
        boolean ok = true;
        if (!lockOffsets())
        {
//            unlockAllLocks();
        //    this.msg.put(System.nanoTime(),Thread.currentThread().getName() + " Aborted \n");
          /*  synchronized(benchmark.lock){
                benchmark.msg += Thread.currentThread().getName() + " Aborted in prepare commit\n";
            }*/
            //Thread.currentThread().stop();
            throw new AbortedException();
        }
        

        ///////////////////////////
        
        
        Map hm = getWriteBuffer();
        
        Iterator iter = hm.keySet().iterator();
        WriteOperations value;
        Vector vec = new Vector();
        while (iter.hasNext() && (this.getStatus() == Status.ACTIVE) && ok) {
            //int expvalue = 0;
            
            INode key = (INode) iter.next();
            vec = (Vector) hm.get(key);
            Collections.sort(vec);
            Iterator it = vec.iterator();
            while (it.hasNext()){
          
                value = (WriteOperations) it.next();
                if (value.isUnknownoffset()){
                   
                    long start;
                    long end;
                    
                    //synchronized(value.getOwnertransactionalFile().getCommitedoffset()){
                        start = value.getRange().getStart() - value.getBelongingto().getCopylocaloffset() + value.getOwnertransactionalFile().getCommitedoffset().getOffsetnumber();
                        end = value.getRange().getEnd() - value.getBelongingto().getCopylocaloffset() + value.getOwnertransactionalFile().getCommitedoffset().getOffsetnumber();
                        if (value.getBelongingto().isUnknown_inital_offset_for_write()){
                            value.getBelongingto().setLocaloffset(value.getBelongingto().getLocaloffset() - value.getBelongingto().getCopylocaloffset() + value.getOwnertransactionalFile().getCommitedoffset().getOffsetnumber());
                            value.getBelongingto().setUnknown_inital_offset_for_write(false);
                        }
                    
                    //}
                 //   System.out.println("start write " + start);
                  ///  System.out.println("end write " + end);
                    int startblock = FileBlockManager.getCurrentFragmentIndexofTheFile(start);
                    int targetblock = FileBlockManager.getTargetFragmentIndexofTheFile(start, value.getRange().getEnd() - value.getRange().getStart());
                    
                    TreeMap sset;
                    if (this.getAccessedBlocks().get(key) != null){
                       sset = (TreeMap) this.getAccessedBlocks().get(key);
                    }
                    
                    else{
                       sset = new TreeMap();
                       this.getAccessedBlocks().put(key, sset);
                    } 

                    
                    for (int i = startblock; i <= targetblock; i++) {
                        if (sset.containsKey(Integer.valueOf(i))){
                            if (sset.get(Integer.valueOf(i)) != BlockAccessModesEnum.WRITE) 
                                sset.put(Integer.valueOf(i), BlockAccessModesEnum.READ_WRITE);
                        }
                        else
                            sset.put(Integer.valueOf(i), BlockAccessModesEnum.WRITE);
                        
                       // tt.add(Integer.valueOf(i));
                    }
                    
                    value.getRange().setStart(start);
                    value.getRange().setEnd(end);
                    
                 //  System.out.println(Thread.currentThread().);
                 //   System.out.println(value.getRange().getStart());
                 //   System.out.println(value.getRange().getEnd());
                 //   System.out.println("---------------");
                    //this.getAccessedBlocks().put(value.getOwnertransactionalFile().getInode(), sset);
                }
            }

        }

        Iterator it = this.getAccessedBlocks().keySet().iterator();
        while (it.hasNext() && (this.getStatus() == Status.ACTIVE)) {
          INode inode = (INode) it.next();
          GlobalINodeState inodestate = TransactionalFileWrapperFactory.getTateransactionalFileINodeState(inode);
          TreeMap vec2 = (TreeMap) this.getAccessedBlocks().get(inode);
          Iterator iter2 = vec2.keySet().iterator();
          while(iter2.hasNext()){
            Integer num = (Integer) iter2.next();         
            
            //BlockDataStructure blockobj = (BlockDataStructure) inodestate.lockmap.get(num);
            BlockDataStructure blockobj;
          //  if (((BlockAccessModesEnum)vec2.get(num)) == BlockAccessModesEnum.WRITE){
                blockobj = inodestate.getBlockDataStructure(num);
          //  }
          //  else 
          //      blockobj = (BlockDataStructure) inodestate.lockmap.get(num);
            
            ok = this.lockBlock(blockobj, (BlockAccessModesEnum)vec2.get(num));
            if (ok == false) 
                break;
          /*  synchronized(benchmark.lock){
                benchmark.msg += Thread.currentThread().getName() + " Locked the Block Number " + blockobj.getBlocknumber() +" for " + inode + "\n";
            }*/
     //       this.msg.put(System.nanoTime(), Thread.currentThread().getName() + " Locked the Block Number " + blockobj.getBlocknumber() + " for file " + inode + "\n");
          }
        }
         
       if (this.getStatus() != Status.ACTIVE){ 
          //  unlockAllLocks();
       //           this.msg.put(System.nanoTime(), Thread.currentThread().getName() + " Aborted \n");
           /* synchronized(benchmark.lock){
                    benchmark.msg += Thread.currentThread().getName() + " Aborted \n";
            }*/
           // Thread.currentThread().stop();
            throw new AbortedException(); 
       }
       abortAllReaders();  
          
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
    }
        
        public void commitChanges(){

        //   this.msg.put(System.nanoTime(), Thread.currentThread().getName() + " is committing \n");
            
          
           
          //synchronized(benchmark.lock){
            //    System.out.println(Thread.currentThread().getName() + " is commiting");
          //}
            
            
            Map hm = getWriteBuffer();
            Iterator iter = hm.keySet().iterator();
            Iterator it;
            WriteOperations writeop;
            Vector vec;
            while (iter.hasNext() && (this.getStatus() == Status.ACTIVE)) {
                INode key = (INode) iter.next();
                 
                vec = (Vector) hm.get(key);
                Collections.sort(vec);
                it = vec.iterator();
                while (it.hasNext()){
                 
          
                    //value = (WriteOperations) it.next();
                   // writeop = (WriteOperations) writeBuffer.get(key);
                    writeop = (WriteOperations) it.next();
                  //  System.out.println(writeop);
                    Byte[] data = new Byte[(int) (writeop.getRange().getEnd() - writeop.getRange().getStart())];
                    byte[] bytedata = new byte[(int) (writeop.getRange().getEnd() - writeop.getRange().getStart())];
                    data = (Byte[]) writeop.getData();

                    for (int i = 0; i < data.length; i++) {
                        bytedata[i] = data[i];
                    }
                
               //     try {
                   //     
                //        writeop.getOwnertransactionalFile().file.seek(writeop.getRange().getStart());
                   //    System.out.println(Thread.currentThread() + " range " + writeop.getRange().getStart());
                 //       writeop.getOwnertransactionalFile().file.write(bytedata);
                        invokeNativepwrite(bytedata, writeop.getRange().getStart(), bytedata.length, writeop.getOwnertransactionalFile().file);
                       // System.out.println(Thread.currentThread() + " " + bytedata);
                        
                 //  } catch (IOException ex) {
                 //       Logger.getLogger(ExtendedTransaction.class.getName()).log(Level.SEVERE, null, ex);
                  // }
                //
                
                }
            }
                
                /*if (((FileAccessModesEum) (this.FilesAccessModes.get(key))) == FileAccessModesEum.APPEND) {
                    try {
                        Range range = (Range) value.getWrittendata().firstKey();


                        //synchronized(value.adapter){
                        //value.f.seek(value.adapter.commitedfilesize.get());
                        value.f.seek(value.getFilelength());
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
                }
                else if (value.relocatablewrite && value.getContinious_written_data() != null){
                    
                    
                }
                else if (!(value.getNon_Speculative_Writtendata().isEmpty())) {
                    int tobeaddedoffset = 0;

                    if (value.isValidatelocaloffset()) {
                        tobeaddedoffset = 0;
                    } else {
                        tobeaddedoffset = (int) (value.getCurrentcommitedoffset().getOffsetnumber() - value.getCopylocaloffset());
                    }
                    Iterator it = value.getNon_Speculative_Writtendata().keySet().iterator();
                    int counter = 0;
                    while (it.hasNext() && (this.getStatus() == Status.ACTIVE)) {
                        try {
                            Range range = (Range) it.next();

                           
                            value.f.seek(range.getStart() + tobeaddedoffset);

                            Byte[] data = new Byte[(int) (range.getEnd() - range.getStart())];
                            byte[] bytedata = new byte[(int) (range.getEnd() - range.getStart())];
                            data = (Byte[]) value.getNon_Speculative_Writtendata().get(range);

                            for (int i = 0; i < data.length; i++) {
                                bytedata[i] = data[i];
                            }
                            value.f.write(bytedata);
                            counter++;

                        } catch (IOException ex) {
                            Logger.getLogger(ExtendedTransaction.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                } else {
                    continue;
                }
            }


            iter = hm.keySet().iterator();
            while (iter.hasNext() ) {
                INode key = (INode) iter.next();
                value = (TransactionLocalFileAttributes) hm.get(key);
                Iterator it = value.getAccesedblocks().keySet().iterator();

                while (it.hasNext()) {
                    Integer blockno = (Integer) it.next();
                    synchronized (value.adapter) {
                        //BlockDataStructure blockobj = (BlockDataStructure) value.adapter.lockmap.get(blockno);
                        //blockobj.getVersion().getAndIncrement(); for versioning strategy
                        //value.getCurrentcommitedoffset().setOffsetnumber(value.getLocaloffset());
                        //value.adapter.commitedfilesize.getAndSet(value.getFilelength());
                    }
                }
            }*/
        Iterator k = GlobaltoLocalMappings.keySet().iterator();
        while (k.hasNext()){
            TransactionalFile trf = (TransactionalFile) (k.next());
        //    synchronized(trf.getCommitedoffset()){
                trf.getCommitedoffset().setOffsetnumber(((TransactionLocalFileAttributes)GlobaltoLocalMappings.get(trf)).getLocaloffset());
                /*synchronized(benchmark.lock){
                    System.out.println(Thread.currentThread() + " KIRIR " +GlobaltoLocalMappings.get(trf).getLocaloffset());
                }*/
        //    }
        }
        //unlockAllLocks();

    }

    public void unlockAllLocks() {
        Iterator it = heldblocklocks.iterator();

        while (it.hasNext()) {
            
           Lock lock = (Lock) it.next();    
           lock.unlock();
           
            /*synchronized(benchmark.lock){
                System.out.println(Thread.currentThread().getName() + " Released the block lock for " + lock);
            }*/
        }
        heldblocklocks.clear();
        
        it = heldoffsetlocks.iterator();
        while (it.hasNext()) {
            ReentrantLock lock = (ReentrantLock) it.next(); 
            lock.unlock();   
        //    synchronized(benchmark.lock){
       //         System.out.println(Thread.currentThread().getName() + " Released the offset lock for "+ lock +"\n");
       //    }
        }
        heldoffsetlocks.clear();
    }
    
    public void abortAllReaders(){
        TreeMap hm = getSortedFileAccessMap(AccessedFiles);
        //lock phase
        Iterator iter = hm.keySet().iterator();
        TransactionalFile value;
        while (iter.hasNext()) {
            INode key = (INode) iter.next();
            Vector vec = (Vector) AccessedFiles.get(key);
            Iterator it = vec.iterator();
            while (it.hasNext())
            {
               
                value = (TransactionalFile)it.next();
           
            //value = (TransactionalFile) hm.get(key);
                //System.out.println(value.getCommitedoffset().getOffsetReaders());

                    Iterator it2 =  value.getCommitedoffset().getOffsetReaders().iterator(); // for visible readers strategy
                    while ( it2.hasNext())
                    {
                      
                        ExtendedTransaction tr = (ExtendedTransaction) it2.next();
                        if (tr != this)
                            tr.abort();
                    }
                    value.getCommitedoffset().getOffsetReaders().clear();
                //}
            }
            
            
            
            TreeMap vec2;
            if (accessedBlocks.get(key) != null){
                vec2 = (TreeMap) accessedBlocks.get(key);
            }
            else{
                vec2 = new TreeMap();

            }
            GlobalINodeState inodestate = TransactionalFileWrapperFactory.getTateransactionalFileINodeState(key);
            Iterator it2 = vec2.keySet().iterator();
          
            while (it2.hasNext())
            {
              
                Integer num = (Integer)it2.next();
                if (vec2.get(num) != BlockAccessModesEnum.READ)
                {
                  BlockDataStructure blockobj = (BlockDataStructure) inodestate.getBlockDataStructure(num);//lockmap.get(num);
                    Iterator it4 =  blockobj.getReaders().iterator(); // from here for visible readers strategy
                
                    while (it4.hasNext())
                    {
                        
                        ExtendedTransaction tr = (ExtendedTransaction) it4.next();
                        if (this != tr)
                            tr.abort();
                    }
                    blockobj.getReaders().clear();
                    
                }
            }
        
        
        
                
       /*         SortedSet sst = (SortedSet) this.getAccessedBlocks().get(key);
                Iterator it3 =  sst.iterator();
                while (it3.hasNext()){
                    Integer num = (Integer)it.next();
                    BlockDataStructure blockobj = (BlockDataStructure) value.getInodestate().lockmap.get(num);
                    Iterator it4 =  blockobj.getReaders().iterator(); // from here for visible readers strategy
                    while (it4.hasNext())
                    {
                        ExtendedTransaction tr = (ExtendedTransaction) it3.next();
                        tr.abort();
                    }

                }*/
            
        }
    }
    
    public void addPropertyChangeListener(PropertyChangeListener listener){
        this.changes.addPropertyChangeListener("status",listener);
    }
    
     public void removePropertyChangeListener(PropertyChangeListener listener){
        this.changes.removePropertyChangeListener("status",listener);
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

    public void setHeldoffsetlocks(Vector heldoffsetlocks) {
        this.heldoffsetlocks = heldoffsetlocks;
    }

    public void abortThisSystem() {
        abort();
    }

    public boolean isCommitted() {
        if (this.status == Status.COMMITTED)
            return true;
            
         return  false;
        
    }
    
    
    
}
     
    
   



