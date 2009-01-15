/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dstm2.benchmark;

import TransactionalIO.benchmarks.benchmark;
import TransactionalIO.core.Defaults;
import TransactionalIO.core.TransactionalFile;
import dstm2.AtomicArray;
import dstm2.atomic;
import dstm2.Thread;
import dstm2.factory.Factory;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 *
 * @author navid
 */
public class FinancialTransaction extends CustomBenchmark{
     private static Factory<FinancialTransactionDS> factory = Thread.makeFactory(FinancialTransactionDS.class);
     private static Factory<RootHolder> factory2 = Thread.makeFactory(RootHolder.class);
     private static Factory<FTrHolder> factory3 = Thread.makeFactory(FTrHolder.class);
     
     LockedFTrHolder[] hlm;
     
     
     
     
 /*    String buyer1 = new String();
     int soldshare1 = 0;
     String seller1 = new String();
     
     String buyer2 = new String();
     int soldshare2 = 0;
     String seller2 = new String();
     
     String buyer3 = new String();
     int soldshare3 = 0;
     String seller3 = new String();
     
     String buyer4 = new String();
     int soldshare4 = 0;
     String seller4 = new String();
     
     String buyer5 = new String();
     int soldshare5 = 0;
     String seller5 = new String();
     
     int lockedcounter = 1;*/
     AtomicArray<FTrHolder> financialTransactionKeeper;

    protected void init() {
       // hlm = new LockedFTrHolder[20];
      /*  for (int i=0; i<20; i++){
            hlm[i] = new LockedFTrHolder();
            hlm[i].counter =1;
            hlm[i].lk = new LockedFinancialTransactionDS[5];
            for (int j=0; j<5; j++){
                hlm[i].lk[j] = new LockedFinancialTransactionDS();
                hlm[i].lk[j].buyer = "";
                hlm[i].lk[j].seller = "";
                hlm[i].lk[j].soldshare = 0;
            }
                
        }*/
        
        
        
        
        RootHolder ck =  factory2.create();
        ck.setFinancialTransactionKeeper(new AtomicArray<FTrHolder>(FTrHolder.class, 20));
        

        financialTransactionKeeper = ck.getFinancialTransactionKeeper();
        for (int i=0; i<20; i++){ 
           
            FTrHolder f1 = factory3.create();
            f1.setCounter(1);
            f1.setFinancialTransactionKeeper(new AtomicArray<FinancialTransactionDS>(FinancialTransactionDS.class, 5));
            for (int j=0; j<5; j++)
            {
                FinancialTransactionDS ftk = factory.create();
                ftk.setBuyer("");
                ftk.setSeller("");
                ftk.setSoldShare(0);
                AtomicArray<FinancialTransactionDS> tmp = f1.getFinancialTransactionKeeper();
                tmp.set(j, ftk);
            }
          
            
            financialTransactionKeeper.set(i, f1);
       }
 
    }


