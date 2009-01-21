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

import java.util.*;
import java.io.*;

public class MemoryIndex implements TableIndex{
    private DataOutputStream out=null;
    private String filename;
    private Hashtable<String,TableIndexEntry> cache;
    
    private long stat_write=0;
 
    public MemoryIndex(String filename) throws IOException{
        this.filename=filename;
        File ftest=new File(filename);
        if(!ftest.exists())ftest.createNewFile();
        cache=new Hashtable<String,TableIndexEntry>();
        DataInputStream in=new DataInputStream(new BufferedInputStream(new FileInputStream(filename)));
        try{
            while(true){
                TableIndexEntry entry=TableIndexEntry.readData(in);
                if(entry.getLocation()==Table.DELETE){
                    cache.remove(entry.getId());
                }else{
                    cache.put(entry.getId(),entry);
                }
            }
        }catch(EOFException e){}
        out=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(filename,true)));
    }
    
    public Hashtable<String,Long> readStatistics(){
        Hashtable<String,Long> hash=new Hashtable<String,Long>();
        hash.put("stat_index_write",stat_write);
        stat_write=0;
        return hash;
    }
 
    public void close(){
     try{
         out.close();
     }catch(Exception e){}
    }
    
    public synchronized List<TableIndexEntry> scanIndex() throws IOException{
        ArrayList<TableIndexEntry> lst=new ArrayList<TableIndexEntry>();
        Iterator<TableIndexEntry> it=cache.values().iterator();
        while(it.hasNext()){
            TableIndexEntry entry=it.next();
            if(entry.getLocation()!=Table.DELETE)lst.add(entry);
        }
        return lst;
    }
    
    public synchronized void updateEntry(String id,int rowsize,int location,long position) throws IOException{
        TableIndexEntry entry=new TableIndexEntry(id,rowsize,location,position);
        entry.writeData(out);
        out.flush();
        stat_write++;
        cache.put(entry.getId(),entry);
    }

    public synchronized void addEntry(String id,int rowsize,int location,long position) throws IOException{
        TableIndexEntry entry=new TableIndexEntry(id,rowsize,location,position);
        entry.writeData(out);
        out.flush();
        stat_write++;
        cache.put(entry.getId(),entry);
    }

    public synchronized TableIndexEntry scanIndex(String id) throws IOException{
        if(!cache.containsKey(id))return null;
        return cache.get(id);
    }
    
    public synchronized List<TableIndexEntry> scanIndex(List<String> rows) throws IOException{
        List<TableIndexEntry> result=new ArrayList<TableIndexEntry>();
        for(int i=0;i<rows.size();i++){
            if(cache.containsKey(rows.get(i))){
                TableIndexEntry entry=cache.get(rows.get(i));
                if(entry!=null)result.add(entry);
            }
        }
        return result;
    }
}