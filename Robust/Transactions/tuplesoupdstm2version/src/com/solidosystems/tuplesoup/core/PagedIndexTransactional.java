/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.solidosystems.tuplesoup.core;


import dstm2.AtomicArray;
import dstm2.atomic;
import dstm2.Thread;
import dstm2.factory.Factory;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.Callable;

/**
 *
 * @author navid
 */
public class PagedIndexTransactional implements TableIndexTransactional{

    static Factory<PageIndexTSInf> factory = Thread.makeFactory(PageIndexTSInf.class);
    
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
    
    
    private RandomAccessFile out=null;
    
    //protected static final int INITIALPAGEHASH=1024;
    //protected static final int PAGESIZE=2048;
    
    protected static final int INITIALPAGEHASH=32;
    protected static final int PAGESIZE=64;
    
        public PagedIndexTransactional(String filename) throws IOException{
        atomicfields = factory.create();
        
        atomicfields.setStat_create_page(Long.valueOf(0));
        atomicfields.setStat_page_branch(Long.valueOf(0));
        atomicfields.setStat_page_next(Long.valueOf(0));
        atomicfields.setStat_read(Long.valueOf(0));
        atomicfields.setStat_write(Long.valueOf(0));
        
        this.atomicfields.setFilename(filename);
        File ftest=new File(filename);
        if(!ftest.exists())ftest.createNewFile();
        out=new RandomAccessFile(filename,"rw");
        atomicfields.setRoots(new AtomicArray<TableIndexPageTransactional>(TableIndexPageTransactional.class, INITIALPAGEHASH));
       // System.out.println(filename);
       // System.out.println(out.length());
        if(out.length()>0){
            for(int i=0;i<INITIALPAGEHASH;i++){
                atomicfields.getRoots().set(i, new TableIndexPageTransactional(this,out));
                atomicfields.getRoots().get(i).setFirst();
               // System.out.println("In loop " + atomicfields.getRoots().get(i).getEndLocation());
                out.seek(atomicfields.getRoots().get(i).getEndLocation());
            }
        }else{
            for(int i=0;i<INITIALPAGEHASH;i++){
                atomicfields.getRoots().set(i, TableIndexPageTransactional.createNewPage(this,out,PAGESIZE));
                //     System.out.println("In Othe loop " + atomicfields.getRoots().get(i).getEndLocation());
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
    
    private /*synchronized*/ TableIndexPageTransactional getFirstFreePage(String id) throws IOException{
        return atomicfields.getRoots().get(rootHash(id)).getFirstFreePage(id, id.hashCode());
    }
    
    private /*synchronized*/ long getOffset(String id) throws IOException{
        if(atomicfields.getRoots()==null)return -1;
        return atomicfields.getRoots().get(rootHash(id)).getOffset(id,id.hashCode());
    }
    
    public /*synchronized*/ void updateEntry(String id,int rowsize,int location,long position) throws IOException{
        long offset=getOffset(id);
        out.seek(offset);
        TableIndexEntryTransactional entry=new TableIndexEntryTransactional(id,rowsize,location,position);
        entry.updateData(out);
        atomicfields.setStat_write(atomicfields.getStat_write()+1);
    }
    
    public synchronized void updateEntryTransactional(String id,int rowsize,int location,long position) throws IOException{
        final String id2 = id;
        final int rowsize2 = rowsize;
        final int location2 = location;
        final long position2 = position;
        
        Thread.doIt(new Callable<Boolean>() {
           public Boolean call() throws Exception{
                long offset=getOffset(id2);
                out.seek(offset);
                TableIndexEntryTransactional entry=new TableIndexEntryTransactional(id2,rowsize2,location2,position2);
                entry.updateData(out);
                atomicfields.setStat_write(atomicfields.getStat_write()+1);
                return true;
           }
        });
    }
    
    public /*synchronized*/ void addEntry(String id,int rowsize,int location,long position) throws IOException{
        TableIndexPageTransactional page=getFirstFreePage(id);
        page.addEntry(id,rowsize,location,position);
        atomicfields.setStat_write(atomicfields.getStat_write()+1);
    }
    public /*synchronized*/ TableIndexEntryTransactional scanIndex(String id) throws IOException{
        if(atomicfields.getRoots()==null)return null;
        return atomicfields.getRoots().get(rootHash(id)).scanIndex(id,id.hashCode());
    }
    
    public synchronized TableIndexEntryTransactional scanIndexTransactional(String id) throws IOException{
        final String id2 = id;
        return Thread.doIt(new Callable<TableIndexEntryTransactional>() {
           public TableIndexEntryTransactional call() throws Exception{
                if(atomicfields.getRoots()==null)return null;
                    return atomicfields.getRoots().get(rootHash(id2)).scanIndex(id2,id2.hashCode());
            }
        });
    }
    
    public synchronized List<TableIndexEntryTransactional> scanIndex(List<String> rows) throws IOException{
        final List<String> rows2 = rows;
        return Thread.doIt(new Callable<List<TableIndexEntryTransactional>>() {
           public List<TableIndexEntryTransactional> call() throws Exception{
                List<TableIndexEntryTransactional> lst=new ArrayList<TableIndexEntryTransactional>();
                for(int i=0;i<rows2.size();i++){
                    String id=rows2.get(i);
                    TableIndexEntryTransactional entry=scanIndex(id);
                    if(entry!=null){
                        if(entry.getLocation()!=TableTransactional.DELETE)lst.add(entry);
                    }
                }
                return lst;
           }
        });
    }
    public synchronized List<TableIndexEntryTransactional> scanIndex() throws IOException{
        return Thread.doIt(new Callable<List<TableIndexEntryTransactional>>() {
           public List<TableIndexEntryTransactional> call() throws Exception{
                ArrayList<TableIndexEntryTransactional> lst=new ArrayList<TableIndexEntryTransactional>();
                System.out.println(Thread.currentThread() + " start");
                for(int i=0;i<INITIALPAGEHASH;i++){
                    atomicfields.getRoots().get(i).addEntriesToList(lst);
                }
                System.out.println(Thread.currentThread() +" done");
                return lst;
           }
        });
    }
    public void close(){
        try{
            if(out!=null){
                out.close();
            }
        }catch(Exception e){}
    }
}
