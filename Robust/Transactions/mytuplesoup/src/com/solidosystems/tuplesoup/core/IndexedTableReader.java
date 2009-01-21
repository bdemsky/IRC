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
 
 import com.solidosystems.tuplesoup.filter.*;
 import java.io.*;
 import java.util.*;

public class IndexedTableReader extends TupleStream{
    private DataInputStream fileastream=null;
    private DataInputStream filebstream=null;
    private long fileaposition=0;
    private long filebposition=0;

    private List<TableIndexEntry>fileaentries;
    private List<TableIndexEntry>filebentries;
    
    private List<TableIndexEntry>entries;

    private Hashtable<String,Row>fileabuffer;
    private Hashtable<String,Row>filebbuffer;

    private List<String>rows;
    private int rowpointer;
    private Row next=null;
    
    private DualFileTable table;
    
    private RowMatcher matcher=null;
    
    public IndexedTableReader(DualFileTable table,List<TableIndexEntry>entries) throws IOException{
        this.table=table;
        this.rows=rows;
        rowpointer=0;
        
        this.entries=entries;
        fileaentries=new ArrayList<TableIndexEntry>();
        filebentries=new ArrayList<TableIndexEntry>();
        
        Iterator<TableIndexEntry> it=entries.iterator();
        while(it.hasNext()){
            TableIndexEntry entry=it.next();
            // TODO: we really shouldn't get nulls here
            if(entry!=null){
                if(entry.location==Table.FILEA){
                    fileaentries.add(entry);
                }else if(entry.location==Table.FILEB){
                    filebentries.add(entry);
                }
            }
        }
        
        Collections.sort(fileaentries);
        Collections.sort(filebentries);
        
        fileabuffer=new Hashtable<String,Row>();
        filebbuffer=new Hashtable<String,Row>();
        
        readNext();   
    }
    
    
    public IndexedTableReader(DualFileTable table,List<TableIndexEntry>entries,RowMatcher matcher) throws IOException{
        this.table=table;
        this.rows=rows;
        rowpointer=0;
        this.matcher=matcher;
        
        this.entries=entries;
        fileaentries=new ArrayList<TableIndexEntry>();
        filebentries=new ArrayList<TableIndexEntry>();
        
        Iterator<TableIndexEntry> it=entries.iterator();
        while(it.hasNext()){
            TableIndexEntry entry=it.next();
            // TODO: we really shouldn't get nulls here
            if(entry!=null){
                if(entry.location==Table.FILEA){
                    fileaentries.add(entry);
                }else if(entry.location==Table.FILEB){
                    filebentries.add(entry);
                }
            }
        }
        
        Collections.sort(fileaentries);
        Collections.sort(filebentries);
        
        fileabuffer=new Hashtable<String,Row>();
        filebbuffer=new Hashtable<String,Row>();
        
        readNext();   
    }
    
    private void readNextFromFileA(TableIndexEntry entry) throws IOException{
        if(fileabuffer.containsKey(entry.id)){
            next=fileabuffer.remove(entry.id);
            return;
        }
        while(true){
            if(fileaentries.size()>0){
                TableIndexEntry nextfilea=fileaentries.remove(0);
                if(fileastream==null){
                    fileastream=new DataInputStream(new BufferedInputStream(new FileInputStream(table.getFileName(Table.FILEA))));
                    fileaposition=0;
                }
                if(fileaposition>nextfilea.position){
                    // We have already read this entry... skip it
                    // readNextFromFileA(entry);
                    // return;
                }else{
                    while(fileaposition!=nextfilea.position){
                        fileaposition+=fileastream.skipBytes((int)(nextfilea.position-fileaposition));
                    }
                    Row row=Row.readFromStream(fileastream);
                    synchronized(table.statlock){
                        table.stat_read_size+=row.getSize();
                        table.stat_read++;
                    }
                    fileaposition+=row.getSize();
                    if(row.getId().equals(entry.id)){
                        next=row;
                        return;
                    }else{
                        fileabuffer.put(row.getId(),row);
                        // readNextFromFileA(entry);
                    }
                }
            }else{
                next=null;
                return;
            }
        }
    }
    
    private void readNextFromFileB(TableIndexEntry entry) throws IOException{
        if(filebbuffer.containsKey(entry.id)){
            next=filebbuffer.remove(entry.id);
            return;
        }
        while(true){
            if(filebentries.size()>0){
                TableIndexEntry nextfileb=filebentries.remove(0);
                if(filebstream==null){
                    filebstream=new DataInputStream(new BufferedInputStream(new FileInputStream(table.getFileName(Table.FILEB))));
                    filebposition=0;
                }
                if(filebposition>nextfileb.position){
                    // We have already read this entry... skip it
                    // readNextFromFileB(entry);
                    // return;
                }else{
                    while(filebposition!=nextfileb.position){
                        filebposition+=filebstream.skipBytes((int)(nextfileb.position-filebposition));
                    }
                    Row row=Row.readFromStream(filebstream);
                    synchronized(table.statlock){
                        table.stat_read_size+=row.getSize();
                        table.stat_read++;
                    }
                    filebposition+=row.getSize();
                    if(row.getId().equals(entry.id)){
                        next=row;
                        return;
                    }else{
                        filebbuffer.put(row.getId(),row);
                        // readNextFromFileB(entry);
                    }
                }
            }else{
                next=null;
                return;
            }
        }
    }
    
    private void readNext() throws IOException{
        if(entries.size()>rowpointer){
            TableIndexEntry entry=entries.get(rowpointer++);
            if(entry!=null){
                   switch(entry.location){
                    case Table.FILEA    : readNextFromFileA(entry);
                                        // return;
                                        break;
                    case Table.FILEB : readNextFromFileB(entry);
                                        // return;
                                        break;
                }
                if(next!=null){
                    if(matcher!=null){
                        if(!matcher.matches(next)){
                             readNext();
                        }
                    }
                }
                return;
            }else{
                readNext();
                return;
            }
        }
        try{
            if(fileastream!=null)fileastream.close();
        }catch(Exception e){}
        try{
            if(filebstream!=null)filebstream.close();
        }catch(Exception e){}
        next=null;
    }
    
    public boolean hasNext(){
        if(next!=null)return true;
        return false;
    }
    
    public Row next(){
        try{
            if(next!=null){
                Row tmp=next;
                readNext();
                return tmp;
            }
        }catch(Exception e){
            e.printStackTrace();
        }
        return null;
    }
    
    public void remove(){
        
    }
}