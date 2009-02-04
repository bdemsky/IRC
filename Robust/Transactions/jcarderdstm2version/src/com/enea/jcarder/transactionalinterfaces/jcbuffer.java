/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.enea.jcarder.transactionalinterfaces;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

/**
 *
 * @author navid
 */
public class jcbuffer {

    jcbytebufferif mbuffer;
    
    public int capacity(){
        return mbuffer.getCapacity();
    }
    
    public int position(){
        return mbuffer.getPosition();
    }
    
    public jcbytebufferif flip(){
        mbuffer.setLimit(mbuffer.getPosition());
        mbuffer.setPosition(0);
        return mbuffer;
    }
    
    public final jcbytebufferif clear(){
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

         jcbyteholder[] ar = mbuffer.getByteHolder();
         jcbyteholder bh;
         if (mbuffer.getByteHolder()[mbuffer.getPosition()] ==  null){
            bh = new jcbyteholder();
            
         }
         else 
             bh = mbuffer.getByteHolder()[mbuffer.getPosition()];
         bh.setByte(value);
         ar[mbuffer.getPosition()] = bh;
         mbuffer.setPosition(mbuffer.getPosition()+1);
         
    }
    
   
    
    public void put(ByteBuffer value){
        
        if (remaining() < value.remaining())
            throw new BufferOverflowException();
        
         jcbyteholder[] ar = mbuffer.getByteHolder();
         while(value.hasRemaining()){
            jcbyteholder bh = mbuffer.getByteHolder()[mbuffer.getPosition()];
            if (mbuffer.getByteHolder()[mbuffer.getPosition()] ==  null){
                bh = new jcbyteholder();
            }
            else 
                bh = mbuffer.getByteHolder()[mbuffer.getPosition()];
            bh.setByte(value.get());
            ar[mbuffer.getPosition()] = bh;
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
    

    
    public jcbuffer allocateDirect(int capacity){
        System.out.println("allocate " + capacity);
        mbuffer = new jcbytebufferif();
        mbuffer.setByteHolder(new jcbyteholder[capacity]);
        mbuffer.setPosition(0);
        mbuffer.setLimit(capacity);
        mbuffer.setCapacity(capacity);
        jcbyteholder[] ar = mbuffer.getByteHolder();
     //   for (int i=0; i<capacity; i++){
          //  ar.set(i, factory2.create());
        //}
      //  for (int i=0; i<capacity; i++)
      //      mbuffer.getByteHolder().set(i, factory2.create());
       // mbuffer.getByteHolder().set(0, factory2.create());
       // mbuffer.getByteHolder().get(0).setByte((byte)2);
        return this;
    }
    
    public byte[] getBytes(){
     //   System.out.println("getbytes");
        int length = remaining();
        byte[] result = new byte[length];
        int i = 0;
        while (hasRemaining()) {
            result[i] = mbuffer.getByteHolder()[mbuffer.getPosition()].getByte();
            mbuffer.setPosition(mbuffer.getPosition()+1);
            i++;
        }
        return result;
    }
    
       class jcbytebufferif{
        jcbyteholder[] bytes;
        int limit;
        int position;
        int capacity;
        int getLimit(){
            return limit;
        }
        void setLimit(int value){
            limit = value;
        }
        int getPosition(){
            return position;
        }
        void setPosition(int value){
            position = value;
        }
        int getCapacity(){
            return capacity;
        }
        void setCapacity(int value){
            capacity = value;
        }
        jcbyteholder[] getByteHolder(){
            return bytes;
        }
        void setByteHolder(jcbyteholder[] bytes){
            this.bytes =  bytes;
        }
        
      //  AtomicArray<Byte> getByteHolderar();
      //  void setByteHolderar(Byte[] bytes);
     }
     
     class jcbyteholder{
         byte val;
         byte getByte(){
             return val;
         };
         void setByte(byte value){
             val = value;
         }
     }
}
