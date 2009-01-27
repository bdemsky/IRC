/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.solidosystems.tuplesoup.core;

import dstm2.AtomicSuperClass;
import java.util.*;
import java.io.*;

public interface TableIndexTransactional extends AtomicSuperClass{
    public Hashtable<String,Long> readStatistics();
    public void updateEntry(String id,int rowsize,int location,long position) throws IOException;
    public void updateEntryTransactional(String id,int rowsize,int location,long position) throws IOException;
    public void addEntry(String id,int rowsize,int location,long position) throws IOException;
    public TableIndexEntryTransactional scanIndex(String id) throws IOException;
    public TableIndexEntryTransactional scanIndexTransactional(String id) throws IOException;
    public List<TableIndexEntryTransactional> scanIndex(List<String> rows) throws IOException;
    public List<TableIndexEntryTransactional> scanIndex() throws IOException;
    public void close();
}