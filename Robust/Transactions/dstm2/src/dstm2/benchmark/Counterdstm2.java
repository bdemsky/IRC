/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dstm2.benchmark;

import dstm2.Thread;
import dstm2.atomic;
import dstm2.factory.Factory;
import TransactionalIO.benchmarks.benchmark;
import TransactionalIO.core.TransactionalFile;
import dstm2.util.Random;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Iterator;
import java.util.Vector;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author navid
 */
public class Counterdstm2 extends CustomBenchmark {
   
    private static Factory<CountKeeper> factory = Thread.makeFactory(CountKeeper.class);
    private CountKeeper word1_occurence;
    private CountKeeper word2_occurence;
    private CountKeeper word3_occurence;
    private CountKeeper word4_occurence;
    private CountKeeper word5_occurence;
    private CountKeeper word6_occurence;
    private CountKeeper word7_occurence;
    private CountKeeper word8_occurence;
    private CountKeeper word9_occurence;
    private CountKeeper word10_occurence;
    private CountKeeper word11_occurence;
    private CountKeeper word12_occurence;
    
    private int[] occurences;
    
    
    
    public void init() {
        occurences = new int[12];
       // for (int i = 0; i< 10; i++)
            
            
        word1_occurence = factory.create();
        word1_occurence.setOccurence(0);
              
        word2_occurence = factory.create();
        word2_occurence.setOccurence(0);
              
        word3_occurence = factory.create();
        word3_occurence.setOccurence(0);
              
        word4_occurence = factory.create();
        word4_occurence.setOccurence(0);
              
        word5_occurence = factory.create();
        word5_occurence.setOccurence(0);
              
        word6_occurence = factory.create();
        word6_occurence.setOccurence(0);
              
        word7_occurence = factory.create();
        word7_occurence.setOccurence(0);
              
        word8_occurence = factory.create();
        word8_occurence.setOccurence(0);
              
        word9_occurence = factory.create();
        word9_occurence.setOccurence(0);
              
        word10_occurence = factory.create();
        word10_occurence.setOccurence(0);
        
        word11_occurence = factory.create();
        word11_occurence.setOccurence(0);
        
        word12_occurence = factory.create();
        word12_occurence.setOccurence(0);
        
    }
    
