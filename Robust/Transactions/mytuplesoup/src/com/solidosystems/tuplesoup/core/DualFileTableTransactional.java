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
import dstm2.atomic;
import dstm2.util.StringKeyHashMap;

/**
 * The table stores a group of rows.
 * Every row must have a unique id within a table.
 */
public class DualFileTableTransactional implements TableTransactional{

    
    private int INDEXCACHESIZE=8192;
     
    private String filealock="filea-dummy";
    private String fileblock="fileb-dummy";

    private DataOutputStream fileastream=null;
    private DataOutputStream filebstream=null;
    private RandomAccessFile filearandom=null;
    private RandomAccessFile filebrandom=null;
    FileChannel fca=null;
    FileChannel fcb=null;
    private TableIndexTransactional index=null;
     
    private long fileaposition=0;
    private long filebposition=0;
     
    private boolean rowswitch=true;
     
    private String title;
    private String location;
     
    private TableIndexNodeTransactional indexcachefirst;
    private TableIndexNodeTransactional indexcachelast;
    private int indexcacheusage;
    
    private StringKeyHashMap<TableIndexNodeTransactional> indexcache;
    //private Hashtable<String,TableIndexNode> indexcache;
    
    
    DualFileTableTSInf atomicfields;
    // Statistic counters
    public @atomic interface DualFileTableTSInf{
        long getstat_add();
        long getstat_update();
        long getstat_delete();
        long getstat_add_size();
        long getstat_update_size();
        long getstat_read_size();
        long getstat_read();
        long getstat_cache_hit();
        long getstat_cache_miss();
        long getstat_cache_drop();
        
        void setstat_add(long val);
        void setstat_update(long val);
        void setstat_delete(long val);
        void setstat_add_size(long val);
        void setstat_update_size(long val);
        void setstat_read_size(long val);
        void setstat_read(long val);
        void setstat_cache_hit(long val);
        void setstat_cache_miss(long val);
        void setstat_cache_drop(long val);
    }
  
    /*long stat_add=0;
    long stat_update=0;
    long stat_delete=0;
    long stat_add_size=0;
    long stat_update_size=0;
    long stat_read_size=0;
    long stat_read=0;
    long stat_cache_hit=0;
    long stat_cache_miss=0;
    long stat_cache_drop=0;*/
    
    protected String statlock="stat-dummy";
    
    /**
     * Return the current values of the statistic counters and reset them.
     * The current counters are:
     * <ul>
     *   <li>stat_table_add
     *   <li>stat_table_update
     *   <li>stat_table_delete
     *   <li>stat_table_add_size
     *   <li>stat_table_update_size
     *   <li>stat_table_read_size
     *   <li>stat_table_read
     *   <li>stat_table_cache_hit
     *   <li>stat_table_cache_miss
     *   <li>stat_table_cache_drop
     * </ul>
     * Furthermore, the index will be asked to deliver separate index specific counters
     */
    public Hashtable<String,Long> readStatistics(){
        Hashtable<String,Long> hash=new Hashtable<String,Long>();
        synchronized(statlock){
            hash.put("stat_table_add",atomicfields.getstat_add());
            hash.put("stat_table_update",atomicfields.getstat_update());
            hash.put("stat_table_delete",atomicfields.getstat_delete());
            hash.put("stat_table_add_size",atomicfields.getstat_add_size());
            hash.put("stat_table_update_size",atomicfields.getstat_update_size());
            hash.put("stat_table_read_size",atomicfields.getstat_read_size());
            hash.put("stat_table_read",atomicfields.getstat_read());
            hash.put("stat_table_cache_hit",atomicfields.getstat_cache_hit());
            hash.put("stat_table_cache_miss",atomicfields.getstat_cache_miss());
            hash.put("stat_table_cache_drop",atomicfields.getstat_cache_drop());
            atomicfields.setstat_add(0);
            atomicfields.setstat_update(0);
            atomicfields.setstat_delete(0);
            atomicfields.setstat_add_size(0);
            atomicfields.setstat_update_size(0);
            atomicfields.setstat_read_size(0);
            atomicfields.setstat_read(0);
            atomicfields.setstat_cache_hit(0);
            atomicfields.setstat_cache_miss(0);
            atomicfields.setstat_cache_drop(0);
            Hashtable<String,Long> ihash=index.readStatistics();
            hash.putAll(ihash);
        }
        return hash;
    }
    
