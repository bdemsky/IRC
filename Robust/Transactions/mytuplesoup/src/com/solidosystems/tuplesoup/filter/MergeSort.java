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
 
package com.solidosystems.tuplesoup.filter;

import com.solidosystems.tuplesoup.core.*;
import java.util.*;
import java.io.*;

public class MergeSort extends Sort{
    private int MEMLIMIT=65536;
    private int BUFFERCACHE=65536;
    private int BUFFERLIMIT=8192;
    
    private RowBuffer result;
    
    private int bufnum=0;
    
    private String filename;
    
    private SortComparator compare;
    
    
    public MergeSort(int cachesize){
        if(cachesize<32768){
            MEMLIMIT=32768;
            BUFFERCACHE=0;
            BUFFERLIMIT=0;
        }else if(cachesize<65536){
            MEMLIMIT=32768;
            BUFFERCACHE=cachesize-MEMLIMIT;
            BUFFERLIMIT=BUFFERCACHE/4;
        }else{
            MEMLIMIT=cachesize/2;
            BUFFERCACHE=MEMLIMIT;
            BUFFERLIMIT=BUFFERCACHE/16;
        }
        if(BUFFERLIMIT<8192)BUFFERLIMIT=8192;
    }
    
    public void mergeSort(RowBuffer result,RowBuffer a,RowBuffer b) throws IOException{
        a.prepare();
        b.prepare();
        Row rowb=null;
        Row rowa=null;
        while(a.hasNext()&&b.hasNext()){
            if(rowa==null)rowa=a.next();
            if(rowb==null)rowb=b.next();
            int cmp=compare.compare(rowa,rowb);
            if(cmp<=0){
                result.addRow(rowa);
                rowa=null;
            }else{
                result.addRow(rowb);
                rowb=null;
            }
        }
        if(rowa!=null)result.addRow(rowa);
        if(rowb!=null)result.addRow(rowb);
        while(a.hasNext()){
            result.addRow(a.next());
        }
        while(b.hasNext()){
            result.addRow(b.next());
        }
    }
    
    public void mergeSort(List<RowBuffer> buffers) throws IOException{
        while(buffers.size()>2){
            RowBuffer tmp=new RowBuffer(filename+"."+(bufnum++));
            tmp.setCacheSize(allocBuffer());
            // Grab two and sort to buf
            RowBuffer a=buffers.remove(0);
            RowBuffer b=buffers.remove(0);
            mergeSort(tmp,a,b);
            a.close();
            freeBuffer(a);
            b.close();
            freeBuffer(b);
            buffers.add(tmp);
        }
        if(buffers.size()==1){
            result.close();
            result=buffers.get(0);
            result.prepare();
            return;
        }
        if(buffers.size()==2){
            RowBuffer a=buffers.get(0);
            RowBuffer b=buffers.get(1);
            mergeSort(result,a,b);
            a.close();
            freeBuffer(a);
            b.close();
            freeBuffer(b);
            result.prepare();
            return;
        }
    }
    
    private int allocBuffer(){
        if(BUFFERCACHE>=BUFFERLIMIT){
            BUFFERCACHE-=BUFFERLIMIT;
            return BUFFERLIMIT;
        }
        int tmp=BUFFERCACHE;
        BUFFERCACHE=0;
        return tmp;
    }
    private void freeBuffer(RowBuffer buf){
        BUFFERCACHE+=buf.getCacheSize();
    }
    
    public void initialize(String filename,TupleStream source,List<SortRule> lst) throws IOException{
        this.filename=filename;
        compare=new SortComparator(lst);
        bufnum=0;
        result=new RowBuffer(filename+".result");
        result.setCacheSize(BUFFERLIMIT);
        List<RowBuffer> buffers=new ArrayList<RowBuffer>();
        int usage=0;
        List<Row> sortlst=new ArrayList<Row>();
        while(source.hasNext()){
            Row row=source.next();
            if(usage+row.getSize()>MEMLIMIT){
                RowBuffer buf=new RowBuffer(filename+"."+(bufnum++));
                buf.setCacheSize(allocBuffer());
                Collections.sort(sortlst,new SortComparator(lst));
                for(int i=0;i<sortlst.size();i++){
                    buf.addRow(sortlst.get(i));
                }
                buffers.add(buf);
                usage=0;
                sortlst=new ArrayList<Row>();
            }
            sortlst.add(row);
            usage+=row.getSize();
        }
        RowBuffer buf=new RowBuffer(filename+"."+(bufnum++));
        buf.setCacheSize(allocBuffer());
        Collections.sort(sortlst,new SortComparator(lst));
        for(int i=0;i<sortlst.size();i++){
            buf.addRow(sortlst.get(i));
        }
        buffers.add(buf);
        mergeSort(buffers);
    }
    public boolean hasNext() throws IOException{
        return result.hasNext();
    }
    public Row next() throws IOException{
        return result.next();
    }
}