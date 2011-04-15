public class Object {
  /* DO NOT USE ANY OF THESE - THEY ARE FOR IMPLEMENTING TAGS */
  private int cachedCode;  //first field has to be a primitive
  private Object tags;  

  public native int hashCode();

  /* DON'T USE THIS METHOD UNLESS NECESSARY */
  /* WE WILL DEPRECATE IT AS SOON AS INSTANCEOF WORKS */
  public native int getType();

  public String toString() {
    return "Object"+hashCode();
  }

  public boolean equals(Object o) {
    if (o==this)
      return true;
    return false;
  }
}
