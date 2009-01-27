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

public class RowMatcherTransactional{
    public final static int LESSTHAN=0;
    public final static int EQUALS=1;
    public final static int GREATERTHAN=2;
    public final static int STARTSWITH=3;
    public final static int ENDSWITH=4;
    public final static int CONTAINS=5;
    public final static int ISNULL=6;
    
    public final static int NOT=7;
    public final static int OR=8;
    public final static int AND=9;
    public final static int XOR=10;
    
    private String key=null;
    private ValueTransactional value=null;
    private int type=-1;
    
    private RowMatcherTransactional match1=null;
    private RowMatcherTransactional match2=null;
    
    public RowMatcherTransactional(String key,int type,ValueTransactional value){
        this.key=key;
        this.type=type;
        this.value=value;
    }
    
    public RowMatcherTransactional(String key,int type){
        this.key=key;
        this.type=type;
    }
    
    public RowMatcherTransactional(RowMatcherTransactional match1,int type,RowMatcherTransactional match2){
        this.match1=match1;
        this.type=type;
        this.match2=match2;
    }
    
    public RowMatcherTransactional(int type,RowMatcherTransactional match1){
        this.match1=match1;
        this.type=type;
    }

    /**
     * This method needs to be seriously optimized... especially the XOR method
     */
    public boolean matches(RowTransactional row){
        if(value!=null){
            ValueTransactional compare=row.get(key);
            switch(type){
                case LESSTHAN   : return compare.lessThan(value);
                case EQUALS     : return compare.equals(value);
                case GREATERTHAN: return compare.greaterThan(value);
                case STARTSWITH : return compare.startsWith(value);
                case ENDSWITH   : return compare.endsWith(value);
                case CONTAINS   : return compare.contains(value);
            }
        }else if(type==ISNULL){
            ValueTransactional compare=row.get(key);
            return compare.isNull();
        }else if((type==AND)||(type==OR)||(type==XOR)){
            switch(type){
                case AND        : return match1.matches(row)&&match2.matches(row);
                case OR         : return match1.matches(row)||match2.matches(row);
                case XOR        : return (match1.matches(row)||match2.matches(row))&&(!(match1.matches(row)&&match2.matches(row)));
            }
        }else if(type==NOT){
            return !match1.matches(row);
        }
        return false;
    }
}