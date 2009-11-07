public class GString {
  char value[];
  int count;
  int offset;

  public GString() {
  }

  public GString(char c) {
    char[] str = global new char[1];
    str[0] = c;
    GString(str);
  }

  public GString(String str) {
    value = global new char[str.count];
    for(int i =0; i< str.count;i++) {
      value[i] = str.value[i+str.offset];
    }
    count = str.count;
    offset = 0;
  }

  public GString(GString gstr) {
    this.value = gstr.value;
    this.count = gstr.count;
    this.offset = gstr.offset;
  }

  public GString(StringBuffer gsb) {
    value = global new char[gsb.length()];
    count = gsb.length();
    offset = 0;
    for (int i = 0; i < count; i++) 
      value[i] = gsb.value[i];
  }

  public GString(char str[]) {
    char charstr[]=new char[str.length];
    for(int i=0; i<str.length; i++)
      charstr[i]=str[i];
    this.value=charstr;
    this.count=str.length;
    this.offset=0;
  }

  public static char[] toLocalCharArray(GString str) {
    char[] c;
    int length;

    length = str.length();

    c = new char[length];

    for (int i = 0; i < length; i++) {
      c[i] = str.value[i+str.offset];
    }
    return c;
  }

  public String toLocalString() {
    return new String(toLocalCharArray(this));
  }

  public int length() {
    return count;
  }

  public int indexOf(int ch, int fromIndex) {
    for (int i = fromIndex; i < count; i++)
      if (this.charAt(i) == ch) 
        return i;
    return -1;
  }

  public int lastindexOf(int ch) {
    return this.lastindexOf(ch, count - 1);
  }

  public int lastindexOf(int ch, int fromIndex) {
    for (int i = fromIndex; i > 0; i--) 
      if (this.charAt(i) == ch) 
        return i;
    return -1;
  }

  public char charAt(int i) {
    return value[i+offset];
  }

  public int indexOf(String str) {
    return this.indexOf(str, 0);
  }

  public int indexOf(String str, int fromIndex) {
    if (fromIndex < 0) 
      fromIndex = 0;
    for (int i = fromIndex; i <= (count-str.count); i++)
      if (regionMatches(i, str, 0, str.count)) 
        return i;
    return -1;
  }	

  public boolean regionMatches(int toffset, String other, int ooffset, int len) {
    if (toffset < 0 || ooffset < 0 || (toffset+len) > count || (ooffset+len) > other.count)
      return false;

    for (int i = 0; i < len; i++) {
      if (other.value[i+other.offset+ooffset] != this.value[i+this.offset+toffset])
        return false;
    }
    return true;
  }

  public String subString(int beginIndex, int endIndex) {
    return substring(beginIndex, endIndex);
  }

  public String substring(int beginIndex, int endIndex) {
    String str;
    str = global new String();
    str.value = this.value;
    str.count = endIndex-beginIndex;
    str.offset = this.offset + beginIndex;
    return str;	
  }

  public static String valueOf(Object o) {
    if (o==null)
      return "null";
    else
      return o.toString();
  }

  public String toLocalString() {
    return new String(toLocalCharArray(this));
  }

  public static char[] toLocalCharArray(GString str) {
    char[] c;
    int length;
    length = str.length();
    c = new char[length];
    for (int i = 0; i < length; i++) {
      c[i] = str.value[i+str.offset];
    }
    return c;
  }

  public int hashCode() {
    String s = this.toLocalString();
    return s.hashCode();
  }

  public boolean equals(Object o) {
    if(o == null)
      return false;
    if(!(o instanceof GString))
      return false;
    GString gs = (GString)o;
    String s1 = gs.toLocalString();
    String s2 = this.toLocalString();
    if(s2.equals(s1))
      return true;
    return false;
  }
}
