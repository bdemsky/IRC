/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.enea.jcarder.transactionalinterfaces;

import com.enea.jcarder.util.Counter;
import com.enea.jcarder.util.TransactionalCounter;
import dstm2.atomic;
import dstm2.Thread;
import dstm2.factory.Factory;

/**
 *
 * @author navid
 */
public class trEventListener{
    public static Factory<trAtomicFieldsEventListener> factory = Thread.makeFactory(trAtomicFieldsEventListener.class); 
    public trAtomicFieldsEventListener atmoicfileds;
    
    
    
    @atomic public interface trAtomicFieldsEventListener {
    public bytebuffer.bytebufferif getCCByteBuffer();
    public void setCCByteBuffer(bytebuffer.bytebufferif val);
    public Bool.boolif getCCShutdown();
    public void setCCShutdown(Bool.boolif val);
    public Intif.positionif getCCPosiiton();
    public void setCCPosiiton(Intif.positionif val);
    
    public Intif.positionif getHMap1Capacity();
    public void setHMap1Capacity(Intif.positionif val);
    public Intif.positionif getHMap1Position();
    public void setHMap1Position(Intif.positionif val);
    public trHashMap.valueHolder getHMap1Values();
    public void setHMap1Values(trHashMap.valueHolder val);
    
    
    public Intif.positionif getELNumberofMonitors();
    public void setELNumberofMonitors(Intif.positionif val);
    
    public bytebuffer.bytebufferif getLIGByteBuffer();
    public void setLIGByteBuffer(bytebuffer.bytebufferif val);
    public Bool.boolif getLIGShutdown();
    public void setLIGShutdown(Bool.boolif val);
    public Intif.positionif getLIGPosiiton();
    public void setLIGPosiiton(Intif.positionif val);
    
   public Intif.positionif getHMap2Capacity();
    public void setHMap2Capacity(Intif.positionif val);
    public Intif.positionif getHMap2Position();
    public void setHMap2Position(Intif.positionif val);
    public trHashMap.valueHolder getHMap2Values();
    public void setHMap2Values(trHashMap.valueHolder val);

    
    public bytebuffer.bytebufferif getEFWByteBuffer();
    public void setEFWByteBuffer(bytebuffer.bytebufferif val);
    public Bool.boolif getEFWShutdown();
    public void setEFWShutdown(Bool.boolif val);
    public Intif.positionif getEFWCounter();
    public void setEFWCounter(Intif.positionif val);
    
    
    }
}


