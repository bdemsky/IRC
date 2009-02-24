public class LookUpClient {
  /**
   * Total number of transactions
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

  public LookUpClient() {
  }

  public LookUpClient(int numtrans, int nobjs, int rdprob, int nLookUp) {
    this.numtrans = numtrans;
    this.nobjs = nobjs;
    this.rdprob = rdprob;
    this.nLookUp = nLookUp;
  }

  public static void main(String[] args) {
    LookUpClient lc = new LookUpClient();
    LookUpClient.parseCmdLine(args, lc);

    Socket sock = new Socket("dw-8.eecs.uci.edu",9001);

    for (int i = 0; i < lc.numtrans; i++) {
      Random rand = new Random(i);
      for (int j = 0; j < lc.nLookUp; j++) {
        int rdwr = rand.nextInt(100);
        int rwkey = rand.nextInt(lc.nobjs);
        Integer key = new Integer(rwkey);
        int operation;
        if (rdwr < lc.rdprob) {
          operation = 1; //read from hashmap
        } else {
          operation = 2;//update hashmap
        }
        lc.doLookUp(operation, sock, key);
      }
    }
    /** Special character to terminate computation **/
    String op = new String("t");
    sock.write(op.getBytes());
  }

  /**
   * Call to do a read/ write on socket
   **/
  public void doLookUp(int operation, Socket sock, Integer key){
    String op;
    if (operation == 1) {
      op = new String("r");
      sock.write(op.getBytes());
      sock.write(key.intToByteArray());
      byte b[] = new byte[4];
      int numbytes = sock.read(b);
    } else {
      op = new String("w");
      sock.write(op.getBytes());
      sock.write(key.intToByteArray());
    }
  }

  /**
   * Parse the command line options.
   **/
  public static void parseCmdLine(String args[], LookUpClient lc) {
    int i = 0;
    String arg;
    while(i < args.length && args[i].startsWith("-")) {
      arg = args[i++];
      //check options
      if(arg.equals("-nObjs")) {
        if(i < args.length) {
          lc.nobjs = new Integer(args[i++]).intValue();
        }
      } else if (arg.equals("-nTrans")) {
        if(i < args.length) {
          lc.numtrans =  new Integer(args[i++]).intValue();
        }
      } else if(arg.equals("-probRead")) {
        if(i < args.length) {
          lc.rdprob = new Integer(args[i++]).intValue();
        }
      } else if(arg.equals("-nLookUp")) {
        if(i < args.length) {
          lc.nLookUp = new Integer(args[i++]).intValue();
        }
      } else if(arg.equals("-h")) {
        lc.usage();
      }
    }

    if(lc.nobjs == 0  || lc.numtrans == 0)
      lc.usage();
  }

  /**
   * The usage routine which describes the program options.
   **/
  public void usage() {
    System.printString("usage: ./Client.bin -nObjs <objects in hashmap> -nTrans <number of transactions> -probRead <read probability> -nLookUp <number of lookups>\n");
    System.printString("    -nObjs the number of objects to be inserted into distributed hashmap\n");
    System.printString("    -nTrans the number of transactions to run\n");
    System.printString("    -probRead the probability of read given a transaction\n");
    System.printString("    -nLookUp the number of lookups per transaction\n");
    System.printString("    -h help with usage\n");
  }
}
