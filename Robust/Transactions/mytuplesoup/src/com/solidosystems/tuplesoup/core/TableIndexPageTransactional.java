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

import java.io.*;
import java.util.*;

public class TableIndexPageTransactional{
    public @atomic interface TableIndexPageTSInf{
        Long getLocation();
        Integer getSize();
        Long getNext();
        Long getLower();
        Integer getOffset();
        Integer getStarthash();
        Integer getEndhash();
        Boolean getFirst();
        TableIndexPageTSInf getNextpage();
        TableIndexPageTSInf getLowerpage();
        
     
        void setLowerpage(TableIndexPageTSInf lowerpage);   
        void setNextpage(TableIndexPageTSInf nextpage);
        void setFirst(Boolean val);
        void setEndhash();
        void setStarthash();
        void setOffset(Integer offset);
        void setNext(Long next);
        void setSize(Integer size);
        void setLocation(Long location);
        void setLower(Long val);
    }
    private final static int BASEOFFSET=4+8+8+4+4;
    private RandomAccessFile file=null;
    
    private long location=-1;
    private int size=-1;
    private long next=-1;
    private long lower=-1;
    private int offset=0;
    
    private int starthash=-1;
    private int endhash=-1;
    private boolean first=false;
    
    private TableIndexPage nextpage=null;
    private TableIndexPage lowerpage=null;
    
    private PagedIndex index=null;
    
    public TableIndexPageTransactional(PagedIndex index,RandomAccessFile file) throws IOException{
        this.file=file;
        this.index=index;
        first=false;
        location=file.getFilePointer();
        size=file.readInt();
        next=file.readLong();
        lower=file.readLong();
        offset=file.readInt();
        endhash=file.readInt();
        if(offset>0)starthash=file.readInt();
    }
    
    public static TableIndexPage createNewPage(PagedIndex index,RandomAccessFile file,int size) throws IOException{
        long pre=file.length();
        file.setLength(file.length()+size+BASEOFFSET);
        file.seek(pre);
        file.writeInt(size);
        file.writeLong(-1l);
        file.writeLong(-1l);
        file.writeInt(0);
        file.writeInt(-1);
        file.seek(pre);
        index.stat_create_page++;
        return new TableIndexPage(index,file);
    }
    
    public void setFirst(){
        first=true;
    }
    
    public long getLocation(){
        return location;
    }
    public long getEndLocation(){
        return location+size+BASEOFFSET;
    }
    
    public String toString(){
        StringBuffer buf=new StringBuffer();
        buf.append("{\n");
        buf.append("  location  "+location+"\n");
        buf.append("  size      "+size+"\n");
        buf.append("  next      "+next+"\n");
        buf.append("  lower     "+lower+"\n");
        buf.append("  offset    "+offset+"\n");
        buf.append("  starthash "+starthash+"\n");
        buf.append("  endhash "+endhash+"\n");
        buf.append("}\n");
        return buf.toString();
    }
    
    private void updateMeta() throws IOException{
        file.seek(location);
        file.writeInt(size);
        file.writeLong(next);
        file.writeLong(lower);
        file.writeInt(offset);
        file.writeInt(endhash);
    }
    
    public void addEntriesToList(List<TableIndexEntry> lst) throws IOException{
        if(lower>-1){
            if(lowerpage==null){
                file.seek(lower);
                lowerpage=new TableIndexPage(index,file);
            }
            lowerpage.addEntriesToList(lst);
        }
        if(next>-1){
            if(nextpage==null){
                file.seek(next);
                nextpage=new TableIndexPage(index,file);
            }
            nextpage.addEntriesToList(lst);
        }
        file.seek(location+BASEOFFSET);
        long pre=file.getFilePointer();
        while(file.getFilePointer()<pre+offset){
            TableIndexEntry entry=TableIndexEntry.readData(file);
            if(entry!=null){
                if(entry.getLocation()!=Table.DELETE)lst.add(entry);
            }
        }
    }
    
