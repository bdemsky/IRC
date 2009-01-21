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

package com.enea.jcarder.agent;

import static com.enea.jcarder.common.contexts.ContextFileReader.EVENT_DB_FILENAME;
import static com.enea.jcarder.common.contexts.ContextFileReader.CONTEXTS_DB_FILENAME;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

//import net.jcip.annotations.ThreadSafe;

import com.enea.jcarder.common.LockingContext;
import com.enea.jcarder.common.contexts.ContextFileWriter;
import com.enea.jcarder.common.contexts.ContextWriterIfc;
import com.enea.jcarder.common.events.EventFileWriter;
import com.enea.jcarder.common.events.LockEventListenerIfc;
import com.enea.jcarder.transactionalinterfaces.trEventListener;
import com.enea.jcarder.util.Counter;
//import com.enea.jcarder.util.TransactionalCounter;
import com.enea.jcarder.util.TransactionalCounter;
import com.enea.jcarder.util.logging.Logger;

import dstm2.Thread;

//@ThreadSafe
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.logging.Level;
final class EventListener implements EventListenerIfc {
    trEventListener listener;
    private final ThreadLocalEnteredMonitors mEnteredMonitors;
    private final LockEventListenerIfc mLockEventListener;
    private final LockIdGenerator mLockIdGenerator;
    private final LockingContextIdCache mContextCache;
    private final Logger mLogger;
    //private final Counter mNumberOfEnteredMonitors;
    private final TransactionalCounter trmNumberOfEnteredMonitors;

    public static EventListener create(Logger logger, File outputdir)
    throws IOException {
        EventFileWriter eventWriter =
            new EventFileWriter(logger,
                                new File(outputdir, EVENT_DB_FILENAME));
        ContextFileWriter contextWriter =
            new ContextFileWriter(logger,
                                  new File(outputdir, CONTEXTS_DB_FILENAME));
        return new EventListener(logger, eventWriter, contextWriter);
    }

    public EventListener(Logger logger,
                         LockEventListenerIfc lockEventListener,
                         /*ContextWriterIfc contextWriter*/ContextFileWriter contextWriter) {
        
          mLogger = logger;
        mEnteredMonitors = new ThreadLocalEnteredMonitors();
        mLockEventListener = lockEventListener;
        mLockIdGenerator = new LockIdGenerator(mLogger, contextWriter);
        mContextCache = new LockingContextIdCache(mLogger, contextWriter);
       // mNumberOfEnteredMonitors =
       //     new Counter("Entered Monitors", mLogger, 100000);
        
        trmNumberOfEnteredMonitors =
            new TransactionalCounter("Entered Monitors", mLogger, 100000);
        
  /*      listener = new trEventListener();
        listener.atmoicfileds = trEventListener.factory.create();
        this.listener.atmoicfileds.setCCByteBuffer(this.getMContextCache().getMContextWriter().getMbuff().mbuffer);
        listener.atmoicfileds.setCCPosiiton(getMContextCache().getMContextWriter().getTest().pos);
        listener.atmoicfileds.setCCShutdown(getMContextCache().getMContextWriter().getShutdownHookExecuted().boolif);

      
        listener.atmoicfileds.setHMap1Capacity(getMContextCache().getMCache().capacity);
        listener.atmoicfileds.setHMap1Position(getMContextCache().getMCache().position);
        listener.atmoicfileds.setHMap1Values(getMContextCache().getMCache().values);
       
        listener.atmoicfileds.setELNumberofMonitors(getTrmNumberOfEnteredMonitors().mValue);
      
        listener.atmoicfileds.setLIGByteBuffer(getMLockIdGenerator().getMContextWriter().getMbuff().mbuffer);
        listener.atmoicfileds.setLIGPosiiton(getMLockIdGenerator().getMContextWriter().getTest().pos);
        listener.atmoicfileds.setLIGShutdown(getMLockIdGenerator().getMContextWriter().getShutdownHookExecuted().boolif);
      
        listener.atmoicfileds.setHMap2Capacity(getMLockIdGenerator().getMIdMap().capacity);
        listener.atmoicfileds.setHMap2Position(getMLockIdGenerator().getMIdMap().position);
        listener.atmoicfileds.setHMap2Values(getMLockIdGenerator().getMIdMap().values);
      
        listener.atmoicfileds.setEFWByteBuffer(((EventFileWriter)this.getMLockEventListener()).getMbuff().mbuffer);
        listener.atmoicfileds.setEFWShutdown(((EventFileWriter)this.getMLockEventListener()).getShutdownHookExecuted().boolif);
        listener.atmoicfileds.setEFWCounter(((EventFileWriter)this.getMLockEventListener()).getTrmWrittenLockEvents().mValue);*/
      
      
    }
    
    
      public EventListener(EventListener other) {
        this.mLogger = other.mLogger;
        this.mEnteredMonitors = other.mEnteredMonitors;
        this.trmNumberOfEnteredMonitors = other.trmNumberOfEnteredMonitors;
        //this.trmNumberOfEnteredMonitors.mValue.setPosition(other.trmNumberOfEnteredMonitors.mValue.getPosition());
        this.mContextCache = new LockingContextIdCache(other.mContextCache);
        this.mLockIdGenerator =new LockIdGenerator(other.mLockIdGenerator);
        this.mLockEventListener = new EventFileWriter((EventFileWriter) (other.mLockEventListener));
    }

