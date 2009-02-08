public class LookUpService extends Thread {
  DistributedHashMap mydhmap;
  int threadid;
  int numthreads;

  public LookUpService(DistributedHashMap dmap, int threadid, int numthreads) {
    mydhmap = dmap;
    this.threadid = threadid;
    this.numthreads = numthreads;
  }

  public void run() {
    //Add to the hash map
    int nobjs = 10;
    // Do read/writes
    int numtrans = 1000;
    // read probability % between 0-99
    int rdprob = 90;
    atomic {
      for(int i = 0; i < nobjs; i++) {
        Integer key = global new Integer(threadid*nobjs+i);
        Integer val = global new Integer(i);
        Object o1 = key;
        Object o2 = val;
        mydhmap.put(o1, o2);
      }
    }
    for (int i = 0; i < numtrans; i++) {
      Random rand = new Random(i);
      int rdwr = rand.nextInt(100);
      int rwkey = rand.nextInt(nobjs*numthreads);
      Integer k = new Integer(rwkey);
      if (rdwr < rdprob) {
        Integer tmp = mydhmap.get(k);
      } else {
        Integer val = global new Integer(i);
        mydhmap.put(k, val);
      }
    }
  }

  public static void main(String[] args) {
    int nthreads;
    if(args.length>0)
      nthreads = Integer.parseInt(args[0]);

    int[] mid = new int[8];
    mid[0] = (128<<24)|(195<<16)|(175<<8)|79;//dc-1
    mid[1] = (128<<24)|(195<<16)|(136<<8)|163;//dc-2
    mid[2] = (128<<24)|(195<<16)|(136<<8)|164;//dc-3
    mid[3] = (128<<24)|(195<<16)|(136<<8)|165;//dc-4
    mid[4] = (128<<24)|(195<<16)|(136<<8)|166;//dc-5
    mid[5] = (128<<24)|(195<<16)|(136<<8)|167;//dc-6
    mid[6] = (128<<24)|(195<<16)|(136<<8)|168;//dc-7
    mid[7] = (128<<24)|(195<<16)|(136<<8)|169;//dc-8

    LookUpService[] lus;
    DistributedHashMap dhmap;
    //DistributedHashEntry dhe;

    
    atomic {
      dhmap = global new DistributedHashMap(100, 100, 0.75f);
      lus = global new LookUpService[nthreads];
      for(int i = 0; i<nthreads; i++) {
        lus[i] = global new LookUpService(dhmap, i, nthreads);
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
}
