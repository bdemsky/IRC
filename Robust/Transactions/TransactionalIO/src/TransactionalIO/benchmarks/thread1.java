/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package TransactionalIO.benchmarks;

import TransactionalIO.core.TransactionalFile;
import TransactionalIO.interfaces.TransactionalProgram;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.Random;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author navid
 */
public class thread1 implements TransactionalProgram{

    char sample;
    TransactionalFile tf;
    Vector v = new Vector();
            
    public thread1(char sample, TransactionalFile tf) {
        this.sample = sample;
        this.tf = tf;
        v.add("Real State");
        v.add("Loan");
        v.add("Mortgage");
        v.add("Account");
        v.add("Debit-card Purchase");
        v.add("Credit-card Purchase");
       
    }
    
    public thread1(char sample) {
        this.sample = sample;
        this.tf = null;
       /* v.add("Real State");
        v.add("Loan");
        v.add("Mortgage");
        v.add("Account");
        v.add("Debit-card Purchase");
        v.add("Credit-card Purchase");*/
       
    }

    

    
   
    public void execute() {
        //TransactionalFile f1 = (TransactionalFile)benchmark.TransactionalFiles.get("0");
            TransactionalFile f1;
            if (tf != null)
                f1 = tf;
            else 
                f1 = (TransactionalFile)benchmark.m.get("0");
            byte[] b = new byte[20];
            byte[] data = new byte[1];
            char[] holder = new char[30];
   
            
           long toseek = (Integer.valueOf(Thread.currentThread().getName().substring(7)))%20 * 20448; 
           f1.seek(toseek);
           
           data[0] ='a';
           if (toseek != 0) //////////////// skipt the first word since its been read already
                while (data[0] != '\n'){
                    f1.read(data);
                }
               
               
           while (f1.getFilePointer() < toseek +20448)
           {
                data[0] = 'a';
                int i = 0;
                int result = 0;
                while (data[0] != '\n'){
                    result = f1.read(data);
                    holder[i] = (char)data[0];
                    i++;
                }
                
                byte[] towrite = new byte[String.valueOf(holder,0,i).length()];
                towrite = String.valueOf(holder,0,i).getBytes();
               try {
                      ((TransactionalFile) (benchmark.m.get(String.valueOf(holder,0,i).toLowerCase().substring(0, 1)))).write(towrite);         
                      
                } catch (IOException ex) {
                    Logger.getLogger(thread1.class.getName()).log(Level.SEVERE, null, ex);
                }
           }
    }
    

}