 /*   public void execute(){
                TransactionalFile f1 = (TransactionalFile)benchmark.m.get("2");
                byte[] data = new byte[1];
                char[] holder = new char[10000];
                char[] word = new char[20];
                boolean flag = false;    
                long toseek = Integer.valueOf(Thread.currentThread().getName().substring(7)) * 21169; 
                f1.seek(toseek);

                data[0] ='a';
                if (toseek != 0) //////////////// skipt the first word since its been read already
                    while (data[0] != '\n'){
                        int res;
                        res = f1.read(data);
                        if (res == -1){
                            flag =true;
                            break;
                        }
                    }

                boolean completeword = false;
           
                int counter = 0;
                while (f1.getFilePointer() < toseek +21169)
                {
                    if (flag)
                        break;
                    data[0] = 'a';
                    int i = 0;
                    int res;
                    //if (completeparag)
                    while ((data[0] != '\n' || completeword)){

                        if (completeword){
                           completeword = false; 
                           int tmp = processInput(String.valueOf(word,0,counter-1)); 
                           if (tmp != -1){
                                switch(tmp){
                                    case 0:
                                        word1_occurence.setOccurence(word1_occurence.getOccurence() + 1);
                                        break;
                                   case 1:
                                        word2_occurence.setOccurence(word2_occurence.getOccurence() + 1);
                                        break;
                                   case 2:
                                        word3_occurence.setOccurence(word3_occurence.getOccurence() + 1);
                                        break;
                                   case 3:
                                        word4_occurence.setOccurence(word4_occurence.getOccurence() + 1);
                                        break;
                                   case 4:
                                        word5_occurence.setOccurence(word5_occurence.getOccurence() + 1);
                                        break;
                                   case 5:
                                        word6_occurence.setOccurence(word6_occurence.getOccurence() + 1);
                                        break;
                                   case 6:
                                        word7_occurence.setOccurence(word7_occurence.getOccurence() + 1);
                                        break;
                                   case 7:
                                        word8_occurence.setOccurence(word8_occurence.getOccurence() + 1);
                                        break;
                                   case 8:
                                        word9_occurence.setOccurence(word9_occurence.getOccurence() + 1);
                                        break;
                                   case 9:
                                        word10_occurence.setOccurence(word10_occurence.getOccurence() + 1);
                                        break;
                                   
                                }
                                switch(tmp){
                                    case 0:
                                        occurences[0] = occurences[0] + 1;
                                        break;
                                   case 1:
                                        occurences[1] = occurences[1] + 1;
                                        break;
                                   case 2:
                                        occurences[2] = occurences[2] + 1;
                                        break;
                                   case 3:
                                        occurences[3] = occurences[3] + 1;
                                        break;
                                   case 4:
                                        occurences[4] = occurences[4] + 1;
                                        break;
                                   case 5:
                                        occurences[5] = occurences[5] + 1;
                                        break;
                                   case 6:
                                        occurences[6] = occurences[6] + 1;
                                        break;
                                   case 7:
                                        occurences[7] = occurences[7] + 1;
                                        break;
                                   case 8:
                                        occurences[8] = occurences[8] + 1;
                                        break;
                                   case 9:
                                        occurences[9] = occurences[9] + 1;
                                        break;  
                                   }
                                //update data structure     
                                String tolog = new String();
                                
                                tolog = "-----------------------------------------------------------------";
                                tolog += "Found Word: " + String.valueOf(word,0,counter-1) + "\nAt Offset: ";
                                tolog += f1.getFilePointer() - counter;
                                tolog += "\n";
                                
                                //byte[] towrite0 = new byte[title.length()];
                                //towrite0  = title.getBytes();
                                
                                tolog += String.valueOf(holder,0,i);
                                tolog += "\n";
                                tolog += "-----------------------------------------------------------------";
                                tolog += "\n";
                                
                                byte[] towrite = new byte[tolog.length()];
                                towrite = tolog.getBytes();
                                //towrite = tmpstr.getBytes();
                                
                                
                                try {                   
                                  
                                    
                                    ((TransactionalFile) (benchmark.m.get("3"))).write(towrite);         
                                    //((TransactionalFile) (benchmark.m.get("3"))).write();

                                } catch (IOException ex) {
                                    Logger.getLogger(Counter.class.getName()).log(Level.SEVERE, null, ex);
                                }
                                
                          } 
                       }

                       if (flag)
                            break;

                       if (completeword){
                            holder[i] = (char)data[0];
                            i++;

                       }
                       counter = 0;   
                       completeword= false;
                       data[0] = 'a';
                       while(Character.isLetter((char)data[0]))
                       {

                            res = f1.read(data);
                            if (res == -1){
                                flag = true;
                                break;
                            }
                            word[counter] = (char)data[0];
                            counter++;
                            if (counter > 1)
                                completeword = true;
                            holder[i] = (char)data[0];
                            i++;
                       }
                    }
                } 
               //return true; 
        }

    
    private int processInput(String str){
        
        Iterator it = benchmark.m2.keySet().iterator();
        while (it.hasNext()){
            Integer index = (Integer) it.next();
            String pattern = (String)benchmark.m2.get(index);
            if (str.equalsIgnoreCase(pattern)){
                return index;
            }
        }
        return -1;
    }*/   
    
    @atomic public interface CountKeeper{
        int getOccurence();
        void setOccurence(int value);;
    }

 
    
