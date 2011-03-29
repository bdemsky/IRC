public class ElementEdge {
  private final Tuple p1;
  private final Tuple p2;
  private final int hashvalue;

  public ElementEdge() {
    p1 = null;
    p2 = null;
    hashvalue = 1;
  }

  public ElementEdge(Tuple a, Tuple b) {
    if (a.lessThan(b)) {
      p1 = a;
      p2 = b;
    } else {
      p1 = b;
      p2 = a;
    }
    int tmphashval = 17;
    tmphashval = 37 * tmphashval + p1.hashCode();
    tmphashval = 37 * tmphashval + p2.hashCode();
    hashvalue = tmphashval;
  }

  public ElementEdge(ElementEdge rhs) {
    p1 = rhs.p1;
    p2 = rhs.p2;
    hashvalue = rhs.hashvalue;
  }

  public boolean equals(Object obj) {
    if (!(obj instanceof ElementEdge))
      return false;
    ElementEdge edge = (ElementEdge) obj;
    return p1.equals(edge.p1) && p2.equals(edge.p2);
  }

  public int hashCode() {
    return hashvalue;
  }

  public boolean notEqual(ElementEdge rhs) {
    return !equals(rhs);
  }

  public boolean lessThan(ElementEdge rhs) {
    return p1.lessThan(rhs.p1) || p1.equals(rhs.p1) && p2.lessThan(rhs.p2);
  }

  public boolean greaterThan(ElementEdge rhs) {
    return p1.greaterThan(rhs.p1) || p1.equals(rhs.p1) && p2.greaterThan(rhs.p2);
  }

  public Tuple getPoint(int i) {
    if (i == 0)
      return p1;
    if (i == 1) {
      return p2;
    } else {
      System.exit(-1);
      return null;
    }
  }

  public String toString() {
    return "<"+p1.toString()+(", ")+p2.toString()+">";
  }
}
