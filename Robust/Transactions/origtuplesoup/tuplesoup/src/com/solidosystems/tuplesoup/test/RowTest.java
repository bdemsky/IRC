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

public class RowTest extends BasicTest{
    public RowTest(){
        
    }
    public void runTest(){
        outbr("Running core Row functionality test");
        outbr(1,"Constructor initializers");
        
        out(2,"String");
        Row row=new Row("FooBarBaz");
        if(!row.getId().equals("FooBarBaz")){
            err("Id value not correct");
        }else if(row.getKeyCount()!=0){
            err("Key count not correct");
        }else ok();
        
        outbr(1,"Setting and getting values");
        
        out(2,"Setting values");
        row.put("key1",new Value(3.1415f));
        row.put("key2","FooBar");
        row.put("key3",1234);
        row.put("key4",5123456789l);
        row.put("key5",3.1415f);
        row.put("key6",3.141592653689793284626433d);
        row.put("key7",true);
        row.put("key8",new java.util.Date(5));
        if(row.getKeyCount()!=8){
            err("Key count not correct");
        }else ok();
        
        out(2,"Getting values");
        Value val1=new Value("FooBar");
        Value val2=new Value(1234);
        Value val3=new Value(5123456789l);
        Value val4=new Value(3.1415f);
        Value val5=new Value(3.141592653689793284626433d);
        Value val6=new Value(true);
        Value val7=new Value(new java.util.Date(5));
        if(!row.get("key1").equals(val4)){
            err("Wrong Value value returned");
        }else if(!row.get("key2").equals(val1)){
            err("Wrong Value value returned");
        }else if(!row.get("key3").equals(val2)){
            err("Wrong Value value returned");
        }else if(!row.get("key4").equals(val3)){
            err("Wrong Value value returned");
        }else if(!row.get("key5").equals(val4)){
            err("Wrong Value value returned");
        }else if(!row.get("key6").equals(val5)){
            err("Wrong Value value returned");
        }else if(!row.get("key7").equals(val6)){
            err("Wrong Value value returned");
        }else if(row.getKeyCount()!=8){
            err("Wrong key count returned");
        }else if(row.getFloat("key1")!=3.1415f){
            err("Wrong value returned from getFloat");
        }else if(!row.getString("key2").equals("FooBar")){
            err("Wrong value returned from getString");
        }else if(row.getInt("key3")!=1234){
            err("Wrong value returned from getInt");
        }else if(row.getLong("key4")!=5123456789l){
            err("Wrong value returned from getLong");
        }else if(row.getFloat("key5")!=3.1415f){
            err("Wrong value returned from getFloat");
        }else if(row.getDouble("key6")!=3.141592653689793284626433d){
            err("Wrong value returned from getDouble");
        }else if(!row.getBoolean("key7")){
            err("Wrong value returned from getBoolean");
        }else if(row.getTimestamp("key8").getTime()!=5){
            err("Wrong value returned from getTimestamp");
        }else ok();
        
        outbr(1,"Stream reading and writing");
        try{
             out(2,"Writing to stream");
             ByteArrayOutputStream bout=new ByteArrayOutputStream();
             DataOutputStream dout=new DataOutputStream(bout);
             
             row.writeToStream(dout);
             row.put("key9","ExtraBonusValue");
             row.writeToStream(dout);
             dout.flush();
             ok();
             
             out(2,"Reading from stream");
             byte[] buf=bout.toByteArray();
             DataInputStream din=new DataInputStream(new ByteArrayInputStream(buf));
             row=Row.readFromStream(din);
             if(row.getKeyCount()!=8){
                 err("Wrong key count returned");
             }else if(row.getFloat("key1")!=3.1415f){
                  err("Wrong value returned from getFloat");
             }else if(!row.getString("key2").equals("FooBar")){
                  err("Wrong value returned from getString");
             }else if(row.getInt("key3")!=1234){
                  err("Wrong value returned from getInt");
             }else if(row.getLong("key4")!=5123456789l){
                  err("Wrong value returned from getLong");
             }else if(row.getFloat("key5")!=3.1415f){
                  err("Wrong value returned from getFloat");
             }else if(row.getDouble("key6")!=3.141592653689793284626433d){
                  err("Wrong value returned from getDouble");
             }else if(!row.getBoolean("key7")){
                  err("Wrong value returned from getBoolean");
             }else if(row.getTimestamp("key8").getTime()!=5){
                 err("Wrong value returned from getTimestamp");
             }else{
                 row=Row.readFromStream(din);
                 if(!row.getString("key9").equals("ExtraBonusValue")){
                     err("Wrong value returned on second row written");
                 }else ok();
             }
              
        }catch(Exception e){
            err("Exception occured "+e);
        }
        
        printErrorSummary();
    }
    public static void main(String args[]){
        RowTest rowt=new RowTest();
        rowt.runTest();
    }
}