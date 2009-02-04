/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.enea.jcarder.transactionalinterfaces;

/**
 *
 * @author navid
 */
/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */



import dstm2.AtomicArray;
import dstm2.atomic;
import dstm2.Thread;
import dstm2.factory.Factory;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

/**
 *
 * @author navid
 */
public class Bool{
    
   
    static Factory<boolif> factory = Thread.makeFactory(boolif.class);
    public boolif boolif;
    
    public void init(){
        boolif = factory.create();
        boolif.setValue(false);
    }
    
    public void set(boolean v){
        boolif.setValue(v);
    }
    
    public boolean isTrue(){
        return boolif.getValue();
    }
     
    
     
     @atomic public interface boolif{
         boolean getValue();
         void setValue(boolean value);
         
       //  void setLong(long value);   
     }
     
    

}
