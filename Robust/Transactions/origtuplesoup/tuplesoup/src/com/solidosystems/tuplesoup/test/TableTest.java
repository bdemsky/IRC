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
import java.util.*;
import java.io.*;

public class TableTest extends BasicTest{
    public TableTest(){
        
    }
    
    public void runTest(){
        outbr("Running core DualFileTable functionality test");
        try{
            outbr(1,"Creating new table");
            
            out(2,"Default index");
            Table table=new DualFileTable("TableTest-test","./");
            if(!table.getTitle().equals("TableTest-test")){
                err("Wrong table title returned");
            }else if(!table.getLocation().equals("./")){
                err("Wrong table location returned");
            }else{
                table.close();
                table.deleteFiles();
                ok();
            }
            out(2,"Memory index");
            table=new DualFileTable("TableTest-test","./",Table.MEMORY);
            table.close();
            table.deleteFiles();
            ok();
            
            out(2,"Flat index");
            table=new DualFileTable("TableTest-test","./",Table.FLAT);
            table.close();
            table.deleteFiles();
            ok();
            
            out(2,"Paged index");
            table=new DualFileTable("TableTest-test","./",Table.PAGED);
            table.close();
            table.deleteFiles();
            ok();
            
            outbr(1,"Testing add, update, delete and read of data");
            
            
            
            outbr(2,"Memory index");
            table=new DualFileTable("TableTest-test","./",Table.MEMORY);
            testIndex(table);
            table.close();
            table.deleteFiles();
            
            outbr(2,"Flat index");
            table=new DualFileTable("TableTest-test","./",Table.FLAT);
            testIndex(table);
            table.close();
            table.deleteFiles();
            
            outbr(2,"Paged index");
            table=new DualFileTable("TableTest-test","./",Table.PAGED);
            testIndex(table);
            table.close();
            table.deleteFiles();
            
            outbr(1,"Close and reopen tables after bulk operations");
            out(2,"Memory index");
            table=new DualFileTable("TableTest-test","./",Table.MEMORY);
            bulkOperate(table);
            table.close();
            table=new DualFileTable("TableTest-test","./",Table.MEMORY);
            boolean bad=false;
            TupleStream stream=table.getRows();
            int count=0;
            while(stream.hasNext()){
                Row row=stream.next();
                if(row.getDouble("key3")!=3.1415)bad=true;
                count++;
            }
            if(bad){
                err("Bad values returned after reopening table");
            }else if(count!=5000){
                err("Wrong number of rows returned after reopening table");
            }else ok();
            table.close();
            table.deleteFiles();
            
            out(2,"Flat index");
            table=new DualFileTable("TableTest-test","./",Table.FLAT);
            bulkOperate(table);
            table.close();
            table=new DualFileTable("TableTest-test","./",Table.FLAT);
             bad=false;
             stream=table.getRows();
             count=0;
            while(stream.hasNext()){
                Row row=stream.next();
                if(row.getDouble("key3")!=3.1415)bad=true;
                count++;
            }
            if(bad){
                err("Bad values returned after reopening table");
            }else if(count!=5000){
                err("Wrong number of rows returned after reopening table");
            }else ok();
            table.close();
            table.deleteFiles();
            
            out(2,"Paged index");
            table=new DualFileTable("TableTest-test","./",Table.PAGED);
            bulkOperate(table);
            table.close();
            table=new DualFileTable("TableTest-test","./",Table.PAGED);
             bad=false;
             stream=table.getRows();
             count=0;
            while(stream.hasNext()){
                Row row=stream.next();
                if(row.getDouble("key3")!=3.1415)bad=true;
                count++;
            }
            if(bad){
                err("Bad values returned after reopening table");
            }else if(count!=5000){
                err("Wrong number of rows returned after reopening table");
            }else ok();
            table.close();
            table.deleteFiles();
            
        }catch(Exception e){
            err("Exception occured while testing "+e);
        }
        outbr("Running core HashedTable functionality test");
        try{
            outbr(1,"Creating new table");
            
            out(2,"Default index");
            Table table=new HashedTable("TableTest-test","./");
            if(!table.getTitle().equals("TableTest-test")){
                err("Wrong table title returned");
            }else if(!table.getLocation().equals("./")){
                err("Wrong table location returned");
            }else{
                table.close();
                table.deleteFiles();
                ok();
            }
            out(2,"Memory index");
            table=new HashedTable("TableTest-test","./",Table.MEMORY);
            table.close();
            table.deleteFiles();
            ok();
            
            out(2,"Flat index");
            table=new HashedTable("TableTest-test","./",Table.FLAT);
            table.close();
            table.deleteFiles();
            ok();
            
            out(2,"Paged index");
            table=new HashedTable("TableTest-test","./",Table.PAGED);
            table.close();
            table.deleteFiles();
            ok();
            
            outbr(1,"Testing add, update, delete and read of data");
            
            
            
            outbr(2,"Memory index");
            table=new HashedTable("TableTest-test","./",Table.MEMORY);
            testIndex(table);
            table.close();
            table.deleteFiles();
            
            outbr(2,"Flat index");
            table=new HashedTable("TableTest-test","./",Table.FLAT);
            testIndex(table);
            table.close();
            table.deleteFiles();
            
            outbr(2,"Paged index");
            table=new HashedTable("TableTest-test","./",Table.PAGED);
            testIndex(table);
            table.close();
            table.deleteFiles();
            
            outbr(1,"Close and reopen tables after bulk operations");
            out(2,"Memory index");
            table=new HashedTable("TableTest-test","./",Table.MEMORY);
            bulkOperate(table);
            table.close();
            table=new HashedTable("TableTest-test","./",Table.MEMORY);
            boolean bad=false;
            TupleStream stream=table.getRows();
            int count=0;
            while(stream.hasNext()){
                Row row=stream.next();
                if(row.getDouble("key3")!=3.1415)bad=true;
                count++;
            }
            if(bad){
                err("Bad values returned after reopening table");
            }else if(count!=5000){
                err("Wrong number of rows returned after reopening table");
            }else ok();
            table.close();
            table.deleteFiles();
            
            out(2,"Flat index");
            table=new HashedTable("TableTest-test","./",Table.FLAT);
            bulkOperate(table);
            table.close();
            table=new HashedTable("TableTest-test","./",Table.FLAT);
             bad=false;
             stream=table.getRows();
             count=0;
            while(stream.hasNext()){
                Row row=stream.next();
                if(row.getDouble("key3")!=3.1415)bad=true;
                count++;
            }
            if(bad){
                err("Bad values returned after reopening table");
            }else if(count!=5000){
                err("Wrong number of rows returned after reopening table");
            }else ok();
            table.close();
            table.deleteFiles();
            
            out(2,"Paged index");
            table=new HashedTable("TableTest-test","./",Table.PAGED);
            bulkOperate(table);
            table.close();
            table=new HashedTable("TableTest-test","./",Table.PAGED);
             bad=false;
             stream=table.getRows();
             count=0;
            while(stream.hasNext()){
                Row row=stream.next();
                if(row.getDouble("key3")!=3.1415)bad=true;
                count++;
            }
            if(bad){
                err("Bad values returned after reopening table");
            }else if(count!=5000){
                err("Wrong number of rows returned after reopening table");
            }else ok();
            table.close();
            table.deleteFiles();
            
        }catch(Exception e){
            err("Exception occured while testing "+e);
        }
    }
    
