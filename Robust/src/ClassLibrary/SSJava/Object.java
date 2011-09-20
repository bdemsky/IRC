import String;

@LATTICE("")
@METHODDEFAULT("TH<IN,THISLOC=IN,GLOBALLOC=TH")
public class Object {

  @RETURNLOC("TH")
  public native int hashCode();

  /* DON'T USE THIS METHOD UNLESS NECESSARY */
  /* WE WILL DEPRECATE IT AS SOON AS INSTANCEOF WORKS */
  public native int getType();

  public boolean equals(@LOC("IN") Object o) {
    if (o == this)
      return true;
    return false;
  }

  @TRUST
  @RETURNLOC("TH")
  public String toString() {
    return "Object" + hashCode();
  }

}
