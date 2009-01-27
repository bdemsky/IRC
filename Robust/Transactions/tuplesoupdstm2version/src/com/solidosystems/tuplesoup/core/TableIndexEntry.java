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

import dstm2.atomic;
import java.io.*;

public class TableIndexEntry implements Comparable<TableIndexEntry>{
    public String id;
    public int location;
    public long position;
    private int size;
    private int rowsize;
    
  
    
    public TableIndexEntry(String id,int rowsize,int location,long position){
        this.id=id;
        this.location=location;
        this.position=position;
        this.rowsize=rowsize;
        size=-1;
    }
    public String getId(){
        return id;
    }
    public void setPosition(long position){
        this.position=position;
    }
    public long getPosition(){
        return position;
    }
    public void setLocation(int location){
        this.location=location;
    }
    public int getLocation(){
        return location;
    }
    
    public int getRowSize(){
        return rowsize;
    }
    
    public int compareTo(TableIndexEntry obj) throws ClassCastException{
        TableIndexEntry ent=(TableIndexEntry)obj;
        if(position<ent.position)return -1;
        if(position==ent.position)return 0;
        return 1;
    }
    
    public boolean equals(Object obj){
        try{
            TableIndexEntry ent=(TableIndexEntry)obj;
            if(ent.location==location){
                if(ent.position==position){
                    if(ent.id.equals(id)){
                        return true;
                    }
                }
            }
        }catch(ClassCastException e){}
        return false;
    }
    
    public int getSize(){
        if(size<0)calcSize();
        return size;
    }
    public void setSize(int size){
        this.size=size;
    }
    private void calcSize(){
        try{
            ByteArrayOutputStream bout=new ByteArrayOutputStream();
            DataOutputStream dout=new DataOutputStream(bout);
            dout.writeInt(id.hashCode());
            dout.writeShort(id.length());
            dout.writeChars(id);
            dout.writeInt(rowsize);
            dout.writeByte(location);
            dout.writeLong(position);
            setSize(bout.size());
            dout.close();
            bout.close();
        }catch(Exception e){
            e.printStackTrace();
        }
    }
    
    protected void writeData(RandomAccessFile out) throws IOException{
        long pre=out.getFilePointer();
        out.writeInt(id.hashCode());
        out.writeShort(id.length());
        out.writeChars(id);
        out.writeInt(rowsize);
        out.writeByte(location);
        out.writeLong(position);
        setSize((int)(out.getFilePointer()-pre));
    }
    protected void updateData(RandomAccessFile out) throws IOException{
        long pre=out.getFilePointer();
        out.skipBytes(4+2+id.length()*2);
        out.writeInt(rowsize);
        out.writeByte(location);
        out.writeLong(position);
        setSize((int)(out.getFilePointer()-pre));
    }
    protected void writeData(DataOutputStream out) throws IOException{
        out.writeInt(id.hashCode());
        out.writeShort(id.length());
        out.writeChars(id);
        out.writeInt(rowsize);
        out.writeByte(location);
        out.writeLong(position);
    }
    protected static TableIndexEntry readData(RandomAccessFile in) throws IOException{
        long pre=in.getFilePointer();
        in.readInt();
        int num=in.readShort();
        StringBuilder buf=new StringBuilder(num);
        for(int i=0;i<num;i++){
            buf.append(in.readChar());
        }
        String id=buf.toString();
        int rowsize=in.readInt();
        int location=in.readByte();
        long position=in.readLong();
        TableIndexEntry tmp=new TableIndexEntry(id,rowsize,location,position);
        tmp.setSize((int)(in.getFilePointer()-pre));
        return tmp;
    }
    
    protected static TableIndexEntry readData(DataInputStream in) throws IOException{
        in.readInt();
        int num=in.readShort();
        System.out.println("num=444444444444444444444444 " + num);
        StringBuilder buf=new StringBuilder(num);
        for(int i=0;i<num;i++){
            buf.append(in.readChar());
        }
        String id=buf.toString();
        int rowsize=in.readInt();
        int location=in.readByte();
        long position=in.readLong();
        TableIndexEntry tmp=new TableIndexEntry(id,rowsize,location,position);
        return tmp;
    }
    
    protected static long scanForOffset(String id,DataInputStream in) throws IOException{
        long offset=0;
        int scanhash=id.hashCode();
        try{
            int datahash=in.readInt();
            while(scanhash!=datahash){
                int num=in.readShort();
                in.skipBytes(1+4+8+num*2);
                offset+=4+4+1+2+8+num*2;
                datahash=in.readInt();
            }
            return offset;
        }catch(EOFException e){}
        return -1;
    }
    protected static TableIndexEntry lookForData(String id,DataInputStream in) throws IOException{
        int scanhash=id.hashCode();
        int datahash=in.readInt();
        int num=in.readShort();
        if(scanhash!=datahash){
            in.skipBytes(4+1+8+num*2);
            return null;
        }
        StringBuilder buf=new StringBuilder(num);
        for(int i=0;i<num;i++){
            buf.append(in.readChar());
        }
        String readid=buf.toString();
        if(!readid.equals(id)){
            in.skipBytes(4+1+8);
            return null;
        }
        int rowsize=in.readInt();
        int location=in.readByte();
        long position=in.readLong();
        TableIndexEntry tmp=new TableIndexEntry(id,rowsize,location,position);
        return tmp;
    }
    protected static TableIndexEntry lookForData(String id,RandomAccessFile in) throws IOException{
        int scanhash=id.hashCode();
        int datahash=in.readInt();
        int num=in.readShort();
        if(scanhash!=datahash){
            in.skipBytes(4+1+8+num*2);
            return null;
        }
        StringBuilder buf=new StringBuilder(num);
        for(int i=0;i<num;i++){
            buf.append(in.readChar());
        }
        String readid=buf.toString();
        if(!readid.equals(id)){
            in.skipBytes(4+1+8);
            return null;
        }
        int rowsize=in.readInt();
        int location=in.readByte();
        long position=in.readLong();
        TableIndexEntry tmp=new TableIndexEntry(id,rowsize,location,position);
        return tmp;
    }
}