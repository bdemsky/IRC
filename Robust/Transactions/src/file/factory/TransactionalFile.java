/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dstm2.file.factory;

import dstm2.Thread;
import dstm2.Transaction.Status;
import dstm2.exceptions.AbortedException;
import dstm2.exceptions.PanicException;
import dstm2.file.interfaces.BlockAccessModesEnum;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Array;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author navid
 */
public class TransactionalFile {

    public RandomAccessFile file;
    private INode inode;
    public Adapter adapter;
    /*  public AtomicLong commitedoffset;
    public AtomicLong commitedfilesize;*/
    public boolean to_be_created = false;
    public boolean writemode = false;
    public boolean appendmode = false;
    public ReentrantLock offsetlock;
    public Offset commitedoffset;

    public TransactionalFile(String filename, String mode) {
        synchronized (this) {
            adapter = TransactionalFileWrapperFactory.createTransactionalFile(filename, mode);
            inode = TransactionalFileWrapperFactory.getINodefromFileName(filename);
        }

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
        if (mode.equals("rw")) {
            writemode = true;
        } else if (mode.equals("a")) {
            appendmode = true;
        }
        synchronized (adapter) {
            try {
                if (!(to_be_created)) {

                    adapter.commitedfilesize.set(file.length());

                } else {
                    adapter.commitedfilesize.set(0);
                }
                if (!appendmode) {
                    commitedoffset.setOffsetnumber(0);
                } else {
                    commitedoffset.setOffsetnumber(file.length());
                }
            } catch (IOException ex) {
                Logger.getLogger(TransactionalFile.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
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
    public /*synchronized*/ BlockLock getBlockLock(int blocknumber) {
        synchronized (adapter.lockmap) {
            if (adapter.lockmap.containsKey(blocknumber)) {
                return ((BlockLock) (adapter.lockmap.get(blocknumber)));
            } else {
                BlockLock tmp = new BlockLock(getInode(),blocknumber);
                adapter.lockmap.put(blocknumber, tmp);
                return tmp;
            }
        }

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

    public void seek(long offset) {

        if (appendmode) {
            throw new PanicException("Cannot seek into a file opened in append mode");
        }
        ExtendedTransaction me = ExtendedTransaction.getTransaction();
        TransactionLocalFileAttributes tmp;
        if (!(me.getFilesAccesses().containsKey(this.inode))) {
            me.addFile(this);
            tmp = ((TransactionLocalFileAttributes) (me.getFilesAccesses().get(this.getInode())));
            tmp.writetoabsoluteoffset = true;
            tmp.writetoabsoluteoffsetfromhere = 0;
            //tmp.setValidatelocaloffset(false);
        } else {
            tmp = ((TransactionLocalFileAttributes) (me.getFilesAccesses().get(this.getInode())));
            //tmp.setValidatelocaloffset(true);
            if (!(tmp.writetoabsoluteoffset))
            {
                tmp.writetoabsoluteoffset = true;
                tmp.writetoabsoluteoffsetfromhere = tmp.getWrittendata().size();
            }
        }
        
        tmp.setLocaloffset(offset);
    }

    public int read(byte[] b) {

        if (appendmode) {
            throw new PanicException("Cannot seek into a file opened in append mode");
        }
        ExtendedTransaction me = ExtendedTransaction.getTransaction();
        int size = b.length;
        int result;

        if (me == null) {  // not a transaction 

            size = 10;
            return size;
        } else if (me.getFilesAccesses().containsKey(this.getInode())) {// in its read list files, read from the file

            TransactionLocalFileAttributes tmp = ((TransactionLocalFileAttributes) (me.getFilesAccesses().get(this.getInode())));
            tmp.setValidatelocaloffset(true);


            long loffset = tmp.getLocaloffset();


            int startblock = FileBlockManager.getCurrentFragmentIndexofTheFile(loffset);
            int targetblock = FileBlockManager.getTargetFragmentIndexofTheFile(loffset, size);
            for (int i = startblock; i <= targetblock; i++) {
                if (tmp.getAccesedblocks().containsKey(Integer.valueOf(i))) {
                    if (((BlockAccessModesEnum) (tmp.getAccesedblocks().get(Integer.valueOf(i)))) == BlockAccessModesEnum.WRITE) {
                        tmp.getAccesedblocks().put(Integer.valueOf(i), BlockAccessModesEnum.READ_WRITE);
                    }
                } else {
                    tmp.getAccesedblocks().put(Integer.valueOf(i), BlockAccessModesEnum.READ);


                    //adapter.lock.lock();
                    tmp.getBlockversions().put(Integer.valueOf(i), Integer.valueOf(getBlockLock(i).version.get()));
                //adapter.lock.unlock();
                    /*synchronized(adapter.version){
                tmp.getBlockversions().put(Integer.valueOf(i), tmp.adapter.version);  // 1st alternative
                }*/

                //return readFromFile(b, tmp);
                }
            }


            if (!(validateBlocksVersions(startblock, targetblock))) {
                throw new AbortedException();
            }



            Range readrange = new Range(loffset, loffset + size);
            Range writerange = null;
            Range[] intersectedrange = new Range[tmp.getWrittendata().size()];
            Range[] markedwriteranges = new Range[tmp.getWrittendata().size()];

            int counter = 0;




            boolean flag = false;
            Iterator it = tmp.getWrittendata().keySet().iterator();
            while (it.hasNext()) {
                writerange = (Range) it.next();
                if (writerange.includes(readrange)) {
                    flag = true;
                    break;
                }

                if (writerange.hasIntersection(readrange)) {
                    intersectedrange[counter] = readrange.intersection(writerange);
                    markedwriteranges[counter] = writerange;
                    counter++;
                }
            }

            if (flag) {
                result = readFromBuffer(b, tmp, writerange);
                tmp.setLocaloffset(tmp.getLocaloffset() + result);
            //return result;
            } else if (counter == 0) {
                result = readFromFile(b, tmp);
                tmp.setLocaloffset(tmp.getLocaloffset() + result);
            //return result;
            } else {

                for (int i = 0; i < counter; i++) {
                    Byte[] data = (Byte[]) (tmp.getWrittendata().get(markedwriteranges[i]));
                    byte[] copydata = new byte[data.length];

                    for (int j = 0; j < data.length; j++) {
                        copydata[j] = data[j].byteValue();
                    }
                    System.arraycopy(copydata, (int) (intersectedrange[i].getStart() - markedwriteranges[i].getStart()), b, (int) (intersectedrange[i].getStart() - readrange.getStart()), (int) (Math.min(intersectedrange[i].getEnd(), readrange.getEnd()) - intersectedrange[i].getStart()));
                }

                Range[] non_intersected_ranges = readrange.minus(intersectedrange, counter);
                Vector occupiedblocks = new Vector();
                Vector heldlocks = new Vector();
                for (int i = 0; i < non_intersected_ranges.length; i++) {
                    int st = FileBlockManager.getCurrentFragmentIndexofTheFile(non_intersected_ranges[i].getStart());
                    int en = FileBlockManager.getCurrentFragmentIndexofTheFile(non_intersected_ranges[i].getEnd());
                    for (int j = st; j <= en; j++) {
                        if (!(occupiedblocks.contains(j))) {
                            occupiedblocks.add(j);
                        }
                    }
                }
                
                
                offsetlock.lock();
                
                for (int k = 0; k < occupiedblocks.size(); k++) {
                    int expvalue = ((Integer) tmp.getBlockversions().get(Integer.valueOf(k))).intValue();
                    while (me.getStatus() == Status.ACTIVE) {
                        BlockLock block = ((BlockLock) tmp.adapter.lockmap.get(Integer.valueOf(k)));
                        //  if (block.version.get() == expvalue) {

                        if (block.lock.tryLock()) {
                            heldlocks.add(block.lock);
                            if (!(block.version.get() == expvalue)) {
                                me.abort();
                            } else {
                                break;
                            }
                        } else {
                            me.getContentionManager().resolveConflict(me, block.owner);
                        }
                    // } else {
                    //     me.abort();
                    //}
                    }
                    if (me.getStatus() == Status.ABORTED) {
                        unlockLocks(heldlocks);
                        throw new AbortedException();
                    }
                }
                
                for (int i = 0; i < non_intersected_ranges.length; i++) {
                    try {
                 //       offsetlock.lock();
                        file.seek(non_intersected_ranges[i].getStart());
                        file.read(b, (int) (non_intersected_ranges[i].getStart() - readrange.getStart()), (int) (non_intersected_ranges[i].getEnd() - non_intersected_ranges[i].getStart()));
                    } catch (IOException ex) {
                        Logger.getLogger(TransactionalFile.class.getName()).log(Level.SEVERE, null, ex);

                    }

                }
                
                unlockLocks(heldlocks);
                offsetlock.unlock();
                tmp.setLocaloffset(tmp.getLocaloffset() + size);
                result = size;
            //return size;

            }

            return result;

        } else {           // add to the readers list  
            //me.addReadFile(this);
            
            me.addFile(this/*, TransactionLocalFileAttributes.MODE.READ*/);
            TransactionLocalFileAttributes tmp = ((TransactionLocalFileAttributes) (me.getFilesAccesses().get(this.getInode())));
            tmp.setValidatelocaloffset(true);
            return read(b);
        }

    }

    public void write(byte[] data) throws IOException {

        if (!(writemode)) {
            throw new IOException();
        //return;
        }

        ExtendedTransaction me = ExtendedTransaction.getTransaction();

        int size = data.length;


        if (me == null) // not a transaction 
        {
            size = 10;
        } else if (me.getFilesAccesses().containsKey(this.getInode())) // in its read list files, read from the file
        {

            TransactionLocalFileAttributes tmp = ((TransactionLocalFileAttributes) (me.getFilesAccesses().get(this.getInode())));
      
            Byte[] by = new Byte[size];
            for (int i = 0; i < size; i++) {
                by[i] = Byte.valueOf(data[i]);
            }
            TreeMap tm = tmp.getWrittendata();
            long loffset = tmp.getLocaloffset();
            Range newwriterange;
            if (appendmode) {
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
            }  
            
            newwriterange = new Range(loffset, loffset + size);
            Range oldwriterange = null;
            Range intersect = null;
            Range[] intersectedrange = new Range[tmp.getWrittendata().size()];
            Range[] markedwriteranges = new Range[tmp.getWrittendata().size()];
            by = new Byte[size];
            int counter = 0;

            /*
            
            if (tmp.accessmode == TransactionLocalFileAttributes.MODE.READ)
            tmp.accessmode = TransactionLocalFileAttributes.MODE.READ_WRITE;
            else if (tmp.accessmode == TransactionLocalFileAttributes.MODE.WRITE)
            simpleWritetoBuffer(by, newwriterange, tm);
             */

            int startblock = FileBlockManager.getCurrentFragmentIndexofTheFile(loffset);
            int targetblock = FileBlockManager.getTargetFragmentIndexofTheFile(loffset, size);
            for (int i = startblock; i <= targetblock; i++) {
                if (tmp.getAccesedblocks().containsKey(Integer.valueOf(i))) {
                    if (((BlockAccessModesEnum) (tmp.getAccesedblocks().get(Integer.valueOf(i)))) == BlockAccessModesEnum.READ) {
                        tmp.getAccesedblocks().put(Integer.valueOf(i), BlockAccessModesEnum.READ_WRITE);
                    }
                } else {
                    tmp.getAccesedblocks().put(Integer.valueOf(i), BlockAccessModesEnum.WRITE);

                    tmp.getBlockversions().put(Integer.valueOf(i), Integer.valueOf(getBlockLock(i).version.get()));
                }
            }

            //    Vector offset = tmp.getStartoffset();
            //    Vector tmpdata = tmp.getData();



            // offset.add(new Integer(tmp.localoffset));

            boolean flag = false;
            Iterator it = tmp.getWrittendata().keySet().iterator();
            while (it.hasNext()) {
                oldwriterange = (Range) it.next();
                if (oldwriterange.includes(newwriterange)) {
                    flag = true;
                    intersect = newwriterange.intersection(oldwriterange);
                    break;
                }

                if (oldwriterange.hasIntersection(newwriterange)) {
                    intersectedrange[counter] = newwriterange.intersection(oldwriterange);
                    markedwriteranges[counter] = oldwriterange;
                    counter++;
                }
            }

            if (flag) {
                int datasize = (int) (oldwriterange.getEnd() - oldwriterange.getStart());
                Byte[] original = (Byte[]) (tmp.getWrittendata().get(oldwriterange));
                byte[] originaldata = new byte[datasize];

                for (int i = 0; i < data.length; i++) {
                    originaldata[i] = original[i].byteValue();
                }
                System.arraycopy(data, 0, originaldata, (int) (newwriterange.getStart() - oldwriterange.getStart()), size);
                Byte[] to_be_inserted = new Byte[datasize];
                //System.arraycopy(b, 0, ab, a.length);  

                for (int i = 0; i < datasize; i++) {
                    to_be_inserted[i] = Byte.valueOf(originaldata[i]);
                }
                tm.put(oldwriterange, to_be_inserted);
                tmp.setLocaloffset(loffset + size);
                if (tmp.getLocaloffset() > tmp.getFilelength())
                    tmp.setFilelength(tmp.getLocaloffset());
                return;
                
            } else if (counter == 0) {
                tm.put(newwriterange, data);
                tmp.setLocaloffset(loffset + size);
                if (tmp.getLocaloffset() > tmp.getFilelength())
                    tmp.setFilelength(tmp.getLocaloffset());
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

                    //if (newwriterange.includes(markedwriteranges[i]))
                    //tm.remove(markedwriteranges);

                    if (markedwriteranges[i].getStart() < newwriterange.getStart()) {

                        prefixdata = new Byte[(int) (newwriterange.getStart() - markedwriteranges[i].getStart())];
                        prefixdata = (Byte[]) (tmp.getWrittendata().get(markedwriteranges[i]));
                        start = markedwriteranges[i].getStart();
                        //newdata = new Byte[size +  newwriterange.getStart() - markedwriteranges[i].getStart()];
                        //System.arraycopy(by, 0, newdata, newwriterange.getStart() - markedwriteranges[i].getStart(), size);
                        //System.arraycopy(originaldata, 0, newdata, 0, newwriterange.getStart() - markedwriteranges[i].getStart());

                        //newwriterange.setStart(markedwriteranges[i].getStart());
                        prefix = true;


                    } else if (markedwriteranges[i].getEnd() > newwriterange.getEnd()) {

                        suffixdata = new Byte[(int) (markedwriteranges[i].getStart() - newwriterange.getStart())];
                        suffixdata = (Byte[]) (tmp.getWrittendata().get(markedwriteranges[i]));
                        end = markedwriteranges[i].getEnd();

                        /*Byte [] originaldata = (Byte [])(tmp.getWrittendata().get(markedwriteranges[i]));
                        newdata = new Byte[size +  newwriterange.getStart() - markedwriteranges[i].getStart()];
                        System.arraycopy(originaldata, 0, newdata, 0, newwriterange.getStart() - markedwriteranges[i].getStart());
                        
                        newwriterange.setStart(markedwriteranges[i].getStart());*/
                        //newwriterange.setEnd(markedwriteranges[i].getEnd());
                        suffix = true;
                        suffixstart = (int) (intersectedrange[i].getEnd() - intersectedrange[i].getStart());
                    //tm.remove(markedwriteranges[i]); 
                    }
                    tm.remove(markedwriteranges[i]);

                }
                Byte[] data_to_insert;

                if ((prefix) && (suffix)) {
                    data_to_insert = new Byte[(int) (newwriterange.getStart() - start + size + newwriterange.getEnd() - end)];
                    System.arraycopy(prefixdata, 0, data_to_insert, 0, (int) (newwriterange.getStart() - start));
                    System.arraycopy(by, 0, data_to_insert, (int) (newwriterange.getStart() - start), size);
                    System.arraycopy(suffixdata, suffixstart, data_to_insert, (int) (size + newwriterange.getStart() - start), (int) (end - newwriterange.getEnd()));
                    newwriterange.setStart(start);
                    newwriterange.setEnd(end);
                }
                else if (prefix) {
                    data_to_insert = new Byte[(int) (newwriterange.getStart() - start + size)];
                    System.arraycopy(prefixdata, 0, data_to_insert, 0, (int) (newwriterange.getStart() - start));
                    System.arraycopy(by, 0, data_to_insert, (int) (newwriterange.getStart() - start), size);
                    newwriterange.setStart(start);
                }
                else if (suffix) {
                    data_to_insert = new Byte[(int) (newwriterange.getEnd() - end + size)];
                    System.arraycopy(by, 0, data_to_insert, 0, size);
                    System.arraycopy(suffixdata, suffixstart, data_to_insert, size, (int) (end - newwriterange.getEnd()));
                    newwriterange.setEnd(end);
                }
                else {
                    data_to_insert = new Byte[size];
                    System.arraycopy(data_to_insert, (int) (newwriterange.getStart() - start), by, 0, size);
                }
                tm.put(newwriterange, data_to_insert);
                tmp.setLocaloffset(loffset + size);
                if (tmp.getLocaloffset() > tmp.getFilelength())
                    tmp.setFilelength(tmp.getLocaloffset());
            }


        } else {
            me.addFile(this/*, TransactionLocalFileAttributes.MODE.WRITE*/);
            write(data);
        }

    }

    private int readFromFile(byte[] readdata, TransactionLocalFileAttributes tmp) {

        ExtendedTransaction me = ExtendedTransaction.getTransaction();
        int st = FileBlockManager.getCurrentFragmentIndexofTheFile(tmp.getLocaloffset());
        int end = FileBlockManager.getTargetFragmentIndexofTheFile(tmp.getLocaloffset(), readdata.length);
        Vector heldlocks = new Vector();
        for (int k = st; k <= end; k++) {
            int expvalue = ((Integer) tmp.getBlockversions().get(Integer.valueOf(k))).intValue();
            while (me.getStatus() == Status.ACTIVE) {
                BlockLock block = ((BlockLock) tmp.adapter.lockmap.get(Integer.valueOf(k)));
                //  if (block.version.get() == expvalue) {

                if (block.lock.tryLock()) {
                    heldlocks.add(block.lock);
                    if (!(block.version.get() == expvalue)) {
                        me.abort();
                    } else {
                        break;
                    }
                } else {
                    me.getContentionManager().resolveConflict(me, block.owner);
                }
            //} else {
            //    me.abort();
            //}
            }
            if (me.getStatus() == Status.ABORTED) {
                //unlockLocks(heldlocks);
                throw new AbortedException();
            }

        }
        int size = -1;
        try {

            offsetlock.lock();
            file.seek(tmp.getLocaloffset());
            size = file.read(readdata);
            offsetlock.unlock();

        } catch (IOException ex) {
            Logger.getLogger(TransactionalFile.class.getName()).log(Level.SEVERE, null, ex);
        }



        unlockLocks(heldlocks);
        tmp.setLocaloffset(tmp.getLocaloffset() + size);
        return size;

    /*try {
    while (me.getStatus() == Status.ACTIVE) {
    if (this.adapter.lock.tryLock()) {
    file.seek(tmp.localoffset);
    int result = file.read(readdata);
    this.adapter.lock.unlock();
    tmp.localoffset += result;
    return result;
    } else {
    me.getContentionManager().resolveConflict(me, adapter.writer);
    }
    }
    
    } catch (IOException ex) {
    Logger.getLogger(TransactionalFile.class.getName()).log(Level.SEVERE, null, ex);
    }*/

    }

    private int readFromBuffer(byte[] readdata, TransactionLocalFileAttributes tmp, Range writerange) {


        long loffset = tmp.getLocaloffset();

        Byte[] data = (Byte[]) (tmp.getWrittendata().get(writerange));
        byte[] copydata = null;

        for (int i = 0; i < data.length; i++) {
            copydata[i] = data[i].byteValue();
        }
        System.arraycopy(copydata, (int) (loffset - writerange.getStart()), readdata, 0, readdata.length);
        return readdata.length;

    }

    public void simpleWritetoBuffer(Byte[] data, Range newwriterange, TreeMap tm) {
        tm.put(newwriterange, data);
    }

    public void unlockLocks(Vector heldlocks) {
        for (int i = 0; i < heldlocks.size(); i++) {
            ((ReentrantLock) heldlocks.get(i)).unlock();
        }
    }

    public boolean validateBlocksVersions(int startblock, int targetblock) {
        boolean valid = true;
        ExtendedTransaction me = ExtendedTransaction.getTransaction();
        TransactionLocalFileAttributes tmp = ((TransactionLocalFileAttributes) (me.getFilesAccesses().get(this.getInode())));
        for (int i = startblock; i <= targetblock; i++) {
            int expvalue = ((Integer) tmp.getBlockversions().get(Integer.valueOf(i))).intValue();
            BlockLock block = ((BlockLock) tmp.adapter.lockmap.get(Integer.valueOf(i)));
            if (expvalue != block.version.get()) {
                valid = false;
                break;
            }
        }

        return valid;
    }

    public INode getInode() {
        return inode;
    }

    public void setInode(INode inode) {
        this.inode = inode;
    }
    /*    public void check(){
    ExtendedTransaction me = ExtendedTransaction.getTransaction();
    for (Adapter reader : me.ReadOnly) 
    if (reader.version.get() == adapter.version.get())
    
    
    
    }*/
} 