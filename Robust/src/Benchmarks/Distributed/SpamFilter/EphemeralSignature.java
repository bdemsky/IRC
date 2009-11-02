public class EphemeralSignature {
  
  private int serverSeed;
  private String serverSeparator;
  Random rand;

  public EphemeralSignature() {
    Random rand = new Random(0);
  }

  public EphemeralSignature(int randomNumberSeed, String separator) {
    Random rand = new Random(randomNumberSeed);
    serverSeparator = separator;
  }

  public EphemeralSignature(String seedAndSeparator) {
    serverSeparator = seedAndSeparator;
  }

  public String computeSignature(String body) {
    MD5 md = new MD5();
    int len = body.length();
    byte buf[] = body.getBytes();
    byte sig[] = new byte[16];

    md.update(buf, len);
    md.md5final(sig);
    String signature = new String(sig);

    return signature;
  }

  private String computeHexDigest(String body) {
    return 
  }

  /*
  public long DEKHash(String str)
  {
    long hash = str.length();

    for(int i = 0; i < str.length(); i++)
    {
      hash = ((hash << 5) ^ (hash >> 27)) ^ str.charAt(i);
    }

    return hash;
  }
  */

}
