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

public class IndexedTableReaderTransactional extends TupleStreamTransactional{
    private DataInputStream fileastream=null;
    private DataInputStream filebstream=null;
    private long fileaposition=0;
    private long filebposition=0;

    private List<TableIndexEntryTransactional>fileaentries;
    private List<TableIndexEntryTransactional>filebentries;
    
    private List<TableIndexEntryTransactional>entries;

    private Hashtable<String,RowTransactional>fileabuffer;
    private Hashtable<String,RowTransactional>filebbuffer;

    private List<String>rows;
    private int rowpointer;
    private RowTransactional next=null;
    
    private DualFileTableTransactional table;
    
    private RowMatcherTransactional matcher=null;
    
    public IndexedTableReaderTransactional(DualFileTableTransactional table,List<TableIndexEntryTransactional>entries) throws IOException{
        this.table=table;
        this.rows=rows;
        rowpointer=0;
        
        this.entries=entries;
        fileaentries=new ArrayList<TableIndexEntryTransactional>();
        filebentries=new ArrayList<TableIndexEntryTransactional>();
        
        Iterator<TableIndexEntryTransactional> it=entries.iterator();
        while(it.hasNext()){
            TableIndexEntryTransactional entry=it.next();
            // TODO: we really shouldn't get nulls here
            if(entry!=null){
                if(entry.getLocation()==Table.FILEA){
                    fileaentries.add(entry);
                }else if(entry.getLocation()==Table.FILEB){
                    filebentries.add(entry);
                }
            }
        }
        
        Collections.sort(fileaentries);
        Collections.sort(filebentries);
        
        fileabuffer=new Hashtable<String,RowTransactional>();
        filebbuffer=new Hashtable<String,RowTransactional>();
        
        readNext();   
    }
    
    
    public IndexedTableReaderTransactional(DualFileTableTransactional table,List<TableIndexEntryTransactional>entries,RowMatcherTransactional matcher) throws IOException{
        this.table=table;
        this.rows=rows;
        rowpointer=0;
        this.matcher=matcher;
        
        this.entries=entries;
        fileaentries=new ArrayList<TableIndexEntryTransactional>();
        filebentries=new ArrayList<TableIndexEntryTransactional>();
        
        Iterator<TableIndexEntryTransactional> it=entries.iterator();
        while(it.hasNext()){
            TableIndexEntryTransactional entry=it.next();
            // TODO: we really shouldn't get nulls here
            if(entry!=null){
                if(entry.getLocation()==Table.FILEA){
                    fileaentries.add(entry);
                }else if(entry.getLocation()==Table.FILEB){
                    filebentries.add(entry);
                }
            }
        }
        
        Collections.sort(fileaentries);
        Collections.sort(filebentries);
        
        fileabuffer=new Hashtable<String,RowTransactional>();
        filebbuffer=new Hashtable<String,RowTransactional>();
        
        readNext();   
    }
    
    private void readNextFromFileA(TableIndexEntryTransactional entry) throws IOException{
        if(fileabuffer.containsKey(entry.getId())){
            next=fileabuffer.remove(entry.getId());
            return;
        }
        while(true){
            if(fileaentries.size()>0){
                TableIndexEntryTransactional nextfilea=fileaentries.remove(0);
                if(fileastream==null){
                    fileastream=new DataInputStream(new BufferedInputStream(new FileInputStream(table.getFileName(Table.FILEA))));
                    fileaposition=0;
                }
                if(fileaposition>nextfilea.getPosition()){
                    // We have already read this entry... skip it
                    // readNextFromFileA(entry);
                    // return;
                }else{
                    while(fileaposition!=nextfilea.getPosition()){
                        fileaposition+=fileastream.skipBytes((int)(nextfilea.getPosition()-fileaposition));
                    }
                    RowTransactional row=RowTransactional.readFromStream(fileastream);
                    synchronized(table.statlock){
                       table.atomicfields.setstat_read_size(table.atomicfields.getstat_read_size()+row.getSize());
                       table.atomicfields.setstat_read(table.atomicfields.getstat_read()+1);
                    }
                    fileaposition+=row.getSize();
                    if(row.getId().equals(entry.getId())){
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
    
    private void readNextFromFileB(TableIndexEntryTransactional entry) throws IOException{
        if(filebbuffer.containsKey(entry.getId())){
            next=filebbuffer.remove(entry.getId());
            return;
        }
        while(true){
            if(filebentries.size()>0){
                TableIndexEntryTransactional nextfileb=filebentries.remove(0);
                if(filebstream==null){
                    filebstream=new DataInputStream(new BufferedInputStream(new FileInputStream(table.getFileName(Table.FILEB))));
                    filebposition=0;
                }
                if(filebposition>nextfileb.getPosition()){
                    // We have already read this entry... skip it
                    // readNextFromFileB(entry);
                    // return;
                }else{
                    while(filebposition!=nextfileb.getPosition()){
                        filebposition+=filebstream.skipBytes((int)(nextfileb.getPosition()-filebposition));
                    }
                    RowTransactional row=RowTransactional.readFromStream(filebstream);
                    synchronized(table.statlock){
                        table.atomicfields.setstat_read_size(table.atomicfields.getstat_read_size()+row.getSize());
                        table.atomicfields.setstat_read(table.atomicfields.getstat_read()+1);
                    }
                    filebposition+=row.getSize();
                    if(row.getId().equals(entry.getId())){
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
            TableIndexEntryTransactional entry=entries.get(rowpointer++);
            if(entry!=null){
                   switch(entry.getLocation()){
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
    
    public RowTransactional next(){
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
    
    public void remove(){
        
    }
}