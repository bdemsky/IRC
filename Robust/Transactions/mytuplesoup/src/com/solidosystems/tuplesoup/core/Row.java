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
 
package com.solidosystems.tuplesoup.core;
 
import java.io.*;
import java.util.*;
import java.nio.channels.*;

/**
 * Holds a row of data
 */
public class Row{
     private String id;
     private int size;
     private Hashtable<String,Value> values;
    
     /**
      * Creates a new empty row with the given row id.
      */
     public Row(String id){
         this.id=id;
         size=-1;
         values=new Hashtable<String,Value>();
     }
     
     /**
      * Returns the number of keys in this row.
      */
     public int getKeyCount(){
         return values.size();
     }
     
     public Set<String> keySet(){
         return values.keySet();
     }
     
     /**
      * Returns the actual size in bytes this row will take when written to a stream.
      */
     public int getSize(){
         if(size==-1)recalcSize();
         return size;
     }

     /**
      * Returns a hashcode for this row. This hashcode will be based purely on the id of the row.
      */
     public int hashCode(){
         return id.hashCode();
     }
 
     public boolean equals(Object obj){
         try{
             Row r=(Row)obj;
             return r.id.equals(id);
         }catch(Exception e){}
         return false;
     }
 
     /**
      * Returns the id of this row.
      */
     public String getId(){
         return id;
     }
 
     /**
      * Stores the given value for the given key.
      */
     public void put(String key,Value value){
         size=-1;
         values.put(key,value);
     }
     
     /**
      * Stores the given string wrapped in a value object for the given key.
      */
     public void put(String key,String value){
         size=-1;
         values.put(key,new Value(value));
     }
     
     /**
      * Stores the given int wrapped in a value object for the given key.
      */
     public void put(String key,int value){
         size=-1;
         values.put(key,new Value(value));
     }
     
     /**
      * Stores the given long wrapped in a value object for the given key.
      */
     public void put(String key,long value){
         size=-1;
         values.put(key,new Value(value));
     }
     
     /**
      * Stores the given float wrapped in a value object for the given key.
      */
     public void put(String key,float value){
         size=-1;
         values.put(key,new Value(value));
     }
     
     /**
      * Stores the given double wrapped in a value object for the given key.
      */
     public void put(String key,double value){
         size=-1;
         values.put(key,new Value(value));
     }
     
     /**
      * Stores the given boolean wrapped in a value object for the given key.
      */
     public void put(String key,boolean value){
         size=-1;
         values.put(key,new Value(value));
     }
     
     /**
      * Stores the given Date wrapped in a value object for the given key.
      */
     public void put(String key,Date value){
         size=-1;
         values.put(key,new Value(value));
     }
     
     /**
      * Returns the value stored for the current key, or a null value (not null) if the key does not exist.
      */
     public Value get(String key){
         if(!values.containsKey(key))return new Value();
         return values.get(key);
     }
     
     /**
      * Returns a string representation of the value stored for the current key.
      * If the key does not exist, an empty string will be returned.
      * See the documentation for Value to learn how the string value is generated.
      */
     public String getString(String key){
         if(!values.containsKey(key))return "";
         return values.get(key).getString();
     }
     
     /**
      * Returns an int representation of the value stored for the current key.
      * If the key does not exist, 0 will be returned.
      * See the documentation for Value to learn how the string value is generated.
      */
     public int getInt(String key){
         if(!values.containsKey(key))return 0;
          return values.get(key).getInt();
     }
     
     /**
      * Returns a long representation of the value stored for the current key.
      * If the key does not exist, 0 will be returned.
      * See the documentation for Value to learn how the string value is generated.
      */
     public long getLong(String key){
         if(!values.containsKey(key))return 0;
          return values.get(key).getLong();
     }
     
     /**
      * Returns a float representation of the value stored for the current key.
      * If the key does not exist, 0 will be returned.
      * See the documentation for Value to learn how the string value is generated.
      */
     public float getFloat(String key){
         if(!values.containsKey(key))return 0f;
          return values.get(key).getFloat();
     }
     
     /**
      * Returns a double representation of the value stored for the current key.
      * If the key does not exist, 0 will be returned.
      * See the documentation for Value to learn how the string value is generated.
      */
     public double getDouble(String key){
         if(!values.containsKey(key))return 0d;
          return values.get(key).getDouble();
     }
     
     /**
      * Returns a boolean representation of the value stored for the current key.
      * If the key does not exist, false will be returned.
      * See the documentation for Value to learn how the string value is generated.
      */
     public boolean getBoolean(String key){
         if(!values.containsKey(key))return false;
          return values.get(key).getBoolean();
     }
     
