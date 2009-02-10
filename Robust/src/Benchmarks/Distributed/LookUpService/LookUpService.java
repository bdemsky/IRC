public class LookUpService extends Thread {
  DistributedHashMap mydhmap;
  /**
   * The thread id involved 
   **/
  private int threadid;
  /**
   * The total number of threads
   **/
  private int numthreads;

  /**
   * The total number of transactions 
   **/
  private int numtrans;

  /**
   * The total number of objects created
   **/
  private int nobjs;

  /**
   * The probability of initiating a look up
   * the read probability % between 0-99
   **/
  private int rdprob;

  public LookUpService() {
  }

  public LookUpService(DistributedHashMap dmap, int threadid, int numthreads, int nobjs, int numtrans, int rdprob) {
    mydhmap = dmap;
    this.threadid = threadid;
    this.numthreads = numthreads;
    this.nobjs = nobjs;
    this.numtrans = numtrans;
    this.rdprob = rdprob;
  }

  public void run() {
    Barrier barr;
    barr = new Barrier("128.195.136.162");
    //Add to the hash map
    int ntrans;
    atomic {
      for(int i = 0; i < nobjs; i++) {
        Integer key = global new Integer(threadid*nobjs+i);
        Integer val = global new Integer(i);
        Object o1 = key;
        Object o2 = val;
        mydhmap.put(o1, o2);
      }
      ntrans = numtrans;
    }
    Barrier.enterBarrier(barr);
    // Do read/writes
    for (int i = 0; i < ntrans; i++) {
      atomic {
        Random rand = new Random(i);
        int rdwr = rand.nextInt(100);
        int rwkey = rand.nextInt(nobjs*numthreads);
        Integer key = global new Integer(rwkey);
        Object o1 = key;
        if (rdwr < rdprob) {
          Object o3 = mydhmap.get(o1); //Read
        } else {
          Integer val = global new Integer(i);
          Object o2 = val;
          mydhmap.put(o1, o2); //Modify 
        }
      }
    }
  }

  public static void main(String[] args) {
    LookUpService ls = new LookUpService();
    LookUpService.parseCmdLine(args,ls);
    BarrierServer mybarr;

    int nthreads = ls.numthreads;
    int[] mid = new int[8];
    mid[0] = (128<<24)|(195<<16)|(136<<8)|162;//dc-1
    mid[1] = (128<<24)|(195<<16)|(136<<8)|163;//dc-2
    mid[2] = (128<<24)|(195<<16)|(136<<8)|164;//dc-3
    mid[3] = (128<<24)|(195<<16)|(136<<8)|165;//dc-4
    mid[4] = (128<<24)|(195<<16)|(136<<8)|166;//dc-5
    mid[5] = (128<<24)|(195<<16)|(136<<8)|167;//dc-6
    mid[6] = (128<<24)|(195<<16)|(136<<8)|168;//dc-7
    mid[7] = (128<<24)|(195<<16)|(136<<8)|169;//dc-8

    LookUpService[] lus;
    DistributedHashMap dhmap;
    atomic {
      mybarr = global new BarrierServer(nthreads);
    }
    
    mybarr.start(mid[0]);
    atomic {
      dhmap = global new DistributedHashMap(100, 100, 0.75f);
      lus = global new LookUpService[nthreads];
      for(int i = 0; i<nthreads; i++) {
        lus[i] = global new LookUpService(dhmap, i, ls.numthreads, ls.nobjs, ls.numtrans, ls.rdprob);
      }
    }

    boolean waitfordone=true;
    while(waitfordone) {
      atomic {  //Master aborts are from here
        if (mybarr.done)
          waitfordone=false;
      }
    }

    LookUpService tmp;
    /* Start threads */
    for(int i = 0; i<nthreads; i++) {
      atomic {
        tmp = lus[i];
      }
      tmp.start(mid[i]);
    }

    /* Join threads */
    for(int i = 0; i<nthreads; i++) {
      atomic {
        tmp = lus[i];
      }
      tmp.join();
    }

    System.printString("Finished\n");
  }

  /**
   * Parse the command line options.
   **/
  public static void parseCmdLine(String args[], LookUpService lus) {
    int i = 0;
    String arg;
    while(i < args.length && args[i].startsWith("-")) {
      arg = args[i++];
      //check options
      if(arg.equals("-N")) {
        if(i < args.length) {
          lus.numthreads = new Integer(args[i++]).intValue();
        }
      } else if(arg.equals("-nEntry")) {
        if(i < args.length) {
          lus.nobjs = new Integer(args[i++]).intValue();
        }
      } else if (arg.equals("-nTrans")) {
        if(i < args.length) {
          lus.numtrans =  new Integer(args[i++]).intValue();
        }
      } else if(arg.equals("-probRead")) {
        if(i < args.length) {
          lus.rdprob = new Integer(args[i++]).intValue();
        }
      } else if(arg.equals("-h")) {
        lus.usage();
      }
    }

    if(lus.nobjs == 0  || lus.numtrans == 0)
      lus.usage();
  }

  /**
   * The usage routine which describes the program options.
   **/
  public void usage() {
    System.printString("usage: ./LookUpServiceN.bin master -N <threads> -nEntry <objects in hashmap> -nTrans <number of transactions> -probRead <read probability> \n");
    System.printString("    -N the number of threads\n");
    System.printString("    -nEntry the number of objects to be inserted into distributed hashmap\n");
    System.printString("    -nTrans the number of transactions to run\n");
    System.printString("    -probRead the probability of read given a transaction\n");
    System.printString("    -h help with usage\n");
  }
}
