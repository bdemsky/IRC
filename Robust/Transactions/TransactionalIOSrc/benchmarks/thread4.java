/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package TransactionalIO.benchmarks;

import TransactionalIO.core.TransactionalFile;
import TransactionalIO.interfaces.TransactionalProgram;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author navid
 */
public class thread4  implements TransactionalProgram{
    public void execute() {
        TransactionalFile f1 = (TransactionalFile)benchmark.TransactionalFiles.get("0");
        byte[] b = new byte[40];
     //   int i = f1.read(b);
     //   i = f1.read(b);
    //    i = f1.read(b);
     //   i = f1.read(b);
     //   i = f1.read(b);
     //   i = f1.read(b);
       // System.out.println(i);
       // for (int j =0; j<10; j++)
    //        System.out.println((char) b[j]);
        try {
          //  f1.read(b);
            for (int j =0; j<40; j++)
                b[j] = 'l';
          //  f1.seek(40);
          //  System.out.println("current offset " +f1.getFilePointer());
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
          //  f1.seek(40);
            //f1.read(b);
            //   for (int j =0; j<10; j++)
            //    System.out.println((char) b[j]);

        } catch (IOException ex) {
            Logger.getLogger(thread1.class.getName()).log(Level.SEVERE, null, ex);
        }
       // TransactionalFile f1 = new TransactionalFile("/home/navid/output1.txt", "rw");
       // TransactionalFile f1 = new TransactionalFile("/home/navid/output1.txt", "rw");
       // TransactionalFile f1 = new TransactionalFile("/home/navid/output1.txt", "rw");
    }
}
