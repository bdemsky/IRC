/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package TransactionalIO.core;



import TransactionalIO.Utilities.Range;
import TransactionalIO.exceptions.AbortedException;
import TransactionalIO.exceptions.PanicException;
import TransactionalIO.benchmarks.benchmark;
import TransactionalIO.core.ExtendedTransaction.Status;
import TransactionalIO.interfaces.BlockAccessModesEnum;
import TransactionalIO.interfaces.OffsetDependency;
import com.sun.org.apache.bcel.internal.generic.IFEQ;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import sun.misc.ConditionLock;

/**
 *
 * @author navid
 */


public class TransactionalFile implements Comparable{

    
    private native int nativepread(byte buff[], long offset, int size, FileDescriptor fd);
    
    {
        System.load("/home/navid/libkooni.so");
    }
    
    
    public RandomAccessFile file;
    private INode inode;
    private int sequenceNum = 0;
    public static int currenSeqNumforInode = 0;
    /*  public AtomicLong commitedoffset;
    public AtomicLong commitedfilesize;*/
    public boolean to_be_created = false;
    public boolean writemode = false;
    public boolean appendmode = false;
    public ReentrantLock offsetlock;
    private GlobalOffset committedoffset;
    private GlobalINodeState inodestate ;
    
    
    public TransactionalFile(String filename, String mode) {
        
       
        File f = new File(filename);
  
        if ((!(f.exists()))) {
            to_be_created = true;
            file = null;

        } else {

            try {

                offsetlock = new ReentrantLock();
                file = new RandomAccessFile(f, mode);
            } catch (FileNotFoundException ex) {
 
                Logger.getLogger(TransactionalFile.class.getName()).log(Level.SEVERE, null, ex);
            }
      
        }
        inode = TransactionalFileWrapperFactory.getINodefromFileName(filename);
        inodestate = TransactionalFileWrapperFactory.createTransactionalFile(inode, filename, mode);
        
        
        sequenceNum = inodestate.seqNum;
        inodestate.seqNum++;
        
        
        


        if (mode.equals("rw")) {
            writemode = true;
        } else if (mode.equals("a")) {
            appendmode = true;
        }

        if (inodestate != null) {
            synchronized (inodestate) {
                try {
                    //      if (!(to_be_created)) {
                    //   } else {
                    //       adapter.commitedfilesize.set(0);
                    //   }

                    if (!appendmode) {
                        //commitedoffset.setOffsetnumber(0);
                        committedoffset = new GlobalOffset(0);
                    } else {
                        committedoffset = new GlobalOffset(file.length());
                    }

                } catch (IOException ex) {
                    Logger.getLogger(TransactionalFile.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        
                
    }

    private int invokeNativepread(byte buff[], long offset, int size) {
        try {
            return nativepread(buff, offset, size, file.getFD());
        } catch (IOException ex) {
            
            Logger.getLogger(TransactionalFile.class.getName()).log(Level.SEVERE, null, ex);
            return -1;
        }
        
    }

    
    public int getSequenceNum() {
        return sequenceNum;
    }

    
    public GlobalOffset getCommitedoffset() {
        return committedoffset;
    }

    public GlobalINodeState getInodestate() {
        return inodestate;
    }

    /*  public TransactionalFile(Adapter adapter, RandomAccessFile file) {
    
    this.adapter = adapter;
    this.file = file;
    decriptors = new Vector();
    }
    
    public void copyTransactionalFile(TransactionalFile tf){
    try {
    int tmp = tf.commitedoffset.get();
    boolean flag = tf.to_be_created;
    FileDescriptor fd = tf.file.getFD();
    Adapter ad = new Adapter(tf.adapter);
    } catch (IOException ex) {
    Logger.getLogger(TransactionalFile.class.getName()).log(Level.SEVERE, null, ex);
    }
    }*/
    
   /* public BlockDataStructure getBlockDataStructure(int blocknumber) {
        synchronized (inodestate.lockmap) {
            if (inodestate.lockmap.containsKey(blocknumber)) {
       
                return ((BlockDataStructure) (inodestate.lockmap.get(Long.valueOf(blocknumber))));
            } else {
       
                BlockDataStructure tmp = new BlockDataStructure(getInode(), blocknumber);
                inodestate.lockmap.put(Integer.valueOf(blocknumber), tmp);
                return tmp;
            }
        }

    }*/


     
    public INode getInode() {
        return inode;
    }
    /* public boolean deleteBlockLock(int blocknumber){
    synchronized(adapter.lockmap){
    //adapter.lockmap.get(blocknumber)
    if (adapter.lockmap.containsKey(blocknumber)){
    if (((BlockLock)(adapter.lockmap.get(blocknumber))).referncount == 0){
    adapter.lockmap.remove(adapter.lockmap.get(blocknumber));
    return true;
    }
    else
    return false;
    }
    else {
    return false;
    }
    }
    }*/
    public void close() {
        try {
            file.close();
        } catch (IOException ex) {
            Logger.getLogger(TransactionalFile.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public long getFilePointer(){
        
        ExtendedTransaction me = Wrapper.getTransaction();
        TransactionLocalFileAttributes tmp = null;
        
        if (me == null) {
            return non_Transactional_getFilePointer();
        }
        
        if (!(me.getGlobaltoLocalMappings().containsKey(this))){
           
                //if (!(me.getFilesAccesses().containsKey(this.inode))) {
                tmp = new TransactionLocalFileAttributes(0);/*, tf.getInodestate().commitedfilesize.get();*/
                  
                 Vector dummy;   
                 if (me.getAccessedFiles().containsKey(this.getInode())){
                    dummy = (Vector) me.getAccessedFiles().get(this.getInode());
                 }
                 else{ 
                  dummy = new Vector();
                  me.getAccessedFiles().put(this.getInode(), dummy);
                 }
            
        
           
      //      this.msg.put(System.nanoTime(), Thread.currentThread().getName() + " Unlocked the offset lock " + tf.offsetlock + " for file " + tf.getInode() + " form descriptor " + tf.getSequenceNum());
                dummy.add(this);
                me.getGlobaltoLocalMappings().put(this, tmp);
                me.merge_for_writes_done.put(this.getInode(), Boolean.TRUE);
 
              // me.addFile(this);
           
            //me.addFile(this);
        }
        
        tmp = (TransactionLocalFileAttributes) me.getGlobaltoLocalMappings().get(this);
        if ((tmp.getOffsetdependency() == OffsetDependency.WRITE_DEPENDENCY_1) || (tmp.getOffsetdependency() == OffsetDependency.NO_ACCESS)){   
             tmp.setOffsetdependency(OffsetDependency.READ_DEPENDENCY);
             //System.out.println("sad");
             //synchronized(this.committedoffset)
             long target;
             lockOffset(me);
             //{
             
                    if (!(this.committedoffset.getOffsetReaders().contains(me))){
                        this.committedoffset.getOffsetReaders().add(me);
                        /*synchronized(benchmark.lock){
                            benchmark.msg += Thread.currentThread().getName() + " Added to Offset Readers for file " + this.inode + " from descriptor "+ this.sequenceNum +"\n";
                        }*/
      //                  me.msg.put(System.nanoTime(), Thread.currentThread().getName() + " Added to Offset Readers for file " + this.inode + " from descriptor "+ this.sequenceNum +"\n");
                      /*  synchronized(benchmark.lock){
                          System.out.println(Thread.currentThread().getName() + " Added to Offset Readers for file " + this.inode + " from descriptor "+ this.sequenceNum +"\n");
                        }*/
                    }
             
                    tmp.setLocaloffset(tmp.getLocaloffset() + this.committedoffset.getOffsetnumber() - tmp.getCopylocaloffset());
                    target = this.committedoffset.getOffsetnumber() - tmp.getCopylocaloffset();
                    
                    
             offsetlock.unlock();         
             //me.getHeldoffsetlocks().remove(offsetlock);
             
             Iterator it;

             if ((me.getWriteBuffer().get(inode)) != null)
             {
                
                it = ((Vector) (me.getWriteBuffer().get(inode))).iterator();
                while (it.hasNext()){
                    WriteOperations wrp = (WriteOperations) it.next();
                    if (wrp.getBelongingto() == tmp && wrp.isUnknownoffset())
                        wrp.setUnknownoffset(false);
                        /*wrp.getRange().setStart(wrp.getOwnertransactionalFile().committedoffset.getOffsetnumber() - wrp.getBelongingto().getCopylocaloffset() + wrp.getRange().getStart());
                        wrp.getRange().setEnd(wrp.getOwnertransactionalFile().committedoffset.getOffsetnumber() - wrp.getBelongingto().getCopylocaloffset() + wrp.getRange().getEnd());*/
                        wrp.getRange().setStart(target + wrp.getRange().getStart());
                        wrp.getRange().setEnd(target + wrp.getRange().getEnd());
                }   
             }
              

            //}    
        }
        
       
        tmp.setUnknown_inital_offset_for_write(false);
       
     /*   synchronized(benchmark.lock){
            benchmark.msg += Thread.currentThread().getName() + " Read the offset value for the file "  + this.inode +" from descriptor " + this.sequenceNum + "\n";
        }
        me.msg += Thread.currentThread().getName() + " Read the offset value for the file "  + this.inode +" from descriptor " + this.sequenceNum + "\n";*/
       /* synchronized(benchmark.lock){
            System.out.println("offset " + Thread.currentThread()  + " " + tmp.getLocaloffset());
        }*/
        return tmp.getLocaloffset();
    }
    
    public void seek(long offset) {

        if (appendmode) {
            throw new PanicException("Cannot seek into a file opened in append mode");
        }
        ExtendedTransaction me = Wrapper.getTransaction();
        
        if (me == null) {
            non_Transactional_Seek(offset);
            return;
        }
        
        else {
         //   if (me.getStatus() != Status.ACTIVE)
          //      throw new AbortedException();
            
           TransactionLocalFileAttributes tmp = null;
          //tf.offsetlock.lock();
           
          //this.heldoffsetlocks.remove(tf.offsetlock);  
          //tf.offsetlock.unlock(); 
            if (!(me.getGlobaltoLocalMappings().containsKey(this))){
                //if (!(me.getFilesAccesses().containsKey(this.inode))) {
                 tmp = new TransactionLocalFileAttributes(offset);/*, tf.getInodestate().commitedfilesize.get();*/
                  
                 Vector dummy;   
                 if (me.getAccessedFiles().containsKey(this.getInode())){
                    dummy = (Vector) me.getAccessedFiles().get(this.getInode());
                 }
                 else{ 
                  dummy = new Vector();
                  me.getAccessedFiles().put(this.getInode(), dummy);
                 }
            
        
           
      //      this.msg.put(System.nanoTime(), Thread.currentThread().getName() + " Unlocked the offset lock " + tf.offsetlock + " for file " + tf.getInode() + " form descriptor " + tf.getSequenceNum());
                dummy.add(this);
                me.getGlobaltoLocalMappings().put(this, tmp);
                me.merge_for_writes_done.put(this.getInode(), Boolean.TRUE);
 
                //me.addFile(this);
            }
            tmp = (TransactionLocalFileAttributes) me.getGlobaltoLocalMappings().get(this);
            //tmp = ((TransactionLocalFileAttributes) (me.getFilesAccesses().get(this.getInode())));
            
            if (tmp.getOffsetdependency() == OffsetDependency.NO_ACCESS)
                tmp.setOffsetdependency(OffsetDependency.NO_DEPENDENCY);
            
            else if (tmp.getOffsetdependency() == OffsetDependency.WRITE_DEPENDENCY_1)
                tmp.setOffsetdependency(OffsetDependency.WRITE_DEPENDENCY_2);
            
            tmp.setUnknown_inital_offset_for_write(false);
          
            tmp.setLocaloffset(offset);
            
          
          /*  synchronized(benchmark.lock){
                System.out.println(tmp.getLocaloffset());
            }*/
     //       me.msg.put(System.nanoTime(), Thread.currentThread().getName() + " Seeked to the file"  + this.inode +" from descriptor " + this.sequenceNum + "\n");
        }
    }

    public int read(byte[] b) {

        if (appendmode) {
            throw new PanicException("Cannot seek into a file opened in append mode");
        }
        
        boolean firsttime = false;
        ExtendedTransaction me = Wrapper.getTransaction();
        int size = b.length;
        int result = 0;


 
        if (me == null) {  // not a transaction, but any I/O operation even though within a non-transaction is considered a single opertion transactiion 
            return non_Transactional_Read(b);
        }
        
        //if (me.getStatus() != Status.ACTIVE)
          //      throw new AbortedException();
        
  
         if (me.getGlobaltoLocalMappings().containsKey(this)){
            
            /*long target;
            Vector locktracker = new Vector();
            TreeMap hm = me.getSortedFileAccessMap(me.getAccessedFiles());;
            Vector vec = (Vector)hm.get(inode);
            Iterator vecit = vec.iterator();
            while(vecit.hasNext()){
                TransactionalFile tr = (TransactionalFile)vecit.next();
                TransactionLocalFileAttributes tmp = (TransactionLocalFileAttributes) me.getGlobaltoLocalMappings().get(tr);
                
                if ((tmp.getOffsetdependency() == OffsetDependency.WRITE_DEPENDENCY_1) || (tmp.offsetdependency == OffsetDependency.NO_ACCESS) || (tmp.getOffsetdependency() == OffsetDependency.WRITE_DEPENDENCY_2)){
                    tmp.setUnknown_inital_offset_for_write(false);
                    tmp.setOffsetdependency(OffsetDependency.READ_DEPENDENCY);  
                    tr.lockOffset(me);
		    System.out.printtln(Thread.currentThread() + " kiri");
                        if (!(tr.committedoffset.getOffsetReaders().contains(me))){
                            tr.committedoffset.getOffsetReaders().add(me);
                            target = this.committedoffset.getOffsetnumber() - tmp.getCopylocaloffset();
                            me.msg.put(System.nanoTime(), Thread.currentThread().getName() + " Added to Offset Readers for file " + this.inode + " from descriptor "+ this.sequenceNum +"\n");
                        }
                }   
            }*/
            
            
            
            
            TransactionLocalFileAttributes tmp = (TransactionLocalFileAttributes) me.getGlobaltoLocalMappings().get(this);
            tmp.setUnknown_inital_offset_for_write(false);
            if ((tmp.getOffsetdependency() == OffsetDependency.WRITE_DEPENDENCY_1) || (tmp.offsetdependency == OffsetDependency.NO_ACCESS) || (tmp.getOffsetdependency() == OffsetDependency.WRITE_DEPENDENCY_2)){
              //System.out.println(Thread.currentThread() + " here");
               //synchronized(this.committedoffset){
               lockOffset(me);
                    if (tmp.getOffsetdependency() != OffsetDependency.WRITE_DEPENDENCY_2){     
                        tmp.setLocaloffset(tmp.getLocaloffset() + this.committedoffset.getOffsetnumber() - tmp.getCopylocaloffset()); 
                    }
                    
                    tmp.setOffsetdependency(OffsetDependency.READ_DEPENDENCY);  
                    if (!(this.committedoffset.getOffsetReaders().contains(me))){
                        this.committedoffset.getOffsetReaders().add(me);
                         /*                                  synchronized(benchmark.lock){
                                       System.out.println("adding offset " + committedoffset + " " +Thread.currentThread());
                                    }*/
                       /* synchronized(benchmark.lock){
                            benchmark.msg += Thread.currentThread().getName() + " Added to Offset Readers for file " + this.inode + " from descriptor "+ this.sequenceNum +"\n";
                        }*/
     //                   me.msg.put(System.nanoTime(), Thread.currentThread().getName() + " Added to Offset Readers for file " + this.inode + " from descriptor "+ this.sequenceNum +"\n");
                        /*synchronized(benchmark.lock){
                          System.out.println(Thread.currentThread().getName() + " Added to Offset Readers for file " + this.inode + " from descriptor "+ this.sequenceNum);
                        }*/
                    }
               
               offsetlock.unlock();
              // me.getHeldoffsetlocks().remove(offsetlock);     
                //}
            }
            Iterator it;
            if (me.getWriteBuffer().get(inode) != null)
            //if (!(((Vector)(me.getWriteBuffer().get(inode))).isEmpty()))
            {
                it = ((Vector) (me.getWriteBuffer().get(inode))).iterator();
                while (it.hasNext()){
                 
                      WriteOperations wrp = (WriteOperations) it.next();
                      if (wrp.isUnknownoffset()){
                        wrp.setUnknownoffset(false);
                        //synchronized(wrp.getOwnertransactionalFile().committedoffset){
                        wrp.getOwnertransactionalFile().lockOffset(me);
                        
                            wrp.getRange().setStart(wrp.getOwnertransactionalFile().committedoffset.getOffsetnumber() - wrp.getBelongingto().getCopylocaloffset() + wrp.getRange().getStart());
                            wrp.getRange().setEnd(wrp.getOwnertransactionalFile().committedoffset.getOffsetnumber() - wrp.getBelongingto().getCopylocaloffset() + wrp.getRange().getEnd());
                            if ((wrp.getBelongingto().getOffsetdependency() == OffsetDependency.WRITE_DEPENDENCY_1) || (wrp.getBelongingto().offsetdependency == OffsetDependency.NO_ACCESS) || (wrp.getBelongingto().getOffsetdependency() == OffsetDependency.WRITE_DEPENDENCY_2)){
                                wrp.getBelongingto().setOffsetdependency(OffsetDependency.READ_DEPENDENCY);
                                wrp.getBelongingto().setUnknown_inital_offset_for_write(false);
                                if (!(wrp.getOwnertransactionalFile().committedoffset.getOffsetReaders().contains(me)))
                                    wrp.getOwnertransactionalFile().committedoffset.getOffsetReaders().add(me);
                                wrp.getBelongingto().setLocaloffset(wrp.getBelongingto().getLocaloffset() + wrp.getOwnertransactionalFile().committedoffset.getOffsetnumber() - wrp.getBelongingto().getCopylocaloffset());
                               /* synchronized(benchmark.lock){
                                    benchmark.msg += Thread.currentThread().getName() + " Added to Offset Readers for file " + this.inode + " from descriptor "+ wrp.getOwnertransactionalFile().sequenceNum +"\n";
                                }*/
                      //          me.msg.put(System.nanoTime(), Thread.currentThread().getName() + " Added to Offset Readers for file " + this.inode + " from descriptor "+ wrp.getOwnertransactionalFile().sequenceNum +"\n");   
                            }
                         
                        // me.getHeldoffsetlocks().remove(wrp.getOwnertransactionalFile().offsetlock);   
                         wrp.getOwnertransactionalFile().offsetlock.unlock();  
                         
                         //}
                        
                        
                        
                        markAccessedBlocks(me, (int)wrp.getRange().getStart(), (int)(wrp.getRange().getEnd() - wrp.getRange().getStart()), BlockAccessModesEnum.WRITE);
//                        markWriteBlocks((int)wrp.getRange().getStart(), (int)(wrp.getRange().getEnd() - wrp.getRange().getStart()));
                      }
                }
            }
            
        /*    if (!(me.isWritesmerged())){
               //    synchronized(benchmark.lock){
                 //   System.out.println("ssssad " + Thread.currentThread() + " " + me.getWriteBuffer());
               // }
                mergeWrittenData();
            }*/
          //  System.out.println("ssssad " + Thread.currentThread() + " " + me.getWriteBuffer());
            if ((Boolean)me.merge_for_writes_done.get(inode) == Boolean.FALSE){
               // synchronized(benchmark.lock){
                System.out.println("ssssad " + Thread.currentThread() + " " + me.getWriteBuffer());
                 mergeWrittenData(me);
               //}
            }
               
            
            long loffset = tmp.getLocaloffset();
            markAccessedBlocks(me, loffset, size, BlockAccessModesEnum.READ);
   

            Vector writebuffer;
            if ((me.getWriteBuffer().get(this.inode)) != null)
                writebuffer = (Vector) (me.getWriteBuffer().get(this.inode));
            else {
                writebuffer = new Vector();
                me.getWriteBuffer().put(this.inode, writebuffer);
            }
            Range readrange = new Range(loffset, loffset + size);
            Range writerange = null;
            Range[] intersectedrange = new Range[writebuffer.size()];
            WriteOperations[] markedwriteop = new WriteOperations[writebuffer.size()];
            
            int counter = 0;




            boolean flag = false;
            //System.out.println("yani che>??");
                    
            it = writebuffer.iterator();
            while (it.hasNext()) {
                
                WriteOperations wrp = (WriteOperations) it.next();
                writerange = wrp.getRange();
                if (writerange.includes(readrange)) {
                    markedwriteop[counter] = wrp;
                    flag = true;
                    break;
                }

                if (writerange.hasIntersection(readrange)) {
                    intersectedrange[counter] = readrange.intersection(writerange);
                    markedwriteop[counter] = wrp;
                   
                    counter++;
                }
            }


            // for block versioning mechanism
            /*if (!(validateBlocksVersions(startblock, targetblock))) { ////check to see if version are still valid 
            
                    throw new AbortedException();
            
            }*/
            if (flag) {
                
                result = readFromBuffer(b, tmp, markedwriteop[counter],writerange);    
               
             /*   synchronized(benchmark.lock){
                    benchmark.msg += Thread.currentThread().getName() + " Read " + this.inode + " from descriptor "+ this.sequenceNum +"\n";
                }*/
                
            //    me.msg.put(System.nanoTime(), Thread.currentThread().getName() + " Read " + this.inode + " from descriptor "+ this.sequenceNum +"\n");
                return result;
            }
            
            else{
                
                if (counter == 0) {
                  /*                 synchronized(benchmark.lock){
                                       System.out.println("here"  +Thread.currentThread());
                    }*/
                
                 //   lockOffset(me);
                    result = readFromFile(me, b, tmp);
                }
                else {
                    
                    for (int i = 0; i < counter; i++) {

                        
                        Byte[] data = markedwriteop[i].getData();
                        byte[] copydata = new byte[data.length];

                        for (int j = 0; j < data.length; j++) {
                            copydata[j] = data[j].byteValue();
                        }
                        System.arraycopy(copydata, (int) (intersectedrange[i].getStart() - markedwriteop[i].getRange().getStart()), b, (int) (intersectedrange[i].getStart() - readrange.getStart()), (int) (Math.min(intersectedrange[i].getEnd(), readrange.getEnd()) - intersectedrange[i].getStart()));
                        result += Math.min(intersectedrange[i].getEnd(), readrange.getEnd()) - intersectedrange[i].getStart();
                    }

                    Range[] non_intersected_ranges = readrange.minus(intersectedrange, counter);
                    Vector occupiedblocks = new Vector();
                    Vector heldlocks = new Vector();
                    for (int i = 0; i < non_intersected_ranges.length; i++) {
                        int st = FileBlockManager.getCurrentFragmentIndexofTheFile(non_intersected_ranges[i].getStart());
                        int en = FileBlockManager.getCurrentFragmentIndexofTheFile(non_intersected_ranges[i].getEnd());
                        for (int j = st; j <= en; j++) {
                            if (!(occupiedblocks.contains(Integer.valueOf(j)))) {
                                occupiedblocks.add(Integer.valueOf(j));
                            }
                        }
                    }


                
                    lockOffset(me);
                    me.getHeldoffsetlocks().add(offsetlock);
                    
                    
                    for (int k = 0; k < occupiedblocks.size(); k++) {   // locking the block locks

                        while (me.getStatus() == Status.ACTIVE) {
                          
                            BlockDataStructure block = this.inodestate.getBlockDataStructure((Integer)(occupiedblocks.get(k)));//(BlockDataStructure) tmp.adapter.lockmap.get(Integer.valueOf(k)));
                      
                            //synchronized(block){

                               // if (block.getLock().readLock().tryLock()) {
                            block.getLock().readLock().lock();
                                   /* synchronized(benchmark.lock){
                                        benchmark.msg += Thread.currentThread().getName() + " Locked The Block Number " + block.getBlocknumber()+ " for file " + this.inode + " from descriptor "+ this.sequenceNum +"\n";
                                    }*/
           //                         me.msg.put(System.nanoTime(), Thread.currentThread().getName() + " Added The Block Number " + block.getBlocknumber()+ " for file " + this.inode + " from descriptor "+ this.sequenceNum +"\n");
                                    //synchronized (block){
                                    if (!(block.getReaders().contains(me))){
                                       /*     synchronized(benchmark.lock){
                                                benchmark.msg += Thread.currentThread().getName() + " Added to Block Readers for Block Number " + block.getBlocknumber()+ " for file " + this.inode + " from descriptor "+ this.sequenceNum +"\n";
                                            }*/
              //                          me.msg.put(System.nanoTime(), Thread.currentThread().getName() + " Added to Block Readers for Block Number " + block.getBlocknumber()+ " for file " + this.inode + " from descriptor "+ this.sequenceNum +"\n");
                                        block.getReaders().add(me);
                                     }
                                     me.getHeldblocklocks().add(block.getLock().readLock());
                                     //heldlocks.add(block.getLock().readLock());
                                    //}
                                    break;
                              //  }
                                //} else {
                               //     me.getContentionmanager().resolveConflict(me, block);
                               // }
                            //}
                        }
                       if (me.getStatus() == Status.ABORTED) {
                            //unlockLocks(heldlocks);
                            //offsetlock.unlock();
           //                 me.msg.put(System.nanoTime(), Thread.currentThread().getName() + " Aborted in locking blocks in read\n");
                   /*         synchronized(benchmark.lock){
                                benchmark.msg += Thread.currentThread().getName() + " Aborted \n";
                            }*/
                          //  Thread.currentThread().stop();
                            throw new AbortedException();
                        }
                    }
                   /*  
                        int expvalue = ((Integer) tmp.getBlockversions().get(Integer.valueOf(k))).intValue();
                        while (me.getStatus() == Status.ACTIVE) {
                            BlockDataStructure block = ((BlockDataStructure) tmp.adapter.lockmap.get(Integer.valueOf(k)));
                            if (block.getLock().tryLock()) {
                                heldlocks.add(block.getLock());
                                if (!(block.getVersion().get() == expvalue)) {  // for block versioning mechanism
                                        me.abort();
                                } 
                                else {
                                        break;
                                }
                            } 
                            else {
                                    me.getContentionManager().resolveConflict(me, block.getOwner());
                            }
                        }
                      if (me.getStatus() == Status.ABORTED) {
                            unlockLocks(heldlocks);
                            offsetlock.unlock();
                            throw new AbortedException();
                        }
                    }
                   }*/


                    for (int i = 0; i < non_intersected_ranges.length; i++) {
                        try {
                            synchronized(benchmark.lock){
                                System.out.println("read start " + non_intersected_ranges[i].getStart());
                            }
                            file.seek(non_intersected_ranges[i].getStart());
                            int tmpsize = file.read(b, (int) (non_intersected_ranges[i].getStart() - readrange.getStart()), (int) (non_intersected_ranges[i].getEnd() - non_intersected_ranges[i].getStart()));
                            result += tmpsize;
                        } catch (IOException ex) {
                            
                            //unlockLocks(heldlocks);
                            //offsetlock.unlock();
                            Logger.getLogger(TransactionalFile.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                    me.unlockAllLocks();
                    tmp.setLocaloffset(tmp.getLocaloffset() + result);
                   // unlockLocks(heldlocks);
                   // offsetlock.unlock();
                }
                /*synchronized(benchmark.lock){
                    benchmark.msg += Thread.currentThread().getName() + " Read from file " + this.inode + " from descriptor "+ this.sequenceNum +"\n";
                }*/
           //     me.msg.put(System.nanoTime(), Thread.currentThread().getName() + " Read from file " + this.inode + " from descriptor "+ this.sequenceNum +"\n");
                return result;
            }

        } else {           // add to the readers list  
            System.out.println("form read???");
            me.addFile(this);
            return read(b);
        }

    }

    public void write(byte[] data) throws IOException {

        if (!(writemode)) {
            throw new IOException();

        }

        ExtendedTransaction me = Wrapper.getTransaction();
        int size = data.length;


        if (me == null) // not a transaction 
        {
            
            non_Transactional_Write(data);
            return;
        }
        
        //else if (me.getFilesAccesses().containsKey(this.getInode())) // 
        //{
     //   if (me.getStatus() != Status.ACTIVE)
       //         throw new AbortedException();
        
        if (me.getGlobaltoLocalMappings().containsKey(this)) // 
        {
            
            
            Byte[] by = new Byte[size];
            for (int i = 0; i < size; i++) {
                by[i] = Byte.valueOf(data[i]);
            }
            TransactionLocalFileAttributes tmp = ((TransactionLocalFileAttributes) (me.getGlobaltoLocalMappings().get(this)));

            /*if (appendmode) {
                newwriterange = new Range((((Range) (tm.firstKey())).getStart()), (((Range) (tm.firstKey())).getEnd()) + size);
                Range range = new Range((((Range) (tm.firstKey())).getStart()), (((Range) (tm.firstKey())).getEnd()));
                Byte[] appenddata = new Byte[(int) (newwriterange.getEnd() - newwriterange.getStart())];
                Byte[] tempor = new Byte[(int) (range.getEnd() - range.getStart())];
                System.arraycopy(tempor, 0, appenddata, 0, tempor.length);
                System.arraycopy(by, 0, appenddata, tempor.length, by.length);
                tm.remove(range);
                tm.put(newwriterange, appenddata);
                tmp.setLocaloffset(loffset + size);
                tmp.setFilelength(tmp.getFilelength() + size);

                return;
            }*/
            Vector dummy;
            if (((Vector)(me.getWriteBuffer().get(this.inode))) != null){
                dummy = new Vector((Vector)(me.getWriteBuffer().get(this.inode)));
            }
            else 
                dummy = new Vector();
            /* synchronized(benchmark.lock){
                    System.out.println(Thread.currentThread() + " gg " + tmp.getLocaloffset() + " " + (tmp.getLocaloffset()+by.length));
                }*/
            /*if (!(tmp.isUnknown_inital_offset_for_write())){
                lockOffset(me);
                tmp.setLocaloffset(this.committedoffset.getOffsetnumber());
                offsetlock.unlock();
            }*/
                
            dummy.add(new WriteOperations(by, new Range(tmp.getLocaloffset(), tmp.getLocaloffset() + by.length), tmp.isUnknown_inital_offset_for_write(), this, tmp));
            me.getWriteBuffer().put(this.inode, dummy);
            
            long loffset = tmp.getLocaloffset();
             
             
            tmp.setLocaloffset(tmp.getLocaloffset() + by.length);
            
            me.merge_for_writes_done.put(inode, Boolean.FALSE);
            //me.setWritesmerged(false);
            
           
            
            if (!(tmp.isUnknown_inital_offset_for_write())){
                markAccessedBlocks(me, loffset, size, BlockAccessModesEnum.WRITE);
//                markWriteBlocks(loffset, size);
            }
            /*{
                int startblock = FileBlockManager.getCurrentFragmentIndexofTheFile(loffset);
                    int targetblock = FileBlockManager.getTargetFragmentIndexofTheFile(loffset, size);
                    for (int i = startblock; i <= targetblock; i++) {
                        if (me.getAccessedBlocks().containsKey(Integer.valueOf(i))) {
                            if (((BlockAccessModesEnum) (me.getAccessedBlocks().get(Integer.valueOf(i)))) == BlockAccessModesEnum.READ) {
                                me.getAccessedBlocks().put(Integer.valueOf(i), BlockAccessModesEnum.READ_WRITE);
                            }
                        } else {
                            me.getAccessedBlocks().put(Integer.valueOf(i), BlockAccessModesEnum.WRITE);
                        // tmp.getBlockversions().put(Integer.valueOf(i), Integer.valueOf(getBlockDataStructure(i).getVersion().get())); //For Block Versioning Mechanism
                        }
                    }
            }*/
           
            if (tmp.getOffsetdependency() == OffsetDependency.NO_ACCESS)
                tmp.offsetdependency = OffsetDependency.WRITE_DEPENDENCY_1;
            
             
            
            /*if ((tmp.access_from_absolute_offset) || !(tmp.relocatablewrite))
            {  
                int startblock = FileBlockManager.getCurrentFragmentIndexofTheFile(loffset);
                int targetblock = FileBlockManager.getTargetFragmentIndexofTheFile(loffset, size);
                for (int i = startblock; i <= targetblock; i++) {
                    if (tmp.getAccesedblocks().containsKey(Integer.valueOf(i))) {
                        if (((BlockAccessModesEnum) (tmp.getAccesedblocks().get(Integer.valueOf(i)))) == BlockAccessModesEnum.READ) {
                            tmp.getAccesedblocks().put(Integer.valueOf(i), BlockAccessModesEnum.READ_WRITE);
                        }
                    } else {
                        tmp.getAccesedblocks().put(Integer.valueOf(i), BlockAccessModesEnum.WRITE);
                    // tmp.getBlockversions().put(Integer.valueOf(i), Integer.valueOf(getBlockDataStructure(i).getVersion().get())); //For Block Versioning Mechanism
                    }
                }
            }
            
            if (tmp.access_from_absolute_offset){
                
                mergeWrittenData(tmp.getNon_Speculative_Writtendata(), data, newwriterange);
                tmp.setLocaloffset(loffset + size);
                if (tmp.getLocaloffset() > tmp.getFilelength()) {
                    tmp.setFilelength(tmp.getLocaloffset());
                }
                return;
            }
            
            else  // for comtimious determingin the accessed block is postpond till commit instant
            {   
                Byte[] dd = new Byte[size];
                System.arraycopy(by, 0, dd, 0, size);
                if (!(tmp.getSpeculative_Writtendata().isEmpty())){
                
                    Range lastrange = (Range) tmp.getSpeculative_Writtendata().lastKey();
                    if (lastrange.getEnd() == newwriterange.getStart()){
                        dd = new Byte[(int)(size + lastrange.getEnd() - lastrange.getStart())];
                        System.arraycopy(((Byte[])tmp.getSpeculative_Writtendata().get(lastrange)), 0, dd, 0, (int)(lastrange.getEnd() - lastrange.getStart()));
                        System.arraycopy(by, 0, dd, (int)(lastrange.getEnd() - lastrange.getStart()), size);
                        newwriterange = new Range(lastrange.getStart(), size + lastrange.getEnd());
                        tmp.getSpeculative_Writtendata().remove(lastrange);
                    }
                       
                }
                
                tmp.getSpeculative_Writtendata().put(newwriterange, dd);
                
                tmp.setLocaloffset(loffset + size);
                if (tmp.getLocaloffset() > tmp.getFilelength()) {
                    tmp.setFilelength(tmp.getLocaloffset());
                }
                
                return;
            }
                
            */

            /*

            if (tmp.accessmode == TransactionLocalFileAttributes.MODE.READ)
            tmp.accessmode = TransactionLocalFileAttributes.MODE.READ_WRITE;
            else if (tmp.accessmode == TransactionLocalFileAttributes.MODE.WRITE)
            simpleWritetoBuffer(by, newwriterange, tm);
             */
           //  me.msg.put(System.nanoTime(), Thread.currentThread().getName() + " Writes to " + this.inode + " from descriptor "+ this.sequenceNum +"\n");

        } else {
           // System.out.println("form write??");
           // me.addFile(this/*, TransactionLocalFileAttributes.MODE.WRITE*/);
               //if (!(me.getGlobaltoLocalMappings().containsKey(this))){
                //if (!(me.getFilesAccesses().containsKey(this.inode))) {
                TransactionLocalFileAttributes tmp = new TransactionLocalFileAttributes(0);/*, tf.getInodestate().commitedfilesize.get();*/
                  
                 Vector dummy;   
                 if (me.getAccessedFiles().containsKey(this.getInode())){
                    dummy = (Vector) me.getAccessedFiles().get(this.getInode());
                 }
                 else{ 
                  dummy = new Vector();
                  me.getAccessedFiles().put(this.getInode(), dummy);
                 }
            
        
           
      //      this.msg.put(System.nanoTime(), Thread.currentThread().getName() + " Unlocked the offset lock " + tf.offsetlock + " for file " + tf.getInode() + " form descriptor " + tf.getSequenceNum());
                dummy.add(this);
                me.getGlobaltoLocalMappings().put(this, tmp);
                me.merge_for_writes_done.put(this.getInode(), Boolean.TRUE);
 
            //me.addFile(this);
            //}
            
            write(data);
        }
       /* synchronized(benchmark.lock){
            benchmark.msg += Thread.currentThread().getName() + " Writes to " + this.inode + " from descriptor "+ this.sequenceNum +"\n";
        }*/
        

    }

    
    private void markAccessedBlocks(ExtendedTransaction me,long loffset, int size, BlockAccessModesEnum mode){
        
        TreeMap map;
        
        if (me.getAccessedBlocks().get(this.getInode()) != null)
            map = (TreeMap) me.getAccessedBlocks().get(this.getInode());
        else{ 
            map = new TreeMap();
            me.getAccessedBlocks().put(this.inode, map);
        }
        int startblock = FileBlockManager.getCurrentFragmentIndexofTheFile(loffset);
        int targetblock = FileBlockManager.getTargetFragmentIndexofTheFile(loffset, size);
        for (int i = startblock; i <= targetblock; i++) {
            if (map.containsKey(Integer.valueOf(i))){
                 if (map.get(Integer.valueOf(i)) != mode){ 
                    map.put(Integer.valueOf(i), BlockAccessModesEnum.READ_WRITE);
                 }
            }
            else{
                  map.put(Integer.valueOf(i), mode);
            }
        }
        /*int startblock = FileBlockManager.getCurrentFragmentIndexofTheFile(loffset);
        int targetblock = FileBlockManager.getTargetFragmentIndexofTheFile(loffset, size);
        for (int i = startblock; i <= targetblock; i++) {
            if (me.getAccessedBlocks().containsKey(Integer.valueOf(i))) {
                if (((BlockAccessModesEnum) (me.getAccessedBlocks().get(Integer.valueOf(i)))) == BlockAccessModesEnum.READ) {
                    me.getAccessedBlocks().put(Integer.valueOf(i), BlockAccessModesEnum.READ_WRITE);
                 }
                
                
             } else {
                    me.getAccessedBlocks().put(Integer.valueOf(i), BlockAccessModesEnum.WRITE);
                // tmp.getBlockversions().put(Integer.valueOf(i), Integer.valueOf(getBlockDataStructure(i).getVersion().get())); //For Block Versioning Mechanism
             }
        }*/
    }
    
/*    private void markWriteBlocks(long loffset, int size){
        ExtendedTransaction me = CustomThread.getTransaction();
        SortedSet tt;
        if (me.writeBlocks.get(this.inode) != null)
            tt = (SortedSet) me.writeBlocks.get(this.inode);
        else {
            tt = new TreeSet();
            me.writeBlocks.put(this.inode, tt);
        }

        int startblock = FileBlockManager.getCurrentFragmentIndexofTheFile(loffset);
        int targetblock = FileBlockManager.getTargetFragmentIndexofTheFile(loffset, size);
        for (int i = startblock; i <= targetblock; i++) {
            tt.add(Integer.valueOf(i));
        }
    }*/
    
    private int readFromFile(ExtendedTransaction me, byte[] readdata, TransactionLocalFileAttributes tmp) {
     

        //ExtendedTransaction me = Wrapper.getTransaction();
        int st = FileBlockManager.getCurrentFragmentIndexofTheFile(tmp.getLocaloffset());
        int end = FileBlockManager.getTargetFragmentIndexofTheFile(tmp.getLocaloffset(), readdata.length);
        
         BlockDataStructure block = null;
         boolean locked = false;
        for (int k = st; k <= end; k++) {
           // int expvalue = ((Integer) tmp.getBlockversions().get(Integer.valueOf(k))).intValue(); // all comments here for versioning mechanism
            while (me.getStatus() == Status.ACTIVE) {
                //BlockDataStructure block = ((BlockDataStructure) this.inodestate.lockmap.get(Integer.valueOf(k)));
              //  BlockDataStructure block;
                //if (me.getAccessedBlocks().get(inode))
                block = this.inodestate.getBlockDataStructure(Integer.valueOf(k));
      
                block.getLock().readLock().lock();
                      //  me.getHeldblocklocks().add(block.getLock().readLock());
                        if (!(block.getReaders().contains(me))){
                            block.getReaders().add(me);
                        }
                        locked = true;
             //       if (!(block.getVersion().get() == expvalue)) {
             //           me.abort();
             //       } else {
                     break;
             //       }
           //     }
            /* else {
                    me.getContentionmanager().resolveConflict(me, block);
                }*/

            }
            if (me.getStatus() == Status.ABORTED) {
                int m;
                if (locked){
                    m = k+1;
                }
                else 
                    m = k;
                for (int i=st; i<m; i++){
                 /*   System.out.println("///////////////////");
                    synchronized(benchmark.lock){
                      System.out.println(block.getBlocknumber() + " =? " + i);
                    }*/
                    block = this.inodestate.getBlockDataStructure(Integer.valueOf(k));
                    me.getHeldblocklocks().add(block.getLock().readLock());
                  //  System.out.println("///////////////////");
                }
             
                locked = false;
                
                throw new AbortedException();
            }

        }
        if (me.getStatus() == Status.ABORTED) {
             for (int i=st; i<=end; i++){
                    block = this.inodestate.getBlockDataStructure(Integer.valueOf(i));
                    me.getHeldblocklocks().add(block.getLock().readLock());
             }
                throw new AbortedException();
        }
        
        int size = -1;
     
            //ByteBuffer buffer = ByteBuffer.wrap(readdata);
            //size = file.getChannel().read(buffer, tmp.getLocaloffset());
            size = invokeNativepread(readdata, tmp.getLocaloffset(), readdata.length);
            
           // synchronized(benchmark.lock){
           // if (Integer.valueOf(Thread.currentThread().getName().substring(7)) == 0)
          /*  for (int i =0; i<readdata.length; i++)
                System.out.println(Thread.currentThread() +  " " + (char)readdata[i]);
            }*/
           // file.seek(tmp.getLocaloffset());
          //  size = file.read(readdata);

            tmp.setLocaloffset(tmp.getLocaloffset() + size);
            
            if (size == 0)
                size = -1;
  
           
         /*   while (it.hasNext()) {
            
                Lock lock = (Lock) it.next();
                lock.unlock();
            }*/
        //    me.getHeldblocklocks().clear();
    // }
        
        if (me.getStatus() == Status.ABORTED) {
                  for (int i=st; i<=end; i++){
                    block = this.inodestate.getBlockDataStructure(Integer.valueOf(i));
                    me.getHeldblocklocks().add(block.getLock().readLock());
             }
                throw new AbortedException();
        }
        for (int k = st; k <= end; k++) {
                    block = this.inodestate.getBlockDataStructure(Integer.valueOf(k));
                    block.getLock().readLock().unlock();
        }
        return size;


    }

    private int readFromBuffer(byte[] readdata, TransactionLocalFileAttributes tmp, WriteOperations wrp, Range writerange) {
        /*synchronized(benchmark.lock){
            System.out.println("in read buffer " + Thread.currentThread());
        }*/
        
        long loffset = tmp.getLocaloffset();

        Byte[] data = (Byte[]) wrp.getData();
        byte[] copydata = new byte[data.length];

        for (int i = 0; i < data.length; i++) {
            copydata[i] = data[i].byteValue();
        }
        System.arraycopy(copydata, (int) (loffset - writerange.getStart()), readdata, 0, readdata.length);
        tmp.setLocaloffset(tmp.getLocaloffset() + readdata.length);
        return readdata.length;

    }

    public void simpleWritetoBuffer(Byte[] data, Range newwriterange, TreeMap tm) {
        tm.put(newwriterange, data);
    }

    public void unlockLocks(Vector heldlocks) {
        for (int i = 0; i < heldlocks.size(); i++) {
            ((Lock) heldlocks.get(i)).unlock();
        }
    }
    

/*    public boolean validateBlocksVersions(int startblock, int targetblock) { // For Block Versioning Mechanism
        boolean valid = true;
        ExtendedTransaction me = ExtendedTransaction.getTransaction();
        TransactionLocalFileAttributes tmp = ((TransactionLocalFileAttributes) (me.getFilesAccesses().get(this.getInode())));
        for (int i = startblock; i <= targetblock; i++) {
            int expvalue = ((Integer) tmp.getBlockversions().get(Integer.valueOf(i))).intValue();
            BlockDataStructure block = ((BlockDataStructure) tmp.adapter.lockmap.get(Integer.valueOf(i)));
            if (expvalue != block.getVersion().get()) {
                valid = false;
                break;
            }
        }

        return valid;
    }*/



    public void setInode(INode inode) {
        this.inode = inode;
    }
    
    public void lockOffset(ExtendedTransaction me){
    //     
           // System.out.println(Integer.getInteger(me.getStatus().toString()));
          //  lo.lockWhen(sequenceNum);
            boolean locked = false;
            while (me.getStatus() == Status.ACTIVE) {                        //locking the offset
                  //synchronized(this.commitedoffset){
               //   System.out.println("Trying");
              //  try{
              //  offsetlock.lockInterruptibly();
                    offsetlock.lock();
               // }
               // catch(InterruptedException e){
                 //   System.out.println("dsadsa");
                //}
                
                //me.getHeldoffsetlocks().add(offsetlock);
               
               locked = true;
               break;
                
                
               //   if (offsetlock.tryLock()) {
                        //    synchronized(this.commitedoffset){
                        //        this.commitedoffset.setOffsetOwner(me);
                      //System.out.println(Thread.currentThread().getName() + " grabbd the lock");
                       //     }
                   /*   synchronized(benchmark.lock){
                        benchmark.msg += Thread.currentThread().getName() + " Locked the offset lock for " + this.inode + " from descriptor "+ this.sequenceNum +"\n";
                      }*/
                   /*   synchronized(benchmark.lock){
                                       System.out.println(Thread.currentThread() + "LOCked the offset lock "  + offsetlock);
                      }*/
                      
                      //offsetlocks.add(offsetlock);
    //                  me.msg.put(System.nanoTime(), Thread.currentThread().getName() + " Locked the offset lock for file " + this.inode + " from descriptor "+ this.sequenceNum +"\n");
                      //break;
                 // }
                       //} 
                       //me.getContentionmanager().resolveConflict(me, this.commitedoffset);
            }
            
          //  if (me.getStatus() != Status.ACTIVE){
             
    //             me.msg.put(System.nanoTime(), Thread.currentThread().getName() + " Aborted  in lock offset\n");
                 /*synchronized(benchmark.lock){
                    benchmark.msg += Thread.currentThread().getName() + " Aborted \n";
                 }*/
                 //  CustomThread.getTransaction().setStatus(Status.ACTIVE);
                 //  CustomThread.getProgram().execute();
//                 if (offsetlock.isHeldByCurrentThread())
                 //if (locked)
                 //  offsetlock.unlock();
                 //unlockOffsetLocks();
                /* synchronized(benchmark.lock){
                     System.out.println("aborting " + committedoffset + " " +Thread.currentThread());
                 }*/
                 //Thread.currentThread().stop();
            if (me.getStatus() != Status.ACTIVE){
               if (locked)
                    me.getHeldoffsetlocks().add(offsetlock);
                throw new AbortedException();
            }
                   
           // }
    }
    
    public void mergeWrittenData(ExtendedTransaction me/*TreeMap target, byte[] data, Range to_be_merged_data_range*/){
            
            //ExtendedTransaction me = Wrapper.getTransaction();
            boolean flag = false;
            Vector vec = (Vector) me.getWriteBuffer().get(this.inode);     
            Range intersectedrange = new Range(0, 0);
            Iterator it1 = vec.iterator();
            WriteOperations wrp;
            WriteOperations wrp2;
            Vector toberemoved = new Vector();
            while (it1.hasNext()) {
                wrp = (WriteOperations) (it1.next());
                
                if (toberemoved.contains(wrp)){
                    continue;
                }
                    
                Iterator it2 = vec.listIterator();
                while (it2.hasNext()) {
                    flag = false;
                    wrp2 = (WriteOperations) (it2.next());
                    /*if (wrp2.getRange().includes(wrp.getRange())) {
                        flag = true;
                        intersect = wrp2.getRange().intersection(wrp.getRange());
                        break;
                    }
                    */
                    if ((wrp2 == wrp) || toberemoved.contains(wrp2)){
                        continue;
                    }
                        
                    if (wrp.getRange().hasIntersection(wrp2.getRange())) {
                        flag = true;
                        intersectedrange = wrp2.getRange().intersection(wrp.getRange());
                        toberemoved.add(wrp2);
                    }
                    
                    
                    long startprefix = 0;
                    long endsuffix = 0;
                    long startsuffix = 0;
                    int prefixsize = 0;
                    int suffixsize = 0;
                    int intermediatesize = 0;
                    Byte[] prefixdata = null;
                    Byte[] suffixdata = null;
                    boolean prefix = false;
                    boolean suffix = false;
                    if (flag){
                        
                        
                        if (wrp.getRange().getStart() < wrp2.getRange().getStart()) {
                             prefixdata = new Byte[(int) (wrp2.getRange().getStart() - wrp.getRange().getStart())];
                             prefixdata = (Byte[]) (wrp.getData());
                             startprefix = wrp.getRange().getStart();
                             prefixsize = (int)(intersectedrange.getStart() - startprefix);
                             intermediatesize = (int)(intersectedrange.getEnd() -intersectedrange.getStart());
                             prefix = true;   
                        }

                        else if (wrp2.getRange().getStart() <= wrp.getRange().getStart()) {
                             prefixdata = new Byte[(int) (wrp.getRange().getStart() - wrp2.getRange().getStart())];
                             prefixdata = (Byte[]) (wrp2.getData());
                             startprefix = wrp2.getRange().getStart();
                             prefixsize = (int)(intersectedrange.getStart() - startprefix);
                             intermediatesize = (int)(intersectedrange.getEnd() -intersectedrange.getStart());
                             prefix = true;
                        }

                        if (wrp2.getRange().getEnd() >= wrp.getRange().getEnd()) {

                             suffixdata = new Byte[(int) (wrp2.getRange().getEnd() - intersectedrange.getEnd())];
                             suffixdata = (Byte[]) (wrp2.getData());
                             startsuffix = intersectedrange.getEnd() - wrp2.getRange().getStart();
                             suffixsize = (int)(wrp2.getRange().getEnd() - intersectedrange.getEnd());
                             suffix = true;
                             //System.out.println("wrp2 > wrp");
                            
                             endsuffix = wrp2.getRange().getEnd();
                             //suffixstart = (int) (intersectedrange[i].getEnd() - intersectedrange[i].getStart());
                        }

                        else if (wrp.getRange().getEnd() > wrp2.getRange().getEnd()) {
                             suffixdata = new Byte[(int) (wrp.getRange().getEnd() - intersectedrange.getEnd())];
                             suffixdata = (Byte[]) (wrp.getData());
                             startsuffix = intersectedrange.getEnd() - wrp.getRange().getStart();
                             suffixsize = (int)(wrp.getRange().getEnd() - intersectedrange.getEnd());
                            // System.out.println("wrp2 < wrp");
                             endsuffix = wrp.getRange().getEnd();
                             suffix = true;

                        }
                    /*   System.out.println("prefix start:" + startprefix);
                        System.out.println("suffix end:" + endsuffix);
                        System.out.println("suffix start:" + startsuffix);
                        System.out.println("intermediate start:" + intermediatetart);
                      
                        System.out.println("suffix size:" + suffixsize);*/
                        

                        Byte[] data_to_insert;

                        if ((prefix) && (suffix)) {
                            data_to_insert = new Byte[(int) (endsuffix - startprefix)];
                            System.arraycopy(prefixdata, 0, data_to_insert, 0, prefixsize);
                            System.arraycopy(wrp2.getData(), (int)(intersectedrange.getStart() - wrp2.getRange().getStart()), data_to_insert, prefixsize, intermediatesize);
                            System.arraycopy(suffixdata, (int)startsuffix, data_to_insert, (prefixsize + intermediatesize), suffixsize);
                            wrp.setData(data_to_insert);
                            wrp.setRange(new Range(startprefix, endsuffix));
                        }
                       
                    }
                }
            }
            Iterator it = toberemoved.iterator();
            while (it.hasNext())
                vec.remove(it.next());
            
            toberemoved.clear();
            Collections.sort(vec);
            me.merge_for_writes_done.put(inode, Boolean.TRUE);
            //me.setWritesmerged(true);
            
                    /*} else if (prefix) {
                        data_to_insert = new Byte[(int) (to_be_merged_data_range.getStart() - start + size)];
                        System.arraycopy(prefixdata, 0, data_to_insert, 0, (int) (to_be_merged_data_range.getStart() - start));
                        System.arraycopy(by, 0, data_to_insert, (int) (to_be_merged_data_range.getStart() - start), size);
                        to_be_merged_data_range.setStart(start);
                    } else if (suffix) {
                        data_to_insert = new Byte[(int) (to_be_merged_data_range.getEnd() - end + size)];
                        System.arraycopy(by, 0, data_to_insert, 0, size);
                        System.arraycopy(suffixdata, suffixstart, data_to_insert, size, (int) (end - to_be_merged_data_range.getEnd()));
                        to_be_merged_data_range.setEnd(end);
                    } else {
                        data_to_insert = new Byte[size];
                        System.arraycopy(data_to_insert, (int) (to_be_merged_data_range.getStart() - start), by, 0, size);
                    }*/
                    //target.put(to_be_merged_data_range, data_to_insert);
             
/*
            if (flag) {
                int datasize = (int) (oldwriterange.getEnd() - oldwriterange.getStart());
                Byte[] original = (Byte[]) (wrp.getData());
                byte[] originaldata = new byte[datasize];

                //for (int i = 0; i < data.length; i++) {
                //    originaldata[i] = original[i].byteValue();
               // }
                System.arraycopy(data, 0, originaldata, (int) (to_be_merged_data_range.getStart() - oldwriterange.getStart()), size);
                Byte[] to_be_inserted = new Byte[datasize];

                for (int i = 0; i < datasize; i++) {
                    to_be_inserted[i] = Byte.valueOf(originaldata[i]);
                }
                target.put(oldwriterange, to_be_inserted);
                return;

            } else if (counter == 0) {
                target.put(to_be_merged_data_range, data);
                return;
                
            } else {

                int suffixstart = 0;
                long start = 0;
                long end = 0;
                Byte[] prefixdata = null;
                Byte[] suffixdata = null;
                boolean prefix = false;
                boolean suffix = false;

                for (int i = 0; i < counter; i++) {
                    if (markedwriteranges[i].getStart() < to_be_merged_data_range.getStart()) {
                        prefixdata = new Byte[(int) (to_be_merged_data_range.getStart() - markedwriteranges[i].getStart())];
                        prefixdata = (Byte[]) (target.get(markedwriteranges[i]));
                        start = markedwriteranges[i].getStart();
                        //newdata = new Byte[size +  newwriterange.getStart() - markedwriteranges[i].getStart()];
                        //System.arraycopy(by, 0, newdata, newwriterange.getStart() - markedwriteranges[i].getStart(), size);
                        //System.arraycopy(originaldata, 0, newdata, 0, newwriterange.getStart() - markedwriteranges[i].getStart());

                        //newwriterange.setStart(markedwriteranges[i].getStart());
                        prefix = true;


                    } else if (markedwriteranges[i].getEnd() > to_be_merged_data_range.getEnd()) {

                        suffixdata = new Byte[(int) (markedwriteranges[i].getStart() - to_be_merged_data_range.getStart())];
                        suffixdata = (Byte[]) (target.get(markedwriteranges[i]));
                        end = markedwriteranges[i].getEnd();

                        //Byte [] originaldata = (Byte [])(tmp.getWrittendata().get(markedwriteranges[i]));
                        //newdata = new Byte[size +  newwriterange.getStart() - markedwriteranges[i].getStart()];
                        //System.arraycopy(originaldata, 0, newdata, 0, newwriterange.getStart() - markedwriteranges[i].getStart());

                        //newwriterange.setStart(markedwriteranges[i].getStart());
                        ///newwriterange.setEnd(markedwriteranges[i].getEnd());
                        suffix = true;
                        suffixstart = (int) (intersectedrange[i].getEnd() - intersectedrange[i].getStart());
                    //tm.remove(markedwriteranges[i]); 
                    }
                    target.remove(markedwriteranges[i]);

                }
                Byte[] data_to_insert;

                if ((prefix) && (suffix)) {
                    data_to_insert = new Byte[(int) (to_be_merged_data_range.getStart() - start + size - to_be_merged_data_range.getEnd() + end)];
                    System.arraycopy(prefixdata, 0, data_to_insert, 0, (int) (to_be_merged_data_range.getStart() - start));
                    System.arraycopy(by, 0, data_to_insert, (int) (to_be_merged_data_range.getStart() - start), size);
                    System.arraycopy(suffixdata, suffixstart, data_to_insert, (int) (size + to_be_merged_data_range.getStart() - start), (int) (end - to_be_merged_data_range.getEnd()));
                    to_be_merged_data_range.setStart(start);
                    to_be_merged_data_range.setEnd(end);
                } else if (prefix) {
                    data_to_insert = new Byte[(int) (to_be_merged_data_range.getStart() - start + size)];
                    System.arraycopy(prefixdata, 0, data_to_insert, 0, (int) (to_be_merged_data_range.getStart() - start));
                    System.arraycopy(by, 0, data_to_insert, (int) (to_be_merged_data_range.getStart() - start), size);
                    to_be_merged_data_range.setStart(start);
                } else if (suffix) {
                    data_to_insert = new Byte[(int) (to_be_merged_data_range.getEnd() - end + size)];
                    System.arraycopy(by, 0, data_to_insert, 0, size);
                    System.arraycopy(suffixdata, suffixstart, data_to_insert, size, (int) (end - to_be_merged_data_range.getEnd()));
                    to_be_merged_data_range.setEnd(end);
                } else {
                    data_to_insert = new Byte[size];
                    System.arraycopy(data_to_insert, (int) (to_be_merged_data_range.getStart() - start), by, 0, size);
                }
                target.put(to_be_merged_data_range, data_to_insert);
                return;
            }*/
 
    }
    
    public void non_Transactional_Write(byte[] data){
        
            Vector heldlocks = new Vector();
            boolean flag = true;
            offsetlock.lock();
            int startblock = FileBlockManager.getCurrentFragmentIndexofTheFile(committedoffset.getOffsetnumber());
            int targetblock = FileBlockManager.getTargetFragmentIndexofTheFile(committedoffset.getOffsetnumber(), data.length);
            for (int i = startblock; i <= targetblock; i++) {
                BlockDataStructure block =this.inodestate.getBlockDataStructure(i);
                //if (block.getLock().writeLock().tryLock()) {
                block.getLock().writeLock().lock(); 
                    heldlocks.add(block.getLock().writeLock());
                //} else {
                //    unlockLocks(heldlocks);
                //    offsetlock.unlock();
                //    flag = false;
                //    break;
                //}
            }
            
            /*if (flag) {
                throw new PanicException("The I/O operation could not be done to contention for the file");
            }*/
            
            //else {
                try {

                    file.seek(committedoffset.getOffsetnumber());
                    file.write(data);                    
                    
                    committedoffset.setOffsetnumber(committedoffset.getOffsetnumber() +data.length);
                    
                } catch (IOException ex) {
                    Logger.getLogger(TransactionalFile.class.getName()).log(Level.SEVERE, null, ex);
                } finally {
                    unlockLocks(heldlocks);
                    offsetlock.unlock();
                }
            //}
    }
    
    public int non_Transactional_Read(byte[] b){
            int size = -1;
            Vector heldlocks = new Vector();
            boolean flag = true;
            offsetlock.lock();
            int startblock;    
            int targetblock; 
            startblock = FileBlockManager.getCurrentFragmentIndexofTheFile(committedoffset.getOffsetnumber());
            targetblock = FileBlockManager.getTargetFragmentIndexofTheFile(committedoffset.getOffsetnumber(), size);
         /*   long offset = committedoffset.getOffsetnumber();
            committedoffset.setOffsetnumber(committedoffset.getOffsetnumber() +b.length);
            if (!(committedoffset.getOffsetReaders().isEmpty())){
                Iterator it2 =  committedoffset.getOffsetReaders().iterator(); // for visible readers strategy
                while ( it2.hasNext())
                {
                    ExtendedTransaction tr = (ExtendedTransaction) it2.next();
                    tr.abort();
                }
                committedoffset.getOffsetReaders().clear();
            }
            offsetlock.unlock();*/
                
            for (int i = startblock; i <= targetblock; i++) {
                BlockDataStructure block = this.inodestate.getBlockDataStructure(i);
                //if (block.getLock().readLock().tryLock()) {
                block.getLock().readLock().lock();    
                    heldlocks.add(block.getLock().readLock());
                /*} else {
                    unlockLocks(heldlocks);
                    offsetlock.unlock();
                    flag = false;
                    break;
                }*/
            }
            /*if (flag) {
                size = -1;
            } else {*/
          
            
            
         //   try {
                //ByteBuffer buffer = ByteBuffer.wrap(b);
                //System.out.println(committedoffset.getOffsetnumber());
                //size = file.getChannel().read(buffer, offset);
                //file.seek(committedoffset.getOffsetnumber());
               // size = file.read(b);
                size = invokeNativepread(b, committedoffset.getOffsetnumber(), b.length);
                
                     
                committedoffset.setOffsetnumber(committedoffset.getOffsetnumber() +size);
                if (!(committedoffset.getOffsetReaders().isEmpty())){
                    Iterator it2 =  committedoffset.getOffsetReaders().iterator(); // for visible readers strategy
                    while ( it2.hasNext())
                    {
                        ExtendedTransaction tr = (ExtendedTransaction) it2.next();
                        tr.abort();
                }   
                committedoffset.getOffsetReaders().clear();
                }
                
      //      } catch (IOException ex) {
         //       Logger.getLogger(TransactionalFile.class.getName()).log(Level.SEVERE, null, ex);
        //        size = -1;
        //   } finally {
                unlockLocks(heldlocks);
                offsetlock.unlock();
                if (size == 0)
                    size = -1;
         //   }
           // }
            return size;
        
    }
    
    public void non_Transactional_Seek(long offset){
            offsetlock.lock();
            //try {
                //file.seek(offset);
                //synchronized(adapter){
                committedoffset.setOffsetnumber(offset);
              //  inodestate.commitedfilesize.set(offset);
            //}
            //} catch (IOException ex) {
             ///   Logger.getLogger(TransactionalFile.class.getName()).log(Level.SEVERE, null, ex);
            //} finally {
                offsetlock.unlock();
            //}
    }

    public long non_Transactional_getFilePointer(){
            long offset = -1;;
            offsetlock.lock();
         
                    
           // try {
                
                //synchronized(adapter){
                offset = committedoffset.getOffsetnumber();
            //}
         //   } catch (IOException ex) {
         //       Logger.getLogger(TransactionalFile.class.getName()).log(Level.SEVERE, null, ex);
          //  } finally {
                offsetlock.unlock();
          //  }
            return offset;
    }
    
    public int compareTo(Object arg0) {
        TransactionalFile tf = (TransactionalFile) arg0;
        if (this.inode.getNumber() < tf.inode.getNumber())
            return -1;
        else if (this.inode.getNumber() > tf.inode.getNumber())
            return 1;
        else {
            if (this.sequenceNum < tf.sequenceNum)
                return -1;
            else 
                return 1;
        }
    }

} 
