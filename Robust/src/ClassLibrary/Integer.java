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
}
