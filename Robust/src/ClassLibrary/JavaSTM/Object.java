public class Object {
  public native int nativehashCode();
  private int objstatus;
  private Object objlocation;

  public int hashCode() {
    if (!cachedHash) {
      cachedCode=nativehashCode();
      cachedHash=true;
    }
    return cachedCode;
  }

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
