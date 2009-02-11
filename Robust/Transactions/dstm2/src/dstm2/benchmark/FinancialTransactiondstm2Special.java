/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dstm2.benchmark;

import TransactionalIO.benchmarks.benchmark;
import TransactionalIO.core.Defaults;
import dstm2.AtomicArray;
import dstm2.SpecialTransactionalFile;
import dstm2.atomic;
import dstm2.Thread;
import dstm2.factory.Factory;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author navid
 */
public class FinancialTransactiondstm2Special extends CustomBenchmark {

    //private SpecialTransactionalFile file;
   // private SpecialTransactionalFile file2;
    private static Factory<FinancialTransactionDS> factory = Thread.makeFactory(FinancialTransactionDS.class);
    private static Factory<RootHolder> factory2 = Thread.makeFactory(RootHolder.class);
    private static Factory<FTrHolder> factory3 = Thread.makeFactory(FTrHolder.class);
    AtomicArray<FTrHolder> financialTransactionKeeper;

    protected void init() {
       // try {
            
            //file = new SpecialTransactionalFile("/scratch/TransactionalIO/FinancialTransactionBenchmarkFiles/accountbalance.text", "rw");
            //file2 = new SpecialTransactionalFile("/scratch/TransactionalIO/FinancialTransactionBenchmarkFiles/financialtransactionlog.text", "rw");

            RootHolder ck = factory2.create();
            ck.setFinancialTransactionKeeper(new AtomicArray<FTrHolder>(FTrHolder.class, 20));


            financialTransactionKeeper = ck.getFinancialTransactionKeeper();
            for (int i = 0; i < 20; i++) {

                FTrHolder f1 = factory3.create();
                f1.setCounter(1);
                f1.setFinancialTransactionKeeper(new AtomicArray<FinancialTransactionDS>(FinancialTransactionDS.class, 5));
                for (int j = 0; j < 5; j++) {
                    FinancialTransactionDS ftk = factory.create();
                    ftk.setBuyer("");
                    ftk.setSeller("");
                    ftk.setSoldShare(0);
                    AtomicArray<FinancialTransactionDS> tmp = f1.getFinancialTransactionKeeper();
                    tmp.set(j, ftk);
                }
                financialTransactionKeeper.set(i, f1);
            }
       // } catch (FileNotFoundException ex) {
       //     Logger.getLogger(FinancialTransactiondstm2Special.class.getName()).log(Level.SEVERE, null, ex);
      //  }

    }

