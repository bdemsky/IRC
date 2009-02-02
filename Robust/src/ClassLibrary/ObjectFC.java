public class Object {
  public int cachedCode;   //first field has to be a primitive
  public boolean cachedHash;
  public Object nextobject;   /* Oid */
  public Object localcopy;
  private Object tags;

  public native int nativehashCode();

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
