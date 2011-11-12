public class Integer {
  private int value;

  public Integer(int value) {
    this.value=value;
  }

  public Integer(String str) {
    value=Integer.parseInt(str, 10);
  }

  public int intValue() {
    return value;
  }

  public double doubleValue() {
    return (double)value;
  }

  public float floatValue() {
    return (float)value;
  }

  public byte[] intToByteArray() {
    byte[] b = new byte[4];
    for (int i = 0; i < 4; i++) {
      int offset = (b.length - 1 - i) * 8;
      b[i] = (byte) ((value >> offset) & 0xFF);
    }
    return b;
  }

  public int byteArrayToInt(byte [] b) {
    int value = 0;
    for (int i = 0; i < 4; i++) {
      int shift = (4 - 1 - i) * 8;
      value += (b[i] & 0x000000FF) << shift;
    }
    return value;
  }

  /*
     public int byteArrayToInt(byte [] b) {
     int val;
     val = b[0] << 24 + ((b[1] & 0xFF) << 16) + ((b[2] & 0xFF) << 8) + (b[3] & 0xFF);
     return val;
     }
   */

  public static int parseInt(String str) {
    return Integer.parseInt(str, 10);
  }

  public static int parseInt(String str, int radix) {
    int value=0;
    boolean isNeg=false;
    int start=0;
    byte[] chars=str.getBytes();

    while(chars[start]==' '||chars[start]=='\t')
      start++;

    if (chars[start]=='-') {
      isNeg=true;
      start++;
    }
    boolean cont=true;
    for(int i=start; cont&&i<str.length(); i++) {
      byte b=chars[i];
      int val;
      if (b>='0'&&b<='9')
        val=b-'0';
      else if (b>='a'&&b<='z')
        val=10+b-'a';
      else if (b>='A'&&b<='Z')
        val=10+b-'A';
      else {
        cont=false;
      }
      if (cont) {
        if (val>=radix)
          System.error();
        value=value*radix+val;
      }
    }
    if (isNeg)
      value=-value;
    return value;
  }

  public String toString() {
    return String.valueOf(value);
  }

  public static String toString(int i) {
    Integer I = new Integer(i);
    return I.toString();
  }

  public int hashCode() {
    return value;
  }

  public boolean equals(Object o) {
    if (o.getType()!=getType())
      return false;
    Integer s=(Integer)o;
    if (s.intValue()!=this.value)
      return false;
    return true;
  }

  public int compareTo(Integer i) {
    if (value == i.value)
      return 0;
    // Returns just -1 or 1 on inequality; doing math might overflow.
    return value > i.value?1:-1;
  }
  
  public static int bitCount(int x) {
    // Successively collapse alternating bit groups into a sum.
    x = ((x >> 1) & 0x55555555) + (x & 0x55555555);
    x = ((x >> 2) & 0x33333333) + (x & 0x33333333);
    x = ((x >> 4) & 0x0f0f0f0f) + (x & 0x0f0f0f0f);
    x = ((x >> 8) & 0x00ff00ff) + (x & 0x00ff00ff);
    return ((x >> 16) & 0x0000ffff) + (x & 0x0000ffff);
  }
  
  public static int numberOfLeadingZeros(int value) {
    value |= value >>> 1;
    value |= value >>> 2;
    value |= value >>> 4;
    value |= value >>> 8;
    value |= value >>> 16;
    return bitCount(~value);
  }

  /**
   * Returns an <code>Integer</code> object wrapping the value.
   * In contrast to the <code>Integer</code> constructor, this method
   * will cache some values.  It is used by boxing conversion.
   *
   * @param val the value to wrap
   * @return the <code>Integer</code>
   */
  public static Integer valueOf(int val)
  {
    //if (val < MIN_CACHE || val > MAX_CACHE)
      return new Integer(val);
    /*else
      return intCache[val - MIN_CACHE];*/
  }
}
