public class SpamFilter extends Thread {
  DistributedHashMap mydhmap;
  int id; //thread id
  int numiter;
  int numemail;
  /**
   * Total number of threads
   **/
  int nthreads;

  public SpamFilter() {

  }

  public SpamFilter(int numiter, int numemail,int threadid) {
    this.numiter=numiter;
    this.numemail=numemail;
    this.id = id;
  }

  public void run() {
    int niter;
    int nemails
    atomic {
      niter=numiter;
      nemails=numemails;
    }

    Random rand = new Random(0);

    for(int i=0; i<niter; i++) {
      for(int j=0; j<nemails; j++) {
        int pickemail = rand.nextInt(100);
        //String email = getEmail(pickemail);
        //checkMails(email);
      }
    }
  }

  public static void main(String[] args) {
    int nthreads;
    int[] mid = new int[8];
    mid[0] = (128<<24)|(195<<16)|(136<<8)|162; //dc-1.calit2
    mid[1] = (128<<24)|(195<<16)|(136<<8)|163; //dc-2.calit2
    mid[2] = (128<<24)|(195<<16)|(136<<8)|164; //dc-3.calit2
    mid[3] = (128<<24)|(195<<16)|(136<<8)|165; //dc-4.calit2
    mid[4] = (128<<24)|(195<<16)|(136<<8)|166; //dc-5.calit2
    mid[5] = (128<<24)|(195<<16)|(136<<8)|167; //dc-6.calit2
    mid[6] = (128<<24)|(195<<16)|(136<<8)|168; //dc-7.calit2
    mid[7] = (128<<24)|(195<<16)|(136<<8)|169; //dc-8.calit2


    /**
     * Read options from command prompt
     **/
    SpamFilter sf = new SpamFilter();
    SpamFilter.parseCmdLine(args, sf);

    /**
     * Create Global data structure 
     **/
    DistributedHashMap dhmap;
    atomic {
      dhmap = global new DistributedHashMap(500, 0.75f);
    }
    //3. N times iteration of work that needs to be done
    //     by each client

  }

  public static void parseCmdLine(String args[], SpamFilter sf) {
    int i = 0;
    String arg;
    while (i < args.length && args[i].startsWith("-")) {
      arg = args[i++];
      //check options
      if(arg.equals("-n")) { //num of iterations
        if(i < args.length) {
          sf.numiter = new Integer(args[i++]).intValue();
        }
      } else if(arg.equals("-e")) { //num of emails
        if(i < args.length) {
          sf.numemail = new Integer(args[i++]).intValue();
        }
      } else if(arg.equals("-t")) { //num of threads
        if(i < args.length) {
          sf.threshold = new Integer(args[i++]).intValue();
        }
      } else if(arg.equals("-h")) {
        sf.usage();
      }
    }
    if(sf.nthreads == 0) {
      sf.usage();
    }
  }

  /**
   * The usage routine describing the program
   **/
  public void usage() {
    System.out.println("usage: ./spamfilter -n <num iterations> -e <num emails> -t <num threads>\n");
    System.out.println(                   "  -n : num iterations");
    System.out.println(                   "  -e : number of emails");
    System.out.println(                   "  -t : number of threads");
  }

  /**
   *  Returns signatures to the Spam filter
   **/
  public FilterResult[] checkMail(Mail mail) {
    //Preprocess emails
      //StringBuffer[] mailStrings = createMailStrings();
    //Compute signatures
      //CommunicationEngine checkEngine = getCheckEngine();
      //SignatureComputer sigComp = new SignatureComputer();
    //check with global data structure

    //return results
    FilterResult[] filterResults = new FilterResult[mailStrings.length];

    return filterResults;
  } 
}
