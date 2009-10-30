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
	public double SPAM_THRESHOLD;
	public double ABSOLUTE_SPAM;
	public double ABSOLUTE_HAM;

    //TODO decide a good way of deciding
	public double result; // the result, a value between 0 (ham) and 1 (spam), negative values for "error", "unknown" etc.

	//public HashMap<String,String> properties = new HashMap<String,String>(); // additional properties of the filter (mainly for statistics)

	// -----------------------------------------------------------------------------

	public FilterResult(double result) {
      SPAM_THRESHOLD=0.5;
      ABSOLUTE_SPAM=1.0;
      ABSOLUTE_HAM=0.0;
      NO_RESULT=-1;
      this.result = result;
    }

	public double getResult() {
		return result;
	}

	public boolean isSpam() {
		return result >= SPAM_THRESHOLD;
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
