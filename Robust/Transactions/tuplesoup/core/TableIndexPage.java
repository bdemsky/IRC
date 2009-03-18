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

import TransactionalIO.interfaces.IOOperations;
import dstm2.atomic;
import dstm2.Thread;
import dstm2.factory.Factory;
import java.io.*;
import java.util.*;

public class TableIndexPage{
    private final static int BASEOFFSET=4+8+8+4+4;
    //private RandomAccessFile file=null;
    private IOOperations file=null;
    
  /*  private long location=-1;
    private int size=-1;
    private long next=-1;
    private long lower=-1;
    private int offset=0;
    
    private int starthash=-1;
    private int endhash=-1;
    private boolean first=false;
    
    private TableIndexPage nextpage=null;
    private TableIndexPage lowerpage=null;*/
    
    private PagedIndex index=null;
    static Factory<TableIndexPageTSInf> factory = Thread.makeFactory(TableIndexPageTSInf.class);
    TableIndexPageTSInf tableindexpage;    
        
    @atomic public interface TableIndexPageTSInf{
        Long getNext();
        Long getLower();
        Long getLocation();
        int getSize();
        int getOffset();
        int getStarthash();
        int getEndhash();
        boolean getFirst();
        TableIndexPage getNextpage();
        TableIndexPage getLowerpage();
        
     
        void setLowerpage(TableIndexPage lowerpage);   
        void setNextpage(TableIndexPage nextpage);
        void setFirst(boolean val);
        void setEndhash(int val);
        void setStarthash(int val);
        void setOffset(int offset);
        void setNext(Long next);
        void setSize(int size);
        void setLocation(Long location);
        void setLower(Long val);
    }
  
    
    public TableIndexPage(PagedIndex index, IOOperations/*RandomAccessFile*/ file) throws IOException{
        this.file=file;
        this.index=index;
        tableindexpage = factory.create();
        tableindexpage.setFirst(false);
        tableindexpage.setLocation(file.getFilePointer());
        tableindexpage.setSize(file.readInt());
        tableindexpage.setNext(file.readLong());
        tableindexpage.setLower(file.readLong());
        tableindexpage.setOffset(file.readInt());
        tableindexpage.setEndhash(file.readInt());
        if(tableindexpage.getOffset()>0) {
            tableindexpage.setStarthash(file.readInt());
        }
    }
    
    public static TableIndexPage createNewPage(PagedIndex index, IOOperations file,int size) throws IOException{
        long pre=file.length();
        file.seek(pre);
        byte[] dummy = new byte[size+BASEOFFSET];
        file.write(dummy);
        //file.setLength(file.length()+size+BASEOFFSET);
        file.seek(pre);
        file.writeInt(size);
        file.writeLong(-1l);
        file.writeLong(-1l);
        file.writeInt(0);
        file.writeInt(-1);
        file.seek(pre);

        return new TableIndexPage(index,file);
    }
    
    
    
    public void setFirst(){
        tableindexpage.setFirst(true);
    }
    
    public long getLocation(){
        return tableindexpage.getLocation();
    }
    public long getEndLocation(){
        return tableindexpage.getLocation()+tableindexpage.getSize()+BASEOFFSET;
    }
    
    public String toString(){
        StringBuffer buf=new StringBuffer();
        buf.append("{\n");
        buf.append("  location  "+tableindexpage.getLocation()+"\n");
        buf.append("  size      "+tableindexpage.getSize()+"\n");
        buf.append("  next      "+tableindexpage.getNext()+"\n");
        buf.append("  lower     "+tableindexpage.getLower()+"\n");
        buf.append("  offset    "+tableindexpage.getOffset()+"\n");
        buf.append("  starthash "+tableindexpage.getStarthash()+"\n");
        buf.append("  endhash "+tableindexpage.getEndhash()+"\n");
        buf.append("}\n");
        return buf.toString();
    }
    
