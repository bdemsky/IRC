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

import dstm2.AtomicArray;
import dstm2.atomic;
import java.io.*;
import java.util.*;
import java.nio.channels.*;

public class PagedIndex implements TableIndex{
    
protected static final int INITIALPAGEHASH=1024;
    protected static final int PAGESIZE=2048;
    
    private RandomAccessFile out=null;
    private String filename;
    private TableIndexPage[] root=null;
   // private TableIndexPageTransactional[] root=null;
    
    private long stat_read=0;
    private long stat_write=0;
    protected long stat_create_page=0;
    protected long stat_page_next=0;
    protected long stat_page_branch=0;
 
    public PagedIndex(String filename) throws IOException{
        this.filename=filename;
        File ftest=new File(filename);
        if(!ftest.exists())ftest.createNewFile();
        out=new RandomAccessFile(filename,"rw");
        root=new TableIndexPage[INITIALPAGEHASH];
        System.out.println(filename);
        System.out.println(out.length());
        if(out.length()>0){
            for(int i=0;i<INITIALPAGEHASH;i++){
                root[i]=new TableIndexPage(this,out);
                root[i].setFirst();
                System.out.println("In loop " + root[i].getEndLocation());
                out.seek(root[i].getEndLocation());
            }
        }else{
            for(int i=0;i<INITIALPAGEHASH;i++){
                root[i]=TableIndexPage.createNewPage(this,out,PAGESIZE);
                System.out.println("In Othe loop " + root[i].getEndLocation());
                root[i].setFirst();
            }
        }
    }
    
    public Hashtable<String,Long> readStatistics(){
        Hashtable<String,Long> hash=new Hashtable<String,Long>();
        hash.put("stat_index_read",stat_read);
        hash.put("stat_index_write",stat_write);
        hash.put("stat_index_create_page",stat_create_page);
        hash.put("stat_index_page_next",stat_page_next);
        hash.put("stat_index_page_branch",stat_page_branch);
        stat_read=0;
        stat_write=0;
        stat_create_page=0;
        stat_page_next=0;
        stat_page_branch=0;
        return hash;
    }
    
    private int rootHash(String id){
        return id.hashCode() & (INITIALPAGEHASH-1);
    }
    
    private synchronized TableIndexPage getFirstFreePage(String id) throws IOException{
        return root[rootHash(id)].getFirstFreePage(id,id.hashCode());
    }
    
    private synchronized long getOffset(String id) throws IOException{
        if(root==null)return -1;
        return root[rootHash(id)].getOffset(id,id.hashCode());
    }
    
    public synchronized void updateEntry(String id,int rowsize,int location,long position) throws IOException{
        long offset=getOffset(id);
        out.seek(offset);
        TableIndexEntry entry=new TableIndexEntry(id,rowsize,location,position);
        entry.updateData(out);
        stat_write++;
    }
    public synchronized void addEntry(String id,int rowsize,int location,long position) throws IOException{
        TableIndexPage page=getFirstFreePage(id);
        page.addEntry(id,rowsize,location,position);
        stat_write++;
    }
    public synchronized TableIndexEntry scanIndex(String id) throws IOException{
        if(root==null)return null;
        return root[rootHash(id)].scanIndex(id,id.hashCode());
    }
    public synchronized List<TableIndexEntry> scanIndex(List<String> rows) throws IOException{
        List<TableIndexEntry> lst=new ArrayList<TableIndexEntry>();
        for(int i=0;i<rows.size();i++){
            String id=rows.get(i);
            TableIndexEntry entry=scanIndex(id);
            if(entry!=null){
                if(entry.getLocation()!=Table.DELETE)lst.add(entry);
            }
        }
        return lst;
    }
    public synchronized List<TableIndexEntry> scanIndex() throws IOException{
        ArrayList<TableIndexEntry> lst=new ArrayList<TableIndexEntry>();
        for(int i=0;i<INITIALPAGEHASH;i++){
            root[i].addEntriesToList(lst);
        }
        return lst;
    }
    public void close(){
        try{
            if(out!=null){
                out.close();
            }
        }catch(Exception e){}
    }
}