    public void printResults() {
        for (int i =0; i<10; i++)
            //System.out.println((String)benchmark.m2.get(Integer.valueOf(i)) + " " + occurences[i]);
             switch(i){
                   case 0:
                        System.out.println((String)benchmark.m2.get(Integer.valueOf(i)) + " " + word1_occurence.getOccurence());
                        break;
                   case 1:
                        System.out.println((String)benchmark.m2.get(Integer.valueOf(i)) + " " + word2_occurence.getOccurence());
                        break;
                   case 2:
                        System.out.println((String)benchmark.m2.get(Integer.valueOf(i)) + " " + word3_occurence.getOccurence());
                        break;
                   case 3:
                        System.out.println((String)benchmark.m2.get(Integer.valueOf(i)) + " " + word4_occurence.getOccurence());
                        break;
                   case 4:
                        System.out.println((String)benchmark.m2.get(Integer.valueOf(i)) + " " + word5_occurence.getOccurence());
                        break;
                   case 5:
                        System.out.println((String)benchmark.m2.get(Integer.valueOf(i)) + " " + word6_occurence.getOccurence());
                        break;
                   case 6:
                        System.out.println((String)benchmark.m2.get(Integer.valueOf(i)) + " " + word7_occurence.getOccurence());
                        break;
                   case 7:
                        System.out.println((String)benchmark.m2.get(Integer.valueOf(i)) + " " + word8_occurence.getOccurence());
                        break;
                   case 8:
                        System.out.println((String)benchmark.m2.get(Integer.valueOf(i)) + " " + word9_occurence.getOccurence());
                        break;
                   case 9:
                        System.out.println((String)benchmark.m2.get(Integer.valueOf(i)) + " " + word10_occurence.getOccurence());
                        break;
                   case 10:
                        System.out.println((String)benchmark.m2.get(Integer.valueOf(i)) + " " + word11_occurence.getOccurence());
                        break;
                   case 11:
                        System.out.println((String)benchmark.m2.get(Integer.valueOf(i)) + " " + word12_occurence.getOccurence());
                        break;
                        
             }
        
    
    }

    @Override
    protected void execute(Vector arguments) {
        String towrite = (String)arguments.get(0);
        Integer i = (Integer)arguments.get(1);
        int indiex_of_object = i.intValue();
       /* switch(indiex_of_object){
            case 0:
                occurences[0] = occurences[0] + 1;
                break;
           case 1:
                occurences[1] = occurences[1] + 1;
                break;
           case 2:
                occurences[2] = occurences[2] + 1;
                break;
           case 3:
                occurences[3] = occurences[3] + 1;
                break;
           case 4:
                occurences[4] = occurences[4] + 1;
                break;
           case 5:
                occurences[5] = occurences[5] + 1;
                break;
           case 6:
                occurences[6] = occurences[6] + 1;
                break;
           case 7:
                occurences[7] = occurences[7] + 1;
                break;
           case 8:
                occurences[8] = occurences[8] + 1;
                break;
           case 9:
                occurences[9] = occurences[9] + 1;
                break;  
           case 10:
                occurences[10] = occurences[10] + 1;
                break; 
           case 11:
                occurences[11] = occurences[11] + 1;
                break; 
           }*/
         switch(indiex_of_object){
            case 0:
                word1_occurence.setOccurence(word1_occurence.getOccurence() + 1);
                break;
           case 1:
                word2_occurence.setOccurence(word2_occurence.getOccurence() + 1);
                break;
           case 2:
                word3_occurence.setOccurence(word3_occurence.getOccurence() + 1);
                break;
           case 3:
                word4_occurence.setOccurence(word4_occurence.getOccurence() + 1);
                break;
           case 4:
                word5_occurence.setOccurence(word5_occurence.getOccurence() + 1);
                break;
           case 5:
                word6_occurence.setOccurence(word6_occurence.getOccurence() + 1);
                break;
           case 6:
                word7_occurence.setOccurence(word7_occurence.getOccurence() + 1);
                break;
           case 7:
                word8_occurence.setOccurence(word8_occurence.getOccurence() + 1);
                break;
           case 8:
                word9_occurence.setOccurence(word9_occurence.getOccurence() + 1);
                break;
           case 9:
                word10_occurence.setOccurence(word10_occurence.getOccurence() + 1);
                break;
           case 10:
                word11_occurence.setOccurence(word11_occurence.getOccurence() + 1);
                break;
           case 11:
                word12_occurence.setOccurence(word12_occurence.getOccurence() + 1);
                break;
        }
                         
                                
        try {                   
            
            //((TransactionalFile) (benchmark.m.get("3"))).write(towrite.getBytes());         
            //((RandomAccessFile) (benchmark.m.get(100))).write(towrite.getBytes());
            ((RandomAccessFile) (benchmark.m.get("counterdstm2output"))).write(towrite.getBytes());

        } catch (IOException ex) {
            Logger.getLogger(Counter.class.getName()).log(Level.SEVERE, null, ex);
        }
        
    }


    
}


 

