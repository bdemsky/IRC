/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package TransactionalIO.core;

import TransactionalIO.Utilities.Conversions;
import TransactionalIO.Utilities.Range;
import TransactionalIO.exceptions.AbortedException;
import TransactionalIO.core.ExtendedTransaction.Status;
import TransactionalIO.interfaces.BlockAccessModesEnum;
import TransactionalIO.interfaces.IOOperations;
import TransactionalIO.interfaces.OffsetDependency;
import java.io.EOFException;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UTFDataFormatException;
import java.util.Collections;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.Vector;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;

import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author navid
 */
public class TransactionalFile implements Comparable, IOOperations {

    private native int nativepread(byte buff[], long offset, int size, FileDescriptor fd);

    private native int nativepwrite(byte buff[], long offset, int size, FileDescriptor fd);
    public RandomAccessFile file;
    public INode inode;
    private int sequenceNum = 0;
    public static int currenSeqNumforInode = 0;
    public boolean to_be_created = false;
    public boolean writemode = false;
    public boolean appendmode = false;
    public MYLock myoffsetlock;
    public GlobalOffset committedoffset;
    AtomicBoolean open = new AtomicBoolean(true);
    public GlobalINodeState inodestate;
  

    public TransactionalFile(File f, String mode) {

        if ((!(f.exists()))) {
            to_be_created = true;
            file = null;

        } else {

            try {

                myoffsetlock = new MYLock();
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
                open.set(true);

            }
        }
    }

    public boolean isOpen() {
        return open.get();
    }

