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

package com.enea.jcarder.common.events;

import TransactionalIO.core.TransactionalFile;
import TransactionalIO.exceptions.GracefulException;
import com.enea.jcarder.transactionalinterfaces.Bool;
import com.enea.jcarder.transactionalinterfaces.bytebuffer;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
//import net.jcip.annotations.ThreadSafe;

import com.enea.jcarder.util.Counter;
import com.enea.jcarder.util.TransactionalCounter;
import com.enea.jcarder.util.logging.Logger;

import java.util.concurrent.Callable;
import static com.enea.jcarder.common.events.EventFileReader.EVENT_LENGTH;

import dstm2.Thread;

//@ThreadSafe
import java.util.Vector;
public final class EventFileWriter implements LockEventListenerIfc {
//    private final ByteBuffer mBuffer =
 //   ByteBuffer.allocateDirect(EVENT_LENGTH * 1024);
 //   private final FileChannel mFileChannel;
    private final Logger mLogger;
//    private final Counter mWrittenLockEvents;
//    private boolean mShutdownHookExecuted = false;
    
    private bytebuffer mbuff;
    private Bool ShutdownHookExecuted; 
    private TransactionalFile traf; 
    private TransactionalCounter trmWrittenLockEvents;

    public EventFileWriter(EventFileWriter other){
        this.ShutdownHookExecuted = other.ShutdownHookExecuted;
        this.ShutdownHookExecuted.boolif.setValue(other.ShutdownHookExecuted.boolif.getValue());
        this.mLogger = other.mLogger;
        this.mbuff = other.mbuff;
     //   this.mbuff.mbuffer.setByteHolder(other.mbuff.mbuffer.getByteHolder());
        this.traf = other.traf;
        this.trmWrittenLockEvents = other.trmWrittenLockEvents;
        this.trmWrittenLockEvents.mValue.setPosition(other.trmWrittenLockEvents.mValue.getPosition());
    }
    
