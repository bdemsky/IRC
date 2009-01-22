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
import java.util.TreeMap;
import java.util.Vector;
import java.util.concurrent.locks.Lock;

import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author navid
 */
public class TransactionalFile implements Comparable {

    private native int nativepread(byte buff[], long offset, int size, FileDescriptor fd);

    private native int nativepwrite(byte buff[], long offset, int size, FileDescriptor fd);
    

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
    
   
    private GlobalINodeState inodestate;
    Lock[] locks;

    public TransactionalFile(File f, String mode) {
        
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
        inode = TransactionalFileWrapperFactory.getINodefromFileName(f.getAbsolutePath());
        inodestate = TransactionalFileWrapperFactory.createTransactionalFile(inode, f.getAbsolutePath(), mode);


        sequenceNum = inodestate.seqNum;
        inodestate.seqNum++;

        if (mode.equals("rw")) {
            writemode = true;
        } else if (mode.equals("a")) {
            appendmode = true;
        }

        if (inodestate != null) {
            synchronized (inodestate) {
                committedoffset = new GlobalOffset(0);
                
            }
        }
    }
    
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
                committedoffset = new GlobalOffset(0);
               
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
    
    
    public long length(){
        ExtendedTransaction me = Wrapper.getTransaction();

        if (me == null) {
            return non_Transactional_getFilePointer();
        }

        if (!(me.getGlobaltoLocalMappings().containsKey(this))) {
            me.addFile(this, 0);
        }
        
        TransactionLocalFileAttributes tmp = (TransactionLocalFileAttributes) me.getGlobaltoLocalMappings().get(this);
        
        lockLength(me);

            if (!(this.inodestate.commitedfilesize.getLengthReaders().contains(me))) {
                this.inodestate.commitedfilesize.getLengthReaders().add(me);
            }
        
           tmp.setLocalsize(this.inodestate.commitedfilesize.getLength());
           tmp.lenght_read = true;
        
        this.inodestate.commitedfilesize.lengthlock.unlock();

        return tmp.getLocalsize();
    }
    public long getFilePointer() {

        ExtendedTransaction me = Wrapper.getTransaction();

        if (me == null) {
            return non_Transactional_getFilePointer();
        }

        if (!(me.getGlobaltoLocalMappings().containsKey(this))) {
            me.addFile(this, 0);
        }

        TransactionLocalFileAttributes tmp = (TransactionLocalFileAttributes) me.getGlobaltoLocalMappings().get(this);
        if ((tmp.getOffsetdependency() == OffsetDependency.WRITE_DEPENDENCY_1) || (tmp.getOffsetdependency() == OffsetDependency.NO_ACCESS)) {
            tmp.setOffsetdependency(OffsetDependency.READ_DEPENDENCY);

            long target;
            lockOffset(me);

            if (!(this.committedoffset.getOffsetReaders().contains(me))) {
                this.committedoffset.getOffsetReaders().add(me);
            }

            tmp.setLocaloffset(tmp.getLocaloffset() + this.committedoffset.getOffsetnumber() - tmp.getCopylocaloffset());
            target = this.committedoffset.getOffsetnumber() - tmp.getCopylocaloffset();

            offsetlock.unlock();

            Iterator it;

            if ((me.getWriteBuffer().get(inode)) != null) {

                it = ((Vector) (me.getWriteBuffer().get(inode))).iterator();
                while (it.hasNext()) {
                    WriteOperations wrp = (WriteOperations) it.next();
                    if (wrp.getBelongingto() == tmp && wrp.isUnknownoffset()) {
                        wrp.setUnknownoffset(false);
                    }
                    wrp.getRange().setStart(target + wrp.getRange().getStart());
                    wrp.getRange().setEnd(target + wrp.getRange().getEnd());
                }
            }

        }


        tmp.setUnknown_inital_offset_for_write(false);
        return tmp.getLocaloffset();
    }

