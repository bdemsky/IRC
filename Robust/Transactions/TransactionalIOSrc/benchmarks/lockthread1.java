/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package TransactionalIO.benchmarks;

import TransactionalIO.core.TransactionalFile;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author navid
 */
public class lockthread1 extends Thread{

    RandomAccessFile f1;
    char sample;
    public lockthread1(RandomAccessFile file, char sample) {
        f1 = file;
        this.sample = sample;
        this.start();
    }

    
    
    public void run(){
        try {


            //  f1.read(b);
            byte[] b = new byte[20];
            for (int j = 0; j < 19; j++) {
                b[j] = (byte) sample;
            }
            b[19] = (byte) '\n';

            

            //  f1 = (TransactionalFile)benchmark.m.get("0");
            //  f1 = (TransactionalFile)benchmark.m.get("0");
            byte[] data = new byte[1];
            char[] holder = new char[40];
            benchmark.filelock.lock();

            long toseek = Integer.valueOf(Thread.currentThread().getName().substring(7)) * 20448;
            f1.seek(toseek);

            data[0] = 'a';
            if (toseek != 0) {
                //////////////// skipt the first word since its been read already
                while (data[0] != '\n') {
                    f1.read(data);
                }
            }
            while (f1.getFilePointer() < toseek + 20448) {
                data[0] = 'a';
                int i = 0;
                int result = 0;
                while (data[0] != '\n') {
                    result = f1.read(data);
                    holder[i] = (char) data[0];
                    i++;
                }

                byte[] towrite = new byte[String.valueOf(holder, 0, i).length()];
                towrite = String.valueOf(holder, 0, i).getBytes();
                try {
                    ((TransactionalFile) (benchmark.m.get(String.valueOf(holder,0,i).toLowerCase().substring(0, 1)))).file.write(towrite);
                } catch (IOException ex) {
                    Logger.getLogger(thread1.class.getName()).log(Level.SEVERE, null, ex);
                }
            }

            benchmark.filelock.unlock();
        } catch (IOException ex) {
            Logger.getLogger(lockthread1.class.getName()).log(Level.SEVERE, null, ex);
        }
           


    }
}
