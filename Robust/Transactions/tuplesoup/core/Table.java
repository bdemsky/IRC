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
public interface Table{
    // Index type constants
    public static final int MEMORY=0;
    public static final int FLAT=1;
    public static final int PAGED=2;
     
    // Row location constants
    public static final int FILEA=0;
    public static final int FILEB=1;
    public static final int DELETE=2;
    public static final int INDEX=3;
     
  
    /**
     * Set the maximal allowable size of the index cache.
     */ 
    public void setIndexCacheSize(int newsize);

    /**
     * Close all open file streams
     */
    public void close();

    /** 
     * Returns the name of this table
     */ 
    public String getTitle();
    
    /**
     * Returns the location of this tables datafiles
     */ 
    public String getLocation();
    
    /**
     * Delete the files created by this table object.
     * Be aware that this will delete any data stored in this table!
     */ 
    public void deleteFiles();
     
    /**
     * Adds a row of data to this table.
     */
    public void addRow(Row row) throws IOException;
    
    
     /**
      * Returns a tuplestream containing the given list of rows
      */
     public TupleStream getRows(List<String> rows) throws IOException;
     
     /**
      * Returns a tuplestream containing the rows matching the given rowmatcher
      */
     public TupleStream getRows(RowMatcher matcher) throws IOException;
     
     /**
      * Returns a tuplestream containing those rows in the given list that matches the given RowMatcher
      */
     public TupleStream getRows(List<String> rows,RowMatcher matcher) throws IOException;
     
     /**
      * Returns a tuplestream of all rows in this table.
      */
     public TupleStream getRows() throws IOException;
     
     /**
      * Returns a single row stored in this table.
      * If the row does not exist in the table, null will be returned.
      */
     public Row getRow(String id) throws IOException;
 }