    public void bulkOperate(Table table) throws IOException{
        for(int i=0;i<10000;i++){
            Row row=new Row(""+i);
            row.put("key1","foo");
            row.put("key2",i);
            table.addRow(row);
        }
        for(int i=0;i<5000;i++){
            Row row=table.getRow(""+i);
            table.deleteRow(row);
        }
        for(int i=5000;i<10000;i++){
            Row row=table.getRow(""+i);
            row.put("key3",3.1415);
            row.put("key2",row.getInt("key2")+1);
            table.updateRow(row);
        }
    }
    
    public void testIndex(Table table) throws IOException{
        Row row1=new Row("1");
        row1.put("key1",1);
        row1.put("key2","foo");
        
        Row row2=new Row("2");
        row2.put("key1",2);
        row2.put("key2","foo");
        
        Row row3=new Row("3");
        row3.put("key1",3);
        row3.put("key2","foo");
        
        Row row4=new Row("4");
        row4.put("key1",4);
        row4.put("key2","foo");
        
        Row row5=new Row("5");
        row5.put("key1",5);
        row5.put("key2","foo");
        
        out(3,"Adding rows");
        table.addRow(row1);
        table.addRow(row2);
        table.addRow(row3);
        table.addRow(row4);
        table.addRow(row5);
        ok();
        
        out(3,"Reading rows");
        TupleStream stream=table.getRows();
        int count=0;
        while(stream.hasNext()){
            Row tmp=stream.next();
            count++;
        }
        if(count!=5){
            err("Wrong number of rows returned");
        }else{
            Row tmp=table.getRow("1");
            if(tmp==null){
                err("Row not found");
            }else{
                if(tmp.getInt("key1")!=1){
                    err("Wrong value in returned row");
                }else{
                    tmp=table.getRow("5");
                    if(tmp==null){
                        err("Row not found");
                    }else{
                        if(tmp.getInt("key1")!=5){
                            err("Wrong value in returned row");
                        }else{
                            ok();
                        }
                    }
                }
            }
        }
        
        out(3,"Updating rows");
        Row tmp=table.getRow("1");
        tmp.put("key3","bar");
        table.updateRow(tmp);
        tmp=table.getRow("1");
        if(!tmp.getString("key3").equals("bar")){
            err("Wrong value returned after update");
        }else ok();
        
        out(3,"Deleting rows");
        tmp=table.getRow("1");
        table.deleteRow(tmp);
        stream=table.getRows();
        count=0;
        while(stream.hasNext()){
            tmp=stream.next();
            count++;
        }
        if(count!=4){
            err("Wrong number of rows returned");
        }else ok();
    }
    
    public static void main(String args[]){
        TableTest tablet=new TableTest();
        tablet.runTest();
    }
}