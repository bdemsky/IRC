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
import TransactionalIO.interfaces.IOOperations;
import dstm2.Configs;
import dstm2.SpecialTransactionalFile;
import java.io.*;
import java.util.*;
import java.nio.channels.*;

public class PagedIndex implements TableIndex{
    protected static final int INITIALPAGEHASH=1024;
    protected static final int PAGESIZE=2048;
    
    //private RandomAccessFile out=null;
    private IOOperations out=null;
    private String filename;
    private TableIndexPage[] root=null;

 
    public PagedIndex(String filename) throws IOException{
        this.filename=filename;
        File ftest=new File(filename);
        if(!ftest.exists())ftest.createNewFile();
        //out=new RandomAccessFile(filename,"rw");
        if (Configs.inevitable)
            out=new SpecialTransactionalFile(filename,"rw");
        else 
            out=new TransactionalFile(filename,"rw");
        
        root=new TableIndexPage[INITIALPAGEHASH];
        if(out.length()>0){
            for(int i=0;i<INITIALPAGEHASH;i++){
                root[i]=new TableIndexPage(this,out);
                root[i].setFirst();
                out.seek(root[i].getEndLocation());
                System.out.println(root[i].getEndLocation());
            }
        }else{
            for(int i=0;i<INITIALPAGEHASH;i++){
                root[i]=TableIndexPage.createNewPage(this,out,PAGESIZE);
                root[i].setFirst();
            }
        }
    }
    
   
    
    private int rootHash(String id){
        return id.hashCode() & (INITIALPAGEHASH-1);
    }
    
    private /*synchronized*/ TableIndexPage getFirstFreePage(String id) throws IOException{
        return root[rootHash(id)].getFirstFreePage(id,id.hashCode());
    }
    
  

    
    public /*synchronized*/ void addEntry(String id,int rowsize,int location,long position) throws IOException{
        TableIndexPage page=getFirstFreePage(id);
        page.addEntry(id,rowsize,location,position);
        
    }
    public /*synchronized*/ TableIndexEntry scanIndex(String id) throws IOException{
        if(root==null)return null;
        return root[rootHash(id)].scanIndex(id,id.hashCode());
    }
   
    
    public void close(){
        try{
            if(out!=null){
                out.close();
            }
        }catch(Exception e){}
    }

    public Hashtable<String, Long> readStatistics() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void updateEntry(String id, int rowsize, int location, long position) throws IOException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public List<TableIndexEntry> scanIndex(List<String> rows) throws IOException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public List<TableIndexEntry> scanIndex() throws IOException {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}