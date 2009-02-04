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

//import net.jcip.annotations.ThreadSafe;
import com.enea.jcarder.common.LockingContext;
import com.enea.jcarder.common.events.EventFileWriter;
import com.enea.jcarder.transactionalinterfaces.trEventListener;
import dstm2.AtomicSuperClass;
import java.util.concurrent.Callable;
import dstm2.Thread;
import java.util.HashMap;
import java.util.Vector;

/**
 * This class provides static methods that are supposed to be invoked directly
 * from the instrumented classes.
 */
//@ThreadSafe
public final class StaticEventListener{

    private StaticEventListener() { }
    //private static EventListenerIfc smListener;
    private static EventListenerIfc smListener;
    private static HashMap map= new HashMap();
    

    //public synchronized static void setListener(EventListenerIfc listener) {
      //  smListener = (EventListener)listener;
    //}
    
    public  static void trsetListener(Vector listener) {
        //smListener = ((EventListener)(arg.get(0)));
        smListener = (EventListenerIfc)listener.get(0);
    //  smListener = new EventListener((EventListener)(arg.get(0)));
   //   smListener.listener = new trEventListener();
     // smListener.listener.atmoicfileds = trEventListener.factory.create();
    //  smListener.listener.atmoicfileds = ((EventListener)(arg.get(0))).listener.atmoicfileds;
      
      /*smListener2.atmoicfileds.setCCByteBuffer(((EventListener)(arg.get(0))).getMContextCache().getMContextWriter().getMbuff().mbuffer);
      smListener2.atmoicfileds.setCCPosiiton(((EventListener)(arg.get(0))).getMContextCache().getMContextWriter().getTest().pos);
      smListener2.atmoicfileds.setCCShutdown(((EventListener)(arg.get(0))).getMContextCache().getMContextWriter().getShutdownHookExecuted().boolif);
      
      
      
      smListener2.atmoicfileds.setHMap1Capacity(((EventListener)(arg.get(0))).getMContextCache().getMCache().capacity);
      smListener2.atmoicfileds.setHMap1Position(((EventListener)(arg.get(0))).getMContextCache().getMCache().position);
      smListener2.atmoicfileds.setHMap1Values(((EventListener)(arg.get(0))).getMContextCache().getMCache().values);
      
      smListener2.atmoicfileds.setELNumberofMonitors(((EventListener)(arg.get(0))).getTrmNumberOfEnteredMonitors().mValue);
      
      smListener2.atmoicfileds.setLIGByteBuffer(((EventListener)(arg.get(0))).getMLockIdGenerator().getMContextWriter().getMbuff().mbuffer);
      smListener2.atmoicfileds.setLIGPosiiton(((EventListener)(arg.get(0))).getMLockIdGenerator().getMContextWriter().getTest().pos);
      smListener2.atmoicfileds.setLIGShutdown(((EventListener)(arg.get(0))).getMLockIdGenerator().getMContextWriter().getShutdownHookExecuted().boolif);
      
      smListener2.atmoicfileds.setHMap2Capacity(((EventListener)(arg.get(0))).getMLockIdGenerator().getMIdMap().capacity);
      smListener2.atmoicfileds.setHMap2Position(((EventListener)(arg.get(0))).getMLockIdGenerator().getMIdMap().position);
      smListener2.atmoicfileds.setHMap2Values(((EventListener)(arg.get(0))).getMLockIdGenerator().getMIdMap().values);
      
      smListener2.atmoicfileds.setEFWByteBuffer(((EventFileWriter)((EventListener)(arg.get(0))).getMLockEventListener()).getMbuff().mbuffer);
      smListener2.atmoicfileds.setEFWShutdown(((EventFileWriter)((EventListener)(arg.get(0))).getMLockEventListener()).getShutdownHookExecuted().boolif);
      smListener2.atmoicfileds.setEFWCounter(((EventFileWriter)((EventListener)(arg.get(0))).getMLockEventListener()).getTrmWrittenLockEvents().mValue);*/
      
    }
    
    public  static void setListener(EventListenerIfc listener) {
         final Vector arg = new Vector();
         arg.add(listener);
         Thread.doIt(new Callable<Boolean>() {
          public Boolean call() throws Exception {
                trsetListener(arg);
                return true;
          }
        });
    }
        
   // }

    public static EventListenerIfc trgetListener() {
    /*  
        ((EventListener)smListener).getMContextCache().getMCache().capacity.getPosition();
        ((EventListener)smListener).getMContextCache().getMCache().position.getPosition();
        ((EventListener)smListener).getMContextCache().getMCache().values.getValues();
        ((EventListener)smListener).getTrmNumberOfEnteredMonitors().mValue.getPosition();
        ((EventListener)smListener).getMLockIdGenerator().getMIdMap().capacity.getPosition();
        ((EventListener)smListener).getMLockIdGenerator().getMIdMap().position.getPosition();
        ((EventListener)smListener).getMLockIdGenerator().getMIdMap().values.getValues();*/
        return smListener;
    }
    
    public  static EventListenerIfc getListener() {
        return Thread.doIt(new Callable<EventListenerIfc>() {
          public EventListenerIfc call() throws Exception {
                return trgetListener();
          }
        });
        
    }

    /**
     * This method is expected to be called from the instrumented classes.
     *
     * @param monitor
     *            The monitor object that was acquired. This value is allowed to
     *            be null.
     *
     * @param lockReference
     *            A textual description of how the lock object was addressed.
     *            For example: "this", "com.enea.jcarder.Foo.mBar" or
     *            "com.enea.jcarder.Foo.getLock()".
     *
     * @param methodWithClass
     *            The method that acquired the lock, on the format
     *            "com.enea.jcarder.Foo.bar()".
     */
    public static void beforeMonitorEnter(Object monitor,
                                          String lockReference,
                                          String methodWithClass) {
        try {
            EventListenerIfc listener = getListener();
            if (listener != null) {
                final LockingContext lockingContext =
                    new LockingContext(Thread.currentThread(),
                                       lockReference,
                                       methodWithClass);
                listener.beforeMonitorEnter(monitor,
                                            lockingContext);
            }
        } catch (Throwable t) {
            handleError(t);
        }
       // System.out.println("here finito");
    }

    private static void handleError(Throwable t) {
        setListener(null);
        t.printStackTrace();
    }
}