    private void updateMeta() throws IOException{
        file.seek(tableindexpage.getLocation());
        file.writeInt(tableindexpage.getSize());
        file.writeLong(tableindexpage.getNext());
        file.writeLong(tableindexpage.getLower());
        file.writeInt(tableindexpage.getOffset());
        file.writeInt(tableindexpage.getEndhash());
    }
    
    public TableIndexEntry scanIndex(String id,int hashcode) throws IOException{
        if(!tableindexpage.getFirst()){
            if(hashcode<tableindexpage.getStarthash()){
                if(tableindexpage.getLower()==-1)return null;
                if(tableindexpage.getLowerpage()==null){
                    file.seek(tableindexpage.getLower());
                    //tableindexpage.setLowerpage();
                    //TableIndexPage tmp = new TableIndexPage(index,file);
                    //tableindexpage.setLowerpage(tmp.tableindexpage);
                    tableindexpage.setLowerpage(new TableIndexPage(index,file));
                }

                return tableindexpage.getLowerpage().scanIndex(id,hashcode);
            }
        }
        if(hashcode>tableindexpage.getEndhash()){
            if(tableindexpage.getNext()==-1)return null;
            if(tableindexpage.getNextpage()==null){
                file.seek(tableindexpage.getNext());
                tableindexpage.setNextpage(new TableIndexPage(index,file));
            }
           // index.stat_page_next++;
            return tableindexpage.getNextpage().scanIndex(id,hashcode);
        }
        file.seek(tableindexpage.getLocation()+BASEOFFSET);
        long pre=file.getFilePointer();
        while(file.getFilePointer()<pre+tableindexpage.getOffset()){
            TableIndexEntry entry=TableIndexEntry.lookForData(id,file);
            if(entry!=null)return entry;
        }
        if(tableindexpage.getNext()==-1)return null;
        if(tableindexpage.getNextpage()==null){
            file.seek(tableindexpage.getNext());
            tableindexpage.setNextpage(new TableIndexPage(index,file));
        }

        return tableindexpage.getNextpage().scanIndex(id,hashcode);
    }

    protected TableIndexPage getFirstFreePage(String id,int hashcode) throws IOException{
        // Is this an empty page?
        if(tableindexpage.getOffset()==0){
            return this;
        }
        // Is this hash lower than the starthash
        if(!tableindexpage.getFirst()){
            if(hashcode<tableindexpage.getStarthash()){
                if(tableindexpage.getLower()==-1){
                    tableindexpage.setLower(file.length());
                    updateMeta();
                    return createNewPage(index,file,PagedIndex.PAGESIZE);
                }
                if(tableindexpage.getLowerpage()==null){
                    file.seek(tableindexpage.getLower());
                    tableindexpage.setLowerpage(new TableIndexPage(index,file));
                }
         //       index.stat_page_branch++;
                return tableindexpage.getLowerpage().getFirstFreePage(id,hashcode);
            }
        }
        // Do we have space in this page
        if(tableindexpage.getSize()-tableindexpage.getOffset()>id.length()*2+4+4+8+1+2)return this;
        // Check next
        if(tableindexpage.getNext()==-1){
            tableindexpage.setNext(file.length());
            updateMeta();
            return createNewPage(index,file,PagedIndex.PAGESIZE);
        }
        if(tableindexpage.getNextpage()==null){
            file.seek(tableindexpage.getNext());
            tableindexpage.setNextpage(new TableIndexPage(index,file));
        }

        return tableindexpage.getNextpage().getFirstFreePage(id,hashcode);
    }
    
    public void addEntry(String id,int rowsize,int location,long position) throws IOException{
        if(tableindexpage.getOffset()==0) tableindexpage.setStarthash(id.hashCode());
        file.seek(this.tableindexpage.getLocation()+BASEOFFSET+tableindexpage.getOffset());
        TableIndexEntry entry=new TableIndexEntry(id,rowsize,location,position);
        entry.writeData(file);
        tableindexpage.setOffset(tableindexpage.getOffset()+entry.getSize());
        if(id.hashCode()>tableindexpage.getEndhash())tableindexpage.setEndhash(id.hashCode());
        updateMeta();
    }
}