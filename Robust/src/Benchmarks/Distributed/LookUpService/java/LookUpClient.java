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

    Socket sock = new Socket("dc-1.calit2.uci.edu",9001);

    for (int i = 0; i < lc.numtrans; i++) {
      Random rand = new Random(i);
      for (int j = 0; j < lc.nLookUp; j++) {
        int rdwr = rand.nextInt(100);
        int rwkey = rand.nextInt(lc.nobjs);
        int operation;
        if (rdwr < lc.rdprob) {
          operation = 1; //read from hashmap
        } else {
          operation = 2;//update hashmap
        }
        lc.doLookUp(operation, sock, rwkey);
      }
    }
    /** Special character to terminate computation **/
    String op = new String("t");
    sock.write(op.getBytes());
  }

  /**
   * Call to do a read/ write on socket
   **/
  public void doLookUp(int operation, Socket sock, int key){
    String op;
    if (operation == 1) {
      sock.write(fillBytes(operation, key));
      byte b[] = new byte[4];
      int numbytes = sock.read(b);
    } else {
      sock.write(fillBytes(operation, key));
    }
  }

  /*
   * Convert int to a byte array 
   **/
  byte[] fillBytes(int operation, int key) {
    byte[] b = new byte[5];
    if(operation == 1) {
      b[0] = (byte)'r';
    } else { 
      b[0] = (byte)'w';
    }
    for(int i = 1; i < 5; i++){
      int offset = (3-(i-1)) * 8;
      b[i] = (byte) ((key >> offset) & 0xFF);
    }
    return b;
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
