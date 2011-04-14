public class Object {
  // temporary extra unused int filed to align objects for Java
  //int wkhqwemnbmwnb;

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
  
  public final native void notify();
  public final native void notifyAll();
  public final native void wait();
}