    protected void execute(Vector arguments) {
        try {

            TransactionalFile file = (TransactionalFile) benchmark.m.get("5");
        //    RandomAccessFile file = (RandomAccessFile) benchmark.m.get("7");
            String oldowner = (String) arguments.get(0);
            Integer stocktrade = (Integer) arguments.get(1);
            String newowner = (String) arguments.get(2);
            String nameofstock = (String) arguments.get(3);
            Integer offset1 = (Integer) benchmark.m4.get(oldowner);
            Integer offset2 = (Integer) benchmark.m4.get(newowner);
            
      
            file.seek(offset1 * Defaults.FILEFRAGMENTSIZE);
            Vector v = computeandupdate(true, stocktrade, nameofstock);
            String st = (String)(v.get(1));
            long offset1towrite = ((Long)(v.get(0))).longValue();
            
            file.seek(offset2 * Defaults.FILEFRAGMENTSIZE);
            v = computeandupdate(false, stocktrade, nameofstock);
            String st2 = (String)(v.get(1));
            long offset2towrite = ((Long)(v.get(0))).longValue();
            
            
            file.seek(offset1towrite);
            file.write(st.getBytes());
            file.seek(offset2towrite);
            file.write(st2.getBytes());
            
      //     RandomAccessFile file2 = (RandomAccessFile) benchmark.m.get("8");
           TransactionalFile file2 = (TransactionalFile) benchmark.m.get("6");
            
            String towrite = oldowner + " " + stocktrade.toString() + " " + newowner + " " + nameofstock + " processed\n";
            file2.write(towrite.getBytes());
            /*switch(lockedcounter){
                case 1:
                      seller1 = oldowner;
                      soldshare1 = stocktrade.intValue();
                      buyer1 = newowner;
                      lockedcounter = 2;
                      break;
                case 2:
                      seller2 = oldowner;
                      soldshare2 = stocktrade.intValue();
                      buyer2 = newowner;
                      lockedcounter = 3;
                      break;
                case 3:
                      seller3 = oldowner;
                      soldshare3 = stocktrade.intValue();
                      buyer3 = newowner;
                      lockedcounter = 4;
                      break;
                case 4:
                      seller4 = oldowner;
                      soldshare4 = stocktrade.intValue();
                      buyer4 = newowner;
                      lockedcounter = 5;
                      break;
                case 5:    
                      seller5 = oldowner;
                      soldshare5 = stocktrade.intValue();
                      buyer5 = newowner;
                      lockedcounter = 1;
                      break;
            }*/
               int i; 
               for (i=0;i<benchmark.stocks.length; i++){
                   if (benchmark.stocks[i].equalsIgnoreCase(nameofstock))
                       break;
               }
         
            switch(financialTransactionKeeper.get(i).getCounter()){
                case 1:
                      financialTransactionKeeper.get(i).getFinancialTransactionKeeper().get(0).setSeller(oldowner);
                      financialTransactionKeeper.get(i).getFinancialTransactionKeeper().get(0).setSoldShare(stocktrade.intValue());
                      financialTransactionKeeper.get(i).getFinancialTransactionKeeper().get(0).setBuyer(newowner);
                      financialTransactionKeeper.get(i).setCounter(2);
                      break;
                case 2:
                      financialTransactionKeeper.get(i).getFinancialTransactionKeeper().get(1).setSeller(oldowner);
                      financialTransactionKeeper.get(i).getFinancialTransactionKeeper().get(1).setSoldShare(stocktrade.intValue());
                      financialTransactionKeeper.get(i).getFinancialTransactionKeeper().get(1).setBuyer(newowner);
                      financialTransactionKeeper.get(i).setCounter(3);
                      break;
                case 3:
                      financialTransactionKeeper.get(i).getFinancialTransactionKeeper().get(2).setSeller(oldowner);
                      financialTransactionKeeper.get(i).getFinancialTransactionKeeper().get(2).setSoldShare(stocktrade.intValue());
                      financialTransactionKeeper.get(i).getFinancialTransactionKeeper().get(2).setBuyer(newowner);
                      financialTransactionKeeper.get(i).setCounter(4);
                      break;
                case 4:
                      financialTransactionKeeper.get(i).getFinancialTransactionKeeper().get(3).setSeller(oldowner);
                      financialTransactionKeeper.get(i).getFinancialTransactionKeeper().get(3).setSoldShare(stocktrade.intValue());
                      financialTransactionKeeper.get(i).getFinancialTransactionKeeper().get(3).setBuyer(newowner);
                      financialTransactionKeeper.get(i).setCounter(5);
                      break;
                case 5:    
                      financialTransactionKeeper.get(i).getFinancialTransactionKeeper().get(4).setSeller(oldowner);
                      financialTransactionKeeper.get(i).getFinancialTransactionKeeper().get(4).setSoldShare(stocktrade.intValue());
                      financialTransactionKeeper.get(i).getFinancialTransactionKeeper().get(4).setBuyer(newowner);
                      financialTransactionKeeper.get(i).setCounter(1);
                      break;

            }
               
        /*     switch(hlm[i].counter){
                case 1:
                      hlm[i].lk[0].seller = oldowner;
                      hlm[i].lk[0].soldshare = stocktrade.intValue();
                      hlm[i].lk[0].buyer = newowner;
                      hlm[i].counter = 2;
                      break;
                case 2:
                      hlm[i].lk[1].seller = oldowner;
                      hlm[i].lk[1].soldshare = stocktrade.intValue();
                      hlm[i].lk[1].buyer = newowner;
                      hlm[i].counter = 3;
                      break;
                case 3:
                      hlm[i].lk[2].seller = oldowner;
                      hlm[i].lk[2].soldshare = stocktrade.intValue();
                      hlm[i].lk[2].buyer = newowner;
                      hlm[i].counter = 4;
                      break;
                case 4:
                      hlm[i].lk[3].seller = oldowner;
                      hlm[i].lk[3].soldshare = stocktrade.intValue();
                      hlm[i].lk[3].buyer = newowner;
                      hlm[i].counter = 5;
                      break;
                case 5:    
                      hlm[i].lk[4].seller = oldowner;
                      hlm[i].lk[4].soldshare = stocktrade.intValue();
                      hlm[i].lk[4].buyer = newowner;
                      hlm[i].counter = 1;
                      break;
            }*/
            
        } catch (IOException ex) {
            Logger.getLogger(FinancialTransaction.class.getName()).log(Level.SEVERE, null, ex);
        }
        }
        /*catch (NullPointerException e){
            System.out.println("file?? " + file);
        System.out.println("offset?? " + offset1);
       System.out.println(oldowner);
       System.out.println(Thread.currentThread());
            e.printStackTrace();
        }*/
       /* int oldbalance = getOldBalance();
       int newnumber =  oldbalance - stocktrade.intValue();
       updateFile(newnumber);
   //     System.out.println("offset: " + offset1 + " for: " + oldowner + " old balance: "+ oldbalance);
        
        if (oldowner.equals("Smith")){
            System.out.println("offset: " + offset1 + " for: " + oldowner + " old balance: "+ oldbalance);
            System.out.println("trade money: " + stocktrade);
        }
       // System.out.println("old number: " + oldbalance);
        
        
        oldbalance = getOldBalance();
        newnumber = oldbalance + stocktrade.intValue();
        updateFile(newnumber);*/
       /* if (newowner.equals("Smith")){
            System.out.println("offset: " + offset2 + " for: " + newowner + " old balance: "+ oldbalance);
               System.out.println("trade money: " + stocktrade);
        }*/
        
          
            