    public EventFileWriter(Logger logger, File file) throws IOException {
        
        
        
        mLogger = logger;
        mLogger.info("Opening for writing: " + file.getAbsolutePath());
        RandomAccessFile raFile = new RandomAccessFile(file, "rw");
        raFile.setLength(0);
    //    mFileChannel = raFile.getChannel();
    //    mWrittenLockEvents = new Counter("Written Lock Events",
       //                                  mLogger,
        //                                 100000);
        
        mbuff = new bytebuffer();
        mbuff.allocateDirect(8192);
        traf = new TransactionalFile(file.getAbsolutePath(), "rw");
        ShutdownHookExecuted = new Bool();
        ShutdownHookExecuted.init();
        trmWrittenLockEvents = new TransactionalCounter("Written Lock Events",
                                         mLogger,
                                         100000);
        
        writeHeader();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() { shutdownHook(); }
        });
    }

  /*  private void writeHeader() throws IOException {
        mBuffer.putLong(EventFileReader.MAGIC_COOKIE);
        mBuffer.putInt(EventFileReader.MAJOR_VERSION);
        mBuffer.putInt(EventFileReader.MINOR_VERSION);
        writeBuffer();
    }*/
    
    private void writeHeader() throws IOException {
        ByteBuffer mBuffer = ByteBuffer.allocateDirect(16);
        mBuffer.putLong(EventFileReader.MAGIC_COOKIE);
        mBuffer.putInt(EventFileReader.MAJOR_VERSION);
        mBuffer.putInt(EventFileReader.MINOR_VERSION);
        mBuffer.rewind();
        
        /*for (int i=0; i<16; i++){
            mbuff.put(mBuffer.get());
        }*/
         
        mbuff.put(mBuffer);
        writeBuffer();
    }

    /*public synchronized void onLockEvent(int lockId,
                                         int lockingContextId,
                                         int lastTakenLockId,
                                         int lastTakenLockingContextId,
                                         long threadId) throws IOException {
        
        mBuffer.putInt(lockId);
        mBuffer.putInt(lockingContextId);
        mBuffer.putInt(lastTakenLockId);
        mBuffer.putInt(lastTakenLockingContextId);
        mBuffer.putLong(threadId);
        mWrittenLockEvents.increment();
        if (mBuffer.remaining() < EVENT_LENGTH || mShutdownHookExecuted) {
            writeBuffer();
        }
    }*/
    
    public void tronLockEvent(Vector arg) throws IOException {
       
            ByteBuffer mBuffer = ByteBuffer.allocateDirect(24);
            mBuffer.putInt((Integer)arg.get(0));
            mBuffer.putInt((Integer)arg.get(1));
            mBuffer.putInt((Integer)arg.get(2));
            mBuffer.putInt((Integer)arg.get(3));
            mBuffer.putLong((Long)arg.get(4));
            mBuffer.rewind();
        
            //for (int i=0; i<24; i++){
              //  mbuff.put(mBuffer.get());
            //}
            
            mbuff.put(mBuffer);
        
            trmWrittenLockEvents.increment();
            if (mbuff.remaining() < EVENT_LENGTH || ShutdownHookExecuted.isTrue()) {
                writeBuffer();
            }
    }
            
    public void onLockEvent(int lockId,
                                         int lockingContextId,
                                         int lastTakenLockId,
                                         int lastTakenLockingContextId,
                                         long threadId) throws IOException{
            
        
            final Vector arg = new Vector();
            arg.add(Integer.valueOf(lockId));
            arg.add(Integer.valueOf(lockingContextId));
            arg.add(Integer.valueOf(lastTakenLockId));
            arg.add(Integer.valueOf(lastTakenLockingContextId));
            arg.add(Long.valueOf(threadId));
            Thread.doIt(new Callable<Boolean>() {
                
            public Boolean call() throws IOException {
                tronLockEvent(arg);
                return true;
            }
            
          });
        
    }        

   /* private void writeBuffer() throws IOException {
        mBuffer.flip();
        mFileChannel.write(mBuffer);
        while (mBuffer.hasRemaining()) {
            Thread.yield();
            mFileChannel.write(mBuffer);
        }
        mBuffer.clear();
    }*/
    
    
    private void writeBuffer() throws IOException {
        mbuff.flip();
        traf.write(mbuff.getBytes());
        while (mbuff.hasRemaining()) {
            Thread.yield();
            traf.write(mbuff.getBytes());
        }
        mbuff.clear();
    }


    /*public synchronized void close() throws IOException {
        
        writeBuffer();
        mFileChannel.close();
    }*/
    
     public void close() throws IOException {
      //  System.out.println("clo");
        try{
            Thread.doIt(new Callable<Boolean>() {
                
            public Boolean call() {
                try {
                  //  System.out.println(Thread.currentThread() + " closeaborted in committing");
                      writeBuffer();
                      traf.close();  
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

  /*  private synchronized void shutdownHook() {
        try {
            if (mFileChannel.isOpen()) {
                writeBuffer();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        mShutdownHookExecuted = true;
    }*/
    
    
   private void shutdownHook() {
        System.out.println(Thread.currentThread() + " ashut ddddddborted in committing");
        try{ 
        Thread.doIt(new Callable<Boolean>() {
	  
            public Boolean call() {
            
                try {
                    if (traf.file.getChannel().isOpen()) {
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
           System.out.println(Thread.currentThread() + " shut graceful exc");
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

    public TransactionalFile getTraf() {
        return traf;
    }

    public void setTraf(TransactionalFile traf) {
        this.traf = traf;
    }

    public TransactionalCounter getTrmWrittenLockEvents() {
        return trmWrittenLockEvents;
    }

    public void setTrmWrittenLockEvents(TransactionalCounter trmWrittenLockEvents) {
        this.trmWrittenLockEvents = trmWrittenLockEvents;
    }
   
   
}
