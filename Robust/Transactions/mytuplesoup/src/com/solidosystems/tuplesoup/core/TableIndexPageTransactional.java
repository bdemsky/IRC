/*
 * Copyright (c) 2007, Solido Systems
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * Neither the name of Solido Systems nor the names of its contributors may be
 * used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
 
package com.solidosystems.tuplesoup.core;

import TransactionalIO.core.TransactionalFile;
import dstm2.AtomicSuperClass;
import dstm2.atomic;
import java.io.*;
import java.util.*;

public class TableIndexPageTransactional implements AtomicSuperClass{
    TableIndexPageTSInf atomicfields = null;
    private final static int BASEOFFSET=4+8+8+4+4;
    //private RandomAccessFile file=null;
    private TransactionalFile file = null;
    
    public @atomic interface TableIndexPageTSInf{
        Long getLocation();
        Integer getSize();
        Long getNext();
        Long getLower();
        Integer getOffset();
        Integer getStarthash();
        Integer getEndhash();
        Boolean getFirst();
        TableIndexPageTransactional getNextpage();
        TableIndexPageTransactional getLowerpage();
        PagedIndexTransactional getIndex(); 
        
     
        void setLowerpage(TableIndexPageTransactional lowerpage);   
        void setNextpage(TableIndexPageTransactional nextpage);
        void setFirst(Boolean val);
        void setEndhash(int val);
        void setStarthash(int val);
        void setOffset(Integer offset);
        void setNext(Long next);
        void setSize(Integer size);
        void setLocation(Long location);
        void setLower(Long val);
        void setIndex(PagedIndexTransactional val);
    }
  
    
    
    public TableIndexPageTransactional(PagedIndexTransactional index,TransactionalFile file) throws IOException{
        this.file=file;
        this.atomicfields.setIndex(index);
        this.atomicfields.setFirst(false);
        this.atomicfields.setLocation(file.getFilePointer());
        this.atomicfields.setSize(file.readInt());
        this.atomicfields.setNext(file.readLong());
        this.atomicfields.setLower(file.readLong());
        this.atomicfields.setOffset(file.readInt());
        this.atomicfields.setEndhash(file.readInt());
        if(this.atomicfields.getOffset()>0)
            this.atomicfields.setStarthash(file.readInt());
    }
    
    public static TableIndexPageTransactional createNewPage(PagedIndexTransactional index,TransactionalFile file,int size) throws IOException{
        long pre=file.length();
//        file.setLength(file.length()+size+BASEOFFSET);
        file.seek(pre);
        file.writeInt(size);
        file.writeLong(-1l);
        file.writeLong(-1l);
        file.writeInt(0);
        file.writeInt(-1);
        file.seek(pre);
        index.atomicfields.setStat_create_page(index.atomicfields.getStat_create_page()+1);
        return new TableIndexPageTransactional(index,file);
    }
    
    public void setFirst(){
        this.atomicfields.setFirst(true);
    }
    
    public long getLocation(){
        return atomicfields.getLocation();
    }
    public long getEndLocation(){
        return this.atomicfields.getLocation()+atomicfields.getSize()+BASEOFFSET;
    }
    
    public String toString(){
        StringBuffer buf=new StringBuffer();
        buf.append("{\n");
        buf.append("  location  "+this.atomicfields.getLocation()+"\n");
        buf.append("  size      "+this.atomicfields.getSize()+"\n");
        buf.append("  next      "+this.atomicfields.getNext()+"\n");
        buf.append("  lower     "+this.atomicfields.getLower()+"\n");
        buf.append("  offset    "+this.atomicfields.getOffset()+"\n");
        buf.append("  starthash "+this.atomicfields.getStarthash()+"\n");
        buf.append("  endhash "+this.atomicfields.getEndhash()+"\n");
        buf.append("}\n");
        return buf.toString();
    }
    
    private void updateMeta() throws IOException{
        file.seek(this.atomicfields.getLocation());
        file.writeInt(this.atomicfields.getSize());
        file.writeLong(this.atomicfields.getNext());
        file.writeLong(this.atomicfields.getLower());
        file.writeInt(this.atomicfields.getOffset());
        file.writeInt(this.atomicfields.getEndhash());
    }
    
    public void addEntriesToList(List<TableIndexEntryTransactional> lst) throws IOException{
        if(this.atomicfields.getLower()>-1){
            if(this.atomicfields.getLowerpage()==null){
                file.seek(this.atomicfields.getLower());
                this.atomicfields.setLowerpage(new TableIndexPageTransactional(this.atomicfields.getIndex(),file));
            }
            this.atomicfields.getLowerpage().addEntriesToList(lst);
        }
        if(this.atomicfields.getNext()>-1){
            if(this.atomicfields.getNextpage()==null){
                file.seek(this.atomicfields.getNext());
                this.atomicfields.setNextpage(new TableIndexPageTransactional(this.atomicfields.getIndex(),file));
            }
            this.atomicfields.getNextpage().addEntriesToList(lst);
        }
        file.seek(this.atomicfields.getLocation()+BASEOFFSET);
        long pre=file.getFilePointer();
        while(file.getFilePointer()<pre+this.atomicfields.getOffset()){
            TableIndexEntryTransactional entry=TableIndexEntryTransactional.readData(file);
            if(entry!=null){
                if(entry.getLocation()!=Table.DELETE)lst.add(entry);
            }
        }
    }
    
    public TableIndexEntryTransactional scanIndex(String id,int hashcode) throws IOException{
        if(!atomicfields.getFirst()){
            if(hashcode<atomicfields.getStarthash()){
                if(atomicfields.getLower()==-1) return null;
                if(this.atomicfields.getLowerpage()==null){
                    file.seek(this.atomicfields.getLower());
                    this.atomicfields.setLowerpage(new TableIndexPageTransactional(this.atomicfields.getIndex(),file));
                }
                atomicfields.getIndex().atomicfields.setStat_page_branch(atomicfields.getIndex().atomicfields.getStat_page_branch()+1);
                return this.atomicfields.getLowerpage().scanIndex(id,hashcode);
            }
        }
        if(hashcode>this.atomicfields.getEndhash()){
            if(this.atomicfields.getNext()==-1)return null;
            if(this.atomicfields.getNextpage()==null){
                file.seek(this.atomicfields.getNext());
                this.atomicfields.setNextpage(new TableIndexPageTransactional(this.atomicfields.getIndex(),file));
            }
            atomicfields.getIndex().atomicfields.setStat_page_next(atomicfields.getIndex().atomicfields.getStat_page_next()+1);
            return this.atomicfields.getNextpage().scanIndex(id,hashcode);
        }
        file.seek(this.atomicfields.getLocation()+BASEOFFSET);
        long pre=file.getFilePointer();
        while(file.getFilePointer()<pre+this.atomicfields.getOffset()){
            TableIndexEntryTransactional entry=TableIndexEntryTransactional.lookForData(id,file);
            if(entry!=null)return entry;
        }
        if(this.atomicfields.getNext()==-1)return null;
        if(this.atomicfields.getNextpage()==null){
            file.seek(this.atomicfields.getNext());
            this.atomicfields.setNextpage(new TableIndexPageTransactional(this.atomicfields.getIndex(),file));
        }
        atomicfields.getIndex().atomicfields.setStat_page_next(atomicfields.getIndex().atomicfields.getStat_page_next()+1);
        return this.atomicfields.getNextpage().scanIndex(id,hashcode);
    }
    protected long getOffset(String id,int hashcode) throws IOException{
        if(!this.atomicfields.getFirst()){
            if(hashcode<this.atomicfields.getStarthash()){
                if(this.atomicfields.getLower()==-1)return -1;
                if(this.atomicfields.getLowerpage()==null){
                    file.seek(atomicfields.getLower());
                    this.atomicfields.setLowerpage(new TableIndexPageTransactional(this.atomicfields.getIndex(),file));
                }
                atomicfields.getIndex().atomicfields.setStat_page_branch(atomicfields.getIndex().atomicfields.getStat_page_branch()+1);
                return this.atomicfields.getLowerpage().getOffset(id,hashcode);
            }
        }
        if(hashcode>this.atomicfields.getEndhash()){
            if(this.atomicfields.getNext()==-1)return -1;
            if(this.atomicfields.getNextpage()==null){
                file.seek(this.atomicfields.getNext());
                this.atomicfields.setNextpage(new TableIndexPageTransactional(this.atomicfields.getIndex(),file));
            }
            atomicfields.getIndex().atomicfields.setStat_page_next(atomicfields.getIndex().atomicfields.getStat_page_next()+1);
            return this.atomicfields.getNextpage().getOffset(id,hashcode);
        }
        file.seek(this.atomicfields.getLocation()+BASEOFFSET);
        long pre=file.getFilePointer();
        while(file.getFilePointer()<pre+this.atomicfields.getOffset()){
            long prescan=file.getFilePointer();
            TableIndexEntryTransactional entry=TableIndexEntryTransactional.lookForData(id,file);
            if(entry!=null)return prescan;
        }
        if(this.atomicfields.getNext()==-1)return -1;
        if(this.atomicfields.getNextpage()==null){
            file.seek(this.atomicfields.getNext());
            this.atomicfields.setNextpage(new TableIndexPageTransactional(this.atomicfields.getIndex(),file));
        }
        atomicfields.getIndex().atomicfields.setStat_page_next(atomicfields.getIndex().atomicfields.getStat_page_next()+1);
        return this.atomicfields.getNextpage().getOffset(id,hashcode);
    }
    
    protected TableIndexPageTransactional getFirstFreePage(String id,int hashcode) throws IOException{
        // Is this an empty page?
        if(this.atomicfields.getOffset()==0){
            return this;
        }
        // Is this hash lower than the starthash
        if(!this.atomicfields.getFirst()){
            if(hashcode<this.atomicfields.getStarthash()){
                if(this.atomicfields.getLower()==-1){
                    this.atomicfields.setLower(file.length());
                    updateMeta();
                    return createNewPage(this.atomicfields.getIndex(),file,PagedIndexTransactional.PAGESIZE);
                }
                if(this.atomicfields.getLowerpage()==null){
                    file.seek(this.atomicfields.getLower());
                    this.atomicfields.setLowerpage(new TableIndexPageTransactional(this.atomicfields.getIndex(),file));
                }
                atomicfields.getIndex().atomicfields.setStat_page_branch(atomicfields.getIndex().atomicfields.getStat_page_branch()+1);
                return this.atomicfields.getLowerpage().getFirstFreePage(id,hashcode);
            }
        }
        // Do we have space in this page
        if(this.atomicfields.getSize()-this.atomicfields.getOffset()>id.length()*2+4+4+8+1+2)return this;
        // Check next
        if(this.atomicfields.getNext()==-1){
            this.atomicfields.setNext(file.length());
            updateMeta();
            return createNewPage(this.atomicfields.getIndex(),file,PagedIndexTransactional.PAGESIZE);
        }
        if(this.atomicfields.getNextpage()==null){
            file.seek(this.atomicfields.getNext());
            this.atomicfields.setNextpage(new TableIndexPageTransactional(this.atomicfields.getIndex(),file));
        }
        atomicfields.getIndex().atomicfields.setStat_page_next(atomicfields.getIndex().atomicfields.getStat_page_next()+1);
        return this.atomicfields.getNextpage().getFirstFreePage(id,hashcode);
    }
    
    public void addEntry(String id,int rowsize,int location,long position) throws IOException{
        if(atomicfields.getOffset()==0)this.atomicfields.setStarthash(id.hashCode());
        file.seek(this.atomicfields.getLocation()+BASEOFFSET+this.atomicfields.getOffset());
        TableIndexEntryTransactional entry=new TableIndexEntryTransactional(id,rowsize,location,position);
        entry.writeData(file);
        this.atomicfields.setOffset(this.atomicfields.getOffset()+entry.getSize());
        if(id.hashCode()>this.atomicfields.getEndhash()) this.atomicfields.setEndhash(id.hashCode());
        updateMeta();
    }
}