/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.enea.jcarder.transactionalinterfaces;

import dstm2.AtomicArray;
import dstm2.AtomicIntArray;
import dstm2.AtomicSuperClass;
import dstm2.atomic;
import dstm2.factory.Factory;
import dstm2.Thread;
import java.util.HashMap;
import java.util.Map.Entry;

/**
 *
 * @author navid
 */
public class trHashMap implements Intif.positionif{
    
    
    public static Factory<Intif.positionif> factory = Thread.makeFactory(Intif.positionif.class); 
    public static Factory<valueHolder> factory2 = Thread.makeFactory(valueHolder.class);
    public static Factory<trLockingContext.holder> factory3 = Thread.makeFactory(trLockingContext.holder.class);
    public static Factory<keyHolder> factory4 = Thread.makeFactory(keyHolder.class);
    public valueHolder values;
    public Object[] keys;
    AtomicIntArray intkeys = new AtomicIntArray(Integer.class, 10);
    public keyHolder keys2;
    public Intif.positionif position;
    public Intif.positionif capacity;
    
    public trHashMap() {
        keys = new Object[15];
        position = factory.create();
        capacity = factory.create();
        values = factory2.create();
        keys2 = factory4.create();
        
        position.setPosition(0);
        capacity.setPosition(15);
        values.setValues(new AtomicIntArray(Integer.class, 15));
        keys2.setKeys(new AtomicArray<trLockingContext.holder>(trLockingContext.holder.class, 15));
    }
    
    
    int indexFor(int h, int length) {
                return h & (length - 1);
    }
    
    public void put(Object key, int value){
      //  int hash = System.identityHashCode(key);
    /*    System.out.println("key " + key + " value " + value);
        if (map.containsKey(key)){
            System.out.println("-------hmap-----------------");
                System.out.println("value replaced " + key);
                System.out.println("-------hmap-------------------");
        }
        map.put(key, value);*/
                
            
        for (int i=0; i<position.getPosition(); i++){
      //      System.out.println("Key " + key + " value " + value + " keys[i] " +keys[i]);
            if ((key.equals(keys[i])) || (key == keys[i])){
                System.out.println("-----------tr-------------");
                System.out.println("value replaced " + key);
                System.out.println("-------------tr-------------");
                values.getValues().set(i, value);
                keys[i] = key;
                return;
            }
        }
        if (position.getPosition()  == capacity.getPosition()){
            System.out.println("expanding");
            capacity.setPosition(capacity.getPosition()*2);
            AtomicIntArray ar = new AtomicIntArray(Integer.class, capacity.getPosition());
            Object[] tmp = new Object[capacity.getPosition()];
            
            for (int i=0; i<capacity.getPosition(); i++){
                ar.set(i, values.getValues().get(i));
                tmp[i] = keys[i];
            }
            keys = new Object[capacity.getPosition()];
            System.arraycopy(tmp, 0, keys, 0, capacity.getPosition()/2);
            values.setValues(ar);
        }
        
        keys[position.getPosition()] = key;
        values.getValues().set(position.getPosition(), value);
        position.setPosition(position.getPosition()+1);
        System.out.println("tr key " + keys[position.getPosition()-1] + " tr value " + values.getValues().get(position.getPosition()-1));
    }
    
    public Integer get(Object key){
       boolean flag  = false;
       int i;
       for (i=0; i<position.getPosition(); i++){
            if (key == keys[i] || key.equals(keys[i])){
                flag = true;
                break;
            }
       }
       if (flag)
           return values.getValues().get(i);
       return null;
     //   if (map.containsKey(key)){
       //     return (Integer)map.get(key);
            
        //}
        //return null;
       //return (Integer)map.get(key);
    }
    
      public void put2(Object key, int value){
      //  int hash = System.identityHashCode(key);
    /*    System.out.println("key " + key + " value " + value);
        if (map.containsKey(key)){
            System.out.println("-------hmap-----------------");
                System.out.println("value replaced " + key);
                System.out.println("-------hmap-------------------");
        }
        map.put(key, value);*/
                
            
        for (int i=0; i<position.getPosition(); i++){
      //      System.out.println("Key " + key + " value " + value + " keys[i] " +keys[i]);
            if ((key.equals(keys[i])) || (key == keys[i])){
                System.out.println("-----------tr-------------");
                System.out.println("value replaced " + key);
                System.out.println("-------------tr-------------");
                values.getValues().set(i, value);
                keys[i] = key;
                return;
            }
        }
        if (position.getPosition()  == capacity.getPosition()){
            System.out.println("expanding");
            capacity.setPosition(capacity.getPosition()*2);
            AtomicIntArray ar = new AtomicIntArray(Integer.class, capacity.getPosition());
            Object[] tmp = new Object[capacity.getPosition()];
            
            for (int i=0; i<capacity.getPosition(); i++){
                ar.set(i, values.getValues().get(i));
                tmp[i] = keys[i];
            }
            keys = new Object[capacity.getPosition()];
            System.arraycopy(tmp, 0, keys, 0, capacity.getPosition()/2);
            values.setValues(ar);
        }
        
        keys[position.getPosition()] = key;
        values.getValues().set(position.getPosition(), value);
        position.setPosition(position.getPosition()+1);
        System.out.println("tr key " + keys[position.getPosition()-1] + " tr value " + values.getValues().get(position.getPosition()-1));
    }
    
    public Integer get2(Object key){
       boolean flag  = false;
       int i;
       for (i=0; i<position.getPosition(); i++){
            if (key == keys[i] || key.equals(keys[i])){
                flag = true;
                break;
            }
       }
       if (flag)
           return values.getValues().get(i);
       return null;
     //   if (map.containsKey(key)){
       //     return (Integer)map.get(key);
            
        //}
        //return null;
       //return (Integer)map.get(key);
    }
    
    
    
  /*  public Integer get(Object key) {
        if (key == null) {
            return getForNullKey();
        }
        int hash = hash(key.hashCode());
        for (Entry<Object, Integer> e = table[indexFor(hash, table.length)]; e != null; e = e.next) {
            Object k;
            if (e.hash == hash && ((k = e.key) == key || key.equals(k))) {
                return e.value;
            }
        }
        return null

    }*/
    
 /*   private Object getForNullKey() {
        table.
        for (Entry e = table[0]; e != null; e = ) {
            if (e.getKey() == null)
              return e.getValue();
        }
        return null;
    }

      static int hash(int h) {
           h ^= (h >>> 20) ^ (h >>> 12);
          return h ^ (h >>> 7) ^ (h >>> 4);
      }*/
       
    
/*    public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            try {
                Object otherReferent = ((EqualsComparableKey) obj).get();
                Object thisReferent = get();
                return (thisReferent != null
                        && otherReferent != null
                        && thisReferent.equals(otherReferent));
            } catch (ClassCastException e) {
                return false;
            }
    }*/
    public void remove(){
        
    }
    
    
    @atomic public interface valueHolder extends AtomicSuperClass{
        AtomicIntArray getValues();
        void setValues(AtomicIntArray values);
     
    }
    
    @atomic public interface keyHolder{
        AtomicArray<trLockingContext.holder> getKeys();
        void setKeys(AtomicArray<trLockingContext.holder> values);

    }

    public int getPosition() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void setPosition(int pos) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
    
    

}
