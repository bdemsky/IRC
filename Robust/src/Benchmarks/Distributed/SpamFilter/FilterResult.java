/**
 * A FilterResult encapsulates the result of a filter made by checking a mail.
 **/
public class FilterResult {
  /**
   * This value is used if type is ERROR or UNKNOWN.
   */
  public double NO_RESULT;

  /**
   * A result value greater or equal this value indicates that the filter has
   * decided on spam.
   */
  public int SPAM_THRESHOLD;
  public int ABSOLUTE_SPAM;
  public int ABSOLUTE_HAM;

  //public double result; // the result, a value between -1 (ham) and 1000 (spam), 
  // negative values for "error", "unknown" etc.

  // -----------------------------------------------------------------------------

  public FilterResult(double result) {
    SPAM_THRESHOLD=500;
    ABSOLUTE_SPAM=1000;
    ABSOLUTE_HAM=0;
    NO_RESULT=-1;
    this.result = result;
  }

  public FilterResult() {
    SPAM_THRESHOLD=500;
    ABSOLUTE_SPAM=1000;
    ABSOLUTE_HAM=0;
    NO_RESULT=-1;
  }

  public double getResult() {
    return result;
  }

  public boolean isSpam() {
    return result >= SPAM_THRESHOLD;
  }

  public boolean getResult(int[] confidenceVals) {
    int[] res = new int[3];
    for(int i=0; i<confidenceVals; i++) {
       if(confidenceVals[i] < 0)
         res[0]+=1; //unknown
       if(confidenceVals[i] >= 0 && confidenceVals[i] < 500)
         res[1]+=1; //ham
       if(confidenceVals[i] > SPAM_THRESHOLD)
         res[2]+=1;//spam
    }
    int maxVotes=0;
    int max;
    for(int i=0; i<3;i++) {
      if(res[i] > maxVotes) {
        maxVotes = res[i];
        max = i;
      }
    }
    if(i==0)
      return false;
    if(i==1)
      return false;
    if(i==2)
      return true;

    System.out.println("Err: getResult() Shouldn't come here\n");
    return false;
  }

  /*
     public void addProperty(String key, String value) {
     properties.put(key,value);
     }

     public String getProperty(String key) {
     return properties.get(key);
     }

     public HashMap<String,String> getProperties() {
     return properties;
     }
   */
}
