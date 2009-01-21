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


public class PerformanceTest extends BasicTest{
    public PerformanceTest(){
        try{
            outbr("Running DualFileTable Performance test");
            outbr(1,"10000 small records");
            
            outbr(2,"Memory index");
            Table table=new DualFileTable("Performance-test","./",Table.MEMORY);
            benchmarkSmall(table,10000);
            table.close();
            table.deleteFiles();
            
            outbr(2,"Flat index");
            table=new DualFileTable("Performance-test","./",Table.FLAT);
            benchmarkSmall(table,10000);
            table.close();
            table.deleteFiles();
            
            outbr(2,"Paged index");
            table=new DualFileTable("Performance-test","./",Table.PAGED);
            benchmarkSmall(table,10000);
            table.close();
            table.deleteFiles();
            
            outbr(1,"20000 large records");
            
            outbr(2,"Memory index");
            table=new DualFileTable("Performance-test","./",Table.MEMORY);
            benchmarkLarge(table,20000);
            table.close();
            table.deleteFiles();
            
            outbr(2,"Flat index");
            table=new DualFileTable("Performance-test","./",Table.FLAT);
            benchmarkLarge(table,20000);
            table.close();
            table.deleteFiles();
            
            outbr(2,"Paged index");
            table=new DualFileTable("Performance-test","./",Table.PAGED);
            benchmarkLarge(table,20000);
            table.close();
            table.deleteFiles();
            
            outbr(1,"30000 large records");
            
            outbr(2,"Memory index");
            table=new DualFileTable("Performance-test","./",Table.MEMORY);
            benchmarkLarge(table,30000);
            table.close();
            table.deleteFiles();
            
            outbr(2,"Flat index");
            table=new DualFileTable("Performance-test","./",Table.FLAT);
            benchmarkLarge(table,30000);
            table.close();
            table.deleteFiles();
            
            outbr(2,"Paged index");
            table=new DualFileTable("Performance-test","./",Table.PAGED);
            benchmarkLarge(table,30000);
            table.close();
            table.deleteFiles();
            
            outbr("Running HashedTable Performance test");
            outbr(1,"10000 small records");
            
            outbr(2,"Memory index");
            table=new HashedTable("Performance-test","./",Table.MEMORY);
            benchmarkSmall(table,10000);
            table.close();
            table.deleteFiles();
            
            outbr(2,"Flat index");
            table=new HashedTable("Performance-test","./",Table.FLAT);
            benchmarkSmall(table,10000);
            table.close();
            table.deleteFiles();
            
            outbr(2,"Paged index");
            table=new HashedTable("Performance-test","./",Table.PAGED);
            benchmarkSmall(table,10000);
            table.close();
            table.deleteFiles();
            
            outbr(1,"20000 large records");
            
            outbr(2,"Memory index");
            table=new HashedTable("Performance-test","./",Table.MEMORY);
            benchmarkLarge(table,20000);
            table.close();
            table.deleteFiles();
            
            outbr(2,"Flat index");
            table=new HashedTable("Performance-test","./",Table.FLAT);
            benchmarkLarge(table,20000);
            table.close();
            table.deleteFiles();
            
            outbr(2,"Paged index");
            table=new HashedTable("Performance-test","./",Table.PAGED);
            benchmarkLarge(table,20000);
            table.close();
            table.deleteFiles();
            
            outbr(1,"30000 large records");
            
            outbr(2,"Memory index");
            table=new HashedTable("Performance-test","./",Table.MEMORY);
            benchmarkLarge(table,30000);
            table.close();
            table.deleteFiles();
            
            outbr(2,"Flat index");
            table=new HashedTable("Performance-test","./",Table.FLAT);
            benchmarkLarge(table,30000);
            table.close();
            table.deleteFiles();
            
            outbr(2,"Paged index");
            table=new HashedTable("Performance-test","./",Table.PAGED);
            benchmarkLarge(table,30000);
            table.close();
            table.deleteFiles();
            
            
        }catch(Exception e){
            e.printStackTrace();
        }
    }
    public static void main(String[] args){
        new PerformanceTest();
    }
    
    public void benchmarkSmall(Table table,int records) throws IOException{
        long pre=System.currentTimeMillis();
        for(int i=0;i<records;i++){
            Row row=new Row(""+i);
            row.put("key1","foo");
            table.addRow(row);
        }
        long post=System.currentTimeMillis();
        outbr(3,"Write "+(post-pre)+" ms");
        pre=System.currentTimeMillis();
        TupleStream stream=table.getRows();
        while(stream.hasNext()){
            stream.next();
        }
        post=System.currentTimeMillis();
        outbr(3,"Read "+(post-pre)+" ms");
        pre=System.currentTimeMillis();
        for(int i=0;i<records;i++){
            Row row=table.getRow(""+(int)(Math.random()*records));
        }
        post=System.currentTimeMillis();
        outbr(3,"Random read "+(post-pre)+" ms");
        
        // printStats(table.readStatistics());
    }
    
    public void benchmarkLarge(Table table,int records) throws IOException{
        long pre=System.currentTimeMillis();
        for(int i=0;i<records;i++){
            Row row=new Row(""+i);
            row.put("key1","foobarbaz");
            row.put("key2",123456);
            row.put("key3",3.141592);
            row.put("key4",true);
            row.put("key5",new Value(new byte[]{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16}));
            table.addRow(row);
        }
        long post=System.currentTimeMillis();
        outbr(3,"Write "+(post-pre)+" ms");
        pre=System.currentTimeMillis();
        TupleStream stream=table.getRows();
        while(stream.hasNext()){
            stream.next();
        }
        post=System.currentTimeMillis();
        outbr(3,"Read "+(post-pre)+" ms");
        pre=System.currentTimeMillis();
        for(int i=0;i<records;i++){
            Row row=table.getRow(""+(int)(Math.random()*records));
        }
        post=System.currentTimeMillis();
        outbr(3,"Random read "+(post-pre)+" ms");
        
        // printStats(table.readStatistics());
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