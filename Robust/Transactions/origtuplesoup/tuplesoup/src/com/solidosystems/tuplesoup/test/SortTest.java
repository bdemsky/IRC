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

public class SortTest{
    public static void main(String[] args){
        String[] first_names={"Adolf","Anders","Bo","Christer","Daniel","Erik","Frederik","Georg","Hans","Hans-Christian","Ingolf","Jens","Kasper","Lars","Mads","Niels","Ole","Per","Rasmus","Søren","Tue","Ulrik"};
        String[] last_names={"Jeppesen","Andersen","Hansen","Nielsen","Langer","Sørensen","Shore","Crane","Saustrup"};
        String[] domains={"email.lan","mymail.lan","lukewarmmail.lan","lessthanfree.lan","devnull.lan"};
        try{
            System.out.println("TupleSoup Performance Test");
            System.out.println(" + Simple user data");
            Table tbl=new DualFileTable("CoreTestData",".");
            tbl.deleteFiles();
            tbl=new DualFileTable("CoreTestData",".",Table.PAGED);
            tbl.setIndexCacheSize(10000);
            int rows=100000;
            System.out.print("   - Adding "+rows+" tuples in random order ... ");
            long pre=System.currentTimeMillis();
            long size=0;
            List<String>flst=new ArrayList<String>();
            for(int i=0;i<rows;i++){
                flst.add(""+i);
            }
            Collections.shuffle(flst);
            
            for(int i=0;i<rows;i++){
                Row tmp=new Row(flst.get(i));
                // Row tmp=new Row(""+i);
                tmp.put("name",first_names[(int)(Math.random()*first_names.length)]+" "+last_names[(int)(Math.random()*last_names.length)]);
                tmp.put("age",(float)(Math.random()*60f)+20f);
                // tmp.put("age",i);
                tmp.put("sex","male");
                tmp.put("zipcode",(int)(Math.random()*8200f)+1000);
                tmp.put("email",first_names[(int)(Math.random()*first_names.length)].toLowerCase()+"@"+domains[(int)(Math.random()*domains.length)]);
                tbl.addRow(tmp);
                size+=tmp.getSize();
            }
            long post=System.currentTimeMillis();
            System.out.println((post-pre)+"ms avg:"+((post-pre)/((float)rows))+"ms "+(size/1024)+"kb "+((size/1024)/((post-pre)/1000f))+"kb/s "+(size/rows)+"b/row");
            
            size=0;
            System.out.print("   - Adding "+rows+" tuples to RowBuffer ... ");
            RowBuffer buf=new RowBuffer("./foobarbaz");
            buf.setCacheSize(1024000);
            pre=System.currentTimeMillis();
            for(int i=0;i<rows;i++){
                Row tmp=new Row(flst.get(i));
                // Row tmp=new Row(""+i);
                tmp.put("name",first_names[(int)(Math.random()*first_names.length)]+" "+last_names[(int)(Math.random()*last_names.length)]);
                tmp.put("age",(float)(Math.random()*60f)+20f);
                // tmp.put("age",i);
                tmp.put("sex","male");
                tmp.put("zipcode",(int)(Math.random()*8200f)+1000);
                tmp.put("email",first_names[(int)(Math.random()*first_names.length)].toLowerCase()+"@"+domains[(int)(Math.random()*domains.length)]);
                buf.addRow(tmp);
                size+=tmp.getSize();
            }
            post=System.currentTimeMillis();
            System.out.println((post-pre)+"ms avg:"+((post-pre)/((float)rows))+"ms "+(size/1024)+"kb "+((size/1024)/((post-pre)/1000f))+"kb/s "+(size/rows)+"b/row");
            
            
            
            System.out.print("   - Fetching all rows from RowBuffer sequentially in bulk ... ");
            pre=System.currentTimeMillis();
            buf.prepare();
            while(buf.hasNext()){
                Row r2=buf.next();
            }
            post=System.currentTimeMillis();
            System.out.println((post-pre)+"ms "+((post-pre)/(float)rows)+"ms/row");
            
            
            System.out.print("   - Fetching all rows from table sequentially in bulk ... ");
            pre=System.currentTimeMillis();
            TupleStream it=tbl.getRows();
            while(it.hasNext()){
                Row r2=it.next();
            }
            post=System.currentTimeMillis();
            System.out.println((post-pre)+"ms "+((post-pre)/(float)rows)+"ms/row");
            long fetchtime=post-pre;
            
            System.out.print("   - Fetching all rows sorted in bulk (JavaSort) ... ");
            pre=System.currentTimeMillis();
            JavaSort sort=new JavaSort();
            sort.initialize("./tmpsort",tbl.getRows(),"age",SortRule.ASC);
            while(sort.hasNext()){
                Row r2=sort.next();
                // System.out.println(r2.getString("age"));
            }
            post=System.currentTimeMillis();
            System.out.println(((post-pre)-fetchtime)+"ms "+(((post-pre)-fetchtime)/(float)rows)+"ms/row");
           
            System.out.print("   - Fetching all rows sorted in bulk (MergeSort 4mb cache) ... ");
            pre=System.currentTimeMillis();
            MergeSort msort=new MergeSort(4096000);
            msort.initialize("./tmpsort",tbl.getRows(),"age",SortRule.ASC);
            while(msort.hasNext()){
                Row r2=msort.next();
            }
            post=System.currentTimeMillis();
            System.out.println(((post-pre)-fetchtime)+"ms "+(((post-pre)-fetchtime)/(float)rows)+"ms/row ");

            System.out.print("   - Fetching all rows sorted in bulk (MergeSort 2mb cache) ... ");
            pre=System.currentTimeMillis();
            msort=new MergeSort(2048000);
            msort.initialize("./tmpsort",tbl.getRows(),"age",SortRule.ASC);
            while(msort.hasNext()){
                Row r2=msort.next();
                // System.out.println(r2.getString("age"));
            }
            post=System.currentTimeMillis();
            System.out.println(((post-pre)-fetchtime)+"ms "+(((post-pre)-fetchtime)/(float)rows)+"ms/row ");

            System.out.print("   - Fetching all rows sorted in bulk (MergeSort 1mb cache) ... ");
            pre=System.currentTimeMillis();
            msort=new MergeSort(1024000);
            msort.initialize("./tmpsort",tbl.getRows(),"age",SortRule.ASC);
            while(msort.hasNext()){
                Row r2=msort.next();
                // System.out.println(r2.getString("age"));
            }
            post=System.currentTimeMillis();
            System.out.println(((post-pre)-fetchtime)+"ms "+(((post-pre)-fetchtime)/(float)rows)+"ms/row ");
            
            
            System.out.print("   - Fetching all rows sorted in bulk (MergeSort 512kb cache) ... ");
            pre=System.currentTimeMillis();
            msort=new MergeSort(512000);
            msort.initialize("./tmpsort",tbl.getRows(),"age",SortRule.ASC);
            while(msort.hasNext()){
                Row r2=msort.next();
                // System.out.println(r2.getString("age"));
            }
            post=System.currentTimeMillis();
            System.out.println(((post-pre)-fetchtime)+"ms "+(((post-pre)-fetchtime)/(float)rows)+"ms/row");
            
            
            tbl.deleteFiles();
        }catch(Exception e){
            e.printStackTrace();
        }
    }
}