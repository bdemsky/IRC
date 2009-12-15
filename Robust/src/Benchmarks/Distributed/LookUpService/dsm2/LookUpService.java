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

  /**
   * The number of look up operations
   **/
  private int nLookUp;

  public LookUpService() {
  }

  public LookUpService(DistributedHashMap dmap, int threadid, int numthreads, int nobjs, int numtrans, int rdprob, int nLookUp) {
    mydhmap = dmap;
    this.threadid = threadid;
    this.numthreads = numthreads;
    this.nobjs = nobjs;
    this.numtrans = numtrans;
    this.rdprob = rdprob;
    this.nLookUp = nLookUp;
  }

  public void run() {
    int ntrans;
    atomic {
      ntrans = numtrans;
    }

    // Do read/writes
    Random rand = new Random(0);
      
    for (int i = 0; i < ntrans; i++) {
      atomic {
        for(int j = 0; j < nLookUp; j++) {
          int rdwr = rand.nextInt(100);
          int rwkey = rand.nextInt(nobjs);
          Integer key = global new Integer(rwkey);
          if (rdwr < rdprob) {
            Integer o3 = (Integer)(mydhmap.get(key)); //Read
          } else {
            Integer val = global new Integer(j);
            mydhmap.put(key, val); //Modify 
          }
        }
      }
    }
  }

  public static void main(String[] args) {
    LookUpService ls = new LookUpService();
    LookUpService.parseCmdLine(args,ls);

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
      dhmap = global new DistributedHashMap(500, 0.75f);
      //Add to the hash map
      for(int i = 0; i < ls.nobjs; i++) {
        Integer key = global new Integer(i);
        Integer val = global new Integer(i*i);
        Object o1 = key;
        Object o2 = val;
        dhmap.put(o1, o2);
      }
      lus = global new LookUpService[nthreads];
      for(int i = 0; i<nthreads; i++) {
        lus[i] = global new LookUpService(dhmap, i, ls.numthreads, ls.nobjs, ls.numtrans, ls.rdprob, ls.nLookUp);
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
      } else if(arg.equals("-nLookUp")) {
        if(i < args.length) {
          lus.nLookUp = new Integer(args[i++]).intValue();
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
    System.printString("usage: ./LookUpServiceN.bin master -N <threads> -nEntry <objects in hashmap> -nTrans <number of transactions> -probRead <read probability> -nLookUp <number of lookups>\n");
    System.printString("    -N the number of threads\n");
    System.printString("    -nEntry the number of objects to be inserted into distributed hashmap\n");
    System.printString("    -nTrans the number of transactions to run\n");
    System.printString("    -probRead the probability of read given a transaction\n");
    System.printString("    -nLookUp the number of lookups per transaction\n");
    System.printString("    -h help with usage\n");
  }
}
