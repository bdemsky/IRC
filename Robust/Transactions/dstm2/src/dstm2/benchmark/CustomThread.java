/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dstm2.benchmark;

import TransactionalIO.benchmarks.benchmark;
import TransactionalIO.core.TransactionalFile;
import TransactionalIO.exceptions.GracefulException;
import com.sun.corba.se.impl.protocol.SpecialMethod;
import dstm2.SpecialTransactionalFile;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import dstm2.Thread;
import dstm2.atomic;
import dstm2.factory.Factory;
import java.io.RandomAccessFile;
import java.util.Vector;
import java.util.concurrent.Callable;

/**
 *
 * @author navid
 */
public class CustomThread implements Runnable {


    private Thread thread;
    private CustomBenchmark mybenchmark;
    static final Object lock = new Object();
    int insertCalls = 0;
    /**
     * number of calls to contains()
     */
    int containsCalls = 0;
    /**
     * number of calls to remove()
     */
    int removeCalls = 0;
    /**
     * amount by which the set size has changed
     */
    int delta = 0;
    Object[] locksforfiles;

    public CustomThread(CustomBenchmark benchmark) {
        locksforfiles = new Object[26];
        for (int i = 0; i < 26; i++) {
            locksforfiles[i] = new Object();
        }
        mybenchmark = benchmark;
        thread = new Thread(this);

    }

    public void start() {
        thread.start();
    }

