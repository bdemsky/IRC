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
            char[] holder = new char[40];
   
            
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
        
          
           // for (int k=0; k< i; k++)
           //     System.out.println(Thread.currentThread() + " " +holder[k]);
          //  f1.seek(40);
          //  System.out.println("current offset " +f1.getFilePointer());
          //  f1.write(b);
          //  f1.write(b);
            
            
          //  f1.write(b);
            //
          
          
          /*  for (int i =0; i< 2000; i++){
                
                String str = "Number: " + (Integer.valueOf(Thread.currentThread().getName().substring(7))*200+i) +"\nType: " + v.get((int)(Math.random()*6)) + "\n\n";
                byte[] buff; 
                char[] charar =  new char[str.length()];
                charar = str.toCharArray();
                buff = new byte[str.length()];
                for (int j=0; j<str.length(); j++)
                    buff[j] = (byte) charar[j];
                
                f1.write(buff);
            }*/
            
      /*      f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
          //  f1.seek(0);
           // f1.seek(0);
         //   f1.getFilePointer();
           // synchronized(benchmark.lock){
              //   /*System.out.println(Thread.currentThread() +" 1-offset " + *///f1.getFilePointer();
            //}
          //      synchronized(benchmark.lock){
          // f1.getFilePointer();
            //   }
           /// f1.read(bread);
    /*        f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);*/
            /*f1.read(bread);
           // f1.seek(0);
            
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);*/
            
          /*  f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);
            f1.write(b);*/
         //   f1.seek(0);
          //  f1.read(bread2);
          /*  synchronized(benchmark.lock){
                for (int j =0; j<20; j++)
                    System.out.println(j+1 + "-" +Thread.currentThread() + "   " + (char)bread2[j]); 
                }*/
           // f1.read(bread);
               /*synchronized(benchmark.lock){
                for (int j =0; j<20; j++)
                    System.out.println(j+1 + "-" +Thread.currentThread() + "   " + (char)bread[j]); 
                }*/
           //f1.read(bread);
            //*synchronized(benchmark.lock){
       //         for (int j =0; j<20; j++)
          //          System.out.println(j+1 + "-" +Thread.currentThread() + "   " + (char)bread[j]); 
          //      }*/
             //  f1.read(bread);
           /*    synchronized(benchmark.lock){
                for (int j =0; j<20; j++)
                    System.out.println(j+1 + "-" +Thread.currentThread() + "   " + (char)bread[j]); 
                }*/
               //  f1.read(bread);
           /*      synchronized(benchmark.lock){
                for (int j =0; j<20; j++)
                    System.out.println(j+1 + "-" +Thread.currentThread() + "   " + (char)bread[j]); 
                }*/
             //f1.read(bread);
       /*      synchronized(benchmark.lock){
                for (int j =0; j<20; j++)
                    System.out.println(j+1 + "-" +Thread.currentThread() + "   " + (char)bread[j]); 
                }*/
              //f1.read(bread);
              /*synchronized(benchmark.lock){
                for (int j =0; j<20; j++)
                    System.out.println(j+1 + "-" +Thread.currentThread() + "   " + (char)bread[j]); 
                }*/
               //f1.read(bread);
               /*synchronized(benchmark.lock){
                for (int j =0; j<20; j++)
                    System.out.println(j+1 + "-" +Thread.currentThread() + "   " + (char)bread[j]); 
                }*/
               /*
               f1.read(bread);
                 f1.read(bread2);
            f1.read(bread);
            f1.read(bread);
               f1.read(bread);
                 f1.read(bread2);
             f1.read(bread);
              f1.read(bread);
               f1.read(bread);
               f1.read(bread);
            
                 f1.read(bread2);
            f1.read(bread);
            f1.read(bread);
               f1.read(bread);
                 f1.read(bread2);
             f1.read(bread);
              f1.read(bread);
               f1.read(bread);
               f1.read(bread);
            
            
             
           // f1.seek(0);
          /*      synchronized(benchmark.lock){
                for (int j =0; j<20; j++)
                    System.out.println(j+1 + "-" +Thread.currentThread() + "   " + (char)bread[j]); 
                }
                 synchronized(benchmark.lock){
                for (int j =0; j<20; j++)
                    System.out.println(j+1 + "-" +Thread.currentThread() + "   " + (char)bread2[j]); 
                }
            */
           /* synchronized(benchmark.lock){
                 System.out.println(Thread.currentThread() +" 2-offset " + f1.getFilePointer());
            }*/
            /*f1.write(b);
            f1.write(b);
           // f1.seek(0);
           
            f1.write(b);
            f1.write(b);*/
            
            
             /* synchronized(benchmark.lock){
                 System.out.println(Thread.currentThread() +" 3-offset " + f1.getFilePointer());
            }*/
           // f1.seek(50);
           /* synchronized(benchmark.lock){
                 System.out.println(Thread.currentThread() +" 4-offset " + f1.getFilePointer());
            }*/
               // f1.read(bread);
            /*    synchronized(benchmark.lock){
                for (int j =0; j<20; j++)
                    System.out.println(j+1 + "-" +Thread.currentThread() + "   " + (char)bread[j]); 
                }*/
               // f1.read(bread);
                /*synchronized(benchmark.lock){
                for (int j =0; j<20; j++)
                    System.out.println(j+1 + "-" +Thread.currentThread() + "   " + (char)bread[j]);
                }*/
               // f1.read(bread);
                /*synchronized(benchmark.lock){
                for (int j =0; j<20; j++)
                    System.out.println(j+1 + "-" +Thread.currentThread() + "   " + (char)bread[j]);
                }*/
               // f1.read(bread);
                /*synchronized(benchmark.lock){
                for (int j =0; j<20; j++)
                    System.out.println(j+1 + "-" +Thread.currentThread() + "   " + (char)bread[j]);
                }*/
                
                //f1.read(bread);
                /*synchronized(benchmark.lock){
                for (int j =0; j<20; j++)
                    System.out.println(j+1 + "-" +Thread.currentThread() + "   " + (char)bread[j]);
                }*/
            /*  synchronized(benchmark.lock){
                 System.out.println(Thread.currentThread() +" 5-offset " + f1.getFilePointer());
            }*/
            //System.out.println("offset " + f1.getFilePointer());
           /* for (int j =0; j<20; j++)
                System.out.println(j+1 + "-" +Thread.currentThread() + "   " + (char)bread[j]);*/
           /* f1.read(bread);
            System.out.println("offset " + f1.getFilePointer());*/
           // for (int j =0; j<20; j++)
            //    System.out.println(j+1 + "-" +Thread.currentThread() + "   " + (char)bread[j]);
          //  f1.write(b);
         /*    synchronized(benchmark.lock){
                 System.out.println(Thread.currentThread() +" 6-offset " + f1.getFilePointer());
            }*/
            //f1.read(bread);
            /*  synchronized(benchmark.lock){
                 System.out.println(Thread.currentThread() +" 5-offset " + f1.getFilePointer());
            }*/
           // System.out.println("offset " + f1.getFilePointer());
            /*f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);
            f1.read(bread);*/
           // f1.write(b);
           // f1.write(b);
            
         
        
          //  f1.seek(40);
            
            //   for (int j =0; j<10; j++)
            //    System.out.println((char) b[j]);

//        } catch (IOException ex) {
   //       Logger.getLogger(thread1.class.getName()).log(Level.SEVERE, null, ex);
   //     }
       // TransactionalFile f1 = new TransactionalFile("/home/navid/output1.txt", "rw");
       // TransactionalFile f1 = new TransactionalFile("/home/navid/output1.txt", "rw");
       // TransactionalFile f1 = new TransactionalFile("/home/navid/output1.txt", "rw");
    }
    

}
