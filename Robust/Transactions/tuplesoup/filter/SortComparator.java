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

public class SortComparator implements Comparator<Row>{
    private List<SortRule> rules;
    
    public SortComparator(List<SortRule> rules){
        this.rules=rules;
    }
    
    private int compare(int rulenum,Row rowa,Row rowb){
        if(rules.size()<=rulenum)return 0;
        SortRule rule=rules.get(rulenum);
        Value a=rowa.get(rule.getKey());
        Value b=rowb.get(rule.getKey());
        int result=a.compareTo(b);
        // TODO: add direction switcher here
        if(result==0){
            rulenum++;
            return compare(rulenum,rowa,rowb);
        }else{
             if(rule.getDirection()==SortRule.DESC){
                 if(result==-1){
                     result=1;
                 }else if(result==1)result=-1;
             }
             return result;
        }
    }
    
    public int compare(Row rowa,Row rowb){
        return compare(0,rowa,rowb);
    }
}