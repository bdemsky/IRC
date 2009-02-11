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
public class PureIOtest extends CustomBenchmark{

    @Override
    protected void init() {
        
    }


    protected void execute() {
        try {
            TransactionalFile f1 = (TransactionalFile)benchmark.m.get("0");
           // RandomAccessFile f1 = ((TransactionalFile) benchmark.m.get("0")).file;
            byte[] data = new byte[1];
            char[] holder = new char[10000];
            char[] word = new char[20];
            boolean flag = false;
            long toseek = Integer.valueOf(Thread.currentThread().getName().substring(7)) * 20448;

          //  benchmark.filelock.lock();
            f1.seek(toseek);

            data[0] = 'a';
            if (toseek != 0) {
                //////////////// skipt the first word since its been read already
                while (data[0] != '\n') {
                    int res;
                    res = f1.read(data);
                    if (res == -1) {
                        flag = true;
                        break;
                    }
                }
            }
            while (f1.getFilePointer() < toseek + 20448) {
                if (flag == true) {
                    break;
                }
                data[0] = 'a';
                int i = 0;
                int res;
                while (data[0] != '\n') {
                    res = f1.read(data);
                    if (res == -1) {
                        flag = true;
                        break;
                    }

                    holder[i] = (char) data[0];
                    i++;
                }


                byte[] towrite = new byte[String.valueOf(holder, 0, i).length()];
                towrite = String.valueOf(holder, 0, i).getBytes();



                  //     System.out.println(String.valueOf(holder,0,i).toLowerCase().substring(0, 1));
                    //((TransactionalFile) (benchmark.m.get(String.valueOf(holder,0,i).toLowerCase().substring(0, 1)))).file.write(towrite);
                ((TransactionalFile) (benchmark.m.get(String.valueOf(holder,0,i).toLowerCase().substring(0, 1)))).write(towrite);
                    //update the memory         //}
            }
           // benchmark.filelock.unlock();
        } catch (IOException ex) {
            Logger.getLogger(PureIOtest.class.getName()).log(Level.SEVERE, null, ex);
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void printResults() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    protected void execute(Vector arguments) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

}
