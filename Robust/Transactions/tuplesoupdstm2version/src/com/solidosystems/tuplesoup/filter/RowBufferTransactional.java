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
 
package com.solidosystems.tuplesoup.filter;

import java.util.*;
import java.io.*;
import com.solidosystems.tuplesoup.core.*;

public class RowBufferTransactional extends TupleStreamTransactional{
    private int CACHESIZE=32768;
    private int cacheusage=0;
    
    private int mempointer=0;
    private boolean diskused=false;
    
    private List<RowTransactional> membuffer;
    private String diskbuffer;
    private DataOutputStream out;
    private DataInputStream in;
    
    private RowTransactional next=null;
    
    public RowBufferTransactional(String filename){
        membuffer=new ArrayList<RowTransactional>();
        diskbuffer=filename;
        out=null;
        in=null;
    }
    
    public void setCacheSize(int size){
        CACHESIZE=size;
    }
    public int getCacheSize(){
        return CACHESIZE;
    }
    
    public void addRow(RowTransactional row) throws IOException{
        if(cacheusage+row.getSize()<=CACHESIZE){
            membuffer.add(row);
            cacheusage+=row.getSize();
        }else{
            cacheusage=CACHESIZE;
            if(out==null)out=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(diskbuffer),2048));
            row.writeToStream(out);
            diskused=true;
        }
    }
    
    public void prepare() throws IOException{
        if(out!=null){
            out.flush();
            out.close();
        }
        mempointer=0;
        if(diskused)in=new DataInputStream(new BufferedInputStream(new FileInputStream(diskbuffer),2048));
        readNext();
    }
    
    public void close(){
        try{
            File ftest=new File(diskbuffer);
            if(ftest.exists()){
                if(out!=null)out.close();
                if(in!=null)in.close();
                ftest.delete();
            }
        }catch(Exception e){
            
        }
    }
    
    private void readNext() throws IOException{
        if(mempointer<membuffer.size()){
            next=membuffer.get(mempointer++);
        }else{
            if(diskused){
                try{
                    next=RowTransactional.readFromStream(in);
                }catch(EOFException e){
                    next=null;
                }
            }else next=null;
        }
    }
    
    public boolean hasNext() throws IOException{
        if(next!=null)return true;
        return false;
    }
    
    public RowTransactional next() throws IOException{
        try{
            if(next!=null){
                RowTransactional tmp=next;
                readNext();
                return tmp;
            }
        }catch(Exception e){
            e.printStackTrace();
        }
        return null;
    }
}