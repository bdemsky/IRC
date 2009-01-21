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

public class ValueTest extends BasicTest{
    private Value val=null;
    
    public ValueTest(){
    }
    public void runTest(){
        outbr("Running core Value functionality test");
        
        outbr(1,"Constructor initializers");
        
        out(2,"String");
        val=new Value("FooBar");
        if(val.getType()!=Value.STRING){
            err("Numerical type incorrect");
        }else{
            if(!val.getTypeName().equals("string")){
                err("Type name incorrect");
            }else{
                if(!val.getString().equals("FooBar")){
                    err("Returned value incorrect");
                }else ok();
            }
        }
        
        out(2,"Timestamp");
        val=new Value(new java.util.Date(5));
        if(val.getType()!=Value.TIMESTAMP){
            err("Numerical type incorrect");
        }else{
            if(!val.getTypeName().equals("timestamp")){
                err("Type name incorrect");
            }else{
                if(val.getTimestamp().getTime()!=5){
                    err("Returned value incorrect");
                }else ok();
            }
        }
        
        out(2,"Null");
        val=new Value();
        if(val.getType()!=Value.NULL){
            err("Numerical type incorrect");
        }else{
            if(!val.getTypeName().equals("null")){
                err("Type name incorrect");
            }else ok();
        }
        
        out(2,"Int");
        val=new Value(123456789);
        if(val.getType()!=Value.INT){
            err("Numerical type incorrect");
        }else{
            if(!val.getTypeName().equals("int")){
                err("Type name incorrect");
            }else{
                if(val.getInt()!=123456789){
                    err("Returned value incorrect");
                }else ok();
            }
        }
        
        out(2,"Long");
        val=new Value(5123456789l);
        if(val.getType()!=Value.LONG){
            err("Numerical type incorrect");
        }else{
            if(!val.getTypeName().equals("long")){
                err("Type name incorrect");
            }else{
                if(val.getLong()!=5123456789l){
                    err("Returned value incorrect");
                }else ok();
            }
        }
        
        out(2,"Float");
        val=new Value(3.141592f);
        if(val.getType()!=Value.FLOAT){
            err("Numerical type incorrect");
        }else{
            if(!val.getTypeName().equals("float")){
                err("Type name incorrect");
            }else{
                if(val.getFloat()!=3.141592f){
                    err("Returned value incorrect");
                }else ok();
            }
        }

        out(2,"Double");
        val=new Value(3.141592653689793284626433d);
        if(val.getType()!=Value.DOUBLE){
            err("Numerical type incorrect");
        }else{
            if(!val.getTypeName().equals("double")){
                err("Type name incorrect");
            }else{
                if(val.getDouble()!=3.141592653689793284626433d){
                    err("Returned value incorrect");
                }else ok();
            }
        }
        
        out(2,"Boolean");
        val=new Value(true);
        if(val.getType()!=Value.BOOLEAN){
            err("Numerical type incorrect");
        }else{
            if(!val.getTypeName().equals("boolean")){
                err("Type name incorrect");
            }else{
                if(val.getBoolean()!=true){
                    err("Returned value incorrect");
                }else ok();
            }
        }
        
        out(2,"Binary");
        byte[] abuf=new byte[10];
        abuf[0]=0;
        abuf[1]=1;
        abuf[2]=2;
        abuf[3]=3;
        abuf[4]=4;
        abuf[5]=5;
        abuf[6]=6;
        abuf[7]=7;
        abuf[8]=8;
        abuf[9]=9;
        val=new Value(abuf);
        if(val.getType()!=Value.BINARY){
            err("Numerical type incorrect");
        }else{
            if(!val.getTypeName().equals("binary")){
                err("Type name incorrect");
            }else{
                if(val.getBinary()[9]!=9){
                    err("Returned value incorrect");
                }else ok();
            }
        }
        
        outbr(1,"Stream reading and writing");
        try{
             out(2,"Writing to stream");
             ByteArrayOutputStream bout=new ByteArrayOutputStream();
             DataOutputStream dout=new DataOutputStream(bout);
             
             Value val1=new Value();
             Value val2=new Value("foo");
             Value val3=new Value(1234);
             Value val4=new Value(5123456789l);
             Value val5=new Value(3.1415f);
             Value val6=new Value(3.141592653689793284626433d);
             Value val7=new Value(true);
             Value val8=new Value(abuf);
             
             val1.writeToStream(dout);
             val2.writeToStream(dout);
             val3.writeToStream(dout);
             val4.writeToStream(dout);
             val5.writeToStream(dout);
             val6.writeToStream(dout);
             val7.writeToStream(dout);
             val8.writeToStream(dout);
             dout.flush();
             
             ok();
             
             out(2,"Reading from stream");
             byte[] buf=bout.toByteArray();
             DataInputStream din=new DataInputStream(new ByteArrayInputStream(buf));
             val1=Value.readFromStream(din);
             val2=Value.readFromStream(din);
             val3=Value.readFromStream(din);
             val4=Value.readFromStream(din);
             val5=Value.readFromStream(din);
             val6=Value.readFromStream(din);
             val7=Value.readFromStream(din);
             val8=Value.readFromStream(din);
             ok();
             
             out(2,"Verifying values");
             if(val1.getType()!=Value.NULL){
                 err("Wrong value returned for null");
             }else{
                 if(!val2.getString().equals("foo")){
                      err("Wrong value returned for string");
                  }else{
                      if(val3.getInt()!=1234){
                          err("Wrong value returned for int");
                      }else{
                          if(val4.getLong()!=5123456789l){
                              err("Wrong value returned for long");
                          }else{
                              if(val5.getFloat()!=3.1415f){
                                  err("Wrong value returned for float");
                              }else{
                                  if(val6.getDouble()!=3.141592653689793284626433d){
                                      err("Wrong value returned for double");
                                  }else{
                                      if(val7.getBoolean()!=true){
                                          err("Wrong value returned for boolean");
                                      }else{
                                          if(val8.getBinary()[9]!=9){
                                              err("Wrong value returned for binary");
                                          }else ok();
                                      }
                                  }
                              }
                          }
                      }
                  }
             }
        }catch(Exception e){
            err("Failed with exception "+e);
        }
        
        outbr(1,"Value comparisons");
        out(2,"Equals");
        Value val1a=new Value();
        Value val2a=new Value("foo");
        Value val3a=new Value(1234);
        Value val4a=new Value(5123456789l);
        Value val5a=new Value(3.1415f);
        Value val6a=new Value(3.141592653689793284626433d);
        Value val7a=new Value(true);
        
        Value val1b=new Value();
        Value val2b=new Value("foo");
        Value val3b=new Value(1234);
        Value val4b=new Value(5123456789l);
        Value val5b=new Value(3.1415f);
        Value val6b=new Value(3.141592653689793284626433d);
        Value val7b=new Value(true);
        
        if(!val1a.equals(val1b)){
            err("Null values not compared correctly");
        }else if(!val2a.equals(val2b)){
            err("String values not compared correctly");
        }else if(!val3a.equals(val3b)){
            err("Int values not compared correctly");
        }else if(!val4a.equals(val4b)){
            err("Long values not compared correctly");
        }else if(!val5a.equals(val5b)){
            err("Float values not compared correctly");
        }else if(!val6a.equals(val6b)){
            err("Double values not compared correctly");
        }else if(!val7a.equals(val7b)){
            err("Boolean values not compared correctly");
        }else ok();
        
        out(2,"Greater than");
        val3b=new Value(2234);
        val4b=new Value(6123456789l);
        val5b=new Value(4.1415f);
        val6b=new Value(4.141592653689793284626433d);
        if(val3a.greaterThan(val3b)){
            err("Int values not compared correctly");
        }else if(val4a.greaterThan(val4b)){
            err("Long values not compared correctly");
        }else if(val5a.greaterThan(val5b)){
            err("Float values not compared correctly");
        }else if(val6a.greaterThan(val6b)){
            err("Double values not compared correctly");
        }else ok();
        
        out(2,"Less than");
        val3b=new Value(2234);
        val4b=new Value(6123456789l);
        val5b=new Value(4.1415f);
        val6b=new Value(4.141592653689793284626433d);
        if(!val3a.lessThan(val3b)){
            err("Int values not compared correctly");
        }else if(!val4a.lessThan(val4b)){
            err("Long values not compared correctly");
        }else if(!val5a.lessThan(val5b)){
            err("Float values not compared correctly");
        }else if(!val6a.lessThan(val6b)){
            err("Double values not compared correctly");
        }else ok();
        
        out(2,"Contains");
        val1a=new Value("FooBarBaz");
        val2a=new Value("Bar");
        if(!val1a.contains(val2a)){
            err("String values not compared correctly");
        }else ok();
        
        out(2,"Starts with");
        val1a=new Value("FooBarBaz");
        val2a=new Value("Foo");
        if(!val1a.startsWith(val2a)){
            err("String values not compared correctly");
        }else ok();
        
        out(2,"Ends with");
        val1a=new Value("FooBarBaz");
        val2a=new Value("Baz");
        if(!val1a.endsWith(val2a)){
            err("String values not compared correctly");
        }else ok();
        
        out(2,"CompareTo");
        val1a=new Value(1);
        val2a=new Value(2);
        if(val1a.compareTo(val2a)!=-1){
            err("CompareTo did not return -1");
        }else if(val2a.compareTo(val1a)!=1){
            err("CompareTo did not return 1");
        }else{
            val2a=new Value(1);
            if(val1a.compareTo(val2a)!=0){
                err("CompareTo did not return 0");
            }else ok();
        }
        
        
        printErrorSummary();
    }
    
    public static void main(String args[]){
        ValueTest valuet=new ValueTest();
        valuet.runTest();
    }
}