    protected void execute(Vector arguments) {
        try {

            String oldowner = (String) arguments.get(0);
            Integer stocktrade = (Integer) arguments.get(1);
            String newowner = (String) arguments.get(2);
            String nameofstock = (String) arguments.get(3);
            SpecialTransactionalFile file = (SpecialTransactionalFile) arguments.get(4);
            Integer offset1 = (Integer) benchmark.m4.get(oldowner);
            Integer offset2 = (Integer) benchmark.m4.get(newowner);
            
            
            
             int i;
            for (i = 0; i < benchmark.stocks.length; i++) {
                if (benchmark.stocks[i].equalsIgnoreCase(nameofstock)) {
                    break;
                }
            }

            switch (financialTransactionKeeper.get(i).getCounter()) {
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

            file.seek(offset1 * Defaults.FILEFRAGMENTSIZE);
            Vector v = computeandupdate(true, stocktrade, nameofstock, file);
            String st = (String) (v.get(1));
            long offset1towrite = ((Long) (v.get(0))).longValue();
            file.seek(offset2 * Defaults.FILEFRAGMENTSIZE);
            v = computeandupdate(false, stocktrade, nameofstock, file);
            String st2 = (String) (v.get(1));
            long offset2towrite = ((Long) (v.get(0))).longValue();


            file.seek(offset1towrite);
            file.write(st.getBytes());
            file.seek(offset2towrite);
            file.write(st2.getBytes());

            String towrite = oldowner + " " + stocktrade.toString() + " " + newowner + " " + nameofstock + " processed\n";
            //file2.write(towrite.getBytes());
      

        } catch (IOException ex) {
            Logger.getLogger(FinancialTransaction.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private Vector computeandupdate(boolean type, Integer stocktrade, String origstockname, SpecialTransactionalFile file) {
      try {
            // try{
            //    RandomAccessFile file = (RandomAccessFile) benchmark.m.get("7");
            //TransactionalFile file = (TransactionalFile) benchmark.m.get("5");
        //    TransactionalFile file = (TransactionalFile) benchmark.m.get("accountbalance");
            Vector v = new Vector();
            byte[] data = new byte[11];
            char[] balance = new char[20];

            int counter =0;
            file.read(data);
            int adad;
            for (adad =0; adad < benchmark.stocks.length; adad++) {
                if (benchmark.stocks[adad].equalsIgnoreCase(origstockname)) {
                    break;
                }
            }
            file.skipBytes(adad*41);
            data = new byte[41];
            file.read(data);
            int i =0;
            while (true) {
                    i = 0;
                    char[] stname = new char[10];
                    int ol = 0;
                   // System.out.println("char " + (char)data[i]);
                    while (data[i] != ' ') {
                            stname[ol] = (char) data[i];
                         //   System.out.println(ol);
                            ol++;
                            i++;
                     
                    }
                   
                    String stockname = String.copyValueOf(stname, 0, ol);
                    if (stockname.equalsIgnoreCase(origstockname)) {
                        break;
                    }
                    else{ 
                        System.out.println("WTF??");
            //            file.read(data);
                    }
            }





            while ((char) data[i] != ':') {
                 i++;
            }

            i++;
            long offsetofnumber = file.getFilePointer();
            offsetofnumber += i-40;
            //file.seek(offsetofnumber);
            //byte[] k = new byte[4];
            //file.read(k);
            //System.out.println("k1 " + (char)k[0]);
            //System.out.println("k2 " + (char)k[1]);
            //System.out.println("k3 " + (char)k[2]);
            //System.out.println("k4 " + (char)k[3]);
            do {
                //System.out.println("d " + (char) data[i]);
                i++;
                balance[counter] = (char) data[i];
                counter++;
            } while (Character.isDigit((char) data[i]) || (char) data[i] == '-');

            int oldbalance = Integer.parseInt(String.valueOf(balance, 0, counter - 1));

            //    return oldnumber;

            int newnumber;
            if (type) {
                newnumber = oldbalance - stocktrade.intValue();
            } else {
                newnumber = oldbalance + stocktrade.intValue();


                //////   file.seek(offsetofnumber);
            }
            String st = new String();
            st = String.valueOf(newnumber);
            if (String.valueOf(newnumber).length() < counter - 1) {

                for (int j = 0; j < counter - String.valueOf(newnumber).length(); j++) {
                    st += (new String(" "));
                }
            }

            v.add(Long.valueOf(offsetofnumber));
            v.add(st);
            return v;

        } catch (IOException ex) {
            Logger.getLogger(FinancialTransaction.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }


    }

    protected void printResults() {

        for (int i = 0; i < 20; i++) {
            System.out.println("----------------------------------------------");
            System.out.println(benchmark.stocks[i]);
            for (int j = 0; j < 5; j++) {
                System.out.print(financialTransactionKeeper.get(i).getFinancialTransactionKeeper().get(j).getSeller() + " ");
                System.out.print(financialTransactionKeeper.get(i).getFinancialTransactionKeeper().get(j).getBuyer() + " ");
                System.out.println(financialTransactionKeeper.get(i).getFinancialTransactionKeeper().get(j).getSoldShare());
            }
            System.out.println("----------------------------------------------");
        }

    }

    @atomic
    public interface FinancialTransactionDS {

        String getSeller();

        void setSeller(String value);

        int getSoldShare();

        void setSoldShare(int value);

        String getBuyer();

        void setBuyer(String value);
    }

    @atomic
    public interface FTrHolder {

        AtomicArray<FinancialTransactionDS> getFinancialTransactionKeeper();

        void setFinancialTransactionKeeper(AtomicArray<FinancialTransactionDS> arr);

        int getCounter();

        void setCounter(int value);
    }

    @atomic
    public interface RootHolder {

        AtomicArray<FTrHolder> getFinancialTransactionKeeper();

        void setFinancialTransactionKeeper(AtomicArray<FTrHolder> arr);
        //  int getCounter();
        //  void setCounter(int value);
    }

    class LockedFinancialTransactionDS {

        public String seller;
        public String buyer;
        public int soldshare;
    }

    class LockedFTrHolder {

        public LockedFinancialTransactionDS[] lk = new LockedFinancialTransactionDS[5];
        public int counter;
    }

}
