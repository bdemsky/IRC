/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dstm2;

import TransactionalIO.core.Wrapper;
import TransactionalIO.exceptions.AbortedException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 *
 * @author navid
 */
public class SpecialTransactionalFile{
    
    RandomAccessFile raFile;
    
    public SpecialTransactionalFile(File arg0, String arg1) throws FileNotFoundException {
        raFile = new RandomAccessFile(arg0, arg1);
    }

    public SpecialTransactionalFile(String arg0, String arg1) throws FileNotFoundException {
        raFile = new RandomAccessFile(arg0, arg1);
    }

    
    private void checkConsisteny(){
        Transaction me = Thread.getTransaction();
        if (!me.isActive()) {
                throw new AbortedException();
        }
        if (me != SpecialLock.getSpecialLock().getOwnerTransaction())
            SpecialLock.getSpecialLock().lock(me);
        
        if (!me.isActive()) {
                SpecialLock.getSpecialLock().unlock(me);
                throw new AbortedException();
        }
        
    }
    
    
    public void close() throws IOException {
        checkConsisteny();
        raFile.close();
    }

    
    public long getFilePointer() throws IOException {
        checkConsisteny();
        return raFile.getFilePointer();
    }

    
    public long length() throws IOException {
        checkConsisteny();
        return raFile.length();
    }

    
    public int read() throws IOException {
        checkConsisteny();
        return raFile.read();
    }

    
    public int read(byte[] arg0, int arg1, int arg2) throws IOException {
        checkConsisteny();
        return raFile.read(arg0, arg1, arg2);
    }

    
    public int read(byte[] arg0) throws IOException {
        checkConsisteny();
        return raFile.read(arg0);
    }

    
    public void seek(long arg0) throws IOException {
        checkConsisteny();
        raFile.seek(arg0);
    }

    
    public void setLength(long arg0) throws IOException {
        checkConsisteny();
        raFile.setLength(arg0);
    }

    
    public int skipBytes(int arg0) throws IOException {
        checkConsisteny();
        return raFile.skipBytes(arg0);
    }

    
    public void write(int arg0) throws IOException {
        checkConsisteny();
        raFile.write(arg0);
    }

    
    public void write(byte[] arg0) throws IOException {
        checkConsisteny();
        raFile.write(arg0);
    }

    
    public void write(byte[] arg0, int arg1, int arg2) throws IOException {
        checkConsisteny();
        raFile.write(arg0, arg1, arg2);
    }
    
    public final void writeInt(int integer) throws IOException{
        checkConsisteny();
        raFile.writeInt(integer);
    }
    
    public final int readInt() throws IOException{
        checkConsisteny();
        return raFile.readInt();
    }
    
    public final void writeBoolean(boolean bool) throws IOException{
        checkConsisteny();
        raFile.writeBoolean(bool);
    }
    
    public final boolean readBoolean() throws IOException{
        checkConsisteny();
        return raFile.readBoolean();
    }
    
    public final void writeUTF(String val) throws IOException{
        checkConsisteny();
        raFile.writeUTF(val);
    }
    
    public final String readUTF() throws IOException{
        checkConsisteny();
        return raFile.readUTF();
    }
    
    public final void writeShort(short val) throws IOException{
        checkConsisteny();
        raFile.writeShort(val);
    }
    
    public final short readShort() throws IOException{
        checkConsisteny();
        return raFile.readShort();
    }
    
      public final void writeByte(byte arg0) throws IOException{
        checkConsisteny();
        raFile.writeByte(arg0);
    }
    
    public final byte readByte() throws IOException{
        checkConsisteny();
        return raFile.readByte();
    }
    
    public final void writeChar(int val) throws IOException{
        checkConsisteny();
        raFile.writeChar(val);
    }
    
    public final char readChar() throws IOException{
        checkConsisteny();
        return raFile.readChar();
    }
    
    public final void writeBytes(String val) throws IOException{
        checkConsisteny();
        raFile.writeBytes(val);
    }
    
      public final void writeLong(long val) throws IOException{
        checkConsisteny();
        raFile.writeLong(val);
    }
    
    public final long readLong() throws IOException{
        checkConsisteny();
        return raFile.readLong();
    }
    
      public final void writeDouble(double arg0) throws IOException{
        checkConsisteny();
        raFile.writeDouble(arg0);
    }
    
    public final double readDouble() throws IOException{
        checkConsisteny();
        return raFile.readDouble();
    }
    
    public final void writeFloat(float val) throws IOException{
        checkConsisteny();
        raFile.writeFloat(val);
    }
    
    public final float readFloat() throws IOException{
        checkConsisteny();
        return raFile.readFloat();
    }
    
  
    
    
    

   
    
}
