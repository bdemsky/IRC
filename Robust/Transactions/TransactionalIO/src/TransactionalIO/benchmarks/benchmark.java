/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package TransactionalIO.benchmarks;

import TransactionalIO.core.Defaults;
import TransactionalIO.core.TransactionalFile;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Vector;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 *
 * @author navid
 */
public class benchmark {
    static public HashMap TransactionalFiles = new HashMap();
     static public HashMap hotwords = new HashMap();
     static public HashMap names = new HashMap();
     static public HashMap reversenames = new HashMap();
    static int count = 0;
    public static String msg = new String();
    public static ReentrantLock lock = new ReentrantLock();
    public static Vector transacctions = new Vector();
    public static ReentrantLock filelock = new ReentrantLock();
    public static Map m;
    public static Map m2;
    public static Map m3;
    public static Map m4;
    public static String[] stocks;
    

    public benchmark() {
        
    }
     
    private static void preparenamelist(){
        try {
            byte[] data = new byte[1];
            char[] name = new char[20];
            RandomAccessFile file = new RandomAccessFile("/scratch/TransactionalIO/FinancialTransactionBenchmarkFiles/namelist.text", "rw");
            RandomAccessFile file2 = new RandomAccessFile("/scratch/TransactionalIO/FinancialTransactionBenchmarkFiles/financialtransaction.text", "rw");
            RandomAccessFile file3 = new RandomAccessFile("/scratch/TransactionalIO/FinancialTransactionBenchmarkFiles/accountbalance.text", "rw");
            
           
          stocks  = new String[20];
          stocks[0] = "Yahoo";
          stocks[1] = "Google";
          stocks[2] = "Microsoft";
          stocks[3] = "Broadcom";
          stocks[4] = "Sun";
          stocks[5] = "Qualcom";
          stocks[6] = "Intel";
          stocks[7] = "WaMU";
          stocks[8] = "BoA";
          stocks[9] = "IMU";
          stocks[10] = "BMW";
          stocks[11] = "Nokia";
          stocks[12] = "Motorolla";
          stocks[13] = "Samsung";
          stocks[14] = "TMobile";
          stocks[15] = "ATT";
          stocks[16] = "PRops";
          stocks[17] = "Asia";
          stocks[18] = "LOLa";
          stocks[19] = "Brita";
        /*          boolean hh = false; 
           boolean found = false;    
           while (true) {
     
                if (found){
                    System.out.println(file4.getFilePointer()-1);
                    file4.seek(file4.getFilePointer()-1);
                    file4.write(' ');
                    file4.write(stocks[(int)(Math.random()*10)].getBytes());
                    file4.write('\n');
                }
                if (hh)
                    break;
                found = false;
                data[0] = 'a';
                while (data[0] != '\n') {
                    int tt =0;
                    tt = file4.read(data);
                    found = true;
                    if (tt == -1) {
                        hh = true;
                        break;
                    } 
                }
           }*/
            
            boolean flag = false;
            boolean done = false;
            int wordcounter = 0;
            int counter =0;
            while(true){
                if (flag)
                    break;
                if (done){
             //       System.out.println("At " + wordcounter + " inserted " +String.copyValueOf(name, 0, counter));
                    m3.put(Integer.valueOf(wordcounter), String.copyValueOf(name, 0, counter));
                    m4.put(String.copyValueOf(name, 0, counter), Integer.valueOf(wordcounter));
                    wordcounter++;
                    done = false;
                }
                counter = 0;    
                data[0] = 'a';
                while (data[0] != '\n') {
                    int res;
                    res = file.read(data);
                    if (res == -1) {
                        flag = true;
                        break;
                    }
                    //System.out.println((char)data[0]);
                    if (!(Character.isLetter((char) data[0]))) {
                        continue;
                   }
                   name[counter] = (char)data[0];
                   done = true;
                   counter++;
                }
          }
            
            
      /*    counter = 0;  
          while (counter <30000)  {
              int index1 = (int)(Math.random()*50);
              int stocktrade = (int)(Math.random()*100);
              while (stocktrade == 0)
                  stocktrade = (int)(Math.random()*100);
              int index2 = (int)(Math.random()*50);
              while (index2 == index1)
                  index2 = (int)(Math.random()*50);
              //System.out.println(index);
              String towrite = (String)m3.get(Integer.valueOf(index1)) + " ";
              towrite += String.valueOf(stocktrade) + " ";
              towrite += (String)m3.get(Integer.valueOf(index2)) + " ";
              towrite += stocks[(int)(Math.random()*20)] + "\n";
              
              file2.write(towrite.getBytes());
            //  System.out.println(towrite);
              counter++;
          }*/
         // for (int i=0; i<50*Defaults.FILEFRAGMENTSIZE; i++)
              //file3.write('');
        
          
          for (int i=0; i<50; i++){
              String towrite = (String)m3.get(Integer.valueOf(i)) +"\n";
              for (int j=0; j<stocks.length; j++)
                towrite +=  stocks[j] + " Stock Balance: " + ((int)(Math.random()*100+100)) + "             \n";
              System.out.println(towrite);
              file3.write(towrite.getBytes());
              while (file3.getFilePointer()<(i+1)*Defaults.FILEFRAGMENTSIZE)
                  file3.write(new String(" ").getBytes());    
          }
          

        /*  for (int i=0; i<10; i++)
              System.out.println((char)f[i]);*/
          file.close();
//          file2.close();
          file3.close();
        } catch (IOException ex) {
            Logger.getLogger(benchmark.class.getName()).log(Level.SEVERE, null, ex);
        } 
       
    
    }

