/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dstm2.util;

import dstm2.AtomicArray;
import dstm2.factory.Factory;
import dstm2.Thread;
import dstm2.atomic;
import java.util.Arrays;


/**
 *
 * @author navid
 */

public class ArrayList<V extends dstm2.AtomicSuperClass>{
   transient volatile int modCount;
    private volatile int size;
    AtomicArray<entry<V>> elementData;
    private Factory<entry> factory;

    public ArrayList() {
        this(10);
    }
    
    public ArrayList(int initialCapacity) {
        super ();
        if (initialCapacity < 0)
            throw new IllegalArgumentException("Illegal Capacity: "  + initialCapacity);
       this .elementData = new AtomicArray<entry<V>>(entry.class,initialCapacity);
    }
    
    
      public void ensureCapacity(int minCapacity) {
          modCount++;
          int oldCapacity = elementData.size();
          if (minCapacity > oldCapacity) {
                //Object oldData[] = elementData;
                int newCapacity = (oldCapacity * 3) / 2 + 1;
                if (newCapacity < minCapacity)
                    newCapacity = minCapacity;
                // minCapacity is usually close to size, so this is a win:
                AtomicArray<entry<V>> newar =  new AtomicArray<entry<V>>(entry.class, newCapacity);
                for (int i=0; i<newCapacity; i++)
                    newar.set(i, elementData.get(i));
                elementData = newar;
          }
      }

            /**
            * Returns the number of elements in this list.
             *
             * @return the number of elements in this list
             */
     public int size() {
        return size;
     }
     
     public boolean add(V value) {
        ensureCapacity(size + 1); // Increments modCount!
        entry<V> e = factory.create();
        e.setValue(value);
        elementData.set(size, e);
        size++;
        return true;
     }
     
     public V get(int index){
         return elementData.get(index).getValue();
     }
     
     
      public V remove(int index) {
        rangeCheck(index);
        modCount++;
        entry<V> oldValue = elementData.get(index);

        int numMoved = size - index - 1;
        if (numMoved > 0)
            System.arraycopy(elementData, index + 1, elementData,
        index, numMoved);
        size--;
        elementData.set(size, null); // Let gc do its work
        return oldValue.getValue();
    }
      
      private void rangeCheck(int index) {
         if (index < 0 || index >= this .size)
             throw new IndexOutOfBoundsException();
     }
      
      public void clear() {
           modCount++;
            // Let gc do its work
           for (int i = 0; i < size; i++)
              elementData.set(i, null);
           size = 0;
     } 
    
     @atomic interface entry<V>{
        V getValue();
        void setValue(V val);
    }
    


}
