/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dstm2.benchmark;

import TransactionalIO.benchmarks.benchmark;
import TransactionalIO.core.TransactionalFile;
import dstm2.SpecialTransactionalFile;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author navid
 */
public class PureIOdstm2version extends CustomBenchmark {

    
    
    int count = 0;
    @Override
    protected void init() {
         int index = 97;
            
            for (int i = 0; i < 26; i++) { 
                try {
                    benchmark.m.put(String.valueOf((char) (index + i) +"special"), new SpecialTransactionalFile("/scratch/TransactionalIO/PureIOBenchmarkFiles/" + String.valueOf((char) (index + i)) + ".text", "rw"));
                    //System.out.println(String.valueOf((char) (index + i) +"special"));
                } catch (FileNotFoundException ex) {
                    Logger.getLogger(PureIOdstm2version.class.getName()).log(Level.SEVERE, null, ex);
                }
                    count++;
            }   
    }

    
    protected void execute(Vector arguments) {
            char[] holder = (char[]) arguments.get(0);
            int i = ((Integer) (arguments.get(1))).intValue();
            byte[] towrite = (byte[]) arguments.get(2);
        try {
            
             // ((TransactionalFile) (benchmark.m.get(String.valueOf(holder,0,i).toLowerCase().substring(0, 1)))).write(towrite);         
            //System.out.println(((SpecialTransactionalFile) (benchmark.m.get(String.valueOf(holder,0,i).toLowerCase().substring(0, 1)+"special"))));
             ((SpecialTransactionalFile) (benchmark.m.get(String.valueOf(holder,0,i).toLowerCase().substring(0, 1)+"special"))).write(towrite);         
   //           
        } catch (NullPointerException e){
             System.out.println(String.valueOf(holder,0,i).toLowerCase().substring(0, 1)+"special");
             
        
        } catch (IOException ex) {
            Logger.getLogger(PureIO.class.getName()).log(Level.SEVERE, null, ex);
        }
        
    }

    
    protected void printResults() {
        
    }

}
