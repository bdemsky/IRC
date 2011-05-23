@LATTICE("String_V<String_C, String_V<String_O")
@METHODDEFAULT("StringDM_O<StringDM_V,StringDM_V<StringDM_C,StringDM_C<StringDM_I,THISLOC=StringDM_O,StringDM_C*")
public class String {

  @LOC("String_V") char value[];
  @LOC("String_C") int count;
  @LOC("String_O") int offset;
  @LOC("String_V") private int cachedHashcode;

  private String() {
  }

  public String(@LOC("StringDM_I") char c) {
    @LOC("StringDM_V") char[] str = new char[1];
    str[0] = c;
    String(str);
  }

  public String(@LOC("StringDM_I") char str[]) {
    @LOC("StringDM_V") char charstr[]=new char[str.length];
    for(@LOC("StringDM_C") int i=0; i<str.length; i++)
      charstr[i]=str[i];
    this.value=charstr;
    charstr=null;
    this.count=str.length;
    this.offset=0;
  }

  @LATTICE("StringM1_O<StringM1_V,StringM1_V<StringM1_C,StringM1_C<StringM1_I,THISLOC=StringM1_I,StringM1_C*")
  public String concat(@LOC("StringM1_I") String str) {
    @LOC("StringM1_O") String newstr=new String(); // create new one, it has OUT location
    @LOC("StringM1_C") int newCount=this.count+str.count;

    @LOC("StringM1_V") char charstr[]=new char[newCount];

    // here, for loop introduces indirect flow from [C] to [V]
    for(@LOC("StringM1_C") int i=0; i<count; i++) {
      // value flows from GLB(THISLOC,C,THISLOC.V)=(THISLOC,TOP) to [V]
      charstr[i]=value[i+offset]; 
    }
    for(@LOC("StringM1_C") int i=0; i<str.count; i++) {
      charstr[i+count]=str.value[i+str.offset];
    }

    newstr.value=charstr;
    charstr=null;
    // LOC(newstr.value)=[O,STRING_V] 
    // LOC(charstr)=[V]
    // [O,STRING_V] < [V]
    
    return newstr;
  }
  
  public boolean equals(@LOC("StringDM_I") Object o) {
    if (o.getType()!=getType()) // values are coming from [StringDM_I] and [THISLOC]
      return false;
    @LOC("StringDM_V") String s=(String)o;
    o=null;
    if (s.count!=count)
      return false;
    for(@LOC("StringDM_C") int i=0; i<count; i++) {
      if (s.value[i+s.offset]!=value[i+offset])
        return false;
    }
    return true;
  }


}
