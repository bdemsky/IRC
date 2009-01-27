/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.solidosystems.tuplesoup.core;

import TransactionalIO.core.TransactionalFile;
import dstm2.AtomicByteArray;
import dstm2.AtomicSuperClass;
import dstm2.atomic;
import dstm2.factory.Factory;
import java.util.*;
import java.io.*;
import dstm2.Thread;

/**
 *
 * @author navid
 */
public class ValueTransactional implements AtomicSuperClass{

     ValueTSInf atomicfields;
     static Factory<ValueTSInf> factory = Thread.makeFactory(ValueTSInf.class);
     
     public @atomic interface ValueTSInf{
         Byte getType();
         String getStr_value();
         Long getInt_value();
         Double getFloat_value();
         AtomicByteArray getBinary();
         
         void setType(Byte val);
         void setStr_value(String val);
         void setInt_value(Long val);
         void setFloat_value(Double val);
         void setBinary(AtomicByteArray bytes);
     }   
     
     
     public final static int NULL=0;
     public final static int STRING=1;
     public final static int INT=2;
     public final static int LONG=3;
     public final static int FLOAT=4;
     public final static int DOUBLE=5;
     public final static int BOOLEAN=6;
     public final static int TIMESTAMP=7;
     public final static int BINARY=8;

  /*   private byte type=NULL; // The type of the value being held
     private String str_value=null;
     private long int_value=0;
     private double float_value=0.0;
     private byte[] binary=null;*/

     /**
      * Returns the numerical type id for this value.
      */
     public int getType(){
         return atomicfields.getType();
     }
     
     /**
      * Returns the name this value's type.
      */
     public String getTypeName(){
         switch(atomicfields.getType()){
              case STRING     : return "string";
              case INT        : return "int";
              case LONG       : return "long";
              case FLOAT      : return "float";
              case DOUBLE     : return "double";
              case BOOLEAN    : return "boolean";
              case TIMESTAMP  : return "timestamp";
              case BINARY     : return "binary";
          }
          return "null";
     }

     /**
      * An implementation of the hashCode method as defined in java.lang.Object
      * 
      * @return a hash code value for this object
      */
     public int hashCode(){
         int hash=0;
         switch(atomicfields.getType()){
             case STRING     : hash+=atomicfields.getStr_value().hashCode();
             case INT        : hash+=atomicfields.getInt_value();
             case LONG       : hash+=atomicfields.getInt_value();
             case FLOAT      : hash+=atomicfields.getFloat_value();
             case DOUBLE     : hash+=atomicfields.getFloat_value();
             case BOOLEAN    : hash+=atomicfields.getInt_value();
             case TIMESTAMP  : hash+=atomicfields.getInt_value();
             case BINARY     : hash+=atomicfields.getBinary().hashCode();
         }
         return hash;
     }

     /**
      * Returns true only if this Value has specifically been set to null.
      *
      * @return true if the data being held is null, false otherwise
      */
     public boolean isNull(){
         return atomicfields.getType()==NULL;
     }

     /**
      * Returns -1, 0 or 1 if this value is smaller, equal or larger than the value given as a parameter.
      */
     public int compareTo(ValueTransactional value){
         if(atomicfields.getType()==STRING){
             return atomicfields.getStr_value().compareTo(value.getString());
         }
         if(lessThan(value))return -1;
         if(greaterThan(value))return 1;
         return 0;
     }

     /**
      * Attempts to compare this Value to the value given as parameter and returns true if this value is less than the value given as a parameter.
      * The types used for the comparison will always be based on the type of this Value based on the following rules.
      * <ul>
      *   <li>If this Value is a numeric type, then the other Value will be asked to deliver the same numeric type for the comparison.
      *   <li>If this Value is a string, then both values will be asked to deliver a double value for the comparison.
      *   <li>If this Value is a timestamp, then both values will be asked to deliver a long value for the comparison.
      *   <li>If this Value is a boolean, false will be returned.
      * </ul>
      *
      * @param value the value this value should be compared to
      * @return true if this value is less than the value given as a parameter, false otherwise
      */
     public boolean lessThan(ValueTransactional value){
         switch(atomicfields.getType()){
             case STRING     : return getDouble()<value.getDouble();
             case INT        : return getInt()<value.getInt();
             case LONG       : return getLong()<value.getLong();
             case FLOAT      : return getFloat()<value.getFloat();
             case DOUBLE     : return getDouble()<value.getDouble();
             case TIMESTAMP  : return getLong()<value.getLong();
         }
         return false;
     }

