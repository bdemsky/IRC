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
 
package com.solidosystems.tuplesoup.test;

import java.util.*;

public class BasicTest{
    public List<String>errors=new ArrayList<String>();
    private int lastlength=0;
    
    public void err(String str){
        while(lastlength<40){
            System.out.print(" ");
            lastlength++;
        }
        outbr(" ERR");
        outbr(" ! "+str);
        errors.add(str);
    }
    
    public void printErrorSummary(){
        outbr("");
        if(errors.size()==0){
            outbr("All tests passed!");
        }else if(errors.size()==1){
            outbr("1 test failed!");
        }else{
            outbr(errors.size()+" tests failed!");
        }
    }
    
    public static void die(String reason){
         System.out.println("ERR");
         System.out.println(" ! "+reason);
         System.exit(0);
    }
    
    public void ok(){
        while(lastlength<40){
            System.out.print(" ");
            lastlength++;
        }
        outbr(" OK");
    }

    public void outbr(String str){
        outbr(0,str);
    }
    
    public void out(String str){
        out(0,str);
    }
    
    public void outbr(int level, String str){
        switch(level){
            case 0:System.out.print("");
                break;
            case 1:System.out.print(" + ");
                break;
            case 2:System.out.print("   * ");
                break;
            case 3:System.out.print("     - ");
                break;
            case 4:System.out.print("       + ");
                break;
        }
        System.out.println(str);
    }
    
    public void out(int level, String str){
        lastlength=0;
        switch(level){
            case 0: System.out.print("");
                break;
            case 1: System.out.print(" + ");
                    lastlength+=3;
                break;
            case 2: System.out.print("   * ");
                    lastlength+=5;
                break;
            case 3: System.out.print("     - ");
                    lastlength+=7;
                break;
        }
        System.out.print(str);
        lastlength+=str.length();
    }
}