package Util;

public class Pair {
  private Object a;
  private Object b;
  public Pair(Object a, Object b) {
    this.a=a;
    this.b=b;
  }
  public int hashCode() {
    return a.hashCode()*31+b.hashCode();
  }
  public boolean equals(Object o) {
    if (!(o instanceof Pair))
      return false;
    Pair t=(Pair)o;
    return a.equals(t.a)&&b.equals(t.b);
  }
}