     /**
      * Attempts to compare this Value to the value given as parameter and returns true if this value is greater than the value given as a parameter.
      * The types used for the comparison will always be based on the type of this Value based on the following rules.
      * <ul>
      *   <li>If this Value is a numeric type, then the other Value will be asked to deliver the same numeric type for the comparison.
      *   <li>If this Value is a string, then both values will be asked to deliver a double value for the comparison.
      *   <li>If this Value is a timestamp, then both values will be asked to deliver a long value for the comparison.
      *   <li>If this Value is a boolean, false will be returned.
      * </ul>
      *
      * @param value the value this value should be compared to
      * @return true if this value is greater than the value given as a parameter, false otherwise
      */
     public boolean greaterThan(ValueTransactional value){
         switch(atomicfields.getType()){
             case STRING     : return getDouble()>value.getDouble();
             case INT        : return getInt()>value.getInt();
             case LONG       : return getLong()>value.getLong();
             case FLOAT      : return getFloat()>value.getFloat();
             case DOUBLE     : return getDouble()>value.getDouble();
             case TIMESTAMP  : return getLong()>value.getLong();
         }
         return false;
     }

     /**
      * Returns true if the string representation of this value starts with the string representation of the value given as parameter.
      */
     public boolean startsWith(ValueTransactional value){
         return getString().startsWith(value.getString());
     }

     /**
      * Returns true if the string representation of this value ends with the string representation of the value given as parameter.
      */
     public boolean endsWith(ValueTransactional value){
         return getString().endsWith(value.getString());
     }

     /**
      * Returns true if the string representation of this value contains the string representation of the value given as parameter.
      */
     public boolean contains(ValueTransactional value){
         return getString().indexOf(value.getString())>-1;
     }

     /**
      * Returns true if the contents of this value equals the contents of the value given as parameter.
      */
     public boolean equals(Object obj){
         try{
             ValueTransactional val=(ValueTransactional)obj;
             if(val.atomicfields.getType()==atomicfields.getType()){
                 switch(atomicfields.getType()){
                     case NULL       : return true;
                     case STRING     : return atomicfields.getStr_value().equals(val.atomicfields.getStr_value());
                     case INT        : return atomicfields.getInt_value()==atomicfields.getInt_value();
                     case LONG       : return atomicfields.getInt_value()==atomicfields.getInt_value();
                     case FLOAT      : return atomicfields.getFloat_value()==atomicfields.getFloat_value();
                     case DOUBLE     : return atomicfields.getFloat_value()==atomicfields.getFloat_value();
                     case BOOLEAN    : return atomicfields.getInt_value()==atomicfields.getInt_value();
                     case TIMESTAMP  : return atomicfields.getInt_value()==atomicfields.getInt_value();
                     case BINARY     : if(atomicfields.getBinary().length()==val.atomicfields.getBinary().length()){
                                          for(int i=0;i<atomicfields.getBinary().length();i++){
                                              if(atomicfields.getBinary().get(i)!=val.atomicfields.getBinary().get(i))return false;
                                          }
                                       }
                                       return true;
                 }
             }
         }catch(Exception e){}
         return false;
     }

     /**
      * Returns a string representation of this object.
      */
     public String toString(){
         return getString();
     }

     /**
      * Returns a string representation of this object (identical to toString)
      */
     public String get(){
         return getString();
     }

     /**
      * Returns this value as an xml tag with the given key set as an attribute called name.
      * The following string is an example of the int value 1234 created with the key foo &lt;value name="foo" type="int"&gt;1234&lt;/value&gt;
      */
     public String toBasicXMLString(String key){
         switch(atomicfields.getType()){
             case STRING     : return "<value name=\""+key+"\" type=\"string\">"+atomicfields.getStr_value()+"</value>";
             case INT        : return "<value name=\""+key+"\"  type=\"int\">"+atomicfields.getInt_value()+"</value>";
             case LONG       : return "<value name=\""+key+"\"  type=\"long\">"+atomicfields.getInt_value()+"</value>";
             case FLOAT      : return "<value name=\""+key+"\"  type=\"float\">"+atomicfields.getFloat_value()+"</value>";
             case DOUBLE     : return "<value name=\""+key+"\"  type=\"double\">"+atomicfields.getFloat_value()+"</value>";
             case BOOLEAN    : if(atomicfields.getInt_value()==1){
                                     return "<value name=\""+key+"\"  type=\"boolean\">TRUE</value>";
                               }else{
                                     return "<value name=\""+key+"\"  type=\"boolean\">FALSE</value>";
                               }
             case TIMESTAMP  : return "<value name=\""+key+"\"  type=\"timestamp\">"+new Date(atomicfields.getInt_value()).toString()+"</value>";
             case BINARY     : return "<value name=\""+key+"\" type=\"binary\">"+getString()+"</value>";
         }
         return "<value name=\""+key+"\"  type=\"null\"></value>";
     }
     
