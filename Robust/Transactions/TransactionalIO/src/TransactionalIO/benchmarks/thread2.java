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
public class thread2 implements TransactionalProgram{

    public void execute() {
        try {
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
        } catch (IOException ex) {
            Logger.getLogger(thread2.class.getName()).log(Level.SEVERE, null, ex);
        }
                                

    }

}