    public void join() {
        try {
            thread.join();
        } catch (InterruptedException ex) {
            Logger.getLogger(CustomThread.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void run() {
        if (mybenchmark instanceof Counter) {
            counterBenchmark();
        } else if (mybenchmark instanceof Counterdstm2) {
            counterdstm2Benchmark();
        } else if (mybenchmark instanceof Counterdstm2Special) {
            counterdstm2SpecialBenchmark();
        } else if (mybenchmark instanceof FinancialTransaction) {
            financialBenchmark();
        } else if (mybenchmark instanceof FinancialTransactionv2) {
            financialBenchmarkv2();
        } else if (mybenchmark instanceof FinancialTransactiondstm2Special) {
            financialBenchmarkdstm2Special();
        } else if (mybenchmark instanceof PureIO) {
            pureIOBenchmark();
        } else if (mybenchmark instanceof PureIOdstm2version) {
            pureIOdstm2Benchmark();
        }
    }

    public void pureIOBenchmark() {
        try {
            //try {
            // TransactionalFile f1 = (TransactionalFile)benchmark.m.get("0");
            //TransactionalFile f1 = new TransactionalFile("/scratch/TransactionalIO/PureIOBenchmarkFiles/randomwords.text", "rw");
            //RandomAccessFile f1 = new RandomAccessFile("/home/navid/randomwords.text", "rw");
            TransactionalFile f1 = new TransactionalFile("/scratch/TransactionalIO/PureIOBenchmarkFiles/randomwords.text", "rw");

            byte[] b = new byte[20];
            byte[] data = new byte[1];
            char[] holder = new char[40];

            boolean flag = false;
            int res = 0;
            long toseek;
            long threadoffset;
            ///for two thread
            threadoffset = 204485;
            //for four thread
            //threadoffset = 204485/2;
            //for eight thread
            //threadoffset = 204485/4;

            toseek = (Integer.valueOf(Thread.currentThread().getName().substring(7))) * threadoffset;
            f1.seek(toseek);
            //   System.out.println(toseek + " " + Thread.currentThread());

            data[0] = 'a';
            if (toseek != 0) {
                //////////////// skipt the first word since its been read already
                while (data[0] != '\n') {
                    f1.read(data);
                }
            }
            while (f1.getFilePointer() < toseek + threadoffset) {

                if (flag == true) {
                    break;
                }
                try {
                    data[0] = 'a';
                    int i = 0;
                    int result = 0;
                    while (data[0] != '\n') {
                        result = f1.read(data);
                        if (result == -1) {
                            flag = true;
                            break;
                        //return;
                        }


                        holder[i] = (char) data[0];
                        //   synchronized(benchmark.lock){
                        //    System.out.println(Thread.currentThread() + " " + holder[i]);
                        // }
                        i++;
                    }
                    if (holder[0] == '\n') {
                        continue;
                    }
                    byte[] towrite = new byte[String.valueOf(holder, 0, i).length()];
                    towrite = String.valueOf(holder, 0, i).getBytes();
                    //final Vector arguments = new Vector();

                    //arguments.add(holder);
                    //arguments.add(Integer.valueOf(i));
                    //arguments.add(towrite);
                    //String.copyValueOf(word, 0, counter - 1)
                    //boolean resultt = Thread.doIt(new Callable<Boolean>() {
                    //mybenchmark.programlock.lock();
                    // public Boolean call() {
                    // System.out.println(holder[0]);
                    // System.out.println((int)Character.toLowerCase(holder[0])-97);
                    ((TransactionalFile) (benchmark.m.get(String.valueOf(holder, 0, i).toLowerCase().substring(0, 1)))).write(towrite);
                //         mybenchmark.execute(arguments);
                //return true;
                // }
                //mybenchmark.programlock.unlock();
                //});
                // arguments.clear();
                } catch (GracefulException g) {
                    //           synchronized (lock) {
                    //             mybenchmark.printResults();
                    /*insertCalls   += myInsertCalls;
                    removeCalls   += myRemoveCalls;
                    containsCalls += myContainsCalls;
                    delta         += myDelta;*/
                    //           }
                }
            }
        //    } catch (IOException ex) {
        //        Logger.getLogger(CustomThread.class.getName()).log(Level.SEVERE, null, ex);
        //   }
        } catch (IOException ex) {
            Logger.getLogger(CustomThread.class.getName()).log(Level.SEVERE, null, ex);
        }
    //    } catch (IOException ex) {
    //        Logger.getLogger(CustomThread.class.getName()).log(Level.SEVERE, null, ex);
    //   }


    }

    public void pureIOdstm2Benchmark() {
        try {
            //try {
            // TransactionalFile f1 = (TransactionalFile)benchmark.m.get("0");
            //TransactionalFile f1 = new TransactionalFile("/scratch/TransactionalIO/PureIOBenchmarkFiles/randomwords.text", "rw");
            RandomAccessFile f1 = new RandomAccessFile("/scratch/TransactionalIO/PureIOBenchmarkFiles/randomwords.text", "rw");
            //TransactionalFile f1 = new TransactionalFile("/home/navid/randomwords.text", "rw");

            byte[] b = new byte[20];
            byte[] data = new byte[1];
            char[] holder = new char[40];

            boolean flag = false;
            int res = 0;
            long toseek;
            long threadoffset;
            ///for two thread
            //threadoffset = 204485;
            //for four thread
            //threadoffset = 204485/2;
            //for eight thread
            threadoffset = 204485/4;

            toseek = (Integer.valueOf(Thread.currentThread().getName().substring(7))) * threadoffset;
            f1.seek(toseek);
            //   System.out.println(toseek + " " + Thread.currentThread());

            data[0] = 'a';
            if (toseek != 0) {
                //////////////// skipt the first word since its been read already
                while (data[0] != '\n') {
                    f1.read(data);
                }
            }
            while (f1.getFilePointer() < toseek + threadoffset) {

                if (flag == true) {
                    break;
                }
                try {
                    data[0] = 'a';
                    int i = 0;
                    int result = 0;
                    while (data[0] != '\n') {
                        result = f1.read(data);
                        if (result == -1) {
                            flag = true;
                            break;
                        //return;
                        }


                        holder[i] = (char) data[0];
                        //   synchronized(benchmark.lock){
                        //    System.out.println(Thread.currentThread() + " " + holder[i]);
                        // }
                        i++;
                    }
                    if (holder[0] == '\n') {
                        continue;
                    }
                    byte[] towrite = new byte[String.valueOf(holder, 0, i).length()];
                    towrite = String.valueOf(holder, 0, i).getBytes();
                    //synchronized (locksforfiles[(int) Character.toLowerCase(holder[0]) - 97]) {
                    final Vector arguments = new Vector();

                    arguments.add(holder);
                    arguments.add(Integer.valueOf(i));
                    arguments.add(towrite);
                    
                    boolean resultt = Thread.doIt(new Callable<Boolean>() {
                    //mybenchmark.programlock.lock();
                        public Boolean call() {
                    // System.out.println(holder[0]);
                    // System.out.println((int)Character.toLowerCase(holder[0])-97);
                    
                         mybenchmark.execute(arguments);
                        return true;
                        }
                        
                    });

                    arguments.clear();
                } catch (GracefulException g) {
                    //           synchronized (lock) {
                    //             mybenchmark.printResults();
                    /*insertCalls   += myInsertCalls;
                    removeCalls   += myRemoveCalls;
                    containsCalls += myContainsCalls;
                    delta         += myDelta;*/
                    //           }
                }
            }

        } catch (IOException ex) {
            Logger.getLogger(CustomThread.class.getName()).log(Level.SEVERE, null, ex);
        }


    }

    public void financialBenchmark() {
        try {
            //       try {
            //RandomAccessFile f1 = new RandomAccessFile("/home/navid/financialtransaction.text", "rw");
            RandomAccessFile f1 = new RandomAccessFile("/scratch/TransactionalIO/FinancialTransactionBenchmarkFiles/financialtransaction.text", "rw");
            //TransactionalFile f1 = new TransactionalFile("/scratch/TransactionalIO/FinancialTransactionBenchmarkFiles/financialtransaction.text", "rw");
            byte[] data = new byte[1];
    
            char[] word = new char[20];

            boolean flag = false;
            int counter = 0;
            long toseek;
            long threadoffset;
            ///for two thread
            threadoffset = 360611;
            //for four thread
            //threadoffset = 360611/2;
            //for eight thread
            //threadoffset = 360611/4;

            toseek = (Integer.valueOf(Thread.currentThread().getName().substring(7))) * threadoffset; //;// 53417;266914;//// ;

            f1.seek(toseek);
            // System.out.println(toseek);

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


            while (f1.getFilePointer() < toseek + threadoffset) {
                if (flag) {
                    break;
                }
                final Vector arguments = new Vector();
                try {
                    counter = 0;
                    data[0] = 'a';
                    while (data[0] != ' ') {
                        int res = f1.read(data);
                        if (res == -1) {
                            flag = true;
                            break;
                        }
                        word[counter] = (char) data[0];
                        counter++;
                    }

                    if (flag) {
                        return;
                    }
                    arguments.add(String.copyValueOf(word, 0, counter - 1));
                    counter = 0;
                    data[0] = 'a';
                    while (data[0] != ' ') {
                        int res = f1.read(data);
                        if (res == -1) {
                            flag = true;
                            break;
                        }
                        word[counter] = (char) data[0];
                        counter++;

                    }
                    if (flag) {
                        return;
                    }
                    arguments.add(Integer.parseInt(String.valueOf(word, 0, counter - 1)));

                    counter = 0;
                    data[0] = 'a';

                    while (data[0] != ' ') {

                        int res = f1.read(data);
                        if (res == -1) {
                            flag = true;
                            break;
                        }
                        word[counter] = (char) data[0];
                        counter++;
                    }

                    if (flag) {
                        return;
                    }
                    arguments.add(String.copyValueOf(word, 0, counter - 1));


                    counter = 0;
                    data[0] = 'a';

                    while (data[0] != '\n') {
                        int res = f1.read(data);
                        if (res == -1) {
                            flag = true;
                            break;
                        }

                        word[counter] = (char) data[0];
                        counter++;
                    }
                    if (flag) {
                        return;
                    }
                    arguments.add(String.copyValueOf(word, 0, counter - 1));
                    //  mybenchmark.programlock.lock();
                    boolean result = Thread.doIt(new Callable<Boolean>() {
                        public Boolean call() {

                            try {

                                mybenchmark.execute(arguments);
                            } catch (java.lang.ArrayIndexOutOfBoundsException e) {
                                e.printStackTrace();
                            }
                            return true;
                        }
                    });
                    //  mybenchmark.programlock.unlock();
                    arguments.clear();
                } catch (GracefulException g) {
                }
            }
           // mybenchmark.printResults();
        } catch (IOException ex) {
            Logger.getLogger(CustomThread.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    public void financialBenchmarkv2() {
        try {
            //       try {
            RandomAccessFile f1 = new RandomAccessFile("/scratch/TransactionalIO/FinancialTransactionBenchmarkFiles/financialtransaction.text", "rw");
            //TransactionalFile f1 = new TransactionalFile("/home/navid/financialtransaction.text", "rw");
            byte[] data = new byte[1];

            char[] word = new char[20];

            boolean flag = false;
            int counter = 0;
            long toseek;
            long threadoffset;
            ///for two thread
            threadoffset = 360611;
            //for four thread
            //threadoffset = 360611/2;
            //for eight thread
            //threadoffset = 360611/4;

            toseek = (Integer.valueOf(Thread.currentThread().getName().substring(7))) * threadoffset; //;// 53417;266914;//// ;

            f1.seek(toseek);
            // System.out.println(toseek);

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


            while (f1.getFilePointer() < toseek + threadoffset) {
                if (flag) {
                    break;
                }
                final Vector arguments = new Vector();
                try {
                    counter = 0;
                    data[0] = 'a';
                    while (data[0] != ' ') {
                        int res = f1.read(data);
                        if (res == -1) {
                            flag = true;
                            break;
                        }
                        word[counter] = (char) data[0];
                        counter++;
                    }

                    if (flag) {
                        return;
                    }
                    arguments.add(String.copyValueOf(word, 0, counter - 1));
                    counter = 0;
                    data[0] = 'a';
                    while (data[0] != ' ') {
                        int res = f1.read(data);
                        if (res == -1) {
                            flag = true;
                            break;
                        }
                        word[counter] = (char) data[0];
                        counter++;
                    }
                    if (flag) {
                        return;
                    }
                    arguments.add(Integer.parseInt(String.valueOf(word, 0, counter - 1)));

                    counter = 0;
                    data[0] = 'a';

                    while (data[0] != ' ') {

                        int res = f1.read(data);
                        if (res == -1) {
                            flag = true;
                            break;
                        }
                        word[counter] = (char) data[0];
                        counter++;

                    }

                    if (flag) {
                        return;
                    }
                    arguments.add(String.copyValueOf(word, 0, counter - 1));


                    counter = 0;
                    data[0] = 'a';

                    while (data[0] != '\n') {
                        int res = f1.read(data);
                        if (res == -1) {
                            flag = true;
                            break;
                        }

                        word[counter] = (char) data[0];
                        counter++;
                    }
                    if (flag) {
                        return;
                    }
                    arguments.add(String.copyValueOf(word, 0, counter - 1));
                    
                   // mybenchmark.programlock.lock();
                    final TransactionalFile file = new  TransactionalFile("/scratch/TransactionalIO/FinancialTransactionBenchmarkFiles/accountbalance.text", "rw");
                    arguments.add(file);
                    boolean result = Thread.doIt(new Callable<Boolean>() {

                        public Boolean call() {

                            try {

                                mybenchmark.execute(arguments);
                            } catch (java.lang.ArrayIndexOutOfBoundsException e) {
                                e.printStackTrace();
                            }

                            return true;
                        }
                    });
                    //mybenchmark.programlock.unlock();
                    arguments.clear();
                    file.file.close();
                } catch (GracefulException g) {
                }
            }
            //mybenchmark.printResults();
//        } catch (IOException ex) {
//            Logger.getLogger(CustomThread.class.getName()).log(Level.SEVERE, null, ex);
        //     }
        } catch (IOException ex) {
            Logger.getLogger(CustomThread.class.getName()).log(Level.SEVERE, null, ex);
        }
//        } catch (IOException ex) {
//            Logger.getLogger(CustomThread.class.getName()).log(Level.SEVERE, null, ex);
    //     }



    }

    public void financialBenchmarkdstm2Special() {
        try {
            //       try {
            RandomAccessFile f1 = new RandomAccessFile("/scratch/TransactionalIO/FinancialTransactionBenchmarkFiles/financialtransaction.text", "rw");
            //TransactionalFile f1 = new TransactionalFile("/home/navid/financialtransaction.text", "rw");
            byte[] data = new byte[1];
            char[] word = new char[20];
            boolean flag = false;
            int counter = 0;
            long toseek;
            long threadoffset;
            ///for two thread
            //threadoffset = 360611;
            //for four thread
            //threadoffset = 360611/2;
            //for eight thread
            threadoffset = 360611/4;
            //threadoffset = 360611/8;

            toseek = (Integer.valueOf(Thread.currentThread().getName().substring(7))) * threadoffset; //;// 53417;266914;//// ;

            f1.seek(toseek);
            // System.out.println(toseek);

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


            while (f1.getFilePointer() < toseek + threadoffset) {
                if (flag) {
                    break;
                }
                final Vector arguments = new Vector();
                try {
                    counter = 0;
                    data[0] = 'a';
                    while (data[0] != ' ') {
                        int res = f1.read(data);
                        if (res == -1) {
                            flag = true;
                            break;
                        }
                        word[counter] = (char) data[0];
                        counter++;
                    }

                    if (flag) {
                        return;
                    }
                    arguments.add(String.copyValueOf(word, 0, counter - 1));
                    counter = 0;
                    data[0] = 'a';
                    while (data[0] != ' ') {
                        int res = f1.read(data);
                        if (res == -1) {
                            flag = true;
                            break;
                        }
                        word[counter] = (char) data[0];
                        counter++;
                    }
                    if (flag) {
                        return;
                    }
                    arguments.add(Integer.parseInt(String.valueOf(word, 0, counter - 1)));

                    counter = 0;
                    data[0] = 'a';

                    while (data[0] != ' ') {

                        int res = f1.read(data);
                        if (res == -1) {
                            flag = true;
                            break;
                        }
                        word[counter] = (char) data[0];
                        counter++;
                    }

                    if (flag) {
                        return;
                    }
                    arguments.add(String.copyValueOf(word, 0, counter - 1));


                    counter = 0;
                    data[0] = 'a';

                    while (data[0] != '\n') {
                        int res = f1.read(data);
                        if (res == -1) {
                            flag = true;
                            break;
                        }

                        word[counter] = (char) data[0];
                        counter++;
                    }
                    if (flag) {
                        return;
                    }
                    arguments.add(String.copyValueOf(word, 0, counter - 1));
                    //mybenchmark.programlock.lock();
                    final SpecialTransactionalFile file = new  SpecialTransactionalFile("/scratch/TransactionalIO/FinancialTransactionBenchmarkFiles/accountbalance.text", "rw");
                    arguments.add(file);
                    boolean result = Thread.doIt(new Callable<Boolean>() {

                        public Boolean call() {

                            try {

                                mybenchmark.execute(arguments);
                            } catch (java.lang.ArrayIndexOutOfBoundsException e) {
                                e.printStackTrace();
                            }

                            return true;
                        }
                    });
                    //mybenchmark.programlock.unlock();
                    file.close();
                    arguments.clear();
                } catch (GracefulException g) {
                }
            }
         //mybenchmark.printResults();
        } catch (IOException ex) {
            Logger.getLogger(CustomThread.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void counterBenchmark() {
        try {


            //TransactionalFile f1 = new TransactionalFile("/scratch/TransactionalIO/WordCunterBenchmarkFiles/iliad.text", "rw");
            //TransactionalFile f1 = new TransactionalFile("/scratch/TransactionalIO/WordCunterBenchmarkFiles/iliad.text", "rw");
            RandomAccessFile f1 = new RandomAccessFile("/scratch/TransactionalIO/WordCunterBenchmarkFiles/iliad.text", "rw");
            //RandomAccessFile f1 = new RandomAccessFile("/home/navid/iliad.text", "rw");
            byte[] data = new byte[1];
            char[] holder = new char[10000];
            char[] word = new char[20];
            boolean flag = false;
            long toseek;
            long threadoffset;
            ///for two thread
            //threadoffset = 211686;
            //for four thread
            threadoffset = 211686/4;
            //for eight thread
            //threadoffset = 211686/4;
            // System.out.print("dddd");
            toseek = Integer.valueOf(Thread.currentThread().getName().substring(7)) * threadoffset;//42337;

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
            boolean completeword = false;

            int counter = 0;

            while (f1.getFilePointer() < toseek + threadoffset) {
                try {
                    if (flag) {
                        break;
                    }
                    data[0] = 'a';
                    int i = 0;
                    int res;
                    //if (completeparag)
                    while (data[0] != '\n' || completeword) {

                        if (completeword) {
                            completeword = false;
                            final int tmp = processInput(String.valueOf(word, 0, counter - 1));
                            if (tmp != -1) {
                                final String topass = execute(holder, word, counter, i, f1.getFilePointer());
                                boolean result;
                                final Vector arguments = new Vector();
                                arguments.add(topass);
                                arguments.add(Integer.valueOf(tmp));
                                result = Thread.doIt(new Callable<Boolean>() {

                                    public Boolean call() {
                                        // mybenchmark.programlock.lock();
                                        //mybenchmark.execute(topass, tmp);
                                        mybenchmark.execute(arguments);
                                        // mybenchmark.programlock.unlock();
                                        return true;
                                    }
                                });
                                arguments.clear();
                            }
                        }

                        if (flag) {
                            break;
                        }
                        if (completeword) {
                            holder[i] = (char) data[0];
                            i++;
                        }
                        counter = 0;
                        completeword = false;
                        data[0] = 'a';
                        while (Character.isLetter((char) data[0])) {

                            res = f1.read(data);
                            if (res == -1) {
                                flag = true;
                                break;
                            }
                            word[counter] = (char) data[0];
                            counter++;
                            if (counter > 1) {
                                completeword = true;
                            }
                            holder[i] = (char) data[0];
                            i++;
                        }
                    }
                } catch (GracefulException g) {
                }
            }

        //mybenchmark.printResults();
        } catch (IOException ex) {
            Logger.getLogger(CustomThread.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    public void counterdstm2Benchmark() {
        try {


            //TransactionalFile f1 = new TransactionalFile("/scratch/TransactionalIO/WordCunterBenchmarkFiles/iliad.text", "rw");
            //  TransactionalFile f1 = new TransactionalFile("/home/navid/iliad.text", "rw");
            RandomAccessFile f1 = new RandomAccessFile("/scratch/TransactionalIO/WordCunterBenchmarkFiles/iliad.text", "rw");
            byte[] data = new byte[1];
            char[] holder = new char[10000];
            char[] word = new char[20];
            boolean flag = false;
            long threadoffset;
            long toseek;
            // threadoffset = 211686;
            //for four thread
            //threadoffset = 211686/2;
            //for eight thread
            threadoffset = 211686 / 4;
            // System.out.print("dddd");
            toseek = Integer.valueOf(Thread.currentThread().getName().substring(7)) * threadoffset;
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
            boolean completeword = false;

            int counter = 0;

            while (f1.getFilePointer() < toseek + threadoffset) {
                try {
                    if (flag) {
                        break;
                    }
                    data[0] = 'a';
                    int i = 0;
                    int res;
                    //if (completeparag)
                    while (data[0] != '\n' || completeword) {

                        if (completeword) {
                            completeword = false;
                            final int tmp = processInput(String.valueOf(word, 0, counter - 1));
                            if (tmp != -1) {
                                final String topass = execute(holder, word, counter, i, f1.getFilePointer());
                                boolean result;
                                final Vector arguments = new Vector();
                                arguments.add(topass);
                                arguments.add(Integer.valueOf(tmp));
                                mybenchmark.programlock.lock();
                                result = Thread.doIt(new Callable<Boolean>() {

                                    public Boolean call() {
                                        mybenchmark.execute(arguments);
                                        return true;
                                    }
                                });
                                mybenchmark.programlock.unlock();
                                arguments.clear();
                            }
                        }

                        if (flag) {
                            break;
                        }
                        if (completeword) {
                            holder[i] = (char) data[0];
                            i++;
                        }
                        counter = 0;
                        completeword = false;
                        data[0] = 'a';
                        while (Character.isLetter((char) data[0])) {

                            res = f1.read(data);
                            if (res == -1) {
                                flag = true;
                                break;
                            }
                            word[counter] = (char) data[0];
                            counter++;
                            if (counter > 1) {
                                completeword = true;
                            }
                            holder[i] = (char) data[0];
                            i++;
                        }
                    }
                } catch (GracefulException g) {
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(CustomThread.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    public void counterdstm2SpecialBenchmark() {
        try {


            //TransactionalFile f1 = new TransactionalFile("/scratch/TransactionalIO/WordCunterBenchmarkFiles/iliad.text", "rw");
            //  TransactionalFile f1 = new TransactionalFile("/home/navid/iliad.text", "rw");
            RandomAccessFile f1 = new RandomAccessFile("/scratch/TransactionalIO/WordCunterBenchmarkFiles/iliad.text", "rw");
            byte[] data = new byte[1];
            char[] holder = new char[10000];
            char[] word = new char[20];
            boolean flag = false;
            long threadoffset;
            long toseek;
            //threadoffset = 211686;
            //for four thread
            threadoffset = 211686/2;
            //for eight thread
            //threadoffset = 211686/4;
            // System.out.print("dddd");
            toseek = Integer.valueOf(Thread.currentThread().getName().substring(7)) * threadoffset;
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
            boolean completeword = false;

            int counter = 0;

            while (f1.getFilePointer() < toseek + threadoffset) {
                try {
                    if (flag) {
                        break;
                    }
                    data[0] = 'a';
                    int i = 0;
                    int res;
                    //if (completeparag)
                    while (data[0] != '\n' || completeword) {

                        if (completeword) {
                            completeword = false;
                            final int tmp = processInput(String.valueOf(word, 0, counter - 1));
                            if (tmp != -1) {
                                final String topass = execute(holder, word, counter, i, f1.getFilePointer());
                                boolean result;
                                final Vector arguments = new Vector();
                                arguments.add(topass);
                                arguments.add(Integer.valueOf(tmp));
                                //   mybenchmark.programlock.lock();
                                result = Thread.doIt(new Callable<Boolean>() {

                                    public Boolean call() {
                                        mybenchmark.execute(arguments);
                                        return true;
                                    }
                                });
                                //  mybenchmark.programlock.unlock();
                                arguments.clear();
                            }
                        }

                        if (flag) {
                            break;
                        }
                        if (completeword) {
                            holder[i] = (char) data[0];
                            i++;
                        }
                        counter = 0;
                        completeword = false;
                        data[0] = 'a';
                        while (Character.isLetter((char) data[0])) {

                            res = f1.read(data);
                            if (res == -1) {
                                flag = true;
                                break;
                            }
                            word[counter] = (char) data[0];
                            counter++;
                            if (counter > 1) {
                                completeword = true;
                            }
                            holder[i] = (char) data[0];
                            i++;
                        }
                    }
                } catch (GracefulException g) {
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(CustomThread.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private int processInput(String str) {

        Iterator it = benchmark.m2.keySet().iterator();
        while (it.hasNext()) {
            Integer index = (Integer) it.next();
            String pattern = (String) benchmark.m2.get(index);
            if (str.equalsIgnoreCase(pattern)) {
                return index;
            }
        }
        return -1;
    }

    private String execute(char[] holder, char[] word, int counter, int i, long offset) {
        String tolog = new String();

        tolog = "-----------------------------------------------------------------";
        tolog += "Found Word: " + String.valueOf(word, 0, counter - 1) + "\nAt Offset: ";
        tolog += offset - counter;
        tolog += "\n";

        //byte[] towrite0 = new byte[title.length()];
        //towrite0  = title.getBytes();

        tolog += String.valueOf(holder, 0, i);
        tolog += "\n";
        tolog += "-----------------------------------------------------------------";
        tolog += "\n";

        byte[] towrite = new byte[tolog.length()];
        towrite = tolog.getBytes();
        //towrite = tmpstr.getBytes();

        return tolog;
    /*try {                   
    //  System.out.println("dddddd");
    
    ((TransactionalFile) (benchmark.m.get("3"))).write(towrite);         
    //((TransactionalFile) (benchmark.m.get("3"))).write();
    
    } catch (IOException ex) {
    Logger.getLogger(CustomThread.class.getName()).log(Level.SEVERE, null, ex);
    }*/
    }
}
