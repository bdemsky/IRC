/*
 * Adapter.java
 *
 * Copyright 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, U.S.A.  All rights reserved.  
 * 
 * Sun Microsystems, Inc. has intellectual property rights relating to
 * technology embodied in the product that is described in this
 * document.  In particular, and without limitation, these
 * intellectual property rights may include one or more of the
 * U.S. patents listed at http://www.sun.com/patents and one or more
 * additional patents or pending patent applications in the U.S. and
 * in other countries.
 * 
 * U.S. Government Rights - Commercial software.
 * Government users are subject to the Sun Microsystems, Inc. standard
 * license agreement and applicable provisions of the FAR and its
 * supplements.  Use is subject to license terms.  Sun, Sun
 * Microsystems, the Sun logo and Java are trademarks or registered
 * trademarks of Sun Microsystems, Inc. in the U.S. and other
 * countries.  
 * 
 * This product is covered and controlled by U.S. Export Control laws
 * and may be subject to the export or import laws in other countries.
 * Nuclear, missile, chemical biological weapons or nuclear maritime
 * end uses or end users, whether direct or indirect, are strictly
 * prohibited.  Export or reexport to countries subject to
 * U.S. embargo or to entities identified on U.S. export exclusion
 * lists, including, but not limited to, the denied persons and
 * specially designated nationals lists is strictly prohibited.
 */
package dstm2.file.factory;

import dstm2.ContentionManager;
import dstm2.Transaction;
import dstm2.exceptions.AbortedException;
import dstm2.exceptions.PanicException;
import dstm2.exceptions.SnapshotException;
import dstm2.factory.Copyable;
import dstm2.factory.Factory;
import dstm2.Thread;
import dstm2.factory.Releasable;
import dstm2.factory.Snapable;

import dstm2.factory.shadow.RecoverableFactory;
import java.io.File;
import java.io.RandomAccessFile;
import java.lang.Class;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

/**
 * Obstruction-free atomic object implementation. Visible reads.
 * Support snapshots and early release.
 * @author Navid Farri
 */
public class Adapter {

    //public HashMap<Integer,BlockLock> lockmap;  
    public HashMap lockmap;
    //public AtomicLong commitedoffset;
    public AtomicLong commitedfilesize;
    private Transaction writer;
   // protected AtomicInteger version = new AtomicInteger(0);
    //public ReentrantLock lock;

    public Transaction getWriter() {
        return writer;
    }

    public void setWriter(Transaction writer) {
        this.writer = writer;
    }
    

    public Adapter() {
       // version.set(0);
      //  lock = new ReentrantLock();
        writer = null;
        lockmap = new HashMap();
//        commitedoffset.set(0);
    }

   /* public Adapter(Adapter adapter) {
        version.set(adapter.version.get());
        lock = adapter.lock;
        writer = adapter.writer;
        lockmap = adapter.lockmap;
    }*/
    /*
     * Creates a new instance of Adapter
     */
    //protected Factory<T> factory;
  /*
    protected AtomicReference<Locator> start;
    
    public AtomicReference<Locator> getStart() {
    return start;
    }
    
    public void setStart(AtomicReference<Locator> start) {
    this.start = start;
    }
     */
    /*  public void onRead(){
    
    try {
    Thread.onCommitOnce( new Runnable() {
    public void run() {
    /// commit the changes to the actual file, meanwhile the memory blocks would be owned by this
    /// transaction so no change could take place regarding those, this need not be done atomically
    }
    });
    
    Thread.onAbortOnce( new Runnable() {
    public void run() {
    //// nothing no-op
    }
    });
    
    Transaction me  = Thread.getTransaction();
    Locator oldLocator = start.get();
    Copyable version = oldLocator.fastPath(me);
    
    ContentionManager manager = Thread.getContentionManager();
    Locator newLocator = new Locator(me, (Copyable) new CopyableFileFactory());
    version = (Copyable) newLocator.newVersion;
    while (true) {
    oldLocator.writePath(me, manager, newLocator);
    if (!me.isActive()) {
    throw new AbortedException();
    }
    
    if (Adapter.this.start.compareAndSet(oldLocator, newLocator)) {
    return;
    }
    oldLocator = Adapter.this.start.get();
    }
    } catch (IllegalAccessException e) {
    throw new PanicException(e);
    } catch (InvocationTargetException e) {
    throw new PanicException(e);
    }
    }
    
    public void onWrite(){
    try {
    T version = (T) start.get().newVersion;
    final Method method = version.getClass().getMethod(methodName);
    return new Adapter.Getter<V>() {
    public V call() {
    try {
    Transaction me  = Thread.getTransaction();
    Locator oldLocator = Adapter.this.start.get();
    T version = (T) oldLocator.fastPath(me);
    if (version == null) {
    ContentionManager manager = Thread.getContentionManager();
    Locator newLocator = new Locator();
    while (true) {
    oldLocator.readPath(me, manager, newLocator);
    if (Adapter.this.start.compareAndSet(oldLocator, newLocator)) {
    version = (T) newLocator.newVersion;
    break;
    }
    oldLocator = start.get();
    }
    if (!me.isActive()) {
    throw new AbortedException();
    }
    }
    return (V)method.invoke(version);
    } catch (SecurityException e) {
    throw new PanicException(e);
    } catch (IllegalAccessException e) {
    throw new PanicException(e);
    } catch (InvocationTargetException e) {
    throw new PanicException(e);
    }
    }};
    } catch (NoSuchMethodException e) {
    throw new PanicException(e);
    }
    }
    
    public void release() {
    Transaction me  = Thread.getTransaction();
    Locator oldLocator = this.start.get();
    T version = (T) oldLocator.fastPath(me);
    if (version == null) {
    ContentionManager manager = Thread.getContentionManager();
    Locator newLocator = new Locator();
    version = (T) newLocator.newVersion;
    while (true) {
    oldLocator.releasePath(me, manager, newLocator);
    if (this.start.compareAndSet(oldLocator, newLocator)) {
    break;
    }
    oldLocator = this.start.get();
    }
    if (!me.isActive()) {
    throw new AbortedException();
    }
    }
    return;
    }
    
    public T snapshot() {
    Transaction me  = Thread.getTransaction();
    Locator oldLocator = this.start.get();
    T version = (T) oldLocator.fastPath(me);
    if (version == null) {
    ContentionManager manager = Thread.getContentionManager();
    return (T)oldLocator.snapshot(me, manager);
    } else {
    return version;
    }
    }
    
    public void validate(T snap) {
    if (snap != snapshot()) {
    throw new SnapshotException();
    }
    }
    
    public void upgrade(T snap) {
    Transaction me  = Thread.getTransaction();
    Locator oldLocator = this.start.get();
    T version = (T) oldLocator.fastPath(me);
    if (version != null) {
    if (version != snap) {
    throw new SnapshotException();
    } else {
    return;
    }
    }
    ContentionManager manager = Thread.getContentionManager();
    Locator newLocator = new Locator(me, (Copyable)factory.create());
    while (true) {
    oldLocator.writePath(me, manager, newLocator);
    if (!me.isActive()) {
    throw new AbortedException();
    }
    if (snap != newLocator.oldVersion) {
    throw new SnapshotException();
    }
    if (this.start.compareAndSet(oldLocator, newLocator)) {
    return;
    }
    oldLocator = this.start.get();
    }
    }*/
}

