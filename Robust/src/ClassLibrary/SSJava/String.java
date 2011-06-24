import Object;
import String;

@LATTICE("V<C, V<O")
@METHODDEFAULT("O<V,V<C,C<IN,THISLOC=O,C*")
public class String {

  @LOC("V") char value[];
  @LOC("C") int count;
  @LOC("O") int offset;
  @LOC("V") private int cachedHashcode;

  private String() {
  }
  
  public String(@LOC("IN") String str) {
    this.value=str.value;
    this.count=str.count;
    this.offset=str.offset;
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
  
  @RETURNLOC("O")
  public static String valueOf(@LOC("IN") Object o) {
    if (o==null)
      return "null";
    else
      return o.toString();
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
