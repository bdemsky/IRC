public class SingleObjectMod extends Thread {
  int nthreads;
  int arrysize;
  int loopsize;
  int lsize1, lsize2;
  int threadid;
  intwrapper[] mainobj;
  Random rand;

  public SingleObjectMod() {

  }

  public SingleObjectMod(int nthreads, int arrysize, int loopsize, int lsize1, int lsize2, int threadid, intwrapper[] mainobj, Random rand) {
    this.nthreads = nthreads;
    this.arrysize = arrysize;
    this.loopsize = loopsize;
    this.lsize1 = lsize1;
    this.lsize2 = lsize2;
    this.threadid = threadid;
    this.mainobj = mainobj;
    this.rand = rand;
  }

  public static void parseCmdLine(String args[], SingleObjectMod som) {
    int i = 0;
    String arg;
    while(i < args.length && args[i].startsWith("-")) {
      arg = args[i++];
      //check options
      if(arg.equals("-t")) {
        if(i < args.length) {
          som.nthreads = new Integer(args[i++]).intValue();
        }
      } else if(arg.equals("-size")) {
        if(i <  args.length) {
          som.arrysize = new Integer(args[i++]).intValue();
        }
      } else if(arg.equals("-l")) {
        if(i < args.length) {
          som.loopsize = new Integer(args[i++]).intValue();
        }
      } else if(arg.equals("-l1")) {
        if(i < args.length) {
          som.lsize1 = new Integer(args[i++]).intValue();
        }
      } else if(arg.equals("-l2")) {
        if(i < args.length) {
          som.lsize2 = new Integer(args[i++]).intValue();
        }
      } else {
        System.out.println("Incorrect argument");
        System.out.println("usage: ./SingleObjectMod -t <threads> -size <array size> -l <loopsize> -l1 <inner loopsize2> -l2 <inner loopsize2>\n");
      }
    }
  }

  public void run() {
    int index, val;
    Random rand = new Random();
    rand.random_alloc();
    rand.random_seed(threadid);
    int partitionSize = (loopsize + nthreads/2) /nthreads;
    int start = threadid * partitionSize;
    int stop;
    if(threadid == (nthreads - 1))
      stop = loopsize;
    else
      stop = start + partitionSize;
    //System.out.println("Start = " + start+ " stop= " + stop + " partitionSize= " + partitionSize+ " loopsize= " + loopsize + " lsize1= " + lsize1 + " lsize2= " + lsize2);
    for(int i = start; i < stop; i++) {
      int count;
      atomic {
        index = (int)(rand.random_generate() % arrysize);
        // Do computation 1
        for(int j = 0; j<lsize1; j++) {
          count+=j*j;
        }
        // Only read values from an object
        val = mainobj[index];
        // Do computation 2
        for(int j = 0; j<lsize2; j++) {
          count+=val*j;
        }
        // Write values 
        mainobj[index] = count;
      }
    }
  }

  public static void main(String[] args) {
    SingleObjectMod som = new SingleObjectMod();
    SingleObjectMod.parseCmdLine(args, som);
    som.mainobj = new intwrapper[som.arrysize];

    Random rand = new Random();
    rand.random_alloc();

    for(int i = 0; i<som.arrysize; i++) {
      som.mainobj[i] = i;
    }

    int nthreads = som.nthreads;
    System.out.println("Num threads= "+ nthreads);

    SingleObjectMod[] mysom = new SingleObjectMod[nthreads];
    for(int i = 0; i < nthreads; i++) {
      mysom[i] = new SingleObjectMod(nthreads, som.arrysize, som.loopsize, som.lsize1, som.lsize2, i, som.mainobj, rand);
    }

    for(int i = 0; i < nthreads; i++) {
      mysom[i].start();
    }

    for(int i = 0; i < nthreads; i++) {
      mysom[i].join();
    }

    System.out.println("Finished......");
    System.exit(0);
  }
}
