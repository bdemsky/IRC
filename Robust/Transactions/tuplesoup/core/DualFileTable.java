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
import java.io.*;
import java.util.*;
import java.nio.channels.*;
import com.solidosystems.tuplesoup.filter.*;
import dstm2.Configs;
import dstm2.SpecialTransactionalFile;
import dstm2.atomic;
import dstm2.Thread;
import dstm2.util.HashMap;
import dstm2.factory.Factory;
import java.util.concurrent.Callable;

/**
 * The table stores a group of rows.
 * Every row must have a unique id within a table.
 */
public class DualFileTable implements Table {

    private int INDEXCACHESIZE = 8192;
    private IOOperations fileastream = null;
    private IOOperations filebstream = null;
    private TableIndex index = null;
    private FilePosition fileaposition;
    private FilePosition filebposition;
    private boolean rowswitch = true;
    private String title;
    private String location;
    private TableIndexNode indexcachefirst;
    private TableIndexNode indexcachelast;
    private intField indexcacheusage;
    //private Hashtable<String, TableIndexNode> indexcache;
    private HashMap<TableIndexNode> indexcache;
    static Factory<FilePosition> filepositionfactory = Thread.makeFactory(FilePosition.class);
    static Factory<intField> intfiledfactory = Thread.makeFactory(intField.class);
    static Factory<TableIndexNode> tableindexnodefactory = Thread.makeFactory(TableIndexNode.class);

    @atomic
    public interface FilePosition {

        Long getFileposition();

        void setFileposition(Long val);
    }

    @atomic
    public interface intField {

        int getintValue();

        void setintValue(int val);
    }

    /**
     * Create a new table object with the default flat index model
     */
    /**
     * Create a new table object with a specific index model
     */
    public DualFileTable(String title, String location, int indextype) throws IOException {

        fileaposition = filepositionfactory.create();
        filebposition = filepositionfactory.create();

        fileaposition.setFileposition((long) 0);
        filebposition.setFileposition((long) 0);

        indexcacheusage = intfiledfactory.create();

        this.title = title;
        this.location = location;
        if (!this.location.endsWith(File.separator)) {
            this.location += File.separator;
        }
        switch (indextype) {
            case PAGED:
                index = new PagedIndex(getFileName(INDEX));
                break;

        }
        indexcachefirst = null;
        indexcachelast = null;
        indexcacheusage.setintValue(0);
        indexcache = new HashMap<TableIndexNode>();
    }

    /**
     * Set the maximal allowable size of the index cache.
     */
    public void setIndexCacheSize(int newsize) {
        INDEXCACHESIZE = newsize;
    }

    /**
     * Close all open file streams
     */
    public void close() {
        try {
            if (fileastream != null) {
                fileastream.close();
            }
            if (filebstream != null) {
                filebstream.close();
            }
            index.close();
        } catch (Exception e) {
        }
    }

    /** 
     * Returns the name of this table
     */
    public String getTitle() {
        return title;
    }

    /**
     * Returns the location of this tables datafiles
     */
    public String getLocation() {
        return location;
    }

    protected String getFileName(int type) {
        switch (type) {
            case FILEB:
                return location + title + ".a";
            case FILEA:
                return location + title + ".b";
            case INDEX:
                return location + title + ".index";
        }
        return null;
    }

    /**
     * Delete the files created by this table object.
     * Be aware that this will delete any data stored in this table!
     */
    public void deleteFiles() {
             try {
        File ftest = new File(getFileName(FILEA));
        ftest.delete();
        } catch (Exception e) {
        }
        try {
        File ftest = new File(getFileName(FILEB));
        ftest.delete();
        } catch (Exception e) {
        }
        try {
        File ftest = new File(getFileName(INDEX));
        ftest.delete();
        } catch (Exception e) {
        }
    }

