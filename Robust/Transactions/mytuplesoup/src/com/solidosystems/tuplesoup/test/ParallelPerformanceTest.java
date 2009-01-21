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
 
 package com.solidosystems.tuplesoup.test;
 
 import com.solidosystems.tuplesoup.core.*;
 import com.solidosystems.tuplesoup.filter.*;
  
 import java.util.*;
 import java.io.*;


public class ParallelPerformanceTest extends BasicTest implements Runnable{
    
    long writetime;
    long readtime;
    long randomtime;
    
    public ParallelPerformanceTest(){
        String path="./Volumes/My Book/test/";
        try{
            int records=50000;
            for(int i=1;i<11;i++){
                outbr("Running Parallel DualFileTable Performance test");
                outbr(1,i+" x "+(records/i)+" Large records");
            
               // outbr(2,"Memory index");
             /*   Table table=new DualFileTable("Performance-test",path,Table.MEMORY);
                benchmark(table,i,(records/i));
                table.close();
                table.deleteFiles();*/
            
              /*  outbr(2,"Flat index");
                table=new DualFileTable("Performance-test",path,Table.FLAT);
                benchmark(table,i,(records/i));
                table.close();
                table.deleteFiles();*/
            
                outbr(2,"Paged index");
                Table table=new DualFileTable("Performance-test",path,Table.PAGED);
                benchmark(table,i,(records/i));
                table.close();
                table.deleteFiles();
            
                outbr("Running Parallel HashedTable Performance test");
                outbr(1,i+" x "+(records/i)+" Large records");
            
            /*    outbr(2,"Memory index");
                table=new HashedTable("Performance-test",path,Table.MEMORY);
                benchmark(table,i,(records/i));
                table.close();
                table.deleteFiles();
            
                outbr(2,"Flat index");
                table=new HashedTable("Performance-test",path,Table.FLAT);
                benchmark(table,i,(records/i));
                table.close();
                table.deleteFiles();*/
            
                outbr(2,"Paged index");
                table=new HashedTable("Performance-test",path,Table.PAGED);
                benchmark(table,i,(records/i));
                table.close();
                table.deleteFiles();
            }
            
            
        }catch(Exception e){
            e.printStackTrace();
        }
    }
    public static void main(String[] args){
        new ParallelPerformanceTest();
    }
    
    public void benchmark(Table table,int threadcount, int records) throws Exception{
        writetime=0;
        readtime=0;
        randomtime=0;
        List<Thread> lst=new ArrayList<Thread>();
        for(int i=0;i<threadcount;i++){
            Thread thr=new Thread(new ParallelThread(this,table,i+"",records));
            thr.start();
            lst.add(thr);
        }
        for(int i=0;i<threadcount;i++){
            lst.get(i).join();
        }
        outbr(3,"Write "+writetime+" ms");
        outbr(3,"Read "+readtime+" ms");
        outbr(3,"Random "+randomtime+" ms");
    }
    
    public void run(){
        
    }
    
    public long benchmarkLargeWrite(Table table,int records, String id) throws IOException{
        long pre=System.currentTimeMillis();
        for(int i=0;i<records;i++){
            Row row=new Row(id+i);
            row.put("key1","foobarbaz");
            row.put("key2",123456);
            row.put("key3",3.141592);
            row.put("key4",true);
            row.put("key5",new Value(new byte[]{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16}));
            table.addRow(row);
        }
        long post=System.currentTimeMillis();
        return post-pre;
    }
    public long benchmarkLargeRead(Table table,int records, String id) throws IOException{
        long pre=System.currentTimeMillis();
        TupleStream stream=table.getRows();
        while(stream.hasNext()){
            stream.next();
        }
        long post=System.currentTimeMillis();
        return post-pre;
    }
    public long benchmarkLargeRandomRead(Table table,int records, String id) throws IOException{
        long pre=System.currentTimeMillis();
        for(int i=0;i<records;i++){
            Row row=table.getRow(id+(int)(Math.random()*records));
        }
        long post=System.currentTimeMillis();
        return post-pre;
    }

    public void printStats(Hashtable<String,Long> hash){
        Set<String> keys=hash.keySet();
        Iterator<String> it=keys.iterator();
        while(it.hasNext()){
            String key=it.next();
            outbr(4,key+" "+hash.get(key));
        }
    }
}