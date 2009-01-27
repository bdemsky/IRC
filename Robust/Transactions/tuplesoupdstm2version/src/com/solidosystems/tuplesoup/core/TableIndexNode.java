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

public class TableIndexNode{
    private TableIndexNode previous;
    private TableIndexEntry data;
    private TableIndexNode next;
    
    public TableIndexNode(){
        previous=null;
        data=null;
        next=null;
    }
    
    public TableIndexNode(TableIndexEntry entry){
        previous=null;
        data=entry;
        next=null;
    }
    public TableIndexNode(TableIndexNode prev,TableIndexEntry entry){
        previous=prev;
        data=entry;
        next=null;
    }
    public TableIndexNode(TableIndexNode prev,TableIndexEntry entry,TableIndexNode nex){
        previous=prev;
        data=entry;
        next=nex;
    }
    
    public TableIndexEntry getData(){
        return data;
    }
    public TableIndexNode getPrevious(){
        return previous;
    }
    public TableIndexNode getNext(){
        return next;
    }
    public void setNext(TableIndexNode node){
        next=node;
    }
    public void setPrevious(TableIndexNode node){
        previous=node;
    }
    public void setData(TableIndexEntry entry){
        data=entry;
    }
    public void remove(){
        if(previous!=null){
            previous.setNext(next);
        }
        if(next!=null){
            next.setPrevious(previous);
        }
    }
}