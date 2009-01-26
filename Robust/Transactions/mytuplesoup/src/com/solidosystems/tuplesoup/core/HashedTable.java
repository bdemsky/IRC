
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
import java.nio.channels.*;
import com.solidosystems.tuplesoup.filter.*;

/**
 * The table stores a group of rows.
 * Every row must have a unique id within a table.
 */
public class HashedTable implements Table{
    private int TABLESETSIZE=5;
    private List<Table> tableset;
    private String title;
    private String location;
    

    
    /**
     * Create a new table object with a specific index model
     */
    public HashedTable(String title,String location, int indextype) throws IOException{
        this.title=title;
        this.location=location;
        tableset=new ArrayList<Table>();
        for(int i=0;i<TABLESETSIZE;i++){
            tableset.add(new DualFileTable(title+"_"+i,location,indextype));
        }
    }
    
    
    public Hashtable<String,Long> readStatistics(){
        Hashtable<String,Long> results=new Hashtable<String,Long>();
        for(int i=0;i<TABLESETSIZE;i++){
            Hashtable<String,Long> tmp=tableset.get(i).readStatistics();
            Set<String> keys=tmp.keySet();
            Iterator<String> it=keys.iterator();
            while(it.hasNext()){
                String key=it.next();
                long value=tmp.get(key);
                if(results.containsKey(key)){
                    results.put(key,results.get(key)+value);
                }else{
                    results.put(key,value);
                }
            }
        }
        return results;
    }
    
    /** 
     * Returns the name of this table
     */ 
    public String getTitle(){
         return title;
    }
    
    /**
     * Returns the location of this tables datafiles
     */ 
    public String getLocation(){
         return location;
    }
    
    /**
     * Delete the files created by this table object.
     * Be aware that this will delete any data stored in this table!
     */ 
    public void deleteFiles(){
        for(int i=0;i<TABLESETSIZE;i++){
            tableset.get(i).deleteFiles();
        }
    }
    
    public void close(){
        for(int i=0;i<TABLESETSIZE;i++){
            tableset.get(i).close();
        }
    }
    
    public void setIndexCacheSize(int size){
        for(int i=0;i<TABLESETSIZE;i++){
            tableset.get(i).setIndexCacheSize(size/TABLESETSIZE);
        }
    }
    
    private Table getTableForId(String id){
        return tableset.get(id.hashCode() & (TABLESETSIZE-1));
    }
    
    /**
      * Returns a single row stored in this table.
      * If the row does not exist in the table, null will be returned.
      */
     public Row getRow(String id) throws IOException{
        Table tbl=getTableForId(id);
        return tbl.getRow(id);
     }
    
    /**
      * Returns a tuplestream of all rows in this table.
      */
     public TupleStream getRows() throws IOException{
        TupleStreamMerger merge=new TupleStreamMerger();
        for(int i=0;i<TABLESETSIZE;i++){
            merge.addStream(tableset.get(i).getRows());
        }
        return merge;
     }
     /**
       * Returns a tuplestream containing the given list of rows
       */
      public TupleStream getRows(List<String> rows) throws IOException{
          List<List<String>> listset=new ArrayList<List<String>>();
          for(int i=0;i<TABLESETSIZE;i++){
              listset.add(new ArrayList<String>());
          }
          for(int i=0;i<rows.size();i++){
              String id=rows.get(i);
              listset.get(id.hashCode() & TABLESETSIZE).add(id);
          }
          TupleStreamMerger merge=new TupleStreamMerger();
          for(int i=0;i<TABLESETSIZE;i++){
              if(listset.get(i).size()>0){
                  merge.addStream(tableset.get(i).getRows(listset.get(i)));
              }
          }
          return merge;
      }

      /**
       * Returns a tuplestream containing the rows matching the given rowmatcher
       */
      public TupleStream getRows(RowMatcher matcher) throws IOException{
          TupleStreamMerger merge=new TupleStreamMerger();
          for(int i=0;i<TABLESETSIZE;i++){
              merge.addStream(tableset.get(i).getRows(matcher));
          }
          return merge;
      }  
        
      /**
       * Returns a tuplestream containing those rows in the given list that matches the given RowMatcher
       */
      public TupleStream getRows(List<String> rows,RowMatcher matcher) throws IOException{
          List<List<String>> listset=new ArrayList<List<String>>();
            for(int i=0;i<TABLESETSIZE;i++){
                listset.add(new ArrayList<String>());
            }
            for(int i=0;i<rows.size();i++){
                String id=rows.get(i);
                listset.get(id.hashCode() & TABLESETSIZE).add(id);
            }
            TupleStreamMerger merge=new TupleStreamMerger();
            for(int i=0;i<TABLESETSIZE;i++){
                if(listset.get(i).size()>0){
                    merge.addStream(tableset.get(i).getRows(listset.get(i),matcher));
                }
            }
            return merge;
      }
      
      /**
        * Marks a row as deleted in the index.
        * Be aware that the space consumed by the row is not actually reclaimed.
        */
       public void deleteRow(Row row) throws IOException{
           getTableForId(row.getId()).deleteRow(row);
       }
       
       /**
        * Adds a row of data to this table.
        */
       public void addRow(Row row) throws IOException{
           getTableForId(row.getId()).addRow(row);
       }

        /**
         * Adds a row to this table if it doesn't already exist, if it does it updates the row instead.
         * This method is much slower than directly using add or update, so only use it if you don't know wether or not the row already exists.
         */
        public void addOrUpdateRow(Row row) throws IOException{
            getTableForId(row.getId()).addOrUpdateRow(row);
        }

        /**
         * Updates a row stored in this table.
         */
        public void updateRow(Row row) throws IOException{
            getTableForId(row.getId()).updateRow(row);
        }
}
