public class Foo {
  public Foo f;
  public Foo g;
}

public class Test {

  

  static public void main( String args[] ) {

    Foo x = getFlagged();
    Foo z = getUnflagged();
    x.f = z;

    // x is flagged and z is reachable from
    // at most one object from that site
    gendefreach z0;
    genreach z0;

    Foo t = getFlagged();
    t = getFlagged();

    Foo y = new Foo();

    // x is flagged and z is reachable from
    // at most one object from that site, even
    // though x is summarized now
    gendefreach z1;
    genreach z1;

    y.f = z;

    gendefreach z2;
    genreach z2;

    x.f = y;

    // if we had definite reachability analysis
    // we would realize z is already reachable
    // from x, but we don't and x is summarized
    // so we conservatively increase the arity
    // of objects by PROPAGATING through y.
    gendefreach z3;
    genreach z3;

    System.out.println( " "+x+y );
  }

  static public Foo getFlagged() {
    return disjoint jupiter new Foo();
  }

  static public Foo getUnflagged() {
    return new Foo();
  }

}