    public TransactionalFile(String filename, String mode) {


        File f = new File(filename);


            try {
                myoffsetlock = new MYLock();
                file = new RandomAccessFile(f, mode);
            } catch (FileNotFoundException ex) {

                Logger.getLogger(TransactionalFile.class.getName()).log(Level.SEVERE, null, ex);
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
                open.set(true);
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



    public INode getInode() {
        return inode;
    }

    public void close() throws IOException {
        ExtendedTransaction me = Wrapper.getTransaction();
        if (!(open.get())) {
            throw new IOException();
        }
     //   if (me == null) {
            open.set(false);
            file.close();
            return;
     //   }

       // if (!(me.GlobaltoLocalMappings.containsKey(this))) {
      //      me.addFile(this, 0);
      //  }

        //TransactionLocalFileAttributes tmp = (TransactionLocalFileAttributes) me.GlobaltoLocalMappings.get(this);
        //tmp.setOpen(false);
    }

    public long length() throws IOException {
        if (!(open.get())) {
            throw new IOException();
        }
        ExtendedTransaction me = Wrapper.getTransaction();

        if (me == null) {
            long length = -1;
	//uncomment
            inodestate.commitedfilesize.lengthlock.lock();
            length = inodestate.commitedfilesize.getLength();
            inodestate.commitedfilesize.lengthlock.unlock();

            return length;
        }

        if (!(me.GlobaltoLocalMappings.containsKey(this))) {
            me.addFile(this, 0);
        }

        TransactionLocalFileAttributes tmp = (TransactionLocalFileAttributes) me.GlobaltoLocalMappings.get(this);
        lockLength(me);

        if (!(this.inodestate.commitedfilesize.getLengthReaders().contains(me))) {
            this.inodestate.commitedfilesize.getLengthReaders().add(me);
        }

        tmp.setLocalsize(this.inodestate.commitedfilesize.getLength());
        tmp.lenght_read = true;

        this.inodestate.commitedfilesize.lengthlock.unlock();

        return tmp.getLocalsize();
    }

    public long getFilePointer() throws IOException {
        if (!(open.get())) {
            throw new IOException();
        }
        ExtendedTransaction me = Wrapper.getTransaction();

        if (me == null) {
            return non_Transactional_getFilePointer();
        }

        if (!(me.GlobaltoLocalMappings.containsKey(this))) {
            me.addFile(this, 0);
        }

        TransactionLocalFileAttributes tmp = (TransactionLocalFileAttributes) me.GlobaltoLocalMappings.get(this);
        if ((tmp.offsetdependency == OffsetDependency.WRITE_DEPENDENCY_1) || (tmp.offsetdependency == OffsetDependency.NO_ACCESS)) {
            tmp.offsetdependency = OffsetDependency.READ_DEPENDENCY;

            long target;
            myoffsetlock.acquire(me);
            if (!(this.committedoffset.getOffsetReaders().contains(me))) {
                this.committedoffset.getOffsetReaders().add(me);
            }

            tmp.localoffset = tmp.localoffset + committedoffset.getOffsetnumber() - tmp.copylocaloffset;
            target = committedoffset.getOffsetnumber() - tmp.copylocaloffset;
            myoffsetlock.release(me);
            

            if ((me.writeBuffer.get(inode)) != null) {

                for (int adad = 0; adad < ((ArrayList) (me.writeBuffer.get(inode))).size(); adad++) {
                    WriteOperations wrp = ((WriteOperations) ((ArrayList) (me.writeBuffer.get(inode))).get(adad));
                    if (wrp.belongingto == tmp && wrp.isUnknownoffset()) {
                        wrp.setUnknownoffset(false);
                    }
                    
                    wrp.range.start = target + wrp.range.start;
                    wrp.range.end = target + wrp.range.end;

                }
            }

        }


        tmp.setUnknown_inital_offset_for_write(false);
        return tmp.localoffset;
    }

    public void force() {
    }

    public void seek(long offset) throws IOException {

        if (!(open.get())) {
            throw new IOException();
        }
        ExtendedTransaction me = Wrapper.getTransaction();

        if (me == null) {
            non_Transactional_Seek(offset);
            return;
        }

        if (!(me.GlobaltoLocalMappings.containsKey(this))) {
            me.addFile(this, offset);
        }

        TransactionLocalFileAttributes tmp = (TransactionLocalFileAttributes) me.GlobaltoLocalMappings.get(this);
        if (tmp.offsetdependency == OffsetDependency.NO_ACCESS) {
            tmp.offsetdependency = OffsetDependency.NO_DEPENDENCY;
        } else if (tmp.offsetdependency == OffsetDependency.WRITE_DEPENDENCY_1) {
            tmp.offsetdependency = OffsetDependency.WRITE_DEPENDENCY_2;
        }
        tmp.setUnknown_inital_offset_for_write(false);
        tmp.localoffset = offset;
    }

    public int skipBytes(int n) throws IOException {
        long pos;
        long len;
        long newpos;

        if (n <= 0) {
            return 0;
        }
        pos = getFilePointer();
        newpos = pos + n;
        seek(newpos);

        /* return the actual number of bytes skipped */
        return (int) (newpos - pos);
    }

    public final byte readByte() throws IOException {
        byte[] data = new byte[1];
        read(data);
        byte result = (byte) (data[0]);
        return result;
    }

    public final boolean readBoolean() throws IOException {
        byte[] data = new byte[1];
        read(data);
        if (data[0] == 0) {
            return false;
        }
        return true;
    }

    public final char readChar() throws IOException {
        byte[] data = new byte[2];
        read(data);
        char result = (char) ((data[0] << 8) | data[1]);
        return result;
    }

    public final short readShort() throws IOException {
        byte[] data = new byte[2];
        read(data);
        short result = (short) ((data[0] << 8) | data[1]);
        return result;
    }

    public final int readUnsignedShort() throws IOException {
        byte[] data = new byte[2];
        read(data);
        return (data[0] << 8) + (data[1] << 0);
    }

    public final String readUTF() throws UTFDataFormatException, IOException {
        int utflen = -1;
        byte[] bytearr = null;
        char[] chararr = null;
        try {
            utflen = readUnsignedShort();
        } catch (IOException ex) {
            Logger.getLogger(TransactionalFile.class.getName()).log(Level.SEVERE, null, ex);
        }
        bytearr = new byte[utflen];
        chararr = new char[utflen];


        int c, char2, char3;
        int count = 0;
        int chararr_count = 0;
        read(bytearr);

        while (count < utflen) {
            c = (int) bytearr[count] & 0xff;
            if (c > 127) {
                break;
            }
            count++;
            chararr[chararr_count++] = (char) c;
        }

        while (count < utflen) {
            c = (int) bytearr[count] & 0xff;
            switch (c >> 4) {
                case 0:
                case 1:
                case 2:
                case 3:
                case 4:
                case 5:
                case 6:
                case 7:
                    /* 0xxxxxxx*/
                    count++;
                    chararr[chararr_count++] = (char) c;
                    break;
                case 12:
                case 13:
                    /* 110x xxxx   10xx xxxx*/
                    count += 2;
                    if (count > utflen) {
                        throw new UTFDataFormatException(
                                "malformed input: partial character at end");
                    }
                    char2 = (int) bytearr[count - 1];
                    if ((char2 & 0xC0) != 0x80) {
                        throw new UTFDataFormatException(
                                "malformed input around byte " + count);
                    }
                    chararr[chararr_count++] = (char) (((c & 0x1F) << 6) | (char2 & 0x3F));
                    break;
                case 14:
                    /* 1110 xxxx  10xx xxxx  10xx xxxx */
                    count += 3;
                    if (count > utflen) {
                        throw new UTFDataFormatException(
                                "malformed input: partial character at end");
                    }
                    char2 = (int) bytearr[count - 2];
                    char3 = (int) bytearr[count - 1];
                    if (((char2 & 0xC0) != 0x80) || ((char3 & 0xC0) != 0x80)) {
                        throw new UTFDataFormatException(
                                "malformed input around byte " + (count - 1));
                    }
                    chararr[chararr_count++] = (char) (((c & 0x0F) << 12) | ((char2 & 0x3F) << 6) | ((char3 & 0x3F) << 0));
                    break;
                default:
                    /* 10xx xxxx,  1111 xxxx */
                    throw new UTFDataFormatException(
                            "malformed input around byte " + count);
            }
        }
        // The number of chars produced may be less than utflen
        return new String(chararr, 0, chararr_count);

    }

    public final float readFloat() throws IOException {
        
        return Float.intBitsToFloat(readInt());
    }

    public final double readDouble() throws IOException {
        return Double.longBitsToDouble(readLong());
    }

    public final int readInt() throws IOException {
        byte[] data = new byte[4];
        int k = read(data);

        int result = Conversions.bytes2int(data);
        return result;
    }

    public final long readLong() throws IOException {
        byte[] data = new byte[8];
        read(data);
        long result = Conversions.bytes2long(data);
        return result;
    }

    public final void writeByte(int b) {
        try {
            byte[] result = new byte[1];
            result[0] = (byte) b;
            write(result);
        } catch (IOException ex) {
            Logger.getLogger(TransactionalFile.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    public final void writeChar(int value) throws IOException{
        try {
            byte[] result = new byte[2];
            result[0] = (byte) (value >> 8);
            result[1] = (byte) (value);
            write(result);
        } catch (IOException ex) {
            Logger.getLogger(TransactionalFile.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    public final void writeShort(int value) throws IOException{
        try {
            byte[] result = new byte[2];
            result[0] = (byte) (value >> 8);
            result[1] = (byte) (value);
            write(result);
        } catch (IOException ex) {
            Logger.getLogger(TransactionalFile.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    public final void writeInt(int value) throws IOException{
        try {
            byte[] result = Conversions.int2bytes(value);
            write(result);
        } catch (IOException ex) {
            Logger.getLogger(TransactionalFile.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    public final void writeFloat(float v) throws IOException{
        writeInt(Float.floatToIntBits(v));
    }

    public final void writeLong(long value) throws IOException{
        try {
            byte[] result = Conversions.long2bytes(value);
            write(result);
        } catch (IOException ex) {
            Logger.getLogger(TransactionalFile.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    public final void writeDouble(double v) throws IOException{
        writeLong(Double.doubleToLongBits(v));
    }

    public final void writeBoolean(boolean v) throws IOException{
        writeByte(v ? 1 : 0);
    }

    public final void writeChars(String s) throws IOException {
        int clen = s.length();
        int blen = 2 * clen;
        byte[] b = new byte[blen];
        char[] c = new char[clen];
        s.getChars(0, clen, c, 0);
        for (int i = 0, j = 0; i < clen; i++) {
            b[j++] = (byte) (c[i] >>> 8);
            b[j++] = (byte) (c[i] >>> 0);
        }
        write(b);
    }

    public final void writeUTF(String str) throws UTFDataFormatException {
        int strlen = str.length();
        int utflen = 0;
        int c, count = 0;
        /* use charAt instead of copying String to char array */
        for (int i = 0; i < strlen; i++) {
            c = str.charAt(i);
            if ((c >= 0x0001) && (c <= 0x007F)) {
                utflen++;
            } else if (c > 0x07FF) {
                utflen += 3;
            } else {
                utflen += 2;
            }
        }

        if (utflen > 65535) {
            throw new UTFDataFormatException(
                    "encoded string too long: " + utflen + " bytes");
        }
        byte[] bytearr = null;

        bytearr = new byte[utflen + 2];


        bytearr[count++] = (byte) ((utflen >>> 8) & 0xFF);
        bytearr[count++] = (byte) ((utflen >>> 0) & 0xFF);

        int i = 0;
        for (i = 0; i < strlen; i++) {
            c = str.charAt(i);
            if (!((c >= 0x0001) && (c <= 0x007F))) {
                break;
            }
            bytearr[count++] = (byte) c;
        }

        for (; i < strlen; i++) {
            c = str.charAt(i);
            if ((c >= 0x0001) && (c <= 0x007F)) {
                bytearr[count++] = (byte) c;

            } else if (c > 0x07FF) {
                bytearr[count++] = (byte) (0xE0 | ((c >> 12) & 0x0F));
                bytearr[count++] = (byte) (0x80 | ((c >> 6) & 0x3F));
                bytearr[count++] = (byte) (0x80 | ((c >> 0) & 0x3F));
            } else {
                bytearr[count++] = (byte) (0xC0 | ((c >> 6) & 0x1F));
                bytearr[count++] = (byte) (0x80 | ((c >> 0) & 0x3F));
            }
        }
        try {
            write(bytearr);
        } catch (IOException ex) {
            Logger.getLogger(TransactionalFile.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public String readLine()throws IOException{
	     StringBuffer input = new StringBuffer();
             int c = -1;
             boolean eol = false;
     
             while (!eol) {
                   switch (c = (int)readByte()) {
                      case -1:
                      case '\n':
                         eol = true;
                         break;
                      case '\r':
                        eol = true;
                      long cur = getFilePointer();
                      if ((int)(readByte()) != '\n') {
                         seek(cur);
                      }
                      break;
                      default:
                      input.append((char)c);
                      break;
                    }
             }
   
             if ((c == -1) && (input.length() == 0)) {
                return null;
       }
       return input.toString();
    }

    public int read(byte[] b) throws IOException {
        ExtendedTransaction me = Wrapper.getTransaction();
        int size = b.length;
        int result = 0;
        if (me == null) {  // not a transaction, but any I/O operation even though within a non-transaction is considered a single opertion transactiion 

            return non_Transactional_Read(b);
        }

        if (!(me.GlobaltoLocalMappings.containsKey(this))) { // if this is the first time the file is accessed by the transcation

            me.addFile(this, 0);
        }

        TransactionLocalFileAttributes tmp = (TransactionLocalFileAttributes) me.GlobaltoLocalMappings.get(this);
        tmp.setUnknown_inital_offset_for_write(false);

        OffsetDependency dep = tmp.offsetdependency;
        if ((dep == OffsetDependency.WRITE_DEPENDENCY_1) ||
                (dep == OffsetDependency.NO_ACCESS) ||
                (dep == OffsetDependency.WRITE_DEPENDENCY_2)) {
            tmp.offsetdependency = OffsetDependency.READ_DEPENDENCY;
            
            myoffsetlock.acquire(me);
            if (dep != OffsetDependency.WRITE_DEPENDENCY_2) {
                tmp.localoffset = tmp.localoffset + this.committedoffset.getOffsetnumber() - tmp.copylocaloffset;
            }

            if (!(this.committedoffset.getOffsetReaders().contains(me))) {
                this.committedoffset.getOffsetReaders().add(me);

            }
            
            myoffsetlock.release(me);
        }        

        if (me.writeBuffer.get(inode) != null) {
            makeWritestDependent(me);
        //}
           if ((Boolean) me.merge_for_writes_done.get(inode) == Boolean.FALSE) //{
            mergeWrittenData(me);
        }

	long loffset = tmp.localoffset;

        ArrayList writebuffer = null;
        if ((me.writeBuffer.get(this.inode)) != null) {
            writebuffer = (ArrayList) (me.writeBuffer.get(this.inode));

        } else {
            result = readFromFile(me, b, tmp);
            markAccessedBlocks(me, loffset, size, BlockAccessModesEnum.READ);
            return result;
        }

        int resultt;
        
        ///////////////////CAUTION TO FIX THIS///////////////////////////////
        Range readrange = new Range(loffset, loffset + size);
        Range writerange = null;
        Range[] intersectedrange = new Range[writebuffer.size()];
        WriteOperations[] markedwriteop = new WriteOperations[writebuffer.size()];

        int counter = 0;
        boolean in_local_buffer = false;


        for (int adad = 0; adad < writebuffer.size(); adad++) {
            WriteOperations wrp = (WriteOperations) writebuffer.get(adad);
            writerange = wrp.range;
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
            markAccessedBlocks(me, loffset, size, BlockAccessModesEnum.READ);
            return result;


        } else {
            if (counter == 0) { // all the read straight from file
                result = readFromFile(me, b, tmp);
            markAccessedBlocks(me, loffset, size, BlockAccessModesEnum.READ);
            } else {    // some parts from file others from buffer
            
                for (int i = 0; i < counter; i++) {
                    byte[] data = markedwriteop[i].data;
                    System.arraycopy(data, (int) (intersectedrange[i].start - markedwriteop[i].range.start), b, (int) (intersectedrange[i].start - readrange.start), (int) (Math.min(intersectedrange[i].end, readrange.end) - intersectedrange[i].start));
                    result += Math.min(intersectedrange[i].end, readrange.end) - intersectedrange[i].start;
                }

                Range[] non_intersected_ranges = readrange.minus(intersectedrange, counter);
                ArrayList occupiedblocks = new ArrayList();
                for (int i = 0; i < non_intersected_ranges.length; i++) {
                    int st = FileBlockManager.getCurrentFragmentIndexofTheFile(non_intersected_ranges[i].start);
                    int en = FileBlockManager.getCurrentFragmentIndexofTheFile(non_intersected_ranges[i].end);
                    for (int j = st; j <= en; j++) {
                        if (!(occupiedblocks.contains(Integer.valueOf(j)))) {
                            occupiedblocks.add(Integer.valueOf(j));
                        }
                    }
                }

                BlockDataStructure block;
                int k;
                for (k = 0; k < occupiedblocks.size() && me.getStatus() == Status.ACTIVE; k++) {   // locking the block locks

                    block = this.inodestate.getBlockDataStructure((Integer) (occupiedblocks.get(k)));
                    block.getLock().readLock().lock();
                    
                    if (!(block.getReaders().contains(me))) {
                        block.getReaders().add(me);
                    }

                }
                if (k < occupiedblocks.size()) {
                    for (int i = 0; i < k; i++) {
                        block = this.inodestate.getBlockDataStructure((Integer) (occupiedblocks.get(k)));
                        if (me.toholdblocklocks[me.blockcount] == null) {
                            me.toholdblocklocks[me.blockcount] = new ReentrantReadWriteLock().readLock();
                        }
                        me.toholdblocklocks[me.blockcount] = block.getLock().readLock();
                        me.blockcount++;
                    }
                    throw new AbortedException();
                }


                for (int i = 0; i < non_intersected_ranges.length; i++) {

                    int sizetoread = (int) (non_intersected_ranges[i].end - non_intersected_ranges[i].start);
                    byte[] tmpdt = new byte[(int) (non_intersected_ranges[i].end - non_intersected_ranges[i].start)];
                    int tmpsize = invokeNativepread(tmpdt, non_intersected_ranges[i].start, sizetoread);
                    System.arraycopy(tmpdt, 0, b, (int) (non_intersected_ranges[i].start - readrange.start), sizetoread);
                    result += tmpsize;

                }

                if (me.getStatus() == Status.ABORTED) {
                    for (k = 0; k < occupiedblocks.size(); k++) {
                        block = this.inodestate.getBlockDataStructure((Integer) (occupiedblocks.get(k)));
                        if (me.toholdblocklocks[me.blockcount] == null) {
                            me.toholdblocklocks[me.blockcount] = new ReentrantReadWriteLock().readLock();
                        }
                        me.toholdblocklocks[me.blockcount] = block.getLock().readLock();
                        me.blockcount++;
                    }
                    throw new AbortedException();
                }
                for (k = 0; k < occupiedblocks.size(); k++) {
                    block = this.inodestate.getBlockDataStructure((Integer) (occupiedblocks.get(k)));
			//unlock
                    block.getLock().readLock().unlock();
                }
                tmp.localoffset = tmp.localoffset + result;
            }

            markAccessedBlocks(me, loffset, size, BlockAccessModesEnum.READ);
            return result;
        }

    }

    public void write(byte[] data, int dataoffset, int size) throws IOException {
        if (!(writemode)) {
            throw new IOException();

        }

        ExtendedTransaction me = Wrapper.getTransaction();

        if (me == null) // not a transaction 
        {

            non_Transactional_Write(data);
            return;
        }
        if (!(me.GlobaltoLocalMappings.containsKey(this))) {
            me.addFile(this, 0);
        }

   
        ArrayList dummy =  null;
        if (((ArrayList) (me.writeBuffer.get(this.inode))) == null) {
            dummy = new ArrayList();
            me.writeBuffer.put(this.inode, dummy);
        }
	
	else
           dummy = (ArrayList) (me.writeBuffer.get(this.inode))/*)*/;

        
        TransactionLocalFileAttributes tmp = ((TransactionLocalFileAttributes) (me.GlobaltoLocalMappings.get(this)));
        
        boolean flag = false;
        for (int i=0; i< ((ArrayList) (me.writeBuffer.get(this.inode))).size(); i++){
            if (((WriteOperations)((ArrayList) (me.writeBuffer.get(this.inode))).get(i)).range.includes(tmp.localoffset, tmp.localoffset + size)){
                System.arraycopy(data, dataoffset, ((WriteOperations)((ArrayList) (me.writeBuffer.get(this.inode))).get(i)).data, 0, size);
                flag = true;
                break;
            }
        }
        
                
        if (!(flag)){
            Range range = new Range(tmp.localoffset, tmp.localoffset + size);
            byte[] by = new byte[size];
            System.arraycopy(data, dataoffset, by, 0, size);
            dummy.add(new WriteOperations(by, range, tmp.isUnknown_inital_offset_for_write(), this, tmp));
            me.writeBuffer.put(this.inode, dummy);
            me.merge_for_writes_done.put(inode, Boolean.FALSE);

        }

        long loffset = tmp.localoffset;


        tmp.localoffset += size;

        if (tmp.localoffset > tmp.localsize) {
            tmp.setLocalsize(tmp.localoffset);
        }

        if (!(tmp.isUnknown_inital_offset_for_write())) {
            markAccessedBlocks(me, loffset, size, BlockAccessModesEnum.WRITE);
        }
        if (tmp.offsetdependency == OffsetDependency.NO_ACCESS) {
            tmp.offsetdependency = OffsetDependency.WRITE_DEPENDENCY_1;
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
        if (!(me.GlobaltoLocalMappings.containsKey(this))) {
            me.addFile(this, 0);
        }

   
        ArrayList dummy =  null;
        if (((ArrayList) (me.writeBuffer.get(this.inode))) == null) {
            dummy = new ArrayList();
            me.writeBuffer.put(this.inode, dummy);
        }
	
	else
           dummy = (ArrayList) (me.writeBuffer.get(this.inode));

        
        TransactionLocalFileAttributes tmp = ((TransactionLocalFileAttributes) (me.GlobaltoLocalMappings.get(this)));
        
        boolean flag = false;
        for (int i=0; i< ((ArrayList) (me.writeBuffer.get(this.inode))).size(); i++){
            if (((WriteOperations)((ArrayList) (me.writeBuffer.get(this.inode))).get(i)).range.includes(tmp.localoffset, tmp.localoffset + size)){
                System.arraycopy(data, 0, ((WriteOperations)((ArrayList) (me.writeBuffer.get(this.inode))).get(i)).data, 0, size);
                flag = true;
                break;
            }
        }
        
                
        if (!(flag)){
            Range range = new Range(tmp.localoffset, tmp.localoffset + size);
            byte[] by = new byte[size];
            System.arraycopy(data, 0, by, 0, size);
            dummy.add(new WriteOperations(by, range, tmp.isUnknown_inital_offset_for_write(), this, tmp));
            me.writeBuffer.put(this.inode, dummy);
            me.merge_for_writes_done.put(inode, Boolean.FALSE);

        }

        long loffset = tmp.localoffset;


        tmp.localoffset += size;

        if (tmp.localoffset > tmp.localsize) {
            tmp.setLocalsize(tmp.localoffset);
        }

        if (!(tmp.isUnknown_inital_offset_for_write())) {
            markAccessedBlocks(me, loffset, size, BlockAccessModesEnum.WRITE);
        }
        if (tmp.offsetdependency == OffsetDependency.NO_ACCESS) {
            tmp.offsetdependency = OffsetDependency.WRITE_DEPENDENCY_1;
        }
    }

    private  void markAccessedBlocks(ExtendedTransaction me, long loffset, int size, BlockAccessModesEnum mode) {
        TreeMap map;

        if (me.accessedBlocks.get(this.inode) != null) {
            map = (TreeMap) me.accessedBlocks.get(this.inode);
        } else {
            map = new TreeMap();
            me.accessedBlocks.put(this.inode, map);
        }

        int startblock = (int) ((loffset / MyDefaults.FILEFRAGMENTSIZE));
        int targetblock = (int) (((size + loffset -1) / MyDefaults.FILEFRAGMENTSIZE));

        for (int i = startblock; i <= targetblock; i++) {
	    Integer iobj=Integer.valueOf(i);
	    BlockAccessModesEnum bame=(BlockAccessModesEnum) map.get(iobj);
            if (bame == null) {
                map.put(iobj, mode);
            } else if (bame != mode && bame != BlockAccessModesEnum.READ_WRITE) {
                map.put(iobj, BlockAccessModesEnum.READ_WRITE);
            }
        }
    }

    private int readFromFile(ExtendedTransaction me, byte[] readdata, TransactionLocalFileAttributes tmp) {
        int st = (int) ((tmp.localoffset / MyDefaults.FILEFRAGMENTSIZE));
        int end = (int) (((tmp.localoffset + readdata.length - 1) / MyDefaults.FILEFRAGMENTSIZE));
        TreeMap m = ((TreeMap)me.accessedBlocks.get(inode));
        for (int k = st; k <= end; k++) {
            //should be uncommented with proper check for read blocks to avoid locking
	    
	    Integer ki=Integer.valueOf(k);
	    if (m==null || m.get(ki) == BlockAccessModesEnum.WRITE){
		BlockDataStructure block = this.inodestate.getBlockDataStructure(ki);
		block.getLock().readLock().lock();
		block.getReaders().add(me);
		me.toholdblocklocks[me.blockcount] = block.getLock().readLock();
		me.blockcount++;
	    }
        }

        //Do the read
        int size = invokeNativepread(readdata, tmp.localoffset, readdata.length);
        tmp.localoffset += size;

        //Handle EOF
        if (size == 0) {
            size = -1;
        }


        if (me.getStatus() == Status.ABORTED) {
            throw new AbortedException();
        }

        for (int k = 0; k <me.blockcount; k++) {
            me.toholdblocklocks[k].unlock();
            me.blockcount = 0;
        }
        return size;
    }

    private int readFromBuffer(byte[] readdata, TransactionLocalFileAttributes tmp, WriteOperations wrp, Range writerange) {
        long loffset = tmp.localoffset;
        
        byte[] data =  wrp.data;
        System.arraycopy(data, (int) (loffset - writerange.start), readdata, 0, readdata.length);
        tmp.localoffset += readdata.length;
        return readdata.length;

    }

    public void simpleWritetoBuffer(Byte[] data, Range newwriterange, TreeMap tm) {
        tm.put(newwriterange, data);
    }

  

    public void setInode(INode inode) {
        this.inode = inode;
    }


    public void lockLength(ExtendedTransaction me) {
        boolean locked = false;	
	System.out.println("saas");
        if (me.getStatus() == Status.ACTIVE) {                        //locking the offset

            this.inodestate.commitedfilesize.lengthlock.lock();
            locked = true;
        }

        if (me.getStatus() != Status.ACTIVE) {
            if (locked) {
                me.heldlengthlocks.add(this.inodestate.commitedfilesize.lengthlock);               
            }
            throw new AbortedException();
        }

    }

    public void mergeWrittenData(ExtendedTransaction me/*TreeMap target, byte[] data, Range to_be_merged_data_range*/) {
        
    
        
        boolean flag = false;
        ArrayList vec = (ArrayList) me.writeBuffer.get(this.inode);
        
        if (vec.size() == 1){
            me.merge_for_writes_done.put(inode, Boolean.TRUE);
            return;
        }
        
        Range intersectedrange = new Range(0, 0);

        WriteOperations wrp;
        WriteOperations wrp2;
        ArrayList toberemoved = new ArrayList();
        for (int adad = 0; adad < vec.size(); adad++) {
            
            wrp = (WriteOperations) (vec.get(adad));
            if (toberemoved.contains(wrp)) {
                continue;
            }

            for (int adad2 = 0; adad2 < vec.size(); adad2++) {
                flag = false;
                wrp2 = (WriteOperations) (vec.get(adad2));

                if ((wrp2 == wrp) || toberemoved.contains(wrp2)) {
                    continue;
                }

                if (wrp.range.hasIntersection(wrp2.range)) {
                    flag = true;
                    intersectedrange = wrp2.range.intersection(wrp.range);
                    toberemoved.add(wrp2);
                }


                long startprefix = 0;
                long endsuffix = 0;
                long startsuffix = 0;
                int prefixsize = 0;
                int suffixsize = 0;
                int intermediatesize = 0;
                byte[] prefixdata = null;
                byte[] suffixdata = null;
                boolean prefix = false;
                boolean suffix = false;
                if (flag) {


                    if (wrp.range.start < wrp2.range.start) {
                        prefixdata = new byte[(int) (wrp2.range.start - wrp.range.start)];
                        prefixdata = (byte[]) (wrp.data);
                        
                        startprefix = wrp.range.start;
                        prefixsize = (int) (intersectedrange.start - startprefix);
                        intermediatesize = (int) (intersectedrange.end - intersectedrange.start);
                        prefix = true;
                    } else if (wrp2.range.start <= wrp.range.start) {
                        prefixdata = new byte[(int) (wrp.range.start - wrp2.range.start)];
                        prefixdata = (byte[]) (wrp2.data);
                        
                        startprefix = wrp2.range.start;
                        prefixsize = (int) (intersectedrange.start - startprefix);
                        intermediatesize = (int) (intersectedrange.end - intersectedrange.start);
                        prefix = true;
                    }

                    if (wrp2.range.end >= wrp.range.end) {

                        suffixdata = new byte[(int) (wrp2.range.end - intersectedrange.end)];
                        suffixdata = (byte[]) (wrp2.data);
                        startsuffix = intersectedrange.end - wrp2.range.start;
                        suffixsize = (int) (wrp2.range.end - intersectedrange.end);
                        suffix = true;
                        endsuffix = wrp2.range.end;
                    } else if (wrp.range.end > wrp2.range.end) {
                        
                        suffixdata = new byte[(int) (wrp.range.end - intersectedrange.end)];
                        suffixdata = (byte[]) (wrp.data);
                        
                        startsuffix = intersectedrange.end - wrp.range.start;
                        suffixsize = (int) (wrp.range.end - intersectedrange.end);
                        endsuffix = wrp.range.end;
                        suffix = true;

                    }

                    byte[] data_to_insert;

                    if ((prefix) && (suffix)) {
                        data_to_insert = new byte[(int) (endsuffix - startprefix)];
                        
                        System.arraycopy(prefixdata, 0, data_to_insert, 0, prefixsize);
                        System.arraycopy(wrp2.data, (int) (intersectedrange.start - wrp2.range.start), data_to_insert, prefixsize, intermediatesize);
                        System.arraycopy(suffixdata, (int) startsuffix, data_to_insert, (prefixsize + intermediatesize), suffixsize);
                        wrp.setData(data_to_insert);
                        wrp.range = new Range(startprefix, endsuffix);
                    }

                }
            }
        }

        for (int adad = 0; adad < toberemoved.size(); adad++) {
            vec.remove(toberemoved.get(adad));
        }
        Collections.sort(vec);
        me.merge_for_writes_done.put(inode, Boolean.TRUE);

    }

    public void non_Transactional_Write(byte[] data) {

  
        myoffsetlock.non_Transactional_Acquire();
        inodestate.commitedfilesize.lengthlock.lock();
        int startblock = FileBlockManager.getCurrentFragmentIndexofTheFile(committedoffset.getOffsetnumber());
        int targetblock = FileBlockManager.getTargetFragmentIndexofTheFile(committedoffset.getOffsetnumber(), data.length-1);
        Lock[] blocksar;
        blocksar = new Lock[targetblock - startblock + 1];
        for (int i = startblock; i <= targetblock; i++) {
	    
            BlockDataStructure block = this.inodestate.getBlockDataStructure(i);
            block.getLock().writeLock().lock();
            blocksar[i - startblock] = block.getLock().writeLock();
        }

        try {
            ExtendedTransaction.invokeNativepwrite(data, committedoffset.getOffsetnumber(), data.length, file);
            if (committedoffset.getOffsetnumber()+data.length > inodestate.commitedfilesize.getLength()){
                    inodestate.commitedfilesize.setLength(committedoffset.getOffsetnumber()+data.length);
                    if (!(inodestate.commitedfilesize.getLengthReaders().isEmpty())){
                        for (int adad = 0; adad < inodestate.commitedfilesize.getLengthReaders().size(); adad++) {
                            ExtendedTransaction tr = (ExtendedTransaction) inodestate.commitedfilesize.getLengthReaders().get(adad);
                            tr.abort();
                        }
                        inodestate.commitedfilesize.getLengthReaders().clear();
                    }
            }
            committedoffset.setOffsetnumber(committedoffset.getOffsetnumber() + data.length);
            if (!(committedoffset.getOffsetReaders().isEmpty())) {
                for (int adad = 0; adad < committedoffset.getOffsetReaders().size(); adad++) {
                    ExtendedTransaction tr = (ExtendedTransaction) committedoffset.getOffsetReaders().get(adad);
                    tr.abort();
                }
                committedoffset.getOffsetReaders().clear();
            }
            

        } finally {
            for (int i = startblock; i <= targetblock; i++) {
                blocksar[i - startblock].unlock();
            }
            
            inodestate.commitedfilesize.lengthlock.unlock();
            myoffsetlock.non_Transactional_Release();
        }

    }

    public int non_Transactional_Read(byte[] b) {
        int size = -1;

        myoffsetlock.non_Transactional_Acquire();

        int startblock;
        int targetblock;
        startblock = FileBlockManager.getCurrentFragmentIndexofTheFile(committedoffset.getOffsetnumber());
        targetblock = FileBlockManager.getTargetFragmentIndexofTheFile(committedoffset.getOffsetnumber(), size);

        
        Lock[] blocksar;
        blocksar = new Lock[targetblock - startblock + 1];

        for (int i = startblock; i <= targetblock; i++) {
           
            BlockDataStructure block = this.inodestate.getBlockDataStructure(i);
            block.getLock().readLock().lock();
            blocksar[i - startblock] = block.getLock().readLock();
        }

        size = invokeNativepread(b, committedoffset.getOffsetnumber(), b.length);
        committedoffset.setOffsetnumber(committedoffset.getOffsetnumber() + size);
        if (!(committedoffset.getOffsetReaders().isEmpty())) {
            for (int adad = 0; adad < committedoffset.getOffsetReaders().size(); adad++) {
                ExtendedTransaction tr = (ExtendedTransaction) committedoffset.getOffsetReaders().get(adad);
                tr.abort();
            }
            committedoffset.getOffsetReaders().clear();
        }

        for (int i = startblock; i <= targetblock; i++) {
            blocksar[i - startblock].unlock();
        }

        myoffsetlock.non_Transactional_Release();
        if (size == 0) {
            size = -1;
        }
        return size;

    }

    public void non_Transactional_Seek(long offset) {
        myoffsetlock.non_Transactional_Acquire();
        committedoffset.setOffsetnumber(offset);
        if (!(committedoffset.getOffsetReaders().isEmpty())) {
            for (int adad = 0; adad < committedoffset.getOffsetReaders().size(); adad++) {
                ExtendedTransaction tr = (ExtendedTransaction) committedoffset.getOffsetReaders().get(adad);
                tr.abort();
            }
            committedoffset.getOffsetReaders().clear();
	}    
        myoffsetlock.non_Transactional_Release();
    }

    public long non_Transactional_getFilePointer() {
        long offset = -1;
        myoffsetlock.non_Transactional_Acquire();
        offset = committedoffset.getOffsetnumber();
        myoffsetlock.non_Transactional_Release();
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
        for (int adad = 0; adad < ((ArrayList) (me.writeBuffer.get(inode))).size(); adad++) {
            WriteOperations wrp = (WriteOperations) ((ArrayList) (me.writeBuffer.get(inode))).get(adad);
            if (wrp.isUnknownoffset()) {
                wrp.setUnknownoffset(false);
                    wrp.ownertransactionalfile.myoffsetlock.acquire(me);
                    wrp.range.start = wrp.ownertransactionalfile.committedoffset.getOffsetnumber() - wrp.belongingto.copylocaloffset + wrp.range.start;
                    wrp.range.end = wrp.ownertransactionalfile.committedoffset.getOffsetnumber() - wrp.belongingto.copylocaloffset + wrp.range.end;
                    
                    if ((wrp.belongingto.offsetdependency == OffsetDependency.WRITE_DEPENDENCY_1) ||
                            (wrp.belongingto.offsetdependency == OffsetDependency.NO_ACCESS) ||
                            (wrp.belongingto.offsetdependency == OffsetDependency.WRITE_DEPENDENCY_2)) {
                        wrp.belongingto.offsetdependency = OffsetDependency.READ_DEPENDENCY;
                        wrp.belongingto.setUnknown_inital_offset_for_write(false);
                        if (!(wrp.ownertransactionalfile.committedoffset.getOffsetReaders().contains(me))) {
                            wrp.ownertransactionalfile.committedoffset.getOffsetReaders().add(me);
                        }
                        wrp.belongingto.localoffset = wrp.belongingto.localoffset + wrp.ownertransactionalfile.committedoffset.getOffsetnumber() - wrp.belongingto.copylocaloffset;
                    }
                    wrp.ownertransactionalfile.myoffsetlock.release(me);
                markAccessedBlocks(me, (int) wrp.range.start, (int) (wrp.range.end - wrp.range.start), BlockAccessModesEnum.WRITE);
            }
        }
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