    public TableIndexEntry scanIndex(String id,int hashcode) throws IOException{
        if(!first){
            if(hashcode<starthash){
                if(lower==-1)return null;
                if(lowerpage==null){
                    file.seek(lower);
                    lowerpage=new TableIndexPage(index,file);
                }
                index.stat_page_branch++;
                return lowerpage.scanIndex(id,hashcode);
            }
        }
        if(hashcode>endhash){
            if(next==-1)return null;
            if(nextpage==null){
                file.seek(next);
                nextpage=new TableIndexPage(index,file);
            }
            index.stat_page_next++;
            return nextpage.scanIndex(id,hashcode);
        }
        file.seek(location+BASEOFFSET);
        long pre=file.getFilePointer();
        while(file.getFilePointer()<pre+offset){
            TableIndexEntry entry=TableIndexEntry.lookForData(id,file);
            if(entry!=null)return entry;
        }
        if(next==-1)return null;
        if(nextpage==null){
            file.seek(next);
            nextpage=new TableIndexPage(index,file);
        }
        index.stat_page_next++;
        return nextpage.scanIndex(id,hashcode);
    }
    protected long getOffset(String id,int hashcode) throws IOException{
        if(!first){
            if(hashcode<starthash){
                if(lower==-1)return -1;
                if(lowerpage==null){
                    file.seek(lower);
                    lowerpage=new TableIndexPage(index,file);
                }
                index.stat_page_branch++;
                return lowerpage.getOffset(id,hashcode);
            }
        }
        if(hashcode>endhash){
            if(next==-1)return -1;
            if(nextpage==null){
                file.seek(next);
                nextpage=new TableIndexPage(index,file);
            }
            index.stat_page_next++;
            return nextpage.getOffset(id,hashcode);
        }
        file.seek(location+BASEOFFSET);
        long pre=file.getFilePointer();
        while(file.getFilePointer()<pre+offset){
            long prescan=file.getFilePointer();
            TableIndexEntry entry=TableIndexEntry.lookForData(id,file);
            if(entry!=null)return prescan;
        }
        if(next==-1)return -1;
        if(nextpage==null){
            file.seek(next);
            nextpage=new TableIndexPage(index,file);
        }
        index.stat_page_next++;
        return nextpage.getOffset(id,hashcode);
    }
    
    protected TableIndexPage getFirstFreePage(String id,int hashcode) throws IOException{
        // Is this an empty page?
        if(offset==0){
            return this;
        }
        // Is this hash lower than the starthash
        if(!first){
            if(hashcode<starthash){
                if(lower==-1){
                    lower=file.length();
                    updateMeta();
                    return createNewPage(index,file,PagedIndex.PAGESIZE);
                }
                if(lowerpage==null){
                    file.seek(lower);
                    lowerpage=new TableIndexPage(index,file);
                }
                index.stat_page_branch++;
                return lowerpage.getFirstFreePage(id,hashcode);
            }
        }
        // Do we have space in this page
        if(size-offset>id.length()*2+4+4+8+1+2)return this;
        // Check next
        if(next==-1){
            next=file.length();
            updateMeta();
            return createNewPage(index,file,PagedIndex.PAGESIZE);
        }
        if(nextpage==null){
            file.seek(next);
            nextpage=new TableIndexPage(index,file);
        }
        index.stat_page_next++;
        return nextpage.getFirstFreePage(id,hashcode);
    }
    
    public void addEntry(String id,int rowsize,int location,long position) throws IOException{
        if(offset==0)starthash=id.hashCode();
        file.seek(this.location+BASEOFFSET+offset);
        TableIndexEntry entry=new TableIndexEntry(id,rowsize,location,position);
        entry.writeData(file);
        offset+=entry.getSize();
        if(id.hashCode()>endhash)endhash=id.hashCode();
        updateMeta();
    }
}