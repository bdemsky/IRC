/*
 * JCarder -- cards Java programs to keep threads disentangled
 *
 * Copyright (C) 2006-2007 Enea AB
 * Copyright (C) 2007 Ulrik Svensson
 * Copyright (C) 2007 Joel Rosdahl
 *
 * This program is made available under the GNU GPL version 2, with a special
 * exception for linking with JUnit. See the accompanying file LICENSE.txt for
 * details.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.enea.jcarder.common.contexts;

import TransactionalIO.core.TransactionalFile;
import TransactionalIO.exceptions.GracefulException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

//import net.jcip.annotations.ThreadSafe;

import com.enea.jcarder.common.Lock;
import com.enea.jcarder.common.LockingContext;
import com.enea.jcarder.transactionalinterfaces.Bool;
import com.enea.jcarder.transactionalinterfaces.Intif;
import com.enea.jcarder.transactionalinterfaces.bytebuffer;
import com.enea.jcarder.transactionalinterfaces.bytebuffer.byteholder;
import com.enea.jcarder.util.logging.Logger;
import dstm2.Init;
import dstm2.atomic;
import dstm2.Thread;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.LongBuffer;
import java.util.Vector;
import java.util.concurrent.Callable;

import dstm2.AtomicArray;

//@ThreadSafe
import java.util.logging.Level;
public final class ContextFileWriter
implements ContextWriterIfc {
   
    private final FileChannel mChannel;
    private int mNextFilePosition = 0;
    private final Logger mLogger;
    private ByteBuffer mBuffer;// = ByteBuffer.allocateDirect(8192);
    private byte[] data;
    RandomAccessFile raFile;
    
    
    private bytebuffer mbuff;
    private Intif filePosition;
    private Intif test;
    private Bool ShutdownHookExecuted; 
    
    TransactionalFile trraFile;
    
    
    private boolean mShutdownHookExecuted = false;

    public ContextFileWriter(Logger logger, File file) throws IOException {
        System.out.println("d");
        mbuff = new bytebuffer();
        mbuff.allocateDirect(8192);
        filePosition = new Intif();
        filePosition.init();
        test = new Intif();
        test.init();
        ShutdownHookExecuted = new Bool();
        ShutdownHookExecuted.init();
        data = new byte[8192];
        mBuffer = ByteBuffer.wrap(data);
        mLogger = logger;
        mLogger.info("Opening for writing: " + file.getAbsolutePath());
        raFile = new RandomAccessFile(file, "rw");
       
        trraFile = new TransactionalFile(file.getAbsolutePath(), "rw");
        trraFile.file.setLength(0);
//        ShutdownHookExecuted.init();
       
        
        raFile.setLength(0);
        mChannel = raFile.getChannel();

        
      //  writeHeader();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() { shutdownHook(); }
        });
    }

  
    
     private void shutdownHook() {
      //  System.out.println(Thread.currentThread() + " ashut ddddddborted in committing");
        try{ 
        Thread.doIt(new Callable<Boolean>() {
	  
            public Boolean call() {
            
                try {
                    if (trraFile.file.getChannel().isOpen()) {
                        writeBuffer();
                    
                }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                
                ShutdownHookExecuted.set(true);
                return true;
            }
       });
       }catch(GracefulException e){
       //    System.out.println(Thread.currentThread() + " shut graceful exc");
       }
     }

  
    
    private void writeBuffer() throws IOException {
        mbuff.flip();
        System.out.println(Thread.currentThread());
        
     //   System.out.println(mbuff.remaining());
        trraFile.write(mbuff.getBytes());
        while (mbuff.hasRemaining()) {
            Thread.yield();
            trraFile.write(mbuff.getBytes());
        }
        mbuff.clear();
    }

  
    
    private void writeHeader() throws IOException {
     //   System.out.println("head");
        ByteBuffer mBuffer = ByteBuffer.allocateDirect(16);
        mBuffer.putLong(ContextFileReader.MAGIC_COOKIE);
        mBuffer.putInt(ContextFileReader.MAJOR_VERSION);
        mBuffer.putInt(ContextFileReader.MINOR_VERSION);
        mBuffer.rewind();
        for (int i=0; i<16; i++){
            mbuff.put(mBuffer.get());
        }
        //filePosition.get();
        test.increment(8+4+4);
        //filePosition.increment(8+4+4);
    }


    
    public void close() throws IOException {
      //  System.out.println("clo");
        try{
            Thread.doIt(new Callable<Boolean>() {
                
            public Boolean call() {
                try {
                  //  System.out.println(Thread.currentThread() + " closeaborted in committing");
                      writeBuffer();
                      trraFile.close();  
                } catch (IOException e) {
                    e.printStackTrace();
                }
                
                return true;
            };
       });
        }catch(GracefulException e){
                    System.out.println(Thread.currentThread() + " close graceful exc");
        }
    }

  
    
    private void writeString(String s) throws IOException {
     //   System.out.println("str");
        ByteBuffer encodedString = ContextFileReader.CHARSET.encode(s);
        final int length = encodedString.remaining();
        assureBufferCapacity(4 + length);
        ByteBuffer mBuffer = ByteBuffer.allocateDirect(length +4);
        mBuffer.putInt(length);
        mBuffer.put(encodedString);
        mBuffer.rewind();
        System.out.println("---------------");
        System.out.println("int: " + length);
        System.out.println("str: " + encodedString);
        System.out.println("---------------");
        //test.increment(8+length);
        
        //for (int i=0; i<4+length; i++)
              //    mbuff.put(mBuffer.get());
      //  while (mBuffer.hasRemaining())
      //      mbuff.put(mBuffer.get());
      //  mbuff.put(mBuffer);
       // mbuff.put(encodedString);
        //filePosition.get();
       // test.increment(4+length);
       // test.increment(4);
        //filePosition.increment(4 + length);
    }

 
    
    private void writeInteger(int i) throws IOException {
     //   System.out.println("int");
        assureBufferCapacity(4);
        ByteBuffer mBuffer = ByteBuffer.allocateDirect(4);
        mBuffer.putInt(i);
        System.out.println("---------------");
        System.out.println("int: " + i);
        
        
        System.out.println("---------------");
        for (int j=0; j<4; j++){
          //      mbuff.put(mBuffer.get());
        }
       // filePosition.get();
        //test.increment(4);
        //filePosition.increment(4);
    }

  
    
    private void assureBufferCapacity(int size) throws IOException {
        if (mbuff.remaining() < size){// || ShutdownHookExecuted.isTrue()) {
            writeBuffer();
        }

        // Grow buffer if it can't hold the requested size.
        while (mbuff.capacity() < size) {
            mbuff = mbuff.allocateDirect(2 * mbuff.capacity());
        }
    }
    
    
    

   
    
    public int trwriteLock(Vector arg) throws IOException {
        int startPosition = test.get();
        //writeString(((Lock)arg.get(0)).getClassName());
        //writeInteger(((Lock)arg.get(0)).getObjectId());
        
        ByteBuffer encodedString = ContextFileReader.CHARSET.encode(((Lock)arg.get(0)).getClassName());
        final int length = encodedString.remaining();
        ByteBuffer mBuffer = ByteBuffer.allocateDirect(8+ length);
        mBuffer.putInt(length);
        mBuffer.put(encodedString);
        mBuffer.putInt(((Lock)arg.get(0)).getObjectId());
        mBuffer.rewind();
      //  assureBufferCapacity(8+length);
        for (int j=0; j<(8+length); j++){
            byte value = mBuffer.get();
            //if (mbuff.mbuffer.getByteHolder().get(mbuff.mbuffer.getPosition()) ==  null)
            //    mbuff.mbuffer.getByteHolder().set(mbuff.mbuffer.getPosition(), mbuff.factory2.create());
            AtomicArray<byteholder> ar = mbuff.mbuffer.getByteHolder();
            byteholder bh = mbuff.factory2.create();
            bh.setByte(value);
            mbuff.mbuffer.getByteHolder().set(mbuff.mbuffer.getPosition(),bh);
            mbuff.mbuffer.setPosition(mbuff.mbuffer.getPosition()+1);
        }
         
        /*System.out.println("---------------");
        System.out.println("int: " + length);
        System.out.println("str: " + encodedString);
        System.out.println("int: " + ((Lock)arg.get(0)).getObjectId());
        System.out.println("---------------");*/
        test.increment(8+length);
        flushBufferIfNeeded();
        return startPosition;
    }
    
    public int writeLock(Lock lock) throws IOException {
      //  System.out.println("lock");
            int result = 0;
            try{
            final Vector arg = new Vector() ;
            arg.add(lock);
            result = Thread.doIt(new Callable<Integer>() {
	
		public Integer call() {
          		try {
              //              System.out.println(Thread.currentThread() + " alockborted in committing");
                            return trwriteLock(arg);
                          //  System.out.println(Thread.currentThread() + " NO???? alockborted in committing");
			} catch (IOException ex) {
                            java.util.logging.Logger.getLogger(ContextFileWriter.class.getName()).log(Level.SEVERE, null, ex);
                        }
	        	return -1;
	        }
            }); 
            
            }catch(GracefulException e){
           System.out.println(Thread.currentThread() + "lock graceful exc");
           
            }finally{
                return result;
            }
    }

  
    
    public  int trwriteContext(LockingContext context)
    throws IOException {
        int startPosition = test.get();
        
     //   writeString(context.getThreadName());
     //   writeString(context.getLockReference());
     //   writeString(context.getMethodWithClass());
        
        ByteBuffer encodedString = ContextFileReader.CHARSET.encode(context.getThreadName());
        final int length = encodedString.remaining();
        ByteBuffer encodedString2 = ContextFileReader.CHARSET.encode(context.getLockReference());
        final int length2 = encodedString2.remaining();
        ByteBuffer encodedString3 = ContextFileReader.CHARSET.encode(context.getMethodWithClass());
        final int length3 = encodedString3.remaining();
        ByteBuffer mBuffer = ByteBuffer.allocateDirect(12+length+length2+length3);
        
        mBuffer.putInt(length);
        mBuffer.put(encodedString);
        mBuffer.putInt(length2);
        mBuffer.put(encodedString2);
        mBuffer.putInt(length3);
        mBuffer.put(encodedString3);
        mBuffer.rewind();
    /*    System.out.println("------");
        System.out.println("int: " + length);
        System.out.println("str: " + encodedString);
        System.out.println("int: " + length2);
        System.out.println("str: " + encodedString2);
        System.out.println("int: " + length3);
        System.out.println("str: " + encodedString3);
        System.out.println("------");*/
        
        
      //  assureBufferCapacity(12 + length + length2 + length3);
        for (int j=0;j<(12+length +length2 +length3);j++){
       //     mbuff.put(mBuffer.get());
               byte value = mBuffer.get();
            //if (mbuff.mbuffer.getByteHolder().get(mbuff.mbuffer.getPosition()) ==  null)
            //    mbuff.mbuffer.getByteHolder().set(mbuff.mbuffer.getPosition(), mbuff.factory2.create());
            AtomicArray<byteholder> ar = mbuff.mbuffer.getByteHolder();
            byteholder bh = mbuff.factory2.create();
            bh.setByte(value);
            mbuff.mbuffer.getByteHolder().set(mbuff.mbuffer.getPosition(),bh);
            mbuff.mbuffer.getByteHolder().get(mbuff.mbuffer.getPosition()).setByte(value);
            mbuff.mbuffer.setPosition(mbuff.mbuffer.getPosition()+1);
        }
       
        test.increment(12+length +length2 +length3);
        flushBufferIfNeeded();
        
        return startPosition;
    }

   public int writeContext(LockingContext context)
    {
       
       int startPosition = -2;
       try{
        final LockingContext c = context;  
    	 startPosition = Thread.doIt(new Callable<Integer>() {
                
		public Integer call() throws IOException {
				 return trwriteContext(c); 
	        }
               
        });
         
        

        }catch(GracefulException e){
           System.out.println(Thread.currentThread() + " context graceful exc");
       }
       finally{
        //   System.out.println(Thread.currentThread() + " con");
        return startPosition;
       }
    }
   
     private void flushBufferIfNeeded() throws IOException {
        if (ShutdownHookExecuted.isTrue()) {
       //     System.out.println("fdddlush");
            writeBuffer();
        }
    }
     
       
     /* 
        private void writeString(String s) throws IOException {
 
        ByteBuffer encodedString = ContextFileReader.CHARSET.encode(s);
        final int length = encodedString.remaining();
     
        assureBufferCapacity(4 + length);
        mBuffer.putInt(length);
        mBuffer.put(encodedString);
        System.out.println("------");
        System.out.println("int: " + length);
        System.out.println("str: " + encodedString);
        System.out.println("------");
        
        mNextFilePosition += 4 + length;
    }
      
      private synchronized void shutdownHook() {
        System.out.println("shut");
        try {
            if (mChannel.isOpen()) {
                writeBuffer();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        mShutdownHookExecuted = true;
    }
    
  
    
        public synchronized void close() throws IOException {
        writeBuffer();
        mChannel.close();
    }
    
       private void writeInteger(int i) throws IOException {
        assureBufferCapacity(4);
        mBuffer.putInt(i);
        System.out.println("------");
        System.out.println("int: " + i);
        System.out.println("------");
        mNextFilePosition += 4;
        
    }
      private void assureBufferCapacity(int size) throws IOException {
        if (mBuffer.remaining() < size || mShutdownHookExecuted) {
            writeBuffer();
        }

        // Grow buffer if it can't hold the requested size.
        while (mBuffer.capacity() < size) {
            mBuffer = ByteBuffer.allocateDirect(2 * mBuffer.capacity());
        }
    }
    
     public synchronized int writeLock(Lock lock) throws IOException {
        final int startPosition = mNextFilePosition;
        writeString(lock.getClassName());
        writeInteger(lock.getObjectId());
        flushBufferIfNeeded();
        return startPosition;
    }
    
    public synchronized int writeContext(LockingContext context)
    throws IOException {
        final int startPosition = mNextFilePosition;
        writeString(context.getThreadName());
        writeString(context.getLockReference());
        writeString(context.getMethodWithClass());
        flushBufferIfNeeded();
       System.out.println(Thread.currentThread() + " con");
        return startPosition;
    }
    
    private void flushBufferIfNeeded() throws IOException {
        if (mShutdownHookExecuted) {
            writeBuffer();
      //      System.out.println("sssskk");
        }
    }
      
        private void writeBuffer() throws IOException {
            mBuffer.flip();
            System.out.println("here " + mBuffer.array().length);
            System.out.println("Written" + mChannel.write(mBuffer));
            while (mBuffer.hasRemaining()) {
                Thread.yield();
                raFile.write(data);
                //mChannel.write(mBuffer);
            }
            mBuffer.clear();
        } 
        private void writeHeader() throws IOException {
            mBuffer.putLong(ContextFileReader.MAGIC_COOKIE);
            mBuffer.putInt(ContextFileReader.MAJOR_VERSION);
            mBuffer.putInt(ContextFileReader.MINOR_VERSION);
            mNextFilePosition += 8 + 4 + 4;
         
     //   LongBuffer g;
     //   Long.
     //   Long.valueOf(ContextFileReader.MAGIC_COOKIE).
     //   mBuffer.putLong(ContextFileReader.MAGIC_COOKIE);
     //   mBuffer.putInt(ContextFileReader.MAJOR_VERSION);
      //  mBuffer.putInt(ContextFileReader.MINOR_VERSION);
      //  mNextFilePosition += 8 + 4 + 4;
    }*/
    }
    
   
		   