    public void beforeMonitorEnter(Object monitor, LockingContext context)
    throws Exception {
        mLogger.finest("EventListener.beforeMonitorEnter");
        Iterator<EnteredMonitor> iter = mEnteredMonitors.getIterator();
        while (iter.hasNext()) {
            Object previousEnteredMonitor = iter.next().getMonitorIfStillHeld();
            if (previousEnteredMonitor == null) {
                iter.remove();
            } else if (previousEnteredMonitor == monitor) {
                return; // Monitor already entered.
            }
        }
        //enteringNewMonitor(monitor, context);
        enteringNewMonitor(monitor, context);
        
    }

 /*   private synchronized  void enteringNewMonitor(Object monitor,
                                                 LockingContext context)
    throws Exception {
        mNumberOfEnteredMonitors.increment();
        int newLockId = mLockIdGenerator.acquireLockId(monitor);
        System.out.println("monitor " + monitor.getClass());
        int newContextId = mContextCache.acquireContextId(context);
        
        EnteredMonitor lastMonitor = mEnteredMonitors.getFirst();
        if (lastMonitor != null) {
            java.lang.Thread performingThread = Thread.currentThread();
            mLockEventListener.onLockEvent(newLockId,
                                           newContextId,
                                           lastMonitor.getLockId(),
                                           lastMonitor.getLockingContextId(),
                                           performingThread.getId());
        }
        mEnteredMonitors.addFirst(new EnteredMonitor(monitor,
                                                     newLockId,
                                                     newContextId));
        
    }*/
    
    private void trenteringNewMonitor(Vector arguments)
    throws Exception {
        
        trmNumberOfEnteredMonitors.increment();
        final int newLockId = mLockIdGenerator.acquireLockId((Object)arguments.get(1));
        final int newContextId = mContextCache.acquireContextId((LockingContext)arguments.get(0));
        arguments.add(newLockId);
        arguments.add(newContextId);
        
        EnteredMonitor lastMonitor = mEnteredMonitors.getFirst();
        if (lastMonitor != null) {
            java.lang.Thread performingThread = Thread.currentThread();
            Vector args = new Vector();
            
            
            
            args.add(newLockId);
            args.add(newContextId);
            args.add(lastMonitor.getLockId());
            args.add(lastMonitor.getLockingContextId());
            args.add(performingThread.getId());
            
            mLockEventListener.tronLockEvent(args);
        }
      //  mEnteredMonitors.addFirst(new EnteredMonitor((Object)context.get(1),
      //                                               newLockId,
        //                                             newContextId));
        
    }
    
    
    
   private void enteringNewMonitor(Object monitor,
                                                 LockingContext context)
    throws Exception {
         final Vector arguments = new Vector();
         arguments.add(context);
         arguments.add(monitor);
         Thread.doIt(new Callable<Boolean>() {
          public Boolean call() throws Exception {
                trenteringNewMonitor(arguments);
                
                return true;
          }
        });
      //  System.out.println(arguments.size());
        mEnteredMonitors.addFirst(new EnteredMonitor(monitor, ((Integer)(arguments.get(2))).intValue(), ((Integer)(arguments.get(3))).intValue()));
        
        arguments.clear();
       
    }

    public LockingContextIdCache getMContextCache() {
        return mContextCache;
    }

    public ThreadLocalEnteredMonitors getMEnteredMonitors() {
        return mEnteredMonitors;
    }

    public LockEventListenerIfc getMLockEventListener() {
        return mLockEventListener;
    }

    public LockIdGenerator getMLockIdGenerator() {
        return mLockIdGenerator;
    }

    public Logger getMLogger() {
        return mLogger;
    }

    public TransactionalCounter getTrmNumberOfEnteredMonitors() {
        return trmNumberOfEnteredMonitors;
    }
   
   
   
   
   
  
}
