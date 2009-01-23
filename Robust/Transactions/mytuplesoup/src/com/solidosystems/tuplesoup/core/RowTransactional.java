/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.solidosystems.tuplesoup.core;

import TransactionalIO.core.TransactionalFile;
import dstm2.atomic;
import dstm2.util.StringKeyHashMap;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
/**
 *
 * @author navid
 */
public class RowTransactional {
     private String id;
    // private int size;
     RowTSInf atomicfields;
     private StringKeyHashMap<ValueTransactional> values;
     
     public @atomic interface RowTSInf{
        int getSize();
        void setSize(int val);
     }
     
     public RowTransactional(String id){
         this.id=id;
         atomicfields.setSize(-1);
         values=new StringKeyHashMap<ValueTransactional>();
     }
     
     /**
      * Returns the number of keys in this row.
      */
     public int getKeyCount(){
         return values.size();
     }
     
     public Set keySet(){
         return values.entrySet();
     }
     
     /**
      * Returns the actual size in bytes this row will take when written to a stream.
      */
     public int getSize(){
         if(atomicfields.getSize()==-1)recalcSize();
         return atomicfields.getSize();
     }

     /**
      * Returns a hashcode for this row. This hashcode will be based purely on the id of the row.
      */
     public int hashCode(){
         return id.hashCode();
     }
 
