public class LookUpServerExample {
  /**
   * Number of objects in the hash table 
   **/
  int nobjs;

  /**
   * Number of threads
   **/
  int nthreads;

  public LookUpServerExample() {
  }

  public LookUpServerExample(int nobjs, int nthreads) {
    this.nobjs = nobjs;
    this.nthreads = nthreads;
  }

  public static int main(String args[]) {
    LookUpServerExample luse = new LookUpServerExample();
    LookUpServerExample.parseCmdLine(args, luse);
    
     /**
     * Create shared hashmap and put values
     **/
    HashMap hmap;
    hmap = new HashMap();
    for(int i = 0; i<luse.nobjs; i++) {
      Integer key = new Integer(i);
      Integer val = new Integer(i*i);
      hmap.put(key,val);
    }

    ServerSocket ss = new ServerSocket(9001);
    acceptConnection(ss, hmap, luse.nthreads);
  }

  public static void acceptConnection(ServerSocket ss, HashMap hmap, int nthreads) {
    LookUpServerThread[] lus = new LookUpServerThread[nthreads];
    for(int i=0; i<nthreads; i++) {
      Socket s = ss.accept();
      lus[i] = new LookUpServerThread(s, hmap);
      lus[i].start();
    }

    for(int i=0; i<nthreads; i++) {
      lus[i].join();
    }
  }

  /**
   * Parse the command line options.
   **/
  public static void parseCmdLine(String args[], LookUpServerExample lse) {
    int i = 0;
    String arg;
    while(i < args.length && args[i].startsWith("-")) {
      arg = args[i++];
      //check options
      if(arg.equals("-N")) {
        if(i < args.length) {
          lse.nthreads = new Integer(args[i++]).intValue();
        }
      } else if(arg.equals("-nObjs")) {
        if(i < args.length) {
          lse.nobjs = new Integer(args[i++]).intValue();
        }
      } else if(arg.equals("-h")) {
        lse.usage();
      }
    }

    if(lse.nobjs == 0)
      lse.usage();
  }

  /**
   * The usage routine which describes the program options.
   **/
  public void usage() {
    System.printString("usage: ./Server.bin -N <threads> -nObjs <objects in hashmap>\n");
    System.printString("    -N the number of threads\n");
    System.printString("    -nObjs the number of objects to be inserted into distributed hashmap\n");
    System.printString("    -h help with usage\n");
  }
}
