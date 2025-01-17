/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.enea.jcarder.transactionalinterfaces;

//import com.enea.jcarder.transactionalinterfaces.bytebuffer.byteholder;
import com.enea.jcarder.util.Counter;
import dstm2.AtomicArray;
import dstm2.AtomicByteArray;
import dstm2.atomic;
import dstm2.Thread;
import dstm2.factory.Factory;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

/**
 *
 * @author navid
 */
public class bytebuffer {
    
   
    static Factory<bytebufferif> factory = Thread.makeFactory(bytebufferif.class);
//    public static Factory<byteholder> factory2 = Thread.makeFactory(byteholder.class);
   
    
    public bytebufferif mbuffer;
    
    public int capacity(){
        return mbuffer.getCapacity();
    }
    
    public int position(){
        return mbuffer.getPosition();
    }
    
    public bytebufferif flip(){
        mbuffer.setLimit(mbuffer.getPosition());
        mbuffer.setPosition(0);
        return mbuffer;
    }
    
    public final bytebufferif clear(){
        mbuffer.setPosition(0);
        mbuffer.setLimit(mbuffer.getCapacity());
        return mbuffer;
    }
    
    public final boolean hasRemaining(){
        return mbuffer.getPosition() < mbuffer.getLimit();
    }
    
    public final int remaining(){
        return mbuffer.getLimit() - mbuffer.getPosition();
    }
    
    public void put(byte value){

         AtomicByteArray ar = mbuffer.getByteHolderar();

         ar.set(mbuffer.getPosition(),value);
         mbuffer.setPosition(mbuffer.getPosition()+1);
         
    }
    
   
    
    public void put(ByteBuffer value){
        
        if (remaining() < value.remaining())
            throw new BufferOverflowException();
        
         /*AtomicArray<byteholder> ar = mbuffer.getByteHolder();
         while(value.hasRemaining()){
            byteholder bh = mbuffer.getByteHolder().get(mbuffer.getPosition());
            if ((bh = mbuffer.getByteHolder().get(mbuffer.getPosition())) ==  null){
                bh = factory2.create();
            }
            bh.setByte(value.get());
            ar.set(mbuffer.getPosition(),bh);
            mbuffer.setPosition(mbuffer.getPosition()+1);
         }*/
        AtomicByteArray ar = mbuffer.getByteHolderar();
         while(value.hasRemaining()){
            
            
            ar.set(mbuffer.getPosition(),Byte.valueOf(value.get()));
            mbuffer.setPosition(mbuffer.getPosition()+1);
         }
       // while(value.hasRemaining()){
        //    if (mbuffer.getByteHolder().get(mbuffer.getPosition()) ==  null)
         //       mbuffer.getByteHolder().set(mbuffer.getPosition(), factory2.create());
        //   mbuffer.getByteHolder.set((mbuffer.getPosition()), value.get());
         //   mbuffer.setPosition(mbuffer.getPosition()+1);
            //System.out.println("sss");
        //}
    }
    

    
    public bytebuffer allocateDirect(int capacity){
        System.out.println("allocate " + capacity);
        mbuffer = factory.create();
        mbuffer.setByteHolderar(new AtomicByteArray(Byte.class,capacity));
        mbuffer.setPosition(0);
        mbuffer.setLimit(capacity);
        mbuffer.setCapacity(capacity);
     //   for (int i=0; i<capacity; i++){
          //  ar.set(i, factory2.create());
        //}
      //  for (int i=0; i<capacity; i++)
        //    mbuffer.getByteHolder().set(i, factory2.create());
       // mbuffer.getByteHolder().set(0, factory2.create());
       // mbuffer.getByteHolder().get(0).setByte((byte)2);
        return this;
    }
    
    public byte[] getBytes(){
     //   System.out.println("getbytes");
        int length = remaining();
        byte[] result = new byte[length];
        int i = 0;
       /* while (hasRemaining()) {
            result[i] = mbuffer.getByteHolder().get(mbuffer.getPosition()).getByte();
            mbuffer.setPosition(mbuffer.getPosition()+1);
            i++;
        }*/
        while (hasRemaining()) {
            result[i] =  mbuffer.getByteHolderar().get(mbuffer.getPosition()).byteValue();
            mbuffer.setPosition(mbuffer.getPosition()+1);
            i++;
        }
        return result;
    }
     
     @atomic public interface bytebufferif{
        int getLimit();
        void setLimit(int value);
        int getPosition();
        void setPosition(int value);
        int getCapacity();
        void setCapacity(int value);
        AtomicByteArray getByteHolderar();
        void setByteHolderar(AtomicByteArray bytes);

     }
 
     
    

}
