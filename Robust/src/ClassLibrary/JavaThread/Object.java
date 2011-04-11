public class Object {
  public int cachedCode;   //first field has to be a primitive
  public boolean cachedHash;

  public native int hashCode();

  /* DON'T USE THIS METHOD UNLESS NECESSARY */
  /* WE WILL DEPRECATE IT AS SOON AS INSTANCEOF WORKS */
  public native int getType();

  public native void MonitorEnter();
  public native void MonitorExit();

  public String toString() {
    return "Object"+hashCode();
  }

  public boolean equals(Object o) {
    if (o==this)
      return true;
    return false;
  }
}