     /**
       * Returns this value as an xml tag.
       * The following string is an example of the int value 1234 &lt;value type="int"&gt;1234&lt;/value&gt;
       */
     public String toBasicXMLString(){
          switch(atomicfields.getType()){
              case STRING     : return "<value type=\"string\">"+atomicfields.getStr_value()+"</value>";
              case INT        : return "<value type=\"int\">"+atomicfields.getInt_value()+"</value>";
              case LONG       : return "<value type=\"long\">"+atomicfields.getInt_value()+"</value>";
              case FLOAT      : return "<value type=\"float\">"+atomicfields.getFloat_value()+"</value>";
              case DOUBLE     : return "<value type=\"double\">"+atomicfields.getFloat_value()+"</value>";
              case BOOLEAN    : if(atomicfields.getInt_value()==1){
                                      return "<value type=\"boolean\">TRUE</value>";
                                }else{
                                      return "<value type=\"boolean\">FALSE</value>";
                                }
              case TIMESTAMP  : return "<value type=\"timestamp\">"+new Date(atomicfields.getInt_value()).toString()+"</value>";
              case BINARY     : return "<value type=\"binary\">"+getString()+"</value>";
          }
          return "<value type=\"null\"></value>";
      }

     /**
      * Returns a string representation of this value
      */
     public String getString(){
         switch(atomicfields.getType()){
             case STRING     : return atomicfields.getStr_value();
             case INT        : return ""+atomicfields.getInt_value();
             case LONG       : return ""+atomicfields.getInt_value();
             case FLOAT      : return ""+atomicfields.getFloat_value();
             case DOUBLE     : return ""+atomicfields.getFloat_value();
             case BOOLEAN    : if(atomicfields.getInt_value()==1){
                                     return "TRUE";
                               }else{
                                     return "FALSE";
                               }
             case TIMESTAMP  : return new Date(atomicfields.getInt_value()).toString();
             case BINARY     : StringBuffer buf=new StringBuffer();
                               for(int i=0;i<atomicfields.getBinary().length();i++){
                                  byte b=atomicfields.getBinary().get(i);
                                  buf.append(Integer.toHexString((b & 0xFF) | 0x100).substring(1,3));
                               }
                               return buf.toString();
         }
         return "";
     }

     /**
      * Attempts to return an integer representation of this value.
      * Numerical values will be returned directly (demoted if necessary).
      * Boolean values will be returned as 1 or 0.
      * Timestamp values will be returned as a unix timestamp representation divided by 1000.
      * String values will be atempted to be converted into an int, if that fails, 0 will be returned.
      * Everything else will return 0.
      */
     public int getInt(){
         try{
             switch(atomicfields.getType()){
                 case STRING     : return Integer.parseInt(atomicfields.getStr_value());
                 case INT        : return atomicfields.getInt_value().intValue();
                 case LONG       : return (int)atomicfields.getInt_value().intValue();
                 case FLOAT      : return (int)atomicfields.getFloat_value().intValue();
                 case DOUBLE     : return (int)atomicfields.getFloat_value().intValue();
                 case BOOLEAN    : return (int)atomicfields.getInt_value().intValue();
                 case TIMESTAMP  : return (int)(atomicfields.getInt_value()/1000);
             }
         }catch(Exception e){}
         return 0;
     }