     /**
      * Returns a Date representation of the value stored for the current key.
      * If the key does not exist, the date initialized with 0 will be returned.
      * See the documentation for Value to learn how the string value is generated.
      */
     public Date getTimestamp(String key){
         if(!values.containsKey(key))return new Date(0);
          return values.get(key).getTimestamp();
      }
 
     /**
      * Utility function to calculate the distance between ints, allowing for a single wraparound.
      */
     protected static int calcSize(int pre,int post){
         if(post>pre)return post-pre;
         return (Integer.MAX_VALUE-pre)+post;
     }
    
     /**
      * Recalculate the size of the row. Be aware that this method will actually write the full row to a buffer to calculate the size.
      * Its a slow and memory consuming method to call!
      */
     private void recalcSize(){
         try{
             ByteArrayOutputStream bout=new ByteArrayOutputStream();
             DataOutputStream dout=new DataOutputStream(bout);
             writeToStream(dout);
             size=bout.size();
             dout.close();
             bout.close();
         }catch(Exception e){}
     }
    
     /**
      * Writes the contents of this row to the given RandomAccessFile
      */
     public void writeToFile(RandomAccessFile out) throws IOException{
          long pre=out.getFilePointer();
          
          out.writeUTF(id);
          
          Set<String> keys=values.keySet();
          out.writeInt(keys.size());
          Iterator<String> it=keys.iterator();
          while(it.hasNext()){
              String key=it.next();
             Value value=values.get(key);
              out.writeUTF(key);
              value.writeToFile(out);
          }
          long post=out.getFilePointer();
          int size=(int)(post-pre);
          this.size=size+4;
          out.writeInt(this.size);
      }
 
      /**
       * Writes the contents of this row to the given DataOutputStream.
       */
      public void writeToStream(DataOutputStream out) throws IOException{
         int pre=out.size();
         out.writeUTF(id);
         Set<String> keys=values.keySet();
         out.writeInt(keys.size());
         Iterator<String> it=keys.iterator();
         while(it.hasNext()){
             String key=it.next();
             Value value=values.get(key);
             out.writeUTF(key);
             value.writeToStream(out);
         }
         int post=out.size();
         int size=calcSize(pre,post);
         this.size=size+4;
         out.writeInt(this.size);
     }
 
     /**
      * Reads a full row from the given DataInputStream and returns it.
      */
     public static Row readFromStream(DataInputStream in) throws IOException{
         String id=in.readUTF();
         Row row=new Row(id);
         int size=in.readInt();
         for(int i=0;i<size;i++){
             String key=in.readUTF();
             Value value=Value.readFromStream(in);
             row.put(key,value);
         }
         size=in.readInt();
         row.size=size;
         return row;
     }
 
     /**
      * Returns a string representing this row formatted as the following example:
      * (1732)=>{"name":string:"Kasper J. Jeppesen","age":int:31}
      *
      * @return a string representation of this row
      */
     public String toString(){
         StringBuffer buf=new StringBuffer();
         buf.append("("+id+")=>{");
         Iterator<String> it=values.keySet().iterator();
         boolean first=true;
         while(it.hasNext()){
             if(!first){
                 buf.append(",");
             }else{
                 first=false;
             }
             String key=it.next();
             buf.append("\"");
             buf.append(key);
             buf.append("\":");
             Value value=values.get(key);
             buf.append(value.getTypeName());
             buf.append(":");
             if(value.getType()==Value.STRING){
                buf.append("\"");
                // TODO: This string should be escaped properly
                buf.append(value.getString());
                buf.append("\"");
             }else{
                 buf.append(value.getString());
             }
         }
         buf.append("}");
         return buf.toString();
     }
     
     /**
      * Shorthand for calling toBasicXMLString("")
      */
     public String toBasicXMLString(){
         return toBasicXMLString("");
     }
     
     /**
      * Creates an indentation of the given size and calls toBasicXMLString(String) with the indentation string as parameter.
      */
     public String toBasicXMLString(int indentation){
         StringBuffer buf=new StringBuffer();
         for(int i=0;i<indentation;i++){
             buf.append(" ");
         }
         return toBasicXMLString(buf.toString());
     }
     
     /**
      * Creates a basic xml representation of the row as shown in the following sample:
      * &lt;row id="1"&gt;
      *   &lt;value name="foo" type="string"&gt;Bar&lt;/value&gt;
      * &lt;/row&gt;
      */
     public String toBasicXMLString(String indentation){
         StringBuffer buf=new StringBuffer();
         buf.append(indentation);
         buf.append("<row id=\""+id+"\">\n");
         Iterator<String> it=values.keySet().iterator();
         while(it.hasNext()){
             String key=it.next();
             Value value=values.get(key);
             buf.append(indentation);
             buf.append("   ");
             buf.append(value.toBasicXMLString(key));
             buf.append("\n");
         }
         buf.append(indentation);
         buf.append("</row>\n");
         return buf.toString();
     }
 }