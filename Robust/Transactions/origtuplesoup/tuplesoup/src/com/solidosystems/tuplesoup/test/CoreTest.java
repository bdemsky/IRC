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
 
 public class CoreTest{
     public static void main(String[] args){
         try{
             System.out.println("TupleSoup Core Data Test");
             System.out.println(" + Preparing data");
             Date dt=new Date();
         
             Table tbl=new DualFileTable("CoreTestData",".");
             tbl.deleteFiles();
             tbl=new DualFileTable("CoreTestData",".");
             Row row1=new Row("1");
             row1.put("string","Hello World!");
             row1.put("int",42);
             row1.put("long",314l);
             row1.put("float",3.14f);
             row1.put("double",3.1415d);
             row1.put("timestamp",dt);
             row1.put("boolean",true);
             tbl.addRow(row1);
             Row row2=new Row("2");
             row2.put("name","Kasper J");
             row2.put("int",43);
             tbl.addRow(row2);
             Row row3=new Row("3");
             row3.put("name","Kasper L");
             row3.put("int",44);
             tbl.addRow(row3);
             Row row4=new Row("4");
             row4.put("name","Christer");
             row4.put("int",45);
             tbl.addRow(row4);
             
             System.out.print(" + Testing basic row storage ... ");
             TupleStream it=tbl.getRows();
             int cnt=0;
             while(it.hasNext()){
                 it.next();
                 cnt++;
             }
             if(cnt!=4)die("Wrong number of rows in table after adding 4 rows");
             Row tmp=tbl.getRow("1");
             if(tmp==null)die("Could not fetch the first row");
             if(!tmp.getString("string").equals("Hello World!"))die("String value incorrect after storage");
             if(tmp.getInt("int")!=42)die("Int value incorrect after storage");
             if(tmp.getLong("long")!=314l)die("Long value incorrect after storage");
             if(tmp.getFloat("float")!=3.14f)die("Float value incorrect after storage");
             if(tmp.getDouble("double")!=3.1415d)die("Double value incorrect after storage");
             if(!tmp.getTimestamp("timestamp").equals(dt))die("Timestamp value incorrect after storage");
             if(!tmp.getBoolean("boolean"))die("Boolean value incorrect after storage");
             System.out.println("OK");
             
             System.out.print(" + Testing bulk fetch order ... ");
             List<String> lst=new ArrayList<String>();
             lst.add("3");
             lst.add("1");
             lst.add("2");
             lst.add("4");
             lst.add("2");
             it=tbl.getRows(lst);
             tmp=it.next();
             if(!tmp.getId().equals("3"))die("Elements returned in wrong order");
             tmp=it.next();
             if(!tmp.getId().equals("1"))die("Elements returned in wrong order");
             tmp=it.next();
             if(!tmp.getId().equals("2"))die("Elements returned in wrong order");
             tmp=it.next();
             if(!tmp.getId().equals("4"))die("Elements returned in wrong order");
             System.out.println("OK");
             
             System.out.print(" + Testing RowMatcher ... ");
             RowMatcher m=new RowMatcher("name",RowMatcher.STARTSWITH,new Value("Kasper"));
             it=tbl.getRows(m);
             cnt=0;
             while(it.hasNext()){
                 it.next();
                 cnt++;
             }
             if(cnt!=2)die("String based RowMatcher failed using STARTSWITH");
             m=new RowMatcher("int",RowMatcher.GREATERTHAN,new Value(44));
             it=tbl.getRows(m);
             tmp=it.next();
             if(!tmp.getId().equals("4"))die("Int based RowMatcher failed using GREATERTHAN");
             // TODO: Add full test of all types and comparison types in RowMatcher
             System.out.println("OK");
             
             System.out.print(" + Testing row updates and deletes ... ");
             tbl.deleteRow(row2);
             row4.put("int",8192);
             tbl.updateRow(row4);
             it=tbl.getRows();
             cnt=0;
             while(it.hasNext()){
                 it.next();
                 cnt++;
             }
             if(cnt!=3)die("Wrong number of rows returned");
             tmp=tbl.getRow("4");
             if(tmp.getInt("int")!=8192)die("Wrong int value returned after update");
             
             row1.put("longtext","Hello world again and again and again and again.....");
             tbl.updateRow(row1);
             tmp=tbl.getRow("1");
             if(!tmp.getString("longtext").equals("Hello world again and again and again and again....."))die("Wrong string value returned after update");
             
             System.out.println("OK");
             
             System.out.print(" + Testing close and reopen ... ");
             tbl.close();
             tbl=new DualFileTable("CoreTestData",".");
             it=tbl.getRows();
             cnt=0;
             while(it.hasNext()){
                it.next();
                cnt++;
             }
             if(cnt!=3)die("Wrong number of rows returned");
             tmp=tbl.getRow("4");
             if(tmp.getInt("int")!=8192)die("Wrong int value returned after close");
             tmp=tbl.getRow("1");
             if(!tmp.getString("longtext").equals("Hello world again and again and again and again....."))die("Wrong string value returned after update");

             System.out.println("OK");
              
             System.out.print(" + Testing large data sets ... ");
             tbl.deleteFiles();
             tbl=new DualFileTable("CoreTestData",".");
             for(int i=0;i<50000;i++){
                 tmp=new Row(i+"_"+Math.random());
                 tmp.put("data1",Math.random());
                 tmp.put("one",1);
                 tbl.addRow(tmp);
             }
             tbl.close();
             tbl=new DualFileTable("CoreTestData",".");
             it=tbl.getRows();
             cnt=0;
             while(it.hasNext()){
                it.next();
                cnt++;
             }
             if(cnt!=50000)die("Wrong number of rows returned");
             it=tbl.getRows();
             cnt=0;
             while(it.hasNext()){
                tmp=it.next();
                cnt+=tmp.getInt("one");
             }
             if(cnt!=50000)die("Wrong numeric data returned");
             
             tmp=new Row("foo");
             tmp.put("bar","baz");
             tbl.addRow(tmp);
             tbl.close();
             tbl=new DualFileTable("CoreTestData",".");
             tmp=tbl.getRow("foo");
             if(!tmp.getString("bar").equals("baz"))die("Wrong string data returned");
             
             it=tbl.getRows();
              cnt=0;
              while(it.hasNext()){
                 it.next();
                 cnt++;
              }
              if(cnt!=50001)die("Wrong number of rows returned");
             System.out.println("OK");
             
             System.exit(0);
             tbl.close();
             tbl.deleteFiles();
        }catch(Exception e){
            e.printStackTrace();
        }   
     }
     public static void die(String reason){
         System.out.println("ERR");
         System.out.println(" ! "+reason);
         System.exit(0);
     }
 }