     /**
       * Attempts to return a long representation of this value.
       * Numerical values will be returned directly.
       * Boolean values will be returned as 1 or 0.
       * Timestamp values will be returned as a unix timestamp representation.
       * String values will be atempted to be converted into an int, if that fails, 0 will be returned.
       * Everything else will return 0.
       */
     public long getLong(){
         try{
             switch(atomicfields.getType()){
                 case STRING     : return Long.parseLong(atomicfields.getStr_value());
                 case INT        : return atomicfields.getInt_value();
                 case LONG       : return atomicfields.getInt_value();
                 case FLOAT      : return (long)atomicfields.getFloat_value().longValue();
                 case DOUBLE     : return (long)atomicfields.getFloat_value().longValue();
                 case BOOLEAN    : return atomicfields.getInt_value();
                 case TIMESTAMP  : return atomicfields.getInt_value();
             }
         }catch(Exception e){}
         return 0;
     }

     /**
      * Attempts to return a float representation of this value.
      * Numerical values will be returned directly (demoted if necessary).
      * Boolean values will be returned as 1 or 0.
      * Timestamp values will be returned as a unix timestamp representation divided by 1000.
      * String values will be atempted to be converted into a float, if that fails, 0 will be returned.
      * Everything else will return 0.
      */
     public float getFloat(){
         try{
             switch(atomicfields.getType()){
                 case STRING     : return Float.parseFloat(atomicfields.getStr_value());
                 case INT        : return (float)atomicfields.getInt_value().floatValue();
                 case LONG       : return (float)atomicfields.getInt_value().floatValue();
                 case FLOAT      : return (float)atomicfields.getFloat_value().floatValue();
                 case DOUBLE     : return (float)atomicfields.getFloat_value().floatValue();
                 case BOOLEAN    : return (float)atomicfields.getInt_value().floatValue();
                 case TIMESTAMP  : return (float)(atomicfields.getInt_value()/1000);
             }
         }catch(Exception e){}
         return 0.0f;
     }

     /**
      * Attempts to return a double representation of this value.
      * Numerical values will be returned directly.
      * Boolean values will be returned as 1 or 0.
      * Timestamp values will be returned as a unix timestamp representation divided by 1000.
      * String values will be atempted to be converted into a float, if that fails, 0 will be returned.
      * Everything else will return 0.
      */
     public double getDouble(){
         try{
             switch(atomicfields.getType()){
                 case STRING     : return Double.parseDouble(atomicfields.getStr_value());
                 case INT        : return (double)atomicfields.getInt_value();
                 case LONG       : return (double)atomicfields.getInt_value();
                 case FLOAT      : return (double)atomicfields.getFloat_value();
                 case DOUBLE     : return (double)atomicfields.getFloat_value();
                 case BOOLEAN    : return (double)atomicfields.getInt_value();
                 case TIMESTAMP  : return (double)(atomicfields.getInt_value());
             }
         }catch(Exception e){}
         return 0.0;
     }

     /**
      * Returns a boolean representation of this value.
      * Boolean values will be returned directly.
      * Integer values equalling 1 will be returned as true.
      * String values equalling true,1,t,yes,on will be returned as true (case insensitive).
      * Everything else will be returned as false.
      *
      * @return a boolean representation of this value.
      */
     public boolean getBoolean(){
         try{
             switch(atomicfields.getType()){
                 case STRING     : if(atomicfields.getStr_value().toLowerCase().trim().equals("true"))return true;
                                   if(atomicfields.getStr_value().trim().equals("1"))return true;
                                   if(atomicfields.getStr_value().toLowerCase().trim().equals("t"))return true;
                                   if(atomicfields.getStr_value().toLowerCase().trim().equals("yes"))return true;
                                   if(atomicfields.getStr_value().toLowerCase().trim().equals("on"))return true;
                 case INT        : if(atomicfields.getInt_value()==1)return true;
                 case LONG       : if(atomicfields.getInt_value()==1)return true;
                 case FLOAT      : if(atomicfields.getFloat_value()==1.0f)return true;
                 case DOUBLE     : if(atomicfields.getFloat_value()==1.0)return true;
                 case BOOLEAN    : if(atomicfields.getInt_value()==1)return true;
             }
         }catch(Exception e){}
         return false;
     }

