package Util;

public class Pair<A,B> {
  private A a;
  private B b;
  public Pair(A a, B b) {
    this.a=a;
    this.b=b;
  }
  public A getFirst() {
    return a;
  }
  public B getSecond() {
    return b;
  }
  public int hashCode() {
    if (b!=null)
      return a.hashCode()*31+b.hashCode();
    else
      return a.hashCode();
  }
  public boolean equals(Object o) {
    if (!(o instanceof Pair))
      return false;
    Pair t=(Pair)o;
    return a.equals(t.a)&&(((b!=null)&&(t.b!=null)&&b.equals(t.b))||
			   ((b==null)&&(t.b==null)));
  }
}