     public boolean equals(Object obj){
         try{
             RowTransactional r=(RowTransactional)obj;
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
     public void put(String key,ValueTransactional value){
         atomicfields.setSize(-1);
         values.put(key,value);
     }
     
     /**
      * Stores the given string wrapped in a value object for the given key.
      */
     public void put(String key,String value){
         atomicfields.setSize(-1);
         values.put(key,new ValueTransactional(value));
     }
     
     /**
      * Stores the given int wrapped in a value object for the given key.
      */
     public void put(String key,int value){
         atomicfields.setSize(-1);
         values.put(key,new ValueTransactional(value));
     }
     
     /**
      * Stores the given long wrapped in a value object for the given key.
      */
     public void put(String key,long value){
         atomicfields.setSize(-1);
         values.put(key,new ValueTransactional(value));
     }
     
     /**
      * Stores the given float wrapped in a value object for the given key.
      */
     public void put(String key,float value){
         atomicfields.setSize(-1);
         values.put(key,new ValueTransactional(value));
     }
     
     /**
      * Stores the given double wrapped in a value object for the given key.
      */
     public void put(String key,double value){
         atomicfields.setSize(-1);
         values.put(key,new ValueTransactional(value));
     }
     
     /**
      * Stores the given boolean wrapped in a value object for the given key.
      */
     public void put(String key,boolean value){
         atomicfields.setSize(-1);
         values.put(key,new ValueTransactional(value));
     }
     
     /**
      * Stores the given Date wrapped in a value object for the given key.
      */
     public void put(String key,Date value){
         atomicfields.setSize(-1);
         values.put(key,new ValueTransactional(value));
     }
     
     /**
      * Returns the value stored for the current key, or a null value (not null) if the key does not exist.
      */
     public ValueTransactional get(String key){
         if(!values.containsKey(key))return new ValueTransactional();
         return values.get(key.hashCode());
     }
     
     /**
      * Returns a string representation of the value stored for the current key.
      * If the key does not exist, an empty string will be returned.
      * See the documentation for Value to learn how the string value is generated.
      */
     public String getString(String key){
         if(!values.containsKey(key))return "";
         return values.get(key.hashCode()).getString();
     }
     
     /**
      * Returns an int representation of the value stored for the current key.
      * If the key does not exist, 0 will be returned.
      * See the documentation for Value to learn how the string value is generated.
      */
     public int getInt(String key){
         if(!values.containsKey(key))return 0;
          return values.get(key.hashCode()).getInt();
     }
     
     /**
      * Returns a long representation of the value stored for the current key.
      * If the key does not exist, 0 will be returned.
      * See the documentation for Value to learn how the string value is generated.
      */
     public long getLong(String key){
         if(!values.containsKey(key))return 0;
          return values.get(key.hashCode()).getLong();
     }
     
     /**
      * Returns a float representation of the value stored for the current key.
      * If the key does not exist, 0 will be returned.
      * See the documentation for Value to learn how the string value is generated.
      */
     public float getFloat(String key){
         if(!values.containsKey(key))return 0f;
          return values.get(key.hashCode()).getFloat();
     }
     
     /**
      * Returns a double representation of the value stored for the current key.
      * If the key does not exist, 0 will be returned.
      * See the documentation for Value to learn how the string value is generated.
      */
     public double getDouble(String key){
         if(!values.containsKey(key))return 0d;
          return values.get(key.hashCode()).getDouble();
     }
     
     /**
      * Returns a boolean representation of the value stored for the current key.
      * If the key does not exist, false will be returned.
      * See the documentation for Value to learn how the string value is generated.
      */
     public boolean getBoolean(String key){
         if(!values.containsKey(key))return false;
          return values.get(key.hashCode()).getBoolean();
     }
     
     /**
      * Returns a Date representation of the value stored for the current key.
      * If the key does not exist, the date initialized with 0 will be returned.
      * See the documentation for Value to learn how the string value is generated.
      */
     public Date getTimestamp(String key){
         if(!values.containsKey(key))return new Date(0);
          return values.get(key.hashCode()).getTimestamp();
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
             this.atomicfields.setSize(bout.size());
             dout.close();
             bout.close();
         }catch(Exception e){}
     }
    
     /**
      * Writes the contents of this row to the given RandomAccessFile
      */
     public void writeToFile(TransactionalFile out) throws IOException{
          long pre=out.getFilePointer();
          
          out.writeUTF(id);
          
          Set<StringKeyHashMap.TEntry<ValueTransactional>> pairs=values.entrySet(); 
          out.writeInt(pairs.size());
          Iterator<StringKeyHashMap.TEntry<ValueTransactional>> it= pairs.iterator();
          while(it.hasNext()){
             String key= it.next().getKey();
             ValueTransactional value= values.get(key);
             out.writeUTF(key);
             value.writeToFile(out);
          }
          
          long post=out.getFilePointer();
          int size=(int)(post-pre);
          this.atomicfields.setSize(size+4);
          out.writeInt(this.atomicfields.getSize());
      }
 
      /**
       * Writes the contents of this row to the given DataOutputStream.
       */
      public void writeToStream(DataOutputStream out) throws IOException{
         int pre=out.size();
         out.writeUTF(id);
         Set<StringKeyHashMap.TEntry<ValueTransactional>> pairs=values.entrySet();
         out.writeInt(pairs.size());
         
         Iterator<StringKeyHashMap.TEntry<ValueTransactional>> it=pairs.iterator();
         while(it.hasNext()){
             String key=it.next().getKey();
             ValueTransactional value=values.get(key);
             out.writeUTF(key);
             value.writeToStream(out);
         }
         int post=out.size();
         int size=calcSize(pre,post);
         this.atomicfields.setSize(size+4);
         out.writeInt(this.atomicfields.getSize());
     }
 
     /**
      * Reads a full row from the given DataInputStream and returns it.
      */
     public static RowTransactional readFromStream(DataInputStream in) throws IOException{
         String id=in.readUTF();
         RowTransactional row=new RowTransactional(id);
         int size=in.readInt();
         for(int i=0;i<size;i++){
             String key=in.readUTF();
             ValueTransactional value=ValueTransactional.readFromStream(in);
             row.put(key,value);
         }
         size=in.readInt();
         row.atomicfields.setSize(size);
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
         Iterator<StringKeyHashMap.TEntry<ValueTransactional>> it=values.entrySet().iterator();
         boolean first=true;
         while(it.hasNext()){
             if(!first){
                 buf.append(",");
             }else{
                 first=false;
             }
             String key=it.next().getKey();
             buf.append("\"");
             buf.append(key);
             buf.append("\":");
             ValueTransactional value=values.get(key);
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
         Iterator<StringKeyHashMap.TEntry<ValueTransactional>> it=values.entrySet().iterator();
         while(it.hasNext()){
             String key=it.next().getKey();
             ValueTransactional value=values.get(key);
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
