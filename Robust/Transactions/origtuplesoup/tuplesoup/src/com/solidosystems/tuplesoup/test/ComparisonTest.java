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
import java.sql.*;


public class ComparisonTest{
    public int DATASET=50000;
    public List<List<Long>>results;
    public List<String>testnames;
    public List<String>idlist;
    
    
    public String generateId(int i){
        return "user.number."+i+"@testdomain.lan";
    }
    
    public void testTupleSoup() throws Exception{
        System.out.println(" + Running TupleSoup test");
        Table tbl=new DualFileTable("CoreTestData",".");
        tbl.deleteFiles();
        tbl=new DualFileTable("CoreTestData",".",Table.PAGED);
        //tbl.setIndexCacheSize(DATASET/10);
        tbl.setIndexCacheSize(50000);
        long pre=System.currentTimeMillis();

        for(int i=0;i<DATASET;i++){
            Row row=new Row(idlist.get(i));
            row.put("name","Kasper J. Jeppesen");
            row.put("age",(int)(Math.random()*60f)+20);
            row.put("sex","male");
            row.put("www","http://syntacticsirup.blogspot.com/");
            row.put("tcreated",new java.util.Date());
            tbl.addRow(row);
        }
        long post=System.currentTimeMillis();
        long addtime=post-pre;
        System.out.println("   - Add "+addtime+"ms");
        
        pre=System.currentTimeMillis();
        for(int i=0;i<DATASET/10;i++){
            Row row=tbl.getRow(idlist.get((int)(Math.random()*DATASET)));
        }
        post=System.currentTimeMillis();
        long fetchtime=post-pre;
        System.out.println("   - Fetch "+fetchtime+"ms");
        
        pre=System.currentTimeMillis();
        for(int i=0;i<DATASET/10;i++){
            Row row=tbl.getRow(idlist.get((int)(Math.random()*DATASET)));
            row.put("age",row.getInt("age")+1);
            tbl.updateRow(row);
        }
        post=System.currentTimeMillis();
        long updatetime=post-pre;
        System.out.println("   - Update "+updatetime+"ms");
        
        // pre=System.currentTimeMillis();
        // MergeSort msort=new MergeSort(8192000);
        // msort.initialize("./tmpsort",tbl.getRows(),"age",SortRule.ASC);
        // while(msort.hasNext()){
        //    Row row=msort.next();
        // }
        // post=System.currentTimeMillis();
        // long sorttime=post-pre;
        // System.out.println("   - Sort "+sorttime+"ms");
        
        // addResults(addtime,fetchtime,updatetime,sorttime,0);
        testnames.add("TupleSoup");
    }
    
    public void testGreedyTupleSoup() throws Exception{
        System.out.println(" + Running Greedy TupleSoup test");
        Table tbl=new DualFileTable("CoreTestData",".");
        tbl.deleteFiles();
        tbl=new DualFileTable("CoreTestData",".",Table.MEMORY);
        tbl.setIndexCacheSize(0);
        long pre=System.currentTimeMillis();

        for(int i=0;i<DATASET;i++){
            Row row=new Row(idlist.get(i));
            row.put("name","Kasper J. Jeppesen");
            row.put("age",(int)(Math.random()*60f)+20);
            row.put("sex","male");
            row.put("www","http://syntacticsirup.blogspot.com/");
            row.put("tcreated",new java.util.Date());
            tbl.addRow(row);
        }
        long post=System.currentTimeMillis();
        long addtime=post-pre;
        System.out.println("   - Add "+addtime+"ms");
        
        pre=System.currentTimeMillis();
        for(int i=0;i<DATASET/10;i++){
            Row row=tbl.getRow(idlist.get((int)(Math.random()*DATASET)));
        }
        post=System.currentTimeMillis();
        long fetchtime=post-pre;
        System.out.println("   - Fetch "+fetchtime+"ms");
        
        pre=System.currentTimeMillis();
        for(int i=0;i<DATASET/10;i++){
            Row row=tbl.getRow(idlist.get((int)(Math.random()*DATASET)));
            row.put("age",row.getInt("age")+1);
            tbl.updateRow(row);
        }
        post=System.currentTimeMillis();
        long updatetime=post-pre;
        System.out.println("   - Update "+updatetime+"ms");
        
        pre=System.currentTimeMillis();
        JavaSort msort=new JavaSort();
        msort.initialize("./tmpsort",tbl.getRows(),"age",SortRule.ASC);
        while(msort.hasNext()){
            Row row=msort.next();
        }
        post=System.currentTimeMillis();
        long sorttime=post-pre;
        System.out.println("   - Sort "+sorttime+"ms");
        
        addResults(addtime,fetchtime,updatetime,sorttime,0);
        testnames.add("TupleSoup");
    }
    
