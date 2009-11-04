public class SpamFilter extends Thread {
  DistributedHashMap mydhmap;

  int id; //thread id

  /**
   * Total number of iterations
   **/
  int numiter;

  /**
   * Total number of emails
   **/
  int numemail;

  /**
   * Total number of threads
   **/
  int nthreads;

  public SpamFilter() {

  }

  public SpamFilter(int numiter, int numemail,int id, DistributedHashMap mydhmap, int nthreads) {
    this.numiter=numiter;
    this.numemail=numemail;
    this.id = id;
    this.mydhmap = mydhmap;
    this.nthreads = nthreads;
  }

  public void run() {
    int niter;
    int nemails;
    int thid;
    atomic {
      niter=numiter;
      nemails=numemail;
      thid = id;
    }

    Random rand = new Random(0);

    for(int i=0; i<niter; i++) {
      for(int j=0; j<nemails; j++) {
        int pickemail = rand.nextInt(100);
        Mail email = new Mail("emails/email"+pickemail);
        //Mail email = getEmail(pickemail);
        Vector signatures = email.checkMail(thid);
        //check with global data structure
        int[] confidenceVals=null;
        atomic {
          confidenceVals = check(signatures,thid);
        }

        //---- create and  return results --------
        FilterResult filterResult = new FilterResult();
        boolean filterAnswer = filterResult.getResult(confidenceVals);

        boolean userAnswer = email.getIsSpam();
        if(filterAnswer != userAnswer) {
          atomic {
            sendFeedBack(email, userAnswer, thid);
          }
        }
      } //end num emails
    }//end num iter
  }

  public static void main(String[] args) {
    int[] mid = new int[8];
    mid[0] = (128<<24)|(195<<16)|(136<<8)|162; //dc-1.calit2
    mid[1] = (128<<24)|(195<<16)|(136<<8)|163; //dc-2.calit2
    mid[2] = (128<<24)|(195<<16)|(136<<8)|164; //dc-3.calit2
    mid[3] = (128<<24)|(195<<16)|(136<<8)|165; //dc-4.calit2
    mid[4] = (128<<24)|(195<<16)|(136<<8)|166; //dc-5.calit2
    mid[5] = (128<<24)|(195<<16)|(136<<8)|167; //dc-6.calit2
    mid[6] = (128<<24)|(195<<16)|(136<<8)|168; //dc-7.calit2
    mid[7] = (128<<24)|(195<<16)|(136<<8)|169; //dc-8.calit2

    //Read options from command prompt
    SpamFilter sf = new SpamFilter();
    SpamFilter.parseCmdLine(args, sf);
    int nthreads = sf.nthreads;

    Random rand = new Random(8);
    //Randomly set Spam vals for each email
    for(int i=0; i<sf.numemail; i++) {
      Mail email = new Mail("./emails/email"+i);
      int spamval = rand.nextInt(100);
      if(spamval<60) { //assume 60% are spam and rest are ham
        email.setIsSpam(false);
      } else {
        email.setIsSpam(true);
      }
    }

    //Create Global data structure 
    DistributedHashMap dhmap;
    SpamFilter[] spf;
    atomic {
      dhmap = global new DistributedHashMap(500, 0.75f);
      spf = global new SpamFilter[nthreads];
      for(int i=0; i<nthreads; i++) {
        spf[i] = global new SpamFilter(sf.numiter, sf.numemail, i, dhmap, nthreads);
      }
    }

    /* ---- Start Threads ---- */
    SpamFilter tmp;
    for(int i = 0; i<nthreads; i++) {
      atomic {
        tmp = spf[i];
      }
      tmp.start(mid[i]);
    }

    /* ---- Join threads----- */
    for(int i = 0; i<nthreads; i++) {
      atomic {
        tmp = spf[i];
      }
      tmp.join();
    }

    System.out.println("Finished");
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
          sf.nthreads = new Integer(args[i++]).intValue();
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
   *  Returns result to the Spam filter
   **/
  /*
  public boolean checkMail(Mail mail, int userid) {
    //Preprocess emails
    //Vector partsOfMailStrings = mail.createMailStringsWithURL();
    /*
    Vector partsOfMailStrings = mail.getCommonPart();
    partsOfMailStrings.addElement(mail.getBodyString());

    //Compute signatures
    SignatureComputer sigComp = new SignatureComputer();
    Vector signatures = sigComp.computeSigs(partsOfMailStrings);//vector of strings

    //check with global data structure
    int[] confidenceVals = check(signatures,userid);

    //---- create and  return results --------
    FilterResult filterResult = new FilterResult();
    boolean spam = filterResult.getResult(confidenceVals);

    return spam;
  } 
   */

  public int[] check(Vector signatures, int userid) {
    int numparts = signatures.size();
    int[] confidenceVals = new int[numparts];
    for(int i=0; i<numparts; i++) {
      String part = (String)(signatures.elementAt(i));
      char tmpengine = part.charAt(0);
      String engine =  global new String(tmpengine);
      String signature = global new String(part.substring(2));
      //String signature = part.substring(2); //a:b index(a)=0, index(:)=1, index(b)=2
      HashEntry myhe = global new HashEntry();
      myhe.setengine(engine);
      myhe.setsig(signature);

      //find object in distributedhashMap: if no object then add object 
      //else read object
      if(!mydhmap.containsKey(myhe)) {
        //add new object
        myhe.stats = global new HashStat();
        myhe.stats.setuser(userid, 0, 0, -1);
        FilterStatistic fs =  global new FilterStatistic(0,0,-1);
        mydhmap.put(myhe, fs);
      } else {
        // ----- now connect to global data structure and ask for spam -----
        HashEntry tmphe = (HashEntry)(mydhmap.getKey(myhe));
        FilterStatistic fs = (FilterStatistic) (mydhmap.get(myhe)); //get the value from hash
        confidenceVals[i] = fs.getChecked();
      }
    }

    //  --> the mail client is able to determine if it is spam or not
    return confidenceVals;
  }

  public void sendFeedBack(Mail mail, boolean isSpam, int id) {
    Vector partsOfMailStrings = mail.getCommonPart();
    partsOfMailStrings.addElement(mail.getBodyString());
    //Compute signatures
    SignatureComputer sigComp = new SignatureComputer();
    Vector signatures = sigComp.computeSigs(partsOfMailStrings);//vector of strings

    for(int i=0;i<signatures.size();i++) {
      String part = (String)(signatures.elementAt(i));
      char tmpengine = part.charAt(0);
      String engine =  global new String(tmpengine);
      String signature = global new String(part.substring(2));
      //String signature = part.substring(2); //a:b index(a)=0, index(:)=1, index(b)=2
      HashEntry myhe = global new HashEntry();
      myhe.setengine(engine);
      myhe.setsig(signature);

      // ----- now connect to global data structure and upate spam count -----
      HashEntry tmphe = (HashEntry)(mydhmap.getKey(myhe));
      if(tmphe.stats.userid[id] != 1) {
        tmphe.stats.setuserid(id);
      }

      FilterStatistic fs = (FilterStatistic) (mydhmap.get(myhe)); //get the value from hash

      //Increment spam or ham value 
      if(isSpam) {
        tmphe.stats.incSpamCount(id);
        fs.increaseSpam();
      } else {
        tmphe.stats.incHamCount(id);
        fs.increaseHam();
      }
    }
  }
}


