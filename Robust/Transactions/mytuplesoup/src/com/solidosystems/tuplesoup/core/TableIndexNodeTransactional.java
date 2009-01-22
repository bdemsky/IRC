/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.solidosystems.tuplesoup.core;

import dstm2.AtomicSuperClass;
import dstm2.atomic;

/**
 *
 * @author navid
 */
public class TableIndexNodeTransactional implements AtomicSuperClass{

    //private TableIndexNodeTransactional previous;
    //private TableIndexEntryTransactional data;
    //private TableIndexNodeTransactional next;
    TableIndexInodeTSinf atomicfields;
    
    public @atomic interface TableIndexInodeTSinf{
        TableIndexNodeTransactional getPrevious(); 
        TableIndexEntryTransactional getData(); 
        TableIndexNodeTransactional getNext(); 
        
        void setPrevious(TableIndexNodeTransactional val); 
        void setData(TableIndexEntryTransactional val); 
        void setNext(TableIndexNodeTransactional val);
    }
    
    public TableIndexNodeTransactional(){
        atomicfields.setPrevious(null);
        atomicfields.setData(null);
        atomicfields.setNext(null);
    }
    
    public TableIndexNodeTransactional(TableIndexEntryTransactional entry){
        atomicfields.setPrevious(null);
        atomicfields.setData(entry);
        atomicfields.setNext(null);
        
    }
    
    public TableIndexNodeTransactional(TableIndexNodeTransactional prev,TableIndexEntryTransactional entry){
        atomicfields.setPrevious(prev);
        atomicfields.setData(entry);
        atomicfields.setNext(null);
    }
    
    public TableIndexNodeTransactional(TableIndexNodeTransactional prev,TableIndexEntryTransactional entry,TableIndexNodeTransactional nex){
        atomicfields.setPrevious(prev);
        atomicfields.setData(entry);
        atomicfields.setNext(nex);
    }
    
    public TableIndexEntryTransactional getData(){
        return atomicfields.getData();
    }
    public TableIndexNodeTransactional getPrevious(){
        return atomicfields.getPrevious();
    }
    public TableIndexNodeTransactional getNext(){
        return atomicfields.getNext();
    }
    public void setNext(TableIndexNodeTransactional node){
        atomicfields.setNext(node);
    }
    public void setPrevious(TableIndexNodeTransactional node){
        atomicfields.setPrevious(node);
    }
    public void setData(TableIndexEntryTransactional entry){
        atomicfields.setData(entry);
    }
    public void remove(){
        if(atomicfields.getPrevious()!=null){
            atomicfields.getPrevious().setNext(atomicfields.getNext());
        }
        if(atomicfields.getNext()!=null){
            atomicfields.getNext().setPrevious(atomicfields.getPrevious());
        }
    }
}
