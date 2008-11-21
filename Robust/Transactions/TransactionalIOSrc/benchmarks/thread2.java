/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package TransactionalIO.benchmarks;

import TransactionalIO.core.TransactionalFile;
import TransactionalIO.interfaces.TransactionalProgram;

/**
 *
 * @author navid
 */
public class thread2 implements TransactionalProgram{

    public void execute() {
        TransactionalFile tf1 = (TransactionalFile) benchmark.TransactionalFiles.get("0");
        //TransactionalFile tf1 = new TransactionalFile("/home/navid/output.text", "rw");
        byte[] b = new byte[20];
       // tf1.seek(20);
        int i = tf1.read(b);
        i = tf1.read(b);
        i = tf1.read(b);
        i = tf1.read(b);
           i = tf1.read(b);
              i = tf1.read(b);
                 i = tf1.read(b);
                    i = tf1.read(b);
                       i = tf1.read(b);
                          i = tf1.read(b);
                             i = tf1.read(b);
                                i = tf1.read(b);
                                

    }

}
