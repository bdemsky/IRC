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
 
 import java.util.*;
 import java.io.*;
 
 /**
  * The Value class holds a single data value.
  * Current size estimate without string data: 8+4+4+8+8 = 32 bytes pr value in mem
  */
 public class Value{
     public final static int NULL=0;
     public final static int STRING=1;
     public final static int INT=2;
     public final static int LONG=3;
     public final static int FLOAT=4;
     public final static int DOUBLE=5;
     public final static int BOOLEAN=6;
     public final static int TIMESTAMP=7;
     public final static int BINARY=8;

     private byte type=NULL; // The type of the value being held
     private String str_value=null;
     private long int_value=0;
     private double float_value=0.0;
     private byte[] binary=null;

     /**
      * Returns the numerical type id for this value.
      */
     public int getType(){
         return type;
     }
     
     /**
      * Returns the name this value's type.
      */
     public String getTypeName(){
         switch(type){
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
         switch(type){
             case STRING     : hash+=str_value.hashCode();
             case INT        : hash+=(int)int_value;
             case LONG       : hash+=(int)int_value;
             case FLOAT      : hash+=(int)float_value;
             case DOUBLE     : hash+=(int)float_value;
             case BOOLEAN    : hash+=(int)int_value;
             case TIMESTAMP  : hash+=(int)int_value;
             case BINARY     : hash+=binary.hashCode();
         }
         return hash;
     }

     /**
      * Returns true only if this Value has specifically been set to null.
      *
      * @return true if the data being held is null, false otherwise
      */
     public boolean isNull(){
         return type==NULL;
     }

     /**
      * Returns -1, 0 or 1 if this value is smaller, equal or larger than the value given as a parameter.
      */
     public int compareTo(Value value){
         if(type==STRING){
             return str_value.compareTo(value.getString());
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
     public boolean lessThan(Value value){
         switch(type){
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
     public boolean greaterThan(Value value){
         switch(type){
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
     public boolean startsWith(Value value){
         return getString().startsWith(value.getString());
     }

     /**
      * Returns true if the string representation of this value ends with the string representation of the value given as parameter.
      */
     public boolean endsWith(Value value){
         return getString().endsWith(value.getString());
     }

     /**
      * Returns true if the string representation of this value contains the string representation of the value given as parameter.
      */
     public boolean contains(Value value){
         return getString().indexOf(value.getString())>-1;
     }

     /**
      * Returns true if the contents of this value equals the contents of the value given as parameter.
      */
     public boolean equals(Object obj){
         try{
             Value val=(Value)obj;
             if(val.type==type){
                 switch(type){
                     case NULL       : return true;
                     case STRING     : return str_value.equals(val.str_value);
                     case INT        : return int_value==val.int_value;
                     case LONG       : return int_value==val.int_value;
                     case FLOAT      : return float_value==val.float_value;
                     case DOUBLE     : return float_value==val.float_value;
                     case BOOLEAN    : return int_value==val.int_value;
                     case TIMESTAMP  : return int_value==val.int_value;
                     case BINARY     : if(binary.length==val.binary.length){
                                          for(int i=0;i<binary.length;i++){
                                              if(binary[i]!=val.binary[i])return false;
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
         switch(type){
             case STRING     : return "<value name=\""+key+"\" type=\"string\">"+str_value+"</value>";
             case INT        : return "<value name=\""+key+"\"  type=\"int\">"+int_value+"</value>";
             case LONG       : return "<value name=\""+key+"\"  type=\"long\">"+int_value+"</value>";
             case FLOAT      : return "<value name=\""+key+"\"  type=\"float\">"+float_value+"</value>";
             case DOUBLE     : return "<value name=\""+key+"\"  type=\"double\">"+float_value+"</value>";
             case BOOLEAN    : if(int_value==1){
                                     return "<value name=\""+key+"\"  type=\"boolean\">TRUE</value>";
                               }else{
                                     return "<value name=\""+key+"\"  type=\"boolean\">FALSE</value>";
                               }
             case TIMESTAMP  : return "<value name=\""+key+"\"  type=\"timestamp\">"+new Date(int_value).toString()+"</value>";
             case BINARY     : return "<value name=\""+key+"\" type=\"binary\">"+getString()+"</value>";
         }
         return "<value name=\""+key+"\"  type=\"null\"></value>";
     }
     
     /**
       * Returns this value as an xml tag.
       * The following string is an example of the int value 1234 &lt;value type="int"&gt;1234&lt;/value&gt;
       */
     public String toBasicXMLString(){
          switch(type){
              case STRING     : return "<value type=\"string\">"+str_value+"</value>";
              case INT        : return "<value type=\"int\">"+int_value+"</value>";
              case LONG       : return "<value type=\"long\">"+int_value+"</value>";
              case FLOAT      : return "<value type=\"float\">"+float_value+"</value>";
              case DOUBLE     : return "<value type=\"double\">"+float_value+"</value>";
              case BOOLEAN    : if(int_value==1){
                                      return "<value type=\"boolean\">TRUE</value>";
                                }else{
                                      return "<value type=\"boolean\">FALSE</value>";
                                }
              case TIMESTAMP  : return "<value type=\"timestamp\">"+new Date(int_value).toString()+"</value>";
              case BINARY     : return "<value type=\"binary\">"+getString()+"</value>";
          }
          return "<value type=\"null\"></value>";
      }

     /**
      * Returns a string representation of this value
      */
     public String getString(){
         switch(type){
             case STRING     : return str_value;
             case INT        : return ""+int_value;
             case LONG       : return ""+int_value;
             case FLOAT      : return ""+float_value;
             case DOUBLE     : return ""+float_value;
             case BOOLEAN    : if(int_value==1){
                                     return "TRUE";
                               }else{
                                     return "FALSE";
                               }
             case TIMESTAMP  : return new Date(int_value).toString();
             case BINARY     : StringBuffer buf=new StringBuffer();
                               for(int i=0;i<binary.length;i++){
                                  byte b=binary[i];
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
             switch(type){
                 case STRING     : return Integer.parseInt(str_value);
                 case INT        : return (int)int_value;
                 case LONG       : return (int)int_value;
                 case FLOAT      : return (int)float_value;
                 case DOUBLE     : return (int)float_value;
                 case BOOLEAN    : return (int)int_value;
                 case TIMESTAMP  : return (int)(int_value/1000);
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
             switch(type){
                 case STRING     : return Long.parseLong(str_value);
                 case INT        : return int_value;
                 case LONG       : return int_value;
                 case FLOAT      : return (long)float_value;
                 case DOUBLE     : return (long)float_value;
                 case BOOLEAN    : return int_value;
                 case TIMESTAMP  : return int_value;
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
             switch(type){
                 case STRING     : return Float.parseFloat(str_value);
                 case INT        : return (float)int_value;
                 case LONG       : return (float)int_value;
                 case FLOAT      : return (float)float_value;
                 case DOUBLE     : return (float)float_value;
                 case BOOLEAN    : return (float)int_value;
                 case TIMESTAMP  : return (float)(int_value/1000);
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
             switch(type){
                 case STRING     : return Double.parseDouble(str_value);
                 case INT        : return (double)int_value;
                 case LONG       : return (double)int_value;
                 case FLOAT      : return (double)float_value;
                 case DOUBLE     : return (double)float_value;
                 case BOOLEAN    : return (double)int_value;
                 case TIMESTAMP  : return (double)(int_value);
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
             switch(type){
                 case STRING     : if(str_value.toLowerCase().trim().equals("true"))return true;
                                   if(str_value.trim().equals("1"))return true;
                                   if(str_value.toLowerCase().trim().equals("t"))return true;
                                   if(str_value.toLowerCase().trim().equals("yes"))return true;
                                   if(str_value.toLowerCase().trim().equals("on"))return true;
                 case INT        : if(int_value==1)return true;
                 case LONG       : if(int_value==1)return true;
                 case FLOAT      : if(float_value==1.0f)return true;
                 case DOUBLE     : if(float_value==1.0)return true;
                 case BOOLEAN    : if(int_value==1)return true;
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
             switch(type){
                 case INT        : return new Date(int_value*1000l);
                 case LONG       : return new Date(int_value);
                 case FLOAT      : return new Date((long)(float_value*1000l));
                 case DOUBLE     : return new Date((long)float_value);
                 case TIMESTAMP  : return new Date(int_value);
             }
         }catch(Exception e){}
         return new Date(0);
     }

     public byte[] getBinary(){
         switch(type){
             case BINARY        : return binary;
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
         out.writeByte(type);
         switch(type){
             case STRING :   out.writeInt(str_value.length());
                             for(int i=0;i<str_value.length();i++){
                                 out.writeChar(str_value.charAt(i));
                             }
                         break;
             case INT    :   out.writeInt((int)int_value);
                         break;
             case LONG   :   out.writeLong(int_value);
                         break;
             case FLOAT  :   out.writeFloat((float)float_value);
                         break;
             case DOUBLE :   out.writeDouble(float_value);
                         break;
             case BOOLEAN:   out.writeBoolean(int_value==1);
                         break;
             case TIMESTAMP: out.writeLong(int_value);
                         break;
             case BINARY : out.writeInt(binary.length);
                           out.write(binary,0,binary.length);
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
      public void writeToFile(DataOutput out) throws IOException{
          out.writeByte(type);
          switch(type){
              case STRING :   out.writeInt(str_value.length());
                              for(int i=0;i<str_value.length();i++){
                                  out.writeChar(str_value.charAt(i));
                              }
                          break;
              case INT    :   out.writeInt((int)int_value);
                          break;
              case LONG   :   out.writeLong(int_value);
                          break;
              case FLOAT  :   out.writeFloat((float)float_value);
                          break;
              case DOUBLE :   out.writeDouble(float_value);
                          break;
              case BOOLEAN:   out.writeBoolean(int_value==1);
                          break;
              case TIMESTAMP: out.writeLong(int_value);
                          break;
              case BINARY : out.writeInt(binary.length);
                            out.write(binary,0,binary.length);
          }
      }

     /**
      * Attempts to read a new Value from the given DataInputStream.
      * The Value should be in the format delivered by writeToStream.
      *
      * @param in the DataInputStream the Value should be read from
      * @return the Value read from the stream
      */
     public static Value readFromStream(DataInputStream in) throws IOException{
         byte type=in.readByte();
         switch(type){
             case STRING :   int size=in.readInt();
                             StringBuffer buf=new StringBuffer();
                             for(int i=0;i<size;i++){
                                 buf.append(in.readChar());
                             }
                             return new Value(buf.toString());
             case INT    :   return new Value(in.readInt());
             case LONG   :   return new Value(in.readLong());
             case FLOAT  :   return new Value(in.readFloat());
             case DOUBLE :   return new Value(in.readDouble());
             case BOOLEAN:   return new Value(in.readBoolean());
             case TIMESTAMP: return new Value(new Date(in.readLong()));
             case BINARY : int length=in.readInt();
                           byte[] abuf=new byte[length];
                           int read=0;
                           while(read<length){
                               read+=in.read(abuf,read,length-read);
                           }
                           return new Value(abuf);

         }
         return new Value();
     }

    /**
     * Initializes this Value with the given String.
     * 
     * @param val the value this Value object should represent
     */
     public Value(String val){
         str_value=val;
         type=STRING;
     }
     
     public Value(byte[] val){
          binary=val;
          type=BINARY;
      }

     /**
      * Initializes this Value with the given Date.
      * The Dates internal long value delivered by the getTime() method will be used.
      * 
      * @param val the value this Value object should represent
      */
     public Value(Date val){
         int_value=val.getTime();
         type=TIMESTAMP;
     }

     /**
      * Initializes this Value as null.
      */
     public Value(){
         type=NULL;
     }

     /**
      * Initializes this Value with the given int.
      * 
      * @param val the value this Value object should represent
      */
     public Value(int val){
         int_value=val;
         type=INT;
     }

     /**
      * Initializes this Value with the given long.
      * 
      * @param val the value this Value object should represent
      */
     public Value(long val){
         int_value=val;
         type=LONG;
     }

     /**
      * Initializes this Value with the given float.
      * 
      * @param val the value this Value object should represent
      */
     public Value(float val){
         float_value=val;
         type=FLOAT;
     }

     /**
      * Initializes this Value with the given double.
      * 
      * @param val the value this Value object should represent
      */
     public Value(double val){
         float_value=val;
         type=DOUBLE;
     }

     /**
      * Initializes this Value with the given boolean.
      * 
      * @param val the value this Value object should represent
      */
     public Value(boolean val){
         if(val){
             int_value=1;
         }else{
             int_value=0;
         }
         type=BOOLEAN;
     }  
 }