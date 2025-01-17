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

    //if(thid == 0)
    //  return;

    Random rand = new Random(thid);
    int i;

    for(i=0; i<niter; i++) {
      correct =0;
      wrong = 0;
      for(int j=0; j<nemails; j++) {
        int pickemail = rand.nextInt(nemails);

        // randomly pick emails
        pickemail+=1;
        //System.out.println("pickemail= " + pickemail);
        Mail email = new Mail("emails/email"+pickemail);
        Vector signatures = email.checkMail(thid);

        //check with global data structure
        int[] confidenceVals=null;
        atomic {
          confidenceVals = check(signatures,thid);
        }

        //---- create and  return results --------
        FilterResult filterResult = new FilterResult();
        boolean filterAnswer = filterResult.getResult(confidenceVals);

        //---- get user's take on email and send feedback ------
        boolean userAnswer = email.getIsSpam();

        //System.out.println("userAnswer= " + userAnswer + " filterAnswer= " + filterAnswer);

        if(filterAnswer != userAnswer) {
          /* wrong answer from the spam filter */
          wrong++;
          atomic {
            sendFeedBack(signatures, userAnswer, thid, rand);
          }
        }
        else {
          /* Correct answer from the spam filter */
          correct++;
        }
      } //end num emails
    }//end num iter
    // Sanity check
    System.out.println((i)+"th iteration correct = " + correct + " Wrong = " + wrong + " percentage = " + ((float)correct/(float)nemails));
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

    //Create Global data structure 
    DistributedHashMap dhmap;
    SpamFilter[] spf;
    atomic {
      dhmap = global new DistributedHashMap(10000, 0.75f);
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

    //*** Prefetch ****/
    //prefetch(this.mydhmap.table);
    int numparts = signatures.size();

    //System.out.println("check() numparts= " + numparts);
    int[] confidenceVals = new int[numparts];

    for(int i=0; i<numparts; i++) {
      String part = (String)(signatures.elementAt(i));
      char tmpengine = part.charAt(0);
      String enginestr=null;
      if(tmpengine == '4') { //Ephemeral Signature calculator
        enginestr = new String("4");
      }
      if(tmpengine == '8') { //Whiplash Signature calculator
        enginestr = new String("8");
      }
      String signaturestr = new String(part.substring(2));//a:b index of a =0, index of : =1, index of b =2

      //find object in distributedhashMap: if no object then add object 
      HashEntry tmphe=null;
      int hashCode = enginestr.hashCode()^signaturestr.hashCode();
      
      int index1 = mydhmap.hash1(hashCode, mydhmap.table.length);

      /*** Prefetch ****/
      //prefetch(mydhmap.table[index1].array.key.stats.userstat[userid],
      //         mydhmap.table[index1].array.value,
      //         mydhmap.table[index1].array.key.stats.userid,
      //         mydhmap.table[index1].array.key.engine.value,
      //         mydhmap.table[index1].array.key.signature.value);  

      DistributedHashEntry testhe = mydhmap.table[index1];
      boolean foundstatistics=false;
      DHashEntry ptr=null;
      if(testhe!=null) {
        /*** Prefetch ****/
        //prefetch(testhe.array.next.value,
        //         testhe.array.next.key.engine.value,
        //         testhe.array.next.key.stats.userid,
        //         testhe.array.next.key.stats.userstat[userid],
        //         testhe.array.next.key.signature,value);

        ptr=testhe.array;

        while(ptr !=null) {
          boolean engineVal= inLineEquals(ptr.key.engine.value, ptr.key.engine.count, ptr.key.engine.offset,
             enginestr.value, enginestr.count, enginestr.offset);
          boolean SignatureVal= inLineEquals(ptr.key.signature.value, ptr.key.signature.count, ptr.key.signature.offset,
              signaturestr.value, signaturestr.count, signaturestr.offset);
	  
          FilterStatistic tmpfs = ptr.value;
          int tmpuserid = ptr.key.stats.userid[userid];
          FilterStatistic myfs = ptr.key.stats.userstat[userid];
          
          if(ptr.hashval==hashCode&&engineVal&&SignatureVal) {
	    //Found statics...get Checked value.
	    confidenceVals[i] = tmpfs.getChecked(); 
	    foundstatistics=true;
	    break;
          }
          /* Prefetch */
          //prefetch(ptr.next.next.key.stats.userid,
          //         ptr.next.next.key.engine.value,
          //         ptr.next.next.key.signature.value,
          //         ptr.next.next.key.stats.userstat[userid],
          //         ptr.next.next.value);
          ptr=ptr.next;
        }
      }

      if (!foundstatistics) {
        /* Prefetch */
        //prefetch(testhe.array);
        HashEntry myhe = global new HashEntry();
        GString engine = global new GString(enginestr);
        GString signature = global new GString(signaturestr);

        myhe.setengine(engine);
        myhe.setsig(signature);

        DHashEntry he = global new DHashEntry();
        //application specific fields
        HashStat mystat = global new HashStat();
        mystat.setuser(userid, 0, 0, -1);
        myhe.setstats(mystat);
        FilterStatistic myfs =  global new FilterStatistic(0,0,-1);
        he.value=myfs;
        he.key=myhe;
        he.hashval=hashCode;
        //link old element into chain
        //build new element

        if (testhe!=null) {
          //splice into old list
          he.next=testhe.array;
          testhe.array=he;
        } else {
          //create new header...this will cause many aborts
          DistributedHashEntry newhe=global new DistributedHashEntry();
          newhe.array=he;
          mydhmap.table[index1]=newhe;
        }
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
      HashEntry tmphe=null;
      FilterStatistic fs=null;
      int hashCode = myhe.hashCode();
      int index1 = mydhmap.hash1(hashCode, mydhmap.table.length);
      DistributedHashEntry testhe = mydhmap.table[index1];
      if(testhe!=null) {
        DHashEntry ptr=testhe.array;
        while(ptr!=null) {
          boolean engineVal= inLineEquals(ptr.key.engine.value, ptr.key.engine.count, ptr.key.engine.offset,
              myhe.engine.value, myhe.engine.count, myhe.engine.offset);
          boolean SignatureVal= inLineEquals(ptr.key.signature.value, ptr.key.signature.count, ptr.key.signature.offset,
              myhe.signature.value, myhe.signature.count, myhe.signature.offset);

          if(ptr.hashval==hashCode&&engineVal&&SignatureVal) {
            tmphe=ptr.key;
            fs=ptr.value;
            break;
          }
          ptr=ptr.next;
        }
      }
      //tmphe has the key at the end
      //fs has the value at the end      

      if(tmphe==null) 
        return;


      if(tmphe.stats.userid[id] != 1) {
        tmphe.stats.setuserid(id);
      }


      //---- get value from distributed hash and update spam count

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
    }//end of for
  }//end of sendFeedback

  public static boolean inLineEquals(char[] array1, int count1, int offset1, char[] array2, int count2, int offset2) {
    if(count1 != count2)
      return false;
    for(int i=0; i<count1; i++) {
      if(array1[i+offset1] != array2[i+offset2]) {
        return false;
      }
    }
    return true;
  }
}
