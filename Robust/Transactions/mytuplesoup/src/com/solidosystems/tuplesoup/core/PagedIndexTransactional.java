/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.solidosystems.tuplesoup.core;

import TransactionalIO.core.TransactionalFile;
import dstm2.AtomicArray;
import dstm2.atomic;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

/**
 *
 * @author navid
 */
public class PagedIndexTransactional implements TableIndexTransactional{

    PageIndexTSInf atomicfields;
   

    
    public @atomic interface PageIndexTSInf{
       String getFilename();
       Long getStat_read();
       Long getStat_write();
       Long getStat_create_page();
       Long getStat_page_next();
       Long getStat_page_branch();
       AtomicArray<TableIndexPageTransactional> getRoots();
       
       void setFilename(String val);
       void setRoots(AtomicArray<TableIndexPageTransactional> roots);
       void setStat_read(Long val);
       void setStat_write(Long val);
       void setStat_create_page(Long val);
       void setStat_page_next(Long val);
       void setStat_page_branch(Long val);
    }
    
    
    private TransactionalFile out=null;
    protected static final int INITIALPAGEHASH=1024;
    protected static final int PAGESIZE=2048;
    
        public PagedIndexTransactional(String filename) throws IOException{
        this.atomicfields.setFilename(filename);
        File ftest=new File(filename);
        if(!ftest.exists())ftest.createNewFile();
        out=new TransactionalFile(filename,"rw");
        atomicfields.setRoots(new AtomicArray<TableIndexPageTransactional>(TableIndexPageTransactional.class, INITIALPAGEHASH));
    
        if(out.length()>0){
            for(int i=0;i<INITIALPAGEHASH;i++){
                atomicfields.getRoots().set(i, new TableIndexPageTransactional(this,out));
                atomicfields.getRoots().get(i).setFirst();
                out.seek(atomicfields.getRoots().get(i).getEndLocation());
            }
        }else{
            for(int i=0;i<INITIALPAGEHASH;i++){
                atomicfields.getRoots().set(i, TableIndexPageTransactional.createNewPage(this,out,PAGESIZE));
                atomicfields.getRoots().get(i).setFirst();
            }
        }
    }
    
    public Hashtable<String,Long> readStatistics(){
        Hashtable<String,Long> hash=new Hashtable<String,Long>();
        hash.put("stat_index_read",atomicfields.getStat_read());
        hash.put("stat_index_write",atomicfields.getStat_write());
        hash.put("stat_index_create_page",atomicfields.getStat_create_page());
        hash.put("stat_index_page_next",atomicfields.getStat_page_next());
        hash.put("stat_index_page_branch",atomicfields.getStat_page_branch());
        atomicfields.setStat_read((long)0);
        atomicfields.setStat_write((long)0);
        atomicfields.setStat_create_page((long)0);
        atomicfields.setStat_page_next((long)0);
        atomicfields.setStat_page_branch((long)0);
        return hash;
    }
    
    private int rootHash(String id){
        return id.hashCode() & (INITIALPAGEHASH-1);
    }
    
    private synchronized TableIndexPageTransactional getFirstFreePage(String id) throws IOException{
        return atomicfields.getRoots().get(rootHash(id)).getFirstFreePage(id, id.hashCode());
    }
    
    private synchronized long getOffset(String id) throws IOException{
        if(atomicfields.getRoots()==null)return -1;
        return atomicfields.getRoots().get(rootHash(id)).getOffset(id,id.hashCode());
    }
    
    public synchronized void updateEntry(String id,int rowsize,int location,long position) throws IOException{
        long offset=getOffset(id);
        out.seek(offset);
        TableIndexEntryTransactional entry=new TableIndexEntryTransactional(id,rowsize,location,position);
        entry.updateData(out);
        atomicfields.setStat_write(atomicfields.getStat_write()+1);
    }
    public synchronized void addEntry(String id,int rowsize,int location,long position) throws IOException{
        TableIndexPageTransactional page=getFirstFreePage(id);
        page.addEntry(id,rowsize,location,position);
        atomicfields.setStat_write(atomicfields.getStat_write()+1);
    }
    public synchronized TableIndexEntryTransactional scanIndex(String id) throws IOException{
        if(atomicfields.getRoots()==null)return null;
        return atomicfields.getRoots().get(rootHash(id)).scanIndex(id,id.hashCode());
    }
    public synchronized List<TableIndexEntryTransactional> scanIndex(List<String> rows) throws IOException{
        List<TableIndexEntryTransactional> lst=new ArrayList<TableIndexEntryTransactional>();
        for(int i=0;i<rows.size();i++){
            String id=rows.get(i);
            TableIndexEntryTransactional entry=scanIndex(id);
            if(entry!=null){
                if(entry.getLocation()!=Table.DELETE)lst.add(entry);
            }
        }
        return lst;
    }
    public synchronized List<TableIndexEntryTransactional> scanIndex() throws IOException{
        ArrayList<TableIndexEntryTransactional> lst=new ArrayList<TableIndexEntryTransactional>();
        for(int i=0;i<INITIALPAGEHASH;i++){
            atomicfields.getRoots().get(i).addEntriesToList(lst);
        }
        return lst;
    }
    public void close(){
        try{
            if(out!=null){
                out.close();
            }
        }catch(Exception e){}
    }
}
