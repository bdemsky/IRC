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

import java.io.IOException;
//import net.jcip.annotations.NotThreadSafe;

import com.enea.jcarder.common.Lock;
import com.enea.jcarder.common.contexts.ContextFileWriter;
import com.enea.jcarder.common.contexts.ContextWriterIfc;
//import com.enea.jcarder.transactionalinterfaces.trHashMap;
import com.enea.jcarder.util.IdentityWeakHashMap;
import com.enea.jcarder.util.logging.Logger;
import dstm2.util.IntHashMap;
import com.enea.jcarder.transactionalinterfaces.Intif;
//import java.util.HashMap;
import sun.misc.VM;

/**
 * This class is responsible for generating unique IDs for objects.
 *
 * We cannot use System.identityHashCode(o) since it returns random numbers,
 * which are not guaranteed to be unique.
 *
 * TODO Add basic tests for this class.
 */
//@NotThreadSafe
final class LockIdGenerator {
    //private final IdentityWeakHashMap<Integer> mIdMap;
   // private final HashMap<Intif.positionif> mIdMap;
    private final IntHashMap mIdMap;
    //private final ContextWriterIfc mContextWriter;
    private final ContextFileWriter mContextWriter;
    private final Logger mLogger;

    /**
     * Create a LockIdGenerator backed by a ContextWriterIfc
     */
    public LockIdGenerator(LockIdGenerator other){
        this.mIdMap = other.mIdMap;
       // this.mIdMap.values.setValues(other.mIdMap.values.getValues());
       // this.mIdMap.position.setPosition(other.mIdMap.position.getPosition());
       // this.mIdMap.capacity.setPosition(other.mIdMap.capacity.getPosition());
        //this.mIdMap.keys = other.mIdMap.keys;
        this.mContextWriter = other.mContextWriter;
        this.mLogger = other.mLogger;
        
        
    }
    
    public LockIdGenerator(Logger logger, /*ContextWriterIfc writer*/ContextFileWriter writer) {
        mLogger = logger;
       // mIdMap = new IdentityWeakHashMap<Integer>();
//        mIdMap = new HashMap<Intif.positionif>();
        mIdMap = new IntHashMap();
        mContextWriter = writer;

    }

    /**
     * Return an ID for a given object.
     *
     * If the method is invoked with the same object instance more than once it
     * is guaranteed that the same ID is returned each time. Two objects that
     * are not identical (as compared with "==") will get different IDs.
     */
    public int acquireLockId(Object o) throws IOException {
        assert o != null;
        Integer id = (Integer)mIdMap.get(System.identityHashCode(o));
     //   if (mIdMap.get(HashMap.hash(o)) == null){
        if (id == null){
            id = mContextWriter.writeLock(new Lock(o));        
           // Intif.positionif tmp = Intif.factory.create();
           // tmp.setPosition(id);
           // mIdMap.put(HashMap.hash(o), tmp);
             //mIdMap.put(o, tmp);
            mIdMap.put(System.identityHashCode(o), id);
            mLogger.finest("Created new lock ID: " + id);        
        }
       // else 
     //       id = mIdMap.get(HashMap.hash(o)).getPosition();
       
      /* if (id == null) {
            id = mContextWriter.writeLock(new Lock(o));
            tmp.setPosition(id);
            mIdMap.put(System.identityHashCode(o), tmp);
            mLogger.finest("Created new lock ID: " + id);
        }*/
        return id;
    }

    public ContextFileWriter getMContextWriter() {
        return mContextWriter;
    }



    public Logger getMLogger() {
        return mLogger;
    }
    
    
    
}
