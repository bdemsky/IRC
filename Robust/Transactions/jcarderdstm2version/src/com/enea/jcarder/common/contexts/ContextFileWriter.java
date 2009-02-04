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
import com.enea.jcarder.transactionalinterfaces.jcbuffer;
import com.enea.jcarder.util.Counter;
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
   

    private final Logger mLogger;
    RandomAccessFile raFile;

    private bytebuffer mbuff;
    private Intif test;
    private Bool ShutdownHookExecuted; 
    ;
    
    
 //   private boolean mShutdownHookExecuted = false;

    public ContextFileWriter(Logger logger, File file) throws IOException {
        mbuff = new bytebuffer();
        mbuff.allocateDirect(8192);

        test = new Intif();
        test.init();
        ShutdownHookExecuted = new Bool();
        ShutdownHookExecuted.init();;
        mLogger = logger;
        mLogger.info("Opening for writing: " + file.getAbsolutePath());
        raFile = new RandomAccessFile(file, "rw");
         raFile.setLength(0);
        System.out.println(file.getAbsolutePath());
       
        ShutdownHookExecuted.init();
       
        
       
//        mChannel = raFile.getChannel();

        
        writeHeader();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() { shutdownHook(); }
        });
    }

  
    
     private synchronized void shutdownHook() {
      //  System.out.println(Thread.currentThread() + " ashut ddddddborted in committing");
        
        Thread.doIt(new Callable<Boolean>() {
	  
            public Boolean call() {
            
                try {
                    if (raFile.getChannel().isOpen()) {
                        writeBuffer();
                    
                }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                
                ShutdownHookExecuted.set(true);
                return true;
            }
       });
      
     }

  
    
    private void writeBuffer() throws IOException {
        mbuff.flip();
        raFile.write(mbuff.getBytes());
        
        while (mbuff.hasRemaining()) {
            Thread.yield();
            raFile.write(mbuff.getBytes());
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
        mbuff.put(mBuffer);
        test.increment(8+4+4);
        //filePosition.increment(8+4+4);
    }


    
    public synchronized void close() throws IOException {
      //  System.out.println("clo");
        
            Thread.doIt(new Callable<Boolean>() {
                
            public Boolean call() {
                try {
                  //  System.out.println(Thread.currentThread() + " closeaborted in committing");
                      writeBuffer();
                      raFile.close();  
                } catch (IOException e) {
                    e.printStackTrace();
                }
                
                return true;
            };
       });
        
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
        mbuff.put(mBuffer);
        
        //for (int j=0; j<4+length; j++){
        //       mbuff.factory2.create();
        //}
        //mbuff.put(mBuffer.get());
        test.increment(4+length);
        //

    }

 
    
    private void writeInteger(int i) throws IOException {
        assureBufferCapacity(4);
        ByteBuffer mBuffer = ByteBuffer.allocateDirect(4);
        mBuffer.putInt(i);
        mBuffer.rewind();
        mbuff.put(mBuffer);
        //for (int j=0; j<4; j++){
           // mbuff.factory2.create();
               //mbuff.put(mBuffer.get());
        //}
        test.increment(4);
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
    
    
    

   
    
    public int writeLock(Lock lock) throws IOException {
        int startPosition = test.get();
        writeString(lock.getClassName());
        writeInteger(lock.getObjectId());
        
     //   ByteBuffer encodedString = ContextFileReader.CHARSET.encode(((Lock)arg.get(0)).getClassName());
     //   final int length = encodedString.remaining();
      //  ByteBuffer mBuffer = ByteBuffer.allocateDirect(8+ length);
     //   mBuffer.putInt(length);
     //   mBuffer.put(encodedString);
      //  mBuffer.putInt(((Lock)arg.get(0)).getObjectId());
       // mBuffer.rewind();
       // assureBufferCapacity(8+length);
       // for (int j=0; j<(8+length); j++){
        //    byte value = mBuffer.get();
         //   mbuff.put(value);
       // }
    //    mbuff.put(mBuffer);
       // test.increment(8+length);
        flushBufferIfNeeded();
        return startPosition;
        
    }
    
 /*   public int writeLockWrapper(Lock lock) throws IOException {
      //  System.out.println("lock");
            int result = 0;
            try{
            final Vector arg = new Vector() ;
            arg.add(lock);
            result = Thread.doIt(new Callable<Integer>() {
	
		public Integer call() {
          		try {
              //              System.out.println(Thread.currentThread() + " alockborted in committing");
                            return writeLock(arg);
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
    }*/

  
    
     public int writeContext(LockingContext context)
    throws IOException {
        int startPosition = test.get();
        writeString(context.getThreadName());
        writeString(context.getLockReference());
        writeString(context.getMethodWithClass());
        
     //   ByteBuffer encodedString = ContextFileReader.CHARSET.encode(context.getThreadName());
      //  final int length = encodedString.remaining();
      //  ByteBuffer encodedString2 = ContextFileReader.CHARSET.encode(context.getLockReference());
      //  final int length2 = encodedString2.remaining();
     //   ByteBuffer encodedString3 = ContextFileReader.CHARSET.encode(context.getMethodWithClass());
     //   final int length3 = encodedString3.remaining();
      //  ByteBuffer mBuffer = ByteBuffer.allocateDirect(12+length+length2+length3);
        
      //  mBuffer.putInt(length);
      //  mBuffer.put(encodedString);
      //  mBuffer.putInt(length2);
      //  mBuffer.put(encodedString2);
      //  mBuffer.putInt(length3);
      //  mBuffer.put(encodedString3);
      //  mBuffer.rewind();

        
        
       // assureBufferCapacity(12 + length + length2 + length3);
       //for (int j=0;j<(12+length +length2 +length3);j++){
       //       byte value = mBuffer.get();
        //      mbuff.put(value);
      // }
     //   mbuff.put(mBuffer);
       
        //test.increment(12+length +length2 +length3);
        flushBufferIfNeeded();
        
        return startPosition;
       // mbuff.put((byte)2);
       // return -1;
    }

/*   public int writeContext(LockingContext context)
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
 //        System.out.println(Thread.currentThread() + " con");
  //       Throwable t = new Throwable();
//StackTraceElement[] es = t.getStackTrace();
//for ( int i=0; i<es.length; i++ )
   //{
   //StackTraceElement e = es[i];
   //System.out.println( " in class:" + e.getClassName()
          //             + " in source file:" + e.getFileName()
         //              + " in method:" + e.getMethodName()
       //                + " at line:" + e.getLineNumber()
     //                  + " " + ( e.isNativeMethod() ? "native" : "" ) );
   //}
         
        return startPosition;
       }
    }*/
   
     private void flushBufferIfNeeded() throws IOException {
        if (ShutdownHookExecuted.isTrue()) {
       //     System.out.println("fdddlush");
            writeBuffer();
        }
    }

    public Bool getShutdownHookExecuted() {
        return ShutdownHookExecuted;
    }

    public void setShutdownHookExecuted(Bool ShutdownHookExecuted) {
        this.ShutdownHookExecuted = ShutdownHookExecuted;
    }

   

    public bytebuffer getMbuff() {
        return mbuff;
    }

    public void setMbuff(bytebuffer mbuff) {
        this.mbuff = mbuff;
    }

    public RandomAccessFile getRaFile() {
        return raFile;
    }

    public void setRaFile(RandomAccessFile raFile) {
        this.raFile = raFile;
    }

    public Intif getTest() {
        return test;
    }

    public void setTest(Intif test) {
        this.test = test;
    }

    public RandomAccessFile getTrraFile() {
        return raFile;
    }

    public void setTrraFile(RandomAccessFile trraFile) {
        this.raFile = trraFile;
    }
     
     
       
      
 /*       private void writeString(String s) throws IOException {
 
        ByteBuffer encodedString = ContextFileReader.CHARSET.encode(s);
        final int length = encodedString.remaining();
     
        assureBufferCapacity(4 + length);
        mBuffer.putInt(length);
        mBuffer.put(encodedString);
        for (int j =0; j<4+length; j++)
            mbuff.put(mBuffer.get());
         //   datastindex++;
        //}
        
        mNextFilePosition += 4 + length;
    }
      
      private synchronized void shutdownHook() {
        System.out.println("shut");
        try {
            if (mChannel.isOpen()) {
                  System.out.println("shut");
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
        System.out.println("ffff");
        for (int j =0; j<4; j++)
            mbuff.put(mBuffer.get());
  //          data[dataendindex+i] = mBuffer.get();
//            datastindex++;
    //    }
        mNextFilePosition += 4;
        
    }
      private void assureBufferCapacity(int size) throws IOException {
        if (mBuffer.remaining() < size || mShutdownHookExecuted) {
            writeBuffer();
        }

        // Grow buffer if it can't hold the requested size.
        while (mBuffer.capacity() < size) {
            System.out.println("be ga raftam");
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
            int rem = mBuffer.remaining();
            int i = 0;
            //while (mBuffer.hasRemaining()) {
            while (i< rem) {
                Thread.yield();
                raFile.write(data);
               // int written = mChannel.write(mBuffer);
                i++;
             //   mBuffer.position(mBuffer.position() + written);
                        
            }
            mBuffer.clear();
        } 
        private void writeHeader() throws IOException {
            mBuffer.putLong(ContextFileReader.MAGIC_COOKIE);
            mBuffer.putInt(ContextFileReader.MAJOR_VERSION);
            mBuffer.putInt(ContextFileReader.MINOR_VERSION);
            mNextFilePosition += 8 + 4 + 4;

    }*/
    }
    
   
		   