      //  }

   // }

    private Vector computeandupdate(boolean type, Integer stocktrade, String origstockname ){
      // try{ 
    //    RandomAccessFile file = (RandomAccessFile) benchmark.m.get("7");
        TransactionalFile file = (TransactionalFile)benchmark.m.get("5");
        Vector v = new Vector();
         byte[] data = new byte[1];
         char[] balance = new char[20];
         
        // int counter =0;
         boolean flag = false;
         data[0] = 'a';
         int counter = 0;
         while (data[0] != '\n') {
                int res;
                res = file.read(data);
                if (res == -1) {
                    flag = true;
                    break;
                }
        }

        while(true){ 
            char[] stname = new char[10];
            data[0] = 'a'; 
            int ol=0;
            while (data[0] != ' ') {
                int res;
                res = file.read(data);
                if (res == -1) {
                    flag = true;
                    break;
                }
                stname[ol] = (char)data[0];
                ol++;
            }
            String stockname = String.copyValueOf(stname, 0, ol-1);
            if (stockname.equalsIgnoreCase(origstockname))   { 
                break;
            }
            else while (data[0] != '\n')
                file.read(data);
        }
        
         
         
         
        data[0] = 'a';    
        while ((char) data[0] != ':') {
            int res;
            res = file.read(data);
            if (res == -1) {
                flag = true;
                break;
            }
        }
        int res = file.read(data);
        long offsetofnumber = file.getFilePointer();
        do {
            res = file.read(data);
            if (res == -1) {
                flag = true;
                break;
            }
            balance[counter] = (char) data[0];
            counter++;
        } while (Character.isDigit((char) data[0]) || (char)data[0] == '-');
        
        int oldbalance = Integer.parseInt(String.valueOf(balance, 0, counter - 1));
            
        //    return oldnumber;
            

         int newnumber;
          if (type){
             newnumber = oldbalance - stocktrade.intValue();
          }
          else 
              newnumber = oldbalance + stocktrade.intValue();

            
         //////   file.seek(offsetofnumber);
            
            
            String st = new String();
            st = String.valueOf(newnumber);
            if (String.valueOf(newnumber).length() < counter - 1){
             
                for (int i=0; i<counter-String.valueOf(newnumber).length(); i++){
                    st += (new String(" "));
                    //file.write((new String(" ")).getBytes());
                }
            }
       //     st += new String("\n");
            //file.write((new String("\n")).getBytes());
            v.add(Long.valueOf(offsetofnumber));
            v.add(st);
            return v;
//            } catch (IOException ex) {
               
       //         Logger.getLogger(FinancialTransaction.class.getName()).log(Level.SEVERE, null, ex);
       ///            return null;
        //    }
    }