     /**
      * Attempts to return this value as a Date object.
      * For non date numerical values, the following rules will be used for conversion:
      * int and float will be multiplied by 1000 and used as a unix timestamp.
      * long and double will be used directly as a unix timestamp.
      * Any other type will result in a Date object initialized with 0.
      *
      * @return a Date object representation of this value
      */
     public Date getTimestamp(){
         try{
             switch(atomicfields.getType()){
                 case INT        : return new Date(atomicfields.getInt_value()*1000l);
                 case LONG       : return new Date(atomicfields.getInt_value());
                 case FLOAT      : return new Date((long)(atomicfields.getFloat_value()*1000l));
                 case DOUBLE     : return new Date((long)atomicfields.getFloat_value().longValue());
                 case TIMESTAMP  : return new Date(atomicfields.getInt_value());
             }
         }catch(Exception e){}
         return new Date(0);
     }

     public AtomicByteArray getBinary(){
         switch(atomicfields.getType()){
             case BINARY        : return atomicfields.getBinary();
         }
         return null;
     }

     /**
      * Attempts to write this Value to the given DataOutputStream.
      * The Value will be written as a byte signifying the type of the Value, followed by the actual Value in the native format used by DataOutput.
      * The exception to this is the string which is written as an integer followed by each character as a single char.
      * 
      * @param out the DataOutputStream the Value should be written to
      */
     public void writeToStream(DataOutputStream out) throws IOException{
         out.writeByte(atomicfields.getType());
         switch(atomicfields.getType()){
             case STRING :   out.writeInt(atomicfields.getStr_value().length());
                             for(int i=0;i<atomicfields.getStr_value().length();i++){
                                 out.writeChar(atomicfields.getStr_value().charAt(i));
                             }
                         break;
             case INT    :   out.writeInt((int)atomicfields.getInt_value().intValue());
                         break;
             case LONG   :   out.writeLong(atomicfields.getInt_value());
                         break;
             case FLOAT  :   out.writeFloat((float)atomicfields.getFloat_value().intValue());
                         break;
             case DOUBLE :   out.writeDouble(atomicfields.getFloat_value());
                         break;
             case BOOLEAN:   out.writeBoolean(atomicfields.getInt_value()==1);
                         break;
             case TIMESTAMP: out.writeLong(atomicfields.getInt_value());
                         break;
             case BINARY : out.writeInt(atomicfields.getBinary().length());
                         for (int i=0; i<atomicfields.getBinary().length(); i++) 
                             out.write(atomicfields.getBinary().get(i));
                         break;
         }
     }
     
     /**
       * Attempts to write this Value to the given DataOutputStream.
       * The Value will be written as a byte signifying the type of the Value, followed by the actual Value in the native format used by DataOutput.
       * The exception to this is the string which is written as an integer followed by each character as a single char.
       * 
       * @param out the DataOutputStream the Value should be written to
       */
      public void writeToFile(TransactionalFile out) throws IOException{
          out.writeByte(atomicfields.getType());
          switch(atomicfields.getType()){
              case STRING :   out.writeInt(atomicfields.getStr_value().length());
                              for(int i=0;i<atomicfields.getStr_value().length();i++){
                                  out.writeChar(atomicfields.getStr_value().charAt(i));
                              }
                          break;
              case INT    :   out.writeInt((int)atomicfields.getInt_value().intValue());
                          break;
              case LONG   :   out.writeLong(atomicfields.getInt_value());
                          break;
              case FLOAT  :   out.writeFloat((float)atomicfields.getFloat_value().intValue());
                          break;
              case DOUBLE :   out.writeDouble(atomicfields.getFloat_value());
                          break;
              case BOOLEAN:   out.writeBoolean(atomicfields.getInt_value()==1);
                          break;
              case TIMESTAMP: out.writeLong(atomicfields.getInt_value());
                          break;
              case BINARY : out.writeInt(atomicfields.getBinary().length());
                            for (int i=0; i<atomicfields.getBinary().length(); i++) 
                                out.writeByte(atomicfields.getBinary().get(i));
                            
          }
      }

     /**
      * Attempts to read a new Value from the given DataInputStream.
      * The Value should be in the format delivered by writeToStream.
      *
      * @param in the DataInputStream the Value should be read from
      * @return the Value read from the stream
      */
   /*  public static ValueTransactional readFromStream(DataInputStream in) throws IOException{
         byte type=in.readByte();
         switch(type){
             case STRING :   int size=in.readInt();
                             StringBuffer buf=new StringBuffer();
                             for(int i=0;i<size;i++){
                                 buf.append(in.readChar());
                             }
                             return new ValueTransactional(buf.toString());
             case INT    :   return new ValueTransactional(in.readInt());
             case LONG   :   return new ValueTransactional(in.readLong());
             case FLOAT  :   return new ValueTransactional(in.readFloat());
             case DOUBLE :   return new ValueTransactional(in.readDouble());
             case BOOLEAN:   return new ValueTransactional(in.readBoolean());
             case TIMESTAMP: return new ValueTransactional(new Date(in.readLong()));
             case BINARY : int length=in.readInt();
                           byte[] abuf=new byte[length];
                           int read=0;
                           while(read<length){
                               read+=in.read(abuf,read,length-read);
                           }
                           return new ValueTransactional(abuf);

         }
         return new ValueTransactional();
     }*/
     
