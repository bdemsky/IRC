public class HashEntry {
  String engine;
  String signature;
  HashStat stats;
  public HashEntry() {
  }

  /**
   * hashCode that combines two strings using xor.
   * @return a hash code value on the entire object.
   */
  public int hashCode() {
    int result=0;
    // this will not work well if some of the strings are equal.
    result = engine.hashCode();
    result ^= signature.hashCode();
    //result ^= stats.hashCode();
    System.out.println("result= " + result);
    return result;
  }

  public void setengine(String engine) {
    this.engine=engine;
  }

  public void setstats(HashStat stats) {
    this.stats=stats;
  }

  public void setsig(String signature) {
    this.setsig=signature;
  }

  public String getEngine() {
    return engine;
  }

  public String getSignature() {
    return signature;
  }

  public Stat getStats() {
    return stats;
  }

  public boolean equals(Object o) {
    if(o.getType()!=getType())
      return false;
    HashEntry he = (HashEntry)o;
    if(!(he.getEngine().equals(Engine)))
      return false;
    if(!(he.getSignature().equals(Signature)))
      return false;
    //if(!(he.getStats().equals(stats)))
    //  return false;
    return true;
  }

  public int askForSpam() {
    Vector users = stats.getUsers();
    int spamConfidence=0;
    for(int i=0; i<users.size(); i++) {
      int userid = (int) (users.elementAt(i));
      spamConfidence += stats.userstat[userid].getChecked();
    }
    return spamConfidence;
  }
}
