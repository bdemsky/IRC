/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.solidosystems.tuplesoup.core;

import TransactionalIO.core.TransactionalFile;
import dstm2.AtomicSuperClass;
import dstm2.atomic;
import dstm2.Thread;
import dstm2.factory.Factory;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 *
 * @author navid
 */
public class TableIndexEntryTransactional implements AtomicSuperClass, Comparable<TableIndexEntryTransactional>{
     
    static Factory<TableIndexEntryTSInf> factory = Thread.makeFactory(TableIndexEntryTSInf.class);
    
    TableIndexEntryTSInf atomicfields;
    public @atomic interface TableIndexEntryTSInf{
        String getId();
        Integer getLocation();
        Long getPosition();
        Integer getSize();
        Integer getRowsize();
        
        void setId(String val);
        void setLocation(Integer val);
        void setPosition(Long val);
        void setSize(Integer val);
        void setRowsize(Integer val);
    }
       
         
    public TableIndexEntryTransactional(String id,int rowsize,int location,long position){
        atomicfields = factory.create();
        
        this.atomicfields.setId(id);
        this.atomicfields.setLocation(location);
        this.atomicfields.setPosition(position);
        this.atomicfields.setRowsize(rowsize);
        this.atomicfields.setSize(-1);
    }
    public String getId(){
        return atomicfields.getId();
    }
    public void setPosition(long position){
        this.atomicfields.setPosition(position);
    }
    public long getPosition(){
        return atomicfields.getPosition();
    }
    public void setLocation(int location){
        this.atomicfields.setLocation(location);
    }
    public int getLocation(){
        return atomicfields.getLocation();
    }
    
    public int getRowSize(){
        return atomicfields.getRowsize().intValue();
    }
    
    public int compareTo(TableIndexEntryTransactional obj) throws ClassCastException{
        TableIndexEntryTransactional ent=(TableIndexEntryTransactional)obj;
        if(atomicfields.getPosition()<ent.atomicfields.getPosition()) return -1;
        if(atomicfields.getPosition()==ent.atomicfields.getPosition()) return 0;
        return 1;
    }
    
    public boolean equals(Object obj){
        try{
            TableIndexEntryTransactional ent=(TableIndexEntryTransactional)obj;
            if(ent.atomicfields.getLocation()==atomicfields.getLocation()){
                if(ent.atomicfields.getPosition()==atomicfields.getPosition()){
                    if(ent.atomicfields.getId().equals(atomicfields.getId())){
                        return true;
                    }
                }
            }
        }catch(ClassCastException e){}
        return false;
    }
    
    public int getSize(){
        if(atomicfields.getSize()<0) calcSize();
        return atomicfields.getSize().intValue();
    }
    public void setSize(int size){
        this.atomicfields.setSize(size);
    }
    private void calcSize(){
        try{
            ByteArrayOutputStream bout=new ByteArrayOutputStream();
            DataOutputStream dout=new DataOutputStream(bout);
            dout.writeInt(atomicfields.getId().hashCode());
            dout.writeShort(atomicfields.getId().length());
            dout.writeChars(atomicfields.getId());
            dout.writeInt(atomicfields.getRowsize().intValue());
            dout.writeByte(atomicfields.getLocation());
            dout.writeLong(atomicfields.getPosition());
            setSize(bout.size());
            dout.close();
            bout.close();
        }catch(Exception e){
            e.printStackTrace();
        }
    }
    
    protected void writeData(TransactionalFile out) throws IOException{
        long pre=out.getFilePointer();
        out.writeInt(atomicfields.getId().hashCode());
        out.writeShort(atomicfields.getId().length());
        out.writeChars(atomicfields.getId());
        out.writeInt(atomicfields.getRowsize().intValue());
        out.writeByte(atomicfields.getLocation());
        out.writeLong(atomicfields.getPosition());
        setSize((int)(out.getFilePointer()-pre));
    }
    protected void updateData(TransactionalFile out) throws IOException{
        long pre=out.getFilePointer();
        out.skipBytes(4+2+atomicfields.getId().length()*2);
        out.writeInt(atomicfields.getRowsize().intValue());
        out.writeByte(atomicfields.getLocation());
        out.writeLong(atomicfields.getPosition());
        setSize((int)(out.getFilePointer()-pre));
    }
    protected void writeData(DataOutputStream out) throws IOException{
        out.writeInt(atomicfields.getId().hashCode());
        out.writeShort(atomicfields.getId().length());
        out.writeChars(atomicfields.getId());
        out.writeInt(atomicfields.getRowsize().intValue());
        out.writeByte(atomicfields.getLocation());
        out.writeLong(atomicfields.getPosition());
    }
    protected static TableIndexEntryTransactional readData(TransactionalFile in) throws IOException{
        long pre=in.getFilePointer();
        in.readInt();
        //short num=in.readShort();
        int num=in.readShort();
        //System.out.println("num= " + num);
        StringBuilder buf=new StringBuilder(num);
        for(int i=0;i<num;i++){
            buf.append(in.readChar());
        }
        String id=buf.toString();
        int rowsize=in.readInt();
        int location=in.readByte();
        long position=in.readLong();
        TableIndexEntryTransactional tmp=new TableIndexEntryTransactional(id,rowsize,location,position);
        tmp.setSize((int)(in.getFilePointer()-pre));
        return tmp;
    }
    
    protected static TableIndexEntryTransactional readData(DataInputStream in) throws IOException{
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
        TableIndexEntryTransactional tmp=new TableIndexEntryTransactional(id,rowsize,location,position);
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
    protected static TableIndexEntryTransactional lookForData(String id,DataInputStream in) throws IOException{
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
        TableIndexEntryTransactional tmp=new TableIndexEntryTransactional(id,rowsize,location,position);
        return tmp;
    }
    protected static TableIndexEntryTransactional lookForData(String id,TransactionalFile in) throws IOException{
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
        TableIndexEntryTransactional tmp=new TableIndexEntryTransactional(id,rowsize,location,position);
        return tmp;
    }
}
