public class Object {
  
  locdef{  
    data
  }
  
  public native int hashCode();

  /* DON'T USE THIS METHOD UNLESS NECESSARY */
  /* WE WILL DEPRECATE IT AS SOON AS INSTANCEOF WORKS */
  public native int getType();

  public String toString() {
    return "Object"+hashCode();
  }

  public boolean equals(@LOC("data") Object o) {
    if (o==this)
      return true;
    return false;
  }
}