    private synchronized void openFile(final int type) throws IOException {

        switch (type) {
            case FILEA:
                if (fileastream == null) {
                    if (Configs.inevitable)
                        fileastream = new SpecialTransactionalFile/*RandomAccessFile*//*TransactionalFile*/(getFileName(FILEA), "rw");
                    else 
                        fileastream = new TransactionalFile(getFileName(FILEA), "rw");
                    
                    File ftest = new File(getFileName(FILEA));
                    fileaposition.setFileposition(ftest.length());
                    fileastream.seek(fileaposition.getFileposition());

                }
                break;
            case FILEB:
                if (filebstream == null) {
                    if (Configs.inevitable)
                        filebstream = new SpecialTransactionalFile/*RandomAccessFile*//*TransactionalFile*/(getFileName(FILEB), "rw");
                    else 
                        filebstream = new TransactionalFile(getFileName(FILEB), "rw");
                    
                    File ftest = new File(getFileName(FILEB));
                    filebposition.setFileposition(ftest.length());
                    filebstream.seek(filebposition.getFileposition());
                }
                break;
        }


    }

    /**
     * Adds a row of data to this table.
     */
    public void addRow(Row row) throws IOException {
        // Distribute new rows between the two datafiles by using the rowswitch, but don't spend time synchronizing... this does not need to be acurate!

        if (rowswitch) {
            addRowA(row);
        } else {
            addRowB(row);
        }
        rowswitch = !rowswitch;
    }

    private void addCacheEntry(TableIndexEntry entry) {
           //   synchronized (indexcache) {
        if (indexcacheusage.getintValue() > INDEXCACHESIZE) {
            // remove first entry
            
            TableIndexNode node = indexcachefirst;
            indexcache.remove(node.getData().getId().hashCode());
            int tmp = indexcacheusage.getintValue();
            indexcacheusage.setintValue(tmp - 1);
        //    System.out.println("in the if " + Thread.currentThread());
            indexcachefirst = node.getNext();
            if (indexcachefirst == null) {
                indexcachelast = null;
            } else {
                indexcachefirst.setPrevious(null);
            }
        }
     //   System.out.println("after first if " + Thread.currentThread() + " objetc " + Thread.getTransaction());
        //TableIndexNode node = new TableIndexNode(indexcachelast, entry);
        TableIndexNode node = tableindexnodefactory.create();
        node.setPrevious(indexcachelast);
        node.setData(entry);
        node.setNext(null);

        if (indexcachelast != null) {
            indexcachelast.setNext(node);
        }
        if (indexcachefirst == null) {
            indexcachefirst = node;
        }
        indexcachelast = node;
        indexcache.put(entry.getId().hashCode(), node);
        
        int tmp = indexcacheusage.getintValue();
        indexcacheusage.setintValue(tmp + 1);

    //  }
    }

    private void addRowA(final Row row) throws IOException {
       //   synchronized(objecta){
        openFile(FILEA);
        Thread.doIt(new Callable<Boolean>() {

            public Boolean call() throws IOException {

                int pre = (int) fileastream.getFilePointer();

                row.writeToStream(fileastream);
                int post = (int) fileastream.getFilePointer();

                index.addEntry(row.getId(), row.getSize(), FILEA, fileaposition.getFileposition());

                if (INDEXCACHESIZE > 0) {
                    TableIndexEntry entry = new TableIndexEntry(row.getId(), row.getSize(), FILEA, fileaposition.getFileposition());
                    addCacheEntry(entry);
                }
                long tmp = fileaposition.getFileposition();
                fileaposition.setFileposition(tmp + Row.calcSize(pre, post));
                return true;
            }
       });
     // }
    }

