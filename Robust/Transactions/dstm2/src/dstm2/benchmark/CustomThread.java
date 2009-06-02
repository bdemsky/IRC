/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dstm2.benchmark;

import TransactionalIO.Utilities.Range;
import TransactionalIO.interfaces.IOOperations;
import TransactionalIO.core.MyDefaults;
import TransactionalIO.core.ExtendedTransaction;
import TransactionalIO.core.TransactionLocalFileAttributes;
import TransactionalIO.core.TransactionalFile;
import TransactionalIO.core.Wrapper;
import TransactionalIO.core.WriteOperations;
import TransactionalIO.exceptions.GracefulException;
import dstm2.SpecialTransactionalFile;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Iterator;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import dstm2.Thread;
import dstm2.atomic;
import dstm2.AtomicArray;
import dstm2.Defaults;
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
    private String benchmark;
    int thresh; 
    boolean inevitable = Defaults.INEVITABLE;
    int size;
    long threadoffset;
    static int version = 0;
    static int occurence = 0;


    ///////////////////PureIO data structure//////////////////////
    
    static TransactionalFile[] pureiofiles;
    static SpecialTransactionalFile[] inevitablepureiofiles;

    ///////////////////financialbenchmark data structure//////////////////
    String[] stocks;
    HashMap financialbm1;
    HashMap financialbm2;
    String st = new String();
    byte[] financedata = new byte[11];
    char[] balance = new char[20];	
    byte[] financedata2 = new byte[41];
    TransactionalFile accountbalance=null; 
    SpecialTransactionalFile inevitableaccountbalance=null; 
    static AtomicArray<FTrHolder> financialTransactionKeeper;
    private static Factory<FinancialTransactionDS> factory = Thread.makeFactory(FinancialTransactionDS.class);
    private static Factory<RootHolder> factory2 = Thread.makeFactory(RootHolder.class);
    private static Factory<FTrHolder> factory3 = Thread.makeFactory(FTrHolder.class);
    //////////////////////////////////////////////////////////////////////


    ///////////////////counter data structure//////////////////////////////
    static TransactionalFile counterfile;
    static SpecialTransactionalFile inevitablecounterfile;
    static Factory<CountKeeper> counterfactory = Thread.makeFactory(CountKeeper.class);
    private static CountKeeper word1_occurence;
    private static CountKeeper word2_occurence;
    private static CountKeeper word3_occurence;
    private static CountKeeper word4_occurence;
    private static CountKeeper word5_occurence;
    public static RandomAccessFile globallocktestfile;
    public static SpecialTransactionalFile inevitabletestfile;
    public static TransactionalFile outoftransactiontestfile;
    public static Object lock = new Object();
    ///////////////////////////////////////////////////////////////////////

    public CustomThread(String benchmark, int versionwanted) {
        
        this.benchmark = benchmark;
	version = versionwanted;
   
        if (benchmark.equals("dstm2.benchmark.ParralelGrep")) {
		initParralelGrep();
        } else if (benchmark.equals("dstm2.benchmark.FinancialTransaction")) {
		intiFinance();
        } else if (benchmark.equals("dstm2.benchmark.ParralelSort")) {
		initPureIO();
	}
        /*}  else if (benchmark.equals("dstm2.benchmark.PureIOInevitablev2")) {
		initPureIO();
        } else if (benchmark.equals("dstm2.benchmark.PureIOTransactionalv2")) {
		initPureIO();
	}*/

        thread = new Thread(this);
   }
   


    public int dummy(int j){
        int result = 0 ;
	for (int i=0; i<10000;i++){
		result+=i*i;
		for (int k=0; k<10;k++){
			result+=k*i+j;
		}
	}
	return result;
    }
   
    public int lock(RandomAccessFile g, byte[] res){

					try{
						g.read(res);	
						int dum = 0;
						for(int j=0;j<5;j++){
							dum += dummy(dum+j); 
						}
						g.read(res);	
						return dum;
					}catch(IOException e){
						e.printStackTrace();
						return -1;
					}

    }
    
    public int lock(IOOperations g, byte[] res){

					try{
						g.read(res);	
						int dum = 0;
						for(int j=0;j<5;j++){
							dum += dummy(dum+j); 
						}
						g.read(res);	
						return dum;
					}catch(IOException e){
						e.printStackTrace();
						return -1;
					}
    }
    
    public void GlobalLockTest(){
        byte[] res = new byte[1023];
	for (int l=0; l<1023;l++)
		res[l]=(byte)'a';
	try{
		RandomAccessFile g = new RandomAccessFile("/scratch/TransactionalIO/test","rw"); 
        	long toseek = (Integer.valueOf(Thread.currentThread().getName().substring(7))) *4000;
		for (int i=0; i<2000;i++){
			g.seek(toseek);
			synchronized(lock){
				int resu = lock(g, res);
				/*	try{
						g.write(res);	
						int dum = 0;
						for(int j=0;j<5;j++){
							dum += dummy(dum+j); 
						}
						g.write(res);	
					}catch(IOException e){
						e.printStackTrace();
					}*/
			}	
		}
	}catch(IOException e){
		e.printStackTrace();
	}
    }

    public void NonTest(){
         final byte[] res = new byte[1023];
	for (int l=0; l<1023;l++)
		res[l]=(byte)'a';
	try{
		final RandomAccessFile g = new RandomAccessFile("/scratch/TransactionalIO/test","rw"); 
        	long toseek = (Integer.valueOf(Thread.currentThread().getName().substring(7))) *4000;
		for (int i=0; i<2000;i++){
					g.seek(toseek);
		//	for (int j=0; j<300;j++){
//				try{
					int resu = lock(g, res);
				//	for(int j=0;j<50;j++)
/*						g.write(res);	
						int dum = 0;
						for (int j=0; j<5;j++){
							dum += dummy(dum+j);
						}
						g.write(res);	*/
//				}catch(IOException e){
//					e.printStackTrace();
//				}
		//	}
		}
	}catch(IOException e){
		e.printStackTrace();
	}
    }

    public void InevitableTest(){
        final byte[] res = new byte[1023];
	for (int l=0; l<1023;l++)
		res[l]=(byte)'a';
	try{
		final SpecialTransactionalFile in = new SpecialTransactionalFile("/scratch/TransactionalIO/test","rw"); 
	        final long toseek = (Integer.valueOf(Thread.currentThread().getName().substring(7))) *4000;

		for (int i=0; i<2000;i++){
			in.seek(toseek);
//			for (int j=0; j<300;j++){
				Thread.doIt(new Callable<Boolean>(){
					public Boolean call(){
/*						try{
							in.write(res);
							int dum = 0;
							for (int j=0; j<5;j++){
								dum += dummy(dum+j);
							}
							in.write(res);
						}catch(IOException e){
							e.printStackTrace();
						}
						finally{
							return true;
						}*/
						int resu = lock(in, res);
						return true;
					}
				});
//			}
		}
	}catch(IOException e){
		e.printStackTrace();
	}
    }
    
    public void TransactionalTest(){
        final byte[] res = new byte[1023];
	for (int l=0; l<1023;l++)
		res[l]=(byte)'a';
//	try{
		final TransactionalFile in = new TransactionalFile("/scratch/TransactionalIO/test","rw"); 
        	final long toseek = (Integer.valueOf(Thread.currentThread().getName().substring(7))) *4000;
		try{
			for (int i=0; i<2000;i++){
				in.seek(toseek);
			//for (int j=0; j<30;j++){
		//		long st = System.currentTimeMillis();
				Thread.doIt(new Callable<Boolean>(){
					public Boolean call(){
						/*try{
							in.write(res);
							int dum = 0;
							for (int j=0; j<5;j++){
								dum += dummy(dum+j);
							}
							in.write(res);
						}
						catch(IOException e){
							e.printStackTrace();
						}
						finally{
							return true;
						}*/
						int resu = lock(in, res);
							return true;
					}
				});
		//		long sp = System.currentTimeMillis();
		//		System.out.println("aaaa  " + (sp-st));
		//	}
		}
	//	}
	}catch(IOException e){
		e.printStackTrace();
	}
    }




    public void intiFinance()
    {
	
		try{
			if (version == 0){
		        	if (inevitable)	
					inevitableaccountbalance= new SpecialTransactionalFile("/scratch/TransactionalIO/FinancialTransactionFilesv0/accountbalance.txt","rw");
				else 
					accountbalance= new TransactionalFile("/scratch/TransactionalIO/FinancialTransactionFilesv0/accountbalance.txt","rw");
			}
			else if (version == 1){
		        	if (inevitable)	
					inevitableaccountbalance= new SpecialTransactionalFile("/scratch/TransactionalIO/FinancialTransactionFilesv1/accountbalance.txt","rw");
				else 
					accountbalance= new TransactionalFile("/scratch/TransactionalIO/FinancialTransactionFilesv1/accountbalance.txt","rw");
			}

		}catch(IOException ex){
			ex.printStackTrace();
		}
	
		preparenamelist();	
        if (occurence == 0){

		occurence++;
		RootHolder ck = factory2.create();
		ck.setFinancialTransactionKeeper(new AtomicArray<FTrHolder>(FTrHolder.class, 20));

		financialTransactionKeeper = ck.getFinancialTransactionKeeper();
		for (int i=0; i<20; i++){
			FTrHolder f1 = factory3.create();
			f1.setCounter(1);
			f1.setFinancialTransactionKeeper(new AtomicArray<FinancialTransactionDS>(FinancialTransactionDS.class, 5));
			for (int j=0; j<5; j++) {
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
    }
   
    public void initPureIO(){
	if (occurence == 0){
		occurence++;
		try{
			if (inevitable){
				inevitablepureiofiles = new SpecialTransactionalFile[26];
				for (int i=0; i<26; i++){
					int j= i+ 'a';
					if (version == 0)
					        inevitablepureiofiles[i] = new SpecialTransactionalFile("/scratch/TransactionalIO/ParallelSortFilesv0/"+String.valueOf((char) (97+i))+ ".text","rw");
					else if (version == 1)
					        inevitablepureiofiles[i] = new SpecialTransactionalFile("/scratch/TransactionalIO/ParallelSortFilesv1/"+String.valueOf((char) (97+i))+ ".text","rw");
				}
			}
			else{
				pureiofiles = new TransactionalFile[26];
				for (int i=0; i<26; i++){
					int j= i+ 'a';
					if (version == 0)
				        	pureiofiles[i] = new TransactionalFile("/scratch/TransactionalIO/ParallelSortFilesv0/"+String.valueOf((char) (97+i))+ ".text","rw");
					else if (version == 1)
				        	pureiofiles[i] = new TransactionalFile("/scratch/TransactionalIO/ParallelSortFilesv1/"+String.valueOf((char) (97+i))+ ".text","rw");
				}	
			}
       
		}catch(IOException ex){	
			ex.printStackTrace();
		}
	}
    }




    public void initParralelGrep(){
	if (occurence == 0){
		occurence++;
            int count = 0;

	    word1_occurence = counterfactory.create();
	    word1_occurence.setOccurence(0);
              
            word2_occurence = counterfactory.create();
       	    word2_occurence.setOccurence(0);
              
            word3_occurence = counterfactory.create();
            word3_occurence.setOccurence(0);
              
            word4_occurence = counterfactory.create();
            word4_occurence.setOccurence(0);
           
            word5_occurence = counterfactory.create();
            word5_occurence.setOccurence(0);

	    if (version ==0){
	    	if (inevitable){
		    try {
		            inevitablecounterfile = new SpecialTransactionalFile("/scratch/TransactionalIO/ParallelGrepFilesv0/output.text", "rw");
        	   } catch (FileNotFoundException ex) {
		            Logger.getLogger(CustomThread.class.getName()).log(Level.SEVERE, null, ex);
            	   }		   
	    	}  
	    	else 
		    counterfile = new TransactionalFile("/scratch/TransactionalIO/ParallelGrepFilesv0/output.text", "rw");
	   }
	  else  if (version ==1){
	    if (inevitable){
		    try {
		            inevitablecounterfile = new SpecialTransactionalFile("/scratch/TransactionalIO/ParallelGrepFilesv1/output.text", "rw");
	            } catch (FileNotFoundException ex) {
		            Logger.getLogger(CustomThread.class.getName()).log(Level.SEVERE, null, ex);
		    }
		}
		    else 
		    	counterfile = new TransactionalFile("/scratch/TransactionalIO/ParallelGrepFilesv1/output.text", "rw");
	
    	 }
	}
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

        if (benchmark.equals("GlobalLockTest")) {
            GlobalLockTest();
	}
        else if (benchmark.equals("NonTest")) {
            NonTest();
	}
        else if (benchmark.equals("InevitableTest")) {
            InevitableTest();
	}
        else if (benchmark.equals("TransactionalTest")) {
            TransactionalTest();
	}
        else if (benchmark.equals("dstm2.benchmark.ParralelGrep")) {
            parralelGrepBenchmark();
        } else if (benchmark.equals("dstm2.benchmark.FinancialTransaction")) {
            financialBenchmark();
        } else if (benchmark.equals("dstm2.benchmark.ParralelSort")) {
            pureIOBenchmark();
        }
	else
		System.out.println("No Such Benchmark");
    /*    } else if (benchmark.equals("dstm2.benchmark.PureIOInevitablev2")) {
            pureIOInevitablev2();
        } else if (benchmark.equals("dstm2.benchmark.PureIOTransactionalv2")) {
            pureIOTransactionalv2();
        } */
    }

    public void pureIOBenchmark() {
        try {
            RandomAccessFile f1;
            if (version == 0)
	            f1 = new RandomAccessFile("/scratch/TransactionalIO/ParallelSortFilesv0/randomwords.text", "rw");
	    else 
	            f1 = new RandomAccessFile("/scratch/TransactionalIO/ParallelSortFilesv1/randomwords.text", "rw");

            long toseek;
            long threadoffset;
            threadoffset = 51121;
            toseek =
                    (Integer.valueOf(Thread.currentThread().getName().substring(7))) * threadoffset;

            for (int i=0; i<10; i++){
	            f1.seek(toseek);
        	    if (toseek != 0) {
                	//////////////// skipt the first word since its been read already
		        f1.readLine();
        	    }
	           while (f1.getFilePointer() < toseek + threadoffset) {
        	        final String pureiodata = f1.readLine();
                	if (pureiodata == null) {
	                    break;
        	        }
                	boolean resultt = Thread.doIt(new Callable<Boolean>() {

	                    public Boolean call() {
				pureioexecute(pureiodata);
                	        return true;
	                    }
        	        });

	            }
	   }
        }catch (IOException ex) {
        	    Logger.getLogger(CustomThread.class.getName()).log(Level.SEVERE, null, ex);
	}

    }



    public void financialBenchmark() {
      try {
	    RandomAccessFile f1;
	    if (version == 0)
	    	f1 = new RandomAccessFile("/scratch/TransactionalIO/FinancialTransactionFilesv0/financialtransaction.text", "rw");
	    else 
	    	f1 = new RandomAccessFile("/scratch/TransactionalIO/FinancialTransactionFilesv1/financialtransaction.text", "rw");

            String data = new String();
            int counter = 0;
            long toseek;
            long threadoffset = 360611*2;
            toseek = (Integer.valueOf(Thread.currentThread().getName().substring(7))) * threadoffset; 
            f1.seek(toseek);
            if (toseek != 0) {
                //////////////// skipt the first line since its been read already            
		data = f1.readLine();
		if (data == null)
			return;
            }

            while (f1.getFilePointer() < toseek + threadoffset) {

                counter = 0;
		data = f1.readLine();

                if (data == null) 
	            break;

                while (data.charAt(counter) != ' ') {
                    counter++;
                }

                final String oldowner = data.substring(0, counter);
		counter++;
	        int counter2 = counter;
                while (data.charAt(counter) != ' ') {
                    counter++;
                }


                final int exchange = Integer.parseInt(data.substring(counter2, counter ));

		counter++;
                counter2 = counter;

                while (data.charAt(counter) != ' ') {
                    counter++;
                }

                final String newowner = data.substring(counter2, counter);

		counter++;
                counter2 = counter;
                for (int j=counter; j<data.length(); j++) {
                    counter++;
                }

                final String company = data.substring(counter2, counter);
                boolean result = Thread.doIt(new Callable<Boolean>() {
                    public Boolean call() {
                        financialexecute(oldowner, exchange, newowner, company);
                        return true;
                    }
                });
            }


        } catch (IOException ex) {
           Logger.getLogger(CustomThread.class.getName()).log(Level.SEVERE, null, ex);
        }

    }


    public void parralelGrepBenchmark() {
        try {

	    final IOOperations f1;
	    if (!(inevitable)){
		if (version == 0)
	            	f1 = new TransactionalFile("/scratch/TransactionalIO/ParallelGrepFilesv0/iliad.text", "rw");
		else
	            	f1 = new TransactionalFile("/scratch/TransactionalIO/ParallelGrepFilesv1/iliad.text", "rw");
	    }
	    else{
		if (version == 0)
	            	f1 = new SpecialTransactionalFile("/scratch/TransactionalIO/ParallelGrepFilesv0/iliad.text", "rw");
		else
	            	f1 = new SpecialTransactionalFile("/scratch/TransactionalIO/ParallelGrepFilesv1/iliad.text", "rw");
	    }
            final long threadoffset;
            final long toseek;
	    final byte[] data = new byte[1000];
            //threadoffset = 52921;
            threadoffset = 50000;
            toseek = Integer.valueOf(Thread.currentThread().getName().substring(7)) * threadoffset;
	    for (int m=0; m<3000; m++){	
	    f1.seek(toseek);
	    for (int l=0; l<2; l++){
		    Thread.doIt (new Callable<Boolean>(){
				public Boolean call(){	
					try{
					   	for (int k=0; k<25; k++){	
					    		int end=0;
							int start = 0;
						        long offset = 0; 	
							int result = f1.read(data);
							if (result==-1)
								return false;
							while (end<data.length){
								start = end;
							while(!(Character.isLetter((char)data[end]))){
									start++;
									end++;
								}
								while(Character.isLetter((char)data[end])){
									
									end++;
								}
								int index_of_object = processInput(new String(data, start, end-start));
								if (index_of_object == -1)
									continue;

						               if (inevitable){
									String tmp = "\n\n Found word " + new String(data, start, end-start) + " At offset: " + offset +"\n\n";
                		                	                inevitablecounterfile.write(tmp.getBytes());
                			                                inevitablecounterfile.write(data, 0, end);
							       }
			                                       else{
									String tmp = "\n\n Found word " + new String(data, start, end-start) + " At offset: " + offset + "\n\n";
                			                                counterfile.write(tmp.getBytes());
                			                                counterfile.write(data, 0, end);
							       }
		                                       		switch(index_of_object){
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
							    	}
							}
						}
	        			} catch (IOException ex) {
					         Logger.getLogger(CustomThread.class.getName()).log(Level.SEVERE, null, ex);
					}
					finally {
						return true;
					}
				 }
			});
		}
		}

        } catch (IOException ex) {
            Logger.getLogger(CustomThread.class.getName()).log(Level.SEVERE, null, ex);
        }

    }



    private long financialcomputeandupdate(boolean type, String oldowner, int stocktrade, String newowner, String nameofstock) {
        try {

            int counter = 0;
	    if (inevitable)
	          inevitableaccountbalance.read(financedata);
            else 
	          accountbalance.read(financedata);
            int adad;
            for (adad = 0; adad < stocks.length; adad++) {
                if (stocks[adad].equalsIgnoreCase(nameofstock)) {
                    break;
                }
            }
	    if (inevitable){
	            inevitableaccountbalance.skipBytes(adad * 41);
        	    inevitableaccountbalance.read(financedata2);
	    }
	    else{
                    accountbalance.skipBytes(adad * 41);
	            accountbalance.read(financedata2);
	    }

            int i = 0;
            while ((char) financedata2[i] != ':') {
                i++;
            }

            i++;
            long offsettowrite;


	    if (inevitable)
            	 offsettowrite = inevitableaccountbalance.getFilePointer();
            else
            	 offsettowrite = accountbalance.getFilePointer();
		 
            offsettowrite += i - 40;

            do {
                i++;
                balance[counter] = (char) financedata2[i];
                counter++;
            } while (Character.isDigit((char) financedata2[i]) || (char) financedata2[i] == '-');

            int oldbalance = Integer.parseInt(String.valueOf(balance, 0, counter - 1));
            int newnumber;
            if (type) {
                newnumber = oldbalance - stocktrade;
            } else {
                newnumber = oldbalance + stocktrade;

            }
            st = String.valueOf(newnumber);
            if (st.length() < counter - 1) {

                for (int j = 0; j < counter - String.valueOf(newnumber).length(); j++) {
                    st += new String(" ");
                }
            }

            return offsettowrite;


        } catch (IOException ex) {
            Logger.getLogger(CustomThread.class.getName()).log(Level.SEVERE, null, ex);
            return -1;
        }

    }
   

    public void pureioexecute(String str){
	try{
	
		int i = str.toLowerCase().charAt(0) - 'a';
		if (inevitable)
			inevitablepureiofiles[i].write(str.getBytes());
		else
			pureiofiles[i].write(str.getBytes());

	}catch(IOException e){
		e.printStackTrace();
	}
    }



    static Integer  processInput(String str) {
            if (str.equalsIgnoreCase("Polydamas")) {
                return 0;
            }
            else if (str.equalsIgnoreCase("Cebriones")) {
                return 1;
            }
            else if (str.equalsIgnoreCase("Eurybates")) {
                return 2;
            }
            else if (str.equalsIgnoreCase("Menoetius")) {
                return 3;
            }
            else if (str.equalsIgnoreCase("countless")) {
                return 4;
            }
        return -1;
   }


	
private void preparenamelist() {
        try {
            byte[] data = new byte[1];
            char[] name = new char[20];
            RandomAccessFile file = new RandomAccessFile("/scratch/TransactionalIO/FinancialTransactionFilesv0/namelist.text", "rw");
            RandomAccessFile file3 = new RandomAccessFile("/scratch/TransactionalIO/FinancialTransactionFilesv0/accountbalance.text", "rw");

            stocks = new String[20];
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

            boolean flag = false;
            boolean done = false;
            int wordcounter = 0;
            int counter = 0;
            financialbm1 = new HashMap();
            financialbm2 = new HashMap();

            while (true) {
                if (flag) {
                    break;
                }
                if (done) {
                    financialbm1.put(Integer.valueOf(wordcounter), String.copyValueOf(name, 0, counter));
                    financialbm2.put(String.copyValueOf(name, 0, counter), Integer.valueOf(wordcounter));
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
                    if (!(Character.isLetter((char) data[0]))) {
                        continue;
                    }
                    name[counter] = (char) data[0];
                    done = true;
                    counter++;
                }
            }



///preparing the accountbalance file/////////
/*
            for (int i = 0; i < 50; i++) {
                String towrite = (String) financialbm1.get(Integer.valueOf(i));
                String tmpst = (String) financialbm2.get(Integer.valueOf(i));
                System.out.println("Ss " + tmpst);
                int tmp = tmpst.length();
                while (tmp < 10) {
                    towrite += new String(" ");
                    tmp++;
                }
                towrite += "\n";
                for (int j = 0; j < stocks.length; j++) {
                    tmpst = stocks[j] + " Stock Balance: " + ((int) (Math.random() * 100 + 100));
                    towrite += stocks[j] + " Stock Balance: " + ((int) (Math.random() * 100 + 100));
                    tmp = tmpst.length();
                    while (tmp < 40) {
                        towrite += new String(" ");
                        tmp++;
                    }
                    towrite += "\n";
                }

                file3.write(towrite.getBytes());
                while (file3.getFilePointer() < (i + 1) * MyDefaults.FILEFRAGMENTSIZE) {
                    file3.write(new String(" ").getBytes());
                }
            }

////////////////////////////////////////////
*/
            file.close();
            file3.close();
        } catch (IOException ex) {
            Logger.getLogger(CustomThread.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

  
       
    protected void financialexecute(String oldowner, int stocktrade, String newowner, String nameofstock) {
       try {
              Integer offset1 = (Integer) financialbm2.get(oldowner);
              Integer offset2 = (Integer) financialbm2.get(newowner);

	      if (inevitable)	
	              inevitableaccountbalance.seek(offset1 * MyDefaults.FILEFRAGMENTSIZE);
	      else
	              accountbalance.seek(offset1 * MyDefaults.FILEFRAGMENTSIZE);

              long offsettowrite = financialcomputeandupdate(true, oldowner, stocktrade, newowner, nameofstock);
	      if (inevitable){
	              inevitableaccountbalance.seek(offsettowrite);
        	      inevitableaccountbalance.write(st.getBytes());
	      }
	      else {
	              accountbalance.seek(offsettowrite);
        	      accountbalance.write(st.getBytes());
	      }


	      if (inevitable)
	              inevitableaccountbalance.seek(offset2 * MyDefaults.FILEFRAGMENTSIZE);
	      else 
	              accountbalance.seek(offset2 * MyDefaults.FILEFRAGMENTSIZE);

              offsettowrite = financialcomputeandupdate(false, oldowner, stocktrade, newowner, nameofstock);
		
	    
	      if (inevitable){
        	      inevitableaccountbalance.seek(offsettowrite);
	              inevitableaccountbalance.write(st.getBytes());
	      }
	      else {
        	      accountbalance.seek(offsettowrite);
	              accountbalance.write(st.getBytes());
	      }

            int i;
            for (i = 0; i < stocks.length; i++) {
                if (stocks[i].equalsIgnoreCase(nameofstock)) {
                    break;
                }
            }
            switch (financialTransactionKeeper.get(i).getCounter()) {
                case 1:
                    financialTransactionKeeper.get(i).getFinancialTransactionKeeper().get(0).setSeller(oldowner);
                    financialTransactionKeeper.get(i).getFinancialTransactionKeeper().get(0).setSoldShare(stocktrade);
                    financialTransactionKeeper.get(i).getFinancialTransactionKeeper().get(0).setBuyer(newowner);
                    financialTransactionKeeper.get(i).setCounter(2);
                    break;
                case 2:
                    financialTransactionKeeper.get(i).getFinancialTransactionKeeper().get(1).setSeller(oldowner);
                    financialTransactionKeeper.get(i).getFinancialTransactionKeeper().get(1).setSoldShare(stocktrade);
                    financialTransactionKeeper.get(i).getFinancialTransactionKeeper().get(1).setBuyer(newowner);
                    financialTransactionKeeper.get(i).setCounter(3);
                    break;
                case 3:
                    financialTransactionKeeper.get(i).getFinancialTransactionKeeper().get(2).setSeller(oldowner);
                    financialTransactionKeeper.get(i).getFinancialTransactionKeeper().get(2).setSoldShare(stocktrade);
                    financialTransactionKeeper.get(i).getFinancialTransactionKeeper().get(2).setBuyer(newowner);
                    financialTransactionKeeper.get(i).setCounter(4);
                    break;
                case 4:
                    financialTransactionKeeper.get(i).getFinancialTransactionKeeper().get(3).setSeller(oldowner);
                    financialTransactionKeeper.get(i).getFinancialTransactionKeeper().get(3).setSoldShare(stocktrade);
                    financialTransactionKeeper.get(i).getFinancialTransactionKeeper().get(3).setBuyer(newowner);
                    financialTransactionKeeper.get(i).setCounter(5);
                    break;
                case 5:
                    financialTransactionKeeper.get(i).getFinancialTransactionKeeper().get(4).setSeller(oldowner);
                    financialTransactionKeeper.get(i).getFinancialTransactionKeeper().get(4).setSoldShare(stocktrade);
                    financialTransactionKeeper.get(i).getFinancialTransactionKeeper().get(4).setBuyer(newowner);
                    financialTransactionKeeper.get(i).setCounter(1);
                    break;
            }

        } catch (IOException ex) {
            Logger.getLogger(CustomThread.class.getName()).log(Level.SEVERE, null, ex);
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

    }

    @atomic
    public interface CountKeeper{
        int getOccurence();
        void setOccurence(int value);;
    }
}

/*    public void pureIOInevitablev2() {
        try {

            final SpecialTransactionalFile f1;
            f1 = new SpecialTransactionalFile("/scratch/TransactionalIO/PureIOBenchmarkFiles/randomwords.text", "rw");
            final long toseek;
            final long threadoffset = 51121;
            toseek = (Integer.valueOf(Thread.currentThread().getName().substring(7))) * threadoffset;
            for (int l=0; l<10; l++){
                f1.seek(toseek);        
                if (toseek !=0)
                        f1.readLine();
                while (f1.getFilePointer() < toseek + threadoffset) {
                        boolean resultt = Thread.doIt(new Callable<Boolean>() {
                                public Boolean call() {
                                    try{
                                                for (int k=0; k<1000; k++){     
                                                        if (f1.getFilePointer() < toseek + threadoffset){       
                                                                
                                                                String str = f1.readLine();
                                                                if (str == null)
                                                                        return true;
                                                                int i = Character.toLowerCase((char)str.charAt(0)) - 'a';
                                                                inevitablepureiofiles[i].write(str.getBytes());
                                                        }
                                                        else
                                                                return true;
                                                
                                                }
                                
                                             return true;

                                      } catch (IOException ex) {
                                            System.exit(0);
                                            Logger.getLogger(CustomThread.class.getName()).log(Level.SEVERE, null, ex);
                                    return false;
                                  }
                               }
                        });
                }
        } 
      } catch (IOException ex) {
            Logger.getLogger(CustomThread.class.getName()).log(Level.SEVERE, null, ex);
      }
    }

    public void pureIOTransactionalv2() {
        try {
            final TransactionalFile f1;
            f1 = new TransactionalFile("/scratch/TransactionalIO/PureIOBenchmarkFiles/randomwords.text", "rw");
            final long toseek;
            final long threadoffset = 51121;
            toseek = (Integer.valueOf(Thread.currentThread().getName().substring(7))) * threadoffset;
            for (int l=0; l<10; l++){
                   f1.seek(toseek);
                   if (toseek !=0)
                        f1.readLine();
                   while (f1.getFilePointer() < toseek + threadoffset) {
                        boolean resultt = Thread.doIt(new Callable<Boolean>() {
                                public Boolean call() {
                                        try{
                                                        for (int k=0; k<1000; k++){     
                                                                if (f1.getFilePointer() < toseek + threadoffset){       
                                                                        String str = f1.readLine();
                                                                        if (str == null)
                                                                                return true;
                                                                        int i = Character.toLowerCase(str.charAt(0)) - 'a';
                                                                        pureiofiles[i].write(str.getBytes());
                                                                }
                                                                else {
                                                                        return true;
                                                                }
                                                      }
                                              return true;
                                        } catch (IOException ex) {
                                            Logger.getLogger(CustomThread.class.getName()).log(Level.SEVERE, null, ex);
                                            return true;
                                        }
                                }
                        });
                   }

                         
            }
                    
        } catch (IOException ex) {
            Logger.getLogger(CustomThread.class.getName()).log(Level.SEVERE, null, ex);
        }
   }*/