    public static void init(){
        try {

            m3 = Collections.synchronizedMap(names);
            m4 = Collections.synchronizedMap(reversenames);




           preparenamelist();
            count = 0;
            m = Collections.synchronizedMap(TransactionalFiles);
            TransactionalFile tr = new TransactionalFile("/scratch/TransactionalIO/PureIOBenchmarkFiles/randomwords.text", "rw");
            m.put(String.valueOf(count), tr);
            count++;
            TransactionalFile tr2 = new TransactionalFile("/home/navid/input.text", "rw");
            m.put(String.valueOf(count), tr2);
            count++;
            TransactionalFile tr3 = new TransactionalFile("/scratch/TransactionalIO/WordCunterBenchmarkFiles/iliad.text", "rw");
            m.put(String.valueOf(count), tr3);
            count++;
            TransactionalFile tr4 = new TransactionalFile("/scratch/TransactionalIO/WordCunterBenchmarkFiles/counter_benchmark_output.text", "rw");
            m.put(String.valueOf(count), tr4);
            count++;

            TransactionalFile tr5 = new TransactionalFile("/scratch/TransactionalIO/FinancialTransactionBenchmarkFiles/financialtransaction.text", "rw");
            m.put(String.valueOf(count), tr5);

            count++;

            TransactionalFile tr6 = new TransactionalFile("/scratch/TransactionalIO/FinancialTransactionBenchmarkFiles/accountbalance.text", "rw");
            m.put(String.valueOf(count), tr6);

            count++;

            TransactionalFile tr7 = new TransactionalFile("/scratch/TransactionalIO/FinancialTransactionBenchmarkFiles/financialtransactionlog.text", "rw");
            m.put(String.valueOf(count), tr7);

            count++;

            RandomAccessFile tr8 = new RandomAccessFile("/scratch/TransactionalIO/FinancialTransactionBenchmarkFiles/accountbalance.text", "rw");
            m.put(String.valueOf(count), tr8);
//
            count++;

            RandomAccessFile tr9 = new RandomAccessFile("/scratch/TransactionalIO/FinancialTransactionBenchmarkFiles/financialtransactionlog.text", "rw");
            m.put(String.valueOf(count), tr9);

            count++;

            int index = 97;
            for (int i = 0; i < 26; i++) {
           
                m.put(String.valueOf((char) (index+i)), new TransactionalFile("/scratch/TransactionalIO/PureIOBenchmarkFiles/"
+ String.valueOf((char) (index+i)) + ".text", "rw"));
                count++;
            }
            count = 0;
            m2 = Collections.synchronizedMap(hotwords);
            m2.put(Integer.valueOf(count), "Polydamas");
            count++;
            m2.put(Integer.valueOf(count), "Cebriones");
            count++;
            m2.put(Integer.valueOf(count), "Eurybates");
            count++;
            m2.put(Integer.valueOf(count), "Menoetius");
            count++;
            m2.put(Integer.valueOf(count), "countless");
            count++;
            m2.put(Integer.valueOf(count), "huntsman");
            count++;
            m2.put(Integer.valueOf(count), "presence");
            count++;
            m2.put(Integer.valueOf(count), "pursuit");
            count++;
            m2.put(Integer.valueOf(count), "masterfully");
            count++;
            m2.put(Integer.valueOf(count), "unweariable");
        } catch (FileNotFoundException ex) {
            Logger.getLogger(benchmark.class.getName()).log(Level.SEVERE, null, ex);
        }
        
    }
    

}