    private void addRowB(final Row row) throws IOException {
        //synchronized(objectb){
        openFile(FILEB);
        Thread.doIt(new Callable<Boolean>() {

            public Boolean call() throws IOException {

                int pre = (int) filebstream.getFilePointer();

                row.writeToStream(filebstream);
                int post = (int) filebstream.getFilePointer();
                //filebstream.flush();
                // System.out.println(row);

                index.addEntry(row.getId(), row.getSize(), FILEB, filebposition.getFileposition());
                if (INDEXCACHESIZE > 0) {
                    TableIndexEntry entry = new TableIndexEntry(row.getId(), row.getSize(), FILEB, filebposition.getFileposition());
                    addCacheEntry(entry);
                }
                long tmp = filebposition.getFileposition();
                filebposition.setFileposition(tmp + Row.calcSize(pre, post));
               return true;
           }
       });
   // }
    }

    private TableIndexEntry getCacheEntry(final String id) {
     //     synchronized (indexcache) {
        
        if (indexcache.containsKey(id.hashCode())) {
            TableIndexNode node = indexcache.get(id.hashCode());
            if (node != indexcachelast) {
                if (node == indexcachefirst) {
                    indexcachefirst = node.getNext();
                }
                //node.remove();
                remove(node);
                indexcachelast.setNext(node);
                node.setPrevious(indexcachelast);
                node.setNext(null);
                indexcachelast = node;
            }

            return node.getData();
        } else {
            return null;
        }
    
     // }
    }

    /**
     * Returns a tuplestream containing the given list of rows
     
    public TupleStream getRows(List<String> rows) throws IOException {
        return new IndexedTableReader(this, index.scanIndex(rows));
    }

    /**
     * Returns a tuplestream containing the rows matching the given rowmatcher
     
    public TupleStream getRows(RowMatcher matcher) throws IOException {
        return new IndexedTableReader(this, index.scanIndex(), matcher);
    }*/

    /**
     * Returns a tuplestream containing those rows in the given list that matches the given RowMatcher
     
    public TupleStream getRows(List<String> rows, RowMatcher matcher) throws IOException {
        return new IndexedTableReader(this, index.scanIndex(rows), matcher);
    }*/

    /**
     * Returns a tuplestream of all rows in this table.
     
    public TupleStream getRows() throws IOException {
        // return new TableReader(this);
        return new IndexedTableReader(this, index.scanIndex());
    }*/

    /**
     * Returns a single row stored in this table.
     * If the row does not exist in the table, null will be returned.
     */
    public Row getRow(final String id) throws IOException {
        TableIndexEntry entry = null;
        // Handle index entry caching
        if (INDEXCACHESIZE > 0) {
         //    synchronized(indexcache){
            entry = Thread.doIt(new Callable<TableIndexEntry>() {

                public TableIndexEntry call() throws IOException {
                    TableIndexEntry entry = getCacheEntry(id);
                    if (entry == null) {
                        entry = index.scanIndex(id);
                        if (entry != null) {
                            addCacheEntry(entry);
                        }
                    }
                    return entry;
                  }
            });
       // }
        } else {
            entry = index.scanIndex(id);
        }
        if (entry != null) {
            long dataoffset = 0;
            DataInputStream data = null;
            if (entry.location == Table.FILEA) {
                data = new DataInputStream(new BufferedInputStream(new FileInputStream(getFileName(Table.FILEA))));
            } else if (entry.location == Table.FILEB) {
                data = new DataInputStream(new BufferedInputStream(new FileInputStream(getFileName(Table.FILEB))));
            }
            if (data != null) {
                while (dataoffset != entry.position) {
                    dataoffset += data.skipBytes((int) (entry.position - dataoffset));
                }
                Row row = Row.readFromStream(data);
                data.close();


                return row;
            }

        }
        return null;
    }

    public void remove(TableIndexNode node) {
        if (node.getPrevious() != null) {
            node.getPrevious().setNext(node.getNext());
        }
        if (node.getNext() != null) {
            node.getNext().setPrevious(node.getPrevious());
        }
    }

    public TupleStream getRows(List<String> rows) throws IOException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public TupleStream getRows(RowMatcher matcher) throws IOException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public TupleStream getRows(List<String> rows, RowMatcher matcher) throws IOException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public TupleStream getRows() throws IOException {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}