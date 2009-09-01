public class SingleObjectMod extends Thread {
  int nthreads;
  int arrysize;
  int loopsize;
  int lsize1, lsize2;
  int prob; //prob to choose if short or long transaction
  int threadid;
  intwrapper[] mainobj;
  Random rand;

  public SingleObjectMod() {

  }

  public SingleObjectMod(int nthreads, int arrysize, int loopsize, int lsize1, int lsize2, int prob, int threadid, intwrapper[] mainobj, Random rand) {
    this.nthreads = nthreads;
    this.arrysize = arrysize;
    this.loopsize = loopsize;
    this.lsize1 = lsize1;
    this.lsize2 = lsize2;
    this.prob = prob;
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
      } else if(arg.equals("-p")) {
        if(i < args.length) {
          som.prob = new Integer(args[i++]).intValue();
        }
      } else {
        System.out.println("Incorrect argument");
        System.out.println("usage: ./SingleObjectMod -t <threads> -size <array size> -l <loopsize> -l1 <inner loopsize2> -l2 <inner loopsize2> -p <prob distribution>\n");
      }
    }
  }

  public void run() {
    int eventcount=0;
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
    LogTime[] lt = new LogTime[8*(stop-start)];
    for(int i = 0; i<(7*(stop-start))+3; i++) {
      lt[i] = new LogTime();
    }

    //System.out.println("Start = " + start+ " stop= " + stop + " partitionSize= " + partitionSize+ " loopsize= " + loopsize + " lsize1= " + lsize1 + " lsize2= " + lsize2);
    rand.random_seed(0);
    int l1, l2;
    //Time at Point1
    lt[eventcount].event = 1;
    lt[eventcount++].time  = System.getticks();

    for(int i = start; i < stop; i++) {
      //Time at Point2
      lt[eventcount].event = 2;
      lt[eventcount++].time  = System.getticks();

      int distribution = (int)(rand.random_generate() % 100);

      //90% long transactions
      if(distribution < prob) {
        l1=l2=lsize1;
      } else {
        //10% short transactions
        l1=l2=lsize2;
      }

      int count;

      //Time at point3
      lt[eventcount].event = 3;
      lt[eventcount++].time  = System.getticks();

      atomic {
        //Time at Point4
        lt[eventcount].event = 4;
        lt[eventcount++].time  = System.getticks();

        index = (int)(rand.random_generate() % arrysize);
        // Do computation 1
        for(int j = 0; j<l1; j++) {
          count+=j*j*j;
        }

        //Time at Point5
        lt[eventcount].event = 5;
        lt[eventcount++].time  = System.getticks();

        // Only read values from an object
        val = mainobj[index];

        //Time at Point6
        lt[eventcount].event = 6;
        lt[eventcount++].time  = System.getticks();

        // Do computation 2
        for(int j = 0; j<l2; j++) {
          count+=val*j*j;
        }
        //Time at Point7
        lt[eventcount].event = 7;
        lt[eventcount++].time  = System.getticks();

        // Write values 
        mainobj[index] = count;
      }
      //Time at Point8
      lt[eventcount].event = 8;
      lt[eventcount++].time  = System.getticks();
    }
    //Time at Point9
    lt[eventcount].event = 9;
    lt[eventcount++].time  = System.getticks();

    //Output to file
    FileOutputStream fo = new FileOutputStream("test_"+threadid);
    //test output to files test_threadid
    for(int i = 0; i<(7*(stop-start))+3; i++) {
      fo.write(fillBytes(lt[i].event));
      fo.write(longToByteArray(lt[i].time));
    }
    fo.close();
  }

  /*
   * Convert int to a byte array 
   **/
  static byte[] fillBytes(int key) {
    byte[] b = new byte[4];
    for(int i = 0; i < 4; i++){
      int offset = (3-i) * 8;
      b[i] = (byte) ((key >> offset) & 0xFF);
    }
    //
    // Debug
    // System.println("Sending b[0]= "+ (char) b[0]);
    //
    return b;
  }

  static byte[] longToByteArray(long a) {
    byte[] b = new byte[8];
    int i, shift;
    for (i = 0, shift = 56; i < 8; i++, shift -= 8)
    {
      b[i] = (byte) (0xFF & (a >> shift));
    }

    return b;
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
      mysom[i] = new SingleObjectMod(nthreads, som.arrysize, som.loopsize, som.lsize1, som.lsize2, som.prob, i, som.mainobj, rand);
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

public class LogTime {
  public int event;
  public long time;
  public LogTime() {
    event = 0;
    time = 0;
  }
}
