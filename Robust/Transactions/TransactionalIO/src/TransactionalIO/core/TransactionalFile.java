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

    private native int nativepwrite(byte buff[], long offset, int size, FileDescriptor fd);
    private native int nativepread(byte buff[], long offset, int size, FileDescriptor fd);
    
    {
        
        System.load("/home/navid/libkooni.so");
    }
    
    
    public RandomAccessFile file;
    private INode inode;
    private int sequenceNum = 0;
    public static int currenSeqNumforInode = 0;
 
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
                    if (!appendmode) {
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
    
    public int invokeNativepwrite(byte buff[], long offset, int size, RandomAccessFile file) {
        try {
            return nativepwrite(buff, offset, buff.length, file.getFD());
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


    public INode getInode() {
        return inode;
    }
  
    
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
               me.addFile(this, 0);
        }
        
        tmp = (TransactionLocalFileAttributes) me.getGlobaltoLocalMappings().get(this);
        if ((tmp.getOffsetdependency() == OffsetDependency.WRITE_DEPENDENCY_1) || (tmp.getOffsetdependency() == OffsetDependency.NO_ACCESS)){   
             tmp.setOffsetdependency(OffsetDependency.READ_DEPENDENCY);

             long target;
             
             lockOffset(me);
                    if (!(this.committedoffset.getOffsetReaders().contains(me))){
                        this.committedoffset.getOffsetReaders().add(me);
                    }
                    tmp.setLocaloffset(tmp.getLocaloffset() + this.committedoffset.getOffsetnumber() - tmp.getCopylocaloffset());
                    target = this.committedoffset.getOffsetnumber() - tmp.getCopylocaloffset();
             offsetlock.unlock();         
             
             
             Iterator it;

             if ((me.getWriteBuffer().get(inode)) != null)
             {   
                it = ((Vector) (me.getWriteBuffer().get(inode))).iterator();
                while (it.hasNext()){
                    WriteOperations wrp = (WriteOperations) it.next();
                    if (wrp.getTFA() == tmp && wrp.isUnknownoffset())
                        wrp.setUnknownoffset(false);
                        wrp.getRange().setStart(target + wrp.getRange().getStart());
                        wrp.getRange().setEnd(target + wrp.getRange().getEnd());
                }   
             }             
        }
        
       
        tmp.setUnknown_inital_offset_for_write(false);
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
           TransactionLocalFileAttributes tmp = null;
            if (!(me.getGlobaltoLocalMappings().containsKey(this))){
                me.addFile(this, offset);
            }
            tmp = (TransactionLocalFileAttributes) me.getGlobaltoLocalMappings().get(this);
            
            if (tmp.getOffsetdependency() == OffsetDependency.NO_ACCESS)
                tmp.setOffsetdependency(OffsetDependency.NO_DEPENDENCY);
            
            else if (tmp.getOffsetdependency() == OffsetDependency.WRITE_DEPENDENCY_1)
                tmp.setOffsetdependency(OffsetDependency.WRITE_DEPENDENCY_2);
            
            tmp.setUnknown_inital_offset_for_write(false);
            tmp.setLocaloffset(offset);
          
        }
    }

    public int read(byte[] b) {
       
        ExtendedTransaction me = Wrapper.getTransaction();
        int size = b.length;
        int result = 0;
        
        if (me == null) {  // not a transaction, but any I/O operation even though within a non-transaction is considered a single opertion transactiion 
            return non_Transactional_Read(b);
        }
   
  
        if (me.getGlobaltoLocalMappings().containsKey(this)){
    
            TransactionLocalFileAttributes tmp = (TransactionLocalFileAttributes) me.getGlobaltoLocalMappings().get(this);
            tmp.setUnknown_inital_offset_for_write(false);
            
            //make this transaction read dependent on this descriptor if it is not so already
            if ((tmp.getOffsetdependency() == OffsetDependency.WRITE_DEPENDENCY_1) || (tmp.offsetdependency == OffsetDependency.NO_ACCESS) || (tmp.getOffsetdependency() == OffsetDependency.WRITE_DEPENDENCY_2)){
                
               lockOffset(me);
                    makeDependentonOffet(me, tmp);
               offsetlock.unlock();
            }

            // make all write operations by any transaction to this offset absolute and those transaction read 
            //dependent on the offset
            Iterator it;
            if (me.getWriteBuffer().get(inode) != null)
            {
                it = ((Vector) (me.getWriteBuffer().get(inode))).iterator();
                while (it.hasNext()){
                 
                      WriteOperations wrp = (WriteOperations) it.next();
                      if (wrp.isUnknownoffset()){
                        wrp.setUnknownoffset(false);
                        
                        wrp.getOwnerTF().lockOffset(me);
                            makeWriteAbsolute(me, wrp);
                        wrp.getOwnerTF().offsetlock.unlock();     
                        
                        markAccessedBlocks(me, (int)wrp.getRange().getStart(), (int)(wrp.getRange().getEnd() - wrp.getRange().getStart()), BlockAccessModesEnum.WRITE);

                      }
                }
            }
            
        /*    if (!(me.isWritesmerged())){
                mergeWrittenData();
            }*/

            
            // merge the write by this transation to this descriptor before start reading from it 
            if ((Boolean)me.merge_for_writes_done.get(inode) == Boolean.FALSE){
                 mergeWrittenData(me);
            }
               
            
            
            // find the intersections of the data o be read with the
            // transactions local buffer if any at all
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
            
            int number_of_intersections = 0;
            boolean data_in_local_buffer = false;
            
                    
            it = writebuffer.iterator();
            while (it.hasNext()) {
                
                WriteOperations wrp = (WriteOperations) it.next();
                writerange = wrp.getRange();
                if (writerange.includes(readrange)) {
                    markedwriteop[number_of_intersections] = wrp;
                    data_in_local_buffer = true;
                    break;
                }

                if (writerange.hasIntersection(readrange)) {
                    intersectedrange[number_of_intersections] = readrange.intersection(writerange);
                    markedwriteop[number_of_intersections] = wrp;
                   
                    number_of_intersections++;
                }
            }


        
            if (data_in_local_buffer) {
                // the to be read offset is written previously by the transaction itself
                // so the read is done from localbuffer
                result = readFromBuffer(b, tmp, markedwriteop[number_of_intersections],writerange);    
                return result;
            }
            
            else{
                
                if (number_of_intersections == 0) {
                    // the whole range to be read should be donefrom the file itself, 
                    result = readFromFile(me, b, tmp);
                }
                
                
                else {
                      //some of the parts to read are in local buffer some should be done
                     //from the file 
                    for (int i = 0; i < number_of_intersections; i++) {

                        
                        Byte[] data = markedwriteop[i].getData();
                        byte[] copydata = new byte[data.length];

                        for (int j = 0; j < data.length; j++) {
                            copydata[j] = data[j].byteValue();
                        }
                        System.arraycopy(copydata, (int) (intersectedrange[i].getStart() - markedwriteop[i].getRange().getStart()), b, (int) (intersectedrange[i].getStart() - readrange.getStart()), (int) (Math.min(intersectedrange[i].getEnd(), readrange.getEnd()) - intersectedrange[i].getStart()));
                        result += Math.min(intersectedrange[i].getEnd(), readrange.getEnd()) - intersectedrange[i].getStart();
                    }

                    Range[] non_intersected_ranges = readrange.minus(intersectedrange, number_of_intersections);
                    Vector occupiedblocks = new Vector();
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
                            block.getLock().readLock().lock();
                                    if (!(block.getReaders().contains(me))){
                                        block.getReaders().add(me);
                                     }
                                     me.getHeldblocklocks().add(block.getLock().readLock());
                                    break;
                        }
                       if (me.getStatus() == Status.ABORTED) {
                            throw new AbortedException();
                        }
                    }



                    for (int i = 0; i < non_intersected_ranges.length; i++) {
                        try {
                            //invokeNativepread(b, non_intersected_ranges[i].getStart(), size);
                            file.seek(non_intersected_ranges[i].getStart());
                            int tmpsize = file.read(b, (int) (non_intersected_ranges[i].getStart() - readrange.getStart()), (int) (non_intersected_ranges[i].getEnd() - non_intersected_ranges[i].getStart()));
                            result += tmpsize;
                        } catch (IOException ex) {

                            Logger.getLogger(TransactionalFile.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                    me.unlockAllLocks();
                    tmp.setLocaloffset(tmp.getLocaloffset() + result);
                }
              
                return result;
            }

        } else {           // add to the readers list  
            me.addFile(this, 0);
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
        
        if (me.getGlobaltoLocalMappings().containsKey(this)) 
        {
 
            Byte[] by = new Byte[size];
            for (int i = 0; i < size; i++) {
                by[i] = Byte.valueOf(data[i]);
            }
            TransactionLocalFileAttributes tmp = ((TransactionLocalFileAttributes) (me.getGlobaltoLocalMappings().get(this)));

            Vector dummy;
            if (((Vector)(me.getWriteBuffer().get(this.inode))) != null){
                dummy = new Vector((Vector)(me.getWriteBuffer().get(this.inode)));
            }
            else 
                dummy = new Vector();
      
            dummy.add(new WriteOperations(by, new Range(tmp.getLocaloffset(), tmp.getLocaloffset() + by.length), tmp.isUnknown_inital_offset_for_write(), this, tmp));
            me.getWriteBuffer().put(this.inode, dummy);
            
            long loffset = tmp.getLocaloffset();
             
             
            tmp.setLocaloffset(tmp.getLocaloffset() + by.length);
            
            me.merge_for_writes_done.put(inode, Boolean.FALSE);
            if (!(tmp.isUnknown_inital_offset_for_write())){
                markAccessedBlocks(me, loffset, size, BlockAccessModesEnum.WRITE);
            }
                     
            if (tmp.getOffsetdependency() == OffsetDependency.NO_ACCESS)
                tmp.offsetdependency = OffsetDependency.WRITE_DEPENDENCY_1;

        } else {
            me.addFile(this, 0);      
            write(data);
        }
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

    }
    

    // reads the data directly from file, 
    private int readFromFile(ExtendedTransaction me, byte[] readdata, TransactionLocalFileAttributes tmp) {
        int st = FileBlockManager.getCurrentFragmentIndexofTheFile(tmp.getLocaloffset());
        int end = FileBlockManager.getTargetFragmentIndexofTheFile(tmp.getLocaloffset(), readdata.length);
        
        BlockDataStructure block = null;
        boolean locked = false;
        for (int k = st; k <= end; k++) {
            lockBlock(me, st, k);
        }
        if (me.getStatus() == Status.ABORTED) {
             for (int i=st; i<=end; i++){
                    block = this.inodestate.getBlockDataStructure(Integer.valueOf(i));
                    me.getHeldblocklocks().add(block.getLock().readLock());
             }
                throw new AbortedException();
        }
        int size = -1;
        size = invokeNativepread(readdata, tmp.getLocaloffset(), readdata.length);
        tmp.setLocaloffset(tmp.getLocaloffset() + size);
        if (size == 0)
            size = -1;
        if (me.getStatus() == Status.ABORTED) {
                for (int i=st; i<=end; i++){
                    block = this.inodestate.getBlockDataStructure(Integer.valueOf(i));
                    me.getHeldblocklocks().add(block.getLock().readLock());
                }
                throw new AbortedException();
        }
        for (int p = st; p <= end; p++) {
                    block = this.inodestate.getBlockDataStructure(Integer.valueOf(p));
                    block.getLock().readLock().unlock();
        }
        return size;
    }

    private int readFromBuffer(byte[] readdata, TransactionLocalFileAttributes tmp, WriteOperations wrp, Range writerange) {
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
    

    public void setInode(INode inode) {
        this.inode = inode;
    }
    
    public void lockOffset(ExtendedTransaction me){
            boolean locked = false;
            while (me.getStatus() == Status.ACTIVE) {                        //locking the offset
                    offsetlock.lock();
               locked = true;
               break;
          
            }

            if (me.getStatus() != Status.ACTIVE){
               if (locked)
                    me.getHeldoffsetlocks().add(offsetlock);
                throw new AbortedException();
            }
    }
    
    public void mergeWrittenData(ExtendedTransaction me){
            
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
                             endsuffix = wrp2.getRange().getEnd();
                        }

                        else if (wrp.getRange().getEnd() > wrp2.getRange().getEnd()) {
                             suffixdata = new Byte[(int) (wrp.getRange().getEnd() - intersectedrange.getEnd())];
                             suffixdata = (Byte[]) (wrp.getData());
                             startsuffix = intersectedrange.getEnd() - wrp.getRange().getStart();
                             suffixsize = (int)(wrp.getRange().getEnd() - intersectedrange.getEnd());
                             endsuffix = wrp.getRange().getEnd();
                             suffix = true;

                        }
                        

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
      
    }
    
    public void non_Transactional_Write(byte[] data){
        
            Vector heldlocks = new Vector();
            boolean flag = true;
            offsetlock.lock();
            int startblock = FileBlockManager.getCurrentFragmentIndexofTheFile(committedoffset.getOffsetnumber());
            int targetblock = FileBlockManager.getTargetFragmentIndexofTheFile(committedoffset.getOffsetnumber(), data.length);
            for (int i = startblock; i <= targetblock; i++) {
                BlockDataStructure block =this.inodestate.getBlockDataStructure(i);
                block.getLock().writeLock().lock(); 
                    heldlocks.add(block.getLock().writeLock());
            }

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
       
            for (int i = startblock; i <= targetblock; i++) {
                BlockDataStructure block = this.inodestate.getBlockDataStructure(i);
                block.getLock().readLock().lock();    
                    heldlocks.add(block.getLock().readLock());
               
            }
            
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

        
            unlockLocks(heldlocks);
            offsetlock.unlock();
            if (size == 0)
                size = -1;
            return size;
    }
    
    public void non_Transactional_Seek(long offset){
            offsetlock.lock();
                committedoffset.setOffsetnumber(offset);
            offsetlock.unlock();
    }

    public long non_Transactional_getFilePointer(){
            long offset = -1;;
            offsetlock.lock();
                offset = committedoffset.getOffsetnumber();
            offsetlock.unlock();
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
    
    public void makeDependentonOffet(ExtendedTransaction me, TransactionLocalFileAttributes tmp){
        
            if (tmp.getOffsetdependency() != OffsetDependency.WRITE_DEPENDENCY_2){     
                        tmp.setLocaloffset(tmp.getLocaloffset() + this.committedoffset.getOffsetnumber() - tmp.getCopylocaloffset()); 
            }
                    
            tmp.setOffsetdependency(OffsetDependency.READ_DEPENDENCY);  
            if (!(this.committedoffset.getOffsetReaders().contains(me))){
                this.committedoffset.getOffsetReaders().add(me);

            }
    }
    
    public void makeWriteAbsolute(ExtendedTransaction me, WriteOperations wrp){
            wrp.getRange().setStart(wrp.getOwnerTF().committedoffset.getOffsetnumber() - wrp.getTFA().getCopylocaloffset() + wrp.getRange().getStart());
            wrp.getRange().setEnd(wrp.getOwnerTF().committedoffset.getOffsetnumber() - wrp.getTFA().getCopylocaloffset() + wrp.getRange().getEnd());
            if ((wrp.getTFA().getOffsetdependency() == OffsetDependency.WRITE_DEPENDENCY_1)  || (wrp.getTFA().offsetdependency == OffsetDependency.NO_ACCESS) || (wrp.getTFA().getOffsetdependency() == OffsetDependency.WRITE_DEPENDENCY_2)){
                wrp.getTFA().setOffsetdependency(OffsetDependency.READ_DEPENDENCY);
                wrp.getTFA().setUnknown_inital_offset_for_write(false);
                if (!(wrp.getOwnerTF().committedoffset.getOffsetReaders().contains(me)))
                    wrp.getOwnerTF().committedoffset.getOffsetReaders().add(me);
                wrp.getTFA().setLocaloffset(wrp.getTFA().getLocaloffset() + wrp.getOwnerTF().committedoffset.getOffsetnumber() - wrp.getTFA().getCopylocaloffset());
            }
        
    }
    
    
    private void lockBlock(ExtendedTransaction me, int st, int k){
        BlockDataStructure block;
        boolean locked = false;
            while (me.getStatus() == Status.ACTIVE) {
                block = this.inodestate.getBlockDataStructure(Integer.valueOf(k));
      
                block.getLock().readLock().lock();
                        if (!(block.getReaders().contains(me))){
                            block.getReaders().add(me);
                        }
                        locked = true;
                     break;
            }
            
            if (me.getStatus() == Status.ABORTED) {
                int m;
                if (locked){
                    m = k+1;
                }
                else 
                    m = k;
                for (int i=st; i<m; i++){
                    block = this.inodestate.getBlockDataStructure(Integer.valueOf(k));
                    me.getHeldblocklocks().add(block.getLock().readLock());
                }
             
                locked = false;
                
                throw new AbortedException();
            }
    }

        // for block versioning mechanism
            /*if (!(validateBlocksVersions(startblock, targetblock))) { ////check to see if version are still valid 
            
                    throw new AbortedException();
            
            }*/
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

} 
