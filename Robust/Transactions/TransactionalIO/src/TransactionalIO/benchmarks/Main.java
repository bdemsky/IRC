/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package TransactionalIO.benchmarks;

import TransactionalIO.core.CustomThread;
import TransactionalIO.core.ExtendedTransaction;
import TransactionalIO.core.TransactionalFile;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author navid
 */
public class Main {

    public static void main(String args[]){
        try {
        //    benchmark.init();
          
            
        // for (int i=0; i<100; i++){
            benchmark.init();
            //System.out.println(
            long starttime = System.nanoTime();
           /* thread1 tr1 = new thread1();
            thread2 tr2 = new thread2();
            thread3 tr3 = new thread3();
            thread4 tr4 = new thread4();*/
            /*TransactionalIO tr2 = new TransactionalIO();
            TransactionalIO tr3 = new TransactionalIO();
            CustomThread ct1 = new CustomThread(tr1);
            CustomThread ct2 = new CustomThread(tr2);*/
           
          /*  CustomThread ct1 = new CustomThread(new thread1('a', new TransactionalFile("/home/navid/output.text", "rw")));
            CustomThread ct2 = new CustomThread(new thread1('b', new TransactionalFile("/home/navid/output.text", "rw")));
            CustomThread ct3 = new CustomThread(new thread1('c', new TransactionalFile("/home/navid/output.text", "rw")));
            CustomThread ct4= new CustomThread(new thread1('d', new TransactionalFile("/home/navid/output.text", "rw")));
            CustomThread ct5 = new CustomThread(new thread1('e', new TransactionalFile("/home/navid/output.text", "rw")));
            CustomThread ct6 = new CustomThread(new thread1('f', new TransactionalFile("/home/navid/output.text", "rw")));
            CustomThread ct7 = new CustomThread(new thread1('g', new TransactionalFile("/home/navid/output.text", "rw")));
            CustomThread ct8 = new CustomThread(new thread1('h', new TransactionalFile("/home/navid/output.text", "rw")));
            CustomThread ct9 = new CustomThread(new thread1('i',new TransactionalFile("/home/navid/output.text", "rw")));
            CustomThread ct10 = new CustomThread(new thread1('j', new TransactionalFile("/home/navid/output.text", "rw")));
            CustomThread ct11 = new CustomThread(new thread1('k', new TransactionalFile("/home/navid/output.text", "rw")));
            CustomThread ct12 = new CustomThread(new thread1('l', new TransactionalFile("/home/navid/output.text", "rw")));
            CustomThread ct13 = new CustomThread(new thread1('m', new TransactionalFile("/home/navid/output.text", "rw")));
            CustomThread ct14 = new CustomThread(new thread1('n', new TransactionalFile("/home/navid/output.text", "rw")));
            
            CustomThread ct15 = new CustomThread(new thread1('o', new TransactionalFile("/home/navid/output.text", "rw")));
            CustomThread ct16 = new CustomThread(new thread1('p', new TransactionalFile("/home/navid/output.text", "rw")));
            CustomThread ct17 = new CustomThread(new thread1('q', new TransactionalFile("/home/navid/output.text", "rw")));
            CustomThread ct18 = new CustomThread(new thread1('r', new TransactionalFile("/home/navid/output.text", "rw")));
             
           // CustomThread ct15 = new CustomThread(tr2);
            CustomThread ct19 = new CustomThread(new thread1('s', new TransactionalFile("/home/navid/output.text", "rw")));
            CustomThread ct20 = new CustomThread(new thread1('t', new TransactionalFile("/home/navid/output.text", "rw")));*/
            
            
            CustomThread ct1 = new CustomThread(new thread1('a'));
            CustomThread ct2 = new CustomThread(new thread1('b'));
            CustomThread ct3 = new CustomThread(new thread1('c'));
            CustomThread ct4= new CustomThread(new thread1('d'));
            CustomThread ct5 = new CustomThread(new thread1('e'));
            CustomThread ct6 = new CustomThread(new thread1('f'));
            CustomThread ct7 = new CustomThread(new thread1('g'));
            CustomThread ct8 = new CustomThread(new thread1('h'));
            CustomThread ct9 = new CustomThread(new thread1('i'));
            CustomThread ct10 = new CustomThread(new thread1('j'));
            CustomThread ct11 = new CustomThread(new thread1('k'));
            CustomThread ct12 = new CustomThread(new thread1('l'));
            CustomThread ct13 = new CustomThread(new thread1('m'));
            CustomThread ct14 = new CustomThread(new thread1('n'));
            
            CustomThread ct15 = new CustomThread(new thread1('o'));
            CustomThread ct16 = new CustomThread(new thread1('p'));
            CustomThread ct17 = new CustomThread(new thread1('q'));
            CustomThread ct18 = new CustomThread(new thread1('r'));
             
           // CustomThread ct15 = new CustomThread(tr2);
            CustomThread ct19 = new CustomThread(new thread1('s'));
            CustomThread ct20 = new CustomThread(new thread1('t'));
            
           
            //CustomThread ct4 = new CustomThread(tr3);
            
            // CustomThread ct5 = new CustomThread(tr4);
            // CustomThread ct6 = new CustomThread(tr2); 
              
           //  CustomThread ct4 = new CustomThread(tr2);
            ct1.runner.join();
            ct2.runner.join();
            ct3.runner.join();
            ct4.runner.join();
            ct5.runner.join();
            ct6.runner.join();
            ct7.runner.join();
            ct8.runner.join();
            ct9.runner.join(); 
            ct10.runner.join();
            ct11.runner.join(); 
            ct12.runner.join();
            ct13.runner.join();
            ct14.runner.join();
            
            
          
            ct15.runner.join();
            ct16.runner.join();
            ct17.runner.join();
            ct18.runner.join(); 
            ct19.runner.join();
            
            ct20.runner.join();
            
            long endttime = System.nanoTime();
           // System.out.println(endttime - starttime);
            System.out.println((endttime - starttime)/1000000);
            //}
          /*  TreeMap msgs = new TreeMap();
            Iterator it = benchmark.transacctions.iterator();
            while(it.hasNext()){
                ExtendedTransaction tr = (ExtendedTransaction) it.next();
                msgs.putAll(tr.msg);
            }
            
            Iterator it2 = msgs.keySet().iterator();
            while(it2.hasNext()){
                 Long time = (Long) it2.next();
                 System.out.print(time +" " + msgs.get(time));
            }*/
            int index =97;
            for (int j = 0; j < 26; j++) {
                try {
                    ((TransactionalFile) (benchmark.m.get(String.valueOf((char) (index+j))))).close();
                } catch (IOException ex) {
                    Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
      //  }
              
            //System.out.println(Thread.currentThread().getName());
       } catch (InterruptedException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }
        //System.out.println(Thread.currentThread().getName());
    
        
    }
}
