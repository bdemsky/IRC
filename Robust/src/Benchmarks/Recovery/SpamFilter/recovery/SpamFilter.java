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
    int correct=0;
    int wrong=0;

    atomic {
      niter=numiter;
      nemails=numemail;
      thid = id;
    }

    Random rand = new Random(thid);
    int i;

    long st = System.currentTimeMillis();
    long fi;

    for(i=0; i<niter; i++) {
      correct =0;
      wrong = 0;
      for(int j=0; j<nemails; j++) {
        //long start = System.currentTimeMillis();
        int pickemail = rand.nextInt(100);

        //System.out.println("pickemail= " + pickemail);

        // randomly pick emails
        pickemail+=1;
        Mail email = new Mail("../emails/email"+pickemail);
        Vector signatures = email.checkMail(thid);

        //check with global data structure
        int[] confidenceVals=null;
        //long startcheck = System.currentTimeMillis(); 
        atomic {
          confidenceVals = check(signatures,thid);
        }
        //long stopcheckMail = System.currentTimeMillis(); 
        //long diff = (stopcheckMail-startcheck);
        //System.out.println("check takes= " + diff + "millisecs");

        /* Only for debugging
        for(int k=0; k<signatures.size();k++) {
          System.out.println("confidenceVals["+k+"]= "+confidenceVals[k]);
        }
        */

        //---- create and  return results --------
        FilterResult filterResult = new FilterResult();
        //long startgetResult = System.currentTimeMillis();
        boolean filterAnswer = filterResult.getResult(confidenceVals);
        //long stopgetResult = System.currentTimeMillis();
        //diff = (stopgetResult-startgetResult);
        //System.out.println("getResult takes= " + diff + "millisecs");

        //---- get user's take on email and send feedback ------
        boolean userAnswer = email.getIsSpam();

//       System.out.println("userAnswer= " + userAnswer + " filterAnswer= " + filterAnswer);

        if(filterAnswer != userAnswer) {
          /* wrong answer from the spam filter */
          wrong++;
          //long startsendFeedBack = System.currentTimeMillis();
          atomic {
            sendFeedBack(signatures, userAnswer, thid, rand);
          }
          //long stopsendFeedBack = System.currentTimeMillis();
          //diff = (stopsendFeedBack-startsendFeedBack);
          //System.out.println("sendFeedback takes= " + diff + "millisecs");
        }
        else {
          /* Correct answer from the spam filter */
          correct++;
        }
        //long stop = System.currentTimeMillis();
        //diff = stop-start;
//        System.out.println("time to complete iteration" + j + " = " + diff + " millisecs");
      } //end num emails
//      System.out.println((i+1)+"th iteration correct = " + correct + " Wrong = " + wrong + " percentage = " + ((float)correct/(float)nemails));
    }//end num iter
    // Sanity check
    fi = System.currentTimeMillis();

    System.out.println((i)+"th iteration correct = " + correct + " Wrong = " + wrong + " percentage = " + ((float)correct/(float)nemails));
    System.out.println("\n\n\n I'm Done - Time Elapse : " + (double)((fi-st)/1000) +"\n\n\n");
    
    RecoveryStat.printRecoveryStat();

    while(true) {
      sleep(1000000);
    }
  }

  public static void main(String[] args) {
    //Read options from command prompt
    SpamFilter sf = new SpamFilter();
    SpamFilter.parseCmdLine(args, sf);
    int nthreads = sf.nthreads;
    int[] mid = null;

    if(nthreads <= 8 ) {
      mid = new int[8];
      mid[0] = (128<<24)|(195<<16)|(136<<8)|162; //dc1
      mid[1] = (128<<24)|(195<<16)|(136<<8)|163; //dc2
      mid[2] = (128<<24)|(195<<16)|(136<<8)|164; //dc3
      mid[3] = (128<<24)|(195<<16)|(136<<8)|165; //dc4
      mid[4] = (128<<24)|(195<<16)|(136<<8)|166; //dc5
      mid[5] = (128<<24)|(195<<16)|(136<<8)|167; //dc6
      mid[6] = (128<<24)|(195<<16)|(136<<8)|168; //dc7
      mid[7] = (128<<24)|(195<<16)|(136<<8)|169; //dc8
    } else {
      mid = new int[16];
      mid[0] = (128<<24)|(195<<16)|(136<<8)|162; //dc1
      mid[1] = (128<<24)|(195<<16)|(136<<8)|162; //dc1
      mid[2] = (128<<24)|(195<<16)|(136<<8)|163; //dc2
      mid[3] = (128<<24)|(195<<16)|(136<<8)|163; //dc2
      mid[4] = (128<<24)|(195<<16)|(136<<8)|164; //dc3
      mid[5] = (128<<24)|(195<<16)|(136<<8)|164; //dc3
      mid[6] = (128<<24)|(195<<16)|(136<<8)|165; //dc4
      mid[7] = (128<<24)|(195<<16)|(136<<8)|165; //dc4
      mid[8] = (128<<24)|(195<<16)|(136<<8)|166; //dc5
      mid[9] = (128<<24)|(195<<16)|(136<<8)|166; //dc5
      mid[10] = (128<<24)|(195<<16)|(136<<8)|167; //dc6
      mid[11] = (128<<24)|(195<<16)|(136<<8)|167; //dc6
      mid[12] = (128<<24)|(195<<16)|(136<<8)|168; //dc7
      mid[13] = (128<<24)|(195<<16)|(136<<8)|168; //dc7
      mid[14] = (128<<24)|(195<<16)|(136<<8)|169; //dc8
      mid[15] = (128<<24)|(195<<16)|(136<<8)|169; //dc8
    }

    if(mid == null) {
      System.out.println("Number of machines not initialized");
      System.exit(1);
    }


    //Create Global data structure 
    DistributedHashMap dhmap;
    SpamFilter[] spf;
    atomic {
      dhmap = global new DistributedHashMap(500, 0.75f);
    }
    atomic {
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
    int i = 1;

    sf.nthreads = new Integer(args[0]).intValue();


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
      }
      
      /*else if(arg.equals("-t")) { //num of threads
        if(i < args.length) {
          sf.nthreads = new Integer(args[i++]).intValue();
        }
      }
      */
      else if(arg.equals("-h")) {
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
    System.out.println("usage: ./spamfilter <num thread> -n <num iterations> -e <num emails>\n");
    System.out.println(                   "  -n : num iterations");
    System.out.println(                   "  -e : number of emails");
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

    //System.out.println("check() numparts= " + numparts);

    int[] confidenceVals = new int[numparts];
    for(int i=0; i<numparts; i++) {
      String part = (String)(signatures.elementAt(i));
      char tmpengine = part.charAt(0);
      GString engine=null;
      if(tmpengine == '4') { //Ephemeral Signature calculator
        String tmpstr = new String("4");
        engine = global new GString(tmpstr);
      }
      if(tmpengine == '8') { //Whiplash Signature calculator
        String tmpstr = new String("8");
        engine = global new GString(tmpstr);
      }

      //System.out.println("check(): engine= " + engine.toLocalString());

      String str = new String(part.substring(2));//a:b index of a =0, index of : =1, index of b =2
      GString signature = global new GString(str);
      HashEntry myhe = global new HashEntry();
      myhe.setengine(engine);
      myhe.setsig(signature);

      //find object in distributedhashMap: if no object then add object 
      if(!mydhmap.containsKey(myhe)) {
        //add new object
        HashStat mystat = global new HashStat();
        mystat.setuser(userid, 0, 0, -1);
        myhe.setstats(mystat);
        FilterStatistic fs =  global new FilterStatistic(0,0,-1);
        mydhmap.put(myhe, fs);
        confidenceVals[i] = 0;
      } else { //read exsisting object
        // ----- now connect to global data structure and ask for spam -----
        HashEntry tmphe = (HashEntry)(mydhmap.getKey(myhe));
        FilterStatistic fs = (FilterStatistic) (mydhmap.get(tmphe)); //get the value from hash

        //System.out.println(fs.toString()+"\n");

        confidenceVals[i] = fs.getChecked();
      }
    }

    //  --> the mail client is able to determine if it is spam or not
    // --- According to the "any"-logic (in Core#check_logic) in original Razor ---
    // If any answer is spam, the entire email is spam.
    return confidenceVals;
  }

  /**
   * This method sends feedback from the user to a distributed
   * spam database and trains the spam database to check future
   * emails and detect spam
   **/
  public void sendFeedBack(Vector signatures, boolean isSpam, int id, Random myrand) {

    for(int i=0;i<signatures.size();i++) {
      String part = (String)(signatures.elementAt(i));
      //
      // Signature is of form a:b
      // where a = string representing a signature engine
      //           either "4" or "8"
      //       b = string representing signature
      //
      char tmpengine = part.charAt(0); //

      GString engine=null;

      if(tmpengine == '4') {
        String tmpstr = new String("4");
        engine = global new GString(tmpstr);
      }

      if(tmpengine == '8') {
        String tmpstr = new String("8");
        engine = global new GString(tmpstr);
      }

      //System.out.println("sendFeedBack(): engine= " + engine.toLocalString());

      String tmpsig = new String(part.substring(2));
      GString signature = global new GString(tmpsig);

      //System.out.println("sendFeedBack(): signature= " + signature.toLocalString());

      HashEntry myhe = global new HashEntry();
      myhe.setengine(engine);
      myhe.setsig(signature);

      // ----- now connect to global data structure and update stats -----
      if(mydhmap.containsKey(myhe)) {
        HashEntry tmphe = (HashEntry)(mydhmap.getKey(myhe));


        if(tmphe.stats.userid[id] != 1) {
          tmphe.stats.setuserid(id);
        }

        //---- get value from distributed hash and update spam count
        FilterStatistic fs = (FilterStatistic) (mydhmap.get(myhe)); 

        //System.out.println(fs.toString());

        //Allow users to give incorrect feedback
        int pickemail = myrand.nextInt(100);
        /* Randomly allow user to provide incorrect feedback */
        if(pickemail < 95) {
          //give correct feedback 95% of times
          //Increment spam or ham value 
          if(isSpam) {
            tmphe.stats.incSpamCount(id);
            fs.increaseSpam();
          } else {
            tmphe.stats.incHamCount(id);
            fs.increaseHam();
          }
        } else {
          // Give incorrect feedback 5% of times
          if(isSpam) {
            tmphe.stats.incHamCount(id);
            fs.increaseHam();
          } else {
            tmphe.stats.incSpamCount(id);
            fs.increaseSpam();
          }
        } //end of pickemail
      }//end of if
    }//end of for
  }//end of sendFeeback()
}


