public class SignatureComputer {
  public EphemeralSignature sig4; //signature engines
  public WhiplashSignature sig8; //signature engines

  int[] enginesToUseForCheck;

  public SignatureComputer() {
    sig4 = new EphemeralSignature(); //default values
    sig8 = new WhiplashSignature();
    createEnginesToCheck();
  }

  /**
   * constructor to be used when some parsing has already taken place with the
   * server-provides value <code>randomNumberSeed</code>.
   * 
   * @param randomNumberSeed
   *        a non-negative number used for seeding the random number generator
   *        before starting to hash values.
   * @param separator
   *        how the mail-text should be splitted into lines. (== what chars
   *        separate 2 lines)
   */
  public SignatureComputer(int randomNumberSeed, String separator) {
    sig4 = new EphemeralSignature(randomNumberSeed,separator);
    sig8 = new WhiplashSignature();
    createEnginesToCheck();
  }

  /**
   * the constructor to be used most of the time. you can hand over the
   * seed-string exactly as it is provided by the razor-server.
   * 
   * @param seedAndSeparator
   *        a string containing the seed value for the RNG and a separator list
   *        (separated by ' <b>- </b>'). default value is
   *        <code>"7542-10"</code> which means server-seed 7542 and only one
   *        separator 10 (which is ascii '\n').
   */
  public SignatureComputer(String seedAndSeparator) {
    sig4 = new EphemeralSignature(seedAndSeparator);
    sig8 = new WhiplashSignature();
    createEnginesToCheck();
  }

  /**
   * 
   */
  public void createEnginesToCheck() {
    enginesToUseForCheck = new int[2];
    enginesToUseForCheck[0] = 4; //Ephemeral engine
    enginesToUseForCheck[1] = 8;//Whiplash engine
  }

  public boolean isSigSupported(int sig) {
    boolean found = false;
    for (int i = 0; i < enginesToUseForCheck.length && !found; i++) {
      if (enginesToUseForCheck[i] == sig) {
        found = true;
      }
    }
    return found;
  }

  public boolean isSigSupported(String sig) {
    return (sig != null && isSigSupported(Integer.parseInt(sig)));
  }

  public String getDefaultEngine() {
    return "4";
  }

  public Vector computeSigs(Vector EmailParts) {
    if (EmailParts == null) return null;

    Vector printableSigs = new Vector(); // vector of strings
    for (int mailIndex = 0; mailIndex < EmailParts.size(); mailIndex++) {
      String mail = (String) (EmailParts.elementAt(mailIndex));

      if (mail == null) continue;

      /*
       * Compute Sig for bodyparts that are cleaned.
       */
      for (int engineIndex = 0; engineIndex < enginesToUseForCheck.length; engineIndex++) {
        int engineNo = enginesToUseForCheck[engineIndex];
        String sig = null;
        /* EphemeralSignature calculator */
        if(engineNo==4) {
          sig = computeSignature(engineNo,mail);
        } 
        /*
        if(engineNo==8) {
          sig = computeSignature(engineNo,mail);
        } 
        if(engineNo!=4 || engineNo!=8) {
          System.out.println("Err: Couldn't find the signature engine: " + engineNo);
        }
        */

        if (sig != null) {
          String hash = engineNo + ":" + sig;
          printableSigs.addElement(hash);
        } else {
          // we didn't produce a signature for the mail. 
        }
      }//engine
    }//each emails part
    return printableSigs;
  }//computeSigs

  /**
   * @param engineNo
   * @param email
   * @return
   */
  private String computeSignature(int engineNo, String mail) {
    if(engineNo==4) {
      //String s1 = this.sig4.computeSignature(mail);
      return this.sig4.computeSignature(mail);
      //return new String { this.sig4.computeSignature(mail) };
    }

    if(engineNo==8) {
        //String cleanedButKeepHTML = Preprocessor.preprocess(mail,Preprocessor.ConfigParams.NO_DEHTML);
        //return this.sig8.computeSignature(cleanedButKeepHTML);
      //return this.sig8.computeSignature(mail);
    }
    return null;
  }
}