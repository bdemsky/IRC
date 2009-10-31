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

  public SpamFilter(int numiter, int numemail,int id) {
    this.numiter=numiter;
    this.numemail=numemail;
    this.id = id;
  }

  public void run() {
    int niter;
    int nemails;
    int thid;
    atomic {
      niter=numiter;
      nemails=numemails;
      thid = id;
    }

    Random rand = new Random(0);

    for(int i=0; i<niter; i++) {
      for(int j=0; j<nemails; j++) {
        int pickemail = rand.nextInt(100);
        //String email = getEmail(pickemail);
        checkMail(email, thid);
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
  public FilterResult[] checkMail(Mail mail, int userid) {
    //Preprocess emails
      //String[] partsOfMailStrings = createMailStrings();
      //RazorMail[] razorMails = 
    //Compute signatures
    SignatureComputer sigComp = new SignatureComputer();
    Vector signatures = sigComp.computeSigs(partsOfMailStrings);//vector of strings
          
    //check with global data structure
    check(signatures, userid);

    //---- create and  return results --------
    FilterResult[] filterResults = new FilterResult[mailStrings.length];

    return filterResults;
  } 

  public void check(Vector emailParts, int userid) {
    for(int i=0; i<emailParts.size(); i++) {
      String part = (String)(emailParts.elementAt(i));
      char tmpengine = part.charAt(0);
      String engine =  new String(tmpengine);
      String signature = part.substring(2); //a:b index(a)=0, index(:)=1, index(b)=2
      HashEntry myhe = new HashEntry();
      myhe.setengine(engine);
      myhe.setsig(signature);
      //find object in distributedhashMap: if no object then add object 
      //else read object
      HashEntry tmphe;
      if((tmphe=(HashEntry)mydhmap.get(myhe))== null) {
        //add new object
        myhe.stats = new HashStat();
        myhe.stats.setuser(userid, 0, 0, 1);
      } else {
        //else if read object
        Vector<String> enginesToSend = new Vector<String>();
        Vector<String> sigsToSend = new Vector<String>();

        for (RazorMail mail : razorMails) {
          for (int partNr = 0; partNr < mail.getPartSize(); partNr++) {
            Part part = mail.getPart(partNr);
            if (part.skipMe()) {
              continue;
            }

            for (Iterator<String> hashIter = part.getHashIterator(); hashIter.hasNext();) {
              String curHash = (String)hashIter.next();
              String[] engineHashSplit = curHash.split(":");
              String engine = engineHashSplit[0];
              String signature = engineHashSplit[1];
              enginesToSend.add(engine);
              sigsToSend.add(signature);
            }
          }
        }

        if (sigsToSend.size() == 0) { // nothing to send
          return;
        }

        String[] enginesToSendArr = new String[enginesToSend.size()];
        enginesToSend.toArray(enginesToSendArr);
        String[] sigsToSendArr = new String[sigsToSend.size()];
        sigsToSend.toArray(sigsToSendArr);

        // ----- now connect to server and ask query -----
        int[] confidenceVals = null;
        RazorCommunicationEngine checkEngine = getCheckEngine();
        try {
          checkEngine.connect();
          confidenceVals = checkEngine.askForSpam(sigsToSendArr,enginesToSendArr);
          checkEngine.disconnect();
        } finally {
          checkEngines.add(checkEngine);
        }

        if (confidenceVals == null) {
          System.err.println("check got no answer from server. error.");
          return; // error
        }

        if (confidenceVals.length != sigsToSendArr.length) {
          throw new IllegalStateException("We got not enough answers from server. expected: " + sigsToSendArr.length + "  received: " + confidenceVals.length);
        }

        // ----- now dispatch the answers to the mail objects -----
        int answerIndex = 0;
        for (RazorMail mail : razorMails) {
          for (int partNr = 0; partNr < mail.getPartSize(); partNr++) {
            Part part = mail.getPart(partNr);
            if (part.skipMe()) {
              continue;
            }

            for (Iterator<String> hashIter = part.getHashIterator(); hashIter.hasNext();) {
              String curHash = hashIter.next();
              part.setResponse(curHash,String.valueOf(confidenceVals[answerIndex++]));
            }
          }
        }
        //  --> after this loop the mail is able to determine if it is spam or not
      }
    }
  }
}
