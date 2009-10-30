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

	public Vector computeSigs(StringBuffer[] Mails) {
		if (Mails == null) return null;

		Vector printableSigs = new Vector();
		for (int mailIndex = 0; mailIndex < Mails.length; mailIndex++) {
			StringBuffer mail = Mails[mailIndex];

			if (mail == null) continue;

            /*
             * Compute Sig for bodyparts that are cleaned.
             */
            for (int engineIndex = 0; engineIndex < enginesToUseForCheck.length; engineIndex++) {
              int engineNo = enginesToUseForCheck[engineIndex];
              String[] sig = null;

              switch (engineNo) {
                case 4:
                  sig = computeSignature(engineNo,curPart.getCleaned());
                  break;
                case 8:
                  sig = computeSignature(engineNo,curPart.getBody());
                  break;
                default:
                  /*
                   * for nilsimsa and sha1 wich are no longer supported by
                   * the server and might be removed someday
                   */
                  sig = computeSignature(engineNo,curPart.getCleaned());
                  break;
              }//switch engineNo

              if (sig != null && sig.length > 0) {
                for (int curSigIndex = 0; curSigIndex < sig.length; curSigIndex++) {
                  String hash = engineNo + ":" + sig[curSigIndex];
                  curPart.addHash(hash);
                  printableSigs.add(hash);
                }

              } else {
                /* we didn't produce a signature for the mail. */
              }
            }//engine
        }//mails
        return printableSigs;
    }//computeSigs

	/**
	 * @param engineNo
	 * @param cleaned
	 * @return
	 */
	private String[] computeSignature(int engineNo, String mail) {
		switch (engineNo) {
			case 4:
				return new String[] { this.sig4.computeSignature(mail) };
			case 8:
				String cleanedButKeepHTML = Preprocessor.preprocess(mail,Preprocessor.ConfigParams.NO_DEHTML);
				return this.sig8.computeSignature(cleanedButKeepHTML);
			default:
				return null;
		}
	}

	public static String[] getCommonSupportedEngines(int serverSupportedEngines) {
		Vector<String> commonSupported = new Vector<String>();
		int engineMask = 1;
		int engineIndex = 1;
		while (engineIndex < 32) {
			boolean serverSupported = (serverSupportedEngines & engineMask) > 0;
			boolean clientSupported = isSigSupported(engineIndex);
			if (serverSupported && clientSupported) {
				commonSupported.add(String.valueOf(engineIndex));
			}
			//switch to next
			engineMask <<= 1; //shift one to left
			engineIndex++;
		}
		if (commonSupported.size() == 0) {
			return null;
		}
		String[] result = new String[commonSupported.size()];
		commonSupported.toArray(result);
		return result;
	}
}
