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
    SPAM_THRESHOLD=50;
    ABSOLUTE_SPAM=100;
    ABSOLUTE_HAM=0;
    NO_RESULT=-1;
    //this.result = result;
  }

  public FilterResult() {
    SPAM_THRESHOLD=50;
    ABSOLUTE_SPAM=100;
    ABSOLUTE_HAM=0;
    NO_RESULT=-1;
  }

  public boolean getResult(int[] confidenceVals) {
    int[] res = new int[3]; //3 equals spam, ham and unknown
    for(int i=0; i<confidenceVals.length; i++) {
       if(confidenceVals[i] < 0)
         res[0]+=1; //unknown
       if(confidenceVals[i] >= 0 && confidenceVals[i] < SPAM_THRESHOLD)
         res[1]+=1; //ham
       if(confidenceVals[i] >= SPAM_THRESHOLD)
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
    if(max==0)
      return false;
    if(max==1)
      return false;
    if(max==2)
      return true;

    System.out.println("Err: getResult() Control shouldn't come here, max= " + max);
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