        public static ValueTransactional readFromStream(TransactionalFile in) throws IOException{
         byte type=in.readByte();
         switch(type){
             case STRING :   int size=in.readInt();
                             StringBuffer buf=new StringBuffer();
                             for(int i=0;i<size;i++){
                                 buf.append(in.readChar());
                             }
                             return new ValueTransactional(buf.toString());
             case INT    :   return new ValueTransactional(in.readInt());
             case LONG   :   return new ValueTransactional(in.readLong());
             case FLOAT  :   return new ValueTransactional(in.readFloat());
             case DOUBLE :   return new ValueTransactional(in.readDouble());
             case BOOLEAN:   return new ValueTransactional(in.readBoolean());
             case TIMESTAMP: return new ValueTransactional(new Date(in.readLong()));
             case BINARY : int length=in.readInt();
                           byte[] abuf=new byte[length];
                           int read=0;
                           while(read<length){
                               read+=in.read(abuf);//,read,length-read);
                           }
                           return new ValueTransactional(abuf);

         }
         return new ValueTransactional();
     }

    /**
     * Initializes this Value with the given String.
     * 
     * @param val the value this Value object should represent
     */
     public ValueTransactional(String val){
         this();
         atomicfields.setStr_value(val);
         atomicfields.setType((byte)STRING);
     }
     
     public ValueTransactional(byte[] val){
         this();
         atomicfields.setBinary(new AtomicByteArray(Byte.class, val.length));
         for (int i=0; i<val.length; i++) 
                 atomicfields.getBinary().set(i, val[i]);
         
         atomicfields.setType((byte)BINARY);
      }

     /**
      * Initializes this Value with the given Date.
      * The Dates internal long value delivered by the getTime() method will be used.
      * 
      * @param val the value this Value object should represent
      */
     public ValueTransactional(Date val){
         atomicfields.setInt_value(val.getTime());
         atomicfields.setType((byte)TIMESTAMP);
     }

     /**
      * Initializes this Value as null.
      */
     public ValueTransactional(){
         atomicfields = factory.create();
         atomicfields.setStr_value(null);
         atomicfields.setInt_value(Long.valueOf(0));
         atomicfields.setFloat_value(0.0);
         atomicfields.setBinary(new AtomicByteArray(Byte.class, NULL));
         atomicfields.setType((byte)NULL);
     }

     /**
      * Initializes this Value with the given int.
      * 
      * @param val the value this Value object should represent
      */
     public ValueTransactional(int val){
         this();
         atomicfields.setInt_value(Long.valueOf(val));
         atomicfields.setType((byte)INT);
     }

     /**
      * Initializes this Value with the given long.
      * 
      * @param val the value this Value object should represent
      */
     public ValueTransactional(long val){
         this();
         atomicfields.setInt_value(val);
         atomicfields.setType((byte)LONG);
     }

     /**
      * Initializes this Value with the given float.
      * 
      * @param val the value this Value object should represent
      */
     public ValueTransactional(float val){
         this();
         atomicfields.setFloat_value(Double.valueOf(val));
         atomicfields.setType((byte)FLOAT);
     }

     /**
      * Initializes this Value with the given double.
      * 
      * @param val the value this Value object should represent
      */
     public ValueTransactional(double val){
         this();
         atomicfields.setFloat_value(val);
         atomicfields.setType((byte)DOUBLE);
     }

     /**
      * Initializes this Value with the given boolean.
      * 
      * @param val the value this Value object should represent
      */
     public ValueTransactional(boolean val){
         this();
         if(val){
             atomicfields.setInt_value(Long.valueOf(1));
         }else{
             atomicfields.setInt_value(Long.valueOf(0));
         }
         atomicfields.setType((byte)BOOLEAN);
     }  
     
     

}
