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
import java.nio.channels.*;

public class FlatIndex implements TableIndex{
    private DataOutputStream out=null;
    private String filename;
    
    private long stat_read=0;
    private long stat_write=0;
    private long stat_scan=0;
 
    public FlatIndex(String filename) throws IOException{
        this.filename=filename;
        File ftest=new File(filename);
        if(!ftest.exists())ftest.createNewFile();
    }
 
    public Hashtable<String,Long> readStatistics(){
        Hashtable<String,Long> hash=new Hashtable<String,Long>();
        hash.put("stat_index_read",stat_read);
        hash.put("stat_index_write",stat_write);
        hash.put("stat_index_scan",stat_scan);
        stat_read=0;
        stat_write=0;
        stat_scan=0;
        return hash;
    }
 
    public void close(){
     try{
         out.close();
     }catch(Exception e){}
    }
    
    public List<TableIndexEntry> scanIndex() throws IOException{
        ArrayList<TableIndexEntry> lst=new ArrayList<TableIndexEntry>();
        DataInputStream data=new DataInputStream(new BufferedInputStream(new FileInputStream(filename)));
          try{
              while(true){
                  TableIndexEntry entry=TableIndexEntry.readData(data);
                  stat_read++;
                  if(entry!=null){
                          if(entry.getLocation()!=Table.DELETE){
                              lst.add(entry);
                          }
                    }
              }
          }catch(EOFException eofe){
          }
        return lst;
    }

    private long getOffset(String id) throws IOException{
        DataInputStream in=new DataInputStream(new BufferedInputStream(new FileInputStream(filename)));
        long offset=TableIndexEntry.scanForOffset(id,in);
        stat_scan++;
        in.close();
        return offset;
    }
    
    public synchronized void updateEntry(String id,int rowsize,int location,long position) throws IOException{
        if(out!=null){
            out.close();
            out=null;
        }
        long offset=getOffset(id);
        RandomAccessFile data=new RandomAccessFile(filename,"rw");
        data.seek(offset);
        TableIndexEntry entry=new TableIndexEntry(id,rowsize,location,position);
        entry.writeData(data);
        stat_write++;
        FileChannel fc=data.getChannel();
        fc.force(false);
        data.close();
    }

    public synchronized void addEntry(String id,int rowsize,int location,long position) throws IOException{
     if(out==null)out=new DataOutputStream(new FileOutputStream(filename,true));
     TableIndexEntry entry=new TableIndexEntry(id,rowsize,location,position);
     entry.writeData(out);
     stat_write++;
     out.flush();
    }

    public TableIndexEntry scanIndex(String id) throws IOException{
     DataInputStream data=new DataInputStream(new BufferedInputStream(new FileInputStream(filename)));
     while(true){
         try{
             TableIndexEntry entry=TableIndexEntry.lookForData(id,data);
             stat_read++;
             if(entry!=null){
                 data.close();
                 return entry;
             }
         }catch(EOFException eofe){
             data.close();
             return null;
         }
     }
    }
    
    public List<TableIndexEntry> scanIndex(List<String> rows) throws IOException{
     HashSet<String>rowhash=new HashSet<String>();
      for(int i=0;i<rows.size();i++){
          rowhash.add(rows.get(i));
      }
      Hashtable<String,TableIndexEntry> entries=new Hashtable<String,TableIndexEntry>();
      DataInputStream data=new DataInputStream(new BufferedInputStream(new FileInputStream(filename)));
      try{
          while(true){
              TableIndexEntry entry=TableIndexEntry.readData(data);
              stat_read++;
              if(entry!=null){
                  if(rowhash.contains(entry.getId())){
                      if(entry.getLocation()!=Table.DELETE){
                          entries.put(entry.getId(),entry);
                      }
                  }
                }
          }
      }catch(EOFException eofe){
      }
      List<TableIndexEntry> result=new ArrayList<TableIndexEntry>();
      Iterator<String>it=rows.iterator();
       while(it.hasNext()){
           String id=it.next();
           if(entries.containsKey(id)){
               result.add(entries.get(id));
           }
       }
       return result;
    }
}