import Object;
import String;
import StringBuffer;
import System;

@LATTICE("V<C, V<O")
@METHODDEFAULT("O<V,V<C,C<IN,THISLOC=O,C*")
public class String {

  @LOC("V") char value[];
  @LOC("C") int count;
  @LOC("O") int offset;
  @LOC("V") private int cachedHashcode;

  private String() {
  }
  
  public String(byte str[]) {
    char charstr[]=new char[str.length];
    for(int i=0; i<str.length; i++)
      charstr[i]=(char)str[i];
    this.value=charstr;
    this.count=str.length;
    this.offset=0;
  }
  
  public String(char str[], int offset, int length) {
    if (length>(str.length-offset))
      length=str.length-offset;
    char charstr[]=new char[length];
    for(int i=0; i<length; i++)
      charstr[i]=str[i+offset];
    this.value=charstr;
    this.count=length;
    this.offset=0;
  }
  

  public String(byte str[], String encoding) {
    int length = this.count;
    if (length>(str.length))
      length=str.length;
    char charstr[]=new char[length];
    for(int i=0; i<length; i++)
      charstr[i]=(char)str[i];
    this.value=charstr;
    this.count=length;
    this.offset=0;
  }
  
  public String( @DELEGATE  @LOC("IN") String str) {
    this.value=str.value;
    str.value=null;
    this.count=str.count;
    this.offset=str.offset;
  }
  
  public String(StringBuffer strbuf) {
    value=new char[strbuf.length()];
    count=strbuf.length();
    offset=0;
    for(int i=0; i<count; i++)
      value[i]=strbuf.value[i];
  }

  public String(@LOC("IN") char c) {
    @LOC("V") char[] str = new char[1];
    str[0] = c;
    String(str);
  }

  public String(@LOC("IN") char str[]) {
    @LOC("V") char charstr[]=new char[str.length];
    for(@LOC("C") int i=0; i<str.length; i++)
      charstr[i]=str[i];
    this.value=charstr;
    charstr=null;
    this.count=str.length;
    this.offset=0;
  }
  
  @LATTICE("O<V,V<C,C<IN,THISLOC=IN,C*")
  @RETURNLOC("O")
  public String concat(@LOC("IN") String str) {
    @LOC("O") String newstr=new String(); // create new one, it has OUT location
    @LOC("C") int newCount=this.count+str.count;

    @LOC("V") char charstr[]=new char[newCount];

    // here, for loop introduces indirect flow from [C] to [V]
    for(@LOC("C") int i=0; i<count; i++) {
      // value flows from GLB(THISLOC,C,THISLOC.V)=(THISLOC,TOP) to [V]
      charstr[i]=value[i+offset]; 
    }
    for(@LOC("C") int i=0; i<str.count; i++) {
      charstr[i+count]=str.value[i+str.offset];
    }

    newstr.value=charstr;
    charstr=null;
    // LOC(newstr.value)=[O,V] 
    // LOC(charstr)=[V]
    // [O,V] < [V]
    
    return newstr;
  }
  
  @RETURNLOC("O")
  public boolean equals(@LOC("IN") Object o) {
    if (o.getType()!=getType()) // values are coming from [IN] and [THISLOC]
      return false;
    @LOC("V") String s=(String)o;
    o=null;
    if (s.count!=count)
      return false;
    for(@LOC("C") int i=0; i<count; i++) {
      if (s.value[i+s.offset]!=value[i+offset])
        return false;
    }
    return true;
  }
  
  public void getChars(char dst[], int dstBegin) {
    getChars(0, count, dst, dstBegin);
  }

  public void getChars(int srcBegin, int srcEnd, char dst[], int dstBegin) {
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
  
  public int indexOf(String str, int fromIndex) {
    if (fromIndex<0)
      fromIndex=0;
    for(int i=fromIndex; i<=(count-str.count); i++)
      if (regionMatches(i, str, 0, str.count))
        return i;
    return -1;
  }
  
  public boolean regionMatches(int toffset, String other, int ooffset, int len) {
    if (toffset<0 || ooffset <0 || (toffset+len)>count || (ooffset+len)>other.count)
      return false;
    for(int i=0; i<len; i++)
      if (other.value[i+other.offset+ooffset]!=
          this.value[i+this.offset+toffset])
        return false;
    return true;
  }
  
  @RETURNLOC("O")
  public static String valueOf(@LOC("IN") Object o) {
    if (o==null)
      return "null";
    else
      return o.toString();
  }
  
  public static String valueOf(boolean b) {
    if (b)
      return new String("true");
    else
      return new String("false");
  }
  
  public static String valueOf(char c) {
    char ar[]=new char[1];
    ar[0]=c;
    return new String(ar);
  }

  public static String valueOf(int x) {
    int length=0;
    int tmp;
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

  public static String valueOf(double val) {
    char[] chararray=new char[20];
    String s=new String();
    s.offset=0;
    s.count=convertdoubletochar(val, chararray);
    s.value=chararray;
    return s;
  }
  
  public static native int convertdoubletochar(double val, char [] chararray);


  public static String valueOf(long x) {
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
  
  @LATTICE("O<V,V<C,C<IN,THISLOC=IN,C*")
  @RETURNLOC("O")
  public byte[] getBytes() {
    @LOC("V") byte str[]=new byte[count];
    for(@LOC("C") int i=0; i<count; i++)
      str[i]=(byte)value[i+offset];
    return str;
  }
  
  
  
  @RETURNLOC("IN")
  public int length() {
    return count;
  }
  
  @RETURNLOC("O")
  public char charAt(@LOC("IN") int index){
    return value[index];
  }

    //public static native int convertdoubletochar(double val, char [] chararray);

}