    public void testPostgreSQL() throws Exception{
        System.out.println(" + Running PostgreSQL test");
        Class.forName("org.postgresql.Driver");
        Connection con=DriverManager.getConnection("jdbc:postgresql:tupledb","tuple", "1234");
        Statement st=con.createStatement();
        try{
            st.executeUpdate("DROP TABLE tbl_user");
        }catch(Exception e){}
        st.executeUpdate("CREATE TABLE tbl_user (id VARCHAR, name VARCHAR, age INT, sex VARCHAR, www VARCHAR, tcreated TIMESTAMP)");
        st.executeUpdate("CREATE INDEX ndx_user ON tbl_user(id)");
        st.executeUpdate("VACUUM FULL");
        // con.setAutoCommit(false);
        PreparedStatement pst=con.prepareStatement("INSERT INTO tbl_user (id,name,age,sex,www,tcreated) VALUES(?,?,?,?,?,?)");
        long pre=System.currentTimeMillis();
        for(int i=0;i<DATASET;i++){
            // st.executeUpdate("INSERT INTO tbl_user (id,name,age,sex,www,tcreated) VALUES('"+generateId(i)+"','Kasper J. Jeppesen',"+((int)(Math.random()*60f)+20)+",'male','http://syntacticsirup.blogspot.com/',now())");
            pst.setString(1,idlist.get(i));
            pst.setString(2,"Kasper J. Jeppesen");
            pst.setInt(3,((int)(Math.random()*60f)+20));
            pst.setString(4,"male");
            pst.setString(5,"http://syntacticsirup.blogspot.com/");
            pst.setDate(6,new java.sql.Date(System.currentTimeMillis()));
            pst.executeUpdate();
        }
        // con.commit();
        long post=System.currentTimeMillis();
        st.executeUpdate("ANALYZE");
        long addtime=post-pre;
        System.out.println("   - Add "+addtime+"ms");
        
        pst=con.prepareStatement("SELECT name,age,sex,www,tcreated FROM tbl_user WHERE id=?");
        pre=System.currentTimeMillis();
        for(int i=0;i<DATASET/10;i++){
            pst.setString(1,idlist.get((int)(Math.random()*DATASET)));
            ResultSet rs=pst.executeQuery();
            rs.next();
        }
        post=System.currentTimeMillis();
        long fetchtime=post-pre;
        System.out.println("   - Fetch "+fetchtime+"ms");
        
        pst=con.prepareStatement("SELECT age FROM tbl_user WHERE id=?");
        PreparedStatement pst2=con.prepareStatement("UPDATE tbl_user SET age=? WHERE id=?");
        pre=System.currentTimeMillis();
        for(int i=0;i<DATASET/10;i++){
            pst.setString(1,idlist.get((int)(Math.random()*DATASET)));
            ResultSet rs=pst.executeQuery();
            rs.next();
            pst2.setInt(1,rs.getInt(1)+1);
            pst2.setString(2,idlist.get(i));
            pst2.executeUpdate();
        }
        post=System.currentTimeMillis();
        long updatetime=post-pre;
        System.out.println("   - Update "+updatetime+"ms");
        
        pre=System.currentTimeMillis();
        ResultSet rs=st.executeQuery("SELECT id,name,age,sex,www,tcreated FROM tbl_user ORDER BY age");
        while(rs.next()){
            rs.getString(1);
        }
        post=System.currentTimeMillis();
        long sorttime=post-pre;
        System.out.println("   - Sort "+sorttime+"ms");
        
        
        addResults(addtime,fetchtime,0,sorttime,0);
        testnames.add("TupleSoup");
    }
    
