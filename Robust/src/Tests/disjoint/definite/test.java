public class Foo {
  public Foo f;
  public Foo g;
}

public class Test {

  

  static public void main( String args[] ) {

    gendefreach yn1;    

    Foo x = getFlagged();
    Foo y = getUnflagged();
    x.f = y;

    // x is flagged and y is reachable from
    // at most one object from that site
    gendefreach y0;
    genreach y0;

    Foo t = getFlagged();
    t = getFlagged();

    // x is flagged and y is reachable from
    // at most one object from that site, even
    // though x is summarized now
    gendefreach y1;
    genreach y1;

    x.g = y;

    // if we had definite reachability analysis
    // we would realize y is already reachable
    // from x, but we don't and x is summarized
    // so we conservatively increase the arity
    // of objects y is reachable from.
    gendefreach y2;
    genreach y2;

    System.out.println( " "+x+y );
  }

  static public Foo getFlagged() {
    return disjoint jupiter new Foo();
  }

  static public Foo getUnflagged() {
    return new Foo();
  }

}
