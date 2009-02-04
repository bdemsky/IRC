/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.enea.jcarder.transactionalinterfaces;

import dstm2.AtomicSuperClass;
import dstm2.atomic;
import dstm2.Thread;
import dstm2.factory.Factory;

/**
 *
 * @author navid
 */
public class Intif {
      
      public static Factory<positionif> factory = Thread.makeFactory(positionif.class);
      
      public positionif pos;
      
      public void init(){
          
          pos = factory.create();
          //pos.setPosition(0);
      }
      
      public void increment(int offset){
          pos.setPosition(pos.getPosition() + offset);
          
      }
      
      public int get(){
          return pos.getPosition();
      }
      
      @atomic public interface positionif extends AtomicSuperClass{
        public int getPosition();
        public void setPosition(int pos);
    }
}
