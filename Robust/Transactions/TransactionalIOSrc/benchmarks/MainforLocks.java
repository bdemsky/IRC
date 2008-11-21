
/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package TransactionalIO.benchmarks;

import TransactionalIO.core.TransactionalFile;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author navid
 */
public class MainforLocks {
    
    public static void main(String args[]){
        try {
            RandomAccessFile file = new RandomAccessFile("/home/navid/randomwords.text", "rw");
            benchmark.init();
            long starttime = System.nanoTime();
            
      //      benchmark.filelock.writeLock().lock();
            lockthread1 thread1 = new lockthread1(file, 'a');
    //        benchmark.filelock.writeLock().unlock();
            
//            benchmark.filelock.writeLock().lock();
            lockthread1 thread2 = new lockthread1(file, 'b');
  //          benchmark.filelock.writeLock().unlock();
            
     //       benchmark.filelock.writeLock().lock();
            lockthread1 thread3 = new lockthread1(file, 'c');
       //     benchmark.filelock.writeLock().unlock();
            
         //   benchmark.filelock.writeLock().lock();
            lockthread1 thread4 = new lockthread1(file, 'd');
         //   benchmark.filelock.writeLock().unlock(); 
            
         //   benchmark.filelock.writeLock().lock();
            lockthread1 thread5 = new lockthread1(file, 'e');
         //   benchmark.filelock.writeLock().unlock();
            
         //   benchmark.filelock.writeLock().lock();
            lockthread1 thread6 = new lockthread1(file, 'f');
         //   benchmark.filelock.writeLock().unlock(); 
            
         //   benchmark.filelock.writeLock().lock();
            lockthread1 thread7 = new lockthread1(file, 'g');
         //   benchmark.filelock.writeLock().unlock();
            
         //   benchmark.filelock.writeLock().lock();
            lockthread1 thread8 = new lockthread1(file, 'h');
         //   benchmark.filelock.writeLock().unlock();
            
         //   benchmark.filelock.writeLock().lock();
            lockthread1 thread9 = new lockthread1(file, 'i');
         //   benchmark.filelock.writeLock().unlock();
            
         //   benchmark.filelock.writeLock().lock();
            lockthread1 thread10 = new lockthread1(file, 'j');
         //   benchmark.filelock.writeLock().unlock();
            
         //   benchmark.filelock.writeLock().lock();
            lockthread1 thread11 = new lockthread1(file, 'k');
         //   benchmark.filelock.writeLock().unlock();
            
         //   benchmark.filelock.writeLock().lock();
            lockthread1 thread12 = new lockthread1(file, 'l');
         //   benchmark.filelock.writeLock().unlock();
           
         //   benchmark.filelock.writeLock().lock();
            lockthread1 thread13 = new lockthread1(file, 'm');
         //   benchmark.filelock.writeLock().unlock();
            
         //   benchmark.filelock.writeLock().lock();
            lockthread1 thread14 = new lockthread1(file, 'n');
         //   benchmark.filelock.writeLock().unlock(); 
            
         //   benchmark.filelock.writeLock().lock();
            lockthread1 thread15 = new lockthread1(file, 'o');
         //   benchmark.filelock.writeLock().unlock();
            
         //   benchmark.filelock.writeLock().lock();
            lockthread1 thread16 = new lockthread1(file, 'p');
         //   benchmark.filelock.writeLock().unlock();
            
         //   benchmark.filelock.writeLock().lock();
            lockthread1 thread17 = new lockthread1(file, 'q');
         //   benchmark.filelock.writeLock().unlock();
            
         //   benchmark.filelock.writeLock().lock();
            lockthread1 thread18 = new lockthread1(file, 'r');
         //   benchmark.filelock.writeLock().unlock();
            
         //   benchmark.filelock.writeLock().lock();
            lockthread1 thread19 = new lockthread1(file, 's');
         //   benchmark.filelock.writeLock().unlock();
            
         //   benchmark.filelock.writeLock().lock();
            lockthread1 thread20 = new lockthread1(file, 't');
         //   benchmark.filelock.writeLock().unlock();

            thread1.join();
            thread2.join();
            thread3.join();
            thread4.join();
            thread5.join();
            thread6.join();
            thread7.join();
            thread8.join();
            thread9.join();
            thread10.join();
            thread11.join();
            thread12.join();
            thread13.join();
            thread14.join();
            thread15.join();
            thread16.join();
            thread17.join();
            thread18.join();
            thread19.join();
            thread20.join();

            long endttime = System.nanoTime();
            System.out.println(endttime - starttime);
            System.out.println((endttime - starttime) / 1000000);
             int index =97;
            for (int j = 0; j < 26; j++) {
                ((TransactionalFile)(benchmark.m.get(String.valueOf((char) (index+j))))).close();
            }
        } catch (InterruptedException ex) {
            Logger.getLogger(MainforLocks.class.getName()).log(Level.SEVERE, null, ex);
        } catch (FileNotFoundException ex) {
            Logger.getLogger(MainforLocks.class.getName()).log(Level.SEVERE, null, ex);
        }
        
    }
}
