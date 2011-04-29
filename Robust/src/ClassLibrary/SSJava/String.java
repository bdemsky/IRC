public class String {

  locdef{
    data<proc,proc<c,c<in,in*,c*,proc*,data*
  }

  @LOC("data") char value[];
  @LOC("data") int count;
  @LOC("data") int offset;
  @LOC("data") private int cachedHashcode;

  private String() {
  }

  public String(@LOC("in") char c) {
    @LOC("data") char[] str = new char[1];
    str[0] = c;
    String(str);
  }

  public String(@LOC("in") char str[]) {
    @LOC("in") char charstr[]=new char[str.length];
    for(@LOC("c") int i=0; i<str.length; i++)
      charstr[i]=str[i];
    this.value=charstr;
    this.count=str.length;
    this.offset=0;
  }

  public String(@LOC("in") byte str[]) {
    @LOC("in") char charstr[]=new char[str.length];
    for(@LOC("c") int i=0; i<str.length; i++)
      charstr[i]=(char)str[i];
    this.value=charstr;
    this.count=str.length;
    this.offset=0;
  }

  public String(@LOC("in") byte str[], @LOC("in") int offset, @LOC("in") int length) {
    if (length>(str.length-offset))
      length=str.length-offset;
    @LOC("in") char charstr[]=new char[length];
    for(@LOC("c")int i=0; i<length; i++)
      charstr[i]=(char)str[i+offset];
    this.value=charstr;
    this.count=length;
    this.offset=0;
  }

  public String(@LOC("in") byte str[], @LOC("in") String encoding) {
    @LOC("data") int length = this.count;
    if (length>(str.length))
      length=str.length;
    @LOC("data") char charstr[]=new char[length];
    for(@LOC("c") int i=0; i<length; i++)
      charstr[i]=(char)str[i];
    this.value=charstr;
    this.count=length;
    this.offset=0;
  }

  public String(@LOC("in") char str[], @LOC("in") int offset, @LOC("in") int length) {
    if (length>(str.length-offset))
      length=str.length-offset;
    @LOC("in") char charstr[]=new char[length];
    for(@LOC("c") int i=0; i<length; i++)
      charstr[i]=str[i+offset];
    this.value=charstr;
    this.count=length;
    this.offset=0;
  }

  public String(@LOC("in") String str) {
    this.value=str.value;
    this.count=str.count;
    this.offset=str.offset;
  }
  
  public String concat(@LOC("in") String str) {
    @LOC("data") String newstr=new String();
    newstr.count=this.count+str.count;
    @LOC("data") char charstr[]=new char[newstr.count];
    newstr.value=charstr;
    newstr.offset=0;
    for(@LOC("c") int i=0; i<count; i++) {
      charstr[i]=value[i+offset];
    }
    for(@LOC("c") int i=0; i<str.count; i++) {
      charstr[i+count]=str.value[i+str.offset];
    }
    return newstr;
  }
  
  /*
  public String(StringBuffer strbuf) {
    value=new char[strbuf.length()];
    count=strbuf.length();
    offset=0;
    for(int i=0; i<count; i++)
      value[i]=strbuf.value[i];
  }
   */
  
  /*
  public boolean endsWith(@LOC("in") String suffix) {
    return regionMatches(count - suffix.count, suffix, 0, suffix.count);
  }


  public String substring(@LOC("in") int beginIndex) {
    return substring(beginIndex, this.count);
  }

  public String subString(@LOC("in") int beginIndex, @LOC("in") int endIndex) {
    return substring(beginIndex, endIndex);
  }

  public String substring(@LOC("in") int beginIndex, @LOC("in") int endIndex) {
    String str=new String();
    if (beginIndex>this.count||endIndex>this.count||beginIndex>endIndex) {
      // FIXME
      System.printString("Index error: "+beginIndex+" "+endIndex+" "+count+"\n"+this);
    }
    str.value=this.value;
    str.count=endIndex-beginIndex;
    str.offset=this.offset+beginIndex;
    return str;
  }

  public String subString(@LOC("in") int beginIndex) {
    return this.subString(beginIndex, this.count);
  }

  public int lastindexOf(@LOC("in") int ch) {
    return this.lastindexOf(ch, count - 1);
  }

  public int lastIndexOf(@LOC("in") char ch) {
    return this.lastindexOf((int)ch, count - 1);
  }

  public static String concat2(@LOC("in") String s1, @LOC("in") String s2) {
    if (s1==null)
      return "null".concat(s2);
    else
      return s1.concat(s2);
  }

  public int lastindexOf(@LOC("in") int ch, @LOC("in") int fromIndex) {
    for(int i=fromIndex; i>0; i--)
      if (this.charAt(i)==ch)
        return i;
    return -1;
  }

  public String replace(@LOC("in") char oldch, @LOC("in") char newch) {
    char[] buffer=new char[count];
    for(int i=0; i<count; i++) {
      char x=charAt(i);
      if (x==oldch)
        x=newch;
      buffer[i]=x;
    }
    return new String(buffer);
  }

  public String toUpperCase() {
    char[] buffer=new char[count];
    for(int i=0; i<count; i++) {
      char x=charAt(i);
      if (x>='a'&&x<='z') {
        x=(char) ((x-'a')+'A');
      }
      buffer[i]=x;
    }
    return new String(buffer);
  }

  public String toLowerCase() {
    char[] buffer=new char[count];
    for(int i=0; i<count; i++) {
      char x=charAt(i);
      if (x>='A'&&x<='Z') {
        x=(char) ((x-'A')+'a');
      }
      buffer[i]=x;
    }
    return new String(buffer);
  }

  public int indexOf(@LOC("in") int ch) {
    return this.indexOf(ch, 0);
  }

  public int indexOf(@LOC("in") int ch, @LOC("in") int fromIndex) {
    for(int i=fromIndex; i<count; i++)
      if (this.charAt(i)==ch)
        return i;
    return -1;
  }

  public int indexOf(@LOC("in") String str) {
    return this.indexOf(str, 0);
  }

  public int indexOf(@LOC("in") String str, @LOC("in") int fromIndex) {
    if (fromIndex<0)
      fromIndex=0;
    for(int i=fromIndex; i<=(count-str.count); i++)
      if (regionMatches(i, str, 0, str.count))
        return i;
    return -1;
  }

  public int indexOfIgnoreCase(@LOC("in") String str, @LOC("in") int fromIndex) {
    if (fromIndex < 0) 
      fromIndex = 0;
  }

  public int lastIndexOf(@LOC("in") String str, @LOC("in") int fromIndex) {
    int k=count-str.count;
    if (k>fromIndex)
      k=fromIndex;
    for(; k>=0; k--) {
      if (regionMatches(k, str, 0, str.count))
        return k;
    }
    return -1;
  }

  public int lastIndexOf(@LOC("in") String str) {
    return lastIndexOf(str, count-str.count);
  }

  public boolean startsWith(@LOC("in") String str) {
    return regionMatches(0, str, 0, str.count);
  }

  public boolean startsWith(@LOC("in") String str, @LOC("in") int toffset) {
    return regionMatches(toffset, str, 0, str.count);
  }

  public boolean regionMatches(@LOC("in") int toffset, @LOC("in") String other, @LOC("in") int ooffset, @LOC("in") int len) {
    if (toffset<0 || ooffset <0 || (toffset+len)>count || (ooffset+len)>other.count)
      return false;
    for(int i=0; i<len; i++)
      if (other.value[i+other.offset+ooffset]!=
        this.value[i+this.offset+toffset])
        return false;
    return true;
  }

  public char[] toCharArray() {
    @LOC("data") char str[]=new char[count];
    for(@LOC("data") int i=0; i<count; i++)
      str[i]=value[i+offset];
    return str;
  }

  public byte[] getBytes() {
    byte str[]=new byte[count];
    for(int i=0; i<count; i++)
      str[i]=(byte)value[i+offset];
    return str;
  }

  public void getChars(@LOC("in") char dst[], @LOC("in") int dstBegin) {
    getChars(0, count, dst, dstBegin);
  }

  public void getChars(@LOC("in") int srcBegin, @LOC("in") int srcEnd, @LOC("in") char dst[],@LOC("in")  int dstBegin) {
    if((srcBegin < 0) || (srcEnd > count) || (srcBegin > srcEnd)) {
      // FIXME
      System.printString("Index error: "+srcBegin+" "+srcEnd+" "+count+"\n"+this);
      System.exit(-1);
    }
    int len = srcEnd - srcBegin;
    int j = dstBegin;
    for(int i=srcBegin; i<srcEnd; i++)
      dst[j++]=value[i+offset];
    return;
  }
*/
  public int length() {
    return count;
  }
/*
  public char charAt(@LOC("in") int i) {
    return value[i+offset];
  }
*/
  public String toString() {
    return this;
  }

  public static String valueOf(@LOC("in") Object o) {
    if (o==null)
      return "null";
    else
      return o.toString();
  }
  
  public static String valueOf(@LOC("in") int x) {
    @LOC("in") int length=0;
    @LOC("in") int tmp;
    if (x<0)
      tmp=-x;
    else
      tmp=x;
    do {
      tmp=tmp/10;
      length=length+1;
    } while(tmp!=0);

    @LOC("in") char chararray[];
    if (x<0)
      chararray=new char[length+1];
    else
      chararray=new char[length];
    @LOC("in") int voffset;
    if (x<0) {
      chararray[0]='-';
      voffset=1;
      x=-x;
    } else
      voffset=0;

    do {
      chararray[--length+voffset]=(char)(x%10+'0');
      x=x/10;
    } while (length!=0);
    return new String(chararray);
  }
  
/*
  public static String valueOf(@LOC("in") boolean b) {
    if (b)
      return new String("true");
    else
      return new String("false");
  }

  public static String valueOf(@LOC("in") char c) {
    @LOC("data") char ar[]=new char[1];
    ar[0]=c;
    return new String(ar);
  }

  

  public static String valueOf(@LOC("in") double val) {
    char[] chararray=new char[20];
    String s=new String();
    s.offset=0;
    s.count=convertdoubletochar(val, chararray);
    s.value=chararray;
    return s;
  }

  public static native int convertdoubletochar(double val, char [] chararray);

  public static String valueOf(@LOC("in") long x) {
    int length=0;
    long tmp;
    if (x<0)
      tmp=-x;
    else
      tmp=x;
    do {
      tmp=tmp/10;
      length=length+1;
    } while(tmp!=0);

    char chararray[];
    if (x<0)
      chararray=new char[length+1];
    else
      chararray=new char[length];
    int voffset;
    if (x<0) {
      chararray[0]='-';
      voffset=1;
      x=-x;
    } else
      voffset=0;

    do {
      chararray[--length+voffset]=(char)(x%10+'0');
      x=x/10;
    } while (length!=0);
    return new String(chararray);
  }

  public int compareTo(@LOC("in") String s) {
    int smallerlength=count<s.count?count:s.count;

    for( int i = 0; i < smallerlength; i++ ) {
      int valDiff = this.charAt(i) - s.charAt(i);
      if( valDiff != 0 ) {
        return valDiff;
      }
    }
    return count-s.count;
  }
*/
  public int hashCode() {
    if (cachedHashcode!=0)
      return cachedHashcode;
    @LOC("data") int hashcode=0;
    for(@LOC("c") int i=0; i<count; i++)
      hashcode=hashcode*31+value[i+offset];
    cachedHashcode=hashcode;
    return hashcode;
  }

  public boolean equals(@LOC("in") Object o) {
    if (o.getType()!=getType())
      return false;
    @LOC("in") String s=(String)o;
    if (s.count!=count)
      return false;
    for(@LOC("c") int i=0; i<count; i++) {
      if (s.value[i+s.offset]!=value[i+offset])
        return false;
    }
    return true;
  }
  /*
  public boolean equalsIgnoreCase(@LOC("in") String s) {
    if (s.count!=count)
      return false;
    for(int i=0; i<count; i++) {
      char l=s.value[i+s.offset];
      char r=value[i+offset];
      if (l>='a'&&l<='z')
        l=(char)((l-'a')+'A');
      if (r>='a'&&r<='z')
        r=(char)((r-'a')+'A');
      if (l!=r)
        return false;
    }
    return true;
  }

  public Vector split() {
    Vector splitted = new Vector();
    int i;
    int cnt =0;

    // skip first spaces
    for(i = 0; i< count;i++) {
      if(value[i+offset] != '\n' && value[i+offset] != '\t' && value[i+offset] != ' ') 
	  break;
    }

    int oldi=i;

    while(i<count) {
      if(value[i+offset] == '\n' || value[i+offset] == '\t' || value[i+offset] == ' ') {
	  String t=new String();
	  t.value=value;
	  t.offset=oldi;
	  t.count=i-oldi;
	  splitted.addElement(t);

	  // skip extra spaces
	  while( i < count && ( value[i+offset] == '\n' || value[i+offset] == '\t' || value[i+offset] == ' ')) {
	      i++;
	  }
	  oldi=i;
      } else {
	  i++;
      }
    }

    if(i!=oldi) {
	String t=new String();
	t.value=value;
	t.offset=oldi;
	t.count=i-oldi;
	splitted.addElement(t);
    }

    return splitted;
  }
   
  public boolean contains(@LOC("in") String str)
  {
    int i,j;
    char[] strChar = str.toCharArray();
    int cnt;

    for(i = 0; i < count; i++) {
      if(value[i] == strChar[0]) {
        cnt=0;
        for(j=0; j < str.length() && i+j < count;j++) {
          if(value[i+j] == strChar[j])
            cnt++;
        }
        if(cnt == str.length())
          return true;
      }
    }

    return false;

  }

  public String trim() {
    int len = count;
    int st = 0;
    int off = offset;      //avoid getfield opcode 
    char[] val = value;    // avoid getfield opcode 

    while ((st < len) && (val[off + st] <= ' ')) {
      st++;
    }
    while ((st < len) && (val[off + len - 1] <= ' ')) {
      len--;
    }
    return ((st > 0) || (len < count)) ? substring(st, len) : this;
  }

  public boolean matches(@LOC("in") String regex) {
    System.println("String.matches() is not fully supported");
    return this.equals(regex);
  }
  */
}