    public void testMySQL() throws Exception{
        System.out.println(" + Running MySQL test");
        Class.forName("com.mysql.jdbc.Driver");
        Connection con=DriverManager.getConnection("jdbc:mysql://localhost:3306/tupledb","tuple", "1234");
        Statement st=con.createStatement();
        try{
            st.executeUpdate("DROP TABLE tbl_user");
        }catch(Exception e){}
        st.executeUpdate("CREATE TABLE tbl_user (id VARCHAR(128), name VARCHAR(128), age INT, sex VARCHAR(16), www VARCHAR(128), tcreated TIMESTAMP)");
        st.executeUpdate("CREATE INDEX ndx_user ON tbl_user(id)");
        // st.executeUpdate("VACUUM FULL");
        // con.setAutoCommit(false);
        PreparedStatement pst=con.prepareStatement("INSERT INTO tbl_user (id,name,age,sex,www,tcreated) VALUES(?,?,?,?,?,?)");
        long pre=System.currentTimeMillis();
        for(int i=0;i<DATASET;i++){
            // st.executeUpdate("INSERT INTO tbl_user (id,name,age,sex,www,tcreated) VALUES('"+generateId(i)+"','Kasper J. Jeppesen',"+((int)(Math.random()*60f)+20)+",'male','http://syntacticsirup.blogspot.com/',now())");
            pst.setString(1,idlist.get(i));
            pst.setString(2,"Kasper J. Jeppesen");
            pst.setInt(3,((int)(Math.random()*60f)+20));
            pst.setString(4,"male");
            pst.setString(5,"http://syntacticsirup.blogspot.com/");
            pst.setDate(6,new java.sql.Date(System.currentTimeMillis()));
            pst.executeUpdate();
        }
        // con.commit();
        long post=System.currentTimeMillis();
        // st.executeUpdate("ANALYZE");
        long addtime=post-pre;
        System.out.println("   - Add "+addtime+"ms");
        
        pst=con.prepareStatement("SELECT name,age,sex,www,tcreated FROM tbl_user WHERE id=?");
        pre=System.currentTimeMillis();
        for(int i=0;i<DATASET/10;i++){
            pst.setString(1,idlist.get((int)(Math.random()*DATASET)));
            ResultSet rs=pst.executeQuery();
            rs.next();
        }
        post=System.currentTimeMillis();
        long fetchtime=post-pre;
        System.out.println("   - Fetch "+fetchtime+"ms");
        
        pst=con.prepareStatement("SELECT age FROM tbl_user WHERE id=?");
        PreparedStatement pst2=con.prepareStatement("UPDATE tbl_user SET age=? WHERE id=?");
        pre=System.currentTimeMillis();
        for(int i=0;i<DATASET/10;i++){
            pst.setString(1,idlist.get((int)(Math.random()*DATASET)));
            ResultSet rs=pst.executeQuery();
            rs.next();
            pst2.setInt(1,rs.getInt(1)+1);
            pst2.setString(2,idlist.get(i));
            pst2.executeUpdate();
        }
        post=System.currentTimeMillis();
        long updatetime=post-pre;
        System.out.println("   - Update "+updatetime+"ms");
        
        pre=System.currentTimeMillis();
        ResultSet rs=st.executeQuery("SELECT id,name,age,sex,www,tcreated FROM tbl_user ORDER BY age");
        while(rs.next()){
            rs.getString(1);
        }
        post=System.currentTimeMillis();
        long sorttime=post-pre;
        System.out.println("   - Sort "+sorttime+"ms");
        
        
        addResults(addtime,fetchtime,0,sorttime,0);
        testnames.add("TupleSoup");
    }
    public void addResults(long add,long fetch,long update,long sort,long sum){
        List<Long> lst=new ArrayList<Long>();
        lst.add(add);
        lst.add(fetch);
        lst.add(update);
        lst.add(sort);
        lst.add(sum);
        results.add(lst);
    }
    
    public ComparisonTest(){
        try{
            for(int n=1;n<=20;n++){
                DATASET=n*10000;
                System.out.println("Running for "+DATASET);
                idlist=new ArrayList<String>();
                for(int i=0;i<DATASET;i++){
                    idlist.add(generateId(i));
                }
                Collections.shuffle(idlist);
                results=new ArrayList<List<Long>>();
                testnames=new ArrayList<String>();
                // testGreedyTupleSoup();
                testTupleSoup();
                // testMySQL();
                // testPostgreSQL();
            }
            // calcScores();
        }catch(Exception e){
            e.printStackTrace();
        }
    }
    
    public void calcScores(){
        System.out.println("Compared results");
    }
    
    public static void main(String[] args){
        new ComparisonTest();
    }
}