    protected  void printResults() {
      
     for (int i=0; i<20; i++){   
        System.out.println("----------------------------------------------");  
        System.out.println(benchmark.stocks[i]);  
        for (int j=0; j<5; j++)
        {
            System.out.print(financialTransactionKeeper.get(i).getFinancialTransactionKeeper().get(j).getSeller() + " ");
            System.out.print(financialTransactionKeeper.get(i).getFinancialTransactionKeeper().get(j).getBuyer() + " ");
            System.out.println(financialTransactionKeeper.get(i).getFinancialTransactionKeeper().get(j).getSoldShare());
        }
        System.out.println("----------------------------------------------");
      }
    /*    for (int i=0; i<20; i++){ 
            System.out.println("----------------------------------------------");  
            System.out.println(benchmark.stocks[i]);  
            for (int j=0; j<5; j++)
            {
                 
                System.out.print(hlm[i].lk[j].seller + " ");
                System.out.print(hlm[i].lk[j].buyer + " ");
                System.out.println(hlm[i].lk[j].soldshare);
            }
            System.out.println("----------------------------------------------");
        }*/
        
      /*  System.out.print(finance1.getSeller() + " ");
        System.out.print(finance1.getBuyer() + " ");
        System.out.println(finance1.getSoldShare());
        
        System.out.print(finance2.getSeller() + " ");
        System.out.print(finance2.getBuyer()+ " ");
        System.out.println(finance2.getSoldShare());
        
        System.out.print(finance3.getSeller() + " ");
        System.out.print(finance3.getBuyer()+ " ");
        System.out.println(finance3.getSoldShare());
        
        System.out.print(finance4.getSeller() + " ");
        System.out.print(finance4.getBuyer()+ " ");
        System.out.println(finance4.getSoldShare());
        
        System.out.print(finance5.getSeller() + " ");
        System.out.print(finance5.getBuyer()+ " ");
        System.out.println(finance5.getSoldShare());*/
      
        /*System.out.print(buyer1 + " ");
        System.out.print(soldshare1 + " ");
        System.out.println(seller1);
        
        System.out.print(buyer2 + " ");
        System.out.print(soldshare2 + " ");
        System.out.println(seller2);
        
        System.out.print(buyer3 + " ");
        System.out.print(soldshare3 + " ");
        System.out.println(seller3);
        
        System.out.print(buyer4 + " ");
        System.out.print(soldshare4 + " ");
        System.out.println(seller4);
        
        System.out.print(buyer5 + " ");
        System.out.print(soldshare5 + " ");
        System.out.println(seller5);*/
        
        //System.out.println("----------------------------------------------");
    }
    

    
    
      @atomic public interface FinancialTransactionDS{
        String getSeller();
        void setSeller(String value);
        int getSoldShare();
        void setSoldShare(int value);
        String getBuyer();
        void setBuyer(String value);  
      }
      
      @atomic public interface FTrHolder{
          AtomicArray<FinancialTransactionDS> getFinancialTransactionKeeper();
          void setFinancialTransactionKeeper(AtomicArray<FinancialTransactionDS> arr);
          int getCounter();
          void setCounter(int value);
      }
      
      @atomic public interface RootHolder{
          AtomicArray<FTrHolder> getFinancialTransactionKeeper();
          void setFinancialTransactionKeeper(AtomicArray<FTrHolder> arr);
        //  int getCounter();
        //  void setCounter(int value);
      }
      
      class LockedFinancialTransactionDS{
          public String seller;
          public String buyer;
          public int soldshare;          
      }
      
      class LockedFTrHolder{
          public LockedFinancialTransactionDS[] lk = new LockedFinancialTransactionDS[5];
          public int counter;
      }
      
      
      
      
      /*    private int getOldBalance(){
         
         byte[] data = new byte[1];
         char[] balance = new char[20];
         
        // int counter =0;
         boolean flag = false;
         data[0] = 'a';
         counter = 0;
         while (data[0] != '\n') {
                int res;
                res = file.read(data);
                if (res == -1) {
                    flag = true;
                    break;
                }
            }
        while ((char) data[0] != ':') {
            int res;
            res = file.read(data);
            if (res == -1) {
                flag = true;
                break;
            }
        }
        int res = file.read(data);
        offsetofnumber = file.getFilePointer();
        do {
            res = file.read(data);
            if (res == -1) {
                flag = true;
                break;
            }
            balance[counter] = (char) data[0];
            counter++;
        } while (Character.isDigit((char) data[0]) || (char)data[0] == '-');
     //   System.out.println((char)data[0]);
            int oldnumber = Integer.parseInt(String.valueOf(balance, 0, counter - 1));
       //     System.out.println(oldnumber);
            return oldnumber;
            

    }
    
    private void updateFile(int newnumber){
        try {
            
            file.seek(offsetofnumber);
         //   System.out.println(String.valueOf(newnumber));
            file.write(String.valueOf(newnumber).getBytes());
            if (String.valueOf(newnumber).length() < counter - 1){
             
                for (int i=0; i<counter-String.valueOf(newnumber).length(); i++){
                  
                    file.write((new String(" ")).getBytes());
                }
            }
            file.write((new String("\n")).getBytes());
   
            } catch (IOException ex) {
                Logger.getLogger(FinancialTransaction.class.getName()).log(Level.SEVERE, null, ex);
            }
    }*/

}
