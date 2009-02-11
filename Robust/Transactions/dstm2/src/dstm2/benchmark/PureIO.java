/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dstm2.benchmark;

import TransactionalIO.benchmarks.benchmark;
import TransactionalIO.core.TransactionalFile;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author navid
 */
public class PureIO extends CustomBenchmark {

    @Override
    protected void init() {
        
    }

    
    protected void execute(Vector arguments) {
            char[] holder = (char[]) arguments.get(0);
            int i = ((Integer) (arguments.get(1))).intValue();
            byte[] towrite = (byte[]) arguments.get(2);
        try {
            
              ((TransactionalFile) (benchmark.m.get(String.valueOf(holder,0,i).toLowerCase().substring(0, 1)))).write(towrite);         
            //  ((RandomAccessFile) (benchmark.m.get(String.valueOf(holder,0,i).toLowerCase().substring(0, 1)))).write(towrite);         
   //           
        } catch (NullPointerException e){
             System.out.println(i);
             System.out.println(holder[0]);
             System.out.println("kir " + String.valueOf(holder,0,i).toLowerCase().substring(0, 1));
             
        
        } catch (IOException ex) {
            Logger.getLogger(PureIO.class.getName()).log(Level.SEVERE, null, ex);
        }
        
    }

    
    protected void printResults() {
        
    }

}