    public void seek(long offset) {

        ExtendedTransaction me = Wrapper.getTransaction();

        if (me == null) {
            non_Transactional_Seek(offset);
            return;
        }

        if (!(me.getGlobaltoLocalMappings().containsKey(this))) {
            me.addFile(this, offset);
        }

        TransactionLocalFileAttributes tmp = (TransactionLocalFileAttributes) me.getGlobaltoLocalMappings().get(this);

        if (tmp.getOffsetdependency() == OffsetDependency.NO_ACCESS) {
            tmp.setOffsetdependency(OffsetDependency.NO_DEPENDENCY);
        } else if (tmp.getOffsetdependency() == OffsetDependency.WRITE_DEPENDENCY_1) {
            tmp.setOffsetdependency(OffsetDependency.WRITE_DEPENDENCY_2);
        }
        tmp.setUnknown_inital_offset_for_write(false);
        tmp.setLocaloffset(offset);

    }

    public final int readInt(){
        byte[] data = new byte[4];
        read(data);
        int result = (data[0] << 24) | (data[1] << 16) + (data[2] << 8) + data[3];
        return result;
    }
    
    public final long readLong(){
        byte[] data = new byte[8];
        read(data);
        long result = ((long)data[0] << 56) + ((long)data[1] << 48) + ((long)data[2] << 40) + ((long)data[3] << 32) + ((long)data[4] << 24) + ((long)data[5] << 16)+ ((long)data[6] << 8) + data[7];
        return result;
    }
    
    public final void writeInt(int value){
        try {
            byte[] result = new byte[4];
            result[0] = (byte) (value >> 24);
            result[1] = (byte) (value >> 16);
            result[2] = (byte) (value >> 8);
            result[3] = (byte) (value);
            write(result);
        } catch (IOException ex) {
            Logger.getLogger(TransactionalFile.class.getName()).log(Level.SEVERE, null, ex);
        }

    }
    
     public final void writeLong(long value){
        try {
            byte[] result = new byte[4];
            result[0] = (byte)(value >> 56);
            result[1] = (byte)(value >> 48);
            result[2] = (byte)(value >> 40);
            result[3] = (byte)(value >> 32);
            result[4] = (byte)(value >> 24);
            result[5] = (byte)(value >> 16);
            result[6] = (byte)(value >> 8);
            result[7] = (byte)(value);
            write(result);
        } catch (IOException ex) {
            Logger.getLogger(TransactionalFile.class.getName()).log(Level.SEVERE, null, ex);
        }

    }
    
