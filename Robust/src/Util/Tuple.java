package Util;

public class Tuple {
  private Object a;
  private Object b;
  public Tuple(Object a, Object b) {
    this.a=a;
    this.b=b;
  }
  public int hashCode() {
    return a.hashCode()*31+b.hashCode();
  }
  public boolean equals(Object o) {
    if (!(o instanceof Tuple))
      return false;
    Tuple t=(Tuple)o;
    return a.equals(t.a)&&b.equals(t.b);
  }
}