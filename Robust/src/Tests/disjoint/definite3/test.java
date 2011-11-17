public class Foo {
  public Foo f;
  public Foo g;
}

public class Test {

  

  static public void main( String args[] ) {

    Foo a = getFlagged();
    Foo b = getUnflagged();
    a.f = b;

    // a is flagged and b is reachable from
    // at most one object from that site
    gendefreach z0;
    genreach z0;

    Foo c = new Foo();
    a.g = c;

    Foo t = getFlagged();
    t = getFlagged();

    Foo u = getUnflagged();
    u = getUnflagged();

    // a is flagged and b is reachable from
    // at most one object from that site, even
    // though a and b are summarized now.  a
    // has a reference to a new object c
    gendefreach z1;
    genreach z1;

    c.f = b;

    // if we had definite reachability analysis
    // we would realize b is already reachable
    // from a
    gendefreach z3;
    genreach z3;

    System.out.println( " "+a+b+c );
  }

  static public Foo getFlagged() {
    return disjoint jupiter new Foo();
  }

  static public Foo getUnflagged() {
    return new Foo();
  }

}