    /**
     * Create a new table object with the default flat index model
     */

    
    /**
     * Create a new table object with a specific index model
     */
    public DualFileTableTransactional(String title,String location, int indextype) throws IOException{
        this.title=title;
        this.location=location;
        if(!this.location.endsWith(File.separator))this.location+=File.separator;
        switch(indextype){
             case PAGED  : index=new PagedIndexTransactional(getFileName(INDEX));
                break;
           
        }
        indexcachefirst=null;
        indexcachelast=null;
        indexcacheusage=0;
        indexcache=new StringKeyHashMap<TableIndexNodeTransactional>();
    }
     
    /**
     * Set the maximal allowable size of the index cache.
     */ 
    public void setIndexCacheSize(int newsize){
        INDEXCACHESIZE=newsize;
    }
    
    /**
     * Close all open file streams
     */
    public void close(){
        try{
            if(fileastream!=null)fileastream.close();
            if(filebstream!=null)filebstream.close();
            if(filearandom!=null)filearandom.close();
            if(filebrandom!=null)filebrandom.close();
            index.close();
        }catch(Exception e){}
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
     
    protected String getFileName(int type){
         switch(type){
             case FILEB  :   return location+title+".a";
             case FILEA  :   return location+title+".b";
             case INDEX  :   return location+title+".index";
         }
         return null;
    }
    
    /**
     * Delete the files created by this table object.
     * Be aware that this will delete any data stored in this table!
     */ 
    public void deleteFiles(){
         try{
             File ftest=new File(getFileName(FILEA));
             ftest.delete();
         }catch(Exception e){}
         try{
             File ftest=new File(getFileName(FILEB));
             ftest.delete();
         }catch(Exception e){}
         try{
             File ftest=new File(getFileName(INDEX));
             ftest.delete();
         }catch(Exception e){}
    }
     
     private synchronized void openFile(int type) throws IOException{
         switch(type){
             case FILEA  : if(fileastream==null){
                                fileastream=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(getFileName(FILEA),true)));
                                File ftest=new File(getFileName(FILEA));
                                fileaposition=ftest.length();
                           }
                          break;
             case FILEB  : if(filebstream==null){
                                filebstream=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(getFileName(FILEB),true)));
                                File ftest=new File(getFileName(FILEB));
                                filebposition=ftest.length();
                           }
                          break;
         }
     }
     
    /**
     * Adds a row of data to this table.
     */
    public void addRow(RowTransactional row) throws IOException{
        // Distribute new rows between the two datafiles by using the rowswitch, but don't spend time synchronizing... this does not need to be acurate!
        if(rowswitch){
            addRowA(row);
        }else{
            addRowB(row);
        }
        rowswitch=!rowswitch;
    }
     
     private void addCacheEntry(TableIndexEntryTransactional entry){
         synchronized(indexcache){
             if(indexcacheusage>INDEXCACHESIZE){
                 // remove first entry
                 TableIndexNodeTransactional node=indexcachefirst;
                 indexcache.remove(node.getData().getId());
                 indexcacheusage--;
                 synchronized(statlock){
                     atomicfields.setstat_cache_drop(atomicfields.getstat_cache_drop()+1);
                 }
                 indexcachefirst=node.getNext();
                 if(indexcachefirst==null){
                    indexcachelast=null;
                 }else{
                    indexcachefirst.setPrevious(null);
                 }
             }
             TableIndexNodeTransactional node=new TableIndexNodeTransactional(indexcachelast,entry);
             if(indexcachelast!=null){
                 indexcachelast.setNext(node);
             }
             if(indexcachefirst==null){
                 indexcachefirst=node;
             }
             indexcachelast=node;
             indexcache.put(entry.getId(),node);
             indexcacheusage++;
        }
     }
     
     private void addRowA(RowTransactional row) throws IOException{
         synchronized(filealock){
             openFile(FILEA);
             int pre=fileastream.size();
             row.writeToStream(fileastream);
             int post=fileastream.size();
             fileastream.flush();
             
             synchronized(statlock){
                  atomicfields.setstat_add(atomicfields.getstat_add()+1);
                  atomicfields.setstat_add_size(atomicfields.getstat_add_size()+row.getSize());
             }
             
             index.addEntry(row.getId(),row.getSize(),FILEA,fileaposition);
             if(INDEXCACHESIZE>0){
                 TableIndexEntryTransactional entry=new TableIndexEntryTransactional(row.getId(),row.getSize(),FILEA,fileaposition);
                 addCacheEntry(entry);
             }
             fileaposition+=Row.calcSize(pre,post);
         }
     }
     private void addRowB(RowTransactional row) throws IOException{
         synchronized(fileblock){
             openFile(FILEB);
             int pre=filebstream.size();
             row.writeToStream(filebstream);
             int post=filebstream.size();
             filebstream.flush();
             synchronized(statlock){
                  atomicfields.setstat_add(atomicfields.getstat_add()+1);
                  atomicfields.setstat_add_size(atomicfields.getstat_add_size()+row.getSize());
              }
             index.addEntry(row.getId(),row.getSize(),FILEB,filebposition);
             if(INDEXCACHESIZE>0){
                 TableIndexEntryTransactional entry=new TableIndexEntryTransactional(row.getId(),row.getSize(),FILEB,filebposition);
                 addCacheEntry(entry);
             }
             filebposition+=RowTransactional.calcSize(pre,post);
         }
     }
     

     private void updateCacheEntry(TableIndexEntryTransactional entry){
          synchronized(indexcache){
              if(indexcache.containsKey(entry.getId())){
                  TableIndexNodeTransactional node=indexcache.get(entry.getId());
                  node.setData(entry);
                  if(node!=indexcachelast){
                      if(node==indexcachefirst){
                          indexcachefirst=node.getNext();
                      }
                      node.remove();
                      indexcachelast.setNext(node);
                      node.setPrevious(indexcachelast);
                      node.setNext(null);
                      indexcachelast=node;
                  }
              }else{
                  addCacheEntry(entry);
              }
         }
      }

      private void removeCacheEntry(String id){
          synchronized(indexcache){
                if(indexcache.containsKey(id)){
                    TableIndexNodeTransactional node=indexcache.get(id);
                    indexcache.remove(id);
                    if(indexcacheusage==1){
                        indexcachefirst=null;
                        indexcachelast=null;
                        indexcacheusage=0;
                        return;
                    }
                    if(node==indexcachefirst){
                        indexcachefirst=node.getNext();
                        indexcachefirst.setPrevious(null);
                    }else if(node==indexcachelast){
                        indexcachelast=node.getPrevious();
                        indexcachelast.setNext(null);
                    }else{
                        node.remove();
                    }
                    indexcacheusage--;
                    synchronized(statlock){
                         atomicfields.setstat_cache_drop(atomicfields.getstat_cache_drop()+1);
                    }
                }
          }
      }

      private TableIndexEntryTransactional getCacheEntry(String id){
          synchronized(indexcache){
              if(indexcache.containsKey(id)){
                  TableIndexNodeTransactional node=indexcache.get(id);
                  if(node!=indexcachelast){
                      if(node==indexcachefirst){
                            indexcachefirst=node.getNext();
                        }
                        node.remove();
                        indexcachelast.setNext(node);
                        node.setPrevious(indexcachelast);
                        node.setNext(null);
                        indexcachelast=node;
                  }
                  synchronized(statlock){
                       atomicfields.setstat_cache_hit(atomicfields.getstat_cache_hit()+1);
                   }
                  return node.getData();
              }
          }
          synchronized(statlock){
               atomicfields.setstat_cache_miss(atomicfields.getstat_cache_miss()+1);
           }
          return null;
      }

     /**
      * Adds a row to this table if it doesn't already exist, if it does it updates the row instead.
      * This method is much slower than directly using add or update, so only use it if you don't know wether or not the row already exists.
      */
     public void addOrUpdateRow(RowTransactional row) throws IOException{
         RowTransactional tmprow=getRow(row.getId());
         if(tmprow==null){
             addRow(row);
         }else{
             updateRow(row);
         }
     }

     /**
      * Updates a row stored in this table.
      */
     public void updateRow(RowTransactional row) throws IOException{
         TableIndexEntryTransactional entry=null;
         // Handle index entry caching
         if(INDEXCACHESIZE>0){
             synchronized(indexcache){
                 entry=getCacheEntry(row.getId());
                 if(entry==null){
                     entry=index.scanIndex(row.getId());
                     addCacheEntry(entry);
                 }
             }
         }else{
             entry=index.scanIndex(row.getId());
         }
         if(entry.getRowSize()>=row.getSize()){
            // Add to the existing location
            switch(entry.getLocation()){
                case FILEA  :synchronized(filealock){
                                if(filearandom==null){
                                    filearandom=new RandomAccessFile(getFileName(FILEA),"rw");
                                    fca=filearandom.getChannel();
                                }
                                filearandom.seek(entry.getPosition());
                                row.writeToFile(filearandom);
                                
                                fca.force(false);
                            }
                            break;
                case FILEB :synchronized(fileblock){
                                if(filebrandom==null){
                                    filebrandom=new RandomAccessFile(getFileName(FILEB),"rw");
                                    fcb=filebrandom.getChannel();
                                }
                                filebrandom.seek(entry.getPosition());
                                row.writeToFile(filebrandom);
                                
                                fcb.force(false);
                            }
                    break;
            }
         }else{
             if(rowswitch){
                  updateRowA(row);
              }else{
                  updateRowB(row);
              }
              rowswitch=!rowswitch;
         }
         synchronized(statlock){
              atomicfields.setstat_update(atomicfields.getstat_update()+1);
              atomicfields.setstat_update_size(atomicfields.getstat_update_size()+row.getSize());
         }
     }
     
     private void updateRowA(RowTransactional row) throws IOException{
         synchronized(filealock){
              openFile(FILEA);
              int pre=fileastream.size();
              row.writeToStream(fileastream);
              int post=fileastream.size();
              fileastream.flush();
              index.updateEntry(row.getId(),row.getSize(),FILEA,fileaposition);
              
              // Handle index entry caching
              if(INDEXCACHESIZE>0){
                  updateCacheEntry(new TableIndexEntryTransactional(row.getId(),row.getSize(),FILEA,fileaposition));
              }
              fileaposition+=Row.calcSize(pre,post);
          }
     }

     private void updateRowB(RowTransactional row) throws IOException{
         synchronized(fileblock){
              openFile(FILEB);
              int pre=filebstream.size();
              row.writeToStream(filebstream);
              int post=filebstream.size();
              filebstream.flush();
              index.updateEntry(row.getId(),row.getSize(),FILEB,filebposition);
              // Handle index entry caching
              // Handle index entry caching
                if(INDEXCACHESIZE>0){
                    updateCacheEntry(new TableIndexEntryTransactional(row.getId(),row.getSize(),FILEB,filebposition));
                }
                filebposition+=Row.calcSize(pre,post);
          }
     }

     /**
      * Marks a row as deleted in the index.
      * Be aware that the space consumed by the row is not actually reclaimed.
      */
     public void deleteRow(RowTransactional row) throws IOException{
          // Handle index entry caching
          if(INDEXCACHESIZE>0){
              removeCacheEntry(row.getId());
          }
          index.updateEntry(row.getId(),row.getSize(),DELETE,0);
          synchronized(statlock){
              atomicfields.setstat_delete(atomicfields.getstat_delete()+1);
          }
     }
     
     /**
      * Returns a tuplestream containing the given list of rows
      */
     public TupleStreamTransactional getRows(List<String> rows) throws IOException{
         return new IndexedTableReaderTransactional(this,index.scanIndex(rows));
     }

     /**
      * Returns a tuplestream containing the rows matching the given rowmatcher
      */
     public TupleStreamTransactional getRows(RowMatcherTransactional matcher) throws IOException{
         return new IndexedTableReaderTransactional(this,index.scanIndex(),matcher);
     }
     
     /**
      * Returns a tuplestream containing those rows in the given list that matches the given RowMatcher
      */
     public TupleStreamTransactional getRows(List<String> rows,RowMatcherTransactional matcher) throws IOException{
          return new IndexedTableReaderTransactional(this,index.scanIndex(rows),matcher);
      }

     /**
      * Returns a tuplestream of all rows in this table.
      */
     public TupleStreamTransactional getRows() throws IOException{
         // return new TableReader(this);
         return new IndexedTableReaderTransactional(this,index.scanIndex());
     }
     
     /**
      * Returns a single row stored in this table.
      * If the row does not exist in the table, null will be returned.
      */
     public RowTransactional getRow(String id) throws IOException{
         TableIndexEntryTransactional entry=null;
          // Handle index entry caching
          if(INDEXCACHESIZE>0){
              synchronized(indexcache){
                  entry=getCacheEntry(id);
                   if(entry==null){
                       entry=index.scanIndex(id);
                       if(entry!=null){
                           addCacheEntry(entry);
                       }
                   }
              }
          }else{
              entry=index.scanIndex(id);
          }
          if(entry!=null){
              long dataoffset=0;
              DataInputStream data=null;
              if(entry.getLocation()==Table.FILEA){
                  data=new DataInputStream(new BufferedInputStream(new FileInputStream(getFileName(Table.FILEA))));
              }else if(entry.getLocation()==Table.FILEB){
                  data=new DataInputStream(new BufferedInputStream(new FileInputStream(getFileName(Table.FILEB))));
              }
              if(data!=null){
                  while(dataoffset!=entry.getPosition()){
                      dataoffset+=data.skipBytes((int)(entry.getPosition()-dataoffset));
                  }
                  RowTransactional row=RowTransactional.readFromStream(data);
                  data.close();
                  synchronized(statlock){
                       atomicfields.setstat_read(atomicfields.getstat_read()+1);
                       atomicfields.setstat_read_size(atomicfields.getstat_read_size()+row.getSize());
                  }
                  return row;
              }
              
          }
          return null;
     }
 }