    public int read(byte[] b) {


        ExtendedTransaction me = Wrapper.getTransaction();
        int size = b.length;
        int result = 0;
        if (me == null) {  // not a transaction, but any I/O operation even though within a non-transaction is considered a single opertion transactiion 

            return non_Transactional_Read(b);
        }

        if (!(me.getGlobaltoLocalMappings().containsKey(this))) { // if this is the first time the file is accessed by the transcation
            me.addFile(this, 0);
        }


        TransactionLocalFileAttributes tmp = (TransactionLocalFileAttributes) me.getGlobaltoLocalMappings().get(this);
        tmp.setUnknown_inital_offset_for_write(false);

        OffsetDependency dep = tmp.getOffsetdependency();
        if ((dep == OffsetDependency.WRITE_DEPENDENCY_1) ||
                (dep == OffsetDependency.NO_ACCESS) ||
                (dep == OffsetDependency.WRITE_DEPENDENCY_2)) {
            tmp.setOffsetdependency(OffsetDependency.READ_DEPENDENCY);
            lockOffset(me);

            if (dep != OffsetDependency.WRITE_DEPENDENCY_2) {
                tmp.setLocaloffset(tmp.getLocaloffset() + this.committedoffset.getOffsetnumber() - tmp.getCopylocaloffset());
            }

            if (!(this.committedoffset.getOffsetReaders().contains(me))) {
                this.committedoffset.getOffsetReaders().add(me);

            }

            offsetlock.unlock();
        }

        if (me.getWriteBuffer().get(inode) != null) {
            makeWritestDependent(me);
        }
        if ((Boolean) me.merge_for_writes_done.get(inode) == Boolean.FALSE) {
            mergeWrittenData(me);
        }

        long loffset = tmp.getLocaloffset();
        markAccessedBlocks(me, loffset, size, BlockAccessModesEnum.READ);


        Vector writebuffer = null;
        if ((me.getWriteBuffer().get(this.inode)) != null) {
            writebuffer = (Vector) (me.getWriteBuffer().get(this.inode));

        } else {
            result = readFromFile(me, b, tmp);
            return result;
        //writebuffer = new Vector();
        //me.getWriteBuffer().put(this.inode, writebuffer);

        }
        Range readrange = new Range(loffset, loffset + size);
        Range writerange = null;
        Range[] intersectedrange = new Range[writebuffer.size()];
        WriteOperations[] markedwriteop = new WriteOperations[writebuffer.size()];

        int counter = 0;
        boolean in_local_buffer = false;


        Iterator it = writebuffer.iterator();
        while (it.hasNext()) {

            WriteOperations wrp = (WriteOperations) it.next();
            writerange = wrp.getRange();
            if (writerange.includes(readrange)) {
                markedwriteop[counter] = wrp;
                in_local_buffer = true;
                break;
            }

            if (writerange.hasIntersection(readrange)) {
                intersectedrange[counter] = readrange.intersection(writerange);
                markedwriteop[counter] = wrp;

                counter++;
            }
        }

        if (in_local_buffer) { // the read one from local buffer

            result = readFromBuffer(b, tmp, markedwriteop[counter], writerange);
            return result;

        } else {

            if (counter == 0) { // all the read straight from file

                result = readFromFile(me, b, tmp);
            } else {    // some parts from file others from buffer

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
                for (int i = 0; i < non_intersected_ranges.length; i++) {
                    int st = FileBlockManager.getCurrentFragmentIndexofTheFile(non_intersected_ranges[i].getStart());
                    int en = FileBlockManager.getCurrentFragmentIndexofTheFile(non_intersected_ranges[i].getEnd());
                    for (int j = st; j <= en; j++) {
                        if (!(occupiedblocks.contains(Integer.valueOf(j)))) {
                            occupiedblocks.add(Integer.valueOf(j));
                        }
                    }
                }

                //  lockOffset(me);

                me.getHeldoffsetlocks().add(offsetlock);
                BlockDataStructure block;
                int k;
                for (k = 0; k < occupiedblocks.size() && me.getStatus() == Status.ACTIVE; k++) {   // locking the block locks
                        block = this.inodestate.getBlockDataStructure((Integer) (occupiedblocks.get(k)));//(BlockDataStructure) tmp.adapter.lockmap.get(Integer.valueOf(k)));

                        block.getLock().readLock().lock();
                        if (!(block.getReaders().contains(me))) {
                            block.getReaders().add(me);
                        }

               }
               if (k<occupiedblocks.size()){ 
                    for (int i = 0; i <k; i++) {
                            block = this.inodestate.getBlockDataStructure((Integer) (occupiedblocks.get(k)));
                            me.getHeldblocklocks().add(block.getLock().readLock());
                    }    
                    throw new AbortedException();
               }
                

                for (int i = 0; i < non_intersected_ranges.length; i++) {

                    int sizetoread = (int) (non_intersected_ranges[i].getEnd() - non_intersected_ranges[i].getStart());
                    byte[] tmpdt = new byte[(int) (non_intersected_ranges[i].getEnd() - non_intersected_ranges[i].getStart())];
                    int tmpsize = invokeNativepread(tmpdt, non_intersected_ranges[i].getStart(), sizetoread);
                    System.arraycopy(tmpdt, 0, b, (int) (non_intersected_ranges[i].getStart() - readrange.getStart()), sizetoread);
                    //file.seek(non_intersected_ranges[i].getStart());
                    //int tmpsize = file.read(b, (int) (non_intersected_ranges[i].getStart() - readrange.getStart()), (int) (non_intersected_ranges[i].getEnd() - non_intersected_ranges[i].getStart()));
                    result += tmpsize;

                }

                if (me.getStatus() == Status.ABORTED) {
                    for (k = 0; k < occupiedblocks.size(); k++) {
                        block = this.inodestate.getBlockDataStructure((Integer) (occupiedblocks.get(k)));
                        me.getHeldblocklocks().add(block.getLock().readLock());
                    }
                    throw new AbortedException();
                }
                for (k = 0; k < occupiedblocks.size(); k++) {
                    block = this.inodestate.getBlockDataStructure((Integer) (occupiedblocks.get(k)));
                    block.getLock().readLock().unlock();
                }
                // offsetlock.unlock();
                tmp.setLocaloffset(tmp.getLocaloffset() + result);
            }

            return result;
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

        if (!(me.getGlobaltoLocalMappings().containsKey(this))) {
            me.addFile(this, 0);
        }

        //  if (me.getGlobaltoLocalMappings().containsKey(this)) // 
        //   {


        Byte[] by = new Byte[size];
        for (int i = 0; i < size; i++) {
            by[i] = Byte.valueOf(data[i]);
        }
        TransactionLocalFileAttributes tmp = ((TransactionLocalFileAttributes) (me.getGlobaltoLocalMappings().get(this)));

        Vector dummy;
        if (((Vector) (me.getWriteBuffer().get(this.inode))) != null) {
            dummy = new Vector((Vector) (me.getWriteBuffer().get(this.inode)));
        } else {
            dummy = new Vector();
        }
        dummy.add(new WriteOperations(by, new Range(tmp.getLocaloffset(), tmp.getLocaloffset() + by.length), tmp.isUnknown_inital_offset_for_write(), this, tmp));
        me.getWriteBuffer().put(this.inode, dummy);

        long loffset = tmp.getLocaloffset();


        tmp.setLocaloffset(tmp.getLocaloffset() + by.length);
        
        if (tmp.getLocaloffset() > tmp.getLocalsize())
            tmp.setLocalsize(tmp.getLocaloffset());

        me.merge_for_writes_done.put(inode, Boolean.FALSE);

        if (!(tmp.isUnknown_inital_offset_for_write())) {
            markAccessedBlocks(me, loffset, size, BlockAccessModesEnum.WRITE);

        }
        if (tmp.getOffsetdependency() == OffsetDependency.NO_ACCESS) {
            tmp.offsetdependency = OffsetDependency.WRITE_DEPENDENCY_1;
        }
    }

    private void markAccessedBlocks(ExtendedTransaction me, long loffset, int size, BlockAccessModesEnum mode) {


        TreeMap map;

        if (me.getAccessedBlocks().get(this.getInode()) != null) {
            map = (TreeMap) me.getAccessedBlocks().get(this.getInode());
        } else {
            map = new TreeMap();
            me.getAccessedBlocks().put(this.inode, map);
        }
        int startblock = (int) ((loffset / Defaults.FILEFRAGMENTSIZE));//FileBlockManager.getCurrentFragmentIndexofTheFile(loffset);
        int targetblock = (int) (((size + loffset) / Defaults.FILEFRAGMENTSIZE));//FileBlockManager.getTargetFragmentIndexofTheFile(loffset, size);
        for (int i = startblock; i <= targetblock; i++) {
            if (map.get(Integer.valueOf(i)) == null) {
                  map.put(Integer.valueOf(i), mode);
            } else if (map.get(Integer.valueOf(i)) != mode) {
                  map.put(Integer.valueOf(i), BlockAccessModesEnum.READ_WRITE);
            }
        }
    }

    private int readFromFile(ExtendedTransaction me, byte[] readdata, TransactionLocalFileAttributes tmp) {
//Inline these method calls for performance...
//int st = FileBlockManager.getCurrentFragmentIndexofTheFile(tmp.getLocaloffset());//(int) ((tmp.getLocaloffset() / Defaults.FILEFRAGMENTSIZE));//
//int end = FileBlockManager.getTargetFragmentIndexofTheFile(tmp.getLocaloffset(), readdata.length);//(int) (((tmp.getLocaloffset() + readdata.length) / Defaults.FILEFRAGMENTSIZE));
        int st = (int) ((tmp.getLocaloffset() / Defaults.FILEFRAGMENTSIZE));
        int end =(int) (((tmp.getLocaloffset() + readdata.length) / Defaults.FILEFRAGMENTSIZE));

        BlockDataStructure block = null;
        
        Lock[] locks = new Lock[end -st +1];
        
        int k;
        //int cou = st;

        for (k = st; k <= end /*&& me.getStatus() == Status.ACTIVE*/; k++) {
            block = this.inodestate.getBlockDataStructure(Integer.valueOf(k));
            block.getLock().readLock().lock();
            
          //  locks[k-st] = block.getLock().readLock();
            if (!(block.getReaders().contains(me))) {
                block.getReaders().add(me);
            }
        }

        //Optimization here...not actually needed...may be worth checking
        //whether this improves performance
        if (k<=end) {
            //We aborted here if k is less than or equal to end
            me.blockcount = k - st;
            for (int i = st; i < k; i++) {
               // block = this.inodestate.getBlockDataStructure(Integer.valueOf(i));
               me.getHeldblocklocks().add(block.getLock().readLock());
                //me.toholdblocklocks[i-st] = this.inodestate.getBlockDataStructure(Integer.valueOf(i)).getLock().readLock();
               // me.getHeldblocklocks().add(locks[i-st]);
            }
            throw new AbortedException();
        }

        //Do the read
        int size = -1;
        size = invokeNativepread(readdata, tmp.getLocaloffset(), readdata.length);
        tmp.setLocaloffset(tmp.getLocaloffset() + size);
        
        //Handle EOF
        if (size == 0) {
            size = -1;
        }

        //Needed to make sure that transaction only sees consistent data
        if (me.getStatus() == Status.ABORTED) {
            me.blockcount = end - st + 1;
            for (int i = st; i <= end; i++) {
                block = this.inodestate.getBlockDataStructure(Integer.valueOf(i));
               // me.toholdblocklocks[i-st] = this.inodestate.getBlockDataStructure(Integer.valueOf(i)).getLock().readLock();
                me.getHeldblocklocks().add(block.getLock().readLock());
              //  me.getHeldblocklocks().add(locks[i-st]);
            }
            throw new AbortedException();
        }

        //unlock the locks
        for (k = st; k <= end; k++) {
            block = this.inodestate.getBlockDataStructure(Integer.valueOf(k));
            block.getLock().readLock().unlock();
            //locks[k-st].unlock();
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

    public void lockOffset(ExtendedTransaction me) {
        boolean locked = false;
        if (me.getStatus() == Status.ACTIVE) {                        //locking the offset

            offsetlock.lock();
            locked = true;
        }

        if (me.getStatus() != Status.ACTIVE) {
            if (locked) {
                me.getHeldoffsetlocks().add(offsetlock);
            }
            throw new AbortedException();
        }

    }

     public void lockLength(ExtendedTransaction me) {
        boolean locked = false;
        if (me.getStatus() == Status.ACTIVE) {                        //locking the offset

            this.inodestate.commitedfilesize.lengthlock.lock();
            locked = true;
        }

        if (me.getStatus() != Status.ACTIVE) {
            if (locked) {
                me.getHeldlengthlocks().add(this.inodestate.commitedfilesize.lengthlock);
            }
            throw new AbortedException();
        }

    }
    
    public void mergeWrittenData(ExtendedTransaction me/*TreeMap target, byte[] data, Range to_be_merged_data_range*/) {

        boolean flag = false;
        Vector vec = (Vector) me.getWriteBuffer().get(this.inode);
        Range intersectedrange = new Range(0, 0);
        Iterator it1 = vec.iterator();
        WriteOperations wrp;
        WriteOperations wrp2;
        Vector toberemoved = new Vector();
        while (it1.hasNext()) {
            wrp = (WriteOperations) (it1.next());

            if (toberemoved.contains(wrp)) {
                continue;
            }

            Iterator it2 = vec.listIterator();
            while (it2.hasNext()) {
                flag = false;
                wrp2 = (WriteOperations) (it2.next());

                if ((wrp2 == wrp) || toberemoved.contains(wrp2)) {
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
                if (flag) {


                    if (wrp.getRange().getStart() < wrp2.getRange().getStart()) {
                        prefixdata = new Byte[(int) (wrp2.getRange().getStart() - wrp.getRange().getStart())];
                        prefixdata = (Byte[]) (wrp.getData());
                        startprefix = wrp.getRange().getStart();
                        prefixsize = (int) (intersectedrange.getStart() - startprefix);
                        intermediatesize = (int) (intersectedrange.getEnd() - intersectedrange.getStart());
                        prefix = true;
                    } else if (wrp2.getRange().getStart() <= wrp.getRange().getStart()) {
                        prefixdata = new Byte[(int) (wrp.getRange().getStart() - wrp2.getRange().getStart())];
                        prefixdata = (Byte[]) (wrp2.getData());
                        startprefix = wrp2.getRange().getStart();
                        prefixsize = (int) (intersectedrange.getStart() - startprefix);
                        intermediatesize = (int) (intersectedrange.getEnd() - intersectedrange.getStart());
                        prefix = true;
                    }

                    if (wrp2.getRange().getEnd() >= wrp.getRange().getEnd()) {

                        suffixdata = new Byte[(int) (wrp2.getRange().getEnd() - intersectedrange.getEnd())];
                        suffixdata = (Byte[]) (wrp2.getData());
                        startsuffix = intersectedrange.getEnd() - wrp2.getRange().getStart();
                        suffixsize = (int) (wrp2.getRange().getEnd() - intersectedrange.getEnd());
                        suffix = true;
                        endsuffix = wrp2.getRange().getEnd();
                    } else if (wrp.getRange().getEnd() > wrp2.getRange().getEnd()) {
                        suffixdata = new Byte[(int) (wrp.getRange().getEnd() - intersectedrange.getEnd())];
                        suffixdata = (Byte[]) (wrp.getData());
                        startsuffix = intersectedrange.getEnd() - wrp.getRange().getStart();
                        suffixsize = (int) (wrp.getRange().getEnd() - intersectedrange.getEnd());
                        endsuffix = wrp.getRange().getEnd();
                        suffix = true;

                    }

                    Byte[] data_to_insert;

                    if ((prefix) && (suffix)) {
                        data_to_insert = new Byte[(int) (endsuffix - startprefix)];
                        System.arraycopy(prefixdata, 0, data_to_insert, 0, prefixsize);
                        System.arraycopy(wrp2.getData(), (int) (intersectedrange.getStart() - wrp2.getRange().getStart()), data_to_insert, prefixsize, intermediatesize);
                        System.arraycopy(suffixdata, (int) startsuffix, data_to_insert, (prefixsize + intermediatesize), suffixsize);
                        wrp.setData(data_to_insert);
                        wrp.setRange(new Range(startprefix, endsuffix));
                    }

                }
            }
        }
        Iterator it = toberemoved.iterator();
        while (it.hasNext()) {
            vec.remove(it.next());
        }
        toberemoved.clear();
        Collections.sort(vec);
        me.merge_for_writes_done.put(inode, Boolean.TRUE);

    }

    public void non_Transactional_Write(byte[] data) {

        Vector heldlocks = new Vector();
        offsetlock.lock();
        int startblock = FileBlockManager.getCurrentFragmentIndexofTheFile(committedoffset.getOffsetnumber());
        int targetblock = FileBlockManager.getTargetFragmentIndexofTheFile(committedoffset.getOffsetnumber(), data.length);
        
        
        WriteLock[] blocksar;
        blocksar = new WriteLock[targetblock-startblock+1];
        for (int i = startblock; i <= targetblock; i++) {
            BlockDataStructure block = this.inodestate.getBlockDataStructure(i);
            block.getLock().writeLock().lock();
            blocksar[i-startblock] = block.getLock().writeLock();
            //heldlocks.add(block.getLock().writeLock());
        }

        try {
            ExtendedTransaction.invokeNativepwrite(data, committedoffset.getOffsetnumber(), data.length, file);
            //file.seek(committedoffset.getOffsetnumber());
            //file.write(data);
            committedoffset.setOffsetnumber(committedoffset.getOffsetnumber() + data.length);

        } finally {
         //   unlockLocks(heldlocks);
            for (int i = startblock; i <= targetblock; i++) {
                blocksar[i-startblock].unlock();
            }
            offsetlock.unlock();
        }
    }

    public int non_Transactional_Read(byte[] b) {
        int size = -1;

        
        offsetlock.lock();
        
        int startblock;
        int targetblock;
        startblock = FileBlockManager.getCurrentFragmentIndexofTheFile(committedoffset.getOffsetnumber());
        targetblock = FileBlockManager.getTargetFragmentIndexofTheFile(committedoffset.getOffsetnumber(), size);

        ReadLock[] blocksar;
        blocksar = new ReadLock[targetblock-startblock+1];
        
        for (int i = startblock; i <= targetblock; i++) {
            BlockDataStructure block = this.inodestate.getBlockDataStructure(i);
            block.getLock().readLock().lock();
            blocksar[i-startblock] = block.getLock().readLock();
        }

        size = invokeNativepread(b, committedoffset.getOffsetnumber(), b.length);
        committedoffset.setOffsetnumber(committedoffset.getOffsetnumber() + size);
        if (!(committedoffset.getOffsetReaders().isEmpty())) {
            Iterator it2 = committedoffset.getOffsetReaders().iterator(); // for visible readers strategy

            while (it2.hasNext()) {
                ExtendedTransaction tr = (ExtendedTransaction) it2.next();
                tr.abort();
            }
            committedoffset.getOffsetReaders().clear();
        }
        
        for (int i = startblock; i <= targetblock; i++) {
            blocksar[i-startblock].unlock();
        }
        
        //unlockLocks(heldlocks);
        offsetlock.unlock();
        if (size == 0) {
            size = -1;
        }
        return size;

    }

    public void non_Transactional_Seek(long offset) {
        offsetlock.lock();
        committedoffset.setOffsetnumber(offset);
        offsetlock.unlock();
    }

    public long non_Transactional_getFilePointer() {
        long offset = -1;

        offsetlock.lock();
        offset = committedoffset.getOffsetnumber();
        offsetlock.unlock();

        return offset;
    }

    public int compareTo(Object arg0) {
        TransactionalFile tf = (TransactionalFile) arg0;
        if (this.inode.getNumber() < tf.inode.getNumber()) {
            return -1;
        } else if (this.inode.getNumber() > tf.inode.getNumber()) {
            return 1;
        } else {
            if (this.sequenceNum < tf.sequenceNum) {
                return -1;
            } else {
                return 1;
            }
        }
    }

    public void makeWritestDependent(ExtendedTransaction me) {// make the writes absolute and dependent on ofset value



        Iterator it;
        it = ((Vector) (me.getWriteBuffer().get(inode))).iterator();
        while (it.hasNext()) {

            WriteOperations wrp = (WriteOperations) it.next();
            if (wrp.isUnknownoffset()) {
                wrp.setUnknownoffset(false);
                wrp.getOwnertransactionalFile().lockOffset(me);

                wrp.getRange().setStart(wrp.getOwnertransactionalFile().committedoffset.getOffsetnumber() - wrp.getBelongingto().getCopylocaloffset() + wrp.getRange().getStart());
                wrp.getRange().setEnd(wrp.getOwnertransactionalFile().committedoffset.getOffsetnumber() - wrp.getBelongingto().getCopylocaloffset() + wrp.getRange().getEnd());
                if ((wrp.getBelongingto().getOffsetdependency() == OffsetDependency.WRITE_DEPENDENCY_1) ||
                        (wrp.getBelongingto().offsetdependency == OffsetDependency.NO_ACCESS) ||
                        (wrp.getBelongingto().getOffsetdependency() == OffsetDependency.WRITE_DEPENDENCY_2)) {
                    wrp.getBelongingto().setOffsetdependency(OffsetDependency.READ_DEPENDENCY);
                    wrp.getBelongingto().setUnknown_inital_offset_for_write(false);
                    if (!(wrp.getOwnertransactionalFile().committedoffset.getOffsetReaders().contains(me))) {
                        wrp.getOwnertransactionalFile().committedoffset.getOffsetReaders().add(me);
                    }
                    wrp.getBelongingto().setLocaloffset(wrp.getBelongingto().getLocaloffset() + wrp.getOwnertransactionalFile().committedoffset.getOffsetnumber() - wrp.getBelongingto().getCopylocaloffset());
                }
                wrp.getOwnertransactionalFile().offsetlock.unlock();
                markAccessedBlocks(me, (int) wrp.getRange().getStart(), (int) (wrp.getRange().getEnd() - wrp.getRange().getStart()), BlockAccessModesEnum.WRITE);

            }
        }
    //  }

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


